package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.RuntimeArtAdmissionPolicy;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.snapshot.PlcProgressSnapshot;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.tests.HardwareBoundaryPump;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestLrzEnemyKosOwner {
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void nativeTitleOwnerSubmitsOrderedEnemyBatchOnceAndRewindsItsLifetime(int act) {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(9, act).build();
        var provider = (Sonic3kObjectArtProvider) GameServices.module().getObjectArtProvider();
        var timing = GameServices.hardwareTiming();
        var art = S3kRuntimeArtCoordinator.current();
        var registry = fixture.gameplayMode().getRewindRegistry();
        provider.prepareRuntimeArtForActTransition(9, RuntimeArtAdmissionPolicy.TITLE_OWNER);
        // Offs_LoadEnemyArt and PLCKosM_LRZ select these ROM archives for both acts.
        // LoadEnemyArt (sonic3k.asm:64321-64353), PLCKosM_LRZ (sonic3k.asm:64463-64467).
        assertEquals(List.of(new PlcProgressSnapshot.PendingKosModule(0x16F332, 0x512),
                        new PlcProgressSnapshot.PendingKosModule(0x16F4E4, 0x530),
                        new PlcProgressSnapshot.PendingKosModule(0x16F7E6, 0x562)),
                provider.capture().pendingKosModules());
        assertTrue(timing.pendingHandles().isEmpty(), "preparation is not submission");
        provider.onTitleCardPresentationSkipped();
        // Level's $16 wait, native children, then the lower-slot owner's next dispatch.
        for (int tick = 0; tick < 34; tick++) provider.processRuntimeArtQueue();
        assertTrue(timing.pendingHandles().isEmpty(), "unretired title still owns admission");
        var before = registry.capture();
        provider.processRuntimeArtQueue();
        var handles = timing.pendingHandles().stream()
                .filter(h -> h.kind() == HardwareWorkKind.KOS_MODULE_QUEUE).toList();
        assertEquals(3, handles.size());
        assertEquals(List.of(0x16F332, 0x16F4E4, 0x16F7E6), handles.stream()
                .map(h -> art.moduleQueue().descriptor(h).sourceAddress()).toList());
        assertEquals(List.of(0xA240, 0xA600, 0xAC40), handles.stream()
                .map(h -> art.moduleQueue().descriptor(h).destinationAddress()).toList());
        assertTrue(handles.stream().noneMatch(timing::isReady));
        var submitted = registry.capture();
        for (int i = 0; i < 2; i++) {
            registry.restore(before);
            for (String key : before.entries().keySet()) assertTrue(RewindSnapshotDiff.diffKey(
                    key, before.get(key), registry.capture().get(key)).isEmpty(), key);
            provider.processRuntimeArtQueue();
            assertEquals(handles, timing.pendingHandles(), "restored title submits the same identities once");
            for (int tick = 0; tick < 128 && handles.stream().anyMatch(h -> !timing.isReady(h)); tick++) {
                HardwareBoundaryPump.service(timing, art, HardwareServiceBoundary.POST_OBJECTS);
                HardwareBoundaryPump.service(timing, art, HardwareServiceBoundary.PRE_MAIN_LOOP);
            }
            assertTrue(handles.stream().allMatch(timing::isReady), "real ROM jobs complete through queue service");
            provider.processRuntimeArtQueue();
            assertTrue(timing.pendingHandles().isEmpty(), "provider claims its completed batch");
            assertTrue(provider.capture().pendingKosModules().isEmpty());
            assertTrue(provider.capture().pendingKosOrdinals().isEmpty());
            provider.processRuntimeArtQueue();
            assertTrue(timing.pendingHandles().isEmpty(), "retired owner cannot resubmit");
        }
        registry.restore(submitted);
        provider.processRuntimeArtQueue();
        assertEquals(handles, timing.pendingHandles(), "pending jobs restore without another enqueue");
    }
}
