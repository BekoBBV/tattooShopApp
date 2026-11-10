package com.tattoostudio.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** A message sent through the contact form. */
@Document("messages")
public class Message {

    @Id
    private String id;
    private String name;
    /** Email or phone number, whichever the sender gave. */
    private String contact;
    private String text;
    /** Up to 5 photos the sender attached. */
    private List<Image> photos = new ArrayList<>();
    /** Handled by the studio ("Mark as handled"). */
    private boolean read;
    private Instant createdAt;
    /** 1 for the first message from this email / phone, 2 for the second, … (helps spot repeat senders). */
    private int senderMessageNumber = 1;
    /** The contact in one form (phone as +359…, email in lower case), to count messages per sender. */
    private String senderKey;
    /** When the admin marked it read (null while unread). */
    private Instant readAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public List<Image> getPhotos() { return photos; }
    public void setPhotos(List<Image> photos) { this.photos = photos; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public int getSenderMessageNumber() { return senderMessageNumber; }
    public void setSenderMessageNumber(int senderMessageNumber) { this.senderMessageNumber = senderMessageNumber; }

    public String getSenderKey() { return senderKey; }
    public void setSenderKey(String senderKey) { this.senderKey = senderKey; }

    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
}
