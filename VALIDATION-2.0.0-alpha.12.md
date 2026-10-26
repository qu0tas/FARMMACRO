# Validation 2.0.0-alpha.12 / K2-006

Build status: BUILD SUCCESSFUL; jar-only; gradlew jar only. Metadata version, required classes, single mixin registrations, whatsnew version, absence of stress/navTest entries and cumulative file list inspected statically. git diff --check clean. No runtime behavior was executed.

- Runtime acceptance NOT performed. No Minecraft/client/server/tests/stress/simulations/benchmarks/GUI harness.
- Source/API/static inspection only. New APIs reuse existing verified official26.1.2 calls: SmoothTurn/Mousemat/PathLevel/GroundProbe, KeyMapping/toggle options, LocalPlayer rotations/position/abilities, connection.sendCommand.
- New farm classes, optional model defaults/copies/sanitation, common execution/manual cancel hooks/GUI, switch-pause calculation (no model mutation), exact version/resources and cumulative archive list must be inspected.
- Report: docs/navigation/FARMING-2.0-ALPHA12.md; roadmap K2-006 code completed, user acceptance outstanding; nextK007.
- No copied/adapted GPL code or assets, no commit/push/publish. Stable2.0.0 requires user approval.
