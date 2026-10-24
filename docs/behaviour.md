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
| `host` | none | vanilla hosts only: the host adapter's options, or `{ "enabled": false }` to switch the file off (see [Vanilla hosts](#vanilla-hosts)) |
| `target_slots` | none | secondary targets by name, e.g. the Wither's side heads (see [Target slots](#target-slots)) |
| `movement` | standoff 2.0 | how it positions itself between abilities (below) |
| `global_cooldown` | 0.5 | pause after every ability before the next can start |
| `intro` | none | ability played once when it first engages (e.g. a roar; set `invulnerable`) |
| `stagger` | none | `{ "clip": "stagger", "duration": 1.8 }` - helpless window after a dash hits a wall |
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
| `follow` | `current` | whom to move toward: `current` (threat target), `nearest`, `farthest`, `random`, `lowest_threat`, `slot:<name>` (as an ability's `target`) |
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
floor: telegraphs then sit at the point itself, and a `hover` over the ground keeps its altitude.

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
| `cooldown` | 0 | before it can be used again |
| `initial_cooldown` | 0 | after engaging, before first use |
| `weight` | 1 | relative chance among the abilities usable right now; `0` = only reached by chaining |
| `range` | any | `{ "min", "max", "above", "below" }` horizontal distance; `above`/`below` default to the reach band |
| `target` | `current` | aim: `current` (threat target / tank), `nearest`, `farthest`, `random` player, `lowest_threat` (the player with the least threat, 0 counts; a tie goes to someone other than the tank), `slot:<name>` (whoever that [target slot](#target-slots) holds; no holder = the ability is not usable) |
| `track_until` | first telegraph | the mob turns toward its aim until this time, then facing and aim lock |
| `force_after` | none | once fighting this long, use it as soon as it is off cooldown, ignoring range (a fuse) |
| `aim_side` | 0 | blocks to the mob's right where its weapon strikes (one-handed weapon); it turns so that line meets the target |
| `invulnerable` | false | takes no damage while it runs (transitions, intros) |
| `events` | `[]` | timeline (below) |

Choosing: every tick out of an ability, the mob picks at random (by `weight`) among abilities that are allowed by
the phase, off cooldown, have an aim and are in range; if none is, it moves per `movement`.

## Events

Every event has `type` and a time. Telegraphs appear at `telegraph.from` (default: shortly before impact) and fill up
until the impact; origin and facing are fixed when the telegraph appears, so the warning is exactly the hit area.

### `hit` - area attack
| field | default | meaning |
|---|---|---|
| `at` | required | impact time |
| `shape` | required | see Shapes |
| `origin` | `self` | `self` (on the mob) or `target` (where the aim stood when the telegraph appeared) |
| `offset` | 0 | push the origin forward along the facing |
| `side` | 0 | push the origin to the mob's right (draw the telegraph under a one-handed weapon) |
| `aim_side` | - | re-aim for this beat: the zone is aimed when the telegraph appears, and the body turns into it during the last 0.3 s before `at` (without a telegraph: turns from 0.3 s before `at`) |
| `height` / `depth` | reach band | how far above / below the origin a victim's feet may be |
| `telegraph` | shown | `{ "show": true, "from": 0.4, "color": "#B01818" }` |
| `hit` | damage 1.0 | `{ "damage": x attack_damage, "effects": [...], "knockback": 0.4, "knock_up": 0 }` (see below) |

The `hit` block (also used by `trace`, `dash`, `chase` and skulls):

| field | default | meaning |
|---|---|---|
| `damage` | 1.0 | multiplier on the mob's live `attack_damage` (the LdMob way) |
| `amount` | - | instead of `damage`: absolute raw damage at level 0 on Normal, multiplied at hit time by **R** = the mob's live `attack_damage` / the `attack_damage` its `base_attributes` declare (1 if the file declares none, so `amount` then works like `damage`). R is 1 on an unscaled mob and carries what scales its attack (Dungeon Difficulty levels, soft enrage), so a balance sheet lists every hit as one number. Setting both is an error |
| `damage_type` | `minecraft:mob_attack` | damage type id (any datapack's; skulls default to vanilla's `minecraft:wither_skull`); the mob stays the attacker either way. Unknown ids are reported when the server starts or reloads, and those hits deal the default type |
| `effects` | none | `[ { "id": "spell_engine:bleed", "duration": 6, "amplifier": 0 } ]` - any mod's effect id, applied only when the hit lands; unknown ids are skipped with a log warning |
| `knockback` / `knock_up` | 0.4 / 0 | the whole push (below) |

Hits ignore vanilla's 10-tick hurt immunity (each event lands at most once per victim), so combo beats and a
slash right after a trample all count. Mod dodges that cancel damage (Combat Roll's roll i-frames) still work.
`knockback` / `knock_up` are the whole push: vanilla's own 0.4 shove on every melee hit is removed, so 0 means the
victim does not budge. Keep knockback low on a combo's opening beats, or the first hit pushes the target out of the
next one's reach.

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

### `dash` - charge
`at`, `until`, `max_distance`, `overshoot` (1.0, blocks past the aim), `width` (2.0), optional `hit` (applied once
to everyone the body sweeps through), `on_wall` (`stagger` | `stop`), `telegraph` (draws the path).

### `chase` - homing run that hands over to another ability
`at`, `clip` (loop played while running), `max_time`, `speed` (blocks per tick; a sprinting player is ~0.28),
`turn_rate` (6, degrees per tick - lower is easier to side-step), `reach` (3.0), `on_reach` / `on_timeout` (ability
names to chain into), `on_wall` (`stagger` | `stop`), `width` + optional `hit` (trample, once per victim).

Chaining: the chased-into ability starts at once with the same aim, skipping `global_cooldown`. Give such
follow-ups `"weight": 0` so they are never picked on their own. Gorefist's charge is `charge` (crouch tell, then
chase) -> `charge_attack` on reach or `charge_exhaust` (a 2.5 s vulnerable window) after 10 s.

### `summon`
`at`, `entity`, `count` (1), `forward` / `up` / `right` offset from the mob, `spread` (1.0), `max_alive` (8).
Adds start on the summoner's aim, are removed when the summoner dies or resets, and are spawned inside the
summoner's tick (Arenas_LD treats them as minions of a run boss).

### `cloud`
`at`, `origin`, `radius`, `duration`, `effects` - a lingering area-effect cloud.

### `effect`
`at`, `radius` (0 = the mob itself), `effects` - buffs on self or debuffs on enemies around.

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
  the mob); `count` 8, `spread` 0.3 blocks, `speed` 0.02.
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

Candidates: players that are alive, attackable, not stealthed (Rogues' vanish), not spectating or in creative, within
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
| `rays` | `count` (even), `length`, `width` |

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
| `cooldowns` | per-ability cooldown overrides |
| `attack_speed` | timelines and clips run this much faster |
| `damage_taken` | `kind`: `melee`, `projectile`, `other` (spells, fire, ...), `all` |
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
  mobs (Warden, piglins, hoglin, zoglin, breeze, villager, allay, axolotl, frog, tadpole, goat, camel, sniffer,
  armadillo), which ignore goals. Players and armor stands are not mobs.
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
- **The `host` block.** `{ "type": "<adapter type>", "enabled": true, ...options }`. `"enabled": false` switches the
  file off: the type is not hosted at all, exactly as if the file were absent (a server datapack can override a file
  shipped in a jar this way). `type` names the options its host adapter reads (only the Wither has one so far:
  `mobs_ld:wither`); every option defaults to vanilla. A `type` for another adapter, and keys nobody defines, are
  reported at reload and ignored. Phases may override options (see Phases).

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
  `dash`) moves it, and a `dash` or `chase` keeps its altitude; in a stagger it sinks.
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
- **The shipped file is a placeholder kit:** base stats 1711 HP / 2.0 attack (so R = its attack damage / 2), players
  only, line-of-sight reach (hide 3 s and aggro passes on; 15 s with nobody visible and it resets), leash 64 (both off
  inside Arenas_LD runs), soft enrage at 8 min, phases at 50 % (blue, projectiles x0.6, player height) and 20 % (red).
  Heads: right = `lowest_threat`, left = `spread`, both within 30 blocks and in sight, the right one re-picked every
  9-11 s and the left one 5 s after each of its picks, replaced after 2 s out of sight. Skulls at level 0 / Normal: middle 2.4 direct + 0.6 splash
  every 4.0 / 3.5 / 3.0 s (by phase), sides 2.0 + 0.5 splash and Wither I for 3 s on a direct hit every 4-5 / 3.5-4.5 /
  3-4 s, all at 1.6 blocks per tick; `volley` 4.0 + 1.0. Abilities: `test_hover_slam` (rise over the target, slam a
  4.5-block circle under it), there to check hover and telegraphs in game, and `test_volley` (weight 0: only through
  `/mobs_ld ability`; all heads lock onto the right head's player with a sound, soul flames at the mouths and an
  action-bar line, then three waves of three `volley` skulls 0.5 s apart). The real kit (Soul Rush, Grave Call)
  replaces them in later milestones; the kit is incomplete until then (milestone M5 of
  `docs/design/wither-rework.md`).

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
- `/mobs_ld debug` - toggle the ability trace in chat for operators within 64 blocks (abilities, hits, slot picks,
  skull hits and splashes with their amounts).
