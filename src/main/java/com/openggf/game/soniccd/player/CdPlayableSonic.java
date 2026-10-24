package com.openggf.game.soniccd.player;

import com.openggf.game.CharacterKey;
import com.openggf.game.internal.NativePlayableSlot;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdPlayerVisualAdapter;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.PerObjectRewindSnapshot.PlayableSubclassRewindExtra;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;
import com.openggf.sprites.render.PlayerResidentBanks;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Engine projection of the CD-owned object call. No shared movement, animation,
 * timer, history, water or display tail runs. Draw reads a completed publication
 * and resident bank; it never reads input, advances a body or services a cue.
 * Native queue capacity/SAT and bit7 publication remain separate owners.
 */
public final class CdPlayableSonic extends AbstractPlayableSprite implements NativePlayableSlot {
    private static final CdPlayerEngineRules ENGINE_RULES = new CdPlayerEngineRules();
    /** Internal composition port; the source-bound shared owner owns touch and queue admission. */
    public interface ObjectPass {
        CdPlayerFrameRuntime.BodyPublication runBody(int levelFrames);
        void enqueuePlayer(CdPlayerBody.DisplayEligibility cut);
        void close();
    }

    private final CdPlayerFrameRuntime frame;
    private final ObjectPass objectPass;
    private final PlayerSpriteRenderer renderer;
    private List<CdPlayerFrameRuntime.Sound> lastSounds = List.of();
    private boolean awaitingSpriteBuild;
    private boolean queuedPointer;
    private Optional<CdPlayerBody.DisplayPublication> packet = Optional.empty();

    public CdPlayableSonic(String code, CdPlayerFrameRuntime frame, GraphicsManager graphics) {
        this(code, frame, graphics, null);
    }

    public CdPlayableSonic(String code, CdPlayerFrameRuntime frame, GraphicsManager graphics, ObjectPass objectPass) {
        super(code, (short) (Objects.requireNonNull(frame, "frame").state().motion().centreX16_16() >> 16),
                (short) (frame.state().motion().centreY16_16() >> 16), CdPlayerEngineRules.SPEC);
        this.frame = frame;
        this.objectPass = objectPass;
        try {
            renderer = new PlayerSpriteRenderer(CdPlayerVisualAdapter.artSet(frame.assets().sonic(), 0x10000),
                    Objects.requireNonNull(graphics, "graphics"));
            PlayerResidentBanks.claim(renderer);
            restoreResident(frame.artState());
            super.setSpriteRenderer(renderer);
            setAnimationFrameCount(frame.assets().sonic().mappingFrames().size());
            frame.bindArtPublication((request, completeState) -> restoreResident(completeState));
            projectNativeState();
        } catch (RuntimeException | Error failure) {
            closeAfterFailure(failure); // A partial bank claim/upload cannot be retried.
            throw failure;
        }
    }

    @Override public CharacterKey characterKey() { return CharacterKey.SONIC; }
    @Override public SecondaryAbility getSecondaryAbility() { return SecondaryAbility.NONE; }
    @Override public com.openggf.game.rules.GameRules getGameRules() {
        return ENGINE_RULES.getRules();
    }
    @Override public com.openggf.game.PhysicsModifiers getPhysicsModifiers() { return ENGINE_RULES.getModifiers(); }

    public CdPlayerBody.State nativeState() { return frame.state(); }
    /** Ordered requests consumed by this silent development presentation; no driver playback. */
    public List<CdPlayerFrameRuntime.Sound> lastSoundRequests() { return lastSounds; }

    public void requireCompletedNativeFrame() {
        if (awaitingSpriteBuild) throw new IllegalStateException("Previous CD frame still awaits its sprite build");
    }

