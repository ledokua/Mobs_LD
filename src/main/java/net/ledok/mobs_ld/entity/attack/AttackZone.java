package net.ledok.mobs_ld.entity.attack;

public sealed interface AttackZone permits AttackZone.Rectangle, AttackZone.Cone, AttackZone.Circle, AttackZone.CircleTarget, AttackZone.CircleRays {
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

    record CircleRays(int rayCount, float length, float width) implements AttackZone {
        public CircleRays {
            rayCount = Math.max(2, rayCount);
            if ((rayCount & 1) != 0) {
                rayCount++;
            }
            length = Math.max(0.0F, length);
            width = Math.max(0.0F, width);
        }

        @Override
        public float maxForwardReach() {
            return length;
        }
    }
}
