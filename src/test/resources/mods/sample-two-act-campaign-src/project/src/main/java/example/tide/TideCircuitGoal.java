package example.tide;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/** Original act-two ring tally. Native S2 signposts intentionally retire in later acts. */
public final class TideCircuitGoal extends AbstractObjectInstance implements RewindRecreatable {
    private boolean activated;
    private boolean departed;
    private int remainingBonus;
    private int resultsTicks;

    public TideCircuitGoal(ObjectSpawn spawn) { super(spawn,"sample-tide-circuit:finish-gate"); }

    @Override public void update(int vIntRunCount,PlayableEntity player) {
        if (player==null || departed) return;
        if (!activated) {
            if (player.getCentreX()<getSpawn().x()) return;
            activated=true;
            remainingBonus=Math.min(999,Math.max(0,player.getRingCount()))*100;
            services().gameState().setActCompletionSignalActive(true);
            services().levelGamestate().pauseTimer();
            return;
        }
        if (player instanceof AbstractPlayableSprite playable) {
            playable.setControlLocked(true);
            playable.setXSpeed((short)0);
            playable.setGSpeed((short)0);
        }
        if (remainingBonus>0) {
            int points=Math.min(100,remainingBonus);
            services().gameState().addScore(points);
            remainingBonus-=points;
            return;
        }
        // An authored two-second results hold, independent of native ROM timing.
        if (++resultsTicks>=120) {
            departed=true;
            services().advanceToNextLevel();
        }
    }

    @Override public boolean isPersistent() { return activated && !departed; }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer=getRenderer("signpost");
        if (renderer!=null && renderer.isReady())
            renderer.drawFrameIndex(activated?3:2,getSpawn().x(),getSpawn().y(),false,false);
    }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new TideCircuitGoal(context.spawn());
    }
}
