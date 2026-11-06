package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.rewind.LiveRewindManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestGameLoopPacingInterruption {
    @Test
    void heldDrainedKeyLeavesForwardPacingActiveButReleaseReconciliationStillOwnsTheFrame() {
        var rewind = mock(LiveRewindManager.class);
        var config = mock(SonicConfigurationService.class);
        var input = mock(InputHandler.class);
        when(config.getInt(SonicConfiguration.LIVE_REWIND_KEY)).thenReturn(82);
        when(input.isKeyDown(82)).thenReturn(true);
        when(rewind.canEngageHeldRewind()).thenReturn(true);
        assertFalse(GameLoopPacingInterruption.allows(rewind, config, input));
        when(rewind.canEngageHeldRewind()).thenReturn(false);
        assertTrue(GameLoopPacingInterruption.allows(rewind, config, input));
        when(rewind.isRewindingOrReleasing()).thenReturn(true);
        assertFalse(GameLoopPacingInterruption.allows(rewind, config, input));
    }
}
