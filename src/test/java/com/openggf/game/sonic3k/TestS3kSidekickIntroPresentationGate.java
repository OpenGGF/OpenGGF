package com.openggf.game.sonic3k;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TestS3kSidekickIntroPresentationGate {

    @Test
    void sszArrivalArmsItsDedicatedDormantCpuBranch() {
        Sonic3kLevelEventManager events = new Sonic3kLevelEventManager();
        events.initLevel(Sonic3kZoneIds.ZONE_SSZ, 0);

        assertTrue(events.shouldEnterSidekickDormantMarker(mock(AbstractPlayableSprite.class)),
                "SSZ's ROM $0A00 arrival owns a dedicated dormant-sidekick marker branch");
    }
}
