package com.openggf.level;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Game;
import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.SpindashDustArtProvider;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.CharacterDefinition;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.resources.DynamicArtDecisionOwner;
import com.openggf.game.resources.DynamicArtLifecycleService;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rules.PowerUpRules;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderContext;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.managers.SpindashDustController;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.managers.TailsTailsController;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;

/**
 * Owns playable sprite renderer, DPLC bank, palette context, and auxiliary
 * playable-art setup for level loads and runtime playable refreshes.
 */
final class LevelPlayableArtInitializer {
    private static final Logger LOGGER = LevelManager.LOGGER;

    private final LevelManager levelManager;
    private final SpriteManager spriteManager;
    private final GraphicsManager graphicsManager;
    private final SonicConfigurationService configService;
    private final CrossGameFeatureProvider crossGameFeatures;

    private int sidekickPatternBankCursor;
    private int legacyDustBankCount;
    private int legacyTailsTailBankCount;

    LevelPlayableArtInitializer(LevelManager levelManager,
                                SpriteManager spriteManager,
                                GraphicsManager graphicsManager,
                                SonicConfigurationService configService,
                                CrossGameFeatureProvider crossGameFeatures) {
        this.levelManager = levelManager;
        this.spriteManager = spriteManager;
        this.graphicsManager = graphicsManager;
        this.configService = configService;
        this.crossGameFeatures = crossGameFeatures;
    }

    void initialize() {
        if (hasOnlyBuiltinPlayables()) {
            initializeBuiltinTeamLegacy();
            return;
        }
        CrossGameFeatureProvider crossGame = crossGameFeatures;
        PlayerSpriteArtProvider artProvider;
        Game game = levelManager.game;
        if (CrossGameFeatureProvider.isActive()) {
            artProvider = crossGame;
        } else if (game instanceof PlayerSpriteArtProvider p) {
            artProvider = p;
        } else artProvider = null;

        Sprite player = spriteManager.getSprite(resolveMainCharacterCode());
        if (!(player instanceof AbstractPlayableSprite playable)) {
            return;
        }
        try {
            SpriteArtSet mainArt = loadPlayableArt(playable, artProvider);
            Palette mainPalette = loadCharacterPalette(playable,
                    playable.characterKey().persisted(), artProvider);
            List<AbstractPlayableSprite> sidekicks = spriteManager.getSidekicks();
            List<SpriteArtSet> sidekickArts = new ArrayList<>(sidekicks.size());
            List<Palette> sidekickPalettes = new ArrayList<>(sidekicks.size());
            List<String> sidekickNames = new ArrayList<>(sidekicks.size());
            for (AbstractPlayableSprite sidekick : sidekicks) {
                String name = spriteManager.getSidekickCharacterName(sidekick);
                if (name == null || name.isBlank()) {
                    name = sidekick.characterKey().persisted();
                }
                sidekickNames.add(name);
                sidekickArts.add(loadPlayableArt(sidekick, artProvider));
                sidekickPalettes.add(loadCharacterPalette(sidekick, name, artProvider));
            }
            if (!validArt(mainArt) || sidekickArts.stream().anyMatch(art -> !validArt(art))) {
                return;
            }
            int cursor = 0;
            Integer mainShiftedBase = null;
            if (!playable.characterKey().isBuiltin()) {
                mainShiftedBase = LevelManager.SIDEKICK_PATTERN_BASE;
                cursor = checkedBankEnd(cursor, mainArt.bankSize());
            }
            List<Integer> sidekickBases = new ArrayList<>(sidekickArts.size());
            for (SpriteArtSet sidekickArt : sidekickArts) {
                sidekickBases.add(LevelManager.SIDEKICK_PATTERN_BASE + cursor);
                cursor = checkedBankEnd(cursor, sidekickArt.bankSize());
            }
            List<AbstractPlayableSprite> allPlayables = new ArrayList<>(sidekicks.size() + 1);
            allPlayables.add(playable);
            allPlayables.addAll(sidekicks);
            List<SpriteArtSet> allPlayableArt = new ArrayList<>(sidekickArts.size() + 1);
            allPlayableArt.add(mainArt);
            allPlayableArt.addAll(sidekickArts);
            List<PreparedAuxiliaryArt> auxiliaries = new ArrayList<>(allPlayables.size());
            int dustCount = 0;
            int tailCount = 0;
            for (int i = 0; i < allPlayables.size(); i++) {
                SpriteArtSet dust = loadSpindashDustArt(allPlayables.get(i));
                Integer dustBase = null;
                if (validArt(dust) && dustCount++ > 0) {
                    dustBase = LevelManager.SIDEKICK_PATTERN_BASE + cursor;
                    cursor = checkedBankEnd(cursor, dust.bankSize());
                }
                PreparedTailArt tail = loadTailsTailArt(allPlayables.get(i), allPlayableArt.get(i));
                Integer tailBase = null;
                if (tail != null && validArt(tail.art()) && tailCount++ > 0) {
                    tailBase = LevelManager.SIDEKICK_PATTERN_BASE + cursor;
                    cursor = checkedBankEnd(cursor, tail.art().bankSize());
                }
                auxiliaries.add(new PreparedAuxiliaryArt(dust, dustBase, tail, tailBase));
            }

            RenderContext.clearSidekickContexts();
            sidekickPatternBankCursor = cursor;

            SpriteArtSet publishedMainArt = mainShiftedBase == null
                    ? mainArt : shiftToBank(mainArt, mainShiftedBase);
            RenderContext mainCharacterContext = createCharacterPaletteContext(
                    playable, publishedMainArt, mainPalette);
            initializeMainPlayable(playable, publishedMainArt, crossGame, mainCharacterContext,
                    auxiliaries.getFirst());
            String mainName = resolveMainCharacterCode();
            for (int i = 0; i < sidekicks.size(); i++) {
                initializeSidekick(sidekicks.get(i), sidekickNames.get(i), mainName,
                        sidekickArts.get(i), crossGame,
                        sidekickBases.get(i),
                        sidekickPalettes.get(i), mainPalette, mainCharacterContext,
                        auxiliaries.get(i + 1));
            }
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to materialize playable sprite art before publication.", e);
            return;
        }
        RenderContext.uploadDonorPalettes(graphicsManager);
    }

