package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdR11bCameraArtResidency;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.RetainedPlaneSceneRenderer;
import com.openggf.graphics.SpritePresentation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure literal component fixtures, not native ingress, a World visit, service execution or pixels.
 * CpuSat values are produced through the existing public draw-queue ports, never forged.
 */
class TestSonicCdPastDisplay {
    @Test
    void inputsRequireExactSelectedShapesAndEnabledOwners() {
        var b = base();
        assertThrows(NullPointerException.class, () -> new SonicCdPastDisplay.Inputs(null,
                b.selectedSat(), b.palette(), b.hscroll(), 0, true, b.foreground(), b.background(), b.current(), b.player()));
        for (int length : new int[] {0, 639, 641}) {
            assertThrows(IllegalArgumentException.class, () -> new SonicCdPastDisplay.Inputs(Optional.empty(),
                    bytes(new byte[length]), b.palette(), b.hscroll(), 0, true,
                    b.foreground(), b.background(), b.current(), b.player()));
        }
        for (int length : new int[] {0, 127, 129}) {
            assertThrows(IllegalArgumentException.class, () -> new SonicCdPastDisplay.Inputs(Optional.empty(),
                    b.selectedSat(), bytes(new byte[length]), b.hscroll(), 0, true,
                    b.foreground(), b.background(), b.current(), b.player()));
        }
        for (int length : new int[] {0, 1023, 1025}) {
            assertThrows(IllegalArgumentException.class, () -> new SonicCdPastDisplay.Inputs(Optional.empty(),
                    b.selectedSat(), b.palette(), bytes(new byte[length]), 0, true,
                    b.foreground(), b.background(), b.current(), b.player()));
        }
        assertThrows(NullPointerException.class, () -> new SonicCdPastDisplay.Inputs(Optional.empty(),
                b.selectedSat(), b.palette(), b.hscroll(), 0, true, null, b.background(), b.current(), b.player()));
        assertThrows(NullPointerException.class, () -> new SonicCdPastDisplay.Inputs(Optional.empty(),
                b.selectedSat(), b.palette(), b.hscroll(), 0, true, b.foreground(), null, b.current(), b.player()));
        assertThrows(NullPointerException.class, () -> new SonicCdPastDisplay.Inputs(Optional.empty(),
                b.selectedSat(), b.palette(), b.hscroll(), 0, true, b.foreground(), b.background(), null, b.player()));
        assertThrows(NullPointerException.class, () -> new SonicCdPastDisplay.Inputs(Optional.empty(),
                b.selectedSat(), b.palette(), b.hscroll(), 0, true, b.foreground(), b.background(), b.current(), null));
    }

    @Test
    void absentGenuineCpuAdmitsOnlyTheActuallyClearedSelectedSat() {
        var b = base();
        var selected = SonicCdPastDisplay.project(b);
        assertSame(b, selected.inputs());
        assertEquals(SpritePresentation.Frame.empty(), scene(selected).sprites());
        byte[] sat = new byte[640];
        sat[17] = 3;
        var input = inputs(Optional.empty(), sat, b.foreground(), b.background(), b.current(), b.player());
        failure(SonicCdPastDisplay.project(input), SonicCdPastDisplay.Boundary.MISSING_CPU_AUTHORITY, 17, -1, -1);
        assertEquals(3, input.selectedSat().unsignedByte(17));
    }

    @Test
    void disabledProjectionPreservesSelectedBytesWithoutSamplingUnownedInputs() {
        byte[] palette = fill(128, 0xA5), scroll = fill(1024, 0x80), sat = fill(640, 0xFF);
        var input = new SonicCdPastDisplay.Inputs(Optional.empty(), bytes(sat), bytes(palette), bytes(scroll),
                0x80017FFF, false, null, null, null, null);
        var selected = SonicCdPastDisplay.project(input);
        var disabled = assertInstanceOf(SonicCdPastDisplay.Disabled.class, selected.projection()).scene();
        assertSame(input, selected.inputs());
        assertFalse(disabled.displayEnabled());
        assertNull(disabled.foreground());
        assertNull(disabled.background());
        assertEquals(SpritePresentation.Frame.empty(), disabled.sprites());
        assertTrue(disabled.nativePatterns().isEmpty());
        assertArrayEquals(palette, disabled.selectedPalette128());
        assertEquals(0x80017FFF, disabled.vscrollLongBits());
        assertArrayEquals(Collections.nCopies(256, 0x80808080).stream().mapToInt(Integer::intValue).toArray(),
                disabled.packedHscroll256());
        var enabled = new SonicCdPastDisplay.Inputs(input.selectedCpu(), input.selectedSat(), input.palette(),
                input.hscroll(), input.vscrollLongBits(), true, uniform(0), uniform(0),
                CdR11bCameraArtResidency.unknown(), player(0, 0, 0, false));
        failure(SonicCdPastDisplay.project(enabled), SonicCdPastDisplay.Boundary.MISSING_CPU_AUTHORITY, 0, -1, -1);
    }

