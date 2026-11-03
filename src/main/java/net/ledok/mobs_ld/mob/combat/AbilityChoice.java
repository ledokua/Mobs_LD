package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The rules {@link MobCombat} picks and times abilities by that need no world: shared cooldown groups
 * ({@code cooldown_group}), no back-to-back repeats ({@code no_repeat}) and clip-timed abilities that ignore the
 * phase's attack speed ({@code fixed_speed}). Times are game ticks.
 */
public final class AbilityChoice {
    private AbilityChoice() {
    }

    /** The clock speed an ability runs at in a phase whose {@code attack_speed} is {@code phaseSpeed}. */
    public static double speed(MobBehaviour.Ability ability, double phaseSpeed) {
        return ability.fixedSpeed() ? 1.0 : phaseSpeed;
    }

    /**
     * Whether ability {@code name} is off cooldown at {@code now}: its own ({@code readyAt}, which also holds its
     * initial cooldown and a phase's {@code ready_in}) and its group's ({@code groupReadyAt}). An ability in
     * {@code readyInWins} (a phase's {@code ready_in} named it, and it has not been used since) ignores its group's
     * cooldown: the phase's time wins.
     */
    public static boolean ready(String name, MobBehaviour.Ability ability, long now, Map<String, Long> readyAt,
                                Map<String, Long> groupReadyAt, Set<String> readyInWins) {
        if (now < readyAt.getOrDefault(name, 0L)) {
            return false;
        }
        if (readyInWins.contains(name)) {
            return true;
        }
        return ability.cooldownGroup().map(group -> now >= groupReadyAt.getOrDefault(group, 0L)).orElse(true);
    }

    /**
     * A phase's {@code ready_in} makes ability {@code name} usable at {@code readyTick}, whatever its group's cooldown
     * says, until it is next used.
     */
    public static void readyIn(String name, long readyTick, Map<String, Long> readyAt, Set<String> readyInWins) {
        readyAt.put(name, readyTick);
        readyInWins.add(name);
    }

    /**
     * Ability {@code name} was used: it is ready again at {@code readyTick}, and every ability of its group no sooner
     * (a group cooldown that runs longer already is kept). A {@code ready_in} it had is spent.
     */
    public static void used(String name, MobBehaviour.Ability ability, long readyTick, Map<String, Long> readyAt,
                            Map<String, Long> groupReadyAt, Set<String> readyInWins) {
        readyAt.put(name, readyTick);
        readyInWins.remove(name);
        ability.cooldownGroup().ifPresent(group -> groupReadyAt.merge(group, readyTick, Math::max));
    }

    /**
     * The candidates without a {@code no_repeat} ability that was also the last one started ({@code last}), unless
     * that would leave none: then they all stay.
     */
    public static <T> List<T> withoutRepeat(List<T> options, Function<T, String> name,
                                            Function<T, MobBehaviour.Ability> ability, String last) {
        if (last == null || options.size() < 2) {
            return options;
        }
        List<T> kept = new ArrayList<>(options.size());
        for (T option : options) {
            if (!(ability.apply(option).noRepeat() && last.equals(name.apply(option)))) {
                kept.add(option);
            }
        }
        return kept.isEmpty() ? options : kept;
    }
}
