package com.farmmacro.loadout;

import java.util.*;

/** Existing owned equipment only. Auto conditions and each action require explicit opt-in. */
public final class LoadoutSettings {
    public boolean enabled=false,auto=false;
    public String selected="combat";
    public List<Entry> entries=new ArrayList<>(List.of(new Entry("combat","Combat"),new Entry("mining","Mining"),new Entry("foraging","Foraging")));
    public static final class Entry {
        public String id="",name="",itemId="",itemUuid="",itemName="",pet="",petUuid="",combatProfile="",locations="",server="",dimension="";
        public boolean hand=false,armor=false,petEnabled=false,profile=false,auto=false;
        public int wardrobe=1;
        public Entry(){}
        public Entry(String id,String name){this.id=id;this.name=name;}
        public void sanitize(){
            name=clean(name,40);if(name.isBlank())name="Loadout";
            itemId=clean(itemId,128);itemUuid=clean(itemUuid,128);itemName=clean(itemName,100);
            pet=clean(pet,64);petUuid=clean(petUuid,128);combatProfile=clean(combatProfile,64);
            locations=clean(locations,512);server=clean(server,256);dimension=clean(dimension,128);
            wardrobe=Math.clamp(wardrobe,1,18);
        }
        public boolean hasAction(){return hand||armor||petEnabled||profile;}
        public List<String> areas(){return Arrays.stream(locations.split(";")).map(String::strip).filter(x->!x.isBlank()).distinct().limit(16).toList();}
    }
    public void sanitize(){
        if(entries==null)entries=new ArrayList<>();Set<String> ids=new HashSet<>();
        entries=new ArrayList<>(entries.stream().filter(e->e!=null&&e.id!=null&&e.id.matches("[a-zA-Z0-9_-]{1,64}")&&ids.add(e.id)).limit(12).toList());
        if(entries.isEmpty())entries.add(new Entry("combat","Combat"));
        for(var e:entries)e.sanitize();
        if(selected==null||entries.stream().noneMatch(e->e.id.equals(selected)))selected=entries.getFirst().id;
    }
    private static String clean(String s,int limit){if(s==null)return "";String t=s.replaceAll("[\\p{Cntrl}]","").strip();return t.length()>limit?t.substring(0,limit):t;}
    public Entry current(){return entries.stream().filter(e->e.id.equals(selected)).findFirst().orElse(entries.getFirst());}
}
