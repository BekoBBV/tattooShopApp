package com.tattoostudio.dto;

import java.time.Instant;
import java.util.List;

import com.tattoostudio.model.Image;
import com.tattoostudio.model.Message;

/** A contact-form message; {@code read} is "handled" on the admin page. */
public record MessageDto(String id, String name, String contact, String text, List<Image> photos, boolean read,
        Instant createdAt, int senderMessageNumber, Instant readAt) {

    public static MessageDto from(Message m) {
        return new MessageDto(m.getId(), m.getName(), m.getContact(), m.getText(),
                m.getPhotos() == null ? List.of() : m.getPhotos(), m.isRead(), m.getCreatedAt(),
                m.getSenderMessageNumber(), m.getReadAt());
    }
}
