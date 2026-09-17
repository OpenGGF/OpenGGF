package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ROM {@code loc_81E82} (sonic3k.asm:173963-174030): a phase-1 end boss turret
 * ({@code ObjDat3_831BA}: priority {@code $280}, frame {@code $1C}). Its countdown starts at
 * {@code subtype * 8}; once the boss sets {@code $38} bit 3 each expiry reloads {@code $60} and fires a
 * {@code loc_81F14} shot. Every sixteenth V-int it aims at Player 1 in eight directions (frame
 * {@code $1C} plus half the direction word), and {@code sub_82CD6} places it at its
 * {@code word_81EAC} offset plus {@code byte_82D08[direction]}. When the boss's {@code status} bit 7
 * is set it becomes {@code Obj_FlickerMove} with {@code Obj_VelocityIndex} velocity by subtype.
 */
final class DdzEndBossTurretObjectInstance extends AbstractDdzObjectInstance {
    /** {@code word_81EAC}. */
    private static final int[][] OFFSETS = {{0x90, 0x60}, {0xB0, 0x58}, {0xA8, 0x78}};
    /** {@code byte_82D08}, indexed by the direction word (0, 2, ... $E). */
    private static final int[][] DIRECTION_OFFSETS = {
            {0, 8}, {8, 8}, {8, 0}, {8, -8}, {0, -8}, {-8, -8}, {-8, 0}, {-8, 8}};
    /** {@code Obj_VelocityIndex} entries 0-2. */
    private static final int[][] FLICKER_VELOCITIES = {{-0x100, -0x100}, {0x100, -0x100}, {-0x200, -0x200}};
    private static final int PALETTE = 2;

    private final int subtype;
    private boolean armed;
    private int timer;
    /** {@code angle(a0)} word: 0, 2, ... $E. */
    private int direction;
    private int mappingFrame = 0x1C;
    private boolean flickerMove;
    private boolean flickerVisible;
    private int xPos;
    private int yPos;
    private short xVel;
    private short yVel;


    DdzEndBossTurretObjectInstance(DdzEndBossObjectInstance boss, int subtype) {
        super(new ObjectSpawn(boss == null ? 0 : boss.getX(), boss == null ? 0 : boss.getY(), 0, subtype, 0, false, 0),
                "DDZEndBossTurret", boss);
        this.subtype = subtype;
        this.timer = subtype << 3;
        if (boss != null) {
            xPos = (boss.getX() & 0xFFFF) << 16;
            yPos = (boss.getY() & 0xFFFF) << 16;
        }
    }

    @Override
    public DdzEndBossTurretObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzEndBossTurretObjectInstance(null, ctx.spawn().subtype());
    }

    @Override
    public int getX() {
        return (xPos >>> 16) & 0xFFFF;
    }

    @Override
    public int getY() {
        return (yPos >>> 16) & 0xFFFF;
    }

    int direction() {
        return direction;
    }

    /** {@code status} bit 7: set when {@code Child_Draw_Sprite_FlickerMove} releases the turret. */
    boolean released() {
        return flickerMove || goingAway();
    }

    @Override
    protected void updateObject(int vIntRunCount, PlayableEntity playerEntity) {
        if (flickerMove) {
            flicker();
            return;
        }
        if (!(parent instanceof DdzEndBossObjectInstance boss) || boss.isDestroyed()) {
            goDelete();
            return;
        }
        if (!armed && boss.flag(3)) {
            armed = true;
        }
        if (armed) {
            timer = (short) (timer - 1);
            if (timer < 0) {
                timer = 0x60;
                int x = getX();
                int y = getY();
                int dir = direction;
                spawnChild(() -> new DdzEndBossTurretShotObjectInstance(this, x, y, dir));
            }
        }
        if ((vIntRunCount & 0xF) == 0) {
            AbstractPlayableSprite player = DdzObjectSupport.player(services());
            if (player != null) {
                int angle = DdzObjectSupport.angleTowards(getX(), getY(), player.getCentreX(), player.getCentreY());
                direction = (((angle + 0x10) & 0xFF) >> 4) & 0xFE;
                mappingFrame = 0x1C + (direction >> 1);
            }
        }
        // sub_82CD6
        int[] base = OFFSETS[subtype >> 1];
        int[] tip = DIRECTION_OFFSETS[direction >> 1];
        xPos = ((boss.getX() + base[0] + tip[0]) & 0xFFFF) << 16 | (xPos & 0xFFFF);
        yPos = ((boss.getY() + base[1] + tip[1]) & 0xFFFF) << 16 | (yPos & 0xFFFF);
        // Child_Draw_Sprite_FlickerMove
        if (boss.destroyedStatus()) {
            flickerMove = true;
            int[] velocity = FLICKER_VELOCITIES[subtype >> 1];
            xVel = (short) velocity[0];
            yVel = (short) velocity[1];
        }
    }

    /** {@code Obj_FlickerMove}: MoveSprite with gravity, delete off screen, draw every other frame. */
    private void flicker() {
        xPos += xVel << 8;
        yPos += yVel << 8;
        yVel = (short) (yVel + 0x38);
        if (DdzObjectSupport.outOfRangeX(services(), getX()) || DdzObjectSupport.outOfRangeY(services(), getY())) {
            goDelete();
            return;
        }
        // bchg #6,$38(a0) / beq: drawn on the frames the bit was set before the toggle.
        flickerVisible = !flickerVisible;
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(0x280);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawable() || (flickerMove && flickerVisible)) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DDZ_MISC);
        if (renderer != null) {
            renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false, PALETTE);
        }
    }


}
