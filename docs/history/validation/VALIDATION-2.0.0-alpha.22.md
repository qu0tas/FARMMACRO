# Validation · 2.0.0-alpha.22 · K2-016

Scope: requested GUI/HUD fixes; NOT whole K015 and NOT stable/RC.

- Restore from delivered cumulativealpha21, backup preserved outside repo.
- Source/API inspection: Screen background/extraction pipeline, GuiGraphicsExtractor item/texture blit, native widgets.
- First bash gradlew --no-daemon jar SUCCESS40s. Final bash gradlew --no-daemon jar SUCCESS12s. Static source/archive inspection against delivered alpha21: previous Java unchanged outside7UI/config files; all24module section allowlists nonempty and refer to known keys, scoped sidebar/scroll/Tab restrictions inspected;20existing HUDIDs/14legacytabs/28keys preserved; previous compiled mod classes retained,2new helpers and original384x256glyph atlas present; old resources except whatsnew and mixin config byte-identical. Metadata/CRC/git diff --check inspected. No runtime/GUI validation.
- Original24icon atlas opened for asset inspection only; not a rendered game/GUI preview. No UI visual acceptance claimed.
- Explicit24card allowlists and scoped navigation; full settings remains independent.
- Read-only presentation context, pending-warning bypass, independent visual-block error isolation; no controllers/input/trading/safety algorithms modified.
- Existing20HUDIDs/28keys preserved; new settings are visual booleans/colors/opacity/editor positions, no new network or finance authorization.
- User checklist and outstandingK015 features: docs/K016-GUI-HUD-ACCEPTANCE.md.

NO Minecraft/server/test/stress/simulation/benchmark/GUI harness/game render/network exercise. No git commit/push/auth/publish/stable claim.
