# Kiln Sprout animation handoff

The model is 0.96 blocks tall and spans 1.37 blocks across the roots. The 0.9 × 0.9 block gameplay hitbox lets the root tips overhang it. The `walk` clip covers 0.40 blocks per 0.80-second cycle at authored speed. The model faces north.

| Clip | Length | Playback | Timing |
| --- | ---: | --- | --- |
| `idle` | 1.80 s | Loop | The bark sways while the coal bobs. |
| `walk` | 0.80 s | Loop | Alternating diagonal steps with slightly staggered footfalls. |
| `spawn` | 0.95 s | Once | Roots unfold, ash kicks up at 0.42 s, and the coal flares at 0.48 s. |
| `channel_start` | 0.80 s | Once | All four roots are planted by 0.55 s. The coal flares and the ward starts at 0.60 s. |
| `channel_loop` | 1.25 s | Loop | Feet stay planted and the coal pulses while the 15-block connection holds. |
| `channel_end` | 0.60 s | Once | Plays when the ward loses its connection. The coal drops and the roots return to the neutral pose. |
| `hurt` | 0.35 s | Once | A short grounded flinch. |
| `death` | 1.30 s | Hold | The stump tips sideways and the coal goes out at 0.88 s. |

The tether starts at `fx_coal`. Ground particles use `fx_root_contact`. The channel start, loop and end share the same braced pose at their boundaries.
