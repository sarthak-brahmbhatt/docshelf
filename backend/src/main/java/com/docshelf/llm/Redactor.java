// Deterministic PII redaction applied before every outbound OpenAI call (design v0.1 section 7.4); unit-tested
package com.docshelf.llm;

import com.docshelf.common.Verhoeff;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class Redactor {

    public static final String OPEN = "⟨";   // ⟨
    public static final String CLOSE = "⟩";  // ⟩

    // Whole MRZ lines: 44 chars of [A-Z0-9<] (TD3) or 30/36 (TD1/TD2) containing at least one '<'
    private static final Pattern MRZ_LINE = Pattern.compile("(?m)^[A-Z0-9<]{30}(?:[A-Z0-9<]{6}|[A-Z0-9<]{14})?$");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    // 12 digits in 4-4-4 or contiguous, optional separators
    private static final Pattern AADHAAR = Pattern.compile("(?<![0-9])(\\d{4}[ \\-]?\\d{4}[ \\-]?\\d{4})(?![0-9])");
    private static final Pattern VID = Pattern.compile("(?<![0-9])(\\d{4}[ \\-]?\\d{4}[ \\-]?\\d{4}[ \\-]?\\d{4})(?![0-9])");
    private static final Pattern PAN = Pattern.compile("(?<![A-Z0-9])([A-Z]{5}[0-9]{4}[A-Z])(?![A-Z0-9])");
    private static final Pattern PASSPORT_KEYWORD = Pattern.compile("(?i)passport");
    private static final Pattern PASSPORT_NUMBER = Pattern.compile("(?<![A-Z0-9])([A-Z][0-9]{7})(?![A-Z0-9])");
    private static final Pattern DL = Pattern.compile("(?<![A-Z0-9])([A-Z]{2}[0-9]{2}[ \\-]?[0-9]{4}[0-9]{7})(?![0-9])");
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])(?:\\+91[ \\-]?|0)?([6-9][0-9]{4}[ \\-]?[0-9]{5})(?![0-9])");
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<![0-9A-Za-z/])([0-9]{9,18})(?![0-9A-Za-z/])");
    private static final Pattern FOLIO_CONTEXT = Pattern.compile("(?i)(folio|policy|account\\s*statement|isin|scheme|ifsc|order|invoice|receipt|ref|txn|transaction|utr|cheque|gstin|bill)");
    private static final Pattern FOLIO_BEFORE_AADHAAR = Pattern.compile("(?i)folio");
    private static final int CONTEXT_WINDOW = 40;

    public Redaction redact(String input) {
        if (input == null || input.isEmpty()) {
            return new Redaction(input == null ? "" : input, Map.of(), Map.of());
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> valueToPlaceholder = new LinkedHashMap<>();
        String text = input;

        text = replaceAll(text, MRZ_LINE, "MRZ", 0, placeholders, counts, valueToPlaceholder, (m, s) -> true, false);
        text = replaceAll(text, EMAIL, "EMAIL", 0, placeholders, counts, valueToPlaceholder, (m, s) -> true, true);
        text = replaceAll(text, VID, "VID", 1, placeholders, counts, valueToPlaceholder,
                (m, s) -> digits(m.group(1)).length() == 16 && !precededByFolio(s, m.start(1)), true);
        text = replaceAll(text, AADHAAR, "AADHAAR", 1, placeholders, counts, valueToPlaceholder,
                (m, s) -> Verhoeff.isAadhaar(digits(m.group(1))) && !precededByFolio(s, m.start(1)), true);
        text = replaceAll(text, PAN, "PAN", 1, placeholders, counts, valueToPlaceholder, (m, s) -> true, true);
        text = replaceAll(text, PASSPORT_NUMBER, "PASSPORT", 1, placeholders, counts, valueToPlaceholder,
                (m, s) -> nearKeyword(s, m.start(1), PASSPORT_KEYWORD), true);
        text = replaceAll(text, DL, "DL", 1, placeholders, counts, valueToPlaceholder, (m, s) -> true, true);
        text = replaceAll(text, PHONE, "PHONE", 0, placeholders, counts, valueToPlaceholder,
                (m, s) -> digits(m.group()).replaceFirst("^(91|0)(?=[6-9]\\d{9}$)", "").length() == 10, true);
        text = replaceAll(text, LONG_NUMBER, "NUM", 1, placeholders, counts, valueToPlaceholder,
                (m, s) -> !inFolioContext(s, m.start(1)), true);
        return new Redaction(text, placeholders, counts);
    }

    private interface Guard {
        boolean accept(Matcher m, String s);
    }

    private static String replaceAll(String text, Pattern pattern, String type, int group,
                                     Map<String, String> placeholders, Map<String, Integer> counts,
                                     Map<String, String> valueToPlaceholder, Guard guard, boolean numbered) {
        Matcher m = pattern.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        int last = 0;
        boolean any = false;
        while (m.find()) {
            if (!guard.accept(m, text)) {
                continue;
            }
            int start = m.start(group);
            int end = m.end(group);
            String value = text.substring(start, end);
            String key = type + ":" + (numbered ? normalise(value) : value);
            String placeholder = valueToPlaceholder.get(key);
            if (placeholder == null) {
                long distinct = valueToPlaceholder.keySet().stream().filter(k -> k.startsWith(type + ":")).count() + 1;
                placeholder = numbered ? OPEN + type + "#" + distinct + CLOSE : OPEN + type + CLOSE;
                valueToPlaceholder.put(key, placeholder);
                placeholders.putIfAbsent(placeholder, value);
            }
            counts.merge(type, 1, Integer::sum);
            sb.append(text, last, start).append(placeholder);
            last = end;
            any = true;
        }
        if (!any) {
            return text;
        }
        sb.append(text, last, text.length());
        return sb.toString();
    }

    private static String normalise(String value) {
        return value.replaceAll("[\\s\\-]", "").toUpperCase(Locale.ROOT);
    }

    private static String digits(String s) {
        return s.replaceAll("\\D", "");
    }

    private static boolean precededByFolio(String text, int start) {
        int from = Math.max(0, start - CONTEXT_WINDOW);
        String before = text.substring(from, start);
        int nl = before.lastIndexOf('\n');
        if (nl >= 0) {
            before = before.substring(nl + 1);
        }
        return FOLIO_BEFORE_AADHAAR.matcher(before).find();
    }

    private static boolean inFolioContext(String text, int start) {
        int from = Math.max(0, start - CONTEXT_WINDOW);
        String before = text.substring(from, start);
        int nl = before.lastIndexOf('\n');
        if (nl >= 0) {
            before = before.substring(nl + 1);
        }
        return FOLIO_CONTEXT.matcher(before).find();
    }

    private static boolean nearKeyword(String text, int start, Pattern keyword) {
        int from = Math.max(0, start - 2 * CONTEXT_WINDOW);
        int to = Math.min(text.length(), start + CONTEXT_WINDOW);
        return keyword.matcher(text.substring(from, to)).find();
    }
}
