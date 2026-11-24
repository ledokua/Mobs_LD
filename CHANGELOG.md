# Changelog

## Unreleased
- **New custom boss: Nameless King** (`mobs_ld:nameless_king`):
  - Data-driven `LdMob` storm-and-lightning demigod (`mobs_ld/entities/nameless_king.json`: fixed `580 HP`, `14` attack,
    `12` armor, `4` toughness) with GeckoLib 4 model (117 cubes, 9 marker bones), all 16 smoothed `catmullrom`/hit-stop
    animations, emissive golden-rune glowmask, overcharged Phase 2 variant, and pre-baked hit tracks
    (`data/mobs_ld/hit_tracks/nameless_king.json`).
  - Three-phase fight (`data/mobs_ld/mob_behaviour/nameless_king.json`) with `target_slots` (`storm_prey`,
    `thunder_mark`), deceptive delayed Dragonslayer Swordspear combos, orbiting cyclone vortex (`cyclone_spear`),
    sustained howling gale push (`storm_wave`), aerial storm-cloud dive (`sky_dive`), vertical lightning stake,
    updraft-into-sky-bolt `falling_bolt` / 3+ player raid `tempest_judgment`, custom `mobs_ld:storm_lightning` damage
    type, custom `mobs_ld:storm_static` debuff with handcrafted 18×18 HUD icon, and 6 exclusive 3D VFX
    (`spear_arc`, `lightning_bolt`, `storm_gale`, `tempest_cyclone`, `thunder_spear_dive`, `storm_nova`).
- **Interactive player-boss combat engine & custom boss spell upgrades:**
  - Added optional additive `"grab"` (`GrabEvent`, collision-safe lift/hold, periodic ticks, directional throw, and
    teamplay `rescue_damage` break) and `"pull"` (`PullEvent`, `inward`/`outward`/`updraft`/`orbit` spatial force
    fields) ability events, plus optional `pull` and `slam_down` physics on `HitSpec` (existing base-mod bosses
    untouched).
  - Upgraded **Midra** (`madness_grab` now genuinely grabs, lifts, sears, and throws a player with `madness_grasp` 3D
    VFX and teammate `28` damage rescue break; `uppercut_slash` launches into a mid-air `flame_pillar`; `frenzy_burst`
    draws players inward before detonating), **Amber Brute** (`double_rake` hook-in -> air-pop -> ground-spike combo,
    pop-up `crystal_fissure`, clamping `amber_snare`, high-launch `crystal_resonance`), and **Emberroot** (`root_snare` /
    `root_cascade` anti-air root undertow `slam_down`, bouncy `cataclysm_drum`, catapulting burrow eruptions), keeping
    all 4 custom bosses strictly disjoint visually, thematically, and mechanically.
- Emberroot summons Kiln Sprouts that ward him from a distance and Cinder Runts that slash, pounce, and roll.
  Runt rolls recover when they reach the end of the marked lane or hit a wall.
- Summons with `per_type: true` count every type in their pool (`entity` and `alternatives`) against `max_alive`.
- **New custom raid boss: Emberroot** (`mobs_ld:emberroot`):
  - Data-driven `LdMob` charred-wood furnace raid boss built for 3–6 player encounters (`mobs_ld/entities/emberroot.json`:
    `640 HP`, `10` armor, `4` toughness) with GeckoLib 4 model (8 marker bones including live `fx_core`), all 14 original
    animations (`damdge` mapped to `hurt`, arm bracing added to `lazer_attack` and `attack_shoulder`), emissive molten
    glowmask, and overheated Phase 2/3 variant.
  - Three-phase raid fight (`data/mobs_ld/mob_behaviour/emberroot.json`) with secondary `target_slots` (`cinder_prey`
    spread & `ember_mark` lowest-threat), 5 dedicated `min_players: 3` raid-wide mechanics (`root_cascade` 3-wave
    `each_fighter` creeping roots, `cataclysm_drum` 3-ring arena seismic dance + personal eruptions, `subterranean_hunt`
    marking `top_damage` amid `each_fighter` decoy mounds, `furnace_crossfire` 6-ray volcanic cinder star + `30°`
    rotation, and chained `roll_crush_p2 -> roll_crush_follow` with `taunt_wins`), reworked frontline/flank/in-out core
    abilities, solo/duo fallbacks (`max_players: 2`), custom `mobs_ld:ember_blight` damage type, and `cinder_mark` stacks.
  - 6 exclusive vanilla-RPG 3D VFX archetypes (`root_tendrils`, `cinder_eruption` supporting both single chest blasts
    and 6-ray raid stars, `roll_trail`, `burrow_mound`, `drum_tremor`, `rib_furnace`) with zero overlap with Midra or
    Amber Brute, pre-baked hit tracks (`data/mobs_ld/hit_tracks/emberroot.json`), English design doc
    (`docs/design/emberroot.md`), and `en_us` / `uk_ua` localization.
