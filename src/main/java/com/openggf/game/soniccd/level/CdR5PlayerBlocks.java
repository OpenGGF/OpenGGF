package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.player.CdR5Conveyor;
import java.io.IOException;
import java.util.Objects;

/** Authenticated R5 finite DATA adapter over the existing same-reader caches, never a loader,
 * decoder clone or source of reverse/checkpoint knowledge. Native r5/player_block_check.asm:78-304
 * owns directory order B/A/D/C and count-minus-one. Whole MMD, registered canonical keys and source
 * operands are authenticated by the existing common Reader. A public equal Range is not a key.
 * Constructing/reading DATA provides no gameplay, dispatch, art residency or publication authority. */
public final class CdR5PlayerBlocks implements CdR5Conveyor.BlockDirectory {
    private final CdProgrammeReader reader;
    private final CdR5DataLayout.Programme programme;
    private CdObjectVisualAssets visual;
    private CdR5PlayerBlocks(CdProgrammeReader reader,CdR5DataLayout.Programme programme){
        this.reader=Objects.requireNonNull(reader);this.programme=Objects.requireNonNull(programme);
        if(reader.definition()!=CdProgrammeDefinition.select(programme.payload().stage())||reader.file()!=programme.file())
            throw new IllegalArgumentException("Actual R5 reader and canonical programme differ");
        reader.requireRetained();
        // Construction validates source relations only. Runtime directory/count/entry reads below
        // remain separate caller cuts and are not replaced with this preflight's scalar values.
        var directory=reader.view(programme.range("PlayerBlocksDirectory"));
        for(int slot=0;slot<4;slot++)if(programme.range("PlayerBlocksDirectory").fileOffset()
                +(short)directory.unsignedWord(slot*2)!=programme.range("PlayerBlocks"+slot+"Read").fileOffset())
            throw new IllegalArgumentException("Original player-block directory relation differs");
    }
    public static CdR5PlayerBlocks open(CdProgramAssets actual,CdR5DataLayout.Programme programme)throws IOException{
        Objects.requireNonNull(programme);return fromReader(CdProgrammeReader.open(actual,programme.payload().stage()),programme);
    }
    /** Same-reader Root join: does not reopen media or create another runtime owner. */
    static CdR5PlayerBlocks fromReader(CdProgrammeReader actual,CdR5DataLayout.Programme programme){return new CdR5PlayerBlocks(actual,programme);}
    public CdR5DataLayout.Programme programme(){return programme;}
    boolean ownsReader(CdProgrammeReader actual){return reader==actual;}
    /** Only this family's fixed canonical key objects; arbitrary offsets and other raw assets reject. */
    public CdAssetBytes view(CdR5DataLayout.Range key){
        Objects.requireNonNull(key);
        boolean allowed=key==programme.range("PlayerBlocksDirectory")||key==programme.range("ConveyorSwitchSprites");
        for(int slot=0;slot<4;slot++)allowed|=key==programme.range("PlayerBlocks"+slot+"Read");
        if(!allowed)throw new IllegalArgumentException("Foreign conveyor-family source key");
        return reader.view(key);
    }
    @Override public int directoryWord(int slot){
        Objects.checkIndex(slot,4);return view(programme.range("PlayerBlocksDirectory")).unsignedWord(slot*2);
    }
    private CdAssetBytes list(int slot){
        Objects.checkIndex(slot,4);
        var key=programme.range("PlayerBlocks"+slot+"Read");
        return view(key);
    }
    @Override public int countMinusOne(int slot){return list(slot).unsignedWord(0);}
    @Override public int entryWord(int slot,int index){
        var bytes=list(slot);int count=bytes.unsignedWord(0)+1;
        Objects.checkIndex(index,count);
        if(2+2*count!=bytes.length())throw new IllegalArgumentException("PlayerBlocks count/read hull differs");
        return bytes.unsignedWord(2+index*2);
    }
    private void requireSwitch(){
        var selected=programme.dispatch().stream().filter(d->d.id()==0x31).findFirst().orElseThrow();
        if(!selected.handler().equals("ConveyorSwitchObject"))throw new UnsupportedOperationException("Native ID31 is not ConveyorSwitch in this selected programme");
        // This is the actual registered table, not a dispatch claim based on the handler's name.
        long target=reader.view(programme.range("ObjectIndex")).unsignedLong((0x31-1)*4);
        if(target!=(selected.nativeAddress()&0xFFFFFFFFL))throw new IllegalArgumentException("Original ConveyorSwitch dispatch operand differs");
    }
    public int switchMappingAddress(){requireSwitch();return 0x200000+programme.range("ConveyorSwitchSprites").fileOffset();}
    public synchronized CdObjectVisualAssets switchVisual(){
        requireSwitch();
        if(visual==null){
            int tiles=switchArtCanonicalInput().unsignedWord(0)&0x7FFF;
            if(tiles!=15)throw new IllegalArgumentException("Original conveyor-control art must supply fifteen tiles");
            visual=CdObjectVisualAssets.decodeStatic(view(programme.range("ConveyorSwitchSprites")).copy(),3,tiles);
        }
        return visual;
    }
    /** Existing compressed-input cache, no new decompression or synthetic art. Root still supplies
     * actual PLC/current-art residency and the finite mapper registration before Draw can run. */
    public CdAssetBytes switchArtCanonicalInput(){
        requireSwitch();String key=switch(programme){
            case R51A,R52A -> "ConveyorCtrlGfxA";case R51B,R52B -> "ConveyorCtrlGfxB";
            case R51C,R52C -> "ConveyorCtrlGfxC";case R51D,R52D -> "ConveyorCtrlGfxD";
            case R53C,R53D -> throw new UnsupportedOperationException("Native Act3 ID31 is Null");
        };return reader.canonicalInput(programme.range(key));
    }
}
