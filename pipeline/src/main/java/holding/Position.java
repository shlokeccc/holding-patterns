package holding;

/**
 * One aircraft at one moment: a single row of OpenSky data.
 */
public record Position(
        long time,          // Unix seconds
        String icao24,      // aircraft ID
        String callsign,    // flight number, may be blank
        double lat,
        double lon,
        double altitude,    // meters
        double heading,     // degrees, 0 = north
        double velocity     // meters per second
) {}