    private boolean hasOnlyBuiltinPlayables() {
        Sprite player = spriteManager.getSprite(resolveMainCharacterCode());
        if (player instanceof AbstractPlayableSprite playable
                && !playable.characterKey().isBuiltin()) {
            return false;
        }
        return spriteManager.getSidekicks().stream()
                .allMatch(sidekick -> sidekick.characterKey().isBuiltin());
    }

    /**
     * Preserves the established built-in initialization sequence exactly. Art providers may
     * retain decode state, and playable setup order is observable by trace replay bootstrap.
     */
    private void initializeBuiltinTeamLegacy() {
        RenderContext.clearSidekickContexts();
        legacyDustBankCount = 0;
        legacyTailsTailBankCount = 0;
        sidekickPatternBankCursor = 0;

        CrossGameFeatureProvider crossGame = crossGameFeatures;
        PlayerSpriteArtProvider artProvider;
        Game game = levelManager.game;
        if (CrossGameFeatureProvider.isActive()) {
            artProvider = crossGame;
        } else if (game instanceof PlayerSpriteArtProvider p) {
            artProvider = p;
        } else {
            return;
        }

        Sprite player = spriteManager.getSprite(resolveMainCharacterCode());
        if (!(player instanceof AbstractPlayableSprite playable)) {
            return;
        }
        initializeMainPlayableLegacy(playable, artProvider, crossGame);
        initializeSidekicksLegacy(artProvider, crossGame);
        RenderContext.uploadDonorPalettes(graphicsManager);
    }

    int reserveSidekickPatternBank(int bankSize) {
        if (bankSize < 0) throw new IllegalArgumentException("bank size must be nonnegative");
        int base = LevelManager.SIDEKICK_PATTERN_BASE + sidekickPatternBankCursor;
        int end = checkedBankEnd(sidekickPatternBankCursor, bankSize);
        sidekickPatternBankCursor = end;
        return base;
    }

