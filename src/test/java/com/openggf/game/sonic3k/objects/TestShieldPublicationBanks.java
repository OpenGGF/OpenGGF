package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameModule;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestShieldPublicationBanks {
    private GraphicsManager graphics;
    private StubObjectServices services;
    private Sonic3kObjectArtProvider provider;

    @BeforeEach void setup() throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
        assertNotNull(TestEnvironment.currentRom());
        provider = new Sonic3kObjectArtProvider();
        var load = Sonic3kObjectArtProvider.class.getDeclaredMethod("loadShieldArt");
        load.setAccessible(true);
        load.invoke(provider);
        GameModule module = mock(GameModule.class);
        when(module.getObjectArtProvider()).thenReturn(provider);
        services = new StubObjectServices() {
            @Override public GameModule gameModule() { return module; }
        };
    }

    @AfterEach void cleanup() {
        SessionManager.clear();
        GraphicsManager.destroyForReinit();
    }

    @ParameterizedTest
    @CsvSource({"false,false,false", "false,true,false", "true,false,false", "true,true,false",
            "false,false,true", "false,true,true", "true,false,true", "true,true,true"})
    void staggeredSonicShieldsKeepBothOwnersRomPixels(boolean reverse, boolean sat, boolean donor) throws Exception {
        if (donor) configureDonor();
        Sonic firstPlayer = new Sonic("sonic", (short) 60, (short) 60);
        Sonic secondPlayer = new Sonic("sonic_cpu", (short) 160, (short) 60);
        secondPlayer.setCpuControlled(true);
        var first = create(firstPlayer);
        var second = create(secondPlayer);
        first.triggerAttack();
        // Ani_InstaShield frames 0..2 use art tiles 0..22; frames 3..5 use 23..51.
        // Map frames 3 and 0 both display bank slots 0..5, with incompatible pixels.
        for (int i = 0; i < 3; i++) first.update(i, firstPlayer);
        second.triggerAttack();
        var firstState = lifecycle(first).captureRewindStateValue();
        var secondState = lifecycle(second).captureRewindStateValue();
        assertNotEquals(firstState.mappingFrame(), secondState.mappingFrame());
        var objectFirst = first.captureRewindState();
        var objectSecond = second.captureRewindState();
        var expectedFirst = publish(first, null, false);
        var expectedSecond = publish(second, null, false);
        assertFalse(expectedFirst.patternVersions().isEmpty());
        assertFalse(expectedSecond.patternVersions().isEmpty());
        var combined = publish(reverse ? second : first, reverse ? first : second, sat);
        assertPixels(expectedFirst, combined);
        assertPixels(expectedSecond, combined);

        for (int i = 0; i < 3; i++) { first.update(i, firstPlayer); second.update(i, secondPlayer); }
        publish(first, second, sat);
        lifecycle(first).restoreRewindStateValue(firstState);
        lifecycle(second).restoreRewindStateValue(secondState);
        lifecycle(first).refreshArtAfterRewindRestore();
        lifecycle(second).refreshArtAfterRewindRestore();
        var restored = publish(reverse ? second : first, reverse ? first : second, sat);
        assertEquals(combined, restored);
        assertPixels(expectedFirst, combined); // Later bank mutations cannot alter retained pixels.
        var recreatedFirst = create(firstPlayer);
        var recreatedSecond = create(secondPlayer);
        recreatedFirst.restoreRewindState(objectFirst);
        recreatedSecond.restoreRewindState(objectSecond);
        assertSame(lifecycle(first).renderer(), lifecycle(recreatedFirst).renderer());
        assertSame(lifecycle(second).renderer(), lifecycle(recreatedSecond).renderer());
        assertEquals(combined, publish(reverse ? recreatedSecond : recreatedFirst,
                reverse ? recreatedFirst : recreatedSecond, sat));
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void mainFireAndAuxiliaryCpuInstaKeepIndependentPixels(boolean reverse, boolean sat) throws Exception {
        Sonic main = new Sonic("sonic", (short) 60, (short) 60);
        Sonic cpu = new Sonic("sonic_cpu", (short) 160, (short) 60);
        cpu.setCpuControlled(true);
        // The spawner places CPU insta-shields in auxiliary space; main elemental
        // shields occupy the native Shield slot. Same-owner insta is suppressed.
        var insta = create(cpu);
        insta.triggerAttack();
        main.giveShield(com.openggf.game.ShieldType.FIRE);
        var fire = ObjectConstructionContext.construct(services, () -> new FireShieldObjectInstance(main));
        fire.setServices(services);
        fire.update(0, main);
        var expectedFire = publish(fire, null, false);
        var expectedInsta = publish(insta, null, false);
        assertFalse(expectedFire.patternVersions().isEmpty());
        assertFalse(expectedInsta.patternVersions().isEmpty());
        var combined = publish(reverse ? insta : fire, reverse ? fire : insta, sat);
        assertPixels(expectedFire, combined);
        assertPixels(expectedInsta, combined);
        var hiddenInsta = create(main);
        hiddenInsta.triggerAttack();
        assertTrue(publish(hiddenInsta, null, sat).tiles().isEmpty());
    }

    @Test void providerReloadRebindsPersistentObjectsWithoutResettingAnimation() throws Exception {
        Sonic main = new Sonic("sonic", (short) 60, (short) 60);
        var shield = create(main);
        shield.triggerAttack();
        shield.update(0, main);
        var expected = publish(shield, null, false);
        var oldRenderer = lifecycle(shield).renderer();
        // Exercise the provider's replacement source boundary without loading unrelated zone art.
        var load = Sonic3kObjectArtProvider.class.getDeclaredMethod("loadShieldArt");
        load.setAccessible(true);
        load.invoke(provider);
        assertEquals(expected, publish(shield, null, false));
        assertNotSame(oldRenderer, lifecycle(shield).renderer());
    }

    @Test void ownerBanksRemainBoundedAndResetWithProviderArt() {
        var banks = new com.openggf.game.sonic3k.ShieldPatternBanks();
        var art = provider.getShieldArtSet(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.INSTA_SHIELD);
        Sonic main = new Sonic("sonic", (short) 0, (short) 0);
        Sonic cpu = new Sonic("cpu", (short) 0, (short) 0);
        cpu.setCpuControlled(true);
        // CPU binding before main must not claim the native player's bank.
        var firstCpu = banks.renderer(cpu, art);
        assertEquals(0x49000, firstCpu.patternBankBase());
        var nativeRenderer = banks.renderer(main, art);
        assertEquals(0x079C, nativeRenderer.patternBankBase());
        assertSame(nativeRenderer, banks.renderer(main, art));
        assertSame(firstCpu, banks.renderer(cpu, art));
        for (int i = 1; i < 0x1000 / 36; i++) {
            Sonic extra = new Sonic("extra" + i, (short) 0, (short) 0);
            extra.setCpuControlled(true);
            assertEquals(0x49000 + i * 36, banks.renderer(extra, art).patternBankBase());
        }
        Sonic excess = new Sonic("excess", (short) 0, (short) 0);
        excess.setCpuControlled(true);
        assertThrows(IllegalStateException.class, () -> banks.renderer(excess, art));
        banks.clear();
        assertEquals(0x49000, banks.renderer(excess, art).patternBankBase());
        var oversized = new com.openggf.sprites.art.SpriteArtSet(art.artTiles(), art.mappingFrames(),
                art.dplcFrames(), art.paletteIndex(), art.basePatternIndex(), art.frameDelay(),
                37, art.animationProfile(), art.animationSet());
        assertThrows(IllegalArgumentException.class, () -> banks.renderer(main, oversized));
    }

    @Test void enablingDonationRelocatesAnAlreadyBoundNativeShieldOnlyOnce() throws Exception {
        Sonic main = new Sonic("sonic", (short) 60, (short) 60);
        var shield = create(main);
        shield.triggerAttack();
        for (int i = 0; i < 3; i++) shield.update(i, main);
        var before = publish(shield, null, false);
        var cursor = lifecycle(shield).captureRewindStateValue();
        var nativeRenderer = lifecycle(shield).renderer();
        assertEquals(0x079C, nativeRenderer.patternBankBase());
        var donor = com.openggf.game.CrossGameFeatureProvider.getInstance();
        try {
            setField(donor, "active", true);
            var after = publish(shield, null, false);
            var donated = lifecycle(shield).renderer();
            assertNotSame(nativeRenderer, donated);
            assertEquals(0x49000, donated.patternBankBase());
            assertEquals(cursor, lifecycle(shield).captureRewindStateValue());
            assertFalse(before.patternVersions().isEmpty());
            before.patternVersions().forEach((id, pixels) -> assertEquals(pixels,
                    after.patternVersions().get(id - 0x079C + 0x49000)));
            assertEquals(0x49000, provider.getShieldDplcRenderer(
                    com.openggf.game.sonic3k.Sonic3kObjectArtKeys.FIRE_SHIELD, main).patternBankBase());
            assertEquals(after, publish(shield, null, false));
            assertSame(donated, lifecycle(shield).renderer());
            setField(donor, "active", false);
            assertEquals(after, publish(shield, null, false));
            assertSame(donated, lifecycle(shield).renderer(), "retain safe allocation until provider reset");
        } finally {
            donor.close();
        }
    }

    @ParameterizedTest
    @CsvSource({"s1,sonic,false,false", "s1,sonic,false,true", "s1,sonic,true,false", "s1,sonic,true,true", "s2,sonic,false,false", "s2,sonic,false,true", "s2,sonic,true,false", "s2,sonic,true,true", "s2,tails,false,false", "s2,tails,false,true", "s2,tails,true,false", "s2,tails,true,true"})
    void nativeElementalShieldKeepsDonatedMainBodyPixels(String game, String character,
                                                        boolean reverse, boolean sat) throws Exception {
        String property = game.equals("s1") ? "sonic1.rom.path" : "sonic2.rom.path";
        String path = System.getProperty(property);
        org.junit.jupiter.api.Assumptions.assumeTrue(path != null && java.nio.file.Files.isRegularFile(java.nio.file.Path.of(path)),
                "Donor ROM unavailable: " + property);
        try (var rom = new com.openggf.data.Rom()) {
            rom.open(path);
            var reader = com.openggf.data.RomByteReader.fromRom(rom);
            var art = game.equals("s1") ? new com.openggf.game.sonic1.Sonic1PlayerArt(reader).loadSonic()
                    : character.equals("tails") ? new com.openggf.game.sonic2.Sonic2PlayerArt(reader).loadTails()
                    : new com.openggf.game.sonic2.Sonic2PlayerArt(reader).loadSonic();
            var donor = com.openggf.game.CrossGameFeatureProvider.getInstance();
            GameModule donorModule = game.equals("s1") ? new com.openggf.game.sonic1.Sonic1GameModule()
                    : new com.openggf.game.sonic2.Sonic2GameModule();
            setField(donor, "donorProvider", donorModule.getCrossGameDonorProvider());
            setField(donor, "active", true);
            assertNull(donor.getDonorShieldFactory(), "S1/S2 donation falls back to host elemental shield factory");
            GameModule host = mock(GameModule.class);
            when(host.getObjectArtProvider()).thenReturn(provider);
            when(host.getShieldFactory()).thenReturn(new com.openggf.game.sonic3k.Sonic3kGameModule().getShieldFactory());
            services = new StubObjectServices() {
                @Override public GameModule gameModule() { return host; }
                @Override public com.openggf.game.CrossGameFeatureProvider crossGameFeatures() { return donor; }
            };
            var spawner = new com.openggf.level.objects.DefaultPowerUpSpawner(null);
            setField(spawner, "cachedServices", services);
            com.openggf.sprites.playable.AbstractPlayableSprite main = character.equals("tails")
                    ? new com.openggf.sprites.playable.Tails("tails", (short) 60, (short) 60)
                    : new Sonic("sonic", (short) 60, (short) 60);
            main.setPowerUpSpawner(spawner);
            // Monitor reward routes through giveShield and the real host factory.
            // Bind shield before body art to cover initialization ordering.
            main.giveShield(com.openggf.game.ShieldType.FIRE);
            var fire = assertInstanceOf(FireShieldObjectInstance.class, main.getShieldObject());
            fire.setServices(services);
            fire.update(0, main);
            var body = new com.openggf.sprites.render.PlayerSpriteRenderer(art, graphics);
            main.setSpriteRenderer(body);
            // Ordinary walking: SonAni_Walk / TailsAni_Walk, not maximum-capacity
            // mappings that may belong to Super Sonic and exclude shield coexistence.
            final int frame = 0x0F;
            Runnable drawBody = () -> body.drawFrame(frame, 60, 60, false, false);
            Runnable drawShield = () -> fire.appendRenderCommands(new ArrayList<>());
            var expectedBody = SpritePresentationRenderer.prepare(graphics, 0, 0, drawBody);
            var expectedShield = SpritePresentationRenderer.prepare(graphics, 0, 0, drawShield);
            assertFalse(expectedBody.patternVersions().isEmpty());
            assertFalse(expectedShield.patternVersions().isEmpty());
            var combined = SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
                if (sat) graphics.beginSpriteSatCollection();
                if (reverse) { drawShield.run(); drawBody.run(); }
                else { drawBody.run(); drawShield.run(); }
                if (sat) graphics.endSpriteSatCollectionAndReplay();
            });
            assertPixels(expectedBody, combined);
            assertPixels(expectedShield, combined);
        } finally {
            com.openggf.game.CrossGameFeatureProvider.getInstance().close();
        }
    }

    private void configureDonor() throws Exception {
        var ctor = com.openggf.game.CrossGameFeatureProvider.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        var donor = ctor.newInstance();
        setField(donor, "donorReader", com.openggf.data.RomByteReader.fromRom(TestEnvironment.currentRom()));
        setField(donor, "donorProvider", new com.openggf.game.sonic3k.Sonic3kGameModule().getCrossGameDonorProvider());
        var load = donor.getClass().getDeclaredMethod("loadInstaShieldArt");
        load.setAccessible(true);
        load.invoke(donor);
        assertNotNull(donor.getInstaShieldArtSet());
        GameModule host = mock(GameModule.class);
        services = new StubObjectServices() {
            @Override public GameModule gameModule() { return host; }
            @Override public com.openggf.game.CrossGameFeatureProvider crossGameFeatures() { return donor; }
        };
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private InstaShieldObjectInstance create(Sonic player) {
        var shield = ObjectConstructionContext.construct(services, () -> new InstaShieldObjectInstance(player));
        shield.setServices(services);
        return shield;
    }

    private SpritePresentation.Frame publish(com.openggf.level.objects.ShieldObjectInstance first,
                                             com.openggf.level.objects.ShieldObjectInstance second, boolean sat) {
        return SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
            if (sat) graphics.beginSpriteSatCollection();
            first.appendRenderCommands(new ArrayList<>());
            if (second != null) second.appendRenderCommands(new ArrayList<>());
            if (sat) graphics.endSpriteSatCollectionAndReplay();
        });
    }

    private static void assertPixels(SpritePresentation.Frame isolated, SpritePresentation.Frame combined) {
        isolated.patternVersions().forEach((id, pixels) -> assertEquals(pixels,
                combined.patternVersions().get(id), "owner ROM pixels at tile " + Integer.toHexString(id)));
    }

    private static ShieldAnimationArtLifecycle lifecycle(Object shield) throws Exception {
        var field = shield.getClass().getDeclaredField("animationLifecycle");
        field.setAccessible(true);
        return (ShieldAnimationArtLifecycle) field.get(shield);
    }
}
