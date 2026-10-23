package com.openggf.mods.code;

import com.openggf.level.objects.authored.AuthoredStageplayService;
import com.openggf.stageplay.StageplayControl;
import com.openggf.stageplay.StageplayPlayback;
import com.openggf.game.GameServiceBundle;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.mode.GameplayFrameDisposition;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Composition adapter for scenes registered through the existing public Stageplay API. */
public final class AuthoredStageplayServices {
    private static final int MAX_SCENES = 256;
    private AuthoredStageplayServices() { }
    public static AuthoredStageplayService blocking(Function<String, StageplayControl> registeredControl) {
        Objects.requireNonNull(registeredControl, "registeredControl");
        return (resource, blocking) -> {
            if (!blocking) throw new IllegalArgumentException("The registered Stageplay player requires blocking playback");
            StageplayControl control = registeredControl.apply(resource);
            if (control == null) throw new IllegalArgumentException("Stageplay resource is not registered: " + resource);
            if (control.status() == StageplayControl.Status.PLAYING || control.status() == StageplayControl.Status.REQUESTED)
                return false;
            control.play(); return true;
        };
    }

    /** Registers bounded public players, then publishes exactly one controller for their shared world. */
    public static void register(ModContext context, List<String> resources) throws IOException {
        var distinct = new LinkedHashSet<>(List.copyOf(resources));
        if (distinct.isEmpty()) return;
        if (distinct.size() > MAX_SCENES)
            throw new IOException("Too many authored Stageplay resources");
        var factories = new LinkedHashMap<String, java.util.function.Supplier<GameServiceBundle>>();
        int index = 0;
        for (String resource : distinct) {
            ModAssetRoot.requireNormalizedEntry(resource);
            String temporaryKey = "authored-scene-" + index++;
            StageplayPlayback.register(context, temporaryKey, resource);
            factories.put(resource, context.extractServiceBundle(temporaryKey));
        }
        context.registerServiceBundle("authored-stageplay", () -> combine(factories));
    }

    /** Validates declared scenes and their typed dependencies without applying or publishing services. */
    public static void validateAssets(ModAssetRoot assets, String game, List<String> resources) throws IOException {
        if (!List.of("s1", "s2", "s3k").contains(game)) throw new IOException("Authored scenes require a stock host");
        register(new ModContext("authored-scene-validation", game, assets), resources);
    }

    /** Fresh editor graph with the same public player registration and immutable asset preparation. */
    public static Prepared prepareForEditor(Path root, String game, List<String> resources) throws IOException {
        if (!List.of("s1", "s2", "s3k").contains(game)) throw new IOException("Authored scenes require a stock host");
        if (resources.isEmpty()) return new Prepared(blocking(resource -> null), null, List.of());
        Path source = root.toAbsolutePath().normalize();
        GameServiceBundle bundle;
        try (var assets = ModAssetRoot.directory(source.getParent(), source,
                ModInputLimits.production(), DirectoryAccess.DEVELOPMENT)) {
            var context = new ModContext("editor-authored-scenes", game, assets);
            register(context, resources);
            bundle = context.extractServiceBundle("authored-stageplay").get();
        }
        var adapters = new ArrayList<RewindSnapshottable<?>>();
        bundle.rewindAdapters().forEach((key, adapter) -> adapters.add(editorAdapter("editor-authored-" + key, adapter)));
        return new Prepared((AuthoredStageplayService) bundle.services().get(AuthoredStageplayService.class),
                (GameplayFrameController) bundle.services().get(GameplayFrameController.class), adapters);
    }

    public record Prepared(AuthoredStageplayService service, GameplayFrameController controller,
            List<RewindSnapshottable<?>> adapters) {
        public Prepared { adapters = List.copyOf(adapters); }
    }

    private static GameServiceBundle combine(Map<String, java.util.function.Supplier<GameServiceBundle>> factories) {
        var router = new Router(factories);
        var builder = GameServiceBundle.builder();
        int index = 0;
        for (var entry : router.players.entrySet()) {
            String prefix = "scene-" + index++ + "-";
            entry.getValue().adapters().forEach((key, adapter) -> {
                String stableKey = prefix + key;
                builder.rewindAdapter(stableKey, router.adapter(stableKey, entry.getKey(), key));
            });
        }
        return builder.service(AuthoredStageplayService.class, router)
                .service(GameplayFrameController.class, router).build();
    }

    private record Player(StageplayControl control, GameplayFrameController controller,
            Map<String, RewindSnapshottable<?>> adapters) { }

    private static final class Router implements AuthoredStageplayService, GameplayFrameController {
        private final Map<String, java.util.function.Supplier<GameServiceBundle>> factories;
        private Map<String, Player> players;
        private Player rowOwner;
        Router(Map<String, java.util.function.Supplier<GameServiceBundle>> factories) {
            this.factories = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(factories));
            ensurePlayers();
        }

