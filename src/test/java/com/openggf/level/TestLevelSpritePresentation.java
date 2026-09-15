package com.openggf.level;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.sonic3k.Sonic3kLevelInitProfile;
import com.openggf.graphics.SpritePresentation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestLevelSpritePresentation {
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

    private static SpritePresentation.Frame frame(int pattern) {
        return new SpritePresentation.Frame(List.of(new SpritePresentation.Tile(
                SpritePresentation.Layer.PLAYER, pattern, 0, false, false, false,
                20, 30, 8, 8, true, 15, false, 1)));
    }
}
