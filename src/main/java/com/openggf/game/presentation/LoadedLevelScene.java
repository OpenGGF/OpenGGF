package com.openggf.game.presentation;

import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.SpindashDustArtProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-level local ROM resource residency and value projection; holds no object/physics snapshot. */
public final class LoadedLevelScene {
    private final RomSceneArtCatalog art = new RomSceneArtCatalog();
    private final GraphicsManager graphics;
    private final LevelManager level;
    private final Map<String, SpriteArtSet> players = new HashMap<>(), dust = new HashMap<>();
    private final SpriteArtSet tails;
    private final boolean separateTailArt;

    public LoadedLevelScene(LevelManager level, GraphicsManager graphics) throws IOException {
        this.level = level; this.graphics = graphics;
        if (level.getGame() == null || level.getGame().getRom() == null) {
            throw new IllegalStateException("Read-only scene requires a locally ROM-loaded resource session");
        }
        // Animated source recipes precede destination slots: both independently loaded consumers
        // choose the same source identity even when their authoritative animation clocks differ.
        if (level.getAnimatedPatternManager() instanceof RomSceneArtSource source) {
            source.sceneArtRecipes().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> art.addRecipe(entry.getKey(), entry.getValue()));
        }
        if (level.getGame() instanceof PlayerSpriteArtProvider provider) {
            for (String character : List.of("sonic", "tails")) {
                SpriteArtSet set = provider.loadPlayerSpriteArt(character);
                if (set != null) { players.put(character, set); art.addRecipe("player/" + character, set.artTiles()); }
            }
        }
        if (level.getGame() instanceof SpindashDustArtProvider provider) {
            for (String character : List.of("sonic", "tails")) {
                SpriteArtSet set = provider.loadSpindashDustArt(character);
                if (set != null) { dust.put(character, set); art.addRecipe("dust/" + character, set.artTiles()); }
            }
        }
        separateTailArt = level.getGameModule().hasSeparateTailsTailArt();
        tails = separateTailArt ? level.getGameModule().loadTailsTailArt() : players.get("tails");
        if (tails != null && !tails.isEmpty()) art.addRecipe("tail/tails", tails.artTiles());
        var objects = level.getObjectRenderManager();
        if (objects != null) {
            if (objects.getArtProvider() instanceof RomSceneArtSource source) {
                source.sceneArtRecipes().entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> art.addRecipe(entry.getKey(), entry.getValue()));
            }
            objects.getArtBundle().sheets().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> art.addRecipe("object/" + entry.getKey(), entry.getValue().getPatterns()));
        }
        if (level.getCurrentLevel().getRingSpriteSheet() != null) {
            art.addRecipe("rings", level.getCurrentLevel().getRingSpriteSheet().getPatterns());
        }
        art.addLevel("terrain", level.getCurrentLevel());
    }

    public SceneViewPresenter presenter(int width, int height) {
        return new RomSceneViewPresenter(art, graphics, width, height, new ScenePoseProjector(players, dust, tails, separateTailArt));
    }

    public ScenePresentationFrame capture(long revision, PlayerPresentationPose pose, SpritePresentation.Frame sprites,
                                          AbstractPlayableSprite player, int cameraX, int cameraY, int width, int height,
                                          int[] horizontal, int foregroundY, int backgroundY) {
        return capture(revision, pose, sprites, player, cameraX, cameraY, width, height,
                horizontal, foregroundY, backgroundY, 0, 0);
    }

    public ScenePresentationFrame capture(long revision, PlayerPresentationPose pose, SpritePresentation.Frame sprites,
                                          AbstractPlayableSprite player, int cameraX, int cameraY, int width, int height,
                                          int[] horizontal, int foregroundY, int backgroundY, int marginX, int marginY) {
        if (marginX < 0 || marginX > width || marginY < 0 || marginY > height
                || (long) (width + 2 * marginX) * (height + 2 * marginY) > 1_612_800) {
            throw new IllegalArgumentException("Terrain survey envelope exceeds limits");
        }
        var tiles = new ArrayList<ScenePresentationFrame.Tile>();
        projectTerrain(tiles, ScenePresentationFrame.Layer.BACKGROUND, width, height, horizontal, backgroundY, marginX, marginY);
        projectTerrain(tiles, ScenePresentationFrame.Layer.FOREGROUND, width, height, horizontal, foregroundY, marginX, marginY);
        int terrainCount = tiles.size();
        var primitives = new ArrayList<ScenePresentationFrame.Primitive>();
        int[] mappedIndices = new int[sprites.tiles().size() + 1];
        boolean insertedPose = false;
        for (int i = 0; i < sprites.tiles().size(); i++) {
            mappedIndices[i] = tiles.size();
            var tile = sprites.tiles().get(i);
            if (tile.layer().isHud()) continue;
            if (pose.kind() != PlayerPresentationPose.Kind.NATIVE && tile.layer() == SpritePresentation.Layer.PLAYER) {
                if (!insertedPose && player != null) {
                    projectPose(tiles, player, pose, cameraX, cameraY); insertedPose = true;
                }
                continue;
            }
            var version = sprites.patternVersions().get(tile.patternId());
            // Level-tile objects (EHZ ledges/platforms) reference the same native
            // pattern buffer as terrain. Headless loads need not upload those slots.
            if (version == null && tile.patternId() < level.getCurrentLevel().getPatternCount()) {
                version = RomSceneArtCatalog.version(level.getCurrentLevel().getPattern(tile.patternId()));
            }
            if (version == null) version = SpritePresentation.patternSample(graphics, tile.patternId());
            if (version == null) throw new IllegalStateException("Unresolved displayed pattern " + tile.patternId());
            var layer = switch (tile.layer()) {
                case PLAYER -> ScenePresentationFrame.Layer.PLAYER;
                case RINGS -> ScenePresentationFrame.Layer.RINGS;
                default -> ScenePresentationFrame.Layer.OBJECT;
            };
            tiles.add(new ScenePresentationFrame.Tile(layer, art.reference(version), tile.palette(), tile.hFlip(), tile.vFlip(),
                    tile.priority(), Math.round(tile.x()), Math.round(tile.y()), Math.round(tile.width()), Math.round(tile.height()),
                    tile.rowStart(), tile.rowEnd(), tile.priorityShader() ? tile.occlusionMask() : 0,
                    tile.ghost() ? Math.round(tile.ghostAlpha() * 255) : 255));
        }
        mappedIndices[sprites.tiles().size()] = tiles.size();
        if (!insertedPose && pose.kind() != PlayerPresentationPose.Kind.NATIVE && player != null) {
            projectPose(tiles, player, pose, cameraX, cameraY);
        }
        for (var primitive : sprites.primitives()) {
            if (primitive.layer().isHud() || (pose.kind() != PlayerPresentationPose.Kind.NATIVE
                    && primitive.layer() == SpritePresentation.Layer.PLAYER)) continue;
            int before = Math.max(terrainCount, mappedIndices[primitive.beforeTile()]);
            var geometry = SpritePresentation.geometry(primitive);
            var kind = switch (geometry.kind()) {
                case RECTANGLE -> ScenePresentationFrame.PrimitiveKind.RECTANGLE;
                case VERTEX -> ScenePresentationFrame.PrimitiveKind.VERTEX;
            };
            var vertices = geometry.vertices().stream().map(vertex -> new ScenePresentationFrame.Vertex(
                    vertex.x1(), vertex.y1(), vertex.x2(), vertex.y2(), vertex.argb())).toList();
            primitives.add(new ScenePresentationFrame.Primitive(before, kind, geometry.method(), vertices));
        }
        int count = Math.max(4, level.getCurrentLevel().getPaletteCount());
        int[] palette = new int[count * 16];
        for (int line = 0; line < level.getCurrentLevel().getPaletteCount(); line++) {
            for (int color = 0; color < 16; color++) {
                var rgb = level.getCurrentLevel().getPalette(line).getColor(color);
                palette[line * 16 + color] = 0xFF000000 | (rgb.r & 255) << 16 | (rgb.g & 255) << 8 | rgb.b & 255;
            }
        }
        var backdrop = level.getCurrentLevel().getBackdropColor();
        return new ScenePresentationFrame(revision, level.getCurrentAct(), width, height, cameraX, cameraY,
                0xFF000000 | (backdrop.r & 255) << 16 | (backdrop.g & 255) << 8 | backdrop.b & 255,
                palette, tiles, primitives);
    }

    private void projectTerrain(List<ScenePresentationFrame.Tile> tiles, ScenePresentationFrame.Layer layer,
                                int width, int height, int[] scroll, int vertical, int marginX, int marginY) {
        // Per-line samples preserve EHZ's ripple and shipped last-two-line scroll bug.
        // Adjacent rows with identical tile geometry are merged below to bound ordinary views.
        var pending = new HashMap<RowKey, Integer>();
        var references = new HashMap<Integer, ScenePresentationFrame.ArtReference>();
        // Survey reads decoded terrain beyond the native viewport. Its animation and
        // deformation remain at the captured native phase: outside its scanline
        // table, repeat the nearest captured band instead of advancing parallax.
        for (int y = -marginY; y < height + marginY; y++) {
            int packed = scroll.length == 0 ? 0 : scroll[Math.clamp(y, 0, scroll.length - 1)];
            int h = layer == ScenePresentationFrame.Layer.FOREGROUND ? (short) (packed >>> 16) : (short) packed;
            int worldY = y + vertical, row = Math.floorMod(worldY, 8), top = y - row;
            for (int x = -marginX - Math.floorMod(-marginX - h, 8); x < width + marginX; x += 8) {
                int worldX = x - h;
                int descriptor = layer == ScenePresentationFrame.Layer.FOREGROUND
                        ? level.getForegroundTileDescriptorAtWorld(worldX, worldY)
                        : level.getBackgroundTileDescriptorAtWorld(worldX, worldY);
                PatternDesc desc = new PatternDesc(descriptor);
                int id = desc.getPatternIndex();
                if (id >= level.getCurrentLevel().getPatternCount()) throw new IllegalStateException("Terrain pattern out of range");
                var reference = references.computeIfAbsent(id, index ->
                        art.reference(RomSceneArtCatalog.version(level.getCurrentLevel().getPattern(index))));
                var key = new RowKey(layer, reference, desc.getPaletteIndex(), desc.getHFlip(), desc.getVFlip(), desc.getPriority(), x, top);
                Integer previous = pending.get(key);
                if (previous != null && tiles.get(previous).rowEnd() == row) {
                    var old = tiles.get(previous);
                    tiles.set(previous, new ScenePresentationFrame.Tile(layer, reference, old.palette(), old.hFlip(), old.vFlip(),
                            old.priority(), x, top, 8, 8, old.rowStart(), row + 1, 0, 255));
                } else {
                    if (tiles.size() == ScenePresentationFrame.MAX_TILES) {
                        throw new IllegalArgumentException("Terrain survey exceeds scene tile limit");
                    }
                    pending.put(key, tiles.size());
                    tiles.add(new ScenePresentationFrame.Tile(layer, reference, desc.getPaletteIndex(), desc.getHFlip(), desc.getVFlip(),
                            desc.getPriority(), x, top, 8, 8, row, row + 1, 0, 255));
                }
            }
        }
    }

    private record RowKey(ScenePresentationFrame.Layer layer, ScenePresentationFrame.ArtReference art, int palette,
                          boolean hFlip, boolean vFlip, boolean priority, int x, int y) { }

    private void projectPose(List<ScenePresentationFrame.Tile> tiles, AbstractPlayableSprite player,
                             PlayerPresentationPose pose, int cameraX, int cameraY) {
        new ScenePoseProjector(players, dust, tails, separateTailArt).project(tiles,
                new ScenePlayerPose(player.getCode(), player.getRenderCentreX(), player.getRenderCentreY(), pose),
                cameraX, cameraY, player.isHighPriority());
    }

}
