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
    private int helpPage, helpPageCount = 1;
    // Errors and transition progress stay in status until the next action; notices are transient.
    private String selectedKey, status = "", notice = "", hint = UiMessages.text("mutator.mutator_configuration_screen.arrows_enter_esc_bb5pn9");
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
            rows.add(new Row(selectedKey,null,d.title(),Action.ENABLE));
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
        var choices = rows();
        Row focused = choices.get(Math.min(row, choices.size() - 1));
        hint = (adjustable(focused) ? (page == Page.HOME && !inGameplay ? String.valueOf((String.valueOf((UiMessages.text("mutator.mutator_configuration_screen.choose_change_hub_1bhyuog", Map.of("value0", String.valueOf(MenuInput.directionLabel(input)), "value1", String.valueOf(MenuInput.horizontalLabel(input)), "value2", String.valueOf(MenuInput.backLabel(input)))))))) : String.valueOf((String.valueOf((UiMessages.text("mutator.mutator_configuration_screen.choose_change_back_1fun2wc", Map.of("value0", String.valueOf(MenuInput.directionLabel(input)), "value1", String.valueOf(MenuInput.horizontalLabel(input)), "value2", String.valueOf(MenuInput.backLabel(input))))))))) : (page == Page.HOME && !inGameplay ? String.valueOf((String.valueOf((UiMessages.text("mutator.mutator_configuration_screen.choose_select_hub_65kfmk", Map.of("value0", String.valueOf(MenuInput.directionLabel(input)), "value1", String.valueOf(MenuInput.confirmLabel(input)), "value2", String.valueOf(MenuInput.backLabel(input)))))))) : String.valueOf((String.valueOf((UiMessages.text("mutator.mutator_configuration_screen.choose_select_back_1cq93ts", Map.of("value0", String.valueOf(MenuInput.directionLabel(input)), "value1", String.valueOf(MenuInput.confirmLabel(input)), "value2", String.valueOf(MenuInput.backLabel(input))))))))));
        // A simultaneous cancel/confirm never admits changes.
        if (MenuInput.back(input)) { back(); sound(Cue.NAVIGATE); return; }
        int vertical = (MenuInput.down(input) ? 1 : 0) - (MenuInput.up(input) ? 1 : 0);
        int horizontal = (MenuInput.right(input) ? 1 : 0) - (MenuInput.left(input) ? 1 : 0);
        if (vertical != 0) { row = Math.floorMod(row + vertical, choices.size()); notice = ""; sound(Cue.NAVIGATE); }
        if(page==Page.HELP && horizontal!=0) {
            helpPage=Math.floorMod(helpPage+horizontal,helpPageCount);
            sound(Cue.NAVIGATE); return;
        }
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
    private String startLabel() { return (support == null ? UiMessages.text("mutator.mutator_configuration_screen.start_game_5zdeds") : support.startLabel()); }
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
    private int homeHeight() { return Math.max(HOME_HEIGHT, MenuChrome.labelLineHeight() + 2 * MenuChrome.compactLineHeight() + 12 + 3 * rowHeight()); }
    private int homeTop() { return 224 - homeHeight(); }
    private int rowsTop() { return homeCard() ? homeTop()+MenuChrome.labelLineHeight()+MenuChrome.compactLineHeight()+8
            : page==Page.HELP ? Math.min(176, footerTop()-rowHeight()-3) : 58; }
    private boolean groupedOptions() {
        return page==Page.OPTIONS && definition(selectedKey).definition().options().stream()
                .anyMatch(option -> option instanceof MutatorOption.Checkbox check && !check.group().isBlank());
    }
    private int rowHeight() { return Math.max(homeCard() ? HOME_ROW : page==Page.HELP || groupedOptions() ? 15 : 24, MenuChrome.compactLineHeight() + 5); }
    private int capacity() { return page==Page.HOME?3:groupedOptions()?7:5; }
    private int firstVisibleRow() {
        int first=Math.max(0,row-capacity()+1);
        if (!homeCard() && page!=Page.HELP) {
            var visible=rows();
            while (first<row && rowY(visible,first,row,rowsTop())+rowHeight()>detailTop()-3) first++;
        }
        return first;
    }
    private int visibleCount(List<Row> visible,int first) {
        int count=Math.min(capacity(),visible.size()-first);
        if (!homeCard() && page!=Page.HELP) while (count>1
                && rowY(visible,first,first+count-1,rowsTop())+rowHeight()>detailTop()-3) count--;
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
        int y0=homeTop()+slide;
        MenuStyle.fill(font,cx,y0,cw,homeHeight(),.012f,.035f,.09f,.93f);
        MenuStyle.fill(font,cx,y0,cw,1,1,.78f,.23f,1);
        MenuStyle.label(font,title,cx+8,y0+4,cw-16,.55f,1,.9f);
        boolean error=!status.isBlank();
        MenuStyle.text(font,(error ? status : UiMessages.text("mutator.mutator_configuration_screen.mutators_native_rom_play_bwf9m2", Map.of("value0", String.valueOf(settings.definitions().size())))),cx+8,y0+MenuChrome.labelLineHeight()+5,cw-16,
                1,error?.62f:.86f,error?.45f:.45f);
        renderRows(rows(),rowLeft(),rowsTop()+slide,rowWidth(),rowHeight(),false);
        MenuStyle.text(font,hint,cx+8,y0+homeHeight()-MenuChrome.compactLineHeight()-2,cw-16,.62f,.8f,.95f);
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
        MenuStyle.text(font,(inGameplay && !MutatorWorldAccess.supportedCell(world) ? UiMessages.text("mutator.mutator_configuration_screen.not_supported_here_effects_are_suspended_dmbgr5") : (support == null ? UiMessages.text("mutator.mutator_configuration_screen.native_gameplay_mxqwhb") : support.locationLabel(world.getCurrentZone(), world.getCurrentAct()))),x+10,40,pw-20,.76f,.86f,1);
        if (page==Page.HELP) renderHelp(x,pw);
        var visible=rows();
        renderRows(visible,rowLeft(),rowsTop(),rowWidth(),rowHeight(),page!=Page.HELP);
        if (page!=Page.HELP) {
            MenuStyle.fill(font,x,detailTop(),pw,footerTop()-detailTop(),.018f,.075f,.15f,.94f);
            var lines=wrap(detail(visible),(pw-16)/MenuPixelFont.glyphAdvance(MenuStyle.COMPACT),2);
            for (int i=0;i<lines.size();i++) MenuStyle.text(font,lines.get(i),x+8,detailTop()+1+i*(MenuChrome.compactLineHeight()+1),pw-16,1,.83f,.45f);
        }
        MenuStyle.fill(font,0,footerTop(),w,224-footerTop(),.008f,.02f,.04f,1);
        MenuStyle.text(font,hint,x+8,footerTop()+3,pw-16,.68f,.89f,1);
        MenuStyle.text(font,(inGameplay ? UiMessages.text("mutator.mutator_configuration_screen.live_resume_load_restart_launch_new_game_g7jjwv") : UiMessages.text("mutator.mutator_configuration_screen.effects_stay_off_until_you_switch_them_on_d5d61m")),x+8,footerTop()+5+MenuChrome.compactLineHeight(),pw-16,.75f,.84f,.96f);
    }
    /** Controls quote the live bindings: keyboard names while typing, the pad's own buttons on a pad. */
    private void renderHelp(int x, int pw) {
        String[][] sections={
            {UiMessages.text("mutator.mutator_configuration_screen.controls_1qq28ra"), UiMessages.text("mutator.mutator_configuration_screen.run_jump_6e0cwc", Map.of("value0", String.valueOf(move()), "value1", String.valueOf(jump()))), UiMessages.text("mutator.mutator_configuration_screen.settings_while_playing_12vvayr", Map.of("value0", String.valueOf(settingsKeys())))},
            {UiMessages.text("mutator.mutator_configuration_screen.applying_edits_m0o76l"), UiMessages.text("mutator.mutator_configuration_screen.resume_applies_live_edits_b56pv6"), UiMessages.text("mutator.mutator_configuration_screen.restart_rebuilds_from_the_act_start_x4hwaw"),
                    UiMessages.text("mutator.mutator_configuration_screen.return_to_the_hub_to_begin_a_new_game_bd6agg")},
            {UiMessages.text("mutator.mutator_configuration_screen.mutators_fdva29"), UiMessages.text("mutator.mutator_configuration_screen.choose_an_effect_then_adjust_its_options_1sm3qqt"), UiMessages.text("mutator.mutator_configuration_screen.every_effect_starts_off_mix_your_own_rules_h18lz2"),
                    UiMessages.text("mutator.mutator_configuration_screen.stage_puzzles_keep_their_native_ring_rules_1wwk1oy")}};
        int pitch=MenuChrome.compactLineHeight()+2;
        int capacity=Math.max(1,(rowsTop()-56-pitch-5)/pitch);
        var lines=new ArrayList<String>();
        var headings=new java.util.HashSet<Integer>();
        for(String[] section:sections) {
            headings.add(lines.size());
            lines.addAll(List.of(section));
        }
        helpPageCount=Math.max(1,(lines.size()+capacity-1)/capacity);
        helpPage=Math.clamp(helpPage,0,helpPageCount-1);
        int first=helpPage*capacity,y=56;
        for(int i=first;i<Math.min(lines.size(),first+capacity);i++,y+=pitch) {
            boolean heading=headings.contains(i);
            MenuStyle.text(font,lines.get(i),x+(heading?10:16),y,pw-(heading?20:26),
                    heading?1:.83f,heading?.78f:.9f,heading?.23f:1);
        }
        if(helpPageCount>1)MenuStyle.text(font,UiMessages.text("mutator.help.page",
                Map.of("page",helpPage+1,"pages",helpPageCount)),x+10,rowsTop()-pitch-2,pw-20,.68f,.89f,1);
    }

    private void renderRows(List<Row> visible, int x, int top, int width, int rh, boolean values) {
        // Schema bounds can exceed one page; keep the focused row and its neighbors visible.
        int capacity=capacity(), first=firstVisibleRow();
        int count=visibleCount(visible,first);
        // Backgrounds, then the moving focus, then every label: the sliding focus never hides text.
        for(int i=0;i<count;i++) {
            int index=first+i,y=rowY(visible,first,index,top);
            if(groupStarts(visible,first,index))
                MenuStyle.text(font,group(visible.get(index)),x+7,y-10,width-14,1,.78f,.23f);
            MenuStyle.fill(font,x+stagger(i),y,width-stagger(i),rh-1,.025f,.1f,.19f,.94f);
        }
        int focusShift=stagger(row-first);
        float focus=Math.clamp(highlight,(float)first,(float)(first+count-1));
        int low=(int)Math.floor(focus),high=Math.min(first+count-1,low+1);
        float focusY=rowY(visible,first,low,top)+(focus-low)*(rowY(visible,first,high,top)-rowY(visible,first,low,top));
        MenuStyle.focus(font,x+focusShift,Math.round(focusY),width-focusShift,rh-1);
        for(int i=0;i<count;i++) {
            Row item=visible.get(first+i); int y=rowY(visible,first,first+i,top), lx=x+7+stagger(i);
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
            String scope=scope(item); if(rh>=24 && !scope.isEmpty()) MenuStyle.text(font,scope,lx,y+13,
                    option instanceof MutatorOption.IntegerSlider?width-100:width-14,.74f,.83f,.96f);
            if(option instanceof MutatorOption.IntegerSlider slider) {
                int number=(Integer)settings.requested().get(item.key()).options().get(item.option());
                int track=72, tx=x+width-track-8, ty=y+(rh>=24?16:11);
                long span=Math.max(1L,(long)slider.maximum()-slider.minimum());
                int fill=(int)(((long)number-slider.minimum())*track/span);
                int native_=(int)(((long)slider.defaultValue()-slider.minimum())*track/span);
                MenuStyle.fill(font,tx,ty,track,3,.15f,.25f,.36f,1);
                MenuStyle.fill(font,tx,ty,Math.max(1,fill),3,.4f,1,.8f,1);
                MenuStyle.fill(font,tx+native_,ty-1,1,rh>=24?7:4,1,.78f,.23f,1);
                MenuStyle.fill(font,tx+Math.min(track-2,fill)-1,ty-1,3,rh>=24?7:4,focused?1:.75f,1,focused?1:.9f,1);
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

    /** Word wrap on the compact grid; an overlong final line keeps the shared ellipsis. */
    static List<String> wrap(String text, int columns, int maxLines) {
        var lines=com.openggf.graphics.text.HostText.shared().wrap(text.strip().replaceAll("\\s+", " "),
                Math.max(1,columns*MenuPixelFont.glyphAdvance(MenuStyle.COMPACT)),
                com.openggf.graphics.text.TextFace.COMPACT,com.openggf.graphics.text.HostTextContext.current());
        if(lines.size()<=maxLines)return lines;
        var visible=new ArrayList<>(lines.subList(0,Math.max(1,maxLines)));
        int last=visible.size()-1;
        visible.set(last,MenuStyle.fit(visible.get(last)+"...",columns*MenuPixelFont.glyphAdvance(MenuStyle.COMPACT)));
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
        if(item.option()==null && requested.enabled()) return UiMessages.text("mutator.value.enabled");
        if (!unavailable(item).isBlank()) return UiMessages.text("mutator.value.unavailable");
        if(item.option()==null) return UiMessages.text("mutator.value.disabled");
        return format(definition(item.key()).definition().option(item.option()),requested.options().get(item.option()));
    }
    private static String format(MutatorOption option, Object value) {
        return switch(option) {
            case MutatorOption.Checkbox checkbox -> ((Boolean) value ? UiMessages.text("mutator.mutator_configuration_screen.x_on_18yr0kf") : UiMessages.text("mutator.mutator_configuration_screen.off_2o5p8n"));
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
        String boundary=!inGameplay?UiMessages.text("mutator.boundary.start"):switch(scope){case LIVE->UiMessages.text("mutator.mutator_configuration_screen.resume_14fc571");case LOAD->UiMessages.text("mutator.mutator_configuration_screen.restart_or_death_1f1ktt4");case LAUNCH->UiMessages.text("mutator.mutator_configuration_screen.new_game_lt5h0y");};
        if(item.option()!=null && pending) return UiMessages.text("mutator.mutator_configuration_screen.pending_now_a5tw1", Map.of("value0", String.valueOf(boundary), "value1", String.valueOf(format(d.option(item.option()), admitted.options().get(item.option())).replace("[x] ", "").replace("[ ] ", ""))));
        String options=item.action()==Action.MUTATOR && lastInput!=null?" / "+MenuInput.confirmLabel(lastInput)+": options":"";
        return (pending ? UiMessages.text("mutator.mutator_configuration_screen.pending_1fb2w32", Map.of("value0", String.valueOf(boundary), "value1", String.valueOf(options))) : UiMessages.text("mutator.mutator_configuration_screen.applies_at_9kuose", Map.of("value0", String.valueOf(boundary), "value1", String.valueOf(options))));
    }
    @Override public void close() {
        if(font!=null) font.cleanup(); if(renderer!=null) renderer.cleanup();
        font=null; renderer=null; open=false; waitingForFade=false; command=Command.NONE;
    }
}
