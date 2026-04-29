package net.ledok.mobs_ld.entity.attack;

public record AttackDisplayConfig(
        AnimationStyle animationStyle,
        int prepareColor,
        int invokeColor
) {
    public enum AnimationStyle {
        APPEAR,
        GROW,
        PULSE,
        FADE_IN
    }

    public static final AttackDisplayConfig DEFAULT =
            new AttackDisplayConfig(AnimationStyle.GROW, 0xFF8B0000, 0xFFFF0000);
}
