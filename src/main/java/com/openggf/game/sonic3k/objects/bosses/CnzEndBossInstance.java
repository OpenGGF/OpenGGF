package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.events.S3kCnzEventWriteSupport;
import com.openggf.game.sonic3k.objects.CnzCannonInstance;
import com.openggf.game.sonic3k.objects.CnzEggCapsuleInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * Bounded CNZ Act 2 end-boss wrapper for Task 8.
 *
 * <p>ROM anchor: {@code Obj_CNZEndBoss}.
 *
 * <p>This implementation explicitly does <strong>not</strong> claim full attack
 * parity. Task 8 only owns two seams from the verified ROM notes:
 * <ul>
 *   <li>bounded startup presence for the CNZ boss slot without claiming the
 *   wider attack-state choreography</li>
 *   <li>defeat handoff: clear {@code Boss_flag}, widen the camera max,
 *   spawn the capsule, and restore player control/music</li>
 * </ul>
 *
 * <p>Any later swing/attack logic should replace this bounded wrapper once the
 * remaining CNZ end-boss choreography is implemented.
 */
public final class CnzEndBossInstance extends AbstractObjectInstance {
    /**
     * Task 8 approximation for the post-defeat camera release.
     *
     * <p>The verified ROM note only requires that the camera max widens once the
     * boss handoff completes. The exact attack-phase arena management is outside
     * this slice, so the implementation widens by a conservative 0x100 pixels to
     * make the release explicit without claiming full boss-boundary parity.
     */
    private static final int CAMERA_RELEASE_DELTA = 0x100;
    private static final int CAPSULE_X = 0x4990;
    private static final int CAPSULE_Y = 0x02E0;
    private static final int CANNON_TRIGGER_X = 0x4A30;
    private static final int CANNON_X = 0x4B20;
    private static final int CANNON_Y = 0x02A8;
    private static final int CANNON_LAUNCH_WAIT = 0xBF;
    private static final int ICZ_START_ZONE_WORD = 0x500;

    private final int centreX;
    private final int centreY;

    private boolean defeatRequestedForTest;
    private boolean defeatHandoffComplete;
    private boolean capsuleResultsComplete;
    private boolean cannonSpawned;
    private boolean cannonArmed;
    private boolean cannonLaunched;
    private boolean transitionRequested;
    private int cannonLaunchTimer = -1;
    private CnzCannonInstance endCannon;

    public CnzEndBossInstance(ObjectSpawn spawn) {
        super(spawn, "CNZEndBoss");
        this.centreX = spawn.x();
        this.centreY = spawn.y();
    }

    /**
     * Task 8 keeps the defeat path bounded and therefore exposes a narrow test
     * seam instead of pretending the full damage/attack state machine exists.
     */
    public void forceDefeatForTest() {
        defeatRequestedForTest = true;
    }

    @Override
    public int getX() {
        return centreX;
    }

    @Override
    public int getY() {
        return centreY;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(5);
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (defeatRequestedForTest && !defeatHandoffComplete) {
            applyDefeatHandoff();
        }
        updatePostDefeatSequence(frameCounter, player);
    }

    /**
     * Returns whether the wider CNZ script has already declared this boss slot
     * active through shared state.
     *
     * <p>Task 8 intentionally does not let the promoted production slot claim
     * boss mode on its own. The real startup gate belongs to the later attack
     * choreography and CNZ event flow; this bounded wrapper only participates in
     * defeat cleanup once that wider state already exists.
     */
    private boolean isBossModeAlreadyOwnedExternally() {
        if (services().gameState().getCurrentBossId() == Sonic3kObjectIds.CNZ_END_BOSS) {
            return true;
        }
        Object provider = services().levelEventProvider();
        if (provider instanceof Sonic3kLevelEventManager manager) {
            Sonic3kCNZEvents events = manager.getCnzEvents();
            return events != null && events.isBossFlag();
        }
        return false;
    }

    /**
     * Verified Task 8 defeat handoff.
     *
     * <p>This is the honest boundary from the ROM findings:
     * <ol>
     *   <li>clear {@code Boss_flag}</li>
     *   <li>widen the camera max so the player can move past the boss arena</li>
     *   <li>spawn the CNZ-local egg capsule wrapper</li>
     *   <li>stay alive as the post-results cannon-launch controller</li>
     * </ol>
     */
    private void applyDefeatHandoff() {
        if (!isBossModeAlreadyOwnedExternally()) {
            return;
        }
        defeatHandoffComplete = true;

        S3kCnzEventWriteSupport.setBossFlag(services(), false);
        services().gameState().setCurrentBossId(0);

        int widenedMaxX = services().camera().getMaxX() + CAMERA_RELEASE_DELTA;
        services().camera().setMaxX((short) widenedMaxX);

        spawnChild(() -> new CnzEggCapsuleInstance(
                new ObjectSpawn(CAPSULE_X, CAPSULE_Y, Sonic3kObjectIds.EGG_CAPSULE, 0, 0, false, 0),
                this::onCapsuleResultsComplete));
    }

