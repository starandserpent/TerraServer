package com.ritualsoftheold.terra.world;

/**
 * Load markers are used by some world implementations to figure out
 * which parts to generate and keep loaded.
 *
 */
public class LoadMarker {
    
    /**
     * Coordinates for this marker.
     */
    private float x, y, z;
    
    private float hardRadius;
    
    private float softRadius;
    
    private boolean hasMoved;
    
    public LoadMarker(float x, float y, float z, float hardRadius, float softRadius) {
        move(x, y, z);
        this.hardRadius = hardRadius;
        this.softRadius = softRadius;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }
    
    public void move(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        
        this.hasMoved = true;
    }
    
    /**
     * World data must be kept loaded or loaded when its distance squared
     * to this marker is less than hard radius.
     * @return Hard radius (units squared).
     */
    public float getHardRadius() {
        return hardRadius;
    }
    
    /**
     * World data should usually be kept loaded when its distance squared
     * to this marker is less than soft radius.
     * @return Soft radius (units squared).
     */
    public float getSoftRadius() {
        return softRadius;
    }
    
    /**
     * Tells if this marker has been moved since last update.
     * @return Has this marker moved.
     */
    public boolean hasMoved() {
        return hasMoved;
    }
    
    /**
     * Marks this load marker as updated.
     */
    public void markUpdated() {
        hasMoved = false;
    }
}
