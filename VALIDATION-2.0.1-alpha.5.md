# Kaelix 2.0.1-alpha.5 — validation
Base: delivered alpha.4 full644file source. Fabric MC26.1.2/Java25.
Scope: no mandatory B/persistent handoff overlay/bind, soft-farm bounded delay default350–750ms, immediate Combat/emergencies/manual stop. Existing Aim reactivation latch retained (fresh Aim press, no B). Shift retention unchanged. No automatic restart/response. No changes to AutoFish/route geometry/start-from-here.
Checks: bounded PanicStopQuickCheck pure policy/deadline/config/no-B; existing quickCheck suites. Initial build PASS, final clean result below.
Not tested: Minecraft/client/server/liveHypixel/heavy simulations. Timing tick-granular; unknown/critical alarms immediate. No guarantee of ban safety or server permission.

Final: clean build quickCheck PASS,18s,8tasks. 647 full source files,25unchangedmixins. PanicStop/Fishing/StartHere checks PASS. Baseline allowed-change list and retained third_party/fishing/route geometry identity verified, ZIP/JAR CRC/resource/source identity/SHA checked. No game/heavytests.
