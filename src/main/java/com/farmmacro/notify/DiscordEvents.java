package com.farmmacro.notify;
import com.farmmacro.config.ModConfig;
import com.farmmacro.panic.Detectors;
import com.farmmacro.panic.ServerText;
import net.minecraft.client.Minecraft;
import java.util.regex.Pattern;
/** Outgoing typed notices only. No inbound listener, remote commands, IRC, bot token or remote resume. */
public final class DiscordEvents {
 public static void session(String code){if(code.equals("PANIC"))return;if(ModConfig.INSTANCE.webhookSessions)DiscordNotify.send("Kaelix • "+label(code)+"\n"+com.farmmacro.macro.Scheduler.status(),null,"сессия");}
 public static void reconnect(String code){if(ModConfig.INSTANCE.webhookSessions)DiscordNotify.send("Kaelix • "+label(code)+"\n"+com.farmmacro.macro.Reconnect.status(),null,"переподключение");}
 private static String label(String c){return switch(c){case "START"->"Сессия начата";case "BREAK"->"Перерыв";case "RESUMED"->"Продолжен прежний источник";case "WAIT"->"Ожидание переподключения";case "JOINED","JOINED_MANUAL"->"Соединение восстановлено";case "EXHAUSTED"->"Попытки подключения исчерпаны";case "LIMIT","WALL_LIMIT","ACTIVE_LIMIT","CYCLE_LIMIT"->"Лимит сессии: автоматика остановлена";case "PANIC"->"Паника: управление игроку";default->"Сессия остановлена ("+c+")";};}
 public static String panicText(Minecraft mc,String id,String reason){var c=ModConfig.INSTANCE;
  String detail=c.webhookDetails?ServerText.clean(reason):"Требуется ручное управление; автоматическое продолжение запрещено";
  if(c.webhookDetails&&mc.player!=null){detail=mask(detail,mc.player.getGameProfile().name());if(mc.getConnection()!=null){int n=0;for(var info:mc.getConnection().getListedOnlinePlayers()){if(n++>=100)break;detail=mask(detail,info.getProfile().name());}}detail=detail.replaceAll("[«\"].*?[»\"]","[TEXT]").replaceAll("(?i)(?:[XYZ]\\s*[:=]\\s*)?-?[0-9]+(?:\\.[0-9]+)?[, ]+(-?[0-9]+(?:\\.[0-9]+)?)[, ]+(-?[0-9]+(?:\\.[0-9]+)?)","[COORDS]");}
  String kind=id==null?"Общая":Detectors.name(id);String server=c.webhookServer?com.farmmacro.panic.PanicShot.server(mc):"скрыт";
  return PrivateText.outgoing("⚠ Kaelix • "+kind+"\n"+detail+"\nВремя: "+com.farmmacro.panic.PanicShot.humanTime()+"\nСервер: "+server);
 }
 private static String mask(String s,String name){return name==null||name.isBlank()?s:s.replaceAll("(?i)(?<![A-Za-z0-9_])"+Pattern.quote(name)+"(?![A-Za-z0-9_])","[PLAYER]");}
 private DiscordEvents(){}
}
