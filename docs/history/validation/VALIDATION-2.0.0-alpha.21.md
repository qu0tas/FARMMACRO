# Validation · 2.0.0-alpha.21

K2-015 PART1 only: menu/cards and HUD editor/inventory. NOT whole K015, NOT RC/stable.

- Recovered exact previously delivered alpha20 source overlay before work; alpha19 workspace was backed up. Alpha20 source/resource preservation is checked against that archive, not a guessed implementation.
- MC26.1.2 javap signatures inspected: GuiGraphicsExtractor.item/itemDecorations, Inventory.getItem/getSelectedSlot, InventoryScreen.extractEntityInInventoryFollowsMouse, Button/AbstractButton rendering, Screen extraction/input.
- Inventory preview helper bytecode operates on extracted render state; no LocalPlayer angle mutation in own code.
- Own UI:23cards/7categories; same FarmMacroScreen type /14legacytabs/28keys. No controller methods in catalogue/card/inventory; navigation retains original Rows and controller safety checks.
- Existing19HUDIDs preserved; inventory20thOFF. Existing slots not migrated/reset automatically. Reset-all explicit2click, per-slot reset documented incl own colors; editor geometry clamped by existing HudLayout / sanitize.
- Compile1 jar SUCCESS38s. Final bash gradlew --no-daemon jar SUCCESS12s. Static source/archive inspection: previous alpha20 Java outside5existing UI/config files byte-identical; previous resources except whatsnew byte-identical, mixins unchanged, prior compiled com/farmmacro classes preserved, new4UI classes present, metadata/resources/CRC inspected,23cards/7categories/14legacytabs/28keys/20HUDIDs. git diff --check clean. No gameplay/visual execution.

No Minecraft/client/server/test/stress/simulation/benchmark/GUI harness/render preview. No in-game/visual acceptance claimed. No manual financial/network exercise, no commit/push/auth/release publication. User checklist: docs/GUI-HUD-2.0-ALPHA21.md.
