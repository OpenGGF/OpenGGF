package com.openggf.mods.mutators;

import com.openggf.game.LevelLoadCause;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.managers.SpriteManagerInternalAccess;
import com.openggf.sprites.playable.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** World-owned policy/preferences lifetime, independent of disposable gameplay contexts. */
final class MutatorWorldState implements WorldSessionPolicyState, RewindSnapshottable<MutatorWorldState.Snapshot> {
    // Identity allocation only. No configuration or gameplay values live in process statics.
    private static final AtomicLong GENERATIONS = new AtomicLong();
    final MutatorSessionState state;
    private final long generation;
    private final com.openggf.mods.code.ModFaultBoundary faults;
    private final TemporaryMutatorState temporary;
    private final com.openggf.mods.code.GameplayObserverRegistry observers;
    private long visitGeneration, nativeTicks;
    private com.openggf.game.LevelLoadCause loadCause = LevelLoadCause.FULL_LEVEL_ASSEMBLY;
    private com.openggf.game.GameplayObservation observation;
    private com.openggf.game.session.GameplayModeContext gameplayContext;
    private boolean retired;
    private java.util.Optional<com.openggf.mods.scene.SceneRomArt> nativeArt;
    private java.util.function.Consumer<com.openggf.audio.GameSound> observerSounds=ignored -> { };
    private final WorldSession world;
    private final MutatorSupportProfile support;
    private final MutatorPreferenceStore store;
    private final com.openggf.game.mutators.LevelMutatorRuntime entryRuntime;
    private final com.openggf.game.mutators.GameplayMutatorPacing pacing;
    private MutatorPreferences preferences;
    private boolean launched;
    private final java.util.Set<com.openggf.mods.mutators.MutatorConfigurationScreen> screens =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    String saveError = "";
    String admissionError = "";

    MutatorWorldState(WorldSession world, MutatorCatalog catalog, MutatorSupportProfile support) {
        this.world = world;
        faults=catalog.faults();
        this.support = support;
        String profile = world.rootGameModule().getGameId().code();
        var preferenceRoot = com.openggf.game.save.SavePaths.root().toAbsolutePath().normalize().resolve("mutators");
        try { provisionPreferenceRoot(preferenceRoot); }
        catch (java.io.IOException failure) { saveError = failure.getMessage(); }
        store = new MutatorPreferenceStore(preferenceRoot, profile);
        var loaded = store.load();
        preferences = loaded.preferences();
        if (!loaded.successful()) saveError = loaded.error();
        Map<String, MutatorSessionState.Configuration> requested = Map.of();
        try { requested = preferences.forSession(catalog.definitions()); }
        catch (IllegalArgumentException invalid) { saveError = invalid.getMessage(); }
        generation = GENERATIONS.incrementAndGet();
        observers = world.resolvedGameModule().getGameService(com.openggf.mods.code.GameplayObserverRegistry.class);
        state = new MutatorSessionState(generation, catalog.definitions(),
                support == null ? Set.of() : Set.copyOf(support.capabilities(0, 0)),
                catalog.faults(), catalog.faults()::isOwnerAvailable, requested);
        temporary = new TemporaryMutatorState(generation,catalog.definitions(),catalog.faults(),
                () -> state.effective().policies().keySet(),this::nativeArt);
        entryRuntime = new com.openggf.game.mutators.LevelMutatorRuntime(world);
        pacing = new com.openggf.game.mutators.GameplayMutatorPacing(() ->
                state.isClosed() ? com.openggf.game.mutators.GameplayMutatorPolicy.STOCK
                        : composed(state.effectiveBeforeNextForwardTick()).gameplayPolicy(availableCapabilities()));
    }

