package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestS3kDezAct3RuntimeState {
    @Test
    void capturesAllTwelveEventBackgroundWords() {
        var state = new S3kDezZoneRuntimeState(
                Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, 0, PlayerCharacter.SONIC_AND_TAILS);
        for (int offset = 0; offset <= 0x16; offset += 2)
            state.setAct3BackgroundWord(offset, 0x1000 + offset);
        byte[] snapshot = state.captureBytes();
        for (int offset = 0; offset <= 0x16; offset += 2)
            state.setAct3BackgroundWord(offset, 0);
        state.restoreBytes(snapshot);
        assertEquals(Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, state.zoneIndex());
        for (int offset = 0; offset <= 0x16; offset += 2)
            assertEquals(0x1000 + offset, state.act3BackgroundWord(offset));
    }
}
