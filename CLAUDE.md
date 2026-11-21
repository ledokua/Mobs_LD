# Mobs_LD: guide for Claude (creating mobs and bosses)

Mobs_LD is a Fabric 1.21.1 mod: **data-driven mobs and bosses**. A mob's fight is a JSON ability timeline (telegraphs,
hits, dashes, summons, phases) run by one engine; its art is a GeckoLib model. The engine can also take over **vanilla
mobs** (the Wither and the Warden ship as reworked bosses). Most new content is JSON, not Java.

Stack: Java 21, Fabric Loom with **official Mojang mappings** (`Mob`, `LivingEntity.hurt`, `ResourceLocation`, not Yarn
names), Mixin + MixinExtras, GeckoLib 4.9.x, JUnit via fabric-loader-junit.

## Read these first
- `docs/behaviour.md`: **the format reference.** Every field, default and event type; vanilla hosts, phases, boss
  block, attack profiles and debug commands. Treat it as the source of truth and keep it in sync with the code.
- `docs/models.md`: the model and animation contract: Blockbench/GeckoLib export, bones, clip names, hand-off.
- `docs/design/*.md`: worked boss designs (Wither, Warden). Good examples of how a concept becomes a behaviour file.
- Working data:
  - `src/main/resources/data/mobs_ld/mob_behaviour/gorefist.json`: a custom boss with traced swings, phases and
    summons;
  - `src/main/resources/data/mobs_ld/mob_behaviour/rot_wart.json`: its add;
  - `src/main/resources/data/minecraft/mob_behaviour/wither.json` and `warden.json`: vanilla hosts.

Before writing a field, look it up in `docs/behaviour.md`; don't guess names from memory. If a field you need doesn't
exist, say so and propose it as an engine change (below) instead of inventing it in JSON. Unknown keys are mostly
ignored silently.

## Three ways to add content
1. **Rework a vanilla mob** (no Java, no art): add `data/minecraft/mob_behaviour/<mob>.json`.
   - Goal-based mobs only. The Ender Dragon and Brain mobs are refused, except the Warden, which has an adapter.
   - No `trace` events (no hit tracks for vanilla models). Use `hit` with a telegraph.
   - `clip` names only work if the type has a host adapter.
   - `host.only_tagged` limits the file to tagged mobs, e.g. a dungeon's spawns, and leaves world mobs vanilla.
2. **New custom mob or boss.** Every one of these files is needed:
   - `src/main/resources/mobs_ld/entities/<mob>.json`: hitbox, base attributes, spawn egg, render options. This is
     read at startup and registers the entity type, so a new mob needs a jar rebuild.
   - `src/main/resources/data/mobs_ld/mob_behaviour/<mob>.json`: the fight.
   - Art per `docs/models.md`:
     - `assets/mobs_ld/geo/entity/<mob>.geo.json`;
     - `assets/mobs_ld/animations/entity/<mob>.animation.json`;
     - `assets/mobs_ld/textures/entity/<mob>.png`;
     - Blockbench source in `art/<mob>/`.
   - `src/main/resources/data/mobs_ld/hit_tracks/<mob>.json` if the behaviour uses `trace`. Bake it in a dev client
     with `/mobs_ldc bake <mob>`, and re-bake after any model or animation change.
   - Lang keys in `assets/mobs_ld/lang/en_us.json` (and `uk_ua.json`): `entity.mobs_ld.<mob>` and
     `item.mobs_ld.<mob>_spawn_egg`.
   - No Java class per mob: `LdMob` and one generic GeckoLib model serve them all.
3. **Size another mod's attacks** (no fight changes): an attack profile in
   `data/<ns>/mobs_ld/attack_profiles/<name>.json` (see "Attack profiles" in `docs/behaviour.md`).

Behaviour files, hit tracks and attack profiles are datapack data. They reload with `/reload`, and a server datapack
can override or switch off (`"host": { "enabled": false }`) a file shipped in the jar.

