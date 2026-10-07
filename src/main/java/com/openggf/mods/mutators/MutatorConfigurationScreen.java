package com.openggf.mods.mutators;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.*;
import com.openggf.game.session.*;
import com.openggf.graphics.*;
import java.util.*;

/** Shared bounded title/configuration UI. Settings admission stays inside the host, not creator code. */
@ModApi
public final class MutatorConfigurationScreen implements TitleScreenProvider, LevelInputOverlay {
    @ModApi public enum Cue { NAVIGATE, CONFIRM, START, ERROR }
    private enum Page { HOME, HELP, LIST, OPTIONS }
    private enum Action { MUTATOR, ENABLE, OPTION, START, RESUME, RESTART, RESET, HUB, BACK, CONFIGURE, HELP }
    private record Row(String key, String option, String label, Action action) { }
    private final WorldSession world;
    private final MutatorSessionState settings;
    private final TitleScreenProvider backdrop;
    private final String title;
    private final java.util.function.Consumer<Cue> cues;
    private final InputHandler neutral = new InputHandler();
    private MenuPixelFont font;
    private TexturedQuadRenderer renderer;
    private State state = State.INACTIVE;
    private Page page = Page.HOME;
    private int row, age, pageAge;
    private String selectedKey, status = "", hint = "Arrows / Enter / Esc";
    private boolean open, inGameplay, waitingForFade;
    private Command command = Command.NONE;
    private float highlight;
    private InputHandler lastInput;

    public MutatorConfigurationScreen(WorldSession world, String title, TitleScreenProvider backdrop,
                                      java.util.function.Consumer<Cue> cues) {
        this.world = Objects.requireNonNull(world);
        this.title = Objects.requireNonNull(title);
        if (title.isBlank() || title.length() > 28) throw new IllegalArgumentException("Title must be 1..28 characters");
        this.backdrop = backdrop;
        this.cues = Objects.requireNonNull(cues);
        settings = Objects.requireNonNull(MutatorWorldAccess.state(world), "No prepared mutator catalog in this world");
        MutatorWorldAccess.ownScreen(world, this);
    }

    @Override public void initialize() {
        if (backdrop != null) backdrop.initialize();
        state = State.ACTIVE; page = Page.HOME; row = age = pageAge = 0;
        highlight = 0; open = false; inGameplay = false; waitingForFade = false; command = Command.NONE;
        status = MutatorWorldAccess.saveError(world);
    }
    @Override public void reset() { state = State.INACTIVE; open = false; }
    @Override public State getState() { return state; }
    @Override public boolean isExiting() { return state == State.EXITING; }
    @Override public boolean isActive() { return state != State.INACTIVE; }
    @Override public boolean ownsEscapeInput() { return usable(); }
    @Override public TitleScreenAction consumeExitAction() { return TitleScreenAction.ONE_PLAYER; }
    @Override public int startZoneIndex() { return 0; }
    @Override public int startActIndex() { return 0; }
    @Override public void setClearColor() { if (backdrop != null) backdrop.setClearColor(); }

    @Override public void update(InputHandler input) {
        if (state != State.ACTIVE || !usable()) return;
        if (backdrop != null) backdrop.update(neutral);
        animate();
        if (!waitingForFade && (backdrop == null || backdrop.getState() == State.ACTIVE)) handle(input);
    }

