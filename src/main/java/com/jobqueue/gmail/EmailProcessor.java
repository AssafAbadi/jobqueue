package com.jobqueue.gmail;

import java.io.IOException;
import static java.lang.System.getenv;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Component;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.common.util.concurrent.RateLimiter;
import com.jobqueue.Job;
import com.jobqueue.JobController;

import jakarta.annotation.PostConstruct;

@Component
public class EmailProcessor {

    private final JobController jobController;

    public EmailProcessor(JobController jobController) {
        this.jobController = jobController;
    }

    @PostConstruct // This method will be called after the bean is initialized
    public void processInbox() throws Exception {
        Gmail service = GmailService.getGmailService();
        List<Message> emailContents = GmailReader.readInbox(service);
        String apiKey = getenv("OPENAI_API_KEY");
        GptClassifierService classifierService = new GptClassifierService(apiKey,jobController);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        RateLimiter rateLimiter = RateLimiter.create(3.0 / 60.0);


        for (Message emailContent : emailContents) {// Loop through each email message
            try {
                rateLimiter.acquire();
                
                Job job = classifierService.classifyEmail(
                    GmailReader.getPlainTextFromMessage(emailContent, service)
                );//getting the job from the classifier
    
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        if (job != null) {
                            jobController.addJob(job); // Add the job to the database if it's not already there
                        } 
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to process job", e);
                    }
                }).whenComplete((res, ex) -> {
                    if (ex != null) {
                        System.err.println("Failed to process job: " + ex.getMessage());
                    } else {
                        System.out.println("Job processed successfully.");
                    }
                });
    
                futures.add(future);
    
            } catch (IOException e) {
                System.err.println("Failed to process email: " + e.getMessage());
            }
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();// Wait for all tasks to complete

    }
}
