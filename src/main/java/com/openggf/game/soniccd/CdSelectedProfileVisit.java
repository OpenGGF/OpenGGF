package com.openggf.game.soniccd;

import com.openggf.game.GameId;
import com.openggf.game.save.*;
import com.openggf.game.session.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

/** Actual selected engine-menu intent and finite pre-level native save/progress owner.
 * Not native Title, CPU/WordRAM/BIOS, clock, level admission or FULL. Data/captures never grant invocation. */
final class CdSelectedProfileVisit {
    enum Phase { PREPARED, INITIALIZATION_POLICY, INITIALIZED, IPX_READ, MENU_ENTRY, SAVE_FIELDS, PROGRAMME_ENTRY }
    enum Programme { R11A, R31A, LATER_ZONE, R41A }
    enum Call { INITIALIZATION_POLICY, LOCAL_SUB_POLICY_COPY, INITIALIZATION_RDSAVE, INITIALIZATION_WRSAVE, TEMPORARY_PREFIX_704,
        IPX_BOOT_READ, IPX_READ_FIELDS, INIT_SS2, GAME_LOOP_STORES, IPX_LOAD_READ, LOAD_GAME_STORES, NEW_GAME_STORES,
        IPX_WRITE_READ, IPX_WRITE_FIELDS, IPX_WRITE_ACK, RUN_R11_ENTRY, RUN_R31_ENTRY, LATER_ZONE_CONTINUATION_FENCE, RUN_R41_ENTRY }
    /** Only values actually written by IPX.GameLoop and the selected entry. Other globals stay unowned. */
    record Globals(OptionalInt zoneAct,OptionalInt lives,int score,int timeAttack,int specialStage,int checkpoint,
                   int rings,int time,int goodFuture,int projector,int period,OptionalInt plcLoadFlags,OptionalInt amyCaptured) {
        Globals { Objects.requireNonNull(zoneAct);Objects.requireNonNull(lives);Objects.requireNonNull(plcLoadFlags);Objects.requireNonNull(amyCaptured); }
        Globals level(int word,OptionalInt newLives,int plc,OptionalInt amy){return new Globals(OptionalInt.of(word),newLives,score,timeAttack,specialStage,checkpoint,rings,time,goodFuture,projector,period,OptionalInt.of(plc),amy);}
    }
    /** SPX.ResetSSFlags2:636-642, distinct from Main timeStones/progress. */
    record SpecialStageInitialization(int timeStonesSub,int stageId,int timer,int rings) { }
    record State(Phase phase,CdIpxProgress.State progress,Optional<CdSaveProfile> buffer,Optional<CdSaveProfile> temporary,
                 Optional<Globals> globals,List<Call> calls,Optional<Programme> programme,Optional<SpecialStageInitialization> subSpecial,OptionalInt localSubPolicyByte) {
        State { Objects.requireNonNull(phase);Objects.requireNonNull(progress);Objects.requireNonNull(buffer);Objects.requireNonNull(temporary);Objects.requireNonNull(globals);calls=List.copyOf(calls);Objects.requireNonNull(programme);Objects.requireNonNull(subSpecial);Objects.requireNonNull(localSubPolicyByte); }
    }
    static final class Prepared {
        private final CdGameDataSource source;private final SaveManager manager;private final int slot;
        private final SonicCdGameModule.ProfileEntry entry;private final CdSaveProfileBootstrap service;private final CdSaveProfileBootstrap.Result result;
        private Prepared(CdGameDataSource source,SaveManager manager,int slot,SonicCdGameModule.ProfileEntry entry,CdSaveProfileBootstrap service,CdSaveProfileBootstrap.Result result){this.source=source;this.manager=manager;this.slot=slot;this.entry=entry;this.service=service;this.result=result;}
        CdSaveProfileBootstrap.Result result(){return result;}
        boolean healthy(){return result.receipt().isPresent();}
    }
    static Prepared prepare(CdGameDataSource source,SaveManager manager,int slot,SonicCdGameModule.ProfileEntry entry){
        Objects.requireNonNull(source);Objects.requireNonNull(manager);Objects.requireNonNull(entry);
        if(slot<=0)throw new IllegalArgumentException("Actual positive frontend profile slot required");
        var assets=source.initialSaveAssets().orElseThrow(()->new IllegalArgumentException("Same-CUE original initial-save source is absent"));
        if(source.release().variant()!=CdReleaseVariant.USA||!assets.identity().observedWholeDiscSha256().equals(source.assets().identity().observedWholeDiscSha256()))throw new IllegalArgumentException("Actual same-disc USA profile source required");
        var service=CdSaveProfileBootstrap.bind(manager,assets);var result=service.openSelectedSlot(slot);
        return new Prepared(source,manager,slot,entry,service,result);
    }
    // BEGIN fresh original pre-storage boot bridge 20261107; old prepare4 remains exact.
    static Prepared prepare(CdGameDataSource source,SaveManager manager,int slot,SonicCdGameModule.ProfileEntry entry,CdOriginalBootPrefix boot){
        Objects.requireNonNull(source);Objects.requireNonNull(manager);Objects.requireNonNull(entry);Objects.requireNonNull(boot);
        if(slot<=0)throw new IllegalArgumentException("Actual positive frontend profile slot required");
        var assets=source.initialSaveAssets().orElseThrow(()->new IllegalArgumentException("Same-CUE original initial-save source is absent"));
        var service=CdSaveProfileBootstrap.bind(manager,assets);
        var result=boot.search(service,source,manager,slot,entry);
        return new Prepared(source,manager,slot,entry,service,result);
    }
    // END fresh original preparation bridge.
    enum Boundary { PROGRAMME_ENTRY, R11_LEVEL_START_LOADING, R41_LEVEL_START_LOADING }
    static final class Unowned extends UnsupportedOperationException {
        private final Programme programme;private final Boundary boundary;
        private Unowned(Programme programme){this(programme,Boundary.PROGRAMME_ENTRY);}
        private Unowned(Programme programme,Boundary boundary){super(boundary==Boundary.R11_LEVEL_START_LOADING?"ORIGINAL_R11_FIRST_SELECTOR_C_SELECTED_DISPLAY_AND_CURRENT_ART_OWNER (Main.WaitPLC:148-149); LevelStart prefix retained, not Ready":programme==Programme.LATER_ZONE?"SELECTED_LATER_ZONE_RUN_RX_SAVE_OWNER":"SELECTED_"+programme+"_PROGRAMME_ENTRY_OWNER: native cold-clock/level continuation unavailable");this.programme=programme;this.boundary=boundary;}
        Programme programme(){return programme;}Boundary boundary(){return boundary;}
    }

