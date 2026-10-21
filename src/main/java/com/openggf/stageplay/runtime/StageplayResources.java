package com.openggf.stageplay.runtime;

import com.openggf.editor.project.EditorProjectStore;
import com.openggf.level.Level;
import com.openggf.level.render.DetachedLevelKit;
import com.openggf.mods.scene.*;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Owns detached scene assets, never advances or changes the loaded gameplay world. */
public final class StageplayResources implements AutoCloseable {
    private final Function<String, SceneRomArt> resolver;
    private final List<String> available;
    private final Runnable release;
    private final Map<StageplayDocument.SetSource, SceneLevelKit> sets = new HashMap<>();
    private Path projectRoot;
    private boolean closed;

    public StageplayResources(SceneRomLibrary library, boolean ownsLibrary) {
        this(library::rom, library.availableGames(), ownsLibrary ? library::close : () -> { });
    }
    public StageplayResources(Function<String, SceneRomArt> resolver, List<String> available) {
        this(resolver, available, () -> { });
    }
    private StageplayResources(Function<String, SceneRomArt> resolver, List<String> available, Runnable release) {
        this.resolver = Objects.requireNonNull(resolver); this.available = List.copyOf(available); this.release = release;
    }
    public List<String> availableGames() { requireOpen(); return available; }
    public void setProjectRoot(Path root) {
        requireOpen(); projectRoot = root == null ? null : root.toAbsolutePath().normalize(); sets.clear();
    }
    public SceneRomArt rom(String game) {
        requireOpen();
        SceneRomArt art = resolver.apply(game);
        if (art == null) throw new IllegalArgumentException("Supply the " + game + " ROM to use this scene");
        return art;
    }
    public SceneSpriteSet character(String game, String character) { return rom(game).character(character); }

    /** Installs a project already decoded through the caller's bounded asset root. */
    public void registerSet(StageplayDocument.SetSource source, SceneLevelKit kit) {
        requireOpen();
        if (source.levelProject().isEmpty()) throw new IllegalArgumentException("Only authored sets may be registered");
        sets.put(Objects.requireNonNull(source), Objects.requireNonNull(kit));
    }
    public SceneLevelKit set(StageplayDocument document) {
        requireOpen();
        var sourceKey = document.set();
        SceneRomArt art = rom(sourceKey.gameId());
        if (!sourceKey.romSha1().isEmpty() && !sourceKey.romSha1().equalsIgnoreCase(art.romSha1()))
            throw new IllegalArgumentException("Scene set requires a different exact ROM identity");
        return sets.computeIfAbsent(sourceKey, source -> {
            if (!source.levelProject().isEmpty()) {
                if (projectRoot == null) throw new IllegalArgumentException("Save the scene beside its referenced level project first");
                try {
                    Path root = projectRoot.toRealPath();
                    Path file = root.resolve(source.levelProject()).toRealPath();
                    if (!file.startsWith(root)) throw new IOException("Level project escapes the scene directory");
                    var project = new EditorProjectStore().open(file);
                    if (!project.metadata().gameId().code().equals(source.gameId()))
                        throw new IOException("Authored level and scene set use different games");
                    return authored(project.snapshot());
                } catch (IOException error) { throw new UncheckedIOException("Cannot load the authored Stageplay set", error); }
            }
            SceneLevelKit kit = art.levelKit(source.zone(), source.act());
            if (kit == null) throw new IllegalArgumentException("This act has no detached Stageplay set: "
                    + source.gameId() + " zone " + source.zone() + " act " + source.act());
            return kit;
        });
    }

    /** Preflight before transport/export. Missing mapping frames and animations are errors, not invisible actors. */
    public void validate(StageplayDocument document) {
        set(document);
        for (var actor : document.actors()) {
            var art = character(actor.gameId(), actor.character());
            for (var key : actor.keys()) {
                if (key.pose() >= art.frameCount()) throw new IllegalArgumentException("Missing mapping frame for " + actor.name());
                if (key.pose() < 0 && art.animationFrames(key.animation()).length == 0)
                    throw new IllegalArgumentException("Missing animation " + key.animation() + " for " + actor.name());
            }
        }
        for (var cue : document.audio()) if (cue.kind() != StageplayDocument.AudioKind.VOICE
                && cue.kind() != StageplayDocument.AudioKind.MUSIC_STOP) rom(cue.gameId());
    }

    /** Authoring uses its explicit level document; no live terrain is mutated. */
    public static SceneLevelKit authored(Level level) {
        var kit = new DetachedLevelKit(level, (layer, pattern, x) ->
                pattern >= 0 && pattern < level.getPatternCount() ? level.getPattern(pattern) : null,
                new int[] {level.getMinX(), level.getMinY(), Math.max(1, level.getMaxX() - level.getMinX() + 1),
                        Math.max(1, level.getMaxY() - level.getMinY() + 1)}, null);
        return new SceneLevelKit() {
            private final Map<Integer, SceneImage> images = new HashMap<>();
            private SceneBackdrop backdrop;
            public int blockSize() { return kit.blockSize(); }
            public int columns() { return kit.columns(); }
            public int rows() { return kit.rows(); }
            public int blockCount() { return kit.blockCount(); }
            public int block(int x, int y) { return kit.blockAt(x, y); }
            public int[] playableArea() { return kit.playable(); }
            public int[] palette() { return kit.palette(); }
            public SceneImage blockImage(int block) {
                return images.computeIfAbsent(block, id -> new SceneImage(blockSize(), blockSize(), kit.blockPixels(id)));
            }
            public byte[] blockSolidity(int block) { return kit.blockSolidity(block); }
            public SceneBackdrop backdrop() {
                if (backdrop == null) {
                    var source = kit.backdrop(); var picture = source.picture();
                    backdrop = new SceneBackdrop(new SceneImage(picture.width(), picture.height(), picture.argb()),
                            source.bands().stream().map(band -> new SceneBackdrop.Band(band.top(), band.height(), band.speed(), band.drift())).toList());
                }
                return backdrop;
            }
        };
    }
    private void requireOpen() { if (closed) throw new IllegalStateException("Stageplay resources are closed"); }
    @Override public void close() { if (!closed) { closed = true; sets.clear(); release.run(); } }
}
