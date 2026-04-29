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
    private static final Brightness BRIGHTNESS_DIM = new Brightness(7, 7);
    private static final Brightness BRIGHTNESS_FLASH = new Brightness(15, 15);
    private static final float THICKNESS = 0.0625F;

    private static final Method SET_BLOCK_STATE = findMethod(Display.BlockDisplay.class, "setBlockState", BlockState.class);
    private static final Method SET_TRANSFORMATION = findMethod(Display.class, "setTransformation", Transformation.class);
    private static final Method SET_BRIGHTNESS_OVERRIDE = findMethod(Display.class, "setBrightnessOverride", Brightness.class);
    private static final Method SET_TRANSFORM_INTERPOLATION_DURATION = findMethod(Display.class, "setTransformationInterpolationDuration", int.class);
    private static final Method SET_TRANSFORM_INTERPOLATION_DELAY = findMethod(Display.class, "setTransformationInterpolationDelay", int.class);

    private final Display.BlockDisplay entity;
    private final AttackZone zone;

    private AttackZoneDisplay(Display.BlockDisplay entity, AttackZone zone) {
        this.entity = entity;
        this.zone = zone;
    }

    public static AttackZoneDisplay spawn(ServerLevel world, Vec3 origin, float yawDegrees, AttackZone zone) {
        Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(world);
        if (display == null) {
            throw new IllegalStateException("Failed to create block display");
        }

        display.setPos(origin.x, origin.y + 0.02, origin.z);
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);

        invoke(SET_BLOCK_STATE, display, prepareState(zone));
        invoke(SET_TRANSFORMATION, display, computeTransformation(yawDegrees, zone));
        invoke(SET_BRIGHTNESS_OVERRIDE, display, BRIGHTNESS_DIM);
        invoke(SET_TRANSFORM_INTERPOLATION_DURATION, display, 0);
        invoke(SET_TRANSFORM_INTERPOLATION_DELAY, display, 0);

        world.addFreshEntity(display);
        return new AttackZoneDisplay(display, zone);
    }

    public void drawDimRed() {
        // Persistent display; no redraw required.
    }

    public void setBrightRed() {
        invoke(SET_BLOCK_STATE, entity, invokeState(zone));
        invoke(SET_BRIGHTNESS_OVERRIDE, entity, BRIGHTNESS_FLASH);
    }

    public void remove() {
        entity.discard();
    }

    private static BlockState prepareState(AttackZone zone) {
        return blockFor(zone).defaultBlockState().setValue(ModBlocks.PHASE, false);
    }

    private static BlockState invokeState(AttackZone zone) {
        return blockFor(zone).defaultBlockState().setValue(ModBlocks.PHASE, true);
    }

    private static Block blockFor(AttackZone zone) {
        return switch (zone) {
            case AttackZone.Rectangle r -> ModBlocks.ATTACK_ZONE_RECT;
            case AttackZone.Cone c -> ModBlocks.ATTACK_ZONE_CONE;
            case AttackZone.Circle c -> ModBlocks.ATTACK_ZONE_CIRCLE;
            case AttackZone.CircleTarget ct -> ModBlocks.ATTACK_ZONE_CIRCLE;
        };
    }

    private static Transformation computeTransformation(float yawDegrees, AttackZone zone) {
        Quaternionf noRot = new Quaternionf();
        Quaternionf yawRot = new Quaternionf().rotationY((float) Math.toRadians(-yawDegrees));

        return switch (zone) {
            case AttackZone.Rectangle r -> new Transformation(
                    new Vector3f(-r.width() * 0.5F, 0.0F, r.offsetForward()),
                    yawRot,
                    new Vector3f(r.width(), THICKNESS, r.length()),
                    noRot
            );
            case AttackZone.Cone c -> {
                float approxWidth = (float) (2.0 * c.maxDistance() * Math.sin(Math.toRadians(c.angleDegrees() * 0.5)));
                yield new Transformation(
                        new Vector3f(-approxWidth * 0.5F, 0.0F, 0.0F),
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
}
