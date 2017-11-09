package com.ritualsoftheold.terra.offheap.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.ritualsoftheold.terra.material.MaterialRegistry;
import com.ritualsoftheold.terra.node.Chunk;
import com.ritualsoftheold.terra.node.Node;
import com.ritualsoftheold.terra.node.Octree;
import com.ritualsoftheold.terra.offheap.DataConstants;
import com.ritualsoftheold.terra.offheap.chunk.ChunkBuffer;
import com.ritualsoftheold.terra.offheap.chunk.ChunkStorage;
import com.ritualsoftheold.terra.offheap.chunk.ChunkType;
import com.ritualsoftheold.terra.offheap.data.DataHeuristics;
import com.ritualsoftheold.terra.offheap.io.ChunkLoader;
import com.ritualsoftheold.terra.offheap.io.OctreeLoader;
import com.ritualsoftheold.terra.offheap.memory.MemoryManager;
import com.ritualsoftheold.terra.offheap.memory.MemoryPanicHandler;
import com.ritualsoftheold.terra.offheap.node.OffheapOctree;
import com.ritualsoftheold.terra.offheap.octree.OctreeStorage;
import com.ritualsoftheold.terra.world.LoadMarker;
import com.ritualsoftheold.terra.world.TerraWorld;
import com.ritualsoftheold.terra.world.gen.WorldGenerator;

import net.openhft.chronicle.core.Memory;
import net.openhft.chronicle.core.OS;

/**
 * Represents world that is mainly backed by offheap memory.
 */
public class OffheapWorld implements TerraWorld {
    
    private static final Memory mem = OS.memory();
    
    // Loaders/savers
    private ChunkLoader chunkLoader;
    private OctreeLoader octreeLoader;
    
    // Data storage
    private Executor storageExecutor;
    private ChunkStorage chunkStorage;
    private OctreeStorage octreeStorage;
    
    // Some cached stuff
    private OffheapOctree masterOctree;
    private float masterScale;
    
    // World generation
    private WorldGenerator generator;
    private WorldGenManager genManager;
    private Executor generatorExecutor;
    
    private MaterialRegistry registry;
    
    // Load markers
    private List<LoadMarker> loadMarkers;
    private WorldLoadListener loadListener;
    
    // Memory management
    private MemoryManager memManager;
    
    private WorldSizeManager sizeManager;
    
    // Coordinates of world center
    private float centerX;
    private float centerY;
    private float centerZ;
    
    // New world loader, no more huge methods in this class!
    private WorldLoader worldLoader;
    
    public static class Builder {
        
        private OffheapWorld world;
        
        private int octreeGroupSize;
        
        private long memPreferred;
        private long memMax;
        private MemoryPanicHandler memPanicHandler;
        
        private ChunkBuffer.Builder chunkBufferBuilder;
        private int chunkMaxBuffers;
        
        public Builder() {
            world = new OffheapWorld();
        }
        
        public Builder chunkLoader(ChunkLoader loader) {
            world.chunkLoader = loader;
            return this;
        }
        
        public Builder octreeLoader(OctreeLoader loader) {
            world.octreeLoader = loader;
            return this;
        }
        
        public Builder storageExecutor(Executor executor) {
            world.storageExecutor = executor;
            return this;
        }
        
        public Builder chunkStorage(ChunkBuffer.Builder bufferBuilder, int maxBuffers) {
            this.chunkBufferBuilder = bufferBuilder;
            this.chunkMaxBuffers = maxBuffers;
            
            return this;
        }
        
        public Builder octreeStorage(int groupSize) {
            this.octreeGroupSize = groupSize;
            return this;
        }
        
        public Builder generator(WorldGenerator generator) {
            world.generator = generator;
            return this;
        }
        
        public Builder generatorExecutor(Executor executor) {
            world.generatorExecutor = executor;
            return this;
        }
        
        public Builder materialRegistry(MaterialRegistry registry) {
            world.registry = registry;
            return this;
        }
        
        public Builder memorySettings(long preferred, long max, MemoryPanicHandler panicHandler) {
            this.memPreferred = preferred;
            this.memMax = max;
            this.memPanicHandler = panicHandler;
            
            return this;
        }
        
        public OffheapWorld build() {
            // Initialize some internal structures AFTER all user-controller initialization
            world.loadMarkers = new ArrayList<>();
            world.sizeManager = new WorldSizeManager(world);
            
            // Initialize memory manager
            world.memManager = new MemoryManager(world, memPreferred, memMax, memPanicHandler);
            
            // Initialize stuff that needs memory manager
            world.octreeStorage = new OctreeStorage(octreeGroupSize, world.octreeLoader, world.storageExecutor, world.memManager);
            chunkBufferBuilder.memListener(world.memManager);
            world.chunkStorage = new ChunkStorage(chunkBufferBuilder, chunkMaxBuffers, world.chunkLoader, world.storageExecutor);
            
            // Initialize world generation
            world.genManager = new WorldGenManager(world.generator, new DataHeuristics(), world.chunkStorage);
            
            // ... and world loading
            world.worldLoader = new WorldLoader(world.octreeStorage, world.chunkStorage, world.genManager);
            
            // Update master octree (and finish loader stuff)
            world.updateMasterOctree();
            
            return world;
        }
    }
    
