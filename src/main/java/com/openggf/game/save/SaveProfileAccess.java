package com.openggf.game.save;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Engine-internal bridge, intentionally NOT ModApi. Same owning serialized read/writer as SaveManager.
 * An actual NoSuchFileException is missing; corrupt/quarantine/denied/unknown reads never authorize creation.
 * Receipts attest only this storage operation. They grant no gameplay, native stack or restore authority.
 */
public final class SaveProfileAccess {
    public enum Disposition { MISSING, VALID, HASH_WARNING, CORRUPT, QUARANTINED_HISTORY, UNKNOWN_HISTORY, UNAVAILABLE, ERROR }
    public enum CreateDisposition { CREATED, CREATED_CLEANUP_FAILED, CONFLICT, NOT_MISSING, ALREADY_USED, RECHECK_FAILED, COMPLETION_UNKNOWN, SUBMISSION_FAILED, FAILED }
    public static final class Lookup {
        private final SaveManager.ProfileLookup actual;
        private final Map<String,Object> payload;
        private Lookup(SaveManager.ProfileLookup actual){this.actual=actual;payload=actual.payload;}
        public Disposition disposition(){return actual.disposition;}
        public int slot(){return actual.slot;}
        public String game(){return actual.game;}
        public Map<String,Object> payload(){return payload;}
        public Optional<Throwable> failure(){return Optional.ofNullable(actual.failure);}
        public Optional<Throwable> quarantineFailure(){return Optional.ofNullable(actual.quarantineFailure);}
        public Optional<Throwable> absenceObservation(){return Optional.ofNullable(actual.absenceObservation);}
    }
    public static final class Creation {
        private final SaveManager.ProfileCreation actual;
        private Creation(SaveManager.ProfileCreation actual){this.actual=actual;}
        public CreateDisposition disposition(){return actual.disposition;}
        public Optional<Throwable> failure(){return Optional.ofNullable(actual.failure);}
        /** True attests publication. Use publication() to distinguish unknown completion from known false. */
        public boolean persistentPublished(){return actual.persistentPublished;}
        public Optional<Boolean> publication(){return actual.disposition==CreateDisposition.COMPLETION_UNKNOWN?Optional.empty():Optional.of(actual.persistentPublished);}
        public Optional<Throwable> cleanupFailure(){return Optional.ofNullable(actual.cleanupFailure);}
        public Optional<Disposition> recheckDisposition(){return actual.recheck==null?Optional.empty():Optional.of(actual.recheck.disposition);}
        public Optional<Throwable> quarantineFailure(){return actual.recheck==null?Optional.empty():Optional.ofNullable(actual.recheck.quarantineFailure);}
    }
    // BEGIN original boot backend initialization 20261107. Point-in-time engine I/O, not firmware status.
    public static final class BackendInitialization {
        private final SaveManager.ProfileBackendInitialization actual;
        private BackendInitialization(SaveManager.ProfileBackendInitialization actual){this.actual=actual;}
        public boolean isFor(SaveManager manager,com.openggf.game.GameId game){return actual.owner==manager&&actual.game==game;}
    }
    public static BackendInitialization initializeBackend(SaveManager manager,com.openggf.game.GameId game)throws java.io.IOException{
        Objects.requireNonNull(manager);Objects.requireNonNull(game);
        return new BackendInitialization(manager.initializeProfileBackend(game));
    }
    // END original boot backend initialization.
    private SaveProfileAccess(){}
    public static Lookup lookup(SaveManager manager,String game,int slot){
        Objects.requireNonNull(manager);Objects.requireNonNull(game);return new Lookup(manager.profileLookup(game,slot));
    }
    /** Synchronous exclusive publication, not capture/async enqueue/flush as a success substitute. */
    public static Creation createIfMissing(Lookup actualMissing,Map<String,Object> payload){
        Objects.requireNonNull(actualMissing);Objects.requireNonNull(payload);
        return new Creation(actualMissing.actual.owner.profileCreate(actualMissing.actual,payload));
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
