package com.farmmacro;

import com.farmmacro.config.ModConfig;
import com.farmmacro.gui.FarmMacroScreen;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.macro.SavedPositionRenderer;
import com.farmmacro.panic.PanicDetector;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FarmMacroMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("farmmacro");

    /** Своя категория в меню управления (1.21.9+: категория — объект, а не строка). */
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("farmmacro", "general"));

    private static KeyMapping makeKey(String id, int defaultCode) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping(id, InputConstants.Type.KEYSYM, defaultCode, CATEGORY));
    }

    public static KeyMapping keyRecord;
    public static KeyMapping keyPlay;
    public static KeyMapping keyClear;
    public static KeyMapping keyOpenGui;
    public static KeyMapping keyResume;
    public static KeyMapping keyResetPos;

    @Override
    public void onInitializeClient() {
        LOGGER.info("FarmMacro loaded!");

        ModConfig.load();
        ModConfig cfg = ModConfig.INSTANCE;

        // Регистрируем оверлей красного экрана
        com.farmmacro.panic.PanicOverlayRenderer.register();

        // Регистрируем маркер сохранённой позиции
        SavedPositionRenderer.register();
        com.farmmacro.macro.StatsOverlay.register();


        keyRecord  = makeKey("key.farmmacro.record",   cfg.keyRecord);
        keyPlay    = makeKey("key.farmmacro.play",      cfg.keyPlay);
        keyClear   = makeKey("key.farmmacro.clear",     cfg.keyClear);
        keyOpenGui = makeKey("key.farmmacro.gui",       cfg.keyOpenGui);
        // O = GLFW_KEY_O = 79
        keyResume  = makeKey("key.farmmacro.resume",    79);
        // End = GLFW_KEY_END = 269 (Delete уже занят под «Очистить запись»)
        keyResetPos = makeKey("key.farmmacro.resetpos", 269);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (keyRecord.consumeClick())  MacroManager.INSTANCE.toggleRecording(client);
            while (keyPlay.consumeClick()) {
                    MacroManager.INSTANCE.togglePlayback(client);
            }
            while (keyClear.consumeClick())   MacroManager.INSTANCE.clearRecording(client);
            while (keyResume.consumeClick())  MacroManager.INSTANCE.resumeFromSaved(client);
            while (keyResetPos.consumeClick()) {
                MacroManager.INSTANCE.clearSavedPosition();
                if (client.player != null)
                    client.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§7[FM] Сохранённая позиция очищена"));
            }
            while (keyOpenGui.consumeClick()) {
                client.setScreen(new FarmMacroScreen(client.screen));
            }

            MacroManager.INSTANCE.tickRecord(client);

            // Сначала проверяем панику — ДО применения кадра макроса.
            if (MacroManager.INSTANCE.isPlaying()) {
                PanicDetector.INSTANCE.tick(client);
            } else {
                // Макрос не играет — только затухание эффектов (красный экран, звук)
                PanicDetector.INSTANCE.tickEffectsOnly(client);
            }

            MacroManager.INSTANCE.tickPlayback(client);
        });
    }

    private static void msg(net.minecraft.client.Minecraft client, String text) {
        if (client.player != null)
            client.player.sendOverlayMessage(Component.literal("[FM] " + text));
    }
}
