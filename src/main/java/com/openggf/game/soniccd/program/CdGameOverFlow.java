package com.openggf.game.soniccd.program;

import com.openggf.game.GameOverFlowProvider;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.common.CdGameOver;
import com.openggf.level.objects.ObjectServices;

/**
 * The death's game-over branch, {@code ObjSonic_DeadChkGone} from {@code .CapLives}
 * (Sonic/Main.asm:2419-2444), on the frame the shared death code takes a life and finds none left or a
 * time over: the {@code timeOver} flag the text reads, the $3B object, and the player's restart timer
 * the object carries: 8 seconds with no lives left, otherwise one second. In Time Attack
 * ({@code tst.b timeAttackMode}) lives become 0 and no text is loaded, with the one-second timer, unless the
 * player gave up from boredom ({@code cmpi.b #$2B,oAnim(a0); beq.s .LoadGameOver}, Sonic/Main.asm:2426-2427):
 * the give-up set one life, so the text loads with the 8-second timer.
 */
public final class CdGameOverFlow implements GameOverFlowProvider {
    /** {@code move.w #8*60,oPlayerReset(a0)} (Sonic/Main.asm:2439). */
    static final int GAME_OVER_FRAMES = 8 * 60;
    /** {@code move.w #60,oPlayerReset(a0)} (.ResetDelay, Sonic/Main.asm:2443-2444). */
    static final int RESTART_FRAMES = 60;

    private final CdSceneCoordinator scenes;

    public CdGameOverFlow(CdSceneCoordinator scenes) {
        this.scenes = scenes;
    }

    @Override
    public void beginGameOverCard(ObjectServices services, boolean timeOver) {
        var levels = services.levelManager();
        if (levels != null && levels.getCurrentLevel() instanceof CdLevel level) level.flags().timeOver = timeOver;
        var objects = services.objectManager();
        if (objects == null) return;
        var camera = services.camera();
        boolean gaveUp = camera != null && camera.getFocusedSprite() instanceof
                com.openggf.sprites.playable.AbstractPlayableSprite player
                && com.openggf.game.soniccd.player.CdBoredom.givingUp(player);
        if (scenes.flow().timeAttackMode() && !gaveUp) {
            objects.addDynamicObject(CdGameOver.afterDeath(RESTART_FRAMES, false));
            return;
        }
        var game = services.gameState();
        boolean noLives = game == null || game.getLives() == 0;
        objects.addDynamicObject(CdGameOver.afterDeath(noLives ? GAME_OVER_FRAMES : RESTART_FRAMES, true));
    }
}
