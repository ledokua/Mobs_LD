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
A brute from one of the dying worlds that did not slip through the barrier — it cut through. Where it broke
the barrier, barrier-stuff crystallised over the arm it used: the right forearm is now fused into a thin
amethyst cleaver, far too heavy for the body, its tip dragged across the floor. Its hide is deepslate-grey
stone-flesh; when it is hurt enough, amethyst erupts through it as a thin shell (the Mantle of Thorns).
It cannot stop swinging because the arm is the only thing it still trusts. For Eden's people it is the first proof that the barrier is
failing, which is why it opens the boss ladder.

Inspiration is *Gorefist* (Remnant: From the Ashes) only at the fantasy level — a savage hulk that fights with
one monstrous arm. Nothing of Gunfire's model, texture or silhouette details is reused: our brute is squat and
wedge-shaped, the blade is grown out of the arm rather than held, the head is tusked and set low between the shoulders.
**Look C** (Minecraft deepslate + amethyst) chosen by LeDok on 2026-10-20, replacing look B (charcoal + ember).

## Silhouette & read
- **One shape:** a wedge. Huge right shoulder sloping down into a cleaver-arm whose tip rests on the ground;
  tiny wiry left arm; short thick legs. Top-heavy and lopsided from any angle.
- **One accent:** amethyst purple (blade, eyes, phase-2 shell) against deepslate grey.
- **One tell:** the blade leaves the ground. Resting = safe. Lifted = something is coming; how it lifts says what
  (leg up + blade overhead = vertical, chest twisted + blade out to the side = horizontal, red flare = combo,
  crouch with the blade dragging = charge).
- Must not read as: Iron Golem (keep it hunched, asymmetric, dark), Ravager (no quadruped stance), Warden (no
  antlers/ribcage glow — our glow is the blade, eyes and, in phase 2, the shell edges).

## Palette (look C, base texture 256×256)
| use | colour |
|---|---|
| hide (deepslate) | `#4a4a50` / highlights `#606068` |
| stone plates: belly, feet, jaw, hand | `#686870` / `#7c7c84` |
| amethyst (blade, shell) | `#995cdb`, light `#cba0ff`, deep `#6437aa` |
| glow (`_glowmask.png`): eyes, blade cracks, shell spikes in phase 2 | `#debeff` |
| tusks, toes (calcite) | `#dfe0dc` |
| iron shackle remains | `#2c2c32` |

Phase 2 texture (`gorefist_phase2.png` + `_glowmask`): identical, plus the **amethyst shell** region painted. The
shell is real geometry (bones `shell_chest`, `shell_head`, `shell_arm_right`, 13 cubes) that is fully transparent
in the base texture, so it is invisible until the texture swap; the `enrage` clip scales those bones from 0 to 1
so the shell visibly grows out of the hide. Looks A and B are kept in `variants/` for reference only.

## Behaviour
Kit from LeDok's reference (Remnant's Gorefist: vertical, horizontal, 3-hit combo, charge-and-slash, Rot Wart
reinforcements, enrage with the Mantle of Thorns), translated to our fist rig and the MMO threat model.
Idle in the arena, fist on the floor. On aggro: `intro` roar (2 s), then it chases the current target
relentlessly and never kites. Melee swings have deceptively long reach (the arm is long): every frontal shape
is drawn 0.5 block longer than the model suggests. Leash 32 blocks. Soft enrage at 7:00 (damage ×2).

### Phase 1 — Breacher (100 → 50 % HP)
| ability | type | shape / size | centre | target | clip | impact | cooldown | counter |
|---|---|---|---|---|---|---|---|---|
| `vertical` | melee chop (overhead) | rectangle 2 × 5 | from boss, forward | current | 2.2 s | 1.1 s | 4 s | tell: **right leg lifts** + fist overhead; sidestep any direction |
| `horizontal` | melee sweep | cone 150°, 4.5 | boss | current | 1.8 s | 1.0 s | 6 s | slower, **no leg lift**; applies **bleed** (`spell_engine:bleed`, already in the pack); step *into* it / behind |
| `combo` | 3 hits in one clip | cone 60° × 4 → cone 60° × 4 (left hand) → rectangle 2 × 5 | boss | current | 2.4 s | 0.6 / 1.1 / 1.7 s | 10 s | tell: **red flare** on the fist at 0.15 s; back off, don't trade |
| `charge` | run + uppercut | runs at the target for up to **10 s** (`charge_loop`); reached → `charge_slash`, cone 90° × 4 at the stop; not reached → `charge_exhaust` (vulnerable 2.5 s) | at the stop point | **farthest** player (MMO twist: the original fires when you are far) | loop + 1.0 s | **0.3 s** into `charge_slash` | 14 s | kite it for 10 s and it exhausts; wall hit → `stagger` 1.8 s |
| `summon_rot_warts` | summon | 2 adds at `fx_chest` | boss | — | 1.6 s | 0.8 s (spawn) | 25 s | kill or kite the Warts before they reach the healers |

