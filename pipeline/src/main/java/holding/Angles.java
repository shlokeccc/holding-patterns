package holding;

/**
 * Math helpers for compass headings.
 */
public final class Angles {

    private Angles() {
        // utility class: never create an Angles object
    }

    /**
     * Signed turn from one heading to another, taking the short way around.
     * Positive = right turn, negative = left turn. Result is between -180 and 180.
     * Example: headingChange(350, 10) = 20, not -340.
     */
    public static double headingChange(double from, double to) {
        return ((to - from + 540) % 360) - 180;
    }
}