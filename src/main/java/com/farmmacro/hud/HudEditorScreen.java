package com.farmmacro.hud;

import com.farmmacro.config.HudSlot;
import com.farmmacro.config.ModConfig;
import com.farmmacro.gui.FlatButton;
import com.farmmacro.gui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Floating editor chrome: no permanently blocked top/bottom HUD regions. H hides chrome. */
public class HudEditorScreen extends Screen {
    private final Screen parent;
    private List<FarmHud.Rect> rects=List.of();
    private final List<FlatButton> buttons=new ArrayList<>();
    private String selectedId="inventory",dragId,panelDrag;
    private double grabDx,grabDy,dragX,dragY,initialX,initialY,initialW,initialH,initialScale,panelGrabX,panelGrabY;
    private boolean resizing,showList,chromeHidden;
    private int listScroll,dockW,dockH,dockX,dockY,infoX,infoY,listX,listY;
    private long resetAllUntil;
    private FlatButton listBtn,snapBtn,gridBtn,mergeBtn,hideBtn,resetBtn,allBtn,alwaysBtn;

    public HudEditorScreen(Screen parent) { super(Component.literal("Редактор HUD"));this.parent=parent; }
    private ModConfig cfg() { return ModConfig.INSTANCE; }
    private String yes(boolean value) { return value?"вкл":"выкл"; }
    private int infoW() { return Math.min(360,Math.max(80,width-16)); }
    private int listW() { return Math.min(198,Math.max(80,width-16)); }
    private int listH() { return Math.min(280,Math.max(60,height-16)); }
    private int boundX(int x,int w) { return Math.clamp(x,0,Math.max(0,width-w)); }
    private int boundY(int y,int h) { return Math.clamp(y,0,Math.max(0,height-h)); }
    private FlatButton add(String text,String tip,Runnable action) {
        var b=new FlatButton(0,0,20,20,text,button->action.run());
        b.setTooltip(Tooltip.create(Component.literal(tip)));buttons.add(b);return addRenderableWidget(b);
    }
    @Override protected void init() {
        buttons.clear();dockW=Math.min(360,Math.max(80,width-16));
        int cols=dockW>=280?3:2;dockH=22+((10+cols-1)/cols)*23;
        dockX=boundX(cfg().hudEditorDockX<0?width-dockW-8:cfg().hudEditorDockX,dockW);
        dockY=boundY(cfg().hudEditorDockY<0?height-dockH-8:cfg().hudEditorDockY,dockH);
        infoX=boundX(cfg().hudEditorInfoX,infoW());infoY=boundY(cfg().hudEditorInfoY,48);
        listX=boundX(cfg().hudEditorListX,listW());listY=boundY(cfg().hudEditorListY,listH());
        listBtn=add("Плашки","Список скрытых/пустых. Перетащи список за заголовок",()->{showList=!showList;dragId=null;});
        snapBtn=add("Snap","Прилипание к сетке8px, краям и центру",()->cfg().hudEditorSnap=!cfg().hudEditorSnap);
        gridBtn=add("Сетка","Сетка8px; отдельно от прилипания",()->cfg().hudEditorGrid=!cfg().hudEditorGrid);
        mergeBtn=add("Общая плашка","Индивидуальные места сохраняются",()->{cfg().hudMerged=!cfg().hudMerged;dragId=null;});
        hideBtn=add("Скрыть / показать","Для инвентаря также включает отображение; не включает автоматику",this::toggleHidden);
        alwaysBtn=add("Показ: по ситуации","Всегда: виджет виден и без активной задачи. Не запускает модуль",()->{
            if(selectedId.equals("all"))return;var s=slot(selectedId);s.always=!s.always;s.hidden=false;
            if(s.always&&selectedId.equals("inventory"))cfg().hudInventory=true;
        });
        resetBtn=add("Сброс выбранной","Только её место/размер/личные цвета. В общей панели — геометрия all",()->{String id=selectedScaleId();var old=slot(id);var fresh=new HudSlot();fresh.always=old.always;fresh.hidden=old.hidden;cfg().hudSlots.put(id,fresh);dragId=null;});
        allBtn=add("Сброс всех","Повторное нажатие в течение3секунд; иначе сброса нет",()->{
            long now=System.nanoTime();if(now>=resetAllUntil){resetAllUntil=now+3_000_000_000L;return;}
            cfg().hudSlots.clear();resetAllUntil=0;dragId=null;
        });
        add("Готово","Сохранить и вернуться; Esc тоже сохраняет",this::onClose);
        add("Скрыть панели · H","Убрать все панели редактора для доступа к любому месту. H возвращает",this::toggleChrome);
        positionButtons();
    }
    private void positionButtons() {
        int cols=dockW>=280?3:2,bw=(dockW-12-(cols-1)*4)/cols;
        for(int i=0;i<buttons.size();i++) {
            var b=buttons.get(i);b.setX(dockX+6+i%cols*(bw+4));b.setY(dockY+17+i/cols*23);b.setWidth(bw);b.visible=!chromeHidden;
        }
    }
    private void toggleChrome() {
        chromeHidden=!chromeHidden;panelDrag=null;dragId=null;positionButtons();setFocused(null);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta) {
        Ui.sync(cfg().uiTheme);g.fill(0,0,width,height,0x1a000000);
        if(cfg().hudEditorGrid) {
            for(int x=0;x<width;x+=8)g.fill(x,0,x+1,height,0x10ffffff);
            for(int y=0;y<height;y+=8)g.fill(0,y,width,y+1,0x10ffffff);
        }
        g.fill(width/2,0,width/2+1,height,0x28ffffff);g.fill(0,height/2,width,height/2+1,0x28ffffff);
        rects=List.of();
        if(minecraft!=null&&minecraft.player!=null){FarmHud.prepare(minecraft);rects=FarmHud.layout(g,minecraft,true);}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        listBtn.setMessage(Component.literal("Плашки: "+yes(showList)));
        snapBtn.setMessage(Component.literal("Snap: "+yes(cfg().hudEditorSnap)));
        gridBtn.setMessage(Component.literal("Сетка: "+yes(cfg().hudEditorGrid)));
        mergeBtn.setMessage(Component.literal("Общая: "+yes(cfg().hudMerged)));
        allBtn.setMessage(Component.literal(System.nanoTime()<resetAllUntil?"Сбросить ВСЁ?":"Сброс всех"));
        hideBtn.active=rect(selectedId)!=null&&!selectedId.equals("all");resetBtn.active=rect(selectedId)!=null;
        hideBtn.setMessage(Component.literal(selectedId.equals("inventory")&&!cfg().hudInventory?"Включить инвентарь":"Скрыть / показать"));
        alwaysBtn.active=rect(selectedId)!=null&&!selectedId.equals("all");
        alwaysBtn.setMessage(Component.literal(FarmHud.pinned(selectedId)?"Показ: всегда":"Показ: по ситуации"));
        for(var r:rects) {
            if(r.id().equals(selectedId)||r.id().equals(dragId))outline(g,r,Ui.ACCENT_HI);
            else if(r.contains(mx,my)&&!overChrome(mx,my))outline(g,r,0x80ffffff);
        }
        g.nextStratum();
        if(chromeHidden) {
            // Only a tiny non-interactive hint; it never reserves a click region.
            Ui.text(g,font,"H — панели · Esc — готово",6,height-12,Ui.TEXT);return;
        }
        Ui.roundBordered(g,infoX,infoY,infoW(),48,5,Ui.TOOLTIP,Ui.BORDER);
        Ui.text(g,font,Ui.ellipsize(font,"HUD · тащи эту панель за заголовок · H скрыть",infoW()-16),infoX+8,infoY+6,Ui.TEXT);
        Ui.text(g,font,Ui.ellipsize(font,"ЛКМ перенос · Ctrl+drag / колесо размер · C в центр",infoW()-16),infoX+8,infoY+20,Ui.SUB);
        var chosen=rect(selectedId);var sl=cfg().hudSlots.get(selectedScaleId());
        String info=chosen==null?"Плашки → выбери элемент":chosen.label()+" · "+Math.round((sl==null?1:sl.scale)*100)+"%"+(chosen.hidden()?" · скрыта":"");
        if(selectedId.equals("inventory")&&!cfg().hudInventory)info+=" · предпросмотр, включи кнопкой";
        Ui.text(g,font,Ui.ellipsize(font,info,infoW()-16),infoX+8,infoY+34,Ui.SUB);
        if(showList)drawList(g);
        Ui.roundBordered(g,dockX,dockY,dockW,dockH,5,Ui.TOOLTIP,Ui.BORDER);
        Ui.text(g,font,Ui.ellipsize(font,"Инструменты · перетащи заголовок · H скрыть",dockW-16),dockX+8,dockY+5,Ui.SUB);
        super.extractRenderState(g,mx,my,delta);
    }
    private static void outline(GuiGraphicsExtractor g,FarmHud.Rect r,int color) {
        int x=r.x(),y=r.y(),w=r.w(),h=r.h();
        g.fill(x-1,y-1,x+w+1,y,color);g.fill(x-1,y+h,x+w+1,y+h+1,color);
        g.fill(x-1,y,x,y+h,color);g.fill(x+w,y,x+w+1,y+h,color);
    }
    private boolean overList(double x,double y){return !chromeHidden&&showList&&Ui.inside(x,y,listX,listY,listW(),listH());}
    private boolean overChrome(double x,double y){return !chromeHidden&&(overList(x,y)||Ui.inside(x,y,dockX,dockY,dockW,dockH)||Ui.inside(x,y,infoX,infoY,infoW(),48));}
    private List<FarmHud.Rect> listRects(){return rects.stream().filter(r->!r.id().equals("all")).toList();}
    private int visibleRows(){return Math.max(0,(listH()-32)/21);}
    private void clampList(){listScroll=Math.clamp(listScroll,0,Math.max(0,listRects().size()-visibleRows()));}
    private void drawList(GuiGraphicsExtractor g) {
        clampList();Ui.roundBordered(g,listX,listY,listW(),listH(),5,Ui.TOOLTIP,Ui.BORDER);
        Ui.text(g,font,"Плашки · тащи заголовок",listX+8,listY+6,Ui.TEXT);
        var list=listRects();int count=Math.min(visibleRows(),list.size()-listScroll);
        for(int i=0;i<count;i++) {
            var r=list.get(i+listScroll);int y=listY+20+i*21;
            if(r.id().equals(selectedId))Ui.round(g,listX+4,y,listW()-8,20,3,Ui.alpha(Ui.ACCENT,.18f));
            boolean off=r.hidden()||r.id().equals("inventory")&&!cfg().hudInventory;
            Ui.text(g,font,Ui.ellipsize(font,r.label(),listW()-42),listX+9,y+6,off?Ui.SUB:Ui.TEXT);
            Ui.text(g,font,off?"−":"+",listX+listW()-20,y+6,off?Ui.WARN:Ui.ON);
        }
        if(count>0&&list.size()>count)Ui.text(g,font,(listScroll+1)+"–"+(listScroll+count)+" / "+list.size(),listX+8,listY+listH()-11,Ui.SUB);
    }
    private FarmHud.Rect hit(double x,double y) {
        if(overChrome(x,y))return null;
        FarmHud.Rect best=null;for(var r:rects)if(r.contains(x,y)&&(best==null||r.sub()||!best.sub()))best=r;return best;
    }
    private FarmHud.Rect rect(String id){for(var r:rects)if(r.id().equals(id))return r;return null;}
    private String selectedScaleId(){var r=rect(selectedId);return r!=null&&r.sub()?"all":selectedId;}
    private HudSlot slot(String id){return cfg().hudSlots.computeIfAbsent(id,k->new HudSlot());}
    private void toggleHidden() {
        if(selectedId.equals("all")||rect(selectedId)==null)return;
        var s=slot(selectedId);
        if(selectedId.equals("inventory")&&!cfg().hudInventory){cfg().hudInventory=true;s.hidden=false;s.always=true;return;}
        s.hidden=!s.hidden;
    }
    private boolean beginPanel(String id,double x,double y,int px,int py) { panelDrag=id;panelGrabX=x-px;panelGrabY=y-py;dragId=null;setFocused(null);return true; }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubled) {
        // Same z-order as drawing: tools on top, then list, then information.
        if(!chromeHidden) {
            if(Ui.inside(e.x(),e.y(),dockX,dockY,dockW,16)&&e.button()==0)return beginPanel("dock",e.x(),e.y(),dockX,dockY);
            if(Ui.inside(e.x(),e.y(),dockX,dockY,dockW,dockH)){super.mouseClicked(e,doubled);return true;}
            if(overList(e.x(),e.y())) {
                if(e.y()<listY+18&&e.button()==0)return beginPanel("list",e.x(),e.y(),listX,listY);
                var list=listRects();int i=(int)(e.y()-listY-20)/21+listScroll;
                if(e.y()>=listY+20&&i>=listScroll&&i<Math.min(list.size(),listScroll+visibleRows())) {
                    selectedId=list.get(i).id();if(e.button()==1||e.x()>listX+listW()-30)toggleHidden();
                }return true;
            }
            if(Ui.inside(e.x(),e.y(),infoX,infoY,infoW(),48)) {
                if(e.y()<infoY+18&&e.button()==0)return beginPanel("info",e.x(),e.y(),infoX,infoY);return true;
            }
        }
        var r=hit(e.x(),e.y());if(r==null)return false;selectedId=r.id();resetAllUntil=0;
        if(e.button()==1){toggleHidden();return true;}if(e.button()!=0)return false;
        dragId=selectedScaleId();var whole=rect(dragId);if(whole==null){dragId=null;return true;}
        var s=slot(dragId);if(!s.placed)HudLayout.store(s,whole.x(),whole.y(),whole.w(),whole.h(),width,height);
        grabDx=e.x()-whole.x();grabDy=e.y()-whole.y();dragX=e.x();dragY=e.y();
        initialX=whole.x();initialY=whole.y();initialW=whole.w();initialH=whole.h();initialScale=s.scale;
        resizing=minecraft.hasControlDown();return true;
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy) {
        if(panelDrag!=null) {
            int x=(int)Math.round(e.x()-panelGrabX),y=(int)Math.round(e.y()-panelGrabY);
            switch(panelDrag) {
                case "dock"->{dockX=boundX(x,dockW);dockY=boundY(y,dockH);positionButtons();}
                case "list"->{listX=boundX(x,listW());listY=boundY(y,listH());}
                case "info"->{infoX=boundX(x,infoW());infoY=boundY(y,48);}
            }return true;
        }
        if(dragId==null)return super.mouseDragged(e,dx,dy);
        var r=rect(dragId);if(r==null)return true;var s=slot(dragId);
        if(resizing) {
            double sx=(e.x()-dragX)/Math.max(1,initialW),sy=(e.y()-dragY)/Math.max(1,initialH);
            double amount=Math.abs(sx)>=Math.abs(sy)?sx:sy;s.scale=Math.clamp(initialScale*(1+amount),.5,2);
            double ratio=s.scale/initialScale;HudLayout.store(s,initialX,initialY,initialW*ratio,initialH*ratio,width,height);
        }else {
            double x=e.x()-grabDx,y=e.y()-grabDy;
            if(cfg().hudEditorSnap){x=HudLayout.snap(Math.round(x/8)*8,r.w(),width);y=HudLayout.snap(Math.round(y/8)*8,r.h(),height);}
            HudLayout.store(s,x,y,r.w(),r.h(),width,height);
        }return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) {
        if(e.button()==0&&(dragId!=null||panelDrag!=null)){dragId=null;panelDrag=null;super.mouseReleased(e);return true;}
        return super.mouseReleased(e);
    }
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy) {
        if(overList(mx,my)){listScroll-=(int)Math.signum(sy);clampList();return true;}
        if(overChrome(mx,my))return true;
        var r=hit(mx,my);if(r==null||sy==0)return super.mouseScrolled(mx,my,sx,sy);
        selectedId=r.id();String id=selectedScaleId();var whole=rect(id);if(whole==null)return true;
        var s=slot(id);double old=s.scale;s.scale=HudLayout.scaleStep(old,sy);double ratio=s.scale/old;
        double nw=whole.w()*ratio,nh=whole.h()*ratio;HudLayout.store(s,whole.x()+(whole.w()-nw)/2,whole.y()+(whole.h()-nh)/2,nw,nh,width,height);return true;
    }
    private void placeSelected(boolean center,int dx,int dy) {
        var r=rect(selectedScaleId());if(r==null)return;
        double x=center?Math.max(0,(width-r.w())/2.0):r.x()+dx;
        double y=center?Math.max(0,(height-r.h())/2.0):r.y()+dy;
        HudLayout.store(slot(selectedScaleId()),Math.clamp(x,0,Math.max(0,width-r.w())),Math.clamp(y,0,Math.max(0,height-r.h())),r.w(),r.h(),width,height);
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if(e.key()==72){toggleChrome();return true;}
        if(e.key()==67){placeSelected(true,0,0);return true;}
        if(chromeHidden&&e.key()>=262&&e.key()<=265) {
            int step=e.hasShiftDown()?8:1;
            placeSelected(false,e.key()==262?step:e.key()==263?-step:0,e.key()==264?step:e.key()==265?-step:0);return true;
        }
        return super.keyPressed(e);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed() {
        cfg().hudEditorDockX=dockX;cfg().hudEditorDockY=dockY;cfg().hudEditorInfoX=infoX;cfg().hudEditorInfoY=infoY;
        cfg().hudEditorListX=listX;cfg().hudEditorListY=listY;cfg().sanitize();ModConfig.save();super.removed();
    }
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreen(parent);}
}
