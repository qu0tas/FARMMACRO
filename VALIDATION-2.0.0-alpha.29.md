# alpha29 — Motion B2 validation

Target: Minecraft26.1.2 official Mojang / Fabric0.19.5 / API0.155.3+26.1.2 / Java25 / Loom1.18.3.

## Scope
Aether ROUTE CameraBinding start/intermediate/final point: exclusive turn ticket, stopped input while waiting, observed-angle acknowledgment and bounded context/anchor/deadline checks. Cancellation does not equal success. Kaelix and recording snapshot unchanged; recording now correctly declares configured camera ownership. Navigation/other preset requests cannot take the binding lease. Existing mouse/key hooks receive scoped cancellation callbacks; no new mixin registrations or packet hooks.
New Motion switch/speed, 120deg/s default20–360; defaults/presets/import preserve personal opt-out. No Combat/HUD/flight-physics/detector rewriting. B2 still not full Motion refactor or stable2.0.0.

## Executed
- Initial build quickCheck PASS29s.
- Final `clean build quickCheck`: authoritative log is validation/clean-build-quickCheck.log inside sourceZIP.
- BindingQuickCheck: pure table for acquisition/conflicting/stale owner, no early ack, retention until angle ack, cancel never resumes even at target, wrong-angle/context/NaN/anchor/floor/deadline rejection, timeout sizing, old defaults/copy/Gson/opt-out preservation. No Minecraft instance/world.
- Prior MotionQuickCheck retained (<=276pure frame calls; no FPS/seed sweep), AttendedQuickCheck and ModuleRetirementCheck retained.
- Static package validation: scoped source changes vs alpha28; unchanged Combat/HUD/panic/flight code and mixin registration; source provenance hashes unchanged; runtime JAR version/license/current notice/new classes/deleted classes/no testclasses; original assets except whatsnew unchanged; source ZIP CRC and full source/JAR identity.

## Not executed / not proven
No Minecraft/client/server/Hypixel, no real mixin injection/GUI/render/physics/key-toggle acceptance, no flight/route simulations, live targets, packet/transaction exercises, benchmarks or routeStress/configSweep/soundStress. Pure gate tests do not prove the runtime adapters; user gameplay testing required. No claim of human-likeness, staff detection or protection from bans. No push/commit/publish.

## Manual acceptance checklist
- Enable Aether and smooth bindings; test whole-route and point bindings on a short supported ground route. Compare Kaelix snapshot mode.
- Check starting, mid-route, final point, pause and resume: no walking/hand/slot writes while waiting, correct angle before continuation, final point not skipped.
- Test manual mouse/movement/click/scroll and End/P: local stop, no automatic restart; verify especially custom bindings/toggle crouch/sprint.
- Menu/focus loss/unsafe support/position shift or interrupted turn must stop, not continue at an unfinished angle. Do not use smooth route bindings in flight/water/ladders: B2 refuses that context.
- Existing Combat and recording behavior should remain unchanged except preset conflict messages/ownership declaration.

## Delivery
JAR and full GPL corresponding source/build scripts/attribution/license/reference originals. Source ZIP: cleanfolder recommended, otherwise explicitly delete10paths in REMOVED-FILES.txt. Stable only after requested remaining roadmap work and user tests.
