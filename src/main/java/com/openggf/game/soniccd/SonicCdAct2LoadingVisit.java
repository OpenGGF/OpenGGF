package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import com.openggf.game.soniccd.scroll.*;

import com.openggf.game.GameServices;
import com.openggf.sprites.render.PlayerResidentBanks;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.EngineTiming;

import java.util.*;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.SonicCdGameModule.Act2LoadCall.*;
import static com.openggf.game.soniccd.SonicCdGameModule.Act2LoadPhase.*;

/** Actual source-bound normal R12A load and first complete ordinary physical walk. */
final class SonicCdAct2LoadingVisit {
    private final SonicCdGameModule module;
    private final CdR12aTerrainAssets assets;
    private final CdR12aPlayerAssets player;
    private final CdPlayerFrameRuntime frame;
    private final SonicCdPlayerPresentation presentation;
    private final CdR12aSectionArt art;
    private final CdR11bForegroundPlane foreground;
    private final PlayerSpriteRenderer playerRenderer;
    private final CdLevelHud.Act2 hud;
    private final CdObjectPlacementRuntime placement;
    private final CdReservedHud reservedHud;
    // A facade only: Values.hudObjects remains the one live/captured reserved owner.
    private final CdReservedObjectBank timeStarBank;
    private final CdTimeStars.Assets timeStarsAssets;
    private final CdTimeStars timeStars;
    private transient TimeStarsInvocation activeTimeStars;
    private boolean timeStarsPort, drawingTimeStars;
    private final CdRing ring;
    private final CdSpring spring;
    private final CdAnimal animal;
    private final CdCollapsePlatform collapsePlatform;
    private transient CollapseInvocation activeCollapse;
    private boolean collapsePort,drawingCollapse;
    private final CdCheckpoint checkpoint;
    private transient CheckpointInvocation activeCheckpoint;
    private boolean checkpointPort, drawingCheckpoint;
    private final CdBreakableWall wall;
    private transient WallInvocation activeWall;
    private boolean wallPort, drawingWall;
    private final CdTamabboh tamabboh;
    private final CdFloatBlock floatBlock;
    private transient TamabbohInvocation activeTamabboh;
    private transient FloatInvocation activeFloat;
    private boolean tamabbohPort, drawingTamabboh, floatPort, drawingFloat;
    private final CdTunnelPath tunnelPath;
    private final CdTunnelPath.Assets tunnelPathAssets;
    private transient TunnelPathInvocation activeTunnelPath;
    private boolean tunnelPathPort;
    private final CdFlower flower;
    private final CdFlower.Assets flowerAssets;
    private transient FlowerInvocation activeFlower;
    private boolean flowerPort, drawingFlower;
    private final CdExplosion explosion;
    private final CdPoints points;
    private final CdExplosion.Assets explosionAssets;
    private final CdPoints.Assets pointsAssets;
    private transient ChildEffectInvocation activeChildEffect;
    private boolean drawingChildEffect;
    private final CdTunnelDoor tunnelDoor;
    private transient DoorInvocation activeDoor;
    private boolean doorPort, drawingDoor;
    private final CdLogShadow logShadow;
    private final CdMosqui mosqui;
    private transient LogMosquiInvocation activeLogMosqui;
    private boolean logMosquiPort, drawingLogMosqui;
    private final CdAnton anton;
    private final CdPataBata pataBata;
    private transient AntonInvocation activeAnton;
    private transient PataInvocation activePata;
    private boolean antonPort, drawingAnton, drawingPata;
    private final CdBoulder boulder;
    private final CdPlatform platform;
    private transient BoulderInvocation activeBoulder;
    private transient PlatformInvocation activePlatform;
    private boolean boulderPort, drawingBoulder, platformPort, drawingPlatform;
    private final CdMonitor monitor;
    private final CdTimePost timePost;
    private transient MonitorInvocation activeMonitor;
    private boolean monitorPort, drawingMonitor;
    private final CdTitleCard title;
    private final CdPlayerObjectTouch primaryScanner;
    private final CdPlayerTouchResponse primaryResponse;
    private transient PrimaryInvocation activePrimary;
    private final List<CdLevelPaletteCycle.Script> paletteCycles;
    private transient CdObjectDrawQueue drawQueue;
    private transient ReservedInvocation activeReserved;
    private transient RingInvocation activeRing;
    private transient SpringInvocation activeSpring;
    private transient DrainInvocation activeDrain;
    private transient TitleInvocation activeTitle;
    private boolean runningObjects, servicing, reservedPort, drawingReserved, ringPort, drawingRing;
    private boolean springPort, drawingSpring, queuePort, titlePort, drawingTitle;
    private Values values;
    private List<SonicCdGameModule.PastSound> sounds = List.of();
    private record SourceView(CdR12aProfile.FileRange range, CdAssetBytes bytes) { }
    private SourceView view(CdR12aProfile.Asset key) {
        return new SourceView(assets.profile().range(key), key == CdR12aProfile.Asset.MAP_SPRING
                ? assets.objects().springMappings() : assets.view(key));
    }
    /** Reached fixed programme stores; subsequent HUD/fade operations preserve their other values. */
    private record TailValues(int player2ControlsWord, int player2BoredWord, int unknownLevelByte,
                              int demoS1IndexWord, int paletteFadeInfoWord) { }
    private record LoadingGlobals(CdCheckpointLiveAux.State live, CdPlayerBody.ContactGlobals contact,
                                  CdOpeningObjectRuntime.ScoreGlobals score, OptionalInt drownTimerWord,
                                  Optional<TailValues> tail, Optional<CdLevelHud.BonusValues> bonus,
                                  Optional<CdAssetBytes> layoutRam, OptionalInt debugBlockWord, OptionalInt pauseMusicBit,
                                  CdOpeningObjectRuntime.DevGlobals retainedDev, CdCheckpointSaveData.State checkpointSaved) {
        private LoadingGlobals {
            Objects.requireNonNull(checkpointSaved); Objects.requireNonNull(live); Objects.requireNonNull(contact);
            Objects.requireNonNull(score); Objects.requireNonNull(drownTimerWord); Objects.requireNonNull(tail);
            Objects.requireNonNull(bonus); Objects.requireNonNull(layoutRam); Objects.requireNonNull(debugBlockWord);
            Objects.requireNonNull(pauseMusicBit); Objects.requireNonNull(retainedDev);
            if (pauseMusicBit.isPresent() && (pauseMusicBit.getAsInt() & ~0x80) != 0)
                throw new IllegalArgumentException("Only PauseMusic's bit7 belongs to this projection");
        }
        LoadingGlobals withCheckpointSaved(CdCheckpointSaveData.State next) {
            return new LoadingGlobals(live,contact,score,drownTimerWord,tail,bonus,layoutRam,debugBlockWord,pauseMusicBit,retainedDev,next);
        }
        LoadingGlobals withLive(CdCheckpointLiveAux.State next) {
            return new LoadingGlobals(next, contact, score, drownTimerWord, tail, bonus, layoutRam, debugBlockWord, pauseMusicBit, retainedDev, checkpointSaved);
        }
        LoadingGlobals withDrownTimer(int word) {
            return new LoadingGlobals(live, contact, score, OptionalInt.of(word), tail, bonus, layoutRam, debugBlockWord, pauseMusicBit, retainedDev, checkpointSaved);
        }
        LoadingGlobals clearBonus() {
            return new LoadingGlobals(live, contact, score, drownTimerWord, tail,
                    Optional.of(new CdLevelHud.BonusValues(0, 0, 0)), layoutRam, debugBlockWord, pauseMusicBit, retainedDev, checkpointSaved);
        }
        LoadingGlobals clearDebugBlock() {
            return new LoadingGlobals(live, contact, score, drownTimerWord, tail, bonus, layoutRam, OptionalInt.of(0), pauseMusicBit, retainedDev, checkpointSaved);
        }
        LoadingGlobals withLayout(CdAssetBytes bytes) {
            return new LoadingGlobals(live, contact, score, drownTimerWord, tail, bonus, Optional.of(bytes), debugBlockWord, pauseMusicBit, retainedDev, checkpointSaved);
        }
        LoadingGlobals withPauseMusicBit(int bit) {
            return new LoadingGlobals(live, contact, score, drownTimerWord, tail, bonus, layoutRam,
                    debugBlockWord, OptionalInt.of(bit), retainedDev, checkpointSaved);
        }
        LoadingGlobals finishMainStores() {
            return new LoadingGlobals(live, new CdPlayerBody.ContactGlobals(0, 0, contact.combineRing()),
                    new CdOpeningObjectRuntime.ScoreGlobals(score.score(), score.nextLifeScore(), 0x80),
                    drownTimerWord, Optional.of(new TailValues(0, 0, 0, 0, 0x202F)), bonus, layoutRam, debugBlockWord, pauseMusicBit, retainedDev, checkpointSaved);
        }
    }

    /** Finite native DBF progress plus the selected display-enable output; neither is a clock. */
    private record FadeProgress(int completedSteps, boolean displayEnabled) {
        private FadeProgress {
            if (completedSteps < 0 || completedSteps > 22) throw new IllegalArgumentException("FromWhite DBF progress");
        }
    }

    /** Native Main223 F78E is distinct from Main309 F790. Knowledge follows actual stores. */
    private record LoopGlobals(OptionalInt demoDataIndexWord,
                               Optional<CdGlobalObjectAnimation.Extras> animations,
                               CdAssetBytes paletteSteps7, CdAssetBytes paletteTimers7,
                               Optional<CdObjectDrawQueue.CpuSat> completedDraw,
                               SonicCdAct2Display.Selection selectedDisplay) {
        private LoopGlobals {
            Objects.requireNonNull(demoDataIndexWord); Objects.requireNonNull(animations);
            Objects.requireNonNull(paletteSteps7); Objects.requireNonNull(paletteTimers7);
            Objects.requireNonNull(completedDraw); Objects.requireNonNull(selectedDisplay);
            if (paletteSteps7.length() != 7 || paletteTimers7.length() != 7)
                throw new IllegalArgumentException("Native palette allocation has seven timer/step bytes");
            if (demoDataIndexWord.isPresent() && (demoDataIndexWord.getAsInt() & ~0xFFFF) != 0)
                throw new IllegalArgumentException("Native demo index is a WORD");
        }
    }

    /** All mutable loading values live here once; actor/shared/scroll remain in their existing owners. */
    private record Values(SonicCdGameModule.Act2LoadPhase phase, CdR12aSectionArt.State section,
                          CdAssetBytes cpuPalette, CdAssetBytes fadePalette, CdAssetBytes waterPalette,
                          CdAssetBytes displayedPalette, CdAssetBytes cpuSat, CdAssetBytes displayedSat,
                          CdAssetBytes displayedHScroll, int displayedVScrollLongBits,
                          CdR11aBackgroundPlane.State foregroundPlane,
                          CdRingGlobals ring, CdLevelGlobals level, LoadingGlobals globals,
                          CdAssetBytes hudObjects, FadeProgress fadeProgress, List<SonicCdGameModule.Act2LoadCall> calls, LoopGlobals loop) {
        private Values {
            Objects.requireNonNull(waterPalette, "actual RunMMD-cleared current water palette");
            if (waterPalette.length() != 128) throw new IllegalArgumentException("Current water palette requires all128 bytes");
            calls = List.copyOf(calls);
        }
    }

    static final class Snapshot {
        private final SonicCdAct2LoadingVisit owner; private final Values values;
        private final CdPlayerFrameRuntime.ActorState actor; private final CdPlayerFrameRuntime.SharedState shared;
        private final CdObjectPlacementRuntime.Snapshot placement;
        private final SonicCdPlayerPresentation.Act2LoadFlags flags;
        private final List<SonicCdGameModule.PastSound> sounds;
        private Snapshot(SonicCdAct2LoadingVisit owner) {
            this.owner=owner; values=owner.values; actor=owner.frame.captureActor(); shared=owner.frame.captureShared();
            placement=owner.placement.capture(); flags=owner.presentation.captureAct2LoadFlags(); sounds=owner.sounds;
        }
    }
    SonicCdAct2LoadingVisit(SonicCdGameModule module, CdR12aTerrainAssets assets, CdPlayerFrameRuntime frame,
                            SonicCdPlayerPresentation presentation, CdR12aPlayerAssets player,
                            CdRingGlobals ringGlobals, CdLevelGlobals level, int sectionWord,
                            SonicCdPlayerPresentation.Act2VideoInit video, CdCheckpointLiveAux.State live,
                            CdPlayerBody.ContactGlobals contact, CdOpeningObjectRuntime.ScoreGlobals score,
                            int displayedVScrollLongBits, CdAssetBytes savedObjectFlags, CdOpeningObjectRuntime.DevGlobals retainedDev,
                            CdAssetBytes runMmdClearedWaterPalette, int retainedDemoDataIndexWord, CdCheckpointSaveData.State checkpointSaved) {
        this.module=Objects.requireNonNull(module); this.assets=Objects.requireNonNull(assets);
        this.frame=Objects.requireNonNull(frame); this.presentation=Objects.requireNonNull(presentation); this.player=Objects.requireNonNull(player);
        placement=CdObjectPlacementRuntime.fromAssets(assets, savedObjectFlags);
        ring=CdRing.fromAssets(placement, assets.objects()); animal=new CdAnimal(placement);
        primaryScanner=new CdPlayerObjectTouch(CdR11aObjectAssets.decodeTouchDimensions(assets.view(CdR12aProfile.Asset.TOUCH_DIMENSIONS).copy()));
        primaryResponse=new CdPlayerTouchResponse(assets.view(CdR12aProfile.Asset.ENEMY_POINTS));
        art=CdR12aSectionArt.fromAssets(assets);
        foreground=CdR11bForegroundPlane.fromTables(assets.terrain().blocks(), assets.terrain().chunks(), assets.terrain().layout().ram());
        var objectAssets=assets.objects();
        var sharedSolid=new CdSolidObject(CdPlayerMovement.fromStageSource(frame.stageSource()));
        var sharedTerrain=CdPlayerTerrain.fromStageTerrain(assets.terrain());
        collapsePlatform=CdCollapsePlatform.fromAct2Assets(placement,objectAssets,sharedSolid,this::commitCollapseSolid,this::drawCollapse,this::playCollapseFm,this::validateCollapsePublication);
        checkpoint=CdCheckpoint.fromAct2Assets(placement,objectAssets,new CdPlayerMath(player.player()),new CdCheckpoint.LivePort() {
            @Override public CdLevelGlobals readLevel(CdCheckpoint.LevelReadCall c){return readCheckpointLevel(c);}
            @Override public int readParent(CdCheckpoint.ParentByteCall c){return readCheckpointParent(c);}
            @Override public CdCheckpointSaveData.State readSaved(CdCheckpoint.SavedReadCall c){return readCheckpointSaved(c);}
            @Override public void store(CdCheckpoint.GlobalStoreCall c){storeCheckpointGlobal(c);}
            @Override public CdCheckpointSaveData.Result save(CdCheckpoint.SaveDataCall c){return saveCheckpoint(c);}
            @Override public void fm(CdCheckpoint.FmRequestCall c){playCheckpointFm(c);}
        },this::drawCheckpoint);
        wall=CdBreakableWall.fromAssets(placement,objectAssets,sharedSolid,this::commitWallSolid,
                this::halveWallX,this::drawWall,this::playWallFm,new CdBreakableWall.DispatchObserver() {
                    @Override public void published(Object operation) { validateWallPublication(operation); }
                    @Override public void completed(CdBreakableWall.Result result) { finishWall(result); }
                    @Override public void failed(Throwable original) { failWall(original); }
                });
        tamabboh=CdTamabboh.fromAct2Assets(placement,objectAssets,sharedTerrain,
                this::commitTamabbohPrimaryAngle,this::readTamabbohPlayerWord,this::playTamabbohFm,this::drawTamabboh);
        floatBlock=CdFloatBlock.fromAct2Assets(placement,objectAssets,sharedSolid,this::commitFloatSolid,this::drawFloatBlock);
        flower=CdFlower.fromAct2Assets(placement,objectAssets,sharedTerrain,new CdFlower.DispatchObserver() {
            @Override public void beforePublication(CdFlower.Publication c) { beforeFlowerPublication(c); }
            @Override public void afterPublication(CdFlower.Publication c) { afterFlowerPublication(c); }
            @Override public void beforeCall(Object c) { beforeFlowerCall(c); }
            @Override public void afterCall(Object c) { afterFlowerCall(c); }
        },this::commitFlowerPrimaryAngle,this::drawFlower,new CdFlower.ReceiptGuard() {
            @Override public void prepared(Object c) { requirePreparedFlowerReceipt(c); }
            @Override public void completed(Object c) { consumeCompletedFlowerReceipt(c); }
        });
        flowerAssets=flower.assets();
        explosion=CdExplosion.fromAct2Assets(placement,objectAssets,new CdExplosion.DispatchObserver() {
            @Override public void beforePublication(CdExplosion.Publication c) { beforeExplosionPublication(c); }
            @Override public void afterPublication(CdExplosion.Publication c) { afterChildPublication(c); }
            @Override public void beforeCall(Object c) { beforeChildCall(c); }
            @Override public void afterCall(Object c) { afterChildCall(c); }
        },this::drawExplosion,new CdExplosion.ReceiptGuard() {
            @Override public void prepared(Object c) { requirePreparedChildReceipt(c); }
            @Override public void completed(Object c) { consumeCompletedChildReceipt(c); }
        });
        points=CdPoints.fromAct2Assets(placement,objectAssets,new CdPoints.DispatchObserver() {
            @Override public void beforePublication(CdPoints.Publication c) { beforePointsPublication(c); }
            @Override public void afterPublication(CdPoints.Publication c) { afterChildPublication(c); }
            @Override public void beforeCall(Object c) { beforeChildCall(c); }
            @Override public void afterCall(Object c) { afterChildCall(c); }
        },this::drawPoints,new CdPoints.ReceiptGuard() {
            @Override public void prepared(Object c) { requirePreparedChildReceipt(c); }
            @Override public void completed(Object c) { consumeCompletedChildReceipt(c); }
        });
        explosionAssets=explosion.assets(); pointsAssets=points.assets();
        tunnelDoor=CdTunnelDoor.withPrefixOwner(placement,this::readDoorSecondary,CdTunnelDoor.Assets.fromAct2Assets(objectAssets),
                sharedSolid,new CdTunnelDoor.PrefixSolidOwner() {
                    @Override public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.PrefixSolidCut c) {return preflightDoor(c);}
                    @Override public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.PrefixSolidCall c) {return commitDoor(c);}
                },this::drawDoor,this::playDoorFm,new CdTunnelDoor.DispatchObserver() {
                    @Override public void beforePublication(CdTunnelDoor.Publication c) {beforeDoorPublication(c);}
                    @Override public void afterPublication(CdTunnelDoor.Publication c) {afterDoorPublication(c);}
                    @Override public void beforeCall(Object c) {beforeDoorCall(c);}
                    @Override public void afterCall(Object c,Object returned) {afterDoorCall(c,returned);}
                    @Override public void failedCall(Object c,Throwable original) {failedDoorCall(c);}
                });
        logShadow=CdLogShadow.fromAct2Assets(placement,objectAssets,new CdLogShadow.DispatchObserver() {
            @Override public void beforePublication(CdLogShadow.Publication c) { beforeLogPublication(c); }
            @Override public void afterPublication(CdLogShadow.Publication c) { afterLogMosquiPublication(c); }
            @Override public void beforeCall(Object c) { beforeLogMosquiCall(c); }
            @Override public void afterCall(Object c) { afterLogMosquiCall(c); }
        },this::drawLogShadow);
        mosqui=CdMosqui.fromAct2Assets(placement,objectAssets,sharedTerrain,this::commitMosquiPrimaryAngle,
                this::playMosquiFm,new CdMosqui.DispatchObserver() {
                    @Override public void beforePublication(CdMosqui.Publication c) { beforeMosquiPublication(c); }
                    @Override public void afterPublication(CdMosqui.Publication c) { afterLogMosquiPublication(c); }
                    @Override public void beforeCall(Object c) { beforeLogMosquiCall(c); }
                    @Override public void afterCall(Object c) { afterLogMosquiCall(c); }
                },this::drawMosqui);
        anton=new CdAnton(placement,CdAnton.Assets.fromAct2Assets(objectAssets),sharedTerrain,this::commitAntonPrimaryAngle,this::drawAnton);
        pataBata=new CdPataBata(placement,CdPataBata.Assets.fromAct2Assets(objectAssets,player),this::drawPataBata);
        boulder=CdBoulder.fromAct2Assets(placement,objectAssets,sharedSolid,this::commitBoulderSolid,this::drawBoulder);
        platform=new CdPlatform(placement,CdPlatform.Assets.fromAct2Assets(objectAssets),new CdPlayerMath(player.player()),
                sharedTerrain,sharedSolid,this::commitPlatformSolid,this::commitPlatformPrimaryAngle,this::drawPlatform);
        var monitorAssets=CdMonitor.Assets.fromAct2Assets(objectAssets);
        monitor=CdMonitor.withPastOwner(placement,monitorAssets,sharedSolid,sharedTerrain,new CdMonitor.PrefixSolidOwner() {
            @Override public CdMonitor.LiveSolid preflight(CdMonitor.PrefixSolidCut cut) { return preflightMonitorSolid(cut); }
            @Override public CdMonitor.LiveSolid commit(CdMonitor.PrefixSolidCall call) { return commitMonitorSolid(call); }
        },this::commitMonitorPrimaryAngle,this::drawMonitor,this::playMonitorFm);
        timePost=CdTimePost.withPastOwner(placement,monitorAssets,this::commitTimePostDirection,this::playTimePostSubCpu,this::drawMonitor);
        spring=new CdSpring(placement, new CdSpring.Assets(objectAssets.springVertical(), objectAssets.springHorizontal(),
                objectAssets.springDiagonal(), CdR12aProfile.Asset.MAP_SPRING.nativeAddress(),
                objectAssets.view(CdR12aProfile.Asset.SPRING_SPEEDS), objectAssets.view(CdR12aProfile.Asset.OBJECT_TILE_IDS)),
                new CdPlayerMath(player.player()), sharedSolid,
                this::commitSpringSolid, this::commitSpringLaunch, this::drawSpring, this::playSpringFm);
        tunnelPathAssets=CdTunnelPath.Assets.fromAct2Assets(objectAssets);
        tunnelPath=CdTunnelPath.withWorldOwner(placement,tunnelPathAssets,new CdTunnelPath.PlayerOwner() {
            @Override public CdTunnelPath.PlayerCut read() { return readTunnelPathPlayer(); }
            @Override public void commit(CdTunnelPath.PlayerCall call) { commitTunnelPathPlayer(call); }
        },this::playTunnelPathFm,new CdTunnelPath.DispatchObserver() {
            @Override public void beforeOperation(Object call) { beforeTunnelPathOperation(call); }
            @Override public void afterOperation(Object call) { afterTunnelPathOperation(call); }
            @Override public void failedOperation(Object call,Throwable original) { failedTunnelPathOperation(call,original); }
        },new CdTunnelPath.ReceiptGuard() {
            @Override public void prepared(Object call) { requirePreparedTunnelPathReceipt(call); }
            @Override public void completed(Object call) { consumeCompletedTunnelPathReceipt(call); }
        });
        hud=new CdLevelHud.Act2(objectAssets.hudAssets(), art); paletteCycles=objectAssets.paletteCycles();
        reservedHud=new CdReservedHud(new CdReservedHud.Assets(CdR12aProfile.Asset.MAP_SPR_HUD.nativeAddress(),
                CdR12aProfile.Asset.MAP_SPR_MONITOR_TIME.nativeAddress()), new CdReservedHud.Slots() {
            @Override public CdAssetBytes read(int whole) { return readReservedHud(whole); }
            @Override public void write(int whole,CdAssetBytes expected,CdAssetBytes next) { writeReservedHud(whole,expected,next); }
        },this::drawReservedHud);
        timeStarBank=CdReservedObjectBank.backedBy(new CdReservedObjectBank.RawBacking() {
            @Override public CdAssetBytes read() { return readTimeStarsReserved(); }
            @Override public boolean compareAndSet(CdAssetBytes expected,CdAssetBytes next) {
                return adoptTimeStarsReserved(expected,next);
            }
        }); // Construction must not read the not-yet-initialized Values owner.
        timeStarsAssets=CdTimeStars.Assets.fromAct2Assets(objectAssets);
        timeStars=CdTimeStars.withWorldOwner(timeStarBank,timeStarsAssets,new CdTimeStars.PowerupPort() {
            @Override public int read() { return readTimeStarsPowerup(); }
            @Override public void write(int value) { writeTimeStarsPowerup(value); }
        },this::drawTimeStar,new CdTimeStars.DispatchObserver() {
            @Override public void beforeOperation(Object call) { beforeTimeStarsOperation(call); }
            @Override public void afterOperation(Object call) { afterTimeStarsOperation(call); }
            @Override public void failedOperation(Object call,Throwable original) { failedTimeStarsOperation(call,original); }
        },new CdTimeStars.ReceiptGuard() {
            @Override public void prepared(Object call) { requirePreparedTimeStarsReceipt(call); }
            @Override public void completed(Object call) { consumeTimeStarsReceipt(call); }
        });
        title=new CdTitleCard(placement,new CdTitleCard.Assets(objectAssets.view(CdR12aProfile.Asset.TITLE_CARD_DATA),
                CdR12aProfile.Asset.TITLE_CARD_MAPPINGS.nativeAddress()),new CdTitleCard.ReservedSlots() {
            @Override public CdAssetBytes read(int whole) { return readTitleReserved(whole); }
            @Override public void write(int whole,CdAssetBytes expected,CdAssetBytes next) { writeTitleReserved(whole,expected,next); }
        },this::readTitle,this::readTitle,this::storeTitle,this::loadTitlePlc,this::drawTitle);
        playerRenderer=new PlayerSpriteRenderer(CdPlayerVisualAdapter.artSet(frame.stageSource().sonic(),0x10000),GameServices.graphics());
        restorePlayerBank(); frame.bindArtPublication(this::publishPlayerArt);
        // RunMMD clears table512, decoder/counts and CPU art; ClearPLCs is then executed at Main57.
        var section=art.clearPlcs(art.fromValues(zero(512),video.currentPatterns(),sectionWord,
                CdR11aPlcBuffer.cleared(),Optional.empty(),new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.CLEARED,0,0,0,0,0),0,0,0,0));
        var initialSelection=SonicCdAct2Display.project(new SonicCdAct2Display.Inputs(Optional.empty(),
                video.selection().displayedSat(),video.selection().displayedPalette(),zero(1024),displayedVScrollLongBits,
                false,null,null,null,null));
        values=new Values(FIRST_PLC,section,zero(128),zero(128),runMmdClearedWaterPalette,
                video.selection().displayedPalette(),zero(640),video.selection().displayedSat(),zero(1024),
                displayedVScrollLongBits,null,ringGlobals,
                new CdLevelGlobals(0,level.updateHudTime(),level.checkpoint(),level.spawnMode(),level.plcLoadFlags()|1,
                        level.flowerCount(),level.fadeEnableDisplay(),level.powerup(),level.timeOver()),
                new LoadingGlobals(CdCheckpointLiveAux.clearMiscAtLevelStart(live),contact,score,OptionalInt.empty(),Optional.empty(),
                        Optional.empty(),Optional.empty(),OptionalInt.empty(),OptionalInt.empty(),retainedDev, checkpointSaved),zero(31*64),
                new FadeProgress(0,false),List.of(LOAD_LEVEL_PCM,INIT_VDP,INIT_CONTROLLERS,NORMAL_LOAD),
                new LoopGlobals(OptionalInt.of(retainedDemoDataIndexWord),Optional.empty(),zero(7),zero(7),Optional.empty(),initialSelection));
    }
    private static CdAssetBytes zero(int size) { return CdAssetBytes.copyOf(new byte[size]); }
    boolean owns(CdR12aTerrainAssets source,CdPlayerFrameRuntime sourceFrame) { return assets==source && frame==sourceFrame; }
    private void requireCurrent() {
        presentation.requirePastRenderIdle();
        requireIdleOwners();
    }
    /** Read-only renderer revalidation can observe the retained selection during its own draw. */
    private void requireIdleOwners() {
        requireBoulderPlatformIdle();
        module.requireAct2LoadingVisit(this);
        if (activeMonitor!=null || monitorPort || drawingMonitor || servicing || runningObjects || drawQueue!=null || activePrimary!=null || activeReserved!=null || activeRing!=null || activeSpring!=null
                || activeDrain!=null || activeTitle!=null || reservedPort || ringPort || springPort || queuePort || titlePort
                || drawingReserved || drawingRing || drawingSpring || drawingTitle)
            throw new IllegalStateException("Act2 native operation is active");
        frame.captureShared();
    }
    private void requireCallback() {
        requireBoulderPlatformIdle();
        module.requireAct2LoadingVisit(this);
        if (activeMonitor!=null || monitorPort || drawingMonitor || !servicing || runningObjects || activeTitle!=null || activeRing!=null || activeSpring!=null || activeReserved!=null)
            throw new IllegalStateException("No actual Act2 V-int callback");
    }
    private void call(SonicCdGameModule.Act2LoadCall call) {
        var calls = new ArrayList<>(values.calls()); calls.add(call);
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(),
                values.displayedPalette(), values.cpuSat(), values.displayedSat(), values.displayedHScroll(),
                values.foregroundPlane(), values.ring(), values.level(), values.globals(), values.hudObjects(), calls);
    }
    private void replace(SonicCdGameModule.Act2LoadPhase phase, CdR12aSectionArt.State section,
                         CdAssetBytes cpuPalette, CdAssetBytes fadePalette, CdAssetBytes displayedPalette,
                         CdAssetBytes cpuSat, CdAssetBytes displayedSat, CdAssetBytes displayedHScroll,
                         CdR11aBackgroundPlane.State fg, CdRingGlobals ring, CdLevelGlobals level,
                         LoadingGlobals globals, CdAssetBytes hud, List<SonicCdGameModule.Act2LoadCall> calls) {
        values = new Values(phase, section, cpuPalette, fadePalette, values.waterPalette(), displayedPalette, cpuSat,
                displayedSat, displayedHScroll, values.displayedVScrollLongBits(), fg, ring, level, globals, hud, values.fadeProgress(), calls, values.loop());
    }
    private void globals(UnaryOperator<LoadingGlobals> operation) {
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), operation.apply(values.globals()), values.hudObjects(), values.calls());
    }
    private void section(UnaryOperator<CdR12aSectionArt.State> operation) {
        try { section(operation.apply(values.section())); }
        catch (CdR12aSectionArt.SectionArtException stopped) { section(stopped.state()); throw stopped; }
    }
    private void section(CdR12aSectionArt.State next) {
        replace(values.phase(), next, values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), values.hudObjects(), values.calls());
    }
    private void phase(SonicCdGameModule.Act2LoadPhase phase) {
        replace(phase, values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), values.hudObjects(), values.calls());
    }

    void service(int held) {
        requireCurrent();
        if (values.phase()==BEGIN_LEVEL) { serviceLevel(held); return; }
        if (values.phase()==FROM_BLACK) { serviceFromBlack(held); return; }
        presentation.validateAct2LevelLoadPlane(); var selectedCpu=values.loop().completedDraw(); servicing=true;
        try { frame.serviceLevelLoad(held,this::publish,this::copyAndDraw,this::fastPlc); }
        finally { servicing=false; }
        representedServiceCompleted(); call(LEVEL_SERVICE); section(art::prepare); call(PROCESS_PLCS);
        if (values.section().queue().headSource()==0 && values.section().remainingTilesWord()==0) {
            if (values.phase()==FIRST_PLC) mainLoadPrefix(); else finishSectionWait();
        }
        settleSelectedDisplay(selectedCpu);
    }
    private void restorePlayerBank() {
        PlayerResidentBanks.claim(playerRenderer);
        PlayerResidentBanks.restore(playerRenderer, new PlayerResidentBanks.Snapshot(
                CdPlayerVisualAdapter.residentPatterns(frame.artState()), -1));
        playerRenderer.ensureCached(GameServices.graphics());
    }
    private void publishPlayerArt(CdPlayerArtRuntime.DmaRequest request, CdPlayerArtRuntime.State complete) {
        requireCallback();
        if (values.phase() != BEGIN_LEVEL || complete != frame.artState())
            throw new IllegalStateException("Player art publication is outside the actual R12A service");
        // The Frame already adopted its exact native DMA prefix, including tile24's
        // retained tail. Publish that same value; no cue selection or gameplay replay.
        restorePlayerBank();
    }
    private void serviceLevel(int held) {
        presentation.validateAct2LevelLoadPlane();
        // Completed-call observations are per ordinary service, not an unbounded
        // gameplay history or a source of execution authority.
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), values.hudObjects(), List.of());
        // VInterrupt's selected VScroll precedes the actual controller read. Root
        // has preflighted the real source/World and the already-claimed art bank.
        var bg = presentation.act2BackgroundImage();
        values = new Values(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.waterPalette(),
                values.displayedPalette(), values.cpuSat(), values.displayedSat(), values.displayedHScroll(), bg.vscroll(),
                values.foregroundPlane(), values.ring(), values.level(), values.globals(), values.hudObjects(),
                values.fadeProgress(), values.calls(), values.loop());
        sounds = List.of(); // Current call requests, not an unbounded sound history.
        var selectedCpu = values.loop().completedDraw();
        var selected = frame.prepareInput(held);
        boolean[] published = {false};
        servicing = true;
        try { frame.serviceVBlank(selected.phase(), () -> { publishLevel(); published[0] = true; },
                this::serviceLevelArtAndHud, this::mainLevelMusic); }
        finally { servicing = false; }
        // This is after the actual art/plane/slow-PLC/HUD service, before Main can
        // replace CPU SAT or update scroll/palette. A real pause still selects here.
        if (published[0]) settleSelectedDisplay(selectedCpu);
        if (frame.paused()) return; // Main's pause branch precedes the native +1/placement.
        int levelFrame = frame.advanceLevelMainFrame(); call(LEVEL_FRAME_INCREMENT);
        // Native Main402 before any physical object call: the actual pre-scroll
        // camera and raw current period, with common-list order and carried bank.
        placement.spawnObjects(presentation.act2ForegroundImage().cameraX(), frame.environment().movement().gates().timeZone());
        call(SPAWN_OBJECTS);
        runObjects(levelFrame);
    }

    /**
     * The queue is pending RunObjects output, never a second completed CPU image.
     * The actual complete physical walk reaches the single camera/SAT/tail owner.
     * A stopped earlier body retains its prefix without finishing that later work.
     */
    private void loop(LoopGlobals next) {
        var v = values;
        values = new Values(v.phase(), v.section(), v.cpuPalette(), v.fadePalette(), v.waterPalette(), v.displayedPalette(),
                v.cpuSat(), v.displayedSat(), v.displayedHScroll(), v.displayedVScrollLongBits(),
                v.foregroundPlane(), v.ring(), v.level(), v.globals(), v.hudObjects(), v.fadeProgress(), v.calls(), next);
    }

    /** Pure projection at a completed source service cut; never calls Frame capture or native work. */
    private void settleSelectedDisplay(Optional<CdObjectDrawQueue.CpuSat> selectedCpu) {
        var old = values.loop();
        boolean enabled = values.fadeProgress().displayEnabled();
        var inputs = new SonicCdAct2Display.Inputs(selectedCpu, values.displayedSat(), values.displayedPalette(),
                values.displayedHScroll(), values.displayedVScrollLongBits(), enabled,
                enabled ? values.foregroundPlane() : null, enabled ? presentation.act2PlaneImage() : null,
                enabled ? values.section().residency() : null, enabled ? frame.artState() : null);
        // Expected current/geometry knowledge gaps remain immutable render-only Blocked values.
        var selected = SonicCdAct2Display.project(inputs);
        loop(new LoopGlobals(old.demoDataIndexWord(), old.animations(), old.paletteSteps7(), old.paletteTimers7(),
                old.completedDraw(), selected));
    }

    SonicCdAct2Display.Selection selectedDisplay() {
        requireIdleOwners();
        return values.loop().selectedDisplay();
    }

    /** Native Main405-432. Only the actual completed physical pass can reach this continuation. */
    private void finishOrdinaryMain() {
        requireBoulderPlatformIdle();
        module.requireAct2LoadingVisit(this);
        if (activeMonitor != null || monitorPort || drawingMonitor || !runningObjects || servicing || activeDrain != null || activeReserved != null
                || activeRing != null || activeSpring != null || reservedPort || drawingReserved || ringPort || drawingRing
                || springPort || drawingSpring || queuePort || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || values.calls().isEmpty() || values.calls().getLast() != DYNAMIC_OBJECTS)
            throw new IllegalStateException("No completed actual R12A physical pass");
        int demo = values.loop().demoDataIndexWord().orElseThrow(
                () -> module.act2Boundary(SonicCdGameModule.Act2Boundary.LEVEL_FLOW));
        if (demo == 0x0800) throw module.act2Boundary(SonicCdGameModule.Act2Boundary.LEVEL_FLOW);
        if (values.level().levelRestart() != 0) {
            throw module.act2Boundary(SonicCdGameModule.Act2Boundary.LEVEL_FLOW);
        }
        call(POST_OBJECT_GUARDS);
        CdObjectDrawQueue.DrawCameras cameras;
        if (frame.environment().globals().debugMode() == 0 && frame.state().motion().routine() >= 6) {
            throw module.act2Boundary(SonicCdGameModule.Act2Boundary.PRIMARY_BODY);
        } else {
            cameras = presentation.continueAct2CameraAfterObjects(frame, values.level().updateHudTime()); call(LEVEL_SCROLL);
        }
        var invocation = new DrainInvocation();
        activeDrain = invocation;
        CdObjectDrawQueue.CpuSat completed;
        try {
            completed = drawQueue.finishAfterCamera(cameras);
            verifyDrain(invocation);
        } catch (RuntimeException | Error failure) {
            try { verifyDrain(invocation); }
            catch (RuntimeException | Error verification) { if (verification != failure) failure.addSuppressed(verification); }
            throw failure;
        } finally { activeDrain = null; }
        var oldLoop = values.loop();
        var readyLoop = new LoopGlobals(oldLoop.demoDataIndexWord(), oldLoop.animations(), oldLoop.paletteSteps7(),
                oldLoop.paletteTimers7(), Optional.of(completed), oldLoop.selectedDisplay());
        var readyCpu = CdAssetBytes.copyOf(completed.bytes());
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                readyCpu, values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(), values.ring(),
                values.level(), values.globals(), values.hudObjects(), values.calls());
        loop(readyLoop); drawQueue.close(); drawQueue = null; call(DRAW_OBJECTS);
        if (values.ring().timeStopTimer() == 0) {
            cyclePalette(); call(PALETTE_CYCLE);
        }
        section(s -> art.updateSection(s, presentation.act2ForegroundImage().cameraX())); call(UPDATE_SECTION);
        // ProcessPLCs prepares the next native job; tile service belongs to a later V-int.
        section(art::prepare); call(PREPARE_PLCS);
        var extras = values.loop().animations().orElseThrow(
                () -> module.act2Boundary(SonicCdGameModule.Act2Boundary.GLOBAL_ANIMATIONS));
        var animated = CdGlobalObjectAnimation.step(extras, values.ring());
        var animationLoop = values.loop();
        var readyAnimationLoop = new LoopGlobals(animationLoop.demoDataIndexWord(), Optional.of(animated.extras()),
                animationLoop.paletteSteps7(), animationLoop.paletteTimers7(), animationLoop.completedDraw(), animationLoop.selectedDisplay());
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(), animated.ring(),
                values.level(), values.globals(), values.hudObjects(), values.calls());
        loop(readyAnimationLoop); call(UPDATE_GLOBAL_ANIMATIONS); call(ORDINARY_MAIN_RETURN);
    }

    private void cyclePalette() {
        var old = values.loop();
        var result = CdLevelPaletteCycle.pass(paletteCycles, new CdLevelPaletteCycle.State(values.cpuPalette(),
                old.paletteTimers7().slice(0, 6), old.paletteSteps7().slice(0, 6)));
        byte[] timers = old.paletteTimers7().copy(), steps = old.paletteSteps7().copy();
        System.arraycopy(result.state().timers().copy(), 0, timers, 0, 6);
        System.arraycopy(result.state().steps().copy(), 0, steps, 0, 6);
        var readyLoop = new LoopGlobals(old.demoDataIndexWord(), old.animations(), CdAssetBytes.copyOf(steps),
                CdAssetBytes.copyOf(timers), old.completedDraw(), old.selectedDisplay());
        // Adopt every reached palette/timer store, including a finite source fault prefix.
        replace(values.phase(), values.section(), result.state().palette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(), values.ring(),
                values.level(), values.globals(), values.hudObjects(), values.calls());
        loop(readyLoop);
        if (!result.completed()) throw module.act2Boundary(SonicCdGameModule.Act2Boundary.PALETTE_CYCLE);
    }

    /** One private DrawObjects scope, never captured and never granted by public slot values. */
    private final class DrainInvocation {
        private final CdObjectDrawQueue queue = drawQueue;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR12aTerrainAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final Object world;
        private final long generation;
        private final CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.ActorState expectedActor = actor;
        private final CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.captureAct2Scroll();
        private final CdObjectPlacementRuntime.Snapshot placed = placement.capture();
        private CdObjectPool.Snapshot expectedPool = placed.pool();
        private Values expectedValues = values;
        private final List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private DrainInvocation() {
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
        }
    }

    private void verifyDrain(DrainInvocation invocation) {
        requireBoulderPlatformIdle();
        module.requireAct2LoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        var actual = placement.capture();
        if (!runningObjects || servicing || invocation == null || activeDrain != invocation
                || activeReserved != null || activeRing != null || activeSpring != null || reservedPort || drawingReserved || ringPort
                || drawingRing || springPort || drawingSpring || invocation.queue != drawQueue
                || invocation.sourceFrame != frame || invocation.sourceAssets != assets || invocation.sourceStage != frame.stageSource()
                || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || !frame.captureActor().equals(invocation.expectedActor) || !frame.captureShared().equals(invocation.shared)
                || !presentation.sameAct2Scroll(invocation.scroll) || values != invocation.expectedValues
                || sounds != invocation.expectedSounds || !actual.status().equals(invocation.placed.status())
                || !actual.pool().slots().equals(invocation.expectedPool.slots())
                || !sameBytes(actual.savedFlags(), invocation.placed.savedFlags()))
            throw new IllegalStateException("DrawObjects changed another owner or stale full native image");
    }

    private DrainInvocation requireDrainingSlot(CdObjectDrawQueue.CdSlotRef ref) {
        Objects.requireNonNull(ref);
        var invocation = activeDrain; verifyDrain(invocation);
        if (queuePort || drawQueue.phase() != CdObjectDrawQueue.Phase.DRAINING)
            throw new IllegalStateException("No actual queue-draining port");
        return invocation;
    }

    private CdObjectDrawQueue.LiveDrawSlot readDrainingSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireDrainingSlot(ref);
        queuePort = true;
        try {
            int whole = ref.wholeIndex();
            if (whole == 0) {
                var live = primaryLiveSlot(); verifyDrain(invocation); return live;
            }
            CdAssetBytes image = whole < 32 ? reservedImage(whole) : CdAssetBytes.copyOf(placement.pool().state(whole).copyBytes());
            var live = new CdObjectDrawQueue.LiveDrawSlot(image.unsignedByte(0), image.unsignedByte(1),
                    image.unsignedWord(8), image.unsignedWord(0x0C), image.unsignedWord(0x0A),
                    (int) image.unsignedLong(4), image.unsignedByte(0x1A), image.unsignedWord(2));
            verifyDrain(invocation); return live;
        } finally { queuePort = false; }
    }

    private void writeDrainingOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireDrainingSlot(ref);
        if (!onScreen) throw new IllegalStateException("DrawObjects only sets a loaded occurrence's bit7");
        queuePort = true;
        try {
            int whole = ref.wholeIndex();
            if (whole == 0) {
                var readyActor = primaryScreenActor(invocation.expectedActor, true);
                verifyDrain(invocation);
                frame.publishDrawObjectOnScreen(true);
                invocation.expectedActor = readyActor;
            } else if (whole < 32) {
                byte[] hud = values.hudObjects().copy();
                hud[(whole - 1) * 64 + 1] |= (byte) 0x80;
                var ready = reservedValues(values, CdAssetBytes.copyOf(hud));
                verifyDrain(invocation); values = ready; invocation.expectedValues = ready;
            } else {
                var source = placement.pool().state(whole);
                var next = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() | 0x80);
                var ready = CdObjectPool.overlay(invocation.expectedPool, List.of(next));
                placement.pool().validateRestore(ready); verifyDrain(invocation);
                placement.pool().write(next); invocation.expectedPool = ready;
            }
            verifyDrain(invocation);
        } finally { queuePort = false; }
    }

    private record ReservedStore(int offset, int width, int value) { }
    private final class ReservedInvocation {
        private final CdReservedHud body = reservedHud;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR12aTerrainAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final Object world;
        private final long generation;
        private final int whole;
        private final Values base = values;
        private CdAssetBytes expectedHud = values.hudObjects();
        private final CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private final CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private final CdObjectPlacementRuntime.Snapshot placed = placement.capture();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.captureAct2Scroll();
        private final List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private final List<ReservedStore> stores;
        private final boolean drawExpected;
        private int storeCursor;
        private boolean drawn;

        private ReservedInvocation(int whole, CdReservedHud.Globals globals) {
            this.whole = whole;
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession();
            generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var source = reservedImage(whole);
            stores = reservedStores(source, globals);
            int id = source.unsignedByte(0), subtype = source.unsignedByte(0x28), routine = source.unsignedByte(0x24);
            drawExpected = (routine == 0 || routine == 2)
                    && (id == 0x1C && subtype < 0x80
                    || id == 0x19 && subtype == 0x0A && globals.timeAttackModeByte() == 0
                    && globals.timeWarpDirectionByte() != 0
                    && (globals.timeWarpTimerWord() < 90 || (globals.levelFramesWord() & 1) == 0));
        }
    }

    private void runReservedHud(int whole) {
        requireDoorIdle();
        requireLogMosquiIdle();
        requireBoulderPlatformIdle();
        if(activeMonitor!=null || monitorPort || drawingMonitor)
            throw new IllegalStateException("Actual Monitor/Post invocation owns this object cut");
        if (!runningObjects || activeReserved != null || activeRing != null || activeSpring != null || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring || queuePort)
            throw new IllegalStateException("No actual reserved invocation entry");
        var env = frame.environment();
        var g = env.globals();
        long absoluteVInt = frame.vIntClock().absoluteValue().orElseThrow(
                () -> new IllegalStateException("R12A reserved HUD requires its absolute native VInt"));
        var globals = new CdReservedHud.Globals(g.debugCheat(), values.ring().rings(), (int) absoluteVInt & 0xFF,
                g.timeAttackMode(), env.movement().gates().timeWarpDirection(), g.timeWarpTimer(), g.levelFrames());
        var invocation = new ReservedInvocation(whole, globals);
        activeReserved = invocation;
        try {
            var result = reservedHud.run(whole, globals);
            verifyReservedInvocation(invocation);
            if (invocation.storeCursor != invocation.stores.size() || invocation.drawn != invocation.drawExpected
                    || !sameBytes(result.image(), reservedImage(whole)))
                throw new IllegalStateException("Incomplete actual reserved body publication");
            // Result/effects are completed metadata; no operation is replayed.
        } catch (RuntimeException | Error failure) {
            verifyReservedFailure(failure);
            throw failure;
        } finally {
            activeReserved = null;
        }
    }

    private ReservedInvocation requireReservedInvocation(int whole) {
        requireBoulderPlatformIdle();
        var invocation = activeReserved;
        if (!runningObjects || servicing || activeRing != null || ringPort || drawingRing || activeSpring != null || springPort || drawingSpring || invocation == null || invocation.whole != whole
                || invocation.body != reservedHud || invocation.sourceFrame != frame
                || invocation.sourceAssets != assets || invocation.sourceStage != frame.stageSource()
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached reserved HUD invocation");
        verifyReservedInvocation(invocation);
        return invocation;
    }

    private void verifyReservedInvocation(ReservedInvocation invocation) {
        requireBoulderPlatformIdle();
        module.requireAct2LoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        var now = placement.capture();
        if (activeReserved != invocation || !runningObjects || activeRing != null || ringPort || drawingRing || activeSpring != null || springPort || drawingSpring || invocation.body != reservedHud
                || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.sourceStage != frame.stageSource() || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared)
                || !now.status().equals(invocation.placed.status())
                || !now.pool().slots().equals(invocation.placed.pool().slots())
                || !sameBytes(now.savedFlags(), invocation.placed.savedFlags())
                || !presentation.sameAct2Scroll(invocation.scroll) || sounds != invocation.expectedSounds
                || !values.equals(reservedValues(invocation.base, invocation.expectedHud))
                || !sameBytes(values.hudObjects(), invocation.expectedHud))
            throw new IllegalStateException("Reserved HUD changed another actual owner or stale full31 image");
    }

    private void verifyReservedFailure(Throwable original) {
        try {
            if (activeReserved != null) verifyReservedInvocation(activeReserved);
        } catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private CdAssetBytes readReservedHud(int whole) {
        var invocation = requireReservedInvocation(whole);
        if (reservedPort || drawingReserved || queuePort)
            throw new IllegalStateException("Reserved HUD read reentry");
        reservedPort = true;
        try {
            var image = reservedImage(whole);
            verifyReservedInvocation(invocation);
            return image;
        } catch (RuntimeException | Error failure) {
            verifyReservedFailure(failure); throw failure;
        } finally { reservedPort = false; }
    }

    private void writeReservedHud(int whole, CdAssetBytes expected, CdAssetBytes next) {
        var invocation = requireReservedInvocation(whole);
        if (reservedPort || drawingReserved || queuePort || invocation.drawn
                || expected == null || next == null || expected.length() != 64 || next.length() != 64
                || !sameBytes(expected, reservedImage(whole))
                || invocation.storeCursor >= invocation.stores.size())
            throw new IllegalStateException("Invalid selected reserved write or native store order");
        reservedPort = true;
        try {
            var raw = expected.copy();
            var store = invocation.stores.get(invocation.storeCursor);
            if (store.width() == 0) Arrays.fill(raw, (byte) 0);
            else for (int i = 0; i < store.width(); i++)
                raw[store.offset() + i] = (byte) (store.value() >>> ((store.width() - i - 1) * 8));
            if (!Arrays.equals(raw, next.copy()))
                throw new IllegalStateException("Reserved write is not the reached finite native store");
            publishReservedImage(invocation, next);
            invocation.storeCursor++;
            verifyReservedInvocation(invocation);
        } catch (RuntimeException | Error failure) {
            verifyReservedFailure(failure); throw failure;
        } finally { reservedPort = false; }
    }

    private CdAssetBytes reservedImage(int whole) {
        if (whole < 1 || whole > 31) throw new IllegalArgumentException("Reserved slot must be 1..31");
        return values.hudObjects().slice((whole - 1) * 64, 64);
    }
    private static boolean sameBytes(CdAssetBytes left, CdAssetBytes right) {
        return Arrays.equals(left.copy(), right.copy());
    }
    private static Values reservedValues(Values v, CdAssetBytes image) {
        return new Values(v.phase(), v.section(), v.cpuPalette(), v.fadePalette(), v.waterPalette(), v.displayedPalette(),
                v.cpuSat(), v.displayedSat(), v.displayedHScroll(), v.displayedVScrollLongBits(),
                v.foregroundPlane(), v.ring(), v.level(), v.globals(), image, v.fadeProgress(), v.calls(), v.loop());
    }
    private void publishReservedImage(ReservedInvocation invocation, CdAssetBytes selected) {
        byte[] all = invocation.expectedHud.copy();
        System.arraycopy(selected.copy(), 0, all, (invocation.whole - 1) * 64, 64);
        var ready = CdAssetBytes.copyOf(all);
        var readyValues = reservedValues(values, ready);
        values = readyValues;
        invocation.expectedHud = ready;
    }

    private void drawReservedHud(int whole) {
        var invocation = requireReservedInvocation(whole);
        if (reservedPort || drawingReserved || queuePort || invocation.drawn || !invocation.drawExpected
                || invocation.storeCursor != invocation.stores.size())
            throw new IllegalStateException("Reserved DrawObject is not at its actual once-only cut");
        invocation.drawn = true;
        drawingReserved = true;
        try {
            var image = reservedImage(whole);
            var camera = presentation.act2ForegroundImage();
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(whole),
                    image.unsignedByte(1), image.unsignedWord(8), image.unsignedWord(0x0C),
                    image.unsignedByte(0x16), image.unsignedByte(0x19), image.unsignedByte(0x18)),
                    new CdObjectDrawQueue.CameraWords(camera.cameraX(), camera.cameraYWord()));
            verifyReservedInvocation(invocation);
        } catch (RuntimeException | Error failure) {
            verifyReservedFailure(failure); throw failure;
        } finally { drawingReserved = false; }
    }

    private CdObjectDrawQueue.LiveDrawSlot readReservedDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireReservedInvocation(Objects.requireNonNull(ref).wholeIndex());
        if (!drawingReserved || reservedPort || queuePort)
            throw new IllegalStateException("No actual reserved queue dereference");
        queuePort = true;
        try {
            var image = reservedImage(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(image.unsignedByte(0), image.unsignedByte(1),
                    image.unsignedWord(8), image.unsignedWord(0x0C), image.unsignedWord(0x0A),
                    (int) image.unsignedLong(4), image.unsignedByte(0x1A), image.unsignedWord(2));
            verifyReservedInvocation(invocation);
            return live;
        } catch (RuntimeException | Error failure) {
            verifyReservedFailure(failure); throw failure;
        } finally { queuePort = false; }
    }

    private void writeReservedOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireReservedInvocation(Objects.requireNonNull(ref).wholeIndex());
        if (!drawingReserved || reservedPort || queuePort || onScreen)
            throw new IllegalStateException("Only actual DrawObject may clear reserved bit7");
        queuePort = true;
        try {
            byte[] image = reservedImage(invocation.whole).copy();
            image[1] &= 0x7F;
            publishReservedImage(invocation, CdAssetBytes.copyOf(image));
            verifyReservedInvocation(invocation);
        } catch (RuntimeException | Error failure) {
            verifyReservedFailure(failure); throw failure;
        } finally { queuePort = false; }
    }

    /**
     * Independent finite store-value verifier for these two native roots, not a
     * gameplay/body/animator replay. Only the actual cached producer can consume it.
     * An empty list preserves the component's earlier unsupported branch.
     */
    private List<ReservedStore> reservedStores(CdAssetBytes source, CdReservedHud.Globals g) {
        var stores = new ArrayList<ReservedStore>();
        int id = source.unsignedByte(0), subtype = source.unsignedByte(0x28);
        int subtype2 = source.unsignedByte(0x29), routine = source.unsignedByte(0x24);
        if (id == 0x1C && subtype < 0x80 && (routine == 0 || routine == 2)) {
            if (routine == 0) {
                stores.add(new ReservedStore(0x24, 1, 2));
                stores.add(new ReservedStore(4, 4, view(CdR12aProfile.Asset.MAP_SPR_HUD).range().nativeAddress()));
                stores.add(new ReservedStore(2, 2, 0x8568));
                stores.add(new ReservedStore(8, 2, 0x90));
                stores.add(new ReservedStore(0x0A, 2, 0x88));
                if (subtype2 != 0) stores.add(new ReservedStore(0x1A, 1, 3));
                else {
                    if (g.debugCheatWord() != 0) stores.add(new ReservedStore(0x1A, 1, 2));
                    if (subtype != 0) {
                        stores.add(new ReservedStore(0x0A, 2, 0x148));
                        stores.add(new ReservedStore(0x1A, 1, 1));
                    }
                }
            }
            if (subtype == 0) {
                if (subtype2 != 0) {
                    int tileHigh = routine == 0 ? 0x85 : source.unsignedByte(2);
                    if (g.ringsWord() != 0) stores.add(new ReservedStore(2, 1, tileHigh & ~0x20));
                    else if ((g.vIntLowByte() & 0x0F) == 0)
                        stores.add(new ReservedStore(2, 1, tileHigh ^ 0x20));
                } else {
                    stores.add(new ReservedStore(0x1A, 1, 0));
                    if (g.debugCheatWord() != 0) stores.add(new ReservedStore(0x1A, 1, 2));
                }
            }
        } else if (id == 0x19 && subtype >= 8) {
            if (g.timeAttackModeByte() != 0) stores.add(new ReservedStore(0, 0, 0));
            else if (subtype == 0x0A && (routine == 0 || routine == 2)) {
                if (routine == 0) {
                    stores.add(new ReservedStore(0x24, 1, 2));
                    stores.add(new ReservedStore(4, 4, view(CdR12aProfile.Asset.MAP_SPR_MONITOR_TIME).range().nativeAddress()));
                    stores.add(new ReservedStore(2, 2, 0x85A8));
                    stores.add(new ReservedStore(8, 2, 0xC4));
                    stores.add(new ReservedStore(0x0A, 2, 0x152));
                }
                stores.add(new ReservedStore(0x1A, 1, 0x12));
                if ((g.timeWarpDirectionByte() & 0x80) == 0) stores.add(new ReservedStore(0x1A, 1, 0x13));
            }
        }
        return List.copyOf(stores);
    }

    /**
     * Immediate capability around one real selected Ring call. The cached algorithm
     * owns pool/bank writes; these guards protect its synchronous external ports and
     * every other owner rather than replaying Init/Main/Collect/AnimateObject.
     */
    private final class RingInvocation {
        private final CdRing body = ring;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR12aTerrainAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Object world;
        private final long generation;
        private final int whole;
        private final int routine;
        private final Values base = values;
        private CdRingGlobals expectedRing = values.ring();
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private final CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private final CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private final CdObjectPlacementRuntime.Status placementStatus = placement.status();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.captureAct2Scroll();
        private final CdObjectDrawQueue.CameraWords preScrollCamera;
        private CdRingGlobals readRing;
        private boolean rewardWritten;
        private boolean soundRequested;
        private boolean drawn;
        private boolean screenCleared;

        private RingInvocation(int whole) {
            this.whole = whole;
            routine = pool.state(whole).routine();
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession();
            generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var camera = presentation.act2ForegroundImage();
            preScrollCamera = new CdObjectDrawQueue.CameraWords(camera.cameraX(), camera.cameraYWord());
        }
    }

    private void runRing(int whole) {
        requireDoorIdle();
        requireLogMosquiIdle();
        requireBoulderPlatformIdle();
        if(activeMonitor!=null || monitorPort || drawingMonitor)
            throw new IllegalStateException("Actual Monitor/Post invocation owns this object cut");
        module.requireAct2LoadingVisit(this);
        if (!runningObjects || servicing || activeReserved != null || activeRing != null || activeSpring != null || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring || queuePort
                || whole < 32 || whole >= 128 || placement.pool().state(whole).id() != 0x10
                || values.phase() != BEGIN_LEVEL || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual live dynamic Ring invocation entry");
        var gates = frame.environment().movement().gates();
        var identity = assets.identity();
        var invocation = new RingInvocation(whole);
        var inputs = new CdRing.Inputs(identity.variant(), identity.stage(), gates.zoneAct() >>> 8,
                invocation.preScrollCamera.xWord(), gates.timeZone(), gates.timeWarpDirection());
        activeRing = invocation;
        try {
            ring.executeSelected(whole, inputs, new CdRing.GlobalsPort() {
                @Override public CdRingGlobals read() { return readRingGlobals(invocation); }
                @Override public void write(CdRingGlobals next) { writeRingGlobals(invocation, next); }
            }, new CdRing.SourceCalls() {
                @Override public void playFmSound(CdRing.FmSound sound) {
                    ringSound(invocation, new SonicCdGameModule.PastRingFm(sound));
                }
                @Override public void subCpuCommand(CdRing.SubCpuCommand command) {
                    ringSound(invocation, new SonicCdGameModule.PastRingSubCpu(command));
                }
                @Override public void drawObject(CdObjectPool pool, int currentWhole) {
                    drawRing(invocation, pool, currentWhole);
                }
            });
            verifyRingInvocation(invocation);
            if (drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                    || invocation.routine == 4 && (!invocation.rewardWritten || !invocation.soundRequested))
                throw new IllegalStateException("Incomplete actual Ring publication");
        } catch (RuntimeException | Error failure) {
            verifyRingFailure(failure);
            throw failure;
        } finally {
            activeRing = null;
        }
    }

    private RingInvocation requireRingInvocation(RingInvocation invocation, CdObjectPool pool, int whole) {
        requireBoulderPlatformIdle();
        if (invocation == null || activeRing != invocation || invocation.pool != pool || pool != placement.pool()
                || invocation.whole != whole || whole < 32 || whole >= 128 || pool.state(whole).id() != 0x10
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Ring callback for this live physical pointer");
        verifyRingInvocation(invocation);
        return invocation;
    }

    private void verifyRingInvocation(RingInvocation invocation) {
        requireBoulderPlatformIdle();
        module.requireAct2LoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeRing != invocation || !runningObjects || servicing || activeReserved != null || activeSpring != null || springPort || drawingSpring || reservedPort || drawingReserved
                || invocation.body != ring || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.sourceStage != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared)
                || !placement.status().equals(invocation.placementStatus)
                || !presentation.sameAct2Scroll(invocation.scroll) || sounds != invocation.expectedSounds
                || !values.equals(ringValues(invocation.base, invocation.expectedRing)))
            throw new IllegalStateException("Ring changed another actual owner or retained a stale source cut");
    }

    private static Values ringValues(Values v, CdRingGlobals ring) {
        return new Values(v.phase(), v.section(), v.cpuPalette(), v.fadePalette(), v.waterPalette(), v.displayedPalette(),
                v.cpuSat(), v.displayedSat(), v.displayedHScroll(), v.displayedVScrollLongBits(),
                v.foregroundPlane(), ring, v.level(), v.globals(), v.hudObjects(), v.fadeProgress(), v.calls(), v.loop());
    }

    /** Complete pool/bank nonwrite proof for a callback, with only DrawObject's one bit-clear exception. */
    private void verifyRingCallback(RingInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                    CdObjectState cleared) {
        verifyRingInvocation(invocation);
        var now = placement.capture();
        var expected = new ArrayList<>(before.pool().slots());
        if (cleared != null) expected.set(invocation.whole - 32, cleared);
        if (!now.status().equals(before.status()) || !now.pool().slots().equals(expected)
                || !sameBytes(now.savedFlags(), before.savedFlags()))
            throw new IllegalStateException("Ring callback changed its pool or saved bank outside the native bit7 clear");
    }

    private void verifyRingFailure(Throwable original) {
        try {
            if (activeRing != null) verifyRingInvocation(activeRing);
        } catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private void verifyRingCallbackFailure(RingInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                           CdObjectState cleared, Throwable original) {
        try { verifyRingCallback(invocation, before, cleared); }
        catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private CdRingGlobals readRingGlobals(RingInvocation invocation) {
        requireRingInvocation(invocation, invocation.pool, invocation.whole);
        if (ringPort || drawingRing || queuePort || invocation.readRing != null || invocation.drawn)
            throw new IllegalStateException("Ring globals read is not at its actual once-only cut");
        var before = placement.capture();
        ringPort = true;
        try {
            var current = values.ring();
            invocation.readRing = current;
            verifyRingCallback(invocation, before, null);
            return current;
        } catch (RuntimeException | Error failure) {
            verifyRingCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { ringPort = false; }
    }

    private void writeRingGlobals(RingInvocation invocation, CdRingGlobals next) {
        requireRingInvocation(invocation, invocation.pool, invocation.whole);
        if (ringPort || drawingRing || queuePort || invocation.routine != 4 || invocation.readRing == null
                || invocation.rewardWritten || invocation.soundRequested || invocation.drawn
                || values.ring() != invocation.readRing || next == null)
            throw new IllegalStateException("Ring reward requires its actual routine4 read and once-only publication");
        requireRingReward(invocation.readRing, next);
        var before = placement.capture();
        ringPort = true;
        try {
            var ready = ringValues(values, next);
            values = ready;
            invocation.expectedRing = next;
            invocation.rewardWritten = true;
            verifyRingCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifyRingCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { ringPort = false; }
    }

    /**
     * CollectRing:290-306 scalar publication contract, independent of the producer.
     * Word/byte wrap and old BSET bits matter; no gameplay body is called to authorize a value.
     */
    private static void requireRingReward(CdRingGlobals before, CdRingGlobals next) {
        int rings = (before.rings() + 1) & 0xFFFF;
        int flags = before.livesFlags();
        if (rings >= 100) {
            flags |= 2;
            if ((before.livesFlags() & 2) != 0 && rings >= 200) flags |= 4;
        }
        boolean life = ringGainsLife(before);
        if (next.rings() != rings || next.livesFlags() != flags
                || next.lives() != ((before.lives() + (life ? 1 : 0)) & 0xFF)
                || next.updateHudRings() != (before.updateHudRings() | 1)
                || next.updateHudLives() != ((before.updateHudLives() + (life ? 1 : 0)) & 0xFF)
                || next.timeStopTimer() != before.timeStopTimer()
                || next.ringAnimationTimer() != before.ringAnimationTimer()
                || next.ringAnimationFrame() != before.ringAnimationFrame())
            throw new IllegalStateException("Ring globals are not the reached wrapped native reward");
    }

    private static boolean ringGainsLife(CdRingGlobals before) {
        int rings = (before.rings() + 1) & 0xFFFF;
        return rings >= 100 && ((before.livesFlags() & 2) == 0
                || rings >= 200 && (before.livesFlags() & 4) == 0);
    }

    private void ringSound(RingInvocation invocation, SonicCdGameModule.PastSound sound) {
        requireRingInvocation(invocation, invocation.pool, invocation.whole);
        if (ringPort || drawingRing || queuePort || invocation.routine != 4 || !invocation.rewardWritten
                || invocation.soundRequested || invocation.drawn
                || (ringGainsLife(invocation.readRing)
                ? !(sound instanceof SonicCdGameModule.PastRingSubCpu subCpu && subCpu.command() == CdRing.SubCpuCommand.SCMD_YESSFX)
                : !(sound instanceof SonicCdGameModule.PastRingFm s && s.sound() == CdRing.FmSound.FM_RING)))
            throw new IllegalStateException("Ring sound is not the actual once-only postreward source call");
        var before = placement.capture();
        ringPort = true;
        try {
            appendSound(sound);
            invocation.expectedSounds = sounds;
            invocation.soundRequested = true;
            verifyRingCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifyRingCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { ringPort = false; }
    }

    private void drawRing(RingInvocation invocation, CdObjectPool pool, int whole) {
        requireRingInvocation(invocation, pool, whole);
        if (ringPort || drawingRing || queuePort || invocation.drawn
                || invocation.routine == 4 && !invocation.soundRequested)
            throw new IllegalStateException("Ring DrawObject is not at its actual once-only source cut");
        var before = placement.capture();
        var current = pool.state(whole);
        var cleared = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        invocation.drawn = true;
        drawingRing = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(whole),
                    current.spriteFlags(), current.centreX(), current.centreY(),
                    current.unsignedByte(CdObjectState.ByteField.Y_RADIUS),
                    current.unsignedByte(CdObjectState.ByteField.WIDTH),
                    current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.preScrollCamera);
            if (!invocation.screenCleared) throw new IllegalStateException("Ring DrawObject did not clear its live bit7");
            verifyRingCallback(invocation, before, cleared);
        } catch (RuntimeException | Error failure) {
            verifyRingCallbackFailure(invocation, before, invocation.screenCleared ? cleared : null, failure);
            throw failure;
        } finally { drawingRing = false; }
    }

    private CdObjectDrawQueue.LiveDrawSlot readRingDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = activeRing;
        requireRingInvocation(invocation, placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingRing || ringPort || queuePort)
            throw new IllegalStateException("No actual Ring queue dereference");
        var before = placement.capture();
        queuePort = true;
        try {
            var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(),
                    current.centreX(), current.centreY(), current.word(CdObjectState.WordField.X_FRACTION),
                    current.longBits(CdObjectState.LongField.MAPPINGS),
                    current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifyRingCallback(invocation, before, null);
            return live;
        } catch (RuntimeException | Error failure) {
            verifyRingCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { queuePort = false; }
    }

    private void writeRingOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = activeRing;
        requireRingInvocation(invocation, placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingRing || ringPort || queuePort || onScreen || invocation.screenCleared)
            throw new IllegalStateException("Only actual DrawObject may clear this live Ring's bit7 once");
        var before = placement.capture();
        var current = invocation.pool.state(invocation.whole);
        var next = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        queuePort = true;
        try {
            invocation.pool.write(next);
            invocation.screenCleared = true;
            verifyRingCallback(invocation, before, next);
        } catch (RuntimeException | Error failure) {
            verifyRingCallbackFailure(invocation, before, invocation.screenCleared ? next : null, failure);
            throw failure;
        } finally { queuePort = false; }
    }


    /** Actual cached Spring producer scope; values and returned effects confer no call authority. */
    private final class SpringInvocation {
        private final CdSpring body = spring;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR12aTerrainAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final Values base = values;
        private final CdObjectPlacementRuntime.Status status = placement.status();
        private final CdAssetBytes savedFlags = placement.capture().savedFlags();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.captureAct2Scroll();
        private final CdObjectDrawQueue.CameraWords camera;
        private CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private CdSolidObject.Result solidResult;
        private boolean launched;
        private boolean sounded;
        private boolean drawn;
        private boolean screenCleared;

        private SpringInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession();
            generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.act2ForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
        }
    }

    private void runSpring(int whole) {
        requireDoorIdle();
        requireLogMosquiIdle();
        requireBoulderPlatformIdle();
        if(activeMonitor!=null || monitorPort || drawingMonitor)
            throw new IllegalStateException("Actual Monitor/Post invocation owns this object cut");
        module.requireAct2LoadingVisit(this);
        if (!runningObjects || servicing || activeReserved != null || activeRing != null
                || activeSpring != null || reservedPort || drawingReserved || ringPort || drawingRing
                || springPort || drawingSpring || queuePort || whole < 32 || whole >= 128
                || placement.pool().state(whole).id() != CdSpring.OBJECT_ID || values.phase() != BEGIN_LEVEL
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual live Past Spring invocation entry");
        var invocation = new SpringInvocation(whole);
        activeSpring = invocation;
        try {
            var env = frame.environment();
            var result = spring.run(whole, new CdSpring.Globals(invocation.camera.xWord()),
                    new CdSpring.SolidInputs(springPlayer(), new CdSolidObject.Gates(
                            env.globals().debugMode(), env.movement().gates().miniSonic()), springGlobals()));
            verifySpringInvocation(invocation);
            if (!invocation.drawn || !result.state().equals(placement.pool().state(whole))
                    || !result.finalPlayer().equals(springPlayer()) || !result.finalGlobals().equals(springGlobals())
                    || invocation.launched != invocation.sounded)
                throw new IllegalStateException("Incomplete actual Spring return");
        } catch (RuntimeException | Error failure) {
            verifySpringFailure(failure); throw failure;
        } finally { activeSpring = null; }
    }

    private SpringInvocation requireSpringInvocation(CdObjectPool pool, int whole) {
        requireBoulderPlatformIdle();
        var invocation = activeSpring;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128 || pool.state(whole).id() != CdSpring.OBJECT_ID
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Spring callback at this physical pointer");
        verifySpringInvocation(invocation);
        return invocation;
    }

    private void verifySpringInvocation(SpringInvocation invocation) {
        requireBoulderPlatformIdle();
        module.requireAct2LoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeSpring != invocation || !runningObjects || servicing || activeReserved != null || activeRing != null || reservedPort || drawingReserved || ringPort || drawingRing
                || invocation.body != spring || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.sourceStage != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared)
                || !placement.status().equals(invocation.status)
                || !sameBytes(placement.capture().savedFlags(), invocation.savedFlags)
                || !presentation.sameAct2Scroll(invocation.scroll) || sounds != invocation.expectedSounds
                || values != invocation.base)
            throw new IllegalStateException("Spring changed another owner or retained a stale actual cut");
    }

    private void verifySpringFailure(Throwable original) {
        try { if (activeSpring != null) verifySpringInvocation(activeSpring); }
        catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private CdSolidObject.Player springPlayer() {
        var s = frame.state(); var h = s.header();
        return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(), h.tile(), h.priority(),
                s.timers().hurt(), s.rotationDistance()), h.slotId(), s.objectLinks().pushObject(), s.objectLinks().standObject());
    }

    private CdSolidObject.Globals springGlobals() {
        var env = frame.environment(); var g = env.movement().globals();
        return new CdSolidObject.Globals(env.globals().invincible(), g.scoreChain(), g.primaryAngle(), g.secondaryAngle());
    }

    /** Complete native-byte nonwrites, including physical identity; aliases do not expand write authority. */
    private static void validateSpringSolidImage(CdObjectState before, CdObjectState after, boolean own,
                                                 Set<CdObjectState.ByteField> writes) {
        if (!before.identity().equals(after.identity()))
            throw new IllegalStateException("Spring Solid changed a physical identity");
        if (own && writes.stream().anyMatch(f -> f != CdObjectState.ByteField.FLAGS && f != CdObjectState.ByteField.SOLID_TYPE))
            throw new IllegalStateException("Spring Solid declared an unowned object byte");
        byte[] old = before.copyBytes(), next = after.copyBytes();
        for (int at = 0; at < 64; at++) {
            int mask = at == 0x22 ? 0x28 : own && at == 0x25 ? 0xFF : 0;
            if (own && at == 0x22 && !writes.contains(CdObjectState.ByteField.FLAGS)) mask = 0;
            if (own && at == 0x25 && !writes.contains(CdObjectState.ByteField.SOLID_TYPE)) mask = 0;
            if (((old[at] ^ next[at]) & ~mask & 0xFF) != 0)
                throw new IllegalStateException("Spring Solid changed unowned full-image byte " + at);
        }
        if (own && writes.contains(CdObjectState.ByteField.SOLID_TYPE)
                && after.unsignedByte(CdObjectState.ByteField.SOLID_TYPE) != 0
                && after.unsignedByte(CdObjectState.ByteField.SOLID_TYPE) != 2)
            throw new IllegalStateException("Spring Solid type differs from its plain/bottom native stores");
    }

    /** One genuine completed Solid call; prepare every cross-owner replacement before first assignment. */
    private void commitSpringSolid(CdSpring.SolidCall call) {
        Objects.requireNonNull(call);
        var invocation = requireSpringInvocation(placement.pool(), call.result().object().identity().wholeIndex());
        if (springPort || drawingSpring || queuePort || invocation.solidResult != null || invocation.launched
                || invocation.sounded || invocation.drawn || invocation.entry.unsignedByte(CdObjectState.ByteField.ROUTINE_2) == 5)
            throw new IllegalStateException("Spring Solid is not at its once-only native cut");
        var pool = invocation.pool; var cut = call.callCut(); var result = call.result();
        pool.validateRestore(cut);
        if (!pool.capture().slots().equals(cut.slots()))
            throw new IllegalStateException("Spring Solid pool changed after its actual call cut");
        var before = cut.slots().get(invocation.whole - 32);
        var entry = switch (before.routine()) {
            case 2, 8, 20 -> CdSolidObject.Entry.SOLID_OBJECT;
            case 14 -> CdSolidObject.Entry.BTM_SOLID_OBJECT;
            default -> throw new IllegalStateException("Spring Solid lacks its reached Main source");
        };
        if (before.id() != CdSpring.OBJECT_ID || result.object().id() != CdSpring.OBJECT_ID
                || (before.spriteFlags() & 0x80) == 0 || result.entry() != entry
                || !call.expectedPlayer().equals(springPlayer()) || !call.expectedGlobals().equals(springGlobals()))
            throw new IllegalStateException("Spring Solid entry/player/global cut differs");
        validateSpringSolidImage(before, result.object(), true, result.objectWrites());
        var images = new ArrayList<CdObjectState>(); images.add(result.object());
        for (var image : result.otherSlots()) {
            validateSpringSolidImage(cut.slots().get(image.identity().wholeIndex() - 32), image, false, Set.of());
            images.add(image);
        }
        var readyPool = CdObjectPool.overlay(cut, images);
        var readyFrame = frame.prepareObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result);
        var readyActor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), readyFrame.finalState(), invocation.actor.display());
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), readyFrame.finalEnvironment(),
                invocation.shared.artState(), invocation.shared.paused(), invocation.shared.vIntClock());
        var ledger = new ArrayList<>(sounds);
        for (var fm : result.fmRequests())
            ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
        var readySounds = List.copyOf(ledger);
        springPort = true;
        try {
            verifySpringInvocation(invocation);
            if (!pool.capture().slots().equals(cut.slots())) throw new IllegalStateException("Stale Spring publication pool");
            frame.validatePreparedObjectSolidPublication(readyFrame);
            pool.restore(readyPool);
            frame.applyPreparedObjectSolidPublication(readyFrame);
            sounds = readySounds;
            invocation.actor = readyActor; invocation.shared = readyShared;
            invocation.expectedSounds = readySounds; invocation.solidResult = result;
            verifySpringInvocation(invocation);
            if (!pool.capture().slots().equals(readyPool.slots()))
                throw new IllegalStateException("Spring completed Solid images were not adopted exactly");
        } catch (RuntimeException | Error failure) {
            verifySpringFailure(failure); throw failure;
        } finally { springPort = false; }
    }

    private void commitSpringLaunch(CdSpring.LaunchCall call) {
        Objects.requireNonNull(call);
        var invocation = requireSpringInvocation(placement.pool(), call.identity().wholeIndex());
        if (springPort || drawingSpring || queuePort || invocation.solidResult == null
                || invocation.launched || invocation.sounded || invocation.drawn)
            throw new IllegalStateException("Spring launch is not after its one actual Solid");
        var solid = invocation.solidResult;
        int routine = invocation.entry.routine();
        boolean trigger = switch (routine) {
            case 2, 14 -> solid.d0().orElseThrow() != 0;
            case 8 -> (solid.object().flags() & 0x20) != 0;
            case 20 -> solid.d0().orElseThrow() != 0 || (solid.object().flags() & 0x20) != 0;
            default -> false;
        };
        int clear = routine == 8 ? 0x20 : routine == 20 ? 0x28 : 8;
        var expectedSource = solid.object().moveByte(CdObjectState.ByteField.ROUTINE, routine + 2)
                .moveByte(CdObjectState.ByteField.FLAGS, solid.object().flags() & ~clear);
        if (!trigger || !expectedSource.equals(invocation.pool.state(invocation.whole))
                || !call.expectedPlayer().equals(solid.player()) || !call.expectedGlobals().equals(solid.globals()))
            throw new IllegalStateException("Spring action source prefix differs from the genuine Solid return");
        Set<CdSpring.LaunchPlayerField> expectedWrites = switch (routine) {
            case 2 -> Set.of(CdSpring.LaunchPlayerField.CENTRE_Y, CdSpring.LaunchPlayerField.Y_VELOCITY,
                    CdSpring.LaunchPlayerField.FLAGS, CdSpring.LaunchPlayerField.ANIMATION);
            case 8 -> {
                var fields = EnumSet.of(CdSpring.LaunchPlayerField.X_VELOCITY, CdSpring.LaunchPlayerField.CENTRE_X,
                        CdSpring.LaunchPlayerField.FLAGS, CdSpring.LaunchPlayerField.MOVE_LOCK,
                        CdSpring.LaunchPlayerField.GROUND_VELOCITY, CdSpring.LaunchPlayerField.ANGLE);
                if ((call.expectedPlayer().actor().motion().flags() & 4) == 0) fields.add(CdSpring.LaunchPlayerField.ANIMATION);
                yield Set.copyOf(fields);
            }
            case 14 -> Set.of(CdSpring.LaunchPlayerField.CENTRE_Y, CdSpring.LaunchPlayerField.Y_VELOCITY, CdSpring.LaunchPlayerField.FLAGS);
            case 20 -> Set.of(CdSpring.LaunchPlayerField.CENTRE_X, CdSpring.LaunchPlayerField.CENTRE_Y,
                    CdSpring.LaunchPlayerField.X_VELOCITY, CdSpring.LaunchPlayerField.Y_VELOCITY, CdSpring.LaunchPlayerField.FLAGS);
            default -> throw new IllegalStateException("No native Spring action");
        };
        if (!call.playerWrites().equals(expectedWrites)) throw new IllegalStateException("Spring launch write sites differ");
        var before = placement.capture();
        frame.validateObjectSpringLaunch(call);
        springPort = true;
        try {
            frame.applyObjectSpringLaunch(call);
            var after = frame.captureActor(); var a = invocation.actor.state(); var b = after.state();
            if (after.owner() != invocation.actor.owner() || !after.display().equals(invocation.actor.display())
                    || !b.motion().equals(call.finalPlayer().actor().motion()) || !b.animation().equals(a.animation())
                    || !b.header().equals(a.header()) || !b.timers().equals(a.timers()) || !b.history().equals(a.history())
                    || b.rotationDistance() != a.rotationDistance() || !b.objectLinks().equals(a.objectLinks())
                    || b.rotationAngle() != a.rotationAngle())
                throw new IllegalStateException("Spring launch changed an unowned complete Actor value");
            invocation.actor = after; invocation.launched = true;
            verifySpringCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifySpringFailure(failure); throw failure;
        } finally { springPort = false; }
    }

    private void playSpringFm(CdSpring.FmSound sound) {
        var invocation = requireSpringInvocation(placement.pool(), activeSpring == null ? -1 : activeSpring.whole);
        if (springPort || drawingSpring || queuePort || !invocation.launched || invocation.sounded || invocation.drawn
                || sound != CdSpring.FmSound.FM_SPRING)
            throw new IllegalStateException("Spring sound is not its once-only postlaunch call");
        var before = placement.capture(); var ledger = new ArrayList<>(sounds);
        ledger.add(new SonicCdGameModule.PastSpringFm(sound)); var ready = List.copyOf(ledger);
        springPort = true;
        try {
            sounds = ready; invocation.expectedSounds = ready; invocation.sounded = true;
            verifySpringCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifySpringFailure(failure); throw failure;
        } finally { springPort = false; }
    }

    private void verifySpringCallback(SpringInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                       CdObjectState cleared) {
        verifySpringInvocation(invocation);
        var now = placement.capture(); var expected = new ArrayList<>(before.pool().slots());
        if (cleared != null) expected.set(invocation.whole - 32, cleared);
        if (!now.status().equals(before.status()) || !now.pool().slots().equals(expected)
                || !sameBytes(now.savedFlags(), before.savedFlags()))
            throw new IllegalStateException("Spring callback changed pool/bank outside the actual bit7 clear");
    }

    private void verifySpringCallbackFailure(SpringInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                              CdObjectState cleared, Throwable original) {
        try { verifySpringCallback(invocation, before, cleared); }
        catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private void drawSpring(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireSpringInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (springPort || drawingSpring || queuePort || invocation.drawn || invocation.launched != invocation.sounded)
            throw new IllegalStateException("Spring DrawObject is not at its actual once-only cut");
        var before = placement.capture(); var current = pool.state(invocation.whole);
        var cleared = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        invocation.drawn = true; drawingSpring = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(),
                    current.unsignedByte(CdObjectState.ByteField.Y_RADIUS), current.unsignedByte(CdObjectState.ByteField.WIDTH),
                    current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            if (!invocation.screenCleared) throw new IllegalStateException("Spring DrawObject did not clear bit7");
            verifySpringCallback(invocation, before, cleared);
        } catch (RuntimeException | Error failure) {
            try { verifySpringCallback(invocation, before, invocation.screenCleared ? cleared : null); }
            catch (RuntimeException | Error verification) { if (verification != failure) failure.addSuppressed(verification); }
            throw failure;
        } finally { drawingSpring = false; }
    }

    private CdObjectDrawQueue.LiveDrawSlot readSpringDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireSpringInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingSpring || springPort || queuePort) throw new IllegalStateException("No actual Spring queue read");
        var before = placement.capture(); queuePort = true;
        try {
            var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(), current.centreX(), current.centreY(),
                    current.word(CdObjectState.WordField.X_FRACTION), current.longBits(CdObjectState.LongField.MAPPINGS),
                    current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifySpringCallback(invocation, before, null); return live;
        } catch (RuntimeException | Error failure) {
            verifySpringCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { queuePort = false; }
    }

    private void writeSpringOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireSpringInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingSpring || springPort || queuePort || onScreen || invocation.screenCleared)
            throw new IllegalStateException("Only actual Spring DrawObject may clear bit7 once");
        var before = placement.capture(); var current = invocation.pool.state(invocation.whole);
        var next = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        queuePort = true;
        try {
            invocation.pool.write(next); invocation.screenCleared = true;
            verifySpringCallback(invocation, before, next);
        } catch (RuntimeException | Error failure) {
            try { verifySpringCallback(invocation, before, invocation.screenCleared ? next : null); }
            catch (RuntimeException | Error verification) { if (verification != failure) failure.addSuppressed(verification); }
            throw failure;
        } finally { queuePort = false; }
    }


    /** Asset-free CheckAnimalPrescence/CheckObjDespawn; no visible Animal body is admitted here. */
    private enum AnimalRead { PRESENCE_TIME_ZONE, GOOD_FUTURE, PROJECTOR, CAMERA,
                              WARP_TIME_ZONE, PERIOD_TIME_ZONE, DIRECTION, DONE, BODY }
    private final class AnimalInvocation implements CdAnimal.Reads {
        private final CdAnimal body = animal;
        private final int whole;
        private final CdObjectPool pool = placement.pool();
        private final CdObjectPlacementRuntime.Snapshot before = placement.capture();
        private final CdObjectState source;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR12aTerrainAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private final CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.captureAct2Scroll();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Values base = values;
        private final List<SonicCdGameModule.PastSound> ledger = sounds;
        private final Object world;
        private final long generation;
        private AnimalRead next;
        private CdAnimal.UnsupportedBoundary unsupported;
        private CdAnimal.DespawnEntry despawnEntry;
        private int period = -1, bankIndex = -1;
        private boolean delete, plainDelete, active = true, reading;
        private AnimalInvocation(int whole) {
            this.whole = whole; source = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            if ((source.subtype() & 0x80) != 0) {
                next = AnimalRead.BODY; unsupported = CdAnimal.UnsupportedBoundary.HOLOGRAM_BODY;
            } else next = AnimalRead.PRESENCE_TIME_ZONE;
        }
        @Override public int timeZone() {
            if (next != AnimalRead.PRESENCE_TIME_ZONE && next != AnimalRead.WARP_TIME_ZONE
                    && next != AnimalRead.PERIOD_TIME_ZONE)
                throw new IllegalStateException("Animal requested an unreached time-zone operand");
            return read(next);
        }
        @Override public int goodFuture() { return read(AnimalRead.GOOD_FUTURE); }
        @Override public int projDestroyed() { return read(AnimalRead.PROJECTOR); }
        @Override public int timeWarpDir() { return read(AnimalRead.DIRECTION); }
        @Override public int cameraXWord() { return read(AnimalRead.CAMERA); }
        private int read(AnimalRead site) {
            verifyAnimalContext(this);
            if (reading || next != site || site == AnimalRead.DONE || site == AnimalRead.BODY)
                throw new IllegalStateException("Animal read is outside its actual source sequence");
            verifyAnimalReadImages(this); reading = true;
            try {
                int value = switch (site) {
                    case PRESENCE_TIME_ZONE, WARP_TIME_ZONE, PERIOD_TIME_ZONE -> frame.environment().movement().gates().timeZone();
                    case GOOD_FUTURE -> values.globals().retainedDev().goodFuture();
                    case PROJECTOR -> values.globals().retainedDev().projDestroyed();
                    case CAMERA -> presentation.act2ForegroundImage().cameraX();
                    case DIRECTION -> frame.environment().movement().gates().timeWarpDirection();
                    default -> throw new IllegalStateException("Animal operand has no native read");
                };
                advance(site, value); verifyAnimalContext(this); verifyAnimalReadImages(this); return value;
            } catch (RuntimeException | Error original) {
                try { verifyAnimalContext(this); verifyAnimalReadImages(this); }
                catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
                throw original;
            } finally { reading = false; }
        }
        private void advance(AnimalRead site, int value) {
            switch (site) {
                case PRESENCE_TIME_ZONE -> next = (byte) value >= 2 ? AnimalRead.GOOD_FUTURE : AnimalRead.PROJECTOR;
                case GOOD_FUTURE -> {
                    if (value != 0) { next = AnimalRead.BODY; unsupported = CdAnimal.UnsupportedBoundary.GOOD_FUTURE_BODY; }
                    else { delete = plainDelete = true; next = AnimalRead.DONE; }
                }
                case PROJECTOR -> {
                    if (value != 0) { next = AnimalRead.BODY; unsupported = CdAnimal.UnsupportedBoundary.PROJECTOR_DESTROYED_BODY; }
                    else next = (source.spriteFlags() & 0x80) == 0 ? AnimalRead.CAMERA : AnimalRead.WARP_TIME_ZONE;
                }
                case CAMERA -> {
                    int distance = ((source.centreX() & 0xFF80) - (((value - 0x80) & 0xFFFF) & 0xFF80)) & 0xFFFF;
                    if (distance > 0x280) despawn(CdAnimal.DespawnEntry.CURRENT_X_OFFSCREEN);
                    else next = AnimalRead.WARP_TIME_ZONE;
                }
                case WARP_TIME_ZONE -> {
                    if ((value & 0x80) != 0) despawn(CdAnimal.DespawnEntry.TIME_WARP);
                    else next = AnimalRead.DONE;
                }
                case PERIOD_TIME_ZONE -> {
                    period = value & 0x7F;
                    if ((value & 0x80) != 0) next = AnimalRead.DIRECTION;
                    else finishPeriod(period);
                }
                case DIRECTION -> {
                    int oldPeriod = (short) ((period - (byte) value) & 0xFFFF);
                    finishPeriod(oldPeriod < 0 ? 0 : Math.min(oldPeriod, 2));
                }
                default -> throw new IllegalStateException("Animal advanced an unread operand");
            }
        }
        private void despawn(CdAnimal.DespawnEntry entry) {
            delete = true; despawnEntry = entry;
            next = source.savedFlagsId() == 0 ? AnimalRead.DONE : AnimalRead.PERIOD_TIME_ZONE;
        }
        private void finishPeriod(int value) {
            period = value; bankIndex = 2 + 3 * source.savedFlagsId() + value; next = AnimalRead.DONE;
        }
    }

    private void runAnimal(int whole) {
        requireDoorIdle();
        requireLogMosquiIdle();
        requireBoulderPlatformIdle();
        if(activeMonitor!=null || monitorPort || drawingMonitor)
            throw new IllegalStateException("Actual Monitor/Post invocation owns this object cut");
        module.requireAct2LoadingVisit(this);
        if (!runningObjects || servicing || activeDrain != null
                || activeReserved != null || activeRing != null || activeSpring != null || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || queuePort || whole < 32 || whole >= 128
                || placement.pool().state(whole).id() != CdAnimal.OBJECT_ID || values.phase() != BEGIN_LEVEL
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Past Animal dispatch");
        var invocation = new AnimalInvocation(whole);
        try {
            var result = animal.runWithReads(whole, invocation);
            verifyAnimalMutation(invocation, true);
            if (invocation.next != AnimalRead.DONE || !result.state().equals(invocation.pool.state(whole)))
                throw new IllegalStateException("Animal returned before its exact native presence/despawn output");
            var effects = new ArrayList<CdAnimal.Effect>();
            effects.add(new CdAnimal.CallReturned(CdAnimal.NativeCall.CHECK_ANIMAL_PRESENCE));
            if (invocation.plainDelete) {
                effects.add(new CdAnimal.Deleted()); effects.add(new CdAnimal.CallReturned(CdAnimal.NativeCall.DELETE_OBJECT));
            } else {
                if (invocation.delete) effects.add(new CdAnimal.Despawn(invocation.despawnEntry,
                        invocation.source.savedFlagsId(), invocation.period));
                effects.add(new CdAnimal.CallReturned(CdAnimal.NativeCall.CHECK_OBJ_DESPAWN));
            }
            if (!effects.equals(result.effects()) || result.deleted() != invocation.delete)
                throw new IllegalStateException("Animal completed metadata disagrees with its actual native branch");
        } catch (RuntimeException | Error original) {
            try {
                verifyAnimalMutation(invocation, false);
                if (original instanceof CdAnimal.UnsupportedAnimal stopped
                        && (stopped.wholeIndex() != whole || stopped.boundary() != invocation.unsupported))
                    throw new IllegalStateException("Animal stopped at another native predicate");
            } catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
            throw original;
        } finally { invocation.active = false; }
    }

    private void verifyAnimalContext(AnimalInvocation invocation) {
        requireBoulderPlatformIdle();
        module.requireAct2LoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (!invocation.active || invocation.body != animal || !runningObjects || servicing || activeDrain != null || activeReserved != null || activeRing != null
                || activeSpring != null || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || queuePort || invocation.pool != placement.pool()
                || invocation.sourceFrame != frame || invocation.sourceAssets != assets || invocation.sourceStage != frame.stageSource()
                || invocation.queue != drawQueue || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || values != invocation.base || values.phase() != BEGIN_LEVEL || sounds != invocation.ledger
                || !placement.status().equals(invocation.before.status()) || !presentation.sameAct2Scroll(invocation.scroll)
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared))
            throw new IllegalStateException("Animal read changed another owner or retained a stale actual source cut");
        invocation.pool.validateRestore(invocation.before.pool());
    }
    private void verifyAnimalReadImages(AnimalInvocation invocation) {
        var now = placement.capture();
        if (!now.pool().slots().equals(invocation.before.pool().slots())
                || !sameBytes(now.savedFlags(), invocation.before.savedFlags())
                || !now.status().equals(invocation.before.status()))
            throw new IllegalStateException("Animal scalar accessor changed actual full96/saved/status");
    }
    private void verifyAnimalMutation(AnimalInvocation invocation, boolean completed) {
        verifyAnimalContext(invocation);
        var now = placement.capture();
        byte[] originalBank = invocation.before.savedFlags().copy(), expectedBank = originalBank.clone();
        byte[] originalSource = invocation.source.copyBytes(), cleared = new byte[CdObjectState.BYTE_SIZE];
        boolean ready = invocation.next == AnimalRead.DONE && invocation.unsupported == null;
        boolean bank = ready && invocation.delete && !invocation.plainDelete && invocation.source.savedFlagsId() != 0;
        boolean owned = !bank || invocation.period >= 0 && invocation.period <= 2
                && invocation.bankIndex >= 2 && invocation.bankIndex < expectedBank.length;
        if (bank && owned) expectedBank[invocation.bankIndex] &= 0x7F;
        boolean expectedClear = ready && invocation.delete && owned;
        byte[] actual = now.pool().slots().get(invocation.whole - 32).copyBytes();
        byte[] actualBank = now.savedFlags().copy();
        boolean beforePair = Arrays.equals(actual, originalSource) && Arrays.equals(actualBank, originalBank);
        boolean afterPair = Arrays.equals(actual, expectedClear ? cleared : originalSource) && Arrays.equals(actualBank, expectedBank);
        boolean bankPrefix = expectedClear && bank && Arrays.equals(actual, originalSource) && Arrays.equals(actualBank, expectedBank);
        if (completed ? !ready || !owned || !afterPair : !beforePair && !afterPair && !bankPrefix)
            throw new IllegalStateException("Animal pool/bank output exceeds its genuine reached native prefix");
        for (int i = 0; i < 96; i++) if (i != invocation.whole - 32
                && !now.pool().slots().get(i).equals(invocation.before.pool().slots().get(i)))
            throw new IllegalStateException("Animal changed another physical image");
        if (!now.pool().slots().get(invocation.whole - 32).identity().equals(invocation.source.identity())
                || !now.status().equals(invocation.before.status()))
            throw new IllegalStateException("Animal changed source identity or placement cursors");
    }


    private CdObjectDrawQueue.LiveDrawSlot primaryLiveSlot() {
        if (activeDrain == null || !queuePort || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.DRAINING)
            throw new IllegalStateException("Primary live pointer dereference belongs only to actual DrawObjects");
        var state = frame.state(); var motion = state.motion(); var header = state.header();
        // Native oYScr WORD $0A aliases X's fraction, not the world-Y fraction.
        return new CdObjectDrawQueue.LiveDrawSlot(header.slotId(), motion.spriteFlags(),
                motion.centreX16_16() >>> 16, motion.centreY16_16() >>> 16, motion.centreX16_16() & 0xFFFF,
                header.mappingAddress(), state.animation().mapFrame(), header.tile());
    }
    private static CdPlayerFrameRuntime.ActorState primaryScreenActor(CdPlayerFrameRuntime.ActorState before, boolean value) {
        var state = before.state(); var m = state.motion(); int bit = value ? 0x80 : 0;
        var motion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                m.routine(), m.angle(), m.flags(), (m.spriteFlags() & 0x7F) | bit, m.xRadius(), m.yRadius(),
                m.animation(), m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(),
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var next = new CdPlayerBody.State(motion, state.animation(), state.header(), state.timers(), state.history(),
                state.rotationDistance(), state.objectLinks(), state.rotationAngle());
        var display = before.display().map(p -> new CdPlayerBody.DisplayPublication(p.queued(), p.mappingAddress(), p.tile(),
                p.priority(), p.width(), p.mapFrame(), (p.spriteFlags() & 0x7F) | bit, p.centreX16_16(), p.centreY16_16()));
        return new CdPlayerFrameRuntime.ActorState(before.owner(), next, display);
    }
    private void mainLevelMusic() {
        requireCallback();
        // The real8 Finish/count has completed; this callback precedes paused
        // reset admission and preserves its native music prefix on a later fault.
        representedServiceCompleted(); call(ORDINARY_SERVICE);
        int old = values.globals().pauseMusicBit().orElseThrow();
        if (frame.paused()) {
            appendSound(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ChargeSound(
                    CdPlayerCharge.Sound.FM_CHARGESTOP)));
            globals(g -> g.withPauseMusicBit(old | 0x80));
            if ((old & 0x80) != 0) return;
        } else {
            globals(g -> g.withPauseMusicBit(old & 0x7F));
            if ((old & 0x80) == 0) return;
        }
        var e = frame.environment();
        boolean pcm = (e.movement().gates().timeZone() & 0x7F) == 0
                && e.globals().invincible() == 0 && e.movement().gates().speedShoes() == 0;
        var command = frame.paused()
                ? pcm ? SonicCdGameModule.PastMusicCommand.SCMD_PAUSEPCM : SonicCdGameModule.PastMusicCommand.SCMD_PAUSECDA
                : pcm ? SonicCdGameModule.PastMusicCommand.SCMD_UNPAUSEPCM : SonicCdGameModule.PastMusicCommand.SCMD_UNPAUSECDA;
        appendSound(new SonicCdGameModule.PastSubCpuSound(command));
    }
    private void appendSound(SonicCdGameModule.PastSound sound) {
        var next = new ArrayList<>(sounds); next.add(sound); sounds = List.copyOf(next);
    }
    private void publishLevel() {
        requireCallback();
        // Normal8 selects palette, HScroll then SAT. No water-palette branch.
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.cpuPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), values.hudObjects(), values.calls());
        byte[] hscroll = values.displayedHScroll().copy();
        System.arraycopy(presentation.act2BackgroundImage().hscrollRam().copy(), 0, hscroll, 0, 0x380);
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), CdAssetBytes.copyOf(hscroll), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), values.hudObjects(), values.calls());
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.cpuSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), values.hudObjects(), values.calls());
        call(ORDINARY_PUBLICATION);
    }
    private void serviceLevelArtAndHud() {
        requireCallback();
        var gate = CdPowerupArtRuntime.gate(values.level().powerup());
        levelFadeAndPowerup(values.level().fadeEnableDisplay(), gate.powerup());
        if (gate.copyRequired()) throw module.act2Boundary(SonicCdGameModule.Act2Boundary.POWERUP_ART);
        call(UPDATE_ANIMATED_TILES);
        copyAndDraw(); call(ORDINARY_DRAW_LEVEL);
        section(s -> art.serviceSlow(s, presentation.act2ForegroundImage().scrollLock())); call(ORDINARY_SLOW_PLC);
        var state = frame.state(); var env = frame.environment(); var g = values.globals();
        var input = new CdLevelHud.Inputs(new CdLevelHud.Globals(env.globals().debugCheat(),
                (g.pauseMusicBit().orElseThrow() | (frame.paused() ? 1 : 0)) << 8,
                env.controls().controlLocked(), env.movement().gates().timeZone(), state.motion().centreX16_16() >>> 16,
                state.motion().centreY16_16() >>> 16, env.movement().gates().zoneAct()),
                new CdLevelHud.DebugData(OptionalInt.empty(), g.layoutRam(), g.debugBlockWord()));
        try { adoptHud(hud.run(new CdLevelHud.Act2State(values.section(), values.ring(), values.level(), g.score(),
                g.live(), g.bonus()), input).state()); }
        catch (CdLevelHud.UnsupportedAct2Hud stopped) { adoptHud(stopped.state()); throw stopped; }
        call(UPDATE_HUD);
    }
    private void adoptHud(CdLevelHud.Act2State next) {
        var g = values.globals();
        replace(values.phase(), next.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(), next.ring(),
                next.level(), new LoadingGlobals(next.liveTime(), g.contact(), next.score(), g.drownTimerWord(), g.tail(),
                        next.bonus(), g.layoutRam(), g.debugBlockWord(), g.pauseMusicBit(), g.retainedDev(), g.checkpointSaved()), values.hudObjects(), values.calls());
    }

    private void representedServiceCompleted() {
        // Account only a successfully returned Frame service, before its CPU continuation.
        // A raw/publication prefix that faults before Finish is not a completed pulse.
        EngineTiming.vIntRunCounter(EngineServices.current()).serviceRepresentedVBlank();
    }

    private void publish() {
        requireCallback();
        var background = presentation.act2BackgroundImage();
        byte[] selectedHScroll = values.displayedHScroll().copy();
        System.arraycopy(background.hscrollRam().copy(), 0, selectedHScroll, 0, 0x380);
        // Common VInterrupt selects the CPU LONG; C then publishes only 0x380 HScroll
        // bytes. Preserve the InitVDP-cleared selected tail and actual native VScroll.
        values = new Values(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.waterPalette(),
                values.cpuPalette(), values.cpuSat(), values.cpuSat(), CdAssetBytes.copyOf(selectedHScroll),
                background.vscroll(), values.foregroundPlane(), values.ring(), values.level(), values.globals(),
                values.hudObjects(), values.fadeProgress(), values.calls(), values.loop());
    }

    private void copyAndDraw() {
        requireCallback();
        var bg = presentation.serviceAct2PlaneAtLevelLoad();
        var copies = presentation.act2DrawCopies();
        if ((copies.fgFlags() & 0xFF00) == 0) return;
        if (values.foregroundPlane() == null) throw module.act2Boundary(SonicCdGameModule.Act2Boundary.ENTRY);
        // BG1 always restores IDs+1. Its column path advances through all16 rows,
        // including skipped rows, retaining the association just beyond the final iteration.
        int index = 1;
        for (var read : bg.sections()) {
            if (read.phase() == CdR11aBackgroundPlane.Phase.INCREMENTAL_COLUMN) index = read.tableIndex() + 1;
        }
        var ids = view(CdR12aProfile.Asset.BG_CAMERA_SECT_IDS).bytes();
        var metadata = new CdR11bForegroundPlane.CallerMetadata(
                index >= 0 && index < ids.length() ? OptionalInt.of(ids.unsignedByte(index)) : OptionalInt.empty());
        CdR11bForegroundPlane.Result result;
        try {
            result = foreground.incremental(values.foregroundPlane(),
                    new CdR11aBackgroundPlane.Camera(copies.fgX16_16(), copies.fgY16_16()), copies.fgFlags(), metadata);
        } catch (CdR11bForegroundPlane.InvalidPlane stopped) {
            var prefix = stopped.completedPrefix();
            adoptForeground(prefix.state(), prefix.flagsWord());
            throw stopped;
        }
        adoptForeground(result.state(), result.flagsWord());
    }
    private void adoptForeground(CdR11aBackgroundPlane.State plane, int flags) {
        presentation.consumeAct2ForegroundFlags(flags);
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), plane, values.ring(), values.level(),
                values.globals(), values.hudObjects(), values.calls());
    }
    private void fastPlc() { requireCallback(); section(art::serviceFast); }

    private void mainLoadPrefix() {
        requireCurrent();
        if (!sounds.isEmpty()) throw new IllegalStateException("R12A music request cannot be replayed");
        sounds = List.of(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_R1AMUS)));
        call(PLAY_LEVEL_MUSIC); // Typed actual Sub CPU request; no sound-driver synthesis.
        section(s -> art.loadImmediateList(s, 3)); call(IMMEDIATE_LEVEL);
        section(s -> art.loadImmediateList(s, 1)); call(IMMEDIATE_STANDARD);
        // Main168 CLR.L includes fadeEnableDisplay, unlike its adjacent powerup byte.
        var l = values.level();
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(), values.ring(),
                new CdLevelGlobals(l.levelRestart(), l.updateHudTime(), l.checkpoint(), l.spawnMode(),
                        l.plcLoadFlags(), 0, 0, 0, l.timeOver()), values.globals(), values.hudObjects(), values.calls());
        frame.clearLevelStartFlowersPlayerValues();
        var r = values.ring();
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                new CdRingGlobals(r.rings(), r.lives(), r.livesFlags(), r.updateHudRings(), r.updateHudLives(), 0, 0, 0),
                values.level(), values.globals(), values.hudObjects(), values.calls());
        // Main178-184 actually clears FF1600..FF1FFF, including ALL global animations.
        var loop = values.loop();
        loop(new LoopGlobals(loop.demoDataIndexWord(), Optional.of(new CdGlobalObjectAnimation.Extras(
                0, 0, 0, 0, new CdRingSpillGlobals(0, 0, 0))),
                loop.paletteSteps7(), loop.paletteTimers7(), loop.completedDraw(), loop.selectedDisplay()));
        presentation.clearAct2FlowersImages(); globals(LoadingGlobals::clearDebugBlock); call(CLEAR_FLOWERS);
        // The actual complete object clear includes primary slot0; its fresh RunMMD
        // image is already zero and has had no native object writer before this cut.
        if (frame.state().header().slotId() != 0 || frame.state().motion().routine() != 0)
            throw module.act2Boundary(SonicCdGameModule.Act2Boundary.ENTRY);
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), zero(31 * 64), values.calls());
        placement.pool().clearDynamicSlots();
        call(CLEAR_OBJECTS);
        globals(g -> g.withLive(CdCheckpointLiveAux.clearMiscAtLevelStart(g.live())).withPauseMusicBit(0)); call(CLEAR_MISC);
        frame.clearLevelStartCameraPlayerValues(); presentation.clearAct2CameraImages();
        // RunMMD's FF2000..FFFAFF clear already established these zero bytes;
        // Main210-216 re-clears the FFF7B0..FFF7BD palette allocation.
        var cameraLoop = values.loop();
        loop(new LoopGlobals(cameraLoop.demoDataIndexWord(), cameraLoop.animations(), zero(7), zero(7), cameraLoop.completedDraw(), cameraLoop.selectedDisplay()));
        globals(LoadingGlobals::clearBonus); call(CLEAR_CAMERA);
        // Main223, after the real camera clear and before ClearScreen. Never borrow demoS1Index/F790.
        var demoLoop = values.loop();
        loop(new LoopGlobals(OptionalInt.of(0), demoLoop.animations(), demoLoop.paletteSteps7(),
                demoLoop.paletteTimers7(), demoLoop.completedDraw(), demoLoop.selectedDisplay())); call(CLEAR_DEMO_INDEX);
        presentation.clearAct2ScreenImages();
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                zero(CdObjectDrawQueue.SAT_BYTE_SIZE), values.displayedSat(), values.displayedHScroll(),
                new CdR11aBackgroundPlane.State(zero(CdR11aBackgroundPlane.NAME_TABLE_BYTES)),
                values.ring(), values.level(), values.globals(), values.hudObjects(), values.calls());
        call(CLEAR_SCREEN);
        globals(g -> g.withDrownTimer(30));
        var sonic = view(CdR12aProfile.Asset.PAL_SONIC).bytes();
        palette(sonic, 0, true); palette(sonic, 0, false); call(PALETTE_SONIC);
        var bounds = assets.loadPlan().bounds();
        frame.initializeLevelSizePlayerValues(new CdPlayerMovement.Bounds(bounds.left(), bounds.right(), bounds.bottom(),
                frame.environment().movement().bounds().bossFight()), bounds.cameraCentreY());
        presentation.initializeAct2LevelSizeImages(); call(LEVEL_SIZE);
        frame.initializeLevelSizeCentrePlayerValues(assets.loadPlan().initialCentre().centreX(), assets.loadPlan().initialCentre().centreY());
        presentation.completeAct2LoadScroll(frame, values.level().updateHudTime()); call(LEVEL_SCROLL);
        CdR12aSectionArt.BlocksResult blocks;
        try { blocks = art.decodeBlocksToRam(values.section()); }
        catch (CdR12aSectionArt.SectionArtException stopped) { section(stopped.state()); throw stopped; }
        section(blocks.state()); call(BLOCKS_RAM);
        var layout = executeLayoutCopy(); globals(g -> g.withLayout(layout)); call(LEVEL_LAYOUT);
        palette(view(CdR12aProfile.Asset.PAL_LEVEL).bytes(), 32, false); call(PALETTE_LEVEL);
        // LoadLevelData37-48: spawn0 and title-bit1 clear skip section PLC2.
        // LoadLevelLayout's final copied BG row leaves the next byte at actual RAM CA.
        var f = presentation.act2ForegroundImage();
        var drawn = foreground.initialize(values.foregroundPlane(),
                new CdR11aBackgroundPlane.Camera(f.cameraX() << 16, f.cameraY16_16()),
                f.scrollFlags(), new CdR11bForegroundPlane.CallerMetadata(OptionalInt.of(layout.unsignedByte(finalBackgroundLayoutCursor()))));
        adoptForeground(drawn.state(), presentation.act2DrawCopies().fgFlags()); call(INITIAL_FOREGROUND);
        presentation.initializeAct2PlaneAtLoad(); call(INITIAL_BACKGROUND);
        call(COLLISION_POINTER);
        phase(SECTION_PLC);
    }

    private CdAssetBytes executeLayoutCopy() {
        byte[] ram = new byte[2048];
        copyLayout(ram, 0, view(CdR12aProfile.Asset.LAYOUT_FG).bytes());
        copyLayout(ram, 64, view(CdR12aProfile.Asset.LAYOUT_BG).bytes());
        var prepared = assets.terrain().layout().ram();
        if (!Arrays.equals(ram, prepared.copy())) throw new IllegalStateException("Executed R12A layout differs from authenticated product");
        return prepared; // Same-value adoption only AFTER the genuine source copy.
    }
    private int finalBackgroundLayoutCursor() {
        var bg = view(CdR12aProfile.Asset.LAYOUT_BG).bytes();
        return 64 + bg.unsignedByte(1) * 128 + bg.unsignedByte(0) + 1;
    }
    private static void copyLayout(byte[] ram, int base, CdAssetBytes source) {
        int columns = source.unsignedByte(0) + 1, rows = source.unsignedByte(1) + 1, at = 2;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) ram[base + y * 128 + x] = (byte) source.unsignedByte(at++);
        }
        if (at != source.length()) throw new IllegalArgumentException("Complete native layout source required");
    }
    private void palette(CdAssetBytes source, int offset, boolean cpu) {
        byte[] target = (cpu ? values.cpuPalette() : values.fadePalette()).copy();
        System.arraycopy(source.copy(), 0, target, offset, source.length());
        replace(values.phase(), values.section(), cpu ? CdAssetBytes.copyOf(target) : values.cpuPalette(),
                cpu ? values.fadePalette() : CdAssetBytes.copyOf(target), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), values.hudObjects(), values.calls());
    }

    private void finishSectionWait() {
        requireCurrent();
        frame.applyLevelStartNormalPlayer(); call(LOAD_PLAYER);
        byte[] hud = values.hudObjects().copy();
        for (int whole : new int[]{2, 3, 5}) hud[(whole - 1) * 64] = 0x1C;
        hud[(3 - 1) * 64 + 0x28] = 1; hud[(5 - 1) * 64 + 0x29] = 1;
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), CdAssetBytes.copyOf(hud), values.calls());
        call(RESERVED_HUD);
        section(s -> art.loadLifeIcon(s, frame.environment().movement().gates().timeZone())); call(LIFE_ICON);
        byte[] icon = values.hudObjects().copy();
        icon[(31 - 1) * 64] = 0x19; icon[(31 - 1) * 64 + 0x28] = 0x0A;
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), CdAssetBytes.copyOf(icon), values.calls());
        call(RESERVED_TIME_ICON);
        if ((values.level().plcLoadFlags() & 2) != 0) throw module.act2Boundary(SonicCdGameModule.Act2Boundary.ENTRY);
        var titleLevel=values.level();
        replace(values.phase(),values.section(),values.cpuPalette(),values.fadePalette(),values.displayedPalette(),
                values.cpuSat(),values.displayedSat(),values.displayedHScroll(),values.foregroundPlane(),values.ring(),
                new CdLevelGlobals(titleLevel.levelRestart(),titleLevel.updateHudTime(),titleLevel.checkpoint(),titleLevel.spawnMode(),
                        titleLevel.plcLoadFlags()|2,titleLevel.flowerCount(),titleLevel.fadeEnableDisplay(),titleLevel.powerup(),titleLevel.timeOver()),
                values.globals(),values.hudObjects(),values.calls());
        byte[] titleImage=values.hudObjects().copy(); titleImage[(4-1)*64]=0x3C;
        var titleValues=reservedValues(values,CdAssetBytes.copyOf(titleImage));
        var lock=frame.prepareObjectTitleCardGlobal(frame.state(),frame.environment(),CdTitleCard.StoreField.CONTROL_LOCK_BYTE,1);
        frame.validatePreparedObjectSolidPublication(lock); values=titleValues; frame.applyPreparedObjectSolidPublication(lock);
        section(s -> art.withSectionWord(s,s.sectionWord() & 0xFF)); call(TITLE_CARD);
        frame.clearLevelStartControlsAndBoredTimer();
        globals(g -> g.withLive(CdCheckpointLiveAux.clearTimeAtSpawnZero(g.live())));
        frame.clearLevelStartPlayerGlobals();
        var l = values.level(); var r = values.ring();
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                new CdRingGlobals(0, r.lives(), 0, 0x80, 1, r.timeStopTimer(),
                        r.ringAnimationTimer(), r.ringAnimationFrame()),
                new CdLevelGlobals(0, 1, l.checkpoint(), 0, l.plcLoadFlags() | 2, l.flowerCount(),
                        l.fadeEnableDisplay(), l.powerup(), 0), values.globals(),
                values.hudObjects(), values.calls());
        globals(LoadingGlobals::finishMainStores);
        call(BEGIN_MAIN); beginFromBlack();
    }

    private void levelFadeAndPowerup(int fade, int powerup) {
        var l = values.level();
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(), values.ring(),
                new CdLevelGlobals(l.levelRestart(), l.updateHudTime(), l.checkpoint(), l.spawnMode(), l.plcLoadFlags(),
                        l.flowerCount(), fade, powerup, l.timeOver()), values.globals(), values.hudObjects(), values.calls());
    }
    private void fadeProgress(FadeProgress progress) {
        values = new Values(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.waterPalette(),
                values.displayedPalette(), values.cpuSat(), values.displayedSat(), values.displayedHScroll(),
                values.displayedVScrollLongBits(), values.foregroundPlane(), values.ring(), values.level(),
                values.globals(), values.hudObjects(), progress, values.calls(), values.loop());
    }
    private void publishFromBlack() {
        publish();
        if (values.level().fadeEnableDisplay() == 1) {
            levelFadeAndPowerup(2, values.level().powerup());
            // Native12 ORI40 into the InitVDP-derived display cache, expressed as
            // its selected semantic output. No renderer/GPU readiness is granted.
            fadeProgress(new FadeProgress(values.fadeProgress().completedSteps(), true));
        }
    }
    private void serviceFromBlack(int held) {
        var selectedCpu = values.loop().completedDraw();
        servicing = true;
        try { frame.serviceLevelPaletteFade(held, this::publishFromBlack, this::fastPlc); }
        finally { servicing = false; }
        representedServiceCompleted(); call(FROM_BLACK_SERVICE);
        var colours = CdLevelPaletteFade.stepFromBlack(values.cpuPalette(), values.fadePalette(), 0x202F);
        replace(values.phase(), values.section(), colours, values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), values.hudObjects(), values.calls());
        call(FROM_BLACK_COLOURS);
        section(art::prepare); call(PROCESS_PLCS);
        fadeProgress(new FadeProgress(values.fadeProgress().completedSteps() + 1, values.fadeProgress().displayEnabled()));
        if (values.fadeProgress().completedSteps() == 22) {
            module.completeAct2LoadingBeginLevel(this); call(LEVEL_STARTED); phase(BEGIN_LEVEL);
        }
        settleSelectedDisplay(selectedCpu);
    }


    private void beginFromBlack() {
        var gate=CdPowerupArtRuntime.gate(values.level().powerup());
        levelFadeAndPowerup(values.level().fadeEnableDisplay(),gate.powerup());
        if (gate.copyRequired()) throw module.act2Boundary(SonicCdGameModule.Act2Boundary.POWERUP_ART);
        call(UPDATE_ANIMATED_TILES); levelFadeAndPowerup(1,values.level().powerup());
        if (frame.clearLevelStartTimeZoneMarker()) throw module.act2Boundary(SonicCdGameModule.Act2Boundary.ENTRY);
        module.clearAct2PaletteFlag(this);
        // FadeFromBlack11-21 clears exactly the caller's $202F range; first16 Sonic colours survive.
        byte[] cpu=values.cpuPalette().copy(); Arrays.fill(cpu,32,128,(byte)0);
        replace(FROM_BLACK,values.section(),CdAssetBytes.copyOf(cpu),values.fadePalette(),values.displayedPalette(),
                values.cpuSat(),values.displayedSat(),values.displayedHScroll(),values.foregroundPlane(),values.ring(),
                values.level(),values.globals(),values.hudObjects(),values.calls());
    }

    private void runObjects(int levelFrame) {
        requireBoulderPlatformIdle();
        if (runningObjects || drawQueue!=null || activeTitle!=null) throw new IllegalStateException("Act2 physical walk reentry");
        runningObjects=true;
        try {
            drawQueue=createReservedDrawQueue(); drawQueue.beginPass();
            var primary=frame.state();
            if (primary.header().slotId()!=0) {
                if (primary.header().slotId()==1 && primary.motion().routine()==2) {
                    runPrimaryMain(levelFrame); call(PRIMARY_MAIN);
                } else {
                if (primary.header().slotId()!=1 || primary.motion().routine()!=0)
                    throw module.act2Boundary(SonicCdGameModule.Act2Boundary.PRIMARY_BODY);
                // First actual ordinary Init: no synthesized pose, art cue, draw or extra input.
                var publication=frame.runBody(levelFrame);
                for (var sound:publication.sounds()) appendSound(new SonicCdGameModule.PastFrameSound(sound));
                call(PRIMARY_INIT);
                }
            }
            for(int whole=1;whole<128;whole++) {
                int id=whole<32 ? reservedImage(whole).unsignedByte(0) : placement.pool().state(whole).id();
                if(id==0) continue;
                if(id==CdTitleCard.OBJECT_ID) { runTitle(whole); call(TITLE_CARD_BODY); }
                else if(whole>=CdTimeStars.FIRST_WHOLE_INDEX && whole<CdTimeStars.END_WHOLE_INDEX
                        && id==CdTimeStars.OBJECT_ID) { runTimeStar(whole); call(TIME_STARS_BODY); }
                else if(whole<32) {
                    if((whole!=2 && whole!=3 && whole!=5 && whole!=31) || (id!=0x1C && id!=0x19))
                        throw module.act2Boundary(SonicCdGameModule.Act2Boundary.RESERVED_HUD);
                    runReservedHud(whole);
                } else if(id==0x10) { runRing(whole); call(RING_BODY); }
                else if(id==CdSpring.OBJECT_ID) { runSpring(whole); call(SPRING_BODY); }
                else if(id==CdAnimal.OBJECT_ID) { runAnimal(whole); call(ANIMAL_PRESENCE); }
                else if(id==CdMonitor.OBJECT_ID) {
                    boolean post=placement.pool().state(whole).subtype()>=8;
                    runMonitor(whole); call(post ? TIME_POST_BODY : MONITOR_BODY);
                }
                else if(id==CdFlower.OBJECT_ID) { runFlower(whole); call(FLOWER_BODY); }
                else if(id==CdExplosion.OBJECT_ID) { runExplosion(whole); call(EXPLOSION_BODY); }
                else if(id==CdPoints.OBJECT_ID) { runPoints(whole); call(POINTS_BODY); }
                else if(id==CdTunnelPath.OBJECT_ID) {runTunnelPath(whole);call(TUNNEL_PATH_BODY);}
                else if(id==CdTunnelDoor.OBJECT_ID) {runDoor(whole);call(TUNNEL_DOOR_BODY);}
                else if(id==CdLogShadow.OBJECT_ID) { runLogShadow(whole); call(LOG_SHADOW_BODY); }
                else if(id==CdMosqui.OBJECT_ID) { runMosqui(whole); call(MOSQUI_BODY); }
                else if(id==CdAnton.OBJECT_ID) { runAnton(whole); call(ANTON_BODY); }
                else if(id==CdPataBata.OBJECT_ID) { runPataBata(whole); call(PATA_BATA_BODY); }
                else if(id==CdCollapsePlatform.OBJECT_ID) { runCollapse(whole); call(COLLAPSE_PLATFORM_BODY); }
                else if(id==CdCheckpoint.OBJECT_ID) { runCheckpoint(whole); call(CHECKPOINT_BODY); }
                else if(id==CdTamabboh.OBJECT_ID) { runTamabboh(whole); call(TAMABBOH_BODY); }
                else if(id==CdFloatBlock.OBJECT_ID) { runFloatBlock(whole); call(FLOAT_BLOCK_BODY); }
                else if(id==CdBreakableWall.OBJECT_ID) { runWall(whole); call(WALL_BODY); }
                else if(id==CdBoulder.OBJECT_ID) { runBoulder(whole); call(BOULDER_BODY); }
                else if(id==CdPlatform.OBJECT_ID) { runPlatform(whole); call(PLATFORM_BODY); }
                else throw module.act2Boundary(SonicCdGameModule.Act2Boundary.DYNAMIC_OBJECTS);
                if(whole==31) call(RESERVED_OBJECTS);
            }
            call(DYNAMIC_OBJECTS); finishOrdinaryMain();
        } catch(RuntimeException|Error original) {
            try { if(drawQueue!=null) drawQueue.close(); }
            catch(RuntimeException|Error cleanup) { if(cleanup!=original) original.addSuppressed(cleanup); }
            throw original;
        } finally { runningObjects=false; }
    }

    private CdObjectDrawQueue createReservedDrawQueue() {
        var sonic=frame.stageSource().sonic(); var roots=new ArrayList<CdObjectMappings.DeclaredTable>();
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_SONIC.nativeAddress(),sonic.mappings(),0,sonic.mappingFrames().size()));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_HUD.nativeAddress(),view(CdR12aProfile.Asset.MAP_SPR_HUD).bytes(),0,4));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_MONITOR_TIME.nativeAddress(),view(CdR12aProfile.Asset.MAP_SPR_MONITOR_TIME).bytes(),0,20));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_RING.nativeAddress(),view(CdR12aProfile.Asset.MAP_SPR_RING).bytes(),0,9));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.TITLE_CARD_MAPPINGS.nativeAddress(),view(CdR12aProfile.Asset.TITLE_CARD_MAPPINGS).bytes(),0,9));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_BOULDER.nativeAddress(),assets.objects().boulder().mappings(),0,1));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_PLATFORM.nativeAddress(),assets.objects().platform().mappings(),0,3));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_FLOWER.nativeAddress(),assets.objects().flower().mappings(),0,8));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_EXPLOSION.nativeAddress(),assets.objects().explosion().mappings(),0,7));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_POINTS.nativeAddress(),assets.objects().points().mappings(),0,6));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_TUNNEL_DOOR.nativeAddress(),assets.objects().tunnelDoor().mappings(),0,2));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_LOG_SHADOW.nativeAddress(),assets.objects().logShadow().mappings(),0,2));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_MOSQUI_NORMAL.nativeAddress(),assets.objects().normalMosqui().mappings(),0,5));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_MOSQUI_DAMAGED.nativeAddress(),assets.objects().damagedMosqui().mappings(),0,5));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_ANTON.nativeAddress(),assets.objects().anton().mappings(),0,4));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_PATA_BATA1.nativeAddress(),assets.objects().pataBataNormal().mappings(),0,3));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_PATA_BATA2.nativeAddress(),assets.objects().pataBataDamaged().mappings(),0,3));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_TAMABBOH.nativeAddress(),assets.objects().tamabbohNormal().mappings(),0,3));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_TAMABBOH.nativeAddress(),assets.objects().tamabbohDamaged().mappings(),22,3));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_TAMABBOH_MISSILE.nativeAddress(),assets.objects().tamabbohMissile().mappings(),0,4));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_FLOAT_BLOCK.nativeAddress(),assets.objects().floatBlock().mappings(),0,6));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_CHECKPOINT.nativeAddress(),assets.objects().checkpoint().mappings(),0,4));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_COLLAPSE_PLATFORM_1.nativeAddress(),assets.objects().collapsePlatform1().mappings(),0,1));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_COLLAPSE_PLATFORM_2.nativeAddress(),assets.objects().collapsePlatform2().mappings(),0,2));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_COLLAPSE_PLATFORM_3.nativeAddress(),assets.objects().collapsePlatform3().mappings(),0,7));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_COLLAPSE_PLATFORM_4.nativeAddress(),assets.objects().collapsePlatform4().mappings(),0,2));
        for(int root:new int[]{0,6,80}) roots.add(new CdObjectMappings.DeclaredTable(
                CdR12aProfile.Asset.MAP_SPRING.nativeAddress(),assets.objects().springMappings(),root,3));
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_BREAKABLE_WALL.nativeAddress(),
                assets.objects().breakableWall().mappings(),0,14));
        // Native empty root0 overlaps frame1's body. Resolve only reached frames from all13 raw roots.
        roots.add(new CdObjectMappings.DeclaredTable(CdR12aProfile.Asset.MAP_SPR_POWERUP.nativeAddress(),
                view(CdR12aProfile.Asset.MAP_SPR_POWERUP).bytes(),0,13));
        var mappings=CdObjectMappings.declaredSourceViews(roots,List.of());
        return new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots() {
            @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                if(activeTimeStars!=null)throw new IllegalStateException("Collecting TimeStars DrawObject has no live queue read");
                if(activeTunnelPath!=null)throw new IllegalStateException("Tunnel Path has no native queue read");
                if(activeFlower!=null) return readFlowerDrawSlot(ref);
                if(activeChildEffect!=null) return readChildDrawSlot(ref);
                if(activeDoor!=null) return readDoorDrawSlot(ref);
                if(activeLogMosqui!=null) return readLogMosquiDrawSlot(ref);
                if(activeDrain!=null) return readDrainingSlot(ref);
                if(activeTitle!=null) return readTitleDrawSlot(ref);
                if(activeBoulder!=null) return readBoulderDrawSlot(ref);
                if(activePlatform!=null) return readPlatformDrawSlot(ref);
                if(activeAnton!=null) return readAntonDrawSlot(ref);
                if(activePata!=null) return readPataDrawSlot(ref);
                if(activeCollapse!=null) return readCollapseDrawSlot(ref);
                if(activeCheckpoint!=null) return readCheckpointDrawSlot(ref);
                if(activeTamabboh!=null) return readTamabbohDrawSlot(ref);
                if(activeFloat!=null) return readFloatDrawSlot(ref);
                if(activeWall!=null) return readWallDrawSlot(ref);
                if(activeMonitor!=null) return readMonitorDrawSlot(ref);
                return ref.wholeIndex()<32 ? readReservedDrawSlot(ref)
                        : activeSpring!=null ? readSpringDrawSlot(ref) : readRingDrawSlot(ref);
            }
            @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref,boolean value) {
                if(activeTimeStars!=null) { writeTimeStarsOnScreen(ref,value); return; }
                if(activeTunnelPath!=null)throw new IllegalStateException("Tunnel Path has no native Draw flag write");
                if(activeFlower!=null) { writeFlowerOnScreen(ref,value); return; }
                if(activeChildEffect!=null) { writeChildOnScreen(ref,value); return; }
                if(activeDoor!=null) {writeDoorOnScreen(ref,value);return;}
                if(activeLogMosqui!=null) { writeLogMosquiOnScreen(ref,value); return; }
                if(activeDrain!=null) { writeDrainingOnScreen(ref,value); return; }
                if(ref.wholeIndex()==0) { writePrimaryOnScreen(ref,value); return; }
                if(activeTitle!=null) { writeTitleOnScreen(ref,value); return; }
                if(activeBoulder!=null) { writeBoulderOnScreen(ref,value); return; }
                if(activePlatform!=null) { writePlatformOnScreen(ref,value); return; }
                if(activeAnton!=null) { writeAntonOnScreen(ref,value); return; }
                if(activePata!=null) { writePataOnScreen(ref,value); return; }
                if(activeCollapse!=null) { writeCollapseOnScreen(ref,value); return; }
                if(activeCheckpoint!=null) { writeCheckpointOnScreen(ref,value); return; }
                if(activeTamabboh!=null) { writeTamabbohOnScreen(ref,value); return; }
                if(activeFloat!=null) { writeFloatOnScreen(ref,value); return; }
                if(activeWall!=null) { writeWallOnScreen(ref,value); return; }
                if(activeMonitor!=null) { writeMonitorOnScreen(ref,value); return; }
                if(ref.wholeIndex()<32) writeReservedOnScreen(ref,value);
                else if(activeSpring!=null) writeSpringOnScreen(ref,value); else writeRingOnScreen(ref,value);
            }
        },mappings,values.cpuSat().copy(),List.of(new CdObjectDrawQueue.CdSlotRef(2),new CdObjectDrawQueue.CdSlotRef(3),
                new CdObjectDrawQueue.CdSlotRef(4),new CdObjectDrawQueue.CdSlotRef(5),
                new CdObjectDrawQueue.CdSlotRef(12),new CdObjectDrawQueue.CdSlotRef(13),
                new CdObjectDrawQueue.CdSlotRef(14),new CdObjectDrawQueue.CdSlotRef(15),new CdObjectDrawQueue.CdSlotRef(31)));
    }

    /** Private synchronous source scope. Public component cuts/effects never create this authority. */
    private final class TitleInvocation {
        final int whole; final CdTitleCard body=title; final CdPlayerFrameRuntime sourceFrame=frame;
        final Object sourceStage=frame.stageSource(); final Object world;
        final long generation; final CdObjectPlacementRuntime.Snapshot placed=placement.capture();
        final CdAssetBytes entry;
        final List<CdObjectPool.Snapshot> nativePools=new ArrayList<>();
        final List<CdAssetBytes> nativeSources=new ArrayList<>();
        CdPlayerFrameRuntime.ActorState actor=frame.captureActor();
        CdPlayerFrameRuntime.SharedState shared=frame.captureShared();
        SonicCdPlayerPresentation.ScrollCapture scroll=presentation.captureAct2Scroll();
        Values expectedValues=values; List<SonicCdGameModule.PastSound> expectedSounds=sounds;
        CdAssetBytes expectedReserved=values.hudObjects(); boolean actRead, plcRead, requested, drawn, screenCleared;
        int reservedStores, globalStores; CdObjectPool.Snapshot actPool; boolean deleteExpected;
        TitleInvocation(int whole) {
            this.whole=whole; entry=selectedTitleImage(); var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world=mode.getWorldSession(); generation=mode.getLevelManager().getCompletedProductionLoadGeneration();
            modelTitlePrefixes(this);
        }
        private CdAssetBytes selectedTitleImage() { return whole<32 ? reservedImage(whole) : CdAssetBytes.copyOf(placement.pool().state(whole).copyBytes()); }
    }
    private CdAssetBytes titleImage(int whole) { return whole<32 ? reservedImage(whole) : CdAssetBytes.copyOf(placement.pool().state(whole).copyBytes()); }
    private static CdAssetBytes titleStore(CdAssetBytes image,int at,int width,int value) {
        byte[] raw=image.copy(); for(int j=0;j<width;j++) raw[at+j]=(byte)(value>>>((width-1-j)*8)); return CdAssetBytes.copyOf(raw);
    }
    private void modelTitlePrefixes(TitleInvocation i) {
        var image=i.entry; int routine=image.unsignedByte(0x24); i.nativeSources.add(image); i.nativePools.add(i.placed.pool());
        if(routine==0) {
            int[][] stores={{0x24,1,2},{8,2,0x118},{0xA,2,0x30},{0x30,2,0x30},{0x2E,2,0xF0},
                    {0x1E,1,0x5A},{2,2,0x8360},{4,4,CdR12aProfile.Asset.TITLE_CARD_MAPPINGS.nativeAddress()},{0x18,1,4}};
            // Alias offsets are checked against the actual Title State definition at integration.
            for(var store:stores) { image=titleStore(image,store[0],store[1],store[2]); i.nativeSources.add(image); }
            var pool=i.placed.pool(); var data=assets.objects().view(CdR12aProfile.Asset.TITLE_CARD_DATA);
            for(int row=0;row<8;row++) {
                CdObjectState child=null; for(var slot:pool.slots()) if(slot.id()==0) { child=slot; break; }
                if(child==null) break;
                child=child.moveByte(CdObjectState.ByteField.ID,0x3C).moveByte(CdObjectState.ByteField.ROUTINE,4)
                        .moveWord(CdObjectState.WordField.TILE,0x8360).moveLong(CdObjectState.LongField.MAPPINGS,CdR12aProfile.Asset.TITLE_CARD_MAPPINGS.nativeAddress());
                pool=CdObjectPool.overlay(pool,List.of(child)); i.nativePools.add(pool);
                child=child.moveWord(CdObjectState.WordField.TITLE_CARD_SCREEN_Y_WORD,data.unsignedWord(row*8));
                pool=CdObjectPool.overlay(pool,List.of(child)); i.nativePools.add(pool);
                int x=data.unsignedWord(row*8+2);
                child=child.moveWord(CdObjectState.WordField.X,x).moveWord(CdObjectState.WordField.TITLE_CARD_START_X,x);
                pool=CdObjectPool.overlay(pool,List.of(child)); i.nativePools.add(pool);
                child=child.moveWord(CdObjectState.WordField.TITLE_CARD_DEST_X,data.unsignedWord(row*8+4));
                pool=CdObjectPool.overlay(pool,List.of(child)); i.nativePools.add(pool);
                child=child.moveByte(CdObjectState.ByteField.MAP_FRAME,data.unsignedByte(row*8+6));
                pool=CdObjectPool.overlay(pool,List.of(child)); i.nativePools.add(pool);
                if(row==5) { i.actPool=pool; child=child.addByte(CdObjectState.ByteField.MAP_FRAME,frame.environment().movement().gates().zoneAct()&255);
                    pool=CdObjectPool.overlay(pool,List.of(child)); i.nativePools.add(pool); }
                child=child.moveByte(CdObjectState.ByteField.ANIMATION_TIME,data.unsignedByte(row*8+7));
                pool=CdObjectPool.overlay(pool,List.of(child)); i.nativePools.add(pool);
            }
        } else if(routine==2 || routine==4) {
            int position=routine==2 ? CdObjectState.WordField.TITLE_CARD_SCREEN_Y_WORD.byteOffset() : 8;
            int target=routine==2 ? CdObjectState.WordField.TITLE_CARD_DEST_Y.byteOffset() : CdObjectState.WordField.TITLE_CARD_DEST_X.byteOffset();
            int current=image.unsignedWord(position),destination=image.unsignedWord(target);
            image=current==destination ? titleStore(image,0x24,1,routine+4)
                    : titleStore(image,position,2,current+((short)destination>=(short)current?8:-8));
            i.nativeSources.add(image);
            if(i.whole>=32) {
                var currentSlot=i.placed.pool().slots().get(i.whole-32);
                var next=current==destination ? currentSlot.moveByte(CdObjectState.ByteField.ROUTINE,routine+4)
                        : currentSlot.moveWord(routine==2 ? CdObjectState.WordField.TITLE_CARD_SCREEN_Y_WORD : CdObjectState.WordField.X,
                            current+((short)destination>=(short)current?8:-8));
                i.nativePools.add(CdObjectPool.overlay(i.placed.pool(),List.of(next)));
                i.nativePools.add(CdObjectPool.overlay(i.placed.pool(),List.of(next.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,next.spriteFlags()&0x7F))));
            }
            i.nativeSources.add(titleStore(image,1,1,image.unsignedByte(1)&0x7F));
        }
        else if(routine==6 || routine==8) {
            int timer=image.unsignedByte(0x1E);
            var position=routine==6 ? CdObjectState.WordField.TITLE_CARD_SCREEN_Y_WORD : CdObjectState.WordField.X;
            var start=routine==6 ? CdObjectState.WordField.TITLE_CARD_START_Y : CdObjectState.WordField.TITLE_CARD_START_X;
            int current=image.unsignedWord(position.byteOffset()), target=image.unsignedWord(start.byteOffset());
            CdObjectState dynamic=i.whole>=32 ? i.placed.pool().slots().get(i.whole-32) : null;
            if(timer!=0) {
                image=titleStore(image,0x1E,1,timer-1);
                if(dynamic!=null) dynamic=dynamic.moveByte(CdObjectState.ByteField.ANIMATION_TIME,timer-1);
            } else if(current!=target) {
                int next=current+((short)target>=(short)current?16:-16);
                image=titleStore(image,position.byteOffset(),2,next);
                if(dynamic!=null) dynamic=dynamic.moveWord(position,next);
            } else if(routine==6) {
                image=titleStore(image,0x24,1,10);
                if(dynamic!=null) dynamic=dynamic.moveByte(CdObjectState.ByteField.ROUTINE,10);
            } else { image=zero(64); i.deleteExpected=true; dynamic=null; }
            i.nativeSources.add(image);
            if(dynamic!=null) i.nativePools.add(CdObjectPool.overlay(i.placed.pool(),List.of(dynamic)));
            if(!i.deleteExpected && !(timer==0 && current==target)) {
                i.nativeSources.add(titleStore(image,1,1,image.unsignedByte(1)&0x7F));
                if(dynamic!=null) i.nativePools.add(CdObjectPool.overlay(i.placed.pool(),List.of(
                        dynamic.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,dynamic.spriteFlags()&0x7F))));
            }
        } else if(routine==10 && values.section().queue().headSource()==0) {
            i.deleteExpected=true; i.nativeSources.add(zero(64));
        }
    }

    private TitleInvocation requireTitle() {
        requireBoulderPlatformIdle();
        var i=activeTitle; module.requireAct2LoadingVisit(this);
        var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if(i==null || !runningObjects || servicing || titlePort || activePrimary!=null || activeRing!=null || activeSpring!=null || activeReserved!=null
                || activeDrain!=null || i.body!=title || i.sourceFrame!=frame || i.sourceStage!=frame.stageSource()
                || drawQueue==null || drawQueue.phase()!=CdObjectDrawQueue.Phase.COLLECTING
                || mode.getWorldSession()!=i.world || mode.getLevelManager().getCompletedProductionLoadGeneration()!=i.generation
                || values!=i.expectedValues || sounds!=i.expectedSounds || !frame.captureActor().equals(i.actor)
                || !frame.captureShared().equals(i.shared) || !presentation.sameAct2Scroll(i.scroll)
                || !sameBytes(values.hudObjects(),i.expectedReserved))
            throw new IllegalStateException("Title callback changed another actual owner/full Frame or stale World/source");
        var now=placement.capture();
        if(!now.status().equals(i.placed.status()) || !sameBytes(now.savedFlags(),i.placed.savedFlags()))
            throw new IllegalStateException("Title changed placement cursors/saved bank");
        boolean pool=false;
        for(var prefix:i.nativePools) if(now.pool().slots().equals(prefix.slots())) { pool=true; break; }
        if(!pool && i.deleteExpected && i.whole>=32) {
            pool=true;
            for(int row=0;row<96;row++) {
                if(row==i.whole-32) { if(!sameBytes(CdAssetBytes.copyOf(now.pool().slots().get(row).copyBytes()),zero(64))) pool=false; }
                else if(!now.pool().slots().get(row).equals(i.placed.pool().slots().get(row))) pool=false;
            }
        }
        if(!pool) throw new IllegalStateException("Title full96 exceeds its independently derived native store prefix");
        return i;
    }
    private void validateTitleCut(CdTitleCard.SourceCut cut) {
        var i=requireTitle(); var actual=placement.capture();
        if(cut.wholeIndex()!=i.whole || !sameBytes(cut.expectedSource(),titleImage(i.whole))
                || !cut.actualPlacement().pool().slots().equals(actual.pool().slots())
                || !cut.actualPlacement().status().equals(actual.status())
                || !sameBytes(cut.actualPlacement().savedFlags(),actual.savedFlags()))
            throw new IllegalStateException("Stale Title selected64/full96/source cut");
    }
    private void runTitle(int whole) {
        requireDoorIdle();
        requireLogMosquiIdle();
        requireBoulderPlatformIdle();
        if(activeMonitor!=null || monitorPort || drawingMonitor)
            throw new IllegalStateException("Actual Monitor/Post invocation owns this object cut");
        if(activeTitle!=null || activeRing!=null || activeSpring!=null || activeReserved!=null || activeDrain!=null)
            throw new IllegalStateException("Title actual dispatch reentry");
        var image=titleImage(whole);
        if(image.unsignedByte(0x24)>10 || (image.unsignedByte(0x24)&1)!=0) throw module.act2Boundary(SonicCdGameModule.Act2Boundary.DYNAMIC_OBJECTS);
        var i=new TitleInvocation(whole); activeTitle=i;
        try {
            var result=title.run(whole); requireTitle();
            if(!sameBytes(result.image(),titleImage(whole))) throw new IllegalStateException("Title returned another source image");
            if(i.entry.unsignedByte(0x24)==0 && (!i.actRead || i.reservedStores!=9))
                throw new IllegalStateException("Title Init missed actual parent stores/act row");
        } catch(RuntimeException|Error original) {
            try { requireTitle(); } catch(RuntimeException|Error verification) { if(original!=verification) original.addSuppressed(verification); }
            throw original;
        } finally { activeTitle=null; }
    }
    private CdAssetBytes readTitleReserved(int whole) {
        var i=requireTitle(); if(whole!=i.whole || whole!=4) throw new IllegalStateException("Title reserved owner is fixed whole4");
        return reservedImage(whole);
    }
    private void writeTitleReserved(int whole,CdAssetBytes expected,CdAssetBytes next) {
        var i=requireTitle();
        if(whole!=i.whole || whole!=4 || !sameBytes(expected,reservedImage(whole))
                || i.reservedStores+1>=i.nativeSources.size() || !sameBytes(next,i.nativeSources.get(i.reservedStores+1)))
            throw new IllegalStateException("Title parent write differs from native selected64/store order");
        var all=i.expectedReserved.copy(); System.arraycopy(next.copy(),0,all,(whole-1)*64,64);
        var ready=reservedValues(values,CdAssetBytes.copyOf(all));
        i.expectedReserved=ready.hudObjects(); i.expectedValues=ready; values=ready; i.reservedStores++;
        requireTitle();
    }
    private int readTitle(CdTitleCard.ReadCall call) {
        validateTitleCut(call.cut()); var i=requireTitle();
        int result;
        if(call.field()==CdTitleCard.ReadField.ACT_BYTE) {
            if(i.entry.unsignedByte(0x24)!=0 || i.actRead || i.actPool==null
                    || !placement.capture().pool().slots().equals(i.actPool.slots()) || i.reservedStores!=9)
                throw new IllegalStateException("Title act read is not native row5/full source prefix");
            i.actRead=true; result=frame.environment().movement().gates().zoneAct()&255;
        } else {
            if(i.entry.unsignedByte(0x24)!=10 || i.plcRead) throw new IllegalStateException("Title queue read is not WaitPLC");
            i.plcRead=true; result=(int)values.section().queue().headSource();
        }
        validateTitleCut(call.cut()); return result;
    }
    private void storeTitle(CdTitleCard.StoreCall call) {
        validateTitleCut(call.cut()); var i=requireTitle();
        int routine=i.entry.unsignedByte(0x24);
        var expectedField=routine==6 || i.globalStores==0 ? CdTitleCard.StoreField.SCROLL_LOCK_BYTE : CdTitleCard.StoreField.CONTROL_LOCK_BYTE;
        int expectedValue=routine==6 ? 1 : 0;
        if((routine!=6 && routine!=10) || (routine==6 && i.globalStores!=0)
                || (routine==10 && (!i.plcRead || values.section().queue().headSource()!=0 || i.globalStores>=2))
                || call.field()!=expectedField || call.value()!=expectedValue)
            throw new IllegalStateException("Title global store differs from the exact native branch/order");
        if(call.field()==CdTitleCard.StoreField.CONTROL_LOCK_BYTE) {
            var ready=frame.prepareObjectTitleCardGlobal(frame.state(),frame.environment(),call.field(),call.value());
            frame.validatePreparedObjectSolidPublication(ready); frame.applyPreparedObjectSolidPublication(ready);
            i.shared=frame.captureShared();
        } else {
            var ready=presentation.prepareAct2TitleScrollLock(frame,i.scroll,call.value());
            presentation.validatePreparedAct2TitleScrollLock(ready); presentation.applyPreparedAct2TitleScrollLock(ready);
            i.scroll=presentation.captureAct2Scroll();
        }
        i.globalStores++; validateTitleCut(call.cut());
    }
    private void loadTitlePlc(CdTitleCard.PlcCall call) {
        validateTitleCut(call.cut()); var i=requireTitle();
        if(call.nativeId()!=2 || i.requested || i.entry.unsignedByte(0x24)!=6 || i.globalStores!=1)
            throw new IllegalStateException("Title requested another native PLC site");
        section(a->art.appendList(a,2)); i.expectedValues=values; i.requested=true; validateTitleCut(call.cut());
    }
    private void drawTitle(CdTitleCard.DrawCall call) {
        validateTitleCut(call.cut()); var i=requireTitle();
        if(i.drawn || drawingTitle) throw new IllegalStateException("Title repeated DrawObject");
        var source=titleImage(i.whole); var camera=presentation.act2ForegroundImage();
        drawingTitle=true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(i.whole),
                    source.unsignedByte(1), source.unsignedWord(8), source.unsignedWord(0xC),
                    source.unsignedByte(0x16), source.unsignedByte(0x19), source.unsignedByte(0x18)),
                    new CdObjectDrawQueue.CameraWords(camera.cameraX(),camera.cameraYWord()));
            requireTitle(); i.drawn=true;
        } catch(RuntimeException|Error original) {
            try { requireTitle(); } catch(RuntimeException|Error verification) { if(original!=verification) original.addSuppressed(verification); }
            throw original;
        } finally { drawingTitle=false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readTitleDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var i=requireTitle();
        if(!drawingTitle || ref.wholeIndex()!=i.whole || queuePort) throw new IllegalStateException("No actual Title queue read");
        var image=titleImage(i.whole);
        return new CdObjectDrawQueue.LiveDrawSlot(image.unsignedByte(0),image.unsignedByte(1),image.unsignedWord(8),
                image.unsignedWord(0xC),image.unsignedWord(0xA),(int)image.unsignedLong(4),image.unsignedByte(0x1A),image.unsignedWord(2));
    }
    private void writeTitleOnScreen(CdObjectDrawQueue.CdSlotRef ref,boolean value) {
        var i=requireTitle();
        if(!drawingTitle || ref.wholeIndex()!=i.whole || value || queuePort || i.screenCleared)
            throw new IllegalStateException("Title DrawObject clears own bit7 once");
        if(i.whole<32) {
            var raw=i.expectedReserved.copy(); raw[(i.whole-1)*64+1]&=0x7F;
            var ready=reservedValues(values,CdAssetBytes.copyOf(raw)); values=ready; i.expectedValues=ready; i.expectedReserved=ready.hudObjects();
        } else {
            var slot=placement.pool().state(i.whole); placement.pool().write(slot.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,slot.spriteFlags()&0x7F));
        }
        i.screenCleared=true; requireTitle();
    }

    SonicCdGameModule.Act2LoadState observation() {
        requireCurrent();
        return new SonicCdGameModule.Act2LoadState(assets,values.phase(),values.cpuPalette(),values.displayedPalette(),values.ring(),
                values.level(),values.section(),frame.captureActor(),frame.captureShared(),values.calls(),sounds,placement.capture(),values.hudObjects());
    }
    Snapshot capture() { requireCurrent(); return new Snapshot(this); }
    void validateRestore(Snapshot capture,SonicCdGameModule.LevelProgramState programme) {
        requireCurrent(); Objects.requireNonNull(capture); Objects.requireNonNull(programme);
        if(capture.owner!=this || capture.values.section().stopped()) throw new IllegalArgumentException("Foreign/stopped Act2 capture");
        placement.validateRestore(capture.placement); frame.validateActorRestore(capture.actor); frame.validateSharedRestore(capture.shared);
        var v=capture.values; int steps=v.fadeProgress().completedSteps();
        boolean fading=v.phase()==FROM_BLACK, begun=v.phase()==BEGIN_LEVEL, first=v.phase()==FIRST_PLC;
        if(programme.phase()!=SonicCdGameModule.LevelProgramPhase.ACT2_LOADING
                || programme.levelStarted()!=(begun?1:0) || ((programme.gameMode()&0x80)==0)!=begun
                || capture.shared.environment().movement().gates().zoneAct()!=1
                || capture.shared.environment().movement().gates().timeZone()!=1
                || capture.flags.load()!=first || capture.flags.setup()!=first
                || (fading && steps>=22) || (begun && steps!=22) || (!fading && !begun && steps!=0)
                || v.fadeProgress().displayEnabled()!=(steps!=0)) throw new IllegalArgumentException("Act2 captured native progress/source mismatch");
        var retained=v.loop().selectedDisplay();
        SonicCdAct2Display.validateSelected(retained,new SonicCdAct2Display.Inputs(retained.inputs().selectedCpu(),v.displayedSat(),
                v.displayedPalette(),v.displayedHScroll(),v.displayedVScrollLongBits(),v.fadeProgress().displayEnabled(),
                retained.inputs().foreground(),retained.inputs().background(),retained.inputs().current(),retained.inputs().player()));
    }
    void restore(Snapshot capture,SonicCdGameModule.LevelProgramState programme) {
        validateRestore(capture,programme); frame.restoreActor(capture.actor); frame.restoreShared(capture.shared); placement.restore(capture.placement);
        values=capture.values; sounds=capture.sounds; presentation.restoreAct2LoadFlags(capture.flags); restorePlayerBank();
    }
    private final class PrimaryInvocation {
        private final SonicCdAct2LoadingVisit owner = SonicCdAct2LoadingVisit.this;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR12aTerrainAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Object world;
        private final long generation;
        private final CdPlayerBody.State originalState = frame.state();
        private final CdPlayerBody.Environment originalEnvironment = frame.environment();
        private final Optional<CdPlayerBody.DisplayPublication> originalDisplay = frame.display();
        private final CdPlayerArtRuntime.State originalArt = frame.artState();
        private final CdPlayerFrameRuntime.VIntClock originalClock = frame.vIntClock();
        private final boolean originalPaused = frame.paused();
        private final CdR11aForegroundScroll.State foreground = presentation.act2ForegroundImage();
        private final CdR11aBackgroundScroll.State background = presentation.act2BackgroundImage();
        private final CdR11aBackgroundPlane.State plane = presentation.act2PlaneImage();
        private final SonicCdPlayerPresentation.DrawCopies copies = presentation.act2DrawCopies();
        private final CdObjectDrawQueue.CameraWords oldCamera =
                new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
        private final CdObjectPlacementRuntime.Snapshot placed = placement.capture();
        private final Values base = values;
        private final List<SonicCdGameModule.PastSound> originalSounds = sounds;
        private CdPlayerBody.State expectedState = originalState;
        private CdPlayerBody.Environment expectedEnvironment = originalEnvironment;
        private Optional<CdPlayerBody.DisplayPublication> expectedDisplay = originalDisplay;
        private CdPlayerFrameRuntime.ActorState expectedActor;
        private CdPlayerFrameRuntime.SharedState expectedShared;
        private CdObjectPool.Snapshot expectedPool = placed.pool();
        private Values expectedValues = base;
        private CdAssetBytes expectedReserved = base.hudObjects();
        private CdReservedObjectBank.Snapshot reservedCut;
        private List<SonicCdGameModule.PastSound> expectedSounds = originalSounds;
        private Optional<CdPlayerBody.DisplayEligibility> displayCut = Optional.empty();
        private boolean enqueued;
        private boolean drawing;
        private boolean cleared;

        private PrimaryInvocation() {
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession();
            generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
        }
    }

    private record PrimaryReady(CdPlayerFrameRuntime.ActorState actor, CdPlayerFrameRuntime.SharedState shared,
                                CdObjectPool.Snapshot pool, Values values,
                                List<SonicCdGameModule.PastSound> sounds,
                                Optional<CdPlayerBody.DisplayEligibility> display) { }

    private void runPrimaryMain(int levelFrame) {
        requireDoorIdle();
        requireLogMosquiIdle();
        requireBoulderPlatformIdle();
        if(activeMonitor!=null || monitorPort || drawingMonitor)
            throw new IllegalStateException("Actual Monitor/Post invocation owns this object cut");
        if (!runningObjects || servicing || activePrimary != null || activeDrain != null
                || activeReserved != null || activeRing != null || activeSpring != null || reservedPort || drawingReserved
                || ringPort || drawingRing || springPort || drawingSpring || queuePort
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual Past primary Main entry");
        var invocation = new PrimaryInvocation();
        activePrimary = invocation;
        try {
            verifyPrimary(invocation);
            var v = invocation.base;
            var response = new CdPlayerBody.ResponseContext(primaryResponse, v.globals().contact(),
                    new CdPlayerTouchResponse.WorldGlobals(v.ring().rings(), v.ring().lives(),
                            v.ring().updateHudLives(), v.globals().score().score(),
                            v.globals().score().nextLifeScore(), v.globals().score().updateHudScore()));
            // The SAME real post-SpawnObjects 96-slot cut supplies every borrowed consumer.
            var cut = invocation.placed.pool();
            var player = invocation.originalState;
            var support = new CdPlayerMovement.ObjectContext(cut, player.objectLinks().standObject(),
                    player.header().slotId(), player.header().width());
            // Borrow the original post-SpawnObjects reserved cut from the existing Values owner.
            // Save/scroll-lock ownership remains absent; no failed helper prefix is published.
            invocation.reservedCut = timeStarBank.capture();
            verifyPrimary(invocation);
            var warp = new CdPlayerTimeWarp.Context(OptionalInt.of(v.level().spawnMode()),
                    Optional.of(invocation.reservedCut), Optional.empty(), OptionalInt.of(v.level().levelRestart()));
            var publication = frame.runBody(levelFrame,
                    Optional.of(new CdPlayerBody.TouchContext(primaryScanner, cut, response)),
                    Optional.of(new CdPlayerBody.LevelContext(cut, v.ring(), v.level())),
                    Optional.of(support), Optional.of(new CdPlayerBody.TimeWarpContext(warp, v.globals().contact())));
            // Only the successful Frame call can advance these expected values. Before
            // it returns, entitlement forbids captures; the raw preimages above are legal.
            invocation.expectedState = publication.result().state();
            invocation.expectedEnvironment = publication.result().environment();
            invocation.expectedDisplay = publication.result().display();
            var ready = preparePrimary(invocation, publication);
            verifyPrimary(invocation);
            // All validation, overlays, immutable Values and sounds precede the first
            // live external write. Frame already adopted its own complete result once.
            invocation.pool.restore(ready.pool());
            values = ready.values(); sounds = ready.sounds();
            invocation.expectedPool = ready.pool();
            invocation.expectedValues = ready.values(); invocation.expectedReserved = ready.values().hudObjects();
            invocation.expectedSounds = ready.sounds();
            invocation.expectedActor = ready.actor(); invocation.expectedShared = ready.shared();
            invocation.displayCut = ready.display();
            verifyPrimary(invocation);
            if (ready.display().isPresent() && ready.display().orElseThrow().eligible())
                enqueuePrimary(invocation, ready.display().orElseThrow());
            verifyPrimary(invocation);
        } catch (RuntimeException | Error failure) {
            // Failed Body has no result/external adoption and spends its entitlement.
            // A later failure retains the genuinely reached Frame/queue prefix; no rollback.
            try { verifyPrimary(invocation); }
            catch (RuntimeException | Error verification) {
                if (verification != failure) failure.addSuppressed(verification);
            }
            throw failure;
        } finally { activePrimary = null; }
    }

    private PrimaryReady preparePrimary(PrimaryInvocation invocation, CdPlayerFrameRuntime.BodyPublication publication) {
        verifyPrimary(invocation);
        var result = publication.result();
        if (result.boundary() != CdPlayerBody.Boundary.MAIN_RETURN
                && result.boundary() != CdPlayerBody.Boundary.CONTROLLED_MAIN_RETURN)
            throw new IllegalStateException("Routine-2 primary returned a different native body");
        // These are actual closed post-Body captures, never fabricated pre-Body tokens.
        var actor = frame.captureActor(); var shared = frame.captureShared();
        if (actor.state() != result.state() || actor.display() != result.display()
                || shared.environment() != result.environment() || shared.artState() != invocation.originalArt
                || shared.vIntClock() != invocation.originalClock || shared.paused() != invocation.originalPaused)
            throw new IllegalStateException("Frame did not adopt exactly its completed primary result");
        invocation.expectedActor = actor; invocation.expectedShared = shared;
        var changed = new LinkedHashMap<Integer, CdObjectState>();
        var old = invocation.base;
        var nextRing = old.ring(); var nextLevel = old.level();
        var nextReserved = old.hudObjects();
        var nextScore = old.globals().score(); var nextContact = old.globals().contact();
        Optional<CdPlayerBody.DisplayEligibility> display = Optional.empty();
        boolean touchSeen = false, warpSeen = false;
        var effects = result.effects();
        for (int at = 0; at < effects.size(); at++) {
            var effect = effects.get(at);
            if (effect instanceof CdPlayerBody.ModeEffect mode
                    && mode.effect() instanceof CdPlayerMovement.ObjectPatch write) {
                var patch = write.patch();
                int whole = patch.before().identity().wholeIndex();
                // overlay is also the accessible same-owner/full-identity preflight.
                CdObjectPool.overlay(invocation.placed.pool(), List.of(patch.before()));
                CdObjectPool.overlay(invocation.placed.pool(), List.of(patch.after()));
                var before = changed.getOrDefault(whole,
                        invocation.placed.pool().slots().get(patch.before().identity().dynamicIndex()));
                if (!patch.before().identity().equals(patch.after().identity()) || !before.equals(patch.before()))
                    throw new IllegalStateException("Stale ordered primary MODE object image");
                changed.put(whole, patch.after());
            } else if (effect instanceof CdPlayerBody.TimeWarpCall warp) {
                if (warpSeen) throw new IllegalStateException("Primary returned multiple TimeWarp calls");
                warpSeen = true;
                var warped = warp.result();
                if (warped.saveResult().isPresent())
                    throw new IllegalStateException("Primary produced an output for an absent warp save owner");
                nextReserved = preparePrimaryReserved(invocation, warped);
                nextContact = new CdPlayerBody.ContactGlobals(warped.state().timeWarp(),
                        nextContact.shield(), nextContact.combineRing());
                if (warped.restartResult().isPresent()) {
                    var restart = warped.restartResult().orElseThrow();
                    if (restart.expectedRestartWord() != nextLevel.levelRestart()
                            || restart.finalRestartWord() != ((nextLevel.levelRestart() & 255) | 0x100)
                            || warped.state().timer() < 230 || !warped.stores().isEmpty() || at < 2
                            || !(effects.get(at - 2) instanceof CdPlayerBody.LevelWrite word)
                            || word.field() != CdPlayerBody.LevelField.LEVEL_RESTART
                            || word.value() != restart.finalRestartWord()
                            || !(effects.get(at - 1) instanceof CdPlayerBody.SoundRequest sound)
                            || sound.sound() != CdPlayerBody.Sound.SCMD_FADECDA)
                        throw new IllegalStateException("Primary TimeWarp restart word/fade source order differs");
                    var l = nextLevel;
                    nextLevel = new CdLevelGlobals(restart.finalRestartWord(), l.updateHudTime(), l.checkpoint(),
                            l.spawnMode(), l.plcLoadFlags(), l.flowerCount(), l.fadeEnableDisplay(), l.powerup(), l.timeOver());
                }
            } else if (effect instanceof CdPlayerBody.DisplayEligibility selected) {
                if (display.isPresent()) throw new IllegalStateException("Primary returned multiple Display cuts");
                display = Optional.of(selected); // Retain false as a real skipped DrawObject.
            } else if (effect instanceof CdPlayerBody.ObjectTouchResult touch) {
                if (touchSeen) throw new IllegalStateException("Primary returned multiple touch results");
                touchSeen = true;
                touch.result().changedSlot().ifPresent(image -> changed.put(image.identity().wholeIndex(), image));
            } else if (effect instanceof CdPlayerBody.ObjectTouchResponse touch) {
                if (touchSeen) throw new IllegalStateException("Primary returned multiple touch results");
                touchSeen = true;
                var response = touch.result(); var world = response.world(); var r = nextRing;
                nextRing = new CdRingGlobals(world.rings(), world.lives(), r.livesFlags(), r.updateHudRings(),
                        world.updateHudLives(), r.timeStopTimer(), r.ringAnimationTimer(), r.ringAnimationFrame());
                nextScore = new CdOpeningObjectRuntime.ScoreGlobals(world.score(), world.nextLifeScore(), world.updateHudScore());
                nextContact = touch.contact();
                // Later touch images supersede earlier MODE images for the same physical slot.
                for (var image : response.changedSlots()) changed.put(image.identity().wholeIndex(), image);
            } else if (effect instanceof CdPlayerBody.DeadCheckGone || effect instanceof CdPlayerBody.RestartCut
                    || effect instanceof CdPlayerBody.ResetSavedObjectFlagsRequest
                    || effect instanceof CdPlayerBody.UncheckedAliasWrite) {
                throw new IllegalStateException("Primary Main returned another routine's external publication");
            }
        }
        if (display.isEmpty()) throw new IllegalStateException("Completed primary Main omitted its Display cut");
        var pool = CdObjectPool.overlay(invocation.placed.pool(), List.copyOf(changed.values()));
        invocation.pool.validateRestore(pool);
        var g = old.globals();
        var globals = new LoadingGlobals(g.live(), nextContact, nextScore, g.drownTimerWord(), g.tail(),
                g.bonus(), g.layoutRam(), g.debugBlockWord(), g.pauseMusicBit(), g.retainedDev(), g.checkpointSaved());
        var readyValues = new Values(old.phase(), old.section(), old.cpuPalette(), old.fadePalette(), old.waterPalette(), old.displayedPalette(),
                old.cpuSat(), old.displayedSat(), old.displayedHScroll(), old.displayedVScrollLongBits(),
                old.foregroundPlane(), nextRing, nextLevel, globals, nextReserved, old.fadeProgress(), old.calls(), old.loop());
        var ledger = new ArrayList<SonicCdGameModule.PastSound>(invocation.originalSounds);
        for (var sound : publication.sounds()) ledger.add(new SonicCdGameModule.PastFrameSound(sound));
        var readySounds = List.copyOf(ledger);
        verifyPrimary(invocation);
        return new PrimaryReady(actor, shared, pool, readyValues, readySounds, display);
    }

    /** Validate returned metadata; never execute the helper or its eight Store records again. */
    private CdAssetBytes preparePrimaryReserved(PrimaryInvocation i, CdPlayerTimeWarp.Result result) {
        verifyPrimary(i);
        if (i.reservedCut == null) throw new IllegalStateException("Primary omitted its original reserved cut");
        timeStarBank.validateRestore(i.reservedCut);
        var writes=result.stores().stream().filter(CdPlayerTimeWarp.ReservedWrite.class::isInstance)
                .map(CdPlayerTimeWarp.ReservedWrite.class::cast).toList();
        var patches=result.reservedPatches();
        if (patches.isEmpty()) {
            if (!writes.isEmpty()) throw new IllegalStateException("Reserved stores omitted their full64 outputs");
            return i.expectedReserved; // Native whole12 occupied and unreached branches both retain all31.
        }
        if (patches.size()!=4 || writes.size()!=8 || i.reservedCut.slots().get(11).byteAt(0)!=0)
            throw new IllegalStateException("Primary MakeTimeWarpStars did not retain its four native publications");
        byte[] next=i.expectedReserved.copy();
        for(int at=0;at<4;at++) {
            int whole=12+at; var before=i.reservedCut.slots().get(whole-1); var patch=patches.get(at);
            var id=writes.get(2*at); var animation=writes.get(2*at+1);
            if(patch.before()!=before || !patch.after().equals(before.moveByteAt(0,3).moveByteAt(0x1C,whole-7))
                    || id.wholeIndex()!=whole || id.field()!=CdPlayerTimeWarp.ReservedField.ID
                    || id.expectedValue()!=before.byteAt(0) || id.value()!=3
                    || animation.wholeIndex()!=whole || animation.field()!=CdPlayerTimeWarp.ReservedField.ANIMATION
                    || animation.expectedValue()!=before.byteAt(0x1C) || animation.value()!=whole-7)
                throw new IllegalStateException("Primary reserved token/full64/nonwrite/native order differs");
            System.arraycopy(patch.after().copyBytes(),0,next,(whole-1)*64,64);
        }
        verifyPrimary(i); return CdAssetBytes.copyOf(next);
    }

    private void verifyPrimary(PrimaryInvocation invocation) {
        requireBoulderPlatformIdle();
        module.requireAct2LoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        var now = placement.capture();
        if (invocation == null || activePrimary != invocation || invocation.owner != this
                || !runningObjects || servicing || activeDrain != null || activeReserved != null
                || activeRing != null || activeSpring != null || reservedPort || drawingReserved || ringPort || drawingRing
                || springPort || drawingSpring || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.sourceStage != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || frame.closed() || frame.state() != invocation.expectedState
                || frame.environment() != invocation.expectedEnvironment || frame.display() != invocation.expectedDisplay
                || frame.artState() != invocation.originalArt || frame.vIntClock() != invocation.originalClock
                || frame.paused() != invocation.originalPaused
                || presentation.act2ForegroundImage() != invocation.foreground
                || presentation.act2BackgroundImage() != invocation.background
                || presentation.act2PlaneImage() != invocation.plane || presentation.act2DrawCopies() != invocation.copies
                || !now.status().equals(invocation.placed.status()) || !now.pool().slots().equals(invocation.expectedPool.slots())
                || !sameBytes(now.savedFlags(), invocation.placed.savedFlags())
                || values != invocation.expectedValues || sounds != invocation.expectedSounds
                || !sameBytes(values.hudObjects(), invocation.expectedReserved))
            throw new IllegalStateException("Primary source/World/Frame/full96/Values/scroll cut changed");
        if (invocation.expectedActor != null && (!frame.captureActor().equals(invocation.expectedActor)
                || !frame.captureShared().equals(invocation.expectedShared)))
            throw new IllegalStateException("Primary closed Frame differs from its actual expected complete capture");
    }

    private void enqueuePrimary(PrimaryInvocation invocation, CdPlayerBody.DisplayEligibility cut) {
        verifyPrimary(invocation);
        if (!cut.eligible() || invocation.displayCut.orElse(null) != cut || invocation.enqueued
                || invocation.drawing || invocation.cleared || queuePort)
            throw new IllegalStateException("Primary DrawObject lacks its actual once-only eligible Display cut");
        invocation.enqueued = true; invocation.drawing = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(0),
                    cut.spriteFlags(), cut.centreXWord(), cut.centreYWord(), cut.yRadius(), cut.width(), cut.priority()),
                    invocation.oldCamera);
            if (!invocation.cleared) throw new IllegalStateException("Actual primary DrawObject omitted its entry bit clear");
            verifyPrimary(invocation);
        } finally { invocation.drawing = false; }
    }

    private void writePrimaryOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean value) {
        var invocation = activePrimary;
        verifyPrimary(invocation);
        if (ref == null || ref.wholeIndex() != 0 || value || queuePort || !invocation.drawing
                || !invocation.enqueued || invocation.cleared || invocation.expectedActor == null)
            throw new IllegalStateException("Only actual collecting primary DrawObject may clear bit7 once");
        var ready = primaryScreenActor(invocation.expectedActor, false);
        queuePort = true;
        try {
            verifyPrimary(invocation);
            frame.publishDrawObjectOnScreen(false);
            var actual = frame.captureActor();
            if (!actual.equals(ready)) throw new IllegalStateException("Primary DrawObject changed more than complete-actor bit7");
            invocation.expectedActor = ready; invocation.expectedState = actual.state(); invocation.expectedDisplay = actual.display();
            invocation.cleared = true;
            verifyPrimary(invocation);
        } finally { queuePort = false; }
    }


    /** One actual cached ID19 dispatch; public component values grant no invocation authority. */
    private enum MonitorSite { ENTRY, SOLID, FLOOR_ANGLE, MONITOR_FM, DIRECTION, SUB_CPU, DRAW, RETURN, FAILURE }
    private final class MonitorInvocation {
        private final CdMonitor monitorBody = monitor;
        private final CdTimePost timePostBody = timePost;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR12aTerrainAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final boolean post;
        private final CdObjectPlacementRuntime.Status status = placement.status();
        private final CdAssetBytes reserved = values.hudObjects();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.captureAct2Scroll();
        private final CdObjectDrawQueue.CameraWords camera;
        private Values expectedValues = values;
        private CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private CdObjectPool.Snapshot currentPool = pool.capture();
        private CdAssetBytes expectedSaved = placement.capture().savedFlags();
        private CdObjectState expectedSource;
        private CdMonitor.PrefixSolidCut pendingCut;
        private CdSolidObject.Outcome adoptedOutcome;
        private boolean initVerified, mainVerified, bodyVerified, solidPreflight, solidCommitted;
        private boolean floorStarted, landingVerified, sounded, voiced, drawn, screenCleared, tailArmed, stopped;
        private boolean noDraw, directBreak, checkingFailure;
        private int floorWrites, directionStores;
        private int activationAddress = -1;
        private MonitorInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole); expectedSource = entry; post = entry.subtype() >= 8;
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.act2ForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
        }
    }

    private void runMonitor(int whole) {
        requireDoorIdle();
        requireLogMosquiIdle();
        requireBoulderPlatformIdle();
        module.requireAct2LoadingVisit(this);
        if (activeTitle != null || titlePort || drawingTitle || !runningObjects || servicing || activePrimary != null || activeReserved != null || activeRing != null
                || activeSpring != null || activeMonitor != null || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || monitorPort || drawingMonitor || queuePort
                || whole < 32 || whole >= 128 || placement.pool().state(whole).id() != CdMonitor.OBJECT_ID
                || values.phase() != BEGIN_LEVEL || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual live Act2 Monitor/Post invocation entry");
        var invocation = new MonitorInvocation(whole); activeMonitor = invocation;
        try {
            // Fresh CURRENT raw subtype is the native unsigned branch, never a placement-row classification.
            if (invocation.post) {
                var result = timePost.runPast(whole, timePostGlobals());
                verifyMonitorInvocation(invocation); validateMonitorPrefix(invocation, MonitorSite.RETURN);
                if (!result.state().equals(invocation.pool.state(whole)) || result.deleted() != (result.state().id() == 0))
                    throw new IllegalStateException("Time Post return differs from its actual complete image");
            } else {
                var result = monitor.runPast(whole, monitorGlobals(), new CdMonitor.SolidInputs(springPlayer(),
                        new CdSolidObject.Gates(frame.environment().globals().debugMode(),
                                frame.environment().movement().gates().miniSonic()), springGlobals()));
                verifyMonitorInvocation(invocation); validateMonitorPrefix(invocation, MonitorSite.RETURN);
                if (!result.state().equals(invocation.pool.state(whole)) || !result.finalPlayer().equals(springPlayer())
                        || !result.finalGlobals().equals(springGlobals()) || result.deleted() != (result.state().id() == 0))
                    throw new IllegalStateException("Monitor return differs from its actual pool/Frame pair");
            }
            if (invocation.stopped || invocation.pendingCut != null || !invocation.noDraw && !invocation.drawn)
                throw new IllegalStateException("ID19 returned before its actual private operation grammar completed");
        } catch (RuntimeException | Error failure) {
            if (invocation.adoptedOutcome instanceof CdSolidObject.NativeStopped nativeStop) {
                var original = nativeStop.stop().originalException();
                if (failure != original) original.addSuppressed(failure);
                verifyMonitorFailure(original); throw original;
            }
            verifyMonitorFailure(failure); throw failure;
        } finally { activeMonitor = null; }
    }

    private MonitorInvocation requireMonitorInvocation(CdObjectPool pool, int whole) {
        requireBoulderPlatformIdle();
        var invocation = activeMonitor;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128 || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Monitor/Post callback at this physical pointer");
        verifyMonitorInvocation(invocation); return invocation;
    }
    private MonitorInvocation currentMonitorInvocation() {
        return requireMonitorInvocation(placement.pool(), activeMonitor == null ? -1 : activeMonitor.whole);
    }
    private void verifyMonitorInvocation(MonitorInvocation invocation) {
        requireBoulderPlatformIdle();
        module.requireAct2LoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeMonitor != invocation || activeTitle != null || titlePort || drawingTitle || !runningObjects || servicing || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || invocation.monitorBody != monitor || invocation.timePostBody != timePost
                || invocation.sourceFrame != frame || invocation.sourceAssets != assets || invocation.sourceStage != frame.stageSource()
                || invocation.pool != placement.pool() || invocation.queue != drawQueue
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared)
                || !placement.status().equals(invocation.status) || !presentation.sameAct2Scroll(invocation.scroll)
                || sounds != invocation.expectedSounds || values != invocation.expectedValues
                || !sameBytes(values.hudObjects(), invocation.reserved) || values.phase() != BEGIN_LEVEL)
            throw new IllegalStateException("Monitor/Post changed a source/World/Frame owner or retained a stale full cut");
    }
    private void verifyMonitorFailure(Throwable original) {
        try {
            if (activeMonitor != null) {
                activeMonitor.checkingFailure = true;
                verifyMonitorInvocation(activeMonitor); validateMonitorPrefix(activeMonitor, MonitorSite.FAILURE);
            }
        } catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private CdMonitor.PastGlobals monitorGlobals() {
        return new CdMonitor.PastGlobals() {
            @Override public int rawTimeZone() { currentMonitorInvocation(); return frame.environment().movement().gates().timeZone(); }
            @Override public int rawTimeWarpDirection() { currentMonitorInvocation(); return frame.environment().movement().gates().timeWarpDirection(); }
            @Override public int cameraXWord() { return currentMonitorInvocation().camera.xWord(); }
            @Override public int timeAttackMode() { currentMonitorInvocation(); return frame.environment().globals().timeAttackMode(); }
            @Override public int zone() { currentMonitorInvocation(); return frame.environment().movement().gates().zoneAct() >>> 8; }
            @Override public int layer() { return monitorLayer(); }
            @Override public int timeStopTimer() { currentMonitorInvocation(); return values.ring().timeStopTimer(); }
        };
    }
    private CdTimePost.PastGlobals timePostGlobals() {
        return new CdTimePost.PastGlobals() {
            @Override public int rawTimeZone() { currentMonitorInvocation(); return frame.environment().movement().gates().timeZone(); }
            @Override public int rawTimeWarpDirection() { currentMonitorInvocation(); return frame.environment().movement().gates().timeWarpDirection(); }
            @Override public int cameraXWord() { return currentMonitorInvocation().camera.xWord(); }
            @Override public int timeAttackMode() { currentMonitorInvocation(); return frame.environment().globals().timeAttackMode(); }
            @Override public int zone() { currentMonitorInvocation(); return frame.environment().movement().gates().zoneAct() >>> 8; }
            @Override public OptionalInt layer() { currentMonitorInvocation(); return OptionalInt.empty(); }
        };
    }
    private int monitorLayer() {
        var invocation = currentMonitorInvocation();
        if (invocation.post || (frame.environment().movement().gates().zoneAct() >>> 8) != 6)
            throw new IllegalStateException("Monitor global layer is not at its reached MMZ read");
        validateMonitorPrefix(invocation, MonitorSite.SOLID);
        throw module.act2Boundary(SonicCdGameModule.Act2Boundary.DYNAMIC_OBJECTS);
    }

    /** Finite raw linear address, including savedID0; no second saved bank or semantic period folding. */
    private OptionalInt monitorSavedAddress(CdObjectState source) {
        var gates = frame.environment().movement().gates(); int period = gates.timeZone() & 0x7F;
        if ((gates.timeZone() & 0x80) != 0) {
            int raw = (period - (byte) gates.timeWarpDirection()) & 0xFFFF;
            period = (short) raw < 0 ? 0 : raw >= 3 ? 2 : raw;
        }
        int at = 2 + (short) ((3 * source.savedFlagsId() + period) & 0xFFFF);
        return at < 2 || at >= CdObjectPlacementRuntime.SAVED_BANK_BYTES ? OptionalInt.empty() : OptionalInt.of(at);
    }
    private boolean monitorSavedChange(CdObjectState source, byte[] bank, int clear, int set) {
        var at = monitorSavedAddress(source);
        if (at.isEmpty()) return false;
        bank[at.getAsInt()] = (byte) ((Byte.toUnsignedInt(bank[at.getAsInt()]) & ~clear) | set); return true;
    }
    private boolean monitorDespawn(MonitorInvocation invocation, CdObjectState source) {
        boolean outside = false;
        if ((source.spriteFlags() & 0x80) == 0) {
            int difference = ((source.centreX() & 0xFF80) - ((invocation.camera.xWord() - 0x80) & 0xFF80)) & 0xFFFF;
            outside = difference > 0x280;
        }
        return outside || (frame.environment().movement().gates().timeZone() & 0x80) != 0;
    }
    private CdObjectState monitorDeletedImage(MonitorInvocation invocation, CdObjectState source) {
        var actual = invocation.pool.state(invocation.whole);
        if (!actual.identity().equals(source.identity()) || !Arrays.equals(actual.copyBytes(), new byte[CdObjectState.BYTE_SIZE]))
            throw new IllegalStateException("Monitor/Post deletion did not clear its entire actual64 image");
        return actual;
    }
    private boolean matchesMonitorPool(MonitorInvocation invocation, CdObjectState source,
                                        List<CdObjectState> extra, byte[] bank) {
        var images = new ArrayList<CdObjectState>(extra); images.add(source);
        return CdObjectPool.overlay(invocation.currentPool, images).slots().equals(invocation.pool.capture().slots())
                && Arrays.equals(bank, placement.capture().savedFlags().copy());
    }
    private void acceptMonitorPool(MonitorInvocation invocation, CdObjectState source,
                                   List<CdObjectState> extra, byte[] bank) {
        if (!matchesMonitorPool(invocation, source, extra, bank))
            throw new IllegalStateException("Monitor/Post source prefix changed full96/saved766 beyond native stores");
        var images = new ArrayList<CdObjectState>(extra); images.add(source);
        invocation.currentPool = CdObjectPool.overlay(invocation.currentPool, images);
        invocation.expectedSource = source; invocation.expectedSaved = CdAssetBytes.copyOf(bank);
    }
    private CdObjectState monitorInitPrefix(MonitorInvocation invocation) {
        var source = invocation.entry.addByte(CdObjectState.ByteField.ROUTINE, 2)
                .moveByte(CdObjectState.ByteField.Y_RADIUS, invocation.post ? 0x20 : 0x0E)
                .moveByte(CdObjectState.ByteField.X_RADIUS, 0x0E)
                .moveLong(CdObjectState.LongField.MAPPINGS, view(CdR12aProfile.Asset.MAP_SPR_MONITOR_TIME).range().nativeAddress())
                .moveWord(CdObjectState.WordField.TILE, 0x05A8).moveByte(CdObjectState.ByteField.PRIORITY, 3);
        if ((frame.environment().movement().gates().zoneAct() >>> 8) == 6 && source.unsignedByte(CdObjectState.ByteField.LAYER) == 0)
            source = source.moveWord(CdObjectState.WordField.TILE, 0x85A8).moveByte(CdObjectState.ByteField.PRIORITY, 0);
        source = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, 4).moveByte(CdObjectState.ByteField.WIDTH, 0x0F);
        return invocation.post ? source.moveByte(CdObjectState.ByteField.ANIMATION, source.subtype()) : source;
    }
    /** ObjMoveGrv:42-82 raw WORD/long arithmetic; this checker never invokes Movement or terrain. */
    private CdObjectState monitorGravityPrefix(CdObjectState source) {
        int xv = source.signedWord(CdObjectState.WordField.X_VELOCITY), yv = source.signedWord(CdObjectState.WordField.Y_VELOCITY);
        int control = source.unsignedByte(CdObjectState.ByteField.RAW_CONTROL_2C), next = yv;
        boolean gravity = (control & 8) == 0 && !(yv < 0 && (control & 2) != 0 && (yv & 0xFFFF) < 0xF800) && (control & 4) == 0;
        if (gravity) next = (short) (next + 0x38);
        if (next >= 0x1000) next = 0x1000;
        return source.moveLong(CdObjectState.LongField.X_POSITION, source.longBits(CdObjectState.LongField.X_POSITION) + (xv << 8))
                .moveLong(CdObjectState.LongField.Y_POSITION, source.longBits(CdObjectState.LongField.Y_POSITION) + (yv << 8))
                .moveWord(CdObjectState.WordField.Y_VELOCITY, next & 0xFFFF);
    }

    /** Validate only already-reached native cuts; never invoke a producer or fetch a future operand. */
    private void validateMonitorPrefix(MonitorInvocation invocation, MonitorSite site) {
        var actual = invocation.pool.state(invocation.whole); byte[] bank = invocation.expectedSaved.copy();
        if (site == MonitorSite.FAILURE && matchesMonitorPool(invocation, invocation.expectedSource, List.of(), bank)) return;
        if (invocation.drawn) {
            var source = invocation.expectedSource;
            if (invocation.tailArmed && monitorDespawn(invocation, source)) {
                if (source.savedFlagsId() != 0 && !monitorSavedChange(source, bank, 0x80, 0)) {
                    if (site == MonitorSite.FAILURE) { acceptMonitorPool(invocation, source, List.of(), bank); return; }
                    throw new IllegalStateException("Monitor/Post returned across an unowned saved byte");
                }
                source = monitorDeletedImage(invocation, source);
            }
            acceptMonitorPool(invocation, source, List.of(), bank); return;
        }
        var source = invocation.expectedSource;
        if (!invocation.initVerified) {
            boolean attack = (invocation.post || source.subtype() == 0)
                    && frame.environment().globals().timeAttackMode() != 0;
            if (invocation.post && attack) {
                invocation.noDraw = true; invocation.bodyVerified = true;
                acceptMonitorPool(invocation, monitorDeletedImage(invocation, source), List.of(), bank); return;
            }
            if (!invocation.post && source.subtype() == 0 && attack) {
                invocation.noDraw = true; invocation.bodyVerified = true; invocation.tailArmed = true;
                if (monitorDespawn(invocation, source)) {
                    if (source.savedFlagsId() != 0 && !monitorSavedChange(source, bank, 0x80, 0))
                        throw new IllegalStateException("Monitor attack tail crossed unowned saved address");
                    source = monitorDeletedImage(invocation, source);
                }
                acceptMonitorPool(invocation, source, List.of(), bank); return;
            }
            if (invocation.post && source.subtype() == 0x0A)
                throw new IllegalStateException("Dynamic Time Icon advanced its source image");
            int routine = invocation.entry.routine();
            if ((routine & 1) != 0 || routine > (invocation.post ? 6 : 8))
                throw new IllegalStateException("Unsupported ID19 routine/icon advanced its source image");
            if (routine == 0) {
                source = monitorInitPrefix(invocation);
                if (site == MonitorSite.FAILURE && matchesMonitorPool(invocation, source, List.of(), bank)) {
                    acceptMonitorPool(invocation, source, List.of(), bank); return;
                }
                if (!monitorSavedChange(source, bank, 0x80, 0))
                    throw new IllegalStateException("ID19 advanced beyond its native finite Init saved address");
                boolean used = (Byte.toUnsignedInt(bank[monitorSavedAddress(source).orElseThrow()]) & 1) != 0;
                if (invocation.post) {
                    source = source.moveByte(CdObjectState.ByteField.MAP_FRAME, source.subtype() == 8 ? 10 : 12);
                    if (used) source = source.addByte(CdObjectState.ByteField.MAP_FRAME, 1).moveByte(CdObjectState.ByteField.ROUTINE, 6);
                    else source = source.moveByte(CdObjectState.ByteField.COLLISION_TYPE, 0xDF);
                } else if (used) {
                    source = source.moveByte(CdObjectState.ByteField.ROUTINE, 8).moveByte(CdObjectState.ByteField.MAP_FRAME, 17);
                    invocation.noDraw = true; invocation.bodyVerified = true;
                } else source = source.moveByte(CdObjectState.ByteField.COLLISION_TYPE, 0x46)
                        .moveByte(CdObjectState.ByteField.ANIMATION, source.subtype());
            }
            invocation.initVerified = true;
        }
        var extra = new ArrayList<CdObjectState>();
        if (invocation.noDraw) { acceptMonitorPool(invocation, source, extra, bank); return; }
        if (!invocation.mainVerified) {
            int routine = source.routine();
            if (!invocation.post && routine == 4) {
                if (site == MonitorSite.MONITOR_FM) { acceptMonitorPool(invocation, source, extra, bank); return; }
                if (!invocation.sounded) throw new IllegalStateException("Monitor Break stores preceded its actual FM96");
                source = source.addByte(CdObjectState.ByteField.ROUTINE, 4).moveByte(CdObjectState.ByteField.COLLISION_TYPE, 0);
                var working = invocation.currentPool;
                for (int id : new int[]{0x1A, 0x18}) {
                    var free = working.slots().stream().filter(image -> image.id() == 0).findFirst();
                    if (free.isPresent()) {
                        var child = free.orElseThrow().moveByte(CdObjectState.ByteField.ID, id)
                                .moveWord(CdObjectState.WordField.X, source.centreX()).moveWord(CdObjectState.WordField.Y, source.centreY())
                                .moveByte(CdObjectState.ByteField.LAYER, source.unsignedByte(CdObjectState.ByteField.LAYER));
                        if (id == 0x1A) child = child.moveByte(CdObjectState.ByteField.ANIMATION, source.unsignedByte(CdObjectState.ByteField.ANIMATION));
                        else child = child.moveByte(CdObjectState.ByteField.ROUTINE_2, 1).moveByte(CdObjectState.ByteField.SUBTYPE, 1);
                        extra.add(child); working = CdObjectPool.overlay(working, List.of(child));
                    }
                }
                if (!monitorSavedChange(source, bank, 0, 1)) {
                    if (site == MonitorSite.FAILURE) { acceptMonitorPool(invocation, source, extra, bank); return; }
                    throw new IllegalStateException("Monitor Break crossed its finite saved address");
                }
                source = source.moveByte(CdObjectState.ByteField.MAP_FRAME, 17); invocation.directBreak = true;
                invocation.mainVerified = true; invocation.bodyVerified = true;
            } else if (invocation.post && routine == 2 && source.unsignedByte(CdObjectState.ByteField.COLLISION_STATUS) != 0) {
                source = source.moveByte(CdObjectState.ByteField.COLLISION_STATUS, 0);
                if ((frame.environment().movement().gates().zoneAct() >>> 8) == 6) {
                    if (site != MonitorSite.FAILURE) throw new IllegalStateException("Post crossed its absent native MMZ layer read");
                    acceptMonitorPool(invocation, source, extra, bank); return;
                }
                source = source.moveByte(CdObjectState.ByteField.TIME_POST_SPIN, 60).addByte(CdObjectState.ByteField.ROUTINE, 2);
                if (!monitorSavedChange(source, bank, 0, 1)) {
                    if (site == MonitorSite.FAILURE) { acceptMonitorPool(invocation, source, extra, bank); return; }
                    throw new IllegalStateException("Post advanced beyond its finite activation saved address");
                }
                invocation.activationAddress = monitorSavedAddress(source).orElseThrow();
                invocation.mainVerified = true;
            } else if (invocation.post && routine == 4) {
                source = source.addByte(CdObjectState.ByteField.TIME_POST_SPIN, -1);
                if (source.unsignedByte(CdObjectState.ByteField.TIME_POST_SPIN) == 0) {
                    source = source.addByte(CdObjectState.ByteField.ROUTINE, 2)
                            .moveByte(CdObjectState.ByteField.MAP_FRAME, source.subtype() == 8 ? 11 : 13);
                    invocation.bodyVerified = true;
                }
                invocation.mainVerified = true;
            } else if (!invocation.post && routine == 2 && (source.spriteFlags() & 0x80) != 0
                    && source.unsignedByte(CdObjectState.ByteField.ROUTINE_2) != 0) {
                source = monitorGravityPrefix(source); invocation.mainVerified = true;
            } else invocation.mainVerified = true;
        }
        if (site == MonitorSite.SOLID || site == MonitorSite.FLOOR_ANGLE || site == MonitorSite.DIRECTION
                || site == MonitorSite.SUB_CPU || site == MonitorSite.MONITOR_FM) {
            acceptMonitorPool(invocation, source, extra, bank); return;
        }
        if (site == MonitorSite.FAILURE && matchesMonitorPool(invocation, source, extra, bank)) {
            acceptMonitorPool(invocation, source, extra, bank); return;
        }
        if (!invocation.bodyVerified && !invocation.post && source.routine() == 2 && (source.spriteFlags() & 0x80) != 0) {
            if (source.unsignedByte(CdObjectState.ByteField.ROUTINE_2) != 0) {
                if (!invocation.floorStarted) throw new IllegalStateException("Monitor skipped its genuine floor pre-zero");
                // Completed terrain distance is owned by the cached floor producer. Only its negative landing writes are admitted.
                if (!invocation.landingVerified) {
                    int difference = (actual.centreY() - source.centreY()) & 0xFFFF;
                    if (actual.unsignedByte(CdObjectState.ByteField.ROUTINE_2) == 0) {
                        if ((short) difference >= 0 || actual.signedWord(CdObjectState.WordField.Y_VELOCITY) != 0)
                            throw new IllegalStateException("Monitor landing is not its negative native distance branch");
                        source = source.moveWord(CdObjectState.WordField.Y, actual.centreY())
                                .moveWord(CdObjectState.WordField.Y_VELOCITY, 0).moveByte(CdObjectState.ByteField.ROUTINE_2, 0);
                    }
                    invocation.landingVerified = true;
                }
            } else if (!invocation.solidCommitted)
                throw new IllegalStateException("Visible Monitor skipped its actual plain Solid call");
        }
        boolean animation = !invocation.bodyVerified && (invocation.post
                ? source.routine() == 4 && invocation.entry.routine() == 4
                : source.routine() == 6 || source.routine() == 2 && (source.spriteFlags() & 0x80) != 0);
        if (animation && (invocation.post || values.ring().timeStopTimer() == 0))
            source = validateMonitorAnimation(invocation, source, actual, site == MonitorSite.FAILURE);
        if (invocation.post && source.routine() == 4 && invocation.entry.routine() != 4) {
            int stores = source.subtype() == 8 ? 1 : 2;
            if (invocation.directionStores != stores || !invocation.voiced)
                throw new IllegalStateException("Post draw precedes native direction stores/voice");
        }
        acceptMonitorPool(invocation, source, extra, bank); invocation.bodyVerified = true;
    }

    /** Object Animate.asm:14-61, finite Ani_Monitor contains only FF commands; no producer is called. */
    private CdObjectState validateMonitorAnimation(MonitorInvocation invocation, CdObjectState source,
                                                   CdObjectState actual, boolean failure) {
        if (failure && !invocation.post && actual.equals(source)) return source; // Existing Monitor animator failure is atomic.
        var expected = source; int animation = source.unsignedByte(CdObjectState.ByteField.ANIMATION);
        if (animation != source.unsignedByte(CdObjectState.ByteField.PREVIOUS_ANIMATION))
            expected = expected.moveByte(CdObjectState.ByteField.PREVIOUS_ANIMATION, animation)
                    .moveByte(CdObjectState.ByteField.ANIMATION_FRAME, 0).moveByte(CdObjectState.ByteField.ANIMATION_TIME, 0);
        expected = expected.addByte(CdObjectState.ByteField.ANIMATION_TIME, -1);
        if (failure && actual.equals(expected)) return expected; // Stop here: do not prefetch the next native read.
        if (expected.signedByte(CdObjectState.ByteField.ANIMATION_TIME) >= 0) return expected;
        var bytes = view(CdR12aProfile.Asset.ANI_MONITOR).bytes();
        if (animation >= 10) throw new IllegalStateException("ID19 animator advanced past its finite directory");
        int script = (short) bytes.unsignedWord(animation * 2);
        if (script < 0 || script >= bytes.length()) throw new IllegalStateException("ID19 animator advanced past its speed");
        expected = expected.moveByte(CdObjectState.ByteField.ANIMATION_TIME, bytes.unsignedByte(script));
        if (failure && actual.equals(expected)) return expected;
        int index = expected.unsignedByte(CdObjectState.ByteField.ANIMATION_FRAME), at = script + 1 + index;
        if (at < 0 || at >= bytes.length()) throw new IllegalStateException("ID19 animator advanced past its frame");
        int raw = bytes.unsignedByte(at);
        if (raw == 0xFF) {
            expected = expected.moveByte(CdObjectState.ByteField.ANIMATION_FRAME, 0); index = 0;
            if (failure && actual.equals(expected)) return expected;
            raw = bytes.unsignedByte(script + 1);
        } else if (raw >= 0x80) throw new IllegalStateException("Ani_Monitor acquired an undeclared negative command");
        int rotated = ((raw << 3) | (raw >>> 5)) & 0xFF;
        return expected.moveByte(CdObjectState.ByteField.MAP_FRAME, raw & 0x1F)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, (source.spriteFlags() & 0xFC) | ((rotated ^ source.flags()) & 3))
                .moveByte(CdObjectState.ByteField.ANIMATION_FRAME, (index + 1) & 0xFF);
    }

    private CdMonitor.LiveSolid preflightMonitorSolid(CdMonitor.PrefixSolidCut cut) {
        Objects.requireNonNull(cut);
        var invocation = requireMonitorInvocation(placement.pool(), cut.objectIdentity().wholeIndex());
        if (monitorPort || drawingMonitor || queuePort || invocation.post || invocation.drawn || invocation.stopped
                || invocation.solidPreflight || invocation.pendingCut != null || !cut.objectIdentity().equals(invocation.entry.identity()))
            throw new IllegalStateException("Monitor Solid lacks its once-only actual preflight");
        var before = placement.capture(); monitorPort = true;
        try {
            validateMonitorPrefix(invocation, MonitorSite.SOLID);
            var source = invocation.expectedSource;
            invocation.pool.validateRestore(cut.callCut());
            if (source.routine() != 2 || (source.spriteFlags() & 0x80) == 0
                    || source.unsignedByte(CdObjectState.ByteField.ROUTINE_2) != 0
                    || (frame.environment().movement().gates().zoneAct() >>> 8) == 6
                    || !before.pool().slots().equals(cut.callCut().slots())
                    || !cut.expectedPlayer().equals(springPlayer()) || !cut.expectedGlobals().equals(springGlobals())
                    || !cut.expectedGates().equals(new CdSolidObject.Gates(frame.environment().globals().debugMode(),
                            frame.environment().movement().gates().miniSonic())))
                throw new IllegalStateException("Monitor plain Solid is not its actual visible/nonfalling full96/Frame cut");
            var live = new CdMonitor.LiveSolid(springPlayer(), cut.expectedGates(), springGlobals());
            verifyMonitorCallback(invocation, before, null);
            invocation.pendingCut = cut; invocation.solidPreflight = true; return live;
        } catch (RuntimeException | Error failure) {
            verifyMonitorCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { monitorPort = false; }
    }
    private void validateMonitorCompleted(CdSolidObject.Result result) {
        if (result.entry() != CdSolidObject.Entry.SOLID_OBJECT || result.path() == CdSolidObject.Path.ROUTINE_RETURN
                || result.d0().isEmpty() || result.d0().getAsInt() != (result.path() == CdSolidObject.Path.STAND_ON
                        || result.path() == CdSolidObject.Path.HIT_BOTTOM ? 1 : 0))
            throw new IllegalStateException("Monitor completed a different helper or fabricated native return");
    }
    private void validateMonitorStopped(CdSolidObject.NativeStopped stopped) {
        if (stopped.prefix().entry() != CdSolidObject.Entry.SOLID_OBJECT || stopped.stop().originalException() == null)
            throw new IllegalStateException("Monitor stopped outcome is not the genuine plain Solid prefix");
    }
    private List<CdObjectState> validateMonitorImages(MonitorInvocation invocation, CdMonitor.PrefixSolidCut cut,
            CdObjectState object, List<CdObjectState> others, Set<CdObjectState.ByteField> writes) {
        var source = cut.callCut().slots().get(invocation.whole - 32);
        if (!source.identity().equals(object.identity())
                || writes.stream().anyMatch(field -> field != CdObjectState.ByteField.FLAGS && field != CdObjectState.ByteField.SOLID_TYPE)
                || writes.contains(CdObjectState.ByteField.SOLID_TYPE) && object.unsignedByte(CdObjectState.ByteField.SOLID_TYPE) != 0
                || ((source.flags() ^ object.flags()) & ~0x28) != 0)
            throw new IllegalStateException("Monitor Solid changed canonical fields/masks; ROUTINE_2 is not write authority");
        byte[] old = source.copyBytes(), next = object.copyBytes();
        for (int at = 0; at < CdObjectState.BYTE_SIZE; at++) {
            int mask = at == 0x22 && writes.contains(CdObjectState.ByteField.FLAGS) ? 0x28
                    : at == 0x25 && writes.contains(CdObjectState.ByteField.SOLID_TYPE) ? 0xFF : 0;
            if (((old[at] ^ next[at]) & ~mask & 0xFF) != 0)
                throw new IllegalStateException("Monitor plain Solid changed undeclared full-image byte " + at);
        }
        if (others.size() > 1) throw new IllegalStateException("Monitor Solid returned more than one native stand release");
        var images = new ArrayList<CdObjectState>(); images.add(object);
        var seen = new HashSet<Integer>(); seen.add(invocation.whole);
        for (var other : others) {
            int whole = other.identity().wholeIndex();
            if (whole < 32 || whole >= 128 || !seen.add(whole) || whole != cut.expectedPlayer().standObject())
                throw new IllegalStateException("Monitor release lacks its unique actual dynamic stand link");
            var before = cut.callCut().slots().get(whole - 32);
            if (!before.moveByte(CdObjectState.ByteField.FLAGS, before.flags() & ~8).equals(other))
                throw new IllegalStateException("Monitor stand release changed bytes beyond the native BCLR3");
            images.add(other);
        }
        return List.copyOf(images);
    }

    /** All full-pool/Frame/ledger/expected values precede the first owner assignment. */
    private CdMonitor.LiveSolid commitMonitorSolid(CdMonitor.PrefixSolidCall call) {
        Objects.requireNonNull(call); var cut = call.cut();
        var invocation = requireMonitorInvocation(placement.pool(), cut.objectIdentity().wholeIndex());
        if (monitorPort || drawingMonitor || queuePort || invocation.post || invocation.drawn || invocation.stopped
                || invocation.pendingCut != cut || invocation.adoptedOutcome == call.outcome())
            throw new IllegalStateException("Monitor commit lacks the actual pending preflight identity");
        var pool = invocation.pool; pool.validateRestore(cut.callCut());
        if (!pool.capture().slots().equals(cut.callCut().slots()) || !cut.callCut().slots().equals(invocation.currentPool.slots())
                || !cut.expectedPlayer().equals(springPlayer()) || !cut.expectedGlobals().equals(springGlobals())
                || !cut.expectedGates().equals(new CdSolidObject.Gates(frame.environment().globals().debugMode(),
                        frame.environment().movement().gates().miniSonic())))
            throw new IllegalStateException("Stale Monitor full96/actual complete Frame publication");
        CdObjectState object; List<CdObjectState> others; Set<CdObjectState.ByteField> writes;
        List<CdPlayerTouchResponse.FmSound> requests; CdPlayerFrameRuntime.ObjectSolidPublication readyFrame; boolean stopped;
        if (call.outcome() instanceof CdSolidObject.Completed done) {
            var result = done.completedResult(); validateMonitorCompleted(result);
            object = result.object(); others = result.otherSlots(); writes = result.objectWrites(); requests = result.fmRequests();
            readyFrame = frame.prepareObjectSolidResult(cut.expectedPlayer(), cut.expectedGlobals(), result); stopped = false;
        } else {
            var nativeStop = (CdSolidObject.NativeStopped) call.outcome(); validateMonitorStopped(nativeStop);
            var prefix = nativeStop.prefix();
            object = prefix.object(); others = prefix.otherSlots(); writes = prefix.objectWrites(); requests = prefix.fmRequests();
            readyFrame = frame.prepareObjectSolidPrefix(cut.expectedPlayer(), cut.expectedGlobals(), prefix); stopped = true;
        }
        var images = validateMonitorImages(invocation, cut, object, others, writes);
        var readyPool = CdObjectPool.overlay(cut.callCut(), images); pool.validateRestore(readyPool);
        var readyActor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), readyFrame.finalState(), invocation.actor.display());
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), readyFrame.finalEnvironment(),
                invocation.shared.artState(), invocation.shared.paused(), invocation.shared.vIntClock());
        var ledger = new ArrayList<>(sounds);
        for (var fm : requests) ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
        var readySounds = List.copyOf(ledger); var readyValues = preparedMonitorValues(MONITOR_SOLID);
        var readyLive = new CdMonitor.LiveSolid(
                call.outcome() instanceof CdSolidObject.Completed done ? done.completedResult().player() : ((CdSolidObject.NativeStopped) call.outcome()).prefix().player(),
                cut.expectedGates(),
                call.outcome() instanceof CdSolidObject.Completed done ? done.completedResult().globals() : ((CdSolidObject.NativeStopped) call.outcome()).prefix().globals());
        monitorPort = true;
        try {
            verifyMonitorInvocation(invocation);
            if (!pool.capture().slots().equals(cut.callCut().slots()) || !sameBytes(placement.capture().savedFlags(), invocation.expectedSaved))
                throw new IllegalStateException("Monitor source/bank changed before adoption");
            frame.validatePreparedObjectSolidPublication(readyFrame);
            pool.restore(readyPool);
            frame.applyPreparedObjectSolidPublication(readyFrame);
            sounds = readySounds; values = readyValues;
            invocation.actor = readyActor; invocation.shared = readyShared; invocation.expectedSounds = readySounds;
            invocation.expectedValues = readyValues; invocation.currentPool = readyPool; invocation.expectedSource = object;
            invocation.stopped = stopped; invocation.adoptedOutcome = call.outcome(); invocation.pendingCut = null; invocation.solidCommitted = true;
            verifyMonitorInvocation(invocation);
            if (!pool.capture().slots().equals(readyPool.slots()) || !sameBytes(placement.capture().savedFlags(), invocation.expectedSaved))
                throw new IllegalStateException("Monitor output full96/saved readback differs from prepared values");
            return readyLive;
        } catch (RuntimeException | Error failure) {
            verifyMonitorFailure(failure); throw failure;
        } finally { monitorPort = false; }
    }
    private Values preparedMonitorValues(SonicCdGameModule.Act2LoadCall call) {
        var old = values; var calls = new ArrayList<>(old.calls()); calls.add(call);
        return new Values(old.phase(), old.section(), old.cpuPalette(), old.fadePalette(), old.waterPalette(), old.displayedPalette(), old.cpuSat(),
                old.displayedSat(), old.displayedHScroll(), old.displayedVScrollLongBits(), old.foregroundPlane(), old.ring(),
                old.level(), old.globals(), old.hudObjects(), old.fadeProgress(), List.copyOf(calls), old.loop());
    }
    private CdPlayerBody.Environment monitorAngleEnvironment(CdPlayerBody.Environment environment, int angle) {
        var movement = environment.movement(); var globals = movement.globals();
        var next = new CdPlayerMovement.Globals(globals.lookMode(), globals.cameraYCentre(), angle, globals.secondaryAngle(),
                globals.boredTimer(), globals.scoreChain(), globals.debugAngle(), globals.debugAngleShift(),
                globals.debugQuadrant(), globals.debugFloorDistance());
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(movement.physics(), movement.bounds(), movement.gates(), next),
                environment.controls(), environment.globals(), environment.dynamicSlots());
    }
    /** Every genuine ObjGetFloorDist store: pre-zero first, then all recursive raw writes with no count cap. */
    private void commitMonitorPrimaryAngle(int expected, int angle) {
        var invocation = currentMonitorInvocation();
        if (monitorPort || drawingMonitor || queuePort || invocation.post || invocation.drawn || invocation.stopped
                || invocation.solidPreflight || (angle & ~0xFF) != 0
                || frame.environment().movement().globals().primaryAngle() != expected
                || invocation.floorWrites == 0 && angle != 0)
            throw new IllegalStateException("Monitor angle lacks its actual fall/native pre-zero/full Frame CAS");
        var before = placement.capture(); monitorPort = true;
        try {
            validateMonitorPrefix(invocation, MonitorSite.FLOOR_ANGLE);
            var source = invocation.expectedSource;
            if (source.routine() != 2 || (source.spriteFlags() & 0x80) == 0
                    || source.unsignedByte(CdObjectState.ByteField.ROUTINE_2) == 0)
                throw new IllegalStateException("Monitor angle is outside its actual native gravity/floor cut");
            var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(),
                    monitorAngleEnvironment(invocation.shared.environment(), angle), invocation.shared.artState(),
                    invocation.shared.paused(), invocation.shared.vIntClock());
            var readyValues = preparedMonitorValues(MONITOR_FLOOR_ANGLE);
            verifyMonitorCallback(invocation, before, null);
            frame.applyObjectPrimaryAngle(angle);
            values = readyValues; invocation.expectedValues = readyValues; invocation.shared = readyShared;
            invocation.floorStarted = true; invocation.floorWrites++;
            verifyMonitorCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifyMonitorCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { monitorPort = false; }
    }

    private CdPlayerBody.Environment monitorDirectionEnvironment(CdPlayerBody.Environment environment, int direction) {
        var movement = environment.movement(); var old = movement.gates();
        var gates = new CdPlayerMovement.Gates(old.zoneAct(), old.timeZone(), old.miniSonic(), old.waterSlide(),
                old.windTunnel(), old.speedShoes(), direction);
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(movement.physics(), movement.bounds(), gates, movement.globals()),
                environment.controls(), environment.globals(), environment.dynamicSlots());
    }
    private void commitTimePostDirection(CdTimePost.DirectionCall call) {
        Objects.requireNonNull(call);
        var invocation = requireMonitorInvocation(placement.pool(), call.identity().wholeIndex());
        if (monitorPort || drawingMonitor || queuePort || !invocation.post || invocation.drawn || invocation.voiced
                || invocation.directionStores > 1 || invocation.directionStores == 1 && invocation.entry.subtype() == 8
                || call.rawDirection() != (invocation.directionStores == 0 ? 0xFF : 1))
            throw new IllegalStateException("Post direction lacks its native FF/conditional01 operation");
        var before = placement.capture(); monitorPort = true;
        try {
            validateMonitorPrefix(invocation, MonitorSite.DIRECTION); var source = invocation.expectedSource;
            int at = invocation.activationAddress;
            if (source.routine() != 4 || source.unsignedByte(CdObjectState.ByteField.TIME_POST_SPIN) != 60
                    || source.unsignedByte(CdObjectState.ByteField.COLLISION_STATUS) != 0
                    || invocation.entry.unsignedByte(CdObjectState.ByteField.COLLISION_STATUS) == 0
                    || at < 2 || at >= CdObjectPlacementRuntime.SAVED_BANK_BYTES || (invocation.expectedSaved.unsignedByte(at) & 1) == 0)
                throw new IllegalStateException("Post direction lacks actual cleared-contact/spin60/activation prefix");
            frame.validateObjectTimePostDirection(call.expectedRawDirection(), call.rawDirection());
            var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(),
                    monitorDirectionEnvironment(invocation.shared.environment(), call.rawDirection()), invocation.shared.artState(),
                    invocation.shared.paused(), invocation.shared.vIntClock());
            var readyValues = preparedMonitorValues(TIME_POST_DIRECTION);
            verifyMonitorCallback(invocation, before, null);
            frame.applyObjectTimePostDirection(call.expectedRawDirection(), call.rawDirection());
            values = readyValues; invocation.expectedValues = readyValues; invocation.shared = readyShared; invocation.directionStores++;
            verifyMonitorCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifyMonitorCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { monitorPort = false; }
    }
    private void playTimePostSubCpu(CdTimePost.SubCpuCommand command) {
        Objects.requireNonNull(command); var invocation = currentMonitorInvocation();
        if (monitorPort || drawingMonitor || queuePort || !invocation.post || invocation.drawn || invocation.voiced
                || invocation.directionStores != (invocation.entry.subtype() == 8 ? 1 : 2)
                || command != (invocation.entry.subtype() == 8 ? CdTimePost.SubCpuCommand.SCMD_PASTSFX : CdTimePost.SubCpuCommand.SCMD_FUTURESFX))
            throw new IllegalStateException("Post voice lacks completed direction stores or exact native77/76 command");
        var before = placement.capture(); monitorPort = true;
        try {
            validateMonitorPrefix(invocation, MonitorSite.SUB_CPU);
            var ledger = new ArrayList<>(sounds); ledger.add(new SonicCdGameModule.PastTimePostSubCpu(command));
            var readySounds = List.copyOf(ledger); var readyValues = preparedMonitorValues(TIME_POST_SUB_CPU);
            verifyMonitorCallback(invocation, before, null);
            sounds = readySounds; values = readyValues; invocation.expectedSounds = readySounds; invocation.expectedValues = readyValues; invocation.voiced = true;
            verifyMonitorCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifyMonitorCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { monitorPort = false; }
    }
    private void playMonitorFm(CdMonitor.FmSound sound) {
        var invocation = currentMonitorInvocation();
        if (monitorPort || drawingMonitor || queuePort || invocation.post || invocation.drawn || invocation.sounded
                || invocation.entry.routine() != 4 || invocation.solidPreflight || invocation.floorStarted
                || sound != CdMonitor.FmSound.FM_DESTROY)
            throw new IllegalStateException("Monitor FM96 is not its native pre-Break-store cut");
        var before = placement.capture(); monitorPort = true;
        try {
            validateMonitorPrefix(invocation, MonitorSite.MONITOR_FM);
            var ledger = new ArrayList<>(sounds); ledger.add(new SonicCdGameModule.PastMonitorFm(sound));
            var readySounds = List.copyOf(ledger); var readyValues = preparedMonitorValues(MONITOR_FM);
            verifyMonitorCallback(invocation, before, null);
            sounds = readySounds; values = readyValues; invocation.expectedSounds = readySounds; invocation.expectedValues = readyValues; invocation.sounded = true;
            verifyMonitorCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifyMonitorCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { monitorPort = false; }
    }
    private void verifyMonitorCallback(MonitorInvocation invocation, CdObjectPlacementRuntime.Snapshot before, CdObjectState cleared) {
        verifyMonitorInvocation(invocation);
        var images = new ArrayList<>(before.pool().slots());
        if (cleared != null) images.set(invocation.whole - 32, cleared);
        var now = placement.capture();
        if (!now.status().equals(before.status()) || !now.pool().slots().equals(images) || !sameBytes(now.savedFlags(), before.savedFlags()))
            throw new IllegalStateException("Monitor/Post callback changed full96/saved/status beyond its actual queue bit7 clear");
    }
    private void verifyMonitorCallbackFailure(MonitorInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                               CdObjectState cleared, Throwable original) {
        try { verifyMonitorCallback(invocation, before, cleared); }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    private void drawMonitor(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireMonitorInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped || invocation.pendingCut != null)
            throw new IllegalStateException("Monitor/Post DrawObject lacks its once-only genuine native return");
        validateMonitorPrefix(invocation, MonitorSite.DRAW);
        if (invocation.noDraw) throw new IllegalStateException("ID19 attempted Draw on a native direct-return branch");
        var before = placement.capture(); var current = pool.state(invocation.whole);
        var cleared = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        invocation.drawn = true; drawingMonitor = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(), current.unsignedByte(CdObjectState.ByteField.Y_RADIUS),
                    current.unsignedByte(CdObjectState.ByteField.WIDTH), current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            if (!invocation.screenCleared) throw new IllegalStateException("ID19 actual DrawObject did not clear bit7");
            verifyMonitorCallback(invocation, before, cleared); invocation.tailArmed = !invocation.directBreak;
        } catch (RuntimeException | Error failure) {
            verifyMonitorCallbackFailure(invocation, before, invocation.screenCleared ? cleared : null, failure); throw failure;
        } finally { drawingMonitor = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readMonitorDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireMonitorInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingMonitor || monitorPort || queuePort) throw new IllegalStateException("No actual ID19 queue read");
        var before = placement.capture(); queuePort = true;
        try {
            acceptMonitorPool(invocation, invocation.expectedSource, List.of(), invocation.expectedSaved.copy());
            var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(), current.centreX(), current.centreY(),
                    current.word(CdObjectState.WordField.X_FRACTION), current.longBits(CdObjectState.LongField.MAPPINGS),
                    current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifyMonitorCallback(invocation, before, null); return live;
        } catch (RuntimeException | Error failure) {
            verifyMonitorCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { queuePort = false; }
    }
    private void writeMonitorOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireMonitorInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingMonitor || monitorPort || queuePort || onScreen || invocation.screenCleared)
            throw new IllegalStateException("Only actual ID19 DrawObject may clear bit7 once");
        var before = placement.capture(); var current = invocation.pool.state(invocation.whole);
        acceptMonitorPool(invocation, invocation.expectedSource, List.of(), invocation.expectedSaved.copy());
        var next = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        var readyPool = CdObjectPool.overlay(before.pool(), List.of(next)); invocation.pool.validateRestore(readyPool);
        queuePort = true;
        try {
            invocation.pool.write(next); invocation.screenCleared = true; invocation.currentPool = readyPool; invocation.expectedSource = next;
            verifyMonitorCallback(invocation, before, next);
        } catch (RuntimeException | Error failure) {
            verifyMonitorCallbackFailure(invocation, before, invocation.screenCleared ? next : null, failure); throw failure;
        } finally { queuePort = false; }
    }



    private void requireCheckpointIdle() {
        if(activeCheckpoint!=null || checkpointPort || drawingCheckpoint) throw new IllegalStateException("Actual Checkpoint owns this cut");
    }
    private void requireTamabbohFloatIdle() {
        requireCheckpointIdle();
        requireWallIdle();
        if (activeTamabboh != null || activeFloat != null || tamabbohPort || drawingTamabboh || floatPort || drawingFloat)
            throw new IllegalStateException("Another actual Act2 Tamabboh/Float source scope is active");
    }
    private void requireAntonPataIdle() {
        requireTamabbohFloatIdle();
        if(activeAnton!=null || activePata!=null || antonPort || drawingAnton || drawingPata)
            throw new IllegalStateException("Another actual Act2 enemy source scope is active");
    }
    private void requireBoulderPlatformIdle() {
        requireAntonPataIdle();
        if (activeTamabboh != null || tamabbohPort || drawingTamabboh || activeFloat != null || floatPort || drawingFloat || activeCheckpoint != null || checkpointPort || drawingCheckpoint || activeCollapse!=null || collapsePort || drawingCollapse || activeBoulder!=null || activePlatform!=null || boulderPort || drawingBoulder || platformPort || drawingPlatform)
            throw new IllegalStateException("Another actual Act2 family scope is active");
    }
    private final class BoulderInvocation {
        private final CdBoulder body = boulder;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR12aTerrainAssets sourceAssets = assets;
        private final Object stageSource = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Values base = values;
        private Values expectedValues = base;
        private final CdObjectPlacementRuntime.Status status = placement.status();
        private final CdAssetBytes savedFlags = placement.capture().savedFlags();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.captureAct2Scroll();
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final CdObjectDrawQueue.CameraWords camera;
        private CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private CdObjectPool.Snapshot currentPool = pool.capture();
        private CdSolidObject.Outcome adopted;
        private CdObjectState drawSource;
        private boolean solidTaken, stopped, drawn, screenCleared;
        private BoulderInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.act2ForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
        }
    }

    private void runBoulder(int whole) {
        requireDoorIdle();
        requireLogMosquiIdle();
        requireAntonPataIdle();
        module.requireAct2LoadingVisit(this);
        if (activeTitle != null || titlePort || drawingTitle || !runningObjects || servicing || activeDrain != null || activePrimary != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeTamabboh != null || tamabbohPort || drawingTamabboh || activeFloat != null || floatPort || drawingFloat || activeCheckpoint != null || checkpointPort || drawingCheckpoint || activeCollapse!=null || collapsePort || drawingCollapse || activeBoulder != null || activeMonitor != null || reservedPort || drawingReserved
                || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform
                || monitorPort || drawingMonitor || boulderPort || drawingBoulder || queuePort
                || whole < 32 || whole >= 128 || placement.pool().state(whole).id() != CdBoulder.OBJECT_ID
                || values.phase() != BEGIN_LEVEL || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Act2 Boulder dispatch");
        var invocation = new BoulderInvocation(whole); activeBoulder = invocation;
        try {
            var environment = frame.environment(); var gates = environment.movement().gates();
            var result = boulder.run(whole, new CdBoulder.Globals(gates.timeZone(), gates.timeWarpDirection(), invocation.camera.xWord()),
                    new CdBoulder.SolidInputs(springPlayer(), new CdSolidObject.Gates(environment.globals().debugMode(), gates.miniSonic()), springGlobals()));
            verifyBoulderInvocation(invocation); requireBoulderOtherImages(invocation);
            var observedSolid = result.effects().stream().filter(CdBoulder.Solid.class::isInstance)
                    .map(CdBoulder.Solid.class::cast).toList();
            boolean needsSolid = (invocation.entry.spriteFlags() & 0x80) != 0;
            if (invocation.solidTaken != needsSolid || observedSolid.size() != (needsSolid ? 1 : 0)
                    || needsSolid && (!(invocation.adopted instanceof CdSolidObject.Completed completed)
                    || observedSolid.getFirst().result() != completed.completedResult()))
                throw new IllegalStateException("Boulder lacks its exact reached native Solid receipt");
            if (invocation.stopped || !invocation.drawn || !invocation.screenCleared
                    || !result.state().equals(invocation.pool.state(whole)))
                throw new IllegalStateException("Boulder returned without its actual completed Solid/Draw/source cut");
        } catch (RuntimeException | Error failure) {
            if (invocation.adopted instanceof CdSolidObject.NativeStopped nativeStop) {
                var original = nativeStop.stop().originalException();
                if (failure != original) original.addSuppressed(failure);
                verifyBoulderFailure(invocation, original); throw original;
            }
            verifyBoulderFailure(invocation, failure); throw failure;
        } finally { activeBoulder = null; }
    }

    private BoulderInvocation requireBoulderInvocation(CdObjectPool pool, int whole) {
        var invocation = activeBoulder;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128)
            throw new IllegalStateException("No actual cached Boulder callback at this physical pointer");
        verifyBoulderInvocation(invocation); return invocation;
    }
    private void verifyBoulderInvocation(BoulderInvocation invocation) {
        requireAntonPataIdle();
        module.requireAct2LoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeTitle != null || titlePort || drawingTitle || activeBoulder != invocation || !runningObjects || servicing || activeDrain != null || activePrimary != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeMonitor != null || reservedPort || drawingReserved
                || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform
                || monitorPort || drawingMonitor
                || invocation.body != boulder || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.stageSource != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || invocation.world != mode.getWorldSession()
                || invocation.generation != mode.getLevelManager().getCompletedProductionLoadGeneration()
                || invocation.expectedValues != values || values.phase() != BEGIN_LEVEL || invocation.expectedSounds != sounds
                || !placement.status().equals(invocation.status) || !presentation.sameAct2Scroll(invocation.scroll)
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared))
            throw new IllegalStateException("Boulder changed another owner or retained a stale actual World/Frame cut");
        requireBoulderOtherImages(invocation);
    }
    private void requireBoulderOtherImages(BoulderInvocation invocation) {
        var actual = placement.capture(); var source = invocation.pool.state(invocation.whole);
        for (var row : invocation.currentPool.slots()) if (row.identity().wholeIndex() != invocation.whole
                && !row.equals(invocation.pool.state(row.identity().wholeIndex())))
            throw new IllegalStateException("Boulder changed an unrelated complete physical image");
        var expected = invocation.currentPool.slots().get(invocation.whole-32);
        boolean nativeSource = source.equals(expected);
        if (!invocation.drawn && !invocation.solidTaken && invocation.entry.routine()==0) {
            var init = boulderInitHeader(invocation.entry);
            nativeSource |= source.equals(init);
            if (invocation.entry.subtype2()==0) nativeSource |= source.equals(init.moveWord(CdObjectState.WordField.TILE,
                    CdR11aObjectAssets.decodeTileWord(assets.objects().view(CdR12aProfile.Asset.OBJECT_TILE_IDS).copy(),CdBoulder.TILE_SELECTOR,0)));
        }
        boolean deleted=invocation.drawn && invocation.screenCleared && invocation.drawSource!=null
                && boulderWouldDespawn(invocation,invocation.drawSource.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,invocation.drawSource.spriteFlags()&0x7F))
                && Arrays.equals(source.copyBytes(),new byte[64]);
        if (!nativeSource && !deleted) throw new IllegalStateException("Boulder selected full64 is outside its native Init/Solid/Draw/despawn prefix");
        byte[] saved=invocation.savedFlags.copy();
        if (deleted && invocation.drawSource.savedFlagsId()!=0) {
            int period=invocation.shared.environment().movement().gates().timeZone()&0x7F;
            if ((invocation.shared.environment().movement().gates().timeZone()&0x80)!=0) {
                int remapped=(period-(byte)invocation.shared.environment().movement().gates().timeWarpDirection())&0xFFFF;
                period=(short)remapped<0?0:remapped<3?remapped:2;
            }
            saved[2+3*invocation.drawSource.savedFlagsId()+period]&=0x7F;
        }
        if (!sameBytes(actual.savedFlags(),CdAssetBytes.copyOf(saved))) throw new IllegalStateException("Boulder changed another saved byte");
    }
    private CdObjectState boulderInitHeader(CdObjectState source) {
        return source.addByte(CdObjectState.ByteField.ROUTINE,2).moveByte(CdObjectState.ByteField.SPRITE_FLAGS,source.spriteFlags()|4)
                .moveByte(CdObjectState.ByteField.PRIORITY,4).moveLong(CdObjectState.LongField.MAPPINGS,CdR12aProfile.Asset.MAP_SPR_BOULDER.nativeAddress())
                .moveByte(CdObjectState.ByteField.WIDTH,16).moveByte(CdObjectState.ByteField.Y_RADIUS,16).moveByte(CdObjectState.ByteField.MAP_FRAME,0);
    }
    private boolean boulderWouldDespawn(BoulderInvocation invocation,CdObjectState source) {
        return (invocation.shared.environment().movement().gates().timeZone()&0x80)!=0
                || (source.spriteFlags()&0x80)==0 && (((source.centreX()&0xFF80)-((invocation.camera.xWord()-0x80)&0xFF80))&0xFFFF)>0x280;
    }
    private void verifyBoulderFailure(BoulderInvocation invocation, Throwable original) {
        try { verifyBoulderInvocation(invocation); requireBoulderOtherImages(invocation); }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    /** Prepare every pool/Frame/ledger value before publishing the genuine current plain-Solid outcome. */
    private void commitBoulderSolid(CdBoulder.PrefixSolidCall call) {
        Objects.requireNonNull(call);
        var invocation = requireBoulderInvocation(placement.pool(), activeBoulder == null ? -1 : activeBoulder.whole);
        if (!boulder.isCurrentPrefixSolidCall(call)) throw new IllegalStateException("Boulder lacks its actual pending producer call");
        if (boulderPort || drawingBoulder || queuePort || invocation.solidTaken || invocation.stopped || invocation.drawn)
            throw new IllegalStateException("Boulder Solid lacks its actual once-only pre-Draw helper cut");
        var pool = invocation.pool; var cut = call.callCut(); pool.validateRestore(cut);
        var source = pool.state(invocation.whole);
        requireBoulderOtherImages(invocation);
        if (!pool.capture().slots().equals(cut.slots()) || !source.equals(cut.slots().get(invocation.whole - 32))
                || source.id() != CdBoulder.OBJECT_ID || source.routine() != 2 || source.signedByte(CdObjectState.ByteField.SPRITE_FLAGS) >= 0
                || !call.expectedPlayer().equals(springPlayer()) || !call.expectedGlobals().equals(springGlobals()))
            throw new IllegalStateException("Boulder Solid differs from its actual full96/source/player/global cut");
        CdObjectState object; List<CdObjectState> others; Set<CdObjectState.ByteField> writes;
        List<CdPlayerTouchResponse.FmSound> requests; CdPlayerFrameRuntime.ObjectSolidPublication readyFrame;
        boolean stopped;
        if (call.outcome() instanceof CdSolidObject.Completed completed) {
            var result = completed.completedResult();
            if (result.entry() != CdSolidObject.Entry.SOLID_OBJECT) throw new IllegalStateException("Boulder completed a different helper");
            object = result.object(); others = result.otherSlots(); writes = result.objectWrites(); requests = result.fmRequests();
            readyFrame = frame.prepareObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result); stopped = false;
        } else {
            var prefix = ((CdSolidObject.NativeStopped) call.outcome()).prefix();
            if (prefix.entry() != CdSolidObject.Entry.SOLID_OBJECT) throw new IllegalStateException("Boulder stopped a different helper");
            object = prefix.object(); others = prefix.otherSlots(); writes = prefix.objectWrites(); requests = prefix.fmRequests();
            readyFrame = frame.prepareObjectSolidPrefix(call.expectedPlayer(), call.expectedGlobals(), prefix); stopped = true;
        }
        validateSpringSolidImage(source, object, true, writes);
        if (writes.contains(CdObjectState.ByteField.SOLID_TYPE)
                && object.unsignedByte(CdObjectState.ByteField.SOLID_TYPE) != 0)
            throw new IllegalStateException("Boulder plain Solid cannot store Top/Bottom type bytes");
        var images = new ArrayList<CdObjectState>(); images.add(object);
        if (others.size() > 1) throw new IllegalStateException("Boulder Solid released more than one native stand link");
        for (var other : others) {
            int whole = other.identity().wholeIndex();
            if (whole < 32 || whole >= 128 || whole == invocation.whole || whole != call.expectedPlayer().standObject())
                throw new IllegalStateException("Boulder stand release lacks its actual dynamic prior link");
            var beforeOther = cut.slots().get(whole - 32);
            if (!beforeOther.moveByte(CdObjectState.ByteField.FLAGS, beforeOther.flags() & ~8).equals(other))
                throw new IllegalStateException("Boulder stand release changed more than its actual linked flag bit");
            images.add(other);
        }
        var readyPool = CdObjectPool.overlay(cut, images); pool.validateRestore(readyPool);
        var before = placement.capture();
        var readyActor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), readyFrame.finalState(), invocation.actor.display());
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), readyFrame.finalEnvironment(),
                invocation.shared.artState(), invocation.shared.paused(), invocation.shared.vIntClock());
        var ledger = new ArrayList<>(sounds);
        for (var fm : requests) ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
        var readySounds = List.copyOf(ledger);
        var readyValues = preparedMonitorValues(BOULDER_SOLID);
        boulderPort = true;
        try {
            verifyBoulderInvocation(invocation);
            if (!pool.capture().slots().equals(cut.slots()) || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Boulder source changed before prepared publication");
            frame.validatePreparedObjectSolidPublication(readyFrame);
            pool.restore(readyPool);
            frame.applyPreparedObjectSolidPublication(readyFrame);
            sounds = readySounds; values = readyValues; invocation.expectedValues = readyValues;
            invocation.actor = readyActor; invocation.shared = readyShared; invocation.expectedSounds = readySounds;
            invocation.currentPool = readyPool; invocation.solidTaken = true; invocation.stopped = stopped; invocation.adopted = call.outcome();
            verifyBoulderInvocation(invocation);
            if (!pool.capture().slots().equals(readyPool.slots()) || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Boulder Solid did not adopt its complete output96 and saved nonwrites");
        } catch (RuntimeException | Error failure) { verifyBoulderFailure(invocation, failure); throw failure; }
        finally { boulderPort = false; }
    }

    private void drawBoulder(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireBoulderInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (boulderPort || drawingBoulder || queuePort || invocation.drawn || invocation.stopped
                || !identity.equals(invocation.entry.identity()) || pool.state(invocation.whole).id() != CdBoulder.OBJECT_ID)
            throw new IllegalStateException("Boulder DrawObject lacks its genuine live physical source");
        requireBoulderOtherImages(invocation);
        boolean needsSolid = (invocation.entry.spriteFlags() & 0x80) != 0;
        if (invocation.solidTaken != needsSolid)
            throw new IllegalStateException("Boulder skipped its reached Solid publication before Draw");
        if (invocation.entry.routine() == 0 && !needsSolid) {
            var completedInit = boulderInitHeader(invocation.entry).moveWord(CdObjectState.WordField.TILE,
                    CdR11aObjectAssets.decodeTileWord(assets.objects().view(CdR12aProfile.Asset.OBJECT_TILE_IDS).copy(),CdBoulder.TILE_SELECTOR,0));
            if (!pool.state(invocation.whole).equals(completedInit))
                throw new IllegalStateException("Boulder Draw regressed its completed Init image");
        }
        invocation.drawSource = pool.state(invocation.whole);
        invocation.currentPool=CdObjectPool.overlay(invocation.currentPool,List.of(invocation.drawSource));
        invocation.drawn = true; drawingBoulder = true;
        var current = invocation.drawSource;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(), current.unsignedByte(CdObjectState.ByteField.Y_RADIUS),
                    current.unsignedByte(CdObjectState.ByteField.WIDTH), current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            verifyBoulderInvocation(invocation);
            if (!invocation.screenCleared) throw new IllegalStateException("Boulder skipped its actual DrawObject BCLR7");
        } catch (RuntimeException | Error failure) { verifyBoulderFailure(invocation, failure); throw failure; }
        finally { drawingBoulder = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readBoulderDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireBoulderInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingBoulder || boulderPort || queuePort || !invocation.drawn || invocation.stopped)
            throw new IllegalStateException("No actual Boulder collecting queue read");
        var before = placement.capture(); queuePort = true;
        try {
            var source = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(source.id(), source.spriteFlags(), source.centreX(), source.centreY(),
                    source.word(CdObjectState.WordField.X_FRACTION), source.longBits(CdObjectState.LongField.MAPPINGS),
                    source.unsignedByte(CdObjectState.ByteField.MAP_FRAME), source.word(CdObjectState.WordField.TILE));
            verifyBoulderInvocation(invocation);
            if (!invocation.pool.capture().slots().equals(before.pool().slots())
                    || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Boulder queue read changed actual full96/saved state");
            return live;
        } catch (RuntimeException | Error failure) { verifyBoulderFailure(invocation, failure); throw failure; }
        finally { queuePort = false; }
    }
    private void writeBoulderOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireBoulderInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingBoulder || boulderPort || queuePort || onScreen || invocation.screenCleared || !invocation.drawn || invocation.stopped)
            throw new IllegalStateException("Only actual Boulder DrawObject may clear bit7 once");
        var before = placement.capture(); var source = invocation.pool.state(invocation.whole);
        if (!source.equals(invocation.drawSource)) throw new IllegalStateException("Boulder changed its native Draw source before BCLR7");
        var next = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() & 0x7F);
        var ready = CdObjectPool.overlay(before.pool(), List.of(next)); invocation.pool.validateRestore(ready);
        queuePort = true;
        try {
            verifyBoulderInvocation(invocation);
            if (!invocation.pool.capture().slots().equals(before.pool().slots())) throw new IllegalStateException("Stale Boulder Draw pool");
            invocation.pool.write(next); invocation.currentPool = ready; invocation.screenCleared = true;
            verifyBoulderInvocation(invocation);
            if (!invocation.pool.capture().slots().equals(ready.slots())
                    || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Boulder Draw changed more than its exact source sprite byte");
        } catch (RuntimeException | Error failure) { verifyBoulderFailure(invocation, failure); throw failure; }
        finally { queuePort = false; }
    }


    private final class PlatformInvocation {
        private final CdPlatform body = platform;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR12aTerrainAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final Values base = values;
        private Values expectedValues = base;
        private final CdObjectPlacementRuntime.Status status = placement.status();
        private final CdAssetBytes savedFlags = placement.capture().savedFlags();
        private CdObjectPool.Snapshot currentPool=pool.capture();
        private final List<CdObjectState> initSources=new ArrayList<>(), childPrefixes=new ArrayList<>();
        private List<CdPlatform.SolidCall> solidReceipts = List.of();
        private List<CdPlatform.PrimaryAngleCall> angleReceipts = List.of();
        private int childWhole=-1;
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.captureAct2Scroll();
        private final CdObjectDrawQueue.CameraWords camera;
        private CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private final boolean capacityDelete;
        private CdObjectState destroySource;
        private boolean directDeleteArmed;
        private boolean stopped;
        private boolean drawn;
        private boolean screenCleared;

        private PlatformInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            capacityDelete = entry.routine() == 0 && entry.subtype2() != 0
                    && pool.capture().slots().stream().noneMatch(image -> image.id() == 0);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession();
            generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.act2ForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
            preparePlatformInitPrefixes(this);
        }
    }

    /** Literal Init stores and first-free child prefixes only; no movement/Solid algorithm is replayed. */
    private void preparePlatformInitPrefixes(PlatformInvocation i) {
        if(i.entry.routine()!=0) return;
        var source=i.entry; i.initSources.add(source);
        source=source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,source.spriteFlags()|4).moveWord(CdObjectState.WordField.TILE,CdPlatform.TILE_WORD)
                .moveByte(CdObjectState.ByteField.PRIORITY,2).moveWord(CdObjectState.WordField.PLATFORM_ANCHOR_X,source.centreX())
                .moveWord(CdObjectState.WordField.PLATFORM_ANCHOR_Y,source.centreY()).moveWord(CdObjectState.WordField.PLATFORM_INITIAL_Y,source.centreY());
        i.initSources.add(source);
        source=source.moveLong(CdObjectState.LongField.MAPPINGS,CdR12aProfile.Asset.MAP_SPR_PLATFORM.nativeAddress());i.initSources.add(source);
        int subtype=source.subtype(); source=source.moveByte(CdObjectState.ByteField.MAP_FRAME,subtype&3);i.initSources.add(source);
        source=source.moveByte(CdObjectState.ByteField.WIDTH,assets.objects().view(CdR12aProfile.Asset.PLATFORM_WIDTHS).unsignedByte(subtype&3));i.initSources.add(source);
        source=source.moveByte(CdObjectState.ByteField.Y_RADIUS,8);i.initSources.add(source);
        source=source.moveByte(CdObjectState.ByteField.PLATFORM_RANGE,assets.objects().view(CdR12aProfile.Asset.PLATFORM_RANGES).unsignedByte((subtype>>>2)&3));i.initSources.add(source);
        if(source.subtype2()!=0) {
            var free=i.currentPool.slots().stream().filter(row->row.id()==0).findFirst();
            if(free.isPresent()) {
                var child=free.orElseThrow(); i.childWhole=child.identity().wholeIndex();i.childPrefixes.add(child);
                child=child.moveByte(CdObjectState.ByteField.ID,0x0A);i.childPrefixes.add(child);
                child=child.moveWord(CdObjectState.WordField.X,source.centreX());i.childPrefixes.add(child);
                child=child.moveWord(CdObjectState.WordField.Y,source.centreY());i.childPrefixes.add(child);
                child=child.addWord(CdObjectState.WordField.Y,-16);i.childPrefixes.add(child);
                child=child.moveByte(CdObjectState.ByteField.SPRING_PARENT_Y_OFFSET,0xF0);i.childPrefixes.add(child);
                child=child.moveWord(CdObjectState.WordField.SPRING_PARENT,source.identity().nativeAddress24());i.childPrefixes.add(child);
                child=child.moveByte(CdObjectState.ByteField.SUBTYPE,source.subtype2()&2);i.childPrefixes.add(child);
                child=child.moveByte(CdObjectState.ByteField.SPRING_PARENT_X_OFFSET,source.subtype2()&0xF8);i.childPrefixes.add(child);
                child=child.addWord(CdObjectState.WordField.X,source.subtype2()&0xF8);i.childPrefixes.add(child);
            }
        }
        i.initSources.add(source.addByte(CdObjectState.ByteField.ROUTINE,2));
    }
    private void validatePlatformPool(PlatformInvocation i) {
        var actual=i.pool.capture();var source=i.pool.state(i.whole);
        for(var before:i.currentPool.slots()) {
            int whole=before.identity().wholeIndex(); if(whole==i.whole) continue;
            var live=i.pool.state(whole);
            if(whole==i.childWhole && i.entry.routine()==0 && !i.drawn) {
                if(i.childPrefixes.stream().noneMatch(live::equals)) throw new IllegalStateException("Platform changed its actual found child outside native full64 prefixes");
            } else if(!before.equals(live)) throw new IllegalStateException("Platform changed another complete native physical image");
        }
        var expected=i.currentPool.slots().get(i.whole-32);
        if(i.stopped) {
            if(!actual.slots().equals(i.currentPool.slots())) throw new IllegalStateException("Stopped Platform advanced beyond its exact adopted native prefix");
            return;
        }
        if(platformDeletedSource(i,source)) return;
        if(i.entry.routine()==0) {
            if (i.drawn) {
                var completed = i.screenCleared ? expected.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,expected.spriteFlags()&0x7F) : expected;
                if (!source.equals(completed)) throw new IllegalStateException("Platform regressed its completed Init source");
                return;
            }
            boolean match=i.initSources.stream().anyMatch(row->source.equals(row) || i.screenCleared
                    && source.equals(row.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,row.spriteFlags()&0x7F)));
            if(!match) throw new IllegalStateException("Platform Init selected full64 differs from literal native stores");
            return;
        }
        // Actual movement stays in the cached body. Pin every native nonwrite between its declared helper cuts.
        boolean[] allowed=new boolean[64];int family=i.entry.subtype()>>>4;
        if(family<=9) {
            for(int at:new int[]{0x2A,0x2B,0x2C,0x2E,0x38,0x39,0x3A,0x3B}) allowed[at]=true;
            if(family==1 || family==2 || family==3 || family==7 || family==8 || family==9) {allowed[8]=allowed[9]=true;}
            allowed[12]=allowed[13]=true;
            if(family==7) {allowed[10]=allowed[11]=allowed[14]=allowed[15]=true;}
            if(family==5) {allowed[14]=allowed[15]=true;}
            if(family==1 || family==2 || family==3 || family==8 || family==9) {allowed[16]=allowed[17]=true;}
            if(family==5 || family==7) {allowed[18]=allowed[19]=true;}
        }
        if(i.screenCleared) expected=expected.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,expected.spriteFlags()&0x7F);
        byte[] before=expected.copyBytes(),after=source.copyBytes();
        for(int at=0;at<64;at++) if(!allowed[at] && before[at]!=after[at])
            throw new IllegalStateException("Platform native movement changed full64 nonwrite byte "+at);
    }

    private void runPlatform(int whole) {
        requireDoorIdle();
        requireLogMosquiIdle();
        requireAntonPataIdle();
        module.requireAct2LoadingVisit(this);
        if (activeTitle != null || titlePort || drawingTitle || activeDrain != null || !runningObjects || servicing || activePrimary != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeMonitor != null || activeTamabboh != null || tamabbohPort || drawingTamabboh || activeFloat != null || floatPort || drawingFloat || activeCheckpoint != null || checkpointPort || drawingCheckpoint || activeCollapse!=null || collapsePort || drawingCollapse || activeBoulder != null || boulderPort || drawingBoulder || reservedPort || drawingReserved || ringPort || drawingRing
                || springPort || drawingSpring || platformPort || drawingPlatform || monitorPort || drawingMonitor || queuePort
                || whole < 32 || whole >= 128 || placement.pool().state(whole).id() != CdPlatform.OBJECT_ID
                || values.phase() != BEGIN_LEVEL || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual live Act2 Platform invocation entry");
        var invocation = new PlatformInvocation(whole);
        activePlatform = invocation;
        try {
            var env = frame.environment(); var gates = env.movement().gates();
            var result = platform.run(whole, new CdPlatform.Globals(gates.zoneAct(), values.ring().timeStopTimer(),
                            invocation.camera.xWord(), invocation.camera.yWord(), gates.timeZone()),
                    new CdPlatform.Inputs(springPlayer(), new CdSolidObject.Gates(
                            env.globals().debugMode(), gates.miniSonic()), springGlobals()));
            verifyPlatformInvocation(invocation);
            // Completed observations authenticate receipts only; never replay helper work or effects.
            var solidObservations = result.effects().stream().filter(CdPlatform.Solid.class::isInstance)
                    .map(CdPlatform.Solid.class::cast).toList();
            var angleObservations = result.effects().stream().filter(CdPlatform.PrimaryAngleCommitted.class::isInstance)
                    .map(CdPlatform.PrimaryAngleCommitted.class::cast).toList();
            if (solidObservations.size() != invocation.solidReceipts.size() || angleObservations.size() != invocation.angleReceipts.size())
                throw new IllegalStateException("Platform omitted or duplicated a reached native publication");
            for (int at=0; at<solidObservations.size(); at++) {
                var observed=solidObservations.get(at); var receipt=invocation.solidReceipts.get(at);
                if (observed.site()!=receipt.site() || !(receipt.outcome() instanceof CdSolidObject.Completed completed)
                        || observed.result()!=completed.completedResult())
                    throw new IllegalStateException("Platform Solid observation differs from its accepted current occurrence");
            }
            for (int at=0; at<angleObservations.size(); at++)
                if (angleObservations.get(at).call()!=invocation.angleReceipts.get(at))
                    throw new IllegalStateException("Platform angle observation differs from its accepted current occurrence");
            if (!invocation.drawn || invocation.stopped || !result.state().equals(placement.pool().state(whole)))
                throw new IllegalStateException("Incomplete actual Platform return");
        } catch (RuntimeException | Error failure) {
            verifyPlatformFailure(failure); throw failure;
        } finally { activePlatform = null; }
    }

    private PlatformInvocation requirePlatformInvocation(CdObjectPool pool, int whole) {
        var invocation = activePlatform;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128 || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Platform callback at this physical pointer");
        var source = pool.state(whole);
        if (source.id() != CdPlatform.OBJECT_ID && !platformDeletedSource(invocation, source))
            throw new IllegalStateException("Platform callback lost its live or genuinely deleted native source");
        verifyPlatformInvocation(invocation);
        return invocation;
    }

    private void verifyPlatformInvocation(PlatformInvocation invocation) {
        requireAntonPataIdle();
        module.requireAct2LoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeTitle != null || titlePort || drawingTitle || activeDrain != null || activePlatform != invocation || activeMonitor != null || activeTamabboh != null || tamabbohPort || drawingTamabboh || activeFloat != null || floatPort || drawingFloat || activeCheckpoint != null || checkpointPort || drawingCheckpoint || activeCollapse!=null || collapsePort || drawingCollapse || activeBoulder != null || boulderPort || drawingBoulder || monitorPort || drawingMonitor || !runningObjects || servicing || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || invocation.body != platform || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.sourceStage != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared)
                || !placement.status().equals(invocation.status) || !presentation.sameAct2Scroll(invocation.scroll)
                || sounds != invocation.expectedSounds || values != invocation.expectedValues)
            throw new IllegalStateException("Platform changed another owner or retained a stale actual cut");
        validatePlatformSavedFlags(invocation, invocation.pool.state(invocation.whole));
        validatePlatformPool(invocation);
    }

    private void verifyPlatformFailure(Throwable original) {
        try { if (activePlatform != null) verifyPlatformInvocation(activePlatform); }
        catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    /** ID0 is admitted only after an actual private native deletion path, never by image equality alone. */
    private boolean platformDeletedSource(PlatformInvocation invocation, CdObjectState source) {
        return (invocation.capacityDelete || invocation.directDeleteArmed || invocation.destroySource != null)
                && source.identity().equals(invocation.entry.identity())
                && Arrays.equals(source.copyBytes(), new byte[CdObjectState.BYTE_SIZE]);
    }

    /** Destroy changes exactly one addressed membership bit; direct vertical DeleteObject changes none. */
    private void validatePlatformSavedFlags(PlatformInvocation invocation, CdObjectState source) {
        var current = placement.capture().savedFlags();
        if (sameBytes(current, invocation.savedFlags)) return;
        var destroyed = invocation.capacityDelete ? invocation.entry : invocation.destroySource;
        if (!platformDeletedSource(invocation, source) || destroyed == null || destroyed.savedFlagsId() == 0)
            throw new IllegalStateException("Platform changed its saved bank before a genuine Destroy");
        int index = 2 + 3 * destroyed.savedFlagsId() + invocation.shared.environment().movement().gates().timeZone();
        if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES)
            throw new IllegalStateException("Platform Destroy did not reach an in-bounds raw saved byte");
        byte[] expected = invocation.savedFlags.copy(); expected[index] &= 0x7F;
        if (!Arrays.equals(expected, current.copy()))
            throw new IllegalStateException("Platform changed saved bytes beyond its exact native BCLR7");
    }

    /** Native post-instruction cuts, including repeated Tops, timer1->0 and deleted5X anchor tails. */
    private void validatePlatformSite(PlatformInvocation invocation, CdPlatform.SolidCall call, CdObjectState source) {
        boolean deleted = call.site() == CdPlatform.Site.ANCHOR_GET_OFF && platformDeletedSource(invocation, source);
        if (!deleted && (source.id() != CdPlatform.OBJECT_ID || source.routine() != 2))
            throw new IllegalStateException("Platform helper requires live ID21/routine2 or its actual cleared anchor tail");
        int stop = values.ring().timeStopTimer();
        if (call.site() == CdPlatform.Site.TIME_STOP_TOP) {
            if (stop == 0 || deleted || call.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT)
                throw new IllegalStateException("Platform time-stop helper requires its nonzero WORD/direct Top");
            return;
        }
        if (stop != 0) throw new IllegalStateException("Platform ordinary helper cannot run under native time-stop");
        int family = source.subtype() >>> 4;
        int state = source.unsignedByte(CdObjectState.ByteField.PLATFORM_SUBSTATE);
        int timer = source.unsignedByte(CdObjectState.ByteField.PLATFORM_TIMER);
        int phase = source.unsignedByte(CdObjectState.ByteField.PLATFORM_PHASE);
        boolean top = true;
        boolean valid = switch (call.site()) {
            case SUBTYPE_0_TOP -> family == 0;
            case SUBTYPE_1_TOP -> family == 1;
            case SUBTYPE_2_TOP -> family == 2;
            case SUBTYPE_3_TOP -> family == 3;
            case SUBTYPE_4_TOP -> family == 4;
            case SUBTYPE_5_STATIONARY_TOP -> family == 5 && state == 0;
            case SUBTYPE_5_TIMER_TOP -> family == 5 && state != 0 && timer != 0xFF;
            case SUBTYPE_5_DROP_TOP -> family == 5 && state != 0 && timer == 0;
            case SUBTYPE_5_DELETE_GET_OFF -> {
                top = false;
                yield family == 5 && state != 0 && timer == 0
                        && ((invocation.camera.yWord() + 0x100) & 0xFFFF) < source.centreY();
            }
            case SUBTYPE_6_STATIONARY_1_TOP -> family == 6 && state == 0;
            case SUBTYPE_6_MOVE_DOWN_TOP -> family == 6 && state == 2 && phase >= 2 && phase <= 0x41;
            case SUBTYPE_6_STATIONARY_2_TOP -> family == 6 && state == 4;
            case SUBTYPE_7_STATIONARY_1_TOP -> family == 7 && state == 0;
            case SUBTYPE_7_TIMER_TOP -> family == 7 && state == 2 && timer != 0xFF;
            case SUBTYPE_7_RISING_TOP -> family == 7 && state == 2 && timer == 0;
            case SUBTYPE_7_STATIONARY_2_TOP -> family == 7 && state == 4;
            case SUBTYPE_8_STATIONARY_1_TOP -> family == 8 && state == 0;
            case SUBTYPE_8_TIMER_TOP -> family == 8 && state == 2 && timer != 0xFF;
            case SUBTYPE_8_MOVE_X_TOP -> family == 8 && state == 2 && timer == 0 && phase >= 1 && phase <= 0x40;
            case SUBTYPE_8_STATIONARY_2_TOP -> family == 8 && state == 4;
            case SUBTYPE_9_STATIONARY_1_TOP -> family == 9 && state == 0;
            case SUBTYPE_9_TIMER_TOP -> family == 9 && state == 2 && timer != 0xFF;
            case SUBTYPE_9_MOVE_X_TOP -> family == 9 && state == 2 && timer == 0 && phase >= 1 && phase <= 0x40;
            case SUBTYPE_9_STATIONARY_2_TOP -> family == 9 && state == 4;
            case ANCHOR_GET_OFF -> {
                top = false;
                int difference = ((source.word(CdObjectState.WordField.PLATFORM_ANCHOR_X) & 0xFF80)
                        - ((invocation.camera.xWord() - 0x80) & 0xFF80)) & 0xFFFF;
                yield difference > 0x280 && (deleted || family <= 9
                        && (family < 6 || state == 0 || state == 2 || state == 4));
            }
            case TIME_STOP_TOP -> false;
        };
        if (!valid || call.entry() != (top ? CdSolidObject.Entry.TOP_SOLID_OBJECT : CdSolidObject.Entry.GET_OFF_OBJECT))
            throw new IllegalStateException("Platform helper differs from its actual native instruction site/cut");
    }

    private List<CdObjectState> validatePlatformImages(PlatformInvocation invocation, CdPlatform.SolidCall call,
            CdObjectState source, CdObjectState object, List<CdObjectState> others, Set<CdObjectState.ByteField> writes) {
        if (!source.identity().equals(object.identity())
                || writes.stream().anyMatch(field -> field != CdObjectState.ByteField.SOLID_TYPE && field != CdObjectState.ByteField.FLAGS)
                || writes.contains(CdObjectState.ByteField.SOLID_TYPE) != (call.entry() == CdSolidObject.Entry.TOP_SOLID_OBJECT)
                || writes.contains(CdObjectState.ByteField.SOLID_TYPE) && object.unsignedByte(CdObjectState.ByteField.SOLID_TYPE) > 1
                || ((object.flags() ^ source.flags()) & ~8) != 0
                || call.entry() == CdSolidObject.Entry.GET_OFF_OBJECT && (object.flags() & ~source.flags()) != 0)
            throw new IllegalStateException("Platform Solid changed identity, canonical masks or unowned flag bits");
        byte[] before = source.copyBytes(), after = object.copyBytes();
        for (int at = 0; at < CdObjectState.BYTE_SIZE; at++) {
            if (at == 0x25 && writes.contains(CdObjectState.ByteField.SOLID_TYPE)
                    || at == 0x22 && writes.contains(CdObjectState.ByteField.FLAGS)) continue;
            if (before[at] != after[at]) throw new IllegalStateException("Platform Solid changed undeclared a0 byte " + at);
        }
        if (others.size() > 1 || call.entry() == CdSolidObject.Entry.GET_OFF_OBJECT && !others.isEmpty())
            throw new IllegalStateException("Platform helper returned extra dynamic releases");
        var images = new ArrayList<CdObjectState>(1 + others.size()); images.add(object);
        var seen = new HashSet<Integer>(); seen.add(invocation.whole);
        for (var other : others) {
            int whole = other.identity().wholeIndex();
            if (whole < 32 || whole >= 128 || !seen.add(whole) || whole != call.expectedPlayer().standObject())
                throw new IllegalStateException("Platform release lacks its actual distinct dynamic stand identity");
            var original = call.actualCut().slots().get(whole - 32);
            if (!original.identity().equals(other.identity())
                    || !original.moveByte(CdObjectState.ByteField.FLAGS, original.flags() & ~8).equals(other))
                throw new IllegalStateException("Platform release changed bytes beyond native BCLR3");
            images.add(other);
        }
        return List.copyOf(images);
    }

    /** All pool/Frame/ledger values and expected full pairs are ready before the first owner assignment. */
    private void commitPlatformSolid(CdPlatform.SolidCall call) {
        Objects.requireNonNull(call);
        var invocation = requirePlatformInvocation(placement.pool(), activePlatform == null ? -1 : activePlatform.whole);
        if (!platform.isCurrentSolidCall(call)) throw new IllegalStateException("Platform Solid lacks its actual pending producer call");
        if (platformPort || drawingPlatform || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped)
            throw new IllegalStateException("Platform Solid lacks its current actual helper cut");
        for (var prior : invocation.solidReceipts)
            if (prior==call || prior.outcome()==call.outcome()
                    || prior.outcome() instanceof CdSolidObject.Completed before && call.outcome() instanceof CdSolidObject.Completed next
                    && before.completedResult()==next.completedResult())
                throw new IllegalStateException("Platform reused one native Solid callback occurrence");
        var pool = invocation.pool; var cut = call.actualCut();
        pool.validateRestore(cut);
        if (!pool.capture().slots().equals(cut.slots())
                || !call.expectedPlayer().equals(springPlayer()) || !call.expectedGlobals().equals(springGlobals()))
            throw new IllegalStateException("Platform Solid differs from its actual full96/player/global cut");
        var source = cut.slots().get(invocation.whole - 32);
        validatePlatformSite(invocation, call, source);
        CdObjectState object; List<CdObjectState> others; Set<CdObjectState.ByteField> writes;
        List<CdPlayerTouchResponse.FmSound> requests;
        CdPlayerFrameRuntime.ObjectSolidPublication readyFrame;
        boolean stopped;
        if (call.outcome() instanceof CdSolidObject.Completed done) {
            var result = done.completedResult();
            if (result.entry() != call.entry()) throw new IllegalStateException("Platform completed entry mismatch");
            object = result.object(); others = result.otherSlots(); writes = result.objectWrites(); requests = result.fmRequests();
            readyFrame = frame.prepareObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result);
            stopped = false;
        } else {
            var prefix = ((CdSolidObject.NativeStopped) call.outcome()).prefix();
            if (prefix.entry() != call.entry() || call.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT
                    || !prefix.fmRequests().isEmpty() || !prefix.otherSlots().isEmpty())
                throw new IllegalStateException("Platform stopped Top acquired a completed FM/release or wrong entry");
            object = prefix.object(); others = prefix.otherSlots(); writes = prefix.objectWrites(); requests = prefix.fmRequests();
            readyFrame = frame.prepareObjectSolidPrefix(call.expectedPlayer(), call.expectedGlobals(), prefix);
            stopped = true;
        }
        var images = validatePlatformImages(invocation, call, source, object, others, writes);
        var readyPool = CdObjectPool.overlay(cut, images); pool.validateRestore(readyPool);
        var readyActor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), readyFrame.finalState(), invocation.actor.display());
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), readyFrame.finalEnvironment(),
                invocation.shared.artState(), invocation.shared.paused(), invocation.shared.vIntClock());
        var ledger = new ArrayList<>(sounds);
        for (var fm : requests) ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
        var readySounds = List.copyOf(ledger);
        var readyValues = preparedMonitorValues(PLATFORM_SOLID);
        var receipts = new ArrayList<>(invocation.solidReceipts); receipts.add(call);
        var readyReceipts = List.copyOf(receipts);
        platformPort = true;
        try {
            verifyPlatformInvocation(invocation);
            if (!pool.capture().slots().equals(cut.slots())) throw new IllegalStateException("Stale Platform publication pool");
            frame.validatePreparedObjectSolidPublication(readyFrame);
            pool.restore(readyPool);
            frame.applyPreparedObjectSolidPublication(readyFrame);
            sounds = readySounds; values = readyValues; invocation.expectedValues = readyValues;
            invocation.actor = readyActor; invocation.shared = readyShared; invocation.expectedSounds = readySounds;
            invocation.stopped = stopped; invocation.currentPool=readyPool; invocation.solidReceipts=readyReceipts;
            if (!stopped && call.site() == CdPlatform.Site.SUBTYPE_5_DELETE_GET_OFF) invocation.directDeleteArmed = true;
            if (!stopped && call.site() == CdPlatform.Site.ANCHOR_GET_OFF) invocation.destroySource = source;
            verifyPlatformInvocation(invocation);
            if (!pool.capture().slots().equals(readyPool.slots()))
                throw new IllegalStateException("Platform Solid output images were not adopted exactly");
        } catch (RuntimeException | Error failure) {
            verifyPlatformFailure(failure); throw failure;
        } finally { platformPort = false; }
    }

    /** Each reached raw ceiling byte, with no preclear and no change to any physical object image. */
    private void commitPlatformPrimaryAngle(CdPlatform.PrimaryAngleCall call) {
        Objects.requireNonNull(call);
        var invocation=requirePlatformInvocation(placement.pool(),call.expectedSource().identity().wholeIndex());
        if (!platform.isCurrentPrimaryAngleCall(call)) throw new IllegalStateException("Platform angle lacks its actual pending producer call");
        if(platformPort || drawingPlatform || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped)
            throw new IllegalStateException("Platform angle is outside its actual rising callback");
        if (invocation.angleReceipts.stream().anyMatch(prior->prior==call))
            throw new IllegalStateException("Platform reused one native primary-angle callback occurrence");
        var pool=invocation.pool;var cut=call.actualCut();var source=pool.state(invocation.whole);pool.validateRestore(cut);
        if(source.id()!=CdPlatform.OBJECT_ID || source.routine()!=2 || source.subtype()>>>4!=7
                || source.unsignedByte(CdObjectState.ByteField.PLATFORM_SUBSTATE)!=2
                || source.unsignedByte(CdObjectState.ByteField.PLATFORM_TIMER)!=0 || values.ring().timeStopTimer()!=0
                || !source.equals(call.expectedSource()) || !pool.capture().slots().equals(cut.slots())
                || !cut.slots().get(invocation.whole-32).equals(source)
                || frame.environment().movement().globals().primaryAngle()!=call.expectedPrimaryAngle() || (call.value()&~255)!=0)
            throw new IllegalStateException("Platform angle changed its genuine full96/source/Frame preimage");
        var before=placement.capture();
        var readyShared=new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(),
                monitorAngleEnvironment(invocation.shared.environment(),call.value()),invocation.shared.artState(),
                invocation.shared.paused(),invocation.shared.vIntClock());
        var readyValues=preparedMonitorValues(PLATFORM_PRIMARY_ANGLE);
        var receipts=new ArrayList<>(invocation.angleReceipts);receipts.add(call);var readyReceipts=List.copyOf(receipts);
        platformPort=true;
        try {
            verifyPlatformCallback(invocation,before,null);
            frame.applyObjectPrimaryAngle(call.value()); values=readyValues; invocation.expectedValues=readyValues;
            invocation.shared=readyShared; invocation.currentPool=before.pool();invocation.angleReceipts=readyReceipts;
            verifyPlatformCallback(invocation,before,null);
        } catch(RuntimeException|Error failure) { verifyPlatformCallbackFailure(invocation,before,null,failure);throw failure; }
        finally { platformPort=false; }
    }

    private void verifyPlatformCallback(PlatformInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                        CdObjectState cleared) {
        verifyPlatformInvocation(invocation);
        var now = placement.capture(); var expected = new ArrayList<>(before.pool().slots());
        if (cleared != null) expected.set(invocation.whole - 32, cleared);
        if (!now.status().equals(before.status()) || !now.pool().slots().equals(expected)
                || !sameBytes(now.savedFlags(), before.savedFlags()))
            throw new IllegalStateException("Platform callback changed pool/bank outside the actual bit7 clear");
    }

    private void verifyPlatformCallbackFailure(PlatformInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                               CdObjectState cleared, Throwable original) {
        try { verifyPlatformCallback(invocation, before, cleared); }
        catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private void drawPlatform(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requirePlatformInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (platformPort || drawingPlatform || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped)
            throw new IllegalStateException("Platform DrawObject is not its actual once-only normal return");
        var before = placement.capture(); var current = pool.state(invocation.whole);
        if (invocation.entry.routine()==0 && !invocation.capacityDelete) {
            if (!current.equals(invocation.initSources.getLast()))
                throw new IllegalStateException("Platform Draw regressed its completed Init parent");
            if (invocation.childWhole>=0 && !pool.state(invocation.childWhole).equals(invocation.childPrefixes.getLast()))
                throw new IllegalStateException("Platform Draw regressed its completed first-free child");
        }
        if (invocation.entry.routine()==2 && invocation.solidReceipts.isEmpty())
            throw new IllegalStateException("Platform Draw skipped its reached native Solid publication");
        var cleared = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        invocation.currentPool=before.pool();
        invocation.drawn = true; drawingPlatform = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(),
                    current.unsignedByte(CdObjectState.ByteField.Y_RADIUS), current.unsignedByte(CdObjectState.ByteField.WIDTH),
                    current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            if (!invocation.screenCleared) throw new IllegalStateException("Platform DrawObject did not clear bit7");
            verifyPlatformCallback(invocation, before, cleared);
        } catch (RuntimeException | Error failure) {
            verifyPlatformCallbackFailure(invocation, before, invocation.screenCleared ? cleared : null, failure); throw failure;
        } finally { drawingPlatform = false; }
    }

    private CdObjectDrawQueue.LiveDrawSlot readPlatformDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requirePlatformInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingPlatform || platformPort || queuePort) throw new IllegalStateException("No actual Platform queue read");
        var before = placement.capture(); queuePort = true;
        try {
            var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(), current.centreX(), current.centreY(),
                    current.word(CdObjectState.WordField.X_FRACTION), current.longBits(CdObjectState.LongField.MAPPINGS),
                    current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifyPlatformCallback(invocation, before, null); return live;
        } catch (RuntimeException | Error failure) {
            verifyPlatformCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { queuePort = false; }
    }

    private void writePlatformOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requirePlatformInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingPlatform || platformPort || queuePort || onScreen || invocation.screenCleared)
            throw new IllegalStateException("Only actual Platform DrawObject may clear bit7 once");
        var before = placement.capture(); var current = invocation.pool.state(invocation.whole);
        var next = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        queuePort = true;
        try {
            invocation.pool.write(next); invocation.screenCleared = true;
            verifyPlatformCallback(invocation, before, next);
        } catch (RuntimeException | Error failure) {
            verifyPlatformCallbackFailure(invocation, before, invocation.screenCleared ? next : null, failure); throw failure;
        } finally { queuePort = false; }
    }




    /** One real cached ID3F call. This scope validates owners; Anton retains its native algorithm. */
    private final class AntonInvocation {
        private final CdAnton body = anton;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR12aTerrainAssets sourceAssets = assets;
        private final Object stageSource = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final CdObjectPool.Snapshot initialPool = pool.capture();
        private final CdPlayerBody.State player = frame.state();
        private final CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private final Values base = values;
        private Values expectedValues = base;
        private final CdObjectPlacementRuntime.Status status = placement.status();
        private final CdAssetBytes reserved = values.hudObjects();
        private final CdAssetBytes savedFlags = placement.capture().savedFlags();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.captureAct2Scroll();
        private final List<SonicCdGameModule.PastSound> ledger = sounds;
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final CdObjectDrawQueue.CameraWords camera;
        private final CdAnton.Globals globals;
        private CdObjectState drawSource;
        private List<CdAnton.PrimaryAngleCall> angleReceipts=List.of();
        private boolean angleSeen, drawn, screenCleared;
        private AntonInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.act2ForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
            var gates = frame.environment().movement().gates();
            globals = new CdAnton.Globals(values.globals().retainedDev().goodFuture(), gates.timeZone(), gates.timeWarpDirection(),
                    camera.xWord(), frame.environment().movement().globals().primaryAngle());
        }
    }

    private void runAnton(int whole) {
        requireDoorIdle();
        requireLogMosquiIdle();
        requireBoulderPlatformIdle();
        if(activeTitle!=null || titlePort || drawingTitle) throw new IllegalStateException("Title owns this Act2 source cut");
        module.requireAct2LoadingVisit(this);
        if (!runningObjects || servicing || activePrimary != null || activeDrain != null || activeReserved != null
                || activeRing != null || activeSpring != null || activePlatform != null || activePata != null
                || activeTamabboh != null || tamabbohPort || drawingTamabboh || activeFloat != null || floatPort || drawingFloat || activeCheckpoint != null || checkpointPort || drawingCheckpoint || activeCollapse!=null || collapsePort || drawingCollapse || activeBoulder != null || activeAnton != null
                || activeMonitor != null || reservedPort || drawingReserved || ringPort || drawingRing
                || springPort || drawingSpring || platformPort || drawingPlatform || drawingPata
                || boulderPort || drawingBoulder
                || monitorPort || drawingMonitor || antonPort || drawingAnton || queuePort
                || whole < 32 || whole >= 128 || placement.pool().state(whole).id() != CdAnton.OBJECT_ID
                || values.phase() != BEGIN_LEVEL || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Act2 Anton dispatch");
        var invocation = new AntonInvocation(whole); activeAnton = invocation;
        try {
            var result = anton.run(whole, invocation.globals);
            verifyAntonInvocation(invocation); requireAntonOtherImages(invocation); validateAntonTail(invocation,false);
            var observed=result.effects().stream().filter(e->e instanceof CdAnton.PrimaryAngleCommitted)
                    .map(e->((CdAnton.PrimaryAngleCommitted)e).call()).toList();
            if(!observed.equals(invocation.angleReceipts))
                throw new IllegalStateException("Anton angle observations differ from actual once-only publication receipts");
            boolean conversion = invocation.globals.goodFuture() != 0
                    && (invocation.globals.timeZone() != 1 || invocation.entry.subtype() != 0);
            if (!result.state().equals(invocation.pool.state(whole))
                    || result.primaryAngle() != frame.environment().movement().globals().primaryAngle()
                    || (conversion ? invocation.drawn : !invocation.angleSeen || !invocation.drawn || !invocation.screenCleared))
                throw new IllegalStateException("Anton returned without its actual angle/Draw/source publications");
        } catch (RuntimeException | Error failure) { verifyAntonFailure(invocation, failure); throw failure; }
        finally { activeAnton = null; }
    }
    private AntonInvocation requireAntonInvocation(CdObjectPool pool, int whole) {
        var invocation = activeAnton;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128)
            throw new IllegalStateException("No actual cached Anton callback at this physical pointer");
        verifyAntonInvocation(invocation); return invocation;
    }
    private void verifyAntonInvocation(AntonInvocation invocation) {
        module.requireAct2LoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeTitle != null || titlePort || drawingTitle || activeAnton != invocation || !runningObjects || servicing
                || activePrimary != null || activeDrain != null || activeReserved != null || activeRing != null || activeSpring != null
                || activePlatform != null || activePata != null
                || activeTamabboh != null || tamabbohPort || drawingTamabboh || activeFloat != null || floatPort || drawingFloat || activeCheckpoint != null || checkpointPort || drawingCheckpoint || activeCollapse!=null || collapsePort || drawingCollapse || activeBoulder != null || activeMonitor != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform
                || drawingPata
                || boulderPort || drawingBoulder || monitorPort || drawingMonitor
                || invocation.body != anton || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.stageSource != frame.stageSource() || invocation.pool != placement.pool() || invocation.queue != drawQueue
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || invocation.world != mode.getWorldSession()
                || invocation.generation != mode.getLevelManager().getCompletedProductionLoadGeneration()
                || invocation.expectedValues != values || values.phase() != BEGIN_LEVEL || invocation.ledger != sounds
                || !placement.status().equals(invocation.status) || !sameBytes(values.hudObjects(),invocation.reserved)
                || !presentation.sameAct2Scroll(invocation.scroll)
                || frame.state() != invocation.player || !frame.captureActor().equals(invocation.actor)
                || !frame.captureShared().equals(invocation.shared))
            throw new IllegalStateException("Anton changed another owner or retained a stale actual World/Frame cut");
    }
    private void requireAntonOtherImages(AntonInvocation invocation) {
        for (var source : invocation.initialPool.slots()) if (source.identity().wholeIndex() != invocation.whole
                && !source.equals(invocation.pool.state(source.identity().wholeIndex())))
            throw new IllegalStateException("Anton changed an unrelated physical image");
    }
    private void verifyAntonReadOnly(AntonInvocation invocation, CdObjectPlacementRuntime.Snapshot before) {
        verifyAntonInvocation(invocation);
        var now = placement.capture();
        if (!now.pool().slots().equals(before.pool().slots()) || !now.status().equals(before.status())
                || !sameBytes(now.savedFlags(), before.savedFlags()))
            throw new IllegalStateException("Anton scalar callback changed actual full96/source/saved state");
    }
    private void verifyAntonFailure(AntonInvocation invocation, Throwable original) {
        try { verifyAntonInvocation(invocation); requireAntonOtherImages(invocation); validateAntonTail(invocation,true); }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    /** Full source/bank ownership, including exact native conversion/despawn prefixes; no effects execute here. */
    private void validateAntonTail(AntonInvocation i,boolean failure) {
        var actual=i.pool.state(i.whole);var bank=placement.capture().savedFlags();
        boolean conversion=i.globals.goodFuture()!=0 && (i.globals.timeZone()!=1 || i.entry.subtype()!=0);
        if(conversion) {
            byte[] deleted=new byte[CdObjectState.BYTE_SIZE],x=deleted.clone();
            x[8]=(byte)(i.entry.centreX()>>>8);x[9]=(byte)i.entry.centreX();
            byte[] xy=x.clone();xy[12]=(byte)(i.entry.centreY()>>>8);xy[13]=(byte)i.entry.centreY();
            byte[] converted=xy.clone();converted[0]=0x18;
            boolean prefix=failure && List.of(i.entry.copyBytes(),deleted,x,xy).stream().anyMatch(b->Arrays.equals(b,actual.copyBytes()));
            if(!sameBytes(bank,i.savedFlags) || i.drawn || (!Arrays.equals(actual.copyBytes(),converted) && !prefix))
                throw new IllegalStateException("Anton conversion exceeded actual Delete64/X/Y/ID18 prefix");
            return;
        }
        if(!i.drawn) {
            if(!sameBytes(bank,i.savedFlags))throw new IllegalStateException("Anton pre-Draw changed saved bank");
            validateAntonBodyNonwrites(i,actual);return;
        }
        var live=i.drawSource;
        boolean outside=(live.spriteFlags()&0x80)==0 && (((live.word(CdObjectState.WordField.ANTON_ANCHOR_X)&0xFF80)
                -((i.camera.xWord()-0x80)&0xFF80))&0xFFFF)>0x280;
        boolean delete=outside || (i.globals.timeZone()&0x80)!=0;
        byte[] ready=i.savedFlags.copy();boolean owned=true;
        if(delete && live.savedFlagsId()!=0) {
            int period=i.globals.timeZone()&0x7F;
            if((i.globals.timeZone()&0x80)!=0) {period=(short)((period-(byte)i.globals.timeWarpDirection())&65535);period=period<0?0:Math.min(period,2);}
            int at=2+3*live.savedFlagsId()+period;owned=at>=0 && at<ready.length;
            if(owned)ready[at]&=0x7F;
        }
        byte[] expected=delete && owned?new byte[CdObjectState.BYTE_SIZE]:live.copyBytes();
        if(failure && actual.equals(live) && sameBytes(bank,i.savedFlags))return;
        if(failure && owned && actual.equals(live) && Arrays.equals(bank.copy(),ready))return;
        if(!owned && !failure || !Arrays.equals(actual.copyBytes(),expected) || !Arrays.equals(bank.copy(),ready))
            throw new IllegalStateException("Anton return/failure differs from actual full64/saved despawn prefix");
    }
    private void validateAntonBodyNonwrites(AntonInvocation i,CdObjectState source) {
        byte[] before=i.entry.copyBytes(),after=source.copyBytes();
        for(int at=0;at<64;at++) {
            int mask=(at>=2 && at<=0xF || at>=0x16 && at<=0x1E || at==0x20 || at==0x24 || at>=0x2A && at<=0x2F)?255
                    :at==1?0x87:at==0x22?1:0;
            if(((before[at]^after[at])&~mask&255)!=0)
                throw new IllegalStateException("Anton changed unowned full-source byte "+at);
        }
    }

    /** Every genuine raw floor store, including same-value pre-zero, goes through the existing Frame owner once. */
    private void commitAntonPrimaryAngle(CdAnton.PrimaryAngleCall call) {
        Objects.requireNonNull(call);
        var invocation = requireAntonInvocation(placement.pool(), call.expectedSource().identity().wholeIndex());
        if (!anton.isCurrentPrimaryAngleCall(call)) throw new IllegalStateException("Anton angle lacks its actual pending producer call");
        if (antonPort || drawingAnton || queuePort || invocation.drawn || invocation.angleReceipts.contains(call)
                || !invocation.angleSeen && call.value()!=0)
            throw new IllegalStateException("Anton angle is outside its actual pre-Draw floor call");
        var pool = invocation.pool; var cut = call.callCut(); pool.validateRestore(cut);
        var source = pool.state(invocation.whole); requireAntonOtherImages(invocation);
        validateAntonBodyNonwrites(invocation,source);
        if (source.id() != CdAnton.OBJECT_ID || source.routine() != 0 && source.routine() != 2 && source.routine() != 4
                || !source.equals(call.expectedSource()) || !cut.slots().get(invocation.whole - 32).equals(source)
                || !pool.capture().slots().equals(cut.slots())
                || frame.environment().movement().globals().primaryAngle() != call.expectedPrimaryAngle())
            throw new IllegalStateException("Anton angle differs from its actual full96/source/current Frame byte");
        var before = placement.capture();
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(),
                monitorAngleEnvironment(invocation.shared.environment(), call.value()), invocation.shared.artState(),
                invocation.shared.paused(), invocation.shared.vIntClock());
        var readyValues=preparedMonitorValues(ANTON_PRIMARY_ANGLE);
        var receipts=new ArrayList<>(invocation.angleReceipts);receipts.add(call);var readyReceipts=List.copyOf(receipts);
        antonPort = true;
        try {
            verifyAntonReadOnly(invocation, before);
            frame.applyObjectPrimaryAngle(call.value()); values=readyValues; invocation.expectedValues=readyValues;
            invocation.shared = readyShared; invocation.angleSeen = true; invocation.angleReceipts=readyReceipts;
            verifyAntonReadOnly(invocation, before);
        } catch (RuntimeException | Error original) {
            try { verifyAntonReadOnly(invocation, before); }
            catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
            throw original;
        } finally { antonPort = false; }
    }

    private void drawAnton(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireAntonInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (antonPort || drawingAnton || queuePort || invocation.drawn || !invocation.angleSeen
                || !identity.equals(invocation.entry.identity()) || pool.state(invocation.whole).id() != CdAnton.OBJECT_ID)
            throw new IllegalStateException("Anton DrawObject lacks its genuine live physical source");
        requireAntonOtherImages(invocation);
        validateAntonBodyNonwrites(invocation,pool.state(invocation.whole));
        if(!sameBytes(placement.capture().savedFlags(),invocation.savedFlags))
            throw new IllegalStateException("Anton reached Draw after an unowned saved-bank change");
        invocation.drawSource = pool.state(invocation.whole); invocation.drawn = true; drawingAnton = true;
        var current = invocation.drawSource;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(), current.unsignedByte(CdObjectState.ByteField.Y_RADIUS),
                    current.unsignedByte(CdObjectState.ByteField.WIDTH), current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            verifyAntonInvocation(invocation);
            if (!invocation.screenCleared) throw new IllegalStateException("Anton skipped its actual DrawObject BCLR7");
        } catch (RuntimeException | Error failure) { verifyAntonFailure(invocation, failure); throw failure; }
        finally { drawingAnton = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readAntonDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireAntonInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingAnton || antonPort || queuePort || !invocation.drawn)
            throw new IllegalStateException("No actual Anton collecting queue read");
        var before = placement.capture(); queuePort = true;
        try {
            var source = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(source.id(), source.spriteFlags(), source.centreX(), source.centreY(),
                    source.word(CdObjectState.WordField.X_FRACTION), source.longBits(CdObjectState.LongField.MAPPINGS),
                    source.unsignedByte(CdObjectState.ByteField.MAP_FRAME), source.word(CdObjectState.WordField.TILE));
            verifyAntonReadOnly(invocation, before); return live;
        } catch (RuntimeException | Error failure) { verifyAntonFailure(invocation, failure); throw failure; }
        finally { queuePort = false; }
    }
    private void writeAntonOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireAntonInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingAnton || antonPort || queuePort || onScreen || invocation.screenCleared || !invocation.drawn)
            throw new IllegalStateException("Only actual Anton DrawObject may clear bit7 once");
        var before = placement.capture(); var source = invocation.pool.state(invocation.whole);
        if (!source.equals(invocation.drawSource)) throw new IllegalStateException("Anton changed its Draw source before BCLR7");
        var next = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() & 0x7F);
        var ready = CdObjectPool.overlay(before.pool(), List.of(next)); invocation.pool.validateRestore(ready);
        queuePort = true;
        try {
            verifyAntonReadOnly(invocation, before);
            invocation.pool.write(next); invocation.screenCleared = true; invocation.drawSource=next;
            verifyAntonInvocation(invocation);
            if (!invocation.pool.capture().slots().equals(ready.slots())
                    || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Anton Draw changed more than its actual source sprite byte");
        } catch (RuntimeException | Error failure) { verifyAntonFailure(invocation, failure); throw failure; }
        finally { queuePort = false; }
    }


    private enum PataSite { DRAW, RETURN, FAILURE }
    private final class PataInvocation {
        private final CdPataBata body = pataBata;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR12aTerrainAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final Values base = values;
        private final CdObjectPlacementRuntime.Snapshot before = placement.capture();
        private final List<SonicCdGameModule.PastSound> ledger = sounds;
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.captureAct2Scroll();
        private final CdObjectDrawQueue.CameraWords camera;
        private final CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private final CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private final CdPataBata.Globals inputs;
        private CdObjectState drawSource;
        private boolean drawn, screenCleared, tailArmed, bodyReady;
        private CdPataBata.UnsupportedBoundary expectedStop;
        private PataInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.act2ForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
            var gates = frame.environment().movement().gates();
            inputs = new CdPataBata.Globals(values.globals().retainedDev().goodFuture(),
                    gates.timeZone(), gates.timeWarpDirection(), camera.xWord());
        }
    }

    private void runPataBata(int whole) {
        requireDoorIdle();
        requireLogMosquiIdle();
        requireBoulderPlatformIdle();
        if(activeTitle!=null || titlePort || drawingTitle) throw new IllegalStateException("Title owns this Act2 source cut");
        module.requireAct2LoadingVisit(this);
        if (!runningObjects || servicing || activePrimary != null || activeDrain != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeMonitor != null || activePata != null || activeTamabboh != null || tamabbohPort || drawingTamabboh || activeFloat != null || floatPort || drawingFloat || activeCheckpoint != null || checkpointPort || drawingCheckpoint || activeCollapse!=null || collapsePort || drawingCollapse || activeBoulder != null || activeAnton != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform
                || monitorPort || drawingMonitor || drawingPata || boulderPort || drawingBoulder || antonPort || drawingAnton || queuePort || whole < 32 || whole >= 128
                || placement.pool().state(whole).id() != CdPataBata.OBJECT_ID || values.phase() != BEGIN_LEVEL
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Act2 Pata-Bata dispatch");
        var invocation = new PataInvocation(whole); activePata = invocation;
        try {
            var result = pataBata.run(whole, invocation.inputs);
            verifyPataInvocation(invocation); validatePataSource(invocation, PataSite.RETURN);
            verifyPataResult(invocation, result);
        } catch (RuntimeException | Error original) {
            verifyPataFailure(invocation, original); throw original;
        } finally { activePata = null; }
    }

    private PataInvocation requirePataInvocation(CdObjectPool pool, int whole) {
        var invocation = activePata;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128 || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Pata-Bata callback at this physical pointer");
        verifyPataInvocation(invocation); return invocation;
    }
    private void verifyPataInvocation(PataInvocation invocation) {
        module.requireAct2LoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeAnton!=null || antonPort || drawingAnton || activeTamabboh != null || tamabbohPort || drawingTamabboh || activeFloat != null || floatPort || drawingFloat || activeCheckpoint != null || checkpointPort || drawingCheckpoint || activeCollapse!=null || collapsePort || drawingCollapse || activeBoulder!=null || boulderPort || drawingBoulder
                || activeTitle != null || titlePort || drawingTitle || invocation == null || activePata != invocation || !runningObjects || servicing
                || activePrimary != null || activeDrain != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeMonitor != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform
                || monitorPort || drawingMonitor || invocation.body != pataBata || invocation.sourceFrame != frame
                || invocation.sourceAssets != assets || invocation.sourceStage != frame.stageSource()
                || invocation.pool != placement.pool() || invocation.queue != drawQueue || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || values != invocation.base || values.phase() != BEGIN_LEVEL || sounds != invocation.ledger
                || !placement.status().equals(invocation.before.status()) || !presentation.sameAct2Scroll(invocation.scroll)
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared))
            throw new IllegalStateException("Pata-Bata changed another owner or retained a stale actual World/Frame cut");
        invocation.pool.validateRestore(invocation.before.pool());
    }
    private void verifyPataFailure(PataInvocation invocation, Throwable original) {
        try {
            verifyPataInvocation(invocation); validatePataSource(invocation, PataSite.FAILURE);
            if (original instanceof CdPataBata.UnsupportedPataBata stopped) {
                var effects = new ArrayList<CdPataBata.Effect>();
                effects.add(new CdPataBata.CallReturned(CdPataBata.NativeCall.DESTROY_ON_GOOD_FUTURE));
                var boundary = stopped.boundary();
                if (boundary == CdPataBata.UnsupportedBoundary.SET_OBJECT_TILE_ID
                        || boundary == CdPataBata.UnsupportedBoundary.ANIMATE_OBJECT) {
                    var prefixes = pataBodyPrefixes(invocation);
                    if (invocation.drawn || invocation.expectedStop != boundary
                            || !prefixes.getLast().equals(invocation.pool.state(invocation.whole)))
                        throw new IllegalStateException("Pata-Bata read fault lacks its exact committed native prefix");
                } else {
                    if (!invocation.bodyReady || !invocation.drawn)
                        throw new IllegalStateException("Pata-Bata callback/tail fault preceded its actual body");
                    effects.add(new CdPataBata.CallReturned(CdPataBata.NativeCall.ANIMATE_OBJECT));
                    if (boundary == CdPataBata.UnsupportedBoundary.CHECK_OBJ_DESPAWN2) {
                        if (!invocation.tailArmed) throw new IllegalStateException("Pata-Bata tail fault preceded returned DrawObject");
                        effects.add(new CdPataBata.CallReturned(CdPataBata.NativeCall.DRAW_OBJECT));
                    }
                }
                if (stopped.wholeIndex() != invocation.whole || !stopped.completedEffects().equals(effects))
                    throw new IllegalStateException("Pata-Bata fault metadata changed its physical source/completed calls");
            }
        } catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    /**
     * Literal observations of the cached body's committed source images, N ObjPataBata:11-77.
     * Never invokes run, AnimateObject, CalcSine or a gameplay publication. Failed selected
     * animation reads retain the caller-owned reset/decrement/speed/reached-index stores.
     */
    private List<CdObjectState> pataBodyPrefixes(PataInvocation invocation) {
        var prefixes = new ArrayList<CdObjectState>(); var source = invocation.entry;
        prefixes.add(source); invocation.bodyReady = false; invocation.expectedStop = null;
        if (source.routine() == 0) {
            source = source.addByte(CdObjectState.ByteField.ROUTINE, 2)
                    .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() | 4)
                    .moveByte(CdObjectState.ByteField.PRIORITY, 3).moveByte(CdObjectState.ByteField.COLLISION_TYPE, 0x2A)
                    .moveByte(CdObjectState.ByteField.X_RADIUS, 0x10).moveByte(CdObjectState.ByteField.WIDTH, 0x10)
                    .moveByte(CdObjectState.ByteField.Y_RADIUS, 0x10)
                    .moveWord(CdObjectState.WordField.PATA_BATA_ANCHOR_X, source.centreX())
                    .moveLong(CdObjectState.LongField.PATA_BATA_ANCHOR_Y, (source.centreY() << 16) | 0x8000);
            prefixes.add(source);
            var tiles = view(CdR12aProfile.Asset.OBJECT_TILE_IDS).bytes();
            int selected = (short) tiles.unsignedWord(2) + (source.subtype2() << 1);
            if ((selected & 1) != 0 || selected < 0 || selected > tiles.length() - 2) {
                invocation.expectedStop = CdPataBata.UnsupportedBoundary.SET_OBJECT_TILE_ID; return prefixes;
            }
            source = source.moveWord(CdObjectState.WordField.TILE, tiles.unsignedWord(selected)); prefixes.add(source);
            boolean damaged = source.subtype() != 0;
            source = source.moveLong(CdObjectState.LongField.PATA_BATA_X_DELTA, damaged ? -0x4000 : -0x8000)
                    .moveWord(CdObjectState.WordField.PATA_BATA_PHASE_SPEED, damaged ? -0x100 : -0x200)
                    .moveWord(CdObjectState.WordField.PATA_BATA_SHIFT, damaged ? 4 : 3)
                    .moveByte(CdObjectState.ByteField.ANIMATION, damaged ? 1 : 0)
                    .moveLong(CdObjectState.LongField.MAPPINGS, view(damaged
                            ? CdR12aProfile.Asset.MAP_SPR_PATA_BATA2 : CdR12aProfile.Asset.MAP_SPR_PATA_BATA1).range().nativeAddress());
            prefixes.add(source);
        }
        // Every nonzero routine enters Main, including odd/FF. No time-stop, player or proximity gate.
        int delta = source.longBits(CdObjectState.LongField.PATA_BATA_X_DELTA);
        source = source.addLong(CdObjectState.LongField.X_POSITION, delta); prefixes.add(source);
        int distance = (source.centreX() - source.word(CdObjectState.WordField.PATA_BATA_ANCHOR_X)) & 0xFFFF;
        if ((short) distance < 0) distance = (-distance) & 0xFFFF;
        if ((short) distance >= 0x80) {
            delta = -delta;
            source = source.moveLong(CdObjectState.LongField.PATA_BATA_X_DELTA, delta); prefixes.add(source);
            source = source.addLong(CdObjectState.LongField.X_POSITION, delta); prefixes.add(source);
            source = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() ^ 1)
                    .moveByte(CdObjectState.ByteField.FLAGS, source.flags() ^ 1)
                    .moveWord(CdObjectState.WordField.PATA_BATA_PHASE, 0); prefixes.add(source);
        }
        source = source.addWord(CdObjectState.WordField.PATA_BATA_PHASE,
                source.signedWord(CdObjectState.WordField.PATA_BATA_PHASE_SPEED)); prefixes.add(source);
        int angle = source.word(CdObjectState.WordField.PATA_BATA_PHASE) >>> 8;
        // Observe the original R12A source WORD, not a second CalcSine helper invocation.
        int sine = (short) player.player().sineTable().unsignedWord(angle * 2);
        int swapped = ((sine & 0xFFFF) << 16) | (delta >>> 16);
        int count = source.word(CdObjectState.WordField.PATA_BATA_SHIFT) & 63;
        source = source.moveLong(CdObjectState.LongField.Y_POSITION,
                source.longBits(CdObjectState.LongField.PATA_BATA_ANCHOR_Y) + (count >= 32 ? swapped >> 31 : swapped >> count));
        prefixes.add(source);
        var animated = pataAnimationImage(source,prefixes);
        if (animated == null) { invocation.expectedStop = CdPataBata.UnsupportedBoundary.ANIMATE_OBJECT; return prefixes; }
        prefixes.add(animated); invocation.bodyReady = true; return prefixes;
    }

    /** Read-only scalar Object Animate:14-95 observation over this one shared 18-byte table. */
    private CdObjectState pataAnimationImage(CdObjectState source,List<CdObjectState> prefixes) {
        var expected = source;
        int animation = source.unsignedByte(CdObjectState.ByteField.ANIMATION);
        int index = source.unsignedByte(CdObjectState.ByteField.ANIMATION_FRAME);
        if (animation != source.unsignedByte(CdObjectState.ByteField.PREVIOUS_ANIMATION)) {
            index = 0;
            expected = expected.moveByte(CdObjectState.ByteField.PREVIOUS_ANIMATION, animation)
                    .moveByte(CdObjectState.ByteField.ANIMATION_FRAME, 0).moveByte(CdObjectState.ByteField.ANIMATION_TIME, 0);
        }
        expected = expected.addByte(CdObjectState.ByteField.ANIMATION_TIME, -1);prefixes.add(expected);
        if (expected.unsignedByte(CdObjectState.ByteField.ANIMATION_TIME) < 0x80) return expected;
        var bytes = view(CdR12aProfile.Asset.ANI_PATA_BATA).bytes();
        if (animation >= 2) return null;
        int script = (short) bytes.unsignedWord(animation * 2);
        int timer=pataAnimationByte(bytes,script,0);if(timer<0)return null;
        expected=expected.moveByte(CdObjectState.ByteField.ANIMATION_TIME,timer);prefixes.add(expected);
        int raw=pataAnimationByte(bytes,script,1+index);if(raw<0)return null;
        if (raw == 0xFF) {
            index = 0;expected=expected.moveByte(CdObjectState.ByteField.ANIMATION_FRAME,0);prefixes.add(expected);
            raw = pataAnimationByte(bytes, script, 1); if (raw < 0) return null;
        } else if (raw == 0xFE) {
            int back = pataAnimationByte(bytes, script, 2 + index); if (back < 0) return null;
            index = (index - back) & 0xFF;expected=expected.moveByte(CdObjectState.ByteField.ANIMATION_FRAME,index);prefixes.add(expected);
            raw = pataAnimationByte(bytes, script, 1 + index); if (raw < 0) return null;
        } else if (raw >= 0x80) {
            if (raw == 0xFD) {
                int next = pataAnimationByte(bytes, script, 2 + index); if (next < 0) return null;
                expected = expected.moveByte(CdObjectState.ByteField.ANIMATION, next);
            } else if (raw == 0xFC) expected = expected.addByte(CdObjectState.ByteField.ROUTINE, 2);
            else if (raw == 0xFB) expected = expected.moveByte(CdObjectState.ByteField.ANIMATION_FRAME, 0)
                    .moveByte(CdObjectState.ByteField.ROUTINE_2, 0);
            else if (raw == 0xFA) expected = expected.addByte(CdObjectState.ByteField.ROUTINE_2, 2);
            return expected;
        }
        int rotated = ((raw << 3) | (raw >>> 5)) & 0xFF;
        return expected.moveByte(CdObjectState.ByteField.MAP_FRAME, raw & 0x1F)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, (source.spriteFlags() & 0xFC) | ((rotated ^ source.flags()) & 3))
                .moveByte(CdObjectState.ByteField.ANIMATION_FRAME, (index + 1) & 0xFF);
    }
    private static int pataAnimationByte(CdAssetBytes bytes, int script, int offset) {
        long address = (long) script + offset;
        return address < 0 || address >= bytes.length() ? -1 : bytes.unsignedByte((int) address);
    }

    private void validatePataSource(PataInvocation invocation, PataSite site) {
        boolean failure = site == PataSite.FAILURE;
        var actual = invocation.pool.state(invocation.whole); var bank = invocation.before.savedFlags();
        boolean conversion = invocation.inputs.goodFuture() != 0
                && (invocation.inputs.timeZone() != 1 || invocation.entry.subtype() != 0);
        if (conversion) {
            if (invocation.drawn || site == PataSite.DRAW) throw new IllegalStateException("Converted Pata-Bata reached DrawObject");
            byte[] deleted = new byte[CdObjectState.BYTE_SIZE], x = deleted.clone();
            x[8] = (byte) (invocation.entry.centreX() >>> 8); x[9] = (byte) invocation.entry.centreX();
            byte[] xy = x.clone(); xy[12] = (byte) (invocation.entry.centreY() >>> 8); xy[13] = (byte) invocation.entry.centreY();
            byte[] converted = xy.clone(); converted[0] = 0x18;
            byte[] chosen = converted;
            if (failure) {
                chosen = null;
                for (var prefix : List.of(invocation.entry.copyBytes(), deleted, x, xy, converted))
                    if (Arrays.equals(actual.copyBytes(), prefix)) { chosen = prefix; break; }
                if (chosen == null) throw new IllegalStateException("Pata-Bata conversion exceeded Delete64/X/Y/ID18");
            }
            verifyPataImages(invocation, chosen, bank); return;
        }
        if (!invocation.drawn) {
            var prefixes = pataBodyPrefixes(invocation);
            byte[] chosen = null;
            if (failure) {
                for (var prefix : prefixes) if (prefix.equals(actual)) { chosen = prefix.copyBytes(); break; }
            } else if (site == PataSite.DRAW && invocation.bodyReady) chosen = prefixes.getLast().copyBytes();
            if (chosen == null) throw new IllegalStateException("Pata-Bata source is outside its actual Init/motion/animation prefix");
            verifyPataImages(invocation, chosen, bank); return;
        }
        if (site == PataSite.DRAW || invocation.drawSource == null)
            throw new IllegalStateException("Pata-Bata repeated or lost its native DrawObject source");
        var source = invocation.drawSource;
        if (!invocation.tailArmed) {
            if (!failure) throw new IllegalStateException("Pata-Bata returned before its actual DrawObject returned");
            verifyPataImages(invocation, source.copyBytes(), bank); return;
        }
        // N CheckObjDespawn2:388-446 reloads the live post-Draw anchor and saved ID.
        boolean outside = (source.spriteFlags() & 0x80) == 0
                && (((source.word(CdObjectState.WordField.PATA_BATA_ANCHOR_X) & 0xFF80)
                - ((invocation.camera.xWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
        boolean delete = outside || (invocation.inputs.timeZone() & 0x80) != 0;
        byte[] expectedBank = bank.copy(); boolean owned = true;
        if (delete && source.savedFlagsId() != 0) {
            int period = invocation.inputs.timeZone() & 0x7F;
            if ((invocation.inputs.timeZone() & 0x80) != 0) {
                period = (short) ((period - (byte) invocation.inputs.timeWarpDir()) & 0xFFFF);
                period = period < 0 ? 0 : Math.min(period, 2);
            }
            int at = 2 + 3 * source.savedFlagsId() + period;
            owned = period >= 0 && period <= 2 && at >= 0 && at < expectedBank.length;
            if (owned) expectedBank[at] &= 0x7F;
        }
        byte[] expectedSource = delete && owned ? new byte[CdObjectState.BYTE_SIZE] : source.copyBytes();
        var actualBank = placement.capture().savedFlags();
        if (failure && Arrays.equals(actual.copyBytes(), source.copyBytes()) && sameBytes(actualBank, bank)) {
            verifyPataImages(invocation, source.copyBytes(), bank); return;
        }
        if (failure && owned && Arrays.equals(actual.copyBytes(), source.copyBytes())
                && Arrays.equals(actualBank.copy(), expectedBank)) {
            verifyPataImages(invocation, source.copyBytes(), CdAssetBytes.copyOf(expectedBank)); return;
        }
        if (!owned && !failure) throw new IllegalStateException("Pata-Bata returned beyond an unowned native saved-period address");
        verifyPataImages(invocation, expectedSource, CdAssetBytes.copyOf(expectedBank));
    }

    private void verifyPataImages(PataInvocation invocation, byte[] source, CdAssetBytes bank) {
        verifyPataInvocation(invocation);
        var now = placement.capture();
        if (!sameBytes(now.savedFlags(), bank) || !now.status().equals(invocation.before.status()))
            throw new IllegalStateException("Pata-Bata changed unowned saved-bank bits or placement cursors");
        for (int i = 0; i < 96; i++) {
            var image = now.pool().slots().get(i);
            if (i == invocation.whole - 32) {
                if (!image.identity().equals(invocation.entry.identity()) || !Arrays.equals(image.copyBytes(), source))
                    throw new IllegalStateException("Pata-Bata changed its actual source identity/full64 prefix");
            } else if (!image.equals(invocation.before.pool().slots().get(i)))
                throw new IllegalStateException("Pata-Bata changed another physical image");
        }
    }
    private void verifyPataCallback(PataInvocation invocation, CdObjectPlacementRuntime.Snapshot before) {
        verifyPataImages(invocation, invocation.drawSource.copyBytes(), before.savedFlags());
        if (!before.status().equals(invocation.before.status())
                || !before.pool().slots().get(invocation.whole - 32).identity().equals(invocation.entry.identity()))
            throw new IllegalStateException("Pata-Bata callback lost its actual pre-call source cut");
    }
    private void verifyPataCallbackFailure(PataInvocation invocation, CdObjectPlacementRuntime.Snapshot before, Throwable original) {
        try { verifyPataCallback(invocation, before); }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    private void drawPataBata(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requirePataInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (drawingPata || boulderPort || drawingBoulder || antonPort || drawingAnton || queuePort || invocation.drawn || !identity.equals(invocation.entry.identity()))
            throw new IllegalStateException("Pata-Bata DrawObject lacks its actual once-only native source");
        validatePataSource(invocation, PataSite.DRAW);
        var before = placement.capture(); invocation.drawSource = pool.state(invocation.whole);
        invocation.drawn = true; drawingPata = true;
        try {
            var current = invocation.drawSource;
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(),
                    current.unsignedByte(CdObjectState.ByteField.Y_RADIUS), current.unsignedByte(CdObjectState.ByteField.WIDTH),
                    current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            if (!invocation.screenCleared) throw new IllegalStateException("Pata-Bata DrawObject did not clear bit7 once");
            verifyPataCallback(invocation, before); invocation.tailArmed = true;
        } catch (RuntimeException | Error original) {
            verifyPataCallbackFailure(invocation, before, original); throw original;
        } finally { drawingPata = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readPataDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requirePataInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingPata || boulderPort || drawingBoulder || antonPort || drawingAnton || queuePort || !invocation.drawn) throw new IllegalStateException("No actual Pata-Bata collecting queue read");
        var before = placement.capture(); queuePort = true;
        try {
            verifyPataCallback(invocation, before); var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(), current.centreX(), current.centreY(),
                    current.word(CdObjectState.WordField.X_FRACTION), current.longBits(CdObjectState.LongField.MAPPINGS),
                    current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifyPataCallback(invocation, before); return live;
        } catch (RuntimeException | Error original) {
            verifyPataCallbackFailure(invocation, before, original); throw original;
        } finally { queuePort = false; }
    }
    private void writePataOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requirePataInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingPata || boulderPort || drawingBoulder || antonPort || drawingAnton || queuePort || onScreen || invocation.screenCleared || !invocation.drawn)
            throw new IllegalStateException("Only actual Pata-Bata DrawObject may clear bit7 once");
        var before = placement.capture(); verifyPataCallback(invocation, before);
        var next = invocation.drawSource.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, invocation.drawSource.spriteFlags() & 0x7F);
        queuePort = true;
        try {
            invocation.pool.write(next); invocation.drawSource = next; invocation.screenCleared = true;
            verifyPataCallback(invocation, before);
        } catch (RuntimeException | Error original) {
            verifyPataCallbackFailure(invocation, before, original); throw original;
        } finally { queuePort = false; }
    }

    /** Returned effects are checked observations only: no effect grants writes, drawing or another dispatch. */
    private void verifyPataResult(PataInvocation invocation, CdPataBata.Result result) {
        var effects = new ArrayList<CdPataBata.Effect>();
        boolean conversion = invocation.inputs.goodFuture() != 0
                && (invocation.inputs.timeZone() != 1 || invocation.entry.subtype() != 0);
        boolean delete = false;
        if (conversion) effects.add(new CdPataBata.ExplosionConversion(invocation.entry.centreX(), invocation.entry.centreY()));
        else {
            if (!invocation.drawn || !invocation.screenCleared || !invocation.tailArmed || !invocation.bodyReady)
                throw new IllegalStateException("Pata-Bata returned without its complete actual body/Draw cut");
            effects.add(new CdPataBata.CallReturned(CdPataBata.NativeCall.DESTROY_ON_GOOD_FUTURE));
            effects.add(new CdPataBata.CallReturned(CdPataBata.NativeCall.ANIMATE_OBJECT));
            effects.add(new CdPataBata.CallReturned(CdPataBata.NativeCall.DRAW_OBJECT));
            var source = invocation.drawSource;
            boolean outside = (source.spriteFlags() & 0x80) == 0
                    && (((source.word(CdObjectState.WordField.PATA_BATA_ANCHOR_X) & 0xFF80)
                    - ((invocation.camera.xWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
            delete = outside || (invocation.inputs.timeZone() & 0x80) != 0;
            if (delete) {
                int period = -1;
                if (source.savedFlagsId() != 0) {
                    period = invocation.inputs.timeZone() & 0x7F;
                    if ((invocation.inputs.timeZone() & 0x80) != 0) {
                        period = (short) ((period - (byte) invocation.inputs.timeWarpDir()) & 0xFFFF);
                        period = period < 0 ? 0 : Math.min(period, 2);
                    }
                }
                effects.add(new CdPataBata.Despawn(outside ? CdPataBata.DespawnEntry.ANCHOR_OFFSCREEN
                        : CdPataBata.DespawnEntry.TIME_WARP, source.savedFlagsId(), period));
            }
            effects.add(new CdPataBata.CallReturned(CdPataBata.NativeCall.CHECK_OBJ_DESPAWN2));
        }
        if (!result.state().equals(invocation.pool.state(invocation.whole)) || !result.effects().equals(effects)
                || result.converted() != conversion || result.deleted() != delete)
            throw new IllegalStateException("Pata-Bata returned metadata disagrees with its actual completed native branch");
    }




    private final class TamabbohInvocation {
        private final CdTamabboh body = tamabboh;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR12aTerrainAssets sourceAssets = assets;
        private final Object stageSource = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final CdPlayerBody.State player = frame.state();
        private final CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private final CdObjectPlacementRuntime.Status placementStatus = placement.status();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.captureAct2Scroll();
        private Values expectedValues = values;
        private final CdObjectPlacementRuntime.Snapshot before = placement.capture();
        private final Set<Object> consumed = Collections.newSetFromMap(new IdentityHashMap<>());
        private List<CdTamabboh.PrimaryAngleCommitted> angleReceipts = List.of();
        private List<CdTamabboh.PlayerWordReadReturned> playerReceipts = List.of();
        private List<CdTamabboh.FmPlayed> fmReceipts = List.of();
        private CdObjectPool.Snapshot drawCut;
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final CdTamabboh.Globals globals;
        private final CdObjectDrawQueue.CameraWords camera;
        private CdObjectState expectedRangeX, drawSource;
        private boolean drawn, screenCleared, sounded;
        private TamabbohInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var fg = presentation.act2ForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(fg.cameraX(), fg.cameraYWord());
            var environment = frame.environment(); var movement = environment.movement();
            globals = new CdTamabboh.Globals(values.globals().retainedDev().goodFuture(),
                    movement.gates().timeZone(), movement.gates().timeWarpDirection(),
                    environment.globals().debugMode(), camera.xWord(), movement.globals().primaryAngle());
        }
    }

    private void runTamabboh(int whole) {
        requireDoorIdle();
        requireLogMosquiIdle();
        requireWallIdle();
        module.requireAct2LoadingVisit(this);
        if (activeTitle != null || titlePort || drawingTitle) throw new IllegalStateException("Actual Title owns this cut");
        if (!runningObjects || servicing || activePrimary != null || activeDrain != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeFloat != null || activePata != null || activeMonitor != null
                || activeTamabboh != null || activeCheckpoint != null || checkpointPort || drawingCheckpoint || activeCollapse!=null || collapsePort || drawingCollapse || activeBoulder != null || activeAnton != null || reservedPort || drawingReserved || ringPort || drawingRing
                || springPort || drawingSpring || platformPort || drawingPlatform || floatPort || drawingFloat
                || drawingPata || monitorPort || drawingMonitor
                || tamabbohPort || drawingTamabboh || boulderPort || drawingBoulder || antonPort || drawingAnton || queuePort || whole < 32 || whole >= 128
                || placement.pool().state(whole).id() != CdTamabboh.OBJECT_ID || values.phase() != BEGIN_LEVEL
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Act2 Tamabboh dispatch");
        var invocation = new TamabbohInvocation(whole); activeTamabboh = invocation;
        try {
            var result = tamabboh.run(whole, invocation.globals);
            verifyTamabbohInvocation(invocation); validateTamabbohMutation(invocation, false);
            if (!result.effects().stream().filter(CdTamabboh.PrimaryAngleCommitted.class::isInstance).toList().equals(invocation.angleReceipts)
                    || !result.effects().stream().filter(CdTamabboh.PlayerWordReadReturned.class::isInstance).toList().equals(invocation.playerReceipts)
                    || !result.effects().stream().filter(CdTamabboh.FmPlayed.class::isInstance).toList().equals(invocation.fmReceipts))
                throw new IllegalStateException("Tamabboh completed observations lack actual scalar/read/FM receipts");
            if (!result.state().equals(invocation.pool.state(whole)))
                throw new IllegalStateException("Tamabboh result differs from its actual completed physical image");
            boolean conversion = invocation.entry.subtype() != 1 && invocation.globals.goodFuture() != 0
                    && (invocation.globals.timeZone() != 1 || invocation.entry.subtype() != 0);
            if (conversion ? invocation.drawn : !invocation.drawn || !invocation.screenCleared)
                throw new IllegalStateException("Tamabboh returned without its actual native DrawObject cut");
        } catch (RuntimeException | Error original) {
            try { verifyTamabbohInvocation(invocation); validateTamabbohMutation(invocation, true); }
            catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
            throw original;
        } finally { activeTamabboh = null; }
    }

    private void validateTamabbohCompletedCallbacks(TamabbohInvocation i) {
        if (!tamabboh.matchesCurrentCallbackPrefix(i.angleReceipts,i.playerReceipts,i.fmReceipts))
            throw new IllegalStateException("Tamabboh skipped or substituted a prior actual callback publication");
    }

    /** Other rows are byte-exact, except the two real first-free Fire children and their native dirty-byte stores. */
    private void validateTamabbohOtherImages(TamabbohInvocation i, boolean failure) {
        var expected = new ArrayList<>(i.before.pool().slots());
        if (i.entry.subtype() == 0 && i.entry.routine() == 10) {
            int direction = 0;
            for (var old : i.before.pool().slots()) {
                if (old.id() != 0 || direction >= 2) continue;
                var child = old.moveByte(CdObjectState.ByteField.ID,CdTamabboh.OBJECT_ID)
                        .moveByte(CdObjectState.ByteField.SUBTYPE,1).moveWord(CdObjectState.WordField.TILE,i.entry.word(CdObjectState.WordField.TILE))
                        .moveByte(CdObjectState.ByteField.PRIORITY,i.entry.unsignedByte(CdObjectState.ByteField.PRIORITY)+1)
                        .moveWord(CdObjectState.WordField.X,i.entry.centreX()).moveWord(CdObjectState.WordField.Y,i.entry.centreY()-10)
                        .moveByte(CdObjectState.ByteField.TAMABBOH_MISSILE_DIRECTION,direction++ == 0 ? 0 : 255);
                var actual = i.pool.state(old.identity().wholeIndex());
                // Before the synchronous FM return, neither child has been touched. Afterwards both stores are pure.
                if (!actual.equals(child) && !(actual.equals(old) && (!i.drawn || failure)))
                    throw new IllegalStateException("Tamabboh Fire changed a first-free dirty image beyond its exact native stores");
                expected.set(old.identity().wholeIndex()-32,actual);
            }
        }
        expected.set(i.whole-32,i.pool.state(i.whole));
        if (!i.pool.capture().slots().equals(expected))
            throw new IllegalStateException("Tamabboh changed an unrelated full96 physical row");
    }

    private void validateTamabbohMutation(TamabbohInvocation i, boolean failure) {
        validateTamabbohOtherImages(i,failure);
        var actual=i.pool.state(i.whole); var bank=placement.capture().savedFlags();
        boolean conversion=i.entry.subtype()!=1 && i.globals.goodFuture()!=0
                && (i.globals.timeZone()!=1 || i.entry.subtype()!=0);
        if(conversion) {
            byte[] zero=new byte[64]; byte[] x=zero.clone(), xy;
            x[8]=(byte)(i.entry.centreX()>>>8);x[9]=(byte)i.entry.centreX();xy=x.clone();
            xy[12]=(byte)(i.entry.centreY()>>>8);xy[13]=(byte)i.entry.centreY();byte[] result=xy.clone();result[0]=0x18;
            boolean prefix=failure && (actual.equals(i.entry) || Arrays.equals(actual.copyBytes(),zero)
                    || Arrays.equals(actual.copyBytes(),x) || Arrays.equals(actual.copyBytes(),xy));
            if(!sameBytes(bank,i.before.savedFlags()) || i.drawn || !prefix && !Arrays.equals(actual.copyBytes(),result))
                throw new IllegalStateException("Tamabboh conversion crossed its exact Delete64/X/Y/ID18 prefix");
            return;
        }
        if(!i.drawn) {
            if(!sameBytes(bank,i.before.savedFlags())) throw new IllegalStateException("Tamabboh pre-Draw changed saved bank");
            if(i.entry.subtype()==1 && i.entry.routine()==2 && Arrays.equals(actual.copyBytes(),new byte[64])) return;
            byte[] before=i.entry.copyBytes(),after=actual.copyBytes();
            for(int at=0;at<64;at++) {
                int mask=(at>=2&&at<=15 || at>=0x16&&at<=0x1E || at==0x20 || at==0x24 || at>=0x2A&&at<=0x39)?255
                        :at==1?0x87:at==0x22?1:0;
                if(((before[at]^after[at])&~mask&255)!=0)
                    throw new IllegalStateException("Tamabboh changed an unowned full64 source byte "+at);
            }
            return;
        }
        var source=i.drawSource;
        if(i.entry.subtype()==1) {
            if(!actual.equals(source) || !sameBytes(bank,i.before.savedFlags()) || i.drawCut==null
                    || !i.pool.capture().slots().equals(i.drawCut.slots()))
                throw new IllegalStateException("Tamabboh missile changed its native post-Draw source/full96/bank");
            return;
        }
        boolean outside=source.signedByte(CdObjectState.ByteField.SPRITE_FLAGS)>=0
                && (((source.word(CdObjectState.WordField.TAMABBOH_ANCHOR_X)&0xFF80)
                -((i.camera.xWord()-0x80)&0xFF80))&0xFFFF)>0x280;
        boolean delete=outside || (i.globals.timeZone()&0x80)!=0;
        byte[] saved=i.before.savedFlags().copy();boolean owned=true;
        if(delete && source.savedFlagsId()!=0) {
            int period=i.globals.timeZone()&0x7F;
            if((i.globals.timeZone()&0x80)!=0){period=(short)((period-(byte)i.globals.timeWarpDirection())&65535);period=period<0?0:Math.min(period,2);}
            int address=2+3*source.savedFlagsId()+period;owned=address>=2&&address<saved.length;
            if(owned)saved[address]&=0x7F;
        }
        if(failure && actual.equals(source) && (sameBytes(bank,i.before.savedFlags()) || owned&&Arrays.equals(bank.copy(),saved)))return;
        if(!owned&&!failure || !Arrays.equals(actual.copyBytes(),delete&&owned?new byte[64]:source.copyBytes())
                || !Arrays.equals(bank.copy(),saved))
            throw new IllegalStateException("Tamabboh return/failure changed its exact source/saved despawn tail");
    }

    private TamabbohInvocation requireTamabbohInvocation(CdObjectPool pool, int whole) {
        var invocation = activeTamabboh;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128)
            throw new IllegalStateException("No actual cached Tamabboh callback at this physical pointer");
        verifyTamabbohInvocation(invocation); return invocation;
    }
    private void verifyTamabbohInvocation(TamabbohInvocation invocation) {
        module.requireAct2LoadingVisit(this);
        if (activeTitle != null || titlePort || drawingTitle) throw new IllegalStateException("Actual Title owns this cut");
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeTamabboh != invocation || activeCheckpoint != null || checkpointPort || drawingCheckpoint || activeCollapse!=null || collapsePort || drawingCollapse || activeBoulder != null || boulderPort || drawingBoulder || activeAnton != null || antonPort || drawingAnton || !runningObjects || servicing
                || activePrimary != null || activeDrain != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeFloat != null || activePata != null
                || activeMonitor != null || reservedPort || drawingReserved
                || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform
                || floatPort || drawingFloat || drawingPata || monitorPort || drawingMonitor
                || invocation.body != tamabboh || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.stageSource != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || invocation.world != mode.getWorldSession()
                || invocation.generation != mode.getLevelManager().getCompletedProductionLoadGeneration()
                || invocation.expectedValues != values || values.phase() != BEGIN_LEVEL || invocation.expectedSounds != sounds
                || !placement.status().equals(invocation.placementStatus) || !presentation.sameAct2Scroll(invocation.scroll)
                || frame.state() != invocation.player || !frame.captureActor().equals(invocation.actor)
                || !frame.captureShared().equals(invocation.shared))
            throw new IllegalStateException("Tamabboh changed another owner or retained a stale actual World/Frame cut");
    }
    private CdObjectState validateTamabbohCut(TamabbohInvocation invocation, CdObjectPool.Snapshot cut,
                                             CdObjectState source) {
        verifyTamabbohInvocation(invocation); invocation.pool.validateRestore(cut);
        var live = invocation.pool.state(invocation.whole);
        if (live.id() != CdTamabboh.OBJECT_ID || !live.equals(source)
                || source.identity().wholeIndex() != invocation.whole
                || !cut.slots().get(invocation.whole - 32).equals(source)
                || !invocation.pool.capture().slots().equals(cut.slots()))
            throw new IllegalStateException("Tamabboh port differs from its actual same-owner full96/source cut");
        validateTamabbohOtherImages(invocation, false);
        if (!sameBytes(placement.capture().savedFlags(),invocation.before.savedFlags()))
            throw new IllegalStateException("Tamabboh changed saved flags before its native despawn tail");
        return live;
    }
    private void verifyTamabbohCallback(TamabbohInvocation invocation, CdObjectPlacementRuntime.Snapshot before) {
        verifyTamabbohInvocation(invocation);
        var now = placement.capture();
        if (!now.status().equals(before.status()) || !now.pool().slots().equals(before.pool().slots())
                || !sameBytes(now.savedFlags(), before.savedFlags()))
            throw new IllegalStateException("Tamabboh read/angle/FM callback changed the full96 pool or saved/placement state");
    }
    private void verifyTamabbohCallbackFailure(TamabbohInvocation invocation,
                                              CdObjectPlacementRuntime.Snapshot before, Throwable original) {
        try { verifyTamabbohCallback(invocation, before); }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    /** Every reached floor store uses this exact current primary byte; no player/state aggregate is written. */
    private void commitTamabbohPrimaryAngle(CdTamabboh.PrimaryAngleCall call) {
        Objects.requireNonNull(call); var invocation = requireTamabbohInvocation(placement.pool(), call.expectedSource().identity().wholeIndex());
        if (!tamabboh.isCurrentPrimaryAngleCall(call) || invocation.consumed.contains(call))
            throw new IllegalStateException("Tamabboh callback lacks its actual unconsumed producer call");
        validateTamabbohCompletedCallbacks(invocation);
        if (tamabbohPort || drawingTamabboh || boulderPort || drawingBoulder || antonPort || drawingAnton || queuePort || invocation.drawn || invocation.expectedRangeX != null)
            throw new IllegalStateException("Tamabboh angle is outside its genuine floor call");
        var live = validateTamabbohCut(invocation, call.callCut(), call.expectedSource());
        if ((live.subtype() == 1 ? live.routine() != 2 : live.routine() != 2 && live.routine() != 4)
                || frame.environment().movement().globals().primaryAngle() != call.expectedPrimaryAngle())
            throw new IllegalStateException("Tamabboh angle differs from its reached routine/current Frame byte");
        var before = placement.capture();
        var readyValues = preparedMonitorValues(TAMABBOH_PRIMARY_ANGLE);
        var receiptList = new ArrayList<>(invocation.angleReceipts);
        receiptList.add(new CdTamabboh.PrimaryAngleCommitted(call.expectedPrimaryAngle(),call.value()));
        var readyReceipts = List.copyOf(receiptList);
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(),
                monitorAngleEnvironment(invocation.shared.environment(), call.value()), invocation.shared.artState(),
                invocation.shared.paused(), invocation.shared.vIntClock());
        tamabbohPort = true;
        try {
            verifyTamabbohCallback(invocation, before);
            invocation.consumed.add(call);
            frame.applyObjectPrimaryAngle(call.value()); invocation.shared = readyShared;
            values = readyValues; invocation.expectedValues = readyValues;
            invocation.angleReceipts = readyReceipts;
            verifyTamabbohCallback(invocation, before);
        } catch (RuntimeException | Error original) {
            verifyTamabbohCallbackFailure(invocation, before, original); throw original;
        } finally { tamabbohPort = false; }
    }
    private int readTamabbohPlayerWord(CdTamabboh.PlayerReadCall call) {
        Objects.requireNonNull(call); var invocation = requireTamabbohInvocation(placement.pool(), call.expectedSource().identity().wholeIndex());
        if (!tamabboh.isCurrentPlayerReadCall(call) || invocation.consumed.contains(call))
            throw new IllegalStateException("Tamabboh callback lacks its actual unconsumed producer call");
        validateTamabbohCompletedCallbacks(invocation);
        if (tamabbohPort || drawingTamabboh || boulderPort || drawingBoulder || antonPort || drawingAnton || queuePort || invocation.drawn)
            throw new IllegalStateException("Tamabboh player read is outside its actual range call");
        var live = validateTamabbohCut(invocation, call.callCut(), call.expectedSource());
        if (live.routine() != 4 || live.subtype() != 0 || live.word(CdObjectState.WordField.TAMABBOH_COOLDOWN) != 0
                || frame.environment().globals().debugMode() != 0)
            throw new IllegalStateException("Tamabboh player read is not its reached Main range cut");
        var before = placement.capture();
        var readyValues = preparedMonitorValues(call.field() == CdTamabboh.PlayerField.Y_WORD ? TAMABBOH_PLAYER_Y : TAMABBOH_PLAYER_X);
        tamabbohPort = true;
        try {
            int value;
            if (call.field() == CdTamabboh.PlayerField.Y_WORD) {
                if (invocation.expectedRangeX != null) throw new IllegalStateException("Tamabboh repeated Y before its reached X read");
                value = invocation.player.motion().centreY16_16() >>> 16;
                invocation.expectedRangeX = ((value - live.centreY() + 0x50) & 0xFFFF) < 0xA0 ? live : null;
            } else {
                if (invocation.expectedRangeX == null || !invocation.expectedRangeX.equals(live))
                    throw new IllegalStateException("Tamabboh X read did not follow its actual in-range Y read");
                value = invocation.player.motion().centreX16_16() >>> 16; invocation.expectedRangeX = null;
            }
            var receiptList = new ArrayList<>(invocation.playerReceipts);
            receiptList.add(new CdTamabboh.PlayerWordReadReturned(call.field(),value)); var readyReceipts = List.copyOf(receiptList);
            invocation.consumed.add(call); values = readyValues; invocation.expectedValues = readyValues;
            invocation.playerReceipts = readyReceipts;
            verifyTamabbohCallback(invocation, before); return value;
        } catch (RuntimeException | Error original) {
            verifyTamabbohCallbackFailure(invocation, before, original); throw original;
        } finally { tamabbohPort = false; }
    }
    private void playTamabbohFm(CdTamabboh.FmCall call) {
        Objects.requireNonNull(call); var invocation = requireTamabbohInvocation(placement.pool(), call.expectedSource().identity().wholeIndex());
        if (!tamabboh.isCurrentFmCall(call) || invocation.consumed.contains(call))
            throw new IllegalStateException("Tamabboh callback lacks its actual unconsumed producer call");
        validateTamabbohCompletedCallbacks(invocation);
        if (tamabbohPort || drawingTamabboh || boulderPort || drawingBoulder || antonPort || drawingAnton || queuePort || invocation.drawn || invocation.sounded)
            throw new IllegalStateException("Tamabboh FM lacks its actual first Fire request");
        var live = validateTamabbohCut(invocation, call.callCut(), call.expectedSource());
        if (live.routine() != 4 || live.subtype() != 0 || live.word(CdObjectState.WordField.TAMABBOH_COOLDOWN) != 0x78
                || live.unsignedByte(CdObjectState.ByteField.ANIMATION) != 0 || live.signedByte(CdObjectState.ByteField.SPRITE_FLAGS) >= 0
                || call.sound() != CdTamabboh.FmSound.FM_A0
                || invocation.pool.findObjSlot().orElse(-1) != call.firstFreeWholeIndex()
                || invocation.pool.state(call.firstFreeWholeIndex()).id() != 0)
            throw new IllegalStateException("Tamabboh FM_A0 lacks its actual parent prefix/untouched first-free candidate");
        var before = placement.capture(); var ledger = new ArrayList<>(sounds);
        ledger.add(new SonicCdGameModule.PastTamabbohFm(call.sound())); var ready = List.copyOf(ledger);
        var readyValues = preparedMonitorValues(TAMABBOH_FM);
        var receiptList = new ArrayList<>(invocation.fmReceipts);
        receiptList.add(new CdTamabboh.FmPlayed(call.firstFreeWholeIndex(),call.sound())); var readyReceipts = List.copyOf(receiptList);
        tamabbohPort = true;
        try {
            verifyTamabbohCallback(invocation, before);
            invocation.consumed.add(call); values = readyValues; invocation.expectedValues = readyValues;
            invocation.fmReceipts = readyReceipts;
            sounds = ready; invocation.expectedSounds = ready; invocation.sounded = true;
            verifyTamabbohCallback(invocation, before);
        } catch (RuntimeException | Error original) {
            verifyTamabbohCallbackFailure(invocation, before, original); throw original;
        } finally { tamabbohPort = false; }
    }

    private void drawTamabboh(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireTamabbohInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (tamabbohPort || drawingTamabboh || boulderPort || drawingBoulder || antonPort || drawingAnton || queuePort || invocation.drawn || !identity.equals(invocation.entry.identity()))
            throw new IllegalStateException("Tamabboh DrawObject lacks its actual once-only physical source");
        if (!tamabboh.isCurrentDrawCall(pool,identity)) throw new IllegalStateException("Tamabboh Draw lacks its actual producer source prefix");
        validateTamabbohCompletedCallbacks(invocation);
        validateTamabbohOtherImages(invocation, false);
        var current = pool.state(invocation.whole);
        invocation.drawCut = pool.capture();
        boolean clearedMissile = invocation.entry.subtype() == 1 && invocation.entry.routine() == 2
                && current.id() == 0 && Arrays.equals(current.copyBytes(), new byte[CdObjectState.BYTE_SIZE]);
        if (current.id() != CdTamabboh.OBJECT_ID && !clearedMissile)
            throw new IllegalStateException("Only the genuine cleared missile may draw its live ID0 image");
        invocation.drawSource = current; invocation.drawn = true; drawingTamabboh = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(),
                    current.unsignedByte(CdObjectState.ByteField.Y_RADIUS), current.unsignedByte(CdObjectState.ByteField.WIDTH),
                    current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            if (!invocation.screenCleared) throw new IllegalStateException("Tamabboh DrawObject did not clear bit7 exactly once");
            verifyTamabbohInvocation(invocation);
        } catch (RuntimeException | Error original) {
            try { verifyTamabbohInvocation(invocation); }
            catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
            throw original;
        } finally { drawingTamabboh = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readTamabbohDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireTamabbohInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingTamabboh || boulderPort || drawingBoulder || antonPort || drawingAnton || tamabbohPort || queuePort || !invocation.drawn)
            throw new IllegalStateException("No actual collecting Tamabboh queue read");
        var before = placement.capture(); queuePort = true;
        try {
            var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(), current.centreX(), current.centreY(),
                    current.word(CdObjectState.WordField.X_FRACTION), current.longBits(CdObjectState.LongField.MAPPINGS),
                    current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifyTamabbohCallback(invocation, before); return live;
        } catch (RuntimeException | Error original) {
            verifyTamabbohCallbackFailure(invocation, before, original); throw original;
        } finally { queuePort = false; }
    }
    private void writeTamabbohOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireTamabbohInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingTamabboh || boulderPort || drawingBoulder || antonPort || drawingAnton || tamabbohPort || queuePort || onScreen || invocation.screenCleared || !invocation.drawn)
            throw new IllegalStateException("Only actual Tamabboh DrawObject may clear bit7 once");
        var before = placement.capture(); var source = invocation.pool.state(invocation.whole);
        if (!source.equals(invocation.drawSource)) throw new IllegalStateException("Tamabboh changed its DrawObject source before BCLR7");
        var next = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() & 0x7F);
        var ready = CdObjectPool.overlay(before.pool(), List.of(next)); invocation.pool.validateRestore(ready);
        queuePort = true;
        try {
            verifyTamabbohCallback(invocation, before);
            invocation.pool.write(next); invocation.screenCleared = true; invocation.drawSource = next;
            invocation.drawCut = ready;
            verifyTamabbohInvocation(invocation); var now = placement.capture();
            if (!now.pool().slots().equals(ready.slots()) || !sameBytes(now.savedFlags(), before.savedFlags())
                    || !now.status().equals(before.status()))
                throw new IllegalStateException("Tamabboh DrawObject changed more than its exact source BCLR7 byte");
        } catch (RuntimeException | Error original) {
            try { verifyTamabbohInvocation(invocation); }
            catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
            throw original;
        } finally { queuePort = false; }
    }

    private enum FloatSite { SOLID, DRAW, RETURN, FAILURE }
    private final class FloatInvocation {
        private final CdFloatBlock body = floatBlock;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR12aTerrainAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final Values base = values;
        private final CdObjectPlacementRuntime.Status status = placement.status();
        private final CdAssetBytes savedFlags = placement.capture().savedFlags();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.captureAct2Scroll();
        private final CdObjectDrawQueue.CameraWords camera;
        private CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private Values expectedValues = base;
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private CdObjectPool.Snapshot currentPool = pool.capture();
        private CdObjectState expectedSource;
        private CdObjectPool.Snapshot lastCut;
        private CdSolidObject.Outcome adoptedOutcome;
        private List<CdSolidObject.Result> completed = List.of();
        private int ordinal;
        private boolean falling, stopped, drawn, screenCleared, bodyVerified, noDraw, tailArmed, animationStopped;
        private FloatInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole); expectedSource = entry;
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.act2ForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
        }
    }

    private void runFloatBlock(int whole) {
        requireDoorIdle();
        requireLogMosquiIdle();
        requireWallIdle();
        module.requireAct2LoadingVisit(this);
        if (activeTitle != null || titlePort || drawingTitle) throw new IllegalStateException("Actual Title owns this cut");
        if (!runningObjects || servicing || activeDrain != null || activePrimary != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeMonitor != null || activeFloat != null || activePata != null || activeAnton != null || antonPort || drawingAnton || activeTamabboh != null || tamabbohPort || drawingTamabboh || activeCheckpoint != null || checkpointPort || drawingCheckpoint || activeCollapse!=null || collapsePort || drawingCollapse || activeBoulder != null || boulderPort || drawingBoulder || drawingPata
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform || monitorPort || drawingMonitor
                || floatPort || drawingFloat || queuePort || whole < 32 || whole >= 128
                || placement.pool().state(whole).id() != CdFloatBlock.OBJECT_ID || values.phase() != BEGIN_LEVEL
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual live Act2 FloatBlock invocation entry");
        var invocation = new FloatInvocation(whole); activeFloat = invocation;
        try {
            var env = frame.environment(); var gates = env.movement().gates();
            var result = floatBlock.run(whole, new CdFloatBlock.Globals(gates.timeZone(),
                            invocation.camera.xWord(), invocation.camera.yWord()),
                    new CdFloatBlock.SolidInputs(springPlayer(),
                            new CdSolidObject.Gates(env.globals().debugMode(), gates.miniSonic()), springGlobals()));
            verifyFloatInvocation(invocation); validateFloatSource(invocation, FloatSite.RETURN);
            if (invocation.stopped || !result.state().equals(invocation.pool.state(whole)))
                throw new IllegalStateException("FloatBlock returned without its exact actual source image");
            verifyFloatResult(invocation, result);
        } catch (RuntimeException | Error failure) {
            if (invocation.adoptedOutcome instanceof CdSolidObject.NativeStopped nativeStop) {
                var original = nativeStop.stop().originalException();
                if (failure != original) original.addSuppressed(failure);
                verifyFloatFailure(original); throw original;
            }
            verifyFloatFailure(failure); throw failure;
        } finally { activeFloat = null; }
    }

    private FloatInvocation requireFloatInvocation(CdObjectPool pool, int whole) {
        var invocation = activeFloat;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128 || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached FloatBlock callback at this physical pointer");
        verifyFloatInvocation(invocation); return invocation;
    }

    private void verifyFloatInvocation(FloatInvocation invocation) {
        module.requireAct2LoadingVisit(this);
        if (activeTitle != null || titlePort || drawingTitle) throw new IllegalStateException("Actual Title owns this cut");
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeFloat != invocation || activePata != null || drawingPata || activeAnton != null || antonPort || drawingAnton || activeTamabboh != null || tamabbohPort || drawingTamabboh || activeCheckpoint != null || checkpointPort || drawingCheckpoint || activeCollapse!=null || collapsePort || drawingCollapse || activeBoulder != null || boulderPort || drawingBoulder || !runningObjects || servicing || activeDrain != null
                || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null
                || activePlatform != null || activeMonitor != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform || monitorPort || drawingMonitor
                || invocation.body != floatBlock || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.sourceStage != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared)
                || !placement.status().equals(invocation.status) || !presentation.sameAct2Scroll(invocation.scroll)
                || !sameBytes(placement.capture().savedFlags(), invocation.savedFlags)
                || !sameBytes(values.hudObjects(), invocation.base.hudObjects())
                || sounds != invocation.expectedSounds || values != invocation.expectedValues || values.phase() != BEGIN_LEVEL)
            throw new IllegalStateException("FloatBlock changed another owner or retained a stale actual World/Frame cut");
    }

    private void verifyFloatFailure(Throwable original) {
        try {
            if (activeFloat != null) {
                verifyFloatInvocation(activeFloat); validateFloatSource(activeFloat, FloatSite.FAILURE);
            }
        } catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private boolean matchesFloatPool(FloatInvocation invocation, CdObjectState source) {
        return CdObjectPool.overlay(invocation.currentPool, List.of(source)).slots().equals(invocation.pool.capture().slots());
    }
    private void acceptFloatPool(FloatInvocation invocation, CdObjectState source) {
        var ready = CdObjectPool.overlay(invocation.currentPool, List.of(source));
        invocation.pool.validateRestore(ready);
        if (!ready.slots().equals(invocation.pool.capture().slots()))
            throw new IllegalStateException("FloatBlock changed bytes beyond its exact reached a0 prefix/full96 cut");
        invocation.currentPool = ready; invocation.expectedSource = source;
    }
    private CdObjectState floatDeletedImage(FloatInvocation invocation, CdObjectState source) {
        var actual = invocation.pool.state(invocation.whole);
        if (!actual.identity().equals(source.identity()) || !Arrays.equals(actual.copyBytes(), new byte[CdObjectState.BYTE_SIZE]))
            throw new IllegalStateException("FloatBlock DeleteObject did not clear its entire actual64 image");
        return actual;
    }
    private boolean floatOutsideWindow(FloatInvocation invocation, CdObjectState source) {
        return (((source.centreX() & 0xFF80) - ((invocation.camera.xWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
    }

    /** Source instruction stores only. No Solid, Movement, terrain, animator or other producer is executed here. */
    private CdObjectState floatSolidSource(FloatInvocation invocation) {
        var source = invocation.expectedSource;
        if (invocation.ordinal == 0) {
            int routine = invocation.entry.routine();
            if ((routine & 1) != 0 || routine > 10)
                throw new IllegalStateException("FloatBlock Top does not belong to this native routine");
            if (routine == 0) {
                source = source.addByte(CdObjectState.ByteField.ROUTINE, 2)
                        .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() | 4)
                        .moveLong(CdObjectState.LongField.MAPPINGS,
                                view(CdR12aProfile.Asset.MAP_SPR_FLOAT_BLOCK).range().nativeAddress());
                if (source.subtype2() != 0)
                    throw new IllegalStateException("FloatBlock Top advanced past a missing Init tile read");
                var tiles = view(CdR12aProfile.Asset.OBJECT_TILE_IDS).bytes();
                int target = (short) tiles.unsignedWord(CdFloatBlock.TILE_SELECTOR * 2);
                int end = CdObjectTileIds.endOfBody(tiles, CdFloatBlock.TILE_SELECTOR);
                if (target < 0 || target > end - 2)
                    throw new IllegalStateException("FloatBlock Init selected an absent declared tile word");
                source = source.moveWord(CdObjectState.WordField.TILE, tiles.unsignedWord(target))
                        .moveByte(CdObjectState.ByteField.PRIORITY, 1).moveByte(CdObjectState.ByteField.WIDTH, 12)
                        .moveByte(CdObjectState.ByteField.Y_RADIUS, 12).moveByte(CdObjectState.ByteField.MAP_FRAME, 5);
            } else if (routine == 8) source = source.moveByte(CdObjectState.ByteField.ANIMATION, 0);
        } else if (invocation.ordinal == 2) {
            if ((invocation.entry.routine() != 0 && invocation.entry.routine() != 2)
                    || invocation.shared.environment().movement().gates().timeZone() != 2 || (source.flags() & 8) == 0)
                throw new IllegalStateException("FloatBlock third Top lacks Main's actual StartFall branch");
            source = source.addByte(CdObjectState.ByteField.ROUTINE, 2);
        } else if (invocation.ordinal != 1 && (invocation.ordinal != 3 || !invocation.falling))
            throw new IllegalStateException("FloatBlock Top ordinal is outside the actual native pair/fall-through");
        return source;
    }

    /** Validate complete live images at native cuts, including same-value stores, no-Draw paths and failed reads. */
    private void validateFloatSource(FloatInvocation invocation, FloatSite site) {
        boolean failure = site == FloatSite.FAILURE;
        var actual = invocation.pool.state(invocation.whole);
        if (failure && matchesFloatPool(invocation, invocation.expectedSource)) return;
        if (invocation.stopped) throw new IllegalStateException("Stopped FloatBlock executed later source work");
        if (site == FloatSite.SOLID) {
            if (invocation.drawn || invocation.bodyVerified)
                throw new IllegalStateException("FloatBlock Top follows its returned body");
            var source = floatSolidSource(invocation);
            acceptFloatPool(invocation, source);
            if (invocation.ordinal == 2) invocation.falling = true;
            return;
        }
        if (invocation.drawn || invocation.bodyVerified) {
            var source = invocation.expectedSource;
            if (!invocation.tailArmed) {
                if (!failure) throw new IllegalStateException("FloatBlock returned before DrawObject returned");
            } else if (floatOutsideWindow(invocation, source)) source = floatDeletedImage(invocation, source);
            acceptFloatPool(invocation, source); return;
        }
        int routine = invocation.entry.routine();
        if ((routine & 1) != 0 || routine > 12)
            throw new IllegalStateException("Unsupported FloatBlock routine changed its native source");
        if (failure && invocation.ordinal == 0 && routine == 0 && invocation.entry.subtype2() != 0) {
            var source = invocation.entry.addByte(CdObjectState.ByteField.ROUTINE, 2)
                    .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, invocation.entry.spriteFlags() | 4)
                    .moveLong(CdObjectState.LongField.MAPPINGS,
                            view(CdR12aProfile.Asset.MAP_SPR_FLOAT_BLOCK).range().nativeAddress());
            acceptFloatPool(invocation, source); return;
        }
        int required = routine == 12 ? 0 : invocation.falling ? 4 : 2;
        if (invocation.ordinal != required) {
            if (failure) { acceptFloatPool(invocation, floatSolidSource(invocation)); return; }
            throw new IllegalStateException("FloatBlock skipped an actual TopSolid pair");
        }
        var source = invocation.expectedSource; boolean draw = true;
        int rawPeriod = invocation.shared.environment().movement().gates().timeZone();
        if (routine == 12) {
            source = source.moveByte(CdObjectState.ByteField.ROUTINE, 2); draw = false;
        } else if (routine == 4 || invocation.falling) {
            source = source.addWord(CdObjectState.WordField.Y, 2);
            if (((invocation.camera.yWord() + 0xE0) & 0xFFFF) < source.centreY()) {
                source = floatDeletedImage(invocation, source); draw = false;
            }
        } else if (routine == 0 || routine == 2) {
            if (rawPeriod != 0 && rawPeriod != 2) {
                source = source.moveByte(CdObjectState.ByteField.MAP_FRAME, 0);
                if ((source.flags() & 8) != 0) source = source.moveByte(CdObjectState.ByteField.ROUTINE, 6)
                        .moveByte(CdObjectState.ByteField.ANIMATION, 1);
            } else if (rawPeriod == 2 && (source.flags() & 8) != 0) {
                if (failure) { acceptFloatPool(invocation, floatSolidSource(invocation)); return; }
                throw new IllegalStateException("FloatBlock Main skipped its native Fall pair");
            }
        } else if (routine == 6 && (source.flags() & 8) == 0) {
            source = source.moveByte(CdObjectState.ByteField.ROUTINE, 2); draw = false;
        } else if (routine == 8 && (source.flags() & 8) == 0) {
            source = source.addByte(CdObjectState.ByteField.ROUTINE, 2).moveByte(CdObjectState.ByteField.ANIMATION, 2);
            draw = false;
        } else {
            source = validateFloatAnimation(invocation, source, failure);
            if (invocation.animationStopped) { acceptFloatPool(invocation, source); return; }
        }
        if (site == FloatSite.DRAW && !draw)
            throw new IllegalStateException("FloatBlock DrawObject is outside its actual native branch");
        if (site == FloatSite.RETURN && draw)
            throw new IllegalStateException("FloatBlock returned without its actual DrawObject");
        if (!draw && floatOutsideWindow(invocation, source)) source = floatDeletedImage(invocation, source);
        acceptFloatPool(invocation, source);
        invocation.bodyVerified = true; invocation.noDraw = !draw;
        if (!draw) invocation.tailArmed = true;
    }

    /**
     * Bounded verification of already-reached Object Animate:14-95 stores over the finite 30-byte owner.
     * This never calls the animator or returns a gameplay State. Missing reads admit only their exact native prefix.
     */
    private CdObjectState validateFloatAnimation(FloatInvocation invocation, CdObjectState source, boolean failure) {
        var expected = source; int animation = source.unsignedByte(CdObjectState.ByteField.ANIMATION);
        if (animation != source.unsignedByte(CdObjectState.ByteField.PREVIOUS_ANIMATION))
            expected = expected.moveByte(CdObjectState.ByteField.PREVIOUS_ANIMATION, animation)
                    .moveByte(CdObjectState.ByteField.ANIMATION_FRAME, 0).moveByte(CdObjectState.ByteField.ANIMATION_TIME, 0);
        expected = expected.addByte(CdObjectState.ByteField.ANIMATION_TIME, -1);
        if (expected.unsignedByte(CdObjectState.ByteField.ANIMATION_TIME) < 0x80) return expected;
        var bytes = view(CdR12aProfile.Asset.ANI_FLOAT_BLOCK).bytes();
        if (animation >= 3) return floatMissingAnimation(invocation, expected, failure);
        int script = (short) bytes.unsignedWord(animation * 2);
        if (script < 0 || script >= bytes.length()) return floatMissingAnimation(invocation, expected, failure);
        expected = expected.moveByte(CdObjectState.ByteField.ANIMATION_TIME, bytes.unsignedByte(script));
        int index = expected.unsignedByte(CdObjectState.ByteField.ANIMATION_FRAME), at = script + 1 + index;
        if (at < 0 || at >= bytes.length()) return floatMissingAnimation(invocation, expected, failure);
        int raw = bytes.unsignedByte(at);
        if (raw == 0xFF) {
            expected = expected.moveByte(CdObjectState.ByteField.ANIMATION_FRAME, 0); index = 0; at = script + 1;
            if (at >= bytes.length()) return floatMissingAnimation(invocation, expected, failure);
            raw = bytes.unsignedByte(at);
        } else if (raw == 0xFE) {
            at = script + 2 + index;
            if (at >= bytes.length()) return floatMissingAnimation(invocation, expected, failure);
            index = (index - bytes.unsignedByte(at)) & 0xFF;
            expected = expected.moveByte(CdObjectState.ByteField.ANIMATION_FRAME, index); at = script + 1 + index;
            if (at >= bytes.length()) return floatMissingAnimation(invocation, expected, failure);
            raw = bytes.unsignedByte(at);
        } else if (raw >= 0x80) {
            if (raw == 0xFD) {
                at = script + 2 + index;
                if (at >= bytes.length()) return floatMissingAnimation(invocation, expected, failure);
                expected = expected.moveByte(CdObjectState.ByteField.ANIMATION, bytes.unsignedByte(at));
            } else if (raw == 0xFC) expected = expected.addByte(CdObjectState.ByteField.ROUTINE, 2);
            else if (raw == 0xFB) expected = expected.moveByte(CdObjectState.ByteField.ANIMATION_FRAME, 0)
                    .moveByte(CdObjectState.ByteField.ROUTINE_2, 0);
            else if (raw == 0xFA) expected = expected.addByte(CdObjectState.ByteField.ROUTINE_2, 2);
            return expected;
        }
        int rotated = ((raw << 3) | (raw >>> 5)) & 0xFF;
        return expected.moveByte(CdObjectState.ByteField.MAP_FRAME, raw & 0x1F)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, (source.spriteFlags() & 0xFC) | ((rotated ^ source.flags()) & 3))
                .moveByte(CdObjectState.ByteField.ANIMATION_FRAME, (index + 1) & 0xFF);
    }
    private CdObjectState floatMissingAnimation(FloatInvocation invocation, CdObjectState prefix, boolean failure) {
        if (!failure) throw new IllegalStateException("FloatBlock advanced past an absent finite animation read");
        invocation.animationStopped = true; return prefix;
    }

    private List<CdObjectState> validateFloatImages(FloatInvocation invocation, CdFloatBlock.PrefixTopSolidCall call,
            CdObjectState source, CdObjectState object, List<CdObjectState> others, Set<CdObjectState.ByteField> writes) {
        if (!source.identity().equals(object.identity()) || source.id() != CdFloatBlock.OBJECT_ID
                || writes.stream().anyMatch(field -> field != CdObjectState.ByteField.SOLID_TYPE && field != CdObjectState.ByteField.FLAGS)
                || !writes.contains(CdObjectState.ByteField.SOLID_TYPE)
                || object.unsignedByte(CdObjectState.ByteField.SOLID_TYPE) > 1 || ((source.flags() ^ object.flags()) & ~8) != 0)
            throw new IllegalStateException("FloatBlock Top changed identity, canonical masks or unowned flag bits");
        byte[] before = source.copyBytes(), after = object.copyBytes();
        for (int at = 0; at < CdObjectState.BYTE_SIZE; at++) {
            int mask = at == 0x25 ? 0xFF : at == 0x22 && writes.contains(CdObjectState.ByteField.FLAGS) ? 8 : 0;
            if (((before[at] ^ after[at]) & ~mask & 0xFF) != 0)
                throw new IllegalStateException("FloatBlock Top changed undeclared a0 byte " + at);
        }
        if (others.size() > 1) throw new IllegalStateException("FloatBlock Top returned extra native stand releases");
        var images = new ArrayList<CdObjectState>(); images.add(object);
        var seen = new HashSet<Integer>(); seen.add(invocation.whole);
        for (var other : others) {
            int whole = other.identity().wholeIndex();
            if (whole < 32 || whole >= 128 || !seen.add(whole) || whole != call.player().standObject())
                throw new IllegalStateException("FloatBlock release lacks its unique actual dynamic stand identity");
            var old = call.callCut().slots().get(whole - 32);
            if (!old.identity().equals(other.identity())
                    || !old.moveByte(CdObjectState.ByteField.FLAGS, old.flags() & ~8).equals(other))
                throw new IllegalStateException("FloatBlock previous-stand release changed bytes beyond BCLR3");
            images.add(other);
        }
        return List.copyOf(images);
    }

    private void validateFloatStopped(CdFloatBlock.PrefixTopSolidCall call, CdSolidObject.NativeStopped stopped,
                                      CdObjectState source) {
        var prefix = stopped.prefix(); var stop = stopped.stop();
        if (prefix.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT || stop.originalException() == null)
            throw new IllegalStateException("FloatBlock stop is not its actual TopSolidObject prefix");
        if (stop.site() == CdSolidObject.StopSite.STAND_RELEASE) {
            if (!(stop.detail() instanceof CdSolidObject.SlotAddress address)
                    || address.routine() != CdSolidObject.Routine.STAND_ON_OBJECT
                    || address.slotByte() != call.player().standObject() || address.slotByte() == 0
                    || address.slotByte() >= 32 && address.slotByte() < 128
                    || address.boundary() != (address.slotByte() < 32 ? CdSolidObject.UnsupportedBoundary.RESERVED_SLOT_ADDRESS
                            : CdSolidObject.UnsupportedBoundary.OUT_OF_POOL_ADDRESS)
                    || prefix.object().unsignedByte(CdObjectState.ByteField.SOLID_TYPE) != 0 || (prefix.object().flags() & 8) == 0
                    || prefix.calls().stream().noneMatch(c -> c instanceof CdSolidObject.RoutineCall entered
                            && entered.routine() == CdSolidObject.Routine.STAND_ON_OBJECT))
                throw new IllegalStateException("FloatBlock stop lacks its actual previous-stand dereference prefix");
        } else if (stop.detail() instanceof CdSolidObject.SlotAddress
                || prefix.object().unsignedByte(CdObjectState.ByteField.SOLID_TYPE) != 1
                || prefix.object().flags() != source.flags())
            throw new IllegalStateException("FloatBlock terrain stop crossed the native pre-StandOn object prefix");
    }

    /** Prepare the full96, existing Frame publication, immutable sound ledger and call trace before any live write. */
    private void commitFloatSolid(CdFloatBlock.PrefixTopSolidCall call) {
        Objects.requireNonNull(call);
        var invocation = requireFloatInvocation(placement.pool(), activeFloat == null ? -1 : activeFloat.whole);
        if (!floatBlock.isCurrentPrefixTopSolidCall(call,invocation.ordinal)) throw new IllegalStateException("Float Top lacks its actual pending producer call");
        if (floatPort || drawingFloat || queuePort || invocation.stopped || invocation.drawn
                || invocation.ordinal >= 4 || call.callCut() == invocation.lastCut || call.outcome() == invocation.adoptedOutcome)
            throw new IllegalStateException("FloatBlock Top lacks its next actual private helper cut");
        validateFloatSource(invocation, FloatSite.SOLID);
        var pool = invocation.pool; var cut = call.callCut(); pool.validateRestore(cut);
        if (!cut.slots().equals(invocation.currentPool.slots()) || !pool.capture().slots().equals(cut.slots())
                || !call.player().equals(springPlayer()) || !call.globals().equals(springGlobals()))
            throw new IllegalStateException("FloatBlock Top differs from its actual full96/player/global cut");
        var source = cut.slots().get(invocation.whole - 32);
        CdObjectState object; List<CdObjectState> others; Set<CdObjectState.ByteField> writes;
        List<CdPlayerTouchResponse.FmSound> requests; CdPlayerFrameRuntime.ObjectSolidPublication readyFrame;
        boolean stopped; var completed = new ArrayList<>(invocation.completed);
        if (call.outcome() instanceof CdSolidObject.Completed done) {
            var result = done.completedResult();
            if (result.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT
                    || result.path() != CdSolidObject.Path.GET_OFF && result.path() != CdSolidObject.Path.STAND_ON
                    || result.object().unsignedByte(CdObjectState.ByteField.SOLID_TYPE) != 0)
                throw new IllegalStateException("FloatBlock completed a different native helper/path");
            object = result.object(); others = result.otherSlots(); writes = result.objectWrites(); requests = result.fmRequests();
            readyFrame = frame.prepareObjectSolidResult(call.player(), call.globals(), result);
            completed.add(result); stopped = false;
        } else {
            var nativeStop = (CdSolidObject.NativeStopped) call.outcome(); validateFloatStopped(call, nativeStop, source);
            var prefix = nativeStop.prefix();
            object = prefix.object(); others = prefix.otherSlots(); writes = prefix.objectWrites(); requests = prefix.fmRequests();
            readyFrame = frame.prepareObjectSolidPrefix(call.player(), call.globals(), prefix); stopped = true;
        }
        var images = validateFloatImages(invocation, call, source, object, others, writes);
        var readyPool = CdObjectPool.overlay(cut, images); pool.validateRestore(readyPool);
        var readyActor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), readyFrame.finalState(), invocation.actor.display());
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), readyFrame.finalEnvironment(),
                invocation.shared.artState(), invocation.shared.paused(), invocation.shared.vIntClock());
        var ledger = new ArrayList<>(sounds);
        for (var fm : requests) ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
        var readySounds = List.copyOf(ledger); var readyValues = preparedFloatValues(FLOAT_BLOCK_SOLID);
        var readyCompleted = List.copyOf(completed); int readyOrdinal = invocation.ordinal + 1;
        floatPort = true;
        try {
            verifyFloatInvocation(invocation);
            if (!pool.capture().slots().equals(cut.slots()) || !sameBytes(placement.capture().savedFlags(), invocation.savedFlags))
                throw new IllegalStateException("FloatBlock full96/saved source changed before publication");
            frame.validatePreparedObjectSolidPublication(readyFrame);
            pool.restore(readyPool);
            frame.applyPreparedObjectSolidPublication(readyFrame);
            sounds = readySounds; values = readyValues;
            invocation.actor = readyActor; invocation.shared = readyShared; invocation.expectedSounds = readySounds;
            invocation.expectedValues = readyValues; invocation.currentPool = readyPool; invocation.expectedSource = object;
            invocation.ordinal = readyOrdinal; invocation.completed = readyCompleted; invocation.stopped = stopped;
            invocation.lastCut = cut; invocation.adoptedOutcome = call.outcome();
            verifyFloatInvocation(invocation);
            if (!pool.capture().slots().equals(readyPool.slots()))
                throw new IllegalStateException("FloatBlock Top did not publish its exact complete output96");
        } catch (RuntimeException | Error failure) {
            verifyFloatFailure(failure); throw failure;
        } finally { floatPort = false; }
    }

    private Values preparedFloatValues(SonicCdGameModule.Act2LoadCall call) {
        var old = values; var calls = new ArrayList<>(old.calls()); calls.add(call);
        return new Values(old.phase(), old.section(), old.cpuPalette(), old.fadePalette(), old.waterPalette(), old.displayedPalette(), old.cpuSat(),
                old.displayedSat(), old.displayedHScroll(), old.displayedVScrollLongBits(), old.foregroundPlane(), old.ring(),
                old.level(), old.globals(), old.hudObjects(), old.fadeProgress(), List.copyOf(calls), old.loop());
    }

    private void verifyFloatCallback(FloatInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                     CdObjectState cleared) {
        verifyFloatInvocation(invocation);
        var now = placement.capture(); var expected = new ArrayList<>(before.pool().slots());
        if (cleared != null) expected.set(invocation.whole - 32, cleared);
        if (!now.status().equals(before.status()) || !now.pool().slots().equals(expected)
                || !sameBytes(now.savedFlags(), before.savedFlags())
                || !now.pool().slots().equals(invocation.currentPool.slots()))
            throw new IllegalStateException("FloatBlock callback changed full96/bank outside its actual bit7 clear");
    }
    private void verifyFloatCallbackFailure(FloatInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                            CdObjectState cleared, Throwable original) {
        try { verifyFloatCallback(invocation, before, cleared); }
        catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private void drawFloatBlock(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireFloatInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (floatPort || drawingFloat || queuePort || invocation.drawn || invocation.stopped
                || !identity.equals(invocation.entry.identity()))
            throw new IllegalStateException("FloatBlock DrawObject lacks its actual once-only native source");
        if (!floatBlock.isCurrentDrawCall(pool,identity)) throw new IllegalStateException("Float Draw lacks its actual producer source prefix");
        validateFloatSource(invocation, FloatSite.DRAW);
        var before = placement.capture(); var current = invocation.expectedSource;
        var cleared = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        invocation.drawn = true; drawingFloat = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(),
                    current.unsignedByte(CdObjectState.ByteField.Y_RADIUS), current.unsignedByte(CdObjectState.ByteField.WIDTH),
                    current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            if (!invocation.screenCleared) throw new IllegalStateException("FloatBlock DrawObject did not clear bit7 once");
            verifyFloatCallback(invocation, before, cleared); invocation.tailArmed = true;
        } catch (RuntimeException | Error failure) {
            verifyFloatCallbackFailure(invocation, before, invocation.screenCleared ? cleared : null, failure); throw failure;
        } finally { drawingFloat = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readFloatDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireFloatInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingFloat || floatPort || queuePort) throw new IllegalStateException("No actual FloatBlock queue read");
        var before = placement.capture(); queuePort = true;
        try {
            if (!before.pool().slots().equals(invocation.currentPool.slots()))
                throw new IllegalStateException("FloatBlock queue read lost its exact live96");
            var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(), current.centreX(), current.centreY(),
                    current.word(CdObjectState.WordField.X_FRACTION), current.longBits(CdObjectState.LongField.MAPPINGS),
                    current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifyFloatCallback(invocation, before, null); return live;
        } catch (RuntimeException | Error failure) {
            verifyFloatCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { queuePort = false; }
    }
    private void writeFloatOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireFloatInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingFloat || floatPort || queuePort || onScreen || invocation.screenCleared)
            throw new IllegalStateException("Only actual FloatBlock DrawObject may clear bit7 once");
        var before = placement.capture(); var current = invocation.pool.state(invocation.whole);
        if (!before.pool().slots().equals(invocation.currentPool.slots()) || !current.equals(invocation.expectedSource))
            throw new IllegalStateException("FloatBlock bit7 store lost its complete current source");
        var next = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        var readyPool = CdObjectPool.overlay(before.pool(), List.of(next)); invocation.pool.validateRestore(readyPool);
        queuePort = true;
        try {
            invocation.pool.write(next); invocation.screenCleared = true;
            invocation.currentPool = readyPool; invocation.expectedSource = next;
            verifyFloatCallback(invocation, before, next);
        } catch (RuntimeException | Error failure) {
            verifyFloatCallbackFailure(invocation, before, invocation.screenCleared ? next : null, failure); throw failure;
        } finally { queuePort = false; }
    }

    /** Read-only completed metadata check, tied to the actual private helper results; never replay effects. */
    private void verifyFloatResult(FloatInvocation invocation, CdFloatBlock.Result result) {
        var expected = new ArrayList<CdFloatBlock.Effect>();
        int routine = invocation.entry.routine();
        expected.add(new CdFloatBlock.RoutineEntered(CdFloatBlock.NativeRoutine.values()[routine / 2]));
        if (routine == 0) expected.add(new CdFloatBlock.RoutineEntered(CdFloatBlock.NativeRoutine.MAIN));
        for (int i = 0; i < invocation.completed.size(); i++) {
            if (i == 2) expected.add(new CdFloatBlock.RoutineEntered(CdFloatBlock.NativeRoutine.FALL));
            expected.add(new CdFloatBlock.TopSolid(i, invocation.completed.get(i)));
        }
        if (routine == 6 && !invocation.noDraw || routine == 8 && !invocation.noDraw || routine == 10)
            expected.add(new CdFloatBlock.CallReturned(CdFloatBlock.NativeCall.ANIMATE_OBJECT));
        if (invocation.drawn) expected.add(new CdFloatBlock.CallReturned(CdFloatBlock.NativeCall.DRAW_OBJECT));
        // Deletion authority came from the checked live Y/window branch, never this returned metadata.
        var source = invocation.expectedSource;
        boolean below = (routine == 4 || invocation.falling)
                && ((invocation.camera.yWord() + 0xE0) & 0xFFFF) < ((invocation.entry.centreY() + 2) & 0xFFFF);
        if (below) expected.add(new CdFloatBlock.Deleted(CdFloatBlock.DeleteSite.FALL_BELOW_CAMERA));
        int x = below ? 0 : invocation.entry.centreX();
        if ((((x & 0xFF80) - ((invocation.camera.xWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280)
            expected.add(new CdFloatBlock.Deleted(CdFloatBlock.DeleteSite.CHUNK_WINDOW));
        if (!result.effects().equals(expected) || !result.state().equals(source)
                || invocation.ordinal != invocation.completed.size() || !invocation.noDraw && !invocation.drawn)
            throw new IllegalStateException("FloatBlock returned metadata without its actual complete native call grammar");
        int at = 0;
        for (var effect : result.effects()) if (effect instanceof CdFloatBlock.TopSolid top
                && top.result() != invocation.completed.get(at++))
            throw new IllegalStateException("FloatBlock returned a substituted completed helper observation");
    }



    private final class CheckpointInvocation {
        final CdCheckpoint body=checkpoint;
        final CdPlayerFrameRuntime sourceFrame=frame;
        final CdR12aTerrainAssets sourceAssets=assets;
        final Object sourceStage=frame.stageSource();
        final CdObjectPool pool=placement.pool();
        final CdObjectDrawQueue queue=drawQueue;
        final CdObjectPlacementRuntime.Snapshot before=placement.capture();
        final CdObjectState entry;
        final CdPlayerFrameRuntime.ActorState actor=frame.captureActor();
        final CdPlayerFrameRuntime.SharedState shared=frame.captureShared();
        final SonicCdPlayerPresentation.ScrollCapture scroll=presentation.captureAct2Scroll();
        final Values base=values;
        final Object world;
        final long generation;
        final int whole;
        final CdObjectDrawQueue.CameraWords camera;
        final Set<CdCheckpoint.LiveCall> consumed=Collections.newSetFromMap(new IdentityHashMap<>());
        List<CdCheckpoint.CompletedLiveCall> receipts=List.of();
        Values expectedValues=values;
        List<SonicCdGameModule.PastSound> expectedSounds=sounds;
        int globalStores;
        Integer parentByte;
        boolean savedCompleted, sounded, drawn, screenCleared;
        CdCheckpointSaveData.State completedSave;
        CdObjectState drawSource;
        CdObjectPool.Snapshot drawPool;
        CheckpointInvocation(int whole) {
            this.whole=whole;entry=pool.state(whole);
            var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world=mode.getWorldSession();generation=mode.getLevelManager().getCompletedProductionLoadGeneration();
            var f=presentation.act2ForegroundImage();camera=new CdObjectDrawQueue.CameraWords(f.cameraX(),f.cameraYWord());
        }
    }
    private void runCheckpoint(int whole) {
        requireDoorIdle();
        requireLogMosquiIdle();
        requireBoulderPlatformIdle();module.requireAct2LoadingVisit(this);
        if(!runningObjects || servicing || activePrimary!=null || activeDrain!=null || activeTitle!=null || titlePort || drawingTitle
                || activeReserved!=null || activeRing!=null || activeSpring!=null || activeMonitor!=null || monitorPort || drawingMonitor
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring || queuePort
                || whole<32 || whole>=128 || placement.pool().state(whole).id()!=CdCheckpoint.OBJECT_ID
                || values.phase()!=BEGIN_LEVEL || drawQueue==null || drawQueue.phase()!=CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Act2 Checkpoint dispatch");
        var i=new CheckpointInvocation(whole);activeCheckpoint=i;
        try {
            var gates=frame.environment().movement().gates();
            var result=checkpoint.run(whole,new CdCheckpoint.DespawnGlobals(gates.timeZone(),gates.timeWarpDirection(),i.camera.xWord()));
            verifyCheckpointInvocation(i);validateCheckpointMutation(i,false);
            boolean contact=i.entry.routine()==2 && i.entry.unsignedByte(CdObjectState.ByteField.CHECKPOINT_ACTIVE)==0
                    && i.entry.unsignedByte(CdObjectState.ByteField.COLLISION_STATUS)!=0;
            var stores=result.effects().stream().filter(CdCheckpoint.GlobalStored.class::isInstance).map(CdCheckpoint.GlobalStored.class::cast).toList();
            if(!result.state().equals(i.pool.state(whole)) || !i.drawn || !i.screenCleared
                    || i.globalStores!=(contact?2:0) || stores.size()!=i.globalStores
                    || i.savedCompleted!=contact || i.sounded!=contact)
                throw new IllegalStateException("Checkpoint omitted an actual ordered global/save/FM/Draw publication");
            for(var stored:stores)if(i.receipts.stream().noneMatch(r->r.call() instanceof CdCheckpoint.GlobalStoreCall c && c.store()==stored.call()))
                throw new IllegalStateException("Checkpoint substituted a completed global observation");
        } catch(RuntimeException|Error failure) {
            try { verifyCheckpointInvocation(i);validateCheckpointMutation(i,true); }
            catch(RuntimeException|Error verification){if(verification!=failure)failure.addSuppressed(verification);}
            throw failure;
        } finally {activeCheckpoint=null;}
    }
    private void verifyCheckpointInvocation(CheckpointInvocation i) {
        module.requireAct2LoadingVisit(this);
        var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if(i==null || activeCheckpoint!=i || !runningObjects || servicing || activePrimary!=null || activeDrain!=null
                || activeReserved!=null || activeRing!=null || activeSpring!=null || activeMonitor!=null || monitorPort || drawingMonitor
                || activeTitle!=null || titlePort || drawingTitle || activeCollapse!=null || collapsePort || drawingCollapse || activeBoulder!=null || boulderPort || drawingBoulder
                || activePlatform!=null || platformPort || drawingPlatform || activeAnton!=null || antonPort || drawingAnton
                || activePata!=null || drawingPata || activeTamabboh!=null || tamabbohPort || drawingTamabboh || activeFloat!=null || floatPort || drawingFloat
                || activeWall!=null || wallPort || drawingWall
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || i.body!=checkpoint || i.sourceFrame!=frame || i.sourceAssets!=assets || i.sourceStage!=frame.stageSource()
                || i.pool!=placement.pool() || i.queue!=drawQueue || drawQueue==null || drawQueue.phase()!=CdObjectDrawQueue.Phase.COLLECTING
                || i.world!=mode.getWorldSession() || i.generation!=mode.getLevelManager().getCompletedProductionLoadGeneration()
                || i.expectedValues!=values || values.phase()!=BEGIN_LEVEL || i.expectedSounds!=sounds
                || !frame.captureActor().equals(i.actor) || !frame.captureShared().equals(i.shared)
                || !placement.status().equals(i.before.status()) || !presentation.sameAct2Scroll(i.scroll)
                || !sameBytes(values.hudObjects(),i.base.hudObjects()))
            throw new IllegalStateException("Checkpoint changed another owner or retained a stale full World/Frame cut");
    }
    private CdObjectState checkpointHeader(CheckpointInvocation i) {
        return i.entry.addByte(CdObjectState.ByteField.ROUTINE,2).moveLong(CdObjectState.LongField.MAPPINGS,CdR12aProfile.Asset.MAP_SPR_CHECKPOINT.nativeAddress())
                .moveWord(CdObjectState.WordField.TILE,0x6CB).moveByte(CdObjectState.ByteField.SPRITE_FLAGS,4).moveByte(CdObjectState.ByteField.WIDTH,8)
                .moveByte(CdObjectState.ByteField.Y_RADIUS,24).moveByte(CdObjectState.ByteField.PRIORITY,4);
    }
    private CdObjectState checkpointMainSource(CheckpointInvocation i) {
        return i.entry.unsignedByte(CdObjectState.ByteField.CHECKPOINT_ACTIVE)==0 && i.entry.unsignedByte(CdObjectState.ByteField.COLLISION_STATUS)!=0
                ?i.entry.moveByte(CdObjectState.ByteField.COLLISION_TYPE,0).moveByte(CdObjectState.ByteField.CHECKPOINT_ACTIVE,1):i.entry;
    }
    private CdObjectState checkpointInitSource(CheckpointInvocation i) {
        var source=checkpointHeader(i);
        return i.base.level().checkpoint()>=source.subtype()?source.moveByte(CdObjectState.ByteField.CHECKPOINT_ACTIVE,1)
                :source.moveByte(CdObjectState.ByteField.COLLISION_TYPE,0xE3);
    }
    private CdObjectState checkpointChild(CheckpointInvocation i,CdObjectState dirty) {
        var parent=checkpointInitSource(i);
        return dirty.moveByte(CdObjectState.ByteField.ID,0x13).addByte(CdObjectState.ByteField.ROUTINE,parent.unsignedByte(CdObjectState.ByteField.CHECKPOINT_ACTIVE)!=0?6:4)
                .moveLong(CdObjectState.LongField.MAPPINGS,CdR12aProfile.Asset.MAP_SPR_CHECKPOINT.nativeAddress()).moveWord(CdObjectState.WordField.TILE,0x6CB)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS,4).moveByte(CdObjectState.ByteField.WIDTH,8).moveByte(CdObjectState.ByteField.Y_RADIUS,8)
                .moveByte(CdObjectState.ByteField.PRIORITY,3).moveByte(CdObjectState.ByteField.MAP_FRAME,1)
                .moveLong(CdObjectState.LongField.CHECKPOINT_PARENT,0xFFFFD000+i.whole*64).moveWord(CdObjectState.WordField.X,parent.centreX())
                .moveWord(CdObjectState.WordField.Y,parent.centreY()-32).moveWord(CdObjectState.WordField.CHECKPOINT_BALL_X,parent.centreX())
                .moveWord(CdObjectState.WordField.CHECKPOINT_BALL_Y,parent.centreY()-24);
    }
    private void validateCheckpointOtherImages(CheckpointInvocation i,boolean failure) {
        var expected=new ArrayList<>(i.before.pool().slots());
        if(i.entry.routine()==0) {
            var first=i.before.pool().slots().stream().filter(x->x.id()==0).findFirst();
            if(first.isPresent()) {
                var old=first.orElseThrow();var child=checkpointChild(i,old);var actual=i.pool.state(old.identity().wholeIndex());
                if(!actual.equals(child) && !(actual.equals(old)&&(!i.drawn||failure)))
                    throw new IllegalStateException("Checkpoint changed first-free dirty bytes outside the native child stores");
                expected.set(old.identity().wholeIndex()-32,actual);
            }
        }
        expected.set(i.whole-32,i.pool.state(i.whole));
        if(!i.pool.capture().slots().equals(expected))throw new IllegalStateException("Checkpoint changed an unrelated full96 row");
    }
    private CheckpointInvocation preflightCheckpoint(CdCheckpoint.LiveCall call) {
        Objects.requireNonNull(call);var i=activeCheckpoint;verifyCheckpointInvocation(i);
        if(checkpointPort || drawingCheckpoint || queuePort || i.drawn || !checkpoint.isCurrentLiveCall(call) || i.consumed.contains(call)
                || !checkpoint.matchesCurrentLivePrefix(i.receipts))
            throw new IllegalStateException("Checkpoint callback lacks the exact unconsumed producer and completed prefix");
        var cut=call.cut();i.pool.validateRestore(cut.pool());
        if(cut.source().identity().wholeIndex()!=i.whole || !cut.source().equals(i.pool.state(i.whole))
                || !cut.pool().slots().equals(i.pool.capture().slots()) || !sameBytes(placement.capture().savedFlags(),i.before.savedFlags()))
            throw new IllegalStateException("Checkpoint callback changed its actual source/full96/bank cut");
        var expected=i.entry.routine()==0?checkpointHeader(i):i.entry.routine()==2?checkpointMainSource(i):i.entry;
        if(!cut.source().equals(expected))throw new IllegalStateException("Checkpoint callback crossed its literal reached source prefix");
        validateCheckpointOtherImages(i,false);return i;
    }
    private List<CdCheckpoint.CompletedLiveCall> checkpointReceipts(CheckpointInvocation i,CdCheckpoint.LiveCall call,Object value) {
        var list=new ArrayList<>(i.receipts);list.add(new CdCheckpoint.CompletedLiveCall(call,value));return List.copyOf(list);
    }
    private Values checkpointValues(CdLevelGlobals level,LoadingGlobals globals,SonicCdGameModule.Act2LoadCall call) {
        var o=values;var calls=new ArrayList<>(o.calls());calls.add(call);
        return new Values(o.phase(),o.section(),o.cpuPalette(),o.fadePalette(),o.waterPalette(),o.displayedPalette(),o.cpuSat(),o.displayedSat(),
                o.displayedHScroll(),o.displayedVScrollLongBits(),o.foregroundPlane(),o.ring(),level,globals,o.hudObjects(),o.fadeProgress(),List.copyOf(calls),o.loop());
    }
    private void verifyCheckpointCallback(CheckpointInvocation i,CdObjectPlacementRuntime.Snapshot before) {
        verifyCheckpointInvocation(i);var now=placement.capture();
        if(!now.pool().slots().equals(before.pool().slots()) || !sameBytes(now.savedFlags(),before.savedFlags()) || !now.status().equals(before.status()))
            throw new IllegalStateException("Checkpoint read/global/save/FM changed full96 or object saved bytes");
    }
    private void checkpointCallbackFailure(CheckpointInvocation i,CdObjectPlacementRuntime.Snapshot before,Throwable original) {
        try{verifyCheckpointCallback(i,before);}catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}
    }
    private CdLevelGlobals readCheckpointLevel(CdCheckpoint.LevelReadCall call) {
        var i=preflightCheckpoint(call);var before=placement.capture();var value=values.level();
        if(i.entry.routine()!=0 && i.entry.routine()!=2)throw new IllegalStateException("No reached Checkpoint level read");
        var receipts=checkpointReceipts(i,call,value);var ready=checkpointValues(value,values.globals(),CHECKPOINT_LEVEL_READ);
        checkpointPort=true;try {verifyCheckpointCallback(i,before);i.consumed.add(call);values=ready;i.expectedValues=ready;i.receipts=receipts;verifyCheckpointCallback(i,before);return value;}
        catch(RuntimeException|Error fault){checkpointCallbackFailure(i,before,fault);throw fault;}finally{checkpointPort=false;}
    }
    private void requireCheckpointContact(CheckpointInvocation i) {
        var s=i.pool.state(i.whole);
        if(i.entry.routine()!=2 || i.entry.unsignedByte(CdObjectState.ByteField.CHECKPOINT_ACTIVE)!=0 || i.entry.unsignedByte(CdObjectState.ByteField.COLLISION_STATUS)==0
                || !s.equals(checkpointMainSource(i)) || s.unsignedByte(CdObjectState.ByteField.CHECKPOINT_ACTIVE)!=1 || s.unsignedByte(CdObjectState.ByteField.COLLISION_TYPE)!=0)
            throw new IllegalStateException("Checkpoint contact source is not its native Main prefix");
    }
    private void storeCheckpointGlobal(CdCheckpoint.GlobalStoreCall call) {
        var i=preflightCheckpoint(call);requireCheckpointContact(i);var store=call.store();var g=values.level();
        var expected=i.globalStores==0?new CdLevelGlobals(g.levelRestart(),g.updateHudTime(),i.entry.subtype(),g.spawnMode(),g.plcLoadFlags(),g.flowerCount(),g.fadeEnableDisplay(),g.powerup(),g.timeOver())
                :new CdLevelGlobals(g.levelRestart(),g.updateHudTime(),g.checkpoint(),1,g.plcLoadFlags(),g.flowerCount(),g.fadeEnableDisplay(),g.powerup(),g.timeOver());
        if(i.globalStores>1 || i.savedCompleted || i.sounded || !store.expectedSourceObject().equals(call.cut().source()) || !store.expectedLevel().equals(g)
                || !store.finalLevel().equals(expected) || store.field()!=(i.globalStores==0?CdCheckpoint.GlobalField.CHECKPOINT:CdCheckpoint.GlobalField.SPAWN_MODE))
            throw new IllegalStateException("Checkpoint requires its two distinct addressed level stores in order");
        var before=placement.capture();var receipts=checkpointReceipts(i,call,null);var ready=checkpointValues(expected,values.globals(),CHECKPOINT_GLOBAL);
        checkpointPort=true;try{verifyCheckpointCallback(i,before);i.consumed.add(call);values=ready;i.expectedValues=ready;i.receipts=receipts;i.globalStores++;verifyCheckpointCallback(i,before);}
        catch(RuntimeException|Error fault){checkpointCallbackFailure(i,before,fault);throw fault;}finally{checkpointPort=false;}
    }
    private CdCheckpointSaveData.State readCheckpointSaved(CdCheckpoint.SavedReadCall call) {
        var i=preflightCheckpoint(call);requireCheckpointContact(i);
        if(i.globalStores!=2)throw new IllegalStateException("Checkpoint saved read precedes completed level stores");
        var before=placement.capture();var value=values.globals().checkpointSaved();var receipts=checkpointReceipts(i,call,value);
        var ready=checkpointValues(values.level(),values.globals(),CHECKPOINT_SAVED_READ);
        checkpointPort=true;try{verifyCheckpointCallback(i,before);i.consumed.add(call);values=ready;i.expectedValues=ready;i.receipts=receipts;verifyCheckpointCallback(i,before);return value;}
        catch(RuntimeException|Error fault){checkpointCallbackFailure(i,before,fault);throw fault;}finally{checkpointPort=false;}
    }


    private CdCheckpointSaveData.Result saveCheckpoint(CdCheckpoint.SaveDataCall call) {
        var i=preflightCheckpoint(call);requireCheckpointContact(i);var save=call.save();
        if(i.globalStores!=2 || i.savedCompleted || i.sounded || !save.expectedSourceObject().equals(call.cut().source())
                || !save.expectedLevel().equals(values.level()) || !save.expectedSaved().equals(values.globals().checkpointSaved()))
            throw new IllegalStateException("Checkpoint save lacks its actual complete level/source/saved preimage");
        var before=placement.capture();var live=values.globals().live();var level=values.level();
        class SavePort implements CdCheckpointSaveData.Port {
            final CdCheckpointSaveData.Store[] stores=CdCheckpointSaveData.Store.values();
            CdCheckpointSaveData.State published=save.expectedSaved();
            int cursor,reachedRaw;boolean awaiting;
            void verify() {
                verifyCheckpointCallback(i,before);
                if(!checkpointPort || !checkpoint.isCurrentLiveCall(call) || !published.equals(values.globals().checkpointSaved())
                        || !level.equals(values.level()) || !live.equals(values.globals().live()))
                    throw new IllegalStateException("Checkpoint save lost its actual pending producer/source/live/saved prefix");
            }
            @Override public CdCheckpointSaveData.State currentState(){verify();return values.globals().checkpointSaved();}
            @Override public int read(CdCheckpointSaveData.Source source) {
                verify();if(cursor>=stores.length || awaiting || source!=stores[cursor].source())
                    throw new IllegalStateException("Checkpoint source read differs from the native save cursor");
                int value=switch(source) {
                    case SPAWN_MODE -> values.level().spawnMode();
                    case PRIMARY_X -> frame.state().motion().centreX16_16()>>>16;
                    case PRIMARY_Y -> frame.state().motion().centreY16_16()>>>16;
                    case BOTTOM_BOUND -> frame.environment().movement().bounds().bottom();
                    case MINI_SONIC -> frame.environment().movement().gates().miniSonic();
                    case WATER_ROUTINE -> CdCheckpointLiveAux.read(values.globals().live(),CdCheckpointLiveAux.Field.WATER_ROUTINE);
                    case WATER_HEIGHT2 -> CdCheckpointLiveAux.read(values.globals().live(),CdCheckpointLiveAux.Field.WATER_HEIGHT2);
                    case WATER_FULLSCREEN -> CdCheckpointLiveAux.read(values.globals().live(),CdCheckpointLiveAux.Field.WATER_FULLSCREEN);
                    case TIME_LONG -> CdCheckpointLiveAux.read(values.globals().live(),CdCheckpointLiveAux.Field.TIME_LONG);
                    case FG_X,FG_Y,BG_X,BG_Y,BG2_X,BG2_Y,BG3_X,BG3_Y -> checkpointCameraWord(source);
                };
                checkpointWidth(value,source.width());verify();reachedRaw=value;awaiting=true;return value;
            }
            @Override public void commit(CdCheckpointSaveData.StoreCall store) {
                verify();if(cursor>=stores.length || !awaiting || store.store()!=stores[cursor] || !published.equals(store.expectedState()))
                    throw new IllegalStateException("Checkpoint saved store differs from its reached source/state cursor");
                int raw=reachedRaw;
                if(stores[cursor]==CdCheckpointSaveData.Store.TIME_LONG && Integer.compareUnsigned(raw,0x00050000)>=0)raw=0x00050000;
                validateCheckpointSavedStore(store,raw);
                var globals=values.globals().withCheckpointSaved(store.finalState());var ready=checkpointValues(values.level(),globals,CHECKPOINT_SAVE_STORE);
                verify();values=ready;i.expectedValues=ready;published=store.finalState();cursor++;awaiting=false;verify();
            }
            void finish(CdCheckpointSaveData.Result result) {
                verify();if(cursor!=stores.length || awaiting || result==null || !result.state().equals(published)
                        || !result.completedStores().equals(List.of(stores)))throw new IllegalStateException("Checkpoint save did not finish exactly eighteen native stores");
            }
        }
        var port=new SavePort();checkpointPort=true;
        try {
            verifyCheckpointCallback(i,before);i.consumed.add(call);
            var result=CdCheckpointSaveData.save(save.expectedSaved(),port);port.finish(result);
            var receipts=checkpointReceipts(i,call,result);var ready=checkpointValues(values.level(),values.globals(),CHECKPOINT_SAVE);
            verifyCheckpointCallback(i,before);values=ready;i.expectedValues=ready;i.receipts=receipts;i.savedCompleted=true;i.completedSave=result.state();
            verifyCheckpointCallback(i,before);return result;
        } catch(RuntimeException|Error fault){checkpointCallbackFailure(i,before,fault);throw fault;}finally{checkpointPort=false;}
    }
    /** Same addressed-byte/knowledge verifier as the existing opening checkpoint bridge; never runs the save helper again. */
    private static void validateCheckpointSavedStore(CdCheckpointSaveData.StoreCall call,int raw) {
        if(call.rawValue()!=raw)throw new IllegalArgumentException("Checkpoint saved store changed its actual source/clamp value");
        checkpointWidth(raw,call.store().width());int[] starts={0xFF1523,0xFF152A,0xFF1530,0xFF1591};int[] lengths={5,4,21,1};
        int first=call.store().nativeAddress(),end=first+call.store().width();
        for(int span=0;span<starts.length;span++)for(int address=starts[span];address<starts[span]+lengths[span];address++) {
            var expected=address>=first&&address<end?OptionalInt.of((raw>>>(8*(end-address-1)))&255):call.expectedState().knownByte(address);
            if(!expected.equals(call.finalState().knownByte(address)))throw new IllegalArgumentException("Checkpoint changed an unaddressed saved value/knowledge byte");
        }
    }
    private static void checkpointWidth(int value,int width) {
        if(width==1&&(value&~255)!=0 || width==2&&(value&~65535)!=0)throw new IllegalArgumentException("Checkpoint native source width");
    }
    private int checkpointCameraWord(CdCheckpointSaveData.Source source) {
        var f=presentation.act2ForegroundImage();var b=presentation.act2BackgroundImage();
        return switch(source) {
            case FG_X -> f.cameraX();case FG_Y -> f.cameraYWord();case BG_X -> b.cameraBgXWord();case BG_Y -> b.cameraBgYWord();
            case BG2_X -> b.cameraBg2XWord();case BG2_Y -> b.cameraBg2YWord();case BG3_X -> b.cameraBg3XWord();case BG3_Y -> b.cameraBg3YWord();
            default -> throw new IllegalArgumentException("Not a checkpoint camera source");
        };
    }
    private void playCheckpointFm(CdCheckpoint.FmRequestCall call) {
        var i=preflightCheckpoint(call);requireCheckpointContact(i);
        if(i.globalStores!=2 || !i.savedCompleted || i.sounded || i.completedSave==null || !i.completedSave.equals(values.globals().checkpointSaved())
                || call.sound()!=CdCheckpoint.FmSound.FM_CHECKPOINT_AE)throw new IllegalStateException("Checkpoint FM precedes its actual completed save");
        var before=placement.capture();var ledger=new ArrayList<>(sounds);ledger.add(new SonicCdGameModule.PastCheckpointFm(call.sound()));
        var readySounds=List.copyOf(ledger);var receipts=checkpointReceipts(i,call,null);var ready=checkpointValues(values.level(),values.globals(),CHECKPOINT_FM);
        checkpointPort=true;try{verifyCheckpointCallback(i,before);i.consumed.add(call);values=ready;i.expectedValues=ready;sounds=readySounds;i.expectedSounds=readySounds;i.receipts=receipts;i.sounded=true;verifyCheckpointCallback(i,before);}
        catch(RuntimeException|Error fault){checkpointCallbackFailure(i,before,fault);throw fault;}finally{checkpointPort=false;}
    }
    private int readCheckpointParent(CdCheckpoint.ParentByteCall call) {
        var i=preflightCheckpoint(call);
        if(i.entry.routine()!=4 || i.entry.unsignedByte(CdObjectState.ByteField.CHECKPOINT_ACTIVE)!=0 || i.parentByte!=null
                || call.rawAddressBits()!=i.entry.longBits(CdObjectState.LongField.CHECKPOINT_PARENT)+0x2E)
            throw new IllegalStateException("Checkpoint parent read differs from its full wrapping native address");
        var before=placement.capture();int value=checkpointAddressedByte(call.rawAddressBits());checkpointWidth(value,1);
        var receipts=checkpointReceipts(i,call,value);var ready=checkpointValues(values.level(),values.globals(),CHECKPOINT_PARENT_READ);
        checkpointPort=true;try{verifyCheckpointCallback(i,before);i.consumed.add(call);values=ready;i.expectedValues=ready;i.receipts=receipts;i.parentByte=value;verifyCheckpointCallback(i,before);return value;}
        catch(RuntimeException|Error fault){checkpointCallbackFailure(i,before,fault);throw fault;}finally{checkpointPort=false;}
    }
    /** Resolve an addressed existing owner, never an object ID, alignment assumption or synthetic slot/RAM image. */
    private int checkpointAddressedByte(int raw) {
        int bus=raw&0xFFFFFF;
        if(bus>=0xFFD000&&bus<0xFFF000) {
            int displacement=bus-0xFFD000,whole=displacement>>>6,offset=displacement&63;
            if(whole>=32)return Byte.toUnsignedInt(placement.pool().state(whole).copyBytes()[offset]);
            if(whole>0)return reservedImage(whole).unsignedByte(offset);
            var s=frame.state();var m=s.motion();var h=s.header();
            return switch(offset) {
                case 0 -> h.slotId();case 1 -> m.spriteFlags();case 2 -> h.tile()>>>8;case 3 -> h.tile()&255;
                case 4,5,6,7 -> (h.mappingAddress()>>>(8*(7-offset)))&255;
                case 8,9,10,11 -> (m.centreX16_16()>>>(8*(11-offset)))&255;
                case 12,13,14,15 -> (m.centreY16_16()>>>(8*(15-offset)))&255;
                case 0x10,0x11 -> (m.xVelocity()>>>(8*(0x11-offset)))&255;
                case 0x12,0x13 -> (m.yVelocity()>>>(8*(0x13-offset)))&255;
                case 0x14,0x15 -> (m.groundVelocity()>>>(8*(0x15-offset)))&255;
                case 0x16 -> m.yRadius();case 0x17 -> m.xRadius();case 0x18 -> h.priority();case 0x19 -> h.width();
                case 0x1A -> s.animation().mapFrame();case 0x1B -> s.animation().scriptIndex();case 0x1C -> m.animation();
                case 0x1D -> m.previousAnimation();case 0x1E -> s.animation().timer();case 0x20 -> s.objectLinks().pushObject();case 0x22 -> m.flags();
                case 0x24 -> m.routine();case 0x26 -> m.angle();case 0x2A -> m.charge();case 0x2B -> s.rotationAngle();case 0x2C -> m.playerControl();
                case 0x30,0x31 -> (s.timers().hurt()>>>(8*(0x31-offset)))&255;
                case 0x32,0x33 -> (s.timers().invincibility()>>>(8*(0x33-offset)))&255;
                case 0x34,0x35 -> (s.timers().speedShoes()>>>(8*(0x35-offset)))&255;
                case 0x36 -> m.previousPrimaryAngle();case 0x37 -> m.previousSecondaryAngle();case 0x38 -> m.stick();case 0x39 -> s.rotationDistance();
                case 0x3A,0x3B -> (s.timers().reset()>>>(8*(0x3B-offset)))&255;
                case 0x3C -> m.playerJump();case 0x3D -> s.objectLinks().standObject();case 0x3E,0x3F -> (m.moveLock()>>>(8*(0x3F-offset)))&255;
                default -> throw new CdCheckpoint.UnownedParentByte(raw);
            };
        }
        var l=values.level();
        if(bus==0xFF158E)return l.checkpoint();if(bus==0xFF1522)return l.spawnMode();if(bus==0xFF151C)return l.plcLoadFlags();
        if(bus==0xFF1510)return l.updateHudTime();if(bus==0xFF150C)return l.timeOver();if(bus==0xFF156B)return l.powerup();
        if(bus==0xFF1903)return l.fadeEnableDisplay();
        if(bus>=0xFF1900&&bus<0xFF1903)return (l.flowerCount()>>>(8*(0xFF1902-bus)))&255;
        if(bus>=0xFF1502&&bus<0xFF1504)return (l.levelRestart()>>>(8*(0xFF1503-bus)))&255;
        for(var field:CdCheckpointLiveAux.Field.values())if(bus>=field.nativeAddress()&&bus<field.nativeAddress()+field.width())
            return values.globals().live().knownByte(bus).orElseThrow(()->new CdCheckpoint.UnownedParentByte(raw));
        int[] starts={0xFF1523,0xFF152A,0xFF1530,0xFF1591};int[] lengths={5,4,21,1};
        for(int n=0;n<starts.length;n++)if(bus>=starts[n]&&bus<starts[n]+lengths[n])
            return values.globals().checkpointSaved().knownByte(bus).orElseThrow(()->new CdCheckpoint.UnownedParentByte(raw));
        for(var source:List.of(CdCheckpointSaveData.Source.FG_X,CdCheckpointSaveData.Source.FG_Y,CdCheckpointSaveData.Source.BG_X,CdCheckpointSaveData.Source.BG_Y,
                CdCheckpointSaveData.Source.BG2_X,CdCheckpointSaveData.Source.BG2_Y,CdCheckpointSaveData.Source.BG3_X,CdCheckpointSaveData.Source.BG3_Y))
            if(bus>=source.nativeAddress()&&bus<source.nativeAddress()+2)return (checkpointCameraWord(source)>>>(8*(source.nativeAddress()+1-bus)))&255;
        if(bus==0xFFF72E||bus==0xFFF72F)return (frame.environment().movement().bounds().bottom()>>>(8*(0xFFF72F-bus)))&255;
        if(bus==0xFF195B)return frame.environment().movement().gates().miniSonic();
        throw new CdCheckpoint.UnownedParentByte(raw);
    }
    private void validateCheckpointDrawSource(CheckpointInvocation i,CdObjectState source) {
        validateCheckpointOtherImages(i,false);
        if(i.entry.routine()==0) {
            var free=i.before.pool().slots().stream().anyMatch(x->x.id()==0);
            if(free?!source.equals(checkpointInitSource(i)):!Arrays.equals(source.copyBytes(),new byte[64]))
                throw new IllegalStateException("Checkpoint Init/failed allocation differs from its exact header/source64");
        } else if(i.entry.routine()==2) {
            if(!source.equals(checkpointMainSource(i)))throw new IllegalStateException("Checkpoint Main changed its complete source image");
        } else if(i.entry.routine()==4) {
            boolean spin=i.entry.unsignedByte(CdObjectState.ByteField.CHECKPOINT_ACTIVE)!=0 || i.parentByte!=null&&i.parentByte!=0;
            if(!spin) {if(!source.equals(i.entry))throw new IllegalStateException("Inactive Checkpoint ball changed bytes");return;}
            int angle=(i.entry.unsignedByte(CdObjectState.ByteField.CHECKPOINT_BALL_ANGLE)+8)&255;
            int active=i.entry.unsignedByte(CdObjectState.ByteField.CHECKPOINT_ACTIVE);if(active==0)active=1;
            if(source.unsignedByte(CdObjectState.ByteField.CHECKPOINT_BALL_ANGLE)!=angle || source.unsignedByte(CdObjectState.ByteField.CHECKPOINT_ACTIVE)!=active
                    || source.routine()!=((i.entry.routine()+(angle==0?2:0))&255))throw new IllegalStateException("Checkpoint ball crossed native activation/phase/routine stores");
            var expected=i.entry.moveByte(CdObjectState.ByteField.CHECKPOINT_ACTIVE,active).moveByte(CdObjectState.ByteField.CHECKPOINT_BALL_ANGLE,angle)
                    .moveByte(CdObjectState.ByteField.ROUTINE,source.routine()).moveWord(CdObjectState.WordField.X,source.centreX()).moveWord(CdObjectState.WordField.Y,source.centreY());
            if(!expected.equals(source))throw new IllegalStateException("Checkpoint ball changed bytes outside its native word stores");
        } else if(i.entry.routine()==6)validateCheckpointAnimationNonwrites(i,source);
        else throw new IllegalStateException("Checkpoint Draw crossed an unsupported routine");
    }
    private void validateCheckpointAnimationNonwrites(CheckpointInvocation i,CdObjectState source) {
        var fields=EnumSet.of(CdObjectState.ByteField.ANIMATION,CdObjectState.ByteField.PREVIOUS_ANIMATION,CdObjectState.ByteField.ANIMATION_FRAME,
                CdObjectState.ByteField.ANIMATION_TIME,CdObjectState.ByteField.MAP_FRAME,CdObjectState.ByteField.ROUTINE,CdObjectState.ByteField.ROUTINE_2);
        byte[] old=i.entry.copyBytes(),now=source.copyBytes();
        for(int at=0;at<64;at++){int mask=at==1?3:0;for(var f:fields)if(at==f.byteOffset())mask=255;
            if(((old[at]^now[at])&~mask&255)!=0)throw new IllegalStateException("Checkpoint animator changed an unowned source byte "+at);}
    }
    private void validateCheckpointMutation(CheckpointInvocation i,boolean failure) {
        validateCheckpointOtherImages(i,failure);var actual=i.pool.state(i.whole);var bank=placement.capture().savedFlags();
        if(!i.drawn) {
            if(!sameBytes(bank,i.before.savedFlags()))throw new IllegalStateException("Checkpoint pre-Draw changed saved object bank");
            if(actual.equals(i.entry))return;
            if(i.entry.routine()==0 && (actual.equals(checkpointHeader(i)) || actual.equals(checkpointInitSource(i)) || Arrays.equals(actual.copyBytes(),new byte[64])))return;
            if(i.entry.routine()==2 && actual.equals(checkpointMainSource(i)))return;
            if(i.entry.routine()==6){validateCheckpointAnimationNonwrites(i,actual);return;}
            if(i.entry.routine()==4){validateCheckpointDrawSource(i,actual);return;}
            throw new IllegalStateException("Checkpoint failed after an unowned source prefix");
        }
        var source=i.drawSource;
        if(failure&&actual.equals(source)&&sameBytes(bank,i.before.savedFlags()))return;
        var gates=i.shared.environment().movement().gates();boolean outside=(source.spriteFlags()&128)==0
                && (((source.centreX()&0xFF80)-((i.camera.xWord()-0x80)&0xFF80))&65535)>0x280;
        boolean delete=outside||(gates.timeZone()&128)!=0;byte[] saved=i.before.savedFlags().copy();boolean owned=true;
        if(delete&&source.savedFlagsId()!=0) {
            int period=gates.timeZone()&127;
            if((gates.timeZone()&128)!=0){int adjusted=(period-(byte)gates.timeWarpDirection())&65535;period=(short)adjusted<0?0:adjusted>=3?2:adjusted;}
            int address=2+(short)((3*source.savedFlagsId()+period)&65535);owned=address>=2&&address<saved.length;if(owned)saved[address]&=0x7F;
        }
        if(failure&&actual.equals(source)&&owned&&Arrays.equals(bank.copy(),saved))return;
        if(!owned&&!failure || !Arrays.equals(actual.copyBytes(),delete&&owned?new byte[64]:source.copyBytes()) || !Arrays.equals(bank.copy(),saved))
            throw new IllegalStateException("Checkpoint post-Draw source/bank differs from native despawn prefix");
    }


    private CheckpointInvocation requireCheckpointDraw(CdObjectPool pool,CdObjectState.Identity id) {
        var i=activeCheckpoint;verifyCheckpointInvocation(i);
        if(pool!=i.pool || id==null || !id.equals(i.entry.identity()) || checkpointPort || queuePort)
            throw new IllegalStateException("No actual Checkpoint draw pointer");return i;
    }
    private void drawCheckpoint(CdObjectPool pool,CdObjectState.Identity id) {
        var i=requireCheckpointDraw(pool,id);
        if(drawingCheckpoint || i.drawn || !checkpoint.isCurrentDrawCall(pool,id) || !checkpoint.matchesCurrentLivePrefix(i.receipts))
            throw new IllegalStateException("Checkpoint Draw lacks its exact native producer/prefix");
        var current=pool.state(i.whole);validateCheckpointDrawSource(i,current);
        if(!sameBytes(placement.capture().savedFlags(),i.before.savedFlags()))throw new IllegalStateException("Checkpoint changed object saved bytes before Draw");
        i.drawSource=current;i.drawPool=pool.capture();i.drawn=true;drawingCheckpoint=true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(i.whole),current.spriteFlags(),current.centreX(),current.centreY(),
                    current.unsignedByte(CdObjectState.ByteField.Y_RADIUS),current.unsignedByte(CdObjectState.ByteField.WIDTH),current.unsignedByte(CdObjectState.ByteField.PRIORITY)),i.camera);
            if(!i.screenCleared || !pool.capture().slots().equals(i.drawPool.slots()))throw new IllegalStateException("Checkpoint Draw omitted or changed its exact BCLR7/full96 prefix");
            verifyCheckpointInvocation(i);
        } catch(RuntimeException|Error fault) {
            try{verifyCheckpointInvocation(i);if(!pool.capture().slots().equals(i.drawPool.slots()))throw new IllegalStateException("Checkpoint Draw fault changed its full96 prefix");}
            catch(RuntimeException|Error verification){if(verification!=fault)fault.addSuppressed(verification);}throw fault;
        } finally{drawingCheckpoint=false;}
    }
    private CdObjectDrawQueue.LiveDrawSlot readCheckpointDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var i=activeCheckpoint;verifyCheckpointInvocation(i);
        if(!drawingCheckpoint || checkpointPort || queuePort || ref.wholeIndex()!=i.whole || !i.drawn)
            throw new IllegalStateException("No actual Checkpoint collecting read");
        var before=placement.capture();queuePort=true;
        try {
            if(!before.pool().slots().equals(i.drawPool.slots()))throw new IllegalStateException("Checkpoint queue lost its full96 source");
            var s=i.pool.state(i.whole);var result=new CdObjectDrawQueue.LiveDrawSlot(s.id(),s.spriteFlags(),s.centreX(),s.centreY(),s.word(CdObjectState.WordField.X_FRACTION),
                    s.longBits(CdObjectState.LongField.MAPPINGS),s.unsignedByte(CdObjectState.ByteField.MAP_FRAME),s.word(CdObjectState.WordField.TILE));
            verifyCheckpointCallback(i,before);return result;
        }catch(RuntimeException|Error fault){checkpointCallbackFailure(i,before,fault);throw fault;}finally{queuePort=false;}
    }
    private void writeCheckpointOnScreen(CdObjectDrawQueue.CdSlotRef ref,boolean onScreen) {
        var i=activeCheckpoint;verifyCheckpointInvocation(i);
        if(!drawingCheckpoint || checkpointPort || queuePort || onScreen || i.screenCleared || ref.wholeIndex()!=i.whole || !i.drawn)
            throw new IllegalStateException("Only actual Checkpoint DrawObject may clear bit7 once");
        var before=placement.capture();var source=i.pool.state(i.whole);
        if(!source.equals(i.drawSource) || !before.pool().slots().equals(i.drawPool.slots()))throw new IllegalStateException("Checkpoint bit7 store lost its actual complete source");
        var next=source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,source.spriteFlags()&127);var ready=CdObjectPool.overlay(before.pool(),List.of(next));i.pool.validateRestore(ready);
        queuePort=true;try {
            verifyCheckpointCallback(i,before);i.pool.write(next);i.screenCleared=true;i.drawSource=next;i.drawPool=ready;verifyCheckpointInvocation(i);
            var now=placement.capture();if(!now.pool().slots().equals(ready.slots()) || !sameBytes(now.savedFlags(),before.savedFlags()) || !now.status().equals(before.status()))
                throw new IllegalStateException("Checkpoint Draw changed bytes beyond its native BCLR7");
        }catch(RuntimeException|Error fault){try{verifyCheckpointInvocation(i);}catch(RuntimeException|Error verification){if(verification!=fault)fault.addSuppressed(verification);}throw fault;}finally{queuePort=false;}
    }


    // BEGIN actual cached Breakable Wall family.
    private void requireCollapseIdle() {
        requireLogMosquiIdle();
        if(activeCollapse!=null || collapsePort || drawingCollapse)throw new IllegalStateException("Actual Collapse owns this cut");
    }
    private void requireWallIdle() {
        requireCollapseIdle();
        if (activeWall != null || wallPort || drawingWall)
            throw new IllegalStateException("Actual Wall source scope owns this cut");
    }
    private final class WallInvocation {
        final CdBreakableWall body = wall;
        final CdPlayerFrameRuntime sourceFrame = frame;
        final CdR12aTerrainAssets sourceAssets = assets;
        final Object stage = frame.stageSource();
        final CdObjectPool pool = placement.pool();
        final CdObjectDrawQueue queue = drawQueue;
        final CdObjectPlacementRuntime.Status status = placement.status();
        final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.captureAct2Scroll();
        final CdAssetBytes reserved = values.hudObjects();
        final int whole;
        final CdObjectState entry;
        final Object world;
        final long generation;
        final CdObjectDrawQueue.CameraWords camera;
        final int branch;
        CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        Values expectedValues = values;
        List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        CdSolidObject.Outcome solidOutcome;
        CdBreakableWall.Result returned;
        Object published;
        CdObjectPool.Snapshot drawPool;
        CdObjectState drawSource;
        int solids, fms, halves, draws;
        boolean cleared;
        WallInvocation(int whole) {
            this.whole=whole; entry=pool.state(whole);
            branch=entry.routine()==4 ? 4 : entry.unsignedByte(CdObjectState.ByteField.COLLISION_STATUS)==0 ? 2 : 3;
            var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world=mode.getWorldSession(); generation=mode.getLevelManager().getCompletedProductionLoadGeneration();
            var fg=presentation.act2ForegroundImage(); camera=new CdObjectDrawQueue.CameraWords(fg.cameraX(),fg.cameraYWord());
        }
    }
    private void verifyWallScope(WallInvocation i) {
        module.requireAct2LoadingVisit(this); var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (i==null || activeWall!=i || !runningObjects || servicing || activeDrain!=null || activePrimary!=null
                || activeTitle!=null || activeReserved!=null || activeRing!=null || activeSpring!=null || activeMonitor!=null
                || activeCollapse!=null || collapsePort || drawingCollapse || activeBoulder!=null || activePlatform!=null || activeAnton!=null || activePata!=null || activeTamabboh!=null || activeFloat!=null
                || activeCheckpoint!=null || checkpointPort || drawingCheckpoint
                || titlePort || drawingTitle || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || monitorPort || drawingMonitor || boulderPort || drawingBoulder || platformPort || drawingPlatform
                || antonPort || drawingAnton || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat
                || i.body!=wall || i.sourceFrame!=frame || i.sourceAssets!=assets || i.stage!=frame.stageSource()
                || i.pool!=placement.pool() || i.queue!=drawQueue || drawQueue==null || drawQueue.phase()!=CdObjectDrawQueue.Phase.COLLECTING
                || mode.getWorldSession()!=i.world || mode.getLevelManager().getCompletedProductionLoadGeneration()!=i.generation
                || !frame.captureActor().equals(i.actor) || !frame.captureShared().equals(i.shared)
                || !placement.status().equals(i.status) || !presentation.sameAct2Scroll(i.scroll)
                || values!=i.expectedValues || sounds!=i.expectedSounds || !sameBytes(values.hudObjects(),i.reserved) || values.phase()!=BEGIN_LEVEL)
            throw new IllegalStateException("Wall lost its actual cached source/World/full Frame/Values/scroll/queue cut");
    }
    private WallInvocation currentWall() { var i=activeWall; verifyWallScope(i); return i; }
    private void runWall(int whole) {
        requireDoorIdle();
        requireLogMosquiIdle();
        requireBoulderPlatformIdle(); module.requireAct2LoadingVisit(this);
        if (!runningObjects || servicing || activePrimary!=null || activeDrain!=null || activeTitle!=null || activeReserved!=null
                || activeRing!=null || activeSpring!=null || activeMonitor!=null || queuePort || whole<32 || whole>=128
                || placement.pool().state(whole).id()!=CdBreakableWall.OBJECT_ID || values.phase()!=BEGIN_LEVEL
                || drawQueue==null || drawQueue.phase()!=CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Wall physical dispatch");
        var i=new WallInvocation(whole); activeWall=i;
        try {
            var gates=frame.environment().movement().gates();
            var result=wall.run(whole,new CdBreakableWall.Globals(gates.timeZone(),gates.timeWarpDirection(),i.camera.xWord()),
                    new CdBreakableWall.Inputs(springPlayer(),new CdSolidObject.Gates(frame.environment().globals().debugMode(),gates.miniSonic()),springGlobals()));
            verifyWallScope(i);
            if (i.returned!=result || !result.state().equals(i.pool.state(whole)))
                throw new IllegalStateException("Wall omitted its actual completed producer receipt");
        } catch (RuntimeException | Error original) {
            try { verifyWallScope(i); } catch (RuntimeException | Error check) { if(check!=original)original.addSuppressed(check); }
            throw original;
        } finally { activeWall=null; }
    }
    private void validateWallPublication(Object operation) {
        var i=currentWall();
        if (wallPort || drawingWall || queuePort || !wall.matchesPendingOperation(operation) || i.published!=operation)
            throw new IllegalStateException("Wall continuation lacks its exact actual World publication receipt");
        i.published=null;
    }
    private void finishWall(CdBreakableWall.Result result) {
        var i=currentWall(); wall.validateCurrentCall(result);
        if (wallPort || drawingWall || queuePort || i.returned!=null
                || result.effects().stream().filter(CdBreakableWall.SolidReturned.class::isInstance).count()!=i.solids
                || result.effects().stream().filter(CdBreakableWall.PlayedFm.class::isInstance).count()!=i.fms
                || result.effects().stream().filter(CdBreakableWall.VelocityHalved.class::isInstance).count()!=i.halves
                || result.effects().stream().filter(CdBreakableWall.DrawReturned.class::isInstance).count()!=i.draws
                || i.branch==2 && i.solids!=1 || i.branch==3 && (i.fms!=1 || i.halves!=1)
                || !result.state().equals(i.pool.state(i.whole)))
            throw new IllegalStateException("Wall completed effects lack their actual once-only World publications");
        i.returned=result;
    }
    private void failWall(Throwable original) {
        currentWall(); wall.validateCurrentCall(original); // Exact original failure and reached full96/bank prefix.
    }
    private void wallCut(WallInvocation i, CdBreakableWall.SourceCut cut) {
        placement.validateRestore(cut.placement());
        if (cut.source().identity().wholeIndex()!=i.whole || !cut.source().equals(i.pool.state(i.whole))
                || !cut.placement().pool().slots().equals(i.pool.capture().slots())
                || !sameBytes(cut.placement().savedFlags(),placement.capture().savedFlags()) || !cut.placement().status().equals(i.status))
            throw new IllegalStateException("Wall callback borrowed another selected64/full96/status/saved cut");
    }
    private Values preparedWallValues(SonicCdGameModule.Act2LoadCall call) {
        var old=values; var calls=new ArrayList<>(old.calls()); calls.add(call);
        return new Values(old.phase(),old.section(),old.cpuPalette(),old.fadePalette(),old.waterPalette(),old.displayedPalette(),old.cpuSat(),
                old.displayedSat(),old.displayedHScroll(),old.displayedVScrollLongBits(),old.foregroundPlane(),old.ring(),old.level(),old.globals(),
                old.hudObjects(),old.fadeProgress(),List.copyOf(calls),old.loop());
    }
    private void commitWallSolid(CdBreakableWall.SolidCall call) {
        Objects.requireNonNull(call); var i=currentWall(); wall.validateCurrentCall(call); wallCut(i,call.cut());
        if (wallPort || drawingWall || queuePort || i.branch!=2 || i.solids!=0 || i.draws!=0
                || !call.expectedPlayer().equals(springPlayer()) || !call.expectedGlobals().equals(springGlobals()))
            throw new IllegalStateException("Wall Solid lacks its actual plain once-only complete Frame cut");
        CdObjectState object; List<CdObjectState> others; Set<CdObjectState.ByteField> writes;
        List<CdPlayerTouchResponse.FmSound> requests; CdPlayerFrameRuntime.ObjectSolidPublication publication;
        if (call.outcome() instanceof CdSolidObject.Completed done) {
            var r=done.completedResult(); validateMonitorCompleted(r); object=r.object(); others=r.otherSlots(); writes=r.objectWrites(); requests=r.fmRequests();
            publication=frame.prepareObjectSolidResult(call.expectedPlayer(),call.expectedGlobals(),r);
        } else {
            var stop=(CdSolidObject.NativeStopped)call.outcome(); validateMonitorStopped(stop);
            var r=stop.prefix(); object=r.object(); others=r.otherSlots(); writes=r.objectWrites(); requests=r.fmRequests();
            publication=frame.prepareObjectSolidPrefix(call.expectedPlayer(),call.expectedGlobals(),r);
        }
        var source=call.cut().source();
        if (!source.identity().equals(object.identity()) || writes.stream().anyMatch(f->f!=CdObjectState.ByteField.FLAGS && f!=CdObjectState.ByteField.SOLID_TYPE))
            throw new IllegalStateException("Wall plain Solid changed another helper field");
        var bytes=source.copyBytes(); var next=object.copyBytes();
        for(int at=0;at<64;at++) {
            int mask=at==0x22 && writes.contains(CdObjectState.ByteField.FLAGS)?0x28:at==0x25 && writes.contains(CdObjectState.ByteField.SOLID_TYPE)?255:0;
            if (((bytes[at]^next[at]) & ~mask & 255)!=0)throw new IllegalStateException("Wall plain Solid changed an undeclared full64 byte");
        }
        if (writes.contains(CdObjectState.ByteField.SOLID_TYPE) && object.unsignedByte(CdObjectState.ByteField.SOLID_TYPE)!=0 || others.size()>1)
            throw new IllegalStateException("Wall Solid has invalid type/stand-release output");
        var images=new ArrayList<CdObjectState>(); images.add(object);
        for(var other:others) {
            int whole=other.identity().wholeIndex();
            if (whole==i.whole || whole!=call.expectedPlayer().standObject()
                    || !call.cut().placement().pool().slots().get(whole-32).moveByte(CdObjectState.ByteField.FLAGS,
                        call.cut().placement().pool().slots().get(whole-32).flags() & ~8).equals(other))
                throw new IllegalStateException("Wall Solid stand release differs from actual dynamic BCLR3");
            images.add(other);
        }
        var readyPool=CdObjectPool.overlay(call.cut().placement().pool(),images); i.pool.validateRestore(readyPool);
        var actor=new CdPlayerFrameRuntime.ActorState(i.actor.owner(),publication.finalState(),i.actor.display());
        var shared=new CdPlayerFrameRuntime.SharedState(i.shared.owner(),publication.finalEnvironment(),i.shared.artState(),i.shared.paused(),i.shared.vIntClock());
        var ledger=new ArrayList<>(sounds); for(var fm:requests)ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
        var readySounds=List.copyOf(ledger); var readyValues=preparedWallValues(WALL_SOLID);
        wallPort=true;
        try {
            verifyWallScope(i); wall.validateCurrentCall(call); frame.validatePreparedObjectSolidPublication(publication);
            i.pool.restore(readyPool); frame.applyPreparedObjectSolidPublication(publication); sounds=readySounds; values=readyValues;
            i.actor=actor; i.shared=shared; i.expectedSounds=readySounds; i.expectedValues=readyValues; i.solids++; i.solidOutcome=call.outcome();
            verifyWallScope(i);
            if (!i.pool.capture().slots().equals(readyPool.slots()) || !sameBytes(placement.capture().savedFlags(),call.cut().placement().savedFlags()))
                throw new IllegalStateException("Wall Solid omitted its prepared exact full96/saved output");
            i.published=call; wall.acknowledgeCurrentCall(call);
        } finally { wallPort=false; }
    }
    private void playWallFm(CdBreakableWall.FmCall call) {
        Objects.requireNonNull(call); var i=currentWall(); wall.validateCurrentCall(call); wallCut(i,call.cut());
        if (wallPort || drawingWall || queuePort || i.branch!=3 || i.fms!=0 || i.halves!=0 || i.draws!=0)
            throw new IllegalStateException("Wall FM_B0 lacks its native pre-ASR cut");
        var ledger=new ArrayList<>(sounds); ledger.add(new SonicCdGameModule.PastWallFm(call.nativeId()));
        var readySounds=List.copyOf(ledger); var readyValues=preparedWallValues(WALL_FM);
        wallPort=true;
        try {
            verifyWallScope(i); wall.validateCurrentCall(call);
            sounds=readySounds; values=readyValues; i.expectedSounds=readySounds; i.expectedValues=readyValues; i.fms++;
            verifyWallScope(i); i.published=call; wall.acknowledgeCurrentCall(call);
        } finally { wallPort=false; }
    }
    private void halveWallX(CdBreakableWall.HalveXVelocityCall call) {
        Objects.requireNonNull(call); var i=currentWall(); wall.validateCurrentCall(call); wallCut(i,call.cut());
        if (wallPort || drawingWall || queuePort || i.branch!=3 || i.fms!=1 || i.halves!=0 || i.draws!=0 || !call.expectedPlayer().equals(springPlayer()))
            throw new IllegalStateException("Wall ASR lacks its actual post-FM complete Frame");
        var publication=frame.prepareObjectBreakableWallXVelocity(frame.state(),frame.environment());
        if (publication.finalState().motion().xVelocity()!=call.finalXVelocity())throw new IllegalStateException("Wall ASR value differs from native WORD store");
        var actor=new CdPlayerFrameRuntime.ActorState(i.actor.owner(),publication.finalState(),i.actor.display());
        var shared=new CdPlayerFrameRuntime.SharedState(i.shared.owner(),publication.finalEnvironment(),i.shared.artState(),i.shared.paused(),i.shared.vIntClock());
        var readyValues=preparedWallValues(WALL_HALVE_X);
        wallPort=true;
        try {
            verifyWallScope(i); wall.validateCurrentCall(call); frame.validatePreparedObjectSolidPublication(publication);
            frame.applyPreparedObjectSolidPublication(publication); values=readyValues; i.actor=actor; i.shared=shared; i.expectedValues=readyValues; i.halves++;
            verifyWallScope(i); i.published=call; wall.acknowledgeCurrentCall(call);
        } finally { wallPort=false; }
    }
    private void verifyWallDrawPool(WallInvocation i) {
        if (!i.pool.capture().slots().equals(i.drawPool.slots())) throw new IllegalStateException("Wall queue changed unowned full96 bytes");
    }
    private void drawWall(CdObjectPool pool, CdObjectState.Identity identity) {
        var i=currentWall(); wall.validateCurrentDraw(pool,identity);
        if (wallPort || drawingWall || queuePort || pool!=i.pool || identity.wholeIndex()!=i.whole || i.draws!=0
                || i.branch==2 && (i.solids!=1 || i.solidOutcome instanceof CdSolidObject.NativeStopped)
                || i.branch==3 && (i.fms!=1 || i.halves!=1))throw new IllegalStateException("Wall Draw lacks native returned owners");
        var before=placement.capture(); var current=pool.state(i.whole); i.drawPool=before.pool(); i.drawSource=current;
        drawingWall=true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(i.whole),current.spriteFlags(),current.centreX(),current.centreY(),
                    current.unsignedByte(CdObjectState.ByteField.Y_RADIUS),current.unsignedByte(CdObjectState.ByteField.WIDTH),current.unsignedByte(CdObjectState.ByteField.PRIORITY)),i.camera);
            verifyWallScope(i); verifyWallDrawPool(i);
            if (!i.cleared || !sameBytes(before.savedFlags(),placement.capture().savedFlags()))throw new IllegalStateException("Wall actual Draw omitted bit7 clear or changed bank");
            i.draws++; i.published=identity; wall.acknowledgeCurrentCall(identity);
        } finally { drawingWall=false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readWallDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var i=currentWall();
        if (!drawingWall || wallPort || queuePort || ref.wholeIndex()!=i.whole)throw new IllegalStateException("No actual Wall queue read");
        queuePort=true;
        try {
            verifyWallDrawPool(i); var s=i.pool.state(i.whole);
            return new CdObjectDrawQueue.LiveDrawSlot(s.id(),s.spriteFlags(),s.centreX(),s.centreY(),s.word(CdObjectState.WordField.X_FRACTION),
                    s.longBits(CdObjectState.LongField.MAPPINGS),s.unsignedByte(CdObjectState.ByteField.MAP_FRAME),s.word(CdObjectState.WordField.TILE));
        } finally { queuePort=false; }
    }
    private void writeWallOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean value) {
        var i=currentWall();
        if (!drawingWall || wallPort || queuePort || value || i.cleared || ref.wholeIndex()!=i.whole)throw new IllegalStateException("Wall Draw clears own bit7 once");
        verifyWallDrawPool(i); var next=i.drawSource.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,i.drawSource.spriteFlags() & 127);
        var ready=CdObjectPool.overlay(i.drawPool,List.of(next)); i.pool.validateRestore(ready); queuePort=true;
        try { i.pool.write(next); i.drawPool=ready; i.drawSource=next; i.cleared=true; verifyWallScope(i); verifyWallDrawPool(i); }
        finally { queuePort=false; }
    }
    // END actual cached Breakable Wall family.


    private enum CollapseStep { ENTRY, MAIN_GET_OFF, FM, BREAKUP, CAPACITY_GET_OFF, DELAY_GET_OFF, DRAW, DONE, STOPPED }
    private final class CollapseInvocation {
        private final CdCollapsePlatform body = collapsePlatform;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR12aTerrainAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private Values expectedValues = values;
        private List<CdCollapsePlatform.Solid> solidReceipts=List.of();
        private final CdObjectPlacementRuntime.Status status = placement.status();
        private final CdAssetBytes savedFlags = placement.capture().savedFlags();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.captureAct2Scroll();
        private final CdObjectDrawQueue.CameraWords camera;
        private CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private CdObjectPool.Snapshot currentPool = pool.capture();
        private CdObjectPool.Snapshot lastCut;
        private CdSolidObject.Outcome adopted;
        private CdObjectState drawSource;
        private Object published;
        private CollapseStep next = CollapseStep.ENTRY;
        private boolean single, sounded, drawn, screenCleared;
        private CollapseInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.act2ForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
        }
    }

    private void runCollapse(int whole) {
        requireDoorIdle();
        requireLogMosquiIdle();
        module.requireAct2LoadingVisit(this);
        if(activeCheckpoint!=null || checkpointPort || drawingCheckpoint || activeWall!=null || wallPort || drawingWall
                || activeTitle!=null || titlePort || drawingTitle)throw new IllegalStateException("Another actual Act2 family owns this cut");
        if (!runningObjects || servicing || activeDrain != null || activePrimary != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeMonitor != null || activeFloat != null || activePata != null
                || activeTamabboh != null || activeBoulder != null || activeAnton != null || antonPort || drawingAnton || activeCollapse != null || reservedPort || drawingReserved
                || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform
                || floatPort || drawingFloat || drawingPata || tamabbohPort || drawingTamabboh || boulderPort || drawingBoulder
                || monitorPort || drawingMonitor || collapsePort || drawingCollapse
                || queuePort || whole < 32 || whole >= 128 || placement.pool().state(whole).id() != CdCollapsePlatform.OBJECT_ID
                || values.phase() != BEGIN_LEVEL || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual live Past Collapse invocation entry");
        var invocation = new CollapseInvocation(whole); activeCollapse = invocation;
        try {
            var environment = frame.environment(); var gates = environment.movement().gates();
            var result = collapsePlatform.run(whole, new CdCollapsePlatform.DespawnGlobals(gates.timeZone(),
                    gates.timeWarpDirection(), invocation.camera.xWord()), new CdCollapsePlatform.Inputs(springPlayer(),
                    new CdSolidObject.Gates(environment.globals().debugMode(), gates.miniSonic()), springGlobals()));
            verifyCollapseInvocation(invocation); validateCollapseReturn(invocation, result);
        } catch (RuntimeException | Error failure) {
            if(invocation.adopted instanceof CdSolidObject.NativeStopped stopped){var original=stopped.stop().originalException();if(failure!=original)original.addSuppressed(failure);verifyCollapseFailure(original);throw original;}
            verifyCollapseFailure(failure); throw failure;
        } finally { activeCollapse = null; }
    }

    private CollapseInvocation requireCollapseInvocation(CdObjectPool pool, int whole) {
        var invocation = activeCollapse;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128 || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Collapse callback at this physical pointer");
        verifyCollapseInvocation(invocation); return invocation;
    }
    private void verifyCollapseInvocation(CollapseInvocation invocation) {
        requireLogMosquiIdle();
        module.requireAct2LoadingVisit(this);
        if(activeCheckpoint!=null || checkpointPort || drawingCheckpoint || activeWall!=null || wallPort || drawingWall
                || activeTitle!=null || titlePort || drawingTitle)throw new IllegalStateException("Another actual Act2 family owns this cut");
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeCollapse != invocation || !runningObjects || servicing || activeDrain != null
                || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null
                || activePlatform != null || activeFloat != null || activePata != null || activeTamabboh != null || activeBoulder != null || activeAnton != null
                || activeMonitor != null || reservedPort || drawingReserved
                || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform
                || floatPort || drawingFloat || drawingPata || tamabbohPort || drawingTamabboh || boulderPort || drawingBoulder
                || monitorPort || drawingMonitor
                || invocation.body != collapsePlatform || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.sourceStage != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared)
                || !placement.status().equals(invocation.status) || !presentation.sameAct2Scroll(invocation.scroll)
                || sounds != invocation.expectedSounds || values != invocation.expectedValues || values.phase() != BEGIN_LEVEL)
            throw new IllegalStateException("Collapse changed another owner or retained a stale World/Frame/queue cut");
    }
    private void verifyCollapseFailure(Throwable original) {
        try { if (activeCollapse != null) {
            verifyCollapseInvocation(activeCollapse);
            if(!activeCollapse.drawn)acceptCollapseBodyCut(activeCollapse,activeCollapse.pool.capture(),activeCollapse.sounded);
        } }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    /** The trusted cached body alone can write its own prefix and first-free children between callbacks. */
    private void acceptCollapseBodyCut(CollapseInvocation invocation, CdObjectPool.Snapshot cut, boolean breakup) {
        invocation.pool.validateRestore(cut);
        if (!cut.slots().equals(invocation.pool.capture().slots()) || !sameBytes(placement.capture().savedFlags(), invocation.savedFlags))
            throw new IllegalStateException("Collapse source cut differs from the actual full96/saved bank");
        boolean freeGap = false;
        for (int at = 0; at < 96; at++) {
            if (at == invocation.whole - 32) continue;
            var before = invocation.currentPool.slots().get(at); var after = cut.slots().get(at);
            if (before.equals(after)) { if (breakup && before.id() == 0) freeGap = true; continue; }
            if (!breakup || freeGap || before.id() != 0) throw new IllegalStateException("Collapse changed a non-child full64 image");
            validateCollapseChild(invocation, before, after);
        }
        invocation.currentPool = cut;
    }
    private void validateCollapseChild(CollapseInvocation invocation, CdObjectState before, CdObjectState after) {
        int map = view(invocation.single ? CdR12aProfile.Asset.MAP_SPR_COLLAPSE_PLATFORM_4
                : CdR12aProfile.Asset.MAP_SPR_COLLAPSE_PLATFORM_3).range().nativeAddress();
        int flip = !invocation.single && (invocation.entry.subtype() & 16) != 0 ? 1 : 0;
        if (!before.identity().equals(after.identity()) || after.id() != CdCollapsePlatform.OBJECT_ID || after.routine() != 4
                || after.word(CdObjectState.WordField.TILE) != 0x44BE || after.longBits(CdObjectState.LongField.MAPPINGS) != map
                || after.longBits(CdObjectState.LongField.COLLAPSE_FALL_DELTA) != 0x20000
                || after.unsignedByte(CdObjectState.ByteField.PRIORITY) != 3
                || after.unsignedByte(CdObjectState.ByteField.MAP_FRAME) >= 7
                || after.spriteFlags() != (before.spriteFlags() | 4 | flip)
                || after.flags() != (before.flags() | flip))
            throw new IllegalStateException("Collapse child lacks its actual family/native header");
        boolean radii = invocation.single || before.unsignedByte(CdObjectState.ByteField.COLLAPSE_SOLID_MARKER)
                != after.unsignedByte(CdObjectState.ByteField.COLLAPSE_SOLID_MARKER)
                || before.unsignedByte(CdObjectState.ByteField.X_RADIUS) != after.unsignedByte(CdObjectState.ByteField.X_RADIUS)
                || before.unsignedByte(CdObjectState.ByteField.Y_RADIUS) != after.unsignedByte(CdObjectState.ByteField.Y_RADIUS)
                || before.unsignedByte(CdObjectState.ByteField.WIDTH) != after.unsignedByte(CdObjectState.ByteField.WIDTH);
        if (radii && (after.unsignedByte(CdObjectState.ByteField.COLLAPSE_SOLID_MARKER) != 255
                || after.unsignedByte(CdObjectState.ByteField.X_RADIUS) != 8 || after.unsignedByte(CdObjectState.ByteField.WIDTH) != 8
                || !invocation.single && after.unsignedByte(CdObjectState.ByteField.Y_RADIUS) != 9))
            throw new IllegalStateException("Collapse child changed radii beyond its native solid-row stores");
        byte[] old = before.copyBytes(), next = after.copyBytes();
        for (int at = 0; at < 64; at++) {
            boolean allowed = at <= 9 || at == 12 || at == 13 || at == 0x18 || at == 0x1A || at == 0x22
                    || at == 0x24 || at == 0x2A || at == 0x2B || at >= 0x2C && at < 0x30
                    || radii && (at == 0x16 || at == 0x17 || at == 0x19 || at == 0x3E);
            if (!allowed && old[at] != next[at]) throw new IllegalStateException("Collapse child changed undeclared byte " + at);
        }
    }

    /** Actual instruction sites and prior completed helper determine the next allowed port. */
    private void validateCollapseSite(CollapseInvocation invocation, CdCollapsePlatform.SolidCall call, CdObjectState source) {
        boolean top; boolean valid;
        switch (call.site()) {
            case MAIN_TOP -> { top = true; valid = invocation.next == CollapseStep.ENTRY && invocation.entry.routine() <= 2 && source.routine() == 2; }
            case MAIN_GET_OFF -> { top = false; valid = invocation.next == CollapseStep.MAIN_GET_OFF && source.routine() == 2; }
            case DELAY_TOP -> { top = true; valid = invocation.next == CollapseStep.ENTRY && invocation.entry.routine() == 4
                    && (source.routine() == 4 || source.routine() == 6) && source.unsignedByte(CdObjectState.ByteField.COLLAPSE_SOLID_MARKER) != 0
                    && (source.routine() == 6) == (source.word(CdObjectState.WordField.COLLAPSE_DELAY) == 0); }
            case DELAY_GET_OFF -> { top = false; valid = invocation.next == CollapseStep.DELAY_GET_OFF && source.routine() == 6
                    && source.word(CdObjectState.WordField.COLLAPSE_DELAY) == 0; }
            case MULTI_CAPACITY_TOP, SINGLE_CAPACITY_TOP -> { top = true; invocation.single = call.site() == CdCollapsePlatform.Site.SINGLE_CAPACITY_TOP;
                valid = invocation.next == CollapseStep.BREAKUP && invocation.sounded && source.routine() == 4
                        && (source.signedByte(CdObjectState.ByteField.SUBTYPE) < 0) == invocation.single
                        && source.unsignedByte(CdObjectState.ByteField.COLLAPSE_SAVED_SOURCE_ID) == CdCollapsePlatform.OBJECT_ID
                        && invocation.pool.findObjSlot().isEmpty(); }
            case MULTI_CAPACITY_GET_OFF, SINGLE_CAPACITY_GET_OFF -> { top = false;
                valid = invocation.next == CollapseStep.CAPACITY_GET_OFF && source.routine() == 4
                        && (call.site() == CdCollapsePlatform.Site.SINGLE_CAPACITY_GET_OFF) == invocation.single; }
            default -> throw new IllegalStateException("Unowned Collapse helper site");
        }
        if (!valid || source.id() != CdCollapsePlatform.OBJECT_ID
                || call.entry() != (top ? CdSolidObject.Entry.TOP_SOLID_OBJECT : CdSolidObject.Entry.GET_OFF_OBJECT))
            throw new IllegalStateException("Collapse helper differs from its actual private native site/order");
    }
    private List<CdObjectState> validateCollapseImages(CollapseInvocation invocation, CdCollapsePlatform.SolidCall call,
            CdObjectState source, CdObjectState object, List<CdObjectState> others, Set<CdObjectState.ByteField> writes) {
        if (!source.identity().equals(object.identity())
                || writes.stream().anyMatch(field -> field != CdObjectState.ByteField.SOLID_TYPE && field != CdObjectState.ByteField.FLAGS)
                || writes.contains(CdObjectState.ByteField.SOLID_TYPE) != (call.entry() == CdSolidObject.Entry.TOP_SOLID_OBJECT)
                || writes.contains(CdObjectState.ByteField.SOLID_TYPE) && object.unsignedByte(CdObjectState.ByteField.SOLID_TYPE) > 1
                || ((object.flags() ^ source.flags()) & ~(call.entry()==CdSolidObject.Entry.TOP_SOLID_OBJECT?0x28:8)) != 0
                || call.entry() == CdSolidObject.Entry.GET_OFF_OBJECT && (object.flags() & ~source.flags()) != 0)
            throw new IllegalStateException("Collapse Solid changed identity, native masks or unowned flag bits");
        byte[] before = source.copyBytes(), after = object.copyBytes();
        for (int at = 0; at < 64; at++) {
            if (at == 0x25 && writes.contains(CdObjectState.ByteField.SOLID_TYPE) || at == 0x22 && writes.contains(CdObjectState.ByteField.FLAGS)) continue;
            if (before[at] != after[at]) throw new IllegalStateException("Collapse Solid changed undeclared source byte " + at);
        }
        if (others.size() > 1 || call.entry() == CdSolidObject.Entry.GET_OFF_OBJECT && !others.isEmpty())
            throw new IllegalStateException("Collapse Solid returned extra stand releases");
        var images = new ArrayList<CdObjectState>(); images.add(object); var seen = new HashSet<Integer>(); seen.add(invocation.whole);
        for (var other : others) {
            int whole = other.identity().wholeIndex();
            if (whole < 32 || whole >= 128 || !seen.add(whole) || whole != call.expectedPlayer().standObject())
                throw new IllegalStateException("Collapse release lacks its actual distinct stand identity");
            var original = call.actualCut().slots().get(whole - 32);
            if (!original.identity().equals(other.identity()) || !original.moveByte(CdObjectState.ByteField.FLAGS, original.flags() & ~8).equals(other))
                throw new IllegalStateException("Collapse release changed bytes beyond native BCLR3");
            images.add(other);
        }
        return List.copyOf(images);
    }

    /** Actual adapter adoption must be observed on its own return, before native continuation. */
    private void validateCollapsePublication(Object operation) {
        var invocation = requireCollapseInvocation(placement.pool(), activeCollapse == null ? -1 : activeCollapse.whole);
        if (collapsePort || drawingCollapse || queuePort || !collapsePlatform.isCurrentOperation(operation)
                || invocation.published != operation || !invocation.pool.capture().slots().equals(invocation.currentPool.slots())
                || !sameBytes(placement.capture().savedFlags(), invocation.savedFlags))
            throw new IllegalStateException("Collapse owner omitted its actual pending publication receipt");
        invocation.published = null;
    }

    private void commitCollapseSolid(CdCollapsePlatform.SolidCall call) {
        Objects.requireNonNull(call); var invocation = requireCollapseInvocation(placement.pool(), activeCollapse == null ? -1 : activeCollapse.whole);
        if(!collapsePlatform.isCurrentSolidCall(call) || !collapsePlatform.matchesCurrentPrefix(invocation.solidReceipts,invocation.sounded))
            throw new IllegalStateException("Collapse Solid lacks its actual pending producer/completed prefix");
        if (collapsePort || drawingCollapse || queuePort || invocation.drawn || invocation.next == CollapseStep.STOPPED
                || call.actualCut() == invocation.lastCut || call.outcome() == invocation.adopted)
            throw new IllegalStateException("Collapse Solid lacks its next fresh actual call");
        var cut = call.actualCut(); invocation.pool.validateRestore(cut);
        var source = cut.slots().get(invocation.whole - 32);
        validateCollapseSite(invocation, call, source);
        acceptCollapseBodyCut(invocation, cut, invocation.next == CollapseStep.BREAKUP);
        if (!call.expectedPlayer().equals(springPlayer()) || !call.expectedGlobals().equals(springGlobals()))
            throw new IllegalStateException("Collapse Solid differs from the actual complete Frame pair");
        CdObjectState object; List<CdObjectState> others; Set<CdObjectState.ByteField> writes;
        List<CdPlayerTouchResponse.FmSound> requests; CdPlayerFrameRuntime.ObjectSolidPublication readyFrame; CollapseStep next;
        if (call.outcome() instanceof CdSolidObject.Completed done) {
            var result = done.completedResult(); if (result.entry() != call.entry()) throw new IllegalStateException("Wrong Collapse completed helper");
            object = result.object(); others = result.otherSlots(); writes = result.objectWrites(); requests = result.fmRequests();
            readyFrame = frame.prepareObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result);
            next = switch (call.site()) {
                case MAIN_TOP -> result.d0().orElseThrow() == 0 ? CollapseStep.DRAW : CollapseStep.MAIN_GET_OFF;
                case MAIN_GET_OFF -> CollapseStep.FM;
                case DELAY_TOP -> result.d0().orElseThrow() != 0 && source.word(CdObjectState.WordField.COLLAPSE_DELAY) == 0 ? CollapseStep.DELAY_GET_OFF : CollapseStep.DRAW;
                case MULTI_CAPACITY_TOP, SINGLE_CAPACITY_TOP -> result.d0().orElseThrow() != 0 ? CollapseStep.CAPACITY_GET_OFF : CollapseStep.DRAW;
                default -> CollapseStep.DRAW;
            };
        } else {
            var stopped = (CdSolidObject.NativeStopped) call.outcome(); var prefix = stopped.prefix();
            if (prefix.entry() != call.entry() || call.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT
                    || stopped.stop().originalException() == null)
                throw new IllegalStateException("Collapse stop is not the genuine reached Top prefix");
            object = prefix.object(); others = prefix.otherSlots(); writes = prefix.objectWrites(); requests = prefix.fmRequests();
            readyFrame = frame.prepareObjectSolidPrefix(call.expectedPlayer(), call.expectedGlobals(), prefix); next = CollapseStep.STOPPED;
        }
        var images = validateCollapseImages(invocation, call, source, object, others, writes);
        var readyPool = CdObjectPool.overlay(cut, images); invocation.pool.validateRestore(readyPool);
        var readyActor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), readyFrame.finalState(), invocation.actor.display());
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), readyFrame.finalEnvironment(), invocation.shared.artState(), invocation.shared.paused(), invocation.shared.vIntClock());
        var ledger = new ArrayList<>(sounds); for (var fm : requests) ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
        var readySounds = List.copyOf(ledger);
        var receiptList=new ArrayList<>(invocation.solidReceipts);
        if(call.outcome() instanceof CdSolidObject.Completed done)receiptList.add(new CdCollapsePlatform.Solid(call.site(),done.completedResult()));
        var readyReceipts=List.copyOf(receiptList);var readyValues=preparedMonitorValues(COLLAPSE_PLATFORM_SOLID);
        collapsePort = true;
        try {
            verifyCollapseInvocation(invocation);
            if (!invocation.pool.capture().slots().equals(cut.slots())) throw new IllegalStateException("Stale Collapse publication full96");
            frame.validatePreparedObjectSolidPublication(readyFrame);
            invocation.pool.restore(readyPool); frame.applyPreparedObjectSolidPublication(readyFrame); sounds = readySounds;
            invocation.actor = readyActor; invocation.shared = readyShared; invocation.expectedSounds = readySounds;
            values=readyValues;invocation.expectedValues=readyValues;invocation.solidReceipts=readyReceipts;
            invocation.currentPool = readyPool; invocation.lastCut = cut; invocation.adopted = call.outcome(); invocation.next = next;
            verifyCollapseInvocation(invocation);
            if (!invocation.pool.capture().slots().equals(readyPool.slots())) throw new IllegalStateException("Collapse full output96 was not adopted exactly");
            invocation.published = call;
        } catch (RuntimeException | Error failure) { verifyCollapseFailure(failure); throw failure; }
        finally { collapsePort = false; }
    }
    private void playCollapseFm(CdCollapsePlatform.FmRequest call) {
        Objects.requireNonNull(call);int nativeId=call.soundId();
        var invocation = requireCollapseInvocation(placement.pool(), activeCollapse == null ? -1 : activeCollapse.whole);
        if(!collapsePlatform.isCurrentFmRequest(call) || !collapsePlatform.matchesCurrentPrefix(invocation.solidReceipts,invocation.sounded)
                || nativeId!=CdCollapsePlatform.COLLAPSE_FM || !call.actualCut().slots().equals(invocation.pool.capture().slots())
                || !call.expectedSource().equals(invocation.pool.state(invocation.whole)))
            throw new IllegalStateException("Collapse A3 lacks its actual pending producer/full source prefix");
        if (collapsePort || drawingCollapse || queuePort || invocation.next != CollapseStep.FM || invocation.sounded)
            throw new IllegalStateException("Collapse A3 is outside its actual post-GetOff/pre-ADDQ cut");
        var before = placement.capture(); var source = before.pool().slots().get(invocation.whole - 32);
        if (source.id() != CdCollapsePlatform.OBJECT_ID || source.routine() != 2 || !before.pool().slots().equals(invocation.currentPool.slots())
                || !sameBytes(before.savedFlags(), invocation.savedFlags)) throw new IllegalStateException("Stale Collapse FM full96 source");
        var ledger = new ArrayList<>(sounds); ledger.add(new SonicCdGameModule.PastCollapsePlatformFm(nativeId)); var ready = List.copyOf(ledger);
        var readyValues=preparedMonitorValues(COLLAPSE_PLATFORM_FM);
        collapsePort = true;
        try {
            verifyCollapseInvocation(invocation);values=readyValues;invocation.expectedValues=readyValues;sounds = ready; invocation.expectedSounds = ready; invocation.sounded = true;
            invocation.single = source.signedByte(CdObjectState.ByteField.SUBTYPE) < 0; invocation.next = CollapseStep.BREAKUP;
            verifyCollapseCallback(invocation, before, null); invocation.published = call;
        } catch(RuntimeException|Error failure){verifyCollapseCallbackFailure(invocation,before,null,failure);throw failure;}
        finally { collapsePort = false; }
    }

    private void verifyCollapseCallback(CollapseInvocation invocation, CdObjectPlacementRuntime.Snapshot before, CdObjectState cleared) {
        verifyCollapseInvocation(invocation); var now = placement.capture(); var expected = new ArrayList<>(before.pool().slots());
        if (cleared != null) expected.set(invocation.whole - 32, cleared);
        if (!now.status().equals(before.status()) || !sameBytes(now.savedFlags(), before.savedFlags()) || !now.pool().slots().equals(expected))
            throw new IllegalStateException("Collapse callback changed full96/saved state beyond its actual bit7 clear");
    }
    private void verifyCollapseCallbackFailure(CollapseInvocation invocation, CdObjectPlacementRuntime.Snapshot before, CdObjectState cleared, Throwable original) {
        try { verifyCollapseCallback(invocation, before, cleared); }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }
    private void drawCollapse(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireCollapseInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        boolean ownOnly = invocation.next == CollapseStep.ENTRY && (invocation.entry.routine() == 4
                && pool.state(invocation.whole).unsignedByte(CdObjectState.ByteField.COLLAPSE_SOLID_MARKER) == 0 || invocation.entry.routine() == 6);
        if (collapsePort || drawingCollapse || queuePort || invocation.drawn || !identity.equals(invocation.entry.identity())
                || !ownOnly && invocation.next != CollapseStep.DRAW && invocation.next != CollapseStep.BREAKUP)
            throw new IllegalStateException("Collapse DrawObject is outside its actual once-only wrapper cut");
        if(!collapsePlatform.isCurrentDrawCall(pool,identity) || !collapsePlatform.matchesCurrentPrefix(invocation.solidReceipts,invocation.sounded))
            throw new IllegalStateException("Collapse Draw lacks its actual native producer/full prefix");
        acceptCollapseBodyCut(invocation, pool.capture(), invocation.next == CollapseStep.BREAKUP);
        var before = placement.capture(); var current = pool.state(invocation.whole);
        if (current.id() != CdCollapsePlatform.OBJECT_ID && !(current.id() == 0 && (invocation.sounded || invocation.entry.routine() == 6)
                && Arrays.equals(current.copyBytes(), new byte[64]))) throw new IllegalStateException("Collapse Draw lacks live or genuinely cleared source");
        if (invocation.sounded && current.id() != 0) throw new IllegalStateException("Collapse breakup skipped native parent deletion");
        var cleared = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        invocation.drawSource = cleared; invocation.drawn = true; drawingCollapse = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole), current.spriteFlags(),
                    current.centreX(), current.centreY(), current.unsignedByte(CdObjectState.ByteField.Y_RADIUS), current.unsignedByte(CdObjectState.ByteField.WIDTH),
                    current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            if (!invocation.screenCleared) throw new IllegalStateException("Collapse DrawObject did not clear bit7");
            verifyCollapseCallback(invocation, before, cleared); invocation.currentPool = pool.capture(); invocation.next = CollapseStep.DONE; invocation.published = identity;
        } catch (RuntimeException | Error failure) { verifyCollapseCallbackFailure(invocation, before, invocation.screenCleared ? cleared : null, failure); throw failure; }
        finally { drawingCollapse = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readCollapseDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireCollapseInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingCollapse || collapsePort || queuePort) throw new IllegalStateException("No actual Collapse queue read");
        var before = placement.capture(); queuePort = true;
        try {
            var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(), current.centreX(), current.centreY(), current.word(CdObjectState.WordField.X_FRACTION),
                    current.longBits(CdObjectState.LongField.MAPPINGS), current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifyCollapseCallback(invocation, before, null); return live;
        } catch (RuntimeException | Error failure) { verifyCollapseCallbackFailure(invocation, before, null, failure); throw failure; }
        finally { queuePort = false; }
    }
    private void writeCollapseOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireCollapseInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingCollapse || collapsePort || queuePort || onScreen || invocation.screenCleared) throw new IllegalStateException("Only actual Collapse DrawObject may clear bit7 once");
        var before = placement.capture(); var current = invocation.pool.state(invocation.whole); var next = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        queuePort = true;
        try { invocation.pool.write(next); invocation.screenCleared = true; verifyCollapseCallback(invocation, before, next); }
        catch (RuntimeException | Error failure) { verifyCollapseCallbackFailure(invocation, before, invocation.screenCleared ? next : null, failure); throw failure; }
        finally { queuePort = false; }
    }

    private void validateCollapseReturn(CollapseInvocation invocation, CdCollapsePlatform.Result result) {
        var observed=result.effects().stream().filter(CdCollapsePlatform.Solid.class::isInstance).toList();
        if(!observed.equals(invocation.solidReceipts) || result.effects().stream().filter(CdCollapsePlatform.FmRequested.class::isInstance).count()!=(invocation.sounded?1:0))
            throw new IllegalStateException("Collapse completed observations lack actual receipts");
        if (!invocation.drawn || invocation.next != CollapseStep.DONE || !result.state().equals(invocation.pool.state(invocation.whole)))
            throw new IllegalStateException("Incomplete actual Collapse return");
        var source = invocation.drawSource; var gates = invocation.shared.environment().movement().gates();
        boolean tail = source.signedByte(CdObjectState.ByteField.ROUTINE) < 4;
        boolean delete = tail && (((source.centreX() & 0xFF80) - ((invocation.camera.xWord() - 0x80) & 0xFF80) & 0xFFFF) > 0x280 || (gates.timeZone() & 0x80) != 0);
        var now = invocation.pool.capture();
        for (int at = 0; at < 96; at++) {
            var expected = invocation.currentPool.slots().get(at); var actual = now.slots().get(at);
            if (delete && at == invocation.whole - 32) {
                if (!actual.identity().equals(expected.identity()) || !Arrays.equals(actual.copyBytes(), new byte[64]))
                    throw new IllegalStateException("Collapse tail did not clear its actual complete source image");
            } else if (!expected.equals(actual)) throw new IllegalStateException("Collapse changed full96 beyond its reached tail deletion");
        }
        byte[] bank = invocation.savedFlags.copy();
        if (delete && source.savedFlagsId() != 0) {
            int period = gates.timeZone() & 0x7F;
            if ((gates.timeZone() & 0x80) != 0) { int selected = (short) ((period - (byte) gates.timeWarpDirection()) & 0xFFFF); period = selected < 0 ? 0 : Math.min(selected, 2); }
            int index = 2 + 3 * source.savedFlagsId() + period;
            if (index >= bank.length) throw new IllegalStateException("Collapse returned past an unavailable saved-byte address");
            bank[index] &= 0x7F;
        }
        if (!Arrays.equals(bank, placement.capture().savedFlags().copy())) throw new IllegalStateException("Collapse changed another saved byte");
    }



    // Transient source scope only; components retain the native algorithms and publication order.
    private void requireLogMosquiIdle() {
        requireChildEffectIdle();
        if(activeLogMosqui!=null || logMosquiPort || drawingLogMosqui)
            throw new IllegalStateException("Another actual Log/Mosqui invocation owns this source cut");
    }
    private void requireOtherLogMosquiScopesIdle() {
        if(activeCollapse!=null || collapsePort || drawingCollapse)throw new IllegalStateException("Actual Collapse owns this cut");
        if(activePrimary!=null || activeDrain!=null || activeReserved!=null || activeRing!=null || activeSpring!=null
                || activeTitle!=null || activeMonitor!=null || activeBoulder!=null || activePlatform!=null
                || activeAnton!=null || activePata!=null || activeTamabboh!=null || activeFloat!=null || activeCheckpoint!=null || activeWall!=null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || titlePort || drawingTitle || monitorPort || drawingMonitor || boulderPort || drawingBoulder
                || platformPort || drawingPlatform || antonPort || drawingAnton || drawingPata
                || tamabbohPort || drawingTamabboh || floatPort || drawingFloat || checkpointPort || drawingCheckpoint || wallPort || drawingWall)
            throw new IllegalStateException("Another native family owns the Log/Mosqui source cut");
    }
    private final class LogMosquiInvocation {
        final boolean mosquito; final int whole; final Object body;
        final CdPlayerFrameRuntime sourceFrame=frame; final Object stageSource=frame.stageSource();
        final CdR12aTerrainAssets sourceAssets=assets; final CdObjectPool pool=placement.pool();
        final CdObjectDrawQueue queue=drawQueue; final CdObjectPlacementRuntime.Status status=placement.status();
        final CdObjectState entry; final CdObjectDrawQueue.CameraWords camera;
        final CdPlayerBody.State state=frame.state(); final CdPlayerFrameRuntime.ActorState actor=frame.captureActor();
        CdPlayerFrameRuntime.SharedState shared=frame.captureShared();
        final SonicCdPlayerPresentation.ScrollCapture scroll=presentation.captureAct2Scroll();
        final Object world; final long generation; final CdAssetBytes reserved=values.hudObjects();
        Values expectedValues=values; List<SonicCdGameModule.PastSound> ledger=sounds;
        List<CdAssetBytes> images=logMosquiImages(placement.capture().pool()); CdAssetBytes saved=placement.capture().savedFlags();
        Object pendingPublication, pendingCall, completedCall; long ordinal;
        List<CdAssetBytes> readyImages; CdAssetBytes readySaved; List<Object> readyReceipts, receipts=List.of();
        boolean drawn, cleared, queueFailed; int angles, fms;
        LogMosquiInvocation(int whole,boolean mosquito) {
            this.whole=whole;this.mosquito=mosquito;body=mosquito?mosqui:logShadow;entry=pool.state(whole);
            var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world=mode.getWorldSession();generation=mode.getLevelManager().getCompletedProductionLoadGeneration();
            var fg=presentation.act2ForegroundImage();camera=new CdObjectDrawQueue.CameraWords(fg.cameraX(),fg.cameraYWord());
        }
    }
    private static List<CdAssetBytes> logMosquiImages(CdObjectPool.Snapshot pool) {
        return pool.slots().stream().map(s->CdAssetBytes.copyOf(s.copyBytes())).toList();
    }
    private void runLogShadow(int whole) { runLogMosqui(whole,false); }
    private void runMosqui(int whole) { runLogMosqui(whole,true); }
    private void runLogMosqui(int whole,boolean mosquito) {
        requireDoorIdle();
        requireLogMosquiIdle();requireOtherLogMosquiScopesIdle();module.requireAct2LoadingVisit(this);
        if(!runningObjects || servicing || queuePort || whole<32 || whole>=128 || values.phase()!=BEGIN_LEVEL
                || drawQueue==null || drawQueue.phase()!=CdObjectDrawQueue.Phase.COLLECTING
                || placement.pool().state(whole).id()!=(mosquito?CdMosqui.OBJECT_ID:CdLogShadow.OBJECT_ID))
            throw new IllegalStateException("No actual cached Log/Mosqui physical dispatch");
        var i=new LogMosquiInvocation(whole,mosquito);activeLogMosqui=i;
        try {
            var gates=frame.environment().movement().gates();var motion=frame.state().motion();
            CdObjectState result;
            if(mosquito) result=mosqui.run(whole,new CdMosqui.Globals(values.globals().retainedDev().goodFuture(),
                    gates.timeZone(),gates.timeWarpDirection(),frame.environment().globals().debugMode(),i.camera.xWord(),
                    motion.centreX16_16()>>>16,motion.centreY16_16()>>>16)).state();
            else result=logShadow.run(whole,new CdLogShadow.Globals(gates.timeZone(),values.globals().retainedDev().goodFuture(),
                    gates.zoneAct()&255,gates.timeWarpDirection(),i.camera.xWord())).state();
            verifyLogMosqui(i);
            if(i.pendingPublication!=null || i.pendingCall!=null || !i.drawn || !i.cleared || !result.equals(i.pool.state(whole)))
                throw new IllegalStateException("Family omitted its actual completed source/Draw receipts");
        } catch(RuntimeException|Error original) {
            try {verifyLogMosqui(i);}
            catch(RuntimeException|Error verification) {if(verification!=original)original.addSuppressed(verification);}
            throw original;
        } finally {activeLogMosqui=null;}
    }
    private LogMosquiInvocation requireLogMosqui() {
        var i=activeLogMosqui;if(i==null)throw new IllegalStateException("No active Log/Mosqui source invocation");
        verifyLogMosqui(i);return i;
    }
    private void verifyLogMosquiOwners(LogMosquiInvocation i) {
        module.requireAct2LoadingVisit(this);requireOtherLogMosquiScopesIdle();
        var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if(activeLogMosqui!=i || !runningObjects || servicing || i.body!=(i.mosquito?mosqui:logShadow)
                || i.sourceFrame!=frame || i.stageSource!=frame.stageSource() || i.sourceAssets!=assets
                || i.pool!=placement.pool() || i.queue!=drawQueue || drawQueue==null
                || !(drawQueue.phase()==CdObjectDrawQueue.Phase.COLLECTING
                    || i.queueFailed && drawQueue.phase()==CdObjectDrawQueue.Phase.FAILED) || values.phase()!=BEGIN_LEVEL
                || i.world!=mode.getWorldSession() || i.generation!=mode.getLevelManager().getCompletedProductionLoadGeneration()
                || frame.state()!=i.state || !frame.captureActor().equals(i.actor) || !frame.captureShared().equals(i.shared)
                || values!=i.expectedValues || sounds!=i.ledger || !placement.status().equals(i.status)
                || !sameBytes(values.hudObjects(),i.reserved) || !presentation.sameAct2Scroll(i.scroll))
            throw new IllegalStateException("Log/Mosqui changed or borrowed another World/Frame/source owner");
    }
    private void verifyLogMosquiImages(LogMosquiInvocation i,List<CdAssetBytes> images,CdAssetBytes saved) {
        var actual=placement.capture();i.pool.validateRestore(actual.pool());
        if(images.size()!=96 || !actual.status().equals(i.status) || !sameBytes(actual.savedFlags(),saved))
            throw new IllegalStateException("Log/Mosqui changed its actual status or full saved bank");
        for(int at=0;at<96;at++)if(!Arrays.equals(actual.pool().slots().get(at).copyBytes(),images.get(at).copy()))
            throw new IllegalStateException("Log/Mosqui full96 image differs at physical "+(at+32));
    }
    private void verifyLogMosqui(LogMosquiInvocation i) {
        verifyLogMosquiOwners(i);verifyLogMosquiImages(i,i.images,i.saved);
    }
    private boolean logMosquiOrigin(LogMosquiInvocation i,Object call) {
        return i.mosquito?mosqui.isCurrentCall(call):logShadow.isCurrentCall(call);
    }
    private void beforeLogPublication(CdLogShadow.Publication c) {
        beforeLogMosquiPublication(c,c.ordinal(),c.cut(),c.source(),c.output(),c.savedIndex(),false);
    }
    private void beforeMosquiPublication(CdMosqui.Publication c) {
        beforeLogMosquiPublication(c,c.ordinal(),c.cut(),c.source(),c.output(),c.savedIndex(),true);
    }
    private void beforeLogMosquiPublication(Object call,long ordinal,CdObjectPlacementRuntime.Snapshot cut,
                                            CdObjectState source,CdObjectState output,int savedIndex,boolean mosquito) {
        var i=requireLogMosqui();
        if(i.mosquito!=mosquito || !logMosquiOrigin(i,call) || logMosquiPort || drawingLogMosqui || queuePort
                || i.pendingPublication!=null || i.pendingCall!=null || ordinal!=i.ordinal+1
                || !source.identity().equals(i.entry.identity()) || !source.equals(i.pool.state(i.whole)))
            throw new IllegalStateException("Family publication lacks its exact pending producer/sequence/source");
        validateLogMosquiCut(i,cut);
        var ready=new ArrayList<>(i.images);var saved=i.saved;
        if(savedIndex<0) {
            if(savedIndex!=-1)throw new IllegalArgumentException("Unknown family publication kind");
            if(output!=null) {
                i.pool.validateRestore(CdObjectPool.overlay(cut.pool(),List.of(output)));
                if(!output.identity().equals(source.identity()))throw new IllegalArgumentException("Family changed physical identity");
            }
            ready.set(i.whole-32,CdAssetBytes.copyOf(output==null?new byte[64]:output.copyBytes()));
        } else {
            if(output!=source || savedIndex<2 || savedIndex>=saved.length())throw new IllegalArgumentException("Unowned family saved byte");
            byte[] bytes=saved.copy();bytes[savedIndex]&=0x7F;saved=CdAssetBytes.copyOf(bytes);
        }
        var receipts=new ArrayList<>(i.receipts);receipts.add(call);
        i.readyImages=List.copyOf(ready);i.readySaved=saved;i.readyReceipts=List.copyOf(receipts);i.pendingPublication=call;
    }
    private void afterLogMosquiPublication(Object call) {
        var i=activeLogMosqui;if(i==null || i.pendingPublication!=call || !logMosquiOrigin(i,call))
            throw new IllegalStateException("No exact family publication receipt");
        verifyLogMosquiOwners(i);verifyLogMosquiImages(i,i.readyImages,i.readySaved);
        i.images=i.readyImages;i.saved=i.readySaved;i.receipts=i.readyReceipts;i.ordinal++;
        i.readyImages=null;i.readySaved=null;i.readyReceipts=null;i.pendingPublication=null;
    }
    private void validateLogMosquiCut(LogMosquiInvocation i,CdObjectPlacementRuntime.Snapshot cut) {
        placement.validateRestore(cut);
        if(!cut.status().equals(i.status))throw new IllegalStateException("Foreign family placement source cut");
        verifyLogMosquiImages(i,logMosquiImages(cut.pool()),cut.savedFlags());
    }
    private void beforeLogMosquiCall(Object call) {
        var i=requireLogMosqui();
        if(!logMosquiOrigin(i,call) || i.pendingCall!=null || i.pendingPublication!=null || logMosquiPort || drawingLogMosqui || queuePort)
            throw new IllegalStateException("No genuine family external call origin");
        CdObjectState source;
        if(call instanceof CdLogShadow.DrawCall c && !i.mosquito) {source=c.source();validateLogMosquiCut(i,c.cut());}
        else if(call instanceof CdMosqui.DrawCall c && i.mosquito) {source=c.source();validateLogMosquiCut(i,c.cut());}
        else if(call instanceof CdMosqui.PrimaryAngleCall c && i.mosquito) {
            source=c.expectedSource();i.pool.validateRestore(c.actualCut());verifyLogMosquiImages(i,logMosquiImages(c.actualCut()),i.saved);
        } else if(call instanceof CdMosqui.FmCall c && i.mosquito) {
            source=c.expectedSource();i.pool.validateRestore(c.actualCut());verifyLogMosquiImages(i,logMosquiImages(c.actualCut()),i.saved);
        } else throw new IllegalArgumentException("Wrong family call kind");
        if(!source.equals(i.pool.state(i.whole)))throw new IllegalStateException("Family external source differs from actual physical image");
        i.pendingCall=call;i.completedCall=null;
    }
    private void afterLogMosquiCall(Object call) {
        var i=requireLogMosqui();
        if(!logMosquiOrigin(i,call) || i.pendingCall!=call || i.completedCall!=call)
            throw new IllegalStateException("Family external call returned without its private once-only receipt");
        i.pendingCall=null;i.completedCall=null;
    }
    private LogMosquiInvocation requireLogMosquiCall(Object call) {
        var i=requireLogMosqui();
        if(call==null || !logMosquiOrigin(i,call) || i.pendingCall!=call || i.completedCall!=null
                || logMosquiPort || drawingLogMosqui || queuePort)
            throw new IllegalStateException("A public/cloned/repeated family call has no private World authority");
        return i;
    }
    private void commitMosquiPrimaryAngle(CdMosqui.PrimaryAngleCall call) {
        var i=requireLogMosquiCall(call);
        if(!i.mosquito || i.entry.routine()!=6 || i.drawn || i.angles==0 && call.value()!=0)
            throw new IllegalStateException("Mosqui angle did not come from the real Dive floor probe");
        var readyShared=new CdPlayerFrameRuntime.SharedState(i.shared.owner(),monitorAngleEnvironment(i.shared.environment(),call.value()),
                i.shared.artState(),i.shared.paused(),i.shared.vIntClock());
        var readyValues=preparedMonitorValues(MOSQUI_PRIMARY_ANGLE);var receipts=new ArrayList<>(i.receipts);receipts.add(call);var readyReceipts=List.copyOf(receipts);
        logMosquiPort=true;
        try {
            verifyLogMosqui(i);frame.applyObjectPrimaryAngle(call.value());i.shared=readyShared;values=readyValues;i.expectedValues=readyValues;
            i.receipts=readyReceipts;i.angles++;verifyLogMosqui(i);i.completedCall=call;
        } finally {logMosquiPort=false;}
    }
    private void playMosquiFm(CdMosqui.FmCall call) {
        var i=requireLogMosquiCall(call);
        if(!i.mosquito || i.entry.routine()!=6 || i.angles==0 || i.fms!=0 || i.drawn || call.sound()!=CdMosqui.Sound.FM_A7)
            throw new IllegalStateException("Mosqui FM lacks its real completed Dive floor prefix");
        var ledger=new ArrayList<>(sounds);ledger.add(new SonicCdGameModule.PastMosquiFm(call.sound()));var readyLedger=List.copyOf(ledger);
        var readyValues=preparedMonitorValues(MOSQUI_FM);var receipts=new ArrayList<>(i.receipts);receipts.add(call);var readyReceipts=List.copyOf(receipts);
        logMosquiPort=true;
        try {verifyLogMosqui(i);sounds=readyLedger;i.ledger=readyLedger;values=readyValues;i.expectedValues=readyValues;
            i.receipts=readyReceipts;i.fms++;verifyLogMosqui(i);i.completedCall=call;}
        finally {logMosquiPort=false;}
    }
    private void drawLogShadow(CdLogShadow.DrawCall call) {drawLogMosqui(call,call.source());}
    private void drawMosqui(CdMosqui.DrawCall call) {drawLogMosqui(call,call.source());}
    private void drawLogMosqui(Object call,CdObjectState source) {
        var i=requireLogMosquiCall(call);
        if(i.drawn || i.mosquito && i.entry.routine()==6 && i.angles==0)
            throw new IllegalStateException("Family Draw skipped its native source calls or repeated");
        var receipts=new ArrayList<>(i.receipts);receipts.add(call);var readyReceipts=List.copyOf(receipts);
        i.drawn=true;drawingLogMosqui=true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(i.whole),source.spriteFlags(),
                    source.centreX(),source.centreY(),source.unsignedByte(CdObjectState.ByteField.Y_RADIUS),
                    source.unsignedByte(CdObjectState.ByteField.WIDTH),source.unsignedByte(CdObjectState.ByteField.PRIORITY)),i.camera);
            verifyLogMosqui(i);if(!i.cleared)throw new IllegalStateException("Family Draw omitted its actual BCLR7");
            i.receipts=readyReceipts;i.completedCall=call;
        } catch(RuntimeException|Error original) {
            // Only this real queue call may explain its documented failed phase on the throwing path.
            i.queueFailed=drawQueue.phase()==CdObjectDrawQueue.Phase.FAILED;throw original;
        } finally {drawingLogMosqui=false;}
    }
    private CdObjectDrawQueue.LiveDrawSlot readLogMosquiDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var i=requireLogMosqui();
        if(!drawingLogMosqui || logMosquiPort || queuePort || ref.wholeIndex()!=i.whole || i.pendingCall==null)
            throw new IllegalStateException("No actual collecting family Draw read");
        queuePort=true;
        try {var s=i.pool.state(i.whole);var out=new CdObjectDrawQueue.LiveDrawSlot(s.id(),s.spriteFlags(),s.centreX(),s.centreY(),
                s.word(CdObjectState.WordField.X_FRACTION),s.longBits(CdObjectState.LongField.MAPPINGS),
                s.unsignedByte(CdObjectState.ByteField.MAP_FRAME),s.word(CdObjectState.WordField.TILE));verifyLogMosqui(i);return out;}
        finally {queuePort=false;}
    }
    private void writeLogMosquiOnScreen(CdObjectDrawQueue.CdSlotRef ref,boolean value) {
        var i=requireLogMosqui();
        if(!drawingLogMosqui || logMosquiPort || queuePort || value || i.cleared || ref.wholeIndex()!=i.whole || i.pendingCall==null)
            throw new IllegalStateException("Only actual family Draw clears its own bit7 once");
        var s=i.pool.state(i.whole);var next=s.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,s.spriteFlags()&0x7F);
        var pool=CdObjectPool.overlay(placement.capture().pool(),List.of(next));i.pool.validateRestore(pool);
        var ready=logMosquiImages(pool);queuePort=true;
        try {verifyLogMosqui(i);i.pool.write(next);i.images=ready;i.cleared=true;verifyLogMosqui(i);}
        finally {queuePort=false;}
    }

    // Source-only child-effect receipts; no new captured partition or gameplay owner.
    private enum ChildStep { ENTRY, HEADER, CHILD, RESET, TIMER, SPEED, ANIMATED, DONE, POINTS }
    private void requireChildEffectIdle() {
        requireFlowerIdle();
        if(activeChildEffect!=null || drawingChildEffect) throw new IllegalStateException("Actual child-effect invocation owns this cut");
    }
    private void requireOtherChildScopesIdle() {
        requireDoorIdle();
        if(activeLogMosqui!=null || logMosquiPort || drawingLogMosqui) throw new IllegalStateException("Actual Log/Mosqui owns this cut");
        requireOtherLogMosquiScopesIdle();
    }
    private final class ChildEffectInvocation {
        final int whole; final boolean explosive; final Object body;
        final CdPlayerFrameRuntime sourceFrame=frame; final Object stageSource=frame.stageSource();
        final CdR12aTerrainAssets sourceAssets=assets; final CdObjectPool pool=placement.pool();
        final CdObjectDrawQueue queue=drawQueue; final CdObjectPlacementRuntime.Status status=placement.status();
        final CdObjectState entry; final CdPlayerBody.State state=frame.state(); final CdPlayerBody.Environment environment=frame.environment();
        final CdPlayerFrameRuntime.ActorState actor=frame.captureActor(); final CdPlayerFrameRuntime.SharedState shared=frame.captureShared();
        final SonicCdPlayerPresentation.ScrollCapture scroll=presentation.captureAct2Scroll();
        final Object world; final long generation; final Values ownerValues=values; final CdAssetBytes reserved=values.hudObjects();
        final List<SonicCdGameModule.PastSound> ledger=sounds; final CdAssetBytes saved=placement.capture().savedFlags();
        final CdObjectDrawQueue.CameraWords camera; final int firstFree; final boolean childWanted;
        List<CdAssetBytes> images=logMosquiImages(placement.capture().pool()), readyImages;
        List<Object> receipts=List.of(),readyReceipts; Object pendingPublication,pendingCall,drawReceipt,completedOperation;
        long ordinal; ChildStep step=ChildStep.ENTRY,readyStep; boolean childWritten,readyChild,drawn,cleared,queueFailed;
        ChildEffectInvocation(int whole,boolean explosive) {
            this.whole=whole;this.explosive=explosive;body=explosive?explosion:points;entry=pool.state(whole);
            int free=-1;
            if(explosive && entry.routine()==0 && entry.unsignedByte(CdObjectState.ByteField.ROUTINE_2)==0)
                for(var slot:placement.capture().pool().slots())if(slot.id()==0){free=slot.identity().wholeIndex();break;}
            firstFree=free;childWanted=free>=0;
            var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();world=mode.getWorldSession();
            generation=mode.getLevelManager().getCompletedProductionLoadGeneration();var fg=presentation.act2ForegroundImage();
            camera=new CdObjectDrawQueue.CameraWords(fg.cameraX(),fg.cameraYWord());
        }
    }
    private void runExplosion(int whole) { runChildEffect(whole,true); }
    private void runPoints(int whole) { runChildEffect(whole,false); }
    private void runChildEffect(int whole,boolean explosive) {
        requireChildEffectIdle();requireOtherChildScopesIdle();module.requireAct2LoadingVisit(this);
        if(!runningObjects || servicing || queuePort || whole<32 || whole>=128 || values.phase()!=BEGIN_LEVEL
                || drawQueue==null || drawQueue.phase()!=CdObjectDrawQueue.Phase.COLLECTING
                || placement.pool().state(whole).id()!=(explosive?CdExplosion.OBJECT_ID:CdPoints.OBJECT_ID))
            throw new IllegalStateException("No actual cached child-effect physical dispatch");
        var i=new ChildEffectInvocation(whole,explosive);activeChildEffect=i;
        try {
            var result=explosive?explosion.run(whole).state():points.run(whole).state();verifyChildEffect(i);
            boolean done=explosive && i.entry.routine()==4;
            if(i.pendingPublication!=null || i.pendingCall!=null || i.completedOperation!=null
                    || !result.equals(i.pool.state(whole)) || done && (i.step!=ChildStep.DONE || i.drawn || i.cleared)
                    || !done && (!i.drawn || !i.cleared || i.step!=(explosive?ChildStep.ANIMATED:ChildStep.POINTS)))
                throw new IllegalStateException("Child effect omitted its actual completed native receipts");
        } catch(RuntimeException|Error original) {
            try {verifyChildFailure(i);}
            catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}
            throw original;
        } finally {activeChildEffect=null;}
    }
    private boolean childOrigin(ChildEffectInvocation i,Object call) {return i.explosive?explosion.isCurrentCall(call):points.isCurrentCall(call);}
    private void verifyChildOwners(ChildEffectInvocation i) {
        module.requireAct2LoadingVisit(this);requireOtherChildScopesIdle();var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if(activeChildEffect!=i || !runningObjects || servicing || i.body!=(i.explosive?explosion:points)
                || i.sourceFrame!=frame || i.stageSource!=frame.stageSource() || i.sourceAssets!=assets || i.pool!=placement.pool()
                || explosion.assets()!=explosionAssets || points.assets()!=pointsAssets
                || explosionAssets.explosion()!=assets.objects().explosion() || pointsAssets.points()!=assets.objects().points()
                || i.queue!=drawQueue || drawQueue==null || !(drawQueue.phase()==CdObjectDrawQueue.Phase.COLLECTING
                    || i.queueFailed && drawQueue.phase()==CdObjectDrawQueue.Phase.FAILED)
                || values.phase()!=BEGIN_LEVEL || i.world!=mode.getWorldSession()
                || i.generation!=mode.getLevelManager().getCompletedProductionLoadGeneration()
                || frame.state()!=i.state || frame.environment()!=i.environment || !frame.captureActor().equals(i.actor)
                || !frame.captureShared().equals(i.shared) || values!=i.ownerValues || sounds!=i.ledger
                || !sameBytes(values.hudObjects(),i.reserved) || !presentation.sameAct2Scroll(i.scroll))
            throw new IllegalStateException("Child effect changed or borrowed another World/Frame/source owner");
    }
    private boolean childImagesEqual(ChildEffectInvocation i,List<CdAssetBytes> expected) {
        var cut=placement.capture();i.pool.validateRestore(cut.pool());
        if(expected==null || expected.size()!=96 || !cut.status().equals(i.status) || !sameBytes(cut.savedFlags(),i.saved))return false;
        for(int at=0;at<96;at++)if(!Arrays.equals(cut.pool().slots().get(at).copyBytes(),expected.get(at).copy()))return false;
        return true;
    }
    private void verifyChildEffect(ChildEffectInvocation i) {
        verifyChildOwners(i);if(!childImagesEqual(i,i.images))throw new IllegalStateException("Child effect changed unowned full96/source/saved/status bytes");
    }
    private void verifyChildFailure(ChildEffectInvocation i) {
        verifyChildOwners(i);
        // A throwing before/after observer can retain only the exact prebuilt old or stored full64 prefix.
        if(!childImagesEqual(i,i.images) && !(i.pendingPublication!=null && childImagesEqual(i,i.readyImages)))
            throw new IllegalStateException("Throwing child effect differs from both exact native publication cuts");
    }
    private ChildEffectInvocation requireChildEffect() {
        var i=activeChildEffect;if(i==null)throw new IllegalStateException("No actual child-effect invocation");verifyChildEffect(i);return i;
    }
    private void validateChildCut(ChildEffectInvocation i,CdObjectPlacementRuntime.Snapshot cut) {
        placement.validateRestore(cut);
        if(!cut.status().equals(i.status) || !sameBytes(cut.savedFlags(),i.saved))throw new IllegalStateException("Foreign child-effect source cut");
        for(int at=0;at<96;at++)if(!Arrays.equals(cut.pool().slots().get(at).copyBytes(),i.images.get(at).copy()))
            throw new IllegalStateException("Child-effect supplied full96 cut differs");
    }
    private void prepareChildPublication(ChildEffectInvocation i,Object call,long ordinal,CdObjectPlacementRuntime.Snapshot cut,
                                         CdObjectState source,CdObjectState before,CdObjectState output,ChildStep next,boolean child) {
        if(!childOrigin(i,call) || drawingChildEffect || queuePort || i.pendingPublication!=null || i.pendingCall!=null
                || i.completedOperation!=null || ordinal!=i.ordinal+1 || !source.equals(i.pool.state(i.whole))
                || !source.identity().equals(i.entry.identity()) || !before.equals(i.pool.state(before.identity().wholeIndex())))
            throw new IllegalStateException("Child publication lacks exact pending producer/ordinal/source");
        validateChildCut(i,cut);var images=new ArrayList<>(i.images);
        if(output!=null) {
            if(!output.identity().equals(before.identity()))throw new IllegalArgumentException("Child publication changed physical identity");
            i.pool.validateRestore(CdObjectPool.overlay(cut.pool(),List.of(output)));
        }
        images.set(before.identity().wholeIndex()-32,CdAssetBytes.copyOf(output==null?new byte[64]:output.copyBytes()));
        var receipts=new ArrayList<>(i.receipts);receipts.add(call);
        i.readyImages=List.copyOf(images);i.readyReceipts=List.copyOf(receipts);i.readyStep=next;i.readyChild=child;i.pendingPublication=call;
    }
    private CdObjectState explosionAnimationBase(ChildEffectInvocation i,CdObjectState source) {
        if(i.entry.routine()!=0 || i.step!=ChildStep.HEADER && i.step!=ChildStep.CHILD)return source;
        var value=source.moveByte(CdObjectState.ByteField.COLLISION_TYPE,0).moveByte(CdObjectState.ByteField.ANIMATION_FRAME,0)
                .moveByte(CdObjectState.ByteField.ANIMATION_TIME,0).moveWord(CdObjectState.WordField.ANIMATION_WORD,0);
        return source.subtype()==0?value:value.moveWord(CdObjectState.WordField.ANIMATION_WORD,0x100);
    }
    private boolean childAnimationSite(ChildEffectInvocation i) {
        return i.entry.routine()==2 && i.step==ChildStep.ENTRY || i.entry.routine()==0
                && (i.step==ChildStep.HEADER || i.step==ChildStep.CHILD) && i.childWritten==i.childWanted;
    }
    private void beforeExplosionPublication(CdExplosion.Publication c) {
        var i=requireChildEffect();if(!i.explosive || !childOrigin(i,c) || i.pendingPublication!=null || i.pendingCall!=null || i.completedOperation!=null)
            throw new IllegalStateException("Wrong/cloned/repeated cached child producer");
        var source=c.source();var output=c.output();CdObjectState expected;ChildStep next;boolean child=i.childWritten;
        switch(c.kind()) {
            case INIT_HEADER -> {
                if(i.entry.routine()!=0 || i.step!=ChildStep.ENTRY)throw new IllegalStateException("Repeated/late Explosion Init");
                expected=source.addByte(CdObjectState.ByteField.ROUTINE,2).moveByte(CdObjectState.ByteField.SPRITE_FLAGS,source.spriteFlags()|4)
                        .moveByte(CdObjectState.ByteField.PRIORITY,1).moveWord(CdObjectState.WordField.TILE,source.unsignedByte(CdObjectState.ByteField.LAYER)==0?0x8680:0x0680)
                        .moveLong(CdObjectState.LongField.MAPPINGS,explosion.assets().mappingsAddress());next=ChildStep.HEADER;
            }
            case POINTS_CHILD -> {
                if(i.step!=ChildStep.HEADER || !i.childWanted || i.childWritten || c.before().identity().wholeIndex()!=i.firstFree || c.before().id()!=0)
                    throw new IllegalStateException("Explosion child is not its actual first-free dirty slot");
                int subtype=((source.word(CdObjectState.WordField.EXPLOSION_POINTS_FRAME)&255)>>>1)|0x80;
                expected=c.before().moveByte(CdObjectState.ByteField.ID,0x1C).moveWord(CdObjectState.WordField.X,source.centreX())
                        .moveWord(CdObjectState.WordField.Y,source.centreY()).moveByte(CdObjectState.ByteField.SUBTYPE,subtype);
                next=ChildStep.CHILD;child=true;
            }
            case ANIMATION_RESULT -> {
                if(!childAnimationSite(i) || output==null)throw new IllegalStateException("Explosion animation omitted Init/child receipt");
                var input=explosionAnimationBase(i,source);
                expected=input.moveByte(CdObjectState.ByteField.PREVIOUS_ANIMATION,input.unsignedByte(CdObjectState.ByteField.ANIMATION))
                        .moveByte(CdObjectState.ByteField.ANIMATION_FRAME,output.unsignedByte(CdObjectState.ByteField.ANIMATION_FRAME))
                        .moveByte(CdObjectState.ByteField.ANIMATION_TIME,output.unsignedByte(CdObjectState.ByteField.ANIMATION_TIME))
                        .moveByte(CdObjectState.ByteField.MAP_FRAME,output.unsignedByte(CdObjectState.ByteField.MAP_FRAME))
                        .moveByte(CdObjectState.ByteField.SPRITE_FLAGS,output.spriteFlags())
                        .moveByte(CdObjectState.ByteField.ROUTINE,output.routine());
                if((output.spriteFlags()&~3)!=(input.spriteFlags()&~3)
                        || output.routine()!=2 && output.routine()!=4)
                    throw new IllegalStateException("Explosion animation changed unowned flags/routine");
                // The actual producer ran the shared animator once; World never synthesizes successful script bytes.
                next=ChildStep.ANIMATED;
            }
            case FAILURE_RESET -> {
                var input=explosionAnimationBase(i,source);
                if(!childAnimationSite(i) || input.unsignedByte(CdObjectState.ByteField.ANIMATION)==input.unsignedByte(CdObjectState.ByteField.PREVIOUS_ANIMATION))
                    throw new IllegalStateException("Unreached animation reset failure prefix");
                expected=input.moveByte(CdObjectState.ByteField.PREVIOUS_ANIMATION,input.unsignedByte(CdObjectState.ByteField.ANIMATION))
                        .moveByte(CdObjectState.ByteField.ANIMATION_FRAME,0).moveByte(CdObjectState.ByteField.ANIMATION_TIME,0);next=ChildStep.RESET;
            }
            case FAILURE_TIMER -> {
                if(!childAnimationSite(i) && i.step!=ChildStep.RESET)throw new IllegalStateException("Unreached animation timer failure prefix");
                expected=explosionAnimationBase(i,source).addByte(CdObjectState.ByteField.ANIMATION_TIME,-1);next=ChildStep.TIMER;
            }
            case FAILURE_SPEED -> {
                if(i.step!=ChildStep.TIMER)throw new IllegalStateException("Animation speed read before timer prefix");
                var table=explosion.assets().explosion().animationTable();int animation=source.unsignedByte(CdObjectState.ByteField.ANIMATION);
                if(animation>=2)throw new IllegalStateException("Unrepresented Explosion animation directory");
                expected=source.moveByte(CdObjectState.ByteField.ANIMATION_TIME,table.unsignedByte((short)table.unsignedWord(2*animation)));next=ChildStep.SPEED;
            }
            case FLOWER -> {
                if(i.entry.routine()!=4 || i.step!=ChildStep.ENTRY || source.unsignedByte(CdObjectState.ByteField.ROUTINE_2)!=0)
                    throw new IllegalStateException("Unreached Flower conversion");
                expected=source.moveByte(CdObjectState.ByteField.ID,0x1F).moveByte(CdObjectState.ByteField.ROUTINE,0);next=ChildStep.DONE;
            }
            case DELETE -> {
                if(i.entry.routine()!=4 || i.step!=ChildStep.ENTRY || source.unsignedByte(CdObjectState.ByteField.ROUTINE_2)==0 || output!=null)
                    throw new IllegalStateException("Unreached nonbadnik delete");
                expected=null;next=ChildStep.DONE;
            }
            default -> throw new IllegalArgumentException("Unknown Explosion store");
        }
        if(c.kind()!=CdExplosion.StoreKind.POINTS_CHILD && !c.before().identity().equals(source.identity())
                || !Objects.equals(expected,output))throw new IllegalStateException("Explosion publication changed another native byte");
        prepareChildPublication(i,c,c.ordinal(),c.cut(),source,c.before(),output,next,child);
    }
    private void beforePointsPublication(CdPoints.Publication c) {
        var i=requireChildEffect();if(i.explosive || !childOrigin(i,c) || i.step!=ChildStep.ENTRY || i.pendingPublication!=null || i.pendingCall!=null || i.completedOperation!=null)
            throw new IllegalStateException("Wrong/cloned/repeated Points producer");
        var source=c.source();var expected=source;
        if((source.subtype()&0x80)==0)throw new IllegalStateException("Dynamic HUD is not the Points producer");
        if(i.entry.routine()==0)expected=source.addByte(CdObjectState.ByteField.ROUTINE,2)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS,source.spriteFlags()|4).moveWord(CdObjectState.WordField.TILE,0x6C6)
                .moveLong(CdObjectState.LongField.MAPPINGS,points.assets().mappingsAddress())
                .moveByte(CdObjectState.ByteField.MAP_FRAME,source.subtype()&0x7F).moveByte(CdObjectState.ByteField.POINTS_TIMER,24);
        else if(i.entry.routine()!=2)throw new IllegalStateException("Unrepresented Points routine");
        expected=expected.addByte(CdObjectState.ByteField.POINTS_TIMER,-1);
        if(c.kind()==CdPoints.StoreKind.DELETE) {
            if(expected.unsignedByte(CdObjectState.ByteField.POINTS_TIMER)!=0 || c.output()!=null)throw new IllegalStateException("Unreached Points deletion");expected=null;
        } else if(c.kind()==CdPoints.StoreKind.MAIN_RESULT) {
            if(expected.unsignedByte(CdObjectState.ByteField.POINTS_TIMER)==0)throw new IllegalStateException("Points skipped its native delete");expected=expected.addWord(CdObjectState.WordField.Y,-2);
        } else throw new IllegalArgumentException("Unknown Points store");
        if(!c.before().identity().equals(source.identity()) || !Objects.equals(expected,c.output()))throw new IllegalStateException("Points changed an unowned full64 byte");
        prepareChildPublication(i,c,c.ordinal(),c.cut(),source,c.before(),c.output(),ChildStep.POINTS,false);
    }
    private void afterChildPublication(Object call) {
        var i=activeChildEffect;
        if(i==null || i.pendingPublication!=call || !childOrigin(i,call) || i.completedOperation!=null)
            throw new IllegalStateException("No exact child publication completion");
        verifyChildOwners(i);if(!childImagesEqual(i,i.readyImages))throw new IllegalStateException("Native child publication differs from prebuilt full96 output");
        i.images=i.readyImages;i.receipts=i.readyReceipts;i.step=i.readyStep;i.childWritten=i.readyChild;i.ordinal++;
        i.readyImages=null;i.readyReceipts=null;i.pendingPublication=null;i.completedOperation=call;
    }
    private void beforeChildCall(Object call) {
        var i=requireChildEffect();CdObjectState source;CdObjectPlacementRuntime.Snapshot cut;
        if(call instanceof CdExplosion.DrawCall c && i.explosive){source=c.source();cut=c.cut();}
        else if(call instanceof CdPoints.DrawCall c && !i.explosive){source=c.source();cut=c.cut();}
        else throw new IllegalArgumentException("Wrong child-effect external call");
        if(!childOrigin(i,call) || i.pendingCall!=null || i.pendingPublication!=null || i.completedOperation!=null || i.drawn
                || i.step!=(i.explosive?ChildStep.ANIMATED:ChildStep.POINTS) || !source.equals(i.pool.state(i.whole)))
            throw new IllegalStateException("Child Draw omitted its actual completed stores");
        validateChildCut(i,cut);i.pendingCall=call;
    }
    private void afterChildCall(Object call) {
        var i=requireChildEffect();if(!childOrigin(i,call) || i.pendingCall!=call || i.drawReceipt!=call || !i.drawn || !i.cleared)
            throw new IllegalStateException("Child Draw returned without its private once-only receipt");
        i.pendingCall=null;i.completedOperation=call;
    }
    private void requirePreparedChildReceipt(Object call) {
        var i=requireChildEffect();
        if(!childOrigin(i,call) || i.completedOperation!=null || !(i.pendingPublication==call && i.readyImages!=null || i.pendingCall==call))
            throw new IllegalStateException("Child observer returned without its private prepared receipt");
    }
    private void consumeCompletedChildReceipt(Object call) {
        var i=activeChildEffect;if(i==null || !childOrigin(i,call) || i.completedOperation!=call || i.pendingPublication!=null || i.pendingCall!=null)
            throw new IllegalStateException("Child observer returned without its private completed receipt");
        verifyChildEffect(i);i.completedOperation=null;
    }
    private void drawExplosion(CdExplosion.DrawCall c) { drawChildEffect(c,c.source()); }
    private void drawPoints(CdPoints.DrawCall c) { drawChildEffect(c,c.source()); }
    private void drawChildEffect(Object call,CdObjectState source) {
        var i=requireChildEffect();
        if(!childOrigin(i,call) || i.pendingCall!=call || i.drawReceipt!=null || drawingChildEffect || queuePort || i.drawn)
            throw new IllegalStateException("Public/cloned/repeated child Draw has no actual producer receipt");
        var receipts=new ArrayList<>(i.receipts);receipts.add(call);var readyReceipts=List.copyOf(receipts);
        i.drawn=true;drawingChildEffect=true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(i.whole),source.spriteFlags(),source.centreX(),source.centreY(),
                    source.unsignedByte(CdObjectState.ByteField.Y_RADIUS),source.unsignedByte(CdObjectState.ByteField.WIDTH),source.unsignedByte(CdObjectState.ByteField.PRIORITY)),i.camera);
            verifyChildEffect(i);if(!i.cleared)throw new IllegalStateException("Child Draw omitted actual BCLR7");
            i.receipts=readyReceipts;i.drawReceipt=call;
        } catch(RuntimeException|Error original){i.queueFailed=drawQueue.phase()==CdObjectDrawQueue.Phase.FAILED;throw original;}
        finally {drawingChildEffect=false;}
    }
    private CdObjectDrawQueue.LiveDrawSlot readChildDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var i=requireChildEffect();if(!drawingChildEffect || queuePort || i.pendingCall==null || ref.wholeIndex()!=i.whole)
            throw new IllegalStateException("No actual child-effect queue read");
        queuePort=true;
        try {var s=i.pool.state(i.whole);var out=new CdObjectDrawQueue.LiveDrawSlot(s.id(),s.spriteFlags(),s.centreX(),s.centreY(),s.word(CdObjectState.WordField.X_FRACTION),
                s.longBits(CdObjectState.LongField.MAPPINGS),s.unsignedByte(CdObjectState.ByteField.MAP_FRAME),s.word(CdObjectState.WordField.TILE));verifyChildEffect(i);return out;}
        finally {queuePort=false;}
    }
    private void writeChildOnScreen(CdObjectDrawQueue.CdSlotRef ref,boolean value) {
        var i=requireChildEffect();if(!drawingChildEffect || queuePort || i.pendingCall==null || value || i.cleared || ref.wholeIndex()!=i.whole)
            throw new IllegalStateException("Only actual child Draw clears bit7 exactly once");
        var source=i.pool.state(i.whole);var next=source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,source.spriteFlags()&0x7F);
        var ready=logMosquiImages(CdObjectPool.overlay(placement.capture().pool(),List.of(next)));queuePort=true;
        try {verifyChildEffect(i);i.pool.write(next);i.images=ready;i.cleared=true;verifyChildEffect(i);}
        finally {queuePort=false;}
    }

    // Horizontal Door: one actual cached dispatch, not another Frame/bank/queue/current owner.
    private void requireDoorIdle() {
        requireTimeStarsIdle();
        if(activeDoor!=null || doorPort || drawingDoor)throw new IllegalStateException("Actual Door owns this source cut");
    }
    private final class DoorInvocation {
        final int whole; final CdTunnelDoor body=tunnelDoor; final CdPlayerFrameRuntime sourceFrame=frame;
        final Object stageSource=frame.stageSource(); final CdR12aTerrainAssets sourceAssets=assets;
        final CdObjectPool pool=placement.pool(); final CdObjectDrawQueue queue=drawQueue;
        final CdObjectPlacementRuntime.Status status=placement.status(); final CdObjectState entry;
        final Object world; final long generation; final CdObjectDrawQueue.CameraWords camera;
        final SonicCdPlayerPresentation.ScrollCapture scroll=presentation.captureAct2Scroll();
        final CdAssetBytes reserved=values.hudObjects();
        CdPlayerBody.State state=frame.state(); CdPlayerFrameRuntime.ActorState actor=frame.captureActor();
        CdPlayerFrameRuntime.SharedState shared=frame.captureShared(); Values expectedValues=values;
        List<SonicCdGameModule.PastSound> ledger=sounds; CdAssetBytes saved=placement.capture().savedFlags();
        List<CdAssetBytes> images=logMosquiImages(placement.capture().pool()), readyImages;
        CdAssetBytes readySaved, secondary; List<Object> receipts=List.of(), readyReceipts;
        CdTunnelDoor.Publication pendingPublication; Object pendingCall, completedCall, completedReturn, lastReturnedCall;
        CdTunnelDoor.PrefixSolidCut pendingCut; CdSolidObject.Outcome pendingOutcome;
        long ordinal; boolean primaryPreflight, primaryDone, opened, secondaryRead, secondaryPreflight, secondaryDone;
        boolean child, fm, drawn, cleared, stopped, queueFailed, failed;
        DoorInvocation(int whole) {
            this.whole=whole;entry=pool.state(whole);var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world=mode.getWorldSession();generation=mode.getLevelManager().getCompletedProductionLoadGeneration();
            var fg=presentation.act2ForegroundImage();camera=new CdObjectDrawQueue.CameraWords(fg.cameraX(),fg.cameraYWord());
        }
    }
    // Actual Flower source scope; no new capture partition or persistent flower-storage owner.
    private enum FlowerStep { ENTRY, INIT, MOVE, GROW, DONE, ANIMATED, RESET, TIMER, SPEED, BLOCKED }
    private void requireFlowerIdle() {
        requireTunnelPathIdle();
        if(activeFlower!=null || flowerPort || drawingFlower)throw new IllegalStateException("Actual Flower invocation owns this cut");
    }
    private void requireOtherFlowerScopesIdle() {
        if(activeChildEffect!=null || drawingChildEffect)throw new IllegalStateException("Actual child-effect invocation owns this cut");
        requireOtherChildScopesIdle();
    }
    private final class FlowerInvocation {
        final int whole; final CdFlower body=flower; final CdFlower.Assets bodyAssets=flowerAssets;
        final CdPlayerFrameRuntime sourceFrame=frame; final Object stageSource=frame.stageSource();
        final CdR12aTerrainAssets sourceAssets=assets; final CdObjectPool pool=placement.pool();
        final CdObjectDrawQueue queue=drawQueue; final CdObjectPlacementRuntime.Status status=placement.status();
        final CdObjectState entry; final CdPlayerBody.State state=frame.state();
        CdPlayerBody.Environment environment=frame.environment();
        final CdPlayerFrameRuntime.ActorState actor=frame.captureActor(); CdPlayerFrameRuntime.SharedState shared=frame.captureShared();
        final SonicCdPlayerPresentation.ScrollCapture scroll=presentation.captureAct2Scroll();
        final Object world; final long generation; Values expectedValues=values;
        final CdAssetBytes reserved=values.hudObjects(),saved=placement.capture().savedFlags();
        final List<SonicCdGameModule.PastSound> ledger=sounds; final CdObjectDrawQueue.CameraWords camera;
        List<CdAssetBytes> images=logMosquiImages(placement.capture().pool()),readyImages;
        Object pendingPublication,pendingCall,pendingFloor,completedOperation,angleReceipt,drawReceipt;
        long ordinal; int angles; FlowerStep step=FlowerStep.ENTRY,readyStep;
        CdObjectCollisionCheck.FloorDistance floor; boolean floorCompleted,drawn,cleared,queueFailed;
        FlowerInvocation(int whole) {
            this.whole=whole;entry=pool.state(whole);var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world=mode.getWorldSession();generation=mode.getLevelManager().getCompletedProductionLoadGeneration();
            var fg=presentation.act2ForegroundImage();camera=new CdObjectDrawQueue.CameraWords(fg.cameraX(),fg.cameraYWord());
        }
    }
    private void runDoor(int whole) {
        requireChildEffectIdle();
        requireDoorIdle();requireLogMosquiIdle();requireOtherLogMosquiScopesIdle();module.requireAct2LoadingVisit(this);
        if(!runningObjects || servicing || queuePort || whole<32 || whole>=128 || values.phase()!=BEGIN_LEVEL
                || drawQueue==null || drawQueue.phase()!=CdObjectDrawQueue.Phase.COLLECTING
                || placement.pool().state(whole).id()!=CdTunnelDoor.OBJECT_ID)
            throw new IllegalStateException("No actual cached horizontal Door dispatch");
        var i=new DoorInvocation(whole);activeDoor=i;
        try {
            var gates=frame.environment().movement().gates();
            var result=tunnelDoor.run(whole,new CdTunnelDoor.Globals(gates.timeZone(),gates.timeWarpDirection(),i.camera.xWord()),
                    new CdTunnelDoor.SolidInputs(springPlayer(),new CdSolidObject.Gates(frame.environment().globals().debugMode(),gates.miniSonic()),springGlobals()));
            verifyDoor(i);
            if(i.pendingCall!=null || i.pendingPublication!=null || i.pendingCut!=null || i.stopped || i.failed || !i.drawn || !i.cleared
                    || (i.entry.routine()==0 || i.entry.routine()==2) && !i.secondaryDone
                    || !result.state().equals(i.pool.state(whole)) || !result.finalPrimary().equals(springPlayer())
                    || !result.finalGlobals().equals(springGlobals()))
                throw new IllegalStateException("Door omitted actual helper/return/Draw receipts");
        } catch(RuntimeException|Error original) {
            try {verifyDoor(i);}catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}
            throw original;
        } finally {activeDoor=null;}
    }
    private void verifyDoorOwners(DoorInvocation i) {
        requireChildEffectIdle();
        module.requireAct2LoadingVisit(this);requireLogMosquiIdle();requireOtherLogMosquiScopesIdle();
        var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if(activeDoor!=i || !runningObjects || servicing || i.body!=tunnelDoor || i.sourceFrame!=frame || i.stageSource!=frame.stageSource()
                || i.sourceAssets!=assets || i.pool!=placement.pool() || i.queue!=drawQueue || drawQueue==null
                || !(drawQueue.phase()==CdObjectDrawQueue.Phase.COLLECTING || i.queueFailed && drawQueue.phase()==CdObjectDrawQueue.Phase.FAILED)
                || values.phase()!=BEGIN_LEVEL || i.world!=mode.getWorldSession()
                || i.generation!=mode.getLevelManager().getCompletedProductionLoadGeneration()
                || frame.state()!=i.state || !frame.captureActor().equals(i.actor) || !frame.captureShared().equals(i.shared)
                || values!=i.expectedValues || sounds!=i.ledger || !placement.status().equals(i.status)
                || !sameBytes(values.hudObjects(),i.reserved) || !presentation.sameAct2Scroll(i.scroll))
            throw new IllegalStateException("Door changed or borrowed another actual World/Frame/source owner");
    }
    private void verifyDoorImages(DoorInvocation i,List<CdAssetBytes> images,CdAssetBytes saved) {
        var actual=placement.capture();placement.validateRestore(actual);
        if(images.size()!=96 || !actual.status().equals(i.status) || !sameBytes(actual.savedFlags(),saved))
            throw new IllegalStateException("Door changed status or complete saved bank");
        for(int at=0;at<96;at++)if(!Arrays.equals(actual.pool().slots().get(at).copyBytes(),images.get(at).copy()))
            throw new IllegalStateException("Door full96 output differs at physical "+(at+32));
    }
    private void verifyDoor(DoorInvocation i) {verifyDoorOwners(i);verifyDoorImages(i,i.images,i.saved);}
    private DoorInvocation requireDoor() {
        var i=activeDoor;if(i==null)throw new IllegalStateException("No actual Door invocation");verifyDoor(i);return i;
    }
    private void doorCut(DoorInvocation i,CdObjectPlacementRuntime.Snapshot cut,CdObjectState source) {
        placement.validateRestore(cut);verifyDoorImages(i,logMosquiImages(cut.pool()),cut.savedFlags());
        if(!cut.status().equals(i.status) || !source.equals(i.pool.state(i.whole)))throw new IllegalStateException("Stale Door source cut");
    }
    private void beforeDoorPublication(CdTunnelDoor.Publication c) {
        var i=requireDoor();
        if(!tunnelDoor.isCurrentCall(c) || i.pendingPublication!=null || i.pendingCall!=null || doorPort || drawingDoor || queuePort
                || i.failed || c.ordinal()!=i.ordinal+1 || !c.source().identity().equals(i.entry.identity()))
            throw new IllegalStateException("Door store lacks its exact pending producer/sequence");
        doorCut(i,c.cut(),c.source());var next=new ArrayList<>(i.images);var saved=i.saved;
        switch(c.kind()) {
            case SOURCE -> {
                var out=Objects.requireNonNull(c.output());i.pool.validateRestore(CdObjectPool.overlay(c.cut().pool(),List.of(out)));
                if(!out.identity().equals(i.entry.identity()))throw new IllegalArgumentException("Door source store changed identity");
                byte[] before=c.source().copyBytes(),after=out.copyBytes();
                for(int at=0;at<64;at++) {
                    int mask=at==1?7:at>=2&&at<=7 || at==0x16 || at>=0x18&&at<=0x1E || at==0x24 || at==0x25?255:0;
                    if(((before[at]^after[at])&~mask&255)!=0)throw new IllegalArgumentException("Door changed unowned source byte "+at);
                }
                next.set(i.whole-32,CdAssetBytes.copyOf(after));
            }
            case CHILD -> {
                var out=Objects.requireNonNull(c.output());i.pool.validateRestore(CdObjectPool.overlay(c.cut().pool(),List.of(out)));
                int whole=out.identity().wholeIndex();var before=c.cut().pool().slots().get(whole-32);
                if(i.child || !i.opened || i.entry.subtype()!=0 || before.id()!=0 || out.id()!=0x0B || whole==i.whole
                        || !before.moveByte(CdObjectState.ByteField.ID,0x0B).moveWord(CdObjectState.WordField.X,c.source().centreX())
                        .moveWord(CdObjectState.WordField.Y,c.source().centreY()-4).equals(out))
                    throw new IllegalStateException("Door child differs from actual dirty ID/X/Y allocation prefix");
                next.set(whole-32,CdAssetBytes.copyOf(out.copyBytes()));
            }
            case SAVED -> {
                if(!i.drawn || !i.cleared || c.output()!=c.source() || c.savedIndex()<2 || c.savedIndex()>=saved.length())
                    throw new IllegalStateException("Door saved clear lacks the reached Draw/tail source");
                byte[] raw=saved.copy();raw[c.savedIndex()]&=0x7F;saved=CdAssetBytes.copyOf(raw);
            }
            case DELETE -> {
                if(!i.drawn || !i.cleared || c.output()!=null)throw new IllegalStateException("Door delete precedes actual Draw");
                next.set(i.whole-32,CdAssetBytes.copyOf(new byte[64]));
            }
        }
        var receipts=new ArrayList<>(i.receipts);receipts.add(c);
        i.readyImages=List.copyOf(next);i.readySaved=saved;i.readyReceipts=List.copyOf(receipts);i.pendingPublication=c;
    }
    private void afterDoorPublication(CdTunnelDoor.Publication c) {
        var i=activeDoor;if(i==null || i.pendingPublication!=c || !tunnelDoor.isCurrentCall(c))throw new IllegalStateException("No exact Door store receipt");
        verifyDoorOwners(i);verifyDoorImages(i,i.readyImages,i.readySaved);
        if(c.kind()==CdTunnelDoor.MutationKind.SOURCE && (i.entry.routine()==0 || i.entry.routine()==2)
                && c.source().routine()==2 && c.output().routine()==4 && !i.primaryDone)i.opened=true;
        if(c.kind()==CdTunnelDoor.MutationKind.CHILD)i.child=true;
        i.images=i.readyImages;i.saved=i.readySaved;i.receipts=i.readyReceipts;i.ordinal++;
        i.readyImages=null;i.readySaved=null;i.readyReceipts=null;i.pendingPublication=null;
    }
    private void beforeDoorCall(Object call) {
        var i=requireDoor();
        if(!tunnelDoor.isCurrentCall(call) || i.pendingCall!=null || i.pendingPublication!=null || doorPort || drawingDoor || queuePort || i.stopped
                || i.failed && !(call instanceof CdTunnelDoor.SecondaryReadCall))
            throw new IllegalStateException("No actual pending Door external producer");
        if(call instanceof CdTunnelDoor.SecondaryReadCall c) {
            doorCut(i,c.cut(),c.source());
            if(i.entry.routine()!=0 && i.entry.routine()!=2 || !i.primaryDone && !i.opened || i.drawn || i.child && !i.fm)
                throw new IllegalStateException("Door secondary read precedes actual primary completion");
        } else if(call instanceof CdTunnelDoor.PrefixSolidCut c) {
            i.pool.validateRestore(c.callCut());verifyDoorImages(i,logMosquiImages(c.callCut()),i.saved);
            if(i.pendingCut!=null || !c.objectIdentity().equals(i.entry.identity()) || !c.expectedPrimary().equals(springPlayer())
                    || !c.expectedGlobals().equals(springGlobals()) || !c.expectedGates().equals(doorGates()))
                throw new IllegalStateException("Door pre-Top cut differs from actual post-prefix full Frame/source");
            if(c.participant()==CdTunnelDoor.Participant.PRIMARY) {
                if(i.primaryPreflight || i.primaryDone || i.opened || i.secondaryRead || c.secondary().isPresent()
                        || i.pool.state(i.whole).routine()!=2)throw new IllegalStateException("Door primary Top is not its first participant");
            } else if(!i.secondaryRead || !i.primaryDone && !i.opened || i.secondaryPreflight || i.secondaryDone || c.secondary().isEmpty()
                    || !sameBytes(c.secondary().orElseThrow(),reservedImage(1)) || !sameBytes(c.secondary().orElseThrow(),i.secondary))
                throw new IllegalStateException("Door secondary Top lacks actual reached whole1 bytes");
        } else if(call instanceof CdTunnelDoor.PrefixSolidCall c) {
            if(c.cut()!=i.pendingCut || i.pendingOutcome!=null)throw new IllegalStateException("Door outcome lacks actual preflight");
            i.pendingOutcome=c.outcome();
        } else if(call instanceof CdTunnelDoor.FmCall c) {
            doorCut(i,c.cut(),c.source());
            if(!i.opened || !i.child || i.fm || i.entry.subtype()!=0 || i.secondaryRead || i.drawn || c.sound()!=CdTunnelDoor.FmSound.FM_A4)
                throw new IllegalStateException("Door FM_A4 precedes its actual successful child prefix");
        } else if(call instanceof CdTunnelDoor.DrawCall c) {
            doorCut(i,c.cut(),c.source());
            if(i.drawn || (i.entry.routine()==0 || i.entry.routine()==2) && !i.secondaryDone)
                throw new IllegalStateException("Door Draw skipped its native participants");
        } else throw new IllegalArgumentException("Unknown Door producer call");
        i.pendingCall=call;i.completedCall=null;i.completedReturn=null;i.lastReturnedCall=null;
    }
    private void afterDoorCall(Object call,Object returned) {
        var i=requireDoor();
        if(!tunnelDoor.isCurrentCall(call) || i.pendingCall!=call || i.completedCall!=call || returned!=i.completedReturn)
            throw new IllegalStateException("Door returned without its exact private adoption/readback receipt");
        i.lastReturnedCall=call;i.pendingCall=null;i.completedCall=null;i.completedReturn=null;
    }
    /** A throw permits only genuine read-only secondary verification, never later native gameplay work. */
    private void failedDoorCall(Object call) {
        var i=activeDoor;
        if(i==null || !tunnelDoor.isCurrentCall(call) || i.pendingCall!=call && i.lastReturnedCall!=call)return;
        verifyDoor(i);i.failed=true;i.pendingCall=null;i.completedCall=null;i.completedReturn=null;i.lastReturnedCall=null;
    }
    private DoorInvocation requireDoorCall(Object call) {
        var i=requireDoor();
        if(call==null || !tunnelDoor.isCurrentCall(call) || i.pendingCall!=call || i.completedCall!=null || doorPort || drawingDoor || queuePort)
            throw new IllegalStateException("Public/cloned/repeated Door values grant no actual invocation authority");return i;
    }
    private CdSolidObject.Gates doorGates() {return new CdSolidObject.Gates(frame.environment().globals().debugMode(),frame.environment().movement().gates().miniSonic());}
    private CdAssetBytes readDoorSecondary() {
        var i=activeDoor;if(i==null || !(i.pendingCall instanceof CdTunnelDoor.SecondaryReadCall call))throw new IllegalStateException("No reached actual Door whole1 read");
        requireDoorCall(call);var image=reservedImage(1);
        if(i.secondary!=null && !sameBytes(image,i.secondary))throw new IllegalStateException("Actual Door secondary source changed");
        var readyValues=i.failed?values:preparedMonitorValues(TUNNEL_DOOR_SECONDARY_READ);
        doorPort=true;
        try {verifyDoor(i);values=readyValues;i.expectedValues=readyValues;i.secondary=image;i.secondaryRead=true;verifyDoor(i);
            i.completedReturn=image;i.completedCall=call;return image;}
        finally {doorPort=false;}
    }
    private CdTunnelDoor.LiveSolid preflightDoor(CdTunnelDoor.PrefixSolidCut call) {
        var i=requireDoorCall(call);var live=new CdTunnelDoor.LiveSolid(springPlayer(),doorGates(),springGlobals());
        doorPort=true;
        try {verifyDoor(i);i.pendingCut=call;
            if(call.participant()==CdTunnelDoor.Participant.PRIMARY)i.primaryPreflight=true;else i.secondaryPreflight=true;
            verifyDoor(i);i.completedReturn=live;i.completedCall=call;return live;}
        finally {doorPort=false;}
    }
    private CdTunnelDoor.LiveSolid commitDoor(CdTunnelDoor.PrefixSolidCall call) {
        var i=requireDoorCall(call);var cut=call.cut();
        if(cut!=i.pendingCut || call.outcome()!=i.pendingOutcome || !cut.expectedPrimary().equals(springPlayer())
                || !cut.expectedGlobals().equals(springGlobals()) || !cut.expectedGates().equals(doorGates()))
            throw new IllegalStateException("Door commit lacks its exact actual helper outcome/Frame cut");
        boolean primary=cut.participant()==CdTunnelDoor.Participant.PRIMARY,stopped;
        CdObjectState object;List<CdObjectState> others;Set<CdObjectState.ByteField> writes;
        List<CdPlayerTouchResponse.FmSound> requests;CdSolidObject.Player helper;CdSolidObject.Globals globals;
        CdPlayerFrameRuntime.ObjectSolidPublication readyFrame=null;
        if(call.outcome() instanceof CdSolidObject.Completed done) {
            var result=done.completedResult();if(result.entry()!=CdSolidObject.Entry.TOP_SOLID_OBJECT)throw new IllegalArgumentException("Door helper entry changed");
            object=result.object();others=result.otherSlots();writes=result.objectWrites();requests=result.fmRequests();helper=result.player();globals=result.globals();stopped=false;
            if(primary)readyFrame=frame.prepareObjectSolidResult(cut.expectedPrimary(),cut.expectedGlobals(),result);
            else if(!result.playerWrites().isEmpty() || !result.globalWrites().isEmpty() || !others.isEmpty() || !requests.isEmpty()
                    || result.path()!=CdSolidObject.Path.GET_OFF || result.getOffReason().orElse(null)!=CdSolidObject.GetOffReason.PLAYER_ABSENT
                    || result.d0().orElse(-1)!=0 || !writes.equals(Set.of(CdObjectState.ByteField.SOLID_TYPE))
                    || !i.pool.state(i.whole).moveByte(CdObjectState.ByteField.SOLID_TYPE,0).equals(object)
                    || !globals.equals(cut.expectedGlobals()))throw new IllegalArgumentException("Door inactive secondary exceeded its exact no-primary-write return");
        } else {
            if(!primary)throw new IllegalStateException("Actual cleared secondary cannot reach a native stop");
            var prefix=((CdSolidObject.NativeStopped)call.outcome()).prefix();
            if(prefix.entry()!=CdSolidObject.Entry.TOP_SOLID_OBJECT)throw new IllegalArgumentException("Door stopped at another entry");
            object=prefix.object();others=prefix.otherSlots();writes=prefix.objectWrites();requests=prefix.fmRequests();helper=prefix.player();globals=prefix.globals();stopped=true;
            readyFrame=frame.prepareObjectSolidPrefix(cut.expectedPrimary(),cut.expectedGlobals(),prefix);
        }
        var source=i.pool.state(i.whole);var before=source.copyBytes();var after=object.copyBytes();
        if(!object.identity().equals(source.identity()) || !Set.of(CdObjectState.ByteField.FLAGS,CdObjectState.ByteField.SOLID_TYPE).containsAll(writes))
            throw new IllegalArgumentException("Door Top changed identity or declared unowned object stores");
        for(int at=0;at<64;at++) {
            int mask=at==0x22&&writes.contains(CdObjectState.ByteField.FLAGS)?0x28:at==0x25&&writes.contains(CdObjectState.ByteField.SOLID_TYPE)?255:0;
            if(((before[at]^after[at])&~mask&255)!=0)throw new IllegalArgumentException("Door Top changed unowned full64 byte "+at);
        }
        var images=new ArrayList<CdObjectState>();images.add(object);var seen=new HashSet<Integer>();seen.add(i.whole);
        for(var other:others) {
            int whole=other.identity().wholeIndex();
            if(whole<32 || whole>=128 || !seen.add(whole) || whole!=cut.expectedPrimary().standObject()
                    || !cut.callCut().slots().get(whole-32).moveByte(CdObjectState.ByteField.FLAGS,
                    cut.callCut().slots().get(whole-32).flags()&~8).equals(other))throw new IllegalArgumentException("Door Top altered an unowned other image");
            images.add(other);
        }
        var readyPool=CdObjectPool.overlay(cut.callCut(),images);i.pool.validateRestore(readyPool);var readyImages=logMosquiImages(readyPool);
        var readyState=primary?readyFrame.finalState():i.state;
        var readyActor=primary?new CdPlayerFrameRuntime.ActorState(i.actor.owner(),readyState,i.actor.display()):i.actor;
        var readyShared=primary?new CdPlayerFrameRuntime.SharedState(i.shared.owner(),readyFrame.finalEnvironment(),i.shared.artState(),i.shared.paused(),i.shared.vIntClock()):i.shared;
        var ledger=new ArrayList<>(sounds);for(var fm:requests)ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
        var readyLedger=List.copyOf(ledger);var readyValues=preparedMonitorValues(primary?TUNNEL_DOOR_PRIMARY_TOP:TUNNEL_DOOR_SECONDARY_TOP);
        var readyLive=new CdTunnelDoor.LiveSolid(primary?helper:cut.expectedPrimary(),cut.expectedGates(),globals);
        doorPort=true;
        try {
            verifyDoor(i);if(primary)frame.validatePreparedObjectSolidPublication(readyFrame);
            i.pool.restore(readyPool);if(primary)frame.applyPreparedObjectSolidPublication(readyFrame);
            sounds=readyLedger;values=readyValues;i.ledger=readyLedger;i.expectedValues=readyValues;i.images=readyImages;
            i.state=readyState;i.actor=readyActor;i.shared=readyShared;i.stopped=stopped;
            if(primary)i.primaryDone=true;else i.secondaryDone=true;i.pendingCut=null;i.pendingOutcome=null;
            verifyDoor(i);i.completedReturn=readyLive;i.completedCall=call;return readyLive;
        } finally {doorPort=false;}
    }
    private void playDoorFm(CdTunnelDoor.FmSound sound) {
        var i=activeDoor;if(i==null || !(i.pendingCall instanceof CdTunnelDoor.FmCall call) || call.sound()!=sound)
            throw new IllegalStateException("No actual Door FM producer");requireDoorCall(call);
        var ledger=new ArrayList<>(sounds);ledger.add(new SonicCdGameModule.PastTunnelDoorFm(sound));var readyLedger=List.copyOf(ledger);
        var readyValues=preparedMonitorValues(TUNNEL_DOOR_FM);doorPort=true;
        try {verifyDoor(i);sounds=readyLedger;values=readyValues;i.ledger=readyLedger;i.expectedValues=readyValues;i.fm=true;verifyDoor(i);
            i.completedReturn=null;i.completedCall=call;}
        finally {doorPort=false;}
    }
    private void drawDoor(CdObjectPool pool,CdObjectState.Identity identity) {
        var i=activeDoor;if(i==null || !(i.pendingCall instanceof CdTunnelDoor.DrawCall call))throw new IllegalStateException("No actual Door Draw producer");
        requireDoorCall(call);
        if(pool!=i.pool || identity!=call.source().identity() || i.drawn)throw new IllegalStateException("Foreign/repeated Door Draw pointer");
        var source=pool.state(i.whole);i.drawn=true;drawingDoor=true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(i.whole),source.spriteFlags(),source.centreX(),source.centreY(),
                    source.unsignedByte(CdObjectState.ByteField.Y_RADIUS),source.unsignedByte(CdObjectState.ByteField.WIDTH),source.unsignedByte(CdObjectState.ByteField.PRIORITY)),i.camera);
            verifyDoor(i);if(!i.cleared)throw new IllegalStateException("Door Draw omitted actual BCLR7");i.completedReturn=null;i.completedCall=call;
        } catch(RuntimeException|Error original) {i.queueFailed=drawQueue.phase()==CdObjectDrawQueue.Phase.FAILED;throw original;}
        finally {drawingDoor=false;}
    }
    private CdObjectDrawQueue.LiveDrawSlot readDoorDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var i=requireDoor();if(!drawingDoor || doorPort || queuePort || ref.wholeIndex()!=i.whole)throw new IllegalStateException("No actual Door collecting read");
        queuePort=true;
        try {var s=i.pool.state(i.whole);var result=new CdObjectDrawQueue.LiveDrawSlot(s.id(),s.spriteFlags(),s.centreX(),s.centreY(),
                s.word(CdObjectState.WordField.X_FRACTION),s.longBits(CdObjectState.LongField.MAPPINGS),s.unsignedByte(CdObjectState.ByteField.MAP_FRAME),s.word(CdObjectState.WordField.TILE));
            verifyDoor(i);return result;}
        finally {queuePort=false;}
    }
    private void writeDoorOnScreen(CdObjectDrawQueue.CdSlotRef ref,boolean value) {
        var i=requireDoor();if(!drawingDoor || doorPort || queuePort || value || i.cleared || ref.wholeIndex()!=i.whole)
            throw new IllegalStateException("Only actual Door Draw clears its own bit7 once");
        var source=i.pool.state(i.whole);var next=source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,source.spriteFlags()&0x7F);
        var readyPool=CdObjectPool.overlay(placement.capture().pool(),List.of(next));i.pool.validateRestore(readyPool);var images=logMosquiImages(readyPool);
        queuePort=true;
        try {verifyDoor(i);i.pool.write(next);i.images=images;i.cleared=true;verifyDoor(i);}
        finally {queuePort=false;}
    }

    private void runFlower(int whole) {
        requireFlowerIdle();requireOtherFlowerScopesIdle();module.requireAct2LoadingVisit(this);
        if(!runningObjects || servicing || queuePort || whole<32 || whole>=128 || values.phase()!=BEGIN_LEVEL
                || drawQueue==null || drawQueue.phase()!=CdObjectDrawQueue.Phase.COLLECTING || placement.pool().state(whole).id()!=CdFlower.OBJECT_ID)
            throw new IllegalStateException("No actual cached Flower physical dispatch");
        var i=new FlowerInvocation(whole);activeFlower=i;
        try {
            var result=flower.run(whole);verifyFlower(i);
            if(i.pendingPublication!=null || i.pendingCall!=null || i.pendingFloor!=null || i.completedOperation!=null
                    || !result.state().equals(i.pool.state(whole)) || i.step!=FlowerStep.ANIMATED || !i.drawn || !i.cleared)
                throw new IllegalStateException("Flower omitted an actual completed native receipt");
        } catch(RuntimeException|Error original) {
            try {verifyFlowerFailure(i);}catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}
            throw original;
        } finally {activeFlower=null;}
    }
    private void verifyFlowerOwners(FlowerInvocation i) {
        module.requireAct2LoadingVisit(this);requireOtherFlowerScopesIdle();var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if(activeFlower!=i || !runningObjects || servicing || flower!=i.body || flowerAssets!=i.bodyAssets || flower.assets()!=i.bodyAssets
                || flowerAssets.flower()!=assets.objects().flower() || i.sourceFrame!=frame || i.stageSource!=frame.stageSource()
                || i.sourceAssets!=assets || i.pool!=placement.pool() || i.queue!=drawQueue || drawQueue==null
                || !(drawQueue.phase()==CdObjectDrawQueue.Phase.COLLECTING || i.queueFailed && drawQueue.phase()==CdObjectDrawQueue.Phase.FAILED)
                || values.phase()!=BEGIN_LEVEL || mode.getWorldSession()!=i.world || mode.getLevelManager().getCompletedProductionLoadGeneration()!=i.generation
                || frame.state()!=i.state || frame.environment()!=i.environment || !frame.captureActor().equals(i.actor) || !frame.captureShared().equals(i.shared)
                || values!=i.expectedValues || sounds!=i.ledger || !sameBytes(values.hudObjects(),i.reserved) || !presentation.sameAct2Scroll(i.scroll))
            throw new IllegalStateException("Flower changed or borrowed another actual World/Frame/source owner");
    }
    private boolean flowerImagesEqual(FlowerInvocation i,List<CdAssetBytes> expected) {
        var cut=placement.capture();i.pool.validateRestore(cut.pool());
        if(expected==null || expected.size()!=96 || !cut.status().equals(i.status) || !sameBytes(cut.savedFlags(),i.saved))return false;
        for(int at=0;at<96;at++)if(!Arrays.equals(cut.pool().slots().get(at).copyBytes(),expected.get(at).copy()))return false;
        return true;
    }
    private void verifyFlower(FlowerInvocation i) {
        verifyFlowerOwners(i);if(!flowerImagesEqual(i,i.images))throw new IllegalStateException("Flower changed unowned full96/source/saved/status bytes");
    }
    private void verifyFlowerFailure(FlowerInvocation i) {
        verifyFlowerOwners(i);
        if(!flowerImagesEqual(i,i.images) && !(i.pendingPublication!=null && flowerImagesEqual(i,i.readyImages)))
            throw new IllegalStateException("Throwing Flower differs from both exact native publication cuts");
    }
    private FlowerInvocation requireFlower() {
        var i=activeFlower;if(i==null)throw new IllegalStateException("No actual Flower invocation");verifyFlower(i);return i;
    }
    private void validateFlowerCut(FlowerInvocation i,CdObjectPlacementRuntime.Snapshot cut,CdObjectState source) {
        placement.validateRestore(cut);
        if(!source.equals(i.pool.state(i.whole)) || !source.identity().equals(i.entry.identity()) || !cut.status().equals(i.status) || !sameBytes(cut.savedFlags(),i.saved))
            throw new IllegalStateException("Foreign Flower source cut");
        for(int at=0;at<96;at++)if(!Arrays.equals(cut.pool().slots().get(at).copyBytes(),i.images.get(at).copy()))
            throw new IllegalStateException("Flower full96 call cut differs");
    }
    private CdObjectState flowerInitHeader(CdObjectState source) {
        return source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,source.spriteFlags()|4).moveByte(CdObjectState.ByteField.PRIORITY,1)
                .moveByte(CdObjectState.ByteField.Y_RADIUS,0).moveWord(CdObjectState.WordField.TILE,source.unsignedByte(CdObjectState.ByteField.SUBTYPE2)==0?0xA6D7:0x26D7)
                .moveLong(CdObjectState.LongField.MAPPINGS,flowerAssets.mappingsAddress());
    }
    private boolean flowerAnimationSite(FlowerInvocation i) {
        return i.entry.routine()==4 && i.step==FlowerStep.ENTRY || i.step==FlowerStep.MOVE && !(i.floor.distance()<0 && i.entry.subtype()==0)
                || i.step==FlowerStep.GROW || i.step==FlowerStep.DONE;
    }
    private void beforeFlowerPublication(CdFlower.Publication c) {
        var i=requireFlower();
        if(!flower.isCurrentCall(c) || i.pendingPublication!=null || i.pendingCall!=null || i.pendingFloor!=null || i.completedOperation!=null
                || flowerPort || drawingFlower || queuePort || c.ordinal()!=i.ordinal+1)
            throw new IllegalStateException("Flower publication lacks its exact pending producer/ordinal");
        validateFlowerCut(i,c.cut(),c.source());var source=c.source();var out=c.output();CdObjectState expected;FlowerStep next;
        switch(c.kind()) {
            case INIT_BLOCKED -> {
                if(i.entry.routine()!=0 || i.step!=FlowerStep.ENTRY || source.subtype()!=0)throw new IllegalStateException("Unreached persistent Flower Init");
                expected=flowerInitHeader(source);next=FlowerStep.BLOCKED;
            }
            case SEED_READY -> {
                if(i.entry.routine()!=0 || i.step!=FlowerStep.ENTRY || source.subtype()==0)throw new IllegalStateException("Unreached respawn-free Flower Init");
                expected=flowerInitHeader(source).moveWord(CdObjectState.WordField.ANIMATION_WORD,2).moveByte(CdObjectState.ByteField.ROUTINE,2).moveWord(CdObjectState.WordField.TILE,0x6D7);next=FlowerStep.INIT;
            }
            case SEED_MOVE -> {
                if(!i.floorCompleted || i.floor==null || !(i.entry.routine()==0 && i.step==FlowerStep.INIT || i.entry.routine()==2 && i.step==FlowerStep.ENTRY))
                    throw new IllegalStateException("Flower seed moved without its actual completed floor return");
                expected=source.addWord(CdObjectState.WordField.Y,i.floor.distance()<0?i.floor.distance():2);
                if(i.floor.distance()<0 && source.subtype()!=0)expected=expected.moveByte(CdObjectState.ByteField.ROUTINE,4)
                        .moveByte(CdObjectState.ByteField.ANIMATION,1).moveByte(CdObjectState.ByteField.Y_RADIUS,0x30);
                next=FlowerStep.MOVE;
            }
            case GROW_READY -> {
                if(i.entry.routine()!=6 || i.step!=FlowerStep.ENTRY)throw new IllegalStateException("Unreached Flower growing prefix");
                expected=source.moveWord(CdObjectState.WordField.TILE,0x26D7).moveByte(CdObjectState.ByteField.ANIMATION,2);next=FlowerStep.GROW;
            }
            case DONE_READY -> {
                if(i.entry.routine()!=8 || i.step!=FlowerStep.ENTRY)throw new IllegalStateException("Unreached Flower done prefix");
                expected=source.moveByte(CdObjectState.ByteField.ANIMATION,3).moveByte(CdObjectState.ByteField.ROUTINE,4);next=FlowerStep.DONE;
            }
            case ANIMATION_RESULT -> {
                if(!flowerAnimationSite(i) || out==null)throw new IllegalStateException("Flower animation skipped its source prefix");
                expected=source.moveByte(CdObjectState.ByteField.PREVIOUS_ANIMATION,source.unsignedByte(CdObjectState.ByteField.ANIMATION))
                        .moveByte(CdObjectState.ByteField.ANIMATION_FRAME,out.unsignedByte(CdObjectState.ByteField.ANIMATION_FRAME))
                        .moveByte(CdObjectState.ByteField.ANIMATION_TIME,out.unsignedByte(CdObjectState.ByteField.ANIMATION_TIME))
                        .moveByte(CdObjectState.ByteField.MAP_FRAME,out.unsignedByte(CdObjectState.ByteField.MAP_FRAME))
                        .moveByte(CdObjectState.ByteField.SPRITE_FLAGS,out.spriteFlags()).moveByte(CdObjectState.ByteField.ROUTINE,out.routine());
                if((out.spriteFlags()&~3)!=(source.spriteFlags()&~3) || out.routine()!=source.routine() && out.routine()!=((source.routine()+2)&255))
                    throw new IllegalStateException("Flower animator changed unowned flags/routine");
                next=FlowerStep.ANIMATED;
            }
            case FAILURE_RESET -> {
                if(!flowerAnimationSite(i) || source.unsignedByte(CdObjectState.ByteField.ANIMATION)==source.unsignedByte(CdObjectState.ByteField.PREVIOUS_ANIMATION))
                    throw new IllegalStateException("Unreached Flower animation reset failure");
                expected=source.moveByte(CdObjectState.ByteField.PREVIOUS_ANIMATION,source.unsignedByte(CdObjectState.ByteField.ANIMATION))
                        .moveByte(CdObjectState.ByteField.ANIMATION_FRAME,0).moveByte(CdObjectState.ByteField.ANIMATION_TIME,0);next=FlowerStep.RESET;
            }
            case FAILURE_TIMER -> {
                if(!flowerAnimationSite(i) && i.step!=FlowerStep.RESET)throw new IllegalStateException("Unreached Flower timer failure");
                expected=source.addByte(CdObjectState.ByteField.ANIMATION_TIME,-1);next=FlowerStep.TIMER;
            }
            case FAILURE_SPEED -> {
                if(i.step!=FlowerStep.TIMER)throw new IllegalStateException("Flower speed read before its timer");
                int animation=source.unsignedByte(CdObjectState.ByteField.ANIMATION);if(animation>=4)throw new IllegalStateException("Unrepresented Flower directory");
                var table=flowerAssets.flower().animationTable();expected=source.moveByte(CdObjectState.ByteField.ANIMATION_TIME,table.unsignedByte((short)table.unsignedWord(2*animation)));next=FlowerStep.SPEED;
            }
            default -> throw new IllegalArgumentException("Unknown Flower publication");
        }
        if(!Objects.equals(expected,out))throw new IllegalStateException("Flower publication changed another native full64 byte");
        i.pool.validateRestore(CdObjectPool.overlay(c.cut().pool(),List.of(out)));var images=new ArrayList<>(i.images);images.set(i.whole-32,CdAssetBytes.copyOf(out.copyBytes()));
        i.readyImages=List.copyOf(images);i.readyStep=next;i.pendingPublication=c;
    }
    private void afterFlowerPublication(CdFlower.Publication c) {
        var i=activeFlower;if(i==null || !flower.isCurrentCall(c) || i.pendingPublication!=c || i.completedOperation!=null)
            throw new IllegalStateException("No exact Flower store completion");
        verifyFlowerOwners(i);if(!flowerImagesEqual(i,i.readyImages))throw new IllegalStateException("Flower store differs from prebuilt full96 output");
        i.images=i.readyImages;i.step=i.readyStep;i.readyImages=null;i.pendingPublication=null;i.ordinal++;i.completedOperation=c;
    }
    private void beforeFlowerCall(Object call) {
        var i=requireFlower();if(!flower.isCurrentCall(call) || i.pendingPublication!=null || i.pendingCall!=null || i.completedOperation!=null || flowerPort || drawingFlower || queuePort)
            throw new IllegalStateException("No exact pending Flower source call");
        long ordinal;CdObjectPlacementRuntime.Snapshot cut;CdObjectState source;
        if(call instanceof CdFlower.FloorCall c) {
            ordinal=c.ordinal();cut=c.cut();source=c.source();
            if(i.pendingFloor!=null || i.floorCompleted || !(i.entry.routine()==0 && i.step==FlowerStep.INIT || i.entry.routine()==2 && i.step==FlowerStep.ENTRY))
                throw new IllegalStateException("Unreached/repeated Flower floor probe");
        } else if(call instanceof CdFlower.PrimaryAngleCall c) {
            ordinal=c.ordinal();cut=c.cut();source=c.source();
            if(i.pendingFloor==null || !flower.isCurrentCall(i.pendingFloor) || i.angles==0 && c.value()!=0 || (c.value()&~255)!=0)
                throw new IllegalStateException("Flower angle outside its actual floor/prezero");
        } else if(call instanceof CdFlower.DrawCall c) {
            ordinal=c.ordinal();cut=c.cut();source=c.source();
            if(i.pendingFloor!=null || i.step!=FlowerStep.ANIMATED || i.drawn)throw new IllegalStateException("Flower Draw skipped native animation");
        } else throw new IllegalArgumentException("Wrong Flower call type");
        if(ordinal!=i.ordinal+1)throw new IllegalStateException("Flower call ordinal differs");validateFlowerCut(i,cut,source);
        if(call instanceof CdFlower.FloorCall)i.pendingFloor=call;else i.pendingCall=call;i.ordinal++;
    }
    private void afterFlowerCall(Object call) {
        var i=requireFlower();if(!flower.isCurrentCall(call) || i.completedOperation!=null)throw new IllegalStateException("No exact Flower call completion");
        if(call instanceof CdFlower.FloorCall c) {
            if(i.pendingFloor!=call || i.pendingCall!=null || i.angles==0 || i.floorCompleted)throw new IllegalStateException("Flower floor omitted its native angle receipts");
            i.floor=flower.currentFloorResult(c);
            if(i.environment.movement().globals().primaryAngle()!=i.floor.primaryAngle())throw new IllegalStateException("Flower floor return differs from its completed real angle stores");
            i.floorCompleted=true;i.pendingFloor=null;
        } else {
            if(i.pendingCall!=call || call instanceof CdFlower.PrimaryAngleCall && i.angleReceipt!=call
                    || call instanceof CdFlower.DrawCall && (i.drawReceipt!=call || !i.drawn || !i.cleared))
                throw new IllegalStateException("Flower port returned without its private once-only receipt");
            i.pendingCall=null;
        }
        i.completedOperation=call;
    }
    private void requirePreparedFlowerReceipt(Object call) {
        var i=requireFlower();if(!flower.isCurrentCall(call) || i.completedOperation!=null
                || !(i.pendingPublication==call && i.readyImages!=null || i.pendingCall==call || i.pendingFloor==call))
            throw new IllegalStateException("Flower observer returned without its prepared receipt");
    }
    private void consumeCompletedFlowerReceipt(Object call) {
        var i=requireFlower();if(!flower.isCurrentCall(call) || i.completedOperation!=call || i.pendingPublication==call || i.pendingCall==call || i.pendingFloor==call)
            throw new IllegalStateException("Flower observer returned without its completed receipt");
        i.completedOperation=null;
    }
    private void commitFlowerPrimaryAngle(CdFlower.PrimaryAngleCall c) {
        var i=requireFlower();if(!flower.isCurrentCall(c) || i.pendingCall!=c || i.pendingFloor==null || i.angleReceipt==c || flowerPort || drawingFlower || queuePort)
            throw new IllegalStateException("No actual once-only Flower angle publication");
        validateFlowerCut(i,c.cut(),c.source());var readyEnvironment=monitorAngleEnvironment(i.environment,c.value());
        var readyShared=new CdPlayerFrameRuntime.SharedState(i.shared.owner(),readyEnvironment,i.shared.artState(),i.shared.paused(),i.shared.vIntClock());
        var readyValues=preparedMonitorValues(FLOWER_PRIMARY_ANGLE);flowerPort=true;
        try {
            verifyFlower(i);frame.applyObjectPrimaryAngle(c.value());i.environment=frame.environment();i.shared=readyShared;values=readyValues;i.expectedValues=readyValues;
            i.angleReceipt=c;i.angles++;verifyFlower(i);
        } finally {flowerPort=false;}
    }
    private void drawFlower(CdFlower.DrawCall c) {
        var i=requireFlower();if(!flower.isCurrentCall(c) || i.pendingCall!=c || i.drawReceipt!=null || i.drawn || flowerPort || drawingFlower || queuePort)
            throw new IllegalStateException("No actual once-only Flower Draw");
        var source=c.source();i.drawn=true;drawingFlower=true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(i.whole),source.spriteFlags(),source.centreX(),source.centreY(),
                    source.unsignedByte(CdObjectState.ByteField.Y_RADIUS),source.unsignedByte(CdObjectState.ByteField.WIDTH),source.unsignedByte(CdObjectState.ByteField.PRIORITY)),i.camera);
            verifyFlower(i);if(!i.cleared)throw new IllegalStateException("Flower Draw omitted native BCLR7");i.drawReceipt=c;
        } catch(RuntimeException|Error original){i.queueFailed=drawQueue.phase()==CdObjectDrawQueue.Phase.FAILED;throw original;}
        finally {drawingFlower=false;}
    }
    private CdObjectDrawQueue.LiveDrawSlot readFlowerDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var i=requireFlower();if(!drawingFlower || flowerPort || queuePort || !(i.pendingCall instanceof CdFlower.DrawCall) || ref.wholeIndex()!=i.whole)
            throw new IllegalStateException("No actual Flower queue read");
        queuePort=true;
        try {var s=i.pool.state(i.whole);var out=new CdObjectDrawQueue.LiveDrawSlot(s.id(),s.spriteFlags(),s.centreX(),s.centreY(),s.word(CdObjectState.WordField.X_FRACTION),
                s.longBits(CdObjectState.LongField.MAPPINGS),s.unsignedByte(CdObjectState.ByteField.MAP_FRAME),s.word(CdObjectState.WordField.TILE));verifyFlower(i);return out;}
        finally {queuePort=false;}
    }
    private void writeFlowerOnScreen(CdObjectDrawQueue.CdSlotRef ref,boolean value) {
        var i=requireFlower();if(!drawingFlower || flowerPort || queuePort || !(i.pendingCall instanceof CdFlower.DrawCall) || ref.wholeIndex()!=i.whole || value || i.cleared)
            throw new IllegalStateException("Only actual Flower Draw clears its bit7 once");
        var source=i.pool.state(i.whole);var next=source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,source.spriteFlags()&0x7F);
        var pool=CdObjectPool.overlay(placement.capture().pool(),List.of(next));i.pool.validateRestore(pool);var images=logMosquiImages(pool);queuePort=true;
        try {verifyFlower(i);i.pool.write(next);i.images=images;i.cleared=true;verifyFlower(i);}finally {queuePort=false;}
    }
    // ID07: the existing path kernel owns every native branch; these are actual private publication receipts.
    private void requireTunnelPathIdle() {
        requireTimeStarsIdle();
        if(activeTunnelPath!=null || tunnelPathPort) throw new IllegalStateException("Actual Tunnel Path owns this cut");
    }
    private void requireOtherTunnelPathScopesIdle() {
        if(activePrimary!=null || activeDrain!=null || activeReserved!=null || activeRing!=null || activeSpring!=null
                || activeTitle!=null || activeMonitor!=null || activeBoulder!=null || activePlatform!=null
                || activeAnton!=null || activePata!=null || activeTamabboh!=null || activeFloat!=null || activeCheckpoint!=null
                || activeWall!=null || activeCollapse!=null || activeLogMosqui!=null || activeChildEffect!=null || activeDoor!=null || activeFlower!=null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || titlePort || drawingTitle || monitorPort || drawingMonitor || boulderPort || drawingBoulder
                || platformPort || drawingPlatform || antonPort || drawingAnton || drawingPata || tamabbohPort || drawingTamabboh
                || floatPort || drawingFloat || checkpointPort || drawingCheckpoint || wallPort || drawingWall
                || collapsePort || drawingCollapse || logMosquiPort || drawingLogMosqui || drawingChildEffect || doorPort || drawingDoor
                || flowerPort || drawingFlower || queuePort)
            throw new IllegalStateException("Another actual family owns the Tunnel Path cut");
    }
    private final class TunnelPathInvocation {
        final int whole; final CdTunnelPath body=tunnelPath; final CdTunnelPath.Assets bodyAssets=tunnelPathAssets;
        final CdPlayerFrameRuntime sourceFrame=frame; final Object stageSource=frame.stageSource();
        final CdR12aTerrainAssets sourceAssets=assets; final CdObjectPool pool=placement.pool(); final CdObjectDrawQueue queue=drawQueue;
        final CdObjectPlacementRuntime.Status status=placement.status(); final CdObjectState entry;
        final CdAssetBytes reserved=values.hudObjects(); final SonicCdPlayerPresentation.ScrollCapture scroll=presentation.captureAct2Scroll();
        final Object world; final long generation; final CdObjectDrawQueue.CameraWords camera;
        Values expectedValues=values,readyValues; List<SonicCdGameModule.PastSound> ledger=sounds,readyLedger;
        CdPlayerBody.State state=frame.state(); CdPlayerBody.Environment environment=frame.environment();
        CdPlayerFrameRuntime.ActorState actor=frame.captureActor(),readyActor;
        CdPlayerFrameRuntime.SharedState shared=frame.captureShared(),readyShared;
        CdAssetBytes saved=placement.capture().savedFlags(),readySaved;
        List<CdAssetBytes> images=logMosquiImages(placement.capture().pool()),readyImages;
        CdPlayerFrameRuntime.ObjectSolidPublication readyPlayer;
        CdObjectPool.Snapshot readyPool;
        CdTunnelPath.PlayerCut readValue;
        Object pendingOperation,completedOperation,portReceipt; long ordinal; boolean failed;
        TunnelPathInvocation(int whole) {
            this.whole=whole;entry=pool.state(whole);var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world=mode.getWorldSession();generation=mode.getLevelManager().getCompletedProductionLoadGeneration();
            var fg=presentation.act2ForegroundImage();camera=new CdObjectDrawQueue.CameraWords(fg.cameraX(),fg.cameraYWord());
        }
    }
    private void runTunnelPath(int whole) {
        requireTunnelPathIdle();requireOtherTunnelPathScopesIdle();module.requireAct2LoadingVisit(this);
        if(!runningObjects || servicing || whole<32 || whole>=128 || values.phase()!=BEGIN_LEVEL
                || drawQueue==null || drawQueue.phase()!=CdObjectDrawQueue.Phase.COLLECTING
                || placement.pool().state(whole).id()!=CdTunnelPath.OBJECT_ID)
            throw new IllegalStateException("No actual cached Tunnel Path physical dispatch");
        var i=new TunnelPathInvocation(whole);activeTunnelPath=i;
        try {
            var gates=frame.environment().movement().gates();
            var result=tunnelPath.run(whole,new CdTunnelPath.Globals(gates.timeZone(),gates.timeWarpDirection(),i.camera.xWord()));
            verifyTunnelPath(i);
            if(i.pendingOperation!=null || i.completedOperation!=null || i.portReceipt!=null || i.failed || i.ordinal==0
                    || !result.state().equals(i.pool.state(whole)))
                throw new IllegalStateException("Tunnel Path omitted its original native receipts");
        } catch(RuntimeException|Error original) {
            try {verifyTunnelPath(i);}catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}
            throw original;
        } finally {activeTunnelPath=null;}
    }
    private void verifyTunnelPathOwners(TunnelPathInvocation i) {
        module.requireAct2LoadingVisit(this);requireOtherTunnelPathScopesIdle();
        var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if(activeTunnelPath!=i || !runningObjects || servicing || i.body!=tunnelPath || i.bodyAssets!=tunnelPathAssets
                || tunnelPath.assets()!=i.bodyAssets || i.sourceFrame!=frame || i.stageSource!=frame.stageSource() || i.sourceAssets!=assets
                || i.pool!=placement.pool() || i.queue!=drawQueue || drawQueue==null || drawQueue.phase()!=CdObjectDrawQueue.Phase.COLLECTING
                || values.phase()!=BEGIN_LEVEL || i.world!=mode.getWorldSession() || i.generation!=mode.getLevelManager().getCompletedProductionLoadGeneration()
                || frame.state()!=i.state || frame.environment()!=i.environment || !frame.captureActor().equals(i.actor) || !frame.captureShared().equals(i.shared)
                || values!=i.expectedValues || sounds!=i.ledger || !placement.status().equals(i.status)
                || !sameBytes(values.hudObjects(),i.reserved) || !presentation.sameAct2Scroll(i.scroll))
            throw new IllegalStateException("Tunnel Path changed actual Frame/World/source/Values/ledger/scroll/queue owners");
    }
    private void verifyTunnelPathImages(TunnelPathInvocation i,List<CdAssetBytes> images,CdAssetBytes saved) {
        var actual=placement.capture();placement.validateRestore(actual);
        if(images==null || images.size()!=96 || !actual.status().equals(i.status) || !sameBytes(actual.savedFlags(),saved))
            throw new IllegalStateException("Tunnel Path changed actual status or saved bank");
        for(int at=0;at<96;at++) if(!Arrays.equals(actual.pool().slots().get(at).copyBytes(),images.get(at).copy()))
            throw new IllegalStateException("Tunnel Path changed full96/source image at whole "+(at+32));
    }
    private void verifyTunnelPath(TunnelPathInvocation i) {verifyTunnelPathOwners(i);verifyTunnelPathImages(i,i.images,i.saved);}
    private TunnelPathInvocation requireTunnelPath() {
        var i=activeTunnelPath;if(i==null)throw new IllegalStateException("No actual Tunnel Path invocation");verifyTunnelPath(i);return i;
    }
    private void validateTunnelPathCut(TunnelPathInvocation i,CdObjectPool.Snapshot cut,CdObjectState source) {
        i.pool.validateRestore(cut);
        if(!source.equals(i.pool.state(i.whole)) || !source.identity().equals(i.entry.identity()) || !cut.slots().equals(i.pool.capture().slots()))
            throw new IllegalStateException("Tunnel Path stale full96 or source64 cut");
    }
    private void beforeTunnelPathOperation(Object operation) {
        var i=requireTunnelPath();
        if(!tunnelPath.isCurrentCall(operation) || i.pendingOperation!=null || i.completedOperation!=null || i.portReceipt!=null
                || tunnelPathPort || tunnelPath.currentOrdinal(operation)!=i.ordinal+1
                || i.failed && !(operation instanceof CdTunnelPath.PlayerReadCall))
            throw new IllegalStateException("Tunnel Path operation lacks its exact pending producer/sequence");
        i.readyImages=i.images;i.readySaved=i.saved;i.readyValues=i.expectedValues;i.readyLedger=i.ledger;
        i.readyActor=i.actor;i.readyShared=i.shared;i.readyPool=null;i.readyPlayer=null;i.readValue=null;
        if(operation instanceof CdTunnelPath.Publication c) {
            placement.validateRestore(c.cut());validateTunnelPathCut(i,c.cut().pool(),c.source());
            if(!c.cut().status().equals(i.status) || !sameBytes(c.cut().savedFlags(),i.saved))throw new IllegalStateException("Tunnel Path stale placement cut");
            var images=new ArrayList<>(i.images);
            switch(c.kind()) {
                case SOURCE -> {
                    if(c.site()==null || c.output().identity()!=c.source().identity())throw new IllegalArgumentException("Tunnel Path own store changed identity/site");
                    i.readyPool=CdObjectPool.overlay(c.cut().pool(),List.of(c.output()));i.pool.validateRestore(i.readyPool);
                    images.set(i.whole-32,CdAssetBytes.copyOf(c.output().copyBytes()));
                    if(!sameBytes(c.savedFlags(),i.saved) || !c.deleteStores().isEmpty())throw new IllegalArgumentException("Tunnel own store changed another owner");
                }
                case SAVED_BYTE -> {
                    if(c.bankSite()==null || c.savedByteIndex()<2 || c.savedByteIndex()>=CdObjectPlacementRuntime.SAVED_BANK_BYTES
                            || !c.output().equals(c.source()) || !c.deleteStores().isEmpty())throw new IllegalArgumentException("Tunnel saved-byte operation shape");
                    byte[] bank=i.saved.copy();bank[c.savedByteIndex()]&=0x7F;
                    if(!Arrays.equals(bank,c.savedFlags().copy()))throw new IllegalArgumentException("Tunnel saved operation changes more than native BCLR7");
                    i.readySaved=c.savedFlags();
                }
                case DELETE -> {
                    if(c.deleteStores().size()!=16 || !sameBytes(c.savedFlags(),i.saved))throw new IllegalArgumentException("Tunnel DeleteObject long shape");
                    byte[] raw=c.source().copyBytes();
                    for(int at=0;at<16;at++) {
                        int bits=0;for(int b=0;b<4;b++)bits=(bits<<8)|Byte.toUnsignedInt(raw[4*at+b]);
                        var store=c.deleteStores().get(at);
                        if(store.field()!=CdTunnelPath.DeleteLong.values()[at] || store.expectedBits()!=bits)throw new IllegalArgumentException("Tunnel DeleteObject LONG order/value");
                    }
                    images.set(i.whole-32,zero(64));
                }
            }
            i.readyImages=List.copyOf(images);
        } else if(operation instanceof CdTunnelPath.PlayerReadCall c) {
            placement.validateRestore(c.cut());validateTunnelPathCut(i,c.cut().pool(),c.source());
            if(!c.cut().status().equals(i.status) || !sameBytes(c.cut().savedFlags(),i.saved))throw new IllegalStateException("Tunnel player read placement cut");
            i.readValue=new CdTunnelPath.PlayerCut(i.state,i.environment);
            if(!i.failed)i.readyValues=preparedMonitorValues(TUNNEL_PATH_PLAYER_READ);
        } else if(operation instanceof CdTunnelPath.WordReadCall c) {
            placement.validateRestore(c.cut());validateTunnelPathCut(i,c.cut().pool(),c.source());
            if(!c.cut().status().equals(i.status) || !sameBytes(c.cut().savedFlags(),i.saved))throw new IllegalStateException("Tunnel source read placement cut");
            i.readyValues=preparedMonitorValues(TUNNEL_PATH_SOURCE_READ);
        } else if(operation instanceof CdTunnelPath.PlayerCall c) {
            validateTunnelPathCut(i,c.actualCut(),c.expectedSource());
            if(c.expectedPlayer().state()!=i.state || c.expectedPlayer().environment()!=i.environment)throw new IllegalArgumentException("Tunnel player cut is not actual Frame pair");
            i.readyPlayer=frame.prepareObjectTunnelPathPlayerResult(i.state,i.environment,c.result());
            i.readyPool=CdObjectPool.overlay(c.actualCut(),List.of(c.finalSource()));i.pool.validateRestore(i.readyPool);
            var images=new ArrayList<>(i.images);images.set(i.whole-32,CdAssetBytes.copyOf(c.finalSource().copyBytes()));i.readyImages=List.copyOf(images);
            i.readyActor=new CdPlayerFrameRuntime.ActorState(i.actor.owner(),i.readyPlayer.finalState(),i.actor.display());
            i.readyShared=new CdPlayerFrameRuntime.SharedState(i.shared.owner(),i.readyPlayer.finalEnvironment(),i.shared.artState(),i.shared.paused(),i.shared.vIntClock());
            i.readyValues=preparedMonitorValues(TUNNEL_PATH_PLAYER);
        } else if(operation instanceof CdTunnelPath.FmCall c) {
            validateTunnelPathCut(i,c.actualCut(),c.expectedSource());
            if(c.expectedPlayer().state()!=i.state || c.expectedPlayer().environment()!=i.environment || c.sound()!=CdTunnelPath.FmSound.FM_91)
                throw new IllegalArgumentException("Tunnel FM is not the actual reached Frame/source");
            var ledger=new ArrayList<>(sounds);ledger.add(new SonicCdGameModule.PastTunnelPathFm(c.sound()));i.readyLedger=List.copyOf(ledger);
            i.readyValues=preparedMonitorValues(TUNNEL_PATH_FM);
        } else throw new IllegalArgumentException("Unknown Tunnel Path producer");
        i.pendingOperation=operation;i.ordinal++;
    }
    private void requirePreparedTunnelPathReceipt(Object operation) {
        var i=requireTunnelPath();
        if(!tunnelPath.isCurrentCall(operation) || i.pendingOperation!=operation || i.completedOperation!=null || i.readyImages==null)
            throw new IllegalStateException("Tunnel observer returned without its prepared receipt");
    }
    private void afterTunnelPathOperation(Object operation) {
        var i=activeTunnelPath;
        if(i==null || !tunnelPath.isCurrentCall(operation) || i.pendingOperation!=operation || i.completedOperation!=null)
            throw new IllegalStateException("No exact Tunnel Path completed producer");
        var result=tunnelPath.currentResult(operation);
        if(operation instanceof CdTunnelPath.Publication) {
            verifyTunnelPathOwners(i);verifyTunnelPathImages(i,i.readyImages,i.readySaved);i.images=i.readyImages;i.saved=i.readySaved;
        } else if(operation instanceof CdTunnelPath.WordReadCall) {
            if(!(result instanceof Integer word) || (word&~65535)!=0)throw new IllegalStateException("No completed Tunnel source WORD");
            verifyTunnelPath(i);values=i.readyValues;i.expectedValues=i.readyValues;
        } else {
            if(i.portReceipt!=operation || operation instanceof CdTunnelPath.PlayerReadCall && result!=i.readValue)
                throw new IllegalStateException("Tunnel port returned without its private adoption/read receipt");
            verifyTunnelPath(i);
        }
        i.pendingOperation=null;i.portReceipt=null;i.completedOperation=operation;
    }
    private void consumeCompletedTunnelPathReceipt(Object operation) {
        var i=requireTunnelPath();
        if(!tunnelPath.isCurrentCall(operation) || i.completedOperation!=operation || i.pendingOperation!=null || i.portReceipt!=null)
            throw new IllegalStateException("Tunnel observer returned without its completed receipt");
        i.completedOperation=null;i.readyImages=null;i.readySaved=null;i.readyValues=null;i.readyLedger=null;i.readyPlayer=null;i.readyPool=null;
        i.readyActor=null;i.readyShared=null;i.readValue=null;
    }
    private void failedTunnelPathOperation(Object operation,Throwable original) {
        var i=activeTunnelPath;
        if(i==null || !tunnelPath.isCurrentCall(operation))throw new IllegalStateException("No original failed Tunnel producer");
        verifyTunnelPathOwners(i);
        // A native own primitive can have completed before its callback failed. Preserve only its exact prebuilt image.
        if(i.pendingOperation==operation && operation instanceof CdTunnelPath.Publication) {
            var now=placement.capture();
            boolean ready=sameBytes(now.savedFlags(),i.readySaved);
            for(int at=0;ready && at<96;at++)ready=Arrays.equals(now.pool().slots().get(at).copyBytes(),i.readyImages.get(at).copy());
            if(ready){i.images=i.readyImages;i.saved=i.readySaved;}
        }
        verifyTunnelPath(i);i.ordinal=tunnelPath.currentOrdinal(operation);i.failed=true;i.pendingOperation=null;i.completedOperation=null;i.portReceipt=null;
        i.readyImages=null;i.readySaved=null;i.readyValues=null;i.readyLedger=null;i.readyPool=null;i.readyPlayer=null;i.readyActor=null;i.readyShared=null;i.readValue=null;
    }
    private CdTunnelPath.PlayerCut readTunnelPathPlayer() {
        var i=requireTunnelPath();var operation=i.pendingOperation;
        if(!(operation instanceof CdTunnelPath.PlayerReadCall) || !tunnelPath.isCurrentCall(operation) || i.portReceipt!=null || tunnelPathPort)
            throw new IllegalStateException("No once-only actual Tunnel player read");
        tunnelPathPort=true;
        try {verifyTunnelPath(i);values=i.readyValues;i.expectedValues=i.readyValues;i.portReceipt=operation;return i.readValue;}
        finally {tunnelPathPort=false;}
    }
    private void commitTunnelPathPlayer(CdTunnelPath.PlayerCall call) {
        var i=requireTunnelPath();
        if(i.pendingOperation!=call || !tunnelPath.isCurrentCall(call) || i.readyPlayer==null || i.portReceipt!=null || tunnelPathPort || i.failed)
            throw new IllegalStateException("No actual once-only Tunnel player publication");
        validateTunnelPathCut(i,call.actualCut(),call.expectedSource());frame.validatePreparedObjectSolidPublication(i.readyPlayer);i.pool.validateRestore(i.readyPool);
        tunnelPathPort=true;
        try {
            verifyTunnelPath(i);
            i.pool.write(call.finalSource());frame.applyPreparedObjectSolidPublication(i.readyPlayer);
            i.state=i.readyPlayer.finalState();i.environment=i.readyPlayer.finalEnvironment();i.actor=i.readyActor;i.shared=i.readyShared;
            i.images=i.readyImages;values=i.readyValues;i.expectedValues=i.readyValues;i.portReceipt=call;verifyTunnelPath(i);
        } finally {tunnelPathPort=false;}
    }
    private void playTunnelPathFm(CdTunnelPath.FmCall call) {
        var i=requireTunnelPath();
        if(i.pendingOperation!=call || !tunnelPath.isCurrentCall(call) || i.portReceipt!=null || tunnelPathPort || i.failed)
            throw new IllegalStateException("No actual once-only Tunnel FM91 publication");
        tunnelPathPort=true;
        try {verifyTunnelPath(i);sounds=i.readyLedger;i.ledger=i.readyLedger;values=i.readyValues;i.expectedValues=i.readyValues;i.portReceipt=call;verifyTunnelPath(i);}
        finally {tunnelPathPort=false;}
    }

    // Actual reserved ID03: one facade, one cached native kernel, no extra rewind/storage owner.
    private void requireTimeStarsIdle() {
        if(activeTimeStars!=null || timeStarsPort || drawingTimeStars)
            throw new IllegalStateException("Actual reserved TimeStars invocation owns this source cut");
    }
    private void requireOtherTimeStarsScopesIdle() {
        if(activePrimary!=null || activeDrain!=null || activeReserved!=null || activeRing!=null || activeSpring!=null
                || activeTitle!=null || activeMonitor!=null || activeBoulder!=null || activePlatform!=null
                || activeAnton!=null || activePata!=null || activeTamabboh!=null || activeFloat!=null || activeCheckpoint!=null
                || activeWall!=null || activeCollapse!=null || activeLogMosqui!=null || activeChildEffect!=null
                || activeDoor!=null || activeFlower!=null || activeTunnelPath!=null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || titlePort || drawingTitle || monitorPort || drawingMonitor || boulderPort || drawingBoulder
                || platformPort || drawingPlatform || antonPort || drawingAnton || drawingPata || tamabbohPort || drawingTamabboh
                || floatPort || drawingFloat || checkpointPort || drawingCheckpoint || wallPort || drawingWall
                || collapsePort || drawingCollapse || logMosquiPort || drawingLogMosqui || drawingChildEffect
                || doorPort || drawingDoor || flowerPort || drawingFlower || tunnelPathPort)
            throw new IllegalStateException("Another native family owns the reserved TimeStars cut");
    }
    private final class TimeStarsInvocation {
        final int whole; final CdTimeStars body=timeStars; final CdTimeStars.Assets bodyAssets=timeStarsAssets;
        final CdReservedObjectBank bank=timeStarBank; final CdPlayerFrameRuntime sourceFrame=frame;
        final CdR12aTerrainAssets sourceAssets=assets; final Object stage=frame.stageSource();
        final CdObjectPool pool=placement.pool(); final CdObjectDrawQueue queue=drawQueue;
        final CdObjectPlacementRuntime.Snapshot placed=placement.capture();
        final CdPlayerBody.State state=frame.state(); final CdPlayerBody.Environment environment=frame.environment();
        final CdPlayerFrameRuntime.ActorState actor=frame.captureActor();
        final CdPlayerFrameRuntime.SharedState shared=frame.captureShared();
        final SonicCdPlayerPresentation.ScrollCapture scroll=presentation.captureAct2Scroll();
        final List<SonicCdGameModule.PastSound> ledger=sounds;
        final Object world; final long generation; final CdObjectDrawQueue.CameraWords camera;
        Values expectedValues=values,readyValues; CdAssetBytes expectedReserved=values.hudObjects();
        Object pending,completed,preparedReceipt,adoptionReceipt,completedReceipt;
        long ordinal; boolean prepared,drawn,cleared,queueFailed;
        TimeStarsInvocation(int whole) {
            this.whole=whole; var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world=mode.getWorldSession();generation=mode.getLevelManager().getCompletedProductionLoadGeneration();
            var fg=presentation.act2ForegroundImage();camera=new CdObjectDrawQueue.CameraWords(fg.cameraX(),fg.cameraYWord());
        }
    }
    private void runTimeStar(int whole) {
        requireTimeStarsIdle();requireOtherTimeStarsScopesIdle();module.requireAct2LoadingVisit(this);
        if(!runningObjects || servicing || queuePort || whole<12 || whole>=16 || values.phase()!=BEGIN_LEVEL
                || drawQueue==null || drawQueue.phase()!=CdObjectDrawQueue.Phase.COLLECTING
                || reservedImage(whole).unsignedByte(0)!=CdTimeStars.OBJECT_ID)
            throw new IllegalStateException("No actual increasing reserved TimeStars physical dispatch");
        var i=new TimeStarsInvocation(whole);activeTimeStars=i;
        CdTimeStars.Result result;
        try {
            var player=frame.state();
            result=timeStars.run(whole,new CdTimeStars.Globals(frame.environment().movement().gates().zoneAct()>>>8,
                    values.globals().contact().timeWarp(),player.motion().flags(),player.history().bytes(),player.history().index()));
            verifyTimeStars(i);
            if(i.pending!=null || i.completed!=null || i.preparedReceipt!=null || i.adoptionReceipt!=null
                    || i.completedReceipt!=null || !result.state().equals(timeStarBank.state(whole)))
                throw new IllegalStateException("TimeStars omitted its original primitive/adoption/completion receipts");
            // Fresh Init has one native publication and never reaches history, animation, Powerup or Draw.
            if(result.effects().getFirst() instanceof CdTimeStars.RoutineEntered entered
                    && entered.routine()==CdTimeStars.NativeRoutine.INIT
                    && (i.ordinal!=1 || i.drawn || result.effects().size()!=1))
                throw new IllegalStateException("Fresh TimeStars Init reached an unowned synchronous call");
        } catch(RuntimeException|Error original) {
            try {verifyTimeStars(i);}catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}
            throw original;
        } finally {
            activeTimeStars=null;timeStarsPort=false;drawingTimeStars=false;
        }
        var entered=(CdTimeStars.RoutineEntered)result.effects().getFirst();
        call(entered.routine()==CdTimeStars.NativeRoutine.INIT?TIME_STARS_INIT:TIME_STARS_ACTIVE);
    }
    private void verifyTimeStars(TimeStarsInvocation i) {
        if(i==null)throw new IllegalStateException("No actual reserved TimeStars invocation");
        module.requireAct2LoadingVisit(this);requireOtherTimeStarsScopesIdle();
        var mode=com.openggf.game.session.SessionManager.getCurrentGameplayMode();var now=placement.capture();
        if(activeTimeStars!=i || !runningObjects || servicing || i.body!=timeStars || i.bodyAssets!=timeStarsAssets
                || timeStars.assets()!=i.bodyAssets || i.bank!=timeStarBank || i.sourceFrame!=frame || i.sourceAssets!=assets
                || i.stage!=frame.stageSource() || i.pool!=placement.pool() || i.queue!=drawQueue || drawQueue==null
                || !(drawQueue.phase()==CdObjectDrawQueue.Phase.COLLECTING
                    || i.queueFailed && drawQueue.phase()==CdObjectDrawQueue.Phase.FAILED)
                || values.phase()!=BEGIN_LEVEL || i.world!=mode.getWorldSession()
                || i.generation!=mode.getLevelManager().getCompletedProductionLoadGeneration()
                || frame.state()!=i.state || frame.environment()!=i.environment || frame.closed()
                || !frame.captureActor().equals(i.actor) || !frame.captureShared().equals(i.shared)
                || values!=i.expectedValues || sounds!=i.ledger || !sameBytes(values.hudObjects(),i.expectedReserved)
                || !now.status().equals(i.placed.status()) || !now.pool().slots().equals(i.placed.pool().slots())
                || !sameBytes(now.savedFlags(),i.placed.savedFlags()) || !presentation.sameAct2Scroll(i.scroll))
            throw new IllegalStateException("TimeStars changed Frame/full96/full31/status/saved/Values/HUD/scroll/queue/ledger/World/generation");
    }
    private TimeStarsInvocation requireTimeStars() {
        var i=activeTimeStars;verifyTimeStars(i);return i;
    }
    /** No factory read, raw import, default bank or public mutation authority. */
    private CdAssetBytes readTimeStarsReserved() {
        if(activePrimary!=null) {
            verifyPrimary(activePrimary);return values.hudObjects();
        }
        requireTimeStars();return values.hudObjects();
    }
    private void validateTimeStarsCut(TimeStarsInvocation i,CdReservedObjectBank.Snapshot cut,
                                      CdReservedObjectBank.Image source) {
        timeStarBank.validateRestore(cut);
        if(source==null || source.identity().wholeIndex()!=i.whole || !source.equals(cut.slots().get(i.whole-1)))
            throw new IllegalStateException("TimeStars omitted its actual selected source64");
        for(int whole=1;whole<32;whole++) {
            var image=cut.slots().get(whole-1);
            if(!Arrays.equals(image.copyBytes(),i.expectedReserved.slice((whole-1)*64,64).copy()))
                throw new IllegalStateException("TimeStars stale complete31 source at whole "+whole);
        }
    }
    private static CdAssetBytes replaceTimeStar(CdAssetBytes all,int whole,CdReservedObjectBank.Image image) {
        byte[] ready=all.copy();System.arraycopy(image.copyBytes(),0,ready,(whole-1)*64,64);return CdAssetBytes.copyOf(ready);
    }
    private static Values timeStarsPowerupValues(Values v,int powerup) {
        var l=v.level();var next=new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),
                l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),powerup,l.timeOver());
        return new Values(v.phase(),v.section(),v.cpuPalette(),v.fadePalette(),v.waterPalette(),v.displayedPalette(),
                v.cpuSat(),v.displayedSat(),v.displayedHScroll(),v.displayedVScrollLongBits(),v.foregroundPlane(),
                v.ring(),next,v.globals(),v.hudObjects(),v.fadeProgress(),v.calls(),v.loop());
    }
    private void beforeTimeStarsOperation(Object call) {
        var i=requireTimeStars();
        if(!timeStars.isCurrentCall(call) || timeStars.isCurrentPrimitive(call) || i.pending!=null || i.completed!=null
                || i.preparedReceipt!=null || i.adoptionReceipt!=null || i.completedReceipt!=null
                || timeStarsPort || drawingTimeStars || queuePort || timeStars.currentOrdinal(call)!=i.ordinal+1)
            throw new IllegalStateException("TimeStars operation lacks the original pending producer/ordinal");
        i.readyValues=i.expectedValues;
        if(call instanceof CdTimeStars.Publication c) {
            validateTimeStarsCut(i,c.cut(),c.source());
            if(c.output()==null || !c.output().equals(c.source().withBytes(c.output().copyBytes())))
                throw new IllegalStateException("TimeStars publication changed facade token/physical identity");
            i.readyValues=reservedValues(i.expectedValues,replaceTimeStar(i.expectedReserved,i.whole,c.output()));
        } else if(call instanceof CdTimeStars.PowerupReadCall c) {
            validateTimeStarsCut(i,c.cut(),c.source());
        } else if(call instanceof CdTimeStars.PowerupWriteCall c) {
            validateTimeStarsCut(i,c.cut(),c.source());int routine=c.source().byteAt(0x24);
            if(c.previousValue()!=values.level().powerup() || routine!=6
                    || !(c.value()==routine && (c.previousValue()&15)<8 && (c.previousValue()&15)!=routine
                        || c.previousValue()==routine && c.value()==(routine|0x80)))
                throw new IllegalStateException("TimeStars Powerup write differs from reached MOVE6/BSET7");
            i.readyValues=timeStarsPowerupValues(i.expectedValues,c.value());
        } else if(call instanceof CdTimeStars.DrawCall c) {
            validateTimeStarsCut(i,c.cut(),c.source());
            if(i.drawn || i.cleared)throw new IllegalStateException("Duplicate native reserved star DrawObject");
            var image=c.source().moveByteAt(1,c.source().byteAt(1)&0x7F);
            i.readyValues=reservedValues(i.expectedValues,replaceTimeStar(i.expectedReserved,i.whole,image));
        } else throw new IllegalStateException("Foreign TimeStars operation family");
        verifyTimeStars(i);i.pending=call;i.preparedReceipt=new Object();i.prepared=false;
        i.ordinal=timeStars.currentOrdinal(call);
    }
    private void requirePreparedTimeStarsReceipt(Object call) {
        var i=requireTimeStars();
        if(i.pending!=call || i.prepared || i.preparedReceipt==null || i.adoptionReceipt!=null
                || !timeStars.isCurrentCall(call) || timeStars.isCurrentPrimitive(call))
            throw new IllegalStateException("Missing/duplicate original TimeStars preflight receipt");
        i.prepared=true;
    }
    private TimeStarsInvocation requireTimeStarsPrimitive(Class<?> kind) {
        var i=requireTimeStars();
        if(i.pending==null || !kind.isInstance(i.pending) || !i.prepared || i.preparedReceipt==null
                || i.adoptionReceipt!=null || i.completed!=null || !timeStars.isCurrentPrimitive(i.pending))
            throw new IllegalStateException("No genuine once-only TimeStars primitive-active receipt");
        return i;
    }
    private boolean adoptTimeStarsReserved(CdAssetBytes expected,CdAssetBytes next) {
        var i=requireTimeStarsPrimitive(CdTimeStars.Publication.class);
        if(timeStarsPort || drawingTimeStars || queuePort || expected==null || next==null
                || expected.length()!=31*64 || next.length()!=31*64)
            throw new IllegalStateException("TimeStars reserved CAS is outside its actual primitive");
        if(!sameBytes(expected,i.expectedReserved))return false;
        if(!sameBytes(next,i.readyValues.hudObjects()))throw new IllegalStateException("TimeStars CAS changed prepared full31/nonwrites");
        timeStarsPort=true;
        try {
            verifyTimeStars(i);values=i.readyValues;i.expectedValues=i.readyValues;i.expectedReserved=i.readyValues.hudObjects();
            i.adoptionReceipt=i.preparedReceipt;
            verifyTimeStars(i);
            if(!sameBytes(values.hudObjects(),next))throw new IllegalStateException("TimeStars CAS omitted actual full31 adoption");
            return true;
        } finally {timeStarsPort=false;}
    }
    private int readTimeStarsPowerup() {
        var i=requireTimeStarsPrimitive(CdTimeStars.PowerupReadCall.class);
        if(timeStarsPort || drawingTimeStars || queuePort)throw new IllegalStateException("TimeStars Powerup read reentry");
        timeStarsPort=true;
        try {int value=values.level().powerup();i.adoptionReceipt=i.preparedReceipt;verifyTimeStars(i);return value;}
        finally {timeStarsPort=false;}
    }
    private void writeTimeStarsPowerup(int value) {
        var i=requireTimeStarsPrimitive(CdTimeStars.PowerupWriteCall.class);
        if(timeStarsPort || drawingTimeStars || queuePort || value!=((CdTimeStars.PowerupWriteCall)i.pending).value())
            throw new IllegalStateException("TimeStars Powerup write lacks its prepared actual value");
        timeStarsPort=true;
        try {
            verifyTimeStars(i);values=i.readyValues;i.expectedValues=i.readyValues;i.adoptionReceipt=i.preparedReceipt;
            verifyTimeStars(i);
            if(values.level().powerup()!=value)throw new IllegalStateException("TimeStars Powerup primitive omitted readback");
        } finally {timeStarsPort=false;}
    }
    private void drawTimeStar(CdReservedObjectBank bank,CdReservedObjectBank.Identity identity) {
        var i=requireTimeStarsPrimitive(CdTimeStars.DrawCall.class);
        if(bank!=timeStarBank || identity==null || identity.wholeIndex()!=i.whole || i.drawn || i.cleared
                || timeStarsPort || drawingTimeStars || queuePort)
            throw new IllegalStateException("No actual selected once-only reserved TimeStars DrawObject");
        i.drawn=true;drawingTimeStars=true;
        try {
            var image=reservedImage(i.whole);
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(i.whole),
                    image.unsignedByte(1),image.unsignedWord(8),image.unsignedWord(0x0C),image.unsignedByte(0x16),
                    image.unsignedByte(0x19),image.unsignedByte(0x18)),i.camera);
            if(!i.cleared)throw new IllegalStateException("TimeStars DrawObject omitted its actual entry bit clear");
            i.adoptionReceipt=i.preparedReceipt;verifyTimeStars(i);
        } catch(RuntimeException|Error original) {
            i.queueFailed=drawQueue.phase()==CdObjectDrawQueue.Phase.FAILED;throw original;
        } finally {drawingTimeStars=false;}
    }
    private void writeTimeStarsOnScreen(CdObjectDrawQueue.CdSlotRef ref,boolean onScreen) {
        var i=requireTimeStarsPrimitive(CdTimeStars.DrawCall.class);
        if(ref==null || ref.wholeIndex()!=i.whole || onScreen || !drawingTimeStars || timeStarsPort || queuePort
                || !i.drawn || i.cleared)throw new IllegalStateException("Only actual TimeStars DrawObject clears bit7 once");
        queuePort=true;
        try {
            verifyTimeStars(i);values=i.readyValues;i.expectedValues=i.readyValues;i.expectedReserved=i.readyValues.hudObjects();
            i.cleared=true;verifyTimeStars(i);
        } finally {queuePort=false;}
    }
    private void afterTimeStarsOperation(Object call) {
        var i=requireTimeStars();
        if(i.pending!=call || !timeStars.isCurrentCall(call) || timeStars.isCurrentPrimitive(call) || !i.prepared
                || i.preparedReceipt==null || i.adoptionReceipt!=i.preparedReceipt || i.completed!=null
                || values!=i.readyValues || timeStarsPort || drawingTimeStars || queuePort)
            throw new IllegalStateException("TimeStars original primitive omitted/duplicated its adoption receipt");
        var actual=timeStars.currentResult(call);
        if(call instanceof CdTimeStars.PowerupReadCall && !Objects.equals(actual,values.level().powerup()))
            throw new IllegalStateException("TimeStars actual Powerup read result differs");
        i.completed=call;i.completedReceipt=i.adoptionReceipt;verifyTimeStars(i);
    }
    private void consumeTimeStarsReceipt(Object call) {
        var i=requireTimeStars();
        if(i.pending!=call || i.completed!=call || i.completedReceipt!=i.preparedReceipt || i.preparedReceipt==null
                || i.adoptionReceipt!=i.preparedReceipt || !timeStars.isCurrentCall(call) || timeStars.isCurrentPrimitive(call))
            throw new IllegalStateException("Missing/clone/no-op/duplicate TimeStars completion receipt");
        timeStars.currentResult(call); // Original primitive completed before the next native read/store.
        i.pending=null;i.completed=null;i.preparedReceipt=null;i.adoptionReceipt=null;i.completedReceipt=null;
        i.readyValues=null;i.prepared=false;
    }
    private void failedTimeStarsOperation(Object call,Throwable original) {
        try {
            var i=requireTimeStars();
            if(!timeStars.isCurrentCall(call) || i.pending!=null && i.pending!=call)
                throw new IllegalStateException("Foreign failed TimeStars producer");
            verifyTimeStars(i);
        } catch(RuntimeException|Error verification) {if(verification!=original)original.addSuppressed(verification);}
    }

}
