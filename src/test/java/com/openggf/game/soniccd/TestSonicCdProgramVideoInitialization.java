package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdR11aCameraArtResidency;
import com.openggf.game.soniccd.resources.CdR11bCameraArtResidency;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Native InitVDP finite outputs (Functions (General).asm:111-176, pin1ecc5f3a).
 * These direct component inputs execute no module admission, native controller,
 * Frame, clock, actual GPU upload or programme load. Root owns the actual call cut.
 */
class TestSonicCdProgramVideoInitialization {
    @Test
    void presentUnknownIngressDoesNotPretendThatZeroStorageWasCleared() {
        var unknown = CdR11aCameraArtResidency.unknown();
        int tiles = 0;
        for (var region : CdR11aCameraArtResidency.Region.values()) {
            var view = unknown.region(region); tiles += region.tileCount();
            for (int i = 0; i < region.byteLength(); i++) {
                assertEquals(0, view.knownMask().unsignedByte(i)); assertTrue(view.knownByte(i).isEmpty());
            }
            assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,
                    assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
                            () -> CdR11aCameraArtResidency.readTile(unknown, region.destinationWord() >>> 5)).boundary());
        }
        assertEquals(452, tiles);
    }

    @Test
    void presentInitializationClearsEveryOwnedByteWithoutAddingUnownedPatterns() {
        var before = CdR11aCameraArtResidency.unknown();
        var dirty = CdR11aCameraArtResidency.writeTile(before, 0x8120, bytes(32, 0x71));
        var value = presentation().initializeProgramVideo(dirty, art(0x39, 0xE7, true));
        int tiles = 0;
        for (var region : CdR11aCameraArtResidency.Region.values()) {
            var view = value.currentPatterns().region(region);
            for (int i = 0; i < region.byteLength(); i++) {
                assertEquals(1, view.knownMask().unsignedByte(i)); assertEquals(0, view.knownByte(i).orElseThrow());
            }
            for (int i = 0; i < region.tileCount(); i++) {
                assertZero(CdR11aCameraArtResidency.readTile(value.currentPatterns(), (region.destinationWord() >>> 5) + i));
                tiles++;
            }
        }
        assertEquals(452, tiles);
        assertArrayEquals(bytes(32, 0x71).copy(), CdR11aCameraArtResidency.readTile(dirty, 0x8120 >>> 5).copy());
        assertEquals(CdR11aCameraArtResidency.Boundary.OUTSIDE_OWNED_WINDOW,
                assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
                        () -> CdR11aCameraArtResidency.readTile(value.currentPatterns(), 0)).boundary());
    }

    @Test
    void pastInitializationUsesItsExisting1510TileProfileAndKeepsHolesExplicit() {
        var unknown = CdR11bCameraArtResidency.unknown();
        var dirty = CdR11bCameraArtResidency.writeTile(unknown, 0xB420, bytes(32, 0x73));
        var value = presentation().initializeProgramVideo(dirty, art(0x11, 0x9A, false));
        int tiles = 0;
        for (var region : CdR11bCameraArtResidency.Region.values()) {
            for (int i = 0; i < region.byteLength(); i++) {
                assertEquals(1, value.currentPatterns().region(region).knownMask().unsignedByte(i));
                assertEquals(0, value.currentPatterns().region(region).knownByte(i).orElseThrow());
            }
            for (int i = 0; i < region.tileCount(); i++) {
                assertZero(CdR11bCameraArtResidency.readTile(value.currentPatterns(), (region.destinationWord() >>> 5) + i)); tiles++;
            }
        }
        assertEquals(1542, tiles);
        assertTrue(unknown.region(CdR11bCameraArtResidency.Region.AT_0000).knownByte(0).isEmpty());
        assertArrayEquals(bytes(32, 0x73).copy(), CdR11bCameraArtResidency.readTile(dirty, 0xB420 >>> 5).copy());
        assertEquals(CdR11bCameraArtResidency.Boundary.OUTSIDE_OWNED_WINDOW,
                assertThrows(CdR11bCameraArtResidency.OwnershipBoundary.class,
                        () -> CdR11bCameraArtResidency.readTile(value.currentPatterns(), 0x5D40 >>> 5)).boundary());
    }

    @Test
    void playerResidentClearPreservesTheWholeCpuWindowAndComparisonFlags() {
        for (boolean dirty : new boolean[]{false, true}) for (int last : new int[]{0, 0xFF}) {
            var before = art(0x58, last, dirty);
            var next = presentation().initializeProgramVideo(CdR11bCameraArtResidency.unknown(), before).playerArt();
            assertSame(before.cpuBuffer(), next.cpuBuffer()); assertZero(next.residentBank());
            assertEquals(last, next.lastFrame()); assertEquals(dirty, next.dirty());
            assertArrayEquals(bytes(0x300, 0x58).copy(), before.cpuBuffer().copy());
            assertArrayEquals(bytes(0x300, 0xD8).copy(), before.residentBank().copy());
        }
    }

    @Test
    void displayedPaletteSatAndBackgroundPlaneAreCompleteKnownZeroOutputs() {
        var value = presentation().initializeProgramVideo(CdR11aCameraArtResidency.unknown(), art(9, 7, true));
        var selected = value.selection();
        assertEquals(128, selected.displayedPalette().length()); assertZero(selected.displayedPalette());
        assertEquals(640, selected.displayedSat().length()); assertZero(selected.displayedSat());
        assertEquals(4096, selected.backgroundPlane().nameTable().length()); assertZero(selected.backgroundPlane().nameTable());
        assertEquals(0, selected.screenHScrollLongBits()); assertEquals(0, selected.screenVScrollLongBits());
        assertFalse(selected.displayEnabled());
        assertSame(selected.backgroundPlane(), selected.plane(SonicCdPlayerPresentation.ProgramPlane.BACKGROUND));
    }

    @Test
    void presentProductsUseCompleteValueEqualityAcrossIndependentByteViews() {
        var a = presentation().initializeProgramVideo(CdR11aCameraArtResidency.unknown(), art(0x21, 0xF0, true));
        var b = presentation().initializeProgramVideo(CdR11aCameraArtResidency.unknown(), art(0x21, 0xF0, true));
        assertNotSame(a, b); assertNotSame(a.playerArt().cpuBuffer(), b.playerArt().cpuBuffer());
        assertEquals(a, b); assertEquals(a.hashCode(), b.hashCode());
        assertEquals(1, new HashSet<>(java.util.List.of(a, b)).size());
        var changed = presentation().initializeProgramVideo(CdR11aCameraArtResidency.unknown(), art(0x22, 0xF0, true));
        assertNotEquals(a, changed);
    }

    @Test
    void pastProductsUseCompleteValueEqualityWithoutBecomingWorldAuthority() {
        var a = presentation().initializeProgramVideo(CdR11bCameraArtResidency.unknown(), art(0x61, 0x11, false));
        var b = presentation().initializeProgramVideo(CdR11bCameraArtResidency.unknown(), art(0x61, 0x11, false));
        assertEquals(a, b); assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, presentation().initializeProgramVideo(CdR11bCameraArtResidency.unknown(), art(0x61, 0x11, true)));
        assertNotEquals(a, presentation().initializeProgramVideo(CdR11bCameraArtResidency.unknown(), art(0x61, 0x12, false)));
        assertNotEquals(a, presentation().initializeProgramVideo(CdR11aCameraArtResidency.unknown(), art(0x61, 0x11, false)));
    }

    @Test
    void selectionValueEqualityIncludesPalettePlaneSatScreenLongsAndDisplay() {
        var zero = selection(0, 0, 0, 0, 0, false); var same = selection(0, 0, 0, 0, 0, false);
        assertEquals(zero, same); assertEquals(zero.hashCode(), same.hashCode());
        for (var changed : java.util.List.of(selection(1, 0, 0, 0, 0, false), selection(0, 1, 0, 0, 0, false),
                selection(0, 0, 1, 0, 0, false), selection(0, 0, 0, -1, 0, false),
                selection(0, 0, 0, 0, Integer.MIN_VALUE, false), selection(0, 0, 0, 0, 0, true))) {
            assertNotEquals(zero, changed);
        }
    }

    @Test
    void immutableOutputCopiesCannotModifyAnotherInitializationOrEarlierInput() {
        var p = presentation(); var before = art(0x2C, 9, true);
        var first = p.initializeProgramVideo(CdR11bCameraArtResidency.unknown(), before);
        var paletteCopy = first.selection().displayedPalette().copy(); Arrays.fill(paletteCopy, (byte) 0x7F);
        var satCopy = first.selection().displayedSat().copy(); Arrays.fill(satCopy, (byte) 0x7F);
        var planeCopy = first.selection().backgroundPlane().nameTable().copy(); Arrays.fill(planeCopy, (byte) 0x7F);
        var residentCopy = first.playerArt().residentBank().copy(); Arrays.fill(residentCopy, (byte) 0x7F);
        var second = p.initializeProgramVideo(CdR11bCameraArtResidency.unknown(), before);
        assertEquals(first, second); assertZero(first.selection().displayedPalette());
        assertZero(first.selection().displayedSat()); assertZero(first.selection().backgroundPlane().nameTable());
        assertZero(first.playerArt().residentBank()); assertSame(before.cpuBuffer(), first.playerArt().cpuBuffer());
    }

    @Test
    void unrepresentedForegroundAndWindowReadsStopAtTheirExactConsumer() {
        var selected = presentation().initializeProgramVideo(CdR11aCameraArtResidency.unknown(), art(0, 0, false)).selection();
        for (var plane : new SonicCdPlayerPresentation.ProgramPlane[]{SonicCdPlayerPresentation.ProgramPlane.FOREGROUND,
                SonicCdPlayerPresentation.ProgramPlane.WINDOW}) {
            assertEquals(plane, assertThrows(SonicCdPlayerPresentation.UnsupportedProgramVideo.class,
                    () -> selected.plane(plane)).plane());
        }
        assertThrows(NullPointerException.class, () -> selected.plane(null));
        assertZero(selected.backgroundPlane().nameTable());
    }

    @Test
    void repeatedCallsAreObservationalDataOperationsWithoutMintedCompletionTokens() {
        var p = presentation(); var state = CdR11aCameraArtResidency.unknown(); var before = art(0x7B, 0xC3, true);
        var first = p.initializeProgramVideo(state, before); var again = p.initializeProgramVideo(state, before);
        assertEquals(first, again); assertEquals(first.hashCode(), again.hashCode());
        assertSame(before.cpuBuffer(), first.playerArt().cpuBuffer());
        assertEquals(0xC3, first.playerArt().lastFrame()); assertTrue(first.playerArt().dirty());
    }

    @Test
    void nullInputsRejectWithoutRepairingAnUnknownPatternOrArtValue() {
        var p = presentation(); var a = CdR11aCameraArtResidency.unknown(); var b = CdR11bCameraArtResidency.unknown();
        var art = art(0, 0, false);
        assertThrows(NullPointerException.class, () -> p.initializeProgramVideo((CdR11aCameraArtResidency.State) null, art));
        assertThrows(NullPointerException.class, () -> p.initializeProgramVideo((CdR11bCameraArtResidency.State) null, art));
        assertThrows(NullPointerException.class, () -> p.initializeProgramVideo(a, null));
        assertThrows(NullPointerException.class, () -> p.initializeProgramVideo(b, null));
        assertTrue(a.region(CdR11aCameraArtResidency.Region.AT_8120).knownByte(0).isEmpty());
        assertTrue(b.region(CdR11bCameraArtResidency.Region.AT_0000).knownByte(0).isEmpty());
        assertThrows(NullPointerException.class, () -> CdR11aCameraArtResidency.clear(null));
    }

    @Test
    void staticAndTerrainOnlyEntriesCannotExecuteOpeningProgramInitialization() {
        var state = CdR11aCameraArtResidency.unknown(); var art = art(0, 0, false);
        var statics = new SonicCdPlayerPresentation(new SonicCdGameModule());
        var terrain = new SonicCdPlayerPresentation(SonicCdGameModule.terrainOnlyDevelopmentPlayer());
        assertThrows(IllegalStateException.class, () -> statics.initializeProgramVideo(state, art));
        assertThrows(IllegalStateException.class, () -> terrain.initializeProgramVideo(state, art));
        assertTrue(state.region(CdR11aCameraArtResidency.Region.AT_8120).knownByte(0).isEmpty());
    }

    @Test
    void directPresentClearPreservesEveryOriginalSnapshotAndItsStrictProfile() {
        var before = CdR11aCameraArtResidency.writeTile(CdR11aCameraArtResidency.unknown(), 0xD960, bytes(32, 0x4C));
        var zero = CdR11aCameraArtResidency.clear(before); var again = CdR11aCameraArtResidency.clear(zero);
        assertEquals(zero, again); assertEquals(zero.hashCode(), again.hashCode());
        assertArrayEquals(bytes(32, 0x4C).copy(), CdR11aCameraArtResidency.readTile(before, 0xD960 >>> 5).copy());
        assertZero(CdR11aCameraArtResidency.readTile(zero, 0xD960 >>> 5));
        assertEquals(CdR11aCameraArtResidency.Boundary.OUTSIDE_OWNED_WINDOW,
                assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
                        () -> CdR11aCameraArtResidency.readTile(zero, 0xF000 >>> 5)).boundary());
    }

    @Test
    void valueConstructorsValidateCompleteShapesWithoutExecutingInitialization() {
        assertThrows(IllegalArgumentException.class, () -> new SonicCdPlayerPresentation.ProgramVideoSelection(
                bytes(127, 0), plane(0), bytes(640, 0), 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new SonicCdPlayerPresentation.ProgramVideoSelection(
                bytes(128, 0), plane(0), bytes(639, 0), 0, 0, false));
        assertThrows(NullPointerException.class, () -> new SonicCdPlayerPresentation.ProgramVideoSelection(
                bytes(128, 0), null, bytes(640, 0), 0, 0, false));
        assertThrows(NullPointerException.class, () -> new SonicCdPlayerPresentation.PresentVideoInit(
                null, art(0, 0, false), selection(0, 0, 0, 0, 0, false)));
        assertThrows(NullPointerException.class, () -> new SonicCdPlayerPresentation.PastVideoInit(
                CdR11bCameraArtResidency.unknown(), null, selection(0, 0, 0, 0, 0, false)));
    }

    private static SonicCdPlayerPresentation presentation() {
        return new SonicCdPlayerPresentation(SonicCdGameModule.openingObjectsDevelopmentPlayer());
    }
    private static CdAssetBytes bytes(int count, int seed) {
        var bytes = new byte[count]; for (int i = 0; i < count; i++) bytes[i] = (byte) (seed + i * 29);
        return CdAssetBytes.copyOf(bytes);
    }
    private static CdPlayerArtRuntime.State art(int seed, int last, boolean dirty) {
        return new CdPlayerArtRuntime.State(bytes(0x300, seed), bytes(0x300, seed ^ 0x80), last, dirty);
    }
    private static CdR11aBackgroundPlane.State plane(int lastByte) {
        var raw = new byte[4096]; raw[4095] = (byte) lastByte;
        return new CdR11aBackgroundPlane.State(CdAssetBytes.copyOf(raw));
    }
    private static SonicCdPlayerPresentation.ProgramVideoSelection selection(int paletteByte, int satByte, int planeByte,
                                                                                  int h, int v, boolean display) {
        var palette = new byte[128]; palette[127] = (byte) paletteByte;
        var sat = new byte[640]; sat[639] = (byte) satByte;
        return new SonicCdPlayerPresentation.ProgramVideoSelection(CdAssetBytes.copyOf(palette), plane(planeByte),
                CdAssetBytes.copyOf(sat), h, v, display);
    }
    private static void assertZero(CdAssetBytes value) {
        for (int i = 0; i < value.length(); i++) assertEquals(0, value.unsignedByte(i), "byte " + i);
    }
}
