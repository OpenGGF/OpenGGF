package com.openggf.game.internal;

import com.openggf.game.GameModule;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class TestStandingInputPolicy {
    @Test
    void nativeHostServiceSurvivesProfileReplacementAndDecoration() {
        for (GameModule host : new GameModule[] {
                new Sonic1GameModule(), new Sonic2GameModule(), new Sonic3kGameModule()}) {
            var nativePolicy = StandingInputPolicy.forHost(host);
            var replacement = host instanceof Sonic3kGameModule
                    ? new Sonic1GameModule().getPhysicsProvider() : new Sonic3kGameModule().getPhysicsProvider();
            // The replacement intentionally carries the opposite Roll Duck threshold and
            // ground-pose record. Standing precedence still belongs to the host module.
            var decorated = new DelegatingGameModule(host, "profile-control") {
                @Override public PhysicsProvider getPhysicsProvider() { return replacement; }
            };
            assertSame(nativePolicy, StandingInputPolicy.forHost(decorated));
        }
        assertSame(StandingInputPolicy.UP_FIRST, StandingInputPolicy.forHost(new Sonic1GameModule()));
        assertSame(StandingInputPolicy.UP_FIRST, StandingInputPolicy.forHost(new Sonic2GameModule()));
        assertSame(StandingInputPolicy.DOWN_FIRST, StandingInputPolicy.forHost(new Sonic3kGameModule()));
    }

    @Test
    void absentServiceRetainsUpFirstWithoutExaminingPublicRuleValues() {
        var noService = new DelegatingGameModule(new Sonic3kGameModule(), "no-policy") {
            @Override public <T> T getGameService(Class<T> type) { return null; }
        };
        assertSame(StandingInputPolicy.UP_FIRST, StandingInputPolicy.forHost(noService));
        assertSame(StandingInputPolicy.UP_FIRST, StandingInputPolicy.forHost(null));
    }
}
