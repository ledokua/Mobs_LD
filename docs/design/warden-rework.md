# Warden rework: proposal for approval

Status: **approved by the owner on 2026-11-03** (open questions: flat arena floor yes, resonator look yes, loot decided
later with Eden Loot, the rest at their defaults). Nothing is built yet. Phase structure A ("The Hunt") with the owner's
Sonic Boom Circle, built on the Wither R1 engine. Only Wardens spawned by Arenas_LD get the kit.

Owner decisions and where they are:

| # | Decision | Section |
|---|---|---|
| 1 | Boss of an Arenas_LD raid (4-5 players) and dungeon (smaller groups), always in its own arena | 1, 5 |
| 2 | World Wardens stay 100% vanilla; kit gated per instance by the run tag; no `base_attributes` | 3, 4.1 |
| 3 | One kit, scaled by player count | 2.6, 5 |
| 4 | Vanilla model, vanilla clips only; tells from telegraphs, particles, sounds | 2, 4.6 |
| 5 | Fair for melee and ranged: no auto-swing, near and far mechanics, role windows, threat | 2.5 |
| 6 | Sonic Boom Circle: 8 + 8 lines, rotated 22.5°, widening lanes, third wave in P3 | 2.2 |
| 7 | Phase structure A | 2.1 |
| 8 | New damage type `mobs_ld:warden_sonic`: armor yes, shield no; swings stay `mob_attack`; no shield disabling | 4.8 |
| 9 | Sculk Resonators, x0.5 damage while alive, 2 + 1 per extra fighter, cap 4 | 2.3, 4.9 |
| 10 | Never digs away, never discarded except by death, Arenas or reset rules | 4.5 |
| 11 | Darkness only from abilities; cues on the action bar | 2.1, 4.3 |

---

## 1. Pitch

The Warden keeps its vanilla entity, model, clips, sounds and death. Inside an Arenas raid or dungeon, Mobs_LD's
ability engine runs the fight.

- **It hunts by sound.** Between abilities it walks at the tank like vanilla. It never auto-swings. Every hit it
  deals is telegraphed on the ground and read from a vanilla clip: `sonic_boom` = sonic attacks, `roar` = waves,
  `sniff` = the hunt, `dig` / `emerge` = the ground, tendril twitch plus a growl = a physical swing.
- **Nobody is safe by distance alone.** Near-band mechanics (Resonant Pulse, the Circle's inner lanes, Sculk
  Backlash) cost melee uptime. Far-band mechanics (Sonic Lance on the farthest player, Tremor Line, Deafening Rings'
  outer rings, Burrow Ambush) cost ranged uptime. The mid band (6-10 blocks) is hit least; healers live there.
- **Sonic Boom Circle** (the owner's attack). Eight sonic lines burst out from its chest, then a second set of eight
  rotated 22.5°. Lanes widen with distance, so everyone has to move between the waves. In P3 a third wave follows.
- **60% HP.** It roars: Darkness for 6 s and Sculk Resonators rise around the arena. While any stands, it takes half
  damage. Breaking them is a ranged job.
- **25% HP.** It burrows, comes up in the arena's heart, and its chest opens for 3 s. P3 is faster and adds
  Listening Silence ("be still").
- **Threat matters.** Big physical hits follow the tank, shields block them, and taunts move them. With 3+ players a
  Sculk Brand on the tank forces a tank swap.

Every hit is an absolute number at level 0 / Normal. Arenas owns HP and tier scaling; Eden Loot calibrates with one
`loot_stage:damage_dealt` value set on the Arenas spawner.

---

## 2. Phases and abilities

How to read the damage column:
- Damage is raw points at level 0 / Normal, before `damage_dealt` and armor (`hit.amount`).
- At run time each hit is x R, where R = the Warden's live attack_damage / 30 (the reference declared in
  `warden.json`). The Arenas spawner sets no attack_damage row, so R = 1. Weakness on the Warden lowers R (a support
  tool); Strength raises it.
- S = `mobs_ld:warden_sonic` (armor applies, shields do not block). P = `minecraft:mob_attack` (armor applies,
  shields block, never disabled).
- The global cooldown between abilities is 0.8 s.
- "Fixed" = the timeline ignores the phase's attack_speed, because the vanilla clip it is timed to cannot speed up
  (section 4.6).

### 2.1 Phase structure A, "The Hunt"

| Phase | HP | Heartbeat | Kit |
|---|---|---|---|
| Opening | full | vanilla | The Arenas spawn emerges from the ground (6.7 s, invulnerable). Vanilla wakes it: vibrations, a hit, then its vanilla roar. From the first hit on, threat drives the target |
| **P1 Stalker** | 100-60% | slow | Crushing Blow, Rending Combo, Sculk Backlash, Sonic Boom Circle (only when the tank is within 8), Resonant Pulse, Sonic Lance (14 s), Tremor Line, Hunting Sniff → Deep Charge |
| **Roar of the Deep** | at 60% | | Invulnerable 4.2 s roar: shove r10, Darkness 6 s within 40, Sculk Resonators rise |
| **P2 Resonance** | 60-25% | medium | P1 kit plus Deafening Rings, Echo Mines, Burrow Ambush (30 s), Sculk Brand on tank hits (3+ fighters), Sonic Volley sharing the Lance cooldown (10 s), Circle at any range, Sculk Pools after Pulse and Burrow, one more Resonator raise 45 s into P2 |
| **Heart of the Deep** | at 25% | | Burrows, emerges at the arena centre, Chest Open 3 s (x1.25 damage taken) |
| **P3 Frenzy** | 25-0% | fast | P2 kit without the Resonator raise; attack_speed 1.2 for the physical abilities; Triple Circle; Listening Silence (40 s); Lance group cooldown 7 s |

- Phases latch downward and never return.
- No Darkness pulse while hosted. Darkness comes only from Roar of the Deep (6 s). The Circle cannot start until
  7 s after that roar, so the owner's signature attack never runs in Darkness.
- No soft enrage. Arenas' run time limit (600 s) is the enrage.
- No boss bar from Mobs_LD. The Arenas run HUD hides vanilla boss bars and shows the boss HP % in its own strip. Every
  cue goes to the action bar (the `fx` message), as for the Wither. Confirmed by the Arenas_LD session (4.14.0): the run
  HUD is its own packet (RunHudSync), not the action bar. Only downed players spectating a teammate get an Arenas
  action-bar line (every 2.5 s), which can replace a cue for them.

### 2.2 Ability table

| Ability | Phase | Telegraph and clip | Counterplay | Dmg L0 raw | Cooldown |
|---|---|---|---|---|---|
| **Crushing Blow** (M1) | all | tendril twitch + `warden.angry` growl at 0; red rectangle 2.5 x 4.5 ahead from 0.0; roll cue 0.7; `attack` cue at impact 1.0 | sidestep, roll, or block with a shield | P 12.0, kb 0.6 | 6 s |
| **Rending Combo** (M2) | all | tendril twitch; red cone 100° r3.5 hits at 1.0; red rectangle 2 x 6 re-aimed at the tank, hits at 2.0; `attack` cue on each | read both shapes; step behind on beat 2 | P 7.0 + 11.0 | 9 s |
| **Sculk Backlash** (M3) | all | only when 2+ players stood behind it for 4 s: sculk burst from its back, heartbeat sound; rear cone 140° r4.5, 1.1 s | do not stack behind it | S 9.0 | 12 s |
| **Sonic Boom Circle** (M4) | P1 (tank within 8), P2 | section 2.2.1 | roll each wave, or move between waves | S 10.0 per wave | 16 s, no repeat, Fixed |
| **Triple Circle** | P3 (replaces the Circle) | as the Circle, third wave at the wave-1 angle | step back out | S 10.0 per wave | 16 s, Fixed |
| **Resonant Pulse** (M5) | P1 | `sonic_boom` clip; teal circle r6 fills 0.2-1.7; roll cue 1.4; burst at 1.7 | walk out (1.5 s), or roll; ranged get a free 1.3 s on a rooted target | S 14.0, knock-up 0.5 | 14 s, tank within 5, no repeat, Fixed |
| Resonant Pulse, pooled | P2, P3 | as above, then a Sculk Pool r3 for 8 s | fight outside the pool | same + Slowness II in the pool | same |
| **Sonic Lance** (M11) | all | `sonic_boom` clip; it listens (sculk particles at the target 0.2, 0.5, 0.8); teal strip 2.5 x 40 with arrowhead locks at 0.9; roll cue 1.4; fires 1.7 through walls | sidestep 1.6 blocks in 0.8 s, or roll; melee get 3 s on a rooted Warden aimed away | S 15.0, kb 1.2 | group `lance`: 14 / 10 / 7 s (the ranged-pressure knob), no repeat, Fixed |
| **Sonic Volley** (M12) | P2, P3 | one `sonic_boom` clip; three lances at 1.7 / 2.2 / 2.7, each re-aimed when its strip appears (0.8 s each) | strafe; roll the last | S 8.0 x3 | group `lance`, Fixed |
| **Tremor Line** (M9) | all | growl; `attack` cue into the ground at 0.5; 6 blue circles r2 walk toward the `listener` slot's player, 2 blocks apart, impacts 1.3-2.8, each drawn 0.8 s ahead | step sideways (backing away loses) | P 7.0 per circle, once per victim | 12 s |
| **Hunting Sniff** (M14) | all | `sniff` clip, 4.2 s rooted; at 3.8 Glowing 8 s on the player with the most damage in the last 10 s, action bar "The Warden has caught a scent" | melee window; the marked player moves to a wall | none | 30 s, first at 20 s |
| **Deep Charge** (M13) | chained from Sniff | `roar` pose (head back); blue strip with arrowhead, body width, from 0.2; `attack` cue at 1.2 ends the roar clip and it runs at 0.9 blocks/tick | sidestep or roll; bait it into a wall: 4.2 s stagger (`sniff` clip) | P 16.0, side push 0.8 | via Sniff |
| **Roar of the Deep** (M6) | 60% transition | `roar` pose 4.2 s, invulnerable; roar sound 1.25; shove r10 at 1.6 (drawn from 0.6); Darkness 6 s within 40 at 1.6; Resonator runes 1.0, rise 2.8 | get pushed, then find the Resonators (they glow) | S 3.0, kb 1.2 | once |
| **Sculk Resonators** (M15) | from 60% | runes 1.8 s ahead, 14-18 blocks from the arena centre; glowing; shriek every 3 s | break them; x0.5 damage on the Warden while any stands | none | transition + one raise 45 s into P2 (2+ fighters) |
| **Deafening Rings** (M7) | P2, P3 | `roar` pose; teal rings 6-9 at 1.6, 12-15 at 2.2, 18-22 at 2.8, each drawn 1.0 s ahead | hug (inside 6 is safe); far players step across a ring after it fires | S 9.0 per ring | 18 s, Fixed |
| **Echo Mines** (M10) | P2, P3 | `sniff` clip 4.2 s rooted; `listening_angry`; under each fighter a teal circle r2.5 placed where they are heading (0.6 s lead), drawn at 0.6 / 1.8 / 3.0, hits 0.8 s later | keep moving; free DPS on a rooted Warden | S 5.0 x3 | 20 s, Fixed |
| **Burrow Ambush** (M8) | P2, P3 | section 2.2.4 | the marked player leaves the circle; everyone gets a 5.5 s window at x1.3 | S 17.0, knock-up 0.8 | 30 s (P3 35 s), farthest at 10+, Fixed |
| **Sculk Brand** (M16) | P2, P3, 3+ fighters | stacks on every Crushing Blow and Rending hit on a player: +12% from the Warden per stack, max 4, 15 s; the branded player gets an action-bar line and sculk particles | taunt swap at 3 stacks | multiplier | on hit |
| **Chest Open** (M17) | Circle tail, Lance tail, Burrow rise, 25% transition | heartbeat sound, `sculk_charge_pop` at the chest | hit it | x1.25 (Burrow rise x1.3) | with them |
| **Listening Silence** (M18) | P3 | `sniff` clip; action bar "Be still..."; tendril twitches; from 1.0 to 3.0 it listens | stop attacking, casting, sprinting, jumping, rolling | S 13.0 on each offender; then a Lance on the first one | 40 s, first at 10 s into P3, Fixed |
| **Sculk Pools** (M19) | P2, P3 | teal ground cloud r3, 8 s, after pooled Pulse and Burrow | do not kite through them; the tank drags it out | Slowness II, no damage | with them |
| **Heart of the Deep** | 25% transition | dig 0-2.0, under 2.0-4.2, emerges at the arena centre; shove r6 at 4.2; Chest Open 4.2-7.2 | stay out of r6; then burst it | kb 1.0, no damage | once |