## How to design a fight (house style)
- **Concept first, code second.** For a boss, write a short design (in the style of `docs/design/`): role, phases,
  each ability with its telegraph, counterplay and what it punishes. Get the author's OK before building it.
- **Every damaging ability is readable.** It has a telegraph (`telegraph.from` before the hit) or a clear clip and
  sound cue. No unavoidable instant damage. Fairness for melee and ranged players is a design goal: give ranged
  players something to dodge, and melee players windows to stay in.
- **Numbers are placeholders for the author.** Don't spend effort on damage or HP balance research. Write sensible
  placeholder numbers in the JSON, keep every number in data (never hard-coded), and list the tunable knobs at the
  end so the author can set them.
- Prefer `hit.amount` (scaled by R = live attack_damage / declared base) over `hit.damage`, so difficulty and level
  scaling still carries.
- Times are seconds and distances blocks. A *time range* field takes `4` or `[4, 5]`.
- Summons that belong to a boss should use the `summon` event: it is spawned inside the boss tick and owned by it, so
  dungeon and raid mods see it as the boss's minion. Use `bound: true` and `loot`/`xp: false` for adds that must not
  be farmed.

## Engine changes (Java)
Only when the format can't express something.
- **New fields must be optional and off by default.** Old files must behave exactly as before. Shipped behaviours
  must keep their behaviour: Gorefist and Rot Wart are frozen until their new models, so a shared engine change must
  not alter them.
- Code lives in `src/main/java/net/ledok/mobs_ld/`:
  - `mob/behaviour/` (format and codecs; `event/` holds one record per event type);
  - `mob/combat/` (runtime: `MobCombat`, `AbilityRun`);
  - `mob/host/` (vanilla host adapters);
  - `mob/damage/` (attack profiles);
  - `mixin/`.
- Match the surrounding code: records with `RecordCodecBuilder` codecs, Javadoc that explains *why*, the same naming
  and comment density.
- Per change:
  - add a JUnit test in `src/test/java/...`, mirroring the existing ones (`ShippedBehavioursTest` loads every shipped
    file);
  - document the field in `docs/behaviour.md`;
  - bump `mod_version` in `gradle.properties` when shipping.
- Integrations are optional and never hard dependencies. DD Loot Stage's `loot_stage:damage_dealt` and Arenas_LD run
  tags are looked up by id or reflection; the mod must still load without them.

## Build and test
- Build: `./gradlew build` (compiles and runs all unit tests; the jar lands in `build/libs/`). It needs about 1.5 GB of
  free RAM.
- Dev game: `./gradlew runClient` or `./gradlew runServer` (run folder `run/`).
- Check JSON without a client: `./gradlew test` (`ShippedBehavioursTest` parses the shipped files). A broken file at
  runtime is logged as `Invalid mob behaviour <id>: ...` and skipped.
- In game (op): `/mobs_ld` has these subcommands.
  - `ability <targets> <name>`: force an ability now.
  - `debug`: turns on the trace of decisions, hits and amounts. It goes to op chat and the server log.
  - `play <targets> <clip>`: preview an animation.
  - `reset <targets>`, `threat <targets>`, `heads <targets>`.
  - `damage audit start|stop|dump`: record every hit on a player.
- Test worlds: set `/gamerule doMobSpawning false` and `/kill @e[type=!player]` first, so nothing else interferes.
  A forced ability on a mob with no target aims at the command source (`execute as <armor stand> run mobs_ld ability
  ...`).
- Say what was and wasn't verified in game. Unit tests check parsing, not how a fight feels.

## Don't
- Don't hard-code balance numbers in Java, or invent JSON fields that `docs/behaviour.md` doesn't list.
- Don't copy code or art from mods you have no rights to. Reading them for ideas is fine.
- Don't commit build output, `run/`, or baked files you didn't copy in on purpose. Commit only when asked.
