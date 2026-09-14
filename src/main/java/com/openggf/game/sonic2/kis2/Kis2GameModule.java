package com.openggf.game.sonic2.kis2;

import com.openggf.data.Game;
import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.BuiltInRomDetectors;
import com.openggf.game.ContinueScreenProvider;
import com.openggf.game.CrossGameDonorProvider;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.sonic2.Sonic2ArtOverlays;
import com.openggf.game.sonic2.Sonic2ObjectArtProvider;
import com.openggf.game.sonic2.Sonic2WaterDataProvider;
import com.openggf.game.sonic2.continuescreen.Sonic2ContinueScreenProvider;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectArtKeys;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Sonic 2 decorated by the Knuckles in Sonic 2 lock-on. Overrides only what
 * the lock-on program changes: physics, the game data (layouts, art, palette),
 * the object-art overlays, the underwater palettes and the continue icon;
 * everything else delegates to stock S2.
 *
 * <p>Tier one (S&amp;K half and S2 cart) is always available. Tier two opens the
 * optional {@link LogicalRom#KIS2} image through the patch context; when the
 * user-supplied dump is absent the module keeps tier-one behaviour exactly.
 */
final class Kis2GameModule extends DelegatingGameModule {

    private static final Logger LOGGER = Logger.getLogger(Kis2GameModule.class.getName());

    /** Which lock-on windows the module reads. */
    enum Fidelity {
        /** S&amp;K half and S2 cart only. */
        TIER_ONE,
        /** The full lock-on address space including the chip. */
        TIER_TWO
    }

    private final PatchContext context;
    private PhysicsProvider physicsProvider;
    private ObjectArtProvider objectArtProvider;
    private RomByteReader sk;
    private PlayerSpriteArtProvider skKnucklesArt;
    private Kis2Game game;
    private boolean kis2ImageProbed;
    private RomByteReader kis2Image;
    private Kis2ChipArt chipArt;
    private Kis2SpecialStageProvider specialStageProvider;
    private Kis2TitleScreen titleScreen;
    private com.openggf.game.DebugModeProvider debugModeProvider;

    Kis2GameModule(GameModule base, PatchContext context) {
        super(base, Kis2Constants.PATCH_ID);
        this.context = context;
    }

    @Override
    public Game createGame(Rom rom) {
        // Run the base module's side effects (PLC service, active ROM) and
        // replace only the game data object.
        base().createGame(rom);
        game = new Kis2Game(rom, skReader(), skKnucklesArt(), kis2Image().orElse(null), chipArt().orElse(null));
        return game;
    }

    @Override
    public Game createGame(GameDataSource source) {
        return createGame(source.rom().orElseThrow(() ->
                new IllegalStateException("Knuckles in Sonic 2 requires a ROM data source")));
    }

    @Override
    public PhysicsProvider getPhysicsProvider() {
        if (physicsProvider == null) {
            physicsProvider = new Kis2PhysicsProvider();
        }
        return physicsProvider;
    }

    @Override
    public ObjectArtProvider getObjectArtProvider() {
        if (objectArtProvider == null) {
            objectArtProvider = new Sonic2ObjectArtProvider(artOverlays());
        }
        return objectArtProvider;
    }

    @Override
    public com.openggf.game.TitleScreenProvider getTitleScreenProvider() {
        if (kis2Image().isEmpty()) return base().getTitleScreenProvider();
        if (titleScreen == null) titleScreen = new Kis2TitleScreen(lockOnAddressSpace());
        return titleScreen;
    }

    @Override
    public com.openggf.game.EndingProvider getEndingProvider() {
        if (kis2Image().isEmpty()) return base().getEndingProvider();
        return new com.openggf.game.sonic2.credits.Sonic2EndingProvider(
                new Kis2EndingPresentation(lockOnAddressSpace(), new Kis2PlayerArt(skReader(), skKnucklesArt())));
    }

    private LockOnAddressSpace lockOnAddressSpace() {
        RomByteReader dump = kis2Image().orElseThrow();
        return LockOnAddressSpace.tierTwo(skReader(), dump.window(Kis2Constants.S2_WINDOW_START,
                Kis2Constants.S2_WINDOW_END - Kis2Constants.S2_WINDOW_START), dump);
    }

    @Override
    public com.openggf.game.SpecialStageProvider getSpecialStageProvider() {
        if (kis2Image().isEmpty()) return base().getSpecialStageProvider();
        if (specialStageProvider == null) specialStageProvider = new Kis2SpecialStageProvider(lockOnAddressSpace());
        return specialStageProvider;
    }

    @Override
    public com.openggf.game.DebugModeProvider getDebugModeProvider() {
        if (kis2Image().isEmpty()) return base().getDebugModeProvider();
        if (debugModeProvider == null) {
            var manager = ((Kis2SpecialStageProvider) getSpecialStageProvider()).getManager();
            debugModeProvider = new com.openggf.game.sonic2.debug.Sonic2DebugModeProvider(
                    manager, manager.getDebugSprites());
        }
        return debugModeProvider;
    }

    @Override
    public <T> T getGameService(Class<T> type) {
        if (kis2Image().isPresent()) {
            var provider = (Kis2SpecialStageProvider) getSpecialStageProvider();
            if (type == com.openggf.game.sonic2.specialstage.Sonic2SpecialStageManager.class)
                return type.cast(provider.getManager());
            if (type == com.openggf.game.sonic2.debug.Sonic2SpecialStageSpriteDebug.class)
                return type.cast(provider.getManager().getDebugSprites());
        }
        return base().getGameService(type);
    }

    @Override
    public com.openggf.sprites.playable.SuperStateController createSuperStateController(
            com.openggf.sprites.playable.AbstractPlayableSprite player) {
        if ("knuckles".equalsIgnoreCase(player.getCode())) {
            return kis2Image().map(image -> new Kis2SuperStateController(player, image)).orElse(null);
        }
        return base().createSuperStateController(player);
    }

    /**
     * {@code PalPtr_CPZ_U} and {@code PalPtr_ARZ_U} point at the chip's
     * recoloured underwater lines; every other zone keeps the stock data.
     */
    @Override
    public WaterDataProvider getWaterDataProvider() {
        Optional<Kis2ChipArt> chip = chipArt();
        if (chip.isEmpty()) {
            return base().getWaterDataProvider();
        }
        Kis2ChipArt art = chip.get();
        return new Sonic2WaterDataProvider((zoneId, actId) -> {
            if (zoneId == Sonic2ZoneConstants.ROM_ZONE_CPZ) {
                return art.chemicalPlantUnderwaterPalette();
            }
            if (zoneId == Sonic2ZoneConstants.ROM_ZONE_ARZ) {
                return art.aquaticRuinUnderwaterPalette();
            }
            return null;
        });
    }

    /** {@code ArtNem_MiniSonic} is the chip's "Knuckles continue.nem" in KiS2. */
    @Override
    public ContinueScreenProvider createContinueScreenProvider() {
        Optional<Kis2ChipArt> chip = chipArt();
        if (chip.isEmpty()) {
            return base().createContinueScreenProvider();
        }
        return new Sonic2ContinueScreenProvider(() -> chip.get().load(Kis2ChipArt.Asset.CONTINUE_ICON),
                new Kis2ContinuePresentation(new Kis2PlayerArt(skReader(), skKnucklesArt())));
    }

    /** The fidelity the module resolved from the ROMs the context could open. */
    Fidelity fidelity() {
        return kis2Image().isPresent() ? Fidelity.TIER_TWO : Fidelity.TIER_ONE;
    }

    /**
     * Tier two mirrors the patched PLC lists ({@code PlrList_Std1},
     * {@code PlrList_Std2}, {@code PlrList_Signpost}); tier one keeps the
     * S&amp;K life icon only.
     */
    private Sonic2ArtOverlays artOverlays() {
        Optional<Kis2ChipArt> chip = chipArt();
        if (chip.isEmpty()) {
            return Sonic2ArtOverlays.lifeIconOnly(this::loadTierOneLifeIcon);
        }
        Kis2ChipArt art = chip.get();
        return new Sonic2ArtOverlays(
                () -> art.load(Kis2ChipArt.Asset.LIFE_COUNTER),
                List.of(
                        // PlrList_Std2: plreq ArtTile_ArtNem_Powerups+44, ArtNem_PowerupsKnucklesPatch
                        new Sonic2ArtOverlays.SheetPatch(ObjectArtKeys.MONITOR,
                                Kis2Constants.POWERUPS_KNUCKLES_PATCH_TILE,
                                () -> art.load(Kis2ChipArt.Asset.POWERUPS_PATCH)),
                        // PlrList_Std2: plreq ArtTile_ArtNem_Shield, ArtNem_Shield_and_invincible_stars
                        new Sonic2ArtOverlays.SheetPatch(ObjectArtKeys.SHIELD, 0, art::shield),
                        new Sonic2ArtOverlays.SheetPatch(ObjectArtKeys.INVINCIBILITY_STARS, 0,
                                art::invincibilityStars),
                        // PlrList_Signpost: plreq ArtTile_ArtNem_Signpost+34, ArtNem_SignpostKnucklesPatch
                        new Sonic2ArtOverlays.SheetPatch(ObjectArtKeys.SIGNPOST,
                                Kis2Constants.SIGNPOST_KNUCKLES_PATCH_TILE,
                                () -> art.load(Kis2ChipArt.Asset.SIGNPOST_PATCH))),
                new Kis2ResultsArt(kis2Image().orElseThrow())::apply);
    }

    private Pattern[] loadTierOneLifeIcon() {
        try {
            return new Kis2PlayerArt(skReader(), skKnucklesArt()).loadLifeIcon();
        } catch (IOException | RuntimeException e) {
            LOGGER.warning("KiS2 life icon unavailable, keeping the stock Sonic 2 icon: " + e.getMessage());
            return null;
        }
    }

    /**
     * The S&amp;K donor surface over the logical S&amp;K ROM. KiS2 reads the same
     * S&amp;K player tables the S3K donor exposes ({@code ArtUnc_Knuckles},
     * {@code MapUnc_Knuckles}, {@code MapRUnc_Knuckles}, {@code AniKnuckles}), so
     * the donor contract is the explicit cross-game seam; this package never
     * depends on the S3K game package.
     */
    private PlayerSpriteArtProvider skKnucklesArt() {
        if (skKnucklesArt == null) {
            CrossGameDonorProvider donor = BuiltInRomDetectors.forGame(GameId.S3K)
                    .createModule().getCrossGameDonorProvider();
            if (donor == null) {
                throw new IllegalStateException("S&K donor provider unavailable for Knuckles in Sonic 2");
            }
            skKnucklesArt = donor.createPlayerArtProvider(skReader());
        }
        return skKnucklesArt;
    }

    private RomByteReader skReader() {
        if (sk == null) {
            try {
                sk = context.openLogicalRom(LogicalRom.SK);
            } catch (IOException e) {
                throw new UncheckedIOException("Logical S&K ROM unavailable for Knuckles in Sonic 2", e);
            }
        }
        return sk;
    }

    /**
     * The optional full lock-on image. Probed once: the context throws when
     * no user-supplied dump serves {@code KIS2}, which selects tier one.
     */
    private Optional<RomByteReader> kis2Image() {
        if (!kis2ImageProbed) {
            kis2ImageProbed = true;
            try {
                RomByteReader image = context.openLogicalRom(LogicalRom.KIS2);
                if (image != null && image.size() >= Kis2Constants.CHIP_WINDOW_END) {
                    kis2Image = image;
                    LOGGER.info("Knuckles in Sonic 2 tier two: reading the chip through the lock-on dump");
                } else {
                    LOGGER.info("Knuckles in Sonic 2 tier one: the lock-on image is too small for the chip");
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.info("Knuckles in Sonic 2 tier one: no lock-on dump available (" + e.getMessage() + ")");
            }
        }
        return Optional.ofNullable(kis2Image);
    }

    private Optional<Kis2ChipArt> chipArt() {
        if (chipArt == null) {
            Optional<RomByteReader> image = kis2Image();
            if (image.isEmpty()) {
                return Optional.empty();
            }
            RomByteReader dump = image.get();
            RomByteReader s2Window = dump.window(Kis2Constants.S2_WINDOW_START,
                    Kis2Constants.S2_WINDOW_END - Kis2Constants.S2_WINDOW_START);
            chipArt = new Kis2ChipArt(LockOnAddressSpace.tierTwo(skReader(), s2Window, dump));
        }
        return Optional.of(chipArt);
    }
}
