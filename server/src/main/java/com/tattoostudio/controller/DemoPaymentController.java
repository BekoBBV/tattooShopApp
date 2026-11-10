package com.tattoostudio.controller;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

import com.tattoostudio.model.Booking;
import com.tattoostudio.model.DepositStatus;
import com.tattoostudio.repository.BookingRepository;
import com.tattoostudio.service.BookingMailer;
import com.tattoostudio.service.BookingService;

import io.swagger.v3.oas.annotations.Hidden;

/**
 * The test payment page that stands in for Revolut (see DemoPaymentProvider). It shows the deposit
 * and two buttons; "Pay" confirms the booking and sends the customer back to the site. No money moves.
 */
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "demo", matchIfMissing = true)
@RestController
@RequestMapping("/api/payments/demo")
@Hidden
public class DemoPaymentController {

    private final BookingRepository bookings;
    private final BookingService bookingService;

    public DemoPaymentController(BookingRepository bookings, BookingService bookingService) {
        this.bookings = bookings;
        this.bookingService = bookingService;
    }

    @GetMapping(value = "/{paymentId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@PathVariable String paymentId) {
        Booking booking = bookings.findByPaymentId(paymentId).orElse(null);
        if (booking == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML)
                    .body(html("Payment not found", "<p>This payment link doesn't exist.</p>"));
        }
        if (booking.getDepositStatus() != DepositStatus.WAITING) {
            return back(booking.getCode(), booking.getDepositStatus() == DepositStatus.PAID);
        }
        String what = HtmlUtils.htmlEscape(booking.getTitle() == null ? "Consultation" : booking.getTitle());
        String body = """
                <p>Test payment: no money is taken.</p>
                <p class="amount">€%s</p>
                <p>Deposit for %s, booking %s</p>
                <form method="post" action="/api/payments/demo/%s">
                  <button name="result" value="paid">Pay €%s</button>
                  <button name="result" value="cancel" class="secondary">Cancel</button>
                </form>
                """.formatted(amount(booking), what, booking.getCode(), paymentId, amount(booking));
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html("Pay the deposit", body));
    }

    @PostMapping("/{paymentId}")
    public ResponseEntity<String> pay(@PathVariable String paymentId, @RequestParam(defaultValue = "cancel") String result) {
        Booking booking = bookings.findByPaymentId(paymentId).orElse(null);
        if (booking == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!"paid".equals(result)) {
            return redirect("/book/deposit?code=" + encode(booking.getCode()));
        }
        BookingService.DepositOutcome outcome = bookingService.depositPaid(paymentId);
        return back(outcome.code(), outcome.confirmed());
    }

    private static ResponseEntity<String> back(String code, boolean confirmed) {
        return redirect((confirmed ? "/booking/confirmed?code=" : "/manage?refunded=1&code=") + encode(code));
    }

    private static ResponseEntity<String> redirect(String location) {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(location));
        return new ResponseEntity<>(headers, HttpStatus.SEE_OTHER);
    }

    private static String encode(String value) {
        return UriUtils.encodeQueryParam(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String amount(Booking booking) {
        return BookingMailer.euros(booking.getDepositAmount());
    }

    private static String html(String title, String body) {
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <style>
                  body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #080707; color: #F5E4D2;
                         font: 17px/1.65 system-ui, sans-serif; }
                  main { width: min(420px, calc(100%% - 48px)); padding: 32px; background: #FBF2E8; color: #201E1A;
                         border: 3px double #201E1A; }
                  h1 { margin: 0 0 8px; font: 600 28px/1.2 Georgia, serif; }
                  .amount { font-size: 36px; font-weight: 700; margin: 8px 0; font-variant-numeric: tabular-nums; }
                  form { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 24px; }
                  button { min-height: 48px; padding: 0 22px; border: 1.5px solid #B92423; background: #B92423; color: #F5E4D2;
                           font: 500 17px system-ui, sans-serif; cursor: pointer; }
                  button.secondary { background: transparent; color: #201E1A; border-color: #201E1A; }
                  button:focus-visible { outline: 2px solid #201E1A; outline-offset: 3px; }
                </style></head>
                <body><main><h1>%s</h1>%s</main></body></html>
                """.formatted(title, title, body);
    }

}
