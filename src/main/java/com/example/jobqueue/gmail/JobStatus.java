package com.example.jobqueue.gmail;

/**
 * Enum representing the possible statuses of a job application. This is used to
 * classify the status of a job application based on email content.
 * Using uppercase names for enum constants to ensure java naming conventions.
 */
public enum JobStatus {
    REJECTED,
    INTERVIEW,
    WAITING;

    /**
     * Checks if the provided string is a valid JobStatus. This method trims the
     * input and compares it against the enum names, ignoring case.
     *
     * * @param value The string to check.
     * @return true if the string matches any JobStatus name, false otherwise.
     */
    public static boolean isValid(String value) {
        for (JobStatus status : JobStatus.values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }
}
