package com.openggf.game.sonic3k.scroll;

import com.openggf.data.Rom;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotBonusStageRuntime;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.scroll.ZoneScrollHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.openggf.level.scroll.M68KMath.unpackBG;
import static com.openggf.level.scroll.M68KMath.unpackFG;

public class SwScrlSlotsTest {
    @Test
    public void restoredScrollRebuildDoesNotAdvanceNativeBackgroundOrPlaneUpdates() {
        var scroll=new SwScrlSlots(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());var output=new int[224];
        scroll.updateForTest(null,output,0x300,0x200,0);
        for(int frame=1;frame<=11;frame++)scroll.updateForTest(null,output,0x300,0x200,frame);
        assertEquals(1,scroll.getVscrollFactorBG()&255);
        var before=scroll.captureRewindState();var expected=output.clone();
        var beforeUpdates=List.copyOf(scroll.lastBgPlaneRowUpdatesForTest());
        scroll.updateForTest(null,output,0x300,0x200,12);
        var after=scroll.captureRewindState();var afterOutput=output.clone();
        for(int cycle=0;cycle<2;cycle++) {
            scroll.restoreRewindState(before);
            scroll.rebuildOutput(output,0x300,0x200,11,0);
            org.junit.jupiter.api.Assertions.assertArrayEquals(expected,output);
            assertEquals(1,scroll.getVscrollFactorBG()&255);
            assertEquals(beforeUpdates,scroll.lastBgPlaneRowUpdatesForTest());
            org.junit.jupiter.api.Assertions.assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.keyEquals("slot-scroll",before,scroll.captureRewindState()));
            scroll.updateForTest(null,output,0x300,0x200,12);
            org.junit.jupiter.api.Assertions.assertArrayEquals(afterOutput,output);
            org.junit.jupiter.api.Assertions.assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.keyEquals("slot-scroll",after,scroll.captureRewindState()));
            scroll.restoreRewindState(before);scroll.restoreRewindState(after);
            scroll.rebuildOutput(output,0x300,0x200,12,0);
            org.junit.jupiter.api.Assertions.assertArrayEquals(afterOutput,output);
        }
    }


    @Test
    public void alteredRomBandWordsDriveSignedArithmeticPlaneRequestsAndDeformation() {
        var bytes=com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.image();
        putWord(bytes,0x599BE,0x60);
        putWord(bytes,0x599DE,0x18);
        putWord(bytes,0x599E0,0xFFFF);putWord(bytes,0x599E2,0);
        putWord(bytes,0x599E8,0xE020);putWord(bytes,0x599EA,-4);
        putWord(bytes,0x599EC,0);putWord(bytes,0x599EE,0x1230);putWord(bytes,0x599F0,7);
        var bank=com.openggf.game.sonic3k.bonusstage.slots.S3kSlotResources.fromRom(
                com.openggf.data.RomByteReader.fromBytes(bytes));
        assertEquals(-0x10000,bank.backgroundBands().getFirst().deltaLong());
        var scroll=new SwScrlSlots(bank);var output=new int[224];
        scroll.updateForTest(null,output,0x300,0x200,0);
        scroll.updateForTest(null,output,0x300,0x200,1);
        assertEquals(25,unpackBG(output[0]));assertEquals(25,unpackBG(output[95]));
        assertEquals(0,unpackBG(output[96]),"the declared height selects the next ROM band");
        assertEquals(List.of(new SwScrlSlots.BgPlaneRowUpdate(0x18,0x1230,0xE01C,7)),
                scroll.lastBgPlaneRowUpdatesForTest());
        scroll.updateForTest(null,output,0x300,0x200,2);
        assertEquals(2,unpackBG(output[0]),"unsigned 32-bit carry precedes the native word-sized step");
    }

    @Test
    public void malformedBackgroundTablesFailBeforeScrollChanges() {
        var bytes=com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.image();
        putWord(bytes,0x599BE,0);var zeroHeight=bytes;
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->
                com.openggf.game.sonic3k.bonusstage.slots.S3kSlotResources.fromRom(com.openggf.data.RomByteReader.fromBytes(zeroHeight)));
        bytes=com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.image();
        putWord(bytes,0x599BE+30,32);var missingSentinel=bytes;
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->
                com.openggf.game.sonic3k.bonusstage.slots.S3kSlotResources.fromRom(com.openggf.data.RomByteReader.fromBytes(missingSentinel)));
    }

    private static void putWord(byte[] bytes,int address,int value) {
        bytes[address]=(byte)(value>>>8);bytes[address+1]=(byte)value;
    }

    @Test
    public void providerRoutesSlotsToDedicatedHandler() throws Exception {
        Sonic3kScrollHandlerProvider provider = new Sonic3kScrollHandlerProvider();
        provider.load(new Rom());

        ZoneScrollHandler handler = provider.getHandler(Sonic3kZoneIds.ZONE_SLOT_MACHINE);
        assertNotNull(handler);
        assertTrue(handler instanceof SwScrlSlots);
        assertNotSame(provider.getHandler(Sonic3kZoneConstants.ZONE_MGZ), handler);
    }

    @Test
    public void slotScrollUsesRuntimeAnchorsInsteadOfCameraOnlyApproximation() {
        SwScrlSlots scroll = new SwScrlSlots(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        runtime.bootstrap();
        var visual = runtime.slotVisualState();

        scroll.updateForTest(runtime, 0x300, 0x200, 12);

        assertEquals((0x400 - visual.eventsBgX()) + 0x300, scroll.lastForegroundOriginXForTest());
        assertEquals((0x400 - visual.eventsBgY()) + 0x200, scroll.lastForegroundOriginYForTest());
        assertEquals(scroll.lastForegroundOriginYForTest(), scroll.getVscrollFactorFG());
        assertEquals(0, scroll.lastBackgroundOriginYForTest());
        int[] horizScrollBuf = new int[224];
        scroll.updateForTest(runtime, horizScrollBuf, 0x300, 0x200, 12);
        assertEquals(0, unpackBG(horizScrollBuf[0]));
        assertEquals(0, unpackBG(horizScrollBuf[223]));
    }

    @Test
    public void slotBackgroundInitClearsDeformationBeforeEventTick() {
        SwScrlSlots scroll = new SwScrlSlots(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        runtime.bootstrap();
        int[] horizScrollBuf = new int[224];

        scroll.updateForTest(runtime, horizScrollBuf, 0x300, 0x200, 0);

        for (int line = 0; line < horizScrollBuf.length; line++) {
            assertEquals(0, unpackBG(horizScrollBuf[line]), "BG scroll should be zero on init line " + line);
        }
        assertEquals(0, scroll.backgroundVelocityForTest());
        assertEquals(0, scroll.getVscrollFactorBG() & 0xFF);
    }

    @Test
    public void slotBackgroundUsesAccumulatorHighWordPlusBaseScroll() {
        SwScrlSlots scroll = new SwScrlSlots(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        runtime.bootstrap();
        int[] horizScrollBuf = new int[224];

        scroll.updateForTest(runtime, horizScrollBuf, 0x300, 0x200, 0); // Slots_BackgroundInit
        scroll.updateForTest(runtime, horizScrollBuf, 0x300, 0x200, 1); // first Slots_BackgroundEvent
        assertEquals(0, unpackBG(horizScrollBuf[0]));
        assertEquals(-0x40, unpackBG(horizScrollBuf[96]));

        scroll.updateForTest(runtime, horizScrollBuf, 0x300, 0x200, 2); // accumulator high word becomes visible

        assertEquals(-1, unpackBG(horizScrollBuf[0]));
        assertEquals(-1, unpackBG(horizScrollBuf[95]));
        assertEquals(-0x41, unpackBG(horizScrollBuf[96]));
        assertEquals(-0x42, unpackBG(horizScrollBuf[175]));
        assertEquals(-1, unpackBG(horizScrollBuf[176]));
    }

    @Test
    public void slotBackgroundVScrollUsesRomFixedPointAccumulatorHighWord() {
        SwScrlSlots scroll = new SwScrlSlots(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        runtime.bootstrap();
        int[] horizScrollBuf = new int[224];

        scroll.updateForTest(runtime, horizScrollBuf, 0x300, 0x200, 0); // Slots_BackgroundInit
        for (int frame = 1; frame <= 11; frame++) {
            scroll.updateForTest(runtime, horizScrollBuf, 0x300, 0x200, frame);
        }

        assertEquals(1, scroll.lastBackgroundOriginYForTest());
        assertEquals(1, scroll.getVscrollFactorBG() & 0xFF);
    }

    @Test
    public void slotBackgroundQueuesRomPlaneRowRefreshesWhenBandsAdvance() {
        SwScrlSlots scroll = new SwScrlSlots(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        runtime.bootstrap();
        int[] horizScrollBuf = new int[224];

        scroll.updateForTest(runtime, horizScrollBuf, 0x300, 0x200, 0); // Slots_BackgroundInit
        assertTrue(scroll.lastBgPlaneRowUpdatesForTest().isEmpty());

        scroll.updateForTest(runtime, horizScrollBuf, 0x300, 0x200, 1); // first Slots_BackgroundEvent

        List<SwScrlSlots.BgPlaneRowUpdate> updates = scroll.lastBgPlaneRowUpdatesForTest();
        assertEquals(2, updates.size());
        assertEquals(0x48, updates.get(0).sourceX());
        assertEquals(0x0000, updates.get(0).sourceY());
        assertEquals(0xDFFE, updates.get(0).destVramAddress() & 0xFFFF);
        assertEquals(0x19, updates.get(0).longWordCount());
        assertEquals(0x48, updates.get(1).sourceX());
        assertEquals(0x0010, updates.get(1).sourceY());
        assertEquals(0xE0FE, updates.get(1).destVramAddress() & 0xFFFF);
        assertEquals(0x19, updates.get(1).longWordCount());
    }

    @Test
    public void slotScrollOwnsBackgroundAccumulatorAndRespondsToScalarDirectionChanges() {
        SwScrlSlots scroll = new SwScrlSlots(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        runtime.bootstrap();

        for (int i = 0; i < 40; i++) {
            scroll.updateForTest(runtime, 0x300, 0x200, i);
        }

        int positiveVelocity = scroll.backgroundVelocityForTest();

        runtime.stageController().setScalarIndex(-0x40);
        scroll.updateForTest(runtime, 0x300, 0x200, 40);
        int postFlipVelocity = scroll.backgroundVelocityForTest();

        for (int i = 41; i < 80; i++) {
            scroll.updateForTest(runtime, 0x300, 0x200, i);
        }

        assertTrue(positiveVelocity >= 0x7C00, "positiveVelocity=" + positiveVelocity);
        assertTrue(positiveVelocity <= 0x8000, "positiveVelocity=" + positiveVelocity);
        assertTrue(postFlipVelocity < positiveVelocity, "positiveVelocity=" + positiveVelocity + " postFlipVelocity=" + postFlipVelocity);
        assertTrue(scroll.backgroundVelocityForTest() < 0, "finalVelocity=" + scroll.backgroundVelocityForTest());
        assertEquals(0x12, scroll.getVscrollFactorBG() & 0xFF);
    }

    @Test
    public void slotBackgroundFreezesWhenDebugForcesRotationScalarToZero() {
        SwScrlSlots scroll = new SwScrlSlots(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        runtime.bootstrap();

        for (int frame = 0; frame < 20; frame++) {
            scroll.updateForTest(runtime, 0x300, 0x200, frame);
        }

        int velocityBeforeDebug = scroll.backgroundVelocityForTest();
        int originBeforeDebug = scroll.lastBackgroundOriginYForTest();
        runtime.stageController().setScalarIndex(0);

        for (int frame = 20; frame < 40; frame++) {
            scroll.updateForTest(runtime, 0x300, 0x200, frame);
        }

        assertEquals(velocityBeforeDebug, scroll.backgroundVelocityForTest());
        assertEquals(originBeforeDebug, scroll.lastBackgroundOriginYForTest());
    }

    @Test
    public void slotScrollProducesStablePackedBufferWithoutBandCorruption() {
        SwScrlSlots scroll = new SwScrlSlots(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        runtime.bootstrap();
        int[] horizScrollBuf = new int[224];

        scroll.updateForTest(runtime, horizScrollBuf, 0x300, 0x200, 12);

        short expectedFg = unpackFG(horizScrollBuf[0]);
        for (int packed : horizScrollBuf) {
            assertEquals(expectedFg, unpackFG(packed));
        }

        assertEquals(horizScrollBuf[0], horizScrollBuf[95]);
        assertEquals(horizScrollBuf[96], horizScrollBuf[175]);
        assertEquals(horizScrollBuf[176], horizScrollBuf[223]);
        assertEquals(unpackBG(horizScrollBuf[95]), unpackBG(horizScrollBuf[176]));
    }
}


