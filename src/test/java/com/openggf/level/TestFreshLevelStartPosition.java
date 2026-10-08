package com.openggf.level;

import com.openggf.game.CheckpointState;
import com.openggf.game.GameModule;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelStartPosition;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestFreshLevelStartPosition {
    private LevelManager level;
    private Sonic player;
    private final AtomicInteger queries = new AtomicInteger();

    @BeforeEach void setup() {
        TestEnvironment.resetAll();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        GameModule module = new DelegatingGameModule(new Sonic2GameModule(), "test-entry") {
            @Override public Optional<LevelStartPosition> freshLevelStartPosition(int zone, int act) {
                assertEquals(0, zone); assertEquals(0, act); queries.incrementAndGet();
                return Optional.of(new LevelStartPosition(0x1D30, 0x1A8));
            }
        };
        var runtime = SessionManager.openGameplaySession(module);
        GameplaySessionFactory.attachManagers(runtime, EngineServices.current());
        level = runtime.getLevelManager();
        player = new Sonic("sonic", (short) 20, (short) 30);
        runtime.getSpriteManager().addSprite(player);
    }

    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    private LevelLoadContext context() {
        var ctx = new LevelLoadContext();
        ctx.setLevelData(new LevelDescriptor() {
            @Override public int levelIndex() { return 0; }
            @Override public int startX() { return 100; }
            @Override public int startY() { return 200; }
        });
        return ctx;
    }

    @Test void freshEntryUsesNativeCentreWordsAndResetsSubpixels() {
        player.setSubpixelRaw(123, 456);
        var ctx = context();
        level.spawnPlayerAtStartPosition(ctx);
        assertEquals(0x1D30, player.getCentreX());
        assertEquals(0x1A8, player.getCentreY());
        assertEquals(0x1A8, ctx.getSpawnY());
        assertEquals(0, player.getXSubpixelRaw());
        assertEquals(0, player.getYSubpixelRaw());
        assertEquals(1, queries.get());
    }

    @Test void checkpointAlwaysWinsWithoutQueryingTheFreshEntryCallback() {
        var ctx = context();
        var checkpoint = new CheckpointState();
        checkpoint.saveCheckpoint(2, 0x1D60, 0x1A8, false);
        ctx.snapshotCheckpoint(checkpoint);
        level.spawnPlayerAtStartPosition(ctx);
        assertEquals(0x1D60, player.getCentreX());
        assertEquals(0x1A8, player.getCentreY());
        assertEquals(0, queries.get());
    }

    @Test void nativeStageReturnWinsOverBothCheckpointAndFreshEntry() {
        var ctx = context();
        var checkpoint = new CheckpointState();
        checkpoint.saveCheckpoint(2, 0x1D60, 0x1A8, false);
        ctx.snapshotCheckpoint(checkpoint);
        level.saveBigRingReturn(new BigRingReturnState(0x2300, 0x280, 0, 0, 0,
                (byte) 0xC, (byte) 0xD, 0x600, 0));
        level.setLastStarPostHit();
        level.spawnPlayerAtStartPosition(ctx);
        assertEquals(0x2300, player.getCentreX());
        assertEquals(0x280, player.getCentreY());
        assertEquals(0, queries.get());
    }

    @Test void startPositionRejectsOutOfRangeWords() {
        assertThrows(IllegalArgumentException.class, () -> new LevelStartPosition(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new LevelStartPosition(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new LevelStartPosition(0x10000, 0));
        assertThrows(IllegalArgumentException.class, () -> new LevelStartPosition(0, 0x10000));
        assertEquals(0xFFFF, new LevelStartPosition(0xFFFF, 0xFFFF).centreX());
    }

    @Test void stageReturnWithoutAnActivatedCheckpointDoesNotBecomeFreshEntry() {
        level.saveBigRingReturn(new BigRingReturnState(0x2300, 0x280, 0, 0, 0,
                (byte) 0xC, (byte) 0xD, 0x600, 0));
        level.clearLastStarPostHit();
        level.spawnPlayerAtStartPosition(context());
        assertEquals(100, player.getCentreX());
        assertEquals(0, queries.get());
        level.clearBigRingReturn();
        level.setBonusStageReturnCheckpointIndex(0);
        level.spawnPlayerAtStartPosition(context());
        assertEquals(100, player.getCentreX());
        assertEquals(0, queries.get());
    }

    @Test void sanctuaryReturnWithoutASavedOriginDoesNotBecomeFreshEntry() {
        level.markSanctuaryReentry(2, false);
        level.spawnPlayerAtStartPosition(context());
        assertEquals(100, player.getCentreX());
        assertEquals(200, player.getCentreY());
        assertEquals(0, queries.get());
    }

    @Test void hiddenPreviewCaptureDoesNotBecomeFreshGameplayEntry() {
        var ctx = context();
        ctx.setLoadMode(com.openggf.game.LevelLoadMode.PREVIEW_CAPTURE);
        level.spawnPlayerAtStartPosition(ctx);
        assertEquals(100, player.getCentreX());
        assertEquals(200, player.getCentreY());
        assertEquals(0, queries.get());
    }
}
