package com.openggf.game.sonic2.competition.view;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.slotmachine.CNZSlotMachineManager;
import java.util.Objects;
import java.util.function.Supplier;
import static com.openggf.game.sonic2.Sonic2RomAddresses.address;

/** Read-only reel observer; the existing CNZ manager alone advances reel state. */
public final class Sonic2CompetitionSlotPictures {
    private final RomByteReader rom;
    private final Sonic2CompetitionArtResidency art;
    private final Supplier<CNZSlotMachineManager.Snapshot> source;
    public record Observation(int routine,int index,java.util.List<Integer> positions) {
        public Observation { positions=java.util.List.copyOf(positions); }
    }
    private Observation previous;
    public record State(Observation previous) { }
    public Sonic2CompetitionSlotPictures(RomByteReader rom, Sonic2CompetitionArtResidency art,
                                       Supplier<CNZSlotMachineManager.Snapshot> source) {
        this.rom=Objects.requireNonNull(rom); this.art=Objects.requireNonNull(art);
        this.source=Objects.requireNonNull(source); previous=observe(source.get());
    }
    public void update() {
        var current=observe(source.get());
        if (previous!=null && current!=null) {
            int index=previous.index();
            // SlotMachine_Routine2/4/5 dispatch DrawSlot exactly once. Init and
            // target setup do not draw, even when they replace every reel word.
            boolean drew=previous.routine()==4 || previous.routine()==12 || previous.routine()==16;
            if (drew && current.index()==(index+4)%12) {
                int slot=index/4, before=previous.positions().get(slot), after=current.positions().get(slot);
                // SlotMachine_Subroutine1/2/3 (s2.asm:59720-59776): redraw only
                // after crossing a whole pixel row, preserving its one-reel order.
                if ((before&0x7F8)!=(after&0x7F8)) art.queueUpload(0x750+slot*16, compose(slot,after));
            }
        }
        previous=current;
    }
    private static Observation observe(CNZSlotMachineManager.Snapshot value) {
        return value==null ? null : new Observation(value.routine(),value.index(),value.slotPos());
    }
    byte[] compose(int slot,int position) {
        if (slot<0 || slot>2) throw new IllegalArgumentException("Native reel index");
        byte[] image=new byte[16*32];
        // SlotMachine_GetPixelRow (s2.asm:59779-59794), SlotSequence1/2/3
        // (s2.asm:59928-59934): native art and scratch layout are column-major.
        for (int row=0;row<32;row++) {
            int cursor=((position&0x7F8)+row*8)&0x7FF;
            int face=rom.readU8(address(rom,0x2C401)+slot*8+(cursor>>>8))&7;
            int line=(cursor&0xF8)>>>3;
            for (int column=0;column<4;column++) {
                int p=address(rom,0x4EEFE)+face*512+column*128+line*4;
                for (int b=0;b<4;b++) image[column*128+row*4+b]=(byte)rom.readU8(p+b);
            }
        }
        return image;
    }
    public State capture() { return new State(previous); }
    public void restore(State state) { previous=state.previous(); }
}
