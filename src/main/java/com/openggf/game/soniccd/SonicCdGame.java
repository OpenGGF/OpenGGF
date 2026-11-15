package com.openggf.game.soniccd;

import com.openggf.audio.GameSound;
import com.openggf.data.Rom;
import com.openggf.game.DynamicStartPositionProvider;
import com.openggf.game.Game;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.level.CdStageLoader;
import com.openggf.level.Level;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.animation.AnimatedPaletteProvider;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.level.animation.AnimatedPatternProvider;
import com.openggf.sprites.art.PlayerSpriteArtProvider;
import com.openggf.sprites.art.SpriteArtSet;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Sonic CD on the shared level engine, backed by the user's disc. */
public final class SonicCdGame extends Game implements PlayerSpriteArtProvider, DynamicStartPositionProvider,
        AnimatedPaletteProvider, AnimatedPatternProvider {
    private final CdDiscDataSource disc;
    private final CdStageLoader loader;
    private CdLevel current;
    private CdStageImage currentImage;

    public SonicCdGame(CdDiscDataSource disc) {
        this.disc = Objects.requireNonNull(disc, "disc");
        this.loader = new CdStageLoader(disc);
    }

    public CdDiscDataSource disc() { return disc; }

    @Override public boolean isCompatible() { return true; }
    @Override public String getIdentifier() { return "SonicCD"; }

    @Override
    public List<String> getTitleCards() {
        return java.util.Arrays.stream(CdStagePayload.values()).map(Enum::name).toList();
    }

    @Override
    public Level loadLevel(int levelIdx) throws IOException {
        CdStagePayload payload = CdStagePayload.values()[levelIdx];
        currentImage = loader.readStage(payload);
        current = loader.load(payload, currentImage, SonicCdZoneRegistry.zoneIndex(payload), true);
        return current;
    }

    /** The stage zone's fragment owns its palette cycle; zones without one return null. */
    @Override
    public AnimatedPaletteManager loadAnimatedPaletteManager(Level level, int zoneIndex) {
        if (!(level instanceof CdLevel stage) || currentImage == null) return null;
        return com.openggf.game.soniccd.zones.CdZones.fragment(stage.payload().stage().zone())
                .createPaletteCycle(stage.payload(), level, currentImage);
    }

    /**
     * UpdateGlobalAnims, the stage-independent clocks (Level/Main.asm:513-549), followed by the
     * stage zone's animated tiles; zones without any contribute nothing.
     */
    @Override
    public AnimatedPatternManager loadAnimatedPatternManager(Level level, int zoneIndex) {
        AnimatedPatternManager zoneTiles = null;
        if (level instanceof CdLevel stage && currentImage != null) {
            zoneTiles = com.openggf.game.soniccd.zones.CdZones.fragment(stage.payload().stage().zone())
                    .createAnimatedTiles(stage.payload(), level, currentImage);
        }
        return new com.openggf.game.soniccd.objects.common.CdGlobalAnimations(zoneTiles);
    }

    public CdLevel currentLevel() { return current; }

    /** The loaded stage file, or {@code null} before the first load. */
    public CdStageImage currentImage() { return currentImage; }

    @Override
    public int[] getStartPosition(int zoneIndex, int actIndex) {
        CdStagePayload payload = SonicCdZoneRegistry.payload(zoneIndex, actIndex);
        return current != null && current.payload() == payload ? new int[] {current.startX(), current.startY()} : null;
    }

    @Override
    public SpriteArtSet loadPlayerSpriteArt(String characterCode) throws IOException {
        if (characterCode == null || !"sonic".equals(characterCode.trim().toLowerCase(Locale.ROOT))
                || currentImage == null) {
            return null;
        }
        return CdPlayerArt.load(currentImage, CdStageCatalogue.entry(current.payload()));
    }

    @Override public int getMusicId(int levelIdx) {
        return com.openggf.game.soniccd.audio.SonicCdAudioProfile.stageMusicCommand(CdStagePayload.values()[levelIdx]);
    }
    @Override public Map<GameSound, Integer> getSoundMap() {
        return com.openggf.game.soniccd.audio.SonicCdAudioProfile.soundMap();
    }
    @Override public boolean canRelocateLevels() { return false; }
    @Override public boolean canSave() { return false; }
    @Override public boolean relocateLevels(boolean unsafe) { return false; }
    @Override public boolean save(int levelIdx, Level level) { return false; }
    @Override public int[] getBackgroundScroll(int levelIdx, int cameraX, int cameraY) { return new int[] {0, 0}; }
    @Override public Rom getRom() { return null; }
}
