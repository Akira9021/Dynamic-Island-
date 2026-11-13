package com.akira.island.face

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FaceLogReaderTest {
    private fun frame(headerSize:Int=28): ByteArray {
        val payload=byteArrayOf(2)+"tag\u0000synthetic\u0000".toByteArray()
        return ByteBuffer.allocate(headerSize+payload.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(payload.size.toShort()); putShort(headerSize.toShort())
            putInt(40);putInt(41);putInt(100);putInt(500_000_000);putInt(0)
            if(headerSize>=28) putInt(1000)
            position(headerSize); put(payload)
        }.array()
    }
    @Test fun readsAuthenticatedMetadataAndHandlesPartialReads() {
        val bytes=frame()
        val input=object:ByteArrayInputStream(bytes) { override fun read(b:ByteArray,off:Int,len:Int)=super.read(b,off,len.coerceAtMost(1)) }
        val r=FaceLogReader.read(input)!!
        assertEquals(1000,r.uid);assertEquals(40,r.pid);assertEquals(100500L,r.wallMs)
        assertEquals("tag",r.tag);assertEquals("synthetic",r.message);assertNull(FaceLogReader.read(input))
    }
    @Test(expected=IllegalArgumentException::class) fun oldHeaderWithoutUidFailsClosed() { FaceLogReader.read(ByteArrayInputStream(frame(24))) }
    @Test(expected=java.io.EOFException::class) fun truncatedFramesFailClosed() { FaceLogReader.read(ByteArrayInputStream(frame().dropLast(3).toByteArray())) }
    @Test fun noRawLogDataInDefaultObjectDescription() { assertFalse(FaceLogReader.read(ByteArrayInputStream(frame())).toString().contains("synthetic")) }
}
