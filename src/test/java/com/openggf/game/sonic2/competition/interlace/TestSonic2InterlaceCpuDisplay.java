package com.openggf.game.sonic2.competition.interlace;

import com.openggf.data.RomByteReader;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile;
import com.openggf.game.sonic2.competition.Sonic2CompetitionNativeArt;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceCpuDisplay.HintState;
import static com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceWorldInputs.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2InterlaceCpuDisplay {
    @Test void p1ParksAndHinterruptUploadsOldP2BeforeResumingOnlyTheCapturedContinuation() throws Exception {
        var h = harness();
        byte[] seed = h.seed().clone();
        put(seed, 0xDD00, 0x345); put(seed, 0xDD02, 0x0500);
        put(seed, 0xDD04, 0xCAFE); put(seed, 0xDD06, 0x180);
        h.display().bindAct(h.profile(), seed, h.art());
        var beforeArt = h.art().capture();
        var pending = h.display().buildAfterObjects(input(1, false), HintState.PENDING);
        assertEquals(0x1D8, pending.ram().readWord(0xF800));
        assertEquals(0x345, pending.ram().readWord(0xDD00), "P2 has not been rebuilt before H-int");
        assertEquals(beforeArt, h.art().capture(), "BuildSprites only edits CPU RAM");
        assertFalse(pending.consumeDisplayLists());
        var p1 = pending.ram().range(0xF800, 768);
        var oldUpload = h.display().prepareHInterruptUpload();
        assertEquals(0x345, oldUpload.write().words().getFirst());
        assertEquals(320, oldUpload.write().words().size());
        assertThrows(IllegalStateException.class, () -> h.display().buildAfterObjects(input(2, false), HintState.CLEAR));
        assertThrows(IllegalStateException.class, () -> h.display().resumePlayer2AfterHintClear(oldUpload, HintState.PENDING));

        // Explicit CPU transaction only: this does not certify a physical H-int arrival or blanking duration.
        var oldPhysical = h.art().publishCpuWordWrites(List.of(oldUpload.write()));
        var completed = h.display().resumePlayer2AfterHintClear(oldUpload, HintState.CLEAR);
        assertArrayEquals(p1, completed.ram().range(0xF800, 768), "resume must not rebuild P1");
        assertNotEquals(0x345, completed.ram().readWord(0xDD00));
        assertEquals(0x345, h.art().current().readWord(0xF800), "new P2 CPU data is not uploaded twice");
        assertSame(oldPhysical, h.art().current());
        assertTrue(completed.consumeDisplayLists());
        assertEquals(HintState.CLEAR, completed.hintState());
        assertThrows(IllegalStateException.class, () -> h.display().resumePlayer2AfterHintClear(oldUpload, HintState.CLEAR));
    }

    @Test void retainedSourcePublicationsAndExplicitFieldProjectionDoNotAdvanceAnyOwner() throws Exception {
        var h = harness();
        var publication = h.display().buildAfterObjects(input(1, true), HintState.CLEAR);
        assertFalse(publication.consumeDisplayLists(), "Teleport_flag retains the native display lists");
        var saved = h.display().capture(); var artSaved = h.art().capture();
        for (var field : Sonic2InterlaceDecoder.Field.values()) for (int page = 0; page < 2; page++) {
            var projected = h.display().prepareCpuPage(publication, page, field);
            assertEquals(field, projected.input().field());
            assertTrue(projected.sprites().size() > 2);
            for (var sprite : projected.sprites()) {
                assertEquals(field.projectY(sprite.nativeSprite().y()), sprite.geometry().y());
                assertEquals(sprite.nativeSprite().widthCells() * sprite.nativeSprite().heightCells(),
                        sprite.fragments().size());
            }
        }
        assertEquals(saved, h.display().capture()); assertEquals(artSaved, h.art().capture());
        h.display().buildAfterObjects(input(2, false), HintState.PENDING);
        h.art().publishCpuWordWrites(List.of(new Sonic2InterlaceVramService.CpuWordWrite(0xF000, 2, List.of(0xABCD))));
        assertEquals(saved.ram(), publication.ram());
        assertSame(artSaved.vram(), publication.artImage());
        assertNotEquals(publication.artImage(), h.display().current().artImage());
        assertFalse(h.display().supportsZone(0)); assertFalse(h.display().supportsZone(11)); assertFalse(h.display().supportsZone(12));
    }

    @Test void actualArtRamIncludingDbfDeformationSpillIsRequiredAndDefensivelyRetained() throws Exception {
        var h = harness();
        var expected = h.seed().clone();
        assertTrue(Arrays.stream(new int[]{read(expected, 0xA800), read(expected, 0xA802), read(expected, 0xA804)})
                .anyMatch(word -> word != 0), "real EHZ DBF spill seeds deformation scratch");
        Arrays.fill(h.seed(), (byte) 0);
        assertArrayEquals(expected, h.display().current().ram().bytes());
        assertThrows(IllegalArgumentException.class,
                () -> h.display().bindAct(h.profile(), new byte[0xA800], h.art()));
        assertArrayEquals(expected, h.display().current().ram().bytes());
        assertEquals(448, h.display().current().horizontalScrollWords().size());
        assertEquals(64, h.display().current().normalPaletteWords().size());
    }

    @Test void destructiveCompositeRestoreReplaysTheParkedContinuationAndSameArtAuthority() throws Exception {
        var h = harness(); var registry = new RewindRegistry();
        h.art().registerRewindAdapters(registry); registry.register(h.display());
        h.display().buildAfterObjects(input(1, false), HintState.PENDING);
        var saved = registry.capture();
        var upload = h.display().prepareHInterruptUpload();
        h.art().publishCpuWordWrites(List.of(upload.write()));
        var expected = h.display().resumePlayer2AfterHintClear(upload, HintState.CLEAR);
        var expectedArt = h.art().capture();
        var otherProfile = Sonic2CompetitionLevelProfile.load(TestEnvironment.currentRom(), 12, 1);
        h.display().bindAct(otherProfile, Sonic2CompetitionNativeArt.load(TestEnvironment.currentRom(), otherProfile).workRamBytes(),
                new Sonic2InterlaceVramService(TestEnvironment.currentRom(), new Sonic2PlcService(TestEnvironment.currentRom())));
        h.art().resetForMissingSnapshot();
        registry.restore(saved);
        upload = h.display().prepareHInterruptUpload();
        h.art().publishCpuWordWrites(List.of(upload.write()));
        var replay = h.display().resumePlayer2AfterHintClear(upload, HintState.CLEAR);
        assertEquals(expected, replay); assertEquals(expectedArt, h.art().capture());
        h.display().resetForMissingSnapshot();
        assertThrows(IllegalStateException.class, h.display()::current);
    }

    @Test void unavailableInputsMalformedRomAndStalePlansFailWithoutPartialCpuPublication() throws Exception {
        var h = harness(); var initial = h.display().capture();
        assertThrows(IllegalArgumentException.class, () -> h.display().buildAfterObjects(input(1, false), HintState.UNKNOWN));
        assertThrows(IllegalArgumentException.class, () -> h.display().buildAfterObjects(input(1, false), null));
        var good = input(1, false);
        assertThrows(NullPointerException.class, () -> new Build(good.firstCamera(), good.secondCamera(), good.hud(),
                null, good.secondRings(), 0, good.admissions(), false), "B unknown cannot be an invented exact RingWindow");
        var badObject = new ObjectSlot(1, 4, 100, 100, 32, 32, new Mapping(0xFFFFFE, 1), 0x42C0, null);
        var bad = new Build(good.firstCamera(), good.secondCamera(), good.hud(), good.firstRings(), good.secondRings(),
                0, List.of(new Admission(16, 0, 0, badObject)), false);
        assertThrows(IndexOutOfBoundsException.class, () -> h.display().buildAfterObjects(bad, HintState.CLEAR));
        assertEquals(initial, h.display().capture());
        h.display().buildAfterObjects(good, HintState.PENDING);
        var parked = h.display().capture();
        assertThrows(IllegalArgumentException.class, () -> new Sonic2InterlaceCpuDisplay.Snapshot(parked.profile(),
                parked.art(), initial.ram(), parked.first(), null, null, HintState.CLEAR));
        assertEquals(parked, h.display().capture());
        var plan = h.display().prepareHInterruptUpload();
        h.display().bindAct(h.profile(), h.seed(), h.art());
        h.display().buildAfterObjects(input(2, false), HintState.PENDING);
        var newer = h.display().capture();
        assertThrows(IllegalStateException.class, () -> h.display().resumePlayer2AfterHintClear(plan, HintState.CLEAR));
        assertEquals(newer, h.display().capture());
    }

    private static Build input(int frame, boolean teleport) {
        var camera = new Camera(0, 0);
        var hud = new Hud(true, 8, new PlayerHud(12, 1, 23, 1), new PlayerHud(99, 2, 45, 1), 0);
        var rings = new RingWindow(List.of(new Ring(1, 0, 120, 80)));
        var object = new ObjectSlot(0x41, 4, 100, 100, 32, 32,
                new Mapping(Sonic2Constants.MAP_UNC_SPRING_ADDR, frame), 0x42C0, null);
        return new Build(camera, camera, hud, rings, rings, 0, List.of(new Admission(16, 2, 0, object)), teleport);
    }
    private static Harness harness() throws Exception {
        var rom = TestEnvironment.currentRom(); var profile = Sonic2CompetitionLevelProfile.load(rom, 0, 0);
        var nativeArt = Sonic2CompetitionNativeArt.load(rom, profile);
        var art = new Sonic2InterlaceVramService(rom, new Sonic2PlcService(rom)); art.loadZoneArt(profile);
        var display = new Sonic2InterlaceCpuDisplay(rom, 0x1D8000, 0x1000);
        byte[] seed = nativeArt.workRamBytes(); display.bindAct(profile, seed, art);
        return new Harness(profile, seed, art, display);
    }
    private record Harness(Sonic2CompetitionLevelProfile profile, byte[] seed, Sonic2InterlaceVramService art,
                           Sonic2InterlaceCpuDisplay display) { }
    private static int read(byte[] bytes, int at) { return (bytes[at] & 255) * 256 + (bytes[at + 1] & 255); }
    private static void put(byte[] bytes, int at, int word) { bytes[at] = (byte) (word >>> 8); bytes[at + 1] = (byte) word; }
}
