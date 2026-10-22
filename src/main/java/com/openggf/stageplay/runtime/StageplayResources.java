package com.openggf.stageplay.runtime;

import com.openggf.level.Level;
import com.openggf.io.PngCodec;
import com.openggf.stageplay.StageplayDialogue;
import com.openggf.level.render.DetachedLevelKit;
import com.openggf.level.resources.AuthoredLevelResources;
import com.openggf.mods.scene.*;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.sprites.playable.PlayablePuppet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Owns scene assets and explicit offline simulation; an editor's active gameplay world is never replaced. */
public final class StageplayResources implements AutoCloseable {
    private final Function<String, SceneRomArt> resolver;
    private final List<String> available;
    private final Runnable release;
    private final SceneRomLibrary library;
    private StageplayLiveWorld liveWorld;
    private StageplayOfflinePreview livePreview;
    private com.openggf.mods.scene.host.SceneSurface nativePosers;
    private final Map<StageplayDocument.SetSource, Level> terrains = new HashMap<>();
    private final Map<StageplayDocument.SetSource, SceneLevelKit> sets = new HashMap<>();
    private Map<String, SceneImage> dialogueImages = Map.of();
    private Map<String, Integer> dialogueImageBytes = Map.of();
    private Map<String, StageplayDocument.DialogueSkin> dialogueStyles = Map.of();
    private Map<String, StageplayDialogue.Layout> dialogueLayouts = Map.of();
    private StageplayDocument preparedDialogue;
    private StageplayAssets.Reader assetReader;
    private Path projectRoot;
    private boolean closed;

    public StageplayResources(SceneRomLibrary library, boolean ownsLibrary) {
        this(library::rom, library.availableGames(), ownsLibrary ? library::close : () -> { }, library);
    }
    public StageplayResources(Function<String, SceneRomArt> resolver, List<String> available) {
        this(resolver, available, () -> { }, null);
    }
    private StageplayResources(Function<String, SceneRomArt> resolver, List<String> available, Runnable release, SceneRomLibrary library) {
        this.resolver = Objects.requireNonNull(resolver); this.available = List.copyOf(available); this.release = release;
        this.library = library;
    }
    public List<String> availableGames() { requireOpen(); return available; }
    public void setProjectRoot(Path root) {
        requireOpen(); projectRoot = root == null ? null : root.toAbsolutePath().normalize(); sets.clear();
        assetReader = null; terrains.clear(); resetPuppets(); livePreview = null; clearDialogue();
    }
    /** A bounded owner snapshot reader, used by registered cutscenes without filesystem access. */
    public void setAssetReader(StageplayAssets.Reader reader) {
        requireOpen(); assetReader = Objects.requireNonNull(reader); sets.clear(); terrains.clear(); resetPuppets(); livePreview = null; clearDialogue();
    }
    private void clearDialogue() {
        preparedDialogue = null; dialogueImages = Map.of(); dialogueLayouts = Map.of();
        dialogueImageBytes = Map.of(); dialogueStyles = Map.of();
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
        terrains.remove(source); resetPuppets();
    }
    /** Registered authored terrain is shared by its pictured set and private playable simulation. */
    public void registerSet(StageplayDocument.SetSource source, Level level) {
        registerSet(source, authored(level));
        terrains.put(source, Objects.requireNonNull(level)); resetPuppets();
    }

    /** Resolves terrain puppets or the shared LIVE world; editor LIVE pixels/state come from an isolated worker. */
    public StageplayTimeline.Frame resolveFrame(StageplayDocument document, StageplayTimeline.Frame authoredFrame) {
        requireOpen();
        if (liveWorld != null) return liveWorld.resolve(document, authoredFrame);
        if (livePreview == null) livePreview = new StageplayOfflinePreview(StageplayPreviewAssets.source(projectRoot, assetReader));
        return livePreview.resolve(document, authoredFrame.frame(), authoredFrame.world() == null ? null : authoredFrame.camera());
    }

