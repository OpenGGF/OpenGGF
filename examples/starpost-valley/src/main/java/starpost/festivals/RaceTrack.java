package starpost.festivals;

/** A Green Hill circuit encoded as native terrain; no scene collision or scripted player loop. */
public final class RaceTrack {
    private RaceTrack() {}
    public static int[] blocks() {
        return new int[] {60,45,12,3,45,53,38,1,16,17,37,21,49,34,2,45,53,60};
    }
    public static int length() { return blocks().length*256; }
}
