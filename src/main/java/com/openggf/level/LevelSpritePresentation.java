package com.openggf.level;

import com.openggf.game.internal.SpriteTablePublication;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.graphics.PaletteUploadPresentation;
import com.openggf.graphics.SpritePresentation;
import com.openggf.sprites.managers.SpriteManager;

/** Internal lifecycle/rewind bridge; the level renderer owns both CPU and published tables. */
public final class LevelSpritePresentation {
    private LevelSpritePresentation() { }

    /**
     * Internal immutable scroll value belonging to one retained level/renderer lifetime.
     * Only captureScroll creates these values; repeated equal captures in that lifetime compare equal.
     * No buffer or mutable render-mode storage is exposed.
     */
    public static final class ScrollFrame {
        private final LevelManager owner;
        private final LevelRenderer renderer;
        private final com.openggf.game.session.WorldSession world;
        private final com.openggf.game.GameDataSource source;
        private final Level level;
        private final com.openggf.camera.Camera camera;
        private final ParallaxManager parallax;
        private final com.openggf.graphics.GraphicsManager graphics;
        private final com.openggf.graphics.PatternAtlas atlas;
        private final Object lifetime;
        private final long loadGeneration;
        private final boolean glInitialized, headless;
        private final int width, height;
        private final LevelScrollPresentation scroll;

        private ScrollFrame(LevelManager owner) {
            this.owner = java.util.Objects.requireNonNull(owner, "level");
            renderer = owner.spritePresentationRenderer();
            world = owner.getWorldSession();
            source = world == null ? null : world.getDataSource();
            level = owner.level;
            camera = owner.camera;
            parallax = owner.parallaxManager;
            graphics = owner.graphicsManager;
            if (renderer == null || world == null || source == null || level == null
                    || world.getCurrentLevel() != level || camera == null || parallax == null || graphics == null) {
                throw new IllegalStateException("Scroll capture requires a retained, attached level");
            }
            atlas = graphics.getPatternAtlas();
            glInitialized = graphics.isGlInitialized();
            headless = graphics.isHeadlessMode();
            if (atlas == null || (!glInitialized && !headless)) {
                throw new IllegalStateException("Scroll capture requires initialized graphics");
            }
            width = camera.getWidth(); height = camera.getHeight();
            lifetime = renderer.spriteTables.scrollLifetime;
            loadGeneration = owner.getCompletedProductionLoadGeneration();
            scroll = renderer.captureScroll();
            requireCurrent(owner);
        }

        public int cameraX() { return scroll.registers().cameraX(); }
        public int cameraY() { return scroll.registers().cameraY(); }

        private void requireCurrent(LevelManager candidate) {
            if (candidate != owner) throw new IllegalArgumentException("Scroll frame belongs to another level manager");
            if (owner.spritePresentationRenderer() != renderer || owner.getWorldSession() != world
                    || world.getDataSource() != source || owner.level != level || world.getCurrentLevel() != level
                    || owner.camera != camera || owner.parallaxManager != parallax || owner.graphicsManager != graphics
                    || graphics.getPatternAtlas() != atlas || graphics.isGlInitialized() != glInitialized
                    || graphics.isHeadlessMode() != headless || camera.getWidth() != width || camera.getHeight() != height
                    || renderer.spriteTables.scrollLifetime != lifetime
                    || owner.getCompletedProductionLoadGeneration() != loadGeneration) {
                throw new IllegalStateException("Scroll frame no longer belongs to the retained renderer lifetime");
            }
        }

        @Override public boolean equals(Object other) {
            return other instanceof ScrollFrame frame && owner == frame.owner && renderer == frame.renderer
                    && world == frame.world && source == frame.source && level == frame.level && camera == frame.camera
                    && parallax == frame.parallax && graphics == frame.graphics && atlas == frame.atlas
                    && lifetime == frame.lifetime && loadGeneration == frame.loadGeneration
                    && glInitialized == frame.glInitialized && headless == frame.headless
                    && width == frame.width && height == frame.height && scroll.equals(frame.scroll);
        }

        @Override public int hashCode() {
            // Owner/lifetime identity narrows the domain; scroll supplies its immutable value hash.
            return java.util.Objects.hash(System.identityHashCode(owner), System.identityHashCode(lifetime),
                    loadGeneration, scroll);
        }
    }

    /**
     * Capture actual current render registers/buffers after completed level load and the native scroll update.
     * Resolves render-mode contributors once; never draws, advances deformation or prepares/publishes sprites.
     * Callers own phase/latch/rewind policy. Capture and drawing must run on the owning engine thread.
     */
    public static ScrollFrame captureScroll(LevelManager level) { return new ScrollFrame(level); }

