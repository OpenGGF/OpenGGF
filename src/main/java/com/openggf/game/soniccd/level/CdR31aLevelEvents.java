package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.release.CdRetailReleases;
import com.openggf.game.soniccd.scroll.CdR11aForegroundScroll;
import java.util.*;

/**
 * Pure original-USA R31A CCZ12 followed by the complete common bottom-bound event tail.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Level/_Events.asm:7-64,143-161,
 * Collision Chaos/Scroll (Act 1 Present).asm:242-269, FixBugs=0.
 * Actual original directories/code/operands are authenticated separately from CAM_BOUNDS.
 * This component creates no World/Frame/service/clock/current/callback/capture authority.
 * The caller owns LevelScroll's high-byte lock test, flag clears, original event invocation,
 * full physical walk and all native publications. No period read is added to RunLevelEvents.
 */
public final class CdR31aLevelEvents {
    public enum Source {
        SCROLL_CONSUMER(0x299C,40,"f09d5db869f9bbc1c097376e27dc587672abae50e029f1aa7ab47225600a9d3b"),
        COMMON(0x3626,122,"2493b1c9b4618523ae9aeaa6c07309e35f029bdbb73c599a8cd5c46baa52757d"),
        ZONE_DIRECTORY(0x36A0,14,"5c179ea1f1742c6118cd93d38a155ae70122a0dc43a4a309f9cc0ec20eca6d9e"),
        CCZ_DISPATCH(0x3748,18,"aa81abf31c052c7c4fe78e285df0ae88f5e01be62218e75c201e98d6b397c632"),
        ACT_DIRECTORY(0x375A,6,"525fe3e7f297271e170a7be4b8fdd703218ffb4db521d170037787fbfd24692b"),
        CCZ12(0x3760,8,"c20949248a7b300e9310bfee37cf78abf4da9a88b09ea17e7e661f0b80cfaa41");
        private final int offset,length;private final String sha;
        Source(int offset,int length,String sha){this.offset=offset;this.length=length;this.sha=sha;}
    }
    /** Fixed finite metadata, never an arbitrary reader or source/adoption authority. */
    public record Range(Source source,int fileOffset,int length,String sha256) {
        public Range {Objects.requireNonNull(source);Objects.requireNonNull(sha256);
            if(fileOffset!=source.offset||length!=source.length||!sha256.equals(source.sha))
                throw new IllegalArgumentException("Not a fixed original R31 event source range");}
    }
    public record Player(int animationByte,int flagsByte) {
        public Player {octet(animationByte);octet(flagsByte);}
    }
    /** Only the actual zone/act dispatch WORD is read; timeZone is deliberately absent. */
    public record Gates(int rawZoneActWord) {
        public Gates {word(rawZoneActWord);}
    }
    public enum Field {
        DEST_BOTTOM_WORD(16),BOTTOM_WORD(16),SHIFT_HIGH_BYTE(8);
        private final int bits;Field(int bits){this.bits=bits;}public int bits(){return bits;}
    }
    /** Ordered comparison-only source stores, including writes that retain the same value.
     * SHIFT_HIGH_BYTE values are bytes, not reconstructed WORDs. No value can issue a live store. */
    public record Write(Field field,int before,int after) {
        public Write {Objects.requireNonNull(field);if(field.bits==8){octet(before);octet(after);}else{word(before);word(after);}}
    }
    public record Result(CdR11aForegroundScroll.State foreground,CdPlayerMovement.Bounds bounds,List<Write> writes) {
        public Result {Objects.requireNonNull(foreground);Objects.requireNonNull(bounds);writes=List.copyOf(writes);}
    }
    public static final class UnsupportedDispatch extends UnsupportedOperationException {
        private static final long serialVersionUID=1L;
        private final int rawZoneActWord;
        private UnsupportedDispatch(int raw){super("Unowned R31 event dispatch zone/act $"+Integer.toHexString(raw));rawZoneActWord=raw;}
        public int rawZoneActWord(){return rawZoneActWord;}
    }