    @Override public void runPlayableSlot(LevelManager levelManager, int levelFrames) {
        if (levelManager == null || !(levelManager.getCurrentLevel() instanceof SonicCdLevel level)
                || level.assets() != frame.assets()) {
            throw new IllegalArgumentException("CD actor requires its exact authenticated stage owner");
        }
        requireCompletedNativeFrame();
        frame.requireBodyEntitlement(); // A pre-service protocol rejection has advanced no Body.
        try {
            var publication = objectPass == null ? frame.runBody(levelFrames) : objectPass.runBody(levelFrames);
            lastSounds = publication.sounds();
            queuedPointer = false;
            var boundary = publication.result().boundary();
            // Main's Display call or Hurt's unconditional DrawObject (Sonic/Main.asm:373-441,2366); Init/absent have none.
            if (boundary == CdPlayerBody.Boundary.MAIN_RETURN || boundary == CdPlayerBody.Boundary.HURT_RETURN) {
                var cut = publication.result().effects().stream().filter(CdPlayerBody.DisplayEligibility.class::isInstance)
                        .map(CdPlayerBody.DisplayEligibility.class::cast).findFirst().orElseThrow();
                if (cut.eligible() && objectPass != null) {
                    objectPass.enqueuePlayer(cut);
                } else if (cut.eligible()) {
                    if ((cut.spriteFlags() & 12) != 4) {
                        throw new UnsupportedOperationException("CD development queue requires foreground-relative sprites");
                    }
                    // DrawObject clears before its pre-scroll camera test. No later
                    // admitted Body reader observes this deferred pure-call write.
                    frame.publishDrawObjectOnScreen(false);
                    var camera = com.openggf.game.GameServices.camera();
                    queuedPointer = insideSourceCamera(cut, camera.getX(), camera.getY());
                }
            }
            awaitingSpriteBuild = true;
            projectNativeState();
        } catch (RuntimeException | Error failure) {
            closeAfterFailure(failure);
            throw failure;
        }
    }

    /** Source DrawObjects after the camera pass, independent of GPU submission. */
    public void finishAfterCamera() {
        if (objectPass != null) throw new IllegalStateException("Shared CD object owner constructs the sprite table");
        if (!awaitingSpriteBuild) throw new IllegalStateException("No completed CD slot awaits sprite construction");
        if (queuedPointer && frame.state().header().slotId() != 0) {
            var pose = frame.display().orElseThrow();
            if (frame.assets().sonic().mappingFrames().get(pose.mapFrame()).pieceCount() > 80) {
                frame.close();
                throw new UnsupportedOperationException("CD one-player SAT exceeds the native 80-piece table");
            }
            frame.publishDrawObjectOnScreen(true); // Empty mappings also reach DrawDone.
            packet = frame.display();
        } else packet = Optional.empty();
        queuedPointer = false;
        awaitingSpriteBuild = false;
        projectNativeState();
    }

    /** Shared owner has built the CPU SAT and written bit7; project only, without another sprite build. */
    public void finishSharedAfterCamera() {
        if (objectPass == null || !awaitingSpriteBuild) {
            throw new IllegalStateException("No shared CD slot awaits its completed sprite table");
        }
        queuedPointer = false;
        packet = Optional.empty();
        awaitingSpriteBuild = false;
        projectNativeState();
    }

    private static boolean insideSourceCamera(CdPlayerBody.DisplayEligibility cut, int cameraX, int cameraY) {
        if ((cut.spriteFlags() & 12) != 4) {
            throw new UnsupportedOperationException("CD development queue requires foreground-relative sprites");
        }
        int dx = (short) (cut.centreXWord() - cameraX);
        int width = cut.width();
        if ((short) (dx + width) < 0 || (short) (dx - width) >= 320) return false;
        int y = sourceWrappedY(cut.centreYWord(), cameraY & 0xFFFF);
        int dy = (short) (y - cameraY);
        return (short) (dy + cut.yRadius()) >= 0 && (short) (dy - cut.yRadius()) < 224;
    }

    private static int sourceWrappedY(int y, int cameraY) {
        if (cameraY < 0x100 && y >= 0x800) return (y - 0x800) & 0xFFFF;
        if (cameraY >= 0x700 && y < 0x100) return (y + 0x800) & 0xFFFF;
        return y;
    }

    /** Reproject after all shared/sprite restore participants have completed. No source work. */
    public void rebindAfterRestore() {
        requireCompletedNativeFrame();
        try {
            projectNativeState();
            PlayerResidentBanks.claim(renderer);
            restoreResident(frame.artState());
        } catch (RuntimeException | Error failure) {
            closeAfterFailure(failure);
            throw failure;
        }
    }

    private void closeAfterFailure(Throwable failure) {
        if (objectPass != null) {
            try { objectPass.close(); }
            catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
        }
        try { frame.close(); }
        catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
        awaitingSpriteBuild = false;
        queuedPointer = false;
        packet = Optional.empty();
        try { projectNativeState(); }
        catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
    }

