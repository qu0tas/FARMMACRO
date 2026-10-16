package com.farmmacro.config;
/** Independent combat-only switches and notification settings; no global registry actions. */
public final class CombatPanicSettings {
    public boolean enabled=true,teleport=true,weapon=true,nearbyChat=true;
    public boolean smallCorrections=true;
    public boolean playerOverlap=true;
    public boolean serverRotation=true;
    public double serverRotationDegrees=20;
    public double correctionHorizontal=.8,correctionVertical=.5;
    public int correctionWindowSec=3,correctionMaxPackets=4;
    public double correctionBudget=2;
    public double chatRadius=10;public int chatMemorySec=15;
    public boolean mentionName=true,ignoreFriends=true;
    public String keywords="макро, macro, бот, bot, афк, afk, ты тут, ответь, проверка, check",friends="";
    public boolean sound=true,redScreen=true,focusWindow=true,exitPip=true;
    public String soundId="builtin:siren";public double volume=1;
    public int soundRepeats=2,soundDelayTicks=30,redScreenTicks=60;
    public void sanitize(){
        serverRotationDegrees=Double.isFinite(serverRotationDegrees)?Math.clamp(serverRotationDegrees,1,180):20;
        correctionHorizontal=Double.isFinite(correctionHorizontal)?Math.clamp(correctionHorizontal,0,1.5):.8;
        correctionVertical=Double.isFinite(correctionVertical)?Math.clamp(correctionVertical,0,1):.5;
        correctionWindowSec=Math.clamp(correctionWindowSec,1,10);
        correctionMaxPackets=Math.clamp(correctionMaxPackets,1,12);
        correctionBudget=Double.isFinite(correctionBudget)?Math.clamp(correctionBudget,.05,6):2;
        chatRadius=Double.isFinite(chatRadius)?Math.clamp(chatRadius,1,32):10;
        chatMemorySec=Math.clamp(chatMemorySec,3,60);
        if(keywords==null)keywords="";if(friends==null)friends="";
        if(keywords.length()>1000)keywords=keywords.substring(0,1000);
        if(friends.length()>1000)friends=friends.substring(0,1000);
        if(!java.util.List.of("builtin:siren","builtin:klaxon","builtin:alarm","builtin:alert").contains(soundId==null?"":soundId))soundId="builtin:siren";
        volume=Double.isFinite(volume)?Math.clamp(volume,0,1):1;
        soundRepeats=Math.clamp(soundRepeats,1,20);soundDelayTicks=Math.clamp(soundDelayTicks,1,1200);redScreenTicks=Math.clamp(redScreenTicks,1,1200);
    }
}
