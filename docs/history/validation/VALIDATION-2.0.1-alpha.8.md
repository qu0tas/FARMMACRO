# Alpha.8 manual Aim panic bugfix
Verified baseline: complete alpha.7 ZIP(661files), SHA2566cc05bbbc6d8a0667862c9c9ba7408b44ed11262601f655c89f05a480487bea3.
No new MC API/mixins, implementation only control-flow/policy/UI. No public code copied.
Manual-only excludes automatic alarms; actual concurrent farm/fishing/special/sway preserved. Global direct trigger defence stops only camera silently before effects. Existing context ownership/lifecycle retained. Four pure policy cases + source call-site checks, not game/network integration tests.
Final clean build quickCheck PASS,8tasks executed. No Minecraft/runClient/runServer/heavytests or push/publication.

Final archive: 664complete source files,25unchangedmixins. Truth-table + explicit source-callsite invariants PASS. Baseline changeallowlist and retained tree/Sweep/Hunting/Scythe/dungeon/route/fishing/motion/mixin/reference identity PASS. JAR+ZIP CRC/resource/source/SHA checks PASS;no live claims.
