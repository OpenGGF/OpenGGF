package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.media.CdProgramAssets;
import java.io.IOException;
import java.util.*;

/** Actual disc-backed PPZ DATA borrow. Descriptors are never source or lifecycle authority.
 * This facade consumes the shared reader; existing PPZ runtime owners are not replaced.
 * Root must qualify an actual caller migration before deleting their legacy parsing loops. */
public final class CdPpzProgrammeData {
    public record EntryData(CdAssetBytes index,CdAssetBytes cameraBounds,CdAssetBytes startCentre,CdAssetBytes specialChunks) { }
    private final CdPpzDataLayout.Programme programme;
    private final CdProgrammeReader reader;
    private CdR11bObjectData objectSource;
    private CdPpzProgrammeData(CdProgrammeReader reader) {
        this.reader=Objects.requireNonNull(reader);reader.requireRetained();
        programme=Arrays.stream(CdPpzDataLayout.Programme.values()).filter(p->p.payload().stage().equals(reader.stage()))
            .findFirst().orElseThrow(()->new IllegalArgumentException("Not a PPZ DATA reader"));
        if(reader.definition()!=CdProgrammeDefinition.select(programme.payload().stage()))
            throw new IllegalArgumentException("PPZ retained definition differs");
    }
    public static CdPpzProgrammeData open(CdProgramAssets actual,CdPpzDataLayout.Programme programme)throws IOException {
        Objects.requireNonNull(programme,"explicit PPZ programme");
        return fromReader(CdProgrammeReader.open(actual,programme.payload().stage()));
    }
    static CdPpzProgrammeData fromReader(CdProgrammeReader actual) { return new CdPpzProgrammeData(actual); }
    public CdPpzDataLayout.Programme programme() { return programme; }
    public void requireRetained() { reader.requireRetained(); }
    public String mmdSha256() { return reader.mmdSha(); }
    public String wholeDiscSha256() { return reader.discSha(); }
    /** Identity-based membership in this closed programme; equal/foreign Range values fail. */
    public CdAssetBytes view(CdPpzDataLayout.Range declaration) { return reader.view(declaration); }
    public CdAssetBytes canonicalInput(CdPpzDataLayout.Range declaration) { return reader.canonicalInput(declaration); }
    public CdAssetBytes javaInput(CdPpzDataLayout.Range declaration) { return reader.javaInput(declaration); }
    public CdAssetBytes nativeInput(CdPpzDataLayout.Range declaration) { return reader.nativeInput(declaration); }
    public CdNemesisAsset.Decoded decoded(CdPpzDataLayout.Range declaration)throws IOException { return reader.decoded(declaration); }
    public CdSonicVisualAssets sonic() { return reader.sonic(); }
    public CdObjectPlacements placements() { return reader.placements(); }
    public CdStageTerrain terrain()throws IOException { return reader.terrain(); }
    /** Same-reader immutable source only. Unbound PPZ alternatives still fail closed. */
    public CdLevelLoadingSource levelLoadingSource() { return reader.levelLoadingSource(); }
    public CdStagePlayerSource playerSource()throws IOException { return reader.playerSource(); }
    /** Pure original B physical object catalogue/mapper DATA, never a World or invocation. */
    public synchronized CdR11bObjectData objectSource(){
        reader.requireRetained();if(objectSource==null)objectSource=CdR11bObjectData.fromReader(this,reader);
        else objectSource.requireRetained();return objectSource;
    }
    public EntryData entryData() {
        return new EntryData(view(programme.range("LevelDataIndex")),view(programme.range("CamBounds")),
            view(programme.range("LevelStartLoc")),view(programme.range("SpecChunks")));
    }
    // Internal shared-table product. This does not provide a service, queue, World or player binding.
    CdProgrammeLoadTables tables() { return reader.loadTables(); }
    boolean ownsReader(CdProgrammeReader actual) { return reader==actual; }
}
