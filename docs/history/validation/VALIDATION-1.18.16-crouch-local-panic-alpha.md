# Validation — compilation/static only

Target Minecraft26.1.2/Fabric/Java25. Alpha, no full release before user testing.

- Verified cached MC API with javap: KeyboardInput.tick(), ClientInput.keyPresses public, Input(boolean x7) accessors, LocalPlayer.input public.
- New mixin only touches the matching local KeyboardInput, reasserts owned Shift and preserves other input bits. Supports hold/toggle physical takeover.
- Local panic review: master unchanged, Toggle cleared/Hold blocked until release, target/frame/fire/special canceled, A/D stopped. Paused Shift retains owner only within same valid world/context; manual/focus/GUI/End/modeoff releases. No automatic camera/use/route resume.
- Teleport tolerance only combat; small horizontal/vertical + angular bounds, throttled factual log, no staff/ping inference. No vanilla packet/ack cancellation; larger events/respawn remain guarded.
- External reference file was read as text only, not compiled/copied. Taunahi exact alert remains unverified without video/timecode.
- Build only `gradlew jar`; do not run game/server/tests/stress/simulations/benchmarks. Final build log and archive must be checked before delivery.
- Runtime crouch/edge behavior, mixin ordering with other mods, local rearm and threshold tuning remain for user. No guarantee against falling, false positives, missed small checks or sanctions.

Final build: BUILD SUCCESSFUL, jar only. Archive metadata/classes/mixin count and cumulative file list inspected statically; git diff --check clean. No game or tests executed.