    @Test
    void genuineEmptyQueuePreservesDirtyUnusedSatTailWithoutInventingPieces() {
        var cpu = cpu(List.of(), 32, 128, 128, fill(640, 0xA5));
        assertEquals(0, cpu.spriteCount());
        assertArrayEquals(new byte[4], Arrays.copyOf(cpu.bytes(), 4));
        assertEquals(0xA5, cpu.bytes()[4] & 255);
        assertEquals(0xA5, cpu.bytes()[639] & 255);
        var b = base();
        var input = inputs(Optional.of(cpu), cpu.bytes(), b.foreground(), b.background(), b.current(), b.player());
        assertTrue(scene(SonicCdPastDisplay.project(input)).sprites().tiles().isEmpty());
        failure(SonicCdPastDisplay.project(inputs(Optional.empty(), cpu.bytes(),
                b.foreground(), b.background(), b.current(), b.player())),
                SonicCdPastDisplay.Boundary.MISSING_CPU_AUTHORITY, 4, -1, -1);
    }

    @Test
    void selectedCpuConsistencyIncludesAllRetainedUnusedBytes() {
        var cpu = cpu(one(0), 32, 128, 128, new byte[640]);
        byte[] selected = cpu.bytes();
        selected[639] = 1;
        var b = base();
        var input = inputs(Optional.of(cpu), selected, b.foreground(), b.background(), b.current(), b.player());
        failure(SonicCdPastDisplay.project(input), SonicCdPastDisplay.Boundary.SAT_MISMATCH, 639, -1, -1);
        assertEquals(0, cpu.bytes()[639]);
    }

    @Test
    void separatelyProducedEquivalentCpuValuesGiveEquivalentInputsAndSelections() {
        var a = cpu(one(0), 32, 128, 128, new byte[640]);
        var b = cpu(one(0), 32, 128, 128, new byte[640]);
        assertNotSame(a, b);
        assertArrayEquals(a.bytes(), b.bytes());
        var first = inputs(Optional.of(a), a.bytes(), uniform(0), uniform(0), current(0, 0x11),
                player(0x22, 0x33, 7, true));
        var second = inputs(Optional.of(b), b.bytes(), uniform(0), uniform(0), current(0, 0x11),
                player(0x22, 0x33, 7, true));
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(SonicCdPastDisplay.project(first), SonicCdPastDisplay.project(second));
        assertEquals(SonicCdPastDisplay.project(first).hashCode(), SonicCdPastDisplay.project(second).hashCode());
    }

    @Test
    void equalRawCpuBytesDoNotEraseDifferentGenuineProvenance() {
        var a = cpu(one(0), 32, 128, 128, new byte[640]);
        var b = cpu(one(0), 33, 128, 128, new byte[640]);
        assertArrayEquals(a.bytes(), b.bytes());
        assertEquals(new CdObjectDrawQueue.Provenance(2, 0, new CdObjectDrawQueue.CdSlotRef(32), 0),
                a.provenance().getFirst());
        assertNotEquals(a.provenance(), b.provenance());
        var first = inputs(Optional.of(a), a.bytes(), uniform(0), uniform(0), current(0, 0), player(0, 0, 0, false));
        var second = inputs(Optional.of(b), b.bytes(), uniform(0), uniform(0), current(0, 0), player(0, 0, 0, false));
        assertNotEquals(first, second);
        assertThrows(IllegalArgumentException.class,
                () -> SonicCdPastDisplay.validateSelected(SonicCdPastDisplay.project(first), second));
    }

