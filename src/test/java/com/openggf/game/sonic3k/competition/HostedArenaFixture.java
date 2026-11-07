package com.openggf.game.sonic3k.competition;

import com.openggf.control.*;
import com.openggf.game.GameServices;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.LevelArtInternalAccess;
import com.openggf.tests.TestEnvironment;
import static org.mockito.Mockito.mock;

/** ROM host fixture owning one world and its outer logical iteration. */
public final class HostedArenaFixture implements AutoCloseable {
    public final InputHandler input = new InputHandler();
    public S3kCompetitionNativeHost host;
    public HostedArenaFixture() throws Exception {
        var rom = GameServices.rom().getRom();
        TestEnvironment.configureRomFixture(rom);
        var engine = EngineServices.current();
        engine.graphics().initHeadless();
        engine.audio().setBackend(new com.openggf.audio.NullAudioBackend());
        var resources = S3kCompetitionResources.identify(rom);
        host = new S3kCompetitionNativeHost(engine, new Sonic3kGameModule(), input, ignored -> {},
                new S3kCompetitionRecords(rom.readBytes(resources.defaultRecords(), 80)),
                ignored -> { throw new AssertionError("Hosted Match Race must not save records"); },
                mock(S3kCompetitionNativeHost.Views.class), LevelArtInternalAccess::initializeObjectArt);
    }
    public void iteration(Runnable body) {
        host.world().context().plcFrameLifecycle().runLogicalIteration(() -> {}, frame -> {
            NativeCompetitionIterationAccess.runAdmitted(frame, body);
            return null;
        });
    }
    public void nativeStep(PlayerInputState p1) {
        var logical = LogicalInputSnapshot.ofPlayers(p1, PlayerInputState.neutral());
        input.setLogicalOverride(logical);
        iteration(() -> host.stepArena(logical, host.session()));
    }
    void menu(int direction, int action) {
        host.screen().update(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(direction,direction,action,action,false,false), PlayerInputState.neutral()));
        host.screen().afterFrame();
    }
    public void menuLaunch(boolean items) {
        host.screen().initialize();
        menu(2,0); // Match Race
        if (!items) menu(4,0);
        menu(0,InputActionMasks.ACTION_A);
        menu(0,InputActionMasks.ACTION_A);
        for (int i=0;i<12;i++) menu(0,0);
        menu(0,InputActionMasks.ACTION_A);
        if (host.session() == null) throw new AssertionError("Native menu did not open arena");
    }
    @Override public void close() { host.closeArena(); SessionManager.clear(); }
}
