package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.options.Sonic2CompetitionRouteAdapter;
import com.openggf.route.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tools.HeadlessGameBoot;
import com.openggf.GameLoop;
import java.nio.file.Path;
import java.util.*;

/**
 * S2 production title/menu/competition adapter for the shared bounded route search.
 * Inputs: verified REV01 ROM and exact two-pad rows. Origin: lane P, 2026-11-08.
 * One isolated worker owns one world; ordinary actor views have ordinary-act semantics.
 */
public final class StageplayCompetitionRouteHosts implements RouteWorldFactory {
    private World current;
    private final Path rom;
    private final java.util.function.Consumer<RouteSample> diagnostic;
    public StageplayCompetitionRouteHosts(Path rom) { this(rom, ignored -> { }); }
    public StageplayCompetitionRouteHosts(Path rom, java.util.function.Consumer<RouteSample> diagnostic) {
        this.rom=Objects.requireNonNull(rom);this.diagnostic=Objects.requireNonNull(diagnostic);
    }
    @Override public String environment() { return "openggf " + com.openggf.version.AppVersion.get() + " stageplay-s2-competition-title/1"; }
    @Override public RouteWorld openSearch(LaunchContract launch) { return current=new World(launch, rom, diagnostic); }
    @Override public ReplayWorld openColdReplay(LaunchContract launch) { return current=new World(launch, rom, diagnostic); }

    /** Draw/read the current completed native page without advancing a row. */
    public com.openggf.graphics.RgbaImage renderCurrent() {
        if(current==null) throw new IllegalStateException("no open competition route world");
        return current.render();
    }
    /** Video demo: renders the current game mode of the open world, including title and menus. */
    public com.openggf.graphics.RgbaImage renderAnyCurrent() {
        if(current==null) throw new IllegalStateException("no open competition route world");
        return current.renderAny();
    }
    public String currentGameMode() { return current==null?"none":String.valueOf(current.loop.getCurrentGameMode()); }

    /** ROM-backed geometry inventory for editor picking and bounded goal declarations; no controller rows. */
    public java.util.List<Region> flipTileLocations() {
        if(current==null || current.adapter[0].visit()==null || current.adapter[0].visit().host().profile()==null)
            return java.util.List.of();
        var host=current.adapter[0].visit().host();
        if(host.profile().romZone()!=12) return java.util.List.of();
        var draw=new com.openggf.game.sonic2.competition.view.Sonic2CompetitionPlaneDraw(host.nativeArt().workRamBytes());
        var result=new java.util.ArrayList<Region>();
        for(int y=0;y<host.profile().bounds().maxY()+224;y+=16) for(int x=0;x<host.profile().bounds().maxX()+320;x+=16) {
            var block=draw.getBlock(com.openggf.game.sonic2.competition.view.Sonic2CompetitionPlaneDraw.Layout.FOREGROUND,x,y);
            if(block.words().stream().mapToInt(w->w&0x3ff).anyMatch(t->t>=0x198&&t<0x1a0 || t>=0x3a0&&t<0x3a8))
                result.add(new Region(x,y,x+15,y+15));
        }
        return java.util.List.copyOf(result);
    }

    public static LaunchContract launch() { return CompetitionRouteEntry.launch(); }
    public static InputTape menuPrefix(int entry) { return CompetitionRouteEntry.menuPrefix(entry); }

    private static final class World implements RouteWorld {
        private final HeadlessGameBoot boot;
        private final GameLoop loop;
        private com.openggf.debug.playback.Bk2FrameInput previous;

