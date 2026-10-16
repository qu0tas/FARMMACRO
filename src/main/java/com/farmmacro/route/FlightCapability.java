package com.farmmacro.route;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Read-only capability. Survival is valid; configuration never grants permission or changes abilities. */
public final class FlightCapability {
    private static LocalPlayer player;
    private static Object world;
    private static Boolean granted,serverFlying;
    private static long observed;
    public static boolean allowed(LocalPlayer p){return p!=null&&(p.getAbilities().mayfly||p.getAbilities().flying);}
    public static void packet(Minecraft mc,boolean permission,boolean flying){
        player=mc.player;world=mc.level;granted=permission;serverFlying=flying;observed=System.nanoTime();
    }
    public static String describe(Minecraft mc){
        if(mc.player==null||mc.level==null)return "Полёт: нет игрока/мира";
        var a=mc.player.getAbilities();
        String packet=player==mc.player&&world==mc.level&&System.nanoTime()-observed<30_000_000_000L
            ?"; пакет сервера: разрешение="+granted+", flying="+serverFlying:"; свежий пакет способностей не наблюдался";
        return "Полёт: mayfly="+a.mayfly+", flying="+a.flying+packet
            +(!allowed(mc.player)?". Сервер не разрешил полёт; настройка не выдаёт его.":". Доступен также в Survival.");
    }
    private FlightCapability(){}
}
