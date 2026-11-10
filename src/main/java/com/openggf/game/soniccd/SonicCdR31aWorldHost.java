package com.openggf.game.soniccd;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.GameDataSource;
import com.openggf.game.session.*;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import com.openggf.game.soniccd.scroll.*;
import com.openggf.graphics.*;
import com.openggf.level.*;
import com.openggf.level.scroll.ZoneScrollHandler;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import java.io.IOException;
import java.util.*;
import static com.openggf.game.soniccd.SonicCdR31aLoadingVisit.*;
/** Original-USA R31A loading external owner, no ordinary/boot/World factory.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Initialization.asm56-93;
 * Level/Main.asm19-329,821-847,942-952; Functions (General).asm99-152,182-235;
 * Collision Chaos/Stage Draw (Act 1 Present).asm28-196,226-320.
 * Actual Module Authority owns source/session/epoch/World/thread and publication admission.
 */
final class SonicCdR31aWorldHost implements Host,AutoCloseable {
 interface Authority {
  void require(SonicCdR31aWorldHost host,SonicCdR31aLoadingVisit core,CdPlayerFrameRuntime frame,CdR31aAssets assets,CdR31aSectionArt art);
  void levelMusic(SonicCdR31aWorldHost host,CdCommand actualCommand);
  void resetGoodFutureFlags(SonicCdR31aWorldHost host);
  void serviceVInt(SonicCdR31aWorldHost host);
  void loadingPublication(SonicCdR31aWorldHost host,SonicCdR31aLoadingVisit core,Publication original,CdAssetBytes actualPalette);
 }
 enum Boundary { GEOMETRY_NOT_LOADED,LOAD_PRODUCT,ACTIVE_POWERUP,SELECTED_DISPLAY,CLEAR_SCREEN_WATER_ALIAS,CLEAR_SCREEN_PLAYER_ALIAS,SPAWNER_CAMERA_CLEAR_OWNER }
 static final class HostBoundary extends UnsupportedOperationException {
  private final Boundary boundary;private final Optional<SonicCdPastDisplay.DisplayFailure> displayFailure;
  private HostBoundary(Boundary b){this(b,Optional.empty());}
  private HostBoundary(Boundary b,Optional<SonicCdPastDisplay.DisplayFailure> f){super("R31A loading Host "+b+f.map(Object::toString).orElse(""));boundary=b;displayFailure=f;}
  Boundary boundary(){return boundary;}Optional<SonicCdPastDisplay.DisplayFailure> displayFailure(){return displayFailure;}
 }
    record Tail(int player2ControlsWord, int player2BoredWord, int unknownLevelByte,
                int demoS1IndexWord, int paletteFadeInfoWord) {
        Tail { word(player2ControlsWord); word(player2BoredWord); octet(unknownLevelByte); word(demoS1IndexWord); word(paletteFadeInfoWord); }
    }
    /** Actual MAINVARS carries and reached knowledge, without Core.Program or Frame fields. */
    record Globals(CdRingGlobals ring, CdCheckpointLiveAux.State live, CdPlayerBody.ContactGlobals contact,
                   CdOpeningObjectRuntime.ScoreGlobals score, OptionalInt projectorByte,
                   CdCheckpointSaveData.State checkpointSaved,
                   OptionalInt drownTimerWord, OptionalInt debugBlockWord, OptionalInt pauseMusicBit,
                   Optional<CdLevelHud.BonusValues> bonus, Optional<Tail> tail,
                   Optional<CdGlobalObjectAnimation.Extras> animations, CdAssetBytes paletteSteps7, CdAssetBytes paletteTimers7, OptionalInt layerByte, OptionalInt bossFlagsByte, OptionalInt eventRoutineWord) {
        Globals {
            Objects.requireNonNull(ring); Objects.requireNonNull(live); Objects.requireNonNull(contact); Objects.requireNonNull(score);
            optionalByte(projectorByte); optionalByte(layerByte); optionalByte(bossFlagsByte); Objects.requireNonNull(eventRoutineWord); eventRoutineWord.ifPresent(SonicCdR31aWorldHost::word); Objects.requireNonNull(checkpointSaved);
            Objects.requireNonNull(drownTimerWord); Objects.requireNonNull(debugBlockWord); Objects.requireNonNull(pauseMusicBit);
            Objects.requireNonNull(bonus); Objects.requireNonNull(tail); Objects.requireNonNull(animations);
            extent(paletteSteps7, 7); extent(paletteTimers7, 7);
            drownTimerWord.ifPresent(SonicCdR31aWorldHost::word); debugBlockWord.ifPresent(SonicCdR31aWorldHost::word);
            if (pauseMusicBit.isPresent() && (pauseMusicBit.getAsInt() & ~0x80) != 0) throw new IllegalArgumentException("PauseMusic bit7 only");
        }
        @Override public boolean equals(Object other){return this==other||other instanceof Globals g&&globalsValue(this).equals(globalsValue(g));}
        @Override public int hashCode(){return globalsValue(this).hashCode();}
        private Globals scalars(CdRingGlobals r, CdCheckpointLiveAux.State l) {
            return new Globals(r,l,contact,score,projectorByte,checkpointSaved,drownTimerWord,debugBlockWord,pauseMusicBit,bonus,tail,animations,paletteSteps7,paletteTimers7,layerByte,bossFlagsByte,eventRoutineWord);
        }
        Globals flowers() {
            var r=ring;
            return new Globals(new CdRingGlobals(r.rings(),r.lives(),r.livesFlags(),r.updateHudRings(),r.updateHudLives(),0,0,0),
                    live,contact,score,projectorByte,checkpointSaved,drownTimerWord,OptionalInt.of(0),pauseMusicBit,bonus,tail,
                    Optional.of(new CdGlobalObjectAnimation.Extras(0,0,0,0,new CdRingSpillGlobals(0,0,0))),paletteSteps7,paletteTimers7,OptionalInt.of(0),bossFlagsByte,eventRoutineWord);
        }
        Globals misc() {
            return new Globals(ring,CdCheckpointLiveAux.clearMiscAtLevelStart(live),contact,score,projectorByte,checkpointSaved,
                    drownTimerWord,debugBlockWord,OptionalInt.of(0),bonus,tail,animations,paletteSteps7,paletteTimers7,layerByte,bossFlagsByte,eventRoutineWord);
        }
        Globals camera() {
            return new Globals(ring,live,contact,score,projectorByte,checkpointSaved,drownTimerWord,debugBlockWord,pauseMusicBit,
                    Optional.of(new CdLevelHud.BonusValues(0,0,0)),tail.map(t->new Tail(t.player2ControlsWord(),0,
                            t.unknownLevelByte(),t.demoS1IndexWord(),t.paletteFadeInfoWord())),animations,zero(7),zero(7),layerByte,OptionalInt.of(0),OptionalInt.of(0));
        }
        Globals drown() {
            return new Globals(ring,live,contact,score,projectorByte,checkpointSaved,OptionalInt.of(30),debugBlockWord,pauseMusicBit,bonus,tail,animations,paletteSteps7,paletteTimers7,layerByte,bossFlagsByte,eventRoutineWord);
        }
        /** Same persistent Tail, preserving all other native words. No shadow timer. */
        Globals secondaryBored(int word) {
            var t=tail.orElseThrow(com.openggf.game.soniccd.player.CdPlayerFrameRuntime.UnownedSecondaryBoredTimer::new);
            return new Globals(ring,live,contact,score,projectorByte,checkpointSaved,drownTimerWord,debugBlockWord,
                    pauseMusicBit,bonus,Optional.of(new Tail(t.player2ControlsWord(),word,t.unknownLevelByte(),
                    t.demoS1IndexWord(),t.paletteFadeInfoWord())),animations,paletteSteps7,paletteTimers7,
                    layerByte,bossFlagsByte,eventRoutineWord);
        }
        Globals fadeInfo(int value) {
            var t=tail.orElseThrow();
            return new Globals(ring,live,contact,score,projectorByte,checkpointSaved,drownTimerWord,debugBlockWord,
                    pauseMusicBit,bonus,Optional.of(new Tail(t.player2ControlsWord(),t.player2BoredWord(),
                    t.unknownLevelByte(),t.demoS1IndexWord(),value)),animations,paletteSteps7,paletteTimers7,
                    layerByte,bossFlagsByte,eventRoutineWord);
        }
        Globals beginMain() { return beginMain(0); }
        /** Main.asm:285-290 tests spawn BEFORE Main:301 clears it. Native nonzero
         * checkpoint/warp entry preserves the loaded rings/lives flags/time. */
        Globals beginMain(int preClearSpawn) {
            if ((preClearSpawn & ~255) != 0) throw new IllegalArgumentException("Raw spawn BYTE required");
            var r=ring; boolean fresh=preClearSpawn==0;
            return new Globals(new CdRingGlobals(fresh?0:r.rings(),r.lives(),fresh?0:r.livesFlags(),0x80,1,r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame()),
                    fresh?CdCheckpointLiveAux.clearTimeAtSpawnZero(live):live,new CdPlayerBody.ContactGlobals(0,0,contact.combineRing()),new CdOpeningObjectRuntime.ScoreGlobals(score.score(),score.nextLifeScore(),0x80),
                    projectorByte,checkpointSaved,drownTimerWord,debugBlockWord,pauseMusicBit,bonus,
                    Optional.of(new Tail(0,0,0,0,0x202F)),animations,paletteSteps7,paletteTimers7,layerByte,bossFlagsByte,eventRoutineWord);
        }
        Globals fromWhite() {
            var t=tail.orElseThrow(()->new IllegalStateException("FromWhite requires actual Main tail stores"));
            return new Globals(ring,live,contact,score,projectorByte,checkpointSaved,drownTimerWord,debugBlockWord,pauseMusicBit,bonus,
                    Optional.of(new Tail(t.player2ControlsWord(),t.player2BoredWord(),t.unknownLevelByte(),t.demoS1IndexWord(),0x003F)),
                    animations,paletteSteps7,paletteTimers7,layerByte,bossFlagsByte,eventRoutineWord);
        }
    }


