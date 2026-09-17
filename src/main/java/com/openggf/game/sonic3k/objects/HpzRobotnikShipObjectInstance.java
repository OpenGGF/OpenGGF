package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

import static com.openggf.game.sonic3k.objects.HpzKnucklesCutsceneSupport.*;

/**
 * ROM {@code loc_64E88} (sonic3k.asm:132749-132873): Robotnik's ship in the Hidden Palace Master
 * Emerald theft ({@code _unkFAAE}). It hovers at {@code ($1640,$2D0)} with
 * {@code Obj_RobotnikHead4} and the {@code loc_6502E} crane until the crane lifts the emerald
 * ({@code $38} bit 2), drops for {@code $40} frames, then swings right at 2 pixels a frame to
 * {@code X $1890}. There it becomes touchable ({@code collision_flags $F}, property {@code -1}),
 * releases the two {@code loc_6531E} spark emitters and waits for both chains
 * ({@code _unkFAB8} bits 2 and 3) before zapping Knuckles (bit 4). Once Knuckles lands (bit 5) it
 * flies right; off screen it deletes the Master Emerald and itself.
 *
 * <p>{@code sub_66348} flashes the head after Knuckles bounces off the ship ({@code status} bit 6);
 * {@code sub_66372} handles a player hit: {@code sfx_BossHit} and a {@code sub_7A614} palette
 * flash of palette line 0 colours 7, {@code $E} and {@code $F}.
 */
public final class HpzRobotnikShipObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, TouchResponseProvider, TouchResponseAttackable {
    static final int START_X = 0x1640;
    static final int START_Y = 0x2D0;
    private static final int MAPPING_FRAME = 0xA;
    private static final int ZAP_X = 0x1890;
    private static final int COLLISION_FLAGS = 0xF;
    private static final int SWING_MAX = 0xC0;
    private static final int SWING_ACCEL = 0x10;
    /** {@code word_7A628}: palette line 0 colours 7, $E and $F, two rows of three words. */
    private static final int[] FLASH = {0x0008, 0x0866, 0x0222, 0x0888, 0x0CCC, 0x0EEE};
    /** {@code addi.w #2*2,d0}: two words into {@code word_7A628}. */
    private static final int FIXBUGS0_EVEN_FLASH_INDEX = 2;
    private static final String FLASH_OWNER = "s3k.hpz.robotnikShipFlash";

    private static final int PHASE_INIT = 0;
    private static final int PHASE_WAIT_CRANE = 1;
    private static final int PHASE_DROP = 2;
    private static final int PHASE_CARRY = 3;
    private static final int PHASE_WAIT_SPARKS = 4;
    private static final int PHASE_WAIT_LANDED = 5;
    private static final int PHASE_LEAVE = 6;

    private int phase;
    private int x;
    private int y;
    private int xSub;
    private int ySub;
    private int xVel;
    private int yVel;
    private int timer;
    /** {@code $38(a0)}. */
    private int flags;
    /** {@code status(a0)} bit 6. */
    private boolean hitFlag;
    /** {@code $20(a0)}. */
    private int flashTimer;
    private int collisionFlags;
    /** {@code $25(a0)}: {@code Touch_Enemy} saves the flags here before clearing them. */
    private int savedCollisionFlags;
    private int collisionProperty;
    private boolean touchListed;

