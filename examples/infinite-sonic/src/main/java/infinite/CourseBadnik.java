package infinite;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.physics.TrigLookupTable;
import java.util.List;

/**
 * ROM-art remix with bounded mod patrols, not a replacement for stock badnik behavior.
 * The spawn subtype holds the {@link CourseSpecies} ordinal chosen for the zone.
 */
public final class CourseBadnik extends AbstractBadnikInstance
        implements RewindRecreatable, TouchResponseListener {
    private long worldAnchor;
    private int anchorX;
    private int anchorY;
    private int offset;
    private int direction = -1;
    private int ticks;
    private int spin; // Orbinaut spike angle: Orb_MoveOrb steps 1 per frame, against the facing.

    /** Spawn-only constructor lets the rewind codec create its restoration probe. */
    public CourseBadnik(ObjectSpawn spawn) {
        this(spawn, spawn.x());
    }

    public CourseBadnik(ObjectSpawn spawn, long worldAnchor) {
        super(spawn, "Course " + CourseSpecies.of(spawn.subtype()).name());
        this.worldAnchor = worldAnchor;
        anchorX = spawn.x();
        anchorY = spawn.y();
    }

    /** The {@link CourseSpecies} id. */
    public int species() { return Math.floorMod(spawn.subtype(), CourseSpecies.COUNT); }
    private CourseSpecies.Traits traits() { return CourseSpecies.of(spawn.subtype()); }
    public boolean flying() { return traits().flying(); }
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
        var species = traits();
        ticks = (ticks + 1) & 127;
        if (species.patrols()) {
            offset += direction;
            if (Math.abs(offset) >= EncounterPlan.PATROL_RADIUS) direction = -direction;
            facingLeft = direction < 0;
        } else {
            // Stationary hoppers turn to face Sonic.
            facingLeft = player == null || player.getCentreX() < currentX;
        }
        currentX = anchorX + offset;
        spin = (spin + (facingLeft ? 1 : -1)) & 0xff;
        if (species.flying()) {
            int bob = ticks < 64 ? ticks / 4 - 8 : (127 - ticks) / 4 - 8;
            currentY = anchorY + bob;
        } else {
            currentY = services().gameService(TerrainLibrary.class).floorAt(worldAnchor + offset) - species.depth();
            // Ball Hog's jump frame (3) lifts it a little off the floor.
            if (species.id() == CourseSpecies.BALL_HOG && species.frame(ticks) == 3) currentY -= 8;
        }
    }
    @Override protected void updateAnimation(int vIntRunCount) {
        animFrame = traits().frame(ticks);
    }
    @Override protected int getCollisionSizeIndex() { return traits().collision() & 0x3f; }
    @Override public int getCollisionFlags() {
        // Hazards (Roller rolling $8E, Bomb $9A) keep col_hurt so attacks still hurt Sonic.
        return isDestroyed() ? 0 : traits().collision();
    }
    @Override public TouchRegion[] getMultiTouchRegions() {
        if (species() != CourseSpecies.ORBINAUT || isDestroyed()) return null;
        var regions = new TouchRegion[5];
        regions[0] = new TouchRegion(currentX, currentY, getCollisionFlags());
        for (int i = 0; i < 4; i++) {
            int[] spike = spike(i);
            regions[i + 1] = new TouchRegion(spike[0], spike[1], CourseSpecies.ORBINAUT_SPIKE);
        }
        return regions;
    }
    /** Orb_CircleSpikeball: CalcSine at quarter-turn spacing, asr #4 gives the radius-16 orbit. */
    private int[] spike(int index) {
        int angle = (spin + index * 0x40) & 0xff;
        return new int[]{currentX + (TrigLookupTable.cosHex(angle) >> 4),
                currentY + (TrigLookupTable.sinHex(angle) >> 4)};
    }
    /** Runs before the engine's hurt pass: a shield or 20 rings absorb the hit (see {@link CourseGuard}). */
    @Override public void onTouchResponse(PlayableEntity entity, TouchResponseResult result, int frameCounter) {
        if (!isDestroyed() && entity instanceof com.openggf.sprites.playable.AbstractPlayableSprite player) {
            CourseGuard.absorb(services(), player, result);
        }
    }
    @Override protected DestructionEffects.DestructionConfig getDestructionConfig() {
        // S1 sfx_BreakItem=$C1. Shared destruction owns scoring and replacement-slot transfer.
        return new DestructionEffects.DestructionConfig(0xc1, null, false, null,
                (x, y, services, points) -> new CourseBurst(CourseBurst.spawnAt(x, y)), false);
    }
    @Override public int getPriorityBucket() { return RenderPriority.bucket(traits().priority()); }
    @Override public int getOnScreenHalfWidth() { return traits().halfWidth(); }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        var species = traits();
        var renderer = getRenderer(species.artKey());
        if (renderer == null) return;
        boolean flip = species.flipFacingLeft() == facingLeft;
        renderer.drawFrameIndex(animFrame, currentX, currentY, flip, false);
        if (species.id() == CourseSpecies.ORBINAUT) {
            for (int i = 0; i < 4; i++) {
                int[] spike = spike(i);
                renderer.drawFrameIndex(3, spike[0], spike[1], false, false);
            }
        }
    }
}
