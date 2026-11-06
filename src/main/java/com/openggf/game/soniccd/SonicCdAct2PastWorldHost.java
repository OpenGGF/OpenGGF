package com.openggf.game.soniccd;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.EngineTiming;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.GameDataSource;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import com.openggf.game.soniccd.scroll.*;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlas;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.RetainedPlaneSceneRenderer;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.scroll.ZoneScrollHandler;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import java.util.*;

import static com.openggf.game.soniccd.SonicCdAct2PastLoadingVisit.Action;

/**
 * Actual R12B loading World's finite projection; no ordinary object/SAT pass.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, shipped FixBugs=0:
 * Level/Main.asm:19-329,860-907; Palmtree Panic/Scroll (Act 2 Past).asm:11-137,
 * 165-259,265-561,940-1043,1139-1201; Functions (General).asm:196-212.
 * Frame and Core keep their own values. This Host has one immutable capture image;
 * an unconsumed clear receipt grants no RAM, actor, queue, pixel or future-read authority.
 */
final class SonicCdAct2PastWorldHost implements SonicCdAct2PastLoadingVisit.Host, AutoCloseable {
    enum Boundary { GEOMETRY_NOT_LOADED, LOAD_PRODUCT, ACTIVE_POWERUP, SELECTED_DISPLAY, CLEAR_SCREEN_WATER_ALIAS, CLEAR_SCREEN_PLAYER_ALIAS }
    static final class HostBoundary extends UnsupportedOperationException {
        private final Boundary boundary;
        private final Optional<SonicCdPastDisplay.DisplayFailure> displayFailure;
        private HostBoundary(Boundary boundary) { this(boundary,Optional.empty()); }
        private HostBoundary(Boundary boundary,Optional<SonicCdPastDisplay.DisplayFailure> displayFailure) {
            super("R12B Host " + boundary + displayFailure.map(f->": "+f).orElse(""));this.boundary=boundary;this.displayFailure=displayFailure;
        }
        Boundary boundary() { return boundary; }
        Optional<SonicCdPastDisplay.DisplayFailure> displayFailure() { return displayFailure; }
    }
    record Tail(int player2ControlsWord, int player2BoredWord, int unknownLevelByte,
                int demoS1IndexWord, int paletteFadeInfoWord) {
        Tail { word(player2ControlsWord); word(player2BoredWord); octet(unknownLevelByte); word(demoS1IndexWord); word(paletteFadeInfoWord); }
    }
    /** Actual MAINVARS carries and reached knowledge, without Core.Program or Frame fields. */
    record Globals(CdRingGlobals ring, CdCheckpointLiveAux.State live, CdPlayerBody.ContactGlobals contact,
                   CdOpeningObjectRuntime.ScoreGlobals score, int projectorByte,
                   CdCheckpointSaveData.State checkpointSaved, CdAssetBytes savedFlags,
                   OptionalInt drownTimerWord, OptionalInt debugBlockWord, OptionalInt pauseMusicBit,
                   Optional<CdLevelHud.BonusValues> bonus, Optional<Tail> tail,
                   Optional<CdGlobalObjectAnimation.Extras> animations, CdAssetBytes paletteSteps7, CdAssetBytes paletteTimers7) {
        Globals {
            Objects.requireNonNull(ring); Objects.requireNonNull(live); Objects.requireNonNull(contact); Objects.requireNonNull(score);
            octet(projectorByte); Objects.requireNonNull(checkpointSaved); Objects.requireNonNull(savedFlags);
            Objects.requireNonNull(drownTimerWord); Objects.requireNonNull(debugBlockWord); Objects.requireNonNull(pauseMusicBit);
            Objects.requireNonNull(bonus); Objects.requireNonNull(tail); Objects.requireNonNull(animations);
            extent(savedFlags, CdObjectPlacementRuntime.SAVED_BANK_BYTES); extent(paletteSteps7, 7); extent(paletteTimers7, 7);
            drownTimerWord.ifPresent(SonicCdAct2PastWorldHost::word); debugBlockWord.ifPresent(SonicCdAct2PastWorldHost::word);
            if (pauseMusicBit.isPresent() && (pauseMusicBit.getAsInt() & ~0x80) != 0) throw new IllegalArgumentException("PauseMusic bit7 only");
        }
        private Globals scalars(CdRingGlobals r, CdCheckpointLiveAux.State l) {
            return new Globals(r,l,contact,score,projectorByte,checkpointSaved,savedFlags,drownTimerWord,debugBlockWord,pauseMusicBit,bonus,tail,animations,paletteSteps7,paletteTimers7);
        }
        private Globals flowers() {
            var r=ring;
            return new Globals(new CdRingGlobals(r.rings(),r.lives(),r.livesFlags(),r.updateHudRings(),r.updateHudLives(),0,0,0),
                    live,contact,score,projectorByte,checkpointSaved,savedFlags,drownTimerWord,OptionalInt.of(0),pauseMusicBit,bonus,tail,
                    Optional.of(new CdGlobalObjectAnimation.Extras(0,0,0,0,new CdRingSpillGlobals(0,0,0))),paletteSteps7,paletteTimers7);
        }
        private Globals misc() {
            return new Globals(ring,CdCheckpointLiveAux.clearMiscAtLevelStart(live),contact,score,projectorByte,checkpointSaved,savedFlags,
                    drownTimerWord,debugBlockWord,OptionalInt.of(0),bonus,tail,animations,paletteSteps7,paletteTimers7);
        }
        private Globals camera() {
            return new Globals(ring,live,contact,score,projectorByte,checkpointSaved,savedFlags,drownTimerWord,debugBlockWord,pauseMusicBit,
                    Optional.of(new CdLevelHud.BonusValues(0,0,0)),tail,animations,zero(7),zero(7));
        }
        private Globals drown() {
            return new Globals(ring,live,contact,score,projectorByte,checkpointSaved,savedFlags,OptionalInt.of(30),debugBlockWord,pauseMusicBit,bonus,tail,animations,paletteSteps7,paletteTimers7);
        }
        private Globals beginMain() {
            var r=ring;
            return new Globals(new CdRingGlobals(r.rings(),r.lives(),r.livesFlags(),0x80,1,r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame()),
                    live,new CdPlayerBody.ContactGlobals(0,0,contact.combineRing()),new CdOpeningObjectRuntime.ScoreGlobals(score.score(),score.nextLifeScore(),0x80),
                    projectorByte,checkpointSaved,savedFlags,drownTimerWord,debugBlockWord,pauseMusicBit,bonus,
                    Optional.of(new Tail(0,0,0,0,0x202F)),animations,paletteSteps7,paletteTimers7);
        }
        private Globals fromWhite() {
            var t=tail.orElseThrow(()->new IllegalStateException("FromWhite requires actual Main tail stores"));
            return new Globals(ring,live,contact,score,projectorByte,checkpointSaved,savedFlags,drownTimerWord,debugBlockWord,pauseMusicBit,bonus,
                    Optional.of(new Tail(t.player2ControlsWord(),t.player2BoredWord(),t.unknownLevelByte(),t.demoS1IndexWord(),0x003F)),
                    animations,paletteSteps7,paletteTimers7);
        }
    }
    record Copies(int fgX16_16, int fgY16_16, CdR11aBackgroundPlane.CopiedCameras bg,
                  int fgFlags, CdR11aBackgroundPlane.FlagWords bgFlags) {
        Copies { Objects.requireNonNull(bg); word(fgFlags); Objects.requireNonNull(bgFlags); }
    }
    record Scroll(CdR11aForegroundScroll.State foreground, CdR11aBackgroundScroll.State background,
                  CdR11aBackgroundPlane.State foregroundPlane, CdR11aBackgroundPlane.State backgroundPlane,
                  Copies copies, int foregroundXFractionWord) {
        Scroll { Objects.requireNonNull(foreground);Objects.requireNonNull(background);Objects.requireNonNull(foregroundPlane);
            Objects.requireNonNull(backgroundPlane);Objects.requireNonNull(copies);word(foregroundXFractionWord); }
    }
    sealed interface Selected permits Carried, Published { }
    /** Descriptive selected picture only. Root's actual target InitVDP path supplies Published Disabled. */
    record Carried(RetainedPlaneSceneRenderer.Scene scene) implements Selected { Carried { Objects.requireNonNull(scene); } }
    record Published(SonicCdAct2PastDisplay.Selection selection) implements Selected { Published { Objects.requireNonNull(selection); } }
    /** PlayerArt here is a sampled immutable ingress/publication value; Frame remains its sole writer. */
    record Video(CdAssetBytes cpuSat, Optional<CdObjectDrawQueue.CpuSat> completedCpu,
                 CdPlayerArtRuntime.State playerArt, CdR12bCameraArtResidency.State current, Selected selected) {
        Video {
            extent(cpuSat,640);Objects.requireNonNull(completedCpu);Objects.requireNonNull(playerArt);Objects.requireNonNull(current);Objects.requireNonNull(selected);
            if(completedCpu.isPresent()&&!Arrays.equals(completedCpu.orElseThrow().bytes(),cpuSat.copy()))
                throw new IllegalArgumentException("CPU SAT/provenance disagree");
        }
    }
    record Initial(Globals globals, Scroll scroll, Video video) {
        Initial { Objects.requireNonNull(globals);Objects.requireNonNull(scroll);Objects.requireNonNull(video); }
    }
    private record Image(SonicCdAct2PastWorldHost owner, Globals globals, Scroll scroll, Video video,
                         Set<Action> clears, boolean geometryReady, boolean setupDone,
                         OptionalLong collisionPointer, List<CdPlayerFrameRuntime.BodySound> sounds) {
        private Image { clears=Set.copyOf(clears);Objects.requireNonNull(collisionPointer);sounds=List.copyOf(sounds); }
    }
    /** Existing global scroll adapter stores lifetime only; Core alone restores native fields. */
    private record ScrollBinding(SonicCdAct2PastWorldHost owner,SonicCdAct2PastLoadingVisit visit) { }
    private record RenderBinding(GameplayModeContext mode, WorldSession world, GameDataSource data,
                                 LevelManager levels, Level level, long generation, Camera camera, GraphicsManager graphics,
                                 PatternAtlas atlas, GLCapabilities capabilities, Thread thread, String backend,
                                 RetainedPlaneSceneRenderer renderer) { }
    private final SonicCdGameModule module;
    private final CdR12bTerrainAssets terrain;
    private final CdR12bPlayerAssets player;
    private final CdPlayerFrameRuntime frame;
    private final CdR12bBackgroundScroll background;
    private final CdR11aBackgroundPlane backgroundPlane;
    private final CdR11bForegroundPlane foregroundPlane;
    private final ZoneScrollHandler handler=new Projection();
    private SonicCdAct2PastLoadingVisit owner;
    private Image image;
    private RenderBinding render;
    private boolean closed, acting, publishing, drawing, closing;

