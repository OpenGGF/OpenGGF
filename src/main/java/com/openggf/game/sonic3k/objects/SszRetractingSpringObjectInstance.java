package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SlopedSolidProvider;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ROM {@code Obj_SSZRetractingSpring} ({@code $74}, sonic3k.asm:92306-92450): the horizontal
 * spring that only exists while a player is approaching it. Five act-1 placements, all subtype 0 —
 * the subtype is never read; the placement's X-flip bit decides which side it faces.
 *
 * <p>Init: {@code bset #2,render_flags}, {@code height_pixels $10}, {@code width_pixels $18},
 * {@code priority $180}, {@code make_art_tile(ArtTile_SSZMisc+$CE,0,0)} over
 * {@code Map_SSZRetractingSpring}.
 *
 * <p>The proximity test is gated on {@code btst #0,(Level_frame_counter+1).w} — bit 0 of the
 * counter's <em>low</em> byte, so it runs on every other frame and the extend and retract ramps
 * take twice as long as their frame counts suggest. The box {@code sub_46514} tests is {@code $60}
 * wide on the spring's facing side and runs from {@code y - $50} to {@code y + $10}; a player
 * under object control never counts. With anyone inside, {@code mapping_frame} climbs 0-1-2-3 and
 * plays {@code sfx_SpringLatch} on the very first step; with nobody inside it falls back towards
 * zero, and both players' state longwords are cleared on the way.
 *
 * <p>{@code loc_464C6}: a spring below mapping frame 2 parks its {@code x_pos} at {@code $7FFF}
 * for the solid call and restores it afterwards, so a half-extended spring is simply not there.
 * The solid itself is {@code sub_1DD0E} — {@code SolidObjSloped2} — with {@code d1 = $23},
 * {@code d2 = $10} over {@code byte_468DC}, one signed byte per two pixels, whose profile is flat
 * at {@code $11} for the first seventeen samples and then falls away to {@code -$C}.
 *
 * <p>{@code sub_46536} is a four-step launch: the contact frame pushes the player eight pixels
 * clear and shows frame 4; the next shows frame 3, writes {@code ground_vel} and {@code x_vel} of
 * {@code ±$C00} away from the spring's facing, flips the player to match and plays
 * {@code sfx_Spring}; the third shows frame 5 and arms an eight-frame timer; the fourth returns to
 * frame 3 and clears the state. A player who approaches from the wrong side is ignored outright.
 */
public final class SszRetractingSpringObjectInstance extends AbstractObjectInstance
        implements SlopedSolidProvider, SpawnRewindRecreatable {
    private static final Logger LOGGER =
            Logger.getLogger(SszRetractingSpringObjectInstance.class.getName());

    /** {@code move.w #$180,priority(a0)}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x180);
    /** {@code make_art_tile(ArtTile_SSZMisc+$CE,0,0)}. */
    private static final int PALETTE_LINE = 0;
    /** {@code moveq #$23,d1} / {@code moveq #$10,d2}. */
    private static final int SOLID_HALF_WIDTH = 0x23;
    private static final int SOLID_HALF_HEIGHT = 0x10;
    /** {@code byte_468DC}: 36 signed bytes, one per two pixels. */
    public static final int HEIGHT_TABLE_ADDR = Sonic3kConstants.SSZ_RETRACTING_SPRING_HEIGHT_TABLE_ADDR;
    public static final int HEIGHT_TABLE_BYTES = 36;
    /** {@code addi.w #$10,d2} / {@code subi.w #$60,d1}. */
    private static final int WINDOW_BELOW = 0x10;
    private static final int WINDOW_HEIGHT = 0x60;
    /** {@code subi.w #$60,d3} and its {@code addi.w #2*$60,d3}. */
    private static final int WINDOW_WIDTH = 0x60;
    /** {@code cmpi.b #3,d1}: frame 3 is fully extended. */
    private static final int EXTENDED_FRAME = 3;
    /** {@code cmpi.b #2,mapping_frame(a0)}: below this the spring is intangible. */
    private static final int SOLID_FROM_FRAME = 2;
    /** {@code move.w #$7FFF,x_pos(a0)}. */
    private static final int PARKED_X = 0x7FFF;
    /** {@code move.w #$C00,d1}. */
    public static final int LAUNCH_VEL = 0xC00;
    /** {@code moveq #8,d1} in {@code sub_46536}. */
    private static final int CLEARANCE_PUSH = 8;
    /** {@code move.b #8,1(a2)}. */
    private static final int RECOIL_FRAMES = 8;

    private final int x;
    private final int y;
    private int mappingFrame;
    /** {@code $2E}/{@code $30}: each player's launch step and, in the high byte, its timer. */
    private int p1Step;
    private int p1Timer;
    private int p2Step;
    private int p2Timer;
    private byte[] heightTable;

    public SszRetractingSpringObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZRetractingSpring");
        this.x = spawn.x();
        this.y = spawn.y();
    }

    @Override
    public SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.MANUAL_CHECKPOINT;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (tryServices() == null) {
            return;
        }
        // lea (Player_2).w,a1: the routine's second pass is the native Player 2 slot.
        PlayableEntity second = services().playerQuery().nativeP2OrNull();
        if ((levelFrameCounter(vIntRunCount) & 1) != 0) {
            advanceExtension(player, second);
        }
        // loc_464C6: the solid runs every frame, but only once the spring has reached frame 2.
        var batch = mappingFrame >= SOLID_FROM_FRAME ? checkpointAll() : null;
        servicePlayer(0, player, batch);
        servicePlayer(1, second, batch);
    }

    /** {@code loc_46452}-{@code loc_464C2}. */
    private void advanceExtension(PlayableEntity first, PlayableEntity second) {
        int nearby = (inWindow(first) ? 1 : 0) + (inWindow(second) ? 1 : 0);
        if (nearby == 0) {
            if (mappingFrame == 0) {
                return;
            }
            // clr.l $2E(a0): both players' launch state goes with the retraction.
            p1Step = 0;
            p1Timer = 0;
            p2Step = 0;
            p2Timer = 0;
            int next = mappingFrame - 1;
            // cmpi.b #3,d1 / blo.s: frames 4 and 5 fall back to 2 rather than to 3.
            mappingFrame = Integer.compareUnsigned(next & 0xFF, EXTENDED_FRAME) >= 0 ? 2 : next;
            return;
        }
        if (mappingFrame >= EXTENDED_FRAME) {
            return;
        }
        if (mappingFrame == 0) {
            services().playSfx(Sonic3kSfx.SPRING_LATCH.id);
        }
        mappingFrame++;
    }

    /** {@code sub_46514}: the proximity box, which sits on the spring's facing side. */
    private boolean inWindow(PlayableEntity entity) {
        if (!(entity instanceof AbstractPlayableSprite sprite) || sprite.isObjectControlled()) {
            return false;
        }
        int bottom = (y + WINDOW_BELOW) & 0xFFFF;
        int top = (bottom - WINDOW_HEIGHT) & 0xFFFF;
        int playerY = sprite.getCentreY() & 0xFFFF;
        if (Integer.compareUnsigned(top, playerY) > 0
                || Integer.compareUnsigned(bottom, playerY) < 0) {
            return false;
        }
        int left = isRenderFlipped() ? x : (x - WINDOW_WIDTH) & 0xFFFF;
        int right = isRenderFlipped() ? (x + WINDOW_WIDTH) & 0xFFFF : x;
        int playerX = sprite.getCentreX() & 0xFFFF;
        return Integer.compareUnsigned(left, playerX) <= 0
                && Integer.compareUnsigned(right, playerX) >= 0;
    }

    /** One {@code sub_46536} call. */
    private void servicePlayer(int slot, PlayableEntity entity,
            com.openggf.game.solid.SolidCheckpointBatch batch) {
        if (!(entity instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        int step = step(slot);
        if (step == 0) {
            if (batch == null) {
                return;
            }
            var contact = batch.perPlayer().get(entity);
            if (contact == null || !(contact.pushingNow() || contact.standingNow())) {
                return;
            }
            // The player has to be on the side the spring is not facing, or nothing happens.
            boolean playerToTheRight = ((sprite.getCentreX() & 0xFFFF) - x) >= 0;
            if (playerToTheRight != isRenderFlipped()) {
                return;
            }
            NativePositionOps.addXPosPreserveSubpixel(sprite,
                    playerToTheRight ? -CLEARANCE_PUSH : CLEARANCE_PUSH);
            mappingFrame = 4;
            setStep(slot, 1);
            return;
        }
        if (step == 1) {
            // d2 = (status(a0) & 1) ^ 1: the launch is away from the face the spring presents.
            boolean launchLeft = !isRenderFlipped();
            int velocity = launchLeft ? -LAUNCH_VEL : LAUNCH_VEL;
            sprite.setGSpeed((short) velocity);
            sprite.setXSpeed((short) velocity);
            sprite.setDirection(launchLeft ? Direction.LEFT : Direction.RIGHT);
            mappingFrame = 3;
            setStep(slot, 2);
            services().playSfx(Sonic3kSfx.SPRING.id);
            return;
        }
        if (step == 2) {
            mappingFrame = 5;
            setTimer(slot, RECOIL_FRAMES);
            setStep(slot, 3);
            return;
        }
        int timer = timer(slot) - 1;
        setTimer(slot, timer);
        if (timer != 0) {
            return;
        }
        mappingFrame = 3;
        setStep(slot, 0);
    }

    private int levelFrameCounter(int fallbackCounter) {
        return services().levelManager() != null
                ? services().levelManager().getFrameCounter()
                : fallbackCounter;
    }

    private int step(int slot) { return slot == 0 ? p1Step : p2Step; }

    private void setStep(int slot, int value) {
        if (slot == 0) {
            p1Step = value;
        } else {
            p2Step = value;
        }
    }

    private int timer(int slot) { return slot == 0 ? p1Timer : p2Timer; }

    private void setTimer(int slot, int value) {
        if (slot == 0) {
            p1Timer = value;
        } else {
            p2Timer = value;
        }
    }

    /**
     * {@code loc_464C6} keeps the real X in {@code $12(a0)}, writes {@code $7FFF} only for the
     * duration of the solid call and restores it immediately afterwards, so the object never
     * appears at {@code $7FFF} to anything else — including the placement window that would
     * otherwise despawn it. This class expresses the same thing by not calling the solid at all
     * below mapping frame 2.
     */
    @Override
    public int getX() {
        return x;
    }

    /** {@code move.w #$7FFF,x_pos(a0)}: what the solid routine alone sees while retracted. */
    public int solidXForTest() {
        return mappingFrame >= SOLID_FROM_FRAME ? x : PARKED_X;
    }

    @Override public int getY() { return y; }

    @Override
    public SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT, SOLID_HALF_HEIGHT);
    }

    @Override public boolean isTopSolidOnly() { return true; }
    @Override public boolean isSlopeFlipped() { return isRenderFlipped(); }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 0x18; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }

    @Override
    public byte[] getSlopeData() {
        if (heightTable == null) {
            try {
                var rom = services().rom();
                heightTable = rom == null ? new byte[0]
                        : rom.readBytes(HEIGHT_TABLE_ADDR, HEIGHT_TABLE_BYTES);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "byte_468DC unavailable", e);
                heightTable = new byte[0];
            }
        }
        return heightTable;
    }

    public int mappingFrameForTest() { return mappingFrame; }
    public int stepForTest(int slot) { return step(slot); }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_RETRACTING_SPRING);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, x, y, isRenderFlipped(), false, PALETTE_LINE);
        }
    }

    /** {@code btst #0,status(a0)}: the placement's X-flip bit. */
    private boolean isRenderFlipped() {
        return (getSpawn().renderFlags() & 1) != 0;
    }

}
