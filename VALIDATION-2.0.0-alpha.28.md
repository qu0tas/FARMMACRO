# alpha28 — Motion B1 validation

Target: Minecraft26.1.2 official Mojang / Fabric0.19.5 / API0.155.3+26.1.2 / Java25 / Loom1.18.3.

## Scope
Independent selectable Aether backend for SmoothTurn timed/follow, actual upstream preset values/all25easingcurves, quantized speed budget/residuals; independent one-pass farm angle and boundary pause copies. Kaelix default retained. No changes to Combat engine or preset migration, panic detectors, world physics, financial controllers, HUD drawing or mixins in this stage.
Full scope/exclusions: docs/MOTION-AETHER.md. B1, NOT completed stageB or stable2.0.0.

## Execution
- First `build quickCheck` PASS79s (included fresh Gradle9.7.1 download).
- Final `clean build quickCheck`: see validation/clean-build-quickCheck.log in sourceZIP for authoritative result.
- MotionQuickCheck: original Normal/Safe/Efficient fields;25curve endpoint/finite checks; shortestyaw and small pure quantized timed/tracking convergence; target retarget/NaN failclosed; snapshot isolation; Gson new/oldconfig; cumulative angularbudget; collinear/corner/floor/drop/landing pause copy/maxexisting/disable. <=276pure frame calls, no seeds/FPS sweep.
- Previous small AttendedQuickCheck and ModuleRetirementCheck retained: near-point failclosed, routecopy/XYZ/mirror, disabled retired services,18cards/5categories/10validtabs.
- Static: git diff --check; fulloriginal AetherreferenceSHA256; embedded3preset byteidentity; metadata/license/mixin/retiredclasses; sourceZIP CRC and source/binaryidentity. Packaging script fails if checks do not pass.

## Not executed / not proven
No Minecraft/client/server/Hypixel, real UI/render harness, flight/route simulation, live entities, packet exercise, GUI transactions, FPS benchmark, routeStress/configSweep/soundStress. No visual/gameplay, human-likeness, staff-detection or ban-safety claims. No push/commit/publish.

## Manual acceptance needed
Compare both modes on the same short farm: initial angle and manual takeover, no pitch drift mid-row, timing at markedcorner/floor/landing, resume without regenerating delays, low/high sensitivity, explicitly opt-in return navigation. Confirm existing Combat/recording behavior unchanged. CameraBinding remains instant in B1; don't interpret it as a failed smooth-turn feature.

## Delivery
CombinedGPLv3 with LICENSE/originalCC0/third-party notices and pinnedcorrespondingsource. Full sourceZIP, not blindly overlay old sources: cleanfolder or explicitly delete all10paths listed in REMOVED-FILES.txt. Binary is identical inside ZIP and separate JAR. Stable pending user testing/further roadmap tasks.