    @Override public void beforeAssembly(LevelLoadCause cause) {
        loadCause=cause;
        if(cause!=LevelLoadCause.DECODE_ONLY && cause!=LevelLoadCause.PREVIEW) {
            try {
                if(observation!=null) {
                    observation=withPhase(com.openggf.game.GameplayObservation.Phase.TRANSITION);
                    dispatch((observer,port) -> observer.onBoundary(observation,port));
                }
            } finally { temporary.clear();visitGeneration++;nativeTicks=0; }
        }
        MutatorSessionState.LoadCause mapped = mapped(cause);
        if (mapped == null || !mapped.qualifies()) return;
        // A fresh native assembly cannot inherit fractional time or input edges.
        // Entry reservations are cleared by the native load admission owner, which
        // preserves synchronous stage publication and stage-result ownership.
        pacing.reset();
        if (!launched) {
            var admission = prepareLaunch();
            if (!admission.accepted()) {
                // Direct/automatic launch has no menu to refuse in. Keep the valid
                // defaults; this new session has consumed its launch opportunity.
                launched = true; admissionError = "Launch kept prior settings: " + admission.message(); return;
            }
        }
        var admission = state.boundary(MutatorScope.LOAD, mapped);
        admissionError = admission.accepted() ? "" : "Reload kept prior settings: " + admission.message();
    }

    @Override public void beforeSpecialStageForwardTick() {
        if (!state.isClosed()) state.onForwardTick();
    }

    @Override public void bindRoster(SpriteManager sprites) {
        SpriteManagerInternalAccess.bindMutatorPolicies(sprites, new PlayableMutatorPolicySource() {
            @Override public com.openggf.game.mutators.TransientMutatorPolicySource transientPolicies() { return temporary; }
            @Override public void beforeForwardTick() { state.onForwardTick(); }
            @Override public PlayableMutatorPolicy policyFor(AbstractPlayableSprite sprite) {
                if (support == null || state.isClosed()) return PlayableMutatorPolicy.STOCK;
                boolean leader = sprites.getMainPlayable() == sprite;
                Set<MutatorCapability> available = Set.copyOf(support.capabilities(world.getCurrentZone(), world.getCurrentAct()));
                var effective = composed(state.effective());
                int gravity = available.contains(MutatorCapability.DRY_SONIC_GRAVITY)
                        && support.supportsPlayer(MutatorCapability.DRY_SONIC_GRAVITY, sprite.characterKey().persisted(), leader)
                        ? effective.gravityPercent() : 100;
                boolean body = false, appendage = false, effects = false;
                if (available.contains(MutatorCapability.PLAYER_STEALTH)
                        && support.supportsPlayer(MutatorCapability.PLAYER_STEALTH, sprite.characterKey().persisted(), leader)) {
                    for (var policy : effective.stealthPolicies()) {
                        if (leader || policy.target() == MutatorPolicy.Target.ALL_TEAM) {
                            body |= policy.hideBody();
                            appendage |= policy.hideAppendage();
                            effects |= policy.hideAttachedEffects();
                        }
                    }
                }
                int head = available.contains(MutatorCapability.BIG_HEAD)
                        && support.supportsPlayer(MutatorCapability.BIG_HEAD, sprite.characterKey().persisted(), leader)
                        // Native ring inventory is ordinary live/rewound state; it is read, never cached.
                        ? effective.headScalePercent(leader, sprite.getRingCount()) : 100;
                return gravity == 100 && !body && !appendage && !effects && head == 100 ? PlayableMutatorPolicy.STOCK
                        : new PlayableMutatorPolicy(gravity, body, appendage, effects, head);
            }
        });
    }

    MutatorSessionState.Admission prepareLaunch() {
        if (launched) return new MutatorSessionState.Admission(true, state.effective().revision(), "Prepared", state.pending());
        var admission = state.boundary(MutatorScope.LAUNCH);
        if (admission.accepted()) launched = true;
        return admission;
    }
    boolean supportedCell() {
        return support != null && !support.capabilities(world.getCurrentZone(), world.getCurrentAct()).isEmpty();
    }
    void ownScreen(com.openggf.mods.mutators.MutatorConfigurationScreen screen) { screens.add(screen); }
    @Override public void closeScreens() {
        temporary.clear();
        screens.forEach(com.openggf.mods.mutators.MutatorConfigurationScreen::close);
    }

