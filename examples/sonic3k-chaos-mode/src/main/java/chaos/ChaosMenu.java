package chaos;

import com.openggf.control.*;
import com.openggf.configuration.*;
import com.openggf.game.LevelInputOverlay;
import com.openggf.mods.ui.*;
import java.util.*;

/** One title/pause menu; selected pool entries never toggle a backing definition permanently. */
public final class ChaosMenu implements LevelInputOverlay {
    private final ChaosController controller;
    private final ChaosCatalog catalogue;
    private final ChaosPreferences preferences;
    private SonicConfigurationService configuration;
    private ChaosSettings draft;
    private boolean open,title,configure,launch,waiting,searchEditing;
    private int row,panel,availableRow,selectedRow,filter,width=320;
    private String search="",message="";
    private Command command=Command.NONE;
    public ChaosMenu(ChaosController controller,ChaosCatalog catalogue,ChaosPreferences preferences) {
        this.controller=controller;this.catalogue=catalogue;this.preferences=preferences;draft=preferences.saved();
    }
    public void configuration(SonicConfigurationService value) {configuration=value;}
    public void openTitle() {title=true;open=true;configure=false;waiting=false;row=0;draft=preferences.saved();message=preferences.error();}
    public boolean open() {return open;}
    public boolean title() {return title;}
    public boolean consumeLaunch() {boolean result=launch;launch=false;return result;}
    public void titleInput(InputHandler input) {if(open && !waiting) handle(input);}
    @Override public boolean handleInput(InputHandler input) {
        if(title) return open;
        if(controller.sidegameActive()) {
            if(input!=null && (input.logical().player1().startPressed() || MenuInput.textKeyPressed(input,256)))
                controller.requestCancelSidegame();
            return true;
        }
        boolean pause=input!=null && (input.logical().player1().startPressed()
                || configuration!=null && input.isKeyPressed(configuration.getInt(SonicConfiguration.PAUSE_KEY)));
        if(!open) {
            if(!pause || controller.sidegameActive()) return false;
            open=true;configure=false;row=0;draft=preferences.saved();message=preferences.error();controller.cue(false);return true;
        }
        if(!waiting) handle(input);
        return true;
    }
    public void leaveTitle() {title=false;open=false;configure=false;waiting=false;}
    @Override public boolean pausesGameplay() {return open && !title;}
    @Override public Command consumeCommand() {Command result=command;command=Command.NONE;return result;}
    @Override public void commandQueued(boolean blocked) {waiting=blocked;if(!blocked)open=false;}
    @Override public void close() {open=false;}
    private List<String> homeRows() {
        return title?List.of("Start Chaos","Play normally","Configure Chaos","Reset defaults","Return to hub"):
                List.of("Resume play","Configure Chaos",draft.enabled()?"Pause Chaos":"Enable Chaos","Clear active effects","Restart act","Return to hub");
    }
    private List<ChaosCatalog.Effect> list(boolean selected) {
        return catalogue.effects().stream().filter(e->draft.selected().contains(e.id())==selected)
                .filter(e->filter==0 || e.category().ordinal()==filter-1)
                .filter(e->search.isEmpty() || (e.name()+" "+e.id()).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))).toList();
    }
    private void handle(InputHandler input) {
        if(input==null)return;
        if(MenuInput.back(input)) {
            if(searchEditing) searchEditing=false;
            else if(configure) {configure=false;row=title?2:1;}
            else if(title) {message="Choose Play normally or Return to hub.";}
            else message="Choose Resume to apply settings.";
            controller.cue(false);return;
        }
        if(configure) {
            if(searchEditing) {
                String typed=MenuInput.consumeText(input);
                for(int i=0;i<typed.length() && search.length()<32;i++) if(!Character.isISOControl(typed.charAt(i)))search+=typed.charAt(i);
                if(MenuInput.textKeyRepeated(input,259) && !search.isEmpty())search=search.substring(0,search.length()-1);
                if(MenuInput.accept(input))searchEditing=false;
                return;
            }
            int horizontal=(MenuInput.right(input)?1:0)-(MenuInput.left(input)?1:0);
            int vertical=(MenuInput.down(input)?1:0)-(MenuInput.up(input)?1:0);
            if(panel==0) {
                if(vertical!=0)row=Math.floorMod(row+vertical,7);
                if(horizontal!=0) {
                    if(row==0)draft=draft.interval(Math.clamp(draft.intervalSeconds()+horizontal*5,5,120));
                    else if(row==1)draft=draft.multiplier(Math.clamp(draft.durationQuarters()+horizontal,1,12));
                    else if(row==2)filter=Math.floorMod(filter+horizontal,4);
                    else {panel=horizontal>0?1:2;}
                }
                if(MenuInput.accept(input)) {
                    if(row==3)searchEditing=true;
                    else if(row==4)draft=new ChaosSettings(draft.intervalSeconds(),draft.durationQuarters(),
                            new LinkedHashSet<>(catalogue.effects().stream().map(ChaosCatalog.Effect::id).toList()),draft.enabled(),draft.seed());
                    else if(row==5)draft=new ChaosSettings(draft.intervalSeconds(),draft.durationQuarters(),Set.of(),draft.enabled(),draft.seed());
                    else if(row==6) {configure=false;row=title?2:1;}
                }
            } else {
                boolean selected=panel==2;List<ChaosCatalog.Effect> entries=list(selected);
                int focus=selected?selectedRow:availableRow;
                if(!entries.isEmpty()) focus=Math.floorMod(focus+vertical,entries.size());
                if(selected)selectedRow=focus;else availableRow=focus;
                if(horizontal!=0)panel=horizontal>0?Math.min(2,panel+1):panel-1;
                if(MenuInput.accept(input) && !entries.isEmpty()) draft=draft.select(entries.get(Math.min(focus,entries.size()-1)).id(),!selected,catalogue);
            }
            var pointer=MenuInput.pointer(input,width,224);
            if(pointer.leftPressed()) {
                for(int p=1;p<=2;p++) {
                    List<ChaosCatalog.Effect> entries=list(p==2);int focus=p==2?selectedRow:availableRow,first=Math.max(0,focus-7);
                    int left=p==1?8:width/2+2;
                    for(int i=first;i<Math.min(entries.size(),first+8);i++) if(pointer.over(left,83+(i-first)*12,width/2-14,12)) {
                        panel=p;if(p==2)selectedRow=i;else availableRow=i;draft=draft.select(entries.get(i).id(),p==1,catalogue);break;
                    }
                }
            }
            return;
        }
        List<String> rows=homeRows();
        if(MenuInput.down(input))row=Math.floorMod(row+1,rows.size());
        if(MenuInput.up(input))row=Math.floorMod(row-1,rows.size());
        if(MenuInput.accept(input)) activateHome();
        var pointer=MenuInput.pointer(input,width,224);
        if(pointer.leftPressed()) for(int i=0;i<rows.size();i++) if(pointer.over(32,68+i*18,width-64,18)) {row=i;activateHome();break;}
    }
    private boolean save() {
        if(!preferences.save(draft)) {message=preferences.error();controller.cue(true);return false;}
        controller.requestSettings(draft);return true;
    }
    private void activateHome() {
        if(row==1 && !title || row==2 && title) {configure=true;panel=0;row=0;return;}
        if(title) {
            if(row==0 || row==1) {draft=draft.enabled(row==0);if(save()){open=false;launch=true;controller.cue(false);}}
            else if(row==3) {if(preferences.reset()){draft=preferences.saved();controller.requestSettings(draft);message="Defaults saved.";}else message=preferences.error();}
            else if(row==4 && save()) {command=Command.RETURN_TO_HUB;waiting=true;}
        } else {
            if(row==0 && save()) {command=Command.RESUME;waiting=true;}
            else if(row==2) {draft=draft.enabled(!draft.enabled());if(save())message=draft.enabled()?"Chaos enabled; Resume to play.":"Chaos paused; active effects cleared.";}
            else if(row==3) {controller.requestClear();message="Active effects cleared.";}
            else if(row==4 && save()) {command=Command.FULL_RESTART;waiting=true;}
            else if(row==5 && save()) {command=Command.RETURN_TO_HUB;waiting=true;}
        }
    }
    public void draw(PixelCanvas canvas) {
        width=canvas.width();if(!open)return;
        canvas.fill(0,0,width,224,0xE7081429);
        CompactFont.draw(canvas,"SONIC3K CHAOS MODE",8,8,2,0xFFFFC95B);
        if(!configure) {
            var rows=homeRows();
            for(int i=0;i<rows.size();i++){if(i==row)canvas.fill(28,66+i*18,width-56,16,0xFF254D72);CompactFont.draw(canvas,rows.get(i),36,70+i*18,1,i==row?0xFFFFFFFF:0xFF97BCD5);}
        } else {
            String[] settings={"INTERVAL "+draft.intervalSeconds()+"S","DURATION "+draft.durationQuarters()/4.0+"X",
                    "FILTER "+(filter==0?"ALL":ChaosCatalog.Category.values()[filter-1]),"SEARCH "+search,"ADD ALL","REMOVE ALL","BACK"};
            for(int i=0;i<settings.length;i++)CompactFont.draw(canvas,(panel==0 && row==i?">":" ")+settings[i],8+(i%2)*(width/2),29+(i/2)*11,1,0xFFDFEAF3);
            drawList(canvas,false,8,availableRow);drawList(canvas,true,width/2+2,selectedRow);
            var entries=panel==2?list(true):list(false);int focus=panel==2?selectedRow:availableRow;
            if(panel!=0 && !entries.isEmpty()) {
                var effect=entries.get(Math.min(focus,entries.size()-1));
                String duration=effect.instant()?"ONE SHOT":effect.duration(draft.durationQuarters())/60+"S";
                CompactFont.draw(canvas,CompactFont.fit(effect.category()+" / "+effect.severity()+" / "+duration,width-16,1),8,184,1,0xFFFFCF65);
                String qualification=controller.qualification(effect);
                CompactFont.draw(canvas,CompactFont.fit(qualification.isEmpty()?effect.description():"Unavailable: "+qualification,width-16,1),8,197,1,0xFFB1CEE2);
            }
        }
        String footer=message.isEmpty() && configure?"ENTER ADD/REMOVE  L/R PANEL  ESC BACK":message;
        CompactFont.draw(canvas,CompactFont.fit(footer,width-16,1),8,212,1,0xFFFFCC80);
    }
    private void drawList(PixelCanvas canvas,boolean selected,int left,int focus) {
        int listWidth=width/2-14;var entries=list(selected);int first=Math.max(0,focus-7);
        CompactFont.draw(canvas,(selected?"SELECTED ":"AVAILABLE ")+entries.size(),left,72,1,0xFF75DFFE);
        for(int i=first;i<Math.min(entries.size(),first+8);i++) {
            int y=83+(i-first)*12;boolean current=panel==(selected?2:1) && i==focus;
            if(current)canvas.fill(left-2,y-2,listWidth+2,12,0xFF275573);
            CompactFont.draw(canvas,CompactFont.fit(entries.get(i).name(),listWidth,1),left,y,1,current?0xFFFFFFFF:0xFFA7CBDA);
        }
    }
}
