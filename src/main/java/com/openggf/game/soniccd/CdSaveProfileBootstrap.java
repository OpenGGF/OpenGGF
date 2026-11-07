package com.openggf.game.soniccd;

import com.openggf.game.save.*;
import com.openggf.game.soniccd.media.CdInitialSaveAssets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Game-owned profile bootstrap; deliberately unconnected to Module/Core/World/stock boot.
 * Actual selected-slot read -> valid historical profile OR proven MISSING -> original InitSaveData
 * projection -> successful synchronous exclusive publication. No corrupt/error/default substitution.
 * Native: Initialization/Main.asm:79-105; Main CPU Functions.asm:223-244; IPX.asm:1420-1475.
 */
final class CdSaveProfileBootstrap {
    enum Disposition { EXISTING_VALID, CREATED, CREATED_CLEANUP_FAILED, STORAGE_COMPLETION_UNKNOWN, HOST_STORAGE_FAILURE, CORRUPT, QUARANTINED_HISTORY, UNKNOWN_HISTORY, CREATION_RECHECK_FAILED, HASH_WARNING, UNAVAILABLE, READ_ERROR,
                       INVALID_PROFILE, CREATE_CONFLICT, CREATION_REJECTED, WRITE_FAILED, TEMPORARY_OWNER_UNAVAILABLE }
    enum Origin { EXISTING, CREATED }
    enum NextNativeOperation { INITIALIZATION_RDSAVE, INITIALIZATION_WRSAVE,
                               INITIALIZATION_WRSAVE_AFTER_STORAGE_FAILURE, HOST_TEMPORARY_CLEANUP, NONE }
    static final class Receipt {
        private final CdSaveProfileBootstrap owner;private final int slot;private final CdSaveProfile profile;private final Origin origin;
        private Receipt(CdSaveProfileBootstrap owner,int slot,CdSaveProfile profile,Origin origin){this.owner=owner;this.slot=slot;this.profile=profile;this.origin=origin;}
        int slot(){return slot;}CdSaveProfile profile(){return profile;}Origin origin(){return origin;}
    }
    static final class Result {
        private final Disposition disposition;private final Receipt receipt;private final CdSaveProfile prefix;
        private final Throwable failure,quarantineFailure;private final Throwable cleanupFailure;private final boolean persistentPublished;private final OptionalInt policy;private final NextNativeOperation next;
        private Result(Disposition disposition,Receipt receipt,CdSaveProfile prefix,Throwable failure,Throwable quarantineFailure,OptionalInt policy,NextNativeOperation next){
            this(disposition,receipt,prefix,failure,quarantineFailure,policy,next,null,false);
        }
        private Result(Disposition disposition,Receipt receipt,CdSaveProfile prefix,Throwable failure,Throwable quarantineFailure,OptionalInt policy,NextNativeOperation next,Throwable cleanupFailure,boolean persistentPublished){
            this.disposition=disposition;this.receipt=receipt;this.prefix=prefix;this.failure=failure;this.quarantineFailure=quarantineFailure;this.policy=policy;this.next=next;this.cleanupFailure=cleanupFailure;this.persistentPublished=persistentPublished;
        }
        Disposition disposition(){return disposition;}Optional<Receipt> receipt(){return Optional.ofNullable(receipt);}
        Optional<CdSaveProfile> preparedPrefix(){return Optional.ofNullable(prefix);}Optional<Throwable> failure(){return Optional.ofNullable(failure);}
        Optional<Throwable> quarantineFailure(){return Optional.ofNullable(quarantineFailure);}
        Optional<Throwable> cleanupFailure(){return Optional.ofNullable(cleanupFailure);}boolean persistentPublished(){return persistentPublished;}
        Optional<Boolean> persistentPublication(){return disposition==Disposition.STORAGE_COMPLETION_UNKNOWN?Optional.empty():Optional.of(persistentPublished);}
        OptionalInt nativeSaveDisabledAfterStorage(){return policy;}NextNativeOperation nextNativeOperation(){return next;}
    }
    private static final String GAME_CODE=com.openggf.game.GameId.SCD.code();
    private final SaveManager manager;private final CdInitialSaveAssets source;
    private final AtomicBoolean running=new AtomicBoolean();private volatile Receipt current;
    private CdSaveProfileBootstrap(SaveManager manager,CdInitialSaveAssets source){this.manager=Objects.requireNonNull(manager);this.source=Objects.requireNonNull(source);Objects.requireNonNull(source.identity());}
    static CdSaveProfileBootstrap bind(SaveManager manager,CdInitialSaveAssets source){return new CdSaveProfileBootstrap(manager,source);}
    /** Genuine service selection only. Does not accept caller summaries, payloads or completion claims. */
    Result openSelectedSlot(int slot){
        if(slot<0)throw new IllegalArgumentException("Selected semantic slot must be nonnegative");
        if(!running.compareAndSet(false,true))throw new IllegalStateException("Profile bootstrap already active");
        try {
            current=null;
            var observed=SaveProfileAccess.lookup(manager,GAME_CODE,slot);
            switch(observed.disposition()){
                case VALID -> {
                    final CdSaveProfile profile;
                    try {profile=CdSaveProfile.decode(source,observed.payload());}
                    catch(IllegalArgumentException invalid){return failure(Disposition.INVALID_PROFILE,null,invalid,null,OptionalInt.empty(),NextNativeOperation.NONE);}
                    var receipt=new Receipt(this,slot,profile,Origin.EXISTING);
                    // Initialization/Main.asm86: policy0, then LOCAL RDSAVE (Main CPU Functions.asm522), not IPX ReadSaveData.
                    var result=new Result(Disposition.EXISTING_VALID,receipt,null,null,null,OptionalInt.of(0),NextNativeOperation.INITIALIZATION_RDSAVE);
                    current=receipt;return result;
                }
                case MISSING -> {
                    // This is the only service edge that may consume the authenticated initial template.
                    var prepared=CdSaveProfile.fromTemplate(source);
                    var creation=SaveProfileAccess.createIfMissing(observed,prepared.encode());
                    if(creation.disposition()==SaveProfileAccess.CreateDisposition.CREATED){
                        var receipt=new Receipt(this,slot,prepared,Origin.CREATED);
                        // Initialization/Main.asm100-102: policy0, then LOCAL WRSAVE (Main CPU Functions.asm545), not IPX progress stores.
                        var result=new Result(Disposition.CREATED,receipt,prepared,null,null,OptionalInt.of(0),NextNativeOperation.INITIALIZATION_WRSAVE,null,true);
                        current=receipt;return result;
                    }
                    if(creation.disposition()==SaveProfileAccess.CreateDisposition.COMPLETION_UNKNOWN)
                        return failure(Disposition.STORAGE_COMPLETION_UNKNOWN,prepared,creation.failure().orElse(null),null,OptionalInt.empty(),NextNativeOperation.NONE);
                    if(creation.disposition()==SaveProfileAccess.CreateDisposition.SUBMISSION_FAILED)
                        return failure(Disposition.HOST_STORAGE_FAILURE,prepared,creation.failure().orElse(null),null,OptionalInt.empty(),NextNativeOperation.NONE);
                    if(creation.disposition()==SaveProfileAccess.CreateDisposition.CREATED_CLEANUP_FAILED)
                        return new Result(Disposition.CREATED_CLEANUP_FAILED,null,prepared,creation.failure().orElse(null),null,OptionalInt.empty(),NextNativeOperation.HOST_TEMPORARY_CLEANUP,creation.cleanupFailure().orElse(null),true);
                    if(creation.disposition()==SaveProfileAccess.CreateDisposition.CONFLICT){
                        var rechecked=creation.recheckDisposition().orElse(null);
                        if(rechecked==SaveProfileAccess.Disposition.QUARANTINED_HISTORY||rechecked==SaveProfileAccess.Disposition.UNKNOWN_HISTORY||rechecked==SaveProfileAccess.Disposition.CORRUPT)
                            return failure(rechecked==SaveProfileAccess.Disposition.QUARANTINED_HISTORY?Disposition.QUARANTINED_HISTORY:rechecked==SaveProfileAccess.Disposition.UNKNOWN_HISTORY?Disposition.UNKNOWN_HISTORY:Disposition.CORRUPT,prepared,creation.failure().orElse(null),creation.quarantineFailure().orElse(null),OptionalInt.empty(),NextNativeOperation.NONE);
                        return new Result(Disposition.CREATE_CONFLICT,null,prepared,creation.failure().orElse(null),null,OptionalInt.empty(),NextNativeOperation.NONE,creation.cleanupFailure().orElse(null),false);
                    }
                    if(creation.disposition()==SaveProfileAccess.CreateDisposition.RECHECK_FAILED)
                        return failure(Disposition.CREATION_RECHECK_FAILED,prepared,creation.failure().orElse(null),creation.quarantineFailure().orElse(null),OptionalInt.empty(),NextNativeOperation.NONE);
                    if(creation.disposition()!=SaveProfileAccess.CreateDisposition.FAILED)
                        return failure(Disposition.CREATION_REJECTED,prepared,creation.failure().orElse(null),null,OptionalInt.empty(),NextNativeOperation.NONE);
                    if(!(creation.failure().orElse(null) instanceof java.io.IOException))
                        return new Result(Disposition.HOST_STORAGE_FAILURE,null,prepared,creation.failure().orElse(null),null,OptionalInt.empty(),NextNativeOperation.NONE,creation.cleanupFailure().orElse(null),false);
                    // Native95 policy1 does not complete local WRSAVE or establish the later Sub backend choice.
                    return new Result(Disposition.WRITE_FAILED,null,prepared,creation.failure().orElse(null),null,OptionalInt.of(1),NextNativeOperation.INITIALIZATION_WRSAVE_AFTER_STORAGE_FAILURE,creation.cleanupFailure().orElse(null),false);
                }
                case CORRUPT -> {return failure(Disposition.CORRUPT,null,observed.failure().orElse(null),observed.quarantineFailure().orElse(null),OptionalInt.empty(),NextNativeOperation.NONE);}
                case QUARANTINED_HISTORY -> {return failure(Disposition.QUARANTINED_HISTORY,null,observed.failure().orElse(null),null,OptionalInt.empty(),NextNativeOperation.NONE);}
                case UNKNOWN_HISTORY -> {return failure(Disposition.UNKNOWN_HISTORY,null,observed.failure().orElse(null),null,OptionalInt.empty(),NextNativeOperation.NONE);}
                case HASH_WARNING -> {return failure(Disposition.HASH_WARNING,null,null,null,OptionalInt.empty(),NextNativeOperation.NONE);}
                case UNAVAILABLE -> {return failure(Disposition.UNAVAILABLE,null,observed.failure().orElse(null),null,OptionalInt.empty(),NextNativeOperation.NONE);}
                case ERROR -> {return failure(Disposition.READ_ERROR,null,observed.failure().orElse(null),null,OptionalInt.empty(),NextNativeOperation.NONE);}
            }
            throw new IllegalStateException("Uncovered actual save disposition");
        } finally {running.set(false);}
    }
    /** There is no owned temporary-data history here; a missing owner never selects persistent storage. */
    Result openTemporary(){
        if(running.get())throw new IllegalStateException("Profile bootstrap already active");
        return failure(Disposition.TEMPORARY_OWNER_UNAVAILABLE,null,null,null,OptionalInt.empty(),NextNativeOperation.NONE);
    }
    boolean ownsReceipt(Receipt receipt){return receipt!=null&&receipt.owner==this&&receipt==current&&!running.get();}
    SaveSnapshotProvider snapshotProvider(Receipt receipt){requireCurrent(receipt);return new Provider(receipt);}
    private void requireCurrent(Receipt receipt){if(!ownsReceipt(receipt))throw new IllegalArgumentException("Not this service's current original storage receipt");}
    private static Result failure(Disposition kind,CdSaveProfile prefix,Throwable original,Throwable quarantine,OptionalInt policy,NextNativeOperation next){return new Result(kind,null,prefix,original,quarantine,policy,next);}
    private final class Provider implements SaveSnapshotProvider {
        private final Receipt receipt;private Provider(Receipt receipt){this.receipt=receipt;}
        @Override public Map<String,Object> capture(SaveReason reason,RuntimeSaveContext context){
            if(!running.compareAndSet(false,true))throw new IllegalStateException("Profile bootstrap already active");
            try {
                // Authenticate this matching operation; ordinary ownsReceipt correctly rejects running=true.
                if(receipt.owner!=CdSaveProfileBootstrap.this||receipt!=current)
                    throw new IllegalArgumentException("Not this service's current original storage receipt");
                Objects.requireNonNull(reason);Objects.requireNonNull(context);
                if(!GAME_CODE.equals(context.gameCode())||context.activeSlot().isEmpty()||context.activeSlot().getAsInt()!=receipt.slot)
                    throw new IllegalArgumentException("Snapshot context differs from actual selected CD profile");
                if(reason!=SaveReason.NEW_SLOT_START&&reason!=SaveReason.EXISTING_SLOT_LOAD)
                    throw new UnsupportedOperationException("Native live progress save producer is not owned by profile bootstrap");
                if((receipt.origin==Origin.CREATED)!=(reason==SaveReason.NEW_SLOT_START))
                    throw new IllegalArgumentException("Snapshot reason differs from actual created/retained profile origin");
                // A pure snapshot does not call storage, fabricate acknowledgement, or restore any game state.
                return receipt.profile.encode();
            } finally {running.set(false);}
        }
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
