package holding;

import java.util.List;

/**
 * Counts go-arounds: the aircraft comes low near the runway, climbs away,
 * and later comes low near the runway again.
 */
public class GoAroundDetector {

    private final double airportLat;
    private final double airportLon;
    private final double lowAltitude;     // meters above sea level
    private final double nearKm;
    private final double climbAltitude;   // meters above sea level

    /** Default settings for SEA (runways at about 130 m above sea level). */
    public GoAroundDetector(double airportLat, double airportLon) {
        this(airportLat, airportLon, 450, 6, 900);
    }

    public GoAroundDetector(double airportLat, double airportLon,
                            double lowAltitude, double nearKm, double climbAltitude) {
        this.airportLat = airportLat;
        this.airportLon = airportLon;
        this.lowAltitude = lowAltitude;
        this.nearKm = nearKm;
        this.climbAltitude = climbAltitude;
    }

    /** Track must be one flight's positions, sorted by time. */
    public int countGoArounds(List<Position> track) {
        int approaches = 0;
        boolean climbedAway = true;   // true so the first low pass counts as an approach

        for (Position p : track) {
            if (Double.isNaN(p.altitude())) {
                continue;
            }
            boolean lowNearRunway = p.altitude() < lowAltitude
                    && Geo.distanceKm(p.lat(), p.lon(), airportLat, airportLon) < nearKm;

            if (lowNearRunway && climbedAway) {
                approaches++;
                climbedAway = false;
            } else if (!climbedAway && p.altitude() > climbAltitude) {
                climbedAway = true;
            }
        }
        return Math.max(0, approaches - 1);
    }
}