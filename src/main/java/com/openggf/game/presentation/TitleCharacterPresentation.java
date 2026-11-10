package com.openggf.game.presentation;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.*;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Palette;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import java.util.EnumMap;
import java.util.logging.Logger;

/** Title selection reads launch configuration, never the previous gameplay session. */
public final class TitleCharacterPresentation {
    private static final Logger LOG = Logger.getLogger(TitleCharacterPresentation.class.getName());
    private final CharacterPresentation.TitleMascot mascot;
    private final int roleBankOffset;
    private RenderContext context;
    private final EnumMap<CharacterPresentation.TitlePart, PlayerSpriteRenderer> renderers =
            new EnumMap<>(CharacterPresentation.TitlePart.class);
    private final java.util.Map<Integer, byte[]> masks = new java.util.HashMap<>();
    private final java.util.Map<Integer, byte[]> clearedPixels = new java.util.HashMap<>();
    private final java.util.Map<Integer, Integer> planeTiles = new java.util.HashMap<>();
    private int residentPlaneFrame = -1;
    TitleCharacterPresentation(CharacterPresentation.TitleMascot mascot, boolean sidekick) {
        roleBankOffset = sidekick ? 0x8000 : 0;
        this.mascot = mascot;
        mascot.planeMasks().forEach((frame, mask) -> {
            masks.put(frame,mask.pixels());
            clearedPixels.put(frame,mask.clearedPixels());
        });
    }

    public static CharacterPresentation.TitleMascot resolve(CharacterPresentation.Host host,
            String code, PlayableCharacterRegistry registry) throws java.io.IOException {
        if (code == null || code.isBlank() || code.equals("none")) return null;
        var key = CharacterKey.parsePersisted(code);
        if (key.isBuiltin()) return null;
        var definition = registry.find(key).orElse(null);
        if (definition == null || definition.presentationSupplier() == null) return null;
        var presentation = definition.presentationSupplier().load(code, switch(host) {
            case S1 -> GameId.S1; case S2 -> GameId.S2; case S3K -> GameId.S3K;
        });
        return presentation == null ? null : presentation.titleMascots().get(host);
    }

    public static TitleCharacterPresentation configured(CharacterPresentation.Host host, boolean sidekick) {
        try {
            var world = com.openggf.game.session.SessionManager.getCurrentWorldSession();
            if (world == null) return null;
            var module = world.getGameModule();
            if (module == null) return null;
            String code = GameServices.configuration().getString(sidekick
                    ? SonicConfiguration.SIDEKICK_CHARACTER_CODE : SonicConfiguration.MAIN_CHARACTER_CODE);
            var mascot = resolve(host, code, module.getPlayableCharacterRegistry());
            return mascot == null ? null : new TitleCharacterPresentation(mascot, sidekick);
        } catch (java.io.IOException failure) {
            LOG.warning("Title mascot retained native fallback: " + failure.getMessage());
            return null;
        }
    }

    public boolean owns(CharacterPresentation.TitlePart part, int selector) {
        var set = mascot.parts().get(part);
        return set != null && set.frames().containsKey(selector);
    }

    public boolean hasPlane(int frame) {
        return mascot.planeMasks().containsKey(frame)
                && owns(CharacterPresentation.TitlePart.PLANE_LEFT, frame)
                && owns(CharacterPresentation.TitlePart.PLANE_RIGHT, frame);
    }

    /** Per-cell copies avoid modifying a shared native tile used elsewhere in the logo. */
    public int maskedTile(int frame, int cell, int word, com.openggf.level.Pattern[] nativeArt) {
        if (!hasPlane(frame)) return -1;
        byte[] pixels = masks.get(frame);
        if (pixels == null) return -1;
        if (residentPlaneFrame != frame) { planeTiles.clear(); residentPlaneFrame = frame; }
        var resident = planeTiles.get(cell);
        if (resident != null) return resident;
        var pattern = maskedPattern(pixels, clearedPixels.get(frame), cell, word,
                nativeArt[word & 0x7ff]);
        if (pattern == null) { planeTiles.put(cell,-1); return -1; }
        int id=com.openggf.graphics.PatternAtlasRange.S3K_TITLE_SCREEN_ANIMATION.base()+0x4000+cell;
        GameServices.graphics().cachePatternTexture(pattern,id);
        planeTiles.put(cell,id);
        return id;
    }

    /** Replace owned screen pixels in a private tile; native flips still apply at draw time. */
    static com.openggf.level.Pattern maskedPattern(byte[] pixels, byte[] replacements,
            int cell, int word, com.openggf.level.Pattern source) {
        int sx = cell % 40 * 8, sy = cell / 40 * 8;
        boolean changed = false;
        var pattern = new com.openggf.level.Pattern();
        for (int y=0;y<8;y++) for (int x=0;x<8;x++) {
            boolean clear = pixels[(sy+y)*320+sx+x] != 0;
            changed |= clear;
            int px = (word & 0x800) != 0 ? 7-x : x;
            int py = (word & 0x1000) != 0 ? 7-y : y;
            pattern.setPixel(px,py,clear ? replacements[(sy+y)*320+sx+x] : source.getPixel(px,py));
        }
        return changed ? pattern : null;
    }

    public void drawPlane(int frame, int x, int y) {
        draw(CharacterPresentation.TitlePart.PLANE_LEFT,frame,x,y);
        draw(CharacterPresentation.TitlePart.PLANE_RIGHT,frame,x+160,y);
    }

    /** Returns false without emitting anything for an unowned selector. */
    public boolean draw(CharacterPresentation.TitlePart part, int selector, int x, int y) {
        var set = mascot.parts().get(part);
        if (set == null || !set.frames().containsKey(selector)) return false;
        var renderer = renderers.computeIfAbsent(part, ignored -> {
            var source = StageSpriteCompiler.compile(set, null);
            // Each native part and team role owns residency until the next title entry.
            // A deferred body draw must never sample a later hand/sidekick upload.
            int base = com.openggf.graphics.PatternAtlasRange.CHARACTER_TITLE_PRESENTATION.base()
                    + roleBankOffset + part.ordinal()*0x1000;
            var art = new com.openggf.sprites.art.SpriteArtSet(source.artTiles(), source.mappingFrames(),
                    source.dplcFrames(), source.paletteIndex(), base, source.frameDelay(), source.bankSize(),
                    source.animationProfile(), source.animationSet());
            var r = new PlayerSpriteRenderer(art);
            if (context == null) context = RenderContext.createSidekickContext(GameServices.module().getGameId());
            var palette = new Palette(); int[] rgb = mascot.rgb();
            for (int i=0;i<16;i++) {
                var c=palette.getColor(i); c.r=(byte)(rgb[i]>>16); c.g=(byte)(rgb[i]>>8); c.b=(byte)rgb[i];
            }
            context.setPalette(r.paletteLine(), palette); r.setRenderContext(context);
            GameServices.graphics().cachePaletteTexture(palette, context.getEffectivePaletteLine(r.paletteLine()));
            return r;
        });
        renderer.drawFrame(selector,x,y,false,false);
        return true;
    }
}
