package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_81E3C} (sonic3k.asm:173930-173961): the phase-1 end boss body sprite, the target
 * {@code _unkFAA4} points at. It stays at boss {@code + ($C0, $4B)} ({@code sub_82C86}) with
 * {@code ObjDat3_831CC} (priority {@code $300}, frame {@code $38}) and seven hit points.
 *
 * <p>A launcher missile hit ({@code sub_82B06}) decrements {@code collision_property} and sets
 * {@code collision_flags = $FF}. {@code loc_82D18} then, while Player 1 is powered: on a negative
 * property awards 100 points, sets {@code status} bit 7 and becomes {@code loc_81E7A}
 * (delete once the boss's {@code $38} bit 4 is set); otherwise it runs a {@code $20}-frame flash
 * ({@code sfx_ThumpBoss} on the first frame, {@code sub_82D72} alternating the two
 * {@code word_82D9E} rows on palette line 3) and clears {@code collision_flags} at the end.
 */
public final class DdzEndBossBodyObjectInstance extends AbstractDdzObjectInstance {
    /** {@code word_82BB4}: launcher missile box around the body. */
    static final int[] MISSILE_BOX = {0, 0x38, -0x2C, 0x18};
    private static final int OFFSET_X = 0xC0;
    private static final int OFFSET_Y = 0x4B;
    private static final int PALETTE = 2;

    private int x;
    private int y;
    private int hitPoints = 7;
    private int collisionFlags;
    private int flashTimer;
    private boolean statusDestroyed;


    DdzEndBossBodyObjectInstance(DdzEndBossObjectInstance boss) {
        super(new ObjectSpawn(boss == null ? 0 : boss.getX(), boss == null ? 0 : boss.getY(), 0, 0, 0, false, 0),
                "DDZEndBossBody", boss);
        follow();
    }

    /** Rewind probe for {@code ObjectRewindDynamicCodecs}; mirrors {@link #recreateForRewind}. */
    private DdzEndBossBodyObjectInstance(ObjectSpawn spawn) {
        this((DdzEndBossObjectInstance) null);
    }

    @Override
    public DdzEndBossBodyObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzEndBossBodyObjectInstance((DdzEndBossObjectInstance) null);
    }

    static DdzEndBossBodyObjectInstance find(ObjectServices services) {
        var manager = services.objectManager();
        if (manager == null) {
            return null;
        }
        for (ObjectInstance object : manager.getActiveObjects()) {
            if (object instanceof DdzEndBossBodyObjectInstance body && !body.isDestroyed()) {
                return body;
            }
        }
        return null;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    /** {@code tst.b collision_flags(a1)}: a flashing body ignores launcher missiles. */
    boolean flashing() {
        return collisionFlags != 0;
    }

    boolean destroyedStatus() {
        return statusDestroyed;
    }

    int hitPoints() {
        return hitPoints;
    }

    /** {@code subq.b #1,collision_property(a1) / st collision_flags(a1)}. */
    void takeMissileHit() {
        hitPoints = (hitPoints - 1) & 0xFF;
        collisionFlags = 0xFF;
    }

    private DdzEndBossObjectInstance boss() {
        return parent instanceof DdzEndBossObjectInstance boss ? boss : null;
    }

    private void follow() {
        DdzEndBossObjectInstance boss = boss();
        if (boss != null) {
            x = (boss.getX() + OFFSET_X) & 0xFFFF;
            y = (boss.getY() + OFFSET_Y) & 0xFFFF;
        }
    }

    @Override
    protected void updateObject(int vIntRunCount, PlayableEntity player) {
        DdzEndBossObjectInstance boss = boss();
        if (boss == null || boss.isDestroyed()) {
            goDelete();
            return;
        }
        follow();
        if (statusDestroyed) {
            // loc_81E7A: sub_82C86 / Child_Draw_Sprite2
            // Child_Draw_Sprite2: Go_Delete_Sprite_2 once the boss's $38 bit 4 is set.
            if (boss.flag(4)) {
                goDelete();
            }
            return;
        }
        // loc_82D18
        if (!DdzObjectSupport.playerPowered(services()) || collisionFlags == 0) {
            return;
        }
        if ((byte) hitPoints < 0) {
            services().gameState().addScore(100);
            statusDestroyed = true;
            return;
        }
        if (flashTimer == 0) {
            flashTimer = 0x20;
            services().playSfx(Sonic3kSfx.THUMP_BOSS.id);
        }
        DdzPalette.applyFlashRow(services(), (flashTimer & 1) != 0 ? 0 : 1);
        flashTimer = (flashTimer - 1) & 0xFF;
        if (flashTimer == 0) {
            collisionFlags = 0;
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(0x300);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawable()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DDZ_MISC);
        if (renderer != null) {
            renderer.drawFrameIndex(0x38, x, y, false, false, PALETTE);
        }
    }


}
