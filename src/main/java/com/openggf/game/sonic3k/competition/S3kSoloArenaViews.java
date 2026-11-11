package com.openggf.game.sonic3k.competition;

import com.openggf.game.arena.GhostArtSource;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.GraphicsProjectionAccess;
import com.openggf.graphics.RenderProjection;
import com.openggf.graphics.SpritePresentation;
import com.openggf.graphics.SpriteSatInternalAccess;
import com.openggf.graphics.SpriteSatMaskPostProcessor;
import com.openggf.graphics.color.DisplayColorConverter;
import com.openggf.level.NativeCompetitionRenderer;
import com.openggf.level.Palette;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.ghost.GhostArtBankAllocator;
import com.openggf.sprites.managers.TailsTailPose;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.util.FboHelper;
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Hosted (Time Attack) arena presentation: P1's native window at twice the scale, with
 * main-game characters. The window is exactly the rows the ROM's P1 camera owns (camera Y
 * and {@value #WINDOW_ROWS} rows, P1's vertical scroll and deformation), and the central
 * half of its columns around the point where the native camera holds the player. Playable
 * bodies are drawn with the full-size art at one screen pixel per art pixel, at their
 * doubled window positions, behind high-priority tiles exactly where the miniature would be.
 * Native screen-space objects (TIME, lap, countdown) keep their full-screen placement.
 *
 * <p>Presentation only. The native split cameras, both 112-line sprite pages and their
 * visibility writeback keep running through the delegated native views.
 */
public final class S3kSoloArenaViews implements S3kCompetitionNativeHost.Views, AutoCloseable {
    /**
     * P1's camera window: the native pages range-test P1's sprites over 112 rows below its
     * camera (S3kCompetitionSpritePages.build), and P2's VScroll is offset by $70 for the lower half.
     */
    static final int WINDOW_ROWS = 0x70;
    /** Centre of the column band where MoveCameraX holds the player ($90..$A0). */
    static final int PLAYER_COLUMN = 0x98;
    /** Obj_Tails2P init loc_13166 (sonic3k.asm:25646-25650): the miniature standing y_radius, doubled on screen. */
    private static final int MINIATURE_STANDING_RADIUS = 0xB;
    /** Item icon under TIME (native TIME sits at screen 72,16), clear of the room's right column. */
    static final int ITEM_ICON_X = 18, ITEM_ICON_Y = 44;
    /** CompetitionHudObjectInstance ITEM_ICON: art key and native x/y $120/$E0, less the VDP bias. */
    private static final String ITEM_ICON_ART = "competition_item_icon";
    private static final int ITEM_ICON_NATIVE_X = 0x120 - 0x80, ITEM_ICON_NATIVE_Y = 0xE0 - 0x80;

    private final S3kCompetitionViews nativeViews;
    private final NativeCompetitionRenderer renderer = new NativeCompetitionRenderer();
    private final GraphicsManager graphics;
    private final Map<S3kCompetitionPlayer.Character, PlayerSpriteRenderer> actors =
            new EnumMap<>(S3kCompetitionPlayer.Character.class);
    private PlayerSpriteRenderer tail;
    private GameplayModeContext actorContext;
    private FboHelper.FboHandle window, composite, mask;
    private int compositeWidth;
    private int priorityAlias;

    public S3kSoloArenaViews(GraphicsManager graphics) {
        this.graphics = graphics;
        nativeViews = new S3kCompetitionViews(graphics);
    }
    @Override public void prepareNativeSpritePages(GameplayModeContext context,NativeCompetitionRoster roster,S3kCompetitionScroll scroll) {
        nativeViews.prepareNativeSpritePages(context,roster,scroll);
    }
    @Override public void publishNativeSpritePages(PlcLifecyclePhase phase,GameplayModeContext context) {
        nativeViews.publishNativeSpritePages(phase,context);
        retain(phase, context);
    }
    @Override public void publishNativeSpritePages(PlcLifecyclePhase phase,GameplayModeContext context,S3kCompetitionSpritePages.Pair pair) {
        nativeViews.publishNativeSpritePages(phase,context,pair);
        retain(phase, context);
    }

    /** One full-size draw: a body (art frame) or Tails' tail (tail frame), at window pixels plus the ground drop. */
    record Body(int bucket, S3kCompetitionPlayer.Character character, boolean tail, int frame, boolean hFlip,
                boolean vFlip, boolean highPriority, int windowX, int windowY, int dropX, int dropY) { }
    /**
     * The zoom's VInt image, taken where the native views publish P1's registers and page, so the
     * zoom shows the same frame as the split view (the display trails the logic by one VInt).
     */
    record Published(S3kCompetitionSpritePages.Frame world, S3kCompetitionSpritePages.Frame screen, List<Body> bodies,
                     com.openggf.game.rewind.snapshot.CameraSnapshot camera, int foreground, int background,
                     int[] horizontal, int originX, int originY, S3kCompetitionViewRegisters.State registers) { }
    private Published published;

    private void retain(PlcLifecyclePhase phase, GameplayModeContext context) {
        if (!(NativeCompetitionRoster.from(context.getWorldSession()) instanceof S3kCompetitionNativeHost host)) return;
        if (phase == PlcLifecyclePhase.LAG && published != null) {
            // VInt_0_NoWater republishes the retained page and Hscroll on lag; keep that image.
            var p = published;
            published = new Published(p.world(), p.screen(), p.bodies(), p.camera(), p.foreground(), p.background(),
                    p.horizontal(), p.originX(), p.originY(), nativeViews.capture().registers());
            return;
        }
        published = image(context, host);
    }

    private Published image(GameplayModeContext context, S3kCompetitionNativeHost host) {
        var scroll = host.scroll();
        var view = scroll.view(0);
        var zoomed = host.spritePages().projectZoomed(context.getObjectManager(), host.players(), scroll, WINDOW_ROWS);
        var poses = host.fullSizeArt().poses();
        var bodies = new ArrayList<Body>();
        for (var actor : zoomed.actors()) {
            var player = actor.player();
            int[] drop = groundOffset(player);
            if (actor.tail()) {
                // Obj_Tails_Tail selection (loc_16266, sonic3k.asm:30188-30196): pushing forces parent anim 4.
                int parent = (player.status() & 0x20) != 0 && player.animation() != 5 ? 4 : player.animation();
                var pose = TailsTailPose.sample(true, parent, host.session().levelFrameCounter() & 0xFFFF,
                        player.facingLeft(), (short) player.xVelocity() / 256.0, (short) player.yVelocity() / 256.0);
                if (pose != null) bodies.add(new Body(actor.bucket(), player.character(), true, pose.mappingFrame(),
                        pose.flipX(), pose.flipY(), player.highPriority(), actor.windowX(), actor.windowY(), drop[0], drop[1]));
                continue;
            }
            bodies.add(new Body(actor.bucket(), player.character(), false,
                    poses.frame(player.character(), player.animation(), player.flipAngle() != 0, player.mappingFrame()),
                    (player.renderFlips() & 1) != 0, (player.renderFlips() & 2) != 0, player.highPriority(),
                    actor.windowX(), actor.windowY(), drop[0], drop[1]));
        }
        var lead = host.session().player(0);
        return new Published(zoomed.world(), zoomed.screen(), List.copyOf(bodies), host.camera(0).capture(),
                view.foregroundVScroll(), view.backgroundVScroll(), scroll.projectSoloHorizontalScroll(view.camera().y()),
                projectedOrigin(lead.centreX(), view.camera().x(), scroll.profile().xWrapMask()),
                projectedOrigin(lead.centreY(), view.camera().y(), scroll.profile().yWrapMask()),
                nativeViews.capture().registers());
    }

    /** The published image; a draw before any publication, or after a rewind restore, projects the live state. */
    private Published current(GameplayModeContext context, S3kCompetitionNativeHost host) {
        if (published != null && published.registers().equals(nativeViews.capture().registers())) return published;
        return image(context, host);
    }

    /** Drops the VInt image, so the next draw projects the live state (as after a rewind restore). */
    void discardPublishedImage() { published = null; }

    /** Displayed world columns: half the logical width, twice magnified. */
    static int windowColumns(int logicalWidth) { return logicalWidth / 2; }
    static int windowLeft(int logicalWidth) { return PLAYER_COLUMN - windowColumns(logicalWidth) / 2; }

    private static int projectedOrigin(int player, int camera, int mask) {
        // The loop image of S3kCompetitionSpritePages' 12px player source: the mod places ghosts nearest P1.
        return player - (((player - camera + 12) & mask) - 12);
    }

    @Override public void drawArena(GameplayModeContext context,NativeCompetitionRoster roster,S3kCompetitionScroll scroll) {
        var host = (S3kCompetitionNativeHost) roster;
        int width = graphics.getProjectionWidth();
        var image = current(context, host);
        var world = S3kCompetitionViews.prepareFrame(graphics, host.artProvider(), image.world(), 0, WINDOW_ROWS);
        var overlay = prepareOverlay(context, host, image, width);
        var nativeView = new NativeCompetitionRenderer.View(image.camera(), 0, WINDOW_ROWS,
                image.foreground(), image.background(), 0, host.planes().widthTiles() * 8, image.horizontal(), world);
        compose(context, nativeView, overlay, backdropRgb(), width);
    }

    /** Read-only overlay preparation, inspectable without drawing or native visibility writes. */
    public SpritePresentation.Frame prepareOverlay(GameplayModeContext context, S3kCompetitionNativeHost host) {
        return prepareOverlay(context, host, current(context, host), graphics.getProjectionWidth());
    }
    /** The delegated native split views, which keep receiving every publication. */
    S3kCompetitionViews nativeViews() { return nativeViews; }

    /** HUD pieces, the admitted bodies and the ghosts, in native SAT order, at screen scale. */
    private SpritePresentation.Frame prepareOverlay(GameplayModeContext context, S3kCompetitionNativeHost host,
            Published image, int width) {
        bindActorBanks(context, host);
        int left = windowLeft(width), hudX = (width - NativeCompetitionRenderer.WIDTH) / 2;
        boolean items = host.session().config().itemsEnabled();
        var art = host.artProvider();
        IntConsumer ghosts = bucket -> {
            context.getGhostRenderRegistry().renderForLayer(bucket, false);
            context.getGhostRenderRegistry().renderForLayer(bucket, true);
        };
        return SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
            SpriteSatInternalAccess.beginCollection(graphics, SpriteSatMaskPostProcessor.Raster.extended(0, 0, width, 224));
            for (int bucket = 0; bucket < 8; bucket++) {
                for (var piece : image.screen().pieces()) {
                    if (piece.bucket() != bucket || piece.maskPrefix()) continue;
                    int x = piece.screenX() + hudX, y = piece.screenY();
                    if (ITEM_ICON_ART.equals(piece.artKey())) {
                        // The ROM centres it in P1's band, mid-screen once magnified; items off: no icon.
                        if (!items) continue;
                        x += ITEM_ICON_X - ITEM_ICON_NATIVE_X;
                        y += ITEM_ICON_Y - ITEM_ICON_NATIVE_Y;
                    }
                    submitPiece(art, piece, x, y);
                }
                // The local body is emitted before the ghosts sharing its bucket, so it stays in front.
                // Tails' tail follows its body in native slot order, so it stays behind.
                for (var body : image.bodies()) {
                    if (body.bucket() != bucket) continue;
                    var renderer = body.tail() ? tail : actors.get(body.character());
                    graphics.setCurrentSpriteHighPriority(body.highPriority());
                    renderer.drawFrame(body.frame(), 2 * (body.windowX() - left) + body.dropX(),
                            2 * body.windowY() + body.dropY(), body.hFlip(), body.vFlip());
                }
                graphics.setCurrentSpriteHighPriority(false);
                ghosts.accept(bucket);
            }
            graphics.setCurrentSpriteHighPriority(false);
            graphics.endSpriteSatCollectionAndReplay();
        });
    }

    /**
     * The doubled miniature centre sits 2 x $B above its feet; the main art's centre sits its
     * own standing y_radius above them (Sonic/Knuckles $13, Tails $F: Obj_Sonic/Obj_Tails/
     * Obj_Knuckles init, sonic3k.asm:21940,26143,30391). Shift the body by the difference
     * toward the current ground, so feet meet the doubled terrain. Rolling radii already agree
     * (miniature 7, main $E).
     */
    static int[] groundOffset(S3kCompetitionPlayer player) {
        return drop(player.character(), player.rolling(), -player.angle());
    }

    /** {@code theta} is the negated terrain angle (256 steps), so (sin, cos) points at the ground. */
    static int[] drop(S3kCompetitionPlayer.Character character, boolean curled, int theta) {
        if (curled) return new int[] {0, 0};
        int main = character == S3kCompetitionPlayer.Character.TAILS ? 0xF : 0x13;
        int drop = 2 * MINIATURE_STANDING_RADIUS - main;
        double radians = (theta & 255) * Math.PI / 128;
        return new int[] {(int) Math.round(drop * Math.sin(radians)), (int) Math.round(drop * Math.cos(radians))};
    }

    /**
     * A ghost carries no angle, but its walk/run frame and flips are the animator's own output:
     * group g and flips F come from (angle + $10) as in loc_12DDA..loc_12E0A (sonic3k.asm:25383-25414),
     * so the negated angle is +/-(g x $20, plus $80 when vertically flipped), negative when facing left.
     */
    static int ghostTheta(int group, boolean hFlip, boolean vFlip) {
        if (group < 0) return 0;
        int base = (vFlip ? 0x80 : 0) + group * 0x20;
        return hFlip == vFlip ? base : -base;
    }

    private void submitPiece(S3kCompetitionArtProvider art, S3kCompetitionSpritePages.Piece piece, int x, int y) {
        var mapping = piece.mapping();
        var renderer = java.util.Objects.requireNonNull(art.getRenderer(piece.artKey()), "Unloaded native sprite bank");
        var sheet = java.util.Objects.requireNonNull(art.getSheet(piece.artKey()), "Missing native sprite mapping");
        int base = renderer.getPatternBase();
        int end = Math.addExact(mapping.tileIndex(), mapping.widthTiles() * mapping.heightTiles());
        if (mapping.tileIndex() < 0 || end > sheet.getPatterns().length)
            throw new IllegalStateException("Native sprite mapping exceeds its ROM art bank: " + piece.artKey());
        for (int tile = mapping.tileIndex(); tile < end; tile++)
            SpritePresentation.bindPatternVersion(graphics, base + tile,
                    SpritePresentationRenderer.version(sheet.getPatterns()[tile]));
        com.openggf.level.render.SpritePieceRenderer.preparePiece(mapping, x, y, base,
                -1, false, false, false, graphics::submitSpriteSatPiece);
    }

    /** One private DPLC bank per body character, reserved in the arena's own pattern range. */
    private void bindActorBanks(GameplayModeContext context, S3kCompetitionNativeHost host) {
        if (actorContext != context) { actors.clear(); tail = null; actorContext = context; }
        for (var player : host.players()) {
            if (player.character() == S3kCompetitionPlayer.Character.TAILS && tail == null) {
                SpriteArtSet art = host.fullSizeArt().tailsTail();
                int base = context.getLevelManager().reserveSidekickPatternBank(art.bankSize());
                tail = new PlayerSpriteRenderer(GhostArtBankAllocator.shiftToGhostBank(art, base), graphics);
            }
            actors.computeIfAbsent(player.character(), character -> {
                SpriteArtSet art = host.fullSizeArt().art(character);
                int base = context.getLevelManager().reserveSidekickPatternBank(art.bankSize());
                return new PlayerSpriteRenderer(GhostArtBankAllocator.shiftToGhostBank(art, base), graphics);
            });
        }
    }

    /** Ghosts draw the same full-size art and poses, at their doubled positions. */
    public GhostArtSource ghostArtSource(S3kCompetitionNativeHost host) {
        return new GhostArtSource() {
            @Override public SpriteArtSet art(String character) { return host.fullSizeArt().art(character(character)); }
            @Override public Palette palette(String character) {
                try { return host.assets().arenaPalette(host.session().arena())[S3kArenaFullSizeArt.paletteLine(character(character))]; }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
            }
            @Override public int screenX(int worldX) {
                var image = current(host.world().context(), host);
                return 2 * (worldX - image.originX() - windowLeft(graphics.getProjectionWidth()));
            }
            @Override public int screenY(int worldY) {
                return 2 * (worldY - current(host.world().context(), host).originY());
            }
            @Override public int frameScreenX(String character, com.openggf.ghost.GhostFrame frame) {
                return screenX(frame.x()) + ghostDrop(character, frame)[0];
            }
            @Override public int frameScreenY(String character, com.openggf.ghost.GhostFrame frame) {
                return screenY(frame.y()) + ghostDrop(character, frame)[1];
            }
            private int[] ghostDrop(String code, com.openggf.ghost.GhostFrame frame) {
                var poses = host.fullSizeArt().poses();
                var character = character(code);
                return drop(character, poses.ball(character, frame.mappingFrame()),
                        ghostTheta(poses.angleGroup(character, frame.mappingFrame()), frame.hFlip(), frame.vFlip()));
            }
            @Override public int artFrame(String character, int streamedFrame) {
                return host.fullSizeArt().poses().frame(character(character), streamedFrame);
            }
        };
    }
    private static S3kCompetitionPlayer.Character character(String code) {
        return S3kCompetitionPlayer.Character.valueOf(code.toUpperCase(Locale.ROOT));
    }

    private int backdropRgb() {
        int word = nativeViews.capture().backdropWord();
        return DisplayColorConverter.toPackedRgb(((word>>>1)&7)*255/7,((word>>>5)&7)*255/7,
                ((word>>>9)&7)*255/7,graphics.getDisplayColorProfile(),graphics.getDisplayColorExpansion());
    }

    /** GPU composition; no gameplay, camera, palette or animation owner is advanced here. */
    private void compose(GameplayModeContext context, NativeCompetitionRenderer.View nativeView,
            SpritePresentation.Frame overlay, int backdropRgb, int width) {
        if (!graphics.isGlInitialized()) throw new IllegalStateException("Arena zoom requires an owned GL context");
        graphics.flushPatternBatch();
        graphics.flush();
        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        int drawFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), readFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int gx = graphics.getViewportX(), gy = graphics.getViewportY();
        int gw = graphics.getViewportWidth(), gh = graphics.getViewportHeight();
        try {
            ensureTargets(width);
            int columns = windowColumns(width), left = windowLeft(width), height = NativeCompetitionRenderer.HEIGHT;
            // 1. The native P1 window, through the same renderer as the split view.
            glBindFramebuffer(GL_FRAMEBUFFER, window.fboId());
            glViewport(0, 0, NativeCompetitionRenderer.WIDTH, height);
            renderer.draw(context, List.of(nativeView), backdropRgb);
            // 2. Its central columns, magnified twice with nearest sampling.
            glBindFramebuffer(GL_READ_FRAMEBUFFER, window.fboId());
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, composite.fboId());
            glBlitFramebuffer(left, height - WINDOW_ROWS, left + columns, height, 0, 0, width, height,
                    GL_COLOR_BUFFER_BIT, GL_NEAREST);
            magnifyPriorityMask(left, columns, width);
            // 3. Screen-scale bodies, ghosts and HUD over the magnified window.
            glBindFramebuffer(GL_FRAMEBUFFER, composite.fboId());
            glViewport(0, 0, width, height);
            float[] matrix = new Matrix4f().ortho2D(0, width, 0, height).get(new float[16]);
            RenderProjection projection = new RenderProjection() {
                @Override public float[] getProjectionMatrixBuffer() { return matrix; }
                @Override public boolean isFBOProjectionActive() { return true; }
                @Override public int getCurrentDisplayHeight() { return height; }
            };
            try (var ignored = GraphicsProjectionAccess.openProjection(graphics, projection)) {
                graphics.setViewport(0, 0, width, height);
                graphics.executeCapturedCommands(() -> SpritePresentationRenderer.drawNative(graphics, overlay, 0, 0,
                        layer -> true), 0, 0, width, height);
            }
            // 4. Present at the caller's viewport.
            glBindFramebuffer(GL_READ_FRAMEBUFFER, composite.fboId());
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFbo);
            glBlitFramebuffer(0, 0, width, height, viewport[0], viewport[1], viewport[0] + viewport[2],
                    viewport[1] + viewport[3], GL_COLOR_BUFFER_BIT, GL_NEAREST);
        } finally {
            graphics.setViewport(gx, gy, gw, gh);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFbo);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    /**
     * The window pass left P1's high-priority tile mask in the shared priority texture. Replace
     * it with the same magnified region, so screen-scale bodies are hidden where the miniature
     * was. The texture is rebuilt by the next tile pass.
     */
    private void magnifyPriorityMask(int left, int columns, int width) {
        var tiles = graphics.getTilePriorityFBO();
        if (tiles == null || !tiles.isInitialized()) return;
        int tw = tiles.getWidth(), th = tiles.getHeight(), height = NativeCompetitionRenderer.HEIGHT;
        glBindFramebuffer(GL_FRAMEBUFFER, priorityAlias);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tiles.getTextureId(), 0);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, priorityAlias);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, mask.fboId());
        glBlitFramebuffer(left * tw / NativeCompetitionRenderer.WIDTH, (height - WINDOW_ROWS) * th / height,
                (left + columns) * tw / NativeCompetitionRenderer.WIDTH, th, 0, 0, width, height,
                GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, mask.fboId());
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, priorityAlias);
        glBlitFramebuffer(0, 0, width, height, 0, 0, tw, th, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, priorityAlias);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
    }

    private void ensureTargets(int width) {
        if (window != null && compositeWidth == width) return;
        releaseTargets();
        int active = glGetInteger(GL_ACTIVE_TEXTURE), texture = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            window = FboHelper.createColorOnly(NativeCompetitionRenderer.WIDTH, NativeCompetitionRenderer.HEIGHT, GL_CLAMP_TO_EDGE);
            composite = FboHelper.createColorOnly(width, NativeCompetitionRenderer.HEIGHT, GL_CLAMP_TO_EDGE);
            mask = FboHelper.createColorOnly(width, NativeCompetitionRenderer.HEIGHT, GL_CLAMP_TO_EDGE);
            priorityAlias = glGenFramebuffers();
            if (window == null || composite == null || mask == null) throw new IllegalStateException("Cannot create arena zoom framebuffers");
            compositeWidth = width;
        } finally {
            glActiveTexture(active);
            glBindTexture(GL_TEXTURE_2D, texture);
        }
    }

    private void releaseTargets() {
        FboHelper.destroy(window); FboHelper.destroy(composite); FboHelper.destroy(mask);
        if (priorityAlias != 0) glDeleteFramebuffers(priorityAlias);
        window = composite = mask = null; priorityAlias = 0; compositeWidth = 0;
    }

    @Override public void drawMenu(S3kCompetitionMenuScene.Scene scene) { nativeViews.drawMenu(scene); }
    @Override public void closeArena() {
        nativeViews.closeArena(); renderer.close(); actors.clear(); tail = null; actorContext = null; published = null;
        if (graphics.isGlInitialized()) releaseTargets();
    }
    @Override public void setClearColor() { nativeViews.setClearColor(); }
    @Override public void close() { closeArena(); nativeViews.close(); }
}
