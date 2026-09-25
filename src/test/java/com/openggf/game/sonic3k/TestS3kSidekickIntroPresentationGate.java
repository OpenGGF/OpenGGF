package com.openggf.game.sonic3k;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@com.openggf.tests.rules.RequiresGameModule(com.openggf.tests.rules.SonicGame.SONIC_3K)
class TestS3kSidekickIntroPresentationGate {

    @Test
    void sszArrivalUsesItsNativeDormantBranchOnlyInActOne() {
        Sonic3kLevelEventManager events = new Sonic3kLevelEventManager();
        events.initLevel(Sonic3kZoneIds.ZONE_SSZ, 0);

        // loc_13AB4 explicitly includes $0A00 before sub_13ECA parks Tails.
        assertTrue(events.shouldEnterSidekickDormantMarker(mock(AbstractPlayableSprite.class)),
                "SSZ arrival must park Tails until its teleporter releases her");
        events.initLevel(Sonic3kZoneIds.ZONE_SSZ, 1);
        assertFalse(events.shouldEnterSidekickDormantMarker(mock(AbstractPlayableSprite.class)));
    }
}
