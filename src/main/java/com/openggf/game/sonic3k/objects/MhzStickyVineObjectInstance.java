package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * S3K SKL object $0A - MHZ sticky vine.
 *
 * <p>ROM reference: {@code Obj_MHZStickyVine}. This ports the route-critical
 * overlap capture, child-chain deformation state, player pull, and spindash
 * release handoff into the retraction routine.
 */
public final class MhzStickyVineObjectInstance extends AbstractObjectInstance {
    private static final int PRIORITY_BUCKET = 4;
    private static final int DISPLAY_HALF_WIDTH = 0x80;
    private static final int DISPLAY_HALF_HEIGHT = 0x80;
    private static final int GRAB_X_BIAS = 0x0C;
    private static final int GRAB_X_RANGE = 0x18;
    private static final int GRAB_Y_BIAS = 0x18;
    private static final int GRAB_Y_RANGE = 0x30;
    private static final int SEGMENT_COUNT = 8;
    private static final int SPINDASH_RELEASE_DELAY = 0x10;

    private final int[] segmentX = new int[SEGMENT_COUNT];
    private final int[] segmentY = new int[SEGMENT_COUNT];

    private AbstractPlayableSprite capturedPlayer;
    private boolean active;
    private boolean spindashReleaseArmed;
    private boolean retracting;
    private int spindashReleaseTimer;
    private int retractX;
    private int retractY;
    private int retractYSpeed;

    public MhzStickyVineObjectInstance(ObjectSpawn spawn) {
        super(spawn, "MHZStickyVine");
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segmentX[i] = spawn.x();
            segmentY[i] = spawn.y();
        }
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (retracting) {
            updateRetraction();
            updateOffscreenLifecycle();
            return;
        }
        if (!active) {
            scanNativePlayers(playerEntity);
        }
        if (capturedPlayer != null) {
            updateActivePlayer(capturedPlayer);
        }
        updateOffscreenLifecycle();
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return DISPLAY_HALF_WIDTH;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return DISPLAY_HALF_HEIGHT;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.MHZ_STICKY_VINE);
        if (renderer == null) {
            return;
        }
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            renderer.drawFrameIndex(0, segmentX[i], segmentY[i], false, false);
        }
    }

    @Override
    public String traceDebugDetails() {
        return super.traceDebugDetails()
                + " active=" + active
                + " retracting=" + retracting
                + " armed=" + spindashReleaseArmed
                + " timer=" + spindashReleaseTimer;
    }

    private void updateActivePlayer(AbstractPlayableSprite player) {
        if (player.getSpindash()) {
            spindashReleaseArmed = true;
            spindashReleaseTimer = SPINDASH_RELEASE_DELAY;
        }

        int playerX = player.getCentreX();
        int playerY = player.getCentreY();
        updateSegmentsToward(playerX, playerY);
        applyStickyPull(player);

        if (spindashReleaseArmed && !player.getSpindash()) {
            spindashReleaseTimer--;
            if (spindashReleaseTimer <= 0) {
                beginRetraction(player);
            }
        }
    }

    private void updateRetraction() {
        if (retractX != spawn.x()) {
            retractX += retractX < spawn.x() ? 2 : -2;
            if (Math.abs(retractX - spawn.x()) < 2) {
                retractX = spawn.x();
            }
        }
        if (retractY != spawn.y()) {
            if (retractY < spawn.y() || retractYSpeed < 0) {
                retractY += retractYSpeed >> 8;
                retractYSpeed += 0x38;
                if (retractY >= spawn.y() && retractYSpeed > 0) {
                    retractY = spawn.y();
                    retractYSpeed = 0;
                }
            } else {
                retractY = Math.max(spawn.y(), retractY - 2);
            }
        }
        updateSegmentsToward(retractX, retractY);
        if (retractX == spawn.x() && retractY == spawn.y()) {
            retracting = false;
            active = false;
            capturedPlayer = null;
        }
    }

    private void beginRetraction(AbstractPlayableSprite player) {
        retractX = player.getCentreX();
        retractY = player.getCentreY();
        retractYSpeed = -0x0600;
        spindashReleaseArmed = false;
        active = false;
        capturedPlayer = null;
        retracting = true;
    }

    private void scanNativePlayers(PlayableEntity playerEntity) {
        if (playerEntity instanceof AbstractPlayableSprite player) {
            tryCapture(player);
        }
        ObjectServices services = tryServices();
        if (services == null) {
            return;
        }
        for (PlayableEntity participant : services.playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            if (participant != playerEntity && participant instanceof AbstractPlayableSprite sprite) {
                tryCapture(sprite);
            }
        }
    }

    private void tryCapture(AbstractPlayableSprite player) {
        if (isInStickyWindow(player)
                && !player.isObjectControlled()
                && !player.isHurt()
                && !player.getDead()
                && !player.isDebugMode()) {
            active = true;
            capturedPlayer = player;
        }
    }

    private void updateOffscreenLifecycle() {
        if (!isInRange()) {
            setDestroyedByOffscreen();
        }
    }

    private void updateSegmentsToward(int targetX, int targetY) {
        int dx = targetX - spawn.x();
        int dy = targetY - spawn.y();
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segmentX[i] = spawn.x() + ((dx * i) / SEGMENT_COUNT);
            segmentY[i] = spawn.y() + ((dy * i) / SEGMENT_COUNT);
        }
    }

    private void applyStickyPull(AbstractPlayableSprite player) {
        int dx = player.getCentreX() - spawn.x();
        int dy = player.getCentreY() - spawn.y();
        int pullStrength = Math.abs(dx) << 6;
        int pullX = clamp(Math.abs(dx) / 2, 1, 4);
        int pullY = clamp(Math.abs(dy) / 2, 0, 4);
        if (dx != 0) {
            NativePositionOps.writeXPosPreserveSubpixel(player, player.getCentreX() - Integer.signum(dx) * pullX);
        }
        if (player.getAir() && dy != 0) {
            NativePositionOps.writeYPosPreserveSubpixel(player, player.getCentreY() - Integer.signum(dy) * pullY);
        }
        if (player.getAir() && player.getYSpeed() >= 0) {
            player.setXSpeed((short) (player.getXSpeed() >> 1));
        }
        if (!player.getAir()) {
            int groundSpeedMagnitude = Math.abs(player.getGSpeed());
            if (groundSpeedMagnitude >= 0x200 && groundSpeedMagnitude - 0x10 < pullStrength) {
                player.setGSpeed((short) (player.getGSpeed() >> 1));
            }
        }
        player.setPushing(false);
    }

    private boolean isInStickyWindow(AbstractPlayableSprite player) {
        int relX = player.getCentreX() - spawn.x() + GRAB_X_BIAS;
        if (relX < 0 || relX >= GRAB_X_RANGE) {
            return false;
        }
        int relY = player.getCentreY() - spawn.y() + GRAB_Y_BIAS;
        return relY >= 0 && relY < GRAB_Y_RANGE;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
