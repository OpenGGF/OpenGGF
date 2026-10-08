package com.openggf.mods.mutators;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.ButtonPrompts;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.*;
import com.openggf.game.session.*;
import com.openggf.graphics.*;
import java.util.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.*;

/** Shared bounded title/configuration UI. Settings admission stays inside the host, not creator code. */
@ModApi
public final class MutatorConfigurationScreen implements TitleScreenProvider, LevelInputOverlay {
    @ModApi public enum Cue { NAVIGATE, CONFIRM, START, ERROR }
    private enum Page { HOME, HELP, LIST, OPTIONS }
    private enum Action { MUTATOR, ENABLE, OPTION, START, RESUME, RESTART, RESET, HUB, BACK, CONFIGURE, HELP }
    private record Row(String key, String option, String label, Action action) { }
    /** Native-grid geometry: the title card leaves the backdrop emblem visible above it. */
    private static final int HOME_TOP = 148, HOME_HEIGHT = 76, HOME_ROW = 13;
    private static final int DETAIL_TOP = 179, FOOTER_TOP = 198, ENTRANCE_FRAMES = 14;
    private final WorldSession world;
    private final MutatorSessionState settings;
    private final MutatorSupportProfile support;
    private final TitleScreenProvider backdrop;
    private final String title;
    private final java.util.function.Consumer<Cue> cues;
    private final InputHandler neutral = new InputHandler();
    private MenuPixelFont font;
    private TexturedQuadRenderer renderer;
    private State state = State.INACTIVE;
    private Page page = Page.HOME;
    private int row, age, pageAge, entrance, overlayAge;
    // Errors and transition progress stay in status until the next action; notices are transient.
    private String selectedKey, status = "", notice = "", hint = "Arrows / Enter / Esc";
    private boolean open, inGameplay, waitingForFade;
    private Command command = Command.NONE;
    private float highlight;
    private InputHandler lastInput;

    /**
     * Creates a host-owned screen for the current prepared mutator world.
     * Native ROM sound IDs are copied once; creators receive no session, audio
     * manager or global service locator. The old screen becomes unusable when
     * its owning world retires or another world becomes current.
     *
     * @param title screen title
     * @param backdrop inherited native title, or null for a plain panel
     * @param navigateSfx native navigation sound ID (1..255)
     * @param confirmSfx native confirmation sound ID (1..255)
     * @param startSfx native start sound ID (1..255)
     * @param errorSfx native refusal sound ID (1..255)
     * @return screen bound to the current prepared world
     */
    public static MutatorConfigurationScreen forCurrentWorld(String title, TitleScreenProvider backdrop,
            int navigateSfx, int confirmSfx, int startSfx, int errorSfx) {
        int[] ids = {navigateSfx, confirmSfx, startSfx, errorSfx};
        for (int id : ids) if (id < 1 || id > 255)
            throw new IllegalArgumentException("Native menu sound IDs must be 1..255");
        WorldSession world = SessionManager.getCurrentWorldSession();
        if (world == null || MutatorWorldAccess.state(world) == null)
            throw new IllegalStateException("No prepared mutator world for this screen");
        return new MutatorConfigurationScreen(world, title, backdrop, cue -> {
            if (SessionManager.getCurrentWorldSession() == world)
                GameServices.audio().playSfx(ids[cue.ordinal()]);
        });
    }

    public MutatorConfigurationScreen(WorldSession world, String title, TitleScreenProvider backdrop,
                                      java.util.function.Consumer<Cue> cues) {
        this.world = Objects.requireNonNull(world);
        this.title = Objects.requireNonNull(title);
        if (title.isBlank() || title.length() > 28) throw new IllegalArgumentException("Title must be 1..28 characters");
        this.backdrop = backdrop;
        this.cues = Objects.requireNonNull(cues);
        settings = Objects.requireNonNull(MutatorWorldAccess.state(world), "No prepared mutator catalog in this world");
        support = world.resolvedGameModule().getGameService(MutatorSupportProfile.class);
        MutatorWorldAccess.ownScreen(world, this);
    }

