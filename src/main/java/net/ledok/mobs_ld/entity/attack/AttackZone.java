package net.ledok.mobs_ld.entity.attack;

public sealed interface AttackZone permits AttackZone.Rectangle, AttackZone.Cone, AttackZone.Circle {
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
}
