package com.openggf.game.sonic3k.objects;

import com.openggf.game.DamageCause;
import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Obj_LRZChainedPlatforms ($4A644): four/eight independent full-solid platforms on a
 * ten-waypoint loop. loc_4A6F4 expands the placed negative subtype using off_4A914;
 * sub_4A818 uses integer DIVS and preserves its remainder in the minor-axis fraction.
 * All path/spawn data and mappings come from the supplied ROM.
 */
public final class LrzChainedPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private static final int PATH_OFFSETS = 0x4A890;
    private static final int GROUP_OFFSETS = 0x4A914;
    private int anchorX, anchorY, xFixed, yFixed, subtype;
    private int targetX, targetY, cursor, stride, xVelocity, yVelocity;
    private int[] path;
    private boolean expanded, initialized;

    public LrzChainedPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZChainedPlatform");
        anchorX = spawn.x() & 0xFFFF;
        anchorY = spawn.y() & 0xFFFF;
        xFixed = anchorX << 16;
        yFixed = anchorY << 16;
        subtype = spawn.subtype() & 255;
        expanded = subtype < 0x80;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity leader) {
        if (!expanded) {
            expandGroup();
            return; // loc_4A744 returns; the overwritten first slot initializes next pass.
        }
        if (!initialized) initializePath();
        // sub_4A7BA: advance only when both integer coordinates reach the target.
        if (getX() == targetX && getY() == targetY) {
            cursor = Math.floorMod(cursor + stride, path.length / 2);
            selectTarget();
        }
        xFixed += xVelocity << 8;
        yFixed += yVelocity << 8;
        updateDynamicSpawn(getX(), getY());
        var contacts = checkpointAll();
        if (contacts != null) contacts.perPlayer().forEach((player, contact) -> {
            // swap d6 / andi #4|8: high bits 18/19, set at loc_1E11A for UNDERSIDE,
            // not the top-landing bits 20/21. The top remains a safe riding surface.
            if (contact.kind() == ContactKind.BOTTOM || contact.kind() == ContactKind.CRUSH) {
                hurtUnderside(player, vIntRunCount);
            }
        });
        if (services().levelManager() != null && isWithinSolidContactBounds()
                && (services().levelManager().getFrameCounter() & 15) == 0) {
            services().playSfx(Sonic3kSfx.CHAIN_TICK.id);
        }
    }

    private void expandGroup() {
        try {
            int table = GROUP_OFFSETS + services().rom().read16BitAddr(GROUP_OFFSETS + (subtype & 0x7F) * 2);
            int count = services().rom().read16BitAddr(table) + 1;
            for (int i = 0; i < count; i++) {
                int row = table + 2 + i * 6;
                int x = (anchorX + (short) services().rom().read16BitAddr(row)) & 0xFFFF;
                int y = (anchorY + (short) services().rom().read16BitAddr(row + 2)) & 0xFFFF;
                int phase = services().rom().read16BitAddr(row + 4) & 255;
                if (i == 0) {
                    xFixed = x << 16;
                    yFixed = y << 16;
                    subtype = phase;
                } else {
                    spawnFreeChild(() -> {
                        var child = new LrzChainedPlatformObjectInstance(new ObjectSpawn(
                                x, y, spawn.objectId(), phase, spawn.renderFlags(), false, 0));
                        child.anchorX = anchorX;
                        child.anchorY = anchorY;
                        return child;
                    });
                }
            }
            expanded = true;
            updateDynamicSpawn(getX(), getY());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read LRZ chained-platform group", e);
        }
    }

    private void initializePath() {
        try {
            int table = PATH_OFFSETS + services().rom().read16BitAddr(PATH_OFFSETS + (subtype >>> 4) * 2);
            int bytes = services().rom().read16BitAddr(table);
            path = new int[bytes / 2];
            for (int i = 0; i < path.length; i++) path[i] = (short) services().rom().read16BitAddr(table + 2 + i * 2);
            cursor = subtype & 15;
            stride = (spawn.renderFlags() & 1) == 0 ? 1 : -1;
            if (stride < 0) cursor = Math.floorMod(cursor - 1, path.length / 2);
            selectTarget();
            initialized = true;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read LRZ chained-platform path", e);
        }
    }

    private void selectTarget() {
        targetX = (anchorX + path[cursor * 2]) & 0xFFFF;
        targetY = (anchorY + path[cursor * 2 + 1]) & 0xFFFF;
        // sub_4A818: signed dividend, positive absolute divisor, quotient negated;
        // DIVS's signed remainder is copied verbatim into the minor-axis fraction.
        int dx = (short) (getX() - targetX), dy = (short) (getY() - targetY);
        int ax = Math.abs(dx), ay = Math.abs(dy);
        if (ay >= ax) {
            int dividend = dx << 8;
            xVelocity = dx == 0 ? 0 : -(dividend / ay);
            yVelocity = dy < 0 ? 0x100 : -0x100;
            xFixed = (xFixed & 0xFFFF0000) | (dx == 0 ? 0 : (dividend % ay) & 0xFFFF);
            yFixed &= 0xFFFF0000;
        } else {
            int dividend = dy << 8;
            yVelocity = dy == 0 ? 0 : -(dividend / ax);
            xVelocity = dx < 0 ? 0x100 : -0x100;
            yFixed = (yFixed & 0xFFFF0000) | (dy == 0 ? 0 : (dividend % ax) & 0xFFFF);
            xFixed &= 0xFFFF0000;
        }
    }

    private void hurtUnderside(PlayableEntity player, int vIntRunCount) {
        if (player.getDead() || player.getInvulnerable()
                || player instanceof AbstractPlayableSprite sprite && sprite.isHurt()) return;
        // sub_24280 backs out this frame's Y velocity before HurtCharacter (FixBugs=0).
        if (player instanceof AbstractPlayableSprite p) {
            int position = (p.getCentreY() << 16 | p.getYSubpixelRaw()) - (p.getYSpeed() << 8);
            NativePositionOps.writeYPosPreserveSubpixel(p, position >> 16);
            p.setSubpixelRaw(p.getXSubpixelRaw(), position & 0xFFFF);
        }
        if (player.isCpuControlled()) {
            player.applyHurt(getX(), DamageCause.NORMAL);
        } else {
            boolean rings = player.getRingCount() > 0;
            if (rings && !player.hasShield()) services().spawnLostRings(player, vIntRunCount);
            player.applyHurtOrDeath(getX(), DamageCause.NORMAL, rings);
        }
    }

    @Override public int getX() { return xFixed >>> 16; }
    @Override public int getY() { return yFixed >>> 16; }
    @Override public SolidObjectParams getSolidParams() { return SolidObjectParams.of(0x23, 0x18, 0x19); }
    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }
    @Override public boolean zeroXSpeedStopsOnLeftSideContact() { return true; }
    @Override public boolean airborneStaleStandingBitReturnsNoContact(PlayableEntity player) { return true; }
    @Override public boolean airborneRiderUnseatRequiresOwnCheckpoint(PlayableEntity player) { return true; }
    @Override public boolean usesInstanceSolidStateLatchKey() { return true; }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    @Override public int getPriorityBucket() { return 4; }
    @Override public int getOnScreenHalfWidth() { return 0x18; }
    @Override public int getOnScreenHalfHeight() { return 0x18; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return isCoarseXOutOfRange(anchorX, cameraX, coarseXCullRange()); }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialized) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.LRZ2_CHAINED_PLATFORM);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(0, getX(), getY(), false, false);
    }
}
