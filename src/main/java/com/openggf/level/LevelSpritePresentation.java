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
            // SAT coordinates are already screen-relative. Scroll presentation
            // addresses the engine's full level layout, which Load_Level replaces;
            // rebuild those addresses from the rebased destination camera.
            preparedScroll = null;
            publishedScroll = null;
        }
        void reset() {
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
