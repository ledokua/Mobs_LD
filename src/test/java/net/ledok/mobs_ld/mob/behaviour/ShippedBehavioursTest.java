package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.ledok.mobs_ld.mob.MobDeclaration;
import net.ledok.mobs_ld.mob.behaviour.event.ChainEvent;
import net.ledok.mobs_ld.mob.behaviour.event.ClipEvent;
import net.ledok.mobs_ld.mob.behaviour.event.DashEvent;
import net.ledok.mobs_ld.mob.behaviour.event.FxEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitSpec;
import net.ledok.mobs_ld.mob.behaviour.event.HoverEvent;
import net.ledok.mobs_ld.mob.behaviour.event.LeapEvent;
import net.ledok.mobs_ld.mob.behaviour.event.SandstoneSpikesEvent;
import net.ledok.mobs_ld.mob.behaviour.event.SummonEvent;
import net.ledok.mobs_ld.mob.behaviour.event.SummonerWardEvent;
import net.ledok.mobs_ld.mob.behaviour.event.Telegraph;
import net.ledok.mobs_ld.mob.behaviour.event.VulnerableEvent;
import net.ledok.mobs_ld.mob.combat.SlotPicker;
import net.ledok.mobs_ld.mob.combat.SummonStats;
import net.ledok.mobs_ld.mob.host.HostOptions;
import net.ledok.mobs_ld.mob.host.warden.WardenAdapter;
import net.ledok.mobs_ld.mob.host.warden.WardenHostOptions;
import net.ledok.mobs_ld.mob.host.wither.WitherAdapter;
import net.ledok.mobs_ld.mob.host.wither.WitherHostOptions;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The behaviour files shipped in the jar decode (and the Wither's per-phase skull cadence merges as intended), the
 * M3 references (slots, skulls, hit damage) are validated at load, and so are the M4 dash and chain fields and the M5
 * summon fields (which leave Gorefist's and Rot Wart's files meaning what they did), and the Warden's file with the
 * Warden rework's M1 fields (none of which the other files use).
 */
class ShippedBehavioursTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        WitherAdapter.register();
        WardenAdapter.register();
    }

    private static MobBehaviour load(String path) throws Exception {
        try (Reader reader = new InputStreamReader(ShippedBehavioursTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            DataResult<MobBehaviour> result = MobBehaviour.CODEC.parse(JsonOps.INSTANCE, json);
            return result.getOrThrow(message -> new AssertionError(path + ": " + message));
        }
    }

    @Test
    void ldMobsLoad() throws Exception {
        load("/data/mobs_ld/mob_behaviour/gorefist.json");
        load("/data/mobs_ld/mob_behaviour/rot_wart.json");
        load("/data/mobs_ld/mob_behaviour/midra.json");
        load("/data/mobs_ld/mob_behaviour/amber_brute.json");
        load("/data/mobs_ld/mob_behaviour/emberroot.json");
        load("/data/mobs_ld/mob_behaviour/nameless_king.json");
        load("/data/mobs_ld/mob_behaviour/frenzy_wisp.json");
        load("/data/mobs_ld/mob_behaviour/amber_mite.json");
        load("/data/mobs_ld/mob_behaviour/ember_cinderling.json");
        load("/data/mobs_ld/mob_behaviour/storm_cloudlet.json");
        load("/data/mobs_ld/mob_behaviour/amber_monolith.json");
        load("/data/mobs_ld/mob_behaviour/ember_pyre.json");
        load("/data/mobs_ld/mob_behaviour/storm_rod.json");
        load("/data/mobs_ld/mob_behaviour/kiln_sprout.json");
        load("/data/mobs_ld/mob_behaviour/cinder_runt.json");
    }

    @Test
    void kilnSproutsWardTheirSummonerDuringTheChannel() throws Exception {
        MobBehaviour emberroot = load("/data/mobs_ld/mob_behaviour/emberroot.json");
        String call = "call_kiln_sprouts";
        assertFalse(emberroot.phases().get(0).allows(call));
        assertTrue(emberroot.phases().get(1).allows(call));
        assertTrue(emberroot.phases().get(2).allows(call));
        MobBehaviour.Ability summon = emberroot.abilities().get(call);
        SummonEvent wave = summons(summon).getFirst();
        assertEquals(ResourceLocation.fromNamespaceAndPath("mobs_ld", "kiln_sprout"), wave.entity());
        assertEquals(List.of(ResourceLocation.fromNamespaceAndPath("mobs_ld", "cinder_runt")), wave.pool().alternatives());
        assertTrue(wave.bound() && summon.skipWhenFull());
        // Emberroot's cinderlings and pyres have their own limits; they must not fill the sprouts' room.
        assertTrue(wave.perType());
        assertFalse(wave.loot() || wave.xp());
        assertEquals(SummonEvent.Count.of(2), wave.count());
        assertEquals(SummonEvent.MaxAlive.of(6), wave.maxAlive());
        assertEquals(new MobBehaviour.WardDamage(3, 1.5), emberroot.boss().orElseThrow().wardDamage().orElseThrow());

        MobBehaviour sprout = load("/data/mobs_ld/mob_behaviour/kiln_sprout.json");
        assertEquals(MobBehaviour.TargetRule.Kind.SUMMONER, sprout.movement().follow().kind());
        MobBehaviour.Ability channel = sprout.abilities().get("channel");
        assertEquals(sprout.movement().follow(), channel.target());
        SummonerWardEvent ward = channel.events().stream().filter(SummonerWardEvent.class::isInstance)
                .map(SummonerWardEvent.class::cast).findFirst().orElseThrow();
        assertEquals(15.0, channel.range().max());
        assertEquals(new SummonerWardEvent(0.6, java.util.Optional.empty(), 15.0, 0.5,
                java.util.Optional.of("channel_release")), ward);
        assertTrue(channel.events().contains(new ClipEvent(0.8, "channel_loop")));
        assertEquals(0.6, sprout.abilities().get(ward.onDisconnect().orElseThrow()).duration());
    }

    @Test
    void cinderRuntUsesThreeDistinctDamageAttacks() throws Exception {
        MobBehaviour runt = load("/data/mobs_ld/mob_behaviour/cinder_runt.json");
        assertEquals(1.0, HitSpec.damageScale(7.0, runt));
        MobBehaviour.Ability charge = runt.abilities().get("roll");
        var roll = charge.events().stream()
                .filter(DashEvent.class::isInstance).map(DashEvent.class::cast).findFirst().orElseThrow();
        assertTrue(roll.hit().isPresent());
        assertEquals("roll_start", charge.clipOr("roll"));
        assertTrue(charge.events().contains(new ClipEvent(roll.at(), "roll_loop")));
        assertTrue(roll.until().isEmpty());
        String recovery = roll.onStop().orElseThrow();
        assertEquals("roll_end", runt.abilities().get(recovery).clipOr(recovery));
        var pounce = runt.abilities().get("pounce").events().stream()
                .filter(LeapEvent.class::isInstance).map(LeapEvent.class::cast).findFirst().orElseThrow();
        assertEquals(0.5, pounce.at());
        assertEquals(1.1, pounce.end());
        assertEquals(new Shape.Cone(2.7, 110), hits(runt.abilities().get("slash")).getFirst().shape());
    }

    @Test
    void colossusSpikesFollowItsSingleStairHeightSlam() throws Exception {
        MobBehaviour colossus = load("/data/mobs_ld/mob_behaviour/sand_golem_colossus.json");
        MobBehaviour.Ability slam = colossus.abilities().get("ground_slam");
        List<HitEvent> hits = hits(slam);
        assertEquals(1, hits.size());
        HitEvent hit = hits.getFirst();
        assertEquals(new Shape.Rectangle(3, 15, 0), hit.shape());
        assertEquals(3.5, hit.height().orElseThrow());
        assertEquals(1.2, hit.at());
        assertEquals(2.7, slam.duration());
        assertTrue(slam.noRepeat());
        assertTrue(colossus.abilities().get("horizontal_slam").noRepeat());
        assertTrue(slam.events().stream().anyMatch(event -> event instanceof SandstoneSpikesEvent spikes
                && spikes.matches(hit)));
    }

    @Test
    void colossusCallsTwoHusksPlusOnePerFighterUpToTen() throws Exception {
        MobBehaviour colossus = load("/data/mobs_ld/mob_behaviour/sand_golem_colossus.json");
        MobBehaviour.Ability call = colossus.abilities().get("summon_husks");
        List<SummonEvent> waves = summons(call);
        assertEquals(1, waves.size());
        SummonEvent wave = waves.getFirst();
        assertEquals(List.of("ground_slam", "horizontal_slam"), call.afterAbilities());
        assertEquals(ResourceLocation.withDefaultNamespace("husk"), wave.entity());
        assertEquals(1.1, wave.at());
        assertTrue(call.skipWhenFull() && wave.bound());
        assertFalse(wave.loot() || wave.xp());
        for (int fighters : List.of(0, 1, 2, 4, 8, 16)) {
            int expected = Math.min(10, 2 + fighters);
            assertEquals(expected, wave.count().roll(fighters, bound -> 0));
            assertEquals(expected, wave.maxAlive().at(fighters));
        }
    }

    @Test
    void colossusHusksScaleWithTheSummoner() throws Exception {
        MobBehaviour colossus = load("/data/mobs_ld/mob_behaviour/sand_golem_colossus.json");
        SummonEvent.Scale scale = summons(colossus.abilities().get("summon_husks")).getFirst().scale().orElseThrow();
        SummonEvent.StatScale health = scale.maxHealth().orElseThrow();
        SummonEvent.StatScale attack = scale.attackDamage().orElseThrow();
        double attackBase = colossus.declaredBase(ResourceLocation.withDefaultNamespace("generic.attack_damage")).orElseThrow();

        assertEquals(20.0, SummonStats.target(health, 20.0, 200.0, 200.0));
        assertEquals(40.0, SummonStats.target(health, 20.0, 400.0, 200.0));
        assertEquals(3.0, SummonStats.target(attack, 3.0, 8.0, attackBase));
        assertEquals(6.0, SummonStats.target(attack, 3.0, 16.0, attackBase));
        assertEquals(6.0, colossus.declaredBase(ResourceLocation.withDefaultNamespace("generic.armor")).orElseThrow());
        assertTrue(scale.armor().orElseThrow().summonerBonus());
        assertTrue(scale.copiesDamageDealt());
    }

    @Test
    void onlyColossusOptsOutOfCollisionPushes() throws Exception {
        for (String name : List.of("sand_golem_colossus", "gorefist", "rot_wart")) {
            String path = "/mobs_ld/entities/" + name + ".json";
            try (Reader reader = new InputStreamReader(ShippedBehavioursTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
                MobDeclaration declaration = MobDeclaration.codec(ResourceLocation.fromNamespaceAndPath("mobs_ld", name))
                        .parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                        .getOrThrow(error -> new AssertionError(path + ": " + error));
                assertEquals(!name.equals("sand_golem_colossus"), declaration.pushable(), name);
            }
        }
    }

    /** M5's summon and cooldown fields are all optional: Gorefist's and Rot Wart's files mean exactly what they did. */
    @Test
    void ldMobsKeepTheirSummonsAndCooldowns() throws Exception {
        MobBehaviour gorefist = load("/data/mobs_ld/mob_behaviour/gorefist.json");
        MobBehaviour.Ability summon = gorefist.abilities().get("summon_rot_warts");
        assertEquals(Seconds.of(25.0), summon.cooldown());
        assertEquals(Seconds.of(12.0), summon.initialCooldown());
        assertEquals(Seconds.of(18.0), gorefist.phases().get(1).cooldowns().get("summon_rot_warts"));
        assertEquals(5.0, gorefist.phases().get(1).readyIn().get("summon_rot_warts"));
        List<SummonEvent> summons = new ArrayList<>(summons(summon));
        summons.addAll(summons(gorefist.phases().get(1).transition().orElseThrow()));
        assertEquals(2, summons.size());
        for (SummonEvent event : summons) {
            assertEquals(ResourceLocation.fromNamespaceAndPath("mobs_ld", "rot_wart"), event.entity());
            assertEquals(SummonEvent.Count.of(2), event.count());
            assertEquals(SummonEvent.MaxAlive.of(6), event.maxAlive());
            assertEquals(0.75, event.forward());
            assertEquals(2.6, event.up());
            assertEquals(1.5, event.spread());
            assertTrue(event.telegraph().isEmpty() && event.placement().isEmpty() && event.scale().isEmpty());
            assertTrue(event.loot() && event.xp());
            assertFalse(event.bound());
            assertEquals(SummonEvent.AddTarget.AIM, event.target());
            assertTrue(event.minionLink().isEmpty(), "Gorefist's rot warts do not hurt him");
            assertEquals(event.at(), event.begin(), "untelegraphed: fires at its time, as before");
            assertEquals(event.at(), event.end());
        }
        for (MobBehaviour behaviour : List.of(gorefist, load("/data/mobs_ld/mob_behaviour/rot_wart.json"))) {
            for (MobBehaviour.Ability ability : behaviour.abilities().values()) {
                assertTrue(ability.cooldown().fixed() && ability.initialCooldown().fixed());
                assertFalse(ability.skipWhenFull());
            }
        }
        assertEquals(Seconds.of(4.0), gorefist.abilities().values().iterator().next().cooldown());
    }

    private static List<SummonEvent> summons(MobBehaviour.Ability ability) {
        return ability.events().stream().filter(SummonEvent.class::isInstance).map(SummonEvent.class::cast).toList();
    }

    /** The Warden rework's instance filter and reference stats are absent from every earlier file: they host as before. */
    @Test
    void earlierFilesHostEveryMobOfTheirType() throws Exception {
        for (String path : List.of("/data/mobs_ld/mob_behaviour/gorefist.json", "/data/mobs_ld/mob_behaviour/rot_wart.json",
                "/data/minecraft/mob_behaviour/wither.json")) {
            MobBehaviour behaviour = load(path);
            assertTrue(behaviour.host().onlyTagged().isEmpty(), path);
            assertTrue(behaviour.host().appliesTo(java.util.Set.of()), path);
            assertTrue(behaviour.referenceAttributes().isEmpty(), path);
        }
        MobBehaviour wither = load("/data/minecraft/mob_behaviour/wither.json");
        assertEquals(java.util.Optional.of(2.0), wither.declaredBase(ResourceLocation.withDefaultNamespace("generic.attack_damage")));
    }

    @Test
    void wardenLoads() throws Exception {
        MobBehaviour warden = load("/data/minecraft/mob_behaviour/warden.json");
        assertEquals(List.of("arenas_ld_run_mob"), warden.host().onlyTagged());
        assertTrue(warden.host().appliesTo(java.util.Set.of("arenas_ld_run_mob", "other")));
        assertFalse(warden.host().appliesTo(java.util.Set.of()), "a world Warden stays vanilla");
        assertTrue(warden.baseAttributes().isEmpty(), "world Wardens keep vanilla stats");
        ResourceLocation attackDamage = ResourceLocation.withDefaultNamespace("generic.attack_damage");
        assertEquals(java.util.Optional.of(30.0), warden.declaredBase(attackDamage));
        assertEquals(1.0, net.ledok.mobs_ld.mob.behaviour.event.HitSpec.damageScale(30.0, warden), 1.0e-9, "R = 1 at vanilla stats");
        WardenHostOptions options = (WardenHostOptions) warden.hostOptions(0).orElseThrow();
        assertEquals(WardenHostOptions.DarknessPulse.OFF, options.darknessPulse());
        assertFalse(options.disableShields());
        assertEquals(48.0, options.followRange());
        assertEquals(MobBehaviour.Movement.Style.VANILLA, warden.movement().style());
        MobBehaviour.Boss boss = warden.boss().orElseThrow();
        assertEquals(MobBehaviour.EngageOn.PLAYERS, boss.engageOn());
        assertTrue(boss.bar().isEmpty(), "no Mobs_LD bar: Arenas shows the boss HP");
        MobBehaviour.Ability blow = warden.abilities().get("crushing_blow");
        assertEquals(Seconds.of(6.0), blow.cooldown());
        net.ledok.mobs_ld.mob.behaviour.event.HitEvent hit = blow.events().stream()
                .filter(net.ledok.mobs_ld.mob.behaviour.event.HitEvent.class::isInstance)
                .map(net.ledok.mobs_ld.mob.behaviour.event.HitEvent.class::cast).findFirst().orElseThrow();
        assertEquals(12.0, hit.hit().amount().orElseThrow());
        assertEquals(1.0, hit.at() - hit.telegraph().from().orElseThrow(), 1.0e-9, "telegraphed 1.0 s ahead");
        assertEquals(java.util.Optional.of(20), options.heartbeat());
    }

    /** M2's P1 kit: the Circle's two waves, the Lance, the Pulse; clip cues, widening lanes, sonic damage. */
    @Test
    void wardenFirstKitLoads() throws Exception {
        MobBehaviour warden = load("/data/minecraft/mob_behaviour/warden.json");
        assertTrue(warden.abilities().keySet().containsAll(java.util.Set.of("crushing_blow", "sonic_circle",
                "resonant_pulse", "sonic_lance")));
        ResourceLocation sonic = ResourceLocation.fromNamespaceAndPath("mobs_ld", "warden_sonic");
        MobBehaviour.Ability blow = warden.abilities().get("crushing_blow");
        assertFalse(blow.fixedSpeed(), "a physical swing follows the phase's attack speed");
        assertTrue(blow.events().stream().anyMatch(event -> event instanceof ClipEvent clip && clip.clip().equals("attack")
                && clip.at() == 1.0));
        assertEquals(ResourceLocation.withDefaultNamespace("mob_attack"), hits(blow).get(0).hit().damageType().orElseThrow());

        MobBehaviour.Ability circle = warden.abilities().get("sonic_circle");
        assertTrue(circle.fixedSpeed() && circle.noRepeat());
        assertEquals("sonic_boom", circle.clip());
        List<HitEvent> waves = hits(circle);
        assertEquals(2, waves.size());
        assertEquals(1.7, waves.get(0).at(), 1.0e-9);
        assertEquals(3.4, waves.get(1).at(), 1.0e-9);
        assertTrue(waves.get(0).rotate().isEmpty());
        assertEquals(java.util.Optional.of(22.5), waves.get(1).rotate());
        assertTrue(waves.get(1).turnBody());
        for (HitEvent wave : waves) {
            Shape.Rays rays = (Shape.Rays) wave.shape();
            assertEquals(new Shape.Rays(8, 26.0, 2.5, 0.3), rays);
            assertEquals(sonic, wave.hit().damageType().orElseThrow());
            assertEquals(10.0, wave.hit().amount().orElseThrow());
        }
        assertEquals(0.3, waves.get(0).begin(), 1.0e-9);
        assertEquals(1.7, waves.get(1).begin(), 1.0e-9, "wave 2's lanes appear as wave 1 fires");
        assertTrue(circle.events().stream().anyMatch(event -> event instanceof ClipEvent clip
                && clip.clip().equals("sonic_boom") && clip.at() == 1.7), "the clip restarts as wave 1 fires");
        assertEquals(2, circle.events().stream().filter(event -> event instanceof FxEvent fx && fx.particles()
                .filter(p -> p.at() == FxEvent.Where.ALONG).isPresent()).count());
        VulnerableEvent open = circle.events().stream().filter(VulnerableEvent.class::isInstance)
                .map(VulnerableEvent.class::cast).findFirst().orElseThrow();
        assertEquals(new VulnerableEvent(3.4, 5.0, 1.25), open);

        MobBehaviour.Ability lance = warden.abilities().get("sonic_lance");
        assertEquals(java.util.Optional.of("lance"), lance.cooldownGroup());
        assertTrue(lance.fixedSpeed() && lance.noRepeat());
        assertEquals(MobBehaviour.TargetRule.FARTHEST, lance.target());
        assertEquals(0.9, lance.trackUntil().orElseThrow(), 1.0e-9);
        HitEvent strip = hits(lance).get(0);
        assertTrue(strip.telegraph().arrow());
        assertEquals(0.9, strip.begin(), 1.0e-9, "the strip locks at 0.9");
        assertEquals(sonic, strip.hit().damageType().orElseThrow());

        MobBehaviour.Ability pulse = warden.abilities().get("resonant_pulse");
        assertTrue(pulse.fixedSpeed() && pulse.noRepeat());
        assertEquals(new Shape.Circle(6.0), hits(pulse).get(0).shape());
        assertEquals(0.5, hits(pulse).get(0).hit().knockUp());
    }

    private static List<HitEvent> hits(MobBehaviour.Ability ability) {
        return ability.events().stream().filter(HitEvent.class::isInstance).map(HitEvent.class::cast).toList();
    }

    /** M2's fields are all optional: the earlier files use none of them, so they play as before. */
    @Test
    void earlierFilesUseNoWardenM2Fields() throws Exception {
        for (String path : List.of("/data/mobs_ld/mob_behaviour/gorefist.json", "/data/mobs_ld/mob_behaviour/rot_wart.json",
                "/data/minecraft/mob_behaviour/wither.json")) {
            MobBehaviour behaviour = load(path);
            List<MobBehaviour.Ability> abilities = new ArrayList<>(behaviour.abilities().values());
            behaviour.phases().forEach(phase -> phase.transition().ifPresent(abilities::add));
            behaviour.intro().ifPresent(abilities::add);
            for (MobBehaviour.Ability ability : abilities) {
                assertFalse(ability.fixedSpeed(), path);
                assertFalse(ability.noRepeat(), path);
                assertTrue(ability.cooldownGroup().isEmpty(), path);
                for (var event : ability.events()) {
                    assertFalse(event instanceof ClipEvent || event instanceof VulnerableEvent, path);
                    if (event instanceof HitEvent hit) {
                        assertTrue(hit.rotate().isEmpty() && !hit.turnBody(), path);
                        assertFalse(hit.shape() instanceof Shape.Rays rays && rays.widen() != 0.0, path);
                    }
                    if (event instanceof FxEvent fx) {
                        assertTrue(fx.particles().map(p -> p.at() != FxEvent.Where.ALONG).orElse(true), path);
                    }
                }
            }
        }
    }

    /**
     * M3's fields are all optional: the earlier files (Gorefist, Rot Wart, the Wither) use none of them, so they play
     * as before.
     */
    @Test
    void earlierFilesUseNoWardenM3Fields() throws Exception {
        for (String path : List.of("/data/mobs_ld/mob_behaviour/gorefist.json", "/data/mobs_ld/mob_behaviour/rot_wart.json",
                "/data/minecraft/mob_behaviour/wither.json")) {
            MobBehaviour behaviour = load(path);
            assertTrue(behaviour.movement().follow().kind() != MobBehaviour.TargetRule.Kind.TOP_DAMAGE, path);
            List<MobBehaviour.Ability> abilities = new ArrayList<>(behaviour.abilities().values());
            behaviour.phases().forEach(phase -> phase.transition().ifPresent(abilities::add));
            behaviour.intro().ifPresent(abilities::add);
            behaviour.onDeath().ifPresent(abilities::add);
            for (MobBehaviour.Ability ability : abilities) {
                assertTrue(ability.minPlayers().isEmpty() && ability.maxPlayers().isEmpty() && ability.when().isEmpty(), path);
                assertFalse(ability.includeUnreachable(), path);
                assertTrue(ability.target().kind() != MobBehaviour.TargetRule.Kind.TOP_DAMAGE, path);
                for (var event : ability.events()) {
                    assertFalse(event instanceof net.ledok.mobs_ld.mob.behaviour.event.ListenEvent, path);
                    if (event instanceof HitEvent hit) {
                        assertTrue(hit.origin() != net.ledok.mobs_ld.mob.behaviour.event.Origin.EACH_FIGHTER, path);
                        assertTrue(hit.lead().isEmpty() && !hit.once(), path);
                        assertTrue(hit.hit().stacks().isEmpty(), path);
                        assertFalse(hit.shape() instanceof Shape.Ring, path);
                    }
                    if (event instanceof DashEvent dash) {
                        assertTrue(dash.hit().flatMap(net.ledok.mobs_ld.mob.behaviour.event.HitSpec::stacks).isEmpty(), path);
                    }
                    if (event instanceof ChainEvent chain) {
                        assertFalse(chain.tauntWins(), path);
                    }
                    if (event instanceof net.ledok.mobs_ld.mob.behaviour.event.EffectEvent effect) {
                        assertEquals(net.ledok.mobs_ld.mob.behaviour.event.EffectEvent.On.AROUND, effect.on(), path);
                    }
                    if (event instanceof SummonEvent summon) {
                        assertTrue(summon.placement().map(p -> p.around() != SummonEvent.Placement.Around.HOME).orElse(true), path);
                    }
                }
            }
            for (MobBehaviour.Phase phase : behaviour.phases()) {
                assertTrue(phase.damageTaken().stream().allMatch(rule -> rule.whileAdds().isEmpty()), path);
            }
            behaviour.host().options().ifPresent(options -> options.skulls().values().forEach(skull ->
                    assertTrue(skull.hit().stacks().isEmpty())));
        }
    }

    /** M3's kit: the three phases and the abilities M3 makes possible (design 2.2), placeholder numbers. */
    @Test
    void wardenM3KitLoads() throws Exception {
        MobBehaviour warden = load("/data/minecraft/mob_behaviour/warden.json");
        assertEquals(3, warden.phases().size());
        assertEquals(List.of(1.0, 0.6, 0.25), warden.phases().stream().map(MobBehaviour.Phase::belowHealth).toList());
        // Every ability a phase lists exists (validated at load); Deep Charge is only chained.
        assertEquals(0, warden.abilities().get("deep_charge").weight());
        assertTrue(warden.phases().stream().noneMatch(phase -> phase.abilities().contains("deep_charge")));
        assertTrue(warden.phases().get(0).abilities().containsAll(List.of("crushing_blow", "rending_combo",
                "sculk_backlash", "sonic_circle", "resonant_pulse", "sonic_lance", "tremor_line", "hunting_sniff")));
        assertTrue(warden.phases().get(1).abilities().containsAll(List.of("sonic_volley", "deafening_rings",
                "echo_mines", "sonic_circle_open", "crushing_blow_branded", "rending_combo_branded")));
        assertTrue(warden.phases().get(2).abilities().containsAll(List.of("listening_silence", "sonic_circle_triple")));
        assertFalse(warden.phases().get(0).abilities().contains("crushing_blow_branded"), "Sculk Brand: P2 and P3");
        assertEquals(java.util.Map.of("listening_silence", 10.0), warden.phases().get(2).readyIn());
        assertEquals(Seconds.of(7.0), warden.phases().get(2).cooldowns().get("sonic_volley"));
        assertEquals(1.2, warden.phases().get(2).attackSpeed());
        assertTrue(warden.phases().get(1).transition().orElseThrow().invulnerable(), "Roar of the Deep");
        assertEquals(java.util.Optional.of(90), ((WardenHostOptions) warden.hostOptions(2).orElseThrow()).heartbeat());
        assertEquals(java.util.Optional.of(50), ((WardenHostOptions) warden.hostOptions(1).orElseThrow()).heartbeat());

        assertTrue(warden.abilities().get("sonic_lance").includeUnreachable());
        assertTrue(warden.abilities().get("sonic_volley").includeUnreachable());
        assertEquals(java.util.Optional.of("lance"), warden.abilities().get("sonic_volley").cooldownGroup());
        assertEquals(3, hits(warden.abilities().get("sonic_volley")).size());

        MobBehaviour.Ability backlash = warden.abilities().get("sculk_backlash");
        assertEquals(new MobBehaviour.Behind(2, 4.0, 4.5, 140.0, 3.0), backlash.behind().orElseThrow());
        assertEquals(java.util.Optional.of(180.0), hits(backlash).get(0).rotate(), "the rear cone");

        MobBehaviour.Ability sniff = warden.abilities().get("hunting_sniff");
        assertEquals(MobBehaviour.TargetRule.TOP_DAMAGE, sniff.target());
        assertTrue(sniff.events().stream().anyMatch(event -> event instanceof net.ledok.mobs_ld.mob.behaviour.event.EffectEvent effect
                && effect.on() == net.ledok.mobs_ld.mob.behaviour.event.EffectEvent.On.AIM));
        assertTrue(sniff.events().stream().anyMatch(event -> event instanceof ChainEvent chain
                && chain.to().equals("deep_charge") && chain.tauntWins()));
        DashEvent charge = dash(warden.abilities().get("deep_charge"));
        assertEquals(java.util.Optional.of(0.9), charge.speed());
        assertEquals(DashEvent.Push.SIDE, charge.push());

        List<HitEvent> rings = hits(warden.abilities().get("deafening_rings"));
        assertEquals(List.of(new Shape.Ring(6, 9), new Shape.Ring(12, 15), new Shape.Ring(18, 22)),
                rings.stream().map(HitEvent::shape).toList());

        List<HitEvent> mines = hits(warden.abilities().get("echo_mines"));
        assertEquals(3, mines.size());
        for (HitEvent mine : mines) {
            assertEquals(net.ledok.mobs_ld.mob.behaviour.event.Origin.EACH_FIGHTER, mine.origin());
            assertEquals(java.util.Optional.of(0.6), mine.lead());
            assertEquals(0.8, mine.at() - mine.begin(), 1.0e-9, "each drawn 0.8 s ahead");
        }

        List<HitEvent> tremor = hits(warden.abilities().get("tremor_line"));
        assertEquals(6, tremor.size());
        assertTrue(tremor.stream().allMatch(HitEvent::once), "once per victim along the line");
        assertEquals(MobBehaviour.TargetRule.slot("listener"), warden.abilities().get("tremor_line").target());

        for (String name : List.of("crushing_blow_branded", "rending_combo_branded")) {
            for (HitEvent hit : hits(warden.abilities().get(name))) {
                var brand = hit.hit().stacks().orElseThrow();
                assertEquals("sculk_brand", brand.id());
                assertEquals(3, brand.minPlayers());
                assertEquals(4, brand.max());
            }
        }
        assertTrue(hits(warden.abilities().get("crushing_blow")).get(0).hit().stacks().isEmpty());

        var listen = warden.abilities().get("listening_silence").events().stream()
                .filter(net.ledok.mobs_ld.mob.behaviour.event.ListenEvent.class::isInstance)
                .map(net.ledok.mobs_ld.mob.behaviour.event.ListenEvent.class::cast).findFirst().orElseThrow();
        assertEquals(1.0, listen.from());
        assertEquals(3.0, listen.until());
        assertEquals(4.2, listen.end(), 1.0e-9);
        assertEquals(java.util.Optional.of("sonic_lance"), listen.chainTo());
        assertEquals(13.0, listen.hit().amount().orElseThrow());
        // No broken references to what later milestones add (burrow M4, resonators M5).
        assertFalse(json("/data/minecraft/mob_behaviour/warden.json").toString().contains("sculk_resonator"));
        assertFalse(warden.abilities().containsKey("burrow_ambush"));
    }

    /** Every sound the Warden's file plays is a registered vanilla sound (no silent typos). */
    @Test
    void wardenSoundsExist() throws Exception {
        java.util.regex.Matcher ids = java.util.regex.Pattern.compile("\"id\": \"(minecraft:entity\\.[a-z_.]+)\"")
                .matcher(json("/data/minecraft/mob_behaviour/warden.json").toString().replace(":\"", ": \""));
        int found = 0;
        while (ids.find()) {
            found++;
            assertTrue(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(
                    ResourceLocation.parse(ids.group(1))), ids.group(1));
        }
        assertTrue(found > 10);
    }

    /** The sonic damage type and its tags ship in the jar (design 4.8). */
    @Test
    void wardenSonicDamageTypeShips() throws Exception {
        com.google.gson.JsonObject type = json("/data/mobs_ld/damage_type/warden_sonic.json").getAsJsonObject();
        assertEquals("sonic_boom", type.get("message_id").getAsString());
        assertEquals("when_caused_by_living_non_player", type.get("scaling").getAsString());
        assertEquals(0.0, type.get("exhaustion").getAsDouble());
        for (String tag : List.of("/data/minecraft/tags/damage_type/bypasses_shield.json",
                "/data/spell_engine/tags/damage_type/evadable.json")) {
            com.google.gson.JsonObject file = json(tag).getAsJsonObject();
            assertFalse(file.get("replace").getAsBoolean(), tag);
            assertTrue(file.getAsJsonArray("values").contains(new com.google.gson.JsonPrimitive("mobs_ld:warden_sonic")), tag);
        }
        for (String tag : List.of("/data/loot_stage/tags/damage_type/unscaled.json",
                "/data/minecraft/tags/damage_type/is_explosion.json", "/data/minecraft/tags/damage_type/no_knockback.json",
                "/data/minecraft/tags/damage_type/panic_causes.json")) {
            assertFalse(json(tag).toString().contains("warden_sonic"), tag);
        }
        assertEquals(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("mobs_ld", "warden_sonic")),
                net.ledok.mobs_ld.registry.ModDamageTypes.WARDEN_SONIC);
    }

    private static JsonElement json(String path) throws Exception {
        try (Reader reader = new InputStreamReader(ShippedBehavioursTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }

    @Test
    void wardenM2FieldsAreValidated() {
        String base = "{\"abilities\": {\"a\": {\"duration\": 3, \"events\": [%s]}}}";
        String hit = "{\"type\": \"hit\", \"at\": 1, \"shape\": {\"type\": \"rays\", \"count\": 8, "
                + "\"length\": 10, \"width\": 2, \"widen\": 0.3}}";
        String along = "{\"type\": \"fx\", \"at\": 1, \"particles\": {\"id\": \"minecraft:sonic_boom\", \"at\": \"along\", "
                + "\"every\": 2}}";
        assertEquals("", error(base.formatted(hit + ", " + along)));
        assertTrue(error(base.formatted(along)).contains("along"), "nothing lands on its beat");
        assertTrue(error(base.formatted(hit + ", " + along.replace("\"at\": 1,", "\"at\": 2,"))).contains("along"));
        assertFalse(error(base.formatted(hit.replace("0.3", "-1"))).isEmpty(), "lanes do not narrow");
        assertTrue(error(base.formatted(hit.replace("\"length\": 10", "\"length\": 60") + ", " + along))
                .contains("at most"), "widening lanes are capped in length");
        assertEquals("", error(base.formatted(hit.replace("\"length\": 10", "\"length\": 60").replace(", \"widen\": 0.3", ""))),
                "straight rays keep any length");
        assertEquals("", error(base.formatted("{\"type\": \"vulnerable\", \"at\": 1, \"until\": 2, \"multiplier\": 1.25}")));
        assertTrue(error(base.formatted("{\"type\": \"vulnerable\", \"at\": 2, \"until\": 1, \"multiplier\": 1.25}"))
                .contains("before it starts"));
        assertEquals("", error(base.formatted("{\"type\": \"clip\", \"at\": 1, \"clip\": \"attack\"}")));
        assertFalse(error(base.formatted("{\"type\": \"clip\", \"at\": 1}")).isEmpty());
        assertFalse(error(base.formatted(hit.replace("}}", "}, \"rotate\": 400}"))).isEmpty());
        String host = "{\"host\": {\"type\": \"mobs_ld:warden\", \"only_tagged\": [\"t\"], \"heartbeat\": %s}}";
        assertEquals("", error(host.formatted("90")));
        assertFalse(error(host.formatted("200")).isEmpty());
        assertEquals("", error("{\"abilities\": {\"a\": {\"duration\": 1, \"cooldown_group\": \"g\", \"no_repeat\": true, "
                + "\"fixed_speed\": true}}}"));
    }

    /** Default options while the Warden's file has no typed host block, and the option ranges. */
    @Test
    void wardenOptionsAreValidated() {
        String base = "{\"host\": {\"type\": \"mobs_ld:warden\", \"only_tagged\": [\"t\"]%s}}";
        MobBehaviour plain = MobBehaviour.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(base.formatted("")))
                .getOrThrow();
        assertEquals(WardenHostOptions.DEFAULTS, plain.hostOptions(0).orElseThrow());
        assertEquals("", error(base.formatted(", \"darkness_pulse\": \"vanilla\", \"disable_shields\": true")));
        assertFalse(error(base.formatted(", \"darkness_pulse\": \"dim\"")).isEmpty());
        assertFalse(error(base.formatted(", \"follow_range\": 0")).isEmpty());
    }

    @Test
    void cooldownsTakeTimeRanges() {
        String json = "{\"abilities\": {\"a\": {\"duration\": 1, \"cooldown\": [30, 60], \"initial_cooldown\": 20, "
                + "\"skip_when_full\": true}}, \"phases\": [{\"below_health\": 1.0, \"cooldowns\": {\"a\": [10, 12]}}]}";
        MobBehaviour behaviour = MobBehaviour.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        MobBehaviour.Ability ability = behaviour.abilities().get("a");
        assertEquals(new Seconds(30.0, 60.0), ability.cooldown());
        assertEquals(Seconds.of(20.0), ability.initialCooldown());
        assertTrue(ability.skipWhenFull());
        assertEquals(new Seconds(10.0, 12.0), behaviour.phases().get(0).cooldowns().get("a"));
        java.util.Random random = new java.util.Random(5);
        boolean low = false;
        boolean high = false;
        for (int i = 0; i < 500; i++) {
            double rolled = ability.cooldown().roll(random::nextDouble);
            assertTrue(rolled >= 30.0 && rolled <= 60.0, "rolled " + rolled);
            low |= rolled < 35.0;
            high |= rolled > 55.0;
        }
        assertTrue(low && high, "a fresh value each use, across the range");
        assertFalse(error(json.replace("[30, 60]", "[60, 30]")).isEmpty());
    }

    @Test
    void witherLoads() throws Exception {
        MobBehaviour wither = load("/data/minecraft/mob_behaviour/wither.json");
        assertEquals(SlotPicker.Rule.LOWEST_THREAT, wither.targetSlots().get("right_head").rule());
        assertEquals(SlotPicker.Rule.SPREAD, wither.targetSlots().get("left_head").rule());
        assertEquals(new Seconds(9.0, 11.0), wither.targetSlots().get("left_head").repick());
        assertEquals(5.0, wither.targetSlots().get("left_head").offset());
        assertEquals(java.util.Optional.of("right_head"), wither.leadSlot("left_head"));
        assertTrue(wither.leadSlot("right_head").isEmpty());
        double[] center = {4.0, 3.5, 3.0};
        Seconds[] side = {new Seconds(4.0, 5.0), new Seconds(3.5, 4.5), new Seconds(3.0, 4.0)};
        for (int phase = 0; phase < 3; phase++) {
            HostOptions options = wither.hostOptions(phase).orElseThrow();
            assertTrue(options instanceof WitherHostOptions);
            assertEquals(Seconds.of(center[phase]), options.skulls().get("center").every().orElseThrow(), "phase " + phase);
            assertEquals(side[phase], options.skulls().get("side").every().orElseThrow(), "phase " + phase);
            // The phase blocks only change the cadence: the hits stay the file's.
            assertEquals(2.4, options.skulls().get("center").hit().amount().orElseThrow());
            assertEquals(1, options.skulls().get("side").hit().effects().size());
            assertEquals(1.0, options.skulls().get("volley").splash().orElseThrow().amount());
        }
    }

    @Test
    void witherSoulRushLoads() throws Exception {
        MobBehaviour wither = load("/data/minecraft/mob_behaviour/wither.json");
        assertEquals(java.util.Set.of("soul_rush", "soul_rush_2", "soul_rush_follow", "grave_call"),
                wither.abilities().keySet());
        DashEvent rush = dash(wither.abilities().get("soul_rush"));
        assertEquals(java.util.Optional.of(1.4), rush.speed());
        assertTrue(rush.width().blocks().isEmpty(), "width: body");
        assertEquals(DashEvent.Push.SIDE, rush.push());
        assertEquals(java.util.Optional.of(0.4), rush.height());
        assertEquals(0.0, rush.staggerMinDistance(), "any wall hit staggers, so it cancels the Grave Call");
        assertEquals(Telegraph.Style.SWEEP, rush.telegraph().style());
        assertTrue(rush.telegraph().arrow());
        assertEquals(0.8, rush.begin(), 1.0e-9);
        assertEquals(20.0, rush.hit().orElseThrow().amount().orElseThrow());
        // 28 blocks at 1.4 per tick take 1 s: the event outlasts the longest rush, the ability outlasts the event.
        assertTrue(rush.end() >= 1.8 + 1.0 && rush.end() <= wither.abilities().get("soul_rush").duration());
        DashEvent follow = dash(wither.abilities().get("soul_rush_follow"));
        assertFalse(follow.telegraph().show());
        assertEquals(12.0, follow.hit().orElseThrow().amount().orElseThrow());
        assertEquals(MobBehaviour.TargetRule.slot("left_head"), wither.abilities().get("soul_rush_follow").target());
        // The follow rush locks 0.3 s before it starts, 0.7 s after the snap.
        assertEquals(0.4, wither.abilities().get("soul_rush_follow").trackUntil().orElseThrow(), 1.0e-9);
        assertEquals(0.7, follow.at(), 1.0e-9);
        assertTrue(wither.abilities().get("soul_rush_2").events().stream().anyMatch(event -> event instanceof ChainEvent chain
                && chain.to().equals("soul_rush_follow") && Math.abs(chain.at() - 2.9) < 1.0e-9));
        assertEquals(java.util.List.of("soul_rush", "grave_call"), wither.phases().get(0).abilities());
        assertEquals(java.util.List.of("soul_rush_2", "grave_call"), wither.phases().get(1).abilities());
        assertEquals(java.util.List.of("soul_rush_2", "grave_call"), wither.phases().get(2).abilities());
        assertEquals(java.util.Map.of("soul_rush_2", 6.0), wither.phases().get(1).readyIn());
        assertTrue(wither.phases().get(2).readyIn().isEmpty());
        assertTrue(wither.phases().stream().allMatch(phase -> phase.cooldowns().isEmpty()));
        assertEquals(3.0, wither.stagger().orElseThrow().recover(), "no forced Grave Call the moment it gets up");
    }

    @Test
    void witherGraveCallLoads() throws Exception {
        MobBehaviour wither = load("/data/minecraft/mob_behaviour/wither.json");
        MobBehaviour.Ability call = wither.abilities().get("grave_call");
        assertEquals(new Seconds(30.0, 60.0), call.cooldown());
        assertEquals(Seconds.of(20.0), call.initialCooldown());
        assertEquals(0, call.weight());
        assertTrue(call.forceAfter().isEmpty(), "only after a full Soul Rush, never on its own");
        assertTrue(call.skipWhenFull());
        for (String last : List.of("soul_rush", "soul_rush_follow")) {
            assertTrue(wither.abilities().get(last).events().stream().anyMatch(event -> event instanceof ChainEvent chain
                    && chain.to().equals("grave_call") && chain.ifReady()
                    && Math.abs(chain.at() - wither.abilities().get(last).duration()) < 1.0e-9), last);
        }
        assertEquals(3.0, call.duration());
        SummonEvent summon = summons(call).get(0);
        assertEquals(ResourceLocation.withDefaultNamespace("wither_skeleton"), summon.entity());
        SummonEvent.MinionLink link = summon.minionLink().orElseThrow();
        assertEquals(new SummonEvent.DeathDamage(0.5, java.util.Optional.empty()), link.deathDamage().orElseThrow());
        assertEquals(new SummonEvent.AllDead(1.5, SummonEvent.Until.NEXT_SUMMON), link.allDead().orElseThrow());
        assertEquals(new SummonEvent.Count(2, 4, 2, 12), summon.count());
        assertEquals(new SummonEvent.MaxAlive(6, 3, 16), summon.maxAlive());
        SummonEvent.Placement placement = summon.placement().orElseThrow();
        assertEquals(SummonEvent.Placement.Around.FIGHTERS, placement.around());
        assertEquals(new SummonEvent.Radius(3.0, 6.0), placement.radius());
        assertEquals(2.0, placement.minGap());
        assertEquals(3, placement.clearance());
        assertTrue(placement.ground());
        SummonEvent.Runes runes = summon.telegraph().orElseThrow();
        assertEquals(0.9, runes.radius());
        assertEquals(0x3A1F5C, runes.telegraph().color());
        // Runes at 0.2 s, the adds rise at 1.6 s.
        assertEquals(0.2, summon.begin(), 1.0e-9);
        assertEquals(1.6, summon.end(), 1.0e-9);
        SummonEvent.Scale scale = summon.scale().orElseThrow();
        assertEquals(java.util.Optional.of(new SummonEvent.StatScale(0.025, true)), scale.maxHealth(), "a fortieth of its HP");
        assertEquals(java.util.Optional.of(new SummonEvent.StatScale(1.0, false)), scale.attackDamage());
        assertTrue(scale.armor().orElseThrow().summonerBonus());
        assertTrue(scale.copiesDamageDealt());
        assertFalse(summon.loot());
        assertFalse(summon.xp());
        assertTrue(summon.bound());
        assertEquals(SummonEvent.AddTarget.RUNE_PLAYER, summon.target());
        assertTrue(call.events().stream().anyMatch(event -> event instanceof HoverEvent hover
                && hover.relativeTo() == HoverEvent.Reference.GROUND && hover.height() == 3.0 && hover.until() == 3.0));
        FxEvent fx = call.events().stream().filter(FxEvent.class::isInstance).map(FxEvent.class::cast).findFirst().orElseThrow();
        assertEquals("mobs_ld.cue.wither.grave_call", fx.message().orElseThrow().key());
        assertEquals(0.6F, fx.sound().orElseThrow().pitch());
        assertEquals(4.0, fx.particles().orElseThrow().column());
    }

    @Test
    void summonsAreValidated() {
        String base = "{\"abilities\": {\"a\": {\"duration\": 3, \"events\": [%s]}}}";
        String plain = "{\"type\": \"summon\", \"at\": 1, \"entity\": \"minecraft:zombie\", \"count\": 2, \"max_alive\": 4}";
        assertEquals("", error(base.formatted(plain)));
        assertEquals("", error(base.formatted(plain.replace("\"count\": 2", "\"count\": {\"min\": 1, \"max\": 3}"))));
        assertFalse(error(base.formatted(plain.replace("\"count\": 2", "\"count\": {\"min\": 3, \"max\": 1}"))).isEmpty());
        assertFalse(error(base.formatted(plain.replace("}", ", \"target\": \"nearest\"}"))).isEmpty());
        assertFalse(error(base.formatted(plain.replace("}", ", \"placement\": {\"radius\": [5, 2]}}"))).isEmpty());
        assertFalse(error(base.formatted(plain.replace("}", ", \"telegraph\": {\"radius\": 0}}"))).isEmpty());
    }

    private static DashEvent dash(MobBehaviour.Ability ability) {
        return ability.events().stream().filter(DashEvent.class::isInstance).map(DashEvent.class::cast).findFirst()
                .orElseThrow();
    }

    @Test
    void dashAndChainAreValidated() {
        String base = "{\"abilities\": {\"a\": {\"duration\": 3, \"events\": [%s]}, \"b\": {\"duration\": 1}}}";
        String timed = "{\"type\": \"dash\", \"at\": 1, \"until\": 2, \"max_distance\": 10}";
        assertEquals("", error(base.formatted(timed)));
        assertEquals("", error(base.formatted("{\"type\": \"dash\", \"at\": 1, \"speed\": 1.4, \"max_distance\": 28, "
                + "\"width\": \"body\", \"push\": \"side\", \"telegraph\": {\"style\": \"sweep\", \"arrow\": true}}")));
        assertEquals("", error(base.formatted(timed.replace("}", ", \"width\": 3.5}"))));
        assertTrue(error(base.formatted("{\"type\": \"dash\", \"at\": 1, \"max_distance\": 10}")).contains("speed"));
        assertTrue(error(base.formatted(timed.replace("\"until\": 2", "\"until\": 0.5"))).contains("before it starts"));
        assertFalse(error(base.formatted(timed.replace("}", ", \"width\": \"wide\"}"))).isEmpty());
        assertFalse(error(base.formatted(timed.replace("}", ", \"push\": \"up\"}"))).isEmpty());
        assertFalse(error(base.formatted(timed.replace("}", ", \"telegraph\": {\"style\": \"spin\"}}"))).isEmpty());
        assertEquals("", error(base.formatted("{\"type\": \"chain\", \"at\": 2, \"to\": \"b\"}")));
        assertTrue(error(base.formatted("{\"type\": \"chain\", \"at\": 2, \"to\": \"c\"}")).contains("'c'"));
    }

    private static String error(String json) {
        return MobBehaviour.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error()
                .map(DataResult.Error::message).orElse("");
    }

    @Test
    void referencesAreValidated() {
        String host = "\"host\": {\"type\": \"mobs_ld:wither\", \"skulls\": {\"volley\": {\"hit\": {\"amount\": 4}}}}, "
                + "\"target_slots\": {\"right_head\": {\"rule\": \"lowest_threat\"}}, ";
        String ok = "{" + host + "\"abilities\": {\"a\": {\"duration\": 1, \"target\": \"slot:right_head\", \"events\": ["
                + "{\"type\": \"projectile\", \"at\": 0, \"skull\": \"volley\", \"aim\": \"slot:right_head\"},"
                + "{\"type\": \"look\", \"at\": 0, \"until\": 1, \"heads\": \"side\", \"target\": \"current\"},"
                + "{\"type\": \"fx\", \"at\": 0, \"particles\": {\"id\": \"minecraft:soul_fire_flame\", \"at\": \"heads\"}}]}}}";
        assertEquals("", error(ok));
        assertTrue(error(ok.replace("\"target\": \"slot:right_head\"", "\"target\": \"slot:left_head\"")).contains("left_head"));
        assertTrue(error(ok.replace("\"skull\": \"volley\"", "\"skull\": \"center\"")).contains("center"));
        assertTrue(error(ok.replace("\"aim\": \"slot:right_head\"", "\"aim\": \"slot:nope\"")).contains("nope"));
        assertTrue(error(ok.replace("minecraft:soul_fire_flame", "minecraft:dust")).contains("dust"));
        assertFalse(error(ok.replace("{\"amount\": 4}", "{\"amount\": 4, \"damage\": 2}")).isEmpty());
        assertFalse(error(ok.replace("\"lowest_threat\"", "\"highest_threat\"")).isEmpty());
        assertFalse(error(ok.replace("\"heads\": \"side\"", "\"heads\": \"tail\"")).isEmpty());
        assertEquals("", error(ok.replace("\"target\": \"slot:right_head\"", "\"target\": \"lowest_threat\"")));
        // An offset slot must re-pick inside its lead's period, or it would never get its turn.
        String slots = "\"target_slots\": {\"right_head\": {\"rule\": \"lowest_threat\"}}, ";
        String paired = "\"target_slots\": {\"right_head\": {\"rule\": \"lowest_threat\", \"repick\": [9, 11]}, "
                + "\"left_head\": {\"rule\": \"spread\", \"offset\": 5}}, ";
        assertEquals("", error(ok.replace(slots, paired)));
        assertTrue(error(ok.replace(slots, paired.replace("\"offset\": 5", "\"offset\": 9"))).contains("offset"));
    }
}
