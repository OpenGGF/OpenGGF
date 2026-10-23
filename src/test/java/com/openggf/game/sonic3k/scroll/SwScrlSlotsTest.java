package com.openggf.game.sonic3k.scroll;

import com.openggf.data.Rom;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotBonusStageRuntime;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.scroll.ZoneScrollHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    public void backgroundEventQueuesRowsWithoutCommittingVisiblePlaneB() {
        var scroll = new SwScrlSlots(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        var level = org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
        org.mockito.Mockito.when(level.getCurrentZone()).thenReturn(Sonic3kZoneIds.ZONE_SLOT_MACHINE);
        try (var services = org.mockito.Mockito.mockStatic(com.openggf.game.GameServices.class)) {
            services.when(com.openggf.game.GameServices::level).thenReturn(level);
            scroll.updateForTest(null, new int[224], 0x300, 0x200, 0);
            scroll.updateForTest(null, new int[224], 0x300, 0x200, 1);
            assertEquals(2, scroll.lastBgPlaneRowUpdatesForTest().size(), "sub_4ECAA prepares two ROM rows");
            org.mockito.Mockito.verify(level, org.mockito.Mockito.never()).copyBackgroundTileRowFromWorldToVdpPlane(
                    org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(com.openggf.game.resources.PlcLifecyclePhase.class)
    void onlyNativeDrawLevelPhasesPublishPreparedRows(com.openggf.game.resources.PlcLifecyclePhase phase) {
        var scroll = new SwScrlSlots(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        var level = org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
        var objects = org.mockito.Mockito.mock(com.openggf.level.objects.ObjectManager.class);
        var services = org.mockito.Mockito.mock(com.openggf.level.objects.ObjectServices.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        var art = org.mockito.Mockito.mock(com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        org.mockito.Mockito.when(art.vintDmaLedger().romBound()).thenReturn(true);
        org.mockito.Mockito.when(level.captureBackgroundVdpPlane()).thenReturn(new byte[64 * 32 * 4]);
        org.mockito.Mockito.when(level.getObjectManager()).thenReturn(objects);
        org.mockito.Mockito.when(objects.getObjectServices()).thenReturn(services);
        org.mockito.Mockito.when(services.parallaxManager().getHandler(org.mockito.ArgumentMatchers.anyInt())).thenReturn(scroll);
        org.mockito.Mockito.when(level.copyBackgroundTileRowFromWorldToVdpPlane(
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        scroll.updateForTest(null, new int[224], 0x300, 0x200, 0);
        scroll.updateForTest(null, new int[224], 0x300, 0x200, 1);
        var profile = new com.openggf.game.sonic3k.Sonic3kLevelInitProfile(null);
        // An explicit DMA-only lag declaration is not VInt_DrawLevel authority.
        profile.serviceQueuedPatternDma(phase, phase == com.openggf.game.resources.PlcLifecyclePhase.LAG, art, level);
        boolean draws = switch (phase) {
            case ORDINARY_LEVEL, CREDITS_DEMO, NORMAL_PAUSE -> true;
            default -> false;
        };
        org.mockito.Mockito.verify(level, org.mockito.Mockito.times(draws ? 2 : 0)).copyBackgroundTileRowFromWorldToVdpPlane(
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        if (draws) {
            org.mockito.Mockito.verify(level).copyBackgroundTileRowFromWorldToVdpPlane(0x48, 0, 0xDFFE, 0x19);
            org.mockito.Mockito.verify(level).copyBackgroundTileRowFromWorldToVdpPlane(0x48, 0x10, 0xE0FE, 0x19);
            var order = org.mockito.Mockito.inOrder(art, level);
            order.verify(art).publishPatternModuleDma();
            order.verify(level).copyBackgroundTileRowFromWorldToVdpPlane(0x48, 0, 0xDFFE, 0x19);
            order.verify(level).copyBackgroundTileRowFromWorldToVdpPlane(0x48, 0x10, 0xE0FE, 0x19);
            order.verify(level).uploadBackgroundTilemap();
            profile.serviceQueuedPatternDma(phase, false, art, level);
            org.mockito.Mockito.verify(level, org.mockito.Mockito.times(2)).copyBackgroundTileRowFromWorldToVdpPlane(
                    org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        }
    }

    @Test
    void onePhysicalVBlankTokenCannotConsumeWorkPreparedAfterItsPublication() {
        var scroll = new SwScrlSlots(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        var level = org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
        scroll.updateForTest(null, new int[224], 0x300, 0x200, 0);
        scroll.updateForTest(null, new int[224], 0x300, 0x200, 1);
        var coordinator = new com.openggf.game.resources.PlcFrameLifecycleCoordinator(
                (com.openggf.game.resources.PlcLifecycleService) null);
        var token = coordinator.latchBeforeFadeUpdate();
        assertTrue(token.claim(com.openggf.game.resources.PlcLifecyclePhase.ORDINARY_LEVEL));
        assertTrue(token.dispatchGameVBlank(() -> scroll.publishBackgroundPlaneRows(level)));
        scroll.updateForTest(null, new int[224], 0x300, 0x200, 2);
        var pending = scroll.captureRewindState();
        assertTrue(!scroll.lastBgPlaneRowUpdatesForTest().isEmpty(), "a new event really prepared more rows");
        org.junit.jupiter.api.Assertions.assertFalse(token.dispatchGameVBlank(() -> scroll.publishBackgroundPlaneRows(level)));
        assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.keyEquals("slot-scroll", pending, scroll.captureRewindState()));
        org.mockito.Mockito.verify(level, org.mockito.Mockito.times(2)).copyBackgroundTileRowFromWorldToVdpPlane(
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        token.finish();
    }

    @Test
    void immutablePublishedPlaneIsReusedUntilARealWriteOrOwnerReplacement() throws Exception {
        var scroll = new SwScrlSlots(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        var level = org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
        var parallax = org.mockito.Mockito.mock(com.openggf.level.ParallaxManager.class);
        var firstLevel = org.mockito.Mockito.mock(com.openggf.level.Level.class);
        org.mockito.Mockito.when(level.getCurrentLevel()).thenReturn(firstLevel);
        org.mockito.Mockito.when(parallax.getHandler(org.mockito.ArgumentMatchers.anyInt())).thenReturn(scroll);
        byte[] initial = new byte[64 * 32 * 4];initial[0] = 7;
        byte[] changed = initial.clone();changed[0] = 9;
        org.mockito.Mockito.when(level.captureBackgroundVdpPlane()).thenReturn(initial, changed, initial);
        org.mockito.Mockito.when(level.copyBackgroundTileRowFromWorldToVdpPlane(
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        try (var services = org.mockito.Mockito.mockStatic(com.openggf.game.GameServices.class)) {
            services.when(com.openggf.game.GameServices::levelOrNull).thenReturn(level);
            services.when(com.openggf.game.GameServices::parallaxOrNull).thenReturn(parallax);
            scroll.updateForTest(null, new int[224], 0x300, 0x200, 0);
            Object first = publishedValue(scroll.captureRewindState());
            initial[0] = 3;
            assertEquals(7, publishedBytes(first)[0], "constructor owns its bytes");
            byte[] returned = publishedBytes(first);returned[0] = 99;
            assertEquals(7, publishedBytes(first)[0], "accessor is defensive");
            assertSame(first, publishedValue(scroll.captureRewindState()));
            scroll.rebuildOutput(new int[224], 0x300, 0x200, 0, 0);
            scroll.updateForTest(null, new int[224], 0x300, 0x200, 1);
            assertSame(first, publishedValue(scroll.captureRewindState()), "pending rows do not replace published bytes");
            org.mockito.Mockito.verify(level).captureBackgroundVdpPlane();
            scroll.publishBackgroundPlaneRows(level);
            Object next = publishedValue(scroll.captureRewindState());
            assertNotSame(first, next);assertEquals(9, publishedBytes(next)[0]);
            assertSame(next, publishedValue(scroll.captureRewindState()));
            org.mockito.Mockito.when(level.getCurrentLevel()).thenReturn(org.mockito.Mockito.mock(com.openggf.level.Level.class));
            Object reloaded = publishedValue(scroll.captureRewindState());
            assertNotSame(next, reloaded, "replacement level identity invalidates the old plane");
            org.mockito.Mockito.verify(level, org.mockito.Mockito.times(3)).captureBackgroundVdpPlane();
        }
    }

    private static Object publishedValue(Object state) throws Exception {
        var accessor = state.getClass().getDeclaredMethod("publishedPlane");accessor.setAccessible(true);
        return accessor.invoke(state);
    }
    private static byte[] publishedBytes(Object value) throws Exception {
        var accessor = value.getClass().getDeclaredMethod("bytes");accessor.setAccessible(true);
        return (byte[]) accessor.invoke(value);
    }

    @Test
    void pendingRowsSurviveRestoreAndRebuildUntilExplicitPublication() {
        var scroll = new SwScrlSlots(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources());
        var level = org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
        int[] output = new int[224];
        scroll.updateForTest(null, output, 0x300, 0x200, 0);
        var before = scroll.captureRewindState();
        scroll.updateForTest(null, output, 0x300, 0x200, 1);
        var pending = scroll.captureRewindState();
        scroll.publishBackgroundPlaneRows(level);
        var published = scroll.captureRewindState();
        for (int cycle = 0; cycle < 2; cycle++) {
            scroll.restoreRewindState(before);
            scroll.updateForTest(null, output, 0x300, 0x200, 1);
            assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.keyEquals("slot-scroll", pending, scroll.captureRewindState()));
            scroll.restoreRewindState(pending);
            scroll.rebuildOutput(output, 0x300, 0x200, 1, 0);
            scroll.rebuildOutput(output, 0x300, 0x200, 1, 0);
            assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.keyEquals("slot-scroll", pending, scroll.captureRewindState()));
            scroll.publishBackgroundPlaneRows(level);
            assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.keyEquals("slot-scroll", published, scroll.captureRewindState()));
            scroll.publishBackgroundPlaneRows(level);
            assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.keyEquals("slot-scroll", published, scroll.captureRewindState()));
        }
    }

    @Test
    public void zeroRotationScalarRunsThePositiveBackgroundStepAndBandLoop() {
        // Slots_BackgroundEvent: tst.w scalar / bpl selects +$400 even at zero.
        var resources = com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData.resources();
        var scroll = new SwScrlSlots(resources);
        var runtime = new S3kSlotBonusStageRuntime(resources);
        runtime.bootstrap();
        runtime.stageController().setScalarIndex(0);
        int[] output = new int[224];
        scroll.updateForTest(runtime, output, 0x300, 0x200, 0); // BackgroundInit
        scroll.updateForTest(runtime, output, 0x300, 0x200, 1); // BackgroundEvent
        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertEquals(0x400, scroll.backgroundVelocityForTest(), "zero takes the positive step"),
                () -> assertEquals(-0x40, unpackBG(output[96]), "the ROM band loop still runs"),
                () -> assertEquals(2, scroll.lastBgPlaneRowUpdatesForTest().size(), "the event queues its ROM rows"));
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


