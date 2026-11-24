# Midra, Lord of Frenzied Flame (`mobs_ld:midra`)

Custom data-driven boss built on the shared `LdMob` + `MobCombat` engine (no per-mob Java entity class). All combat
numbers are placeholder values kept in data (`mobs_ld/entities/midra.json` and
`data/mobs_ld/mob_behaviour/midra.json`) for server tuning.

---

## 1. Role and Fight Identity

Midra is a towering (~5.5-block visual height, 1.8 × 4.8 hitbox), fire-immune duel-and-sorcery boss wielding the
Greatsword of Damnation and the Frenzied Flame:

- **Clear damage split.** Physical greatsword strikes deal `minecraft:mob_attack` (reduced by armor, blockable with
  shields). Frenzied Flame sorcery and magma eruptions deal `mobs_ld:frenzied_flame` (armor and Protection apply,
  `#minecraft:bypasses_shield`, `#spell_engine:evadable`, deliberately outside `#minecraft:is_fire` so a Fire
  Resistance potion does not trivialize the fight, with `"effects": "burning"` for fiery hurt feedback).
- **Frenzy Buildup (`hit.stacks`, id `"frenzy"`).** Frenzied Flame hits leave 1 stack of Frenzy (`+10%` damage taken
  from Midra per stack, cap 5, 18 s duration, refreshes on hit, active at any player count). Rewards clean dodging.
- **Custom 3D Frenzied Flame spell VFX (`"type": "vfx"`).** Every ability replaces flat ground rectangles and arrows
  with readable, server-optimized 3D procedural spell visuals bound directly to silent early-locked hitboxes and dash
  corridors (`"telegraph": { "show": false, "from": ... }` + `"origin": "locked"`):
  - Greatsword swings (`attack`, `swipe`, `backhand_cleave`, `uppercut_slash`, `combo`, `triple_waltz`): glowing windup
    arcs bursting into 3D fiery crescent slashes (`slash_arc`) tilted to match horizontal, diagonal, rising, and
    vertical overhead cleaves.
  - `thrust_lunge`: zero ground arrow — uses `lance_lunge` (converging golden charge rings + luminous corridor rails
    and streaming golden wind-streaks, bursting into a 3D helical Frenzied Flame spear-cone + shockwave rings along the
    exact locked dash distance).
  - `frenzy_beam` / `frenzy_beam_p2`: zero ground arrows — `eye_charge` corona feeding a single continuous 3D volumetric
    `sweeping_beam` with crackling Frenzied lightning tendrils sweeping across the arena in sync with the `once` hit slices.
  - `flame_rain_cast` / `flame_rain_cast_p2`: rotating golden arcane sigils + plummeting 3D comets (`meteor_strike`) at
    every locked `each_fighter` predicted landing spot.
  - `sword_plunge_aoe`, `ground_wave_sweep`, `whirlwind_slash`, `frenzy_burst`, `frenzy_nova_slam`: 3D `nova_dome`
    detonations, 6 erupting `magma_fissures` flame curtains, crashing `flame_wave` tsunami walls, twin tilted 360°
    `vortex_slash` cyclones, towering `flame_pillar` columns, and expanding `shockwave_ring` fire walls.
- **Melee and ranged fairness.** Melee players get readable 3D greatsword windup arcs, shieldable physical hits, and
  explicit `vulnerable` (`1.2x`–`1.25x`) punish windows after big Frenzied Flame casts (`triple_waltz`, `frenzy_beam`,
  `frenzy_burst`, `frenzy_nova_slam`), while `whirlwind_backlash` discourages mindless rear stacking. Ranged players are
  kept moving by `frenzy_beam` (`farthest`, `include_unreachable: true`), `flame_rain_cast` (`each_fighter` with
  movement lead), `thrust_lunge` (gap-closing dash), and outer shockwave rings.
- **Server optimization.** All ambient flame embers, sword-tip sparks, hit-sparks, and camera shakes live client-side
  in GeckoLib animation keyframes (`midra.animation.json`), and all 3D spell VFX run client-side from a single compact
  S2C packet (`mobs_ld:spell_vfx`) with zero server visual entities and zero server tick loops. Server `fx` events fire
  only for sounds, action-bar cues, and death ash/smoke.

---

## 2. Phases

