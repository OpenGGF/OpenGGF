package example.flappysample;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
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
    private static final int PIPE_POOL_SIZE = 6;
    private static final int PIPE_SPACING = 224;
    private static final int PIPE_SPEED = 0x200;
    private static final int FIRST_PIPE_LEAD = 64;

    // Non-final gameplay scalars are captured by the generic compact rewind schema.
    private int routine;
    private int anchorX;
    private boolean poolInitialized;
    private int generationCounter;

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

        ensurePipePool();
        if (routine == WAITING) {
            activateNativeRun(tails);
            return;
        }

        tails.setCentreX((short) anchorX);
        tails.setXSpeed((short) 0);
        tails.setGSpeed((short) 0);
        tails.setDoubleJumpProperty((byte) FLIGHT_REFILL);
        advanceAndRecyclePipes();
    }

    private void ensurePipePool() {
        if (poolInitialized) {
            return;
        }
        int firstLeadX = services().camera().getX()
                + services().camera().getWidth() + FIRST_PIPE_LEAD;
        int cameraMidY = services().camera().getY() + PLAYER_SCREEN_Y;
        for (int slot = 0; slot < PIPE_POOL_SIZE; slot++) {
            int variant = nextGapVariant();
            int x = firstLeadX + slot * PIPE_SPACING;
            ObjectSpawn pipeSpawn = buildSpawnAt(x, cameraMidY);
            spawnFreeChild(() -> new FlappyPipe(pipeSpawn, variant));
        }
        poolInitialized = true;
    }

    private void advanceAndRecyclePipes() {
        List<FlappyPipe> pipes = services().objectManager()
                .activeObjectsOfType(FlappyPipe.class);
        if (pipes.isEmpty()) {
            return;
        }
        RewindIdentityTable identities = services().objectManager()
                .captureIdentityContext().requireIdentityTable();
        pipes.sort((left, right) -> compareStableIds(
                identities.idFor(left), identities.idFor(right)));

        int rightmostX = Integer.MIN_VALUE;
        for (FlappyPipe pipe : pipes) {
            pipe.advance(PIPE_SPEED);
            rightmostX = Math.max(rightmostX, pipe.centreX());
        }
        int viewportLeft = services().camera().getX();
        for (FlappyPipe pipe : pipes) {
            if (pipe.rightEdge() < viewportLeft) {
                rightmostX += PIPE_SPACING;
                pipe.recycleAfter(rightmostX, nextGapVariant());
            }
        }
    }

    private int nextGapVariant() {
        int variant = gapVariantFor(generationCounter);
        generationCounter++;
        return variant;
    }

    private static int gapVariantFor(int generation) {
        return switch (Math.floorMod(generation, 5)) {
            case 0 -> 2;
            case 1 -> 0;
            case 2 -> 4;
            case 3 -> 1;
            default -> 3;
        };
    }

    private static int compareStableIds(ObjectRefId left, ObjectRefId right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        int dynamic = Integer.compare(left.dynamicId(), right.dynamicId());
        if (dynamic != 0) {
            return dynamic;
        }
        int spawn = Integer.compare(left.spawnId(), right.spawnId());
        if (spawn != 0) {
            return spawn;
        }
        return Integer.compare(left.generation(), right.generation());
    }

    public int generationCounter() {
        return generationCounter;
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
