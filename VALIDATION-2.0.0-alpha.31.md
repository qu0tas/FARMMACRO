# Kaelix 2.0.0-alpha.31 — C2 GUI/HUD redesign

## Scope
Responsive 2/1-column module catalogue, persistent 5-category sidebar, single-column scoped module settings, shared geometry for paint/clicks/keyboard. Quiet blue default palette, retained authored pixel icons, own Slate HUD with thin outline/short side accent and stat divider. Legacy appearance selectable under HUD -> Стиль плашек. Config IDs/F8 positions/scales/per-widget overrides retained. No macro/camera/targeting/panic behavior change.

## Automated checks
Command: `JAVA_HOME=/data/jdk25 PATH=/data/jdk25/bin:$PATH bash gradlew --no-daemon clean build quickCheck`.
Final output in distribution `validation/clean-build-quickCheck.log`; package creation requires BUILD SUCCESSFUL and UiLayoutQuickCheck/TelemetryQuickCheck/BindingQuickCheck/MotionQuickCheck/AttendedQuickCheck PASS.
New tiny pure tests: 4 GUI sizes, window/content/category/close bounds, half-open hits, one/two columns, keyboard selection and clamped drag; KAELIX/LEGACY/null config style cases. Previous bounded pure checks retained. No client/server or heavy stress tasks.

## Visual checks
Five offline HTML projections inspected: home, selected module, narrow, empty search and sample HUD. See docs/design/alpha31/README.md for dimensions and limitations. These are not game screenshots; Minecraft font, live item/model rendering, input and gameplay remain untested here.

## Packaging gates
Compare main Java to alpha30 backup: only 7 existing UI/config/HUD files plus MenuLayout may change. Keep 18 module cards, 18 registered keys, 18 HUD slots, unchanged mixin registrations and preexisting assets except whatsnew. Preserve InventoryHud, model/layout logic, controller sources, XP parsers and removed feature tombstones. Verify pinned Aether originals, GPL notices, identical JAR in full-source ZIP, ZIP CRC and SHA256. No test classes in JAR. Cumulative removed-file list remains 10 paths.

## Manual acceptance / not stable
Open menu at normal/small GUI scales, search and navigate categories, enter each module and confirm scope, operate toggles and keyboard, drag window. Test F8 positions/scales, transparent/per-widget backgrounds, inventory36/model placement, active/inactive HUD visibility, skill/pet wrap, Legacy switch and custom themes. No reset of personal layout intended; stat panels +2px may need adjustment. Full Motion ownership and Freecam remain pending, not delivered here. No server/staff/ban-safety guarantee. No commit/push/publish.
