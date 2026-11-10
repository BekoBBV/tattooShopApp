package com.tattoostudio.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One appointment: a flash design, a consultation or a session the artist reserved.
 *
 * No double booking: {@code slots} lists every 30-minute step the booking takes, from its start
 * until the end of the artist's break after it. The unique index below lets each (artist, slot)
 * pair appear in only one booking that still holds its time ({@code holdsTime}), so MongoDB itself
 * rejects an overlapping booking, even when two customers press "Book" at the same moment.
 * The same goes for flash designs: each one can be claimed by one booking only ({@code claimsFlash}).
 */
@Document("bookings")
@CompoundIndex(name = "no_double_booking", def = "{ 'artistId': 1, 'slots': 1 }",
        unique = true, partialFilter = "{ 'holdsTime': true }")
@CompoundIndex(name = "one_booking_per_flash", def = "{ 'flashId': 1 }",
        unique = true, partialFilter = "{ 'claimsFlash': true, 'flashId': { $exists: true } }")
@CompoundIndex(name = "artist_schedule", def = "{ 'artistId': 1, 'startsAt': 1 }")
@CompoundIndex(name = "deposit_holds", def = "{ 'status': 1, 'depositDueAt': 1 }")
public class Booking {

    @Id
    private String id;
    @Indexed(unique = true)
    private String code;
    private BookingType type;
    private BookingStatus status;
    private String artistId;
    private Instant startsAt;
    @Indexed
    private Instant endsAt;
    private List<Instant> slots = new ArrayList<>();
    /** status.holdsTime(), stored so the index can use it. */
    private boolean holdsTime;
    /** status.claimsFlash() for flash bookings (always false otherwise), stored for the index. */
    private boolean claimsFlash;

    /** What is being tattooed: the flash design's name or the session's description; null for consultations. */
    private String title;
    // Flash bookings: the design, with its size copied so history survives changes to it
    private String flashId;
    private Integer widthCm;
    private Integer heightCm;
    /** Full price (flash price or the session price the artist set); null for consultations. */
    private BigDecimal price;

    // Deposit (none for consultations)
    private BigDecimal depositAmount;
    private DepositStatus depositStatus = DepositStatus.NONE;
    private DepositMethod depositMethod;
    /** Pay by then: the end of the 15-minute hold (flash) or the deadline the studio gives (sessions). */
    private Instant depositDueAt;
    private Instant depositPaidAt;
    private Instant depositRefundedAt;
    /** The payment page this deposit is paid on (random, also used to find the booking again). */
    @Indexed(unique = true, sparse = true)
    private String paymentId;

    // Customer details: wiped some months after the appointment (see anonymized)
    private String customerName;
    /** Normalised phone number, e.g. +359888123456. */
    @Indexed(sparse = true)
    private String phone;
    private String email;
    private LocalDate dateOfBirth;
    /** "Anything your artist should know?" (flash), or the studio's own note. */
    private String notes;
    /** When the customer confirmed being 18+, the consent form and (with a deposit) the deposit policy. */
    private Instant consentAt;

    // Consultations: the idea for the custom piece
    private String idea;
    private Placement placement;
    private Integer sizeCm;
    private ColourChoice colour;
    private List<Image> photos = new ArrayList<>();

    /** Sessions: the consultation (or earlier session) this one follows, where the customer came from. */
    private String followsBookingId;

    private boolean anonymized;
    /** The customer account that made this booking (or that the artist reserved it for); null for guests. */
    @Indexed(sparse = true)
    private String userId;
    private Instant createdAt;
    private Instant cancelledAt;
    /** When the reminder email went out (null: not sent). */
    private Instant reminderSentAt;
    /** When the customer reviewed it (null: not yet). */
    private Instant reviewedAt;

    /** Sets the status together with the two index flags that follow from it. */
    public void changeStatus(BookingStatus status) {
        this.status = status;
        this.holdsTime = status.holdsTime();
        this.claimsFlash = type == BookingType.FLASH && status.claimsFlash();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public BookingType getType() { return type; }
    public void setType(BookingType type) { this.type = type; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public String getArtistId() { return artistId; }
    public void setArtistId(String artistId) { this.artistId = artistId; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public List<Instant> getSlots() { return slots; }
    public void setSlots(List<Instant> slots) { this.slots = slots; }
    public boolean isHoldsTime() { return holdsTime; }
    public void setHoldsTime(boolean holdsTime) { this.holdsTime = holdsTime; }
    public boolean isClaimsFlash() { return claimsFlash; }
    public void setClaimsFlash(boolean claimsFlash) { this.claimsFlash = claimsFlash; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getFlashId() { return flashId; }
    public void setFlashId(String flashId) { this.flashId = flashId; }
    public Integer getWidthCm() { return widthCm; }
    public void setWidthCm(Integer widthCm) { this.widthCm = widthCm; }
    public Integer getHeightCm() { return heightCm; }
    public void setHeightCm(Integer heightCm) { this.heightCm = heightCm; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }
    public DepositStatus getDepositStatus() { return depositStatus; }
    public void setDepositStatus(DepositStatus depositStatus) { this.depositStatus = depositStatus; }
    public DepositMethod getDepositMethod() { return depositMethod; }
    public void setDepositMethod(DepositMethod depositMethod) { this.depositMethod = depositMethod; }
    public Instant getDepositDueAt() { return depositDueAt; }
    public void setDepositDueAt(Instant depositDueAt) { this.depositDueAt = depositDueAt; }
    public Instant getDepositPaidAt() { return depositPaidAt; }
    public void setDepositPaidAt(Instant depositPaidAt) { this.depositPaidAt = depositPaidAt; }
    public Instant getDepositRefundedAt() { return depositRefundedAt; }
    public void setDepositRefundedAt(Instant depositRefundedAt) { this.depositRefundedAt = depositRefundedAt; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getConsentAt() { return consentAt; }
    public void setConsentAt(Instant consentAt) { this.consentAt = consentAt; }
    public String getIdea() { return idea; }
    public void setIdea(String idea) { this.idea = idea; }
    public Placement getPlacement() { return placement; }
    public void setPlacement(Placement placement) { this.placement = placement; }
    public Integer getSizeCm() { return sizeCm; }
    public void setSizeCm(Integer sizeCm) { this.sizeCm = sizeCm; }
    public ColourChoice getColour() { return colour; }
    public void setColour(ColourChoice colour) { this.colour = colour; }
    public List<Image> getPhotos() { return photos; }
    public void setPhotos(List<Image> photos) { this.photos = photos; }
    public String getFollowsBookingId() { return followsBookingId; }
    public void setFollowsBookingId(String followsBookingId) { this.followsBookingId = followsBookingId; }
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
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
