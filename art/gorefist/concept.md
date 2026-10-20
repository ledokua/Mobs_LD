# Gorefist — working id `mobs_ld:gorefist`

| | |
|---|---|
| Role | **Boss**, first test asset. Not a Guardian or Source: a *breacher* — the first thing big enough to force its way through the weakened barrier into Eden |
| Display name | LeDok's call. Proposals: **The Breacher**, **Knucklebound**, **Hulk of the Breach**, **Varrak the Breaker** |
| Arena | any raid/dungeon arena; it wants ~24×24 blocks of open floor for the leap and charge |
| Size | hitbox **2.5 × 4.0** blocks; model overhangs to the right (the fist) by ~1 block and stands 4 blocks tall hunched |
| Movement | ground; walks to standoff melee range (~3 blocks), gap-closers = leap and charge |
| Threat | normal MMO threat; frontal attacks follow the tank, three abilities deliberately hit people who are *not* the tank |

## Fantasy
A brute from one of the dying worlds that did not slip through the barrier — it punched through. Where it broke
the barrier, barrier-stuff crystallised over the arm it used: the right arm is now a mass of pale crystal fused
into scar tissue, far too heavy for the body, dragged knuckles-down across the floor. It cannot stop punching
because the arm is the only thing it still trusts. For Eden's people it is the first proof that the barrier is
failing, which is why it opens the boss ladder.

Inspiration is *Gorefist* (Remnant: From the Ashes) only at the fantasy level — a savage hulk that fights with
one monstrous arm. Nothing of Gunfire's model, texture or silhouette details is reused: our brute is squat and
wedge-shaped, the arm is crystal-grown rather than fleshy, the head is tusked and set low between the shoulders.

## Silhouette & read
- **One shape:** a wedge. Huge right shoulder sloping down to a fist the size of the torso resting on the ground;
  tiny wiry left arm; short thick legs. Top-heavy and lopsided from any angle.
- **One accent:** pale cyan crystal (fist, knuckle shards) against dark red-brown hide.
- **One tell:** the fist leaves the ground. Resting = safe. Lifted = something is coming; how high it goes says what
  (shoulder height = haymaker, over the head = slam, crouch with fist behind = leap, fist forward like a ram = charge).
- Must not read as: Iron Golem (keep it hunched, asymmetric, dark), Ravager (no quadruped stance), Warden (no
  antlers/ribcage glow — our glow is in the arm only).

## Palette (base texture, 256×256)
| use | colour |
|---|---|
| hide (main) | `#5a2a22` / highlights `#7a3b2e` |
| scar tissue, underbelly | `#b8907c` / `#c9a08a` |
| crystal (fist, shards) | `#9fdce8` / `#bfeff5`, deep `#5aa9bd` |
| glow (`_glow.png`): eyes, crystal cracks | `#e6ffff` |
| tusks, claws | `#e8dcc0` |
| iron shackle remains on left wrist / ankles | `#4a4a52` |

Phase 2 texture (`gorefist_phase2.png`): crystal creeps up over the right shoulder and across the chest; cracks
in the hide glow. Phase 3 reuses phase 2 with a brighter glow layer if cheap, otherwise the same texture.

## Behaviour
Idle in the arena, fist on the floor, slow breathing. On aggro: `intro` roar (2 s), then walks toward the tank to
standoff range and cycles frontal attacks, mixing in one "move the group" ability every ~15 s.
Leash: resets if pulled more than 32 blocks from the arena centre. Soft enrage at 6:00 (see phase 3).

### Phase 1 — Breacher (100 → 60 % HP)
| ability | type | shape / size | centre | target | clip | impact | cooldown |
|---|---|---|---|---|---|---|---|
| `haymaker` | melee | cone 90°, 5 blocks | boss | current (tank) | 1.4 s | 0.6 s | 4 s |
| `slam` | ground slam | circle r 3.5 | 3 blocks in front | current | 2.2 s | 1.1 s | 10 s |
| `leap_slam` | leap + slam | circle r 4 | landing point | **farthest** player | 2.4 s | 1.5 s (0.6 s crouch telegraph, airborne 0.6–1.4 s) | 18 s |

### Phase 2 — Cracked (60 → 25 % HP)
`phase_2_transition` 2.5 s, invulnerable, swaps to `gorefist_phase2.png`. Keeps phase-1 kit and adds:
| ability | type | shape / size | centre | target | clip | impact | cooldown |
|---|---|---|---|---|---|---|---|
| `charge` | dash along a line | rectangle 3 × 14 | from boss, forward | **random** player | 2.0 s | 0.7–1.5 s (moving) | 14 s |
| `slam` (upgraded) | ground slam + rays | circle r 3.5, then rays ×6, length 10 | impact point | current | 2.2 s | 1.1 s circle, 1.5 s rays | 10 s |
| `stagger` | — | — | — | — | 1.8 s, held | — | plays when `charge` hits a wall: boss open to punishment, melee burst window |

