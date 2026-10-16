package com.farmmacro.panic;

import java.util.Objects;

/** One-shot evidence for an exact destination slot/item, not a blanket inventory grace period. */
public final class SlotSwitchIntents<T> {
    public static final long MAX_AGE_NS=500_000_000L;
    private record Ticket<T>(T identity,long at) {}
    private final java.util.Map<Integer,Ticket<T>> tickets=new java.util.HashMap<>();
    public void arm(int slot,T identity,long now){if(slot>=0&&slot<9)tickets.put(slot,new Ticket<>(identity,now));}
    public boolean consume(int slot,T identity,long now){
        Ticket<T> ticket=tickets.remove(slot);
        return ticket!=null&&now>=ticket.at()&&now-ticket.at()<=MAX_AGE_NS&&Objects.equals(identity,ticket.identity());
    }
    public void clear(){tickets.clear();}
}
