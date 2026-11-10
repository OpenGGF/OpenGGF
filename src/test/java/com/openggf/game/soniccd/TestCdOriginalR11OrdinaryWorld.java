package com.openggf.game.soniccd;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.CdMosqui;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.openggf.game.GameServices;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import static org.junit.jupiter.api.Assertions.*;
/** R4 positive requirements FIRST. Real source/factory5/slot/attempt/openSession/Headless boot
 * and caller rows. No private Frame, receipt or captured-state injection. All Java UNRUN. */
class TestCdOriginalR11OrdinaryWorld extends TestCdOriginalR11LoadingWorld {
 private Path captureDirectory;
 @BeforeEach void validateOptionalCaptureDirectory() throws Exception {
  String raw=System.getenv("SONICCD_R11_ORIGINAL_CAPTURE_DIR");
  captureDirectory=raw==null?null:validatedCaptureDirectory(raw);
 }
 private static void ready(com.openggf.GameLoop loop,SonicCdGameModule m){for(int row=0;row<256&&m.originalR11LoadingPrefix().main().levelStartedByte()==0;row++)step(loop,0);assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte());}
 @Test void publicOriginalReadyThenTwoOrdinaryRowsReturnWithActualInputPhysicsAndCpuSat()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var a=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=a.module().orElseThrow();
  try(var boot=boot();var renderCleanup=disposeWhileContextCurrent()){var loop=boot.bootSonicCdSelectedProfile(source,a);ready(loop,m);var f=m.originalR11EntryFrame();var clock=f.vIntClock().absoluteValue().orElseThrow();var services=m.programServicesState();
   assertEquals(0,f.state().motion().routine());step(loop,0);assertEquals(2,f.state().motion().routine());assertTrue(f.environment().movement().physics().topSpeed()>0);
   step(loop,8);assertEquals(8,f.environment().controls().rawHeld());assertEquals(8,f.environment().controls().rawTap());assertEquals(clock+2,f.vIntClock().absoluteValue().orElseThrow());assertFalse(f.closed());
   assertSame(services.loads(),m.programServicesState().loads());assertEquals(services.completedCalls(),m.programServicesState().completedCalls());
   assertTrue(m.originalR11FirstLoad().selection().inputs().selectedCpu().isPresent());assertEquals(640,m.originalR11FirstLoad().selection().inputs().selectedSat().length());
   var pixels=drawClosed(m);exportCapture(CaptureFile.TWO_ORDINARY,pixels,m,2);
  }
 }
 @Test void fullAndCourseReplayBothOrdinaryRowsOnTheSameRealOwner()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var a=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=a.module().orElseThrow();
  try(var boot=boot();var renderCleanup=disposeWhileContextCurrent()){var loop=boot.bootSonicCdSelectedProfile(source,a);ready(loop,m);var r=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=r.capture();var course=r.captureCourse();
   step(loop,0);step(loop,8);var expected=closed(m);var expectedPixels=drawClosed(m);
   r.restore(full);step(loop,0);step(loop,8);assertClosed(expected,m);assertPixels(expectedPixels,drawClosed(m));
   r.restoreCourse(course);step(loop,0);step(loop,8);assertClosed(expected,m);assertPixels(expectedPixels,drawClosed(m));
   // Positive restore from an already ordinary cut, through both real capture forms.
   var ordinaryFull=r.capture();var ordinaryCourse=r.captureCourse();step(loop,8);var next=closed(m);var nextPixels=drawClosed(m);
   r.restore(ordinaryFull);step(loop,8);assertClosed(next,m);assertPixels(nextPixels,drawClosed(m));
   r.restoreCourse(ordinaryCourse);step(loop,8);assertClosed(next,m);assertPixels(nextPixels,drawClosed(m));
  }
 }
 @Test void callerRightRowsRequireActualOpeningRingPickupAndLiveMosquiWithoutPoseOrClockInjection()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var a=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=a.module().orElseThrow();
  try(var boot=boot();var renderCleanup=disposeWhileContextCurrent()){var loop=boot.bootSonicCdSelectedProfile(source,a);ready(loop,m);boolean pickup=false,liveMosqui=false;int rings=m.originalR11LoadingPrefix().ring().rings();
   var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();int rows=0;
   // A source proposal, not a searched/tuned route. Title must release its own lock.
   for(;rows<1800&&!(pickup&&liveMosqui);rows++){step(loop,8);int now=m.originalR11LoadingPrefix().ring().rings();pickup|=now>rings;rings=now;liveMosqui|=m.originalR11PlacementImage().pool().slots().stream().anyMatch(slot->slot.id()==CdMosqui.OBJECT_ID&&slot.routine()!=0);}
   var actor=m.originalR11EntryFrame().state().motion();var controls=m.originalR11EntryFrame().environment().controls();
   assertTrue(pickup,"actual same-walk ring reward required: rows="+rows+", x="+(actor.centreX16_16()>>>16)+", y="+(actor.centreY16_16()>>>16)+", routine="+actor.routine()+", flags="+actor.flags()+", playerControl="+actor.playerControl()+", held="+controls.rawHeld()+", locked="+controls.controlLocked()+", rings="+rings+", mosqui="+liveMosqui);assertTrue(liveMosqui,"actual physical Mosqui handler required");assertTrue(m.originalR11FirstLoad().selection().inputs().selectedCpu().isPresent());
   var expected=closed(m);var endpointPixels=drawClosed(m);var prefix=m.originalR11LoadingPrefix();var reward=prefix.ring();
   // Comparison-only controls: no constructed value is applied to gameplay or a restore owner.
   var changedReward=new com.openggf.game.soniccd.objects.CdRingGlobals((reward.rings()+1)&65535,reward.lives(),reward.livesFlags(),reward.updateHudRings(),reward.updateHudLives(),reward.timeStopTimer(),reward.ringAnimationTimer(),reward.ringAnimationFrame());
   var changedPrefix=new SonicCdGameModule.R11LoadingPrefix(prefix.main(),prefix.goodFuture(),prefix.gameMode(),prefix.goodFutureFlags(),changedReward,prefix.score(),prefix.section(),prefix.normalPalette(),prefix.fadePalette(),prefix.calls(),prefix.blockDefinitions());
   assertNotEquals(material(prefix),material(changedPrefix),"Reward differences must remain visible independently of actor/pool/SAT");
   var palette=m.originalR11FirstLoad().selection().inputs().palette();var bytes=palette.copy();
   assertEquals(material(palette),material(com.openggf.game.soniccd.level.CdAssetBytes.copyOf(bytes)),"Byte comparison is by content");bytes[0]^=1;
   assertNotEquals(material(palette),material(com.openggf.game.soniccd.level.CdAssetBytes.copyOf(bytes)),"Selected byte differences must remain visible");
   registry.restore(full);for(int row=0;row<rows;row++)step(loop,8);assertClosed(expected,m);assertPixels(endpointPixels,drawClosed(m));
   registry.restoreCourse(course);for(int row=0;row<rows;row++)step(loop,8);assertClosed(expected,m);assertPixels(endpointPixels,drawClosed(m));
   exportCapture(CaptureFile.RING_MOSQUI,endpointPixels,m,rows);
  }
 }
 @Test void actualOrdinaryCaptureDoesNotReopenARetiredWorldAndInvalidRowIsANonwrite()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var a=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=a.module().orElseThrow();
  try(var boot=boot();var renderCleanup=disposeWhileContextCurrent()){var loop=boot.bootSonicCdSelectedProfile(source,a);ready(loop,m);step(loop,0);step(loop,8);var f=m.originalR11EntryFrame();var actor=f.captureActor();var shared=f.captureShared();var prefix=m.originalR11LoadingPrefix();var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var complete=closed(m);var healthyPixels=drawClosed(m);
   assertThrows(IllegalArgumentException.class,()->m.serviceOriginalR11Loading(256));assertEquals(actor,f.captureActor());assertEquals(shared,f.captureShared());assertSame(prefix,m.originalR11LoadingPrefix());assertClosed(complete,m);assertPixels(healthyPixels,drawClosed(m));
   SessionManager.clear();assertTrue(f.closed());assertThrows(IllegalStateException.class,()->registry.restore(full));assertThrows(IllegalArgumentException.class,()->registry.restoreCourse(course));assertThrows(IllegalStateException.class,()->m.serviceOriginalR11Loading(0));
  }
 }



 // The existing boot owns real EGL. Retire World/textures and process graphics caches while
 // that context is still current; the inherited cleanup remains the idempotent outer reset.
 private static AutoCloseable disposeWhileContextCurrent(){return ()->{
  Throwable failure=null;
  try{SessionManager.clear();}catch(RuntimeException|Error original){failure=original;}
  try{GameServices.graphics().cleanup();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
  if(failure instanceof RuntimeException original)throw original;
  if(failure instanceof Error original)throw original;
 };}
 @Test void actualPlantProducerSelectsTheNextVIntPictureAndReplaysItsPixels()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot();var cleanup=disposeWhileContextCurrent()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);int rows=0;boolean family=false;
   while(rows<1800&&!family){
    step(loop,8);rows++;
    var slots=m.originalR11PlacementImage().pool().slots();
    family=slots.stream().anyMatch(x->x.id()==com.openggf.game.soniccd.objects.Cd3dPlant.OBJECT_ID&&x.routine()==2)
        &&slots.stream().anyMatch(x->x.id()==com.openggf.game.soniccd.objects.CdFloatBlock.OBJECT_ID&&x.routine()==2)
        &&m.originalR11WorldImage().orElseThrow().cpu().stream().flatMap(cpu->cpu.provenance().stream()).anyMatch(p->{
            int whole=p.slotRef().wholeIndex();return whole>=32&&whole<128
                &&slots.get(whole-32).id()==com.openggf.game.soniccd.objects.Cd3dPlant.OBJECT_ID;
        });
   }
   assertTrue(family,"Both real physical families must return before selecting their picture");
   var plantSlots=new java.util.HashSet<Integer>();for(var slot:m.originalR11PlacementImage().pool().slots())
    if(slot.id()==com.openggf.game.soniccd.objects.Cd3dPlant.OBJECT_ID&&slot.routine()==2)plantSlots.add(slot.identity().wholeIndex());
   var produced=m.originalR11WorldImage().orElseThrow().cpu().orElseThrow();
   assertTrue(produced.provenance().stream().anyMatch(p->plantSlots.contains(p.slotRef().wholeIndex())),"Actual Plant producer must contribute a selected sprite piece");
   step(loop,8);rows++;
   assertSame(produced,m.originalR11FirstLoad().selection().inputs().selectedCpu().orElseThrow(),"The next real VInt selects the preceding completed producer");
   var currentPixels=drawClosed(m);var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();
   var full=registry.capture();var course=registry.captureCourse();
   step(loop,8);rows++;var expected=closed(m);var expectedPixels=drawClosed(m);
   registry.restore(full);step(loop,8);assertClosed(expected,m);assertPixels(expectedPixels,drawClosed(m));
   registry.restoreCourse(course);step(loop,8);assertClosed(expected,m);assertPixels(expectedPixels,drawClosed(m));
   exportCapture(CaptureFile.PLANT_FLOAT,expectedPixels,m,rows);
  }
 }
 private static RgbaImage drawClosed(SonicCdGameModule module){
  var before=closed(module);var first=drawFresh(module,0xFF13C75D);assertClosed(before,module);
  var second=drawFresh(module,0xFFDC3B91);assertClosed(before,module);assertPixels(first,second);return first;
 }
 private static RgbaImage drawFresh(SonicCdGameModule module,int sentinel){
  org.lwjgl.opengl.GL11.glViewport(0,0,320,224);
  org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
  org.lwjgl.opengl.GL11.glColorMask(true,true,true,true);
  org.lwjgl.opengl.GL11.glClearColor(((sentinel>>>16)&255)/255f,((sentinel>>>8)&255)/255f,(sentinel&255)/255f,1);
  org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT|org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
  org.lwjgl.opengl.GL11.glFinish();var cleared=ScreenshotCapture.captureFramebuffer(320,224);
  assertEquals(org.lwjgl.opengl.GL11.GL_NO_ERROR,org.lwjgl.opengl.GL11.glGetError(),"Fresh sentinel readback");
  assertTrue(java.util.Arrays.stream(cleared.pixels()).allMatch(pixel->pixel==sentinel),"Readback must see this freshly cleared target");
  module.gameplayFrameController().drawOverlay();org.lwjgl.opengl.GL11.glFinish();
  var image=ScreenshotCapture.captureFramebuffer(320,224);
  assertEquals(org.lwjgl.opengl.GL11.GL_NO_ERROR,org.lwjgl.opengl.GL11.glGetError(),"Actual original R11 renderer/readback");
  assertEquals(320,image.width());assertEquals(224,image.height());assertEquals(320*224,image.pixels().length);
  assertFalse(java.util.Arrays.equals(cleared.pixels(),image.pixels()),"A retained or untouched sentinel frame is not a draw");
  assertTrue(java.util.Arrays.stream(image.pixels()).map(pixel->pixel&0xFFFFFF).distinct().limit(2).count()>1,"Actual selected picture must be nonblank");
  return image;
 }
 private static void assertPixels(RgbaImage expected,RgbaImage actual){
  assertEquals(expected.width(),actual.width());assertEquals(expected.height(),actual.height());assertArrayEquals(expected.pixels(),actual.pixels());
 }
 private static Path validatedCaptureDirectory(String raw)throws java.io.IOException{
  Path requested=Path.of(raw);
  if(!requested.isAbsolute()||!raw.equals(requested.toString())||!requested.equals(requested.normalize()))throw new IllegalArgumentException("Capture directory must be an exact absolute normalized path");
  Path allowed=Path.of(System.getProperty("user.home"), "Downloads", "Sonic-CD", "captures", "engine").toRealPath();
  if(!Files.isDirectory(requested,LinkOption.NOFOLLOW_LINKS)||!requested.equals(requested.toRealPath())||requested.equals(allowed)||!requested.startsWith(allowed))throw new IllegalArgumentException("Capture directory must be an existing canonical subdirectory of the Sonic CD engine captures");
  return requested;
 }
 private enum CaptureFile{
  TWO_ORDINARY("ENGINE-R11-original-two-ordinary"),RING_MOSQUI("ENGINE-R11-original-ring-mosqui"),PLANT_FLOAT("ENGINE-R11-original-plant-float"),BEFORE_JUMP("ENGINE-R11-original-returned4000"),AFTER_JUMP("ENGINE-R11-original-returned4012-jump"),BEFORE_LEDGE_JUMP("ENGINE-R11-original-returned4096-before-ledge-jump"),NEXT_LEDGE_BEFORE("ENGINE-R11-original-returned4608-before-next-ledge"),NEXT_LEDGE_AIRBORNE("ENGINE-R11-original-returned4640-next-ledge-jump"),STARS_CUT_4822("ENGINE-R11-original-returned4822-reserved-stars-cut"),STARS_CUT_4823("ENGINE-R11-original-returned4823-reserved-stars-cut"),FOURTH_LEDGE_AIRBORNE("ENGINE-R11-original-first-accepted-fourth-ledge-jump");
  private final String stem;CaptureFile(String stem){this.stem=stem;}
 }
 /** Optional ENGINE evidence only. Export runs on the successful path after all replay checks;
  * it supplies no runtime asset, state, authority, golden image, Sonic-visibility or parity claim. */
 private void exportCapture(CaptureFile name,RgbaImage image,SonicCdGameModule module,int ordinaryRows)throws Exception{
  Path directory=captureDirectory;if(directory==null)return;
  if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS)||!directory.equals(directory.toRealPath()))throw new IllegalArgumentException("Validated capture directory changed");
  var before=closed(module);var frame=module.originalR11EntryFrame();var motion=frame.state().motion();
  var bytes=java.nio.ByteBuffer.allocate(image.pixels().length*Integer.BYTES);for(int pixel:image.pixels())bytes.putInt(pixel);
  String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.array()));
  var png=new java.awt.image.BufferedImage(image.width(),image.height(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
  png.setRGB(0,0,image.width(),image.height(),image.pixels(),0,image.width());
  try(var output=Files.newOutputStream(directory.resolve(name.stem+".png"),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
   assertTrue(javax.imageio.ImageIO.write(png,"png",output),"PNG writer must encode the actual readback");
  }
  String metadata="ENGINE OpenGGF original USA R11; actual public selected NEW_GAME/Boot/ordinary drawOverlay; constructed save/profile ingress as in this test\n"
   +"dimensions="+image.width()+"x"+image.height()+"; postReadyCallerRows="+ordinaryRows+"; phase="+module.originalR11LoadingImage().phase()+"; VInt="+frame.vIntClock()+"\n"
   +"routine="+motion.routine()+"; centreXWord="+(motion.centreX16_16()>>>16)+"; centreYWord="+(motion.centreY16_16()>>>16)+"; rings="+module.originalR11LoadingPrefix().ring().rings()+"\n"
   +"argb32_big_endian_sha256="+hash+"\nFresh sentinel clears rejected retained frames; complete closed state unchanged by draw; exact pixel replay compared where required. This file alone proves no native parity or visible Sonic.\n";
  Files.writeString(directory.resolve(name.stem+".txt"),metadata,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);assertClosed(before,module);
 }

 // One read-only complete comparison; these values never enter the capture/restore protocol.
 private record Closed(Object source,Object actorOwner,Object sharedOwner,Object loads,java.util.List<Object> values){}
 private static Closed closed(SonicCdGameModule m){
  var f=m.originalR11EntryFrame();var actor=f.captureActor();var shared=f.captureShared();var first=m.originalR11FirstLoad();var placed=m.originalR11PlacementImage();var world=m.originalR11WorldImage().orElseThrow();
  var values=new java.util.ArrayList<Object>();
  for(var value:java.util.Arrays.asList(actor,shared,m.selectedProfileState(),m.programServicesState(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),placed.status(),placed.pool().slots(),placed.savedFlags(),first.selection().inputs(),first.selection().projection().getClass(),first.foreground(),first.background(),first.copies(),first.levelLoaded(),first.standardLoaded(),first.completedWaits(),first.music(),world.cpu(),world.sounds()))values.add(material(value));
  return new Closed(f.stageSource(),actor.owner(),shared.owner(),m.programServicesState().loads(),java.util.List.copyOf(values));
 }
 private static void assertClosed(Closed expected,SonicCdGameModule m){var actual=closed(m);assertSame(expected.source(),actual.source());assertSame(expected.actorOwner(),actual.actorOwner());assertSame(expected.sharedOwner(),actual.sharedOwner());assertSame(expected.loads(),actual.loads());assertEquals(expected.values(),actual.values());}
 private enum NullValue{ABSENT}
 /** Immutable bytes and records are compared structurally; opaque source/owner and established
  * value classes keep their own equality. No private fields or state writers are invoked. */
 private static Object material(Object value){
  if(value==null)return NullValue.ABSENT;
  if(value instanceof com.openggf.game.soniccd.level.CdAssetBytes bytes)return java.util.List.of(com.openggf.game.soniccd.level.CdAssetBytes.class,java.util.HexFormat.of().formatHex(bytes.copy()));
  if(value instanceof com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat cpu)return java.util.List.of(com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat.class,java.util.HexFormat.of().formatHex(cpu.bytes()),cpu.spriteCount(),material(cpu.provenance()));
  if(value instanceof java.util.Optional<?> optional)return optional.isPresent()?java.util.List.of(java.util.Optional.class,material(optional.orElseThrow())):java.util.List.of(java.util.Optional.class);
  if(value instanceof java.util.List<?> list){var result=new java.util.ArrayList<Object>();for(var item:list)result.add(material(item));return java.util.List.copyOf(result);}
  if(value.getClass().isArray()){var result=new java.util.ArrayList<Object>();for(int n=0;n<java.lang.reflect.Array.getLength(value);n++)result.add(material(java.lang.reflect.Array.get(value,n)));return java.util.List.copyOf(result);}
  if(value.getClass().isRecord()){var result=new java.util.ArrayList<Object>();result.add(value.getClass());for(var field:value.getClass().getRecordComponents()){var getter=field.getAccessor();if(!getter.trySetAccessible())throw new AssertionError("Cannot observe immutable record "+field);try{result.add(material(getter.invoke(value)));}catch(ReflectiveOperationException failure){throw new AssertionError("Immutable record observation failed",failure);}}return java.util.List.copyOf(result);}
  return value;
 }

 /** Diagnostic only: the established returned pickup/Mosqui prefix is compulsory. No stop
  * here means route completion, native parity, death continuation or a regional result. */
 @Test void observeOriginalR11AfterRetained4000ThenCallerJumpToFirstStop()throws Exception{
  observeOriginalR11Route(true);
 }
 @Test void observeOriginalR11AfterReturnedRingAndMosquiToFirstStop()throws Exception{
  observeOriginalR11Route(false);
 }
 private void observeOriginalR11Route(boolean jumpExtension)throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
  try(var boot=boot();var renderCleanup=disposeWhileContextCurrent()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,module);
   // Retain the real read-only Frame and immutable healthy data cuts before a fallible row.
   // Frame.state/environment/vIntClock/closed are direct readers even after its owner closes.
   var frame=module.originalR11EntryFrame();var placed=module.originalR11PlacementImage();
   var prefix=module.originalR11LoadingPrefix();var loading=module.originalR11LoadingImage();
   var selected=module.originalR11FirstLoad().selection().inputs();
   String readyClock=frame.vIntClock().toString();String identity=source.assets().identity().toString();
   int completed=0,attempted=0,cachedCut=0,prefixReturned=0,previousRings=prefix.ring().rings();
   boolean pickup=false,liveMosqui=false,returnedDeath=false;UnsupportedOperationException edge=null;
   String lastHealthy=originalR11ObserverFacts(frame,prefix,loading,placed,selected,cachedCut);
   int maximum=jumpExtension?4512:4000,jumpRequests=0;boolean observedJump=false;
   while(completed<maximum){
    attempted=completed+1;
    int held=jumpExtension&&completed>=4000&&completed<4012?0x18:8;
    if(held==0x18)jumpRequests++;
    try{step(loop,held);}catch(UnsupportedOperationException first){edge=first;break;}
    completed=attempted;
    if(jumpExtension&&completed>4000&&(frame.state().motion().flags()&2)!=0&&frame.state().motion().yVelocity()<0)observedJump=true;
    // Stop on the first returned native death state BEFORE another row, replay or Module read.
    if(frame.state().motion().routine()>=6){returnedDeath=true;break;}
    assertFalse(frame.closed(),"A silently closed returned row is not a supported observer stop");
    placed=module.originalR11PlacementImage();prefix=module.originalR11LoadingPrefix();loading=module.originalR11LoadingImage();selected=module.originalR11FirstLoad().selection().inputs();cachedCut=completed;
    int now=prefix.ring().rings();pickup|=now>previousRings;previousRings=now;
    liveMosqui|=placed.pool().slots().stream().anyMatch(slot->slot.id()==CdMosqui.OBJECT_ID&&slot.routine()!=0);
    if(prefixReturned==0&&pickup&&liveMosqui)prefixReturned=completed;
    lastHealthy=originalR11ObserverFacts(frame,prefix,loading,placed,selected,cachedCut);
    if(jumpExtension&&captureDirectory!=null&&(completed==4000||completed==4012))
     exportCapture(completed==4000?CaptureFile.BEFORE_JUMP:CaptureFile.AFTER_JUMP,drawClosed(module),module,completed);
   }
   String outcome=edge!=null?"FIRST_UNSUPPORTED":returnedDeath?"FIRST_RETURNED_DEATH":"BOUND_REACHED";
   var error=new StringBuilder();
   if(edge!=null){
    String message=String.valueOf(edge.getMessage());if(message.length()>2048)message=message.substring(0,2048)+"[message truncated]";
    error.append("errorClass=").append(edge.getClass().getName()).append("; errorMessage=").append(message).append('\n');
    var stack=edge.getStackTrace();for(int i=0;i<Math.min(12,stack.length);i++){String line=stack[i].toString();error.append(" at ").append(line,0,Math.min(512,line.length())).append('\n');}
    if(stack.length>12)error.append("[remaining stack frames omitted]\n");
   }else error.append("errorClass=none; errorMessage=none\n");
   String report="DIAGNOSTIC OBSERVER ONLY: not route completion, native parity, death continuation, pixels or regional coverage.\n"
    +"ENGINE OpenGGF actual original USA R11 public NEW_GAME/selected Boot; genuine caller input only; no capture restore, pose, clock, pool or Frame injection.\n"
    +"INPUT_POLICY="+(jumpExtension?"retain4000Right8,then12RightPlusB18,thenRight8through4512":"Right8through4000")+"; jumpRequests="+jumpRequests+"; acceptedJumpObserved="+observedJump+"\n"
    +"outcome="+outcome+"; postReadyLastCompletedCallerOrdinal="+completed+"; postReadyLastAttemptedCallerOrdinal="+attempted+"; cachedHealthyModuleCutOrdinal="+cachedCut+"; confirmedPickupAndMosquiReturnedOrdinal="+prefixReturned+"; pickup="+pickup+"; liveMosqui="+liveMosqui+"; readyVInt="+readyClock+"\n"
    +"sourceIdentity="+identity+"\nLAST_COMPLETE_HEALTHY_MODULE_FACTS\n"+lastHealthy
    +"ACTUAL_TERMINAL_FRAME_WITH_CACHED_MODULE_FACTS (cached cut is NOT a failed/death-row Module snapshot)\n"+originalR11ObserverFacts(frame,prefix,loading,placed,selected,cachedCut)
    +error+"An unsupported attempted row did not return. A returned death is terminal for this recipe. No healthy Module getters are called after either stop.\n";
   if(report.length()>32768)report=report.substring(0,32768)+"\n[diagnostic text truncated at32768 characters]\n";
   if(!pickup||!liveMosqui||prefixReturned==0)fail("Required known returned Ring/Mosqui prefix was not established before this observer stop.\n"+report,edge);
   if(jumpExtension)assertTrue(completed>=4000,"The measured returned4000 prefix is compulsory before this jump diagnostic.\n"+report);
   if(edge!=null)assertEquals(completed+1,attempted,"Only the first unsupported attempted row is unreturned");
   else{assertEquals(completed,attempted);if(returnedDeath)assertTrue(frame.state().motion().routine()>=6);else{assertEquals(maximum,completed);if(jumpExtension)assertTrue(observedJump,"No accepted caller jump was observed.\n"+report);}}
   System.out.println(report);
   Path directory=captureDirectory;if(directory!=null){
    if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS)||!directory.equals(directory.toRealPath()))throw new IllegalArgumentException("Validated capture directory changed");
    Files.writeString(directory.resolve(jumpExtension?"ENGINE-R11-original-after4000-caller-jump.txt":"ENGINE-R11-original-after-ring-observer.txt"),report,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
   }
  }
 }
 /** Read-only diagnostic values. Only the retained Frame is live; prefix/loading/pool/selected
  * are explicitly cached immutable cuts, never restored or used to create invocation authority. */
 private static String originalR11ObserverFacts(com.openggf.game.soniccd.player.CdPlayerFrameRuntime frame,
   SonicCdGameModule.R11LoadingPrefix prefix,SonicCdGameModule.R11LoadingState loading,
   com.openggf.game.soniccd.objects.CdObjectPlacementRuntime.Snapshot placed,CdOriginalR11Display.Inputs selected,int cachedOrdinal){
  var motion=frame.state().motion();var controls=frame.environment().controls();var out=new StringBuilder();
  out.append("liveReadOnlyFrame: closed=").append(frame.closed()).append("; VInt=").append(frame.vIntClock())
   .append("; routine=").append(motion.routine()).append("; centreXWord=").append(motion.centreX16_16()>>>16).append("; centreYWord=").append(motion.centreY16_16()>>>16)
   .append("; xLongBits=").append(Integer.toUnsignedString(motion.centreX16_16(),16)).append("; yLongBits=").append(Integer.toUnsignedString(motion.centreY16_16(),16))
   .append("; flags=").append(motion.flags()).append("; playerControl=").append(motion.playerControl()).append("; rawHeld=").append(controls.rawHeld()).append("; rawTap=").append(controls.rawTap()).append("; locked=").append(controls.controlLocked()).append('\n');
  out.append("cachedHealthyModuleCutOrdinal=").append(cachedOrdinal).append("; phase=").append(loading.phase()).append("; fadeSteps=").append(loading.fadeSteps()).append("; loadingDisplay=").append(loading.displayEnabled())
   .append("; ring=").append(prefix.ring()).append("; score=").append(prefix.score()).append("; level=").append(prefix.main().level()).append("; placementStatus=").append(placed.status()).append('\n');
  out.append("cachedActualSelected: display=").append(selected.displayEnabled()).append("; cpuSprites=").append(selected.selectedCpu().map(cpu->Integer.toString(cpu.spriteCount())).orElse("absent"))
   .append("; satBytes=").append(selected.selectedSat().length()).append("; hscrollBytes=").append(selected.hscroll().length()).append("; vscrollBits=").append(Integer.toUnsignedString(selected.vscrollLongBits(),16))
   .append("; paletteHex=").append(java.util.HexFormat.of().formatHex(selected.palette().copy())).append('\n');
  out.append("cachedNonzeroDynamicSlots[whole:id:routine:first16RawBytes]=");
  for(var slot:placed.pool().slots())if(slot.id()!=0)out.append(slot.identity().wholeIndex()).append(':').append(Integer.toHexString(slot.id())).append(':').append(slot.routine()).append(':').append(java.util.HexFormat.of().formatHex(java.util.Arrays.copyOf(slot.copyBytes(),16))).append(';');
  out.append('\n');return out.toString();
 }

 /** Diagnostic input experiment only. Native Boulder/Solid width16+9+1 matches the retained
  * ENGINE4000 side-contact separation26; ObjSonic_JumpHeight (Sonic/Main.asm:1867-1887)
  * bypasses the early release cap while an action button stays held.32 is an experimental
  * input budget, not a native constant. Historical pose values below are comparison-only:
  * they never initialize, restore, correct or otherwise enter a production owner. */
 @Test void diagnosticOriginalR11BoulderHeldJump32To4096()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
  try(var boot=boot();var renderCleanup=disposeWhileContextCurrent()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,module);
   var frame=module.originalR11EntryFrame();var placed=module.originalR11PlacementImage();
   var prefix=module.originalR11LoadingPrefix();var loading=module.originalR11LoadingImage();var selected=module.originalR11FirstLoad().selection().inputs();
   int completed=0,attempted=0,cachedHealthyCut=0,jumpRequests=0;boolean acceptedJump=false;
   UnsupportedOperationException unsupported=null;String outcome="BOUND_REACHED";
   String lastHealthy=originalR11ObserverFacts(frame,prefix,loading,placed,selected,0)
    +"nativeVelocityWords: x="+frame.state().motion().xVelocity()+"; y="+frame.state().motion().yVelocity()+"; ground="+frame.state().motion().groundVelocity()+"\n";
   String cut4000="NOT_REACHED_HEALTHY",cut4032="NOT_REACHED_HEALTHY",cut4096="NOT_REACHED_HEALTHY";
   while(completed<4096){
    attempted=completed+1;int held=completed>=4000&&completed<4032?0x18:8;
    if(held==0x18)jumpRequests++;
    try{step(loop,held);}catch(UnsupportedOperationException first){unsupported=first;outcome="FIRST_UNSUPPORTED";break;}
    completed=attempted;var motion=frame.state().motion();
    if(frame.closed()){outcome="RETURNED_CLOSED_FRAME";break;}
    if(motion.routine()>=6){outcome="FIRST_RETURNED_DEATH";break;}
    // No healthy Module getter is used after either terminal Frame condition or a failed row.
    placed=module.originalR11PlacementImage();prefix=module.originalR11LoadingPrefix();loading=module.originalR11LoadingImage();selected=module.originalR11FirstLoad().selection().inputs();cachedHealthyCut=completed;
    if(completed>4000&&completed<=4032&&motion.playerJump()!=0&&(motion.flags()&2)!=0&&motion.yVelocity()<0)acceptedJump=true;
    lastHealthy=originalR11ObserverFacts(frame,prefix,loading,placed,selected,cachedHealthyCut)
     +"nativeVelocityWords: x="+motion.xVelocity()+"; y="+motion.yVelocity()+"; ground="+motion.groundVelocity()+"\n";
    if(completed==4000||completed==4032||completed==4096){
     String facts="ACTUAL_HEALTHY_CALLER="+completed+"; nativeX="+(motion.centreX16_16()>>>16)+"; nativeY="+(motion.centreY16_16()>>>16)
      +"; xVelocity="+motion.xVelocity()+"; yVelocity="+motion.yVelocity()+"; groundVelocity="+motion.groundVelocity()
      +"; flags="+motion.flags()+"; routine="+motion.routine()+"; rings="+prefix.ring().rings()+"; acceptedJumpObserved="+acceptedJump+"\n";
     if(completed==4000)cut4000=facts;else if(completed==4032)cut4032=facts;else cut4096=facts;
    }
    if(completed==4000){
     var controls=frame.environment().controls();
     boolean sameHistoricalContact=motion.routine()==2&&(motion.centreX16_16()>>>16)==3398&&(motion.centreY16_16()>>>16)==747
      &&motion.flags()==32&&motion.playerControl()==0&&prefix.ring().rings()==16&&controls.rawHeld()==8&&controls.controlLocked()==0;
     if(!sameHistoricalContact){outcome="DIFFERENT_4000_COMPARISON_CUT";break;}
    }
   }
   var terminalMotion=frame.state().motion();var detail=new StringBuilder();
   if(unsupported!=null){
    detail.append("errorClass=").append(unsupported.getClass().getName()).append("; errorMessage=").append(unsupported.getMessage()).append('\n');
    var stack=unsupported.getStackTrace();for(int n=0;n<Math.min(12,stack.length);n++)detail.append(" at ").append(stack[n]).append('\n');
   }else detail.append("errorClass=none; errorMessage=none\n");
   System.out.println("DIAGNOSTIC INPUT EXPERIMENT ONLY: no positive family, completed route, healthy late route, native parity, pixel or regional claim.\n"
    +"ENGINE OpenGGF actual original USA R11 public NEW_GAME/selected Boot; no pose, clock, pool, capture restore or Frame injection.\n"
    +"INPUT_POLICY=4000Right8,32RightPlusB18,64Right8; maximumPostReadyCaller=4096; jumpRequests="+jumpRequests+"; acceptedJumpObserved="+acceptedJump+"\n"
    +"outcome="+outcome+"; lastCompletedCaller="+completed+"; lastAttemptedCaller="+attempted+"; cachedHealthyModuleCut="+cachedHealthyCut+"\n"
    +"CHECKPOINT_4000\n"+cut4000+"\nCHECKPOINT_4032\n"+cut4032+"\nCHECKPOINT_4096\n"+cut4096+"\n"
    +"LAST_COMPLETE_HEALTHY_MODULE_PREFIX\n"+lastHealthy
    +"ACTUAL_TERMINAL_FRAME_WITH_CACHED_MODULE_PREFIX (cached rings/pool are not a terminal-row Module snapshot)\n"
    +originalR11ObserverFacts(frame,prefix,loading,placed,selected,cachedHealthyCut)
    +"terminalNativeVelocityWords: x="+terminalMotion.xVelocity()+"; y="+terminalMotion.yVelocity()+"; ground="+terminalMotion.groundVelocity()+"\n"
    +detail+"NOT_REACHED_HEALTHY means no safe complete checkpoint was sampled. A failed attempted row did not return. Closed/death rows stop before further Module reads. Historical4000 pose is comparison-only; a mismatch ends this experiment. Strict mechanism/closure/Splash obligations are unchanged.\n");
  }
 }

 /** Diagnostic only: retained R255 returned4096 at3573,747 with pushing flag32 and rings16.
  * Native1ecc5f3a FG cell$1C gives a side-solid$61CB block at3584,736; the grass
  * surface is735 versus the left767 floor. This source-backed32px ledge, rather
  * than another Boulder, motivates one32-held/64-release input experiment. */
 @Test void diagnosticOriginalR11AfterBoulderLedgeJumpTo4192()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
  try(var boot=boot();var renderCleanup=disposeWhileContextCurrent()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,module);
   var frame=module.originalR11EntryFrame();var placed=module.originalR11PlacementImage();var prefix=module.originalR11LoadingPrefix();var loading=module.originalR11LoadingImage();var selected=module.originalR11FirstLoad().selection().inputs();
   int completed=0,attempted=0,cachedHealthyCut=0,jumpRequests=0;boolean firstJump=false,ledgeJump=false;
   UnsupportedOperationException unsupported=null;String outcome="BOUND_REACHED";
   String lastHealthy=originalR11ObserverFacts(frame,prefix,loading,placed,selected,0);
   String cut4000="NOT_REACHED_HEALTHY",cut4096="NOT_REACHED_HEALTHY",cut4128="NOT_REACHED_HEALTHY",cut4192="NOT_REACHED_HEALTHY";
   while(completed<4192){
    attempted=completed+1;int held=((completed>=4000&&completed<4032)||(completed>=4096&&completed<4128))?0x18:8;if(held==0x18)jumpRequests++;
    try{step(loop,held);}catch(UnsupportedOperationException first){unsupported=first;outcome="FIRST_UNSUPPORTED";break;}
    completed=attempted;var motion=frame.state().motion();
    if(frame.closed()){outcome="RETURNED_CLOSED_FRAME";break;}if(motion.routine()>=6){outcome="FIRST_RETURNED_DEATH";break;}
    placed=module.originalR11PlacementImage();prefix=module.originalR11LoadingPrefix();loading=module.originalR11LoadingImage();selected=module.originalR11FirstLoad().selection().inputs();cachedHealthyCut=completed;
    boolean jumping=motion.playerJump()!=0&&(motion.flags()&2)!=0&&motion.yVelocity()<0;
    if(completed>4000&&completed<=4032&&jumping)firstJump=true;if(completed>4096&&completed<=4128&&jumping)ledgeJump=true;
    lastHealthy=originalR11ObserverFacts(frame,prefix,loading,placed,selected,cachedHealthyCut)
     +"nativeVelocityWords: x="+motion.xVelocity()+"; y="+motion.yVelocity()+"; ground="+motion.groundVelocity()+"\n";
    if(completed==4000||completed==4096||completed==4128||completed==4192){
     String facts="ACTUAL_HEALTHY_CALLER="+completed+"; nativeX="+(motion.centreX16_16()>>>16)+"; nativeY="+(motion.centreY16_16()>>>16)
      +"; xLongBits="+Integer.toUnsignedString(motion.centreX16_16(),16)+"; yLongBits="+Integer.toUnsignedString(motion.centreY16_16(),16)
      +"; xVelocity="+motion.xVelocity()+"; yVelocity="+motion.yVelocity()+"; groundVelocity="+motion.groundVelocity()
      +"; flags="+motion.flags()+"; routine="+motion.routine()+"; rings="+prefix.ring().rings()+"; firstJumpObserved="+firstJump+"; ledgeJumpObserved="+ledgeJump+"\n";
     if(completed==4000)cut4000=facts;else if(completed==4096)cut4096=facts;else if(completed==4128)cut4128=facts;else cut4192=facts;
    }
    if(completed==4096&&captureDirectory!=null)exportCapture(CaptureFile.BEFORE_LEDGE_JUMP,drawClosed(module),module,completed);
    if(completed==4000||completed==4096){
     var controls=frame.environment().controls();int expectedX=completed==4000?3398:3573;
     boolean sameHistoricalCut=motion.routine()==2&&(motion.centreX16_16()>>>16)==expectedX&&(motion.centreY16_16()>>>16)==747
      &&motion.flags()==32&&motion.playerControl()==0&&prefix.ring().rings()==16&&controls.rawHeld()==8&&controls.controlLocked()==0;
     if(!sameHistoricalCut){outcome="DIFFERENT_"+completed+"_COMPARISON_CUT";break;}
    }
   }
   var terminalMotion=frame.state().motion();var detail=new StringBuilder();
   if(unsupported!=null){detail.append("errorClass=").append(unsupported.getClass().getName()).append("; errorMessage=").append(unsupported.getMessage()).append('\n');var stack=unsupported.getStackTrace();for(int n=0;n<Math.min(12,stack.length);n++)detail.append(" at ").append(stack[n]).append('\n');}
   else detail.append("errorClass=none; errorMessage=none\n");
   System.out.println("DIAGNOSTIC INPUT EXPERIMENT ONLY: no positive family, completed route, native parity, pixel parity or regional claim.\n"
    +"ENGINE OpenGGF actual original USA R11 public NEW_GAME/selected Boot; fixed input only; historical cuts are comparison-only and never hydrated.\n"
    +"INPUT_POLICY=4000Right8,32RightPlusB18,64Right8,32RightPlusB18,64Right8; maximumPostReadyCaller=4192; jumpRequests="+jumpRequests+"; firstJumpObserved="+firstJump+"; ledgeJumpObserved="+ledgeJump+"\n"
    +"outcome="+outcome+"; lastCompletedCaller="+completed+"; lastAttemptedCaller="+attempted+"; cachedHealthyModuleCut="+cachedHealthyCut+"\n"
    +"CHECKPOINT_4000\n"+cut4000+"\nCHECKPOINT_4096\n"+cut4096+"\nCHECKPOINT_4128\n"+cut4128+"\nCHECKPOINT_4192\n"+cut4192+"\n"
    +"LAST_COMPLETE_HEALTHY_MODULE_PREFIX\n"+lastHealthy
    +"ACTUAL_TERMINAL_FRAME_WITH_CACHED_MODULE_PREFIX (cached rings/pool are not a terminal-row Module snapshot)\n"+originalR11ObserverFacts(frame,prefix,loading,placed,selected,cachedHealthyCut)
    +"terminalNativeVelocityWords: x="+terminalMotion.xVelocity()+"; y="+terminalMotion.yVelocity()+"; ground="+terminalMotion.groundVelocity()+"\n"
    +detail+"NOT_REACHED_HEALTHY means no safe complete checkpoint was sampled. Failed attempted rows did not return. No fresh Module getters follow closed/death/unsupported stops. Optional4096 PNG uses fresh sentinel/nonwrite helpers; no image authenticates native parity. Strict obligations remain unchanged.\n");
  }
 }

 /** Diagnostic continuation only. Preserve the three public held-B intervals, then request
  * one fourth held jump at caller-index6000..6031. R272 returned8192 alive at X5781/Y779;
  * initial native cells at X5792 are solid at Y760/779 and clear at Y744. This fixed public
  * input is an experiment, not a collision correction or a native timing constant. A healthy
  * Flower and the prior third jump remain compulsory;10000 is only this finite observation. */
 @Test void diagnosticOriginalR11AfterStarsNextLedgeJumpTo10000()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
  try(var boot=boot();var renderCleanup=disposeWhileContextCurrent()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,module);
   var frame=module.originalR11EntryFrame();var placed=module.originalR11PlacementImage();var prefix=module.originalR11LoadingPrefix();var loading=module.originalR11LoadingImage();var selected=module.originalR11FirstLoad().selection().inputs();
   int completed=0,attempted=0,cachedHealthyCut=0,firstReturnedFlower=0,jumpRequests=0;boolean nextLedgeJump=false,fourthLedgeJump=false;int firstAcceptedFourthJump=0;String outcome="BOUND_REACHED";
   UnsupportedOperationException unsupported=null;
   String lastHealthy=originalR11ObserverFacts(frame,prefix,loading,placed,selected,0)+afterFlowerActorFacts(frame);
   String flowerHealthy="NOT_REACHED_HEALTHY";var checkpoints=new StringBuilder();
   while(completed<10000){
    attempted=completed+1;int held=((completed>=4000&&completed<4032)||(completed>=4096&&completed<4128)||(completed>=4608&&completed<4640)||(completed>=6000&&completed<6032))?0x18:8;if(held==0x18)jumpRequests++;
    try{step(loop,held);}catch(UnsupportedOperationException first){unsupported=first;outcome="FIRST_UNSUPPORTED";break;}
    completed=attempted;var motion=frame.state().motion();
    // Neither a returned death nor a closed Frame grants a healthy Module snapshot/replay.
    if(motion.routine()>=6){outcome="FIRST_RETURNED_DEATH";break;}
    if(frame.closed()){outcome="FIRST_RETURNED_CLOSED_FRAME";break;}
    placed=module.originalR11PlacementImage();prefix=module.originalR11LoadingPrefix();loading=module.originalR11LoadingImage();selected=module.originalR11FirstLoad().selection().inputs();cachedHealthyCut=completed;
    lastHealthy=originalR11ObserverFacts(frame,prefix,loading,placed,selected,cachedHealthyCut)+afterFlowerActorFacts(frame);
    if(completed>4608&&completed<=4640&&motion.playerJump()!=0&&(motion.flags()&2)!=0&&motion.yVelocity()<0)nextLedgeJump=true;
    if(completed>6000&&completed<=6032&&motion.playerJump()!=0&&(motion.flags()&2)!=0&&motion.yVelocity()<0&&!fourthLedgeJump){
     fourthLedgeJump=true;firstAcceptedFourthJump=completed;
     if(captureDirectory!=null)exportCapture(CaptureFile.FOURTH_LEDGE_AIRBORNE,drawClosed(module),module,completed);
    }
    if(captureDirectory!=null&&(completed==4608||completed==4640))exportCapture(completed==4608?CaptureFile.NEXT_LEDGE_BEFORE:CaptureFile.NEXT_LEDGE_AIRBORNE,drawClosed(module),module,completed);
    if(completed==4822||completed==4823){
     checkpoints.append("ACTUAL_HEALTHY_RESERVED_12_TO_15_CALLER=").append(completed).append("; raw256Hex=").append(java.util.HexFormat.of().formatHex(loading.reserved().slice(11*64,4*64).copy())).append("; powerup=").append(prefix.main().level().powerup()).append('\n').append(afterFlowerActorFacts(frame));
     if(captureDirectory!=null)exportCapture(completed==4822?CaptureFile.STARS_CUT_4822:CaptureFile.STARS_CUT_4823,drawClosed(module),module,completed);
    }
    if(firstReturnedFlower==0&&placed.pool().slots().stream().anyMatch(slot->slot.id()==0x1F&&slot.routine()!=0)){
     firstReturnedFlower=completed;assertFalse(frame.closed());assertTrue(motion.routine()<6);
     flowerHealthy="ACTUAL_HEALTHY_RETURNED_FLOWER_CALLER="+completed+"\n"+lastHealthy;
    }
    if(completed==4000||completed==4032||completed==4096||completed==4128||completed==4192||completed==4608||completed==4640||completed==4800||completed==4822||completed==4823||completed==6000||completed==6032||completed==6144||completed==8192||completed==10000)
     checkpoints.append("ACTUAL_HEALTHY_CALLER=").append(completed).append("; nativeX=").append(motion.centreX16_16()>>>16).append("; nativeY=").append(motion.centreY16_16()>>>16)
      .append("; routine=").append(motion.routine()).append("; flags=").append(motion.flags()).append("; xVelocity=").append(motion.xVelocity()).append("; yVelocity=").append(motion.yVelocity()).append("; groundVelocity=").append(motion.groundVelocity())
      .append("; rings=").append(prefix.ring().rings()).append("; score=").append(prefix.score()).append("; level=").append(prefix.main().level()).append("; VInt=").append(frame.vIntClock()).append("; firstReturnedFlower=").append(firstReturnedFlower).append('\n');
   }
   var boundary=new StringBuilder();
   if(unsupported!=null){
    String message=String.valueOf(unsupported.getMessage());
    boundary.append("firstBoundarySlot=").append(afterFlowerBoundaryOperand(message,"slot")).append("; firstBoundaryWhole=").append(afterFlowerBoundaryOperand(message,"whole")).append("; firstBoundaryId=").append(afterFlowerBoundaryOperand(message,"id")).append(" (literal message tokens; decimal/hex spelling is not normalized)\n")
     .append("errorClass=").append(unsupported.getClass().getName()).append("; errorMessage=").append(message).append('\n');
    var stack=unsupported.getStackTrace();for(int n=0;n<Math.min(12,stack.length);n++)boundary.append(" at ").append(stack[n]).append('\n');
   }else boundary.append("firstBoundarySlot=not-reported; firstBoundaryWhole=not-reported; firstBoundaryId=not-reported; errorClass=none; errorMessage=none\n");
   String report="DIAGNOSTIC OBSERVER ONLY: a bound/unsupported/death/closed stop is not completed route, stage closure, reload, warp, pixel, native or regional parity.\n"
    +"ENGINE OpenGGF actual original USA R11 public NEW_GAME/selected Boot and genuine Ready. Fixed public input only; no pool, pose, Frame, clock or captured-state hydration.\n"
    +"INPUT_POLICY=Right8 except caller-index4000..4031,4096..4127,4608..4639 and6000..6031 RightPlusB18; maximumCompletedPostReadyCallers=10000; jumpRequests="+jumpRequests+"\n"
    +"outcome="+outcome+"; lastCompletedCaller="+completed+"; lastAttemptedCaller="+attempted+"; cachedHealthyModuleCut="+cachedHealthyCut+"; firstHealthyReturnedFlowerCaller="+firstReturnedFlower+"; nextLedgeJumpObserved="+nextLedgeJump+"; fourthLedgeJumpObserved="+fourthLedgeJump+"; firstAcceptedFourthJumpCaller="+firstAcceptedFourthJump+"\n"
    +boundary+"FIRST_HEALTHY_RETURNED_FLOWER_PREFIX\n"+flowerHealthy+"\nSELECTED_HEALTHY_CHECKPOINTS\n"+checkpoints
    +"LAST_COMPLETE_HEALTHY_MODULE_PREFIX\n"+lastHealthy
    +"ACTUAL_TERMINAL_FRAME_WITH_CACHED_MODULE_PREFIX (cached pool/reward/level are not a failed/death/closed-row Module snapshot)\n"
    +originalR11ObserverFacts(frame,prefix,loading,placed,selected,cachedHealthyCut)+afterFlowerActorFacts(frame)
    +"Only message-reported boundary tokens are extracted; not-reported is UNKNOWN, never inferred from cached slots. Failed attempted rows did not return. No fresh Module getter follows any terminal stop. Strict Flower, mechanisms, checkpoint, Splash and stage-closure obligations remain unchanged. Optional4822/4823 reserved-star checkpoints and first-accepted-fourth-jump pictures use the existing canonical/sentinel/nonwrite owner. They are actual healthy ENGINE output only, not reference/native/parity evidence.\n";
   System.out.println(report);
   if(firstReturnedFlower==0)fail("Required healthy returned original R11 Flower prefix was not established before the diagnostic stop.\n"+report,unsupported);
   assertTrue(nextLedgeJump,"The third actual caller jump must be accepted before the diagnostic stop.\n"+report);
   if(unsupported!=null)assertEquals(completed+1,attempted,"Only the first unsupported attempted row is unreturned");
   else{assertEquals(completed,attempted);if(outcome.equals("FIRST_RETURNED_DEATH"))assertTrue(frame.state().motion().routine()>=6);else if(outcome.equals("FIRST_RETURNED_CLOSED_FRAME"))assertTrue(frame.closed());else assertEquals(10000,completed);}
  }
 }
 /** Read-only current Frame values; no closed-Frame capture/restore capability is requested. */
 private static String afterFlowerActorFacts(com.openggf.game.soniccd.player.CdPlayerFrameRuntime frame){
  var state=frame.state();var environment=frame.environment();
  return "actualReadOnlyFrameActor: motion="+state.motion()+"; header="+state.header()+"; animation="+state.animation()+"; timers="+state.timers()+"; objectLinks="+state.objectLinks()+"; rotationDistance="+state.rotationDistance()+"; rotationAngle="+state.rotationAngle()+"\n"
   +"actualReadOnlyFrameEnvironment: controls="+environment.controls()+"; globals="+environment.globals()+"; movementGates="+environment.movement().gates()+"; movementGlobals="+environment.movement().globals()+"; VInt="+frame.vIntClock()+"\n";
 }
 /** Preserve only an explicitly written message operand; absence conveys no slot/ID knowledge. */
 private static String afterFlowerBoundaryOperand(String message,String key){
  var matcher=java.util.regex.Pattern.compile("(?<![A-Za-z0-9_])"+java.util.regex.Pattern.quote(key)+"=([^\\s;,]+)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(message);
  return matcher.find()?matcher.group(1):"not-reported";
 }
}
