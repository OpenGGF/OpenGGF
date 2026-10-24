package com.openggf.tests;

import com.openggf.LevelFrameResult;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.objects.IczBigSnowPileInstance;
import com.openggf.game.sonic3k.objects.IczSnowboardIntroInstance;
import com.openggf.game.sonic3k.objects.IczTeleporterInstance;
import com.openggf.game.sonic3k.objects.TeleporterBeamObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Native ICZ1 fresh Knuckles component inlet, $53608/$53AD4; not a cold CNZ route
 * or native-pixel qualification. Initial assembly and ordinary frames use production owners.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kIczKnucklesArrival {
    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        config.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT_PIXELS, 224);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
    }

    @AfterEach
    void tearDown() { TestEnvironment.resetAll(); }

    private HeadlessTestFixture boot() {
        return HeadlessTestFixture.builder().withZoneAndAct(5, 0)
                .withFreshLevelStartLifecycle().build();
    }

    @Test
    void nativeCameraTriggersAdvanceKnucklesWithoutSnowAndReplayTwice() {
        var fixture = boot();
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        step(fixture, 64);
        assertTrue(pad().released(), "the real teleporter releases Knuckles first");
        assertFalse(fixture.sprite().isObjectControlled());
        assertSnowFreeBackground(fixture, 0);

        // Declared camera-word component controls, not ordinary traversal from
        // x=$3640. Stock loc_1C9E0 checks BOTH $3700 and $68C; loc_1C9FA
        // checks $3940. No background stage, snow offset or player state is set.
        updateAtCamera(fixture, 0x36FF, 0x068C);
        assertSnowFreeBackground(fixture, 0);
        updateAtCamera(fixture, 0x3700, 0x068B);
        assertSnowFreeBackground(fixture, 0);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var beforeFirst = registry.capture();

        updateAtCamera(fixture, 0x3700, 0x068C);
        assertSnowFreeBackground(fixture, 4);
        var afterFirst = registry.capture();
        updateAtCamera(fixture, 0x393F, 0x068C);
        assertSnowFreeBackground(fixture, 4);
        var beforeSecond = registry.capture();

        updateAtCamera(fixture, 0x3940, 0x068C);
        assertSnowFreeBackground(fixture, 8);
        var afterSecond = registry.capture();
        // Continue through the existing refresh owners to Normal, without the
        // late-handoff fixture's forceAct1NormalBackgroundRoutineForTest bypass.
        updateAtCamera(fixture, 0x3940, 0x068C);
        assertSnowFreeBackground(fixture, 12);
        updateAtCamera(fixture, 0x3940, 0x068C);
        assertSnowFreeBackground(fixture, 16);
        assertFalse(events().isAct2TransitionRequested(), "the later $6900 handoff is independent");
        var normal = registry.capture();

        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(beforeFirst);
            assertWorld(beforeFirst, registry.capture(), "before first camera trigger " + cycle);
            updateAtCamera(fixture, 0x3700, 0x068C);
            assertWorld(afterFirst, registry.capture(), "first camera trigger " + cycle);
            updateAtCamera(fixture, 0x393F, 0x068C);
            assertWorld(beforeSecond, registry.capture(), "before second camera trigger " + cycle);
            registry.restore(beforeSecond);
            assertWorld(beforeSecond, registry.capture(), "second trigger restore " + cycle);
            updateAtCamera(fixture, 0x3940, 0x068C);
            assertWorld(afterSecond, registry.capture(), "second camera trigger " + cycle);
            updateAtCamera(fixture, 0x3940, 0x068C);
            updateAtCamera(fixture, 0x3940, 0x068C);
            assertWorld(normal, registry.capture(), "normal background forward " + cycle);
        }
    }

    @Test
    void genuineFreshAssemblyOverridesTableStartAndOnlyPatchesTargetColours() {
        var fixture = boot();
        var level = GameServices.level();
        var player = fixture.sprite();
        assertEquals(0x65C, player.getCentreY() & 0xFFFF, "unmodified Knuckles start table");
        byte[] normalBefore = normalPaletteLine();
        int xSub = player.getXSubpixelRaw(), ySub = player.getYSubpixelRaw();
        assertTrue(level.consumePendingInitialProcessSpritesPass(), "executes real initial assembly");
        var pad = pad();
        assertTrue(pad.initialized());
        assertNotNull(pad.beamForTest());
        assertEquals(0x3640, player.getCentreX() & 0xFFFF);
        assertEquals(0x660, player.getCentreY() & 0xFFFF, "ScreenInit Y word + same-pass zero swing");
        assertEquals(xSub, player.getXSubpixelRaw());
        assertEquals(ySub, player.getYSubpixelRaw());
        assertEquals(0x35A0, fixture.camera().getX() & 0xFFFF);
        assertEquals(0x35A0, fixture.camera().getXCopy() & 0xFFFF);
        assertEquals(0x5FB, fixture.camera().getY() & 0xFFFF);
        assertEquals(0x5FB, fixture.camera().getYCopy() & 0xFFFF);
        assertTrue(player.isObjectControlled());
        assertTrue(player.getRolling());
        assertEquals(2, player.getAnimationId());
        assertEquals(1, pad.getPriorityBucket());
        assertFalse(pad.isHighPriority());
        assertEquals(0x23, pad.getSolidParams().halfWidth());
        assertEquals(0x10, pad.getSolidParams().airHalfHeight());
        assertEquals(1, pad.getSolidParams().groundHalfHeight());
        assertFalse(pad.isSolidFor(player), "Main does not call SolidObjectFull while swinging");
        assertArrayEquals(normalBefore, normalPaletteLine(), "init writes Target, not Normal palette");
        var palette = GameServices.paletteOwnershipRegistry();
        assertArrayEquals(new byte[] {0x0E, (byte) 0xA2}, palette.targetSegaData(1, 8, 1));
        assertArrayEquals(new byte[] {0x0E, (byte) 0x80}, palette.targetSegaData(1, 10, 1));
        assertTrue(level.getObjectManager().activeObjectsOfType(IczSnowboardIntroInstance.class).isEmpty());
        assertFalse(level.consumePendingInitialProcessSpritesPass(), "one-shot assembly token");
    }

    @Test
    void p1SwingIgnoresP2FocusAndScreenInitDoesNotRepeat() {
        GameServices.configuration().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        var fixture = boot();
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        var p2 = GameServices.sprites().getRegisteredSidekicks().getFirst();
        fixture.camera().setFocusedSprite(p2);
        int p2X = p2.getCentreX(), p2Y = p2.getCentreY();
        pad().update(0, p2);
        assertEquals(0x65E, fixture.sprite().getCentreY() & 0xFFFF, "second exact swing call");
        assertEquals(p2X, p2.getCentreX());
        assertEquals(p2Y, p2.getCentreY());
        var pad = pad();
        events().applyScreenInitAtLoad(0);
        assertSame(pad, pad());
        assertEquals(1, GameServices.level().getObjectManager().activeObjectsOfType(IczTeleporterInstance.class).size());
        assertEquals(0x65E, fixture.sprite().getCentreY() & 0xFFFF, "captured latch prevents recentering");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sonic", "tails"})
    void otherNativeModesDoNotReceiveKnucklesArrival(String main) {
        GameServices.configuration().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, main);
        boot();
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(IczTeleporterInstance.class).isEmpty());
    }

    @Test
    void savedEntryFlagSkipsAllocationAndForcedWords() {
        var fixture = boot();
        // Declared saved-entry component inlet: the same public Last_star_post_hit
        // owner restored by the production checkpoint profile, before initial ScreenInit.
        GameServices.level().setLastStarPostHit();
        int x = fixture.sprite().getCentreX(), y = fixture.sprite().getCentreY();
        int cameraX = fixture.camera().getX(), cameraY = fixture.camera().getY();
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(IczTeleporterInstance.class).isEmpty());
        assertEquals(x, fixture.sprite().getCentreX());
        assertEquals(y, fixture.sprite().getCentreY());
        assertEquals(cameraX, fixture.camera().getX());
        assertEquals(cameraY, fixture.camera().getY());
    }

    @Test
    void screenInitExhaustionSkipsForcedWordsAndDoesNotRetryNextFrame() {
        var fixture = boot();
        var objects = GameServices.level().getObjectManager();
        var fillers = new ArrayList<IczTeleporterInstance>();
        while (true) {
            var filler = objects.createDynamicObject(() -> new IczTeleporterInstance(spawn()));
            if (filler == null) break;
            fillers.add(filler);
        }
        int x = fixture.sprite().getCentreX(), y = fixture.sprite().getCentreY();
        int cameraX = fixture.camera().getX(), cameraY = fixture.camera().getY();
        int count = objects.activeObjectsOfType(IczTeleporterInstance.class).size();
        // Component call at the genuine filled AllocateObject pool boundary;
        // none of the intentionally inert filler owners are dispatched.
        events().applyScreenInitAtLoad(0);
        assertEquals(count, objects.activeObjectsOfType(IczTeleporterInstance.class).size());
        assertEquals(x, fixture.sprite().getCentreX());
        assertEquals(y, fixture.sprite().getCentreY());
        assertEquals(cameraX, fixture.camera().getX());
        assertEquals(cameraY, fixture.camera().getY());
        objects.removeDynamicObject(fillers.getLast());
        events().applyScreenInitAtLoad(0);
        assertEquals(count - 1, objects.activeObjectsOfType(IczTeleporterInstance.class).size(),
                "ScreenInit is once-only, unlike the allocated parent's retrying init routine");
    }

    @Test
    void realAfterCurrentExhaustionRetriesWithoutControlPaletteOrPoseEffects() {
        var fixture = boot();
        var objects = GameServices.level().getObjectManager();
        // Declared allocation component inlet: a genuine public pool allocation,
        // before native ScreenInit; no captured phase or private slot is injected.
        var pad = objects.createDynamicObject(() -> new IczTeleporterInstance(spawn()));
        assertNotNull(pad);
        var fillers = new ArrayList<IczTeleporterInstance>();
        while (true) {
            var filler = objects.createDynamicObject(() -> new IczTeleporterInstance(spawn()));
            if (filler == null) break;
            fillers.add(filler);
        }
        var player = fixture.sprite();
        player.setSkidding(true);
        player.setSkidDustTimer(7);
        player.setFixedSkidDustActive(true);
        player.setSubpixelRaw(0x1234, 0xABCD);
        player.setXSpeed((short) 0x123);
        player.setYSpeed((short) 0x234);
        player.setGSpeed((short) 0x345);
        int xRadius = player.getXRadius(), yRadius = player.getYRadius();
        int y = player.getCentreY();
        byte[] target = GameServices.paletteOwnershipRegistry().targetSegaData(1, 0, 16);
        int occupied = objects.getActiveObjectSlotCount();
        pad.update(0, player);
        assertFalse(pad.initialized());
        assertEquals(occupied, objects.getActiveObjectSlotCount());
        assertTrue(objects.activeObjectsOfType(TeleporterBeamObjectInstance.class).isEmpty());
        assertNull(pad.beamForTest(), "a constructed but unregistered child is allocation failure");
        assertFalse(player.isObjectControlled());
        assertEquals(y, player.getCentreY());
        assertArrayEquals(target, GameServices.paletteOwnershipRegistry().targetSegaData(1, 0, 16));
        var freed = fillers.getLast();
        int freedSlot = freed.getSlotIndex();
        objects.removeDynamicObject(freed);
        pad.update(0, player);
        assertTrue(pad.initialized());
        assertNotNull(pad.beamForTest());
        assertEquals(freedSlot, pad.beamForTest().getSlotIndex());
        assertSame(pad.beamForTest(), NativeSstSlotAccess.currentOwner(objects, freedSlot));
        assertTrue(player.isObjectControlled());
        assertTrue(player.getRolling());
        assertEquals(xRadius, player.getXRadius());
        assertEquals(yRadius, player.getYRadius());
        assertEquals(0x1234, player.getXSubpixelRaw());
        assertEquals(0xABCD, player.getYSubpixelRaw());
        assertEquals(0x123, player.getXSpeed());
        assertEquals(0x234, player.getYSpeed());
        assertEquals(0x345, player.getGSpeed());
        assertTrue(player.getSkidding());
        assertEquals(7, player.getSkidDustTimer());
        assertTrue(player.isFixedSkidDustActive());
    }

    @Test
    void terminalBeamKeepsItsRealSlotThroughFinalDrawThenRetiresNextDispatchAndReplaysTwice() {
        var fixture = boot();
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        var objects = GameServices.level().getObjectManager();
        var beam = pad().beamForTest();
        int slot = beam.getSlotIndex();
        int frames = 0;
        while (beam.progress() > 0) {
            assertTrue(++frames < 64, "native contracting beam reaches zero");
            step(fixture, 1);
        }
        assertEquals(0, beam.progress());
        assertSame(beam, pad().beamForTest(), "completion is not published at progress zero");
        while (beam.progress() == 0) {
            assertTrue(++frames < 64, "next odd native frame underflows");
            step(fixture, 1);
        }
        assertEquals(-1, beam.progress());
        assertFalse(beam.isDestroyed());
        assertSame(beam, NativeSstSlotAccess.currentOwner(objects, slot),
                "frame9 remains in its registered SST during the underflow draw");
        assertNull(pad().beamForTest(), "the native parent completion write already ran");
        var registry = fixture.gameplayMode().getRewindRegistry();
        var terminal = registry.capture();
        step(fixture, 1);
        assertNull(NativeSstSlotAccess.currentOwner(objects, slot), "next own Delete dispatch retires the slot");
        assertTrue(objects.activeObjectsOfType(TeleporterBeamObjectInstance.class).isEmpty());
        var retired = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(terminal);
            assertWorld(terminal, registry.capture(), "registered terminal restore " + cycle);
            var restoredObjects = GameServices.level().getObjectManager();
            var restored = restoredObjects.activeObjectsOfType(TeleporterBeamObjectInstance.class).getFirst();
            assertSame(restored, NativeSstSlotAccess.currentOwner(restoredObjects, slot));
            assertFalse(restored.isDestroyed());
            assertNull(pad().beamForTest());
            step(fixture, 1);
            assertNull(NativeSstSlotAccess.currentOwner(restoredObjects, slot));
            assertWorld(retired, registry.capture(), "registered Delete forward " + cycle);
        }
    }

    @Test
    void fullRegisteredWorldRestoresInitContractionAndReleaseTwice() {
        var fixture = boot();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var beforeInit = registry.capture();
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        var initialized = registry.capture();
        assertTrue(initialized.entries().size() >= 55, "complete registered world, not an object-only snapshot");
        step(fixture, 12);
        var contracting = registry.capture();
        assertNotNull(pad().beamForTest());
        assertTrue(pad().beamForTest().progress() < 0x18);
        step(fixture, 52);
        assertTrue(pad().released());
        assertFalse(fixture.sprite().isObjectControlled());
        assertTrue(pad().isSolidFor(fixture.sprite()));
        assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(TeleporterBeamObjectInstance.class).isEmpty());
        var released = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(beforeInit);
            assertWorld(beforeInit, registry.capture(), "pre-init restore " + cycle);
            assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
            assertWorld(initialized, registry.capture(), "real initialization forward " + cycle);
            step(fixture, 12);
            assertWorld(contracting, registry.capture(), "contracting forward " + cycle);
            registry.restore(contracting);
            assertWorld(contracting, registry.capture(), "reciprocal graph restore " + cycle);
            step(fixture, 52);
            assertWorld(released, registry.capture(), "release forward " + cycle);
        }
        // Declared threshold component inlet; CheckDel reads the camera COPY word.
        fixture.camera().setXCopy((short) 0x377F);
        pad().update(0, fixture.sprite());
        assertFalse(pad().isDestroyed());
        var beforeDelete = registry.capture();
        fixture.camera().setXCopy((short) 0x3780);
        var pad = pad();
        pad.update(0, fixture.sprite());
        assertTrue(pad.isDestroyed());
        byte[] normal = normalPaletteLine();
        assertEquals(0x002C, ((normal[16] & 255) << 8) | (normal[17] & 255));
        assertEquals(0, ((normal[20] & 255) << 8) | (normal[21] & 255));
        var deleted = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(beforeDelete);
            assertWorld(beforeDelete, registry.capture(), "solid pad restore " + cycle);
            fixture.camera().setXCopy((short) 0x3780);
            pad().update(0, fixture.sprite());
            assertWorld(deleted, registry.capture(), "threshold component delete forward " + cycle);
        }
    }

    @Test
    void nativeBeamAndPadFramesUseTheActualIczLevelPlcTiles() throws Exception {
        boot();
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        var art = assertInstanceOf(Sonic3kObjectArtProvider.class, GameServices.module().getObjectArtProvider());
        var beam = art.getSheet(Sonic3kObjectArtKeys.ICZ_TELEPORTER);
        assertNotNull(beam);
        assertEquals(1, beam.getPaletteIndex());
        assertEquals(8, beam.getFrame(9).pieces().size(), "native terminal main mapping is not empty");
        var rom = GameServices.rom().getRom();
        assertEquals(0x15, rom.readByte(0x92681) & 255);
        assertEquals(0x9CD0, rom.read16BitAddr(0x92682));
        assertEquals(0xAE00, rom.read16BitAddr(0x92684));
        assertEquals(0x10, rom.read16BitAddr(0x159CD0) & 0x7FFF, "native Nemesis beam has 16 patterns");
        var nativePatterns = com.openggf.util.PatternDecompressor.nemesis(rom, 0x159CD0);
        for (int tile = 0; tile < nativePatterns.length; tile++) {
            // All 11 preserved mappings include tile zero in the unused pad;
            // beam mapping $12 therefore remains sheet index $12.
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) assertEquals(
                    nativePatterns[tile].getPixel(x, y), beam.getPatterns()[0x12 + tile].getPixel(x, y),
                    "actual ICZ PLC beam tile " + tile);
        }
        for (int frame = 1; frame <= 8; frame++) {
            assertFalse(beam.getFrame(frame).pieces().isEmpty());
            for (var piece : beam.getFrame(frame).pieces()) {
                // Level-backed sheets normalize their minimum mapping tile, preserving
                // the actual $570/$574 reads through the registration's $55E base.
                assertTrue(piece.tileIndex() >= 0);
                assertTrue(piece.tileIndex() + piece.widthTiles() * piece.heightTiles() <= beam.getPatterns().length);
            }
        }
        assertFalse(art.getSheet(Sonic3kObjectArtKeys.ICZ_PLATFORMS_INTRO).getFrame(0x1A).pieces().isEmpty());
    }

    @Test
    void visualReleaseUsesAcquiredP1AfterMainAndFocusChangeWithoutAdvancingArrival() throws Exception {
        GameServices.configuration().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        var fixture = boot();
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        var source = pad();
        var original = fixture.sprite();
        var p2 = GameServices.sprites().getRegisteredSidekicks().getFirst();
        var replacement = new TestablePlayableSprite("knuckles", (short) 0x3700, (short) 0x680);
        GameServices.sprites().addSprite(replacement);
        fixture.camera().setFocusedSprite(p2);
        assertSame(replacement, GameServices.sprites().getMainPlayable());
        com.openggf.sprites.playable.ObjectControlState.nativeBit7FullControl().applyTo(replacement);
        com.openggf.sprites.playable.ObjectControlState.nativeBit7FullControl().applyTo(p2);
        var nativeBefore = arrivalNativeScalars(source);
        var beam = source.beamForTest();
        var beamBefore = arrivalNativeScalars(beam);
        var playerBefore = arrivalPlayerProgress(original);
        byte[] targetBefore = GameServices.paletteOwnershipRegistry().targetSegaData(1, 0, 16);
        byte[] normalBefore = normalPaletteLine();

        source.releasePlayersForVisualDirection();

        assertFalse(original.isObjectControlled());
        assertFalse(original.isObjectControlAllowsCpu());
        assertFalse(original.isObjectControlSuppressesMovement());
        assertTrue(replacement.isObjectControlled());
        assertTrue(p2.isObjectControlled());
        assertEquals(nativeBefore, arrivalNativeScalars(source));
        assertSame(beam, source.beamForTest());
        assertEquals(beamBefore, arrivalNativeScalars(beam));
        assertEquals(playerBefore, arrivalPlayerProgress(original));
        assertArrayEquals(targetBefore, GameServices.paletteOwnershipRegistry().targetSegaData(1, 0, 16));
        assertArrayEquals(normalBefore, normalPaletteLine());
        // A subsequent owner on the original P1 must survive an idempotent release.
        com.openggf.sprites.playable.ObjectControlState.nativeBit7FullControl().applyTo(original);
        source.releasePlayersForVisualDirection();
        assertTrue(original.isObjectControlled());
        assertEquals(nativeBefore, arrivalNativeScalars(source));
    }

    @Test
    void neverAcquiredAndRefusedBeamDoNotReleaseSomebodyElsesObjectControl() {
        var fixture = boot();
        var objects = GameServices.level().getObjectManager();
        var source = objects.createDynamicObject(() -> new IczTeleporterInstance(spawn()));
        assertNotNull(source);
        var player = fixture.sprite();
        com.openggf.sprites.playable.ObjectControlState.nativeBit7FullControl().applyTo(player);
        source.releasePlayersForVisualDirection();
        assertTrue(player.isObjectControlled());
        var fillers = new ArrayList<IczTeleporterInstance>();
        while (true) {
            var filler = objects.createDynamicObject(() -> new IczTeleporterInstance(spawn()));
            if (filler == null) break;
            fillers.add(filler);
        }
        source.update(0, player);
        assertFalse(source.initialized());
        assertNull(source.beamForTest());
        source.releasePlayersForVisualDirection();
        assertTrue(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertTrue(player.isObjectControlSuppressesMovement());
        objects.removeDynamicObject(fillers.getLast());
        source.update(0, player);
        assertTrue(source.initialized(), "the genuine retry reaches the acquisition writer");
        assertNotNull(source.beamForTest());
        assertTrue(player.isObjectControlAllowsCpu());
        source.releasePlayersForVisualDirection();
        assertFalse(player.isObjectControlled());
    }

    @Test
    void nativeSwingClearRelinquishesVisualOwnership() {
        var fixture = boot();
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        var source = pad();
        for (int dispatch = 0; dispatch < 64 && !source.released(); dispatch++) {
            source.update(dispatch, fixture.sprite());
        }
        assertTrue(source.released());
        assertFalse(fixture.sprite().isObjectControlled());
        com.openggf.sprites.playable.ObjectControlState.nativeBit7FullControl().applyTo(fixture.sprite());
        source.releasePlayersForVisualDirection();
        assertTrue(fixture.sprite().isObjectControlled(), "native clear removed this owner's metadata");
        assertTrue(source.released());
    }

    @Test
    void freshRegisteredIdentityRestoreBeforeDuringAndAfterVisualOwnershipReplaysTwice() {
        var fixture = boot();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        var original = pad();
        assertTrue(original.initialized());
        assertNotNull(original.beamForTest());
        var acquired = registry.capture();
        original.releasePlayersForVisualDirection();
        assertFalse(fixture.sprite().isObjectControlled());
        var released = registry.capture();

        for (int replay = 0; replay < 2; replay++) {
            registry.restore(before);
            assertWorld(before, registry.capture(), "pre-acquisition restore " + replay);
            assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
            var forward = pad();
            assertNotSame(original, forward, "native ScreenInit creates a fresh registered owner");
            assertTrue(fixture.sprite().isObjectControlled());
            assertWorld(acquired, registry.capture(), "native acquisition replay " + replay);

            // Retire the actual registered graph, then let the production rewind
            // reconstructor create fresh instances and resolve their player identities.
            com.openggf.level.objects.ObjectDirectionAccess.forManager(
                    GameServices.level().getObjectManager()).removeDynamic(forward);
            registry.restore(acquired);
            var restored = pad();
            assertNotSame(forward, restored);
            assertWorld(acquired, registry.capture(), "fresh acquired restore " + replay);
            restored.releasePlayersForVisualDirection();
            assertFalse(fixture.sprite().isObjectControlled());
            assertWorld(released, registry.capture(), "visual release replay " + replay);

            registry.restore(released);
            assertWorld(released, registry.capture(), "post-release restore " + replay);
            com.openggf.sprites.playable.ObjectControlState.nativeBit7FullControl().applyTo(fixture.sprite());
            pad().releasePlayersForVisualDirection();
            assertTrue(fixture.sprite().isObjectControlled(), "released snapshot contains no stale owner");
        }
    }

    private static java.util.Map<String, Object> arrivalNativeScalars(Object target) throws Exception {
        var state = new java.util.LinkedHashMap<String, Object>();
        for (Class<?> type = target.getClass(); type != Object.class; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                if (field.getType().isPrimitive() || field.getType().isEnum()) {
                    state.put(type.getSimpleName() + "." + field.getName(), field.get(target));
                } else if (field.get(target) instanceof com.openggf.game.rewind.RewindStateful<?> holder) {
                    state.put(type.getSimpleName() + "." + field.getName(), holder.captureRewindStateValue());
                }
            }
        }
        return state;
    }

    private static java.util.List<Object> arrivalPlayerProgress(com.openggf.sprites.playable.AbstractPlayableSprite player) {
        return java.util.List.of(player.getCentreX(), player.getCentreY(), player.getXSubpixelRaw(),
                player.getYSubpixelRaw(), player.getXSpeed(), player.getYSpeed(), player.getGSpeed(),
                player.getAnimationId(), player.getMappingFrame(), player.isObjectMappingFrameControl(),
                player.getRolling(), player.getXRadius(), player.getYRadius());
    }

    private static ObjectSpawn spawn() { return new ObjectSpawn(0, 0, 0, 0, 0, false, 0); }
    private static void updateAtCamera(HeadlessTestFixture fixture, int x, int y) {
        fixture.camera().setX((short) x);
        fixture.camera().setY((short) y);
        ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).update();
    }
    private static void assertSnowFreeBackground(HeadlessTestFixture fixture, int routine) {
        assertEquals(routine, events().getIcz1BackgroundRoutine(), "native Knuckles background stage");
        assertEquals(0, events().getIcz1BigSnowOffset(), "Knuckles never runs ICZ1_BigSnowFall");
        assertFalse(events().isEventsFg5(), "the normal background owner consumes the trigger");
        assertFalse(fixture.sprite().isControlLocked(), "Knuckles does not acquire Sonic's quake lock");
        assertTrue(GameServices.level().getObjectManager()
                .activeObjectsOfType(IczBigSnowPileInstance.class).isEmpty());
    }
    private static byte[] normalPaletteLine() {
        var palette = GameServices.level().getCurrentLevel().getPalette(1);
        byte[] bytes = new byte[32];
        for (int color = 0; color < 16; color++) {
            int word = com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(palette.getColor(color));
            bytes[color * 2] = (byte) (word >>> 8);
            bytes[color * 2 + 1] = (byte) word;
        }
        return bytes;
    }
    private static IczTeleporterInstance pad() {
        return GameServices.level().getObjectManager().activeObjectsOfType(IczTeleporterInstance.class).getFirst();
    }
    private static com.openggf.game.sonic3k.events.Sonic3kICZEvents events() {
        return ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getIczEvents();
    }
    private static void step(HeadlessTestFixture fixture, int frames) {
        for (int i = 0; i < frames; i++) assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                fixture.runner().stepFrame(false, false, false, false, false));
    }
    private static void assertWorld(CompositeSnapshot expected, CompositeSnapshot actual, String context) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), context);
        for (String key : expected.entries().keySet()) assertTrue(
                RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                () -> context + " " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
    }
}
