package com.farmmacro.macro;

import com.farmmacro.config.ModConfig;
import com.farmmacro.gui.Ui;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.chat.Component;

/**
 * v1.14: на экране отключения — «Переподключение через N с (попытка k/n)» и кнопки «Сейчас» / «Отмена».
 * Своё решение (идея отсчёта — Hybridash/AutoReconnect, MIT; код свой). Логика — в {@link Reconnect}.
 */
public final class ReconnectScreenHook {
    private ReconnectScreenHook() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof DisconnectedScreen)) return;
            int bw = 70, y = 30, x = w / 2 - bw - 2;
            Button now = Button.builder(Component.literal("Сейчас"), b -> Reconnect.connectNow()).bounds(x, y, bw, 20).build();
            Button cancel = Button.builder(Component.literal("Отмена"), b -> Reconnect.cancelByUser()).bounds(x + bw + 4, y, bw, 20).build();
            Screens.getWidgets(screen).add(now);
            Screens.getWidgets(screen).add(cancel);
            Runnable vis = () -> {
                boolean on = Reconnect.state() == Reconnect.State.WAIT;
                now.visible = on; cancel.visible = on;
                now.active = on; cancel.active = on;
            };
            vis.run();
            ScreenEvents.afterTick(screen).register(s -> vis.run());
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> {
                if (Reconnect.state() != Reconnect.State.WAIT) return;
                String t = "Переподключение через " + Reconnect.secondsLeft() + " с (попытка " + Reconnect.tries() + "/"
                        + ModConfig.INSTANCE.reconnectTries + ")";
                Ui.textCentered(g, Screens.getFont(s), t, w / 2, 16, Ui.WARN);
            });
        });
    }
}
