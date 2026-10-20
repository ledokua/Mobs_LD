# Mobs_LD mob behaviour format

How a data-driven mob fights. One JSON file per mob in a datapack:
`data/<namespace>/mob_behaviour/<mob>.json` (the file id is the entity id, e.g. `mobs_ld:gorefist`).
Reloads with `/reload`; a broken file is logged and skipped. Times are **seconds**, distances **blocks**.

The entity itself (hitbox, base stats, spawn egg) is declared in the jar at `mobs_ld/entities/<mob>.json`, and its
art follows [models.md](models.md). Working examples: `gorefist.json` and `rot_wart.json` in
`src/main/resources/data/mobs_ld/mob_behaviour/`.

## Top level

| field | default | meaning |
|---|---|---|
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
| `style` | `standoff` | `standoff` (walk to `distance`, hold), `kite` (stay about `distance` away), `stationary` |
| `distance` | 2.0 | preferred distance to the followed target |
| `speed` | 1.0 | navigation speed multiplier |
| `follow` | `current` | whom to move toward: `current` (threat target), `nearest`, `farthest`, `random` |

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
| `range` | any | `{ "min", "max" }` distance to the aim target that allows starting it |
| `target` | `current` | aim: `current` (threat target / tank), `nearest`, `farthest`, `random` player |
| `track_until` | first telegraph | the mob turns toward its aim until this time, then facing and aim lock |
| `force_after` | none | once fighting this long, use it as soon as it is off cooldown, ignoring range (a fuse) |
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
| `height` | 3.0 | how far above the origin a victim's feet may be |
| `telegraph` | shown | `{ "show": true, "from": 0.4, "color": "#B01818" }` |
| `hit` | damage 1.0 | `{ "damage": x attack_damage, "effects": [...], "knockback": 0.4, "knock_up": 0 }` |

`effects`: `[ { "id": "spell_engine:bleed", "duration": 6, "amplifier": 0 } ]` - any mod's effect id; unknown ids are
skipped with a log warning.

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

## Boss

```json
"boss": { "bar": { "color": "red", "overlay": "progress" }, "leash": 32, "reset_after": 15,
          "soft_enrage": { "after": 420, "damage_multiplier": 2.0, "clip": "enrage" } }
```

- `bar`: vanilla boss bar, shown while engaged.
- `leash`: if pulled further than this from its spawn point it resets and walks home (invulnerable meanwhile).
- `reset_after`: seconds without any target before it resets (a wipe).
- `soft_enrage`: after `after` seconds in combat its damage is multiplied.
- Reset = full heal, phase 1, cooldowns and enrage cleared, adds removed. Bosses never despawn.

## Debug commands (ops)
- `/mobs_ld ability <targets> <name>` - start an ability now.
- `/mobs_ld reset <targets>` - run the boss reset.
- `/mobs_ld play <targets> <clip>` / `/mobs_ld stop <targets>` - preview an animation clip.
