package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
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
     * not media authentication. Production obtains the record only through {@link #fromObjectAssets}. A future
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
    public Result run(int wholeIndex, GlobalsPort globals) {
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
        effects.add(new SubCpuRequest(SubCpuCommand.SCMD_FADEPCM));              // MOVE.W #SCMD_FADEPCM,d0 / JSR SubCPUCmd
        pool.write(state.addByte(ROUTINE, 2)                                     // ADDQ.B #2,oRoutine(a0)
                .moveWord(X_FRACTION, SCREEN_Y_WORD)                             // MOVE.W #96+128,oYScr(a0): the WORD at $0A
                .moveWord(X, GAME_START_X)                                       // MOVE.W #0+128,oX(a0)
                .moveWord(GAME_OVER_DEST_X, DESTINATION_X)                       // MOVE.W #160+128,oGmOverDestX(a0)
                .moveWord(TILE, NATIVE_TILE_WORD)                                // MOVE.W #$8544,oTile(a0)
                .moveLong(MAPPINGS, assets.gameOverMappingsAddress()));          // MOVE.L #MapSpr_GameOver,oMap(a0)
        globals.writePowerup(POWERUP_GAME_OVER);                                 // MOVE.B #8,powerup
        int timeOver = read(globals).timeOver();                                 // BCLR #0,timeOver: Z is the old bit 0
        globals.writeTimeOver(timeOver & 0xFE);
        if ((timeOver & 1) != 0) {                                               // BEQ.S .NotTimeOver not taken
            if (read(globals).lives() != 0) {                                    // TST.B lives / BEQ.S .GameOver
                pool.write(pool.state(whole)
                        .moveLong(MAPPINGS, assets.timeOverMappingsAddress()));  // MOVE.L #MapSpr_TimeOver,oMap(a0)
                globals.writePowerup((read(globals).powerup() + POWERUP_TIME_OVER_STEP) & 0xFF); // ADDQ.B #2,powerup
            }                                                                    // BRA.S .GameOver
        } else if (read(globals).lives() != 0) {                                 // .NotTimeOver: TST.B lives / BNE.S .Destroy
            delete(whole, DeleteEntry.LIVES_REMAIN, effects);
            effects.add(new CallReturned(NativeCall.INIT));
            return;
        }
        globals.writePowerup(read(globals).powerup() | POWERUP_LOAD_BIT);       // .GameOver: BSET #7,powerup
        var found = pool.findObjSlot();                                          // JSR FindObjSlot: first oID-zero slot
        if (found.isEmpty()) {                                                   // BEQ.S .SpawnOverText not taken
            effects.add(new OverTextSlotUnavailable());
            delete(whole, DeleteEntry.NO_FREE_SLOT, effects);                    // .Destroy: no music, no DrawObject
            effects.add(new CallReturned(NativeCall.INIT));
            return;
        }
        int child = found.getAsInt();
        var parent = pool.state(whole);
        pool.write(pool.state(child)
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
        effects.add(new SubCpuRequest(SubCpuCommand.SCMD_GMOVERMUS));            // MOVE.W #SCMD_GMOVERMUS,d0 / JMP SubCPUCmd
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
            pool.write(state);
        }
        effects.add(new CallReturned(NativeCall.MAIN));
        drawObject.drawObject(pool, state.identity());                           // .Draw: JMP DrawObject
        effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
    }

    private void delete(int whole, DeleteEntry entry, List<Effect> effects) {
        runtime.pool().deleteObject(whole);                                      // DeleteObject: all 16 longwords
        effects.add(new Deleted(entry));
    }

    private static Globals read(GlobalsPort globals) {
        return Objects.requireNonNull(globals.read(), "Game Over globals");
    }

    private static void byteValue(int value, String name) {
        if ((value & ~0xFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native byte"); }
    }
}
