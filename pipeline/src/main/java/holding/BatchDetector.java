package holding;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Runs the hold detector over every hourly file in time order.
 * Splits each aircraft's positions into separate flights wherever there is a gap,
 * and keeps only flights in progress in memory (streaming).
 */
public class BatchDetector {

    private static final long MAX_GAP_SECONDS = 600;   // 10 minutes of silence ends a flight
    private static final double KM_PER_DEG_LAT = 111.32;

    // Airports near SEA whose landings must not count as SEA arrivals: {lat, lon}
    private static final double[] SEA = {47.449, -122.309};
    private static final double[] BOEING_FIELD = {47.530, -122.302};
    private static final double[] RENTON = {47.493, -122.216};

    private final HoldDetector detector = new HoldDetector();
    private final Map<String, List<Position>> active = new HashMap<>();
    private final BufferedWriter flightsOut;
    private final BufferedWriter loopsOut;

    private long positionsRead = 0;
    private int flights = 0;
    private int arrivals = 0;
    private int airlineArrivals = 0;
    private int loops = 0;
    private int airlineArrivalLoops = 0;

    BatchDetector(BufferedWriter flightsOut, BufferedWriter loopsOut) throws IOException {
        this.flightsOut = flightsOut;
        this.loopsOut = loopsOut;
        flightsOut.write("icao24,callsign,start_time,end_time,minutes,n_points,"
                + "start_alt,end_alt,end_lat,end_lon,is_arrival,is_airline,n_loops");
        flightsOut.newLine();
        loopsOut.write("icao24,callsign,is_arrival,is_airline,start_time,end_time,minutes,"
                + "direction,size_km,elongation,center_lat,center_lon,type");
        loopsOut.newLine();
    }

    public static void main(String[] args) throws IOException {
        Path inDir = Path.of(args.length > 0 ? args[0] : "../data/raw/hours");
        Path outDir = Path.of(args.length > 1 ? args[1] : "../data/processed");
        Files.createDirectories(outDir);

        List<Path> files;
        try (Stream<Path> listing = Files.list(inDir)) {
            files = listing.filter(p -> p.toString().endsWith(".csv")).sorted().toList();
        }
        System.out.println("Processing " + files.size() + " hourly files...");

        try (BufferedWriter flightsOut = Files.newBufferedWriter(outDir.resolve("flights.csv"));
             BufferedWriter loopsOut = Files.newBufferedWriter(outDir.resolve("loops.csv"))) {

            BatchDetector batch = new BatchDetector(flightsOut, loopsOut);
            for (Path file : files) {
                batch.processHour(file);
                if (file.getFileName().toString().endsWith("_23utc.csv")) {
                    System.out.printf("  finished %s  (%,d positions so far)%n",
                            file.getFileName().toString().substring(4, 14), batch.positionsRead);
                }
            }
            batch.finishAll();
            batch.printSummary();
        }
    }

    /** Reads one hour file, adding each position to its aircraft's current flight. */
    void processHour(Path file) throws IOException {
        long latestTime = 0;

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            reader.readLine();   // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Position p = CsvTrackReader.parseLine(line);
                positionsRead++;
                latestTime = Math.max(latestTime, p.time());

                List<Position> track = active.get(p.icao24());
                if (track != null && p.time() - track.getLast().time() > MAX_GAP_SECONDS) {
                    finishFlight(track);   // long gap: the previous flight is over
                    track = null;
                }
                if (track == null) {
                    track = new ArrayList<>();
                    active.put(p.icao24(), track);
                }
                track.add(p);
            }
        }

        // Any flight not heard from in the last 10 minutes of this hour is finished
        Iterator<List<Position>> it = active.values().iterator();
        while (it.hasNext()) {
            List<Position> track = it.next();
            if (latestTime - track.getLast().time() > MAX_GAP_SECONDS) {
                finishFlight(track);
                it.remove();
            }
        }
    }

    /** At the end of the data, every flight still in progress is finished. */
    void finishAll() throws IOException {
        for (List<Position> track : active.values()) {
            finishFlight(track);
        }
        active.clear();
    }

    /** Detects loops in one completed flight and writes the results. */
    private void finishFlight(List<Position> track) throws IOException {
        if (track.size() < 2) {
            return;
        }
        Position first = track.getFirst();
        Position last = track.getLast();
        String callsign = callsignOf(track);
        boolean arrival = isSeaArrival(first, last);
        boolean airline = isAirline(callsign);
        List<Loop> found = detector.findLoops(track);

        flights++;
        loops += found.size();
        if (arrival) {
            arrivals++;
        }
        if (arrival && airline) {
            airlineArrivals++;
            airlineArrivalLoops += found.size();
        }

        writeRow(flightsOut, first.icao24(), callsign, first.time(), last.time(),
                (last.time() - first.time()) / 60.0, track.size(),
                first.altitude(), last.altitude(), last.lat(), last.lon(),
                arrival, airline, found.size());

        for (Loop loop : found) {
            writeRow(loopsOut, first.icao24(), callsign, arrival, airline,
                    loop.startTime(), loop.endTime(), loop.minutes(), loop.direction(),
                    loop.sizeKm(), loop.elongation(), loop.centerLat(), loop.centerLon(),
                    loop.type());
        }
    }

    /** Ends low, descending, and closer to SEA than to Boeing Field or Renton. */
    static boolean isSeaArrival(Position first, Position last) {
        if (!(last.altitude() < 600 && last.altitude() < first.altitude())) {
            return false;
        }
        double toSea = distanceKm(last, SEA);
        return toSea < 25
                && toSea < distanceKm(last, BOEING_FIELD)
                && toSea < distanceKm(last, RENTON);
    }

    /** Airline-style callsign: three letters, then a digit (ASA416, DAL868). */
    static boolean isAirline(String callsign) {
        return callsign.matches("[A-Z]{3}[0-9].*");
    }

    /** Approximate distance in km from a position to an airport {lat, lon}. */
    static double distanceKm(Position p, double[] airport) {
        double dy = (p.lat() - airport[0]) * KM_PER_DEG_LAT;
        double dx = (p.lon() - airport[1]) * KM_PER_DEG_LAT * Math.cos(Math.toRadians(airport[0]));
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static String callsignOf(List<Position> track) {
        for (Position p : track) {
            if (!p.callsign().isBlank()) {
                return p.callsign();
            }
        }
        return "";
    }

    /** Writes values separated by commas, then a new line. */
    private static void writeRow(BufferedWriter out, Object... values) throws IOException {
        for (int k = 0; k < values.length; k++) {
            if (k > 0) {
                out.write(",");
            }
            out.write(String.valueOf(values[k]));
        }
        out.newLine();
    }

    private void printSummary() {
        System.out.printf("Read %,d positions%n", positionsRead);
        System.out.printf("%,d flights, %,d SEA arrivals, %,d of them airline-style%n",
                flights, arrivals, airlineArrivals);
        System.out.printf("%,d loops in total, %,d by airline-style SEA arrivals%n",
                loops, airlineArrivalLoops);
    }
}