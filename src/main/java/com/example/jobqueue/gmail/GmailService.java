package com.example.jobqueue.gmail;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.security.GeneralSecurityException;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;



/**
 * This class provides methods to create a Gmail service instance using OAuth2
 * credentials. It reads the credentials from a JSON file and stores the access
 * tokens in a specified directory.
 */
public class GmailService {
    private static final String APPLICATION_NAME = "JobQueue Gmail Client";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();// Use GsonFactory for JSON parsing 
    private static final String TOKENS_DIRECTORY_PATH = "src/main/resources/tokens";
    private static final List<String> SCOPES = List.of(GmailScopes.GMAIL_READONLY);
    private static final String CREDENTIALS_FILE_PATH = "src/main/resources/credentials.json";


    /**
     * Creates a Gmail service instance using OAuth2 credentials.
     *
     * @return a Gmail service instance
     * @throws IOException if an error occurs while reading the credentials file
     * @throws GeneralSecurityException if an error occurs while creating the HTTP
     *                                  transport
     */
    
    public static Gmail getGmailService() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();// Create a trusted HTTP transport for the authorization flow
        Credential credential = getCredentials(HTTP_TRANSPORT);// Get the OAuth2 credentials from the JSON file
        return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();// Create the Gmail service from the credentials
    }

    /**
     * Reads the OAuth2 credentials from a JSON file and returns a Credential
     * object.
     *
     * @param HTTP_TRANSPORT the HTTP transport to use for the authorization flow
     * @return a Credential object containing the OAuth2 credentials
     * @throws IOException if an error occurs while reading the credentials file
     */

    private static Credential getCredentials(NetHttpTransport HTTP_TRANSPORT) throws IOException {
        Reader reader = new FileReader(CREDENTIALS_FILE_PATH);// Read the credentials from the JSON file
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);// Load the client secrets from the JSON file

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))// Set the data store factory to save tokens
                .setAccessType("offline")
                .build();// Create a flow to handle the OAuth2 authorization process

        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");// Authorize the user and return the credentials
    }
}