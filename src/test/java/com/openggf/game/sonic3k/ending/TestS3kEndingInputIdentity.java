package com.openggf.game.sonic3k.ending;

import com.openggf.game.LevelInputOverlay;
import com.openggf.game.internal.NativeLevelInputOwner;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestS3kEndingInputIdentity {
    @Test void longModCreditsRetainNativeInputCallbackIdentity() {
        var module = new Sonic3kGameModule();
        var nativeInput = module.getGameService(NativeLevelInputOwner.class);
        var overlay = module.getGameService(LevelInputOverlay.class);
        for (int i = 0; i < 18000; i++) {
            assertSame(nativeInput, module.getGameService(NativeLevelInputOwner.class));
            assertSame(overlay, module.getGameService(LevelInputOverlay.class));
        }
    }
}
