package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import com.openggf.game.session.EngineTiming;
import com.openggf.game.session.EngineServices;

import java.util.*;
import java.util.function.UnaryOperator;

/** Actual private R12B World composition. Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Level_MainLoop (src/Level/Main.asm:333-432), VInt_Level (Main.asm:821-847).
 * Values remain in Core, Frame and Host; this object retains metadata and stack scopes only.
 */
final class SonicCdAct2PastOrdinaryWorld implements SonicCdAct2PastLoadingVisit.OrdinaryOwner {
    enum Boundary { LEVEL_FLOW, PRIMARY_DEATH_CAMERA, ACTIVE_POWERUP_CPU, PALETTE_SOURCE, MISSING_GLOBAL_ANIMATIONS }
    static final class Stopped extends UnsupportedOperationException {
        private final Boundary boundary;
        private Stopped(Boundary boundary) { super("R12B ordinary " + boundary); this.boundary=boundary; }
        Boundary boundary() { return boundary; }
    }
    private final SonicCdGameModule module;
    private final SonicCdAct2PastLoadingVisit core;
    private final SonicCdAct2PastWorldHost host;
    private final CdPlayerFrameRuntime frame;
    private final CdR12bTerrainAssets terrain;
    private final CdR12bPlayerAssets player;
    private final CdR12bSectionArt art;
    private final CdLevelHud.Act2Past hud;
    private final List<CdLevelPaletteCycle.Script> paletteCycles;
    private final SonicCdAct2PastOrdinaryLoop loop;
    private final SonicCdAct2PastObjectPass objects;
    private SonicCdAct2PastLoadingVisit.OrdinaryAccess active;
    private SonicCdAct2PastOrdinaryLoop.Cut cut;

