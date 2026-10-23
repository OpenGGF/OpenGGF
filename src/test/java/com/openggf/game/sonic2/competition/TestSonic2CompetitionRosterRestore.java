package com.openggf.game.sonic2.competition;

import com.openggf.audio.NullAudioBackend;
import com.openggf.camera.Camera;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.LevelState;
import com.openggf.game.ShieldType;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.competition.NativeCompetitionWorld;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.RenderContext;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Native-human reconstruction through the production world, independent of death/checkpoint services. */
class TestSonic2CompetitionRosterRestore {
    @AfterEach void resetWorld() {
        RenderContext.reset();
        TestEnvironment.resetAll();
    }

    @Test void stockFactoryCreatesTwoHumanParticipantsWithoutCpuControllers() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        var sonic = Sonic2CompetitionSession.createNativeParticipant(0, "native_sonic");
        var tails = Sonic2CompetitionSession.createNativeParticipant(1, "native_tails");
        assertInstanceOf(Sonic.class, sonic);
        assertInstanceOf(Tails.class, tails);
        assertEquals("native_sonic", sonic.getCode());
        assertEquals("native_tails", tails.getCode());
        for (var player : List.of(sonic, tails)) {
            assertFalse(player.isCpuControlled());
            assertNull(player.getCpuController());
            assertNotNull(player.getAnimationManager());
            assertNotNull(player.getDrowningController());
            assertEquals(6, player.getAllSensors().length);
        }
        assertThrows(IllegalArgumentException.class,
                () -> Sonic2CompetitionSession.createNativeParticipant(2, "other"));
    }

    @Test void detachedSpriteRestoreCannotInvokeTheCurrentWorldNativePreparationHook() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        var session = activeSession();
        int[] preparations = {0};
        NativeCompetitionRoster roster = new NativeCompetitionRoster() {
            @Override public AbstractPlayableSprite player(int slot) { return session.player(slot); }
            @Override public LevelState levelState(int slot) { return session.levelState(slot); }
            @Override public GameStateManager gameState(int slot) { return session.gameState(slot); }
            @Override public Camera camera(int slot) { return session.camera(slot); }
            @Override public void prepareRestore(GameplayModeContext context, Set<String> codes) {
                preparations[0]++;
            }
        };
        var policy = new WorldSessionPolicyState() {
            @Override public <T> T getService(Class<T> type) {
                return type.isInstance(roster) ? type.cast(roster) : null;
            }
            @Override public RewindSnapshottable<?> rewindAdapter() { return session; }
            @Override public void beforeAssembly(LevelLoadCause cause) { }
            @Override public void bindRoster(SpriteManager sprites) { }
            @Override public void failedAssembly(LevelLoadCause cause) { }
            @Override public void closeScreens() { }
            @Override public void retire() { }
        };
        var module = new DelegatingGameModule(new Sonic2GameModule(), "test:native-roster-restore") {
            @Override public <T> T getGameService(Class<T> type) {
                return type == WorldSessionPolicyProvider.class
                        ? type.cast((WorldSessionPolicyProvider) world -> policy) : super.getGameService(type);
            }
        };
        var context = SessionManager.openGameplaySession(module);
        GameplaySessionFactory.attachManagers(context, EngineServices.current());
        var detached = new SpriteManager();
        var detachedAdapter = detached.rewindSnapshottable();
        detachedAdapter.restore(detachedAdapter.capture());
        assertEquals(0, preparations[0]);
        assertSame(session.player(1), context.getSpriteManager().getSprite("tails"));
        var activeAdapter = context.getSpriteManager().rewindSnapshottable();
        activeAdapter.restore(activeAdapter.capture());
        assertEquals(1, preparations[0], "the exact active manager must invoke native preparation");
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void fullRegistryRecreatesPlayerTwoAndBothPlayersWithRomResourcesAndIdenticalForwardState() throws Exception {
        GameServices.graphics().initHeadless();
        GameServices.audio().setBackend(new NullAudioBackend());
        var session = activeSession();
        var module = new Sonic2GameModule();
        var world = NativeCompetitionWorld.open(module, module, session,
                new Sonic2CompetitionPolicy(session), EngineServices.current(), ignored -> { });
        world.load(0, 0);
        var profile = Sonic2CompetitionLevelProfile.load(TestEnvironment.currentRom(), 0, 0);
        for (int slot = 0; slot < 2; slot++) {
            var start = slot == 0 ? profile.sonicStart() : profile.tailsStart();
            var player = session.player(slot);
            NativePositionOps.writeXPosResetSubpixel(player, start.x());
            NativePositionOps.writeYPosResetSubpixel(player, start.y());
            player.setAir(true);
            player.updateSensors(player.getX(), player.getY());
            var camera = session.camera(slot);
            camera.setFocusedSprite(player);
            camera.setMinX((short) profile.bounds().minX());
            camera.setMaxX((short) profile.bounds().maxX());
            camera.setMinY((short) profile.bounds().minY());
            camera.setMaxY((short) profile.bounds().maxY());
            camera.setLevelStarted(true);
            camera.updatePosition(true);
        }
        idle(session, 4);
        // Player-bound object reconstruction must resolve the new humans before
        // ObjectManager restores, rather than retaining either retired camera target.
        session.player(0).giveShield();
        session.player(1).giveShield();
        session.player(0).giveInvincibility(600);
        session.player(1).giveInvincibility(480);
        session.playerState(0).addRings(7);
        session.playerState(1).addRings(19);
        session.player(1).getSpindashDustController().triggerSplash(
                session.player(1).getCentreX(), session.player(1).getCentreY(), true);
        var registry = world.context().getRewindRegistry();
        var captured = registry.capture();
        idle(session, 4);
        var forward = registry.capture();

        for (boolean removeBoth : new boolean[]{false, true}) {
            var oldP1 = session.player(0);
            var oldP2 = session.player(1);
            var sprites = world.context().getSpriteManager();
            sprites.removeSprite(oldP2.getCode());
            if (removeBoth) sprites.removeSprite(oldP1.getCode());
            registry.restore(captured);

            assertNotSame(oldP2, session.player(1));
            if (removeBoth) assertNotSame(oldP1, session.player(0));
            else assertSame(oldP1, session.player(0));
            for (int slot = 0; slot < 2; slot++) assertParticipantWiring(world, session, slot);
            assertSameSnapshot(captured, registry.capture(), session, "canonical state must follow reconstructed resources");
            idle(session, 4);
            for (int slot = 0; slot < 2; slot++) assertParticipantWiring(world, session, slot);
            assertSameSnapshot(forward, registry.capture(), session, "neutral forward replay after roster reconstruction");
        }
    }

    private static Sonic2CompetitionSession activeSession() {
        var session = new Sonic2CompetitionSession();
        session.updateMenu(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()), true);
        session.bindPlayers(Sonic2CompetitionSession.createNativeParticipant(0, "sonic"),
                Sonic2CompetitionSession.createNativeParticipant(1, "tails"));
        session.bindCameras(new Camera(), new Camera());
        return session;
    }

    private static void assertParticipantWiring(NativeCompetitionWorld world,
                                               Sonic2CompetitionSession session, int slot) throws Exception {
        var player = session.player(slot);
        assertSame(player, world.context().getSpriteManager().getSprite(player.getCode()));
        assertSame(player, session.camera(slot).getFocusedSprite());
        assertSame(session.camera(slot), player.currentCamera());
        assertSame(session.levelState(slot), player.currentLevelState());
        assertSame(session.gameState(slot), player.currentGameState());
        assertFalse(player.isCpuControlled());
        assertNull(player.getCpuController());
        assertNotNull(player.getPowerUpSpawner());
        assertNotNull(player.getSpriteRenderer());
        assertNotNull(player.getAnimationProfile());
        assertTrue(player.getAnimationManager().hasDynamicArtDecisionOwner());
        var provider = assertInstanceOf(PlayerSpriteArtProvider.class, world.context().getLevelManager().getGame());
        var art = provider.loadPlayerSpriteArt(player.characterKey().persisted());
        assertEquals(art.mappingFrames().size(), player.getAnimationFrameCount());
        assertEquals(art.animationSet().getAllScripts(), player.getAnimationSet().getAllScripts());
        assertEquals(132 + slot, player.getSpindashDustController().fixedSlotIndex());
        assertNotNull(player.getSpindashDustController().getRenderer());
        assertNotNull(player.getDrowningController());
        assertEquals(6, player.getAllSensors().length);
        assertTrue(player.getShieldObject().isShieldFor(player, ShieldType.BASIC));
        assertFalse(player.getShieldObject().isDestroyed());
        assertNotNull(player.getInvincibilityObject());
        assertSame(player, player.getInvincibilityObject().boundPlayer());
        assertFalse(player.getInvincibilityObject().isDestroyed());
        if (slot == 1) {
            assertNotNull(player.getTailsTailsController());
            assertNotNull(player.getTailsTailsController().getRenderer());
        }
    }

    private static void idle(Sonic2CompetitionSession session, int frames) {
        new HeadlessTestRunner(session.player(0)).stepIdleFrames(frames);
    }

    private static void assertSameSnapshot(CompositeSnapshot expected, CompositeSnapshot actual,
                                          Sonic2CompetitionSession session, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label + " registry coverage");
        for (String key : expected.entries().keySet()) {
            Object value = expected.get(key);
            if (value instanceof ObjectManagerSnapshot objects) value = expectedOwners(objects, session);
            var differences = RewindSnapshotDiff.diffKey(key, value, actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences);
        }
    }

    /** Compare structural owners only after independently checking fresh actors and effect bindings. */
    private static ObjectManagerSnapshot expectedOwners(ObjectManagerSnapshot source, Sonic2CompetitionSession session) {
        var entries = source.dynamicObjects().stream().map(entry -> {
            if (!(entry.playerOwner() instanceof AbstractPlayableSprite old)) return entry;
            for (int slot = 0; slot < 2; slot++) {
                var current = session.player(slot);
                if (old.getCode().equals(current.getCode())) {
                    assertEquals(old.characterKey(), current.characterKey());
                    return new ObjectManagerSnapshot.DynamicObjectEntry(entry.className(), entry.spawn(),
                            entry.slotIndex(), entry.state(), current, entry.objectId(), entry.ownerModId(),
                            entry.rewindableAuxiliary());
                }
            }
            fail("Captured native effect owner has no current human: " + old.getCode());
            return entry;
        }).toList();
        // Every captured ID, ordinal, allocator bit, state blob and other registry
        // key remains compared. This changes expected reference metadata only.
        return new ObjectManagerSnapshot(source.usedSlotsBits(), source.slots(), source.frameCounter(),
                source.vblaCounter(), source.currentExecSlot(), source.peakSlotCount(), source.bucketsDirty(),
                source.childSpawns(), entries, source.placement(), source.solidContactRiding(),
                source.solidContactState(), source.planeSwitchers(), source.touchResponseOverlap(),
                source.dynamicObjectIdCounter(), source.collisionResponseState());
    }
}
