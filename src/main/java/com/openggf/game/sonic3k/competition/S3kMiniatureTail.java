package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;
import com.openggf.level.objects.NativeSpriteSubmissionSource;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.TrigLookupTable;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Obj_Tails2P_Tail, in fixed native SST96/97, not a miniature renderer attachment.
 * Copies the current parent slot on every dispatch; animation and Draw_Sprite remain
 * independent of body blink and duplicate-character render flags. Owning routines:
 * loc_16232 (sonic3k.asm:30176-30211).
 * Animate_Tails_Tail2P (sonic3k.asm:29718-29745).
 * sub_15D1E (sonic3k.asm:29746-29825).
 * loc_15F08 (sonic3k.asm:29958-29983).
 * Tails2P_Tail_Load_PLC (sonic3k.asm:29999-30030).
 */
public final class S3kMiniatureTail extends AbstractObjectInstance
        implements SpawnRewindRecreatable, NativeSpriteSubmissionSource {
    public static final String ART_KEY="competition_tails_tail";
    public static final int P1_FIXED_SLOT=96, P2_FIXED_SLOT=97;

    /** Immutable ROM selection/scripts. Mappings and DPLC-remapped art belong to Assets.tailsTail(). */
    public static final class Bank {
        public static final int SELECTION_ADDRESS=0x162A0, ANIMATION_ADDRESS=0x162C2;
        private final byte[] selection;
        private final byte[][] scripts;

        public Bank(byte[] selection,byte[][] scripts) {
            if (selection.length!=34 || scripts.length!=4)
                throw new IllegalArgumentException("Native miniature tail table shape differs");
            this.selection=selection.clone(); this.scripts=new byte[4][];
            for (int i=0;i<4;i++) this.scripts[i]=Objects.requireNonNull(scripts[i]).clone();
        }
        public static Bank fromReader(RomByteReader reader) {
            return fromReader(reader,S3kCompetitionResources.LOCKED_ON);
        }
        public static Bank fromReader(RomByteReader reader,S3kCompetitionResources resources) {
            Objects.requireNonNull(reader,"Miniature tail requires the injected ROM reader");
            int animation=resources.at(ANIMATION_ADDRESS);
            byte[][] scripts=new byte[4][];
            for (int i=0;i<4;i++) scripts[i]=reader.slice(animation
                    +(short)reader.readU16BE(animation+i*2),256);
            return new Bank(reader.slice(resources.at(SELECTION_ADDRESS),34),scripts);
        }
        public static Bank fromRom(Rom rom) throws IOException { return fromReader(RomByteReader.fromRom(rom)); }
        public int selectionFor(int parentAnimation) { return read(selection,parentAnimation); }
        public byte[] scriptBytes(int animation) { return scripts[animation].clone(); }
        private int scriptByte(int animation,int index) { return read(scripts[animation],index); }
        private static int read(byte[] bytes,int index) {
            if (index<0 || index>=bytes.length) throw new IllegalStateException("Miniature tail left its ROM table");
            return bytes[index]&0xFF;
        }
    }

    private int parentSlot;
    private boolean initialized;
    private int x,y,angle,status,priorityWord=0x100;
    private boolean highPriority;
    private int renderFlags;
    private int parentAnimation;
    private int animation,previousAnimation,animationFrame,animationTimer,mappingFrame;
    private boolean drawPublished;
    @RewindTransient(reason="Immutable ROM configuration; recreated native tail reloads the injected reader")
    private Bank romBank;

    /** Supported probe/recreation constructor; the logical spawn retains the native parent slot. */
    public S3kMiniatureTail(ObjectSpawn spawn) {
        super(spawn,"CompetitionTailsTail"); parentSlot=spawn.subtype()&1;
    }
    public S3kMiniatureTail(int parentSlot) { this(logicalSpawn(parentSlot)); }
    private static ObjectSpawn logicalSpawn(int parentSlot) {
        if (parentSlot<0 || parentSlot>1) throw new IllegalArgumentException("Native tail parent must be P1 or P2");
        return new ObjectSpawn(0,0,0,parentSlot,0,false,0);
    }

    @Override public void update(int vIntRunCount,PlayableEntity ignored) {
        if (isDestroyed()) return;
        var session=WorldSessionPolicyAccess.getService(services().worldSession(),S3kCompetitionSession.class);
        if (session==null) return;
        if (romBank==null) {
            try { romBank=Bank.fromReader(services().romReader(),session.resources()); }
            catch (IOException failure) { throw new java.io.UncheckedIOException("Cannot read native miniature tail ROM",failure); }
        }
        updateNative(session.player(parentSlot),romBank);
        // Draw_Sprite is called on every native tail dispatch, even while its body is blinking.
        NativeSpriteSubmissionAccess.submitSpilling(services().objectManager(),getSlotIndex(),getPriorityBucket());
    }

    public void updateNative(S3kCompetitionPlayer parent,Bank bank) {
        if (isDestroyed()) return;
        Objects.requireNonNull(parent); Objects.requireNonNull(bank);
        if (parent.slot()!=parentSlot) throw new IllegalArgumentException("Tail resolved a different native parent slot");
        if (!initialized) { initialized=true; renderFlags=4; }
        x=parent.centreX()&0xFFFF; y=parent.centreY()&0xFFFF;
        angle=parent.angle(); status=parent.status(); priorityWord=parent.nativePriorityWord();
        highPriority=parent.highPriority();
        int selected=parent.animation();
        if ((status&0x20)!=0 && selected!=5) selected=4;
        if (selected!=parentAnimation) {
            parentAnimation=selected; animation=bank.selectionFor(selected);
        }
        if (animation!=previousAnimation) {
            previousAnimation=animation; animationFrame=animationTimer=0; status&=~0x20;
        }
        int delay=bank.scriptByte(animation,0);
        if (delay<0x80) {
            renderFlags=(renderFlags&0xFC)|(status&1);
            animationTimer=(animationTimer-1)&0xFF;
            if ((byte)animationTimer<0) { animationTimer=delay; advance(bank); }
        } else {
            // The shipped four-entry tail bank's sole negative-delay script is $FC.
            if (delay!=0xFC) throw new IllegalStateException("Unexpected native miniature tail delay");
            animationTimer=(animationTimer-1)&0xFF;
            if ((byte)animationTimer<0) {
                int direction=TrigLookupTable.calcAngle((short)parent.xVelocity(),(short)parent.yVelocity());
                int facing=status&1;
                direction=(facing==0?~direction:direction+0x80)&0xFF;
                direction=(direction+0x10)&0xFF;
                renderFlags=(renderFlags&0xFC)|(facing^((byte)direction<0?3:0));
                int offset=((direction>>>5)&3)*3;
                animationTimer=3; advance(bank); mappingFrame=(mappingFrame+offset)&0xFF;
            }
        }
        drawPublished=true;
        updateDynamicSpawn(x,y);
    }

    private void advance(Bank bank) {
        int value=bank.scriptByte(animation,1+animationFrame);
        if (value<0xF0) {
            mappingFrame=value; animationFrame=(animationFrame+1)&0xFF;
        } else if (value==0xFF) {
            mappingFrame=bank.scriptByte(animation,1); animationFrame=1;
        } else if (value==0xFE) {
            animationFrame=(animationFrame-bank.scriptByte(animation,2+animationFrame))&0xFF;
            mappingFrame=bank.scriptByte(animation,1+animationFrame); animationFrame=(animationFrame+1)&0xFF;
        } else if (value==0xFD) animation=bank.scriptByte(animation,2+animationFrame);
    }

    public int parentSlot() { return parentSlot; }
    public int nativeFixedSlot() { return P1_FIXED_SLOT+parentSlot; }
    public int mappingFrame() { return mappingFrame; }
    public int animation() { return animation; }
    public int animationFrame() { return animationFrame; }
    public int animationTimer() { return animationTimer; }
    public int nativeAngle() { return angle; }
    public int nativeStatus() { return status; }
    public int nativeRenderFlags() { return renderFlags; }
    public int nativeArtWord() { return (parentSlot==0?0x690:0x6B0)|(highPriority?0x8000:0); }
    public boolean nativeDrawPublished() { return drawPublished; }
    /** Caller owns actual queued-pointer admission, including a replacement occupant's carried entry. */
    public void refreshNativeVisibility(boolean visible) {
        renderFlags=(renderFlags&0x7F)|(visible?0x80:0);
    }
    // Fixed helpers have no placement-table ID. This nonzero live-routine sentinel keeps an
    // installed Obj_Tails2P_Tail pointer eligible for late SST render lookup before its first tick.
    @Override public int getLiveObjectId() { return isDestroyed()?0:1; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getOnScreenHalfWidth() { return 0x18; }
    @Override public int getOnScreenHalfHeight() { return 0x18; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(priorityWord); }
    @Override public boolean isHighPriority() { return highPriority; }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawPublished || isDestroyed()) return;
        var renderer=getRenderer(ART_KEY);
        if (renderer!=null) renderer.drawFrameIndex(mappingFrame,x,y,(renderFlags&1)!=0,(renderFlags&2)!=0);
    }
}
