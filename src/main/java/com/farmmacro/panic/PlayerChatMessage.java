package com.farmmacro.panic;

import net.minecraft.client.Minecraft;
import java.util.UUID;
import java.util.regex.Pattern;

/** Social text only. Unsigned server text requires a header AND a known player identity. */
record PlayerChatMessage(String author,String body) {
    private static final Pattern HEADER=Pattern.compile(
        "^(?:(?:Guild|Party|Co-op|Officer) > |From )?(?:\\[[^\\]]{1,24}\\]\\s*)*([A-Za-z0-9_]{1,16})\\s*:\\s*(.*)$",Pattern.DOTALL);
    static PlayerChatMessage resolve(Minecraft mc,String raw,UUID sender,String senderName){
        if(mc.player==null||raw==null)return null;
        String text=ServerText.clean(raw);if(text.length()>2048)text=text.substring(0,2048);
        if(text.startsWith("To "))return null;
        var header=HEADER.matcher(text);boolean formatted=header.matches();
        String name=senderName;
        if(sender!=null){
            if(sender.equals(mc.player.getUUID()))return null;
            var info=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(sender);
            if(info!=null)name=info.getProfile().name();
            if(!PlayerRules.validName(name))return null;
            if(formatted&&header.group(1).equalsIgnoreCase(name))text=header.group(2);
        }else{
            if(!formatted)return null;
            name=header.group(1);text=header.group(2);
            boolean known=mc.getConnection()!=null&&mc.getConnection().getPlayerInfo(name)!=null;
            if(!known&&mc.level!=null)for(var p:mc.level.players())
                if(p.getGameProfile().name().equalsIgnoreCase(name)){known=true;break;}
            if(!known)return null;
        }
        if(name.equalsIgnoreCase(mc.player.getGameProfile().name()))return null;
        return new PlayerChatMessage(name,text);
    }
}