    @Override public boolean handleInput(InputHandler input) {
        if (!usable() || input == null) return false;
        inGameplay = true; lastInput = input;
        boolean pause = input.isKeyPressed(GameServices.configuration().getInt(
                com.openggf.configuration.SonicConfiguration.PAUSE_KEY)) || input.logical().player1().startPressed();
        // The configuration menu owns Start and Escape together. Movie/trace sessions never receive its catalog.
        if (!open) {
            if (!pause || GameServices.level().hasPendingFreshLevelTransitionBoundary()) return false;
            open = true; switchPage(Page.LIST); status = MutatorWorldAccess.admissionError(world).isBlank()
                    ? "Play held. Edit settings, then choose Resume." : MutatorWorldAccess.admissionError(world);
            sound(Cue.NAVIGATE); return true;
        }
        animate();
        if (!waitingForFade) handle(input);
        return true;
    }
    @Override public boolean pausesGameplay() { return open; }
    @Override public Command consumeCommand() { Command next = command; command = Command.NONE; return next; }
    @Override public void commandQueued(boolean waiting) {
        // A title-to-hub acceptance starts its exit fade; keep title input held until retirement.
        waitingForFade = waiting || !inGameplay;
        if (waiting) { open = true; status = "Waiting for the current transition..."; }
        else { open = false; status = inGameplay ? "" : "Returning to game hub..."; }
    }

    private boolean usable() { return !settings.isClosed() && SessionManager.getCurrentWorldSession() == world; }
    private void animate() { age++; pageAge++; highlight += (row - highlight) * .45f; }
    private void switchPage(Page next) { page = next; row = 0; pageAge = 0; highlight = 0; }
    private List<Row> rows() {
        var rows = new ArrayList<Row>();
        if (page == Page.HOME) {
            rows.add(new Row(null,null,"Start Emerald Hill",Action.START));
            rows.add(new Row(null,null,"Configure mutators",Action.CONFIGURE));
            rows.add(new Row(null,null,"How to play",Action.HELP));
            return rows;
        }
        if (page == Page.HELP) return List.of(new Row(null,null,"Back",Action.BACK));
        if (page == Page.OPTIONS) {
            var owned = definition(selectedKey); var d = owned.definition();
            rows.add(new Row(selectedKey,null,d.title(),Action.ENABLE));
            for (var option : d.options()) rows.add(new Row(selectedKey,option.id(),option.label(),Action.OPTION));
            rows.add(new Row(null,null,"Back to mutators",Action.BACK));
            return rows;
        }
        for (var owned : settings.definitions()) rows.add(new Row(owned.key(),null,owned.definition().title(),Action.MUTATOR));
        rows.add(new Row(null,null,inGameplay ? "Resume play" : "Start Emerald Hill",inGameplay ? Action.RESUME : Action.START));
        if (inGameplay) rows.add(new Row(null,null,"Restart from act start",Action.RESTART));
        rows.add(new Row(null,null,"Reset to defaults",Action.RESET));
        rows.add(new Row(null,null,inGameplay ? "Return to game hub" : "Back to title",inGameplay ? Action.HUB : Action.BACK));
        return List.copyOf(rows);
    }
    private OwnedMutator definition(String key) {
        return settings.definitions().stream().filter(owned -> owned.key().equals(key)).findFirst().orElseThrow();
    }

