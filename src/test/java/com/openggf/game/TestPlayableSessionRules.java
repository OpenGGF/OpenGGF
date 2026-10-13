package com.openggf.game;

import com.openggf.game.rules.GameRules;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestPlayableSessionRules {
    @AfterEach
    void clearSession() {
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void constructionUsesSelectedWorldRatherThanBootstrapDefault() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        GameModuleRegistry.setCurrent(new Sonic2GameModule());

        var sprite = new TestablePlayableSprite("tails", (short) 32, (short) 32);

        assertSame(GameRules.SONIC_3K, sprite.getGameRules());
        // ROM sub_13ECA: move.w #$7F00,x_pos(a0), sonic3k.asm.
        assertEquals(0x7F00, sprite.getGameRules().sidekickCpu().sidekickDespawnX());
    }

    @Test
    void levelResetKeepsSelectedWorldRulesBeforeFirstStatusTick() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        var sprite = new TestablePlayableSprite("tails", (short) 32, (short) 32);
        GameModuleRegistry.setCurrent(new Sonic2GameModule());

        sprite.resetState();

        assertSame(GameRules.SONIC_3K, sprite.getGameRules());
        assertEquals(0x7F00, sprite.getGameRules().sidekickCpu().sidekickDespawnX());
    }

    @Test
    void preSessionConstructionRetainsBootstrapFallback() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        SessionManager.clear();
        GameModuleRegistry.setCurrent(new Sonic2GameModule());

        var sprite = new TestablePlayableSprite("tails", (short) 32, (short) 32);

        assertSame(GameRules.SONIC_2, sprite.getGameRules());
        assertEquals(0x4000, sprite.getGameRules().sidekickCpu().sidekickDespawnX());
    }
}
