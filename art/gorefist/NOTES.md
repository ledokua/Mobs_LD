# Gorefist — hand-off notes (`mobs_ld:gorefist`)

Status: **blockout v1** (2026-10-20). Proportions and rig are final enough to test; the texture is a flat-colour
blockout, and only four clips exist (idle, walk, slam, death). Full concept: `concept.md` in this folder.

## Model
- Hitbox: **2.5 × 4.0** blocks (width × height). Eye height ≈ 3.4 blocks (head pivot at y = 50/16).
- Visual extent: 86 units wide (x −42 … +20, the fist overhangs 1.3 blocks to the creature's right),
  60 units tall, z −30 … +12. Faces north, origin at the centre of the feet.
- Texture 256 × 256, box UV, 1 texel per unit. Glowmask `gorefist_glowmask.png` (eyes + crystal cracks on the fist).
- Bones: `root` › `body` › `chest` › `head` › `jaw`; `chest` › `arm_right` › `forearm_right` › `fist_right`;
  `chest` › `arm_left` › `forearm_left`; `body` › `leg_right`, `leg_left`.
- Marker bones (empty): `hit_fist_right` (bottom of the fist, where slams land), `fx_fist_right` (front knuckles),
  `hit_hand_left`, `fx_mouth`, `fx_chest`.

## Base clips
| clip | length | loop | notes |
|---|---|---|---|
| `idle` | 2.4 s | loop | breathing; fist stays on the floor |
| `walk` | 1.4 s | loop | **2.0 blocks per cycle** (two steps of 1.0). Fist drags and lifts on the right step |
| `death` | 2.0 s | hold | falls forward onto the fist, topples to its left; sound at 0.0 / 1.2, `shake 0.4 12` at 1.2 |

## Ability clips
| clip | length | impact | telegraph | centre | target |
|---|---|---|---|---|---|
| `slam` | 2.2 s | **1.1 s** | circle, r 3.5 blocks | 3 blocks in front of the boss | current (tank) |

In the clip: roar sound at 0.3 s, explode sound + `minecraft:explosion` particle at `hit_fist_right` and
`shake 0.6 16` at 1.1 s. Windup is 0–1.0 s (fist overhead, chest rears back); recovery 1.5–2.2 s.

Planned next (see concept.md for shapes/targets/cooldowns): `haymaker` (1.4 s, impact 0.6), `leap_slam`
(2.4 s, impact 1.5), `charge` (2.0 s, moving 0.7–1.5), `quake` (2.6 s, impact 1.3), `intro`, `stagger`,
`phase_2_transition`, `enrage`, `hurt`, plus `gorefist_phase2.png`.

## Resolved with Mobs_LD (2026-10-20)
- Abilities are timelines of hit events, so one clip may carry several impact times (phase-2 slam: 1.1 s circle, 1.5 s rays).
- `charge` → wall hit → `stagger` clip, ~1.8 s, hold on last frame. Planned, not built yet.
- Animation file loads as exported (carries `geckolib_format_version: 2`); trailing `;` on timeline instructions is fine.
- Further clips wait for LeDok's confirmation of the ability kit.
