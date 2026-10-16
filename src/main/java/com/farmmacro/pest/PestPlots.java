package com.farmmacro.pest;

import com.farmmacro.panic.ServerText;
import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.regex.Pattern;

/** Session-local server evidence, not an entity ESP. Independently implemented from the Garden
 * tab/chat/inventory protocol; SkyHanni is documented as a reference, not bundled/copied. */
public final class PestPlots {
    public static final PestPlots INSTANCE=new PestPlots();
    private static final int[][] GRID={{21,13,9,14,22},{15,5,1,6,16},{10,2,0,3,11},{17,7,4,8,18},{23,19,12,20,24}};
    private static final Pattern TAB=Pattern.compile("^Plots:\\s*(.*)$"),
        NAME=Pattern.compile("^Plot\\s*-\\s*(.+)$"),
        COUNT=Pattern.compile(".*This plot has\\s+(\\d+)\\s+Pests?!.*",Pattern.CASE_INSENSITIVE),
        SPAWN=Pattern.compile("^\\w+! (?:A Pest has appeared|[1-9]\\d* Pests? have spawned) in (?:Plot - )?(.+)!$"),
        OFFLINE=Pattern.compile("^\\w+! While you were offline, Pests? spawned in Plots (.+)!$");
    public record Plot(int id,double x,double z,String name){}
    private final Map<Integer,Long> infected=new HashMap<>();
    private final Map<String,Integer> names=new HashMap<>();
    private ClientLevel world;
    private int ticks,total=-1,serverMinute=-1;
    private long totalAt,clockAt,feverUntil;
    private final Map<Integer,Integer> counts=new HashMap<>();
    private final Map<Integer,Long> countAt=new HashMap<>();
    private static final Pattern ALIVE=Pattern.compile("(?i)^(?:Pests|Alive):?\\s*\\(?(\\d{1,3})\\)?$"),
        CLOCK=Pattern.compile("(?i)\\b(1[0-2]|[1-9]):([0-5][0-9])(am|pm)\\b"),
        SIDE_COUNT=Pattern.compile("(?i)\\bPlot\\s*[-:#]?\\s*(\\d{1,2}).*?\\bx\\s*(\\d{1,3})\\b");
    private boolean garden;
    private String source="Нет данных о заражённых плотах";
    private PestPlots(){}
    public void tick(Minecraft mc){
        if(world!=mc.level){world=mc.level;infected.clear();names.clear();source="Ожидаю Plots: в Tab / Configure Plots";ticks=0;garden=false;total=-1;totalAt=clockAt=feverUntil=0;serverMinute=-1;counts.clear();countAt.clear();}
        if(mc.player==null||world==null)return;
        if(++ticks%20!=0)return;
        garden=PestHunter.garden(mc);
        if(!garden){infected.clear();names.clear();counts.clear();countAt.clear();total=-1;totalAt=clockAt=feverUntil=0;return;}
        long now=System.currentTimeMillis();infected.values().removeIf(time->now-time>180_000);
        for(String line:PanicDetector.sidebar(mc)){
            String s=ServerText.clean(line);var time=CLOCK.matcher(s);if(time.find()){
                int h=Integer.parseInt(time.group(1))%12;serverMinute=h*60+Integer.parseInt(time.group(2))+(time.group(3).equalsIgnoreCase("pm")?720:0);clockAt=now;
            }
            var count=SIDE_COUNT.matcher(s);if(count.find())try{int id=Integer.parseInt(count.group(1));if(id>=0&&id<=24)setCount(id,Integer.parseInt(count.group(2)),now);}catch(NumberFormatException ignored){}
        }
        var conn=mc.getConnection();
        if(conn!=null)for(var info:conn.getOnlinePlayers()){
            var display=info.getTabListDisplayName();if(display==null)continue;
            for(String raw:display.getString().split("\\n")){
                String clean=ServerText.clean(raw);var alive=ALIVE.matcher(clean);
                if(alive.matches()){int n=Integer.parseInt(alive.group(1));if(n<=128){total=n;totalAt=now;}}
                var m=TAB.matcher(clean);if(!m.matches())continue;
                String body=m.group(1).trim();Set<Integer> found=new HashSet<>();boolean valid=true;
                if(!body.isEmpty()&&!body.equalsIgnoreCase("None")&&!body.equals("0")){
                    for(String token:body.split(",\\s*")){
                        try{int id=Integer.parseInt(token.trim());if(id<1||id>24){valid=false;break;}found.add(id);}
                        catch(NumberFormatException ex){valid=false;break;}
                    }
                }
                if(valid){infected.clear();for(int id:found)infected.put(id,now);source="Tab: "+found.size()+" заражённых плотов";}
            }
        }
        if(mc.screen instanceof AbstractContainerScreen<?> screen && screen.getTitle().getString().equals("Configure Plots")){
            var slots=screen.getMenu().slots;
            // 5x5 board in a nine-column server inventory; ignore the player's inventory.
            for(int row=0;row<5;row++)for(int col=0;col<5;col++){
                int slot=2+row*9+col,id=GRID[row][col];if(slot>=slots.size())continue;
                var stack=slots.get(slot).getItem();if(stack.isEmpty())continue;
                String name=ServerText.clean(stack.getHoverName().getString());var nm=NAME.matcher(name);
                boolean recognized=nm.matches() || id==0&&name.equals("The Barn");
                if(!recognized)continue;
                String alias=id==0?"The Barn":nm.group(1);names.put(alias.toLowerCase(Locale.ROOT),id);
                var lore=stack.get(DataComponents.LORE);if(lore==null)continue;
                boolean incomplete=false;int count=-1;
                for(var line:lore.lines()){
                    String text=ServerText.clean(line.getString());var c=COUNT.matcher(text);if(c.matches())count=Integer.parseInt(c.group(1));
                    if(text.contains("Cost:")||text.contains("Cleanup:")||text.contains("Pasting in progress")||text.contains("Greenhouse Plot"))incomplete=true;
                }
                if(!incomplete&&count>=0){setCount(id,count,now);if(count>0)infected.put(id,now);else infected.remove(id);}
            }
            source="Имена плотов обновлены из Configure Plots";
        }
    }
    public void onServerText(String raw){
        if(!garden||raw==null)return;
        String text=ServerText.clean(raw);long now=System.currentTimeMillis();
        if(text.equals("WOAH! You caught a case of the CROP FEVER for 60 seconds!"))feverUntil=now+60_000;
        if(text.equals("GONE! Your CROP FEVER has been cured!"))feverUntil=0;
        if(text.equals("There are not any Pests on your Garden right now! Keep farming!")){infected.clear();total=0;totalAt=now;counts.clear();countAt.clear();return;}
        var m=SPAWN.matcher(text);
        if(m.matches()){Integer id=resolve(m.group(1));if(id!=null){infected.put(id,now);source="Сообщение появления песта";}return;}
        m=OFFLINE.matcher(text);if(m.matches())for(String part:m.group(1).split(",\\s*| and ")){Integer id=resolve(part);if(id!=null)infected.put(id,now);}
    }
    private Integer resolve(String name){
        Integer known=names.get(name.trim().toLowerCase(Locale.ROOT));if(known!=null)return known;
        if(name.equals("The Barn"))return 0;
        try{int id=Integer.parseInt(name.trim());return id>0&&id<=24?id:null;}catch(NumberFormatException ex){return null;}
    }
    public Plot nearest(Vec3 p,Set<Integer> visited){
        Plot best=null;double distance=Double.MAX_VALUE;
        for(int row=0;row<5;row++)for(int col=0;col<5;col++){
            int id=GRID[row][col];Long stamp=infected.get(id);
            if(stamp==null||System.currentTimeMillis()-stamp>180_000||visited.contains(id))continue;
            double x=(col-2)*96,z=(row-2)*96,d=Math.hypot(x-p.x,z-p.z);
            if(d<distance){distance=d;best=new Plot(id,x,z,Integer.toString(id));}
        }
        return best;
    }
    private void setCount(int id,int n,long now){if(n>=0&&n<=128){counts.put(id,n);countAt.put(id,now);}}
    public boolean noWorkRemains(Set<Integer> retained){
        int n=alive();if(n==0)return true;if(n<0||infected.isEmpty())return false;
        int kept=0;for(int id:infected.keySet()){
            if(!retained.contains(id)||plotCount(id)!=1)return false;kept++;
        }
        return kept==n;
    }
    public int alive(){return System.currentTimeMillis()-totalAt<=10_000?total:-1;}
    public int plotCount(int id){return System.currentTimeMillis()-countAt.getOrDefault(id,0L)<=30_000?counts.getOrDefault(id,-1):-1;}
    public boolean fever(){return System.currentTimeMillis()<feverUntil;}
    public boolean inTime(PestSettings s){
        if(!s.timeWindow)return true;if(System.currentTimeMillis()-clockAt>10_000||serverMinute<0)return false;
        return s.startMinute<=s.endMinute?serverMinute>=s.startMinute&&serverMinute<=s.endMinute:serverMinute>=s.startMinute||serverMinute<=s.endMinute;
    }
    public static int plotAt(double x,double z){int col=(int)Math.floor((x+48)/96)+2,row=(int)Math.floor((z+48)/96)+2;return row>=0&&row<5&&col>=0&&col<5?GRID[row][col]:-1;}
    public static Plot plot(int id){for(int row=0;row<5;row++)for(int col=0;col<5;col++)if(GRID[row][col]==id)return new Plot(id,(col-2)*96,(row-2)*96,Integer.toString(id));return null;}
    public String commandName(int id){return names.entrySet().stream().filter(e->e.getValue()==id).map(Map.Entry::getKey).findFirst().orElse(Integer.toString(id));}
    public Set<Integer> knownPlots(){return Set.copyOf(infected.keySet());}
    public boolean hasPlots(){return !infected.isEmpty();}
    public String status(){return source+"; плоты: "+new TreeSet<>(infected.keySet())+"; всего: "+alive()+" (-1 неизвестно), время: "+(serverMinute<0?"?":serverMinute/60+":"+String.format("%02d",serverMinute%60))+"; Crop Fever: "+fever();}
}