        private final Sonic2CompetitionRouteAdapter[] adapter = new Sonic2CompetitionRouteAdapter[1];
        private final java.util.function.Consumer<RouteSample> diagnostic;
        private final Sonic2CompetitionRouteWitness witness=new Sonic2CompetitionRouteWitness(()->{
            try { return com.openggf.data.RomByteReader.fromRom(GameServices.rom().getRom()); }
            catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        });
        private int rows;
        private int gameplayAnchorRows = -1;
        private int levelAnchorRows=-1;
        private long epoch;
        private Object level;
        private Object mode;
        World(LaunchContract launch, Path rom, java.util.function.Consumer<RouteSample> diagnostic) {
            this.diagnostic=diagnostic;
            if(SessionManager.getCurrentWorldSession()!=null)
                throw new RouteHostException(RouteHostException.Reason.UNSUPPORTED_LAUNCH,"competition routes require an isolated worker");
            if (!(launch.inlet() instanceof Inlet.Title) || !launch.rom().equals(RomIdentity.standard("s2"))
                    || !launch.roster().equals(new Roster("sonic", "tails")) || !launch.initial().seeds().none() || launch.width()!=320 || !launch.donor().equals("off"))
                throw new RouteHostException(RouteHostException.Reason.UNSUPPORTED_LAUNCH, "requires retail S2 title, Sonic/Tails, 320 pixels, no donor or seeds");
            try {
                var image = com.openggf.data.PhysicalImage.probe(rom);
                if (!image.fingerprint().sha1Hex().equalsIgnoreCase(launch.rom().sha1()))
                    throw new RouteHostException(RouteHostException.Reason.ROM_UNAVAILABLE, "S2 ROM SHA-1 mismatch");
            } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            if(launch.initial().vIntStart()!=0) throw new RouteHostException(RouteHostException.Reason.UNSUPPORTED_LAUNCH,"title inlet owns its initial clock");
            boot = new HeadlessGameBoot(launch.width(),448,launch.width(),224);
            try {
                boot.setModuleDecorator(base -> adapter[0] = new Sonic2CompetitionRouteAdapter(base));
                boot.setBootToTitle(true);
                loop = boot.boot(rom,0,0);
                boundary();
            } catch (Exception | Error e) {
                try { com.openggf.graphics.GraphicsManager.destroyForReinit(); } finally { boot.close(); }
                throw new IllegalStateException("competition title boot",e);
            }
        }
        private void boundary() {
            var current = SessionManager.getCurrentGameplayMode();
            Object next = current == null ? null : current.getLevelManager().getCurrentLevel();
            Object nextMode = loop.getCurrentGameMode();
            if (level != next || mode != nextMode) { epoch++; level = next; mode = nextMode; }
        }
        @Override public RecordedStep step(PadRow row) {
            var input = row.toEngine(rows);
            try(var ownership = com.openggf.control.RecordedInputOwnership.acquire(loop.getInputHandler())) {
                loop.getInputHandler().setLogicalOverride(com.openggf.debug.playback.RecordedInputSnapshots.fromBk2(input,previous));
                previous = input;
                loop.step();
            }
            GameServices.graphics().runPendingRenderThreadTasks();
            rows++; boundary();
            return new RecordedStep(row, observe());
        }
        @Override public RouteSample observe() {
            var visit = com.openggf.game.nativemenu.NativeMenuController.handles(loop.getCurrentGameMode()) ? adapter[0].visit() : null;
            var context = SessionManager.getCurrentGameplayMode();
            var lm = context.getLevelManager();
            var facts = new LinkedHashMap<String,Integer>();
            var title = (com.openggf.game.sonic2.titlescreen.TitleScreenManager) loop.getTitleScreenProvider();
            facts.put("title.state",title.getState().ordinal());
            facts.put("title.selection",title.getNativeMenuSelection());
            var p1 = GameServices.sprites().getMainPlayable();
            com.openggf.sprites.playable.AbstractPlayableSprite p2 = null;
            if (visit != null) {
                var state = visit.session();
                facts.put("competition.phase", state.phase().ordinal());
                facts.put("competition.titleCard", visit.host().titleCardActive()?1:0);
                facts.put("input.locked", visit.inputLocked()?1:0);
                // loadAct has returned from its real title owner, before the ordinary LFC increment.
                if (gameplayAnchorRows < 0 && state.phase()==com.openggf.game.sonic2.competition.Sonic2CompetitionSession.Phase.ACT
                        && !visit.host().titleCardActive() && lm.getFrameCounter()==0) gameplayAnchorRows=rows;
                if(state.phase()==com.openggf.game.sonic2.competition.Sonic2CompetitionSession.Phase.ACT
                        && !visit.host().titleCardActive() && lm.getFrameCounter()==0)levelAnchorRows=rows;
                if(levelAnchorRows>=0)facts.put("input.levelAnchorRows",levelAnchorRows);
                if (gameplayAnchorRows >= 0) facts.put("input.gameplayAnchorRows",gameplayAnchorRows);
                facts.put("competition.paused",state.gameState(0).isGamePaused()?1:0);
                facts.put("input.menu",state.phase()==com.openggf.game.sonic2.competition.Sonic2CompetitionSession.Phase.ACT?0:1);
                for(int slot=0;slot<2;slot++) {
                    facts.put("competition.p"+(slot+1)+".rings",state.levelState(slot).getRings());
                    facts.put("competition.p"+(slot+1)+".totalRings",state.playerState(slot).totalRings());
                    facts.put("competition.p"+(slot+1)+".monitors",state.playerState(slot).monitorsBroken());
                    facts.put("competition.p"+(slot+1)+".lives",state.gameState(slot).getLives());
                    facts.put("competition.p"+(slot+1)+".timerPaused",state.playerState(slot).isTimerPaused()?1:0);
                    facts.put("competition.p"+(slot+1)+".timeOver",state.timeOver(slot)?1:0);
                    facts.put("competition.p"+(slot+1)+".score",state.gameState(slot).getScore());
                    facts.put("competition.p"+(slot+1)+".time",Math.toIntExact(state.playerState(slot).getTimerFrames()));
                    if(state.player(slot)!=null) {
                        facts.put("competition.p"+(slot+1)+".objectControlled",state.player(slot).isObjectControlled()?1:0);
                        facts.put("competition.p"+(slot+1)+".shield",state.player(slot).hasShield()?1:0);
                        facts.put("competition.p"+(slot+1)+".speedShoes",state.player(slot).hasSpeedShoes()?1:0);
                        facts.put("competition.p"+(slot+1)+".invincibility",state.player(slot).getInvincibleFrames()>0?1:0);
                        facts.put("competition.p"+(slot+1)+".dead",state.player(slot).getDead()?1:0);
                        facts.put("competition.p"+(slot+1)+".cameraX",state.camera(slot).getX()&65535);
                        facts.put("competition.p"+(slot+1)+".cameraY",state.camera(slot).getY()&65535);
                    }
                }
                facts.put("competition.selection", state.selection());
                facts.put("competition.romZone",state.romZone());facts.put("competition.act",state.act());
                facts.put("competition.resultsReady",state.resultsPlcReady()?1:0);
                facts.put("competition.presentationFrame",state.presentationFrame());
                if(state.currentZone()>=0 && state.currentZone()<3) {
                    var wins=state.zoneWins(state.currentZone());
                    facts.put("competition.zoneWins.p1",wins.player1());facts.put("competition.zoneWins.p2",wins.player2());
                }
                facts.put("competition.firstFinisher", state.firstFinisher());
                facts.put("competition.specialStage", state.specialStageIndex());
                if (state.player(0) != null) { p1 = state.player(0); p2 = state.player(1); }
            }
            facts.put("competition.levelFrame", lm.getFrameCounter());
            facts.put("competition.levelPhase", lm.getFrameCounter() & 7);
            if (context.getObjectManager() != null) {
                for (var icon : context.getObjectManager().activeObjectsOfType(
                        com.openggf.game.sonic2.competition.Sonic2CompetitionMonitorContents.class)) {
                    if (!icon.isDestroyed()) {
                        facts.put("competition.monitor.spawned", 1);
                    }
                    if (!icon.isDestroyed() && icon.initializedContent() != 0) {
                        facts.put("competition.monitor.content", icon.initializedContent());
                        facts.put("competition.monitor.initFrame", icon.initializationFrame());
                        facts.put("competition.monitor.breaker", icon.breakerSlot() + 1);
                    }
                }
            }
            var observed = new ArrayList<ObjectView>();
            var interactions = new ArrayList<InteractionView>();
            if(context.getObjectManager()!=null) observeObjects(context.getObjectManager(),lm.getCurrentZone(),lm.getCurrentAct(),p1,p2,observed,interactions);
            if (p2 != null) {
                if(lm.getAnimatedPatternManager() instanceof com.openggf.game.sonic2.competition.view.Sonic2CompetitionAnimatedArt animation) {
                    var counters=animation.capture().counters();
                    for(int i=0;i<counters.size();i++) {
                        facts.put("competition.animation."+i+".nextFrame",counters.get(i).nextFrame());
                        facts.put("competition.animation."+i+".timer",counters.get(i).timer());
                    }
                }
                var module = context.getWorldSession().getGameModule();
                var lifecycle = module.getGameService(com.openggf.game.sonic2.competition.lifecycle.Sonic2CompetitionLifecycle.class);
                if(lifecycle != null) for(int slot=0;slot<2;slot++)
                    facts.put("competition.p"+(slot+1)+".routine",lifecycle.nativeRoutineByte(slot));
                if(module.getZoneFeatureProvider() instanceof com.openggf.game.sonic2.Sonic2ZoneFeatureProvider features
                        && features.getSlotMachineManager()!=null) {
                    var slots = features.getSlotMachineManager().snapshot();
                    facts.put("competition.slot.inUse",slots.inUse()?1:0);
                    facts.put("competition.slot.routine",slots.routine());
                    facts.put("competition.slot.timer",slots.timer());
                    for(int i=0;i<3;i++) facts.put("competition.slot.position"+i,slots.slotPos().get(i));
                }
            }
            if(p2!=null && visit.session().phase()==com.openggf.game.sonic2.competition.Sonic2CompetitionSession.Phase.ACT)
                witness.observe(context,facts,this::render);
            return new RouteSample(rows, epoch, new RouteSample.Level(lm.getCurrentZone(),lm.getCurrentAct(),
                    lm.getApparentAct(), String.valueOf(mode)), new RouteSample.Clocks(
                    context.getObjectManager()==null?com.openggf.game.session.EngineTiming.vIntRunCounter(com.openggf.game.session.EngineServices.current()).objectClockSeed():context.getObjectManager().getVblaCounter(),lm.getFrameCounter()), view(p1),
                    Optional.ofNullable(p2).map(World::view),
                    p2==null?context.getCamera().getX()&65535:visit.session().camera(0).getX()&65535,
                    p2==null?context.getCamera().getY()&65535:visit.session().camera(0).getY()&65535,
                    p2!=null && visit.session().camera(0).isVerticalWrapEnabled()?visit.session().camera(0).getVerticalWrapRange():0,
                    observed,interactions,facts);
        }
    private void observeObjects(com.openggf.level.objects.ObjectManager manager, int zone, int act,
                                AbstractPlayableSprite p1, AbstractPlayableSprite p2, java.util.List<ObjectView> out,
                                java.util.List<InteractionView> interactions) {
        var query = manager.objectQuery();
        var riding1 = p1 == null ? null : manager.getRidingObject(p1);
        var riding2 = p2 == null ? null : manager.getRidingObject(p2);
        for (var object : manager.getActiveObjects()) {
            boolean phase = object instanceof com.openggf.level.objects.observe.PhaseSource;
            boolean attachment = object instanceof com.openggf.level.objects.observe.AttachmentSource;
            boolean hits = object instanceof com.openggf.level.objects.observe.HitPointSource;
            if (!phase && !attachment && !hits && object != riding1 && object != riding2) continue;
            var identity = query.identityOf(object);
            if (identity.isEmpty()) continue;
            var ref = identity.get();
            var key = new InstanceKey(epoch, ref.slotIndex(), ref.generation(), ref.spawnId(), ref.dynamicId(),
                    ref.kind().name());
            var spawn = object.getSpawn();
            java.util.Optional<PlacementKey> placement = spawn != null && spawn.layoutIndex() >= 0
                    ? java.util.Optional.of(new PlacementKey(zone, act, spawn.layoutIndex(), spawn.objectId(), spawn.subtype()))
                    : java.util.Optional.empty();
            var capabilities = java.util.EnumSet.noneOf(Capability.class);
            java.util.List<String> phaseNames = java.util.List.of();
            String current = "";
            if (object instanceof com.openggf.level.objects.observe.PhaseSource source) {
                capabilities.add(Capability.PHASE);
                phaseNames = source.phaseNames();
                current = source.phase();
            }
            int remaining = -1;
            boolean defeated = false;
            if (object instanceof com.openggf.level.objects.observe.HitPointSource source) {
                capabilities.add(Capability.HIT_POINTS);
                remaining = source.hitsRemaining();
                defeated = source.defeated();
            }
            if (attachment) capabilities.add(Capability.ATTACHMENT);
            out.add(new ObjectView(key, placement, object.getClass().getSimpleName(), object.getX(), object.getY(),
                    capabilities, phaseNames, current, remaining, defeated));
            if (object == riding1) interactions.add(new InteractionView(key, PlayerSlot.P1, InteractionKind.STANDING,
                    object.getX(), object.getY(), false));
            if (object == riding2) interactions.add(new InteractionView(key, PlayerSlot.P2, InteractionKind.STANDING,
                    object.getX(), object.getY(), false));
            if (object instanceof com.openggf.level.objects.observe.AttachmentSource source) {
                attach(source, p1, PlayerSlot.P1, key, interactions);
                attach(source, p2, PlayerSlot.P2, key, interactions);
            }
        }
    }

