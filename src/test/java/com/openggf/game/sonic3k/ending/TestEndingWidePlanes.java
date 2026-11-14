package com.openggf.game.sonic3k.ending;

import com.openggf.camera.Camera;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** What the ending's tile planes show beside the native window, and which scenes fill the picture. */
class TestEndingWidePlanes {
    /** A layout cell that names its own position, and a name table holding different words. */
    private static int layout(int x, int y) { return 0x4000 | ((x >> 3) & 0x7F) << 5 | ((y >> 3) & 0x1F); }
    private static int ring(int x, int y) { return 0x8000 | ((x >> 3) & 63) << 5 | ((y >> 3) & 31); }

    private static Camera camera(int width, int copyX) {
        var camera = spy(new Camera());
        doReturn((short) width).when(camera).getWidth();
        camera.setXCopy((short) copyX);
        return camera;
    }

    /** The rescue level with Plane A filled at its opening position, as Ending_ScreenInit leaves it. */
    private static EndingZoneRuntimeState rescue() {
        var zone = new EndingZoneRuntimeState(PlayerCharacter.SONIC_ALONE);
        zone.ending().scene().foreground().fill(0x200, 0x200, TestEndingWidePlanes::ring);
        return zone;
    }

    private static S3kEndingSceneState.Plane filled() {
        return rescue().ending().scene().foreground();
    }

    private static int foreground(EndingZoneRuntimeState zone, Camera camera, int x, int y) {
        return EndingWidePlanes.foreground(zone, camera, TestEndingWidePlanes::layout, x, y);
    }

    @Test void atTheNativeWidthBothPlanesAreTheNameTablesAndNothingElse() {
        var zone = rescue();
        var plane = zone.ending().scene().foreground();
        var camera = camera(320, 0x260);
        for (int routine : new int[] {0, 8, 20, 44}) {
            zone.screen().foregroundRoutine = routine;
            for (int x = 0; x < 0x800; x += 8) for (int y = 0; y < 0x100; y += 8) {
                assertEquals(plane.descriptor(x, y), foreground(zone, camera, x, y));
                assertEquals(plane.descriptor(x, y), EndingWidePlanes.background(plane, camera, x, y));
            }
            assertEquals(plane.revision(), EndingWidePlanes.foregroundRevision(zone, camera));
        }
        assertEquals(plane.revision(), EndingWidePlanes.backgroundRevision(plane, camera));
        camera.setXCopy((short) 0x300);
        assertEquals(plane.revision(), EndingWidePlanes.foregroundRevision(zone, camera),
                "the native tilemap is rebuilt only when the name table changes");
    }

    /** The cell the tile pass draws at this picture pixel: tilemap column X is at pixel X - copyX. */
    private static int shown(EndingZoneRuntimeState zone, Camera camera, int pixel) {
        return foreground(zone, camera, (camera.getXCopy() & ~7) + pixel, 0x90);
    }

    @ParameterizedTest @ValueSource(ints = {352, 400, 528, 800})
    void aWiderPictureStandsPlaneABesideTheSpritesAroundTheNameTablesOwnWindow(int width) {
        var zone = rescue();
        var plane = zone.ending().scene().foreground();
        int copyX = 0x200, margin = (width - 320) / 2;
        var camera = camera(width, copyX);
        for (int pixel = 0; pixel < width; pixel += 8) {
            int nativeX = copyX + pixel - margin;
            // The opening shot draws nothing beside its 512-pixel plane: the scratch layout
            // there belongs to other shots and is never shown.
            int expected = nativeX >= copyX + 0x160 - 512 && nativeX < copyX + 0x160 ? plane.descriptor(nativeX, 0x90) : 0;
            assertEquals(expected, shown(zone, camera, pixel), "picture pixel " + pixel);
        }
        long revision = EndingWidePlanes.foregroundRevision(zone, camera);
        assertNotEquals(0, revision);
        camera.setXCopy((short) (copyX + 7));
        assertEquals(revision, EndingWidePlanes.foregroundRevision(zone, camera), "same 8-pixel cell");
        camera.setXCopy((short) (copyX + 8));
        assertNotEquals(revision, EndingWidePlanes.foregroundRevision(zone, camera),
                "the cells beside the window follow the scroll position");
        camera.setXCopy((short) copyX);
        plane.fill(0x200, 0x200, TestEndingWidePlanes::ring);
        assertNotEquals(revision, EndingWidePlanes.foregroundRevision(zone, camera), "and the name table");
    }

