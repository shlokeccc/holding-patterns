package holding;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds loops in one aircraft's track: about 360 degrees of turning in one
 * direction, within a time limit, inside a small area.
 */
public class HoldDetector {

    private static final double KM_PER_DEG_LAT = 111.32;

    private final double maxMinutes;
    private final double maxSizeKm;
    private final double minTurnDeg;
    private final double racetrackElongation;

    /** Default settings, the same ones the R prototype used. */
    public HoldDetector() {
        this(12, 30, 340, 2);
    }

    public HoldDetector(double maxMinutes, double maxSizeKm,
                        double minTurnDeg, double racetrackElongation) {
        this.maxMinutes = maxMinutes;
        this.maxSizeKm = maxSizeKm;
        this.minTurnDeg = minTurnDeg;
        this.racetrackElongation = racetrackElongation;
    }

    /** Track must be one aircraft's positions, sorted by time. */
    public List<Loop> findLoops(List<Position> track) {
        List<Loop> loops = new ArrayList<>();
        int n = track.size();
        if (n < 2) {
            return loops;
        }

        // turn[k] = how much the plane turned between point k-1 and point k
        double[] turn = new double[n];
        for (int k = 1; k < n; k++) {
            turn[k] = Angles.headingChange(track.get(k - 1).heading(), track.get(k).heading());
        }

        int i = 0;
        while (i < n - 1) {
            double cumulative = 0;
            int end = -1;   // -1 means "no loop found yet"

            for (int j = i + 1; j < n; j++) {
                double minutes = (track.get(j).time() - track.get(i).time()) / 60.0;
                if (minutes > maxMinutes) {
                    break;
                }
                cumulative += turn[j];
                if (Math.abs(cumulative) >= minTurnDeg) {
                    end = j;
                    break;
                }
            }

            if (end != -1) {
                Loop loop = measure(track.subList(i, end + 1), cumulative);
                if (loop.sizeKm() <= maxSizeKm) {
                    loops.add(loop);
                    i = end + 1;   // skip past this loop
                    continue;
                }
            }
            i++;
        }
        return loops;
    }

    /** Size, shape, center and type of one loop segment. */
    private Loop measure(List<Position> seg, double cumulative) {
        int m = seg.size();
        double refLat = seg.get(0).lat();
        double refLon = seg.get(0).lon();
        double kmPerDegLon = KM_PER_DEG_LAT * Math.cos(Math.toRadians(refLat));

        double[] x = new double[m];
        double[] y = new double[m];
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double sumLat = 0, sumLon = 0;

        for (int k = 0; k < m; k++) {
            Position p = seg.get(k);
            x[k] = (p.lon() - refLon) * kmPerDegLon;
            y[k] = (p.lat() - refLat) * KM_PER_DEG_LAT;
            minX = Math.min(minX, x[k]);
            maxX = Math.max(maxX, x[k]);
            minY = Math.min(minY, y[k]);
            maxY = Math.max(maxY, y[k]);
            sumLat += p.lat();
            sumLon += p.lon();
        }

        double size = Math.max(maxX - minX, maxY - minY);
        double elongation = elongation(x, y);
        LoopType type = elongation >= racetrackElongation
                ? LoopType.RACETRACK_HOLD
                : LoopType.VECTORING_LOOP;

        Position first = seg.get(0);
        Position last = seg.get(m - 1);
        return new Loop(
                first.time(),
                last.time(),
                (last.time() - first.time()) / 60.0,
                cumulative > 0 ? "right" : "left",
                size,
                elongation,
                sumLat / m,
                sumLon / m,
                type);
    }

    /** How stretched out a set of points is: about 1 = round or square, higher = long and narrow. */
    static double elongation(double[] x, double[] y) {
        int m = x.length;
        double meanX = 0, meanY = 0;
        for (int k = 0; k < m; k++) {
            meanX += x[k];
            meanY += y[k];
        }
        meanX /= m;
        meanY /= m;

        double sxx = 0, syy = 0, sxy = 0;
        for (int k = 0; k < m; k++) {
            double dx = x[k] - meanX;
            double dy = y[k] - meanY;
            sxx += dx * dx;
            syy += dy * dy;
            sxy += dx * dy;
        }
        sxx /= (m - 1);
        syy /= (m - 1);
        sxy /= (m - 1);

        // Eigenvalues of the 2x2 covariance matrix, solved by formula
        double half = (sxx + syy) / 2;
        double diff = Math.sqrt(((sxx - syy) / 2) * ((sxx - syy) / 2) + sxy * sxy);
        double big = half + diff;
        double small = half - diff;
        return Math.sqrt(big / Math.max(small, 1e-6));
    }
}