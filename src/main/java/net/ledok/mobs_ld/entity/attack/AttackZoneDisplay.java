package net.ledok.mobs_ld.entity.attack;

import com.mojang.math.Transformation;
import net.ledok.mobs_ld.registry.ModBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;

public class AttackZoneDisplay {
    private static final int BRIGHT_WINDOW_TICKS = 2;
    private static final Brightness BRIGHTNESS_DIM = new Brightness(7, 7);
    private static final Brightness BRIGHTNESS_FLASH = new Brightness(15, 15);
    private static final float THICKNESS = 0.0625F;

    private static final Method SET_BLOCK_STATE = findMethod(Display.BlockDisplay.class, "setBlockState", BlockState.class);
    private static final Method SET_TRANSFORMATION = findMethod(Display.class, "setTransformation", Transformation.class);
    private static final Method SET_BRIGHTNESS_OVERRIDE = findMethod(Display.class, "setBrightnessOverride", Brightness.class);
    private static final Method SET_GLOW_COLOR_OVERRIDE = findMethod(Display.class, "setGlowColorOverride", int.class);
    private static final Method SET_TRANSFORM_INTERPOLATION_DURATION = findMethod(Display.class, "setTransformationInterpolationDuration", int.class);
    private static final Method SET_TRANSFORM_INTERPOLATION_DELAY = findMethod(Display.class, "setTransformationInterpolationDelay", int.class);

    private final Display.BlockDisplay entity;
    private final AttackZone zone;
    private final AttackDisplayConfig config;
    private final int totalWindupTicks;
    private final float baseScaleX;
    private final float baseScaleY;
    private final float baseScaleZ;
    private final Vector3f baseTranslation;
    private final Quaternionf leftRotation;
    private final Quaternionf rightRotation;

    private AttackZoneDisplay(
            Display.BlockDisplay entity,
            AttackZone zone,
            AttackDisplayConfig config,
            int totalWindupTicks,
            Transformation baseTransform
    ) {
        this.entity = entity;
        this.zone = zone;
        this.config = config;
        this.totalWindupTicks = Math.max(1, totalWindupTicks);
        this.baseTranslation = new Vector3f(baseTransform.getTranslation());
        this.leftRotation = new Quaternionf(baseTransform.getLeftRotation());
        this.rightRotation = new Quaternionf(baseTransform.getRightRotation());
        this.baseScaleX = baseTransform.getScale().x();
        this.baseScaleY = baseTransform.getScale().y();
        this.baseScaleZ = baseTransform.getScale().z();
    }

    public static AttackZoneDisplay spawn(
            ServerLevel world,
            Vec3 origin,
            float yawDegrees,
            AttackZone zone,
            AttackDisplayConfig config,
            int windupTicks
    ) {
        Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(world);
        if (display == null) {
            throw new IllegalStateException("Failed to create block display");
        }

        Transformation baseTransform = computeTransformation(yawDegrees, zone);
        display.setPos(origin.x, origin.y + 0.02, origin.z);
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);

        invoke(SET_BLOCK_STATE, display, blockFor(zone).defaultBlockState());
        invoke(SET_TRANSFORMATION, display, baseTransform);
        invoke(SET_BRIGHTNESS_OVERRIDE, display, BRIGHTNESS_DIM);
        invoke(SET_GLOW_COLOR_OVERRIDE, display, config.prepareColor());

        AttackZoneDisplay result = new AttackZoneDisplay(display, zone, config, windupTicks, baseTransform);
        result.applySpawnAnimationState();

