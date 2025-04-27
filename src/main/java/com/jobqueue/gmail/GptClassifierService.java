package com.jobqueue.gmail;

import java.util.Arrays;

import com.jobqueue.Job;
import com.jobqueue.JobController;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

public class GptClassifierService {

    private final OpenAiService service;
    private final JobController jobController;

    public GptClassifierService(String apiKey, JobController jobController) {
        this.service = new OpenAiService(apiKey);
        this.jobController = jobController; 
    }

    public Job classifyEmail(String emailContent) {//classify the email content
         String prompt = """
                         Classify the content of this email into one of the following categories: Interview, Rejected, Waiting.
                         
                         Instructions:
                         1. Analyze the email content for keywords and phrases strongly indicating one of the three categories.
                         2. If the email clearly indicates a rejection, the category should be 'Rejected'. Look for phrases like 'sorry but', 'we will not be moving forward', 'your application was not successful', 'we are unable to offer you', 'rejected'.
                         3. If the email invites you for an interview or discusses scheduling an interview, the category should be 'Interview'. Look for phrases like 'interview invitation', 'we would like to schedule an interview', 'available times for an interview', 'next steps in the interview process'.
                         4. If the email suggests your application is still under consideration, or you are in a pool of candidates, or there will be further communication at a later date without a clear interview invitation or rejection, the category should be 'Waiting'. Look for phrases like 'your application is under review', 'we will be in touch', 'we are still evaluating candidates', 'your profile has been shortlisted'.
                         5. If the email content is not clearly related to a job application or the job search process at all, you should ignore it and no category is needed.
                         6. Return your classification in the format: 'Company Name: Category'.
                         7. The 'Company Name' should be the name of the company that sent the email, if it is clearly identifiable in the email content. 
                         8. If the email is ignored (not related to job search), return null. "Return the response in the format 'Company Name: Status' only. If the email is not related to job search, return null.". Email content: """ //
         //
         //
         //
         //
         //
         //
         //
         //
         //
          + emailContent;
        
       
        ChatMessage message = new ChatMessage(ChatMessageRole.USER.value(), prompt);
        
        
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
                .messages(Arrays.asList(message))
                .build();

      
        ChatCompletionResult result = service.createChatCompletion(request);

        //get the response from ChatGPT
        String response = result.getChoices().get(0).getMessage().getContent().trim();
        String[] parts = response.split(":");
        if (parts.length < 2) {
            return null; // If the response is not in the expected format, return null
        }
        String companyName = parts[0].trim();
        String status = parts[1].trim();
        System.out.println("Company Name: " + companyName);
        System.out.println("Status: " + status);
         int jobId=jobController.getJobByName(companyName);
        //if the job already exists, return null
        if(jobId != 0) { 
            jobController.updateJobStatus(jobId,status);//update the status of the job 
            return null;
        }else{//if the job doesn't exist, create a new job
        Job job = new Job();
        job.setName(companyName);
        job.setStatus(status);

        return job;
        }
    }
}
