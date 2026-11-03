package com.tattoostudio.config;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The agreed booking rules, configurable under {@code app.booking.*} in application.properties.
 */
@ConfigurationProperties("app.booking")
public record BookingRules(
        /** The shop's timezone; opening hours are local times in this zone. */
        @DefaultValue("Europe/Sofia") ZoneId timezone,
        /** Length of every appointment; free times start every slot length from the artist's start time. */
        @DefaultValue("60m") Duration slotLength,
        /** Earliest bookable start, counted from now. */
        @DefaultValue("1h") Duration minNotice,
        /** How many days ahead customers can book. */
        @DefaultValue("30") int maxDaysAhead,
        /** How many upcoming bookings one phone number may hold at a time. */
        @DefaultValue("2") int maxUpcomingPerPhone,
        /** How many upcoming bookings one customer account may hold at a time (whatever phone numbers it uses). */
        @DefaultValue("2") int maxUpcomingPerAccount,
        /** Online cancellation closes this long before the appointment. */
        @DefaultValue("2h") Duration cancelCutoff,
        /** Customer details are wiped this long after the appointment ends. */
        @DefaultValue("24h") Duration anonymizeAfter,
        /** Failed code + phone lookups allowed per IP within {@link #lookupWindow}. */
        @DefaultValue("5") int maxFailedLookups,
        @DefaultValue("15m") Duration lookupWindow,
        /** Customers with an email get a reminder this long before the appointment. */
        @DefaultValue("24h") Duration reminderBefore) {
}
