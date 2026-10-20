# Rot Wart — working id `mobs_ld:rot_wart` (stub)

| | |
|---|---|
| Role | add, summoned by Gorefist (`summon_rot_warts`), 2 at a time |
| Size | hitbox 0.8 × 0.9 blocks, knee-high |
| Movement | sprints at the **nearest** player, faster than a sprinting player |
| Threat | ignores threat, always nearest |

## Fantasy
Swollen pustules that budded off the brute's crystal-rotted flesh and crawl on stubby legs. They pop.

## Behaviour
Runs at the nearest player; on contact or after 6 s it bursts: circle r 2.5, heavy damage, leaves a **rot cloud**
(area effect, 4 s, r 2, poison/wither-style) where it burst. Fodder HP; the burst still happens when killed
at melee range, so ranged kills are the clean answer.

## Animations
`idle` (loop, pulsing), `run` (loop, 1.0 block per cycle), `burst` (0.6 s, impact 0.5 s), `death` = burst.

## Model plan
One bloated body cube with a glowing core (glowmask), two stubby legs, a few wart cubes. ~8 cubes, 64×64.
Not built yet — Gorefist first.
