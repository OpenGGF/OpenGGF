package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** SKL {@code $56}, {@code Obj_DEZEnergyBridgeCurved} (sonic3k.asm:94033-94106). */
public final class S3kDezCurvedEnergyBridgeObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private static final int[] PERIOD_MASKS = {0x7F, 0xFF, 0x1FF, 0x3FF};
    private int onFramesLeft;
    private int mappingFrame;
    private boolean initialized;
    private boolean visibleThisPass;
    private boolean playerOneInside;
    private boolean playerTwoInside;

    public S3kDezCurvedEnergyBridgeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZEnergyBridgeCurved");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        int frame = levelFrameCounter(vIntRunCount);
        if (!initialized) {
            initialized = true;
            int remaining = onDuration() - phase(frame);
            onFramesLeft = Math.max(remaining, 0);
        }
        if (onFramesLeft == 0 && phase(frame) == 0) {
            onFramesLeft = onDuration();
        }
        visibleThisPass = onFramesLeft != 0;
        if (visibleThisPass) {
            onFramesLeft--;
        }
        applyNativePlayers(onFramesLeft != 0);
        mappingFrame = frame & 3;
        if (visibleThisPass && (frame & 7) == 0 && isOnScreen(0) && tryServices() != null) {
            services().playSfx(Sonic3kSfx.ENERGY_ZAP.id);
        }
        if (!visibleThisPass && !isInRangeAt(getX())) {
            setDestroyedByOffscreen();
        }
    }

    private void applyNativePlayers(boolean fieldActive) {
        if (tryServices() == null || services().playerQuery() == null) {
            return;
        }
        List<PlayableEntity> players = services().playerQuery()
                .playersFor(ObjectPlayerParticipationPolicy.NATIVE_P1_P2);
        for (int i = 0; i < players.size(); i++) {
            applyPlayer(players.get(i), i != 0, fieldActive);
        }
    }

    void applyPlayer(PlayableEntity player, boolean playerTwo, boolean fieldActive) {
        if (!(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        boolean wasInside = playerTwo ? playerTwoInside : playerOneInside;
        boolean inside = fieldActive
                && ((player.getCentreX() - getX() + 0x50) & 0xFFFF) < 0xA0
                && ((player.getCentreY() - getY() + 0x30) & 0xFFFF) < 0x60;
        if (inside) {
            sprite.setTopSolidBit((byte) 0x0E);
            sprite.setLrbSolidBit((byte) 0x0F);
        } else if (wasInside) {
            if (!fieldActive) {
                sprite.setAir(true);
                sprite.setOnObject(false);
            }
            sprite.setTopSolidBit((byte) 0x0C);
            sprite.setLrbSolidBit((byte) 0x0D);
        }
        if (playerTwo) {
            playerTwoInside = inside;
        } else {
            playerOneInside = inside;
        }
    }

    private int periodMask() { return PERIOD_MASKS[(spawn.subtype() & 0x0C) >> 2]; }
    private int phaseOffset() { return ((periodMask() + 1) >> 4) * ((spawn.subtype() & 0xF0) >> 4); }
    private int onDuration() { return ((spawn.subtype() & 3) + 2) << 5; }
    private int phase(int frame) { return (frame + phaseOffset()) & periodMask(); }
    private int levelFrameCounter(int fallback) {
        ObjectServices svc = tryServices();
        return svc != null && svc.levelManager() != null ? svc.levelManager().getFrameCounter() : fallback;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visibleThisPass) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_ENERGY_BRIDGE_CURVED);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, getX(), getY(),
                    (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
        }
    }

    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x300); }
    @Override public int getOnScreenHalfWidth() { return 0x50; }
    @Override public int getOnScreenHalfHeight() { return 0x30; }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    int onFramesLeftForTest() { return onFramesLeft; }
    boolean visibleForTest() { return visibleThisPass; }
}
