package example.flappysample;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** Holds visible native Tails at a fixed X while preserving the engine's flight mechanics. */
public final class FlappyController extends AbstractObjectInstance implements RewindRecreatable {
    private static final int WAITING = 0;
    private static final int RUNNING = 1;
    private static final int PLAYER_SCREEN_X = 96;
    private static final int PLAYER_SCREEN_Y = 112;
    private static final int FLIGHT_REFILL = 0xF0;

    // Non-final gameplay scalars are captured by the generic compact rewind schema.
    private int routine;
    private int anchorX;

    public FlappyController(ObjectSpawn spawn) {
        super(spawn, "sample-flappy:controller");
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite tails = (AbstractPlayableSprite)
                services().playerQuery().mainPlayerOrNull();
        if (tails == null) {
            return;
        }

        if (routine == WAITING) {
            activateNativeRun(tails);
            return;
        }

        tails.setCentreX((short) anchorX);
        tails.setXSpeed((short) 0);
        tails.setGSpeed((short) 0);
        tails.setDoubleJumpProperty((byte) FLIGHT_REFILL);
    }

    private void activateNativeRun(AbstractPlayableSprite tails) {
        tails.setCentreX((short) (services().camera().getX() + PLAYER_SCREEN_X));
        tails.setCentreY((short) (services().camera().getY() + PLAYER_SCREEN_Y));
        anchorX = tails.getCentreX();
        tails.setXSpeed((short) 0);
        tails.setGSpeed((short) 0);
        tails.getTailsFlightController().activate();
        tails.setDoubleJumpProperty((byte) FLIGHT_REFILL);
        routine = RUNNING;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // The controller owns gameplay state only; native Tails renders through the player pipeline.
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new FlappyController(context.spawn());
    }
}
