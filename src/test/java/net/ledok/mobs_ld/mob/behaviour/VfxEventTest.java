package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.ledok.mobs_ld.mob.behaviour.event.AbilityEvent;
import net.ledok.mobs_ld.mob.behaviour.event.DashEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitEvent;
import net.ledok.mobs_ld.mob.behaviour.event.VfxEvent;
import net.ledok.mobs_ld.network.SpellVfxCancelPayload;
import net.ledok.mobs_ld.network.SpellVfxKind;
import net.ledok.mobs_ld.network.SpellVfxPayload;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@code vfx} ability event codec, validation rules, silent early locking on {@code hit} / {@code dash},
 * S2C binary packet round-trips, and zero regressions on existing shipped mobs.
 */
class VfxEventTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static String error(String json) {
        return MobBehaviour.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error()
                .map(DataResult.Error::message).orElse("");
    }

    @Test
    void vfxEventParsesAllKindsAndValidatesBounds() {
        String base = "{\"abilities\": {\"a\": {\"duration\": 3.0, \"events\": [%s]}}}";

        for (SpellVfxKind kind : SpellVfxKind.values()) {
            String json = base.formatted("{\"type\": \"vfx\", \"at\": 0.5, \"kind\": \"" + kind.getSerializedName()
                    + "\", \"origin\": \"locked\", \"windup\": 0.4, \"duration\": 1.2, \"color\": \"#FF5A00\", "
                    + "\"secondary_color\": \"#FFF8CC\", \"radius\": 4.5, \"length\": 12.0, \"width\": 2.2, "
                    + "\"height\": 3.5, \"angle\": 110, \"count\": 6, \"tilt\": 45, \"reverse\": true, "
                    + "\"particles\": false, \"offset\": 1.5, \"side\": -0.5, \"up\": 2.0, \"rotate\": 15}");
            MobBehaviour behaviour = MobBehaviour.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
            VfxEvent vfx = (VfxEvent) behaviour.abilities().get("a").events().get(0);
            assertEquals(kind, vfx.kind());
            assertEquals(VfxEvent.VfxOrigin.LOCKED, vfx.origin());
            assertEquals(0.5, vfx.begin(), 1.0e-9);
            assertEquals(0.9, vfx.end(), 1.0e-9);
            assertEquals(0xFF5A00, vfx.colors().primary());
            assertEquals(0xFFF8CC, vfx.colors().secondary());
            assertEquals(Optional.of(4.5), vfx.dimensions().radius());
            assertEquals(Optional.of(12.0), vfx.dimensions().length());
            assertEquals(Optional.of(2.2), vfx.dimensions().width());
            assertEquals(Optional.of(3.5), vfx.dimensions().height());
            assertEquals(Optional.of(110.0), vfx.dimensions().angle());
            assertEquals(Optional.of(6), vfx.dimensions().count());
            assertEquals(45.0, vfx.dimensions().tilt(), 1.0e-9);
            assertTrue(vfx.dimensions().reverse());
            assertFalse(vfx.dimensions().particles());
            assertEquals(1.5, vfx.placement().offset(), 1.0e-9);
            assertEquals(-0.5, vfx.placement().side(), 1.0e-9);
            assertEquals(2.0, vfx.placement().up(), 1.0e-9);
            assertEquals(Optional.of(15.0), vfx.placement().rotate());
        }

        // Defaults: windup 0 -> instant begin() == end() == at.
        MobBehaviour minimal = MobBehaviour.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                base.formatted("{\"type\": \"vfx\", \"at\": 1.2, \"kind\": \"eye_charge\", \"duration\": 1.0}")))
                .getOrThrow();
        VfxEvent instant = (VfxEvent) minimal.abilities().get("a").events().get(0);
        assertEquals(VfxEvent.VfxOrigin.SELF, instant.origin());
        assertEquals(1.2, instant.begin(), 1.0e-9);
        assertEquals(1.2, instant.end(), 1.0e-9);
        assertTrue(instant.dimensions().particles());
        assertFalse(instant.dimensions().reverse());

        // Invalid cases must fail decoding cleanly.
        assertFalse(error(base.formatted("{\"type\": \"vfx\", \"at\": -0.1, \"kind\": \"slash_arc\", \"duration\": 1.0}")).isEmpty());
        assertFalse(error(base.formatted("{\"type\": \"vfx\", \"at\": 0.5, \"kind\": \"slash_arc\", \"windup\": 1.0, \"duration\": 1.0}")).isEmpty());
        assertFalse(error(base.formatted("{\"type\": \"vfx\", \"at\": 0.5, \"kind\": \"slash_arc\", \"windup\": 1.2, \"duration\": 0.8}")).isEmpty());
        assertFalse(error(base.formatted("{\"type\": \"vfx\", \"at\": 0.5, \"kind\": \"unknown_spell\", \"duration\": 1.0}")).isEmpty());
        assertFalse(error(base.formatted("{\"type\": \"vfx\", \"at\": 0.5, \"kind\": \"slash_arc\", \"duration\": 1.0, \"color\": \"nope\"}")).isEmpty());
    }

    @Test
    void hiddenTelegraphLocksEarlyOnlyWhenFromIsSet() {
        String json = "{\"abilities\": {\"a\": {\"duration\": 3.0, \"events\": ["
                + "{\"type\": \"hit\", \"at\": 1.5, \"shape\": {\"type\": \"circle\", \"radius\": 3.0}, \"telegraph\": {\"show\": false}},"
                + "{\"type\": \"hit\", \"at\": 1.5, \"shape\": {\"type\": \"circle\", \"radius\": 3.0}, \"telegraph\": {\"show\": false, \"from\": 0.7}},"
                + "{\"type\": \"dash\", \"at\": 1.2, \"until\": 1.6, \"max_distance\": 10.0, \"telegraph\": {\"show\": false}},"
                + "{\"type\": \"dash\", \"at\": 1.2, \"until\": 1.6, \"max_distance\": 10.0, \"telegraph\": {\"show\": false, \"from\": 0.42}}"
                + "]}}}";
        MobBehaviour behaviour = MobBehaviour.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        List<AbilityEvent> events = behaviour.abilities().get("a").events();

        HitEvent plainHiddenHit = (HitEvent) events.get(0);
        HitEvent silentLockHit = (HitEvent) events.get(1);
        DashEvent plainHiddenDash = (DashEvent) events.get(2);
        DashEvent silentLockDash = (DashEvent) events.get(3);

        assertEquals(1.5, plainHiddenHit.begin(), 1.0e-9, "show:false without from stays identical to before");
        assertEquals(0.7, silentLockHit.begin(), 1.0e-9, "show:false with from locks silently at from");
        assertEquals(1.2, plainHiddenDash.begin(), 1.0e-9, "dash show:false without from stays identical to before");
        assertEquals(0.42, silentLockDash.begin(), 1.0e-9, "dash show:false with from locks silently at from");
    }

    @Test
    void spellVfxPacketsRoundTripOverNetworkBuffer() {
        SpellVfxPayload original = new SpellVfxPayload(
                42, 77, SpellVfxKind.LANCE_LUNGE, 12.5, 64.0, -33.25, 137.5F,
                0xFF5A00, 0xFFF6C4, 0.36F, 1.05F, 2.2F, 13.0F, 2.2F, 2.4F, 120.0F, -15.0F,
                (byte) 6, SpellVfxPayload.packFlags(true, false));

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        SpellVfxPayload.STREAM_CODEC.encode(buf, original);
        SpellVfxPayload decoded = SpellVfxPayload.STREAM_CODEC.decode(buf);
        assertEquals(original, decoded);
        assertTrue(decoded.reverse());
        assertFalse(decoded.particles());
        assertEquals(6, decoded.countUnsigned());

        SpellVfxCancelPayload cancel = new SpellVfxCancelPayload(42);
        RegistryFriendlyByteBuf cancelBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        SpellVfxCancelPayload.STREAM_CODEC.encode(cancelBuf, cancel);
        assertEquals(cancel, SpellVfxCancelPayload.STREAM_CODEC.decode(cancelBuf));
    }

    @Test
    void existingShippedMobsStayUnaffectedByVfx() throws Exception {
        for (String path : List.of(
                "/data/mobs_ld/mob_behaviour/gorefist.json",
                "/data/mobs_ld/mob_behaviour/rot_wart.json",
                "/data/minecraft/mob_behaviour/wither.json",
                "/data/minecraft/mob_behaviour/warden.json")) {
            InputStream stream = VfxEventTest.class.getResourceAsStream(path);
            assertNotNull(stream, "Missing " + path);
            MobBehaviour behaviour;
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                behaviour = MobBehaviour.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
            }
            for (MobBehaviour.Ability ability : behaviour.abilities().values()) {
                assertTrue(ability.events().stream().noneMatch(VfxEvent.class::isInstance),
                        path + " should remain untouched by vfx");
            }
        }
    }
}