    static List<Integer> computeSidekickBankOffsets(List<Integer> bankSizes) {
        List<Integer> offsets = new ArrayList<>(bankSizes.size());
        int running = 0;
        for (int size : bankSizes) {
            if (size < 0) throw new IllegalArgumentException("bank size must be nonnegative");
            offsets.add(running);
            running = Math.addExact(running, size);
            if (running > com.openggf.graphics.PatternAtlasRange.SIDEKICK_BANKS.size()) {
                throw new IllegalArgumentException("sidekick pattern banks exceed reserved range");
            }
        }
        return offsets;
    }

    private void initializeMainPlayableLegacy(AbstractPlayableSprite playable,
                                               PlayerSpriteArtProvider artProvider,
                                               CrossGameFeatureProvider crossGame) {
        try {
            SpriteArtSet artSet = artProvider.loadPlayerSpriteArt(playable.getCode());
            if (!validArt(artSet)) {
                playable.setSpriteRenderer(null);
                return;
            }
            PlayerSpriteRenderer renderer = new PlayerSpriteRenderer(artSet);
            if (CrossGameFeatureProvider.isActive()) {
                renderer.setRenderContext(crossGame.getDonorRenderContext());
            }
            renderer.ensureCached(graphicsManager);
            applyPlayableArt(playable, renderer, artSet);
            initSpindashDustLegacy(playable);
            initTailsTailsLegacy(playable, artSet);
            initSuperState(playable);
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to load player sprite art.", e);
        }
    }

    private void initializeSidekicksLegacy(PlayerSpriteArtProvider artProvider,
                                           CrossGameFeatureProvider crossGame) {
        List<AbstractPlayableSprite> sidekicks = spriteManager.getSidekicks();
        String mainCharName = resolveMainCharacterCode();
        List<String> sidekickCharNames = new ArrayList<>(sidekicks.size());
        for (AbstractPlayableSprite sidekick : sidekicks) {
            String name = spriteManager.getSidekickCharacterName(sidekick);
            if (name == null) {
                name = configService.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
            }
            sidekickCharNames.add(name);
        }

        java.util.Map<String, SpriteArtSet> artCache = new java.util.HashMap<>();
        List<SpriteArtSet> sidekickSourceArts = new ArrayList<>(sidekicks.size());
        for (String sidekickCharName : sidekickCharNames) {
            SpriteArtSet sourceArt = artCache.computeIfAbsent(
                    sidekickCharName.toLowerCase(),
                    key -> {
                        try {
                            return artProvider.loadPlayerSpriteArt(key);
                        } catch (IOException e) {
                            LOGGER.log(SEVERE, "Failed to load art for sidekick character: " + key, e);
                            return null;
                        }
                    });
            sidekickSourceArts.add(validArt(sourceArt) ? sourceArt : null);
        }

        for (int i = 0; i < sidekicks.size(); i++) {
            AbstractPlayableSprite sidekick = sidekicks.get(i);
            String sidekickCharName = sidekickCharNames.get(i);
            SpriteArtSet sourceArt = sidekickSourceArts.get(i);
            if (sourceArt == null) {
                LOGGER.warning("Skipping art init for sidekick " + i
                        + " (" + sidekickCharName + "): art unavailable or empty.");
                continue;
            }
            initializeSidekickLegacy(sidekick, sidekickCharName, mainCharName,
                    sourceArt, artProvider, crossGame, i);
        }
    }

