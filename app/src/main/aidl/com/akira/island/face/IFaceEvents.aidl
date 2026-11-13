package com.akira.island.face;

// Semantic codes only. Raw journal data and biometric identifiers never cross this interface.
oneway interface IFaceEvents {
    void onSignal(int kind, long elapsedMillis);
    void onSourceState(int state);
}
