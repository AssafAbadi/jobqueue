package com.jobqueue.gmail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;

public class GmailReader {

    public static List<Message> readInbox(Gmail service) throws IOException {
    ListMessagesResponse messagesResponse = service.users().messages().list("me")
            .setMaxResults(50L)
            .setQ("in:inbox category:primary is:unread newer_than:1d") // Filter for unread messages in the primary category from the last day
            .setIncludeSpamTrash(false) // Exclude spam and trash
            .execute();

            List<Message> messages = messagesResponse.getMessages();
            if (messages == null) {
                messages = new ArrayList<>();
            }
            return messages;   
        }


    public static String getPlainTextFromMessage(Message message, Gmail service) throws IOException {
            Message fullMessage = service.users().messages().get("me", message.getId()).setFormat("full").execute();
            MessagePart payload = fullMessage.getPayload();// Get the body of the message
            return getTextFromPayload(payload);
}

private static String getTextFromPayload(MessagePart part) {
    if (part.getParts() != null) {
        for (MessagePart subPart : part.getParts()) {// Recursively check subparts
            String text = getTextFromPayload(subPart);
            if (text != null) return text;
        }
    } else if (part.getMimeType().equals("text/plain")) {// Check if the part is plain text
        byte[] decodedBytes = Base64.getUrlDecoder().decode(part.getBody().getData());
        return new String(decodedBytes, StandardCharsets.UTF_8);
    }
    return null;
}

    } 
    
    



