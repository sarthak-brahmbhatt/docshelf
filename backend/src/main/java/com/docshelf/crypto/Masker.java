// Display masks for sensitive identifiers (design v0.1 section 7.2): the only form in which they appear in API responses, chat and logs
package com.docshelf.crypto;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class Masker {

    public static final char DOT = '•';

    /** Aadhaar: {@code XXXX XXXX 1234}. */
    public String aadhaar(String number) {
        String d = digits(number);
        if (d.length() < 4) {
            return generic(number, 0);
        }
        return "XXXX XXXX " + d.substring(d.length() - 4);
    }

    /** PAN: {@code XXXXX1234X} (keeps the 4 digits, masks letters). */
    public String pan(String pan) {
        String p = compact(pan).toUpperCase(Locale.ROOT);
        if (p.length() != 10) {
            return generic(pan, 4);
        }
        return "XXXXX" + p.substring(5, 9) + "X";
    }

    /** Passport: first letter + dots + last 2 ({@code X•••••67}). */
    public String passport(String number) {
        String p = compact(number).toUpperCase(Locale.ROOT);
        if (p.length() < 3) {
            return generic(number, 0);
        }
        return p.charAt(0) + repeat(DOT, p.length() - 3) + p.substring(p.length() - 2);
    }

    /** Driving licence: state code (first 4) + space + dots + last 4 ({@code MH12 •••••••2345}). */
    public String drivingLicence(String number) {
        String p = compact(number).toUpperCase(Locale.ROOT);
        if (p.length() < 8) {
            return generic(number, 2);
        }
        return p.substring(0, 4) + " " + repeat(DOT, p.length() - 8) + p.substring(p.length() - 4);
    }

    /** Phone: {@code +91 98•••• •210} for Indian mobiles; otherwise keeps country code and last 3. */
    public String phone(String e164) {
        String p = compact(e164);
        String d = digits(p);
        if (d.length() == 12 && d.startsWith("91")) {
            String local = d.substring(2);
            return "+91 " + local.substring(0, 2) + repeat(DOT, 4) + " " + DOT + local.substring(7);
        }
        if (d.length() == 10) {
            return "+91 " + d.substring(0, 2) + repeat(DOT, 4) + " " + DOT + d.substring(7);
        }
        if (d.length() < 4) {
            return generic(e164, 0);
        }
        return (p.startsWith("+") ? "+" : "") + d.substring(0, Math.min(2, d.length() - 3))
                + repeat(DOT, Math.max(1, d.length() - 5)) + d.substring(d.length() - 3);
    }

    /** Email: first character of the local part + dots + @domain. */
    public String email(String email) {
        if (email == null || !email.contains("@")) {
            return generic(email, 0);
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String masked = local.isEmpty() ? "" : local.charAt(0) + repeat(DOT, Math.max(2, local.length() - 1));
        return masked + email.substring(at);
    }

    /** Aadhaar VID (16 digits): keeps the last 4. */
    public String vid(String vid) {
        String d = digits(vid);
        if (d.length() < 4) {
            return generic(vid, 0);
        }
        return repeat('X', 4) + " " + repeat('X', 4) + " " + repeat('X', 4) + " " + d.substring(d.length() - 4);
    }

    /** Generic mask keeping only the last {@code keepLast} characters. */
    public String generic(String value, int keepLast) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        int keep = Math.min(keepLast, Math.max(0, v.length() - 1));
        return repeat(DOT, v.length() - keep) + v.substring(v.length() - keep);
    }

    /** Dispatches on the identity document type name (AADHAAR, PASSPORT, DRIVING_LICENCE, PAN_CARD, VOTER_ID). */
    public String forIdType(String idType, String number) {
        if (number == null) {
            return null;
        }
        return switch (idType == null ? "" : idType.toUpperCase(Locale.ROOT)) {
            case "AADHAAR" -> aadhaar(number);
            case "PASSPORT" -> passport(number);
            case "DRIVING_LICENCE", "DRIVING_LICENSE" -> drivingLicence(number);
            case "PAN_CARD", "PAN" -> pan(number);
            default -> generic(number, 4);
        };
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    private static String compact(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\-]", "");
    }

    private static String repeat(char c, int n) {
        return String.valueOf(c).repeat(Math.max(0, n));
    }
}