        world.addFreshEntity(display);
        return result;
    }

    public void update(int windupTimer, int totalWindupTicks) {
        float t = 1.0F - ((float) windupTimer / Math.max(1, totalWindupTicks));
        t = clamp01(t);

        int color = lerpColor(config.prepareColor(), config.invokeColor(), t);
        setTint(color);
        invoke(SET_BRIGHTNESS_OVERRIDE, entity, BRIGHTNESS_DIM);

        switch (config.animationStyle()) {
            case PULSE -> {
                float pulse = 1.0F + 0.1F * (float) Math.sin(t * Math.PI * 4.0);
                setScaleMultiplier(pulse);
            }
            case FADE_IN -> {
                int alpha = Math.round(t * 255.0F);
                setTint((alpha << 24) | (color & 0x00FFFFFF));
            }
            case GROW, APPEAR -> {
                // handled by spawn state or default transform
            }
        }

        if (windupTimer <= BRIGHT_WINDOW_TICKS) {
            setTint(config.invokeColor());
            invoke(SET_BRIGHTNESS_OVERRIDE, entity, BRIGHTNESS_FLASH);
        }
    }

    public void setBrightRed() {
        setTint(config.invokeColor());
        invoke(SET_BRIGHTNESS_OVERRIDE, entity, BRIGHTNESS_FLASH);
    }

    public void remove() {
        entity.discard();
    }

    private static Block blockFor(AttackZone zone) {
        return switch (zone) {
            case AttackZone.Rectangle r -> ModBlocks.ATTACK_ZONE_RECT;
            case AttackZone.Cone c -> ModBlocks.ATTACK_ZONE_CONE;
            case AttackZone.Circle c -> ModBlocks.ATTACK_ZONE_CIRCLE;
            case AttackZone.CircleTarget ct -> ModBlocks.ATTACK_ZONE_CIRCLE;
        };
    }

    private void applySpawnAnimationState() {
        switch (config.animationStyle()) {
            case APPEAR -> {
                invoke(SET_TRANSFORM_INTERPOLATION_DURATION, entity, 0);
                invoke(SET_TRANSFORM_INTERPOLATION_DELAY, entity, 0);
                setScaleMultiplier(1.0F);
            }
            case GROW -> {
                invoke(SET_TRANSFORM_INTERPOLATION_DURATION, entity, totalWindupTicks);
                invoke(SET_TRANSFORM_INTERPOLATION_DELAY, entity, 0);
                setScaleMultiplier(0.0F);
                setScaleMultiplier(1.0F);
            }
            case PULSE -> {
                invoke(SET_TRANSFORM_INTERPOLATION_DURATION, entity, 0);
                invoke(SET_TRANSFORM_INTERPOLATION_DELAY, entity, 0);
                setScaleMultiplier(1.0F);
            }
            case FADE_IN -> {
                invoke(SET_TRANSFORM_INTERPOLATION_DURATION, entity, 0);
                invoke(SET_TRANSFORM_INTERPOLATION_DELAY, entity, 0);
                setScaleMultiplier(1.0F);
                int color = config.prepareColor() & 0x00FFFFFF;
                setTint(color);
            }
        }
    }

    private void setTint(int argb) {
        invoke(SET_GLOW_COLOR_OVERRIDE, entity, argb);
    }

    private void setScaleMultiplier(float multiplier) {
        float clamped = Math.max(0.0F, multiplier);
        Transformation scaled = new Transformation(
                new Vector3f(baseTranslation),
                new Quaternionf(leftRotation),
                new Vector3f(baseScaleX * clamped, baseScaleY, baseScaleZ * clamped),
                new Quaternionf(rightRotation)
        );
        invoke(SET_TRANSFORMATION, entity, scaled);
    }

    private static Transformation computeTransformation(float yawDegrees, AttackZone zone) {
        Quaternionf noRot = new Quaternionf();
        Quaternionf yawRot = new Quaternionf().rotationY((float) Math.toRadians(-yawDegrees));

        return switch (zone) {
            case AttackZone.Rectangle r -> {
                Vector3f offset = new Vector3f(-r.width() * 0.5F, 0.0F, r.offsetForward());
                offset.rotate(yawRot);
                yield new Transformation(
                        offset,
                        yawRot,
                        new Vector3f(r.width(), THICKNESS, r.length()),
                        noRot
                );
            }
            case AttackZone.Cone c -> {
                float approxWidth = (float) (2.0 * c.maxDistance() * Math.sin(Math.toRadians(c.angleDegrees() * 0.5)));
                Vector3f offset = new Vector3f(-approxWidth * 0.5F, 0.0F, 0.0F);
                offset.rotate(yawRot);
                yield new Transformation(
                        offset,
                        yawRot,
                        new Vector3f(approxWidth, THICKNESS, c.maxDistance()),
                        noRot
                );
            }
            case AttackZone.Circle c -> {
                float radius = c.radius();
                yield new Transformation(
                        new Vector3f(-radius, 0.0F, -radius),
                        noRot,
                        new Vector3f(radius * 2.0F, THICKNESS, radius * 2.0F),
                        noRot
                );
            }
            case AttackZone.CircleTarget c -> {
                float radius = c.radius();
                yield new Transformation(
                        new Vector3f(-radius, 0.0F, -radius),
                        noRot,
                        new Vector3f(radius * 2.0F, THICKNESS, radius * 2.0F),
                        noRot
                );
            }
        };
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            Method method = owner.getDeclaredMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Missing method: " + owner.getName() + "#" + name, e);
        }
    }

    private static void invoke(Method method, Object target, Object... args) {
        try {
            method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed invoking " + method.getName(), e);
        }
    }

    private static float clamp01(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    private static int lerpColor(int from, int to, float t) {
        int a = lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = lerp((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
        int g = lerp((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
        int b = lerp(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }
}
