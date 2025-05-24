package com.example;
// JobNotFoundException is a custom exception class that extends RuntimeException.
// It is used to indicate that a job was not found in the system.
// This exception can be thrown when trying to retrieve a job by its ID or name,
// and the job does not exist in the database.

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(String message) {
        super(message);
    }
}
