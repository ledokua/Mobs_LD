# Emberroot — Art & Rig Notes

- **Source:** `emberroot.bbmodel` + `emberroot.png` (512×512 per-face UV atlas, 282 visible cubes across 16 rig bones).
- **GeckoLib conversion:**
  - Removed the hidden ModelEngine `hitbox` cube group so only visible geometry ships in `emberroot.geo.json`.
  - Renamed the misspelled `damdge` clip to `hurt` so `LdMob`'s hurt controller triggers automatically.
  - Added 8 marker bones (`fx_feet`, `fx_core`, `fx_crown`, `fx_shoulder_right`, `fx_hand_right`, `hit_hand_right`, `fx_hand_left`, `hit_hand_left`) for client-side particles, live furnace-core 3D VFX tracking, and server-side `trace` melee hitboxes.
  - Added subtle arm-bracing keyframes to `lazer_attack` and `attack_shoulder` (whose original `arm_r` tracks were blank) and Catmull-Rom spline smoothing across all 14 clips.
- **Textures:**
  - `emberroot.png` + `emberroot_glowmask.png` — charred bark with glowing molten heart (`core`), ember eye slits, chest veins, claw tips, and smoldering branch crown tips.
  - `emberroot_phase2.png` + `emberroot_phase2_glowmask.png` — overheated furnace variant (white-hot heart, ignited bark ember cracks, deeper ash-charcoal crust).
- **Stride calibration:** `walk` is a 2.4 s two-step heavy gait (~3.2 blocks per cycle).
