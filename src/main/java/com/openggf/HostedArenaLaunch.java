package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameMode;
import com.openggf.game.arena.*;
import com.openggf.game.nativemenu.NativeMenuHostContext;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.mods.run.MonitorRule;
import com.openggf.mods.run.RunSpec;

/** Native-screen delegate for a scene-owned arena. The outer loop still owns PLC admission. */
final class HostedArenaLaunch implements NativeMenuScreen {
    private final GameLoop loop;
    private final RunSpec spec;
    private final HostedArenaProvider provider;
    private final NativeMenuHostContext context;
    private HostedArenaSession session;

    private HostedArenaLaunch(GameLoop loop,RunSpec spec,HostedArenaProvider provider,NativeMenuHostContext context) {
        this.loop=loop; this.spec=spec; this.provider=provider; this.context=context;
    }
    static boolean open(GameLoop loop,RunSpec spec,NativeMenuHostContext context) {
        var provider = context.rootModule().getGameService(HostedArenaProvider.class);
        if (provider == null || !provider.characters().contains(spec.character()))
            throw new IllegalArgumentException("Game has no hosted arena for this character");
        if (spec.act()!=0 || spec.area()!=null || spec.policy().monitors()!=MonitorRule.OFF
                && spec.policy().monitors()!=MonitorRule.COMPETITION_ITEMS)
            throw new IllegalArgumentException("Arena needs act zero, no area and COMPETITION_ITEMS or OFF");
        loop.openHostedArenaScreen(new HostedArenaLaunch(loop,spec,provider,context));
        loop.setGameMode(GameMode.COMPETITION);
        return true;
    }
    @Override public GameMode mode() { return GameMode.COMPETITION; }
    @Override public void initialize() {
        session = provider.open(new HostedArenaRequest(spec.zone(),spec.character(),
                spec.policy().monitors()==MonitorRule.COMPETITION_ITEMS),context);
        loop.hostedRuns.bindArena(session);
        loop.hostedRuns.onArenaReady(session.geometry());
    }
    @Override public void update(InputHandler input) {
        var reason = loop.hostedRuns.consumeLeave();
        if (reason!=null) { loop.endHostedRun(reason); return; }
        if (!loop.hostedRuns.admitArenaStep(input)) return;
        PlayerInputState admitted = loop.hostedRuns.beforeArenaStep(input);
        var step = session.step(admitted);
        loop.hostedRuns.afterArenaStep(step,session.trackFinished());
    }
    @Override public void draw() {
        // Presentation remains a capability of the native session, not a gameplay update.
        session.draw();
    }
    @Override public void setClearColor() { }
    @Override public Exit consumeExit() { return Exit.NONE; }
    @Override public void reset() { if (session!=null) { session.close(); session=null; } }
}
