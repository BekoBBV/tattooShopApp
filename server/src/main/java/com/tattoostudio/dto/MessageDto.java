package com.tattoostudio.dto;

import java.time.Instant;

import com.tattoostudio.model.Message;

public record MessageDto(String id, String name, String contact, String text, boolean read, Instant createdAt,
        int senderMessageNumber, Instant readAt) {

    public static MessageDto from(Message m) {
        return new MessageDto(m.getId(), m.getName(), m.getContact(), m.getText(), m.isRead(), m.getCreatedAt(),
                m.getSenderMessageNumber(), m.getReadAt());
    }
}