    private void initializeSidekickLegacy(AbstractPlayableSprite sidekick,
                                           String sidekickCharName,
                                           String mainCharName,
                                           SpriteArtSet sourceArt,
                                           PlayerSpriteArtProvider artProvider,
                                           CrossGameFeatureProvider crossGame,
                                           int index) {
        try {
            int shiftedBase = reserveSidekickPatternBank(sourceArt.bankSize());
            SpriteArtSet sidekickArt = shiftToBank(sourceArt, shiftedBase);
            PlayerSpriteRenderer sidekickRenderer = new PlayerSpriteRenderer(sidekickArt);
            RenderContext sidekickPaletteCtx = createSidekickPaletteContextLegacy(
                    artProvider, sidekickCharName, mainCharName);
            if (sidekickPaletteCtx != null) {
                sidekickRenderer.setRenderContext(sidekickPaletteCtx);
            } else if (CrossGameFeatureProvider.isActive()) {
                sidekickRenderer.setRenderContext(crossGame.getDonorRenderContext());
            }
            sidekickRenderer.ensureCached(graphicsManager);
            applyPlayableArt(sidekick, sidekickRenderer, sidekickArt);
            initSpindashDustLegacy(sidekick);
            initTailsTailsLegacy(sidekick, sidekickArt);
            if (sidekickPaletteCtx != null) {
                propagateSidekickPaletteContext(sidekick, sidekickPaletteCtx);
            }
            initSuperState(sidekick);
        } catch (Exception e) {
            LOGGER.log(SEVERE, "Failed to load sidekick sprite art for index " + index + ".", e);
        }
    }

    private RenderContext createSidekickPaletteContextLegacy(
            PlayerSpriteArtProvider artProvider,
            String sidekickCharName, String mainCharName) {
        if (sidekickCharName.equalsIgnoreCase(mainCharName)) {
            return null;
        }
        Palette sidekickPalette = artProvider.loadCharacterPalette(sidekickCharName);
        if (sidekickPalette == null) {
            return null;
        }
        Palette mainPalette = artProvider.loadCharacterPalette(mainCharName);
        if (mainPalette != null && sidekickPalette.dataEquals(mainPalette)) {
            return null;
        }
        GameModule activeModule = levelManager.gameModule;
        if (activeModule == null && GameServices.hasRuntime()) {
            activeModule = GameServices.module();
        }
        GameId gameId = activeModule != null ? activeModule.getGameId() : null;
        RenderContext ctx = RenderContext.createSidekickContext(gameId);
        ctx.setPalette(0, sidekickPalette);
        return ctx;
    }

    private void initializeMainPlayable(AbstractPlayableSprite playable, SpriteArtSet artSet,
                                        CrossGameFeatureProvider crossGame,
                                        RenderContext characterContext,
                                        PreparedAuxiliaryArt auxiliary) throws IOException {
            PlayerSpriteRenderer renderer = new PlayerSpriteRenderer(artSet, graphicsManager);
            if (characterContext != null) {
                renderer.setRenderContext(characterContext);
            } else if (CrossGameFeatureProvider.isActive()) {
                renderer.setRenderContext(crossGame.getDonorRenderContext());
            }
            renderer.ensureCached(graphicsManager);
            applyPlayableArt(playable, renderer, artSet);
            initSpindashDust(playable, auxiliary);
            initTailsTails(playable, auxiliary);
            initSuperState(playable);
    }

    private void initializeSidekick(AbstractPlayableSprite sidekick,
                                   String sidekickCharName,
                                   String mainCharName,
                                   SpriteArtSet sourceArt,
                                   CrossGameFeatureProvider crossGame,
                                   int shiftedBase,
                                   Palette sidekickPalette, Palette mainPalette,
                                   RenderContext mainCharacterContext,
                                   PreparedAuxiliaryArt auxiliary) throws IOException {
            SpriteArtSet sidekickArt = shiftToBank(sourceArt, shiftedBase);
            PlayerSpriteRenderer sidekickRenderer = new PlayerSpriteRenderer(sidekickArt, graphicsManager);
            RenderContext sidekickPaletteCtx = createSidekickPaletteContext(
                    sidekickCharName, mainCharName, sidekickPalette, mainPalette,
                    mainCharacterContext, sourceArt.paletteIndex());
            if (sidekickPaletteCtx != null) {
                sidekickRenderer.setRenderContext(sidekickPaletteCtx);
            } else if (CrossGameFeatureProvider.isActive()) {
                sidekickRenderer.setRenderContext(crossGame.getDonorRenderContext());
            }
            sidekickRenderer.ensureCached(graphicsManager);
            applyPlayableArt(sidekick, sidekickRenderer, sidekickArt);
            initSpindashDust(sidekick, auxiliary);
            initTailsTails(sidekick, auxiliary);
            if (sidekickPaletteCtx != null) {
                propagateSidekickPaletteContext(sidekick, sidekickPaletteCtx);
            }
            initSuperState(sidekick);
    }

