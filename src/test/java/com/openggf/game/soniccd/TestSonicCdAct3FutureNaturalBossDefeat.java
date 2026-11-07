package com.openggf.game.soniccd;

import com.openggf.audio.NullAudioBackend;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.graphics.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.ready;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static org.lwjgl.opengl.GL11.*;

/** Permanent one-case original-USA controller qualification through fixed19658. Author Java/GL/media UNRUN.
 * J e20af69f7deec18a7bdcb3cdce44288a252e3379; N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Root R53 supplies bounded output checkpoints only, never gameplay state or an adaptive controller.
 * No actor/camera/pool/clock/physics/progress/queue/art seeds. Only genuine FULL/course restores.
 * Reflection creates only an existing TEST comparison wrapper over actual captured owner references;
 * it never constructs a Runtime, invocation, producer, admission or capture authority.
 * A separately labelled GPU-only retirement witness is excluded from all native/source/pixel coverage.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
@Isolated
class TestSonicCdAct3FutureNaturalBossDefeat {
    private static final int END=19658;
    private static final Set<Integer> REPLAY=Set.of(19198,19284,19317,19658);
    private static final LogicalInputSnapshot RIGHT=LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,0,0,false,false),PlayerInputState.neutral());
    private static final LogicalInputSnapshot RIGHT_B=LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,2,0,false,false),PlayerInputState.neutral());

    @Test void originalR53ControllerDefeatsBossAndReplaysSelectedDamageRemovalAndFixedPixels() throws Exception {
        var fixture=new TestSonicCdPastLoadingWorld();fixture.setup();var exports=new LinkedHashMap<String,Picture>();String diagnostic;
        try(var egl=TestSonicCdPastResultsRoute.Egl.open()) {
            assertInstanceOf(NullAudioBackend.class,GameServices.audio().getBackend());var prepared=ready();var h=prepared.harness();var helper=new Comparisons();
            // This is the unchanged public GameLoop/controller route, not a preloaded R13 fixture.
            for(int call=prepared.returned()+1;call<=17142;call++)step(h,input(helper,call));
            var o=Owners.actual(h,helper);helper.owners(h,o);var assets=CdR13BossAssets.bind(o.assets());var capsuleData=CdR13CapsuleAssets.bind(o.assets());
            var capsuleRows=o.assets().placements().forPeriod(CdPeriod.BAD_FUTURE).stream().filter(r->r.objectId()==0x15).toList();assertEquals(1,capsuleRows.size());var capsuleRow=capsuleRows.getFirst();
            assertEquals(0xE48,capsuleRow.centreX());assertEquals(0x3A0,capsuleRow.centreY());assertEquals(0x95,capsuleRow.encodedId());assertEquals(0xE0,capsuleRow.periodFlags());
            var events=new ArrayList<String>();int firstDefeat=-1,firstEscape=-1,firstExit=-1,removed=-1,firstCapsule=-1,firstRelease=-1;boolean machine=false,points=false,explosions=false;
            for(int call=17143;call<=END;call++) {
                var control=input(helper,call);boolean replay=REPLAY.contains(call);var registry=h.mode().getRewindRegistry();
                var beforePool=o.host().placement().pool().capture();var beforeParent=beforePool.slots().getFirst();var beforeCpu=o.host().video().completedCpu();
                var beforeClock=o.frame().vIntClock();int beforeFrames=o.frame().environment().globals().levelFrames();var beforeScore=o.host().globals().score();int beforeFight=o.frame().environment().movement().bounds().bossFight();
                var full=replay?registry.capture():null;var course=replay?registry.captureCourse():null;byte[] beforePixels=replay?helper.image(h,o,call-1):null;
                if(replay)helper.snapshots(h,full,course,"actual before "+call);
                step(h,control);helper.owners(h,o);assertEquals((beforeFrames+1)&0xFFFF,o.frame().environment().globals().levelFrames());assertEquals(beforeClock.deltaSinceAdmittedCut()+1,o.frame().vIntClock().deltaSinceAdmittedCut());assertFalse(o.frame().paused());
                var state=o.core().state();assertEquals(SonicCdAct3FutureLoadingVisit.Phase.READY,state.phase());assertFalse(o.core().failed());assertFalse(o.frame().closed());
                var selection=assertInstanceOf(SonicCdAct3FutureWorldHost.Published.class,o.host().selected()).selection();helper.equal(beforeCpu,selection.inputs().selectedCpu(),"original prior CPU at "+call);if(beforeCpu.isPresent())assertSame(beforeCpu.orElseThrow(),selection.inputs().selectedCpu().orElseThrow());
                assertEquals("DONE",field(field(field(o.ordinary(),"binding"),"objects"),"phase").toString());
                var pool=o.host().placement().pool().capture();var parent=pool.slots().getFirst();int fight=o.frame().environment().movement().bounds().bossFight();
                if(parent.id()==0x2A) {
                    assertEquals(32,parent.identity().wholeIndex());assertEquals(0x3FD,parent.word(TILE));assertEquals(assets.range(CdR13BossAssets.Table.EGGMAN_MAPPINGS).nativeAddress(),parent.longBits(MAPPINGS));
                    if(beforeParent.id()!=0x2A||parent.routine()!=beforeParent.routine()||parent.routine()==4&&parent.unsignedByte(BOSS_COUNTER)!=beforeParent.unsignedByte(BOSS_COUNTER))events.add(call+":ID2A/r"+parent.routine()+"/role="+(parent.routine()==4?"health:":"phase-byte:")+parent.unsignedByte(BOSS_COUNTER));
                    if(call==18160){assertEquals(4,parent.routine());assertEquals(3,parent.unsignedByte(BOSS_COUNTER));machine(pool,parent,assets);machine=true;}
                    if(call==19198||call==19284){assertEquals(4,beforeParent.routine());assertEquals(4,parent.routine());assertEquals(call==19198?3:2,beforeParent.unsignedByte(BOSS_COUNTER));assertEquals(beforeParent.unsignedByte(BOSS_COUNTER)-1,parent.unsignedByte(BOSS_COUNTER));assertEquals(0x14,parent.unsignedByte(BOSS_HIT_TIMER));assertEquals(1,fight);}
                    if(parent.routine()==6&&firstDefeat<0){firstDefeat=call;assertEquals(19317,call);assertEquals(4,beforeParent.routine());assertEquals(1,beforeParent.unsignedByte(BOSS_COUNTER));
                        // Native hit clears health and selects6; the SAME dispatch's routine6 adds1 for explosions.
                        assertEquals(1,parent.unsignedByte(BOSS_COUNTER));assertEquals(0,parent.unsignedByte(ROUTINE_2));assertEquals(0,parent.unsignedByte(BOSS_TIMER));assertEquals(0x14,parent.unsignedByte(BOSS_HIT_TIMER));assertEquals(0,parent.unsignedByte(COLLISION_TYPE));assertEquals(0,parent.unsignedByte(COLLISION_STATUS));assertEquals(0,parent.unsignedByte(BOSS_FLAGS)&8);assertEquals(beforeParent.word(BOSS_NEXT),parent.word(BOSS_NEXT));assertEquals(beforeParent.word(BOSS_BRANCH),parent.word(BOSS_BRANCH));}
                    if(parent.routine()==8&&firstEscape<0){firstEscape=call;assertEquals(6,beforeParent.routine());assertEquals(0x77,beforeParent.unsignedByte(BOSS_COUNTER));assertEquals(0,parent.unsignedByte(BOSS_COUNTER));assertEquals(32,parent.unsignedByte(WIDTH));assertEquals(32,parent.unsignedByte(Y_RADIUS));assertEquals(Math.min(999999,beforeScore.score()+100),o.host().globals().score().score());points=true;}
                    if(parent.routine()==10&&firstExit<0){firstExit=call;assertEquals(8,beforeParent.routine());assertEquals(0,fight);assertEquals(0,o.host().globals().bossFlagsByte().orElseThrow());assertEquals(0,parent.unsignedByte(BOSS_COUNTER));
                        var pal=capsuleData.capsulePalette();assertEquals(0xFB20,pal.destinationWord());assertEquals(7,pal.longwordCounter());assertArrayEquals(pal.bytes().copy(),state.normalPalette().slice(32,32).copy());}
                } else if(beforeParent.id()==0x2A) {
                    assertEquals(10,beforeParent.routine());assertEquals(19658,call);assertArrayEquals(new byte[64],parent.copyBytes());assertEquals(o.assets().bounds().right(),o.frame().environment().movement().bounds().right());assertEquals(o.assets().bounds().right(),o.host().targetRightBoundWord().orElseThrow());removed=call;events.add(call+":ID2A original DeleteObject64/right-bound clamp");
                }
                if(beforeFight!=0&&fight==0){firstRelease=call;events.add(call+":native BossFight/bossFlags release");}
                if(o.host().sounds().stream().anyMatch(s->s instanceof SonicCdGameModule.PastR13BossFm f&&f.sound()==CdR13Boss.FmSound.EXPLOSION))explosions=true;
                var capsules=pool.slots().stream().filter(s->s.id()==0x15).toList();assertTrue(capsules.size()<=1);
                if(!capsules.isEmpty()&&firstCapsule<0){firstCapsule=call;var c=capsules.getFirst();assertEquals(0,beforePool.slots().get(c.identity().dynamicIndex()).id(),"Native SpawnObjects reuses a genuinely free slot, not a Boss ID rewrite");capsule(o,c,capsuleRow,capsuleData);assertTrue(o.host().placement().status().rightRecordIndex()>capsuleRow.index());events.add(call+":placement row"+capsuleRow.index()+" spawned Capsule whole"+c.identity().wholeIndex()+" savedID"+c.unsignedByte(SAVED_FLAGS_ID));}
                if(replay) {
                    assertEquals(1,Collections.frequency(state.calls(),SonicCdAct3FutureLoadingVisit.Call.BOSS_PLC_APPEND));assertEquals(0,state.section().queue().headSource());assertEquals(0,state.section().remainingTilesWord());
                    var afterFull=registry.capture();var afterCourse=registry.captureCourse();byte[] afterPixels=helper.image(h,o,call);
                    Picture fixed=call==19317?picture(h,o,helper,call,beforePool,true):call==19658?picture(h,o,helper,call,beforePool,false):null;
                    if(fixed!=null){exports.put(call==19317?"defeat-19317":"capsule-19658",fixed);assertArrayEquals(afterPixels,fixed.rgba());}
                    helper.snapshots(h,afterFull,afterCourse,"after pixels "+call);
                    for(boolean courseScope:new boolean[]{false,true}) {
                        if(courseScope)registry.restoreCourse(course);else registry.restore(full);helper.owners(h,o);helper.snapshots(h,full,course,"restored before "+call+"/"+courseScope);assertArrayEquals(beforePixels,helper.image(h,o,call-1));helper.snapshots(h,full,course,"before replay "+call+"/"+courseScope);
                        step(h,control);helper.owners(h,o);helper.snapshots(h,afterFull,afterCourse,"original forward replay "+call+"/"+courseScope);assertArrayEquals(afterPixels,helper.image(h,o,call));
                        if(fixed!=null){var again=picture(h,o,helper,call,beforePool,call==19317);assertEquals(fixed.coverage(),again.coverage());assertEquals(fixed.wholes(),again.wholes());assertArrayEquals(fixed.rgba(),again.rgba());assertArrayEquals(fixed.without(),again.without());}
                        helper.snapshots(h,afterFull,afterCourse,"replayed pixels "+call+"/"+courseScope);
                    }
                }
            }
            assertTrue(machine&&points&&explosions);assertEquals(19317,firstDefeat);assertTrue(firstEscape>firstDefeat&&firstExit>firstEscape);assertEquals(firstExit,firstRelease);assertEquals(19658,removed);assertTrue(firstCapsule>firstDefeat&&firstCapsule<=END);
            assertEquals(0,o.frame().environment().movement().bounds().bossFight());assertEquals(0,o.host().globals().bossFlagsByte().orElseThrow());assertTrue(o.host().placement().pool().capture().slots().stream().noneMatch(s->s.id()>=0x2A&&s.id()<=0x32));
            assertEquals(1,o.host().sounds().stream().filter(s->s instanceof SonicCdGameModule.PastR13BossSubCpu c&&c.command()==CdR13Boss.CpuCommand.BAD_MUSIC).count());assertEquals(0,o.host().sounds().stream().filter(s->s instanceof SonicCdGameModule.PastR13BossSubCpu c&&c.command()==CdR13Boss.CpuCommand.GOOD_MUSIC).count());
            var finalCapsules=o.host().placement().pool().capture().slots().stream().filter(s->s.id()==0x15).toList();assertEquals(1,finalCapsules.size());capsule(o,finalCapsules.getFirst(),capsuleRow,capsuleData);
            var full=h.mode().getRewindRegistry().capture();var course=h.mode().getRewindRegistry().captureCourse();diagnostic=diagnostic(h,o,finalCapsules.getFirst(),capsuleRow,events);System.out.println(diagnostic);helper.snapshots(h,full,course,"read-only final19658 diagnostic");
            assertEquals(Set.of("defeat-19317","capsule-19658"),exports.keySet());for(var entry:exports.entrySet())System.out.println("ENGINE_FIXED_CUT_COVERAGE "+entry.getKey()+" "+entry.getValue().coverage()+" selectedWholes="+entry.getValue().wholes());
            retire(h,o); // Actual CD owner retirement plus an unrelated GPU resource witness, never scene priming.
        } finally {fixture.cleanup();}
        // No PNG/text output until Module/session/graphics/EGL/fixture cleanup is complete.
        export(exports,diagnostic);
    }

    /** Fixed R53 inputs: no read of actor, sounds, counters, slot IDs or image data can select input. */
    private static LogicalInputSnapshot input(Comparisons h,int call)throws Exception {
        if(call<=14516)return h.control("controller",call);if(call<=16187)return h.control("naturalR12bGoalController",call);
        return call>17142&&call<=17782&&(call-17143)%32<16||call>18028&&(call-18029)%32<16?RIGHT_B:RIGHT;
    }
    private record Owners(CdPlayerFrameRuntime frame,CdStagePlayerSource source,SonicCdAct3FutureWorldHost host,SonicCdAct3FutureLoadingVisit core,CdWorld ordinary,CdR13FutureAssets assets,SonicCdAct3FutureLevel level,Object world,long generation,Object comparison) {
        static Owners actual(Harness h,Comparisons helper)throws Exception{var m=h.module();assertTrue(m.act3FutureLoadingSelected());var f=m.requirePlayerFrame();var c=m.act3FutureLoadingVisit();var host=m.act3FutureWorldHost();var ordinary=assertInstanceOf(CdWorld.class,field(m,"act3FutureWorld"));var level=assertInstanceOf(SonicCdAct3FutureLevel.class,h.mode().getLevelManager().getCurrentLevel());var world=h.mode().getWorldSession();long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();var a=c.assets();assertEquals(CdNativeFile.R13D,a.identity().file());assertEquals(CdReleaseVariant.USA,a.identity().variant());var wrapper=helper.wrapper.newInstance(f,f.stageSource(),host,c,ordinary,a,level,world,generation);return new Owners(f,f.stageSource(),host,c,ordinary,a,level,world,generation,wrapper);}
    }
    /** Only the existing private TEST record is constructed; all arguments are observed actual refs. */
    private static final class Comparisons {
        final Class<?> type;final Constructor<?> wrapper;final Method owners,image,equal,snapshots;
        Comparisons()throws Exception{type=Arrays.stream(TestSonicCdAct2ControllerRoute.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("R13NaturalOwners")).findFirst().orElseThrow();assertTrue(type.isRecord());wrapper=type.getDeclaredConstructors()[0];assertEquals(9,wrapper.getParameterCount());wrapper.setAccessible(true);owners=method("assertNaturalR13Owners",Harness.class,type);image=method("drawNaturalR13SelectedImage",Harness.class,type,int.class);equal=method("assertNaturalR13SnapshotValue",Object.class,Object.class,String.class);snapshots=method("assertNaturalR13Snapshots",Harness.class,Object.class,Object.class,String.class);}
        LogicalInputSnapshot control(String name,int call)throws Exception{return (LogicalInputSnapshot)invoke(method(name,int.class),call);}
        void owners(Harness h,Owners o)throws Exception{invoke(owners,h,o.comparison());}
        byte[] image(Harness h,Owners o,int call)throws Exception{return (byte[])invoke(image,h,o.comparison(),call);}
        void equal(Object a,Object b,String label)throws Exception{invoke(equal,a,b,label);}
        void snapshots(Harness h,Object full,Object course,String label)throws Exception{invoke(snapshots,h,full,course,label);}
    }
    private static Method method(String name,Class<?>... types)throws Exception{var m=TestSonicCdAct2ControllerRoute.class.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
    private static Object invoke(Method m,Object... args)throws Exception{try{return m.invoke(null,args);}catch(InvocationTargetException wrapped){var original=wrapped.getCause();if(original instanceof Error e)throw e;if(original instanceof Exception e)throw e;throw new AssertionError(original);}}
    private static Object field(Object actual,String name)throws Exception{for(Class<?> c=actual.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(actual);}catch(NoSuchFieldException absent){}throw new NoSuchFieldException(name);}
    private static int pointer(CdObjectState s){return s.identity().nativeAddress24()&0xFFFF;}
    private static void link(CdObjectState from,CdObjectState.WordField field,CdObjectState to){assertEquals(pointer(to),from.word(field));}
    private static void machine(CdObjectPool.Snapshot pool,CdObjectState root,CdR13BossAssets data){
        var children=pool.slots().stream().filter(s->s.id()>=0x2B&&s.id()<=0x32).toList();assertEquals(14,children.size());assertEquals(data.children().stream().map(CdR13BossAssets.ChildDeclaration::objectId).toList(),children.stream().map(CdObjectState::id).toList());
        var b=children.get(0);var t1=children.get(1);var c1=children.get(2);var f1=children.get(3);var t2=children.get(4);var c2=children.get(5);var f2=children.get(6);var s=children.get(7);var a1=children.get(8);var r1=children.get(9);var u1=children.get(10);var a2=children.get(11);var r2=children.get(12);var u2=children.get(13);
        link(root,BOSS_NEXT,b);link(b,BOSS_PARENT,root);link(b,BOSS_NEXT,t1);link(b,BOSS_BRANCH,t2);link(t1,BOSS_PARENT,b);link(t1,BOSS_NEXT,c1);link(c1,BOSS_PARENT,t1);link(c1,BOSS_NEXT,f1);link(f1,BOSS_PARENT,c1);link(t2,BOSS_PARENT,b);link(t2,BOSS_NEXT,c2);link(c2,BOSS_PARENT,t2);link(c2,BOSS_NEXT,f2);link(f2,BOSS_PARENT,c2);
        link(root,BOSS_BRANCH,s);link(s,BOSS_PARENT,b);link(s,BOSS_NEXT,a1);link(s,BOSS_BRANCH,a2);link(a1,BOSS_PARENT,s);link(a1,BOSS_NEXT,r1);link(r1,BOSS_PARENT,a1);link(r1,BOSS_NEXT,u1);link(u1,BOSS_PARENT,r1);link(u1,BOSS_NEXT,b);link(a2,BOSS_PARENT,s);link(a2,BOSS_NEXT,r2);link(r2,BOSS_PARENT,a2);link(r2,BOSS_NEXT,u2);link(u2,BOSS_PARENT,r2);link(u2,BOSS_NEXT,b);
    }
    private static void capsule(Owners o,CdObjectState c,CdObjectPlacements.Placement row,CdR13CapsuleAssets data){
        assertTrue(o.host().placement().ownsAct3FutureAssets(o.assets()));assertSame(o.assets().placements(),o.host().placement().placements());assertEquals(row.centreX(),c.centreX());assertEquals(row.centreY(),c.centreY());assertEquals(row.subtype(),c.subtype());assertEquals(row.subtype2(),c.unsignedByte(SUBTYPE2));assertEquals(2,c.routine());assertEquals(data.mappingAddress(),c.longBits(MAPPINGS));assertEquals(data.tileWord(),c.word(TILE));assertEquals(32,c.unsignedByte(X_RADIUS));assertEquals(32,c.unsignedByte(WIDTH));assertEquals(24,c.unsignedByte(Y_RADIUS));assertTrue((c.spriteFlags()&4)!=0);assertTrue(row.hasSavedFlag());assertTrue((o.host().placement().savedFlag(c.unsignedByte(SAVED_FLAGS_ID),2)&0x80)!=0);
    }
    private record Picture(byte[] rgba,byte[] without,List<Integer> wholes,String coverage){Picture{rgba=rgba.clone();without=without.clone();wholes=List.copyOf(wholes);}@Override public byte[] rgba(){return rgba.clone();}@Override public byte[] without(){return without.clone();}}
    private static Picture picture(Harness h,Owners o,Comparisons helper,int call,CdObjectPool.Snapshot originalPriorPool,boolean boss)throws Exception{
        var registry=h.mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var published=o.host().selected();var selected=assertInstanceOf(SonicCdAct3FutureWorldHost.Published.class,published).selection();var scene=assertInstanceOf(SonicCdPastDisplay.Ready.class,selected.projection()).scene();var cpu=selected.inputs().selectedCpu().orElseThrow();
        var group=originalPriorPool.slots().stream().filter(s->boss?s.id()>=0x2A&&s.id()<=0x32:s.id()==0x15).map(s->s.identity().wholeIndex()).collect(java.util.stream.Collectors.toSet());var selectedWholes=cpu.provenance().stream().map(p->p.slotRef().wholeIndex()).filter(group::contains).distinct().toList();byte[] actual=helper.image(h,o,call);
        if(selectedWholes.isEmpty()){helper.snapshots(h,full,course,"fixed "+call+" unselected coverage nonwrites");return new Picture(actual,new byte[0],List.of(),"SOURCE_UNREACHED_FIXED_"+call+": no original selected "+(boss?"2A..32 Boss group":"ID15 Capsule")+" provenance; no alternative cut");}
        var omitted=omit(scene,cpu,Set.copyOf(selectedWholes));var binding=field(o.host(),"render");var renderer=assertInstanceOf(RetainedPlaneSceneRenderer.class,field(binding,"renderer"));assertSame(o.world(),field(binding,"world"));assertSame(o.level(),field(binding,"level"));assertEquals(o.generation(),field(binding,"generation"));assertSame(GameServices.graphics(),field(binding,"graphics"));assertSame(GameServices.graphics().getPatternAtlas(),field(binding,"atlas"));assertSame(h.mode().getCamera(),field(binding,"camera"));assertSame(Thread.currentThread(),field(binding,"thread"));assertEquals("surfaceless-egl",field(binding,"backend"));assertSame(org.lwjgl.opengl.GL.getCapabilities(),field(binding,"capabilities"));
        long epoch=o.level().currentEpoch();clear();renderer.draw(omitted,h.mode().getCamera().getX(),h.mode().getCamera().getY(),h.module()::validateAct3FutureLoadingCut);byte[] without=pixels();clear();renderer.draw(omitted,h.mode().getCamera().getX(),h.mode().getCamera().getY(),h.module()::validateAct3FutureLoadingCut);assertArrayEquals(without,pixels());assertEquals(epoch,o.level().currentEpoch());assertSame(published,o.host().selected());helper.snapshots(h,full,course,"fixed group omission nonwrites "+call);assertArrayEquals(actual,helper.image(h,o,call));helper.snapshots(h,full,course,"fixed original redraw "+call);
        return new Picture(actual,without,selectedWholes,Arrays.equals(actual,without)?"COVERAGE_GAP_FIXED_"+call+": source-selected but no visible group pixels":"VISIBLE_FIXED_"+call+": actual selected group omission changes pixels");
    }
    /** Filter once in ORIGINAL CPU reverse SAT/tile order. Preserve every other Scene partition. */
    private static RetainedPlaneSceneRenderer.Scene omit(RetainedPlaneSceneRenderer.Scene scene,CdObjectDrawQueue.CpuSat cpu,Set<Integer> group){
        byte[] raw=cpu.bytes();var tiles=new ArrayList<SpritePresentation.Tile>();int at=0;for(int n=cpu.spriteCount()-1;n>=0;n--){int size=raw[n*8+2]&15,count=(((size>>>2)&3)+1)*((size&3)+1);if(!group.contains(cpu.provenance().get(n).slotRef().wholeIndex()))tiles.addAll(scene.sprites().tiles().subList(at,at+count));at+=count;}assertEquals(scene.sprites().tiles().size(),at);assertTrue(tiles.size()<at);
        var frame=new SpritePresentation.Frame(tiles,scene.sprites().primitives(),scene.sprites().patternVersions());var out=new RetainedPlaneSceneRenderer.Scene(scene.foreground(),scene.background(),scene.packedHscroll256(),scene.vscrollLongBits(),scene.selectedPalette128(),scene.displayEnabled(),frame,scene.nativePatterns(),scene.selectedBackdropPaletteIndex());
        assertSame(scene.foreground(),out.foreground());assertSame(scene.background(),out.background());assertArrayEquals(scene.packedHscroll256(),out.packedHscroll256());assertEquals(scene.vscrollLongBits(),out.vscrollLongBits());assertArrayEquals(scene.selectedPalette128(),out.selectedPalette128());assertEquals(scene.displayEnabled(),out.displayEnabled());assertEquals(scene.selectedBackdropPaletteIndex(),out.selectedBackdropPaletteIndex());assertEquals(scene.nativePatterns(),out.nativePatterns());assertEquals(scene.sprites().patternVersions(),out.sprites().patternVersions());assertEquals(scene.sprites().primitives(),out.sprites().primitives());return out;
    }
    private static String diagnostic(Harness h,Owners o,CdObjectState c,CdObjectPlacements.Placement row,List<String> events){
        var camera=h.mode().getCamera();var m=o.frame().state().motion();int rx=(byte)m.xRadius()+32,ry=(byte)m.yRadius()+28;int dx=((m.centreX16_16()>>>16)-c.centreX()+rx)&65535,dy=((m.centreY16_16()>>>16)-c.centreY()+ry)&65535;boolean roll=(m.flags()&4)!=0;boolean overlap=roll&&(short)dx>=0&&dx<((rx+rx)&65535)&&(short)dy>=0&&dy<((ry+ry)&65535);int id=c.unsignedByte(SAVED_FLAGS_ID);
        return "R13_NATURAL_BOSS_DEFEAT_FINAL_19658 READ_ONLY original route; Capsule whole="+c.identity().wholeIndex()+",raw64="+HexFormat.of().formatHex(c.copyBytes())+",X="+c.centreX()+",Y="+c.centreY()+",flags="+c.flags()+",spriteFlags="+c.spriteFlags()+",routine="+c.routine()+",savedID="+id+",savedByteFuture="+o.host().placement().savedFlag(id,2)+",placement="+row+",placementStatus="+o.host().placement().status()+",actor="+o.frame().state()+",actorHistory256="+HexFormat.of().formatHex(o.frame().state().history().bytes().copy())+",environment="+o.frame().environment()+",bounds="+o.frame().environment().movement().bounds()+",nativeCamera="+o.host().scroll()+",engineCamera={X="+camera.getX()+",Y="+camera.getY()+",XCopy="+camera.getXCopy()+",YCopy="+camera.getYCopy()+",width="+camera.getWidth()+",height="+camera.getHeight()+",minX="+camera.getMinX()+",targetMinX="+camera.getMinXTarget()+",maxX="+camera.getMaxX()+",targetMaxX="+camera.getMaxXTarget()+",minY="+camera.getMinY()+",targetMinY="+camera.getMinYTarget()+",maxY="+camera.getMaxY()+",targetMaxY="+camera.getMaxYTarget()+"},globals="+o.host().globals()+",vIntClock="+o.frame().vIntClock()+",parentEvents="+events+",sourceCapsuleHitComparison={rollFlag4="+roll+",radiusX=(byte)xRadius+32="+rx+",radiusY=(byte)yRadius+28="+ry+",dxWord="+dx+",dyWord="+dy+",overlap="+overlap+"}; assertions/diagnostic only, never controller input";
    }
    private static void clear(){glViewport(0,0,320,224);glClearColor(1,0,1,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);}
    private static byte[] pixels(){glFinish();var b=org.lwjgl.BufferUtils.createByteBuffer(320*224*4);glReadPixels(0,0,320,224,GL_RGBA,GL_UNSIGNED_BYTE,b);assertEquals(GL_NO_ERROR,glGetError());byte[] out=new byte[b.capacity()];b.get(out);return out;}
    private static void retire(Harness h,Owners o)throws Exception{
        var graphics=GameServices.graphics();var atlas=graphics.getPatternAtlas();var binding=field(o.host(),"render");var renderer=assertInstanceOf(RetainedPlaneSceneRenderer.class,field(binding,"renderer"));var touched=assertInstanceOf(Map.class,field(renderer,"touched"));var installed=new ArrayList<Integer>();for(Object id:touched.keySet()){installed.add(assertInstanceOf(Integer.class,id));assertNotNull(atlas.getEntry((Integer)id));}assertFalse(installed.isEmpty());
        int other=-1;boolean own=false;search:for(var r:atlas.registeredRangesForTesting())if(r.base()>=PatternAtlasRange.HUD.base())for(int id=r.base();id<r.base()+r.size();id++)if(!touched.containsKey(id)&&atlas.getEntry(id)!=null){other=id;break search;}
        if(other<0){atlas.registerRange(PatternAtlasRange.HUD);other=PatternAtlasRange.HUD.base()+19;assertFalse(touched.containsKey(other));assertNull(atlas.getEntry(other));graphics.cachePatternTexture(new com.openggf.level.Pattern(),other);own=true;System.out.println("TEST_ONLY_GPU_RESOURCE_WITNESS id="+other+"; after all gameplay/pixels, outside CD/touched spans; excluded from native/source/pixel coverage");}
        assertTrue(other>=PatternAtlasRange.HUD.base());var entry=atlas.getEntry(other);assertNotNull(entry);var sample=SpritePresentation.patternSample(graphics,other);var mask=graphics.getTilePriorityFBO();assertNotNull(mask);int texture=mask.getTextureId();
        try{SessionManager.closeGameplaySession();assertTrue(o.frame().closed());assertTrue((boolean)field(o.host(),"closed"));assertNull(field(o.host(),"render"));assertTrue((boolean)field(renderer,"closed"));assertTrue(touched.isEmpty());for(int id:installed){assertNull(atlas.getEntry(id));assertNull(SpritePresentation.patternSample(graphics,id));}assertSame(entry,atlas.getEntry(other));assertEquals(sample,SpritePresentation.patternSample(graphics,other));assertSame(mask,graphics.getTilePriorityFBO());assertTrue(glIsTexture(texture));}
        finally{if(own&&atlas.getEntry(other)==entry){assertTrue(graphics.uncachePattern(other));assertNull(atlas.getEntry(other));}/* CPU resource sample is released by the ordinary graphics owner cleanup immediately after this scope. */}
    }
    private static void export(Map<String,Picture> pictures,String diagnostic)throws Exception{
        String raw=System.getenv("SONICCD_R13_NATURAL_DEFEAT_OUTPUT");if(raw==null||raw.isBlank())return;String root=System.getenv("SONICCD_ENGINE_CAPTURE_ROOT");assertNotNull(root,"Explicit task-owned ENGINE capture root required");
        Path expected=Path.of(System.getProperty("user.home"),"Downloads","Sonic-CD","captures","engine").toAbsolutePath().normalize();Path owned=Path.of(root);assertTrue(owned.isAbsolute());assertEquals(expected,owned);assertEquals(owned,owned.toRealPath());Path directory=Path.of(raw);assertTrue(directory.isAbsolute());assertEquals(directory,directory.normalize());assertEquals(raw,directory.toString());assertTrue(directory.startsWith(owned)&&!directory.equals(owned));
        // Require a pre-created output directory. Open every absolute component relative to an
        // already-open directory with NOFOLLOW_LINKS; no check-then-create symlink traversal.
        assertTrue(Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS),"Pre-create the task-owned output directory");
        var opened=new ArrayList<SecureDirectoryStream<Path>>();
        try(var initial=Files.newDirectoryStream(directory.getRoot())){
            assertInstanceOf(SecureDirectoryStream.class,initial,"Linux secure directory output required");@SuppressWarnings("unchecked") var secure=(SecureDirectoryStream<Path>)initial;
            for(Path component:directory){var next=secure.newDirectoryStream(component,LinkOption.NOFOLLOW_LINKS);opened.add(next);secure=next;}
            StringBuilder metadata=new StringBuilder("ENGINE OpenGGF original-USA R13D; one fixed R53 controller through19658.\nNo native-image/audio/regional/full-act/Results/Capsule-hit certificate.\n").append(diagnostic).append('\n');
            for(var e:pictures.entrySet()){
                var picture=e.getValue();metadata.append(e.getKey()).append(' ').append(picture.coverage()).append(" selectedWholes=").append(picture.wholes()).append('\n');
                // Unselected fixed cuts are reported, never replaced with another frame or a labelled fake PNG.
                if(picture.wholes().isEmpty())continue;String stem="ENGINE-R13D-natural-"+e.getKey();writeNew(secure,stem+".png",png(picture.rgba()));writeNew(secure,stem+"-without-selected-group.png",png(picture.without()));
            }
            metadata.append("Written only after actual session/module/graphics/EGL/fixture cleanup. Images are output only.\n");writeNew(secure,"ENGINE-R13D-natural-defeat-19658.txt",metadata.toString().getBytes(StandardCharsets.UTF_8));
        }finally{for(int i=opened.size()-1;i>=0;i--)opened.get(i).close();}
    }
    private static void writeNew(SecureDirectoryStream<Path> directory,String name,byte[] bytes)throws Exception{assertEquals(name,Path.of(name).getFileName().toString());Set<OpenOption> options=Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);try(SeekableByteChannel channel=directory.newByteChannel(Path.of(name),options,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")))){var b=ByteBuffer.wrap(bytes);while(b.hasRemaining())channel.write(b);}}
    private static byte[] png(byte[] rgba)throws Exception{assertEquals(320*224*4,rgba.length);var image=new java.awt.image.BufferedImage(320,224,java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<224;y++)for(int x=0;x<320;x++){int at=((223-y)*320+x)*4;image.setRGB(x,y,(rgba[at+3]&255)<<24|(rgba[at]&255)<<16|(rgba[at+1]&255)<<8|rgba[at+2]&255);}var out=new java.io.ByteArrayOutputStream();assertTrue(javax.imageio.ImageIO.write(image,"png",out));return out.toByteArray();}

    // BEGIN natural original-USA Capsule/Results/retained-exit proof 20261107.
    /** Fixed R59 controller only. Root observed through21931; author Java/EGL UNRUN.
     * Native N1ecc5f3a Level End/Main.asm:173-379,818-907; Results/Main.asm:42-366,
     * Level/Main.asm:19-115,333-432 and IPX.asm:1372-1391.
     * The permanent positive bound is BEFORE the first unknown historical unlock read21932.
     * Checkpoints and pixels are comparisons; they never supply gameplay/controller values. */
    @Test void actualFixedControllerHitsCapsuleRunsResultsAndRetainsOldProgrammeThroughIpxReturn() throws Exception {
        var fixture=new TestSonicCdPastLoadingWorld();fixture.setup();var exports=new LinkedHashMap<String,Picture>();String diagnostic;
        try(var egl=TestSonicCdPastResultsRoute.Egl.open()) {
            assertInstanceOf(NullAudioBackend.class,GameServices.audio().getBackend());var prepared=ready();var h=prepared.harness();var helper=new Comparisons();
            for(int call=prepared.returned()+1;call<=17142;call++)step(h,capsuleInput(helper,call));
            var o=Owners.actual(h,helper);helper.owners(h,o);var data=CdR13CapsuleAssets.bind(o.assets());var results=CdR13ResultsAssets.bind(o.assets(),data);
            var row=o.assets().placements().forPeriod(CdPeriod.BAD_FUTURE).stream().filter(v->v.objectId()==0x15).toList();assertEquals(1,row.size());assertEquals(60,row.getFirst().index());assertEquals(3656,row.getFirst().centreX());assertEquals(928,row.getFirst().centreY());
            // Fixed edges: hit, hit picture, final explosion/seeds, actual Results allocation/init,
            // selected Results, NextLevel/full walk, last fade, clear/RTS and original IPX blank.
            var edges=Set.of(21187,21188,21191,21308,21368,21384,21504,21906,21907,21929,21930,21931);
            var events=new ArrayList<String>();int firstHit=-1,firstSeeds=-1,firstResults=-1,firstTransfer=-1;
            for(int call=17143;call<=21931;call++) {
                var control=capsuleInput(helper,call);boolean replay=edges.contains(call);var registry=h.mode().getRewindRegistry();
                capsuleExitOwners(h,o,helper,call-1);var before=o.core().state();var beforePool=o.host().placement().pool().capture();
                var beforeCpu=o.host().video().completedCpu();var beforeClock=o.frame().vIntClock();var beforeActor=o.frame().state();var beforeSounds=o.host().sounds();int beforeFrames=o.frame().environment().globals().levelFrames();
                var full=replay?registry.capture():null;var course=replay?registry.captureCourse():null;byte[] beforePixels=null;
                if(replay){helper.snapshots(h,full,course,"Capsule before pixels "+call);beforePixels=capsuleExitPixels(h,o,helper,call-1);helper.snapshots(h,full,course,"Capsule before forward "+call);}
                step(h,control); // Original RuntimeException/Error propagates; never retry a failed composite.
                capsuleExitOwners(h,o,helper,call);var after=o.core().state();var pool=o.host().placement().pool().capture();
                assertEquals(beforeClock.absoluteSeed(),o.frame().vIntClock().absoluteSeed());
                assertEquals(call==21931?beforeClock.deltaSinceAdmittedCut():(beforeClock.deltaSinceAdmittedCut()+1)&0xFFFF_FFFFL,o.frame().vIntClock().deltaSinceAdmittedCut(),"Actual old Frame pulses; IPX blank has none");
                assertEquals(call<=21907?(beforeFrames+1)&0xFFFF:beforeFrames,o.frame().environment().globals().levelFrames(),"Level main counter advances only on original ordinary walk");
                if(call>21907)helper.equal(beforeActor,o.frame().state(),"Retained old actor/history; no Body dispatch "+call);
                var selected=assertInstanceOf(SonicCdAct3FutureWorldHost.Published.class,o.host().selected()).selection();
                helper.equal(beforeCpu,selected.inputs().selectedCpu(),"Native selected prior CPU at "+call);if(beforeCpu.isPresent())assertSame(beforeCpu.orElseThrow(),selected.inputs().selectedCpu().orElseThrow());
                assertSame(after.section().residency(),o.host().video().current());assertSame(o.host().video().current(),selected.inputs().current());
                var cap=pool.slots().get(37-32);var oldCap=beforePool.slots().get(37-32);
                if(cap.id()==0x15&&cap.routine()>=4&&firstHit<0){firstHit=call;assertEquals(21187,call);}
                if(call==21184){assertEquals(2,cap.routine());assertEquals(0,o.frame().state().motion().flags()&2);assertEquals(0,o.frame().state().motion().playerJump());}
                if(call==21185){assertEquals(0,beforeActor.motion().playerJump());assertEquals(1,o.frame().state().motion().playerJump());assertTrue(o.frame().state().motion().yVelocity()<0,"Genuine accepted jump; no inference from held B");}
                if(call==21187){assertEquals(2,oldCap.routine());assertEquals(4,cap.routine());assertEquals(120,cap.unsignedByte(LEVEL_END_TIMER));assertEquals(2,cap.unsignedByte(MAP_FRAME));assertEquals(388,o.frame().state().motion().yVelocity());assertEquals(0,after.level().updateHudTime());assertEquals(2,o.frame().environment().movement().gates().zoneAct());assertEquals(2,o.frame().environment().movement().gates().timeZone());events.add(call+": actual row60/whole37 Capsule hit and original Y bounce");}
                if(call==21191){assertEquals(0,beforePool.slots().getFirst().id());var explosion=pool.slots().getFirst();assertEquals(0x18,explosion.id());assertEquals(0,explosion.routine());assertEquals(1,explosion.unsignedByte(ROUTINE_2));assertEquals(beforeSounds.size()+1,o.host().sounds().size());assertInstanceOf(SonicCdGameModule.PastR13CapsuleExplosion.class,o.host().sounds().getLast());events.add(call+": first original Explosion32 allocated behind physical cursor; native FM completed");}
                if(cap.id()==0x15&&cap.routine()==6&&firstSeeds<0){firstSeeds=call;assertEquals(21308,call);}
                if(call==21308){assertEquals(4,oldCap.routine());assertEquals(6,cap.routine());assertEquals(60,cap.unsignedByte(LEVEL_END_TIMER));assertEquals(30,o.host().sounds().stream().filter(v->v instanceof SonicCdGameModule.PastR13CapsuleExplosion).count());
                    var holes=beforePool.slots().stream().filter(v->v.id()==0).limit(7).map(v->v.identity().wholeIndex()).toList();assertEquals(7,holes.size());
                    var seeds=pool.slots().stream().filter(v->v.id()==0x15&&v.routine()==10).toList();assertEquals(holes,seeds.stream().map(v->v.identity().wholeIndex()).toList());
                    for(var seed:seeds){assertTrue(seed.identity().wholeIndex()>37,"Original same-walk seed dispatch follows parent");assertEquals(data.mappingAddress(),seed.longBits(MAPPINGS));assertEquals(data.tileWord(),seed.word(TILE));}
                    // The 96-byte ID4 restore is component-authenticated; the later actual palette cycle
                    // may alter normal colours before this closed cut. Do not compare it to an uncycled target.
                    assertEquals(96,data.restoredPalette().bytes().length());assertEquals(23,data.restoredPalette().longwordCounter());assertEquals(32,data.capsulePalette().bytes().length());assertEquals(7,data.capsulePalette().longwordCounter());events.add(call+": original seven first-free seeds; restore96 distinct from escape32");}
                if(pool.slots().stream().anyMatch(v->v.id()==0x3A)&&firstResults<0){firstResults=call;assertEquals(21368,call);}
                if(call==21368){assertEquals(6,oldCap.routine());assertEquals(8,cap.routine());var parent=pool.slots().getFirst();assertEquals(0,beforePool.slots().getFirst().id());assertEquals(0x3A,parent.id());assertEquals(32,parent.identity().wholeIndex());assertEquals(0,parent.routine());assertEquals(16,parent.unsignedByte(RESULTS_TIMER));assertEquals(1,o.frame().environment().controls().controlLocked()&1);assertEquals(8,o.frame().environment().controls().logicalHeld());assertEquals(8,o.frame().environment().controls().logicalTap());events.add(call+": Results32 allocated AFTER its physical position; first dispatch next walk");}
                if(call==21384){assertEquals(0x3A,pool.slots().getFirst().id());assertEquals(2,pool.slots().getFirst().routine());assertEquals(1,Collections.frequency(after.calls(),SonicCdAct3FutureLoadingVisit.Call.RESULTS_PLC_APPEND));events.add(call+": native Results BYTE16 delay completed and original PLC16 append");}
                if(call==21504){var rows=pool.slots().stream().filter(v->v.id()==0x3A).toList();assertEquals(3,rows.size());assertEquals(List.of(32,33,34),rows.stream().map(v->v.identity().wholeIndex()).toList());assertEquals(6,rows.getFirst().routine());for(var r:rows){assertEquals(results.mappingAddress(),r.longBits(MAPPINGS));assertEquals(0x83C4,r.word(TILE));}for(int whole=39;whole<=45;whole++){var flower=pool.slots().get(whole-32);assertEquals(0x1F,flower.id());assertEquals(1,flower.subtype());assertEquals(4,flower.routine());}events.add(call+": original three Results rows and actual fixed selected picture");}
                if(before.phase()==SonicCdAct3FutureLoadingVisit.Phase.READY&&after.phase()!=before.phase()){firstTransfer=call;assertEquals(21907,call);}
                if(call==21906){assertEquals(8,pool.slots().getFirst().routine());assertEquals(SonicCdAct3FutureLoadingVisit.Phase.READY,after.phase());}
                if(call==21907){assertEquals(SonicCdAct3FutureLoadingVisit.Phase.READY,before.phase());assertEquals(SonicCdAct3FutureLoadingVisit.Phase.OLD_BLACK_FADE,after.phase());assertEquals(0,o.core().oldExitSteps());assertEquals(2,after.level().levelRestart());assertEquals(1,after.level().plcLoadFlags());assertEquals(0,after.section().queue().headSource());assertEquals(0,after.section().remainingTilesWord());assertEquals(0,h.module().levelProgramState().goodFutureFlags().orElseThrow());events.add(call+": full physical walk completed; literal0100/1 Results stores then old LevelStart");}
                if(call>=21908&&call<=21929)assertEquals(call-21907,o.core().oldExitSteps());
                if(call==21929){assertEquals(SonicCdAct3FutureLoadingVisit.Phase.OLD_BLACK_FADE,before.phase());assertEquals(SonicCdAct3FutureLoadingVisit.Phase.OLD_CLEAR_WAIT,after.phase());assertEquals(22,o.core().oldExitSteps());assertEquals(0,after.level().levelRestart());assertEquals(0,after.program().palFadeFlagsByte());assertArrayEquals(new byte[128],after.normalPalette().copy());events.add(call+": native22nd fade/colour/prepare then clear palette");}
                if(call==21930){assertEquals(SonicCdAct3FutureLoadingVisit.Phase.OLD_CLEAR_WAIT,before.phase());assertEquals(SonicCdAct3FutureLoadingVisit.Phase.OLD_RETURNED,after.phase());assertEquals(1,Collections.frequency(after.calls(),SonicCdAct3FutureLoadingVisit.Call.OLD_EXIT_CLEAR_SERVICE));assertEquals(1,Collections.frequency(after.calls(),SonicCdAct3FutureLoadingVisit.Call.OLD_EXIT_RTS));assertTrue(after.displayEnabled());events.add(call+": selectorC clear publication and MMD RTS");}
                if(call==21931){assertEquals(SonicCdAct3FutureLoadingVisit.Phase.OLD_RETURNED,before.phase());assertEquals(SonicCdAct3FutureLoadingVisit.Phase.OLD_IPX_RETURNED,after.phase());assertFalse(after.displayEnabled());assertEquals(1,Collections.frequency(after.calls(),SonicCdAct3FutureLoadingVisit.Call.OLD_EXIT_IPX_RETURN));assertEquals(before.calls().size()+3,after.calls().size(),"blank, actual VInt completion, IPX return only");assertEquals(beforeSounds.size()+1,o.host().sounds().size());helper.equal(beforeSounds,o.host().sounds().subList(0,beforeSounds.size()),"IPX preserves earlier completed sound prefix");assertEquals(new SonicCdGameModule.PastProgrammeSound(SonicCdGameModule.ProgramSound.STOP_FM),o.host().sounds().getLast());assertInstanceOf(SonicCdPastDisplay.Disabled.class,selected.projection());events.add(call+": genuine IPX display-disable/STOP_FM/blank; no old Frame pulse");}
                if(replay){
                    var afterFull=registry.capture();var afterCourse=registry.captureCourse();helper.snapshots(h,afterFull,afterCourse,"Capsule after forward before pixels "+call);byte[] afterPixels=capsuleExitPixels(h,o,helper,call);
                    Picture fixed=call==21188?picture(h,o,helper,call,beforePool,false):call==21504?resultsPicture(h,o,helper,call,beforePool):null;
                    if(fixed!=null){exports.put(call==21188?"capsule-hit-21188":"results-21504",fixed);assertArrayEquals(afterPixels,fixed.rgba());}
                    helper.snapshots(h,afterFull,afterCourse,"Capsule selected pixels nonwrites "+call);
                    for(boolean courseScope:new boolean[]{false,true}){
                        if(courseScope)registry.restoreCourse(course);else registry.restore(full);capsuleExitOwners(h,o,helper,call-1);helper.snapshots(h,full,course,"Capsule restored before "+call+"/"+courseScope);
                        assertArrayEquals(beforePixels,capsuleExitPixels(h,o,helper,call-1));helper.snapshots(h,full,course,"Capsule before replay "+call+"/"+courseScope);
                        step(h,control);capsuleExitOwners(h,o,helper,call);helper.snapshots(h,afterFull,afterCourse,"Capsule original forward replay "+call+"/"+courseScope);assertArrayEquals(afterPixels,capsuleExitPixels(h,o,helper,call));
                        if(fixed!=null){var again=call==21188?picture(h,o,helper,call,beforePool,false):resultsPicture(h,o,helper,call,beforePool);assertEquals(fixed.coverage(),again.coverage());assertEquals(fixed.wholes(),again.wholes());assertArrayEquals(fixed.rgba(),again.rgba());assertArrayEquals(fixed.without(),again.without());}
                        helper.snapshots(h,afterFull,afterCourse,"Capsule replay pixels nonwrites "+call+"/"+courseScope);
                    }
                }
            }
            assertEquals(21187,firstHit);assertEquals(21308,firstSeeds);assertEquals(21368,firstResults);assertEquals(21907,firstTransfer);assertEquals(22,o.core().oldExitSteps());assertEquals(22,Collections.frequency(o.core().state().calls(),SonicCdAct3FutureLoadingVisit.Call.OLD_EXIT_BLACK_SERVICE));assertEquals(22,Collections.frequency(o.core().state().calls(),SonicCdAct3FutureLoadingVisit.Call.OLD_EXIT_BLACK_COLOURS));assertEquals(1,Collections.frequency(o.core().state().calls(),SonicCdAct3FutureLoadingVisit.Call.OLD_EXIT_CLEAR));assertEquals(1,Collections.frequency(o.core().state().calls(),SonicCdAct3FutureLoadingVisit.Call.OLD_EXIT_BLANK));
            assertEquals(1,o.host().sounds().stream().filter(v->v instanceof SonicCdGameModule.PastR13CapsuleSubCpu c&&c.command()==CdR13Capsule.CpuCommand.RESULTS_MUSIC).count());assertEquals(0,o.host().sounds().stream().filter(v->v instanceof SonicCdGameModule.PastR13CapsuleSubCpu c&&c.command()==CdR13Capsule.CpuCommand.FADE_PCM).count());
            assertEquals(3479,o.frame().environment().movement().bounds().left());assertEquals(3479,o.frame().environment().movement().bounds().right());assertEquals(0,o.core().state().level().checkpoint());
            assertTrue(h.module().ipxProgressState().timeAttackUnlockByte().isEmpty(),"Historical unlock remains UNKNOWN; no selected-profile/history invention");
            assertEquals(Set.of("capsule-hit-21188","results-21504"),exports.keySet());for(var e:exports.entrySet())System.out.println("ENGINE_CAPSULE_RESULTS_FIXED_CUT "+e.getKey()+" "+e.getValue().coverage()+" selectedWholes="+e.getValue().wholes());
            diagnostic="ENGINE original-USA R13D actual fixed controller through21931; Capsule/Results/retained old programme only. events="+events+"; phase="+o.core().state().phase()+"; gates="+o.frame().environment().movement().gates()+"; level="+o.core().state().level()+"; clock="+o.frame().vIntClock()+"; native history unlock UNKNOWN. Call21932 is deliberately outside this positive proof; Root separately observed its original missing-unlock boundary. No R31 admission/full-act/native-image/audio/regional claim.";
            System.out.println(diagnostic);var finalFull=h.mode().getRewindRegistry().capture();var finalCourse=h.mode().getRewindRegistry().captureCourse();helper.snapshots(h,finalFull,finalCourse,"Capsule final21931 diagnostic nonwrites");
            retire(h,o); // Existing actual Module/session/renderer retirement and unrelated GPU witness.
        } finally {fixture.cleanup();}
        exportCapsule(exports,diagnostic); // Output only after actual session/module/graphics/EGL/fixture cleanup.
    }

    /** Exact R59 masks. The original Boss pulses stop at21000; no observation selects an input. */
    private static LogicalInputSnapshot capsuleInput(Comparisons helper,int call)throws Exception {
        if(call<=21000)return input(helper,call);
        if(call<=21120)return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(6,0,0,0,false,false),PlayerInputState.neutral());
        return call>=21185&&call<=21200?RIGHT_B:RIGHT;
    }
    /** Original comparison helper is strict2/2. This separate TEST checker accepts only the
     * source-derived retained old0100/1 cut, without normalizing either owner or admitting R31. */
    private static void capsuleExitOwners(Harness h,Owners o,Comparisons helper,int call)throws Exception {
        if(call<=21906){helper.owners(h,o);return;}
        var m=h.module();m.validateAct3FutureLoadingCut();o.core().requireServiceIdle();assertTrue(o.core().oldProgrammeSelected());assertFalse(o.core().failed());assertFalse(o.frame().closed());assertFalse(o.ordinary().isOrdinaryRunning());
        assertSame(o.frame(),m.requirePlayerFrame());assertSame(o.source(),o.frame().stageSource());assertSame(o.host(),m.act3FutureWorldHost());assertSame(o.core(),m.act3FutureLoadingVisit());assertSame(o.assets(),o.core().assets());
        assertSame(o.ordinary(),field(m,"act3FutureWorld"));assertSame(o.world(),h.mode().getWorldSession());assertSame(m,h.mode().getWorldSession().rootGameModule());assertSame(m,h.mode().getWorldSession().resolvedGameModule());assertSame(m.actualAct3RegistrySource(),h.mode().getWorldSession().getDataSource());
        assertSame(o.assets(),m.actualAct3RegistryAssets());assertSame(o.level(),h.mode().getLevelManager().getCurrentLevel());assertSame(o.assets(),o.level().assets());assertEquals(o.generation(),h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertEquals(0,h.mode().getLevelManager().getCurrentZone());assertEquals(0,h.mode().getWorldSession().getCurrentZone());assertEquals(2,h.mode().getLevelManager().getCurrentAct());assertEquals(2,h.mode().getWorldSession().getCurrentAct());assertEquals(2,h.mode().getLevelManager().getApparentAct());assertEquals(2,h.mode().getWorldSession().getApparentAct());
        assertTrue(m.act3FutureLoadingSelected());assertFalse(m.act2PastLoadingSelected());assertFalse(m.act2LoadingSelected());assertTrue(o.source().ownsAct3Future(o.assets()));assertTrue(o.host().placement().ownsAct3FutureAssets(o.assets()));assertSame(o.assets().placements(),o.host().placement().placements());assertEquals(CdStageKey.parse("R13D"),o.source().stage());assertEquals(CdNativeFile.R13D,o.assets().identity().file());assertEquals(CdReleaseVariant.USA,o.assets().identity().variant());assertEquals(0,o.host().selectedGoodFutureByte());
        assertEquals(0x100,o.frame().environment().movement().gates().zoneAct());assertEquals(1,o.frame().environment().movement().gates().timeZone());assertEquals(o.core().state().program().specialStageByte(),m.levelProgramState().specialStage());assertEquals(o.core().state().program().timeStonesByte(),m.levelProgramState().timeStones());
        assertSame(o.core(),field(o.host(),"owner"));assertSame(o.ordinary(),field(o.host(),"world"));assertSame(o.frame(),field(o.host(),"frame"));assertSame(o.assets(),field(o.host(),"assets"));assertSame(field(m,"act3FutureArt"),field(o.host(),"art"));assertTrue(o.host().video().current().ownsAssets(o.assets()));assertTrue(o.host().video().current().hasNativeHudProfile());assertSame(o.core().state().section().residency(),o.host().video().current());assertEquals("DONE",field(field(field(o.ordinary(),"binding"),"objects"),"phase").toString());o.frame().captureActor();o.frame().captureShared();
    }
    /** Full native state before pixels. Reuse the existing selected-current oracle while2/2;
     * old-programme rendering uses the same actual overlay/Host renderer, with literal gates. */
    private static byte[] capsuleExitPixels(Harness h,Owners o,Comparisons helper,int call)throws Exception {
        capsuleExitOwners(h,o,helper,call);if(call<=21906)return helper.image(h,o,call);
        var registry=h.mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var publication=o.host().selected();var selected=assertInstanceOf(SonicCdAct3FutureWorldHost.Published.class,publication).selection();var in=selected.inputs();
        var scene=selected.projection() instanceof SonicCdPastDisplay.Ready r?r.scene():assertInstanceOf(SonicCdPastDisplay.Disabled.class,selected.projection()).scene();
        var cpu=in.selectedCpu().orElseThrow();assertArrayEquals(cpu.bytes(),in.selectedSat().copy());assertSame(o.host().video().current(),in.current());assertTrue(o.host().geometryReady());assertEquals(o.assets().range(CdR13FutureAssets.Asset.LEVEL_COLLISION).nativeAddress(),o.host().collisionPointer().orElseThrow());
        assertArrayEquals(o.assets().terrain().blocks().copy(),o.core().state().blocksRam().orElseThrow().copy());assertArrayEquals(o.assets().terrain().layout().ram().copy(),o.core().state().layoutRam().orElseThrow().copy());
        assertArrayEquals(o.frame().artState().cpuBuffer().copy(),in.player().cpuBuffer().copy());assertArrayEquals(o.frame().artState().residentBank().copy(),in.player().residentBank().copy());assertEquals(o.frame().artState().lastFrame(),in.player().lastFrame());assertEquals(o.frame().artState().dirty(),in.player().dirty());assertArrayEquals(in.palette().copy(),scene.selectedPalette128());assertEquals(in.vscrollLongBits(),scene.vscrollLongBits());assertEquals(in.displayEnabled(),scene.displayEnabled());assertEquals(0x20,scene.selectedBackdropPaletteIndex());
        if(in.displayEnabled()){var fg=scene.foreground().words64x32();var bg=scene.background().words64x32();for(int y=0;y<32;y++)for(int x=0;x<64;x++){assertEquals(in.foreground().word(x,y),fg[y*64+x]);assertEquals(in.background().word(x,y),bg[y*64+x]);}}else{assertNull(scene.foreground());assertNull(scene.background());assertEquals(SpritePresentation.Frame.empty(),scene.sprites());assertTrue(scene.nativePatterns().isEmpty(),"Actual disabled projection samples no art");}var hs=scene.packedHscroll256();for(int i=0;i<256;i++)assertEquals((int)in.hscroll().unsignedLong(i*4),hs[i]);
        for(var e:scene.nativePatterns().entrySet()){int id=e.getKey();var raw=id>=0x780&&id<0x798?in.player().residentBank().slice((id-0x780)*32,32):com.openggf.game.soniccd.resources.CdR13ArtResidency.readTile(in.current(),id);assertEquals(com.openggf.game.soniccd.resources.CdObjectPresentation.patternVersion(raw),e.getValue());}
        if(in.displayEnabled())assertEquals(com.openggf.game.soniccd.resources.CdObjectPresentation.convert(cpu,id->new com.openggf.game.soniccd.resources.CdObjectPresentation.TilePattern(PatternAtlasRange.OBJECTS.base()+id,Objects.requireNonNull(scene.nativePatterns().get(id)))),scene.sprites());
        helper.snapshots(h,full,course,"old programme selected-current before pixels "+call);long epoch=o.level().currentEpoch();clear();h.module().gameplayFrameController().drawOverlay();byte[] actual=pixels();clear();h.module().gameplayFrameController().drawOverlay();assertArrayEquals(actual,pixels());assertEquals(epoch,o.level().currentEpoch());assertSame(publication,o.host().selected());capsuleExitOwners(h,o,helper,call);helper.snapshots(h,full,course,"old programme actual renderer nonwrites "+call);return actual;
    }
    /** Fixed21504 Results group from ORIGINAL prior-pool/CPU provenance; no alternate cut. */
    private static Picture resultsPicture(Harness h,Owners o,Comparisons helper,int call,CdObjectPool.Snapshot prior)throws Exception {
        assertEquals(21504,call);helper.owners(h,o);var registry=h.mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var published=o.host().selected();var selected=assertInstanceOf(SonicCdAct3FutureWorldHost.Published.class,published).selection();var scene=assertInstanceOf(SonicCdPastDisplay.Ready.class,selected.projection()).scene();var cpu=selected.inputs().selectedCpu().orElseThrow();
        var group=prior.slots().stream().filter(v->v.id()==0x3A).map(v->v.identity().wholeIndex()).collect(java.util.stream.Collectors.toSet());var wholes=cpu.provenance().stream().map(v->v.slotRef().wholeIndex()).filter(group::contains).distinct().toList();byte[] actual=helper.image(h,o,call);
        if(wholes.isEmpty()){helper.snapshots(h,full,course,"Results fixed21504 unselected nonwrites");return new Picture(actual,new byte[0],List.of(),"SOURCE_UNREACHED_FIXED_21504: no original selected Results3A group; no alternative cut");}
        var omitted=omit(scene,cpu,Set.copyOf(wholes));var binding=field(o.host(),"render");var renderer=assertInstanceOf(RetainedPlaneSceneRenderer.class,field(binding,"renderer"));assertSame(o.world(),field(binding,"world"));assertSame(o.level(),field(binding,"level"));assertEquals(o.generation(),field(binding,"generation"));assertSame(GameServices.graphics(),field(binding,"graphics"));assertSame(GameServices.graphics().getPatternAtlas(),field(binding,"atlas"));assertSame(h.mode().getCamera(),field(binding,"camera"));assertSame(Thread.currentThread(),field(binding,"thread"));assertEquals("surfaceless-egl",field(binding,"backend"));assertSame(org.lwjgl.opengl.GL.getCapabilities(),field(binding,"capabilities"));
        long epoch=o.level().currentEpoch();clear();renderer.draw(omitted,h.mode().getCamera().getX(),h.mode().getCamera().getY(),h.module()::validateAct3FutureLoadingCut);byte[] without=pixels();clear();renderer.draw(omitted,h.mode().getCamera().getX(),h.mode().getCamera().getY(),h.module()::validateAct3FutureLoadingCut);assertArrayEquals(without,pixels());assertEquals(epoch,o.level().currentEpoch());assertSame(published,o.host().selected());helper.snapshots(h,full,course,"Results omission complete nonwrites");assertArrayEquals(actual,helper.image(h,o,call));helper.snapshots(h,full,course,"Results original redraw nonwrites");
        return new Picture(actual,without,wholes,Arrays.equals(actual,without)?"COVERAGE_GAP_FIXED_21504: source-selected Results but no visible group pixels":"VISIBLE_FIXED_21504: actual selected Results group omission changes pixels");
    }
    /** Same secure create-new export mechanics; new opt-in and Capsule/Results-only names. */
    private static void exportCapsule(Map<String,Picture> pictures,String diagnostic)throws Exception {
        String raw=System.getenv("SONICCD_R13_NATURAL_CAPSULE_OUTPUT");if(raw==null||raw.isBlank())return;String root=System.getenv("SONICCD_ENGINE_CAPTURE_ROOT");assertNotNull(root,"Explicit task-owned ENGINE capture root required");
        Path expected=Path.of(System.getProperty("user.home"),"Downloads","Sonic-CD","captures","engine").toAbsolutePath().normalize();Path owned=Path.of(root);assertTrue(owned.isAbsolute());assertEquals(expected,owned);assertEquals(owned,owned.toRealPath());Path directory=Path.of(raw);assertTrue(directory.isAbsolute());assertEquals(directory,directory.normalize());assertEquals(raw,directory.toString());assertTrue(directory.startsWith(owned)&&!directory.equals(owned));assertTrue(Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS),"Pre-create task-owned output directory");
        var opened=new ArrayList<SecureDirectoryStream<Path>>();
        try(var initial=Files.newDirectoryStream(directory.getRoot())){
            assertInstanceOf(SecureDirectoryStream.class,initial,"Linux secure directory output required");@SuppressWarnings("unchecked") var secure=(SecureDirectoryStream<Path>)initial;
            for(Path component:directory){var next=secure.newDirectoryStream(component,LinkOption.NOFOLLOW_LINKS);opened.add(next);secure=next;}
            StringBuilder text=new StringBuilder("ENGINE OpenGGF original-USA actual natural Capsule/Results/retained old exit through21931.\nFixed R59 controller. Renderer-only group omission and complete FULL/course/current/pixel replay.\nNo R31 admission, native screenshot, audio, regional or full-act certificate.\n").append(diagnostic).append('\n');
            for(var e:pictures.entrySet()){assertTrue(Set.of("capsule-hit-21188","results-21504").contains(e.getKey()));var p=e.getValue();text.append(e.getKey()).append(' ').append(p.coverage()).append(" selectedWholes=").append(p.wholes()).append('\n');if(p.wholes().isEmpty())continue;String stem="ENGINE-R13D-natural-"+e.getKey();writeNew(secure,stem+".png",png(p.rgba()));writeNew(secure,stem+"-without-selected-group.png",png(p.without()));}
            text.append("Written only after actual module/session/graphics/EGL/fixture cleanup; outputs are never runtime input.\n");writeNew(secure,"ENGINE-R13D-natural-capsule-results-exit-21931.txt",text.toString().getBytes(StandardCharsets.UTF_8));
        }finally{for(int i=opened.size()-1;i>=0;i--)opened.get(i).close();}
    }
    // END natural original-USA Capsule/Results/retained-exit proof 20261107.

}
