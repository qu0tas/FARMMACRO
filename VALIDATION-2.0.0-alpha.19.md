# Validation — 2.0.0-alpha.19 / K2-013

Scope: passive receipt cashflow + inventory/shard estimate + Farming progress; bounded observation buckets and optional local daily/lifetime; read-only task projection and expanded themed HUD/editor. All metrics have explicit coverage/unknown/source boundaries; not a universal farm-profit/profile-API claim.

Performed: source/API/javap review, public Hypixel item/skill factual resource retrieval, read-only external format/license reference, jar compilation, static source/archive/resource inspection. Initial jar BUILD SUCCESSFUL13s; review jar BUILD SUCCESSFUL12s. Only jar task graph used.

NOT performed: Minecraft/client/server/tests/stress/simulations/benchmarks/GUI harness/visual screenshots, user Discord requests, runtime Bazaar quote fetch exercises, actual server trades/XP/catches/reconnect. No code execution as a surrogate gameplay test. No commit/push/auth/publication. User gameplay/visual acceptance pending; not stable2.0.0.

Review scope:
- receipt telemetry is inserted after existing Stash sale and Visitor purchase receipt+inventory conditions, guarded, no financial retry or action added;
- inventory valuation never increments cashflow; unknown/special items and virtual Hunting Box not guessed;
- official NPC and61 cumulative XP thresholds; MIT level1 attribute→Bazaar correspondence with bundled license; no Aether/SkyHanni implementation code copied;
- fixed optional public endpoint, no account/key payload, one-in-flight/60s/body8MB/timeout/TTL/redirect/RetryAfter boundaries;
- XP absolute progress deltas, no repeated '+XP' summation, world/gap/backwards/unknown guards;
- optional schema/size-bounded atomic coalesced history, device-wide/local-date labels, generation locking on reset/disable; no executable restore;
- all13 legacy HUD blocks + all slot IDs preserved, six new cards, 14tabs/28bindings unchanged; exact number/placement labels inspected, no screenshot QA claim;
- draw paths read existing state without input/action/network/IO; HUD profile lookup does not mutate CombatProfiles;
- portable profiles exclude local quote/storage grants and preserve the current device's permissions.

Residual limits: source/compile cannot establish live packet ordering, target server menu/Tab/actionbar formats, full profit attribution or visual fit at arbitrary GUI scale. Original saved-slot placement retained; new compact left dashboard can be rearranged in F8. User imagery/colors/transparency affect contrast. Only own supported confirmed cash transactions and partially observed XP; unknown costs/earnings remain unknown. Pet price is manual annotation, max unknown without explicit evidence; Hunting Box virtual shards not valued. File IO/HTTP already in progress may finish after disabling, without applying revoked quote result; crash may lose last checkpoint.

Final bash gradlew --no-daemon jar: BUILD SUCCESSFUL in13s; only checkRemovedFiles/compileJava/processResources/classes/processIncludeJars/jar tasks. Static JAR/source/archive inspection: prior alpha18 classes preserved, metadata/resources/CRC,19blocks incl13legacy,14tabs/28bindings; official NPC2357 + XP61 + MIT shard324 data and bundled license, sale90/visitor39 unchanged; source boundaries and git diff --check inspected. No runtime/visual execution.
