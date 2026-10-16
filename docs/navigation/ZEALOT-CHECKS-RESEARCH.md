# Zealot / teleport checks: what is and is not established

Requested research, not an implementation specification or proof of bypass.

- Taunahi's public edition3.0 page describes Hydra context-dependent responses, multiple detections and backup triggers for sudden interruptions. It does not document the exact phrase “staff checking via zealot teleport”, a reliable staff identity signal, or a public algorithm for that alert: https://www.taunahi.net/latest/v3.0
- Read the public JellyLabScripts/FarmHelper master source `failsafe/impl/TeleportFailsafe.java` (legacy S08PacketPlayerPosLook): intentional-teleport exclusions, recent-position history for lag-backs, displacement/layer thresholds and movement/lag estimates. It demonstrates contextual heuristic filtering, not a universal guaranteed bypass and not proof of Taunahi internals: https://github.com/JellyLabScripts/FarmHelper/blob/master/src/main/java/com/jelly/farmhelperv2/failsafe/impl/TeleportFailsafe.java
- Without the original video/timecode it is not established whether “zealot teleport” means movement of a mob or the player. Do not silently equate these. Current Kaelix focused guard observes the PLAYER position packet, not every entity teleport.
- Search returned several similarly named download/repository sites; none were installed/run or treated as verified official distributions. No external JAR executed and no source code copied.
- Current change: bounded combat-only tolerance of small PLAYER corrections, accepted by vanilla normally, plus local stop for larger events. No inferred staff identity, scripted chat response or automatic resumption. A tolerated small event could also be deliberate; a larger lag-back still stops. No sanction-safety claim.
