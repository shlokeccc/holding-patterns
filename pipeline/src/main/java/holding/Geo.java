package holding;

/** Shared geography helpers. */
public final class Geo {

    public static final double KM_PER_DEG_LAT = 111.32;

    private Geo() {
    }

    /** Approximate distance in km between two points. Flat-earth math, accurate at airport scale. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dy = (lat1 - lat2) * KM_PER_DEG_LAT;
        double dx = (lon1 - lon2) * KM_PER_DEG_LAT * Math.cos(Math.toRadians((lat1 + lat2) / 2));
        return Math.sqrt(dx * dx + dy * dy);
    }
}