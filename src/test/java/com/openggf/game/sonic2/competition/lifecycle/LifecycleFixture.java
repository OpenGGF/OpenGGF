package com.openggf.game.sonic2.competition.lifecycle;

import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.*;
import com.openggf.game.competition.NativePlayerDeathPass;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2GameOverFlowProvider;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.game.sonic2.objects.Sonic2GameOverCardObjectInstance;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic2CompetitionObjectFixture;
import com.openggf.tests.TestEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/** Real human actors/session, fixed SST manager and cards; no active-global-player substitution. */
final class LifecycleFixture {
    final Sonic2CompetitionObjectFixture actors = new Sonic2CompetitionObjectFixture();
    final Sonic2CompetitionSession session = actors.session;
    final ObjectManager objects;
    final ObjectServices services;
    final Sonic2CompetitionLifecycle lifecycle;
    final WorldSession world;
    final Sonic2PlcService plc = mock(Sonic2PlcService.class);
    final RuntimeArtCoordinator runtimeArt = mock(RuntimeArtCoordinator.class,
            withSettings().extraInterfaces(com.openggf.game.internal.PlayerDplcDmaSink.class));
    final List<String> cardRequests = new ArrayList<>();
    final List<String> audio = new ArrayList<>();
    final InputHandler input = new InputHandler();
    boolean plcBusy;
    Sonic2CompetitionCheckpoints.PrimaryEnvironment environment =
            new Sonic2CompetitionCheckpoints.PrimaryEnvironment(6, 0x456, 0x123, 0x234,
                    0x345, 0x456, 0x567, 0x678, 0x789, 0x89A, 0x9AB, 4, 1);

    LifecycleFixture() throws Exception {
        when(plc.isBusy()).thenAnswer(ignored -> plcBusy);
        var policy = new WorldSessionPolicyState() {
            @Override public <T> T getService(Class<T> type) {
                if (type.isInstance(session)) return type.cast(session);
                if (type == NativePlayerDeathPass.class || type == Sonic2CompetitionLifecycle.class)
                    return type.cast(lifecycle);
                return null;
            }
            @Override public RewindSnapshottable<?> rewindAdapter() { return session; }
            @Override public void beforeAssembly(LevelLoadCause cause) { }
            @Override public void bindRoster(com.openggf.sprites.managers.SpriteManager sprites) { }
            @Override public void failedAssembly(LevelLoadCause cause) { }
            @Override public void closeScreens() { }
            @Override public void retire() { }
        };
        var module = new Sonic2GameModule() {
            @Override public <T> T getGameService(Class<T> type) {
                return type == WorldSessionPolicyProvider.class
                        ? type.cast((WorldSessionPolicyProvider) ignored -> policy) : super.getGameService(type);
            }
        };
        world = new WorldSession(module);
        when(actors.level.getWorldSession()).thenReturn(world);
        when(actors.level.getGameModule()).thenReturn(module);
        services = new StubObjectServices() {
            @Override public WorldSession worldSession() { return world; }
            @Override public com.openggf.level.LevelManager levelManager() { return actors.level; }
            @Override public ObjectManager objectManager() { return objects; }
            @Override public com.openggf.sprites.managers.SpriteManager spriteManager() { return actors.sprites; }
            @Override public com.openggf.camera.Camera camera() { return actors.cameras[0]; }
            @Override public LevelState levelGamestate() { return actors.ordinary; }
            @Override public RespawnState checkpointState() { return ordinaryCheckpoint; }
            @Override public GameStateManager gameState() { return actors.ordinaryGame; }
            @Override public GameModule gameModule() { return module; }
            @Override public RuntimeArtCoordinator runtimeArtCoordinator() { return runtimeArt; }
            @Override public void playSfx(int id) { audio.add("sfx:" + id); }
            @Override public void playMusic(int id) { audio.add("music:" + id); }
            @Override public <T> T gameService(Class<T> type) {
                return type == Sonic2PlcService.class ? type.cast(plc) : null;
            }
        };
        var registry = mock(ObjectRegistry.class);
        when(registry.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_2);
        objects = new ObjectManager(List.of(), registry, 0, null, null, null, actors.cameras[0], services);
        objects.reset(0);
        when(actors.level.getObjectManager()).thenReturn(objects);
        lifecycle = new Sonic2CompetitionLifecycle(TestEnvironment.currentRom(), session, (slot, timeout) -> {
            cardRequests.add(slot + ":" + timeout);
            new Sonic2GameOverFlowProvider().beginNativeGameOverCard(services, slot, timeout);
        }, () -> environment);
        for (int slot = 0; slot < 2; slot++) {
            session.player(slot).setCentreX((short) (0x100 + slot * 0x20));
            session.player(slot).setCentreY((short) (0x180 + slot * 0x10));
            actors.cameras[slot].setMaxY((short) (0x600 + slot * 0x100));
        }
        lifecycle.initializeAct();
    }

    final CheckpointState ordinaryCheckpoint = new CheckpointState();
    AbstractPlayableSprite actor(int slot) { return session.player(slot); }
    Sonic2GameOverCardObjectInstance card(int slot) {
        return (Sonic2GameOverCardObjectInstance) NativeSstSlotAccess.currentOwner(objects, slot);
    }
    void putBelowOwnDeathRow(int slot) {
        var actor = actor(slot);
        actor.setDead(true);
        actor.setCentreY((short) (session.camera(slot).getMaxY() + 0x101));
    }
    void die(int slot) { putBelowOwnDeathRow(slot); lifecycle.runDeathPass(actor(slot)); }
    void lives(int slot, int count) {
        var bank = session.gameState(slot);
        bank.restoreSaveProgress(count, 2, List.of(0, 2), List.of(), false);
    }
    void timeout(int slot) {
        session.levelState(slot).setTimerFrames(35999);
        session.levelState(slot).update();
    }
    void press(int slot, int actions, boolean start) {
        var state = PlayerInputState.of(0, 0, actions, actions, start, start);
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                slot == 0 ? state : PlayerInputState.neutral(),
                slot == 1 ? state : PlayerInputState.neutral()));
        actors.sprites.publishHeldInputForLevelEvents(input);
    }
    void stepCards(int count) {
        for (int i = 0; i < count; i++) {
            card(2).update(i, actor(0));
            card(3).update(i, actor(0));
        }
    }
}
