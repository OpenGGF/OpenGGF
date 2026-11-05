package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ALL JAVA UNRUN. Actual normal load establishes CPU knowledge; all dirty CPU,
 * forced active BYTE and direct-service cuts are explicitly constructed controls.
 * No constructed control qualifies natural star acquisition or a later controller call.
 */
@Isolated
class TestSonicCdAct2PowerupCpuCurrentWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }
    private record Scene(Harness h, SonicCdAct2LoadingVisit visit, CdPlayerFrameRuntime frame) { }

    @Test void actualNormalMainClearAloneCreatesCompleteCpuKnowledgeAndBothCaptureScopesRetainIt() throws Exception {
        var old=oldReturned();step(old.harness(),neutral());var h=old.harness();var s=scene(h);
        assertEquals(SonicCdGameModule.Act2LoadPhase.FIRST_PLC,h.module().act2LoadState().phase());
        assertTrue(cpu(s).isEmpty(),"No constructor/default CPU ingress");
        var first=h.mode().getRewindRegistry().capture();var firstCourse=h.mode().getRewindRegistry().captureCourse();
        var before=current(s);step(h,neutral());
        assertTrue(h.module().act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.CLEAR_FLOWERS));
        assertArrayEquals(new byte[1152],cpu(s).orElseThrow().copy());
        var realCpu=cpu(s).orElseThrow();var after=current(s);
        // CPU RAM's Main clear is not a current-art writer. Existing actual InitVDP
        // may already have established real zeros; this store adds no knowledge.
        assertArrayEquals(before.region(CdR12aCameraArtResidency.Region.AT_A400).knownMask().slice(0x480,1152).copy(),
                after.region(CdR12aCameraArtResidency.Region.AT_A400).knownMask().slice(0x480,1152).copy());
        var full=h.mode().getRewindRegistry().capture();var course=h.mode().getRewindRegistry().captureCourse();
        h.mode().getRewindRegistry().restore(first);assertTrue(cpu(s).isEmpty());
        h.mode().getRewindRegistry().restoreCourse(firstCourse);assertTrue(cpu(s).isEmpty());
        h.mode().getRewindRegistry().restore(full);assertSame(realCpu,cpu(s).orElseThrow());assertEquals(after,current(s));
        h.mode().getRewindRegistry().restoreCourse(course);assertSame(realCpu,cpu(s).orElseThrow());assertEquals(after,current(s));
    }

    @Test void constructedInactiveGateDoesNotInspectEvenUnavailableCpuOrRawSourceCacheAndDoesNotWriteCurrent() throws Exception {
        var s=ordinary();var before=current(s);var objects=s.h().module().act2LoadState().assets().objects();
        Object cache=actualField(objects,"powerupArtSources");
        setCpu(s,Optional.empty());
        for(int raw=0;raw<128;raw++) {
            setPowerup(s,raw);directConstructedService(s);
            assertTrue(cpu(s).isEmpty());assertSame(before,current(s));assertEquals(raw,level(s).powerup());
            assertSame(cache,actualField(objects,"powerupArtSources"));
        }
    }

    @Test void constructedFiveActiveSelectorsCopyActualSourcesAndDirtyTailPublishExactly36CurrentTilesWithAllOtherBytesAndMasksUnchanged() throws Exception {
        var s=ordinary();var registry=s.h().mode().getRewindRegistry();var base=registry.capture();
        var sources=s.h().module().act2LoadState().assets().objects().powerupArtSources();
        for(int slot=0;slot<5;slot++) {
            registry.restore(base);var dirty=CdAssetBytes.copyOf(dirtyCpu(slot));setCpu(s,Optional.of(dirty));setPowerup(s,0x82+slot*2);
            var before=current(s);var actor=s.frame().captureActor();var shared=s.frame().captureShared();
            var placed=s.h().module().act2LoadState().placement();var calls=s.h().module().act2LoadState().calls();
            directConstructedService(s);var actual=cpu(s).orElseThrow();
            assertArrayEquals(sources.copy(slot).copy(),actual.slice(0,1024).copy());
            assertArrayEquals(dirty.slice(1024,128).copy(),actual.slice(1024,128).copy());
            assertEquals(2+slot*2,level(s).powerup());assertEquals(calls,s.h().module().act2LoadState().calls());
            assertEquals(actor,s.frame().captureActor());assertEquals(shared,s.frame().captureShared());
            var now=s.h().module().act2LoadState().placement();assertEquals(placed.pool().slots(),now.pool().slots());
            assertEquals(placed.status(),now.status());assertArrayEquals(placed.savedFlags().copy(),now.savedFlags().copy());
            assertWholeCurrent(before,current(s),actual);
            var retained=current(s);directConstructedService(s);assertSame(retained,current(s));assertSame(actual,cpu(s).orElseThrow());
            assertNull(actualField(s.visit(),"activePowerupArt"));
        }
    }

    @Test void constructedActiveRequestUsesActualVintBeforeExistingSelectedDisplayAndBothRewindScopesRetainCpuAndCurrent() throws Exception {
        var s=ordinary();setPowerup(s,0x86);var registry=s.h().mode().getRewindRegistry();
        var before=registry.capture();var beforeCourse=registry.captureCourse();var nativeBefore=captureNative(s.h());
        var oldCpu=cpu(s).orElseThrow();var oldCurrent=current(s);var world=s.h().mode().getWorldSession();
        var source=s.frame().stageSource();long generation=s.h().mode().getLevelManager().getCompletedProductionLoadGeneration();
        step(s.h(),neutral());var nativeAfter=captureNative(s.h());var afterCpu=cpu(s).orElseThrow();var afterCurrent=current(s);
        assertArrayEquals(s.h().module().act2LoadState().assets().objects().powerupArtSources().copy(2).copy(),afterCpu.slice(0,1024).copy());
        assertArrayEquals(oldCpu.slice(1024,128).copy(),afterCpu.slice(1024,128).copy());assertEquals(6,level(s).powerup());
        assertSame(afterCurrent,s.h().module().act2SelectedDisplay().inputs().current());
        assertWindow(afterCurrent,afterCpu);
        for(boolean course:new boolean[]{false,true}) {
            if(course)registry.restoreCourse(beforeCourse);else registry.restore(before);
            assertNativeCapture(nativeBefore,s.h());assertSame(oldCpu,cpu(s).orElseThrow());assertEquals(oldCurrent,current(s));
            step(s.h(),neutral());assertNativeCapture(nativeAfter,s.h());
            assertArrayEquals(afterCpu.copy(),cpu(s).orElseThrow().copy());assertEquals(afterCurrent,current(s));
            assertSame(world,s.h().mode().getWorldSession());assertSame(source,s.frame().stageSource());
            assertEquals(generation,s.h().mode().getLevelManager().getCompletedProductionLoadGeneration());
        }
        var full=registry.capture();var course=registry.captureCourse();
        step(s.h(),neutral());registry.restore(full);assertArrayEquals(afterCpu.copy(),cpu(s).orElseThrow().copy());assertEquals(afterCurrent,current(s));
        registry.restoreCourse(course);assertArrayEquals(afterCpu.copy(),cpu(s).orElseThrow().copy());assertEquals(afterCurrent,current(s));
    }

    @Test void constructedUnavailableCpuKeepsActualBclrPrefixWithoutPublishingCurrentOrReadingSources() throws Exception {
        var s=ordinary();setCpu(s,Optional.empty());setPowerup(s,0x86);
        var before=current(s);var objects=s.h().module().act2LoadState().assets().objects();Object cache=actualField(objects,"powerupArtSources");
        var failure=assertThrows(SonicCdGameModule.UnsupportedAct2Loading.class,()->directConstructedService(s));
        assertEquals(SonicCdGameModule.Act2Boundary.POWERUP_ART,failure.boundary());
        assertEquals(6,level(s).powerup());assertTrue(cpu(s).isEmpty());assertSame(before,current(s));
        assertSame(cache,actualField(objects,"powerupArtSources"));assertNull(actualField(s.visit(),"activePowerupArt"));
    }

    @Test void constructedInvalidActiveSelectorRetainsOriginalFiniteFaultBclrCpuAndCurrentAndRetiresLease() throws Exception {
        var s=ordinary();setPowerup(s,0x83);var before=current(s);var beforeCpu=cpu(s).orElseThrow();
        var failure=assertThrows(CdPowerupArtRuntime.UnsupportedArt.class,()->directConstructedService(s));
        assertEquals(2,failure.pointerOffset());assertEquals(3,level(s).powerup());assertSame(before,current(s));
        assertSame(beforeCpu,cpu(s).orElseThrow());assertNull(actualField(s.visit(),"activePowerupArt"));
        // Explicitly constructed private service control only; no failed Body prefix is adopted.
        setPowerup(s,0x86);directConstructedService(s);assertEquals(6,level(s).powerup());assertWindow(current(s),cpu(s).orElseThrow());
    }

    @Test void actualVintFailureFromConstructedInvalidRequestKeepsBclrAndClosesOriginalFrameWithoutCurrentPublicationOrRetry() throws Exception {
        var s=ordinary();setPowerup(s,0x83);var before=current(s);var actualCpu=cpu(s).orElseThrow();
        var failure=assertThrows(CdPowerupArtRuntime.UnsupportedArt.class,()->step(s.h(),neutral()));
        assertEquals(2,failure.pointerOffset());assertTrue(s.frame().closed());
        // Direct retained private values only: failed-cut module/capture/render diagnostics are forbidden.
        assertEquals(3,level(s).powerup());assertSame(before,current(s));assertSame(actualCpu,cpu(s).orElseThrow());
        assertNull(actualField(s.visit(),"activePowerupArt"));assertFalse((boolean)actualField(s.visit(),"servicing"));
    }

    @Test void constructedLeaseCannotReenterServiceOrCaptureOrCrossFamilyAndClosedFrameGrantsNoPublication() throws Exception {
        var s=ordinary();var before=values(s);var type=Arrays.stream(s.visit().getClass().getDeclaredClasses())
                .filter(c->c.getSimpleName().equals("PowerupArtInvocation")).findFirst().orElseThrow();
        var ctor=type.getDeclaredConstructor(SonicCdAct2LoadingVisit.class);ctor.setAccessible(true);var lease=ctor.newInstance(s.visit());
        set(s.visit(),"activePowerupArt",lease);
        try {
            assertThrows(IllegalStateException.class,()->directConstructedService(s));
            assertThrows(IllegalStateException.class,()->s.h().mode().getRewindRegistry().capture());
            assertThrows(IllegalStateException.class,()->invoke(s.visit(),"requireCheckpointIdle"));
            assertSame(before,values(s));
        } finally {set(s.visit(),"activePowerupArt",null);}
        s.frame().close();assertThrows(IllegalStateException.class,()->directConstructedService(s));assertSame(before,values(s));
    }

    @Test void constructedLeaseRejectsStaleFullReservedValuesAndUnselectedDynamicImagesWithoutAdoptingEither() throws Exception {
        var s=ordinary();var type=Arrays.stream(s.visit().getClass().getDeclaredClasses())
                .filter(c->c.getSimpleName().equals("PowerupArtInvocation")).findFirst().orElseThrow();
        var ctor=type.getDeclaredConstructor(SonicCdAct2LoadingVisit.class);ctor.setAccessible(true);
        var verify=s.visit().getClass().getDeclaredMethod("verifyPowerupArt",type);verify.setAccessible(true);
        var initial=values(s);var placement=(CdObjectPlacementRuntime)actualField(s.visit(),"placement");var placed=placement.capture();
        var lease=ctor.newInstance(s.visit());set(s.visit(),"activePowerupArt",lease);
        try {
            verify.invoke(s.visit(),lease);
            var reserved=((CdAssetBytes)actualField(initial,"hudObjects")).copy();reserved[30*64+63]^=1;
            var changed=record(initial,"hudObjects",CdAssetBytes.copyOf(reserved));set(s.visit(),"values",changed);
            var bad=assertThrows(InvocationTargetException.class,()->verify.invoke(s.visit(),lease));
            assertInstanceOf(IllegalStateException.class,bad.getCause());assertSame(changed,values(s));
            set(s.visit(),"values",initial);verify.invoke(s.visit(),lease);
            var unselected=placement.pool().state(127);placement.pool().write(unselected.moveByte(CdObjectState.ByteField.SUBTYPE,unselected.unsignedByte(CdObjectState.ByteField.SUBTYPE)^1));
            bad=assertThrows(InvocationTargetException.class,()->verify.invoke(s.visit(),lease));assertInstanceOf(IllegalStateException.class,bad.getCause());
            assertSame(initial,values(s));placement.restore(placed);verify.invoke(s.visit(),lease);
        } finally {set(s.visit(),"values",initial);placement.restore(placed);set(s.visit(),"activePowerupArt",null);}
    }

    @Test void selectedConstructedTimeStarFramesUseActualRawRootsAndMissingThenPublishedCurrentWithoutWorldProducerAuthority() throws Exception {
        var s=ordinary();var source=s.h().module().act2LoadState().assets().objects();
        var raw=source.view(CdR12aProfile.Asset.MAP_SPR_POWERUP);assertEquals(274,raw.length());
        var selected=s.h().module().act2SelectedDisplay().inputs();
        // Constructed knowledge control: keep every actual known tile outside36,
        // leaving exactly the newly owned physical window unknown.
        var unknown=withoutPowerupWindow(current(s));
        // Constructed value service, using actual authenticated source and a labelled dirty CPU tail.
        var request=CdPowerupArtRuntime.fromRawSources(source.powerupArtSources()).prepareActiveCopy(CdAssetBytes.copyOf(dirtyCpu(7)),6);
        var published=unknown;
        for(int at=0;at<1152;at+=32)published=CdR12aCameraArtResidency.withTile(published,0xA880+at,request.bytes().slice(at,32));
        for(int frame:new int[]{0,8,9,10,11,12}) {
            var sat=constructedStarSat(raw,source.profile().range(CdR12aProfile.Asset.MAP_SPR_POWERUP).nativeAddress(),frame);
            var missing=project(selected,sat,unknown);
            var complete=project(selected,sat,published);
            assertInstanceOf(SonicCdPastDisplay.Ready.class,complete.projection());
            if(sat.spriteCount()>0) {
                var absent=assertInstanceOf(SonicCdPastDisplay.Blocked.class,missing.projection()).failure();
                assertEquals(SonicCdPastDisplay.Boundary.UNKNOWN_PATTERN,absent.boundary());
                assertTrue(absent.nativePatternId()>=0x544 && absent.nativePatternId()<0x568);
                var scene=assertInstanceOf(SonicCdPastDisplay.Ready.class,complete.projection()).scene();
                assertTrue(scene.nativePatterns().keySet().stream().anyMatch(id->id>=0x544 && id<0x568));
            }
            else assertInstanceOf(SonicCdPastDisplay.Ready.class,missing.projection(),"Native frame0 is empty");
        }
    }

    @Test void constructedSelectedTimeStarUploadRetiresOnlyItsOwnPatternsAndPreservesOtherProducerUploads() throws Exception {
        try(var egl=TestSonicCdPastResultsRoute.Egl.open()) {
            var s=ordinary();setPowerup(s,0x86);directConstructedService(s);
            var source=s.h().module().act2LoadState().assets().objects();
            var sat=constructedStarSat(source.view(CdR12aProfile.Asset.MAP_SPR_POWERUP),
                    source.profile().range(CdR12aProfile.Asset.MAP_SPR_POWERUP).nativeAddress(),8);
            var selected=project(s.h().module().act2SelectedDisplay().inputs(),sat,current(s));
            var picture=assertInstanceOf(SonicCdPastDisplay.Ready.class,selected.projection()).scene();
            var graphics=com.openggf.game.GameServices.graphics();var atlas=graphics.getPatternAtlas();
            atlas.registerRange(com.openggf.graphics.PatternAtlasRange.HUD);
            int other=com.openggf.graphics.PatternAtlasRange.HUD.base()+19;
            if(atlas.getEntry(other)==null)graphics.cachePatternTexture(new com.openggf.level.Pattern(),other);
            var retained=atlas.getEntry(other);var sample=com.openggf.graphics.SpritePresentation.patternSample(graphics,other);
            var before=captureNative(s.h());var beforeCpu=cpu(s).orElseThrow();
            try(var renderer=new com.openggf.graphics.RetainedPlaneSceneRenderer(graphics,com.openggf.graphics.PatternAtlasRange.OBJECTS)) {
                renderer.draw(picture,0,0,s.h().module()::validateAct2LoadingCut);
                for(int id:picture.nativePatterns().keySet())assertNotNull(atlas.getEntry(com.openggf.graphics.PatternAtlasRange.OBJECTS.base()+id));
                renderer.draw(picture,0,0,s.h().module()::validateAct2LoadingCut);
                assertNativeCapture(before,s.h());assertSame(beforeCpu,cpu(s).orElseThrow());
            }
            for(int id:picture.nativePatterns().keySet()) {
                assertNull(atlas.getEntry(com.openggf.graphics.PatternAtlasRange.OBJECTS.base()+id));
                assertNull(com.openggf.graphics.SpritePresentation.patternSample(graphics,com.openggf.graphics.PatternAtlasRange.OBJECTS.base()+id));
            }
            assertSame(retained,atlas.getEntry(other));assertEquals(sample,com.openggf.graphics.SpritePresentation.patternSample(graphics,other));
            assertNativeCapture(before,s.h());
        }
    }

    @Test void genuineControllerRecipeStopsAt12884AndChecksOnlySourceReachedCpuAndCurrentCuts() throws Exception {
        var old=ready();var h=old.harness();Scene scene=null;
        for(int call=old.returned()+1;call<=12884;call++) {
            // An earlier unsupported body throws here and ends this test immediately.
            // Never inspect/adopt its prefix or predict a later natural call.
            step(h,controller(call));
            if(h.module().act2LoadingSelected()) {
                if(scene==null)scene=scene(h);
                var knowledge=cpu(scene);
                if(h.module().act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.CLEAR_FLOWERS))
                    assertArrayEquals(new byte[1152],knowledge.orElseThrow().copy());
                if(knowledge.isPresent())assertEquals(1152,knowledge.orElseThrow().length());
            }
        }
        assertNotNull(scene);assertEquals(0,scene.frame().environment().controls().controlLocked());
        assertEquals(0,scene.frame().state().motion().charge());
        assertEquals(1,scene.frame().environment().movement().gates().timeZone());
        // No native LoadShieldArt active request has been supplied as an assumption.
        // This asserts retained actual CPU knowledge only, not12885 Init/active/art.
        assertArrayEquals(new byte[1152],cpu(scene).orElseThrow().copy());
        var registry=h.mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
        var actual=cpu(scene).orElseThrow();var patterns=current(scene);var nativeCut=captureNative(h);
        registry.restore(full);assertNativeCapture(nativeCut,h);assertSame(actual,cpu(scene).orElseThrow());assertEquals(patterns,current(scene));
        registry.restoreCourse(course);assertNativeCapture(nativeCut,h);assertSame(actual,cpu(scene).orElseThrow());assertEquals(patterns,current(scene));
    }
    /** Exact immutable controller fixture recipe; finite endpoint12884 supplies no gameplay values. */
    private static LogicalInputSnapshot controller(int call) {
        if(call<1 || call>12884)throw new IllegalArgumentException("Bounded source recipe ends12884");
        if(call<=8276)return route(call);
        int directions=8,actions=call<=8308?2:0;
        if(call>=9001 && call<=9203)directions=4;
        else if(call>=9206 && call<=9217)directions=0;
        else if(call>=9218 && call<=9263){directions=2;actions=call==9219?2:0;}
        else if(call>=9336 && call<=9367)actions=2;
        if(call>=11214)actions=(call-11214)%128<32?2:0;
        if(call==11587 || call>=12671 && call<=12699)actions=0;
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions,0,actions,0,false,false),PlayerInputState.neutral());
    }

    private static CdR12aCameraArtResidency.State withoutPowerupWindow(CdR12aCameraArtResidency.State actual) {
        var out=CdR12aCameraArtResidency.unknown();
        for(var region:CdR12aCameraArtResidency.Region.values())for(int offset=0;offset<region.byteLength();offset+=32) {
            int at=region.destinationWord()+offset;
            if((at<0xA880 || at>=0xAD00) && actual.region(region).knownByte(offset).isPresent())
                out=CdR12aCameraArtResidency.withTile(out,at,CdR12aCameraArtResidency.readTile(actual,at>>>5));
        }
        return out;
    }
    private static SonicCdAct2Display.Selection project(SonicCdAct2Display.Inputs p,CdObjectDrawQueue.CpuSat sat,CdR12aCameraArtResidency.State current) {
        return SonicCdAct2Display.project(new SonicCdAct2Display.Inputs(Optional.of(sat),CdAssetBytes.copyOf(sat.bytes()),p.palette(),p.hscroll(),
                p.vscrollLongBits(),true,p.foreground(),p.background(),current,p.player()));
    }
    /** Constructed standalone queue uses the real raw counted mapping roots (including empty frame0). */
    private static CdObjectDrawQueue.CpuSat constructedStarSat(CdAssetBytes raw,int address,int frame) {
        var ref=new CdObjectDrawQueue.CdSlotRef(12);
        var queue=new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots() {
            @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef slot) {
                assertEquals(ref,slot);return new CdObjectDrawQueue.LiveDrawSlot(3,0,160,112,112,address,frame,0x8544);
            }
            @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef slot,boolean value) {assertEquals(ref,slot);}
        },(pointer,staticPiece,id)-> {
            assertEquals(address,pointer);assertFalse(staticPiece);assertEquals(frame,id);
            int root=(short)((raw.unsignedByte(id*2)<<8)|raw.unsignedByte(id*2+1));int count=raw.unsignedByte(root);
            var pieces=new ArrayList<CdObjectDrawQueue.RawPiece>();
            for(int p=0;p<count;p++) {int at=root+1+p*5;pieces.add(new CdObjectDrawQueue.RawPiece((byte)raw.unsignedByte(at),
                    raw.unsignedByte(at+1),(raw.unsignedByte(at+2)<<8)|raw.unsignedByte(at+3),(byte)raw.unsignedByte(at+4)));}
            return new CdObjectDrawQueue.FrameMapping(count,pieces);
        },new byte[640],List.of(ref));
        queue.beginPass();var camera=new CdObjectDrawQueue.CameraWords(0,0);
        assertEquals(CdObjectDrawQueue.EnqueueResult.QUEUED,queue.drawObject(new CdObjectDrawQueue.DrawCallCut(ref,0,160,112,16,16,1),camera));
        var sat=queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera,camera,camera));queue.close();return sat;
    }
    private static void assertWholeCurrent(CdR12aCameraArtResidency.State before,CdR12aCameraArtResidency.State after,CdAssetBytes expected) {
        assertWindow(after,expected);
        for(var region:CdR12aCameraArtResidency.Region.values()) {
            if(region!=CdR12aCameraArtResidency.Region.AT_A400)assertSame(before.region(region),after.region(region));
            for(int offset=0;offset<region.byteLength();offset++) {
                int at=region.destinationWord()+offset;
                if(at<0xA880 || at>=0xAD00) {
                    assertEquals(before.region(region).knownByte(offset),after.region(region).knownByte(offset));
                    assertEquals(before.region(region).knownMask().unsignedByte(offset),after.region(region).knownMask().unsignedByte(offset));
                }
            }
        }
    }
    private static void assertWindow(CdR12aCameraArtResidency.State current,CdAssetBytes cpu) {
        for(int at=0;at<1152;at+=32)assertArrayEquals(cpu.slice(at,32).copy(),CdR12aCameraArtResidency.readTile(current,(0xA880+at)>>>5).copy());
    }
    private static byte[] dirtyCpu(int seed) {var out=new byte[1152];for(int i=0;i<out.length;i++)out[i]=(byte)(seed+i*29+(i>>>7));return out;}
    /** Genuine existing controller recipe reaches the old programme return; no seeded Results object. */
    private static Prepared oldReturned() throws Exception {
        var old = ready();
        for (int call = old.returned() + 1; call <= 10419; call++) step(old.harness(), controller(call));
        assertEquals(SonicCdGameModule.PastLoadPhase.OLD_IPX_RETURNED, old.harness().module().pastLoadState().phase());
        assertFalse(old.frame().closed());
        return old;
    }
    private static LogicalInputSnapshot neutral() {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), PlayerInputState.neutral());
    }
    private static Scene ordinary() throws Exception {
        var old=oldReturned();step(old.harness(),neutral());var h=old.harness();
        for(int count=0;count<64;count++) {step(h,neutral());if(h.module().act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN))return scene(h);}
        throw new AssertionError("Normal load did not return its first ordinary cut");
    }
    private static Scene scene(Harness h) throws Exception {return new Scene(h,(SonicCdAct2LoadingVisit)actualField(h.module(),"act2Loading"),h.module().requirePlayerFrame());}
    private static Object values(Scene s) throws Exception {return actualField(s.visit(),"values");}
    private static CdLevelGlobals level(Scene s) throws Exception {return (CdLevelGlobals)actualField(values(s),"level");}
    @SuppressWarnings("unchecked") private static Optional<CdAssetBytes> cpu(Scene s) throws Exception {
        return (Optional<CdAssetBytes>)actualField(actualField(values(s),"loop"),"powerupCpu");
    }
    private static CdR12aCameraArtResidency.State current(Scene s) throws Exception {return ((CdR12aSectionArt.State)actualField(values(s),"section")).residency();}
    private static void setCpu(Scene s,Optional<CdAssetBytes> value) throws Exception {
        Object v=values(s);set(s.visit(),"values",record(v,"loop",record(actualField(v,"loop"),"powerupCpu",value)));
    }
    private static void setPowerup(Scene s,int value) throws Exception {
        Object v=values(s);var l=level(s);set(s.visit(),"values",record(v,"level",new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),
                l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),value,l.timeOver())));
    }
    private static Object record(Object original,String name,Object value) throws Exception {
        var parts=original.getClass().getRecordComponents();var types=new Class<?>[parts.length];var args=new Object[parts.length];
        for(int i=0;i<parts.length;i++) {types[i]=parts[i].getType();var accessor=parts[i].getAccessor();accessor.setAccessible(true);args[i]=parts[i].getName().equals(name)?value:accessor.invoke(original);}
        var ctor=original.getClass().getDeclaredConstructor(types);ctor.setAccessible(true);return ctor.newInstance(args);
    }
    private static void directConstructedService(Scene s) throws Exception {
        set(s.visit(),"servicing",true);try {invoke(s.visit(),"loadPowerupArt");}finally {set(s.visit(),"servicing",false);}
    }
    private static void set(Object owner,String name,Object value) throws Exception {var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);}
    private static Object invoke(Object owner,String name) throws Exception {
        var method=owner.getClass().getDeclaredMethod(name);method.setAccessible(true);
        try {return method.invoke(owner);}catch(InvocationTargetException wrapped) {
            if(wrapped.getCause() instanceof Error e)throw e;if(wrapped.getCause() instanceof RuntimeException e)throw e;throw wrapped;
        }
    }
}
