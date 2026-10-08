package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.tests.HeadlessTestFixture;
import java.util.List;

/** Retire the real placed encounter before independent short child fixtures. */
final class DezEndBossTestSupport {
    private DezEndBossTestSupport() { }
    static void retirePlacedEncounter(HeadlessTestFixture fixture) {
        fixture.stepIdleFrames(2);
        for(var object:List.copyOf(GameServices.level().getObjectManager().getActiveObjects())) {
            if(object instanceof DezEndBossSprite part) part.setDestroyed(true);
        }
        fixture.stepIdleFrames(1);
    }
}
