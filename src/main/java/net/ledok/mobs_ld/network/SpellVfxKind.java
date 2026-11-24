package net.ledok.mobs_ld.network;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Procedural 3D client-side spell VFX archetypes triggered by {@code vfx} ability events.
 * Wire encoding uses enum ordinal (clamped on decode), so append new kinds at the end to keep protocol stability.
 */
public enum SpellVfxKind implements StringRepresentable {
    /** Curved fiery greatsword crescent slash with optional windup preview arc and tilt. */
    SLASH_ARC("slash_arc"),
    /** Charging lance rings + glowing energy corridor replacing flat ground arrows, bursting into a 3D energy spear. */
    LANCE_LUNGE("lance_lunge"),
    /** Continuous 3D volumetric beam from elevated origin sweeping smoothly across an angular arc. */
    SWEEPING_BEAM("sweeping_beam"),
    /** Swirling blazing corona + counter-rotating arcane rings + converging filaments around a focal point. */
    EYE_CHARGE("eye_charge"),
    /** Rotating arcane ground sigil + blazing descending 3D meteor comet bursting on impact. */
    METEOR_STRIKE("meteor_strike"),
    /** Radiating molten ground cracks erupting into jagged 3D vertical flame curtains along every ray. */
    MAGMA_FISSURES("magma_fissures"),
    /** Contracting charge sphere + glowing perimeter ring exploding into a layered 3D blazing hemisphere dome. */
    NOVA_DOME("nova_dome"),
    /** Expanding 3D rising cylindrical wall of fire & shockwave rings sweeping from inner width to outer radius. */
    SHOCKWAVE_RING("shockwave_ring"),
    /** Traveling 3D curved crescent tsunami of fire surging forward across a cone. */
    FLAME_WAVE("flame_wave"),
    /** Towering rotating vortex column of fire with helical ribbons shooting skyward. */
    FLAME_PILLAR("flame_pillar"),
    /** Full 360-degree spinning double-layered flaming greatsword whirlwind rings. */
    VORTEX_SLASH("vortex_slash"),
    /** Chunky faceted 3D amber crystal stalagmite + side shards bursting up from cracked earth. */
    AMBER_SPIRE("amber_spire"),
    /** Octagonal earthen crack snapping upward into 4 angled amber crystal jaws around a target. */
    AMBER_GEODE_TRAP("amber_geode_trap"),
    /** Radial earthen ground fractures with chunky tilted amber-crust slabs erupting around an impact. */
    GROUND_FRACTURE("ground_fracture"),
    /** Directional fan of chunky tumbling 3D faceted crystal shards launched along parabolic arcs. */
    SHARD_SPRAY("shard_spray"),
    /** Spaced ring of chunky faceted amber crystal spikes erupting across an inner-to-outer band. */
    RESONANCE_RING("resonance_ring"),
    /** Crisp 3-prong physical claw-scratch trails matching a fast melee claw rake. */
    CLAW_RAKE("claw_rake"),
    /** Chunky stepped charred-wood roots with glowing ember tips coiling upward out of cracked soil. */
    ROOT_TENDRILS("root_tendrils"),
    /** Rough volcanic charcoal-and-cinder furnace jet roaring forward from the caster's chest core. */
    CINDER_ERUPTION("cinder_eruption"),
    /** Twin scorched bark-and-ember wheel ruts with tumbling charred splinters along a rolling corridor. */
    ROLL_TRAIL("roll_trail"),
    /** Heaving mound of chunky dark soil clods and glowing cracks churning before an underground ambush. */
    BURROW_MOUND("burrow_mound"),
    /** Concentric blocky ash-and-cinder seismic ground tiles rippling outward from heavy fist drums. */
    DRUM_TREMOR("drum_tremor"),
    /** Roaring cinder-block and heatwave burst erupting out of a split ribcage furnace. */
    RIB_FURNACE("rib_furnace"),
    /** Closing cone of clutching Frenzied Flame tendrils and constricting madness rings for Midra's grab. */
    MADNESS_GRASP("madness_grasp"),
    /** Sleek narrow storm-steel and sunlight-lightning crescent trail for the Nameless King's swordspear swings. */
    SPEAR_ARC("spear_arc"),
    /** Gathering storm-cloud ring discharging a jagged sky-to-earth golden sunlight thunderbolt with ground forks. */
    LIGHTNING_BOLT("lightning_bolt"),
    /** Layered pale-cyan howling wind-shear crescents and storm ribbons surging forward across a cone. */
    STORM_GALE("storm_gale"),
    /** Swirling grey-cyan storm-cloud funnel with orbiting golden static bands for the Nameless King's cyclone. */
    TEMPEST_CYCLONE("tempest_cyclone"),
    /** Elevated storm-cloud halo + crackling cyan-gold plunging lightning streak and impact forks for sky dives. */
    THUNDER_SPEAR_DIVE("thunder_spear_dive"),
    /** Contracting storm-mist updraft sphere erupting into radial golden floor lightning arcs and a cyan wind ring. */
    STORM_NOVA("storm_nova"),
    /** A muted dust crescent following the radius and angle of a broad melee sweep. */
    SAND_SWEEP("sand_sweep");

    public static final Codec<SpellVfxKind> CODEC = StringRepresentable.fromEnum(SpellVfxKind::values);
    private static final SpellVfxKind[] VALUES = values();

    private final String name;

    SpellVfxKind(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static SpellVfxKind byId(int id) {
        if (id < 0 || id >= VALUES.length) {
            return SLASH_ARC;
        }
        return VALUES[id];
    }
}
