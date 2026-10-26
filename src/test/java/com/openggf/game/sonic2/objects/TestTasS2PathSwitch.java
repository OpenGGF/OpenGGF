package com.openggf.game.sonic2.objects;

import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaneSwitcherConfig;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * s2-path-switch: TASVideos 4682M/7453S and 5640M/8629S, CPZ2/HTZ2 Sonic.
 * Tests Obj03's bypass primitive through the real object and controller, not the
 * three-clip route or Knuckles' lower jump-height limitation in 5412M/8376S.
 * Synthetic placement ($680,$280), radius $20, side-1 path B/high priority.
 * Oracle: Obj03_MainX (s2.asm:45682-45761), pinned at
 * 34fc7f130a96db171e1a1c43e28e0bad81cd6ca7: side is latched BEFORE span/air tests.
 */
class TestTasS2PathSwitch {
    private TestablePlayableSprite player;

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x67F);
        player.setCentreY((short) 0x280);
        player.setTopSolidBit((byte) 0xC);
        player.setLrbSolidBit((byte) 0xD);
        player.setAir(false);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @ParameterizedTest
    @CsvSource({"-33,false", "-32,true", "31,true", "32,false"})
    void spanEdgesDecideWhetherTheCrossingChangesCollisionPath(int yOffset, boolean switches) {
        var switcher = switcher(0x28);
        switcher.update(0, player);
        player.setCentreY((short) (0x280 + yOffset));
        player.setCentreX((short) 0x680);
        switcher.update(1, player);

        assertPath(switches);
        // Re-enter the vertical span on the already-consumed side. No second
        // crossing occurred: the missed event cannot be repaired retroactively.
        player.setCentreY((short) 0x280);
        switcher.update(2, player);
        assertPath(switches);
        player.setCentreX((short) 0x67F);
        switcher.update(3, player);
        assertPath(false);
        player.setCentreX((short) 0x680);
        switcher.update(4, player);
        assertPath(true); // positive control: same object still switches on a fresh crossing
    }

    @Test
    void airborneGroundedOnlyCrossingConsumesItsLatchWithoutChangingThePath() {
        var switcher = switcher(0xA8);
        switcher.update(0, player);
        player.setAir(true);
        player.setCentreX((short) 0x680);
        switcher.update(1, player);
        assertPath(false);
        player.setAir(false);
        switcher.update(2, player);
        assertPath(false);
        player.setCentreX((short) 0x67F);
        switcher.update(3, player);
        player.setCentreX((short) 0x680);
        switcher.update(4, player);
        assertPath(true);
    }

    private Sonic2LayerSwitcherObjectInstance switcher(int subtype) {
        var spawn = new ObjectSpawn(0x680, 0x280, 3, subtype, 0, false, 0);
        var manager = new ObjectManager(List.of(spawn), null, 3,
                new PlaneSwitcherConfig((byte) 0xC, (byte) 0xD, (byte) 0xE, (byte) 0xF),
                null, null, null, null);
        var switcher = new Sonic2LayerSwitcherObjectInstance(spawn, "LayerSwitcher");
        switcher.setServices(new StubObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
            @Override public ObjectPlayerQuery playerQuery() {
                return new ObjectPlayerQuery(() -> player, List::of);
            }
        });
        return switcher;
    }

    private void assertPath(boolean pathB) {
        assertEquals(pathB ? 1 : 0, player.getLayer());
        assertEquals(pathB ? 0xE : 0xC, player.getTopSolidBit());
        assertEquals(pathB ? 0xF : 0xD, player.getLrbSolidBit());
        assertEquals(pathB, player.isHighPriority());
    }
}
