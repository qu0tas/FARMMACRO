# Kaelix 2.0.1-alpha.2 — removal verification

User request: remove all Experimentation Table assistance, not just cursor input.
Removed: runtime engine/input/settings/policy, GUIcard/settings, keymapping+translations, tick/frame/input/menu/panic/reset hooks, packet observers/overlay/accessors, active table docs, table test dependency. Old JSON field and options binding inert/unregistered. No helper-only replacement.
Preserved exactly from delivered alpha.1: PlayerGazeGuard, GazeTimer, RecentTargetMemory, IdleLook, AimAssist, CombatPanic, CombatPanicSettings, CombatPanicRows, PanicHandoff; all unrelated source/runtime assets.

Only clean build quickCheck; NO Minecraft/client/server, live Hypixel, heavy stress, commit/push/publication. New CombatObservationQuickCheck covers prior gaze/memory fixtures, settings clamp and missing table config/runtime. ModuleRetirementCheck:18cards/5categories/10tab indices; table card forbidden.
Final packager verifies no table classes/key/mixin/config references in compiled artifact, source deletion manifest, full ZIP/JAR identity and unchanged Combat observation implementation. Build log included under validation/clean-build-quickCheck.log. Alpha after2.0.0, not a newly validated stable.

Final: clean build quickCheck PASS; 621full source files, 22cumulative removal paths;25unchanged baseline mixins. No experiment runtime/classes/keys/config hooks. All9Combat-observation/aim/panic source files preserved byte-identical to delivered alpha.1.
