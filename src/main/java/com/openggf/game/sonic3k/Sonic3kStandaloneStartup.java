package com.openggf.game.sonic3k;

import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesSkIntroInstance;

/** Native startup input policy. The live world's admitted object owns all restart writes. */
public final class Sonic3kStandaloneStartup {
    private Sonic3kStandaloneStartup() { }

    public static boolean handleIntroInput(InputHandler input) {
        if (!Sonic3kCartridgePolicy.isStandaloneSk() || !GameServices.hasRuntime()) return false;
        var objects = GameServices.level().getObjectManager();
        if (objects == null) return false;
        boolean ownsStart = false;
        for (var intro : objects.objectQuery().activeObjectsOfType(CutsceneKnucklesSkIntroInstance.class)) {
            if (!intro.ownsNativeIntroInput()) continue;
            ownsStart = true;
            if (input.logical().player1().startPressed() || input.logical().player2().startPressed())
                intro.requestNativeSkip();
        }
        return ownsStart;
    }
}
