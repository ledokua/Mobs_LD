# Changelog

## 1.7.11
- A creeper running a behaviour never fuses or explodes (nor can it be lit); its attacks are its abilities.
- The Sand Golem Colossus holds its ground-slam pose during recovery and lights its chest ankh. It completes a
  slam or sweep before its first husk call, and calls three husks solo plus one per additional fighter, up to ten.
- Summoned husks take 10% of the Colossus's max health and inherit its attack scaling, bonus armor, and
  DD Loot Stage damage multiplier when available.
- `after_abilities` gates an ability until any named prerequisite finishes in the current fight.
- `/mobs_ld debug preview` outlines locked area-hit zones and shows the impact tick and hit targets for operators.
- The build checks shipped mob clips, ability timing, marker bones, and baked hit-track freshness.

## 1.7.10
- `shoot`: the drawn line stops where the shot would first hit a block (a low wall under the shot's path does not
  cut it).

## 1.7.9
- `telegraph.repeat` and `projectile.directions.step`: lines drawn and fired one after another, clockwise.
- Skull spec `hit_radius`: skulls that hit anything passing within that many blocks of their path.

## 1.7.8
- Telegraphs follow the terrain: each part is laid on the floor under it (stairs, slabs, steps), joined by walls at
  height changes, and left out where the floor is outside the hit's height band (the band is now sent to clients).
- `shoot` event: fires any projectile (arrow, small fireball, ...) down a line drawn on the ground first.
- `telegraph` event: draws a shape that deals nothing (e.g. the paths of a volley).
- `projectile.directions`: skulls fired along fixed directions around the mob (matching a `rays` shape).
- `hover.home`: fly back to the mob's home (an Arenas_LD arena's spawn point).
- Ability `only_tagged`: an ability only mobs with one of these tags may use.

## 1.7.7
- `hit.ignite`: seconds the victim burns once a hit lands (ability hits and skulls).
- `config/mobs_ld_damage.json` is written at startup.

## 1.7.6
- **Attack profiles** (`data/<ns>/mobs_ld/attack_profiles/*.json`): resize any mob's hits on players before
  `loot_stage:damage_dealt` and the Arenas tier scale them.
  - Actions: `hits` × a reference hit, `multiply`, `set`, `ignore`.
  - Matches: damage type or tag, direct entity, ability, or a hit with no attacker near a mob of the type.
- **Hit caps** (`config/mobs_ld_damage.json`, off by default; a profile can set its own): one mob hit takes at most a
  share of the player's max health. The cap applies after damage scaling and before armour.
- `/mobs_ld damage config` reloads the caps.
- Shipped with no profiles and caps off, so nothing changes until a server adds a profile table.

## 1.7.5
- `/mobs_ld damage audit start|stop|dump|status|attributes`: records every hit on a player, grouped by dealer, damage
  type, projectile and ability. It writes the raw amount, the share of max health, the health taken and the dealer's
  stats to `mobs_ld/damage_audit/*.json`, and lists the mob types without `loot_stage:damage_dealt`.
- `/mobs_ld debug` also writes its trace to the server log.

## 1.7.1 - 1.7.4
- `summon.minion_link`:
  - a killed add hurts its summoner, credited to the add's killer;
  - once all linked adds are dead, the summoner takes extra damage until its next summon.
- `chain.if_ready`: hand over to the next ability only if it could start on its own.
- `stagger.recover`: a no-ability window after a stagger.
- Wither: Grave Call comes only after a full Soul Rush sequence; a wall stagger cancels it.
- Threat:
  - summon damage shares threat with the summon's owner;
  - mrpg_lib stealth (Infiltrator's Vanish) drops threat;
  - Alter Ego decoys hold aggro as provokes and copy the caster's health.

## 1.7 - Warden rework (M1 - M3)
- The engine can host Brain-driven mobs through a host adapter. The Warden is hosted only in Arenas_LD runs
  (`host.only_tagged`); world Wardens stay vanilla.
- New: the `clip`, `vulnerable` and `listen` events, `rays` and `ring` shapes, widening lanes, `each_fighter` hits with
  lead, `hit.stacks`, `when.behind`, `top_damage` targeting, player-count limits, cooldown groups, and the
  `mobs_ld:warden_sonic` damage type.
- The Warden's P1-P3 kit with the Sonic Boom Circle.

## 1.6 - Wither rework (R1)
- Ability engine runs on any vanilla mob (vanilla hosts, host adapters, `base_attributes`, `movement.style: vanilla`).
- Wither: three-head targeting with target slots, skull volleys, Soul Rush dashes, Grave Call summons. Its spawn blast
  has its own unscaled damage type.
- Summon extensions: runes, placement, scaling from the summoner, bound adds, no-loot adds.

## 1.5 and earlier
- Threat system (MMO-style aggro, taunts and provokes, Spell Engine AGGRO compat).
- Data-driven mob foundation on GeckoLib 4: entity declarations, the behaviour JSON format, phases and boss rules,
  `trace` hit tracks, chase and chain. Gorefist and Rot Wart.
