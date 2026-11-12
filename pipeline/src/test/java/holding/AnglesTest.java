package holding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnglesTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void smallRightTurn() {
        assertEquals(30.0, Angles.headingChange(90, 120), TOLERANCE);
    }

    @Test
    void wrapsPastNorthTurningRight() {
        assertEquals(20.0, Angles.headingChange(350, 10), TOLERANCE);
    }

    @Test
    void wrapsPastNorthTurningLeft() {
        assertEquals(-20.0, Angles.headingChange(10, 350), TOLERANCE);
    }

    @Test
    void noTurn() {
        assertEquals(0.0, Angles.headingChange(275, 275), TOLERANCE);
    }

    @Test
    void halfTurnIsEitherDirection() {
        assertEquals(180.0, Math.abs(Angles.headingChange(0, 180)), TOLERANCE);
    }
}