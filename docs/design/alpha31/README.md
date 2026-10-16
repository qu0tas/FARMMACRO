# Alpha31 — offline design QA

These are design projections, NOT Minecraft screenshots or a pixel-exact rendering test. Courier is a surrogate for the runtime Minecraft font; controls/text metrics are approximate. HUD values are samples and inventory/model placeholders are explicitly labelled. No game, account or server was launched.

`layout-wide.json` and `layout-narrow.json` are exported by UiLayoutQuickCheck --snapshot from the actual pure MenuLayout. `render.py` uses these coordinates, current palette and local pixel icons/catalogue to regenerate five self-contained local-resource HTML pages. Screenshots are included under validation/ui-projections in the distribution ZIP.

## Inspected states
- home 960×540 GUI / 1536×912 capture: category labels, two-column cards, spacing and scroll crop clear; search/disclosure unsupported glyphs removed from source and projection, recaptured and inspected.
- detail, same dimensions: selected HUD module only, single column, no overlaps or clipping, toggles aligned.
- narrow 320×240 GUI / 640×528 capture: icon-only sidebar, one-column gallery, first card entirely accessible; intentional viewport scrolling.
- empty search, wide: zero result count and recovery message visible, no overlap.
- HUD 800×440 GUI / 1280×752 capture: Slate frame/tick separation, wrapped skill/pet rows, independent positions, 27+9 inventory outline. This does NOT test item/entity rendering.

Captures: no console errors, exceptions, failed resources or viewport overflow. Home/narrow clippedOverflow is the intentional scroll viewport. Human inspection accepted all five projections; real Minecraft font, scaling, click behavior and inventory/model rendering remain manual acceptance tasks.

## Solid default palette contrast
- Primary / window: 15.98:1
- Secondary / card: 8.32:1
- Dim / card: 5.43:1
- Primary button: 7.39:1
These ratios use opaque default menu surfaces only. Translucent HUD over arbitrary worlds and user-defined colors have no guaranteed contrast.

## Scope
Default Slate keeps positions, scales, IDs, per-widget overrides and custom assets. Legacy paired bands remain selectable. Stats panels are 2 GUI pixels taller, so very tightly packed personal layouts may need an F8 adjustment. No new gameplay control, telemetry parser, panic detector, camera or targeting behavior.
