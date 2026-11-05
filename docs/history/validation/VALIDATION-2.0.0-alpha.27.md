# Validation alpha27 — attended milestone A

MC26.1.2 / Java25 / Fabric0.19.5 / API0.155.3+26.1.2.

## Scope
Build and explicitly bounded `quickCheck` only. No routeStress, soundStress, configSweep, client/server/Hypixel or large parameter simulations. Gradle compiles stress-source helpers to run the tiny check runner; it does not execute the large suites.

`AttendedQuickCheck`: finite/manual-start boundaries, XYZ translation, mirrored firstpoint/offset/drop direction/yaw/relativefarmkeys/lookBlock, sourceimmutability, doublemirror, displacement bounds, old-config migration, explicitreturnoptin persistence, inert scheduled callback/reconnect and existing module-retirement smokecheck. These are pure/small checks, not actual terrain, GUI clicks, camera rendering or server behavior.

Manual-start position rechecked after preparation using captured source/saved-stop coordinates. Sessions/reconnect implementations are inert and their schedules/hooks unregistered. Static packaging checks verify18cards/5categories/10tabs/18registeredkeys/18HUDblocks, removed scheduling controls/keys, no obsolete reconnectscreenclass after clean build.

## Pending user validation / implementation
Full separate Aether-humanize, all-source camera arbitration/refactor, corner/floor-specific presets/defaults, Freecam, newminimalGUI, ownHUD and CombatXP/pet-next-level are NOT implemented in this milestone. See docs/ROADMAP-2.0-ATTENDED.md. Current Aether TrackingRotationStrategy remains only prior partial adaptation.

User must inspect copied points/floors/drop safety and camera before playback; commands in copied routes are not rewritten. Route copying is not auto-navigation or a collision validator. Compatibility parsers/config classes remain, not a full physical purge.

No claims of gameplay acceptance, ideal thresholds, staff classification or ban safety. Alpha, not stable. FullGPL corresponding source accompanies binary. No push/commit/publish.
