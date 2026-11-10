package com.openggf.game.soniccd.level;

import java.io.IOException;
import java.util.Objects;
import static com.openggf.game.soniccd.level.CdR11aAsset.*;

/** Immutable compatibility projection of THIS original R11A reader's existing DATA.
 * No second decoder/parser, copied image, legacy Shape or current block bank is created.
 * The selected-known terrain retains typed unavailable reads; this is not proof of
 * an original file-buffer clear, decoder-row publication or gameplay admission. */
final class CdR11aTerrainProducts {
    private final CdR11aAssets.NemesisRead blocks;
    private final CdR11aAssets.NemesisRead tiles;
    private final CdR11aTerrain terrain;

    CdR11aTerrainProducts(CdProgrammeReader reader)throws IOException {
        Objects.requireNonNull(reader).retainedOriginalR11Image();
        var profile=CdR11aProfile.forStage(com.openggf.game.soniccd.CdReleaseVariant.USA,reader.stage());
        var binding=reader.definition().terrain().orElseThrow();
        // Keep the actual loader's blocks -> tiles -> raw-terrain construction cut.
        blocks=read(reader,profile,LEVEL_BLOCKS,binding.blocks());
        tiles=read(reader,profile,ART_LEVEL_TILES,binding.graphics());
        terrain=CdR11aTerrain.borrow(reader,reader.terrain());
    }
    private static CdR11aAssets.NemesisRead read(CdProgrammeReader reader,CdR11aProfile profile,
            CdR11aAsset asset,Object key)throws IOException {
        var blob=profile.range(asset);
        if(reader.fileOffset(key)!=blob.fileOffset() || reader.view(key).length()!=blob.length())
            throw new IllegalArgumentException("Original R11A terrain source range differs");
        var decoded=reader.decoded(key);
        // Java read extent and cursor already belong to the common authenticated contract.
        // Art_LevelTiles includes its real adjacent input byte; no EOF padding is supplied.
        var input=new CdR11aProfile.FileRange(profile.file(),profile.domain(),blob.fileOffset(),reader.javaInput(key).length());
        return new CdR11aAssets.NemesisRead(asset,blob,input,decoded.consumedInputBytes(),decoded.bytes());
    }
    CdR11aAssets.NemesisRead blocks(){return blocks;}
    CdR11aAssets.NemesisRead tiles(){return tiles;}
    CdR11aTerrain terrain(){return terrain;}
    /** Equality of public descriptors or decoded values never substitutes for ownership. */
    void require(CdR11aAssets.NemesisRead actualBlocks,CdR11aAssets.NemesisRead actualTiles,CdR11aTerrain actualTerrain) {
        if(actualBlocks!=blocks || actualTiles!=tiles || actualTerrain!=terrain)
            throw new IllegalArgumentException("Foreign original R11A terrain source products");
    }
}
