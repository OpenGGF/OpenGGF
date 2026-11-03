package com.openggf.game.solid;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectInstance;

import java.util.Map;

@com.openggf.game.ModApi
public final class ObjectSolidExecutionContext {

    /**
     * Resolves one solid-helper call for the currently executing object. Each
     * call resolves P1, then P2 and the extension sidekicks, as the ROM helpers
     * do inside the object's own slot.
     */
    @FunctionalInterface
    @com.openggf.game.ModApi
    public interface Resolver {
        SolidCheckpointBatch resolve(SolidCall call);

        default SolidCheckpointBatch resolvePlayer(PlayableEntity player, SolidCall call) {
            throw new UnsupportedOperationException(
                    "This solid resolver does not support participant-scoped checkpoints.");
        }

        /**
         * The provider's declared registers with {@code d4} = the object's x
         * position when its routine was entered: the call a routine ending in
         * {@code bsr SolidObject} makes after moving its body.
         */
        default SolidCall entryCall() {
            return null;
        }
    }

    private static final SolidCheckpointBatch EMPTY_BATCH =
            new SolidCheckpointBatch(null, Map.of());
    private static final ObjectSolidExecutionContext INERT =
            new ObjectSolidExecutionContext(null, null, null);

    private final SolidExecutionRegistry owner;
    private final ObjectInstance object;
    private final Resolver resolver;
    private SolidCheckpointBatch lastCheckpoint;

    public ObjectSolidExecutionContext(
            SolidExecutionRegistry owner,
            ObjectInstance object,
            Resolver resolver) {
        this.owner = owner;
        this.object = object;
        this.resolver = resolver;
        this.lastCheckpoint = emptyBatchFor(object);
    }

    public static ObjectSolidExecutionContext inert() {
        return INERT;
    }

    public boolean isInert() {
        return object == null || resolver == null || owner == null;
    }

    public ObjectInstance object() {
        return object;
    }

    /** The routine-entry call: {@link Resolver#entryCall()}. Null when inert. */
    public SolidCall entryCall() {
        return isInert() ? null : resolver.entryCall();
    }

    /** Resolves the provider's declared registers with the routine-entry {@code d4}. */
    public SolidCheckpointBatch resolveSolidNowAll() {
        return solid(entryCall());
    }

    /**
     * Resolves one solid-helper call for every participant, in P1, P2,
     * extension order, at the object's own call site.
     */
    public SolidCheckpointBatch solid(SolidCall call) {
        if (isInert()) {
            lastCheckpoint = emptyBatchFor(object);
            return lastCheckpoint;
        }
        lastCheckpoint = resolver.resolve(call);
        owner.publishCheckpoint(lastCheckpoint);
        return lastCheckpoint;
    }

    /**
     * {@link #solid(SolidCall)} with the provider's declared d1-d3 and the
     * {@code d4} the call site loads itself, e.g. {@code move.w x_pos(a0),d4}
     * after the routine has moved its body, which carries a rider by nothing.
     */
    public SolidCheckpointBatch solidWithD4(int d4) {
        SolidCall entry = entryCall();
        return solid(entry == null ? null : new SolidCall(
                entry.d1HalfWidth(), entry.d2AirHalfHeight(), entry.d3GroundHalfHeight(), d4));
    }

    /**
     * Resolves only one participant with the routine-entry call and merges that
     * result into this object's current checkpoint.
     */
    public PlayerSolidContactResult resolveSolidNowOnly(PlayableEntity player) {
        return solidFor(player, entryCall());
    }

    /**
     * Resolves one solid-helper call for one participant and merges that result
     * into this object's current checkpoint. This supports ROM routines that
     * interleave each character's {@code *_1P} call with object-local reaction
     * code ({@code SolidObject_SingleCharacter}, {@code SolidObjectFull_1P}).
     */
    public PlayerSolidContactResult solidFor(PlayableEntity player, SolidCall call) {
        PlayerStandingState previousStanding = isInert()
                ? PlayerStandingState.NONE
                : owner.previousStanding(object, player);
        if (isInert()) {
            return PlayerSolidContactResult.noContact(
                    previousStanding, PreContactState.ZERO, PostContactState.ZERO);
        }
        SolidCheckpointBatch resolved = resolver.resolvePlayer(player, call);
        if (resolved == null || resolved.object() != object) {
            throw new IllegalStateException(
                    "Participant checkpoint batch must match the currently executing object.");
        }
        for (PlayableEntity returnedPlayer : resolved.perPlayer().keySet()) {
            if (returnedPlayer != player) {
                throw new IllegalStateException(
                        "Participant checkpoint may contain only the requested player.");
            }
        }
        PlayerSolidContactResult fresh = resolved.perPlayer().get(player);
        if (fresh == null) {
            fresh = PlayerSolidContactResult.noContact(
                    previousStanding, PreContactState.ZERO, PostContactState.ZERO);
        }
        java.util.IdentityHashMap<PlayableEntity, PlayerSolidContactResult> merged =
                new java.util.IdentityHashMap<>(lastCheckpoint.perPlayer());
        merged.put(player, fresh);
        lastCheckpoint = new SolidCheckpointBatch(object, merged);
        owner.publishCheckpoint(lastCheckpoint);
        return fresh;
    }

    public SolidCheckpointBatch lastCheckpoint() {
        return lastCheckpoint;
    }

    private static SolidCheckpointBatch emptyBatchFor(ObjectInstance object) {
        return object == null ? EMPTY_BATCH : new SolidCheckpointBatch(object, Map.of());
    }
}
