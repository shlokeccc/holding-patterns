package holding;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Properties;

/**
 * Downloads SEA-area traffic from OpenSky one hour at a time.
 * One query at a time, every query filtered by hour, resumable.
 */
public class Fetcher {

    private static final String URL = "jdbc:trino://trino.opensky-network.org:443";

    private static final String QUERY = """
            SELECT time, icao24, callsign, lat, lon, baroaltitude, heading, velocity
            FROM minio.osky.state_vectors_data4
            WHERE hour = ?
              AND lat BETWEEN 46.6 AND 48.3
              AND lon BETWEEN -123.6 AND -121.0
              AND onground = false
              AND baroaltitude IS NOT NULL
              AND heading IS NOT NULL
              AND baroaltitude < 6096
              AND time % 10 = 0
            ORDER BY icao24, time
            """;

    public static void main(String[] args) throws Exception {
        LocalDate start = LocalDate.parse(args.length > 0 ? args[0] : "2026-10-01");
        LocalDate end = LocalDate.parse(args.length > 1 ? args[1] : "2026-11-01");
        Path outDir = Path.of("../data/raw/hours");
        Files.createDirectories(outDir);

        Properties props = new Properties();
        props.setProperty("user", "retroboss54");
        props.setProperty("SSL", "true");
        props.setProperty("externalAuthentication", "true");
        props.setProperty("externalAuthenticationTokenCache", "MEMORY");

        long firstHour = start.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long lastHour = end.atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        try (Connection conn = DriverManager.getConnection(URL, props);
             PreparedStatement stmt = conn.prepareStatement(QUERY)) {

            for (long hour = firstHour; hour < lastHour; hour += 3600) {
                Path out = outDir.resolve(fileName(hour));
                if (Files.exists(out)) {
                    System.out.println("skip   " + out.getFileName());
                    continue;
                }

                long startMs = System.currentTimeMillis();
                int rows = fetchHour(stmt, hour, out);
                double seconds = (System.currentTimeMillis() - startMs) / 1000.0;
                System.out.printf("saved  %s  %,7d rows  %.1f s%n", out.getFileName(), rows, seconds);

                Thread.sleep(1000);   // be polite to OpenSky's servers
            }
        }
    }

    /** e.g. 1792083600 -> "sea_2026-10-15_17utc.csv" */
    static String fileName(long hour) {
        LocalDateTime t = LocalDateTime.ofEpochSecond(hour, 0, ZoneOffset.UTC);
        return String.format("sea_%s_%02dutc.csv", t.toLocalDate(), t.getHour());
    }

    /** Runs the query for one hour and writes the rows to a CSV. Returns the row count. */
    private static int fetchHour(PreparedStatement stmt, long hour, Path out)
            throws SQLException, IOException {
        stmt.setLong(1, hour);
        Path partial = out.resolveSibling(out.getFileName() + ".part");
        int rows = 0;

        try (ResultSet rs = stmt.executeQuery();
             BufferedWriter writer = Files.newBufferedWriter(partial)) {

            writer.write("time,icao24,callsign,lat,lon,baroaltitude,heading,velocity");
            writer.newLine();

            while (rs.next()) {
                String callsign = rs.getString("callsign");
                writer.write(rs.getLong("time") + ","
                        + rs.getString("icao24") + ","
                        + (callsign == null ? "" : callsign.trim()) + ","
                        + rs.getDouble("lat") + ","
                        + rs.getDouble("lon") + ","
                        + rs.getDouble("baroaltitude") + ","
                        + rs.getDouble("heading") + ","
                        + numberOrEmpty(rs, "velocity"));
                writer.newLine();
                rows++;
            }
        }

        // Only now that the file is complete does it get its real name
        Files.move(partial, out, StandardCopyOption.REPLACE_EXISTING);
        return rows;
    }

    /** JDBC returns 0 for a NULL number, so check wasNull() and write an empty field instead. */
    private static String numberOrEmpty(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? "" : String.valueOf(value);
    }
}