    private void projectNativeState() {
        var state = frame.state();
        var motion = state.motion();
        // Direct projection avoids generic landing/roll setters and their writes.
        air = (motion.flags() & 2) != 0;
        rolling = (motion.flags() & 4) != 0;
        rollingJump = (motion.flags() & 0x10) != 0;
        pushing = (motion.flags() & 0x20) != 0;
        jumping = motion.playerJump() != 0;
        objectControlled = (motion.playerControl() & 1) != 0;
        xSpeed = (short) motion.xVelocity();
        ySpeed = (short) motion.yVelocity();
        gSpeed = (short) motion.groundVelocity();
        angle = (byte) motion.angle();
        setCollisionRadii((short) motion.xRadius(), (short) motion.yRadius(), false);
        setWidth((motion.xRadius() + 1) * 2);
        setHeight(motion.yRadius() * 2);
        NativePositionOps.writeXPosResetSubpixel(this, motion.centreX16_16() >> 16);
        NativePositionOps.writeYPosResetSubpixel(this, motion.centreY16_16() >> 16);
        setSubpixelRaw(motion.centreX16_16() & 0xFFFF, motion.centreY16_16() & 0xFFFF);
        setDirection((motion.flags() & 1) != 0 ? Direction.LEFT : Direction.RIGHT);
        setAnimationId(motion.animation());
        setMappingFrame(state.animation().mapFrame());
        setRenderFlips((motion.spriteFlags() & 1) != 0, (motion.spriteFlags() & 2) != 0);
        setRenderOffsets((short) 0, (short) 0);
        setRenderFlagWidthPixels(state.header().width());
        setHighPriority((state.header().tile() & 0x8000) != 0);
        setPriorityBucket(state.header().priority() & 7);
        setNativeSlotPresent(state.header().slotId() != 0);
    }

    private void restoreResident(CdPlayerArtRuntime.State state) {
        PlayerResidentBanks.restore(renderer, new PlayerResidentBanks.Snapshot(
                CdPlayerVisualAdapter.residentPatterns(state), -1));
    }

    @Override public void draw() {
        if (frame.closed()) throw new IllegalStateException("CD player admission is closed");
        requireCompletedNativeFrame();
        if (objectPass != null) return; // The overlay draws the one immutable shared picture.
        var optional = packet;
        if (optional.isEmpty() || !optional.orElseThrow().queued()) return;
        var pose = optional.orElseThrow();
        if ((pose.spriteFlags() & 12) != 4) {
            throw new UnsupportedOperationException("CD development player requires foreground-relative sprites");
        }
        int y = sourceWrappedY(pose.centreY16_16() >>> 16,
                com.openggf.game.GameServices.camera().getY() & 0xFFFF);
        renderer.drawFrame(pose.mapFrame(), (short) (pose.centreX16_16() >> 16), (short) y,
                (pose.spriteFlags() & 1) != 0, (pose.spriteFlags() & 2) != 0);
    }

    @Override public void setSpriteRenderer(PlayerSpriteRenderer replacement) {
        if (replacement != renderer) {
            throw new UnsupportedOperationException("CD renderer is owned by its native resident bank");
        }
        super.setSpriteRenderer(replacement);
    }

    @Override public void resetState() {
        throw new UnsupportedOperationException("Rebuild the named CD admission instead of generic player reset");
    }

    private record Extra(CdPlayerFrameRuntime.ActorState actor, List<CdPlayerFrameRuntime.Sound> sounds,
                         Optional<CdPlayerBody.DisplayPublication> packet) implements PlayableSubclassRewindExtra {
        private Extra { sounds = List.copyOf(sounds); Objects.requireNonNull(packet); }
    }

    @Override protected PlayableSubclassRewindExtra captureSubclassRewindState() {
        requireCompletedNativeFrame();
        return new Extra(frame.captureActor(), lastSounds, packet);
    }

    @Override protected void restoreSubclassRewindState(PlayableSubclassRewindExtra extra) {
        if (!(extra instanceof Extra value)) {
            frame.close();
            projectNativeState();
            throw new IllegalArgumentException("Missing CD-native player capture; admission is now closed");
        }
        try { frame.validateActorRestore(value.actor()); }
        catch (RuntimeException | Error failure) {
            // The base has already applied its mirror fields. Restore their native projection.
            try { projectNativeState(); }
            catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
        frame.restoreActor(value.actor());
        lastSounds = value.sounds();
        packet = value.packet();
        awaitingSpriteBuild = false;
        queuedPointer = false;
        projectNativeState();
    }
}