    private void handle(InputHandler input) {
        if (input == null) return;
        lastInput = input;
        hint = MenuInput.directionLabel(input) + " choose  " + MenuInput.confirmLabel(input) + " select  "
                + MenuInput.backLabel(input) + (page == Page.HOME && !inGameplay ? " hub" : " back");
        // A simultaneous cancel/confirm never admits changes.
        if (MenuInput.back(input)) { back(); sound(Cue.NAVIGATE); return; }
        var choices = rows();
        int vertical = (MenuInput.down(input) ? 1 : 0) - (MenuInput.up(input) ? 1 : 0);
        int horizontal = (MenuInput.right(input) ? 1 : 0) - (MenuInput.left(input) ? 1 : 0);
        if (vertical != 0) { row = Math.floorMod(row + vertical, choices.size()); sound(Cue.NAVIGATE); }
        Row selected = choices.get(Math.min(row, choices.size() - 1));
        if (horizontal != 0 && (selected.action() == Action.OPTION || selected.action() == Action.ENABLE
                || selected.action() == Action.MUTATOR)) edit(selected, horizontal);
        if (MenuInput.accept(input)) activate(selected);
        // Pointer uses the same native grid and the same actions as keyboard/controller controls.
        var pointer = MenuInput.pointer(input, width(), 224);
        int top = rowsTop(), height = rowHeight(), first = firstVisibleRow(), count = Math.min(capacity(), choices.size()-first);
        if (pointer.leftPressed()) for (int i=0;i<count;i++) {
            if (pointer.over(left()+8,top+i*height,panelWidth()-16,height)) {
                row=first+i; activate(choices.get(row)); break;
            }
        }
    }
    private void back() {
        if (page == Page.HOME && !inGameplay) activate(new Row(null,null,"Return to game hub",Action.HUB));
        else if (page == Page.OPTIONS) switchPage(Page.LIST);
        else if (page == Page.HELP || !inGameplay && page == Page.LIST) switchPage(Page.HOME);
        else if (inGameplay) { status = "Choose Resume to apply. Esc keeps play held."; }
    }
    private void activate(Row item) {
        switch(item.action()) {
            case CONFIGURE -> { switchPage(Page.LIST); sound(Cue.CONFIRM); }
            case HELP -> { switchPage(Page.HELP); sound(Cue.CONFIRM); }
            case MUTATOR -> { selectedKey=item.key(); switchPage(Page.OPTIONS); sound(Cue.CONFIRM); }
            case ENABLE, OPTION -> edit(item,1);
            case BACK -> back();
            case RESET -> { settings.resetDefaults(); status="Defaults requested. Choose Start or Resume."; save(); }
            case START -> {
                if (!save()) break;
                var admission=MutatorWorldAccess.prepareLaunch(world);
                if (!admission.accepted()) { status=admission.message(); sound(Cue.ERROR); break; }
                state=State.EXITING; open=false;
                status="Loading Emerald Hill..."; sound(Cue.START);
            }
            case RESUME -> {
                if (!save()) break;
                var admission=settings.boundary(MutatorScope.LIVE);
                if (!admission.accepted()) { status=admission.message(); sound(Cue.ERROR); }
                else { command=Command.RESUME; waitingForFade=true; sound(Cue.NAVIGATE); }
            }
            case RESTART -> {
                if (!save()) break;
                var admission=settings.previewBoundary(MutatorScope.LOAD, MutatorSessionState.LoadCause.FULL_RESTART);
                if (!admission.accepted()) { status=admission.message(); sound(Cue.ERROR); }
                else { command=Command.FULL_RESTART; status="Restart requested..."; waitingForFade=true; }
            }
            case HUB -> {
                if (!save()) break;
                command=Command.RETURN_TO_HUB; status="Returning to game hub..."; waitingForFade=true;
            }
        }
    }
    private void edit(Row item, int direction) {
        var d=definition(item.key()).definition();
        var value=settings.requested().get(item.key());
        if (item.option()==null) settings.requestEnabled(item.key(),!value.enabled());
        else {
            var option=d.option(item.option()); Object old=value.options().get(item.option());
            Object next=switch(option) {
                case MutatorOption.IntegerSlider slider -> {
                    long last = slider.minimum() + ((long)slider.maximum()-slider.minimum())/slider.step()*slider.step();
                    yield (int)Math.clamp((long)(Integer)old+(long)direction*slider.step(), (long)slider.minimum(), last);
                }
                case MutatorOption.Checkbox checkbox -> !(Boolean)old;
                case MutatorOption.Choice choice -> choice.tokens().get(Math.floorMod(choice.tokens().indexOf(old)+direction,choice.tokens().size()));
            };
            settings.requestOption(item.key(),item.option(),next);
        }
        status="Requested. Play changes only at the shown boundary.";
        save(); sound(Cue.NAVIGATE);
    }
    private boolean save() {
        if (MutatorWorldAccess.save(world)) return true;
        status="Save failed. Draft kept; retry.";
        sound(Cue.ERROR);
        return false;
    }
    private void sound(Cue cue) { cues.accept(cue); }

