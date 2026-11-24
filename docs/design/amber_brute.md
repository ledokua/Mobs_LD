# Amber Brute (`mobs_ld:amber_brute`)

Custom data-driven woodland colossus built on the shared `LdMob` + `MobCombat` engine (no per-mob Java entity class).
Designed for a grounded, vanilla-RPG modpack feel: tactile animation-driven claw swings, cascading earthen amber
stalagmites, crystalline snare traps, sticky resin puddles, and clear melee punish windows — with zero overlap in
attacks or VFX archetypes with Midra, Gorefist, the Wither, or the Warden.

All combat numbers are data-driven placeholders in `mobs_ld/entities/amber_brute.json` and
`data/mobs_ld/mob_behaviour/amber_brute.json` for server tuning.

---

## 1. Role and Fight Identity

Amber Brute is a lumbering (~4.6-block tall, `2.0 × 4.6` hitbox) asymmetric relic guardian clad in mossy boots, leather
harness, wooden dome visor helmet, rattling rod backpack, a whip-fast 3-clawed left hand, and an enormous amber-crystal
right arm:

- **Asymmetric two-arm rhythm & damage split.**
  - Physical claw rakes (`claw_rake`, `double_rake` beats 1–2, `knuckle_sweep` drag) use real animation-driven `trace`
    capsules (`["forearm_l", "hit_claw_left"]` and `["forearm_r", "hit_fist_right"]`) dealing `minecraft:mob_attack`
    (shield-blockable, armor applies). Heavy slams (`heavy_slam`, `tremor_stomp` stomp) are also physical and blockable.
  - Earthen crystal eruptions and flung shards (`crystal_fissure`, `amber_snare`, `knuckle_sweep` shards,
    `resonance_ring`, `double_rake` slam) deal `mobs_ld:amber_shatter` (armor and Protection apply,
    `#minecraft:bypasses_shield`, `#spell_engine:evadable`, `"effects": "thorns"` for crisp crystalline impact feedback).
- **Amber Resin (`hit.stacks`, id `"amber_resin"`) & sticky puddles.** Claw rakes and crystal shatters coat victims in
  Amber Resin (`+12%` damage taken per stack, cap 4, 14 s, active at any player count), paired with short `Slowness`
  puddles (`cloud`) on heavy slams and Phase 2 transition.
- **Grounded vanilla-RPG 3D VFX (6 dedicated archetypes, zero overlap with Midra).** Every attack replaces flat ground
  boxes and arrows with restrained, chunky, faceted translucent honey-amber geometry (`amber_spire`, `amber_geode_trap`,
  `ground_fracture`, `shard_spray`, `resonance_ring`, `claw_rake`) and vanilla `wax_on`, `falling_honey`, `crit`, and
  `poof` client-local particles — single compact S2C packet per event, zero server entities, zero server tick work.
- **No dashes, beams, or sky meteors.** Battlefield control comes purely from linear cascading earth-spires,
  stationary geode traps under ranged players, low knuckle shard fans, and inner/outer crystal rings with generous
  `vulnerable` (`1.15x`–`1.30x`) fist-pullout openings.

---

## 2. Phases

| Phase | HP | Attack speed | Visuals / Bar | Changes |
|---|---|---|---|---|
| **Intro** | 100% | 1.0 | `spawn` clip (3.2 s, invulnerable) | Claws up out of the earth (`0.95 s`), shakes off dirt (`1.55 s`), raises massive crystal arm (`2.25 s`). |
| **Phase 1 — Dormant Relic** | 100–55% | 1.0 | Base texture + crystal glowmask; yellow `notched_6` bar | Methodical forest brute: `claw_rake`, `heavy_slam`, `crystal_fissure` (4-spire fault line), `amber_snare` (`each_fighter` geode traps), `knuckle_sweep`, `tremor_stomp` / `tremor_backlash`. |
| **Transition — Hardened Crust** | at 55% | — | `crystal_roar` clip (2.6 s, invulnerable), swaps to `phase2` texture + glowmask | Helmet visor slits ignite warm amber and crystal clusters brighten; detonates a mid-range amber spike ring (`3.0–8.5` at `1.15 s`) and drops a sticky resin cloud. |
| **Phase 2 — Awakened Amber Colossus** | 55–0% | 1.12 | `amber_brute_phase2.png` + `amber_brute_phase2_glowmask.png`; yellow `notched_10` bar | Hardens against arrows (`projectile: 0.75x` damage taken), unlocks `double_rake` (3-beat L-L-R combo) and `crystal_resonance` (safe inside `r 3.2` with `1.3x` melee punish window), tightens `crystal_fissure` and `amber_snare` cooldowns. |
| **Soft Enrage** | 7:00 | — | `crystal_roar` clip | `2.0x` damage multiplier after 420 s in combat. |
| **On Death** | 0% | 1.0 | `death` clip (3.4 s, `death_ticks: 70`) | Knees buckle at `1.25 s`, topples onto his heavy crystal arm at `2.15 s` with a small ground fracture. |

