package infinite;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import java.util.List;

/** Small ROM-art explosion that can cross a course rebase without jumping position. */
public final class CourseBurst extends AbstractObjectInstance implements ModRewindRecreatable {
    private int x;
    private int y;
    private int age;
    public CourseBurst(ObjectSpawn spawn) {
        super(spawn, "Course explosion");
        x = spawn.x();
        y = spawn.y();
    }
    public static ObjectSpawn spawnAt(int x, int y) {
        return new ObjectSpawn(x, y, 0, 0, 0, false, y, -1,
                "infinite-sonic", "infinite-sonic:burst");
    }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isPersistent() { return !isDestroyed(); }
    @Override public boolean participatesInLevelRepeatOffset() { return true; }
    @Override public void applyLevelRepeatOffset(int dx, int dy) {
        x += dx;
        y += dy;
        updateDynamicSpawn(x, y);
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (++age >= 40) setDestroyed(true);
    }
    @Override public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) {
        return new CourseBurst(context.spawn());
    }
    @Override public int getPriorityBucket() {
        return RenderPriority.bucket(1); // ExplosionItem: obPriority=1.
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        var renderer = getRenderer(ObjectArtKeys.EXPLOSION);
        if (renderer != null) renderer.drawFrameIndex(age / 8, x, y, false, false);
    }
}
