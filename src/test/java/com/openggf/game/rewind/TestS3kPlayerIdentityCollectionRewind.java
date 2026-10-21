package com.openggf.game.rewind;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.LbzMovingPlatformObjectInstance;
import com.openggf.game.sonic3k.objects.SinkingMudObjectInstance;
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
 * Per-player identity collections outside MHZ that carry state across the snapshot boundary
 * (s3k-known-bugs "MHZ Rewind Snapshot Identity Gaps"). Each frame of a ride is restored with
 * the object recreated and replayed against the live run.
 * <ul>
 *   <li>LBZ moving platform, falling type: the riders recorded by the solid pass are released
 *       by a later update when the fall timer expires.</li>
 *   <li>Obj_SinkingMud: each character's surface depth and its standing bit persist between
 *       frames (sonic3k.asm:68540-68701); a restore that forgets them resets a sinking player
 *       to the full surface.</li>
 * </ul>
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kPlayerIdentityCollectionRewind {
    private static final SolidContact STANDING = new SolidContact(true, false, false, true, false);

    private static Object oldSkipIntros;
    private static Object oldMainCharacter;
    private static Object oldSidekickCharacter;

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
    static void restoreConfiguration() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                oldSidekickCharacter != null ? oldSidekickCharacter : "tails");
    }

    @Test
    void lbzFallingPlatformReleaseSurvivesRestoreOnEveryFrame() throws Exception {
        try (Zone zone = new Zone(Sonic3kZoneIds.ZONE_LBZ)) {
            AbstractPlayableSprite sprite = zone.fixture.sprite();
            int x = sprite.getCentreX();
            int y = sprite.getCentreY() + 0x20;
            // Subtype $0D: size 0, move type 13 (delayed fall), then type 14 releases the riders.
            List<String> live = zone.replayFromEveryFrame(LbzMovingPlatformObjectInstance.class,
                    () -> new LbzMovingPlatformObjectInstance(
                            new ObjectSpawn(x, y, Sonic3kObjectIds.LBZ_MOVING_PLATFORM, 0x0D, 0, false, 0)),
                    (platform, frame) -> {
                        platform.update(frame, sprite);
                        if (!sprite.getAir()) platform.onSolidContact(sprite, STANDING, frame);
                    },
                    platform -> sprite.getAir() + "," + sprite.isOnObject() + " ys=" + sprite.getYSpeed()
                            + " y=" + platform.getY(), 90);
            assertTrue(live.getLast().startsWith("true,false"), "the live fall must release the rider: " + live);
        }
    }

    @Test
    void sinkingMudDepthSurvivesRestoreOnEveryFrame() throws Exception {
        try (Zone zone = new Zone(Sonic3kZoneIds.ZONE_MGZ)) {
            AbstractPlayableSprite sprite = zone.fixture.sprite();
            int x = sprite.getCentreX();
            int y = sprite.getCentreY() + 0x30;
            List<String> live = zone.replayFromEveryFrame(SinkingMudObjectInstance.class,
                    () -> new SinkingMudObjectInstance(
                            new ObjectSpawn(x, y, Sonic3kObjectIds.SINKING_MUD, 4, 0, false, 0)),
                    (mud, frame) -> {
                        mud.update(frame, sprite);
                        if (!sprite.getDead()) mud.onSolidContact(sprite, STANDING, frame);
                    },
                    mud -> sprite.getCentreY() + "," + sprite.getYSubpixelRaw() + " dead=" + sprite.getDead()
                            + " solid=" + mud.getSolidParams(), 40);
            assertTrue(!live.getFirst().equals(live.getLast()), "the live player must sink: " + live);
        }
    }

    /** One loaded zone, an idle fixture and the restore/replay harness. */
    private static final class Zone implements AutoCloseable {
        private final SharedLevel sharedLevel;
        private final HeadlessTestFixture fixture;
        private final ObjectManager objectManager;

        Zone(int zoneId) throws Exception {
            sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, zoneId, 0);
            fixture = HeadlessTestFixture.builder().withSharedLevel(sharedLevel).build();
            fixture.camera().updatePosition(true);
            GameServices.level().postCameraObjectPlacementSync();
            objectManager = GameServices.level().getObjectManager();
            objectManager.reset(fixture.camera().getX());
            fixture.stepIdleFrames(1);
        }

        /** Live run, capturing each frame; then restore each capture (recreating the object) and replay. */
        <T extends AbstractObjectInstance> List<String> replayFromEveryFrame(
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

        @Override
        public void close() {
            sharedLevel.dispose();
        }
    }
}
