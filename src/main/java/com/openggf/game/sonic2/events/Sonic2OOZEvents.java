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
    public void update(int act, int frameCounter) {
        if (oilManager == null) {
            return;
        }
        // ROM Obj07_Main processes both Player 1 and Player 2 every frame
        // (s2.asm:49681-49736). Mirror that by ticking the oil layer for the
        // camera-focused character (Sonic) and every registered sidekick.
        ObjectPlayerQuery playerQuery = playerQueryFromRuntime();
        for (PlayableEntity participant :
                playerQuery.playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            if (participant instanceof AbstractPlayableSprite playable) {
                oilManager.update(playable);
            }
        }
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
