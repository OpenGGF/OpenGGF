package com.openggf.game.mutators;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameplayObservation;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.*;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.LevelSpritePresentation;
import com.openggf.mods.mutators.*;
import com.openggf.sprites.managers.SpriteManager;
import java.util.*;

/** World-owned presentation clock and bounded immutable trail history. GPU draws never advance it. */
public final class LivePresentationRuntime implements WorldSessionPolicyState, RewindSnapshottable<LivePresentationRuntime.State> {
    public static final int MAX_TRAIL_POSES = 4;
    public static final int MAX_TRAIL_TILES = 256;
    public record Pose(SpritePresentation.Frame frame, int cameraX, int cameraY) { }
    public record State(Object owner,long activePresentations, LogicalInputSnapshot input,
                        List<TransientMutatorPolicySource.Entry> policies, List<Pose> trails,
                        Set<Long> trailActivations, Object hud,
                        com.openggf.level.render.SceneBackgroundPreparation.State scenery,long sceneryActivation) {
        public State { policies=List.copyOf(policies);trails=List.copyOf(trails);trailActivations=Set.copyOf(trailActivations); }
    }
    private final WorldSession world;
    private final Object snapshotOwner=new Object();
    private boolean retired;
    private final HudPresentationRuntime hud;
    private final com.openggf.level.render.SceneBackgroundPreparation scenery=new com.openggf.level.render.SceneBackgroundPreparation();
    private long sceneryActivation;
    private SpriteManager roster;
    private com.openggf.graphics.GraphicsManager trailGraphics;
    private com.openggf.graphics.PresentationGraphicsAccess.TrailBank trailBank;
    private final IdentityHashMap<Object,Runnable> renderResources=new IdentityHashMap<>();
    private long activePresentations;
    private LogicalInputSnapshot input=LogicalInputSnapshot.neutral();
    private List<TransientMutatorPolicySource.Entry> policies=List.of();
    private final ArrayDeque<Pose> trails=new ArrayDeque<>();
    private Set<Long> trailActivations=Set.of();

