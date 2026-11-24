# Nameless King — Art & Rig Notes

- **Source:** `nameless_king.bbmodel` + `nameless_king.png` (256×256 per-face UV atlas, 117 visible cubes across 17 rig bones).
- **GeckoLib conversion:**
  - Excluded the hidden ModelEngine `hitbox` cube group so only visible geometry ships in `nameless_king.geo.json`.
  - Added 9 zero-cube marker bones (`fx_feet`, `fx_core`, `fx_crown`, `fx_hand_left`, `fx_spear_base`, `fx_spear_tip`, `hit_spear_base`, `hit_spear_mid`, `hit_spear_tip`) for client-side particles, VFX anchors, and baked weapon hit tracks.
  - Applied GeckoLib 4 `catmullrom` spline interpolation on flowing wind/cape/spear arcs with crisp `linear` hit-stops on impact beats, eliminating all 125 action-layer boundary snaps across all 16 clips.
- **Textures:**
  - `nameless_king.png` + `nameless_king_glowmask.png` — weathered storm-bronze armor, windswept dark cloak, icy spiky mane, and glowing golden sunlight-lightning runes on the Dragonslayer Swordspear.
  - `nameless_king_phase2.png` + `nameless_king_phase2_glowmask.png` — Heir of Sunlight Phase 2 variant with blazing white-gold overcharged spear runes, storm-charged icy-cyan mane rim highlights, and deepened thundercloud cloak.
- **Stride calibration:** `walk` is a 2.2 s heavy regal stalk (~3.2 blocks per cycle).