| Phase | HP | Attack speed | Visuals / Bar | Changes |
|---|---|---|---|---|
| **Intro** | 100% | 1.0 | `spawn` clip (3.8 s, invulnerable) | Rises from bowed pose and ignites his Frenzied Flame sun-head at 1.72 s. |
| **Phase 1 — The Enduring Lord** | 100–55% | 1.0 | Base texture + glowmask; yellow `notched_10` bar | Measured greatsword kit (`attack`, `swipe`, `backhand_cleave`, `uppercut_slash`, `combo`, `thrust_lunge`, `madness_grab`) plus `whirlwind_slash` / `whirlwind_backlash`, `sword_plunge_aoe`, `ground_wave_sweep`, `frenzy_beam` (4-beat sweep), and `flame_rain_cast` (2 waves). |
| **Transition — The Depths of Your Foolishness** | at 55% | — | `frenzy_burst` clip (3.6 s, invulnerable), swaps to `phase2` texture + glowmask | Levitates, detonates inner r 7.0 blast at 1.60 s and outer ring 7.0–13.0 at 2.15 s, leaves a Weakening scorched cloud on landing. |
| **Phase 2 — Lord of Frenzied Flame** | 55–25% | 1.12 | `midra_phase2.png` + `midra_phase2_glowmask.png` (blazing white-gold crown, widened blade veins, glowing ribcage); red `notched_10` bar | Replaces `swipe` with `triple_waltz`, upgrades `frenzy_beam` → `frenzy_beam_p2` (5-beat wider sweep) and `flame_rain_cast` → `flame_rain_cast_p2` (3 waves), and unlocks `frenzy_burst` and `frenzy_nova_slam`. |
| **Phase 3 — Frenzied Crescendo** | 25–0% | 1.22 | `phase2` texture; red `notched_12` bar | No invulnerable interruption; accelerates timelines and clips to `1.22x`, tightens signature cooldowns, and readies `frenzy_nova_slam` (2 s) and `frenzy_beam_p2` (5 s). |
| **Soft Enrage** | 7:00 | — | `frenzy_burst` clip | `2.0x` damage multiplier after 420 s in combat. |
| **On Death** | 0% | 1.0 | `death` clip (3.8 s, `death_ticks: 80`) | Kneels at 1.40 s as the Frenzied Flame dies out, collapses forward at 2.95 s. |

---

## 3. Ability Table

Damage values are raw `hit.amount` at level 0 / Normal, scaled at hit time by `R = live attack_damage / 14.0`.
`P` = `minecraft:mob_attack`, `F` = `mobs_ld:frenzied_flame`.

