# Mobs_LD model & animation contract

How mob/boss art must be authored for Mobs_LD. Mobs render through **GeckoLib 4** (4.9.x, already in the
Fallen Eden pack), driven by one generic model class that finds files by entity id - no Java per model. This file
is the contract between modeling and code. If a model needs something not listed here, ask before building it.

## Blockbench project

- Plugin: **GeckoLib Models & Animations** 4.2.5+ (Blockbench 5.x). Keep the *AzureLib Animator* plugin disabled
  while working on these models - it is a GeckoLib fork that patches the same exporters.
- Project type: **GeckoLib Animated Model**, model type *Entity* (a plain *Bedrock Entity* project also loads,
  but the GeckoLib type exports GeckoLib easings correctly). Not "Modded Entity", not Java Block/Item.
- Exports:
  - `File > Export > Export GeckoLib Model` -> `<mob>.geo.json`
  - `Animation > Export GeckoLib Animations` -> `<mob>.animation.json`
  - Texture -> `<mob>.png`
- Geometry identifier: `geometry.mobs_ld.<mob>`. Animation ids: `animation.mobs_ld.<mob>.<name>`.
- `<mob>` is lowercase snake_case and equals the entity id (e.g. `gorefist` -> `mobs_ld:gorefist`).

## Scale, orientation, origin

- 16 model units = 1 block. Model at true in-game size; no scaling to fix it later.
- Model faces **north (-Z)** in Blockbench, same as vanilla Bedrock entity models.
- Origin `[0, 0, 0]` is the center of the entity's feet, on the ground.
- The gameplay hitbox is a separate axis-aligned box set in code (width x height in blocks). State the intended
  hitbox in the hand-off notes; the model may overhang it (big arms, weapons).
- An entity declaration can set `"pushable": false` to resist shoves from overlapping entities.

## Geometry: supported

- Bones: `pivot`, `rotation`, `parent` hierarchy, `mirror`.
- Cubes: `origin`, `size`, `uv` (box UV **or** per-face UV), `inflate`, cube `rotation` + `pivot`, `mirror`.
- Marker bones: empty bones (no cubes) whose pivot is the point - used for hit points and particle/projectile
  spawn points (code reads their world position). Use these instead of Bedrock locators.

Not supported (do not use): mesh / poly_mesh elements, texture_meshes, bone `binding`, locators, multiple
geometries per file, per-cube textures from different files.

## Bones

