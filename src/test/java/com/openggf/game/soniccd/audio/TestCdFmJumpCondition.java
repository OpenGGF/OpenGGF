package com.openggf.game.soniccd.audio;

import com.openggf.audio.rewind.AudioCommand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCdFmJumpCondition {
    @Test
    void stopSoundClearsTheConditionAndRollbackRestoresIt() {
        var driver = new CdFmDriverState();
        var requests = requests(driver);
        driver.setJumpCondition(true);
        requests.submitSound(0xE0, new AudioCommand.StopAllSfx());

        var boundary = requests.beginForwardBoundary();
        try {
            List<AudioCommand> commands = new ArrayList<>();
            boundary.service(commands::add);
            assertEquals(List.of(new AudioCommand.StopAllSfx()), commands);
            assertFalse(driver.jumpCondition(), "StopSound clears the condition before the track pass");
        } finally {
            boundary.rollback();
        }
        assertTrue(driver.jumpCondition(), "a rolled-back StopSound keeps the pre-tick condition");
    }

    @Test
    void rewindRestoresBothJumpConditionValues() {
        var driver = new CdFmDriverState();
        var requests = requests(driver);
        var beforeWrite = requests.snapshot();
        driver.setJumpCondition(true);
        var afterWrite = requests.snapshot();

        requests.restore(beforeWrite);
        assertFalse(driver.jumpCondition(), "rewind before the write holds the loop");
        requests.restore(afterWrite);
        assertTrue(driver.jumpCondition(), "rewind after the write permits the fade");
    }

    private static CdFmSoundRequestService requests(CdFmDriverState driver) {
        return new CdFmSoundRequestService(id -> 0, new CdSubCpuAudio(() -> null), driver);
    }
}
