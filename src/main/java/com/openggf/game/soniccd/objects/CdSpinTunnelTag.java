package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR13FutureAssets;
import com.openggf.game.soniccd.level.CdR13ReusedObjectAssets;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.level.CdR12bObjectAssets;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.player.CdPlayerMotionState;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.MAP_FRAME;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.ROUTINE;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.SPRITE_FLAGS;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE;

/**
 * Pure staged ObjSpinTunnel tag ($34), not TunnelPath or either door body.
 * Source: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Level/_Objects/Spin Tunnel/Main (Tag).asm:7-188; Object Spawner.asm:385-446.
 * Shipped FixBugs=0 behavior; these owning routines have no conditional fixed alternative.
 *
 * <p>Root preflights and publishes the complete expected object/player cut before calling the existing
 * shared DrawObject when requested. Root MUST then reload the LIVE slot and pass that post-draw image
 * to {@link #despawn}: DrawObject can clear sprite bit7. Neither result authenticates a call or grants
 * publication authority. Root owns phase/nonreentry, pool and expected-motion checks, the real 766-byte
 * placement-bank preflight, loaded-bit clearing and full 64-byte DeleteObject.</p>
 *
 * <p>Only immutable Assets is retained. No pool, snapshot, Frame, Runtime, callback, sound, probe,
 * animation service or Body rerun is performed or retained. Results preserve unowned bytes/fields;
 * their exact store sets include executed stores whose values were unchanged.</p>
 */
public final class CdSpinTunnelTag {
    public static final int OBJECT_ID = 0x34;
    private static final int MAPPING_BYTES = 274;

    /** An explicit pointer declaration alone authenticates no media or native operand. */
    public record Assets(int mappingsAddress) {
        public Assets {
            if (mappingsAddress <= 0 || (mappingsAddress & 1) != 0
                    || mappingsAddress > 0xFFFFFF - (MAPPING_BYTES - 1)) {
                throw new IllegalArgumentException("Powerup mappings need a nonzero aligned bounded native pointer");
            }
        }