    private static void attach(com.openggf.level.objects.observe.AttachmentSource source, AbstractPlayableSprite player,
                               PlayerSlot slot, InstanceKey key, java.util.List<InteractionView> interactions) {
        if (player == null) return;
        source.attachmentOf(player).ifPresent(a -> interactions.add(new InteractionView(key, slot,
                InteractionKind.valueOf(a.kind().name()), a.anchorX(), a.anchorY(), a.releasing())));
    }

        private com.openggf.graphics.RgbaImage render() {
            if(adapter[0].visit()==null) throw new IllegalStateException("no native visit to draw");
            adapter[0].visit().draw();
            return com.openggf.graphics.ScreenshotCapture.captureFramebuffer(320,448);
        }
        /** Video-demo draw of whatever the loop shows: title, native menu/competition screens, or black. */
        private com.openggf.graphics.RgbaImage renderAny() {
            var graphics=GameServices.graphics();
            graphics.runPendingRenderThreadTasks();
            var gameMode=loop.getCurrentGameMode();
            if(gameMode==com.openggf.game.GameMode.TITLE_SCREEN && !loop.isTitleEntryShowingLevel()) {
                var title=loop.getTitleScreenProvider();
                title.setClearColor(); org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT|org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
                title.draw();
                title.drawOverlay(new com.openggf.mods.ui.LevelOverlayCanvas(graphics,graphics.getProjectionWidth(),graphics.getConfiguredScreenHeightPx()));
                graphics.flushScreenSpace();
                if(graphics.getUiRenderPipeline()!=null) graphics.getUiRenderPipeline().renderFadePass();
            } else if(com.openggf.game.nativemenu.NativeMenuController.handles(gameMode)
                    && com.openggf.NativeMenuPresentationAccess.screen(loop)!=null) {
                var screen=com.openggf.NativeMenuPresentationAccess.screen(loop);
                screen.setClearColor(); org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT|org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
                graphics.resetForFixedFunction();
                screen.draw();
                graphics.flushScreenSpace();
                if(graphics.getUiRenderPipeline()!=null) graphics.getUiRenderPipeline().renderFadePass();
            } else {
                org.lwjgl.opengl.GL11.glClearColor(0,0,0,1); org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT);
            }
            org.lwjgl.opengl.GL11.glFinish();
            return com.openggf.graphics.ScreenshotCapture.captureFramebuffer(320,448);
        }

