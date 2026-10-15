package com.openggf.game.rewind;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.MhzCurledVineObjectInstance;
import com.openggf.game.sonic3k.objects.MhzMushroomCatapultObjectInstance;
import com.openggf.game.sonic3k.objects.MhzMushroomPlatformObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MHZ riders whose per-player state lives only in the object's identity collections. A
 * snapshot taken between the solid pass that records the rider and the update that consumes
 * it must restore that state, or forward replay changes the ride. Every frame of a ride is
 * restored (with the object recreated) and replayed against the live run.
 * <ul>
 *   <li>Obj_MHZCurledVine: P1/P2 segment at $36/$37 with the standing bits (loc_3E8A2,
 *       sonic3k.asm:82803-82812); sub_3E9C6's continued ride omits loc_1E45A's landing -1
 *       (800px MHZ1 route frame 6861: restored rider 1px high).</li>
 *   <li>Mushroom catapult: standing riders collected by the solid pass, launched by the next
 *       update, which tests those standing bits (sub_3FA26/sub_3FA5A, sonic3k.asm:84311-84353).</li>
 *   <li>Mushroom platform: standing riders released when the dropping platform moves.</li>
 * </ul>
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMhzRiderCollectionRewind {
    private static final SolidContact STANDING = new SolidContact(true, false, false, true, false);

    private static SharedLevel sharedLevel;
    private static Object oldSkipIntros;
    private static Object oldMainCharacter;
    private static Object oldSidekickCharacter;

    private HeadlessTestFixture fixture;
    private AbstractPlayableSprite sprite;
    private ObjectManager objectManager;

    @BeforeAll
    static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        oldSidekickCharacter = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_MHZ, 0);
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
        sprite = fixture.sprite();
        fixture.camera().updatePosition(true);
        GameServices.level().postCameraObjectPlacementSync();
        objectManager = GameServices.level().getObjectManager();
        objectManager.reset(fixture.camera().getX());
        fixture.stepIdleFrames(1);
    }

    @Test
    void curledVineContinuedRideSurvivesRestoreOnEveryFrame() {
        int x = sprite.getCentreX();
        int y = sprite.getCentreY() + 0x20;
        replayFromEveryFrame(MhzCurledVineObjectInstance.class,
                () -> new MhzCurledVineObjectInstance(new ObjectSpawn(x, y, 0x13, 0, 0, false, 0)),
                (vine, frame) -> {
                    vine.update(frame, sprite);
                    vine.onSolidContact(sprite, STANDING, frame);
                },
                vine -> sprite.getCentreY() + "," + sprite.getYSubpixelRaw() + " solid="
                        + vine.getSolidParams(), 24);
    }

    @Test
    void catapultRiderLaunchSurvivesRestoreOnEveryFrame() {
        int x = sprite.getCentreX() - 0x30;
        int y = sprite.getCentreY() + 0x18;
        List<String> live = replayFromEveryFrame(MhzMushroomCatapultObjectInstance.class,
                () -> new MhzMushroomCatapultObjectInstance(new ObjectSpawn(x, y, 0x24, 0, 0, false, 0)),
                (catapult, frame) -> {
                    catapult.update(frame, sprite);
                    // The child cap's rider stays on it until the native launch.
                    if (sprite.getYSpeed() >= 0) catapult.onPieceContact(1, sprite, STANDING, frame);
                },
                catapult -> sprite.getYSpeed() + "," + sprite.getAir() + " caps="
                        + catapult.getPieceY(0) + "," + catapult.getPieceY(1), 160);
        assertTrue(live.stream().anyMatch(row -> row.startsWith("-")), "the live ride must launch the rider");
    }

    @Test
    void droppingPlatformReleaseSurvivesRestoreOnEveryFrame() {
        int x = sprite.getCentreX();
        int y = sprite.getCentreY() + 0x20;
        List<String> live = replayFromEveryFrame(MhzMushroomPlatformObjectInstance.class,
                () -> new MhzMushroomPlatformObjectInstance(new ObjectSpawn(x, y, 0x25, 1, 0, false, 0)),
                (platform, frame) -> {
                    platform.update(frame, sprite);
                    if (!sprite.getAir()) platform.onSolidContact(sprite, STANDING, frame);
                },
                platform -> sprite.getAir() + "," + sprite.isOnObject() + " y=" + platform.getY(), 40);
        assertTrue(live.getLast().startsWith("true,false"), "the live drop must release the rider");
    }

    /** Live run, capturing each frame; then restore each capture (recreating the object) and replay. */
    private <T extends AbstractObjectInstance> List<String> replayFromEveryFrame(
            Class<T> type, Supplier<T> factory, BiConsumer<T, Integer> frame,
            Function<T, String> observe, int frames) {
        T source = objectManager.createDynamicObject(factory);
        ObjectRefId id = objectManager.captureIdentityContext().requireIdentityTable().idFor(source);
        assertNotNull(id);
        RewindRegistry registry = fixture.gameplayMode().getRewindRegistry();
        List<CompositeSnapshot> captures = new ArrayList<>();
        List<String> live = new ArrayList<>();
        captures.add(registry.capture());
        for (int f = 1; f <= frames; f++) {
            frame.accept(source, f);
            live.add(observe.apply(source));
            captures.add(registry.capture());
        }
        T current = source;
        for (int k = 0; k < frames; k++) {
            objectManager.removeDynamicObject(current);
            registry.restore(captures.get(k));
            T restored = objectById(type, id);
            assertNotSame(current, restored, "restore must recreate the object");
            current = restored;
            for (int f = k + 1; f <= frames; f++) {
                frame.accept(current, f);
                assertEquals(live.get(f - 1), observe.apply(current),
                        type.getSimpleName() + " restored at frame " + k + " diverges at frame " + f);
            }
        }
        return live;
    }

    private <T extends ObjectInstance> T objectById(Class<T> type, ObjectRefId id) {
        var table = objectManager.captureIdentityContext().requireIdentityTable();
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass() == type && !object.isDestroyed())
                .map(type::cast)
                .filter(object -> id.equals(table.idFor(object)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing restored object " + id));
    }
}
