# Alpha32 validation scope

Only JDK25 clean build and bounded offline quickCheck; no Minecraft/client/server or heavy simulations. The final log accompanies the binary/source archive.

New OwnershipQuickCheck covers owner priority, alarm/freecam denial, background permission/cursor rules, Freecam normalized diagonal/radius/time cap, one-shot legacy camera migration, Combat profile isolation, deep-copy and finite config bounds. Existing motion/binding/telemetry/UI/retirement quick checks remain.

26.1.2 Camera.alignWithEntity(float), protected setPosition/setRotation and detached field verified with javap before implementing the camera adapter. Injection applies before Camera.update FOV/frustum preparation. Compilation does not verify live mixin application or visual interpolation.

One global Motion settings source and final rotation sink, shared movement permission; not a universal rewrite of pathfinding/physics. Macro start proximity rules unchanged. Global Kaelix/Aether selection is authoritative; Combat legacy camera aliases no longer applied by profiles. Backend-specific task semantics documented in README.

Freecam is local render-only, no fake body or camera coordinates sent as player movement. KeyboardInput movement becomes EMPTY, attack/use gates refuse actions while active. Physical gravity/inertia and server displacement remain possible. Camera/frame/key lifecycle and perspective restoration require manual testing. No auto-resume.

Background permission is explicit/default ON. Active tasks temporarily own pauseOnLostFocus and restore it afterwards. Toggle Combat is required for unattended key release; Hold still releases. Real screens/pause/death/world/alarm guards remain; OS may throttle minimized rendering. PiP/unfocus/desktop cursor behavior requires manual testing on user's platform.

No delayed stop or staff-response disguise added. Existing panic stop remains immediate. HUD/inventory/XP renderers/assets preserved. 18 module cards, 19 registered bindings (new Freecam unbound), 18 HUD slots. Full GPL corresponding source with pinned upstream originals, build scripts and identical binary; retired paths manifest remains cumulative 10. Alpha, not stable or ban-safety certification.
