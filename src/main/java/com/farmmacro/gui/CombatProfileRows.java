package com.farmmacro.gui;
import com.farmmacro.config.*;
import com.farmmacro.combat.AimAssist;
import java.util.*;
public final class CombatProfileRows {
    public static void build(List<Rows.Row> rows,Runnable save,Runnable rebuild){
        var c=ModConfig.INSTANCE;CombatProfiles.ensure(c);
        rows.add(new Rows.Section("Профили Combat — тестовая alpha").key("combat.profiles"));
        rows.add(new Rows.Note("Отдельная вкладка-профиль «Зиалоты» и свои профили других мобов. Ниже редактируется ТОЛЬКО выбранный профиль. Паника и клавиша общие для Combat; смена профиля сбрасывает захват/Toggle.",Ui.SUB));
        rows.add(new Rows.Selector("Активный профиль", "Каждый хранит свой фильтр, выстрелы, разрешение осмотра и A/D. Камера и её скорость общие в Motion. Действие оружия пока только Glacial Scythe — также по другим разрешённым мобам.",
            ()->c.combatProfiles.stream().map(p->new Rows.Choice(p.id,p.name)).toList(),()->c.combatProfileId,id->{
                AimAssist.INSTANCE.configure(c.aimAssistEnabled);CombatProfiles.select(c,id);save.run();rebuild.run();}));
        rows.add(new Rows.Buttons(new Rows.Btn("Зиалоты",Rows.Style.SECONDARY,()->{
            AimAssist.INSTANCE.configure(c.aimAssistEnabled);CombatProfiles.select(c,CombatProfiles.ZEALOTS);save.run();rebuild.run();}),
            new Rows.Btn("Другие мобы",Rows.Style.SECONDARY,()->{
                AimAssist.INSTANCE.configure(c.aimAssistEnabled);
                var other=c.combatProfiles.stream().filter(p->!CombatProfiles.ZEALOTS.equals(p.id)).findFirst().orElse(null);
                if(other==null)CombatProfiles.duplicate(c,true);else CombatProfiles.select(c,other.id);
                save.run();rebuild.run();})));
        rows.add(new Rows.Buttons(new Rows.Btn("Новый из зиалотов",Rows.Style.SECONDARY,()->{
                AimAssist.INSTANCE.configure(c.aimAssistEnabled);CombatProfiles.duplicate(c,true);save.run();rebuild.run();}),
            new Rows.Btn("Камера — в Motion",Rows.Style.SECONDARY,()->{
                var mc=net.minecraft.client.Minecraft.getInstance();if(mc.player!=null)mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Открой Kaelix Motion: камера общая для всех профилей"));})));
        rows.add(new Rows.TextField("Имя профиля", "До40 символов; настройки сохраняются автоматически",()->CombatProfiles.active(c).name,v->{
            if(v.isBlank())return false;CombatProfiles.active(c).name=v.strip();save.run();rebuild.run();return true;},null,40,0));
        rows.add(new Rows.Buttons(new Rows.Btn("Копия текущего",Rows.Style.SECONDARY,()->{
            AimAssist.INSTANCE.configure(c.aimAssistEnabled);CombatProfiles.duplicate(c,false);save.run();rebuild.run();}),
            new Rows.Btn("Удалить свой профиль",Rows.Style.SECONDARY,()->{
                AimAssist.INSTANCE.configure(c.aimAssistEnabled);CombatProfiles.remove(c);save.run();rebuild.run();})));
        rows.add(new Rows.Buttons(new Rows.Btn("Сброс выбранного",Rows.Style.SECONDARY,()->{
            AimAssist.INSTANCE.configure(c.aimAssistEnabled);CombatProfiles.resetSelected(c);save.run();rebuild.run();}),
            new Rows.Btn("Экспорт профиля",Rows.Style.SECONDARY,()->{
                var mc=net.minecraft.client.Minecraft.getInstance();mc.keyboardHandler.setClipboard(CombatProfiles.exportSelected(c));
                if(mc.player!=null)mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Combat-профиль скопирован"));}),
            new Rows.Btn("Импорт нового",Rows.Style.SECONDARY,()->{
                var mc=net.minecraft.client.Minecraft.getInstance();AimAssist.INSTANCE.configure(c.aimAssistEnabled);
                String error=CombatProfiles.importSelected(c,mc.keyboardHandler.getClipboard());
                if(mc.player!=null)mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(error==null?"Combat-профиль импортирован":error));
                if(error==null){save.run();rebuild.run();}
            })));
        rows.add(new Rows.Note(()->CombatProfiles.summary(c),Ui.SUB));
        rows.add(new Rows.Note("Экспорт KAELIX-COMBAT1: содержит только один профиль. Импорт создаёт новый, не меняет клавиши, панику, разрешение aim и лоадауты. Сброс меняет только выбранный: автоогонь/A-D выключаются; своих мобов выбери заново.",Ui.SUB));
        rows.add(new Rows.Note(()->"Редактируется: "+CombatProfiles.active(c).name+" • "+c.combatProfiles.size()+"/24 профилей. Новая копия из зиалотов требует выбрать имена мобов в каталоге; пустой список никого не атакует.",Ui.SUB));
    }
    private CombatProfileRows(){}
}
