# Loading screen art

`python tools/loading/generate_loading_art.py` — Pillow + numpy, Arial Bold or compatible Liberation/DejaVu font. Generates PNG wordmark/labels/digits/Foxy atlas. It uses existing Kaelix K/logo and user-supplied meme assets; rights are not asserted. Mod/Minecraft version for the footer comes from gradle.properties.

`python tools/loading/preview_loading.py` — Pillow; creates software compositions matching runtime geometry in /data/loading-previews. **Not Minecraft screenshots or client tests.**

Runtime uses bundled bitmap fonts/labels during initial reload (Minecraft Font can be unavailable). Any visual changes should be inspected at wide, 4:3, small and ultrawide sizes; callbacks/tick must stay vanilla.