    @Test
    void identicalRawSatCanRetainDifferentGenuineActiveCounts() {
        byte[] initial = new byte[640];
        initial[639] = 1;
        var piece = new CdObjectDrawQueue.RawPiece(0, 0, 0, -127);
        var a = cpu(Collections.nCopies(79, piece), 32, 128, 0, initial);
        var b = cpu(Collections.nCopies(80, piece), 32, 128, 0, initial);
        assertEquals(79, a.spriteCount());
        assertEquals(80, b.spriteCount());
        assertArrayEquals(a.bytes(), b.bytes());
        var first = inputs(Optional.of(a), a.bytes(), uniform(0), uniform(0), current(0, 0), player(0, 0, 0, false));
        var second = inputs(Optional.of(b), b.bytes(), uniform(0), uniform(0), current(0, 0), player(0, 0, 0, false));
        assertNotEquals(first, second);
        assertEquals(79, scene(SonicCdPastDisplay.project(first)).sprites().tiles().size());
        assertEquals(80, scene(SonicCdPastDisplay.project(second)).sprites().tiles().size());
    }

    @Test
    void completePlanesAllAttributesWrappedCoordinatesAndFullScrollTailAreRetained() {
        int[] foreground = new int[2048], background = new int[2048], packed = new int[256];
        byte[] rawScroll = new byte[1024];
        for (int i = 0; i < 2048; i++) {
            foreground[i] = (i & 31) << 11;
            background[i] = ((31 - i) & 31) << 11;
        }
        for (int i = 0; i < 256; i++) {
            packed[i] = 0x80017FFF + i * 0x10001;
            int at = i * 4;
            rawScroll[at] = (byte) (packed[i] >>> 24);
            rawScroll[at + 1] = (byte) (packed[i] >>> 16);
            rawScroll[at + 2] = (byte) (packed[i] >>> 8);
            rawScroll[at + 3] = (byte) packed[i];
        }
        byte[] palette = new byte[128];
        for (int i = 0; i < 128; i++) palette[i] = (byte) (i * 3);
        var input = new SonicCdPastDisplay.Inputs(Optional.empty(), bytes(new byte[640]), bytes(palette),
                bytes(rawScroll), 0x80017FFF, true, plane(foreground), plane(background),
                current(0, 0x12), player(0, 0, 0, false));
        var scene = scene(SonicCdPastDisplay.project(input));
        assertArrayEquals(foreground, scene.foreground().words64x32());
        assertArrayEquals(background, scene.background().words64x32());
        assertEquals(foreground[2047], scene.foreground().descriptorAt(-1, -1));
        assertEquals(foreground[0], scene.foreground().descriptorAt(512, 256));
        assertArrayEquals(packed, scene.packedHscroll256());
        assertEquals(packed[255], scene.packedHscroll256()[255]);
        assertArrayEquals(palette, scene.selectedPalette128());
        assertEquals(0x80017FFF, scene.vscrollLongBits());
        assertEquals(1, scene.nativePatterns().size());
    }

    @Test
    void nativeDescriptorZeroUsesActualNonblankCurrentPixels() {
        var input = inputs(Optional.empty(), new byte[640], uniform(0), uniform(0), current(0, 0x12),
                player(0xFF, 0xEE, 5, true));
        var expected = new SpritePresentation.PatternVersion(0x2121212121212121L, 0x2121212121212121L,
                0x2121212121212121L, 0x2121212121212121L);
        assertEquals(Map.of(0, expected), scene(SonicCdPastDisplay.project(input)).nativePatterns());
        assertEquals(0, input.foreground().word(0, 0));
        assertEquals(0x12, CdR11bCameraArtResidency.readTile(input.current(), 0).unsignedByte(0));
    }

    @Test
    void playerPhysicalWindowSelectsResidentPixelsInsteadOfCpuCueBuffer() {
        var cpu = cpu(one(0x780), 32, 128, 128, new byte[640]);
        var input = inputs(Optional.of(cpu), cpu.bytes(), uniform(0x780), uniform(0x780),
                CdR11bCameraArtResidency.unknown(), player(0x11, 0x22, 6, true));
        var scene = scene(SonicCdPastDisplay.project(input));
        var expected = new SpritePresentation.PatternVersion(0x2222222222222222L, 0x2222222222222222L,
                0x2222222222222222L, 0x2222222222222222L);
        assertEquals(Map.of(0x780, expected), scene.nativePatterns());
        assertEquals(expected, scene.sprites().patternVersions().get(PatternAtlasRange.OBJECTS.base() + 0x780));
        assertEquals(0x11, input.player().cpuBuffer().unsignedByte(0));
    }

