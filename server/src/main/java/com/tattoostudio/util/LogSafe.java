package com.tattoostudio.util;

import com.tattoostudio.model.Role;
import com.tattoostudio.model.User;

/**
 * How people are named in the server log, so the log holds no personal data (GDPR): a customer by their
 * account id (their username is their email address), staff by their login name, and an email that
 * has to appear anyway (a failed sign-in) masked to its first two letters and its domain.
 */
public final class LogSafe {

    private LogSafe() {
    }

    /** "customer 65f1…" for a customer account, the login name for staff. */
    public static String account(User user) {
        return user.getRole() == Role.USER ? "customer " + user.getId() : "'" + user.getUsername() + "'";
    }

    /** "iv***@example.com" for an email address; a staff login name stays as it is. */
    public static String mask(String usernameOrEmail) {
        if (usernameOrEmail == null) {
            return "(none)";
        }
        int at = usernameOrEmail.indexOf('@');
        if (at < 0) {
            return usernameOrEmail;
        }
        String local = usernameOrEmail.substring(0, at);
        return local.substring(0, Math.min(2, local.length())) + "***" + usernameOrEmail.substring(at);
    }
}
