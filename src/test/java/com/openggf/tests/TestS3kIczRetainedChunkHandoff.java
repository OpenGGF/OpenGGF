package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingJob;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.level.Chunk;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.ResourceLoader;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Independent ICZ Load_Level/LoadSolids component inlet; no movie or trace state. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kIczRetainedChunkHandoff {
    @Test
    void synchronousHandoffRetainsKnownPatternsWithTargetCollisionUntilPublication() throws Exception {
        var fixture = enterTransition();
        var rom = GameServices.rom().getRom();
        var loader = new ResourceLoader(rom);
        int llb = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR;
        int stride = Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE;
        int previousSource = rom.read32BitAddr(llb + 10 * stride + 12) & 0xFFFFFF;
        int targetSource = rom.read32BitAddr(llb + 11 * stride + 12) & 0xFFFFFF;
        assertEquals(0x3D5958, previousSource);
        assertEquals(0x3DA26A, targetSource);
        byte[] previous = loader.loadSingle(LoadOp.kosinskiBase(previousSource));
        byte[] target = loader.loadSingle(LoadOp.kosinskiBase(targetSource));
        assertEquals(559 * 8, previous.length);
        assertEquals(589 * 8, target.length);
        assertArrayEquals(new int[]{0xC131, 0xC132, 0xC133, 0xC134}, words(previous, 0));
        assertArrayEquals(words(previous, 0), patterns(current(), 129));
        assertEquals(0xFF, current().getChunk(129).getSolidTileIndex());
        assertEquals(0xFF, current().getChunk(129).getSolidTileAltIndex());
        int targetSolids = rom.read32BitAddr(Sonic3kConstants.SOLID_INDEXES_ADDR + 11 * 4)
                & 0xFFFFFF;
        assertEquals(0x27A761, targetSolids);
        assertEquals(8, rom.readByte(targetSolids + 129 * 2) & 0xFF);
        assertEquals(8, rom.readByte(targetSolids + 0x600 + 129 * 2) & 0xFF);

        var manager = (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        HardwareBoundaryPump.service(HardwareServiceBoundary.POST_OBJECTS);
        int dispatch = 0;
        while (S3kRuntimeArtCoordinator.current().directQueue().decompressionsPending()
                && dispatch++ < 100_000) {
            HardwareBoundaryPump.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            manager.update();
            if (S3kRuntimeArtCoordinator.current().directQueue().decompressionsPending()) {
                assertEquals(0, GameServices.level().getCurrentAct());
                HardwareBoundaryPump.service(HardwareServiceBoundary.POST_OBJECTS);
            }
        }
        assertEquals(1, GameServices.level().getCurrentAct());
        // skdisasm044fa467 ICZ1BGE_Transition -> Load_Level copies layout only;
        // LoadSolids selects target collision tables while Block_table stays resident.
        assertRetained(previous, targetSolids);
        assertFalse(parent().claimed());
        // Capture only the new ICZ2 timeline, never retired Act1 history.
        GameServices.level().getObjectManager().setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var beforePublication = registry.capture();
        publishAndAssert(previous, target, targetSolids);
        var afterPublication = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            registry.restore(beforePublication);
            assertWorld(beforePublication, registry.capture());
            assertRetained(previous, targetSolids);
            publishAndAssert(previous, target, targetSolids);
            assertWorld(afterPublication, registry.capture());
        }
    }

    private static void publishAndAssert(byte[] previous, byte[] target, int targetSolids) throws Exception {
        var manager = (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        var rom = GameServices.rom().getRom();
        int passes = 0;
        do {
            HardwareBoundaryPump.service(HardwareServiceBoundary.POST_OBJECTS);
            assertFalse(parent().claimed(), "POST readiness is not payload publication");
            assertRetained(previous, targetSolids);
            if (!parent().ready()) {
                HardwareBoundaryPump.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
                assertRetained(previous, targetSolids);
                if (parent().ready()) break;
                manager.update();
                assertRetained(previous, targetSolids);
            }
        } while (!parent().ready() && passes++ < 100_000);
        assertTrue(parent().ready());
        manager.update();
        assertTrue(parent().claimed());
        assertEquals(718, current().getChunkCount(),
                "publication installs all target definitions, including previously unknown688..717");
        for (int i = 0; i < target.length / 8; i++) {
            int index = 129 + i;
            assertArrayEquals(words(target, i), patterns(current(), index), "published entry " + index);
            assertEquals(rom.readByte(targetSolids + index * 2) & 0xFF,
                    current().getChunk(index).getSolidTileIndex(), "target primary " + index);
            assertEquals(rom.readByte(targetSolids + 0x600 + index * 2) & 0xFF,
                    current().getChunk(index).getSolidTileAltIndex(), "target secondary " + index);
        }
    }

    private static void assertRetained(byte[] previous, int targetSolids) throws Exception {
        var level = current();
        assertEquals(688, level.getChunkCount(),
                "thirty unknown target entries must not become fabricated resident zeros");
        for (int i = 0; i < previous.length / 8; i++) {
            int index = 129 + i;
            assertArrayEquals(words(previous, i), patterns(level, index), "retained entry " + index);
            assertEquals(GameServices.rom().getRom().readByte(targetSolids + index * 2) & 0xFF,
                    level.getChunk(index).getSolidTileIndex(), "current target primary " + index);
            assertEquals(GameServices.rom().getRom().readByte(targetSolids + 0x600 + index * 2) & 0xFF,
                    level.getChunk(index).getSolidTileAltIndex(), "current target secondary " + index);
        }
    }

    private static HeadlessTestFixture enterTransition() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, 0).build();
        // Existing maintained component inlet immediately before ICZ1BGE_Normal.
        fixture.sprite().setCentreX((short) 0x6950);
        fixture.sprite().setCentreY((short) 0x0700);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x6900); camera.setY((short) 0x0600);
        camera.setMinX((short) 0); camera.setMaxX((short) 0x7000);
        camera.setMinY((short) -0x100); camera.setMaxY((short) 0x800);
        camera.setMaxYTarget((short) 0x800);
        var manager = (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        manager.getIczEvents().forceAct1NormalBackgroundRoutineForTest();
        manager.update();
        return fixture;
    }

    private static Sonic3kLevel current() {
        return (Sonic3kLevel) GameServices.level().getCurrentLevel();
    }

    private static HardwareTimingJob.Snapshot parent() {
        return GameServices.hardwareTiming().capture().jobs().stream()
                .filter(job -> job.kind() == HardwareWorkKind.KOS_MODULE_QUEUE).findFirst().orElseThrow();
    }

    private static int[] patterns(Sonic3kLevel level, int index) {
        return Arrays.copyOf(level.getChunk(index).saveState(), 4);
    }

    private static void assertWorld(com.openggf.game.rewind.CompositeSnapshot expected,
                                    com.openggf.game.rewind.CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }

    private static int[] words(byte[] payload, int index) {
        int[] result = new int[4];
        for (int word = 0; word < 4; word++) {
            int offset = index * 8 + word * 2;
            result[word] = (payload[offset] & 0xFF) << 8 | payload[offset + 1] & 0xFF;
        }
        return result;
    }
}
