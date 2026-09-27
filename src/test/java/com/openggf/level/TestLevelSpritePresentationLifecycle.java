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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {4, 7, 8, 9, 11})
    void seamlessActReloadRetainsSatUntilTheNextPublication(int zone) throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(zone, 0).build();
        LevelManager level = GameServices.level();
        var tables = level.spritePresentationRenderer().spriteTables;
        for (int i = 0; i < 120 && tables.published().tiles().isEmpty(); i++)
            fixture.stepFrame(false, false, false, false, false);
        var before = tables.capture();
        assertFalse(before.prepared().tiles().isEmpty());
        assertFalse(before.published().tiles().isEmpty());
        // Native Load_Level changes the layout inside LevelLoop. It neither
        // clears the SAT in VRAM nor the CPU table waiting for the next VInt.
        level.executeActTransition(SeamlessLevelTransitionRequest.builder(
                SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(zone, 1).build());
        assertSame(before.prepared(), tables.capture().prepared(), "retain pending Render_Sprites output");
        assertSame(before.published(), tables.published(), "retain the currently displayed SAT");
        assertSame(before.counters(), tables.counters(), "retain the displayed HUD counters");
        // Engine full-layout coordinates change on reload; the next scroll
        // producer must use the new layout, rather than stale source offsets.
        assertNull(tables.publishedScroll());
        tables.publish();
        assertSame(before.prepared(), tables.published(), "ordinary publication resumes from retained CPU output");
    }

}