    @Test
    void exactTwentyFourthResidentTileIsIncludedButNextPhysicalGapIsBlocked() {
        byte[] resident = fill(768, 0x22);
        Arrays.fill(resident, 23 * 32, 24 * 32, (byte) 0x34);
        var player = new CdPlayerArtRuntime.State(fill(768, 0xFF), resident, 0xFF, true);
        var input = inputs(Optional.empty(), new byte[640], uniform(0x797), uniform(0x797),
                CdR11bCameraArtResidency.unknown(), player);
        var expected = new SpritePresentation.PatternVersion(0x4343434343434343L, 0x4343434343434343L,
                0x4343434343434343L, 0x4343434343434343L);
        assertEquals(Map.of(0x797, expected), scene(SonicCdPastDisplay.project(input)).nativePatterns());
        var beyond = inputs(Optional.empty(), new byte[640], uniform(0x798), uniform(0x797),
                input.current(), player);
        failure(SonicCdPastDisplay.project(beyond), SonicCdPastDisplay.Boundary.OUTSIDE_OWNED_WINDOW,
                -1, 0x798, 0xF300);
    }

    @Test
    void planesAndSatShareOneFrozenPhysicalPatternVersionAndLogicalAddress() {
        var cpu = cpu(one(0x780), 32, 128, 128, new byte[640]);
        var input = inputs(Optional.of(cpu), cpu.bytes(), uniform(0xD780), uniform(0x9780),
                CdR11bCameraArtResidency.unknown(), player(0, 0x33, 0, false));
        var scene = scene(SonicCdPastDisplay.project(input));
        assertEquals(1, scene.nativePatterns().size());
        assertEquals(1, scene.sprites().patternVersions().size());
        var version = scene.nativePatterns().get(0x780);
        assertSame(version, scene.sprites().patternVersions().get(PatternAtlasRange.OBJECTS.base() + 0x780));
        assertEquals(PatternAtlasRange.OBJECTS.base() + 0x780, scene.sprites().tiles().getFirst().patternId());
        assertEquals(0xD780, scene.foreground().words64x32()[0]);
        assertEquals(0x9780, scene.background().words64x32()[0]);
    }

