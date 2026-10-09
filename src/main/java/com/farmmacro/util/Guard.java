package com.farmmacro.util;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Защита от исключений в коде мода, который вызывает игра каждый кадр/тик.
 * Исключение логируется один раз (со стеком), затем отключается только этот элемент,
 * а не роняется вся игра.
 */
public final class Guard {
    private Guard() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/Guard");
    private static final Set<String> DISABLED = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    /** HUD-элемент, который после первого исключения выключается до перезапуска игры. */
    public static HudElement hud(String name, HudElement element) {
        return (g, delta) -> {
            if (DISABLED.contains(name)) return;
            try {
                element.extractRenderState(g, delta);
            } catch (Throwable t) {
                DISABLED.add(name);
                LOGGER.error("HUD «{}» упал и отключён до перезапуска игры", name, t);
            }
        };
    }

    /**
     * Выполнить шаг, не пропуская исключение наружу.
     * @return true, если шаг прошёл без ошибок
     */
    public static boolean run(String name, Runnable step) {
        try {
            step.run();
            return true;
        } catch (Throwable t) {
            if (LOGGED.add(name)) LOGGER.error("Ошибка в «{}» (дальше без стека)", name, t);
            else LOGGER.error("Ошибка в «{}»: {}", name, t.toString());
            return false;
        }
    }

    /** Как {@link #run}, но после первой ошибки шаг отключается до перезапуска игры. */
    public static boolean runOrDisable(String name, Runnable step) {
        if (DISABLED.contains(name)) return false;
        try {
            step.run();
            return true;
        } catch (Throwable t) {
            DISABLED.add(name);
            LOGGER.error("«{}» упал и отключён до перезапуска игры", name, t);
            return false;
        }
    }

    public static boolean isDisabled(String name) { return DISABLED.contains(name); }
}
