package com.openggf.game.sonic1.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ClimbableWall;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.game.PlayableEntity;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;

import java.util.List;

/**
 * GHZ Edge Walls (Object ID 0x44).
 * <p>
 * Solid wall barriers used exclusively in Green Hill Zone. The subtype byte controls
 * both the visual frame and whether the wall provides solid collision:
 * <ul>
 *   <li>Bit 4 = 0: Solid wall (collision active)</li>
 *   <li>Bit 4 = 1: Display-only decoration (no collision)</li>
 * </ul>
 * The frame index is the subtype with bit 4 cleared:
 * <ul>
 *   <li>Frame 0: Light top + shadow body</li>
 *   <li>Frame 1: All light</li>
 *   <li>Frame 2: All dark/shadow</li>
 * </ul>
 * <p>
 * Collision dimensions from disassembly: d1 = $13 (half-width 19px), d2 = $28 (half-height 40px).
 * Uses custom Obj44_SolidWall routine which pushes player sideways (zeroing inertia + X velocity)
 * and blocks upward movement through ceiling.
 * <p>
 * Reference: docs/s1disasm/_incObj/44 GHZ Edge Walls.asm
 */
public class Sonic1EdgeWallObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, SolidObjectProvider, ClimbableWall, ObjectVisualFacing.NonDirectional {

// Edge-wall light/shadow mapping sections are static terrain geometry without horizontal facing.

    // From disassembly: move.w #$13,d1
    private static final int HALF_WIDTH = 0x13;

    // From disassembly: move.w #$28,d2
    private static final int HALF_HEIGHT = 0x28;

    // From disassembly: move.b #16/2,obActWid(a0). The strip is 16 pixels wide; d1 is that
    // half-width plus the 11 pixels every Sonic 1 solid adds for the player's side.
    private static final int FACE_HALF_WIDTH = 16 / 2;

    private int frameIndex;
    private boolean solid;
    private ClimbableWall.Box wall;

    public Sonic1EdgeWallObjectInstance(ObjectSpawn spawn) {
        super(spawn, "EdgeWall");

        int subtype = spawn.subtype();
        // From disassembly: bclr #4,obFrame(a0) / beq.s Edge_Solid
        this.solid = (subtype & 0x10) == 0;
        // Frame = subtype with bit 4 cleared
        this.frameIndex = subtype & ~0x10;
    }

    // move.b #6,obPriority(a0): docs/s1disasm/_incObj/44 GHZ Edge Walls.asm:23.
    private static final int PRIORITY_BUCKET = RenderPriority.bucket(6);

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.GHZ_EDGE_WALL);
        if (renderer == null) return;
        renderer.drawFrameIndex(frameIndex, getX(), getY(), false, false);
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(HALF_WIDTH, HALF_HEIGHT, HALF_HEIGHT);
    }

    /**
     * Engine extension, with no counterpart in Sonic 1: the solid strip as a wall for a climber's
     * probes, as wide as it is drawn and as tall as Edge_Solid's d2. A display-only wall is none.
     */
    @Override
    public ClimbableWall.Box climbableWall() {
        if (solid && wall == null) {
            wall = new ClimbableWall.Box(getX() - FACE_HALF_WIDTH, getY() - HALF_HEIGHT,
                    getX() + FACE_HALF_WIDTH - 1, getY() + HALF_HEIGHT - 1);
        }
        return wall;
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        return solid && !readsTheWallHimself(playerEntity);
    }

    /**
     * double_jump_flag 1, 4 and 5: Knuckles' glide, wall climb and pull-up. Their own probes find this
     * wall and place him against it. EdgeWall_SolidWall would stop a glide a pixel short of the contact
     * those probes need for a grab, and push the first steps of a pull-up back off the ledge.
     */
    private static boolean readsTheWallHimself(PlayableEntity playerEntity) {
        if (!(playerEntity instanceof AbstractPlayableSprite player)
                || player.getSecondaryAbility() != SecondaryAbility.GLIDE) {
            return false;
        }
        int state = player.getDoubleJumpFlag();
        return state == 1 || state == 4 || state == 5;
    }

}
