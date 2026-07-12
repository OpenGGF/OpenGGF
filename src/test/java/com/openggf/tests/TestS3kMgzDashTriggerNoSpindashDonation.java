package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.rules.CrossGameRuleComposer;
import com.openggf.game.rules.GameRules;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.MGZDashTriggerObjectInstance;
import com.openggf.game.sonic3k.objects.MGZTriggerPlatformObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ROM-backed compatibility coverage for the MGZ1 index-7 dash-trigger route.
 * The real layout pairs the trigger at {@code (0x12D0,0x5C4)} with four
 * following trigger platforms keyed by the same low subtype nibble.
 */
@RequiresRom(SonicGame.SONIC_3K)
@Isolated("Uses the singleton headless gameplay runtime and shared MGZ level")
class TestS3kMgzDashTriggerNoSpindashDonation {

    private static final int TRIGGER_X = 0x12D0;
    private static final int TRIGGER_Y = 0x05C4;
    private static final int TRIGGER_INDEX = 7;
    private static final int SUSTAINED_RUN_FRAMES = 40;

    private static SharedLevel sharedLevel;
    private static Object oldSkipIntros;
    private static Object oldMainCharacter;
    private static Object oldSidekickCharacter;

    private HeadlessTestFixture fixture;
    private Sonic sprite;
    private MGZDashTriggerObjectInstance trigger;
    private List<MGZTriggerPlatformObjectInstance> pairedPlatforms;

    @BeforeAll
    static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        oldSidekickCharacter = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_MGZ, 0);
    }

    @AfterAll
    static void cleanup() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                oldSidekickCharacter != null ? oldSidekickCharacter : "tails");
        if (sharedLevel != null) {
            sharedLevel.dispose();
            sharedLevel = null;
        }
    }

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder().withSharedLevel(sharedLevel).build();
        sprite = (Sonic) fixture.sprite();
        Sonic3kLevelTriggerManager.reset();
        teleportNearRoute();
        trigger = findClosest(MGZDashTriggerObjectInstance.class, TRIGGER_X, TRIGGER_Y);
        assertNotNull(trigger, "Expected the real MGZ1 index-7 dash trigger to be active");
        assertEquals(TRIGGER_INDEX, trigger.getSpawn().subtype() & 0x0F);
        pairedPlatforms = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(MGZTriggerPlatformObjectInstance.class::isInstance)
                .map(MGZTriggerPlatformObjectInstance.class::cast)
                .filter(platform -> (platform.getSpawn().subtype() & 0x0F) == TRIGGER_INDEX)
                .toList();
        assertFalse(pairedPlatforms.isEmpty(), "Expected index-7 trigger platforms in the placement window");
    }

    @Test
    void nativeSpindashArmsThePairedMechanismExactly() {
        placeFlushLeftOfTrigger();
        fixture.stepFrame(false, true, false, false, false);
        fixture.stepFrame(false, true, false, false, true);

        assertEquals(Sonic3kAnimationIds.SPINDASH.id(), sprite.getAnimationId());
        assertTrue(Sonic3kLevelTriggerManager.testAny(TRIGGER_INDEX));
    }

    @Test
    void noSpindashDonorSustainedGroundedRunOpensTheMandatoryPairedMechanism() {
        installRules(sprite, noSpindashDonorRules());
        assertFalse(sprite.getGameRules().playerCapability().spindashEnabled());
        landAtLeftEdgeOfTrigger();
        assertFalse(sprite.getAir(), "Donor fallback observation must begin from a real grounded state");
        sprite.setDirectionalInputPressed(false, false, false, true);
        int[] initialPlatformY = pairedPlatforms.stream().mapToInt(ObjectInstance::getY).toArray();
        int maxCentreX = sprite.getCentreX();

        for (int frame = 0; frame < SUSTAINED_RUN_FRAMES; frame++) {
            fixture.stepFrame(false, false, false, true, false);
            maxCentreX = Math.max(maxCentreX, sprite.getCentreX());
        }

        boolean platformMoved = false;
        for (int i = 0; i < pairedPlatforms.size(); i++) {
            platformMoved |= pairedPlatforms.get(i).getY() != initialPlatformY[i];
        }
        assertTrue(Sonic3kLevelTriggerManager.testAny(TRIGGER_INDEX) && platformMoved,
                "S1-capability Sonic sustained grounded right input at the real MGZ1 index-7 mechanism, "
                        + "but the trigger/platform route stayed closed; maxCentreX=0x"
                        + Integer.toHexString(maxCentreX)
                        + ", air=" + sprite.getAir()
                        + ", pushing=" + sprite.getPushing()
                        + ", centreY=0x" + Integer.toHexString(sprite.getCentreY() & 0xFFFF)
                        + ", right=" + sprite.isRightPressed()
                        + ", triggerActive=" + Sonic3kLevelTriggerManager.testAny(TRIGGER_INDEX));
    }

    private void teleportNearRoute() {
        sprite.setCentreX((short) (TRIGGER_X - 0x40));
        sprite.setCentreY((short) TRIGGER_Y);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAir(false);
        Camera camera = fixture.camera();
        camera.updatePosition(true);
        GameServices.level().getObjectManager().reset(camera.getX());
        fixture.stepIdleFrames(1);
    }

    private void placeFlushLeftOfTrigger() {
        sprite.setCentreX((short) (trigger.getX() - (27 + sprite.getXRadius())));
        sprite.setCentreY((short) trigger.getY());
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAir(false);
        sprite.setRolling(false);
        sprite.setSpindash(false);
        sprite.setPushing(false);
    }

    private void landAtLeftEdgeOfTrigger() {
        sprite.setCentreX((short) (trigger.getX() - (27 + sprite.getXRadius()) + 1));
        sprite.setCentreY((short) (trigger.getY() - 0x40));
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAir(true);
        sprite.setRolling(false);
        sprite.setSpindash(false);
        sprite.setPushing(false);
        for (int frame = 0; frame < 120 && sprite.getAir(); frame++) {
            fixture.stepIdleFrames(1);
        }
    }

    private <T> T findClosest(Class<T> type, int x, int y) {
        T closest = null;
        int closestDistance = Integer.MAX_VALUE;
        for (ObjectInstance object : GameServices.level().getObjectManager().getActiveObjects()) {
            if (!type.isInstance(object)) {
                continue;
            }
            int distance = Math.abs(object.getX() - x) + Math.abs(object.getY() - y);
            if (distance < closestDistance) {
                closest = type.cast(object);
                closestDistance = distance;
            }
        }
        return closest;
    }

    private static void installRules(AbstractPlayableSprite player, GameRules rules) {
        try {
            Method method = AbstractPlayableSprite.class.getDeclaredMethod("setGameRulesForTest", GameRules.class);
            method.setAccessible(true);
            method.invoke(player, rules);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to install donor capability rules", e);
        }
    }

    private static GameRules noSpindashDonorRules() {
        return CrossGameRuleComposer.compose(
                GameRules.SONIC_3K,
                GameRules.SONIC_1,
                new Sonic1GameModule().getDonorCapabilities());
    }
}
