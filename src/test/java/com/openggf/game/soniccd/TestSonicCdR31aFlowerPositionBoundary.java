package com.openggf.game.soniccd;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;

/** Public original Boot/World/terrain/Frame; deliberately constructed pool Seed, not natural reward reach. */
@Isolated class TestSonicCdR31aFlowerPositionBoundary {
    @TempDir Path directory;
    final TestSonicCdR31aSelectedProfileLoading fixture = new TestSonicCdR31aSelectedProfileLoading();
    @BeforeEach void setup() { fixture.directory = directory; fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualPublicSeedCompletesItsPositionStoresBeforeNativeGrowth() throws Exception {
        try (var boot = fixture.start(1, 0x80)) {
            TestSonicCdR31aSelectedProfileLoading.ready(boot);
            TestSonicCdR31aSelectedProfileLoading.step(boot, 8);
            var host = boot.module.r31WorldHost();
            var core = boot.module.r31LoadingVisit();
            var frame = boot.module.requirePlayerFrame();
            var pool = host.placement().pool();
            for (int whole = 32; whole < 128; whole++) pool.deleteObject(whole);
            var seed = pool.state(32).moveByte(ID, 0x1F).moveByte(ROUTINE, 2)
                    .moveByte(SUBTYPE, 0).moveByte(SPRITE_FLAGS, 4)
                    .moveWord(X, 96).moveWord(Y, 1024).moveLong(MAPPINGS, 0x2336D8).moveWord(TILE, 0x6D7);
            pool.write(seed);
            var floor = com.openggf.game.soniccd.objects.CdObjectCollisionCheck.objGetFloorDist(
                    com.openggf.game.soniccd.player.CdPlayerTerrain.fromStageTerrain(host.currentTerrain()), seed);
            assertTrue(floor.distance() < 0, "Actual current terrain must satisfy the labelled landing precondition");
            assertEquals(1, frame.environment().movement().gates().timeZone() & 0x7F);
            assertEquals(0, core.state().level().flowerCount());
            assertDoesNotThrow(() -> TestSonicCdR31aSelectedProfileLoading.step(boot, 8));
            assertEquals(0x100, core.state().level().flowerCount());
            assertEquals((seed.centreY() + floor.distance()) & 0xFFFF, pool.state(32).centreY());
            assertEquals(floor.primaryAngle(), frame.environment().movement().globals().primaryAngle());
            assertEquals(4, pool.state(32).routine());
            assertEquals(1, pool.state(32).unsignedByte(ANIMATION));
            assertFalse(frame.closed());
        }
    }
}
