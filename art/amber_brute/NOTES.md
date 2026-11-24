# Amber Brute (`mobs_ld:amber_brute`) — Model & Animation Hand-Off Notes

- **Geometry id:** `geometry.mobs_ld.amber_brute` (`src/main/resources/assets/mobs_ld/geo/entity/amber_brute.geo.json`)
- **Animations id prefix:** `animation.mobs_ld.amber_brute.<clip>` (`src/main/resources/assets/mobs_ld/animations/entity/amber_brute.animation.json`)
- **Texture atlas:** `512 × 512` (`amber_brute.png`), emissive amber crystal/talon glowmask (`amber_brute_glowmask.png`), and Phase 2 variant (`amber_brute_phase2.png` + `amber_brute_phase2_glowmask.png`, where amber glow seeps through the wooden dome helmet's visor slits and crystal clusters brighten).
- **Intended hitbox:** `2.0 × 4.6` blocks (`eye_height: 3.85`, `shadow_radius: 1.25`).
- **Walk stride:** `3.4` blocks per 2.4 s `walk` cycle (`walk_blocks_per_cycle: 3.4`).
- **Death hold:** `death` clip is `3.4 s` (`death_ticks: 70`).
- **Bone hierarchy:** 19 rig bones (`root`, `leg_r/l` → `shin_r/l`, `body`, `scarf`, `pack` → `rod_0..5`, `head` (renamed from `h_head` for `LdMobModel` look-at), `arm_r` → `forearm_r`, `arm_l` → `forearm_l`) + 8 marker bones (`fx_feet`, `fx_chest`, `fx_head`, `fx_pack`, `fx_fist_right`, `hit_fist_right`, `fx_claw_left`, `hit_claw_left`). Hidden ModelEngine `hitbox` bone stripped.

## Animation Clips (11 total: 7 original + 4 physics-matched)

| Clip | Len (s) | Loop | Source | Key Gameplay Beats |
|---|---:|---|---|---|
| `idle` | 3.6 | loop | original | Heavy breathing, right claws planted on floor, head scans, left-claw twitch |
| `walk` | 2.4 | loop | original | Asymmetric heavy gait (3.4 blocks/cycle), pack rods rattle on each right heel strike |
| `hurt` | 0.9 | once | original | Heavy flinch recoil |
| `death` | 3.4 | hold | original | Knees buckle at 1.25 s, topples onto heavy crystal arm at 2.15 s |
| `spawn` | 3.2 | once | original | Emerges from soil (land 0.95 s), shakes off dirt (1.55 s), threatens with crystal arm (2.25 s) |
| `attack` | 2.5 | once | original | Heavy right-arm overhead slam landing at `0.98 s` (`offset: 1.9, side: 0.8`), hit-stop `0.98–1.05 s`, slow fist pullout recovery (`1.55–2.40 s`) |
| `swipe` | 1.6 | once | original | Fast thin left-claw diagonal rake sweeping through body height `0.45–0.56 s` (`trace` on `["forearm_l", "hit_claw_left"]`) |
| `knuckle_sweep` | 2.2 | once | physics-matched | Low heavy right-knuckle plow right-to-left (`0.84–0.98 s` `trace` + `0.95 s` amber shard spray cone) |
| `double_rake` | 2.4 | once | physics-matched | Asymmetric 3-beat combo: left claw forehand (`0.45–0.56 s`), left claw backhand (`1.04–1.18 s`), short right-fist slam (`1.75 s`, `offset: 2.9, side: 0.35`) |
| `tremor_stomp` | 2.4 | once | physics-matched | Heavy mossy boot stomp (`1.05 s` inner fracture r 3.8) followed by crystal spike resonance ring (`1.70 s`, r 3.8–7.4) |
| `crystal_roar` | 2.6 | once | physics-matched | Hunch and amber resonance slam/roar (`1.15 s`): Phase 2 transition + Phase 2 mid-range crystal spike ring |
