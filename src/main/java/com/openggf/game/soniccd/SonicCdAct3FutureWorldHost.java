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
import java.util.*;
import static com.openggf.game.soniccd.SonicCdAct3FutureLoadingVisit.*;
/** Original-USA Act3 external loading/ordinary values. Native N1ecc5f3a Main19-432,
 * Scroll(Act3) includes shared plane writers; _Events127-137,445-481 and common7-53.
 * Module's private Authority authenticates assembly/epoch/session, never an equal value or snapshot.
 * No constructor submits work, mints FULL admission, creates a clock or marks unknown pixels known.
 */
final class SonicCdAct3FutureWorldHost implements Host,AutoCloseable {
 interface Authority {
  void require(SonicCdAct3FutureWorldHost host,SonicCdAct3FutureLoadingVisit core,CdWorld world,
               CdPlayerFrameRuntime frame,CdR13FutureAssets assets,CdR13SectionArt art);
  int selectedGoodFutureByte(SonicCdAct3FutureWorldHost host);
  SonicCdGameModule.PastSound levelMusic(SonicCdAct3FutureWorldHost host,Program actualProgramme,int rawTimeZone,int timeAttackMode);
  void serviceVInt(SonicCdAct3FutureWorldHost host);
  void resetGoodFutureFlags(SonicCdAct3FutureWorldHost host);
  // BEGIN IPX BOSS RETURN AUTHORITY 20261107
  /** Only the original private Core/Host call may borrow the actual Module progress owner. */
  default Optional<IpxProgressStore> prepareIpxBossReturn(SonicCdAct3FutureWorldHost host,SonicCdAct3FutureLoadingVisit.IpxBossReturnCall call){return Optional.empty();}
  /** Reached RunR1/UnlockTimeAttackLevel operand; no native return/store authority from the value. */
  default OptionalInt runR1UnlockByte(SonicCdAct3FutureWorldHost host){return OptionalInt.empty();}
  default Optional<IpxRunR1Store> prepareRunR1(SonicCdAct3FutureWorldHost host,SonicCdAct3FutureLoadingVisit.IpxRunR1Call call){return Optional.empty();}
  // END IPX BOSS RETURN AUTHORITY 20261107
  /** Actual Module/knowledge image only; absence means the owner is unavailable, not byte zero. */
  default Optional<SonicCdAct3FutureObjectPass.ResultsFlagsImage> resultsFlags(SonicCdAct3FutureWorldHost host,SonicCdAct3FutureObjectPass pass){return Optional.empty();}
  /** Prebuild the native CLR/RMW against the full programme/knowledge image, with no owner writes. */
  default ResultsFlagsStore prepareResultsFlags(SonicCdAct3FutureWorldHost host,SonicCdAct3FutureObjectPass pass,SonicCdAct3FutureObjectPass.Adoption actual,SonicCdAct3FutureObjectPass.ResultsFlagsImage expected,int value){throw actual.unownedEndFlags();}
 }
 enum Boundary { GEOMETRY_NOT_LOADED,LOAD_PRODUCT,ACTIVE_POWERUP,SELECTED_DISPLAY,CLEAR_SCREEN_WATER_ALIAS,CLEAR_SCREEN_PLAYER_ALIAS,BOSS_FLAGS,WATER_FULLSCREEN,WATER_PALETTE_PUBLICATION }
 static final class HostBoundary extends UnsupportedOperationException {
  private final Boundary boundary;private final Optional<SonicCdPastDisplay.DisplayFailure> displayFailure;
  private HostBoundary(Boundary b){this(b,Optional.empty());}private HostBoundary(Boundary b,Optional<SonicCdPastDisplay.DisplayFailure> f){super("R13 Host "+b+f.map(Object::toString).orElse(""));boundary=b;displayFailure=f;}
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
            optionalByte(projectorByte); optionalByte(layerByte); optionalByte(bossFlagsByte); Objects.requireNonNull(eventRoutineWord); eventRoutineWord.ifPresent(SonicCdAct3FutureWorldHost::word); Objects.requireNonNull(checkpointSaved);
            Objects.requireNonNull(drownTimerWord); Objects.requireNonNull(debugBlockWord); Objects.requireNonNull(pauseMusicBit);
            Objects.requireNonNull(bonus); Objects.requireNonNull(tail); Objects.requireNonNull(animations);
            extent(paletteSteps7, 7); extent(paletteTimers7, 7);
            drownTimerWord.ifPresent(SonicCdAct3FutureWorldHost::word); debugBlockWord.ifPresent(SonicCdAct3FutureWorldHost::word);
            if (pauseMusicBit.isPresent() && (pauseMusicBit.getAsInt() & ~0x80) != 0) throw new IllegalArgumentException("PauseMusic bit7 only");
        }
        @Override public boolean equals(Object other){return this==other||other instanceof Globals g&&globalsValue(this).equals(globalsValue(g));}
        @Override public int hashCode(){return globalsValue(this).hashCode();}
        private Globals scalars(CdRingGlobals r, CdCheckpointLiveAux.State l) {
            return new Globals(r,l,contact,score,projectorByte,checkpointSaved,drownTimerWord,debugBlockWord,pauseMusicBit,bonus,tail,animations,paletteSteps7,paletteTimers7,layerByte,bossFlagsByte,eventRoutineWord);
        }
        private Globals flowers() {
            var r=ring;
            return new Globals(new CdRingGlobals(r.rings(),r.lives(),r.livesFlags(),r.updateHudRings(),r.updateHudLives(),0,0,0),
                    live,contact,score,projectorByte,checkpointSaved,drownTimerWord,OptionalInt.of(0),pauseMusicBit,bonus,tail,
                    Optional.of(new CdGlobalObjectAnimation.Extras(0,0,0,0,new CdRingSpillGlobals(0,0,0))),paletteSteps7,paletteTimers7,OptionalInt.of(0),bossFlagsByte,eventRoutineWord);
        }
        private Globals misc() {
            return new Globals(ring,CdCheckpointLiveAux.clearMiscAtLevelStart(live),contact,score,projectorByte,checkpointSaved,
                    drownTimerWord,debugBlockWord,OptionalInt.of(0),bonus,tail,animations,paletteSteps7,paletteTimers7,layerByte,bossFlagsByte,eventRoutineWord);
        }
        private Globals camera() {
            return new Globals(ring,live,contact,score,projectorByte,checkpointSaved,drownTimerWord,debugBlockWord,pauseMusicBit,
                    Optional.of(new CdLevelHud.BonusValues(0,0,0)),tail,animations,zero(7),zero(7),layerByte,OptionalInt.of(0),OptionalInt.of(0));
        }
        private Globals drown() {
            return new Globals(ring,live,contact,score,projectorByte,checkpointSaved,OptionalInt.of(30),debugBlockWord,pauseMusicBit,bonus,tail,animations,paletteSteps7,paletteTimers7,layerByte,bossFlagsByte,eventRoutineWord);
        }
        private Globals beginMain() {
            var r=ring;
            return new Globals(new CdRingGlobals(0,r.lives(),0,0x80,1,r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame()),
                    CdCheckpointLiveAux.clearTimeAtSpawnZero(live),new CdPlayerBody.ContactGlobals(0,0,contact.combineRing()),new CdOpeningObjectRuntime.ScoreGlobals(score.score(),score.nextLifeScore(),0x80),
                    projectorByte,checkpointSaved,drownTimerWord,debugBlockWord,pauseMusicBit,bonus,
                    Optional.of(new Tail(0,0,0,0,0x202F)),animations,paletteSteps7,paletteTimers7,layerByte,bossFlagsByte,eventRoutineWord);
        }
        private Globals fromWhite() {
            var t=tail.orElseThrow(()->new IllegalStateException("FromWhite requires actual Main tail stores"));
            return new Globals(ring,live,contact,score,projectorByte,checkpointSaved,drownTimerWord,debugBlockWord,pauseMusicBit,bonus,
                    Optional.of(new Tail(t.player2ControlsWord(),t.player2BoredWord(),t.unknownLevelByte(),t.demoS1IndexWord(),0x003F)),
                    animations,paletteSteps7,paletteTimers7,layerByte,bossFlagsByte,eventRoutineWord);
        }
    }

 record Inputs(Optional<CdObjectDrawQueue.CpuSat> selectedCpu,CdAssetBytes selectedSat,CdAssetBytes palette,CdAssetBytes hscroll,
               int vscrollLongBits,boolean displayEnabled,CdR11aBackgroundPlane.State foreground,CdR11aBackgroundPlane.State background,
               CdR13ArtResidency.State current,CdPlayerArtRuntime.State player) implements SonicCdCurrentDisplay.Inputs {
  Inputs{Objects.requireNonNull(selectedCpu);extent(selectedSat,640);extent(palette,128);extent(hscroll,1024);Objects.requireNonNull(foreground);Objects.requireNonNull(background);Objects.requireNonNull(current);Objects.requireNonNull(player);}
  @Override public boolean equals(Object other){return this==other||other instanceof Inputs i&&inputsValue(this).equals(inputsValue(i));}
  @Override public int hashCode(){return inputsValue(this).hashCode();}
 }
 record Selection(Inputs inputs,SonicCdPastDisplay.Projection projection){Selection{Objects.requireNonNull(inputs);Objects.requireNonNull(projection);}}
 sealed interface Selected permits Carried,Published{}
 record Carried(RetainedPlaneSceneRenderer.Scene scene)implements Selected{Carried{Objects.requireNonNull(scene);}}
 record Published(Selection selection)implements Selected{Published{Objects.requireNonNull(selection);}}
 record Video(CdAssetBytes cpuSat,Optional<CdObjectDrawQueue.CpuSat> completedCpu,CdPlayerArtRuntime.State playerArt,
              CdR13ArtResidency.State current,Selected selected){
  Video{extent(cpuSat,640);Objects.requireNonNull(completedCpu);Objects.requireNonNull(playerArt);Objects.requireNonNull(current);Objects.requireNonNull(selected);
   if(completedCpu.isPresent()&&!Arrays.equals(completedCpu.orElseThrow().bytes(),cpuSat.copy()))throw new IllegalArgumentException("CPU SAT/provenance differ");}
 }
 record Initial(Globals globals,Scroll scroll,CdR11aBackgroundPlane.State foreground,CdR11aBackgroundPlane.State background,Video video,CdAssetBytes savedFlags){
  Initial{Objects.requireNonNull(globals);Objects.requireNonNull(scroll);Objects.requireNonNull(foreground);Objects.requireNonNull(background);Objects.requireNonNull(video);extent(savedFlags,CdObjectPlacementRuntime.SAVED_BANK_BYTES);}
 }
 private record Image(SonicCdAct3FutureWorldHost owner,Globals globals,Scroll scroll,CdR11aBackgroundPlane.State foreground,
                      CdR11aBackgroundPlane.State background,Video video,Set<Action> clears,boolean geometryReady,
                      OptionalLong collisionPointer,OptionalInt fadeInfoWithoutTail,OptionalLong rngSeedLong,OptionalInt targetRightBoundWord,List<SonicCdGameModule.PastSound> sounds){
  Image{clears=Set.copyOf(clears);Objects.requireNonNull(collisionPointer);Objects.requireNonNull(fadeInfoWithoutTail);Objects.requireNonNull(rngSeedLong);Objects.requireNonNull(targetRightBoundWord);targetRightBoundWord.ifPresent(SonicCdAct3FutureWorldHost::word);if(rngSeedLong.isPresent()&&(rngSeedLong.getAsLong()<0||rngSeedLong.getAsLong()>0xFFFF_FFFFL))throw new IllegalArgumentException("Native RNG LONG");fadeInfoWithoutTail.ifPresent(SonicCdAct3FutureWorldHost::word);if(globals.tail().isPresent()&&fadeInfoWithoutTail.isPresent())throw new IllegalArgumentException("One palette-fade-info owner required");sounds=List.copyOf(sounds);}
 }
 private record Snapshot(Image image,CdObjectPlacementRuntime.Snapshot placement){
  @Override public boolean equals(Object o){return o instanceof Snapshot s&&imageValue(image).equals(imageValue(s.image))&&placement.status().equals(s.placement.status())&&placement.pool().slots().equals(s.placement.pool().slots())&&Arrays.equals(placement.savedFlags().copy(),s.placement.savedFlags().copy());}
  @Override public int hashCode(){return 31*(31*imageValue(image).hashCode()+placement.status().hashCode())+31*placement.pool().slots().hashCode()+Arrays.hashCode(placement.savedFlags().copy());}
 }
 static final class Prepared{private final Image before,after;private final Object scope;private Prepared(Image b,Image a,Object scope){before=b;after=a;this.scope=scope;}}
 private record ScrollBinding(SonicCdAct3FutureWorldHost owner,SonicCdAct3FutureLoadingVisit visit){}
 private record RenderBinding(GameplayModeContext mode,WorldSession world,GameDataSource data,LevelManager levels,Level level,long generation,
                              Camera camera,GraphicsManager graphics,PatternAtlas atlas,GLCapabilities capabilities,Thread thread,String backend,RetainedPlaneSceneRenderer renderer){}
 private final Authority authority;private final CdR13FutureAssets assets;private final CdPlayerFrameRuntime frame;private final CdR13SectionArt art;
 private final CdObjectPlacementRuntime placement;private final CdR13FutureScrollPalette scrollRules;private final CdR13PlaneSources planeSources;
 private final CdR11aBackgroundPlane backgroundWriter;private final CdR11bForegroundPlane foregroundWriter;
 private final ZoneScrollHandler handler=new Projection();private SonicCdAct3FutureLoadingVisit owner;private CdWorld world;
 private Image image;private RenderBinding render;private boolean acting,publishing,drawing,closing,closed;
 SonicCdAct3FutureWorldHost(Authority authority,CdR13FutureAssets assets,CdPlayerFrameRuntime frame,CdR13SectionArt art,Initial initial){
  this.authority=Objects.requireNonNull(authority);this.assets=Objects.requireNonNull(assets);this.frame=Objects.requireNonNull(frame);this.art=Objects.requireNonNull(art);Objects.requireNonNull(initial);
  if(!frame.stageSource().ownsAct3Future(assets)||!art.ownsAssets(assets)||!initial.video().current().ownsAssets(assets)||!initial.video().current().hasNativeHudProfile())throw new IllegalArgumentException("Exact R13 Frame/art/native-HUD profile required");
  if(!samePlayer(frame.artState(),initial.video().playerArt()))throw new IllegalArgumentException("Ingress player image differs from Frame");
  placement=CdObjectPlacementRuntime.fromAct3FutureAssets(assets,initial.savedFlags());scrollRules=CdR13FutureScrollPalette.bind(assets);planeSources=CdR13PlaneSources.fromAssets(assets);
  backgroundWriter=CdR11aBackgroundPlane.bindAct3Future(planeSources);var t=assets.terrain();foregroundWriter=CdR11bForegroundPlane.fromTables(t.blocks(),t.chunks(),t.layout().ram());
  image=new Image(this,initial.globals(),initial.scroll(),initial.foreground(),initial.background(),initial.video(),Set.of(),false,OptionalLong.empty(),OptionalInt.empty(),OptionalLong.empty(),OptionalInt.empty(),List.of());
 }
 void install(SonicCdAct3FutureLoadingVisit core){idle();if(owner!=null||Objects.requireNonNull(core).assets()!=assets||core.state().phase()!=Phase.ENTRY||core.state().section().residency()!=image.video().current())throw new IllegalArgumentException("One actual Core/entry/current relation");authority.require(this,core,null,frame,assets,art);owner=core;}
 void bindWorld(CdWorld actual){current();if(world!=null||owner.state().phase()!=Phase.ENTRY)throw new IllegalStateException("Bind actual World once before begin");authority.require(this,owner,Objects.requireNonNull(actual),frame,assets,art);world=actual;}
 void requireRenderIdle(){if(drawing)throw new IllegalStateException("R13 retained draw active");}
 private void idle(){requireRenderIdle();if(closed||closing)throw new IllegalStateException("R13 Host retired");}
 private void bound(){if(owner==null)throw new IllegalStateException("R13 Host not installed");authority.require(this,owner,world,frame,assets,art);if(frame.closed()||!frame.stageSource().ownsAct3Future(assets))throw new IllegalStateException("R13 Frame/source retired");}
 private void current(){idle();bound();}
 void requireAuthority(Authority a,CdWorld w){current();if(a!=authority||w!=world)throw new IllegalArgumentException("Another actual World/authority");}
 @Override public void validate(SonicCdAct3FutureLoadingVisit core,CdPlayerFrameRuntime f,CdR13FutureAssets a,CdR13SectionArt section){current();if(core!=owner||f!=frame||a!=assets||section!=art)throw new IllegalArgumentException("Foreign Core/Frame/assets/art");}
 Globals globals(){current();return image.globals();}CdObjectPlacementRuntime placement(){current();return placement;}
 @Override public Scroll scroll(){current();return image.scroll();}Video video(){current();return image.video();}Selected selected(){current();return image.video().selected();}
 CdR11aBackgroundPlane.State foreground(){current();return image.foreground();}CdR11aBackgroundPlane.State background(){current();return image.background();}
 boolean geometryReady(){current();return image.geometryReady();}OptionalLong collisionPointer(){current();return image.collisionPointer();}
 List<SonicCdGameModule.PastSound> sounds(){current();return image.sounds();}
 List<SonicCdGameModule.PastSound> takeSounds(){ordinaryIdle();if(world!=null&&world.isOrdinaryRunning())throw new IllegalStateException("Cannot drain sounds during original ordinary callbacks");var s=image.sounds();if(!s.isEmpty())image=copy(image.globals(),image.scroll(),image.foreground(),image.background(),image.video(),List.of());return s;}
 ZoneScrollHandler handler(){current();return handler;}
 @Override public int selectedGoodFutureByte(){current();return authority.selectedGoodFutureByte(this);}
 @Override public int livesByte(){current();return image.globals().ring().lives();}
 @Override public Object capture(){current();if(acting||publishing)throw new IllegalStateException("R13 Host operation active");return new Snapshot(image,placement.capture());}
 @Override public void validateRestore(Object value){current();if(world!=null&&world.isOrdinaryRunning())throw new IllegalStateException("World service active");if(acting||publishing)throw new IllegalStateException("R13 Host operation active");if(!(value instanceof Snapshot s)||s.image().owner()!=this)throw new IllegalArgumentException("Foreign Host snapshot");placement.validateRestore(s.placement());}
 @Override public void restore(Object value){validateRestore(value);var s=(Snapshot)value;placement.restore(s.placement());image=s.image();}
 private Image copy(Globals g,Scroll s,CdR11aBackgroundPlane.State fg,CdR11aBackgroundPlane.State bg,Video v,List<SonicCdGameModule.PastSound> sounds){return new Image(this,g,s,fg,bg,v,image.clears(),image.geometryReady(),image.collisionPointer(),g.tail().isPresent()?OptionalInt.empty():image.fadeInfoWithoutTail(),image.rngSeedLong(),image.targetRightBoundWord(),sounds);}
 private void globals(Globals g){image=copy(g,image.scroll(),image.foreground(),image.background(),image.video(),image.sounds());}
 private void video(Video v){image=copy(image.globals(),image.scroll(),image.foreground(),image.background(),v,image.sounds());}
 private void scrollValue(Scroll s){image=copy(image.globals(),s,image.foreground(),image.background(),image.video(),image.sounds());}
 /** Native destRightBound, never inferred from the separately owned current Frame bound. */
 OptionalInt targetRightBoundWord(){current();return image.targetRightBoundWord();}
 private void targetRightBound(int word){image=new Image(this,image.globals(),image.scroll(),image.foreground(),image.background(),image.video(),image.clears(),image.geometryReady(),image.collisionPointer(),image.fadeInfoWithoutTail(),image.rngSeedLong(),OptionalInt.of(word),image.sounds());}
 OptionalLong rngSeedLong(){current();return image.rngSeedLong();}
 OptionalInt paletteFadeInfoWord(){current();return image.globals().tail().isPresent()?OptionalInt.of(image.globals().tail().orElseThrow().paletteFadeInfoWord()):image.fadeInfoWithoutTail();}
 private void setFadeInfo(int word){var g=image.globals();if(g.tail().isPresent()){var t=g.tail().orElseThrow();globals(new Globals(g.ring(),g.live(),g.contact(),g.score(),g.projectorByte(),g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),g.pauseMusicBit(),g.bonus(),Optional.of(new Tail(t.player2ControlsWord(),t.player2BoredWord(),t.unknownLevelByte(),t.demoS1IndexWord(),word)),g.animations(),g.paletteSteps7(),g.paletteTimers7(),g.layerByte(),g.bossFlagsByte(),g.eventRoutineWord()));}else image=new Image(this,g,image.scroll(),image.foreground(),image.background(),image.video(),image.clears(),image.geometryReady(),image.collisionPointer(),OptionalInt.of(word),image.rngSeedLong(),image.targetRightBoundWord(),image.sounds());}
 private void receipt(Action a){var set=EnumSet.noneOf(Action.class);set.addAll(image.clears());set.add(a);image=new Image(this,image.globals(),image.scroll(),image.foreground(),image.background(),image.video(),set,image.geometryReady(),image.collisionPointer(),image.fadeInfoWithoutTail(),image.rngSeedLong(),image.targetRightBoundWord(),image.sounds());}
 @Override public void storeScroll(Scroll before,Scroll ready){current();owner.requireScrollStore(this,before,ready);if(image.scroll()!=before)throw new IllegalStateException("Stale complete scroll");scrollValue(ready);}
 @Override public void act(Action action,State state){
  current();owner.requireAction(this,action,state);if(acting||publishing)throw new IllegalStateException("R13 Host reentry");acting=true;
  try{switch(action){
   case ENTRY_STORES,PRELUDE_END,NORMAL_LOAD_PALETTE,CLEAR_POWERUP_FLOWERS,CLEAR_DEMO_INDEX,LEVEL_SCROLL,FORCE_LEFT_COLUMN,RESERVED_HUD,RESERVED_TIME_ICON,BEGIN_LEVEL->{} // Core/Frame already own these exact native writes.
   case INITIAL_FADE_SETUP->setFadeInfo(0x003F);
   case FRESH_RESET_PREFIX->{var g=image.globals();globals(with(g,g.ring(),g.live(),g.contact(),new CdOpeningObjectRuntime.ScoreGlobals(g.score().score(),5000,g.score().updateHudScore()),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7()));}
   case RESET_SAVED_OBJECT_FLAGS->placement.resetSavedObjectFlags();
   case FRESH_RESET_SUFFIX->{authority.resetGoodFutureFlags(this);var g=image.globals();var r=g.ring();globals(with(g,new CdRingGlobals(r.rings(),frame.environment().globals().timeAttackMode()==0?3:1,r.livesFlags(),r.updateHudRings(),r.updateHudLives(),r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame()),g.live(),g.contact(),new CdOpeningObjectRuntime.ScoreGlobals(0,g.score().nextLifeScore(),g.score().updateHudScore()),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7()));}
   case PLAY_LEVEL_MUSIC->{if(state.calls().contains(Call.PLAY_LEVEL_MUSIC))throw new IllegalStateException("Music replay");var s=new ArrayList<>(image.sounds());s.add(Objects.requireNonNull(authority.levelMusic(this,state.program(),frame.environment().movement().gates().timeZone(),frame.environment().globals().timeAttackMode())));image=copy(image.globals(),image.scroll(),image.foreground(),image.background(),image.video(),s);}
   case CLEAR_DRAW_QUEUE,CLEAR_UNKNOWN_BUFFER->receipt(action);
   case CLEAR_FLOWERS->{globals(image.globals().flowers());receipt(action);}
   case CLEAR_OBJECTS->{if(frame.state().header().slotId()!=0||!allZero(state.hudSlots()))throw new HostBoundary(Boundary.LOAD_PRODUCT);placement.pool().clearDynamicSlots();receipt(action);}
   case CLEAR_MISC->{globals(image.globals().misc());image=new Image(this,image.globals(),image.scroll(),image.foreground(),image.background(),image.video(),image.clears(),image.geometryReady(),image.collisionPointer(),image.fadeInfoWithoutTail(),OptionalLong.of(0),image.targetRightBoundWord(),image.sounds());receipt(action);}
   case CLEAR_CAMERA->{globals(image.globals().camera());targetRightBound(0);receipt(action);}
   case CLEAR_SCREEN->{clearScreen(state);receipt(action);}
   case LEVEL_SIZE->targetRightBound(assets.bounds().right());
   case DROWN_TIMER->globals(image.globals().drown());
   case SETUP_CAMERA_AND_SCROLL->{} // Core adopted the actual source start/camera and native variant initialization.
   case LEVEL_LAYOUT->{if(state.blocksRam().isEmpty()||state.layoutRam().isEmpty()||!Arrays.equals(state.blocksRam().orElseThrow().copy(),assets.terrain().blocks().copy())||!Arrays.equals(state.layoutRam().orElseThrow().copy(),assets.terrain().layout().ram().copy()))throw new HostBoundary(Boundary.LOAD_PRODUCT);image=new Image(this,image.globals(),image.scroll(),image.foreground(),image.background(),image.video(),image.clears(),true,image.collisionPointer(),image.fadeInfoWithoutTail(),image.rngSeedLong(),image.targetRightBoundWord(),image.sounds());}
   case INITIAL_FOREGROUND->initialForeground(state);
   case INITIAL_BACKGROUND->initialBackground();
   case COLLISION_POINTER->{long p=assets.view(CdR13FutureAssets.Asset.LEVEL_COL_INDEX).unsignedLong((frame.environment().movement().gates().zoneAct()>>>8)*4);if(p!=assets.range(CdR13FutureAssets.Asset.LEVEL_COLLISION).nativeAddress())throw new HostBoundary(Boundary.LOAD_PRODUCT);image=new Image(this,image.globals(),image.scroll(),image.foreground(),image.background(),image.video(),image.clears(),image.geometryReady(),OptionalLong.of(p),image.fadeInfoWithoutTail(),image.rngSeedLong(),image.targetRightBoundWord(),image.sounds());}
   case TITLE_CARD_CREATE->{if(state.hudSlots().unsignedByte(192)!=0x3C)throw new HostBoundary(Boundary.LOAD_PRODUCT);frame.applyPreparedObjectSolidPublication(frame.prepareObjectTitleCardGlobal(frame.state(),frame.environment(),CdTitleCard.StoreField.CONTROL_LOCK_BYTE,1));}
   case BEGIN_MAIN->globals(image.globals().beginMain());
   case UPDATE_ANIMATED_TILES->{if(CdPowerupArtRuntime.gate(state.level().powerup()).copyRequired())throw new HostBoundary(Boundary.ACTIVE_POWERUP);}
   case ENABLE_FADE_AND_CLEAR_MARKER->{if(frame.environment().movement().gates().timeZone()!=2)throw new HostBoundary(Boundary.LOAD_PRODUCT);}
   case FROM_WHITE_INFO->globals(image.globals().fromWhite());
   case ENABLE_DISPLAY->enableSelected();
   case COPY_AND_DRAW->drawCopied();
   case CURRENT_ART->{var v=image.video();video(new Video(v.cpuSat(),v.completedCpu(),frame.artState(),state.section().residency(),v.selected()));refreshCurrentPicture();}
   case VINT_COMPLETED->authority.serviceVInt(this);
   case OLD_EXIT_FADE_SETUP->{if(state.phase()!=Phase.OLD_BLACK_FADE)throw new IllegalStateException("No retained black fade setup");setFadeInfo(0x003F);}
   case OLD_EXIT_SETTLE_CURRENT->{if(!owner.oldProgrammeSelected())throw new IllegalStateException("No retained exit picture");refreshCurrentPicture();}
  }bound();}finally{acting=false;}
 }
 private void clearScreen(State state){var plane=new CdR11aBackgroundPlane.State(zero(4096));var v=image.video();image=copy(image.globals(),image.scroll(),plane,plane,new Video(zero(640),Optional.empty(),v.playerArt(),v.current(),v.selected()),image.sounds());for(int i=0;i<4;i++)if(state.waterPalette().unsignedByte(i)!=0)throw new HostBoundary(Boundary.CLEAR_SCREEN_WATER_ALIAS);if(frame.state().header().slotId()!=0||frame.state().motion().spriteFlags()!=0||frame.state().header().tile()!=0)throw new HostBoundary(Boundary.CLEAR_SCREEN_PLAYER_ALIAS);}
 private void initialForeground(State state){if(!image.geometryReady())throw new HostBoundary(Boundary.GEOMETRY_NOT_LOADED);var s=image.scroll();var f=s.foreground();var bg=assets.view(CdR13FutureAssets.Asset.LAYOUT_BG);int cursor=64+bg.unsignedByte(1)*128+bg.unsignedByte(0)+1;var metadata=new CdR11bForegroundPlane.CallerMetadata(OptionalInt.of(state.layoutRam().orElseThrow().unsignedByte(cursor)));try{adoptForeground(foregroundWriter.initialize(image.foreground(),new CdR11aBackgroundPlane.Camera((f.cameraX()<<16)|s.foregroundXFractionWord(),f.cameraY16_16()),f.scrollFlags(),metadata),false);}catch(CdR11bForegroundPlane.InvalidPlane original){adoptForeground(original.completedPrefix(),false);throw original;}}
 private void initialBackground(){if(!image.geometryReady())throw new HostBoundary(Boundary.GEOMETRY_NOT_LOADED);var s=image.scroll();var result=backgroundWriter.initialize(image.background(),live(s.background()),flags(s.background()));image=copy(image.globals(),s,image.foreground(),result.state(),image.video(),image.sounds());}
 private void drawCopied(){var s=image.scroll();var c=s.copies();if(!image.geometryReady()&&((c.foregroundFlags()|c.backgroundFlags().bg1())&0xFF00)!=0)throw new HostBoundary(Boundary.GEOMETRY_NOT_LOADED);var bg=backgroundWriter.incremental(image.background(),live(s.background()),c.background(),c.backgroundFlags());var copies=new Copies(c.foregroundX16_16(),c.foregroundY16_16(),c.background(),c.foregroundFlags(),bg.flags());scrollValue(new Scroll(s.foreground(),s.background(),copies,s.foregroundXFractionWord(),s.topBoundWord()));image=copy(image.globals(),image.scroll(),image.foreground(),bg.state(),image.video(),image.sounds());int index=1;for(var read:bg.sections())if(read.phase()==CdR11aBackgroundPlane.Phase.INCREMENTAL_COLUMN)index=read.tableIndex()+1;var ids=planeSources.ids();var metadata=new CdR11bForegroundPlane.CallerMetadata(index>=0&&index<ids.length()?OptionalInt.of(ids.unsignedByte(index)):OptionalInt.empty());try{adoptForeground(foregroundWriter.incremental(image.foreground(),new CdR11aBackgroundPlane.Camera(copies.foregroundX16_16(),copies.foregroundY16_16()),copies.foregroundFlags(),metadata),true);}catch(CdR11bForegroundPlane.InvalidPlane original){adoptForeground(original.completedPrefix(),true);throw original;}}
 private void adoptForeground(CdR11bForegroundPlane.Result result,boolean consume){var s=image.scroll();var c=s.copies();if(consume)c=new Copies(c.foregroundX16_16(),c.foregroundY16_16(),c.background(),result.flagsWord(),c.backgroundFlags());image=copy(image.globals(),new Scroll(s.foreground(),s.background(),c,s.foregroundXFractionWord(),s.topBoundWord()),result.state(),image.background(),image.video(),image.sounds());}
 @Override public void publish(Publication selected){current();owner.requirePublication(this,selected);selectVscroll();if(selected.selector()==0x12){int water;try{water=CdCheckpointLiveAux.read(image.globals().live(),CdCheckpointLiveAux.Field.WATER_FULLSCREEN);}catch(CdCheckpointLiveAux.UnknownLiveByte missing){throw new HostBoundary(Boundary.WATER_FULLSCREEN);}if(water!=0)throw new HostBoundary(Boundary.WATER_PALETTE_PUBLICATION);}if(acting||publishing)throw new IllegalStateException("R13 publication reentry");publishing=true;try{var v=image.video();var s=image.scroll();var i=new Inputs(v.completedCpu(),v.cpuSat(),selected.normalPalette(),selectedHscroll(s.background().hscrollRam()),s.background().vscroll(),selected.displayEnabled(),image.foreground(),image.background(),selected.section().residency(),frame.artState());video(new Video(v.cpuSat(),v.completedCpu(),i.player(),i.current(),new Published(project(i))));bound();}finally{publishing=false;}}
 /** Every native selector8/C/12 copies exactly380 HScroll bytes; retain the genuine selected tail. */
 private CdAssetBytes selectedHscroll(CdAssetBytes live){
  CdAssetBytes previous=image.video().selected() instanceof Published p?p.selection().inputs().hscroll():CdSelectedHscroll.fromCarriedWords(((Carried)image.video().selected()).scene().packedHscroll256());
  return CdSelectedHscroll.copyNativePrefix(previous,live);
 }
 /** Pattern writes affect the retained picture without selecting another SAT/palette/scroll cut. */
 private void refreshCurrentPicture(){var v=image.video();if(v.selected() instanceof Published p){var old=p.selection().inputs();var i=new Inputs(old.selectedCpu(),old.selectedSat(),old.palette(),old.hscroll(),old.vscrollLongBits(),old.displayEnabled(),image.foreground(),image.background(),v.current(),v.playerArt());video(new Video(v.cpuSat(),v.completedCpu(),v.playerArt(),v.current(),new Published(project(i))));}}
 private static Selection project(Inputs i){return new Selection(i,SonicCdCurrentDisplay.project(i,id->{try{return CdR13ArtResidency.readTile(i.current(),id);}catch(CdR13ArtResidency.OwnershipBoundary f){throw new SonicCdCurrentDisplay.ReadStopped(new SonicCdPastDisplay.DisplayFailure(SonicCdPastDisplay.Boundary.valueOf(f.boundary().name()),-1,id,f.destinationWord()));}}));}
 private void ordinaryIdle(){current();if(acting||publishing)throw new IllegalStateException("R13 Host operation active");}
 private void ordinaryCut(CdOrdinaryLoop.Cut cut){ordinaryIdle();if(world==null)throw new IllegalStateException("No actual ordinary World");world.requireCut(cut);}
 /** VInterrupt683-695 selects VScroll before ReadControllers; no SAT/palette/HScroll reborrow. */
 private void selectVscroll(){var v=image.video();int bits=image.scroll().background().vscroll();Selected selected;if(v.selected() instanceof Published p){var o=p.selection().inputs();var i=new Inputs(o.selectedCpu(),o.selectedSat(),o.palette(),o.hscroll(),bits,o.displayEnabled(),o.foreground(),o.background(),o.current(),o.player());selected=new Published(project(i));}else{var o=((Carried)v.selected()).scene();selected=new Carried(new RetainedPlaneSceneRenderer.Scene(o.foreground(),o.background(),o.packedHscroll256(),bits,o.selectedPalette128(),o.displayEnabled(),o.sprites(),o.nativePatterns(),o.selectedBackdropPaletteIndex()));}video(new Video(v.cpuSat(),v.completedCpu(),v.playerArt(),v.current(),selected));}
 Optional<CdObjectDrawQueue.CpuSat> beforeSelector8(){ordinaryCut(CdOrdinaryLoop.Cut.BEFORE_SELECTOR8);selectVscroll();return image.video().completedCpu();}
 void publishOrdinary(CdAssetBytes palette,Optional<CdObjectDrawQueue.CpuSat> cpu){ordinaryCut(CdOrdinaryLoop.Cut.PUBLISH_LEVEL);var v=image.video();if(cpu!=v.completedCpu()||palette!=owner.state().normalPalette())throw new IllegalArgumentException("Selected CPU/palette is not current cut");var old=inputs(v.selected());var h=CdSelectedHscroll.copyNativePrefix(old.hscroll(),image.scroll().background().hscrollRam());var s=image.scroll();var i=new Inputs(cpu,v.cpuSat(),palette,h,s.background().vscroll(),owner.state().displayEnabled(),image.foreground(),image.background(),v.current(),frame.artState());video(new Video(v.cpuSat(),v.completedCpu(),i.player(),i.current(),new Published(project(i))));}
 void settleOrdinary(Optional<CdObjectDrawQueue.CpuSat> cpu){ordinaryCut(CdOrdinaryLoop.Cut.SETTLE_SELECTED_CPU);var v=image.video();var old=inputs(v.selected());if(cpu!=old.selectedCpu())throw new IllegalArgumentException("Selected CPU replaced");var current=owner.state().section().residency();var i=new Inputs(cpu,old.selectedSat(),old.palette(),old.hscroll(),old.vscrollLongBits(),old.displayEnabled(),image.foreground(),image.background(),current,frame.artState());video(new Video(v.cpuSat(),v.completedCpu(),i.player(),current,new Published(project(i))));}
 private static Inputs inputs(Selected s){if(s instanceof Published p)return p.selection().inputs();throw new HostBoundary(Boundary.SELECTED_DISPLAY);}
 // BEGIN IPX BOSS RETURN HOST 20261107
 interface IpxProgressStore{CdIpxProgress.Result result();void validate();void adoptAfterNative();}
 static final class IpxBossReturn {
  private final Image before;private final IpxProgressStore progress;private boolean applied;
  private IpxBossReturn(Image before,IpxProgressStore progress){this.before=before;this.progress=progress;}
  CdIpxProgress.Result result(){return progress.result();}boolean ownsProgress(IpxProgressStore actual){return progress==actual;}
 }
 private boolean preparingIpxBossReturn;
 Optional<IpxBossReturn> prepareIpxBossReturn(SonicCdAct3FutureLoadingVisit.IpxBossReturnCall call){
  current();owner.requireIpxBossReturnPreparation(this,call);
  if(preparingIpxBossReturn||acting||publishing)throw new IllegalStateException("IPX Host preparation active");
  if(!(call.hostSnapshot() instanceof Snapshot captured)||captured.image()!=image)throw new IllegalStateException("Not the original full Host image");
  var before=image;preparingIpxBossReturn=true;
  try{var progress=Objects.requireNonNull(authority.prepareIpxBossReturn(this,call));return progress.map(p->new IpxBossReturn(before,p));}
  finally{preparingIpxBossReturn=false;}
 }
 void requireIpxBossAuthorityPreparation(SonicCdAct3FutureLoadingVisit.IpxBossReturnCall call){
  if(!preparingIpxBossReturn)throw new IllegalStateException("No actual Host-to-Module IPX preparation");owner.requireIpxBossReturnPreparation(this,call);
 }
 void validateIpxBossReturn(IpxBossReturn p){
  current();owner.requireIpxBossReturnCandidate(this,p);if(p.applied||p.before!=image)throw new IllegalStateException("Stale complete Host IPX pair");p.progress.validate();
 }
 void adoptIpxBossReturnAfterNative(IpxBossReturn p){
  owner.requireIpxBossReturnCommit(this,p);if(p.applied||p.before!=image)throw new IllegalStateException("Original Host IPX pair expired");
  p.progress.adoptAfterNative();p.applied=true; // no Host scalar/image/pool/bank/selected/current write exists here
 }
 OptionalInt runR1UnlockByte(){current();owner.requireRunR1UnlockRead(this);return Objects.requireNonNull(authority.runR1UnlockByte(this));}
 interface IpxRunR1Store {
  CdIpxProgress.Result result();void validate();void adoptHeader();void adoptHistory();
 }
 static final class IpxRunR1 {
  private final Image before,ready;private final IpxRunR1Store progress;private int step;
  private IpxRunR1(Image before,Image ready,IpxRunR1Store progress){this.before=before;this.ready=ready;this.progress=progress;}
  CdIpxProgress.Result result(){return progress.result();}boolean owns(IpxRunR1Store actual){return actual==progress;}
 }
 private boolean preparingRunR1;
 Optional<IpxRunR1> prepareRunR1(SonicCdAct3FutureLoadingVisit.IpxRunR1Call call){
  current();owner.requireRunR1Preparation(this,call);
  if(preparingRunR1||acting||publishing)throw new IllegalStateException("RunR1 preparation active");
  if(!(call.hostSnapshot() instanceof Snapshot captured)||captured.image()!=image)throw new IllegalStateException("Not original full RunR1 Host cut");
  var before=image;preparingRunR1=true;
  try{
   var store=Objects.requireNonNull(authority.prepareRunR1(this,call));if(store.isEmpty())return Optional.empty();
   var p=store.orElseThrow();var ready=before;var projector=p.result().writes().projectorByte();
   if(projector.isPresent()){var g=before.globals();var next=new Globals(g.ring(),g.live(),g.contact(),g.score(),projector,g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),g.pauseMusicBit(),g.bonus(),g.tail(),g.animations(),g.paletteSteps7(),g.paletteTimers7(),g.layerByte(),g.bossFlagsByte(),g.eventRoutineWord());ready=copy(next,before.scroll(),before.foreground(),before.background(),before.video(),before.sounds());}
   return Optional.of(new IpxRunR1(before,ready,p));
  }finally{preparingRunR1=false;}
 }
 void requireRunR1AuthorityPreparation(SonicCdAct3FutureLoadingVisit.IpxRunR1Call call){if(!preparingRunR1)throw new IllegalStateException("No Host-to-Module RunR1 preparation");owner.requireRunR1Preparation(this,call);}
 void validateRunR1(IpxRunR1 p){current();owner.requireRunR1Candidate(this,p);if(p.step!=0||p.before!=image)throw new IllegalStateException("Stale RunR1 Host image");p.progress.validate();}
 void adoptRunR1Header(IpxRunR1 p){owner.requireRunR1Commit(this,p);if(p.step!=0||image!=p.before)throw new IllegalStateException("RunR1 header expired");p.progress.adoptHeader();p.step=1;}
 void adoptRunR1Projector(IpxRunR1 p){owner.requireRunR1Commit(this,p);if(p.step!=1||image!=p.before)throw new IllegalStateException("RunR1 projector expired");image=p.ready;p.step=2;}
 void adoptRunR1History(IpxRunR1 p){owner.requireRunR1Commit(this,p);if(p.step!=2||image!=p.ready)throw new IllegalStateException("RunR1 flags expired");p.progress.adoptHistory();p.step=3;}
 // END IPX BOSS RETURN HOST 20261107

 // Original private Core blank scope retains this complete prebuild; public values cannot adopt it.
 Prepared prepareOldExitBlank(){
  current();owner.requireOldExitBlankPreparation(this);if(acting||publishing)throw new IllegalStateException("R13 Host active");
  var before=image;var state=owner.state();var v=image.video();var old=inputs(v.selected());var i=new Inputs(old.selectedCpu(),old.selectedSat(),old.palette(),old.hscroll(),old.vscrollLongBits(),false,old.foreground(),old.background(),old.current(),old.player());
  var selected=new Published(project(i));var sounds=new ArrayList<>(image.sounds());sounds.add(new SonicCdGameModule.PastProgrammeSound(SonicCdGameModule.ProgramSound.STOP_FM));
  return new Prepared(before,copy(image.globals(),image.scroll(),image.foreground(),image.background(),new Video(v.cpuSat(),v.completedCpu(),v.playerArt(),v.current(),selected),sounds),state);
 }
 void validateOldExitBlank(Prepared p){current();owner.requireOldExitBlankCandidate(this,p);if(p.before!=image||p.scope!=owner.state())throw new IllegalStateException("Stale complete IPX Core/Host prebuild");}
 void adoptOldExitBlankAfterNative(Prepared p){owner.requireOldExitBlankCommit(this,p);if(p.before!=image)throw new IllegalStateException("Original IPX Host pair expired");image=p.after;}
 private void enableSelected(){var v=image.video();var old=inputs(v.selected());var i=new Inputs(old.selectedCpu(),old.selectedSat(),old.palette(),old.hscroll(),old.vscrollLongBits(),true,old.foreground(),old.background(),old.current(),old.player());video(new Video(v.cpuSat(),v.completedCpu(),v.playerArt(),v.current(),new Published(project(i))));}
 void copyAndDrawOrdinary(){ordinaryCut(CdOrdinaryLoop.Cut.COPY_CAMERA_DRAW_LEVEL);var s=image.scroll();var f=s.foreground();var live=live(s.background());scrollValue(new Scroll(f,s.background(),new Copies((f.cameraX()<<16)|s.foregroundXFractionWord(),f.cameraY16_16(),new CdR11aBackgroundPlane.CopiedCameras(live.bg1(),live.bg2(),live.bg3()),f.scrollFlags(),flags(s.background())),s.foregroundXFractionWord(),s.topBoundWord()));drawCopied();}
 void scrollOrdinary(int hudTime){ordinaryCut(CdOrdinaryLoop.Cut.POST_OBJECT_CAMERA);octet(hudTime);var s=image.scroll();var f=s.foreground();if((f.scrollLock()&0xFF00)!=0)return;var b=s.background();scrollValue(new Scroll(foreground(f,0,f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift()),bg(b,0,0,0),s.copies(),s.foregroundXFractionWord(),s.topBoundWord()));applyEvents();s=image.scroll();var m=frame.state().motion();var e=frame.environment();var input=new CdR11aForegroundScroll.Input(new CdR11aForegroundScroll.Actor(m.centreX16_16()>>>16,m.centreY16_16()>>>16,m.flags(),m.groundVelocity(),m.animation(),m.playerControl()),e.movement().bounds(),s.topBoundWord(),e.globals().cameraXCentre(),e.movement().globals().cameraYCentre(),frame.stageSource().variant(),hudTime,320,224);var out=scrollRules.foregroundAfterEvents(s.foreground(),input);frame.applyScrollFeedback(out.bounds(),out.cameraYCentre());f=out.state();scrollValue(new Scroll(f,s.background(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord()));s=image.scroll();b=scrollRules.background(s.background(),new CdR13FutureScrollPalette.BackgroundInput(f.cameraX(),f.cameraYWord(),(short)f.scrollXDiff(),(short)f.scrollYDiff(),f.scrollLock()>>>8));scrollValue(new Scroll(f,b,s.copies(),s.foregroundXFractionWord(),s.topBoundWord()));}
 @Override public void events(State state){current();owner.requireEvents(this,state);applyEvents();}
 private void applyEvents(){var m=frame.state().motion();var s=image.scroll();var p=eventPlan(s.foreground(),frame.environment().movement().bounds(),image.globals().bossFlagsByte().orElseThrow(()->new HostBoundary(Boundary.BOSS_FLAGS)),m.centreX16_16()>>>16,m.flags(),m.animation());scrollValue(new Scroll(p.foreground(),s.background(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord()));frame.applyScrollFeedback(p.bounds(),frame.environment().movement().globals().cameraYCentre());}
 record EventPlan(CdR11aForegroundScroll.State foreground,CdPlayerMovement.Bounds bounds){}
 /** Pure native WORD arithmetic; controls cannot grant an event invocation or Frame store. */
 static EventPlan eventPlan(CdR11aForegroundScroll.State f,CdPlayerMovement.Bounds b,int bossFlags,int x,int flags,int animation){
  Objects.requireNonNull(f);Objects.requireNonNull(b);octet(bossFlags);word(x);octet(flags);octet(animation);int dest=f.destBottomBound(),left=b.left(),destLeft=f.destLeftBound(),bottom=b.bottom(),shift=f.bottomBoundShift();
  if(bossFlags==0){dest=0x310;if((short)x>=(short)0xD70){int diff=(short)(dest-bottom);if(diff<0)diff=(short)-diff;if(diff<4)bottom=dest;int target=(x-160)&65535;if((short)target>=(short)left){if((short)target>(short)b.right())target=b.right();left=destLeft=target;}}}
  if(animation==0x2B){bottom=dest=f.cameraYWord();}
  else if(dest!=bottom){if(Integer.compareUnsigned(dest,bottom)<0){if(Integer.compareUnsigned(f.cameraYWord(),dest)>0)bottom=f.cameraYWord()&0xFFFE;bottom=(bottom-4)&65535;}else{int speed=4;if(Integer.compareUnsigned((f.cameraYWord()+8)&65535,bottom)>=0&&(flags&2)!=0)speed=16;bottom=(bottom+speed)&65535;}shift=(shift&255)|0x100;}
  return new EventPlan(foreground(f,f.scrollFlags(),dest,destLeft,shift),new CdPlayerMovement.Bounds(left,b.right(),bottom,b.bossFight()));
 }
 static Globals with(Globals g,CdRingGlobals ring,CdCheckpointLiveAux.State live,CdPlayerBody.ContactGlobals contact,CdOpeningObjectRuntime.ScoreGlobals score,OptionalInt pause,Optional<CdLevelHud.BonusValues> bonus,Optional<CdGlobalObjectAnimation.Extras> animations,CdAssetBytes steps,CdAssetBytes timers){return new Globals(ring,live,contact,score,g.projectorByte(),g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),pause,bonus,g.tail(),animations,steps,timers,g.layerByte(),g.bossFlagsByte(),g.eventRoutineWord());}
 Prepared prepareOrdinary(Globals expected,Video video,Globals next,List<SonicCdGameModule.PastSound> append,Optional<CdObjectDrawQueue.CpuSat> cpu){ordinaryIdle();if(world==null)throw new IllegalStateException("No original ordinary owner");var scope=world.actualScope();if(expected!=image.globals()||video!=image.video())throw new IllegalStateException("Stale complete Host pair");Objects.requireNonNull(next);var v=video;if(cpu!=v.completedCpu()){if(cpu.isEmpty())throw new IllegalArgumentException("Cannot erase actual CPU provenance");v=new Video(CdAssetBytes.copyOf(cpu.orElseThrow().bytes()),cpu,v.playerArt(),v.current(),v.selected());}var sounds=new ArrayList<>(image.sounds());sounds.addAll(List.copyOf(append));return new Prepared(image,copy(next,image.scroll(),image.foreground(),image.background(),v,sounds),scope);}
 Prepared prepareScrollLock(Prepared prepared,int scrollLock){validateOrdinary(prepared);word(scrollLock);var a=prepared.after;var s=a.scroll();var f=s.foreground();var fg=new CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift(),scrollLock);return new Prepared(prepared.before,new Image(this,a.globals(),new Scroll(fg,s.background(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord()),a.foreground(),a.background(),a.video(),a.clears(),a.geometryReady(),a.collisionPointer(),a.fadeInfoWithoutTail(),a.rngSeedLong(),a.targetRightBoundWord(),a.sounds()),prepared.scope);}
 // BEGIN R13 GAME OVER HOST OWNER JOIN 20261107
 SonicCdAct3FutureObjectPass.GameOverOwners readGameOverOwners(SonicCdAct3FutureObjectPass pass){
  current();world.requireObjectPass(pass);if(!pass.isCurrentGameOverOwnersRead())throw new IllegalStateException("No original GameOver owner read");var core=owner.state();return new SonicCdAct3FutureObjectPass.GameOverOwners(image,core,core.level());
 }
 Prepared prepareGameOverScalar(SonicCdAct3FutureObjectPass.GameOverStore actual,State expected,CdLevelGlobals next){
  ordinaryCut(CdOrdinaryLoop.Cut.RUN_OBJECTS);world.requireObjectPass(actual.owner());actual.validateBefore();var old=expected.level();var call=actual.call();
  if(actual.expected().hostFence()!=image||actual.expected().coreFence()!=expected||owner.state()!=expected||actual.expected().level()!=old)throw new IllegalArgumentException("Stale complete GameOver Core/Host fence");
  var required=new CdLevelGlobals(old.levelRestart(),old.updateHudTime(),old.checkpoint(),old.spawnMode(),old.plcLoadFlags(),old.flowerCount(),old.fadeEnableDisplay(),call.field()==com.openggf.game.soniccd.objects.CdGameOver.Scalar.POWERUP?call.value():old.powerup(),call.field()==com.openggf.game.soniccd.objects.CdGameOver.Scalar.TIME_OVER?call.value():old.timeOver());
  if(!required.equals(next))throw new IllegalArgumentException("GameOver changed another native level field");
  return prepareOrdinary(image.globals(),image.video(),image.globals(),List.of(),image.video().completedCpu());
 }
 void validateGameOverScalar(Prepared prepared,SonicCdAct3FutureObjectPass.GameOverStore actual,State expected){
  validateOrdinary(prepared);actual.validateBefore();if(prepared.before!=image||owner.state()!=expected||actual.expected().hostFence()!=image||actual.expected().coreFence()!=expected||world==null||!world.isCurrentScope(prepared.scope))throw new IllegalStateException("Original GameOver pair expired during preparation");
 }
 // END R13 GAME OVER HOST OWNER JOIN 20261107
 // BEGIN R13 LEVEL MAIN DEATH CAMERA HOST JOIN 20261107
 /** Native Main413-415. Preserve the full Host image except destBottomBound; this
  * private post-walk scope, not a public value/receipt, owns the matching Frame store. */
 Prepared prepareLevelMainDeathCamera(int expectedCameraYWord){
  ordinaryCut(CdOrdinaryLoop.Cut.POST_OBJECT_CAMERA);word(expectedCameraYWord);
  var state=owner.state();var e=frame.environment();
  if(state.program().demoDataIndexWord()==0x800||state.level().levelRestart()!=0||frame.paused()||e.globals().debugMode()!=0||frame.state().motion().routine()<6)throw new IllegalStateException("Native death-camera branch not reached");
  var before=image;var s=before.scroll();var f=s.foreground();if(f.cameraYWord()!=expectedCameraYWord)throw new IllegalArgumentException("Stale actual cameraY WORD");
  var fg=CdPrimaryLifecycle.freezeDestination(f,expectedCameraYWord);
  var scroll=fg==f?s:new Scroll(fg,s.background(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord());
  var after=new Image(this,before.globals(),scroll,before.foreground(),before.background(),before.video(),before.clears(),before.geometryReady(),before.collisionPointer(),before.fadeInfoWithoutTail(),before.rngSeedLong(),before.targetRightBoundWord(),before.sounds());
  return new Prepared(before,after,world.actualScope());
 }
 /** Final full-image/scope recheck after validateOrdinary's last authority read. No callback
  * follows these checks; callers retain actual placement/Frame references for their final CAS. */
 void validatePrimaryDeathPublication(Prepared prepared){validateOrdinary(prepared);if(prepared.before!=image||world==null||!world.isCurrentScope(prepared.scope))throw new IllegalStateException("Primary death Host pair changed during validation");}
 // END R13 LEVEL MAIN DEATH CAMERA HOST JOIN 20261107
 // BEGIN R13 BOSS HOST IMAGE JOIN 20261106
 SonicCdAct3FutureObjectPass.BossOwners readBossOwners(SonicCdAct3FutureObjectPass pass){
  current();if(world==null)throw new IllegalStateException("No actual Boss World");world.requireObjectPass(pass);
  if(!pass.isCurrentBossOwnersRead())throw new IllegalStateException("No original Boss owner read");
  var f=image.scroll().foreground();var core=owner.state();return new SonicCdAct3FutureObjectPass.BossOwners(image,core,image.globals().bossFlagsByte(),f.destBottomBound(),f.destLeftBound(),image.targetRightBoundWord(),image.globals().score(),core.normalPalette());
 }
 Prepared prepareBossOwners(Prepared prepared,SonicCdAct3FutureObjectPass pass,SonicCdAct3FutureObjectPass.BossOwners expected,SonicCdAct3FutureObjectPass.BossValues next){
  validateOrdinary(prepared);world.requireObjectPass(pass);if(!pass.isCurrentBossOwnerPublication(expected,next)||expected.hostFence()!=image||expected.coreFence()!=owner.state())throw new IllegalArgumentException("No original full Boss owner fence");
  var f=image.scroll().foreground();if(!expected.bossFlagsByte().equals(image.globals().bossFlagsByte())||expected.targetBottomWord()!=f.destBottomBound()||expected.targetLeftWord()!=f.destLeftBound()||!expected.targetRightWord().equals(image.targetRightBoundWord())||!expected.score().equals(image.globals().score())||!Arrays.equals(expected.normalPalette().copy(),owner.state().normalPalette().copy()))throw new IllegalArgumentException("Stale Boss projections");
  var a=prepared.after;var g=a.globals();if(!next.score().equals(g.score()))throw new IllegalArgumentException("Boss score was not prepared by actual World");
  var globals=new Globals(g.ring(),g.live(),g.contact(),g.score(),g.projectorByte(),g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),g.pauseMusicBit(),g.bonus(),g.tail(),g.animations(),g.paletteSteps7(),g.paletteTimers7(),g.layerByte(),next.bossFlagsByte(),g.eventRoutineWord());
  var old=a.scroll();var fg=old.foreground();var foreground=new CdR11aForegroundScroll.State(fg.cameraX(),fg.cameraY16_16(),fg.horizontalCrossed(),fg.verticalCrossed(),fg.scrollXDiff(),fg.scrollYDiff(),fg.scrollFlags(),next.targetBottomWord(),next.targetLeftWord(),fg.bottomBoundShift(),fg.scrollLock());
  var scroll=new Scroll(foreground,old.background(),old.copies(),old.foregroundXFractionWord(),old.topBoundWord());
  return new Prepared(prepared.before,new Image(this,globals,scroll,a.foreground(),a.background(),a.video(),a.clears(),a.geometryReady(),a.collisionPointer(),a.fadeInfoWithoutTail(),a.rngSeedLong(),next.targetRightWord(),a.sounds()),prepared.scope);
 }
 /** Strict owning PLC prefix check. Only actual Core art publication may replace Video.current;
  * all other full Host partitions remain the exact old references/values. No setter/continuation. */
 void validateBossPlcPrefix(SonicCdAct3FutureObjectPass.BossPlcSubmission call,SonicCdAct3FutureLoadingVisit.State after){
  current();world.requireObjectPass(call.owner());if(!call.owner().isCurrentBossPlcSubmission(call)||after!=owner.state()||!(call.expectedOwners().hostFence() instanceof Image before)||before.owner()!=this)throw new IllegalStateException("Not original Boss PLC prefix");
  var a=image;var b=before.video();var v=a.video();if(a.owner()!=before.owner()||a.globals()!=before.globals()||a.scroll()!=before.scroll()||a.foreground()!=before.foreground()||a.background()!=before.background()||!a.clears().equals(before.clears())||a.geometryReady()!=before.geometryReady()||!a.collisionPointer().equals(before.collisionPointer())||!a.fadeInfoWithoutTail().equals(before.fadeInfoWithoutTail())||!a.rngSeedLong().equals(before.rngSeedLong())||!a.targetRightBoundWord().equals(before.targetRightBoundWord())||!a.sounds().equals(before.sounds())||v.cpuSat()!=b.cpuSat()||v.completedCpu()!=b.completedCpu()||v.playerArt()!=b.playerArt()||!bossPlcSelectedPrefix(b.selected(),v.selected(),a,v)||v.current()!=after.section().residency())throw new IllegalStateException("Boss PLC changed an unowned complete Host image");
 }
 private static boolean bossPlcSelectedPrefix(Selected before,Selected after,Image image,Video video){
  if(before==after)return true;if(!(before instanceof Published b)||!(after instanceof Published a))return false;var x=b.selection().inputs();var y=a.selection().inputs();
  // CURRENT_ART refreshes only the actual current/Frame-art/live-plane participants; it does
  // not select another CPU SAT, palette, scroll, display gate or historical HScroll tail.
  return y.selectedCpu()==x.selectedCpu()&&y.selectedSat()==x.selectedSat()&&y.palette()==x.palette()&&y.hscroll()==x.hscroll()&&y.vscrollLongBits()==x.vscrollLongBits()&&y.displayEnabled()==x.displayEnabled()&&y.foreground()==image.foreground()&&y.background()==image.background()&&y.current()==video.current()&&y.player()==video.playerArt();
 }
 // END R13 BOSS HOST IMAGE JOIN 20261106
 // BEGIN R13 CAPSULE RESULTS HOST JOIN 20261106
 interface ResultsFlagsStore{void validate();void adoptAfterNative();}
 static final class FlagsReady{private final SonicCdAct3FutureObjectPass pass;private final SonicCdAct3FutureObjectPass.Adoption publication;private final Image before;private final State core;private final ResultsFlagsStore nativeStore;private boolean applied;private FlagsReady(SonicCdAct3FutureObjectPass p,SonicCdAct3FutureObjectPass.Adoption a,Image i,State c,ResultsFlagsStore store){pass=p;publication=a;before=i;core=c;nativeStore=store;}}
 SonicCdAct3FutureObjectPass.EndOwners readEndOwners(SonicCdAct3FutureObjectPass pass){
  current();world.requireObjectPass(pass);if(!pass.isCurrentEndOwnersRead())throw new IllegalStateException("No original Capsule/Results owner read");var old=image;var core=owner.state();var flags=Objects.requireNonNull(authority.resultsFlags(this,pass));if(image!=old||owner.state()!=core)throw new IllegalStateException("Programme flags read changed full Host/Core image");var g=image.globals();return new SonicCdAct3FutureObjectPass.EndOwners(image,core,core.level(),g.live(),g.bonus(),g.score(),g.tail().isPresent()?OptionalInt.of(g.tail().orElseThrow().unknownLevelByte()):OptionalInt.empty(),core.normalPalette(),core.program().specialStageByte(),core.program().timeStonesByte(),flags);
 }
 Prepared prepareEndOwners(Prepared prepared,SonicCdAct3FutureObjectPass pass,SonicCdAct3FutureObjectPass.Adoption actual){
  validateOrdinary(prepared);world.requireObjectPass(pass);var expected=actual.endExpected();var next=actual.endNext();if(expected==null||next==null||!pass.isCurrentEndOwnerPublication(expected,next)||expected.hostFence()!=image||expected.coreFence()!=owner.state())throw new IllegalArgumentException("No original full Capsule/Results owner fence");var g=image.globals();if(expected.level()!=owner.state().level()||expected.live()!=g.live()||!expected.bonus().equals(g.bonus())||!expected.score().equals(g.score())||expected.normalPalette()!=owner.state().normalPalette())throw new IllegalArgumentException("Stale Capsule/Results projections");
  var a=prepared.after;var ready=a.globals();var tail=ready.tail();if(!next.unknownLevelByte().equals(expected.unknownLevelByte())){var t=tail.orElseThrow(()->new IllegalStateException("Actual Results tail owner missing"));tail=Optional.of(new Tail(t.player2ControlsWord(),t.player2BoredWord(),next.unknownLevelByte().orElseThrow(),t.demoS1IndexWord(),t.paletteFadeInfoWord()));}
  var globals=new Globals(ready.ring(),ready.live(),ready.contact(),next.score(),actual.next().projectorByte(),ready.checkpointSaved(),ready.drownTimerWord(),ready.debugBlockWord(),ready.pauseMusicBit(),next.bonus(),tail,ready.animations(),ready.paletteSteps7(),ready.paletteTimers7(),ready.layerByte(),ready.bossFlagsByte(),ready.eventRoutineWord());return new Prepared(prepared.before,new Image(this,globals,a.scroll(),a.foreground(),a.background(),a.video(),a.clears(),a.geometryReady(),a.collisionPointer(),a.fadeInfoWithoutTail(),a.rngSeedLong(),a.targetRightBoundWord(),a.sounds()),prepared.scope);
 }
 FlagsReady prepareEndFlags(SonicCdAct3FutureObjectPass pass,SonicCdAct3FutureObjectPass.Adoption actual){
  current();world.requireObjectPass(pass);if(!pass.isCurrentEndFlagsPublication(actual))throw new IllegalStateException("No original pending Results flags store");var before=image;var core=owner.state();var expected=actual.endExpected().flags().orElseThrow(actual::unownedEndFlags);var value=actual.endNext().flagsByte().orElseThrow();var store=Objects.requireNonNull(authority.prepareResultsFlags(this,pass,actual,expected,value));if(image!=before||owner.state()!=core)throw new IllegalStateException("Flags preparation assigned an owner");actual.validateBefore();return new FlagsReady(pass,actual,before,core,store);
 }
 void validateEndFlags(FlagsReady ready){current();world.requireObjectPass(ready.pass);if(ready.applied||ready.before!=image||ready.core!=owner.state()||!ready.pass.isCurrentEndFlagsPublication(ready.publication))throw new IllegalArgumentException("Stale original Results flags pair");ready.nativeStore.validate();ready.publication.validateBefore();}
 /** Caller has prevalidated every aggregate. The Module implementation may only assign its prepared
  * full programme/knowledge image here: no callbacks, fresh reads, allocation or thrown work. */
 void adoptEndFlagsAfterNative(FlagsReady ready){if(ready.applied||ready.before!=image||ready.core!=owner.state()||!ready.pass.isCurrentEndOperation(ready.publication.endCall()))throw new IllegalStateException("Original Results flags pair expired");ready.nativeStore.adoptAfterNative();ready.applied=true;}
 void validateEndPlcPrefix(SonicCdAct3FutureObjectPass.EndPlcSubmission call,State after){
  current();world.requireObjectPass(call.owner());if(!call.owner().isCurrentResultsPlcSubmission(call)||after!=owner.state()||!(call.expectedOwners().hostFence() instanceof Image before)||before.owner()!=this)throw new IllegalStateException("Not original Results PLC prefix");var a=image;var b=before.video();var v=a.video();if(a.globals()!=before.globals()||a.scroll()!=before.scroll()||a.foreground()!=before.foreground()||a.background()!=before.background()||!a.clears().equals(before.clears())||a.geometryReady()!=before.geometryReady()||!a.collisionPointer().equals(before.collisionPointer())||!a.fadeInfoWithoutTail().equals(before.fadeInfoWithoutTail())||!a.rngSeedLong().equals(before.rngSeedLong())||!a.targetRightBoundWord().equals(before.targetRightBoundWord())||!a.sounds().equals(before.sounds())||v.cpuSat()!=b.cpuSat()||v.completedCpu()!=b.completedCpu()||v.playerArt()!=b.playerArt()||!bossPlcSelectedPrefix(b.selected(),v.selected(),a,v)||v.current()!=after.section().residency())throw new IllegalStateException("Results PLC changed unowned full Host fields");
 }
 // END R13 CAPSULE RESULTS HOST JOIN 20261106
 void validateOrdinary(Prepared p){ordinaryIdle();if(Objects.requireNonNull(p).before!=image||world==null||p.scope!=world.actualScope())throw new IllegalStateException("Stale/foreign original Host preparation");}
 void adoptOrdinary(Prepared p){validateOrdinary(p);image=p.after;}
 void adoptAfterNative(Prepared p){if(p.before!=image||world==null||!world.isCurrentScope(p.scope))throw new IllegalStateException("Original Host pair expired");image=p.after;}
 void serviceVInt(){ordinaryCut(CdOrdinaryLoop.Cut.MAIN_MUSIC);authority.serviceVInt(this);}
    void projectCamera(Camera camera) {
        current();Objects.requireNonNull(camera);var b=frame.environment().movement().bounds();var f=image.scroll().foreground();
        camera.setMinX((short)b.left());camera.setMaxX((short)b.right());camera.setMinY((short)image.scroll().topBoundWord());camera.setMaxY((short)b.bottom());
        camera.setVerticalWrapEnabled(false);camera.setX((short)f.cameraX());camera.setY((short)f.cameraYWord());camera.setShakeOffsets(0,0);
    }
    private final class Projection implements ZoneScrollHandler,com.openggf.level.scroll.ScrollOutputRebuilder {
        @Override public void rebuildOutput(int[] output,int cameraX,int cameraY,int frameCounter,int actId) {
            // Generic logical act indices do not select the native stage. This exact Host already
            // owns R13; project its restored HScroll without a native init/update dispatch.
            projectOutput(output);
        }
        @Override public void update(int[] output,int cameraX,int cameraY,int frameCounter,int actId) {
            if(actId!=2)throw new IllegalArgumentException("R13 act2 required");projectOutput(output);
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
        @Override public Object captureRewindState(){current();return new ScrollBinding(SonicCdAct3FutureWorldHost.this,owner);}
        @Override public void restoreRewindState(Object snapshot){
            current();if(!(snapshot instanceof ScrollBinding b)||b.owner()!=SonicCdAct3FutureWorldHost.this||b.visit()!=owner)
                throw new IllegalArgumentException("Foreign or retired R13 scroll binding");
            // Observational lifetime token only. Core Snapshot already restores this ONE Host image.
        }
    }

    /** Logic-only callers need no GL. Real drawing lazily borrows the existing governed renderer. */
    void draw() {
        if(world!=null&&world.isOrdinaryRunning())throw new IllegalStateException("World service active");
        current();if(acting||publishing)throw new IllegalStateException("R13 loading operation active");frame.captureShared();
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
            throw new IllegalStateException("R13 actual World/viewport changed before renderer allocation");
        var renderer=new RetainedPlaneSceneRenderer(graphics,PatternAtlasRange.OBJECTS);
        return new RenderBinding(mode,world,world.getDataSource(),levels,levels.getCurrentLevel(),levels.getCompletedProductionLoadGeneration(),
                camera,graphics,graphics.getPatternAtlas(),GL.getCapabilities(),Thread.currentThread(),System.getProperty("openggf.headless.gl"),renderer);
    }
    private void requireRender(RenderBinding b,Selected selected) {
        if(!drawing||closed||closing||acting||publishing||image.video().selected()!=selected)
            throw new IllegalStateException("R13 selected draw was reentered or changed");
        bound();var mode=SessionManager.getCurrentGameplayMode();
        if(b==null||b!=render||mode!=b.mode()||mode.getWorldSession()!=b.world()||b.world().getDataSource()!=b.data()||mode.getLevelManager()!=b.levels()||GameServices.level()!=b.levels()
                ||b.levels().getCurrentLevel()!=b.level()||b.levels().getCompletedProductionLoadGeneration()!=b.generation()
                ||mode.getCamera()!=b.camera()||GameServices.camera()!=b.camera()||b.camera().getWidth()!=320||b.camera().getHeight()!=224
                ||GameServices.graphics()!=b.graphics()||!b.graphics().isGlInitialized()||b.graphics().isHeadlessMode()
                ||b.graphics().getPatternAtlas()!=b.atlas()||Thread.currentThread()!=b.thread()
                ||!Objects.equals(System.getProperty("openggf.headless.gl"),b.backend())||GL.getCapabilities()!=b.capabilities())
            throw new IllegalStateException("R13 retained source/World/level/generation/graphics lifetime changed");
    }
    @Override public void close() {
        if(world!=null&&world.isOrdinaryRunning())throw new IllegalStateException("World service active");
        requireRenderIdle();
        if(closed)return;if(closing||acting||publishing)throw new IllegalStateException("R13 Host operation active");
        closing=true;
        try {if(render!=null){render.renderer().close();render=null;}closed=true;}
        finally {closing=false;}
    }
    private static boolean samePlayer(CdPlayerArtRuntime.State a,CdPlayerArtRuntime.State b) {
        return a.lastFrame()==b.lastFrame()&&a.dirty()==b.dirty()&&Arrays.equals(a.cpuBuffer().copy(),b.cpuBuffer().copy())&&Arrays.equals(a.residentBank().copy(),b.residentBank().copy());
    }

 private static CdR11aBackgroundPlane.LiveCameras live(CdR11aBackgroundScroll.State b){return new CdR11aBackgroundPlane.LiveCameras(new CdR11aBackgroundPlane.Camera(b.cameraBgX(),b.cameraBgY()),new CdR11aBackgroundPlane.Camera(b.cameraBg2X(),b.cameraBg2Y()),new CdR11aBackgroundPlane.Camera(b.cameraBg3X(),b.cameraBg3Y()));}
 private static CdR11aBackgroundPlane.FlagWords flags(CdR11aBackgroundScroll.State b){return new CdR11aBackgroundPlane.FlagWords(b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3());}
 private static CdR11aBackgroundScroll.State bg(CdR11aBackgroundScroll.State b,int a,int c,int d){return new CdR11aBackgroundScroll.State(b.cameraBgX(),b.cameraBgY(),b.cameraBg2X(),b.cameraBg2Y(),b.cameraBg3X(),b.cameraBg3Y(),b.crossingRam(),a,c,d,b.cloudRam(),b.deformationRam(),b.hscrollRam(),b.vscroll());}
 private static CdR11aForegroundScroll.State foreground(CdR11aForegroundScroll.State f,int flags,int dest,int left,int shift){return new CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),flags,dest,left,shift,f.scrollLock());}
 private static final class ByteValue {
  private final CdAssetBytes bytes;private ByteValue(CdAssetBytes b){bytes=Objects.requireNonNull(b);}
  @Override public boolean equals(Object o){return o instanceof ByteValue v&&Arrays.equals(bytes.copy(),v.bytes.copy());}
  @Override public int hashCode(){return Arrays.hashCode(bytes.copy());}
 }
 private static ByteValue bytes(CdAssetBytes b){return new ByteValue(b);}
 private static Object cpuValue(Optional<CdObjectDrawQueue.CpuSat> cpu){if(cpu.isEmpty())return List.of();var c=cpu.orElseThrow();return List.of(bytes(CdAssetBytes.copyOf(c.bytes())),c.spriteCount(),c.provenance());}
 private static Object playerValue(CdPlayerArtRuntime.State p){return List.of(bytes(p.cpuBuffer()),bytes(p.residentBank()),p.lastFrame(),p.dirty());}
 private static Object inputsValue(Inputs i){return List.of(cpuValue(i.selectedCpu()),bytes(i.selectedSat()),bytes(i.palette()),bytes(i.hscroll()),i.vscrollLongBits(),i.displayEnabled(),bytes(i.foreground().nameTable()),bytes(i.background().nameTable()),i.current(),playerValue(i.player()));}
 private static Object selectedValue(Selected selected){if(selected instanceof Carried c)return c.scene();var s=((Published)selected).selection();return List.of(inputsValue(s.inputs()),s.projection());}
 private static Object scrollValueKey(Scroll s){var b=s.background();return List.of(s.foreground(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),List.of(b.cameraBgX(),b.cameraBgY(),b.cameraBg2X(),b.cameraBg2Y(),b.cameraBg3X(),b.cameraBg3Y(),bytes(b.crossingRam()),b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3(),bytes(b.cloudRam()),bytes(b.deformationRam()),bytes(b.hscrollRam()),b.vscroll()));}
 private static Object globalsValue(Globals g){return List.of(g.ring(),g.live(),g.contact(),g.score(),g.projectorByte(),g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),g.pauseMusicBit(),g.bonus(),g.tail(),g.animations(),bytes(g.paletteSteps7()),bytes(g.paletteTimers7()),g.layerByte(),g.bossFlagsByte(),g.eventRoutineWord());}
 private static Object imageValue(Image i){var v=i.video();return List.of(i.owner(),globalsValue(i.globals()),scrollValueKey(i.scroll()),bytes(i.foreground().nameTable()),bytes(i.background().nameTable()),List.of(bytes(v.cpuSat()),cpuValue(v.completedCpu()),playerValue(v.playerArt()),v.current(),selectedValue(v.selected())),i.clears(),i.geometryReady(),i.collisionPointer(),i.fadeInfoWithoutTail(),i.rngSeedLong(),i.targetRightBoundWord(),i.sounds());}
 private static void optionalByte(OptionalInt v){Objects.requireNonNull(v);v.ifPresent(SonicCdAct3FutureWorldHost::octet);}
 private static boolean allZero(CdAssetBytes b){for(byte v:b.copy())if(v!=0)return false;return true;}
 private static CdAssetBytes zero(int n){return CdAssetBytes.copyOf(new byte[n]);}
 private static void extent(CdAssetBytes b,int n){if(Objects.requireNonNull(b).length()!=n)throw new IllegalArgumentException("Exact extent "+n);}
 private static void word(int v){if((v&~65535)!=0)throw new IllegalArgumentException("Native WORD required");}private static void octet(int v){if((v&~255)!=0)throw new IllegalArgumentException("Native BYTE required");}
}
