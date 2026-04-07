package nl.amk.blocklookup.util;

import java.time.Duration;

public final class DurationParser {

    private DurationParser() {
    }

    public static Duration parse(String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase();
        if (s.isEmpty()) return null;

        long multiplier;
        char last = s.charAt(s.length() - 1);
        if (Character.isDigit(last)) {
            multiplier = 60L;
        } else if (last == 's') {
            multiplier = 1L;
            s = s.substring(0, s.length() - 1);
        } else if (last == 'm') {
            multiplier = 60L;
            s = s.substring(0, s.length() - 1);
        } else if (last == 'h') {
            multiplier = 3600L;
            s = s.substring(0, s.length() - 1);
        } else if (last == 'd') {
            multiplier = 86400L;
            s = s.substring(0, s.length() - 1);
        } else {
            return null;
        }

        long value;
        try {
            value = Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (value < 0) return null;
        return Duration.ofSeconds(value * multiplier);
    }
}

