package com.ritualsoftheold.terra.mesher.resource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.jme3.asset.AssetManager;
import com.jme3.texture.Image;
import com.jme3.texture.Image.Format;
import com.jme3.texture.Texture.MagFilter;
import com.jme3.texture.Texture.MinFilter;
import com.jme3.texture.TextureArray;
import com.ritualsoftheold.terra.core.material.MaterialRegistry;
import com.ritualsoftheold.terra.core.material.TerraMaterial;
import com.ritualsoftheold.terra.core.material.TerraTexture;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;

/**
 * Manages textures of materials. Creates texture atlases.
 *
 */
public class TextureManager {

    private static final int TEXTURE_MIN_RES = 2;
    //Size of the cube
    public static final int ATLAS_SIZE = 256;
    private static final int BYTES_PER_PIXEL = 4;
    private static final int ATLAS_SIZE_IMAGE = ATLAS_SIZE * BYTES_PER_PIXEL;
    private int x;
    private int y;

    private TerraTexture mainTexture;
    private Int2ObjectMap<TerraTexture> textures;
    private AssetManager assetManager;
    private MaterialRegistry reg;
    private HashMap<TerraTexture, Image> imageTypes;

    public TextureManager(AssetManager assetManager, MaterialRegistry reg) {
        textures = new Int2ObjectArrayMap<>();
        this.assetManager = assetManager;
        this.reg = reg;
    }
    /**
     * Returns texture array used for ground texture.
     * @return Ground texture array.
     */

    public TextureArray convertTexture(TerraTexture[][] terraTextures, Image mainImage){
        // TODO make these configurable, Rituals art style already changed a bit since I wrote this
        ArrayList<Image> atlases = new ArrayList<>();
        int texturesPerSide = ATLAS_SIZE / 256;

        ByteBuffer atlasBuf = ByteBuffer.allocateDirect(ATLAS_SIZE * ATLAS_SIZE * BYTES_PER_PIXEL);
        x = 0;
        y = 0;
        makeImage(mainImage, null, texturesPerSide, atlasBuf, 256);

        for(int x = 0; x < 16; x++){
            for(int y = 0; y < 16; y++){
                this.x = x * 16;
                this.y = y * 16;
                TerraTexture terraTexture = terraTextures[x][y];
                if(terraTexture != mainTexture && terraTexture != null) {
                    Image image = assetManager.loadTexture(terraTexture.getAsset()).getImage();
                    makeImage(image, terraTexture, texturesPerSide, atlasBuf, 16);
                }
            }
        }

        if (atlasBuf.position() != 0) {
            System.out.println("Incomplete atlas");
            Image incompleteAtlas = new Image(Format.ABGR8, ATLAS_SIZE, ATLAS_SIZE, atlasBuf, null, com.jme3.texture.image.ColorSpace.Linear);
            atlases.add(incompleteAtlas);
        }

        TextureArray array = new TextureArray(atlases);
        array.setMagFilter(MagFilter.Nearest);
        array.setMinFilter(MinFilter.NearestNoMipMaps);
        return array;
    }

    public Image convertMainTexture(TerraTexture mainTexture){
        this.mainTexture = mainTexture;
        return imageTypes.get(mainTexture);
    }

    public void loadMaterials() {
        textures.clear(); // Clear previous textures
        Int2ObjectMap<List<TerraTexture>> resulutions = new Int2ObjectArrayMap<>();

        for (TerraMaterial mat : reg.getAllMaterials()) {
            TerraTexture texture = mat.getTexture();
            if (texture == null) {
                continue; // This material has no texture (e.g. air)
            }
            textures.put(mat.getWorldId(), texture); // Put texture to map

            int width = texture.getWidth();
            int height = texture.getHeight();


            // TODO check that texture is power of 2
            if (width != height) {
                throw new UnsupportedOperationException("non-square textures are not yet supported");
            }

            List<TerraTexture> sameRes = resulutions.getOrDefault(width, new ArrayList<>()); // Get or create list of others with same res
            sameRes.add(texture); // Add this texture to list
            resulutions.put(width, sameRes); // Re-put list if we actually only just created it
        }

        imageTypes = new HashMap<>();
        for (Entry<List<TerraTexture>> e : resulutions.int2ObjectEntrySet()) {
            generateAtlases(e.getValue(), e.getIntKey(), imageTypes); // Generate atlases...
        }
    }

    private void generateAtlases(List<TerraTexture> textures, int size, HashMap<TerraTexture, Image> imageTypes) {
        int texturesPerSide = ATLAS_SIZE / size;

        for (TerraTexture texture : textures) {
            int x = 0;
            int y = 0;
            ByteBuffer atlasBuf = ByteBuffer.allocateDirect(ATLAS_SIZE * ATLAS_SIZE * BYTES_PER_PIXEL + 1); // 4 for alpha channel+colors, TODO configurable
            Image img = assetManager.loadTexture(texture.getAsset()).getImage(); // Use asset manager to load
            int atlasStart = x * size * BYTES_PER_PIXEL + y * size * ATLAS_SIZE_IMAGE;
            ByteBuffer imgData = img.getData(0);
            for (int i = 0; i < size; i++) {
                byte[] row = new byte[size * BYTES_PER_PIXEL]; // Create array for one row of image data
                imgData.position(i * size * BYTES_PER_PIXEL);
                imgData.get(row); // Copy one row of data to array
                atlasBuf.position(atlasStart + i * ATLAS_SIZE_IMAGE); // Travel to correct point in atlas data
                atlasBuf.put(row); // Set a row of data to atlas
            }

            // Assign texture data for shader
            texture.setPage(imageTypes.size()); // Texture array id, "page"
            texture.setTileId(y * texturesPerSide + x); // Texture tile id
            texture.setTexturesPerSide(texturesPerSide); // For MeshContainer

            // Not full atlas, but not empty either
            if (atlasBuf.position() != 0) {
                System.out.println("Incomplete atlas");
                Image incompleteAtlas = new Image(Format.ABGR8, ATLAS_SIZE, ATLAS_SIZE, atlasBuf, null, com.jme3.texture.image.ColorSpace.Linear);
                imageTypes.put(texture, incompleteAtlas);
            }
        }
    }

    public void makeImage(Image image, TerraTexture texture, int texturesPerSide, ByteBuffer atlasBuf, int size) {
        long atlasStart = (x * size * BYTES_PER_PIXEL + y * size * BYTES_PER_PIXEL) * ATLAS_SIZE;

        ByteBuffer imgData = image.getData(0);
        for (int i = 0; i < size; i++) {
            byte[] row = new byte[size * BYTES_PER_PIXEL]; // Create array for one row of image data
            imgData.position(i * size * BYTES_PER_PIXEL);
            imgData.get(row); // Copy one row of data to array
            atlasBuf.position((int) (atlasStart + i * ATLAS_SIZE * BYTES_PER_PIXEL)); // Travel to correct point in atlas data
            atlasBuf.put(row); // Set a row of data to atlas
        }

        if (texture != null) {
            // Assign texture data for shader
            texture.setTileId(y * texturesPerSide + x); // Texture tile id
            texture.setTexturesPerSide(texturesPerSide); // For MeshContainer
        }
        x++;
    }

    public int getAtlasSize() {
        return ATLAS_SIZE;
    }
}