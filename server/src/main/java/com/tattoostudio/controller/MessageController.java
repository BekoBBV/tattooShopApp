package com.tattoostudio.controller;

import java.time.Clock;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tattoostudio.dto.CreateMessageRequest;
import com.tattoostudio.model.Message;
import com.tattoostudio.repository.MessageRepository;
import com.tattoostudio.service.SubmissionLimiter;
import com.tattoostudio.util.PhoneNumbers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/messages")
@Tag(name = "Contact", description = "Contact form (public)")
public class MessageController {

    private final MessageRepository messages;
    private final SubmissionLimiter limiter;
    private final Clock clock;

    public MessageController(MessageRepository messages, SubmissionLimiter limiter, Clock clock) {
        this.messages = messages;
        this.limiter = limiter;
        this.clock = clock;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send a message to the shop")
    public void create(@Valid @RequestBody CreateMessageRequest request, HttpServletRequest http) {
        limiter.checkMessage(http.getRemoteAddr());
        Message message = new Message();
        message.setName(request.name().trim());
        message.setContact(request.contact().trim());
        message.setText(request.text().trim());
        message.setCreatedAt(clock.instant());
        message.setSenderKey(senderKey(message.getContact()));
        message.setSenderMessageNumber((int) messages.countBySenderKey(message.getSenderKey()) + 1);
        messages.save(message);
    }

    /** "0888 123 456", "0888123456" and "+359888123456" are the same sender; emails ignore case. */
    static String senderKey(String contact) {
        if (!contact.contains("@")) {
            try {
                return PhoneNumbers.normalize(contact);
            } catch (IllegalArgumentException notAPhone) {
                // Neither a phone nor an email: compare the text as it is
            }
        }
        return contact.trim().toLowerCase(Locale.ROOT);
    }
}