- lowercase snake_case names, unique.
- `root`: single top-level bone, pivot `[0, 0, 0]`, parent of everything. Code may scale/offset it.
- `head`: if present, code adds look-at yaw/pitch toward the target on top of animations. Pivot at the neck.
- Name limbs and parts plainly: `body`, `chest`, `arm_left`, `arm_right`, `forearm_right`, `fist_right`,
  `leg_left`, `leg_right`, ... (left/right = the creature's own left/right).
- Marker bones (empty, parented to the part they follow): `hit_<part>` for where an attack lands
  (e.g. `hit_fist_right`), `fx_<name>` for effect anchors, `proj_<name>` for projectile spawns.

## Textures

- Minecraft pixel density: 1 texel per model unit (no hi-res textures on low-res cubes).
- Power-of-two size, up to 512x512 (bosses usually 128-256).
- Optional emissive layer: `<mob>_glowmask.png` (GeckoLib's naming), same size/UV; fully transparent except
  glowing pixels (eyes, cracks, embers). Rendered full-bright.
- Optional damage/phase variants: `<mob>_phase2.png` etc., same UV.
- To use a variant only during an action, set `render.action_textures` in the entity declaration. For example,
  `{"enrage": "phase2", "enrage_exit": "phase2"}` uses the `_phase2` texture for those two clips.

## Animations

Interpolation: `linear`, `catmullrom`, `step`, plus GeckoLib easings. Channels: rotation, position, scale.
Loop modes: `loop: true`, `"hold_on_last_frame"`, or play-once. Molang in keyframes: plain numbers preferred;
allowed expressions are arithmetic, `math.*`, `query.anim_time`, `query.life_time`, `query.ground_speed`.
No custom variables unless agreed with the Mobs_LD session.

Required:

| name | loop | notes |
|---|---|---|
| `idle` | loop | breathing/sway; base layer |
| `walk` | loop | base layer; state **blocks travelled per cycle** in hand-off notes (playback is synced to speed) |
| `death` | hold | ends lying/collapsed; ~1-2 s |

Optional: `run` (loop), `spawn` / `intro` (once), `hurt` (short, once), `phase_<n>_transition` (once),
`enrage` (once), `stagger` (once).

Ability animations - one clip per ability, named after it (e.g. `slam`, `leap_slam`, `charge`):

- One clip covers **windup -> impact -> recovery**.
- The **impact time** (seconds from clip start, when damage lands) is a gameplay number: list it per clip in the
  hand-off notes. Code puts it in the ability's data; the server never reads animation files.
- Ability clips play on top of the base layer and **override every bone they key**. Key all bones that should
  move (usually the whole body) so idle/walk does not leak through.
- Looping abilities (channels, spins): make a separate `<ability>_loop` clip plus optional `<ability>_start` /
  `<ability>_end`.

Client-only effects go in the clip itself. **One effect per keyframe time**: GeckoLib rejects the whole clip if a
sound/particle/instruction timestamp holds an array of effects; offset extra ones by a tick (0.5 / 0.52 / 0.54).

- Blockbench **Sound** keyframes: effect = a sound event id (`minecraft:entity.iron_golem.attack` or a
  `mobs_ld:` sound we add).
- Blockbench **Particle** keyframes: effect = particle id, locator = the name of an `fx_`/`hit_` marker bone.
  Simple particle types only (`minecraft:explosion`, `minecraft:flame`, ...). Optional *Script* field for a burst:
  `count=12; spread=0.6; spread_y=0.4; speed=0.05` - particles, horizontal scatter around the marker, upward
  scatter (default 0, so ground impacts stay on the ground), random velocity. Default: one particle.
  Big sprites (`explosion` is ~2 blocks) at a hit marker burst right where the victim stands: keep counts low.
- Blockbench **Instructions** (timeline) keyframes, one command per line:
  - `shake(<strength>, <radius>)` - camera shake for nearby players (strength 0.1-1.0, radius blocks), e.g.
    `shake(0.3, 12)`. The older `shake 0.3 12` still works in game, but Blockbench's Molang check flags it as an
    error, so prefer the call form.

## Hand-off

`./gradlew check` verifies shipped mob animation and model references, including clip timing and marker bones.
Hit tracks baked with `/mobs_ldc bake` include the geometry and animation fingerprints used by this check.

Per mob, deliver:

- `src/main/resources/assets/mobs_ld/geo/entity/<mob>.geo.json`
- `src/main/resources/assets/mobs_ld/animations/entity/<mob>.animation.json`
- `src/main/resources/assets/mobs_ld/textures/entity/<mob>.png` (+ `_glowmask`, variants)
- `art/<mob>/<mob>.bbmodel` (source, not shipped in the jar) and concept art/notes in `art/<mob>/`
- `art/<mob>/NOTES.md`: intended hitbox, walk blocks-per-cycle, and per ability clip: length, impact time(s),
  what the telegraph shape should be (circle/cone/rectangle/rays, size in blocks, where it is centered).

Do not commit; the Mobs_LD session wires the files in, re-bakes the hit tracks (`/mobs_ldc bake <mob>`) and commits.

**Swings that hit physically** (`trace` events in behaviour.md) follow the baked bone paths, so the weapon must
actually pass through body height (roughly 0.5-1.8 blocks above the ground, where players are) during the strike.
A swing at head height only grazes; one above 2.5 misses.
