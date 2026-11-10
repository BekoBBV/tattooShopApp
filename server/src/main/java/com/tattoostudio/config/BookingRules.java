package com.tattoostudio.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The agreed booking rules, configurable under {@code app.booking.*} in application.properties.
 */
@ConfigurationProperties("app.booking")
public record BookingRules(
        /** The studio's timezone; opening hours are local times in this zone. */
        @DefaultValue("Europe/Sofia") ZoneId timezone,
        /** The time grid: bookings start (and end) on multiples of this, counted from midnight. */
        @DefaultValue("30m") Duration slotLength,
        /** How long a free consultation takes. */
        @DefaultValue("30m") Duration consultationLength,
        /** Earliest bookable start, counted from now. */
        @DefaultValue("2h") Duration minNotice,
        /** How many days ahead customers can book flash and consultations. */
        @DefaultValue("30") int maxDaysAhead,
        /** How far ahead artists can reserve sessions. */
        @DefaultValue("365") int maxSessionDaysAhead,
        /** How many upcoming bookings (flash and consultations) one phone number may hold at a time. */
        @DefaultValue("2") int maxUpcomingPerPhone,
        /** The same for one customer account (whatever phone numbers it uses). */
        @DefaultValue("2") int maxUpcomingPerAccount,
        /** Customers must be at least this old on the day of the appointment. */
        @DefaultValue("18") int minAge,
        /** The deposit for every flash design (EUR); it is taken off the price. */
        @DefaultValue("30") BigDecimal flashDeposit,
        /** How long a new flash booking holds its time and design while the deposit is paid. */
        @DefaultValue("15m") Duration depositHold,
        /** How long the customer has to pay a session deposit after the artist reserves the time. */
        @DefaultValue("72h") Duration sessionDepositDue,
        /** Cancelling at least this long before the appointment refunds the deposit; later it is kept. */
        @DefaultValue("48h") Duration refundCutoff,
        /** Customer details (and reference photos) are wiped this long after the appointment ends. */
        @DefaultValue("180d") Duration anonymizeAfter,
        /** Failed code + phone lookups allowed per IP within {@link #lookupWindow}. */
        @DefaultValue("5") int maxFailedLookups,
        @DefaultValue("15m") Duration lookupWindow,
        /** Customers with an email get a reminder this long before the appointment. */
        @DefaultValue("24h") Duration reminderBefore) {
}