    SonicCdAct2PastWorldHost(SonicCdGameModule module, CdR12bTerrainAssets terrain,
                            CdR12bPlayerAssets player, CdPlayerFrameRuntime frame, Initial initial) {
        this.module=Objects.requireNonNull(module);this.terrain=Objects.requireNonNull(terrain);this.player=Objects.requireNonNull(player);
        this.frame=Objects.requireNonNull(frame);Objects.requireNonNull(initial);
        if(!frame.stageSource().ownsAct2Past(terrain,player))throw new IllegalArgumentException("Actual R12B Frame pair required");
        if(!samePlayer(frame.artState(),initial.video().playerArt()))throw new IllegalArgumentException("Ingress player image differs from Frame");
        background=CdR12bBackgroundScroll.bind(terrain);backgroundPlane=CdR11aBackgroundPlane.bindAct2Past(terrain);
        var t=terrain.terrain();foregroundPlane=CdR11bForegroundPlane.fromTables(t.blocks(),t.chunks(),t.layout().ram());
        image=new Image(this,initial.globals(),initial.scroll(),initial.video(),Set.of(),false,false,OptionalLong.empty(),List.of());
    }
    void requireRenderIdle() {
        if(drawing)throw new IllegalStateException("R12B retained draw is active");
    }
    private void idle() {
        requireRenderIdle();
        if(closed||closing)throw new IllegalStateException("R12B Host retired");
    }
    private void bound() {
        if(owner==null)throw new IllegalStateException("R12B Host has no installed visit");
        module.requireAct2PastLoadingVisit(owner);
        if(frame.closed()||!frame.stageSource().ownsAct2Past(terrain,player))throw new IllegalStateException("R12B source/Frame retired");
    }
    private void current(){idle();bound();}
    @Override public void validate(SonicCdAct2PastLoadingVisit visit,CdPlayerFrameRuntime f,CdR12bTerrainAssets t,CdR12bPlayerAssets p) {
        idle();Objects.requireNonNull(visit);
        if(f!=frame||t!=terrain||p!=player||!frame.stageSource().ownsAct2Past(t,p))throw new IllegalArgumentException("Foreign R12B Host source");
        module.requireAct2PastLoadingVisit(visit); // Root authenticates actual private visit, World, source and load epoch.
        if(owner!=null&&owner!=visit)throw new IllegalArgumentException("Foreign installed R12B visit");
        if(frame.closed())throw new IllegalStateException("R12B Frame retired");
        if(owner==null&&image.video().current()!=visit.state().section().residency())throw new IllegalArgumentException("Ingress current is not the Core section image");
        if(owner==null)owner=visit; // Once-only metadata binding after the actual Module's admission, never a value/capture key.
    }
    Globals globals(){current();return image.globals();}
    Scroll scroll(){current();return image.scroll();}
    Video video(){current();return image.video();}
    Selected selected(){current();return image.video().selected();}
    List<CdPlayerFrameRuntime.BodySound> sounds(){current();return image.sounds();}
    /** Synchronous request extraction only; restore never submits audio. Core's completed-call cursor prevents replay. */
    List<CdPlayerFrameRuntime.BodySound> takeSounds(){
        current();if(acting||publishing)throw new IllegalStateException("R12B Host operation active");var pending=image.sounds();
        if(!pending.isEmpty())image=new Image(this,image.globals(),image.scroll(),image.video(),image.clears(),image.geometryReady(),image.setupDone(),image.collisionPointer(),List.of());
        return pending;
    }
    Set<Action> completedClears(){current();return image.clears();}
    boolean geometryReady(){current();return image.geometryReady();}
    OptionalLong collisionPointer(){current();return image.collisionPointer();}
    ZoneScrollHandler handler(){current();return handler;}
    @Override public Object capture(){current();if(acting||publishing)throw new IllegalStateException("R12B Host operation active");return image;}
    @Override public void validateRestore(Object snapshot) {
        current();if(acting||publishing)throw new IllegalStateException("R12B Host operation is active");
        if(!(snapshot instanceof Image i)||i.owner()!=this)throw new IllegalArgumentException("Foreign R12B Host image");
    }
    @Override public void restore(Object snapshot){validateRestore(snapshot);image=(Image)snapshot;}
    private void values(Globals g,Scroll s,Video v){image=new Image(this,g,s,v,image.clears(),image.geometryReady(),image.setupDone(),image.collisionPointer(),image.sounds());}
    private void scroll(Scroll s){values(image.globals(),s,image.video());}
    private void globals(Globals g){values(g,image.scroll(),image.video());}
    private void video(Video v){values(image.globals(),image.scroll(),v);}
    private void receipt(Action a) {
        var next=EnumSet.noneOf(Action.class);next.addAll(image.clears());next.add(a);
        image=new Image(this,image.globals(),image.scroll(),image.video(),next,image.geometryReady(),image.setupDone(),image.collisionPointer(),image.sounds());
    }
    @Override public int cameraXWord(){current();return image.scroll().foreground().cameraX();}

