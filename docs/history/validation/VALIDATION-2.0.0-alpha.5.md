# Validation — 2.0.0-alpha.5

Scope: K2-004 package4a, navigation-only ground ascent/progress/lease/toggle input. Not stable2.0.0, not complete A*/flight/climb rewrite.

- Platform unchanged MC26.1.2/Fabric Loader0.19.5/API0.155.3+26.1.2/Loom1.18.3/Java25.
- Cached javap ToggleKeyMapping.setDown bytecode verified (toggle flips only on true; false ignored); Options.toggleCrouch/toggleSprint, AABB min/max/move/intersects and VoxelShape.toAabbs APIs checked.
- Static ascent review: no inherited waypoint pulse or unguarded collision-jump fallback; dedicated ascent aligns35°, prefers clear walking treads, bounded conservative body envelope and3 attempts per target with release. New plan remains under prior replan limit. No physics/packet writes.
- GroundProbe live footprint gets max reachable support before body sweep; exact body check used instead of coarse maximum-column/edge veto. Hazards/support/unknown terrain retained, coarse/cache path conservative. PathLevel collision cache bounded512/adapter-local, live adapter rebuilt per tick, no worker-live-world access.
- Best-so-far progress metric excludes cumulative oscillation, accounts height and genuine alignment;80tick deadline/intentional pause handling. Ground nav completion tolerance0.2 guarded by nav-only requireHeight, not general precision playback.
- All ReturnWalker key calls through NavigationInput; toggle states set only if different, client-thread lease guards inactive/foreign clear. Lease released on DONE/cancel/failed planning/timeout/door failure; owner only stops own global follow turn. No lease for every other subsystem claimed.
- Context guard screen/focus/pause/pending and existing world/teleport checks; humanizer skipped for ascent/drop/air; old chunk/door/climb/flight gates retained. Prior panicB/Combat/queue/perception/privacy/crouch preserved.
- New nav classes only; no external code/assets copied, no runtime automations beyond requested existing scope. Existing mod config and route format unchanged.
- NEVER launched Minecraft/server/tests/stress/simulations/benchmarks/GUI harness. Only static source/API/javap/gradlew jar/archive checks.
- Runtime stair/block/drop/toggle sprint/lease/GUI/world transition/performance and old Combat regressions require user acceptance. No guaranteed path success/safety/human likeness/server-rule compliance.

Build status: BUILD SUCCESSFUL; jar-only; gradlew jar only. Metadata version, required classes, single mixin registrations, whatsnew version, absence of stress/navTest entries and cumulative file list inspected statically. git diff --check clean. No runtime behavior was executed.