    @Override public void initialize() {
        if (backdrop != null) backdrop.initialize();
        state = State.ACTIVE; page = Page.HOME; row = age = pageAge = entrance = overlayAge = 0;
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
        boolean backdropReady = backdrop == null || backdrop.getState() == State.ACTIVE;
        // The title card enters with the native backdrop, not during its SEGA/intro-text screens.
        if (backdropReady) entrance++;
        if (!waitingForFade && backdropReady) handle(input);
    }

    @Override public boolean handleInput(InputHandler input) {
        if (!usable() || input == null) return false;
        inGameplay = true; lastInput = input;
        boolean pause = input.isKeyPressed(GameServices.configuration().getInt(
                com.openggf.configuration.SonicConfiguration.PAUSE_KEY)) || input.logical().player1().startPressed();
        // The configuration menu owns Start and Escape together. Movie/trace sessions never receive its catalog.
        if (!open) {
            if (!pause || GameServices.level().hasPendingFreshLevelTransitionBoundary()) return false;
            open = true; overlayAge = 0; switchPage(Page.LIST); status = MutatorWorldAccess.admissionError(world);
            notice = "Play held. Edit settings, then choose Resume.";
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
    private void animate() { age++; pageAge++; overlayAge++; highlight += (row - highlight) * .45f; }
    private void switchPage(Page next) { page = next; row = 0; pageAge = 0; highlight = 0; notice = ""; }
    private List<Row> rows() {
        var rows = new ArrayList<Row>();
        if (page == Page.HOME) {
            rows.add(new Row(null,null,startLabel(),Action.START));
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
        rows.add(new Row(null,null,inGameplay ? "Resume play" : startLabel(),inGameplay ? Action.RESUME : Action.START));
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
        var choices = rows();
        Row focused = choices.get(Math.min(row, choices.size() - 1));
        hint = MenuInput.directionLabel(input) + " choose  " + (adjustable(focused)
                ? MenuInput.horizontalLabel(input) + " change  " : MenuInput.confirmLabel(input) + " select  ")
                + MenuInput.backLabel(input) + (page == Page.HOME && !inGameplay ? " hub" : " back");
        // A simultaneous cancel/confirm never admits changes.
        if (MenuInput.back(input)) { back(); sound(Cue.NAVIGATE); return; }
        int vertical = (MenuInput.down(input) ? 1 : 0) - (MenuInput.up(input) ? 1 : 0);
        int horizontal = (MenuInput.right(input) ? 1 : 0) - (MenuInput.left(input) ? 1 : 0);
        if (vertical != 0) { row = Math.floorMod(row + vertical, choices.size()); notice = ""; sound(Cue.NAVIGATE); }
        Row selected = choices.get(Math.min(row, choices.size() - 1));
        if (horizontal != 0 && (selected.action() == Action.OPTION || selected.action() == Action.ENABLE
                || selected.action() == Action.MUTATOR)) edit(selected, horizontal);
        if (MenuInput.accept(input)) activate(selected);
        // Pointer uses the same native grid and the same actions as keyboard/controller controls.
        var pointer = MenuInput.pointer(input, width(), 224);
        int top = rowsTop(), height = rowHeight(), first = firstVisibleRow(), count = Math.min(capacity(), choices.size()-first);
        if (pointer.leftPressed()) for (int i=0;i<count;i++) {
            if (pointer.over(rowLeft(),top+i*height,rowWidth(),height)) {
                row=first+i; activate(choices.get(row)); break;
            }
        }
    }
    private void back() {
        if (page == Page.HOME && !inGameplay) activate(new Row(null,null,"Return to game hub",Action.HUB));
        else if (page == Page.OPTIONS) switchPage(Page.LIST);
        else if (page == Page.HELP || !inGameplay && page == Page.LIST) switchPage(Page.HOME);
        else if (inGameplay) { notice = "Choose Resume to apply. Esc keeps play held."; }
    }
    private void activate(Row item) {
        switch(item.action()) {
            case CONFIGURE -> { switchPage(Page.LIST); sound(Cue.CONFIRM); }
            case HELP -> { switchPage(Page.HELP); sound(Cue.CONFIRM); }
            case MUTATOR -> { selectedKey=item.key(); switchPage(Page.OPTIONS); sound(Cue.CONFIRM); }
            case ENABLE, OPTION -> edit(item,1);
            case BACK -> back();
            case RESET -> { settings.resetDefaults(); status=""; notice=inGameplay?"Defaults requested. Choose Resume to apply."
                    :"Defaults requested. Choose Start to play."; save(); }
            case START -> {
                if (!save()) break;
                var admission=MutatorWorldAccess.prepareLaunch(world);
                if (!admission.accepted()) { status=admission.message(); sound(Cue.ERROR); break; }
                state=State.EXITING; open=false;
                status="Loading your native level..."; sound(Cue.START);
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
                else { command=Command.FULL_RESTART; status="Restarting from the act start..."; waitingForFade=true; }
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
        String unavailable=unavailable(item);
        // A saved unavailable toggle can always be turned off; unavailable options never edit.
        if (!unavailable.isBlank() && (item.option()!=null || !value.enabled())) {
            notice=unavailable; sound(Cue.ERROR); return;
        }
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
        status=""; notice=switch(scopeOf(item)) {
            case LIVE -> inGameplay ? "Saved. Applies when you choose Resume." : "Saved. Applies when you choose Start.";
            case LOAD -> inGameplay ? "Saved. Applies after a full restart." : "Saved. Applies when you choose Start.";
            case LAUNCH -> inGameplay ? "Saved. Applies to your next new game." : "Saved. Applies when you choose Start.";
        };
        save(); sound(Cue.NAVIGATE);
    }
    private boolean save() {
        if (MutatorWorldAccess.save(world)) return true;
        status="Save failed. Draft kept; retry.";
        sound(Cue.ERROR);
        return false;
    }
    private void sound(Cue cue) { cues.accept(cue); }
    /** The boundary that admits this row's requested value; a toggle uses the scope of its new state. */
    private MutatorScope scopeOf(Row item) {
        var d=definition(item.key()).definition();
        if (item.option()!=null) return d.optionScope(item.option());
        return settings.requested().get(item.key()).enabled()?d.enableScope():d.disableScope();
    }
    private boolean adjustable(Row item) {
        return item.action()==Action.OPTION || item.action()==Action.ENABLE || item.action()==Action.MUTATOR;
    }
    private String startLabel() { return support == null ? "Start game" : support.startLabel(); }
    private String unavailable(Row item) {
        if (item.key()==null || support==null) return "";
        var definition=definition(item.key()).definition();
        if (!support.capabilities(world.getCurrentZone(),world.getCurrentAct()).containsAll(definition.capabilities())) {
            String reason=support.optionUnavailableReason(definition.localId(),item.option()==null?"":item.option());
            return reason.isBlank()?"This effect is unavailable for the current game or art profile.":reason;
        }
        return item.option()==null ? "" : support.optionUnavailableReason(definition.localId(),item.option());
    }

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
    private boolean homeCard() { return page==Page.HOME && !open; }
    private int cardWidth() { return panelWidth(); }
    private int rowLeft() { return homeCard() ? (width()-cardWidth())/2+6 : left()+5; }
    private int rowWidth() { return homeCard() ? cardWidth()-12 : panelWidth()-10; }
    private int rowsTop() { return homeCard() ? HOME_TOP+26 : page==Page.HELP ? 176 : 58; }
    private int rowHeight() { return homeCard() ? HOME_ROW : page==Page.HELP ? 15 : 24; }
    private int capacity() { return page==Page.HOME?3:5; }
    private int firstVisibleRow() { return Math.max(0,row-capacity()+1); }
    private static float ease(int frames, int length) {
        float t=Math.clamp(frames/(float)length,0f,1f); float inverse=1-t;
        return 1-inverse*inverse*inverse;
    }
    private void render(boolean overlay) {
        var graphics=GameServices.graphics();
        if (graphics.isHeadlessMode()) return;
        graphics.flushScreenSpace(); graphics.resetForFixedFunction(); ensureFont();
        renderer.setProjectionMatrix(graphics.getProjectionMatrixBuffer());
        // Panels and the play-hold dim blend over the native frame. Destination alpha stays opaque
        // so frame readback matches the window; the caller's blend state is restored afterwards.
        boolean blend=glIsEnabled(GL_BLEND);
        int srcRgb=glGetInteger(GL_BLEND_SRC_RGB), dstRgb=glGetInteger(GL_BLEND_DST_RGB);
        int srcAlpha=glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha=glGetInteger(GL_BLEND_DST_ALPHA);
        glEnable(GL_BLEND); glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ZERO,GL_ONE);
        try {
            font.beginMegaBatch();
            if (page==Page.HOME && !overlay) renderHome(); else renderPage(overlay);
            font.endMegaBatch();
        } finally {
            glBlendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
            if (!blend) glDisable(GL_BLEND);
        }
    }
    /** Title card below the native emblem: it slides up once the backdrop is interactive. */
    private void renderHome() {
        int cw=cardWidth(), cx=(width()-cw)/2;
        int slide=Math.round((1-ease(Math.min(entrance,pageAge*2),ENTRANCE_FRAMES))*(224-HOME_TOP));
        int y0=HOME_TOP+slide;
        MenuStyle.fill(font,cx,y0,cw,HOME_HEIGHT,.012f,.035f,.09f,.93f);
        MenuStyle.fill(font,cx,y0,cw,1,1,.78f,.23f,1);
        MenuStyle.label(font,title,cx+8,y0+4,cw-16,.55f,1,.9f);
        boolean error=!status.isBlank();
        MenuStyle.text(font,error?status:settings.definitions().size()+" mutators / native ROM play",cx+8,y0+15,cw-16,
                1,error?.62f:.86f,error?.45f:.45f);
        renderRows(rows(),rowLeft(),rowsTop()+slide,rowWidth(),rowHeight(),false);
        MenuStyle.text(font,hint,cx+8,y0+HOME_HEIGHT-10,cw-16,.62f,.8f,.95f);
    }
    private void renderPage(boolean overlay) {
        int w=width(), x=left(), pw=panelWidth();
        if (overlay) {
            // The held native frame stays readable behind the menu; the dim eases in over six frames.
            MenuStyle.fill(font,0,0,w,224,.01f,.02f,.05f,.66f*ease(overlayAge,6));
        } else {
            MenuStyle.fill(font,0,0,w,224,.015f,.035f,.08f,1);
            MenuStyle.checkerboard(font,w);
        }
        int inset=Math.round((1-ease(pageAge,6))*12);
        MenuStyle.fill(font,x,8-inset,pw,27,.018f,.075f,.15f,.94f);
        MenuStyle.fill(font,x,8-inset,pw,1,1,.78f,.23f,1);
        MenuStyle.label(font,title,x+10,14-inset,pw-86,.55f,1,.9f);
        if (page==Page.LIST || page==Page.OPTIONS) {
            String position=(row+1)+" / "+rows().size();
            MenuStyle.text(font,position,x+pw-68,17-inset,58,.76f,.86f,1);
        }
        MenuStyle.fill(font,x,37,pw,14,.018f,.075f,.15f,.94f);
        MenuStyle.text(font,inGameplay && !MutatorWorldAccess.supportedCell(world)
                ? "Not supported here; effects are suspended." : support==null ? "Native gameplay"
                : support.locationLabel(world.getCurrentZone(),world.getCurrentAct()),x+10,40,pw-20,.76f,.86f,1);
        if (page==Page.HELP) renderHelp(x,pw);
        var visible=rows();
        renderRows(visible,rowLeft(),rowsTop(),rowWidth(),rowHeight(),page!=Page.HELP);
        if (page!=Page.HELP) {
            MenuStyle.fill(font,x,DETAIL_TOP,pw,19,.018f,.075f,.15f,.94f);
            var lines=wrap(detail(visible),(pw-16)/MenuPixelFont.glyphAdvance(MenuStyle.COMPACT),2);
            for (int i=0;i<lines.size();i++) MenuStyle.text(font,lines.get(i),x+8,DETAIL_TOP+1+i*9,pw-16,1,.83f,.45f);
        }
        MenuStyle.fill(font,0,FOOTER_TOP,w,224-FOOTER_TOP,.008f,.02f,.04f,1);
        MenuStyle.text(font,hint,x+8,FOOTER_TOP+3,pw-16,.68f,.89f,1);
        MenuStyle.text(font,inGameplay?"Live: Resume  Load: Restart  Launch: new game"
                :"Effects stay off until you switch them on.",x+8,FOOTER_TOP+14,pw-16,.75f,.84f,.96f);
    }
    /** Controls quote the live bindings: keyboard names while typing, the pad's own buttons on a pad. */
    private void renderHelp(int x, int pw) {
        String[][] sections={
            {"Controls", move()+": run.  "+jump()+": jump.", settingsKeys()+": settings while playing."},
            {"Applying edits", "Resume applies live edits.", "Restart rebuilds from the act start.",
                    "Return to the hub to begin a new game."},
            {"Mutators", "Choose an effect, then adjust its options.", "Every effect starts off. Mix your own rules.",
                    "Stage puzzles keep their native ring rules."}};
        int y=56;
        for (String[] section : sections) {
            MenuStyle.text(font,section[0],x+10,y,pw-20,1,.78f,.23f); y+=10;
            for (int i=1;i<section.length;i++) { MenuStyle.text(font,section[i],x+16,y,pw-26,.83f,.9f,1); y+=10; }
            y+=2;
        }
    }
    private void renderRows(List<Row> visible, int x, int top, int width, int rh, boolean values) {
        // Schema bounds can exceed one page; keep the focused row and its neighbors visible.
        int capacity=capacity(), first=firstVisibleRow();
        int count=Math.min(capacity,visible.size()-first);
        // Backgrounds, then the moving focus, then every label: the sliding focus never hides text.
        for(int i=0;i<count;i++) MenuStyle.fill(font,x+stagger(i),top+i*rh,width-stagger(i),rh-1,.025f,.1f,.19f,.94f);
        int focusShift=stagger(row-first);
        MenuStyle.focus(font,x+focusShift,top+Math.round((Math.clamp(highlight,(float)first,(float)(first+count-1))-first)*rh),width-focusShift,rh-1);
        for(int i=0;i<count;i++) {
            Row item=visible.get(first+i); int y=top+i*rh, lx=x+7+stagger(i);
            boolean focused=first+i==row;
            int textY=MenuStyle.textY(y,values?13:rh-1,MenuStyle.COMPACT);
            boolean available=unavailable(item).isBlank();
            MenuStyle.text(font,item.label(),lx,textY,width-104,available?1:.57f,available?1:.65f,available?1:.75f);
            if (!values) continue;
            String val=value(item);
            if (focused && adjustable(item) && item.action()!=Action.MUTATOR && !val.isEmpty()) val="< "+val+" >";
            boolean on=val.contains("[x]");
            val=MenuStyle.fit(val,92);
            MenuStyle.text(font,val,x+width-6-MenuPixelFont.glyphAdvance(MenuStyle.COMPACT)*val.length(),textY,92,
                    on?.45f:.8f,on?1:.86f,on?.6f:.95f);
            var option=item.option()==null?null:definition(item.key()).definition().option(item.option());
            String scope=scope(item); if(!scope.isEmpty()) MenuStyle.text(font,scope,lx,y+13,
                    option instanceof MutatorOption.IntegerSlider?width-100:width-14,.74f,.83f,.96f);
            if(option instanceof MutatorOption.IntegerSlider slider) {
                int number=(Integer)settings.requested().get(item.key()).options().get(item.option());
                int track=72, tx=x+width-track-8, ty=y+16;
                long span=Math.max(1L,(long)slider.maximum()-slider.minimum());
                int fill=(int)(((long)number-slider.minimum())*track/span);
                int native_=(int)(((long)slider.defaultValue()-slider.minimum())*track/span);
                MenuStyle.fill(font,tx,ty,track,3,.15f,.25f,.36f,1);
                MenuStyle.fill(font,tx,ty,Math.max(1,fill),3,.4f,1,.8f,1);
                MenuStyle.fill(font,tx+native_,ty-2,1,7,1,.78f,.23f,1);
                MenuStyle.fill(font,tx+Math.min(track-2,fill)-1,ty-2,3,7,focused?1:.75f,1,focused?1:.9f,1);
            }
        }
    }
    /** Rows extend from the right edge in a short cascade after each page change. */
    private int stagger(int index) { return Math.max(0,10-3*(pageAge-index)); }
    private String detail(List<Row> visible) {
        if (!status.isBlank()) return status;
        if (!notice.isBlank()) return notice;
        Row current=visible.get(Math.min(row,visible.size()-1));
        if (current.key()!=null) {
            String unavailable=unavailable(current);
            if (!unavailable.isBlank()) return unavailable;
            var d=definition(current.key()).definition();
            Set<MutatorCapability> available=support==null?Set.of():support.capabilities(world.getCurrentZone(),world.getCurrentAct());
            if (d.capabilities().contains(MutatorCapability.RINGFALL) && settings.effective().levelPolicy(available).noRings())
                return "No Rings prevents main-level spills. Ringfall stays ready for when it is disabled.";
            if (d.capabilities().contains(MutatorCapability.BIG_HEAD)
                    && settings.effective().stealthPolicies().stream().anyMatch(MutatorPolicy.PlayerStealth::hideBody))
                return "Stealth hides selected bodies. Their head size returns when Stealth is disabled.";
            if (d.capabilities().contains(MutatorCapability.BIG_HEAD)) {
                String reason=leaderHeadPresentationReason();
                if (!reason.isBlank()) return reason;
            }
            return current.option()!=null ? d.option(current.option()).help() : d.description();
        }
        int pending=settings.pending().size();
        if (pending>0) return pending==1?"1 edit waits for its boundary.":pending+" edits wait for their boundaries.";
        var active=settings.definitions().stream().filter(o->settings.admitted().get(o.key()).enabled())
                .map(o->o.definition().title()).toList();
        return active.isEmpty()?"No pending edits. Every mutator is off.":"Active: "+String.join(", ",active)+".";
    }
    /** Current-pose guidance never changes editing eligibility or native gameplay. */
    private String leaderHeadPresentationReason() {
        if (!inGameplay || support==null) return "";
        var sprites=GameServices.spritesOrNull();
        var player=sprites==null?null:sprites.getMainPlayable();
        if (player==null) return "";
        if (!support.supportsPlayer(MutatorCapability.BIG_HEAD,player.characterKey().persisted(),true))
            return "The leader uses native head size. Reviewed Sonic art can be enlarged.";
        if (player.isSuperSonic()) return "Powered leader art keeps native head size. Ordinary Sonic uses the chosen scale.";
        var renderer=player.getSpriteRenderer();
        if (renderer==null) return "";
        if (renderer.headProfileId().isBlank())
            return "The leader's current art has no reviewed head mask. Native size is kept.";
        return renderer.headPresentationReason(player.getMappingFrame());
    }

    /** Word wrap on the compact grid; an overlong final line keeps the shared ellipsis. */
    static List<String> wrap(String text, int columns, int maxLines) {
        var lines=new ArrayList<String>(); var line=new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (line.length()>0 && line.length()+1+word.length()>columns) {
                if (lines.size()==maxLines-1) { line.append(' ').append(word); continue; }
                lines.add(line.toString()); line.setLength(0);
            }
            if (line.length()>0) line.append(' ');
            line.append(word);
        }
        if (line.length()>0 || lines.isEmpty()) lines.add(MenuStyle.fit(line.toString(),columns*MenuPixelFont.glyphAdvance(MenuStyle.COMPACT)));
        return lines;
    }
    private String move() {
        if (lastInput!=null && MenuInput.controller(lastInput)) return "D-Pad";
        var config=GameServices.configuration();
        return ButtonPrompts.keyName(config.getInt(SonicConfiguration.LEFT)).orElse("Left")+" / "
                +ButtonPrompts.keyName(config.getInt(SonicConfiguration.RIGHT)).orElse("Right");
    }
    private String jump() {
        var labels=new LinkedHashSet<String>();
        if (lastInput!=null && MenuInput.controller(lastInput)) {
            for (var button : List.of(ButtonPrompts.Button.A,ButtonPrompts.Button.B,ButtonPrompts.Button.C))
                ButtonPrompts.label(lastInput,0,button).ifPresent(labels::add);
        } else {
            var config=GameServices.configuration();
            for (var key : List.of(SonicConfiguration.P1_A,SonicConfiguration.P1_B,SonicConfiguration.P1_C))
                ButtonPrompts.keyName(config.getInt(key)).ifPresent(labels::add);
        }
        return labels.isEmpty()?"A / B / C":String.join(" / ",labels);
    }
    private String settingsKeys() {
        if (lastInput!=null && MenuInput.controller(lastInput))
            return ButtonPrompts.label(lastInput,0,ButtonPrompts.Button.START).orElse("Start");
        var config=GameServices.configuration(); var keys=new LinkedHashSet<String>();
        ButtonPrompts.keyName(config.getInt(SonicConfiguration.PAUSE_KEY)).ifPresent(keys::add);
        ButtonPrompts.keyName(config.getInt(SonicConfiguration.START)).ifPresent(keys::add);
        return keys.isEmpty()?"Start":String.join(" or ",keys);
    }
    private String value(Row item) {
        if(item.key()==null) return "";
        var requested=settings.requested().get(item.key());
        if (!unavailable(item).isBlank()) return "Unavailable";
        if(item.option()==null) return requested.enabled()?"[x] On":"[ ] Off";
        return format(definition(item.key()).definition().option(item.option()),requested.options().get(item.option()));
    }
    private static String format(MutatorOption option, Object value) {
        return switch(option) {
            case MutatorOption.Checkbox checkbox -> (Boolean)value?"[x] On":"[ ] Off";
            case MutatorOption.IntegerSlider slider -> value+slider.unit();
            case MutatorOption.Choice choice -> {
                String words=((String)value).replace('_',' ');
                yield words.isEmpty()?words:Character.toUpperCase(words.charAt(0))+words.substring(1);
            }
        };
    }
    private String scope(Row item) {
        if(item.key()==null) return "";
        var d=definition(item.key()).definition();
        var admitted=settings.admitted().get(item.key());
        MutatorScope scope=scopeOf(item);
        var pendingEdit=settings.pending().stream().filter(p->p.key().equals(item.key()) && Objects.equals(p.optionId(),item.option())).findFirst();
        boolean pending=pendingEdit.isPresent();
        if (pending && pendingEdit.get().reason().contains("edit")) return "History differs / edit to apply";
        String boundary=switch(scope){case LIVE->inGameplay?"Resume":"Start";case LOAD->"full restart / death reload";case LAUNCH->"new game";};
        if(item.option()!=null && pending) return "Pending "+boundary+" / now "
                +format(d.option(item.option()),admitted.options().get(item.option())).replace("[x] ","").replace("[ ] ","");
        return (pending?"Pending ":"Applies at ")+boundary+(item.action()==Action.MUTATOR?" / Enter: options":"");
    }
    @Override public void close() {
        if(font!=null) font.cleanup(); if(renderer!=null) renderer.cleanup();
        font=null; renderer=null; open=false; waitingForFade=false; command=Command.NONE;
    }
}