    static SpriteArtSet shiftToBank(SpriteArtSet sourceArt, int shiftedBase) {
        return new SpriteArtSet(
                    sourceArt.artTiles(),
                    sourceArt.mappingFrames(),
                    sourceArt.dplcFrames(),
                    sourceArt.paletteIndex(),
                    shiftedBase,
                    sourceArt.frameDelay(),
                    sourceArt.bankSize(),
                    sourceArt.animationProfile(),
                    sourceArt.animationSet());
    }

    private void applyPlayableArt(AbstractPlayableSprite playable,
                                  PlayerSpriteRenderer renderer,
                                  SpriteArtSet artSet) {
        playable.setSpriteRenderer(renderer);
        // Mod playables can reach art init before an animation manager exists.
        if (playable.getAnimationManager() != null) {
            playable.getAnimationManager().setDynamicArtDecisionOwner(
                    createDynamicArtOwner(playable.getCode(), renderer));
        }
        playable.setMappingFrame(0);
        playable.setAnimationFrameCount(artSet.mappingFrames().size());
        playable.setAnimationProfile(artSet.animationProfile());
        playable.setAnimationSet(artSet.animationSet());
        playable.setAnimationId(0);
        playable.setAnimationFrameIndex(0);
        playable.setAnimationTick(0);
    }

    private RenderContext createSidekickPaletteContext(
            String sidekickCharName, String mainCharName,
            Palette sidekickPalette, Palette mainPalette,
            RenderContext mainCharacterContext, int paletteIndex) {
        boolean sameName = sidekickCharName.equalsIgnoreCase(mainCharName);
        boolean samePalette = mainPalette != null && sidekickPalette != null
                && sidekickPalette.dataEquals(mainPalette);
        if (mainCharacterContext != null && (sameName || samePalette)) {
            Palette populated = mainCharacterContext.getPalette(paletteIndex);
            if (populated != null && sidekickPalette != null
                    && populated.dataEquals(sidekickPalette)) {
                return mainCharacterContext;
            }
        }
        if (sameName && mainCharacterContext == null) return null;
        if (sidekickPalette == null) {
            return null;
        }
        if (samePalette && mainCharacterContext == null) return null;
        GameModule activeModule = levelManager.gameModule;
        if (activeModule == null && GameServices.hasRuntime()) {
            activeModule = GameServices.module();
        }
        GameId gameId = activeModule != null ? activeModule.getGameId() : null;
        RenderContext ctx = RenderContext.createSidekickContext(gameId);
        ctx.setPalette(paletteIndex, sidekickPalette);
        return ctx;
    }

    private SpriteArtSet loadPlayableArt(AbstractPlayableSprite playable,
                                         PlayerSpriteArtProvider fallback) throws IOException {
        CharacterDefinition definition = activeRegistry().find(playable.characterKey()).orElse(null);
        if (definition != null && !definition.key().isBuiltin()) {
            return definition.artSupplier().load(playable.characterKey().persisted());
        }
        return fallback == null ? null : fallback.loadPlayerSpriteArt(playable.getCode());
    }

    private Palette loadCharacterPalette(AbstractPlayableSprite playable, String code,
                                         PlayerSpriteArtProvider fallback) throws IOException {
        CharacterDefinition definition = activeRegistry().find(playable.characterKey()).orElse(null);
        if (definition != null && !definition.key().isBuiltin() && definition.paletteSupplier() != null) {
            return definition.paletteSupplier().load(playable.characterKey().persisted());
        }
        return fallback == null ? null : fallback.loadCharacterPalette(code);
    }

