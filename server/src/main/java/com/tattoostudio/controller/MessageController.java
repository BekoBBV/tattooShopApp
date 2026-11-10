package com.tattoostudio.controller;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tattoostudio.dto.CreateMessageRequest;
import com.tattoostudio.model.Image;
import com.tattoostudio.model.Message;
import com.tattoostudio.repository.MessageRepository;
import com.tattoostudio.service.SubmissionLimiter;
import com.tattoostudio.storage.ImageUploads;
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
    private final ImageUploads images;
    private final Clock clock;

    public MessageController(MessageRepository messages, SubmissionLimiter limiter, ImageUploads images, Clock clock) {
        this.messages = messages;
        this.limiter = limiter;
        this.images = images;
        this.clock = clock;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Write to the studio (without photos)")
    public void create(@Valid @RequestBody CreateMessageRequest request, HttpServletRequest http) {
        limiter.checkMessage(http.getRemoteAddr());
        save(request, List.of());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Write to the studio, with photos",
            description = "A multipart form: the part \"message\" is the JSON request (Content-Type application/json), "
                    + "plus up to 5 \"photos\" parts (JPG, PNG or HEIC, 10 MB each).")
    public void createWithPhotos(@Valid @RequestPart("message") CreateMessageRequest request,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos, HttpServletRequest http) {
        limiter.checkMessage(http.getRemoteAddr());
        List<Image> stored = images.storeAll(photos, "messages");
        try {
            save(request, stored);
        } catch (RuntimeException e) {
            images.deleteAll(stored);
            throw e;
        }
    }

    private void save(CreateMessageRequest request, List<Image> photos) {
        Message message = new Message();
        message.setName(request.name().trim());
        message.setContact(request.contact().trim());
        message.setText(request.text().trim());
        message.setPhotos(photos);
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
