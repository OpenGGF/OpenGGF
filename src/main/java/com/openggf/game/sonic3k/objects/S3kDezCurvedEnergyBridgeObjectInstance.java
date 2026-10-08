package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/** SKL $56, Obj_DEZEnergyBridgeCurved / sub_47F9C (sonic3k.asm:93992-94084). */
public final class S3kDezCurvedEnergyBridgeObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private boolean initialized;
    private boolean activeRoutine;
    private int remaining;
    private int mappingFrame;
    private boolean drawPublished;
    private boolean renderedOnScreen;
    private final boolean[] admitted = new boolean[2];

    public S3kDezCurvedEnergyBridgeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZEnergyBridgeCurved");
    }
    private int periodMask() { return (0x80 << ((spawn.subtype() >> 2) & 3)) - 1; }
    private int phase(int levelFrame) {
        int offset = ((periodMask() + 1) >> 4) * ((spawn.subtype() >> 4) & 15);
        return (levelFrame + offset) & periodMask();
    }
    private int duration() { return ((spawn.subtype() & 3) + 2) << 5; }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        int levelFrame = services().levelManager().getFrameCounter();
        drawPublished = false;
        if (!initialized) {
            initialized = true;
            int unusedWindow = duration() - phase(levelFrame);
            if (unusedWindow > 0) {
                activeRoutine = true;
                remaining = unusedWindow;
            }
        }
        if (!activeRoutine) {
            if (phase(levelFrame) != 0) return;
            activeRoutine = true;
            remaining = duration();
        }
        remaining = (remaining - 1) & 0xFFFF;
        if (remaining == 0) activeRoutine = false;
        var query = services().playerQuery();
        PlayableEntity main = query.mainPlayerOrNull();
        if (main == null) main = player;
        selectPath(main, 0);
        PlayableEntity second = query.nativeP2OrNull();
        if (second != main) selectPath(second, 1);
        mappingFrame = levelFrame & 3;
        if (renderedOnScreen && (levelFrame & 7) == 0) services().playSfx(Sonic3kSfx.ENERGY_ZAP.id);
        // Even the expiry dispatch reaches Sprite_OnScreen_Test and draws.
        drawPublished = true;
    }
    private void selectPath(PlayableEntity entity, int slot) {
        if (!(entity instanceof AbstractPlayableSprite player)) return;
        if (remaining != 0
                && ((player.getCentreX() - getX() + 0x50) & 0xFFFF) < 0xA0
                && ((player.getCentreY() - getY() + 0x30) & 0xFFFF) < 0x60) {
            admitted[slot] = true;
            player.setTopSolidBit((byte) 0xE);
            player.setLrbSolidBit((byte) 0xF);
        } else if (admitted[slot]) {
            admitted[slot] = false;
            // Only timer expiry sets InAir. Leaving the rectangle while lit
            // resets the path without otherwise changing the player's status.
            if (remaining == 0) player.setAir(true);
            player.setTopSolidBit((byte) 0xC);
            player.setLrbSolidBit((byte) 0xD);
        }
    }
    @Override public void refreshPostCameraRenderState() {
        if (drawPublished) renderedOnScreen = isWithinRenderSpriteBounds(0x50, 0x30);
    }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return isCoarseXOutOfRange(getX(), cameraX, coarseXCullRange()); }
    @Override public int getOnScreenHalfWidth() { return 0x50; }
    @Override public int getOnScreenHalfHeight() { return 0x30; }
    @Override public int getPriorityBucket() { return 6; } // sub_47DDE: priority=$300, art bit 15 clear
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawPublished) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_CURVED_ENERGY_BRIDGE);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(mappingFrame, getX(), getY(),
                (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
    }
    public int remainingForTest() { return remaining; }
    public boolean drawPublishedForTest() { return drawPublished; }
    public boolean admittedForTest(int slot) { return admitted[slot]; }
}
