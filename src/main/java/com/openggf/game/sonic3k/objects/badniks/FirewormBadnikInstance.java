package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomObjectCodePointerProvider;

import java.util.List;

/**
 * ROM object {@code Obj_Fireworm} -- object id {@code $99} in the {@code SKL} pointer set
 * (sonic3k.asm:196192-196232, ROM {@code $8F760}). The {@code S3KL} set spends the same id on
 * {@code Obj_HCZMiniboss}. Lava Reef places 20 in act 1 and 9 in act 2.
 *
 * <p>This placement object is a spawner and nothing else. {@code Obj_WaitOffscreen} heads its
 * routine, its init ({@code loc_8F770}, :196206-196208) runs {@code SetUp_ObjAttributes} from
 * {@code ObjDat3_8F9DE} -- {@code Map_FirewormSegments}, {@code priority $280}, a
 * {@code $C x $C} box and {@code collision_flags 0}, so it can neither be hit nor hurt -- and
 * routine 2 ({@code loc_8F77A}, :196211-196215) waits for {@code Find_SonicTails} to report a
 * horizontal distance under {@code $80}. It then creates the head through
 * {@code ChildObjDat_8FA0E} at {@code (0,-8)}, copies its own subtype onto it, and settles into
 * routine 4, which is a bare {@code rts} ({@code locret_8F7A2}, :196230).
 *
 * <p>There is no {@code Draw_Sprite} anywhere in {@code Obj_Fireworm}, so the spawner is never
 * drawn; every visible part of the worm belongs to {@link FirewormHeadInstance} and its children.
 * The tail is {@code Sprite_CheckDelete}, the ordinary dynamic out-of-range check, and the head
 * carries its own, so the two can expire independently.
 */
public final class FirewormBadnikInstance extends AbstractObjectInstance
        implements RewindRecreatable, RomObjectCodePointerProvider {

    /** {@code cmpi.w #$80,d2} on {@code Find_SonicTails}'s nearer-player X distance. */
    private static final int SPAWN_DISTANCE = 0x80;
    /** {@code ObjDat3_8F9DE}: {@code dc.b $C,$C,0,0}. */
    private static final int HALF_SIZE = 0x0C;

    /** ROM {@code render_flags(a0)} bit 7 as {@code Obj_WaitOffscreen} reads it. */
    private boolean awake;
    /** ROM {@code routine(a0)} 4: the head has been created and this slot is finished. */
    private boolean spawned;

    public FirewormBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Fireworm");
    }

    /**
     * {@code Obj_Fireworm} is installed from the SKL object pointer table at ROM
     * {@code $0008F760} (sonic3k.lst); its whole code block lies in one bank, so the high word
     * {@code sub_13EFC} latches into {@code Tails_CPU_interact} is {@code $0008}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0008;
    }

    @Override
    public FirewormBadnikInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new FirewormBadnikInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (spawned) {
            return;
        }
        // Obj_WaitOffscreen (sonic3k.asm:180271-180302) is a one-shot latch: once the placeholder
        // has been on screen the real routine runs for the rest of the slot's life.
        if (!awake) {
            awake = isOnScreen();
            return;
        }
        // loc_8F77A: Find_SonicTails leaves d2 = |x distance| to the NEARER of the two players.
        if (nearestHorizontalDistance(playerEntity) >= SPAWN_DISTANCE) {
            return;
        }
        int x = (getCentreX() + FirewormHeadInstance.CHILD_DX) & 0xFFFF;
        int y = (getCentreY() + FirewormHeadInstance.CHILD_DY) & 0xFFFF;
        ObjectSpawn headSpawn = new ObjectSpawn(x, y, getSpawn().objectId(),
                getSpawn().subtype(), getSpawn().renderFlags(), false, 0);
        spawnChild(() -> new FirewormHeadInstance(headSpawn));
        spawned = true;
    }

    private int nearestHorizontalDistance(PlayableEntity playerEntity) {
        int best = Integer.MAX_VALUE;
        if (playerEntity != null) {
            best = Math.abs((short) (getCentreX() - playerEntity.getCentreX()));
        }
        try {
            PlayableEntity p2 = services().playerQuery().nativeP2OrNull();
            if (p2 != null) {
                best = Math.min(best, Math.abs((short) (getCentreX() - p2.getCentreX())));
            }
        } catch (Exception e) {
            // Headless unit contexts have no second player.
        }
        return best;
    }

    /** ROM {@code render_flags(a0)} bit 7. */
    public boolean awake() {
        return awake;
    }

    /** ROM {@code routine(a0)} 4. */
    public boolean spawned() {
        return spawned;
    }

    public int getCentreX() {
        return getSpawn().x() & 0xFFFF;
    }

    public int getCentreY() {
        return getSpawn().y() & 0xFFFF;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return HALF_SIZE;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HALF_SIZE;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Obj_Fireworm never reaches Draw_Sprite.
    }
}
