package com.tattoostudio.payment;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A stand-in for Revolut: the "payment page" is the server's own test page
 * (DemoPaymentController), where a button confirms the payment. No money moves.
 *
 * Used while app.payments.provider is "demo" (the default). A real provider sets another value, so this
 * one and its page switch off and never compete with it.
 */
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "demo", matchIfMissing = true)
@Component
public class DemoPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(DemoPaymentProvider.class);

    @Override
    public String checkoutUrl(String paymentId, BigDecimal amount, String description) {
        return "/api/payments/demo/" + paymentId;
    }

    @Override
    public void refund(String paymentId, BigDecimal amount) {
        log.info("Demo payments: refunded €{} of payment {}", amount, paymentId);
    }
}