        private static PlayerView view(com.openggf.sprites.playable.AbstractPlayableSprite p) {
            if(p==null) return new PlayerView(0,0,0,0,0,0,0,0,false,false,false,false,false,false,0,false,false,false,PlayerView.Motion.NONE);
            var o = com.openggf.sprites.playable.PlayablePuppet.observe(p);
            return new PlayerView(p.getCentreX(),p.getCentreY(),p.getXSubpixelRaw(),p.getYSubpixelRaw(),
                    p.getXSpeed(),p.getYSpeed(),p.getGSpeed(),p.getAngle()&255,p.getAir(),p.getRolling(),
                    p.getSpindash(),p.getCrouching(),p.isHurt(),p.getDead(),p.getRingCount(),p.isOnObject(),
                    p.isObjectControlled(),p.getDirection()==com.openggf.physics.Direction.LEFT,
                    new PlayerView.Motion(o.maxSpeed(),o.runAccel(),o.runDecel(),o.friction(),o.slopeImpulse(),o.skidding()));
        }
        @Override public int rowsOffered() { return rows; }
        private record Saved(World owner, long epoch, Object level,
                com.openggf.game.rewind.RewindRegistry registry, long layout,
                com.openggf.game.rewind.CompositeSnapshot state,
                com.openggf.debug.playback.Bk2FrameInput previous, int rows, int levelAnchorRows, int gameplayAnchorRows) implements WorldCheckpoint { }
        @Override public WorldCheckpoint capture() {
            var visit = adapter[0].visit();
            var context = SessionManager.getCurrentGameplayMode();
            if(visit==null || visit.session().phase()!=com.openggf.game.sonic2.competition.Sonic2CompetitionSession.Phase.ACT
                    || visit.host().titleOnly()
                    || loop.getCurrentGameMode()!=com.openggf.game.GameMode.COMPETITION
                    || context.getLevelManager().hasPendingFreshLevelTransitionBoundary()
                    || GameServices.graphics().getFadeManager().isActive())
                throw new RouteHostException(RouteHostException.Reason.CHECKPOINT_REFUSED,"outside stable competition act");
            var registry=context.getRewindRegistry();
            return new Saved(this,epoch,level,registry,registry.courseLayoutVersion(),registry.capture(),previous,rows,levelAnchorRows,gameplayAnchorRows);
        }
        @Override public void restore(WorldCheckpoint checkpoint) {
            var context=SessionManager.getCurrentGameplayMode();
            if(!(checkpoint instanceof Saved c) || c.owner()!=this || c.epoch()!=epoch || c.level()!=level
                    || c.registry()!=context.getRewindRegistry() || c.layout()!=c.registry().courseLayoutVersion())
                throw new RouteHostException(RouteHostException.Reason.CHECKPOINT_MISMATCH,"competition checkpoint belongs to another epoch/layout");
            c.registry().restore(c.state()); previous=c.previous(); rows=c.rows(); levelAnchorRows=c.levelAnchorRows(); gameplayAnchorRows=c.gameplayAnchorRows();
        }
        @Override public void close() {
            try { diagnostic.accept(observe()); }
            finally { try { if(adapter[0]!=null && adapter[0].visit()!=null) adapter[0].visit().reset(); }
            finally {
                try { com.openggf.graphics.GraphicsManager.destroyForReinit(); }
                finally { boot.close(); }
            } }
        }
    }
}