    private RenderContext createCharacterPaletteContext(AbstractPlayableSprite playable,
                                                         SpriteArtSet artSet,
                                                         Palette palette) {
        if (palette == null || playable.characterKey().isBuiltin()) return null;
        GameModule module = levelManager.activeGameModule();
        RenderContext context = RenderContext.createSidekickContext(
                module == null ? null : module.getGameId());
        context.setPalette(artSet.paletteIndex(), palette);
        return context;
    }

    private com.openggf.game.PlayableCharacterRegistry activeRegistry() {
        return levelManager.playableCharacterRegistry();
    }

    private static boolean validArt(SpriteArtSet art) {
        return art != null && art.bankSize() > 0 && !art.mappingFrames().isEmpty()
                && !art.dplcFrames().isEmpty()
                && art.paletteIndex() >= 0 && art.paletteIndex() < 4;
    }

    private static void propagateSidekickPaletteContext(AbstractPlayableSprite sidekick, RenderContext ctx) {
        if (sidekick.getSpindashDustController() != null
                && sidekick.getSpindashDustController().getRenderer() != null) {
            sidekick.getSpindashDustController().getRenderer().setRenderContext(ctx);
        }
        if (sidekick.getTailsTailsController() != null
                && sidekick.getTailsTailsController().getRenderer() != null) {
            sidekick.getTailsTailsController().getRenderer().setRenderContext(ctx);
        }
    }

    private void initSpindashDustLegacy(AbstractPlayableSprite playable) {
        CrossGameFeatureProvider crossGame = crossGameFeatures;
        SpindashDustArtProvider dustProv;
        Game game = levelManager.game;
        if (CrossGameFeatureProvider.isActive()) {
            dustProv = crossGame;
        } else if (game instanceof SpindashDustArtProvider d) {
            dustProv = d;
        } else {
            playable.setSpindashDustController(null);
            return;
        }
        try {
            String characterCode = playable.getCode().endsWith("_p2")
                    ? playable.getCode().substring(0, playable.getCode().length() - 3)
                    : playable.getCode();
            SpriteArtSet dustArt = dustProv.loadSpindashDustArt(characterCode);
            if (!validArt(dustArt)) {
                playable.setSpindashDustController(null);
                return;
            }
            if (legacyDustBankCount > 0) {
                dustArt = shiftToBank(dustArt, reserveSidekickPatternBank(dustArt.bankSize()));
            }
            legacyDustBankCount++;
            PlayerSpriteRenderer dustRenderer = new PlayerSpriteRenderer(dustArt);
            if (CrossGameFeatureProvider.isActive()) {
                dustRenderer.setRenderContext(crossGame.getDonorRenderContext());
            }
            dustRenderer.ensureCached(graphicsManager);
            playable.setSpindashDustController(new SpindashDustController(
                    playable, dustRenderer, fixedDustSlotFor(playable)));
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to load spindash dust art.", e);
            playable.setSpindashDustController(null);
        }
    }

    private void initTailsTailsLegacy(AbstractPlayableSprite playable, SpriteArtSet artSet) {
        if (!(playable instanceof Tails)) {
            playable.setTailsTailsController(null);
            return;
        }
        CrossGameFeatureProvider crossGame = crossGameFeatures;
        GameModule gameModule = levelManager.gameModule;
        boolean isS3k = CrossGameFeatureProvider.isActive()
                ? crossGame.hasSeparateTailsTailArt()
                : gameModule.hasSeparateTailsTailArt();
        SpriteArtSet tailsArt;
        if (isS3k) {
            if (CrossGameFeatureProvider.isActive()) {
                try {
                    tailsArt = crossGame.loadTailsTailArt();
                } catch (IOException e) {
                    LOGGER.log(SEVERE, "Failed to load cross-game tails tail art.", e);
                    tailsArt = null;
                }
            } else {
                tailsArt = gameModule.loadTailsTailArt();
            }
            if (tailsArt == null || tailsArt.isEmpty()) {
                playable.setTailsTailsController(null);
                return;
            }
        } else {
            tailsArt = new SpriteArtSet(
                    artSet.artTiles(), artSet.mappingFrames(), artSet.dplcFrames(),
                    artSet.paletteIndex(), gameModule.getTailsTailVramBase(), artSet.frameDelay(),
                    artSet.bankSize(), null, null);
        }
        if (legacyTailsTailBankCount > 0) {
            tailsArt = shiftToBank(tailsArt, reserveSidekickPatternBank(tailsArt.bankSize()));
        }
        legacyTailsTailBankCount++;
        PlayerSpriteRenderer tailsRenderer = new PlayerSpriteRenderer(tailsArt);
        if (CrossGameFeatureProvider.isActive()) {
            tailsRenderer.setRenderContext(crossGame.getDonorRenderContext());
        }
        tailsRenderer.ensureCached(graphicsManager);
        playable.setTailsTailsController(new TailsTailsController(
                playable, tailsRenderer, isS3k,
                createDynamicArtOwner("tails-tails", tailsRenderer)));
    }

