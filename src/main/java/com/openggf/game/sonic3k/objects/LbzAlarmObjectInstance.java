package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectVisualFacing;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.difficulty.LbzAlarmRecipe;
import com.openggf.game.sonic3k.objects.badniks.Flybot767BadnikInstance;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.TouchActorContextPolicy;
import com.openggf.level.objects.TouchAttackBouncePolicy;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchOverlapStopPolicy;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.TouchShieldDeflectCapability;

import java.util.List;

/**
 * ROM object: {@code Obj_LBZAlarm} ({@code sonic3k.asm:57074-57152}).
 *
 * <p>The alarm is an invisible S3K special-property touch object. Touching its
 * {@code collision_flags = $D7} box latches {@code collision_property}, starts
 * a {@code $81}-frame alarm window, plays {@code sfx_Alarm} every {@code $20}
 * frames while active, and spawns {@code Obj_Flybot767} from Player 1's
 * position. Subtype bit 0 delays the Flybot spawn until the timer reaches
 * {@code $41}; subtype bit 1 chooses the spawn side.
 */
public final class LbzAlarmObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener, RewindRecreatable, ObjectVisualFacing.Container {

    private static final int COLLISION_FLAGS = 0xD7;
    private static final int COLLISION_SIZE_INDEX = 0x17;
    private static final int ALARM_DURATION = 0x81;
    private static final int DELAYED_SPAWN_TIMER_VALUE = 0x41;
    private static final int SFX_PERIOD_MASK = 0x1F;
    private static final int FLYBOT_X_OFFSET = 0x00C0;
    private static final int FLYBOT_Y_OFFSET = -0x0060;

    private static final TouchResponseProfile TOUCH_RESPONSE_PROFILE = new TouchResponseProfile(
            TouchCategoryDecodeMode.S3K_SPECIAL_PROPERTY,
            true,
            true,
            false,
            TouchShieldDeflectCapability.NONE,
            0,
            TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
            TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
            TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);

    private int subtype;
    private int alarmTimer;
    private int collisionProperty;
    // Non-final so the rewind codec restores a creator preset recreated as STOCK.
    private LbzAlarmRecipe recipe = LbzAlarmRecipe.STOCK;
    /** BOUNDED only: this alarm has summoned its single escort and stays inert. */
    private boolean summoned;

    public LbzAlarmObjectInstance(ObjectSpawn spawn) {
        this(spawn, LbzAlarmRecipe.STOCK);
    }

    public LbzAlarmObjectInstance(ObjectSpawn spawn, LbzAlarmRecipe recipe) {
        super(spawn, "LBZAlarm");
        this.recipe = java.util.Objects.requireNonNull(recipe, "recipe");
        this.subtype = spawn.subtype();
    }

    @Override
    public LbzAlarmObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new LbzAlarmObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            return;
        }

        if (alarmTimer != 0) {
            alarmTimer--;
            if (alarmTimer == 0) {
                setAlarmAnimationActive(false);
                return;
            }
            if ((subtype & 0x01) != 0 && alarmTimer == DELAYED_SPAWN_TIMER_VALUE) {
                spawnFlybot(playerEntity);
            }
            if ((alarmTimer & SFX_PERIOD_MASK) == 0) {
                services().playSfx(Sonic3kSfx.ALARM.id);
            }
            return;
        }

        if (summoned) {
            // BOUNDED: a latch from an earlier overlap cannot re-arm a spent alarm.
            collisionProperty = 0;
            return;
        }
        if (collisionProperty == 0) {
            return;
        }
        if (recipe == LbzAlarmRecipe.BOUNDED && liveEscortExists()) {
            // BOUNDED: another alarm's escort is still alive. Check availability before
            // the cue, timer and animation latch so a blocked alarm stays silent and armed.
            collisionProperty = 0;
            return;
        }

        // ROM loc_2947E: clr.b collision_property(a0); move.w #$81,$30(a0).
        collisionProperty = 0;
        alarmTimer = ALARM_DURATION;
        setAlarmAnimationActive(true);
        if ((subtype & 0x01) == 0) {
            spawnFlybot(playerEntity);
        }
    }

    @Override
    public int getCollisionFlags() {
        // BOUNDED: after the single summon the alarm never re-arms, and while another
        // bounded escort is live it publishes no touch region at all: the alarm's
        // STOP_AFTER_FIRST_OVERLAP profile would otherwise end the player's touch
        // pass at this alarm and shadow later objects. STOCK is unchanged.
        if (alarmTimer != 0 || summoned) {
            return 0;
        }
        if (recipe == LbzAlarmRecipe.BOUNDED && liveEscortExists()) {
            return 0;
        }
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return collisionProperty;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile() {
        return TOUCH_RESPONSE_PROFILE;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TOUCH_RESPONSE_PROFILE;
    }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        if (alarmTimer == 0 && result.sizeIndex() == COLLISION_SIZE_INDEX) {
            collisionProperty = 1;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Invisible trigger in ROM: no mappings, art tile, or DisplaySprite call.
    }

    public int alarmTimerForTesting() {
        return alarmTimer;
    }

    private void spawnFlybot(PlayableEntity playerEntity) {
        if (recipe == LbzAlarmRecipe.BOUNDED) {
            // One attempt per allocated alarm instance, latched before allocation so a
            // slot failure is never retried. Unload/reload allocates a new instance
            // (new attempt); a destructive rewind restore preserves this latch.
            if (summoned) {
                return;
            }
            summoned = true;
        }
        if (playerEntity == null) {
            return;
        }

        int xOffset = (subtype & 0x02) == 0 ? FLYBOT_X_OFFSET : -FLYBOT_X_OFFSET;
        ObjectSpawn flybotSpawn = new ObjectSpawn(
                playerEntity.getCentreX() + xOffset,
                playerEntity.getCentreY() + FLYBOT_Y_OFFSET,
                Sonic3kObjectIds.FLYBOT_767,
                0,
                0,
                false,
                0);
        if (recipe == LbzAlarmRecipe.BOUNDED) {
            // A delayed-spawn alarm may find an escort that appeared during its siren
            // delay: the attempt is consumed without allocating (max one live escort).
            if (!liveEscortExists()) {
                spawnChild(() -> new Flybot767BadnikInstance(flybotSpawn).asBoundedEscort());
            }
            return;
        }
        spawnChild(() -> createFlybotChild(flybotSpawn));
    }

    private boolean liveEscortExists() {
        var services = tryServices();
        if (services == null || services.objectQuery() == null) {
            return false;
        }
        for (Flybot767BadnikInstance flybot
                : services.objectQuery().activeObjectsOfType(Flybot767BadnikInstance.class)) {
            if (!flybot.isDestroyed() && flybot.isBoundedEscort()) {
                return true;
            }
        }
        return false;
    }

    private AbstractObjectInstance createFlybotChild(ObjectSpawn flybotSpawn) {
        ObjectInstance child = new Sonic3kObjectRegistry().create(flybotSpawn);
        if (child instanceof AbstractObjectInstance object) {
            return object;
        }
        return new PlaceholderObjectInstance(flybotSpawn, "Flybot767");
    }

    private void setAlarmAnimationActive(boolean active) {
        var registry = services().zoneRuntimeRegistry();
        if (registry == null) {
            return;
        }
        LbzZoneRuntimeState state = S3kRuntimeStates.currentLbz(registry).orElse(null);
        if (state != null) {
            state.setAlarmAnimationActive(active);
        }
    }
}
