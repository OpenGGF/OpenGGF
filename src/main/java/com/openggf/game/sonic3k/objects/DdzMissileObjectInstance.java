package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ROM {@code Obj_DDZMissile} ($B8, sonic3k.asm:174131-174250) and its hit logic {@code sub_82B06}.
 *
 * <p><b>Subtype 0</b> (placed): {@code $3C = $C0} (angle), frame {@code $E}. Once
 * {@code Render_Sprites} has shown it, {@code loc_82038} plays {@code sfx_Dash}; from then on it
 * moves left two pixels a frame, follows the wrap offset, runs {@code sub_82B06} and
 * {@code Sprite_CheckDelete}.
 *
 * <p><b>Subtype 1</b> (boss launcher {@code loc_81F94}, index {@code $39} 0-2): rides the launcher
 * for {@code index * 16} frames with a {@code word_82032} Y offset, drifts right at {@code $200} for
 * {@code $C} frames tracking the launcher's Y, then {@code loc_820D0} flies straight for {@code $20}
 * frames, turns by {@code byte_8211E} for {@code $20} frames and finally homes on Player 1 with
 * {@code sub_82A82}, always at speed {@code 2 * sin/cos}.
 *
 * <p>{@code sub_82B06}: while Player 1 is powered, an on-screen launcher missile inside the end boss
 * body's {@code word_82BB4} box while the body is not flashing damages the body; otherwise a missile
 * whose {@code off_82BBC} box (by direction {@code $3D}) contains an unhurt Player 1 sets
 * {@code invulnerability_timer = 89}. Either hit tells the launcher one missile is spent, becomes
 * {@code Wait_Draw} for four frames and creates an {@code Obj_CreateBossExplosion} subtype 6. A
 * launcher missile whose launcher was destroyed explodes the same way.
 */
public final class DdzMissileObjectInstance extends AbstractDdzObjectInstance {
    /** Launcher interface the boss's {@code loc_81F94} provides. */
    public interface Launcher {
        int launcherX();
        int launcherY();
        void missileSpent();
    }

    /** {@code word_82032}. */
    private static final int[] LAUNCH_Y_OFFSETS = {0, -8, 8};
    /** {@code byte_8211E}. */
    private static final int[] TURN_RATES = {4, 2, -2};
    /** {@code off_82BBC} via {@code word_82BCC}, {@code word_82BD4}, {@code word_82BDC}. */
    private static final int[][] PLAYER_BOXES = {
            {-0x18, 0x30, -0x24, 0x48}, {-0x1C, 0x38, -0x1C, 0x38}, {-0x24, 0x48, -0x18, 0x30},
            {-0x1C, 0x38, -0x1C, 0x38}, {-0x18, 0x30, -0x24, 0x48}, {-0x1C, 0x38, -0x1C, 0x38},
            {-0x24, 0x48, -0x18, 0x30}, {-0x1C, 0x38, -0x1C, 0x38}};
    private static final int MISSILE_PALETTE = 2;

    static final int STATE_PLACED_WAIT = 0;
    static final int STATE_PLACED_FLY = 1;
    static final int STATE_RIDE_LAUNCHER = 2;
    static final int STATE_DRIFT = 3;
    static final int STATE_STRAIGHT = 4;
    static final int STATE_TURN = 5;
    static final int STATE_HOME = 6;
    static final int STATE_EXPLODING = 7;

    private int subtype;
    private int index;
    private int xPos;
    private int yPos;
    private short xVel;
    private short yVel;
    private int state;
    /** {@code $3C} angle and {@code $3D} direction (0-7). */
    private int angle;
    private int direction;
    private int timer;
    private int turnRate;
    private boolean highPriority;
    private int priorityWord = 0x180;
    private boolean initialized;


