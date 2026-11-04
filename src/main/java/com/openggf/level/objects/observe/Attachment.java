package com.openggf.level.objects.observe;

import java.util.Objects;

/** One reported attachment: its kind, the owner's anchor and whether the owner is releasing. */
public record Attachment(AttachmentKind kind, int anchorX, int anchorY, boolean releasing) {
    public Attachment {
        Objects.requireNonNull(kind, "kind");
    }
}
