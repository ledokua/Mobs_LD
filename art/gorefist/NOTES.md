# Gorefist — hand-off notes (`mobs_ld:gorefist`)

Status: **blockout v3** (2026-10-20): look B palette, right forearm rebuilt as a crystal cleaver (`blade_right`
replaces `fist_right`), charge split into run / slash / exhaust clips (15 clips). Texture is still a flat-colour
blockout. Full concept: `concept.md` here.

## Model
- Hitbox: **2.5 × 4.0** blocks (width × height). Eye height ≈ 3.4 blocks (head pivot at y = 50/16).
- Visual extent: x −35 … +20 (the arm overhangs ~0.9 block to the creature's right), 60 units tall, z −31 … +12
  (the blade tip rests on the floor at about 1.4 blocks ahead of the origin). Faces north, origin at the centre of the feet.
- Texture 256 × 256, box UV, 1 texel per unit, look B. Glowmask `gorefist_glowmask.png` (eyes + ember cracks on the blade).
- Bones: `root` › `body` › `chest` › `head` › `jaw`; `chest` › `arm_right` › `forearm_right` › `blade_right`;
  `chest` › `arm_left` › `forearm_left`; `body` › `leg_right`, `leg_left`.
- Marker bones (empty): `hit_blade_right` (blade belly near the tip, where chops and the dragged tip land),
  `fx_blade_right` (mid-edge), `hit_hand_left`, `fx_mouth`, `fx_chest`. **Renamed from `hit_fist_right` / `fx_fist_right` in v3.**

## Base clips
| clip | length | loop | notes |
|---|---|---|---|
| `idle` | 2.4 s | loop | breathing; fist stays on the floor |
| `walk` | 1.4 s | loop | **2.0 blocks per cycle** (two steps of 1.0). Fist drags and lifts on the right step |
| `hurt` | 0.4 s | once | keys chest + head only (walk keeps playing underneath) |
| `intro` | 2.0 s | once | spawn/aggro roar; fist slams down at 1.5 s (`shake 0.3 12`, explosion ×4 at `hit_fist_right`) |
| `enrage` | 1.8 s | once | phase-2 transition; flame burst at `fx_chest` 0.6 s (`shake 0.3 12`), chest beat 1.2 s |
| `stagger` | 1.8 s | hold | after a wall-hit charge; reels back, fist drops, slumps; hold until released |
| `death` | 2.0 s | hold | falls forward onto the fist, topples to its left; sound 0.0 / 1.2, `shake 0.4 12` at 1.2 |

## Ability clips (hit events)
| clip | length | hit events (time → shape, centre, target) | cooldown |
|---|---|---|---|
| `vertical` | 2.2 s | **1.1 s** → rectangle 2 × 5 blocks from the boss forward (fist lands ~3.3 blocks ahead), current target | 4 s |
| `horizontal` | 1.8 s | **1.0 s** → cone 150°, 4.5 blocks, centred on the boss, current target; applies `spell_engine:bleed` | 6 s |
| `combo` | 2.4 s | **0.6 s** → cone 60° × 4 (right jab); **1.1 s** → cone 60° × 4 (left backhand); **1.7 s** → rectangle 2 × 5 (overhead chop); all current target | 10 s |
| `charge` (4 clips) | `charge_start` 0.5 s (optional tell) → `charge_loop` 0.5 s loop while running at the **farthest** player for up to **10 s** → if reached: `charge_slash` 1.0 s with **0.3 s** → cone 90° × 4 at the stop point, knock-up; if not reached in 10 s: `charge_exhaust` 2.5 s, no hit, boss vulnerable. Optional trample while running: rectangle 3 wide along the path, low damage. Wall hit → cancel into `stagger` | 14 s |
| `summon_rot_warts` | 1.6 s | **0.8 s** → spawn 2 `mobs_ld:rot_wart` at the `fx_chest` rest offset (≈ 0.75 forward, 2.6 up; the server cannot read bones) | 25 s (18 s in phase 2) |

Tells: `vertical` lifts the right leg and raises the fist overhead; `horizontal` twists the chest to its right
with the fist out to the side and no leg lift; `combo` flares flame particles at `fx_fist_right` at 0.15 s;
`charge_start`/`charge_loop` crouch and run with the blade tip dragging behind on the floor (cloud particles at `hit_blade_right` twice per loop).

Client effects are inside the clips (sound, particle with `count=…; spread=…` scripts, `shake`). Vanilla
particle ids only: explosion, sweep_attack, crit, flame, lava, cloud, smoke.

## Phases
- Phase 1 (100–50 %): all five abilities. Phase 2 (< 50 %): `enrage` (invulnerable 1.8 s) → texture
  `gorefist_phase2.png` (not built yet; use the base until then), melee damage taken −66 %, 2 Rot Warts at
  once + 2 after 5 s, attack speed +15 %.
- Soft enrage 7:00 (damage ×2). Leash 32 blocks. Boss HP/damage: Mobs_LD tuning.

## Resolved with Mobs_LD (2026-10-20)
- Abilities are timelines of hit events, so one clip may carry several impact times (phase-2 slam: 1.1 s circle, 1.5 s rays).
- `charge` → wall hit → `stagger` clip, ~1.8 s, hold on last frame. Planned, not built yet.
- Animation file loads as exported (carries `geckolib_format_version: 2`); trailing `;` on timeline instructions is fine.
- LeDok supplied the kit (Remnant reference) on 2026-10-20; clips built from it.
- `slam` was renamed `vertical` (same clip, plus the leg-lift tell and a 6-particle burst).