 record Inputs(Optional<CdObjectDrawQueue.CpuSat> selectedCpu,CdAssetBytes selectedSat,CdAssetBytes palette,CdAssetBytes hscroll,
               int vscrollLongBits,boolean displayEnabled,CdR11aBackgroundPlane.State foreground,CdR11aBackgroundPlane.State background,
               CdR31aArtResidency.State current,CdPlayerArtRuntime.State player) implements SonicCdCurrentDisplay.Inputs {
  Inputs{Objects.requireNonNull(selectedCpu);extent(selectedSat,640);extent(palette,128);extent(hscroll,1024);Objects.requireNonNull(foreground);Objects.requireNonNull(background);Objects.requireNonNull(current);Objects.requireNonNull(player);}
  @Override public boolean equals(Object o){return this==o||o instanceof Inputs i&&inputsValue(this).equals(inputsValue(i));}
  @Override public int hashCode(){return inputsValue(this).hashCode();}
 }
 record Selection(Inputs inputs,SonicCdPastDisplay.Projection projection){Selection{Objects.requireNonNull(inputs);Objects.requireNonNull(projection);}}
 sealed interface Selected permits Carried,Published{}
 record Carried(RetainedPlaneSceneRenderer.Scene scene) implements Selected{Carried{Objects.requireNonNull(scene);}}
 record Published(Selection selection) implements Selected{Published{Objects.requireNonNull(selection);}}
 record Video(CdAssetBytes cpuSat,Optional<CdObjectDrawQueue.CpuSat> completedCpu,CdPlayerArtRuntime.State playerArt,
              CdR31aArtResidency.State current,Selected selected){
  Video{extent(cpuSat,640);Objects.requireNonNull(completedCpu);Objects.requireNonNull(playerArt);Objects.requireNonNull(current);Objects.requireNonNull(selected);
   if(completedCpu.isPresent()&&!Arrays.equals(completedCpu.orElseThrow().bytes(),cpuSat.copy()))throw new IllegalArgumentException("CPU SAT/provenance differ");}
 }
 /** Existing actual placement owner, never saved bytes plus a constructor-created native pool. */
 record Planes(CdR11aBackgroundPlane.State foreground,CdR11aBackgroundPlane.State background){Planes{Objects.requireNonNull(foreground);Objects.requireNonNull(background);}}
 /** Retained named native values/knowledge, never a default or generic address-based storage token. */
 record NativeCarry(OptionalLong collisionPointer,OptionalInt fadeInfoWithoutTail,OptionalLong rngSeedLong,OptionalInt targetRightBoundWord,OptionalInt amyCapturedByte){
  NativeCarry(OptionalLong c,OptionalInt f,OptionalLong r,OptionalInt t){this(c,f,r,t,OptionalInt.empty());}
  NativeCarry{optionalByte(amyCapturedByte);Objects.requireNonNull(collisionPointer);Objects.requireNonNull(fadeInfoWithoutTail);Objects.requireNonNull(rngSeedLong);Objects.requireNonNull(targetRightBoundWord);fadeInfoWithoutTail.ifPresent(SonicCdR31aWorldHost::word);targetRightBoundWord.ifPresent(SonicCdR31aWorldHost::word);for(var value:new OptionalLong[]{collisionPointer,rngSeedLong})if(value.isPresent()&&(value.getAsLong()<0||value.getAsLong()>0xFFFFFFFFL))throw new IllegalArgumentException("Native unsigned LONG bits required");}
 }
 record Initial(Globals globals,Scroll scroll,Planes planes,Video video,CdObjectPlacementRuntime placement,NativeCarry nativeCarry){
  Initial{Objects.requireNonNull(globals);Objects.requireNonNull(scroll);Objects.requireNonNull(planes);Objects.requireNonNull(video);Objects.requireNonNull(placement);Objects.requireNonNull(nativeCarry);if(globals.tail().isPresent()&&nativeCarry.fadeInfoWithoutTail().isPresent())throw new IllegalArgumentException("One complete fade-info owner required");}
  CdR11aBackgroundPlane.State foreground(){return planes.foreground();}CdR11aBackgroundPlane.State background(){return planes.background();}
 }
 /** One captured source-specific current/scratch partition; no invocation authority. */
 private record Writers(CdStageTerrain terrain,CdR11bForegroundPlane foreground,CdR11aBackgroundPlane background){
  Writers{Objects.requireNonNull(terrain);Objects.requireNonNull(foreground);Objects.requireNonNull(background);}
 }
 private record OrdinaryImage(CdStageTerrain terrain,OptionalInt noBumper,OptionalInt bossStarted,
                              Optional<CdAssetBytes> shieldCpu,Writers writers,OptionalInt amyCaptured) {
  OrdinaryImage{optionalByte(amyCaptured);Objects.requireNonNull(terrain);optionalByte(noBumper);optionalByte(bossStarted);Objects.requireNonNull(shieldCpu);shieldCpu.ifPresent(b->extent(b,0x480));if(Objects.requireNonNull(writers).terrain()!=terrain)throw new IllegalArgumentException("Current terrain/writer pair differs");}
 }
 private record Image(SonicCdR31aWorldHost owner,Globals globals,Scroll scroll,CdR11aBackgroundPlane.State foreground,
                      CdR11aBackgroundPlane.State background,Video video,Set<Action> clears,boolean geometryReady,
                      OptionalLong collisionPointer,OptionalInt fadeInfoWithoutTail,OptionalLong rngSeedLong,
                      OptionalInt targetRightBoundWord,List<SonicCdGameModule.PastSound> sounds,OrdinaryImage ordinary){
  Image{Objects.requireNonNull(ordinary);clears=Set.copyOf(clears);Objects.requireNonNull(collisionPointer);Objects.requireNonNull(fadeInfoWithoutTail);Objects.requireNonNull(rngSeedLong);Objects.requireNonNull(targetRightBoundWord);targetRightBoundWord.ifPresent(SonicCdR31aWorldHost::word);fadeInfoWithoutTail.ifPresent(SonicCdR31aWorldHost::word);if(globals.tail().isPresent()&&fadeInfoWithoutTail.isPresent())throw new IllegalArgumentException("One palette fade-info owner required");if(rngSeedLong.isPresent()&&(rngSeedLong.getAsLong()<0||rngSeedLong.getAsLong()>0xFFFFFFFFL))throw new IllegalArgumentException("Native RNG LONG");sounds=List.copyOf(sounds);}
 }
 private static final class Snapshot {
  private final Image image;private final CdObjectPlacementRuntime.Snapshot placement;
  private Snapshot(Image image,CdObjectPlacementRuntime.Snapshot placement){this.image=image;this.placement=placement;}
  @Override public boolean equals(Object other){return this==other||other instanceof Snapshot s&&imageValue(image).equals(imageValue(s.image))&&samePlacement(placement,s.placement);}
  @Override public int hashCode(){return 31*imageValue(image).hashCode()+placement.status().hashCode()+placement.pool().slots().hashCode()+Arrays.hashCode(placement.savedFlags().copy());}
 }
 private record ScrollBinding(SonicCdR31aWorldHost owner,SonicCdR31aLoadingVisit visit){}
 private record RenderBinding(GameplayModeContext mode,WorldSession world,GameDataSource data,LevelManager levels,Level level,long generation,
                              Camera camera,GraphicsManager graphics,PatternAtlas atlas,GLCapabilities capabilities,Thread thread,String backend,RetainedPlaneSceneRenderer renderer){}
 private final Authority authority;private final CdR31aAssets assets;private final CdPlayerFrameRuntime frame;private final CdR31aSectionArt art;
 private final CdObjectPlacementRuntime placement;private final CdR31aLevelEvents events;private final CdStageTerrain terrain;
 private Writers runtimeBlockWriters;
 private Writers runtimeBlockSource;
 private com.openggf.game.soniccd.resources.CdBlockDefinitionStaging runtimeBlockBank;
 private Writers runtimeWriters(){
  var source=image.ordinary().writers();var bank=owner.state().blockDefinitions();
  if(runtimeBlockWriters==null||runtimeBlockSource!=source||runtimeBlockBank!=bank){
   runtimeBlockWriters=new Writers(source.terrain(),source.foreground().withBlockDefinitions(bank),source.background().withBlockDefinitions(bank));
   runtimeBlockSource=source;runtimeBlockBank=bank;
  }
  return runtimeBlockWriters;
 }
 private Writers writers(CdStageTerrain current){
  try{return new Writers(current,CdR11bForegroundPlane.fromTables(current.blocks(),current.chunks(),current.layout().ram()),CdR11aBackgroundPlane.bindR31a(assets,current));}
  catch(IOException fault){throw new java.io.UncheckedIOException("Cannot prepare current R31 terrain writers",fault);}
 }
 private final ZoneScrollHandler handler=new Projection();private final Thread thread=Thread.currentThread();
 private CdWorld ordinary;
 private SonicCdR31aLoadingVisit owner;private Image image;private RenderBinding render;
 private boolean acting,publishing,eventing,storing,readingCpu,checkingAuthority,drawing,closing,closed;
 private Publication activePublication;private CdAssetBytes activePalette;
 SonicCdR31aWorldHost(Authority authority,CdR31aAssets assets,CdPlayerFrameRuntime frame,CdR31aSectionArt art,Initial initial)throws IOException{
  this.authority=Objects.requireNonNull(authority);this.assets=Objects.requireNonNull(assets);this.frame=Objects.requireNonNull(frame);this.art=Objects.requireNonNull(art);Objects.requireNonNull(initial);
  if(!frame.stageSource().ownsR31aAssets(assets)||!art.ownsAssets(assets)||!initial.video().current().ownsAssets(assets)||!initial.video().current().hasNativeHudProfile()||!initial.placement().ownsR31aAssets(assets))throw new IllegalArgumentException("Exact original R31 Frame/art/native-HUD/placement relation required");
  if(initial.video().playerArt()!=frame.artState())throw new IllegalArgumentException("Ingress must borrow the exact current Frame player image");
  placement=initial.placement();var p=placement.capture();if(p.pool().slots().size()!=96||p.savedFlags().length()!=766)throw new IllegalArgumentException("Complete original placement pool/bank required");
  events=CdR31aLevelEvents.fromAssets(assets);terrain=assets.terrain();
  var carry=initial.nativeCarry();image=new Image(this,initial.globals(),initial.scroll(),initial.foreground(),initial.background(),initial.video(),Set.of(),false,carry.collisionPointer(),carry.fadeInfoWithoutTail(),carry.rngSeedLong(),carry.targetRightBoundWord(),List.of(),new OrdinaryImage(terrain,OptionalInt.empty(),OptionalInt.empty(),Optional.empty(),writers(terrain),carry.amyCapturedByte()));
 }
  private void bindFinishTimers(){
   frame.bindFinishTimersOwner(new CdPlayerFrameRuntime.FinishTimersOwner(){
    private void require(CdPlayerFrameRuntime.FinishTimerCall call,boolean writing){
     call.requireCurrent(frame);current();owner.requireFinishTimers(SonicCdR31aWorldHost.this,frame);
     if(call.writing()!=writing)throw new IllegalArgumentException("Wrong original Finish timer site");
    }
    public int readSecondary(CdPlayerFrameRuntime.FinishTimerCall call){
     require(call,false);return image.globals().tail().orElseThrow(CdPlayerFrameRuntime.UnownedSecondaryBoredTimer::new).player2BoredWord();
    }
    public void writeSecondary(CdPlayerFrameRuntime.FinishTimerCall call,int word){
     require(call,true);var ready=globalsImage(image.globals().secondaryBored(word));require(call,true);image=ready;
    }
   });
  }
 void install(SonicCdR31aLoadingVisit core){idle();if(owner!=null||Objects.requireNonNull(core).assets()!=assets||core.state().phase()!=Phase.START||core.state().section().residency()!=image.video().current())throw new IllegalArgumentException("One actual Core/Start/current relation");var selected=image.video().selected();if(selectedEnabled(selected)!=core.state().displayEnabled()||!Arrays.equals(selectedPalette(selected),core.state().displayedPalette().copy()))throw new IllegalArgumentException("Initial physical selected display differs from actual Core entry");requireAuthority(core);owner=core;bindFinishTimers();}
 void requireRenderIdle(){if(drawing)throw new IllegalStateException("R31A retained draw active");}
 private void idle(){requireRenderIdle();if(Thread.currentThread()!=thread||closed||closing)throw new IllegalStateException("R31A Host retired or another thread");}
 private void requireAuthority(SonicCdR31aLoadingVisit core){
  if(checkingAuthority)throw new IllegalStateException("Reentrant Host authority");
  // install validates before owner assignment, so fence the actual supplied Core as well.
  var before=externalCut(core);checkingAuthority=true;
  try{try{authority.require(this,core,frame,assets,art);}catch(RuntimeException|Error original){try{unchanged(before,core);}catch(RuntimeException|Error violation){if(violation!=original)original.addSuppressed(violation);}throw original;}unchanged(before,core);}
  finally{checkingAuthority=false;}
 }
 private record ExternalCut(Image image,State core,SonicCdR31aLoadingVisit installedOwner,boolean closed,boolean closing,CdPlayerBody.State actor,CdPlayerBody.Environment environment,
                            CdPlayerArtRuntime.State player,Optional<CdPlayerBody.DisplayPublication> display,
                            CdPlayerFrameRuntime.VIntClock clock,boolean paused,CdObjectPlacementRuntime.Snapshot placement){}
 private ExternalCut externalCut(){return externalCut(owner);}
 private ExternalCut externalCut(SonicCdR31aLoadingVisit core){return new ExternalCut(image,core.state(),owner,closed,closing,frame.state(),frame.environment(),frame.artState(),frame.display(),frame.vIntClock(),frame.paused(),placement.capture());}
 private void unchanged(ExternalCut before){unchanged(before,owner);}
 private void unchanged(ExternalCut before,SonicCdR31aLoadingVisit core){if(frame.closed()||closed!=before.closed()||closing!=before.closing()||owner!=before.installedOwner()||image!=before.image()||core.state()!=before.core()||frame.state()!=before.actor()||frame.environment()!=before.environment()||frame.artState()!=before.player()||frame.display()!=before.display()||frame.vIntClock()!=before.clock()||frame.paused()!=before.paused()||!samePlacement(before.placement(),placement.capture()))throw new IllegalStateException("Authority callback changed complete Host/Core/Frame/pool cut");}
 /** Root may change its actual private Module owner only, never these borrowed native owners. */
 private void external(Runnable callback){var before=externalCut();try{callback.run();}catch(RuntimeException|Error original){try{unchanged(before);}catch(RuntimeException|Error violation){if(violation!=original)original.addSuppressed(violation);}throw original;}unchanged(before);}
 private void bound(){if(owner==null)throw new IllegalStateException("R31A Host not installed");requireAuthority(owner);if(frame.closed()||!frame.stageSource().ownsR31aAssets(assets)||!placement.ownsR31aAssets(assets))throw new IllegalStateException("R31A Frame/source retired");var gates=frame.environment().movement().gates();if(gates.zoneAct()!=0x100||gates.timeZone()!=1)throw new IllegalStateException("R31A loading raw source/gates changed");}
 private void current(){idle();bound();}
 private void operationIdle(){if(acting||publishing||eventing||storing||readingCpu)throw new IllegalStateException("R31A original loading operation active");}
 @Override public void validate(SonicCdR31aLoadingVisit core,CdPlayerFrameRuntime f,CdR31aAssets a,CdR31aSectionArt section){current();if(core!=owner||f!=frame||a!=assets||section!=art)throw new IllegalArgumentException("Foreign Core/Frame/assets/art");}
 Globals globals(){current();return image.globals();}CdObjectPlacementRuntime placement(){current();return placement;}
 @Override public Scroll scroll(){current();return image.scroll();}Video video(){current();return image.video();}Selected selected(){current();return image.video().selected();}
 CdR11aBackgroundPlane.State foreground(){current();return image.foreground();}CdR11aBackgroundPlane.State background(){current();return image.background();}
 boolean geometryReady(){current();return image.geometryReady();}OptionalLong collisionPointer(){current();return image.collisionPointer();}
 OptionalLong rngSeedLong(){current();return image.rngSeedLong();}OptionalInt targetRightBoundWord(){current();return image.targetRightBoundWord();}
 OptionalInt paletteFadeInfoWord(){current();return image.globals().tail().isPresent()?OptionalInt.of(image.globals().tail().orElseThrow().paletteFadeInfoWord()):image.fadeInfoWithoutTail();}
 Set<Action> clears(){current();return image.clears();}List<SonicCdGameModule.PastSound> sounds(){current();return image.sounds();}
 ZoneScrollHandler handler(){current();return handler;}
 @Override public int livesByte(){current();return image.globals().ring().lives();}
 @Override public Object capture(){current();operationIdle();return new Snapshot(image,placement.capture());}
 @Override public void validateRestore(Object value){current();if(ordinary!=null&&ordinary.isOrdinaryRunning())throw new IllegalStateException("World service active");operationIdle();if(!(value instanceof Snapshot s)||s.image.owner()!=this)throw new IllegalArgumentException("Foreign Host snapshot");placement.validateRestore(s.placement);if(!s.image.video().current().ownsAssets(assets))throw new IllegalArgumentException("Foreign captured source");}
 @Override public void restore(Object value){validateRestore(value);var s=(Snapshot)value;placement.restore(s.placement);image=s.image;}
 private Image copy(Globals g,Scroll scroll,CdR11aBackgroundPlane.State fg,CdR11aBackgroundPlane.State bg,Video v,List<SonicCdGameModule.PastSound> sounds){return new Image(this,g,scroll,fg,bg,v,image.clears(),image.geometryReady(),image.collisionPointer(),g.tail().isPresent()?OptionalInt.empty():image.fadeInfoWithoutTail(),image.rngSeedLong(),image.targetRightBoundWord(),sounds,image.ordinary());}
 private static Set<Action> completed(Image i,Action action){var set=EnumSet.noneOf(Action.class);set.addAll(i.clears());set.add(action);return Set.copyOf(set);}
 private Image receipt(Image i,Action action){return new Image(this,i.globals(),i.scroll(),i.foreground(),i.background(),i.video(),completed(i,action),i.geometryReady(),i.collisionPointer(),i.fadeInfoWithoutTail(),i.rngSeedLong(),i.targetRightBoundWord(),i.sounds(),i.ordinary());}
 private Image globalsImage(Globals globals){return copy(globals,image.scroll(),image.foreground(),image.background(),image.video(),image.sounds());}
 private Image videoImage(Video video){return copy(image.globals(),image.scroll(),image.foreground(),image.background(),video,image.sounds());}
 private Image scrollImage(Scroll scroll){return copy(image.globals(),scroll,image.foreground(),image.background(),image.video(),image.sounds());}
 private Image fadeInfo(int value){var g=image.globals();if(g.tail().isEmpty())return new Image(this,g,image.scroll(),image.foreground(),image.background(),image.video(),image.clears(),image.geometryReady(),image.collisionPointer(),OptionalInt.of(value),image.rngSeedLong(),image.targetRightBoundWord(),image.sounds(),image.ordinary());return globalsImage(g.fadeInfo(value));}

