package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.graphics.SpritePresentation;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestLevelSpritePresentationLifecycle {
    @Test void realGameplayPreparesWithoutDrawingPublishesPriorStateAndLoadClearsBothTables() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(4, 0).build();
        LevelManager level = GameServices.level();
        var tables = level.spritePresentationRenderer().spriteTables;
        for (int i = 0; i < 120 && tables.capture().prepared().tiles().isEmpty(); i++) {
            fixture.stepFrame(false, false, false, true, false);
        }
        var before = tables.capture();
        var registry = com.openggf.tests.TestEnvironment.activeGameplayMode().getRewindRegistry();
        var composite = registry.capture();
        assertEquals(before, composite.entries().get("level-sprite-presentation"),
                "the production level-load path must register the presentation adapter");
        assertFalse(before.prepared().tiles().isEmpty(), "headless production loop must prepare without a draw call");
        assertTrue(before.prepared().tiles().stream().anyMatch(tile -> tile.layer() == SpritePresentation.Layer.PLAYER));
        assertTrue(before.prepared().tiles().stream().anyMatch(tile -> tile.layer() == SpritePresentation.Layer.HUD));
        fixture.stepFrame(false, false, false, true, false);
        assertNotNull(before.preparedScroll());
        assertEquals(before.preparedScroll(), tables.publishedScroll(),
                "VBlank must publish the scroll registers/buffers paired with SAT");
        assertEquals(before.prepared(), tables.published(), "VBlank must upload the preceding loop's table");
        var next = tables.capture();
        registry.restore(composite);
        assertEquals(before, tables.capture(), "rewind must restore pending, displayed and counter presentation");
        fixture.stepFrame(false, false, false, true, false);
        assertEquals(next, tables.capture(), "forward replay after restore must reproduce all presentation buffers");
        level.resetZoneScopedRegistriesForLevelLoad();
        assertTrue(tables.capture().prepared().tiles().isEmpty());
        assertTrue(tables.published().tiles().isEmpty());
        assertTrue(tables.counters().tiles().isEmpty());
        assertNull(tables.publishedScroll());
    }
}
