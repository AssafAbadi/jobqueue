package com.example.jobqueue;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.JobNotFoundException;

@RestController
@RequestMapping("/jobs") //define the base URL for this controller
public class JobController {

    private final JobService jobService;

    // Constructor injection: Spring will automatically inject JobService
    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Add a new job.
     *
     * @param job the job to add
     * @return a CompletableFuture containing the ResponseEntity with status 201
     * Created
     * @throws RuntimeException if an error occurs while adding the job
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<Void>> addJob(@RequestBody Job job) {
        return CompletableFuture.runAsync(() -> {
            try {
                jobService.addJob(job); // Synchronous call
            } catch (Exception ex) {
                // Catch any unexpected exceptions and re-throw as ResponseStatusException
                System.err.println("Error adding job: " + ex.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to add job", ex);
            }
        }).thenApply(aVoid -> ResponseEntity.status(HttpStatus.CREATED).build());
    } // Return 201 Created on success

    /**
     * Get a job by its ID.
     *
     * @param id the ID of the job to retrieve
     * @return a CompletableFuture containing the ResponseEntity with the job
     *  * @throws RuntimeException if the job is not found
     */
    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<Job>> getJob(@PathVariable int id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return jobService.getJob(id);//synchronous call
            } catch (JobNotFoundException ex) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job with ID " + id + " not found", ex);
            } catch (Exception ex) {
                System.err.println("Error getting job by ID " + id + ": " + ex.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve job", ex);
            }
        }).thenApply(ResponseEntity::ok);// Return 200 OK with the job
    }

    /**
     * Get a job by its name.
     *
     * @param name the name of the job to retrieve
     * @return a CompletableFuture containing the ResponseEntity with the job ID
     * @throws RuntimeException if the job is not found
     */
    @GetMapping("/name")
    public CompletableFuture<ResponseEntity<Integer>> getJobByName(@RequestParam String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return jobService.getJobIdByName(name).getId();
            } catch (JobNotFoundException ex) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job with name '" + name + "' not found", ex);
            } catch (Exception ex) {
                System.err.println("Error getting job by name '" + name + "': " + ex.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve job by name", ex);
            }
        }).thenApply(ResponseEntity::ok);
    }

    /**
     * Get all jobs.
     *
     * @return a CompletableFuture containing the ResponseEntity with the list
     * of jobs
     * @throws RuntimeException if no jobs are found
     */
    @GetMapping
    public CompletableFuture<ResponseEntity<List<Job>>> getAllJobs() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Job> jobs = jobService.getAllJobs();
                if (jobs == null || jobs.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No jobs found");
                }
                return jobs;
            } catch (ResponseStatusException ex) {
                System.err.println("Error getting all jobs: " + ex.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve all jobs", ex);
            }
        }).thenApply(ResponseEntity::ok);
    }

    /**
     * Update the status of a job.
     *
     * @param id the ID of the job to update
     * @param request the request body containing the new status
     * @return a CompletableFuture containing the ResponseEntity with status 200
     * OK
     * @throws RuntimeException if the job is not found or if an error occurs
     */
    @PutMapping("/{id}")
    public CompletableFuture<ResponseEntity<Void>> updateJobStatus(@PathVariable int id, @RequestBody StatusUpdateRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                jobService.updateJobStatus(id, request.getStatus());
            } catch (JobNotFoundException ex) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job with ID " + id + " not found", ex);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                System.err.println("Error updating job status for ID " + id + ": " + ex.getMessage());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request to update job status", ex);
            }
        }).thenApply(aVoid -> ResponseEntity.ok().build());
    }

    /**
     * Delete a job by its ID.
     *
     * @param id the ID of the job to delete
     * @return a CompletableFuture containing the ResponseEntity with status 204
     * No Content
     * @throws RuntimeException if the job is not found or if an error occurs
     */
    @DeleteMapping("/{id}")
    public CompletableFuture<ResponseEntity<Void>> deleteJob(@PathVariable int id) {
        return CompletableFuture.runAsync(() -> {
            try {
                jobService.deleteJob(id);
            } catch (JobNotFoundException ex) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job with ID " + id + " not found", ex);
            } catch (Exception ex) {
                System.err.println("Error deleting job with ID " + id + ": " + ex.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete job", ex);
            }
        }).thenApply(aVoid -> ResponseEntity.noContent().build());
    }

    /**
     * Deletes all jobs. This method is asynchronous to avoid blocking the web
     * server thread.
     *
     * @return A CompletableFuture<Void> representing the asynchronous operation
     * completion.
     */
    @DeleteMapping // No path variable for deleting all
    public CompletableFuture<ResponseEntity<Void>> deleteAllJobs() {
        return CompletableFuture.runAsync(() -> {
            try {
                jobService.deleteAllJobs(); // Synchronous call
            } catch (Exception ex) {
                // Catch any unexpected exceptions and return 500
                System.err.println("Error deleting all jobs: " + ex.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete all jobs", ex);
            }
        }).thenApply(aVoid -> ResponseEntity.noContent().build()); // Return 204 No Content on successful deletion
    }
}
