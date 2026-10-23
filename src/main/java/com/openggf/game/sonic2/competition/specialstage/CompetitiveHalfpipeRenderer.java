package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.data.RomByteReader;
import com.openggf.game.SpecialStageViewport;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageBomb;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageObject;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStagePlayer;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageRing;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageSpriteData;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageSpriteMappings;
import com.openggf.graphics.GLCommandable;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.RenderPriority;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.graphics.SpriteSatInternalAccess;
import com.openggf.graphics.SpriteSatMaskPostProcessor;
import com.openggf.level.Pattern;
import com.openggf.level.Palette;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.physics.TrigLookupTable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;

/** Native H32 presentation for a two-human halfpipe, with one shared SAT admission pass. */
public final class CompetitiveHalfpipeRenderer {
    private static final int PATTERN_BASE = PatternAtlasRange.SPECIAL_STAGE_PLAYFIELD.base();
    private static final int HUD_MAP = 0x7070;
    private static final int START_MAP = 0x7240;
    private static final int DIGIT_MAP = 0x72D2;
    private static final int MESSAGE_MAP = 0x35E1E;
    private static final int SHADOW_MAP = 0x34492;
    private static final int SHADOW_POSES = 0x3417A;
    private static final int PHRASE_TABLE = 0x35DDE;
    private static final int WORD_TABLE = 0x35C62;
    private static final int PHRASE_X = 0x35DD6;

    public record Rainbow(int x, int y, int mappingFrame) { }
    public record BannerPart(int mappingFrame, int x, int y) { }
    public record MessageLetter(int mappingFrame, int x, int y) { }

    /** Phrase IDs are the ROM's Obj5A_PrintPhrase IDs; -1 means no phrase. */
    public record Overlay(boolean bannerVisible, int bannerY, int phraseId,
                          boolean checkpointHand, int handY, int winner,
                          List<Rainbow> rainbow, List<BannerPart> bannerParts,
                          List<MessageLetter> messageLetters) {
        public Overlay {
            if (phraseId != -1 && (phraseId < 8 || phraseId > 20 || (phraseId & 1) != 0)) {
                throw new IllegalArgumentException("Invalid competitive phrase ID");
            }
            if (winner < 0 || winner > 2) throw new IllegalArgumentException("Invalid checkpoint winner");
            rainbow = List.copyOf(rainbow);
            bannerParts = List.copyOf(bannerParts);
            messageLetters = List.copyOf(messageLetters);
        }

        public Overlay(boolean bannerVisible, int bannerY, int phraseId,
                       boolean checkpointHand, int handY, int winner,
                       List<Rainbow> rainbow, List<BannerPart> bannerParts) {
            this(bannerVisible, bannerY, phraseId, checkpointHand, handY, winner, rainbow, bannerParts, List.of());
        }

        public static Overlay none() {
            return new Overlay(false, 0, -1, false, 0, 0, List.of(), List.of());
        }
    }

    private record Piece(int x, int y, int width, int height, int word) { }
    private record Resident(int nativeBase, int engineBase, int count) { }
    private record Sprite(int bucket, int order, boolean shadow, SpritePieceRenderer.PreparedPiece piece) { }

    private final CompetitiveHalfpipeRomData data;
    private final GraphicsManager graphics;
    private final SpecialStageViewport viewport;
    private final byte[] perspective;
    private final Map<Long, List<Piece>> mappingFrames = new HashMap<>();
    private final List<Resident> residents = new ArrayList<>();
    private final List<Integer> cachedPatterns = new ArrayList<>();
    private CompetitiveHalfpipePlanes planes;
    private boolean initialized;
    private int nextPattern;
    private int playerBase;
    private int ringBase;
    private int bombBase;
    private int starsBase;
    private int explosionBase;
    private int backgroundBase;
    private int trackBase;
    private int geometryOffsetX;
    private int geometryOffsetY;
    private int p1SectorWins;
    private int p2SectorWins;

    public CompetitiveHalfpipeRenderer(CompetitiveHalfpipeRomData data, GraphicsManager graphics,
                                      SpecialStageViewport viewport) {
        this.data = Objects.requireNonNull(data, "data");
        this.graphics = Objects.requireNonNull(graphics, "graphics");
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        this.perspective = data.perspective();
    }