    /** Pure owner/lifetime/scope preflight for composite restore before any partition is written. */
    public static void validateScroll(LevelManager level, ScrollFrame frame) {
        java.util.Objects.requireNonNull(frame, "frame");
        frame.requireCurrent(level);
        frame.renderer.requireScrollIdle();
    }

    /**
     * Select one captured scroll for terrain drawing and drain its commands before restoring the prior view.
     * The callback must only draw: no owner/lifecycle changes, native preparation or nested scroll/cinematic scope.
     * Commands use the retained graphics camera, not a substituted snapshot camera. Same-object graphics
     * cleanup/reinitialization without a world/renderer/load reset is an unsupported lifecycle.
     */
    public static void withScroll(LevelManager level, ScrollFrame frame, Runnable draw) {
        java.util.Objects.requireNonNull(draw, "draw");
        validateScroll(level, frame);
        frame.renderer.withScroll(frame.scroll, () -> frame.requireCurrent(level), draw);
    }

    /**
     * Internal terrain scope over an exact physical 64x32 RGBA background image (8192 bytes).
     * A null selection privately copies the current completed-load view; it creates/publishes no ScrollFrame
     * and never updates native scroll, deformation, sprites or clocks. A nonnull selection keeps its existing
     * owner-aware lifetime. The input is detached before old commands drain and before the callback runs.
     *
     * Requires the owning rendering thread, an attached completed production load, a static 512px
     * background, no feature provider and no full-width/persistent runtime owner. The callback only draws
     * terrain/normal patterns: no owner, art, palette, cache-policy or native-state changes. Enclosing
     * scroll/cinematic/preparation/graphics-capture scopes reject before cache/upload mutation.
     * Scroll/mode/guards restore on failure; the installed image remains a derived retained cache.
     * Callers retain their closed/per-visit gates. Calls during a load, and same-instance graphics
     * cleanup/reinitialization without a world/renderer/load reset, are unsupported lifecycles.
     */
    public static void withScrollAndBackground(LevelManager level, ScrollFrame selectedScroll,
                                               byte[] descriptors64x32, Runnable draw) {
        java.util.Objects.requireNonNull(draw, "draw");
        if (descriptors64x32 == null || descriptors64x32.length != 64 * 32 * 4) {
            throw new IllegalArgumentException("Background scope requires an exact 64x32 RGBA image");
        }
        java.util.Objects.requireNonNull(level, "level");
        if (selectedScroll != null) validateScroll(level, selectedScroll);
        var validateOwner = backgroundScopeOwner(level);
        Runnable validate = () -> {
            validateOwner.run();
            if (selectedScroll != null) selectedScroll.requireCurrent(level);
        };
        level.spritePresentationRenderer().withScrollAndBackground(
                selectedScroll == null ? null : selectedScroll.scroll, validate, descriptors64x32.clone(), draw);
    }

    /** Retain actual owners for one call without manufacturing a public captured frame or epoch. */
    private static Runnable backgroundScopeOwner(LevelManager owner) {
        var renderer = owner.spritePresentationRenderer();
        var world = owner.getWorldSession();
        var source = world == null ? null : world.getDataSource();
        var module = owner.gameModule;
        var level = owner.level;
        var camera = owner.camera;
        var parallax = owner.parallaxManager;
        var graphics = owner.graphicsManager;
        var tilemap = owner.tilemapManager;
        long generation = owner.getCompletedProductionLoadGeneration();
        if (renderer == null || world == null || source == null || module == null
                || world.getGameModule() != module || level == null || world.getCurrentLevel() != level
                || camera == null || parallax == null || graphics == null || tilemap == null || generation <= 0) {
            throw new IllegalStateException("Background scope requires an attached completed production load");
        }
        renderer.requireScrollIdle();
        SpritePresentation.requireUncapturedCommandScope(graphics);
        var atlas = graphics.getPatternAtlas();
        boolean initialized = graphics.isGlInitialized(), headless = graphics.isHeadlessMode();
        int width = camera.getWidth(), height = camera.getHeight();
        if (atlas == null || (!initialized && !headless) || width <= 0 || height <= 0) {
            throw new IllegalStateException("Background scope requires initialized graphics and viewport");
        }
        var lifetime = renderer.spriteTables.scrollLifetime;
        return () -> {
            if (owner.spritePresentationRenderer() != renderer || owner.getWorldSession() != world
                    || world.getDataSource() != source || owner.gameModule != module || world.getGameModule() != module
                    || owner.level != level || world.getCurrentLevel() != level || owner.camera != camera
                    || owner.parallaxManager != parallax || owner.graphicsManager != graphics || owner.tilemapManager != tilemap
                    || graphics.getPatternAtlas() != atlas || graphics.isGlInitialized() != initialized
                    || graphics.isHeadlessMode() != headless || camera.getWidth() != width || camera.getHeight() != height
                    || renderer.spriteTables.scrollLifetime != lifetime
                    || owner.getCompletedProductionLoadGeneration() != generation) {
                throw new IllegalStateException("Background scope no longer belongs to the retained renderer lifetime");
            }
        };
    }

