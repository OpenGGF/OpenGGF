package com.openggf.game.sonic2.competition;

import com.openggf.control.InputHandler;
import com.openggf.data.Rom;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.nativemenu.NativeMenuScreen;

import java.io.IOException;

import static org.lwjgl.opengl.GL11.glClearColor;

/** Connected native product controller. A provider must check supported() before exposing it. */
public final class Sonic2CompetitionScreen implements NativeMenuScreen {
    private final Sonic2CompetitionSession session;
    private final Sonic2CompetitionHost host;
    private final Rom rom;
    private final Sonic2CompetitiveSpecialStageAdapter special;
    private Sonic2CompetitionAssets assets;
    private Sonic2CompetitionPresentation presentation;
    private Exit exit = Exit.NONE;

    public Sonic2CompetitionScreen(Sonic2CompetitionSession session, Sonic2CompetitionHost host,
                                   Sonic2CompetitiveSpecialStageAdapter special, Rom rom) {
        this.session = java.util.Objects.requireNonNull(session);
        this.host = java.util.Objects.requireNonNull(host);
        this.special = special;
        this.rom = java.util.Objects.requireNonNull(rom);
    }
    public Sonic2CompetitionSession session() { return session; }
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
        session.reset(); exit = Exit.NONE;
        try { assets = Sonic2CompetitionAssets.load(rom); }
        catch (IOException failure) { throw new IllegalStateException("Cannot load native competition assets", failure); }
        presentation = null;
    }
    /** Actual PLC owner signals this only after the result screen's pending art has retired. */
    public void resultsPlcReady() { session.setResultsPlcReady(true); }
    @Override public void update(InputHandler input) {
        if (assets == null) throw new IllegalStateException("Competition screen not initialized");
        var before = session.phase();
        int beforeAct = session.act(), beforeSpecial = session.specialStageIndex();
        session.advancePresentation();
        switch (before) {
            case ACT -> host.step(session, input);
            case SPECIAL_STAGE -> special.step(input);
            case SELECT -> session.updateMenu(input.logical(), supportsSelection(nextSelection(input)));
            default -> session.updateMenu(input.logical(), session.resultsPlcReady());
        }
        if (session.phase() == Sonic2CompetitionSession.Phase.ACT
                && (before != session.phase() || beforeAct != session.act())) host.loadAct(session, session.romZone(), session.act());
        else if (session.phase() == Sonic2CompetitionSession.Phase.SPECIAL_STAGE
                && (before != session.phase() || beforeSpecial != session.specialStageIndex())) special.load();
        if (session.phase() != before) {
            session.setResultsPlcReady(false);
            if (presentation != null) presentation.invalidate();
        }
        if (session.phase() == Sonic2CompetitionSession.Phase.FINISHED)
            exit = new Exit(Destination.TITLE_SCREEN, null, false);
    }
    private int nextSelection(InputHandler input) {
        int selection = session.selection();
        int mask = input.logical().player1().pressedMask() | input.logical().player2().pressedMask();
        if ((mask & 3) != 0) selection ^= 2;
        if ((mask & 12) != 0) selection ^= 1;
        return selection;
    }
    @Override public void draw() {
        if (session.phase() == Sonic2CompetitionSession.Phase.ACT) host.draw(session);
        else if (session.phase() == Sonic2CompetitionSession.Phase.SPECIAL_STAGE) special.draw();
        else if (session.phase() != Sonic2CompetitionSession.Phase.FINISHED) {
            if (presentation == null) try { presentation = new Sonic2CompetitionPresentation(assets, rom, GameServices.graphics()); }
            catch (IOException failure) { throw new IllegalStateException("Cannot load native winner mappings", failure); }
            presentation.draw(session, session.presentationFrame());
        }
    }
    @Override public void setClearColor() {
        if (assets == null) { glClearColor(0, 0, 0, 1); return; }
        // MenuScreen/TwoPlayerResults select VDP background palette/color 0/0 (Pal_Menu's blue).
        var color = assets.menuPalettes()[0].getColor(0);
        glClearColor(color.rFloat(), color.gFloat(), color.bFloat(), 1);
    }
    @Override public void reset() {
        session.reset(); assets = null; presentation = null; exit = Exit.NONE;
        if (special != null) special.reset();
    }
    @Override public Exit consumeExit() { var result = exit; exit = Exit.NONE; return result; }
}
