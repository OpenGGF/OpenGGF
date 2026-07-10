package com.openggf.io;

/**
 * Immutable hostile-input limits shared by mod readers and converters.
 * Production values are fixed; tests may only lower them through {@link #loweringBuilder()}.
 */
public final class ModInputLimits {
    public static final long DEFAULT_MAX_METADATA_BYTES = 1L << 20;
    public static final long DEFAULT_MAX_JAR_BYTES = 1L << 30;
    public static final int DEFAULT_MAX_JAR_ENTRIES = 16_384;
    public static final int DEFAULT_MAX_ENTRY_NAME_BYTES = 512;
    public static final long DEFAULT_MAX_AGGREGATE_ENTRY_NAME_BYTES = 4L << 20;
    public static final long DEFAULT_MAX_ASSET_BYTES = 64L << 20;
    public static final long DEFAULT_MAX_MOD_VALIDATION_BYTES = 512L << 20;
    public static final int DEFAULT_MAX_AUDIO_TRACK_SECONDS = 600;
    public static final long DEFAULT_MAX_AUDIO_TRACK_PCM_BYTES = 256L << 20;
    public static final int DEFAULT_MAX_SFX_SECONDS = 30;
    public static final long DEFAULT_MAX_SFX_PCM_BYTES = 32L << 20;
    public static final int DEFAULT_MAX_SFX_VOICES = 16;
    public static final long DEFAULT_MAX_AUDIO_CACHE_BYTES = 512L << 20;
    public static final int DEFAULT_MIN_AUDIO_SAMPLE_RATE = 8_000;
    public static final int DEFAULT_MAX_AUDIO_SAMPLE_RATE = 192_000;
    public static final int DEFAULT_MIN_AUDIO_CHANNELS = 1;
    public static final int DEFAULT_MAX_AUDIO_CHANNELS = 2;
    public static final double DEFAULT_MIN_AUDIO_GAIN = 0.0;
    public static final double DEFAULT_MAX_AUDIO_GAIN = 4.0;
    public static final int DEFAULT_MAX_MOD_JARS = 1_024;
    public static final long DEFAULT_MAX_REPOSITORY_VALIDATION_BYTES = 8L << 30;
    public static final int DEFAULT_MAX_IMAGE_WIDTH = 8_192;
    public static final int DEFAULT_MAX_IMAGE_HEIGHT = 8_192;
    public static final long DEFAULT_MAX_IMAGE_PIXELS = 33_554_432L;
    public static final int DEFAULT_MAX_SHEET_PATTERNS = 65_536;
    public static final int DEFAULT_MAX_SHEET_FRAMES = 4_096;
    public static final int DEFAULT_MAX_SHEET_PIECES = 65_536;
    public static final int DEFAULT_MAX_MAP_WIDTH = 4_096;
    public static final int DEFAULT_MAX_MAP_HEIGHT = 4_096;
    public static final long DEFAULT_MAX_MAP_CELLS = 4_194_304L;
    public static final int DEFAULT_MAX_LEVEL_OBJECTS = 65_535;
    public static final int DEFAULT_MAX_LEVEL_RINGS = 65_535;
    public static final int DEFAULT_MAX_XML_DEPTH = 64;
    public static final int DEFAULT_MAX_XML_ATTRIBUTES = 128;
    public static final int DEFAULT_MAX_YAML_DEPTH = 32;
    public static final int DEFAULT_MAX_COLLECTION_ENTRIES = 10_000;
    public static final int DEFAULT_MAX_YAML_ALIASES = 0;
    public static final int DEFAULT_MAX_DOCUMENT_CODE_POINTS = 1_048_576;
    public static final int DEFAULT_MAX_STRING_CHARS = 65_536;
    public static final int DEFAULT_MAX_NUMERIC_DIGITS = 20;

    private static final ModInputLimits PRODUCTION = new Builder().buildInternal();

    private final Builder values;

    private ModInputLimits(Builder values) {
        this.values = new Builder(values);
    }

