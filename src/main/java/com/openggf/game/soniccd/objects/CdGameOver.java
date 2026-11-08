package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR13FutureAssets;
import com.openggf.game.soniccd.level.CdR13ObjectAssets;
import com.openggf.game.soniccd.media.CdMmdImage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * ObjGameOver ($3B): Level/_Objects/Game Over/Main.asm:7-118 at scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * as included by Palmtree Panic/Act 1 Present.asm and executed over the live dynamic pool of one
 * CdObjectPlacementRuntime. R11A sets R43_LEGACY_DATA_PREFIX 0 (Act 1 Present.asm:9) and leaves R3_SEMANTIC_GAME_OVER
 * undefined, so the helpers are plain FindObjSlot, DeleteObject and DrawObject (Level End/Main.asm:36-47). One
 * {@link #run} is one RunObjects dispatch through the two-entry .Index (:19-21): Init (:27-88) or Main (:92-112).
 *
 * <p>Init requests SCMD_FADEPCM, commits its own slot writes, then stores powerup and timeOver through the caller's
 * port at their source cuts. Lives remaining without a time over delete the slot: no allocation, music or draw.
 * Otherwise BSET #7,powerup precedes FindObjSlot. A full pool deletes the parent, again with no music or draw. A free
 * slot receives the "OVER" child; a later physical slot executes in this same RunObjects pass through the caller's
 * live walk, an earlier one waits a frame. Then lives remaining fall into Main, while zero lives request
 * SCMD_GMOVERMUS and return. Main moves oX eight pixels toward oVar2A by a SIGNED comparison and tail-calls DrawObject.
 * Every unwritten byte of both slots keeps its contents. Nothing is keyed on a fixture, frame, route or coordinate.</p>
 *
 * <p>As in the other CD bodies, a port or draw failure propagates with completed writes retained; nothing is rolled
 * back or retried and no Result is published, so the frame root closes admission. This pure body owns no art, V-int
 * LoadShieldArt service, mapping resolver, renderer, sub-CPU handshake or world dispatch, and is no playback claim.</p>
 */
public final class CdGameOver {
    public static final int OBJECT_ID = 0x3B;
    /** MOVE.W #$8544,oTile(a0) (:35): priority, palette 0, tile $544 (VRAM $A880). */
    public static final int NATIVE_TILE_WORD = 0x8544;
    /** MOVE.W #96+128,oYScr (:32,81): absolute screen Y, stored as the WORD at $0A. */
    private static final int SCREEN_Y_WORD = 96 + 128;
    /** MOVE.W #0+128,oX(a0) (:33) and MOVE.W #320+128,oX(a1) (:82). */
    private static final int GAME_START_X = 0 + 128;
    private static final int OVER_START_X = 320 + 128;
    /** MOVE.W #160+128,oGmOverDestX (:34,83). */
    private static final int DESTINATION_X = 160 + 128;
    /** MOVE.B #1,oMapFrame(a1) (:80): the second entry of the selected two-word table. */
    private static final int OVER_MAP_FRAME = 1;
    /** MOVE.B #8,powerup (:38), ADDQ.B #2,powerup (:44), BSET #7,powerup (:52). */
    private static final int POWERUP_GAME_OVER = 8;
    private static final int POWERUP_TIME_OVER_STEP = 2;
    private static final int POWERUP_LOAD_BIT = 0x80;
    /** MOVEQ #8,d0 (:93). */
    private static final int SPEED = 8;

    /** INIT precedes MAIN in one dispatch when lives remain after a spawned child (:85-86). */
    public enum NativeCall { INIT, MAIN, DRAW_OBJECT }

    /** MOVE.W #SCMD_x,d0 / JSR or JMP SubCPUCmd. Values count every RS.B from RSSET 1 (_Include/System.inc:144). */
    public enum SubCpuCommand {
        /** System.inc:274, the 130th value including the unnamed entry at :183 (Game Over/Main.asm:28). */
        SCMD_FADEPCM(0x82),
        /** System.inc:254, the 110th value (Game Over/Main.asm:87). */
        SCMD_GMOVERMUS(0x6E);

        private final int commandWord;
        SubCpuCommand(int commandWord) { this.commandWord = commandWord; }
        public int commandWord() { return commandWord; }
    }

    /** Which source branch reached .Destroy (:64-73). */
    public enum DeleteEntry {
        /** .NotTimeOver: TST.B lives / BNE.S .Destroy (:47-49). */
        LIVES_REMAIN,
        /** .GameOver: FindObjSlot returned NE and BEQ.S .SpawnOverText fell through (:60-64). */
        NO_FREE_SLOT
    }

    /**
     * Ordered return metadata. OverTextCreated, OverTextSlotUnavailable, Deleted and CallReturned describe completed
     * pool work and must never be replayed; a child is dispatched only by the caller's live walk reading its ID. Global
     * stores were already made through the port. SubCpuRequest entries go to the caller's sub-CPU ledger in order; this
     * body performs no GACOMCMD0/GACOMSTAT0 handshake and no PCM or music playback.
     */
    public sealed interface Effect permits CallReturned, SubCpuRequest, OverTextCreated, OverTextSlotUnavailable, Deleted { }

    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    public record SubCpuRequest(SubCpuCommand command) implements Effect {
        public SubCpuRequest { Objects.requireNonNull(command, "command"); }
    }
    /** .SpawnOverText (:75-83) committed every child write at this whole-pool slot. */
    public record OverTextCreated(int wholeIndex) implements Effect {
        public OverTextCreated {
            if (wholeIndex < CdObjectPool.FIRST_WHOLE_INDEX || wholeIndex >= CdObjectPool.END_WHOLE_INDEX) {
                throw new IllegalArgumentException("The OVER text lives in dynamic slots 32..127");
            }
        }
    }
    /** FindObjSlot returned NE: no child byte was written. */
    public record OverTextSlotUnavailable() implements Effect { }
    /** JMP DeleteObject (Object Functions.asm:228-237): the full 64-byte clear, with no saved-bank access. */
    public record Deleted(DeleteEntry entry) implements Effect {
        public Deleted { Objects.requireNonNull(entry, "entry"); }
    }

    /**
     * Native bytes lives ($FF1508), timeOver ($FF150C) and powerup ($FF156B) at one source cut
     * (_Include/Main CPU Variables.inc:21,56,59,120).
     */
    public record Globals(int lives, int powerup, int timeOver) {
        public Globals {
            byteValue(lives, "lives");
            byteValue(powerup, "powerup");
            byteValue(timeOver, "timeOver");
        }
    }

    /**
     * The caller's one live store of these bytes. read() returns all three after every earlier store of this dispatch;
     * the body reads at each TST.B, BCLR, ADDQ.B and BSET cut. Each write is one native byte store. There is
     * deliberately no lives writer: this object only tests lives.
     */
    public interface GlobalsPort {
        Globals read();
        /** One MOVE.B #8, ADDQ.B #2 or BSET #7 store to powerup. */
        void writePowerup(int value);
        /** One BCLR #0 store to timeOver; the byte is written back even when bit 0 was already clear. */
        void writeTimeOver(int value);
    }

    /**
     * Source DrawObject (Object Functions.asm:137-196) at the .Draw cut (Game Over/Main.asm:103-112), after Main's
     * write. The shared CPU queue implements it over the live pool: clear bit 7 of the live slot first, then the cull
     * and capacity tests, then append only the physical slot reference. A clean slot's sprite flags select the absolute
     * mode, whose later DrawObjects reads oX and the $0A screen-Y word. Any bit-7 write is reloaded into the Result. The
     * caller must keep this synchronous port non-reentrant: it must not dispatch another body.
     */
    @FunctionalInterface
    public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity slot);
    }

    /**
     * The raw shared MapSpr_GameOver/MapSpr_TimeOver include (Mappings.asm:1-58) and the native long Init stores in
     * oMap. Both tables are kept exactly: GAME's two signed relative words are based at +0 and TIME's at +4, so nothing
     * is compacted, rebased or presented as one uniform four-entry header. Validation is structural and bounded; it is
     * not media authentication. Production obtains the record through authenticated stage factories. A future
     * resolver/renderer owner reads {@link #mappings()} and the two addresses; this record resolves and draws nothing.
     */
    public record Assets(CdAssetBytes mappings, int gameOverMappingsAddress) {
        /** Eight header bytes and four twelve-byte bodies. */
        public static final int MAPPING_BYTES = 56;
        /** MapSpr_TimeOver follows MapSpr_GameOver's two words (Mappings.asm:4). */
        public static final int TIME_OVER_TABLE_OFFSET = 4;
        private static final int HEADER_BYTES = 8;

        public Assets {
            Objects.requireNonNull(mappings, "mappings");
            if (mappings.length() != MAPPING_BYTES) {
                throw new IllegalArgumentException("MapSpr_GameOver/MapSpr_TimeOver include extent");
            }
            if ((gameOverMappingsAddress & 1) != 0 || gameOverMappingsAddress < 0x200000
                    || gameOverMappingsAddress > 0x240000 - MAPPING_BYTES) {
                throw new IllegalArgumentException("oMap must be an even original Word RAM address holding the include");
            }
            validateTables(mappings);
        }

        /** MOVE.L #MapSpr_TimeOver,oMap(a0) (:43). */
        public int timeOverMappingsAddress() { return gameOverMappingsAddress + TIME_OVER_TABLE_OFFSET; }

        /**
         * The authenticated owner's lazy GAME_OVER_MAPPINGS view and its original Word RAM address. Only the USA R11A
         * original program is bound; other regions have no object-asset owner and no inferred offset.
         */
        public static Assets fromObjectAssets(CdR11aObjectAssets assets) {
            Objects.requireNonNull(assets, "assets");
            var identity = assets.identity();
            if (identity.variant() != CdReleaseVariant.USA || !identity.stage().equals(CdStageKey.parse("R11A"))
                    || identity.file() != CdNativeFile.R11A || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM) {
                throw new UnsupportedOperationException("ObjGameOver mappings are bound only for authenticated original USA R11A");
            }
            var view = assets.view(CdR11aObjectAssets.Table.GAME_OVER_MAPPINGS);
            return new Assets(view.bytes(), view.range().nativeAddress());
        }

        // BEGIN R13 AUTHENTIC GAME OVER ASSETS 20261107
        /** Actual C/D ID3B/Init operands and complete56 source; no selected art or runtime authority. */
        public static Assets fromAct3FutureAssets(CdR13FutureAssets assets,CdR13ObjectAssets owner){
            Objects.requireNonNull(assets,"actual R13 source");Objects.requireNonNull(owner,"actual R13 object metadata");
            if(!owner.ownsAssets(assets)||!owner.identity().equals(assets.identity()))throw new IllegalArgumentException("Different R13 GameOver metadata owner");
            var code=owner.view(CdR13ObjectAssets.View.GAME_OVER_CODE);int entry=CdR13ObjectAssets.View.GAME_OVER_CODE.range(assets.identity().file()).nativeAddress();
            var raw=owner.view(CdR13ObjectAssets.View.MAP_SPR_GAME_OVER);int root=CdR13ObjectAssets.View.MAP_SPR_GAME_OVER.range(assets.identity().file()).nativeAddress();
            if(owner.view(CdR13ObjectAssets.View.OBJECT_INDEX).unsignedLong((OBJECT_ID-1)*4)!=entry||code.unsignedWord(14)!=4||code.unsignedWord(16)!=0xBC
                    ||code.unsignedWord(0x38)!=0x217C||code.unsignedLong(0x3A)!=root||code.unsignedWord(0x3E)!=4
                    ||code.unsignedWord(0x5A)!=0x217C||code.unsignedLong(0x5C)!=root+4||code.unsignedWord(0x60)!=4)
                throw new IllegalArgumentException("R13 original GameOver dispatch/mapping operands differ");
            return new Assets(raw,root);
        }
        // END R13 AUTHENTIC GAME OVER ASSETS 20261107

        /**
         * Each table entry is a signed word relative to its OWN label. The four bodies must begin right after the
         * header, hold count + 5 * count bytes before the next distinct body or the end, keep at most one real zero
         * alignment byte, and use only admitted size nibbles.
         */
        private static void validateTables(CdAssetBytes bytes) {
            int[] bodies = new int[4];
            int first = MAPPING_BYTES;
            for (int entry = 0; entry < bodies.length; entry++) {
                int root = (entry >>> 1) * TIME_OVER_TABLE_OFFSET;
                int body = root + (short) bytes.unsignedWord(entry * 2);
                if (body < HEADER_BYTES || body >= MAPPING_BYTES) {
                    throw new IllegalArgumentException("Game/Time Over relative word leaves the include bodies");
                }
                bodies[entry] = body;
                first = Math.min(first, body);
            }
            if (first != HEADER_BYTES) {
                throw new IllegalArgumentException("Unreviewed gap after the Game/Time Over tables");
            }
            for (int start : bodies) {
                int end = MAPPING_BYTES;
                for (int other : bodies) {
                    if (other > start && other < end) { end = other; }
                }
                int count = bytes.unsignedByte(start);
                int length = 1 + count * 5;
                if (count > 128 || length > end - start) {
                    throw new IllegalArgumentException("Game/Time Over body crosses its next distinct body or the include end");
                }
                int tail = end - start - length;
                if (tail > 1 || (tail == 1 && bytes.unsignedByte(start + length) != 0)) {
                    throw new IllegalArgumentException("Unreviewed bytes after a Game/Time Over body");
                }
                for (int piece = 0; piece < count; piece++) {
                    if ((bytes.unsignedByte(start + 2 + piece * 5) & ~15) != 0) {
                        throw new IllegalArgumentException("Game/Time Over piece size outside the admitted nibble");
                    }
                }
            }
        }
    }

    /** Final live image of the dispatched slot after the whole dispatch (fully zero after a delete). */
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state, "state");
            effects = List.copyOf(effects);
        }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }

    public enum UnsupportedBoundary {
        /** oRoutine outside {0,2}: an odd word fetch or an index past the two-entry .Index (:12-21). */
        ROUTINE_DISPATCH
    }

    /** Thrown before any write, request or port call; the pool holds the last committed source state. */
    public static final class UnsupportedGameOver extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;

        private UnsupportedGameOver(UnsupportedBoundary boundary, int wholeIndex, String detail) {
            super(boundary + " at dynamic slot " + wholeIndex + ": " + detail);
            this.boundary = boundary;
            this.wholeIndex = wholeIndex;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
    }

    private final CdObjectPlacementRuntime runtime;
    private final Assets assets;
    private final DrawObjectCall drawObject;

    /** The runtime supplies the single live pool; no branch touches its saved bank. */
    public CdGameOver(CdObjectPlacementRuntime runtime, Assets assets, DrawObjectCall drawObject) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
    }

    public Assets assets() { return assets; }

    /** One ObjGameOver dispatch for the whole-pool dynamic slot currently holding ID $3B. */
    // BEGIN R13 ORIGINAL GAME OVER DISPATCH 20261107
    public Result run(int wholeIndex,GlobalsPort globals){
        if(observer==null)return runOriginal(wholeIndex,globals);
        if(running||failed)throw new IllegalStateException("GameOver source invocation active/retired");
        running=true;activeWhole=wholeIndex;ordinal=0;
        try{var result=runOriginal(wholeIndex,globals);var cut=runtime.capture();return observed(new CompletionCall(cut,source()),cut,r->result);}
        catch(RuntimeException|Error original){failed=true;throw original;}
        finally{running=false;pending=null;operationResult=null;resultReady=false;primitive=false;}
    }
    // END R13 ORIGINAL GAME OVER DISPATCH 20261107
    private Result runOriginal(int wholeIndex, GlobalsPort globals) {
        Objects.requireNonNull(globals, "globals");
        var pool = runtime.pool();
        var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) {
            throw new IllegalArgumentException("RunObjects dispatch error: slot " + wholeIndex
                    + " holds ID " + state.id() + ", not ObjGameOver $3B");
        }
        var effects = new ArrayList<Effect>();
        switch (state.routine()) {                                              // MOVE.B oRoutine(a0),d0 / JMP .Index
            case 0 -> init(state, globals, effects);
            case 2 -> main(state, effects);
            default -> throw new UnsupportedGameOver(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex,
                    "oRoutine " + state.routine() + " is not an ObjGameOver .Index entry");
        }
        return new Result(pool.state(wholeIndex), effects);
    }

    /** ObjGameOver_Init (Game Over/Main.asm:27-88). */
    private void init(CdObjectState state, GlobalsPort globals, List<Effect> effects) {
        var pool = runtime.pool();
        int whole = state.identity().wholeIndex();
        sound(effects,SubCpuCommand.SCMD_FADEPCM);              // MOVE.W #SCMD_FADEPCM,d0 / JSR SubCPUCmd
        write(state.addByte(ROUTINE, 2)                                     // ADDQ.B #2,oRoutine(a0)
                .moveWord(X_FRACTION, SCREEN_Y_WORD)                             // MOVE.W #96+128,oYScr(a0): the WORD at $0A
                .moveWord(X, GAME_START_X)                                       // MOVE.W #0+128,oX(a0)
                .moveWord(GAME_OVER_DEST_X, DESTINATION_X)                       // MOVE.W #160+128,oGmOverDestX(a0)
                .moveWord(TILE, NATIVE_TILE_WORD)                                // MOVE.W #$8544,oTile(a0)
                .moveLong(MAPPINGS, assets.gameOverMappingsAddress()));          // MOVE.L #MapSpr_GameOver,oMap(a0)
        writePowerup(globals,POWERUP_GAME_OVER);                                 // MOVE.B #8,powerup
        int timeOver = read(globals).timeOver();                                 // BCLR #0,timeOver: Z is the old bit 0
        writeTimeOver(globals,timeOver & 0xFE);
        if ((timeOver & 1) != 0) {                                               // BEQ.S .NotTimeOver not taken
            if (read(globals).lives() != 0) {                                    // TST.B lives / BEQ.S .GameOver
                write(pool.state(whole)
                        .moveLong(MAPPINGS, assets.timeOverMappingsAddress()));  // MOVE.L #MapSpr_TimeOver,oMap(a0)
                writePowerup(globals,(read(globals).powerup() + POWERUP_TIME_OVER_STEP) & 0xFF); // ADDQ.B #2,powerup
            }                                                                    // BRA.S .GameOver
        } else if (read(globals).lives() != 0) {                                 // .NotTimeOver: TST.B lives / BNE.S .Destroy
            delete(whole, DeleteEntry.LIVES_REMAIN, effects);
            effects.add(new CallReturned(NativeCall.INIT));
            return;
        }
        writePowerup(globals,read(globals).powerup() | POWERUP_LOAD_BIT);       // .GameOver: BSET #7,powerup
        var found = pool.findObjSlot();                                          // JSR FindObjSlot: first oID-zero slot
        if (found.isEmpty()) {                                                   // BEQ.S .SpawnOverText not taken
            effects.add(new OverTextSlotUnavailable());
            delete(whole, DeleteEntry.NO_FREE_SLOT, effects);                    // .Destroy: no music, no DrawObject
            effects.add(new CallReturned(NativeCall.INIT));
            return;
        }
        int child = found.getAsInt();
        var parent = pool.state(whole);
        write(pool.state(child)
                .moveByte(ID, OBJECT_ID)                                         // MOVE.B #$3B,oID(a1)
                .moveByte(ROUTINE, parent.routine())                             // MOVE.B oRoutine(a0),oRoutine(a1)
                .moveWord(TILE, parent.word(TILE))                               // MOVE.W oTile(a0),oTile(a1)
                .moveLong(MAPPINGS, parent.longBits(MAPPINGS))                   // MOVE.L oMap(a0),oMap(a1)
                .moveByte(MAP_FRAME, OVER_MAP_FRAME)                             // MOVE.B #1,oMapFrame(a1)
                .moveWord(X_FRACTION, SCREEN_Y_WORD)                             // MOVE.W #128+96,oYScr(a1)
                .moveWord(X, OVER_START_X)                                       // MOVE.W #320+128,oX(a1)
                .moveWord(GAME_OVER_DEST_X, DESTINATION_X));                     // MOVE.W #160+128,oGmOverDestX(a1)
        effects.add(new OverTextCreated(child));
        if (read(globals).lives() != 0) {                                        // TST.B lives / BNE.S ObjGameOver_Main
            effects.add(new CallReturned(NativeCall.INIT));
            main(pool.state(whole), effects);
            return;
        }
        sound(effects,SubCpuCommand.SCMD_GMOVERMUS);            // MOVE.W #SCMD_GMOVERMUS,d0 / JMP SubCPUCmd
        effects.add(new CallReturned(NativeCall.INIT));
    }

    /** ObjGameOver_Main (Game Over/Main.asm:92-112): no global is read or written. */
    private void main(CdObjectState state, List<Effect> effects) {
        var pool = runtime.pool();
        int destination = state.word(GAME_OVER_DEST_X);                          // MOVE.W oGmOverDestX(a0),d1
        int x = state.centreX();
        if (destination != x) {                                                  // CMP.W oX(a0),d1 / BEQ.S .Draw
            int d0 = (short) destination >= (short) x ? SPEED : -SPEED;          // BGE.S .AddX is signed / NEG.W d0
            state = state.moveWord(X, x + d0);                                   // .AddX: ADD.W d0,oX(a0): the word only
            write(state);
        }
        effects.add(new CallReturned(NativeCall.MAIN));
        draw(state.identity());                           // .Draw: JMP DrawObject
        effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
    }

    private void delete(int whole, DeleteEntry entry, List<Effect> effects) {
        deleteStore(whole);                                      // DeleteObject: all 16 longwords
        effects.add(new Deleted(entry));
    }

    private Globals read(GlobalsPort globals) {
        if(observer==null)return Objects.requireNonNull(globals.read(),"Game Over globals");
        var cut=runtime.capture();return observed(new ReadCall(cut,source()),cut,r->Objects.requireNonNull(globals.read(),"Game Over globals"));
    }

    // BEGIN R13 ORIGINAL GAME OVER OPERATION SEAM 20261107
    /** Opted-in R13 ports execute original requests synchronously. Returned Effect metadata is
     * comparison-only there; legacy constructor callers retain their existing after-return contract. */
    public record Publication(CdObjectPlacementRuntime.Snapshot cut,CdObjectState source,CdObjectState before,CdObjectState output){}
    public record ReadCall(CdObjectPlacementRuntime.Snapshot cut,CdObjectState source){}
    public enum Scalar { POWERUP, TIME_OVER }
    public record ScalarCall(CdObjectPlacementRuntime.Snapshot cut,CdObjectState source,Scalar field,int value){public ScalarCall{Objects.requireNonNull(field);byteValue(value,"GameOver scalar");}}
    public record SoundCall(CdObjectPlacementRuntime.Snapshot cut,CdObjectState source,SubCpuCommand command){public SoundCall{Objects.requireNonNull(command);}}
    public record DrawCall(CdObjectPlacementRuntime.Snapshot cut,CdObjectState source){}
    public record CompletionCall(CdObjectPlacementRuntime.Snapshot cut,CdObjectState source){}
    public interface DispatchObserver{void beforeOperation(Object call);void afterOperation(Object call);void failedOperation(Object call,Throwable original);}
    public interface ReceiptGuard{void prepared(Object call);void completed(Object call);}
    @FunctionalInterface public interface SubCpuPort{void command(SubCpuCommand command);}
    private DispatchObserver observer;private ReceiptGuard receiptGuard;private SubCpuPort subCpu;
    private boolean running,failed,primitive,resultReady;private int activeWhole;private long ordinal;private Object pending,operationResult;
    /** Same original algorithm over the exact actual placement/source/metadata. Legacy constructor is unchanged. */
    public static CdGameOver fromAct3FutureAssets(CdObjectPlacementRuntime runtime,CdR13FutureAssets assets,CdR13ObjectAssets owner,
            DrawObjectCall draw,SubCpuPort sound,DispatchObserver observer,ReceiptGuard guard){
        Objects.requireNonNull(runtime);Objects.requireNonNull(assets);if(!runtime.ownsAct3FutureAssets(assets))throw new IllegalArgumentException("Different R13 GameOver placement");
        return withOriginalOperations(runtime,Assets.fromAct3FutureAssets(assets,owner),draw,sound,observer,guard);
    }
    /** One shared native body. Assets are comparison DATA; only its private current call and the
     * source-bound caller's guards admit an operation. This factory grants no World authority. */
    public static CdGameOver withOriginalOperations(CdObjectPlacementRuntime runtime,Assets assets,
            DrawObjectCall draw,SubCpuPort sound,DispatchObserver observer,ReceiptGuard guard){
        var result=new CdGameOver(runtime,assets,draw);result.subCpu=Objects.requireNonNull(sound);result.observer=Objects.requireNonNull(observer);result.receiptGuard=Objects.requireNonNull(guard);return result;
    }
    public boolean isCurrentCall(Object call){return running&&call!=null&&pending==call;}
    public boolean isCurrentPrimitive(Object call){return isCurrentCall(call)&&primitive;}
    public long currentOrdinal(Object call){if(!isCurrentCall(call))throw new IllegalStateException("No original GameOver operation");return ordinal;}
    public Object currentResult(Object call){if(!isCurrentCall(call)||!resultReady||primitive)throw new IllegalStateException("No original completed GameOver operation");return operationResult;}
    private CdObjectState source(){return runtime.pool().state(activeWhole);}
    private static final class Receipt{List<CdObjectState> expected;Receipt(CdObjectPlacementRuntime.Snapshot cut){expected=cut.pool().slots();}}
    @FunctionalInterface private interface Operation<T>{T run(Receipt receipt);}
    private <T>T observed(Object call,CdObjectPlacementRuntime.Snapshot cut,Operation<T> operation){
        if(pending!=null)throw new IllegalStateException("Nested GameOver operation");pending=call;ordinal++;resultReady=false;var receipt=new Receipt(cut);
        try{verify(cut,receipt);observer.beforeOperation(call);verify(cut,receipt);receiptGuard.prepared(call);verify(cut,receipt);
            T result;primitive=true;try{result=operation.run(receipt);}finally{primitive=false;}verify(cut,receipt);operationResult=result;resultReady=true;
            observer.afterOperation(call);verify(cut,receipt);receiptGuard.completed(call);verify(cut,receipt);return result;
        }catch(RuntimeException|Error original){suppress(original,()->verify(cut,receipt));suppress(original,()->observer.failedOperation(call,original));suppress(original,()->verify(cut,receipt));throw original;}
        finally{primitive=false;pending=null;operationResult=null;resultReady=false;}
    }
    private void verify(CdObjectPlacementRuntime.Snapshot cut,Receipt receipt){
        runtime.validateRestore(cut);var actual=runtime.capture();if(!actual.status().equals(cut.status())||!java.util.Arrays.equals(actual.savedFlags().copy(),cut.savedFlags().copy()))throw new IllegalStateException("GameOver changed placement/saved nonwrites");
        for(int n=0;n<96;n++){byte[] expected=receipt.expected.get(n).copyBytes();if(!java.util.Arrays.equals(expected,actual.pool().slots().get(n).copyBytes()))throw new IllegalStateException("GameOver changed unowned full96 bytes at "+(n+32));}
    }
    private static void suppress(Throwable original,Runnable check){try{check.run();}catch(RuntimeException|Error failure){if(failure!=original)original.addSuppressed(failure);}}
    private void write(CdObjectState output){
        if(observer==null){runtime.pool().write(output);return;}var cut=runtime.capture();var before=runtime.pool().state(output.identity().wholeIndex());var next=CdObjectPool.overlay(cut.pool(),List.of(output));
        observed(new Publication(cut,source(),before,output),cut,r->{runtime.pool().write(output);r.expected=next.slots();return null;});
    }
    private void deleteStore(int whole){
        if(observer==null){runtime.pool().deleteObject(whole);return;}var cut=runtime.capture();
        // Prepare the existing pool owner's native sixteen-longword clear before any adoption.
        var staging=CdObjectPool.stagingCopy(cut.pool());staging.deleteObject(whole);var cleared=staging.state(whole);
        var next=CdObjectPool.overlay(cut.pool(),List.of(cleared));
        observed(new Publication(cut,source(),source(),null),cut,r->{runtime.pool().write(cleared);r.expected=next.slots();return null;});
    }
    private void writePowerup(GlobalsPort globals,int value){
        if(observer==null){globals.writePowerup(value);return;}var cut=runtime.capture();observed(new ScalarCall(cut,source(),Scalar.POWERUP,value),cut,r->{globals.writePowerup(value);return null;});
    }
    private void writeTimeOver(GlobalsPort globals,int value){
        if(observer==null){globals.writeTimeOver(value);return;}var cut=runtime.capture();observed(new ScalarCall(cut,source(),Scalar.TIME_OVER,value),cut,r->{globals.writeTimeOver(value);return null;});
    }
    private void sound(List<Effect> effects,SubCpuCommand command){
        if(observer!=null){var cut=runtime.capture();observed(new SoundCall(cut,source(),command),cut,r->{subCpu.command(command);return null;});}effects.add(new SubCpuRequest(command));
    }
    private void draw(CdObjectState.Identity identity){
        if(observer==null){drawObject.drawObject(runtime.pool(),identity);return;}var cut=runtime.capture();var source=source();
        observed(new DrawCall(cut,source),cut,r->{try{drawObject.drawObject(runtime.pool(),identity);}finally{int flags=runtime.pool().state(identity.wholeIndex()).spriteFlags();r.expected=CdObjectPool.overlay(cut.pool(),List.of(source.moveByte(SPRITE_FLAGS,flags))).slots();}return null;});
    }
    // END R13 ORIGINAL GAME OVER OPERATION SEAM 20261107

    private static void byteValue(int value, String name) {
        if ((value & ~0xFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native byte"); }
    }
}