    private void updatePostDefeatSequence(int frameCounter, PlayableEntity player) {
        if (!defeatHandoffComplete || transitionRequested) {
            return;
        }
        if (capsuleResultsComplete && !cannonSpawned) {
            restorePlayerControl();
            restoreLevelMusic();
            if (player instanceof AbstractPlayableSprite sprite
                    && (sprite.getCentreX() & 0xFFFF) >= CANNON_TRIGGER_X) {
                spawnEndCannon();
            }
            return;
        }
        if (!cannonSpawned || !(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        if (!cannonArmed && endCannon != null && endCannon.isEndSequenceLaunchReady()) {
            cannonArmed = true;
            cannonLaunchTimer = CANNON_LAUNCH_WAIT;
            services().camera().setMaxYTarget((short) 0x0200);
            sprite.setControlLocked(true);
            return;
        }
        if (cannonArmed && !cannonLaunched) {
            if (cannonLaunchTimer-- > 0) {
                return;
            }
            if (endCannon != null) {
                endCannon.triggerEndSequenceLaunch(frameCounter);
            }
            cannonLaunched = true;
            return;
        }
        if (cannonLaunched && isPlayerPastIczLaunchThreshold(sprite)) {
            requestIczTransition();
        }
    }

    private void onCapsuleResultsComplete() {
        capsuleResultsComplete = true;
    }

    private void spawnEndCannon() {
        cannonSpawned = true;
        endCannon = spawnChild(() -> new CnzCannonInstance(
                new ObjectSpawn(CANNON_X, CANNON_Y, Sonic3kObjectIds.CNZ_CANNON, 0, 0, false, 0)));
    }

    private boolean isPlayerPastIczLaunchThreshold(AbstractPlayableSprite sprite) {
        int cameraYPlusWindow = (services().camera().getY() & 0xFFFF) + 0x20;
        int playerY = sprite.getCentreY() & 0xFFFF;
        return cameraYPlusWindow >= playerY;
    }

    private void requestIczTransition() {
        transitionRequested = true;
        int act = ICZ_START_ZONE_WORD & 0xFF;
        preparePlayersForIczFade();
        services().requestZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, act, true);
        setDestroyed(true);
    }

    private void preparePlayersForIczFade() {
        PlayableEntity focused = services().camera().getFocusedSprite();
        List<PlayableEntity> players = services().playerQuery()
                .playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS);
        for (PlayableEntity candidate : players) {
            if (candidate instanceof AbstractPlayableSprite sprite) {
                neutralizeLauncherStateForFade(sprite);
            }
        }
        if (focused instanceof AbstractPlayableSprite focusedPlayer && !players.contains(focusedPlayer)) {
            neutralizeLauncherStateForFade(focusedPlayer);
        }
    }

    /**
     * Restores main-player and sidekick control after the bounded defeat handoff.
     *
     * <p>The teleporter beam can leave the player object-controlled, rolled, and
     * hidden. CNZ's boss release must clear all three so the capsule handoff
     * leaves the player in a normal controllable state.
     */
    private void restorePlayerControl() {
        PlayableEntity focused = services().camera().getFocusedSprite();
        List<PlayableEntity> players = services().playerQuery()
                .playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS);
        for (PlayableEntity candidate : players) {
            if (candidate instanceof AbstractPlayableSprite sprite) {
                releaseSprite(sprite);
            }
        }
        if (focused instanceof AbstractPlayableSprite focusedPlayer && !players.contains(focusedPlayer)) {
            releaseSprite(focusedPlayer);
        }
    }

    private void releaseSprite(AbstractPlayableSprite sprite) {
        sprite.setControlLocked(false);
        ObjectControlState.none().applyTo(sprite);
        sprite.setHidden(false);
        sprite.setRolling(false);
    }

    private void neutralizeLauncherStateForFade(AbstractPlayableSprite sprite) {
        releaseSprite(sprite);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAir(false);
        sprite.setJumping(false);
        sprite.setOnObject(false);
        sprite.setObjectMappingFrameControl(false);
        sprite.setHidden(true);
        sprite.setControlLocked(true);
        sprite.setPriorityBucket(RenderPriority.PLAYER_DEFAULT);
        sprite.setHighPriority(false);
    }

    /**
     * Restores CNZ Act 2 music instead of claiming a full boss music / fade
     * state machine.
     */
    private void restoreLevelMusic() {
        services().playMusic(Sonic3kMusic.CNZ2.id);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.CNZ_END_BOSS);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(0, centreX, centreY, false, false);
    }
}