    public LivePresentationRuntime(WorldSession world) {
        this.world=Objects.requireNonNull(world); hud=new HudPresentationRuntime(world);
    }
    public static LivePresentationRuntime from(WorldSession world) {
        return WorldSessionPolicyAccess.getService(world,LivePresentationRuntime.class);
    }
    public List<TransientMutatorPolicySource.Entry> scenePolicies() {
        return policies.stream().filter(e->e.policy() instanceof ScenePresentationPolicy).toList();
    }
    public List<TransientMutatorPolicySource.Entry> spritePolicies() {
        return policies.stream().filter(e->e.policy() instanceof SpritePresentationPolicy).toList();
    }
    /** Producer admission can precede the active-presentation observer; read the current lease set. */
    public boolean tags(SpritePresentation.Role role) {
        return !retired&&TransientMutatorPolicyAccess.entries(world).stream().anyMatch(e->e.remainingPresentations()>0&&e.policy() instanceof SpritePresentationPolicy p && targets(p.target(),role));
    }
    public LogicalInputSnapshot input() { return input; }
    public com.openggf.level.render.ScenePresentationPass.Input sceneInput() {
        var player = input.player1();
        int held = player.heldMask();
        return new com.openggf.level.render.ScenePresentationPass.Input(
                (held & (com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_LEFT
                        | com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT)) != 0,
                (held & (com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_UP
                        | com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_DOWN)) != 0,
                player.actionHeldMask() != 0);
    }
    public WorldSession world() { return world; }
    /** Structural render owners register their own exact teardown; callbacks are never snapshot gameplay values. */
    public void registerRenderResource(Object owner,Runnable cleanup) { renderResources.put(Objects.requireNonNull(owner),Objects.requireNonNull(cleanup)); }
    private void releaseRenderResources() {
        var cleanup=List.copyOf(renderResources.values());renderResources.clear();
        RuntimeException first=null;
        for(var operation:cleanup)try{operation.run();}catch(RuntimeException failure){if(first==null)first=failure;else first.addSuppressed(failure);}
        if(first!=null)throw first;
    }
    public long activePresentations() { return activePresentations; }
    public com.openggf.level.render.ZonePictureSource.Picture sceneryPicture() { return scenery.picture(); }
    public List<Pose> trails() { return List.copyOf(trails); }
    public int trailBank(com.openggf.graphics.GraphicsManager graphics) {
        if(trailGraphics!=null&&trailGraphics!=graphics)releaseTrailArt();
        trailGraphics=graphics;
        if(!com.openggf.graphics.PresentationGraphicsAccess.valid(trailBank,graphics,this))
            trailBank=com.openggf.graphics.PresentationGraphicsAccess.acquire(graphics,this);
        return com.openggf.graphics.PresentationGraphicsAccess.base(trailBank,graphics,this);
    }
    private void clearTrails() {
        trails.clear();
        releaseTrailArt();
    }
    /** Render failure may release host resources; the captured logical history remains unchanged. */
    public void releaseTrailArt() {
        com.openggf.graphics.PresentationGraphicsAccess.release(trailBank,this);trailBank=null;trailGraphics=null;
    }
    public SpritePresentation.Role playerRole(com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
        return roster!=null&&roster.getMainPlayable()==sprite?SpritePresentation.Role.LEADER:SpritePresentation.Role.TEAM;
    }
    public static boolean targets(SpritePresentationPolicy.Target target, SpritePresentation.Role role) {
        return switch(target) {
            case LEADER -> role==SpritePresentation.Role.LEADER;
            case ALL_TEAM -> role==SpritePresentation.Role.LEADER||role==SpritePresentation.Role.TEAM;
            case BADNIKS -> role==SpritePresentation.Role.BADNIK;
            case PICKUPS -> role==SpritePresentation.Role.PICKUP;
            case BADNIKS_AND_PICKUPS -> role==SpritePresentation.Role.BADNIK||role==SpritePresentation.Role.PICKUP;
        };
    }
    @Override public void onPresentation(GameplayModeContext context, LogicalInputSnapshot input, GameplayObservation.Phase phase) {
        if(context!=null&&context.getWorldSession()!=world)return;
        if(retired)return;
        hud.onPresentation(context,input,phase);
        policies=List.copyOf(TransientMutatorPolicyAccess.entries(world));
        if(scenePolicies().stream().noneMatch(entry->entry.remainingPresentations()>0))releaseRenderResources();
        var sceneryId=scenePolicies().stream().filter(entry->entry.remainingPresentations()>0
                &&entry.policy() instanceof ScenePresentationPolicy p&&p.mode()==ScenePresentationPolicy.Mode.SCENERY)
                .mapToLong(TransientMutatorPolicySource.Entry::activationId).reduce((first,last)->last).orElse(0);
        if(sceneryActivation!=sceneryId) { scenery.reset();sceneryActivation=sceneryId; }
        var ids=new HashSet<Long>();
        for(var entry:spritePolicies()) if(entry.remainingPresentations()>0&&((SpritePresentationPolicy)entry.policy()).mode()==SpritePresentationPolicy.Mode.TRAIL) ids.add(entry.activationId());
        if(!ids.equals(trailActivations)) clearTrails();
        trailActivations=Set.copyOf(ids);
        if(ids.isEmpty())clearTrails();
        // Removal is serviced on held/paused boundaries, but surviving presentation history and clocks freeze.
        if(phase!=GameplayObservation.Phase.ACTIVE)return;
        this.input=Objects.requireNonNullElse(input,LogicalInputSnapshot.neutral());
        activePresentations++;
        advanceScenery(context);
        if(ids.isEmpty())return;
        if(context!=null && context.getCamera()!=null && activePresentations%3==0) {
            remember(LevelSpritePresentation.published(context.getLevelManager()),context.getCamera().getXWithShake(),context.getCamera().getYWithShake());
        }
    }
    private void advanceScenery(GameplayModeContext context) {
        TransientMutatorPolicySource.Entry selected=null;
        for(var entry:scenePolicies())if(entry.remainingPresentations()>0&&entry.policy() instanceof ScenePresentationPolicy p
                &&p.mode()==ScenePresentationPolicy.Mode.SCENERY)selected=entry;
        if(selected==null) { scenery.reset();sceneryActivation=0;return; }
        if(sceneryActivation!=selected.activationId()) { scenery.reset();sceneryActivation=selected.activationId(); }
        if(context==null||context.getCamera()==null)return;
        var camera=context.getCamera();
        var graphics=com.openggf.game.session.EngineServices.current().graphics();
        int width=graphics==null?camera.getWidth():graphics.getProjectionWidth();
        try {
            scenery.advance(world,((ScenePresentationPolicy)selected.policy()).sceneryIndex(),camera.getXWithShake(),camera.getYWithShake(),
                    activePresentations,width,camera.getHeight());
        } catch(RuntimeException failure) {
            scenery.reset();
            TransientMutatorPolicyAccess.report(world,selected.activationId(),"Scenery unavailable: "+failure.getMessage());
        }
    }
    /** Snapshot only admitted body/appendage pixels. Transparent/masked rows and art generations remain exact. */
    public void remember(SpritePresentation.Frame frame,int cameraX,int cameraY) {
        var tiles=frame.tiles().stream().filter(t->t.subject().body()!=null && !t.subject().suppressed()
                && (t.subject().part()==SpritePresentation.Part.BODY||t.subject().part()==SpritePresentation.Part.APPENDAGE)
                && spritePolicies().stream().anyMatch(e->e.remainingPresentations()>0&&e.policy() instanceof SpritePresentationPolicy p
                && p.mode()==SpritePresentationPolicy.Mode.TRAIL&&targets(p.target(),t.subject().body().role())))
                .limit(MAX_TRAIL_TILES).toList();
        if(tiles.isEmpty()) return;
        var versions=new HashMap<Integer,SpritePresentation.PatternVersion>();
        for(var tile:tiles) {
            var version=frame.patternVersions().get(tile.patternId());
            // Standard native player producers bind every referenced DPLC slot. Never retain a mutable address alone.
            if(version==null) return;
            versions.put(tile.patternId(),version);
        }
        trails.addLast(new Pose(new SpritePresentation.Frame(tiles,List.of(),versions),cameraX,cameraY));
        while(trails.size()>MAX_TRAIL_POSES) trails.removeFirst();
    }
    @Override public <T>T getService(Class<T> type) { return type==LivePresentationRuntime.class?type.cast(this):hud.getService(type); }
    @Override public RewindSnapshottable<?> rewindAdapter() { return this; }
    @Override public String key() { return "live-presentation"; }
    @Override public State capture() { return new State(snapshotOwner,activePresentations,input,policies,List.copyOf(trails),trailActivations,hud.rewindAdapter().capture(),scenery.capture(),sceneryActivation); }
    @SuppressWarnings({"rawtypes","unchecked"}) @Override public void restore(State state) {
        if(retired)throw new IllegalStateException("Presentation world retired");
        if(state.owner()!=snapshotOwner)throw new IllegalArgumentException("Presentation snapshot belongs to another world");
        activePresentations=state.activePresentations();input=state.input();policies=state.policies();
        trails.clear();trails.addAll(state.trails());trailActivations=state.trailActivations();
        ((RewindSnapshottable)hud.rewindAdapter()).restore(state.hud());
        scenery.restore(world,state.scenery());sceneryActivation=state.sceneryActivation();
    }
    private void clear() {
        policies=List.of();clearTrails();trailActivations=Set.of();input=LogicalInputSnapshot.neutral();
        scenery.reset();sceneryActivation=0;hud.resetForMissingSnapshot();releaseRenderResources();
    }
    @Override public void beforeAssembly(LevelLoadCause cause) {
        if (cause == LevelLoadCause.DECODE_ONLY || cause == LevelLoadCause.PREVIEW) return;
        clear();hud.beforeAssembly(cause);
    }
    public void leaveOrdinaryGameplay(GameplayObservation.Phase phase) {
        if(phase==GameplayObservation.Phase.SETUP||phase==GameplayObservation.Phase.STAGE||phase==GameplayObservation.Phase.TRANSITION||phase==GameplayObservation.Phase.CLOSED) {
            clear();hud.leaveOrdinaryGameplay(phase);
        }
    }
    @Override public void bindRoster(SpriteManager sprites) { roster=sprites;hud.bindRoster(sprites); }
    @Override public void failedAssembly(LevelLoadCause cause) {
        if (cause == LevelLoadCause.DECODE_ONLY || cause == LevelLoadCause.PREVIEW) return;
        clear();hud.failedAssembly(cause);
    }
    @Override public void closeScreens() { clear();hud.closeScreens(); }
    @Override public void resetForMissingSnapshot() { clear();hud.resetForMissingSnapshot(); }
    @Override public void retire() {
        retired=true;
        try { clear(); } finally { roster=null;hud.retire(); }
    }
}