---

## 3. Ability Table

Damage values are raw `hit.amount` at level 0 / Normal, scaled at hit time by `R = live attack_damage / 13.0`.
`P` = `minecraft:mob_attack`, `A` = `mobs_ld:amber_shatter`.

| Ability | Phases | Clip (len) | Visual Tell & Impact | Counterplay | Damage (L0 raw) | Cooldown |
|---|---|---|---|---|---|---|
| `claw_rake` | P1, P2 | `swipe` (1.6 s) | Fast left-claw `trace` (`0.45–0.56 s`) + crisp 3-prong `claw_rake` trails | Read arm coil; block or roll | P 10.0, Slowness I 2.5 s, +1 Resin | 3.8–5.0 s |
| `heavy_slam` | P1, P2 | `attack` (2.5 s) | `ground_fracture` under right fist (`offset 1.9, side 0.8`, r 3.2) at `0.98 s` + Slowness cloud; `vulnerable` `1.55–2.40 s` (`1.25x`) | Sidestep left or block, then punish while fist pulls free | P 17.0, kb 0.85, +1 Resin | 5.5–7.0 s |
| `crystal_fissure` | P1, P2 | `attack` (2.5 s) | 4 staggered `amber_spire` stalagmites (`offset 2.6 / 5.2 / 7.8 / 10.4`, r 1.85–1.95, `once: true`) at `1.05 / 1.30 / 1.55 / 1.80 s`; `vulnerable` `1.95–2.45 s` (`1.15x`) | Sidestep the rolling crystal fault line | A 13.5 (once), knock_up 0.4, +1 Resin | 9.5–12.5 s (P2: 8–10.5 s) |
| `amber_snare` | P1, P2 | `attack` (2.5 s) | Fist slam `ground_fracture` at `0.98 s` triggers `amber_geode_trap` crystal jaws under `each_fighter` (r 2.3, no lead) locked at `0.98 s` → snap at `1.88 s`; `vulnerable` `1.95–2.45 s` (`1.15x`) | Walk out of the octagonal crack before the jaws snap | A 12.0, Slowness III 2.0 s, +1 Resin | 14–17 s (P2: 12–15 s) |
| `knuckle_sweep` | P1, P2 | `knuckle_sweep` (2.2 s) | Low right-arm `trace` (`0.84–0.98 s`) + tumbling `shard_spray` cone (`offset 1.4`, r 7.6, 80°) at `1.05 s` | Roll behind or step outside the front shard fan | P 9.5 + A 10.5 (+1 Resin) | 8.5–11 s |
| `tremor_stomp` | P1, P2 | `tremor_stomp` (2.4 s) | Heavy boot stomp `ground_fracture` (r 3.8 at `1.05 s`) → `resonance_ring` crystal spikes (`3.8–7.4` at `1.70 s`) | Step out for stomp, step back inside `r 3.8` for the crystal ring | P 12.5 + A 12.0 (+1 Resin) | group `stomp`: 10–13 s |
| `tremor_backlash` | P1, P2 | `tremor_stomp` (2.4 s) | Triggers when 1+ player stands behind Brute (r 5, 140°) for 3.2 s; same stomp + ring dance | Avoid camping behind its back | P 13.0 + A 12.5 (+1 Resin) | group `stomp`: 9.5 s |
| `double_rake` | P2 | `double_rake` (2.4 s) | Fast left forehand `trace` (`0.45–0.56 s`) → left backhand `trace` (`1.04–1.18 s`) → right-fist `ground_fracture` (`offset 2.9, side 0.35`, r 3.0 at `1.75 s`); `vulnerable` `1.95–2.40 s` (`1.25x`) | Block or weave the two fast claws, dodge the heavy finisher, punish recovery | P 8.5 + P 9.0 + A 15.5 (+2 Resin) | 8–10.5 s |
| `crystal_resonance` | P2 | `crystal_roar` (2.6 s) | Roar + 14-spike `resonance_ring` (`3.2–8.2` at `1.15 s`); `vulnerable` `1.75–2.55 s` (`1.30x`) | Hug close (`< 3.2`) to dodge the ring and burst during the `1.3x` opening | A 15.0, knock_up 0.45, +1 Resin | 15–18 s |

---

## 4. Tunable Knobs

- **Base stats:** `src/main/resources/mobs_ld/entities/amber_brute.json` (`max_health: 460`, `attack_damage: 13`, `armor: 14`, `movement_speed: 0.23`).
- **Damage scaling baseline:** `reference_attributes.minecraft:generic.attack_damage` (`13.0`) in `src/main/resources/data/mobs_ld/mob_behaviour/amber_brute.json`.
- **Phase 2 ranged hardening & punish windows:** `phases[1].damage_taken` (`projectile: 0.75`), `vulnerable` multipliers (`1.15x`–`1.30x`), and `amber_resin` stack weight (`per_stack: 0.12`, `max: 4`).
