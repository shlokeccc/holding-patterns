package holding;

/** One detected loop: the Java version of one row of the R `loops` table. */
public record Loop(
        long startTime,
        long endTime,
        double minutes,
        String direction,     // "right" or "left"
        double sizeKm,
        double elongation,
        double centerLat,
        double centerLon,
        double avgSpeed,      // meters per second, averaged over the loop
        double avgAltitude,   // meters, averaged over the loop
        LoopType type
) {}