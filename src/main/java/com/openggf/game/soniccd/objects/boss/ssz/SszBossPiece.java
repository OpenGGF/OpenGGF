package com.openggf.game.soniccd.objects.boss.ssz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * State every Stardust Speedway race object shares (objects/r7/boss.asm:3-2085, boss_block.asm, spikes.asm,
 * amy_rose.asm). The routines name their scratch bytes by offset; these fields name them by use:
 * <ul>
 *   <li>{@link #status}: {@code obj.flags}; bit 0 faces left (AnimateObject flips the frame by it).</li>
 *   <li>{@link #xSpeed}, {@link #ySpeed}: {@code obj.x_speed}, {@code obj.y_speed}; {@link #accelX} and
 *       {@link #gravity} ({@code var_30}, {@code var_32}) are added to them, {@link #speedCap} ({@code var_34}) caps
 *       the X speed in {@code sub_20CE68}.</li>
 *   <li>{@link #timer} ({@code var_2a} word) and {@link #step} ({@code var_2d}): a routine's frame count and its
 *       sub-routine index, which the script runner {@code sub_20CEE0} shares.</li>
 *   <li>{@link #bits}: {@code var_2c}, the race's flag byte (Eggman: bit 2 on screen, 3 exhaust, 4 laser kills,
 *       5 laser hurts, 6 laser shown; the laser copies 5 and 6 down its chain; Metal Sonic: 5 racing, 6 the player
 *       is ahead).</li>
 * </ul>
 */
abstract class SszBossPiece extends CdBossPart {
    int status;
    int xSpeed;
    int ySpeed;
    int accelX;
    int gravity;
    int speedCap;
    int timer;
    int step;
    int bits;
    /** {@code obj.sprite_flags} bits 0-1: the drawn frame's flips. */
    int renderFlip;
    /** This frame's routine left through {@code addq.l #4,sp} or {@code DeleteObject}: nothing drawn. */
    boolean skipDraw;

    SszBossPiece(int id, int x, int y, String name) {
        super(id, x, y, name);
    }

    SszBossPiece(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    final boolean has(int bit) { return (bits & bit) != 0; }

    final void set(int bit) { bits |= bit; }

    final void clear(int bit) { bits &= ~bit; }

    final boolean facingLeft() { return (status & 1) != 0; }

    /** {@code obj.anim_id}, as another race object reads it. */
    final int animId() { return anim; }

    /** {@code obj.width_2} ({@code $19}), as another race object reads it. */
    final int halfWidth() { return width; }

    // ---- movement (objects/r7/boss.asm:1782-1822) ----------------------------------------------------------

    /** loc_20CEA0 (objects/r7/boss.asm:1807-1811): {@code var_30} into the X speed, {@code var_32} into the Y speed, then move. */
    final void accelerateAndMove() {
        xSpeed = (short) (xSpeed + accelX);
        ySpeed = (short) (ySpeed + gravity);
        move();
    }

    /** loc_20CEB0 (objects/r7/boss.asm:1813-1822): {@code ext.l; lsl.l #8; add.l} for both axes. */
    final void move() {
        addXLong((long) (short) xSpeed << 8);
        addYLong((long) (short) ySpeed << 8);
    }

    /**
     * sub_20CE68 (objects/r7/boss.asm:1782-1803): gravity, then {@code var_30} into the X speed; with a positive
     * acceleration the speed is cut to {@code var_34} once it reaches it, with a negative one raised to it once it
     * falls to it; then move.
     */
    final void accelerateCappedAndMove() {
        ySpeed = (short) (ySpeed + gravity);
        xSpeed = (short) (xSpeed + accelX);
        if ((short) accelX > 0) {
            if ((short) speedCap <= (short) xSpeed) xSpeed = speedCap;   // cmp.w x_speed,d0; bgt.s
        } else if ((short) accelX < 0) {
            if ((short) speedCap >= (short) xSpeed) xSpeed = speedCap;   // cmp.w x_speed,d0; blt.s
        }
        move();
    }

    /**
     * sub_20CEE0 (objects/r7/boss.asm:1846-1868): while the frame count {@code var_2a} has not reached entry
     * {@code var_2d}'s, it counts up; on the entry's frame it counts up, steps {@code var_2d} and returns the entry
     * index whose routine the caller runs ({@code jmp (a3)}). -1 otherwise.
     */
    final int runScript(String column) {
        CdStageImage image = stageImage();
        CdStagePayload payload = payload();
        if (image == null || payload == null) return -1;
        int frame = SszBossCatalogue.scriptFrame(image, payload, column, step);
        if (frame != (timer & 0xFFFF)) {
            timer = (timer + 1) & 0xFFFF;
            return -1;
        }
        timer = (timer + 1) & 0xFFFF;
        int entry = step;
        step = (step + 1) & 0xFF;
        return entry;
    }

    /** EggmanObject_1_Routine4 (objects/r7/boss.asm:1826-1829, loc_20CEDA :1840-1842): the word count runs out, then {@code var_2d} + 1. */
    final void waitWord() {
        timer = (timer - 1) & 0xFFFF;
        if (timer == 0) step = (step + 1) & 0xFF;
    }

    /** MetalSonicObject_2_Routine4_0 (objects/r7/boss.asm:1833-1842): the byte count {@code var_2a} runs out, then {@code var_2d} + 1. */
    final void waitByte() {
        timer = (timer & 0xFF00) | ((timer - 1) & 0xFF);
        if ((timer & 0xFF) == 0) step = (step + 1) & 0xFF;
    }

    // ---- AnimateObject ---------------------------------------------------------------------------------------

    /**
     * {@code AnimateObject} (common/object_animate.asm:6-85) over the script table at {@code table}, with the frame
     * byte's bits 5-6 XOR {@code obj.flags} bits 0-1 into the sprite flips; $FF loops, $FE steps back, $FD changes the
     * animation, $FC adds 2 to the routine.
     */
    final void animateObject(int table) {
        CdStageImage image = stageImage();
        if (image == null || table < 0) return;
        if (anim != prevAnim) {
            prevAnim = anim;
            animFrame = 0;
            animTime = 0;
        }
        animTime = (animTime - 1) & 0xFF;
        if ((animTime & 0x80) == 0) return;
        int script = table + (short) image.u16(table + anim * 2);
        animTime = image.u8(script);
        int value = image.u8(script + 1 + animFrame);
        if (value >= 0x80) {
            switch (value) {
                case 0xFF -> {
                    animFrame = 0;
                    value = image.u8(script + 1);
                }
                case 0xFE -> {
                    int back = image.u8(script + 2 + animFrame);
                    animFrame = (animFrame - back) & 0xFF;
                    value = image.u8(script + 1 + animFrame);
                }
                case 0xFD -> {
                    anim = image.u8(script + 2 + animFrame);
                    return;
                }
                case 0xFC -> {
                    routine = (routine + 2) & 0xFF;
                    return;
                }
                default -> {
                    return;
                }
            }
        }
        mapFrame = value & 0x1F;
        renderFlip = (((value << 3) | (value >> 5)) ^ status) & 3;        // rol.b #3,d1; eor.b d0,d1; andi.b #3
        animFrame = (animFrame + 1) & 0xFF;
    }

    // ---- services --------------------------------------------------------------------------------------------

    /** {@code SpawnObjectAfter} (common/object_spawn.asm:295-311): the first free slot after this object's. */
    final boolean spawnAfter(AbstractObjectInstance child) {
        var objects = services().objectManager();
        if (objects == null) return false;
        objects.addDynamicObjectAfterCurrent(child);
        return !child.isDestroyed();
    }

    final CdStagePayload payload() {
        var level = cdLevel();
        return level == null ? null : level.payload();
    }

    final int table(String column) {
        return SszBossCatalogue.offset(payload(), column);
    }

    /**
     * {@code CheckBlockDown} (common/object_block_collide.asm:143-159) at {@code y + dy}: the floor distance below
     * {@code oYRadius}. With no floor in this block or the next, {@code CheckBlockY} answers from the second block's
     * empty branch, $1F less the probe's Y within its block (CheckBlockYEmpty and CheckBlockY2Empty,
     * common/block_collide.asm:238-243,332-337): Sonic CD's object floor rule.
     */
    final int floorDistance(int dy) {
        return com.openggf.physics.ObjectTerrainUtils.checkFloorDist(x, (y + dy) & 0xFFFF, (byte) yRadius).distance();
    }

    /** The player object ({@code player_object}), or null. */
    static AbstractPlayableSprite sonic(com.openggf.game.PlayableEntity player) {
        return player instanceof AbstractPlayableSprite s ? s : null;
    }

    /** {@code obj.x(a1)} of the player: the centre X word. */
    static int px(AbstractPlayableSprite player) { return (short) player.getCentreX(); }

    /**
     * {@code CheckObjectDespawn} for the layout objects (CheckObjDespawn2, Object Spawner.asm:388-399): kept while on
     * screen, else removed when its 128-pixel column is more than $280 beyond the camera's less $80.
     */
    final boolean despawnOutOfRange(int cameraX) {
        if (renderFlagOnScreen()) return false;
        int chunk = x & 0xFF80;
        int cameraChunk = (cameraX - 0x80) & 0xFF80;
        return ((chunk - cameraChunk) & 0xFFFF) > 0x80 + (320 + 0x40) + 0x80;
    }

    // ---- drawing ---------------------------------------------------------------------------------------------

    /** The part's sheet kind ({@link SszBossArt}). */
    abstract String sheet();

    @Override
    protected String sheetKey() {
        return artProvider() == null ? null : SszBossArt.key(sheet());
    }

    @Override
    protected boolean drawn() { return !skipDraw && routine != 0; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || !drawn()) return;
        String key = sheetKey();
        if (key != null) drawFrame(key, mapFrame, (renderFlip & 1) != 0, (renderFlip & 2) != 0);
    }
}
