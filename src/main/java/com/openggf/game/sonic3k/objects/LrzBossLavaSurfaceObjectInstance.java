package com.openggf.game.sonic3k.objects;

import com.openggf.game.DamageCause;
import com.openggf.game.PlayableEntity;
import com.openggf.game.ShieldType;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/** Obj_59FC4: invisible LRZ3 lava solid, slope publisher and fractional current. */
public final class LrzBossLavaSurfaceObjectInstance extends AbstractObjectInstance
        implements SlopedSolidProvider, SolidObjectListener, ZeroArgRewindRecreatable {
    // Derived view of HScroll_table+$110. The owning runtime captures the underlying table.
    private final transient byte[] slope = new byte[176];
    private boolean tilted;

    public LrzBossLavaSurfaceObjectInstance() {
        super(new ObjectSpawn(0xB80, 0x640, 0, 0, 0, false, 0), "LRZBossLavaSurface");
    }

    private LrzZoneRuntimeState state() {
        return S3kRuntimeStates.currentLrz(services().zoneRuntimeRegistry()).orElseThrow();
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        tilted = state().backgroundRoutine() == 0xC;
        state().bossAct().advanceLava(tilted);
        updateDynamicSpawn(tilted ? 0xAA0 : 0xB80, 0x640);
    }

    @Override public byte[] getSlopeData() {
        if (!tilted) return null;
        for (int i = 0; i < slope.length; i++) slope[i] = (byte) state().bossAct().lavaHeight(i + 16);
        return slope;
    }
    @Override public boolean isSlopeFlipped() { return false; }
    @Override public int getSlopeBaseline() { return 0; }
    @Override public Integer getDirectTopLandingOverlapLimit() { return tilted ? 17 : null; }
    @Override public boolean rejectsZeroDistanceTopSolidLanding() { return tilted; }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public boolean usesCollisionHalfWidthForTopLanding() { return true; }
    @Override public boolean allowsObjectControlledSolidContacts() { return true; }
    @Override public SolidObjectParams getSolidParams() {
        return tilted ? SolidObjectParams.of(0xA0, 0, 0) : SolidObjectParams.of(0x180, 0x40, 0x30);
    }

    @Override public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (!contact.standing()) return;
        var boss = state().bossAct();
        // loc_5A0AC/loc_5A0DE: only native P1 receives the fire-shield exemption.
        boolean protectedP1 = player == services().playerQuery().mainPlayerOrNull()
                && player.getShieldType() == ShieldType.FIRE;
        if (!services().gameState().isEndOfLevelFlag() && !boss.capsuleOpened() && !protectedP1
                && !player.getDead() && !player.getInvulnerable()) {
            if (player.isCpuControlled()) player.applyHurt(getX(), DamageCause.NORMAL);
            else {
                boolean rings = player.getRingCount() > 0;
                if (rings && !player.hasShield()) services().spawnLostRings(player, frameCounter);
                player.applyHurtOrDeath(getX(), DamageCause.NORMAL, rings);
            }
        }
        // The current still applies after a burn, shield exemption or end-of-level gate.
        if (player instanceof AbstractPlayableSprite sprite) NativePositionOps.addXPos16_16(sprite, boss.lavaFlow());
    }

    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
    @Override public int getOnScreenHalfWidth() { return 0x180; }
    @Override public int getOnScreenHalfHeight() { return 0xC0; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { /* Background tiles draw the lava. */ }
}