    /** Immutable displayed SAT, read by captured presentation consumers independently of GPU drawing. */
    public static SpritePresentation.Frame published(LevelManager level) {
        var renderer = level == null ? null : level.spritePresentationRenderer();
        return renderer == null ? SpritePresentation.Frame.empty() : renderer.spriteTables.published();
    }

    /** Immutable background registers/buffers selected by the active level draw. */
    public record BackgroundScroll(int[] horizontal, float y, short[] columns, int period) {
        public BackgroundScroll {
            horizontal = horizontal.clone();
            columns = columns == null ? null : columns.clone();
        }
        @Override public int[] horizontal() { return horizontal.clone(); }
        @Override public short[] columns() { return columns == null ? null : columns.clone(); }
    }

    /** Internal effect bridge: overlays must replay the generation used by both tile passes. */
    public static BackgroundScroll backgroundScrollForDraw(LevelManager level) {
        var renderer = level == null ? null : level.spritePresentationRenderer();
        return renderer == null ? null : renderer.backgroundScrollForDraw();
    }

    /** Internal directed-render scope. The callback must flush its drawing before returning. */
    public static void withCinematicCamera(LevelManager level, int x, int y, Runnable draw) {
        level.spritePresentationRenderer().withCinematicCamera(x, y, draw);
    }

    static final class Tables implements RewindSnapshottable<Tables.State> {
        record State(SpritePresentation.Frame prepared, SpritePresentation.Frame published,
                     SpritePresentation.Frame counters, LevelScrollPresentation preparedScroll,
                     LevelScrollPresentation publishedScroll,
                     PaletteUploadPresentation.DisplayedFade displayedFade) { }
        private SpritePresentation.Frame prepared = SpritePresentation.Frame.empty();
        private SpritePresentation.Frame published = SpritePresentation.Frame.empty();
        private SpritePresentation.Frame counters = SpritePresentation.Frame.empty();
        private LevelScrollPresentation preparedScroll, publishedScroll;
        // A lifetime marker, deliberately excluded from rewind State. Reset/reload invalidates old views.
        private Object scrollLifetime = new Object();
        void prepare(SpritePresentation.Frame frame) { prepare(frame, null); }
        void prepare(SpritePresentation.Frame frame, LevelScrollPresentation scroll) {
            prepared = frame;
            preparedScroll = scroll;
        }
        LevelScrollPresentation publishedScroll() { return publishedScroll; }
        void publish() {
            // S3K VInt / VInt_8_Cont publish VSRAM, H_scroll_buffer and SAT
            // from the completed CPU loop. Single-player VInt_0 retains them.
            published = prepared;
            publishedScroll = preparedScroll;
        }
        void publishCounters(SpritePresentation.Frame frame) { counters = frame; }
        SpritePresentation.Frame counters() { return counters; }
        SpritePresentation.Frame published() { return published; }
        void invalidateScrollForActReload() {
            scrollLifetime = new Object();
            // SAT coordinates are already screen-relative. Scroll presentation
            // addresses the engine's full level layout, which Load_Level replaces;
            // rebuild those addresses from the rebased destination camera.
            preparedScroll = null;
            publishedScroll = null;
        }
        void reset() {
            scrollLifetime = new Object();
            prepared = SpritePresentation.Frame.empty();
            published = prepared;
            counters = prepared;
            preparedScroll = null;
            publishedScroll = null;
        }
        @Override public String key() { return "level-sprite-presentation"; }
        @Override public State capture() {
            // The displayed CRAM fade belongs to the same V-int publication as the SAT and scroll.
            var graphics = com.openggf.game.GameServices.hasRuntime() ? com.openggf.game.GameServices.graphics() : null;
            return new State(prepared, published, counters, preparedScroll, publishedScroll,
                    PaletteUploadPresentation.captureDisplayedFade(graphics));
        }
        @Override public void restore(State state) {
            // A restore redraws without a V-int publication: upload any latched palette
            // lines now; the next ordinary publication resumes latching.
            if (com.openggf.game.GameServices.hasRuntime()) {
                var graphics = com.openggf.game.GameServices.graphics();
                PaletteUploadPresentation.release(graphics);
                // Lines redraw from their restored owners at once, but the fade CRAM displayed
                // with this restored publication is the captured generation, not the restored
                // owners' newer request (which reaches CRAM at the next V-int).
                PaletteUploadPresentation.restoreDisplayedFade(graphics, state.displayedFade());
            }
            prepared = state.prepared();
            published = state.published();
            counters = state.counters();
            preparedScroll = state.preparedScroll();
            publishedScroll = state.publishedScroll();
        }
        @Override public void resetForMissingSnapshot() { reset(); }
    }