        private void ensurePlayers() {
            if (players != null) return;
            var fresh = new LinkedHashMap<String, Player>();
            try {
                for (var entry : factories.entrySet()) {
                    var bundle = Objects.requireNonNull(entry.getValue().get(), "Stageplay service bundle");
                    var control = (StageplayControl) Objects.requireNonNull(bundle.services().get(StageplayControl.class), "Stageplay control");
                    var controller = (GameplayFrameController) Objects.requireNonNull(bundle.services().get(GameplayFrameController.class), "Stageplay controller");
                    fresh.put(entry.getKey(), new Player(control, controller, bundle.rewindAdapters()));
                }
            } catch (RuntimeException failure) {
                for (var player : fresh.values()) try { player.controller().close(); }
                catch (RuntimeException cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
                throw failure;
            }
            players = java.util.Collections.unmodifiableMap(fresh);
        }

        private RewindSnapshottable<?> currentAdapter(String scene, String key) {
            ensurePlayers();
            return Objects.requireNonNull(players.get(scene).adapters().get(key), "Stageplay rewind adapter");
        }

        private RewindSnapshottable<Object> adapter(String stableKey, String scene, String key) {
            return new RewindSnapshottable<>() {
                @Override public String key() { return stableKey; }
                @Override public Object capture() { return currentAdapter(scene, key).capture(); }
                @Override public void restore(Object state) {
                    rowOwner = null;
                    restoreUnchecked(currentAdapter(scene, key), state);
                }
                @Override public void resetForMissingSnapshot() {
                    rowOwner = null;
                    currentAdapter(scene, key).resetForMissingSnapshot();
                }
            };
        }

        @Override public boolean play(String resource, boolean blocking) {
            if (!blocking) throw new IllegalArgumentException("Authored Stageplay requires blocking playback");
            if (!factories.containsKey(resource)) throw new IllegalArgumentException("Stageplay resource is not registered: " + resource);
            ensurePlayers();
            Player player = players.get(resource);
            if (active() != null) return false;
            player.control().play();
            return true;
        }

        private Player active() {
            if (players == null) return null;
            Player active = null;
            for (var player : players.values()) {
                var status = player.control().status();
                if (status != StageplayControl.Status.PLAYING && status != StageplayControl.Status.REQUESTED) continue;
                if (active != null) throw new IllegalStateException("Two authored scenes cannot own the same gameplay frame");
                active = player;
            }
            return active;
        }

        @Override public boolean requiresExclusiveControl() { return true; }
        @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) {
            return beforeFrame(course, input) != GameplayFrameDisposition.HOLD;
        }
        @Override public GameplayFrameDisposition beforeFrame(CourseControl course, LogicalInputSnapshot input) {
            ensurePlayers();
            rowOwner = active();
            var disposition = GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
            for (var player : players.values()) {
                // Even idle public players observe load generations here. Skipping this leaves
                // their previous act visit live and cancels a request made in the next act.
                var result = player.controller().beforeFrame(course, input);
                if (player == rowOwner) disposition = result;
                else if (result != GameplayFrameDisposition.ADVANCE_NATIVE_INPUT)
                    throw new IllegalStateException("An inactive authored scene requested world control");
            }
            return disposition;
        }
        @Override public void afterTick(CourseControl course, boolean advanced) {
            if (rowOwner != null) rowOwner.controller().afterTick(course, advanced);
            rowOwner = null;
        }
        @Override public boolean retainRolling() { var p = active(); return p != null && p.controller().retainRolling(); }
        @Override public boolean drawScene() { var p = active(); return p != null && p.controller().drawScene(); }
        @Override public void drawOverlay() { var p = active(); if (p != null) p.controller().drawOverlay(); }
        @Override public com.openggf.game.presentation.RewindPresentation rewindPresentation() {
            var p = active(); return p == null ? com.openggf.game.presentation.RewindPresentation.NONE : p.controller().rewindPresentation();
        }
        @Override public boolean presentationPaused() { var p = active(); return p != null && p.controller().presentationPaused(); }
        @Override public boolean allowsDebugRewind() { var p = active(); return p == null || p.controller().allowsDebugRewind(); }
        @Override public boolean consumeTitleRequest() { var p = active(); return p != null && p.controller().consumeTitleRequest(); }
        @Override public void close() {
            if (players == null) return;
            // Effective modules can outlive a session. Retire this graph while retaining only
            // immutable registered factories; the next session receives fresh public players.
            var retired = players;
            players = null;
            rowOwner = null;
            RuntimeException failure = null;
            for (var player : retired.values()) try { player.controller().close(); }
            catch (RuntimeException rejected) {
                if (failure == null) failure = rejected;
                else if (failure != rejected) failure.addSuppressed(rejected);
            }
            if (failure != null) throw failure;
        }
    }

    private static RewindSnapshottable<Object> editorAdapter(String key, RewindSnapshottable<?> delegate) {
        return new RewindSnapshottable<>() {
            @Override public String key() { return key; }
            @Override public Object capture() { return delegate.capture(); }
            @Override public void restore(Object state) { restoreUnchecked(delegate, state); }
            @Override public void resetForMissingSnapshot() { delegate.resetForMissingSnapshot(); }
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restoreUnchecked(RewindSnapshottable delegate, Object state) { delegate.restore(state); }
}
