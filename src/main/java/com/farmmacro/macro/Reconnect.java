package com.farmmacro.macro;

import com.farmmacro.config.ModConfig;
import com.farmmacro.panic.EventLog;
import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * v1.13.1 «Переподключение»: вылетел с сервера, пока играл макрос, — через N с зайти снова (до K попыток),
 * по желанию отправить команду (например «/warp garden») и продолжить с точки остановки, когда игрок снова рядом с ней.
 * Не переподключается: выход через меню паузы, таймер выхода, паника за последние 60 с (тебя могли кикнуть за макрос), одиночная игра.
 */
public final class Reconnect {
    private Reconnect() {}

    public enum State { OFF, WAIT, CONNECTING, JOINED }

    private static State state = State.OFF;
    private static ServerData server;
    private static int ticks, tries, joinedTicks;
    private static boolean resumeAfter, commandSent, intentional;

    public static State state() { return state; }
    public static int secondsLeft() { return state == State.WAIT ? (ticks + 19) / 20 : 0; }
    public static int tries() { return tries; }

    /** Вход на сервер (Fabric JOIN): запомнить адрес. */
    public static void onJoin(Minecraft mc) {
        ServerData d = mc.getCurrentServer();
        if (d != null) server = d;
        intentional = false;
    }

    /** Таймер выхода / выход вручную: не переподключаться. */
    public static void markIntentional() { intentional = true; cancel(); }

    public static void cancel() { state = State.OFF; tries = 0; }

    /** Вызовется ли переподключение после этого отключения (решение принимается в {@link #onDisconnect}). */
    public static boolean wouldReconnect(Minecraft mc, boolean wasPlaying) {
        ModConfig c = ModConfig.INSTANCE;
        long sincePanic = System.currentTimeMillis() - PanicDetector.INSTANCE.getLastPanicMs();
        return c.autoReconnect && wasPlaying && !intentional && server != null && !mc.isSingleplayer()
                && (PanicDetector.INSTANCE.getLastReason() == null || sincePanic > 60_000);
    }

    /** Отключение (Fabric DISCONNECT). wasPlaying — макрос играл (или только что играл). */
    public static void onDisconnect(Minecraft mc, boolean wasPlaying) {
        if (!wouldReconnect(mc, wasPlaying)) { if (state != State.CONNECTING) state = State.OFF; return; }
        resumeAfter = ModConfig.INSTANCE.reconnectResume;
        schedule("вылет с сервера");
    }

    private static void schedule(String why) {
        ModConfig c = ModConfig.INSTANCE;
        if (tries >= c.reconnectTries) {
            EventLog.log("RECONNECT", "попытки кончились (" + tries + ")");
            state = State.OFF; tries = 0;
            return;
        }
        tries++;
        ticks = c.reconnectDelaySec * 20;
        state = State.WAIT;
        EventLog.log("RECONNECT", why + ": попытка " + tries + "/" + c.reconnectTries + " через " + c.reconnectDelaySec + " с");
    }

    /** Каждый тик (и на экране отключения). */
    public static void tick(Minecraft mc) {
        ModConfig c = ModConfig.INSTANCE;
        if (state == State.OFF) return;
        if (!c.autoReconnect) { cancel(); return; }
        switch (state) {
            case WAIT -> {
                if (mc.level != null) { cancel(); return; }        // зашёл сам
                if (--ticks > 0) return;
                state = State.CONNECTING;
                ticks = 60 * 20;
                EventLog.log("RECONNECT", "подключаюсь к " + server.ip);
                ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc,
                        ServerAddress.parseString(server.ip), server, false, null);
            }
            case CONNECTING -> {
                if (mc.player != null && mc.level != null) {
                    state = State.JOINED; joinedTicks = 0; commandSent = false;
                    EventLog.log("RECONNECT", "зашёл");
                    return;
                }
                if (mc.screen instanceof DisconnectedScreen || --ticks <= 0) schedule("не удалось зайти");
            }
            case JOINED -> {
                if (mc.player == null) return;                      // снова вылетел — решит onDisconnect
                joinedTicks++;
                String cmd = c.reconnectCommand.strip();
                if (!commandSent && joinedTicks >= 60) {
                    commandSent = true;
                    if (!cmd.isEmpty() && mc.getConnection() != null) {
                        String s = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                        EventLog.log("RECONNECT", "команда /" + s);
                        mc.getConnection().sendCommand(s);
                    }
                }
                if (!resumeAfter) { if (joinedTicks > 60) { state = State.OFF; tries = 0; } return; }
                MacroManager m = MacroManager.INSTANCE;
                if (m.isActive()) { state = State.OFF; tries = 0; return; }
                // ждём, пока игрок окажется у точки остановки (после /warp — до 60 с)
                boolean near = m.hasSavedPosition() && mc.player.distanceToSqr(m.getSavedX(), m.getSavedY(), m.getSavedZ())
                        <= c.startPointWarnDistance * c.startPointWarnDistance;
                if (near && joinedTicks >= 100) {
                    EventLog.log("RECONNECT", "продолжаю с точки остановки");
                    m.resumeFromSaved(mc);
                    state = State.OFF; tries = 0;
                } else if (joinedTicks > 20 * 75) {
                    EventLog.log("RECONNECT", "не у точки остановки — не продолжаю");
                    m.msgPublic(mc, "§6Переподключился, но ты не у точки остановки — макрос не продолжен");
                    state = State.OFF; tries = 0;
                }
            }
            default -> {}
        }
    }
}
