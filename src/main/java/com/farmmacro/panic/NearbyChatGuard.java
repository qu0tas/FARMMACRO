package com.farmmacro.panic;

import com.farmmacro.combat.AimAssist;
import com.farmmacro.combat.CombatSway;
import com.farmmacro.combat.SpecialZealotHunt;
import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.pest.PestHunter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.regex.Pattern;

/** Main-thread nearby-author memory. An unknown author is never guessed from proximity alone. */
public final class NearbyChatGuard {
    public static final NearbyChatGuard INSTANCE=new NearbyChatGuard();
    private record Seen(String name,long time,double distance) {}
    private final Map<UUID,Seen> farmRecent=new HashMap<>(),combatRecent=new HashMap<>();
    private ClientLevel world;private Vec3 lastPlayer;
    private static final Pattern HEADER=Pattern.compile("^(?:\\[[^\\]]{1,24}\\]\\s*)*([A-Za-z0-9_]{1,16})\\s*:\\s*(.*)$",Pattern.DOTALL);
    private static final Pattern GREETING=Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])(?:hi|hello|hey|yo|привет|прив|здравствуй|здравствуйте|ты\\s+тут|ты\\s+здесь|ответь|are\\s+you\\s+there|you\\s+there|are\\s+you\\s+here)(?![\\p{L}\\p{N}_])");
    private static boolean watching(){return CombatPanic.enabled()&&CombatPanic.chat();}
    private void clear(){farmRecent.clear();combatRecent.clear();}
    private static void trim(Map<UUID,Seen> map,long now,int seconds){
        map.values().removeIf(v->now-v.time()>(long)seconds*1_000_000_000L);
        if(map.size()>256){var oldest=map.entrySet().stream().sorted(Comparator.comparingLong(e->e.getValue().time()))
            .limit(map.size()-256).map(Map.Entry::getKey).toList();oldest.forEach(map::remove);}
    }
    public void sample(Minecraft mc){
        if(mc.level!=world){world=mc.level;clear();lastPlayer=null;}
        if(mc.player==null||mc.level==null){clear();lastPlayer=null;return;}
        Vec3 position=mc.player.position();
        if(lastPlayer!=null&&lastPlayer.distanceTo(position)>8)clear();
        lastPlayer=position;
        var c=ModConfig.INSTANCE;var p=c.combatPanic;long now=System.nanoTime();
        boolean farm=c.panicEnabled&&c.detectNearbyChat,combat=p.enabled&&p.nearbyChat;
        trim(farmRecent,now,c.nearbyChatMemorySec);trim(combatRecent,now,p.chatMemorySec);
        if(!farm)farmRecent.clear();if(!combat)combatRecent.clear();
        if(!farm&&!combat)return;
        for(var other:mc.level.players()){
            if(other==mc.player||other.getUUID().equals(mc.player.getUUID()))continue;
            String name=other.getGameProfile().name();if(!PlayerRules.validName(name))continue;
            double distance=other.distanceTo(mc.player);Seen seen=new Seen(name,now,distance);
            if(farm&&distance<=c.nearbyChatRadius)farmRecent.put(other.getUUID(),seen);
            if(combat&&distance<=p.chatRadius)combatRecent.put(other.getUUID(),seen);
        }
        trim(farmRecent,now,c.nearbyChatMemorySec);trim(combatRecent,now,p.chatMemorySec);
    }
    public String match(Minecraft mc,String text,UUID sender,String senderName,boolean overlay){
        if(overlay||text==null||mc.player==null||!watching())return null;
        sample(mc);boolean combat=CombatPanic.context();
        Map<UUID,Seen> recent=combat?combatRecent:farmRecent;
        String body=ServerText.clean(text);
        if(body.length()>2048)body=body.substring(0,2048);
        var header=HEADER.matcher(body);Seen author=null;
        if(sender!=null){
            if(sender.equals(mc.player.getUUID()))return null;
            author=recent.get(sender); // A known UUID is authoritative; no name fallback to a different entity.
            if(author!=null&&header.matches()&&header.group(1).equalsIgnoreCase(author.name()))body=header.group(2);
        }else{
            if(!header.matches())return null;
            String name=header.group(1);body=header.group(2);
            if(name.equalsIgnoreCase(mc.player.getGameProfile().name()))return null;
            for(Seen seen:recent.values())if(seen.name().equalsIgnoreCase(name)){
                if(author!=null)return null; // Ambiguous duplicate names: do not guess.
                author=seen;
            }
        }
        if(author==null)return null;
        var c=ModConfig.INSTANCE;
        var settings=c.combatPanic;
        boolean friend=combat?settings.ignoreFriends&&PlayerRules.isFriend(PlayerRules.parseNames(settings.friends),author.name())
            :c.friendsIgnoreChat&&PanicDetector.INSTANCE.isFriend(author.name());
        if(friend)return null;
        boolean greeting=GREETING.matcher(body).find();
        String keyword=ChatMatch.hit(body,mc.player.getGameProfile().name(),combat?settings.mentionName:c.chatMentionName,combat?settings.keywords:c.chatKeywords);
        if(!greeting&&keyword==null)return null;
        String quote=body.length()>100?body.substring(0,99)+"…":body;
        return String.format(Locale.ROOT,"Обращение от %s (был рядом %.1f бл, %.1f с назад): «%s» — управление тебе",
            author.name(),author.distance(),(System.nanoTime()-author.time())/1e9,quote);
    }
    private NearbyChatGuard(){}
}
