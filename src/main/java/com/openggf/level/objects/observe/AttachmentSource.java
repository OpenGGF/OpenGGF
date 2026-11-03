package com.openggf.level.objects.observe;

import com.openggf.game.PlayableEntity;

import java.util.Optional;

/**
 * Whether an object holds a player this tick (grab, hang, carry, tube, airflow, latch), read
 * only. The owner reports the custody its routine established; proximity, animation or speed
 * never prove it.
 */
public interface AttachmentSource {
    Optional<Attachment> attachmentOf(PlayableEntity player);
}
