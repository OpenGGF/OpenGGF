package com.openggf.game.sonic2.objects;

import com.openggf.audio.GameSound;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.PlayerStandingState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpringHelper;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;

/**
 * OOZSpring (Obj45) - pressure spring from Oil Ocean Zone.
 */
public class OOZSpringObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, RewindRecreatable {
    private static final int STRONG_STRENGTH = -0x1000;
    private static final int WEAK_STRENGTH = -0x0A00;
    private static final int HORIZONTAL_IDLE_FRAME = 0x0A;
    private static final int VERTICAL_MAX_FRAME = 9;
    private static final int HORIZONTAL_COMPRESS_LIMIT = 0x12;
    private static final ObjectPlayerParticipationPolicy PLAYER_PARTICIPATION =
            ObjectPlayerParticipationPolicy.NATIVE_P1_P2;

    private int originalX;
    private int currentX;
    private int currentY;
    private boolean horizontal;
    private int strength;
    private int mappingFrame;
    private boolean pendingMainHorizontalLaunch;
    private boolean pendingSidekickHorizontalLaunch;
    private boolean compressedThisFrame;

    public OOZSpringObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.originalX = spawn.x();
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.horizontal = horizontalModeForSubtype(spawn.subtype());
        this.strength = (spawn.subtype() & 0x02) == 0 ? STRONG_STRENGTH : WEAK_STRENGTH;
        this.mappingFrame = horizontal ? HORIZONTAL_IDLE_FRAME : 0;
        updateDynamicSpawn(currentX, currentY);
    }

    @Override
    public OOZSpringObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new OOZSpringObjectInstance(ctx.spawn(), getName());
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            return;
        }

        if (horizontal) {
            updateHorizontal(playerEntity);
        } else {
            updateVertical(playerEntity);
        }
        updateDynamicSpawn(currentX, currentY);
    }

    private void updateHorizontal(PlayableEntity playerEntity) {
        compressedThisFrame = false;
        List<PlayableEntity> participants = playerParticipants(playerEntity);
        if (!solidExecutionIsInert()) {
            SolidCheckpointBatch batch = checkpointAll();
            for (PlayableEntity participant : participants) {
                PlayerSolidContactResult result = batch.perPlayer().get(participant);
                if (result != null && result.kind() != ContactKind.NONE && result.pushingNow()
                        && participant instanceof AbstractPlayableSprite player) {
                    compressHorizontalSpring(player);
                }
            }
        }

        if (!compressedThisFrame) {
            releaseHorizontalSpring(participants);
        }
    }

    private void updateVertical(PlayableEntity playerEntity) {
        List<PlayableEntity> participants = playerParticipants(playerEntity);
        List<AbstractPlayableSprite> standingPlayers = new ArrayList<>(2);
        boolean launchedThisFrame = false;
        for (PlayableEntity participant : participants) {
            PlayerStandingState previous = services().solidExecutionRegistry().previousStanding(this, participant);
            if (previous.standing() && participant instanceof AbstractPlayableSprite player) {
                standingPlayers.add(player);
            }
        }

        if (!standingPlayers.isEmpty()) {
            if (mappingFrame >= VERTICAL_MAX_FRAME) {
                for (AbstractPlayableSprite player : standingPlayers) {
                    launchVertical(player);
                }
                launchedThisFrame = true;
            } else {
                mappingFrame++;
            }
        } else if (mappingFrame > 0) {
            mappingFrame--;
        }

        // ROM Obj45_Vertical branches directly to the launch routine when
        // frame 9 fires; SolidObject45 only runs on the non-launch path.
        if (!launchedThisFrame && !solidExecutionIsInert()) {
            checkpointAll();
        }
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        if (!(playerEntity instanceof AbstractPlayableSprite player)) {
            return;
        }
        if (horizontal) {
            if (contact.pushing()) {
                compressHorizontalSpring(player);
            }
            return;
        }
        if (contact.standing()) {
            updateVerticalForStandingPlayer(player);
        }
    }

    private void compressHorizontalSpring(AbstractPlayableSprite player) {
        boolean flipped = isXFlipped();
        if (flipped) {
            if (player.getDirection() != Direction.RIGHT) {
                return;
            }
            if (currentX < originalX + HORIZONTAL_COMPRESS_LIMIT) {
                currentX++;
                NativePositionOps.addXPosPreserveSubpixel(player, 1);
                player.setGSpeed((short) 0x40);
                player.setXSpeed((short) 0);
            }
        } else {
            if (player.getDirection() != Direction.LEFT) {
                return;
            }
            if (currentX > originalX - HORIZONTAL_COMPRESS_LIMIT) {
                currentX--;
                NativePositionOps.addXPosPreserveSubpixel(player, -1);
                player.setGSpeed((short) -0x40);
                player.setXSpeed((short) 0);
            }
        }
        mappingFrame = Math.abs(originalX - currentX) + HORIZONTAL_IDLE_FRAME;
        setPendingHorizontalLaunch(player);
        compressedThisFrame = true;
        updateDynamicSpawn(currentX, currentY);
    }

    private void releaseHorizontalSpring(List<PlayableEntity> participants) {
        if (currentX == originalX) {
            return;
        }

        if (currentX > originalX) {
            mappingFrame -= 4;
            currentX -= 4;
            if (currentX <= originalX) {
                currentX = originalX;
                mappingFrame = HORIZONTAL_IDLE_FRAME;
            }
        } else {
            mappingFrame -= 4;
            currentX += 4;
            if (currentX >= originalX) {
                currentX = originalX;
                mappingFrame = HORIZONTAL_IDLE_FRAME;
            }
        }

        for (PlayableEntity participant : participants) {
            if (participant instanceof AbstractPlayableSprite player && consumePendingHorizontalLaunch(player)) {
                launchHorizontal(player);
            }
        }
    }

    private void updateVerticalForStandingPlayer(AbstractPlayableSprite player) {
        if (mappingFrame >= VERTICAL_MAX_FRAME) {
            launchVertical(player);
        } else {
            mappingFrame++;
        }
    }

    private void launchVertical(AbstractPlayableSprite player) {
        player.setYSpeed((short) strength);
        player.setAir(true);
        player.setOnObject(false);
        player.setHurt(false);
        player.setAnimationId(Sonic2AnimationIds.SPRING);
        if ((spawn.subtype() & 0x80) != 0) {
            player.setXSpeed((short) 0);
        }
        applySubtypeLaunchEffects(player, false);
        playSpringSound();
    }

    private void launchHorizontal(AbstractPlayableSprite player) {
        int launchStrength = (Math.abs(originalX - currentX) + HORIZONTAL_IDLE_FRAME) << 7;
        int xVelocity = -launchStrength;
        int playerX = player.getCentreX() - 4;
        Direction direction = Direction.LEFT;

        if (!isXFlipped()) {
            xVelocity = -xVelocity;
            playerX += 8;
            direction = Direction.RIGHT;
        }

        NativePositionOps.writeXPosPreserveSubpixel(player, playerX);
        player.setXSpeed((short) xVelocity);
        player.setGSpeed((short) xVelocity);
        player.setDirection(direction);
        player.setMoveLockTimer(0x0F);
        if (!player.getRolling()) {
            player.setAnimationId(Sonic2AnimationIds.WALK);
            player.forceAnimationRestart();
        }
        if ((spawn.subtype() & 0x80) != 0) {
            player.setYSpeed((short) 0);
        }
        applySubtypeLaunchEffects(player, true);
        player.setPushing(false);
        playSpringSound();
    }

    private void applySubtypeLaunchEffects(AbstractPlayableSprite player, boolean horizontalLaunch) {
        int subtype = spawn.subtype();
        if ((subtype & 0x01) != 0) {
            player.setGSpeed((short) 1);
            player.setFlipAngle(1);
            player.setAnimationId(Sonic2AnimationIds.WALK);
            if (horizontalLaunch) {
                player.setFlipsRemaining((subtype & 0x02) == 0 ? 3 : 1);
                player.setFlipSpeed(8);
            } else {
                player.setFlipsRemaining((subtype & 0x02) == 0 ? 1 : 0);
                player.setFlipSpeed(4);
            }
            if (player.getDirection() == Direction.LEFT) {
                player.setFlipAngle(-player.getFlipAngle());
                player.setGSpeed((short) -player.getGSpeed());
            }
        }
        SpringHelper.applyCollisionLayerBits(player, subtype);
    }

    private void playSpringSound() {
        ObjectServices services = tryServices();
        if (services != null) {
            services.playSfx(GameSound.SPRING);
        }
    }

    @Override
    public SolidObjectParams getSolidParams() {
        if (horizontal) {
            return new SolidObjectParams(31, 12, 13);
        }
        return new SolidObjectParams(27, 20, 20);
    }

    @Override
    public SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.MANUAL_CHECKPOINT;
    }

    @Override
    public boolean bypassesOffscreenSolidGate() {
        return true;
    }

    @Override
    public boolean usesInclusiveRightEdge() {
        return horizontal;
    }

    @Override
    public boolean usesInstanceSolidStateLatchKey() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.OOZ_PRESSURE_SPRING);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, currentX, currentY, isXFlipped(), false);
        }
    }

    @Override
    public int getOnScreenHalfWidth() {
        return horizontal ? 20 : 16;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return 20;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    private boolean isXFlipped() {
        return (spawn.renderFlags() & 0x01) != 0;
    }

    private List<PlayableEntity> playerParticipants(PlayableEntity updatePlayer) {
        ObjectServices services = tryServices();
        if (services == null || services.playerQuery() == null) {
            return updatePlayer == null ? List.of() : List.of(updatePlayer);
        }
        List<PlayableEntity> participants = services.playerQuery().playersFor(PLAYER_PARTICIPATION);
        if (updatePlayer != null && !participants.contains(updatePlayer)) {
            ArrayList<PlayableEntity> withUpdatePlayer = new ArrayList<>(participants.size() + 1);
            withUpdatePlayer.add(updatePlayer);
            withUpdatePlayer.addAll(participants);
            return withUpdatePlayer;
        }
        return participants;
    }

    private boolean solidExecutionIsInert() {
        ObjectServices services = tryServices();
        return services == null || services.solidExecution() == null || services.solidExecution().isInert();
    }

    private void setPendingHorizontalLaunch(AbstractPlayableSprite player) {
        if (isNativeSidekick(player)) {
            pendingSidekickHorizontalLaunch = true;
        } else {
            pendingMainHorizontalLaunch = true;
        }
    }

    private boolean consumePendingHorizontalLaunch(AbstractPlayableSprite player) {
        if (isNativeSidekick(player)) {
            boolean pending = pendingSidekickHorizontalLaunch;
            pendingSidekickHorizontalLaunch = false;
            return pending;
        }
        boolean pending = pendingMainHorizontalLaunch;
        pendingMainHorizontalLaunch = false;
        return pending;
    }

    private boolean isNativeSidekick(AbstractPlayableSprite player) {
        ObjectServices services = tryServices();
        if (services == null) {
            return false;
        }
        List<PlayableEntity> nativePlayers = services.playerQuery().playersFor(PLAYER_PARTICIPATION);
        return nativePlayers.size() > 1 && nativePlayers.get(1) == player;
    }

    private static boolean horizontalModeForSubtype(int subtype) {
        int selector = ((subtype & 0xFF) >> 3) & 0x0E;
        return selector != 0;
    }
}
