package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.media.CdProgramAssets;
import java.io.IOException;
import java.util.*;

/** Internal authenticated TT DATA borrow. Public declaration values never construct a source owner. */
final class CdProgrammeData {
    private final CdProgrammeReader reader;
    private final CdR4DataLayout.Programme programme;
    private CdProgrammeData(CdProgrammeReader reader) {
        this.reader=Objects.requireNonNull(reader);reader.requireRetained();
        programme=reader.definition().tt().orElseThrow(()->new IllegalArgumentException("Not a TT data definition"));
    }
    static CdProgrammeData open(CdProgramAssets actual,CdR4DataLayout.Programme programme)throws IOException {
        Objects.requireNonNull(programme,"explicit TT programme");
        return fromReader(CdProgrammeReader.open(actual,programme.payload().stage()));
    }
    static CdProgrammeData fromReader(CdProgrammeReader actual) { return new CdProgrammeData(actual); }
    boolean ownsReader(CdProgrammeReader actual) { return reader==actual; }
    CdR4DataLayout.Programme programme() { return programme; }
    CdAssetBytes view(CdR4DataLayout.Range declaration) { return reader.view(declaration); }
    CdAssetBytes canonicalInput(CdR4DataLayout.Range declaration) { return reader.canonicalInput(declaration); }
    CdAssetBytes javaInput(CdR4DataLayout.Range declaration) { return reader.javaInput(declaration); }
    CdAssetBytes nativeInput(CdR4DataLayout.Range declaration) { return reader.nativeInput(declaration); }
    CdNemesisAsset.Decoded decoded(CdR4DataLayout.Range declaration)throws IOException { return reader.decoded(declaration); }
    CdR4ProgrammeProfiles.Product productContract(CdR4DataLayout.Range declaration) {
        reader.canonicalInput(declaration); // Actual owner authenticates the key/include before descriptive facts.
        return CdR4ProgrammeProfiles.contract(programme,declaration);
    }
    List<CdR4DataLayout.Range> compressedRanges() {
        return reader.definition().sources().stream().map(s->(CdR4DataLayout.Range)s.key()).toList();
    }
    CdSonicVisualAssets sonic() { return reader.sonic(); }
    CdProgrammeLoadTables tables() { return reader.loadTables(); }
}
