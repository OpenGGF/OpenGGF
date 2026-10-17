package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.events.DezFinalScreenEvents;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezFinalScreenEntry {
    @org.junit.jupiter.api.AfterEach void resetViewport() {
        com.openggf.configuration.SonicConfigurationService.getInstance().clearSessionOverrides();
        com.openggf.game.session.SessionManager.clear();
    }
    private HeadlessTestFixture boot() { return boot(320); }
    private HeadlessTestFixture boot(int width) {
        var config = com.openggf.configuration.SonicConfigurationService.getInstance();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.DISPLAY_ASPECT,
                com.openggf.configuration.WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        com.openggf.game.session.SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder().withZoneAndAct(23, 0).build();
    }
    private DezFinalBossZoneRuntimeState state() {
        return (DezFinalBossZoneRuntimeState) GameServices.zoneRuntimeState();
    }
    @Test void coldFirstProductionScreenInitClearsOnlyRomUnusedLayoutTail() throws Exception {
        assertRomOwnedScreenInitLayout(boot(), false);
    }

    @Test void publicNativeContinuationScreenInitClearsOnlyRomUnusedLayoutTail() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(11, 1).build();
        var manager = GameServices.level();
        // Declared native continuation inlet, not a boss defeat or full-route claim.
        com.openggf.level.LevelContinuationCarry.request(manager, 23, 0, 0, 0, null);
        assertTrue(manager.consumeZoneActRequest());
        manager.loadZoneAndActAtFreshTitleCardBoundary(23, 0);
        assertFalse(manager.hasPendingFreshLevelTransitionBoundary());
        // All captures below are AFTER this intentional fresh-load history reset.
        assertRomOwnedScreenInitLayout(fixture, true);
    }

    /**
     * DEZ3_ScreenInit $5A3DA..$5A400 clears unused Level_layout_header bytes,
     * not RAM_start definitions or collision banks. Definition zero is NOT assumed empty.
     * The two forced recreated-world continuations and independent controls deliberately
     * precede the final discriminating clear assertions, so a tail old-red qualifies them.
     */
    private void assertRomOwnedScreenInitLayout(HeadlessTestFixture fixture, boolean continuation)
            throws Exception {
        var rom = TestEnvironment.currentRom();
        assertEquals(0x0A7F4C, rom.read32BitAddr(0x09D5C0 + 46 * 4) & 0xFFFFFF);
        byte[] loaded = rom.readBytes(0x0A7F4C, 0x1000);
        assertArrayEquals(java.util.HexFormat.of().parseHex("0014000800040004"),
                java.util.Arrays.copyOf(loaded, 8), "actual ROM header, dimensions are unsigned words");
        assertArrayEquals(java.util.HexFormat.of().parseHex(
                "43f880003011c0e9000432290002c2e90006d04106400088d2c0323c0fff9240421951c9fffc"),
                rom.readBytes(0x05A3DA, 0x26), "complete native clear body including operands and DBF");
        int clearOffset = ((nativeWord(loaded, 0) * nativeWord(loaded, 4)
                + nativeWord(loaded, 2) * nativeWord(loaded, 6)) + 0x88) & 0xFFFF;
        assertEquals(0x00F8, clearOffset, "0x14*0x04 + 0x08*0x04 + 0x88; not 0x20*4");
        assertEquals(0x0F08, 0x1000 - clearOffset, "DBF 0x0F07 is inclusive");
        assertEquals(0x06, loaded[0x00FF] & 0xFF, "discriminating adjacent MHZ ROM bytes before ScreenInit");
        assertEquals(0x80, loaded[0x0100] & 0xFF);
        var manager = GameServices.level();
        assertEquals(23, manager.getCurrentZone());
        assertEquals(0, manager.getCurrentAct());
        assertFalse(state().screenInitApplied(), "ordinary first production ScreenInit has not run");
        var objects = manager.getObjectManager();
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        assertArrayEquals(loaded, nativeLayout(before).encoded(), "Load_Level copies the actual 0x1000 bytes");
        int[] definitions = finalDefinitionWords();
        assertFinalTargetResources(continuation);

        fixture.stepIdleFrames(1); // Existing production pre-physics ScreenInit, no private phase/timer writes.
        assertTrue(state().screenInitApplied());
        assertEquals(2, objects.activeObjectsOfType(DezFinalArenaFloor.class).size());
        assertEquals(1, objects.activeObjectsOfType(DezFinalBossController.class).size());
        var oldFloor = objects.activeObjectsOfType(DezFinalArenaFloor.class).getFirst();
        applyDeclaredNativeBackgroundCellEdit();
        var initialized = registry.capture();
        assertScreenInitIsIdempotent(initialized);
        assertArrayEquals(definitions, finalDefinitionWords(), "ScreenInit edits layout only");
        assertFinalTargetResources(continuation);
        fixture.stepIdleFrames(3);
        var forward = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            registry.restore(before);
            sameNativeWorld(before, registry.capture());
            assertFalse(state().screenInitApplied());
            fixture.stepIdleFrames(1);
            assertTrue(state().screenInitApplied());
            assertNotSame(oldFloor, manager.getObjectManager().activeObjectsOfType(DezFinalArenaFloor.class).getFirst(),
                    "forced registered-world restore recreates actual support owners");
            oldFloor = manager.getObjectManager().activeObjectsOfType(DezFinalArenaFloor.class).getFirst();
            applyDeclaredNativeBackgroundCellEdit();
            sameNativeWorld(initialized, registry.capture());
            assertScreenInitIsIdempotent(initialized);
            fixture.stepIdleFrames(3);
            sameNativeWorld(forward, registry.capture());
            assertArrayEquals(definitions, finalDefinitionWords());
            assertFinalTargetResources(continuation);
        }

        // Prefix preservation is independent of the tail red. Native ScreenInit's
        // four row-0 alias WORD writes are the only permitted prefix exceptions.
        byte[] actual = nativeLayout(registry.capture()).encoded();
        byte[] preserved = loaded.clone();
        int editOffset = nativeWord(loaded, 8 + 0x02 * 4) - 0x8000 + 0x0D;
        preserved[editOffset] = 0x1A; // Explicit legitimate later background edit below.
        for (int offset = 0; offset < clearOffset; offset++) {
            boolean aliasByte = offset >= 0x18 && offset < 0x1C || offset >= 0x84 && offset < 0x88;
            if (!aliasByte) assertEquals(preserved[offset] & 0xFF, actual[offset] & 0xFF,
                    "meaningful native prefix preserved at byte " + Integer.toHexString(offset));
        }
        int rowOne = nativeWord(loaded, 0x0C) - 0x8000;
        assertEquals(0x009C, rowOne);
        assertArrayEquals(java.util.Arrays.copyOfRange(loaded, rowOne, rowOne + 0x14),
                java.util.Arrays.copyOfRange(actual, rowOne, rowOne + 0x14), "all 0x14 meaningful FG row-1 cells");
        // FINAL discriminants: no assertion of player velocity, blank definition zero,
        // an observed route descriptor, or native full-route correctness follows.
        assertEquals(0, actual[0x00FF] & 0xFF, "DEZ3_ScreenInit must clear native layout RAM $FF80FF");
        assertEquals(0, actual[0x0100] & 0xFF, "DEZ3_ScreenInit must clear native layout RAM $FF8100");
        for (int offset = clearOffset; offset < 0x1000; offset++)
            assertEquals(0, actual[offset] & 0xFF, "native inclusive clear through $FF8FFF, offset " + offset);
    }

    private void applyDeclaredNativeBackgroundCellEdit() {
        var manager = GameServices.level();
        // DEZ3_BackgroundEvent loc_5A542's actual later command is FG (0x0D,0x02)=0x1A.
        // This is a DECLARED mutation-pipeline control, not a naturally triggered boss-signal claim.
        var context = new com.openggf.game.mutation.LayoutMutationContext(
                com.openggf.game.mutation.LevelMutationSurface.forLevel(manager.getCurrentLevel()),
                manager::applyMutationEffects);
        GameServices.level().getObjectManager().getObjectServices().zoneLayoutMutationPipeline()
                .applyImmediately(mutation -> mutation.surface()
                        .setBlockInMapWithoutRedraw(0, 0x0D, 0x02, 0x1A), context);
        assertEquals(0x1A, manager.getCurrentLevel().getMap().getValue(0, 0x0D, 0x02) & 0xFF);
    }

    private void assertScreenInitIsIdempotent(com.openggf.game.rewind.CompositeSnapshot initialized) {
        var manager = GameServices.level();
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var current = registry.capture();
        sameNativeWorld(initialized, current);
        DezFinalScreenEvents.initializeObjectsAndCamera(manager.getObjectManager(), state());
        var repeated = registry.capture();
        sameNativeWorld(current, repeated);
        assertSame(current.get("s3k-collision-layout"), repeated.get("s3k-collision-layout"),
                "later layout edit must not cause a second encoded-owner write/clear");
        assertEquals(0x1A, manager.getCurrentLevel().getMap().getValue(0, 0x0D, 0x02) & 0xFF);
    }

    private static com.openggf.game.sonic3k.S3kCollisionLayoutRewindAdapter.State nativeLayout(
            com.openggf.game.rewind.CompositeSnapshot snapshot) {
        return assertInstanceOf(com.openggf.game.sonic3k.S3kCollisionLayoutRewindAdapter.State.class,
                snapshot.get("s3k-collision-layout"));
    }

    private static int nativeWord(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static byte[] finalRomDecode(int address) throws Exception {
        return new com.openggf.level.resources.ResourceLoader(TestEnvironment.currentRom()).loadWithOverlays(
                java.util.List.of(com.openggf.level.resources.LoadOp.kosinskiBase(address)), 0x8000);
    }

    private static void assertFinalTargetResources(boolean continuation) throws Exception {
        var rom = TestEnvironment.currentRom();
        var level = GameServices.level().getCurrentLevel();
        byte[] target128 = finalRomDecode(0x1DC24E);
        assertEquals(0x0E80, target128.length);
        byte[] expected128 = continuation ? finalRomDecode(0x1D5068) : target128.clone();
        if (continuation) assertEquals(0x8000, expected128.length);
        System.arraycopy(target128, 0, expected128, 0, target128.length);
        assertEquals(expected128.length / 128, level.getBlockCount());
        for (int offset = 0; offset < expected128.length; offset += 2) {
            int within = offset & 127;
            assertEquals(nativeWord(expected128, offset), level.getBlock(offset >>> 7)
                    .getChunkDesc((within >>> 1) & 7, within >>> 4).get(), "ROM-owned128 byte " + offset);
        }
        byte[] target16 = finalRomDecode(0x1D899C);
        assertEquals(0x0CF0, target16.length);
        byte[] expected16 = target16;
        if (continuation) {
            byte[] primary = finalRomDecode(0x1D1DD6), secondary = finalRomDecode(0x1D7E3A);
            assertEquals(0x15E0, primary.length); assertEquals(0x0210, secondary.length);
            expected16 = java.util.Arrays.copyOf(primary, primary.length + secondary.length);
            System.arraycopy(secondary, 0, expected16, primary.length, secondary.length);
            System.arraycopy(target16, 0, expected16, 0, target16.length);
        }
        int collision = rom.read32BitAddr(0x098100 + 46 * 4) & 0xFFFFFF;
        assertEquals(0x09CFC0, collision, "final native LoadSolids target bank, never predecessor DEZ 0x09B1C0");
        for (int group = 0; group < expected16.length / 8; group++) {
            var chunk = level.getChunk(group);
            for (int word = 0; word < 4; word++)
                assertEquals(nativeWord(expected16, group * 8 + word * 2),
                        chunk.getPatternDesc(word & 1, word >>> 1).get(), "known16 group " + group);
            assertEquals(rom.readByte(collision + group * 2) & 0xFF, chunk.getSolidTileIndex());
            assertEquals(rom.readByte(collision + group * 2 + 1) & 0xFF, chunk.getSolidTileAltIndex());
        }
        // Capacity beyond the actual known extent is not asserted to be native zero.
    }

    private static int[] finalDefinitionWords() {
        var level = GameServices.level().getCurrentLevel();
        int[] words = new int[2 + level.getBlockCount() * 64 + level.getChunkCount() * 6];
        int at = 0; words[at++] = level.getBlockCount(); words[at++] = level.getChunkCount();
        for (int block = 0; block < level.getBlockCount(); block++)
            for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++)
                words[at++] = level.getBlock(block).getChunkDesc(col, row).get();
        for (int group = 0; group < level.getChunkCount(); group++) {
            var chunk = level.getChunk(group);
            for (int word = 0; word < 4; word++) words[at++] = chunk.getPatternDesc(word & 1, word >>> 1).get();
            words[at++] = chunk.getSolidTileIndex(); words[at++] = chunk.getSolidTileAltIndex();
        }
        return words; // Opaque capacity is compared for preservation, never claimed native-known.
    }

    private static void sameNativeWorld(com.openggf.game.rewind.CompositeSnapshot expected,
                                        com.openggf.game.rewind.CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet())
            assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={320,800})
    void outgoingDoomsdayLoadDoesNotInheritFinalArenaCameraProjection(int width) throws Exception {
        var fixture=boot(width); fixture.stepIdleFrames(1);
        assertTrue(((com.openggf.game.internal.NativeArenaCameraFraming)
                GameServices.level().getZoneFeatureProvider()).centerNativeArenaCamera());
        GameServices.level().loadZoneAndAct(12,0);
        assertEquals(0,GameServices.camera().getX(),"DDZ Get_LevelSizeStart begins at the native origin");
        assertFalse(((com.openggf.game.internal.NativeArenaCameraFraming)
                GameServices.level().getZoneFeatureProvider()).centerNativeArenaCamera());
    }

    @Test void firstProductionFrameAllocatesSupportsAndStartsForcedEntryOnlyOnce() {
        var fixture = boot();
        var manager = GameServices.level().getObjectManager();
        assertFalse(state().screenInitApplied());
        fixture.stepIdleFrames(1);
        assertTrue(state().screenInitApplied());
        assertEquals(2, manager.activeObjectsOfType(DezFinalArenaFloor.class).size());
        assertEquals(1, manager.activeObjectsOfType(DezFinalBossController.class).size());
        assertTrue(fixture.sprite().isObjectControlled());
        assertEquals(0x30, fixture.sprite().getCentreX());
        assertEquals(0x80, fixture.camera().getXCopy());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        fixture.stepIdleFrames(3);
        int x = fixture.sprite().getCentreX();
        registry.restore(saved);
        fixture.stepIdleFrames(3);
        assertEquals(x, fixture.sprite().getCentreX());
        assertEquals(1, manager.activeObjectsOfType(DezFinalBossController.class).size());
        assertEquals(2, manager.activeObjectsOfType(DezFinalArenaFloor.class).size());
    }
    @Test void allocationPressureKeepsNativePrefixAndStillInitializesCamera() {
        for (int free = 0; free <= 3; free++) {
            var fixture = boot();
            var manager = GameServices.level().getObjectManager();
            manager.reserveAllButNFreeSlots(free);
            DezFinalScreenEvents.initializeObjectsAndCamera(manager, state());
            assertEquals(Math.min(2, free), manager.activeObjectsOfType(DezFinalArenaFloor.class).size());
            assertEquals(free == 3 ? 1 : 0, manager.activeObjectsOfType(DezFinalBossController.class).size());
            assertEquals(free == 3 ? 0x3C0 : 0, state().bossX());
            assertEquals(free == 3 ? 0xF8 : 0, state().bossY());
            assertEquals(0x80, fixture.camera().getXCopy());
            assertEquals(0x6C0, state().windowBase());
            assertEquals(0, state().uploadedLaserOffset());
            manager.releaseDynamicSlot(80);
            DezFinalScreenEvents.initializeObjectsAndCamera(manager, state());
            assertEquals(free == 3 ? 1 : 0, manager.activeObjectsOfType(DezFinalBossController.class).size());
        }
    }
    @Test void productionRunReachesHandsAndRetainedPlaneStagesThenReplaysAfterRestore() {
        var fixture = boot();
        var manager = GameServices.level().getObjectManager();
        for (int frame = 0; frame < 410; frame++)
            fixture.stepFrame(false, false, false, true, false);
        assertFalse(fixture.sprite().getDead());
        assertEquals(0x2C0, state().windowBase());
        assertEquals(0x10, state().backgroundRoutine());
        assertEquals(2, manager.activeObjectsOfType(DezFinalHand.class).size());
        assertEquals(6, manager.activeObjectsOfType(DezFinalHand.Finger.class).size());
        assertTrue(state().plane().revision() > 16);
        assertTrue(state().arenaPlane().revision() > 16);
        assertEquals(0, state().redrawRequest(), "screen event consumes floor hole redraws");
        var features = new com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider();
        assertEquals(0, features.foregroundDescriptorAt(0x100, 0x160));
        assertEquals(0, features.foregroundDescriptorAt(0x500, 0x160));
        assertEquals(state().plane().descriptor(0x2C0, 0x160),
                features.foregroundDescriptorAt(0x2C0, 0x160));
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        fixture.stepIdleFrames(12);
        byte[] stateBytes = state().captureBytes();
        int x = fixture.sprite().getCentreX(), y = fixture.sprite().getCentreY();
        registry.restore(saved);
        fixture.stepIdleFrames(12);
        assertArrayEquals(stateBytes, state().captureBytes());
        assertEquals(x, fixture.sprite().getCentreX());
        assertEquals(y, fixture.sprite().getCentreY());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {320, 352, 400, 528, 800})
    void widenedViewportKeepsNativeEntrySupportAndReachesHands(int width) {
        var fixture = boot(width);
        assertEquals(width, fixture.camera().getWidth());
        fixture.stepIdleFrames(1);
        var provider = new com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider();
        var columns = provider.backgroundColumns();
        if (width == 320) assertNull(columns);
        else {
            assertNotNull(columns);
            assertEquals(0xC0, columns.sourceYEndExclusive(), "native $E0 split minus $20 FBO origin");
            for (int x = 0; x < 320; x++) {
                int screenX = (width - 320) / 2 + x;
                assertEquals(x, columns.sourceX().get(screenX));
                assertEquals(0, columns.yOffsets().get(screenX));
            }
            assertSame(columns, provider.backgroundColumns());
        }
        for (int frame = 0; frame < 410; frame++) {
            fixture.stepFrame(false, false, false, true, false);
            assertFalse(fixture.sprite().getDead(), "death at frame " + frame + ", width " + width);
        }
        assertEquals(2, GameServices.level().getObjectManager().activeObjectsOfType(DezFinalHand.class).size());
        assertFalse(fixture.sprite().isObjectControlled());
        assertEquals(0x10, state().backgroundRoutine());
        int nativeLeft = state().retainedPlaneX() != 0 ? state().retainedPlaneX() : state().planeX();
        for (int x = 0x200; x < 0x400; x += 8) for (int y = 0x100; y < 0x200; y += 8) {
            boolean outside = x + 8 <= nativeLeft || x >= nativeLeft + 320;
            int expected = width > 320 && outside
                    ? GameServices.level().getForegroundTileDescriptorAtWorld(x, y)
                    : state().plane().descriptor(x, y);
            assertEquals(expected, provider.foregroundDescriptorAt(x, y), "body margin/native overlap");
        }
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        fixture.stepIdleFrames(12);
        int x = fixture.sprite().getCentreX(), y = fixture.sprite().getCentreY();
        int cameraX = fixture.camera().getX();
        byte[] bytes = state().captureBytes();
        registry.restore(saved);
        fixture.stepIdleFrames(12);
        assertEquals(x, fixture.sprite().getCentreX());
        assertEquals(y, fixture.sprite().getCentreY());
        assertEquals(cameraX, fixture.camera().getX());
        assertArrayEquals(bytes, state().captureBytes());
    }

    @Test void nativeWindowEdgeRefreshesTheCacheBeforeTheNextRomColumnDraw() {
        var fixture = boot(800); fixture.stepIdleFrames(1);
        state().windowBase(0x2C0); state().bossPosition(0x280, 0x100);
        state().publishPlanePosition(0x80, 0); state().publishDisplayedWindow();
        var provider = new com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider();
        int original = GameServices.level().getForegroundTileDescriptorAtWorld(0x200, 0x100);
        int retained = original ^ 0x800;
        state().plane().writeRow(0x200, 0x100, 1, (x, y) -> retained);
        // Source $200 is outside [$C0,$200), then overlaps the native view
        // after a one-pixel move. No retained-plane write happens between reads.
        assertEquals(original, provider.foregroundDescriptorAt(0x200, 0x100));
        long revision = provider.foregroundDescriptorRevision();
        state().publishPlanePosition(0x81, 0);
        assertEquals(retained, provider.foregroundDescriptorAt(0x200, 0x100));
        assertNotEquals(revision, provider.foregroundDescriptorRevision());
    }

    @Test void screenEventPublishesRomFloorHoleIntoTheRetainedPlaneOnly() {
        var fixture = boot();
        fixture.stepIdleFrames(1);
        var level = GameServices.level();
        int untouched = state().arenaPlane().descriptor(0x40, 0xE0);
        state().redrawRequest(0x20);
        fixture.stepIdleFrames(1);
        assertEquals(0, state().redrawRequest());
        for (int y = 0; y < 32; y += 8) for (int x = 0; x < 32; x += 8)
            assertEquals(level.getBackgroundTileDescriptorAtWorld(0x20 + x, 0x1E0 + y),
                    state().arenaPlane().descriptor(0x20 + x, 0xE0 + y));
        assertEquals(untouched, state().arenaPlane().descriptor(0x40, 0xE0));
    }

    @Test void rendererUsesIndependentBossScrollIncludingZeroVerticalOffset() {
        var fixture = boot(); fixture.stepIdleFrames(1);
        var provider = new com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider();
        var controller = new com.openggf.game.render.AdvancedRenderModeController();
        provider.registerAdvancedRenderModes(controller, 23, 0);
        state().bossPosition(0x3C0, 0x1A0); state().publishPlanePosition(0x80, 0);
        var render = controller.resolve(new com.openggf.game.render.AdvancedRenderModeContext(
                fixture.camera(), 0, GameServices.level(), 23, 0, fixture.camera().getX()));
        assertTrue(render.enablePerLineForegroundScroll());
        assertTrue(render.hasForegroundVScrollOverride());
        assertEquals(0, render.foregroundVScrollOverride());
        controller.clear(); provider.registerAdvancedRenderModes(controller, 23, 1);
        assertTrue(controller.isEmpty(), "sanctuary keeps its own rendering");
    }

}
