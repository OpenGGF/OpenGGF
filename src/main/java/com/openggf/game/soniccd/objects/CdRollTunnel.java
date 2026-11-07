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
        var shared=CdSpinTunnelTag.prepareRollingMotion(motion,o.centreX(),o.centreY(),o.subtype(),
                new CdSpinTunnelTag.Gates(gates.zone(),gates.debugModeWord(),gates.debugCheatWord(),gates.held()));
        var stores=EnumSet.noneOf(CdIntroSpikes.PlayerStore.class);
        for(var field:shared.executedWrites())stores.add(switch(field){
            case CENTRE_Y_WORD->Y_WORD;case X_VELOCITY->X_VELOCITY;case Y_VELOCITY->Y_VELOCITY;
            case GROUND_VELOCITY->GROUND_VELOCITY;case FLAGS->FLAGS;case Y_RADIUS->Y_RADIUS;
            case X_RADIUS->X_RADIUS;case ANIMATION->ANIMATION;});
        return new Result(before,o,motion,shared.finalMotion(),stores,(gates.debugCheatWord()>>>8)!=0);
    }
    private static void byteValue(int v){if((v&~255)!=0)throw new IllegalArgumentException("BYTE");}
    private static void wordValue(int v){if((v&~65535)!=0)throw new IllegalArgumentException("WORD");}
}
