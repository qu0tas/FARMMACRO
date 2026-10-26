# Kaelix 2.0.0-alpha.15 · K2-009 validation

Status: implementation complete; USER GAME ACCEPTANCE PENDING.

- Only source inspection, javap/API, gradlew jar and ZIP/JAR inspection permitted.
- No Minecraft/client/server/tests/stress/simulations/benchmarks/GUI harness executed.
- First and second gradlew jar: BUILD SUCCESSFUL. Final gradlew jar: BUILD SUCCESSFUL. JAR metadata/resources/classes and archive CRC inspected. All prior alpha14 com/farmmacro classes remain in the new JAR. 12 tab/icon entries and 26 bindings statically counted; 45 profile fields enumerated. git diff --check clean. No runtime execution.
- 45 allowlisted per-profile fields; shared activation/key mode/diagnostics/version not imported. Missing/malformed fields use independent cached defaults; copy preserves current settings. Single-profile code strict type/size/allowlist limits, no permissions/panic/loadouts/binds.
- 12 UI tabs/icons; 26 key mappings, new loadout binding UNBOUND. Global settings nested under loadouts; all automation/actions default OFF. Mining/Combat/Foraging defaults, <=12 named copies.
- Exact current server/dimension scope; auto exact sidebar stable60 ticks, unique matching entry, >=10-second spacing, one attempt until exit/reentry; idle-only. No generic chat success or automation resume.
- Transaction <=20 seconds/400 ticks; steps <=4 seconds; own menu expected <=2 seconds; <=4 clicks / <=1 Wardrobe page. Packet copies separate from vanilla predicted menu. Cursor and screen/menu/container checks, no inventory dragging or purchases/THROW/setPos/fake abilities.
- Armor/PET active result must be observed from server, hand uses local identity + normal carried-slot packet with NO separate server ack claim. Partial equipment changes are not rolled back. Current Pets page only, known English Wardrobe/Pets/lore only; native /loadout not implemented by this adapter.
- End/panic/manual key/button/scroll/world/focus/foreign screen/server displacement/external hand cancels. Own hand selection calls expectSlot; no general detector disablement.
- Aether source read-only reference, no copied/adapted source/assets.

User acceptance list: docs/COMBAT-LOADOUTS-2.0-ALPHA15.md. Stable 2.0.0 requires user approval; next K2-010.