    private final CdR31aAssets assets;private final CdR31aAssets.Identity identity;
    private final Map<Source,CdAssetBytes> views;private final List<Range> ranges;
    private final int targetBottomWord,boredAnimationByte,shiftSpeedWord,evenMaskWord,shiftByte;
    private CdR31aLevelEvents(CdR31aAssets assets) {
        this.assets=Objects.requireNonNull(assets,"actual original R31 aggregate");
        identity=Objects.requireNonNull(assets.identity());
        if(identity.variant()!=CdReleaseVariant.USA||identity.file()!=CdNativeFile.R31A
                ||!identity.stage().equals(CdStagePayload.R31A.stage())||identity.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM
                ||!identity.observedWholeDiscSha256().equals(CdRetailReleases.USA.normalizedSha256()))
            throw new IllegalArgumentException("Actual original-USA R31 event source required");
        var original=assets.levelEventsSource();
        if(original==null||!original.owns(assets))throw new IllegalArgumentException("Foreign original R31 event source custody");
        var v=new EnumMap<Source,CdAssetBytes>(Source.class);
        v.put(Source.SCROLL_CONSUMER,original.scrollConsumer());v.put(Source.COMMON,original.common());
        v.put(Source.ZONE_DIRECTORY,original.zoneDirectory());v.put(Source.CCZ_DISPATCH,original.cczDispatch());
        v.put(Source.ACT_DIRECTORY,original.actDirectory());v.put(Source.CCZ12,original.ccz12());
        var r=new ArrayList<Range>();for(var source:Source.values()){
            var bytes=Objects.requireNonNull(v.get(source));
            if(bytes.length()!=source.length||!CdR11aProfile.sha256(bytes.copy()).equals(source.sha))
                throw new IllegalArgumentException("Original complete R31 event source differs: "+source);
            r.add(new Range(source,source.offset,source.length,source.sha));
        }
        views=Collections.unmodifiableMap(v);ranges=List.copyOf(r);authenticateJoins();
        var body=views.get(Source.CCZ12);var common=views.get(Source.COMMON);
        targetBottomWord=body.unsignedWord(2);boredAnimationByte=common.unsignedWord(20);
        shiftSpeedWord=common.unsignedByte(39);evenMaskWord=common.unsignedWord(70);shiftByte=common.unsignedWord(80);
    }
    public static CdR31aLevelEvents fromAssets(CdR31aAssets assets){return new CdR31aLevelEvents(assets);}
    public boolean ownsAssets(CdR31aAssets expected){return assets==expected;}
    public CdR31aAssets.Identity identity(){return identity;}
    public List<Range> ranges(){return ranges;}
    public CdAssetBytes view(Source source){return views.get(Objects.requireNonNull(source));}

    private void authenticateJoins() {
        var s=views.get(Source.SCROLL_CONSUMER);var c=views.get(Source.COMMON);
        var d=views.get(Source.CCZ_DISPATCH);var body=views.get(Source.CCZ12);
        // Original USA R3 caller: lock check, four complete WORD clears, then events -> X -> Y.
        exactLong(s,0,0x4DF8D000L);exactLong(s,4,0x4A38F744L);exactWord(s,8,0x6702);exactWord(s,10,0x4E75);
        int[] flags={0xF754,0xF756,0xF758,0xF75A};for(int i=0;i<4;i++){exactWord(s,12+i*4,0x4278);exactWord(s,14+i*4,flags[i]);}
        int[] calls={0x3626,0x2CF2,0x2D92};for(int i=0;i<3;i++){int at=28+i*4;exactWord(s,at,0x6100);if(Source.SCROLL_CONSUMER.offset+at+2+(short)s.unsignedWord(at+2)!=calls[i])bad();}
        exactWord(c,0,0x7000);exactWord(c,2,0x1039);exactLong(c,4,0x00FF1506L);exactWord(c,8,0xD040);
        indexed(c,Source.COMMON.offset,10,0x303B,Source.ZONE_DIRECTORY.offset);
        indexed(c,Source.COMMON.offset,14,0x4EBB,Source.ZONE_DIRECTORY.offset);
        int[] zones={0x36AE,0x3748,0x386C,0x37DE,0x3780,0x38E2,0x3828};directory(Source.ZONE_DIRECTORY,zones);
        exactWord(d,0,0x7000);exactWord(d,2,0x1039);exactLong(d,4,0x00FF1507L);exactWord(d,8,0xD040);
        indexed(d,Source.CCZ_DISPATCH.offset,10,0x303B,Source.ACT_DIRECTORY.offset);
        indexed(d,Source.CCZ_DISPATCH.offset,14,0x4EFB,Source.ACT_DIRECTORY.offset);
        directory(Source.ACT_DIRECTORY,new int[]{0x3760,0x3760,0x3768});
        exactWord(body,0,0x31FC);exactWord(body,2,0x0510);exactWord(body,4,0xF726);exactWord(body,6,0x4E75);
        // Complete span hashes bind every remaining opcode. These are the consumed arithmetic operands.
        exactWord(c,18,0x0C38);exactWord(c,20,0x002B);exactWord(c,38,0x7204);
        exactWord(c,68,0x0278);exactWord(c,70,0xFFFE);exactWord(c,78,0x11FC);exactWord(c,80,1);
        exactWord(c,90,0x5040); // ADDQ.W #8,D0; encoded quick immediate0 means8.
        exactWord(c,98,0x0838);exactWord(c,100,1);exactWord(c,106,0xD241);exactWord(c,108,0xD241);
        exactWord(c,114,0x11FC);exactWord(c,116,1);
    }
    private void directory(Source source,int[] targets){var bytes=views.get(source);if(bytes.length()!=targets.length*2)bad();for(int i=0;i<targets.length;i++)if(source.offset+(short)bytes.unsignedWord(i*2)!=targets[i])bad();}
    private static void indexed(CdAssetBytes bytes,int base,int at,int opcode,int table){exactWord(bytes,at,opcode);int extension=bytes.unsignedWord(at+2);if((extension&0xFF00)!=0||base+at+2+(byte)extension!=table)bad();}
    private static void exactWord(CdAssetBytes bytes,int at,int value){if(bytes.unsignedWord(at)!=value)bad();}
    private static void exactLong(CdAssetBytes bytes,int at,long value){if(bytes.unsignedLong(at)!=value)bad();}
    private static void bad(){throw new IllegalArgumentException("Original R31 event consumer/directory join differs");}

