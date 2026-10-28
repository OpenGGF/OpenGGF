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
    /** Native-grid geometry shared by drawing and pointer selection. */
    private static final int HOME_TOP = 148, HOME_HEIGHT = 57, HOME_ROW = 14;
    private static final int DETAIL_TOP = 179, ENTRANCE_FRAMES = 14;
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
    private String selectedKey, status = "", notice = "";
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
    private void switchPage(Page next) { switchPage(next, 0); }
    /** Backing out lands on the row that opened the page, so a long catalogue keeps its place. */
    private void switchPage(Page next, int focus) { page = next; row = focus; pageAge = 0; highlight = focus; notice = ""; }
    private int mutatorRow(String key) {
        var owned = settings.definitions();
        for (int i = 0; i < owned.size(); i++) if (owned.get(i).key().equals(key)) return i;
        return 0;
    }
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
        int top = rowsTop(), height = rowHeight(), first = firstVisibleRow(), count = visibleCount(choices,first);
        if (pointer.leftPressed()) for (int i=0;i<count;i++) {
            if (pointer.over(rowLeft(),rowY(choices, first, first+i, top),rowWidth(),height)) {
                row=first+i; activate(choices.get(row)); break;
            }
        }
    }
    private void back() {
        if (page == Page.HOME && !inGameplay) activate(new Row(null,null,"Return to game hub",Action.HUB));
        else if (page == Page.OPTIONS) switchPage(Page.LIST, mutatorRow(selectedKey));
        else if (page == Page.HELP || !inGameplay && page == Page.LIST) switchPage(Page.HOME, page == Page.HELP ? 2 : 1);
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
    /** Presentation eligibility mirrors edit's refusal, including clearing a saved unavailable toggle. */
    private boolean editable(Row item) {
        return adjustable(item) && (unavailable(item).isBlank()
                || item.option()==null && settings.requested().get(item.key()).enabled());
    }
    private String startLabel() { return support == null ? "Start game" : support.startLabel(); }
    private String unavailable(Row item) {
        if (item.key()==null || support==null) return "";
        var definition=definition(item.key()).definition();
        if (!support.capabilities(world.getCurrentZone(),world.getCurrentAct()).containsAll(definition.capabilities())) {
            String reason=support.optionUnavailableReason(definition.localId(),item.option()==null?"":item.option());
            return reason.isBlank()?"Unavailable for this game or art profile.":reason;
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
    private int rowsTop() { return homeCard() ? HOME_TOP+14 : page==Page.HELP ? 176 : 58; }
    private boolean groupedOptions() {
        return page==Page.OPTIONS && definition(selectedKey).definition().options().stream()
                .anyMatch(option -> option instanceof MutatorOption.Checkbox check && !check.group().isBlank());
    }
    private int rowHeight() { return homeCard() ? HOME_ROW : page==Page.HELP || groupedOptions() ? 15 : 24; }
    private int capacity() { return page==Page.HOME?3:groupedOptions()?7:5; }
    private int firstVisibleRow() {
        int first=Math.max(0,row-capacity()+1);
        if (groupedOptions()) {
            var visible=rows();
            while (first<row && rowY(visible,first,row,rowsTop())+rowHeight()>DETAIL_TOP-3) first++;
        }
        return first;
    }
    private int visibleCount(List<Row> visible,int first) {
        int count=Math.min(capacity(),visible.size()-first);
        if (groupedOptions()) while (count>1
                && rowY(visible,first,first+count-1,rowsTop())+rowHeight()>DETAIL_TOP-3) count--;
        return count;
    }
    private String group(Row item) {
        if (item.option()==null) return "";
        var option=definition(item.key()).definition().option(item.option());
        return option instanceof MutatorOption.Checkbox check ? check.group() : "";
    }
    private boolean groupStarts(List<Row> visible,int first,int index) {
        String label=group(visible.get(index));
        return !label.isBlank() && (index==first || !label.equals(group(visible.get(index-1))));
    }
    private int rowY(List<Row> visible,int first,int index,int top) {
        int y=top+(index-first)*rowHeight();
        for(int i=first;i<=index;i++) if(groupStarts(visible,first,i)) y+=12;
        return y;
    }
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
    /** Compact native-title card; its controls share the page footer's binding-aware key caps. */
    private void renderHome() {
        int cw=cardWidth(), cx=(width()-cw)/2;
        int slide=Math.round((1-ease(Math.min(entrance,pageAge*2),ENTRANCE_FRAMES))*(224-HOME_TOP));
        int y0=HOME_TOP+slide;
        MenuChrome.header(font,width(),MenuChrome.fitLabel(title,width()-92),"NEW GAME");
        // Preserve the native emblem's original clearance; the title lives in the shared header.
        MenuChrome.fill(font,cx,y0,cw,HOME_HEIGHT,MenuChrome.INK,.96f);
        MenuChrome.fill(font,cx,y0,cw,1,MenuChrome.EDGE);
        MenuChrome.compact(font,MenuChrome.fitCompact(status.isBlank()
                ? settings.definitions().size()+" mutators / native ROM play" : status,cw-16),
                cx+8,y0+3,status.isBlank()?MenuChrome.MUTED:MenuChrome.RED);
        renderRows(rows(),rowLeft(),rowsTop()+slide,rowWidth(),rowHeight(),false);
        renderFooter();
    }
    private void renderPage(boolean overlay) {
        int w=width(), x=left(), pw=panelWidth();
        String location=inGameplay && !MutatorWorldAccess.supportedCell(world)
                ? "Not supported here; effects are suspended." : support==null ? "Native gameplay"
                : support.locationLabel(world.getCurrentZone(),world.getCurrentAct());
        Row current=rows().get(Math.min(row,rows().size()-1));
        String scope=page==Page.HELP?"HELP":inGameplay?"PLAY HELD":"NEW GAME";
        String tag=page==Page.OPTIONS?statusTag(current):"";
        if(page==Page.OPTIONS) location=scope(current);
        if(!tag.isBlank()) scope=tag;
        String badge=tag.isBlank()?scope:"";
        if (overlay) {
            // The held native frame stays readable behind the menu; dim eases in over six frames.
            // The held frame stays visible but recedes enough that its HUD never competes with menu text.
            MenuChrome.fill(font,0,0,w,224,MenuChrome.INK,.88f*ease(overlayAge,6));
            MenuChrome.header(font,w,MenuChrome.fitLabel(title,w-MenuChrome.compactWidth(scope)-34),badge);
            // A quiet band keeps the location line off the held frame's own HUD.
            MenuChrome.fill(font,0,MenuChrome.HEADER_H+1,w,13,MenuChrome.INK,.75f);
            MenuChrome.compact(font,MenuChrome.fitCompact(location,w-18),9,36,MenuChrome.MUTED);
        } else MenuChrome.page(font,w,MenuChrome.fitLabel(title,w-MenuChrome.compactWidth(scope)-34),badge,location);
        if(!tag.isBlank()) MenuChrome.tag(font,w-8-MenuChrome.tagWidth(tag),9,tag,
                unavailable(current).isBlank()?MenuChrome.AMBER:MenuChrome.DIM);
        if (page==Page.HELP) renderHelp(x,pw);
        else {
            String caption=page==Page.OPTIONS?definition(selectedKey).definition().title():"Mutators";
            String position=(row+1)+" / "+rows().size();
            MenuChrome.section(font,MenuChrome.fitCompact(caption.toUpperCase(Locale.ROOT),pw-76),x+5,47,pw-10);
            MenuChrome.fill(font,x+pw-64,46,64,10,MenuChrome.INK,.95f);
            MenuChrome.compact(font,position,x+pw-5-MenuChrome.compactWidth(position),47,MenuChrome.MUTED);
        }
        var visible=rows();
        renderRows(visible,rowLeft(),rowsTop(),rowWidth(),rowHeight(),page!=Page.HELP);
        if (page!=Page.HELP) {
            var lines=wrap(detail(visible),pw-12,2);
            MenuChrome.descriptionBox(font,x,DETAIL_TOP,pw,23,lines,
                    List.of(status.isBlank()?MenuChrome.TEXT:MenuChrome.RED,MenuChrome.MUTED));
        }
        renderFooter();
    }
    /** Controls quote the live bindings: keyboard names while typing, the pad's own buttons on a pad. */
    private void renderHelp(int x, int pw) {
        String[][] sections={
            {"CONTROLS", move()+": run.  "+jump()+": jump.", settingsKeys()+": settings while playing."},
            {"APPLYING EDITS", "Resume applies live edits.", "Restart rebuilds from the act start.",
                    "Return to the hub to begin a new game."},
            {"MUTATORS", "Choose an effect, then adjust its options.", "Every effect starts off. Mix your own rules.",
                    "Stage puzzles keep their native ring rules."}};
        int y=49;
        for (String[] section : sections) {
            MenuChrome.section(font,section[0],x+8,y,pw-16); y+=11;
            for (int i=1;i<section.length;i++) {
                MenuChrome.compact(font,MenuChrome.fitCompact(section[i],pw-22),x+12,y,MenuChrome.TEXT); y+=10;
            }
            y+=3;
        }
    }
    private void renderFooter() {
        Row focused=rows().get(Math.min(row,rows().size()-1));
        var hints=new ArrayList<MenuChrome.Hint>();
        if (page!=Page.HELP) hints.add(MenuChrome.upDown(lastInput,"CHOOSE"));
        if (editable(focused)) hints.add(MenuChrome.leftRight(lastInput,"CHANGE"));
        // Left/Right already reads CHANGE; Enter names its own action rather than repeating it.
        boolean sliderRow=focused.option()!=null
                && definition(focused.key()).definition().option(focused.option()) instanceof MutatorOption.IntegerSlider;
        String optionVerb=!editable(focused)?"WHY":sliderRow?"STEP":"TOGGLE";
        // Enter opens catalogue options; it cycles/toggles individual options as before.
        hints.add(MenuChrome.hint(MenuChrome.confirmKey(lastInput),switch(focused.action()) {
            case MUTATOR -> "OPTIONS";
            case ENABLE, OPTION -> optionVerb;
            case START -> "START";
            case RESUME -> "RESUME";
            default -> "SELECT";
        }));
        hints.add(MenuChrome.hint(MenuChrome.backKey(lastInput),page==Page.HOME && !inGameplay?"HUB"
                : inGameplay && page==Page.LIST?"HOLD":"BACK"));
        MenuChrome.footer(font,width(),"",hints,MenuChrome.controller(lastInput));
    }
    private void renderRows(List<Row> visible, int x, int top, int width, int rh, boolean values) {
        int first=firstVisibleRow(), count=visibleCount(visible,first);
        // Keep the gold frame outside each row's fill, with room before captions and details.
        for(int i=0;i<count;i++) {
            int index=first+i,y=rowY(visible,first,index,top);
            if(groupStarts(visible,first,index))
                MenuChrome.section(font,group(visible.get(index)).toUpperCase(Locale.ROOT),x+8,y-10,width-16);
            MenuChrome.fill(font,x+stagger(i),y,width-stagger(i),(rh>=24?rh-3:rh-4),MenuChrome.NAVY,.96f);
        }
        int shift=stagger(row-first);
        float focus=Math.clamp(highlight,(float)first,(float)(first+count-1));
        int low=(int)Math.floor(focus), high=Math.min(first+count-1,low+1);
        int focusY=Math.round(rowY(visible,first,low,top)+(focus-low)*(rowY(visible,first,high,top)-rowY(visible,first,low,top)));
        MenuChrome.focus(font,x+shift,focusY,width-shift,(rh>=24?rh-3:rh-4),age);
        for(int i=0;i<count;i++) {
            Row item=visible.get(first+i); int y=rowY(visible,first,first+i,top), lx=x+10+stagger(i);
            boolean focused=first+i==row, available=unavailable(item).isBlank();
            int textY=y+2;
            var option=item.option()==null?null:definition(item.key()).definition().option(item.option());
            boolean slider=option instanceof MutatorOption.IntegerSlider;
            boolean toggle=item.key()!=null && (item.option()==null || option instanceof MutatorOption.Checkbox);
            boolean changed=changed(item);
            if(changed) MenuChrome.changedMarker(font,lx-7,textY+1);
            MenuChrome.Icon icon=switch(item.action()) {
                case START, RESUME -> MenuChrome.Icon.PLAY;
                case CONFIGURE -> MenuChrome.Icon.PUZZLE;
                case HELP -> MenuChrome.Icon.INFO;
                default -> null;
            };
            if(icon!=null) { MenuChrome.icon(font,icon,lx,textY,MenuChrome.GOLD); lx+=12; }
            int right=x+width-12, valueX=right;
            if(values && item.key()!=null) {
                String val=value(item);
                if(toggle) {
                    boolean on=item.option()==null?settings.requested().get(item.key()).enabled()
                            :(Boolean)settings.requested().get(item.key()).options().get(item.option());
                    String shown=on?"On":"Off";
                    // Switch then word, the same order as Settings rows.
                    int wordX=right-MenuChrome.compactWidth(shown);
                    valueX=wordX-5-MenuChrome.TOGGLE_W;
                    MenuChrome.toggle(font,valueX,textY,on,available);
                    MenuChrome.compact(font,shown,wordX,textY,available?MenuChrome.TEXT:MenuChrome.DIM);
                } else if(slider) {
                    String shown=MenuChrome.fitCompact(val,54);
                    valueX=right-MenuChrome.compactWidth(shown);
                    renderSlider((MutatorOption.IntegerSlider)option,item,valueX-91,textY+3,available);
                    MenuChrome.compact(font,shown,valueX,textY,available?MenuChrome.TEXT:MenuChrome.DIM);
                    valueX-=91;
                } else {
                    String shown=MenuChrome.fitCompact(val,96);
                    valueX=right-MenuChrome.compactWidth(shown);
                    MenuChrome.compact(font,shown,valueX,textY,available?MenuChrome.TEXT:MenuChrome.DIM);
                }
                if(focused && editable(item)) {
                    MenuChrome.triangle(font,valueX-7,textY+1,3,'L',MenuChrome.GOLD,1);
                    MenuChrome.triangle(font,right+4,textY+1,3,'R',MenuChrome.GOLD,1);
                }
            }
            int labelWidth=values && item.key()!=null?valueX-lx-12:width-20-stagger(i);
            MenuChrome.compact(font,MenuChrome.fitCompact(item.label(),labelWidth),lx,textY,
                    !available?MenuChrome.DIM:focused?MenuChrome.WHITE:MenuChrome.TEXT);
            if(values && rh>=24 && item.key()!=null) {
                String tag=statusTag(item);
                int tagX=tag.isBlank()?right:right-MenuChrome.tagWidth(tag);
                if(!tag.isBlank()) MenuChrome.tag(font,tagX,y+11,tag,!available?MenuChrome.DIM:MenuChrome.AMBER);
                MenuChrome.compact(font,MenuChrome.fitCompact(rowGuidance(item),tagX-lx-6),lx,y+12,
                        focused?MenuChrome.MUTED:MenuChrome.DIM);
            }
        }
        if(values) MenuChrome.scrollbar(font,x+width+5,top,DETAIL_TOP-top-5,first,count,visible.size());
    }
    /** Same 85px cyan track and 3x8 gold thumb as Settings' hub-music slider. */
    private void renderSlider(MutatorOption.IntegerSlider slider, Row item, int x, int y, boolean available) {
        int number=(Integer)settings.requested().get(item.key()).options().get(item.option());
        long span=Math.max(1L,(long)slider.maximum()-slider.minimum());
        int fill=(int)(((long)number-slider.minimum())*85/span);
        int nativeMark=(int)(((long)slider.defaultValue()-slider.minimum())*85/span);
        MenuChrome.fill(font,x,y,85,2,MenuChrome.EDGE);
        MenuChrome.fill(font,x,y,fill,2,available?MenuChrome.CYAN:MenuChrome.DIM);
        // Retain the schema-default tick (the help text calls it the gold mark).
        MenuChrome.fill(font,x+nativeMark,y-1,1,4,MenuChrome.GOLD,.65f);
        MenuChrome.fill(font,x+fill-1,y-3,3,8,available?MenuChrome.GOLD:MenuChrome.DIM);
    }
    private boolean changed(Row item) {
        if(item.key()==null) return false;
        var requested=settings.requested().get(item.key());
        var defaults=definition(item.key()).definition().defaults();
        return item.option()==null?requested.enabled() || !requested.options().equals(defaults)
                :!Objects.equals(requested.options().get(item.option()),defaults.get(item.option()));
    }
    private boolean pending(Row item) {
        return item.key()!=null && settings.pending().stream()
                .anyMatch(p->p.key().equals(item.key()) && Objects.equals(p.optionId(),item.option()));
    }
    private String statusTag(Row item) {
        if(item.key()==null) return "";
        if(!unavailable(item).isBlank()) return "UNAVAILABLE";
        var edit=settings.pending().stream()
                .filter(p->p.key().equals(item.key()) && Objects.equals(p.optionId(),item.option())).findFirst();
        if(edit.isEmpty()) return "";
        if(edit.get().reason().contains("edit")) return "EDIT TO APPLY";
        return !inGameplay?"PENDING START":switch(edit.get().scope()) {
            case LIVE -> "PENDING RESUME";
            case LOAD -> "RESTART NEEDED";
            case LAUNCH -> "NEW GAME NEEDED";
        };
    }
    /** The tag names the pending boundary; this line names the value still admitted now. */
    private String rowGuidance(Row item) {
        if(!unavailable(item).isBlank()) return item.option()==null && settings.requested().get(item.key()).enabled()
                ?"Change to switch off":"Not supported here";
        if(!pending(item)) return scope(item);
        var admitted=settings.admitted().get(item.key());
        return "Now "+(item.option()==null?(admitted.enabled()?"On":"Off")
                :format(definition(item.key()).definition().option(item.option()),admitted.options().get(item.option())));
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
            if (d.capabilities().contains(MutatorCapability.BIG_HEAD) && current.option()==null) {
                String reason=settings.effective().policies().values().stream().flatMap(List::stream)
                        .anyMatch(p->p instanceof MutatorPolicy.BigHead h && h.afflicts().contains(MutatorPolicy.HeadTarget.PLAYER))
                        ? leaderHeadPresentationReason() : "";
                if (!reason.isBlank()) return reason;
                if (current.option()==null) { String rings=ringScaledHeadReadout(); if (!rings.isBlank()) return rings; }
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
            return "The leader's current ROM art keeps native head size.";
        var renderer=player.getSpriteRenderer();
        if (renderer==null) return "";
        if (renderer.headProfileId().isBlank())
            return "The leader's current art has no reviewed head mask. Native size is kept.";
        return renderer.headPresentationReason(player.getMappingFrame());
    }

    /** The admitted ring-scaled size, read from the leader's live native inventory. */
    private String ringScaledHeadReadout() {
        var sprites=GameServices.spritesOrNull();
        var player=sprites==null?null:sprites.getMainPlayable();
        boolean scaled=settings.effective().policies().values().stream().flatMap(List::stream)
                .anyMatch(policy -> policy instanceof MutatorPolicy.BigHead head && head.scaleWithRings());
        if (!inGameplay || player==null || !scaled) return "";
        int rings=player.getRingCount();
        boolean playerSelected=settings.effective().policies().values().stream().flatMap(List::stream)
                .anyMatch(p->p instanceof MutatorPolicy.BigHead h && h.afflicts().contains(MutatorPolicy.HeadTarget.PLAYER));
        return playerSelected ? "Player head: "+settings.effective().headScalePercent(true,rings)+"% at "+rings+" rings."
                : "Sidekicks use their own rings; enemies use Player's "+rings+" rings.";
    }

    /** Word wrap of compact text to a pixel width; an overlong final line keeps the shared ellipsis. */
    static List<String> wrap(String text, int maxWidth, int maxLines) {
        var lines=new ArrayList<String>(); var line=new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (line.length()>0 && MenuChrome.compactWidth(line+" "+word)>maxWidth) {
                if (lines.size()==maxLines-1) { line.append(' ').append(word); continue; }
                lines.add(line.toString()); line.setLength(0);
            }
            if (line.length()>0) line.append(' ');
            line.append(word);
        }
        if (line.length()>0 || lines.isEmpty()) lines.add(MenuChrome.fitCompact(line.toString(),maxWidth));
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
        if(item.option()==null && requested.enabled()) return "On";
        if (!unavailable(item).isBlank()) return "Unavailable";
        if(item.option()==null) return "Off";
        return format(definition(item.key()).definition().option(item.option()),requested.options().get(item.option()));
    }
    private static String format(MutatorOption option, Object value) {
        return switch(option) {
            case MutatorOption.Checkbox checkbox -> (Boolean)value?"On":"Off";
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
        String unavailable=unavailable(item);
        if(!unavailable.isBlank()) {
            // No boundary applies here: name the reason, or how to clear a toggle saved before it became unavailable.
            if(item.option()==null && settings.requested().get(item.key()).enabled()) return "Unavailable here. Change to switch off.";
            int sentence=unavailable.indexOf(". ");
            return sentence<0?unavailable:unavailable.substring(0,sentence+1);
        }
        var admitted=settings.admitted().get(item.key());
        MutatorScope scope=scopeOf(item);
        var pendingEdit=settings.pending().stream().filter(p->p.key().equals(item.key()) && Objects.equals(p.optionId(),item.option())).findFirst();
        boolean pending=pendingEdit.isPresent();
        if (pending && pendingEdit.get().reason().contains("edit")) return "History differs / edit to apply";
        // Before play every boundary is the Start load; in play the row names the menu choice that applies it.
        String boundary=!inGameplay?"Start":switch(scope){case LIVE->"Resume";case LOAD->"restart or death";case LAUNCH->"new game";};
        if(item.option()!=null && pending) return "Pending "+boundary+" / now "
                +format(d.option(item.option()),admitted.options().get(item.option()));
        return (pending?"Pending ":"Applies at ")+boundary;
    }
    @Override public void close() {
        if(font!=null) font.cleanup(); if(renderer!=null) renderer.cleanup();
        font=null; renderer=null; open=false; waitingForFade=false; command=Command.NONE;
    }
}
