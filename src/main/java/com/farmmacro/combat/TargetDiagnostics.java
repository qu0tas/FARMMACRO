package com.farmmacro.combat;

import java.util.*;

/** Bounded last-selection snapshot, not a chat/event log. Callers pass canonical mob kinds only. */
public final class TargetDiagnostics {
    public enum Reason {
        CHOSEN("выбран",0), WAITING("ожидает результат выстрела",1), READY("подходит, сравнивается оценка",2),
        COVER("тело закрыто",3), FOV("за пределами FOV",4), RANGE("вне радиуса",5),
        BUDGET("отложен: лимит геометрии",6), FILTER("не проходит фильтр",6), SPECIAL("приоритет Special",7), INVALID("недоступен",8);
        final String label;final int order;
        Reason(String label,int order){this.label=label;this.order=order;}
    }
    private record Row(int id,int number,String kind,Reason reason,double distance,int band) {}
    private static final int LIMIT=12;
    private static final Comparator<Row> ORDER=Comparator.comparingInt((Row r)->r.reason().order)
        .thenComparingDouble(Row::distance).thenComparingInt(Row::id);
    private final List<Row> rows=new ArrayList<>(LIMIT+1);
    private final EnumMap<Reason,Integer> counts=new EnumMap<>(Reason.class);
    private long capturedAt;
    private int total;
    private String state="выбор ещё не выполнялся";

    public void begin(){rows.clear();counts.clear();total=0;capturedAt=System.nanoTime();}
    public void state(String value){state=value;}
    public void clear(String value){begin();capturedAt=0;state=value;}
    public void record(int id,int number,String kind,Reason reason,double distance,int band){
        total++;counts.merge(reason,1,Integer::sum);
        retain(new Row(id,number,kind,reason,distance,band));
    }
    private void retain(Row row){
        if(rows.size()==LIMIT&&ORDER.compare(row,rows.getLast())>=0)return;
        rows.add(row);rows.sort(ORDER);
        if(rows.size()>LIMIT)rows.removeLast();
    }
    public void chosen(int id,int number,String kind,double distance,int band){
        if(counts.getOrDefault(Reason.READY,0)>0)counts.merge(Reason.READY,-1,Integer::sum);
        counts.merge(Reason.CHOSEN,1,Integer::sum);
        rows.removeIf(r->r.id()==id);
        retain(new Row(id,number,kind,Reason.CHOSEN,distance,band));
    }
    public String summary(){
        StringBuilder out=new StringBuilder("Выбор: ").append(state);
        if(capturedAt==0)return out.toString();
        out.append("\nПоследний поиск ").append(Math.max(0,(System.nanoTime()-capturedAt)/1_000_000L))
            .append(" мс назад; кандидатов ").append(total);
        for(var reason:Reason.values()){
            int n=counts.getOrDefault(reason,0);
            if(n>0)out.append(" · ").append(reason.label).append(": ").append(n);
        }
        return out.toString();
    }
    /** Safe report omits free-form state/kind. Local numeric target ordinals only. */
    public String safeDetails(){StringBuilder b=new StringBuilder("ageMs=").append(capturedAt==0?-1:Math.max(0,(System.nanoTime()-capturedAt)/1_000_000L)).append("; candidates=").append(total);
        for(var reason:Reason.values())b.append(' ').append(reason.name()).append('=').append(counts.getOrDefault(reason,0));
        for(var row:rows)b.append("\n#").append(row.number()).append(' ').append(row.reason().name()).append(" band=").append(row.band());return b.toString();}
    public String details(){
        StringBuilder out=new StringBuilder(summary());
        for(var row:rows){
            out.append("\n").append(row.number()>0?"#"+row.number():"entity:"+row.id())
                .append(" ").append(row.kind()).append(" — ").append(row.reason().label);
            if(Double.isFinite(row.distance()))out.append(String.format(Locale.ROOT," · %.1f бл",row.distance()));
            if(row.band()>=0)out.append(" · сектор ").append(row.band());
        }
        if(total>rows.size())out.append("\nПоказаны приоритетные ").append(rows.size()).append(" из ").append(total);
        return out.toString();
    }
}