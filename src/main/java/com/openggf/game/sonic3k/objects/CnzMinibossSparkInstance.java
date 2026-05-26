package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchActorContextPolicy;
import com.openggf.level.objects.TouchAttackBouncePolicy;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchOverlapStopPolicy;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchShieldDeflectCapability;

import java.util.List;

/**
 * CNZ miniboss open-coil spark child.
 *
 * <p>Created by Obj_CNZMinibossOpenGo via Child1_CNZCoilOpenSparks
 * (sonic3k.asm:144950-144951,145672-145692). The three children are
 * hurt-category touch objects while the boss remains open.
 */
public final class CnzMinibossSparkInstance extends AbstractObjectInstance
        implements TouchResponseProvider {
    private static final int COLLISION_FLAGS = 0x92;
    private static final int SHIELD_REACTION_LIGHTNING_IMMUNITY = 1 << 5;
    private static final TouchResponseProfile TOUCH_RESPONSE_PROFILE = new TouchResponseProfile(
            TouchCategoryDecodeMode.NORMAL,
            false,
            false,
            false,
            TouchShieldDeflectCapability.NONE,
            SHIELD_REACTION_LIGHTNING_IMMUNITY,
            TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
            TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
            TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);

    private CnzMinibossInstance boss;
    private int parentOffsetX;
    private int parentOffsetY;

    public CnzMinibossSparkInstance(ObjectSpawn spawn) {
        super(spawn, "CNZMinibossSpark");
    }

    public void attachBossForTest(CnzMinibossInstance boss) {
        this.boss = boss;
        if (boss != null) {
            parentOffsetX = getX() - boss.getCentreX();
            parentOffsetY = getY() - boss.getCentreY();
        }
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (boss != null && (boss.isDefeatedForChild() || !boss.isOpenForTopHit())) {
            setDestroyed(true);
            return;
        }
        refreshChildPosition();
    }

    private void refreshChildPosition() {
        if (boss == null) {
            return;
        }
        // ROM Obj_CNZMinibossSparksMain refreshes parent-relative position
        // before Animate_RawMultiDelay and Child_DrawTouch_Sprite.
        updateDynamicSpawn(boss.getCentreX() + parentOffsetX, boss.getCentreY() + parentOffsetY);
    }

    @Override
    public int getCollisionFlags() {
        // ObjDat3_CNZMinibossSpark collision byte is $92 (sonic3k.asm:145660-145663).
        return isDestroyed() ? 0 : COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile() {
        return TOUCH_RESPONSE_PROFILE;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // CNZ miniboss spark art rendering is outside the headless touch slice.
    }

    @Override
    public String traceDebugDetails() {
        return String.format("sub=%02X off=%04X,%04X col=%02X",
                getSpawn().subtype(), parentOffsetX & 0xFFFF, parentOffsetY & 0xFFFF,
                getCollisionFlags());
    }
}
