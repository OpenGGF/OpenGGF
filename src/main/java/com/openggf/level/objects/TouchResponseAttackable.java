package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;

@com.openggf.game.ModApi
public interface TouchResponseAttackable {
    void onPlayerAttack(PlayableEntity player, TouchResponseResult result);
}
