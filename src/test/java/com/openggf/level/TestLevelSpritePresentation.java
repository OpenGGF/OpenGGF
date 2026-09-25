package com.openggf.level;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.sonic3k.Sonic3kLevelInitProfile;
import com.openggf.graphics.SpritePresentation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestLevelSpritePresentation {
    @Test void registrationBeforeRendererAttachmentRemovesStalePresentationAdapters() {
        var registry = new com.openggf.game.rewind.RewindRegistry();
        registry.register(new LevelBoundsMaskTransition());
        registry.register(new LevelSpritePresentation.Tables());
        LevelSpritePresentation.register(org.mockito.Mockito.mock(LevelManager.class), registry);
        assertFalse(registry.capture().containsKey("level-bounds-mask"));
        assertFalse(registry.capture().containsKey("level-sprite-presentation"));
    }

    @Test void preparationDoesNotPublishAndRewindRestoresBothTables() {
        var tables = new LevelSpritePresentation.Tables();
        var first = frame(1);
        var second = frame(2);
        tables.prepare(first);
        assertTrue(tables.published().tiles().isEmpty());
        tables.publish();
        tables.prepare(second);
        tables.publishCounters(frame(9));
        var pending = tables.capture();
        assertEquals(first, tables.published());
        tables.publish();
        assertEquals(second, tables.published());
        tables.publishCounters(frame(10));
        tables.restore(pending);
        assertEquals(frame(9), tables.counters());
        assertEquals(first, tables.published());
        tables.publish();
        assertEquals(second, tables.published());
        tables.reset();
        assertTrue(tables.capture().prepared().tiles().isEmpty());
        assertTrue(tables.published().tiles().isEmpty());
        assertTrue(tables.counters().tiles().isEmpty());
    }

    @Test void skippedDrawingDoesNotPreventPreparationAndLagHoldsThePublishedTable() {
        var profile = new Sonic3kLevelInitProfile(null);
        var tables = new LevelSpritePresentation.Tables();
        assertFalse(profile.publishesSpriteTable(PlcLifecyclePhase.SPECIAL_STAGE));
        assertFalse(profile.publishesSpriteTable(PlcLifecyclePhase.SPECIAL_STAGE_PAUSE));
        // A Chaos Emerald results screen parks a stale level table; only the sanctuary
        // backdrop publishes, through publishPreparedScene.
        assertFalse(profile.publishesSpriteTable(PlcLifecyclePhase.SPECIAL_STAGE_RESULTS));
        assertFalse(profile.updatesHudCounters(PlcLifecyclePhase.LEVEL_TITLE_CARD));
        assertFalse(profile.updatesHudCounters(PlcLifecyclePhase.LAG));
        assertTrue(profile.updatesHudCounters(PlcLifecyclePhase.NORMAL_PAUSE));
        assertFalse(profile.advancesHudTimer(PlcLifecyclePhase.NORMAL_PAUSE));
        tables.prepare(frame(1));
        if (profile.publishesSpriteTable(PlcLifecyclePhase.ORDINARY_LEVEL)) tables.publish();
        tables.prepare(frame(2)); // no drawing between these gameplay loops
        var beforeLag = tables.capture();
        if (profile.publishesSpriteTable(PlcLifecyclePhase.LAG)) tables.publish();
        assertEquals(beforeLag, tables.capture());
        if (profile.publishesSpriteTable(PlcLifecyclePhase.NORMAL_PAUSE)) tables.publish();
        assertEquals(frame(2), tables.published());
        tables.resetForMissingSnapshot();
        assertTrue(tables.published().tiles().isEmpty());
        assertTrue(tables.counters().tiles().isEmpty());
    }

    @Test void scrollDmaAndSatRemainPairedAcrossPreparationLagRewindAndReset() {
        var tables = new LevelSpritePresentation.Tables();
        var first = scroll(-8, (short) 2047);
        var second = scroll(8, (short) 0);
        tables.prepare(frame(1), first);
        assertNull(tables.publishedScroll());
        tables.publish();
        tables.prepare(frame(2), second);
        var saved = tables.capture();
        assertSame(first, tables.publishedScroll(), "next CPU scroll must not leak into current VDP output");
        var profile = new Sonic3kLevelInitProfile(null);
        if (profile.publishesSpriteTable(PlcLifecyclePhase.LAG)) tables.publish();
        assertEquals(saved, tables.capture(), "lag retains the complete presentation generation");
        tables.publish();
        assertSame(second, tables.publishedScroll());
        tables.restore(saved);
        assertSame(first, tables.publishedScroll());
        tables.publish();
        assertSame(second, tables.publishedScroll(), "rewind also restores the pending DMA buffers");
        tables.resetForMissingSnapshot();
        assertNull(tables.publishedScroll());
        assertNull(tables.capture().preparedScroll());
    }

    @Test void scrollSnapshotOwnsBuffersAndRetainsPlaneReversalAndSignedOffsets() {
        int[] horizontal = {0xFFF80004, 0x0010FFFE};
        short[] columns = {-3, 12};
        var registers = new LevelScrollPresentation.Registers(-8, 2047, -11,
                (short) 2045, (short) -3, -16, 1024, true, true, true,
                true, (short) 99, true, (short) -17,
                new com.openggf.graphics.ArenaMaskState(8, 328, 17));
        var snapshot = new LevelScrollPresentation(registers, horizontal, columns, columns, columns, columns);
        var equal = new LevelScrollPresentation(registers, horizontal, columns, columns, columns, columns);
        horizontal[0] = 99;
        columns[0] = 99;
        snapshot.horizontal()[0] = 100;
        snapshot.backgroundLines()[0] = 100;
        assertEquals(equal, snapshot, "neither producer reuse nor a consumer may mutate retained output");
        assertEquals(equal.hashCode(), snapshot.hashCode());
        assertEquals(-11, snapshot.registers().cameraXWithShake());
        var mode = snapshot.renderMode();
        assertTrue(mode.reversePlaneAssignment());
        assertTrue(mode.enableForegroundHeatHaze());
        assertTrue(mode.enablePerLineForegroundScroll());
        assertEquals(99, mode.foregroundVScrollOverride());
        assertEquals(-17, mode.backgroundVScrollOverride());
        assertArrayEquals(new short[] {-3, 12}, mode.foregroundPerColumnVScrollOverride());
        assertArrayEquals(new short[] {-3, 12}, snapshot.foregroundColumns());
        assertArrayEquals(new short[] {-3, 12}, snapshot.backgroundColumns());
    }

    private static LevelScrollPresentation scroll(int x, short y) {
        return new LevelScrollPresentation(new LevelScrollPresentation.Registers(x, y, x - 3,
                y, (short) -5, x / 2, 512, false, false, false,
                false, (short) 0, false, (short) 0,
                new com.openggf.graphics.ArenaMaskState(0, 320, 0)), new int[] {-x << 16}, null, null, null, null);
    }

    private static SpritePresentation.Frame frame(int pattern) {
        return new SpritePresentation.Frame(List.of(new SpritePresentation.Tile(
                SpritePresentation.Layer.PLAYER, pattern, 0, false, false, false,
                20, 30, 8, 8, true, 15, false, 1)));
    }
}
