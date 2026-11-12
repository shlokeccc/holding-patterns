package holding;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class App {

    public static void main(String[] args) throws IOException {
        Path csv = Path.of(args.length > 0
                ? args[0]
                : "../data/raw/sea_2026-10-15_17utc.csv");

        Map<String, List<Position>> tracks = CsvTrackReader.readTracks(csv);
        System.out.println("Read " + tracks.size() + " aircraft from " + csv);

        HoldDetector detector = new HoldDetector();
        int found = 0;

        for (Map.Entry<String, List<Position>> entry : tracks.entrySet()) {
            String callsign = callsignOf(entry.getValue());
            for (Loop loop : detector.findLoops(entry.getValue())) {
                System.out.printf("%-7s %-8s %5.1f min  %-5s  %5.1f km  elongation %.2f  %s%n",
                        entry.getKey(), callsign, loop.minutes(), loop.direction(),
                        loop.sizeKm(), loop.elongation(), loop.type());
                found++;
            }
        }
        System.out.println(found + " loops found");
    }

    /** First non-blank callsign in a track, or "?" if it never broadcast one. */
    private static String callsignOf(List<Position> track) {
        for (Position p : track) {
            if (!p.callsign().isBlank()) {
                return p.callsign();
            }
        }
        return "?";
    }
}