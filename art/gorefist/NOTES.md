# Gorefist — hand-off notes (`mobs_ld:gorefist`)

Status: **blockout v4** (2026-10-20): look C (deepslate + amethyst), blade thinned to 4 units, phase-2 amethyst
shell as real geometry grown by `enrage`. 15 clips. Textures: Minecraft-style pass (see below). Full concept: `concept.md`.

## Model
- Hitbox: **2.5 × 4.0** blocks (width × height). Eye height ≈ 3.4 blocks (head pivot at y = 50/16).
- Visual extent: x −35 … +20 (the arm overhangs ~0.9 block to the creature's right), 60 units tall, z −31 … +12
  (the blade tip rests on the floor at about 1.4 blocks ahead of the origin). Faces north, origin at the centre of the feet.
- Textures 256 × 256, box UV, 1 texel per unit, look C: `gorefist.png` + `gorefist_glowmask.png` (eyes, blade cracks)
  and `gorefist_phase2.png` + `gorefist_phase2_glowmask.png` (same, plus the shell region painted; shell spikes glow).
- **Shell = phase-2 geometry.** Bones `shell_chest` (under chest), `shell_head` (under head), `shell_arm_right` (under
  arm_right), 13 cubes. Their texels are **alpha 0 in the base texture**, so they must be invisible in phase 1 — this relies
  on GeckoLib discarding fully transparent texels (cutout). The `enrage` clip scales the three shell bones 0.01 → 1.12 → 1
  between 0.4 and 1.3 s; no other clip keys them. If the texture swap happens at the start of `enrage`, the shell grows
  out of the hide; if it happens later, it pops in at full size.
- Bones: `root` › `body` › `chest` › `head` › `jaw`; `chest` › `arm_right` › `forearm_right` › `blade_right`; shell bones above;
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
| `enrage` | 1.8 s | once | phase-2 transition; shell grows 0.4–1.3 s; crit sparkle at `fx_chest` 0.6 s (`shake 0.3 12`), chest beat 1.2 s |
| `stagger` | 1.8 s | hold | after a wall-hit charge; reels back, fist drops, slumps; hold until released |
| `death` | 2.0 s | hold | falls forward onto the fist, topples to its left; sound 0.0 / 1.2, `shake 0.4 12` at 1.2 |

## Ability clips (hit events)
| clip | length | hit events (time → shape, centre, target) | cooldown |
|---|---|---|---|
| `vertical` | 2.2 s | **1.1 s** → rectangle 2 × 5 blocks from the boss forward (fist lands ~3.3 blocks ahead), current target | 4 s |
| `horizontal` | 1.8 s | **1.0 s** → cone 150°, 4.5 blocks, centred on the boss, current target; applies `spell_engine:bleed` | 6 s |
| `combo` | 2.4 s | **0.6 s** → cone 60° × 4 (right jab); **1.1 s** → cone 60° × 4 (left backhand); **1.7 s** → rectangle 2 × 5 (overhead chop); all current target | 10 s |
| `charge` (4 clips) | `charge_start` 0.5 s → `charge_loop` homing run at the **farthest** player for up to **10 s** (engine: 0.32 blocks/tick, turn 5°/tick, so it can be sidestepped or baited into walls) → within 3 blocks: `charge_slash` 1.0 s with **0.3 s** → cone 90° × 4 at the stop point, knock-up; after 10 s without reaching: `charge_exhaust` 2.5 s, no hit, free punish window. Trample while running: 3 wide, low damage, once per player. Wall hit → `stagger` | 14 s |
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
- v3 (look B, blade-arm, 4-clip charge) committed by Mobs_LD as 9384ec5; action clips honour the file's loop modes.
- v4 (look C, thin blade, phase-2 shell geometry) committed by Mobs_LD as d31371f. LeDok: more changes will follow later.
- Shell hiding confirmed against GeckoLib 4.9.3 source: default render type is entityCutoutNoCull, which discards alpha-0 texels.
  The phase-2 texture swaps at the start of the transition, so the shell grows in with the `enrage` scale keys.
- Soft enrage (7:00) also plays `enrage`: in phase 2 the shell briefly shrinks and regrows; in phase 1 it stays invisible.
  If that pulse looks wrong in game, add an `enrage_soft` clip that does not key the shell bones.
- `slam` was renamed `vertical` (same clip, plus the leg-lift tell and a 6-particle burst).
- 2026-10-20 texture pass: Minecraft-style materials (deepslate, cobbled/polished deepslate, amethyst, calcite, iron)
  from `tools/mc_texture.py`, per LeDok's "blend with Minecraft" direction; rules in `_references/STYLE.md`.
  Same UV, geometry and clips. Rot Wart burst/death: one particle per timestamp (0.5 / 0.52 / 0.54) for GeckoLib.
- 2026-10-20 first in-game fight test (Mobs_LD 8911d85, includes the Minecraft-style textures): confirmed intro, vertical,
  horizontal, charge_start → charge_loop → charge_slash, wall → stagger (code path), Rot Wart summon + burst, phase-2
  shell growth with the texture swap, death hold + cleanup. Not yet seen on screen: `charge_exhaust`, the `stagger` pose.
- 2026-10-20 particles reworked for Mobs_LD 2c33e44 (`spread` horizontal only, new `spread_y`): slam explosions cut to 1–2 sprites
  at the impact + a flat `minecraft:poof` dust ring; chest/head effects got upward scatter.
