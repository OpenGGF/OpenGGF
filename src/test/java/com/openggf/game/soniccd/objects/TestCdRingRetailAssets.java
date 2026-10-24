package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual authenticated input binding; no preload/residency/world or Mosqui execution claim. UNRUN. */
class TestCdRingRetailAssets {
    @Test
    void actualUsaTablesExpandOpeningParentsAndExecuteLaterChildrenInPhysicalOrder() throws Exception {
        var assets = load(); var objects = assets.openingObjects();
        var runtime = CdObjectPlacementRuntime.fromAssets(assets);
        var ring = CdRing.fromAssets(runtime, objects); var ports = new TestCdRing.Ports(runtime);
        assertEquals(Optional.of(objects.identity()), ring.sourceIdentity());
        assertSame(objects.rings(), ring.sourceData().visuals());
        assertEquals(objects.initialArtBank().binding(CdR11aAsset.ART_RINGS), ring.artBinding().orElseThrow());
        assertEquals(0xA7AE, ring.sourceData().baseTileWord());
        assertEquals(0x20A0FA, ring.sourceData().mappingAddress());
        assertEquals(TestCdRing.data().deltas(), ring.sourceData().deltas());
        assertArrayEquals(TestCdRing.data().visuals().animationTable().copy(), objects.rings().animationTable().copy());
        assertArrayEquals(TestCdRing.data().visuals().mappings().copy(), objects.rings().mappings().copy());
        runtime.spawnObjects(0, 1);
        assertEquals(0x10, runtime.pool().state(32).id()); assertEquals(0x10, runtime.pool().state(33).id());
        assertEquals(0x3D, runtime.pool().state(34).id());
        var before = runtime.capture();
        for (int whole = 32; whole < 128; whole++) {
            if (runtime.pool().state(whole).id() == 0x10) { ring.executeSelected(whole, TestCdRing.input(), ports, ports); }
        }
        assertEquals(java.util.List.of(32, 33, 35, 36, 37), ports.drawn);
        assertEquals(0x110, runtime.pool().state(35).centreX());
        assertEquals(0x210, runtime.pool().state(36).centreX()); assertEquals(0x230, runtime.pool().state(37).centreX());
        assertEquals(before.pool().slots().get(2), runtime.pool().state(34), "Mosqui is outside this unit");
        assertEquals(0, ports.value.rings()); assertTrue(ports.sounds.isEmpty());
        var expanded = runtime.capture();
        runtime.pool().write(runtime.pool().state(35).moveByte(ROUTINE, 4)); // Caller-owned touch request, not a scan claim.
        ring.executeSelected(35, TestCdRing.input(), ports, ports);
        assertEquals(1, ports.value.rings()); assertEquals(2, runtime.savedFlag(3, 1));
        assertEquals(4, runtime.pool().state(35).unsignedByte(MAP_FRAME));
        assertEquals(java.util.List.of("FM_RING"), ports.sounds);
        runtime.restore(expanded);
        assertEquals(2, runtime.pool().state(35).routine()); assertEquals(0, runtime.savedFlag(3, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdRing(runtime, TestCdRing.data()),
                "Constructed data cannot bind an authenticated placement owner");
    }

    @Test
    void productionFactoryRejectsConstructedPlacementInsteadOfLendingItAnAssetIdentity() throws Exception {
        var objects = load().openingObjects();
        var constructed = TestCdRing.runtime();
        assertThrows(IllegalArgumentException.class, () -> CdRing.fromAssets(constructed, objects));
        assertTrue(constructed.sourceIdentity().isEmpty());
        assertEquals(0, constructed.pool().state(32).id());
    }

    private static CdR11aAssets load() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for actual USA asset checks");
        Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured media must have exactly one actual USA CUE");
            cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            return CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        }
    }
}