    static boolean enabled(LevelManager level) {
        return level != null && level.gameModule != null
                && level.gameModule.getLevelInitProfile() instanceof SpriteTablePublication;
    }

    public static void prepare(LevelManager level, SpriteManager sprites) {
        var renderer = level == null ? null : level.spritePresentationRenderer();
        if (renderer != null) renderer.rejectScrollOperation();
        if (level != null && level.camera != null) {
            level.spritePresentationRenderer().boundsMask.advance(LevelScrollPresentation.captureArenaMask(level),
                    level.camera.getXWithShake(), level.camera.getWidth());
        }
        if (enabled(level)) {
            level.spritePresentationRenderer().prepareSpritePresentation(sprites);
            // Render_Sprites also places sprite-object title-card pieces in this table.
            com.openggf.game.internal.TitleCardVintPublication.prepare(level.gameModule.getTitleCardProvider());
        }
    }

    public static void publish(LevelManager level, PlcLifecyclePhase phase) {
        var renderer = level == null ? null : level.spritePresentationRenderer();
        if (renderer != null) renderer.rejectScrollOperation();
        if (enabled(level) && ((SpriteTablePublication) level.gameModule.getLevelInitProfile())
                .publishesSpriteTable(phase)) {
            var profile = (SpriteTablePublication) level.gameModule.getLevelInitProfile();
            level.spritePresentationRenderer().spriteTables.publish();
            // Sprite-object title cards reach the screen through the same SAT upload.
            com.openggf.game.internal.TitleCardVintPublication.publish(level.gameModule.getTitleCardProvider());
            PaletteUploadPresentation.publishAndLatch(level.graphicsManager);
            if (profile.updatesHudCounters(phase)) {
                if (!level.isHudSuppressed()) {
                    com.openggf.game.LevelRingDisplay.publish(level.getLevelGamestate());
                }
                level.spritePresentationRenderer().publishHudCounters(profile.advancesHudTimer(phase));
            }
        } else if (level != null) {
            PaletteUploadPresentation.release(level.graphicsManager);
        }
    }

    /**
     * Publishes the prepared table for a scene that runs the level's VInt outside the ordinary
     * level phases (S3K {@code SpecialStage_Results} over the rebuilt HPZ sanctuary). Phase-gated
     * {@link #publish} deliberately never publishes for a special-stage results screen, whose
     * parked level table is stale.
     */
    public static void publishPreparedScene(LevelManager level) {
        var renderer = level == null ? null : level.spritePresentationRenderer();
        if (renderer != null) renderer.rejectScrollOperation();
        if (enabled(level)) level.spritePresentationRenderer().spriteTables.publish();
        if (level != null) {
            PaletteUploadPresentation.release(level.graphicsManager);
        }
    }

    public static void register(LevelManager level, RewindRegistry registry) {
        if (level != null) com.openggf.game.LevelRingDisplay.register(level, registry);
        else com.openggf.game.LevelRingDisplay.unregister(registry);
        registry.deregister("level-sprite-presentation");
        registry.deregister("level-bounds-mask");
        registry.deregister("camera-boundary-presentation");
        var renderer = level == null ? null : level.spritePresentationRenderer();
        // Registration can precede renderer attachment (including headless sessions).
        // Remove the previous scene's adapters even when this scene has none yet.
        if (renderer == null) return;
        registry.register(renderer.boundsMask);
        if (level.camera != null) {
            registry.register(com.openggf.camera.CameraBoundaryPresentation.rewindAdapter(level.camera));
        }
        if (enabled(level)) registry.register(renderer.spriteTables);
    }
}