    /**
     * Pure RunLevelEvents cut. LevelScroll eligibility/flag clears and all owner publication stay outside.
     * No timeZone, bossFlags, eventRoutine, actor position, top/water/layout/platform or clock is read.
     * All returned writes are observations, not callbacks, receipts or executable command requests.
     */
    public Result run(CdR11aForegroundScroll.State foreground,CdPlayerMovement.Bounds bounds,Player player,Gates gates) {
        Objects.requireNonNull(foreground);Objects.requireNonNull(bounds);Objects.requireNonNull(player);Objects.requireNonNull(gates);
        if(gates.rawZoneActWord()!=0x0100)throw new UnsupportedDispatch(gates.rawZoneActWord());
        var writes=new ArrayList<Write>();int dest=foreground.destBottomBound(),bottom=bounds.bottom(),shift=foreground.bottomBoundShift();
        writes.add(new Write(Field.DEST_BOTTOM_WORD,dest,targetBottomWord));dest=targetBottomWord;
        if(player.animationByte()==boredAnimationByte){
            int cameraY=foreground.cameraYWord();
            writes.add(new Write(Field.BOTTOM_WORD,bottom,cameraY));bottom=cameraY;
            writes.add(new Write(Field.DEST_BOTTOM_WORD,dest,cameraY));dest=cameraY;
        }
        // SUB.W/BCC tests unsigned no-borrow. Equality retains the entire dirty shift WORD.
        if(dest!=bottom){
            if(dest<bottom){
                int cameraY=foreground.cameraYWord();
                if(cameraY>dest){
                    writes.add(new Write(Field.BOTTOM_WORD,bottom,cameraY));bottom=cameraY;
                    int even=bottom&evenMaskWord;writes.add(new Write(Field.BOTTOM_WORD,bottom,even));bottom=even;
                }
                int ready=(bottom-shiftSpeedWord)&0xFFFF;writes.add(new Write(Field.BOTTOM_WORD,bottom,ready));bottom=ready;
            }else{
                int cameraY=foreground.cameraYWord();
                int speed=shiftSpeedWord;
                if(((cameraY+8)&0xFFFF)>=bottom&&(player.flagsByte()&2)!=0){speed=(speed+speed)&0xFFFF;speed=(speed+speed)&0xFFFF;}
                int ready=(bottom+speed)&0xFFFF;writes.add(new Write(Field.BOTTOM_WORD,bottom,ready));bottom=ready;
            }
            writes.add(new Write(Field.SHIFT_HIGH_BYTE,shift>>>8,shiftByte));shift=(shift&0xFF)|(shiftByte<<8);
        }
        var nextForeground=dest==foreground.destBottomBound()&&shift==foreground.bottomBoundShift()?foreground:
                new CdR11aForegroundScroll.State(foreground.cameraX(),foreground.cameraY16_16(),foreground.horizontalCrossed(),foreground.verticalCrossed(),
                        foreground.scrollXDiff(),foreground.scrollYDiff(),foreground.scrollFlags(),dest,foreground.destLeftBound(),shift,foreground.scrollLock());
        var nextBounds=bottom==bounds.bottom()?bounds:new CdPlayerMovement.Bounds(bounds.left(),bounds.right(),bottom,bounds.bossFight());
        return new Result(nextForeground,nextBounds,writes);
    }
    private static void word(int value){if((value&~0xFFFF)!=0)throw new IllegalArgumentException("Native WORD required");}
    private static void octet(int value){if((value&~0xFF)!=0)throw new IllegalArgumentException("Native BYTE required");}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
