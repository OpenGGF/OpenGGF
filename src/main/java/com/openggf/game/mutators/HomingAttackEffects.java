package com.openggf.game.mutators;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PresentationGraphicsAccess;
import com.openggf.graphics.SpritePresentation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Instagib's cosmetic layer, built only from frozen ROM sprite tiles the renderer already drew:
 * a hue-shifted friction-fire trail of the leader and defeated enemies blown apart along the
 * strike. It owns no object slot, never feeds gameplay, and is discarded on rewind and reload.
 * A headless world that never draws simply has no pixels to throw.
 */
public final class HomingAttackEffects {
    /** Blue native player art rotated towards orange flame. */
    static final float FLAME_HUE = 3.0f;
    static final int FLAME_POSES = 6;
    static final int FLAME_RADIUS = 28;
    static final int DEBRIS_TICKS = 60;
    static final int DEBRIS_FADE_TICKS = 15;
    static final int DEBRIS_GRAVITY = 0x30;
    static final int VICTIM_RADIUS = 24;
    static final int MAX_TILES = 24 * 256;

    private record Pose(List<SpritePresentation.Tile> tiles, java.util.Map<Integer, SpritePresentation.PatternVersion> versions,
                        int cameraX, int cameraY) { }

    /** One flying 8x8 piece: world position and velocity in 8.8 fixed point. */
    private static final class Piece {
        final SpritePresentation.Tile tile;
        final SpritePresentation.PatternVersion version;
        int x, y, vx, vy, age;
        float angle, spin;
        Piece(SpritePresentation.Tile tile, SpritePresentation.PatternVersion version, int x, int y,
              int vx, int vy, float spin) {
            this.tile = tile; this.version = version; this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.spin = spin;
        }
    }

    private final Object bankOwner = new Object();
    private PresentationGraphicsAccess.TrailBank bank;
    private GraphicsManager bankGraphics;
    private SpritePresentation.Frame lastFrame = SpritePresentation.Frame.empty();
    private int lastCameraX, lastCameraY;
    private final ArrayDeque<Pose> flame = new ArrayDeque<>();
    private final List<Piece> pieces = new ArrayList<>();

    /** Draw thread: the frame about to be displayed, and a flame sample while the trail burns. */
    void observe(SpritePresentation.Frame frame, int cameraX, int cameraY, boolean flameActive, int leaderX, int leaderY) {
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
        if (!tiles.isEmpty()) flame.addLast(new Pose(tiles, versions, cameraX, cameraY));
        while (flame.size() > FLAME_POSES) flame.removeFirst();
    }

    /** Logic tick: throw the victim's last displayed pieces outwards along the strike. */
    void burst(int centreX, int centreY, int impulseX, int impulseY) {
        int sx = centreX - lastCameraX, sy = centreY - lastCameraY;
        for (var tile : lastFrame.tiles()) {
            if (tile.layer() != SpritePresentation.Layer.OBJECT || tile.subject().suppressed()) continue;
            float tx = tile.x() + tile.width() / 2, ty = tile.y() + tile.height() / 2;
            if (Math.abs(tx - sx) > VICTIM_RADIUS || Math.abs(ty - sy) > VICTIM_RADIUS) continue;
            if (pieces.size() >= 256) break;
            var version = lastFrame.patternVersions().get(tile.patternId());
            int dx = Math.round(tx - sx), dy = Math.round(ty - sy);
            // Momentum from the hit plus an outward spread; no gameplay RNG is consumed.
            int vx = impulseX * 3 / 4 + dx * 0x28;
            int vy = impulseY * 3 / 4 + dy * 0x28 - 0x200;
            float spin = (dx - dy) * 0.02f + (dx >= 0 ? 0.25f : -0.25f);
            pieces.add(new Piece(tile, version, Math.round(tile.x()) + lastCameraX << 8,
                    Math.round(tile.y()) + lastCameraY << 8, vx, vy, spin));
        }
    }

    /** Logic tick: debris physics. */
    void tick() {
        for (var iterator = pieces.iterator(); iterator.hasNext(); ) {
            var piece = iterator.next();
            piece.x += piece.vx;
            piece.y += piece.vy;
            piece.vy += DEBRIS_GRAVITY;
            piece.angle += piece.spin;
            if (++piece.age >= DEBRIS_TICKS) iterator.remove();
        }
    }

    void clear() {
        flame.clear();
        pieces.clear();
        lastFrame = SpritePresentation.Frame.empty();
    }

    void release() {
        clear();
        PresentationGraphicsAccess.release(bank, bankOwner);
        bank = null;
        bankGraphics = null;
    }

    boolean idle() { return flame.isEmpty() && pieces.isEmpty(); }

    /** Draw thread, before the native sprite table so the effects sit behind live sprites. */
    void draw(GraphicsManager graphics, int cameraX, int cameraY) {
        if (graphics == null || idle()) return;
        if (bankGraphics != graphics || !PresentationGraphicsAccess.valid(bank, graphics, bankOwner)) {
            if (bank != null) PresentationGraphicsAccess.release(bank, bankOwner);
            bank = PresentationGraphicsAccess.acquire(graphics, bankOwner);
            bankGraphics = graphics;
        }
        int base = PresentationGraphicsAccess.base(bank, graphics, bankOwner);
        int next = 0;
        graphics.flushPatternBatch();
        int poseIndex = 0;
        for (var pose : flame) {
            float opacity = 0.15f + 0.45f * poseIndex++ / Math.max(1, flame.size() - 1);
            var ids = new java.util.HashMap<Integer, Integer>();
            for (var tile : pose.tiles()) {
                if (next >= MAX_TILES) return;
                Integer id = ids.get(tile.patternId());
                if (id == null) {
                    id = base + next++;
                    ids.put(tile.patternId(), id);
                    PresentationGraphicsAccess.restoreFrozenPattern(graphics, pose.versions().get(tile.patternId()), id);
                }
                float x = tile.x() + pose.cameraX(), y = tile.y() + pose.cameraY();
                render(graphics, id, tile, quad(tile, x, y, 0), FLAME_HUE, opacity);
            }
        }
        for (var piece : pieces) {
            if (next >= MAX_TILES) return;
            int id = piece.tile.patternId();
            if (piece.version != null) {
                id = base + next++;
                PresentationGraphicsAccess.restoreFrozenPattern(graphics, piece.version, id);
            }
            int left = DEBRIS_TICKS - piece.age;
            float opacity = left >= DEBRIS_FADE_TICKS ? 1f : left / (float) DEBRIS_FADE_TICKS;
            render(graphics, id, piece.tile, quad(piece.tile, piece.x / 256f, piece.y / 256f, piece.angle), 0, opacity);
        }
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
