# Emberroot (`mobs_ld:emberroot`) — 3–6 Player Raid Boss Design

## Concept & Silhouette
A hulking charred-wood forest colossus with a molten furnace heart caged inside blackened ribs, massive ground-sweeping fists, stubby planted legs, and a smoldering crown of burnt branches. Designed as a grounded vanilla-RPG **3–6 player raid boss**: chunky blocky silhouettes, readable physical windups, multi-target role pressure across tanks, melee DPS, ranged DPS, and healers, and zero VFX overlap with **Midra** or **Amber Brute**.

## Entity Declaration (`mobs_ld/entities/emberroot.json`)
- **Hitbox:** `2.4 × 3.9` blocks (`eye_height: 2.9`), `tracking_range: 16`, `fire_immune: true`, `death_ticks: 70` (matches the `3.4 s` death hold clip).
- **Raid Base Stats:** `640 HP`, `14.0` attack damage (reference stat), `10.0` armor, `4.0` armor toughness, `1.0` knockback resistance, `0.25` movement speed, `48.0` follow range.
- **Visuals:** Phase 1 `emberroot.png` + emissive `emberroot_glowmask.png` (core, eyes, veins, branch tips); Phase 2 & 3 (`< 60% HP`) switch to `emberroot_phase2.png` + `emberroot_phase2_glowmask.png` (overheated white-gold furnace core & ignited bark cracks).

## Custom Damage Type, Status Effect & Raid Pressure
- **`mobs_ld:ember_blight`** (`ModDamageTypes.EMBER_BLIGHT`): molten furnace blasts, erupting charred roots, and ribcage bursts. Armor and Protection apply; bypasses shields (`#minecraft:bypasses_shield`); dodgeable via Spell Engine (`#spell_engine:evadable`); intentionally outside `#minecraft:is_fire` so Fire Resistance potions do not trivialize the raid.
- **`mobs_ld:charred_roots`** (`ModEffects.CHARRED_ROOTS`): custom status effect with handcrafted 18×18 pixel-art HUD icon (`textures/mob_effect/charred_roots.png`), `-20%` movement speed and `-2.0` armor per level, emitting `minecraft:smoke` on afflicted players and ground clouds.
- **`cinder_mark` stacks:** `+12%` damage per stack (up to 5 stacks, 15 s duration, active at `min_players: 3`), rewarding tank-swapping and clean dodging in 3–6 player raids.
- **Target Slots (`target_slots`):**
  - `cinder_prey` (`spread`, repick `8–11 s`): rotates onto non-tank raid members (`shoulder_barge`, `roll_crush_follow`).
  - `ember_mark` (`lowest_threat`, repick `9–12 s`, `offset: 4.0 s`): hunts backline healers and low-threat ranged DPS (`roll_crush_p2`).

## Exclusive Vanilla-RPG 3D VFX Archetypes
All 6 archetypes are unique to Emberroot (zero overlap with Midra's 11 or Amber Brute's 6):
1. `root_tendrils` — chunky 3-segment charred-wood roots with glowing ember tips curling inward out of cracked earth (`root_snare`, `root_cascade`).
2. `cinder_eruption` — rough blocky volcanic ash-and-cinder jet (`count: 1` tracks live `fx_core` for `furnace_blast`; `count: 6` radiates a 360° volcanic star for `furnace_crossfire`).
3. `roll_trail` — twin scorched wheel ruts with kicking bark & dirt clods (`roll_crush`, `roll_crush_p2`, `roll_crush_follow`, `shoulder_barge`).
4. `burrow_mound` — heaving pile of dark soil blocks and molten cracks erupting outward (`burrow_ambush`, `subterranean_hunt`, `cataclysm_drum`, `spawn`).
5. `drum_tremor` — concentric blocky ash-and-earth seismic tiles rippling outward across circles or arena-wide rings (`hammer_slam`, `tantrum_drum`, `cataclysm_drum`, `rib_furnace`, `swipe`).
6. `rib_furnace` — orbiting cinder blocks exploding outward in a chunky hexagonal furnace wave (`rib_furnace`, `rib_backlash`, Phase 2 transition).