    /** The caller supplies an explicitly owned offline world; resources owns its lifetime. */
    public void setLiveWorld(StageplayLiveWorld world) {
        requireOpen();
        if (liveWorld != null) liveWorld.close();
        liveWorld = Objects.requireNonNull(world);
        liveWorld.setTerrainResolver(this::terrain);
    }
    public void drawLiveWorld(StageplayTimeline.Frame frame) {
        drawLiveWorld(frame, frame.camera());
    }
    /** Preview framing and exports share the same complete world layer, including Posers. */
    public void drawLiveWorld(StageplayTimeline.Frame frame, StageplayTimeline.CameraPose view) {
        if (liveWorld == null) throw new IllegalStateException("Live world was not resolved");
        var document = liveWorld.document();
        boolean posed = frame.actors().stream().anyMatch(a -> a.visible()
                && document.puppets().stream().noneMatch(p -> p.actorId().equals(a.actorId())));
        if (!posed) { liveWorld.draw(frame, view); return; }
        if (nativePosers == null) nativePosers = new com.openggf.mods.scene.host.SceneSurface();
        var renderer = new StageplayRenderer(this);
        liveWorld.draw(frame, view, (x, y, width, height) -> {
            var camera = new StageplayTimeline.CameraPose(x, y, 1);
            nativePosers.record(width, height, canvas -> renderer.drawLivePosers(document, frame, canvas, camera));
            nativePosers.render(com.openggf.mods.scene.host.SceneSurface.projection(width, height), new int[]{0, 0, width, height});
        });
    }

    /** Exact owned preview PID for lifecycle diagnostics, or zero before launch/after release. */
    public long previewProcessId() { return livePreview == null ? 0 : livePreview.processId(); }
    /** Captured native no-device SFX for this sampled frame; the editor decides forward-playback admission. */
    public com.openggf.audio.StreamedMusicPort.SfxPcm previewSound(int frame) { return livePreview==null ? null : livePreview.sound(frame); }
    SceneImage livePreviewImage() { return livePreview == null ? null : livePreview.image(); }
    public void resetPuppets() { if (liveWorld != null) liveWorld.reset(); if (livePreview != null) livePreview.close(); }

    public java.util.List<StageplayObjectInspection> objectInspection() {
        if (liveWorld != null) return liveWorld.objectInspection();
        return livePreview == null ? java.util.List.of() : livePreview.objectInspection();
    }

    private void requirePhysicalLibrary() {
        if (library == null) throw new IllegalStateException("Physics puppets require supplied native ROM resources");
    }

    private Level terrain(StageplayDocument.SetSource source) {
        set(source); // validates identity and prepares authored project data first
        if (terrains.containsKey(source)) return terrains.get(source);
        requirePhysicalLibrary();
        if (!source.levelProject().isEmpty()) throw new IllegalArgumentException("Authored set has no native terrain data");
        return terrains.computeIfAbsent(source, value -> library.level(value.gameId(), value.zone(), value.act()));
    }

    /** Actual decoded collision terrain for the bounded, isolated authoring route worker. */
    public Level routeTerrain(StageplayDocument.SetSource source) {
        requireOpen();
        return terrain(source);
    }

    /** Same donor, native anchor and collision setup as ordinary Stageplay physical actors. */
    public PlayablePuppet createRouteProbe(
            StageplayDocument.SetSource source,
            StageplayDocument.Actor actor,
            StageplayDocument.ActorKey anchor,
            StageplayDocument.PuppetTrack track) {
        requireOpen();
        requirePhysicalLibrary();
        return PlayablePuppet.createNativeWorldProbe(
                terrain(source),
                library.module(source.gameId()),
                source.zone(),
                source.act(),
                library.module(actor.gameId()),
                actor.character(),
                library.characterArt(actor.gameId(), actor.character()),
                nativePixel(anchor.x(), true),
                nativePixel(anchor.y(), false),
                track.collisionPath(),
                track.snapToFloor(),
                anchor.flipX());
    }

    /** Foreground query from an explicitly owned, initialized native route visit. */
    public com.openggf.physics.RouteTerrainProbe routeTerrainProbe(com.openggf.level.LevelManager ownedLevel) {
        requireOpen();
        requirePhysicalLibrary();
        var objects = Objects.requireNonNull(ownedLevel, "owned route level").getObjectManager();
        if (objects == null) throw new IllegalArgumentException("Native route visit has no injected collision owner");
        return new com.openggf.physics.NativeRouteTerrainProbe(ownedLevel,
                objects.getObjectServices().backgroundPlaneCollisionProvider());
    }

    private static int nativePixel(double value, boolean horizontal) {
        long rounded = Math.round(value);
        if (rounded < (horizontal ? 0 : Short.MIN_VALUE) || rounded > Short.MAX_VALUE)
            throw new IllegalArgumentException("Physics puppet spawn lies outside native 16-bit coordinates");
        return (int) rounded;
    }

