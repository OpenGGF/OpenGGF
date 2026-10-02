package infinite;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import java.util.List;

/** ROM-art remix with bounded mod patrols, not a replacement for stock badnik behavior. */
public final class CourseBadnik extends AbstractBadnikInstance implements RewindRecreatable {
    private long worldAnchor;
    private int anchorX;
    private int anchorY;
    private int offset;
    private int direction = -1;
    private int ticks;

    /** Spawn-only constructor lets the rewind codec create its restoration probe. */
    public CourseBadnik(ObjectSpawn spawn) {
        this(spawn, spawn.x());
    }

    public CourseBadnik(ObjectSpawn spawn, long worldAnchor) {
        super(spawn, spawn.subtype() == 0 ? "Course Motobug" : "Course Buzz Bomber");
        this.worldAnchor = worldAnchor;
        anchorX = spawn.x();
        anchorY = spawn.y();
    }

    public boolean flying() { return spawn.subtype() != 0; }
    public long worldAnchor() { return worldAnchor; }
    @Override public boolean isPersistent() { return !isDestroyed(); }
    @Override public boolean participatesInLevelRepeatOffset() { return true; }
    @Override public void applyLevelRepeatOffset(int dx, int dy) {
        anchorX += dx;
        anchorY += dy;
        currentX += dx;
        currentY += dy;
        updateDynamicSpawn(currentX, currentY);
    }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new CourseBadnik(context.spawn(), 0);
    }
    @Override protected void updateMovement(int vIntRunCount, PlayableEntity player) {
        var camera = services().camera();
        if (anchorX + EncounterPlan.PATROL_RADIUS < camera.getX() - 256
                || anchorX - EncounterPlan.PATROL_RADIUS > camera.getX() + camera.getWidth() + 384) {
            setDestroyed(true);
            return;
        }
        offset += direction;
        if (Math.abs(offset) >= EncounterPlan.PATROL_RADIUS) direction = -direction;
        currentX = anchorX + offset;
        facingLeft = direction < 0;
        ticks = (ticks + 1) & 127;
        if (flying()) {
            int bob = ticks < 64 ? ticks / 4 - 8 : (127 - ticks) / 4 - 8;
            currentY = anchorY + bob;
        } else {
            currentY = services().gameService(TerrainLibrary.class).floorAt(worldAnchor + offset) - 14;
        }
    }
    @Override protected void updateAnimation(int vIntRunCount) {
        // Ani_Moto driving frames 0,1,0,2; Ani_Buzz flight frames 2,3.
        animFrame = flying() ? 2 + ((ticks / 4) & 1) : switch ((ticks / 8) & 3) {
            case 1 -> 1;
            case 3 -> 2;
            default -> 0;
        };
    }
    @Override protected int getCollisionSizeIndex() {
        // Moto_Main col_40x32 ($0C); Buzz_Main col_48x24 ($08), shipped S1 tables.
        return flying() ? 0x08 : 0x0c;
    }
    @Override protected DestructionEffects.DestructionConfig getDestructionConfig() {
        // S1 sfx_BreakItem=$C1. Shared destruction owns scoring and replacement-slot transfer.
        return new DestructionEffects.DestructionConfig(0xc1, null, false, null,
                (x, y, services, points) -> new CourseBurst(CourseBurst.spawnAt(x, y)), false);
    }
    @Override public int getPriorityBucket() {
        // Buzz_Main obPriority=3; Moto_Main obPriority=4.
        return RenderPriority.bucket(flying() ? 3 : 4);
    }
    @Override public int getOnScreenHalfWidth() { return flying() ? 24 : 20; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        var renderer = getRenderer(flying() ? ObjectArtKeys.BUZZ_BOMBER : ObjectArtKeys.MOTOBUG);
        if (renderer != null) renderer.drawFrameIndex(animFrame, currentX, currentY, !facingLeft, false);
    }
}
