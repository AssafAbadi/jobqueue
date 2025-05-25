package com.example.jobqueue.gmail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;

public class GmailReader {

    /**
     * reads the next email of unread emails from the Gmail inbox. it uses the
     * Gmail API to fetch the messages.
     *
     * @param service the Gmail service object
     * @param pageToken the token for the next page of messages
     * @return a ListMessagesResponse object containing the next page of
     * messages
     * @throws IOException if an error occurs while communicating with the Gmail
     * API
     */
    public static ListMessagesResponse getNextMessageSummaryPage(Gmail service, String pageToken) throws IOException {
        return service.users().messages().list("me")
                .setMaxResults(1L) //only 1 mail at a time to make the process faster becaue now we can classify the mail and fetch the next mail
                .setQ("in:inbox category:primary is:unread newer_than:2d") //only unread mails in the inbox
                .setIncludeSpamTrash(false) // don't include spam or trash
                .setPageToken(pageToken) //use the page token to get the next page of messages
                .execute();//no need for execption handling here gmail api will throw an exception if there is a problem
    }

    /**
     * fetches the plain text content of a message from the Gmail API.
     *
     * @param messageId the ID of the message to fetch
     * @param service the Gmail service object
     * @return an Optional containing the plain text content of the message, or
     * an empty Optional if not found
     * @throws IOException if an error occurs while communicating with the Gmail
     * API
     */
    public static Optional<String> getPlainTextFromMessage(String messageId, Gmail service) throws IOException {
        if (messageId == null || messageId.isEmpty()) {
            return Optional.empty();
        }

        Message fullMessage = service.users().messages().get("me", messageId)
                .setFormat("full")// fetch the full message including headers and body
                .execute();

        if (fullMessage == null || fullMessage.getPayload() == null) {
            return Optional.empty();
        }

        //if the mimeType is not text/plain then we need to get the text from the parts of the message
        //it can be multipart/alternative or multipart/mixed
        //if the message is multipart then we need to get the text from the parts of the message   
        return getTextFromMessageParts(Collections.singletonList(fullMessage.getPayload())
        );
    }

    /**
     * recursively fetches the plain text content from the message parts.
     *
     * @param parts the list of message parts to search
     * @return an Optional containing the plain text content, or an empty
     * Optional if not found
     * @throws IOException if an error occurs while decoding the message parts
     */
    private static Optional<String> getTextFromMessageParts(List<MessagePart> parts) throws IOException {
        if (parts == null || parts.isEmpty()) {
            return Optional.empty();
        }
        // Iterate through the message parts to find the plain text part
        for (MessagePart part : parts) {
            String mimeType = part.getMimeType();//ceck if mimeType of the part is text/plain
            if ("text/plain".equals(mimeType)) {
                MessagePartBody body = part.getBody();
                if (body != null && body.getData() != null) {
                    byte[] decodedBytes;
                    try {
                        decodedBytes = Base64.getUrlDecoder().decode(body.getData());// decode the base64url encoded data
                    } catch (IllegalArgumentException e) {
                        decodedBytes = Base64.getDecoder().decode(body.getData());//if the data is not base64url encoded then decode it using base64 decoder because gmail api sometimes returns base64 encoded data
                    }
                    return Optional.of(new String(decodedBytes, StandardCharsets.UTF_8));
                }
            } else if ("text/html".equalsIgnoreCase(mimeType)) {
                MessagePartBody body = part.getBody();
                if (body != null && body.getData() != null) {
                    byte[] decodedBytes;
                    try {
                        // Decode the base64url encoded data (Gmail usually uses base64url)
                        decodedBytes = Base64.getUrlDecoder().decode(body.getData());
                    } catch (IllegalArgumentException e) {
                        // If decoding as base64url fails, try decoding as standard base64
                        decodedBytes = Base64.getDecoder().decode(body.getData());
                    }
                    String htmlContent = new String(decodedBytes, StandardCharsets.UTF_8);
                    String plainText = stripHtmlTags(htmlContent); // Convert HTML to plain text
                    return Optional.of(plainText);
                }
            } else if (part.getParts() != null && !part.getParts().isEmpty()) {
                // If the part is multipart, recursively check its parts
                // This is to handle cases like multipart/alternative or multipart/mixed
                Optional<String> nestedText = getTextFromMessageParts(part.getParts());
                if (nestedText.isPresent()) {
                    return nestedText;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Strips HTML tags from a string to convert it to plain text.
     *
     * @param html the HTML string to strip
     * @return the plain text version of the string
     */
    private static String stripHtmlTags(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        // Use Jsoup to parse the HTML and extract the text
        Document doc = Jsoup.parse(html);
        return doc.text();
    }

}