## Kiln Sprouts and Cinder Runts

In phases 2 and 3, Emberroot marks ground 9–13 blocks away and raises two minions per wave, up to six live adds.
Each spot independently rolls a Kiln Sprout or a Cinder Runt. Both start with 3.5% of Emberroot's live maximum health.
Sprouts channel from up to 15 blocks away.
The first tether makes Emberroot take half damage. Three or more connected Sprouts also increase his outgoing
damage by 50%. The ember tethers show which Sprouts are channeling. Killing a Sprout, blocking its line of
sight or getting outside tether range breaks its connection.

Cinder Runts chase players. Their short claw slash covers a forward cone, their pounce lands in a marked circle,
and their roll charges down a marked lane. The pounce misses if the Runt hits terrain and falls short of the marker.

## Raid-Wide (`min_players: 3`) & Reworked Full 14-Animation Kit
All 14 clips in `emberroot.bbmodel` are actively used (`damdge` mapped to `hurt`):
- **Dedicated 3+ Player Raid Abilities (`min_players: 3`):**
  - `root_cascade` (`root_snare`, `4.4 s`) — 3-wave creeping `each_fighter` + `lead: 0.55` root storm erupting under every player simultaneously; forces the whole raid to spread and keep moving.
  - `cataclysm_drum` (`tantrum_drum`, `4.4 s`) — full-arena rhythmic dance combining 3 expanding concentric tremor rings (`4.0–9.5`, `9.5–16.0`, `15.5–24.0`), an inner melee slam, and simultaneous `each_fighter` ground eruptions, ending in a `1.3×` raid burst window.
  - `subterranean_hunt` (`burrow_ambush`, `3.6 s`) — marks `top_damage` with `minecraft:glowing`, triggers decoy `each_fighter` `burrow_mound` eruptions under every player while surging underground straight beneath the top DPS, and erupts in a heavy uppercut.
  - `furnace_crossfire` (`lazer_attack`, `4.5 s`, Phase 2+) — 6 radiating volcanic `cinder_eruption` lanes (`length: 24`, `width: 2.6`) followed by a `30°`-rotated second salvo (`turn_body: true`), forcing the entire raid to rotate through the safe gaps.
  - `roll_crush_p2` → `roll_crush_follow` (`roll_crush`, Phase 2+) — bowls straight into the backline (`slot:ember_mark`) and immediately chains (`taunt_wins: true`) into a second roll onto `slot:cinder_prey` unless a tank taunts to intercept it.
- **Reworked Core Abilities (All Roles Engaged):**
  - `swipe` — wide `trace` (`radius: 1.2`) + off-hand shock circle to punish careless melee flanking.
  - `hammer_slam` — heavy frontline slam (`radius: 4.2`) + expanding mid-range seismic ring (`4.2–10.5`).
  - `shoulder_barge` — aims at `slot:cinder_prey` to break up stacked melee clumps.
  - `rib_furnace` — out-then-in raid dance (`circle 7.0` + `charred_roots` cloud, followed by `ring 6.5–16.5` forcing players back inside for the `1.25×` punish window).
  - `rib_backlash` — `when.behind` (`players: 2`, `3.0 s`) punishes melee packs camping his back.
  - Solo/duo fallbacks (`max_players: 2`): `root_snare`, `tantrum_drum`, `burrow_ambush`, `furnace_blast` remain available so 1–2 player fights stay clean and balanced.
- **3 Raid Phases:**
  - **Phase 1 (`100%–60%`):** Core kit + `root_cascade`, `cataclysm_drum`, `subterranean_hunt`, `roll_crush`, `rib_backlash`.
  - **Phase 2 (`60%–25%`, `1.10×` speed, `phase2` texture):** Invulnerable `rib_tear` Furnace Awakening transition; unlocks `furnace_crossfire`, `rib_furnace`, and chained `roll_crush_p2 → roll_crush_follow`.
  - **Phase 3 (`< 25%`, `1.20×` speed, `notched_12` bar):** Tighter cooldowns across all raid-wide abilities and immediate `cataclysm_drum` / `furnace_crossfire` readiness.
