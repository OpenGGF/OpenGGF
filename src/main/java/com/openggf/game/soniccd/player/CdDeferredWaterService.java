package com.openggf.game.soniccd.player;

import java.util.Objects;
import java.util.function.Function;

/** Frame-subordinate semantic TT stage tail, not a Frame, scheduler or capture owner.
 * N1ecc5f3a r4/main.asm:1142-1157,964-973,1316-1374. The actual Frame enters AFTER
 * its genuine camera/flag copies and stays in its existing inOperation guard until return.
 * Every Call is synchronous/private-issued; Owners must additionally validate the original
 * source/lifetime/Frame phase and complete read/store cuts. Public values grant no admission.
 * No beam, CPU/VDP protocol, clock value, water update, animation or palette algorithm lives here.
 */
final class CdDeferredWaterService {
    enum Boundary { FRAME_SERVICE_OWNER, ORIGINAL_DEFERRED_CALLER, LOST_NATIVE_PENDING,
        UNFINISHED_WATER_SERVICE, UNFINISHED_CAPTURE, FINISH_SECOND_BORED_TIMER_OWNER }
    static final class Unowned extends UnsupportedOperationException {
        private final Boundary boundary;
        Unowned(Boundary boundary){super(Objects.requireNonNull(boundary).name());this.boundary=boundary;}
        Boundary boundary(){return boundary;}
    }
    enum Site { STAGE_SPLIT, SET_PENDING, DRAW_STAGE, SLOW_PLC, HUD, FINISH,
        ARM_READ, ARM_CLEAR, WATER_SPLIT, WATER_LONG_READ, SELECT_LONG_STORE,
        PENDING_READ, PENDING_CLEAR }
    /** Root's one non-null private Frame adapter; no generic action dispatcher or per-act bag.
     * Finish binds the Frame timer/count responsibility; TT second-bored ownership is unbound.
     * Do not treat the current primary-only helper as complete TT Finish. It must not toggle Main pause, run
     * Body, create deferred work, or select a continuation from a completed Result/dirty byte.
     * Palette reads/stores are one actual current LONG at a time, with completed prefixes retained.
     */
    interface Owners {
        void requireCurrent(Call call);
        int splitByte(Call call);
        int armWord(Call call);
        int deferredByte(Call call);
        void clearArm(Call call);
        void setDeferred(Call call,int value);
        int waterPaletteLong(Call call,int byteOffset);
        void selectPaletteLong(Call call,int byteOffset,int valueBits);
        void drawStage(Call call);
        void slowPlc(Call call);
        void updateHud(Call call);
        void finishVBlank(Call call);
    }
    /** Diagnostic site/cursor only: cannot construct, retain, transfer or replay a live call. */
    final class Call {
        private final Site site;private final int offset;private final long ordinal;
        private final Thread thread=Thread.currentThread();
        private Call(Site site,int offset,long ordinal){this.site=site;this.offset=offset;this.ordinal=ordinal;}
        Site site(){return site;}int paletteByteOffset(){return offset;}long ordinal(){return ordinal;}
        boolean isCurrent(){return operating&&current==this&&thread==Thread.currentThread();}
        void requireCurrent(Owners expected){if(expected!=owners||!isCurrent())throw new IllegalStateException("No original synchronous Frame water-service call");}
    }
    private final Owners owners;
    private boolean operating,unsettled,failed;
    private Call current;private long ordinal;
    CdDeferredWaterService(Owners owners){this.owners=Objects.requireNonNull(owners);}

