package com.ritualsoftheold.terra.offheap.io;

import com.ritualsoftheold.terra.offheap.chunk.ChunkBuffer;
import com.ritualsoftheold.terra.offheap.io.ChunkLoaderInterface;
import com.ritualsoftheold.terra.offheap.node.OffheapChunk;
import com.ritualsoftheold.terra.offheap.world.OffheapLoadMarker;
import com.ritualsoftheold.terra.offheap.world.WorldLoadListener;

public class ChunkLoader implements ChunkLoaderInterface {
    private WorldLoadListener loadListener;

    public ChunkLoader(WorldLoadListener loadListener) {
        this.loadListener = loadListener;
    }

    @Override
    public void loadChunk(OffheapChunk chunk) {
        loadListener.chunkLoaded(chunk);
    }

    @Override
    public synchronized OffheapChunk getChunk(float x, float z, OffheapLoadMarker loadMarker) {
        for (ChunkBuffer buffer:loadMarker.getBuffersInside()){
            for(int i = 0; i < buffer.getChunkCount(); i++){
                OffheapChunk chunk = buffer.getChunk(i);
                if(chunk.getX() == x && chunk.getZ() == z){
                    return chunk;
                }
            }
        }
        return null;
    }

    @Override
    public ChunkBuffer saveChunks(int index, ChunkBuffer buf) {
        return buf;
    }
}