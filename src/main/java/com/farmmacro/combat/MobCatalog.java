package com.farmmacro.combat;
import java.util.*;

/** Name presets, not an exhaustive promise about every server disguise. Sources in COMBAT-ALPHA.md. */
public final class MobCatalog {
    public record Group(String id,String title,List<String> locations,List<String> mobs){}
    private static Group group(String id,String title,String locations,String mobs){
        return new Group(id,title,List.of(locations.split(";")),List.of(mobs.split(";")));
    }
    private static Group slayer(String id,String title,String locations,String related){
        return group("slayer_"+id,title,locations,SlayerCatalog.withEncounter(id,related));
    }
    public static final List<Group> GROUPS=List.of(
        group("zealots","Зиалоты — The End","The End;Dragon's Nest;Zealot Bruiser Hideout","Zealot;Zealot Bruiser;Special Zealot"),
        group("deep_caverns","Combat: Deep Caverns / Dwarven Mines","Deep Caverns;Gunpowder Mines;Lapis Quarry;Pigmen's Den;Slimehill;Diamond Reserve;Obsidian Sanctuary;Dwarven Mines;Royal Mines;The Mist","Sneaky Creeper;Lapis Zombie;Redstone Pigman;Emerald Slime;Miner Zombie;Miner Skeleton;Glacite Walker;Ghost"),
        group("crimson","Combat: Crimson Isle","Crimson Isle;Stronghold;Barbarian Outpost;Mage Outpost;Smoldering Tomb","Magma Cube;Magma Cube Rider;Blaze;Wither Skeleton;Flaming Spider;Magma Slug;Matcho;Millenia-Aged Blaze;Bladesoul;Duke;Ashfang"),
        slayer("zombie","Slayer: Zombie — Hub","Hub;Graveyard;Crypt;Coal Mine;Deep Caverns;Jungle;Backwater Bayou","Zombie;Graveyard Zombie;Zombie Villager;Crypt Ghoul;Golden Ghoul;Lapis Zombie;Miner Zombie;Key Guardian;Sea Walker;Rider of the Deep;Water Hydra;Dumpster Diver;Minos Hunter;Blessed Minos Hunter;Stalwart Minos Hunter;Venerable Minos Hunter;Exalted Minos Hunter;Empyrean Minos Hunter;Revenant"),
        slayer("spider","Slayer: Spider — Spider's Den","Spider's Den;Spider Mound;Arachne;Burning Desert","Spider;Dasher Spider;Splitter Spider;Voracious Spider;Weaver Spider;Spider Jockey;Cave Spider;Broodmother;Flaming Spider;Tarantula Spider"),
        slayer("wolf","Slayer: Wolf — Ruins / Park","Ruins;The Park;Howling Cave;Mithril Deposits","Wolf;Old Wolf;Pack Spirit;Howling Spirit;Soul of the Alpha;Belle"),
        slayer("enderman","Slayer: Enderman — End","The End;Dragon's Nest;Void Sepulture;Zealot Bruiser Hideout","Enderman;Zealot;Zealot Bruiser;Voidling;Voidling Fanatic;Voidling Extremist"),
        slayer("blaze","Slayer: Blaze — Crimson Isle","Crimson Isle;Smoldering Tomb;Stronghold;Khazad-dûm","Blaze;Infernal;Bald Blaze;Mutated Blaze;Bezal;Smoldering Blaze"),
        slayer("vampire","Slayer: Vampire — Rift","The Rift;Stillgore Château","Vampire;Thrall;Fledgling;Splatter"),
        group("hunting_marsh","Hunting: Moonglade Marsh","Galatea;Moonglade Marsh;Wyrmgrove Tomb;Tranquility Sanctum;Tomb Floodway","Chillshot;Chillblade;Phanpyre;Phanflare;Dreadwing;Mochibear;Bambuleaf;Spike;Shellwise;Mossybit;Birries;Coralot;Joydive;Lumisquid;Azure;Hideonleaf;Honeyhog;Honeymite;Hewver"),
        group("hunting_canyon","Hunting: Torrhus Canyon","Galatea;Torrhus Canyon;Torrhus Heights;Torrhus Springs;Ant's Cave;Desert Temple;Spring Shallows;Spring Depths","Ant;Queen Ant;Beeheemoth;Bunbun;Firefox;Cheeky Tiki;Sneaky Tiki;Shrieky Tiki;Silkbreeze;Puck;Ember;Sepialot"),
        group("hunting_charm","Hunting: Combat / Charm","Dwarven Mines;Royal Mines;The Mist;Glacite","Glacite Walker;Ghost"),
        group("combat","Combat: обычные мобы","Hub;Spider's Den;The End;Crimson Isle","Zombie;Skeleton;Spider;Cave Spider;Enderman;Blaze;Slime;Magma Cube;Witch;Wither Skeleton;Crypt Ghoul;Zealot;Zealot Bruiser"),
        group("nearby","Мобы рядом — свой список","",""));
    public static Group get(String id){return GROUPS.stream().filter(g->g.id().equals(id)).findFirst().orElseGet(()->GROUPS.stream().filter(g->g.id().equals("combat")).findFirst().orElseThrow());}
    private MobCatalog(){}
}