    private record RewindExtra(int[] state)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public HpzRobotnikShipObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HpzRobotnikShip");
        x = spawn.x();
        y = spawn.y();
    }

    @Override
    public HpzRobotnikShipObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzRobotnikShipObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        HpzZoneRuntimeState hpz = hpz(services());
        if (hpz == null) {
            return;
        }
        touchListed = false;
        switch (phase) {
            case PHASE_INIT -> {
                // loc_64E88: move.w a0,(_unkFAAE).w, render flip, ($1640,$2D0), children.
                x = START_X;
                y = START_Y;
                spawnChild(() -> new HpzRobotnikShipChildObjectInstance(
                        new ObjectSpawn(x, y - 0x1C, 0, 0, 0, false, 0), this,
                        HpzRobotnikShipChildObjectInstance.KIND_HEAD));
                spawnChild(() -> new HpzShipCraneObjectInstance(
                        new ObjectSpawn(x, y + 0x23, 0, 0, 0, false, 0), this));
                phase = PHASE_WAIT_CRANE;
                waitCrane();
            }
            case PHASE_WAIT_CRANE -> waitCrane();
            case PHASE_DROP -> {
                moveSprite2();
                timer = (short) (timer - 1);
                if (timer < 0) {
                    // loc_64F04
                    phase = PHASE_CARRY;
                    xVel = 0;
                    swingSetup1();
                    spawnFlame();
                }
            }
            case PHASE_CARRY -> updateCarry();
            case PHASE_WAIT_SPARKS -> {
                if ((hpz.knucklesCutsceneFlags() & 0xC) == 0xC) {
                    phase = PHASE_WAIT_LANDED;
                    hpz.setKnucklesCutsceneFlag(FLAG_ZAP);
                }
                swingUpAndDown();
                moveSprite2();
                sub66372();
                touchListed = true;
            }
            case PHASE_WAIT_LANDED -> {
                swingUpAndDown();
                moveSprite2();
                if (hpz.knucklesCutsceneFlag(FLAG_KNUCKLES_LANDED)) {
                    phase = PHASE_LEAVE;
                    xVel = 0x190;
                    flags &= ~0x10;
                    spawnFlame();
                }
                sub66372();
                touchListed = true;
            }
            default -> {
                swingUpAndDown();
                moveSprite2();
                if (((services().camera().getX() + 0x200) & 0xFFFF) < x) {
                    loc65000(hpz);
                    return;
                }
                sub66372();
                touchListed = true;
            }
        }
        updateDynamicSpawn(x, y);
    }

    /** {@code loc_64EC4}. */
    private void waitCrane() {
        if ((flags & 4) == 0) {
            return;
        }
        phase = PHASE_DROP;
        xVel = 0x100;
        yVel = 0x180;
        timer = 0x40;
    }

    /** {@code loc_64F20}. */
    private void updateCarry() {
        swingUpAndDown();
        moveSprite2();
        if (!hitFlag) {
            x = (x + 2) & 0xFFFF;
        }
        if (x >= ZAP_X) {
            phase = PHASE_WAIT_SPARKS;
            flags |= 0x10;
            xVel = 0;
            collisionFlags = COLLISION_FLAGS;
            collisionProperty = 0xFF;
            // ChildSpriteDat_66624: 2 x loc_6531E at (8,-4).
            for (int i = 0; i < 2; i++) {
                int subtype = i * 2;
                spawnChild(() -> new HpzShipSparkEmitterObjectInstance(
                        new ObjectSpawn(x + 8, y - 4, 0, subtype, 0, false, 0), this));
            }
        }
        sub66348();
    }

    /** {@code loc_65000}. */
    private void loc65000(HpzZoneRuntimeState hpz) {
        flags |= 0x30;
        HPZMasterEmeraldObjectInstance emerald = masterEmerald(services());
        if (emerald != null) {
            ObjectLifetimeOps.deleteNoRespawn(emerald);
        }
        hpz.setPaletteCycleCounter1(0);
        // Queue_Kos_Module ArtKosM_Teleporter restores the tiles PLC_KnuxHPZCutsceneShip used;
        // the standalone teleporter sheet never shared them.
        ObjectLifetimeOps.expireDynamic(this);
    }

    private void spawnFlame() {
        spawnChild(() -> new HpzRobotnikShipChildObjectInstance(
                new ObjectSpawn(x - 0x1E, y, 0, 0, 0, false, 0), this,
                HpzRobotnikShipChildObjectInstance.KIND_FLAME));
    }

    /** {@code sub_66348}: head flash after Knuckles' bounce. */
    private void sub66348() {
        if (!hitFlag) {
            return;
        }
        if (flashTimer == 0) {
            flashTimer = 0x30;
            services().playSfx(Sonic3kSfx.BOSS_HIT.id);
        }
        flashTimer--;
        if (flashTimer == 0) {
            hitFlag = false;
        }
    }

    /** {@code sub_66372}. */
    private void sub66372() {
        if (collisionFlags != 0) {
            return;
        }
        if (flashTimer == 0) {
            flashTimer = 0x20;
            services().playSfx(Sonic3kSfx.BOSS_HIT.id);
            hitFlag = true;
        }
        // FixBugs=0: the even pass adds 2*2, selecting colours 2-4 of word_7A628 ($222,$888,$CCC)
        // instead of the second row the fixed 2*3 would select ($888,$CCC,$EEE).
        int index = (flashTimer & 1) != 0 ? 0 : FIXBUGS0_EVEN_FLASH_INDEX;
        writeFlash(FLASH[index], FLASH[index + 1], FLASH[index + 2]);
        flashTimer--;
        if (flashTimer == 0) {
            hitFlag = false;
            collisionFlags = savedCollisionFlags;
        }
    }

    private void writeFlash(int colour7, int colourE, int colourF) {
        var registry = services().paletteOwnershipRegistryOrNull();
        var level = services().currentLevel();
        var graphics = services().graphicsManager();
        S3kPaletteWriteSupport.applyContiguousPatch(registry, level, graphics, FLASH_OWNER,
                S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, 0, 7,
                new byte[]{(byte) (colour7 >> 8), (byte) colour7});
        S3kPaletteWriteSupport.applyContiguousPatch(registry, level, graphics, FLASH_OWNER + ".ef",
                S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, 0, 0xE,
                new byte[]{(byte) (colourE >> 8), (byte) colourE,
                        (byte) (colourF >> 8), (byte) colourF});
    }

    /** {@code Swing_Setup1}. */
    private void swingSetup1() {
        yVel = SWING_MAX;
        flags &= ~1;
    }

    /** {@code Swing_UpAndDown} with {@code $3E = $C0} and {@code $40 = $10}. */
    private void swingUpAndDown() {
        int d0 = SWING_ACCEL;
        int d1 = yVel;
        int d2 = SWING_MAX;
        if ((flags & 1) == 0) {
            d0 = -d0;
            d1 = (short) (d1 + d0);
            d2 = -d2;
            if (d1 > d2) {
                yVel = d1;
                return;
            }
            flags |= 1;
            d0 = -d0;
            d2 = -d2;
        }
        d1 = (short) (d1 + d0);
        if (d1 >= d2) {
            flags &= ~1;
            d0 = -d0;
            d1 = (short) (d1 + d0);
        }
        yVel = d1;
    }

    private void moveSprite2() {
        int nextX = ((x << 8) | xSub) + (short) xVel;
        int nextY = ((y << 8) | ySub) + (short) yVel;
        x = (nextX >> 8) & 0xFFFF;
        xSub = nextX & 0xFF;
        y = (nextY >> 8) & 0xFFFF;
        ySub = nextY & 0xFF;
    }

    /** {@code bset #6,status(a1)} from Knuckles' {@code loc_6471A}. */
    void markHitByKnuckles() {
        hitFlag = true;
    }

    /** {@code $38(a0)} bit 2, written by the crane's {@code loc_650D4}. */
    void markCraneLifted() {
        flags |= 4;
    }

    boolean parentFlag(int bit) {
        return (flags & (1 << bit)) != 0;
    }

    boolean hitFlag() {
        return hitFlag;
    }

    int xVel() {
        return xVel;
    }

    int yVel() {
        return yVel;
    }

    int phaseForTest() {
        return phase;
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() { return 0; }
    @Override public int getOnScreenHalfWidth() { return 0x1C; }
    @Override public int getOnScreenHalfHeight() { return 0x20; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public int getCollisionFlags() {
        return touchListed ? collisionFlags : 0;
    }

    @Override
    public int getCollisionProperty() {
        return collisionProperty;
    }

    @Override
    public boolean usesCurrentTouchResponseState() {
        return true;
    }

    /** {@code Touch_Enemy .checkhurtenemy} with a non-zero {@code boss_hitcount2}. */
    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        savedCollisionFlags = collisionFlags;
        collisionFlags = 0;
        collisionProperty = (collisionProperty - 1) & 0xFF;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(MAPPING_FRAME, x, y, true, false);
        }
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(new int[]{
                phase, x, y, xSub, ySub, xVel, yVel, timer, flags, hitFlag ? 1 : 0, flashTimer,
                collisionFlags, savedCollisionFlags, collisionProperty, touchListed ? 1 : 0}));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra e) {
            int[] s = e.state();
            phase = s[0];
            x = s[1];
            y = s[2];
            xSub = s[3];
            ySub = s[4];
            xVel = s[5];
            yVel = s[6];
            timer = s[7];
            flags = s[8];
            hitFlag = s[9] != 0;
            flashTimer = s[10];
            collisionFlags = s[11];
            savedCollisionFlags = s[12];
            collisionProperty = s[13];
            touchListed = s[14] != 0;
        }
    }
}
