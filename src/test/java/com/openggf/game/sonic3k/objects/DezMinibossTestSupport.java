package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.tests.HeadlessTestFixture;
import java.util.List;

/** Keeps component-only graph probes independent of the now-registered placement. */
final class DezMinibossTestSupport {
    private DezMinibossTestSupport() { }
    static void retirePlacedEncounter(HeadlessTestFixture fixture) {
        fixture.stepIdleFrames(1);
        for (var object : List.copyOf(GameServices.level().getObjectManager().getActiveObjects()))
            if (object instanceof DezMinibossSprite part) part.setDestroyed(true);
        fixture.stepIdleFrames(1);
    }
}
