package holding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldDetectorTest {

    private final HoldDetector detector = new HoldDetector();

    @Test
    void straightFlightHasNoLoops() {
        List<Position> track = new TrackBuilder(180, 120)
                .straight(600)
                .build();

        assertTrue(detector.findLoops(track).isEmpty());
    }

    @Test
    void racetrackIsDetectedAsHold() {
        // Two 3-minute legs joined by 1-minute 180-degree right turns: a standard holding lap
        List<Position> track = new TrackBuilder(0, 120)
                .straight(180)
                .turn(180, 60)
                .straight(180)
                .turn(180, 60)
                .build();

        List<Loop> loops = detector.findLoops(track);

        assertEquals(1, loops.size());
        assertEquals(LoopType.RACETRACK_HOLD, loops.get(0).type());
        assertEquals("right", loops.get(0).direction());
    }

    @Test
    void squareBoxIsDetectedAsVectoringLoop() {
        // Four 2-minute legs joined by 90-degree right turns: a delay-vector box
        List<Position> track = new TrackBuilder(0, 120)
                .straight(120).turn(90, 30)
                .straight(120).turn(90, 30)
                .straight(120).turn(90, 30)
                .straight(120).turn(90, 30)
                .build();

        List<Loop> loops = detector.findLoops(track);

        assertEquals(1, loops.size());
        assertEquals(LoopType.VECTORING_LOOP, loops.get(0).type());
    }

    @Test
    void slowWideCircleIsNotALoop() {
        // A full 360 spread over 30 minutes: too slow and too big to be a hold
        List<Position> track = new TrackBuilder(0, 120)
                .turn(360, 1800)
                .build();

        assertTrue(detector.findLoops(track).isEmpty());
    }

    @Test
    void wobblingDoesNotAddUpToALoop() {
        // Small left-right wobbles, like heading noise in real data
        TrackBuilder builder = new TrackBuilder(90, 120);
        for (int k = 0; k < 60; k++) {
            builder.turn(10, 10).turn(-10, 10);
        }

        assertTrue(detector.findLoops(builder.build()).isEmpty());
    }
}