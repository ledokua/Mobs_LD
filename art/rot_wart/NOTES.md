# Rot Wart — hand-off notes (`mobs_ld:rot_wart`)

Status: **blockout v1** (2026-10-20). Add summoned by Gorefist (`summon_rot_warts`, 2 at a time; 2 + 2 at the
phase-2 transition). Concept: `concept.md` here.

## Model
- Hitbox: **0.8 × 0.9** blocks (width × height). Eye height ≈ 0.75 blocks. Model is 14 units tall, 14 wide with warts.
- Texture 64 × 64, box UV, 1 texel per unit. Glowmask: crystal core on the front + eyes. Ember variant in `variants/`.
- Bones: `root` › `body` (sac, pivot at its base so scale-pulses grow upward); `root` › `leg_right`, `leg_left`.
  No `head` bone on purpose: it does not look at anything, it just runs.
- Marker bones: `hit_body` (centre of the sac, burst origin), `fx_core` (front of the sac, glow core).

## Clips
| clip | length | loop | notes |
|---|---|---|---|
| `idle` | 1.6 s | loop | sac pulses (scale channel) |
| `run` | 0.5 s | loop | **1.0 block per cycle**; frantic scurry, body leans forward 12° |
| `hurt` | 0.3 s | once | squash |
| `burst` | 0.6 s | hold | ability clip: swells 0–0.45 s, **impact 0.5 s**, model collapses to nothing after the pop |
| `death` | 0.6 s | hold | identical to `burst`: killing it pops it too |

## Ability
| clip | hit events | notes |
|---|---|---|
| `burst` | **0.5 s** → circle r 2.5 centred on the Wart, everyone; then an area-effect **rot cloud** r 2 for 4 s at the same spot (poison/wither-style, id Mobs_LD's call) | trigger: contact with a player, or 6 s after spawn. Despawn the entity right after 0.6 s. |

Behaviour: sprints at the **nearest** player (ignores threat), faster than a sprinting player. Fodder HP.
Sounds: creeper primed hiss while swelling, explode at the pop. Particles: flame at `fx_core` during the swell;
explosion + large_smoke + item_slime at `hit_body` on the pop; `shake 0.2 8`.
