package com.farmmacro.gui;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.macro.MacroManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Edits the registered vanilla mappings, not a second parallel binding configuration. */
public final class KeybindRows {
    private static String label(KeyMapping key){return Component.translatable(key.getName()).getString();}
    private static String conflicts(KeyMapping key){
        if(key.isUnbound())return "Не назначена";
        var mc=Minecraft.getInstance();
        var matches=java.util.Arrays.stream(mc.options.keyMappings)
            .filter(other->other!=key&&key.same(other)).toList();
        if(matches.isEmpty())return "Конфликтов с зарегистрированными клавишами не найдено";
        return "Совпадает: "+matches.stream().limit(4).map(KeybindRows::label)
            .collect(java.util.stream.Collectors.joining(", "))+(matches.size()>4?" …":"");
    }
    static void assign(KeyMapping key,com.mojang.blaze3d.platform.InputConstants.Key value){
        key.setDown(false);while(key.consumeClick()){}
        key.setKey(value);KeyMapping.resetMapping();
        // Persist through Minecraft's normal options.txt; controls menu reflects the same mapping.
        Minecraft.getInstance().options.save();
    }
    public static void build(List<Rows.Row> rows,Screen parent){
        rows.add(new Rows.Section("Клавиши Kaelix").key("kaelix.keybinds"));
        rows.add(new Rows.Note("Все бинды мода: нажми «Назначить», затем клавишу или кнопку мыши. Esc — отмена, Delete/Backspace — снять бинд. Сохраняются сразу в options.txt, как в «Управлении» Minecraft; профили Combat их не меняют.",Ui.SUB));
        rows.add(new Rows.Note("B по умолчанию совпадает с редактором маршрута. Во время паники передача управления имеет приоритет и не открывает редактор. Другие совпадения показаны ниже; они разрешены, но могут запускать несколько действий.",Ui.WARN));
        for(var key:FarmMacroMod.bindings()){
            rows.add(new Rows.Note(()->label(key)+" · "+MacroManager.keyName(key),Ui.TEXT));
            rows.add(new Rows.Buttons(
                new Rows.Btn("Назначить",Rows.Style.SECONDARY,()->Minecraft.getInstance().setScreen(new KeybindCaptureScreen(parent,key))),
                new Rows.Btn("По умолчанию",Rows.Style.SECONDARY,()->assign(key,key.getDefaultKey())),
                new Rows.Btn("Снять",Rows.Style.SECONDARY,()->assign(key,com.mojang.blaze3d.platform.InputConstants.UNKNOWN))));
            rows.add(new Rows.Note(()->conflicts(key),Ui.SUB));
        }
    }
    private KeybindRows(){}
}