    /** Uploads only this renderer's ROM art; logic-only owners can initialize without GL. */
    public void initialize(int stage) throws IOException {
        reset();
        nextPattern = PATTERN_BASE;
        try {
            Palette[] palettes = data.palettes(stage);
            for (int line = 0; line < 4; line++) graphics.cachePaletteTexture(palettes[line], line);
            backgroundBase = cache(data.backgroundPatterns());
            trackBase = cache(data.trackPatterns());
            playerBase = cache(data.playerPatterns());
            resident(0x1A2, data.messagesPatterns());
            resident(0x1FA, data.hudPatterns());
            resident(0x23C, data.shadowHorizPatterns());
            resident(0x262, data.shadowDiagPatterns());
            resident(0x29C, data.shadowVertPatterns());
            resident(0x38A, data.startPatterns());
            resident(0x3DF, data.playerVsPlayerPatterns());
            resident(0x7A4, data.tailsTextPatterns());
            ringBase = cache(data.ringPatterns());
            bombBase = cache(data.bombPatterns());
            starsBase = cache(data.starsPatterns());
            explosionBase = cache(data.explosionPatterns());
            planes = new CompetitiveHalfpipePlanes(data, graphics, viewport, backgroundBase, trackBase);
            planes.initialize(palettes);
            initialized = true;
        } catch (IOException | RuntimeException | Error failure) {
            try { reset(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private int cache(Pattern[] patterns) {
        if (nextPattern + patterns.length > PatternAtlasRange.SPECIAL_STAGE_PLAYFIELD.endExclusive()) {
            throw new IllegalStateException("Competitive ROM art exceeds the special-stage atlas range");
        }
        int base = nextPattern;
        for (Pattern pattern : patterns) {
            graphics.cachePatternTexture(pattern, nextPattern);
            cachedPatterns.add(nextPattern++);
        }
        return base;
    }

    private void resident(int nativeBase, Pattern[] patterns) {
        residents.add(new Resident(nativeBase, cache(patterns), patterns.length));
    }

    public void reset() {
        initialized = false;
        geometryOffsetX = geometryOffsetY = 0;
        p1SectorWins = p2SectorWins = 0;
        if (planes != null) {
            planes.cleanup();
            planes = null;
        }
        for (int pattern : cachedPatterns) graphics.uncachePattern(pattern);
        cachedPatterns.clear();
        residents.clear();
        planes = null;
    }

    /** SSSetGeometryOffsets presentation deltas; actor physics stays in native halfpipe coordinates. */
    public void setGeometryOffset(int deltaX, int deltaY) {
        geometryOffsetX = deltaX;
        geometryOffsetY = deltaY;
    }

    /** Derived Obj87 VS marks; the native checkpoint owner retains the actual win ledger. */
    public void setSectorWins(int one, int two) {
        if (one < 0 || one > 3 || two < 0 || two > 3) throw new IllegalArgumentException("Native sector wins must be 0..3");
        p1SectorWins = one;
        p2SectorWins = two;
    }

    public void draw(int trackFrame, int[] tiles, int bgScroll, int vScroll,
                     List<Sonic2SpecialStagePlayer> players, List<Sonic2SpecialStageObject> objects,
                     int p1rings, int p2rings, Overlay overlay) {
        if (!initialized) throw new IllegalStateException("Competitive halfpipe renderer is not initialized");
        if (tiles == null || tiles.length == 0) throw new IllegalArgumentException("Decoded track frame is required");
        List<Sprite> sprites = prepareSprites(trackFrame, players, objects, p1rings, p2rings,
                Objects.requireNonNull(overlay, "overlay"));
        drawSprites(tiles, bgScroll, vScroll, sprites);
    }

    /** Consumes only actual native display events, retaining their enqueue order within each bucket. */
    public void drawNative(int[] tiles, int bgScroll, int vScroll,
                           List<Sonic2SpecialStagePlayer> players, CompetitiveHalfpipeRenderFrame frame) {
        if (!initialized) throw new IllegalStateException("Competitive halfpipe renderer is not initialized");
        if (tiles == null || tiles.length == 0) throw new IllegalArgumentException("Decoded track frame is required");
        drawSprites(tiles, bgScroll, vScroll, prepareNativeSprites(players, frame));
    }

    private void drawSprites(int[] tiles, int bgScroll, int vScroll, List<Sprite> sprites) {
        planes.draw(tiles, bgScroll, vScroll, () -> {
            SpriteSatInternalAccess.beginCollection(graphics,
                    SpriteSatMaskPostProcessor.Raster.h32(viewport.innerH32OriginX(), 0, 224));
            for (Sprite sprite : sprites) {
                graphics.setCurrentSpriteSatBucket(sprite.bucket());
                graphics.setCurrentSpriteSatDebugSource(sprite.piece().debugSource());
                if (sprite.shadow()) SpriteSatInternalAccess.submitShadowPieces(graphics,
                        () -> graphics.submitSpriteSatPiece(sprite.piece()));
                else graphics.submitSpriteSatPiece(sprite.piece());
            }
            ClipScope raster = new ClipScope(viewport.innerH32OriginX(), 0, 256, 224);
            graphics.registerCommand(raster.begin);
            try {
                // The shared renderer resolves source ownership in native SAT order and retains
                // exact admitted rows, operator metadata and palette-specific terrain occlusion.
                graphics.endSpriteSatCollectionAndReplay();
            } finally {
                graphics.registerCommand(raster.end);
            }
        });
    }

    /** SSClearObjs leaves the native planes running with an empty displayed sprite table. */
    public void drawCleared(int[] tiles, int bgScroll, int vScroll) {
        if (!initialized) throw new IllegalStateException("Competitive halfpipe renderer is not initialized");
        if (tiles == null || tiles.length == 0) throw new IllegalArgumentException("Decoded track frame is required");
        planes.draw(tiles, bgScroll, vScroll, () -> { });
    }

    /** One palette producer supplies both ordinary intensity and the source-derived shadow companion. */
    public void setPalettes(Palette[] palettes) {
        if (!initialized) throw new IllegalStateException("Competitive halfpipe renderer is not initialized");
        if (palettes == null || palettes.length != 4) throw new IllegalArgumentException("Four native palette lines required");
        Palette[] snapshot = new Palette[4];
        for (int line = 0; line < 4; line++) {
            snapshot[line] = Objects.requireNonNull(palettes[line], "palette line").deepCopy();
            graphics.cachePaletteTexture(snapshot[line], line);
        }
        planes.setPalettes(snapshot);
    }

    /** Deferred, nested scissor ownership also unwinds if a later draw command fails. */
    private final class ClipScope {
        private final int x, y, width, height;
        private final int[] previous = new int[4];
        private boolean enabled;
        private boolean armed;

        private ClipScope(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            begin = (cameraX, cameraY, cameraWidth, cameraHeight) -> open();
        }

        private final GLCommandable begin;

        private void open() {
            int[] actualViewport = new int[4];
            glGetIntegerv(GL_VIEWPORT, actualViewport);
            glGetIntegerv(GL_SCISSOR_BOX, previous);
            enabled = glIsEnabled(GL_SCISSOR_TEST);
            armed = true;
            double sx = actualViewport[2] / (double) viewport.logicalWidth();
            double sy = actualViewport[3] / 224.0;
            int left = actualViewport[0] + (int) Math.floor(x * sx);
            int right = actualViewport[0] + (int) Math.ceil((x + width) * sx);
            int bottom = actualViewport[1] + (int) Math.floor((224 - y - height) * sy);
            int top = actualViewport[1] + (int) Math.ceil((224 - y) * sy);
            if (enabled) {
                left = Math.max(left, previous[0]); right = Math.min(right, previous[0] + previous[2]);
                bottom = Math.max(bottom, previous[1]); top = Math.min(top, previous[1] + previous[3]);
            }
            glScissor(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
            glEnable(GL_SCISSOR_TEST);
        }

        private final GLCommandable end = new GLCommandable() {
            @Override public void execute(int x, int y, int width, int height) { restore(); }
            @Override public void unwindAfterFailure(int x, int y, int width, int height) { restore(); }
        };

        private void restore() {
            if (!armed) return;
            glScissor(previous[0], previous[1], previous[2], previous[3]);
            if (enabled) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
            armed = false;
        }
    }

    private List<Sprite> prepareSprites(int trackFrame, List<Sonic2SpecialStagePlayer> players,
                                        List<Sonic2SpecialStageObject> objects,
                                        int p1rings, int p2rings, Overlay overlay) {
        List<Sprite> out = new ArrayList<>();
        // Obj5E/Obj87 use separate Sonic/Miles counters in VS mode; no TOTAL object is created.
        raw(out, 0, 2, false, HUD_MAP, 0, 0x80, 0, 0x1FA, 0, false, false);
        raw(out, 0, 2, false, HUD_MAP, 2, 0x80, 0, 0x1FA, 0, false, false);
        digits(out, p1rings, 0x48);
        digits(out, p2rings, 0xE0);
        // Obj87 loc_75DE is a later multi-sprite object. FixBugs=0 displays its
        // win marks in priority bucket zero, after the existing HUD counter object.
        winCounters(out, p1SectorWins, p2SectorWins);
        for (int i = 0; i < players.size(); i++) {
            Sonic2SpecialStagePlayer player = players.get(i);
            if (player == null || !player.isSpawned()) continue;
            if (player.dplcLoadRuns()) {
                player(out, player, i);
            }
            if (player.shouldRenderTailsTails() && player.tailsTailsDplcLoadRuns()) {
                playerTail(out, player);
            }
            playerShadow(out, player, i);
        }
        for (int i = 0; i < objects.size(); i++) {
            Sonic2SpecialStageObject object = objects.get(i);
            if (object == null || object.shouldRemove() || !object.isOnScreen()) continue;
            int order = 8 + i * 2;
            if (object instanceof Sonic2SpecialStageRing ring) {
                staticPieces(out, object.getDepth() < 4 && ring.isSparkle() ? 1 : 3, order,
                        false, Sonic2SpecialStageSpriteData.getRingPieces(ring.getMappingFrame()),
                        ring.getScreenX(), ring.getScreenY(), ring.isSparkle() ? starsBase : ringBase,
                        ring.isSparkle() ? 2 : 3, false, false, "ring");
                if (!ring.isSparkle()) objectShadow(out, trackFrame, object, order + 1);
            } else if (object instanceof Sonic2SpecialStageBomb bomb) {
                staticPieces(out, object.getDepth() < 4 && bomb.isExploding() ? 1 : 3, order,
                        false, Sonic2SpecialStageSpriteData.getBombPieces(bomb.getMappingFrame()),
                        bomb.getScreenX(), bomb.getScreenY(), bomb.isExploding() ? explosionBase : bombBase,
                        bomb.isExploding() ? 2 : 1, false, false, "bomb");
                if (!bomb.isExploding()) objectShadow(out, trackFrame, object, order + 1);
            }
        }
        overlay(out, overlay);
        out.sort(Comparator.comparingInt(Sprite::bucket).thenComparingInt(Sprite::order));
        return List.copyOf(out);
    }

    /** Fresh native geometry for focused admission/order checks; never a second budget filter. */
    List<SpriteSatEntry> satEntries(int trackFrame, List<Sonic2SpecialStagePlayer> players,
                                  List<Sonic2SpecialStageObject> objects, int p1rings, int p2rings,
                                  Overlay overlay) {
        return entries(prepareSprites(trackFrame, players, objects, p1rings, p2rings, overlay));
    }

    List<SpriteSatEntry> nativeSatEntries(List<Sonic2SpecialStagePlayer> players, CompetitiveHalfpipeRenderFrame frame) {
        return entries(prepareNativeSprites(players, frame));
    }

    private static List<SpriteSatEntry> entries(List<Sprite> sprites) {
        return sprites.stream()
                .map(sprite -> SpriteSatEntry.fromPreparedPiece(sprite.piece(), sprite.bucket())
                        .withPixelOperation(sprite.shadow() ? SpriteSatEntry.PixelOperation.SHADOW_15
                                : SpriteSatEntry.PixelOperation.NORMAL)).toList();
    }

    private List<Sprite> prepareNativeSprites(List<Sonic2SpecialStagePlayer> players,
                                             CompetitiveHalfpipeRenderFrame frame) {
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(frame, "frame");
        List<Sprite> out = new ArrayList<>();
        int ordinal = 0;
        for (CompetitiveHalfpipeRenderFrame.Draw draw : frame.draws()) {
            List<Sprite> geometry = new ArrayList<>();
            switch (draw.type()) {
                case PLAYER -> nativePlayer(geometry, players.get(draw.owner()), draw, false);
                case PLAYER_SHADOW -> playerShadow(geometry, players.get(draw.owner()), draw.owner());
                case TAIL -> nativePlayer(geometry, players.get(draw.owner()), draw, true);
                case HUD -> {
                    raw(geometry, 0, 0, false, HUD_MAP, 0, 0x80, 0, 0x1FA, 0, false, false);
                    raw(geometry, 0, 0, false, HUD_MAP, 2, 0x80, 0, 0x1FA, 0, false, false);
                }
                case COUNTERS -> {
                    digits(geometry, draw.x(), 0x48);
                    digits(geometry, draw.y(), 0xE0);
                }
                case WIN_COUNTERS -> winCounters(geometry, draw.x(), draw.y());
                case ITEM -> nativeItem(geometry, draw);
                case ITEM_SHADOW -> raw(geometry, draw.bucket(), 0, true, SHADOW_MAP,
                        draw.mappingFrame(), draw.x(), draw.y(), draw.nativeArt() & 0x7FF,
                        draw.palette(), draw.xFlip(), false);
                case SPILL -> nativeStaticPieces(geometry, draw,
                        Sonic2SpecialStageSpriteData.getRingPieces(draw.mappingFrame()), draw.x(), draw.y(),
                        ringBase, 0x322, 3, "spill");
                case BANNER -> raw(geometry, draw.bucket(), 0, false, START_MAP, draw.mappingFrame(),
                        draw.x(), draw.y(), draw.nativeArt() == 0 ? 0x38A : draw.nativeArt() & 0x7FF,
                        draw.palette(), draw.xFlip(), false);
                case MESSAGE, HAND, WINGS -> raw(geometry, draw.bucket(), 0, false, MESSAGE_MAP,
                        draw.mappingFrame(), draw.x(), draw.y(), draw.nativeArt() == 0 ? 0x1A2 : draw.nativeArt() & 0x7FF,
                        draw.palette(), draw.xFlip(), false);
                case RAINBOW -> nativeStaticPieces(geometry, draw,
                        Sonic2SpecialStageSpriteData.getRingPieces(draw.mappingFrame()), draw.x(), draw.y(),
                        ringBase, draw.nativeArt() & 0x7FF, draw.palette(), "rainbow");
            }
            for (Sprite sprite : geometry) {
                out.add(new Sprite(draw.bucket(), ordinal, sprite.shadow(), sprite.piece()));
            }
            ordinal++;
        }
        // The graph has already drained native display buckets in order. Sorting physical slots
        // here would change first-source ownership after same-pass allocation or slot reuse.
        return List.copyOf(out);
    }

    private void nativeItem(List<Sprite> out, CompetitiveHalfpipeRenderFrame.Draw draw) {
        int art = draw.nativeArt() & 0x7FF;
        if (draw.item() instanceof Sonic2SpecialStageRing) {
            nativeStaticPieces(out, draw,
                    Sonic2SpecialStageSpriteData.getRingPieces(draw.mappingFrame()), draw.x(), draw.y(),
                    art == 0x77F ? starsBase : ringBase, art, draw.palette(), "ring");
        } else if (draw.item() instanceof Sonic2SpecialStageBomb) {
            nativeStaticPieces(out, draw,
                    Sonic2SpecialStageSpriteData.getBombPieces(draw.mappingFrame()), draw.x(), draw.y(),
                    art == 0x2B5 ? explosionBase : bombBase, art, draw.palette(), "bomb");
        } else throw new IllegalArgumentException("Unsupported competitive item display type");
    }

    private void nativeStaticPieces(List<Sprite> out, CompetitiveHalfpipeRenderFrame.Draw draw,
                                     Sonic2SpecialStageSpriteData.SpritePiece[] pieces,
                                     int x, int y, int cachedBase, int nativeBase, int palette, String name) {
        for (Sonic2SpecialStageSpriteData.SpritePiece piece : pieces) {
            add(out, draw.bucket(), 0, false, x, y, piece.xOffset, piece.yOffset,
                    piece.widthTiles, piece.heightTiles, cachedBase + piece.tileIndex,
                    nativeBase + piece.tileIndex, palette, piece.hFlip, piece.vFlip,
                    draw.xFlip(), false, true, name);
        }
    }

    private void winCounters(List<Sprite> out, int one, int two) {
        if (one < 0 || one > 3 || two < 0 || two > 3) throw new IllegalArgumentException("Native sector wins must be 0..3");
        for (int i = 0; i < one; i++) raw(out, 0, 5, false, DIGIT_MAP, 10,
                0x20 + i * 0x10, 0x2C, 0x1FA, 2, false, false);
        for (int i = 0; i < two; i++) raw(out, 0, 5, false, DIGIT_MAP, 10,
                0xB8 + i * 0x10, 0x2C, 0x1FA, 2, false, false);
    }

    private void digits(List<Sprite> out, int rings, int x) {
        int value = Math.max(0, Math.min(999, rings));
        if (value >= 100) { raw(out, 0, 4, false, DIGIT_MAP, value / 100, x, 0x20, 0x1FA, 2, false, false); x += 8; }
        if (value >= 10) { raw(out, 0, 4, false, DIGIT_MAP, value / 10 % 10, x, 0x20, 0x1FA, 2, false, false); x += 8; }
        raw(out, 0, 4, false, DIGIT_MAP, value % 10, x, 0x20, 0x1FA, 2, false, false);
    }

    private void nativePlayer(List<Sprite> out, Sonic2SpecialStagePlayer player,
                              CompetitiveHalfpipeRenderFrame.Draw draw, boolean tail) {
        boolean sonic = player.getPlayerType() == Sonic2SpecialStagePlayer.PlayerType.SONIC;
        int index = tail ? player.getTailsTailsMappingFrame() : player.getMappingFrame();
        int map = tail ? 0x34DA8 : sonic ? 0x34212 : 0x34B3E;
        var source = tail ? Sonic2SpecialStageSpriteMappings.getTailsTailsFrame(index)
                : sonic ? Sonic2SpecialStageSpriteMappings.getSonicFrame(index)
                : Sonic2SpecialStageSpriteMappings.getTailsFrame(index);
        List<Piece> nativePieces = frame(map, index);
        if (source.pieces.length != nativePieces.size()) throw new IllegalStateException("Native player DPLC piece count differs");
        for (int i = 0; i < nativePieces.size(); i++) {
            Piece piece = nativePieces.get(i);
            // BuildSprites adds the whole art word to the ROM mapping word.
            // Preserve its priority carry as well as palette and native VRAM index;
            // the cached source offset continues to select the decoded DPLC pixels.
            int word = (piece.word() + (draw.nativeArt() & 0x9FFF) + (draw.palette() << 13)) & 0xFFFF;
            add(out, draw.bucket(), 0, false, player.getXPos() + geometryOffsetX,
                    player.getYPos() + geometryOffsetY, piece.x(), piece.y(), piece.width(), piece.height(),
                    playerBase + source.pieces[i].tileIndex, word & 0x7FF, word >>> 13 & 3,
                    (word & 0x800) != 0, (word & 0x1000) != 0, player.isRenderXFlip(), player.isRenderYFlip(),
                    (word & 0x8000) != 0, tail ? "tails-tail" : "player");
        }
    }

    private void player(List<Sprite> out, Sonic2SpecialStagePlayer player, int order) {
        Sonic2SpecialStageSpriteMappings.SpriteFrame frame = player.getPlayerType() == Sonic2SpecialStagePlayer.PlayerType.SONIC
                ? Sonic2SpecialStageSpriteMappings.getSonicFrame(player.getMappingFrame())
                : Sonic2SpecialStageSpriteMappings.getTailsFrame(player.getMappingFrame());
        playerPieces(out, frame, player.getPriority(), order, player,
                player.getPlayerType() == Sonic2SpecialStagePlayer.PlayerType.SONIC ? 1 : 2, "player");
    }

    private void playerTail(List<Sprite> out, Sonic2SpecialStagePlayer player) {
        playerPieces(out, Sonic2SpecialStageSpriteMappings.getTailsTailsFrame(player.getTailsTailsMappingFrame()),
                Math.max(0, player.getPriority() - 1), 7, player, 2, "tails-tail");
    }

    private void playerPieces(List<Sprite> out, Sonic2SpecialStageSpriteMappings.SpriteFrame frame,
                              int priority, int order, Sonic2SpecialStagePlayer player, int palette, String name) {
        for (Sonic2SpecialStageSpriteMappings.SpritePiece piece : frame.pieces) {
            add(out, priority, order, false, player.getXPos() + geometryOffsetX,
                    player.getYPos() + geometryOffsetY, piece.xOffset, piece.yOffset,
                    piece.widthTiles, piece.heightTiles, playerBase + piece.tileIndex, piece.tileIndex,
                    palette, piece.hFlip, piece.vFlip, player.isRenderXFlip(), player.isRenderYFlip(), true, name);
        }
    }

    private void playerShadow(List<Sprite> out, Sonic2SpecialStagePlayer player, int slot) {
        int angle = player.getAngle() & 0xFF;
        int x = player.getXPos();
        int y = player.getYPos();
        int offsetY = y - player.getSSYPos();
        if (player.getRoutine() != Sonic2SpecialStagePlayer.RoutineState.NORMAL) {
            int absX = Math.abs((short) player.getSSXPos());
            int z = player.getSSZPos() & 0xFFFF;
            if (z == 0) return;
            // loc_33D02 uses the signed word quotient as a PC-relative index. FixBugs=0
            // has no saturation: airborne ratios can read past byte_33D32 into following ROM bytes.
            int index = (short) ((absX << 7) / z);
            angle = data.rom().readU8(0x33D32 + index);
            if (player.getSSXPos() >= 0) angle = -(angle - 0x80) & 0xFF;
            if (player.getSSYPos() < 0) angle = -angle & 0xFF;
            int offsetX = x - ((player.getSSXPos() * 0xCC) >> 8);
            x = offsetX + (((short) (TrigLookupTable.cosHex(angle) * z) * 0xCC) >> 16);
            y = offsetY + ((TrigLookupTable.sinHex(angle) * z) >> 8);
        }
        int index = ((angle - 0x10) & 0xFF) >>> 5;
        int pose = SHADOW_POSES + index * 8;
        int art = data.rom().readU16BE(pose);
        x += data.rom().readS16BE(pose + 2);
        y += data.rom().readS16BE(pose + 4);
        int frame = data.rom().readU8(pose + 6);
        if ((angle & 0x80) != 0) {
            if (index == 3 || index == 7) frame += 3;
            y = offsetY * 2 - y;
        }
        if (player.getPlayerType() == Sonic2SpecialStagePlayer.PlayerType.TAILS
                && player.getAnim() == 1 && !player.isRenderYFlip()) {
            x += player.isRenderXFlip() ? -4 : 4;
            y -= 4;
        }
        raw(out, 4, 5 + slot, true, SHADOW_MAP, frame, x + geometryOffsetX, y + geometryOffsetY, art & 0x7FF,
                art >>> 13 & 3, (data.rom().readU8(pose + 7) & 1) != 0, false);
    }

    private void objectShadow(List<Sprite> out, int trackFrame, Sonic2SpecialStageObject object, int order) {
        int angle = object.getAngle() & 0xFF;
        if ((angle & 0x80) != 0) return;
        Sonic2SpecialStageSpriteData.ObjectShadowInfo info = Sonic2SpecialStageSpriteData.getObjectShadowInfo(angle);
        int nativeBase = switch (info.type) { case FLAT -> 0x23C; case DIAGONAL -> 0x262; case SIDE -> 0x29C; };
        int frame = 10 - Math.min(9, object.getAnimIndex());
        if (frame == 10) frame = 9;
        int perspectiveBase = u16(perspective, trackFrame * 2);
        int entry = perspectiveBase + 2 + (object.getDepth() - 1) * 6;
        if (entry < perspectiveBase + 2 || entry + 6 > perspective.length) return;
        int xr = perspective[entry + 2] & 0xFF;
        int yr = perspective[entry + 3] & 0xFF;
        // loc_351A0 projects the linked shadow with both ellipse radii increased by one quarter.
        int cosine = TrigLookupTable.cosHex(angle);
        int sine = TrigLookupTable.sinHex(angle);
        int x = object.getScreenX() - ((cosine * xr) >> 8) + ((cosine * (xr + (xr >>> 2))) >> 8);
        int y = object.getScreenY() - ((sine * yr) >> 8) + ((sine * (yr + (yr >>> 2))) >> 8);
        staticPieces(out, 5, order, true, Sonic2SpecialStageSpriteData.getShadowPieces(info.type, frame),
                x, y, resolve(nativeBase, 1), 3, info.xFlip, false, "object-shadow");
    }

    private void overlay(List<Sprite> out, Overlay overlay) {
        if (overlay.bannerVisible()) raw(out, 1, 3, false, START_MAP, 0, 0x80, overlay.bannerY(), 0x38A, 0, false, false);
        for (BannerPart part : overlay.bannerParts()) {
            raw(out, 1, 64, false, START_MAP, part.mappingFrame(), part.x(), part.y(), 0x38A, 0, false, false);
        }
        if (overlay.phraseId() >= 0) phrase(out, overlay.phraseId());
        int messageOrder = 68;
        for (MessageLetter letter : overlay.messageLetters()) {
            raw(out, 1, messageOrder++, false, MESSAGE_MAP, letter.mappingFrame(), letter.x(), letter.y(),
                    0x1A2, 2, false, false);
        }
        if (overlay.checkpointHand()) {
            int palette = overlay.winner() == 2 ? 2 : 1;
            raw(out, 1, 65, false, MESSAGE_MAP, 20, 0x80, 0x1C, 0x1A2, palette, false, false);
            raw(out, 0, 66, false, MESSAGE_MAP, 21, 0x80, overlay.handY(), 0x1A2, palette, false, false);
        }
        for (Rainbow rainbow : overlay.rainbow()) {
            if (rainbow.mappingFrame() < 0) continue;
            staticPieces(out, 5, 67, false, Sonic2SpecialStageSpriteData.getRingPieces(rainbow.mappingFrame()),
                    rainbow.x(), rainbow.y(), ringBase, 3, false, false, "rainbow");
        }
    }

    private void phrase(List<Sprite> out, int phraseId) {
        int order = 68;
        for (MessageLetter letter : decodePhrase(phraseId)) {
            raw(out, 1, order++, false, MESSAGE_MAP, letter.mappingFrame(), letter.x(), letter.y(),
                    0x1A2, 2, false, false);
        }
    }

    public List<MessageLetter> decodePhrase(int phraseId) { return decodePhrase(data, phraseId); }

    /** Initial Obj5A_PrintPhrase positions, entirely decoded from this injected ROM. */
    public static List<MessageLetter> decodePhrase(CompetitiveHalfpipeRomData data, int phraseId) {
        if (phraseId < 8 || phraseId > 20 || (phraseId & 1) != 0) {
            throw new IllegalArgumentException("Invalid competitive phrase ID");
        }
        RomByteReader rom = data.rom();
        List<MessageLetter> result = new ArrayList<>();
        int cursor = PHRASE_TABLE + rom.readS16BE(PHRASE_TABLE + phraseId);
        int x = rom.readU8(PHRASE_X + (phraseId - 8) / 2);
        for (int words = 0; words < 8; words++) {
            int word = rom.readU8(cursor++);
            if (word == 0xFF) return List.copyOf(result);
            int text = WORD_TABLE + rom.readS16BE(WORD_TABLE + word);
            for (int characters = 0; characters < 24; characters++) {
                int frame = rom.readU8(text++);
                if (frame == 0xFF) break;
                result.add(new MessageLetter(frame, x, 0x48));
                x += 8;
            }
            x += 8;
        }
        throw new IllegalStateException("Unterminated native special-stage phrase");
    }

    private void raw(List<Sprite> out, int priority, int order, boolean shadow, int map, int frame,
                     int x, int y, int nativeBase, int palette, boolean flipX, boolean flipY) {
        for (Piece piece : frame(map, frame)) {
            int nativeTile = (nativeBase + (piece.word() & 0x7FF)) & 0x7FF;
            add(out, priority, order, shadow, x, y, piece.x(), piece.y(), piece.width(), piece.height(),
                    resolve(nativeTile, piece.width() * piece.height()), nativeTile,
                    (palette + (piece.word() >>> 13 & 3)) & 3,
                    (piece.word() & 0x800) != 0, (piece.word() & 0x1000) != 0, flipX, flipY,
                    (piece.word() & 0x8000) != 0, shadow ? "shadow" : "native-map");
        }
    }

    private List<Piece> frame(int map, int index) {
        if (index < 0 || index >= 64) throw new IllegalArgumentException("Invalid native mapping frame");
        long key = ((long) map << 8) | index;
        return mappingFrames.computeIfAbsent(key, unused -> {
            RomByteReader rom = data.rom();
            int cursor = map + rom.readS16BE(map + index * 2);
            int count = rom.readU16BE(cursor);
            if (count > 64) throw new IllegalStateException("Invalid native special-stage mapping piece count");
            List<Piece> pieces = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int p = cursor + 2 + i * 8;
                int size = rom.readU8(p + 1);
                pieces.add(new Piece(rom.readS16BE(p + 6), (byte) rom.readU8(p),
                        (size >>> 2 & 3) + 1, (size & 3) + 1, rom.readU16BE(p + 2)));
            }
            return List.copyOf(pieces);
        });
    }

    private int resolve(int nativeTile, int count) {
        for (Resident resident : residents) {
            if (nativeTile >= resident.nativeBase() && nativeTile + count <= resident.nativeBase() + resident.count()) {
                return resident.engineBase() + nativeTile - resident.nativeBase();
            }
        }
        throw new IllegalStateException("Native halfpipe sprite references unloaded ROM art: " + Integer.toHexString(nativeTile));
    }

    private void staticPieces(List<Sprite> out, int bucket, int order, boolean shadow,
                              Sonic2SpecialStageSpriteData.SpritePiece[] pieces,
                              int x, int y, int base, int palette, boolean flipX, boolean flipY, String name) {
        for (Sonic2SpecialStageSpriteData.SpritePiece piece : pieces) {
            add(out, bucket, order, shadow, x, y, piece.xOffset, piece.yOffset, piece.widthTiles, piece.heightTiles,
                    base + piece.tileIndex, piece.tileIndex, palette, piece.hFlip, piece.vFlip,
                    flipX, flipY, !shadow, name);
        }
    }

    private void add(List<Sprite> out, int bucket, int order, boolean shadow, int x, int y,
                     int dx, int dy, int width, int height, int firstPattern, int rawTile,
                     int palette, boolean pieceXFlip, boolean pieceYFlip, boolean objectXFlip,
                     boolean objectYFlip, boolean tilePriority, String name) {
        int drawX = viewport.innerH32OriginX() + x + (objectXFlip ? -dx - width * 8 : dx);
        int drawY = y + (objectYFlip ? -dy - height * 8 : dy);
        out.add(new Sprite(RenderPriority.bucket(bucket), order, shadow,
                new SpritePieceRenderer.PreparedPiece(drawX, drawY, width, height, firstPattern,
                        rawTile & 0x7FF, palette, pieceXFlip ^ objectXFlip, pieceYFlip ^ objectYFlip,
                        tilePriority, false, 0, height).withDebugSource(name)));
    }

    private static int u16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 8 | bytes[offset + 1] & 0xFF;
    }
}