    @Test
    void genuineSelectedSatPreservesLiteralGeometryAttributesAndReversePieceOrder() {
        var cpu = cpu(List.of(new CdObjectDrawQueue.RawPiece(2, 0, 0xD800, 1),
                new CdObjectDrawQueue.RawPiece(10, 0, 0x2001, 9)), 32, 128, 128, new byte[640]);
        var input = inputs(Optional.of(cpu), cpu.bytes(), uniform(0), uniform(1),
                add(current(0, 0x11), 1, 0x22), player(0, 0, 0, false));
        var tiles = scene(SonicCdPastDisplay.project(input)).sprites().tiles();
        assertEquals(List.of(
                new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT, 0x20001, 1, false, false,
                        false, 9, 10, 8, 8, true, 0, false, 1),
                new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT, 0x20000, 2, true, true,
                        true, 1, 2, 8, 8, true, 0, false, 1)), tiles);
        assertEquals(List.of(new CdObjectDrawQueue.Provenance(2, 0, new CdObjectDrawQueue.CdSlotRef(32), 0),
                new CdObjectDrawQueue.Provenance(2, 0, new CdObjectDrawQueue.CdSlotRef(32), 1)), cpu.provenance());
    }

    @Test
    void unknownForegroundProducesOnlyExactFiniteCurrentPatternFailure() {
        var input = inputs(Optional.empty(), new byte[640], uniform(0), uniform(0),
                CdR11bCameraArtResidency.unknown(), player(0, 0, 0, false));
        var selected = SonicCdPastDisplay.project(input);
        assertSame(input, selected.inputs());
        failure(selected, SonicCdPastDisplay.Boundary.UNKNOWN_PATTERN, -1, 0, 0);
        assertTrue(input.current().region(CdR11bCameraArtResidency.Region.AT_0000).knownByte(0).isEmpty());
    }

    @Test
    void completeOffscreenLastBackgroundCellMustAlsoHaveKnownCurrentPixels() {
        int[] words = new int[2048];
        words[2047] = 0xF801;
        var input = inputs(Optional.empty(), new byte[640], uniform(0), plane(words),
                current(0, 0x11), player(0, 0, 0, false));
        failure(SonicCdPastDisplay.project(input), SonicCdPastDisplay.Boundary.UNKNOWN_PATTERN, -1, 1, 32);
        assertEquals(0xF801, input.background().word(63, 31));
    }

    @Test
    void attributeBitsCannotInventAnOwnedPhysicalWindow() {
        var input = inputs(Optional.empty(), new byte[640], uniform(0xFB00), uniform(0),
                current(0, 0), player(0, 0, 0, false));
        failure(SonicCdPastDisplay.project(input), SonicCdPastDisplay.Boundary.OUTSIDE_OWNED_WINDOW,
                -1, 0x300, 0x6000);
        var sat = cpu(one(0x300), 32, 128, 128, new byte[640]);
        var satOnlyGap = inputs(Optional.of(sat), sat.bytes(), uniform(0), uniform(0),
                current(0, 0), player(0, 0, 0, false));
        failure(SonicCdPastDisplay.project(satOnlyGap), SonicCdPastDisplay.Boundary.OUTSIDE_OWNED_WINDOW,
                -1, 0x300, 0x6000);
    }

    @Test
    void olderSelectedVersionSurvivesLaterCurrentPatternWrites() {
        var before = current(0, 0x11);
        var b = inputs(Optional.empty(), new byte[640], uniform(0), uniform(0), before, player(0, 0, 0, false));
        var selected = SonicCdPastDisplay.project(b);
        var after = add(before, 0, 0x22);
        var a = inputs(Optional.empty(), new byte[640], uniform(0), uniform(0), after, b.player());
        var later = SonicCdPastDisplay.project(a);
        assertEquals(new SpritePresentation.PatternVersion(0x1111111111111111L, 0x1111111111111111L,
                0x1111111111111111L, 0x1111111111111111L), scene(selected).nativePatterns().get(0));
        assertEquals(new SpritePresentation.PatternVersion(0x2222222222222222L, 0x2222222222222222L,
                0x2222222222222222L, 0x2222222222222222L), scene(later).nativePatterns().get(0));
        assertNotEquals(selected, later);
        assertEquals(0x11, CdR11bCameraArtResidency.readTile(before, 0).unsignedByte(0));
        assertSame(before, selected.inputs().current());
    }

    @Test
    void laterCpuTableCannotReplaceTheGenuinePriorSelectedCpu() {
        var selectedCpu = cpu(one(0), 32, 128, 128, new byte[640]);
        var laterCpu = cpu(one(1), 32, 128, 128, new byte[640]);
        var input = inputs(Optional.of(selectedCpu), selectedCpu.bytes(), uniform(0), uniform(0),
                add(current(0, 0x11), 1, 0x22), player(0, 0, 0, false));
        var selected = SonicCdPastDisplay.project(input);
        var substituted = inputs(Optional.of(laterCpu), selectedCpu.bytes(), input.foreground(), input.background(),
                input.current(), input.player());
        failure(SonicCdPastDisplay.project(substituted), SonicCdPastDisplay.Boundary.SAT_MISMATCH, 5, -1, -1);
        assertThrows(IllegalArgumentException.class, () -> SonicCdPastDisplay.validateSelected(selected, substituted));
        assertEquals(0x20000, scene(selected).sprites().tiles().getFirst().patternId());
    }

    @Test
    void laterCpuArtCueDoesNotChangeAnUnchangedSelectedResidentPicture() {
        var a = inputs(Optional.empty(), new byte[640], uniform(0x780), uniform(0x780),
                CdR11bCameraArtResidency.unknown(), player(0x11, 0x33, 1, false));
        var b = inputs(Optional.empty(), new byte[640], uniform(0x780), uniform(0x780),
                CdR11bCameraArtResidency.unknown(), player(0xEE, 0x33, 2, true));
        assertNotEquals(a, b);
        assertEquals(scene(SonicCdPastDisplay.project(a)), scene(SonicCdPastDisplay.project(b)));
        assertThrows(IllegalArgumentException.class, () -> SonicCdPastDisplay.validateSelected(SonicCdPastDisplay.project(a), b));
    }

    @Test
    void genuineOutOfPolicyYIsBlockedBeforeAnyUnknownPlaneOrArtRead() {
        var cpu = cpu(one(0), 32, 128, 512, new byte[640]);
        assertEquals(1, cpu.spriteCount());
        assertEquals(2, cpu.bytes()[0] & 255);
        var input = inputs(Optional.of(cpu), cpu.bytes(), uniform(0x300), uniform(0),
                CdR11bCameraArtResidency.unknown(), player(0, 0, 0, false));
        failure(SonicCdPastDisplay.project(input), SonicCdPastDisplay.Boundary.SAT_COORDINATES, 0, -1, -1);
        assertArrayEquals(cpu.bytes(), input.selectedSat().copy());
        // X=0/high size cannot be minted through the current genuine queue's finite RawPiece API.
    }

    @Test
    void validationComparesEverySelectedInputByContentWithoutReprojection() {
        var b = base();
        var selected = SonicCdPastDisplay.project(b);
        var equivalent = inputs(Optional.empty(), new byte[640], uniform(0), uniform(0), current(0, 0x11),
                player(0, 0, 0, false));
        assertDoesNotThrow(() -> SonicCdPastDisplay.validateSelected(selected, equivalent));
        var variants = List.of(
                new SonicCdPastDisplay.Inputs(b.selectedCpu(), b.selectedSat(), bytes(fill(128, 1)), b.hscroll(),
                        0, true, b.foreground(), b.background(), b.current(), b.player()),
                new SonicCdPastDisplay.Inputs(b.selectedCpu(), b.selectedSat(), b.palette(), bytes(fill(1024, 1)),
                        0, true, b.foreground(), b.background(), b.current(), b.player()),
                new SonicCdPastDisplay.Inputs(b.selectedCpu(), b.selectedSat(), b.palette(), b.hscroll(),
                        1, true, b.foreground(), b.background(), b.current(), b.player()),
                inputs(b.selectedCpu(), b.selectedSat().copy(), uniform(0x800), b.background(), b.current(), b.player()),
                inputs(b.selectedCpu(), b.selectedSat().copy(), b.foreground(), uniform(0x1000), b.current(), b.player()),
                inputs(b.selectedCpu(), b.selectedSat().copy(), b.foreground(), b.background(),
                        add(b.current(), 1, 0x44), b.player()),
                inputs(b.selectedCpu(), b.selectedSat().copy(), b.foreground(), b.background(),
                        b.current(), player(1, 0, 0, false)),
                inputs(b.selectedCpu(), b.selectedSat().copy(), b.foreground(), b.background(),
                        b.current(), player(0, 1, 0, false)),
                inputs(b.selectedCpu(), b.selectedSat().copy(), b.foreground(), b.background(),
                        b.current(), player(0, 0, 1, false)),
                inputs(b.selectedCpu(), b.selectedSat().copy(), b.foreground(), b.background(),
                        b.current(), player(0, 0, 0, true)),
                new SonicCdPastDisplay.Inputs(b.selectedCpu(), b.selectedSat(), b.palette(), b.hscroll(),
                        0, false, b.foreground(), b.background(), b.current(), b.player()));
        for (var variant : variants) {
            assertNotEquals(b, variant);
            assertThrows(IllegalArgumentException.class, () -> SonicCdPastDisplay.validateSelected(selected, variant));
        }
        var blockedInput = inputs(Optional.empty(), new byte[640], uniform(0), uniform(0),
                CdR11bCameraArtResidency.unknown(), player(0, 0, 0, false));
        assertDoesNotThrow(() -> SonicCdPastDisplay.validateSelected(SonicCdPastDisplay.project(blockedInput),
                inputs(Optional.empty(), new byte[640], uniform(0), uniform(0),
                        CdR11bCameraArtResidency.unknown(), player(0, 0, 0, false))));
    }

    @Test
    void allCallerArraysAndPublishedCollectionsRemainImmutable() {
        byte[] sat = new byte[640], palette = new byte[128], scroll = new byte[1024];
        int[] descriptors = new int[2048];
        byte[] cpuArt = new byte[768], resident = new byte[768];
        var player = new CdPlayerArtRuntime.State(cpuArt, resident, 0, false);
        var input = new SonicCdPastDisplay.Inputs(Optional.empty(), bytes(sat), bytes(palette), bytes(scroll),
                0, true, plane(descriptors), plane(descriptors), current(0, 0x11), player);
        sat[0] = 1; palette[0] = 1; scroll[0] = 1; descriptors[0] = 1; cpuArt[0] = 1; resident[0] = 1;
        var selected = SonicCdPastDisplay.project(input);
        var scene = scene(selected);
        scene.foreground().words64x32()[0] = 1;
        scene.packedHscroll256()[0] = 1;
        scene.selectedPalette128()[0] = 1;
        input.selectedSat().copy()[0] = 1;
        assertEquals(0, scene.foreground().words64x32()[0]);
        assertEquals(0, scene.packedHscroll256()[0]);
        assertEquals(0, scene.selectedPalette128()[0]);
        assertEquals(0, input.selectedSat().unsignedByte(0));
        assertEquals(0, input.player().cpuBuffer().unsignedByte(0));
        assertEquals(0, input.player().residentBank().unsignedByte(0));
        assertThrows(UnsupportedOperationException.class, () -> scene.nativePatterns().clear());
        assertThrows(UnsupportedOperationException.class, () -> scene.sprites().tiles().add(null));
        var genuine = cpu(one(0), 32, 128, 128, new byte[640]);
        byte[] copy = genuine.bytes();
        copy[1] = 0;
        assertEquals(0x80, genuine.bytes()[1] & 255);
        assertThrows(UnsupportedOperationException.class, () -> genuine.provenance().clear());
    }

    @Test
    void blockedReplayHasStableFiniteValueMetadataAndNoRetainedThrowable() {
        var a = inputs(Optional.empty(), new byte[640], uniform(0), uniform(0),
                CdR11bCameraArtResidency.unknown(), player(0, 0, 0, false));
        var b = inputs(Optional.empty(), new byte[640], uniform(0), uniform(0),
                CdR11bCameraArtResidency.unknown(), player(0, 0, 0, false));
        var first = SonicCdPastDisplay.project(a);
        var replay = SonicCdPastDisplay.project(b);
        assertEquals(first, replay);
        assertEquals(first.hashCode(), replay.hashCode());
        assertEquals(new SonicCdPastDisplay.DisplayFailure(SonicCdPastDisplay.Boundary.UNKNOWN_PATTERN,
                -1, 0, 0), assertInstanceOf(SonicCdPastDisplay.Blocked.class, first.projection()).failure());
        var value = assertInstanceOf(SonicCdPastDisplay.Blocked.class, first.projection()).failure();
        assertEquals(SonicCdPastDisplay.Boundary.UNKNOWN_PATTERN, value.boundary());
        assertEquals(-1, value.byteOffset());
        assertEquals(0, value.nativePatternId());
        assertEquals(0, value.destinationWord());
        assertThrows(IllegalArgumentException.class, () -> new SonicCdPastDisplay.DisplayFailure(
                SonicCdPastDisplay.Boundary.UNKNOWN_PATTERN, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SonicCdPastDisplay.DisplayFailure(
                SonicCdPastDisplay.Boundary.SAT_MISMATCH, -1, -1, -1));
        assertThrows(IllegalArgumentException.class, () -> new SonicCdPastDisplay.DisplayFailure(
                SonicCdPastDisplay.Boundary.OUTSIDE_OWNED_WINDOW, -1, 0x300, 0x6001));
        assertThrows(IllegalArgumentException.class, () -> new SonicCdPastDisplay.Selection(a,
                new SonicCdPastDisplay.Disabled(new RetainedPlaneSceneRenderer.Scene(null, null, new int[256],
                        0, new byte[128], false, SpritePresentation.Frame.empty(), Map.of()))));
    }

    private static CdAssetBytes bytes(byte[] value) { return CdAssetBytes.copyOf(value); }

    private static byte[] fill(int count, int value) {
        byte[] bytes = new byte[count];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private static CdR11aBackgroundPlane.State plane(int[] words) {
        byte[] raw = new byte[4096];
        assertEquals(2048, words.length);
        for (int i = 0; i < words.length; i++) {
            raw[i * 2] = (byte) (words[i] >>> 8);
            raw[i * 2 + 1] = (byte) words[i];
        }
        return new CdR11aBackgroundPlane.State(bytes(raw));
    }

    private static CdR11aBackgroundPlane.State uniform(int word) {
        int[] words = new int[2048];
        Arrays.fill(words, word);
        return plane(words);
    }

    private static CdR11bCameraArtResidency.State current(int nativeId, int rawByte) {
        return add(CdR11bCameraArtResidency.unknown(), nativeId, rawByte);
    }

    private static CdR11bCameraArtResidency.State add(CdR11bCameraArtResidency.State before,
                                                    int nativeId, int rawByte) {
        return CdR11bCameraArtResidency.writeTile(before, nativeId << 5, bytes(fill(32, rawByte)));
    }

    private static CdPlayerArtRuntime.State player(int cpuByte, int residentByte, int frame, boolean dirty) {
        return new CdPlayerArtRuntime.State(fill(768, cpuByte), fill(768, residentByte), frame, dirty);
    }

    private static SonicCdPastDisplay.Inputs inputs(Optional<CdObjectDrawQueue.CpuSat> cpu, byte[] selectedSat,
                                                     CdR11aBackgroundPlane.State foreground,
                                                     CdR11aBackgroundPlane.State background,
                                                     CdR11bCameraArtResidency.State current,
                                                     CdPlayerArtRuntime.State player) {
        return new SonicCdPastDisplay.Inputs(cpu, bytes(selectedSat), bytes(new byte[128]), bytes(new byte[1024]),
                0, true, foreground, background, current, player);
    }

    private static SonicCdPastDisplay.Inputs base() {
        return inputs(Optional.empty(), new byte[640], uniform(0), uniform(0), current(0, 0x11),
                player(0, 0, 0, false));
    }

    private static RetainedPlaneSceneRenderer.Scene scene(SonicCdPastDisplay.Selection selected) {
        return assertInstanceOf(SonicCdPastDisplay.Ready.class, selected.projection()).scene();
    }

    private static void failure(SonicCdPastDisplay.Selection selected, SonicCdPastDisplay.Boundary boundary,
                                int byteOffset, int nativePatternId, int destinationWord) {
        assertEquals(new SonicCdPastDisplay.DisplayFailure(boundary, byteOffset, nativePatternId, destinationWord),
                assertInstanceOf(SonicCdPastDisplay.Blocked.class, selected.projection()).failure());
    }

    private static List<CdObjectDrawQueue.RawPiece> one(int nativeId) {
        return List.of(new CdObjectDrawQueue.RawPiece(0, 0, nativeId, 0));
    }

    /** Existing public queue factory with explicit component ports and no private authority fabrication. */
    private static CdObjectDrawQueue.CpuSat cpu(List<CdObjectDrawQueue.RawPiece> pieces, int physicalSlot,
                                               int xWord, int screenYWord, byte[] initialSat) {
        var ref = new CdObjectDrawQueue.CdSlotRef(physicalSlot);
        int[] flags = {0};
        var slots = new CdObjectDrawQueue.CdDrawSlots() {
            @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef queried) {
                assertEquals(ref, queried);
                return new CdObjectDrawQueue.LiveDrawSlot(1, flags[0], xWord, screenYWord, screenYWord,
                        0x200000, 0, 0);
            }
            @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef queried, boolean value) {
                assertEquals(ref, queried);
                flags[0] = (flags[0] & ~0x80) | (value ? 0x80 : 0);
            }
        };
        CdObjectDrawQueue.CdDrawMappings mappings = (address, staticPiece, frame) -> {
            assertEquals(0x200000, address);
            assertFalse(staticPiece);
            assertEquals(0, frame);
            return new CdObjectDrawQueue.FrameMapping(pieces.size(), pieces);
        };
        var camera = new CdObjectDrawQueue.CameraWords(0, 0);
        try (var queue = new CdObjectDrawQueue(slots, mappings, initialSat)) {
            queue.beginPass();
            assertEquals(CdObjectDrawQueue.EnqueueResult.QUEUED, queue.drawObject(
                    new CdObjectDrawQueue.DrawCallCut(ref, 0, xWord, screenYWord, 16, 16, 2), camera));
            var output = queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera, camera, camera));
            assertEquals(CdObjectDrawQueue.Phase.CLOSED_CUT, queue.phase());
            assertSame(output, queue.cpuSat());
            return output;
        }
    }
}
