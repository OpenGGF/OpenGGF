package com.openggf.level.objects;

/** Stable portable ordering shared by population and plane-switcher rewind snapshots. */
final class ObjectRewindOrdering {
    private ObjectRewindOrdering() {}

    static String spawnKey(ObjectSpawn spawn) {
        if (spawn == null) {
            return "";
        }
        return spawn.layoutIndex()
                + ":" + spawn.objectId()
                + ":" + spawn.x()
                + ":" + spawn.y()
                + ":" + spawn.subtype()
                + ":" + spawn.renderFlags()
                + ":" + spawn.rawYWord();
    }

}
