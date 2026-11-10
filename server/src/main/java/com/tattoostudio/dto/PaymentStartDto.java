package com.tattoostudio.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Where to send the customer to pay a deposit, and until when the time is held. */
public record PaymentStartDto(String checkoutUrl, BigDecimal amount, Instant dueAt) {
}
