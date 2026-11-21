# Contributing

## Adding a mob or boss
1. **Design first.** Write a short concept in the style of `docs/design/` before any JSON:
   - role and phases;
   - every ability with its telegraph and counterplay.

   Get it approved.
2. **Pick the route** (details in [CLAUDE.md](CLAUDE.md) and [docs/behaviour.md](docs/behaviour.md)):
   - **Vanilla rework:** only `data/minecraft/mob_behaviour/<mob>.json`.
   - **Custom mob:** an entity file, a behaviour file, model, animations and texture, lang keys, and hit tracks if it
     uses `trace`.
   - **Another mod's mob:** a behaviour file at its entity id if it is goal-based, or an attack profile to resize its
     attacks.
3. **Start from [docs/examples](docs/examples/)** and the shipped files. Look every field up in `docs/behaviour.md`.
4. **Test in a dev game** (`./gradlew runClient`) on a test world:
   - set `/gamerule doMobSpawning false` and `/kill @e[type=!player]` first;
   - `/mobs_ld debug` shows what the engine decides;
   - `/mobs_ld ability <mob> <name>` forces an ability.

## Rules
- **Every damaging ability is readable:** a telegraph, or a clear clip and sound before the hit. Fights should be fair
  for melee and ranged players.
- **Numbers live in data.** Use `hit.amount` (scaled by R) for balance-sheet numbers. Placeholder numbers are fine;
  the server owner sets the final ones.
- **Engine changes:**
  - new fields are optional and off by default, so existing files behave exactly as before;
  - add a JUnit test and document the field in `docs/behaviour.md`;
  - don't change how the shipped mobs (Gorefist, Rot Wart, Wither, Warden) behave as a side effect.
- **Integrations stay optional.** The mod must load without DD Loot Stage, Arenas_LD or Spell Engine.
- **Art:** only assets you made or have the rights to. Follow [docs/models.md](docs/models.md).
- **Code style:** match the surrounding code.
  - official Mojang mappings;
  - records with codecs for data;
  - Javadoc that explains why;
  - 4-space indent for Java, 2 for JSON.

## Before a pull request
- `./gradlew build` passes. It runs the unit tests, including the ones that load every shipped file and every
  example.
- `docs/behaviour.md` and `CHANGELOG.md` are updated.
- Say what you checked in game and what you didn't.
