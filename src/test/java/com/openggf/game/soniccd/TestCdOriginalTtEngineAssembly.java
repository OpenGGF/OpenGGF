package com.openggf.game.soniccd;
import com.openggf.game.save.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Real source/engine assembly requirements, tests FIRST and Java UNRUN.
 * Engine geometry commits no native Ready/ordinary result; no private source/Frame/capture is made. */
@Isolated class TestCdOriginalTtEngineAssembly extends TestCdOriginalR11LoadingWorld {
 private SonicCdGameModule.SelectedProfileAttempt tt(CdGameDataSource source)throws Exception{
  var saves=new SaveManager(root);var save=CdSaveProfile.fromTemplate(source.initialSaveAssets().orElseThrow()).withPatch(new CdIpxProgress.SavePatch(OptionalInt.of(0x200),OptionalInt.of(4),OptionalInt.of(0x90),OptionalInt.of(0),OptionalInt.of(0x35),OptionalInt.of(2),OptionalInt.of(3)));saves.writeSlot("scd",1,save.encode());return SonicCdGameModule.prepareSelectedProfile(source,saves,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,0);
 }
 @Test void realTtDataProjectsThroughTheSharedGeometryWithoutAnR11AssetSubstitute()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var loading=source.programmeData(CdStagePayload.R41A.stage()).orElseThrow().levelLoadingSource();var first=SonicCdLevel.fromLoadingSource(loading);var second=SonicCdLevel.fromLoadingSource(loading);
  assertSame(loading,first.loadingSource());assertSame(loading.placements(),first.nativePlacements());assertEquals(loading.initialCentre(),first.initialCentre());assertNotSame(first,second);assertNotSame(first.getMap(),second.getMap());assertThrows(UnsupportedOperationException.class,first::assets);
  assertEquals(loading.bounds().left(),first.getMinX());assertEquals(loading.bounds().right(),first.getMaxX());assertEquals(loading.bounds().top(),first.getMinY());assertEquals(loading.bounds().bottom(),first.getMaxY());assertEquals(2,first.getZoneIndex());assertEquals(0x200,loading.nativeZoneActWord());assertEquals(188,loading.playerSource().sonic().mappingFrames().size());
 }
 @Test void publicTtEngineCommitsOneActualGenerationAndKeepsLoadingFullAndCourseOwners()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var module=attempt.module().orElseThrow();
  try(var boot=boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);var mode=SessionManager.getCurrentGameplayMode();var manager=mode.getLevelManager();var level=assertInstanceOf(SonicCdLevel.class,manager.getCurrentLevel());var loading=source.programmeData(CdStagePayload.R41A.stage()).orElseThrow().levelLoadingSource();
   assertSame(loading,level.loadingSource());assertEquals(2,level.getZoneIndex());assertEquals(6,module.getZoneRegistry().getLevelDataForZone(2).getFirst().levelIndex());assertArrayEquals(new int[]{80,64},module.getZoneRegistry().getStartPosition(2,0));assertThrows(UnsupportedOperationException.class,()->module.getZoneRegistry().getStartPosition(0,0));assertTrue(module.originalR11EngineLoadingAssembled());assertEquals(CdStagePayload.R41A.stage(),module.originalR11EntryFrame().stageSource().stage());assertSame(loading.retainedPlayerSource(),module.originalR11EntryFrame().stageSource());assertThrows(UnsupportedOperationException.class,level::assets);
   var generation=manager.getCompletedProductionLoadGeneration();var services=module.programServicesState();var frame=module.originalR11EntryFrame();var registry=mode.getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
   var before=image(module);module.gameplayFrameController().drawOverlay();CdCapturedValueAssertions.assertCapturedValueEquals(before,image(module));
   step(loop,0);var expected=image(module);registry.restore(full);step(loop,0);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));registry.restoreCourse(course);step(loop,0);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));
   assertEquals(generation,manager.getCompletedProductionLoadGeneration());assertSame(level,manager.getCurrentLevel());assertSame(frame,module.originalR11EntryFrame());assertSame(services.loads(),module.programServicesState().loads());assertEquals(services.completedCalls(),module.programServicesState().completedCalls());
  }
 }
 @Test void engineAssemblyDoesNotGrantReentryAnotherThreadOrRetiredCapture()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var module=attempt.module().orElseThrow();
  try(var boot=boot()){
   boot.bootSonicCdSelectedProfile(source,attempt);var before=image(module);assertThrows(IllegalStateException.class,()->module.createGame(source));CdCapturedValueAssertions.assertCapturedValueEquals(before,image(module));
   var fault=new java.util.concurrent.atomic.AtomicReference<Throwable>();var worker=new Thread(()->{try{module.createGame(source);}catch(Throwable e){fault.set(e);}});worker.start();worker.join();assertInstanceOf(IllegalStateException.class,fault.get());CdCapturedValueAssertions.assertCapturedValueEquals(before,image(module));
   var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var frame=module.originalR11EntryFrame();SessionManager.clear();assertTrue(frame.closed());assertFalse(module.originalR11EngineLoadingAssembled());assertThrows(IllegalStateException.class,()->registry.restore(full));
  }
 }
 @Test void originalR11StillUsesItsOwnAssetsAndTheSameSelectedFullPipeline()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
  try(var boot=boot()){
   boot.bootSonicCdSelectedProfile(source,attempt);var manager=SessionManager.getCurrentGameplayMode().getLevelManager();var level=assertInstanceOf(SonicCdLevel.class,manager.getCurrentLevel());assertThrows(UnsupportedOperationException.class,level::assets);assertSame(source.assets().retainedStandardLoadingSource(),level.loadingSource());assertTrue(level.loadingSource().ownsRetainedStandardAssets(source.assets()));assertSame(source.assets().placements(),level.nativePlacements());assertTrue(module.originalR11EngineLoadingAssembled());assertTrue(module.originalR11EntryFrame().stageSource().ownsPresent(source.assets()));
  }
 }
 @Test void closedTtFrameRejectsFullAndCourseRestoreBeforeCameraMutation()throws Exception{assertClosedFrameRestorePreflight(true);}
 @Test void closedR11FrameRejectsFullAndCourseRestoreBeforeCameraMutation()throws Exception{assertClosedFrameRestorePreflight(false);}
 private void assertClosedFrameRestorePreflight(boolean water)throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=water?tt(source):SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
  try(var boot=boot()){
   boot.bootSonicCdSelectedProfile(source,attempt);var mode=SessionManager.getCurrentGameplayMode();var registry=mode.getRewindRegistry();var camera=mode.getCamera();var full=registry.capture();var course=registry.captureCourse();
   var capturedCamera=camera.capture();module.originalR11EntryFrame().close();camera.setX((short)(camera.getX()+37));camera.setY((short)(camera.getY()+19));var marker=camera.capture();assertNotEquals(capturedCamera,marker);
   assertThrows(IllegalStateException.class,()->registry.restore(full));assertEquals(marker,camera.capture(),"rejected full restore must not mutate earlier camera owner");
   assertThrows(IllegalStateException.class,()->registry.restoreCourse(course));assertEquals(marker,camera.capture(),"rejected course restore must not mutate earlier camera owner");
  }
 }
 @Test void publicTtReadyLandscapeDrawsFreshAndReplaysFullAndCourseLoading()throws Exception{
  var output=ttCaptureDirectory();var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var module=attempt.module().orElseThrow();
  try(var boot=boot();var cleanup=disposeTtWhileCurrent()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);step(loop,0);var mode=SessionManager.getCurrentGameplayMode();var registry=mode.getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var frame=module.originalR11EntryFrame();var generation=mode.getLevelManager().getCompletedProductionLoadGeneration();
   ttReady(loop,module);var expected=image(module);var first=ttDrawClosed(module);
   registry.restore(full);ttReady(loop,module);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));assertArrayEquals(first.pixels(),ttDrawClosed(module).pixels());
   registry.restoreCourse(course);ttReady(loop,module);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));assertArrayEquals(first.pixels(),ttDrawClosed(module).pixels());
   assertSame(frame,module.originalR11EntryFrame());assertEquals(generation,mode.getLevelManager().getCompletedProductionLoadGeneration());assertEquals(0,frame.state().motion().routine(),"Ready is before the first ordinary Body");
   if(output!=null){
    if(!java.nio.file.Files.isDirectory(output,java.nio.file.LinkOption.NOFOLLOW_LINKS)||!output.equals(output.toRealPath()))throw new IllegalArgumentException("Validated TT capture directory changed");
    var png=new java.awt.image.BufferedImage(first.width(),first.height(),java.awt.image.BufferedImage.TYPE_INT_ARGB);png.setRGB(0,0,first.width(),first.height(),first.pixels(),0,first.width());
    try(var stream=java.nio.file.Files.newOutputStream(output.resolve("ENGINE-R41A-original-loading-ready.png"),java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE)){assertTrue(javax.imageio.ImageIO.write(png,"png",stream));}
    var pixels=java.nio.ByteBuffer.allocate(first.pixels().length*4);for(int pixel:first.pixels())pixels.putInt(pixel);
    String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(pixels.array()));
    java.nio.file.Files.writeString(output.resolve("ENGINE-R41A-original-loading-ready.txt"),"ENGINE OpenGGF USA R41A; actual selected LOAD_GAME source/engine FULL, native loading to Ready. Constructed existing save ingress.\n320x224; first ordinary Body has NOT executed; no visible Sonic, route, native or regional parity claim.\nFresh two-sentinel draws, complete captured state nonwrite and exact pixels after FULL/course loading replay required.\nargb32_big_endian_sha256="+hash+"\nphase="+module.originalR11LoadingImage().phase()+"; source="+frame.stageSource().stage()+"; VInt="+frame.vIntClock()+"\n",java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE);
   }
  }
 }
 @Test void publicTtThreeOrdinaryRowsDrawFreshAndReplayTheCompleteActualWorld()throws Exception{
  var output=ttCaptureDirectory();var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var module=attempt.module().orElseThrow();
  try(var boot=boot();var cleanup=disposeTtWhileCurrent()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ttReady(loop,module);
   var mode=SessionManager.getCurrentGameplayMode();var registry=mode.getRewindRegistry();
   var full=registry.capture();var course=registry.captureCourse();var frame=module.originalR11EntryFrame();
   var generation=mode.getLevelManager().getCompletedProductionLoadGeneration();
   for(int row=0;row<3;row++)step(loop,0);
   assertEquals(3,frame.environment().globals().levelFrames());assertEquals(2,frame.state().motion().routine());assertFalse(frame.closed());
   assertTrue(module.originalR11WorldImage().orElseThrow().cpu().isPresent());
   assertTrue(module.originalR11FirstLoad().selection().inputs().selectedCpu().isPresent());
   assertTrue(module.originalR11FirstLoad().selection().inputs().selectedCpu().orElseThrow().provenance().stream().anyMatch(piece->piece.slotRef().wholeIndex()==0),"Actual eligible primary must reach the selected CPU sprite list by the third ordinary service");
   var expected=ttOrdinaryImage(module);var first=ttDrawOrdinaryClosed(module);
   var playerPictureMetadata=output==null?"":ttPlayerPictureMetadata(module);
   CdCapturedValueAssertions.assertCapturedValueEquals(expected,ttOrdinaryImage(module)); // Metadata reads must not change the captured World.
   registry.restore(full);for(int row=0;row<3;row++)step(loop,0);
   CdCapturedValueAssertions.assertCapturedValueEquals(expected,ttOrdinaryImage(module));assertArrayEquals(first.pixels(),ttDrawOrdinaryClosed(module).pixels());
   registry.restoreCourse(course);for(int row=0;row<3;row++)step(loop,0);
   CdCapturedValueAssertions.assertCapturedValueEquals(expected,ttOrdinaryImage(module));assertArrayEquals(first.pixels(),ttDrawOrdinaryClosed(module).pixels());
   assertSame(frame,module.originalR11EntryFrame());assertEquals(generation,mode.getLevelManager().getCompletedProductionLoadGeneration());
   if(output!=null){
    if(!java.nio.file.Files.isDirectory(output,java.nio.file.LinkOption.NOFOLLOW_LINKS)||!output.equals(output.toRealPath()))throw new IllegalArgumentException("Validated TT capture directory changed");
    var png=new java.awt.image.BufferedImage(first.width(),first.height(),java.awt.image.BufferedImage.TYPE_INT_ARGB);png.setRGB(0,0,first.width(),first.height(),first.pixels(),0,first.width());
    try(var stream=java.nio.file.Files.newOutputStream(output.resolve("ENGINE-R41A-original-three-ordinary.png"),java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE)){assertTrue(javax.imageio.ImageIO.write(png,"png",stream));}
    var bytes=java.nio.ByteBuffer.allocate(first.pixels().length*4);for(int pixel:first.pixels())bytes.putInt(pixel);
    String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.array()));
    java.nio.file.Files.writeString(output.resolve("ENGINE-R41A-original-three-ordinary.txt"),"ENGINE OpenGGF USA R41A, actual selected LOAD_GAME/FULL/loading then three returned ordinary rows. Constructed existing save ingress; caller inputs only.\nFresh two-sentinel draw nonwrite and exact full-world/byte/pixel replay after FULL/course required. No native, route or regional parity implied. Visible Sonic must be inspected separately.\nargb32_big_endian_sha256="+hash+"\nlevelFrames="+frame.environment().globals().levelFrames()+"; routine="+frame.state().motion().routine()+"; VInt="+frame.vIntClock()+"\n"+playerPictureMetadata,java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE);
   }
  }
 }
 /** Observation only at the exact first three-row picture cut; no player visibility/parity claim. */
 private static String ttPlayerPictureMetadata(SonicCdGameModule module)throws Exception{
  var frame=module.originalR11EntryFrame();var state=frame.state();var selected=module.originalR11FirstLoad().selection().inputs();var live=module.originalR11WorldImage().orElseThrow().cpu();var camera=SessionManager.getCurrentGameplayMode().getCamera();var scroll=module.originalR11LoadingImage().scroll();
  var out=new StringBuilder("player_picture_observation=ENGINE actual three returned rows; raw selected/live partitions; no visible-Sonic or parity claim\n");
  out.append("player_source=").append(frame.stageSource().stage()).append('\n');
  out.append("player_state=").append(state).append('\n');
  out.append("player_header=").append(state.header()).append('\n');
  out.append("player_motion=").append(state.motion()).append('\n');
  out.append("player_animation=").append(state.animation()).append('\n');
  out.append("player_display=").append(frame.display()).append('\n');
  out.append("engine_camera_x=").append(camera.getX()).append("; engine_camera_y=").append(camera.getY()).append('\n');
  out.append("foreground_scroll=").append(scroll.foreground()).append('\n');
  out.append("copied_scroll=").append(scroll.copies()).append('\n');
  out.append("selected_display_enabled=").append(selected.displayEnabled()).append("; selected_vscroll_long_bits=").append(selected.vscrollLongBits()).append('\n');
  out.append("selected_sat_hex=").append(java.util.HexFormat.of().formatHex(selected.selectedSat().copy())).append('\n');
  ttAppendCpuObservation(out,"selected_cpu",selected.selectedCpu());ttAppendCpuObservation(out,"live_cpu",live);
  ttAppendPlayerArtObservation(out,"selected_player_art",selected.player());ttAppendPlayerArtObservation(out,"live_player_art",frame.artState());
  return out.toString();
 }
 private static void ttAppendCpuObservation(StringBuilder out,String label,Optional<com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat> value){
  out.append(label).append("_present=").append(value.isPresent()).append('\n');
  if(value.isEmpty())return;var cpu=value.orElseThrow();
  out.append(label).append("_sprite_count=").append(cpu.spriteCount()).append('\n');
  out.append(label).append("_raw640_hex=").append(java.util.HexFormat.of().formatHex(cpu.bytes())).append('\n');
  out.append(label).append("_provenance=").append(cpu.provenance()).append('\n');
 }
 private static void ttAppendPlayerArtObservation(StringBuilder out,String label,com.openggf.game.soniccd.resources.CdPlayerArtRuntime.State art)throws Exception{
  out.append(label).append("_last_frame=").append(art.lastFrame()).append("; ").append(label).append("_dirty=").append(art.dirty()).append('\n');
  var digest=java.security.MessageDigest.getInstance("SHA-256");
  out.append(label).append("_cpu_sha256=").append(java.util.HexFormat.of().formatHex(digest.digest(art.cpuBuffer().copy()))).append('\n');
  out.append(label).append("_resident_sha256=").append(java.util.HexFormat.of().formatHex(digest.digest(art.residentBank().copy()))).append('\n');
 }
 private static Object ttOrdinaryImage(SonicCdGameModule module){return List.of(image(module),module.originalR11WorldImage());}
 private static com.openggf.graphics.RgbaImage ttDrawOrdinaryClosed(SonicCdGameModule module){var before=ttOrdinaryImage(module);var pixels=ttDrawClosed(module);CdCapturedValueAssertions.assertCapturedValueEquals(before,ttOrdinaryImage(module));return pixels;}
 private static void ttReady(com.openggf.GameLoop loop,SonicCdGameModule module){for(int i=0;i<256&&module.originalR11LoadingPrefix().main().levelStartedByte()==0;i++)step(loop,0);assertEquals(1,module.originalR11LoadingPrefix().main().levelStartedByte());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,module.originalR11LoadingImage().phase());assertTrue(module.originalR11FirstLoad().selection().inputs().displayEnabled());assertFalse(module.originalR11EntryFrame().closed());}
 private static com.openggf.graphics.RgbaImage ttDrawClosed(SonicCdGameModule module){var before=image(module);var a=ttDrawFresh(module,0xFF13C75D);CdCapturedValueAssertions.assertCapturedValueEquals(before,image(module));var b=ttDrawFresh(module,0xFFDC3B91);CdCapturedValueAssertions.assertCapturedValueEquals(before,image(module));assertArrayEquals(a.pixels(),b.pixels());return a;}
 private static com.openggf.graphics.RgbaImage ttDrawFresh(SonicCdGameModule module,int sentinel){
  org.lwjgl.opengl.GL11.glViewport(0,0,320,224);org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);org.lwjgl.opengl.GL11.glColorMask(true,true,true,true);org.lwjgl.opengl.GL11.glClearColor(((sentinel>>>16)&255)/255f,((sentinel>>>8)&255)/255f,(sentinel&255)/255f,1);org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT|org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);org.lwjgl.opengl.GL11.glFinish();
  var cleared=com.openggf.graphics.ScreenshotCapture.captureFramebuffer(320,224);assertTrue(Arrays.stream(cleared.pixels()).allMatch(v->v==sentinel));module.gameplayFrameController().drawOverlay();org.lwjgl.opengl.GL11.glFinish();var pixels=com.openggf.graphics.ScreenshotCapture.captureFramebuffer(320,224);assertEquals(org.lwjgl.opengl.GL11.GL_NO_ERROR,org.lwjgl.opengl.GL11.glGetError());assertEquals(320,pixels.width());assertEquals(224,pixels.height());assertFalse(Arrays.equals(cleared.pixels(),pixels.pixels()));assertTrue(Arrays.stream(pixels.pixels()).map(v->v&0xFFFFFF).distinct().limit(2).count()>1,"Ready must show actual nonblank terrain");return pixels;
 }
 private static java.nio.file.Path ttCaptureDirectory()throws Exception{String raw=System.getenv("SONICCD_TT_ORIGINAL_CAPTURE_DIR");if(raw==null)return null;var path=java.nio.file.Path.of(raw);var allowed=java.nio.file.Path.of(System.getProperty("user.home"), "Downloads", "Sonic-CD", "captures", "engine").toRealPath();if(!path.isAbsolute()||!raw.equals(path.toString())||!path.equals(path.normalize())||!java.nio.file.Files.isDirectory(path,java.nio.file.LinkOption.NOFOLLOW_LINKS)||!path.equals(path.toRealPath())||path.equals(allowed)||!path.startsWith(allowed))throw new IllegalArgumentException("Exact canonical TT engine capture subdirectory required");return path;}
 private static AutoCloseable disposeTtWhileCurrent(){return ()->{Throwable fault=null;try{SessionManager.clear();}catch(RuntimeException|Error first){fault=first;}try{com.openggf.game.GameServices.graphics().cleanup();}catch(RuntimeException|Error next){if(fault==null)fault=next;else if(next!=fault)fault.addSuppressed(next);}if(fault instanceof RuntimeException x)throw x;if(fault instanceof Error x)throw x;};}
 private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();return List.of(f.captureActor(),f.captureShared(),p.status(),p.pool().slots(),p.savedFlags(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),m.originalR11FirstLoad(),m.originalLoadingWaterImage(),m.programServicesState(),m.selectedProfileState());}
}
