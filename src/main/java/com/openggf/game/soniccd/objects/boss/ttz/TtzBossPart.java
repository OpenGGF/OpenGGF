package com.openggf.game.soniccd.objects.boss.ttz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * State the Tidal Tempest boss slots share (objects/r4/boss_door.asm, boss_chase.asm, boss.asm). The routines name
 * their scratch bytes by offset; the subclasses name them by use. Here: {@link #flipX} ({@code oSprFlags} bit 0, the
 * horizontal flip {@code DrawSprite} reads, Level/Object Functions.asm:382-387), {@link #facing} ({@code oFlags}
 * bit 0, the status direction the routines test) and {@code SineCosine}.
 */
abstract class TtzBossPart extends CdBossPart {
    /** {@code oSprFlags} bits 0 and 1: drawn mirrored, upside down. */
    boolean flipX;
    boolean flipY;
    /** {@code oFlags} bit 0. */
    boolean facing;
    /** False on a frame whose routine skipped {@code DrawObject} ({@code addq.l #4,sp}). */
    boolean hidden;

    TtzBossPart(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    TtzBossPart(int id, int x, int y, String name) {
        super(id, x, y, name);
    }

    /**
     * The logical facing of a part whose body {@code AnimateObject} mirrors by {@code oFlags} bit 0 ({@link #facing}):
     * the routines set the bit when the part moves or turns left (objects/r4/boss_chase.asm:153-277, the turn in
     * objects/r4/boss.asm:222-291), and the frame's flip is XORed with it.
     */
    final com.openggf.level.objects.ObjectVisualFacing.Basis facingByFlags() {
        return facing ? com.openggf.level.objects.ObjectVisualFacing.Basis.LEFT
                : com.openggf.level.objects.ObjectVisualFacing.Basis.RIGHT;
    }

    /** {@code SineCosine}: d0 = sine, d1 = cosine of the byte angle, $100 = 1. */
    static int sine(int angle) { return com.openggf.physics.TrigLookupTable.sinHex(angle & 0xFF); }

    static int cosine(int angle) { return com.openggf.physics.TrigLookupTable.cosHex(angle & 0xFF); }

    /** This part's object services, for the package's touch helper. */
    final com.openggf.level.objects.ObjectServices objectServices() { return services(); }

    final CdStagePayload payload() {
        var level = cdLevel();
        return level == null ? null : level.payload();
    }

    /**
     * {@code AnimateObject} (common/object_animate.asm:6-85) over the stage's script table in catalogue column
     * {@code column}: a new {@code oAnim} restarts the script; when the timer runs out the next byte is a frame
     * (bits 0-4 the mapping frame; bits 5-6 the X and Y flip, XOR {@code oFlags} bits 0-1, into {@code oSprFlags})
     * or a command: $FF loops, $FE steps back, $FD changes animation, $FC adds 2 to the routine, $FB restarts with
     * {@code routine_2} cleared, $FA adds 2 to {@code routine_2} ({@link #animRoutine2}).
     */
    final void animateObject(String column) {
        var image = stageImage();
        int table = TtzBossCatalogue.offset(payload(), column);
        if (image == null || table < 0) return;
        if (anim != prevAnim) {
            prevAnim = anim;
            animFrame = 0;
            animTime = 0;
        }
        animTime = (animTime - 1) & 0xFF;
        if ((animTime & 0x80) == 0) return;                             // subq.b #1,obj.anim_timer; bpl.s
        int script = table + (short) image.u16(table + anim * 2);
        animTime = image.u8(script);
        int value = image.u8(script + 1 + animFrame);
        if (value < 0x80) {
            applyFrame(value);
            return;
        }
        switch (value) {
            case 0xFF -> {
                animFrame = 0;
                applyFrame(image.u8(script + 1));
            }
            case 0xFE -> {
                int back = image.u8(script + 2 + animFrame);
                animFrame = (animFrame - back) & 0xFF;
                applyFrame(image.u8(script + 1 + animFrame));
            }
            case 0xFD -> anim = image.u8(script + 2 + animFrame);
            case 0xFC -> routine = (routine + 2) & 0xFF;
            case 0xFB -> {
                animFrame = 0;
                animRoutine2(0);
            }
            case 0xFA -> animRoutine2(2);
            default -> { }
        }
    }

    /** {@code $FB} (0: clear) and {@code $FA} (2: add 2) on the part's {@code routine_2}; parts without one ignore it. */
    void animRoutine2(int add) { }

    private void applyFrame(int value) {
        mapFrame = value & 0x1F;
        int flips = ((value >> 5) & 3) ^ (facing ? 1 : 0);              // rol.b #3,d1; eor.b obj.flags,d1; andi.b #3
        flipX = (flips & 1) != 0;
        flipY = (flips & 2) != 0;
        animFrame = (animFrame + 1) & 0xFF;
    }

    /**
     * sub_20CE72 (objects/r4/boss.asm:1083-1098): the offset of the byte angle {@code angle >> 8} on an ellipse of
     * radii {@code radiusX} (cosine) and {@code radiusY} (sine) words: {@code (r * trig) >> 8} each (asr.w of the
     * low word of the product), added to X and Y. Returns {dx, dy}, which the caller stores as var_38/var_3a.
     */
    final int[] orbit(int angle, int radiusX, int radiusY) {
        int a = (angle >> 8) & 0xFF;
        int dy = (short) (((short) radiusY * sine(a)) & 0xFFFF) >> 8;    // muls.w d0,d2; asr.w #8,d2
        int dx = (short) (((short) radiusX * cosine(a)) & 0xFFFF) >> 8;  // muls.w d1,d3; asr.w #8,d3
        addX(dx);
        addY(dy);
        return new int[] {dx & 0xFFFF, dy & 0xFFFF};
    }

    /** {@code jsr DeleteOtherObject} on a linked slot, when it still holds the object. */
    static void deleteOther(CdBossPart other) {
        if (other != null && !other.isDestroyed()) other.setDestroyed(true);
    }

    @Override protected boolean drawn() { return !hidden && routine != 0; }

    @Override
    protected String sheetKey() {
        return artProvider() == null ? null : TtzBossArt.key(sheet());
    }

    /** The part's sheet kind ({@link TtzBossArt}). */
    abstract String sheet();

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || !drawn()) return;
        String key = sheetKey();
        if (key != null) drawFrame(key, mapFrame, flipX, flipY);
    }
}
