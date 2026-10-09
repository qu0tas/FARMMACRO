package com.farmmacro.macro;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;

/**
 * Рисует HUD-маркер сохранённой позиции остановки макроса.
 *
 * Показывает стрелку + расстояние в метрах в углу экрана.
 * Стрелка указывает горизонтальное направление к сохранённой точке
 * относительно взгляда игрока.
 * Виден только когда есть сохранённая позиция и макрос не играет.
 */
public class SavedPositionRenderer {

    // Цвета
    private static final int COLOR_BG     = 0xAA000000; // полупрозрачный чёрный фон
    private static final int COLOR_GREEN  = 0xFF44FF55; // зелёный текст
    private static final int COLOR_ARROW  = 0xFF44FF55;
    private static final int COLOR_TITLE  = 0xFFFFFFFF;

    // Положение панели (отступ от правого-верхнего угла)
    private static final int MARGIN_RIGHT = 8;
    private static final int MARGIN_TOP   = 60;
    private static final int PANEL_W      = 110;
    private static final int PANEL_H      = 36;

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("farmmacro", "saved_position"), (drawContext, tickDelta) -> {
            MacroManager mgr = MacroManager.INSTANCE;
            if (!mgr.hasSavedPosition() || mgr.isPlaying()) return;

            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            double px = client.player.getX();
            double py = client.player.getY();
            double pz = client.player.getZ();
            double tx = mgr.getSavedX();
            double ty = mgr.getSavedY();
            double tz = mgr.getSavedZ();

            double dist = Math.sqrt((px - tx) * (px - tx) + (pz - tz) * (pz - tz));

            // Угол между взглядом игрока и направлением к точке (горизонтально)
            double targetYaw   = Math.toDegrees(Math.atan2(tz - pz, tx - px)) - 90.0;
            double playerYaw   = client.player.getYRot();
            double relAngle    = (targetYaw - playerYaw + 360.0) % 360.0;
            // relAngle: 0=вперёд, 90=право, 180=назад, 270=лево

            int sw = client.getWindow().getGuiScaledWidth();
            int panelX = sw - PANEL_W - MARGIN_RIGHT;
            int panelY = MARGIN_TOP;

            // Пульсация: мигание рамки
            float pulse = (float)(0.6 + 0.4 * Math.sin(System.currentTimeMillis() / 350.0));
            int pulseAlpha = (int)(pulse * 255);
            int borderColor = (pulseAlpha << 24) | 0x44FF55;

            // Фон панели
            drawContext.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, COLOR_BG);
            // Рамка (пульсирующая)
            drawContext.fill(panelX,               panelY,                panelX + PANEL_W, panelY + 1,          borderColor);
            drawContext.fill(panelX,               panelY + PANEL_H - 1,  panelX + PANEL_W, panelY + PANEL_H,    borderColor);
            drawContext.fill(panelX,               panelY,                panelX + 1,        panelY + PANEL_H,   borderColor);
            drawContext.fill(panelX + PANEL_W - 1, panelY,                panelX + PANEL_W,  panelY + PANEL_H,   borderColor);

            // Заголовок
            drawContext.text(client.font,
                    "§f[FM] §aSaved pos", panelX + 5, panelY + 4, COLOR_TITLE, false);

            // Дистанция
            String distStr = String.format("%.1fm", dist);
            drawContext.text(client.font,
                    distStr, panelX + 5, panelY + 15, COLOR_GREEN, false);

            // Стрелка направления (ASCII-арт символы)
            String arrow = getArrow(relAngle);
            int arrowX = panelX + PANEL_W - 28;
            drawContext.text(client.font,
                    arrow, arrowX, panelY + 10, COLOR_ARROW, false);

            // Подсказка клавиши
            drawContext.text(client.font,
                    "§7[O] resume", panelX + 5, panelY + 26, 0xFFAAAAAA, false);
        });
    }

    /**
     * Возвращает стрелку-символ по относительному углу (0=вперёд).
     * Разбиваем 360° на 8 секторов по 45°.
     */
    private static String getArrow(double angle) {
        // Сдвигаем на 22.5° чтобы секторы были симметричны
        int sector = (int)((angle + 22.5) / 45.0) % 8;
        switch (sector) {
            case 0: return "↑";   // вперёд
            case 1: return "↗";
            case 2: return "→";   // вправо
            case 3: return "↘";
            case 4: return "↓";   // назад
            case 5: return "↙";
            case 6: return "←";   // влево
            case 7: return "↖";
            default: return "?";
        }
    }
}

