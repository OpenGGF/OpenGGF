package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR31IntroAssets;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import static com.openggf.game.soniccd.objects.CdIntroSpikes.PlayerStore.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.ROUTINE;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.SPRITE_FLAGS;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.MAP_FRAME;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;

/** CC $35 full routine0/2 prefix; never PPZ TunnelPath07 or SpinTunnel34.
 * N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/Collision Chaos/Objects/Roll Tunnel/Main.asm:7-210.
 * A selects CC_LEGACY_ROLL_TUNNEL_ABI=1; B/C/D objects/roll_tunnel.asm:3-179
 * independently select the same XRadius and debug high BYTE behavior.
 * No FixBugs alternative. Raw2C is neither an admission gate nor a store in this routine.
 * Detached results are DATA, not invocation receipts: Root owns ordered adoption, complete
 * State/Environment, original source/pool/private operation and actual Draw then live Despawn.
 * No public result can replay sound, Frame work or lifecycle. Programme operands require their
 * own authenticated adapter; this component does not confer A source authority on B/C/D.
 */
public final class CdRollTunnel {
    /** Finite operand, not media/invocation authentication. B/C/D descriptors belong to Root. */
    public record Assets(int powerupMappings) {
        public Assets {if(powerupMappings<=0 || powerupMappings>0xFFFFFE || (powerupMappings&1)!=0)
            throw new IllegalArgumentException("Powerup mapping operand");}
        public static Assets fromAssets(CdR31IntroAssets actual){return new Assets(Objects.requireNonNull(actual).rollMappings());}
    }
    public record Gates(int zone,int debugModeWord,int debugCheatWord,int held) {
        public Gates {byteValue(zone);wordValue(debugModeWord);wordValue(debugCheatWord);byteValue(held);}
    }
    /** executedWrites includes no-op stores. Y_WORD preserves the native fractional WORD. */
    public record Result(CdObjectState expectedObject,CdObjectState object,CdPlayerMotionState expectedMotion,
                         CdPlayerMotionState motion,Set<CdIntroSpikes.PlayerStore> executedWrites,boolean draw) {
        public Result {executedWrites=Set.copyOf(executedWrites);}
    }
    private final Assets assets;
    public CdRollTunnel(Assets assets){this.assets=Objects.requireNonNull(assets);}
    public Result step(CdObjectState before,CdPlayerMotionState motion,Gates gates) {
        Objects.requireNonNull(before);Objects.requireNonNull(motion);Objects.requireNonNull(gates);
        if(before.id()!=0x35)throw new IllegalArgumentException("Expected CC RollTunnel35");
        P5Call.routine(before.routine(),2);
        var o=before;
        if(o.routine()==0)o=o.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,o.spriteFlags()|4)
                .moveWord(TILE,0x544).moveLong(MAPPINGS,assets.powerupMappings())
                .moveByte(MAP_FRAME,o.subtype()+1);
        var stores=EnumSet.noneOf(CdIntroSpikes.PlayerStore.class);
        var result=motion;
        if(motion.animation()!=0x2B && motion.routine()<6 && (gates.debugModeWord()>>>8)==0
                && range(motion.centreX16_16()>>>16,o.centreX()) && range(motion.centreY16_16()>>>16,o.centreY())) {
            int xv=motion.xVelocity(),yv=motion.yVelocity(),gv=motion.groundVelocity(),flags=motion.flags();
            int subtype=o.subtype(),y=motion.centreY16_16(),xr=motion.xRadius(),yr=motion.yRadius(),anim=motion.animation();
            if(subtype==0) {
                xv=minimum(xv,gates.zone()==5?0xD00:0xA00);gv=xv;
                if((((motion.angle()+0x20)&255)&0xC0)==0x80)gv=(short)-gv;
                stores.add(X_VELOCITY);stores.add(GROUND_VELOCITY);
            } else if(subtype==1) {
                yv=minimum(yv,0xD00);gv=yv;flags|=2;
                stores.add(Y_VELOCITY);stores.add(GROUND_VELOCITY);
            } else if(subtype==4) {
                if(xv>=0 && (gates.held()&1)!=0){yv=-0xA00;gv=yv;flags|=2;stores.add(Y_VELOCITY);stores.add(GROUND_VELOCITY);}
            } else if(subtype==2?yv<0:yv>=0) {
                int v=(gates.held()&8)!=0?0xD00:(gates.held()&4)!=0?-0xD00:0;
                if(v!=0){if(subtype!=2)flags|=2;xv=v;gv=v;stores.add(X_VELOCITY);stores.add(GROUND_VELOCITY);}
            }
            boolean rolling=(flags&4)!=0;flags|=4;stores.add(FLAGS);
            if(!rolling){yr=0xE;xr=7;y=((((y>>>16)+5)&0xFFFF)<<16)|(y&0xFFFF);anim=2;
                stores.add(Y_RADIUS);stores.add(X_RADIUS);stores.add(Y_WORD);stores.add(ANIMATION);}
            result=new CdPlayerMotionState(motion.centreX16_16(),y,xv,yv,gv,motion.routine(),motion.angle(),flags,
                    motion.spriteFlags(),xr,yr,anim,motion.previousAnimation(),motion.playerJump(),motion.charge(),
                    motion.moveLock(),motion.stick(),motion.playerControl(),motion.previousPrimaryAngle(),motion.previousSecondaryAngle());
        }
        return new Result(before,o,motion,result,stores,(gates.debugCheatWord()>>>8)!=0);
    }
    private static int minimum(int v,int floor){int magnitude=v<0?(short)-v:v;if((magnitude&0xFFFF)<floor)magnitude=floor;return v<0?(short)-magnitude:(short)magnitude;}
    private static boolean range(int player,int origin){int d=(player-origin+0x28)&0xFFFF;return (short)d>=0&&d<0x50;}
    private static void byteValue(int v){if((v&~255)!=0)throw new IllegalArgumentException("BYTE");}
    private static void wordValue(int v){if((v&~65535)!=0)throw new IllegalArgumentException("WORD");}
}
