# Validation 1.18.10-sway-alpha

API javap26.1.2: KeyMapping input state/matches, Options.toggleCrouch, ToggleKeyMapping true/false semantics, Entity.onGround/isPassenger; PathLevel/ShotPath API earlier verified. Static review: bounded jitter sampled targets/low-pass not accumulated yaw drift, human-only native quantization; sway owns3 keys only, random phases, support/hazard/collision checks, bounded origin, manual takeover and toggle cleanup. Queue releases do not re-anchor sway. Special movement preemption and pending-panic handling before snapshot. Detector activation/silence/disconnect extended to sway. No server changes or XP guarantee.

Только JDK25 gradlew --no-daemon jar. Packager checks current BUILD SUCCESSFUL, metadata/classes/one AimFrameMixin/SHA256. Minecraft/сервер/тесты/stress/симуляции НЕ запускались. Runtime sway/crouch/geometry/XP unverified, no human/anti-cheat guarantee.
