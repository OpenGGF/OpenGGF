package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * SKL {@code $60}, {@code Obj_DEZBumperWall} (sonic3k.asm:95958-96082). Ten act 1 placements,
 * all of them in the {@code $5F} turbine room; the S3KL table has no object at this number.
 *
 * <p><b>One object, three shapes, chosen by the subtype's sign.</b> The header writes
 * {@code $C} x {@code $20} and then immediately overwrites it (:95962-95970):
 *
 * <ul>
 *   <li><b>Subtype 0</b> ({@code loc_497C2}, :95983-95988) is the full-height wall:
 *       {@code SolidObjectFull2} with {@code d1 = $17}, {@code d2 = $20}, {@code d3 = $21}.
 *       Records 311 and 312, at {@code $2600} y {@code $07C0} and {@code $08C0}.</li>
 *   <li><b>A positive subtype</b> ({@code loc_49804}, :96013-96021) is a narrow post:
 *       {@code width_pixels = 8}, {@code height_pixels = subtype}, and the solid takes
 *       {@code d1 = $13} with {@code d2 = height} and {@code d3 = height + 1}. Records 305/306
 *       and 329/330 are {@code $18}; 327/328 are {@code $38}.</li>
 *   <li><b>A negative subtype</b> ({@code loc_497B4}, :95976-95981) is the room's exit gate. It
 *       is the full-height wall <em>plus</em> one test run before the solid every update:
 *       {@code cmpi.b #$3F,(MHZ_pollen_counter).w / bne / move.w #$7F00,x_pos(a0)}. When all six
 *       bits of the byte the {@code $61} gravity puzzle writes are set, the wall moves itself to
 *       {@code $7F00} and stops being in the room at all. Records 331 and 332, at {@code $280C}
 *       y {@code $0820} and {@code $0860} — the two that sit past the puzzle.</li>
 * </ul>
 *
 * <p><b>The push launch is the puzzle's launch.</b> {@code sub_49848} (:96045-96047) plays
 * {@code sfx_Bumper} and falls into {@code loc_49850}, which
 * {@link S3kDezGravityPuzzleObjectInstance} already models: {@code x_vel = ±$C00} away from the
 * wall, airborne, {@code ground_vel = 1} negated for a left-facing player, and the endless
 * tumble ({@code flip_angle} 1 only when it was zero, {@code flips_remaining = -1},
 * {@code flip_speed = 4}, {@code anim = 0}). Only the returned side-contact bits reach it
 * ({@code swap d6 / andi.w #1|2,d6}, :95989-95990), and Player 1 and Player 2 are tested
 * separately.
 *
 * <p>The positive-subtype posts end on {@code Delete_Sprite_If_Not_In_Range} (:96042) and the
 * other two on {@code Sprite_OnScreen_Test} (:96000); both are the engine's ordinary
 * off-screen handling.
 */
public final class S3kDezBumperWallObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SpawnRewindRecreatable {

    /** {@code move.w #$17,d1 / #$20,d2 / #$21,d3} (:95983-95985). */
    private static final int WALL_HALF_WIDTH = 0x17;
    private static final int WALL_AIR_HALF_HEIGHT = 0x20;
    private static final int WALL_GROUND_HALF_HEIGHT = 0x21;
    /** {@code move.w #$13,d1} (:96013). */
    private static final int POST_HALF_WIDTH = 0x13;

    /** {@code move.w #$280,priority(a0)} (:95963). */
    private static final int PRIORITY_WORD = 0x280;

    /** {@code move.w #$C00,x_vel(a1)} (:96051). */
    private static final int LAUNCH_X_VEL = 0xC00;

    /** {@code cmpi.b #$3F,(MHZ_pollen_counter).w} (:95978): all six puzzle panels. */
    private static final int ALL_PANELS = 0x3F;
    /** {@code move.w #$7F00,x_pos(a0)} (:95980). */
    private static final int PARKED_X = 0x7F00;

    public S3kDezBumperWallObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZBumperWall");
    }

    /** {@code tst.b d0 / beq / bmi} on the subtype byte (:95964-95966). */
    private boolean isPost() {
        return (byte) spawn.subtype() > 0;
    }

    private boolean isGate() {
        return (byte) spawn.subtype() < 0;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (!isGate()) {
            return;
        }
        // loc_497B4 :95977-95981. The test runs before the solid every update, so the gate
        // stops being solid on the same update the sixth panel is pressed.
        boolean open = panelBits() == ALL_PANELS;
        updateDynamicSpawn(open ? PARKED_X : spawn.x() & 0xFFFF, getY());
    }

    @Override
    public SolidObjectParams getSolidParams() {
        if (isPost()) {
            // moveq #0,d2 / move.b height_pixels(a0),d2 / move.w d2,d3 / addq.w #1,d3
            // (:96014-96017) with height_pixels taken from the subtype (:95968).
            int height = spawn.subtype() & 0xFF;
            return SolidObjectParams.of(POST_HALF_WIDTH, height, height + 1);
        }
        return SolidObjectParams.of(WALL_HALF_WIDTH, WALL_AIR_HALF_HEIGHT, WALL_GROUND_HALF_HEIGHT);
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        // A parked gate is at $7F00 and out of every player's reach; saying so here as well
        // keeps the contact pass from having to reach that far to find out.
        return !(isGate() && panelBits() == ALL_PANELS);
    }

    @Override
    public boolean allowsObjectControlledSolidContacts() {
        // SolidObjectFull2 -> loc_1DFFE rejects only negative object_control.
        // The turbine room writes positive $01 and must still hit this solid.
        return true;
    }

    @Override
    public boolean rejectsBit7ObjectControlNewSolidContact(PlayableEntity player) {
        return true;
    }

    /** Returned d6 side bits also cover airborne contact (loc_1E094). */
    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (player == null || !contact.touchSide()) {
            return;
        }
        AbstractPlayableSprite sprite = player instanceof AbstractPlayableSprite s ? s : null;
        if (sprite == null) {
            return;
        }
        ObjectServices objectServices = tryServices();
        if (objectServices != null) {
            // sub_49848 (:96046-96047): the sound is the bumper's, not the puzzle's.
            objectServices.playSfx(Sonic3kSfx.BUMPER.id);
        }
        launch(sprite);
    }

    /**
     * {@code loc_49850} :96049-96080, shared verbatim with {@code Obj_DEZGravityPuzzle}'s
     * {@code sub_49A02} tail.
     */
    private void launch(AbstractPlayableSprite player) {
        boolean toTheLeft = (player.getCentreX() & 0xFFFF) < (getX() & 0xFFFF);
        player.setDirection(toTheLeft ? Direction.LEFT : Direction.RIGHT);
        player.setXSpeed((short) (toTheLeft ? -LAUNCH_X_VEL : LAUNCH_X_VEL));
        player.setAir(true);
        player.setPushing(false);
        player.setDoubleJumpFlag(0);
        player.setRollingJump(false);
        player.setJumping(false);
        player.setGSpeed((short) (toTheLeft ? -1 : 1));
        // tst.b flip_angle(a1) / bne (:96065-96067): an already-tumbling player keeps its angle.
        if ((player.getFlipAngle() & 0xFF) == 0) {
            player.setFlipAngle(1);
        }
        player.setAnimationId(0);
        player.setFlipsRemaining(-1);
        player.setFlipSpeed(4);
    }

    /**
     * {@code (MHZ_pollen_counter).w} (:95978). Mushroom Hill's particle counter is the byte the
     * {@code $61} gravity puzzle writes its six panel bits into, which is why this class reads
     * it from {@link S3kDezZoneRuntimeState} rather than from anything near the MHZ spawner.
     */
    private int panelBits() {
        ObjectServices objectServices = tryServices();
        if (objectServices == null || objectServices.zoneRuntimeRegistry() == null) {
            return 0;
        }
        S3kDezZoneRuntimeState state =
                S3kRuntimeStates.currentDez(objectServices.zoneRuntimeRegistry()).orElse(null);
        return state == null ? 0 : state.panelBits();
    }

    /**
     * {@code Map_DEZBumperWall} (:96083, ROM {@code $000498C2}) through
     * {@code make_art_tile(ArtTile_DEZMisc2+$31,1,0)} (:95960): one frame, two 16x32 pieces
     * stacked, the same art block the {@code $61} puzzle draws from.
     */
    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isGate() && panelBits() == ALL_PANELS) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_BUMPER_WALL);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(0, getX(), getY(),
                (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(PRIORITY_WORD);
    }

    /** Test accessors. */
    public boolean isPostForTest() {
        return isPost();
    }

    public boolean isGateForTest() {
        return isGate();
    }

    public boolean isOpenForTest() {
        return isGate() && panelBits() == ALL_PANELS;
    }
}
