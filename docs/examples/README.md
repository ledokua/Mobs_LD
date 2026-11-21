# Examples

Starter files to copy. `ExamplesTest` parses every file here during the build, so they stay valid as the format grows.
Numbers are placeholders.

| Folder | Copy to | What it is |
|---|---|---|
| `vanilla_host/zombie.json` | a datapack: `data/minecraft/mob_behaviour/zombie.json` | slam and stomp for zombies |
| `custom_boss/entities/example_brute.json` | the mod: `src/main/resources/mobs_ld/entities/<mob>.json` | a custom mob's hitbox, stats and spawn egg |
| `custom_boss/mob_behaviour/example_brute.json` | `data/mobs_ld/mob_behaviour/<mob>.json` (jar or datapack) | a two-phase boss: cleave, charge with a wall stagger, guard summon |
| `attack_profile/creeper.json` | a datapack: `data/<ns>/mobs_ld/attack_profiles/creeper.json` | creeper blasts ×0.35, at most 40% of max health per hit |

**Vanilla host.** The example has `"host": { "only_tagged": ["mobs_ld_example"] }`, so only tagged zombies use it. Try
it with `/summon zombie ~ ~ ~ {Tags:["mobs_ld_example"]}`. Remove that line to change every zombie.

**Custom boss.** It also needs art and names:
- a GeckoLib model, animations and texture named `example_brute` (see [../models.md](../models.md)), with clips
  `idle`, `walk`, `death` (required), plus `cleave`, `charge`, `call_guards` and `stagger`;
- lang keys:
  - `entity.mobs_ld.example_brute`;
  - `item.mobs_ld.example_brute_spawn_egg`;
  - `mobs_ld.cue.example_brute.call_guards` (the action-bar text).

Entity files are read at startup, so a new mob needs a rebuilt jar. A broken entity file stops the game from
starting. That's on purpose: server and client must agree on the entity list.
