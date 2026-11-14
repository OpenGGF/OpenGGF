package com.openggf.game.soniccd;

/**
 * Software build branches, not console environments or certified disc pressings.
 * Main.asm header (Main.asm:11-69) supplies these source facts.
 * Header agreement alone cannot certify user-supplied media.
 */
public enum CdReleaseVariant {
    JAPAN(0, "J", "SEGAIPSAMP", "08061993", "GM G-6021  -00"),
    USA(1, "U", "SEGASONICCD", "10061993", "GM MK-4407 -00"),
    EUROPE(2, "E", "SEGASONICCD", "08271993", "GM MK-4407-00");

    private final int sourceRegionValue;
    private final String headerMarket;
    private final String discHeaderVolumeId;
    private final String sourceBuildDate;
    private final String productId;

    CdReleaseVariant(int sourceRegionValue, String headerMarket, String discHeaderVolumeId,
                     String sourceBuildDate, String productId) {
        this.sourceRegionValue = sourceRegionValue;
        this.headerMarket = headerMarket;
        this.discHeaderVolumeId = discHeaderVolumeId;
        this.sourceBuildDate = sourceBuildDate;
        this.productId = productId;
    }

    public int sourceRegionValue() { return sourceRegionValue; }
    public String headerMarket() { return headerMarket; }
    public String discHeaderVolumeId() { return discHeaderVolumeId; }
    public String sourceBuildDate() { return sourceBuildDate; }
    public String productId() { return productId; }
}
