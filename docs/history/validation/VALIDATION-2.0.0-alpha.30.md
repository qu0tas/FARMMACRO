# alpha30 — C1 passive HUD data

## Scope
- 12 offline skill tables and independent absolute progress observers, AUTO/manual selector. Combat XP in Combat card, skill/pet context includes Combat and recent manual skill observations.
- Pet menu snapshot: active-item/visible-container checks retained; strict next-level lore, totalXP separate, explicitMAX, own60sTTL, invalidation on obvious pet/level/rarity/world/scope changes. Same-name/level pet instances cannot be distinguished via Tab; manualrefresh required.
- Existing skill/pet widget IDs/positions/customcolors/renderer retained; critical rows use existing width wrap preference. No full visual redesign.
- No changes to camera/route/combat/input/panic/physics/inventory renderer, existing hooks reused client-thread-only. No runtime HTTP or menu interaction.

## Lightweight verification
`JAVA_HOME=/data/jdk25 PATH=/data/jdk25/bin:$PATH bash gradlew --no-daemon clean build quickCheck`

- Compile/package, explicit pure `TelemetryQuickCheck`: official catalog structure; absolute gain vs repeated banners; independent skills; world/gap/decrease/repeatedfield/badformat/ambiguouscost;60s per-skill rate/remaining/ETA; Pets marker/ratio/negative/total-only/MAX/TTL/world.
- Previous MotionQuickCheck <=276 mathematical frames, BindingQuickCheck bounded table, attended/retirement checks only.
- Packager: unchanged mainJava outside seven allowed existing files; FarmHud label-only; stable18cards/18keys/18HUD; unchanged mixin registration, prior assets except whatsnew byte-identical; references checksums; no test classes in runtime; fullsource+JAR ZIP CRC/identity/SHA.
- Cumulative10 removed paths remain absent, listed in REMOVED-FILES.txt.

## Not tested / limitations
NO Minecraft/client/server/Hypixel, routeStress/configSweep/soundStress/large simulations, live entities or GUI rendering. Synthetic parser cases do not prove the current server menu/actionbar formats or visual sizing in your GUI scale. Unsupported data remains unknown. No promised staff/ban safety or stable status.

Per-skill rate is gained XP divided by connected valid observation intervals, not total playtime or an imputed true rate. Rounded actionbar values remain approximate; max table is not the player's unlock. Persistent daily/lifetime XP remains Farming-only. Pet snapshot is NOT live: manually open Pets to refresh; Tab must not renew old XP.

Full Motion ownership, own minimal GUI/HUD style and Freecam remain pending. See roadmap. Final PASS log bundled at validation/clean-build-quickCheck.log; release/SHA256SUMS.txt covers identical delivered JAR.
