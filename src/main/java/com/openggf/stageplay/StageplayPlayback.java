package com.openggf.stageplay;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameServiceBundle;
import com.openggf.game.GameServices;
import com.openggf.game.ModApi;
import com.openggf.game.mode.*;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.ResourceLoader;
import com.openggf.level.resources.AuthoredLevelResources;
import com.openggf.mods.code.ModContext;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.mods.scene.host.SceneSurface;
import com.openggf.stageplay.runtime.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

/**
 * Reusable staged cutscene controller. Native world clocks are held while its independent
 * performance runs. The default cast is detached; the optional binding explicitly borrows
 * the main player and camera. Install only in modules without another frame controller.
 */
@ModApi
public final class StageplayPlayback implements GameplayFrameController, RewindSnapshottable<StageplayPlayback.State>, StageplayControl {
    private final StageplayDocument document;
    private final StageplayNativeBinding binding;
    private final StageplayNativeBinding.FinishMode finishMode;
    private final HostFactory hostFactory;
    private Host host;
    private Status status = Status.IDLE;
    private int frame = -1;
    private boolean skip, cancel;
    private long visit = -1;
    private RuntimeException deferredFailure;
    private boolean closed;

    public StageplayPlayback(StageplayDocument document) {
        this(document, "", StageplayNativeBinding.FinishMode.RESTORE_ENTRY);
    }
    public StageplayPlayback(StageplayDocument document, String boundActor,
                             StageplayNativeBinding.FinishMode finishMode) {
        this(document, boundActor, finishMode, Map.of(), Map.of());
    }
    private StageplayPlayback(StageplayDocument document, String boundActor,
                              StageplayNativeBinding.FinishMode finishMode, Map<String, byte[]> assets,
                              Map<StageplayDocument.SetSource, com.openggf.level.Level> sets) {
        this(document, boundActor, finishMode, () -> new LiveHost(document, assets, sets));
    }
    StageplayPlayback(StageplayDocument document, String boundActor, StageplayNativeBinding.FinishMode finishMode,
                      HostFactory hostFactory) {
        this.document = Objects.requireNonNull(document); this.finishMode = Objects.requireNonNull(finishMode);
        this.hostFactory = Objects.requireNonNull(hostFactory);
        Objects.requireNonNull(boundActor);
        binding = boundActor.isEmpty() ? null : new StageplayNativeBinding(boundActor);
        validateNativeDocument(document, boundActor);
    }