    public DdzMissileObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DDZMissile", null);
        subtype = spawn.subtype() & 0xFF;
        xPos = (spawn.x() & 0xFFFF) << 16;
        yPos = (spawn.y() & 0xFFFF) << 16;
    }

    /** {@code loc_81FB0}: a launcher missile at the launcher with index {@code $39}. */
    <T extends AbstractObjectInstance & Launcher> DdzMissileObjectInstance(int x, int y, int index, T launcher) {
        this(new ObjectSpawn(x & 0xFFFF, y & 0xFFFF, 0, 1, 0, false, 0));
        this.index = index;
        this.parent = launcher;
    }

    /** {@code parent3}: the {@code loc_81F94} launcher of a subtype-1 missile. */
    private Launcher launcher() {
        return parent instanceof Launcher launcher ? launcher : null;
    }

    @Override
    public DdzMissileObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzMissileObjectInstance(ctx.spawn());
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

    /** {@code $3C(a2)} as the exhaust puff reads it through {@code parent3}. */
    int angleForExhaust() {
        return angle;
    }

    /** {@code status} bit 7: set by {@code loc_82B84} when the missile explodes. */
    boolean exploding() {
        return state == STATE_EXPLODING || goingAway();
    }

    @Override
    protected void updateObject(int vIntRunCount, PlayableEntity playerEntity) {
        if (!initialized) {
            initialize();
            return;
        }
        switch (state) {
            case STATE_PLACED_WAIT -> {
                // loc_82038: tst.b render_flags(a0) / bpl.s
                if (isWithinSolidContactBounds()) {
                    state = STATE_PLACED_FLY;
                    services().playSfx(Sonic3kSfx.DASH.id);
                }
                flyPlaced();
            }
            case STATE_PLACED_FLY -> flyPlaced();
            case STATE_RIDE_LAUNCHER -> rideLauncher();
            case STATE_DRIFT -> drift();
            case STATE_STRAIGHT, STATE_TURN, STATE_HOME -> flyLaunched(vIntRunCount);
            case STATE_EXPLODING -> {
                // Wait_Draw: Obj_Wait, then loc_85088 (Sprite_CheckDelete's delete tail).
                timer = (short) (timer - 1);
                if (timer < 0) {
                    goDeleteClearingRespawn();
                }
            }
            default -> throw new IllegalStateException("DDZ missile state " + state);
        }
    }

    /** {@code Obj_DDZMissile} init. */
    private void initialize() {
        initialized = true;
        spawnChild(() -> new DdzMissileExhaustObjectInstance(this));
        if (subtype == 0) {
            state = STATE_PLACED_WAIT;
            // move.w #-$3FFA,$3C(a0): angle $C0, $3D = 6 (rewritten by sub_82AA6).
            angle = 0xC0;
            updateDirection();
            return;
        }
        // loc_82014
        state = STATE_RIDE_LAUNCHER;
        yPos += LAUNCH_Y_OFFSETS[index] << 16;
        // add.w d0,d0 (word index) then lsl.w #3: sixteen frames per index.
        timer = index << 4;
        launcherGoneCheck();
    }

    /** {@code loc_8204C}. */
    private void flyPlaced() {
        xPos -= DdzObjectSupport.wrapOffset(services()) << 16;
        xPos -= 2 << 16;
        if (checkHits()) {
            return;
        }
        // Sprite_CheckDelete
        if (outOfRangeX(getX())) {
            goDeleteClearingRespawn();
        }
    }

    /** {@code loc_82060}. */
    private void rideLauncher() {
        Launcher launcher = launcher();
        if (launcher != null) {
            xPos = (launcher.launcherX() & 0xFFFF) << 16 | (xPos & 0xFFFF);
            yPos = (launcher.launcherY() & 0xFFFF) << 16 | (yPos & 0xFFFF);
        }
        timer = (short) (timer - 1);
        if (timer < 0) {
            state = STATE_DRIFT;
            angle = 0x40;
            updateDirection();
            xVel = 0x200;
            timer = 0xB;
        }
        launcherGoneCheck();
    }

    /** {@code loc_8209E}: Obj_Wait callback {@code loc_820B6} starts the straight flight. */
    private void drift() {
        Launcher launcher = launcher();
        if (launcher != null) {
            yPos = (launcher.launcherY() & 0xFFFF) << 16 | (yPos & 0xFFFF);
        }
        xPos += xVel << 8;
        yPos += yVel << 8;
        timer = (short) (timer - 1);
        if (timer < 0) {
            state = STATE_STRAIGHT;
            timer = 0x1F;
        }
        // Child_Draw_Sprite
        if (subtype != 0 && parentGone()) {
            goDelete();
        }
    }

    /** {@code loc_820D0}. */
    private void flyLaunched(int vIntRunCount) {
        switch (state) {
            case STATE_STRAIGHT -> {
                // loc_820EE: MoveSprite2, Obj_Wait -> loc_820F8
                xPos += xVel << 8;
                yPos += yVel << 8;
                timer = (short) (timer - 1);
                if (timer < 0) {
                    state = STATE_TURN;
                    timer = 0x1F;
                    highPriority = true;
                    priorityWord = 0x180;
                    turnRate = TURN_RATES[index];
                }
            }
            case STATE_TURN -> {
                timer = (short) (timer - 1);
                if (timer < 0) {
                    state = STATE_HOME;
                }
                angle = (angle + turnRate) & 0xFF;
                updateDirection();
                moveAtAngle();
            }
            default -> {
                homeOnPlayer(vIntRunCount);
                updateDirection();
                moveAtAngle();
            }
        }
        checkHits();
    }

    /** {@code sub_82A82}: turn two units towards Player 1 on three of every four V-ints. */
    private void homeOnPlayer(int vIntRunCount) {
        if ((vIntRunCount & 3) == 0) {
            return;
        }
        AbstractPlayableSprite player = DdzObjectSupport.player(services());
        if (player == null) {
            return;
        }
        int target = DdzObjectSupport.angleTowards(getX(), getY(), player.getCentreX(), player.getCentreY());
        int step = (byte) (target - angle) >= 0 ? 2 : -2;
        angle = (angle + step) & 0xFF;
    }

    /** {@code loc_82ABC}: {@code x_vel = 2 sin}, {@code y_vel = 2 cos}, then {@code MoveSprite2}. */
    private void moveAtAngle() {
        xVel = (short) (TrigLookupTable.sinHex(angle) * 2);
        yVel = (short) (TrigLookupTable.cosHex(angle) * 2);
        xPos += xVel << 8;
        yPos += yVel << 8;
    }

    /** {@code sub_82AA6}. */
    private void updateDirection() {
        direction = ((angle + 0x10) & 0xFF) >> 5;
    }

    /** {@code loc_81FDA}: a launcher missile explodes with its launcher. */
    private void launcherGoneCheck() {
        if (subtype != 0 && parentGone()) {
            goDelete();
        }
    }

    /** {@code sub_82B06}. Returns {@code true} when the missile started exploding. */
    private boolean checkHits() {
        if (!DdzObjectSupport.playerPowered(services())) {
            return false;
        }
        if (subtype != 0 && isWithinSolidContactBounds()) {
            DdzEndBossBodyObjectInstance body = DdzEndBossBodyObjectInstance.find(services());
            if (body != null && !body.flashing()
                    && DdzObjectSupport.inTheirRange(getX(), getY(), body.getX(), body.getY(),
                    DdzEndBossBodyObjectInstance.MISSILE_BOX)) {
                body.takeMissileHit();
                if (launcher() != null) {
                    launcher().missileSpent();
                }
                explode();
                return true;
            }
        }
        AbstractPlayableSprite player = DdzObjectSupport.player(services());
        if (player != null && player.getInvulnerableFrames() == 0
                && DdzObjectSupport.inMyRange(getX(), getY(), player.getCentreX(), player.getCentreY(),
                PLAYER_BOXES[direction])) {
            player.setInvulnerableFrames(90 - 1);
            if (launcher() != null) {
                launcher().missileSpent();
            }
            explode();
            return true;
        }
        if (subtype != 0 && parentGone()) {
            explode();
            return true;
        }
        return false;
    }

    /** {@code loc_82B84}. */
    private void explode() {
        state = STATE_EXPLODING;
        timer = 3;
        int x = getX();
        int y = getY();
        spawnChild(() -> new DdzCreateBossExplosionObjectInstance(x, y, 6, this));
    }

    @Override
    public int getOnScreenHalfWidth() {
        return 0x18;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return 0x18;
    }

    @Override
    public boolean isHighPriority() {
        return highPriority;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(priorityWord);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawable() || !initialized || state == STATE_RIDE_LAUNCHER) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DDZ_MISC);
        if (renderer != null) {
            renderer.drawFrameIndex(8 + direction, getX(), getY(), false, false, MISSILE_PALETTE);
        }
    }


}
