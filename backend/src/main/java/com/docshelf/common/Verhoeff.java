// Verhoeff checksum (used for Aadhaar number validation by the redactor, classifier and ID extractor)
package com.docshelf.common;

public final class Verhoeff {

    private static final int[][] D = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
            {1, 2, 3, 4, 0, 6, 7, 8, 9, 5},
            {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
            {3, 4, 0, 1, 2, 8, 9, 5, 6, 7},
            {4, 0, 1, 2, 3, 9, 5, 6, 7, 8},
            {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
            {6, 5, 9, 8, 7, 1, 0, 4, 3, 2},
            {7, 6, 5, 9, 8, 2, 1, 0, 4, 3},
            {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
            {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}};
    private static final int[][] P = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
            {1, 5, 7, 6, 2, 8, 3, 0, 9, 4},
            {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
            {8, 9, 1, 6, 0, 4, 3, 5, 2, 7},
            {9, 4, 5, 3, 1, 2, 6, 8, 7, 0},
            {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
            {2, 7, 9, 3, 8, 0, 6, 4, 1, 5},
            {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}};
    private static final int[] INV = {0, 4, 3, 2, 1, 5, 6, 7, 8, 9};

    private Verhoeff() {
    }

    /** True when the digit string (no separators) carries a valid trailing Verhoeff check digit. */
    public static boolean isValid(String digits) {
        if (digits == null || digits.isEmpty()) {
            return false;
        }
        int c = 0;
        int len = digits.length();
        for (int i = 0; i < len; i++) {
            char ch = digits.charAt(len - 1 - i);
            if (ch < '0' || ch > '9') {
                return false;
            }
            c = D[c][P[i % 8][ch - '0']];
        }
        return c == 0;
    }

    /** Computes the check digit to append to the given digit string. */
    public static int checkDigit(String digits) {
        int c = 0;
        int len = digits.length();
        for (int i = 0; i < len; i++) {
            char ch = digits.charAt(len - 1 - i);
            c = D[c][P[(i + 1) % 8][ch - '0']];
        }
        return INV[c];
    }

    /** True for a 12-digit, Verhoeff-valid Aadhaar number (first digit 2-9 per UIDAI). */
    public static boolean isAadhaar(String digits) {
        return digits != null && digits.length() == 12 && digits.charAt(0) >= '2' && isValid(digits);
    }
}
