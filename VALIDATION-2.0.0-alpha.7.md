# Validation — 2.0.0-alpha.7

Scope: finish K2-001 implementation only; runtime acceptance pending. Prior nav/Combat features retained; no K2-002 or later stage declared complete.

- Platform unchanged MC26.1.2/Fabric Loader0.19.5/API0.155.3+26.1.2/Loom1.18.3/Java25; cached javap Entity.turn(double,double), getXRot/getYRot, MC isWindowActive/isPaused verified.
- CombatCamera: all finite guards; one apply caller; actual post-turn expected view recording; external-view/frame-gap resets; reaction/error use delayed perception,80ms settling hysteresis, separate speed multipliers/Special floor and smooth drift. Not runtime phase assertions.
- Human: aligned-only65% yaw carry, pitch reset, observations32 max, elapsed real vs integration50ms. One pending rare overshoot per retarget,400ms correction /150ms fade, capped size-aware yaw/pitch budget, normal overdamped tracking. No persistent underdamped setting.
- Aim: zero additional margins/jitter on selected partial opening, separate pitch bound, disabled idle vertical output; frame and final apply block NavigationInput/SmoothTurn. Own Special workflow retains narrow free exception but cannot turn while its navigator owns camera. Mouse manual-yield preference unchanged; movement/End stop guards retained.
- Idle: EMPTY/COVERED/WAITING/INACTIVE distinct; eligibility weapon/location before empty timer; neighbor/sector/return choices without forced alternation/modulo dwell, bounded origin/pitch, real elapsed fixation and >250ms reanchor. No actual runtime sequence executed.
- Existing camera config/profile keys/defaults retained; no new schema; prior fresh actual-view shot geometry preserved. No Aether code/assets copied, KeyAuth deferred.
- Only source/API/static review, gradlew jar and archive inspection. NEVER Minecraft/server/tests/stress/simulations/benchmarks/GUI harness. No performance or human-indistinguishability guarantee.
- User-only acceptance checklist CAMERA-2.0-ALPHA7.md; roadmap distinguishes implementation complete / acceptance not passed. Bugs reopen K2-001 before unrelated work.

Build status: BUILD SUCCESSFUL; jar-only; gradlew jar only. Metadata version, required classes, single mixin registrations, whatsnew version, absence of stress/navTest entries and cumulative file list inspected statically. git diff --check clean. No runtime behavior was executed.
