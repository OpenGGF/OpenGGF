package starfall;

/** Player-selected lengths; depth and local gameplay scale stay consistent. */
public enum WorldSize {
    SMALL("SMALL",4096),
    MEDIUM("MEDIUM",8192),
    LARGE("LARGE",16384);

    public final String label;
    public final int width;
    WorldSize(String label,int width){this.label=label;this.width=width;}
    static boolean supports(int width,int height) {
        if(height!=World.H)return false;
        for(WorldSize size:values())if(size.width==width)return true;
        return false;
    }
    static WorldSize forWidth(int width) {
        for(WorldSize size:values())if(size.width==width)return size;
        return MEDIUM;
    }
}