#### 2.2.1 Sonic Boom Circle (owner's attack, decision 6)

Geometry:
- Wave 1: 8 lanes, 45° apart, one on the facing (the tank). Wave 2: the same 8 rotated +22.5°, so each wave-2 lane
  runs down the middle of a wave-1 gap.
- Lane width = 2.5 + 0.3 per block from the Warden (`widen: 0.3`). Length 26. Height band +4 / -2 around the floor
  under the Warden.
- Hit test: the victim's feet centre inside a lane, as for every `hit`. Lanes pass through walls (vanilla sonic boom
  does).

| Distance | Lane width | Wave-1 free gap | Share of the ring a wave-1 lane covers | Step to clear wave 2 from a gap's centre | Spot safe from both waves |
|---|---|---|---|---|---|
| 2-4 | 3.1-3.7 | none | 100% | 1.6-1.9 | none |
| 6 | 4.3 | 0.3 | 91% | 2.2 | none |
| 8 | 4.9 | 1.2 | 78% | 2.5 | none |
| 12 | 6.1 | 3.1 | 65% | 3.1 | none |
| 16 | 7.3 | 4.9 | 58% | 3.7 | none |
| 20 | 8.5 | 6.8 | 54% | 4.3 | none |
| 24 | 9.7 | 8.7 | 51% | 4.9 | none |
| 26 | 10.3 | 9.6 | 50% | 5.2 | none |

No spot is safe from both waves within 27.8 blocks (a point between two lanes of different waves is 0.195 x d from
each, against a half-width of 1.25 + 0.15 x d). The lanes stop at 26, so the arena floor should end within about
24-26 blocks of where the Warden fights (question 1). A player past 26 blocks is outside the Circle, but the Lance
picks the farthest player.

Timeline (`sonic_circle`, duration 5.0 s, rooted, Fixed):

| t (s) | Players see and hear | Engine |
|---|---|---|
| 0.00 | it plants its feet; `sonic_boom` clip (chest crouch); `warden.sonic_charge`; tendril twitch | ability clip; `fx`; facing locks at 0.3 |
| 0.30 | wave-1 lanes (teal) sweep outward to 26 blocks | `hit` rays 8, `widen` 0.3, telegraph `sweep` from 0.3 |
| 1.40 | roll cue: high tendril click | `fx` |
| **1.70** | **wave 1 fires**: the clip's chest burst; `warden.sonic_boom`; sonic particles along all 8 lanes | `hit`; `fx` particles `along` |
| 1.70 | the clip restarts (second crouch); the body turns 22.5° over 0.3 s; `sonic_charge` at pitch 1.25; wave-2 lanes (cyan) sweep out over the gaps | `clip` event; `rotate: 22.5` with `turn_body` |
| 3.10 | roll cue | `fx` |
| **3.40** | **wave 2 fires** | `hit` |
| 3.40-5.00 | rooted recovery, chest open: x1.25 damage taken (1.6 s melee window) | `vulnerable` |

- The clip's release is at 1.7 s. Restarting the clip as wave 1 fires puts wave 2's release exactly 1.7 s later.
- **Triple Circle (P3):** a third clip restart at 3.4, wave 3 at 5.1 at the wave-1 angle (`rotate: 0`, the body turns
  back), recovery 5.1-6.7. Players who stepped into a wave-1 lane to dodge wave 2 must step out again, into a lane
  wave 2 has just burned.
- Caught by both waves: 47% of a typical L15 player; by all three: 70%. A roll through a wave costs nothing.
- How it plays:
  - Tank at 2-3 blocks: on a lane by definition. Roll twice, or back out to about 6 during the 1.4 s telegraph and
    come back for the 1.6 s window. About 3 s of uptime or two roll charges.
  - Melee DPS beside it: the same.
  - Mid range 6-12 (healers, casters): tight gaps; stepping out of a lane sideways is easier.
  - Far ranged 16-24: about half the ring is a wave-1 lane, and every wave-2 lane needs a 3.7-4.9 block step in
    1.7 s. A real uptime tax on casters and bows.

#### 2.2.2 Tank swings (no auto-swing, decision 5)

- Vanilla melee (30 damage every 0.9 s, disables shields) is off. Vanilla sonic boom is off. The engine owns all
  damage.
- Crushing Blow and Rending Combo aim at `current` (the threat top or taunt lock). Only the tank eats them. They are
  `mob_attack`, so a shield blocks them, and the Warden never disables the shield.
- Between abilities it walks into melee range and stands. It does not swing.

#### 2.2.3 Sonic Lance and Volley (far band)

- Target `farthest`, at least 8 blocks away, and players the reach rule gave up on count too (`include_unreachable`),
  so a player on a ledge is still a target. Height band +12 / -4, so the lance reaches ledges.
- The zone locks at 0.9 (0.8 s strip). The engine fixes a telegraph when it appears, so the earlier "listening"
  phase (0.2-0.9) is shown with sculk particles at the target's feet, not a moving strip.
- Lance and Volley share one cooldown (`cooldown_group: lance`); in P2 each is picked about half the time.
- **Ranged-pressure knob:** the `lance` group cooldown per phase (14 / 10 / 7 s). From T3, where ranged DPS leads,
  lower it in the server datapack (for example 10 / 8 / 6). Echo Mines' cooldown is the second knob.

#### 2.2.4 Burrow Ambush (decision 10 holds: nothing is ever discarded)

Duration 11.2 s, target the farthest player at 10+ blocks, Fixed.

