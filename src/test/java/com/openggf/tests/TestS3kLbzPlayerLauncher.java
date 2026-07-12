package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLbzPlayerLauncher {
    private static final int OBJECT_ID_LBZ_PLAYER_LAUNCHER = 0x15;
    private static final int LAUNCHER_X = 0x1000;
    private static final int LAUNCHER_Y = 0x0500;

    private static Object oldSkipIntros;
    private static Object oldMainCharacter;
    private static Object oldSidekickCharacter;

    private HeadlessTestFixture fixture;
    private AbstractPlayableSprite sonic;

    @BeforeAll
    static void configure() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        oldSidekickCharacter = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
    }

    @AfterAll
    static void restoreConfig() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                oldSidekickCharacter != null ? oldSidekickCharacter : "tails");
    }

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 0)
                .startPosition((short) LAUNCHER_X, (short) LAUNCHER_Y)
                .startPositionIsCentre()
                .build();
        sonic = fixture.sprite();
        sonic.setCentreX((short) LAUNCHER_X);
        sonic.setCentreY((short) LAUNCHER_Y);
        sonic.setAir(false);
        sonic.setXSpeed((short) 0);
        sonic.setGSpeed((short) 0);
        sonic.setMoveLockTimer(0);
        sonic.setDirection(Direction.RIGHT);
        sonic.setPushing(true);
    }

    @Test
    void airbornePlayerInsideLauncherDoesNotLaunch() {
        ObjectInstance launcher = createLauncher(0x00, 0);
        sonic.setAir(true);

        for (int i = 0; i < 4; i++) {
            launcher.update(i, sonic);
        }

        assertEquals(0, sonic.getXSpeed());
        assertEquals(0, sonic.getGSpeed());
        assertEquals(0, sonic.getMoveLockTimer());
    }

    @Test
    void groundedPlayerLaunchesOnFourthOverlappingFrameUsingSubtypeSpeedAndFacing() {
        ObjectInstance launcher = createLauncher(0x02, 1);

        for (int i = 0; i < 3; i++) {
            launcher.update(i, sonic);
            assertEquals(0, sonic.getXSpeed(),
                    "ROM waits until counter $38 reaches 4 before writing x_vel");
        }

        launcher.update(3, sonic);

        assertEquals((short) -0x0A00, sonic.getXSpeed());
        assertEquals((short) -0x0A00, sonic.getGSpeed());
        assertEquals(15, sonic.getMoveLockTimer());
        assertEquals(Direction.LEFT, sonic.getDirection());
    }

    @Test
    void extraEngineSidekickLaunchesIndependentlyOfNativeP2() {
        AbstractPlayableSprite nativeP2 = new Sonic("native_p2", (short) (LAUNCHER_X - 0x80),
                (short) LAUNCHER_Y);
        nativeP2.setCpuControlled(true);
        GameServices.sprites().addSprite(nativeP2, "sonic");
        AbstractPlayableSprite extraSidekick = new Sonic("extra_sidekick", (short) LAUNCHER_X,
                (short) LAUNCHER_Y);
        extraSidekick.setCpuControlled(true);
        GameServices.sprites().addSprite(extraSidekick, "sonic");
        List<AbstractPlayableSprite> sidekicks = GameServices.sprites().getSidekicks();
        assertTrue(sidekicks.size() >= 2,
                "fixture plus registered extension players must expose a native P2 and an extra sidekick");
        nativeP2 = sidekicks.get(0);
        extraSidekick = sidekicks.getLast();
        sonic.setCentreX((short) (LAUNCHER_X - 0x80));
        nativeP2.setCentreX((short) (LAUNCHER_X - 0x80));
        extraSidekick.setCentreX((short) LAUNCHER_X);
        extraSidekick.setCentreY((short) LAUNCHER_Y);
        extraSidekick.setAir(false);
        extraSidekick.setXSpeed((short) 0);
        extraSidekick.setGSpeed((short) 0);

        ObjectInstance launcher = createLauncher(0x00, 0);
        for (int i = 0; i < 4; i++) {
            launcher.update(i, sonic);
        }

        assertEquals((short) 0x1000, extraSidekick.getXSpeed(),
                "OpenGGF extension sidekicks must receive the native P2 launcher behavior independently");
        assertEquals(15, extraSidekick.getMoveLockTimer());
    }

    @Test
    void launchProgressFollowsSidekickIdentityWhenEarlierSidekickLeavesRoster() {
        AbstractPlayableSprite departingSidekick = new Sonic("departing_sidekick",
                (short) (LAUNCHER_X - 0x80), (short) LAUNCHER_Y);
        departingSidekick.setCpuControlled(true);
        GameServices.sprites().addTemporarySidekick(
                departingSidekick, "sonic",
                () -> new Sonic("departing_sidekick", (short) 0, (short) 0));
        AbstractPlayableSprite launchingSidekick = new Sonic("launching_sidekick",
                (short) LAUNCHER_X, (short) LAUNCHER_Y);
        launchingSidekick.setCpuControlled(true);
        GameServices.sprites().addTemporarySidekick(
                launchingSidekick, "sonic",
                () -> new Sonic("launching_sidekick", (short) 0, (short) 0));
        sonic.setCentreX((short) (LAUNCHER_X - 0x80));
        launchingSidekick.setCentreX((short) LAUNCHER_X);
        launchingSidekick.setCentreY((short) LAUNCHER_Y);
        launchingSidekick.setAir(false);
        launchingSidekick.setXSpeed((short) 0);
        launchingSidekick.setGSpeed((short) 0);

        ObjectInstance launcher = createLauncher(0x00, 0);
        launcher.update(0, sonic);
        launcher.update(1, sonic);
        assertEquals(2, counterFor(launcher, launchingSidekick));
        assertTrue(GameServices.sprites().removeTemporarySidekick(departingSidekick));
        launcher.update(2, sonic);
        assertEquals(3, counterFor(launcher, launchingSidekick),
                "changing from extension sidekick to native P2 must retain launch progress");
        launcher.update(3, sonic);

        assertEquals((short) 0x1000, launchingSidekick.getXSpeed(),
                "launcher progress must stay with the same player when its team slot changes");
        assertEquals(15, launchingSidekick.getMoveLockTimer());
    }

    @Test
    void newNativeP2DoesNotInheritDepartedSidekickLaunchProgress() {
        AbstractPlayableSprite departingSidekick = new Sonic("departing_native_p2",
                (short) LAUNCHER_X, (short) LAUNCHER_Y);
        departingSidekick.setCpuControlled(true);
        GameServices.sprites().addTemporarySidekick(
                departingSidekick, "sonic",
                () -> new Sonic("departing_native_p2", (short) 0, (short) 0));
        departingSidekick.setCentreX((short) LAUNCHER_X);
        departingSidekick.setCentreY((short) LAUNCHER_Y);
        departingSidekick.setAir(false);
        AbstractPlayableSprite replacementSidekick = new Sonic("replacement_native_p2",
                (short) (LAUNCHER_X - 0x80), (short) LAUNCHER_Y);
        replacementSidekick.setCpuControlled(true);
        GameServices.sprites().addTemporarySidekick(
                replacementSidekick, "sonic",
                () -> new Sonic("replacement_native_p2", (short) 0, (short) 0));
        replacementSidekick.setCentreX((short) (LAUNCHER_X - 0x80));
        replacementSidekick.setCentreY((short) LAUNCHER_Y);
        replacementSidekick.setAir(false);
        sonic.setCentreX((short) (LAUNCHER_X - 0x80));

        ObjectInstance launcher = createLauncher(0x00, 0);
        launcher.update(0, sonic);
        launcher.update(1, sonic);
        assertTrue(GameServices.sprites().removeTemporarySidekick(departingSidekick));
        replacementSidekick.setCentreX((short) LAUNCHER_X);
        launcher.update(2, sonic);
        launcher.update(3, sonic);

        assertEquals(0, replacementSidekick.getXSpeed(),
                "a newly promoted native P2 must start its own four-frame launcher sequence");
        launcher.update(4, sonic);
        launcher.update(5, sonic);
        assertEquals((short) 0x1000, replacementSidekick.getXSpeed());
    }

    private ObjectInstance createLauncher(int subtype, int renderFlags) {
        ObjectSpawn spawn = new ObjectSpawn(LAUNCHER_X, LAUNCHER_Y,
                OBJECT_ID_LBZ_PLAYER_LAUNCHER, subtype, renderFlags, false, 0);
        ObjectInstance launcher = GameServices.module().createObjectRegistry().create(spawn);
        assertEquals("LBZPlayerLauncher", launcher.getName());
        GameServices.level().getObjectManager().addDynamicObject(launcher);
        return launcher;
    }

    @SuppressWarnings("unchecked")
    private static int counterFor(ObjectInstance launcher, AbstractPlayableSprite player) {
        try {
            Field field = launcher.getClass().getDeclaredField("countersByPlayer");
            field.setAccessible(true);
            return ((Map<AbstractPlayableSprite, Integer>) field.get(launcher)).getOrDefault(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to inspect launcher player counters", e);
        }
    }
}
