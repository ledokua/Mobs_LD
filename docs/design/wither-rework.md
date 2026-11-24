# Wither rework: proposal for approval

Status: approved by the owner on 2026-10-24 (first release R1 = M0-M5; projectiles x0.6 below 50%, untelegraphed 2nd rush, Eden Loot sets per-level damage_dealt). Base design is C (balance and ops first), with grafts from A (generic engine pieces) and B (summon stat handling, side push, Marching Graves). The errors the review found are fixed in this version.

---

## 1. Pitch

The Wither keeps its vanilla entity, model, build ritual, 11 s blue charge, boss bar, death and loot. After the charge, Mobs_LD's ability engine runs the fight.

- **Heads.** The three heads split the group. The middle head follows the tank. One side head aims at the lowest-threat player. The other spreads to a player no head is on. Side heads re-pick about every 10 s. Solo, all three heads are on you.
- **Between abilities** it flies like vanilla and the heads fire weak skulls.
- **Soul Rush.** This is its main threat. It pulls back and all heads snap onto you. A purple strip with an arrowhead is drawn on the ground under its path, then its body rushes down the lane. Damage happens only on contact with the body. You can step out of the strip or Combat Roll through the body. If you bait it into a wall, it staggers for 2 s.
- **Grave Call.** Every 30-60 s it raises wither skeletons on runes around the players fighting it: 2-4 for one player, +2 for each extra player. Their HP and damage follow the Wither's own stats.
- **50% HP.** It hardens (the vanilla armor swirl), comes down to player height, and adds a two-rush chain and skull volleys.
- **20% HP.** It speeds up.
- **Anti-cheese.** Hiding out of sight passes aggro to a visible player. A wipe resets the fight.

Every hit is an absolute number at level 0 / Normal that Eden Loot can calibrate. Only the spawn blast stays unscaled.

---

## 2. Phases and abilities

Releases:
- **R1** = first release: the owner's three asks, plus skull chip, line-of-sight reach and leash/reset.
- **R1.1** = the same engine, more data (no new engine code).

How to read the damage column:
- Damage is in raw points at level 0 / Normal, before damage_dealt.
- At run time every Wither hit is multiplied by R = the Wither's attack_damage / 2.0. R carries the Dungeon Difficulty (DD) level and soft enrage.
- The global cooldown between abilities is 1.0 s.
- All skull fire pauses while an ability or stagger runs.