| t (s) | Players see | Engine |
|---|---|---|
| 0.0 | `dig` pose (the body sinks), `warden.dig`, dirt particles | pose cue; still vulnerable |
| 2.0 | it is gone (invisible) | burrowed: invisible, invulnerable, no collision |
| 2.0-4.1 | a tremor of sculk particles and dig sounds follows the target every 0.25 s | `burrow.tremor` |
| 4.1 | a blue circle r3.5 locks where the target stands | the emerge spot is validated: solid floor, 3 open blocks, within 22 of home; else the nearest valid spot within 3; else its old spot |
| 4.5 | it is moved there and starts the `emerge` pose | teleport, visible, EMERGING |
| **5.7** | head and shoulders burst out (the clip's 1.2 s mark) | `hit` circle r3.5, S 17.0, knock-up 0.8; pool in P2+ |
| 5.7-11.2 | it rises, rooted; x1.3 damage taken | `vulnerable`; invulnerability off |

- While EMERGING the vanilla hitbox is 1 block tall. Melee and arrows can hit it low. If that plays badly, the fallback
  is to end the pose at 8.4 s (half out): the hitbox becomes full height while the client finishes the clip.
- The Warden is never removed. It stays the same entity, so Arenas never sees "Boss entity disappeared".

#### 2.2.5 Listening Silence (P3)

- 0.0: `sniff` clip, action bar "Be still...", `warden.listening_angry`. 1.0 s of grace to stop.
- 1.0-3.0: the `listen` window. An offender is a fighter within 40 who damages the Warden or a Resonator, starts
  using an item (bows, crossbows, shields; Spell Engine casts if they count as item use, to verify), sprints, jumps,
  or moves more than 0.6 blocks from where they stood at 1.0 (a roll moves about 3).
- Each offender takes S 13.0 at once (sonic particles from the chest to them; one hit each).
- At 4.2 (the clip's end), if anyone offended, it chains into a Lance aimed at the first offender.

### 2.3 Sculk Resonators (decision 9)

- Count 2, +1 per extra fighting player, at most 4. Placed on runes 14-18 blocks from the arena centre (the Warden's
  home), at least 8 apart, on valid floor.
- HP = 1% of the Warden's max health (25 solo at 2,480; 73 for 4 players at 7,316; 110 on a Hard-tier 4-player raid).
  One T1 archer breaks one in about 7 s; the group in 1-2 s.
- They glow (outline through walls and Darkness), shriek every 3 s (sound + vanilla `shriek` particle), and do nothing
  else. No loot, no XP. They leave when the Warden dies or resets.
- While any of its Resonators stands, the Warden takes x0.5 damage from all sources (stacks with Chest Open:
  x0.625).
- Raises: the 60% transition, and once more 45 s into P2 if 2+ fighters (a solo dungeon player has one wave only).

### 2.4 Phase timing at a glance (4 players, T1, Normal tier, 65% uptime)

P1 about 75 s; the roar and Resonators about 15 s; P2 about 70 s; P3 about 45 s. Total 185-212 s (section 5.4).

### 2.5 Fairness (decision 5)

Model: risk follows reward. Unavoidable damage about 1:1 between roles (the only unavoidable hit is the 60% shove,
3.0 raw). Avoidable damage plus lost uptime about 1.5:1 melee:ranged at T1 (melee DPS 21.5 vs ranged 10.5), about
1:1 at T2 (44.1 vs 39.3). From T3 ranged leads; the Lance knob adds far pressure.

P1 estimate per 60 s (about 3 Crushing, 1 Rending, 2 Circles, 2 Pulses, 3 Lances, 2 Tremor Lines, 1 Sniff → Charge),
raw damage a player takes standing where its role stands and dodging nothing:

| Role and spot | Exposure (raw / min) | What it is | Uptime it costs to dodge |
|---|---|---|---|
| Tank, front, 2-3 blocks | 122 (54 of it shield-blockable) | swings 54, Circle 40, Pulse 28 | about 10 s (Pulse walk-outs, Circle) |
| Melee DPS, side, 2-3 | 68 (+9 if stacking behind) | Circle 40, Pulse 28 | about 10 s |
| Far ranged, 16-24, alone | 89 | Circle 22 (half the ring), Lance 45, Tremor 14, Charge 8 | about 5 s (strafes, two Circle steps) |
| Far ranged, two sharing the Lance | 66 | | about 4 s |
| Caster or healer, 6-12 | 43 | Circle 29, Tremor 14 | about 3 s |

Melee average about 97 vs ranged about 78, plus twice the lost uptime: about 1.5:1 at T1, as targeted. In P2 the far
band gains Rings' outer rings, Echo Mines on everyone, Burrow on the farthest and the Lance at 10 s; the ratio moves
toward 1:1 for T2. These are estimates; the playtest (M7) sets the final cooldowns.

Role windows:
- melee: Hunting Sniff (4.2 s rooted), the Lance wind-up (3 s, aimed away), the Circle's tail, the Burrow rise
  (5.5 s at x1.3), wall staggers (4.2 s);
- ranged: Pulse (outside it, 1.3 s rooted), Roar and Rings (rooted 4.2 s), the Circle (rooted 5 s), Echo Mines
  (rooted 4.2 s), and the Resonators are theirs;
- everyone: Chest Open.

Threat:
- the physical hits follow the tank; a taunt moves them, and a taunt during Hunting Sniff moves the Deep Charge onto
  the taunter (taunt lock wins over a chained aim);
- Sculk Brand forces a tank swap with 3+ fighters;
- the `listener` slot (spread rule) aims Tremor Line at a non-tank.

### 2.6 One kit, scaled by player count (decision 3)

All by "fighting players" (threat on the table, alive, not spectating or creative, within 48 blocks):
- HP: Arenas' per-player formula, `hpScalePerPlayer` 0.65 (section 5).
- Resonators: 2 + 1 per extra fighter, cap 4; the P2 re-raise needs 2+.
- Echo Mines: one set per fighter (automatic).
- Sculk Brand: only with 3+ fighters (a duo has no swap partner).
- Sculk Backlash: needs 2+ players behind it, so never solo.
- Lance and Volley: farthest at 8+ blocks. A melee-only duo never sees them and faces the near kit and Burrow.

### 2.7 Changes against the mechanics menu

- **Brain:** the menu said "skip `brain.tick`". Not done; see 4.2.
- **Sculk Pools:** Slowness II only, no damage over time. A ticking pool would be attacker-less damage (Eden_LD hazard
  rules) and needs a custom effect.
- **Sculk Brand:** engine-side stacks on the Warden's hits, not a MobEffect. Milk and cleanses would wipe a MobEffect,
  and it would need an icon (new art).
- **Resonators:** at the 60% transition and once in P2 (the menu said 66% and 33%), to match decision 7.
- **Roar of the Deep:** transition only, so Darkness stays rare and telegraphs stay readable.
- **Deep Charge:** only chained from Hunting Sniff.
- **Lance:** strip locks at 0.9 (0.8 s warning); the 0.2-0.9 tracking is particles, not a moving strip.
- **P3 attack_speed 1.2** applies only to the physical abilities. Clip-timed abilities are Fixed (4.6).
- **Noise threat and an anger-to-threat bridge:** left out. Vibrations are muzzled during the fight, so tendril
  twitches are always a tell. Threat comes from damage, heals and taunts. A later idea.
- **Not used:** an "eye of the storm" safe spot under the Circle, phase options B and C.

---

## 3. What stays vanilla

- **World Wardens (decision 2):** ancient-city shrieker Wardens, the pre-placed ones in ATi Ancient Vessels and
  Warden Monuments, MMR prison Wardens. They have no run tag, so Mobs_LD never attaches. They keep vanilla stats, the
  DD L15 boss rule, melee, sonic boom, Darkness pulse, dig-away, loot (sculk catalyst + 25% Warden Ears), the FTB
  150-kill quest and every advancement. `warden.json` declares no `base_attributes`, and the loader refuses a file that
  combines `only_tagged` with `base_attributes`.
- **Hosted Wardens keep:** id `minecraft:warden`, the Warden class, model, renderer, emissive layers, textures, sounds,
  hitbox (0.9 x 2.9), knockback immunity, fire immunity, the vibration listener (muzzled in the fight), the anger
  system (kept alive), pathing (doors, swimming), and a real, player-credited death with the vanilla loot table
  (Arenas' "Enable drops" decides whether it drops).
- **Changed only for hosted Wardens:**

| Area | Vanilla | Hosted |
|---|---|---|
| Melee | 30 every 0.9 s, disables shields | off; telegraphed swings only; shields never disabled |
| Sonic boom | 10, untelegraphed, every ~5 s | off; Lance, Circle and the rest |
| Darkness | pulse every 6 s within 20 | only from Roar of the Deep |
| Dig-away | after 60 s without stimulus | never |
| Target | anger, roar, ATTACK_TARGET | threat table (taunts work), written to ATTACK_TARGET |
| Follow range | 16 | 48 (host option, this instance only) |
| Pose invulnerability | whole DIGGING / EMERGING | only while burrowed under the ground |

---

## 4. Engine changes in Mobs_LD

All under `src/main/java/net/ledok/mobs_ld/`. Every new field is optional, and its default keeps today's behaviour, so
`gorefist.json`, `rot_wart.json` and `wither.json` load and play unchanged.

### 4.1 Per-instance gating (decision 2)

Today everything is keyed by `EntityType` (`MobBehaviours.get(type)`).

- New `host.only_tagged: ["arenas_ld_run_mob"]`: the file applies to a mob only if it carries one of these entity
  tags.
- New `MobBehaviours.forMob(Mob)`: the type's behaviour if the filter passes, else null. It replaces `get(type)` at
  every per-instance call site: `MobCombat.behaviour()`, `MobCombatHostMixin` (`resolve`, `keepBosses`, `loadCombat`),
  `ThreatEvents.shouldThreatTarget`, `HitSpec.damageScale`, `SummonStats`, `MobsLdCommands`.
- The decision is made on attach (the first AI tick) and again on each `generation()` bump, and cached in
  `MobCombat`. Arenas tags every run mob in `markRunMob`, before `addFreshEntity`, on all five spawn paths, so the tag
  is there on the first tick. Tags are saved, so it survives restarts.
- `base_attributes` run in `Mob.<init>`, before any tag exists, so they cannot be gated. The loader rejects
  `only_tagged` together with `base_attributes`.
- New top-level `reference_attributes {id: value}`: values used only as the base for R and for summon `scale` ratios,
  never applied. `HitSpec.damageScale` and `SummonStats` read `base_attributes`, then `reference_attributes`, then 1.
  The Warden needs `{"minecraft:generic.attack_damage": 30}`, or R would be 30.
- World Wardens pay one int compare per tick (the existing generation check), plus one tag lookup per reload.
- Every Warden arena (raid, dungeon, arena wave) gets the same kit. Telling them apart later needs a per-spawner tag
  from Arenas (a variant selector on top of `only_tagged`).

### 4.2 The Brain: muzzle and hold, never skip (resolves the reports' disagreement)

The mechanics report said "skip `brain.tick`". The vanilla and engine reports said do not. Checked in the mapped 1.21.1
jar (`javap -c -p`): **do not skip it.**

- `Brain.tick` = `forgetOutdatedMemories`, `tickSensors`, `startEachNonRunningBehavior`, `tickEachRunningBehavior`.
  Skipping it freezes memory expiry (cooldowns stop counting) and the sensors.
- `Behavior.tickOrStop` calls `doStop` once `timedOut`. A behaviour that was running when the skip began stops at once
  when ticks resume. `Digging.stop` calls `Warden.remove(DISCARDED)` (verified): a skipped tick during a dig would
  lose the raid. `Roar.stop` calls `setAttackTarget` (target change, boom cooldown 200). `Sniffing.stop` adds anger.
- `navigation.tick()` sits outside the Brain, so a skipped Brain keeps walking the last path.

Memories alone (the vanilla report's plan) stop the attacks but not the body: FIGHT's
`SetWalkTargetFromAttackTargetIfTargetOutOfReach` re-sets WALK_TARGET every Brain tick, and `MoveToTargetSink` and
`LookAtTargetSink` move and turn it before the engine's tick, so the engine would undo them every tick.

The design uses both:
1. **Permanent memory muzzles** while hosted, re-set every tick by the adapter (after the Brain, so they hold for the
   next Brain tick), and seeded at the HEAD of `customServerAiStep` before the first Brain tick:
   `ATTACK_COOLING_DOWN` (MeleeAttack needs it absent), `SONIC_BOOM_COOLDOWN` (SonicBoom needs it absent),
   `VIBRATION_COOLDOWN` while engaged (the listener needs it absent; so tendril twitches are only engine tells), and
   `DIG_COOLDOWN` (4.5).
2. **Brain hold** while the engine owns the body (an ability, a transition, a stagger, a burrow, the walk home): a
   `@WrapOperation` on the `Brain.tick` INVOKE inside `Warden.customServerAiStep` calls only
   `forgetOutdatedMemories()` and `tickSensors(level, warden)` (through a new `mixin/BrainAccessor` with `@Invoker`s),
   not the two behaviour steps. Memories keep expiring and sensors stay fresh; no behaviour starts or ticks.
   - On the claim edge it calls `brain.stopAll()` (never while the pose is DIGGING; `Digging.stop` is guarded anyway,
     4.5), erases WALK_TARGET and PATH and stops the navigation.
   - On release the Brain runs again with ATTACK_TARGET intact.
   - Warden-only, not a global `@Mixin(Brain.class)`: villagers and piglins pay nothing. It can move into a shared
     Brain-host base when a second Brain host comes.

### 4.3 WardenAdapter and its hooks

New files:
- `mob/host/warden/WardenAdapter.java`, `WardenHostOptions.java`, `WardenCues.java` (clip table and pose timers).
- `mixin/warden/WardenHostMixin.java`, `WardenAccessor.java`, `DiggingMixin.java`, `SonicBoomMixin.java`.
- `mixin/BrainAccessor.java`.
- `mixin/client/WardenClientMixin.java` (4.6).
- `entity/SculkResonator.java`, `client/renderer/SculkResonatorRenderer.java` (4.9).

New `HostAdapter` hooks (all no-op by default; the Wither and default adapters do not override them):

| Hook | Called from | Warden |
|---|---|---|
| `acceptsBrain()` | `MobBehaviours` refusal | true; lifts the Warden from `REFUSED_TYPES` / `REFUSED_CLASSES`. Piglins, villagers and the Ender Dragon stay refused |
| `onTargetSet(Mob, LivingEntity)` | new TAIL inject on `Mob.setTarget` (after the existing HEAD filter) | writes ATTACK_TARGET (`setMemory`), erases ROAR_TARGET and CANT_REACH_WALK_TARGET_SINCE; null erases ATTACK_TARGET. So ThreatTargetGoal, reach give-up, reset, Spell Engine aggro and taunts all steer the Brain. `Mob.setTarget` alone only writes a field the Warden never reads (`Warden.getTarget()` reads the Brain) |
| `holdsBrain(Mob)` | WardenHostMixin's Brain wrap | busy, returning, staggered or burrowed |
| `onClaimBody` / `onReleaseBody` | `MobCombat` when `busy()` flips | stopAll (guarded), erase WALK_TARGET and PATH, stop the navigation |
| `onReset(Mob)` | end of `MobCombat.reset()`, `goInert()` | new empty `AngerManagement` (through a `@Mutable @Accessor`), erase ATTACK_TARGET, ROAR_TARGET, WALK_TARGET, DISTURBANCE_LOCATION, RECENT_PROJECTILE; pose STANDING; keep DIG_COOLDOWN |
| `setBurrowed(Mob, boolean)` | `burrow` event | invisible, invulnerable, no collision while under. Other hosts: the same without poses |

Existing hooks:
- `ready` = not DIGGING or EMERGING, unless the engine's own burrow set the pose.
- `install`: `setPersistenceRequired`, seed the muzzles and DIG_COOLDOWN, follow range 48 (base value, this instance
  only), emerge on spawn (EMERGING for 134 ticks, which also covers players who start tracking it mid-emerge).
- `tick`: re-set the muzzles; anger keep-alive (once a second, if ATTACK_TARGET is valid, top its anger up to 100 with
  `getAngerManagement().increaseAnger`, so FIGHT's `StopAttackingIfTargetInvalid` never drops it); pose timers.
- `onAction` / `onStopAction`: clip cues (4.6).
- `bossBar` = null and no `boss.bar` in the file: no Mobs_LD bar (Arenas hides vanilla bars and shows HP %).
- `muzzle(CENTER)` = the chest attachment (`WARDEN_CHEST`, Y +1.6), where vanilla's boom starts.
- `bodyWidth` = 2.1 x scale (arms at ±13 px plus 4 px), for Deep Charge's `width: body`.

`WardenHostMixin` (`@Mixin(Warden.class)`, full descriptors, never cancels `customServerAiStep`, every inject checks
"hosted" first; no pack mod hooks Warden code, per the pack survey):
- `customServerAiStep` HEAD (not cancelling): seed DIG_COOLDOWN for any Warden that is hosted **or** carries
  `arenas_ld_run_mob`; seed the attack muzzles when hosted.
- `@WrapOperation` on the `Brain.tick` INVOKE: the hold (4.2).
- `@WrapWithCondition` on the `applyDarknessAround(ServerLevel, Vec3, Entity, I)V` INVOKE: `darkness_pulse: off`.
- `@ModifyReturnValue` on `canTargetEntity(Entity)Z`: AND `combat.mayTarget(e) && !combat.refusesTarget(e)`. This one
  predicate gates vibration anger, anger gain, roar targets, `StopAttackingIfTargetInvalid` and the vanilla boom
  filter, so `engage_on: players` and the unreachable rule hold for the Brain too.
- `@WrapWithCondition` on the one `Brain.eraseMemory` INVOKE in `increaseAngerAt(Entity, int, boolean)` (vanilla's
  "drop the mob, go roar at the player"): skipped when hosted.
- `@ModifyReturnValue` on `canDisableShield()Z` → false when hosted. Verified: `LivingEntity.hurt` on a blocked hit
  calls `blockUsingShield`, which disables the shield if the attacker's `canDisableShield()` is true; vanilla returns
  true. Without this, engine swings would still disable shields.
- `@WrapOperation` on `isDiggingOrEmerging()` inside `isInvulnerableTo`: when hosted, true only while burrowed under
  or during the spawn emerge.
- Optional `heartbeat`: `@WrapOperation` on the value `syncClientAngerLevel` writes, so a phase sets the heartbeat
  rate (P1 20, P2 50, P3 90; 80+ is the fastest beat).
- Note: `Warden.hurt` calls `super.hurt` first and then adds +100 anger to the attacker even when the hit was
  cancelled (verified). The engine's invulnerability therefore does not stop anger. Harmless: anger only keeps the
  Brain's target valid, and the target comes from threat.

### 4.4 Persistence, reset, discard

- Arenas sets PersistenceRequired; `Warden.removeWhenFarAway` is already false.
- Inside a run, Mobs_LD's leash and wipe reset are off (`inArenasRun`); Arenas owns the wipe. The kit never runs
  outside runs.
- A Warden loaded back mid-fight resets (existing `settleLoadedFight`) and `onReset` clears its anger. Arenas keeps the
  boss chunk loaded, so this should not happen in a run.
- Discard paths left: death, `/kill`, Arenas (run end, stray-boss cleanup), and vanilla Peaceful (every Monster; the
  server runs Hard; not changed).

### 4.5 Dig lock (decision 10)

**Arenas_LD bug (reported to the Arenas_LD session):** `EntityEquipmentHelper.markRunMob` calls
`WardenAi.setDigCooldown(entity)`. Verified in bytecode: `setDigCooldown` only refreshes DIG_COOLDOWN when the memory
is already present (`hasMemoryValue`, then return), and so does FIGHT's `DIG_COOLDOWN_SETTER`. Only `finalizeSpawn`
creates the memory, and Arenas never calls `finalizeSpawn`. So an Arenas Warden has no dig cooldown: DIG outranks
FIGHT in `updateActivity`, and once it has no ROAR_TARGET it digs and is discarded after 100 ticks. In a raid that is
"Boss entity disappeared" = LOST; in a dungeon room it counts as a kill. Arenas' one-line fix:
`brain.setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 1200L)`; `markRunMob`'s persistence then
keeps it refreshed every tick.

Mobs_LD's three locks, independent of that fix:
1. **Seed before the first Brain tick:** the HEAD inject in 4.3 sets DIG_COOLDOWN (`setMemoryWithExpiry`, not the
   guarded helper) for hosted or run-tagged Wardens.
2. **Keep it alive:** the adapter sets it every tick (no expiry).
3. **Hard guard** in `DiggingMixin` (`ai.behavior.warden.Digging`): `checkExtraStartConditions` returns false for
   hosted or run-tagged Wardens (this also blocks the instant "in water or lava, then discard" branch in `start`), and
   a `@WrapWithCondition` on `remove(DISCARDED)` in `stop` skips it for them, sets STANDING and logs a warning.

`SonicBoomMixin`: `checkExtraStartConditions` returns false when hosted (belt and braces with the memory).

### 4.6 Clips and poses (decision 4)

Generic `clip` event (new): `{"type": "clip", "at": 1.7, "clip": "sonic_boom"}` calls `playAction` mid-timeline. It
works on LdMobs too.

`WardenCues` maps clip names:

| Cue | Server trigger | Length | Notes |
|---|---|---|---|
| `attack` | entity event 4 | 0.33 s | impact flash, not a wind-up; also stops a roar clip |
| `sonic_boom` | entity event 62 | 3.0 s | chest burst at 1.7 s; re-sending restarts it |
| `tendrils` | entity event 61 | 0.5 s | also lights the tendril glow |
| `roar` | pose ROARING, STANDING after 84 ticks | 4.2 s | the engine plays `warden.roar` at 1.25 s |
| `sniff` | pose SNIFFING, STANDING after 84 ticks | 4.16 s | |
| `dig` | pose DIGGING | 5.0 s | burrow only |
| `emerge` | pose EMERGING, STANDING after 134 ticks | 6.68 s | |

- **Pose reset:** `onStopAction` and the per-cue timer set STANDING. Re-setting the same pose needs one full tick in
  another pose first (synced data is flushed once per tick).
- **Fixed speed:** `WardenModel` plays clips at speed 1 and the client never stops pose clips early. An ability timed
  to a clip sets the new ability field `fixed_speed: true`, so it ignores the phase's `attack_speed`. Otherwise P3's
  1.2 would put the Circle's waves 0.28 s before the visible chest burst.
- **Burrow needs one client mixin:** the `dig` clip holds its buried last keyframe forever (vanilla never sees it
  because the Warden is removed). `WardenClientMixin` on `Warden.onSyncedDataUpdated` (TAIL, DATA_POSE): when the pose
  becomes EMERGING or STANDING, stop `diggingAnimationState`; when it becomes DIGGING, stop `emergeAnimationState`. No
  new animation. World Wardens never go DIGGING then EMERGING on the same entity, so it changes nothing for them.
- **Later (not now):** custom keyframe clips on the vanilla `WardenModel` through a client inject at the TAIL of
  `setupAnim`, Blockbench "Java Entity (Mojmap)" exports and a small synced cue. Bone names: body, head, ribcages,
  tendrils, arms, legs.

### 4.7 New fields and events (all optional)

| Where | Field / event | Default (= today) | Used by |
|---|---|---|---|
| top level | `reference_attributes` | none | R base (4.1) |
| `host` (any adapter) | `only_tagged` | none (whole type) | gating |
| `host` (`mobs_ld:warden`) | `vanilla_melee`, `vanilla_sonic_boom`, `darkness_pulse` (`vanilla`/`off`), `dig_away`, `disable_shields`, `follow_range`, `emerge_on_spawn`, `heartbeat` (per phase) | all vanilla | Warden |
| ability | `fixed_speed` | false | clip-timed abilities |
| ability | `cooldown_group` | none | Lance + Volley |
| ability | `no_repeat` | false | Circle, Pulse, Lance group |
| ability | `min_players` / `max_players` | none | Resonator re-raise |
| ability | `when.behind {players, for, range, angle}` | none | Sculk Backlash |
| ability | `include_unreachable` | false | Lance, Volley |
| ability `target` | `top_damage` (most damage dealt to it in the last 10 s) | | Hunting Sniff |
| `chain` | a taunt lock wins over a kept aim | kept aim | Deep Charge |
| `hit` | `rotate` (degrees added to the locked facing, zone and test) and `turn_body` (the body turns to it over 0.3 s when the telegraph appears) | 0 / false | Circle, Backlash |
| `hit` `origin` | `each_fighter` (one zone per fighting player) with `lead` (seconds of predicted movement from the last 0.5 s, capped at 4 blocks) | `self` | Echo Mines |
| `hit.hit` | `stacks {id, per_stack, max, duration, min_players}`: a per-victim stack that multiplies this mob's later hits on them; action-bar line to the victim | none | Sculk Brand |
| shape `rays` | `widen` (blocks of width per block of length; drawn as trapezoids) | 0 | Circle |
| shape | `ring {inner, outer}` + `AttackZone.Ring` renderer | | Rings |
| `fx.particles.at` | `along`: along the zones of this ability's `hit` events firing at the same `at`, every `every` blocks | | Circle, Lance |
| `effect` | `on: aim` | around the mob | Hunting Sniff mark |
| `summon.placement.around` | `home` | `self` | Resonators |
| phase `damage_taken` | `while_adds: <entity id>` (applies only while one of its own adds of that type lives) | always | Resonators |
| event | `clip {at, clip}` | | all |
| event | `vulnerable {at, until, multiplier}` | | Chest Open, Burrow rise |
| event | `burrow {at, under_at, emerge_at, to: aim\|farthest\|home, follow_until, max_from_home, tremor, telegraph, hit}`; spot search shares `RuneSpots`; calls `setBurrowed` | | Burrow, 25% transition |
| event | `listen {from, until, range, triggers, move_tolerance, hit, chain_to}` | | Listening Silence |
| optional | segmented `rays` laid on the ground like dash strips | flat | only if the arena floor is not flat (question 1) |

Shapes, hit tests and `rays` keep their current behaviour when the new fields are absent; Gorefist uses none of them.

### 4.8 Damage type `mobs_ld:warden_sonic` (decision 8)

`data/mobs_ld/damage_type/warden_sonic.json`: `{"message_id": "sonic_boom", "scaling":
"when_caused_by_living_non_player", "exhaustion": 0.0}`, registered in `ModDamageTypes`.

| Tag | In? | Why |
|---|---|---|
| `#minecraft:bypasses_shield` | yes | sonic goes through a shield; roll or move |
| `#minecraft:bypasses_armor`, `#bypasses_enchantments` | no | armor and Protection reduce it (vanilla sonic_boom bypasses both) |
| `#loot_stage:unscaled` | no | `damage_dealt` scales it, as the owner asked |
| `#spell_engine:evadable` | yes | consistent with `mob_attack`, which is evadable; evasion is a player stat |
| `#paladins:barrier_protects` | no | a Barrier dome would make the Circle a non-event; vanilla sonic_boom is not in it either |
| `#minecraft:is_projectile` | no | |
| Eden_LD `hazards.json` | no | it always has an attacker |

Difficulty scaling: Hard x1.5, Easy min(x/2+1, x), only when the Warden is the attacker. Physical swings stay
`minecraft:mob_attack`. Lang: `death.attack.sonic_boom` already exists in vanilla.

### 4.9 Sculk Resonator entity

`mobs_ld:sculk_resonator` (`entity/SculkResonator.java`), a minimal `Mob`, not an LdMob, no GeckoLib, no new art:
- Hitbox 1.0 x 2.0. No goals, movement speed 0, knockback resistance 1, not pushable, gravity on. Base HP 20 (the
  summon's `scale` sets the real value). Glowing tag on.
- Every 3 s: a vanilla `shriek` particle and `block.sculk_shrieker.shriek` (volume 1.5), so players can find it.
- Hurt and death sounds: sculk block sounds. No loot table, no XP.
- `OwnableEntity`: owner = the Warden, set at creation, before `addFreshEntity` (Arenas rule 2). It is also spawned
  inside the boss tick (rule 1) and tagged as a Mobs_LD summon. On the Warden's team, so the Warden never targets it.
- `SculkResonatorRenderer` (client): draws vanilla block models with `BlockRenderDispatcher.renderSingleBlock`: a
  `sculk_catalyst` (y 0-1) under a `sculk_shrieker[can_summon=true]` (y 1-1.5), with a slow 0.05-block bob. No new
  textures or models.
- Lang `entity.mobs_ld.sculk_resonator` in en_us and uk_ua.

### 4.10 Keeping Gorefist and the Wither unchanged

- Gorefist is frozen (owner): every change to shared code keeps his behaviour. New fields are optional with
  today's defaults; `rays` without `widen`, `hit` without `rotate`, `origin: self`.
- `forMob` returns the same as `get(type)` for files without `only_tagged`.
- The new HostAdapter hooks default to no-ops; the Wither adapter overrides none.
- Regression after M1, M2 and M3: Gorefist (trace, chase chains, Rot Wart summons, bar), Rot Wart, and the Wither R1
  (heads, skulls, Soul Rush and x2, Grave Call adds' HP 1/40, phase colours).

### 4.11 warden.json sketch

`data/minecraft/mob_behaviour/warden.json` (abridged: two of each family; the rest follow the table in 2.2).

```json
{
  "reference_attributes": { "minecraft:generic.attack_damage": 30 },
  "host": { "type": "mobs_ld:warden", "enabled": true, "only_tagged": ["arenas_ld_run_mob"],
    "vanilla_melee": false, "vanilla_sonic_boom": false, "darkness_pulse": "off", "dig_away": false,
    "disable_shields": false, "follow_range": 48, "emerge_on_spawn": true, "heartbeat": 20 },
  "target_slots": {
    "listener": { "rule": "spread", "range": 40, "line_of_sight": false, "repick": [8, 12], "lost_after": 4 } },
  "movement": { "style": "vanilla", "reach_rule": "path" },
  "global_cooldown": 0.8,
  "stagger": { "clip": "sniff", "duration": 4.2 },
  "boss": { "engage_on": "players" },
  "abilities": {
    "crushing_blow": { "duration": 1.6, "cooldown": 6, "weight": 3, "clip": "tendrils", "range": { "max": 4.5 },
      "track_until": 0.0, "events": [
        { "type": "fx", "at": 0.0, "sound": { "id": "minecraft:entity.warden.angry", "volume": 3 } },
        { "type": "fx", "at": 0.7, "sound": { "id": "minecraft:entity.warden.tendril_clicks", "pitch": 1.8, "volume": 3 } },
        { "type": "clip", "at": 1.0, "clip": "attack" },
        { "type": "hit", "at": 1.0, "shape": { "type": "rectangle", "width": 2.5, "length": 4.5 },
          "telegraph": { "from": 0.0, "color": "#B01818" },
          "hit": { "amount": 12.0, "knockback": 0.6,
                   "stacks": { "id": "sculk_brand", "per_stack": 0.12, "max": 4, "duration": 15, "min_players": 3 } } } ] },
    "sonic_circle": { "duration": 5.0, "cooldown": 16, "initial_cooldown": 10, "weight": 3, "no_repeat": true,
      "fixed_speed": true, "clip": "sonic_boom", "range": { "max": 8 }, "track_until": 0.3, "events": [
        { "type": "fx", "at": 0.0, "sound": { "id": "minecraft:entity.warden.sonic_charge", "volume": 3 },
          "particles": { "id": "minecraft:sculk_soul", "at": "self", "count": 16 } },
        { "type": "hit", "at": 1.7, "shape": { "type": "rays", "count": 8, "length": 26, "width": 2.5, "widen": 0.3 },
          "height": 4, "depth": 2, "telegraph": { "from": 0.3, "style": "sweep", "color": "#0E8C8C" },
          "hit": { "amount": 10.0, "damage_type": "mobs_ld:warden_sonic", "knockback": 0.3 } },
        { "type": "fx", "at": 1.4, "sound": { "id": "minecraft:entity.warden.tendril_clicks", "pitch": 1.8, "volume": 3 } },
        { "type": "fx", "at": 1.7, "sound": { "id": "minecraft:entity.warden.sonic_boom", "volume": 3 },
          "particles": { "id": "minecraft:sonic_boom", "at": "along", "every": 2 } },
        { "type": "clip", "at": 1.7, "clip": "sonic_boom" },
        { "type": "fx", "at": 1.7, "sound": { "id": "minecraft:entity.warden.sonic_charge", "pitch": 1.25, "volume": 3 } },
        { "type": "hit", "at": 3.4, "rotate": 22.5, "turn_body": true,
          "shape": { "type": "rays", "count": 8, "length": 26, "width": 2.5, "widen": 0.3 },
          "height": 4, "depth": 2, "telegraph": { "from": 1.7, "style": "sweep", "color": "#29E0E0" },
          "hit": { "amount": 10.0, "damage_type": "mobs_ld:warden_sonic", "knockback": 0.3 } },
        { "type": "fx", "at": 3.1, "sound": { "id": "minecraft:entity.warden.tendril_clicks", "pitch": 1.8, "volume": 3 } },
        { "type": "fx", "at": 3.4, "sound": { "id": "minecraft:entity.warden.sonic_boom", "volume": 3 },
          "particles": { "id": "minecraft:sonic_boom", "at": "along", "every": 2 } },
        { "type": "vulnerable", "at": 3.4, "until": 5.0, "multiplier": 1.25 } ] },
    "sonic_lance": { "duration": 3.0, "cooldown_group": "lance", "cooldown": 14, "initial_cooldown": 8, "weight": 3,
      "no_repeat": true, "fixed_speed": true, "clip": "sonic_boom", "target": "farthest", "include_unreachable": true,
      "range": { "min": 8, "max": 40, "above": 12, "below": 4 }, "track_until": 0.9, "events": [
        { "type": "fx", "at": 0.0, "sound": { "id": "minecraft:entity.warden.sonic_charge", "volume": 3 } },
        { "type": "fx", "at": 0.2, "particles": { "id": "minecraft:sculk_charge_pop", "at": "aim", "count": 6 } },
        { "type": "fx", "at": 0.5, "particles": { "id": "minecraft:sculk_charge_pop", "at": "aim", "count": 6 } },
        { "type": "hit", "at": 1.7, "shape": { "type": "rectangle", "width": 2.5, "length": 40 }, "height": 12, "depth": 4,
          "telegraph": { "from": 0.9, "style": "sweep", "arrow": true, "color": "#0E8C8C" },
          "hit": { "amount": 15.0, "damage_type": "mobs_ld:warden_sonic", "knockback": 1.2 } },
        { "type": "fx", "at": 1.4, "sound": { "id": "minecraft:entity.warden.tendril_clicks", "pitch": 1.8, "volume": 3 } },
        { "type": "fx", "at": 1.7, "sound": { "id": "minecraft:entity.warden.sonic_boom", "volume": 3 },
          "particles": { "id": "minecraft:sonic_boom", "at": "along", "every": 1 } },
        { "type": "vulnerable", "at": 1.7, "until": 3.0, "multiplier": 1.25 } ] },
    "burrow_ambush": { "duration": 11.2, "cooldown": 30, "initial_cooldown": 15, "weight": 2, "fixed_speed": true,
      "clip": "dig", "target": "farthest", "range": { "min": 10 }, "events": [
        { "type": "fx", "at": 0.0, "sound": { "id": "minecraft:entity.warden.dig", "volume": 4 } },
        { "type": "burrow", "at": 0.0, "under_at": 2.0, "emerge_at": 4.5, "to": "aim", "follow_until": 4.1,
          "max_from_home": 22,
          "tremor": { "every": 0.25, "particles": "minecraft:sculk_charge_pop",
                      "sound": { "id": "minecraft:entity.warden.dig", "volume": 2 } },
          "telegraph": { "from": 4.1, "radius": 3.5, "color": "#1B2A6B" },
          "hit": { "at": 5.7, "radius": 3.5, "amount": 17.0, "damage_type": "mobs_ld:warden_sonic",
                   "knockback": 0.4, "knock_up": 0.8 } },
        { "type": "cloud", "at": 5.7, "radius": 3, "duration": 8,
          "effects": [ { "id": "minecraft:slowness", "duration": 2, "amplifier": 1 } ] },
        { "type": "vulnerable", "at": 5.7, "until": 11.2, "multiplier": 1.3 } ] },
    "resonance": { "duration": 4.2, "weight": 0, "force_after": 0, "cooldown": 60, "min_players": 2,
      "skip_when_full": true, "fixed_speed": true, "clip": "roar", "events": [
        { "type": "fx", "at": 0.0, "message": { "key": "mobs_ld.cue.warden.resonators", "where": "actionbar" } },
        { "type": "fx", "at": 1.25, "sound": { "id": "minecraft:entity.warden.roar", "volume": 3 } },
        { "type": "summon", "at": 2.8, "entity": "mobs_ld:sculk_resonator",
          "count": { "min": 2, "per_extra_player": 1, "cap": 4 }, "max_alive": 4,
          "placement": { "around": "home", "radius": [14, 18], "min_gap": 8, "clearance": 2, "ground": true },
          "telegraph": { "from": 1.0, "radius": 1.2, "color": "#0E8C8C" },
          "scale": { "max_health": { "of_summoner": 0.01 } },
          "loot": false, "xp": false, "bound": true } ] }
  },
  "phases": [
    { "below_health": 1.0, "abilities": ["crushing_blow", "rending_combo", "sculk_backlash", "sonic_circle",
        "resonant_pulse", "sonic_lance", "tremor_line", "hunting_sniff"] },
    { "below_health": 0.6, "transition": "roar_of_the_deep",
      "abilities": ["crushing_blow", "rending_combo", "sculk_backlash", "sonic_circle_open", "resonant_pulse_pooled",
        "sonic_lance", "sonic_volley", "tremor_line", "deafening_rings", "echo_mines", "burrow_ambush",
        "hunting_sniff", "resonance"],
      "ready_in": { "sonic_circle_open": 7, "resonance": 45 }, "cooldowns": { "sonic_lance": 10, "sonic_volley": 10 },
      "damage_taken": [ { "kind": "all", "multiplier": 0.5, "while_adds": "mobs_ld:sculk_resonator" } ],
      "host": { "heartbeat": 50 } },
    { "below_health": 0.25, "transition": "heart_of_the_deep", "attack_speed": 1.2,
      "abilities": ["crushing_blow", "rending_combo", "sculk_backlash", "sonic_circle_triple", "resonant_pulse_pooled",
        "sonic_lance", "sonic_volley", "tremor_line", "deafening_rings", "echo_mines", "burrow_ambush",
        "hunting_sniff", "listening_silence"],
      "ready_in": { "listening_silence": 10 },
      "cooldowns": { "sonic_lance": 7, "sonic_volley": 7, "burrow_ambush": 35 },
      "damage_taken": [ { "kind": "all", "multiplier": 0.5, "while_adds": "mobs_ld:sculk_resonator" } ],
      "host": { "heartbeat": 90 } }
  ]
}
```

The phase `transition` is written here as a name for brevity; in the file it is the transition ability object, as
today.

---

## 5. Numbers for the balance team (Eden Loot)

### 5.1 Who owns the stats: Arenas

| Candidate | Verdict |
|---|---|
| DD's boss rule (fixed L15, HP x11.016, attack x0.5, damage_dealt 0.9) | It exists only for the **Overworld**. The instance dimensions (`yggdrasil:raid_dim`, `dungeon_dim`, ...) have no dimension difficulty and only loot-table rules that match Arenas' completion tables, not `minecraft:entities/warden`. So it most likely does not apply in runs (to verify, M0). If an arena were in the Overworld, DD would multiply on top of Arenas' base: HP x11 twice over. Do not add a DD Warden rule for the instance dimensions |
| Mobs_LD `base_attributes` | Forbidden: applied in the constructor to every Warden, world ones included |
| **Arenas spawner rows + tier + per-player** | **Recommended.** Only the boss's spawner touches it, the tier and player count are built in, and the rows accept any attribute id (verified in `EntityEquipmentHelper.applyScaledAttributes`: every row is set as a base value; only `max_health` and `attack_damage` are multiplied) |

What Arenas sets (raid controller and dungeon controller for the Warden):
- Spawner row `minecraft:generic.max_health` = **2,480** (x tier `healthMultiplier` x per-player).
- Spawner row `loot_stage:damage_dealt` = **0.90** (Eden Loot's knob; set as a base, never tier-scaled).
- **No** `minecraft:generic.attack_damage` row. The Warden keeps its vanilla 30 = the declared reference, so R = 1. A
  row would be x `damageMultiplier`, and raids also multiply every point of damage a participant takes by
  `damageMultiplier`: abilities would get the tier twice.
- Raid controller tiers: `hpScalePerPlayer` **0.65** (Arenas: `perPlayer = 1 + (players at start - 1) x
  hpScalePerPlayer`, frozen at the start; it is per controller and tier, so other raids are not touched). Boss
  regeneration 0.
- Dungeon controller: "Mob HP per extra player" 0.65 (default 0). Confirmed by the Arenas_LD session: mob HP =
  max_health row x tier healthMultiplier x (1 + (n - 1) x that value), fixed at run start from the party size (leavers
  and deaths do not rescale it), and only for spawners with a max_health row. Raids use the same shape per tier.
- "Scale minions": **Off** on the Warden spawners. Mobs_LD sets the Resonators' HP from the Warden's (its
  normalization lands exactly on target either way, but Off keeps it readable).
- "Enable drops": see question 3.
- Mobs_LD owns follow range (48, host option) and nothing else. Armor 0, knockback resistance 1 (vanilla).

What Eden Loot sets: `damage_dealt` (0.90 proposed) and the HP base (2,480 proposed), plus the completion loot.

### 5.2 Formula

landed = amount x R x damage_dealt x 1.5 (Hard) x 30 / (30 + armor) x Arenas tier damage multiplier

- R = 1 (live attack_damage 30 / reference 30). Sculk Brand multiplies its victim's hits by 1.12-1.48.
- Both types are difficulty-scaled for a non-player attacker and reduced by armor (Effective Armor Formula, constant
  30). Protection also reduces them (not counted below).
- Typical L15 player: 45 HP, armor 8.6 (factor 0.777). Well-geared: 79 HP, armor 10.2 (0.746). +LB6: 95 HP, 10.2.
- Raid tier damage multipliers 0.75 / 1 / 1.5 / 2.5 (Easy / Normal / Hard / Nightmare). The table is Normal.

### 5.3 Per attack (damage_dealt 0.90, Hard, Normal tier)

Hand-off in hits_brief format: reference "Burrow Ambush emerge", base 17.0, kind sonic (armor yes, shield no),
per_hit true, difficulty_scaled true, dd_scales true, R = 1.

| Hit | Type | L0 Normal raw (= amount) | Hard x0.9 raw | Typical landed | % of 45 | % of 79 | % of 95 |
|---|---|---|---|---|---|---|---|
| Burrow Ambush emerge (biggest) | S | 17.0 | 22.9 | 17.8 | **40%** | 22% | 18% |
| Deep Charge contact | P | 16.0 | 21.6 | 16.8 | 37% | 20% | 17% |
| Sonic Lance | S | 15.0 | 20.2 | 15.7 | 35% | 19% | 16% |
| Resonant Pulse | S | 14.0 | 18.9 | 14.7 | 33% | 18% | 15% |
| Listening Silence, per offender | S | 13.0 | 17.6 | 13.6 | 30% | 17% | 14% |
| Crushing Blow | P | 12.0 | 16.2 | 12.6 | 28% | 15% | 13% |
| Rending Combo beat 2 / beat 1 | P | 11.0 / 7.0 | 14.8 / 9.5 | 11.5 / 7.3 | 26% / 16% | 14% / 9% | 12% / 7% |
| Sonic Boom Circle, per wave | S | 10.0 | 13.5 | 10.5 | 23% | 13% | 11% |
| Sculk Backlash | S | 9.0 | 12.2 | 9.4 | 21% | 11% | 10% |
| Deafening Rings, per ring | S | 9.0 | 12.2 | 9.4 | 21% | 11% | 10% |
| Sonic Volley, per lance | S | 8.0 | 10.8 | 8.4 | 19% | 10% | 8% |
| Tremor Line, per circle | P | 7.0 | 9.5 | 7.3 | 16% | 9% | 7% |
| Echo Mine | S | 5.0 | 6.8 | 5.2 | 12% | 6% | 5% |
| Roar of the Deep shove | S | 3.0 | 4.0 | 3.1 | 7% | 4% | 3% |

- Caught by both Circle waves: 47% / 26% / 21%; by all three (P3): 70% / 38% / 32%.
- Crushing Blow on a tank at 3 Brand stacks: x1.36 = 38%.
- Other raid tiers scale every row: Hard x1.5 (biggest hit 60% of a typical player), Nightmare x2.5 (99%: a one-shot
  for a typical player, 54% for a well-geared one). Nightmare is for T2 gear.
- Compared with today's world Warden on a typical player (35% swing every 0.9 s, 30% boom): one big telegraphed hit
  at 40%, and nothing that lands without a warning.

**Correction from Eden Loot (2026-11-03): tier = player level.** The Arenas tiers Easy / Normal / Hard / Nightmare are
for levels 5 / 10 / 15 / 20, so the Normal tier means **level 10** players, not 15. Player model (typical /
well-geared): L5 31 / 57 HP, L10 38 / 68, L15 45 / 79, L20 50 / 90; armor 8.6 / 10.2 (hits land at about x0.75).
The table above is the level-15 view. With the right player per tier, the emerge (17.2 landed before the tier
multiplier) is:

| Tier (players) | Hits scaled by the tier (raids) | % typical / well-geared | Hits not scaled |
|---|---|---|---|
| Easy (L5) x0.75 | 12.9 | 42% / 23% | 55% / 30% |
| Normal (L10) x1 | 17.2 | 45% / 25% | 45% / 25% |
| Hard (L15) x1.5 | 25.8 | 57% / 33% | 38% / 22% |
| Nightmare (L20) x2.5 | 43.0 | 86% / 48% | 34% / 19% |

- Both raids and dungeons use the "scaled" column (confirmed by Arenas_LD): `LivingEntityHooks.modifyPlayerHurtAmount`
  multiplies every hit a run player takes by the run tier's damage multiplier (per controller, snapshotted at run
  start). Requirement: dungeons apply it per the server key `dungeon_damage_source_filter` (ALL / LIVING / MOB), so
  **every Warden ability hit must carry the Warden as the damage source's causing entity** (as HitSpec already does for
  the Wither). Then the tier applies whatever the filter. Mob arenas (not in scope) scale mob attributes per wave
  instead and would need an attack_damage row.
- To hit the 40% target at the Normal tier: `damage_dealt` 0.80 instead of 0.90 (Eden Loot's knob). Nightmare at x2.5
  nearly one-shots a typical L20 player (86%); fine if Nightmare expects well-geared players (48%), otherwise about
  x2.0 for hits on that tier (owner and Arenas_LD). Both are calibration items for M7, not blockers.
- Group mix at T1: 4 melee about 140 s, 4 archers about 205 s+ (T1 has no fast bow). Skill-tree XP stays 100, killer
  only.

### 5.4 HP and fight length

HP = 2,480 x (1 + 0.65 x (players - 1)) x tier health multiplier. DPS from Eden Loot's best-weapon rows, boss armor 0.
Seconds at 70% / 60% uptime, including about 25 s (T1) / 15 s (T2) for the invulnerable roar, burrows and the
Resonators' half-damage windows.

| Group | HP (Normal) | T1 Normal | T2 Normal | T1 Hard | T2 Hard | T1 Nightmare | T2 Nightmare |
|---|---|---|---|---|---|---|---|
| Solo melee (dungeon) | 2,480 | 190-217 | 95-109 | 272-313 | 136-156 | 437-506 | 216-249 |
| Duo melee + ranged | 4,092 | 208-238 | 85-97 | 299-345 | 120-138 | 482-558 | 190-219 |
| Trio melee + ranged + caster | 5,704 | 211-243 | 93-105 | 305-351 | 131-151 | 491-569 | 209-241 |
| 4: 2 melee + ranged + caster (raid) | 7,316 | 185-212 | 85-97 | 265-306 | 120-138 | 426-493 | 190-219 |
| 5: 2 melee + 2 ranged + caster (raid) | 8,928 | 193-222 | 83-94 | 278-320 | 116-133 | 446-516 | 184-212 |

Group DPS used: T1 21.5 / 10.5 / 11.7 and T2 44.1 / 39.3 / 21.7 (melee / ranged / caster), so a 4-player raid is
65.2 (T1) and 149.2 (T2).

- With 0.65 per player every size lands within about 15% of the same fight length.
- Against the 600 s limit: everything fits; T1 on Nightmare is the tightest (up to about 570 s at 60% uptime).
- **Should HP rise per tier?** Yes, through the Arenas tiers' existing health multipliers (0.75 / 1 / 1.5 / 2.5).
  One base for all. T1 groups: Normal (about 3.5 min). T2 groups: Hard or Nightmare (about 2-4 min); on Normal they
  finish in about 1.5 min and skip most of P2.
- Resonators: 1% of the Warden's max HP each.

---

## 6. Compatibility notes

- **Arenas_LD:**
  - The dig bug (4.5) is reported to the Arenas_LD session. Ship its one-line fix with or before the first Warden
    spawner; Mobs_LD's locks do not depend on it.
  - Spawner rows, tier settings, Scale minions Off: section 5.1.
  - Resonators are minions (spawned in the boss tick and owned by the Warden): they drop nothing, never hold up the
    run, and vanish with it.
  - The HUD hides vanilla boss bars; Mobs_LD sends no bar, only action-bar cues.
  - The dungeon "protect" objective calls `mob.setTarget`; with `onTargetSet` it would now steer a hosted Warden, but
    `engage_on: players` refuses non-player targets. The Warden is not meant for protect rooms.
  - Downed players are spectators: `canTargetEntity` and the fighter count skip them.
- **Dungeon Difficulty:** no rule for the Warden in the instance dimensions; keep it that way (5.1). World Wardens
  keep the Overworld L15 rule.
- **DD Loot Stage:** `damage_dealt` applies to every Warden-attributed hit; `warden_sonic` is not unscaled. The live
  1.1.1 already applies to Wardens.
- **Spell Engine:**
  - The Warden is not in `#c:bosses` (so not in `#spell_engine:bosses`). Decision: **no tag change.** Adding it would
    also change world Wardens (CC immunity, Eden Loot's boss_ids). The FallenEden CC HP-limit rule (for example
    Impale: 250 + 6 x power) keeps CC off anything above a few hundred HP, and the smallest hosted Warden has 1,860
    (solo, Easy). `#mrpg_lib:stun_immune` and `#forcemaster_rpg:knock_up_immune` already list it. M7 checks that no
    root, freeze or stun lands; if one does, a hosted-only effect filter is a small addition.
  - Evasion dodges both damage types (4.8). Spell Engine aggro and taunts reach the Brain through `onTargetSet`.
- **Combat Roll:** every hit is one `hurt()` on one tick; 8 roll i-frames (live `invulnerable_ticks_upon_roll: 8`)
  clear it. Listening Silence treats a roll as moving.
- **Paladins:** `warden_sonic` stays out of `#paladins:barrier_protects` (4.8).
- **Eden_LD hazards:** no attacker-less damage in the kit (pools are Slowness only). `#falleneden:bosses` already holds
  the Warden (boss hazard share 1/10).
- **bards_rpg, Warden's Paean:** blocks harmful effects, so it counters the 60% Darkness. Fine.
- **Immersive Optimization:** add `"minecraft:warden": false` (tick skipping would shift telegraphs and roll windows).
  `mobs_ld` is already exempt, which covers the Resonator. Its `dimensions` lists only Overworld, Nether and End:
  check how it treats `yggdrasil:*`.
- **ServerCore:** the Warden is already in `excluded-entity-types`, and activation range is off.
- **Lithium game events, Origins vibration powers:** unaffected; the hosted listener is muzzled in fights.
- **EntityCulling (client):** whitelist `mobs_ld:attack_zone_visual` and `mobs_ld:sculk_resonator` if they flicker.
- **ETF emissive layers, AmbientSounds:** cosmetic, unaffected.
- **Loot n Explore, DataRewriter (Warden Ears 25%), FTB quests (150 kills, Road to the End), Divine Encounters omen,
  Stellarity boost, Soulslike Excalibur advancement, sculk_and_scavenge catalyst ritual:** unchanged. A hosted kill is
  a real, player-credited death on the vanilla loot table.
- **Water cheese** (Discord, 2026-06-08: Wardens on water, fights from boats): arenas need no water. Arenas players are
  in Adventure mode and cannot build pillars.
- **Same jar everywhere:** the client needs the Resonator renderer and the burrow client mixin.

---

## 7. Milestones and test plan

Standard test setup (owner rules):
- headless dev server through Loom `runServer` for logic and regression;
- dev client on its own Xvfb :97 (never the desktop), volume 10%, killed by PID only; two offline clients for group
  tests;
- scripted test world: `/gamerule doMobSpawning false`, then `/kill @e[type=!player]`, `/difficulty hard`, a flat
  50 x 50 deepslate floor by `/fill`;
- a hosted Warden: `/summon minecraft:warden ~ ~ ~ {Tags:["arenas_ld_run_mob"],PersistenceRequired:1b}`. With NBT,
  `/summon` skips `finalizeSpawn`, exactly like Arenas, so it also tests the dig lock;
- a world Warden: `/summon minecraft:warden` with no NBT;
- abilities forced with `/mobs_ld ability`, a test datapack with weights 0, `/mobs_ld debug`, `/mobs_ld threat`, and a
  new `/mobs_ld brain <target>` (held or not, ATTACK_TARGET, its anger, DIG_COOLDOWN, pose, burrow state).

| # | Milestone | Size | Exit checks |
|---|---|---|---|
| M0 | Ops prep: Immersive Optimization exemption; Arenas dig fix agreed; arena build rules (question 1); spawner rows and tiers agreed with Eden Loot and the Arenas session | S, under 0.5 d | config diffs reviewed |
| M1 | Gating and Brain host core: `only_tagged`, `forMob`, `reference_attributes`, `acceptsBrain`, WardenAdapter (ready, install, muzzles, anger keep-alive, `onTargetSet`, `canTargetEntity`, the erase wrap, darkness option, shield option, follow range), Brain hold with claim and release, `onReset`, the three dig locks, SonicBoomMixin, `/mobs_ld brain` | L, 3-4 d | world Warden: swings 30, booms, pulses Darkness, digs away after 60 s idle. Tagged Warden: idle 90 s on land and in water, never digs; no vanilla swing or boom; follows the threat top; a taunt moves it; `/mobs_ld reset` clears anger and it does not roar back. Gorefist, Rot Wart, Wither R1 unchanged |
| M2 | Cues and basics: `clip` event, `WardenCues` and pose timers, `fixed_speed`, `warden_sonic` and its tags, `vulnerable`, `fx along`, `rotate` and `turn_body`, `rays.widen` and its renderer, `cooldown_group`, `no_repeat`, `heartbeat` | M, 2-3 d | every clip plays on Xvfb (screenshot bursts); a pose re-triggers; Crushing Blow, Circle and Lance work data-only; the Circle's lanes widen (JUnit hit tests at the table's distances); a shield blocks Crushing Blow and is never disabled; the Circle goes through the shield; regression again |
| M3 | Aim, shapes and conditions: `ring`, `origin: each_fighter` with `lead`, `include_unreachable`, `top_damage`, `when.behind`, `min_players` / `max_players`, `effect on: aim`, `placement around: home`, `damage_taken while_adds`, `stacks`, `listen`, taunt at chain | M, 2-3 d | JUnit: lead prediction, behind detection, stacks; two clients: Backlash only with both behind for 4 s, Brand only with 3 fighters (third client or a pet test), Echo Mines one set each, Silence hits a mover and spares a still player; regression |
| M4 | Burrow: `burrow` event, `setBurrowed`, pose invulnerability wrap, spot search, the client dig-state fix, `emerge_on_spawn` | M, about 2 d | Xvfb bursts: dig, gone, tremor, circle, emerge at the spot, clean rise with no buried offset; invulnerable only while under; spot never outside 22 of home or in a wall; the entity UUID never changes |
| M5 | Sculk Resonator: entity, renderer, owner, summon tags | M, 1.5-2 d | renders from vanilla blocks; glows; shriek every 3 s; x0.5 on the Warden while any stands and x1 after; count 2/3/4 by fighters; removed on the Warden's death and reset |
| M6 | Content: full `warden.json`, transitions, en_us and uk_ua cues, `docs/behaviour.md` "The Warden" section | S-M, 1.5-2 d | every ability forced once on Xvfb with screenshot bursts; timings checked against the debug trace; phases latch at 60% and 25% |
| M7 | Verification and calibration on a copy of the Prism Server instance (Arenas_LD, DD, loot_stage, Spell Engine, Combat Roll): a raid controller and a dungeon spawner in `yggdrasil:test_dim` with the rows from 5.1 | M, 2-3 d + playtest | HP = 2,480 x tier x per-player; no DD modifiers on the Warden (`/attribute ... get`, Jade); damage_dealt 0.9; "Boss entity disappeared" never logged; Resonators adopted as minions; cues readable under the Arenas HUD; Darkness does not hide telegraphs; no CC lands; hits sheet to Eden Loot (`test_damage_dealt.sh`); 2-4 player playtest at T1 Normal; tune cooldowns and the Lance knob |
| opt | Segmented rays on uneven floors | M, about 2 d | only if question 1 is "no" |

Full plan: about 15-19 working days. A P1-only file (Circle, Lance, swings, Pulse, Tremor) can be playtested after M2.

---

## 8. Open questions for the owner

Answered 2026-11-03: 1 flat floor (yes), 2 resonator look (yes), 3 loot **decided later** with Eden Loot, 4-6 at their
defaults.

1. **Arena floor.** Flat within 24-26 blocks of where the Warden fights, no water, so the Circle's flat lanes, the
   rings and the Burrow spots lie on the floor? *Default: yes, flat. Otherwise add segmented rays (+2 d).*
2. **Resonator look.** A sculk catalyst with a sculk shrieker on top, glowing, shrieking every 3 s, no new art.
   *Default: yes.*
3. **Raid and dungeon loot.** *Default:*
   - the boss_gear rolls (Eden Loot's pool proposal) go into the Arenas completion tables for the Warden runs, never
     into `minecraft:entities/warden`, so shrieker Wardens stay unfarmable for gear;
   - the spawner's "Enable drops" On, so the vanilla table drops the sculk catalyst and the 25% Warden Ears as today;
   - the "wear the Warden's ear" cosmetic (Discord request) is a separate, later item.
4. **HP per tier.** Base 2,480, `hpScalePerPlayer` 0.65, the Arenas tiers' health multipliers 0.75 / 1 / 1.5 / 2.5
   unchanged. *Default: yes; T2 groups are expected on Hard or Nightmare.*
5. **Damage.** `damage_dealt` 0.90 on the spawner, biggest hit 40% of a typical L15 player on Normal tier; Nightmare
   (x2.5) then one-shots a typical player with the Burrow emerge. *Default: keep; Nightmare is for T2 gear.*
6. **Spell Engine crowd control.** Leave the Warden out of `#c:bosses` (world Wardens unchanged); rely on the CC
   HP limits and add a hosted-only filter only if a test shows CC landing. *Default: yes.*

---

## 9. Deploy checklist

- [ ] **Same jar everywhere:** server and client modpack get the same Mobs_LD version (new entity renderer and client
  mixin).
- [ ] **Arenas_LD dig fix** (`markRunMob` sets DIG_COOLDOWN directly) deployed with or before the first Warden
  spawner.
- [ ] **Arenas Warden spawners** (raid and dungeon): rows `minecraft:generic.max_health` 2,480 and
  `loot_stage:damage_dealt` 0.90; no attack_damage row; "Scale minions" Off; "Enable drops" per question 3.
- [ ] **Raid controller tiers:** `hpScalePerPlayer` 0.65, regeneration 0. **Dungeon controller:** HP per extra player
  0.65.
- [ ] **DD:** no Warden rule in `yggdrasil:*` (confirm on a test spawn that the Warden has no DD modifiers).
- [ ] **Arenas completion loot** for the Warden runs, from Eden Loot.
- [ ] **Immersive Optimization:** `"minecraft:warden": false`; check its handling of `yggdrasil:*` dimensions.
- [ ] **EntityCulling (client):** whitelist `mobs_ld:attack_zone_visual` and `mobs_ld:sculk_resonator` if they
  flicker.
- [ ] **Combat Roll:** `invulnerable_ticks_upon_roll: 8` still set.
- [ ] Uploads and restarts go through the "MCP eden" session with the owner's approval.

The repo is unchanged except this file. Files referenced: `/home/ledok/Projects/Mods/LeDok/Fabric/Mobs_LD/src/main/java/net/ledok/mobs_ld/`
(`mob/behaviour/MobBehaviours.java`, `mob/behaviour/event/HitSpec.java`, `mob/combat/SummonStats.java`,
`mob/host/HostAdapter.java`, `mixin/MobCombatHostMixin.java`, `registry/ModDamageTypes.java`),
`/home/ledok/Projects/Mods/LeDok/Fabric/Mobs_LD/docs/behaviour.md`, and in Arenas_LD
`/home/ledok/Projects/Mods/LeDok/Fabric/Arenas_LD/common/src/main/java/net/ledok/arenas_ld/util/EntityEquipmentHelper.java`
and `raid/run/RaidRunLifecycle.java`.