    private SpriteArtSet loadSpindashDustArt(AbstractPlayableSprite playable) throws IOException {
        CrossGameFeatureProvider crossGame = crossGameFeatures;
        SpindashDustArtProvider dustProv;
        Game game = levelManager.game;
        if (CrossGameFeatureProvider.isActive()) {
            dustProv = crossGame;
        } else if (game instanceof SpindashDustArtProvider d) {
            dustProv = d;
        } else {
            return null;
        }
        String characterCode = playable.getCode().endsWith("_p2")
                ? playable.getCode().substring(0, playable.getCode().length() - 3)
                : playable.getCode();
        return dustProv.loadSpindashDustArt(characterCode);
    }

    private void initSpindashDust(AbstractPlayableSprite playable,
                                  PreparedAuxiliaryArt auxiliary) {
            SpriteArtSet dustArt = auxiliary.dustArt();
            if (!validArt(dustArt)) {
                playable.setSpindashDustController(null);
                return;
            }
            if (auxiliary.dustShiftedBase() != null)
                dustArt = shiftToBank(dustArt, auxiliary.dustShiftedBase());
            PlayerSpriteRenderer dustRenderer = new PlayerSpriteRenderer(dustArt, graphicsManager);
            if (CrossGameFeatureProvider.isActive()) {
                dustRenderer.setRenderContext(crossGameFeatures.getDonorRenderContext());
            }
            dustRenderer.ensureCached(graphicsManager);
            playable.setSpindashDustController(new SpindashDustController(
                    playable, dustRenderer, fixedDustSlotFor(playable)));
    }

    private int fixedDustSlotFor(AbstractPlayableSprite playable) {
        if (playable == null) {
            return -1;
        }
        PowerUpRules rules = powerUpRulesFor(levelManager.activeGameModule());
        if (rules == null || !rules.waterSplashUsesFixedDustObject()) {
            return -1;
        }
        if (!playable.isCpuControlled()) {
            return rules.fixedDustSlotIndex(false);
        }
        List<AbstractPlayableSprite> sidekicks = spriteManager != null ? spriteManager.getSidekicks() : List.of();
        return !sidekicks.isEmpty() && sidekicks.get(0) == playable
                ? rules.fixedDustSlotIndex(true)
                : -1;
    }

    private PreparedTailArt loadTailsTailArt(AbstractPlayableSprite playable,
                                             SpriteArtSet artSet) throws IOException {
        if (!(playable instanceof Tails)) {
            return null;
        }
        CrossGameFeatureProvider crossGame = crossGameFeatures;
        GameModule gameModule = levelManager.activeGameModule();
        if (gameModule == null) return null;
        boolean isS3k = CrossGameFeatureProvider.isActive()
                ? crossGame.hasSeparateTailsTailArt()
                : gameModule.hasSeparateTailsTailArt();
        SpriteArtSet tailsArt;
        if (isS3k) {
            if (CrossGameFeatureProvider.isActive()) {
                tailsArt = crossGame.loadTailsTailArt();
            } else {
                tailsArt = gameModule.loadTailsTailArt();
            }
            if (tailsArt == null || tailsArt.isEmpty()) {
                return null;
            }
        } else {
            tailsArt = new SpriteArtSet(
                    artSet.artTiles(),
                    artSet.mappingFrames(),
                    artSet.dplcFrames(),
                    artSet.paletteIndex(),
                    gameModule.getTailsTailVramBase(),
                    artSet.frameDelay(),
                    artSet.bankSize(),
                    null,
                    null
            );
        }
        return new PreparedTailArt(tailsArt, isS3k);
    }

