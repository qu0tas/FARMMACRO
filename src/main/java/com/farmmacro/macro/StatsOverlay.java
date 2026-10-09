package com.farmmacro.macro;

import com.farmmacro.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;

/**
 * HUD-оверлей статистики текущей сессии.
 * Показывает: длительность сессии и количество запусков макроса.
 * Отображается только когда макрос играет или уже запускался (sessionRuns > 0).
 */
public class StatsOverlay {

    private static final int COLOR_BG     = 0xAA000000;
    private static final int COLOR_BORDER = 0xFF2A2A2A;
    private static final int COLOR_TITLE  = 0xFFFFFFFF;
    private static final int COLOR_VALUE  = 0xFF44FF55;
    private static final int COLOR_LABEL  = 0xFFAAAAAA;

    private static final int MARGIN_RIGHT = 8;
    private static final int MARGIN_TOP   = 8;
    private static final int PANEL_W      = 120;
    private static final int PANEL_H      = 44;

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("farmmacro", "stats"), (ctx, tickDelta) -> {
            if (!ModConfig.INSTANCE.statsHudEnabled) return;

            MacroManager mgr = MacroManager.INSTANCE;
            if (mgr.getSessionRuns() == 0) return;

            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;
            // Не показываем поверх GUI
            if (client.screen != null) return;

            int sw = client.getWindow().getGuiScaledWidth();
            int px = sw - PANEL_W - MARGIN_RIGHT;
            int py = MARGIN_TOP;

            // Фон
            ctx.fill(px, py, px + PANEL_W, py + PANEL_H, COLOR_BG);
            // Рамка
            ctx.fill(px,               py,               px + PANEL_W, py + 1,          COLOR_BORDER);
            ctx.fill(px,               py + PANEL_H - 1, px + PANEL_W, py + PANEL_H,    COLOR_BORDER);
            ctx.fill(px,               py,               px + 1,        py + PANEL_H,   COLOR_BORDER);
            ctx.fill(px + PANEL_W - 1, py,               px + PANEL_W,  py + PANEL_H,  COLOR_BORDER);

            // Заголовок
            ctx.text(client.font, "§f[FM] §7Статистика", px + 5, py + 4, COLOR_TITLE, false);

            // Длительность сессии
            long elapsed = mgr.getSessionStartMs() > 0
                    ? System.currentTimeMillis() - mgr.getSessionStartMs() : 0;
            String timeStr = formatDuration(elapsed);
            ctx.text(client.font, "§7Сессия: §a" + timeStr, px + 5, py + 16, COLOR_LABEL, false);

            // Количество запусков
            ctx.text(client.font, "§7Запусков: §a" + mgr.getSessionRuns(), px + 5, py + 28, COLOR_LABEL, false);
        });
    }

    private static String formatDuration(long ms) {
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) return String.format("%dч %02dм %02dс", h, m, s);
        if (m > 0) return String.format("%dм %02dс", m, s);
        return String.format("%dс", s);
    }
}
