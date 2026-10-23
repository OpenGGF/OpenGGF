package com.openggf.level.metadata;

import com.openggf.level.objects.ObjectSpawn;

/** Detached placement presentation. Implementations select ROM mappings, never create objects. */
public interface ObjectPreviewProvider {
    ObjectPreview objectPreview(ObjectSpawn spawn);
}