    SonicCdAct2PastOrdinaryWorld(SonicCdGameModule module,SonicCdAct2PastLoadingVisit core,
                                SonicCdAct2PastWorldHost host,CdPlayerFrameRuntime frame,
                                CdR12bTerrainAssets terrain,CdR12bPlayerAssets player,CdR12bSectionArt art) {
        this.module=Objects.requireNonNull(module);this.core=Objects.requireNonNull(core);this.host=Objects.requireNonNull(host);
        this.frame=Objects.requireNonNull(frame);this.terrain=Objects.requireNonNull(terrain);this.player=Objects.requireNonNull(player);
        this.art=Objects.requireNonNull(art);
        if(!frame.stageSource().ownsAct2Past(terrain,player))throw new IllegalArgumentException("Actual R12B pair required");
        hud=new CdLevelHud.Act2Past(terrain.directArtSources().hudAssets(),art);
        var data=terrain.view(CdR12bProfile.Asset.PALETTE_CYCLE_DATA);
        var scripts=new ArrayList<CdLevelPaletteCycle.Script>();
        for(int i=0;i<6;i++)scripts.add(new CdLevelPaletteCycle.Script(data.slice(i*14,8),data.slice(i*14+8,6)));
        paletteCycles=List.copyOf(scripts);
        objects=new SonicCdAct2PastObjectPass(new ObjectPorts(),terrain,player,frame,host.placement());
        loop=new SonicCdAct2PastOrdinaryLoop(frame,terrain,player,new LoopPorts());
        frame.bindArtPublication(this::playerArt);
    }
    private void require() {
        host.requireRenderIdle();module.requireAct2PastLoadingVisit(core);
        if(active==null||frame.closed()||!frame.stageSource().ownsAct2Past(terrain,player))
            throw new IllegalStateException("No actual R12B ordinary World invocation");
        active.state();
    }
    @Override public void run(int held,SonicCdAct2PastLoadingVisit.OrdinaryAccess access) {
        host.requireRenderIdle();
        if(active!=null)throw new IllegalStateException("R12B ordinary reentry");
        module.requireAct2PastLoadingVisit(core);active=Objects.requireNonNull(access);
        try { require();loop.run(held); }
        catch(RuntimeException|Error original) {
            try {objects.discard();}catch(RuntimeException|Error cleanup){if(cleanup!=original)original.addSuppressed(cleanup);}
            throw original;
        } finally {cut=null;active=null;}
    }
    private void playerArt(CdPlayerArtRuntime.DmaRequest request,CdPlayerArtRuntime.State complete) {
        require();
        if(cut!=SonicCdAct2PastOrdinaryLoop.Cut.PUBLISH_LEVEL||request.vramByteAddress()!=0xF000
                ||!Arrays.equals(request.bytes().copy(),complete.residentBank().slice(0,0x2E0).copy())
                ||!Arrays.equals(frame.artState().residentBank().copy(),complete.residentBank().copy()))
            throw new IllegalStateException("Player DMA left its actual selector8 cut");
        // Frame already owns the complete native resident value. Host selects that same value
        // after this V-int's physical plane/PLC/HUD work; no atlas or second art bank is created.
    }
    private void adopt(SonicCdAct2PastWorldHost.Globals nextGlobals,CdR12bSectionArt.State section,
                       CdLevelGlobals level,CdAssetBytes reserved,CdAssetBytes normal,CdAssetBytes displayed,
                       List<SonicCdGameModule.PastSound> appended,Optional<CdObjectDrawQueue.CpuSat> cpu) {
        require();var before=active.state();var ready=active.prepare(before,section,level,reserved,normal,displayed);
        var publication=host.prepareOrdinary(host.globals(),host.video(),nextGlobals,appended,cpu);
        active.validate(before);host.validateOrdinary(publication);
        active.adopt(before,ready);host.adoptOrdinary(publication);
    }
    private void globals(SonicCdAct2PastWorldHost.Globals g,List<SonicCdGameModule.PastSound> sounds) {
        var s=active.state();adopt(g,s.section(),s.level(),s.hudSlots(),s.normalPalette(),s.displayedPalette(),sounds,host.video().completedCpu());
    }
    private static SonicCdAct2PastWorldHost.Globals values(SonicCdAct2PastWorldHost.Globals g,
            CdRingGlobals ring,CdCheckpointLiveAux.State live,CdOpeningObjectRuntime.ScoreGlobals score,
            Optional<CdLevelHud.BonusValues> bonus,OptionalInt pause,Optional<CdGlobalObjectAnimation.Extras> animations,
            CdAssetBytes steps,CdAssetBytes timers) {
        return new SonicCdAct2PastWorldHost.Globals(ring,live,g.contact(),score,g.projectorByte(),g.checkpointSaved(),
                g.drownTimerWord(),g.debugBlockWord(),pause,bonus,g.tail(),animations,steps,timers);
    }
    private void section(UnaryOperator<CdR12bSectionArt.State> operation) {
        require();var s=active.state();CdR12bSectionArt.State next;
        try { next=operation.apply(s.section()); }
        catch(CdR12bSectionArt.SectionArtException failure) {
            adopt(host.globals(),failure.state(),s.level(),s.hudSlots(),s.normalPalette(),s.displayedPalette(),List.of(),host.video().completedCpu());
            host.ordinaryCurrent(failure.state());throw failure;
        }
        adopt(host.globals(),next,s.level(),s.hudSlots(),s.normalPalette(),s.displayedPalette(),List.of(),host.video().completedCpu());
        host.ordinaryCurrent(next);
    }
    private void adoptHud(CdLevelHud.Act2PastState next) {
        var g=host.globals();var s=active.state();
        var ready=values(g,next.ring(),next.liveTime(),next.score(),next.bonus(),g.pauseMusicBit(),g.animations(),g.paletteSteps7(),g.paletteTimers7());
        adopt(ready,next.section(),next.level(),s.hudSlots(),s.normalPalette(),s.displayedPalette(),List.of(),host.video().completedCpu());
        host.ordinaryCurrent(next.section());
    }
    private final class LoopPorts implements SonicCdAct2PastOrdinaryLoop.Ports {
        @Override public void requireCurrent(SonicCdAct2PastOrdinaryLoop.Cut at,CdPlayerFrameRuntime f,CdR12bTerrainAssets t,CdR12bPlayerAssets p) {
            require();if(f!=frame||t!=terrain||p!=player)throw new IllegalStateException("Foreign ordinary kernel");cut=at;
        }
        @Override public Optional<CdObjectDrawQueue.CpuSat> beforeSelector8(){require();host.takeSounds();return host.video().completedCpu();}
        @Override public void publishLevel(Optional<CdObjectDrawQueue.CpuSat> selected){
            require();var s=active.state();host.publishOrdinary(s.normalPalette(),selected);
            adopt(host.globals(),s.section(),s.level(),s.hudSlots(),s.normalPalette(),s.normalPalette(),List.of(),host.video().completedCpu());
        }
        @Override public void updateAnimTiles(){
            require();var s=active.state();var l=s.level();var gate=CdPowerupArtRuntime.gate(l.powerup());
            var next=new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),gate.powerup(),l.timeOver());
            adopt(host.globals(),s.section(),next,s.hudSlots(),s.normalPalette(),s.displayedPalette(),List.of(),host.video().completedCpu());
            if(gate.copyRequired())throw new Stopped(Boundary.ACTIVE_POWERUP_CPU);
        }
        @Override public void copyCameraAndDrawLevel(){require();host.copyAndDrawOrdinary();}
        @Override public void serviceSlowPlc(){require();section(s->art.serviceSlow(s,host.scroll().foreground().scrollLock()));}
        @Override public void updateHud(){
            require();var s=active.state();var g=host.globals();var e=frame.environment();var m=frame.state().motion();
            var inputs=new CdLevelHud.Inputs(new CdLevelHud.Globals(e.globals().debugCheat(),
                    (g.pauseMusicBit().orElseThrow()|(frame.paused()?1:0))<<8,e.controls().controlLocked(),e.movement().gates().timeZone(),
                    m.centreX16_16()>>>16,m.centreY16_16()>>>16,e.movement().gates().zoneAct()),
                    new CdLevelHud.DebugData(OptionalInt.empty(),s.layoutRam(),g.debugBlockWord()));
            try {adoptHud(hud.run(new CdLevelHud.Act2PastState(s.section(),g.ring(),s.level(),g.score(),g.live(),g.bonus()),inputs).state());}
            catch(CdLevelHud.UnsupportedAct2PastHud failure){adoptHud(failure.state());throw failure;}
        }
        @Override public void mainMusic(){
            require();EngineTiming.vIntRunCounter(EngineServices.current()).serviceRepresentedVBlank();
            var g=host.globals();int old=g.pauseMusicBit().orElseThrow();boolean paused=frame.paused();
            var sounds=new ArrayList<SonicCdGameModule.PastSound>();
            if(paused)sounds.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ChargeSound(CdPlayerCharge.Sound.FM_CHARGESTOP)));
            if(paused?((old&0x80)==0):((old&0x80)!=0)) {
                var e=frame.environment();boolean pcm=(e.movement().gates().timeZone()&0x7F)==0&&e.globals().invincible()==0&&e.movement().gates().speedShoes()==0;
                var command=paused?pcm?SonicCdGameModule.PastMusicCommand.SCMD_PAUSEPCM:SonicCdGameModule.PastMusicCommand.SCMD_PAUSECDA
                        :pcm?SonicCdGameModule.PastMusicCommand.SCMD_UNPAUSEPCM:SonicCdGameModule.PastMusicCommand.SCMD_UNPAUSECDA;
                sounds.add(new SonicCdGameModule.PastSubCpuSound(command));
            }
            globals(values(g,g.ring(),g.live(),g.score(),g.bonus(),OptionalInt.of(paused?old|0x80:old&0x7F),g.animations(),g.paletteSteps7(),g.paletteTimers7()),sounds);
        }
        @Override public void settleSelectedCpu(Optional<CdObjectDrawQueue.CpuSat> selected){require();host.settleOrdinary(selected);}
        @Override public void spawnObjects(){require();host.placement().spawnObjects(host.scroll().foreground().cameraX(),frame.environment().movement().gates().timeZone());}
        @Override public void runObjects(int levelFrame){require();objects.runWalk(levelFrame);}
        @Override public void postObjectCamera(){
            require();var s=active.state();
            if(s.program().demoDataIndexWord()==0x800||s.level().levelRestart()!=0)throw new Stopped(Boundary.LEVEL_FLOW);
            if(frame.environment().globals().debugMode()==0&&frame.state().motion().routine()>=6)throw new Stopped(Boundary.PRIMARY_DEATH_CAMERA);
            host.scrollOrdinary(s.level().updateHudTime());
        }
        @Override public void drawObjects(){
            require();var s=host.scroll();var bg=s.background();
            objects.finishDraw(new CdObjectDrawQueue.DrawCameras(new CdObjectDrawQueue.CameraWords(s.foreground().cameraX(),s.foreground().cameraYWord()),
                    new CdObjectDrawQueue.CameraWords(bg.cameraBgX()>>>16,bg.cameraBgY()>>>16),
                    new CdObjectDrawQueue.CameraWords(bg.cameraBg3X()>>>16,bg.cameraBg3Y()>>>16)));
        }
        @Override public void paletteCycleIfTimeRunning(){
            require();var g=host.globals();if(g.ring().timeStopTimer()!=0)return;var s=active.state();
            var result=CdLevelPaletteCycle.pass(paletteCycles,new CdLevelPaletteCycle.State(s.normalPalette(),g.paletteTimers7().slice(0,6),g.paletteSteps7().slice(0,6)));
            byte[] timers=g.paletteTimers7().copy(),steps=g.paletteSteps7().copy();
            System.arraycopy(result.state().timers().copy(),0,timers,0,6);System.arraycopy(result.state().steps().copy(),0,steps,0,6);
            var ready=values(g,g.ring(),g.live(),g.score(),g.bonus(),g.pauseMusicBit(),g.animations(),CdAssetBytes.copyOf(steps),CdAssetBytes.copyOf(timers));
            adopt(ready,s.section(),s.level(),s.hudSlots(),result.state().palette(),s.displayedPalette(),List.of(),host.video().completedCpu());
            if(!result.completed())throw new Stopped(Boundary.PALETTE_SOURCE);
        }
        @Override public void updateSectionArt(){require();section(s->art.updateSection(s,host.scroll().foreground().cameraX()));}
        @Override public void preparePlc(){require();section(art::prepare);}
        @Override public void updateGlobalAnimations(){
            require();var g=host.globals();var extras=g.animations().orElseThrow(()->new Stopped(Boundary.MISSING_GLOBAL_ANIMATIONS));
            var result=CdGlobalObjectAnimation.step(extras,g.ring());
            globals(values(g,result.ring(),g.live(),g.score(),g.bonus(),g.pauseMusicBit(),Optional.of(result.extras()),g.paletteSteps7(),g.paletteTimers7()),List.of());
        }
    }
    private SonicCdAct2PastObjectPass.Cut objectCut() {
        require();var s=active.state();var v=host.video();var f=host.scroll().foreground();
        return new SonicCdAct2PastObjectPass.Cut(host.globals(),s.level(),s.program().goodFutureByte(),module.act2PastGoodFutureFlags(core),
                s.hudSlots(),v.cpuSat(),v.completedCpu(),host.sounds(),f.cameraX(),f.cameraYWord(),frame.environment().globals().timeAttackMode(),OptionalInt.empty());
    }
    private static boolean sameCut(SonicCdAct2PastObjectPass.Cut a,SonicCdAct2PastObjectPass.Cut b) {
        return a.globals()==b.globals()&&a.level()==b.level()&&a.goodFutureByte()==b.goodFutureByte()&&a.goodFutureFlags().equals(b.goodFutureFlags())
                &&a.reservedSlots()==b.reservedSlots()&&a.cpuSat()==b.cpuSat()&&a.completedCpu()==b.completedCpu()&&a.sounds()==b.sounds()
                &&a.cameraXWord()==b.cameraXWord()&&a.cameraYWord()==b.cameraYWord()&&a.timeAttackModeByte()==b.timeAttackModeByte()&&a.layerByte().equals(b.layerByte());
    }
    private final class ObjectPorts implements SonicCdAct2PastObjectPass.Ports {
        @Override public void validate(SonicCdAct2PastObjectPass pass,CdPlayerFrameRuntime f,CdR12bTerrainAssets t,CdR12bPlayerAssets p,CdObjectPlacementRuntime placement){
            require();if(pass!=objects||f!=frame||t!=terrain||p!=player||placement!=host.placement()
                    ||(cut!=SonicCdAct2PastOrdinaryLoop.Cut.RUN_OBJECTS&&cut!=SonicCdAct2PastOrdinaryLoop.Cut.DRAW_OBJECTS))
                throw new IllegalStateException("No actual R12B physical pass");
        }
        @Override public SonicCdAct2PastObjectPass.Cut read(){require();return objectCut();}
        /** Source LoadPLC4/5 only. Both actual lists are one descriptor; no service/current writes here. */
        @Override public void submitPlc(SonicCdAct2PastObjectPass.PlcSubmission submission){
            require();if(submission.owner()!=objects||!objects.isCurrentPlcSubmission(submission)||cut!=SonicCdAct2PastOrdinaryLoop.Cut.RUN_OBJECTS)
                throw new IllegalArgumentException("Not actual Projector LoadPLC producer");
            submission.validate();var before=active.state();
            // appendList is pure. A full16-entry4/5 append exposes SOURCE_SCAN96 before any store;
            // its stopped diagnostic cannot enter Core.prepare. Preserve the original exception and live Core.
            var section=art.appendList(before.section(),submission.nativeId());
            if(section.residency()!=before.section().residency())throw new IllegalStateException("PLC append changed current knowledge");
            var ready=active.prepare(before,section,before.level(),before.hudSlots(),before.normalPalette(),before.displayedPalette());
            var hostReady=host.prepareOrdinary(host.globals(),host.video(),host.globals(),List.of(),host.video().completedCpu());
            active.validate(before);host.validateOrdinary(hostReady);submission.validate();
            active.adopt(before,ready);host.adoptOrdinary(hostReady);submission.acknowledge();
        }
        @Override public void adopt(SonicCdAct2PastObjectPass.Adoption publication){
            require();if(!objects.isCurrentAdoption(publication)||!sameCut(publication.expected(),objectCut()))throw new IllegalArgumentException("Stale/foreign object publication");
            var next=publication.next();var before=active.state();
            if(!next.goodFutureFlags().equals(module.act2PastGoodFutureFlags(core)))
                throw new IllegalArgumentException("Unbound good-future programme writer");
            var ready=active.prepare(before,before.section(),next.level(),next.reservedSlots(),before.normalPalette(),before.displayedPalette());
            ready=active.prepareGoodFuture(before,ready,next.goodFutureByte());
            var oldSounds=host.sounds();if(next.sounds().size()<oldSounds.size()||!next.sounds().subList(0,oldSounds.size()).equals(oldSounds))throw new IllegalArgumentException("Sound prefix replaced");
            var hostReady=host.prepareOrdinary(host.globals(),host.video(),next.globals(),next.sounds().subList(oldSounds.size(),next.sounds().size()),next.completedCpu());
            active.validate(before);host.validateOrdinary(hostReady);publication.applyNative();
            active.adopt(before,ready);host.adoptOrdinary(hostReady);
        }
    }
}
