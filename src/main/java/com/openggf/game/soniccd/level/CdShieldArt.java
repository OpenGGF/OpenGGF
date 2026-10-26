package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.media.CdMmdImage;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.soniccd.level.CdR11aAsset.*;

/**
 * LoadShieldArt's five raw copies, regardless of source filename extensions.
 * Powerup/Main.asm:157-186 copies $100 longwords, then LVLDMA uploads $480 bytes.
 * The extra $80 bytes are resident RAM of unknown content, not a longer file view.
 */
public final class CdShieldArt {
    public enum ContentStatus { UNKNOWN }
    public record ResidentTail(int nativeAddress, int length, ContentStatus status) { }
    public record SourceSegment(CdR11aAsset source, int sourceRelativeOffset, int length) { }
    public record RawCopy(int slot, CdR11aAsset source, CdR11aProfile.FileRange consumedRange,
                           CdAssetBytes bytes, List<SourceSegment> segments) {
        public RawCopy { segments = List.copyOf(segments); }
    }

    private final List<RawCopy> copies;

    private CdShieldArt(List<RawCopy> copies) { this.copies = List.copyOf(copies); }

    /** Bounded component decoding. Full program/media identity is checked by CdR11aAssetLoader. */
    public static CdShieldArt decode(CdR11aProfile profile, CdMmdImage image) {
        CdAssetBytes index = profile.range(SHIELD_ART_INDEX).read(image);
        CdR11aAsset[] sources = {ART_SHIELD, ART_INV_STARS, ART_TIME_STARS, ART_GAME_OVER, ART_TIME_OVER};
        List<RawCopy> copies = new ArrayList<>();
        for (int slot = 0; slot < sources.length; slot++) {
            var source = sources[slot];
            var blob = profile.range(source);
            if (index.unsignedLong(slot * 4) != blob.nativeAddress()) {
                throw new IllegalArgumentException("ShieldArtIndex has an unsupported pointer, tag or slot order");
            }
            var consumed = new CdR11aProfile.FileRange(profile.file(), profile.domain(), blob.fileOffset(), 1024);
            List<SourceSegment> segments = switch (source) {
                case ART_TIME_STARS -> List.of(new SourceSegment(source, 0, 992), new SourceSegment(ART_DIAGONAL_SPRING, 0, 32));
                case ART_TIME_OVER -> List.of(new SourceSegment(source, 0, 960), new SourceSegment(ART_GAME_OVER, 0, 64));
                default -> List.of(new SourceSegment(source, 0, 1024));
            };
            int cursor = consumed.fileOffset();
            for (SourceSegment segment : segments) {
                var range = profile.range(segment.source());
                if (cursor != range.fileOffset() + segment.sourceRelativeOffset()
                        || segment.length() > range.length() - segment.sourceRelativeOffset()) {
                    throw new IllegalArgumentException("Shield copy continuation lacks a bounded adjacent source");
                }
                cursor += segment.length();
            }
            if (cursor != consumed.endExclusive()) { throw new IllegalArgumentException("Shield copy coverage gap"); }
            copies.add(new RawCopy(slot, source, consumed, consumed.read(image), segments));
        }
        return new CdShieldArt(copies);
    }

    public List<RawCopy> copies() { return copies; }
    public RawCopy copy(int nativeSlot) { return copies.get(nativeSlot); }
    public int copyRamAddress() { return 0xFF1980; }
    public int uploadBytes() { return 1152; }
    public int vramDestination() { return 0xA880; }
    public ResidentTail residentTail() { return new ResidentTail(0xFF1D80, 128, ContentStatus.UNKNOWN); }

    /** A data loader cannot manufacture the bytes owned by a future RAM/history consumer. */
    public CdAssetBytes completeUploadBytes() {
        throw new UnsupportedOperationException("Shield upload needs 128 bytes of resident RAM from its runtime owner");
    }
}
