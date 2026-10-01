package infinite;

import com.openggf.game.PlayableEntity;
import com.openggf.game.mutation.*;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** Scalar origin is captured by the ordinary mod-object rewind codec. */
public final class CourseController extends AbstractObjectInstance implements RewindRecreatable {
    private long origin;
    public CourseController(ObjectSpawn spawn) { super(spawn, "Infinite Sonic course"); }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new CourseController(context.spawn());
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (player == null) return;
        // This prototype has no timed failure; leave the stock HUD timer frozen.
        services().levelGamestate().pauseTimer();
        int delta = player.getCentreX() >= 8192 ? 4096
                : origin > 0 && player.getCentreX() < 2048 ? -4096 : 0;
        if (delta == 0) return;
        origin += delta / 256;
        var library = services().gameService(TerrainLibrary.class);
        var level = services().currentLevel();
        var surface = LevelMutationSurface.forLevel(level);
        for (int x = 0; x < TerrainLibrary.WIDTH; x++) {
            for (int y = 0; y < library.height(); y++) {
                surface.setBlockInMap(0, x, y, library.cell(origin + x, y));
            }
        }
        // PlayableEntity.shiftX is the public native-position delta operation:
        // it preserves 16:16 fractions, velocity and the current pose.
        player.shiftX(-delta);
        var camera = services().camera();
        camera.setX((short) (camera.getX() - delta));
        camera.setXCopy((short) (camera.getXCopy() - delta));
        services().levelManager().invalidateAllTilemaps();
    }
}