    /** Host-selected settings directory, created without following existing ancestor symlinks.
     * Publication separately pins directory identity and uses SecureDirectoryStream.
     * This creates empty directories only; creator code never supplies this path. */
    private static void provisionPreferenceRoot(java.nio.file.Path root) throws java.io.IOException {
        var pending = new java.util.ArrayDeque<java.nio.file.Path>();
        for (var path=root; path!=null; path=path.getParent()) {
            if (java.nio.file.Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                if (java.nio.file.Files.isSymbolicLink(path) || !java.nio.file.Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    throw new java.io.IOException("Settings path must contain only directories, without symlinks");
            } else pending.addFirst(path);
        }
        for (var path:pending) {
            try { java.nio.file.Files.createDirectory(path); }
            catch (java.nio.file.FileAlreadyExistsException raced) {
                if (java.nio.file.Files.isSymbolicLink(path) || !java.nio.file.Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw raced;
            }
        }
    }

    boolean save() {
        var draft = preferences.withRequested(state);
        var result = store.save(draft);
        if (result instanceof ModStateSaveResult.Failed failed) { saveError = failed.message(); return false; }
        preferences = draft; saveError = ""; return true;
    }

    private static MutatorSessionState.LoadCause mapped(LevelLoadCause cause) {
        return switch (cause) {
            case DECODE_ONLY -> null;
            case FULL_LEVEL_ASSEMBLY -> MutatorSessionState.LoadCause.FULL_LEVEL_ASSEMBLY;
            case FULL_RESTART -> MutatorSessionState.LoadCause.FULL_RESTART;
            case FULL_DEATH_RELOAD -> MutatorSessionState.LoadCause.FULL_DEATH_RELOAD;
            case STAGE_RETURN_FULL_ASSEMBLY -> MutatorSessionState.LoadCause.STAGE_RETURN_FULL_ASSEMBLY;
            case PREVIEW -> MutatorSessionState.LoadCause.PREVIEW;
            case CHECKPOINT_RESTORE -> MutatorSessionState.LoadCause.CHECKPOINT_RESTORE;
            case EDITOR_SWAP -> MutatorSessionState.LoadCause.EDITOR_SWAP;
            case SEAMLESS_HANDOFF -> MutatorSessionState.LoadCause.SEAMLESS_HANDOFF;
        };
    }
    @Override public <T> T getService(Class<T> type) {
        if (type == com.openggf.game.mutators.ObjectHeadScaleSource.class) {
            return type.cast((com.openggf.game.mutators.ObjectHeadScaleSource) (role, rings) ->
                    state.isClosed() || !availableCapabilities().contains(MutatorCapability.BIG_HEAD)
                            || role == com.openggf.game.mutators.ObjectHeadRole.NONE ? 100
                            : composed(state.effective()).headScalePercent(role == com.openggf.game.mutators.ObjectHeadRole.BADNIK
                                    ? MutatorPolicy.HeadTarget.BADNIKS : MutatorPolicy.HeadTarget.BOSSES, rings));
        }
        if (type == com.openggf.game.mutators.TransientMutatorPolicySource.class) return type.cast(temporary);
        if (type == MutatorWorldState.class) return type.cast(this);
        if (type == com.openggf.game.mutators.LevelMutatorRuntime.class) return type.cast(entryRuntime);
        if (type == com.openggf.game.mutators.GameplayMutatorPacing.class) return type.cast(pacing);
        if (type == com.openggf.game.mutators.LevelMutatorPolicySource.class) {
            return type.cast((com.openggf.game.mutators.LevelMutatorPolicySource)
                    () -> state.isClosed() ? com.openggf.game.mutators.LevelMutatorPolicy.STOCK
                            : composed(state.effective()).levelPolicy(availableCapabilities()));
        }
        if (type == com.openggf.game.mutators.GameplayMutatorPolicySource.class) {
            return type.cast((com.openggf.game.mutators.GameplayMutatorPolicySource)
                    () -> state.isClosed() ? com.openggf.game.mutators.GameplayMutatorPolicy.STOCK
                            : composed(state.effective()).gameplayPolicy(availableCapabilities()));
        }
        return null;
    }
    private Set<MutatorCapability> availableCapabilities() {
        return support == null || state.isClosed() ? Set.of()
                : Set.copyOf(support.capabilities(world.getCurrentZone(), world.getCurrentAct()));
    }
    private java.util.Optional<com.openggf.mods.scene.SceneRomArt> nativeArt() {
        if(nativeArt==null)nativeArt=world.getDataSource().rom().map(rom ->
                com.openggf.mods.scene.host.SceneRomArtFactory.forModule(world.rootGameModule(),rom));
        return nativeArt;
    }
    private MutatorSessionState.Effective composed(MutatorSessionState.Effective base) {
        var policies=new java.util.LinkedHashMap<>(base.policies());
        var grouped=new java.util.LinkedHashMap<String,java.util.List<MutatorPolicy>>();
        for(var entry:temporary.entries())grouped.computeIfAbsent("temporary/"+entry.activationId(),ignored -> new java.util.ArrayList<>()).add(entry.policy());
        policies.putAll(grouped);
        return new MutatorSessionState.Effective(base.revision(),policies);
    }
    @Override public boolean holdsGameplay() {
        return temporary.entries().stream().anyMatch(e -> e.policy() instanceof GameplayHoldPolicy);
    }
    @Override public void onPresentation(com.openggf.game.session.GameplayModeContext context,
            com.openggf.control.LogicalInputSnapshot input,com.openggf.game.GameplayObservation.Phase phase) {
        if(retired)return; gameplayContext=context;
        observerSounds=context::playObservedSfx;
        admitPhaseBoundary(phase);
        if(phase==com.openggf.game.GameplayObservation.Phase.ACTIVE || phase==com.openggf.game.GameplayObservation.Phase.HELD)
            temporary.advancePresentation(phase==com.openggf.game.GameplayObservation.Phase.HELD);
        observation=com.openggf.game.session.GameplayObservationFactory.capture(world,generation,visitGeneration,temporary.clock(),nativeTicks,
                context,null,input,phase,loadCause);
        dispatch((observer,port) -> observer.onPresentation(observation,port));
    }
    @Override public void onMenuPresentation(com.openggf.control.LogicalInputSnapshot input,int width,int height,
            java.util.function.Consumer<com.openggf.audio.GameSound> sounds) {
        if(retired)return;observerSounds=sounds;
        observation=new com.openggf.game.GameplayObservation(generation,visitGeneration,temporary.clock(),nativeTicks,
                com.openggf.game.GameplayObservation.Phase.SETUP,world.getCurrentZone(),world.getCurrentAct(),width,height,
                java.util.Optional.empty(),java.util.List.of(),input,loadCause);
        dispatch((observer,port) -> observer.onPresentation(observation,port));
    }
    @Override public void observeTick(com.openggf.LevelFrameContext context,com.openggf.control.LogicalInputSnapshot input) {
        if(retired)return;
        var leader=context.spriteManager()==null?null:context.spriteManager().getMainPlayable();
        var phase=leader!=null && leader.getDead() || context.levelManager()!=null && context.levelManager().isLevelInactiveForTransition()
                ? com.openggf.game.GameplayObservation.Phase.TRANSITION : com.openggf.game.GameplayObservation.Phase.ACTIVE;
        admitPhaseBoundary(phase);
        observation=com.openggf.game.session.GameplayObservationFactory.capture(world,generation,visitGeneration,temporary.clock(),nativeTicks,
                gameplayContext,context,input,phase,loadCause);
        dispatch((observer,port) -> observer.onTick(observation,port));
    }
    private void admitPhaseBoundary(com.openggf.game.GameplayObservation.Phase phase) {
        if((phase==com.openggf.game.GameplayObservation.Phase.SETUP || phase==com.openggf.game.GameplayObservation.Phase.STAGE
                || phase==com.openggf.game.GameplayObservation.Phase.TRANSITION)
                && (observation==null || observation.phase()!=phase || !temporary.entries().isEmpty()))
            com.openggf.game.session.WorldSessionPolicyAccess.leaveOrdinaryGameplay(world,phase);
    }
    @Override public void leaveOrdinaryGameplay(com.openggf.game.GameplayObservation.Phase phase) {
        try {
            if(observation!=null) {
                observation=withPhase(phase);
                if(!com.openggf.game.session.WorldSessionPolicyAccess.replaying(world))
                    dispatch((observer,port) -> observer.onBoundary(observation,port));
            }
        } finally { temporary.clear(); }
    }
    @Override public void afterNativeTick(com.openggf.LevelFrameContext context,com.openggf.control.LogicalInputSnapshot input) { nativeTicks++; }
    @Override public void drawOverlay(com.openggf.mods.ui.PixelCanvas canvas) {
        if(observers!=null && observation!=null && !retired)for(var entry:observers.entries())
            if(temporary.ownerAvailable(entry.owner()))entry.observer().drawOverlay(canvas,observation);
    }
    private void dispatch(java.util.function.BiConsumer<com.openggf.game.GameplayObserver,TemporaryMutators> callback) {
        if(observers==null)return;
        for(var entry:observers.entries()) {
            if(!temporary.ownerAvailable(entry.owner()))continue;
            try(var port=temporary.open(entry.owner(),availableCapabilities(),observerSounds,observation!=null && (observation.phase()==com.openggf.game.GameplayObservation.Phase.ACTIVE
                    || observation.phase()==com.openggf.game.GameplayObservation.Phase.HELD))) { callback.accept(entry.observer(),port); }
        }
    }
    private com.openggf.game.GameplayObservation withPhase(com.openggf.game.GameplayObservation.Phase phase) {
        var o=observation;
        return new com.openggf.game.GameplayObservation(generation,visitGeneration,temporary.clock(),nativeTicks,phase,o.zone(),o.act(),
                o.viewportWidth(),o.viewportHeight(),o.leader(),o.team(),o.input(),loadCause);
    }
    @Override public RewindSnapshottable<?> rewindAdapter() { return this; }
    @Override public void failedAssembly(LevelLoadCause cause) {
        if (cause != LevelLoadCause.DECODE_ONLY && cause != LevelLoadCause.PREVIEW) {
            temporary.retire(); entryRuntime.retire(); pacing.reset(); state.close();
        }
    }
    @Override public void retire() {
        if(retired)return; retired=true;
        var availableObservers=observers==null?java.util.List.<com.openggf.mods.code.GameplayObserverRegistry.Entry>of():
                observers.entries().stream().filter(e -> faults.isOwnerAvailable(e.owner())).toList();
        temporary.retire(); entryRuntime.retire(); pacing.reset(); state.close();
        RuntimeException first=null;
        try { closeScreens(); } catch(RuntimeException failure) { first=failure; }
        for(var entry:availableObservers) {
            try { entry.observer().close(); } catch(RuntimeException failure) {
                if(first==null)first=failure;else first.addSuppressed(failure);
            }
        }
        gameplayContext=null;
        if(first!=null)throw first;
    }
    @Override public String key() { return "mutators"; }
    @Override public Snapshot capture() { return new Snapshot(state.snapshot(), entryRuntime.capture(), pacing.capture(),temporary.capture(),visitGeneration,nativeTicks,observation,loadCause); }
    @Override public void restore(Snapshot snapshot) {
        // The registered world adapter precedes player/object reconstruction. Saved
        // preferences and host owner quarantine remain outside this historical state.
        state.restore(snapshot.policies());
        entryRuntime.restore(snapshot.entries());
        pacing.restore(snapshot.pacing());
        temporary.restore(snapshot.temporary());
        visitGeneration=snapshot.visitGeneration(); nativeTicks=snapshot.nativeTicks(); observation=snapshot.observation();loadCause=snapshot.loadCause();
    }
    record Snapshot(MutatorSessionState.Snapshot policies,
                    com.openggf.game.mutators.LevelMutatorRuntime.Snapshot entries,
                    com.openggf.game.mutators.GameplayMutatorPacing.Snapshot pacing,
                    TemporaryMutatorState.Snapshot temporary,long visitGeneration,long nativeTicks,
                    com.openggf.game.GameplayObservation observation,LevelLoadCause loadCause) {
        Snapshot {
            java.util.Objects.requireNonNull(policies);
            java.util.Objects.requireNonNull(entries);
            java.util.Objects.requireNonNull(pacing);
        }
    }
}
