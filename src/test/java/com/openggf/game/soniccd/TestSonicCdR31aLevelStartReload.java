package com.openggf.game.soniccd;

import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine selected historical-save recipe, fresh Boot/FULL/loading; no failed-World retry.
 * Main405-408 -> LevelStart19-329 at N1ecc5f3a. No state/clock/controller hydration. */
@Isolated class TestSonicCdR31aLevelStartReload {
 @TempDir Path directory;
 final TestSonicCdR31aSelectedProfileLoading fixture=new TestSonicCdR31aSelectedProfileLoading();
 @BeforeEach void setup(){fixture.directory=directory;fixture.setup();}
 record Picture(String name,byte[] rgba,String state,String provenance){}
 final List<Picture> pictures=new ArrayList<>();
 @AfterEach void cleanup()throws Exception{
  fixture.cleanup();String out=System.getProperty("soniccd.r31.capture.dir");if(out==null)return;
  Path root=Path.of(Objects.requireNonNull(System.getenv("SONICCD_ENGINE_CAPTURE_ROOT"))).toRealPath(),dest=Path.of(out).toRealPath();assertTrue(dest.startsWith(root)&&!dest.equals(root));
  for(var p:pictures){var image=new java.awt.image.BufferedImage(320,224,java.awt.image.BufferedImage.TYPE_INT_ARGB);byte[] bytes=p.rgba();for(int y=0;y<224;y++)for(int x=0;x<320;x++){int k=((223-y)*320+x)*4;image.setRGB(x,y,((bytes[k+3]&255)<<24)|((bytes[k]&255)<<16)|((bytes[k+1]&255)<<8)|(bytes[k+2]&255));}
   try(var output=Files.newOutputStream(dest.resolve(p.name()+".png"),StandardOpenOption.CREATE_NEW)){assertTrue(javax.imageio.ImageIO.write(image,"png",output));}
   Files.writeString(dest.resolve(p.name()+".state.txt"),p.state(),StandardOpenOption.CREATE_NEW);Files.writeString(dest.resolve(p.name()+".txt"),p.provenance()+"\nExported after fixture/session/GL cleanup completed.\n",StandardOpenOption.CREATE_NEW);
  }
 }
 static void step(TestSonicCdR31aSelectedProfileLoading.Booted b){TestSonicCdR31aSelectedProfileLoading.step(b,8);}
 static void beforeTransfer(TestSonicCdR31aSelectedProfileLoading.Booted b){TestSonicCdR31aSelectedProfileLoading.ready(b);for(int n=1;n<=494;n++)step(b);}
 static void replayNext(TestSonicCdR31aSelectedProfileLoading.Booted b)throws Exception{
  var r=b.mode.getRewindRegistry();var full=r.capture();var course=r.captureCourse();var before=TestSonicCdR31aSelectedProfileLoading.values(b);var pixels=TestSonicCdR31aSelectedProfileLoading.draw(b);step(b);var after=TestSonicCdR31aSelectedProfileLoading.values(b);var next=TestSonicCdR31aSelectedProfileLoading.draw(b);
  for(boolean c:new boolean[]{false,true}){if(c)r.restoreCourse(course);else r.restore(full);assertEquals(before,TestSonicCdR31aSelectedProfileLoading.values(b));assertArrayEquals(pixels,TestSonicCdR31aSelectedProfileLoading.draw(b));step(b);assertEquals(after,TestSonicCdR31aSelectedProfileLoading.values(b));assertArrayEquals(next,TestSonicCdR31aSelectedProfileLoading.draw(b));}
  r.restore(full);assertEquals(before,TestSonicCdR31aSelectedProfileLoading.values(b));
 }
 void export(TestSonicCdR31aSelectedProfileLoading.Booted b,String name)throws Exception{
  String out=System.getProperty("soniccd.r31.capture.dir");if(out==null)return;byte[] rgba=TestSonicCdR31aSelectedProfileLoading.draw(b);
  var frame=b.module.requirePlayerFrame();var host=b.module.r31WorldHost();var bank=host.placement().capture();
  String state="ACTUAL RETURNED engine cut; not native-image/regional parity\nCore="+b.module.r31LoadingVisit().state()+"\nState8="+frame.state()+"\nHistory="+HexFormat.of().formatHex(frame.state().history().bytes().copy())+"\nEnvironment="+frame.environment()+"\nClock="+frame.vIntClock()+"\nGlobals="+host.globals()+"\nScroll="+host.scroll()+"\nCPU="+HexFormat.of().formatHex(host.video().cpuSat().copy())+"\nStatus="+bank.status()+"\nSaved="+HexFormat.of().formatHex(bank.savedFlags().copy())+"\nPool="+bank.pool().slots().stream().map(s->s.identity().wholeIndex()+":"+HexFormat.of().formatHex(s.copyBytes())).toList()+"\n";
  String provenance="ENGINE source="+b.module.r31LoadingVisit().assets().identity()+"; phase="+b.module.r31LoadingVisit().state().phase()+"; levelFrames="+frame.environment().globals().levelFrames()+"; VInt="+frame.vIntClock()+"; generation="+b.mode.getLevelManager().getCompletedProductionLoadGeneration()+"; rgba_sha256="+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(rgba));
  pictures.add(new Picture(name,rgba,state,provenance));
 }

 @Test void freshOrdinaryControllerCompletesBothRealReloadsThrough1200()throws Exception{
  try(var b=fixture.start(1,0x80)){
   beforeTransfer(b);var core=b.module.r31LoadingVisit();var frame=b.module.requirePlayerFrame();var host=b.module.r31WorldHost();var placement=host.placement();long generation=b.mode.getLevelManager().getCompletedProductionLoadGeneration();
   var old=core.state();assertEquals(494,frame.environment().globals().levelFrames());assertEquals(1,frame.state().timers().reset());assertEquals(2,host.globals().ring().lives());assertEquals(0,old.level().checkpoint());assertEquals(0,old.level().spawnMode());assertEquals(3,old.level().plcLoadFlags());assertEquals(1,old.program().palFadeFlagsByte()&1);assertEquals(1,frame.environment().movement().gates().timeZone());
   export(b,"last-closed-pre-transfer-494");replayNext(b);var oldCpu=host.video().completedCpu();var actor=frame.state();int oldCalls=core.state().calls().size();step(b);
   assertEquals(SonicCdR31aLoadingVisit.Phase.INITIAL_TO_BLACK,core.state().phase());assertEquals(495,frame.environment().globals().levelFrames());assertEquals(0,frame.state().timers().reset());assertEquals(1,core.state().level().levelRestart());assertEquals(1,core.state().level().plcLoadFlags());assertEquals(2,host.globals().ring().lives());assertEquals(0,core.state().initialFadeSteps());assertEquals(0,core.state().fromFadeSteps());assertSame(oldCpu,host.video().completedCpu());assertEquals(1,Collections.frequency(core.state().calls().subList(oldCalls,core.state().calls().size()),SonicCdR31aLoadingVisit.Call.COPY_AND_DRAW),"The actual selector8 copy precedes RunObjects; no obsolete Main draw tail runs");
   assertEquals(actor.history(),frame.state().history());export(b,"returned-levelstart-transfer-495");
   int returned=495,ordinaryAfter=0;boolean reentered=false;var phases=new ArrayList<String>();Throwable frontier=null;var last=TestSonicCdR31aSelectedProfileLoading.values(b);var lastFrame=frame.state();var lastEnv=frame.environment();var lastBank=placement.capture();var lastClock=frame.vIntClock();
   for(int service=496;service<=1200;service++){
    var prior=core.state();try{step(b);}catch(UnsupportedOperationException reached){frontier=reached;break;}returned=service;
    assertSame(frame,b.module.requirePlayerFrame());assertSame(host,b.module.r31WorldHost());assertSame(core,b.module.r31LoadingVisit());assertEquals(generation,b.mode.getLevelManager().getCompletedProductionLoadGeneration());if(!reentered)assertEquals(2,host.globals().ring().lives(),"Reload does not reset lives; later ordinary deaths remain native");
    if(core.state().phase()!=prior.phase()){String transition=service+":"+prior.phase()+"->"+core.state().phase()+" levelFrames="+frame.environment().globals().levelFrames()+" clock="+frame.vIntClock();phases.add(transition);System.out.println("R31_RELOAD_PHASE "+transition);export(b,"phase-"+service+"-"+core.state().phase());replayNext(b);}
    if(core.state().phase()==SonicCdR31aLoadingVisit.Phase.READY){if(!reentered){reentered=true;assertEquals(0,frame.environment().globals().levelFrames());assertEquals(0,frame.state().motion().routine());assertEquals(1,frame.state().header().slotId());assertEquals(0,core.state().level().levelRestart());}else if(prior.phase()==SonicCdR31aLoadingVisit.Phase.READY)ordinaryAfter++;}
    last=TestSonicCdR31aSelectedProfileLoading.values(b);lastFrame=frame.state();lastEnv=frame.environment();lastBank=placement.capture();lastClock=frame.vIntClock();
    if(ordinaryAfter==2){assertEquals(2,frame.environment().globals().levelFrames());assertEquals(2,frame.state().motion().routine());export(b,"reloaded-first-ordinary-main-"+service);}
   }
   String evidence="R31_RELOAD returned="+returned+" attempted="+(frontier==null?"none":returned+1)+" reenteredReady="+reentered+" ordinaryAfter="+ordinaryAfter+" firstFrontier="+frontier+"\nPhases="+phases+"\nLAST_CLOSED_State8="+lastFrame+"\nLAST_CLOSED_Environment="+lastEnv+"\nLAST_CLOSED_Clock="+lastClock+"\nLAST_CLOSED_Status="+lastBank.status()+"\nLAST_CLOSED_values="+last+"\nPARTIAL_Core="+core.state()+"\nPARTIAL_State8="+frame.state()+"\nPARTIAL_Environment="+frame.environment()+"\nPARTIAL_Clock="+frame.vIntClock()+"\nPARTIAL_Status="+placement.status()+"\n";
   System.out.println(evidence);String out=System.getProperty("soniccd.r31.capture.dir");if(out!=null)Files.writeString(Path.of(out).resolve("reload-continuation.txt"),evidence,StandardOpenOption.CREATE_NEW);
   if(frontier!=null){assertTrue(frame.closed());var failedState=frame.state();var failedEnv=frame.environment();assertThrows(IllegalStateException.class,()->step(b));assertSame(failedState,frame.state());assertSame(failedEnv,frame.environment());}
   assertNull(frontier,evidence);assertTrue(reentered);assertEquals(1200,returned);assertEquals(612,ordinaryAfter);
   // Native22-step fades and compulsory selectors retain both actual reload backedges.
   assertEquals(List.of("517:INITIAL_TO_BLACK->FIRST_PLC","518:FIRST_PLC->LEVEL_PLC",
       "519:LEVEL_PLC->FROM_BLACK","541:FROM_BLACK->READY","1036:READY->INITIAL_TO_BLACK",
       "1058:INITIAL_TO_BLACK->FIRST_PLC","1059:FIRST_PLC->LEVEL_PLC",
       "1060:LEVEL_PLC->FROM_BLACK","1082:FROM_BLACK->READY"),
       phases.stream().map(value->value.substring(0,value.indexOf(" levelFrames="))).toList());
  }
 }
 @Test void valuesOrIdleCallsCannotMintTheOriginalWorldTransfer()throws Exception{
  try(var b=fixture.start(1,0x80)){TestSonicCdR31aSelectedProfileLoading.ready(b);var field=SonicCdGameModule.class.getDeclaredField("r31World");field.setAccessible(true);var world=(CdWorld)field.get(b.module);var before=TestSonicCdR31aSelectedProfileLoading.values(b);assertThrows(IllegalStateException.class,()->world.requireLevelStartOwner(b.module.r31LoadingVisit()));assertThrows(IllegalStateException.class,()->world.abandonLevelStartWalk(b.module.r31LoadingVisit()));assertEquals(before,TestSonicCdR31aSelectedProfileLoading.values(b));assertFalse(b.module.requirePlayerFrame().closed());}
 }
}
