# Kaelix2.0.1-alpha.6 validation
Scope: manual Aim dungeon filters + Glacial Scythe bounded lead/floor point only. Baseline fullalpha.5 source, no game launched or heavy simulations.
Research (mechanics/names only, no implementation copied):
- https://wiki.hypixel.net/Glacial_Scythe — ground explosion3blocks; no documented speed.
- https://hypixelskyblock.minecraft.wiki/w/Glacial_Scythe — direct+block explosion tip; not guarantee.
- https://wiki.hypixel.net/The_Professor — Player, guardian form phase3; named guardian bodies accepted, no phase automation.
- https://wiki.hypixel.net/Catacombs_Mobs
- https://wiki.hypixel.net/Angry_Archaeologist
- https://wiki.hypixel.net/Frozen_Adventurer
NPC UUID/name association is a conservative client heuristic, not verified in live game. Unknown encodings skipped. Phase/ownership/invulnerability unresolved by design, Watcher head not targeted, Thorn still Spirit Bow.40b/s estimate requires user calibration.
Checks: compileJava/compileStressJava PASS15s. Final clean build quickCheck PASS (all 8tasks executed). Pure catalogue/type/UUID exclusion/intercept/caps/config-profile checks added, no game raycast/render/integration claims.

Final archive: 653 complete corresponding source files;25 unchanged client mixins; baseline-change allowlist, third_party/fishing/panic/mixin/route/automatic-Combat unchanged-source checks PASS. ZIP/JAR CRC, source/resource identity and SHA checked. No game/live NPC/physics testing claimed.
