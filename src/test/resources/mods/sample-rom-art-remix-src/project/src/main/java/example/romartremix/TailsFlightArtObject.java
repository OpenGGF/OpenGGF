package example.romartremix;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectReconstructionContext;
import com.openggf.level.objects.ModRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Displays two ROM-borrowed Tails flight frames without controlling the playable. */
public final class TailsFlightArtObject extends AbstractObjectInstance
        implements ModRewindRecreatable {
    private int animTick;

    public TailsFlightArtObject(ObjectSpawn spawn) {
        super(spawn, "sample-rom-art-remix:tails-flight-art");
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        animTick = (animTick + 1) & 7;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer("sample-rom-art-remix:tails-flight");
        if (renderer != null) {
            renderer.drawFrameIndex(94 + (animTick / 4), spawn.x(), spawn.y(), false, false);
        }
    }

    @Override
    public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) {
        return new TailsFlightArtObject(context.spawn());
    }
}