    public static ModInputLimits production() { return PRODUCTION; }
    public static Builder loweringBuilder() { return new Builder(); }

    public long maxMetadataBytes() { return values.maxMetadataBytes; }
    public long maxJarBytes() { return values.maxJarBytes; }
    public int maxJarEntries() { return values.maxJarEntries; }
    public int maxEntryNameBytes() { return values.maxEntryNameBytes; }
    public long maxAggregateEntryNameBytes() { return values.maxAggregateEntryNameBytes; }
    public long maxAssetBytes() { return values.maxAssetBytes; }
    public long maxModValidationBytes() { return values.maxModValidationBytes; }
    public int maxAudioTrackSeconds() { return values.maxAudioTrackSeconds; }
    public long maxAudioTrackPcmBytes() { return values.maxAudioTrackPcmBytes; }
    public int maxSfxSeconds() { return values.maxSfxSeconds; }
    public long maxSfxPcmBytes() { return values.maxSfxPcmBytes; }
    public int maxSfxVoices() { return values.maxSfxVoices; }
    public long maxAudioCacheBytes() { return values.maxAudioCacheBytes; }
    public int minAudioSampleRate() { return DEFAULT_MIN_AUDIO_SAMPLE_RATE; }
    public int maxAudioSampleRate() { return values.maxAudioSampleRate; }
    public int minAudioChannels() { return DEFAULT_MIN_AUDIO_CHANNELS; }
    public int maxAudioChannels() { return values.maxAudioChannels; }
    public double minAudioGain() { return DEFAULT_MIN_AUDIO_GAIN; }
    public double maxAudioGain() { return values.maxAudioGain; }
    public int maxModJars() { return values.maxModJars; }
    public long maxRepositoryValidationBytes() { return values.maxRepositoryValidationBytes; }
    public int maxImageWidth() { return values.maxImageWidth; }
    public int maxImageHeight() { return values.maxImageHeight; }
    public long maxImagePixels() { return values.maxImagePixels; }
    public int maxSheetPatterns() { return values.maxSheetPatterns; }
    public int maxSheetFrames() { return values.maxSheetFrames; }
    public int maxSheetPieces() { return values.maxSheetPieces; }
    public int maxMapWidth() { return values.maxMapWidth; }
    public int maxMapHeight() { return values.maxMapHeight; }
    public long maxMapCells() { return values.maxMapCells; }
    public int maxLevelObjects() { return values.maxLevelObjects; }
    public int maxLevelRings() { return values.maxLevelRings; }
    public int maxXmlDepth() { return values.maxXmlDepth; }
    public int maxXmlAttributes() { return values.maxXmlAttributes; }
    public int maxYamlDepth() { return values.maxYamlDepth; }
    public int maxCollectionEntries() { return values.maxCollectionEntries; }
    public int maxYamlAliases() { return DEFAULT_MAX_YAML_ALIASES; }
    public int maxDocumentCodePoints() { return values.maxDocumentCodePoints; }
    public int maxStringChars() { return values.maxStringChars; }
    public int maxNumericDigits() { return values.maxNumericDigits; }