    public SceneLevelKit set(StageplayDocument document) {
        return set(document.set());
    }
    /** Resolves any shot's set with the same identity and bounded-path checks as the opening set. */
    public SceneLevelKit set(StageplayDocument.SetSource sourceKey) {
        requireOpen();
        SceneRomArt art = rom(sourceKey.gameId());
        if (!sourceKey.romSha1().isEmpty() && !sourceKey.romSha1().equalsIgnoreCase(art.romSha1()))
            throw new IllegalArgumentException("Scene set requires a different exact ROM identity");
        return sets.computeIfAbsent(sourceKey, source -> {
            if (!source.levelProject().isEmpty()) {
                try {
                    com.openggf.level.resources.AuthoredLevelDecoder.Decoded project;
                    if (assetReader != null) {
                        project = AuthoredLevelResources.decode("ogfproject", assetReader.read(source.levelProject()));
                    } else {
                        if (projectRoot == null) throw new IllegalArgumentException("Save the scene beside its referenced level project first");
                        Path root = projectRoot.toRealPath();
                        Path file = root.resolve(source.levelProject()).toRealPath();
                        if (!file.startsWith(root)) throw new IOException("Level project escapes the scene directory");
                        project = AuthoredLevelResources.read("ogfproject", file);
                    }
                    if (!project.gameId().equals(source.gameId()))
                        throw new IOException("Authored level and scene set use different games");
                    terrains.put(source, project.level());
                    return authored(project.level());
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
        prepareDialogue(document);
        validateSet(document.set());
        for (var change : document.sceneChanges()) validateSet(change.set());
        for (var camera : document.camera())
            StageplayWorldPresentation.validate(document.width(), document.height(), camera.zoom());
        for (var actor : document.actors()) {
            var art = character(actor.gameId(), actor.character());
            for (var key : actor.keys()) {
                if (key.pose() >= art.frameCount()) throw new IllegalArgumentException("Missing mapping frame for " + actor.name());
                if (key.pose() < 0 && art.animationFrames(key.animation()).length == 0)
                    throw new IllegalArgumentException("Missing animation " + key.animation() + " for " + actor.name());
                rom(actor.gameId()).characterAccessoryPose(actor.character(), key.animation(), key.pose(),
                        0, key.flipX(), 0, 0);
            }
        }
        if (!document.puppets().isEmpty()) {
            requirePhysicalLibrary();
            for (var track : document.puppets()) {
                var actor = document.actors().stream().filter(value -> value.id().equals(track.actorId())).findFirst().orElseThrow();
                if (library.characterArt(actor.gameId(), actor.character()) == null)
                    throw new IllegalArgumentException("Missing native character art: " + actor.id());
                for (var key : actor.keys()) { nativePixel(key.x(), true); nativePixel(key.y(), false); }
                for (var command : track.commands()) {
                    var source = document.set();
                    for (var change : document.sceneChanges()) if (change.frame() <= command.frame()) source = change.set();
                    var capabilities = library.module(actor.gameId()).getRules().playerCapability();
                    if ((command.kind() == StageplayDocument.PuppetCommandKind.FLY_TO || command.kind() == StageplayDocument.PuppetCommandKind.HOVER)
                            && !capabilities.tailsFlightEnabled()) throw new IllegalArgumentException("Donor module does not support native flight: " + actor.id());
                    if (command.kind() == StageplayDocument.PuppetCommandKind.GLIDE_TO && !capabilities.glideAttacksEnabled())
                        throw new IllegalArgumentException("Donor module does not support native glide: " + actor.id());
                    var target = command.target();
                    if (target.layoutIndex() >= 0 && !source.levelProject().isEmpty() && terrain(source).getObjects().stream().noneMatch(o -> o.layoutIndex() == target.layoutIndex()
                            && o.objectId() == target.objectId() && o.subtype() == target.subtype()))
                        throw new IllegalArgumentException("Unknown placed-object target: " + command.id());
                }
            }
        }
        for (var cue : document.audio()) if (cue.kind() != StageplayDocument.AudioKind.VOICE
                && cue.kind() != StageplayDocument.AudioKind.MUSIC_STOP) rom(cue.gameId());
    }

    private void validateSet(StageplayDocument.SetSource source) {
        if (!source.levelProject().isEmpty()) {
            set(source);
            return;
        }
        var art = rom(source.gameId());
        if (!source.romSha1().isEmpty() && !source.romSha1().equalsIgnoreCase(art.romSha1()))
            throw new IllegalArgumentException("Scene set requires a different exact ROM identity");
        // Native load validates its own act. Detached level-kit support is not a
        // prerequisite for native-world playback or its placed-object inventory.
    }

    /**
     * Prepares immutable dialogue images/layout without requiring any ROM or GL context.
     * Publication is atomic; failed edits retain the previous complete resource snapshot.
     */
    public void prepareDialogue(StageplayDocument document) {
        requireOpen(); Objects.requireNonNull(document);
        if (document.equals(preparedDialogue)) return;
        var paths = StageplayAssets.imagePaths(document);
        if (paths.size() > StageplayAssets.MAX_ASSETS) throw new IllegalArgumentException("Too many dialogue images");
        var images = new HashMap<String, SceneImage>();
        var layouts = new HashMap<String, StageplayDialogue.Layout>();
        var sizes = new HashMap<String, Integer>();
        var styles = new HashMap<String, StageplayDocument.DialogueSkin>();
        long pixels = 0, encodedBytes = 0;
        try {
            for (String path : paths) {
                SceneImage image = dialogueImages.get(path);
                int encodedSize = dialogueImageBytes.getOrDefault(path, 0);
                if (image == null) {
                    byte[] encoded = assetReader == null ? StageplayAssets.read(projectRoot, path) : assetReader.read(path);
                    if (encoded == null || encoded.length == 0 || encoded.length > StageplayAssets.MAX_ENCODED_BYTES)
                        throw new IOException("Invalid encoded dialogue image size: " + path);
                    encodedSize = encoded.length;
                    if (encodedBytes + encodedSize > StageplayAssets.MAX_TOTAL_BYTES)
                        throw new IOException("Dialogue image assets exceed 64 MiB");
                    var info = PngCodec.info(encoded);
                    long count = (long) info.width() * info.height();
                    if (info.width() > 1024 || info.height() > 1024 || count > 1_048_576)
                        throw new IOException("Dialogue image exceeds 1024 pixels per side: " + path);
                    if (pixels + count > 4_194_304) throw new IOException("Dialogue images exceed the decoded pixel budget");
                    var decoded = PngCodec.decode(encoded);
                    image = new SceneImage(decoded.getWidth(), decoded.getHeight(), decoded.pixels());
                }
                encodedBytes += encodedSize;
                if (encodedBytes > StageplayAssets.MAX_TOTAL_BYTES) throw new IOException("Dialogue image assets exceed 64 MiB");
                sizes.put(path, encodedSize);
                pixels += (long) image.width() * image.height();
                if (pixels > 4_194_304) throw new IOException("Dialogue images exceed the decoded pixel budget");
                images.put(path, image);
            }
            for (var skin : document.dialogueSkins()) if (!skin.decorationAsset().isEmpty()) {
                var image = images.get(skin.decorationAsset()); int border = skin.decorationBorder();
                if (2 * border >= image.width() || 2 * border >= image.height())
                    throw new IllegalArgumentException("Dialogue decoration border leaves no image centre: " + skin.id());
            }
            for (var cue : document.dialogue()) {
                var skin = StageplayDialogue.skin(document, cue);
                if (!skin.decorationAsset().isEmpty()
                        && (2 * skin.decorationBorder() >= cue.width() || 2 * skin.decorationBorder() >= cue.height()))
                    throw new IllegalArgumentException("Dialogue decoration border does not fit panel: " + cue.id());
                layouts.put(cue.id(), StageplayDialogue.layout(document, cue));
                styles.put(cue.id(), skin);
            }
        } catch (IOException error) { throw new UncheckedIOException("Cannot prepare Stageplay dialogue", error); }
        dialogueImages = Map.copyOf(images); dialogueImageBytes = Map.copyOf(sizes);
        dialogueLayouts = Map.copyOf(layouts); dialogueStyles = Map.copyOf(styles); preparedDialogue = document;
    }

    public StageplayDocument.DialogueSkin dialogueSkin(String cueId) {
        requireOpen();
        var skin = dialogueStyles.get(cueId);
        if (skin == null) throw new IllegalArgumentException("Dialogue style was not prepared: " + cueId);
        return skin;
    }
    public SceneImage dialogueImage(String path) {
        requireOpen();
        var image = dialogueImages.get(path);
        if (image == null) throw new IllegalArgumentException("Dialogue image was not prepared: " + path);
        return image;
    }
    public StageplayDialogue.Layout dialogueLayout(String cueId) {
        requireOpen();
        var layout = dialogueLayouts.get(cueId);
        if (layout == null) throw new IllegalArgumentException("Dialogue layout was not prepared: " + cueId);
        return layout;
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
    @Override public void close() { if (!closed) { closed = true; if (nativePosers != null) nativePosers.close(); if (liveWorld != null) liveWorld.close(); if (livePreview != null) livePreview.close(); sets.clear(); terrains.clear(); clearDialogue(); release.run(); } }
}
