package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.ledok.mobs_ld.mob.behaviour.event.HoverEvent;
import net.ledok.mobs_ld.mob.behaviour.event.ProjectileEvent;
import net.ledok.mobs_ld.mob.behaviour.event.ShootEvent;
import net.ledok.mobs_ld.mob.behaviour.event.TelegraphEvent;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 1.7.7 ability fields (only_tagged, hover home, telegraph, projectile directions, shoot) and their defaults. */
class LineVolleyFieldsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static MobBehaviour.Ability ability(String json) {
        return MobBehaviour.Ability.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void lineVolleyParses() {
        MobBehaviour.Ability lines = ability("""
                { "duration": 6, "only_tagged": [ "arenas_ld_run_mob" ], "events": [
                  { "type": "hover", "from": 0, "until": 3, "height": 0.5, "home": true },
                  { "type": "telegraph", "at": 4, "rotate": 0, "shape": { "type": "rays", "count": 24, "length": 24, "width": 1 } },
                  { "type": "projectile", "at": 4, "skull": "line", "waves": 3, "directions": { "count": 24 } } ] }""");
        assertTrue(lines.allowsTags(Set.of("arenas_ld_run_mob")));
        assertFalse(lines.allowsTags(Set.of()));
        assertTrue(assertInstanceOf(HoverEvent.class, lines.events().get(0)).home());
        TelegraphEvent drawn = assertInstanceOf(TelegraphEvent.class, lines.events().get(1));
        assertEquals(Optional.of(0.0), drawn.rotate());
        ProjectileEvent.Directions directions = assertInstanceOf(ProjectileEvent.class, lines.events().get(2))
                .directions().orElseThrow();
        assertEquals(new ProjectileEvent.Directions(24, 0.0, 1.0, 1.5, 0.0), directions);
    }

    @Test
    void clockwiseSequence() {
        MobBehaviour.Ability lines = ability("""
                { "duration": 9, "events": [
                  { "type": "telegraph", "at": 4, "rotate": 0, "shape": { "type": "rectangle", "width": 1, "length": 26 },
                    "repeat": { "count": 36, "rotate": 10, "step": 0.1 } },
                  { "type": "projectile", "at": 4, "skull": "line", "waves": 3, "interval": 0.3,
                    "directions": { "count": 36, "step": 0.1 } } ] }""");
        TelegraphEvent drawn = assertInstanceOf(TelegraphEvent.class, lines.events().get(0));
        assertEquals(new TelegraphEvent.Repeat(36, 10.0, 0.1), drawn.repeat().orElseThrow());
        assertEquals(4.0 + 35 * 0.1, drawn.end(), 1.0e-9);
        ProjectileEvent volley = assertInstanceOf(ProjectileEvent.class, lines.events().get(1));
        // The last line's last wave: 35 steps of 0.1 s plus 2 intervals of 0.3 s.
        assertEquals(4.0 + 3.5 + 0.6, volley.end(), 1.0e-9);
    }

    @Test
    void oldFilesKeepTheirDefaults() {
        MobBehaviour.Ability plain = ability("""
                { "duration": 2, "events": [ { "type": "hover", "from": 0, "until": 1, "height": 2 },
                  { "type": "projectile", "at": 1, "skull": "center" } ] }""");
        assertEquals(List.of(), plain.onlyTagged());
        assertTrue(plain.allowsTags(Set.of()));
        assertFalse(assertInstanceOf(HoverEvent.class, plain.events().get(0)).home());
        assertTrue(assertInstanceOf(ProjectileEvent.class, plain.events().get(1)).directions().isEmpty());
    }

    @Test
    void shootDefaults() {
        ShootEvent shot = assertInstanceOf(ShootEvent.class, ability("""
                { "duration": 2, "events": [ { "type": "shoot", "at": 1, "projectile": "minecraft:arrow" } ] }""")
                .events().get(0));
        assertEquals(1.6, shot.speed());
        assertEquals(1, shot.shots());
        assertFalse(shot.gravity());
        assertTrue(shot.telegraph().show() && shot.telegraph().arrow());
    }
}