| Ability | When | Telegraph | Counterplay | Dmg L0 Normal raw | Cooldown |
|---|---|---|---|---|---|
| Head targeting (R1) | always | heads visibly turn to their player (vanilla synced aim) | taunt moves the middle head and the body | none | side heads re-pick every 9-11 s; the two are offset by 5 s |
| Middle-head skull (R1) | between abilities, needs line of sight | head aimed at the tank, vanilla shoot sound | strafe, break line of sight, roll at impact | 2.4 direct, 0.6 splash r1.5 to others | 4.0 / 3.5 / 3.0 s (P1/P2/P3) |
| Side-head skull (R1) | between abilities, needs line of sight | each side head aimed at its own player | same | 2.0 direct, 0.5 splash, Wither I 3 s only if the hit lands | 4-5 / 3.5-4.5 / 3-4 s |
| Spawn blast (done) | end of the build charge | the 11 s charge | leave the area (about 14 blocks to fall to 0) | 99 at the center, **UNSCALED** (#loot_stage:unscaled; still x1.5 on Hard) | once |
| **Soul Rush** (R1) | P1 | see below: pull-back, 1.0 s ground strip with an arrowhead | step 2 blocks out of the lane; roll as the body arrives; bait into a wall | **20.0** on body contact, once per victim, pushed sideways out of the lane | 12 s (first use at 8 s) |
| **Grave Call** (R1) | all phases | see below: action-bar text, runes 1.4 s before the adds rise | AoE the runes, peel adds off healers, taunt them | adds swing 8.0 (vanilla stone-sword wither skeleton) + vanilla Wither I 10 s; the add is the attacker | random 30-60 s (first at 20 s); skipped while at max alive |
| Marching Graves (R1.1) | P1 | 5 circles r2 walk from under the Wither toward the locked aim; each is drawn 0.8 s before impact, 0.35 s apart; soul-fire column on impact | step sideways (backing straight away loses) | 9.0 per circle, knock-up 0.5 | 10 s |
| Grave Slam (R1.1) | all phases, target within 6 blocks horizontally (anti-hug, anti-pillar) | rises 2 blocks with all heads on the aim; circle r5.5 on the ground under it fills in 1.1 s; slams down | walk out or roll at impact, then 2 s grounded melee window | 14.0, knock-up 0.6; hits feet from -2 to +6 blocks | 14 s (P3 12 s), first at 10 s |
| Armored Descent (R1.1) | crossing 50% | invulnerable 3.5 s; bar turns blue, action-bar text; rises 3, circle r7 fills in 1.5 s, slams; vanilla armor swirl appears | get 7 blocks out or roll | 16.0, knock-up 0.7 | once |
| Soul Rush x2 (R1.1) | P2, P3 (replaces Soul Rush) | rush 1 as Soul Rush, 0.85 s strip. At 2.9 s all heads snap onto the left-head player, shoot sound (pitch 0.5), smoke. Rush 2 has **no strip**, starts 0.7 s after the snap, and locks direction 0.3 s before it starts | rush 1 as above; rush 2: keep moving sideways during the 0.7 s tell, or roll on the sound | 20.0, then 12.0 | 13 s (first at 6 s) |
| Triple Volley (R1.1) | P2, P3 | target = right-head (lowest-threat) player; all heads lock on for 1.0 s with a charge sound and soul flames; 3 waves at 1.0 / 1.5 / 2.0 s, each a flat wall of 3 skulls (±1.5 blocks) aimed where you are at fire time | strafe 2.5+ blocks after each wave, use cover, roll the last wave | 4.0 direct + 1.0 splash per skull; at most one direct hit per wave | 10 s (P3 8 s), first at 4 s |
| Last Rites (R1.1) | crossing 20% | invulnerable 2.5 s; bar turns red, action-bar text, ring of soul flames, roar | none | none | once |
| P3 | 20-0% | the P2 kit with every timeline 15% faster (attack_speed 1.15) and the cooldowns above; a third chained rush comes only after the 2-rush chain has been playtested | | | |
| Soft enrage (R1) | 480 s in combat | action-bar text | kill it faster | all Wither hits x1.5; adds raised after this also hit x1.5 | once |

**Phases** are P1 100-50% (purple bar), P2 50-20% (blue), P3 20-0% (red). They latch downward and never return. P2 starts at the same edge as vanilla's isPowered: armor swirl, and hovering at player height. In P2 and P3, projectiles deal 60% of their normal damage.

**Soul Rush timeline (requirement 2)**
- 0.0-0.8 s: hovers back to 12 blocks from the target, 4 above it. All three heads snap on, with ambient sound (pitch 0.7) and soul flames at the mouths.
- 0.8 s: direction and length lock. The ground strip appears:
  - as wide as the rendered body (3.0 x scale);
  - length = distance to the target + 5, at most 28 blocks;
  - split into segments of up to 6 blocks, each placed on the ground with the deep ground scan, so it follows slopes;
  - fills from the Wither toward the tip, with an arrowhead at the end.
- 0.8-1.8 s: drops to the target's feet + 0.4 while holding position.
- 1.8 s: rushes at a constant 1.4 blocks/tick (28 blocks in 1 s). Body contact damages anyone whose center is inside the drawn strip. Each tick it tests the segment from the old to the new position, so a fast rush cannot skip past a player.
- Each victim is hit at most once. A roll that blocks the first contact clears the whole rush. Contact lasts 1-2 ticks, against 8 roll i-frames.
- On a wall after at least 4 blocks of travel: 2 s stagger. It drops to the ground with smoke, and the side heads hold fire.
- About 2.8-4.0 s: it recovers low, which gives a short melee window.

**Grave Call timeline (requirement 3)**
- 0.0 s: hovers 1.6 blocks above the ground and holds still, within reach of players who close in. Action-bar text "The Wither calls up its dead", low ambient sound, a soul particle column. Side heads hold fire.
- 0.2 s: one rune (r0.9) per add, placed 3-6 blocks from its player.
  - A valid spot has a solid floor, 3 blocks of air, no fluid, and is at least 2 blocks from other runes.
  - After 8 failed tries it uses the spot next to the Wither's ground shadow. Over the void the rune is skipped.
- 1.6 s: the adds rise. Each one first attacks the player its rune was placed by.

Add count and stats:
- **Count:** random 2-4, plus 2 for each extra fighting player, at most 12 per wave.
- **Who counts as fighting:** players with threat > 0 on the Wither's table, alive, not spectator or creative, within 48 blocks. Bystanders do not count.
- **Max alive:** 6 + 3 per extra player, at most 16. A wave only fills the free room.
- **Runes** are dealt 2 per player in turn; extras go to random players.
- **Stats** are set after spawning, from the Wither's own stats:
  - HP = 20 x (Wither max_health / 1711); **changed 2026-10-25 (owner): HP = Wither max_health / 40** (`"max_health": {"of_summoner": 0.025}`), because 26-HP adds next to a 2,201 HP Wither felt too weak. With the Eden Loot boss type: 55 / 80 / 125 HP (OW L12 / Nether L25 / End L45), about 15% of the Wither's HP per wave of 6;
  - attack_damage = 8.0 x (Wither attack_damage / 2.0);
  - armor = the Wither's armor above its base 4.
  So the Wither's fixed dimension level carries over, not the add's own location level.
  - **Eden Loot update (2026-10-24):** the Eden Loot boss type keeps the Wither's attack_damage at 2.0 (R = 1) and carries the level in loot_stage:damage_dealt (0.34 OW / 0.90 Nether / 2.28 End; HP 2201 / 3187 / 4986). So each add also copies the summoner's loot_stage:damage_dealt value (looked up by id, no hard dependency); otherwise the adds' swing would not scale with level.
- **No loot, no XP.** Owned by the Wither, removed on reset and on death.

**Head rules (requirement 1)**

Who can be picked: alive, attackable, not stealthed, not spectator or creative, within 30 blocks, with line of sight from that head.

- **Middle head:** getTarget(), i.e. the threat top or the taunt lock (the tank).
- **Right head:** the lowest-threat player. Threat 0 counts. Ties go to someone other than the tank, then random.
- **Left head (spread), in this order:**
  1. a player no other head is on (avoiding its previous pick when there is another choice);
  2. otherwise the player with the fewest heads on them; ties go away from its previous pick, so with 2 players it alternates at each re-pick;
  3. only if no player qualifies: a pet or summon that is on the Wither's threat table.

When a head re-picks:
- every 9-11 s; the left head is offset by 5 s, so the side heads never switch together;
- at once when its target dies, leaves, goes past 30 blocks, or is out of sight for 2 s.

Results:

| Group | Middle | Right | Left |
|---|---|---|---|
| Solo | the player | the player | the player (never a pet) |
| 2 players | tank | other player | alternates between them |
| 3+ players | one head each | | |

**Anti-cheese and reset**
- A target with no line of sight to any head for 3 s is marked unreachable, and aggro passes to a visible player.
- If nobody is visible for 15 s (stealth such as rogue vanish counts as not visible), or the Wither is pulled more than 64 blocks from home, it resets: full heal, adds removed, threat cleared, back to P1, head targets cleared, flies home.
- Only players, player-owned mobs and mobs that hurt it in the last 10 s can be targeted or start the fight. Cows and endermen no longer pull it.

---

## 3. What stays vanilla

- The entity: id minecraft:wither, the WitherBoss class, model, renderer, textures, sounds, hitbox (0.9 x 3.5, follows generic.scale), and the synced head aim.
- The build ritual, the summon_wither advancement, and the 220-tick blue charge with its growing scale. Blue means "immune" only during this charge; the rework never calls setInvulnerableTicks.
- Already committed: the charge ends at full health, and the spawn blast deals mobs_ld:wither_spawn_blast (unscaled, still x1.5 on Hard).
- Death and rewards:
  - a real, player-credited death;
  - loot table id minecraft:entities/wither, with no override file, so the Loot n Explore injection stays;
  - the nether star from dropCustomDeathLoot, so Soulslike Weaponry's TAIL hook still works;
  - the DataRewriter boss gear and ribcage pools, FTB kill quests, the BOMD lich counter, soul_trap and the Omen of War all keep working.
- The vanilla boss bar (purple, darkens the screen, progress = health). Only its color changes per phase. There is no second Mobs_LD bar.
- Flight between abilities: the vanilla straight-line chase, parking within 3 blocks. It aims to hover 1.5 blocks above the target in P1 and at player height from 50%. After a named ability or its last chained move ends, it holds position and pauses skull fire for 1.35 seconds.
- Fire, drowning and wither-rose immunity; #c:bosses crowd-control immunity; never despawns; discarded on Peaceful.
- Block breaking 1 s after being hit: unchanged outside Arenas runs, off inside runs (see question 5).

Changed only for Withers that have the behaviour file:

| Area | Vanilla | Reworked |
|---|---|---|
| Base max_health | 300 | 1711 |
| Skulls | 8 damage + Wither II 10 s / 40 s, block-breaking blast, heal on kill | data-driven amounts, no block damage, no heal |
| Idle blue skulls | on | off |
| Side heads | random non-undead within 20 blocks | threat slots |
| 1 HP/s regen | always | off while engaged |
| Targets | any living thing | players only |

---

## 4. Engine changes in Mobs_LD

### 4.1 How a datapack targets the Wither
- File: `data/minecraft/mob_behaviour/wither.json`. The file id is the entity id, as today. It ships in the Mobs_LD jar, and the server datapack can override the same path.
- `MobBehaviours` changes:
  - It accepts any registered entity type whose `EntityType.getBaseClass()` is a `Mob`, instead of warning on non-LdMob ids.
  - It refuses EnderDragon (its aiStep never reaches `Mob.serverAiStep`) and Brain-driven mobs (Warden, piglins, hoglins), with a warning.
  - It builds an `IdentityHashMap<EntityType<?>, MobBehaviour>` plus a generation counter, so mobs without a behaviour pay one int compare per tick.
- A mob gets its `MobCombat` lazily, on its first server AI tick after its type gains a behaviour.
- After `/reload` the generation check re-resolves: adding the file attaches on the next tick, and removing it leaves an inert combat.
- Off switch: `{"host":{"enabled":false}}`.

### 4.2 Host layer (new files under `src/main/java/net/ledok/mobs_ld/`)

`mob/combat/CombatHost.java` is a duck interface on Mob. Every member is prefixed `mobs_ld$`: `combat()`, `combatOrCreate()`, `playAction`, `stopAction`, `setVariant`, and `static of(Entity)`. On vanilla mobs the visual calls become adapter cues.

`mixin/MobCombatHostMixin.java` (`@Mixin(Mob)`):
- `<init>` TAIL, server only: applies `base_attributes`. This runs before WitherBoss's own `setHealth(max)` and before DD scaling.
- `serverAiStep`: `@Inject` at INVOKE `customServerAiStep()V`, shift AFTER. It attaches the combat, then runs `combat.tick()` when `adapter.ready()`, then `adapter.tick()`.
  - This is inside the boss tick (Arenas rule 1). It runs before the move and look controls, so engine velocity and look win in the same tick.
- `add/readAdditionalSaveData` TAIL: persistence (4.6).
- `setTarget` HEAD, cancellable: refuses unreachable targets, and non-player targets when `engage_on: players`.
- On attach:
  - adds CombatGoal (adapter priority, default 1) and ThreatTargetGoal(0) if they are missing;
  - calls `setPersistenceRequired()` when the behaviour has a boss block.

`mixin/LivingEntityCombatMixin.java`:
- `hurt` HEAD: returns false when invulnerable, and scales the amount with `@ModifyVariable` (`adjustDamage`). Server only; damage that bypasses invulnerability (/kill) is exempt.
- `baseTick` before `tickDeath()`: runs the engine's death tick.
- `shouldDropLoot` and the XP reward return nothing for summons marked loot or XP false.

`mob/combat/CombatEvents.java`:
- `AFTER_DEATH` → onDeath.
- `ENTITY_UNLOAD` → onRemoved. This also fixes today's bug: chunk unload never reached `LdMob.remove()`.
- `ENTITY_LOAD` → orphan check.

Other new files:
- `mob/host/HostAdapter.java` and `HostAdapters.java`: a registry keyed by EntityType; the default adapter does nothing. Methods: `ready`, `install`, `tick`, `onCue`/`onStopCue`, `holdsMovement`, `bossBar`, `muzzle(head)`, `look(heads, entity, ticks)`, `applySlot(name, LivingEntity)`, `bodyWidth`, `goalPriority`.
- `mob/host/HostSpec.java`: the `host` block, a dispatch codec on `type`, plus `enabled` and the adapter options, validated at reload.
- `mob/combat/TargetSlots.java` with a pure picker `SlotPicker.java`: generic secondary targets. The rules are data (`lowest_threat`, `spread`, `range`, `line_of_sight`, `repick`, `offset`, `lost_after`); the adapter only maps slot names to visuals.
- `mob/combat/Fighters.java`: the summon player count from 2.
- `mob/behaviour/Seconds.java`: a codec for a number or `[min, max]`, rolled per use.

### 4.3 Retyped from LdMob to Mob
- `MobCombat`, `AbilityRun`, `CombatGoal` (resolves `CombatHost.of` lazily), `CombatTargets`, `HitSpec`.
- The Chase, Dash, Summon, Trace and Despawn events. Despawn uses a static `MobCombat.despawnWithPoof(Mob)`.
- `ThreatTargetGoal.isValidTarget`; `ThreatEvents.shouldThreatTarget` (also true for hosted types).
- `MobsLdCommands`: accepts any host, and gets the new `/mobs_ld heads`.
- Also fixed: an engine bar no longer lingers when a behaviour is removed.

### 4.4 New behaviour fields (all optional)

| Where | New fields |
|---|---|
| top level | `base_attributes {id: base}`, `host {...}`, `target_slots {...}` |
| `movement` | `style: vanilla` (the host moves itself between abilities), `reach_rule: path\|line_of_sight` (replaces the "flying navigation always reaches" shortcut), `lose_after`, `ground_scan` (default 8; Wither 40) |
| ability | `cooldown` / `initial_cooldown` as Seconds; `target: lowest_threat \| slot:<name>`; `skip_when_full` |
| phase | `bar {color, overlay}` (applied to the adapter's bar); `host` (per-phase adapter options, e.g. skull cadence) |
| `boss` | `engage_on: any\|players` |
| `HitSpec` | `amount` (absolute at L0/Normal, final = amount x R, R = live attack_damage / declared base); `damage_type`. The existing `damage` (x attack_damage) stays for LdMob files. |
| `Telegraph` | `style: grow\|sweep` and `arrow` |

About the sweep style: SWEEP is already rendered. On rectangles it grows along the length, which is what the dash strip uses. On cones it grows the radius, not the angle, so angular sweeps (Annihilation) are not in this plan.

`AbilityRun.groundAt` scans from point+2 down to `ground_scan`, with no heightmap (because of the Nether roof). It reports "none" over the void; zones and summon spots are then skipped.

### 4.5 Events

New (all registered in `AbilityEvent.TYPES`):

| Event | Fields and behaviour |
|---|---|
| `hover` | `{from, until, height, relative_to: ground\|aim\|self, rate, retreat_to}`. Sets velocity every tick, which pins the flyer against FlyingMoveControl's gravity toggle. |
| `look` | `{at, until, heads: all\|side\|left\|right\|center, target: aim\|current\|slot:<name>}`. Overrides the slots while active and pauses side-head fire. |
| `fx` | `{sound, particles (at self\|heads\|aim\|ground), message {key, where: actionbar}}`. Server-side only. Every cue goes to the action bar, because Arenas timed runs hide vanilla boss bars. |
| `projectile` | `{skull: <named spec>, from: heads, aim: aim\|slot, waves, interval, spread, speed, dangerous}`. Speed fix: velocity is set to `speed` at launch and `accelerationPower = speed x (1 - inertia) / inertia` (inertia 0.95 normal, 0.73 dangerous), so the skull flies at a constant speed and flight time = distance / speed. The old `speed / 19` formula was only right for normal skulls. |
| `chain` | `{at, to}`. Ends the run and starts `to`; validated like chase chains. A chained ability whose target is not `current` re-picks its aim. |

Changed:
- **`dash`:**
  - `width: body | number`;
  - contact = the drawn strip (a player's center must be inside it), with the swept per-tick test;
  - constant `speed` (today speed = distance / (until - at));
  - `height` (y driven every tick);
  - `stagger_min_distance` (default 4);
  - `push: away | side`;
  - segmented SWEEP strip with an arrowhead.
  - Today's contact box is 0.9 wider than the strip; that is fixed.
  - "Struck once, even if blocked" stays.
- **`summon`:**
  - timed: runes at `telegraph.from`, spawn at `at`;
  - `count` / `max_alive` as `{min, max | base, per_extra_player, cap}`;
  - `placement {around: self|fighters|aim, radius, min_gap, clearance, ground}`;
  - `scale {max_health, attack_damage, armor}`;
  - `loot`, `xp`, `target: rune_player | aim`.
  - Order: create, then finalizeSpawn, then tags `mobs_ld_summon` and `mobs_ld_summoner:<uuid>`, then trackSummon, then addFreshEntity. After that, stats are normalized:
    - `mobs_ld:summoner_scale` as an ADD_MULTIPLIED_TOTAL modifier = target / current - 1 for HP and damage;
    - an ADD_VALUE modifier for armor;
    - then setHealth(max). As built (M5) there is no re-check at age 1: DD and Arenas scale synchronously inside addFreshEntity, the add's equipment modifiers are counted up front, and a later check would bake temporary effects into the permanent modifier.
  - This works whatever DD or Arenas did inside addFreshEntity, needs no reflection into DD, and avoids double scaling.
  - The ratio uses the summoner's base values declared in wither.json, not the instance's base, so adds follow a raid Wither's stats too.
- `trace` on a vanilla host logs one warning and does nothing (vanilla models have no hit tracks).

### 4.6 Wither adapter and mixins

`mob/host/wither/WitherAdapter.java`:
- `ready` = `getInvulnerableTicks() <= 0`. Built Withers start after the charge; /summon, Arenas and Wabbajack Withers start at once.
- The bar is the vanilla `bossEvent` (through an accessor).
- Muzzles come from the `getHeadX/Y/Z` invokers, using the head that actually aims. Vanilla fires each side head's skull from the opposite side; this fixes that.
- `bodyWidth` = 3.0 x scale.
- `install` swaps `NearestAttackableTargetGoal<LivingEntity>` for a `<Player>` one.
- Slots write `setAlternativeTarget(1|2, id)` **only with LivingEntity ids**: any other id crashes the server tick at the cast in `customServerAiStep`. Slots are cleared on reset.
- It holds still while busy.

`mob/host/wither/WitherSkulls.java`: the adapter owns skull cadence. It spawns **vanilla `WitherSkull`** entities from the right muzzle, tagged `mobs_ld_skull:<center|side|volley>`, with sound event 1024, inside the boss tick. There is no new entity type and no client code.

`mixin/wither/WitherBossHostMixin.java`. It uses full descriptors everywhere, never cancels `customServerAiStep` (OPAC hooks its HEAD), and never touches `dropCustomDeathLoot`.
- `performRangedAttack(IDDDZ)V` HEAD cancel when hosted. This one point stops the centre-head goal, the side heads, the idle blue skulls and their sound.
- In `customServerAiStep`:
  - wrap `getNearbyEntities` (side-head acquisition) → empty list;
  - wrap `setAlternativeTarget(II)V` ordinal 0 (the clear after a failed check) → skipped, so there is no aim flicker;
  - wrap `heal(F)V` ordinal 1 (the 1 HP/s regen) → skipped while engaged;
  - a non-cancelling HEAD inject zeroes `destroyBlocksTick` per the `block_breaking` option.
- In `aiStep`:
  - `@ModifyExpressionValue` on `getAlternativeTarget(I)I` ordinal 0 → 0 while the adapter holds movement;
  - `@ModifyConstant` on 5.0D → `hover_height`.
- In `hurt`: `@ModifyExpressionValue` on `isPowered()` → false when `arrow_immunity` is false.

`mixin/wither/WitherBossAccessor.java`: `@Accessor` `bossEvent` and `destroyBlocksTick`; `@Invoker` `getHeadX/Y/Z(I)D`.

`mixin/wither/WitherSkullMixin.java` is active only for tagged skulls owned by a hosted Wither. The amounts are looked up in the owner's behaviour at hit time, so they survive save/load and `/reload`.
- `onHitEntity`:
  - wrap the `Entity.hurt` call (the vanilla 8) → amount x R;
  - wrap `heal(5.0F)` → skipped;
  - wrap `addEffect` → the spec's effect, still only when the hit landed.
- `onHit`: wrap `Level.explode` → no explosion and no block damage; instead, splash damage to enemies in the radius (not the direct victim) using the same damage source vanilla skull blasts use, plus the vanilla particles and sound. OPAC's HEAD inject still runs, harmlessly.
- `canHitEntity`: false for the owner's summons and non-player #wither_friends, so skulls pass through adds.

The existing `mixin/WitherBossMixin.java` (full heal at the end of the charge, spawn-blast damage type) is unchanged.

### 4.7 Persistence
- A `mobs_ld_combat` compound holds: home, phase, enraged, fight time, and summon UUIDs. The legacy `mobs_ld_home` key is still read and written.
- On load: the phase is restored silently (no transition replay), engaged starts false, and initial cooldowns apply again.
- Orphans: a summon whose summoner is not loaded is discarded after 100 ticks. A periodic sweep removes adds whose summoner is gone.
- Arenas contract: met by rule 1 (spawned inside the boss tick). Vanilla wither skeletons are not OwnableEntity, so rule 2 cannot apply. Mobs_LD's summoner tags, set before addFreshEntity, are the owner record. No `ArenasApi` call is needed.

### 4.8 Keeping Gorefist working
- **Pass 1:**
  - every new hook is guarded with `!(this instanceof LdMob)`;
  - LdMob implements CombatHost, and `combat()` = `mobs_ld$combatOrCreate()`, still created in the constructor;
  - all new fields are optional, so `gorefist.json` and `rot_wart.json` load unchanged.
- **Regression:** Gorefist (trace, chase chains, Rot Wart summons, bar) and Rot Wart.
- **Pass 2:** delete LdMob's duplicate overrides (`customServerAiStep`, `hurt`, `die`, `remove`, save/load, seen-by, `removeWhenFarAway`), so each hook exists exactly once. Otherwise ticks and damage scaling would run twice.
- **Unchanged:** `registerGoals`, `createNavigation`, the death hold, GeckoLib, the synced data, HitTrackBaker and `trace`.

### 4.9 Other files
- `mobs_ld.mixins.json`: the new mixins.
- `MobsLdMod`: registers CombatEvents and HostAdapters.
- Lang keys in `assets/mobs_ld/lang/en_us.json` and a new `uk_ua.json`.
- `docs/behaviour.md`: a new "Vanilla hosts" section.
- `fabric-loader-junit` for the pure logic.
- No new entity types, client mixins or network payloads.

### 4.10 wither.json sketch (R1 part)

```json
{
  "base_attributes": {"minecraft:generic.max_health": 1711, "minecraft:generic.attack_damage": 2.0},
  "host": {"type": "mobs_ld:wither", "enabled": true, "hover_height": 1.5, "regen_while_engaged": false,
    "arrow_immunity": false, "block_breaking": "outside_runs",
    "skulls": {
      "center": {"every": 4.0, "speed": 1.6, "hit": {"amount": 2.4}, "splash": {"radius": 1.5, "amount": 0.6}},
      "side":   {"every": [4, 5], "speed": 1.6, "hit": {"amount": 2.0, "effects": [{"id": "minecraft:wither", "duration": 3}]}, "splash": {"radius": 1.5, "amount": 0.5}},
      "volley": {"speed": 1.6, "hit": {"amount": 4.0}, "splash": {"radius": 1.5, "amount": 1.0}}}},
  "target_slots": {
    "right_head": {"rule": "lowest_threat", "range": 30, "line_of_sight": true, "repick": [9, 11], "lost_after": 2},
    "left_head":  {"rule": "spread", "range": 30, "line_of_sight": true, "repick": [9, 11], "offset": 5, "lost_after": 2}},
  "movement": {"style": "vanilla", "reach_rule": "line_of_sight", "lose_after": 3, "reach_height": 4, "reach_depth": 8, "ground_scan": 40},
  "global_cooldown": 1.6,
  "boss": {"engage_on": "players", "leash": 64, "reset_after": 15, "recovery": 1.35,
           "soft_enrage": {"after": 480, "damage_multiplier": 1.5}},
  "abilities": {
    "soul_rush": {"duration": 4.0, "cooldown": 12, "initial_cooldown": 8, "weight": 3, "target": "current",
      "range": {"max": 30, "above": 6, "below": 16}, "track_until": 0.8, "events": [
        {"type": "hover", "from": 0, "until": 0.8, "relative_to": "aim", "height": 4, "retreat_to": 12},
        {"type": "look", "at": 0, "until": 1.8, "heads": "all", "target": "aim"},
        {"type": "fx", "at": 0, "sound": {"id": "minecraft:entity.wither.ambient", "pitch": 0.7, "volume": 2}, "particles": {"id": "minecraft:soul_fire_flame", "at": "heads", "count": 12}},
        {"type": "hover", "from": 0.8, "until": 1.8, "relative_to": "aim", "height": 0.4},
        {"type": "dash", "at": 1.8, "speed": 1.4, "overshoot": 5, "max_distance": 28, "width": "body", "height": 0.4,
         "push": "side", "on_wall": "stagger", "stagger_min_distance": 4,
         "telegraph": {"from": 0.8, "style": "sweep", "arrow": true, "color": "#6A2CB0"},
         "hit": {"amount": 20.0, "knockback": 0.8, "knock_up": 0.3}}]},
    "grave_call": {"duration": 3.0, "cooldown": [30, 60], "initial_cooldown": 20, "weight": 0, "force_after": 0, "skip_when_full": true, "events": [
        {"type": "hover", "from": 0, "until": 3.0, "relative_to": "ground", "height": 1.6},
        {"type": "fx", "at": 0, "message": {"key": "mobs_ld.cue.wither.grave_call", "where": "actionbar"}, "sound": {"id": "minecraft:entity.wither.ambient", "pitch": 0.6, "volume": 4}},
        {"type": "summon", "at": 1.6, "entity": "minecraft:wither_skeleton",
         "count": {"min": 2, "max": 4, "per_extra_player": 2, "cap": 12},
         "max_alive": {"base": 6, "per_extra_player": 3, "cap": 16},
         "placement": {"around": "fighters", "radius": [3, 6], "min_gap": 2, "clearance": 3, "ground": true},
         "telegraph": {"from": 0.2, "radius": 0.9, "color": "#3A1F5C"},
         "scale": {"max_health": 1.0, "attack_damage": 1.0, "armor": "summoner_bonus"},
         "loot": false, "xp": false, "target": "rune_player"}]}
  }
}
```

Per phase, the `phases[]` entries set: `below_health` 0.5 and 0.2, the ability lists, the transitions, `damage_taken` projectile x0.6, `bar.color` blue or red, and `host.skulls.*.every` for the tighter cadence.

---

## 5. Numbers for the balance team

**Formula** (Wither as attacker):

landed = amount x R x damage_dealt x 1.5 (Hard) x armor factor

- R = 1 + 0.02L if DD keeps attack_damage scaling on the Wither; 1.9 at L45. Soft enrage adds x1.5.
- Armor factor = 30 / (30 + armor): 0.746 at armor 10.2.
- All types used are difficulty-scaled for non-player attackers: mob_attack, wither_skull, and the explosion source for splash.

**Suggested damage_dealt** = 136.7 / (20 x 1.9 x 1.5) = **2.40**. If Eden Loot's boss type drops DD's attack scaling (R = 1), it is **4.56**; the boss-hit numbers below come out the same.

Hand-off in hits_brief format: reference "Soul Rush (contact)", base 20.0, kind melee, per_hit true, difficulty_scaled true, dd_scales true.

**L45 Hard, damage_dealt 2.40, typical player 255 HP, armor 10.2:**

| Hit | L0 Normal raw (= amount) | L45 Hard raw | Landed | % of 255 HP |
|---|---|---|---|---|
| Soul Rush contact (telegraphed) | 20.0 | 136.8 | 102.1 | 40% |
| Armored Descent slam | 16.0 | 109.4 | 81.7 | 32% |
| Grave Slam | 14.0 | 95.8 | 71.5 | 28% |
| Rush follow-up (no strip) | 12.0 | 82.1 | 61.3 | 24% |
| Marching Graves, per circle | 9.0 | 61.6 | 45.9 | 18% |
| Volley skull direct / splash | 4.0 / 1.0 | 27.4 / 6.8 | 20.4 / 5.1 | 8% / 2% |
| Middle-head skull direct / splash | 2.4 / 0.6 | 16.4 / 4.1 | 12.3 / 3.1 | 4.8% / 1.2% |
| Side-head skull direct / splash | 2.0 / 0.5 | 13.7 / 3.4 | 10.2 / 2.6 | 4.0% / 1.0% (+ Wither I 3 s) |
| Add swing (wither skeleton is the attacker, its own damage_dealt 1.0) | 8.0 | 22.8 | 17.0 | 6.7% (+ vanilla Wither I 10 s) |

- With R = 1, the add swing is 12.0 raw / 9.0 landed (3.5%). Add damage then stops scaling with level; see question 1.
- Worst case in P2/P3, caught by both rushes: 64%. The side push makes the second contact unlikely.
- Skull pressure, if every skull hits (upper bound; strafing dodges most, and fire pauses during abilities):

| Case | Tank | Other player |
|---|---|---|
| P1, 2 players | 4.2 HP/s (1.6%/s) | 3.4 HP/s |
| P1, solo | 7.6 HP/s (3.0%/s) | |
| P3, solo | 9.9 HP/s (3.9%/s) | |

  If chip is too high, lengthen the intervals in data. Do not lower damage_dealt: it is fixed by the biggest hit.

**Outside damage_dealt**
- The spawn blast: unscaled, 99 Normal / 148.5 Hard at the center, landing about 111 (43%), telegraphed by the charge.
- Every Wither-effect tick (side skulls give 1 tick; an add hit gives 5): no attacker. Eden_LD makes each tick 1% max HP + 4% missing HP.
- The adds' melee: the add is the attacker, with its own damage_dealt (default 1.0). Mobs_LD does not copy the Wither's.

**HP.** Base 1711 x (1 + 0.04L), ±1% DD randomness:

| Dimension | Level | Wither HP | Add HP |
|---|---|---|---|
| Overworld | 12 | 2,532 | 30 |
| Nether | 25 | 3,422 | 40 |
| End | 45 | 4,791 | 56 |

- Wither armor at L45 is 13.
- One trio wave = 6-8 adds = 336-448 HP, about 7-9% of the bar. (Superseded 2026-10-25: adds have max_health / 40, so a wave of 6 is 15% of the bar.)

**Fight length at L45** (about 29 DPS per player, 60% uptime, boss armor about x0.70):
- trio about 130 s, duo about 195 s, solo about 6.5 min;
- the two transitions add about 6 s of invulnerability;
- soft enrage at 8 min only stops stalling.

**Other dimensions.** A single L45 calibration one-shots lower levels:
- Overworld L12 (40 HP players): the rush lands 69.
- Nether L25 (101 HP players): the rush lands 81.
- Level-aware damage_dealt that keeps the 40% target: **about 0.55 at L12, 1.20 at L25, 2.40 at L45** (0.68 / 1.82 / 4.56 if R = 1).
- Optional HP trim to the levels of comparable bosses: about 1,982 (Overworld) and 2,962 (Nether).

---

## 6. Compatibility notes

- **Open Parties and Claims:**
  - its HEAD injects on `WitherBoss.customServerAiStep` and `WitherSkull.onHit` keep running; we only wrap calls inside those methods;
  - vanilla block breaking stays inside the boss tick behind the mobGriefing check;
  - reworked skulls break nothing.
- **Soulslike Weaponry:** the `dropCustomDeathLoot` TAIL hook is untouched, and the death is real. The Withered Wabbajack can spawn up to 3 hosted Withers at once (question 4).
- **Dungeon Difficulty:** `base_attributes` are set in the constructor, so DD multiplies them once per dimension and keeps the health ratio. Saved Withers keep their saved base values. Adds are normalized after DD has run, so they are never scaled twice. No reflection into DD.
- **DD Loot Stage:** damage_dealt applies to every hit where the Wither is the attacker, including the tagged vanilla skulls. **Deploy together with loot-stage 1.1.0:** live runs Mobs_LD 1.5 and the Prism Server test instance 1.4, neither with #loot_stage:unscaled or the mobs_ld damage types; the rework ships as part of the full Mobs_LD rewrite.
- **Eden Loot:** owns damage_dealt and the per-level fix (section 5).
- **Eden_LD hazards:** boss-applied Wither is level I, 3 s at most. The adds keep the vanilla 10 s Wither I. Both are outside the calibration.
- **Arenas_LD:**
  - Adds become minions through rule 1.
  - The Wither raid spawner (raid_nether_wither: 15000 HP, scale 3, attack_damage 40) must be retuned with the Arenas session **before deploy**:
    - set "Scale minions: Off" (Mobs_LD already derives the adds from the boss's stats);
    - set the attack_damage override back to 2, because R would otherwise be 20 for every hit and add; Arenas' own player-hurt multiplier keeps doing the raid scaling;
    - scale 3 makes the rush strip about 9 wide.
  - Inside runs: cues mirror to the action bar (bars are hidden), block breaking is off, and Mobs_LD leash/reset is off (question 4).
- **Combat Roll:** every hit goes through `hurt()` on a single tick, and effects apply only when the hit lands. Confirm `invulnerable_ticks_upon_roll: 8` on live; the 10-19 snapshot had 0.
- **Spell Engine:** #c:bosses still blocks crowd control, so the boss makes its own openings (stagger, slam recovery, Grave Call). mob_attack and wither_skull are evadable, so evasion can dodge the rush and skulls (by design). WitherBoss.addEffect always fails, and the kit applies no effects to the boss.
- **Paladins:** the splash uses the same damage source as vanilla skull blasts, so Barrier behaves as before. Existing gap: mobs_ld:wither_spawn_blast is not in #paladins:barrier_protects; add it for parity.
- **Immersive Optimization:** add `"minecraft:wither": false` and `"mobs_ld": false`. Otherwise skipped ticks shift telegraph timing and dodge windows. Adds are vanilla wither skeletons and may still be throttled, which is acceptable.
- **EntityCulling (client):** whitelist `mobs_ld:attack_zone_visual` if strips flicker.
- **Loot n Explore, DataRewriter, FTB Quests, BOMD lich counter, soul_trap, Divine Encounters:** all fine. The loot table is the built-in one (no override file) and the kill is player-credited.
- **Hats Renewed:** a Wither may wear a hat (cosmetic; can be excluded). Jade, Debugify, ServerCore, Waystones and puzzleslib are unaffected.
- **End risk:** knockback of 0.6-0.8 near island edges can throw players into the void. Fight away from edges, or lower the End values.
- **Deploy:** the server and the client modpack get the same Mobs_LD version. There is no new client-only code, but the jar must match.

---

## 7. Milestones and test plan

Standard test setup (per the owner's rules):
- headless dev server through Loom `runServer` for logic and regression tests;
- dev client on its own Xvfb :97 (never the desktop), volume 10%, killed by PID only;
- scripted test world: `/gamerule doMobSpawning false`, then `/kill @e[type=!player]`;
- abilities forced with `/mobs_ld ability`, a test datapack setting weights to 0, and `/mobs_ld debug`, `/mobs_ld threat`, `/mobs_ld heads`;
- DD is not in the dev run, so levels are simulated with `/attribute ... modifier`.

| # | Milestone | Size | Exit checks |
|---|---|---|---|
| M0 | Ops prep: Immersive Optimization and EntityCulling exemptions; confirm Combat Roll i-frames on live; deploy plan with loot-stage 1.1.0; raid spawner retune agreed with the Arenas session | S, under 0.5 d | config diffs reviewed |
| M1 | Host layer (4.2, 4.3, 4.7) + LdMob pass 1 | L, 3-4 d | headless: Gorefist and Rot Wart abilities, trace, chase chains, summons and bar unchanged; a test zombie behaviour runs; save/reload keeps phase and summons; chunk unload loses no adds and leaves no orphans. Then pass 2 and the same regression again |
| M2 | Wither adapter core: ready gate, vanilla bar and phase colors, movement hold, `hover` event, hover_height, regen gate, players-only targeting, arrow option, block_breaking option, line-of-sight reach, deep ground scan, base_attributes | M, 2-3 d | built Wither: charge, full heal and blast type unchanged; /summon Wither engages at once; a cow never engages it; hiding in a tunnel passes aggro after 3 s; reset after 15 s; a Nether-roof world places zones on the floor |
| M3 | Heads and skulls: TargetSlots + picker, side-head wraps, `look`, `/mobs_ld heads`, skull cadence, WitherSkullMixin, `projectile` with the speed fix, `fx` | M, 2-3 d | JUnit picker cases: solo with a pet = all heads on the player; 2 players alternate; 3 players one each; ties; 10 s re-pick; lost target. Two offline dev clients on :97 confirm the heads in game; skulls break no blocks and do not heal; JUnit projectile flight times |
| M4 | Dash: strip = contact, swept test, constant speed, height, stagger minimum, segmented sweep strip with arrowhead, push side, `chain`, chained re-aim, new target rules | M, about 2 d | JUnit contact vs strip edges and no tunnelling at 1.4 b/t; Xvfb screenshot bursts of the strip on flat ground, a slope and under the Nether roof; a roll 0-7 ticks before contact clears the rush; a wall stagger only after 4+ blocks |
| M5 | Summons: timed runes, placement, fighter count, Seconds codec, skip_when_full, post-spawn normalization, loot/XP off, tags, orphan sweep | M, about 2 d | JUnit count formula; solo 2-4 per wave within max alive; add stats match the Wither ratio at simulated L12/25/45; adds removed on reset and death; orphan discarded after its summoner is unloaded |
| R1 | **First release = M0-M5 + R1 wither.json** (heads, skull chip, Soul Rush, Grave Call, line-of-sight reach, leash/reset, phase bar colors and skull cadence) | about 12-14 d total | |
| M6 | Content: Marching Graves, Grave Slam, Triple Volley, Soul Rush x2, both transitions, soft enrage; en_us and uk_ua; docs/behaviour.md "Vanilla hosts" | S-M, 1-1.5 d | every ability forced once on Xvfb with screenshot bursts; timings checked against the debug trace |
| M7 | Verification and calibration | M, 2-3 d + playtest | hits sheet to Eden Loot (run its test_damage_dealt.sh); compat run on a copy of the Server instance (DD, loot_stage, Arenas, OPAC, Combat Roll, Soulslike drops, Loot n Explore spine); 2-3 player playtest at End L45; tune intervals |

Full plan: about 15-19 working days.

---

## 8. Open questions for the owner

1. **HP and damage source.** The proposal has three parts:
   - Mobs_LD sets base max_health 1711 in wither.json (4,791 End / 3,422 Nether / 2,532 Overworld);
   - DD keeps its +0.02/L attack_damage on the Wither (damage_dealt 2.40, and the adds' damage follows the level);
   - Eden Loot fixes the Overworld/Nether one-shot with a level-aware damage_dealt (about 0.55 / 1.20 / 2.40).

   *Default: yes to all three. If Eden Loot cannot do per-level values, Mobs_LD adds one documented per-dimension damage factor.*
2. **Arrows below 50%.** Keep vanilla's full arrow and wind-charge immunity, or replace it with projectile damage x0.6? *Default: replace with x0.6, so archers still contribute in P2/P3.*
3. **Untelegraphed second rush from P2.** P1 is always telegraphed. From P2, the chained follow-up has no ground strip, deals 60%, and has a 0.7 s head-snap and sound tell. *Default: yes.*
4. **Which Withers get the kit, and resets.** *Default:*
   - every Wither with the behaviour: built, /summon, and the Arenas raid after its retune;
   - leash 64 and reset after 15 s with nobody visible (full heal, adds removed, flies home) for Withers outside runs;
   - off inside Arenas runs, where Arenas owns the wipe;
   - Withered Wabbajack Wither spawns disabled in its config (`disable_use_withered_wabbajack: true`), so one bad roll does not start three boss fights.
5. **Arena safety and add rewards.** *Default:*
   - vanilla block breaking (1 s after being hit) outside Arenas runs, off inside runs;
   - skulls never break blocks;
   - adds drop no loot or XP, so wither skeleton skulls cannot be farmed during the fight.

The repo is unchanged; the files referenced are under `/home/ledok/Projects/Mods/LeDok/Fabric/Mobs_LD/src/main/java/net/ledok/mobs_ld/` (`mob/combat/`, `mob/behaviour/`, `mixin/WitherBossMixin.java`), `/home/ledok/Projects/Mods/LeDok/Fabric/Mobs_LD/src/main/resources/data/mobs_ld/mob_behaviour/` (gorefist.json, rot_wart.json), `/home/ledok/Projects/Mods/LeDok/Fabric/Mobs_LD/src/main/resources/assets/mobs_ld/lang/en_us.json` and `/home/ledok/Projects/Mods/LeDok/Fabric/Mobs_LD/docs/behaviour.md`.

---

## 9. Deploy checklist

Ops items from section 6 and M0, to tick off when R1 goes to the servers:

- [ ] **Same jar everywhere:** the server and the client modpack get the same Mobs_LD version.
- [ ] **DD Loot Stage 1.1.0** is deployed together with this Mobs_LD (it has `#loot_stage:unscaled` and knows the
  mobs_ld damage types).
- [ ] **Immersive Optimization:** add `"minecraft:wither": false` and `"mobs_ld": false` to its exemptions.
- [ ] **EntityCulling (client):** whitelist `mobs_ld:attack_zone_visual` if the strips or runes flicker.
- [ ] **Combat Roll:** `invulnerable_ticks_upon_roll: 8` (already set on live and on the Prism Server instance by
  Eden_LD; check it is still there).
- [ ] **Arenas_LD raid Wither spawner** (`raid_nether_wither`): "Scale minions: Off" (Mobs_LD scales the adds from the
  Wither) and the attack_damage override back to 2.
- [ ] **Soulslike Weaponry:** `disable_use_withered_wabbajack: true`.
- [ ] **Paladins (optional):** add `mobs_ld:wither_spawn_blast` to `#paladins:barrier_protects` for parity with other
  explosions.
