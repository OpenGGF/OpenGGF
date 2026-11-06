package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.openggf.game.soniccd.SonicCdAct2PastLoadingVisit.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Actual media-backed loading algorithms; explicit fake Host does not certify a live World or planes. Java UNRUN. */
class TestSonicCdAct2PastLoadingVisit {
    static CdR12bTerrainAssets terrain;
    static CdR12bPlayerAssets player;

    @BeforeAll static void assets() throws Exception {
        String root = System.getProperty("soniccd.media.root"); if (root == null || root.isBlank()) return;
        Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var media = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            terrain = CdR12bTerrainAssets.fromPrograms(media); player = CdR12bPlayerAssets.fromPrograms(media);
        }
    }

    @Test void constructorRetainsExplicitValuesAndDoesNoNativeWorkBeforeInstalledBegin() {
        var f = fixture(0, 22, false); var entry = f.entry;
        assertEquals(Phase.ENTRY, f.visit.state().phase()); assertSame(entry.normalPalette(), f.visit.state().normalPalette());
        assertSame(entry.fadePalette(), f.visit.state().fadePalette()); assertSame(entry.waterPalette(), f.visit.state().waterPalette());
        assertSame(entry.section(), f.visit.state().section()); assertTrue(f.host.actions.isEmpty()); assertEquals(0, f.host.vints);
        assertSame(f.saved, f.visit.saved()); assertEquals(17, f.frame.vIntClock().deltaSinceAdmittedCut());
        assertEquals(0xBEEF, f.frame.environment().globals().levelFrames());
        assertThrows(NullPointerException.class, () -> new SonicCdAct2PastLoadingVisit(null, terrain, player, f.frame, f.art, entry));
    }

    @Test void skippedInitialWhitePreservesRealOldFadeHistoryAndStillRequiresFirstSelectorC() {
        var f = fixture(0, 22, false); var actor = f.frame.state(); var clock = f.frame.vIntClock(); f.visit.begin();
        assertEquals(Phase.FIRST_PLC, f.visit.state().phase()); assertEquals(22, f.visit.state().oldWhiteSteps());
        assertEquals(0, f.visit.state().initialWhiteSteps()); assertEquals(0xA1, f.visit.state().program().palFadeFlagsByte());
        assertEquals(1, f.visit.state().program().goodFutureByte()); assertSame(CdProgramInitialization.KnownBit.UNKNOWN, f.visit.state().program().palClearFlagBit0());
        assertSame(clock, f.frame.vIntClock()); assertSame(actor, f.frame.state()); assertEquals(0, f.host.vints);
        assertWhite(f.visit.state().normalPalette()); assertEquals(0, f.frame.environment().movement().gates().timeWarpDirection());
        assertFalse(f.visit.state().calls().contains(Call.PLAY_LEVEL_MUSIC));
        f.visit.service(8); assertEquals(Phase.SECTION_PLC, f.visit.state().phase());
        assertEquals(1, f.host.vints); assertEquals(12, f.host.selections.getFirst().selector());
        assertEquals(1, Collections.frequency(f.visit.state().calls(), Call.FIRST_WAIT));
        assertEquals(1, Collections.frequency(f.visit.state().calls(), Call.IMMEDIATE_LEVEL));
        assertEquals(0, f.frame.vIntClock().deltaSinceAdmittedCut(), "genuine later ClearFlowers resets the same clock");
    }

    @Test void oldSkipBitSelectsExactlyTwentyTwoInitialWhiteServicesBeforeAnyMainLoad() {
        var f = fixture(1, 0, false); var original = f.entry.normalPalette(); f.visit.begin();
        assertEquals(Phase.INITIAL_WHITE, f.visit.state().phase()); assertSame(original, f.visit.state().normalPalette());
        for (int i = 0; i < 22; i++) {
            var selectedBefore = f.visit.state().normalPalette(); var actor = f.frame.state();
            f.visit.service(8); assertSame(selectedBefore, f.host.selections.getLast().normalPalette());
            assertSame(actor, f.frame.state()); assertEquals(i + 1, f.visit.state().initialWhiteSteps());
            assertEquals(0xBEEF, f.frame.environment().globals().levelFrames());
            assertFalse(f.visit.state().calls().contains(Call.IMMEDIATE_LEVEL));
            assertEquals(i == 21 ? Phase.FIRST_PLC : Phase.INITIAL_WHITE, f.visit.state().phase());
        }
        assertEquals(22, f.host.vints); assertEquals(39, f.frame.vIntClock().deltaSinceAdmittedCut());
        assertEquals(0xA0, f.visit.state().program().palFadeFlagsByte()); assertEquals(0, f.visit.state().oldWhiteSteps());
        assertEquals(22, Collections.frequency(f.visit.state().calls(), Call.INITIAL_WHITE_SERVICE));
        assertEquals(22, Collections.frequency(f.visit.state().calls(), Call.INITIAL_WHITE_COLOURS));
        assertWhite(f.visit.state().normalPalette()); assertWhite(f.visit.state().waterPalette());
    }

    @Test void clearScreenSpriteDbfClearsOnlyFirstFourCoreWaterBytesBeforeHostPublication() {
        var f=fixture(1,0,false);f.visit.begin();
        for(int i=0;i<22;i++)f.visit.service(8);
        assertEquals(Phase.FIRST_PLC,f.visit.state().phase());assertEquals(22,f.visit.state().initialWhiteSteps());
        var before=f.visit.state().waterPalette();assertWhite(before);
        var normal=f.visit.state().normalPalette();var fade=f.visit.state().fadePalette();
        byte[] expected=before.copy();Arrays.fill(expected,0,4,(byte)0);int[] observed={0};
        f.host.onAction=(action,state)->{
            if(action==Action.CLEAR_SCREEN){
                observed[0]++;assertArrayEquals(expected,state.waterPalette().copy());
                assertSame(normal,state.normalPalette());assertSame(fade,state.fadePalette());
                assertSame(normal,state.displayedPalette());
            }
        };
        f.visit.service(8);
        assertEquals(1,observed[0]);assertArrayEquals(expected,f.visit.state().waterPalette().copy());
        assertArrayEquals(Arrays.copyOfRange(before.copy(),4,128),Arrays.copyOfRange(f.visit.state().waterPalette().copy(),4,128));
        assertWhite(before);assertEquals(Phase.SECTION_PLC,f.visit.state().phase());
        assertEquals(1,Collections.frequency(f.visit.state().calls(),Call.CLEAR_SCREEN));
    }

    @Test void actualTwentyStoreLoadUsesTrueR12bFrameAndPreservesSavedAndExternalUnsignedTime() {
        var f = fixture(0, 22, false); var saved = f.saved; var mask = saved.knownMask().copy(); f.visit.begin(); f.visit.service(8);
        assertEquals(20, f.visit.completedSavedStores().size());
        assertEquals(Arrays.asList(CdTimeWarpLoadData.NativeStore.values()).subList(0, 20), f.visit.completedSavedStores());
        assertEquals(0x01500000, f.frame.state().motion().centreX16_16()); assertEquals(0x02500000, f.frame.state().motion().centreY16_16());
        assertEquals(-32768, f.frame.state().motion().groundVelocity()); assertEquals(-1, f.frame.state().motion().xVelocity());
        assertEquals(0x1234, f.frame.state().motion().yVelocity()); assertEquals(0xC7, f.frame.state().motion().flags());
        assertEquals(0x0480, f.frame.environment().movement().bounds().bottom());
        assertEquals(0xF1234567L, f.host.targets.get(CdTimeWarpLoadData.StoreTarget.TIME_LONG).longValue());
        assertTrue(f.host.unknownTimePreimage); assertSame(saved, f.visit.saved()); assertArrayEquals(mask, saved.knownMask().copy());
        assertTrue(f.frame.stageSource().ownsAct2Past(terrain, player)); assertEquals(1, f.frame.environment().movement().gates().zoneAct());
        assertEquals(0x80, f.frame.environment().movement().gates().timeZone()); assertEquals(0, f.frame.state().header().slotId());
        assertSame(terrain.blocksDecode().bytes(), f.visit.state().blocksRam().orElseThrow());
        assertSame(terrain.terrain().layout().ram(), f.visit.state().layoutRam().orElseThrow());
        assertOrder(f.host.actions, Action.LEVEL_SIZE, Action.SETUP_CAMERA_AND_SCROLL, Action.LEVEL_SCROLL,
                Action.LEVEL_LAYOUT, Action.INITIAL_FOREGROUND, Action.INITIAL_BACKGROUND, Action.COLLISION_POINTER);
    }

    @Test void sectionQueueActuallyServicesThenCreatesOnlyNativeReservedHudAndLifeIcon() {
        var f = fixture(0, 22, false); f.visit.begin(); int waits = until(f, Phase.FROM_WHITE);
        assertTrue(waits >= 2); assertEquals(0, f.visit.state().section().remainingTilesWord()); assertEquals(0, f.visit.state().section().queue().headSource());
        assertEquals(1, f.frame.state().header().slotId()); assertEquals(0x78, f.frame.state().timers().hurt());
        assertEquals(0, f.frame.state().motion().routine(), "no ordinary primary Init ran during loading");
        byte[] hud = f.visit.state().hudSlots().copy(); assertEquals(0x1C, hud[64] & 255); assertEquals(0x1C, hud[128] & 255);
        assertEquals(1, hud[128 + 0x28] & 255); assertEquals(0x1C, hud[256] & 255); assertEquals(1, hud[256 + 0x29] & 255);
        assertEquals(0x19, hud[30 * 64] & 255); assertEquals(10, hud[30 * 64 + 0x28] & 255);
        var life = terrain.directArtSources().view(CdR12bDirectArtSources.View.LIFE_ICON);
        for (int i = 0; i < 4; i++) assertArrayEquals(life.slice(i * 32, 32).copy(), CdR12bCameraArtResidency.readTile(f.visit.state().section().residency(), (0xB420 >>> 5) + i).copy());
        assertEquals(0, f.frame.environment().globals().levelFrames()); assertEquals(0, f.frame.environment().movement().gates().timeZone());
        assertEquals(0, f.visit.state().level().spawnMode()); assertEquals(1, f.visit.state().level().fadeEnableDisplay());
        assertThrows(IllegalStateException.class, () -> f.frame.runBody(1));
    }

    @Test void fromWhitePublishesBeforeColourAndBeginsLevelAtTwentyTwoWithoutOrdinaryObjectWork() {
        var f = fixture(0, 22, false); f.visit.begin(); until(f, Phase.FROM_WHITE); int baseline = f.host.vints;
        var actor = f.frame.state(); var art = f.frame.artState();
        for (int i = 0; i < 22; i++) {
            var selected = f.visit.state().normalPalette(); f.visit.service(8);
            assertSame(selected, f.host.selections.getLast().normalPalette()); assertEquals(i != 0, f.host.selections.getLast().displayEnabled());
            assertTrue(f.visit.state().displayEnabled());
            assertEquals(i + 1, f.visit.state().fromWhiteSteps()); assertSame(actor, f.frame.state()); assertSame(art, f.frame.artState());
            assertEquals(0, f.frame.environment().globals().levelFrames());
            assertEquals(i == 21 ? Phase.BEGIN_LEVEL : Phase.FROM_WHITE, f.visit.state().phase());
        }
        assertEquals(baseline + 22, f.host.vints); assertEquals(1, f.visit.state().program().levelStartedByte());
        assertEquals(0, f.visit.state().program().gameModeByte() & 0x80);
        assertSame(CdProgramInitialization.KnownBit.UNKNOWN,f.visit.state().program().palClearFlagBit0()); assertEquals(2, f.visit.state().level().fadeEnableDisplay());
        assertArrayEquals(f.visit.state().fadePalette().copy(), f.visit.state().normalPalette().copy());
        var snapshot = f.visit.capture(); var count = f.host.vints;
        assertEquals(Boundary.ORDINARY_OBJECT_PASS, assertThrows(UnsupportedLoading.class, () -> f.visit.service(8)).boundary());
        assertEquals(count, f.host.vints); assertSame(snapshot.state(), f.visit.state()); assertFalse(f.frame.closed());
    }

    @Test void firstFromWhitePublicationFailureDoesNotExecuteLaterNativeDisplayEnable() {
        var f=fixture(0,22,false);f.visit.begin();until(f,Phase.FROM_WHITE);int baseline=f.host.vints;
        var failure=new IllegalArgumentException("first FromWhite palette stopped");f.host.fault=failure;
        assertSame(failure,assertThrows(IllegalArgumentException.class,()->f.visit.service(8)));
        assertEquals(1,f.visit.state().level().fadeEnableDisplay());assertFalse(f.visit.state().displayEnabled());
        assertFalse(f.host.actions.contains(Action.ENABLE_DISPLAY));assertEquals(baseline,f.host.vints);
        assertEquals(0,f.visit.state().fromWhiteSteps());assertTrue(f.frame.closed());assertTrue(f.visit.failed());
    }

    @Test void sameVisitCaptureRestoreForwardPreservesSourceAndDoesNotReplayActions() {
        var f = fixture(1, 0, false); f.visit.begin(); f.visit.service(8); var snapshot = f.visit.capture();
        f.visit.service(8); var expected = f.visit.state(); var clock = f.frame.vIntClock(); var host = f.host.capture();
        int executions = f.host.executions; f.visit.restore(snapshot); assertEquals(executions, f.host.executions);
        f.visit.service(8); assertState(expected, f.visit.state()); assertEquals(clock, f.frame.vIntClock()); assertHost((FakeHost.Image)host, (FakeHost.Image)f.host.capture());
        assertTrue(f.frame.stageSource().ownsAct2Past(terrain, player));
        var other = fixture(1, 0, false); other.visit.begin();
        assertThrows(IllegalArgumentException.class, () -> other.visit.restore(snapshot));
    }

    @Test void laterUnknownSavedReadPreservesFourStorePrefixAndMakesInterruptedVisitTerminal() {
        var f = fixture(0, 22, true); f.visit.begin();
        var unknown = assertThrows(CdTimeWarpSaveData.UnknownSavedByte.class, () -> f.visit.service(8)); assertEquals(0xFF1566, unknown.nativeAddress());
        assertEquals(List.of(CdTimeWarpLoadData.NativeStore.RESTORE_X, CdTimeWarpLoadData.NativeStore.RESTORE_Y,
                CdTimeWarpLoadData.NativeStore.RESTORE_FLAGS, CdTimeWarpLoadData.NativeStore.RESTORE_GVEL), f.visit.completedSavedStores());
        assertEquals(-32768, f.frame.state().motion().groundVelocity()); assertEquals(0, f.frame.state().motion().xVelocity());
        assertTrue(f.frame.closed()); assertTrue(f.visit.failed()); assertFalse(f.host.actions.contains(Action.SETUP_CAMERA_AND_SCROLL));
        var clock = f.frame.vIntClock(); assertThrows(IllegalStateException.class, () -> f.visit.service(8));
        assertThrows(IllegalStateException.class, f.visit::capture); assertSame(clock, f.frame.vIntClock());
    }

    @Test void outOfWidthExternalPreimageRejectsBeforeThatTargetsStoreAndKeepsNativeEarlierPrefix() {
        var f=fixture(0,22,false);f.visit.begin();f.host.targets.put(CdTimeWarpLoadData.StoreTarget.RINGS,0x10000L);
        assertThrows(IllegalArgumentException.class,()->f.visit.service(8));assertTrue(f.visit.failed());
        assertEquals(6,f.visit.completedSavedStores().size());assertEquals(0x10000L,f.host.targets.get(CdTimeWarpLoadData.StoreTarget.RINGS).longValue());
        assertFalse(f.host.targets.containsKey(CdTimeWarpLoadData.StoreTarget.TIME_LONG));assertTrue(f.frame.closed());
    }

    @Test void returnedButUnpublishedExternalStoreDoesNotCountAsACompletedNativeTarget() {
        var f=fixture(0,22,false);f.visit.begin();f.host.dropRingStore=true;
        assertThrows(IllegalStateException.class,()->f.visit.service(8));assertEquals(6,f.visit.completedSavedStores().size());
        assertFalse(f.host.targets.containsKey(CdTimeWarpLoadData.StoreTarget.RINGS));assertTrue(f.visit.failed());assertTrue(f.frame.closed());
    }

    @Test void mutableTargetObservationCannotSmuggleChangedOwnersIntoTheGenuineHelperCommit() {
        var f=fixture(0,22,false);f.visit.begin();f.host.mutateRead=true;
        assertThrows(IllegalStateException.class,()->f.visit.service(8));assertEquals(6,f.visit.completedSavedStores().size());
        assertTrue(f.host.targets.containsKey(CdTimeWarpLoadData.StoreTarget.RINGS));
        assertEquals(0x4321L,f.host.targets.get(CdTimeWarpLoadData.StoreTarget.RINGS).longValue(),"only the malicious read's own prefix occurred");
        assertTrue(f.visit.failed());assertTrue(f.frame.closed());
    }

    @Test void hostFailureKeepsReachedControllerPrefixAndCannotRestoreOrRetry() {
        var f = fixture(1, 0, false); f.visit.begin(); var prior = f.visit.capture(); var state = f.frame.state();
        var failure = new IllegalArgumentException("actual publication stopped"); f.host.fault = failure;
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> f.visit.service(0x35)));
        assertTrue(f.visit.failed()); assertTrue(f.frame.closed()); assertSame(state, f.frame.state());
        assertEquals(0x35, f.frame.environment().controls().rawHeld()); assertEquals(0, f.host.vints);
        assertThrows(IllegalStateException.class, () -> f.visit.restore(prior)); assertThrows(IllegalStateException.class, () -> f.visit.service(8));
    }

    @Test void hostLifetimeReentryAndPendingInputRejectBeforeAnyNativeWrite() {
        var f = fixture(0, 22, false); f.host.active = false;
        assertThrows(IllegalStateException.class, f.visit::begin); assertTrue(f.host.actions.isEmpty()); f.host.active = true;
        f.frame.prepareInput(8); assertThrows(IllegalStateException.class, f.visit::begin); f.frame.clearPendingInput();
        f.host.reenter = true; f.visit.begin(); f.visit.service(8); assertTrue(f.host.reentryChecked);
        var before = f.visit.state(); var clock = f.frame.vIntClock(); f.host.active = false;
        assertThrows(IllegalStateException.class, () -> f.visit.service(8)); assertSame(before, f.visit.state()); assertSame(clock, f.frame.vIntClock());
    }

    private record Fixture(FakeHost host, CdPlayerFrameRuntime frame, CdR12bSectionArt art, Entry entry,
                           CdTimeWarpSaveData.State saved, SonicCdAct2PastLoadingVisit visit) { }
    private static Fixture fixture(int fadeBit, int oldSteps, boolean missing) {
        assumeTrue(terrain != null && player != null, "Requires original-USA soniccd.media.root");
        var frame = CdPlayerFrameRuntime.fromAct2PastEntry(terrain, player, zeroActor(), environment(),
                new CdPlayerArtRuntime.State(new byte[0x300], new byte[0x300], 0, false),
                new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(), 17));
        var art = CdR12bSectionArt.fromAssets(terrain);
        var section = art.fromValues(bytes(512, 0), CdR12bCameraArtResidency.clear(CdR12bCameraArtResidency.unknown()), 0x1234,
                CdR11aPlcBuffer.fromBytes(bytes(96, 0)), Optional.empty(),
                new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.CLEARED, 0, 0, 0, 0, 0), 0, 0, 0, 0);
        var entry = new Entry(saved(missing), section, new Program(0x1234, 0x7F, 0x34, 0x56, 0, 0xA0 | fadeBit, CdProgramInitialization.KnownBit.UNKNOWN, 0x12, 1, 0x3456),
                new CdLevelGlobals(0, 0x34, 0x56, 2, 3, 0x123456, 0x78, 0x9A, 0xBC),
                colours(0x0246), colours(0x068A), colours(0x0000), colours(0x0EEE), bytes(1984, 0), oldSteps, false);
        var host = new FakeHost(); var visit = new SonicCdAct2PastLoadingVisit(host, terrain, player, frame, art, entry); host.visit = visit;
        return new Fixture(host, frame, art, entry, entry.saved(), visit);
    }
    private static CdPlayerBody.State zeroActor() {
        return new CdPlayerBody.State(new CdPlayerMotionState(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),
                new CdPlayerBody.AnimationCursor(0,0,0), new CdPlayerBody.Header(0,0,0,0,0), new CdPlayerBody.Timers(0,0,0,0),
                new CdPlayerBody.History(bytes(256,0),0),0,new CdPlayerBody.ObjectLinks(0,0),0);
    }
    private static CdPlayerBody.Environment environment() {
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0,0,0),
                new CdPlayerMovement.Bounds(0,0,0,0),new CdPlayerMovement.Gates(1,0x80,0x12,0,0,0x34,0xFF),
                new CdPlayerMovement.Globals(0,0,0,0,0,0,0x12,0x34,0x56,0x78)),new CdPlayerBody.Controls(0,0,0,0,0),
                new CdPlayerBody.Globals(0xBEEF,0,0,0,0x1234,0,0x56,0x78),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
    }
    private static CdTimeWarpSaveData.State saved(boolean missing) {
        byte[] data=new byte[42],mask=new byte[42];Arrays.fill(mask,(byte)1);
        int[][] rows={{4,1},{5,0x50},{6,2},{7,0x50},{8,0xC7},{9,4},{10,0x80},{31,0x80},{32,0},{33,0xFF},{34,0xFF},{35,0x12},{36,0x34},
                {37,0xF1},{38,0x23},{39,0x45},{40,0x67},{41,0xE1}};
        for(var row:rows)data[row[0]]=(byte)row[1];if(missing)mask[33]=0;
        return CdTimeWarpSaveData.State.supplied(CdAssetBytes.copyOf(data),CdAssetBytes.copyOf(mask));
    }
    private static CdAssetBytes bytes(int count,int value){byte[] b=new byte[count];Arrays.fill(b,(byte)value);return CdAssetBytes.copyOf(b);}
    private static CdAssetBytes colours(int word){byte[] b=new byte[128];for(int i=0;i<b.length;i+=2){b[i]=(byte)(word>>>8);b[i+1]=(byte)word;}return CdAssetBytes.copyOf(b);}
    private static void assertWhite(CdAssetBytes p){assertArrayEquals(colours(0xEEE).copy(),p.copy());}
    private static int until(Fixture f,Phase phase){int n=0;while(f.visit.state().phase()!=phase){assertTrue(n++<300,"bounded source queue did not reach "+phase);f.visit.service(8);}return n;}
    private static void assertState(State a,State b){
        assertEquals(a.phase(),b.phase());assertEquals(a.section(),b.section());assertEquals(a.program(),b.program());assertEquals(a.level(),b.level());
        assertArrayEquals(a.normalPalette().copy(),b.normalPalette().copy());assertArrayEquals(a.fadePalette().copy(),b.fadePalette().copy());
        assertArrayEquals(a.waterPalette().copy(),b.waterPalette().copy());assertArrayEquals(a.displayedPalette().copy(),b.displayedPalette().copy());
        assertArrayEquals(a.hudSlots().copy(),b.hudSlots().copy());assertOptional(a.blocksRam(),b.blocksRam());assertOptional(a.layoutRam(),b.layoutRam());
        assertEquals(a.oldWhiteSteps(),b.oldWhiteSteps());assertEquals(a.initialWhiteSteps(),b.initialWhiteSteps());assertEquals(a.fromWhiteSteps(),b.fromWhiteSteps());
        assertEquals(a.displayEnabled(),b.displayEnabled());assertEquals(a.calls(),b.calls());
    }
    private static void assertOptional(Optional<CdAssetBytes> a,Optional<CdAssetBytes> b){assertEquals(a.isPresent(),b.isPresent());if(a.isPresent())assertArrayEquals(a.orElseThrow().copy(),b.orElseThrow().copy());}
    private static void assertHost(FakeHost.Image a,FakeHost.Image b){
        assertEquals(a.targets(),b.targets());assertEquals(a.actions(),b.actions());assertEquals(a.vints(),b.vints());assertEquals(a.selections().size(),b.selections().size());
        for(int i=0;i<a.selections().size();i++){var p=a.selections().get(i);var q=b.selections().get(i);assertEquals(p.selector(),q.selector());assertEquals(p.phase(),q.phase());
            assertArrayEquals(p.normalPalette().copy(),q.normalPalette().copy());assertArrayEquals(p.waterPalette().copy(),q.waterPalette().copy());
            assertEquals(p.displayEnabled(),q.displayEnabled());assertEquals(p.section(),q.section());}
    }
    private static void assertOrder(List<Action> actions,Action... expected){int cursor=-1;for(var a:expected){int at=actions.subList(cursor+1,actions.size()).indexOf(a);assertTrue(at>=0,a.name());cursor+=at+1;}}
    private static final class FakeHost implements Host {
        SonicCdAct2PastLoadingVisit visit;boolean active=true,reenter,reentryChecked,unknownTimePreimage,mutateRead,dropRingStore;int vints,executions;
        RuntimeException fault;final List<Action> actions=new ArrayList<>();final List<Publication> selections=new ArrayList<>();
        java.util.function.BiConsumer<Action,State> onAction=(action,state)->{};
        final EnumMap<CdTimeWarpLoadData.StoreTarget,Long> targets=new EnumMap<>(CdTimeWarpLoadData.StoreTarget.class);
        record Image(Map<CdTimeWarpLoadData.StoreTarget,Long> targets,List<Action> actions,List<Publication> selections,int vints){}
        public void validate(SonicCdAct2PastLoadingVisit v,CdPlayerFrameRuntime f,CdR12bTerrainAssets t,CdR12bPlayerAssets p){
            if(!active||visit!=v||t!=terrain||p!=player||!f.stageSource().ownsAct2Past(t,p))throw new IllegalStateException("actual Host lifetime/source");
        }
        public Object capture(){return new Image(Map.copyOf(targets),List.copyOf(actions),List.copyOf(selections),vints);}
        public void validateRestore(Object image){assertInstanceOf(Image.class,image);}
        public void restore(Object image){var i=(Image)image;targets.clear();targets.putAll(i.targets);actions.clear();actions.addAll(i.actions);selections.clear();selections.addAll(i.selections);vints=i.vints;}
        public void act(Action action,State state){executions++;actions.add(action);if(action==Action.VINT_COMPLETED)vints++;
            onAction.accept(action,state);
            if(reenter){assertThrows(IllegalStateException.class,()->visit.service(8));reentryChecked=true;}}
        public void publish(Publication selected){if(fault!=null)throw fault;selections.add(selected);}
        public int cameraXWord(){return 0xB0;}
        public OptionalLong readLoadTarget(CdTimeWarpLoadData.StoreTarget t){var v=targets.get(t);
            if(mutateRead&&t==CdTimeWarpLoadData.StoreTarget.RINGS){mutateRead=false;targets.put(t,0x4321L);}
            return v==null?OptionalLong.empty():OptionalLong.of(v);}
        public void storeLoadTarget(CdTimeWarpLoadData.StoreCall call){
            if(dropRingStore&&call.target()==CdTimeWarpLoadData.StoreTarget.RINGS)return;
            var before=readLoadTarget(call.target());assertEquals(before.isPresent(),call.expectedValue().isPresent());
            if(before.isPresent())assertEquals((int)before.getAsLong(),call.expectedValue().getAsInt());
            if(call.target()==CdTimeWarpLoadData.StoreTarget.TIME_LONG)unknownTimePreimage=call.expectedValue().isEmpty();
            long mask=call.target().width()==4?0xFFFFFFFFL:call.target().width()==2?0xFFFFL:0xFFL;targets.put(call.target(),Integer.toUnsignedLong(call.value())&mask);
        }
    }
}
