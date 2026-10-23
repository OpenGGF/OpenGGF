package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;

/** Explicit post-Obj_CNZMinibossGo setup for isolated phase tests, never route evidence. */
final class CnzMinibossPhaseFixture {
    private CnzMinibossPhaseFixture() { }

    static void releasedStart(CnzMinibossInstance boss, com.openggf.tests.HeadlessTestFixture fixture) {
        var events = (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        events.getCnzEvents().forceMinibossStartGateForTest(true, true);
        // Obj_CNZMinibossGo installs the Start dispatcher; Init/phase runs next.
        boss.update(0, fixture.sprite());
    }
}
