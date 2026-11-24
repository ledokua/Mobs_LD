# Midra, Lord of Frenzied Flame — hand-off notes (`mobs_ld:midra`)

Status: **ready for in-game fight pass** (exported to GeckoLib 4 from Blockbench 5.0 source, 20 clips, glowmasks and
Phase 2 variant generated, hit tracks baked). Full boss design: `docs/design/midra.md`.

## Model
- **Hitbox:** `1.8 × 4.8` blocks (width × height), `eye_height: 4.35` (centered on the Frenzied Flame eye oval),
  `fire_immune: true`, `death_ticks: 80`.
- **Visual extent:** ~5.5 blocks tall (head crown solid cubes reach Y = 85.8, outer flickering flame tongues to 92.8);
  faces north (`-Z`), origin `[0, 0, 0]` at feet center on the ground.
- **Textures (256 × 256, per-face UV, 1 texel per model unit, alpha-cutout robes & flame corona):**
  - `midra.png` + `midra_glowmask.png` (blazing Frenzied Flame head & outer corona full-bright with the pitch-black eye
    void kept non-emissive; molten veins and barbs on the Greatsword of Damnation glow full-bright).
  - `midra_phase2.png` + `midra_phase2_glowmask.png` (hotter white-gold flame corona, widened organic molten fissures
    along the basalt blade, glowing frenzied amber inside the charred ribcage, lit robe fringes).
- **Rig hierarchy (30 bones total):**
  - `root` > `leg_r` > `shin_r`; `leg_l` > `shin_l`; `body` > `head` > `flame_outer`, `shawl_r`,
    `shawl_l` > `shawl_l_far`, `skirt_f/b/r/l`, `arm_r` > `forearm_r` > `sword`, `arm_l` > `forearm_l`.
  - Hidden ModelEngine `hitbox` bone was stripped on GeckoLib export.
- **Marker bones (empty, no cubes):**
  - `fx_feet` (under `root`), `fx_chest` (under `body`), `fx_eye` & `fx_crown` (under `head`),
    `hit_hand_left` & `fx_hand_left` (under `forearm_l`),
    `hit_sword_mid`, `hit_sword_tip`, `fx_sword_mid`, `fx_sword_tip` (under `sword`).

## Base Clips
| clip | length | loop | notes |
|---|---|---|---|
| `idle` | 3.8 s | loop | Dignified sway; subtle client-side ember & sword lava particles |
| `walk` | 2.2 s | loop | **3.0 blocks per cycle** (measured from FK stride `1.53 × 2`); subtle footstep smoke |
| `hurt` | 0.9 s | once | Crisp recoil; ember scatter from `fx_eye` |
| `spawn` | 3.8 s | once | Used as `intro`: rises from bowed pose, ignites Frenzied Flame head at 1.72 s (`shake(0.55, 24)`) |
| `death` | 3.8 s | hold | Kneels at 1.40 s, flame extinguishes, collapses forward at 2.95 s (`death_ticks: 80`) |

## Ability Clips (15 clips, all active in `midra.json`)
| clip | length | key impact(s) | telegraph / hit notes |
|---|---|---|---|
| `attack` | 2.5 s | 1.30 s | Heavy overhead cleave: rectangle 2.4 × 5.5 |
| `swipe` | 2.1 s | 1.00 s | Horizontal flame slash: cone 130° r 5.0 |
| `backhand_cleave` | 2.0 s | 0.96 s | Reverse left-to-right cleave: cone 120° r 4.8 |
| `uppercut_slash` | 2.3 s | 1.12 s | Rising uppercut: sweeping rectangle 2.2 × 5.5, knock-up 0.65 |
| `combo` | 3.4 s | 0.85 s, 2.15 s | Rising cone 115° r 4.8 → re-aimed parabolic leap slam circle (`offset: 2.0`, r 4.2) |
| `triple_waltz` | 3.6 s | 0.78 s, 1.52 s, 2.48 s | 2 re-aimed cones 120° r 4.8 → sweeping finisher rect 2.6 × 6.2; vulnerable 2.85–3.60 s |
| `thrust_lunge` | 2.2 s | 0.78–1.10 s | Forward impaling dash (`width: 2.2`, `max_distance: 13`, `overshoot: 2`) at `farthest` |
| `madness_grab` | 2.8 s | 0.78 s, 1.85 s, 2.22 s | Reach snare cone → blazing eye gaze sear (Nausea + Darkness) → forceful fling |
| `whirlwind_slash` | 2.9 s | 1.10 s, 1.65 s | 360° circle r 5.2 → outer flame ring 2.8–6.4 (also drives `whirlwind_backlash`) |
| `sword_plunge_aoe` | 3.2 s | 1.38 s, 2.15 s | Blade tip plunges at `offset: 2.8` (circle r 4.5) → 6 widening magma rays (length 14) + cloud |
| `ground_wave_sweep` | 2.9 s | 1.35 s, 1.88 s | Low sweep cone 135° r 5.0 → launched Frenzied Flame wave cone 95° r 12.0 |
| `frenzy_beam` | 3.8 s | 1.25–2.75 s | Sweeping left-to-right beam strips (4 in P1, 5 wider in P2, `once: true`); vulnerable 3.05–3.80 s |
| `flame_rain_cast` | 3.4 s | 1.50/2.35 s (P2: 1.40/2.00/2.60 s) | Skyward V-ritual: `each_fighter` predicted circles (r 2.8) |
| `frenzy_burst` | 3.6 s | 1.60 s, 2.25 s | Levitation detonation (P2 transition + P2/P3 ability): circle r 6.5 → outer ring 6.5–12.5 |
| `frenzy_nova_slam` | 3.6 s | 1.82 s, 2.35 s | Soaring leap slam (`offset: 1.5`): crater r 5.5 → shockwave ring 5.5–11.5; vulnerable 2.40–3.60 s |

## Note on Hit Tracks vs Ground Telegraphs
Because Midra is 5.5 blocks tall, his greatsword sweeps pass at 2.5–5.2 blocks above ground during most horizontal and
overhead swings (above a 1.8-block player's hitbox). To guarantee reliable, fair, readable combat, Midra's attacks use
ground-projected `hit` shapes and `dash` lanes rather than `trace` capsules. `data/mobs_ld/hit_tracks/midra.json` is
still baked and shipped for all 20 clips × 30 bones in case future tweaks use `trace` (re-bake with `/mobs_ldc bake midra`
if the model or clips change).
