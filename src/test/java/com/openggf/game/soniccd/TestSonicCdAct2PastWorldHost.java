package com.openggf.game.soniccd;

import com.openggf.camera.Camera;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import com.openggf.game.soniccd.scroll.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.openggf.game.soniccd.SonicCdAct2PastLoadingVisit.*;
import static com.openggf.game.soniccd.SonicCdAct2PastWorldHost.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests authored before Host. Actual USA detached sources/Frame/scroll/art/plane algorithms;
 * mocked Module guard is a constructed admission oracle, never a live-World qualification.
 * Explicit target ingress represents the caller's InitVDP/RunMMD projections. Java/GL UNRUN.
 */
class TestSonicCdAct2PastWorldHost {
    private static CdR12bTerrainAssets terrain;
    private static CdR12bPlayerAssets player;

    @BeforeAll static void originalSources() throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        assertNotNull(configured, "Explicit original-USA source tests require soniccd.media.root");
        assertFalse(configured.isBlank());
        Path cue;
        try (var files = Files.list(Path.of(configured).resolve("USA"))) {
            var cues = files.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var media = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            terrain = CdR12bTerrainAssets.fromPrograms(media); player = CdR12bPlayerAssets.fromPrograms(media);
        }
    }

    @Test void constructorRetainsExplicitImagesAndCreatesNeitherNativeWorkNorRenderer() throws Exception {
        var f = fixture(0);
        assertTrue(Modifier.isFinal(SonicCdAct2PastWorldHost.class.getModifiers()));
        assertFalse(Modifier.isPublic(SonicCdAct2PastWorldHost.class.getModifiers()));
        assertSame(f.initial.globals(), f.host.globals()); assertSame(f.initial.scroll(), f.host.scroll());
        assertSame(f.initial.video(), f.host.video()); assertEquals(f.host.capture(), f.host.capture());
        assertTrue(f.host.sounds().isEmpty()); assertTrue(f.host.completedClears().isEmpty());
        assertEquals(Phase.ENTRY, f.visit.state().phase()); assertEquals(17, f.frame.vIntClock().deltaSinceAdmittedCut());
        assertNull(field(f.host, "render")); assertThrows(NullPointerException.class,
                () -> new SonicCdAct2PastWorldHost(null, terrain, player, f.frame, f.initial));
    }

    @Test void revokedModuleGuardAndWrongPairRejectBeforeMutationWithoutCaptureInCallbacks() {
        var f = fixture(0); Object before = f.host.capture();
        doThrow(new IllegalStateException("revoked actual World")).when(f.module).requireAct2PastLoadingVisit(any());
        assertThrows(IllegalStateException.class, f.visit::begin);
        assertThrows(IllegalStateException.class, f.host::placement);
        assertEquals(Phase.ENTRY, f.visit.state().phase());
        reset(f.module); assertEquals(before, f.host.capture());
        assertThrows(IllegalArgumentException.class, () -> f.host.validate(f.visit, f.frame, null, player));
    }

    @Test void firstRealCSelectsPreviousCpuThenCopiesAndExecutesAuthenticGeometryLoad() {
        var f = fixture(0); var entrySelected = f.host.selected(); var priorHscroll = f.host.scroll().background().hscrollRam();
        f.visit.begin(); assertSame(entrySelected, f.host.selected()); f.visit.service(8);
        var selected = ((Published) f.host.selected()).selection().inputs();
        assertArrayEquals(priorHscroll.copy(), selected.hscroll().copy());
        assertArrayEquals(f.initial.video().cpuSat().copy(), selected.selectedSat().copy());
        assertEquals(Phase.SECTION_PLC, f.visit.state().phase()); assertTrue(f.host.geometryReady());
        assertSame(terrain.terrain().layout().ram(), f.visit.state().layoutRam().orElseThrow());
        assertSame(terrain.blocksDecode().bytes(), f.visit.state().blocksRam().orElseThrow());
        assertEquals(CdR12bProfile.Asset.LEVEL_COLLISION.nativeAddress(), f.host.collisionPointer().orElseThrow());
        assertEquals(20, f.visit.completedSavedStores().size());
    }

    @Test void nativeClearCutsPreserveCarriedCheckpointScoreFlagsAndUnreachedTimeKnowledge() {
        for (int fade : new int[]{0, 1}) {
        var f = fixture(fade); var carried = f.host.globals(); var placement = f.host.placement(); var bank = placement.capture().savedFlags(); f.visit.begin(); until(f, Phase.FIRST_PLC); f.visit.service(8);
        var g = f.host.globals();
        assertSame(carried.checkpointSaved(), g.checkpointSaved()); assertSame(placement, f.host.placement());
        assertArrayEquals(bank.copy(), placement.capture().savedFlags().copy()); assertZeroPool(placement);
        assertEquals(carried.projectorByte(), g.projectorByte()); assertEquals(carried.score(), g.score());
        assertEquals(0, g.ring().timeStopTimer()); assertEquals(0, g.ring().ringAnimationTimer());
        assertEquals(0, g.ring().ringAnimationFrame()); assertEquals(30, g.drownTimerWord().orElseThrow());
        assertEquals(0, g.debugBlockWord().orElseThrow()); assertEquals(0, g.pauseMusicBit().orElseThrow());
        assertArrayEquals(new byte[7], g.paletteSteps7().copy()); assertArrayEquals(new byte[7], g.paletteTimers7().copy());
        assertEquals(new CdLevelHud.BonusValues(0, 0, 0), g.bonus().orElseThrow());
        assertTrue(f.host.completedClears().containsAll(Set.of(Action.CLEAR_DRAW_QUEUE, Action.CLEAR_FLOWERS,
                Action.CLEAR_OBJECTS, Action.CLEAR_UNKNOWN_BUFFER, Action.CLEAR_MISC, Action.CLEAR_CAMERA, Action.CLEAR_SCREEN)));
        assertEquals(0xF1234567L, Integer.toUnsignedLong(CdCheckpointLiveAux.read(g.live(), CdCheckpointLiveAux.Field.TIME_LONG)));
        byte[] water = f.visit.state().waterPalette().copy();
        assertArrayEquals(new byte[4], Arrays.copyOfRange(water, 0, 4));
        assertEquals(fade == 0 ? 0 : 0xEEE, f.visit.state().waterPalette().unsignedWord(4), "only the reached644-byte clear alias may change water colours");
        }

    }

    @Test void thirteenExternalTargetsPreserveUnsignedTimeAndWordCameraFractions() {
        var f = fixture(0); var before = f.host.scroll();
        var targets = new CdTimeWarpLoadData.StoreTarget[]{CdTimeWarpLoadData.StoreTarget.RINGS,
                CdTimeWarpLoadData.StoreTarget.LIVES_FLAGS, CdTimeWarpLoadData.StoreTarget.TIME_LONG,
                CdTimeWarpLoadData.StoreTarget.WATER_ROUTINE, CdTimeWarpLoadData.StoreTarget.DEST_BOTTOM_BOUND,
                CdTimeWarpLoadData.StoreTarget.FG_X, CdTimeWarpLoadData.StoreTarget.FG_Y,
                CdTimeWarpLoadData.StoreTarget.BG_X, CdTimeWarpLoadData.StoreTarget.BG_Y,
                CdTimeWarpLoadData.StoreTarget.BG2_X, CdTimeWarpLoadData.StoreTarget.BG2_Y,
                CdTimeWarpLoadData.StoreTarget.BG3_X, CdTimeWarpLoadData.StoreTarget.BG3_Y};
        for (var target : targets) {
            long value = target.width() == 4 ? 0xF2345678L : target.width() == 1 ? 0xA5 : 0xBEEF;
            var expected = f.host.readLoadTarget(target);
            var instruction = Arrays.stream(CdTimeWarpLoadData.NativeStore.values()).filter(s -> s.target() == target).findFirst().orElseThrow();
            f.host.storeLoadTarget(new CdTimeWarpLoadData.StoreCall(instruction, expected.isPresent()
                    ? OptionalInt.of((int) expected.getAsLong()) : OptionalInt.empty(), (int) value));
            assertEquals(value, f.host.readLoadTarget(target).orElseThrow());
        }
        var after = f.host.scroll(); assertEquals(before.foregroundXFractionWord(), after.foregroundXFractionWord());
        assertEquals(before.foreground().cameraY16_16() & 65535, after.foreground().cameraY16_16() & 65535);
        int[] oldLongs = bgLongs(before.background()), newLongs = bgLongs(after.background());
        for (int i = 0; i < 6; i++) assertEquals(oldLongs[i] & 65535, newLongs[i] & 65535);
        assertThrows(IllegalArgumentException.class, () -> f.host.readLoadTarget(CdTimeWarpLoadData.StoreTarget.PRIMARY_X));
    }

    @Test void unknownTimeReadStaysAbsentAndStoreCasRejectsStalePreimageWithoutWrites() {
        var f = fixture(0); assertTrue(f.host.readLoadTarget(CdTimeWarpLoadData.StoreTarget.TIME_LONG).isEmpty());
        Object before = f.host.capture();
        assertThrows(IllegalStateException.class, () -> f.host.storeLoadTarget(new CdTimeWarpLoadData.StoreCall(
                CdTimeWarpLoadData.NativeStore.RESTORE_TIME, OptionalInt.of(0), 0xF1234567)));
        assertEquals(before, f.host.capture()); assertTrue(f.host.readLoadTarget(CdTimeWarpLoadData.StoreTarget.TIME_LONG).isEmpty());
        f.host.storeLoadTarget(new CdTimeWarpLoadData.StoreCall(CdTimeWarpLoadData.NativeStore.RESTORE_TIME,
                OptionalInt.empty(), 0xF1234567));
        assertEquals(0xF1234567L, f.host.readLoadTarget(CdTimeWarpLoadData.StoreTarget.TIME_LONG).orElseThrow());
    }

    @Test void setupAndOneUsaPpz2ScrollEqualSharedAlgorithmsOverActualRestoredFrame() {
        var f = fixture(0); f.visit.begin(); f.visit.service(8);
        var m = f.frame.state().motion(); var env = f.frame.environment();
        var zero = new CdR11aForegroundScroll.State(0, 0, 16, 16, 0, 0, 0,
                0x480, terrain.loadPlan().bounds().left(), 0, 0);
        var setup = CdR11aForegroundScroll.checkpointSetupCamera(zero, m.centreX16_16() >>> 16,
                m.centreY16_16() >>> 16, env.movement().bounds());
        var input = new CdR11aForegroundScroll.Input(new CdR11aForegroundScroll.Actor(m.centreX16_16() >>> 16,
                m.centreY16_16() >>> 16, m.flags(), m.groundVelocity(), m.animation(), m.playerControl()),
                env.movement().bounds(), terrain.loadPlan().bounds().top(), env.globals().cameraXCentre(),
                env.movement().globals().cameraYCentre(), f.frame.stageSource().variant(), f.visit.state().level().updateHudTime(), 320, 224);
        var expected = CdR11aForegroundScroll.stepForPeriodBackground(setup, input, 0x80, CdR11aForegroundScroll.EventRoutine.PPZ2).state();
        assertEquals(expected.cameraX(), f.host.scroll().foreground().cameraX());
        assertEquals(expected.cameraY16_16(), f.host.scroll().foreground().cameraY16_16());
        assertEquals(expected.scrollFlags() | 0x400, f.host.scroll().foreground().scrollFlags());
        assertNotEquals(0, f.host.scroll().background().deformationRam().unsignedLong(0));
    }

    @Test void nonzeroPreloadDrawFlagStopsAfterNativeCopyBeforeDetachedGeometryFallback() throws Exception {
        var f = fixture(0); var old = f.initial.scroll(); var fg = old.foreground();
        var changed = new CdR11aForegroundScroll.State(fg.cameraX(), fg.cameraY16_16(), fg.horizontalCrossed(), fg.verticalCrossed(),
                fg.scrollXDiff(), fg.scrollYDiff(), 0x400, fg.destBottomBound(), fg.destLeftBound(), fg.bottomBoundShift(), fg.scrollLock());
        var host = new SonicCdAct2PastWorldHost(f.module, terrain, player, f.frame,
                new Initial(f.initial.globals(), new Scroll(changed, old.background(), old.foregroundPlane(), old.backgroundPlane(), old.copies(), old.foregroundXFractionWord()), f.initial.video(), f.initial.savedFlags()));
        var visit = new SonicCdAct2PastLoadingVisit(host, terrain, player, f.frame, f.art, f.entry);
        host.validate(visit, f.frame, terrain, player); visit.begin();
        var stopped = assertThrows(HostBoundary.class, () -> visit.service(8));
        assertEquals(SonicCdAct2PastWorldHost.Boundary.GEOMETRY_NOT_LOADED, stopped.boundary());
        var prefix = (Scroll) field(field(host, "image"), "scroll");
        assertEquals(0x400, prefix.copies().fgFlags()); assertEquals(false, field(field(host, "image"), "geometryReady"));
        assertTrue(visit.failed()); assertTrue(f.frame.closed());
    }

    @Test void currentArtAdoptionDoesNotRepublishOrReissueCueAndPlanesUseRealSource() {
        var f = fixture(0); f.visit.begin(); f.visit.service(8);
        assertSame(f.visit.state().section().residency(), f.host.video().current());
        var selected = ((Published) f.host.selected()).selection().inputs();
        assertNotSame(selected.current(), f.host.video().current(), "publication preceded genuine immediate/current writes");
        assertTrue(f.visit.state().calls().contains(Call.CURRENT_ART));
        var planes = f.host.scroll(); var live = live(planes.background());
        var directBg = CdR11aBackgroundPlane.bindAct2Past(terrain).initialize(
                new CdR11aBackgroundPlane.State(bytes(4096, 0)), live, flags(planes.background())).state();
        assertArrayEquals(directBg.nameTable().copy(), planes.backgroundPlane().nameTable().copy());
        assertTrue(Arrays.stream(words(planes.foregroundPlane())).anyMatch(w -> w != 0));
    }

    @Test void finalLoadOwnsOnlyReservedHudAndStopsBeforeOrdinarySelectorEight() {
        var f = fixture(0); f.visit.begin(); until(f, Phase.BEGIN_LEVEL);
        assertEquals(1, f.frame.state().header().slotId()); assertEquals(0, f.frame.state().motion().routine());
        assertEquals(0x80, f.host.globals().ring().updateHudRings()); assertEquals(1, f.host.globals().ring().updateHudLives());
        assertEquals(0x80, f.host.globals().score().updateHudScore()); assertEquals(new CdPlayerBody.ContactGlobals(0, 0, 0x45), f.host.globals().contact());
        assertEquals(0x003F, f.host.globals().tail().orElseThrow().paletteFadeInfoWord());
        assertEquals(1, f.host.sounds().size()); assertEquals(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_PASTMUS)), f.host.sounds().getFirst());
        var before = f.host.capture(); var clock = f.frame.vIntClock();
        assertEquals(SonicCdAct2PastLoadingVisit.Boundary.ORDINARY_OBJECT_PASS,
                assertThrows(UnsupportedLoading.class, () -> f.visit.service(8)).boundary());
        assertEquals(before, f.host.capture()); assertSame(clock, f.frame.vIntClock());
    }

    @Test void fromWhitePublishesBeforeColoursAndEnableChangesOnlySelectedGate() {
        var f = fixture(0); f.visit.begin(); until(f, Phase.FROM_WHITE);
        assertFalse(((Published) f.host.selected()).selection().inputs().displayEnabled());
        var cpu = f.visit.state().normalPalette(); var h = f.host.scroll().background().hscrollRam(); f.visit.service(0);
        var selected = ((Published) f.host.selected()).selection().inputs();
        assertTrue(selected.displayEnabled()); assertSame(cpu, selected.palette());
        assertSame(h, selected.hscroll()); assertNotSame(cpu, f.visit.state().normalPalette());
        assertSame(f.host.video().current(), selected.current());
        assertEquals(1, f.visit.state().fromWhiteSteps());
    }

    @Test void coreSnapshotContainsOneHostParticipantAndRestoreReplaysNoActionsMusicOrPlc() {
        var f = fixture(0); f.visit.begin(); until(f, Phase.FROM_WHITE); var snapshot = f.visit.capture();
        var host = f.host.capture(); var clock = f.frame.vIntClock(); var selected = f.host.selected();
        f.visit.service(8); f.visit.restore(snapshot);
        assertEquals(host, f.host.capture()); assertSame(snapshotImage(host), snapshotImage(f.host.capture())); assertSame(selected, f.host.selected()); assertEquals(clock, f.frame.vIntClock());
        assertEquals(1, f.host.sounds().size());
        var foreign = fixture(0); assertThrows(IllegalArgumentException.class, () -> foreign.host.validateRestore(host));
        assertThrows(IllegalArgumentException.class, () -> f.host.restore(new Object()));
    }

    @Test void scrollHandlerAndCameraProjectionArePureAndExposeNoSecondRewindBank() {
        var f = fixture(0); f.visit.begin(); f.visit.service(8); Object before = f.host.capture();
        var handler = f.host.handler(); int[] projected = new int[256]; handler.update(projected, 999, -99, 999999, 1);
        assertArrayEquals(longs(f.host.scroll().background().hscrollRam()), projected);
        var camera = mock(Camera.class); f.host.projectCamera(camera);
        verify(camera).setX((short) f.host.scroll().foreground().cameraX()); verify(camera).setY((short) f.host.scroll().foreground().cameraYWord());
        var token = handler.captureRewindState(); assertEquals(token, handler.captureRewindState()); handler.restoreRewindState(token);
        assertThrows(IllegalArgumentException.class, () -> handler.restoreRewindState(new Object()));
        var foreign = fixture(0); assertThrows(IllegalArgumentException.class, () -> handler.restoreRewindState(foreign.host.handler().captureRewindState()));
        assertEquals(before, f.host.capture());
        assertThrows(IllegalArgumentException.class, () -> handler.update(new int[257], 0, 0, 0, 1));
    }

    @Test void closeIsIdleFirstOnceOnlyAndRetiresLogicWithoutClosingTheFrame() throws Exception {
        var f = fixture(0); var drawing = f.host.getClass().getDeclaredField("drawing"); drawing.setAccessible(true); drawing.setBoolean(f.host, true);
        var before = field(f.host, "image"); clearInvocations(f.module);
        assertThrows(IllegalStateException.class, f.host::capture); assertThrows(IllegalStateException.class, f.host::placement); assertThrows(IllegalStateException.class, f.host::close);
        assertSame(before, field(f.host, "image")); assertThrows(IllegalStateException.class, f.host::requireRenderIdle); verifyNoInteractions(f.module);
        drawing.setBoolean(f.host, false); f.host.close(); f.host.close(); assertNull(field(f.host, "render"));
        assertFalse(f.frame.closed()); assertThrows(IllegalStateException.class, f.host::capture); assertThrows(IllegalStateException.class, f.host::placement);
        assertThrows(IllegalStateException.class, f.host::draw); assertThrows(IllegalStateException.class, f.visit::begin);
    }

    @Test void initialRequiresAll766CarriedBytesAndGlobalsHasNoSavedBankMirror() {
        var f = fixture(0);
        assertEquals(14, Globals.class.getRecordComponents().length);
        assertEquals(4, Initial.class.getRecordComponents().length);
        var owners = Arrays.stream(SonicCdAct2PastWorldHost.class.getDeclaredFields())
                .filter(fld -> fld.getType() == CdObjectPlacementRuntime.class).toList();
        assertEquals(1, owners.size()); assertTrue(Modifier.isFinal(owners.getFirst().getModifiers()));
        assertEquals(2, f.host.capture().getClass().getRecordComponents().length);
        assertTrue(Modifier.isPrivate(f.host.capture().getClass().getModifiers()));
        assertTrue(Arrays.stream(Globals.class.getRecordComponents()).noneMatch(c -> c.getName().equals("savedFlags")));
        assertTrue(Arrays.stream(snapshotImage(f.host.capture()).getClass().getRecordComponents())
                .noneMatch(c -> c.getName().equals("savedFlags") || c.getName().equals("placement")
                        || c.getType() == CdObjectPlacementRuntime.Snapshot.class || c.getType() == CdObjectPool.Snapshot.class));
        for (int length : new int[]{0, 765, 767}) assertThrows(IllegalArgumentException.class,
                () -> new Initial(f.initial.globals(), f.initial.scroll(), f.initial.video(), bytes(length, 0)));
        assertThrows(NullPointerException.class,
                () -> new Initial(f.initial.globals(), f.initial.scroll(), f.initial.video(), null));
        assertArrayEquals(f.initial.savedFlags().copy(), f.host.placement().capture().savedFlags().copy());
    }

    @Test void oneActualPlacementRetainsTheExactCarriedBankAndRunMmdCleared96SlotPool() {
        var carried = patternedBank(); var f = fixture(0, carried); var placement = f.host.placement();
        assertSame(placement, f.host.placement());
        assertSame(terrain.placements(), placement.placements());
        assertArrayEquals(carried.copy(), placement.capture().savedFlags().copy());
        assertNotSame(carried, placement.capture().savedFlags());
        assertEquals(0xA5, placement.capture().savedFlags().unsignedByte(0));
        assertEquals(0x5A, placement.capture().savedFlags().unsignedByte(1));
        assertEquals(0, placement.status().routine()); assertEquals(0, placement.status().rightRecordIndex());
        assertEquals(0, placement.status().leftRecordIndex()); assertZeroPool(placement);
        assertEquals(Phase.ENTRY, f.visit.state().phase()); assertTrue(f.host.completedClears().isEmpty());
        assertTrue(f.host.sounds().isEmpty()); assertNullRender(f.host);
        // Metadata plus a constructed clear ingress is not actual World/Spawn admission.
    }

    @Test void independentNoWriteCapturesHaveSemanticEqualityAndMatchingHashCodes() {
        var f = fixture(0, patternedBank()); var a = f.host.capture(); var b = f.host.capture();
        assertNotSame(a, b); assertNotSame(placementImage(a), placementImage(b));
        assertNotSame(placementImage(a).savedFlags(), placementImage(b).savedFlags());
        assertEquals(a, b); assertEquals(b, a); assertEquals(a.hashCode(), b.hashCode());
        assertSame(snapshotImage(a), snapshotImage(b));
        var current = f.host.placement().pool().state(127);
        f.host.placement().pool().write(current.moveByte(CdObjectState.ByteField.ROUTINE, current.routine()));
        var equalImage = f.host.capture(); assertEquals(a, equalImage); assertEquals(a.hashCode(), equalImage.hashCode());
        assertEquals(1, new HashSet<>(List.of(a, b, equalImage)).size());
        assertNotEquals(a, fixture(0, patternedBank()).host.capture());
    }

    @Test void bankOnlyChangesAreObservedAndRestoreRecoversEveryCarriedByteWithoutInit() {
        var f = fixture(0, patternedBank()); var placement = f.host.placement(); var before = f.host.capture();
        var exact = placementImage(before).savedFlags().copy(); var status = placement.status();
        placement.resetSavedObjectFlags(); // Explicit component negative, not a native Host action.
        var changed = f.host.capture(); assertNotEquals(before, changed);
        var resetStatus=new CdObjectPlacementRuntime.Status(status.routine(),status.previousChunkWord(),status.rightRecordIndex(),status.leftRecordIndex(),
                1,1,status.nullRightByteOffset(),status.nullLeftByteOffset());
        assertEquals(resetStatus,placement.status());byte[] resetBank=new byte[766];resetBank[0]=resetBank[1]=1;
        assertArrayEquals(resetBank,placement.capture().savedFlags().copy());assertZeroPool(placement);
        f.host.validateRestore(before); f.host.restore(before);
        assertSame(placement, f.host.placement()); assertEquals(before, f.host.capture());
        assertArrayEquals(exact, placement.capture().savedFlags().copy()); assertEquals(status, placement.status());
        assertZeroPool(placement); assertTrue(f.host.sounds().isEmpty()); assertTrue(f.host.completedClears().isEmpty());
        assertEquals(Phase.ENTRY, f.visit.state().phase());
        // The final valid saved byte is observed too, independently of headers and other flags.
        int raw = placement.savedFlag(254, 1);
        if ((raw & 0x80) == 0) placement.bsetSavedFlag(254, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
        else placement.bclrSavedFlag(254, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
        byte[] expected = exact.clone(); expected[765] ^= (byte)0x80;
        assertArrayEquals(expected, placement.capture().savedFlags().copy()); assertNotEquals(before, f.host.capture());
        f.host.restore(before); assertArrayEquals(exact, placement.capture().savedFlags().copy());
    }

    @Test void snapshotObservesAll64BytesAndRestoresAll96OwnerBoundSlotImages() {
        var f = fixture(0, patternedBank()); var placement = f.host.placement(); var zero = f.host.capture();
        var base = placement.pool().state(127);
        for (int offset = 0; offset < 64; offset++) {
            byte[] raw = base.copyBytes(); raw[offset] = (byte)(offset + 1);
            placement.pool().write(componentSlot(base, raw));
            assertNotEquals(zero, f.host.capture(), "unwritten byte offset " + offset + " must participate");
        }
        for (int whole = 32; whole < 128; whole++) {
            var slot = placement.pool().state(whole); byte[] raw = new byte[64];
            for (int i = 0; i < 64; i++) raw[i] = (byte)(whole * 13 + i * 7);
            placement.pool().write(componentSlot(slot, raw));
        }
        var dirty = f.host.capture(); assertNotEquals(zero, dirty);
        var expected = placementImage(dirty).pool().slots(); f.host.restore(zero); assertZeroPool(placement);
        f.host.restore(dirty); assertSame(placement, f.host.placement());
        assertEquals(expected, placement.capture().pool().slots()); assertEquals(dirty, f.host.capture());
        assertArrayEquals(f.initial.savedFlags().copy(), placement.capture().savedFlags().copy());
        assertTrue(f.host.sounds().isEmpty()); assertEquals(Phase.ENTRY, f.visit.state().phase());
    }

    @Test void foreignPlacementPreflightRejectsBeforeEitherCapturedPartitionIsAdopted() {
        var f = fixture(0); var other = fixture(0); var old = f.host.capture();
        f.host.storeLoadTarget(new CdTimeWarpLoadData.StoreCall(CdTimeWarpLoadData.NativeStore.RESTORE_TIME,
                OptionalInt.empty(), 0xE1234567));
        f.host.placement().pool().write(f.host.placement().pool().state(65).moveByte(CdObjectState.ByteField.ID, 0x19));
        var before = f.host.capture(); var mixed = mixedSnapshot(old, other.host.placement().capture());
        assertThrows(IllegalArgumentException.class, () -> f.host.validateRestore(mixed));
        assertThrows(IllegalArgumentException.class, () -> f.host.restore(mixed));
        assertEquals(before, f.host.capture()); assertSame(snapshotImage(before), snapshotImage(f.host.capture()));
        assertEquals(0xE1234567L, f.host.readLoadTarget(CdTimeWarpLoadData.StoreTarget.TIME_LONG).orElseThrow());
        assertEquals(0x19, f.host.placement().pool().state(65).id());
    }

    @Test void foreignImagePreflightRejectsEvenWhenThePlacementCaptureIsLocal() {
        var f = fixture(0); var other = fixture(0); var before = f.host.capture();
        var mixed = mixedSnapshot(other.host.capture(), f.host.placement().capture());
        assertThrows(IllegalArgumentException.class, () -> f.host.validateRestore(mixed));
        assertThrows(IllegalArgumentException.class, () -> f.host.restore(mixed));
        assertEquals(before, f.host.capture()); assertSame(snapshotImage(before), snapshotImage(f.host.capture()));
        assertZeroPool(f.host.placement());
    }

    @Test void coreRestoreUsesTheSamePlacementParticipantForBothFadeHistoriesWithoutReplay() {
        for (int fade : new int[]{0, 1}) {
            var f = fixture(fade, patternedBank()); f.visit.begin(); until(f, Phase.FROM_WHITE);
            var placement = f.host.placement(); var host = f.host.capture(); var core = f.visit.capture();
            var clock = f.frame.vIntClock(); var calls = f.visit.state().calls(); var selected = f.host.selected();
            placement.resetSavedObjectFlags();
            placement.pool().write(placement.pool().state(127).moveLong(CdObjectState.LongField.X_POSITION, 0xFEDCBA98)
                    .moveByte(CdObjectState.ByteField.ROUTINE, 0x44));
            f.visit.service(8); f.host.takeSounds(); f.visit.restore(core);
            assertSame(placement, f.host.placement()); assertEquals(host, f.host.capture());
            assertArrayEquals(placementImage(host).savedFlags().copy(), placement.capture().savedFlags().copy());
            assertEquals(placementImage(host).status(), placement.status()); assertZeroPool(placement);
            assertSame(selected, f.host.selected()); assertEquals(clock, f.frame.vIntClock());
            assertEquals(calls, f.visit.state().calls()); assertEquals(1, f.host.sounds().size());
        }
    }

    @Test void ordinarySelectionBorrowsExactCpuNormalPaletteAndOnlyTheNativeHscrollPrefix() {
        byte[] selectedScroll = new byte[1024]; Arrays.fill(selectedScroll, (byte)0x6D);
        var f = fixture(0, patternedBank(), CdAssetBytes.copyOf(selectedScroll));
        var cpu = Optional.of(completedEmptyCpu(0x35)); var prepared = f.host.prepareOrdinary(f.host.globals(), f.host.video(), f.host.globals(), List.of(), cpu);
        f.host.adoptOrdinary(prepared); var beforeVideo = f.host.video(); var beforeScroll = f.host.scroll(); var palette = f.visit.state().normalPalette();
        f.host.publishOrdinary(palette, cpu); var selected = ((Published)f.host.selected()).selection().inputs();
        assertSame(cpu, selected.selectedCpu()); assertSame(beforeVideo.cpuSat(), selected.selectedSat()); assertSame(palette, selected.palette());
        byte[] expected = selectedScroll.clone(); System.arraycopy(beforeScroll.background().hscrollRam().copy(), 0, expected, 0, 0x380);
        assertArrayEquals(expected, selected.hscroll().copy()); assertEquals(beforeScroll.background().vscroll(), selected.vscrollLongBits());
        assertSame(beforeScroll.foregroundPlane(), selected.foreground()); assertSame(beforeScroll.backgroundPlane(), selected.background());
        assertSame(cpu, f.host.video().completedCpu()); assertArrayEquals(beforeVideo.cpuSat().copy(), f.host.video().cpuSat().copy());
        // This mock-authorized component cut proves publication semantics, not an actual ordinary World service.
    }

    @Test void ordinaryPublicationRejectsEqualButUnborrowedCpuOptionalAndPaletteBeforeWrites() {
        var f = fixture(0); var cpu = Optional.of(completedEmptyCpu(0x36));
        f.host.adoptOrdinary(f.host.prepareOrdinary(f.host.globals(), f.host.video(), f.host.globals(), List.of(), cpu));
        var before = f.host.capture(); var normal = f.visit.state().normalPalette();
        assertThrows(IllegalArgumentException.class, () -> f.host.publishOrdinary(normal, Optional.of(cpu.orElseThrow())));
        assertThrows(IllegalArgumentException.class, () -> f.host.publishOrdinary(CdAssetBytes.copyOf(normal.copy()), cpu));
        assertEquals(before, f.host.capture()); assertFalse(f.frame.closed());
        f.host.publishOrdinary(normal, cpu); before = f.host.capture();
        assertThrows(IllegalArgumentException.class, () -> f.host.settleOrdinary(Optional.of(cpu.orElseThrow())));
        assertEquals(before, f.host.capture());
    }

    @Test void ordinarySettleRetainsThePublishedCpuEvenWhenTheNextCpuWasAlreadyAdopted() {
        var f = fixture(0); f.visit.begin(); until(f, Phase.BEGIN_LEVEL);
        var first = Optional.of(completedEmptyCpu(0x37)); f.host.adoptOrdinary(f.host.prepareOrdinary(f.host.globals(), f.host.video(), f.host.globals(), List.of(), first));
        f.host.publishOrdinary(f.visit.state().normalPalette(), first); var selected = ((Published)f.host.selected()).selection().inputs();
        var second = Optional.of(completedEmptyCpu(0x38)); f.host.adoptOrdinary(f.host.prepareOrdinary(f.host.globals(), f.host.video(), f.host.globals(), List.of(), second));
        var actualCpu = f.host.video().cpuSat(); f.host.ordinaryCurrent(f.visit.state().section()); f.host.settleOrdinary(first);
        var settled = ((Published)f.host.selected()).selection().inputs();
        assertSame(first, settled.selectedCpu()); assertSame(selected.selectedSat(), settled.selectedSat());
        assertSame(selected.palette(), settled.palette()); assertSame(selected.hscroll(), settled.hscroll()); assertEquals(selected.vscrollLongBits(), settled.vscrollLongBits());
        assertSame(second, f.host.video().completedCpu()); assertSame(actualCpu, f.host.video().cpuSat());
        assertSame(f.host.scroll().foregroundPlane(), settled.foreground()); assertSame(f.host.scroll().backgroundPlane(), settled.background());
        assertSame(f.visit.state().section().residency(), settled.current()); assertSame(f.frame.artState(), settled.player());
    }

    @Test void ordinaryCurrentRequiresTheExactCoreSectionAndCreatesNoFrameOrPlacementOwner() {
        var f = fixture(0); f.visit.begin(); until(f, Phase.BEGIN_LEVEL); var section = f.visit.state().section(); var before = f.host.capture();
        var equivalent = f.art.withSectionWord(f.art.withSectionWord(section, section.sectionWord() ^ 1), section.sectionWord()); assertNotSame(section, equivalent);
        assertThrows(IllegalArgumentException.class, () -> f.host.ordinaryCurrent(equivalent)); assertEquals(before, f.host.capture());
        var placement = f.host.placement(); var actor = f.frame.captureActor(); var shared = f.frame.captureShared(); var cpu = f.host.video().completedCpu();
        f.host.ordinaryCurrent(section); assertSame(section.residency(), f.host.video().current()); assertSame(f.frame.artState(), f.host.video().playerArt());
        assertSame(cpu, f.host.video().completedCpu()); assertSame(placement, f.host.placement());
        assertEquals(actor, f.frame.captureActor()); assertEquals(shared, f.frame.captureShared());
    }

    @Test void ordinaryScrollUsesActualSourceKernelsWithoutTheLoadingOnlyForcedColumn() {
        var f = fixture(0); f.visit.begin(); until(f, Phase.BEGIN_LEVEL); var before = f.host.scroll(); var m = f.frame.state().motion(); var env = f.frame.environment();
        var input = new CdR11aForegroundScroll.Input(new CdR11aForegroundScroll.Actor(m.centreX16_16() >>> 16, m.centreY16_16() >>> 16,
                m.flags(), m.groundVelocity(), m.animation(), m.playerControl()), env.movement().bounds(), terrain.loadPlan().bounds().top(),
                env.globals().cameraXCentre(), env.movement().globals().cameraYCentre(), f.frame.stageSource().variant(), 0, 320, 224);
        var expected = CdR11aForegroundScroll.stepForPeriodBackground(before.foreground(), input, env.movement().gates().timeZone(), CdR11aForegroundScroll.EventRoutine.PPZ2);
        var fg = expected.state(); var bg = CdR12bBackgroundScroll.bind(terrain).step(before.background(), new CdR12bBackgroundScroll.Input(
                fg.cameraX(), fg.cameraYWord(), (short)fg.scrollXDiff(), (short)fg.scrollYDiff(), fg.scrollLock() >>> 8));
        var clock = f.frame.vIntClock(); var selected = f.host.selected(); var cpu = f.host.video().completedCpu();
        f.host.scrollOrdinary(0); assertEquals(fg, f.host.scroll().foreground());
        assertBackground(bg, f.host.scroll().background()); assertSame(before.foregroundPlane(), f.host.scroll().foregroundPlane());
        assertSame(before.backgroundPlane(), f.host.scroll().backgroundPlane()); assertSame(before.copies(), f.host.scroll().copies());
        assertEquals(expected.bounds(), f.frame.environment().movement().bounds()); assertEquals(expected.cameraYCentre(), f.frame.environment().movement().globals().cameraYCentre());
        assertSame(selected, f.host.selected()); assertSame(cpu, f.host.video().completedCpu()); assertEquals(clock, f.frame.vIntClock());
    }

    @Test void ordinaryPlaneCopyIsSeparateFromPostObjectLogicalScrollAndKeepsSelectedInputs() {
        var f = fixture(0); f.visit.begin(); until(f, Phase.BEGIN_LEVEL); var cpu = f.host.video().completedCpu();
        f.host.publishOrdinary(f.visit.state().normalPalette(), cpu); var selected = ((Published)f.host.selected()).selection().inputs();
        f.host.scrollOrdinary(0); var logical = f.host.scroll(); var actor = f.frame.captureActor(); var shared = f.frame.captureShared();
        f.host.copyAndDrawOrdinary(); var copied = f.host.scroll().copies();
        assertEquals((logical.foreground().cameraX() << 16) | logical.foregroundXFractionWord(), copied.fgX16_16());
        assertEquals(logical.foreground().cameraY16_16(), copied.fgY16_16()); assertEquals(live(logical.background()).bg1(), copied.bg().bg1());
        assertSame(selected, ((Published)f.host.selected()).selection().inputs()); assertSame(cpu, f.host.video().completedCpu());
        assertEquals(actor, f.frame.captureActor()); assertEquals(shared, f.frame.captureShared());
        f.host.settleOrdinary(cpu); var settled = ((Published)f.host.selected()).selection().inputs();
        assertSame(selected.hscroll(), settled.hscroll()); assertEquals(selected.vscrollLongBits(), settled.vscrollLongBits());
        assertSame(f.host.scroll().foregroundPlane(), settled.foreground()); assertSame(f.host.scroll().backgroundPlane(), settled.background());
    }

    @Test void preparedAdoptionBuildsAllReferencesBeforeCommittingGlobalsSoundsAndCpuTogether() throws Exception {
        var f = fixture(0, patternedBank()); var before = f.host.capture(); var g = f.host.globals(); var v = f.host.video(); var next = globalsWithRings(g, 0x1234);
        var appended = new ArrayList<SonicCdGameModule.PastSound>();
        appended.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_FADECDA)));
        appended.add(new SonicCdGameModule.PastTimePostSubCpu(CdTimePost.SubCpuCommand.SCMD_PASTSFX));
        var expectedSounds = List.copyOf(appended); var cpu = Optional.of(completedEmptyCpu(0x39)); var actor = f.frame.captureActor(); var shared = f.frame.captureShared();
        var prepared = f.host.prepareOrdinary(g, v, next, appended, cpu); appended.clear();
        assertEquals(before, f.host.capture()); assertEquals(actor, f.frame.captureActor()); assertEquals(shared, f.frame.captureShared());
        var ready = field(prepared, "after"); f.host.validateOrdinary(prepared); f.host.adoptOrdinary(prepared);
        assertSame(ready, field(f.host, "image")); assertSame(next, f.host.globals()); assertEquals(expectedSounds, f.host.sounds());
        assertSame(cpu, f.host.video().completedCpu()); assertArrayEquals(cpu.orElseThrow().bytes(), f.host.video().cpuSat().copy());
        assertSame(v.selected(), f.host.selected()); assertSame(v.current(), f.host.video().current());
        assertArrayEquals(placementImage(before).savedFlags().copy(), f.host.placement().capture().savedFlags().copy());
        assertEquals(placementImage(before).pool().slots(), f.host.placement().capture().pool().slots());
    }

    @Test void preparedCasRequiresExactGlobalsVideoAndTheWholeOriginalHostImage() {
        var f = fixture(0); var g = f.host.globals(); var v = f.host.video(); var before = f.host.capture();
        assertThrows(IllegalStateException.class, () -> f.host.prepareOrdinary(globalsWithRings(g, g.ring().rings()), v, g, List.of(), v.completedCpu()));
        assertThrows(IllegalStateException.class, () -> f.host.prepareOrdinary(g, new Video(v.cpuSat(), v.completedCpu(), v.playerArt(), v.current(), v.selected()), g, List.of(), v.completedCpu()));
        assertEquals(before, f.host.capture()); var prepared = f.host.prepareOrdinary(g, v, g, List.of(), v.completedCpu());
        var nativeStore = Arrays.stream(CdTimeWarpLoadData.NativeStore.values()).filter(s -> s.target() == CdTimeWarpLoadData.StoreTarget.FG_X).findFirst().orElseThrow();
        f.host.storeLoadTarget(new CdTimeWarpLoadData.StoreCall(nativeStore, OptionalInt.of((int)f.host.readLoadTarget(nativeStore.target()).orElseThrow()), 0x1234));
        assertSame(g, f.host.globals()); assertSame(v, f.host.video()); var changed = f.host.capture();
        assertThrows(IllegalStateException.class, () -> f.host.validateOrdinary(prepared)); assertThrows(IllegalStateException.class, () -> f.host.adoptOrdinary(prepared));
        assertEquals(changed, f.host.capture()); assertFalse(f.frame.closed());
    }

    @Test void preparedAdoptionRejectsForeignAndAlreadyConsumedImagesWithoutWrites() {
        var f = fixture(0); var other = fixture(0); var before = f.host.capture();
        var foreign = other.host.prepareOrdinary(other.host.globals(), other.host.video(), other.host.globals(), List.of(), other.host.video().completedCpu());
        assertThrows(IllegalStateException.class, () -> f.host.adoptOrdinary(foreign)); assertEquals(before, f.host.capture());
        var own = f.host.prepareOrdinary(f.host.globals(), f.host.video(), f.host.globals(), List.of(), f.host.video().completedCpu()); f.host.adoptOrdinary(own);
        var after = f.host.capture(); assertThrows(IllegalStateException.class, () -> f.host.adoptOrdinary(own)); assertEquals(after, f.host.capture());
    }

    @Test void preparedInputsAndCpuProvenanceCannotBeDroppedOrPartiallyAdopted() {
        var f = fixture(0); var cpu = Optional.of(completedEmptyCpu(0x3A)); f.host.adoptOrdinary(f.host.prepareOrdinary(f.host.globals(), f.host.video(), f.host.globals(), List.of(), cpu));
        var before = f.host.capture(); var g = f.host.globals(); var v = f.host.video();
        assertThrows(IllegalArgumentException.class, () -> f.host.prepareOrdinary(g, v, g, List.of(), Optional.empty()));
        assertThrows(NullPointerException.class, () -> f.host.prepareOrdinary(g, v, null, List.of(), cpu));
        assertThrows(NullPointerException.class, () -> f.host.prepareOrdinary(g, v, g, null, cpu));
        assertThrows(NullPointerException.class, () -> f.host.prepareOrdinary(g, v, g, List.of(), null));
        var nullSound = new ArrayList<SonicCdGameModule.PastSound>(); nullSound.add(null);
        assertThrows(NullPointerException.class, () -> f.host.prepareOrdinary(g, v, g, nullSound, cpu));
        assertEquals(before, f.host.capture()); assertFalse(f.frame.closed());
    }

    @Test void theSinglePastSoundLedgerKeepsLoadingDynamicAndFrameOrderAcrossTakeAndRestore() {
        var f = fixture(0); f.visit.begin(); until(f, Phase.BEGIN_LEVEL); var initial = List.copyOf(f.host.sounds());
        var dynamic = new SonicCdGameModule.PastTimePostSubCpu(CdTimePost.SubCpuCommand.SCMD_PASTSFX);
        var frame = new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_FADECDA));
        f.host.adoptOrdinary(f.host.prepareOrdinary(f.host.globals(), f.host.video(), f.host.globals(), List.of(dynamic, frame), f.host.video().completedCpu()));
        var expected = new ArrayList<SonicCdGameModule.PastSound>(initial); expected.add(dynamic); expected.add(frame);
        var captured = f.host.capture(); assertEquals(expected, f.host.takeSounds()); assertTrue(f.host.sounds().isEmpty());
        f.host.restore(captured); assertEquals(expected, f.host.sounds()); assertEquals(expected, f.host.takeSounds()); assertTrue(f.host.sounds().isEmpty());
    }

    @Test void ordinaryRenderAndOperationGuardsRejectBeforeAnyCatchOrHealthyFrameMutation() throws Exception {
        var f = fixture(0); var normal = f.visit.state().normalPalette(); var cpu = f.host.video().completedCpu();
        var prepared = f.host.prepareOrdinary(f.host.globals(), f.host.video(), f.host.globals(), List.of(), cpu);
        var before = f.host.capture(); var image = field(f.host, "image"); var actor = f.frame.captureActor(); var shared = f.frame.captureShared();
        for (String flag : new String[]{"drawing", "acting", "publishing"}) {
            var active = f.host.getClass().getDeclaredField(flag); active.setAccessible(true); active.setBoolean(f.host, true);
            assertThrows(IllegalStateException.class, () -> f.host.publishOrdinary(normal, cpu));
            assertThrows(IllegalStateException.class, () -> f.host.settleOrdinary(cpu));
            assertThrows(IllegalStateException.class, () -> f.host.ordinaryCurrent(f.visit.state().section()));
            assertThrows(IllegalStateException.class, () -> f.host.scrollOrdinary(0)); assertThrows(IllegalStateException.class, f.host::copyAndDrawOrdinary);
            assertThrows(IllegalStateException.class, () -> f.host.prepareOrdinary(f.initial.globals(), f.initial.video(), f.initial.globals(), List.of(), cpu));
            assertThrows(IllegalStateException.class, () -> f.host.validateOrdinary(prepared)); assertThrows(IllegalStateException.class, () -> f.host.adoptOrdinary(prepared));
            assertSame(image, field(f.host, "image")); assertFalse(f.frame.closed()); active.setBoolean(f.host, false);
        }
        assertEquals(before, f.host.capture()); assertEquals(actor, f.frame.captureActor()); assertEquals(shared, f.frame.captureShared());
    }

    private record Fixture(SonicCdGameModule module, SonicCdAct2PastWorldHost host, CdPlayerFrameRuntime frame,
                           CdR12bSectionArt art, Entry entry, Initial initial, SonicCdAct2PastLoadingVisit visit) { }
    private static Fixture fixture(int fadeBit) { return fixture(fadeBit, bytes(CdObjectPlacementRuntime.SAVED_BANK_BYTES, 0x56)); }
    private static Fixture fixture(int fadeBit, CdAssetBytes bank) { return fixture(fadeBit, bank, null); }
    private static Fixture fixture(int fadeBit, CdAssetBytes bank, CdAssetBytes selectedHscroll) {
        var frame = CdPlayerFrameRuntime.fromAct2PastEntry(terrain, player, zeroActor(), environment(),
                new CdPlayerArtRuntime.State(new byte[768], new byte[768], 0, false),
                new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(), 17));
        var art = CdR12bSectionArt.fromAssets(terrain);
        // Constructed InitVDP projection, not arbitrary patterned pixels or a source-load shortcut.
        var current = CdR12bCameraArtResidency.clear(CdR12bCameraArtResidency.unknown());
        var section = art.fromValues(bytes(512, 0), current, 0x1234, CdR11aPlcBuffer.fromBytes(bytes(96, 0)), Optional.empty(),
                new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.CLEARED, 0, 0, 0, 0, 0), 0, 0, 0, 0);
        var entry = new Entry(saved(), section, new Program(0, 0x7F, 0x34, 0, 0, fadeBit,
                CdProgramInitialization.KnownBit.UNKNOWN, 0, 0, 0),
                new CdLevelGlobals(0, 0, 0x56, 2, 3, 0x123456, 0x78, 0, 0),
                bytes(128, 0), bytes(128, 0), bytes(128, 0), bytes(128, 0), bytes(1984, 0), 22, false);
        var g = new Globals(new CdRingGlobals(0x3456, 3, 0x56, 0x23, 0x34, 0x5678, 0x45, 0x56),
                CdCheckpointLiveAux.unknown(), new CdPlayerBody.ContactGlobals(0x23, 0x34, 0x45),
                new CdOpeningObjectRuntime.ScoreGlobals(0x12345678, 0x3456789A, 0x67),
                0x45, CdCheckpointSaveData.State.unknown(),
                OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                bytes(7, 0x34), bytes(7, 0x45));
        var b = new CdR11aBackgroundScroll.State(0x1234, 0x2345, 0x3456, 0x4567, 0x5678, 0x6789, bytes(8, 0), 0, 0, 0,
                bytes(512, 0x23), bytes(512, 0), bytes(1024, 0), 0);
        var plane = new CdR11aBackgroundPlane.State(bytes(4096, 0));
        var c = new CdR11aBackgroundPlane.Camera(0x12345678, 0x23456789);
        var scroll = new Scroll(new CdR11aForegroundScroll.State(0, 0xBEEF, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                b, plane, plane, new Copies(0x3456789A, 0x456789AB, new CdR11aBackgroundPlane.CopiedCameras(c, c, c),
                0x9876, new CdR11aBackgroundPlane.FlagWords(0x8765, 0x7654, 0x6543)), 0xCAFE);
        var inputs = new SonicCdAct2PastDisplay.Inputs(Optional.empty(), bytes(640, 0), bytes(128, 0), selectedHscroll == null ? b.hscrollRam() : selectedHscroll, 0, false,
                plane, plane, current, frame.artState());
        var video = new Video(bytes(640, 0), Optional.empty(), frame.artState(), current,
                new Published(SonicCdAct2PastDisplay.project(inputs)));
        var initial = new Initial(g, scroll, video, bank); var module = mock(SonicCdGameModule.class);
        var host = new SonicCdAct2PastWorldHost(module, terrain, player, frame, initial);
        var visit = new SonicCdAct2PastLoadingVisit(host, terrain, player, frame, art, entry);
        host.validate(visit, frame, terrain, player); return new Fixture(module, host, frame, art, entry, initial, visit);
    }
    private static void assertBackground(CdR11aBackgroundScroll.State expected, CdR11aBackgroundScroll.State actual) {
        assertArrayEquals(bgLongs(expected), bgLongs(actual)); assertEquals(flags(expected), flags(actual)); assertEquals(expected.vscroll(), actual.vscroll());
        assertArrayEquals(expected.crossingRam().copy(), actual.crossingRam().copy()); assertArrayEquals(expected.cloudRam().copy(), actual.cloudRam().copy());
        assertArrayEquals(expected.deformationRam().copy(), actual.deformationRam().copy()); assertArrayEquals(expected.hscrollRam().copy(), actual.hscrollRam().copy());
    }
    /** Genuine empty DrawObjects algorithm over explicit component ingress; no source/World admission is inferred. */
    private static CdObjectDrawQueue.CpuSat completedEmptyCpu(int retained) {
        var slots = mock(CdObjectDrawQueue.CdDrawSlots.class); var mappings = mock(CdObjectDrawQueue.CdDrawMappings.class);
        var queue = new CdObjectDrawQueue(slots, mappings, bytes(640, retained).copy()); queue.beginPass();
        var camera = new CdObjectDrawQueue.CameraWords(0, 0);
        var cpu = queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera, camera, camera));
        assertEquals(0, cpu.spriteCount()); verifyNoInteractions(slots, mappings); return cpu;
    }
    private static Globals globalsWithRings(Globals g, int rings) {
        var r = g.ring(); return new Globals(new CdRingGlobals(rings, r.lives(), r.livesFlags(), r.updateHudRings(), r.updateHudLives(), r.timeStopTimer(), r.ringAnimationTimer(), r.ringAnimationFrame()),
                g.live(), g.contact(), g.score(), g.projectorByte(), g.checkpointSaved(), g.drownTimerWord(), g.debugBlockWord(), g.pauseMusicBit(), g.bonus(), g.tail(), g.animations(), g.paletteSteps7(), g.paletteTimers7());
    }
    private static CdAssetBytes patternedBank() {
        byte[] raw = new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];
        for (int i = 0; i < raw.length; i++) raw[i] = (byte)(i * 37 + 11);
        raw[0] = (byte)0xA5; raw[1] = 0x5A; return CdAssetBytes.copyOf(raw);
    }
    private static void assertZeroPool(CdObjectPlacementRuntime placement) {
        var slots = placement.capture().pool().slots(); assertEquals(96, slots.size());
        for (int i = 0; i < slots.size(); i++) { assertEquals(i + 32, slots.get(i).identity().wholeIndex()); assertArrayEquals(new byte[64], slots.get(i).copyBytes()); }
    }
    private static Object snapshotImage(Object snapshot) {
        try { return field(snapshot, "image"); } catch (Exception e) { throw new AssertionError(e); }
    }
    private static CdObjectPlacementRuntime.Snapshot placementImage(Object snapshot) {
        try { return (CdObjectPlacementRuntime.Snapshot)field(snapshot, "placement"); } catch (Exception e) { throw new AssertionError(e); }
    }
    private static Object mixedSnapshot(Object snapshot, CdObjectPlacementRuntime.Snapshot placement) {
        try { var c = snapshot.getClass().getDeclaredConstructors()[0]; c.setAccessible(true); return c.newInstance(snapshotImage(snapshot), placement); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    /** Explicit same-pool component preimage; it is never a native Spawn result or a World admission. */
    private static CdObjectState componentSlot(CdObjectState base, byte[] bytes) {
        try { var c = CdObjectState.class.getDeclaredConstructor(Object.class, CdObjectState.Identity.class, byte[].class);
            c.setAccessible(true); return c.newInstance(field(base, "owner"), base.identity(), bytes.clone()); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static void assertNullRender(SonicCdAct2PastWorldHost host) {
        try { assertNull(field(host, "render")); } catch (Exception e) { throw new AssertionError(e); }
    }
    private static CdPlayerBody.State zeroActor() {
        return new CdPlayerBody.State(new CdPlayerMotionState(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),
                new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(0,0,0,0,0),new CdPlayerBody.Timers(0,0,0,0),
                new CdPlayerBody.History(bytes(256,0),0),0,new CdPlayerBody.ObjectLinks(0,0),0);
    }
    private static CdPlayerBody.Environment environment() {
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0,0,0),
                new CdPlayerMovement.Bounds(0,0,0,0),new CdPlayerMovement.Gates(1,0x80,0,0,0,0,0),
                new CdPlayerMovement.Globals(0,0,0,0,0,0,0,0,0,0)),new CdPlayerBody.Controls(0,0,0,0,0),
                new CdPlayerBody.Globals(0,0,0,0,0,0,0,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
    }
    private static CdTimeWarpSaveData.State saved() {
        byte[] raw = new byte[42], mask = new byte[42]; Arrays.fill(mask, (byte)1);
        int[][] data = {{4,1},{5,0x50},{6,2},{7,0x50},{8,0xC7},{9,4},{10,0x80},{31,0x80},{32,0},
                {33,0xFF},{34,0xFF},{35,0x12},{36,0x34},{37,0xF1},{38,0x23},{39,0x45},{40,0x67},{41,0xE1}};
        for (var row : data) raw[row[0]] = (byte)row[1];
        return CdTimeWarpSaveData.State.supplied(CdAssetBytes.copyOf(raw),CdAssetBytes.copyOf(mask));
    }
    private static void until(Fixture f, Phase phase) { for(int i=0;f.visit.state().phase()!=phase;i++){assertTrue(i<300);f.visit.service(8);} }
    private static CdAssetBytes bytes(int n,int value) { byte[] b=new byte[n];Arrays.fill(b,(byte)value);return CdAssetBytes.copyOf(b); }
    private static Object field(Object owner,String name)throws Exception {var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static int[] bgLongs(CdR11aBackgroundScroll.State b){return new int[]{b.cameraBgX(),b.cameraBgY(),b.cameraBg2X(),b.cameraBg2Y(),b.cameraBg3X(),b.cameraBg3Y()};}
    private static int[] longs(CdAssetBytes b){int[] a=new int[b.length()/4];for(int i=0;i<a.length;i++)a[i]=(int)b.unsignedLong(i*4);return a;}
    private static int[] words(CdR11aBackgroundPlane.State p){int[] a=new int[2048];for(int i=0;i<a.length;i++)a[i]=p.nameTable().unsignedWord(i*2);return a;}
    private static CdR11aBackgroundPlane.LiveCameras live(CdR11aBackgroundScroll.State b){
        return new CdR11aBackgroundPlane.LiveCameras(new CdR11aBackgroundPlane.Camera(b.cameraBgX(),b.cameraBgY()),
                new CdR11aBackgroundPlane.Camera(b.cameraBg2X(),b.cameraBg2Y()),new CdR11aBackgroundPlane.Camera(b.cameraBg3X(),b.cameraBg3Y()));
    }
    private static CdR11aBackgroundPlane.FlagWords flags(CdR11aBackgroundScroll.State b){return new CdR11aBackgroundPlane.FlagWords(b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3());}
}
