package net.ledok.mobs_ld.entity.attack;

public sealed interface AttackZone permits AttackZone.Rectangle, AttackZone.Cone, AttackZone.Circle, AttackZone.CircleTarget,
        AttackZone.CircleRays, AttackZone.Strip {
    float maxForwardReach();

    record Rectangle(float width, float length, float offsetForward) implements AttackZone {
        @Override
        public float maxForwardReach() {
            return offsetForward + length;
        }
    }

    record Cone(float maxDistance, float angleDegrees) implements AttackZone {
        @Override
        public float maxForwardReach() {
            return maxDistance;
        }
    }

    record Circle(float radius) implements AttackZone {
        @Override
        public float maxForwardReach() {
            return radius;
        }
    }

    record CircleTarget(float range, float radius) implements AttackZone {
        @Override
        public float maxForwardReach() {
            return range;
        }
    }

    /**
     * {@code rayCount} rays from the origin (lines through it), each lane {@code width} wide at the origin and
     * {@code widen} blocks wider per block along it (0 = straight lanes; else trapezoids).
     */
    record CircleRays(int rayCount, float length, float width, float widen) implements AttackZone {
        public CircleRays {
            rayCount = Math.max(2, rayCount);
            if ((rayCount & 1) != 0) {
                rayCount++;
            }
            length = Math.max(0.0F, length);
            width = Math.max(0.0F, width);
            widen = Math.max(0.0F, widen);
        }

        public CircleRays(int rayCount, float length, float width) {
            this(rayCount, length, width, 0.0F);
        }

        @Override
        public float maxForwardReach() {
            return length;
        }
    }

    /**
     * One piece of a lane (a dash's strip, which is laid on the ground in segments): {@code width} wide, {@code length}
     * long ahead of the origin, optionally ending in an arrowhead {@code headLength} long past its far end (0 = none;
     * its base is wider than the lane). With the SWEEP style the piece fills while the wind-up runs from
     * {@code sweepFrom} to {@code sweepTo} (fractions of the whole wind-up), so the segments of one strip fill one after
     * the other, like a single long strip.
     */
    record Strip(float width, float length, float headLength, float sweepFrom, float sweepTo) implements AttackZone {
        /** The arrowhead's half-width at its base, per block of lane width. */
        public static final float HEAD_HALF_WIDTH = 0.75F;

        public Strip {
            width = Math.max(0.0F, width);
            length = Math.max(0.0F, length);
            headLength = Math.max(0.0F, headLength);
            sweepFrom = Math.max(0.0F, Math.min(1.0F, sweepFrom));
            sweepTo = Math.max(sweepFrom, Math.min(1.0F, sweepTo));
        }

        @Override
        public float maxForwardReach() {
            return length + headLength;
        }
    }
}
