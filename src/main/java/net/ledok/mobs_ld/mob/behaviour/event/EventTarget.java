package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;

/**
 * Whom a beat looks or shoots at, read live every time it is used: {@code aim} (the ability's aim), {@code current}
 * (the mob's main target now) or {@code slot:<name>} (whoever that target slot holds now).
 */
public record EventTarget(Kind kind, Optional<String> slot) {
    public static final EventTarget AIM = new EventTarget(Kind.AIM, Optional.empty());
    private static final String SLOT_PREFIX = "slot:";

    public static final Codec<EventTarget> CODEC = Codec.STRING.comapFlatMap(EventTarget::parse, EventTarget::serialized);

    private static DataResult<EventTarget> parse(String text) {
        if (text.startsWith(SLOT_PREFIX) && text.length() > SLOT_PREFIX.length()) {
            return DataResult.success(new EventTarget(Kind.SLOT, Optional.of(text.substring(SLOT_PREFIX.length()))));
        }
        return switch (text) {
            case "aim" -> DataResult.success(AIM);
            case "current" -> DataResult.success(new EventTarget(Kind.CURRENT, Optional.empty()));
            default -> DataResult.error(() -> "Unknown target '" + text + "' (expected aim, current or slot:<name>)");
        };
    }

    public String serialized() {
        return switch (kind) {
            case AIM -> "aim";
            case CURRENT -> "current";
            case SLOT -> SLOT_PREFIX + slot.orElse("");
        };
    }

    /** The slot it reads, for validation. */
    public List<String> slots() {
        return slot.map(List::of).orElse(List.of());
    }

    /** The entity it points at now; null if there is none (or it died). */
    public LivingEntity resolve(AbilityRun run) {
        LivingEntity entity = switch (kind) {
            case AIM -> run.aim();
            case CURRENT -> run.mob().getTarget();
            case SLOT -> run.combat().slots().target(slot.orElse(""));
        };
        return entity != null && entity.isAlive() ? entity : null;
    }

    public enum Kind {
        AIM,
        CURRENT,
        SLOT
    }
}
