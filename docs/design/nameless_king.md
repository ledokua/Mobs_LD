# Nameless King (`mobs_ld:nameless_king`) — Boss Design

## Concept & Identity
- **Theme:** Storm-and-Sunlight War God wielding the Dragonslayer Swordspear (sky-cyan tempest winds, slate storm clouds, and golden sunlight-lightning).
- **Fixed Attributes:** `580 HP` (fixed regardless of player count), `14.0` attack damage, `12` armor, `4` armor toughness, `1.0` knockback resistance, `0.26` movement speed, `46` follow range.
- **Zero Overlap With Other Custom Bosses:**
  - Exclusive custom damage type: `mobs_ld:storm_lightning` (armour/Protection reduce it, `#minecraft:bypasses_shield`, `#spell_engine:evadable`).
  - Exclusive custom status effect: `mobs_ld:storm_static` (`-14%` attack speed, `-5%` move speed, periodic non-lethal static pulse with electric spark particles and custom 18×18 HUD icon).
  - Exclusive 3D VFX (6 kinds, zero overlap with Midra, Amber Brute, or Emberroot): `spear_arc`, `lightning_bolt`, `storm_gale`, `tempest_cyclone`, `thunder_spear_dive`, `storm_nova`.
  - Exclusive interactive physics mechanics: orbiting tornado cyclone (`pull: orbit`), sustained outward wind gale (`pull: outward`), floating storm updrafts (`pull: updraft`) paired with sky-calling thunderbolts that slam airborne players downward (`slam_down`).

## Target Slots
- `storm_prey` (`spread`, range `40`, repick `8–11 s`) — drives `storm_wave` across different members of the group.
- `thunder_mark` (`lowest_threat`, range `40`, repick `9–12 s`, offset `4.0`) — drives `sky_dive` onto ranged/support players.

## Ability Kit (All 16 Clips Used)
- **Lifecycle:** `spawn` (sky descent + lightning arrival), `idle`, `walk`, `hurt`, `death` (kneel and collapse flat).
- **Delayed Spear Martial Arts:**
  - `delayed_slash_r` (`slash_r`): Deceptive hold followed by a wide `spear_arc` sweep applying `storm_static`.
  - `reverse_cleave` (`slash_l`): Fast backhand cleave with upward knock-up (`0.42`).
  - `spear_thrust` (`thrust`): Impaling dash (`11.5` blocks, `push: side`) along a `thunder_spear_dive` corridor.
  - `overhead_cleave` (`overhead_slam`): Delayed vertical cleave (`slam_down: 1.0`) followed by a forward ground `lightning_bolt` launch (`knock_up: 0.62`).
  - `king_combo` (`triple_combo`): 3-beat delayed combo (sweep -> upward reverse cleave `knock_up: 0.52` -> lunging lightning thrust).
- **Interactive Storm & Lightning Abilities:**
  - `storm_wave` (`storm_wave_aoe`, `slot:storm_prey`): Wide `storm_gale` cone with sustained `pull: outward` wind pushing and lifting players backward across the arena.
  - `cyclone_spear` & `cyclone_backlash` (`cyclone_spear_aoe`, `when.behind`): Full 360° `tempest_cyclone` with sustained `pull: orbit` spiral suction culminating in a high-launch shockwave (`knock_up: 0.72`).
  - `sky_dive` (`air_dive_thrust`, `slot:thunder_mark`): Vaults into a storm cloud, plunges along a locked `thunder_spear_dive` corridor (`16.5` blocks), and triggers a lightning touchdown launch (`knock_up: 0.72`).
  - `lightning_stake` (`lightning_slam_aoe`, Phase 2+): Vertical spear plunge (`lightning_bolt` center launch) followed by an outer `storm_nova` ring (`5.2..12.8`).
  - `falling_bolt` (`max_players: 2`) / `tempest_judgment` (`min_players: 3`, `fixed_speed: true`) (`falling_bolt_cast`): Iconic skyward lightning-rod cast — `pull: updraft` floats fighters off the ground before `each_fighter` + `lead` golden `lightning_bolt` strikes spike victims downward (`slam_down: 1.05`) with `storm_static II` (2 staggered waves in 3+ player raids).
  - `storm_nova` (`storm_nova_aoe`, Phase 2+): Rising `pull: updraft` lifts nearby fighters before an explosive `storm_nova` radial thunder burst (`knockback: 1.25`, `knock_up: 0.58`) + static cloud.

## Phases
1. **Phase 1 (`100% -> 60%`, blue `notched_6`):** Full martial + storm kit (`sky_dive`, `cyclone_spear`, `storm_wave`, `falling_bolt`/`tempest_judgment`).
2. **Phase 2 (`60% -> 25%`, yellow `notched_10`):** Invulnerable `storm_nova_aoe` transition, swaps to `nameless_king_phase2` (overcharged white-gold runes & icy-cyan static mane), `1.1x` attack speed, unlocks `lightning_stake` and `storm_nova`.
3. **Phase 3 (`<25%`, white `notched_12`):** `1.18x` attack speed, tightened storm/sky-bolt cooldowns.
