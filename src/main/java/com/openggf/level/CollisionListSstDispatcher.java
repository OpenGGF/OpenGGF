package com.openggf.level;

interface CollisionListSstDispatcher {
    void freezePreviousReadView();

    void resetCurrentBuild();

    void captureCompletedBuild();
}
