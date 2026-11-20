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
    private int[] projectStart;
    private CdStageImage currentImage;
    private CdStageDemo currentDemo;
    private CdStageImage demoImage;
    private int demoEntryInputReads;
    private java.util.function.Supplier<CdStageDemo> demoSelection = () -> null;

    public SonicCdGame(CdDiscDataSource disc) {
        this.disc = Objects.requireNonNull(disc, "disc");
        this.loader = new CdStageLoader(disc);
    }

    void bindDemoSelection(java.util.function.Supplier<CdStageDemo> selection) {
        demoSelection = selection;
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
        projectStart = null;
        CdStagePayload payload = CdStagePayload.values()[levelIdx];
        currentImage = loader.readStage(payload);
        CdStageDemo demo = demoSelection.get();
        currentDemo = demo != null && demo.payload() == payload ? demo : null;
        demoImage = currentDemo == null ? null : loader.readDemo(currentDemo);
        demoEntryInputReads = demoImage == null ? 0 : currentDemo.entryInputReads(demoImage);
        int zone = SonicCdZoneRegistry.zoneIndex(payload);
        current = currentDemo == null ? loader.load(payload, currentImage, zone, true)
                : loader.loadDemo(currentDemo, demoImage, currentImage, zone);
        return current;
    }

    /** Reload through the gameplay owner before applying detached authored state. */
    public CdLevel loadProject(com.openggf.game.internal.NativeProjectSource source, Level authored) throws IOException {
        CdStagePayload payload = loader.validate(source);
        CdLevel level = (CdLevel) loadLevel(payload.ordinal());
        level.applyProject(authored);
        return level;
    }

    public CdLevel loadProject(com.openggf.game.internal.NativeProjectSource source, Level authored,
            int x, int y) throws IOException {
        CdLevel level = loadProject(source, authored);
        projectStart = new int[]{x, y};
        return level;
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

    /** An authored act does not retain the native stage or its art. */
    void retireNativeStage() {
        current = null;
        currentImage = null;
        currentDemo = null;
        demoImage = null;
        demoEntryInputReads = 0;
        projectStart = null;
    }

    public CdLevel currentLevel() { return current; }

    CdStageDemo currentDemo() { return currentDemo; }

    int nextDemoSample(int completedIterations) {
        return demoEntryInputReads + completedIterations;
    }

    int demoWord(int index) {
        return demoImage.u16(CdStageCatalogue.entry(currentDemo).demoInput() + index * 2);
    }

    /** Source of the catalogued stage object and presentation tables, or null before the first load. */
    public CdStageImage currentImage() { return currentImage; }

    @Override
    public int[] getStartPosition(int zoneIndex, int actIndex) {
        CdStagePayload payload = SonicCdZoneRegistry.payload(zoneIndex, actIndex);
        return current != null && current.payload() == payload
                ? (projectStart == null ? new int[]{current.startX(), current.startY()} : projectStart.clone()) : null;
    }

    @Override
    public SpriteArtSet loadPlayerSpriteArt(String characterCode) throws IOException {
        if (characterCode == null || !"sonic".equals(characterCode.trim().toLowerCase(Locale.ROOT))) {
            return null;
        }
        // Authored terrain has no native stage load; read Sonic's art from the supplied disc.
        if (currentDemo != null) return CdPlayerArt.load(demoImage, CdStageCatalogue.entry(currentDemo));
        return currentImage == null
                ? CdPlayerArt.load(loader.readStage(CdStagePayload.R11A), CdStageCatalogue.entry(CdStagePayload.R11A))
                : CdPlayerArt.load(currentImage, CdStageCatalogue.entry(current.payload()));
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
