package com.example.jobqueue;
//DTO object for updating job status for better scalability and maintainability

public class StatusUpdateRequest {

    private String status;

    public StatusUpdateRequest() {
    }

    public StatusUpdateRequest(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
