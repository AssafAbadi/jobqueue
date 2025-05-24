Here's a detailed and comprehensive README for your Job Search Tracker project in English:

---

# Job Search Tracker

## Overview

Job Search Tracker is an independent project designed to integrate with Gmail and OpenAI APIs for analyzing and classifying job search-related emails. The project stores the status of each email using a PostgreSQL database. It utilizes HTTP protocols, asynchronous programming, and Maven for project management and dependencies.

## Key Features

* **Gmail API Integration:** Enables fetching and parsing of job search-related emails.
* **OpenAI API Integration:** Leverages OpenAI's Natural Language Processing (NLP) capabilities for email content classification and analysis.
* **Email Status Management:** Stores the status of each email (e.g., "Resume Sent", "Interview Scheduled", "Rejection") in a database.
* **PostgreSQL Database:** For consistent and reliable data management.
* **Asynchronous Programming:** For improved performance and responsiveness.
* **Maven:** For efficient project dependency management and build automation.

## Technologies Used

* **Java 17+**
* **Spring Boot**
* **Maven**
* **PostgreSQL**
* **Gmail API**
* **OpenAI API**
* **HTTP Protocols**
* **Asynchronous Programming**

## Setup and Running the Project

To run the Job Search Tracker application locally, follow these steps:

### 1. Prerequisites

* **Java Development Kit (JDK) 17 or higher:** Download and install from [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) or [OpenJDK](https://openjdk.org/install/).
* **Maven:** Typically included with Spring Boot CLI or integrated into IDEs like IntelliJ IDEA. If not, download from [Apache Maven](https://maven.apache.org/download.cgi).
* **PostgreSQL Server:**
    * Install PostgreSQL on your machine. You can download it from [PostgreSQL Downloads](https://www.postgresql.org/download/).
    * Ensure the PostgreSQL server is running on port `5432`.
    * Create a new database named `jobqueue`. You can do this using a management tool like `pgAdmin` or by running the SQL command:
        ```sql
        CREATE DATABASE jobqueue;
        ```
* **Gmail API Access:**
    * Enable the Gmail API in your Google Cloud Platform project.
    * Create OAuth 2.0 Client IDs for your application.
    * Download the JSON credentials file and place it at `src/main/resources/tokens/StoredCredential`
* **OpenAI API Access:**
    * Sign up for OpenAI and obtain an API key.

### 2. Project Configuration (`application.properties.EXAMPLE`)

This file contains the basic application settings.

1.  **Create a new file named `application.properties`** in the `src/main/resources/` directory.
2.  **Copy the content** from `application.properties.EXAMPLE` into your new `application.properties` file.
3.  **Update the following values** in your `application.properties` file:

    ```properties
    spring.application.name=jobqueue

    # PostgreSQL Database Configuration
    spring.datasource.url=jdbc:postgresql://localhost:5432/jobqueue
    spring.datasource.username=postgres # Your PostgreSQL username
    spring.datasource.password=your_db_password # Replace with your actual database password

    # Hibernate/JPA Configuration
    spring.jpa.hibernate.ddl-auto=update # Recommended for development only; use migration tools like Flyway/Liquibase for production
    spring.jpa.show-sql=true
    spring.jpa.properties.hibernate.format_sql=true
    spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

    # OpenAI API Key
    openai.api.key=your_openai_api_key # Replace with your OpenAI API key

    # Gmail API Configuration (Additional settings might be here depending on your implementation)
    # gmail.api.client.id=your_gmail_client_id
    # gmail.api.client.secret=your_gmail_client_secret
    ```
    * **`spring.datasource.username` and `spring.datasource.password`:** Replace these with your PostgreSQL database connection credentials.
    * **`openai.api.key`:** Replace with your confidential OpenAI API key.
    * **Note on Gmail API:** Exact details for Gmail API setup might vary based on how your code handles authentication (often via a `credentials.json` file generated from Google Cloud Console). If you have a `credentials.json` file downloaded from Google, ensure it is placed in the correct path your code expects (e.g., `src/main/resources/tokens/StoredCredential`) and is excluded by your `.gitignore`.

### 3. Building and Running the Project

**Using Maven:**

1.  Open your terminal or command prompt in the project's root directory.
2.  Build the project:
    ```bash
    mvn clean install
    ```
    This command will download all dependencies, compile the code, and generate the JAR file in the `target/` directory.
3.  Run the application:
    ```bash
    java -jar target/jobqueue-application.jar # Or the exact name of the generated JAR file
    ```

**Using an IDE (e.g., IntelliJ IDEA or VS Code with appropriate extensions):**

1.  Open the project in your IDE.
2.  Ensure your JDK is correctly configured.
3.  Locate your main application file 'JobqueueApplication.java`.
4.  Click the "Run" button next to it.


## Contributing

If you wish to contribute to the project, please fork the repository, create a new branch, and submit a Pull Request with your changes.


