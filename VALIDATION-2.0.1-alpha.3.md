# Kaelix 2.0.1-alpha.3 — validation
Target: Fabric/MC26.1.2/Java25. Base: delivered alpha.2 ZIP. Upstream: lyn1dev/skyblock-autofisher @ ec82aec357406d1137189936917775030d6ef56b (CC0).
Scope: reel/recast/Fireveil/delays/debug/commands, GUI/bind/HUD/lifecycle; no experiments/services resurrection.
Checks: javap API review, small deterministic FishingQuickCheck tables, existing quickCheck smoke suites, full build. Final result recorded after build.
Not performed: Minecraft/client/server/liveHypixel/stress/heavy simulations. Fishing marker geometry/packet latency/Fireveil kill effectiveness require user acceptance. Automation can violate server rules; no ban-safety guarantee.

Final clean build quickCheck: PASS. 641 full source files, 20 added paths, 29 modified baseline paths, baseline25mixins unchanged;52canonicalmessages and no experimentclasses. JAR/resources/source identity, ZIPCRC and SHA256 verified. Existing route/Combat tracking/camera/idlelook/gaze implementations preserved except explicit fish ownership gate in AimAssist. No game/heavytests.
