package com.monkopedia.konstructor

import java.nio.channels.FileChannel

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Vector3(
    val x: Float,
    val y: Float,
    val z: Float
)

class StlWriter(fileName: String, title: String, val indices: List<Vector3>) {

    init {
        val raf = RandomAccessFile(fileName, "rw")
        raf.setLength(0L)
        val ch: FileChannel = raf.channel

        val bb: ByteBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)

        val titleByte = ByteArray(80)
        System.arraycopy(title.toByteArray(), 0, titleByte, 0, title.length)
        bb.put(titleByte)

        bb.putInt(nofTriangles) // Number of triangles


        bb.flip() // prep for writing

        ch.write(bb)

        for (i in 0 until nofIndices)  // triangles
        {
            bb.clear()
            val normal: Vector3 =
                getNormal(indices.get(i).get(0), indices.get(i).get(1), indices.get(i).get(2))
            bb.putFloat(normal.get(k).x)
            bb.putFloat(normal.get(k).y)
            bb.putFloat(normal.get(k).z)
            for (j in 0..2)  // triangle indices
            {
                bb.putFloat(vertices.get(indices.get(i).get(j)).x)
                bb.putFloat(vertices.get(indices.get(i).get(j)).y)
                bb.putFloat(vertices.get(indices.get(i).get(j)).z)
            }
            bb.putShort(0.toShort()) // number of attributes
            bb.flip()
            ch.write(bb)
        }

        ch.close();
    }

    fun getNormal( ind1: Int, ind2: Int, ind3 : Int): Vector3
    {
        Vector3 p1 = vertices[ ind1 ];
        Vector3 p2 = vertices[ ind2 ];
        Vector3 p3 = vertices[ ind3 ];
        return p1.cpy().sub( p2 ).crs( p2.x - p3.x, p2.y - p3.y, p2.z - p3.z ) ).nor();
    }

}