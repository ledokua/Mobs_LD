package net.ledok.mobs_ld.mob.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The lane a dash runs along, in its own frame: {@code along} is blocks ahead of where the dash starts (its body's
 * centre then), {@code across} blocks to the right of the centre line. The lane is exactly the strip its telegraph
 * draws: {@code width} wide, from the start to where the body's centre stops ({@code length}). Pure maths, unit-tested.
 *
 * @param width  lane width (the body's width for {@code width: body})
 * @param length how far the body's centre travels
 */
public record DashLane(double width, double length) {
    /** Longest strip segment; each segment lies on the floor found under it, so a strip follows slopes in steps. */
    public static final double MAX_SEGMENT = 6.0;
    /** Floors closer than this (slabs, carpets, snow) share one strip segment. */
    public static final double FLOOR_TOLERANCE = 0.5;
    /**
     * Length of the arrowhead past the strip's end, per block of lane width (its base is 1.5 lane widths wide, see
     * AttackZone.Strip).
     */
    public static final double HEAD_LENGTH = 0.75;

    public DashLane {
        width = Math.max(0.0, width);
        length = Math.max(0.0, length);
    }

    /** Whether a victim's centre is on the lane: within half the width of the centre line, between start and end. */
    public boolean contains(double along, double across) {
        return along >= 0.0 && along <= length && Math.abs(across) <= width / 2.0;
    }

    /**
     * Whether the body touched a victim during one tick. The body reaches {@code halfDepth} ahead of and behind its
     * centre; its centre went from {@code bodyBefore} to {@code bodyNow} along the lane, the victim's from
     * {@code victimBefore} to {@code victimNow}. Swept: the test is on the victim's position relative to the body over
     * the whole tick, so neither a fast body nor a victim running (or rolling) through it can skip the contact. The
     * victim's centre must be on the lane now (stepping out of it in time is the dodge).
     */
    public boolean contact(double bodyBefore, double bodyNow, double halfDepth, double victimBefore, double victimNow,
                           double acrossNow) {
        if (!contains(victimNow, acrossNow)) {
            return false;
        }
        double relativeBefore = victimBefore - bodyBefore;
        double relativeNow = victimNow - bodyNow;
        return Math.min(relativeBefore, relativeNow) <= halfDepth && Math.max(relativeBefore, relativeNow) >= -halfDepth;
    }

    /**
     * Which way {@code push: side} shoves a victim: +1 to the lane's right, -1 to its left, i.e. out of the lane on the
     * side it stands; one dead on the centre line goes right.
     */
    public static int sideSign(double across) {
        return across >= 0.0 ? 1 : -1;
    }

    /** Length of the arrowhead drawn past the end of a lane this wide. */
    public static double headLength(double width) {
        return width * HEAD_LENGTH;
    }

    /** Number of one-block floor samples a strip of {@code length} takes (one per started block). */
    public static int samples(double length) {
        return Math.max(0, (int) Math.ceil(length - 1.0e-9));
    }

    /** Where sample {@code index} of a strip of {@code length} is taken: the middle of its block (the last may be short). */
    public static double sampleAt(int index, double length) {
        return (index + Math.min(index + 1.0, length)) / 2.0;
    }

    /**
     * Cuts a strip of {@code length} into segments to lay on the ground. {@code floors[k]} is the floor height under
     * block {@code k} of the strip (from {@code k} to {@code k + 1}, the last one possibly shorter; see
     * {@link #sampleAt}), NaN where there is none (the void, a drop deeper than the ground scan). Consecutive blocks
     * whose floors stay within {@link #FLOOR_TOLERANCE} of each other form a run, laid at the run's highest floor (so
     * no part of it is buried in a rise); a run longer than {@code maxSegment} is split into equal segments no longer
     * than that. Runs over the void are left out, so the strip shows a gap there.
     */
    public static List<Segment> segments(double length, double maxSegment, double[] floors) {
        List<Segment> segments = new ArrayList<>();
        int count = Math.min(samples(length), floors.length);
        int runStart = 0;
        while (runStart < count) {
            boolean empty = Double.isNaN(floors[runStart]);
            double low = floors[runStart];
            double high = floors[runStart];
            int runEnd = runStart + 1;
            while (runEnd < count) {
                double floor = floors[runEnd];
                if (Double.isNaN(floor) != empty) {
                    break;
                }
                if (!empty && (Math.max(high, floor) - Math.min(low, floor) > FLOOR_TOLERANCE)) {
                    break;
                }
                if (!empty) {
                    low = Math.min(low, floor);
                    high = Math.max(high, floor);
                }
                runEnd++;
            }
            if (!empty) {
                double from = runStart;
                double to = Math.min(runEnd, length);
                int pieces = Math.max(1, (int) Math.ceil((to - from) / maxSegment - 1.0e-9));
                double piece = (to - from) / pieces;
                for (int p = 0; p < pieces; p++) {
                    segments.add(new Segment(from + p * piece, p == pieces - 1 ? to : from + (p + 1) * piece, high));
                }
            }
            runStart = runEnd;
        }
        return segments;
    }

    /**
     * The floor the arrowhead lies on: the highest of its own samples (one per started block of its length, past the
     * lane's end), NaN when none found a floor (it is then not drawn).
     */
    public static double headFloor(double[] floors) {
        double floor = Double.NaN;
        for (double sample : floors) {
            if (!Double.isNaN(sample) && (Double.isNaN(floor) || sample > floor)) {
                floor = sample;
            }
        }
        return floor;
    }

    /**
     * The swept contact test over a whole dash. Positions are sampled once per check, in the dashing mob's own tick,
     * and each victim's position at the previous check is remembered here: vanilla's previous-position fields
     * ({@code xo}/{@code zo}) already equal the current position for a player ticked earlier in the same server tick,
     * which would let a head-on roll skip the body. A victim not seen at the previous check (it just came near) counts
     * as having stood still; one that left the search area is forgotten, so an old position never joins a new one.
     * Victims must be looked for at least {@link #LOOKAHEAD} blocks ahead of the body's front, so they are seen before
     * the body reaches them.
     *
     * @param <K> what identifies a victim (its UUID in game)
     */
    public static final class Sweep<K> {
        /** Blocks ahead of the body's front (beyond this tick's step) victims are tracked from. */
        public static final double LOOKAHEAD = 3.0;

        private final DashLane lane;
        private final double halfDepth;
        private Map<K, Double> previous = new HashMap<>();
        private Map<K, Double> current = new HashMap<>();
        private double bodyBefore;
        private double bodyNow;
        private boolean started;

        public Sweep(DashLane lane, double halfDepth) {
            this.lane = lane;
            this.halfDepth = halfDepth;
        }

        /** Starts a check: the body's centre is now {@code body} along the lane (the first check: where it starts). */
        public void check(double body) {
            bodyBefore = started ? bodyNow : body;
            bodyNow = body;
            started = true;
            Map<K, Double> swap = previous;
            previous = current;
            current = swap;
            current.clear();
        }

        public double bodyBefore() {
            return bodyBefore;
        }

        public double bodyNow() {
            return bodyNow;
        }

        /**
         * Whether {@code victim}, whose centre is now {@code along} / {@code across}, met the body since the previous
         * check; remembers where it is for the next one.
         */
        public boolean meets(K victim, double along, double across) {
            Double before = previous.get(victim);
            current.put(victim, along);
            return lane.contact(bodyBefore, bodyNow, halfDepth, before == null ? along : before, along, across);
        }
    }

    /** One piece of a strip: from {@code start} to {@code end} along the lane, lying at height {@code floor}. */
    public record Segment(double start, double end, double floor) {
        public double length() {
            return end - start;
        }
    }
}