    private void initTailsTails(AbstractPlayableSprite playable,
                                PreparedAuxiliaryArt auxiliary) {
        PreparedTailArt prepared = auxiliary.tailArt();
        if (prepared == null || !validArt(prepared.art())) {
            playable.setTailsTailsController(null);
            return;
        }
        SpriteArtSet tailsArt = prepared.art();
        if (auxiliary.tailShiftedBase() != null)
            tailsArt = shiftToBank(tailsArt, auxiliary.tailShiftedBase());
        PlayerSpriteRenderer tailsRenderer = new PlayerSpriteRenderer(tailsArt, graphicsManager);
        if (CrossGameFeatureProvider.isActive()) {
            tailsRenderer.setRenderContext(crossGameFeatures.getDonorRenderContext());
        }
        tailsRenderer.ensureCached(graphicsManager);
        playable.setTailsTailsController(new TailsTailsController(
                playable, tailsRenderer, prepared.separate(),
                createDynamicArtOwner("tails-tails", tailsRenderer)));
    }

    private DynamicArtDecisionOwner createDynamicArtOwner(
            String owner,
            PlayerSpriteRenderer renderer) {
        GameModule module = levelManager.gameModule;
        DynamicArtLifecycleService lifecycle =
                GameServices.dynamicArtLifecycleOrNull();
        if (module == null || lifecycle == null || owner == null
                || module.getRules() == null) {
            return null;
        }
        String normalizedOwner = owner.toLowerCase(java.util.Locale.ROOT);
        boolean supportedOwner = "sonic".equals(normalizedOwner)
                || "tails".equals(normalizedOwner)
                || "tails-tails".equals(normalizedOwner);
        if (!supportedOwner || !module.getRules().dynamicArtDmaService()
                .supportsPlayerDynamicArtAudit()) {
            return null;
        }
        return new DynamicArtDecisionOwner(
                lifecycle, module.getGameId(), normalizedOwner, renderer);
    }

    private static int checkedBankEnd(int cursor, int bankSize) {
        if (bankSize < 0) throw new IllegalArgumentException("bank size must be nonnegative");
        int end = Math.addExact(cursor, bankSize);
        if (end > com.openggf.graphics.PatternAtlasRange.SIDEKICK_BANKS.size())
            throw new IllegalArgumentException("sidekick pattern banks exceed reserved range");
        return end;
    }

    private record PreparedTailArt(SpriteArtSet art, boolean separate) { }
    private record PreparedAuxiliaryArt(SpriteArtSet dustArt, Integer dustShiftedBase,
                                        PreparedTailArt tailArt, Integer tailShiftedBase) { }

    private void initSuperState(AbstractPlayableSprite playable) {
        GameModule gameModule = levelManager.gameModule;
        if (gameModule == null) {
            return;
        }
        var superCtrl = gameModule.createSuperStateController(playable);
        playable.setSuperStateController(superCtrl);

        if (superCtrl != null && !superCtrl.isRomDataPreLoaded()) {
            try {
                Rom rom = GameServices.rom().getRom();
                RomByteReader reader = RomByteReader.fromRom(rom);
                superCtrl.loadRomData(reader);
            } catch (Exception e) {
                LOGGER.fine("Could not load Super Sonic ROM data: " + e.getMessage());
            }
        }
    }

    private String resolveMainCharacterCode() {
        return com.openggf.game.session.ActiveGameplayTeamResolver.resolveMainCharacterCode(configService);
    }

    private static PowerUpRules powerUpRulesFor(GameModule module) {
        GameRules rules = module != null ? module.getRules() : null;
        return rules != null ? rules.powerUp() : null;
    }
}
