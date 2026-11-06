package com.openggf.game.mutators;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PresentationGraphicsAccess;
import com.openggf.graphics.SpritePresentation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Homing presentation: the lock-on reticle and Instagib's friction heat, embers, afterimages and
 * shard pictures. Gameplay shards belong to {@link HomingAttackRuntime}; this class only chooses
 * how each looks, using frozen ROM tiles from the sprite table the renderer already drew. It
 * never feeds gameplay and is discarded on rewind and reload. A headless world that never draws
 * has no captured pictures, so its shards fall back to plain sparks.
 */
public final class HomingAttackEffects {
    /** Blue native player art rotated towards orange flame. */
    static final float FLAME_HUE = 3.0f;
    static final int FLAME_POSES = 5;
    static final int FLAME_RADIUS = 28;
    static final int VICTIM_RADIUS = 24;
    static final int MAX_TILES = 24 * 256;
    static final int MAX_BURST_PICTURES = 32;
    static final int LOGICAL_HEIGHT = 224;

    private record Pose(List<SpritePresentation.Tile> tiles, Map<Integer, SpritePresentation.PatternVersion> versions,
                        int cameraX, int cameraY, boolean fire) { }
    /** Ember ramps, hottest first: Instagib fire, and the quieter blue of ordinary homing. */
    private static final int[] FIRE_EMBERS = {0xFFF080, 0xFF8020, 0xD02000};
    private static final int[] BLUE_EMBERS = {0xE0F8FF, 0x60B8FF, 0x2050E0};
    // {colour, radius, square size, alpha, arc half-width}: outer haze to the white-hot nose.
    private static final int[][] FIRE_FRONT = {{0xC01800, 26, 6, 130, 9}, {0xFF5010, 22, 5, 210, 8},
            {0xFFA020, 19, 4, 240, 7}, {0xFFE870, 16, 4, 255, 6}, {0xFFFFF4, 14, 3, 255, 4}};
    private static final int[][] BLUE_FRONT = {{0x1830C0, 23, 5, 120, 8}, {0x3878F0, 19, 4, 190, 7},
            {0x90D0FF, 16, 3, 230, 6}, {0xF0FCFF, 14, 3, 240, 4}};
    private record PictureTile(SpritePresentation.Tile tile, SpritePresentation.PatternVersion version) { }