    public static final class Builder {
        private long maxMetadataBytes = DEFAULT_MAX_METADATA_BYTES;
        private long maxJarBytes = DEFAULT_MAX_JAR_BYTES;
        private int maxJarEntries = DEFAULT_MAX_JAR_ENTRIES;
        private int maxEntryNameBytes = DEFAULT_MAX_ENTRY_NAME_BYTES;
        private long maxAggregateEntryNameBytes = DEFAULT_MAX_AGGREGATE_ENTRY_NAME_BYTES;
        private long maxAssetBytes = DEFAULT_MAX_ASSET_BYTES;
        private long maxModValidationBytes = DEFAULT_MAX_MOD_VALIDATION_BYTES;
        private int maxAudioTrackSeconds = DEFAULT_MAX_AUDIO_TRACK_SECONDS;
        private long maxAudioTrackPcmBytes = DEFAULT_MAX_AUDIO_TRACK_PCM_BYTES;
        private int maxSfxSeconds = DEFAULT_MAX_SFX_SECONDS;
        private long maxSfxPcmBytes = DEFAULT_MAX_SFX_PCM_BYTES;
        private int maxSfxVoices = DEFAULT_MAX_SFX_VOICES;
        private long maxAudioCacheBytes = DEFAULT_MAX_AUDIO_CACHE_BYTES;
        private int maxAudioSampleRate = DEFAULT_MAX_AUDIO_SAMPLE_RATE;
        private int maxAudioChannels = DEFAULT_MAX_AUDIO_CHANNELS;
        private double maxAudioGain = DEFAULT_MAX_AUDIO_GAIN;
        private int maxModJars = DEFAULT_MAX_MOD_JARS;
        private long maxRepositoryValidationBytes = DEFAULT_MAX_REPOSITORY_VALIDATION_BYTES;
        private int maxImageWidth = DEFAULT_MAX_IMAGE_WIDTH;
        private int maxImageHeight = DEFAULT_MAX_IMAGE_HEIGHT;
        private long maxImagePixels = DEFAULT_MAX_IMAGE_PIXELS;
        private int maxSheetPatterns = DEFAULT_MAX_SHEET_PATTERNS;
        private int maxSheetFrames = DEFAULT_MAX_SHEET_FRAMES;
        private int maxSheetPieces = DEFAULT_MAX_SHEET_PIECES;
        private int maxMapWidth = DEFAULT_MAX_MAP_WIDTH;
        private int maxMapHeight = DEFAULT_MAX_MAP_HEIGHT;
        private long maxMapCells = DEFAULT_MAX_MAP_CELLS;
        private int maxLevelObjects = DEFAULT_MAX_LEVEL_OBJECTS;
        private int maxLevelRings = DEFAULT_MAX_LEVEL_RINGS;
        private int maxXmlDepth = DEFAULT_MAX_XML_DEPTH;
        private int maxXmlAttributes = DEFAULT_MAX_XML_ATTRIBUTES;
        private int maxYamlDepth = DEFAULT_MAX_YAML_DEPTH;
        private int maxCollectionEntries = DEFAULT_MAX_COLLECTION_ENTRIES;
        private int maxDocumentCodePoints = DEFAULT_MAX_DOCUMENT_CODE_POINTS;
        private int maxStringChars = DEFAULT_MAX_STRING_CHARS;
        private int maxNumericDigits = DEFAULT_MAX_NUMERIC_DIGITS;

        private Builder() {}
        private Builder(Builder source) {
            this.maxMetadataBytes=source.maxMetadataBytes; this.maxJarBytes=source.maxJarBytes;
            this.maxJarEntries=source.maxJarEntries; this.maxEntryNameBytes=source.maxEntryNameBytes;
            this.maxAggregateEntryNameBytes=source.maxAggregateEntryNameBytes; this.maxAssetBytes=source.maxAssetBytes;
            this.maxModValidationBytes=source.maxModValidationBytes; this.maxAudioTrackSeconds=source.maxAudioTrackSeconds;
            this.maxAudioTrackPcmBytes=source.maxAudioTrackPcmBytes; this.maxSfxSeconds=source.maxSfxSeconds;
            this.maxSfxPcmBytes=source.maxSfxPcmBytes; this.maxSfxVoices=source.maxSfxVoices;
            this.maxAudioCacheBytes=source.maxAudioCacheBytes; this.maxAudioSampleRate=source.maxAudioSampleRate;
            this.maxAudioChannels=source.maxAudioChannels; this.maxAudioGain=source.maxAudioGain; this.maxModJars=source.maxModJars;
            this.maxRepositoryValidationBytes=source.maxRepositoryValidationBytes; this.maxImageWidth=source.maxImageWidth;
            this.maxImageHeight=source.maxImageHeight; this.maxImagePixels=source.maxImagePixels;
            this.maxSheetPatterns=source.maxSheetPatterns; this.maxSheetFrames=source.maxSheetFrames;
            this.maxSheetPieces=source.maxSheetPieces; this.maxMapWidth=source.maxMapWidth; this.maxMapHeight=source.maxMapHeight;
            this.maxMapCells=source.maxMapCells; this.maxLevelObjects=source.maxLevelObjects; this.maxLevelRings=source.maxLevelRings;
            this.maxXmlDepth=source.maxXmlDepth; this.maxXmlAttributes=source.maxXmlAttributes; this.maxYamlDepth=source.maxYamlDepth;
            this.maxCollectionEntries=source.maxCollectionEntries; this.maxDocumentCodePoints=source.maxDocumentCodePoints;
            this.maxStringChars=source.maxStringChars; this.maxNumericDigits=source.maxNumericDigits;
        }

