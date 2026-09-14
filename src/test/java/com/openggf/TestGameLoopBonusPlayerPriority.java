package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.graphics.RenderPriority;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestGameLoopBonusPlayerPriority {
    @AfterEach
    void closeSession() {
        SessionManager.clear();
    }

    @ParameterizedTest
    @EnumSource(value = BonusStageType.class, names = {"GUMBALL", "GLOWING_SPHERE", "SLOT_MACHINE"})
    void postFramePriorityHonorsStageAndObjectOwnership(BonusStageType type) throws Exception {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        GameLoop loop = new GameLoop(new InputHandler());
        var provider = new Sonic3kBonusStageCoordinator();
        provider.onEnter(type, null);
        var field = GameLoop.class.getDeclaredField("activeBonusStageProvider");
        field.setAccessible(true);
        field.set(loop, provider);
        var sonic = new Sonic("sonic", (short) 0, (short) 0);
        var tails = new Tails("tails", (short) 0, (short) 0);
        GameServices.sprites().addSprite(sonic);
        GameServices.sprites().addSprite(tails);
        var enforce = GameLoop.class.getDeclaredMethod("forcePlayerHighPriorityInBonusStage");
        enforce.setAccessible(true);
        for (int frame = 0; frame < 2; frame++) {
            sonic.setHighPriority(false);
            tails.setHighPriority(false);
            enforce.invoke(loop);
            assertEquals(type != BonusStageType.SLOT_MACHINE, sonic.isHighPriority());
            assertEquals(type != BonusStageType.SLOT_MACHINE, tails.isHighPriority());
        }
        sonic.setPriorityBucket(RenderPriority.MIN);
        sonic.setHighPriority(false);
        enforce.invoke(loop);
        assertFalse(sonic.isHighPriority(), "Object-owned priority overrides must remain untouched");
    }
}
