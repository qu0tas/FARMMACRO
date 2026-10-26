# Validation 2.0.0-alpha.9

Build status: BUILD SUCCESSFUL; jar-only; gradlew jar only. Metadata version, required classes, single mixin registrations, whatsnew version, absence of stress/navTest entries and cumulative file list inspected statically. git diff --check clean. No runtime behavior was executed.

Scope: MC26.1.2/FabricLoader0.19.5/API0.155.3+26.1.2/Java25; K2-003 implementation completed, game acceptance NOT passed.
Only source/API/javap inspections, gradlew jar compilation and archive integrity checks. Minecraft/client/server/tests/stress/simulations/benchmarks/GUI harness NEVER launched. Prior source-only compilation13s passed twice; those had previous metadata version, final current-version build required.

- Expected new classes: CorrectionWindow/Sample, FarmingSignals, PlayerChatMessage; previous K001/K002/nav/keybind/handoff classes retained, required mixins unique.
- Correction budget bounded sum+count+time, zero movement normalization, independent rotation, sanitized UI values; world baseline/known weapon identity immediate event hooks preserved. No packet spoof/cancellation.
- BPS harvest-only / crop break evidence / full window+40 warmup+20confirmation; XP opt-in defaultfalse/setAll excluded/observed baseline40warmup+timeout+10confirmation; no actionbar chat/suspicion path. Unknown telemetry not treated as XP.
- Strict known social author, own/To/friends ignore; full-word matching. Nearby own module memory retained.
- Actual block shape contact3ticks, candidate bound512/TTL60s/reset and immediate dangerous near block separated. Growth/ownbreak exceptions, unavailable chunk skipped.
- Farm/pest guard before action, pending returns, GUI stop/cancel without auto-resume (disconnect lifecycle excepted), common panic preserves aim master, toggle-aware release and manual Sprint/Sway binding label, Shift valid-owner only.
- Source static contracts are archive/source assertions, not execution of mod classes. No stress/test source edits added for this iteration. No fixture/config secrets packaged. SHA256 compared between standalone and release member; every cumulative source file byte compared.
- Stage coverage/manual checklist/known limitations: docs/navigation/SAFETY-2.0-ALPHA9.md. Existing emergency farm teleport vs Rewarp and same-input empty harvest transitions explicitly documented. No banproof/human indistinguishability/XP/server lag/staff verification claim.
- AetherGPL3 files read only for policy comparison; no code/assets copied/adapted. MIT base unchanged. No push/commit/release publication; KeyAuth deferred.

Full acceptance pending user: Shift platform/pauses/toggle/hold/manual/B/End/context, corrections/window/rotation/teleport/weapons/ownslot, chatHP/mana/ability/self/friends/nearby/unknown, harvestBPS/XP pauses/nav/pest/lag/hiddenHUD, real vs transient block, GUI/Disconnect lifecycle, no auto actions after pending. No gameplay assertion is inferred from build success.

Alpha.9 final review: pending-паника сохраняется после disconnect до явного подтверждения; исходная причина не заменяется вторичной disconnect-тревогой. Reconnect.wouldReconnect/tick и Scheduler.tick имеют прямой pending guard. GUI-policy не вмешивается в connect/login screens без player/world; configured nonpanic reconnect не отменяется собственным ConnectScreen. Это статическая проверка, не сетевой игровой тест.