    // Only used by the builder
    private OffheapWorld() {
        
    }

    @Override
    public Octree getMasterOctree() {
        return masterOctree;
    }

    @Override
    public MaterialRegistry getMaterialRegistry() {
        return registry;
    }
    
    public Node getNode(float x, float y, float z) {
        long nodeData = getNodeId(x, y, z);
        boolean isChunk = nodeData >>> 32 == 1;
        int nodeId = (int) (nodeData & 0xffffff);
        
        if (isChunk) {
            return chunkStorage.getChunk(nodeId, registry);
        } else {
            return octreeStorage.getOctree(nodeId, registry);
        }
    }

    @Override
    public Chunk getChunk(float x, float y, float z) {
        return null; // TODO
    }
    
    /**
     * Attempts to get an id for smallest node at given coordinates.
     * @param x X coordinate.
     * @param y Y coordinate.
     * @param z Z coordinate.
     * @return 32 least significant bits represent the actual id. 33th
     * tells if the id refers to chunk (1) or octree (2).
     */
    private long getNodeId(float x, float y, float z) {
        return 0; // TODO redo this
    }
    
    
    public OctreeStorage getOctreeStorage() {
        return octreeStorage;
    }
    
    public ChunkStorage getChunkStorage() {
        return chunkStorage;
    }

    @Override
    public void addLoadMarker(LoadMarker marker) {
        loadMarkers.add(marker);
        loadMarkers.sort(Comparator.reverseOrder()); // Sort most important first
    }
    

    @Override
    public void removeLoadMarker(LoadMarker marker) {
        Iterator<LoadMarker> it = loadMarkers.iterator();
        while (it.hasNext()) {
            LoadMarker m = it.next();
            if (m == marker) {
                it.remove();
                return;
            }
        }
    }

    @Override
    public List<CompletableFuture<Void>> updateLoadMarkers() {
        List<CompletableFuture<Void>> pendingMarkers = new ArrayList<>(loadMarkers.size());
        // Delegate updating to async code, this might be costly
        for (LoadMarker marker : loadMarkers) {
            if (marker.hasMoved()) { // Update only marker that has been moved
                // When player moves a little, DO NOT, I repeat, DO NOT just blindly move load marker.
                // Move it when player has moved a few meters or so!
                pendingMarkers.add(CompletableFuture.runAsync(() -> updateLoadMarker(marker, loadListener, false), storageExecutor));
            }
        }
        
        return pendingMarkers;
    }
    
    public List<CompletableFuture<Void>> updateLoadMarkers(WorldLoadListener listener, boolean soft, boolean ignoreMoved) {
        List<CompletableFuture<Void>> pendingMarkers = new ArrayList<>(loadMarkers.size());
        // Delegate updating to async code, this might be costly
        for (LoadMarker marker : loadMarkers) {
            if (ignoreMoved || marker.hasMoved()) { // Update only marker that has been moved
                // When player moves a little, DO NOT, I repeat, DO NOT just blindly move load marker.
                // Move it when player has moved a few meters or so!
                pendingMarkers.add(CompletableFuture.runAsync(() -> updateLoadMarker(marker, listener, soft), storageExecutor));
            }
        }
        
        return pendingMarkers;
    }
    
    /**
     * Updates given load marker no matter what. Only used internally.
     * @param marker Load marker to update.
     * @param listener Load listener.
     * @param soft If soft radius should be used.
     */
    private void updateLoadMarker(LoadMarker marker, WorldLoadListener listener, boolean soft) {
        System.out.println("Update load marker...");
        worldLoader.seekArea(marker.getX(), marker.getY(), marker.getZ(), soft ? marker.getSoftRadius() : marker.getHardRadius(), listener, !soft);
        marker.markUpdated(); // Tell it we updated it
    }
    
    public void setLoadListener(WorldLoadListener listener) {
        this.loadListener = listener;
    }
    
    public void requestUnload() {
        memManager.queueUnload();
    }
    
    public void updateMasterOctree() {
        System.out.println("masterIndex: " + octreeStorage.getMasterIndex());
        int masterIndex = octreeStorage.getMasterIndex();
        masterOctree = octreeStorage.getOctree(masterIndex, registry); // TODO do we really need OffheapOctree in world for this?
        masterScale = octreeStorage.getMasterScale(128); // TODO need to have this CONFIGURABLE!
        centerX = octreeStorage.getCenterPoint(0);
        centerY = octreeStorage.getCenterPoint(1);
        centerZ = octreeStorage.getCenterPoint(2);
        System.out.println("world center: " + centerX + ", " + centerY + ", " + centerZ + ", scale: " + masterScale);
        mem.writeByte(masterOctree.memoryAddress(), (byte) 0xff); // Just in case, master octree has no single nodes
        
        // Update relevant data to world loader
        worldLoader.worldConfig(centerX, centerY, centerZ, masterIndex, masterScale);
    }

    public float getMasterScale() {
        return masterScale;
    }

}
