package holding;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoAroundDetectorTest {

    private static final double SEA_LAT = 47.449;
    private static final double SEA_LON = -122.309;

    private final GoAroundDetector detector = new GoAroundDetector(SEA_LAT, SEA_LON);

    /** Builds a flight from pairs of {km north of SEA, altitude in m}, one point every 10 seconds. */
    private static List<Position> flight(double[][] points) {
        List<Position> track = new ArrayList<>();
        long time = 1_800_000_000L;
        for (double[] pt : points) {
            double lat = SEA_LAT + pt[0] / Geo.KM_PER_DEG_LAT;
            track.add(new Position(time, "test01", "TEST1", lat, SEA_LON, pt[1], 180, 70));
            time += 10;
        }
        return track;
    }

    @Test
    void normalLandingIsNotAGoAround() {
        List<Position> track = flight(new double[][]{
                {20, 1500}, {15, 1100}, {10, 800}, {6, 500}, {3, 300}, {1, 170}
        });
        assertEquals(0, detector.countGoArounds(track));
    }

    @Test
    void goAroundIsCounted() {
        List<Position> track = flight(new double[][]{
                {10, 800}, {5, 400}, {2, 250},           // first approach, low near the runway
                {-2, 500}, {-6, 900}, {-10, 1100},       // climbs away past the airport
                {-6, 1000}, {6, 700}, {3, 350}, {1, 170} // comes around and lands
        });
        assertEquals(1, detector.countGoArounds(track));
    }

    @Test
    void departureIsNotAGoAround() {
        List<Position> track = flight(new double[][]{
                {0, 150}, {-3, 400}, {-8, 900}, {-15, 1500}, {-25, 2500}
        });
        assertEquals(0, detector.countGoArounds(track));
    }

    @Test
    void highOverflightIsNotAGoAround() {
        List<Position> track = flight(new double[][]{
                {10, 2000}, {0, 2000}, {-10, 2000},      // passes right over SEA, high
                {-6, 1200}, {-3, 600}, {-1, 200}         // then lands
        });
        assertEquals(0, detector.countGoArounds(track));
    }

    @Test
    void smallBumpOnFinalIsNotAGoAround() {
        List<Position> track = flight(new double[][]{
                {6, 440}, {5, 470}, {4, 430}, {3, 460},  // wobbles around 450 m but never climbs to 900
                {2, 300}, {1, 170}
        });
        assertEquals(0, detector.countGoArounds(track));
    }
}