Optional adds (needs a second model, later): 3 **Breach Shards** — knee-high crystal crawlers summoned at 50 % and
35 %, 1 ability each (lunge, cone 60° × 2 blocks). Skip for the first version.

### Phase 3 — Frenzy (25 → 0 % HP)
`enrage` 1.5 s. Attack speed +30 %, movement speed +20 %, `leap_slam` cooldown 12 s. Adds:
| ability | type | shape / size | centre | target | clip | impact | cooldown |
|---|---|---|---|---|---|---|---|
| `quake` | targeted ground AoE ×4 | circle r 3 each | where each target stood | **4 random** players | 2.6 s | 1.3 s | 12 s |

Soft enrage at 6:00: damage ×2 and `quake` every 6 s.

### Group roles
Tank: faces it away from the group; every frontal attack has a 0.6–1.1 s windup to sidestep or block.
Healers/ranged: `leap_slam` chases the farthest player, `charge` picks a random one, `quake` marks four — nobody
stands still. Melee: the `stagger` after a wall-hit charge is the burst window; phase 3 is the DPS race.

## Animations
| name | length | loop | notes |
|---|---|---|---|
| `idle` | 2.4 s | loop | breathing, fist resting, head sways |
| `walk` | 1.4 s | loop | **2.0 blocks per cycle**; fist drags, lifts 1 unit on the right step |
| `intro` | 2.0 s | once | lifts fist, roars, slams it back down (`shake 0.3 12`) |
| `haymaker` | 1.4 s | once | arm swings across the front, impact 0.6 s |
| `slam` | 2.2 s | once | fist overhead → floor 3 blocks ahead; impact 1.1 s; `shake 0.6 16`; particles at `hit_fist_right` |
| `leap_slam` | 2.4 s | once | crouch, jump (position +Y), land fist-first at 1.5 s; `shake 0.8 20` |
| `charge` | 2.0 s | once | fist forward like a ram, legs sprint, body low |
| `quake` | 2.6 s | once | both fists pound the floor, 1.3 s |
| `phase_2_transition` | 2.5 s | once | clutches the arm as crystal spreads, roar |
| `enrage` | 1.5 s | once | roar, chest beat with the small arm |
| `stagger` | 1.8 s | hold | reels back, fist drops; held until the engine releases it |
| `hurt` | 0.4 s | once | flinch |
| `death` | 2.0 s | hold | falls forward onto the fist, then sideways |

## Model plan
```
root
└ body (hips)            pelvis
  ├ chest                barrel chest, hunched ~18°, spine ridge
  │ ├ head               low between shoulders, brow, tusked lower jaw (bone `jaw` for the roar)
  │ ├ arm_right          giant shoulder boss + upper arm (crystal creeping on)
  │ │ └ forearm_right    thick forearm
  │ │   └ fist_right     fist bigger than the head, crystal knuckle shards (rotated cubes); locators hit_fist_right, fx_fist_right
  │ └ arm_left           thin upper arm
  │   └ forearm_left     forearm + hand, iron shackle cuff; locator hit_hand_left
  ├ leg_right            short thick leg + foot (toes)
  └ leg_left
```
~30 cubes, box UV on 256×256, 1 texel per unit. Locators also at `fx_mouth` (roar) and `fx_chest` (phase glow).

## Drops / hooks
Loot: a Mobs_LD boss table (to design with Loot). Natural hook: the first barrier-crystal material for the Eden
story (the fist's crystal), and a trophy head for the hub.

## Engine answers (Mobs_LD, 2026-10-20)
- An ability is a **timeline of hit events**, each with its own shape, time, damage and target. The phase-2
  slam is one ability with two events: circle at 1.1 s, rays at 1.5 s.
- `charge` ends on a wall hit and switches to a `stagger` clip (1.5–2 s, looping or held) — in scope for v1.
- Blockbench's trailing `;` on timeline instructions is tolerated; AzureLib may stay enabled.
- Blockout v1 loads in the GeckoLib renderer as-is. **No further clips until LeDok confirms the kit.**

## Open questions
- Display name and which arena it opens in.
- Boss HP / damage numbers: left to Mobs_LD tuning.
