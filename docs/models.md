# Mobs_LD model & animation contract

How mob/boss art must be authored so Mobs_LD's own Bedrock renderer can load it. Mobs_LD does **not** use
GeckoLib or vanilla Java models: it reads Blockbench's Bedrock Entity exports directly. This file is the contract
between modeling and code. If a model needs something not listed here, ask before building it.

## Blockbench project

- Project type: **Bedrock Entity** (not "Modded Entity", not "GeckoLib Animated Model", not "Java Block/Item").
- Exports:
  - `File > Export > Export Bedrock Geometry` -> `<mob>.geo.json` (format_version `1.12.0` or newer)
  - `Animation > Export Animations` -> `<mob>.animation.json` (format_version `1.8.0`)
  - Texture -> `<mob>.png`
- Geometry identifier: `geometry.mobs_ld.<mob>`. Animation ids: `animation.mobs_ld.<mob>.<name>`.
- `<mob>` is lowercase snake_case and equals the entity id (e.g. `gorefist` -> `mobs_ld:gorefist`).

## Scale, orientation, origin

- 16 model units = 1 block. Model at true in-game size; no scaling to fix it later.
- Model faces **north (-Z)** in Blockbench, same as vanilla Bedrock entity models.
- Origin `[0, 0, 0]` is the center of the entity's feet, on the ground.
- The gameplay hitbox is a separate axis-aligned box set in code (width x height in blocks). State the intended
  hitbox in the hand-off notes; the model may overhang it (big arms, weapons).

## Geometry: supported

- Bones: `pivot`, `rotation`, `parent` hierarchy, `mirror`.
- Cubes: `origin`, `size`, `uv` (box UV **or** per-face UV), `inflate`, cube `rotation` + `pivot`, `mirror`.
- Locators (`locators` on a bone), with optional offset/rotation - used for hit points, particle/projectile
  spawn points.

Not supported (do not use): mesh / poly_mesh elements, texture_meshes, bone `binding`, multiple geometries per
file, per-cube textures from different files.

## Bones

- lowercase snake_case names, unique.
- `root`: single top-level bone, pivot `[0, 0, 0]`, parent of everything. Code may scale/offset it.
- `head`: if present, code adds look-at yaw/pitch toward the target on top of animations. Pivot at the neck.
- Name limbs and parts plainly: `body`, `chest`, `arm_left`, `arm_right`, `forearm_right`, `fist_right`,
  `leg_left`, `leg_right`, ... (left/right = the creature's own left/right).
- Locators: `hit_<part>` for where an attack lands (e.g. `hit_fist_right`), `fx_<name>` for effect anchors,
  `proj_<name>` for projectile spawns.

## Textures

- Minecraft pixel density: 1 texel per model unit (no hi-res textures on low-res cubes).
- Power-of-two size, up to 512x512 (bosses usually 128-256).
- Optional emissive layer: `<mob>_glow.png`, same size/UV; fully transparent except glowing pixels (eyes,
  cracks, embers). Rendered full-bright.
- Optional damage/phase variants: `<mob>_phase2.png` etc., same UV.

## Animations

Interpolation: `linear`, `catmullrom`, `step`. Channels: rotation, position, scale. Loop modes: `loop: true`,
`"hold_on_last_frame"`, or play-once. Molang in keyframes: plain numbers preferred; allowed expressions are
arithmetic, `math.*`, `query.anim_time`, `query.life_time`. No variables, no other queries.

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

Client-only effects go in the clip itself:

- Blockbench **Sound** keyframes: effect = a sound event id (`minecraft:entity.iron_golem.attack` or a
  `mobs_ld:` sound we add).
- Blockbench **Particle** keyframes: effect = particle id, locator = a `fx_`/`hit_` locator.
- Blockbench **Instructions** (timeline) keyframes, one command per line:
  - `shake <strength> <radius>` - camera shake for nearby players (strength 0.1-1.0, radius blocks).

## Hand-off

Per mob, deliver:

- `src/main/resources/assets/mobs_ld/geo/<mob>.geo.json`
- `src/main/resources/assets/mobs_ld/animations/<mob>.animation.json`
- `src/main/resources/assets/mobs_ld/textures/entity/<mob>.png` (+ `_glow`, variants)
- `art/<mob>/<mob>.bbmodel` (source, not shipped in the jar) and concept art/notes in `art/<mob>/`
- `art/<mob>/NOTES.md`: intended hitbox, walk blocks-per-cycle, and per ability clip: length, impact time(s),
  what the telegraph shape should be (circle/cone/rectangle/rays, size in blocks, where it is centered).

Do not commit; the Mobs_LD session wires the files in and commits.
