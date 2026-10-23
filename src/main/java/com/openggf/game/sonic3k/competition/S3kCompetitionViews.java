package com.openggf.game.sonic3k.competition;

import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.graphics.SpriteSatInternalAccess;
import com.openggf.graphics.color.DisplayColorConverter;
import com.openggf.level.NativeCompetitionRenderer;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.level.render.SpritePresentationRenderer;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.glClearColor;

/** GPU consumer of the domain's retained planes, physical SAT pages, and native split registers. */
public final class S3kCompetitionViews implements S3kCompetitionNativeHost.Views,
        RewindSnapshottable<S3kCompetitionViews.State>, AutoCloseable {
    public record State(S3kCompetitionViewRegisters.State registers, int backdropWord) {
        public State { Objects.requireNonNull(registers); }
    }

    private final GraphicsManager graphics;
    private final NativeCompetitionRenderer arena = new NativeCompetitionRenderer();
    private final S3kCompetitionMenuRenderer menus;
    private final S3kCompetitionViewRegisters registers = new S3kCompetitionViewRegisters();
    private GameplayModeContext context;
    private S3kCompetitionScroll scrollOwner;
    private int backdropWord;
    private boolean closed;

    public S3kCompetitionViews(GraphicsManager graphics) {
        this.graphics = Objects.requireNonNull(graphics);
        menus = new S3kCompetitionMenuRenderer(graphics);
    }

    @Override public void prepareNativeSpritePages(GameplayModeContext context,
            NativeCompetitionRoster roster, S3kCompetitionScroll scroll) {
        bind(context, scroll);
    }

    @Override public void publishNativeSpritePages(PlcLifecyclePhase phase, GameplayModeContext context) {
        var host = nativeHost(context);
        publishNativeSpritePages(phase, context, host.spritePages().uploaded());
    }

    @Override public void publishNativeSpritePages(PlcLifecyclePhase phase, GameplayModeContext context,
            S3kCompetitionSpritePages.Pair pair) {
        var host = nativeHost(context);
        bind(context, host.scroll());
        var p1 = scrollOwner.view(0);
        var p2 = scrollOwner.view(1);
        registers.publish(phase, p1.foregroundVScroll(), p1.backgroundVScroll(),
                p2.foregroundVScroll(), p2.backgroundVScroll(),
                Arrays.stream(scrollOwner.horizontalScrollBuffer()).boxed().toList());
        // The VInt boundary reads the current native CRAM bank before the next object/AnPal pass.
        // loc_60DE installs VDP register $8720 for all five race arenas.
        backdropWord = host.palettes().readCurrentColor(2, 0);
    }

    @Override public void drawArena(GameplayModeContext context, NativeCompetitionRoster roster,
            S3kCompetitionScroll scroll) {
        var host = nativeHost(context);
        drawArena(context, roster, scroll, host.planes(), host.spritePages().uploaded());
    }

    @Override public void drawArena(GameplayModeContext context, NativeCompetitionRoster roster,
            S3kCompetitionScroll scroll, S3kCompetitionPlanes planes, S3kCompetitionSpritePages.Pair pair) {
        requireOpen();
        if (this.context != context || scrollOwner != scroll || !registers.capture().published())
            throw new IllegalStateException("Native arena has no published VInt registers");
        var art = nativeHost(context).artProvider();
        var p1 = prepareFrame(graphics, art, pair.player1(), 0, 108);
        var p2 = prepareFrame(graphics, art, pair.player2(), 108, 116);
        var state = registers.capture();
        int[] horizontal = state.horizontalScroll().stream().mapToInt(Integer::intValue).toArray();
        int period = planes.widthTiles() * 8;
        arena.draw(context, List.of(
                new NativeCompetitionRenderer.View(roster.camera(0).capture(), 0, 108,
                        state.foreground1(), state.background1(), 0, period, horizontal, p1),
                new NativeCompetitionRenderer.View(roster.camera(1).capture(), 108, 116,
                        state.foreground2(), state.background2(), 0, period, horizontal, p2)), backdropRgb());
    }

    /** The DTO already contains native mapping addition, positions, flips, palette, and priority. */
    static SpritePresentation.Frame prepareFrame(GraphicsManager graphics, S3kCompetitionArtProvider art,
            S3kCompetitionSpritePages.Frame frame, int rasterStart, int rasterHeight) {
        return NativeCompetitionRenderer.prepareSprites(graphics, rasterStart, rasterHeight, () -> {
            for (var piece : frame.pieces()) {
                var mapping = piece.mapping();
                if (piece.maskPrefix()) {
                    // These raw offscreen SAT entries still consume table capacity and own native X masking.
                    var geometry = SpriteSatEntry.of(piece.screenX(), piece.screenY(), mapping.widthTiles(),
                            mapping.heightTiles(), 0, 0, false, false, false, false);
                    SpriteSatInternalAccess.submitGroups(graphics, List.of(geometry), () -> { });
                    continue;
                }
                var renderer = Objects.requireNonNull(art.getRenderer(piece.artKey()), "Unloaded native sprite bank");
                var sheet = Objects.requireNonNull(art.getSheet(piece.artKey()), "Missing native sprite mapping");
                int base = renderer.getPatternBase();
                // Freeze only the mapping's actual source slots; another ROM bank may share unused capacity.
                int end = Math.addExact(mapping.tileIndex(), mapping.widthTiles() * mapping.heightTiles());
                if (mapping.tileIndex() < 0 || end > sheet.getPatterns().length)
                    throw new IllegalStateException("Native sprite mapping exceeds its ROM art bank: " + piece.artKey());
                for (int tile = mapping.tileIndex(); tile < end; tile++)
                    SpritePresentation.bindPatternVersion(graphics, base + tile,
                            SpritePresentationRenderer.version(sheet.getPatterns()[tile]));
                SpritePieceRenderer.preparePiece(mapping, piece.screenX(), piece.screenY(), base,
                        -1, false, false, false, graphics::submitSpriteSatPiece);
            }
        });
    }

    private void bind(GameplayModeContext next, S3kCompetitionScroll scroll) {
        requireOpen();
        Objects.requireNonNull(next); Objects.requireNonNull(scroll);
        if (context == next && scrollOwner == scroll) return;
        context = next;
        scrollOwner = scroll;
        var p2 = scroll.view(1);
        registers.initialize(p2.foregroundVScroll(), p2.backgroundVScroll());
        backdropWord = 0;
        var registry = next.getRewindRegistry();
        registry.deregister(key());
        registry.register(this);
    }

    private static S3kCompetitionNativeHost nativeHost(GameplayModeContext context) {
        var roster = NativeCompetitionRoster.from(context.getWorldSession());
        if (roster instanceof S3kCompetitionNativeHost host) return host;
        throw new IllegalArgumentException("Native S3K view requires its actual world host");
    }

    private int backdropRgb() {
        int r = ((backdropWord >>> 1) & 7) * 255 / 7;
        int g = ((backdropWord >>> 5) & 7) * 255 / 7;
        int b = ((backdropWord >>> 9) & 7) * 255 / 7;
        return DisplayColorConverter.toPackedRgb(r, g, b,
                graphics.getDisplayColorProfile(), graphics.getDisplayColorExpansion());
    }

    @Override public void drawMenu(S3kCompetitionMenuScene.Scene scene) { requireOpen(); menus.draw(scene); }
    @Override public void setClearColor() { glClearColor(0, 0, 0, 1); }
    @Override public String key() { return "sonic3k.competition.native-views"; }
    @Override public State capture() { return new State(registers.capture(), backdropWord); }
    @Override public void restore(State state) { registers.restore(state.registers()); backdropWord = state.backdropWord(); }

    @Override public void closeArena() {
        // Host teardown closes the gameplay session before releasing its view resources.
        if (context != null && context.getRewindRegistry() != null) context.getRewindRegistry().deregister(key());
        context = null; scrollOwner = null; registers.resetForMissingSnapshot(); backdropWord = 0;
        arena.close();
    }

    private void requireOpen() { if (closed) throw new IllegalStateException("Native competition views have closed"); }
    @Override public void close() {
        if (closed) return;
        closeArena(); menus.close(); closed = true;
    }
}