    /** One real Frame operation: low-split native return skips Draw, Finish completes, then the
     * semantic water-palette boundary executes the original tail. There is NO saved continuation
     * or resumption API. Suppressed/missing low-split continuation stops before Frame closure.
     * Selector0 lag, stage-load, pause-special and warp admission are separate actual callers;
     * none may substitute this Stage tail for its own source path.
     */
    void runStageTail(){
        if(operating)throw new IllegalStateException("Recursive Frame water service");
        if(failed||unsettled)throw new IllegalStateException("Unfinished Frame service cannot restart");
        operating=true;unsettled=true;ordinal=0;
        try{
            int split=octet(step(Site.STAGE_SPLIT,-1,owners::splitByte));
            boolean originalDeferred=split<0x60;
            if(originalDeferred)step(Site.SET_PENDING,-1,c->{owners.setDeferred(c,1);return null;});
            else drawTail();
            // Native VBlankDispatchDone: bored/warp only when old paused==0; LONG count always.
            // The existing Frame owns those reads/writes and retains their own failure prefix.
            step(Site.FINISH,-1,c->{owners.finishVBlank(c);return null;});
            waterBoundary(originalDeferred);
            unsettled=false;
        }catch(RuntimeException|Error original){failed=true;throw original;}
        finally{current=null;operating=false;}
    }
    private void waterBoundary(boolean originalDeferred){
        int arm=word(step(Site.ARM_READ,-1,owners::armWord));
        if(arm==0){suppressed(originalDeferred);return;}
        step(Site.ARM_CLEAR,-1,c->{owners.clearArm(c);return null;});
        int split=octet(step(Site.WATER_SPLIT,-1,owners::splitByte));
        if(split==0xDF){suppressed(originalDeferred);return;}
        // HBlank's32 native LONG copies. Do not borrow an early128-byte snapshot or coalesce
        // reads: missing current source LONG n retains every completed selected LONG before n.
        for(int offset=0;offset<128;offset+=4){
            int at=offset;int bits=step(Site.WATER_LONG_READ,at,c->owners.waterPaletteLong(c,at));
            step(Site.SELECT_LONG_STORE,at,c->{owners.selectPaletteLong(c,at,bits);return null;});
        }
        int pending=octet(step(Site.PENDING_READ,-1,owners::deferredByte));
        if(pending==0){if(originalDeferred)throw new Unowned(Boundary.LOST_NATIVE_PENDING);return;}
        // Native reaches this byte AFTER palette selection. A dirty raw byte without this
        // original Frame invocation cannot authorize a fabricated second/deferred Draw tail.
        if(!originalDeferred)throw new Unowned(Boundary.ORIGINAL_DEFERRED_CALLER);
        step(Site.PENDING_CLEAR,-1,c->{owners.setDeferred(c,0);return null;});
        drawTail();
    }
    private static void suppressed(boolean originalDeferred){
        if(originalDeferred)throw new Unowned(Boundary.UNFINISHED_WATER_SERVICE);
    }
    private void drawTail(){
        step(Site.DRAW_STAGE,-1,c->{owners.drawStage(c);return null;});
        step(Site.SLOW_PLC,-1,c->{owners.slowPlc(c);return null;});
        step(Site.HUD,-1,c->{owners.updateHud(c);return null;});
    }
    private <T>T step(Site site,int offset,Function<Call,T> nativeOwner){
        if(!operating||current!=null)throw new IllegalStateException("Water service site reentry");
        var call=new Call(site,offset,++ordinal);current=call;
        try{
            call.requireCurrent(owners);owners.requireCurrent(call);
            var result=nativeOwner.apply(call);
            call.requireCurrent(owners);owners.requireCurrent(call);return result;
        }catch(RuntimeException|Error original){
            try{call.requireCurrent(owners);owners.requireCurrent(call);}
            catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}
            throw original;
        }finally{current=null;}
    }
    /** Root additionally keeps its existing requireOpenIdle/requireClosedFrame and full owner CAS.
     * This guard conveys no capture authority. No raw pending-byte value makes an unfinished
     * operation safe; a fault is terminal and this helper has no restore/reset/continuation API.
     */
    void requireClosedCut(){
        if(operating||unsettled||failed)throw new Unowned(Boundary.UNFINISHED_CAPTURE);
    }
    private static int octet(int v){if((v&~255)!=0)throw new IllegalArgumentException("Native unsigned BYTE required");return v;}
    private static int word(int v){if((v&~65535)!=0)throw new IllegalArgumentException("Native unsigned WORD required");return v;}
}
