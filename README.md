# Mobs_LD

Data-driven mobs and bosses for Minecraft 1.21.1 (Fabric). A mob's fight is written as JSON: abilities on a timeline,
with ground telegraphs, area hits, weapon swings that follow the animation, dashes, summons, phases and boss rules.
One engine runs them. The same engine can take over **vanilla mobs**, so a zombie, the Wither or the Warden can get a
new moveset from a datapack.

Built for the Fallen Eden server, MIT licensed.

## Features
- **Ability engine.** Timeline events:
  - `hit`: circle, cone, rectangle, rays and ring shapes, each with a telegraph;
  - `trace`: hitboxes swept along the baked animation;
  - `dash`, `chase` and `chain` for movement and combos;
  - `summon`, `cloud`, `effect`, `hover`, `look`;
  - `fx`: sound, particles, text;
  - `listen`, `vulnerable` and more.
- **Bosses.** Phases with transitions and their own speed, damage-taken rules, boss bar, leash, wipe reset and soft
  enrage. Summoned adds are owned by the boss.
- **Threat system.** MMO-style aggro from damage, heals, buffs and debuffs, with taunts and provokes, and threat for
  player summons. Spell Engine aggro is supported.
- **Vanilla hosts.** Any goal-based mob can run a behaviour file. It can be limited to tagged mobs, e.g. only a
  dungeon's spawns.
  - The Wither ships reworked: heads, Soul Rush, Grave Call.
  - So does the Warden: Arenas_LD runs only, with the Sonic Boom Circle.
- **Damage tools.**
  - `/mobs_ld damage audit` records how hard every mob really hits players.
  - Attack profiles resize other mods' attacks.
  - Optional per-hit caps (share of max health) stop one-shots.
- **Art.** GeckoLib 4 models and animations; no Java class per mob.

## Requirements
- Minecraft 1.21.1, Fabric Loader 0.19+, Fabric API
- [GeckoLib](https://modrinth.com/mod/geckolib) 4.9+
- Java 21
- Needed on both server and client: the client renders the models and telegraphs.

Optional integrations are picked up when present and never required: DD Loot Stage (`loot_stage:damage_dealt`),
Arenas_LD (run tags, minions), Spell Engine (aggro, heals).

## Quick start: rework a vanilla mob
Put a file in a datapack at `data/minecraft/mob_behaviour/zombie.json` and run `/reload`:

```json
{
  "abilities": {
    "overhead_slam": {
      "duration": 1.6, "cooldown": [4, 6], "range": { "max": 3.5 },
      "events": [
        { "type": "hit", "at": 1.0, "telegraph": { "from": 0.3 },
          "shape": { "type": "rectangle", "width": 1.6, "length": 3.5 },
          "hit": { "damage": 1.5, "knockback": 0.6 } }
      ]
    }
  }
}
```

Every zombie now winds up a slam that shows a red strip on the ground for 0.7 s before it lands. More starter files
are in [docs/examples](docs/examples/): a vanilla host limited to tagged mobs, a full custom boss (entity file plus
behaviour) and an attack profile.

## Documentation
- [docs/behaviour.md](docs/behaviour.md): the full behaviour format. Every field, event and default; vanilla hosts,
  phases, boss rules, attack profiles and commands.
- [docs/models.md](docs/models.md): how to build models and animations (Blockbench with the GeckoLib plugin), and
  what to hand off.
- [docs/design/](docs/design/): design write-ups of the Wither and Warden reworks.
- [docs/examples/](docs/examples/): starter files.
- [CLAUDE.md](CLAUDE.md): if you create content with Claude Code, it reads this guide automatically.
- [CONTRIBUTING.md](CONTRIBUTING.md): how to add mobs and engine features.

## Commands (operators)
| Command | What it does |
|---|---|
| `/mobs_ld ability <targets> <name>` | force an ability now |
| `/mobs_ld debug` | trace the engine's decisions and hits, in op chat and the server log |
| `/mobs_ld play <targets> <clip>` / `stop <targets>` | preview an animation |
| `/mobs_ld reset <targets>` | boss reset: full heal, phase 1, adds removed |
| `/mobs_ld threat <targets>`, `heads <targets>`, `brain <targets>` | show threat tables, head targets, a Warden's brain |
| `/mobs_ld damage audit start\|stop\|dump\|status\|attributes` | record hits on players to JSON |
| `/mobs_ld damage config` | reload `config/mobs_ld_damage.json` |
| `/mobs_ldc bake <mob>` (client) | bake a custom mob's animation hit tracks |

## Building
```bash
./gradlew build
```

The jar lands in `build/libs/`, and the build runs all unit tests. Use `./gradlew runClient` or `./gradlew runServer`
for a dev game. The code uses official Mojang mappings.

## License
MIT; see [LICENSE.txt](LICENSE.txt).