- **Custom boss status effects & pixel-art HUD icons:**
  - Added `BossStatusEffect` (`mobs_ld:frenzied_burn`, `mobs_ld:amber_mire`, `mobs_ld:charred_roots`) with handcrafted
    18×18 Minecraft pixel-art HUD icons (`assets/mobs_ld/textures/mob_effect/*.png`), custom afflicted-entity particles
    (`small_flame`, `falling_honey`, `smoke`), and matching ground `cloud` particles (`CloudEvent`).
- **Smoother Minecraft-style boss animations:**
  - Refined all 45 animation clips across Midra (20), Amber Brute (11), and Emberroot (14) to use hybrid GeckoLib 4
    `catmullrom` splines on flowing motion with crisp `linear` impact hit-stops, eliminated all 205 action-layer
    boundary snaps, and re-baked `hit_tracks/{midra,amber_brute,emberroot}.json`.
- **New custom boss: Amber Brute** (`mobs_ld:amber_brute`):
  - Data-driven `LdMob` woodland/amber colossus (`mobs_ld/entities/amber_brute.json`) with GeckoLib 4 model (`head`
    look-at, 8 marker bones), all 7 original animations plus 4 physics-matched combat clips (`knuckle_sweep`,
    `double_rake`, `tremor_stomp`, `crystal_roar`), warm crystal glowmask, and Phase 2 illuminated-visor variant.
  - Distinct vanilla-RPG two-phase fight (`data/mobs_ld/mob_behaviour/amber_brute.json`): animation-driven left-claw
    and right-knuckle `trace` attacks, heavy fist slams with `vulnerable` fist-pullout openings, cascading 4-spire
    tectonic crystal fault lines (`crystal_fissure`), `each_fighter` amber geode snare traps (`amber_snare`), low
    tumbling amber shard fans (`knuckle_sweep`), boot-stomp + crystal resonance rings, sticky resin puddles, Phase 2
    hardened projectile resistance (`0.75x`), `mobs_ld:amber_shatter` damage type, and `amber_resin` stacks.
  - 6 new restrained vanilla-RPG 3D VFX archetypes (`amber_spire`, `amber_geode_trap`, `ground_fracture`, `shard_spray`,
    `resonance_ring`, `claw_rake`) with zero overlap with Midra's VFX, pre-baked hit tracks
    (`data/mobs_ld/hit_tracks/amber_brute.json`), English design doc (`docs/design/amber_brute.md`), and `en_us` /
    `uk_ua` localization.
- **Custom client-side 3D spell VFX (`"type": "vfx"` ability event & silent telegraph locking):**
  - Optional data-driven `"vfx"` event backed by compact one-shot S2C networking (`mobs_ld:spell_vfx` /
    `mobs_ld:spell_vfx_cancel`) and a shader-safe client-side 3D renderer with 17 procedural spell archetypes
    (`slash_arc`, `lance_lunge`, `sweeping_beam`, `eye_charge`, `meteor_strike`, `magma_fissures`, `nova_dome`,
    `shockwave_ring`, `flame_wave`, `flame_pillar`, `vortex_slash`, `amber_spire`, `amber_geode_trap`,
    `ground_fracture`, `shard_spray`, `resonance_ring`, `claw_rake`) and local client particles — zero server visual
    entities and zero server tick overhead.
  - Hidden telegraphs (`"show": false`) with an explicit `"from"` on `hit` and `dash` now lock their hitboxes / dash
    corridors silently at `from`, allowing `"vfx"` events with `"origin": "locked"` to align pixel-for-pixel with the
    server hitbox (including `each_fighter` + `lead` and dynamic dash length) without drawing flat ground boxes or
    arrows. Off by default; existing shipped mobs are untouched.
- **New custom boss: Midra, Lord of Frenzied Flame** (`mobs_ld:midra`):
  - Data-driven `LdMob` entity (`mobs_ld/entities/midra.json`) with GeckoLib 4 model, 20 animations with client-side
    ember/flame/camera-shake keyframes, emissive glowmask, and Phase 2 incandescent texture + glowmask variant.
  - Three-phase fight (`data/mobs_ld/mob_behaviour/midra.json`) covering all 20 animations with full custom 3D Frenzied
    Flame spell visuals across every attack (no flat ground boxes or arrows): tilted fiery greatsword crescent slashes,
    3D helical lance-lunge dash, Madness Grab, twin-ring 360° whirlwind + rear backlash, Damnation Plunge with 6
    erupting magma fissure curtains, continuous sweeping volumetric Frenzy Beam, plummeting Frenzied Flame meteors
    (`each_fighter`), levitating Frenzied Sun dome detonation, and Meteoric Crater Slam with explicit `vulnerable`
    punish windows.
  - Custom damage type `mobs_ld:frenzied_flame` (`#minecraft:bypasses_shield`, `#spell_engine:evadable`, burning
    feedback) and `frenzy` hit stacks (`+10%` per stack, max 5, 18 s).
  - Pre-baked hit tracks (`data/mobs_ld/hit_tracks/midra.json`), English design doc (`docs/design/midra.md`), and
    `en_us` / `uk_ua` localization.