        /**
         * Authenticated-owner ingress; retains only the raw view's native address, with no uniform
         * mapping decode, embedded bytes, guessed address or fallback. Compile prerequisite: Root's
         * separately owned Table.POWERUP_MAPPINGS binding for the exact raw 274-byte include.
         */
        /** Actual retained original-USA R12B data only; no body/Frame/World or current-art authority. */
        public static Assets fromAct2PastAssets(CdR12bObjectAssets owner) {
            Objects.requireNonNull(owner, "actual R12B metadata"); var identity = owner.identity();
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R12B
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R12B.stage()))
                throw new IllegalArgumentException("Actual original USA R12B metadata required");
            if (owner.view(CdR12bObjectAssets.View.MAP_SPR_POWERUP).length() != MAPPING_BYTES)
                throw new IllegalArgumentException("Complete actual raw Powerup mapping source required");
            return new Assets(CdR12bObjectAssets.View.MAP_SPR_POWERUP.range().nativeAddress());
        }

        public static Assets fromObjectAssets(CdR11aObjectAssets owner) {
            Objects.requireNonNull(owner, "object assets");
            var view = owner.view(CdR11aObjectAssets.Table.POWERUP_MAPPINGS);
            if (view.range().length() != MAPPING_BYTES || view.bytes().length() != MAPPING_BYTES) {
                throw new IllegalArgumentException("Powerup mappings must retain the complete raw 274-byte view");
            }
            return new Assets(view.range().nativeAddress());
        }
    }

    /** Full native words; TST.B at either big-endian debug symbol reads its HIGH byte. */
    public record Gates(int zoneByte, int debugModeWord, int debugCheatWord, int rawHeldByte) {
        public Gates {
            byteValue(zoneByte); wordValue(debugModeWord); wordValue(debugCheatWord); byteValue(rawHeldByte);
        }
    }

    /** The complete native store union. CENTRE_Y_WORD never authorizes a fraction write. */
    public enum PlayerField {
        CENTRE_Y_WORD, X_VELOCITY, Y_VELOCITY, GROUND_VELOCITY, FLAGS, Y_RADIUS, X_RADIUS, ANIMATION
    }

    /** Detached values, not a generic patch/capture owner. Root validates preservation and store authority. */
    public record PlayerResult(CdPlayerMotionState expectedMotion, CdPlayerMotionState finalMotion,
                               Set<PlayerField> executedWrites) {
        public PlayerResult {
            Objects.requireNonNull(expectedMotion, "expected motion");
            Objects.requireNonNull(finalMotion, "final motion");
            executedWrites = Set.copyOf(Objects.requireNonNull(executedWrites, "executed stores"));
        }
    }

    /** Source prefix only. Draw/despawn have not run; the object is never written back after shared draw. */
    public record Result(CdObjectState expectedObject, CdObjectState object, PlayerResult player,
                         boolean drawObject) {
        public Result {
            Objects.requireNonNull(expectedObject, "expected object");
            Objects.requireNonNull(object, "object");
            Objects.requireNonNull(player, "player");
        }
    }

    /** Raw bytes preserve signed direction and the independent time-warp bit. Camera is pre-scroll. */
    public record DespawnGlobals(int rawTimeZoneByte, int rawTimeWarpDirByte, int cameraXWord) {
        public DespawnGlobals {
            byteValue(rawTimeZoneByte); byteValue(rawTimeWarpDirByte); wordValue(cameraXWord);
        }
    }

    /**
     * Actual reached BCLR index, including out-of-bank values. Without a warp the native low seven-bit
     * period is not clamped. No bank is read/filled here; Root checks this index against its real bank
     * BEFORE either clearing loaded bit7 (preserving bits0..6) or deleting the slot.
     */
    public record LoadedClear(int savedId, int period, int index) {
        public LoadedClear {
            if (savedId < 1 || savedId > 0xFF || period < 0 || period > 0x7F
                    || index != 2 + 3 * savedId + period) {
                throw new IllegalArgumentException("Not the reached native saved-ID/period/index tuple");
            }
        }
    }

    /** A decision, never a performed deletion/clear. Saved ID zero has no LoadedClear. */
    public record DespawnResult(boolean deleteObject, Optional<LoadedClear> loadedClear) {
        public DespawnResult {
            Objects.requireNonNull(loadedClear, "loaded clear");
            if (!deleteObject && loadedClear.isPresent()) {
                throw new IllegalArgumentException("A kept tag cannot request a loaded-bit clear");
            }
        }
    }

    public enum UnsupportedBoundary { ROUTINE_DISPATCH, SAVED_FLAGS_ADDRESS }
    public static final class UnsupportedSpinTunnelTag extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;
        private final int value;

        private UnsupportedSpinTunnelTag(CdObjectState object){this(UnsupportedBoundary.ROUTINE_DISPATCH,object,object.routine());}
        private UnsupportedSpinTunnelTag(UnsupportedBoundary boundary,CdObjectState object,int value){
            super(boundary+" at Spin tunnel tag whole slot "+object.identity().wholeIndex()+", reached value $"+Integer.toHexString(value));
            this.boundary=boundary;this.wholeIndex=object.identity().wholeIndex();this.value=value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    private final Assets assets;

    // BEGIN R13 SOURCE FACTORY 20261106
    /** Actual ObjRollTunnel, Collision Chaos/Objects/Roll Tunnel/Main.asm:7-212.
     * REGION=USA|STAGE_R13 selects the same finite control/roll branches as the shared tag body;
     * .Reverse jumps after the old-Y-sign test. Uses its own actual Powerup mapping operand. */
    public static CdSpinTunnelTag fromAct3FutureAssets(CdObjectPlacementRuntime runtime,CdR13FutureAssets assets,CdR13ReusedObjectAssets owner) {
        Objects.requireNonNull(runtime,"actual placement");Objects.requireNonNull(assets,"actual original future source");Objects.requireNonNull(owner,"actual reuse metadata");
        if(!runtime.ownsAct3FutureAssets(assets)||!owner.ownsAssets(assets,owner.startup()))throw new IllegalArgumentException("R13 reused body requires exact retained source/placement/metadata");
        if(owner.view(CdR13ReusedObjectAssets.View.MAP_SPR_POWERUP).length()!=274)throw new IllegalArgumentException("Actual R13 Powerup274");
        return new CdSpinTunnelTag(new Assets(CdR13ReusedObjectAssets.View.MAP_SPR_POWERUP.range(assets.identity().file()).nativeAddress()));
    }
    // END R13 SOURCE FACTORY 20261106

    /** Source-pair ingress only; caller retains every actual helper/publication authority. */
    public static CdSpinTunnelTag fromAct2PastAssets(CdObjectPlacementRuntime placement, CdR12bTerrainAssets owner) {
        Objects.requireNonNull(placement); Objects.requireNonNull(owner);
        if (!placement.sameAct2PastSource(owner)) throw new IllegalArgumentException("SpinTunnel placement/assets source differs");
        return new CdSpinTunnelTag(Assets.fromAct2PastAssets(owner.objects()));
    }

    public enum Read { ZONE, DEBUG_MODE, DEBUG_CHEAT, RAW_HELD }
    public enum Site { MOTION, READ, OBJECT, PLAYER, DRAW, SAVED, DELETE }
    /** Privately issued only while this cached kernel is executing its original operation. */
    public static final class OriginalCall {
        private final Site site;private final long ordinal;private final CdObjectPlacementRuntime.Snapshot cut;
        private final CdObjectState source,output;private final Read read;private final PlayerResult player;private final int savedIndex;
        private boolean completed;
        private OriginalCall(Site site,long ordinal,CdObjectPlacementRuntime.Snapshot cut,CdObjectState source,
                CdObjectState output,Read read,PlayerResult player,int savedIndex){
            this.site=site;this.ordinal=ordinal;this.cut=cut;this.source=source;this.output=output;this.read=read;this.player=player;this.savedIndex=savedIndex;
        }
        public Site site(){return site;}public long ordinal(){return ordinal;}public CdObjectPlacementRuntime.Snapshot cut(){return cut;}
        public CdObjectState source(){return source;}public CdObjectState output(){return output;}public Read read(){return read;}
        public PlayerResult player(){return player;}public int savedIndex(){return savedIndex;}public boolean completed(){return completed;}
    }
    public interface OriginalPorts {
        CdPlayerMotionState motion(OriginalCall call);int read(OriginalCall call,Read field);
        void player(OriginalCall call,PlayerResult result);void draw(OriginalCall call);
    }
    public interface OriginalObserver {void before(OriginalCall call);void after(OriginalCall call);void failed(OriginalCall call,Throwable original);}
    private CdObjectPlacementRuntime placement;private OriginalPorts ports;private OriginalObserver observer;
    private OriginalCall pending;private boolean running;private int whole;private long ordinal;
    public boolean isCurrentCall(Object call){return running&&pending!=null&&pending==call;}
    /** Constructed controls only; production authenticates the retained source pair first. */
    public static CdSpinTunnelTag withOriginalOperations(CdObjectPlacementRuntime placement,Assets assets,OriginalPorts ports,OriginalObserver observer){
        var body=new CdSpinTunnelTag(assets);body.placement=Objects.requireNonNull(placement);body.ports=Objects.requireNonNull(ports);body.observer=Objects.requireNonNull(observer);return body;
    }
    public static CdSpinTunnelTag fromOriginalPresentAssets(CdObjectPlacementRuntime placement,
            com.openggf.game.soniccd.level.CdR11aAssets assets,OriginalPorts ports,OriginalObserver observer)throws java.io.IOException{
        CdOriginalPresentObjectSource.require(placement,assets);
        return withOriginalOperations(placement,Assets.fromObjectAssets(assets.openingObjects()),ports,observer);
    }
    private <T> T operation(Site site,CdObjectState output,Read read,PlayerResult player,int savedIndex,
            java.util.function.Function<OriginalCall,T> action){
        if(!running||pending!=null)throw new IllegalStateException("No original Tag operation or nested operation");
        var c=new OriginalCall(site,++ordinal,placement.capture(),placement.pool().state(whole),output,read,player,savedIndex);pending=c;
        try{observer.before(c);T result=action.apply(c);c.completed=true;observer.after(c);return result;}
        catch(RuntimeException|Error original){try{observer.failed(c,original);}catch(RuntimeException|Error v){if(v!=original)original.addSuppressed(v);}throw original;}
        finally{pending=null;}
    }
    private int originalRead(Read field){return operation(Site.READ,null,field,null,-1,c->ports.read(c,field));}
    /** Original Init/Main/optional Draw/despawn; never a result/effect replay. */
    public void runOriginal(int wholeIndex,DespawnGlobals globals){
        if(placement==null||running)throw new IllegalStateException("No original Tag owner or reentry");
        running=true;whole=wholeIndex;ordinal=0;
        try{
            var o=placement.pool().state(whole);if(o.id()!=OBJECT_ID)throw new IllegalArgumentException("Expected original Tag $34");
            if(o.routine()!=0&&o.routine()!=2)throw new UnsupportedSpinTunnelTag(o);
            if(o.routine()==0){
                var n=o.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,o.spriteFlags()|4).moveWord(TILE,0x544)
                    .moveLong(MAPPINGS,assets.mappingsAddress()).moveByte(MAP_FRAME,(o.subtype()+1)&255);
                operation(Site.OBJECT,n,null,null,-1,c->{placement.pool().write(n);return null;});
            }
            var m=operation(Site.MOTION,null,null,null,-1,c->ports.motion(c));var live=placement.pool().state(whole);
            rolling(m,live.centreX(),live.centreY(),live.subtype(),this::originalRead,
                value->operation(Site.PLAYER,null,null,value,-1,c->{ports.player(c,value);return null;}));
            if((originalRead(Read.DEBUG_CHEAT)>>>8)!=0)operation(Site.DRAW,null,null,null,-1,c->{ports.draw(c);return null;});
            var decision=despawn(placement.pool().state(whole),globals);
            if(decision.deleteObject()){
                if(decision.loadedClear().isPresent()){
                    int index=decision.loadedClear().orElseThrow().index();
                    if(index<2||index>=CdObjectPlacementRuntime.SAVED_BANK_BYTES)throw new UnsupportedSpinTunnelTag(UnsupportedBoundary.SAVED_FLAGS_ADDRESS,placement.pool().state(whole),index);
                    operation(Site.SAVED,null,null,null,index,c->{placement.bclrSavedFlag((index-2)/3,(index-2)%3,CdObjectPlacementRuntime.SavedBit.LOADED);return null;});
                }
                operation(Site.DELETE,null,null,null,-1,c->{placement.pool().deleteObject(whole);return null;});
            }
        }finally{running=false;whole=-1;}
    }

    public CdSpinTunnelTag(Assets assets) { this.assets = Objects.requireNonNull(assets, "assets"); }

    /** One actual routine0/2 source prefix, with Init0 falling straight through into Main2. */
    public Result step(CdObjectState beforeObject, CdPlayerMotionState motion, Gates gates) {
        Objects.requireNonNull(beforeObject, "before object");
        Objects.requireNonNull(motion, "motion");
        Objects.requireNonNull(gates, "gates");
        if (beforeObject.id() != OBJECT_ID) {
            throw new IllegalArgumentException("Expected Spin tunnel tag object ID $34");
        }
        // Native routine is an unsigned BYTE OFFSET into two words, not a normalized routine number.
        // Stop before Init or any player store for odd offsets and every offset above2 (Tag:7-11,21-23).
        if (beforeObject.routine() != 0 && beforeObject.routine() != 2) {
            throw new UnsupportedSpinTunnelTag(beforeObject);
        }

        var object = beforeObject;
        if (object.routine() == 0) {
            // ObjSpinTunnel_Init (Tag:26-32). No RTS; no other a0 byte is initialized.
            object = object.addByte(ROUTINE, 2)
                    .moveByte(SPRITE_FLAGS, object.spriteFlags() | 4)
                    .moveWord(TILE, 0x0544)
                    .moveLong(MAPPINGS, assets.mappingsAddress())
                    .moveByte(MAP_FRAME, object.subtype()).addByte(MAP_FRAME, 1);
        }

        return new Result(beforeObject, object, prepareRollingMotion(motion,object.centreX(),object.centreY(),object.subtype(),gates),
                (gates.debugCheatWord() >>> 8) != 0);
    }

    /** Shared equal native Tag/Roll arithmetic only. Values carry no source or invocation authority.
     * N1ecc5f3a Tag37-161 and CC Roll Tunnel/Main37-210 retain distinct IDs/operands/callers. */
    static PlayerResult prepareRollingMotion(CdPlayerMotionState motion,int objectX,int objectY,int subtype,Gates gates) {
        Objects.requireNonNull(motion);Objects.requireNonNull(gates);byteValue(subtype);wordValue(objectX);wordValue(objectY);
        return rolling(motion,objectX,objectY,subtype,read->switch(read){
            case ZONE->gates.zoneByte();case DEBUG_MODE->gates.debugModeWord();
            case DEBUG_CHEAT->gates.debugCheatWord();case RAW_HELD->gates.rawHeldByte();
        },null);
    }

    /** One arithmetic kernel for detached legacy values and original ordered operations. */
    private static PlayerResult rolling(CdPlayerMotionState motion,int x,int y,int subtype,
            java.util.function.ToIntFunction<Read> gates,java.util.function.Consumer<PlayerResult> publish) {
        var c=new Roll(motion,publish);
        if(motion.animation()!=0x2B&&motion.routine()<6&&(gates.applyAsInt(Read.DEBUG_MODE)>>>8)==0
                &&inRange(motion.centreX16_16()>>>16,x)&&inRange(motion.centreY16_16()>>>16,y)){
            if(subtype==0){
                int speed=minimumMagnitude(motion.xVelocity(),gates.applyAsInt(Read.ZONE)==5?0xD00:0xA00);
                c.store(PlayerField.X_VELOCITY,speed);c.store(PlayerField.GROUND_VELOCITY,speed);
                if((((motion.angle()+0x20)&255)&0xC0)==0x80)c.store(PlayerField.GROUND_VELOCITY,(short)-speed);
            }else if(subtype==1){
                int speed=minimumMagnitude(motion.yVelocity(),0xD00);
                c.store(PlayerField.Y_VELOCITY,speed);c.store(PlayerField.GROUND_VELOCITY,speed);
                c.store(PlayerField.FLAGS,c.value.flags()|2);
            }else{
                int held=gates.applyAsInt(Read.RAW_HELD);
                if(subtype==4){
                    if(motion.xVelocity()>=0&&(held&1)!=0){
                        c.store(PlayerField.Y_VELOCITY,-0xA00);c.store(PlayerField.GROUND_VELOCITY,-0xA00);
                        c.store(PlayerField.FLAGS,c.value.flags()|2);
                    }
                }else if(subtype==2?motion.yVelocity()<0:motion.yVelocity()>=0){
                    int launch=(held&8)!=0?0xD00:(held&4)!=0?-0xD00:0;
                    if(launch!=0){
                        if(subtype!=2)c.store(PlayerField.FLAGS,c.value.flags()|2);
                        c.store(PlayerField.X_VELOCITY,launch);c.store(PlayerField.GROUND_VELOCITY,launch);
                    }
                }
            }
            boolean rolled=(c.value.flags()&4)!=0;c.store(PlayerField.FLAGS,c.value.flags()|4);
            if(!rolled){c.store(PlayerField.Y_RADIUS,14);c.store(PlayerField.X_RADIUS,7);
                c.store(PlayerField.CENTRE_Y_WORD,((c.value.centreY16_16()>>>16)+5)&65535);c.store(PlayerField.ANIMATION,2);}
        }
        return new PlayerResult(motion,c.value,c.stores);
    }
    private static final class Roll {
        CdPlayerMotionState value;final EnumSet<PlayerField> stores=EnumSet.noneOf(PlayerField.class);
        final java.util.function.Consumer<PlayerResult> publish;
        Roll(CdPlayerMotionState value,java.util.function.Consumer<PlayerResult> publish){this.value=value;this.publish=publish;}
        void store(PlayerField field,int raw){
            var m=value;
            var n=new CdPlayerMotionState(m.centreX16_16(),field==PlayerField.CENTRE_Y_WORD?(raw<<16)|(m.centreY16_16()&65535):m.centreY16_16(),
                field==PlayerField.X_VELOCITY?raw:m.xVelocity(),field==PlayerField.Y_VELOCITY?raw:m.yVelocity(),
                field==PlayerField.GROUND_VELOCITY?raw:m.groundVelocity(),m.routine(),m.angle(),field==PlayerField.FLAGS?raw:m.flags(),
                m.spriteFlags(),field==PlayerField.X_RADIUS?raw:m.xRadius(),field==PlayerField.Y_RADIUS?raw:m.yRadius(),
                field==PlayerField.ANIMATION?raw:m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),
                m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
            if(publish!=null)publish.accept(new PlayerResult(m,n,Set.of(field)));
            value=n;stores.add(field);
        }
    }

    /**
     * Pure second cut of CheckObjDespawn, Object Spawner.asm:385-446. Supply the reloaded live image
     * AFTER optional shared DrawObject; never reuse step's stale sprite bit7. There is no viewport fence
     * or original-anchor substitution. No bank/pool access occurs, including at out-of-range indices.
     */
    public DespawnResult despawn(CdObjectState afterDrawLiveImage, DespawnGlobals globals) {
        Objects.requireNonNull(afterDrawLiveImage, "post-draw live image");
        return despawnAtX(afterDrawLiveImage, afterDrawLiveImage.centreX(), globals);
    }

    /** Shared CheckObjectDespawn2's explicit selected-X operand; original image is not rewritten. */
    public DespawnResult despawnAtX(CdObjectState afterDrawLiveImage,int selectedXWord,DespawnGlobals globals) {
        return checkObjectDespawnAtX(afterDrawLiveImage,selectedXWord,globals);
    }
    /** One shared native CheckObjectDespawn/2 value entry. Original live callers retain their
     * own current operation, pool/saved-bank/source/Frame guards and ordered BCLR/Delete.
     * No Tag asset, body, data fallback or invocation is manufactured to reuse this algorithm. */
    public static DespawnResult checkObjectDespawnAtX(CdObjectState afterDrawLiveImage,int selectedXWord,DespawnGlobals globals) {
        Objects.requireNonNull(afterDrawLiveImage, "post-draw live image");
        if((selectedXWord&~65535)!=0)throw new IllegalArgumentException("Selected native X WORD required");
        Objects.requireNonNull(globals, "despawn globals");
        boolean warp = (globals.rawTimeZoneByte() & 0x80) != 0;
        boolean distanceDelete = false;
        if ((afterDrawLiveImage.spriteFlags() & 0x80) == 0) {
            distanceDelete = outsideCameraWindow(selectedXWord,globals.cameraXWord());
        }
        if (!distanceDelete && !warp) return new DespawnResult(false, Optional.empty());
        int savedId = afterDrawLiveImage.savedFlagsId();
        if (savedId == 0) return new DespawnResult(true, Optional.empty());

        int period = selectedDespawnPeriod(globals.rawTimeZoneByte(),globals.rawTimeWarpDirByte());
        return new DespawnResult(true, Optional.of(new LoadedClear(savedId, period, 2 + 3 * savedId + period)));
    }

    /** One semantic native warp-period selection, also used by the P3 staging saved-store caller. */
    static int selectedDespawnPeriod(int rawTimeZoneByte,int rawTimeWarpDirByte) {
        int period=rawTimeZoneByte&0x7f;
        if((rawTimeZoneByte&0x80)!=0){
            period=(short)(period-(byte)rawTimeWarpDirByte);
            if(period<0)period=0;else if(period>=3)period=2;
        }
        return period;
    }

    /** Shared native unsigned chunk-window arithmetic, without onscreen/warp policy or writes.
     * CheckObjectDespawn and HMove's loc_20C224 are actual callers with distinct native gates. */
    public static boolean outsideCameraWindow(int selectedXWord,int cameraXWord){
        if(((selectedXWord|cameraXWord)&~65535)!=0)throw new IllegalArgumentException("Native X WORD operands required");
        int cameraChunk=((cameraXWord-0x80)&0xFFFF)&0xFF80;
        int distance=((selectedXWord&0xFF80)-cameraChunk)&0xFFFF;
        return distance>0x280;
    }

    /** Tag:166-188; signed BMI followed by unsigned BCC on the wrapped distance word. */
    private static boolean inRange(int playerWord, int objectWord) {
        int distance = (playerWord - objectWord + 0x28) & 0xFFFF;
        return (short) distance >= 0 && distance < 0x50;
    }

    /** Native NEG.W $8000 remains $8000; unsigned magnitude comparison must preserve that case. */
    private static int minimumMagnitude(int velocity, int minimum) {
        int magnitude = velocity < 0 ? (short) -velocity : velocity;
        if ((magnitude & 0xFFFF) < minimum) magnitude = minimum;
        return velocity < 0 ? (short) -magnitude : (short) magnitude;
    }

    private static void byteValue(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native byte");
    }
    private static void wordValue(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected an unsigned native word");
    }
}
