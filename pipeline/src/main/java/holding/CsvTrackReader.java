package holding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Reads an OpenSky CSV export into one time-sorted track per aircraft. */
public final class CsvTrackReader {

    private CsvTrackReader() {
    }

    /** Expected columns: time, icao24, callsign, lat, lon, baroaltitude, heading, velocity */
    public static Map<String, List<Position>> readTracks(Path csv) throws IOException {
        List<String> lines = Files.readAllLines(csv);
        Map<String, List<Position>> tracks = new TreeMap<>();

        for (String line : lines.subList(1, lines.size())) {   // skip the header row
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            for (int k = 0; k < f.length; k++) {
                f[k] = f[k].replace("\"", "").trim();   // remove quotes and extra spaces
            }

            Position p = new Position(
                    Long.parseLong(f[0]),
                    f[1],
                    f[2],
                    Double.parseDouble(f[3]),
                    Double.parseDouble(f[4]),
                    parseOrNaN(f[5]),
                    parseOrNaN(f[6]),
                    parseOrNaN(f[7]));

            tracks.computeIfAbsent(p.icao24(), id -> new ArrayList<>()).add(p);
        }

        for (List<Position> track : tracks.values()) {
            track.sort(Comparator.comparingLong(Position::time));
        }
        return tracks;
    }

    /** Missing values (empty or NULL) become NaN instead of crashing. */
    private static double parseOrNaN(String s) {
        if (s.isEmpty() || s.equalsIgnoreCase("NULL")) {
            return Double.NaN;
        }
        return Double.parseDouble(s);
    }
}