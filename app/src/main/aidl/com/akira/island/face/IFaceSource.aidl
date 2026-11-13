package com.akira.island.face;
import com.akira.island.face.IFaceEvents;

interface IFaceSource {
    void start(IFaceEvents events, int userId, int systemUiUid) = 0;
    void stop() = 1;
    void destroy() = 16777114;
}
