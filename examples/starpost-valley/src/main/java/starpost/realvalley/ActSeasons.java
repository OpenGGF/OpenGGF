package starpost.realvalley;

import com.openggf.game.modzone.*;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteWrite;
import com.openggf.game.render.*;
import com.openggf.level.Palette;
import com.openggf.mods.ui.LevelOverlayCanvas;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import starpost.art.Tone;
import starpost.core.Game;
import starpost.realtown.LevelPictureCanvas;
import starpost.scene.WorldWeather;

/** Seasons own only the encoder's terrain claims; characters, HUD and Ruins retain their palettes. */
public final class ActSeasons {
    private Game game;
    private PaletteOwnershipRegistry registry;
    private final Map<String,List<ModPaletteClaim>> sources = new HashMap<>();

    /** The admitted native director supplies its live dependency, never a global engine lookup. */
    public void bind(PaletteOwnershipRegistry registry, Game game) {
        this.registry = registry; this.game = game;
    }

    /** Load-time ROM colours, before any seasonal writes. Never sample the already tinted live palette. */
    public void remember(String zone, List<ModPaletteClaim> claims) {
        sources.put(zone, List.copyOf(claims));
    }

    public ModZoneRuntimeServices create(ModZoneRuntimeContext context) {
        List<ModPaletteClaim> claims = sources.getOrDefault(context.destination().localName(), List.of());
        registry = null; game = null;
        var images = LevelPictureCanvas.cache();
        return ModZoneRuntimeServices.builder()
            .paletteAnimation(() -> {
                Game current = game;
                if (current != null && registry != null) submit(registry, claims, current);
            })
            .renderEffect(new SpecialRenderEffect() {
                public SpecialRenderEffectStage stage() { return SpecialRenderEffectStage.AFTER_SPRITES; }
                public void render(SpecialRenderEffectContext frame) {
                    Game current = game;
                    if (current == null) return;
                    var canvas = new LevelPictureCanvas(new LevelOverlayCanvas(frame.graphicsManager(),
                        frame.camera().getWidth(), frame.camera().getHeight()), images);
                    WorldWeather.draw(current, frame.frameCounter(), canvas);
                }
            }).build();
    }

    /** Stateless per-frame derivation makes restored calendar/weather authoritative immediately. */
    public static void submit(PaletteOwnershipRegistry registry, List<ModPaletteClaim> claims, Game game) {
        Tone tone = new Tone(game.calendar.season());
        for (ModPaletteClaim claim : claims) {
            int word = colour(claim.segaColor(), tone, game.calendar.light(), game.raining);
            registry.submit(PaletteWrite.normal("starpost-valley:seasons", 20, claim.line(), claim.color(),
                new byte[] {(byte)(word >>> 8), (byte)word}));
        }
    }

    /** Same nine-bit Tone palette map as the scene; rainy daylight uses its cool night tint. */
    public static int colour(int source, Tone tone, int light, boolean rain) {
        Palette.Color color = new Palette.Color();
        color.fromSegaFormat(source);
        int rgb = tone.apply(0xFF000000 | (color.r & 255) << 16 | (color.g & 255) << 8 | color.b & 255);
        rgb = Tone.sky(rgb, light);
        if (rain && light == 0) {
            rgb = Tone.genesis(0xFF000000 | (rgb >>> 16 & 255) * 3 / 4 << 16
                | (rgb >>> 8 & 255) * 3 / 4 << 8 | (rgb & 255));
        }
        return Math.round((rgb >>> 16 & 255) * 7 / 255f) << 1
            | Math.round((rgb >>> 8 & 255) * 7 / 255f) << 5
            | Math.round((rgb & 255) * 7 / 255f) << 9;
    }
}
