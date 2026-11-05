# Validation — 2.0.0-alpha.6

Scope: K2-004/4b door execution, navigation mouse ownership and per-climb-waypoint progress baseline. Not full navigation/flight rewrite or stable2.0.0.

- Platform unchanged MC26.1.2/Fabric Loader0.19.5/API0.155.3+26.1.2/Loom1.18.3/Java25.
- Cached javap verified MultiPlayerGameMode.useItemOn(LocalPlayer,InteractionHand,BlockHitResult), Player.blockInteractionRange, ClipContext constructors, BlockState.getShape, LivingEntity.swing, InteractionResult.Success.swingSource/CLIENT.
- Static only: immediate path collision corridor bounded3 blocks/32 samples; loaded chunk gate; same block + canonical door lower half; selection AABB points with fresh OUTLINE visibility, stable chosen point; actual player look clip and player-reach recheck before interaction.
- No generic useItem/key-use hold/slot switch; vanilla useItemOn only, CLIENT success swing. No guarantee of server plugin behavior.
- Three attempts per door/run (ledger cap64, no reset on short predicted opening),10tick retry wait including final attempt,100tick episode /20tick hidden bounds;3 open observations debounce not server ack. New explicit run resets ledger; replan does not. Blocks/replacement/failure release owner same tick.
- NavigationInput attack/use direct owned state, no MouseHold OR/delay gate inheritance; MouseHold suppressed while navigation busy; only button gates reset on acquire/release, slot timing/model/settings unchanged.
- ReturnCore climb best and deadline reset on new target index. Old physical ladder handling remains conservative. Prior alpha5 source contracts retained.
- No Minecraft/server/tests/stress/simulations/benchmarks/GUI harness launched. Only code/API/static review, gradlew jar and archive inspection. No Aether code/assets copied. KeyAuth deferred.
- Runtime acceptance for doors/gates/corners/denied interactions/lag/mouse ownership/climb and prior Combat/ground navigation remains user-only.

Build status: BUILD SUCCESSFUL; jar-only; gradlew jar only. Metadata version, required classes, single mixin registrations, whatsnew version, absence of stress/navTest entries and cumulative file list inspected statically. git diff --check clean. No runtime behavior was executed.