### Phase 2 — Mantle of Thorns (50 → 0 % HP)
`enrage` 1.8 s, invulnerable, swaps to `gorefist_phase2.png` (the glow cracks flare, the thorns on the back
light up). Effects for the rest of the fight:
- **Mantle of Thorns:** melee damage taken −66 % (the original says "reduced by 200 %"); ranged and spells unchanged.
  Tanks and melee swap to survive-and-kite, casters carry the damage.
- Immediately summons **2 Rot Warts**, and 2 more 5 s later; `summon_rot_warts` cooldown drops to 18 s.
- Attack speed +15 %. Kit otherwise unchanged.

### Rot Wart (add, needs its own small model — `mobs_ld:rot_wart`)
Knee-high bloated runner. Sprints at the **nearest** player, explodes on contact or after 6 s: circle r 2.5,
heavy damage, leaves a lingering **rot cloud** (poison/wither-style area effect, 4 s, r 2) where it burst.
Fodder HP; the point is that someone has to deal with them. Concept stub in `../rot_wart/concept.md`.

### Group roles
Tank: holds it facing away; `vertical` and `horizontal` both have readable tells, `combo` is the "walk away"
moment. Ranged/healers: `charge_slash` comes for the farthest player, Rot Warts come for the nearest.
Melee: after a wall-hit `stagger`, 1.8 s of free hits; in phase 2 melee damage is cut, so switch to
interrupting Warts and let casters finish it.

## Animations
| name | length | loop | notes |
|---|---|---|---|
| `idle` | 2.4 s | loop | breathing, fist resting, head sways |
| `walk` | 1.4 s | loop | **2.0 blocks per cycle**; fist drags, lifts 1 unit on the right step |
| `intro` | 2.0 s | once | lifts fist, roars, slams it back down (`shake 0.3 12`) |
| `vertical` | 2.2 s | once | right leg lifts, blade overhead → floor 3 blocks ahead; impact 1.1 s; `shake 0.6 16` |
| `horizontal` | 1.8 s | once | chest winds to the right, blade sweeps across the front at 1.0 s |
| `combo` | 2.4 s | once | blade flare at 0.15 s; thrust 0.6 s, left backhand 1.1 s, overhead chop 1.7 s |
| `charge_start` | 0.5 s | once | optional crouch tell, blade swings back |
| `charge_loop` | 0.5 s | loop | sprint dragging the blade tip behind on the floor; ~1.5 blocks per cycle |
| `charge_slash` | 1.0 s | once | reached the target: upward slash from the run pose, impact 0.3 s |
| `charge_exhaust` | 2.5 s | once | could not reach anyone in 10 s: skids, bends over panting (vulnerable) |
| `summon_rot_warts` | 1.6 s | once | left hand raised, right foot stomp at 0.8 s (`shake 0.2 10`) |
| `enrage` | 1.8 s | once | rears back, roar, chest beat at 1.2 s; amethyst shell grows 0.4–1.0 s (scale), crit sparkle at `fx_chest` |
| `stagger` | 1.8 s | hold | reels back, blade drops, slumps; held until the engine releases it |
| `hurt` | 0.4 s | once | flinch (keys chest + head only, so walk keeps playing underneath) |
| `death` | 2.0 s | hold | falls forward over the blade, then sideways |

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
53 cubes (13 of them shell), box UV on 256×256, 1 texel per unit. Markers also at `fx_mouth` (roar), `fx_chest` (phase glow), `hit_hand_left`.

## Drops / hooks
Loot: a Mobs_LD boss table (to design with Loot). Natural hook: the first barrier-crystal material for the Eden
story (the fist's crystal), and a trophy head for the hub.

## Engine answers (Mobs_LD, 2026-10-20)
- An ability is a **timeline of hit events**, each with its own shape, time, damage and target. `combo` has three hit events in one clip.
- `charge` ends on a wall hit and switches to a `stagger` clip (1.5–2 s, looping or held) — in scope for v1.
- Blockbench's trailing `;` on timeline instructions is tolerated; AzureLib may stay enabled.
- Blockout v1 loads in the GeckoLib renderer as-is. **No further clips until LeDok confirms the kit.**

## Open questions
- Display name and which arena it opens in.
- Boss HP / damage numbers: left to Mobs_LD tuning.
