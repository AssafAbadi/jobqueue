package com.example.jobqueue.gmail;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

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
                Email content: """ + emailContent;

        ChatMessage message = new ChatMessage(ChatMessageRole.USER.value(), prompt);// Create a ChatMessage object with the prompt

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
                .messages(Arrays.asList(message))
                .build();// Create a ChatCompletionRequest object with the message

        // 1. Asynchronously call OpenAI API
        return CompletableFuture.supplyAsync(() -> {
            try {
                ChatCompletionResult result = openAiApiService.createChatCompletion(request);//this is a blocking call to the OpenAI API
                return result.getChoices().get(0).getMessage().getContent().trim();//trim the response to remove leading and trailing whitespace
            } catch (Exception e) {
                System.err.println("Error calling OpenAI API: " + e.getMessage());
                // Wrap and re-throw to be caught by .exceptionally later in the chain
                throw new CompletionException("Failed to get response from OpenAI API", e);
            }
        }).thenCompose(response -> { // Use thenCompose for nesting CompletableFutures
            // 2. Process the response from ChatGPT
            if (response == null || "null".equalsIgnoreCase(response)) {
                System.out.println("GPT returned null, email ignored.");
                // Return a CompletableFuture that is already completed with a null result
                return CompletableFuture.completedFuture(null);
            }

            // Using Regex for parsing of "Company Name: Status" format
            Pattern pattern = Pattern.compile("([^:]+):\\s*(.*)");//this is the regex pattern to match the company name and status
            Matcher matcher = pattern.matcher(response);// Create a Pattern and Matcher to extract company name and status

            String companyName;
            String status;

            if (matcher.matches()) { // This checks if the response matches the expected format of "Company Name: Status"
                companyName = matcher.group(1).trim();// Extract the company name
                status = matcher.group(2).trim();// Extract the status
            } else {
                System.err.println("GPT response not in expected format: '" + response + "'");
                // If the format is wrong, we treat it as unclassifiable
                return CompletableFuture.completedFuture(null);
            }

            System.out.println("Classified Company: " + companyName + ", Status: " + status);

            // 3. Asynchronously check and update/create the Job using JobService
            // This nested supplyAsync ensures the database operation also runs in a separate thread
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // Attempt to retrieve the job by name. This is a synchronous call to JobService.
                    return jobService.createOrUpdateJob(companyName, status);
                } catch (Exception e) {
                    // Catch any other unexpected exceptions during JobService interaction
                    System.err.println("Error processing classified job for company '" + companyName + "': " + e.getMessage());
                    throw new CompletionException("Failed to process classified job in DB", e);
                }
            });
        }).exceptionally(ex -> {
            // This .exceptionally handles any CompletionException (or other RuntimeExceptions)
            // that occurred earlier in the CompletableFuture chain.
            System.err.println("Final classification error for email: " + emailContent + ". Cause: " + ex.getCause().getMessage());
            throw new CompletionException("Failed to classify email", ex.getCause());
        });
    }
}
