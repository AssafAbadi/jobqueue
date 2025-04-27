package com.jobqueue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "JobQueue")  
public class Job {

    @Id //indicates that this field is the primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY) //indicates that the value of this field will be generated automatically by the database
    private int id;

    @Column(nullable = false) 
    private String name;

    @Column(nullable = false)  
    private String status;

    
    public Job() {}

    
    public Job(String name, String status) {
        this.name = name;
        this.status = status;
    }

    
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
