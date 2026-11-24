# Sand Golem Colossus

The Colossus is a level 0–10 Desert Temple boss. The model uses sandstone, gold, turquoise, a carved ankh, and mirrored stonework on both sides.

## Moves

| Clip | Length | Impact | Warning and effect |
| --- | ---: | ---: | --- |
| `summon_husks` | 2.35 s | 1.10 s | A distinct call and spawn runes at 0.40 s warn players to clear the adds. A solo fight gets three husks, plus one per additional fighter, up to ten. The same count caps living husks. The summons drop no loot or XP and leave when the boss is gone. |
| `ground_slam` | 2.70 s | 1.20 s | Sidestep the 3 × 15 block lane locked at 0.40 s. The slam hits once and raises cosmetic sandstone spikes. The golem stays braced through 2.00 s as dust shakes loose and the chest ankh glows, giving melee players time to close in. |
| `horizontal_slam` | 1.75 s | 0.95 s | Move behind the boss or out of the 6-block reach of its 120° frontal sweep. The warning appears at 0.25 s. |

The ground slam reaches a target whose feet are up to 3.5 blocks above the lane's origin or 1 block below. The horizontal sweep reaches 1.75 blocks above or 0.75 below.

The first husk call opens after eight seconds and after the Colossus finishes either slam. When both slams are available, it favors changing attacks over repeating its last move.

Each husk spawns with 10% of the Colossus's current max health (20 HP at baseline). Its own attack scales by the boss's current attack damage divided by 8. It also gains the boss's armor above 6 and copies its DD Loot Stage damage multiplier when available.

## Model and tuning

- Entity ID `mobs_ld:sand_golem_colossus`.
- The model faces north with its feet at Y = 0. The crest reaches 86 model units. The hitbox is 3 blocks wide and 5.25 blocks high.
- The walk loop runs 1.60 s and covers 1.75 blocks per cycle. Idle loops at 2.60 s. Hurt plays once for 0.45 s. Death holds its final frame after 2.30 s.
- The 256 × 256 base texture and emissive mask share the same box UV layout. The editable project embeds the base texture.
- Initial level-0 tuning is 200 HP, 8 raw ground-slam damage, and 6 raw sweep damage. The ground slam, sweep, and summon have 7, 5, and 18 s cooldowns, respectively.