    @Override public void draw() {
        if (state==State.INACTIVE) return;
        if (backdrop!=null && page==Page.HOME) {
            backdrop.draw();
            if (backdrop.getState()!=State.ACTIVE) return;
        }
        render(false);
    }
    @Override public void drawOverlay() {
        if (open) render(true);
    }
    private void ensureFont() {
        if (font!=null) return;
        renderer=new TexturedQuadRenderer();
        font=new MenuPixelFont();
        try { renderer.init(); font.init("pixel-font.png",renderer); }
        catch(java.io.IOException failure) { close(); throw new IllegalStateException("Menu font unavailable",failure); }
    }
    private int width() { return Math.max(320,GameServices.graphics().getProjectionWidth()); }
    private int panelWidth() { return Math.min(376,width()-24); }
    private int left() { return (width()-panelWidth())/2; }
    private int rowsTop() { return page==Page.HOME ? 140 : page==Page.HELP ? 176 : 58; }
    private int rowHeight() { return page==Page.HOME || page==Page.HELP ? 15 : 24; }
    private int capacity() { return page==Page.HOME?3:5; }
    private int firstVisibleRow() { return Math.max(0,row-capacity()+1); }
    private void render(boolean overlay) {
        var graphics=GameServices.graphics();
        if (graphics.isHeadlessMode()) return;
        graphics.flushScreenSpace(); graphics.resetForFixedFunction(); ensureFont();
        renderer.setProjectionMatrix(graphics.getProjectionMatrixBuffer());
        int w=width(), x=left(), pw=panelWidth();
        font.beginMegaBatch();
        if (page!=Page.HOME || overlay) {
            MenuStyle.fill(font,0,0,w,224,.015f,.035f,.08f,overlay?.85f:1);
            MenuStyle.checkerboard(font,w);
        }
        int inset=Math.max(0,12-pageAge*2);
        MenuStyle.fill(font,x,8+inset,pw,27,.018f,.075f,.15f,.94f);
        MenuStyle.label(font,title,x+10,14+inset,pw-20,.55f,1,.9f);
        MenuStyle.fill(font,x,37,pw,14,.018f,.075f,.15f,.94f);
        MenuStyle.text(font,inGameplay && !MutatorWorldAccess.supportedCell(world)
                ? "Outside supported cell / effects suspended" : "Sonic 2 / Emerald Hill 1 / solo Sonic",x+10,40,pw-20,.76f,.86f,1);
        if (page==Page.HOME) {
            MenuStyle.fill(font,x,115,pw,22,.015f,.07f,.13f,.94f);
            MenuStyle.text(font,"Choose your gravity. Stay in control.",x+10,119,pw-20,.55f,1,.9f);
            MenuStyle.text(font,"Dry Gravity + selective Stealth",x+10,129,pw-20,1,.86f,.45f);
        } else if (page==Page.HELP) {
            String[] help={"Left / Right: move. A / B / C: jump.","Start or Pause: configure.","Resume applies live edits.","Restart rebuilds from the act start.","Gravity changes dry air acceleration.","Jump impulse stays native.","Stealth keeps collision, sound and HUD.","Return to hub for a new session."};
            for(int i=0;i<help.length;i++) MenuStyle.text(font,help[i],x+10,61+i*13,pw-20,.83f,.9f,1);
        }
        var visible=rows(); int top=rowsTop(), rh=rowHeight();
        // Schema bounds can exceed one page; keep the focused row and its neighbors visible.
        int capacity=capacity(), first=firstVisibleRow();
        int count=Math.min(capacity,visible.size()-first);
        for(int i=0;i<count;i++) {
            Row item=visible.get(first+i); int y=top+i*rh;
            MenuStyle.fill(font,x+5,y,pw-10,rh-2,.025f,.1f,.19f,.94f);
            if(first+i==row) MenuStyle.focus(font,x+5,top+Math.round((Math.clamp(highlight,(float)first,(float)(first+count-1))-first)*rh),pw-10,rh-2);
            MenuStyle.text(font,item.label(),x+12,y+3,pw-100,1,1,1);
            String val=value(item); MenuStyle.text(font,val,x+pw-90,y+3,78,.5f,1,.82f);
            var option=item.option()==null?null:definition(item.key()).definition().option(item.option());
            String scope=scope(item); if(!scope.isEmpty()) MenuStyle.text(font,scope,x+12,y+13,
                    option instanceof MutatorOption.IntegerSlider?pw-100:pw-24,.74f,.83f,.96f);
            if(option instanceof MutatorOption.IntegerSlider slider) {
                int number=(Integer)settings.requested().get(item.key()).options().get(item.option());
                int trackWidth=60;
                long span=Math.max(1L,(long)slider.maximum()-slider.minimum());
                int fill=(int)(((long)number-slider.minimum())*trackWidth/span);
                MenuStyle.fill(font,x+pw-83,y+17,trackWidth,2,.15f,.25f,.36f,1);
                MenuStyle.fill(font,x+pw-83,y+17,Math.max(1,fill),2,.4f,1,.8f,1);
            }
        }
        String detail=status;
        if(page==Page.OPTIONS && row<visible.size()) {
            Row current=visible.get(row);
            if(current.option()!=null && status.isBlank()) detail=definition(current.key()).definition().option(current.option()).help();
        }
        if(detail.isBlank()) detail=settings.pending().isEmpty()?"No pending edits. Stock defaults are off.":settings.pending().size()+" edit(s) waiting for their boundary.";
        if(page!=Page.HELP) {
            MenuStyle.fill(font,x,184,pw,14,.018f,.075f,.15f,.94f);
            MenuStyle.text(font,detail,x+8,185,pw-16,1,.83f,.45f);
        }
        MenuStyle.fill(font,0,198,w,26,.008f,.02f,.04f,1);
        MenuStyle.text(font,hint,x+8,201,pw-16,.68f,.89f,1);
        MenuStyle.text(font,inGameplay?"LIVE: Resume / LOAD: Restart / LAUNCH: new session":"Prepared package / effects off until enabled",x+8,213,pw-16,.75f,.84f,.96f);
        font.endMegaBatch();
    }
    private String value(Row item) {
        if(item.key()==null) return "";
        var requested=settings.requested().get(item.key());
        if(item.option()==null) return requested.enabled()?"[x] On":"[ ] Off";
        Object value=requested.options().get(item.option());
        var option=definition(item.key()).definition().option(item.option());
        return switch(option) {
            case MutatorOption.Checkbox checkbox -> (Boolean)value?"[x] On":"[ ] Off";
            case MutatorOption.IntegerSlider slider -> value+slider.unit();
            case MutatorOption.Choice choice -> ((String)value).replace('_',' ');
        };
    }
    private String scope(Row item) {
        if(item.key()==null) return "";
        var d=definition(item.key()).definition();
        var requested=settings.requested().get(item.key()); var admitted=settings.admitted().get(item.key());
        MutatorScope scope=item.option()==null?(requested.enabled()?d.enableScope():d.disableScope()):d.optionScope(item.option());
        var pendingEdit=settings.pending().stream().filter(p->p.key().equals(item.key()) && Objects.equals(p.optionId(),item.option())).findFirst();
        boolean pending=pendingEdit.isPresent();
        if (pending && pendingEdit.get().reason().contains("edit")) return "History differs / edit to apply";
        String boundary=switch(scope){case LIVE->inGameplay?"Resume":"Start";case LOAD->"full restart / death reload";case LAUNCH->"new session";};
        if(item.option()!=null && pending) return "Pending " + boundary + " / admitted: " + admitted.options().get(item.option());
        return (pending?"Pending ":"Applies at ")+boundary+(item.action()==Action.MUTATOR?" / Enter: options":"");
    }
    @Override public void close() {
        if(font!=null) font.cleanup(); if(renderer!=null) renderer.cleanup();
        font=null; renderer=null; open=false; waitingForFade=false; command=Command.NONE;
    }
}
