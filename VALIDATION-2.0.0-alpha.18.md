# Validation — 2.0.0-alpha.18 / K2-012

Scope: one existing Scheduler, passive bounded SessionLedger, memory-only ResumeTicket, finite Reconnect, outbound Discord, portable privacy boundary and existing GUI/HUD integration. K2-013 redesign not implemented here.

Performed: source/API inspection; final `bash gradlew --no-daemon jar`; JAR/archive/source inspection. This document is NOT runtime evidence.

Explicitly NOT performed: Minecraft/client/server, tests, stress tasks, simulations, benchmarks, GUI harness, user webhook requests, Discord delivery/429 exercises, server reconnect/warp/financial operations. No Git commit/push/auth/publication. User gameplay acceptance pending; stable2.0.0 not approved.

Static review scope:
- early maintenance accounts/limits without connect/resume; existing late Scheduler tick after pending panic checks requests break resume;
- panic revokes reconnect authorization before service shutdown; JOIN/time/B alone do not clear latch;
- exact saved-source/config/hand/scope checks; source hashing only at capture/resume, not each tick;
- finite retries, command allowlist and stop-on-refusal; accumulated budgets survive service/offline/automatic resume;
- passive schema/size-bounded history without executable restore, bounded coalesced IO;
- bounded outbound queue, duplicate/global spacing, no HTTP retry/redirect, code/class-only failures, 429 header handling;
- separate masked plaintext token file, portable URL/secret scrub, local consent fields preserved on import;
- explicit two-flag PNG consent, bounded image read/signature/known local directory, no claim of pixel anonymization;
- old source/modules/resources kept in cumulative overlay; 14 tabs / 28 bindings unchanged.

Residual limitations: compilation cannot prove live menu/packet/event ordering, successful reconnect/resume/Discord delivery, thread timing or complete anonymization. Local raw game logs and opt-in screenshots remain private. In-flight HTTP/file IO may complete after disabling; last checkpoint can be lost on crash. Discord API/network cooldown state is memory-only. Unknown context fails closed rather than promising seamless resume. See docs/SESSIONS-DISCORD-2.0-ALPHA18.md.

Final bash gradlew --no-daemon jar: BUILD SUCCESSFUL in 14s; only checkRemovedFiles/compileJava/processResources/classes/processIncludeJars/jar tasks. Inspected JAR metadata/resources/CRC, prior alpha17 com/farmmacro classes preserved, 14 tabs/icons, 28 bindings, sale90/visitor39 official IDs/names, source boundaries and git diff --check. No runtime execution.
