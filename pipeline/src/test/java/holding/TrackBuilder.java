package holding;

import java.util.ArrayList;
import java.util.List;

/** Builds fake flight tracks for tests by flying straight legs and turns. */
class TrackBuilder {

    private static final double STEP_SECONDS = 10;
    private static final double KM_PER_DEG_LAT = 111.32;

    private final List<Position> points = new ArrayList<>();
    private final double speedKmPerSec;
    private long time = 1_800_000_000L;
    private double lat = 47.9;
    private double lon = -122.3;
    private double heading;

    TrackBuilder(double startHeading, double speedMetersPerSec) {
        this.heading = startHeading;
        this.speedKmPerSec = speedMetersPerSec / 1000.0;
        record();
    }

    TrackBuilder straight(double seconds) {
        return fly(seconds, 0);
    }

    /** Positive degrees = right turn, negative = left turn. */
    TrackBuilder turn(double degrees, double seconds) {
        return fly(seconds, degrees);
    }

    private TrackBuilder fly(double seconds, double totalTurn) {
        int steps = (int) Math.round(seconds / STEP_SECONDS);
        double turnPerStep = totalTurn / steps;
        for (int s = 0; s < steps; s++) {
            heading = (heading + turnPerStep + 360) % 360;
            double rad = Math.toRadians(heading);
            double km = speedKmPerSec * STEP_SECONDS;
            lat += km * Math.cos(rad) / KM_PER_DEG_LAT;
            lon += km * Math.sin(rad) / (KM_PER_DEG_LAT * Math.cos(Math.toRadians(lat)));
            time += (long) STEP_SECONDS;
            record();
        }
        return this;
    }

    private void record() {
        points.add(new Position(time, "test01", "TEST1", lat, lon,
                3000, heading, speedKmPerSec * 1000));
    }

    List<Position> build() {
        return points;
    }
}