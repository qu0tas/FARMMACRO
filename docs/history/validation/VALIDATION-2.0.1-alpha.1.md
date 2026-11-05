# 2.0.1-alpha.1 validation

Base: delivered stable2.0.0 full source archive (616files), not environment's older alpha31.
Scope: ExperimentalTable attended mouse module + automaticZealots gaze panic + recent-area IdleLook.

Checked: Java25/Minecraft26.1.2 API via javap; compile; bounded pure ExperimentQuickCheck fixtures added to existing quickCheck. Existing retired services kept inert. No Minecraft/client/server, routeStress/configSweep, live account/server testing or publication.

Command: JAVA_HOME=/data/jdk25 PATH=/data/jdk25/bin:$PATH bash gradlew --no-daemon clean build quickCheck
Final result/log included in source ZIP under validation/clean-build-quickCheck.log.

Not verified live: Mixin application under Minecraft, actual Hypixel titles/slot phases/reveal callbacks/powerup variants, OS cursor callbacks at different GUI/HiDPI scales, table completion/awards, head-yaw approximation with real server players, false positives and remembered-area camera appearance. Unknown table format must not be mistaken for successful automation; no clicks or safe stop is an intentional fallback. User manual acceptance required. Current alpha deliberately does not choose tiers, spend Bits, claim rewards, automate inter-game menus or implement Ultrasequencer. No ban-safety guarantee.

Final clean build quickCheck: PASS. 630 full source files, 12 new Java files, 29 registered client mixins. Original runtime assets retained except localized key label and whatsnew; pinned references/licenses unchanged. No game launched.
