package chaos;

import com.openggf.control.*;
import com.openggf.configuration.*;
import com.openggf.game.LevelInputOverlay;
import com.openggf.mods.ui.*;
import java.util.*;

/** One title/pause menu; selected pool entries never toggle a backing definition permanently. */
public final class ChaosMenu implements LevelInputOverlay {
    /** Configuration controls in reading order; Up/Down visit them in this order. */
    private static final int INTERVAL=0,DURATION=1,SEARCH=2,FILTER=3,ADD=4,REMOVE=5,DONE=6,CONTROLS=7;
    private static final int ROW_HEIGHT=11,HOME_ROW=16;
    private final ChaosController controller;
    private final ChaosCatalog catalogue;
    private final ChaosPreferences preferences;
    private SonicConfigurationService configuration;
    private ChaosSettings draft;
    private boolean open,title,configure,launch,waiting,searchEditing,controllerHints;
    private int row,panel,availableRow,selectedRow,availableFirst,selectedFirst,filter,width=320,height=224;
    /** Menu-only animation clock, advanced with menu input rather than drawing. */
    private long age,messageAt;
    private String search="",message="",confirm="Enter",back="Esc",directions="Arrows",sideways="Left/Right";
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
    private String homeHelp(int index) {
        String every="a new surprise every "+draft.intervalSeconds()+" seconds";
        if(title) return switch(index) {
            case 0 -> "Play Sonic 3 & Knuckles with "+every+". Save select comes next.";
            case 1 -> "Plain Sonic 3 & Knuckles. You can switch Chaos on later from the pause menu.";
            case 2 -> "Choose which effects can appear, how often, and how long they last.";
            case 3 -> "Restore the 30 second interval, normal durations and the default effects.";
            default -> "Leave this mod and return to the OpenGGF hub.";
        };
        return switch(index) {
            case 0 -> "Back to the action. Any changes apply as you resume.";
            case 1 -> "Choose which effects can appear, how often, and how long they last.";
            case 2 -> draft.enabled()?"Stop new effects and clear the ones running now.":"Start drawing effects again: "+every+".";
            case 3 -> "End every running effect immediately. The timer keeps going.";
            case 4 -> "Start this act again from the beginning.";
            default -> "Leave the game and return to the OpenGGF hub.";
        };
    }
    private boolean filtered() {return filter!=0 || !search.isEmpty();}
    private List<ChaosCatalog.Effect> list(boolean selected) {
        return catalogue.effects().stream().filter(e->draft.selected().contains(e.id())==selected)
                .filter(e->filter==0 || e.category().ordinal()==filter-1)
                .filter(e->search.isEmpty() || (e.name()+" "+e.id()).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))).toList();
    }
    private int visibleRows() {return Math.max(3,(cardTop()-2-listTop()-16)/ROW_HEIGHT);}
    private int listTop() {return 62;}
    private int cardTop() {return height-11-44;}
    private int listLeft(boolean selected) {return selected?width/2+2:4;}
    private int listWidth() {return width/2-6;}
    /** First visible row that keeps focus onscreen; pure, so drawing never changes menu state. */
    private int first(boolean selected,int focus,int count) {
        int visible=visibleRows(),start=selected?selectedFirst:availableFirst;
        if(focus<start)start=focus;
        if(focus>=start+visible)start=focus-visible+1;
        return Math.clamp(start,0,Math.max(0,count-visible));
    }
    private void follow(boolean selected,int focus,int count) {
        int start=first(selected,focus,count);
        if(selected)selectedFirst=start;else availableFirst=start;
    }
    private void handle(InputHandler input) {
        if(input==null)return;
        age++;
        controllerHints=MenuInput.controller(input);confirm=MenuInput.confirmLabel(input);back=MenuInput.backLabel(input);
        directions=MenuInput.directionLabel(input);sideways=MenuInput.horizontalLabel(input);
        if(MenuInput.back(input)) {
            if(searchEditing) searchEditing=false;
            else if(configure) {configure=false;row=title?2:1;message="";}
            else if(title) {message="Choose Play normally or Return to hub.";}
            else message="Choose Resume play to apply settings.";
            controller.cue(false);return;
        }
        if(configure) {
            if(searchEditing) {
                String typed=MenuInput.consumeText(input);
                for(int i=0;i<typed.length() && search.length()<24;i++) if(!Character.isISOControl(typed.charAt(i)))search+=typed.charAt(i);
                if(MenuInput.textKeyRepeated(input,259) && !search.isEmpty())search=search.substring(0,search.length()-1);
                if(MenuInput.accept(input))searchEditing=false;
                availableRow=selectedRow=availableFirst=selectedFirst=0;
                return;
            }
            int horizontal=(MenuInput.right(input)?1:0)-(MenuInput.left(input)?1:0);
            int vertical=(MenuInput.down(input)?1:0)-(MenuInput.up(input)?1:0);
            if(panel==0) {
                if(vertical!=0)row=Math.floorMod(row+vertical,CONTROLS);
                if(horizontal!=0) {
                    if(row==INTERVAL)draft=draft.interval(Math.clamp(draft.intervalSeconds()+horizontal*5,5,120));
                    else if(row==DURATION)draft=draft.multiplier(Math.clamp(draft.durationQuarters()+horizontal,1,12));
                    else if(row==FILTER) {filter=Math.floorMod(filter+horizontal,4);availableRow=selectedRow=availableFirst=selectedFirst=0;}
                    else panel=horizontal>0?1:2;
                }
                if(MenuInput.accept(input)) activateControl(row);
            } else {
                boolean selected=panel==2;List<ChaosCatalog.Effect> entries=list(selected);
                int focus=selected?selectedRow:availableRow;
                if(!entries.isEmpty()) focus=Math.floorMod(Math.min(focus,entries.size()-1)+vertical,entries.size());
                if(selected)selectedRow=focus;else availableRow=focus;
                follow(selected,focus,entries.size());
                // Settings, Available and Selected form one Left/Right cycle.
                if(horizontal!=0)panel=Math.floorMod(panel+horizontal,3);
                else if(MenuInput.accept(input) && !entries.isEmpty()) move(entries.get(Math.min(focus,entries.size()-1)),!selected);
            }
            pointerConfigure(MenuInput.pointer(input,width,height));
            return;
        }
        List<String> rows=homeRows();
        if(MenuInput.down(input))row=Math.floorMod(row+1,rows.size());
        if(MenuInput.up(input))row=Math.floorMod(row-1,rows.size());
        if(MenuInput.accept(input)) activateHome();
        var pointer=MenuInput.pointer(input,width,height);
        if(pointer.leftPressed()) for(int i=0;i<rows.size();i++) if(pointer.over(homeLeft(),homeTop()+i*HOME_ROW,homeWidth(),HOME_ROW)) {row=i;activateHome();break;}
    }
    private void move(ChaosCatalog.Effect effect,boolean include) {
        draft=draft.select(effect.id(),include,catalogue);
        message=(include?"Added ":"Removed ")+effect.name();messageAt=age;
        for(int p=1;p<=2;p++) {
            boolean selected=p==2;int count=list(selected).size();int focus=Math.clamp(selected?selectedRow:availableRow,0,Math.max(0,count-1));
            if(selected)selectedRow=focus;else availableRow=focus;
            follow(selected,focus,count);
        }
    }
    private void activateControl(int control) {
        if(control==SEARCH) {searchEditing=true;message="";}
        else if(control==ADD || control==REMOVE) {
            boolean add=control==ADD;var next=new LinkedHashSet<>(draft.selected());
            var shown=filtered()?list(!add).stream().map(ChaosCatalog.Effect::id).toList():catalogue.effects().stream().map(ChaosCatalog.Effect::id).toList();
            if(add)next.addAll(shown);else shown.forEach(next::remove);
            draft=new ChaosSettings(draft.intervalSeconds(),draft.durationQuarters(),next,draft.enabled(),draft.seed());
            message=(add?"Added ":"Removed ")+shown.size()+(shown.size()==1?" effect":" effects");messageAt=age;
            availableRow=selectedRow=availableFirst=selectedFirst=0;
        }
        else if(control==DONE) {configure=false;row=title?2:1;message="";}
    }
    private void pointerConfigure(MenuInput.Pointer pointer) {
        if(!pointer.leftPressed())return;
        for(int control=0;control<CONTROLS;control++) if(pointer.over(controlX(control)-3,controlY(control)-2,controlWidth(control)+6,11)) {
            panel=0;row=control;if(control>=SEARCH && control!=FILTER)activateControl(control);return;
        }
        for(int p=1;p<=2;p++) {
            boolean selected=p==2;List<ChaosCatalog.Effect> entries=list(selected);int focus=selected?selectedRow:availableRow;
            int start=first(selected,Math.min(focus,Math.max(0,entries.size()-1)),entries.size()),left=listLeft(selected);
            for(int i=start;i<Math.min(entries.size(),start+visibleRows());i++) if(pointer.over(left,listTop()+13+(i-start)*ROW_HEIGHT,listWidth(),ROW_HEIGHT)) {
                panel=p;if(selected)selectedRow=i;else availableRow=i;move(entries.get(i),!selected);return;
            }
        }
    }
    private boolean save() {
        if(!preferences.save(draft)) {message=preferences.error();messageAt=age;controller.cue(true);return false;}
        controller.requestSettings(draft);return true;
    }
    private void activateHome() {
        if(row==1 && !title || row==2 && title) {configure=true;panel=0;row=0;message="";return;}
        if(title) {
            if(row==0 || row==1) {draft=draft.enabled(row==0);if(save()){open=false;launch=true;controller.cue(false);}}
            else if(row==3) {if(preferences.reset()){draft=preferences.saved();controller.requestSettings(draft);message="Defaults restored and saved.";}else message=preferences.error();}
            else if(row==4 && save()) {command=Command.RETURN_TO_HUB;waiting=true;}
        } else {
            if(row==0 && save()) {command=Command.RESUME;waiting=true;}
            else if(row==2) {draft=draft.enabled(!draft.enabled());if(save())message=draft.enabled()?"Chaos enabled. Resume to play.":"Chaos paused. Active effects cleared.";}
            else if(row==3) {controller.requestClear();message="Active effects cleared.";}
            else if(row==4 && save()) {command=Command.FULL_RESTART;waiting=true;}
            else if(row==5 && save()) {command=Command.RETURN_TO_HUB;waiting=true;}
        }
    }
    // --- Drawing: reads menu/draft/schedule state only. ---
    private int homeWidth() {return Math.min(width-48,232);}
    private int homeLeft() {return (width-homeWidth())/2;}
    private int homeTop() {return title?56:66;}
    private String quarters() {
        int q=draft.durationQuarters();String whole=Integer.toString(q/4);
        return (q%4==0?whole:whole+"."+switch(q%4){case 1->"25";case 2->"5";default->"75";})+"X";
    }
    public void draw(PixelCanvas canvas) {
        width=canvas.width();height=canvas.height();if(!open)return;
        canvas.fill(0,0,width,height,0xC8040914);
        UiPrimitives.gradient(canvas,0,0,width,configure?20:38,0xF0183A78,0xF0081A3C);
        canvas.fill(0,configure?20:38,width,1,ChaosUi.GOLD);
        if(configure) drawConfigure(canvas); else drawHome(canvas);
        // Configuration feedback replaces the hint line briefly; home messages stay above the hints.
        boolean recent=!message.isEmpty() && age-messageAt<150;
        if(configure) ChaosUi.centred(canvas,ChaosUi.fit(recent?message:hint(),width-8,1),width/2,height-9,1,recent?ChaosUi.WARN:ChaosUi.DIM);
        else {
            if(!message.isEmpty())ChaosUi.centred(canvas,ChaosUi.fit(message,width-16,1),width/2,height-21,1,ChaosUi.WARN);
            ChaosUi.centred(canvas,ChaosUi.fit(hint(),width-8,1),width/2,height-10,1,ChaosUi.DIM);
        }
    }
    private String hint() {
        if(waiting) return "Please wait...";
        // Short enough for a 320-pixel viewport; labels follow the active keyboard or controller.
        String choose=(controllerHints?"D-Pad":directions)+" choose";
        if(!configure) return choose+"  "+confirm+" select";
        if(searchEditing) return "Type to search  Backspace delete  Enter done";
        if(panel==0) return (row<=DURATION || row==FILTER?choose+"  "+sideways+" change":confirm+" select  "+sideways+" lists")+"  "+back+" done";
        return confirm+(panel==1?" add":" remove")+"  "+sideways+" lists  "+back+" done";
    }
    private void drawHome(PixelCanvas canvas) {
        ChaosUi.centred(canvas,"CHAOS MODE",width/2,8,3,ChaosUi.GOLD);
        var schedule=controller.schedule();
        String status;
        if(title) status="Sonic 3 & Knuckles  -  "+catalogue.effects().size()+" ways to ruin your day";
        else if(!schedule.settings().enabled()) status="Paused  -  Chaos is off";
        else status="Paused  -  next effect in "+ChaosUi.seconds(schedule.countdown());
        ChaosUi.centred(canvas,ChaosUi.fit(status,width-16,1),width/2,43,1,ChaosUi.CYAN);
        if(!title && schedule.settings().enabled() && !schedule.active().isEmpty()) {
            var names=new StringBuilder("Now: ");
            for(var value:schedule.active()) {
                if(names.length()>5)names.append(", ");
                names.append(catalogue.effect(value.effect()).name());
                if(value.expires()>0)names.append(' ').append(ChaosUi.seconds(Math.max(0,value.expires()-schedule.clock())));
            }
            ChaosUi.centred(canvas,ChaosUi.fit(names.toString(),width-16,1),width/2,53,1,ChaosUi.TEXT);
        }
        var rows=homeRows();int left=homeLeft(),top=homeTop(),w=homeWidth();
        ChaosUi.panel(canvas,left-4,top-4,w+8,rows.size()*HOME_ROW+6,ChaosUi.NAVY,ChaosUi.EDGE);
        for(int i=0;i<rows.size();i++) {
            int y=top+i*HOME_ROW;boolean focus=i==row;
            if(focus) {
                UiPrimitives.gradient(canvas,left,y,w,HOME_ROW-2,0xF03C6CDA,0xF01A3890);
                UiPrimitives.frame(canvas,left,y,w,HOME_ROW-2,ChaosUi.alpha(ChaosUi.GOLD,140+(int)(115*ChaosUi.pulse(age,48))));
                ChaosUi.text(canvas,">",left+6,y+3,1,ChaosUi.GOLD);
            }
            ChaosUi.text(canvas,rows.get(i),left+18,y+3,1,focus?ChaosUi.WHITE:ChaosUi.DIM);
        }
        int helpTop=top+rows.size()*HOME_ROW+8;
        var help=ChaosUi.wrap(homeHelp(row),width-32,2);
        for(int i=0;i<help.size();i++)ChaosUi.centred(canvas,help.get(i),width/2,helpTop+i*10,1,ChaosUi.TEXT);
        String summary="Every "+draft.intervalSeconds()+"s  /  "+quarters()+" duration  /  "+draft.selected().size()+" of "+catalogue.effects().size()+" effects";
        if(helpTop+24<height-24)ChaosUi.centred(canvas,ChaosUi.fit(summary,width-16,1),width/2,helpTop+24,1,ChaosUi.GOLD);
    }
    private int controlX(int control) {
        int column=width/2+6;
        return switch(control) {case INTERVAL,SEARCH,ADD->12;case REMOVE->12+CompactFont.width(controlLabel(ADD),1)+12;default->column;};
    }
    private int controlY(int control) {return 26+switch(control) {case INTERVAL,DURATION->0;case SEARCH,FILTER->11;default->22;};}
    private int controlWidth(int control) {return CompactFont.width(controlLabel(control),1);}
    private String controlLabel(int control) {
        boolean focus=panel==0 && row==control;
        String filterName=filter==0?"ALL":ChaosCatalog.Category.values()[filter-1].name();
        return switch(control) {
            case INTERVAL -> "INTERVAL "+(focus?"< "+draft.intervalSeconds()+"S >":draft.intervalSeconds()+"S");
            case DURATION -> "DURATION "+(focus?"< "+quarters()+" >":quarters());
            case FILTER -> "FILTER "+(focus?"< "+filterName+" >":filterName);
            case SEARCH -> "SEARCH: "+(search.isEmpty() && !searchEditing?"-":ChaosUi.fit(search,72,1))+(searchEditing && ChaosUi.pulse(age,30)>0.5?"_":"");
            case ADD -> filtered()?"ADD SHOWN":"ADD ALL";
            case REMOVE -> filtered()?"REMOVE SHOWN":"REMOVE ALL";
            default -> "DONE";
        };
    }
    private String controlHelp(int control) {
        return switch(control) {
            case INTERVAL -> "Time between effects: a new one every "+draft.intervalSeconds()+" seconds (5-120).";
            case DURATION -> "Effect length: "+quarters()+" each effect's normal duration (0.25-3X).";
            case FILTER -> "Show only one kind of effect in both lists.";
            case SEARCH -> searchEditing?"Type part of an effect name.":"Press "+confirm+" and type part of an effect name.";
            case ADD -> filtered()?"Select every effect currently shown in Available.":"Select every effect in the catalogue.";
            case REMOVE -> filtered()?"Deselect every effect currently shown in Selected.":"Empty the pool. Chaos waits until something is selected.";
            default -> "Back to the Chaos menu. Settings save when you start or resume.";
        };
    }
    private void drawConfigure(PixelCanvas canvas) {
        ChaosUi.text(canvas,"CONFIGURE CHAOS",8,3,2,ChaosUi.GOLD);
        ChaosUi.right(canvas,draft.selected().size()+"/"+catalogue.effects().size()+" ON",width-6,7,1,ChaosUi.WHITE);
        ChaosUi.panel(canvas,4,22,width-8,36,ChaosUi.NAVY,ChaosUi.EDGE);
        for(int control=0;control<CONTROLS;control++) {
            boolean focus=panel==0 && row==control;int x=controlX(control),y=controlY(control);
            String label=controlLabel(control);
            if(focus) {
                int w=CompactFont.width(label,1)+6;
                UiPrimitives.gradient(canvas,x-3,y-2,w,11,0xF03C6CDA,0xF01A3890);
                UiPrimitives.frame(canvas,x-3,y-2,w,11,ChaosUi.alpha(ChaosUi.GOLD,140+(int)(115*ChaosUi.pulse(age,48))));
            }
            ChaosUi.text(canvas,label,x,y,1,focus?ChaosUi.WHITE:control==SEARCH && searchEditing?ChaosUi.GOLD:ChaosUi.TEXT);
        }
        drawList(canvas,false);drawList(canvas,true);
        int top=cardTop();ChaosUi.panel(canvas,4,top,width-8,44,ChaosUi.NAVY_DEEP,ChaosUi.alpha(ChaosUi.GOLD,200));
        if(panel==0) {
            ChaosUi.text(canvas,controlLabel(row).replace("< ","").replace(" >",""),10,top+5,1,ChaosUi.GOLD);
            var lines=ChaosUi.wrap(controlHelp(row),width-20,2);
            for(int i=0;i<lines.size();i++)ChaosUi.text(canvas,lines.get(i),10,top+17+i*10,1,ChaosUi.TEXT);
            return;
        }
        var entries=list(panel==2);int focus=panel==2?selectedRow:availableRow;
        if(entries.isEmpty()) {ChaosUi.text(canvas,filtered()?"No effects match this filter.":"This list is empty.",10,top+5,1,ChaosUi.DIM);return;}
        var effect=entries.get(Math.min(focus,entries.size()-1));
        String duration=effect.instant()?"INSTANT":ChaosUi.seconds(effect.duration(draft.durationQuarters()));
        ChaosUi.text(canvas,ChaosUi.fit(effect.name(),width-80,1),10,top+5,1,ChaosUi.GOLD);
        ChaosUi.right(canvas,duration,width-10,top+5,1,ChaosUi.WHITE);
        int x=10;
        ChaosUi.text(canvas,effect.category().name(),x,top+15,1,ChaosUi.category(effect.category()));
        x+=CompactFont.width(effect.category().name()+" ",1);
        ChaosUi.text(canvas,"/ "+effect.severity().name(),x,top+15,1,ChaosUi.severity(effect.severity()));
        String qualification=controller.qualification(effect);
        var lines=ChaosUi.wrap(effect.description(),width-20,qualification.isEmpty()?2:1);
        for(int i=0;i<lines.size();i++)ChaosUi.text(canvas,lines.get(i),10,top+25+i*10,1,ChaosUi.TEXT);
        if(!qualification.isEmpty())ChaosUi.text(canvas,ChaosUi.fit("Unavailable here: "+qualification,width-20,1),10,top+35,1,ChaosUi.WARN);
    }
    private void drawList(PixelCanvas canvas,boolean selected) {
        int left=listLeft(selected),w=listWidth(),top=listTop(),visible=visibleRows();var entries=list(selected);
        boolean active=panel==(selected?2:1);int focus=selected?selectedRow:availableRow;
        int start=first(selected,Math.min(focus,Math.max(0,entries.size()-1)),entries.size());
        ChaosUi.panel(canvas,left,top,w,13+visible*ROW_HEIGHT+3,ChaosUi.NAVY,active?ChaosUi.GOLD:ChaosUi.EDGE);
        ChaosUi.text(canvas,selected?"SELECTED":"AVAILABLE",left+5,top+3,1,active?ChaosUi.GOLD:ChaosUi.CYAN);
        String counter=entries.isEmpty()?"0":(Math.min(focus,entries.size()-1)+1)+"/"+entries.size();
        ChaosUi.right(canvas,counter,left+w-6,top+3,1,ChaosUi.DIM);
        if(entries.isEmpty()) ChaosUi.text(canvas,filtered()?"No matches":selected?"Nothing selected":"All selected",left+8,top+16,1,ChaosUi.DIM);
        for(int i=start;i<Math.min(entries.size(),start+visible);i++) {
            int y=top+13+(i-start)*ROW_HEIGHT;boolean current=i==Math.min(focus,entries.size()-1);
            var effect=entries.get(i);
            if(current && active) UiPrimitives.gradient(canvas,left+2,y,w-7,ROW_HEIGHT,0xF03C6CDA,0xF01A3890);
            else if(current) UiPrimitives.frame(canvas,left+2,y,w-7,ROW_HEIGHT,0x804F7FC8);
            canvas.fill(left+5,y+3,3,5,ChaosUi.severity(effect.severity()));
            ChaosUi.text(canvas,ChaosUi.fit(effect.name(),w-21,1),left+11,y+2,1,
                    current && active?ChaosUi.WHITE:controller.qualification(effect).isEmpty()?ChaosUi.TEXT:ChaosUi.DIM);
        }
        if(entries.size()>visible) {
            int track=visible*ROW_HEIGHT,thumb=Math.max(6,track*visible/entries.size());
            int y=top+13+(track-thumb)*start/Math.max(1,entries.size()-visible);
            canvas.fill(left+w-4,top+13,2,track,0x40FFFFFF);canvas.fill(left+w-4,y,2,thumb,0xC0FFFFFF);
        }
    }
}
