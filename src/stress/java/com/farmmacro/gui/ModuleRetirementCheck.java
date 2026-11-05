package com.farmmacro.gui;

import com.farmmacro.config.ModConfig;
import com.google.gson.Gson;
import java.util.Locale;

/** Retired entrypoints stay inert even when stale settings ask to activate them. No client. */
public final class ModuleRetirementCheck {
    public static void main() {
        if (ModuleCatalog.ALL.size() != 19 || ModuleCatalog.CATEGORIES.length != 5)
            throw new IllegalStateException("retired module catalogue size");
        for (var module : ModuleCatalog.ALL) {
            if (module.tab() < 0 || module.tab() >= 10 || module.category() < 1 || module.category() >= 5)
                throw new IllegalStateException("module index " + module);
            String s = module.title().toLowerCase(Locale.ROOT);
            if (s.contains("эксперимент")
                    || s.contains("pest")
                    || s.contains("визитор")
                    || s.contains("ловушк")
                    || s.contains("лассо")
                    || s.contains("bazaar")
                    || s.contains("лоадаут")) throw new IllegalStateException("retired card " + s);
        }
        // 2.0.2: retired modules are deleted, not stubbed. A stale config with their keys must still load.
        String legacy =
                "{\"pestHuntEnabled\":true,\"pests\":{\"autoStart\":true},\"visitors\":{\"enabled\":true,\"auto\":true},"
                        + "\"stashSales\":{\"enabled\":true},\"loadouts\":{\"enabled\":true},\"autoReconnect\":true,\"schedEnabled\":true,\"reactEnabled\":true,"
                        + "\"telemetry\":{\"bazaar\":true,\"priceMode\":\"BAZAAR\"}}";
        var c = new Gson().fromJson(legacy, ModConfig.class);
        c.sanitize();
        if (c.telemetry.bazaar || !c.telemetry.priceMode.equals("NPC"))
            throw new IllegalStateException("retired services resurrected");
        for (String gone : new String[] {
            "pest.PestHunter",
            "pest.PestTraps",
            "visitor.Visitors",
            "market.StashSales",
            "market.MarketTrip",
            "loadout.GlobalLoadouts",
            "macro.Scheduler",
            "macro.Reconnect",
            "panic.HumanReaction",
            "session.SessionLedger"
        }) {
            try {
                Class.forName("com.farmmacro." + gone, false, ModuleRetirementCheck.class.getClassLoader());
                throw new IllegalStateException("retired class still shipped: " + gone);
            } catch (ClassNotFoundException expected) {
            }
        }
        System.out.println(
                "ModuleRetirementCheck: OK (19 cards/5 categories/10 valid tab indices; legacy config keys ignored; retired Pests/Visitors/Bazaar/Loadouts/Scheduler/Reconnect classes absent)");
    }

    private ModuleRetirementCheck() {}
}
