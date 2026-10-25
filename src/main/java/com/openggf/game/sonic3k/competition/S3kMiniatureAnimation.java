package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import java.io.IOException;
import java.util.Objects;

/**
 * Animate_SonicKnuckles_2P / Animate_Tails2P, including their speed-dependent
 * script selection and byte timers (sonic3k.asm:25272-25513,29675-29952).
 * Every script byte is loaded from the supplied locked-on ROM.
 */
public final class S3kMiniatureAnimation {
    public static final int SONIC_TABLE=0x12F84, KNUCKLES_TABLE=0x12FCC, TAILS_TABLE=0x15F60;
    public record State(int previousAnimation,int frame,int timer,int mappingFrame,int renderFlips) {
        public static final State INITIAL=new State(-1,0,0,0,0);
    }
    /** Immutable decoded pointer tables; raw-byte injection supports independent native oracles. */
    public static final class Bank {
        private final byte[][] sonic,knuckles,tails;
        public Bank(byte[][] sonic,byte[][] knuckles,byte[][] tails) {
            this.sonic=copy(sonic,36);this.knuckles=copy(knuckles,36);this.tails=copy(tails,33);
        }
        private static byte[][] copy(byte[][] source,int expected) {
            if(source.length!=expected) throw new IllegalArgumentException("Incorrect miniature animation table size");
            byte[][] result=new byte[source.length][];
            for(int i=0;i<source.length;i++) result[i]=Objects.requireNonNull(source[i]).clone();
            return result;
        }
        private byte[] script(S3kCompetitionPlayer.Character character,int animation) {
            byte[][] table=switch(character) {case SONIC->sonic;case TAILS->tails;case KNUCKLES->knuckles;};
            if(animation<0 || animation>=table.length)
                throw new IllegalStateException("Native miniature animation out of range: "+animation);
            return table[animation];
        }
        public static Bank fromRom(Rom rom) throws IOException {
            return new Bank(load(rom,SONIC_TABLE,36),load(rom,KNUCKLES_TABLE,36),load(rom,TAILS_TABLE,33));
        }
        private static byte[][] load(Rom rom,int base,int entries) throws IOException {
            byte[] pointers=rom.readBytes(base,entries*2);
            byte[][] result=new byte[entries][];
            for(int i=0;i<entries;i++) {
                int offset=(short)(((pointers[i*2]&255)<<8)|(pointers[i*2+1]&255));
                result[i]=rom.readBytes(base+offset,256);
            }
            return result;
        }
    }
    private final Bank bank;
    public S3kMiniatureAnimation(Bank bank) { this.bank=Objects.requireNonNull(bank); }
    public static S3kMiniatureAnimation fromRom(Rom rom) throws IOException {
        return new S3kMiniatureAnimation(Bank.fromRom(rom));
    }
    public State tick(S3kCompetitionPlayer player,State previous) {
        int anim=player.animation(),frame=previous.frame(),timer=previous.timer(),mapping=previous.mappingFrame();
        if(anim!=previous.previousAnimation()) {
            frame=timer=0;player.setPushing(false);
        }
        byte[] script=bank.script(player.character(),anim);
        int delay=read(script,0),flips=player.facingLeft()?1:0;
        if(delay<0x80) {
            timer=(timer-1)&255;
            if((byte)timer>=0) return new State(anim,frame,timer,mapping,flips);
            return advance(player,script,anim,frame,delay,mapping,flips);
        }
        int speed=Math.abs((short)player.groundVelocity());
        if(delay==0xFF) {
            if(player.flipAngle()!=0) {
                int tumble=player.flipAngle();
                if(!player.facingLeft()) {flips=0;tumble=(tumble+0x16)&255;}
                else if((player.flipType()&0x80)!=0) {flips=1;tumble=(tumble+0x16)&255;}
                else {flips=3;tumble=(-tumble+0x9A)&255;}
                return new State(anim,frame,0,(tumble/0x2C+0x15)&255,flips);
            }
            int angle=player.angle();
            if((byte)angle>0) angle=(angle-1)&255;
            if(!player.facingLeft()) angle=(~angle)&255;
            angle=(angle+0x10)&255;
            flips=(player.facingLeft()?1:0)^((byte)angle<0?3:0);
            if(player.pushing()) {
                timer=(timer-1)&255;
                if((byte)timer>=0) return new State(anim,frame,timer,mapping,flips);
                int pause=Math.max(0,0x800-speed)>>>6;
                if(player.character()==S3kCompetitionPlayer.Character.KNUCKLES) pause>>>=2;
                return advance(player,bank.script(player.character(),4),anim,frame,pause&255,mapping,player.facingLeft()?1:0);
            }
            int angleGroup=(angle>>>5)&3;
            if((player.statusSecondary()&0x80)!=0) speed=(speed*2)&65535;
            boolean running=speed>=0x600;
            script=bank.script(player.character(),running?1:0);
            int value=read(script,1+frame);
            if(value==0xFF) {frame=0;value=read(script,1);}
            mapping=(value+angleGroup*(running?2:3))&255;
            timer=(timer-1)&255;
            if((byte)timer<0) { timer=Math.max(0,0x800-speed)>>>8;frame=(frame+1)&255; }
            return new State(anim,frame,timer&255,mapping,flips);
        }
        timer=(timer-1)&255;
        if((byte)timer>=0) return new State(anim,frame,timer,mapping,previous.renderFlips());
        // Tails' $FD branch is only used by its separate tail SST, whose parent
        // velocity and animation are supplied by that attachment owner. Main
        // miniature Tails roll scripts have a positive delay of 1 instead.
        if(player.character()==S3kCompetitionPlayer.Character.TAILS && delay!=0xFE)
            throw new IllegalStateException("Tail-SST animation cannot execute on the miniature body");
        script=bank.script(player.character(),speed>=0x600?3:2);
        return advance(player,script,anim,frame,Math.max(0,0x400-speed)>>>8,mapping,flips);
    }
    private static State advance(S3kCompetitionPlayer player,byte[] script,int anim,
            int frame,int timer,int mapping,int flips) {
        int value=read(script,1+frame);
        int limit=player.character()==S3kCompetitionPlayer.Character.TAILS?0xF0:0xFC;
        if(value<limit) return new State(anim,(frame+1)&255,timer,value,flips);
        switch(value) {
            case 0xFF -> { frame=0;mapping=read(script,1);frame=1; }
            case 0xFE -> {
                frame=(frame-read(script,2+frame))&255;
                mapping=read(script,1+frame);frame=(frame+1)&255;
            }
            case 0xFD -> player.setAnimation(read(script,2+frame));
            default -> { }
        }
        return new State(anim,frame,timer,mapping,flips);
    }
    private static int read(byte[] bytes,int index) {
        if(index<0 || index>=bytes.length)
            throw new IllegalStateException("Native miniature animation left its ROM script");
        return bytes[index]&255;
    }
}
