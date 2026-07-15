package com.openggf.game.sonic3k;

import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.resources.KosinskiModuleQueue;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Pure live-owner capability test: intentionally always-on and ROM-independent. */
class TestSonic3kPlcLiveDmaTarget {
    @Test void rawPlcReportsUnavailablePatternOwnerAsAnObservableZeroLengthPrefix() {
        StubObjectServices services=new StubObjectServices(){
            @Override public Sonic3kLevel currentLevel(){return null;}
        };
        Sonic3kPlcLoader.RawPlcApplyResult result=Sonic3kPlcLoader.applyRawQuietly(
                List.of(new Sonic3kPlcLoader.RawPlcEntry(0x100,0x20)),services);

        assertEquals(1,result.attemptedEntries());
        assertEquals(0,result.appliedEntries());
        assertFalse(result.complete());
        assertNotNull(result.failure());
    }

    @Test void targetRetainsImmediatePayloadAcrossLevelGapAndNeverMutatesStaleLevel() {
        Sonic3kLevel first=mock(Sonic3kLevel.class),second=mock(Sonic3kLevel.class);
        Sonic3kLevel[] current={first};KosinskiModuleQueue queue=new KosinskiModuleQueue();
        StubObjectServices services=new StubObjectServices(){
            @Override public Sonic3kLevel currentLevel(){return current[0];}
            @Override public KosinskiModuleQueue kosinskiModuleQueue(){return queue;}
        };
        Sonic3kPlcLoader.bindRuntimePatternDmaTarget(queue,services);
        byte[] firstPayload=new byte[32];Arrays.fill(firstPayload,(byte)1);
        assertTrue(queue.applyImmediateDma(0x100,firstPayload));
        verify(first).applyPatternOverlay(aryEq(firstPayload),eq(0x100),eq(false));
        current[0]=null;
        byte[] pending=new byte[32];Arrays.fill(pending,(byte)5);
        assertFalse(queue.applyImmediateDma(0x100,pending));
        current[0]=second;
        assertTrue(queue.applyImmediateDma(0x100,pending));
        verify(second).applyPatternOverlay(aryEq(pending),eq(0x100),eq(false));
        verify(first,never()).applyPatternOverlay(aryEq(pending),eq(0x100),eq(false));
    }
}
