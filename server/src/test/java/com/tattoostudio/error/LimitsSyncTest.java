package com.tattoostudio.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.tattoostudio.dto.AdminUserRequest;
import com.tattoostudio.dto.ArtistLoginRequest;
import com.tattoostudio.dto.ArtistRequest;
import com.tattoostudio.dto.BookingLookupRequest;
import com.tattoostudio.dto.BookingUpdateRequest;
import com.tattoostudio.dto.CreateBookingRequest;
import com.tattoostudio.dto.CreateMessageRequest;
import com.tattoostudio.dto.CreateReviewRequest;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.LoginRequest;
import com.tattoostudio.dto.RegisterRequest;
import com.tattoostudio.dto.ServiceRequest;
import com.tattoostudio.service.ScheduleService;

import jakarta.validation.constraints.Size;

/**
 * "The website's fields have the same limits as the server": every number in the site's
 * client/src/lib/limits.ts must equal the @Size limit of the matching request field.
 */
class LimitsSyncTest {

    /** limits.ts key → the server's request record and field (".min" keys compare the minimum). */
    private static final Map<String, Object[]> SERVER_FIELDS = new LinkedHashMap<>();
    static {
        field("booking.customerName", CreateBookingRequest.class, "customerName");
        field("booking.phone", CreateBookingRequest.class, "phone");
        field("booking.email", CreateBookingRequest.class, "email");
        field("booking.notes", CreateBookingRequest.class, "notes");
        field("lookup.code", BookingLookupRequest.class, "code");
        field("lookup.phone", BookingLookupRequest.class, "phone");
        field("account.name", RegisterRequest.class, "name");
        field("account.phone", RegisterRequest.class, "phone");
        field("account.email", RegisterRequest.class, "email");
        field("account.passwordMin", RegisterRequest.class, "password");
        field("account.password", RegisterRequest.class, "password");
        field("login.username", LoginRequest.class, "username");
        field("login.password", LoginRequest.class, "password");
        field("review.name", CreateReviewRequest.class, "name");
        field("review.text", CreateReviewRequest.class, "text");
        field("message.name", CreateMessageRequest.class, "name");
        field("message.contact", CreateMessageRequest.class, "contact");
        field("message.text", CreateMessageRequest.class, "text");
        field("service.name", ServiceRequest.class, "name");
        field("service.description", ServiceRequest.class, "description");
        field("service.label", ServiceRequest.class, "label");
        field("artist.name", ArtistRequest.class, "name");
        field("artist.handle", ArtistRequest.class, "handle");
        field("artist.specialty", ArtistRequest.class, "specialty");
        field("artist.bio", ArtistRequest.class, "bio");
        field("artist.photoUrl", ArtistRequest.class, "photoUrl");
        field("artistLogin.username", ArtistLoginRequest.class, "username");
        field("user.username", AdminUserRequest.class, "username");
        field("user.name", AdminUserRequest.class, "name");
        field("user.email", AdminUserRequest.class, "email");
        field("user.phone", AdminUserRequest.class, "phone");
        field("user.passwordMin", AdminUserRequest.class, "password");
        field("user.password", AdminUserRequest.class, "password");
        field("search.q", ListQuery.class, "q");
        // The booking edit form shares the booking limits
        SERVER_FIELDS.put("booking.customerName/edit", new Object[] { BookingUpdateRequest.class, "customerName" });
        SERVER_FIELDS.put("booking.notes/edit", new Object[] { BookingUpdateRequest.class, "notes" });
    }

    private static void field(String key, Class<?> type, String name) {
        SERVER_FIELDS.put(key, new Object[] { type, name });
    }

    @Test
    void websiteFieldLimitsMatchTheServer() throws Exception {
        Path file = Path.of("..", "client", "src", "lib", "limits.ts");
        Map<String, Integer> site = new HashMap<>();
        Matcher m = Pattern.compile("\"([\\w.]+)\":\\s*(\\d+)").matcher(Files.readString(file));
        while (m.find()) {
            site.put(m.group(1), Integer.parseInt(m.group(2)));
        }
        assertThat(site.keySet()).as("every key in limits.ts is checked here")
                .containsExactlyInAnyOrderElementsOf(Stream.concat(
                        SERVER_FIELDS.keySet().stream().filter(k -> !k.contains("/")), Stream.of("exception.note")).toList());

        Map<String, Integer> server = new HashMap<>();
        for (var entry : SERVER_FIELDS.entrySet()) {
            String key = entry.getKey().replace("/edit", "");
            server.put(entry.getKey(), limit((Class<?>) entry.getValue()[0], (String) entry.getValue()[1], key.endsWith("Min")));
            if (entry.getKey().contains("/")) {
                assertThat(server.get(entry.getKey())).as(entry.getKey()).isEqualTo(site.get(key));
            }
        }
        server.put("exception.note", noteLimit());
        for (String key : site.keySet()) {
            assertThat(site.get(key)).as("limits.ts %s", key).isEqualTo(server.get(key));
        }
    }

    /** The @Size max (or min) of a record field; staff usernames use a {3,30} pattern instead. */
    private static int limit(Class<?> type, String name, boolean min) throws Exception {
        for (RecordComponent c : type.getRecordComponents()) {
            if (!c.getName().equals(name)) {
                continue;
            }
            Size size = c.getAccessor().getAnnotation(Size.class);
            if (size == null) {
                size = type.getDeclaredField(name).getAnnotation(Size.class);
            }
            if (size == null) {
                size = type.getDeclaredConstructors()[0].getParameters()[indexOf(type, name)].getAnnotation(Size.class);
            }
            if (size != null) {
                return min ? size.min() : size.max();
            }
            jakarta.validation.constraints.Pattern pattern = type.getDeclaredConstructors()[0]
                    .getParameters()[indexOf(type, name)].getAnnotation(jakarta.validation.constraints.Pattern.class);
            Matcher range = Pattern.compile("\\{(\\d+),(\\d+)}").matcher(pattern.regexp());
            if (range.find()) {
                return Integer.parseInt(min ? range.group(1) : range.group(2));
            }
        }
        throw new IllegalStateException("No limit on " + type.getSimpleName() + "." + name);
    }

    /** Day-off notes are checked by ScheduleService (they are part of the schedule, not a request record). */
    private static int noteLimit() throws Exception {
        Field field = ScheduleService.class.getDeclaredField("MAX_NOTE_LENGTH");
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static int indexOf(Class<?> type, String name) {
        RecordComponent[] all = type.getRecordComponents();
        for (int i = 0; i < all.length; i++) {
            if (all[i].getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