    private static void validateNativeDocument(StageplayDocument document, String boundActor) {
        Objects.requireNonNull(boundActor, "boundActor");
        if (boundActor.isEmpty()) return;
        if (document.set().worldPolicy() == StageplayDocument.WorldPolicy.LIVE
                || document.sceneChanges().stream().anyMatch(c -> c.set().worldPolicy() == StageplayDocument.WorldPolicy.LIVE))
            throw new IllegalArgumentException("LIVE scenes own native players in their isolated world; active-player binding requires terrain-only shots");
        var actor = document.actors().stream().filter(value -> value.id().equals(boundActor)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Bound actor is missing"));
        if (!document.set().levelProject().isEmpty()
                || document.sceneChanges().stream().anyMatch(change -> !change.set().equals(document.set())))
            throw new IllegalArgumentException("Native player binding must stay in its native set; authored or different sets require detached actors");
        if (!actor.gameId().equals(document.set().gameId()) || actor.keys().stream().anyMatch(key -> key.scale() != 1))
            throw new IllegalArgumentException("Native player binding requires the set's matching game and unit actor scale");
        for (var key : actor.keys()) { requireNativeCoordinate(key.x()); requireNativeCoordinate(key.y()); }
        for (var key : document.camera()) { requireNativeCoordinate(key.x()); requireNativeCoordinate(key.y()); }
    }

    private static void requireNativeCoordinate(double value) {
        if (value < Short.MIN_VALUE || value > 0xffff)
            throw new IllegalArgumentException("Bound actor/camera coordinate is outside native 16-bit space");
    }

    private void validateNativeCourse(CourseControl course) {
        if (binding == null) return;
        var services = course.objectServices();
        var source = document.set();
        var actor = document.actors().stream().filter(value -> value.id().equals(binding.capture().actorId())).findFirst().orElseThrow();
        if (services.levelManager().getCurrentZone() != source.zone()
                || services.levelManager().getCurrentAct() != source.act()
                || !source.gameId().equals(services.gameModule().getGameCode())
                || !actor.character().equals(course.playerState().character()))
            throw new IllegalArgumentException("Native player binding requires the current native game, zone, act and character");
    }

    /** Loads the immutable document and owned media assets during the registration transaction. */
    public static void register(ModContext context, String localKey, String sceneAsset) throws IOException {
        register(context, localKey, sceneAsset, "", StageplayNativeBinding.FinishMode.RESTORE_ENTRY);
    }
    public static void register(ModContext context, String localKey, String sceneAsset, String boundActor,
                                StageplayNativeBinding.FinishMode finishMode) throws IOException {
        register(context, localKey, sceneAsset, boundActor, finishMode, false);
    }
    /** Loads the same document and begins at the first ready gameplay frame of each session. */
    public static void registerOnEntry(ModContext context, String localKey, String sceneAsset) throws IOException {
        register(context, localKey, sceneAsset, "", StageplayNativeBinding.FinishMode.RESTORE_ENTRY, true);
    }
    public static void registerOnEntry(ModContext context, String localKey, String sceneAsset, String boundActor,
                                       StageplayNativeBinding.FinishMode finishMode) throws IOException {
        register(context, localKey, sceneAsset, boundActor, finishMode, true);
    }
    private static void register(ModContext context, String localKey, String sceneAsset, String boundActor,
                                 StageplayNativeBinding.FinishMode finishMode, boolean onEntry) throws IOException {
        var root = context.modAssets();
        var loader = ResourceLoader.forModAssetsOnly();
        var document = StageplayStore.decode(loader.loadSingle(LoadOp.modAssetBase(root, sceneAsset)));
        validateNativeDocument(document, boundActor);
        String prefix = sceneAsset.contains("/") ? sceneAsset.substring(0, sceneAsset.lastIndexOf('/') + 1) : "";
        var sources = new LinkedHashSet<StageplayDocument.SetSource>();
        sources.add(document.set());
        document.sceneChanges().forEach(change -> sources.add(change.set()));
        var projects = new HashMap<String, com.openggf.level.resources.AuthoredLevelDecoder.Decoded>();
        var projectBytes = new HashMap<String, byte[]>();
        var sets = new HashMap<StageplayDocument.SetSource, com.openggf.level.Level>();
        long bytes = 0;
        for (var source : sources) if (!source.levelProject().isEmpty()) {
            var project = projects.get(source.levelProject());
            if (project == null) {
                byte[] encoded = loader.loadSingle(LoadOp.modAssetBase(root, prefix + source.levelProject()));
                bytes += encoded.length;
                if (bytes > 64L * 1024 * 1024) throw new IOException("Stageplay assets exceed the 64 MiB registration budget");
                project = AuthoredLevelResources.decode("ogfproject", encoded);
                projects.put(source.levelProject(), project);
                projectBytes.put(source.levelProject(), encoded);
            }
            if (!project.gameId().equals(source.gameId()))
                throw new IOException("Authored level and scene set use different games: " + source.levelProject());
            sets.put(source, project.level());
        }
        final var preparedSets = Map.copyOf(sets);
        var assets = new HashMap<String, byte[]>();
        var paths = new LinkedHashSet<String>();
        for (var cue : document.audio()) if (cue.kind() == StageplayDocument.AudioKind.VOICE) paths.add(cue.asset());
        paths.addAll(StageplayAssets.imagePaths(document));
        if (paths.size() > StageplayAssets.MAX_ASSETS) throw new IOException("Stageplay assets exceed registration count budget");
        for (String path : paths) {
            byte[] encoded = projectBytes.get(path);
            if (encoded == null) {
                encoded = loader.loadSingle(LoadOp.modAssetBase(root, prefix + path));
                bytes += encoded.length;
            }
            if (encoded.length > StageplayAssets.MAX_ENCODED_BYTES || bytes > StageplayAssets.MAX_TOTAL_BYTES)
                throw new IOException("Stageplay assets exceed registration byte budget");
            assets.put(path, encoded);
        }
        var prepared = Map.copyOf(assets);
        // Validate decoration dimensions, slicing and layout before publishing any service.
        // This pass needs neither the native world nor a loaded ROM.
        try (var presentation = new StageplayResources(game -> null, List.of())) {
            presentation.setAssetReader(path -> preparedAsset(prepared, path));
            presentation.prepareDialogue(document);
        } catch (UncheckedIOException failure) { throw failure.getCause(); }
        context.registerServiceBundle(localKey, () -> {
            var player = new StageplayPlayback(document, boundActor, finishMode, prepared, preparedSets);
            if (onEntry) player.play();
            return GameServiceBundle.builder().frameController("playback", player).service(StageplayControl.class, player).build();
        });
    }

    private static byte[] preparedAsset(Map<String, byte[]> assets, String path) throws IOException {
        byte[] bytes = assets.get(path);
        if (bytes == null) throw new IOException("Asset was not loaded in the mod registration transaction: " + path);
        return bytes;
    }

    @Override public void play() {
        requireOpen();
        if (status == Status.REQUESTED || status == Status.PLAYING) return;
        status = Status.REQUESTED; frame = -1; skip = false; cancel = false;
    }
    @Override public void skip() { requireOpen(); if (active()) skip = true; }
    @Override public void cancel() { requireOpen(); if (active()) cancel = true; }
    @Override public Status status() { return status; }
    @Override public int frame() { return frame; }
    private boolean active() { return status == Status.REQUESTED || status == Status.PLAYING; }
    private void requireOpen() { if (closed) throw new IllegalStateException("Stageplay playback is closed"); }

    @Override public boolean requiresExclusiveControl() { return true; }
    @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) {
        return beforeFrame(course, input) != GameplayFrameDisposition.HOLD;
    }
    @Override public GameplayFrameDisposition beforeFrame(CourseControl course, LogicalInputSnapshot input) {
        requireOpen();
        try {
            long currentVisit = course.objectServices().levelManager().getCompletedProductionLoadGeneration();
            if (visit >= 0 && visit != currentVisit) {
                if (binding != null) binding.abandon();
                if (host != null) { host.abandon(); host = null; }
                status = active() ? Status.CANCELLED : status; frame = -1; skip = false; cancel = false;
            }
            visit = currentVisit;
            if (deferredFailure != null) { var failure = deferredFailure; deferredFailure = null; throw failure; }
            if (!active()) return GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
            if (cancel) { finish(course, Status.CANCELLED); return GameplayFrameDisposition.HOLD; }
            if (status == Status.REQUESTED) {
                if (!course.presentationReady()) return GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
                validateNativeCourse(course);
                if (host == null) host = hostFactory.create();
                host.prepare(); host.seek(-1);
                if (binding != null) binding.captureEntry(course);
                status = Status.PLAYING; frame = -1;
            }
            if (skip) {
                frame = document.durationFrames() - 1;
                applyBinding(course, host.resolveFrame(StageplayTimeline.sample(document, frame)));
                finish(course, Status.SKIPPED); return GameplayFrameDisposition.HOLD;
            }
            if (frame + 1 == document.durationFrames()) {
                finish(course, Status.COMPLETED); return GameplayFrameDisposition.HOLD;
            }
            frame++;
            applyBinding(course, host.resolveFrame(StageplayTimeline.sample(document, frame)));
            host.playFrame(frame);
            return GameplayFrameDisposition.HOLD;
        } catch (RuntimeException failure) {
            try { finish(course, Status.CANCELLED); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private void applyBinding(CourseControl course, StageplayTimeline.Frame sampled) {
        if (binding == null) return;
        String actorId = binding.capture().actorId();
        var actor = sampled.actors().stream().filter(value -> value.actorId().equals(actorId)).findFirst().orElseThrow();
        binding.apply(course, actor, sampled.baseCamera(), host.mappingFrame(actor));
    }
    private void finish(CourseControl course, Status result) {
        if (binding != null && binding.capture().entry() != null) {
            if (binding.current(course)) {
                if (result == Status.CANCELLED) binding.cancel(course); else binding.finish(course, finishMode);
            } else binding.abandon();
        }
        status = result; skip = false; cancel = false;
        if (host != null) host.stop();
    }
    @Override public void afterTick(CourseControl course, boolean advanced) { }
    @Override public boolean drawScene() {
        if (status != Status.PLAYING || frame < 0 || host == null) return false;
        try { host.draw(frame); }
        catch (RuntimeException failure) { deferredFailure = failure; }
        return true;
    }
    @Override public String key() { return "stageplay"; }
    @ModApi public record State(Status status, int frame, boolean skip, boolean cancel, long visit,
                               StageplayNativeBinding.State binding, com.openggf.audio.rewind.AudioLogicalSnapshot entryAudio) { }
    @Override public State capture() { return new State(status, frame, skip, cancel, visit, binding == null ? null : binding.capture(), host == null ? null : host.captureEntryAudio()); }
    @Override public void restore(State state) {
        Objects.requireNonNull(state);
        if (state.frame() < -1 || state.frame() >= document.durationFrames() || state.status() == null
                || (binding == null) != (state.binding() == null)) throw new IllegalArgumentException("Invalid Stageplay rewind state");
        status = state.status(); frame = state.frame(); skip = state.skip(); cancel = state.cancel(); visit = state.visit();
        deferredFailure = null;
        if (binding != null) binding.restore(state.binding());
        if (host != null) { host.seek(frame); host.restoreEntryAudio(state.entryAudio()); } // Restore never emits cues.
    }
    @Override public void resetForMissingSnapshot() {
        status = Status.IDLE; frame = -1; skip = false; cancel = false; visit = -1; deferredFailure = null;
        if (binding != null) binding.abandon();
        if (host != null) { host.seek(-1); host.restoreEntryAudio(null); }
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        if (binding != null) binding.abandon();
        if (host != null) { var owned = host; host = null; owned.close(); }
    }

    interface HostFactory { Host create(); }
    interface Host {
        void prepare(); void seek(int frame); void playFrame(int frame); void draw(int frame); void stop(); void abandon();
        int mappingFrame(StageplayTimeline.ActorPose actor);
        default StageplayTimeline.Frame resolveFrame(StageplayTimeline.Frame authored) {
            if (!authored.puppets().isEmpty()) throw new IllegalStateException("Host has no physics puppet simulation");
            return authored;
        }
        default boolean current() { return true; }
        default void close() { try { if (current()) stop(); } finally { abandon(); } }
        default com.openggf.audio.rewind.AudioLogicalSnapshot captureEntryAudio() { return null; }
        default void restoreEntryAudio(com.openggf.audio.rewind.AudioLogicalSnapshot entry) { }
    }
    private static final class LiveHost implements Host {
        private final StageplayDocument document;
        private final com.openggf.game.session.WorldSession world = GameServices.worldSession();
        private final com.openggf.game.session.GameplayModeContext gameplay = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        private final long visit = GameServices.level().getCompletedProductionLoadGeneration();
        private final SceneRomLibrary library;
        private final StageplayResources resources;
        private final StageplayRenderer renderer;
        private final StageplayAudio audio;
        private StageplayTimeline.Frame resolved;
        private final SceneSurface surface = new SceneSurface();
        private LiveHost(StageplayDocument document, Map<String, byte[]> assets,
                         Map<StageplayDocument.SetSource, com.openggf.level.Level> sets) {
            this.document = document;
            try { library = new SceneRomLibrary(GameServices.module(), GameServices.rom().getRom(), GameServices.rom()); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
            resources = new StageplayResources(library, false);
            resources.setAssetReader(path -> preparedAsset(assets, path));
            sets.forEach((source, level) -> resources.registerSet(source, level));
            renderer = new StageplayRenderer(resources);
            audio = new StageplayAudio(GameServices.audio(), library, path -> preparedAsset(assets, path));
        }
        public boolean current() {
            return world == com.openggf.game.session.SessionManager.getCurrentWorldSession()
                    && gameplay == com.openggf.game.session.SessionManager.getCurrentGameplayMode()
                    && GameServices.levelOrNull() != null
                    && visit == GameServices.levelOrNull().getCompletedProductionLoadGeneration();
        }
        public void prepare() { resources.validate(document); try { audio.prepare(document); } catch (IOException failure) { throw new UncheckedIOException(failure); } }
        public void seek(int frame) {
            audio.seek(frame);
            if (frame < 0) { resources.resetPuppets(); resolved = null; }
            else resolveFrame(StageplayTimeline.sample(document, frame));
        }
        public StageplayTimeline.Frame resolveFrame(StageplayTimeline.Frame authored) {
            return resolved = resources.resolveFrame(document, authored);
        }
        public void playFrame(int frame) {
            audio.playFrame(document, frame);
            audio.playNativeFrame(frame, resources.previewSound(frame));
        }
        public void stop() { audio.stop(); resources.resetPuppets(); resolved = null; }
        public com.openggf.audio.rewind.AudioLogicalSnapshot captureEntryAudio() { return audio.captureEntry(); }
        public void restoreEntryAudio(com.openggf.audio.rewind.AudioLogicalSnapshot entry) { audio.restoreEntry(entry); }
        public int mappingFrame(StageplayTimeline.ActorPose actor) { return renderer.mappingFrame(actor); }
        public void draw(int frame) {
            if (resolved == null || resolved.frame() != frame)
                throw new IllegalStateException("Stageplay frame was not evaluated before drawing");
            surface.record(document.width(), document.height(), canvas -> renderer.draw(document, resolved, canvas));
            var graphics = GameServices.graphics();
            if (graphics.isGlInitialized()) surface.render(SceneSurface.projection(document.width(), document.height()),
                    new int[] {graphics.getViewportX(), graphics.getViewportY(), graphics.getViewportWidth(), graphics.getViewportHeight()});
        }
        public void abandon() {
            try { audio.abandon(); } finally { try { surface.close(); } finally { resources.close(); library.close(); } }
        }
    }
}
