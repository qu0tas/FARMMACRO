# Alpha33 — camera-only manual Combat

## Verification
JDK25 compile + clean build quickCheck only. No Minecraft/client/server, routeStress, configSweep, rendering or gameplay tests. Final build log included in source archive.

New ManualAimQuickCheck is a tiny pure table: Slayer boss name canonicalization with decorated health labels, miniboss rejection, exact/custom target filters, compatible real-Mob body kinds, deterministic shortest-yaw center turn, 2D angular speed budget, no overshoot, dt clamp, invalid numeric rejection, config defaults/bounds and isolation from automatic Combat profile selection. Existing small checks remain.

Static review: manual tick/frame branch returns before auto-combat workflows. It never ticks AutoScytheFire, CombatSway, IdleLook or Special hunt and only sends rotation through MotionController. No direct attack/use/slot/movement/command packet calls were added. Manual WASD callback bypasses only Aim handoff in explicit camera-only mode; alarm/context/End handoffs remain. Mouse is not cancelled: frame tracking continually corrects toward center until user disables Hold/Toggle.

## Semantics and limits
Mode defaults OFF; existing automatic Combat/profile/camera backend settings stay intact. Manual task uses deterministic DirectAimTurn independently of Kaelix/Aether backend, but speed is the existing single Motion aimTurnSpeed. Global manual mode/target selection are deliberately excluded from automatic profile snapshots; no silent mode reactivation by switching profiles.

Only loaded real Mob hitboxes; armor stands provide labels, never become targets. Recognized boss labels require compatible body species. No player targets, boss ownership, boss tier, invulnerability or actual server damage confirmation. Unsupported disguised body kinds, missing server labels or label ambiguity can prevent or misidentify targets; test in game. World labels are not trusted as authority. Existing recognition geometry is not a proof of whose boss it is.

Acquire by crosshair angular error plus distance, then keep a valid living lock without FOV reselection while running around it. Max128 center rays per acquisition scan, at most every2ticks; retained target center revalidated each frame/tick. Closed center pauses all rotation, retains lock up to8ticks then allows reselection. No aiming through walls, prediction or added projectile range.

Foreground+grab required for manual task; AltTab/menu/freecam/world/death/alarm/other camera owner stops it and requires deliberate reactivation. Automatic background mode from alpha32 is unchanged. Teleport/weapon safety settings retained; manual weapon swaps can trip the enabled weapon-change guard. End retains old full-stop behavior, including clearing master aim permission.

HUD task label updated for manual mode; layout, inventory, XP sources, 18module cards/19bindings/18HUD widgets and mixin registration unchanged. Existing automatic flags are ignored by this task, not deleted. Independently launched unrelated tasks are not silently reclassified as camera-only.

Alpha test build, not stable2.0.0 or ban-safety certification. Full GPL corresponding source/build scripts/licenses/pinned originals accompany identical JAR. No push/commit/publish.
