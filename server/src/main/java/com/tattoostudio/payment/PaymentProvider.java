package com.tattoostudio.payment;

import java.math.BigDecimal;

/**
 * Takes deposits online. Blade & Brush is a concept studio, so {@link DemoPaymentProvider} stands in
 * with a test payment page; a real provider (Stripe's test mode, for example) would implement this.
 *
 * Flow: the server gives the booking a random payment id and sends the customer to
 * {@link #checkoutUrl}. When the provider reports the payment complete, the server calls
 * {@code BookingService.depositPaid(paymentId)}, which confirms the booking.
 */
public interface PaymentProvider {

    /**
     * Where to send the customer to pay.
     *
     * @param paymentId   the booking's payment id
     * @param amount      the deposit in EUR
     * @param description shown on the payment page, e.g. "Deposit for Small dragon with Kai"
     */
    String checkoutUrl(String paymentId, BigDecimal amount, String description);

    /** Gives a paid deposit back. */
    void refund(String paymentId, BigDecimal amount);
}
