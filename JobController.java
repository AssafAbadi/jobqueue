package com.example.jobqueue;


import java.util.List;
import java.util.Optional;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs") //define the base URL for this controller
public class JobController {

    private final JobService jobService;

   
    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public void addJob(@RequestBody Job job) {
        jobService.addJob(job);
    }

    @GetMapping("/{id}")
    public Job getJob(@PathVariable int id) {
        Optional<Job> jobOptional = jobService.getJob(id).join();
        if (jobOptional.isPresent()) {
            return jobOptional.get();
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
}

@GetMapping("/name")
public int getJobByName(@RequestParam String name) {
    Optional<Job> jobOptional = jobService.getJobByName(name).join();
    if (jobOptional.isPresent()) {
        return jobOptional.get().getId();
    } else {
        return 0;
    }
}


    @GetMapping
    public List<Job> getAllJobs() {
        List<Job> jobs = jobService.getAllJobs().join();
        if(jobs!=null)
            return jobs;
        else
            throw new RuntimeException("No jobs found");
    }


    @PutMapping("/{id}")
    public void updateJobStatus(@PathVariable int id, @RequestBody String status) {
        jobService.updateJobStatus(id, status);
    }

    
    @DeleteMapping("/{id}")
    public void deleteJob(@PathVariable int id) {
        jobService.deleteJob(id);
    }
}

