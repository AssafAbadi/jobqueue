package com.example.jobqueue.gmail;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.example.jobqueue.Job;
import com.example.jobqueue.JobService;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.common.util.concurrent.RateLimiter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * EmailProcessor is responsible for the reading of emails from Gmail,
 * classifying them using GPT, and adding jobs to the database. It uses
 * asynchronous processing to handle potentially long-running tasks without
 * blocking.
 */
@Component
public class EmailProcessor {

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
                Gmail service = GmailService.getGmailService(); // Synchronous operation: get Gmail client

                String nextPageToken = null; // Holds the page token for the next email
                AtomicInteger processedCount = new AtomicInteger(0); // Atomic counter for processed emails
                final int maxEmailsToProcess = 100; // Safety limit to prevent infinite loop/overload

                // Loop to read and dispatch email processing one by one
                while (processedCount.get() < maxEmailsToProcess) {
                    // Get the next "page" (a single email) from the Gmail API
                    ListMessagesResponse response = GmailReader.getNextMessageSummaryPage(service, nextPageToken);
                    List<Message> messages = response.getMessages(); // Only get headers for efficiency

                    if (messages == null || messages.isEmpty()) {
                        System.out.println("No more new unread messages found from Gmail API.");
                        break; // Exit loop if no more messages
                    }

                    // Since we requested maxResults=1, we expect at most one message here
                    Message emailSummary = messages.get(0);
                    processedCount.incrementAndGet(); // Increment the processed email count
                    System.out.println("Initiating processing for email ID: " + emailSummary.getId() + " (Email " + processedCount + ")");

                    // --- Start the ASYNCHRONOUS processing chain for this SINGLE email ---
                    processSingleEmailAsync(emailSummary, service);// This method starts the processing chain for a single email
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
        }, gmailFetchExecutor); // This runs on the dedicated gmailFetchExecutor
    }

    /**
     * Processes a single email asynchronously: 1. Reads the full plain text
     * content of the email. 2. Classifies the email using GPT if content is
     * present. 3. Adds the classified job to the database if classification was
     * successful.
     *
     * @param emailSummary The summary of the email to process.
     * @param service The Gmail service instance to use for reading the email
     * content.
     */
    private void processSingleEmailAsync(Message emailSummary, Gmail service) {
        CompletableFuture
                .supplyAsync(() -> readEmailContentWithRateLimit(emailSummary.getId(), service), emailProcessingExecutor)
                .thenCompose(optionalContent -> classifyEmailIfPresent(optionalContent, emailSummary.getId()))
                .thenCompose(classifiedJob -> addClassifiedJobIfPresent(classifiedJob, emailSummary.getId()))
                .exceptionally(ex -> {
                    // Error handler for the entire chain
                    System.err.println("Failed to fully process email ID " + emailSummary.getId() + ". Error: " + ex.getCause().getMessage());
                    return null;
                });
    }

    /**
     * Acquire GPT rate limit and try reading the full plain text from the email
     *
     * @param messageId The ID of the email to read
     * @param service The Gmail service instance to use for reading the email
     * content
     * @return An Optional containing the plain text content of the email, or an
     * empty Optional if not found
     * @throws CompletionException if reading the email content fails
     */
    private Optional<String> readEmailContentWithRateLimit(String messageId, Gmail service) {
        gptRateLimiter.acquire(); // Block if needed to respect rate limits
        try {
            return GmailReader.getPlainTextFromMessage(messageId, service);
        } catch (IOException e) {
            throw new CompletionException("Failed to read full email content for ID " + messageId, e);
        }
    }

    /**
     * Classify email if plain text is present
     *
     * @param plainTextContent The plain text content of the email, wrapped in
     * an Optional
     * @param messageId The ID of the email being processed
     * @return A CompletableFuture that completes with the classified Job, or
     * null if classification was skipped
     */
    private CompletableFuture<Job> classifyEmailIfPresent(Optional<String> plainTextContent, String messageId) {
        if (plainTextContent.isPresent()) {
            return gptClassifierService.classifyEmail(plainTextContent.get());
        } else {
            System.out.println("No plain text content found for email ID " + messageId + ". Skipping classification.");
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * If the classifier returned a job, add it to DB
     *
     * @param classifiedJob The Job object returned by the classifier, or null
     * if classification was skipped
     * @param messageId The ID of the email being processed
     * @return A CompletableFuture that completes when the job is added to the
     * database, or null if no job was classified
     */
    private CompletableFuture<Void> addClassifiedJobIfPresent(Job classifiedJob, String messageId) {
        if (classifiedJob != null) {
            System.out.println("Attempting to add classified job: " + classifiedJob.getName() + " for email ID " + messageId);
            return CompletableFuture
                    .supplyAsync(() -> jobService.addJob(classifiedJob), emailProcessingExecutor)
                    .thenApply(ignored -> null);
        } else {
            System.out.println("Email ID " + messageId + " either updated existing job or was ignored by classifier.");
            return CompletableFuture.completedFuture(null);
        }
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
