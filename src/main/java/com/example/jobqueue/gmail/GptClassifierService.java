package com.example.jobqueue.gmail;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.jobqueue.Job;
import com.example.jobqueue.JobService;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

@Service
public class GptClassifierService {// This class is responsible for interacting with the OpenAI API and classifying emails

    private final OpenAiService openAiApiService;
    private final JobService jobService;

    //we get to secret key from system environment variable
    public GptClassifierService(@Value("${OPENAI_API_KEY}") String apiKey, JobService jobService) {
        this.openAiApiService = new OpenAiService(apiKey);
        this.jobService = jobService;
    }

    /**
     * Classifies the content of an email into one of three categories:
     * Interview, Rejected, or Waiting. this method is asynchronous and returns
     * a CompletableFuture<Job> to allow non-blocking processing.
     *
     * @param emailContent The content of the email to classify.
     * @return A CompletableFuture containing the classified Job object or null
     * if classification failed.
     */
    public CompletableFuture<Job> classifyEmail(String emailContent) {
        String prompt = """
            Classify the content of this email into one of the following categories: Interview, Rejected, Waiting.

            Instructions:
            1. Analyze the email content for keywords and phrases strongly indicating one of the three categories.
            2. If the email clearly indicates a rejection, the category should be 'Rejected'. Look for phrases like 'sorry but', 'we will not be moving forward', 'your application was not successful', 'we are unable to offer you', 'rejected'.
            3. If the email invites you for an interview or discusses scheduling an interview, the category should be 'Interview'. Look for phrases like 'interview invitation', 'we would like to schedule an interview', 'available times for an interview', 'next steps in the interview process'.
            4. If the email suggests your application is still under consideration, or you are in a pool of candidates, or there will be further communication at a later date without a clear interview invitation or rejection, the category should be 'Waiting'. Look for phrases like 'your application is under review', 'we will be in touch', 'we are still evaluating candidates', 'your profile has been shortlisted'.
            5. If the email content is not clearly related to a job application or the job search process at all, you should ignore it and return the string "null".
            6. Return your classification in the exact format: 'Company Name: Category'.
            7. The 'Company Name' should be the name of the company that sent the email, if it is clearly identifiable in the email content.
            8. You MUST return one of EXACTLY the following categories: 1.INTERVIEW 2.REJECTED 3.WAITING Use exactly one of these words—nothing else.
            Email content: """ + emailContent;

        ChatMessage message = new ChatMessage(ChatMessageRole.USER.value(), prompt);// Create a chat message with the email content as the prompt

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
                .messages(Arrays.asList(message))
                .build();// Build the request for the OpenAI API with the chat message

        return sendClassificationRequestAsync(request)// Asynchronously send the classification request to OpenAI API
                .thenCompose(this::processClassificationResponse)
                .exceptionally(ex -> {
                    System.err.println("Final classification error for email: " + emailContent + ". Cause: " + ex.getCause().getMessage());
                    throw new CompletionException("Failed to classify email", ex.getCause());
                });
    }

    /**
     * 1. Asynchronously call OpenAI API with the classification request.
     *
     * @param request The ChatCompletionRequest containing the email content and
     * prompt.
     * @return A CompletableFuture containing the response string from OpenAI
     * API.
     */
    private CompletableFuture<String> sendClassificationRequestAsync(ChatCompletionRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ChatCompletionResult result = openAiApiService.createChatCompletion(request);// Send the request to OpenAI API and get the result
                return result.getChoices().get(0).getMessage().getContent().trim();// Extract the content of the first choice from the result
            } catch (Exception e) {
                System.err.println("Error calling OpenAI API: " + e.getMessage());
                throw new CompletionException("Failed to get response from OpenAI API", e);
            }
        });
    }

    /**
     * 2. Process the GPT response string into a Job object if valid.
     *
     * @param response The response string from OpenAI API containing the
     * classification result.
     * @return A CompletableFuture containing the Job object if classification
     * was successful, or null if it failed.
     */
    private CompletableFuture<Job> processClassificationResponse(String response) {
        if (response == null || "null".equalsIgnoreCase(response)) {// Check if the response is null or the string "null"
            System.out.println("GPT returned null, email ignored.");
            return CompletableFuture.completedFuture(null);
        }

        // Parse the GPT response for company and status
        ParsedClassification parsed = parseClassificationResponse(response);

        if (parsed == null) {
            System.err.println("GPT response not in expected format: '" + response + "'");
            return CompletableFuture.completedFuture(null);
        }

        // Defensive check for null or empty companyName or status
        if (parsed.companyName == null || parsed.companyName.isEmpty()) {
            System.err.println("Parsed company name or status is empty: Company='" + parsed.companyName + "', Status='" + parsed.status + "'");
            return CompletableFuture.completedFuture(null);
        }

        System.out.println("Classified Company: " + parsed.companyName + ", Status: " + parsed.status);

        // 3. Async database update or creation of Job entity
        return CompletableFuture.supplyAsync(() -> {
            try {
                return jobService.createOrUpdateJob(parsed.companyName, parsed.status);// Create or update the Job entity in the database in the same function for transactional integrity
            } catch (Exception e) {
                System.err.println("Error processing classified job for company '" + parsed.companyName + "': " + e.getMessage());
                throw new CompletionException("Failed to process classified job in DB", e);
            }
        });
    }

    /**
     * Parses the GPT response string to extract company name and status.
     * Returns null if the response is not in the expected "Company Name:
     * Status" format.
     *
     * @param response The response string from OpenAI API.
     * @return A ParsedClassification object containing the company name and
     * status, or null if parsing fails.
     */
    private ParsedClassification parseClassificationResponse(String response) {
        Pattern pattern = Pattern.compile("([^:]+):\\s*(.*)");
        Matcher matcher = pattern.matcher(response);

        if (matcher.matches()) { // This checks if the response matches the expected format of "Company Name: Status"
            String companyName = matcher.group(1).trim(); // Extract the company name
            String statusString = matcher.group(2).trim();// Extract the status

            try {
                String normalizedStatus = normalizeStatus(statusString);// Normalize the status string to a standard format
                if (!JobStatus.isValid(normalizedStatus)) {// Check if the normalized status is a valid JobStatus
                    System.err.println("Invalid status from GPT: '" + normalizedStatus + "'");
                    return null;
                }
                JobStatus statusEnum = JobStatus.valueOf(normalizedStatus);// Convert the status string to the JobStatus enum, ensuring it matches one of the defined statuses
                return new ParsedClassification(companyName, statusEnum);
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid status from GPT: '" + statusString + "'");
                return null;
            }
        }

        return null;
    }

    /**
     * Normalizes the status string to a standard format. Converts the status to
     * lower case and checks for keywords to determine the final status.
     *
     * @param value The status string to normalize.
     * @return A normalized status string: "Interview", "Rejected", or
     * "Waiting".
     */
    public static String normalizeStatus(String value) {
        value = value.toUpperCase();
        if (value.contains("REJECT")) {
            return "REJECTED";
        }
        if (value.contains("INTERVIEW")) {
            return "INTERVIEW";
        }
        if (value.contains("WAIT")) {
            return "WAITING";
        }
        return value;
    }

    /**
     * Helper class to hold parsed classification result.
     *
     * @param companyName The name of the company extracted from the response.
     * @param status The job status extracted from the response (Interview,
     * Rejected, Waiting).
     * @return ParsedClassification object containing the company name and
     * status.
     */
    private static class ParsedClassification {

        final String companyName;
        final JobStatus status;

        ParsedClassification(String companyName, JobStatus status) {
            this.companyName = companyName;
            this.status = status;
        }
    }

}
