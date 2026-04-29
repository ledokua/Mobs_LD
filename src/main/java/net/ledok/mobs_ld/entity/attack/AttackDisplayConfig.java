package net.ledok.mobs_ld.entity.attack;

public record AttackDisplayConfig(
        AnimationStyle animationStyle,
        int prepareColor,
        int invokeColor,
        int previewColor
) {
    public enum AnimationStyle {
        GROW,
        DEFAULT,
        SWEEP
    }

    public AttackDisplayConfig(AnimationStyle animationStyle, int prepareColor, int invokeColor) {
        this(animationStyle, prepareColor, invokeColor, -1);
    }

    public int resolvedPreviewColor() {
        if (previewColor != -1) {
            return previewColor;
        }
        return (0x40 << 24) | (prepareColor & 0x00FFFFFF);
    }

    public static final AttackDisplayConfig DEFAULT =
            new AttackDisplayConfig(AnimationStyle.DEFAULT, 0xFF8B0000, 0xFFFF0000);
}
