# Static validation — Kaelix 2.0.0-alpha.10

Build status: BUILD SUCCESSFUL; jar-only; gradlew jar only. Metadata version, required classes, single mixin registrations, whatsnew version, absence of stress/navTest entries and cumulative file list inspected statically. git diff --check clean. No runtime behavior was executed.

- Scope: K2-004 implementation complete, game acceptance outstanding. Prior K001–K003 preserved.
- Only source inspection, public reference read, javap, Python source/archive assertions, git diff --check and gradlew jar. These assertions inspect text/bytes, not Java behavior.
- NO Minecraft/client/server/tests/stress/simulations/benchmarks/GUI harness executed. No commit/push/publish.
- MC26.1.2 / Loader0.19.5 / API0.155.3+26.1.2 / Loom1.18.3 / Gradle9.7.1 / JDK25.
- Required NavigationClimb/NavigationDrop/NavigationInput ownership classes, existing Combat/panic/door classes, single mixin registration, metadata version and resource bytes inspected in JAR.
- Source contracts: exactAt/support shapes/transition sweeps; climb contact/exit/top-descent and deadlines; drop support/depth/phase deadlines; local bounded recovery; stale-plan drift; scoped camera/frame fences; physical cancel/no task retry; Macro/MouseHold ownership; style-independent sprint; single pre-action Pest detector tick.
- Previous policy contracts retained; obsolete text assertions updated only where implementation intentionally changed (climb controller replacement, exactAt replacing exactShapes, direct owned KeyMapping writes).
- Cumulative source ZIP bytes equal workspace; included JAR SHA256 equals delivered standalone JAR; archive integrity checked. Overlay or alternative original-baseline patch, never both.
- Runtime navigation acceptance delegated only to user: docs/navigation/GROUND-2.0-ALPHA10.md. Bounded failure possible; no navmesh/physics simulation/server acknowledgement guarantee.
