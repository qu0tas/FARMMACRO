package com.farmmacro.notify;

import com.farmmacro.config.ModConfig;
import com.farmmacro.panic.Detectors;
import com.farmmacro.panic.PanicShot;
import com.farmmacro.panic.ServerText;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;

/** Outgoing typed notices only. No inbound listener, remote commands, IRC, bot token or remote resume. */
public final class DiscordEvents {
    public static String panicText(Minecraft mc, String id, String reason) {
        var c = ModConfig.INSTANCE;
        String detail = c.webhookDetails
                ? ServerText.clean(reason)
                : "Требуется ручное управление; автоматическое продолжение запрещено";
        if (c.webhookDetails && mc.player != null) {
            detail = mask(detail, mc.player.getGameProfile().name());
            if (mc.getConnection() != null) {
                int n = 0;
                for (var info : mc.getConnection().getListedOnlinePlayers()) {
                    if (n++ >= 100) break;
                    detail = mask(detail, info.getProfile().name());
                }
            }
            detail = detail.replaceAll("[«\"].*?[»\"]", "[TEXT]")
                    .replaceAll(
                            "(?i)(?:[XYZ]\\s*[:=]\\s*)?-?[0-9]+(?:\\.[0-9]+)?[, ]+(-?[0-9]+(?:\\.[0-9]+)?)[, ]+(-?[0-9]+(?:\\.[0-9]+)?)",
                            "[COORDS]");
        }
        String kind = id == null ? "Общая" : Detectors.name(id);
        String server = c.webhookServer ? PanicShot.server(mc) : "скрыт";
        return PrivateText.outgoing(
                "⚠ Kaelix • " + kind + "\n" + detail + "\nВремя: " + PanicShot.humanTime() + "\nСервер: " + server);
    }
    /** 2.1.0: штатное окончание запуска (лимит времени, конец маршрута, Jacob's). Ручной стоп и паника сюда не идут. */
    public static void sessionEnd(String reason, long runMs, int loops) {
        var c = ModConfig.INSTANCE;
        if (!c.webhookEnabled || !c.webhookOnSessionEnd || !DiscordNotify.configured()) return;
        long min = Math.max(0, runMs) / 60000;
        String why = c.webhookDetails ? ServerText.clean(reason) : "штатное окончание";
        DiscordNotify.send(
                "⏹ Kaelix • запуск завершён\n" + why + "\nДлительность: " + min / 60 + " ч " + min % 60
                        + " мин • кругов: " + loops + "\nВремя: " + PanicShot.humanTime(),
                null,
                "сессия");
    }

    private static String mask(String s, String name) {
        return name == null || name.isBlank()
                ? s
                : s.replaceAll("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])", "[PLAYER]");
    }

    private DiscordEvents() {}
}