| Ability | Phases | Clip (len) | Telegraph & Impact | Counterplay | Damage (L0 raw) | Cooldown |
|---|---|---|---|---|---|---|
| `attack` | all | `attack` (2.5 s) | Crimson rect 2.4 × 5.5 from 0.50 s → 1.30 s | Sidestep, roll, or block | P 16.0, kb 0.7 | 4.5–5.5 s |
| `swipe` | P1 | `swipe` (2.1 s) | Crimson cone 130° r 5.0 from 0.35 s → 1.00 s | Roll through or block | P 12.0, kb 0.6, +1 Frenzy | 4.5–6.0 s |
| `backhand_cleave` | all | `backhand_cleave` (2.0 s) | Crimson cone 120° r 4.8 from 0.32 s → 0.96 s | Quick punish against flanking; block or roll | P 11.0, kb 0.5 | 4.0–5.5 s |
| `uppercut_slash` | all | `uppercut_slash` (2.3 s) | Sweeping rect 2.2 × 5.5 from 0.42 s → 1.12 s | Sidestep; launches victim upward | P 14.0, knock_up 0.65 | 6.5–8.0 s |
| `combo` | all | `combo` (3.4 s) | Cone 115° r 4.8 at 0.85 s; re-aimed circle offset 2.0 r 4.2 at 2.15 s | Block/dodge opener; step out of leap slam crater | P 10.0 + F 16.0 (+1 Frenzy) | 9–11 s |
| `triple_waltz` | P2, P3 | `triple_waltz` (3.6 s) | Two re-aimed cones 120° r 4.8 (0.78 s, 1.52 s) → sweeping rect 2.6 × 6.2 (2.48 s); vulnerable 2.85–3.60 s (`1.2x`) | Stay disciplined through three beats, punish recovery | P 9.0 + P 10.0 + F 17.0 (+1 Frenzy) | 9–11 s (P3: 7.5–9.5 s) |
| `thrust_lunge` | all | `thrust_lunge` (2.2 s) | 3D `lance_lunge` corridor + charge rings + stop-bar (w 2.2, max 13 + 2 overshoot, no arrow) from 0.22 s → dash 0.78–1.10 s at `farthest` (4.5–14 blocks); `vulnerable` 1.45–2.15 s (`1.2x`) | Sidestep or roll through the lunge corridor, then punish recovery | P 15.0, side push, +1 Frenzy | 9–12 s |
| `madness_grab` | all | `madness_grab` (2.8 s) | Actionbar cue + snaring reach cone 75° r 4.2 at 0.78 s (Slowness III 1.5 s) → gaze sear 90° r 5.0 at 1.85 s (Nausea + Darkness) → toss 100° r 5.2 at 2.22 s; `vulnerable` 2.28–2.80 s (`1.2x`) | Step out of the initial reach cone before 0.78 s and punish end-lag | P 6.0 + F 13.0 (+1 Frenzy) + F 7.0 (kb 1.35) | 13–16 s |
| `whirlwind_slash` | all | `whirlwind_slash` (2.9 s) | Inner circle r 5.2 at 1.10 s → outer flame ring 2.8–6.4 at 1.65 s | Step out for beat 1, then step back in (< 2.8) for beat 2! | P 10.0 + F 11.0 (+1 Frenzy) | group `whirlwind`: 10–13 s |
| `whirlwind_backlash` | all | `whirlwind_slash` (2.9 s) | Triggers when 1+ player stands behind Midra (r 5, 140°) for 3.5 s; same 2-beat inside/outside dance | Do not camp behind his back | P 11.0 + F 12.0 (+1 Frenzy) | group `whirlwind`: 10 s |
| `sword_plunge_aoe` | all | `sword_plunge_aoe` (3.2 s) | Crater circle at blade tip (`offset: 2.8`, r 4.5) at 1.38 s → 6 widening magma rays (length 14) at 2.15 s + Slowness cloud | Dodge the plunge, then step between the 6 magma fissures | P 14.0 + F 13.0 (+1 Frenzy) | 12–15 s |
| `ground_wave_sweep` | all | `ground_wave_sweep` (2.9 s) | Close cone 135° r 5.0 at 1.35 s → launched flame cone 95° r 12.0 (`sweep`) at 1.88 s | Roll behind Midra or sidestep the long flame wave | P 11.0 + F 13.0 (+1 Frenzy) | 10–13 s |
| `frenzy_beam` | P1 | `frenzy_beam` (3.8 s) | `eye_charge` + continuous 3D `sweeping_beam` (54°, len 26) synced to 4 `once` hit slices (`-24°, -8°, +8°, +24°`) at 1.25 / 1.75 / 2.25 / 2.75 s; vulnerable 3.05–3.80 s (`1.25x`) | Run ahead of or roll across the beam sweep; punish recovery | F 15.0 (once per sweep, +1 Frenzy) | group `frenzy_beam`: 15–18 s |
| `frenzy_beam_p2` | P2, P3 | `frenzy_beam` (3.8 s) | `eye_charge` + wider 3D `sweeping_beam` (66°, len 28) synced to 5 `once` hit slices (`-30°..+30°`); vulnerable 3.05–3.80 s (`1.25x`) | Strafe or roll through the beam; melee punish on his flank | F 17.0 (once per sweep, +1 Frenzy) | group `frenzy_beam`: 13–16 s (P3: 10–13 s) |
| `flame_rain_cast` | P1 | `flame_rain_cast` (3.4 s) | `flame_pillar` cast + 2 waves of `each_fighter` `meteor_strike` comets (r 2.8, lead 0.4 s) at 1.50 s / 2.35 s | Keep moving across the arcane sigils; free melee DPS on rooted Midra | F 11.0 per wave (+1 Frenzy) | group `flame_rain`: 18–22 s |
| `flame_rain_cast_p2` | P2, P3 | `flame_rain_cast` (3.4 s) | `flame_pillar` cast + 3 waves of `each_fighter` `meteor_strike` comets (r 2.8, lead 0.45 s) at 1.40 / 2.00 / 2.60 s | Continuous movement; high melee uptime | F 11.5 per wave (+1 Frenzy) | group `flame_rain`: 15–19 s (P3: 12–15 s) |
| `frenzy_burst` | P2, P3 | `frenzy_burst` (3.6 s) | Inner blast r 6.5 at 1.60 s (Nausea 2.5 s) → outer ring 6.5–12.5 at 2.25 s; vulnerable 2.95–3.60 s (`1.25x`) | Run outside r 6.5, then dash back inside r 6.5 before the ring pops! | F 18.0 + F 13.0 (+1 Frenzy) | 22–26 s (P3: 18–21 s) |
| `frenzy_nova_slam` | P2, P3 | `frenzy_nova_slam` (3.6 s) | Crater circle (`offset: 1.5`, r 5.5) at 1.82 s → shockwave ring 5.5–11.5 at 2.35 s; vulnerable 2.40–3.60 s (`1.25x`) | Clear crater, step back in as the outer ring detonates, burst during crater hold | F 20.0 + F 13.0 (+1 Frenzy) | 18–22 s (P3: 14–17 s) |

---

## 4. Tunable Knobs

All balance values are data-driven:

- **Base stats:** `src/main/resources/mobs_ld/entities/midra.json` (`max_health`, `attack_damage`, `armor`,
  `movement_speed`, `follow_range`).
- **Damage scaling baseline:** `reference_attributes.minecraft:generic.attack_damage` in
  `src/main/resources/data/mobs_ld/mob_behaviour/midra.json` (currently `14.0`, matching the entity file).
- **Per-ability damage, cooldowns, ranges, and Frenzy stack weight (`per_stack`, `max`, `duration`):**
  `src/main/resources/data/mobs_ld/mob_behaviour/midra.json`.
- **Phase thresholds and tempo:** `phases[].below_health` (`1.0`, `0.55`, `0.25`), `attack_speed` (`1.0`, `1.12`,
  `1.22`), `boss.leash` (`36`), `boss.soft_enrage.after` (`420`).
