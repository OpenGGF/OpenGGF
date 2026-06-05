package com.openggf.game.sonic2.events;

import com.openggf.game.sonic2.OilSurfaceManager;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Oil Ocean Zone events.
 * ROM: LevEvents_OOZ (s2.asm:20938-21035)
 * Also handles oil surface (Obj07) and oil slides (OilSlides routine).
 */
public class Sonic2OOZEvents extends Sonic2ZoneEvents {
    private OilSurfaceManager oilManager;

    public Sonic2OOZEvents() {
    }

    @Override
    public void init(int act) {
        super.init(act);
        oilManager = new OilSurfaceManager();
    }

    @Override
    public void updatePrePhysics(int act, int frameCounter) {
        if (oilManager == null) {
            return;
        }
        // ROM OilSlides runs in NonWaterEffects, BEFORE RunObjects executes the
        // player object (docs/s2disasm/s2.asm:5301,5537-5642). Running it here
        // (pre-physics) lets the sliding status bit gate the same frame's player
        // friction/move and makes the OilSlides_Chunks lookup read the previous
        // frame's player position, exactly as the ROM does. Running it post-
        // physics applied oil friction one frame early (OOZ1 trace f563).
        //
        // ROM processes both Player 1 and Player 2 every frame; mirror that for
        // the camera-focused character and every registered sidekick.
        for (PlayableEntity participant :
                playerQueryFromRuntime().playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            if (participant instanceof AbstractPlayableSprite playable) {
                oilManager.updateSlides(playable);
            }
        }
    }

    @Override
    public void update(int act, int frameCounter) {
        if (oilManager == null) {
            return;
        }
        // ROM Obj07_Main (oil surface) runs during object processing, after the
        // player object (s2.asm:49659-49749). Mirror that post-physics here.
        ObjectPlayerQuery playerQuery = playerQueryFromRuntime();
        for (PlayableEntity participant :
                playerQuery.playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            if (participant instanceof AbstractPlayableSprite playable) {
                oilManager.updateSurface(playable);
            }
        }
        // Re-arm the per-frame slide cadence for the next frame's pre-physics pass.
        oilManager.endFrame();
    }

    private ObjectPlayerQuery playerQueryFromRuntime() {
        AbstractPlayableSprite focusedPlayer = camera().getFocusedSprite();
        List<AbstractPlayableSprite> sidekicks = List.copyOf(spriteManager().getSidekicks());
        return new ObjectPlayerQuery(
                () -> focusedPlayer,
                () -> sidekicks);
    }
}
