# Validation — 2.0.0-alpha.4

Scope: K2-003 package3a plus user-requested persistent panic handoff and in-mod keybind editor. Not stable2.0.0, not full HUD or complete safety suite.

- Platform unchanged: MC26.1.2/Fabric Loader0.19.5/API0.155.3+26.1.2/Loom1.18.3/Java25.
- Cached javap checked KeyMapping/Options/InputConstants/MouseButtonEvent/MouseButtonInfo/Minecraft APIs. First jar compilation succeeded; final metadata/resources compilation below.
- Static flow review: notice set before stop, no delayed dangerous action; pending cancels old reaction/reconnect/scheduler/hold/turn, holds no input lock, blocks new Combat/Macro/Pest starts and SmoothTurn/Mousemat/MouseHold GUI entry points. Explicit B/GUI ack releases retained crouch but does not resume. End emergency reset. Preview unchanged, history retained.
- B physical interception prevents shared editor binding double action while pending. Catalog contains all22 mod binds; vanilla mappings edited through setKey/resetMapping/options.save, keyboard+mouse, reset/unbind/conflict display. GUI capture input not a new JSON binding layer. Persistence/layout are NOT runtime-tested.
- Combat panic keeps master/profile, activation click queue drained, hold/toggle deliberate rearm. Context changes/menu/focus and manual movement cancel local session, no stale physical restoration across invalid context. Shift kept only while context valid, no terrain gates added.
- World baseline survives weapon=false; pending Special in combat context. Independent explicit-packet server rotation threshold20°/1–180; narrow expected Mousemat evaluated first; no screen-space own-aim detector. Existing correction thresholds/identity/actionbar filtering untouched.
- Aether pinned source RotationFailsafe/FailsafeManager read textually for context/grace/policy comparison. No external code/assets copied, no external JAR run.
- Prior body/cover/perception/queue/privacy/one-turn gates statically preserved. Required classes/resources/mixins and no stress/navTest in JAR checked during packaging.
- Minecraft/server/tests/stress/simulations/benchmarks/GUI harness NEVER launched. Only javap/source/static checks/gradlew jar/archive verification.
- User acceptance required for HUD readability/GUI/keyboard capture/persistence/crouch/focus/teleport/packet thresholds and earlier Combat regressions. No guarantee of hits/human likeness/server-rule compliance.

Build status: BUILD SUCCESSFUL; jar-only; gradlew jar only. Metadata version, required classes, single mixin registrations, whatsnew version, absence of stress/navTest entries and cumulative file list inspected statically. git diff --check clean. No runtime behavior was executed.
