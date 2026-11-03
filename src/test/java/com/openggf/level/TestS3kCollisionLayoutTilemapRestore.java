package com.openggf.level;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.rewind.snapshot.LevelSnapshot;
import com.openggf.game.sonic3k.S3kCollisionLayoutRewindAdapter;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.level.rewind.LevelRewindSnapshotAdapter;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCollisionLayoutTilemapRestore {
    private TraceReplaySessionBootstrap.ConfigSnapshot savedConfig;

    @BeforeEach
    void configure() {
        savedConfig = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect();
    }

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
        if (savedConfig != null) TraceReplaySessionBootstrap.restoreGameplayConfig(savedConfig);
    }

    @Test
    void unchangedWholeWorldRestoresReuseBothTilemapsAndCapturedMapBytes() {
        var fixture = openLrz1();
        var owner = GameServices.level();
        var tilemaps = owner.getTilemapManager();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var snapshot = registry.capture();
        var levelSnapshot = (LevelSnapshot) snapshot.get("level");
        var collision = (S3kCollisionLayoutRewindAdapter.State) snapshot.get("s3k-collision-layout");
        assertNotNull(collision.encoded(), "the real S3K layout owner must participate");
        byte[] foreground = tilemaps.getForegroundTilemapData();
        byte[] background = tilemaps.getBackgroundTilemapData();
        int rebuilds = tilemaps.bgFullRebuildCount;

        for (int cycle = 0; cycle < 3; cycle++) {
            registry.restore(snapshot);
            assertSame(levelSnapshot.mapData(), owner.getCurrentLevel().getMap().getData(),
                    "the per-game adapter must preserve the shared COW snapshot identity");
            ensureTilemaps(owner);
            assertSame(foreground, tilemaps.getForegroundTilemapData(),
                    "unchanged layout metadata must not force a full foreground rebuild");
            assertSame(background, tilemaps.getBackgroundTilemapData(),
                    "unchanged full-layout background remains a valid derived image");
            assertEquals(rebuilds, tilemaps.bgFullRebuildCount);
        }
    }

    @Test
    void wholeWorldRestoreRebuildsAfterProductionChunkMutation() {
        var fixture = openLrz1();
        var owner = GameServices.level();
        var level = owner.getCurrentLevel();
        var tilemaps = owner.getTilemapManager();
        byte[] expectedForeground = tilemaps.getForegroundTilemapData().clone();
        byte[] expectedBackground = tilemaps.getBackgroundTilemapData().clone();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var snapshot = registry.capture();
        int block = level.getMap().getValue(0, 0, 0) & 0xFF;
        int chunk = level.getBlock(block).getChunkDesc(0, 0).getChunkIndex();
        int[] changed = level.getChunk(chunk).saveState();
        changed[0] ^= 0x0800;
        GameServices.zoneLayoutMutationPipeline().applyImmediately(
                context -> context.surface().restoreChunkState(chunk, changed),
                new LayoutMutationContext(LevelMutationSurface.forLevel(level), owner::applyMutationEffects));
        ensureTilemaps(owner);
        byte[] discardedForeground = tilemaps.getForegroundTilemapData();
        byte[] discardedBackground = tilemaps.getBackgroundTilemapData();
        assertFalse(Arrays.equals(expectedForeground, discardedForeground),
                "the production mutation must change a rendered descriptor");

        registry.restore(snapshot);
        assertTrue(tilemaps.isForegroundTilemapDirty());
        assertTrue(tilemaps.isBackgroundTilemapDirty());
        ensureTilemaps(owner);
        assertNotSame(discardedForeground, tilemaps.getForegroundTilemapData());
        assertNotSame(discardedBackground, tilemaps.getBackgroundTilemapData());
        assertArrayEquals(expectedForeground, tilemaps.getForegroundTilemapData());
        assertArrayEquals(expectedBackground, tilemaps.getBackgroundTilemapData());
    }

    @Test
    void encodedRowRestoreInvalidatesEvenWhenSharedMapBytesWereAlreadyRestored() {
        var fixture = openLrz1();
        var owner = GameServices.level();
        var level = assertInstanceOf(Sonic3kLevel.class, owner.getCurrentLevel());
        var tilemaps = owner.getTilemapManager();
        var snapshot = fixture.gameplayMode().getRewindRegistry().capture();
        var collision = (S3kCollisionLayoutRewindAdapter.State) snapshot.get("s3k-collision-layout");
        byte[] expectedBackground = tilemaps.getBackgroundTilemapData().clone();
        int[] rows = distinctBackgroundRows(collision.encoded());
        GameServices.zoneLayoutMutationPipeline().applyImmediately(context -> {
            level.copyLayoutRowPointer(1, rows[0], rows[1]);
            return MutationEffects.redrawAllTilemaps();
        }, new LayoutMutationContext(LevelMutationSurface.forLevel(level), owner::applyMutationEffects));
        var adapter = new S3kCollisionLayoutRewindAdapter();
        assertFalse(Arrays.equals(collision.encoded(), adapter.capture().encoded()));

        LevelRewindSnapshotAdapter.create(owner).restore((LevelSnapshot) snapshot.get("level"));
        ensureTilemaps(owner);
        assertFalse(tilemaps.isBackgroundTilemapDirty());
        adapter.restore(collision);
        assertTrue(tilemaps.isForegroundTilemapDirty(), "encoded layout changes still invalidate geometry");
        assertTrue(tilemaps.isBackgroundTilemapDirty());
        assertSame(collision.scene().mapData(), level.getMap().getData());
        ensureTilemaps(owner);
        assertArrayEquals(expectedBackground, tilemaps.getBackgroundTilemapData());
        assertArrayEquals(collision.encoded(), adapter.capture().encoded());
    }

    private static HeadlessTestFixture openLrz1() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(9, 0)
                .withFreshLevelStartLifecycle().build();
        fixture.stepIdleFrames(2);
        ensureTilemaps(GameServices.level());
        return fixture;
    }

    private static void ensureTilemaps(LevelManager owner) {
        owner.ensureForegroundTilemapData();
        owner.ensureBackgroundTilemapData();
    }

    private static int[] distinctBackgroundRows(byte[] encoded) {
        for (int source = 0; source < 32; source++) {
            int sourcePointer = word(encoded, 10 + source * 4);
            if (sourcePointer == 0) continue;
            for (int destination = 0; destination < 32; destination++) {
                int destinationPointer = word(encoded, 10 + destination * 4);
                if (destinationPointer != 0 && sourcePointer != destinationPointer)
                    return new int[]{source, destination};
            }
        }
        return fail("LRZ1 requires two distinct background row pointers for this mutation witness");
    }

    private static int word(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }
}
