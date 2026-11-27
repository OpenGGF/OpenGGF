package com.openggf.level;

import com.openggf.game.ObjectArtProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.HudProfileAccess;
import com.openggf.level.objects.HudRenderManager;
import com.openggf.level.objects.HudStaticArt;

import java.util.logging.Logger;

/**
 * Caches the overlay HUD's ROM-backed patterns (digits, static art, lives numbers and hex digits)
 * at the fixed HUD pattern base, and tells the HUD where each set starts. Moved out of
 * {@link LevelManager}'s object-art initialisation unchanged; the HUD's fixed base avoids
 * collisions with dynamically registered object sheets.
 */
final class LevelHudArtPatterns {
    private static final Logger LOGGER = Logger.getLogger(LevelManager.class.getName());

    /** Offset of the ring hunger dial frames inside the 0x8000-pattern HUD range. */
    static final int HUNGER_DIAL_OFFSET = 0x7F00;

    private LevelHudArtPatterns() { }

    private static com.openggf.level.Palette paletteLine(com.openggf.level.Level level, int line) {
        return level == null || line < 0 || line > 3 ? null : level.getPalette(line);
    }

    static void cache(ObjectArtProvider provider, GraphicsManager graphicsManager, HudRenderManager hudRenderManager,
                      int hudBaseIndex, com.openggf.level.Level level) {
        Pattern[] hudDigits = provider.getHudDigitPatterns();
        if (hudDigits != null) {
            LOGGER.info("Cached " + hudDigits.length + " HUD Digit patterns at index " + hudBaseIndex);
            for (int i = 0; i < hudDigits.length; i++) {
                graphicsManager.cachePatternTexture(hudDigits[i], hudBaseIndex + i);
            }
            hudRenderManager.setDigitPatternIndex(hudBaseIndex);
            HudProfileAccess.measureTimeSeparators(hudRenderManager, hudDigits);
            // Mutator presentation art sits at the top of the HUD range, clear of every ROM set below.
            HudProfileAccess.installHungerDial(hudRenderManager, graphicsManager, hudDigits, provider.getHudStaticArt(),
                    paletteLine(level, provider.getHudTextPaletteLine()),
                    paletteLine(level, provider.getHudFlashPaletteLine()), hudBaseIndex + HUNGER_DIAL_OFFSET);

            int nextHudIndex = hudBaseIndex + hudDigits.length;
            com.openggf.level.objects.HudProfileAccess.installSatLayout(hudRenderManager,
                    provider instanceof com.openggf.game.internal.HudSatLayoutSource nativeHud
                            ? nativeHud.getHudSatLayout() : null);
            HudStaticArt staticHudArt = provider.getHudStaticArt();
            if (staticHudArt != null && staticHudArt.patterns() != null) {
                LOGGER.info("Cached " + staticHudArt.patterns().length
                        + " HUD Static patterns at index " + nextHudIndex);
                for (int i = 0; i < staticHudArt.patterns().length; i++) {
                    graphicsManager.cachePatternTexture(staticHudArt.patterns()[i], nextHudIndex + i);
                }
                hudRenderManager.setStaticHudArt(nextHudIndex, staticHudArt);
                hudRenderManager.setLivesPaletteOverrideSupplier(provider::getHudLivesPaletteOverride);
                nextHudIndex += staticHudArt.patterns().length;
            }

            Pattern[] hudLivesNumbers = provider.getHudLivesNumbers();
            if (hudLivesNumbers != null) {
                LOGGER.info("Cached " + hudLivesNumbers.length + " HUD Lives Numbers patterns at index "
                        + nextHudIndex);
                for (int i = 0; i < hudLivesNumbers.length; i++) {
                    graphicsManager.cachePatternTexture(hudLivesNumbers[i], nextHudIndex + i);
                }
                hudRenderManager.setLivesNumbersPatternIndex(nextHudIndex);
                nextHudIndex += hudLivesNumbers.length;
            }

            Pattern[] hudHexDigits = provider.getHudHexDigitPatterns();
            if (hudHexDigits != null) {
                LOGGER.info("Cached " + hudHexDigits.length + " HUD Hex Digit patterns at index "
                        + nextHudIndex);
                for (int i = 0; i < hudHexDigits.length; i++) {
                    graphicsManager.cachePatternTexture(hudHexDigits[i], nextHudIndex + i);
                }
                hudRenderManager.setHexDigitsPatternIndex(nextHudIndex);
            }
        }
    }
}
