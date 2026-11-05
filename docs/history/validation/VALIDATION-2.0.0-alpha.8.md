# Validation — 2.0.0-alpha.8

Scope: complete K2-002 implementation; user runtime acceptance pending, next K2-003. No later stage closed.

- Platform MC26.1.2/Fabric Loader0.19.5/API0.155.3+26.1.2/Loom1.18.3/Java25 unchanged.
- Cached javap MultiPlayerGameMode.useItem=>InteractionResult, AABB.distanceToSqr(Vec3)/clip/contains, Entity.getId/getUUID verified. No new Fabric registrations/mixins.
- ESP scan same allowed/range, shared FOV helper and fresh filter/range in highlightReason; WAITING/FOV/SPECIAL colors, all loaded entity scan no separate radius. Label unique nearest compatible mob owner and stable tie, same-object confirmed cache, no ArmorStand targets.
- Shared ScytheShotGeometry: actual look, opening fresh, body/early/area corridor, first UP ground hit, conservative2.3 to AABB not center, clear impact-to-opening. Direct body before unknown extension; ShotPath known prefix collision only, never unknown MISS clear. Finite/bounded DDA resource guards. Optional inflate approximate, not measured projectile width or server damage guarantee.
- Pre-use context/panic/nav/Special gates, late readiness resets, interval on all attempts including FAIL. FAIL false keeps target; other client results only request dispatch. Existing fresh actual-view ray, not silent rotation.
- Ledger512 local records, nonpending-first eviction, deadline/retry, death observation separate from removed/world unknown; eviction not kill. Stable chain scoring/fallback/tie rules retained.
- Special bounded18 origin/time, preserve open living focus, after8 blocked ticks re-evaluate open-first, previous walker stopped on switch, removed-before-death classification, no target chase beyond bound. Manual/panic handoff unchanged.
- Bounded12 diagnostics/private-report exclusions and K2-001 camera source contracts preserved. Existing config/profile schema unchanged, no third-party code/assets copied, no KeyAuth.
- Wiki source checked fast mode: https://wiki.hypixel.net/Glacial_Scythe ground explosion3blocks /RIGHT CLICK/75mana. No projectile range/lifetime guessed;2.3 remains local conservative geometry.
- NEVER launched Minecraft/server/tests/stress/simulations/benchmarks/GUI harness. Source/API/static checks, gradlew jar and archives only. Runtime checklist TARGETS-2.0-ALPHA8.md user-only.

Build status: BUILD SUCCESSFUL; jar-only; gradlew jar only. Metadata version, required classes, single mixin registrations, whatsnew version, absence of stress/navTest entries and cumulative file list inspected statically. git diff --check clean. No runtime behavior was executed.