    static final class StorageStop extends UnsupportedOperationException {
        private final SaveProfileAccess.Disposition disposition;private final Throwable quarantineFailure;
        private StorageStop(SaveProfileAccess.Lookup actual){super("Actual selected profile read: "+actual.disposition(),actual.failure().orElse(null));disposition=actual.disposition();quarantineFailure=actual.quarantineFailure().orElse(null);}
        SaveProfileAccess.Disposition disposition(){return disposition;}
        Optional<Throwable> quarantineFailure(){return Optional.ofNullable(quarantineFailure);}
    }
    /** Original single publication, issued only inside this visit's private current invocation. */
    final class Publication {
        private final Object issuedScope;private final State before,after;private boolean applied;
        private boolean programmeHandoffCompleted; // Actual original callback only; never snapshot-restored.
        private Publication(State before,State after){issuedScope=scope;this.before=before;this.after=after;}
        State before(){return before;}State after(){return after;}
        void applyNative(SonicCdGameModule expected){requirePublication(expected,this);if(applied)throw new IllegalStateException("Already published selected prefix");state=after;applied=true;}
    }
    interface Port { void publish(Publication original); }
    static final class Snapshot {
        private final CdSelectedProfileVisit owner;private final WorldSession world;private final Object lifetime;private final long ioOrdinal;private final State state;
        private Snapshot(CdSelectedProfileVisit owner){this.owner=owner;world=owner.world;lifetime=owner.lifetime;ioOrdinal=owner.ioOrdinal;state=owner.state;}
        State state(){return state;}
    }
    private final SonicCdGameModule owner;private final Prepared prepared;private final Object invocation;private final Object lifetime=new Object();
    private CdOriginalBootPrefix originalBoot; // Only the new ctor4 binds this actual preparation.
    private State state=new State(Phase.PREPARED,CdIpxProgress.unknown(),Optional.empty(),Optional.empty(),Optional.empty(),List.of(),Optional.empty(),Optional.empty(),OptionalInt.empty());
    private WorldSession world;private boolean openingConsumed,running,failed,retired;private Object scope;private Thread thread;private Publication pending;private Port port;private long ioOrdinal;private Boundary programmeBoundary=Boundary.PROGRAMME_ENTRY;
    CdSelectedProfileVisit(SonicCdGameModule owner,Prepared prepared,Object invocation){this.owner=Objects.requireNonNull(owner);this.invocation=Objects.requireNonNull(invocation);this.prepared=Objects.requireNonNull(prepared);if(!prepared.healthy()||!prepared.service.ownsReceipt(prepared.result.receipt().orElseThrow()))throw new IllegalArgumentException("Original current selected storage receipt required");}
    // BEGIN fresh original boot/private applied-publication cuts 20261107.
    CdSelectedProfileVisit(SonicCdGameModule owner,Prepared prepared,Object invocation,CdOriginalBootPrefix boot){
        this(owner,prepared,invocation);originalBoot=Objects.requireNonNull(boot);boot.bindVisit(owner,this,invocation,prepared);
        state=new State(state.phase(),boot.state().progress(),state.buffer(),state.temporary(),state.globals(),state.calls(),state.programme(),state.subSpecial(),state.localSubPolicyByte());
    }
    private void requireApplied(SonicCdGameModule expected,Publication original,Call call){
        requireWorld(world);
        if(expected!=owner||!running||failed||pending!=original||scope==null||original.issuedScope!=scope||thread!=Thread.currentThread()
                ||!original.applied||state!=original.after||state.calls().isEmpty()||state.calls().getLast()!=call)
            throw new IllegalStateException("No original applied selected publication at this native cut");
    }
    void requireInitializationReturn(SonicCdGameModule expected,Publication original){
        requireApplied(expected,original,Call.TEMPORARY_PREFIX_704);
        if(state.phase()!=Phase.INITIALIZED)throw new IllegalStateException("Not the initialization return cut");
    }
    void requireProgrammeHandoff(SonicCdGameModule expected,Publication original){requireProgrammeHandoff(expected,original,Programme.R31A);}
    void requireProgrammeHandoff(SonicCdGameModule expected,Publication original,Programme target){
        if(target!=Programme.R11A&&target!=Programme.R31A&&target!=Programme.R41A)throw new IllegalArgumentException("No native entry for this target");
        requireApplied(expected,original,target==Programme.R41A?Call.RUN_R41_ENTRY:target==Programme.R31A?Call.RUN_R31_ENTRY:Call.RUN_R11_ENTRY);
        var g=state.globals().orElseThrow();
        if(state.phase()!=Phase.PROGRAMME_ENTRY||state.programme().orElseThrow()!=target||g.zoneAct().orElse(-1)!=(target==Programme.R41A?0x200:target==Programme.R31A?0x100:0)
                ||g.period()!=1||(target==Programme.R31A&&g.amyCaptured().orElse(-1)!=0)||original.programmeHandoffCompleted)
            throw new IllegalStateException("Not the original selected load publication");
    }
    void completeProgrammeHandoff(SonicCdGameModule expected,Publication original){requireProgrammeHandoff(expected,original);original.programmeHandoffCompleted=true;}
    void adoptR11StartAfterNative(SonicCdGameModule expected,Publication original){
        if(expected!=owner||!running||failed||pending!=original||scope==null||original.issuedScope!=scope||thread!=Thread.currentThread()||!original.applied||state!=original.after||original.programmeHandoffCompleted||state.programme().orElseThrow()!=Programme.R11A)
            throw new IllegalStateException("Original prevalidated R11 publication expired");
        original.programmeHandoffCompleted=true;programmeBoundary=Boundary.R11_LEVEL_START_LOADING;
    }
    void adoptR41StartAfterNative(SonicCdGameModule expected,Publication original){
        requireProgrammeHandoff(expected,original,Programme.R41A);
        original.programmeHandoffCompleted=true;programmeBoundary=Boundary.R41_LEVEL_START_LOADING;
    }
    GameplayModeContext openSession(SonicCdGameModule expected,Object originalInvocation){
        if(expected!=owner||originalInvocation!=invocation||openingConsumed||failed||retired||running||world!=null||state.phase()!=Phase.PREPARED||SessionManager.getCurrentWorldSession()!=null)
            throw new IllegalStateException("Original fresh selected session already opened or unavailable");
        // Consume before the first fallible opening. A partially opened or retired first
        // World cannot license a second attempt from this original preparation.
        openingConsumed=true;
        try {
            var mode=SessionManager.openGameplaySession(owner,owner,prepared.source,SaveSessionContext.forSlot(GameId.SCD.code(),prepared.slot,new SelectedTeam("sonic",List.of()),0,0));
            world=mode.getWorldSession();requireWorld(world);
            if(originalBoot!=null)originalBoot.bindWorld(owner,world,invocation);
            return mode;
        }catch(RuntimeException|Error original){failed=true;if(originalBoot!=null)originalBoot.retire(world);throw original;}
    }
    // END fresh original boot/private applied-publication cuts.
    State state(){return state;}
    CdGameDataSource source(){return prepared.source;}
    private void requireWorld(WorldSession actual){
        if(!owner.ownsSelectedProfileVisit(this)||retired||actual==null||actual!=SessionManager.getCurrentWorldSession()||SessionManager.getCurrentGameplayMode()==null||SessionManager.getCurrentGameplayMode().getWorldSession()!=actual
                ||actual.rootGameModule()!=owner||actual.resolvedGameModule()!=owner||actual.getDataSource()!=prepared.source)throw new IllegalStateException("Actual selected new Module/source/World lifetime required");
        var c=actual.getSaveSessionContext();if(c==null||!GameId.SCD.code().equals(c.gameCode())||c.activeSlot().isEmpty()||c.activeSlot().getAsInt()!=prepared.slot)throw new IllegalStateException("Actual final selected save context differs");
        if(world!=null&&world!=actual)throw new IllegalStateException("Selected profile cannot rebind another World");
    }
    void requirePublication(SonicCdGameModule expected,Publication original){
        requireWorld(world);
        if(expected!=owner||!running||failed||pending!=original||scope==null||original.issuedScope!=scope||thread!=Thread.currentThread()||original.before!=state||original.applied)throw new IllegalStateException("Expired/foreign selected prefix publication");
    }
    void run(WorldSession actual,Port actualPort,Object originalInvocation){
        if(originalInvocation!=invocation)throw new IllegalStateException("Not the Modules original selected-entry invocation");
        requireWorld(actual);if(failed||running)throw new IllegalStateException("Selected visit failed/active");
        if(state.phase()==Phase.PROGRAMME_ENTRY)throw new Unowned(state.programme().orElseThrow(),programmeBoundary);
        if(state.phase()!=Phase.PREPARED||!prepared.service.ownsReceipt(prepared.result.receipt().orElseThrow()))throw new IllegalStateException("Selected visit cannot replay a partial attempt");
        world=actual;port=Objects.requireNonNull(actualPort);scope=new Object();thread=Thread.currentThread();running=true;
        try {
            // Initialization/Main86/101: source-selected healthy storage branch writes Main policy0.
            int policy=prepared.result.nativeSaveDisabledAfterStorage().orElseThrow();if(policy!=0)throw new IllegalStateException("Healthy original initialization policy differs");
            var p=state.progress();p=new CdIpxProgress.State(p.savedLevel(),p.goodFuturesByte(),p.titleFlagsByte(),p.timeAttackUnlockByte(),p.unknownBackupByte(),p.currentSpecialStageByte(),p.timeStonesByte(),OptionalInt.of(policy));
            update(Phase.INITIALIZATION_POLICY,p,Optional.of(prepared.result.receipt().orElseThrow().profile()),state.temporary(),state.globals(),Call.INITIALIZATION_POLICY,state.programme());
            // RunBuRAMCmd:284 copies the actual Main byte; Sub tests this distinct whole byte.
            var localCalls=new ArrayList<>(state.calls());localCalls.add(Call.LOCAL_SUB_POLICY_COPY);
            publish(new State(state.phase(),state.progress(),state.buffer(),state.temporary(),state.globals(),localCalls,state.programme(),state.subSpecial(),OptionalInt.of(policy)));
            if(state.localSubPolicyByte().orElseThrow()!=0)throw new UnsupportedOperationException("Actual Sub temporary/backend policy owner unavailable");
            if(prepared.result.nextNativeOperation()==CdSaveProfileBootstrap.NextNativeOperation.INITIALIZATION_RDSAVE)read(Call.INITIALIZATION_RDSAVE);
            else if(prepared.result.nextNativeOperation()==CdSaveProfileBootstrap.NextNativeOperation.INITIALIZATION_WRSAVE){write(state.buffer().orElseThrow());update(Phase.INITIALIZED,state.progress(),state.buffer(),state.temporary(),state.globals(),Call.INITIALIZATION_WRSAVE,state.programme());}
            else throw new IllegalStateException("Healthy receipt lacks its original initialization-local operation");
            // First known cache after-image: native copies704 bytes. Its unobserved2-byte tail
            // and independent noncopied policy knowledge remain UNKNOWN, never borrowed from the file.
            var temp=state.buffer().orElseThrow().withTail(List.of(OptionalInt.empty(),OptionalInt.empty())).withPolicy(OptionalInt.empty());
            update(Phase.INITIALIZED,state.progress(),state.buffer(),Optional.of(temp),state.globals(),Call.TEMPORARY_PREFIX_704,state.programme());
            read(Call.IPX_BOOT_READ);projectRead();
            // Actual INITSS2 semantic owner: four source stores, no Main progress/clock read.
            var resetCalls=new ArrayList<>(state.calls());resetCalls.add(Call.INIT_SS2);
            publish(new State(state.phase(),state.progress(),state.buffer(),state.temporary(),state.globals(),resetCalls,state.programme(),Optional.of(new SpecialStageInitialization(0,0,0,0)),state.localSubPolicyByte()));
            var globals=new Globals(OptionalInt.empty(),OptionalInt.empty(),0,0,0,0,0,0,0,0,1,OptionalInt.empty(),OptionalInt.empty());
            update(Phase.MENU_ENTRY,state.progress(),state.buffer(),state.temporary(),Optional.of(globals),Call.GAME_LOOP_STORES,state.programme());
            boolean newGame=prepared.entry==SonicCdGameModule.ProfileEntry.NEW_GAME;
            if(!newGame){
                read(Call.IPX_LOAD_READ);projectRead();int raw=state.progress().savedLevel().word().orElseThrow();
                globals=state.globals().orElseThrow().level(raw,OptionalInt.of(3),0,OptionalInt.empty());
                update(Phase.MENU_ENTRY,state.progress(),state.buffer(),state.temporary(),Optional.of(globals),Call.LOAD_GAME_STORES,state.programme());
                int zone=raw>>>8;newGame=zone==0||zone>6;
                if(!newGame&&zone==2&&originalBoot!=null){
                    // IPX.LoadGame147-148 -> RunR4:212 -> RunR41:407-410 -> RunLevel542-547.
                    // Read/write the ACTUAL save before replacing zoneAct's low unlock byte.
                    // Unlike RunR3, this path never writes Amy's captured flag.
                    save(CdIpxProgress.SaveSite.RUN_R4);
                    globals=state.globals().orElseThrow().level(0x200,state.globals().orElseThrow().lives(),0,OptionalInt.empty());
                    finish(Programme.R41A,globals,Call.RUN_R41_ENTRY);return;
                }
                if(!newGame&&zone!=1){finish(Programme.LATER_ZONE,globals,Call.LATER_ZONE_CONTINUATION_FENCE);}
            }
            if(newGame){
                var next=CdIpxProgress.newGameStores(state.progress());globals=state.globals().orElseThrow().level(0,state.globals().orElseThrow().lives(),0,OptionalInt.empty());
                update(Phase.MENU_ENTRY,next.progress(),state.buffer(),state.temporary(),Optional.of(globals),Call.NEW_GAME_STORES,state.programme());
                save(CdIpxProgress.SaveSite.NEW_GAME);finish(Programme.R11A,globals,Call.RUN_R11_ENTRY);return;
            }
            save(CdIpxProgress.SaveSite.RUN_R3);globals=state.globals().orElseThrow().level(0x100,state.globals().orElseThrow().lives(),0,OptionalInt.of(0));finish(Programme.R31A,globals,Call.RUN_R31_ENTRY);
        } catch(Unowned original){throw original;}
        catch(IOException original){failed=true;throw new UncheckedIOException("Actual selected synchronous save did not return",original);}
        catch(RuntimeException|Error original){failed=true;throw original;}
        finally{pending=null;port=null;scope=null;thread=null;running=false;if(failed&&originalBoot!=null)originalBoot.retire(world);}
    }
    private void projectRead(){update(Phase.IPX_READ,state.buffer().orElseThrow().projectAfterRead(state.progress()),state.buffer(),state.temporary(),state.globals(),Call.IPX_READ_FIELDS,state.programme());}
    private void read(Call call){
        requireWorld(world);ioOrdinal++;var actual=SaveProfileAccess.lookup(prepared.manager,GameId.SCD.code(),prepared.slot);requireWorld(world);
        if(actual.disposition()!=SaveProfileAccess.Disposition.VALID)throw new StorageStop(actual);
        var profile=CdSaveProfile.decode(prepared.source.initialSaveAssets().orElseThrow(),actual.payload());
        update(state.phase(),state.progress(),Optional.of(profile),state.temporary(),state.globals(),call,state.programme());
    }
    private void write(CdSaveProfile value)throws IOException{requireWorld(world);var payload=value.encode();ioOrdinal++;prepared.manager.writeSlot(GameId.SCD.code(),prepared.slot,payload);requireWorld(world);}
    private void save(CdIpxProgress.SaveSite site)throws IOException{
        var requested=CdIpxProgress.requestSaveRead(state.progress(),site);
        if(requested.missingRead().isPresent()||requested.request().orElseThrow().mode()!=CdIpxProgress.SaveMode.BACKUP)throw new UnsupportedOperationException("Actual selected save read policy owner unavailable");
        read(Call.IPX_WRITE_READ); // Buffer retention only: writing-path read NEVER hydrates live progress.
        var fields=CdIpxProgress.prepareWriteSaveAfterRead(state.progress(),site);
        if(fields.missingRead().isPresent())throw new UnsupportedOperationException("Actual selected save field owner: "+fields.missingRead().orElseThrow());
        var patched=state.buffer().orElseThrow().withPatch(fields.patch());
        update(Phase.SAVE_FIELDS,state.progress(),Optional.of(patched),state.temporary(),state.globals(),Call.IPX_WRITE_FIELDS,state.programme());
        var write=CdIpxProgress.requestSaveWriteAfterFields(state.progress(),site,fields.patch());
        if(write.missingRead().isPresent()||write.request().orElseThrow().mode()!=CdIpxProgress.SaveMode.BACKUP)throw new UnsupportedOperationException("Actual selected post-field write policy owner unavailable");
        write(patched);update(Phase.SAVE_FIELDS,state.progress(),state.buffer(),state.temporary(),state.globals(),Call.IPX_WRITE_ACK,state.programme());
    }
    private void finish(Programme target,Globals globals,Call call){var publication=update(Phase.PROGRAMME_ENTRY,state.progress(),state.buffer(),state.temporary(),Optional.of(globals),call,Optional.of(target));if(publication.programmeHandoffCompleted&&owner.originalSelectedSourceLoadingAssembled(publication))return;if(target==Programme.R11A&&publication.programmeHandoffCompleted){if(owner.originalR11EngineLoadingAssembled())return;throw new Unowned(target,programmeBoundary);}if(target!=Programme.R31A||!publication.programmeHandoffCompleted)throw new Unowned(target);}
    private Publication update(Phase phase,CdIpxProgress.State progress,Optional<CdSaveProfile> buffer,Optional<CdSaveProfile> temporary,Optional<Globals> globals,Call call,Optional<Programme> programme){
        var calls=new ArrayList<>(state.calls());calls.add(call);return publish(new State(phase,progress,buffer,temporary,globals,calls,programme,state.subSpecial(),state.localSubPolicyByte()));
    }
    private Publication publish(State next){var original=new Publication(state,next);pending=original;
        try{port.publish(original);if(!original.applied||state!=next)throw new IllegalStateException("Original selected prefix was not adopted");}finally{pending=null;}
        return original;
    }
    Snapshot capture(WorldSession actual){requireWorld(actual);if(running||failed||state.phase()!=Phase.PROGRAMME_ENTRY)throw new IllegalStateException("Selected capture requires healthy completed pre-level boundary");return new Snapshot(this);}
    void validateRestore(Snapshot snapshot,WorldSession actual){requireWorld(actual);if(snapshot==null||snapshot.owner!=this||snapshot.world!=world||snapshot.lifetime!=lifetime||snapshot.ioOrdinal!=ioOrdinal)throw new IllegalArgumentException("Foreign selected profile/source/World/I-O lifetime capture");if(running||failed||state.phase()!=Phase.PROGRAMME_ENTRY||snapshot.state.phase()!=Phase.PROGRAMME_ENTRY)throw new IllegalStateException("Cannot restore an active/failed/pending save operation");}
    void restore(Snapshot snapshot,WorldSession actual){validateRestore(snapshot,actual);state=snapshot.state;}
    void retire(WorldSession actual){if(world==actual){retired=true;}}
    SaveSnapshotProvider genericProvider(){return new SaveSnapshotProvider(){
        @Override public Map<String,Object> capture(SaveReason reason,RuntimeSaveContext context){requireWorld(world);throw new UnsupportedOperationException("Generic save reasons do not own an original CD native save invocation");}
        @Override public boolean restoreProgress(com.openggf.game.GameStateManager gameState,int lives,int continues,Map<String,Object> payload){requireWorld(world);throw new UnsupportedOperationException("Generic cartridge restore is not a CD profile entry");}
    };}
}
