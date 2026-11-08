package com.openggf.game.sonic2.competition.view;

import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile;
import com.openggf.game.sonic2.competition.Sonic2CompetitionNativeArt;
import com.openggf.game.sonic2.competition.Sonic2NativeCompetitionHost;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.graphics.color.DisplayColorConverter;
import com.openggf.level.NativeCompetitionRenderer;
import com.openggf.level.Palette;
import com.openggf.level.render.SpritePresentationRenderer;
import java.util.List;
import java.util.Objects;

/** Progressive double-height presentation of the native world's completed semantic display pages. */
public final class Sonic2CompetitionViews implements Sonic2NativeCompetitionHost.Views, AutoCloseable {
    private final GraphicsManager graphics;
    private final NativeCompetitionRenderer renderer = new NativeCompetitionRenderer(448);
    private boolean closed;
    public Sonic2CompetitionViews(GraphicsManager graphics) { this.graphics = Objects.requireNonNull(graphics); }
    @Override public void prepare(GameplayModeContext context, Sonic2CompetitionSession session,
                                  Sonic2CompetitionLevelProfile profile, Sonic2CompetitionNativeArt art) {
        requireOpen();
        var world = world(context);
        world.prepareSprites(context, session, profile);
    }
    @Override public void draw(GameplayModeContext context, Sonic2CompetitionSession session) {
        requireOpen();
        var world = world(context);
        var state = world.registers().capture();
        if (!state.published()) throw new IllegalStateException("Native display has no completed level VInt");
        var fields = world.art().fields();
        fields.flush(graphics);
        publishPalette(state.paletteWords());
        var first = prepareFrame(fields, state.pages().first(), 0, 216);
        var second = prepareFrame(fields, state.pages().second(), 216, 232);
        int[] horizontal = new int[448];
        for (int y = 0; y < horizontal.length; y++) horizontal[y] = state.horizontal().get(y / 2);
        renderer.draw(context, List.of(
                new NativeCompetitionRenderer.View(state.firstCamera(), 0, 216,
                        state.foreground1(), state.background1(), 0, 512, horizontal, first,
                        world.planes().source(state.upperPlane())),
                new NativeCompetitionRenderer.View(state.secondCamera(), 216, 232,
                        state.foreground2(), state.background2(), 0, 512, horizontal, second,
                        world.planes().source(Sonic2CompetitionPlanes.PlaneId.FOREGROUND_P2))),
                backdropRgb(state.paletteWords().get(32)));
    }
    private SpritePresentation.Frame prepareFrame(Sonic2CompetitionFieldPatterns fields,
            Sonic2CompetitionSatBuilder.Page page, int start, int height) {
        var patterns = fields.live();
        return NativeCompetitionRenderer.prepareSprites(graphics, start, height, 448, () -> {
            for (var piece : Sonic2CompetitionFieldSprites.project(page)) {
                for (var fragment : piece.fragments())
                    SpritePresentation.bindPatternVersion(graphics, fragment.firstPatternIndex(),
                            SpritePresentationRenderer.version(patterns[fragment.firstPatternIndex()]));
                com.openggf.graphics.SpriteSatInternalAccess.submitGroups(graphics, List.of(piece.geometry()),
                        () -> piece.fragments().forEach(graphics::submitSpriteSatPiece));
            }
        });
    }
    private void publishPalette(List<Integer> words) {
        for (int line = 0; line < 4; line++) {
            var palette = new Palette();
            for (int color = 0; color < 16; color++) palette.getColor(color).fromSegaFormat(words.get(line * 16 + color));
            graphics.cachePaletteTexture(palette, line);
        }
    }
    private int backdropRgb(int word) {
        return DisplayColorConverter.toPackedRgb(((word >>> 1) & 7) * 255 / 7,
                ((word >>> 5) & 7) * 255 / 7, ((word >>> 9) & 7) * 255 / 7,
                graphics.getDisplayColorProfile(), graphics.getDisplayColorExpansion());
    }
    private static Sonic2CompetitionVideoFrame world(GameplayModeContext context) {
        return Objects.requireNonNull(context.getWorldSession().getGameModule()
                .getGameService(Sonic2CompetitionVideoFrame.class), "Native semantic display owner");
    }
    private void requireOpen() { if (closed) throw new IllegalStateException("Native views have closed"); }
    @Override public void close() { if (!closed) { renderer.close(); closed = true; } }
}
