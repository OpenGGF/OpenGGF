package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.resources.CdR11aPlcBuffer;
import java.util.List;
import java.util.Objects;

/**
 * Original R13 C/D loading metadata over an actual immutable aggregate, not a loading/service owner.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Level/Main.asm:156-165 immediate index3 then1;
 * Load Level Data (Act3).asm:34-43 queues index2 only if plcLoadFlags bit1 is set;
 * Section Graphics (Act3).asm:7-10 Init/Update RTS. No fake section threshold or kernel.
 * Caller owns actual native clear, bits, order, queue adoption and all service/current-art operations.
 */
public final class CdR13LoadPlan {
    public enum Delivery { IMMEDIATE, QUEUED }
    public enum Condition { ALWAYS, PLC_LOAD_FLAGS_BIT_1_SET }
    /** Inert original descriptor values; construction/exposure executes nothing. */
    public record PlcOperation(Delivery delivery,Condition condition,int nativeId,CdR13FutureAssets.PlcList sourceList,
                               List<CdR11aPlcBuffer.Entry> entries) {
        public PlcOperation {
            Objects.requireNonNull(delivery);Objects.requireNonNull(condition);Objects.requireNonNull(sourceList);entries=List.copyOf(entries);
            if(nativeId!=sourceList.nativeId() || entries.size()!=sourceList.entries().size()
                    || (delivery==Delivery.IMMEDIATE)!=(condition==Condition.ALWAYS))throw new IllegalArgumentException("R13 PLC operation metadata differs");
            for(int i=0;i<entries.size();i++){var original=sourceList.entries().get(i);var entry=entries.get(i);if(entry.source()!=original.sourceNativeAddress() || entry.destination()!=original.vramAddress())throw new IllegalArgumentException("R13 operation rows differ from original list");}
        }
    }
    private final CdR13FutureAssets assets;
    private final CdR13SectionSources sources;
    private final List<PlcOperation> levelStart,levelData;
    private CdR13LoadPlan(CdR13FutureAssets assets){
        this.assets=Objects.requireNonNull(assets,"actual R13 assets");sources=CdR13SectionSources.fromAssets(assets);
        int first=assets.levelIndex().levelArt().plcId(),queued=assets.levelIndex().blocks().plcId();
        if(first!=3 || queued!=2)throw new IllegalArgumentException("R13 original load index selectors differ");
        levelStart=List.of(operation(first,Delivery.IMMEDIATE,Condition.ALWAYS),operation(1,Delivery.IMMEDIATE,Condition.ALWAYS));
        levelData=List.of(operation(queued,Delivery.QUEUED,Condition.PLC_LOAD_FLAGS_BIT_1_SET));
    }
    public static CdR13LoadPlan fromAssets(CdR13FutureAssets assets){return new CdR13LoadPlan(assets);}
    public boolean ownsAssets(CdR13FutureAssets assets){return this.assets==assets;}
    public CdR13FutureAssets.Identity identity(){return assets.identity();}
    public CdR13SectionSources sources(){return sources;}
    public CdStageTerrain terrain(){return assets.terrain();}
    public CdNemesisAsset.Decoded blocksDecode(){return assets.blocksDecode();}
    public CdNemesisAsset.Decoded levelTilesDecode(){return assets.levelTilesDecode();}
    public CdR11bLoadPlan.LevelIndex levelIndex(){return assets.levelIndex();}
    public CdR11bLoadPlan.Bounds bounds(){return assets.bounds();}
    public CdR11bLoadPlan.InitialCentre initialCentre(){return assets.initialCentre();}
    public List<CdR13FutureAssets.PlcList> plcs(){return assets.plcs();}
    public List<CdR13FutureAssets.PaletteBinding> paletteBindings(){return assets.paletteBindings();}
    public CdAssetBytes initialPalette(){return assets.initialPalette();}
    public List<PlcOperation> levelStartOperations(){return levelStart;}
    public List<PlcOperation> levelDataOperations(){return levelData;}
    public List<PlcOperation> initSectionOperations(){return List.of();}
    public List<PlcOperation> updateSectionOperations(){return List.of();}
    private PlcOperation operation(int id,Delivery delivery,Condition condition){
        var list=assets.plcs().get(id);var entries=list.entries().stream().map(row->{sources.source(row.sourceNativeAddress());return new CdR11aPlcBuffer.Entry(row.sourceNativeAddress(),row.vramAddress());}).toList();
        return new PlcOperation(delivery,condition,id,list,entries);
    }
}