    @ParameterizedTest @ValueSource(ints = {352, 400, 528, 800})
    void theIslandPanShowsTheColumnsItDrawsAheadOfItsLeadingEdge(int width) {
        var zone = rescue();
        var plane = zone.ending().scene().foreground();
        int margin = (width - 320) / 2;
        long opening = EndingWidePlanes.foregroundRevision(zone, camera(width, 0x140));
        zone.screen().foregroundRoutine = 4; // loc_5AB36 has parked the plane at $140
        assertNotEquals(opening, EndingWidePlanes.foregroundRevision(zone, camera(width, 0x140)));
        for (int copyX : new int[] {0x140, 0x1D0, 0x260}) {
            var camera = camera(width, copyX);
            for (int pixel = 0; pixel < width; pixel += 8) {
                int nativeX = copyX + pixel - margin, drawn = (copyX & ~15) + 0x160;
                int expected = nativeX + 8 > copyX && nativeX < copyX + 320 ? plane.descriptor(nativeX, 0x90)
                        : nativeX >= 0x290 && nativeX < 0x3C0 ? layout(nativeX, 0x90)
                        : nativeX >= drawn - 512 && nativeX < drawn ? plane.descriptor(nativeX, 0x90) : 0;
                assertEquals(expected, shown(zone, camera, pixel), "pan at " + copyX + ", picture pixel " + pixel);
            }
        }
        // As the pan starts, the island's first column is 16 pixels outside the native
        // window: a wider picture already shows the layout there.
        var start = camera(width, 0x140);
        assertEquals(layout(0x290, 0x90), foreground(zone, start, 0x290 + margin, 0x90));
        // (At 352 the picture's last column is still short of that first column.)
        if (margin >= 24)
            assertEquals(layout(0x140 + 320 + margin - 8, 0x90), shown(zone, start, width - 8), "at the real right edge");
    }

    @ParameterizedTest @ValueSource(ints = {352, 400, 528, 800})
    void angelIslandsOwnLayoutContinuesOnBothSides(int width) {
        var zone = rescue();
        var scene = zone.ending().scene();
        scene.beginQueue(S3kEndingSceneState.AIZ);
        scene.installed();
        scene.foreground().fill(0x1EB8, 0x1E0, TestEndingWidePlanes::ring);
        zone.screen().foregroundRoutine = 32;
        int copyX = 0x1EB8, margin = (width - 320) / 2;
        var camera = camera(width, copyX);
        for (int pixel = 0; pixel < width; pixel += 8) {
            int nativeX = (copyX & ~7) + pixel - margin;
            int expected = nativeX + 8 > copyX && nativeX < copyX + 320 ? scene.foreground().descriptor(nativeX, 0x90)
                    : layout(nativeX, 0x90);
            assertEquals(expected, shown(zone, camera, pixel), "picture pixel " + pixel);
        }
    }

    @ParameterizedTest @ValueSource(ints = {352, 400, 528, 800})
    void aWiderPictureRepeatsPlaneBBesideTheSprites(int width) {
        var plane = filled();
        int margin = (width - 320) / 2;
        var camera = camera(width, 0x200);
        for (int x = 0; x < 0x600; x += 8) {
            assertEquals(plane.descriptor(x - margin, 0x40), EndingWidePlanes.background(plane, camera, x, 0x40));
            assertEquals(EndingWidePlanes.background(plane, camera, x, 0x40),
                    EndingWidePlanes.background(plane, camera, x + 512, 0x40), "one image, repeated every 512 pixels");
        }
        assertNotEquals(0, EndingWidePlanes.backgroundRevision(plane, camera));
        assertNotEquals(EndingWidePlanes.backgroundRevision(plane, camera(320, 0x200)),
                EndingWidePlanes.backgroundRevision(plane, camera));
    }

    @Test void theIslandPlaneRepeatsItsNearestNativeColumnInEachMargin() {
        int backgroundX = 94, first = 94 & ~7, last = (94 + 319) & ~7;
        for (int x = 0; x < 0x400; x += 8) assertEquals(x, EndingWidePlanes.islandColumn(0, backgroundX, x));
        for (int margin : new int[] {16, 40, 104, 240}) {
            assertEquals(first, EndingWidePlanes.islandColumn(margin, backgroundX, first), "left margin");
            assertEquals(first, EndingWidePlanes.islandColumn(margin, backgroundX, first + margin));
            assertEquals(first + 8, EndingWidePlanes.islandColumn(margin, backgroundX, first + margin + 8));
            assertEquals(last, EndingWidePlanes.islandColumn(margin, backgroundX, last + margin));
            assertEquals(last, EndingWidePlanes.islandColumn(margin, backgroundX, last + margin + 200), "right margin");
        }
    }

    @Test void everyEndingSceneFillsThePictureUntilItsClosingArtOnBlack() {
        var rescue = new EndingZoneRuntimeState(PlayerCharacter.SONIC_ALONE);
        assertTrue(EndingWidePlanes.fillsPicture(rescue));
        rescue.ending().setFlag(3);
        assertFalse(EndingWidePlanes.fillsPicture(rescue), "the closing art stays between the bars");

        var fight = new SszZoneRuntimeState(1, PlayerCharacter.KNUCKLES);
        assertFalse(EndingWidePlanes.fillsPicture(fight), "the Mecha Sonic arena keeps its locked-arena fill");
        fight.setAct2EndingActive(true);
        assertTrue(EndingWidePlanes.fillsPicture(fight), "from the white screen of Mecha Sonic's final defeat");
        fight.ending().setFlag(3);
        assertFalse(EndingWidePlanes.fillsPicture(fight));

        var actOne = new SszZoneRuntimeState(0, PlayerCharacter.SONIC_ALONE);
        actOne.setAct2EndingActive(true);
        assertFalse(EndingWidePlanes.fillsPicture(actOne));
        assertFalse(EndingWidePlanes.fillsPicture(null));
    }
}
