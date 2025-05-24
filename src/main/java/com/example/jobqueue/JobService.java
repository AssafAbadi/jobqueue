package com.example.jobqueue;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

@Service
@Transactional// Ensures atomicity and rollback for write operations

public class JobService {// This class is responsible for business logic and data access

    final private JobRepository jobRepository;

    // Constructor injection: Spring will automatically inject JobRepository
    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * Adds a new Job to the database. This operation is synchronous and
     * transactional.
     *
     * @param job The Job object to add.
     * @return The saved Job object with potentially generated ID.
     */
    public Job addJob(Job job) { // Changed to synchronous and returns Job
        return jobRepository.save(job);//we dont throw an exception because save will throw one for us, the DB handles the problem
    }

    /**
     * Retrieves a Job by its ID. This operation is synchronous and read-only.
     *
     * @param id The ID of the job to retrieve.
     * @return The found Job object.
     * @throws JobNotFoundException if the Job with the given ID is not found.
     */
    public Job getJob(int id) { // Changed to synchronous and returns Job
        return jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Job with ID " + id + " not found"));//the DB won't throw an exception as this is a standart situation so we need to decide for our busines logic to throw an exception
    }

    /**
     * Retrieves a Job by its name. This operation is synchronous and read-only.
     *
     * @param name The name of the job to retrieve.
     * @return The found Job object.
     * @throws JobNotFoundException if the Job with the given name is not found.
     */
    public Job getJobIdByName(String name) {
        return jobRepository.getJobByName(name)
                .orElseThrow(() -> new JobNotFoundException("Job with name '" + name + "' not found"));
    }

    /**
     * Creates or updates a Job based on its name. This operation is synchronous
     * and transactional. I created this method in order to use both methods
     * (getJobIdByName and addJob) in one method for transactional purposes if i
     * use the methods inside the method it might cause the transaction to fail
     * because if the first one failed then spring will cancel the transcation
     * and the second one will be a new transaction
     *
     * @param companyName The name of the company/job.
     * @param status The status to set for the job.
     * @return The created or updated Job object.
     */
    public Job createOrUpdateJob(String companyName, String status) {
        Optional<Job> existingJobOptional = jobRepository.getJobByName(companyName); // שימוש ב-findByName מה-Repository

        if (existingJobOptional.isPresent()) {
            Job existingJob = existingJobOptional.get();
            existingJob.setStatus(status);
            System.out.println("Updated existing job: " + companyName + " to status: " + status);
            return jobRepository.save(existingJob);
        } else {
            Job newJob = new Job();
            newJob.setName(companyName);
            newJob.setStatus(status);
            System.out.println("Created new job: " + companyName + " with status: " + status);
            return jobRepository.save(newJob);
        }
    }

    /**
     * Retrieves all Jobs from the database. This operation is synchronous and
     * read-only.
     *
     * @return A list of all Job objects.
     */
    public List<Job> getAllJobs() {
        List<Job> jobs = jobRepository.findAll();
        if (jobs.isEmpty()) {
            throw new JobNotFoundException("No jobs found");
        }
        return jobs;
    }

    //* 
    //* This method is synchronous in order to guarantee the transactional integrity of the operation.
    //* we then make the controller method async and then it will call this method
    //* because if it was async the @Transactional would not work
    //*
    /**
     * Updates the status of a Job by its ID. This operation is synchronous and
     * transactional. This method is synchronous in order to guarantee the
     * transactional integrity of the operation we then make the controller
     * method async and then it will call this method because if it was async
     * the @Transactional would not work
     *
     * @param id The ID of the job to update.
     * @param status The new status to set for the job.
     * @throws JobNotFoundException if the Job with the given ID is not found.
     */
    public void updateJobStatus(int id, String status) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Cannot update: Job with ID " + id + " not found"));
        job.setStatus(status);
        jobRepository.save(job);
    }

    /**
     * Deletes a Job by its ID. This operation is synchronous and transactional.
     *
     * @param id The ID of the job to delete.
     * @throws JobNotFoundException if the Job with the given ID is not found.
     */
    public void deleteJob(int id) { // Changed to synchronous and returns void
        if (!jobRepository.existsById(id)) {
            throw new JobNotFoundException("Cannot delete: Job with ID " + id + " not found");
        }
        jobRepository.deleteById(id);
    }

    /**
     * Deletes all Jobs from the database. This operation is synchronous and
     * transactional.
     *
     * @throws JobNotFoundException if no jobs are found to delete.
     */
    public void deleteAllJobs() {
        jobRepository.deleteAll();

    }

}
