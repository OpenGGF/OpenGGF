package com.openggf.mods.mutators;

import com.openggf.i18n.UiMessages;
import java.util.Map;



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
    private int helpPage, helpPageCount = 1;
    // Errors and transition progress stay in status until the next action; notices are transient.
    private String selectedKey, status = "", notice = "";
    private boolean open, inGameplay, waitingForFade;
    private Command command = Command.NONE;
    private float highlight;
    private InputHandler lastInput;
    private MenuDetailsScreen details;

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
            throw new IllegalArgumentException(UiMessages.text("mutator.mutator_configuration_screen.native_menu_sound_ids_must_be_1_255_glk054"));
        WorldSession world = SessionManager.getCurrentWorldSession();
        if (world == null || MutatorWorldAccess.state(world) == null)
            throw new IllegalStateException(UiMessages.text("mutator.mutator_configuration_screen.no_prepared_mutator_world_for_this_screen_xahk93"));
        return new MutatorConfigurationScreen(world, title, backdrop, cue -> {
            if (SessionManager.getCurrentWorldSession() == world)
                GameServices.audio().playSfx(ids[cue.ordinal()]);
        });
    }

    public MutatorConfigurationScreen(WorldSession world, String title, TitleScreenProvider backdrop,
                                      java.util.function.Consumer<Cue> cues) {
        this.world = Objects.requireNonNull(world);
        this.title = Objects.requireNonNull(title);
        if (title.isBlank() || title.length() > 28) throw new IllegalArgumentException(UiMessages.text("mutator.mutator_configuration_screen.title_must_be_1_28_characters_ww52pt"));
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
        details = null;
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
            notice = UiMessages.text("mutator.mutator_configuration_screen.play_held_edit_settings_then_choose_resume_1wlie9");
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
        if (waiting) { open = true; status = UiMessages.text("mutator.mutator_configuration_screen.waiting_for_the_current_transition_14z0zx5"); }
        else { open = false; status = (inGameplay ? "" : UiMessages.text("mutator.mutator_configuration_screen.returning_to_game_hub_9phrw0")); }
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
            rows.add(new Row(null,null,UiMessages.text("mutator.mutator_configuration_screen.configure_mutators_1crs5gr"),Action.CONFIGURE));
            rows.add(new Row(null,null,UiMessages.text("mutator.mutator_configuration_screen.how_to_play_tx21mx"),Action.HELP));
            return rows;
        }
        if (page == Page.HELP) return List.of(new Row(null,null,UiMessages.text("mutator.mutator_configuration_screen.back_187if"),Action.BACK));
        if (page == Page.OPTIONS) {
            var owned = definition(selectedKey); var d = owned.definition();
            // The section caption already names the mutator; its switch row says what it switches.
            rows.add(new Row(selectedKey,null,UiMessages.text("mutator.menu.enabled"),Action.ENABLE));
            for (var option : d.options()) rows.add(new Row(selectedKey,option.id(),option.label(),Action.OPTION));
            rows.add(new Row(null,null,UiMessages.text("mutator.mutator_configuration_screen.back_to_mutators_n24sn1"),Action.BACK));
            return rows;
        }
        for (var owned : settings.definitions()) rows.add(new Row(owned.key(),null,owned.definition().title(),Action.MUTATOR));
        rows.add(new Row(null,null,(inGameplay ? UiMessages.text("mutator.mutator_configuration_screen.resume_play_pb94pz") : startLabel()),inGameplay ? Action.RESUME : Action.START));
        if (inGameplay) rows.add(new Row(null,null,UiMessages.text("mutator.mutator_configuration_screen.restart_from_act_start_d4jpnz"),Action.RESTART));
        rows.add(new Row(null,null,UiMessages.text("mutator.mutator_configuration_screen.reset_to_defaults_tp4d7q"),Action.RESET));
        rows.add(new Row(null,null,(inGameplay ? UiMessages.text("mutator.mutator_configuration_screen.return_to_game_hub_16z0poc") : UiMessages.text("mutator.mutator_configuration_screen.back_to_title_1ptrg4s")),inGameplay ? Action.HUB : Action.BACK));
        return List.copyOf(rows);
    }
    private OwnedMutator definition(String key) {
        return settings.definitions().stream().filter(owned -> owned.key().equals(key)).findFirst().orElseThrow();
    }

    private void handle(InputHandler input) {
        if (input == null) return;
        lastInput = input;
        if (details != null) { if (details.update(input)) details = null; return; }
        var choices = rows();
        // A simultaneous cancel/confirm never admits changes.
        if (MenuInput.back(input)) { back(); sound(Cue.NAVIGATE); return; }
        if (MenuInput.textDetails(input)) {
            details = new MenuDetailsScreen(UiMessages.text("menu.path.details_caps"), detailsText(choices.get(Math.min(row, choices.size()-1))));
            sound(Cue.NAVIGATE); return;
        }
        int vertical = (MenuInput.down(input) ? 1 : 0) - (MenuInput.up(input) ? 1 : 0);
        int horizontal = (MenuInput.right(input) ? 1 : 0) - (MenuInput.left(input) ? 1 : 0);
        if (vertical != 0) { row = Math.floorMod(row + vertical, choices.size()); notice = ""; sound(Cue.NAVIGATE); }
        if(page==Page.HELP && horizontal!=0) {
            helpPageCount = helpPageCount();
            helpPage=Math.floorMod(helpPage+horizontal,helpPageCount);
            sound(Cue.NAVIGATE); return;
        }
        Row selected = choices.get(Math.min(row, choices.size() - 1));
        if (horizontal != 0 && (selected.action() == Action.OPTION || selected.action() == Action.ENABLE
                || selected.action() == Action.MUTATOR)) edit(selected, horizontal);
        if (MenuInput.accept(input)) activate(selected);
        // Pointer uses the same native grid and the same actions as keyboard/controller controls.
        var pointer = MenuInput.pointer(input, width(), 224);
        int top = rowsTop(), first = firstVisibleRow(), count = visibleCount(choices,first);
        if (pointer.leftPressed()) for (int i=0;i<count;i++) {
            if (pointer.over(rowLeft(),rowY(choices, first, first+i, top),rowWidth(),rowHeight(choices.get(first+i)))) {
                row=first+i; activate(choices.get(row)); break;
            }
        }
    }
    private void back() {
        if (page == Page.HOME && !inGameplay) activate(new Row(null,null,UiMessages.text("mutator.mutator_configuration_screen.return_to_game_hub_16z0poc"),Action.HUB));
        else if (page == Page.OPTIONS) switchPage(Page.LIST, mutatorRow(selectedKey));
        else if (page == Page.HELP || !inGameplay && page == Page.LIST) switchPage(Page.HOME, page == Page.HELP ? 2 : 1);
        else if (inGameplay) { notice = UiMessages.text("mutator.mutator_configuration_screen.choose_resume_to_apply_esc_keeps_play_held_c6pqpb"); }
    }
    private void activate(Row item) {
        switch(item.action()) {
            case CONFIGURE -> { switchPage(Page.LIST); sound(Cue.CONFIRM); }
            case HELP -> { switchPage(Page.HELP); sound(Cue.CONFIRM); }
            case MUTATOR -> { selectedKey=item.key(); switchPage(Page.OPTIONS); sound(Cue.CONFIRM); }
            case ENABLE, OPTION -> edit(item,1);
            case BACK -> back();
            case RESET -> { settings.resetDefaults(); status=""; notice=(inGameplay ? UiMessages.text("mutator.mutator_configuration_screen.defaults_requested_choose_resume_to_apply_4634uh") : UiMessages.text("mutator.mutator_configuration_screen.defaults_requested_choose_start_to_play_cg4lqi")); save(); }
            case START -> {
                if (!save()) break;
                var admission=MutatorWorldAccess.prepareLaunch(world);
                if (!admission.accepted()) { status=admission.message(); sound(Cue.ERROR); break; }
                state=State.EXITING; open=false;
                status=UiMessages.text("mutator.mutator_configuration_screen.loading_your_native_level_1e4ha0a"); sound(Cue.START);
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
                else { command=Command.FULL_RESTART; status=UiMessages.text("mutator.mutator_configuration_screen.restarting_from_the_act_start_wusgya"); waitingForFade=true; }
            }
            case HUB -> {
                if (!save()) break;
                command=Command.RETURN_TO_HUB; status=UiMessages.text("mutator.mutator_configuration_screen.returning_to_game_hub_9phrw0"); waitingForFade=true;
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
            case LIVE -> (inGameplay ? UiMessages.text("mutator.mutator_configuration_screen.saved_applies_when_you_choose_resume_185j76a") : UiMessages.text("mutator.mutator_configuration_screen.saved_applies_when_you_choose_start_dkon7f"));
            case LOAD -> (inGameplay ? UiMessages.text("mutator.mutator_configuration_screen.saved_applies_after_a_full_restart_6pu2ps") : UiMessages.text("mutator.mutator_configuration_screen.saved_applies_when_you_choose_start_dkon7f"));
            case LAUNCH -> (inGameplay ? UiMessages.text("mutator.mutator_configuration_screen.saved_applies_to_your_next_new_game_1ysert0") : UiMessages.text("mutator.mutator_configuration_screen.saved_applies_when_you_choose_start_dkon7f"));
        };
        save(); sound(Cue.NAVIGATE);
    }
    private boolean save() {
        if (MutatorWorldAccess.save(world)) return true;
        status=UiMessages.text("mutator.mutator_configuration_screen.save_failed_draft_kept_retry_1xpgxh6");
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
    private String startLabel() { return support == null ? UiMessages.text("mutator.mutator_configuration_screen.start_game_5zdeds") : support.startLabel(); }
    private String unavailable(Row item) {
        if (item.key()==null || support==null) return "";
        var definition=definition(item.key()).definition();
        if (!support.capabilities(world.getCurrentZone(),world.getCurrentAct()).containsAll(definition.capabilities())) {
            String reason=support.optionUnavailableReason(definition.localId(),item.option()==null?"":item.option());
            return (reason.isBlank() ? UiMessages.text("mutator.mutator_configuration_screen.unavailable_for_this_game_or_art_profile_122i5po") : reason);
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
        catch(java.io.IOException failure) { close(); throw new IllegalStateException(UiMessages.text("mutator.mutator_configuration_screen.menu_font_unavailable_h1q2yo"),failure); }
    }
    private int width() { return Math.max(320,GameServices.graphics().getProjectionWidth()); }
    private int panelWidth() { return Math.min(376,width()-24); }
    private int left() { return (width()-panelWidth())/2; }
    private boolean homeCard() { return page==Page.HOME && !open; }
    private int cardWidth() { return panelWidth(); }
    private int rowLeft() { return homeCard() ? (width()-cardWidth())/2+6 : left()+5; }
    private int rowWidth() { return homeCard() ? cardWidth()-12 : panelWidth()-10; }
    private int footerTop() { return Math.min(FOOTER_TOP, 224 - 2 * (MenuChrome.compactLineHeight() + 2) - 6); }
    private int detailTop() { return Math.min(DETAIL_TOP, footerTop() - 2 * MenuChrome.compactLineHeight() - 3); }
    private int homeHeight() { return Math.max(HOME_HEIGHT, MenuChrome.compactLineHeight() + 6 + 3 * rowHeight()); }
    private int homeTop() { return Math.min(HOME_TOP, footerTop() + 7 - homeHeight()); }
    private int rowsTop() { return homeCard() ? homeTop()+MenuChrome.compactLineHeight()+6 : page==Page.HELP ? Math.min(176,footerTop()-rowHeight()-3) : 58; }
    private boolean groupedOptions() {
        return page==Page.OPTIONS && definition(selectedKey).definition().options().stream()
                .anyMatch(option -> option instanceof MutatorOption.Checkbox check && !check.group().isBlank());
    }
    private int rowHeight() { return Math.max(homeCard() ? HOME_ROW : page==Page.HELP || singleLineOptions() ? 15 : 24, MenuChrome.compactLineHeight()+5); }
    /**
     * A row is two lines only when it has its own second line (a status tag or a timing other than
     * the band's); rows that share the band's timing, and action rows, are one line.
     */
    private boolean secondLine(Row item) {
        return !homeCard() && page!=Page.HELP && item.key()!=null && !singleLineOptions()
                && !(statusTag(item).isBlank() && rowGuidance(item).equals(sharedBoundary()));
    }
    private int rowHeight(Row item) { return Math.max(homeCard() ? HOME_ROW : secondLine(item) ? 24 : 15, MenuChrome.compactLineHeight()*(secondLine(item)?2:1)+5); }
    private int capacity() { return page==Page.HOME?3:7; }
    /**
     * Options rows are one line when nothing row-specific remains to say: grouped checkboxes, or
     * every row applying at the same boundary, which the band names once ({@link #sharedBoundary}).
     */
    private boolean singleLineOptions() {
        if (groupedOptions()) return true;
        String shared=sharedBoundary();
        return shared!=null && rows().stream().filter(item -> item.key()!=null)
                .allMatch(item -> statusTag(item).isBlank() && shared.equals(rowGuidance(item)));
    }
    /**
     * On the mutator list and an options page, the timing most rows share ("Applies at Resume"),
     * named once in the section band; rows that differ (pending, unavailable, another boundary)
     * keep their own line.
     */
    private String sharedBoundary() {
        if (page!=Page.OPTIONS && page!=Page.LIST) return null;
        Map<String,Integer> counts=new java.util.LinkedHashMap<>();
        for (Row item:rows()) if (item.key()!=null && statusTag(item).isBlank()) counts.merge(rowGuidance(item),1,Integer::sum);
        return counts.entrySet().stream().filter(e -> e.getValue()>=2 || counts.size()==1)
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }
    private int firstVisibleRow() {
        int first=Math.max(0,row-capacity()+1);
        if (!homeCard()) {
            var visible=rows();
            while (first<row && rowY(visible,first,row,rowsTop())+rowHeight(visible.get(row))>detailTop()-3) first++;
        }
        return first;
    }
    private int visibleCount(List<Row> visible,int first) {
        int count=Math.min(capacity(),visible.size()-first);
        if (!homeCard()) while (count>1
                && rowY(visible,first,first+count-1,rowsTop())+rowHeight(visible.get(first+count-1))>detailTop()-3) count--;
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
        int y=top;
        for(int i=first;i<index;i++) y+=rowHeight(visible.get(i));
        for(int i=first;i<=index;i++) if(groupStarts(visible,first,i)) y+=Math.max(12,MenuChrome.compactLineHeight()+4);
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
            if (details != null) details.render(font, width());
            else if (page==Page.HOME && !overlay) renderHome(); else renderPage(overlay);
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
        int y0=homeTop()+slide;
        MenuChrome.header(font,width(),MenuChrome.fitLabel(title,width()-92),UiMessages.text("mutator.menu.new_game"));
        // Preserve the native emblem's original clearance; the title lives in the shared header.
        MenuChrome.fill(font,cx,y0,cw,homeHeight(),MenuChrome.INK,.96f);
        MenuChrome.fill(font,cx,y0,cw,1,MenuChrome.EDGE);
        MenuChrome.compact(font,MenuChrome.fitCompact(status.isBlank()
                ? UiMessages.text("mutator.mutator_configuration_screen.mutators_native_rom_play_bwf9m2", Map.of("value0",Integer.toString(settings.definitions().size()))) : status,cw-16),
                cx+8,y0+3,status.isBlank()?MenuChrome.MUTED:MenuChrome.RED);
        renderRows(rows(),rowLeft(),rowsTop()+slide,rowWidth(),rowHeight(),false);
        renderFooter();
    }
    private void renderPage(boolean overlay) {
        int w=width(), x=left(), pw=panelWidth();
        String location=inGameplay && !MutatorWorldAccess.supportedCell(world)
                ? UiMessages.text("mutator.mutator_configuration_screen.not_supported_here_effects_are_suspended_dmbgr5") : support==null ? UiMessages.text("mutator.mutator_configuration_screen.native_gameplay_mxqwhb")
                : support.locationLabel(world.getCurrentZone(),world.getCurrentAct());
        Row current=rows().get(Math.min(row,rows().size()-1));
        String scope=page==Page.HELP?UiMessages.text("mutator.menu.help"):inGameplay?UiMessages.text("mutator.menu.play_held"):UiMessages.text("mutator.menu.new_game");
        String tag=page==Page.OPTIONS?statusTag(current):"";
        // Each option row already names when it applies; the band keeps naming where you are, as on
        // the list page, instead of repeating the focused row's timing a third time.
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
            String caption=page==Page.OPTIONS?definition(selectedKey).definition().title():UiMessages.text("mutator.mutator_configuration_screen.mutators_fdva29");
            String position=(row+1)+" / "+rows().size();
            String shared=sharedBoundary();
            int captionRoom=shared==null?pw-76:pw-81-MenuChrome.compactWidth(shared);
            MenuChrome.section(font,MenuChrome.fitCompact(caption.toUpperCase(Locale.ROOT),captionRoom),x+5,47,pw-10);
            MenuChrome.fill(font,x+pw-64,46,64,10,MenuChrome.INK,.95f);
            MenuChrome.compact(font,position,x+pw-5-MenuChrome.compactWidth(position),47,MenuChrome.MUTED);
            if (shared!=null) {
                int sx=x+pw-64-4-MenuChrome.compactWidth(shared);
                MenuChrome.fill(font,sx-4,46,MenuChrome.compactWidth(shared)+8,10,MenuChrome.INK,.95f);
                MenuChrome.compact(font,shared,sx,47,MenuChrome.MUTED);
            }
        }
        var visible=rows();
        renderRows(visible,rowLeft(),rowsTop(),rowWidth(),rowHeight(),page!=Page.HELP);
        if (page!=Page.HELP) {
            var lines=MenuStyle.wrap(detail(visible),pw-12,MenuStyle.COMPACT);
            MenuChrome.descriptionBox(font,x,detailTop(),pw,footerTop()-detailTop(),lines,
                    List.of(status.isBlank()?MenuChrome.TEXT:MenuChrome.RED,MenuChrome.MUTED),age);
        }
        renderFooter();
    }
    /** Controls quote the live bindings: keyboard names while typing, the pad's own buttons on a pad. */
    private String[][] helpSections() {
        String[][] sections={
            {UiMessages.text("mutator.mutator_configuration_screen.controls_1qq28ra"), UiMessages.text("mutator.mutator_configuration_screen.run_jump_6e0cwc", Map.of("value0", String.valueOf(move()), "value1", String.valueOf(jump()))), UiMessages.text("mutator.mutator_configuration_screen.settings_while_playing_12vvayr", Map.of("value0", String.valueOf(settingsKeys())))},
            {UiMessages.text("mutator.mutator_configuration_screen.applying_edits_m0o76l"), UiMessages.text("mutator.mutator_configuration_screen.resume_applies_live_edits_b56pv6"), UiMessages.text("mutator.mutator_configuration_screen.restart_rebuilds_from_the_act_start_x4hwaw"),
                    UiMessages.text("mutator.mutator_configuration_screen.return_to_the_hub_to_begin_a_new_game_bd6agg")},
            {UiMessages.text("mutator.mutator_configuration_screen.mutators_fdva29"), UiMessages.text("mutator.mutator_configuration_screen.choose_an_effect_then_adjust_its_options_1sm3qqt"), UiMessages.text("mutator.mutator_configuration_screen.every_effect_starts_off_mix_your_own_rules_h18lz2"),
                    UiMessages.text("mutator.mutator_configuration_screen.stage_puzzles_keep_their_native_ring_rules_1wwk1oy")}};
        for (var section:sections) section[0]=section[0].toUpperCase(UiMessages.locale());
        return sections;
    }
    private record HelpLine(String text, boolean heading) { }
    /** Wrap every complete sentence before partitioning pages, including tall-script headings. */
    private List<HelpLine> helpLines(int pw) {
        var lines = new ArrayList<HelpLine>();
        for (var section : helpSections()) for (int i=0;i<section.length;i++) {
            boolean heading = i==0;
            for (var line : com.openggf.graphics.text.HostText.shared().wrap(section[i],
                    pw-(heading?20:26), com.openggf.graphics.text.TextFace.COMPACT,
                    com.openggf.graphics.text.HostTextContext.current())) lines.add(new HelpLine(line, heading));
        }
        return List.copyOf(lines);
    }
    private int helpCapacity() {
        int pitch=MenuChrome.compactLineHeight()+2;
        return Math.max(1,(rowsTop()-49-pitch-5)/pitch);
    }
    private int helpPageCount() { return Math.max(1,(helpLines(panelWidth()).size()+helpCapacity()-1)/helpCapacity()); }
    private void renderHelp(int x, int pw) {
        int pitch=MenuChrome.compactLineHeight()+2, capacity=helpCapacity();
        var lines=helpLines(pw);
        helpPageCount=Math.max(1,(lines.size()+capacity-1)/capacity);
        helpPage=Math.clamp(helpPage,0,helpPageCount-1);
        int first=helpPage*capacity,y=49;
        for(int i=first;i<Math.min(lines.size(),first+capacity);i++,y+=pitch) {
            boolean heading=lines.get(i).heading();
            if (heading) MenuChrome.section(font,lines.get(i).text(),x+8,y,pw-16);
            else MenuChrome.compact(font,lines.get(i).text(),x+12,y,MenuChrome.TEXT);
        }
        if(helpPageCount>1)MenuStyle.text(font,UiMessages.text("mutator.help.page",
                Map.of("page",helpPage+1,"pages",helpPageCount)),x+10,rowsTop()-pitch-2,pw-20,.68f,.89f,1);
    }

    private void renderFooter() {
        Row focused=rows().get(Math.min(row,rows().size()-1));
        var hints=new ArrayList<MenuChrome.Hint>();
        if (page!=Page.HELP) hints.add(MenuChrome.upDown(lastInput,UiMessages.text("mutator.menu.choose")));
        else if(helpPageCount>1) hints.add(MenuChrome.leftRight(lastInput,UiMessages.text("mutator.menu.page")));
        if (editable(focused)) hints.add(MenuChrome.leftRight(lastInput,UiMessages.text("mutator.menu.change")));
        // Left/Right already reads CHANGE; Enter names its own action rather than repeating it.
        boolean sliderRow=focused.option()!=null
                && definition(focused.key()).definition().option(focused.option()) instanceof MutatorOption.IntegerSlider;
        String optionVerb=!editable(focused)?UiMessages.text("mutator.menu.why"):sliderRow?UiMessages.text("mutator.menu.step"):UiMessages.text("mutator.menu.toggle");
        // Enter opens catalogue options; it cycles/toggles individual options as before.
        hints.add(MenuChrome.hint(MenuChrome.confirmKey(lastInput),switch(focused.action()) {
            case MUTATOR -> UiMessages.text("mutator.menu.options");
            case ENABLE, OPTION -> optionVerb;
            case START -> UiMessages.text("mutator.menu.start");
            case RESUME -> UiMessages.text("mutator.menu.resume");
            default -> UiMessages.text("mutator.menu.select");
        }));
        // While play is held the list has no Back: Esc only reminds that Resume applies the edits,
        // and the Resume row is the way out, so the footer offers no cap for it.
        if (!(inGameplay && page==Page.LIST))
            hints.add(MenuChrome.hint(MenuChrome.backKey(lastInput),page==Page.HOME && !inGameplay?UiMessages.text("mutator.menu.hub"):UiMessages.text("mutator.menu.back")));
        hints.add(MenuChrome.hint(MenuChrome.detailsKey(lastInput),UiMessages.text("menu.path.details_caps")));
        MenuChrome.footer(font,width(),"",hints,MenuChrome.controller(lastInput),age);
    }
    private void renderRows(List<Row> visible, int x, int top, int width, int rh, boolean values) {
        int first=firstVisibleRow(), count=visibleCount(visible,first);
        // Keep the gold frame outside each row's fill, with room before captions and details.
        for(int i=0;i<count;i++) {
            int index=first+i,y=rowY(visible,first,index,top);
            if(groupStarts(visible,first,index))
                MenuChrome.section(font,group(visible.get(index)).toUpperCase(Locale.ROOT),x+8,y-10,width-16);
            int h=rowHeight(visible.get(first+i));
            MenuChrome.fill(font,x+stagger(i),y,width-stagger(i),(h>=24?h-3:h-4),MenuChrome.NAVY,.96f);
        }
        int shift=stagger(row-first);
        float focus=Math.clamp(highlight,(float)first,(float)(first+count-1));
        int low=(int)Math.floor(focus), high=Math.min(first+count-1,low+1);
        int focusY=Math.round(rowY(visible,first,low,top)+(focus-low)*(rowY(visible,first,high,top)-rowY(visible,first,low,top)));
        int focusH=rowHeight(visible.get(Math.clamp(row,first,first+count-1)));
        MenuChrome.focus(font,x+shift,focusY,width-shift,(focusH>=24?focusH-3:focusH-4),age);
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
                    String shown=on?UiMessages.text("mutator.menu.on"):UiMessages.text("mutator.menu.off");
                    // Switch then word, the same order as Settings rows.
                    int wordX=right-MenuChrome.compactWidth(shown);
                    valueX=wordX-5-MenuChrome.TOGGLE_W;
                    MenuChrome.toggle(font,valueX,textY,on,available);
                    MenuChrome.compact(font,shown,wordX,textY,available?MenuChrome.TEXT:MenuChrome.DIM);
                } else if(slider) {
                    String shown=focused?MenuStyle.pageText(val,54,MenuStyle.COMPACT,pageAge):MenuChrome.fitCompact(val,54);
                    valueX=right-MenuChrome.compactWidth(shown);
                    renderSlider((MutatorOption.IntegerSlider)option,item,valueX-91,textY+3,available);
                    MenuChrome.compact(font,shown,valueX,textY,available?MenuChrome.TEXT:MenuChrome.DIM);
                    valueX-=91;
                } else {
                    String shown=focused?MenuStyle.pageText(val,96,MenuStyle.COMPACT,pageAge):MenuChrome.fitCompact(val,96);
                    valueX=right-MenuChrome.compactWidth(shown);
                    MenuChrome.compact(font,shown,valueX,textY,available?MenuChrome.TEXT:MenuChrome.DIM);
                }
                if(focused && editable(item)) {
                    MenuChrome.triangle(font,valueX-7,textY+1,3,'L',MenuChrome.GOLD,1);
                    MenuChrome.triangle(font,right+4,textY+1,3,'R',MenuChrome.GOLD,1);
                }
            }
            int labelWidth=values && item.key()!=null?valueX-lx-12:width-20-stagger(i);
            MenuChrome.compact(font,focused?MenuStyle.pageText(item.label(),labelWidth,MenuStyle.COMPACT,pageAge):MenuChrome.fitCompact(item.label(),labelWidth),lx,textY,
                    !available?MenuChrome.DIM:focused?MenuChrome.WHITE:MenuChrome.TEXT);
            if(values && secondLine(item)) {
                String tag=statusTag(item);
                int tagX=tag.isBlank()?right:right-MenuChrome.tagWidth(tag);
                if(!tag.isBlank()) MenuChrome.tag(font,tagX,y+MenuChrome.compactLineHeight()+3,tag,!available?MenuChrome.DIM:MenuChrome.AMBER);
                String guidance=rowGuidance(item);
                // The band already names the shared timing; a row speaks only for its exception.
                if(!(tag.isBlank() && guidance.equals(sharedBoundary())))
                    MenuChrome.compact(font,focused?MenuStyle.pageText(guidance,tagX-lx-6,MenuStyle.COMPACT,pageAge):MenuChrome.fitCompact(guidance,tagX-lx-6),lx,y+MenuChrome.compactLineHeight()+4,
                            focused?MenuChrome.MUTED:MenuChrome.DIM);
            }
        }
        if(values) MenuChrome.scrollbar(font,x+width+5,top,detailTop()-top-5,first,count,visible.size());
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
        if(!unavailable(item).isBlank()) return UiMessages.text("mutator.menu.unavailable");
        var edit=settings.pending().stream()
                .filter(p->p.key().equals(item.key()) && Objects.equals(p.optionId(),item.option())).findFirst();
        if(edit.isEmpty()) return "";
        if(edit.get().reason().contains("edit")) return UiMessages.text("mutator.menu.edit_to_apply");
        return !inGameplay?UiMessages.text("mutator.menu.pending_start"):switch(edit.get().scope()) {
            case LIVE -> UiMessages.text("mutator.menu.pending_resume");
            case LOAD -> UiMessages.text("mutator.menu.restart_needed");
            case LAUNCH -> UiMessages.text("mutator.menu.new_game_needed");
        };
    }
    /** The tag names the pending boundary; this line names the value still admitted now. */
    private String rowGuidance(Row item) {
        if(!unavailable(item).isBlank()) return item.option()==null && settings.requested().get(item.key()).enabled()
                ?UiMessages.text("mutator.menu.change_to_switch_off"):UiMessages.text("mutator.menu.not_supported_here");
        if(!pending(item)) return scope(item);
        var admitted=settings.admitted().get(item.key());
        return UiMessages.text("mutator.menu.now_value", Map.of("value", item.option()==null?(admitted.enabled()?UiMessages.text("mutator.menu.on"):UiMessages.text("mutator.menu.off"))
                :format(definition(item.key()).definition().option(item.option()),admitted.options().get(item.option()))));
    }
    /** Rows extend from the right edge in a short cascade after each page change. */
    private int stagger(int index) { return Math.max(0,10-3*(pageAge-index)); }
    private String detail(List<Row> visible) {
        if (!status.isBlank()) return status;
        if (!notice.isBlank()) return notice;
        return rowDetail(visible.get(Math.min(row,visible.size()-1)));
    }
    private String rowDetail(Row current) {
        if (current.key()!=null) {
            String unavailable=unavailable(current);
            if (!unavailable.isBlank()) return unavailable;
            var d=definition(current.key()).definition();
            Set<MutatorCapability> available=support==null?Set.of():support.capabilities(world.getCurrentZone(),world.getCurrentAct());
            if (d.capabilities().contains(MutatorCapability.RINGFALL) && settings.effective().levelPolicy(available).noRings())
                return UiMessages.text("mutator.mutator_configuration_screen.no_rings_prevents_main_level_spills_ringfall_stays_read_13gxzzu");
            if (d.capabilities().contains(MutatorCapability.BIG_HEAD)
                    && settings.effective().stealthPolicies().stream().anyMatch(MutatorPolicy.PlayerStealth::hideBody))
                return UiMessages.text("mutator.mutator_configuration_screen.stealth_hides_selected_bodies_their_head_size_returns_w_gu4vpu");
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
        if (pending>0) return UiMessages.text("mutator.pending_edits",Map.of("count",pending));
        var active=settings.definitions().stream().filter(o->settings.admitted().get(o.key()).enabled())
                .map(o->o.definition().title()).toList();
        return (active.isEmpty() ? UiMessages.text("mutator.mutator_configuration_screen.no_pending_edits_every_mutator_is_off_1vlzrr3") : UiMessages.text("mutator.mutator_configuration_screen.active_fvnyqn", Map.of("value0", String.valueOf(String.join(", ", active)))));
    }
    private String location() {
        return (inGameplay && !MutatorWorldAccess.supportedCell(world) ? UiMessages.text("mutator.mutator_configuration_screen.not_supported_here_effects_are_suspended_dmbgr5") : (support == null ? UiMessages.text("mutator.mutator_configuration_screen.native_gameplay_mxqwhb") : support.locationLabel(world.getCurrentZone(), world.getCurrentAct())));
    }
    private String detailsControl() {
        return UiMessages.text("import.details_control",Map.of("key",lastInput==null?"F1":MenuInput.detailsLabel(lastInput)));
    }
    /** Full source data remains readable even when a higher-priority refusal owns the preview. */
    private String detailsText(Row current) {
        var paragraphs=new ArrayList<String>();
        paragraphs.add(title);
        paragraphs.add(location());
        paragraphs.add(current.label());
        paragraphs.add(page==Page.HELP?UiMessages.text("mutator.menu.help"):inGameplay?UiMessages.text("mutator.menu.play_held"):UiMessages.text("mutator.menu.new_game"));
        if(current.key()!=null) {
            var owned=definition(current.key());var d=owned.definition();
            paragraphs.add(owned.ownerModId());
            paragraphs.add(d.title());
            paragraphs.add(d.description());
            var requested=settings.requested().get(current.key());
            if(current.option()!=null) {
                var option=d.option(current.option());
                paragraphs.add(option.label());
                paragraphs.add(UiMessages.text("menu.text.value_details",Map.of("value",format(option,requested.options().get(current.option())))));
                paragraphs.add(option.help());
                paragraphs.add(group(current));
            } else paragraphs.add(requested.enabled()?UiMessages.text("mutator.value.enabled"):UiMessages.text("mutator.value.disabled"));
            paragraphs.add(scope(current));
            paragraphs.add(statusTag(current));
            paragraphs.add(rowGuidance(current));
            paragraphs.add(unavailable(current));
        }
        paragraphs.add(rowDetail(current));
        paragraphs.add(status);paragraphs.add(notice);
        paragraphs.add(MutatorWorldAccess.saveError(world));
        paragraphs.add(MutatorWorldAccess.admissionError(world));
        if(page==Page.HELP)for(var section:helpSections())paragraphs.addAll(List.of(section));
        paragraphs.add(detailsControl());
        paragraphs.add(inGameplay?UiMessages.text("mutator.mutator_configuration_screen.live_resume_load_restart_launch_new_game_g7jjwv")
                :UiMessages.text("mutator.mutator_configuration_screen.effects_stay_off_until_you_switch_them_on_d5d61m"));
        return String.join("\n\n",paragraphs.stream().filter(p->p!=null&&!p.isBlank()).distinct().toList());
    }
    /** Current-pose guidance never changes editing eligibility or native gameplay. */
    private String leaderHeadPresentationReason() {
        if (!inGameplay || support==null) return "";
        var sprites=GameServices.spritesOrNull();
        var player=sprites==null?null:sprites.getMainPlayable();
        if (player==null) return "";
        if (!support.supportsPlayer(MutatorCapability.BIG_HEAD,player.characterKey().persisted(),true))
            return UiMessages.text("mutator.mutator_configuration_screen.the_leader_s_current_rom_art_keeps_native_head_size_1flzg5n");
        var renderer=player.getSpriteRenderer();
        if (renderer==null) return "";
        if (renderer.headProfileId().isBlank())
            return UiMessages.text("mutator.mutator_configuration_screen.the_leader_s_current_art_has_no_reviewed_head_mask_nati_1doawtx");
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
        return (playerSelected ? UiMessages.text("mutator.mutator_configuration_screen.player_head_at_rings_wnqjun", Map.of("value0", String.valueOf(settings.effective().headScalePercent(true, rings)), "value1", String.valueOf(rings))) : UiMessages.text("mutator.mutator_configuration_screen.sidekicks_use_their_own_rings_enemies_use_player_s_ring_16i3h36", Map.of("value0", String.valueOf(rings))));
    }

    /** Word wrap of compact text to a pixel width; an overlong final line keeps the shared ellipsis. */
    static List<String> wrap(String text, int maxWidth, int maxLines) {
        var lines=com.openggf.graphics.text.HostText.shared().wrap(text.strip(),Math.max(1,maxWidth),
                com.openggf.graphics.text.TextFace.COMPACT,com.openggf.graphics.text.HostTextContext.current());
        if(lines.size()<=maxLines)return lines;
        var visible=new ArrayList<>(lines.subList(0,Math.max(1,maxLines)));
        int last=visible.size()-1;
        visible.set(last,MenuChrome.fitCompact(visible.get(last)+"...",maxWidth));
        return List.copyOf(visible);
    }

    private String move() {
        if (lastInput!=null && MenuInput.controller(lastInput)) return UiMessages.text("mutator.mutator_configuration_screen.d_pad_128gh6");
        var config=GameServices.configuration();
        return UiMessages.text("mutator.mutator_configuration_screen.message_1iaymnk", Map.of("value0", String.valueOf(ButtonPrompts.keyName(config.getInt(SonicConfiguration.LEFT)).orElse("Left")), "value1", String.valueOf(ButtonPrompts.keyName(config.getInt(SonicConfiguration.RIGHT)).orElse("Right"))));
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
            return ButtonPrompts.label(lastInput,0,ButtonPrompts.Button.START).orElse(UiMessages.text("mutator.mutator_configuration_screen.start_1br2gy"));
        var config=GameServices.configuration(); var keys=new LinkedHashSet<String>();
        ButtonPrompts.keyName(config.getInt(SonicConfiguration.PAUSE_KEY)).ifPresent(keys::add);
        ButtonPrompts.keyName(config.getInt(SonicConfiguration.START)).ifPresent(keys::add);
        return (keys.isEmpty() ? UiMessages.text("mutator.mutator_configuration_screen.start_1br2gy") : String.join(" or ", keys));
    }
    private String value(Row item) {
        if(item.key()==null) return "";
        var requested=settings.requested().get(item.key());
        if(item.option()==null && requested.enabled()) return UiMessages.text("mutator.menu.on");
        if (!unavailable(item).isBlank()) return UiMessages.text("mutator.value.unavailable");
        if(item.option()==null) return UiMessages.text("mutator.menu.off");
        return format(definition(item.key()).definition().option(item.option()),requested.options().get(item.option()));
    }
    private static String format(MutatorOption option, Object value) {
        return switch(option) {
            case MutatorOption.Checkbox checkbox -> (Boolean)value?UiMessages.text("mutator.menu.on"):UiMessages.text("mutator.menu.off");
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
            if(item.option()==null && settings.requested().get(item.key()).enabled()) return UiMessages.text("mutator.mutator_configuration_screen.unavailable_here_change_to_switch_off_9sxx44");
            int sentence=unavailable.indexOf(". ");
            return sentence<0?unavailable:unavailable.substring(0,sentence+1);
        }
        var admitted=settings.admitted().get(item.key());
        MutatorScope scope=scopeOf(item);
        var pendingEdit=settings.pending().stream().filter(p->p.key().equals(item.key()) && Objects.equals(p.optionId(),item.option())).findFirst();
        boolean pending=pendingEdit.isPresent();
        if (pending && pendingEdit.get().reason().contains("edit")) return UiMessages.text("mutator.mutator_configuration_screen.history_differs_edit_to_apply_vcctur");
        // Before play every boundary is the Start load; in play the row names the menu choice that applies it.
        // Menu choices keep their capitals (Start, Resume, Restart); events stay lower case.
        String boundary=!inGameplay?UiMessages.text("mutator.boundary.start"):switch(scope){case LIVE->UiMessages.text("mutator.mutator_configuration_screen.resume_14fc571");case LOAD->UiMessages.text("mutator.menu.restart_or_death");case LAUNCH->UiMessages.text("mutator.menu.next_new_game");};
        if(item.option()!=null && pending) return UiMessages.text("mutator.mutator_configuration_screen.pending_now_a5tw1",
                Map.of("value0",boundary,"value1",format(d.option(item.option()),admitted.options().get(item.option()))));
        return UiMessages.text(pending ? "mutator.mutator_configuration_screen.pending_1fb2w32"
                : "mutator.mutator_configuration_screen.applies_at_9kuose",Map.of("value0",boundary,"value1",""));
    }
    @Override public void close() {
        if(font!=null) font.cleanup(); if(renderer!=null) renderer.cleanup();
        font=null; renderer=null; open=false; waitingForFade=false; command=Command.NONE;
    }
}
