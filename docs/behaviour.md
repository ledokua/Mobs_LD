# Mobs_LD mob behaviour format

How a data-driven mob fights. One JSON file per mob in a datapack:
`data/<namespace>/mob_behaviour/<mob>.json` (the file id is the entity id, e.g. `mobs_ld:gorefist`).
Reloads with `/reload`; a broken file is logged and skipped. Times are **seconds**, distances **blocks**.
Vanilla mobs can run a behaviour too: see [Vanilla hosts](#vanilla-hosts).

Fields marked *time range* take a number (`4`) or a range (`[4, 5]`), from which a fresh value is rolled each time
it is used (each re-pick, each shot), so repeating timers drift apart.

The entity itself (hitbox, base stats, spawn egg) is declared in the jar at `mobs_ld/entities/<mob>.json`, and its
art follows [models.md](models.md). Working examples: `gorefist.json` and `rot_wart.json` in
`src/main/resources/data/mobs_ld/mob_behaviour/`.

## Top level

| field | default | meaning |
|---|---|---|
| `base_attributes` | none | `{ "minecraft:generic.max_health": 1711, ... }`: base values set on every new mob of the type (below) |
| `reference_attributes` | none | `{ "minecraft:generic.attack_damage": 30 }`: the base values the file's numbers are written against, never applied: read after `base_attributes` as the base of R (`hit.amount`) and of summon `scale` ratios. For a mob whose stats come from elsewhere (an Arenas_LD spawner) |
| `host` | none | vanilla hosts only: the host adapter's options, `only_tagged` (which mobs of the type it applies to), or `{ "enabled": false }` to switch the file off (see [Vanilla hosts](#vanilla-hosts)) |
| `target_slots` | none | secondary targets by name, e.g. the Wither's side heads (see [Target slots](#target-slots)) |
| `movement` | standoff 2.0 | how it positions itself between abilities (below) |
| `global_cooldown` | 0.5 | pause after every ability before the next can start |
| `intro` | none | ability played once when it first engages (e.g. a roar; set `invulnerable`) |
| `stagger` | none | `{ "clip": "stagger", "duration": 1.8, "recover": 0 }` - helpless window after a dash hits a wall; `recover`: seconds after it during which no ability starts (it moves on its own again), so an ability that became ready meanwhile (a forced summon) does not fire the moment it gets up |
| `abilities` | `{}` | name -> ability (below) |
| `on_death` | none | ability timeline run while the body falls (its clip is the death clip) |
| `phases` | none | boss phases (below) |
| `boss` | none | boss bar, leash, wipe reset, soft enrage (below) |

### movement

| field | default | meaning |
|---|---|---|
| `style` | `standoff` | `standoff` (walk to `distance`, hold), `kite` (stay about `distance` away), `stationary`, `vanilla` (the host's own AI moves and attacks between abilities; see below) |
| `distance` | 2.0 | preferred distance to the followed target |
| `speed` | 1.0 | navigation speed multiplier |
| `follow` | `current` | whom to move toward: `current` (threat target), `nearest`, `farthest`, `random`, `lowest_threat`, `top_damage`, `slot:<name>` (as an ability's `target`) |
| `reach_height` | 85 % of its height | how far above its feet it can strike |
| `reach_depth` | 1.5 | how far below its feet it can strike (into a pit, off a ledge) |
| `reach_rule` | `path` | what makes a target reachable: `path` (reach band or a full path) or `line_of_sight` (it can see the target) |
| `lose_after` | 2 (`path`), 3 (`line_of_sight`) | seconds a target must stay unreachable before it loses aggro |
| `ground_scan` | 8 | how many blocks below a point telegraphs and `hover` look for the floor; a high flyer needs more (the Wither uses 40) |

**`style: vanilla`** (vanilla hosts): between abilities the mob keeps its own movement and attacks (the Wither flies
and fires skulls as usual); the engine still picks an ability every tick it is free, and only takes the body over
while an ability, a stagger or the walk home runs.

**Ground scan**: the floor is found by scanning down block by block (from 2 blocks above a telegraph's point, from
the mob's feet for `hover`), never by heightmap, so under the Nether roof it is the floor, not the roof. The surface
of water or lava below the point counts as floor (a flyer over a lava sea hovers over the lava, not its bed); a mob
wading or swimming still gets the bed under it. With nothing within `ground_scan` (the void, a deep drop) there is no
floor: telegraphs then sit at the point itself (a `dash` strip leaves those parts out), and a `hover` over the ground
keeps its altitude.

**Reach band**: `reach_height` / `reach_depth` are used everywhere a height matters - an ability only starts if the
aim's feet are inside the band, hits land on anyone inside it, and a target outside it that the mob cannot path to
is unreachable. Big mobs therefore reach higher on their own (Gorefist, 4 blocks tall, strikes 3.4 up).

**Unreachable targets** (out of the reach band with no path, or out of sight with `reach_rule: line_of_sight`, for
`lose_after` seconds) lose aggro: threat targeting, initial aggro and retaliation skip them and the mob turns on
someone it can reach. If nobody is reachable it has no target, and a boss resets after `boss.reset_after` (other mobs
stop fighting after 10 s). They get aggro back as soon as they are reachable again. By `path`, flyers never count as
unreachable (see [Vanilla hosts](#vanilla-hosts)); by `line_of_sight` hiding behind cover counts for flyers too.

## Abilities

```json
"vertical": {
  "duration": 2.2, "cooldown": 4, "weight": 3,
  "range": { "max": 4.5 },
  "events": [
    { "type": "hit", "at": 1.1, "telegraph": { "from": 0.4 },
      "shape": { "type": "rectangle", "width": 2, "length": 5 },
      "hit": { "damage": 1.4, "knockback": 0.6 } }
  ]
}
```

| field | default | meaning |
|---|---|---|
| `clip` | the ability name | animation clip on the action layer |
| `duration` | required | how long the mob is committed (normally the clip length) |
| `cooldown` | 0 | *time range*: before it can be used again (a range is rolled at each use) |
| `initial_cooldown` | 0 | *time range*: after engaging, before first use (rolled at each engage) |
| `weight` | 1 | relative chance among the abilities usable right now; `0` = only reached by chaining |
| `range` | any | `{ "min", "max", "above", "below" }` horizontal distance; `above`/`below` default to the reach band |
| `target` | `current` | aim: `current` (threat target / tank), `nearest`, `farthest`, `random` player, `lowest_threat` (the player with the least threat, 0 counts; a tie goes to someone other than the tank), `top_damage` (whoever dealt it the most damage, after armour, in the last 10 s; a tie goes to whoever got there first; if none of the candidates hurt it in that time, the one with the most threat), `slot:<name>` (whoever that [target slot](#target-slots) holds; no holder = the ability is not usable) |
| `track_until` | first telegraph | the mob turns toward its aim until this time, then facing and aim lock |
| `force_after` | none | once fighting this long, use it as soon as it is off cooldown, ignoring range (a fuse) |
| `aim_side` | 0 | blocks to the mob's right where its weapon strikes (one-handed weapon); it turns so that line meets the target |
| `invulnerable` | false | takes no damage while it runs (transitions, intros) |
| `skip_when_full` | false | not picked (nor forced by `force_after`) while none of its `summon` events has room under its `max_alive`; it stays ready and goes as soon as an add dies (no effect on an ability that summons nothing) |
| `fixed_speed` | false | its timeline and clip ignore the phase's `attack_speed` (run at 1): for an ability timed to a host clip that cannot play faster (the Warden's `sonic_boom`: at 1.2 the wave would land 0.28 s before the chest burst) |
| `cooldown_group` | none | a name; abilities with the same group share one cooldown: using one puts every ability of the group on the cooldown it rolled (its own `cooldown`, or the phase's `cooldowns` entry for it), never shortening a group cooldown that already runs longer. Their own initial cooldowns still count separately. A phase's `ready_in` wins over the group: the ability named there is usable at that time even while its group is on cooldown (once; its next use puts the group on cooldown as usual) |
| `no_repeat` | false | never picked right after itself (the ability started last, by name) while another ability can be picked; alone it may repeat. A `force_after` fuse ignores it |
| `min_players` / `max_players` | none | only usable while at least / at most this many [players fight it](#fighting-players) (counted when it would be picked; also holds a `force_after` fuse back) |
| `when` | none | `{ "behind": { "players": 2, "for": 4, "range": 4.5, "angle": 140, "height": 3 } }`: only usable once at least `players` fighting players have each stood behind it (within `range` blocks, the rear `angle` degrees, the whole width, centred on its back, and `height` blocks above or below its feet, so a player on a ledge does not count) for `for` seconds without a break (defaults: 1 player, 0 s, 5 blocks, 120 degrees, 3 blocks). Who stands where is only watched while the ability is allowed by the phase and off cooldown, so the count starts when it comes off cooldown |
| `include_unreachable` | false | its target rule may also pick players the reach rule gave up on (a player on a ledge can still be the farthest); `current` and `slot:` rules are not affected |
| `events` | `[]` | timeline (below) |

Choosing: every tick out of an ability, the mob picks at random (by `weight`) among abilities that are allowed by
the phase, off cooldown (their own and their `cooldown_group`'s), meet their `min_players` / `max_players` and `when`,
have an aim and are in range, leaving out a `no_repeat` ability that went last while there is another; if none is, it
moves per `movement`. An ability with
`"weight": 0, "force_after": 0` is never picked by chance but goes as soon as it is ready, at any range (the Wither's
Grave Call).

## Events

Every event has `type` and a time. Telegraphs appear at `telegraph.from` (default: shortly before impact) and fill up
until the impact; origin and facing are fixed when the telegraph appears, so the warning is exactly the hit area.

### `hit` - area attack
| field | default | meaning |
|---|---|---|
| `at` | required | impact time |
| `shape` | required | see Shapes |
| `origin` | `self` | `self` (on the mob), `target` (where the aim stood when the telegraph appeared) or `each_fighter`: one zone under each [fighting player](#fighting-players), all locked when the telegraph appears and drawn as one telegraph; anyone inside any of them is hit, once per event even where zones overlap. With nobody fighting yet (a forced test) one zone at the aim |
| `lead` | - | with `each_fighter` only: each zone goes where its player is heading, this many seconds ahead at their horizontal speed over the last 0.5 s, at most 4 blocks from where they stand. Fighters' positions are only sampled while an ability with such a hit is allowed by the phase and off cooldown, or running (a chained or forced one starts the sampling as it starts, so its first zones go where the players stand); without a trail, and for a player who just joined, where they stand |
| `once` | false | a victim already struck by another `once` hit of the same ability run is skipped (a line of overlapping circles strikes each player once) |
| `offset` | 0 | push the origin forward along the facing |
| `side` | 0 | push the origin to the mob's right (draw the telegraph under a one-handed weapon) |
| `aim_side` | - | re-aim for this beat: the zone is aimed when the telegraph appears, and the body turns into it during the last 0.3 s before `at` (without a telegraph: turns from 0.3 s before `at`) |
| `height` / `depth` | reach band | how far above / below the origin a victim's feet may be |
| `rotate` | - | degrees added to the facing the ability locked when its `track_until` ended (positive = clockwise seen from above, toward the mob's right); the zone and its hit test are turned by it. Measured from the locked facing, not the body, so a later beat with `rotate: 0` goes back to the first angle. With `aim_side`, added to that beat's re-aim. Unset = the current facing, as before |
| `turn_body` | false | when the telegraph appears, the body turns to the zone's facing evenly over 0.3 s (of the ability's clock) |
| `telegraph` | shown | `{ "show": true, "from": 0.4, "color": "#B01818", "style": "grow", "arrow": false }` (below) |
| `hit` | damage 1.0 | `{ "damage": x attack_damage, "effects": [...], "knockback": 0.4, "knock_up": 0 }` (see below) |

The `telegraph` block (also used by `dash`):

| field | default | meaning |
|---|---|---|
| `show` | true | false = no ground warning |
| `from` | shortly before the impact | when it appears |
| `color` | `#B01818` | `#RRGGBB`; the impact flash is a brighter shade |
| `style` | `grow` | how it fills until the impact: `grow` (the whole shape at once, growing from its origin: rectangles widen, circles and cones grow) or `sweep` (rectangles and dash strips fill along their length, from the origin toward the far end; cones grow their radius; circles as `grow`) |
| `arrow` | false | rectangles (and dash strips) get an arrowhead past their far end, 1.5 times as wide as the rectangle at its base and 0.75 times its width long: a direction marking only, outside the area, nobody standing in it is hit. Other shapes ignore it |

The `hit` block (also used by `trace`, `dash`, `chase` and skulls):

| field | default | meaning |
|---|---|---|
| `damage` | 1.0 | multiplier on the mob's live `attack_damage` (the LdMob way) |
| `amount` | - | instead of `damage`: absolute raw damage at level 0 on Normal, multiplied at hit time by **R** = the mob's live `attack_damage` / the `attack_damage` its `base_attributes` declare, else its `reference_attributes` (1 if the file declares neither, so `amount` then works like `damage`). R is 1 on an unscaled mob and carries what scales its attack (Dungeon Difficulty levels, soft enrage), so a balance sheet lists every hit as one number. Setting both is an error |
| `damage_type` | `minecraft:mob_attack` | damage type id (any datapack's; skulls default to vanilla's `minecraft:wither_skull`); the mob stays the attacker either way. Unknown ids are reported when the server starts or reloads, and those hits deal the default type |
| `effects` | none | `[ { "id": "spell_engine:bleed", "duration": 6, "amplifier": 0 } ]` - any mod's effect id, applied only when the hit lands; unknown ids are skipped with a log warning |
| `knockback` / `knock_up` | 0.4 / 0 | the whole push (below) |
| `stacks` | none | `{ "id": "sculk_brand", "per_stack": 0.12, "max": 4, "duration": 15, "min_players": 3 }`: a landed hit leaves a stack (below). Ability hits only (`hit`, `dash`, `chase`, `trace`, `listen`), not skulls |

Hits ignore vanilla's 10-tick hurt immunity (each event lands at most once per victim), so combo beats and a
slash right after a trample all count. Mod dodges that cancel damage (Combat Roll's roll i-frames) still work.
`knockback` / `knock_up` are the whole push: vanilla's own 0.4 shove on every melee hit is removed, so 0 means the
victim does not budge. Keep knockback low on a combo's opening beats, or the first hit pushes the target out of the
next one's reach.

**Stacks** (`hit.stacks`, the Warden's Sculk Brand): each stack of `id` a player carries from this mob makes its later
ability hits on them `1 + per_stack x stacks` times stronger (several ids multiply); the hit that leaves a stack
counts it only from the next hit on. At most `max` stacks (1); every new one restarts the `duration` (seconds,
required) of all of them, and they all go when it runs out, or when the mob resets or dies. Only while at least
`min_players` players [fight it](#fighting-players) (0 = always). They live on the mob's fight state, not as a mob
effect: milk and cleanses do not remove them, and there is no icon. A player who dies, logs out or leaves the mob's
level loses them at once. The player sees an action-bar line with the
count, translation key `mobs_ld.stacks.<id>` (`%s` = the count), e.g. "Sculk Brand: 3".

### `clip` - play a clip mid-timeline
```json
{ "type": "clip", "at": 1.7, "clip": "sonic_boom" }
```
At `at` the action clip `clip` starts, as the ability's own `clip` does at its start: on an LdMob the GeckoLib action
layer (restarted if it is already playing), on a vanilla host its cue (the Warden's, see
[The Warden](#the-warden-dataminecraftmob_behaviourwardenjson)); names a host does not know do nothing. It plays at
the ability's speed and replaces the ability's clip; the ability's end stops it as usual. The ability's own clip goes
out on the tick its timeline reads 0, so a `clip` at 1.7 and a hit at 1.7 both land 34 ticks after it (at speed 1).

### `vulnerable` - damage taken window
```json
{ "type": "vulnerable", "at": 3.4, "until": 5.0, "multiplier": 1.25 }
```
From `at` to `until` the mob takes `multiplier` times the damage (an opening after a big attack). It stacks with the
phase's `damage_taken` and with other windows open at the same time, and closes early if the ability is cut off
(phase change, stagger, reset). The ability lasts at least until `until`. `until` before `at` is an error.

### `listen` - punish whoever moves
```json
{ "type": "listen", "from": 1.0, "until": 3.0, "range": 40, "move_tolerance": 0.6,
  "triggers": ["damage", "use_item", "sprint", "jump", "move"],
  "hit": { "amount": 13.0, "damage_type": "mobs_ld:warden_sonic" },
  "chain_to": "sonic_lance", "chain_at": 4.2, "sound": { "id": "minecraft:entity.warden.sonic_boom", "volume": 2 } }
```
From `from` to `until` the mob listens to every [fighting player](#fighting-players) within `range` blocks when the
window opens. Whoever gives themselves away takes `hit` at once (once per window), with a line of `particles`
(default `minecraft:sonic_boom`, one per block) from the mob's chest to them and `sound` (none by default) at the mob.
At `chain_at` (default `until`; not before it) the ability hands over to `chain_to` (checked at load, like a `chain`),
aimed at the first offender who is still alive, in the mob's level and someone it may hit; if there is no such
offender (nobody was caught, or they all died or left) there is no hand-over and it simply runs to its end.

| trigger | what gives a player away during the window |
|---|---|
| `damage` | damaging the mob or one of its adds by an action during the window: a melee hit (or a spell the player strikes with directly), or a projectile or other damaging entity of theirs that came into being after the window opened. Arrows already in flight when it opened, damage over time and effects (no direct source), and thorns do not count |
| `use_item` | starting to use an item: drawing a bow or crossbow, raising a shield, eating (one already raised when the window opened is not "starting"; lowering and raising it again is); using an instant item: an ender pearl, a snowball, a splash potion, a firework (the stack shrinks or goes on cooldown; a right-click that does nothing does not count). Also starting a Spell Engine cast with a cast time or a channel |
| `sprint` | sprinting at any moment |
| `jump` | a jump (the server's jump statistic) |
| `move` | being more than `move_tolerance` (0.6) blocks, horizontally, from where they stood when the window opened (a roll moves about 3) |

`triggers` defaults to all five. The players are only read during the window, once per tick.

- **Pushes are forgiven.** The server counts a hit's knock-up as a jump, and knockback moves a player. For 10 ticks
  after a player was hurt or had their velocity set by the server (knockback, a knock-up, a shove), `jump` and `move`
  are not held against them, and the spot they must keep becomes where they land. `use_item` and `sprint` still
  count. A shove the server does not see (a wind charge's push is applied by the player's own client) is not
  forgiven.
- **Not caught:** an instant Spell Engine spell that deals no damage (a heal or a buff without a cast time): it is
  neither an item use nor a cast in progress. A mod's damage over time that names the player as its direct source
  counts as damage.

### `trace` - weapon hitbox that follows the animation
`from`, `to` (active window, seconds), `path` (bones, e.g. `["blade_right", "hit_blade_right"]` = wrist to blade
tip), `radius` (0.6, capsule thickness), optional `clip` (default: the ability's clip), optional `aim_side`
(re-aim for this beat, e.g. a left-hand backhand in a right-hand combo; turns from 0.3 s before `from`), `hit`.
A re-aim turns the whole body, so let a swing's window end before the next beat starts turning (0.3 s before its
`from`, or before the `at` of a telegraphed `hit`), or the swing is dragged off its line.
From `from` to `to` a capsule is swept along the bones' baked positions; anyone it passes through takes `hit`
once. No ground telegraph: players read the swing. Use it for blade swings and punches; keep `hit` + telegraph
for ground slams and area mechanics.

Needs the mob's **hit tracks**: `data/<ns>/hit_tracks/<mob>.json`, baked in a client with
`/mobs_ldc bake <mob>` (writes `<game dir>/mobs_ld_bake/<ns>/<mob>.json`; copy it in). The server cannot run
GeckoLib animations, so it replays these. **Re-bake after any model or animation change.** With
`/mobs_ld debug` on, swept blades show as electric sparks. Pick windows from the baked track (when the
blade moves fast through body height, 0-1.8 blocks).

### `dash` - charge in a straight line
```json
{ "type": "dash", "at": 1.8, "speed": 1.4, "overshoot": 5, "max_distance": 28, "width": "body", "height": 0.4,
  "push": "side", "on_wall": "stagger", "stagger_min_distance": 4,
  "telegraph": { "from": 0.8, "style": "sweep", "arrow": true, "color": "#6A2CB0" },
  "hit": { "amount": 20.0, "knockback": 0.8, "knock_up": 0.3 } }
```
| field | default | meaning |
|---|---|---|
| `at` | required | the body starts moving |
| `until` | - | it covers its lane evenly by then; needed unless `speed` is set |
| `speed` | - | instead: a constant speed in blocks per tick (a sprinting player is ~0.28), faster with the phase's `attack_speed` like the rest of the timeline; it runs until the lane is covered (with `until` as well: at most until then) |
| `max_distance` | required | longest lane |
| `overshoot` | 1.0 | blocks past the aim |
| `width` | 2.0 | lane width in blocks, or `body`: the host's rendered body (the Wither: 3 x its scale; other mobs: their hitbox width) |
| `height` | none | blocks above the aim's feet (where it stood when the dash locked) that the body's feet are driven to every tick: a flyer rushing low. Without it the body keeps its own vertical speed (gravity, a jump), and a host that holds its altitude (the Wither) stays level |
| `push` | `away` | which way `hit`'s knockback shoves: `away` from the body, or `side`: out of the lane on the side the victim stands (dead centre: to the lane's right) |
| `on_wall` | `stagger` | `stagger` (the behaviour's `stagger` window) or `stop` |
| `stagger_min_distance` | 4 | a wall only staggers after this much travel; hit sooner, the dash just stops |
| `telegraph` | shown | the strip (below); `{ "show": false }` for an untelegraphed rush |
| `hit` | none | applied once to everyone the body meets |

- **Locking.** Direction, length and lane lock when the telegraph appears (at `at` without one): the lane runs from
  the body's centre straight at where the aim stood then (followed until the ability's `track_until`),
  `distance to the aim + overshoot` long, at most `max_distance`, and the body turns along it. An untelegraphed dash
  that must lock earlier ends `track_until` that much before `at` (the Wither's second rush locks 0.3 s ahead).
- **Contact = the drawn strip.** A victim is hit when its centre is on the lane (within `width` / 2 of the centre
  line, between the start and where the body's centre stops, the side edges included) as the body passes it (the
  body reaches half its hitbox ahead and behind its centre; vertically, the victim's hitbox must come within half a
  block of the height the body's hitbox spanned during the tick). The test is swept every tick from the old to the
  new position of both (the dash remembers where everyone near the lane was at its previous tick, from a few blocks
  ahead of the body), so a 1.4 blocks per tick rush cannot skip a player and a head-on roll through it still meets
  it. Each victim is struck at most once per dash,
  even when a dodge blocks it: a Combat Roll whose i-frames take the contact clears that player for the rest of the
  dash. A sideways drift (a shove, a hover settling) is pulled back onto the centre line.
- **The strip** is laid on the ground in segments of up to 6 blocks: the floor is sampled every block along the
  centre line with the deep ground scan (from 2 blocks above the height the body will rush at, down
  `movement.ground_scan`, so under the Nether roof it is the floor; where the ground rises above that start, the scan
  starts at the first open block above it, so the strip lies on top of the rise, not inside it), blocks whose floors
  are within half a block of each other share a segment (laid at their highest floor, so no part sinks into a rise),
  and parts over the void are left out. With `style: sweep` it fills from the mob toward the tip, one segment after
  the other; `arrow: true` adds the arrowhead past the end, laid on the highest floor of its own samples (left out
  only if none has a floor). A body held at `height` (or by a host that holds its altitude) flies level: over a dip
  or a downslope it passes above players more than half a block below its hitbox, and rising ground in the lane is a
  wall.
- **Walls** are blocks (and the world border): a dashing body passes through entities with collision boxes (boats,
  shulkers, minecarts), and climbables (vines, ladders, scaffolding) do not slow it. Running into a wall ends the dash;
  after `stagger_min_distance` blocks of travel with `on_wall: stagger` it puffs smoke and the mob staggers (the
  behaviour's `stagger` block; without one it just stops).

### `chase` - homing run that hands over to another ability
`at`, `clip` (loop played while running), `max_time`, `speed` (blocks per tick; a sprinting player is ~0.28),
`turn_rate` (6, degrees per tick - lower is easier to side-step), `reach` (3.0), `on_reach` / `on_timeout` (ability
names to chain into), `on_wall` (`stagger` | `stop`), `width` + optional `hit` (trample, once per victim).

Chaining (a chase's hand-over, a `chain` event): the chained-into ability starts at once, skipping
`global_cooldown`, its range and the phase's ability list. It keeps the same aim if its own `target` is `current`
(the default); any other rule picks its aim again (below). Give such follow-ups `"weight": 0` so they are never
picked on their own. Gorefist's charge is `charge` (crouch tell, then
chase) -> `charge_attack` on reach or `charge_exhaust` (a 2.5 s vulnerable window) after 10 s.

### `chain` - hand over to another ability
```json
{ "type": "chain", "at": 2.9, "to": "soul_rush_follow" }
```
At `at` the ability ends (events still running are cut off, telegraphs vanish) and `to` starts at once (`to` must be
one of the file's abilities). The follow-up keeps this ability's aim if its `target` is `current` (the default);
any other rule picks again, so `"target": "slot:left_head"` turns it on whoever the left head holds now (with nobody
there it keeps the old aim). A stagger, phase change or reset before `at` ends the ability without the hand-over.
With `"taunt_wins": true` (default false) a taunt lock beats the kept aim: if a live taunt or provoke holds the mob
when the hand-over happens, a `current` follow-up goes for the taunter instead (a taunt during the Warden's Hunting
Sniff moves its Deep Charge onto the taunter).
With `"if_ready": true` (default false) the hand-over only happens if `to` could start on its own now: its phase allows
it, its cooldown (and initial cooldown) is over and, with `skip_when_full`, a summon of it has room; else the ability
just plays on to its end. The follow-up's cooldown starts as usual. The Wither's last dash of each Soul Rush chains into
Grave Call this way, so its skeletons rise after a full sequence, and never after one that ended in a wall stagger.

### `summon` - adds
```json
{ "type": "summon", "at": 1.6, "entity": "minecraft:wither_skeleton",
  "count": { "min": 2, "max": 4, "per_extra_player": 2, "cap": 12 },
  "max_alive": { "base": 6, "per_extra_player": 3, "cap": 16 },
  "placement": { "around": "fighters", "radius": [3, 6], "min_gap": 2, "clearance": 3, "ground": true },
  "telegraph": { "from": 0.2, "radius": 0.9, "color": "#3A1F5C" },
  "scale": { "max_health": { "of_summoner": 0.025 }, "attack_damage": 1.0, "armor": "summoner_bonus", "damage_dealt": "copy" },
  "loot": false, "xp": false, "bound": true, "target": "rune_player" }
```
Only `at` and `entity` are required; every other field is optional, and left out the summon works as it always did
(Gorefist's Rot Warts): `count` adds scattered up to `spread` blocks around the offset point, at `at`, while fewer than
`max_alive` of the mob's adds live, each on the summoner's aim, dropping their usual loot.

| field | default | meaning |
|---|---|---|
| `at` | required | the adds rise |
| `entity` | required | entity id (any mod's) |
| `count` | 1 | a number, or `{ "min", "max", "per_extra_player", "cap" }`: a random count in [min, max] (max defaults to min), plus `per_extra_player` for each [fighting player](#fighting-players) beyond the first, at most `cap` |
| `max_alive` | 8 | no more spawn while this many of the mob's adds (of any summon) live: a number, or `{ "base", "per_extra_player", "cap" }`. A wave only fills the free room |
| `forward` / `up` / `right` | 0 | offset of the spawn point from the mob, along its facing |
| `spread` | 1.0 | random scatter around that point (without a `placement`) |
| `telegraph` | none | runes: `{ "from", "radius": 0.9, "color", "style", "show" }` (the telegraph block, plus the circle's `radius`). The wave (how many, where) is decided when the runes appear at `from` (default 1 s before `at`), one circle per add on its spot; the adds rise on them at `at`. Without it nothing is drawn and the wave is decided at `at` |
| `placement` | none | where the adds rise instead of the scatter (below) |
| `scale` | none | the adds' stats from the summoner's (below) |
| `loot` | true | false: the adds drop nothing when killed: their whole death drop step is skipped (no loot table drops such as a wither skeleton's skull, no custom death drops, so a charged creeper's one skull is not spent on them, no equipment), any other item they would drop while dying is held back too (mods' death hooks), and they never pick items up (those would vanish with them) |
| `xp` | true | false: the adds drop no experience |
| `bound` | false | true: the adds also leave (a puff of smoke, no drops) once the summoner is dead or gone, even where it cannot reach them (below) |
| `target` | `aim` | whom each add goes for first: `aim` (the ability's aim) or `rune_player` (the player its rune was placed for; the aim when there is none). A `rune_player` add that runs threat targeting (vanilla monsters do under the default threat config) starts with 1 threat on its player, so it stays on them instead of turning to whoever is nearest, until someone builds more threat on it (one hit, a taunt) |
| `minion_link` | - | `{death_damage, all_dead}` (at least one): an add that is **killed** hurts its summoner, and the summoner takes more damage once all such adds are dead (below) |

Adds are spawned inside the summoner's tick (Arenas_LD treats them as minions of a run boss), counted as its adds for
`max_alive` and `skip_when_full`, and removed when the summoner dies or resets, for every summon. The drop and bond
marks are entity tags (`mobs_ld_no_loot`, `mobs_ld_no_xp`, `mobs_ld_summon` + `mobs_ld_summoner:<uuid>`), set before
the add enters the level, so they survive restarts.

**Fighting players.** Players with threat on the summoner's table (they hit, healed against or debuffed it), alive,
not spectating or in creative, within 48 blocks. Bystanders do not count. With nobody fighting, `per_extra_player`
adds nothing (a solo count).

**`placement`**

| field | default | meaning |
|---|---|---|
| `around` | `self` | `self` (the floor under the offset point), `aim`, `fighters`: each rune is placed for one fighting player, one each in turn for two rounds (so a short wave reaches as many players as it can), the rest to random players; the order is shuffled per wave; or `home`: the floor under the mob's home point (where it first ticked, the spot it walks back to after a reset: an arena's centre for a boss spawned there), wherever the mob and the players stand now |
| `radius` | [2, 4] | blocks from that centre: a number or `[min, max]`, rolled per spot |
| `min_gap` | 1.5 | least distance between two runes of a wave |
| `clearance` | 2 | open blocks needed above the floor (a wither skeleton is 2.4 tall: 3) |
| `ground` | true | spots on the floor; false: at the centre's height in open air (flyers) |

A spot is valid with a solid floor, `clearance` blocks above it free of collision and fluid, no fluid on the way down
or in the floor (lakes, lava seas, waterlogged blocks), at least `min_gap` from the wave's other runes, in a loaded
chunk inside the world border. The floor is scanned from one block above the centre's feet (a step up is fine, a
tunnel's roof is never taken for the floor) at most 6 blocks down, so adds do not rise at the foot of a cliff; spots
are centred on their block. Each rune gets 8 random tries around its centre; then it goes next to the summoner's
ground shadow (1.5-3 blocks from the floor under it, keeping the gap if it can); over the void (or a lava sea) with
nothing valid there either, that rune is skipped.

**`scale`**: each part set brings that stat of the add to a target, from the summoner's live stats; parts left out
stay the add's own.

| field | target |
|---|---|
| `max_health` | a number: the add's own max health x this factor x (the summoner's max health / the base its behaviour declares in `base_attributes`, else in `reference_attributes`, else its own base value); or `{ "of_summoner": f }`: the summoner's max health x `f`, whatever the add's own is |
| `attack_damage` | the same two forms; as a number, the add's own attack damage x this factor x (the summoner's attack damage / its declared base), i.e. R; a soft enrage carries over to adds raised after it |
| `armor` | the add's own armor + `summoner_bonus` (the summoner's armor above its declared base, never negative), or + a fixed number |
| `damage_dealt` | `copy`: the add's `loot_stage:damage_dealt` (DD Loot Stage) is set to the summoner's current value. Looked up by id: skipped if that attribute is not installed or either mob lacks it |

"Own" is the add as vanilla made it, read before it enters the level: its base values and modifiers plus its
equipment's (a wither skeleton: 20 HP, attack 4 + its stone sword's 4 = 8, armor 0). So the summoner's level carries
over, not the add's own spot's. Once the add is in the level (where Dungeon Difficulty and Arenas_LD's minion scaling
change new mobs, both inside the spawn), one modifier per stat, `mobs_ld:summoner_scale`, brings the live value exactly
to the target (`target / current - 1` on the total; a flat bonus for an armor of 0), so nothing scales it twice, and
the add is healed to its new maximum. This happens once, at the spawn: the equipment's modifiers (which vanilla only
applies on the add's first tick) are counted in up front, and later changes (Weakness, Health Boost and other effects)
stay temporary. The modifiers are permanent: they are saved with the add. Example, a Wither skeleton of the shipped Wither
(`max_health` `{ "of_summoner": 0.025 }`, base attack 2.0, armor 4): at 4986 HP and armor 13 it gets 124.65 HP and
9 armor; at attack 2.0 (R = 1) its swing stays 8.

**`bound`**: once every second the add checks on its summoner (only bound adds pay for this). It leaves in a puff of
smoke, without dying or dropping anything, when the summoner is dead or removed, no longer counts it among its adds (a
reset while the add was unloaded), or has not been loaded in the add's level for 5 s while the add ticks (a restart or
chunk unload that kept only the add, the summoner gone through a portal or discarded).

**`minion_link`**: `{ "death_damage": { "of_minion_max_health": 0.5, "cap_of_summoner_max_health": 0.03 },
"all_dead": { "damage_taken": 1.5, "until": "next_summon" } }`.
- `death_damage`: when one of these adds dies (a real death: killed by anyone, `/kill`; not an add that vanishes in a
  puff of smoke, is discarded by a reset or despawns), its summoner takes `of_minion_max_health` x the add's max health,
  at most `cap_of_summoner_max_health` x its own max health (no cap without it). The damage is `mobs_ld:minion_link`,
  credited to the add's killer (whoever the killing blow names, else whoever last hurt it; nobody if that is the summoner
  or one of its adds), so it counts for the killer in damage shares (Loot Stage, `top_damage`, threat). Armour,
  Protection, Resistance, shields and the hurt cooldown do not reduce it, nor knock the summoner back, and per-mob damage
  multipliers leave it alone (`#loot_stage:unscaled`); the summoner's own `damage_taken` rules, `vulnerable` windows and
  `invulnerable` abilities still apply.
- `all_dead`: once an add of a linked summon dies and none of the summoner's linked adds is left alive, the summoner takes
  `damage_taken` x damage until it raises linked adds again (`until`: `next_summon`, the only value), resets or dies.
  The fighters see "All minions are dead: <name> takes +50% damage!" over the hotbar (`mobs_ld.minion_link.exposed`,
  en_us and uk_ua; servers without the client mod's language file send the English text), hear a shield break and see
  sparks (`enchanted_hit`) on the summoner while it lasts.
- A link is kept per add with the summoner's saved adds; a boss that loads mid-fight resets, which removes its adds and
  the exposure.

### `cloud`
`at`, `origin`, `radius`, `duration`, `effects` - a lingering area-effect cloud.

### `effect`
`at`, `radius` (0 = the mob itself), `effects` - buffs on self or debuffs on enemies around. With `"on": "aim"`
(default `around`, as above) the effects go on the ability's aim alone (if it is alive), credited to the mob, and
`radius` is not used: the Warden's Hunting Sniff puts Glowing on the player it picked.

### `despawn`
`at` - removes the mob in a puff of smoke (no death, drops or `on_death`), e.g. after a self-destruct.

### `hover` - fly to a height and hold it (flyers)
```json
{ "type": "hover", "from": 0.0, "until": 0.8, "relative_to": "aim", "height": 4, "rate": 0.5, "retreat_to": 12 }
```
| field | default | meaning |
|---|---|---|
| `from` / `until` | required | active window |
| `height` | required | blocks above the reference (may be negative) |
| `relative_to` | `ground` | `ground` (the floor under the mob, see `movement.ground_scan`; over the void it keeps its altitude), `aim` (the aim's feet: tracked until `track_until`, then where it stood), `self` (its own height when the hover began) |
| `rate` | 0.4 | top speed in blocks per tick (scaled with the phase's `attack_speed`) |
| `retreat_to` | none | also back off horizontally until this far from the aim (only backs off; never closes in) |

Every tick its velocity is set straight toward the point (horizontally it holds still unless `retreat_to` moves it),
so a flyer stays pinned whatever its own move control does with gravity. Chain several for a rise, a slam and a
low recovery. Meant for flying hosts (the Wither, flying Mobs_LD mobs); a walker would float.

### `look` - turn heads onto someone
```json
{ "type": "look", "at": 0.0, "until": 1.8, "heads": "all", "target": "aim" }
```
| field | default | meaning |
|---|---|---|
| `at` / `until` | required | active window |
| `heads` | `all` | `all`, `side`, `center`, `right`, `left` (the host's own right) |
| `target` | `aim` | `aim` (the ability's aim), `current` (the main target now) or `slot:<name>`; read every tick |

While it runs the heads it names follow `target` instead of their target slots (a tell: all heads snap onto the
player a rush is aimed at), and those side heads hold their own skull fire; afterwards the slots take them back.
On a mob with one head, `center` turns its head (the body still faces the ability's aim).

### `fx` - sound, particles, text
```json
{ "type": "fx", "at": 1.7, "particles": { "id": "minecraft:sonic_boom", "at": "along", "every": 2, "count": 1, "spread": 0 } }
{ "type": "fx", "at": 0.0,
  "sound": { "id": "minecraft:entity.wither.ambient", "pitch": 0.7, "volume": 2 },
  "particles": { "id": "minecraft:soul_fire_flame", "at": "heads", "count": 12, "spread": 0.3 },
  "message": { "key": "mobs_ld.cue.wither.grave_call", "where": "actionbar" } }
```
All parts are optional and sent from the server (no client mod code).
- `sound`: any sound id (`pitch` 1, `volume` 1), played at the mob; an id no mod registers still plays if a resource
  pack defines it.
- `particles`: a particle type that needs no extra settings; `at` = `self` (middle of the body), `heads` (every head:
  the Wither's three mouths, a one-headed mob's eyes), `aim` (middle of the aim's body) or `ground` (the floor under
  the mob) or `along` (below); `count` 8, `spread` 0.3 blocks, `speed` 0.02; `column` (0) = instead of a puff, a
  column this many blocks tall rising from the point, the `count` spread over its height (`spread` then only
  sideways).
- `particles.at: along`: along the zones of the ability's `hit` events that land at the same `at` as this fx, one
  point every `every` blocks (default 1) on each zone's centre line (every ray of `rays`, a rectangle's or cone's
  middle line, a circle's rim), at the height of the mob's chest (its center muzzle: the Warden's chest, else its
  eyes) above the zone's floor; `count` is per point, so `"count": 1, "spread": 0` gives a clean line. Sent once the
  beat's hits have run, whatever the event order. An `along` fx with no `hit` landing at its `at` is an error.
- `message`: a translation key (`assets/<namespace>/lang/*.json`) shown in the action bar of every player within 64
  blocks or on the mob's threat table. The action bar, because Arenas_LD's timed runs hide boss bars.

### `projectile` - skull volley
```json
{ "type": "projectile", "at": 1.0, "skull": "volley", "from": "heads", "aim": "slot:right_head",
  "waves": 3, "interval": 0.5, "spread": 1.5 }
```
| field | default | meaning |
|---|---|---|
| `at` | required | first wave |
| `skull` | required | a skull spec from the host block's `skulls` (validated at load) |
| `from` | `heads` | `heads` / `all`, `side`, `center`, `right`, `left`; a host without those heads fires from its eyes |
| `aim` | `aim` | `aim`, `current` or `slot:<name>`, read at each wave (so a wave goes where the target is then) |
| `waves` / `interval` | 1 / 0.5 | how many waves, how far apart |
| `spread` | 0 | each wave fires one skull per head as a flat wall across the line of fire, its ends `spread` blocks to either side of the target (the host's right head takes the right end) |
| `speed` | the spec's | blocks per tick |
| `dangerous` | false | vanilla's blue skulls (only the look: speed and damage stay the spec's) |

Skulls fly at a constant speed from launch, so one reaches a target `d` blocks away after `d / speed` ticks (vanilla
skulls start slow and settle at 1.9; the blue ones at 0.27). They are vanilla wither skulls, spawned inside the mob's
tick, and deal their spec: see [The Wither](#the-wither-dataminecraftmob_behaviourwitherjson).

## Target slots

Secondary targets the mob keeps besides its main one (the threat top or taunt lock). The host adapter decides how a
slot shows: the Wither's `right_head` and `left_head` slots turn those heads. Abilities aim at a slot with
`"target": "slot:<name>"`, `look` and `projectile` events with `slot:<name>`.

```json
"target_slots": {
  "right_head": { "rule": "lowest_threat", "range": 30, "line_of_sight": true, "repick": [9, 11], "lost_after": 2 },
  "left_head":  { "rule": "spread", "range": 30, "line_of_sight": true, "repick": [9, 11], "offset": 5, "lost_after": 2 }
}
```

| field | default | meaning |
|---|---|---|
| `rule` | required | `lowest_threat` or `spread` (below) |
| `range` | 30 | candidates within this many blocks of the mob |
| `line_of_sight` | true | candidates must be in sight of the slot's head (the Wither's side mouths; the eyes for a slot without a head) |
| `repick` | 10 | *time range*: picks again this often (at least 0.5) |
| `offset` | 0 | re-pick this long after each pick of its lead (the first slot listed without an offset), for the whole fight, whatever made either pick, so the two never switch together; must be shorter than the lead's `repick`. Without a lead it only delays the first scheduled re-pick |
| `lost_after` | 2 | a holder out of sight this long is replaced |

Candidates: players that are alive, attackable, not stealthed (Rogues' vanish, Infiltrator's Vanish), not spectating or in creative, within
`range` and (with `line_of_sight`) visible from that head. Pets and summons only as a spread slot's last resort;
other mobs never.
- `lowest_threat`: the player with the least threat on the mob's table (0 counts). A tie goes to someone other than
  the main target, then at random.
- `spread`: a player that neither the main target nor any other slot is on (avoiding its own previous pick when there
  is another choice); else the player with the fewest of them on him, ties away from its previous pick (so with two
  players it alternates at each re-pick); only if no player qualifies, a pet or summon with threat on the table, held
  only until a player qualifies again (checked every quarter second).

A slot picks as soon as the fight starts (lowest-threat slots first, so a spread slot sees their picks), then every
`repick` (a slot with an `offset`: that long after each of its lead's picks), and at once when its holder dies,
leaves, goes out of range, vanishes, or has been out of sight for `lost_after`. With nobody to pick it tries again every half second. Slots empty when the fight ends (reset, wipe,
disengage). Results for the Wither's heads (middle head = the main target):

| Group | Middle | Right (`lowest_threat`) | Left (`spread`) |
|---|---|---|---|
| Solo (with or without a pet) | the player (or a pet holding aggro) | the player | the player |
| 2 players | tank | other player | alternates between them |
| 3+ players | tank | lowest threat | a third player |

## Shapes
| type | fields |
|---|---|
| `rectangle` | `width`, `length`, `offset` (start distance ahead, 0) |
| `cone` | `radius`, `angle` (degrees, total width) |
| `circle` | `radius` |
| `rays` | `count` (even; lines through the origin, the first on the facing), `length` (each way), `width`, `widen` (0: blocks of extra lane width per block along the lane, so a lane is `width + widen x distance` wide there; drawn and tested as trapezoids; widening lanes are at most 48 long) |
| `ring` | `inner`, `outer` (blocks, `outer` larger): a band around the origin; a victim whose feet are between `inner` and `outer` blocks away horizontally (both edges included, within the event's `height` / `depth`) is hit, inside `inner` is safe. Its telegraph fills outward across the band (`grow` and `sweep` alike). Clients from before rings draw nothing for it (the hit still lands) |

## Phases

```json
"phases": [
  { "below_health": 1.0 },
  { "below_health": 0.5,
    "transition": { "clip": "enrage", "duration": 1.8, "invulnerable": true, "events": [ ... ] },
    "ready_in": { "summon_rot_warts": 5 },
    "cooldowns": { "summon_rot_warts": 18 },
    "attack_speed": 1.15,
    "damage_taken": [ { "kind": "melee", "multiplier": 0.34 } ],
    "texture": "phase2" }
]
```

| field | meaning |
|---|---|
| `below_health` | active from this fraction of max health down to the next phase |
| `abilities` | names usable in this phase (empty = all) |
| `transition` | ability played once on entering (cancels whatever was running) |
| `ready_in` | on entering, these abilities become usable after N seconds |
| `cooldowns` | per-ability cooldown overrides (*time range*s) |
| `attack_speed` | timelines and clips run this much faster |
| `damage_taken` | `[ { "kind": "all", "multiplier": 0.5, "while_adds": "mobs_ld:sculk_resonator" } ]`: `kind` is `melee`, `projectile`, `other` (spells, fire, ...) or `all`; rules multiply. With `while_adds` (an entity id) the rule only applies while one of the mob's own live adds (its summons) is of that type |
| `texture` | uses `<mob>_<texture>.png` if it exists |
| `movement` | overrides `movement` |
| `bar` | `{ "color": "blue", "overlay": "notched_10" }`: each part set replaces `boss.bar`'s while the phase runs; on a vanilla host with its own bar (the Wither) it recolours that bar, even without `boss.bar`, and a part neither sets goes back to the host bar's own look |
| `host` | host options for this phase, laid key by key over the file's `host` block (nested blocks merge too: `{ "hover_height": 0, "skulls": { "center": { "every": 3.5 } } }` changes only that skull's cadence); it cannot change the `type` |

## Boss

```json
"boss": { "bar": { "color": "red", "overlay": "progress" }, "leash": 32, "reset_after": 15,
          "soft_enrage": { "after": 420, "damage_multiplier": 2.0, "clip": "enrage" }, "engage_on": "players" }
```

- `bar`: vanilla boss bar, shown while engaged.
- `engage_on`: `any` (default: whatever its own AI targets) or `players`: only players, player-owned summons on its
  threat table and mobs that hurt it in the last 10 s can be its target, so only they start or steer the fight
  (cows and endermen never pull it).
- `leash`: if pulled further than this from its spawn point it resets and walks home (invulnerable and ignoring
  everyone meanwhile).
- `reset_after`: seconds without any target before it resets (a wipe).
- `soft_enrage`: after `after` seconds in combat its damage is multiplied.
- **Inside an Arenas_LD run** (the mob carries Arenas' run tag `arenas_ld_run_mob`) `leash` and `reset_after` are off:
  Arenas owns the wipe there. `/mobs_ld reset` still works.
- Reset = full heal, phase 1, cooldowns and enrage cleared, adds removed. Bosses never despawn while their behaviour
  has a `boss` block (decided by entity type; the mob's own `PersistenceRequired` is not touched).

A mob **without** a `boss` block never resets: after 10 s with no target it just stops fighting, as it is (no heal,
phase kept), and goes back to wandering; it engages again (intro, initial cooldowns) when it next has a target.

Saved with the mob (chunk unload, restart), for every mob with a behaviour: home, the adds it summoned, and whether
a fight was on (phase, enrage, fight time; `mobs_ld_combat` in its NBT). A chunk only unloads once every player has
left, so a **boss** that comes back mid-fight (past phase 1, fight time, or adds alive) **resets** on its first tick:
full heal, adds removed, threat cleared, walks home. Other mobs come back at phase 1, not engaged, and start their
next fight fresh (keeping their health); their adds are still removed when they die.

## Vanilla hosts

Any goal-based mob runs a behaviour placed at its entity id, e.g. `data/minecraft/mob_behaviour/zombie.json`. Every
zombie picks it up on its next AI tick, including after a `/reload`; deleting the file hands them back to their
vanilla AI. Everything above works the same, except:

- **Goals.** The engine adds its combat goal (priority 0, above every vanilla attack goal) and threat targeting. While
  engaged it moves per `movement` and attacks only through its abilities; vanilla attack goals (and wandering,
  sun shelter) run only outside a fight, so most hosts should have no `boss` block and drop out after 10 s without
  a target. With `movement.style: vanilla` the vanilla goals keep running in the fight too, between abilities (a
  ranged host keeps shooting). Vanilla target goals skip players the engine gave up on. `hit.damage` multiplies the
  mob's own `attack_damage` (a zombie's is 3), `hit.amount` is scaled by R; a mob without that attribute (cow, ghast)
  deals no hit damage (one log warning).
- **Clips and textures.** Vanilla models have no action layer: an ability's `clip`, the stagger and enrage clips and
  a phase `texture` become cues for the entity type's host adapter. Without one (the default) nothing shows; the
  timeline, telegraphs and hits run regardless.
- **No `trace`.** Hit tracks come from Mobs_LD's own models, so a `trace` event does nothing on a vanilla mob (one log
  warning). Use `hit` with a telegraph.
- **Boss bar.** `boss.bar` gets an engine bar, unless the host adapter supplies the mob's own bar (then only its
  colour and overlay are applied, and a phase's `bar` recolours it even without `boss.bar`). A behaviour with a
  `boss` block keeps the mob from despawning.
- **Not supported**, logged and ignored at reload: the Ender Dragon (its AI bypasses the mob tick) and Brain-driven
  mobs (piglins, hoglin, zoglin, breeze, villager, allay, axolotl, frog, tadpole, goat, camel, sniffer, armadillo),
  which ignore goals. The Warden is a Brain mob too, but its host adapter drives its Brain (see
  [The Warden](#the-warden-dataminecraftmob_behaviourwardenjson)); other mods' Warden subclasses stay refused.
  Players and armor stands are not mobs.
- **Flyers and climbers.** A mob with flying navigation, a flying body (ghast, phantom), a flying move control, no
  gravity (vex) or a wall-climbing navigation (spider) always counts as able to reach its target by `path`, so it is
  never made to drop aggro as unreachable; give a ranged flyer `reach_rule: line_of_sight` instead, so hiding from it
  works. The reach band is still measured from its feet, so a hovering mob needs `movement.reach_depth` (or
  `range.below`) to start abilities on targets under it. A blaze (ground navigation, rises on its own) is still
  judged by ground paths.
- **Dimensions.** A host carried to another dimension (portal, `/tp`) keeps fighting and re-homes where it arrives;
  only a mob loaded back from disk mid-fight resets.
- **Base stats.** `base_attributes` sets base values (any attribute id, other mods' too) on every mob of the type as
  it is constructed, server side: before the host's own constructor fills its health from them and before Dungeon
  Difficulty scales it on entering the world. Setting `max_health` also fills its health. Mobs loaded from disk keep
  their saved base values; ids that are unknown or that the entity does not have are reported at reload and skipped,
  and a value outside the attribute's range (as it stands with the installed mods, e.g. AttributeFix lifts
  max_health's 1024 cap) is reported and clamped by the game. On a dedicated server AttributeFix widens ranges only
  once the server has started, so that report can be a false alarm there. Some vanilla mobs rewrite a few of their
  own base values after spawning (wither skeleton attack damage in finalizeSpawn, slime and magma cube size stats,
  horse stats, phantom size, ravager speed, tamed wolf health, ...); a file cannot override those values on them.
  `reference_attributes` sets nothing: it only names the base R and summon scaling divide by, for a host whose real
  stats come from its spawner.
- **The `host` block.** `{ "type": "<adapter type>", "enabled": true, ...options }`. `"enabled": false` switches the
  file off: the type is not hosted at all, exactly as if the file were absent (a server datapack can override a file
  shipped in a jar this way). `type` names the options its host adapter reads (only the Wither has one so far:
  `mobs_ld:wither`, `mobs_ld:warden`); every Wither option defaults to vanilla. A `type` for another adapter, and keys
  nobody defines, are reported at reload and ignored. Phases may override options (see Phases), but not
  `only_tagged` or `enabled`.
- **Only some mobs of the type: `host.only_tagged`.** `["arenas_ld_run_mob"]` (any list of entity tags) makes the
  file apply only to mobs carrying at least one of the tags; every other mob of the type never gets the engine and
  stays fully vanilla (its goals, stats, despawning). Arenas_LD tags every mob of a run this way before it enters the
  world, and tags are saved, so it survives restarts. The decision is made when the engine would attach (the mob's
  first AI tick) and again after each `/reload`: a tag added later by a command counts from the next reload. A file
  with `only_tagged` may not have `base_attributes` (it is refused at reload): those are set while the mob is being
  built, before it carries any tag, so they would reach every mob of the type. Declare the numbers' base in
  `reference_attributes` instead and let the spawner set the stats.

### The Wither (`data/minecraft/mob_behaviour/wither.json`)

Mobs_LD ships a behaviour for the vanilla Wither; a datapack can replace it at the same path or switch it off with
`{ "host": { "enabled": false } }`. The Wither stays the vanilla entity: model, build ritual, the 11 s blue charge
(which now ends at full health, and whose blast deals `mobs_ld:wither_spawn_blast`), death, loot and the nether star.

- **Start.** A built Wither starts fighting once its charge is over; one made by `/summon` (or a spawner, or Arenas_LD)
  starts at once.
- **Bar.** Its own vanilla bar stays (darkens the sky, follows its health); phases only recolour it (`bar`), and a
  phase without one shows the vanilla purple again.
- **Between abilities** it flies like vanilla (`movement.style: vanilla`) and its heads shoot: vanilla's skulls, or
  with `host.skulls` Mobs_LD's (below). While an ability, a stagger or
  the walk home runs, its own chase stops; during an ability it holds perfectly still unless an event (`hover`,
  `dash`) moves it, and a `chase` or a `dash` without `height` keeps its altitude; in a stagger it sinks.
- **Targets.** With `boss.engage_on: players` its nearest-target goal looks for players only.
- **Heads.** The middle head follows its main target (the tank: threat top or taunt lock). With
  [target slots](#target-slots) named `right_head` / `left_head` those slots aim the side heads (on the Wither's own
  right and left) and vanilla's random side-head targets stop; a side head without a slot then stays idle. `look`
  events turn any of the three.
- **Skulls.** With `host.skulls` Mobs_LD fires all its skulls: vanilla's shots, its side-head shots and the idle blue
  skulls (and their sound) stop. Between abilities the middle head fires the `center` spec at the tank and each side
  head the `side` spec at its own target, each exactly every `every`, from its own mouth (shoot sound per skull).
  A head holds its shot while its target is out of sight or `range`; all skull fire pauses while an ability or a
  stagger runs (the timers wait, so the heads do not all fire the moment it ends), and the first shots come one
  interval into the fight. The side heads are kept apart: the left one starts half an interval after the right one,
  and neither fires within half a `side` interval (its average) of the other's last shot, so on a lone player the
  second skull does not land inside the first one's hurt immunity; the middle head keeps its own timing. `projectile` events fire any spec by name. The skulls are vanilla wither skulls (vanilla
  look and client), owned by the Wither (so Dungeon Difficulty's and DD Loot Stage's damage_dealt scale them) and
  tagged `mobs_ld_skull:<spec>`; when one lands its spec is looked up in the Wither's current phase, so reloads and
  phase changes apply in flight and saved skulls keep working:
  - direct hit: `hit.amount` x R (`damage_type` default `minecraft:wither_skull`; vanilla's knockback and hurt
    immunity), its `effects` only if the hit lands (also on Easy); no Wither II, no 5 HP heal for the Wither on a
    kill;
  - `splash`: in place of vanilla's blast, `amount` x R to every enemy (players, their pets, its current target)
    within `radius` blocks of the impact that the blast can see, except the one hit directly, as vanilla's skull
    blast (an explosion caused by the Wither); vanilla's puff and bang; no block is broken and nobody pushed, and the
    block it lands on does not react either (a decorated pot or chorus flower stays, a target block or bell stays
    quiet);
  - they fly through the Wither, its adds and undead allies (non-player `#minecraft:wither_friends`).
  A skull spawned that way whose Wither is gone (or lost the spec) hits as vanilla but still breaks no blocks. An
  unhosted Wither's skulls are entirely vanilla.
- **Host options** (`"type": "mobs_ld:wither"`; all may be overridden per phase):

| option | vanilla default | meaning |
|---|---|---|
| `hover_height` | 5.0 | how high above its target it hovers between abilities; below half health vanilla brings it to the target's height anyway |
| `regen_while_engaged` | true | false = no 1 HP/s regeneration while it is fighting |
| `arrow_immunity` | true | false = arrows and wind charges hit it below half health too (scale them with the phase's `damage_taken`) |
| `block_breaking` | `always` | breaking the blocks around it 1 s after a hit: `always`, `never`, or `outside_runs` (never while it carries Arenas_LD's run tag `arenas_ld_run_mob`) |
| `skulls` | none (vanilla skulls) | skull specs by name: `center` and `side` are fired by the heads on their own, any name by `projectile` events (below) |

A skull spec:
```json
"skulls": {
  "center": { "every": 4.0, "speed": 1.6, "range": 30, "hit": { "amount": 2.4 }, "splash": { "radius": 1.5, "amount": 0.6 } },
  "side":   { "every": [4, 5], "speed": 1.6, "hit": { "amount": 2.0, "effects": [ { "id": "minecraft:wither", "duration": 3 } ] },
              "splash": { "radius": 1.5, "amount": 0.5 } },
  "volley": { "speed": 1.6, "hit": { "amount": 4.0 }, "splash": { "radius": 1.5, "amount": 1.0 } }
}
```
| field | default | meaning |
|---|---|---|
| `every` | none | *time range*: cadence of a head firing it on its own (at least 0.25); without it only events fire the spec |
| `speed` | 1.9 | blocks per tick, constant from launch (at most 3.9) |
| `range` | 30 | a head only fires it at targets this close |
| `hit` | required | the direct hit (see `hit` under Events; `knockback` / `knock_up` are not used: vanilla's knockback applies) |
| `splash` | none | `{ "radius": 1.5, "amount": ... }` |

Per phase, e.g. `"host": { "skulls": { "center": { "every": 3.5 } } }` tightens one cadence and keeps the rest.

- **Still vanilla:** the summoning charge itself, and every part of a Wither whose file leaves an option out.
- **The shipped kit** (first release, R1): base stats 1711 HP / 2.0 attack (so R = its attack damage / 2), players
  only, line-of-sight reach (hide 3 s and aggro passes on; 15 s with nobody visible and it resets), leash 64 (both off
  inside Arenas_LD runs), soft enrage at 8 min, phases at 50 % (blue, projectiles x0.6, player height) and 20 % (red).
  Heads: right = `lowest_threat`, left = `spread`, both within 30 blocks and in sight, the right one re-picked every
  9-11 s and the left one 5 s after each of its picks, replaced after 2 s out of sight. Skulls at level 0 / Normal: middle 2.4 direct + 0.6 splash
  every 4.0 / 3.5 / 3.0 s (by phase), sides 2.0 + 0.5 splash and Wither I for 3 s on a direct hit every 4-5 / 3.5-4.5 /
  3-4 s, all at 1.6 blocks per tick; `volley` 4.0 + 1.0 (for events; R1 has none yet). A wall stagger lasts 2 s.
  Abilities: **Soul Rush** in phase 1, **Soul Rush x2** in phases 2 and 3, and **Grave Call** in all phases (below).

**Soul Rush** (`soul_rush`; cooldown 12 s, first use 8 s into the fight; target the tank within 22 blocks):
- 0.0-0.8 s: it backs off to 12 blocks from its target, 4 above it (`hover` with `retreat_to`); all three heads snap
  on (`look`), ambient sound at pitch 0.7 and soul flames at the mouths.
- 0.8 s: direction and length lock and the purple strip appears: as wide as the body (3 x scale), the distance to the
  target + 5 long (at most 28), laid on the ground in segments that follow slopes, filling from the Wither toward the
  arrowhead at its end.
- 0.8-1.8 s: it drops to 0.4 above the target's feet, holding its place.
- 1.8 s: it rushes down the lane at 1.4 blocks per tick (28 blocks in 1 s), still 0.4 above where the target stood.
  Contact only: 20.0 x R to everyone whose centre is on the strip as the body passes, once each, shoved sideways out
  of the lane (0.8, knock-up 0.3). Dodge: step 2 blocks out of the lane, or Combat Roll through the body (its contact
  lasts 1-2 ticks against 8 i-frame ticks, and a blocked contact clears the player for the whole rush).
- A wall after 4+ blocks of travel: smoke and a 2 s stagger (it sinks to the ground, the heads hold fire).
- Until 4.0 s it stays low where the rush ended: a short melee window.

**Soul Rush x2** (`soul_rush_2`; cooldown 13 s, ready 6 s into phase 2; replaces Soul Rush there): the first rush as
above with a 0.85 s strip (it locks at 0.95 s), then at 2.9 s a `chain` into `soul_rush_follow`, aimed at the left
(spread) head's player: all heads snap onto that player with the shoot sound at pitch 0.5 and smoke at the mouths;
the second rush has no strip, locks its direction 0.4 s after the snap (0.3 s before it starts) and starts 0.7 s after
the snap, at 1.8 blocks per tick, for 12.0 x R (same contact, side push and wall rules). A stagger on the first rush
ends the combo.

**Grave Call** (`grave_call`; every 30-60 s, rolled per use, first 20 s into the fight; `weight` 0 with `force_after`
0, so it goes the moment it is ready, at any range; skipped while the adds are at their limit, `skip_when_full`):
- 0.0 s: it rises or sinks to 3 blocks above the ground and holds still until 3.0 s (a window for ranged attacks and
  spells); action-bar line "The Wither calls up its dead" (`mobs_ld.cue.wither.grave_call`), its ambient sound at
  pitch 0.6, a column of soul particles under it. Its skulls hold fire, as during every ability.
- 0.2 s: one dark purple rune (a circle, r 0.9) per add, 3-6 blocks from the player it is placed for, on valid ground
  (solid floor, 3 open blocks, no fluid, 2 blocks apart; else next to the Wither's ground shadow; skipped over the
  void). Runes are dealt one per fighting player in turn for two rounds, the rest at random.
- 1.6 s: wither skeletons rise on the runes, each going first for the player its rune was placed for.
- Count: 2-4, +2 for each fighting player beyond the first, at most 12 per wave; at most 6 alive, +3 per extra player,
  at most 16 (a wave only fills the free room). Fighting = threat on the Wither's table, alive, not spectating or in
  creative, within 48 blocks.
- Stats from the Wither's: HP 1/40 of its max health (55 / 80 / 125 at Eden Loot's 2,201 / 3,187 / 4,986), attack 8 x R, armor + its armor above 4, and its
  `loot_stage:damage_dealt` copied (Eden Loot carries the Wither's level there and keeps R at 1). Vanilla wither
  skeleton swing otherwise (stone sword, Wither I for 10 s).
- No loot, equipment or experience (no farmable skulls); removed when the Wither dies or resets, and (`bound`) gone in
  a puff of smoke within seconds of the Wither being gone or unloaded, also after a restart.
- `minion_link`: a killed skeleton takes half its max health off the Wither (no cap), credited to its killer; once all
  of them are dead the Wither takes +50% damage until its next Grave Call raises skeletons.

### The Warden (`data/minecraft/mob_behaviour/warden.json`)

Mobs_LD ships a behaviour for the vanilla Warden with `host.only_tagged: ["arenas_ld_run_mob"]`: only Wardens an
Arenas_LD run spawns are hosted. Every other Warden (ancient cities, structures, shriekers, `/summon` without the tag)
is fully vanilla: its swing, sonic boom, Darkness pulse, dig-away and loot. The file declares no `base_attributes`
(Arenas sets the stats) and `reference_attributes` `attack_damage` 30, so R is 1 at vanilla attack damage. The file
holds the kit as far as the engine supports it so far, in its three phases (all numbers are placeholders the owner
tunes later; see the kit below and the design, `docs/design/warden-rework.md`).

A hosted Warden keeps its entity, model, sounds, pathing, anger and a real death with its vanilla loot table. Its AI
is a Brain, not goals; the engine works with it:
- **Between abilities** its Brain walks it at its target (`movement.style: vanilla`). While an ability, a stagger or
  the walk home runs, its Brain is held: memories still expire and its senses stay fresh, but no Brain behaviour
  starts or runs, so the engine alone moves and turns it (it still swims, as its Brain would, so it does not sink).
- **No vanilla attacks.** Its swing (30, disables shields) and sonic boom never start; every hit it deals comes from
  the behaviour. While it fights it is deaf to vibrations (tendril twitches are not its listener reacting).
- **Target.** Threat decides, as for every host (taunts work): the target goes to its Brain's attack target, and its
  anger at that target is kept angry so the Brain never drops it; when the engine drops or replaces a target, its
  anger at that one is cleared, so it does not roar and take them back. Its own senses, anger and retaliation when hit
  only ever pick targets the engine allows (`engage_on`, players given up on as unreachable, nobody while walking
  home). Before anyone has threat it wakes like vanilla (vibrations, a hit, then a roar).
- **Never digs away** (vanilla discards a Warden that idles 60 s; in a raid that loses the run): a Warden that is
  hosted, or simply carries `arenas_ld_run_mob`, keeps a dig cooldown from its first tick, and vanilla's dig can
  neither start nor discard it. It is also made persistent. Wardens re-read the run tag every second, so one tagged
  after it spawned is locked and hosted within a second (not only after a `/reload`).
- **Reset** (`/mobs_ld reset`, or a wipe outside a run): its anger table is emptied as well, and its target, roar,
  walk, disturbance and projectile memories cleared, so it does not roar straight back. Inside a run Arenas owns the
  wipe, as for every boss.
- **No Mobs_LD boss bar** (no `boss.bar`): the Arenas run HUD shows the boss's health.
- **Clips.** An ability's `clip`, a [`clip`](#clip---play-a-clip-mid-timeline) event and the stagger play its vanilla
  animations (no new animation, nothing on the client):

  | cue | what the server does | clip |
  |---|---|---|
  | `attack` | entity event 4 | 0.33 s impact flash (not a wind-up); also ends a roar clip |
  | `sonic_boom` | entity event 62 | 3.0 s chest crouch, burst at 1.7 s; sending it again restarts it |
  | `tendrils` | entity event 61 | 0.5 s tendril twitch, lights the tendrils |
  | `roar` | pose ROARING, STANDING after 84 ticks | 4.2 s (play `entity.warden.roar` from an `fx` at 1.25 s) |
  | `sniff` | pose SNIFFING, STANDING after 84 ticks | 4.16 s |

  The end of the ability (or the stagger) puts a roar or sniff pose back to STANDING. Playing a pose cue while the
  Warden already holds that pose takes one tick in STANDING first, so the clip restarts. Its clients play every clip
  at speed 1, so an ability timed to a clip sets `fixed_speed` (a phase's `attack_speed` would move its hits off the
  clip). Other names do nothing (`dig` and `emerge` come with the burrow).
- **Damage.** Sonic attacks deal `mobs_ld:warden_sonic` (`"damage_type": "mobs_ld:warden_sonic"`): death message as
  vanilla's sonic boom, armour and Protection reduce it, shields do not block it (`#minecraft:bypasses_shield`),
  Spell Engine evasion can dodge it (`#spell_engine:evadable`); difficulty-scaled (Hard x1.5) because the Warden is
  the attacker, and `loot_stage:damage_dealt` scales it. Physical swings stay `minecraft:mob_attack`: a shield blocks
  them and is never disabled. Every engine hit names the Warden as the attacker (Arenas' dungeon damage filter).

Host options (`"type": "mobs_ld:warden"`; these defaults are the boss's, not vanilla's):

| option | default | meaning |
|---|---|---|
| `darkness_pulse` | `off` | `vanilla`: its Darkness every 6 s within 20 blocks; `off`: none (Darkness only from the behaviour's own effects) |
| `disable_shields` | false | true: a shield that blocks its hit is disabled, as by vanilla's swing |
| `follow_range` | 48 | its base `generic.follow_range` once hosted (vanilla 16: threat targeting and abilities only reach players within it), with its path search sized to match; a phase or a reload that changes it applies within a second |
| `heartbeat` | vanilla | 0-150: while it fights, the anger level its clients' heartbeat and heart glow show instead of its real anger (0 = one beat every 2 s; 80 or more = the fastest, two a second); set per phase for the fight's tempo (the file: 20). Synced once a second |

**The kit so far** (phases 100-60 %, 60-25 %, 25-0 %; every hit names the Warden as the attacker; P = physical
`minecraft:mob_attack`, S = `mobs_ld:warden_sonic`; raw amounts at level 0 / Normal):

| ability | phases | what it does |
|---|---|---|
| `crushing_blow` | P1 (`crushing_blow_branded` in P2, P3) | tendril twitch, red rectangle 2.5 x 4.5 at the tank, P 12 at 1.0 s |
| `rending_combo` | P1 (`rending_combo_branded` in P2, P3) | red cone 100 degrees r 3.5 at 1.0 s (P 7), then a 2 x 6 rectangle re-aimed at the tank at 2.0 s (P 11) |
| Sculk Brand | P2, P3, 3+ fighters | the `_branded` swings leave `sculk_brand` stacks: +12 % from the Warden per stack, at most 4, 15 s |
| `sculk_backlash` | all | only once 2 players have each stood behind it (4.5 blocks, rear 140 degrees, within 3 blocks of its height) for 4 s: heartbeat and sculk souls, rear cone 140 degrees r 4.5 at 1.1 s, S 9 |
| `sonic_circle` | P1, tank within 8 | the Circle (two waves, see M2); `sonic_circle_open` (any range) in P2, ready 7 s into it; `sonic_circle_triple` (a third wave at the first wave's angle at 5.1 s, chest open 5.1-6.7 s) in P3 |
| `resonant_pulse` | P1 (`resonant_pulse_pooled` in P2, P3) | circle r 6 at 1.7 s, S 14; pooled: a Slowness II cloud r 3 for 8 s where it burst |
| `sonic_lance` | all | farthest player at 8+ blocks, also one the reach rule gave up on; group `lance` 14 / 10 / 7 s by phase |
| `sonic_volley` | P2, P3 | three lances (S 8 each) at 1.7 / 2.2 / 2.7 s, the second and third re-aimed as their strips appear; shares the `lance` cooldown |
| `tremor_line` | all | aimed at the `listener` slot (a spread slot: not the tank): six blue circles r 2 walking toward that player, 2 blocks apart, landing 1.3-2.8 s; P 7, each player at most once |
| `hunting_sniff` | all | `sniff` clip; at 3.8 s Glowing 8 s on the player with the most damage in the last 10 s (picked at its start) and "The Warden has caught a scent"; at 4.2 s it chains into `deep_charge` (a taunt lock wins) |
| `deep_charge` | chained only | roar pose, blue strip with an arrowhead from 0.2 s, runs at 0.9 blocks per tick from 1.2 s, P 16 with a side push; a wall stagger is the 4.2 s `sniff` |
| `deafening_rings` | P2, P3 | roar pose; rings 6-9 at 1.6 s, 12-15 at 2.2 s, 18-22 at 2.8 s, each drawn 1 s ahead; S 9 each |
| `echo_mines` | P2, P3 | `sniff` clip; a circle r 2.5 under every fighter, 0.6 s ahead of where they are heading, drawn at 0.6 / 1.8 / 3.0 s and landing 0.8 s later; S 5 each |
| `listening_silence` | P3, first 10 s into it | "Be still..."; from 1.0 to 3.0 s it [listens](#listen---punish-whoever-moves): every offender takes S 13, and at 4.2 s it chains into a Lance at the first one |
| Roar of the Deep | the 60 % transition | invulnerable roar (4.2 s): shove r 10 at 1.6 s (S 3, knockback 1.2), Darkness 6 s on everyone within 40 |

Not yet (later milestones of the design; the file names none of it, so nothing refers to missing parts):
- M4: Burrow Ambush, the 25 % transition (Heart of the Deep: dig, emerge at the arena centre, Chest Open), the `dig`
  and `emerge` cues, and its emerge when a run spawns it. P3 starts without a transition for now.
- M5: the Sculk Resonators: their raise in Roar of the Deep and 45 s into P2 (`resonance`, `min_players` 2,
  `placement.around: home`), and the x0.5 damage taken while any stands (`damage_taken.while_adds`).

## Attack profiles (`data/<ns>/mobs_ld/attack_profiles/*.json`)

Size any mob's hits on players (modded or vanilla, hosted or not) before anything scales them. With no files and caps
off (the defaults) nothing changes. One file per mob type, any file name; `/reload` reloads them.

```json
{ "entity": "bosses_of_mass_destruction:lich", "reference_hit": 12.0,
  "attacks": [
    { "match": { "damage_type": "minecraft:mob_attack" }, "hits": 1.0 },
    { "match": { "direct": "bosses_of_mass_destruction:blue_fireball" }, "hits": 1.5 },
    { "match": { "damage_type": "#minecraft:is_explosion" }, "multiply": 0.4 },
    { "match": { "damage_type": "minecraft:magic", "no_attacker": true, "within": 24 }, "hits": 0.5 }
  ],
  "cap_of_target_max_health": 0.45 }
```

| Field | Default | Meaning |
|---|---|---|
| `entity` | - | the mob type |
| `reference_hit` | - | the mob's one normal hit at level 0 on Normal: the unit of `hits` (required if any attack uses `hits`) |
| `reference_attack_damage` | the type's default | the attack_damage `reference_hit` was measured at; R = live attack_damage / this |
| `attacks` | `[]` | rules, first match wins; a hit nothing matches keeps its number |
| `cap_of_target_max_health` | config | one hit of this mob takes at most this share of the player's max health (see caps) |

An attack: `match` (every hit when absent; every field set must hold) and exactly one action.
- `match.damage_type`: an id or `#tag`; `match.direct`: the direct source's entity type (a projectile); `match.ability`:
  the Mobs_LD ability the mob is running; `match.no_attacker: true` + `within` (default 16): a hit with no living
  attacker or direct source on a player within that many blocks of a living mob of this type (for mods that drop the
  attacker; the nearest such mob stands in as the dealer).
- `hits: N` = N x `reference_hit` x R (attack_damage scaling by Dungeon Difficulty or Arenas still carries);
  `multiply: m` = the mod's own number x m; `set: n` = n; `ignore: true` = keep the number, stop looking.

The dealer is who DD Loot Stage scales by: the attacker (the shooter, the owner of a cloud), else a living direct
source; never a player. Order of one hit: the profile (at the very start of `LivingEntity.hurt`, wrapping every other
mod's code there) -> `loot_stage:damage_dealt` -> the Arenas tier -> the cap (start of the player's `actuallyHurt`) ->
armour, Resistance, absorption. `/kill` and other damage that bypasses invulnerability is never touched.

**Caps** (`config/mobs_ld_damage.json`, reload with `/mobs_ld damage config`): `capsEnabled` (default false),
`capOfTargetMaxHealth` (0.40) for normal mobs, `bossCapOfTargetMaxHealth` (0.60) for bosses (`#c:bosses` or a
Mobs_LD behaviour with a `boss` block). A profile's own `cap_of_target_max_health` applies even with caps off. Hits
without a mob behind them (falling, lava, a `no_attacker` hit no profile claims) are never capped. During i-frames
vanilla passes only the excess over the last hit, which is capped alike.

The damage audit (below) records each hit's raw number and, when a profile changed it, the profiled one.

## Debug commands (ops)
All take any mob running a behaviour, Mobs_LD's own or a vanilla host (e.g. `@e[type=zombie,limit=1]`).
- `/mobs_ld ability <targets> <name>` - start an ability now, aimed by its `target` rule (else at the mob's target,
  else at you).
- `/mobs_ld reset <targets>` - run the boss reset.
- `/mobs_ld play <targets> <clip>` / `/mobs_ld stop <targets>` - preview an animation clip (a host cue on vanilla mobs).
- `/mobs_ld threat <targets>` - print the threat table, highest first, and the current target.
- `/mobs_ld heads <targets>` - print what every head aims at: the middle head's main target, then each target slot
  with its rule, the head it shows on, its holder, the time to its next re-pick (and the slot it follows) and how
  long the holder has been out of sight.
- `/mobs_ld brain <targets>` - a Warden's Brain: hosted or not, held by the engine or running, its activity, its
  attack target and its anger at it, its dig cooldown (and when it expires), its pose (and the clip cue holding it)
  and whether it is burrowed.
- `/mobs_ld debug` - toggle the ability trace in chat for operators within 64 blocks and in the server log (`Mobs_LD trace ...`) (abilities, hits, slot picks,
  skull hits and splashes with their amounts).
- `/mobs_ld damage audit start|stop|dump|status` - records every hit on a player while it runs (to size mob attacks
  from data). Grouped by dealer kind (`attacker`, `direct`, `none`, `player`; the dealer is who DD Loot Stage scales
  by: the attacker, else a living direct source), dealer entity type, damage type, direct entity type and the Mobs_LD
  ability the dealer runs. Per group: landed/refused, raw min/avg/max (what `LivingEntity.hurt` received: after a
  player's difficulty scaling, before `loot_stage:damage_dealt`, Arenas tier, armour, absorption), raw as a share of
  the player's max health, health + absorption really taken, the dealer's attack_damage and damage_dealt, whether the
  type is `#loot_stage:unscaled`, and for hits with no dealer the living non-players within 16 blocks (suspects).
  `stop` and `dump` write `mobs_ld/damage_audit/<time>.json` in the server folder (`dump` keeps recording); a recording
  still running when the server stops is written too. `/mobs_ld damage audit attributes` lists the living entity
  types whose default attributes lack `loot_stage:damage_dealt` (`attributes_<time>.json`).