        public Builder maxMetadataBytes(long v){maxMetadataBytes=bounded(v,DEFAULT_MAX_METADATA_BYTES,"maxMetadataBytes");return this;}
        public Builder maxJarBytes(long v){maxJarBytes=bounded(v,DEFAULT_MAX_JAR_BYTES,"maxJarBytes");return this;}
        public Builder maxJarEntries(int v){maxJarEntries=bounded(v,DEFAULT_MAX_JAR_ENTRIES,"maxJarEntries");return this;}
        public Builder maxEntryNameBytes(int v){maxEntryNameBytes=bounded(v,DEFAULT_MAX_ENTRY_NAME_BYTES,"maxEntryNameBytes");return this;}
        public Builder maxAggregateEntryNameBytes(long v){maxAggregateEntryNameBytes=bounded(v,DEFAULT_MAX_AGGREGATE_ENTRY_NAME_BYTES,"maxAggregateEntryNameBytes");return this;}
        public Builder maxAssetBytes(long v){maxAssetBytes=bounded(v,DEFAULT_MAX_ASSET_BYTES,"maxAssetBytes");return this;}
        public Builder maxModValidationBytes(long v){maxModValidationBytes=bounded(v,DEFAULT_MAX_MOD_VALIDATION_BYTES,"maxModValidationBytes");return this;}
        public Builder maxAudioTrackSeconds(int v){maxAudioTrackSeconds=bounded(v,DEFAULT_MAX_AUDIO_TRACK_SECONDS,"maxAudioTrackSeconds");return this;}
        public Builder maxAudioTrackPcmBytes(long v){maxAudioTrackPcmBytes=bounded(v,DEFAULT_MAX_AUDIO_TRACK_PCM_BYTES,"maxAudioTrackPcmBytes");return this;}
        public Builder maxSfxSeconds(int v){maxSfxSeconds=bounded(v,DEFAULT_MAX_SFX_SECONDS,"maxSfxSeconds");return this;}
        public Builder maxSfxPcmBytes(long v){maxSfxPcmBytes=bounded(v,DEFAULT_MAX_SFX_PCM_BYTES,"maxSfxPcmBytes");return this;}
        public Builder maxSfxVoices(int v){maxSfxVoices=bounded(v,DEFAULT_MAX_SFX_VOICES,"maxSfxVoices");return this;}
        public Builder maxAudioCacheBytes(long v){maxAudioCacheBytes=bounded(v,DEFAULT_MAX_AUDIO_CACHE_BYTES,"maxAudioCacheBytes");return this;}
        public Builder maxAudioSampleRate(int v){maxAudioSampleRate=bounded(v,DEFAULT_MAX_AUDIO_SAMPLE_RATE,"maxAudioSampleRate");return this;}
        public Builder maxAudioChannels(int v){maxAudioChannels=bounded(v,DEFAULT_MAX_AUDIO_CHANNELS,"maxAudioChannels");return this;}
        public Builder maxAudioGain(double v){if(!Double.isFinite(v)||v<=0||v>DEFAULT_MAX_AUDIO_GAIN)throw new IllegalArgumentException("maxAudioGain");maxAudioGain=v;return this;}
        public Builder maxModJars(int v){maxModJars=bounded(v,DEFAULT_MAX_MOD_JARS,"maxModJars");return this;}
        public Builder maxRepositoryValidationBytes(long v){maxRepositoryValidationBytes=bounded(v,DEFAULT_MAX_REPOSITORY_VALIDATION_BYTES,"maxRepositoryValidationBytes");return this;}
        public Builder maxImageWidth(int v){maxImageWidth=bounded(v,DEFAULT_MAX_IMAGE_WIDTH,"maxImageWidth");return this;}
        public Builder maxImageHeight(int v){maxImageHeight=bounded(v,DEFAULT_MAX_IMAGE_HEIGHT,"maxImageHeight");return this;}
        public Builder maxImagePixels(long v){maxImagePixels=bounded(v,DEFAULT_MAX_IMAGE_PIXELS,"maxImagePixels");return this;}
        public Builder maxSheetPatterns(int v){maxSheetPatterns=bounded(v,DEFAULT_MAX_SHEET_PATTERNS,"maxSheetPatterns");return this;}
        public Builder maxSheetFrames(int v){maxSheetFrames=bounded(v,DEFAULT_MAX_SHEET_FRAMES,"maxSheetFrames");return this;}
        public Builder maxSheetPieces(int v){maxSheetPieces=bounded(v,DEFAULT_MAX_SHEET_PIECES,"maxSheetPieces");return this;}
        public Builder maxMapWidth(int v){maxMapWidth=bounded(v,DEFAULT_MAX_MAP_WIDTH,"maxMapWidth");return this;}
        public Builder maxMapHeight(int v){maxMapHeight=bounded(v,DEFAULT_MAX_MAP_HEIGHT,"maxMapHeight");return this;}
        public Builder maxMapCells(long v){maxMapCells=bounded(v,DEFAULT_MAX_MAP_CELLS,"maxMapCells");return this;}
        public Builder maxLevelObjects(int v){maxLevelObjects=bounded(v,DEFAULT_MAX_LEVEL_OBJECTS,"maxLevelObjects");return this;}
        public Builder maxLevelRings(int v){maxLevelRings=bounded(v,DEFAULT_MAX_LEVEL_RINGS,"maxLevelRings");return this;}
        public Builder maxXmlDepth(int v){maxXmlDepth=bounded(v,DEFAULT_MAX_XML_DEPTH,"maxXmlDepth");return this;}
        public Builder maxXmlAttributes(int v){maxXmlAttributes=bounded(v,DEFAULT_MAX_XML_ATTRIBUTES,"maxXmlAttributes");return this;}
        public Builder maxYamlDepth(int v){maxYamlDepth=bounded(v,DEFAULT_MAX_YAML_DEPTH,"maxYamlDepth");return this;}
        public Builder maxCollectionEntries(int v){maxCollectionEntries=bounded(v,DEFAULT_MAX_COLLECTION_ENTRIES,"maxCollectionEntries");return this;}
        public Builder maxDocumentCodePoints(int v){maxDocumentCodePoints=bounded(v,DEFAULT_MAX_DOCUMENT_CODE_POINTS,"maxDocumentCodePoints");return this;}
        public Builder maxStringChars(int v){maxStringChars=bounded(v,DEFAULT_MAX_STRING_CHARS,"maxStringChars");return this;}
        public Builder maxNumericDigits(int v){maxNumericDigits=bounded(v,DEFAULT_MAX_NUMERIC_DIGITS,"maxNumericDigits");return this;}

        public ModInputLimits build() { return buildInternal(); }
        private ModInputLimits buildInternal() { return new ModInputLimits(this); }
        private static int bounded(int v,int max,String name){if(v<=0||v>max)throw new IllegalArgumentException(name+" must be in 1.."+max);return v;}
        private static long bounded(long v,long max,String name){if(v<=0||v>max)throw new IllegalArgumentException(name+" must be in 1.."+max);return v;}
    }
}
