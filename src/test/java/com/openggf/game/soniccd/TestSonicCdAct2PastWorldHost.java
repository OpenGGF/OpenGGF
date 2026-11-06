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
        assertSame(f.initial.video(), f.host.video()); assertSame(f.host.capture(), f.host.capture());
        assertTrue(f.host.sounds().isEmpty()); assertTrue(f.host.completedClears().isEmpty());
        assertEquals(Phase.ENTRY, f.visit.state().phase()); assertEquals(17, f.frame.vIntClock().deltaSinceAdmittedCut());
        assertNull(field(f.host, "render")); assertThrows(NullPointerException.class,
                () -> new SonicCdAct2PastWorldHost(null, terrain, player, f.frame, f.initial));
    }

    @Test void revokedModuleGuardAndWrongPairRejectBeforeMutationWithoutCaptureInCallbacks() {
        var f = fixture(0); Object before = f.host.capture();
        doThrow(new IllegalStateException("revoked actual World")).when(f.module).requireAct2PastLoadingVisit(any());
        assertThrows(IllegalStateException.class, f.visit::begin);
        assertEquals(Phase.ENTRY, f.visit.state().phase());
        reset(f.module); assertSame(before, f.host.capture());
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
        var f = fixture(fade); var carried = f.host.globals(); f.visit.begin(); until(f, Phase.FIRST_PLC); f.visit.service(8);
        var g = f.host.globals();
        assertSame(carried.checkpointSaved(), g.checkpointSaved()); assertSame(carried.savedFlags(), g.savedFlags());
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
        assertSame(before, f.host.capture()); assertTrue(f.host.readLoadTarget(CdTimeWarpLoadData.StoreTarget.TIME_LONG).isEmpty());
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
                new Initial(f.initial.globals(), new Scroll(changed, old.background(), old.foregroundPlane(), old.backgroundPlane(), old.copies(), old.foregroundXFractionWord()), f.initial.video()));
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
        assertEquals(1, f.host.sounds().size()); assertEquals(CdPlayerBody.Sound.SCMD_PASTMUS, f.host.sounds().getFirst().sound());
        var before = f.host.capture(); var clock = f.frame.vIntClock();
        assertEquals(SonicCdAct2PastLoadingVisit.Boundary.ORDINARY_OBJECT_PASS,
                assertThrows(UnsupportedLoading.class, () -> f.visit.service(8)).boundary());
        assertSame(before, f.host.capture()); assertSame(clock, f.frame.vIntClock());
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

    @Test void coreSnapshotContainsOneHostImageAndRestoreReplaysNoActionsMusicOrPlc() {
        var f = fixture(0); f.visit.begin(); until(f, Phase.FROM_WHITE); var snapshot = f.visit.capture();
        var host = f.host.capture(); var clock = f.frame.vIntClock(); var selected = f.host.selected();
        f.visit.service(8); f.visit.restore(snapshot);
        assertSame(host, f.host.capture()); assertSame(selected, f.host.selected()); assertEquals(clock, f.frame.vIntClock());
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
        assertSame(before, f.host.capture());
        assertThrows(IllegalArgumentException.class, () -> handler.update(new int[257], 0, 0, 0, 1));
    }

    @Test void closeIsIdleFirstOnceOnlyAndRetiresLogicWithoutClosingTheFrame() throws Exception {
        var f = fixture(0); var drawing = f.host.getClass().getDeclaredField("drawing"); drawing.setAccessible(true); drawing.setBoolean(f.host, true);
        var before = field(f.host, "image"); clearInvocations(f.module);
        assertThrows(IllegalStateException.class, f.host::capture); assertThrows(IllegalStateException.class, f.host::close);
        assertSame(before, field(f.host, "image")); assertThrows(IllegalStateException.class, f.host::requireRenderIdle); verifyNoInteractions(f.module);
        drawing.setBoolean(f.host, false); f.host.close(); f.host.close(); assertNull(field(f.host, "render"));
        assertFalse(f.frame.closed()); assertThrows(IllegalStateException.class, f.host::capture);
        assertThrows(IllegalStateException.class, f.host::draw); assertThrows(IllegalStateException.class, f.visit::begin);
    }

    private record Fixture(SonicCdGameModule module, SonicCdAct2PastWorldHost host, CdPlayerFrameRuntime frame,
                           CdR12bSectionArt art, Entry entry, Initial initial, SonicCdAct2PastLoadingVisit visit) { }
    private static Fixture fixture(int fadeBit) {
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
                0x45, CdCheckpointSaveData.State.unknown(), bytes(CdObjectPlacementRuntime.SAVED_BANK_BYTES, 0x56),
                OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                bytes(7, 0x34), bytes(7, 0x45));
        var b = new CdR11aBackgroundScroll.State(0x1234, 0x2345, 0x3456, 0x4567, 0x5678, 0x6789, bytes(8, 0), 0, 0, 0,
                bytes(512, 0x23), bytes(512, 0), bytes(1024, 0), 0);
        var plane = new CdR11aBackgroundPlane.State(bytes(4096, 0));
        var c = new CdR11aBackgroundPlane.Camera(0x12345678, 0x23456789);
        var scroll = new Scroll(new CdR11aForegroundScroll.State(0, 0xBEEF, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                b, plane, plane, new Copies(0x3456789A, 0x456789AB, new CdR11aBackgroundPlane.CopiedCameras(c, c, c),
                0x9876, new CdR11aBackgroundPlane.FlagWords(0x8765, 0x7654, 0x6543)), 0xCAFE);
        var inputs = new SonicCdAct2PastDisplay.Inputs(Optional.empty(), bytes(640, 0), bytes(128, 0), b.hscrollRam(), 0, false,
                plane, plane, current, frame.artState());
        var video = new Video(bytes(640, 0), Optional.empty(), frame.artState(), current,
                new Published(SonicCdAct2PastDisplay.project(inputs)));
        var initial = new Initial(g, scroll, video); var module = mock(SonicCdGameModule.class);
        var host = new SonicCdAct2PastWorldHost(module, terrain, player, frame, initial);
        var visit = new SonicCdAct2PastLoadingVisit(host, terrain, player, frame, art, entry);
        host.validate(visit, frame, terrain, player); return new Fixture(module, host, frame, art, entry, initial, visit);
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
