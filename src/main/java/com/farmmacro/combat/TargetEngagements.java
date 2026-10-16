package com.farmmacro.combat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Mob;
import java.util.*;
/** Bounded local observation ledger; death observations are NOT attributed kills. */
public final class TargetEngagements {
    private static final int LIMIT=512;
    private static final class Entry {final int number;long until;int attempts;Entry(int n){number=n;}}
    private final Map<Mob,Entry> entries=new LinkedHashMap<>();
    private int next=1,observedDeaths,disappeared;
    public void clear(){entries.clear();next=1;observedDeaths=disappeared=0;}
    public void prune(ClientLevel world){
        var it=entries.entrySet().iterator();
        while(it.hasNext()){
            var row=it.next();Mob m=row.getKey();Entry e=row.getValue();
            if(m.level()!=world||m.isRemoved()){if(e.attempts>0)disappeared++;it.remove();}
            else if(!m.isAlive()||m.isDeadOrDying()){if(e.attempts>0)observedDeaths++;it.remove();}
        }
    }
    private Entry entry(Mob mob){
        Entry e=entries.get(mob);if(e!=null)return e;
        if(entries.size()>=LIMIT){
            long now=System.nanoTime();Mob evict=null;
            for(var row:entries.entrySet())if(now>=row.getValue().until){evict=row.getKey();break;}
            // With a >=100ms attempt interval and <=3s wait there are at most30 normal pending requests.
            if(evict==null)evict=entries.keySet().iterator().next();
            entries.remove(evict); // Capacity eviction is not counted as death/disappearance.
        }
        e=new Entry(next++);entries.put(mob,e);return e;
    }
    public int number(Mob mob){return entry(mob).number;}
    public int knownNumber(Mob mob){var e=entries.get(mob);return e==null?0:e.number;}
    public boolean waiting(Mob mob,long now){var e=entries.get(mob);return e!=null&&now<e.until;}
    public boolean returning(Mob mob,long now){var e=entries.get(mob);return e!=null&&e.attempts>0&&now>=e.until;}
    public void sent(Mob mob,long now,int waitMs){var e=entry(mob);e.attempts++;e.until=now+(long)Math.clamp(waitMs,300,3000)*1_000_000L;}
    public int pending(long now){return (int)entries.values().stream().filter(e->now<e.until).count();}
    public String summary(long now){
        long retry=entries.values().stream().filter(e->e.attempts>0&&now>=e.until).count();
        return "ждут "+pending(now)+", доступны повторно "+retry+", наблюдалась смерть "+observedDeaths+", исчезли без подтверждения "+disappeared;
    }
}
