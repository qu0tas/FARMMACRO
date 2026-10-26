# Static validation / compilation only

Target: Minecraft26.1.2, Fabric Loader0.19.5 / API0.155.3+26.1.2, Java25, alpha not release.

- Build task: jar only; no Minecraft, server, tests, stress, simulations, datagen or benchmarks.
- Intermediate compilation before shot timing succeeded. Final version must succeed separately before packaging.
- MC camera methods verified via javap against cached26.1.2: Entity.turn(double,double), getYRot/getXRot.
- Static review: bounded32-observation history, dt caps, owner handoff/clear, real-ray fire gates, opt-in master/use, profile allowlist, deep copies, max24, legacy migration, capture/save/export and activation reset.
- New files CombatCamera, CombatProfile, CombatProfiles, CombatProfileRows expected in JAR; one AimFrameMixin; no stress classes.
- User still needs runtime check: render/FPS, camera reaction and idle transitions, normal and early/late shot feel, profile switch and restart persistence, mob name filters, manual/panic stops.
- No guarantee of human likeness, hits, XP or avoiding server sanctions. No source copied from another mod; no push.

Final build: BUILD SUCCESSFUL, jar only. Archive metadata/classes/mixin count and cumulative file list inspected statically; git diff --check clean. No game or tests executed.
