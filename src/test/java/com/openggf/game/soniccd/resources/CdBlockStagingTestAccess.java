package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.media.CdMmdImage;
import java.util.*;

/** Test-only adapter to the existing actual shared kernel; no second decoder. Java UNRUN. */
public final class CdBlockStagingTestAccess {
    private CdBlockStagingTestAccess(){}
    private record Value(CdAssetBytes table){}
    public static CdBlockDefinitionStaging decode(CdAssetBytes reader,CdAssetBytes canonical,CdNativeFile file,long address){
        var binding=new CdSectionArtKernel.Binding(new Object(),file,CdMmdImage.Domain.ORIGINAL_WORD_RAM);
        var source=new CdSectionArtKernel.Source(binding,new Object(),file,CdMmdImage.Domain.ORIGINAL_WORD_RAM,
                (int)(address-0x200000),reader.length(),address,reader);
        var kernel=new CdImmediateArtKernel<Value,Object>(Map.of(),Map.of(),source,()->canonical,
                CdAssetBytes.copyOf(new byte[0]),0,new CdImmediateArtKernel.States<>(){
            public void checked(Value v){Objects.requireNonNull(v);}
            public CdAssetBytes codeTable(Value v){return v.table();}
            public Object residency(Value v){return this;}
            public int remainingTilesWord(Value v){return 0;}
            public CdR11aPlcBuffer.State queue(Value v){return CdR11aPlcBuffer.cleared();}
            public Value withValues(Value v,CdAssetBytes table,Object resident){return new Value(table);}
            public RuntimeException failure(CdImmediateArtKernel.Reason r,Value v,CdImmediateArtKernel.Operation o,
                    long source,int destination,CdAssetBytes prefix,Throwable cause){return new IllegalStateException(r+" at "+destination,cause);}
            public void admitDestination(Value v,int d,CdImmediateArtKernel.Operation o,long source){throw new AssertionError("RAM wrote patterns");}
            public Object commitTile(Value v,int d,CdAssetBytes b,CdImmediateArtKernel.Operation o,long source){throw new AssertionError("RAM wrote patterns");}
        });
        return kernel.decodeBlocksToRam(new Value(CdAssetBytes.copyOf(new byte[512]))).blockDefinitions();
    }
}
