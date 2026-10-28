package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.resources.CdR11aSectionArt.Reason;
import com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder;
import com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Pure declared configuration checks; no source authentication, media or execution authority. */
class TestCdSectionArtKernel {
    private static final SavedDecoder CLEARED = new SavedDecoder(Writer.CLEARED, 0, 0, 0, 0, 0);

    @Test
    void equalAddressForeignBindingsAndDuplicateSourcesRejectBeforeAnyStateWork() {
        Object identity = new Object();
        var a = binding(identity, CdNativeFile.R11A);
        var equalButDistinct = binding(identity, CdNativeFile.R11A);
        assertEquals(a, equalButDistinct);
        var equalSource = source(equalButDistinct, CdNativeFile.R11A, CdAssetBytes.copyOf(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> kernel(a, List.of(equalSource)));
        var past = binding(identity, CdNativeFile.R11B);
        var pastSource = source(past, CdNativeFile.R11B, CdAssetBytes.copyOf(new byte[0]));
        assertEquals(equalSource.nativeAddress(), pastSource.nativeAddress());
        assertThrows(IllegalArgumentException.class, () -> kernel(a, List.of(pastSource)));
        var own = source(a, CdNativeFile.R11A, CdAssetBytes.copyOf(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> kernel(a, List.of(own, own)));
    }

    @Test
    void fileDomainBlobAndNativePointerMustDescribeTheSameBoundOriginalSource() {
        var a = binding(new Object(), CdNativeFile.R11A);
        var bytes = CdAssetBytes.copyOf(new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> new CdSectionArtKernel.Source(a, "fixture",
                CdNativeFile.R11B, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x1234, 0, 0x201234, bytes));
        assertThrows(IllegalArgumentException.class, () -> new CdSectionArtKernel.Source(a, "fixture",
                CdNativeFile.R11A, CdMmdImage.Domain.RELOCATED_COPY, 0x1234, 0, 0x201234, bytes));
        var relocated = new CdSectionArtKernel.Binding(new Object(), CdNativeFile.R11A, CdMmdImage.Domain.RELOCATED_COPY);
        assertThrows(IllegalArgumentException.class, () -> new CdSectionArtKernel.Source(relocated, "fixture",
                CdNativeFile.R11A, CdMmdImage.Domain.RELOCATED_COPY, 0x1234, 0, 0x201234, bytes));
        assertThrows(IllegalArgumentException.class, () -> new CdSectionArtKernel.Source(a, "fixture",
                CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, -1, 0, 0x201234, bytes));
        assertThrows(IllegalArgumentException.class, () -> new CdSectionArtKernel.Source(a, "fixture",
                CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x1234, -1, 0x201234, bytes));
        assertThrows(IllegalArgumentException.class, () -> new CdSectionArtKernel.Source(a, "fixture",
                CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x1234, 0, 0x201236, bytes));
    }

    @Test
    void emptyFiniteReaderAndMissingProgramsRemainUnsampledConfigurationValues() {
        var a = binding(new Object(), CdNativeFile.R11A);
        var empty = CdAssetBytes.copyOf(new byte[0]);
        var declared = source(a, CdNativeFile.R11A, empty);
        assertSame(empty, declared.readerBytes());
        assertEquals(0, declared.blobLength());
        assertDoesNotThrow(() -> kernel(a, List.of(declared)));
        assertDoesNotThrow(() -> kernel(a, List.of()));
        assertEquals(0, empty.length());
        // Every state/value operation below fails the test if configuration invokes it.
        // The existing legacy tests own reached prepare/decode/unknown-source behavior.
    }

    private static CdSectionArtKernel.Binding binding(Object identity, CdNativeFile file) {
        return new CdSectionArtKernel.Binding(identity, file, CdMmdImage.Domain.ORIGINAL_WORD_RAM);
    }
    private static CdSectionArtKernel.Source source(CdSectionArtKernel.Binding binding, CdNativeFile file,
                                                     CdAssetBytes bytes) {
        return new CdSectionArtKernel.Source(binding, "declared fixture", file,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x1234, 0, 0x201234, bytes);
    }
    private static CdSectionArtKernel<Object, Object> kernel(CdSectionArtKernel.Binding binding,
                                                            List<CdSectionArtKernel.Source> sources) {
        return new CdSectionArtKernel<>(binding, Map.of(),
                List.of(new CdSectionArtKernel.Section(0xFFFF, 0, 0)), sources, untouchedStates(), CLEARED);
    }
    private static CdSectionArtKernel.States<Object, Object> untouchedStates() {
        return new CdSectionArtKernel.States<>() {
            @Override public void checked(Object state) { throw unexpected(); }
            @Override public int sectionWord(Object state) { throw unexpected(); }
            @Override public CdR11aPlcBuffer.State queue(Object state) { throw unexpected(); }
            @Override public CdAssetBytes codeTable(Object state) { throw unexpected(); }
            @Override public CdNemesisPlcDecoder.State decoder(Object state) { throw unexpected(); }
            @Override public SavedDecoder savedDecoder(Object state) { throw unexpected(); }
            @Override public int remainingTilesWord(Object state) { throw unexpected(); }
            @Override public int processBudgetWord(Object state) { throw unexpected(); }
            @Override public int hintWord(Object state) { throw unexpected(); }
            @Override public int padWord(Object state) { throw unexpected(); }
            @Override public Object residency(Object state) { throw unexpected(); }
            @Override public boolean stopped(Object state) { throw unexpected(); }
            @Override public Object make(int sectionWord, CdR11aPlcBuffer.State queue, CdAssetBytes codeTable,
                                         CdNemesisPlcDecoder.State decoder, SavedDecoder savedDecoder,
                                         int remainingTilesWord, int processBudgetWord, int hintWord, int padWord,
                                         Object resident, boolean stopped) { throw unexpected(); }
            @Override public RuntimeException failure(Reason reason, Object state, long source,
                                                      int destination, Throwable cause) { throw unexpected(); }
            @Override public void admitDestination(Object state, int destination) { throw unexpected(); }
            @Override public Object writeTile(Object state, int destination, CdAssetBytes bytes) { throw unexpected(); }
        };
    }
    private static AssertionError unexpected() {
        return new AssertionError("Pure source configuration must not invoke State, residency or decoder operations");
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
