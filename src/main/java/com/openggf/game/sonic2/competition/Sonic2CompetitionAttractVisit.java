package com.openggf.game.sonic2.competition;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.internal.AttractDemoPlayback;
import com.openggf.game.nativemenu.NativeMenuHostContext;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.sonic2.competition.view.Sonic2CompetitionViews;
import com.openggf.game.sonic2.objects.Sonic2TeleportObjectFieldsAccess;

import static org.lwjgl.opengl.GL11.glClearColor;

/** Stock EHZ attract uses the native two-controller competition world without its menus. */
public final class Sonic2CompetitionAttractVisit implements NativeMenuScreen {
    private final Sonic2CompetitionSession session = new Sonic2CompetitionSession();
    private final Sonic2CompetitionViews views;
    private final Sonic2NativeCompetitionHost host;
    private final AttractDemoPlayback playback;
    private final int zone;
    private final int act;

    public Sonic2CompetitionAttractVisit(NativeMenuHostContext context, AttractDemoPlayback playback,
            int zone, int act, boolean overseas) {
        this.playback = playback;
        this.zone = zone;
        this.act = act;
        views = new Sonic2CompetitionViews(context.engine().graphics());
        host = Sonic2NativeCompetitionHost.production(context.rootModule(), context.engine(), context.publishMode(),
                views, false, overseas, new Sonic2TeleportObjectFieldsAccess());
        host.enableAttractVisit();
        host.bindNativeWaitInput(NativeWaitInput.from(context.input()), () -> false);
    }

    @Override public GameMode mode() { return GameMode.COMPETITION; }
    @Override public int presentationHeight() { return 448; }

    @Override
    public void initialize() {
        session.initializeAttract(GameServices.gameState(), zone, act);
        host.loadAct(session, zone, act);
    }

    @Override
    public void update(InputHandler input) {
        host.stepAttract(input, playback);
    }

    @Override public void draw() { host.draw(session); }
    @Override public void setClearColor() { glClearColor(0, 0, 0, 1); }
    @Override public Exit consumeExit() { return Exit.NONE; }

    @Override
    public void reset() {
        try { host.close(); } finally { views.close(); }
    }
}
