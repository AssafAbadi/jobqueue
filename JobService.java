package com.example.jobqueue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class JobService {

    final private JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

   
    @Async
    public CompletableFuture<Void> addJob(Job job) {
        jobRepository.save(job);
        return CompletableFuture.completedFuture(null);
    }

   
    @Async
    public CompletableFuture<Optional<Job>> getJob(int id) {
        Optional<Job> job = jobRepository.findById(id);
        if (job.isPresent()) {
            return CompletableFuture.completedFuture(job);
        } else {
            throw new RuntimeException("Job not found"); 
        }
    }

    @Async
    public CompletableFuture<Optional<Job>> getJobByName(String name) {
        Optional<Job> job = jobRepository.getJobByName(name);
            return CompletableFuture.completedFuture(job); 
    }

    @Async
    public CompletableFuture<List<Job>> getAllJobs() {
        List<Job> jobs = jobRepository.findAll();
        return CompletableFuture.completedFuture(jobs);
    }

    
    @Async
    public CompletableFuture<Void> updateJobStatus(int id, String status) {
        Job job = jobRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Job not found"));
        job.setStatus(status);
        jobRepository.save(job);
        return CompletableFuture.completedFuture(null);
}

@Async
public CompletableFuture<Void> deleteJob(int id) {
        if (!jobRepository.existsById(id)) {
            throw new RuntimeException("Job not found");
        }
        jobRepository.deleteById(id);
        return CompletableFuture.completedFuture(null);
}

    @Async
    public CompletableFuture<Void> deleteAllJobs() {
        jobRepository.deleteAll();
        return CompletableFuture.completedFuture(null);
    }
}
