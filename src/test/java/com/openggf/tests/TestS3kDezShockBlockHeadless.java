package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.ShieldType;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.Sonic3kInvisibleShockBlockObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** DEZ1_Sprites record 27: the placed $6D:$71 shock floor at ($780,$7D2). */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezShockBlockHeadless {
    @AfterEach void reset() { SessionManager.clear(); }

    @ParameterizedTest
    @ValueSource(strings = {"NONE", "BASIC", "FIRE", "BUBBLE", "LIGHTNING"})
    void placedShockFloorUsesTheShieldReactionAndReplaysAfterRewind(String shield) {
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .startPosition((short) 0x780, (short) 0x780)
                .startPositionIsCentre().build();
        fixture.sprite().setRingCount(7);
        if (!shield.equals("NONE")) fixture.sprite().giveShield(ShieldType.valueOf(shield));
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var before = registry.capture();
        List<String> first = fall(fixture);
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(o -> o instanceof Sonic3kInvisibleShockBlockObjectInstance && o.getX() == 0x780),
                "the ROM placement must resolve to the new hazard");
        boolean hurt = first.stream().anyMatch(row -> row.contains("hurt=true"));
        assertEquals(!shield.equals("LIGHTNING"), hurt, "only bit 5 answers the shock reaction");
        assertFalse(fixture.sprite().getDead(), "ringed or shielded contact is survivable");
        if (shield.equals("LIGHTNING")) {
            assertTrue(fixture.sprite().getRingCount() >= 7,
                    "keeps its rings; lightning attraction may collect nearby placed rings");
            assertTrue(fixture.sprite().hasShield());
            assertTrue(fixture.sprite().isOnObject(), "immunity still leaves the solid floor");
        }
        registry.restore(before);
        assertEquals(first, fall(fixture), "hurt, shield and ring state replay after restore");
    }

    private List<String> fall(HeadlessTestFixture fixture) {
        List<String> rows = new ArrayList<>();
        for (int frame = 0; frame < 80; frame++) {
            fixture.stepIdleFrames(1);
            var p = fixture.sprite();
            rows.add(p.getCentreX() + "," + p.getCentreY() + "," + p.getYSpeed()
                    + ",hurt=" + p.isHurt() + ",shield=" + p.getShieldType() + ",rings=" + p.getRingCount());
        }
        return rows;
    }
}
