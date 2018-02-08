package com.ritualsoftheold.terra.mesher;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

/**
 * Helps with building meshes for voxel data.
 *
 */
public class MeshContainer {
    
    private ByteBuf vertices;
    
    private ByteBuf indices;
    
    private ByteBuf texCoords;
    
    public MeshContainer(int startSize, ByteBufAllocator allocator) {
        this.vertices = allocator.directBuffer(startSize * 4);
        this.indices = allocator.directBuffer(startSize * 6);
        this.texCoords = allocator.directBuffer(startSize * 4);
    }
    
    public void vertex(int x, int y, int z) {
        float packed = Float.intBitsToFloat(x << 22 | y << y | z);
        vertices.writeFloat(packed);
    }
    
    public void triangle(int vertIndex, int i, int j, int k) {
        indices.writeInt(vertIndex + i);
        indices.writeInt(vertIndex + j);
        indices.writeInt(vertIndex +  k);
    }
    
    public void texture(int page, int tile, int x, int y) {
        float packed = Float.intBitsToFloat(tile << 24 | x << 16 | y << 8 | page);
        texCoords.writeFloat(packed);
    }
    
    public ByteBuf getVertices() {
        return vertices;
    }
    
    public ByteBuf getIndices() {
        return indices;
    }
    
    public ByteBuf getTextureCoordinates() {
        return texCoords;
    }
}