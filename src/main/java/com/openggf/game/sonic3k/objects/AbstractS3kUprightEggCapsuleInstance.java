package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.EggPrisonAnimalInstance;
import com.openggf.level.objects.MultiPieceSolidProvider;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared S3K upright {@code Obj_EggCapsule} behavior.
 *
 * <p>ROM contract: body runs {@code SolidObjectFull} with
 * {@code d1=$2B,d2=$18,d3=$18}; the top button is a child at
 * {@code child_dy=-$24} using {@code d1=$1B,d2=4,d3=6}. Standing contact with
 * that child sets the parent trigger bit consumed by the capsule-open routine.
 */
public abstract class AbstractS3kUprightEggCapsuleInstance extends AbstractObjectInstance
        implements MultiPieceSolidProvider {
    protected static final int PIECE_BODY = 0;
    protected static final int PIECE_BUTTON = 1;

    private static final int BODY_HALF_WIDTH = 0x2B;
    private static final int BODY_HALF_HEIGHT = 0x18;
    private static final int BUTTON_Y_OFFSET = -0x24;
    private static final int BUTTON_HALF_WIDTH = 0x1B;
    private static final int BUTTON_AIR_HALF_HEIGHT = 4;
    private static final int BUTTON_GROUND_HALF_HEIGHT = 6;
    private static final int BUTTON_RECESS = 8;
    private static final int POST_OPEN_DELAY = 0x40;

    private final int centreX;
    private final int centreY;
    private boolean buttonTriggered;
    private boolean opened;
    private boolean resultsStarted;
    private int postOpenTimer;
    private int buttonRecess;
    protected S3kBossExplosionController explosionController;

    protected AbstractS3kUprightEggCapsuleInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.centreX = spawn.x();
        this.centreY = spawn.y();
    }

    protected AbstractS3kUprightEggCapsuleInstance(int x, int y, String name) {
        this(new ObjectSpawn(x, y, Sonic3kObjectIds.EGG_CAPSULE, 0, 0, false, 0), name);
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
        checkpointAll();
        if (!opened) {
            if (buttonTriggered) {
                openCapsule();
            }
            return;
        }

        tickExplosionController();
        if (resultsStarted) {
            updateAfterResultsStarted(frameCounter, player);
            return;
        }
        if (postOpenTimer > 0) {
            postOpenTimer--;
        }
        if (postOpenTimer == 0 && player instanceof AbstractPlayableSprite sprite && !sprite.getAir()) {
            startResults(sprite);
        }
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return getPieceParams(PIECE_BODY);
    }

    @Override
    public SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.MANUAL_CHECKPOINT;
    }

    @Override
    public int getPieceCount() {
        return 2;
    }

    @Override
    public int getPieceX(int pieceIndex) {
        return centreX;
    }

    @Override
    public int getPieceY(int pieceIndex) {
        return pieceIndex == PIECE_BUTTON
                ? centreY + BUTTON_Y_OFFSET + buttonRecess
                : centreY;
    }

    @Override
    public SolidObjectParams getPieceParams(int pieceIndex) {
        if (pieceIndex == PIECE_BUTTON) {
            return new SolidObjectParams(BUTTON_HALF_WIDTH, BUTTON_AIR_HALF_HEIGHT, BUTTON_GROUND_HALF_HEIGHT);
        }
        return new SolidObjectParams(BODY_HALF_WIDTH, BODY_HALF_HEIGHT, BODY_HALF_HEIGHT);
    }

    @Override
    public void onPieceContact(int pieceIndex, PlayableEntity player, SolidContact contact, int frameCounter) {
        if (pieceIndex == PIECE_BUTTON && !opened && contact != null && contact.standing()) {
            triggerButton();
        }
    }

    protected final boolean isOpened() {
        return opened;
    }

    protected final boolean isResultsStarted() {
        return resultsStarted;
    }

    protected int animalCount() {
        return 9;
    }

    protected int animalYOffset() {
        return -8;
    }

    protected void updateAfterResultsStarted(int frameCounter, PlayableEntity player) {
    }

    protected PlayerCharacter resolvePlayerCharacter() {
        if (services().configuration() == null) {
            return PlayerCharacter.SONIC_ALONE;
        }
        return S3kRuntimeStates.resolvePlayerCharacter(
                services().zoneRuntimeRegistry(),
                services().configuration());
    }

    private void triggerButton() {
        buttonTriggered = true;
        buttonRecess = BUTTON_RECESS;
    }

    private void openCapsule() {
        if (opened) {
            return;
        }
        opened = true;
        postOpenTimer = POST_OPEN_DELAY;
        services().playSfx(Sonic3kSfx.EXPLODE.id);
        explosionController = new S3kBossExplosionController(centreX, centreY, 3, services().rng());
        spawnAnimals();
    }

    private void tickExplosionController() {
        if (explosionController == null || explosionController.isFinished()) {
            return;
        }
        explosionController.tick();
        for (var entry : explosionController.drainPendingExplosions()) {
            if (entry.playSfx()) {
                services().playSfx(Sonic3kSfx.EXPLODE.id);
            }
            spawnChild(() -> new S3kBossExplosionChild(entry.x(), entry.y()));
        }
    }

    private void spawnAnimals() {
        for (int i = 0; i < animalCount(); i++) {
            int animalX = centreX + (i % 2 == 0 ? -(8 + i * 4) : (8 + i * 4));
            int animalY = centreY + animalYOffset();
            int delay = i * 4;
            int artVariant = services().rng().nextBits(1);
            ObjectSpawn animalSpawn = new ObjectSpawn(animalX, animalY, 0x28, 0, 0, false, 0);
            spawnChild(() -> new EggPrisonAnimalInstance(animalSpawn, delay, artVariant));
        }
    }

    private void startResults(AbstractPlayableSprite player) {
        resultsStarted = true;
        if (services().gameState() != null) {
            services().gameState().setEndOfLevelActive(true);
        }
        for (PlayableEntity candidate : resultParticipants(player)) {
            if (candidate instanceof AbstractPlayableSprite sprite) {
                lockForResults(sprite);
            }
        }
        PlayerCharacter character = resolvePlayerCharacter();
        int currentAct = services().currentAct();
        spawnChild(() -> new S3kResultsScreenObjectInstance(character, currentAct));
    }

    private List<PlayableEntity> resultParticipants(AbstractPlayableSprite player) {
        try {
            List<PlayableEntity> queried = services().playerQuery()
                    .playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS);
            if (queried.contains(player)) {
                return queried;
            }
            List<PlayableEntity> participants = new ArrayList<>(queried.size() + 1);
            participants.add(player);
            participants.addAll(queried);
            return participants;
        } catch (RuntimeException ignored) {
            List<PlayableEntity> participants = new ArrayList<>();
            participants.add(player);
            participants.addAll(services().sidekicks());
            return participants;
        }
    }

    private void lockForResults(AbstractPlayableSprite sprite) {
        ObjectControlState.nativeBit7FullControl().applyTo(sprite);
        sprite.setControlLocked(true);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAnimationId(Sonic3kAnimationIds.VICTORY);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.EGG_CAPSULE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(opened ? 1 : 0, centreX, centreY, false, false);
        renderer.drawFrameIndex(buttonTriggered ? 0x0C : 5,
                centreX, centreY + BUTTON_Y_OFFSET + buttonRecess, false, false);
    }
}
