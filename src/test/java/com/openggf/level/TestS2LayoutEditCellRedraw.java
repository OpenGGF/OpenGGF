package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2LevelEventManager;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.RivetObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.openggf.level.LayoutRedrawEquivalence.Foreground.CHANGED;
import static com.openggf.level.LayoutRedrawEquivalence.Foreground.UNCHANGED;
import static com.openggf.level.LayoutRedrawEquivalence.assertWriterRedrawsCellsInPlace;
import static com.openggf.level.LayoutRedrawEquivalence.invoke;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Sonic 2 layout writers the all-level sweep caught rebuilding the whole foreground
 * image (12-55 ms a frame) now rewrite only the cells they changed, and the patched
 * image equals a full rebuild of the edited layout.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestS2LayoutEditCellRedraw {
    private static final int CNZ = 3;
    private static final int HTZ = 4;
    private static final int WFZ = 9;

    @AfterEach
    void cleanup() {
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    private static LevelManager load(int zone, int act) {
        HeadlessTestFixture.builder().withZoneAndAct(zone, act).build();
        return GameServices.level();
    }

    private static Sonic2LevelEventManager events() {
        return (Sonic2LevelEventManager) GameServices.module().getLevelEventProvider();
    }

    /** HTZ2 {@code setEarthquakeActive}: only the background changes, both on entry and exit. */
    @Test
    void htz2EarthquakeToggleRewritesNoForegroundCell() {
        LevelManager manager = load(HTZ, 1);
        var htz = events().getHtzEvents();
        assertWriterRedrawsCellsInPlace(manager, UNCHANGED, () -> htz.setEarthquakeActive(true));
        assertWriterRedrawsCellsInPlace(manager, UNCHANGED, () -> htz.setEarthquakeActive(false));
    }

    /** CNZ2 boss arena walls: placed on arena entry, the right wall removed on defeat. */
    @Test
    void cnz2ArenaWallsRedrawOnlyTheirCells() {
        LevelManager manager = load(CNZ, 1);
        var cnz = events().getCnzEventsForTest();
        long placed = assertWriterRedrawsCellsInPlace(manager, CHANGED, () -> invoke(cnz, "placeCNZArenaWalls"));
        assertTrue(placed <= 2, "two wall cells, rewrote " + placed);
        long removed = assertWriterRedrawsCellsInPlace(manager, CHANGED, () -> invoke(cnz, "removeCNZArenaWalls"));
        assertEquals(1, removed);
    }

    /** WFZ {@code RivetObjectInstance.bustRivet} opens the ship passage (two rows of six cells). */
    @Test
    void wfzRivetPassageRedrawsOnlyItsCells() {
        LevelManager manager = load(WFZ, 0);
        RivetObjectInstance rivet = new RivetObjectInstance(
                new ObjectSpawn(0x2880, 0x0400, Sonic2ObjectIds.RIVET, 0, 0, false, 0), "Rivet");
        manager.getObjectManager().addDynamicObject(rivet);
        long cells = assertWriterRedrawsCellsInPlace(manager, CHANGED, () -> invoke(rivet, "modifyLevelLayout"));
        assertTrue(cells <= 12, "rewrote " + cells);
    }
}
