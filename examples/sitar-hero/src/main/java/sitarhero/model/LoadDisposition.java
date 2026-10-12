package sitarhero.model;

/** Consumer-local load authority; rejected input never becomes replacement permission. */
public enum LoadDisposition {
    ABSENT, VALID, INCOMPATIBLE, CORRUPT;
    public boolean saveEligible() { return this == ABSENT || this == VALID; }
    public static LoadDisposition rejected(String text, String headerKey, String supportedHeader) {
        if (text != null && text.lines().findFirst().orElse("").startsWith(headerKey + "=")
                && !text.lines().findFirst().orElse("").equals(supportedHeader)) return INCOMPATIBLE;
        return CORRUPT;
    }
}
