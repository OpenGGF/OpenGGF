package com.openggf.level;

import com.openggf.game.internal.SpriteTablePublication;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.graphics.SpritePresentation;
import com.openggf.sprites.managers.SpriteManager;

/** Internal lifecycle/rewind bridge; the level renderer owns both CPU and published tables. */
public final class LevelSpritePresentation {
    private LevelSpritePresentation() { }

    static final class Tables implements RewindSnapshottable<Tables.State> {
        record State(SpritePresentation.Frame prepared, SpritePresentation.Frame published,
                     SpritePresentation.Frame counters) { }
        private SpritePresentation.Frame prepared = SpritePresentation.Frame.empty();
        private SpritePresentation.Frame published = SpritePresentation.Frame.empty();
        private SpritePresentation.Frame counters = SpritePresentation.Frame.empty();
        void prepare(SpritePresentation.Frame frame) { prepared = frame; }
        void publish() { published = prepared; }
        void publishCounters(SpritePresentation.Frame frame) { counters = frame; }
        SpritePresentation.Frame counters() { return counters; }
        SpritePresentation.Frame published() { return published; }
        void reset() { prepared = SpritePresentation.Frame.empty(); published = prepared; counters = prepared; }
        @Override public String key() { return "level-sprite-presentation"; }
        @Override public State capture() { return new State(prepared, published, counters); }
        @Override public void restore(State state) { prepared = state.prepared(); published = state.published(); counters = state.counters(); }
        @Override public void resetForMissingSnapshot() { reset(); }
    }

    static boolean enabled(LevelManager level) {
        return level != null && level.gameModule != null
                && level.gameModule.getLevelInitProfile() instanceof SpriteTablePublication;
    }

    public static void prepare(LevelManager level, SpriteManager sprites) {
        if (enabled(level)) level.spritePresentationRenderer().prepareSpritePresentation(sprites);
    }

    public static void publish(LevelManager level, PlcLifecyclePhase phase) {
        if (enabled(level) && ((SpriteTablePublication) level.gameModule.getLevelInitProfile())
                .publishesSpriteTable(phase)) {
            var profile = (SpriteTablePublication) level.gameModule.getLevelInitProfile();
            level.spritePresentationRenderer().spriteTables.publish();
            if (profile.updatesHudCounters(phase)) {
                level.spritePresentationRenderer().publishHudCounters(profile.advancesHudTimer(phase));
            }
        }
    }

    public static void register(LevelManager level, RewindRegistry registry) {
        registry.deregister("level-sprite-presentation");
        if (enabled(level)) registry.register(level.spritePresentationRenderer().spriteTables);
    }
}