## 1.7.11
- A creeper running a behaviour never fuses or explodes (nor can it be lit); its attacks are its abilities.
- The Sand Golem Colossus holds its ground-slam pose during recovery and lights its chest ankh. It completes a
  slam or sweep before its first husk call, and calls three husks solo plus one per additional fighter, up to ten.
- Summoned husks take 10% of the Colossus's max health and inherit its attack scaling, bonus armor, and
  DD Loot Stage damage multiplier when available.
- `after_abilities` gates an ability until any named prerequisite finishes in the current fight.
- `/mobs_ld debug preview` outlines locked area-hit zones and shows the impact tick and hit targets for operators.
- The build checks shipped mob clips, ability timing, marker bones, and baked hit-track freshness.

## 1.7.10
- `shoot`: the drawn line stops where the shot would first hit a block (a low wall under the shot's path does not
  cut it).

## 1.7.9
- `telegraph.repeat` and `projectile.directions.step`: lines drawn and fired one after another, clockwise.
- Skull spec `hit_radius`: skulls that hit anything passing within that many blocks of their path.

## 1.7.8
- Telegraphs follow the terrain: each part is laid on the floor under it (stairs, slabs, steps), joined by walls at
  height changes, and left out where the floor is outside the hit's height band (the band is now sent to clients).
- `shoot` event: fires any projectile (arrow, small fireball, ...) down a line drawn on the ground first.
- `telegraph` event: draws a shape that deals nothing (e.g. the paths of a volley).
- `projectile.directions`: skulls fired along fixed directions around the mob (matching a `rays` shape).
- `hover.home`: fly back to the mob's home (an Arenas_LD arena's spawn point).
- Ability `only_tagged`: an ability only mobs with one of these tags may use.

## 1.7.7
- `hit.ignite`: seconds the victim burns once a hit lands (ability hits and skulls).
- `config/mobs_ld_damage.json` is written at startup.

## 1.7.6
- **Attack profiles** (`data/<ns>/mobs_ld/attack_profiles/*.json`): resize any mob's hits on players before
  `loot_stage:damage_dealt` and the Arenas tier scale them.
  - Actions: `hits` × a reference hit, `multiply`, `set`, `ignore`.
  - Matches: damage type or tag, direct entity, ability, or a hit with no attacker near a mob of the type.
- **Hit caps** (`config/mobs_ld_damage.json`, off by default; a profile can set its own): one mob hit takes at most a
  share of the player's max health. The cap applies after damage scaling and before armour.
- `/mobs_ld damage config` reloads the caps.
- Shipped with no profiles and caps off, so nothing changes until a server adds a profile table.

## 1.7.5
- `/mobs_ld damage audit start|stop|dump|status|attributes`: records every hit on a player, grouped by dealer, damage
  type, projectile and ability. It writes the raw amount, the share of max health, the health taken and the dealer's
  stats to `mobs_ld/damage_audit/*.json`, and lists the mob types without `loot_stage:damage_dealt`.
- `/mobs_ld debug` also writes its trace to the server log.

## 1.7.1 - 1.7.4
- `summon.minion_link`:
  - a killed add hurts its summoner, credited to the add's killer;
  - once all linked adds are dead, the summoner takes extra damage until its next summon.
- `chain.if_ready`: hand over to the next ability only if it could start on its own.
- `stagger.recover`: a no-ability window after a stagger.
- Wither: Grave Call comes only after a full Soul Rush sequence; a wall stagger cancels it.
- Threat:
  - summon damage shares threat with the summon's owner;
  - mrpg_lib stealth (Infiltrator's Vanish) drops threat;
  - Alter Ego decoys hold aggro as provokes and copy the caster's health.

## 1.7 - Warden rework (M1 - M3)
- The engine can host Brain-driven mobs through a host adapter. The Warden is hosted only in Arenas_LD runs
  (`host.only_tagged`); world Wardens stay vanilla.
- New: the `clip`, `vulnerable` and `listen` events, `rays` and `ring` shapes, widening lanes, `each_fighter` hits with
  lead, `hit.stacks`, `when.behind`, `top_damage` targeting, player-count limits, cooldown groups, and the
  `mobs_ld:warden_sonic` damage type.
- The Warden's P1-P3 kit with the Sonic Boom Circle.

## 1.6 - Wither rework (R1)
- Ability engine runs on any vanilla mob (vanilla hosts, host adapters, `base_attributes`, `movement.style: vanilla`).
- Wither: three-head targeting with target slots, skull volleys, Soul Rush dashes, Grave Call summons. Its spawn blast
  has its own unscaled damage type.
- Summon extensions: runes, placement, scaling from the summoner, bound adds, no-loot adds.

## 1.5 and earlier
- Threat system (MMO-style aggro, taunts and provokes, Spell Engine AGGRO compat).
- Data-driven mob foundation on GeckoLib 4: entity declarations, the behaviour JSON format, phases and boss rules,
  `trace` hit tracks, chase and chain. Gorefist and Rot Wart.
