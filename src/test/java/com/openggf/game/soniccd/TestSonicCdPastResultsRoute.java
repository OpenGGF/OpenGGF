package com.openggf.game.soniccd;

import com.openggf.audio.NullAudioBackend;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import com.openggf.game.soniccd.resources.CdLevelHud;
import com.openggf.game.soniccd.resources.CdR11bCameraArtResidency;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.RetainedPlaneSceneRenderer;
import com.openggf.graphics.SpritePresentation;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.BufferUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/**
 * Controller-only Results10394 pixels and native old-programme return10419; no Act2 admission.
 * Native N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Results/Main.asm:61-272;
 * Object Functions.asm:137-196,275-346. Original USA/FixBugs=0.
 * The pixel case stops at10394; the exit case reaches10419 before Act2 admission. No actor, global,
 * pool, terrain, art, camera, queue, clock or Frame seed, input search or failed restore.
 * Frozen input call numbers select only this fixture's input/positive bound, never production.
 * Actual current selected scene is drawn; renderer-only whole32 omission checks Results pixels.
 * No visible-Sonic, native-image parity, >=50 BigRing touch, new act, new LoadMMD or save claim.
 */
@Isolated
class TestSonicCdPastResultsRoute {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}

    @Test void naturalResultsReturnThroughBlackFadeAndIpx10419WithFullAndCourseReplay() throws Exception {
        var p=ready();var h=p.harness();var owners=new Owners(p);var evidence=new Evidence();
        for(int call=p.returned()+1;call<=10394;call++)advance(p,call,owners,evidence);
        assertFinalResults(p,evidence);
        var input=controller(10394); // The same held Right continues; no gameplay state ingress.
        long beforeClock=p.frame().vIntClock().absoluteValue().orElseThrow();
        int beforeFrames=p.frame().environment().globals().levelFrames();
        CdPlayerFrameRuntime.ActorState stoppedActor=null;
        CdObjectPlacementRuntime.Snapshot stoppedPlacement=null;
        for(int offset=0;offset<=24;offset++) {
            var phase=offset<22?SonicCdGameModule.PastLoadPhase.OLD_BLACK_FADE
                    :offset==22?SonicCdGameModule.PastLoadPhase.OLD_CLEAR_WAIT
                    :offset==23?SonicCdGameModule.PastLoadPhase.OLD_RETURNED:SonicCdGameModule.PastLoadPhase.OLD_IPX_RETURNED;
            boolean replay=offset==0||offset==1||offset==11||offset>=22;
            var registry=h.mode().getRewindRegistry();var full=replay?registry.capture():null;
            var course=replay?registry.captureCourse():null;var before=replay?captureNative(h):null;
            step(h,input);assertOldExitOwners(p,owners,phase);
            if(replay) {
                var after=captureNative(h);
                registry.restore(full);assertNativeCapture(before,h);step(h,input);assertOldExitOwners(p,owners,phase);assertNativeCapture(after,h);
                registry.restoreCourse(course);assertNativeCapture(before,h);step(h,input);assertOldExitOwners(p,owners,phase);assertNativeCapture(after,h);
            }
            if(offset==0){stoppedActor=p.frame().captureActor();stoppedPlacement=p.placement().capture();}
            else {CdCapturedValueAssertions.assertCapturedValueEquals(stoppedActor,p.frame().captureActor());assertPlacement(stoppedPlacement,p);}
            assertEquals((beforeFrames+1)&0xFFFF,p.frame().environment().globals().levelFrames());
            assertEquals(beforeClock+1+Math.min(offset,23),p.frame().vIntClock().absoluteValue().orElseThrow());
            assertEquals(Math.min(offset,22),h.module().levelProgramState().whiteFadeSteps());
            assertEquals(offset<22?1:0,h.module().levelProgramState().palFadeFlags());
            assertEquals(offset<22?2:0,h.module().pastLoadState().level().levelRestart());
            assertEquals(1,p.frame().environment().movement().gates().zoneAct());
            assertEquals(1,p.frame().environment().movement().gates().timeZone());
            if(offset>=22)assertArrayEquals(new byte[128],h.module().pastLoadState().cpuPalette().copy());
            if(offset>=23)assertEquals(0xFF,h.module().pastOldProgrammeReturnCode());
        }
        assertArrayEquals(new byte[128],h.module().pastLoadState().displayedPalette().copy());
        var sound=assertInstanceOf(SonicCdGameModule.PastProgrammeSound.class,h.module().pastLoadState().sounds().getLast());
        assertEquals(SonicCdGameModule.ProgramSound.STOP_FM,sound.sound());
        assertOldExitOwners(p,owners,SonicCdGameModule.PastLoadPhase.OLD_IPX_RETURNED);
        System.out.println("Past controller returned10419: Results NextLevel,22 black services,clear-C,FF return and IPX blank; next admission is covered by the Act2 controller case.");
    }

    private static void assertOldExitOwners(Prepared p,Owners owners,SonicCdGameModule.PastLoadPhase phase)throws Exception {
        var h=p.harness();assertFalse(p.frame().closed());assertSame(p.frame(),h.module().requirePlayerFrame());
        assertSame(owners.world(),h.mode().getWorldSession());assertEquals(owners.generation(),h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertSame(owners.source(),p.frame().stageSource());assertSame(owners.visit(),actualField(h.module(),"pastLoading"));
        assertSame(owners.pool(),p.placement().pool());assertSame(owners.assets(),h.module().pastLoadState().assets());
        assertSame(owners.body(),actualField(p.visit(),"results"));assertEquals(phase,h.module().pastLoadState().phase());
        assertNull(actualField(p.visit(),"drawQueue"));assertNull(actualField(p.visit(),"activeResults"));
        assertFalse((boolean)actualField(p.visit(),"resultsPort"));assertFalse((boolean)actualField(p.visit(),"drawingResults"));
        assertFalse((boolean)actualField(p.visit(),"oldProgrammeContinuation"));
    }

    @Test void naturalResultsCompleteBonusesReturn10394ReplayBothScopesAndDrawTheirSelectedPixels() throws Exception {
        byte[] imageForExport;
        try(var egl=Egl.open()) {
            assertInstanceOf(NullAudioBackend.class,GameServices.audio().getBackend());
            var p=ready();var h=p.harness();var owners=new Owners(p);var evidence=new Evidence();
            assertInstanceOf(NullAudioBackend.class,GameServices.audio().getBackend());
            for(int call=p.returned()+1;call<=10393;call++)advance(p,call,owners,evidence);
            assertTrue(evidence.delayObserved);assertTrue(evidence.cueObserved);assertEquals(3,evidence.rows.size());
            assertTrue(evidence.awards>0);assertTrue(evidence.points>0);assertEquals(1,evidence.kachings);
            assertEquals(6,p.placement().pool().state(32).routine());assertEquals(0,p.placement().pool().state(32).word(RESULTS_TIMER_WORD));
            var registry=h.mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
            var before10394=captureNative(h);var beforePool=p.placement().capture();var selectedBefore=h.module().pastSelectedDisplay();var clockBefore=p.frame().vIntClock();
            owners.healthy(p);assertPlacement(beforePool,p);advance(p,10394,owners,evidence);assertFinalResults(p,evidence);
            assertEquals(10394,evidence.expired);var after10394=captureNative(h);var afterPool=p.placement().capture();
            var selectedAfter=h.module().pastSelectedDisplay();var clockAfter=p.frame().vIntClock();
            System.out.printf("Past Results returned10394: rows=%s awards=%d points=%d tallyRequests=%d Kaching=%d score=%d nextLife=%d; bonuses zero, programme not entered.%n",
                    evidence.rows.keySet(),evidence.awards,evidence.points,evidence.tallies,evidence.kachings,score(p).score(),score(p).nextLifeScore());
            registry.restore(full);assertNativeCapture(before10394,h);assertSame(selectedBefore,h.module().pastSelectedDisplay());assertEquals(clockBefore,p.frame().vIntClock());owners.healthy(p);assertPlacement(beforePool,p);
            step(h,controller(10394));owners.healthy(p);assertNativeCapture(after10394,h);assertPlacement(afterPool,p);assertEquals(selectedAfter,h.module().pastSelectedDisplay());assertEquals(clockAfter,p.frame().vIntClock());assertFinalResults(p,evidence);
            registry.restoreCourse(course);assertNativeCapture(before10394,h);assertSame(selectedBefore,h.module().pastSelectedDisplay());assertEquals(clockBefore,p.frame().vIntClock());owners.healthy(p);assertPlacement(beforePool,p);
            step(h,controller(10394));owners.healthy(p);assertNativeCapture(after10394,h);assertPlacement(afterPool,p);assertEquals(selectedAfter,h.module().pastSelectedDisplay());assertEquals(clockAfter,p.frame().vIntClock());assertFinalResults(p,evidence);

            var selected=h.module().pastSelectedDisplay();var scene=readyScene(selected);var selectedCpu=selected.inputs().selectedCpu().orElseThrow();
            assertTrue(selectedCpu.provenance().stream().anyMatch(v->v.slotRef().wholeIndex()==32));
            assertCurrentKnown(selected.inputs().current());
            var actual=draw(h);assertNativeCapture(after10394,h);imageForExport=actual.clone();
            assertArrayEquals(actual,draw(h));assertNativeCapture(after10394,h);assertEquals(selected,h.module().pastSelectedDisplay());
            var presentation=(SonicCdPlayerPresentation)actualField(h.module(),"playerPresentation");
            var binding=actualField(presentation,"pastRenderBinding");var renderer=(RetainedPlaneSceneRenderer)actualField(binding,"renderer");var camera=h.mode().getCamera();
            clear();renderer.draw(omitWhole(scene,selectedCpu,32),camera.getX(),camera.getY(),h.module()::validatePastLoadingCut);
            assertFalse(Arrays.equals(actual,pixels()),"The actual selected Results parent contributes visible engine pixels");
            assertNativeCapture(after10394,h);assertEquals(selected,h.module().pastSelectedDisplay());
            assertArrayEquals(actual,draw(h));assertNativeCapture(after10394,h);assertFinalResults(p,evidence);

            var atlas=egl.graphics.getPatternAtlas();var installed=scene.nativePatterns().keySet();assertFalse(installed.isEmpty());
            for(int id:installed)assertNotNull(atlas.getEntry(PatternAtlasRange.OBJECTS.base()+id));
            // GPU-only unrelated retirement contrast, never gameplay current art or a replacement source.
            atlas.registerRange(PatternAtlasRange.HUD);int other=PatternAtlasRange.HUD.base()+19;
            if(atlas.getEntry(other)==null)egl.graphics.cachePatternTexture(new com.openggf.level.Pattern(),other);
            var unrelated=atlas.getEntry(other);var sample=SpritePresentation.patternSample(egl.graphics,other);
            var mask=egl.graphics.getTilePriorityFBO();int texture=mask.getTextureId();
            SessionManager.closeGameplaySession();
            for(int id:installed){assertNull(atlas.getEntry(PatternAtlasRange.OBJECTS.base()+id));assertNull(SpritePresentation.patternSample(egl.graphics,PatternAtlasRange.OBJECTS.base()+id));}
            assertSame(unrelated,atlas.getEntry(other));assertEquals(sample,SpritePresentation.patternSample(egl.graphics,other));assertSame(mask,egl.graphics.getTilePriorityFBO());assertTrue(glIsTexture(texture));
        } finally {fixture.cleanup();}
        writeOptionalCapture(imageForExport); // Only after session, graphics, context AND fixture cleanup.
    }

    private static final class Evidence {
        final Map<Integer,CdObjectState> rows=new TreeMap<>();
        CdObjectState signpost,bigRing;
        boolean delayObserved,cueObserved;int awards,points,tallies,kachings,expired;
    }
    private static void advance(Prepared p,int call,Owners owners,Evidence e)throws Exception {
        var pool=p.placement().capture();var parent=pool.pool().slots().getFirst();var oldBonus=bonus(p);var oldScore=score(p);var oldRing=p.harness().module().pastLoadState().ring();
        step(p.harness(),controller(call));owners.healthy(p);var now=p.placement().pool().state(32);
        if(parent.id()!=0x3A || now.id()!=0x3A)return;
        if(parent.routine()==0) {
            e.delayObserved=true;var expected=parent.addByte(RESULTS_TIMER,-1);
            if(expected.unsignedByte(RESULTS_TIMER)==0){expected=expected.addByte(ROUTINE,2);e.cueObserved=true;assertNotEquals(0,p.harness().module().pastLoadState().section().queue().headSource());}
            assertEquals(expected,now,"Native Init changes only high timer BYTE, then routine on expiry; no Draw");
        }
        if(parent.routine()==2 && now.routine()==4 && e.rows.isEmpty()) {
            var rows=p.placement().capture().pool().slots().stream().filter(s->s.id()==0x3A).toList();assertEquals(3,rows.size());
            var free=pool.pool().slots().stream().filter(s->s.id()==0).map(s->s.identity().wholeIndex()).limit(2).toList();
            assertEquals(free,rows.stream().skip(1).map(s->s.identity().wholeIndex()).toList());
            var raw=p.assets().view(CdR11bAssets.Asset.RESULTS_INIT_DATA).bytes();int act=p.frame().environment().movement().gates().zoneAct()&255;
            boolean ssz=p.frame().environment().movement().gates().zoneAct()==0x502;
            var dev=(CdOpeningObjectRuntime.DevGlobals)actualField(globals(p),"retainedDev");
            int root=p.assets().view(CdR11bAssets.Asset.MAP_SPR_RESULTS).range().nativeAddress()+(ssz?10:0)+(dev.goodFuture()==0?0:20);
            for(int row=0;row<rows.size();row++) {
                var s=rows.get(row);assertEquals(raw.unsignedWord(row*8),s.word(RESULTS_SCREEN_Y_WORD));assertEquals(raw.unsignedWord(row*8+4),s.word(RESULTS_DEST_X));
                assertEquals(root,s.longBits(MAPPINGS));assertEquals(ssz?0x82F2:0x83C4,s.word(TILE));
                assertEquals((raw.unsignedByte(row*8+7)+(row==2?act:0))&255,s.unsignedByte(MAP_FRAME));assertEquals(0,s.spriteFlags()&12);
                assertEquals(row==0?480:479,s.word(RESULTS_TIMER_WORD),"Later physical children make their native first Move in this same pass");
                assertEquals(4,s.routine());e.rows.put(s.identity().wholeIndex(),s);
            }
            e.signpost=p.placement().pool().state(33);e.bigRing=p.placement().pool().state(34);
        }
        if(parent.routine()==6) {
            int points=(oldBonus.timeBonusWord()!=0?10:0)+(oldBonus.ringBonusWord()!=0?10:0);var b=bonus(p);var sound=resultsSounds(p);
            assertEquals(oldBonus.timeBonusWord()==0?0:(oldBonus.timeBonusWord()-100)&0xFFFF,b.timeBonusWord());
            assertEquals(oldBonus.ringBonusWord()==0?0:(oldBonus.ringBonusWord()-100)&0xFFFF,b.ringBonusWord());assertEquals(1,b.updateHudBonusByte());
            if(points!=0) {
                var expected=CdPlayerTouchResponse.addPoints(new CdPlayerTouchResponse.WorldGlobals(oldRing.rings(),oldRing.lives(),oldRing.updateHudLives(),oldScore.score(),oldScore.nextLifeScore(),oldScore.updateHudScore()),points);
                assertEquals(expected.world().score(),score(p).score());assertEquals(expected.world().nextLifeScore(),score(p).nextLifeScore());assertEquals(1,score(p).updateHudScore());
                assertEquals(expected.world().lives(),p.harness().module().pastLoadState().ring().lives());e.awards++;e.points+=points;
                if(b.timeBonusWord()==0 && b.ringBonusWord()==0){assertEquals(List.of(CdResults.Sound.DRIVER_KACHING),sound);e.kachings++;}
                else {var expectedSound=(now.unsignedByte(RESULTS_TIMER)&1)==0?List.of(CdResults.Sound.FM_TALLY):List.<CdResults.Sound>of();assertEquals(expectedSound,sound);e.tallies+=sound.size();}
                var ledger=p.harness().module().pastLoadState().sounds();long oneUps=ledger.stream().filter(s->s instanceof SonicCdGameModule.PastFrameSound f && f.sound() instanceof CdPlayerFrameRuntime.ContactSubCpuCommand c && c.command()==CdPlayerTouchResponse.SubCpuCommand.SCMD_YESSFX).count();
                assertEquals(expected.command().isPresent()?1:0,oneUps);
            } else if(now.routine()==8) {assertEquals(0,parent.word(RESULTS_TIMER_WORD));assertEquals(0xFFFF,now.word(RESULTS_TIMER_WORD));assertEquals(0,e.expired);e.expired=call;}
        }
    }
    private static void assertFinalResults(Prepared p,Evidence e)throws Exception {
        assertEquals(3,e.rows.size());assertEquals(0,bonus(p).timeBonusWord());assertEquals(0,bonus(p).ringBonusWord());assertEquals(1,bonus(p).updateHudBonusByte());
        var raw=p.assets().view(CdR11bAssets.Asset.RESULTS_INIT_DATA).bytes();int row=0;
        for(var entry:e.rows.entrySet()) {
            var current=p.placement().pool().state(entry.getKey());boolean parent=entry.getKey()==32;var old=entry.getValue();
            assertEquals(old.moveWord(X,old.word(RESULTS_DEST_X)).moveWord(RESULTS_TIMER_WORD,parent?0xFFFF:0).moveByte(ROUTINE,parent?8:4).moveByte(SPRITE_FLAGS,current.spriteFlags()),current);
            assertEquals(old.spriteFlags()&0x7F,current.spriteFlags()&0x7F);assertEquals(0x80,current.spriteFlags()&0x80);assertEquals(0,current.spriteFlags()&12);
            assertEquals(raw.unsignedWord(row++*8),current.word(RESULTS_SCREEN_Y_WORD));assertNotEquals(current.centreY(),current.word(RESULTS_SCREEN_Y_WORD));
        }
        var sign=p.placement().pool().state(33);assertEquals(e.signpost.moveByte(SPRITE_FLAGS,sign.spriteFlags()),sign);assertEquals(8,sign.routine());
        assertEquals(e.bigRing,p.placement().pool().state(34));assertEquals(0,p.placement().pool().state(34).routine());assertTrue(p.harness().module().pastLoadState().ring().rings()<50);
        assertEquals(0,p.harness().module().pastLoadState().level().levelRestart());assertTrue(resultsSounds(p).isEmpty());
        assertEquals(0,p.harness().module().pastLoadState().section().queue().headSource());assertEquals(3,p.harness().module().pastLoadState().calls().stream().filter(c->c==SonicCdGameModule.PastLoadCall.RESULTS_BODY).count());
        var selected=p.harness().module().pastSelectedDisplay();var selectedCpu=selected.inputs().selectedCpu().orElseThrow();var completed=completedCpu(p);
        assertEquals(56,selectedCpu.spriteCount());assertEquals(selectedCpu.spriteCount(),completed.spriteCount());assertArrayEquals(selectedCpu.bytes(),completed.bytes());assertEquals(selectedCpu.provenance(),completed.provenance());
        assertTrue(selectedCpu.provenance().stream().anyMatch(v->v.slotRef().wholeIndex()==32));assertFalse(selectedCpu.provenance().stream().anyMatch(v->v.slotRef().wholeIndex()==34));readyScene(selected);
    }
    private static void assertCurrentKnown(CdR11bCameraArtResidency.State current) {
        int known=0;for(var region:CdR11bCameraArtResidency.Region.values()){var mask=current.region(region).knownMask();for(byte value:mask.copy()){assertNotEquals(0,value);known++;}}
        assertEquals(49344,known,"Actual complete current-profile knowledge; no fallback patterns");
    }
    private record Owners(Object world,long generation,Object source,Object visit,Object pool,Object assets,Object programme,Object body) {
        Owners(Prepared p)throws Exception {this(p.harness().mode().getWorldSession(),p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration(),p.frame().stageSource(),p.visit(),p.placement().pool(),p.assets(),p.harness().module().levelProgramState(),actualField(p.visit(),"results"));}
        void healthy(Prepared p)throws Exception {var h=p.harness();assertFalse(p.frame().closed());assertSame(p.frame(),h.module().requirePlayerFrame());assertSame(world,h.mode().getWorldSession());assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
            assertSame(source,p.frame().stageSource());assertSame(visit,actualField(h.module(),"pastLoading"));assertSame(pool,p.placement().pool());assertSame(assets,h.module().pastLoadState().assets());assertSame(programme,h.module().levelProgramState());assertSame(body,actualField(visit,"results"));
            for(String owner:List.of("Goal","Signpost","BigRing","Results")){assertNull(actualField(visit,"active"+owner));String lower=Character.toLowerCase(owner.charAt(0))+owner.substring(1);assertFalse((boolean)actualField(visit,lower+"Port"));assertFalse((boolean)actualField(visit,"drawing"+owner));}
            assertNull(actualField(visit,"drawQueue"));assertEquals(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN,h.module().pastLoadState().calls().getLast());}
    }
    private static void assertPlacement(CdObjectPlacementRuntime.Snapshot expected,Prepared p){p.placement().validateRestore(expected);var now=p.placement().capture();assertEquals(expected.status(),now.status());assertArrayEquals(expected.savedFlags().copy(),now.savedFlags().copy());for(var image:expected.pool().slots())assertEquals(image,p.placement().pool().state(image.identity().wholeIndex()));}
    private static Object globals(Prepared p)throws Exception{return actualField(actualField(p.visit(),"values"),"globals");}
    private static CdLevelHud.BonusValues bonus(Prepared p)throws Exception{return (CdLevelHud.BonusValues)((Optional<?>)actualField(globals(p),"bonus")).orElseThrow();}
    private static CdOpeningObjectRuntime.ScoreGlobals score(Prepared p)throws Exception{return (CdOpeningObjectRuntime.ScoreGlobals)actualField(globals(p),"score");}
    private static CdObjectDrawQueue.CpuSat completedCpu(Prepared p)throws Exception{return assertInstanceOf(CdObjectDrawQueue.CpuSat.class,((Optional<?>)actualField(actualField(actualField(p.visit(),"values"),"loop"),"completedDraw")).orElseThrow());}
    private static List<CdResults.Sound> resultsSounds(Prepared p){return p.harness().module().pastLoadState().sounds().stream().filter(SonicCdGameModule.PastResultsSound.class::isInstance).map(SonicCdGameModule.PastResultsSound.class::cast).map(SonicCdGameModule.PastResultsSound::sound).toList();}

    /** Frozen Goal/ActExit recipe, extending only this fixture's positive bound. */
    private static LogicalInputSnapshot controller(int call) {
        if(call<1 || call>10394)throw new IllegalArgumentException("Positive Results route ends at10394");
        if(call<=8276)return route(call);
        int directions=8,actions=call<=8308?2:0;
        if(call>=9001 && call<=9203)directions=4;
        else if(call>=9206 && call<=9217)directions=0;
        else if(call>=9218 && call<=9263){directions=2;actions=call==9219?2:0;}
        else if(call>=9336 && call<=9367)actions=2;
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions,0,actions,0,false,false),PlayerInputState.neutral());
    }

    private static RetainedPlaneSceneRenderer.Scene readyScene(SonicCdPastDisplay.Selection selection) {
        return assertInstanceOf(SonicCdPastDisplay.Ready.class, selection.projection(), "Actual selected-image prerequisite").scene();
    }
    private static byte[] draw(Harness h) {
        readyScene(h.module().pastSelectedDisplay()); clear(); h.module().gameplayFrameController().drawOverlay(); return pixels();
    }
    private static void clear() { glViewport(0, 0, 320, 224); glClearColor(1, 0, 1, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); }
    private static byte[] pixels() {
        glFinish(); var buffer = BufferUtils.createByteBuffer(320 * 224 * 4);
        glReadPixels(0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, buffer); assertEquals(GL_NO_ERROR, glGetError());
        byte[] result = new byte[buffer.capacity()]; buffer.get(result); return result;
    }
    /** Existing pure selected-display omission algorithm, parameterized by actual physical provenance. */
    static RetainedPlaneSceneRenderer.Scene omitWhole(RetainedPlaneSceneRenderer.Scene scene, CdObjectDrawQueue.CpuSat cpu, int whole) {
        byte[] raw = cpu.bytes(); var tiles = new ArrayList<SpritePresentation.Tile>(); int at = 0;
        for (int index = cpu.spriteCount() - 1; index >= 0; index--) {
            int size = raw[index * 8 + 2] & 15, count = (((size >>> 2) & 3) + 1) * ((size & 3) + 1);
            if (cpu.provenance().get(index).slotRef().wholeIndex() != whole) tiles.addAll(scene.sprites().tiles().subList(at, at + count));
            at += count;
        }
        assertEquals(scene.sprites().tiles().size(), at); assertTrue(tiles.size() < at, "Selected CPU must actually contain whole" + whole);
        var picture = new SpritePresentation.Frame(tiles, List.of(), scene.sprites().patternVersions());
        return new RetainedPlaneSceneRenderer.Scene(scene.foreground(), scene.background(), scene.packedHscroll256(),
                scene.vscrollLongBits(), scene.selectedPalette128(), true, picture, scene.nativePatterns());
    }

    /** Optional engine pixels only; no expected image/runtime state is read from this output. */
    private static void writeOptionalCapture(byte[] rgba) throws Exception {
        String output = System.getenv("SONICCD_PAST_RESULTS_OUTPUT"); if (output == null || output.isBlank()) return;
        var directory = Path.of(output).toAbsolutePath().normalize();
        var owned = Path.of(System.getProperty("user.home"), "Downloads", "Sonic-CD", "captures", "engine").toAbsolutePath().normalize();
        assertTrue(directory.startsWith(owned) && !directory.equals(owned), "Optional export needs its own directory under the engine capture root");
        Files.createDirectories(directory);
        var image = new java.awt.image.BufferedImage(320, 224, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) {
            int at = ((223 - y) * 320 + x) * 4;
            int argb = (rgba[at + 3] & 255) << 24 | (rgba[at] & 255) << 16 | (rgba[at + 1] & 255) << 8 | rgba[at + 2] & 255;
            image.setRGB(x, y, argb);
        }
        if (!javax.imageio.ImageIO.write(image, "png", directory.resolve("past-world-call-10394.png").toFile()))
            throw new java.io.IOException("PNG writer unavailable");
    }

    static final class Egl implements AutoCloseable {
        private final PresentationOffscreenTestContext context; private final GraphicsManager graphics;
        private Egl(PresentationOffscreenTestContext context, GraphicsManager graphics) { this.context = context; this.graphics = graphics; }
        static Egl open() throws Exception {
            assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual World EGL/media opt-in");
            assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
            assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
            var context = PresentationOffscreenTestContext.openConfigured(320, 224, PresentationOffscreenTestContext.LegacyProfile.CORE_41);
            var graphics = GameServices.graphics();
            try {
                graphics.setHeadlessMode(false); graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setProjectionWidth(320); graphics.setViewport(0, 0, 320, 224);
                graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, 320, 0, 224, -1, 1).get(new float[16]));
                graphics.setWindowHeight(224); graphics.setScreenHeight(224); glViewport(0, 0, 320, 224);
                return new Egl(context, graphics);
            } catch (Exception | Error failure) {
                try { graphics.cleanup(); } catch (Exception | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
                try { GraphicsManager.destroyForReinit(); } catch (Exception | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
                try { context.close(); } catch (Exception | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
        @Override public void close() {
            try { SessionManager.closeGameplaySession(); }
            finally { try { graphics.cleanup(); }
                finally { try { GraphicsManager.destroyForReinit(); TestEnvironment.resetAll(); } finally { context.close(); } } }
        }
    }
}
