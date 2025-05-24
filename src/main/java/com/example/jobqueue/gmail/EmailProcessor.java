package com.example.jobqueue.gmail;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.common.util.concurrent.RateLimiter;
import com.example.jobqueue.JobService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class EmailProcessor {// This class orchestrates the reading of emails from Gmail, classifying them using GPT, and adding jobs to the database.

    private final JobService jobService;
    private final GptClassifierService gptClassifierService;
    private final RateLimiter gptRateLimiter;

    // Executor for sequential Gmail API calls to fetch message summaries
    private final ExecutorService gmailFetchExecutor;
    // Executor for the processing chain of each individual email (content reading, classification, DB operations)
    private final ExecutorService emailProcessingExecutor;

    public EmailProcessor(JobService jobService, GptClassifierService gptClassifierService) {
        this.jobService = jobService;
        this.gptClassifierService = gptClassifierService;
        // Limit to 3 requests per minute for the OpenAI API
        this.gptRateLimiter = RateLimiter.create(3.0 / 60.0);

        // A single-threaded executor ensures that Gmail API list calls happen sequentially.
        // This prevents overloading Gmail and allows safe management of the pageToken.
        this.gmailFetchExecutor = Executors.newSingleThreadExecutor();

        // A CachedThreadPool is good for I/O-bound tasks like network/DB calls,
        // as it creates threads on demand and reuses them.
        this.emailProcessingExecutor = Executors.newCachedThreadPool();
    }

    /**
     * This method is called after the Spring context is initialized. It starts
     * processing the inbox asynchronously.
     */
    @PostConstruct
    public void processInbox() {
        System.out.println("EmailProcessor initiated. Starting to process inbox asynchronously...");

        // Launches the entire email reading and processing in a separate thread.
        // This is done to avoid blocking the main application thread during startup.
        // This immediately frees up the @PostConstruct thread, allowing the application to start quickly.
        CompletableFuture.runAsync(() -> {
            try {
                Gmail service = GmailService.getGmailService(); // Synchronous operation: Get the Gmail service client

                String nextPageToken = null; // Holds the page token for the next email
                AtomicInteger processedCount = new AtomicInteger(0); // Atomic counter for processed emails
                final int maxEmailsToProcess = 100; // Safety limit to prevent infinite loop/overload

                // Loop to read and dispatch email processing one by one
                while (processedCount.get() < maxEmailsToProcess) {
                    // Get the next "page" (a single email) from the Gmail API
                    ListMessagesResponse response = GmailReader.getNextMessageSummaryPage(service, nextPageToken);
                    List<Message> messages = response.getMessages(); // I only get the headers of the emails to save time and resources

                    if (messages == null || messages.isEmpty()) {
                        System.out.println("No more new unread messages found from Gmail API.");
                        break; // Exit loop if no more messages
                    }

                    // Since we requested maxResults=1, we expect at most one message here
                    Message emailSummary = messages.get(0);
                    processedCount.incrementAndGet();// Increment the processed email count
                    System.out.println("Initiating processing for email ID: " + emailSummary.getId() + " (Email " + processedCount + ")");

                    // --- Start the ASYNCHRONOUS processing chain for this SINGLE email ---
                    CompletableFuture.supplyAsync(() -> {
                        // A. Apply the GPT rate limit BEFORE reading email content and classifying.
                        // This will block the specific thread processing this email if needed.
                        gptRateLimiter.acquire();

                        try {
                            // B. Read the full email content. This operation is dispatched to emailProcessingExecutor.
                            // This allows the email content reading to be non-blocking and run in parallel with other tasks.
                            // Note: This is a synchronous call to Gmail API, but it runs in the emailProcessingExecutor thread.
                            return GmailReader.getPlainTextFromMessage(emailSummary.getId(), service);
                        } catch (IOException e) {
                            throw new CompletionException("Failed to read full email content for ID " + emailSummary.getId(), e);
                        }
                    }, emailProcessingExecutor) // Steps A and B run in emailProcessingExecutor
                            .thenCompose(optionalEmailContent -> { // We receive an Optional<String> for the email content
                                if (optionalEmailContent.isPresent()) {
                                    // C. Once content is ready, asynchronously classify it using GPT.
                                    return gptClassifierService.classifyEmail(optionalEmailContent.get());// This is a non-blocking call 
                                    // to the OpenAI API and will return a CompletableFuture<Job>.
                                } else {
                                    System.out.println("No plain text content found for email ID " + emailSummary.getId() + ". Skipping classification.");
                                    return CompletableFuture.completedFuture(null); // No content, so no Job
                                }
                            })
                            .thenCompose(classifiedJob -> {
                                // D. Once classified, if a new Job needs to be added, do so asynchronously.
                                if (classifiedJob != null) {
                                    System.out.println("Attempting to add classified job: " + classifiedJob.getName() + " for email ID " + emailSummary.getId());
                                    return CompletableFuture.supplyAsync(() -> jobService.addJob(classifiedJob), emailProcessingExecutor)// This is a non-blocking call to the database although addJob is a synchronous call
                                            .thenApply(newlyAddedJob -> null);
                                } else {
                                    System.out.println("Email ID " + emailSummary.getId() + " either updated existing job or was ignored by classifier.");
                                    return CompletableFuture.completedFuture(null);
                                }
                            })
                            .exceptionally(ex -> {
                                // E. Handle exceptions at any stage in this email's processing chain.
                                System.err.println("Failed to fully process email ID " + emailSummary.getId() + ". Error: " + ex.getCause().getMessage());
                                return null; // Allows this specific Future to complete without crashing the whole application
                            });
                    // --- End of ASYNCHRONOUS processing chain for this SINGLE email ---

                    // Get the token for the next email from the current response
                    nextPageToken = response.getNextPageToken();
                    if (nextPageToken == null) {
                        System.out.println("Last email page fetched. No more page tokens.");
                        break; // No more emails to read
                    }

                }
                System.out.println("Finished initiating processing for a total of " + processedCount + " emails.");

            } catch (IOException e) {
                System.err.println("IO error during email fetching and processing orchestration: " + e.getMessage());
            } catch (RuntimeException e) {
                System.err.println("Runtime error during email fetching and processing orchestration: " + e.getMessage());
            } catch (GeneralSecurityException e) {
                System.err.println("Unexpected error during email fetching and processing orchestration: " + e.getMessage());
            }
        }, gmailFetchExecutor); // This  runs on the dedicated gmailFetchExecutor
    }

    /**
     * Shutdown procedures for Executors when the application closes. It's
     * crucial in order to release thread resources gracefully.
     */
    @PreDestroy
    public void shutdownExecutors() {
        System.out.println("Shutting down EmailProcessor executors...");
        gmailFetchExecutor.shutdown();
        emailProcessingExecutor.shutdown();
        try {
            // Waits for Executors to terminate their ongoing tasks, up to 5 seconds.
            if (!gmailFetchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                // If they don't terminate, forces shutdown.
                gmailFetchExecutor.shutdownNow();
            }
            if (!emailProcessingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                emailProcessingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            // In case of interruption during waiting
            Thread.currentThread().interrupt();//interrupt the current thread in order to wake it up
            System.err.println("Executors shutdown interrupted.");
        }
        System.out.println("EmailProcessor executors shut down.");
    }
}
