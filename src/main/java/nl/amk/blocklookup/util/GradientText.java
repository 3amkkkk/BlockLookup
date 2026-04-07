package nl.amk.blocklookup.util;

import java.util.Locale;

public final class GradientText {

    private static final int[] DEFAULT_PALETTE = new int[]{
            0xFF004C,
            0xFF7A00,
            0xFFE600,
            0x00FF6A,
            0x00C8FF,
            0x7A00FF,
            0xFF00D4
    };

    private GradientText() {
    }

    public static String prefix() {
        return gradient("[BlockLookup]", DEFAULT_PALETTE) + " " + gray();
    }

    public static String gradient(String text) {
        return gradient(text, DEFAULT_PALETTE);
    }

    public static String gradient(String text, int[] palette) {
        if (text == null || text.isEmpty()) return "";
        if (palette == null || palette.length < 2) return text;

        int length = text.length();
        if (length == 1) {
            return color(palette[0]) + text;
        }

        StringBuilder out = new StringBuilder(length * 14);
        int segments = palette.length - 1;
        for (int i = 0; i < length; i++) {
            double t = (double) i / (double) (length - 1);
            double scaled = t * segments;
            int index = Math.min(segments - 1, (int) Math.floor(scaled));
            double local = scaled - index;
            int c0 = palette[index];
            int c1 = palette[index + 1];
            int rgb = lerpRgb(c0, c1, local);
            out.append(color(rgb)).append(text.charAt(i));
        }
        return out.toString();
    }

    public static String gray() {
        return "\u00A77";
    }

    public static String darkGray() {
        return "\u00A78";
    }

    public static String red() {
        return "\u00A7c";
    }

    public static String yellow() {
        return "\u00A7e";
    }

    public static String green() {
        return "\u00A7a";
    }

    public static String reset() {
        return "\u00A7r";
    }

    public static String color(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        String hex = String.format(Locale.ROOT, "%02x%02x%02x", r, g, b);
        StringBuilder sb = new StringBuilder(14);
        sb.append('\u00A7').append('x');
        for (int i = 0; i < 6; i++) {
            sb.append('\u00A7').append(hex.charAt(i));
        }
        return sb.toString();
    }

    private static int lerpRgb(int a, int b, double t) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;

        int rr = lerpChannel(ar, br, t);
        int rg = lerpChannel(ag, bg, t);
        int rb = lerpChannel(ab, bb, t);
        return (rr << 16) | (rg << 8) | rb;
    }

    private static int lerpChannel(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }
}

