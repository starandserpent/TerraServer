package com.ritualsoftheold.terra.server.world;

import com.ritualsoftheold.terra.core.DataConstants;
import com.ritualsoftheold.terra.core.WorldLoadListener;
import com.ritualsoftheold.terra.core.chunk.ChunkLArray;
import com.ritualsoftheold.terra.core.octrees.OctreeBase;
import com.ritualsoftheold.terra.core.octrees.OctreeLeaf;
import com.ritualsoftheold.terra.server.LoadMarker;
import com.ritualsoftheold.terra.server.chunks.ChunkGenerator;
import com.ritualsoftheold.terra.server.morton.Morton3D;
import com.ritualsoftheold.terra.server.chunks.OffheapOctree;

import java.util.ArrayList;

/**
 * Handles loading of offheap worlds. Usually this class is used by load
 * markers; direct usage is not recommended for application developers.
 */
class ChunkSVOGenerator {

    private Morton3D morton3D = new Morton3D();
    private OffheapOctree offheapOctree;
    private ChunkGenerator generator;

    ChunkSVOGenerator(ChunkGenerator generator, int height, OffheapOctree offheapOctree) {
        this.generator = generator;
        this.offheapOctree = offheapOctree;
        System.out.println("Called------------------------------- " + height);
    }

    void seekSector(LoadMarker marker, ArrayList<OctreeBase> nodes) {

        float x = marker.getPosX();
        float y = marker.getPosY();
        float z = marker.getPosZ();

        float range = marker.getHardRadius();

        int chunkWorldSize = DataConstants.CHUNK_SCALE;

        float genOriginX = x - (range * chunkWorldSize);
        float genOriginY = y - (range * chunkWorldSize);
        float genOriginZ = z - (range * chunkWorldSize);
        System.out.println("Player loc: " + x + " " + y + " " + z);
        System.out.println("Origin: " + genOriginX + "," + genOriginY + "," + genOriginZ);
        int size = (int) (range) * 2;
        int maxSize = size * size * size;

        OctreeBase[] octreeLeafs = new OctreeBase[maxSize];

        for (int i = 0; i < maxSize; i++) {
            int xOffset = i % size;
            int yOffset = (i / size) % size;
            int zOffset = i / (size * size);

            float xWorld = (xOffset * chunkWorldSize) + genOriginX;
            float yWorld = (yOffset * chunkWorldSize) + genOriginY;
            float zWorld = (zOffset * chunkWorldSize) + genOriginZ;

            long lolong = morton3D.encode(xOffset, yOffset, zOffset);

            loadArea(xWorld, yWorld, zWorld, marker);

            OctreeLeaf leafNode = new OctreeLeaf();
            leafNode.worldX = xWorld;
            leafNode.worldY = yWorld;
            leafNode.worldZ = zWorld;
            leafNode.locCode = lolong;
            octreeLeafs[(int) lolong] = leafNode;
        }

        offheapOctree.setOctreeOrigin((int) x, (int) y, (int) z, maxSize);
        offheapOctree.createOctree(octreeLeafs);
        if (nodes != null) {
            nodes.addAll(offheapOctree.getOctreeNodes());
        }
    }

    void updateSector(float x, float z, float range, WorldLoadListener listener, LoadMarker trigger) {

    }

  /*  public void unloadArea(float x, float y, float z, WorldLoadListener listener, OffheapLoadMarker trigger){
        ChunkLoader chunkLoader = new ChunkLoader(listener);
        ChunkLArray chunk = chunkLoader.getChunk(x, y, z, trigger);
        if(chunk != null) {
            //genManager.remove(chunk);
            listener.chunkUnloaded(chunk);
        }
    }*/

    private void loadArea(float x, float y, float z, LoadMarker marker) {
        if (x > 0 && z > 0) {
            ChunkLArray chunk = generator.getChunk(x, y, z);
            marker.sendChunk(chunk);
        }
    }
}