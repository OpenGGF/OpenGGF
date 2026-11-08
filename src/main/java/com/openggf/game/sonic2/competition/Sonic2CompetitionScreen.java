package com.openggf.game.sonic2.competition;

import com.openggf.control.InputHandler;
import com.openggf.data.Rom;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.nativemenu.NativeMenuScreen;

import java.io.IOException;

import static org.lwjgl.opengl.GL11.glClearColor;

/** Connected native product controller. A provider must check supported() before exposing it. */
public final class Sonic2CompetitionScreen implements NativeMenuScreen, com.openggf.game.rewind.RewindSnapshottable<Sonic2CompetitionScreen.State> {
    private final Sonic2CompetitionSession session;
    private final Sonic2CompetitionHost host;
    private final Rom rom;
    private final Sonic2CompetitiveSpecialStageAdapter special;
    private Sonic2CompetitionAssets assets;
    private Sonic2CompetitionPresentation presentation;
    private Exit exit = Exit.NONE;
    private PendingLoad pendingLoad;
    private final java.util.function.Supplier<com.openggf.game.sonic2.resources.Sonic2PlcService> plcOwner;
    private com.openggf.game.GameStateManager entryProgress;
    private boolean optionsEntry;
    private int fadePass;
    private boolean fadeIn;
    private Sonic2CompetitionSession.Snapshot destination;
    public record State(int fadePass, boolean fadeIn, Sonic2CompetitionSession.Snapshot destination) { }
    @Override public String key() { return "s2-competition-menu-flow"; }
    @Override public State capture() { return new State(fadePass, fadeIn, destination); }
    @Override public void restore(State state) {
        fadePass = state.fadePass(); fadeIn = state.fadeIn(); destination = state.destination();
        pendingLoad = null;
    }
    @Override public void resetForMissingSnapshot() { fadePass = 0; fadeIn = false; destination = null; }


    /** Resolve the current PLC owner after each world/load replacement, never a retired queue. */
    public Sonic2CompetitionScreen(Sonic2CompetitionSession session, Sonic2CompetitionHost host,
            Sonic2CompetitiveSpecialStageAdapter special, Rom rom,
            java.util.function.Supplier<com.openggf.game.sonic2.resources.Sonic2PlcService> plcOwner) {
        this.session = java.util.Objects.requireNonNull(session);
        this.host = java.util.Objects.requireNonNull(host);
        this.special = special;
        this.rom = java.util.Objects.requireNonNull(rom);
        this.plcOwner = java.util.Objects.requireNonNull(plcOwner);
    }
    /** Called by the provider before initialize; options retains emeralds, title clears them. */
    public void setEntryProgress(com.openggf.game.GameStateManager progress, boolean fromOptions) {
        entryProgress = java.util.Objects.requireNonNull(progress); optionsEntry = fromOptions;
    }

    private record PendingLoad(Sonic2CompetitionSession.Phase phase, int zone, int act, int specialIndex) { }

