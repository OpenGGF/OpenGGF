package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.resources.KosinskiModuleQueue;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestFbz2SubbossArtHandoff {
    @Test void defeatKosmQueueOrderAndDestinationsMatchTheRom() {
        var entries = Sonic3kPlcLoader.fbz2SubbossDefeatKosmEntries();
        assertEquals(2, entries.size());
        assertEquals(Sonic3kConstants.ART_KOSM_FBZ_CLOUD_ADDR, entries.get(0).sourceAddress());
        assertEquals(0x3A3 * 32, entries.get(0).destinationVramBytes());
        assertEquals(Sonic3kConstants.ART_KOSM_FBZ_BOSS_PILLAR_ADDR, entries.get(1).sourceAddress());
        assertEquals(0x3D5 * 32, entries.get(1).destinationVramBytes());
    }

    @Test
    void completedModulesApplyOnNativeDmaFramesAndRewindRestoresPatternBytes() throws Exception {
        byte[] vram = new byte[0x10000];
        Arrays.fill(vram, (byte) 0x5A);
        List<KosinskiModuleQueue.DmaChunk> writes = new ArrayList<>();
        KosinskiModuleQueue.DmaTarget target = new KosinskiModuleQueue.DmaTarget() {
            @Override public byte[] read(int destinationVramBytes, int length) {
                return Arrays.copyOfRange(vram, destinationVramBytes, destinationVramBytes + length);
            }
            @Override public void apply(KosinskiModuleQueue.DmaChunk chunk) {
                System.arraycopy(chunk.data(), 0, vram, chunk.destinationVramBytes(), chunk.data().length);
                writes.add(chunk);
            }
        };
        KosinskiModuleQueue queue = new KosinskiModuleQueue();
        queue.bindDmaTarget(target);
        var entries = Sonic3kPlcLoader.fbz2SubbossDefeatKosmEntries();
        for (var entry : entries)
            assertTrue(queue.enqueue(TestEnvironment.currentRom(), entry.sourceAddress(), entry.destinationVramBytes()));
        KosinskiModuleQueue.Snapshot before = queue.capture();

        queue.processNativeFrame();
        assertTrue(writes.isEmpty(), "start phase must not publish VRAM bytes");
        queue.processNativeFrame();
        assertEquals(1, writes.size());
        assertEquals(0x3A3 * 32, writes.get(0).destinationVramBytes());
        assertEquals(0x640, writes.get(0).data().length);
        KosinskiModuleQueue.Snapshot afterCloud = queue.capture();

        queue.processNativeFrame();
        assertEquals(1, writes.size());
        queue.processNativeFrame();
        assertEquals(2, writes.size());
        assertEquals(0x3D5 * 32, writes.get(1).destinationVramBytes());
        assertEquals(0x200, writes.get(1).data().length);

        queue.restore(afterCloud);
        assertNotEquals((byte) 0x5A, vram[0x3A3 * 32]);
        assertEquals((byte) 0x5A, vram[0x3D5 * 32]);
        int writesAfterMidRestore = writes.size();
        queue.processNativeFrame();
        queue.processNativeFrame();
        assertEquals(writesAfterMidRestore + 1, writes.size(), "pillar replays exactly once after rewind");

        queue.restore(before);
        assertEquals((byte) 0x5A, vram[0x3A3 * 32]);
        assertEquals((byte) 0x5A, vram[0x3D5 * 32]);
    }
}
