package com.openggf.game.presentation;

import com.openggf.game.*;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.*;
import java.io.IOException;
import java.util.*;

/** Derived screen sprites; native owners still select every frame and tick. */
public final class ScreenFrameRenderer {
    private final Map<String, Map<Integer, Integer>> indices = new HashMap<>();
    private final PatternSpriteRenderer renderer;
    public static PresentationFrameSet active(CharacterPresentation.Host host) throws IOException {
        return active(host, StageCharacterPresentation.activeDefinition());
    }
    public static PresentationFrameSet active(CharacterPresentation.Host host, CharacterDefinition definition) throws IOException {
        var presentation = definition == null || definition.presentationSupplier() == null ? null
                : definition.presentationSupplier().load(definition.key().persisted(), GameId.valueOf(host.name()));
        var art = presentation == null ? null : presentation.art().get(host);
        return art == null ? null : art.screenFrames();
    }
    public ScreenFrameRenderer(PresentationFrameSet set, GraphicsManager graphics, int base, int line) {
        this(set,graphics,base,line,true);
    }
    public ScreenFrameRenderer(PresentationFrameSet set, GraphicsManager graphics, int base, int line, boolean uploadPalette) {
        var patterns = new ArrayList<Pattern>();
        var frames = new ArrayList<SpriteMappingFrame>();
        set.banks().forEach((bank, poses) -> {
            var ids = new HashMap<Integer, Integer>();
            poses.forEach((id, pose) -> {
                ids.put(id, frames.size());
                frames.add(mapping(pose, patterns.size(), line));
                patterns.addAll(Arrays.asList(CharacterNameComposer.tiles(pose.image())));
            });
            indices.put(bank, ids);
        });
        renderer = new PatternSpriteRenderer(new ObjectSpriteSheet(patterns.toArray(Pattern[]::new), frames, 0, 1), graphics);
        renderer.ensurePatternsCached(graphics, base);
        if (uploadPalette) graphics.cachePaletteTexture(palette(set), line);
    }
    public static ScreenFrameRenderer privatePalette(PresentationFrameSet set, GraphicsManager graphics, int base) {
        var context = com.openggf.graphics.RenderContext.createSidekickContext(GameServices.module().getGameId());
        context.setPalette(0, palette(set));
        return new ScreenFrameRenderer(set, graphics, base, context.getEffectivePaletteLine(0));
    }
    public static Palette palette(PresentationFrameSet set) {
        var palette = new Palette();
        int[] rgb = set.rgb();
        for (int i=0;i<16;i++) {
            palette.colors[i].r=(byte)(rgb[i] >> 16);
            palette.colors[i].g=(byte)(rgb[i] >> 8);
            palette.colors[i].b=(byte)rgb[i];
        }
        return palette;
    }
    public static SpriteMappingFrame mapping(PresentationFrameSet.Pose pose, int tile, int line) {
        var pieces = new ArrayList<SpriteMappingPiece>();
        int rows=(pose.image().height()+7)/8, columns=(pose.image().width()+7)/8;
        for(int x=0;x<columns;x++) for(int y=0;y<rows;y++)
            pieces.add(new SpriteMappingPiece(x*8-pose.hotspotX(), y*8-pose.hotspotY(), 1,1,
                    tile+x*rows+y, false,false,line,true));
        return new SpriteMappingFrame(pieces);
    }
    public boolean draw(String bank, int frame, int x, int y, boolean flip) {
        Integer index=indices.getOrDefault(bank,Map.of()).get(frame);
        if(index==null) return false;
        renderer.drawFrameIndex(index,x,y,flip,false);
        return true;
    }
}