    public Sonic2CompetitionScreen(Sonic2CompetitionSession session, Sonic2CompetitionHost host,
                                   Sonic2CompetitiveSpecialStageAdapter special, Rom rom) {
        this(session, host, special, rom, () -> null);
    }
    public Sonic2CompetitionSession session() { return session; }
    public boolean actTransitionPending() { return destination != null; }
    /** A full zone includes its native tiebreaker, not just decodable terrain. */
    public boolean supportsSelection(int selection) {
        if (selection < 0 || selection > 3 || special == null) return false;
        if (selection == 3) return special.supportsStage(4) && special.supportsStage(5) && special.supportsStage(6);
        int zone = selection == 0 ? 0 : selection == 1 ? 11 : 12;
        return host.supportsZone(zone) && special.supportsStage(selection + 1);
    }
    public boolean supported() {
        for (int i = 0; i < 4; i++) if (supportsSelection(i)) return true;
        return false;
    }
    @Override public GameMode mode() {
        return switch (session.phase()) {
            case SELECT -> GameMode.COMPETITION_MENU;
            case ACT, SPECIAL_STAGE -> GameMode.COMPETITION;
            default -> GameMode.COMPETITION_RESULTS;
        };
    }
    @Override public void initialize() {
        GameServices.graphics().getFadeManager().clearOverlayForImmediatePaletteLoad();
        session.initializeEntry(entryProgress, optionsEntry);
        session.playSelectMusic(GameServices.audio()); exit = Exit.NONE; pendingLoad = null;
        try { assets = Sonic2CompetitionAssets.load(rom); }
        catch (IOException failure) { throw new IllegalStateException("Cannot load native competition assets", failure); }
        presentation = null;
        // MenuScreen (s2.asm:11817-11824) owns one initial menu wait before its 22 fade waits.
        fadePass = -1; fadeIn = true; destination = null;
    }
    /** Actual PLC owner signals this only after the result screen's pending art has retired. */
    public void resultsPlcReady() { session.setResultsPlcReady(true); }
    @Override public void update(InputHandler input) {
        pendingLoad = null;
        if (assets == null) throw new IllegalStateException("Competition screen not initialized");
        // MenuScreen (s2.asm:11717-11824).
        // Pal_FadeFromBlack (s2.asm:3262-3285).
        if (fadeIn) {
            if (++fadePass == 22) { fadeIn = false; fadePass = 0; clearFlowFade(); }
            return;
        }
        // Level owns the outgoing 22-pass Pal_FadeToBlack before loading Obj34.
        // Level (s2.asm:4756-4765). No gameplay runs during these waits.
        if (destination != null) {
            if (++fadePass == 22) {
                session.restore(destination); destination = null; fadePass = 0; clearFlowFade();
                pendingLoad = new PendingLoad(session.phase(), session.romZone(), session.act(), session.specialStageIndex());
            }
            return;
        }
        var source = session.capture();
        var before = session.phase();
        int beforeAct = session.act(), beforeSpecial = session.specialStageIndex();
        syncResultsAudio();
        var plc = plcOwner.get();
        if (plc != null) session.setResultsPlcReady(!plc.isBusy());
        session.advancePresentation();
        switch (before) {
            case ACT -> {
                host.step(session, input);
                if (host.consumePendingTitleExit()) {
                    exit = new Exit(Destination.TITLE_SCREEN, null, false);
                    pendingLoad = null;
                    return;
                }
            }
            case SPECIAL_STAGE -> special.step(input);
            case SELECT -> {
                int selected = nextSelection(input);
                if (session.completed(selected) && (input.logical().player1().startPressed()
                        || input.logical().player2().startPressed()))
                    GameServices.audio().playSfx(com.openggf.audio.GameSound.ERROR);
                session.updateMenu(input.logical(), supportsSelection(selected));
            }
            default -> session.updateMenu(input.logical(), session.resultsPlcReady());
        }
        if (session.phase() == Sonic2CompetitionSession.Phase.ACT
                && (before != session.phase() || beforeAct != session.act()))
        {
            destination = session.capture(); session.restore(source); fadePass = 0;
            return;
        }
        else if (session.phase() == Sonic2CompetitionSession.Phase.SPECIAL_STAGE
                && (before != session.phase() || beforeSpecial != session.specialStageIndex()))
            pendingLoad = new PendingLoad(session.phase(), session.romZone(), session.act(), session.specialStageIndex());
        if (session.phase() != before) {
            session.setResultsPlcReady(false);
            if (presentation != null) presentation.invalidate();
            if (session.phase() == Sonic2CompetitionSession.Phase.SELECT) session.playSelectMusic(GameServices.audio());
            syncResultsAudio();
        }
        if (session.phase() == Sonic2CompetitionSession.Phase.FINISHED)
            exit = new Exit(Destination.SEGA_SCREEN, null, false);
    }
    private void syncResultsAudio() {
        switch (session.phase()) {
            case ACT_RESULTS, ZONE_RESULTS, GAME_RESULTS, SPECIAL_ACT_RESULTS, SPECIAL_ZONE_RESULTS ->
                    session.playResultsMusic(GameServices.audio());
            default -> { }
        }
    }
    @Override public void afterFrame() {
        var load = pendingLoad;
        pendingLoad = null;
        if (load == null || session.phase() != load.phase()
                || session.romZone() != load.zone() || session.act() != load.act()) return;
        if (load.phase() == Sonic2CompetitionSession.Phase.ACT) host.loadAct(session, load.zone(), load.act());
        else if (session.specialStageIndex() == load.specialIndex()) special.load();
    }
    private int nextSelection(InputHandler input) {
        int selection = session.selection();
        int mask = input.logical().player1().pressedMask() | input.logical().player2().pressedMask();
        if ((mask & 3) != 0) selection ^= 2;
        if ((mask & 12) != 0) selection ^= 1;
        return selection;
    }
    @Override public void draw() {
        var graphics = GameServices.graphics();
        var mode = fadeIn ? com.openggf.graphics.PaletteFadePresentation.Mode.FROM_BLACK
                : destination != null ? com.openggf.graphics.PaletteFadePresentation.Mode.TO_BLACK
                : com.openggf.graphics.PaletteFadePresentation.Mode.NONE;
        if (fadeIn || destination != null) graphics.setPaletteFadePresentation(mode, fadePass, -1);
        if (session.phase() == Sonic2CompetitionSession.Phase.ACT) host.draw(session);
        else if (session.phase() == Sonic2CompetitionSession.Phase.SPECIAL_STAGE) special.draw();
        else if (session.phase() != Sonic2CompetitionSession.Phase.FINISHED) {
            if (presentation == null) try { presentation = new Sonic2CompetitionPresentation(assets, rom, GameServices.graphics()); }
            catch (IOException failure) { throw new IllegalStateException("Cannot load native winner mappings", failure); }
            presentation.draw(session, session.presentationFrame());
        }
    }
    @Override public void setClearColor() {
        if (assets == null || session.phase() == Sonic2CompetitionSession.Phase.ACT) { glClearColor(0, 0, 0, 1); return; }
        // MenuScreen/TwoPlayerResults select VDP background palette/color 0/0 (Pal_Menu's blue).
        var color = assets.menuPalettes()[0].getColor(0);
        var mode = fadeIn ? com.openggf.graphics.PaletteFadePresentation.Mode.FROM_BLACK
                : destination != null ? com.openggf.graphics.PaletteFadePresentation.Mode.TO_BLACK
                : com.openggf.graphics.PaletteFadePresentation.Mode.NONE;
        int rgb = com.openggf.graphics.PaletteFadePresentation.fadeRgb(color.r & 255, color.g & 255, color.b & 255, mode, fadePass);
        int displayed = com.openggf.graphics.color.DisplayColorConverter.toPackedRgb(
                (rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255,
                GameServices.graphics().getDisplayColorProfile(), GameServices.graphics().getDisplayColorExpansion());
        glClearColor(com.openggf.level.PaletteDisplayColor.red(displayed),
                com.openggf.level.PaletteDisplayColor.green(displayed),
                com.openggf.level.PaletteDisplayColor.blue(displayed), 1);
    }
    private void clearFlowFade() {
        GameServices.graphics().setPaletteFadePresentation(com.openggf.graphics.PaletteFadePresentation.Mode.NONE, 0, -1);
    }
    @Override public void reset() {
        clearFlowFade();
        resetForMissingSnapshot(); session.reset(); assets = null; presentation = null; exit = Exit.NONE; pendingLoad = null;
        if (special != null) special.reset();
    }
    @Override public Exit consumeExit() { var result = exit; exit = Exit.NONE; return result; }
}