 // Common World is the sole lifetime/admission coordinator. Host retains one captured native image.
 /** Named immutable early writer products; these data values confer no entry or invocation. */
 void retainOriginalMainClears(Authority expected,CdOriginalBootPrefix.MainClears clears){
  current();operationIdle();if(expected!=authority||ordinary!=null||owner.state().phase()!=Phase.START)throw new IllegalStateException("Original pre-Start clear projection only");
  var c=Objects.requireNonNull(clears);var old=image.ordinary();
  var ready=withOrdinary(image,new OrdinaryImage(old.terrain(),OptionalInt.of(c.noBumperByte()),old.bossStarted(),Optional.of(c.shieldCpu()),old.writers(),old.amyCaptured()));
  image=ready;
 }
 void bindOrdinaryWorld(Authority expected,CdWorld world){current();operationIdle();if(expected!=authority||ordinary!=null||owner.state().phase()!=Phase.START)throw new IllegalStateException("Bind one original World before Start");ordinary=Objects.requireNonNull(world);}
 void requireOrdinaryWorld(Authority expected,CdWorld world){current();if(expected!=authority||world==null||ordinary!=world)throw new IllegalArgumentException("Foreign actual World/authority");}
 private void ordinaryCut(CdOrdinaryLoop.Cut cut){current();if(ordinary==null)throw new IllegalStateException("No original World");ordinary.requireCut(cut);}
 CdStageTerrain currentTerrain(){current();return image.ordinary().terrain();}
 OptionalInt noBumperByte(){current();return image.ordinary().noBumper();}
 OptionalInt amyCapturedByte(){current();return image.ordinary().amyCaptured();}
 PreparedOrdinary prepareAmyCaptured(PreparedOrdinary p,OptionalInt value){validateOrdinary(p);optionalByte(value);var o=p.ready.ordinary();return new PreparedOrdinary(this,p.scope,p.before,withOrdinary(p.ready,new OrdinaryImage(o.terrain(),o.noBumper(),o.bossStarted(),o.shieldCpu(),o.writers(),value)));}
 OptionalInt bossStartedByte(){current();return image.ordinary().bossStarted();}
 Optional<CdAssetBytes> shieldCpu(){current();return image.ordinary().shieldCpu();}
 private Image withOrdinary(Image i,OrdinaryImage o){return new Image(this,i.globals(),i.scroll(),i.foreground(),i.background(),i.video(),i.clears(),i.geometryReady(),i.collisionPointer(),i.fadeInfoWithoutTail(),i.rngSeedLong(),i.targetRightBoundWord(),i.sounds(),o);}
 private static Object ordinaryValue(OrdinaryImage o){return List.of(o.terrain(),o.noBumper(),o.bossStarted(),o.shieldCpu().map(SonicCdR31aWorldHost::bytes),o.amyCaptured());}
 static final class PreparedOrdinary {
  private final SonicCdR31aWorldHost owner;private final Object scope;private final ExternalCut before;private final Image ready;
  private PreparedOrdinary(SonicCdR31aWorldHost owner,Object scope,ExternalCut before,Image ready){this.owner=owner;this.scope=scope;this.before=before;this.ready=ready;}
 }
 PreparedOrdinary prepareOrdinary(Globals expected,Video video,Globals ready,List<SonicCdGameModule.PastSound> suffix,Optional<CdObjectDrawQueue.CpuSat> cpu){
  current();if(ordinary==null||!ordinary.isOrdinaryRunning()||expected!=image.globals()||video!=image.video())throw new IllegalArgumentException("Stale complete Host pair");
  var before=externalCut();var sounds=new ArrayList<>(image.sounds());sounds.addAll(List.copyOf(suffix));
  var v=new Video(cpu.map(c->CdAssetBytes.copyOf(c.bytes())).orElse(video.cpuSat()),cpu,frame.artState(),owner.state().section().residency(),video.selected());
  var next=copy(Objects.requireNonNull(ready),image.scroll(),image.foreground(),image.background(),refreshed(v),sounds);
  return new PreparedOrdinary(this,ordinary.actualScope(),before,next);
 }
 PreparedOrdinary prepareScrollLock(PreparedOrdinary p,int lock){
  validateOrdinary(p);word(lock);var i=p.ready;var s=i.scroll();var f=s.foreground();
  var fg=new CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift(),lock);
  var scroll=new Scroll(fg,s.background(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord());
  var ready=new Image(this,i.globals(),scroll,i.foreground(),i.background(),i.video(),i.clears(),i.geometryReady(),i.collisionPointer(),i.fadeInfoWithoutTail(),i.rngSeedLong(),i.targetRightBoundWord(),i.sounds(),i.ordinary());
  return new PreparedOrdinary(this,p.scope,p.before,ready);
 }
 /** Native Level_MainLoop:413-415, only the current original completed-walk scope. */
 PreparedOrdinary prepareLevelMainDeathCamera(int cameraY){
  ordinaryCut(CdOrdinaryLoop.Cut.POST_OBJECT_CAMERA);var core=owner.state();var e=frame.environment();
  if(core.program().demoDataIndexWord()==0x800||core.level().levelRestart()!=0||frame.paused()||e.globals().debugMode()!=0||frame.state().motion().routine()<6)throw new IllegalStateException("Native death-camera branch not reached");
  var before=externalCut();var i=image;var s=i.scroll();var f=CdPrimaryLifecycle.freezeDestination(s.foreground(),cameraY);
  var next=f==s.foreground()?s:new Scroll(f,s.background(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord());
  return new PreparedOrdinary(this,ordinary.actualScope(),before,copy(i.globals(),next,i.foreground(),i.background(),i.video(),i.sounds()));
 }
 void validatePrimaryDeathPublication(PreparedOrdinary p){validateOrdinary(p);if(image!=p.before.image()||!ordinary.isCurrentScope(p.scope))throw new IllegalStateException("Changed original death publication pair");}
 PreparedOrdinary preparePrimary(SonicCdR31aObjectPass.PrimarySubmission original,Globals globals,List<SonicCdGameModule.PastSound> sounds){
  ordinaryCut(CdOrdinaryLoop.Cut.RUN_OBJECTS);ordinary.requirePrimary(original);var values=original.publication().nativeValues();
  // Final Body data cannot replay a chunk/plane store; only genuine reached receipts publish it.
  if(values.terrain()!=image.ordinary().terrain())throw new IllegalArgumentException("Final Body changed terrain outside original PlaceBlock prefixes");
  var p=prepareOrdinary(image.globals(),image.video(),globals,sounds,original.next().completedCpu());var old=p.ready.ordinary();
  return new PreparedOrdinary(this,p.scope,p.before,withOrdinary(p.ready,new OrdinaryImage(old.terrain(),values.noBumperByte(),old.bossStarted(),old.shieldCpu(),old.writers(),old.amyCaptured())));
 }
 /** Only called inside this World's original physical pass and private Frame helper receipt. */
 PreparedOrdinary preparePlaceBlock(CdPlayerFrameRuntime.R31PlaceBlockCall original,Globals globals,List<SonicCdGameModule.PastSound> sounds){
  ordinaryCut(CdOrdinaryLoop.Cut.RUN_OBJECTS);ordinary.requirePlace(original);
  var before=original.before().nativeValues();var values=original.prefix().nativeValues();var old=image.ordinary();
  if(before.terrain()!=old.terrain()||!before.noBumperByte().equals(old.noBumper()))throw new IllegalArgumentException("Stale current terrain/scratch prefix");
  var p=prepareOrdinary(image.globals(),image.video(),globals,sounds,image.video().completedCpu());
  var fg=p.ready.foreground();var readyWriters=old.writers();
  if(original.store() instanceof CdPlayerBody.R31ChunkWord word){
   if(before.terrain().chunks().unsignedWord(word.byteOffset())!=word.previousWord())throw new IllegalArgumentException("Changed original chunk WORD");
   byte[] expected=before.terrain().chunks().copy();expected[word.byteOffset()]=0;expected[word.byteOffset()+1]=0;
   if(!Arrays.equals(expected,values.terrain().chunks().copy())||!Arrays.equals(before.terrain().layout().ram().copy(),values.terrain().layout().ram().copy()))throw new IllegalArgumentException("PlaceBlock changed another chunk/layout byte");
   readyWriters=writers(values.terrain()); // authentic materials/full tables checked before adoption
  }else{
   if(values.terrain()!=before.terrain())throw new IllegalArgumentException("Plane store changed current terrain");
   var word=(CdPlayerBody.R31PlaneAWord)original.store();byte[] bytes=fg.nameTable().copy();int offset=(word.row()*64+word.column())*2;
   bytes[offset]=(byte)(word.word()>>>8);bytes[offset+1]=(byte)word.word();fg=new CdR11aBackgroundPlane.State(CdAssetBytes.copyOf(bytes));
  }
  var i=p.ready;var nativeImage=new OrdinaryImage(values.terrain(),values.noBumperByte(),old.bossStarted(),old.shieldCpu(),readyWriters,old.amyCaptured());
  // Keep OLD selected CPU/palette/scroll/art. Only the genuinely written physical word changes.
  var video=refreshed(i.video(),fg,i.background());
  var ready=new Image(this,i.globals(),i.scroll(),fg,i.background(),video,i.clears(),i.geometryReady(),i.collisionPointer(),i.fadeInfoWithoutTail(),i.rngSeedLong(),i.targetRightBoundWord(),i.sounds(),nativeImage);
  return new PreparedOrdinary(this,p.scope,p.before,ready);
 }
 /** Screen is verification only: no projection/source lookup or prepared native write. */
 PreparedOrdinary preparePlaceScreenFence(CdPlayerFrameRuntime.R31PlaceBlockScreenCall original){
  ordinaryCut(CdOrdinaryLoop.Cut.RUN_OBJECTS);ordinary.requirePlace(original);
  return new PreparedOrdinary(this,ordinary.actualScope(),externalCut(),image);
 }
 void validateOrdinary(PreparedOrdinary p){current();if(p==null||p.owner!=this||ordinary==null||!ordinary.isCurrentScope(p.scope))throw new IllegalArgumentException("Expired/foreign prepared pair");unchanged(p.before);}
 void adoptAfterNative(PreparedOrdinary p){if(p.owner!=this||image!=p.before.image())throw new IllegalStateException("Prevalidated Host pair expired");image=p.ready;}
 Optional<CdObjectDrawQueue.CpuSat> beforeSelector8(){ordinaryCut(CdOrdinaryLoop.Cut.BEFORE_SELECTOR8);image=videoImage(withSelectedVscroll(image.video(),image.scroll().background().vscroll()));return image.video().completedCpu();}
 void publishOrdinary(CdAssetBytes palette,Optional<CdObjectDrawQueue.CpuSat> selected){
  ordinaryCut(CdOrdinaryLoop.Cut.PUBLISH_LEVEL);var v=image.video();
  if(selected!=v.completedCpu()||palette!=owner.state().normalPalette())throw new IllegalArgumentException("Exact OLD CPU and normal palette required");
  var i=new Inputs(selected,v.cpuSat(),palette,selectedHscroll(image.scroll().background().hscrollRam()),image.scroll().background().vscroll(),owner.state().displayEnabled(),image.foreground(),image.background(),owner.state().section().residency(),frame.artState());
  image=videoImage(new Video(v.cpuSat(),v.completedCpu(),i.player(),i.current(),new Published(project(i))));
 }
 void settleOrdinary(Optional<CdObjectDrawQueue.CpuSat> selected){
  ordinaryCut(CdOrdinaryLoop.Cut.SETTLE_SELECTED_CPU);var v=image.video();
  if(!(v.selected() instanceof Published p)||p.selection().inputs().selectedCpu()!=selected)throw new IllegalArgumentException("Exact borrowed OLD CPU required");
  var i=p.selection().inputs();var n=new Inputs(selected,i.selectedSat(),i.palette(),i.hscroll(),i.vscrollLongBits(),i.displayEnabled(),image.foreground(),image.background(),owner.state().section().residency(),frame.artState());
  image=videoImage(new Video(v.cpuSat(),v.completedCpu(),n.player(),n.current(),new Published(project(n))));
 }

 @Override public void storeScroll(Scroll before,Scroll ready){current();operationIdle();owner.requireScrollStore(this,before,ready);if(image.scroll()!=before)throw new IllegalStateException("Stale complete scroll");storing=true;try{var next=scrollImage(Objects.requireNonNull(ready));image=next;}finally{storing=false;}}
 @Override public CdAssetBytes cpuSat(State expected){current();operationIdle();owner.requireCpuSat(this,expected);readingCpu=true;try{return image.video().cpuSat();}finally{readingCpu=false;}}
 @Override public void events(State state){
  current();operationIdle();owner.requireEvents(this,state);eventing=true;
  try{var before=image;var actor=frame.state();var environment=frame.environment();var pool=placement.capture();
   var result=events.run(before.scroll().foreground(),environment.movement().bounds(),new CdR31aLevelEvents.Player(actor.motion().animation(),actor.motion().flags()),new CdR31aLevelEvents.Gates(environment.movement().gates().zoneAct()));
   var s=before.scroll();var next=copy(before.globals(),new Scroll(result.foreground(),s.background(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()),before.foreground(),before.background(),before.video(),before.sounds());
   CdPlayerFrameRuntime.ObjectSolidPublication publication=null;var bounds=environment.movement().bounds();
   if(!result.bounds().equals(bounds)){if(result.bounds().left()!=bounds.left()||result.bounds().right()!=bounds.right()||result.bounds().bossFight()!=bounds.bossFight())throw new IllegalStateException("Unowned event field");publication=frame.prepareObjectBossScalar(CdR13Boss.Field.BOTTOM_BOUND,CdR13Boss.StoreForm.ASSIGN,bounds.bottom(),result.bounds().bottom());}
   owner.requireEvents(this,state);if(image!=before||frame.state()!=actor||frame.environment()!=environment||!samePlacement(pool,placement.capture()))throw new IllegalStateException("Stale complete event cut");if(publication!=null)frame.validatePreparedObjectSolidPublication(publication);
   if(publication!=null)frame.applyPreparedObjectSolidPublication(publication);image=next;
  }finally{eventing=false;}
 }
 @Override public void clear(SonicCdR31aLoadingVisit.NativeClearCall original){
  current();operationIdle();original.requireOwner(this);
  if(!new Snapshot(image,placement.capture()).equals(original.externalBefore()))throw new IllegalStateException("Changed full native-clear Host/pool cut");
  var before=externalCut();var action=original.action();var o=image.ordinary();Image ready;CdObjectPool.Snapshot poolReady=null;
  switch(action){
   case CLEAR_FLOWERS -> {var g=image.globals().flowers();ready=copy(g,original.scroll(),image.foreground(),image.background(),image.video(),image.sounds());ready=withOrdinary(ready,new OrdinaryImage(o.terrain(),OptionalInt.of(0),o.bossStarted(),Optional.of(zero(0x480)),o.writers(),o.amyCaptured()));}
   case CLEAR_OBJECTS -> {if(!allZero(original.ready().reserved()))throw new HostBoundary(Boundary.LOAD_PRODUCT);var staging=CdObjectPool.stagingCopy(before.placement().pool());staging.clearDynamicSlots();poolReady=staging.capture();placement.pool().validateRestore(poolReady);ready=image;}
   case CLEAR_CAMERA -> {ready=copy(image.globals().camera(),original.scroll(),image.foreground(),image.background(),image.video(),image.sounds());ready=new Image(this,ready.globals(),ready.scroll(),ready.foreground(),ready.background(),ready.video(),ready.clears(),ready.geometryReady(),ready.collisionPointer(),ready.fadeInfoWithoutTail(),ready.rngSeedLong(),OptionalInt.of(0),ready.sounds(),ready.ordinary());ready=withOrdinary(ready,new OrdinaryImage(o.terrain(),o.noBumper(),OptionalInt.of(0),o.shieldCpu(),o.writers(),o.amyCaptured()));}
   default -> throw new IllegalArgumentException("Not a named native loop");
  }
  ready=receipt(ready,action);original.requireOwner(this);unchanged(before);
  acting=true;try{original.applyNative();if(poolReady!=null)placement.pool().restore(poolReady);if(action==Action.CLEAR_CAMERA)placement.clearLevelStartCameraValues();image=ready;}finally{acting=false;}
 }
 @Override public void act(Action action,State state){
  current();operationIdle();owner.requireAction(this,action,state);acting=true;
  try{switch(action){
   // Actual Core/Frame already own these exact stores; receipts are comparison-only.
   case VERSION_CACHE,INIT_MARKER,INIT_CONTROLLERS,GAME_MODE,ENTRY_STORES,PRELUDE_END,NORMAL_LOAD_PALETTE,CLEAR_POWERUP_FLOWERS,CLEAR_DEMO_INDEX,SETUP_CAMERA_AND_SCROLL,LEVEL_SCROLL,FORCE_LEFT_COLUMN,RESERVED_HUD,RESERVED_TIME_ICON,BEGIN_LEVEL -> image=receipt(image,action);
   // Opaque source buffers have no independent gameplay consumer here; no virtual RAM is invented.
   case START_SCRATCH_CLEAR,CLEAR_DRAW_QUEUE,CLEAR_UNKNOWN_BUFFER -> image=receipt(image,action);
   case INIT_VDP -> image=receipt(videoImage(selectedDisplay(image.video(),false)),action);
   case CLEAR_DISPLAY_PALETTE -> image=receipt(videoImage(withSelectedPalette(image.video(),state.displayedPalette())),action);
   case CLEAR_SCROLL_OUTPUT -> image=receipt(videoImage(withSelectedVscroll(image.video(),image.scroll().background().vscroll())),action);
   case INITIAL_FADE_SETUP -> image=receipt(fadeInfo(0x003F),action);
   case FRESH_RESET_PREFIX -> {var g=image.globals();image=receipt(globalsImage(with(g,g.ring(),g.live(),g.contact(),new CdOpeningObjectRuntime.ScoreGlobals(g.score().score(),5000,g.score().updateHudScore()),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7())),action);}
   case RESET_SAVED_OBJECT_FLAGS -> {var ready=receipt(image,action);placement.resetSavedObjectFlags();image=ready;}
   case FRESH_RESET_SUFFIX -> {var g=image.globals();var r=g.ring();var next=with(g,new CdRingGlobals(r.rings(),frame.environment().globals().timeAttackMode()==0?3:1,r.livesFlags(),r.updateHudRings(),r.updateHudLives(),r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame()),g.live(),g.contact(),new CdOpeningObjectRuntime.ScoreGlobals(0,g.score().nextLifeScore(),g.score().updateHudScore()),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7());var ready=receipt(globalsImage(next),action);external(()->authority.resetGoodFutureFlags(this));image=ready;}
   case PLAY_LEVEL_MUSIC -> {var command=state.music().orElseThrow(()->new IllegalStateException("No original R31 programme command"));var sounds=new ArrayList<>(image.sounds());sounds.add(new SonicCdGameModule.PastLevelMusic(command));var ready=receipt(copy(image.globals(),image.scroll(),image.foreground(),image.background(),image.video(),sounds),action);external(()->authority.levelMusic(this,command));image=ready;}
   case CLEAR_FLOWERS,CLEAR_OBJECTS,CLEAR_CAMERA -> throw new IllegalStateException("Native loop requires original coupled clear call");
   case CLEAR_MISC -> {var ready=globalsImage(image.globals().misc());image=receipt(new Image(this,ready.globals(),ready.scroll(),ready.foreground(),ready.background(),ready.video(),ready.clears(),ready.geometryReady(),ready.collisionPointer(),ready.fadeInfoWithoutTail(),OptionalLong.of(0),ready.targetRightBoundWord(),ready.sounds(),ready.ordinary()),action);}

   case CLEAR_SCREEN -> {clearScreen(state);image=receipt(image,action);}
   case LEVEL_SIZE -> image=receipt(new Image(this,image.globals(),image.scroll(),image.foreground(),image.background(),image.video(),image.clears(),image.geometryReady(),image.collisionPointer(),image.fadeInfoWithoutTail(),image.rngSeedLong(),OptionalInt.of(assets.bounds().right()),image.sounds(),image.ordinary()),action);
   case DROWN_TIMER -> image=receipt(globalsImage(image.globals().drown()),action);
   case LEVEL_LAYOUT -> {if(state.blocksRam().isEmpty()||state.layoutRam().isEmpty()||!Arrays.equals(state.blocksRam().orElseThrow().copy(),terrain.blocks().copy())||!Arrays.equals(state.layoutRam().orElseThrow().copy(),terrain.layout().ram().copy()))throw new HostBoundary(Boundary.LOAD_PRODUCT);image=receipt(new Image(this,image.globals(),image.scroll(),image.foreground(),image.background(),image.video(),image.clears(),true,image.collisionPointer(),image.fadeInfoWithoutTail(),image.rngSeedLong(),image.targetRightBoundWord(),image.sounds(),image.ordinary()),action);}
   case INITIAL_FOREGROUND -> {initialForeground(state);image=receipt(image,action);}
   case INITIAL_BACKGROUND -> {initialBackground();image=receipt(image,action);}
   case COLLISION_POINTER -> {long pointer=assets.view(CdR31aAssets.Asset.LEVEL_COL_INDEX).unsignedLong((frame.environment().movement().gates().zoneAct()>>>8)*4);if(pointer!=assets.range(CdR31aAssets.Asset.LEVEL_COLLISION).nativeAddress())throw new HostBoundary(Boundary.LOAD_PRODUCT);image=receipt(new Image(this,image.globals(),image.scroll(),image.foreground(),image.background(),image.video(),image.clears(),image.geometryReady(),OptionalLong.of(pointer),image.fadeInfoWithoutTail(),image.rngSeedLong(),image.targetRightBoundWord(),image.sounds(),image.ordinary()),action);}
   case TITLE_CARD_CREATE -> {if(state.reserved().unsignedByte(192)!=0x3C)throw new HostBoundary(Boundary.LOAD_PRODUCT);var ready=receipt(image,action);var publication=frame.prepareObjectTitleCardGlobal(frame.state(),frame.environment(),CdTitleCard.StoreField.CONTROL_LOCK_BYTE,1);frame.validatePreparedObjectSolidPublication(publication);frame.applyPreparedObjectSolidPublication(publication);image=ready;}
   case BEGIN_MAIN -> image=receipt(globalsImage(image.globals().beginMain()),action);
   case UPDATE_ANIMATED_TILES -> {if(CdPowerupArtRuntime.gate(state.level().powerup()).copyRequired())throw new HostBoundary(Boundary.ACTIVE_POWERUP);image=receipt(image,action);}
   case ENABLE_FADE_AND_CLEAR_MARKER -> {if(frame.environment().movement().gates().timeZone()!=1)throw new HostBoundary(Boundary.LOAD_PRODUCT);image=receipt(image,action);}
   case FROM_WHITE_INFO -> image=receipt(globalsImage(image.globals().fromWhite()),action);
   case ENABLE_DISPLAY -> {image=videoImage(selectedDisplay(image.video(),true));image=receipt(image,action);}
   case COPY_AND_DRAW -> {drawCopied();image=receipt(image,action);}
   case CURRENT_ART -> {currentArt(state);image=receipt(image,action);}
   case VINT_COMPLETED -> {var ready=receipt(image,action);external(()->authority.serviceVInt(this));image=ready;}
   case RETURN_CLEAR -> image=receipt(image,action); // Core's normal-palette clear, not another V-int.
  }bound();}finally{acting=false;}
 }
 private void clearScreen(State state){var plane=new CdR11aBackgroundPlane.State(zero(4096));var v=image.video();var ready=copy(image.globals(),image.scroll(),plane,plane,new Video(zero(640),Optional.empty(),v.playerArt(),v.current(),v.selected()),image.sounds());image=ready;
  for(int i=0;i<4;i++)if(state.waterPalette().unsignedByte(i)!=0)throw new HostBoundary(Boundary.CLEAR_SCREEN_WATER_ALIAS);
  if(frame.state().header().slotId()!=0||frame.state().motion().spriteFlags()!=0||frame.state().header().tile()!=0)throw new HostBoundary(Boundary.CLEAR_SCREEN_PLAYER_ALIAS);
 }
 private void initialForeground(State state){if(!image.geometryReady())throw new HostBoundary(Boundary.GEOMETRY_NOT_LOADED);var s=image.scroll();var f=s.foreground();var bg=assets.view(CdR31aAssets.Asset.LAYOUT_BG);int cursor=64+bg.unsignedByte(1)*128+bg.unsignedByte(0)+1;var ram=state.layoutRam().orElseThrow();var metadata=new CdR11bForegroundPlane.CallerMetadata(cursor<ram.length()?OptionalInt.of(ram.unsignedByte(cursor)):OptionalInt.empty());
  try{adoptForeground(runtimeWriters().foreground().initialize(image.foreground(),new CdR11aBackgroundPlane.Camera((f.cameraX()<<16)|s.foregroundXFractionWord(),f.cameraY16_16()),f.scrollFlags(),metadata),false);}catch(CdR11bForegroundPlane.InvalidPlane original){try{adoptForeground(original.completedPrefix(),false);}catch(RuntimeException|Error publication){if(publication!=original)original.addSuppressed(publication);}throw original;}
 }
 /** Native name-table writes affect current physical planes after selection, without
  * selecting another CPU/palette/HScroll image or replaying draw observations. */
 private Image planeImage(Scroll scroll,CdR11aBackgroundPlane.State foreground,CdR11aBackgroundPlane.State background){
  var v=image.video();if(v.selected() instanceof Published p){var old=p.selection().inputs();var i=new Inputs(old.selectedCpu(),old.selectedSat(),old.palette(),old.hscroll(),old.vscrollLongBits(),old.displayEnabled(),foreground,background,v.current(),v.playerArt());v=new Video(v.cpuSat(),v.completedCpu(),v.playerArt(),v.current(),new Published(project(i)));}
  return copy(image.globals(),scroll,foreground,background,v,image.sounds());
 }
 private void initialBackground(){if(!image.geometryReady())throw new HostBoundary(Boundary.GEOMETRY_NOT_LOADED);var s=image.scroll();try{var out=runtimeWriters().background().initialize(image.background(),live(s.background()),flags(s.background()));image=planeImage(s,image.foreground(),out.state());}catch(CdR11aBackgroundPlane.InvalidPlane original){retainBackgroundPrefix(original,false);throw original;}}
 private void drawCopied(){var s=image.scroll();var c=s.copies();if(!image.geometryReady()&&((c.foregroundFlags()|c.backgroundFlags().bg1())&0xFF00)!=0)throw new HostBoundary(Boundary.GEOMETRY_NOT_LOADED);
  CdR11aBackgroundPlane.Result bg;try{bg=runtimeWriters().background().incremental(image.background(),live(s.background()),c.background(),c.backgroundFlags());}catch(CdR11aBackgroundPlane.InvalidPlane original){retainBackgroundPrefix(original,true);throw original;}var copies=new Copies(c.foregroundX16_16(),c.foregroundY16_16(),c.background(),c.foregroundFlags(),bg.flags());var ready=new Scroll(s.foreground(),s.background(),copies,s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord());image=planeImage(ready,image.foreground(),bg.state());
  // Native BG rows/drawn columns restore a0 (R31 Stage Draw118-136,184-190).
  // Inherit BG IDs+1 without columns, or the post16-read directory cursor.
  int index=1;for(var read:bg.sections())if(read.phase()==CdR11aBackgroundPlane.Phase.INCREMENTAL_COLUMN)index=read.tableIndex()+1;
  var ids=assets.view(CdR31aAssets.Asset.BG_SECTIONS);var metadata=new CdR11bForegroundPlane.CallerMetadata(index>=0&&index<ids.length()?OptionalInt.of(ids.unsignedByte(index)):OptionalInt.empty());
  try{adoptForeground(runtimeWriters().foreground().incremental(image.foreground(),new CdR11aBackgroundPlane.Camera(copies.foregroundX16_16(),copies.foregroundY16_16()),copies.foregroundFlags(),metadata),true);}catch(CdR11bForegroundPlane.InvalidPlane original){try{adoptForeground(original.completedPrefix(),true);}catch(RuntimeException|Error publication){if(publication!=original)original.addSuppressed(publication);}throw original;}
 }
 private void retainBackgroundPrefix(CdR11aBackgroundPlane.InvalidPlane original,boolean consume){
  try{var prefix=original.completedPrefix();if(prefix.isPresent()){var result=prefix.orElseThrow();var s=image.scroll();var c=s.copies();if(consume)c=new Copies(c.foregroundX16_16(),c.foregroundY16_16(),c.background(),c.foregroundFlags(),result.flags());var ready=consume?new Scroll(s.foreground(),s.background(),c,s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()):s;image=planeImage(ready,image.foreground(),result.state());}}
  catch(RuntimeException|Error retention){if(retention!=original)original.addSuppressed(retention);}
 }
 private void adoptForeground(CdR11bForegroundPlane.Result result,boolean consume){var s=image.scroll();var c=s.copies();if(consume)c=new Copies(c.foregroundX16_16(),c.foregroundY16_16(),c.background(),result.flagsWord(),c.backgroundFlags());var ready=new Scroll(s.foreground(),s.background(),c,s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord());image=planeImage(ready,result.state(),image.background());}
 @Override public CdAssetBytes publish(Publication selected){
  current();operationIdle();owner.requirePublication(this,selected);publishing=true;activePublication=selected;
  try{image=videoImage(withSelectedVscroll(image.video(),image.scroll().background().vscroll()));
   // C has no water read. UnknownLiveByte escapes unchanged with prior VScroll retained.
   CdAssetBytes palette=selected.normalPalette();if(selected.selector()==0x12&&CdCheckpointLiveAux.read(image.globals().live(),CdCheckpointLiveAux.Field.WATER_FULLSCREEN)!=0)palette=selected.waterPalette();
   var v=image.video();var s=image.scroll();var inputs=new Inputs(v.completedCpu(),v.cpuSat(),palette,selectedHscroll(s.background().hscrollRam()),s.background().vscroll(),selected.displayEnabled(),image.foreground(),image.background(),selected.section().residency(),frame.artState());
   var ready=videoImage(new Video(v.cpuSat(),v.completedCpu(),inputs.player(),inputs.current(),new Published(project(inputs))));activePalette=palette;image=ready;
   var actualPalette=palette;external(()->authority.loadingPublication(this,owner,selected,actualPalette));bound();return actualPalette;
  }finally{activePalette=null;activePublication=null;publishing=false;}
 }
 void requireLoadingPublication(Publication original,CdAssetBytes palette){current();if(!publishing||activePublication!=original||activePalette!=palette)throw new IllegalStateException("No original applied loading publication");owner.requirePublication(this,original);if(!(image.video().selected() instanceof Published p)||p.selection().inputs().palette()!=palette)throw new IllegalStateException("Original selected image changed");}
 private CdAssetBytes selectedHscroll(CdAssetBytes live){
  CdAssetBytes previous=image.video().selected() instanceof Published p?p.selection().inputs().hscroll():CdSelectedHscroll.fromCarriedWords(((Carried)image.video().selected()).scene().packedHscroll256());
  return CdSelectedHscroll.copyNativePrefix(previous,live);
 }
 private void currentArt(State state){var v=image.video();var current=state.section().residency();
  if(state.phase()==Phase.START&&image.clears().contains(Action.INIT_VDP)&&!image.clears().contains(Action.INIT_CONTROLLERS)){
   // Native InitVDP clears all resident patterns, including the separate Sonic bank,
   // without clearing its CPU buffer, cue or dirty flag (Functions (General).asm:137-150).
   var player=frame.preparePlayerPatternClear();var plane=new CdR11aBackgroundPlane.State(zero(4096));
   var i=new Inputs(Optional.empty(),zero(640),state.displayedPalette(),zero(1024),image.scroll().background().vscroll(),false,plane,plane,current,player.artState());
   var ready=copy(image.globals(),image.scroll(),plane,plane,new Video(v.cpuSat(),v.completedCpu(),i.player(),current,new Published(project(i))),image.sounds());
   frame.validatePreparedPlayerPatternClear(player);frame.applyPreparedPlayerPatternClear(player);image=ready;
  }else{var next=new Video(v.cpuSat(),v.completedCpu(),frame.artState(),current,v.selected());image=videoImage(refreshed(next));}
 }
 private Video refreshed(Video v){return refreshed(v,image.foreground(),image.background());}
 private Video refreshed(Video v,CdR11aBackgroundPlane.State foreground,CdR11aBackgroundPlane.State background){if(!(v.selected() instanceof Published p))return v;var old=p.selection().inputs();var i=new Inputs(old.selectedCpu(),old.selectedSat(),old.palette(),old.hscroll(),old.vscrollLongBits(),old.displayEnabled(),foreground,background,v.current(),v.playerArt());return new Video(v.cpuSat(),v.completedCpu(),v.playerArt(),v.current(),new Published(project(i)));}
 private Video selectedDisplay(Video v,boolean enabled){Selected selected;if(v.selected() instanceof Published p){var o=p.selection().inputs();var i=new Inputs(o.selectedCpu(),o.selectedSat(),o.palette(),o.hscroll(),o.vscrollLongBits(),enabled,o.foreground(),o.background(),o.current(),o.player());selected=new Published(project(i));}else{var o=((Carried)v.selected()).scene();selected=new Carried(new RetainedPlaneSceneRenderer.Scene(o.foreground(),o.background(),o.packedHscroll256(),o.vscrollLongBits(),o.selectedPalette128(),enabled,o.sprites(),o.nativePatterns(),o.selectedBackdropPaletteIndex()));}return new Video(v.cpuSat(),v.completedCpu(),v.playerArt(),v.current(),selected);}
 private Video withSelectedPalette(Video v,CdAssetBytes palette){Selected selected;if(v.selected() instanceof Published p){var o=p.selection().inputs();selected=new Published(project(new Inputs(o.selectedCpu(),o.selectedSat(),palette,o.hscroll(),o.vscrollLongBits(),o.displayEnabled(),o.foreground(),o.background(),o.current(),o.player())));}else{var o=((Carried)v.selected()).scene();selected=new Carried(new RetainedPlaneSceneRenderer.Scene(o.foreground(),o.background(),o.packedHscroll256(),o.vscrollLongBits(),palette.copy(),o.displayEnabled(),o.sprites(),o.nativePatterns(),o.selectedBackdropPaletteIndex()));}return new Video(v.cpuSat(),v.completedCpu(),v.playerArt(),v.current(),selected);}
 private Video withSelectedVscroll(Video v,int bits){Selected selected;if(v.selected() instanceof Published p){var o=p.selection().inputs();selected=new Published(project(new Inputs(o.selectedCpu(),o.selectedSat(),o.palette(),o.hscroll(),bits,o.displayEnabled(),o.foreground(),o.background(),o.current(),o.player())));}else{var o=((Carried)v.selected()).scene();selected=new Carried(new RetainedPlaneSceneRenderer.Scene(o.foreground(),o.background(),o.packedHscroll256(),bits,o.selectedPalette128(),o.displayEnabled(),o.sprites(),o.nativePatterns(),o.selectedBackdropPaletteIndex()));}return new Video(v.cpuSat(),v.completedCpu(),v.playerArt(),v.current(),selected);}
 private static boolean selectedEnabled(Selected selected){return selected instanceof Carried c?c.scene().displayEnabled():((Published)selected).selection().inputs().displayEnabled();}
 private static byte[] selectedPalette(Selected selected){return selected instanceof Carried c?c.scene().selectedPalette128():((Published)selected).selection().inputs().palette().copy();}
 private static Selection project(Inputs i){return new Selection(i,SonicCdCurrentDisplay.project(i,id->{try{return CdR31aArtResidency.readTile(i.current(),id);}catch(CdR31aArtResidency.OwnershipBoundary f){throw new SonicCdCurrentDisplay.ReadStopped(new SonicCdPastDisplay.DisplayFailure(SonicCdPastDisplay.Boundary.valueOf(f.boundary().name()),-1,id,f.destinationWord()));}}));}
    void projectCamera(Camera camera) {
        current();Objects.requireNonNull(camera);var b=frame.environment().movement().bounds();var f=image.scroll().foreground();
        camera.setMinX((short)b.left());camera.setMaxX((short)b.right());camera.setMinY((short)image.scroll().topBoundWord());camera.setMaxY((short)b.bottom());
        camera.setVerticalWrapEnabled(false);camera.setX((short)f.cameraX());camera.setY((short)f.cameraYWord());camera.setShakeOffsets(0,0);
    }
    private final class Projection implements ZoneScrollHandler,com.openggf.level.scroll.ScrollOutputRebuilder {
        @Override public void rebuildOutput(int[] output,int cameraX,int cameraY,int frameCounter,int actId) {
            // Generic logical act indices do not select the native stage. This exact Host already
            // owns R31A; project its restored HScroll without a native init/update dispatch.
            projectOutput(output);
        }
        @Override public void update(int[] output,int cameraX,int cameraY,int frameCounter,int actId) {
            if(actId!=0)throw new IllegalArgumentException("R31A act0 required");projectOutput(output);
        }
        private void projectOutput(int[] output) {
            current();Objects.requireNonNull(output);
            if(output.length>256)throw new IllegalArgumentException("Complete HScroll is256 LONGs");
            var h=image.scroll().background().hscrollRam();for(int i=0;i<output.length;i++)output[i]=(int)h.unsignedLong(i*4);
        }
        @Override public short getVscrollFactorBG(){current();return(short)image.scroll().background().vscroll();}
        @Override public short getVscrollFactorFG(){current();return(short)(image.scroll().background().vscroll()>>>16);}
        private int extreme(boolean minimum) {
            current();var h=image.scroll().background().hscrollRam();int result=minimum?Integer.MAX_VALUE:Integer.MIN_VALUE;
            for(int i=0;i<224;i++){int bits=(int)h.unsignedLong(i*4);int offset=(short)bits-(short)(bits>>>16);result=minimum?Math.min(result,offset):Math.max(result,offset);}
            return result;
        }
        @Override public int getMinScrollOffset(){return extreme(true);}
        @Override public int getMaxScrollOffset(){return extreme(false);}
        @Override public int getBgCameraX(){current();return(short)image.scroll().background().cameraBgXWord();}
        @Override public int getBgPeriodWidth(){current();return 512;}
        @Override public Object captureRewindState(){current();return new ScrollBinding(SonicCdR31aWorldHost.this,owner);}
        @Override public void restoreRewindState(Object snapshot){
            current();if(!(snapshot instanceof ScrollBinding b)||b.owner()!=SonicCdR31aWorldHost.this||b.visit()!=owner)
                throw new IllegalArgumentException("Foreign or retired R31A scroll binding");
            // Observational lifetime token only. Core Snapshot already restores this ONE Host image.
        }
    }

    /** Logic-only callers need no GL. Real drawing lazily borrows the existing governed renderer. */
    void draw() {
        if(ordinary!=null&&ordinary.isOrdinaryRunning())throw new IllegalStateException("World service active");
        current();if(acting||publishing)throw new IllegalStateException("R31A loading operation active");frame.captureShared();
        var selected=image.video().selected();var scene=scene(selected);var graphics=GameServices.graphics();
        if(graphics.isHeadlessMode()||!graphics.isGlInitialized())return;
        drawing=true;
        try {
            if(render==null)render=createRenderBinding();
            var binding=render;requireRender(binding,selected);
            binding.renderer().draw(scene,binding.camera().getX(),binding.camera().getY(),()->requireRender(binding,selected));
            requireRender(binding,selected);
        } finally {drawing=false;}
    }
    private static RetainedPlaneSceneRenderer.Scene scene(Selected selected) {
        if(selected instanceof Carried c)return c.scene();
        var projection=((Published)selected).selection().projection();
        if(projection instanceof SonicCdPastDisplay.Blocked blocked)throw new HostBoundary(Boundary.SELECTED_DISPLAY,Optional.of(blocked.failure()));
        return projection instanceof SonicCdPastDisplay.Ready r?r.scene():((SonicCdPastDisplay.Disabled)projection).scene();
    }
    private RenderBinding createRenderBinding() {
        var mode=SessionManager.getCurrentGameplayMode();var world=mode.getWorldSession();var levels=mode.getLevelManager();
        var camera=mode.getCamera();var graphics=GameServices.graphics();
        if(GameServices.level()!=levels||camera!=GameServices.camera()||camera.getWidth()!=320||camera.getHeight()!=224)
            throw new IllegalStateException("R31A actual World/viewport changed before renderer allocation");
        var renderer=new RetainedPlaneSceneRenderer(graphics,PatternAtlasRange.OBJECTS);
        return new RenderBinding(mode,world,world.getDataSource(),levels,levels.getCurrentLevel(),levels.getCompletedProductionLoadGeneration(),
                camera,graphics,graphics.getPatternAtlas(),GL.getCapabilities(),Thread.currentThread(),System.getProperty("openggf.headless.gl"),renderer);
    }
    private void requireRender(RenderBinding b,Selected selected) {
        if(!drawing||closed||closing||acting||publishing||image.video().selected()!=selected)
            throw new IllegalStateException("R31A selected draw was reentered or changed");
        bound();var mode=SessionManager.getCurrentGameplayMode();
        if(b==null||b!=render||mode!=b.mode()||mode.getWorldSession()!=b.world()||b.world().getDataSource()!=b.data()||mode.getLevelManager()!=b.levels()||GameServices.level()!=b.levels()
                ||b.levels().getCurrentLevel()!=b.level()||b.levels().getCompletedProductionLoadGeneration()!=b.generation()
                ||mode.getCamera()!=b.camera()||GameServices.camera()!=b.camera()||b.camera().getWidth()!=320||b.camera().getHeight()!=224
                ||GameServices.graphics()!=b.graphics()||!b.graphics().isGlInitialized()||b.graphics().isHeadlessMode()
                ||b.graphics().getPatternAtlas()!=b.atlas()||Thread.currentThread()!=b.thread()
                ||!Objects.equals(System.getProperty("openggf.headless.gl"),b.backend())||GL.getCapabilities()!=b.capabilities())
            throw new IllegalStateException("R31A retained source/World/level/generation/graphics lifetime changed");
    }
    @Override public void close() {
        if(ordinary!=null&&ordinary.isOrdinaryRunning())throw new IllegalStateException("World service active");
        requireRenderIdle();
        if(Thread.currentThread()!=thread)throw new IllegalStateException("Another R31A Host lifetime thread");
        if(closed)return;if(closing||acting||publishing)throw new IllegalStateException("R31A Host operation active");
        closing=true;
        try {if(render!=null){render.renderer().close();render=null;}closed=true;}
        finally {closing=false;}
    }

 private static CdR11aBackgroundPlane.LiveCameras live(CdR11aBackgroundScroll.State b){return new CdR11aBackgroundPlane.LiveCameras(new CdR11aBackgroundPlane.Camera(b.cameraBgX(),b.cameraBgY()),new CdR11aBackgroundPlane.Camera(b.cameraBg2X(),b.cameraBg2Y()),new CdR11aBackgroundPlane.Camera(b.cameraBg3X(),b.cameraBg3Y()));}
 private static CdR11aBackgroundPlane.FlagWords flags(CdR11aBackgroundScroll.State b){return new CdR11aBackgroundPlane.FlagWords(b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3());}
 static Globals with(Globals g,CdRingGlobals ring,CdCheckpointLiveAux.State live,CdPlayerBody.ContactGlobals contact,CdOpeningObjectRuntime.ScoreGlobals score,OptionalInt pause,Optional<CdLevelHud.BonusValues> bonus,Optional<CdGlobalObjectAnimation.Extras> animations,CdAssetBytes steps,CdAssetBytes timers){return new Globals(ring,live,contact,score,g.projectorByte(),g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),pause,bonus,g.tail(),animations,steps,timers,g.layerByte(),g.bossFlagsByte(),g.eventRoutineWord());}
 /** CLR camera range has no broader spawner-reset seam. Unbound -1 is this existing
  * runtime's cold null-cursor representation; saved header bytes are outside that clear. */
 private static boolean coldSpawner(CdObjectPlacementRuntime.Status s){return s.routine()==0&&s.previousChunkWord()==0&&s.rightRecordIndex()==0&&s.leftRecordIndex()==0&&s.nullRightByteOffset()==-1&&s.nullLeftByteOffset()==-1;}
 private static boolean samePlacement(CdObjectPlacementRuntime.Snapshot a,CdObjectPlacementRuntime.Snapshot b){return a.status().equals(b.status())&&a.pool().slots().equals(b.pool().slots())&&Arrays.equals(a.savedFlags().copy(),b.savedFlags().copy());}
 private static final class ByteValue{private final CdAssetBytes value;private ByteValue(CdAssetBytes value){this.value=value;}@Override public boolean equals(Object o){return o instanceof ByteValue b&&Arrays.equals(value.copy(),b.value.copy());}@Override public int hashCode(){return Arrays.hashCode(value.copy());}}
 private static Object bytes(CdAssetBytes b){return new ByteValue(b);}
 private static Object cpuValue(Optional<CdObjectDrawQueue.CpuSat> cpu){if(cpu.isEmpty())return List.of();var c=cpu.orElseThrow();return List.of(bytes(CdAssetBytes.copyOf(c.bytes())),c.spriteCount(),c.provenance());}
 private static Object playerValue(CdPlayerArtRuntime.State p){return List.of(bytes(p.cpuBuffer()),bytes(p.residentBank()),p.lastFrame(),p.dirty());}
 private static Object inputsValue(Inputs i){return List.of(cpuValue(i.selectedCpu()),bytes(i.selectedSat()),bytes(i.palette()),bytes(i.hscroll()),i.vscrollLongBits(),i.displayEnabled(),bytes(i.foreground().nameTable()),bytes(i.background().nameTable()),i.current(),playerValue(i.player()));}
 private static Object selectedValue(Selected selected){if(selected instanceof Carried c)return c.scene();var s=((Published)selected).selection();return List.of(inputsValue(s.inputs()),s.projection());}
 private static Object scrollValueKey(Scroll s){var b=s.background();return List.of(s.foreground(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord(),List.of(b.cameraBgX(),b.cameraBgY(),b.cameraBg2X(),b.cameraBg2Y(),b.cameraBg3X(),b.cameraBg3Y(),bytes(b.crossingRam()),b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3(),bytes(b.cloudRam()),bytes(b.deformationRam()),bytes(b.hscrollRam()),b.vscroll()));}
 private static Object globalsValue(Globals g){return List.of(g.ring(),g.live(),g.contact(),g.score(),g.projectorByte(),g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),g.pauseMusicBit(),g.bonus(),g.tail(),g.animations(),bytes(g.paletteSteps7()),bytes(g.paletteTimers7()),g.layerByte(),g.bossFlagsByte(),g.eventRoutineWord());}
 private static Object imageValue(Image i){var v=i.video();return List.of(i.owner(),globalsValue(i.globals()),scrollValueKey(i.scroll()),bytes(i.foreground().nameTable()),bytes(i.background().nameTable()),List.of(bytes(v.cpuSat()),cpuValue(v.completedCpu()),playerValue(v.playerArt()),v.current(),selectedValue(v.selected())),i.clears(),i.geometryReady(),i.collisionPointer(),i.fadeInfoWithoutTail(),i.rngSeedLong(),i.targetRightBoundWord(),i.sounds(),ordinaryValue(i.ordinary()));}
 private static void optionalByte(OptionalInt v){Objects.requireNonNull(v);v.ifPresent(SonicCdR31aWorldHost::octet);}
 private static boolean allZero(CdAssetBytes b){for(byte value:b.copy())if(value!=0)return false;return true;}
 private static CdAssetBytes zero(int n){return CdAssetBytes.copyOf(new byte[n]);}
 private static void extent(CdAssetBytes b,int n){if(Objects.requireNonNull(b).length()!=n)throw new IllegalArgumentException("Exact extent "+n);}
 private static void word(int v){if((v&~65535)!=0)throw new IllegalArgumentException("Native WORD required");}
 private static void octet(int v){if((v&~255)!=0)throw new IllegalArgumentException("Native BYTE required");}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
