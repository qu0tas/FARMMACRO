# Validation 2.0.0-alpha.13 / K2-007

Build status: BUILD SUCCESSFUL; jar-only; gradlew jar only. Metadata version, required classes, single mixin registrations, whatsnew version, absence of stress/navTest entries and cumulative file list inspected statically. git diff --check clean. No runtime behavior was executed.

- Runtime acceptance NOT performed. No Minecraft/client/server/tests/stress/simulations/benchmarks/GUI harness.
- Source/API/javap/static inspection only. LocalPlayer.swing vanilla swing packet, particle packet fields, official26.1.2 item/interaction/BlockTags/shape/raycast/render methods; reused NavigationInput/ReturnWalker/FlightGuide/VisibleBody/ShotPath/SmoothTurn.
- New pest classes/settings/GUI/particle hook, one shared pre-action panic detector, old config toggles preserved, count/TTL/unknown/death distinction, bounded FSM/travel/Tracker/leaf constraints, exact expected single-use move and slot ID/UUID checks inspected statically.
- Version/resources/class presence, mixin registrations, archive cumulative source bytes/hash/list, no private config or tests in JAR; git diff --check.
- Report: docs/navigation/PESTS-2.0-ALPHA13.md. K007 code implemented; user acceptance outstanding; next K008.
- No copied/adapted GPL code or assets. No commit/push/publish. Stable2.0.0 requires user approval.
