package com.openggf.editor.properties;

import com.openggf.level.objects.ObjectSpawn;

/** Internal editor capability; deliberately separate from the pinned creator ObjectRegistry API. */
public interface ObjectPropertyProvider {
    ObjectPropertySchema objectProperties(int stockObjectId);

    default ObjectPropertySchema objectProperties(ObjectSpawn spawn) {
        return spawn.objectKey() == null ? objectProperties(spawn.objectId()) : ObjectPropertySchema.FALLBACK;
    }
}
