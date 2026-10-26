# Validation — Kaelix 2.0.1-alpha.9

Baseline: complete verified alpha.8,664 corresponding files; no rolled-back git HEAD restore.

## Executed
- Java25/FabricMC26.1.2: clean build quickCheck PASS,8tasks,18s. All existing checks plus HuntingTreeQuickCheck passed. No Minecraft, client/server, routeStress/configSweep/soundStress, commit/push/publication.
- API javap: Items.BOW/CROSSBOW, BlockPos.offset/immutable and Mob hierarchy confirmed from cached26.1.2 JAR; production code compiled.
- New bounded pure tables:9amphibian/aquatic type filters; any-name nearest mode; level/HP label normalization; nearest distance-vs-angle; connected diagonal/upper logs beyond oldpatch; known branches survive removed trunk; unrelated separated tree not selected; height-first/Sweep ties; max512logs/4096predicate checks; finite lead config and profile isolation; moving/stationary lead and teleport rejection.
- Source-callsite checks verify runtime manual body filter, Hunting lead routing and camera-only boundaries. Existing automatic Aim loop, panic, MobNames, route/fishing/motion/mixins/reference sources byte-identical. No new packet/movement/attack/click calls in helpers.25unchangedclientmixins.
- Complete corresponding source manifest667files. JAR/ZIP CRC, resource identity, version/license/class presence, no experiment/QuickCheck runtime classes, embedded-JAR identity, source identity and SHA256 checked during packaging.

## Not executed / limitations
Pure tables are NOT Minecraft entity/raycast/network integration tests. Hidden/disguised display-only bodies are not solved. Explicit nearest mode can target ordinary animals and any living visible Mob, not only SkyBlock Hunting creatures; players/stands/displays never qualify. Range/FOV/LOS still apply; acquired live target retained.
Hunting prediction uses configurable40b/s estimate and constant observed velocity, capped1s/4b; actual configured default.65s/50ms. No arrow gravity/drag/water trajectory model, no automatic fire. Other item forecast explicit opt-in; lasso/net defaultcenter.
Tree graph is client loaded connected permitted-material wood, not authoritative server tree identity/stage. Touching trees can merge. Remembered branches survive cuts, but unknown disconnected portions cannot be inferred. Radius16b default4–24,512logs/4096reads per scan; rays<=96with resumablecursor, validlock scan every5ticks, invalid immediately. Highest reachable among budgeted probes, progressively checks remaining candidates; same-height validlock stays stable. Reach unchanged, no game-side block removal or auto-click. Server stage order remains player-selected, WrongStyle/protection guards retained. Logs unchanged by server may be aimed again. Sweep count heuristic, not exactserverformula.

## User live checklist
1. Combat/manual Aim: select Hunting nearest(any name), hold/toggle configuredAim near frog/axolotl/fish; move/shoot yourself. Check nearest visible body and normal-player exclusion.
2. Hold bow/Scythe: toggleHunting lead and compare side-moving target. Calibrate projectileSpeed/MaxLead. Lasso/net: other-itemlead off -> centre.
3. Mark correct tree-stage log and enableAim: cut manually, verify next available upper log/diagonalbranch and continued branch memory after trunkcut. Move into reach if upperblockfar. WrongStyle requires manualstagechange/rearm.
4. End/release/menu/focus/freecam/world quietstop; isolatedmanual has no panic; automaticmacros retain existingpanicbehaviour.
