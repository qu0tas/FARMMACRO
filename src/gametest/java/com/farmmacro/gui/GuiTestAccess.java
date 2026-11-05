package com.farmmacro.gui;

import java.util.List;
import net.minecraft.client.gui.screens.Screen;

/** Доступ game-тестов к каталогу модулей (тот же пакет, в релизный JAR не попадает). */
public final class GuiTestAccess {
    private GuiTestAccess() {}

    public static List<String> moduleTitles() {
        return ModuleCatalog.ALL.stream().map(ModuleCatalog.Module::title).toList();
    }

    public static Screen module(int index) {
        return FarmMacroScreen.module(null, ModuleCatalog.ALL.get(index));
    }
}
