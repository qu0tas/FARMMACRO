# Validation — 2.0.0-alpha.2

Scope: K2-001 partial camera1b + K2-002 exposed-body2a, not complete targeting/stable2.0.

- MC26.1.2/Fabric Loader0.19.5/API0.155.3+26.1.2/Java25 unchanged.
- Cached javap verified AABB public min/max/size/getCenter/clip/move/contains and Vec3 add/subtract/scale/distance methods.
- Static review of VisibleBody: max9 rays per search, interior samples, center preference and250ms exposed-part hold; checks valid actual Mob and loaded-chunk collision rays. Finite sampling can miss narrow apertures.
- Tick-local cache reused by candidate scan/selection, frame/firing use fresh body probes; Special transitioned to same geometry with8tick cover grace.
- All mandatory eye+center AND gates removed from aim/fire/Special, real current-view corridor and ground-body path checks remain. No firing from cached ESP alone.
- AutoFire late-window reset on manual use/cooldown/interval/context/occlusion. Item-use still opt-in, all master/key/weapon/perception/manual/panic gates retained.
- Human target-change pitch velocity reset only; yaw handoff retained. Idle steep prior pitch bounded. No claim of all runtime pitch causes fixed.
- Prior Shift/local-panic/correction configuration untouched; only one existing Entity.turn application remains.
- No Aether code/assets copied this iteration, no license reclassification, future GPL-compliant reuse possible.
- No Minecraft/server/tests/stress/simulations/benchmarks/GUI harness executed. Only javap, static review, gradlew jar and archive/source checks.
- User acceptance pending: partial-body shots/near walls/far targets/Special, handoff/pitch, performance, existing manual/Shift/panic behavior.

Build status: BUILD SUCCESSFUL in11s; gradlew jar only. Metadata version, required classes, single mixin registrations, whatsnew version, absence of stress/navTest entries and cumulative file list inspected statically. git diff --check clean. No runtime behavior was executed.
