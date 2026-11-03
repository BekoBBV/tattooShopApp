package com.tattoostudio.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One appointment.
 *
 * No double booking: {@code slots} lists the appointment slot(s) the booking takes.
 * The unique index below lets each (artist, slot) pair appear in only one BOOKED
 * booking, so MongoDB itself rejects an overlapping booking, even when two customers
 * press "Book" at the same moment. With fixed-length appointments this is one slot,
 * but the list keeps working if longer appointments are ever added.
 */
@Document("bookings")
@CompoundIndex(name = "no_double_booking", def = "{ 'artistId': 1, 'slots': 1 }",
        unique = true, partialFilter = "{ 'status': 'BOOKED' }")
@CompoundIndex(name = "artist_schedule", def = "{ 'artistId': 1, 'startsAt': 1 }")
public class Booking {

    @Id
    private String id;
    @Indexed(unique = true)
    private String code;

    private String serviceId;
    /** Copied at booking time so history survives renaming or deleting the service. */
    private String serviceName;
    private BigDecimal priceAtBooking;
    private String artistId;

    private Instant startsAt;
    @Indexed
    private Instant endsAt;
    private List<Instant> slots = new ArrayList<>();
    private BookingStatus status = BookingStatus.BOOKED;

    // Customer details: wiped 24 h after the appointment ends (see anonymized)
    private String customerName;
    /** Normalised phone number, e.g. +359888123456. */
    @Indexed(sparse = true)
    private String phone;
    private String email;
    /** "Anything your artist should know?" (optional, max 300 characters). */
    private String notes;
    private boolean anonymized;
    /** The customer account that made this booking; null for guest bookings. */
    @Indexed(sparse = true)
    private String userId;

    private Instant createdAt;
    private Instant cancelledAt;
    /** When the reminder email went out (null: not sent). */
    private Instant reminderSentAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public BigDecimal getPriceAtBooking() { return priceAtBooking; }
    public void setPriceAtBooking(BigDecimal priceAtBooking) { this.priceAtBooking = priceAtBooking; }

    public String getArtistId() { return artistId; }
    public void setArtistId(String artistId) { this.artistId = artistId; }

    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }

    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }

    public List<Instant> getSlots() { return slots; }
    public void setSlots(List<Instant> slots) { this.slots = slots; }

    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isAnonymized() { return anonymized; }
    public void setAnonymized(boolean anonymized) { this.anonymized = anonymized; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }

    public Instant getReminderSentAt() { return reminderSentAt; }
    public void setReminderSentAt(Instant reminderSentAt) { this.reminderSentAt = reminderSentAt; }
}