    @Override public void act(Action action,SonicCdAct2PastLoadingVisit.State state) {
        current();Objects.requireNonNull(action);
        if(acting||publishing||owner.state()!=state)throw new IllegalStateException("R12B action requires its current Core cut");
        acting=true;
        try {
            switch(action) {
                case ENTRY_STORES -> { if((state.program().gameModeByte()&0x80)==0||state.program().levelStartedByte()!=0)throw new IllegalStateException("Missing Core entry stores"); }
                case WHITE_PRELUDE_END -> { if(frame.environment().movement().gates().timeWarpDirection()!=0)throw new IllegalStateException("Missing Frame direction clear"); }
                case NORMAL_LOAD_PALETTE -> { for(int i=0;i<128;i+=2)if(state.normalPalette().unsignedWord(i)!=0xEEE)throw new IllegalStateException("Missing marked normal-white CPU stores"); }
                case PLAY_LEVEL_MUSIC -> {
                    if(!image.sounds().isEmpty()||state.calls().contains(SonicCdAct2PastLoadingVisit.Call.PLAY_LEVEL_MUSIC))throw new IllegalStateException("Level music request cannot replay");
                    image=new Image(this,image.globals(),image.scroll(),image.video(),image.clears(),image.geometryReady(),image.setupDone(),image.collisionPointer(),
                            List.of(new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_PASTMUS)));
                }
                case CLEAR_POWERUP_FLOWERS -> { if(state.level().powerup()!=0||state.level().flowerCount()!=0||state.level().fadeEnableDisplay()!=0)throw new IllegalStateException("Missing Core powerup/flower LONG stores"); }
                case CLEAR_DRAW_QUEUE, CLEAR_UNKNOWN_BUFFER -> receipt(action);
                case CLEAR_FLOWERS -> { clearFlowers();receipt(action); }
                case CLEAR_OBJECTS -> {
                    if(frame.state().header().slotId()!=0||frame.state().motion().routine()!=0||!allZero(state.hudSlots()))throw new IllegalStateException("Missing actual primary/reserved clear");
                    receipt(action); // No dynamic native object owner exists before ordinary selector8.
                }
                case CLEAR_MISC -> { globals(image.globals().misc());receipt(action); }
                case CLEAR_CAMERA -> { clearCamera();receipt(action); }
                case CLEAR_DEMO_INDEX -> { if(state.program().demoDataIndexWord()!=0)throw new IllegalStateException("Core demo index was not cleared"); }
                case CLEAR_SCREEN -> { clearScreen(state);receipt(action); }
                case DROWN_TIMER -> globals(image.globals().drown());
                case LEVEL_SIZE -> initializeSize();
                case SETUP_CAMERA_AND_SCROLL -> setup();
                case LEVEL_SCROLL -> levelScroll(state.level().updateHudTime());
                case LEVEL_LAYOUT -> {
                    if(state.blocksRam().isEmpty()||state.layoutRam().isEmpty()
                            ||!Arrays.equals(state.blocksRam().orElseThrow().copy(),terrain.terrain().blocks().copy())
                            ||!Arrays.equals(state.layoutRam().orElseThrow().copy(),terrain.terrain().layout().ram().copy()))throw new HostBoundary(Boundary.LOAD_PRODUCT);
                    image=new Image(this,image.globals(),image.scroll(),image.video(),image.clears(),true,image.setupDone(),image.collisionPointer(),image.sounds());
                }
                case INITIAL_FOREGROUND -> initialForeground(state);
                case INITIAL_BACKGROUND -> initialBackground();
                case COLLISION_POINTER -> {
                    int offset=(frame.environment().movement().gates().zoneAct()>>>8)*4;
                    long pointer=terrain.view(CdR12bProfile.Asset.LEVEL_COL_INDEX).unsignedLong(offset);
                    if(pointer!=CdR12bProfile.Asset.LEVEL_COLLISION.nativeAddress())throw new HostBoundary(Boundary.LOAD_PRODUCT);
                    image=new Image(this,image.globals(),image.scroll(),image.video(),image.clears(),image.geometryReady(),image.setupDone(),OptionalLong.of(pointer),image.sounds());
                }
                case RESERVED_HUD -> { extent(state.hudSlots(),1984); } // Core executes exact native byte writes; no second object bank.
                case BEGIN_MAIN -> globals(image.globals().beginMain());
                case UPDATE_ANIMATED_TILES -> { if(CdPowerupArtRuntime.gate(state.level().powerup()).copyRequired())throw new HostBoundary(Boundary.ACTIVE_POWERUP); }
                case ENABLE_FADE_AND_CLEAR_MARKER -> {
                    if(frame.environment().movement().gates().timeZone()!=0)throw new IllegalStateException("Frame marker remains set");
                    globals(image.globals().fromWhite());
                }
                case BEGIN_LEVEL -> { if(state.phase()!=SonicCdAct2PastLoadingVisit.Phase.BEGIN_LEVEL||state.program().levelStartedByte()!=1)throw new IllegalStateException("Missing Core BeginLevel stores"); }
                case ENABLE_DISPLAY -> enableSelected();
                case COPY_AND_DRAW -> copyAndDraw();
                case CURRENT_ART -> {
                    var v=image.video();video(new Video(v.cpuSat(),v.completedCpu(),v.playerArt(),state.section().residency(),v.selected()));
                }
                case VINT_COMPLETED -> EngineTiming.vIntRunCounter(EngineServices.current()).serviceRepresentedVBlank();
            }
            bound();
        } finally { acting=false; }
    }
    private void clearFlowers() {
        globals(image.globals().flowers());var s=image.scroll();var b=s.background();var zeroCamera=new CdR11aBackgroundPlane.Camera(0,0);
        var bg=background(b,b.cameraBgX(),b.cameraBgY(),b.cameraBg2X(),b.cameraBg2Y(),b.cameraBg3X(),b.cameraBg3Y(),
                b.crossingRam(),b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3(),zero(512),b.deformationRam(),b.hscrollRam(),b.vscroll());
        scroll(new Scroll(s.foreground(),bg,s.foregroundPlane(),s.backgroundPlane(),
                new Copies(0,0,new CdR11aBackgroundPlane.CopiedCameras(zeroCamera,zeroCamera,zeroCamera),0,new CdR11aBackgroundPlane.FlagWords(0,0,0)),s.foregroundXFractionWord()));
    }
    private void clearCamera() {
        globals(image.globals().camera());var s=image.scroll();var b=s.background();
        var bg=background(b,0,0,0,0,0,0,zero(8),0,0,0,b.cloudRam(),b.deformationRam(),b.hscrollRam(),b.vscroll());
        scroll(new Scroll(new CdR11aForegroundScroll.State(0,0,0,0,0,0,0,0,0,0,0),bg,s.foregroundPlane(),s.backgroundPlane(),s.copies(),0));
    }
    private void clearScreen(SonicCdAct2PastLoadingVisit.State state) {
        var s=image.scroll();var b=s.background();var plane=new CdR11aBackgroundPlane.State(zero(4096));
        var bg=background(b,b.cameraBgX(),b.cameraBgY(),b.cameraBg2X(),b.cameraBg2Y(),b.cameraBg3X(),b.cameraBg3Y(),
                b.crossingRam(),b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3(),b.cloudRam(),b.deformationRam(),b.hscrollRam(),0);
        scroll(new Scroll(s.foreground(),bg,plane,plane,s.copies(),s.foregroundXFractionWord()));
        var v=image.video();video(new Video(zero(640),Optional.empty(),v.playerArt(),v.current(),v.selected()));
        // Shipped DBF sprite count clears644 bytes:640 owned SAT/water-fade bytes,
        // then Core waterPalette[0,4). Never borrow or duplicate that palette authority.
        for(int i=0;i<4;i++)if(state.waterPalette().unsignedByte(i)!=0)throw new HostBoundary(Boundary.CLEAR_SCREEN_WATER_ALIAS);
        bg=background(bg,bg.cameraBgX(),bg.cameraBgY(),bg.cameraBg2X(),bg.cameraBg2Y(),bg.cameraBg3X(),bg.cameraBg3Y(),
                bg.crossingRam(),bg.scrollFlagsBg(),bg.scrollFlagsBg2(),bg.scrollFlagsBg3(),bg.cloudRam(),bg.deformationRam(),zero(1024),0);
        scroll(new Scroll(s.foreground(),bg,plane,plane,s.copies(),s.foregroundXFractionWord()));
        // The second shipped DBF writes1028 bytes, ending at primary ID/flags/tile.
        // This admitted prefix already cleared the complete primary; same-zero result is retained.
        if(frame.state().header().slotId()!=0||frame.state().motion().spriteFlags()!=0||frame.state().header().tile()!=0)
            throw new HostBoundary(Boundary.CLEAR_SCREEN_PLAYER_ALIAS);
    }
    private void initializeSize() {
        var s=image.scroll();var f=s.foreground();var b=terrain.loadPlan().bounds();
        var fg=new CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),16,16,f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),b.bottom(),b.left(),f.bottomBoundShift(),f.scrollLock());
        scroll(new Scroll(fg,s.background(),s.foregroundPlane(),s.backgroundPlane(),s.copies(),s.foregroundXFractionWord()));
    }
    private void setup() {
        if(image.setupDone())throw new IllegalStateException("SetupCamera cannot replay");
        var s=image.scroll();var m=frame.state().motion();
        var fg=CdR11aForegroundScroll.checkpointSetupCamera(s.foreground(),m.centreX16_16()>>>16,m.centreY16_16()>>>16,frame.environment().movement().bounds());
        var bg=CdR12bBackgroundScroll.init(s.background(),fg);
        image=new Image(this,image.globals(),new Scroll(fg,bg,s.foregroundPlane(),s.backgroundPlane(),s.copies(),s.foregroundXFractionWord()),
                image.video(),image.clears(),image.geometryReady(),true,image.collisionPointer(),image.sounds());
    }
    private void levelScroll(int hudTime) {
        if(!image.setupDone())throw new IllegalStateException("LevelScroll precedes actual SetupCamera");
        var s=image.scroll();var m=frame.state().motion();var env=frame.environment();var movement=env.movement();
        var input=new CdR11aForegroundScroll.Input(new CdR11aForegroundScroll.Actor(m.centreX16_16()>>>16,m.centreY16_16()>>>16,
                m.flags(),m.groundVelocity(),m.animation(),m.playerControl()),movement.bounds(),terrain.loadPlan().bounds().top(),
                env.globals().cameraXCentre(),movement.globals().cameraYCentre(),frame.stageSource().variant(),hudTime,320,224);
        var next=CdR11aForegroundScroll.stepForPeriodBackground(s.foreground(),input,movement.gates().timeZone(),CdR11aForegroundScroll.EventRoutine.PPZ2);
        var f=next.state();var bg=background.step(s.background(),new CdR12bBackgroundScroll.Input(f.cameraX(),f.cameraYWord(),(short)f.scrollXDiff(),(short)f.scrollYDiff(),f.scrollLock()>>>8));
        frame.applyScrollFeedback(next.bounds(),next.cameraYCentre());
        var fg=new CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),
                f.scrollFlags()|0x400,f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift(),f.scrollLock());
        scroll(new Scroll(fg,bg,s.foregroundPlane(),s.backgroundPlane(),s.copies(),s.foregroundXFractionWord()));
    }
    private void initialForeground(SonicCdAct2PastLoadingVisit.State state) {
        if(!image.geometryReady())throw new HostBoundary(Boundary.GEOMETRY_NOT_LOADED);
        var s=image.scroll();var f=s.foreground();var bg=terrain.view(CdR12bProfile.Asset.LAYOUT_BG);
        int cursor=64+bg.unsignedByte(1)*128+bg.unsignedByte(0)+1;
        var metadata=new CdR11bForegroundPlane.CallerMetadata(OptionalInt.of(state.layoutRam().orElseThrow().unsignedByte(cursor)));
        try { adoptForeground(foregroundPlane.initialize(s.foregroundPlane(),new CdR11aBackgroundPlane.Camera((f.cameraX()<<16)|s.foregroundXFractionWord(),f.cameraY16_16()),f.scrollFlags(),metadata),false); }
        catch(CdR11bForegroundPlane.InvalidPlane stopped){adoptForeground(stopped.completedPrefix(),false);throw stopped;}
    }
    private void initialBackground() {
        if(!image.geometryReady())throw new HostBoundary(Boundary.GEOMETRY_NOT_LOADED);
        var s=image.scroll();var result=backgroundPlane.initialize(s.backgroundPlane(),live(s.background()),flags(s.background()));
        scroll(new Scroll(s.foreground(),s.background(),s.foregroundPlane(),result.state(),s.copies(),s.foregroundXFractionWord()));
    }
    private void copyAndDraw() {
        var s=image.scroll();var f=s.foreground();var live=live(s.background());
        var copies=new Copies((f.cameraX()<<16)|s.foregroundXFractionWord(),f.cameraY16_16(),
                new CdR11aBackgroundPlane.CopiedCameras(live.bg1(),live.bg2(),live.bg3()),f.scrollFlags(),flags(s.background()));
        scroll(new Scroll(f,s.background(),s.foregroundPlane(),s.backgroundPlane(),copies,s.foregroundXFractionWord()));
        if(!image.geometryReady()&&((copies.fgFlags()|copies.bgFlags().bg1())&0xFF00)!=0)throw new HostBoundary(Boundary.GEOMETRY_NOT_LOADED);
        var bg=backgroundPlane.incremental(s.backgroundPlane(),live,copies.bg(),copies.bgFlags());
        copies=new Copies(copies.fgX16_16(),copies.fgY16_16(),copies.bg(),copies.fgFlags(),bg.flags());
        scroll(new Scroll(f,s.background(),s.foregroundPlane(),bg.state(),copies,s.foregroundXFractionWord()));
        int index=1;
        for(var read:bg.sections())if(read.phase()==CdR11aBackgroundPlane.Phase.INCREMENTAL_COLUMN)index=read.tableIndex()+1;
        var ids=terrain.view(CdR12bProfile.Asset.BG_CAMERA_SECT_IDS);
        var metadata=new CdR11bForegroundPlane.CallerMetadata(index>=0&&index<ids.length()?OptionalInt.of(ids.unsignedByte(index)):OptionalInt.empty());
        try { adoptForeground(foregroundPlane.incremental(image.scroll().foregroundPlane(),new CdR11aBackgroundPlane.Camera(copies.fgX16_16(),copies.fgY16_16()),copies.fgFlags(),metadata),true); }
        catch(CdR11bForegroundPlane.InvalidPlane stopped){adoptForeground(stopped.completedPrefix(),true);throw stopped;}
    }
    private void adoptForeground(CdR11bForegroundPlane.Result result,boolean consume) {
        var s=image.scroll();var c=s.copies();
        if(consume)c=new Copies(c.fgX16_16(),c.fgY16_16(),c.bg(),result.flagsWord(),c.bgFlags());
        scroll(new Scroll(s.foreground(),s.background(),result.state(),s.backgroundPlane(),c,s.foregroundXFractionWord()));
    }

    @Override public void publish(SonicCdAct2PastLoadingVisit.Publication selected) {
        current();Objects.requireNonNull(selected);
        var core=owner.state();
        if(acting||publishing||selected.phase()!=core.phase()||selected.normalPalette()!=core.normalPalette()
                ||selected.waterPalette()!=core.waterPalette()||selected.section()!=core.section()||selected.displayEnabled()!=core.displayEnabled())
            throw new IllegalStateException("Publication must borrow this actual Core cut");
        publishing=true;
        try {
            var s=image.scroll();var v=image.video();
            var inputs=new SonicCdAct2PastDisplay.Inputs(v.completedCpu(),v.cpuSat(),selected.normalPalette(),s.background().hscrollRam(),
                    s.background().vscroll(),selected.displayEnabled(),s.foregroundPlane(),s.backgroundPlane(),v.current(),frame.artState());
            var output=new Published(SonicCdAct2PastDisplay.project(inputs));
            video(new Video(v.cpuSat(),v.completedCpu(),inputs.player(),v.current(),output));bound();
        } finally { publishing=false; }
    }
    private void enableSelected() {
        var v=image.video();
        if(!(v.selected() instanceof Published p))throw new HostBoundary(Boundary.SELECTED_DISPLAY);
        var i=p.selection().inputs();
        var enabled=new SonicCdAct2PastDisplay.Inputs(i.selectedCpu(),i.selectedSat(),i.palette(),i.hscroll(),i.vscrollLongBits(),true,
                i.foreground(),i.background(),i.current(),i.player());
        video(new Video(v.cpuSat(),v.completedCpu(),v.playerArt(),v.current(),new Published(SonicCdAct2PastDisplay.project(enabled))));
    }
    @Override public OptionalLong readLoadTarget(CdTimeWarpLoadData.StoreTarget target) {
        current();Objects.requireNonNull(target);var g=image.globals();var s=image.scroll();var f=s.foreground();var b=s.background();
        return switch(target) {
            case RINGS -> OptionalLong.of(g.ring().rings());
            case LIVES_FLAGS -> OptionalLong.of(g.ring().livesFlags());
            case TIME_LONG -> known(g.live(),CdCheckpointLiveAux.Field.TIME_LONG);
            case WATER_ROUTINE -> known(g.live(),CdCheckpointLiveAux.Field.WATER_ROUTINE);
            case DEST_BOTTOM_BOUND -> OptionalLong.of(f.destBottomBound());
            case FG_X -> OptionalLong.of(f.cameraX());
            case FG_Y -> OptionalLong.of(f.cameraYWord());
            case BG_X -> OptionalLong.of(b.cameraBgX()>>>16);
            case BG_Y -> OptionalLong.of(b.cameraBgY()>>>16);
            case BG2_X -> OptionalLong.of(b.cameraBg2X()>>>16);
            case BG2_Y -> OptionalLong.of(b.cameraBg2Y()>>>16);
            case BG3_X -> OptionalLong.of(b.cameraBg3X()>>>16);
            case BG3_Y -> OptionalLong.of(b.cameraBg3Y()>>>16);
            default -> throw new IllegalArgumentException("Target belongs to Frame, not this Host");
        };
    }
    @Override public void storeLoadTarget(CdTimeWarpLoadData.StoreCall call) {
        current();Objects.requireNonNull(call);if(acting||publishing)throw new IllegalStateException("Host action is active");
        var before=readLoadTarget(call.target());
        if(before.isPresent()!=call.expectedValue().isPresent()||(before.isPresent()&&(int)before.getAsLong()!=call.expectedValue().getAsInt()))
            throw new IllegalStateException("Actual saved-store preimage changed");
        var g=image.globals();var r=g.ring();var s=image.scroll();var f=s.foreground();var b=s.background();int value=call.value();
        switch(call.target()) {
            case RINGS,LIVES_FLAGS -> globals(g.scalars(new CdRingGlobals(call.target()==CdTimeWarpLoadData.StoreTarget.RINGS?value:r.rings(),r.lives(),
                    call.target()==CdTimeWarpLoadData.StoreTarget.LIVES_FLAGS?value:r.livesFlags(),r.updateHudRings(),r.updateHudLives(),r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame()),g.live()));
            case TIME_LONG -> globals(g.scalars(r,CdCheckpointLiveAux.writeTimeLong(g.live(),value)));
            case WATER_ROUTINE -> globals(g.scalars(r,CdCheckpointLiveAux.writeWaterRoutine(g.live(),value)));
            case DEST_BOTTOM_BOUND,FG_X,FG_Y -> {
                var fg=new CdR11aForegroundScroll.State(call.target()==CdTimeWarpLoadData.StoreTarget.FG_X?value:f.cameraX(),
                        call.target()==CdTimeWarpLoadData.StoreTarget.FG_Y?storeWord(f.cameraY16_16(),value):f.cameraY16_16(),
                        f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),
                        call.target()==CdTimeWarpLoadData.StoreTarget.DEST_BOTTOM_BOUND?value:f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift(),f.scrollLock());
                scroll(new Scroll(fg,b,s.foregroundPlane(),s.backgroundPlane(),s.copies(),s.foregroundXFractionWord()));
            }
            case BG_X,BG_Y,BG2_X,BG2_Y,BG3_X,BG3_Y -> {
                var bg=background(b,call.target()==CdTimeWarpLoadData.StoreTarget.BG_X?storeWord(b.cameraBgX(),value):b.cameraBgX(),
                        call.target()==CdTimeWarpLoadData.StoreTarget.BG_Y?storeWord(b.cameraBgY(),value):b.cameraBgY(),
                        call.target()==CdTimeWarpLoadData.StoreTarget.BG2_X?storeWord(b.cameraBg2X(),value):b.cameraBg2X(),
                        call.target()==CdTimeWarpLoadData.StoreTarget.BG2_Y?storeWord(b.cameraBg2Y(),value):b.cameraBg2Y(),
                        call.target()==CdTimeWarpLoadData.StoreTarget.BG3_X?storeWord(b.cameraBg3X(),value):b.cameraBg3X(),
                        call.target()==CdTimeWarpLoadData.StoreTarget.BG3_Y?storeWord(b.cameraBg3Y(),value):b.cameraBg3Y(),
                        b.crossingRam(),b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3(),b.cloudRam(),b.deformationRam(),b.hscrollRam(),b.vscroll());
                scroll(new Scroll(f,bg,s.foregroundPlane(),s.backgroundPlane(),s.copies(),s.foregroundXFractionWord()));
            }
            default -> throw new IllegalArgumentException("Target belongs to Frame");
        }
        bound();
    }
    private static OptionalLong known(CdCheckpointLiveAux.State state,CdCheckpointLiveAux.Field field) {
        try { int raw=CdCheckpointLiveAux.read(state,field);return OptionalLong.of(field.width()==4?Integer.toUnsignedLong(raw):raw); }
        catch(CdCheckpointLiveAux.UnknownLiveByte unknown){return OptionalLong.empty();}
    }

    void projectCamera(Camera camera) {
        current();Objects.requireNonNull(camera);var b=frame.environment().movement().bounds();var f=image.scroll().foreground();
        camera.setMinX((short)b.left());camera.setMaxX((short)b.right());camera.setMinY((short)terrain.loadPlan().bounds().top());camera.setMaxY((short)b.bottom());
        camera.setVerticalWrapEnabled(false);camera.setX((short)f.cameraX());camera.setY((short)f.cameraYWord());camera.setShakeOffsets(0,0);
    }
    private final class Projection implements ZoneScrollHandler {
        @Override public void update(int[] output,int cameraX,int cameraY,int frameCounter,int actId) {
            current();if(actId!=1)throw new IllegalArgumentException("R12B act1 required");Objects.requireNonNull(output);
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
        @Override public Object captureRewindState(){current();return new ScrollBinding(SonicCdAct2PastWorldHost.this,owner);}
        @Override public void restoreRewindState(Object snapshot){
            current();if(!(snapshot instanceof ScrollBinding b)||b.owner()!=SonicCdAct2PastWorldHost.this||b.visit()!=owner)
                throw new IllegalArgumentException("Foreign or retired R12B scroll binding");
            // Observational lifetime token only. Core Snapshot already restores this ONE Host image.
        }
    }

    /** Logic-only callers need no GL. Real drawing lazily borrows the existing governed renderer. */
    void draw() {
        current();if(acting||publishing)throw new IllegalStateException("R12B loading operation active");frame.captureShared();
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
        if(GameServices.level()!=levels||world.getGameModule()!=module||camera!=GameServices.camera()||camera.getWidth()!=320||camera.getHeight()!=224)
            throw new IllegalStateException("R12B actual World/viewport changed before renderer allocation");
        var renderer=new RetainedPlaneSceneRenderer(graphics,PatternAtlasRange.OBJECTS);
        return new RenderBinding(mode,world,world.getDataSource(),levels,levels.getCurrentLevel(),levels.getCompletedProductionLoadGeneration(),
                camera,graphics,graphics.getPatternAtlas(),GL.getCapabilities(),Thread.currentThread(),System.getProperty("openggf.headless.gl"),renderer);
    }
    private void requireRender(RenderBinding b,Selected selected) {
        if(!drawing||closed||closing||acting||publishing||image.video().selected()!=selected)
            throw new IllegalStateException("R12B selected draw was reentered or changed");
        bound();var mode=SessionManager.getCurrentGameplayMode();
        if(b==null||b!=render||mode!=b.mode()||mode.getWorldSession()!=b.world()||b.world().getGameModule()!=module
                ||b.world().getDataSource()!=b.data()||mode.getLevelManager()!=b.levels()||GameServices.level()!=b.levels()
                ||b.levels().getCurrentLevel()!=b.level()||b.levels().getCompletedProductionLoadGeneration()!=b.generation()
                ||mode.getCamera()!=b.camera()||GameServices.camera()!=b.camera()||b.camera().getWidth()!=320||b.camera().getHeight()!=224
                ||GameServices.graphics()!=b.graphics()||!b.graphics().isGlInitialized()||b.graphics().isHeadlessMode()
                ||b.graphics().getPatternAtlas()!=b.atlas()||Thread.currentThread()!=b.thread()
                ||!Objects.equals(System.getProperty("openggf.headless.gl"),b.backend())||GL.getCapabilities()!=b.capabilities())
            throw new IllegalStateException("R12B retained source/World/level/generation/graphics lifetime changed");
    }
    @Override public void close() {
        requireRenderIdle();
        if(closed)return;if(closing||acting||publishing)throw new IllegalStateException("R12B Host operation active");
        closing=true;
        try {if(render!=null){render.renderer().close();render=null;}closed=true;}
        finally {closing=false;}
    }
    private static boolean samePlayer(CdPlayerArtRuntime.State a,CdPlayerArtRuntime.State b) {
        return a.lastFrame()==b.lastFrame()&&a.dirty()==b.dirty()&&Arrays.equals(a.cpuBuffer().copy(),b.cpuBuffer().copy())&&Arrays.equals(a.residentBank().copy(),b.residentBank().copy());
    }
    private static CdR11aBackgroundPlane.LiveCameras live(CdR11aBackgroundScroll.State b) {
        return new CdR11aBackgroundPlane.LiveCameras(new CdR11aBackgroundPlane.Camera(b.cameraBgX(),b.cameraBgY()),
                new CdR11aBackgroundPlane.Camera(b.cameraBg2X(),b.cameraBg2Y()),new CdR11aBackgroundPlane.Camera(b.cameraBg3X(),b.cameraBg3Y()));
    }
    private static CdR11aBackgroundPlane.FlagWords flags(CdR11aBackgroundScroll.State b){return new CdR11aBackgroundPlane.FlagWords(b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3());}
    private static CdR11aBackgroundScroll.State background(CdR11aBackgroundScroll.State before,int x,int y,int x2,int y2,int x3,int y3,
            CdAssetBytes crossing,int flags,int flags2,int flags3,CdAssetBytes cloud,CdAssetBytes deformation,CdAssetBytes hscroll,int vscroll) {
        Objects.requireNonNull(before);return new CdR11aBackgroundScroll.State(x,y,x2,y2,x3,y3,crossing,flags,flags2,flags3,cloud,deformation,hscroll,vscroll);
    }
    private static int storeWord(int before,int value){return(value<<16)|(before&65535);}
    private static boolean allZero(CdAssetBytes b){for(byte value:b.copy())if(value!=0)return false;return true;}
    private static CdAssetBytes zero(int length){return CdAssetBytes.copyOf(new byte[length]);}
    private static void extent(CdAssetBytes bytes,int length){if(Objects.requireNonNull(bytes).length()!=length)throw new IllegalArgumentException("Exact extent"+length+" required");}
    private static void word(int value){if((value&~65535)!=0)throw new IllegalArgumentException("Unsigned WORD required");}
    private static void octet(int value){if((value&~255)!=0)throw new IllegalArgumentException("Unsigned BYTE required");}
}
