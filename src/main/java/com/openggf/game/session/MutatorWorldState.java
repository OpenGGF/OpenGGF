package com.openggf.game.session;

import com.openggf.game.LevelLoadCause;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.mods.mutators.*;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.managers.SpriteManagerInternalAccess;
import com.openggf.sprites.playable.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** World-owned policy/preferences lifetime, independent of disposable gameplay contexts. */
final class MutatorWorldState implements RewindSnapshottable<MutatorSessionState.Snapshot> {
    // Identity allocation only. No configuration or gameplay values live in process statics.
    private static final AtomicLong GENERATIONS = new AtomicLong();
    final MutatorSessionState state;
    private final WorldSession world;
    private final MutatorSupportProfile support;
    private final MutatorPreferenceStore store;
    private MutatorPreferences preferences;
    private boolean launched;
    private final java.util.Set<com.openggf.mods.mutators.MutatorConfigurationScreen> screens =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    String saveError = "";
    String admissionError = "";

    MutatorWorldState(WorldSession world, MutatorCatalog catalog, MutatorSupportProfile support) {
        this.world = world;
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
        state = new MutatorSessionState(GENERATIONS.incrementAndGet(), catalog.definitions(),
                support == null ? Set.of() : Set.copyOf(support.capabilities(0, 0)),
                catalog.faults(), catalog.faults()::isOwnerAvailable, requested);
    }

    void beforeAssembly(LevelLoadCause cause) {
        MutatorSessionState.LoadCause mapped = mapped(cause);
        if (mapped == null || !mapped.qualifies()) return;
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

    void bind(SpriteManager sprites) {
        SpriteManagerInternalAccess.bindMutatorPolicies(sprites, new PlayableMutatorPolicySource() {
            @Override public void beforeForwardTick() { state.onForwardTick(); }
            @Override public PlayableMutatorPolicy policyFor(AbstractPlayableSprite sprite) {
                if (support == null || state.isClosed()) return PlayableMutatorPolicy.STOCK;
                boolean leader = sprites.getMainPlayable() == sprite;
                if (!support.supportsPlayer(sprite.getCode(), leader)) return PlayableMutatorPolicy.STOCK;
                Set<MutatorCapability> available = Set.copyOf(support.capabilities(world.getCurrentZone(), world.getCurrentAct()));
                var effective = state.effective();
                int gravity = available.contains(MutatorCapability.DRY_SONIC_GRAVITY) ? effective.gravityPercent() : 100;
                boolean body = false, appendage = false, effects = false;
                if (available.contains(MutatorCapability.PLAYER_STEALTH)) {
                    for (var policy : effective.stealthPolicies()) {
                        if (leader || policy.target() == MutatorPolicy.Target.ALL_TEAM) {
                            body |= policy.hideBody();
                            appendage |= policy.hideAppendage();
                            effects |= policy.hideAttachedEffects();
                        }
                    }
                }
                return gravity == 100 && !body && !appendage && !effects ? PlayableMutatorPolicy.STOCK
                        : new PlayableMutatorPolicy(gravity, body, appendage, effects);
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
    void closeScreens() { screens.forEach(com.openggf.mods.mutators.MutatorConfigurationScreen::close); }

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
    @Override public String key() { return "mutators"; }
    @Override public MutatorSessionState.Snapshot capture() { return state.snapshot(); }
    @Override public void restore(MutatorSessionState.Snapshot snapshot) { state.restore(snapshot); }
}
