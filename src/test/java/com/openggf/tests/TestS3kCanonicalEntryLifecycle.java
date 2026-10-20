package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.launch.LaunchProfile;
import com.openggf.game.launch.LaunchProfileApplier;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindDeterminismAuditor;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Pattern;
import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.managers.SpriteManagerInternalAccess;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.graphics.RenderContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Short, independent normal-entry bindings for the eight canonical S3KL acts
 * without an owning entry matrix. This is a ROM entry/graph floor, not a route
 * completion or native pixel claim. Native intro owners remain active.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCanonicalEntryLifecycle {
    record Act(String name, int zone, int act) { }
    private static final List<Act> ACTS = List.of(
            new Act("AIZ2", 0, 1), new Act("HCZ2", 1, 1),
            new Act("MGZ1", 2, 0), new Act("MGZ2", 2, 1),
            new Act("CNZ1", 3, 0), new Act("CNZ2", 3, 1),
            new Act("ICZ1", 5, 0), new Act("ICZ2", 5, 1));
    record Entry(Act binding, String main, WidescreenAspect aspect, String donor, String followers) {
        Entry(Act binding, String main, WidescreenAspect aspect, String donor) {
            this(binding, main, aspect, donor, "");
        }
    }

    static Stream<Entry> nativeBindings() {
        return ACTS.stream().flatMap(act -> Stream.of("sonic", "tails", "knuckles")
                .map(main -> new Entry(act, main, WidescreenAspect.NATIVE_4_3, "off")));
    }

    static Stream<Entry> cnzSoloCarrierConfigurations() {
        return Arrays.stream(WidescreenAspect.values()).flatMap(aspect ->
                Stream.of("off", "s1", "s2").map(donor ->
                        new Entry(new Act("CNZ1", 3, 0), "sonic", aspect, donor)));
    }

    static Stream<Entry> configuredDonorControls() {
        return cnzSoloCarrierConfigurations().map(row -> new Entry(row.binding(), row.main(),
                row.aspect(), row.donor(), row.donor().equals("s1") ? "sonic" : "tails"));
    }

    @ParameterizedTest(name = "configured CNZ team retains donor art {0}")
    @MethodSource("configuredDonorControls")
    void configuredParticipantsKeepTheirDonorBodyPaletteAndAuxiliaryArt(Entry row) throws Exception {
        var fixture = boot(row);
        assertOwners(row);
        assertArtSources(row);
        stepAndAssert(fixture, row, 6);
        replayTwice(fixture, row);
    }

    @ParameterizedTest(name = "CNZ native carrier natural deletion and recreation {0}")
    @MethodSource("cnzSoloCarrierConfigurations")
    void naturallyDeletedCarrierRecreatesNativeArtAndReplaysBothWorldStates(Entry row) throws Exception {
        var fixture = boot(row);
        stepAndAssert(fixture, row, 2);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var live = registry.capture();
        stepAndAssert(fixture, row, 30);
        var afterCarryWindow = registry.capture();
        boolean vanished = false;
        for (int frame = 0; frame < 900; frame++) {
            fixture.stepIdleFrames(1);
            assertFalse(GameServices.sprites().getMainPlayable().getDead());
            assertEquals(3, GameServices.gameState().getLives(), "native flyoff cannot respawn P1");
            if (GameServices.sprites().getRegisteredSidekicks().isEmpty()) {
                vanished = true;
                break;
            }
        }
        assertTrue(vanished, "loc_140AC must naturally retire the transient carrier");
        assertOwners(row, false);
        var absent = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(live);
            fixture.runner().primeInputState(new Bk2FrameInput(0, 0, 0, false, ""));
            assertSameWorld(live, registry.capture(), "restored carrier after native flyoff " + row);
            assertOwners(row);
            assertArtSources(row);
            stepAndAssert(fixture, row, 30);
            assertSameWorld(afterCarryWindow, registry.capture(), "carrier replay " + row);
            registry.restore(absent);
            assertSameWorld(absent, registry.capture(), "reverse restore preserves native NPC absence " + row);
            assertOwners(row, false);
        }
    }

    @org.junit.jupiter.api.Test
    void nativeArtRegistrationSurvivesSceneRosterRetirementWithoutChangingPublicSnapshots() throws Exception {
        var row = new Entry(new Act("CNZ1", 3, 0), "sonic", WidescreenAspect.NATIVE_4_3, "s1");
        var fixture = boot(row);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        SpriteManagerInternalAccess.retireAllSidekicksForRewind(GameServices.sprites());
        assertOwners(row, false);
        registry.restore(before);
        assertSameWorld(before, registry.capture(), "scene-retired native roster restores its original RAM");
        assertOwners(row);
        // A later ordinary art refresh must still use native art for the retired/reinstated NPC.
        GameServices.level().refreshPlayableSpriteArt();
        assertArtSources(row);
        replayTwice(fixture, row);
    }

    private final EnumMap<SonicConfiguration, Object> saved = new EnumMap<>(SonicConfiguration.class);
    private SonicConfigurationService config;

    @BeforeEach
    void saveConfiguration() {
        config = SonicConfigurationService.getInstance();
        for (var key : SonicConfiguration.values()) {
            if (config.hasSessionOverride(key)) saved.put(key, config.getConfigValue(key));
        }
    }

    @AfterEach
    void restoreConfiguration() {
        CrossGameFeatureProvider.getInstance().resetState();
        config.clearSessionOverrides();
        saved.forEach(config::setSessionOverride);
        config.resolveDisplayAspect();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @ParameterizedTest(name = "canonical native entry {0}")
    @MethodSource("nativeBindings")
    void nativeMainCharacterStartsFromTheBoundRomTableAndReplaysTwice(Entry row) throws IOException {
        var fixture = boot(row);
        assertRomStart(row);
        assertOwners(row);
        stepAndAssert(fixture, row, 6);
        replayTwice(fixture, row);
    }

    @ParameterizedTest(name = "CNZ1 native NPC with configured solo Sonic {0}")
    @MethodSource("cnzSoloCarrierConfigurations")
    void cnzSoloCarrierHasNativeArtAndOwnershipUnderEveryPresetAndDonor(Entry row) throws Exception {
        var fixture = boot(row);
        assertRomStart(row);
        assertOwners(row);
        // loc_13A10 first arms routine $C; only its next dispatch writes x_vel=$100.
        fixture.stepIdleFrames(1);
        assertEquals((short) 0, GameServices.sprites().getMainPlayable().getXSpeed());
        fixture.stepIdleFrames(1);
        var main = GameServices.sprites().getMainPlayable();
        assertEquals((short) 0x100, main.getXSpeed(), "native carry, not donor follow AI");
        assertTrue(main.isObjectControlled());
        assertTrue(main.getAir());
        assertOwners(row);
        assertArtSources(row);
        replayTwice(fixture, row);
    }

    private HeadlessTestFixture boot(Entry row) {
        config.clearSessionOverrides();
        // Apply the actual supported menu profile. No raw width, position, counters,
        // object routines or physics are seeded by these normal-entry scenarios.
        new LaunchProfileApplier(config).apply(new LaunchProfile(true, row.donor(), false,
                row.aspect().name(), row.main(), row.followers().isEmpty() ? "none" : row.followers()), MasterTitleScreen.GameEntry.SONIC_3K);
        config.resolveDisplayAspect();
        assertEquals(row.main(), config.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals(row.followers(), config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        if (!row.donor().equals("off")) {
            var rom = row.donor().equals("s1") ? RomTestUtils.ensureSonic1RomAvailable()
                    : RomTestUtils.ensureSonic2RomAvailable();
            assertNotNull(rom, "required donor ROM " + row.donor());
            config.setSessionOverride(row.donor().equals("s1") ? SonicConfiguration.SONIC_1_ROM
                    : SonicConfiguration.SONIC_2_ROM, rom.getAbsolutePath());
        }
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var builder = HeadlessTestFixture.builder()
                .withZoneAndAct(row.binding().zone(), row.binding().act())
                .withFreshLevelStartLifecycle();
        if (!row.donor().equals("off")) builder.withCrossGameDonation(row.donor());
        return builder.build();
    }

    static void assertRomStart(Entry row) throws IOException {
        // Shipped SpawnLevelMainSprites selects the character table. The eight
        // canonical bindings use that table, except ICZ1_ScreenInit's fresh
        // Knuckles arrival which overwrites Y=$65C with $660 ($53608-$53648).
        boolean iczArrival = row.main().equals("knuckles") && row.binding().zone() == 5
                && row.binding().act() == 0;
        if (iczArrival) {
            // Execute the genuine deferred ScreenInit/Load_Sprites/Process_Sprites
            // boundary before comparing its native override. Its first swing returns zero.
            assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        }
        int table = row.main().equals("knuckles") ? Sonic3kConstants.KNUX_START_LOCATIONS_ADDR
                : Sonic3kConstants.SONIC_START_LOCATIONS_ADDR;
        int address = table + (row.binding().zone() * 2 + row.binding().act()) * 4;
        var rom = GameServices.rom().getRom();
        var main = GameServices.sprites().getMainPlayable();
        assertEquals(rom.read16BitAddr(address), main.getCentreX() & 0xFFFF, "ROM start X");
        int expectedY = iczArrival ? 0x660 : rom.read16BitAddr(address + 2);
        assertEquals(expectedY, main.getCentreY() & 0xFFFF, "ROM start Y / native ScreenInit override");
    }

    /**
     * A Sonic-led CNZ1 start always has one Obj_Tails story writer: loc_68D8 writes
     * it to Player_2 for solo Sonic (sonic3k.asm:8190-8197) and loc_13A10 gives it
     * the carry (sonic3k.asm:26389-26415). A configured Tails P2 is that carrier;
     * any other configured follower keeps its place and the native actor joins it.
     */
    private static boolean hasNativeCarrier(Entry row) {
        return row.binding().zone() == 3 && row.binding().act() == 0 && row.main().equals("sonic")
                && !row.followers().split(",")[0].equals("tails");
    }

    private static void assertOwners(Entry row) {
        assertOwners(row, hasNativeCarrier(row));
    }

    private static void assertOwners(Entry row, boolean carrierPresent) {
        assertEquals(row.binding().zone(), GameServices.level().getCurrentZone());
        assertEquals(row.binding().act(), GameServices.level().getCurrentAct());
        assertNotNull(GameServices.level().getCurrentLevel());
        assertNotNull(GameServices.level().getObjectManager());
        SozAcceptanceConfigurations.assertDisplay(row.aspect());
        var main = GameServices.sprites().getMainPlayable();
        assertEquals(row.main(), main.characterKey().persisted());
        assertSame(main, GameServices.camera().getFocusedSprite(), "current P1 owns camera focus");
        assertArt(main, "configured main " + row.donor());
        var followers = GameServices.sprites().getRegisteredSidekicks();
        var nativeP2 = GameServices.level().getObjectManager().getObjectServices().playerQuery().nativeP2OrNull();
        int configured = row.followers().isEmpty() ? 0 : 1;
        assertEquals(configured + (carrierPresent ? 1 : 0), followers.size(),
                "configured followers plus the native transient NPC, if live");
        if (configured == 1) {
            var follower = followers.getFirst();
            assertEquals(row.followers(), follower.characterKey().persisted());
            assertSame(follower, nativeP2, "the configured follower keeps physical Player_2");
            assertTrue(follower.isCpuControlled());
            assertSame(main, follower.getCpuController().getLeader());
            assertFalse(follower.getCpuController().isTransientCarrySidekick());
            assertFalse(SpriteManagerInternalAccess.usesNativeScriptedArt(GameServices.sprites(), follower));
            SozAcceptanceConfigurations.assertUsableTeam(row.donor());
        }
        if (carrierPresent) {
            // loc_68D8 creates native Obj_Tails for solo Sonic. It is not a
            // configured donor participant; S1's configured-Tails rejection
            // must not be applied to this throwaway script owner.
            var carrier = assertInstanceOf(Tails.class, followers.getLast());
            assertNotSame(main, carrier);
            if (configured == 0) assertSame(carrier, nativeP2, "native Player_2 resolves the carrier");
            assertTrue(carrier.isCpuControlled());
            assertNotNull(carrier.getCpuController());
            assertTrue(carrier.getCpuController().isTransientCarrySidekick());
            assertTrue(SpriteManagerInternalAccess.usesNativeScriptedArt(GameServices.sprites(), carrier));
            assertSame(main, carrier.getCpuController().getLeader());
            assertArt(carrier, "native CNZ Tails NPC with " + row.donor() + " configured donor");
        } else if (configured == 0) {
            assertTrue(followers.isEmpty(), "no configured or still-live scripted P2");
            assertNull(nativeP2);
        }
        assertEquals(row.followers(), SonicConfigurationService.getInstance()
                .getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE), "NPC never becomes a configured follower");
        assertEquals(!row.donor().equals("off"), CrossGameFeatureProvider.isActive());
        if (!row.donor().equals("off")) assertEquals(row.donor(),
                CrossGameFeatureProvider.getInstance().getDonorGameId());
    }

    private static void assertArtSources(Entry row) throws Exception {
        var host = (PlayerSpriteArtProvider) GameServices.level().getGame();
        var configured = row.donor().equals("off") ? host : CrossGameFeatureProvider.getInstance();
        var main = GameServices.sprites().getMainPlayable();
        assertFalse(SpriteManagerInternalAccess.usesNativeScriptedArt(GameServices.sprites(), main));
        var mainProvider = SpriteManagerInternalAccess.hasBorrowedNativePlayerArt(GameServices.sprites(), main)
                ? host : configured;
        assertRendererSource(main.getSpriteRenderer(), mainProvider.loadPlayerSpriteArt(row.main()));
        assertPaletteSource(main.getSpriteRenderer(), mainProvider.loadCharacterPalette(row.main()));
        var followers = GameServices.sprites().getRegisteredSidekicks();
        for (var follower : followers) {
            boolean nativeActor = hasNativeCarrier(row) && follower == followers.getLast();
            assertEquals(nativeActor, SpriteManagerInternalAccess.usesNativeScriptedArt(GameServices.sprites(), follower));
            var provider = nativeActor ? host : configured;
            String character = follower.characterKey().persisted();
            assertRendererSource(follower.getSpriteRenderer(), provider.loadPlayerSpriteArt(character));
            assertPaletteSource(follower.getSpriteRenderer(), provider.loadCharacterPalette(character));
            if (nativeActor) {
                assertNotNull(follower.getSpindashDustController());
                assertRendererSource(follower.getSpindashDustController().getRenderer(),
                        ((com.openggf.data.SpindashDustArtProvider) GameServices.level().getGame())
                                .loadSpindashDustArt(character));
                assertNotNull(follower.getTailsTailsController());
                assertRendererSource(follower.getTailsTailsController().getRenderer(),
                        GameServices.module().loadTailsTailArt());
                assertPaletteSource(follower.getTailsTailsController().getRenderer(),
                        host.loadCharacterPalette(character));
            }
        }
    }

    static void assertRendererSource(PlayerSpriteRenderer renderer, SpriteArtSet expected) throws Exception {
        assertNotNull(renderer);
        assertNotNull(expected);
        var field = PlayerSpriteRenderer.class.getDeclaredField("artSet");
        field.setAccessible(true);
        var actual = (SpriteArtSet) field.get(renderer);
        assertEquals(expected.mappingFrames(), actual.mappingFrames(), "ROM mapping source");
        assertEquals(expected.dplcFrames(), actual.dplcFrames(), "ROM DPLC source");
        assertEquals(expected.bankSize(), actual.bankSize(), "actual native/donor bank size");
        assertEquals(expected.artTiles().length, actual.artTiles().length);
        for (int tile = 0; tile < expected.artTiles().length; tile++) {
            byte[] expectedPixels = new byte[64], actualPixels = new byte[64];
            expected.artTiles()[tile].copyInto(expectedPixels, 0);
            actual.artTiles()[tile].copyInto(actualPixels, 0);
            assertArrayEquals(expectedPixels, actualPixels, "ROM art tile " + tile);
        }
    }

    static void assertPaletteSource(PlayerSpriteRenderer renderer, com.openggf.level.Palette expected)
            throws Exception {
        assertNotNull(expected);
        var field = PlayerSpriteRenderer.class.getDeclaredField("renderContext");
        field.setAccessible(true);
        var context = (RenderContext) field.get(renderer);
        var actual = context == null ? GameServices.level().getCurrentLevel().getPalette(0) : context.getPalette(0);
        assertNotNull(actual);
        assertTrue(expected.dataEquals(actual), "participant palette comes from its ROM art owner");
    }

    private static void assertArt(AbstractPlayableSprite player, String context) {
        assertNotNull(player.getSpriteRenderer(), context + " ROM-backed renderer");
        assertNotNull(player.getAnimationProfile(), context + " animation profile");
        assertNotNull(player.getAnimationSet(), context + " ROM-backed animation scripts");
        assertTrue(player.getAnimationFrameCount() > 0, context + " decoded mappings");
    }

    private static void stepAndAssert(HeadlessTestFixture fixture, Entry row, int frames) {
        int lives = GameServices.gameState().getLives();
        for (int frame = 0; frame < frames; frame++) {
            fixture.stepIdleFrames(1);
            assertFalse(GameServices.sprites().getMainPlayable().getDead(), "P1 survives normal entry");
            assertEquals(lives, GameServices.gameState().getLives(), "entry cannot silently respawn P1");
            assertOwners(row);
        }
    }

    private static void replayTwice(HeadlessTestFixture fixture, Entry row) {
        assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
        assertFalse(GameServices.fade().hasPendingCompletion(), "raw checkpoints cannot carry callbacks");
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        stepAndAssert(fixture, row, 30);
        var after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            fixture.runner().primeInputState(new Bk2FrameInput(0, 0, 0, false, ""));
            assertSameWorld(before, registry.capture(), row + " restore=" + cycle);
            assertOwners(row);
            try { assertArtSources(row); } catch (Exception exception) { throw new AssertionError(exception); }
            stepAndAssert(fixture, row, 30);
            assertSameWorld(after, registry.capture(), row + " replay=" + cycle);
        }
    }

    static void assertSameWorld(CompositeSnapshot expected, CompositeSnapshot actual, String context) {
        assertSameWorld(expected, actual, context, 30);
    }

    static void assertSameWorld(CompositeSnapshot expected, CompositeSnapshot actual, String context, int replayFrames) {
        var differences = new ArrayList<String>();
        boolean divergent = new RewindDeterminismAuditor(differences::add)
                .report(0, replayFrames, contentSnapshot(expected), contentSnapshot(actual));
        assertFalse(divergent, () -> context + ": " + String.join("\n", differences));
    }

    /** Compare every captured title-card field and every pattern byte, never Pattern identity. */
    private static CompositeSnapshot contentSnapshot(CompositeSnapshot snapshot) {
        var entries = new LinkedHashMap<>(snapshot.entries());
        Object title = entries.get("s3k-title-card");
        if (title != null) {
            var fields = new LinkedHashMap<String, Object>();
            for (var component : title.getClass().getRecordComponents()) {
                try {
                    Object value = component.getAccessor().invoke(title);
                    if (value instanceof Pattern[] patterns) {
                        byte[][] pixels = new byte[patterns.length][];
                        for (int i = 0; i < patterns.length; i++) {
                            if (patterns[i] != null) {
                                pixels[i] = new byte[64];
                                patterns[i].copyInto(pixels[i], 0);
                            }
                        }
                        value = pixels;
                    }
                    fields.put(component.getName(), value);
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError("cannot compare title-card field " + component.getName(), exception);
                }
            }
            entries.put("s3k-title-card", new TitleState(fields));
        }
        return new CompositeSnapshot(entries);
    }
    public record TitleState(Map<String, Object> fields) { }
}
