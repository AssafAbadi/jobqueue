package com.example.jobqueue;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Integer> {
    Optional<Job> getJobByName(String name); 

}