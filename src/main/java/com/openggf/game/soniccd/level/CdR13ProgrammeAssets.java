package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdCommand;
import com.openggf.game.soniccd.CdNativeFile;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Detached original R13 programme operands, separate from the existing terrain/object inventories.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: PlayLevelMusic/MusicPlayCmds
 * (Level/Main.asm:555-588). This is source data and byte arithmetic, never SubCPU/audio authority.
 */
public final class CdR13ProgrammeAssets {
    public static final class UnownedMusicByte extends UnsupportedOperationException {
        private final int address;
        private UnownedMusicByte(int address) { super("Unowned native MusicPlayCmds byte at $"+Integer.toHexString(address));this.address=address; }
        public int address() { return address; }
    }
    private final CdR13FutureAssets assets;
    private final CdAssetBytes music;
    private final int address;
    private CdR13ProgrammeAssets(CdR13FutureAssets assets) {
        this.assets=Objects.requireNonNull(assets,"actual original R13 aggregate");assets.requireAuthenticatedLayout();
        var image=assets.originalImage();boolean good=assets.identity().file()==CdNativeFile.R13C;
        if(image.fileBytes()!=262144||!CdR11aProfile.sha256(image.readFile(0,image.fileBytes())).equals(assets.identity().mmdSha256()))
            throw new IllegalArgumentException("Programme image differs from its authenticated aggregate");
        int offset=good?0x1A30:0x1A9C;address=0x200000+offset;
        byte[] consumer=HexFormat.of().parseHex(good?"7000103b10084ef900205a08":"7000103b10084ef900205a20");
        if(!java.util.Arrays.equals(consumer,image.readFile(offset-12,12)))
            throw new IllegalArgumentException("Original indexed-PC music consumer differs");
        music=CdAssetBytes.copyOf(image.readFile(offset,28));
        if(!CdR11aProfile.sha256(music.copy()).equals("6d7c2e0d13ed1ddb7b8f04090f806e86600cc3e5fffe7c68a1e999fb89542932"))
            throw new IllegalArgumentException("Original complete MusicPlayCmds table differs");
    }
    public static CdR13ProgrammeAssets fromAssets(CdR13FutureAssets assets) { return new CdR13ProgrammeAssets(assets); }
    public boolean ownsAssets(CdR13FutureAssets assets) { return this.assets==assets; }
    public CdAssetBytes musicCommands() { return music; }
    public int musicCommandsAddress() { return address; }
    public CdCommand selectLevelMusic(int rawZoneActWord,int rawPeriodByte,int timeAttackByte,int goodFutureByte) {
        if((rawZoneActWord&~65535)!=0)throw new IllegalArgumentException("Native zoneAct WORD required");
        for(int value:new int[]{rawPeriodByte,timeAttackByte,goodFutureByte})
            if((value&~255)!=0)throw new IllegalArgumentException("Native music selector BYTE required");
        int period=rawPeriodByte&0x7F;
        if(timeAttackByte==0&&period==2)period=(period+goodFutureByte)&255;
        int index=((rawZoneActWord>>>8)*4+period)&65535;
        if(index>=music.length())throw new UnownedMusicByte(address+index);
        return CdCommand.fromId(music.unsignedByte(index));
    }
}