    /** A presentation ember in world pixels. */
    private static final class Ember {
        float x, y, vx, vy;
        int age, life, size;
        final boolean fire;
        Ember(float x, float y, float vx, float vy, int life, int size, boolean fire) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.life = life; this.size = size; this.fire = fire;
        }
    }

    private final Object bankOwner = new Object();
    private PresentationGraphicsAccess.TrailBank bank;
    private GraphicsManager bankGraphics;
    private SpritePresentation.Frame lastFrame = SpritePresentation.Frame.empty();
    private int lastCameraX, lastCameraY;
    private final ArrayDeque<Pose> flame = new ArrayDeque<>();
    private final Map<Long, List<PictureTile>> pictures = new LinkedHashMap<>();
    private final List<Ember> embers = new ArrayList<>();
    // Presentation-only noise; never the game's RNG.
    private final SplittableRandom noise = new SplittableRandom(0x5EED);
    private int clock;

    /** Draw thread: the frame about to be displayed, and an afterimage while the trail burns. */
    void observe(SpritePresentation.Frame frame, int cameraX, int cameraY, boolean flameActive, boolean fire,
                 int leaderX, int leaderY) {
        lastFrame = frame;
        lastCameraX = cameraX;
        lastCameraY = cameraY;
        if (!flameActive) {
            if (!flame.isEmpty()) flame.removeFirst();
            return;
        }
        var tiles = new ArrayList<SpritePresentation.Tile>();
        var versions = new java.util.HashMap<Integer, SpritePresentation.PatternVersion>();
        int sx = leaderX - cameraX, sy = leaderY - cameraY;
        for (var tile : frame.tiles()) {
            if (tile.layer() != SpritePresentation.Layer.PLAYER || tile.subject().suppressed()) continue;
            if (Math.abs(tile.x() + tile.width() / 2 - sx) > FLAME_RADIUS
                    || Math.abs(tile.y() + tile.height() / 2 - sy) > FLAME_RADIUS) continue;
            var version = frame.patternVersions().get(tile.patternId());
            // DPLC art is rewritten every frame; only frozen generations can be redrawn later.
            if (version == null) continue;
            tiles.add(tile);
            versions.put(tile.patternId(), version);
        }
        if (!tiles.isEmpty()) flame.addLast(new Pose(tiles, versions, cameraX, cameraY, fire));
        while (flame.size() > FLAME_POSES) flame.removeFirst();
    }

    /** Logic tick: keep the victim's last displayed tiles as the pictures for its shards. */
    void capture(long burst, int centreX, int centreY) {
        int sx = centreX - lastCameraX, sy = centreY - lastCameraY;
        var tiles = new ArrayList<PictureTile>();
        for (var tile : lastFrame.tiles()) {
            if (tile.layer() != SpritePresentation.Layer.OBJECT || tile.subject().suppressed()) continue;
            float tx = tile.x() + tile.width() / 2, ty = tile.y() + tile.height() / 2;
            if (Math.abs(tx - sx) > VICTIM_RADIUS || Math.abs(ty - sy) > VICTIM_RADIUS) continue;
            tiles.add(new PictureTile(tile, lastFrame.patternVersions().get(tile.patternId())));
        }
        if (tiles.isEmpty()) return;
        pictures.put(burst, List.copyOf(tiles));
        while (pictures.size() > MAX_BURST_PICTURES) pictures.remove(pictures.keySet().iterator().next());
    }

    /** Logic tick: embers stream back from the heat front. */
    void tick(HomingAttackRuntime runtime, PlayableEntity leader) {
        clock++;
        for (var iterator = embers.iterator(); iterator.hasNext(); ) {
            var ember = iterator.next();
            ember.x += ember.vx;
            ember.y += ember.vy;
            ember.vx *= 0.92f;
            ember.vy = ember.vy * 0.92f - 0.05f;
            if (++ember.age >= ember.life) iterator.remove();
        }
        float heat = runtime.heat();
        if (heat <= 0 || leader == null) return;
        boolean fire = runtime.instagibVisuals();
        float[] dir = direction(leader);
        int count = fire ? 4 + Math.round(heat * 6) : 1 + Math.round(heat * 3);
        for (int i = 0; i < count && embers.size() < 240; i++) {
            float spread = (float) (noise.nextDouble() - 0.5) * 2.2f;
            float angle = (float) Math.atan2(dir[1], dir[0]) + spread;
            float r = 12 + (float) noise.nextDouble() * 6;
            float x = leader.getCentreX() + (float) Math.cos(angle) * r;
            float y = leader.getCentreY() + (float) Math.sin(angle) * r;
            float back = 1.5f + (float) noise.nextDouble() * 2.5f;
            embers.add(new Ember(x, y, -dir[0] * back + (float) (noise.nextDouble() - 0.5),
                    -dir[1] * back + (float) (noise.nextDouble() - 0.5), 12 + noise.nextInt(12),
                    fire ? 2 + noise.nextInt(2) : 1 + noise.nextInt(2), fire));
        }
    }

    void clear() {
        flame.clear();
        embers.clear();
        pictures.clear();
        lastFrame = SpritePresentation.Frame.empty();
    }

    void release() {
        clear();
        PresentationGraphicsAccess.release(bank, bankOwner);
        bank = null;
        bankGraphics = null;
    }

    /** Draw thread, before the native sprite table: afterimages and shards sit behind live sprites. */
    void drawBehind(GraphicsManager graphics, int cameraX, int cameraY, List<HomingAttackRuntime.Shard> shards) {
        if (graphics == null || (flame.isEmpty() && shards.isEmpty() && embers.isEmpty())) return;
        int base = bankBase(graphics);
        int next = 0;
        graphics.flushPatternBatch();
        // Embers stream behind the live sprites, so they wrap the leader instead of speckling him.
        for (var ember : embers) {
            float life = ember.age / (float) ember.life;
            int[] ramp = ember.fire ? FIRE_EMBERS : BLUE_EMBERS;
            int colour = ramp[life < 0.35f ? 0 : life < 0.7f ? 1 : 2];
            int alpha = Math.round(255 * (1 - life));
            rect(graphics, Math.round(ember.x) - cameraX, Math.round(ember.y) - cameraY, ember.size, ember.size,
                    alpha << 24 | colour);
        }
        int poseIndex = 0;
        for (var pose : flame) {
            // Instagib's afterimages burn orange; ordinary homing leaves fainter native-blue echoes.
            float opacity = (pose.fire() ? 1f : 0.6f) * (0.3f + 0.5f * poseIndex++ / Math.max(1, flame.size() - 1));
            var ids = new java.util.HashMap<Integer, Integer>();
            for (var tile : pose.tiles()) {
                if (next >= MAX_TILES) return;
                Integer id = ids.get(tile.patternId());
                if (id == null) {
                    id = base + next++;
                    ids.put(tile.patternId(), id);
                    PresentationGraphicsAccess.restoreFrozenPattern(graphics, pose.versions().get(tile.patternId()), id);
                }
                render(graphics, id, tile, quad(tile, tile.x() + pose.cameraX(), tile.y() + pose.cameraY(), 0),
                        pose.fire() ? FLAME_HUE : 0f, opacity);
            }
        }
        var sparks = new ArrayList<HomingAttackRuntime.Shard>();
        for (var shard : shards) {
            var picture = pictures.get(shard.burst());
            if (picture == null) { sparks.add(shard); continue; }
            if (next >= MAX_TILES) return;
            // Shards outnumber a small enemy's tiles, so parts repeat: duplicated wreckage.
            var piece = picture.get(shard.index() % picture.size());
            int id = piece.tile().patternId();
            if (piece.version() != null) {
                id = base + next++;
                PresentationGraphicsAccess.restoreFrozenPattern(graphics, piece.version(), id);
            }
            int left = HomingAttackRuntime.SHARD_TICKS - shard.age();
            float opacity = left >= 12 ? 1f : left / 12f;
            float spin = ((shard.index() % 5) - 2 + 0.5f) * 0.18f;
            render(graphics, id, piece.tile(), quad(piece.tile(), shard.x() / 256f - piece.tile().width() / 2,
                    shard.y() / 256f - piece.tile().height() / 2, spin * shard.age()), 0, opacity);
        }
        for (var shard : sparks) {
            int alpha = Math.max(0, 255 - shard.age() * 5);
            rect(graphics, shard.x() / 256 - cameraX - 1, shard.y() / 256 - cameraY - 1, 3, 3, alpha << 24 | 0xFFB040);
        }
    }

    /** Draw thread, after the native sprite table: heat front, embers and the reticle. */
    void drawFront(GraphicsManager graphics, int cameraX, int cameraY, float heat, boolean fire, PlayableEntity leader,
                   boolean reticle, boolean locked, int reticleX, int reticleY) {
        if (graphics == null) return;
        graphics.flushPatternBatch();
        if (heat > 0 && leader != null) drawHeatFront(graphics, cameraX, cameraY, heat, fire, leader);
        if (reticle) drawReticle(graphics, reticleX - cameraX, reticleY - cameraY, locked);
    }

    /** A bow shock of compressed, glowing air: white-hot at the nose, orange and red outwards. */
    private void drawHeatFront(GraphicsManager graphics, int cameraX, int cameraY, float heat, boolean fire,
                               PlayableEntity leader) {
        float[] dir = direction(leader);
        float heading = (float) Math.atan2(dir[1], dir[0]);
        int cx = leader.getCentreX() - cameraX, cy = leader.getCentreY() - cameraY;
        int[][] layers = fire ? FIRE_FRONT : BLUE_FRONT;
        for (int[] layer : layers) {
            int arc = layer[4];
            for (int k = -arc; k <= arc; k++) {
                float flicker = 0.7f + 0.3f * (float) Math.sin(clock * 0.9 + k * 1.7 + layer[1]);
                float edge = 1 - Math.abs(k) / (float) (arc + 1);
                float angle = heading + k * 0.19f;
                float radius = layer[1] + 3 * heat + (flicker - 0.85f) * 3 - Math.abs(k) * 0.45f;
                int size = layer[2] + (k == 0 ? 1 : 0);
                int x = Math.round(cx + (float) Math.cos(angle) * radius) - size / 2;
                int y = Math.round(cy + (float) Math.sin(angle) * radius) - size / 2;
                int alpha = Math.round(layer[3] * heat * (0.35f + 0.65f * edge) * flicker);
                if (alpha > 0) rect(graphics, x, y, size, size, Math.min(255, alpha) << 24 | layer[0]);
            }
        }
        // Licks of flame peeling back along both flanks.
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 6; i++) {
                float angle = heading + side * (1.25f + i * 0.17f);
                float radius = 15 + i * 1.5f + (float) Math.sin(clock * 1.3 + i) * 1.5f;
                int x = Math.round(cx + (float) Math.cos(angle) * radius) - 1;
                int y = Math.round(cy + (float) Math.sin(angle) * radius) - 1;
                int[] ramp = fire ? FIRE_EMBERS : BLUE_EMBERS;
                int colour = ramp[i < 2 ? 0 : i < 4 ? 1 : 2];
                int alpha = Math.round(230 * heat * (1 - i / 7f));
                if (alpha > 0) rect(graphics, x, y, 3, 3, alpha << 24 | colour);
            }
        }
    }

    /** Four corner brackets; green while acquiring, a tighter red lock while homing. */
    private void drawReticle(GraphicsManager graphics, int x, int y, boolean locked) {
        int size = locked ? 10 : 13 + (int) Math.round(2 * Math.sin(clock * 0.3));
        int colour = locked ? 0xFFFF3830 : 0xFF70FF50;
        int arm = 5;
        for (int sxSign = -1; sxSign <= 1; sxSign += 2) {
            for (int sySign = -1; sySign <= 1; sySign += 2) {
                int cornerX = x + sxSign * size, cornerY = y + sySign * size;
                int hx = sxSign < 0 ? cornerX : cornerX - arm + 1, vy = sySign < 0 ? cornerY : cornerY - arm + 1;
                rect(graphics, hx - 1, cornerY - 1, arm + 2, 4, 0xA0000000);
                rect(graphics, cornerX - 1, vy - 1, 4, arm + 2, 0xA0000000);
                rect(graphics, hx, cornerY, arm, 2, colour);
                rect(graphics, cornerX, vy, 2, arm, colour);
            }
        }
        rect(graphics, x - 1, y - 1, 2, 2, colour);
    }

    private static float[] direction(PlayableEntity leader) {
        float vx = leader.getXSpeed(), vy = leader.getYSpeed();
        float length = (float) Math.hypot(vx, vy);
        if (length < 64) {
            boolean right = !(leader instanceof com.openggf.sprites.AbstractSprite sprite)
                    || sprite.getDirection() == com.openggf.physics.Direction.RIGHT;
            return new float[]{right ? 1 : -1, 0};
        }
        return new float[]{vx / length, vy / length};
    }

    private int bankBase(GraphicsManager graphics) {
        if (bankGraphics != graphics || !PresentationGraphicsAccess.valid(bank, graphics, bankOwner)) {
            if (bank != null) PresentationGraphicsAccess.release(bank, bankOwner);
            bank = PresentationGraphicsAccess.acquire(graphics, bankOwner);
            bankGraphics = graphics;
        }
        return PresentationGraphicsAccess.base(bank, graphics, bankOwner);
    }

    private static void rect(GraphicsManager graphics, int x, int y, int width, int height, int argb) {
        if (width <= 0 || height <= 0 || (argb >>> 24) == 0) return;
        graphics.registerCommand(GLCommand.screenSpaceRect(graphics, LOGICAL_HEIGHT, x, y, width, height, argb));
    }

    private static void render(GraphicsManager graphics, int id, SpritePresentation.Tile tile, float[] quad,
                               float hue, float opacity) {
        PresentationGraphicsAccess.render(graphics, id, tile.palette(), tile.hFlip(), tile.vFlip(), tile.priority(),
                tile.rowStart(), tile.rowEnd(), quad, hue, opacity);
    }

    /**
     * World-space corners of the tile's visible rows, top-left/top-right/bottom-right/bottom-left,
     * rotated about the tile centre.
     */
    private static float[] quad(SpritePresentation.Tile tile, float x, float y, float angle) {
        float width = tile.width();
        float top = y + tile.height() * tile.rowStart() / 8f, height = tile.height() * (tile.rowEnd() - tile.rowStart()) / 8f;
        float cx = x + width / 2, cy = top + height / 2, c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        float[] corners = {-width / 2, -height / 2, width / 2, -height / 2, width / 2, height / 2, -width / 2, height / 2};
        float[] quad = new float[8];
        for (int i = 0; i < 8; i += 2) {
            quad[i] = cx + corners[i] * c - corners[i + 1] * s;
            quad[i + 1] = cy + corners[i] * s + corners[i + 1] * c;
        }
        return quad;
    }
}
