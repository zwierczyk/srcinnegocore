package dev.grzybalson.utils;

import org.bukkit.Chunk;
import org.bukkit.World;

import java.lang.reflect.Method;

public final class ChunkAccess {

    private static final Method GET_CHUNK_AT_IF_LOADED = findGetChunkAtIfLoaded();

    private ChunkAccess() {
    }

    public static Chunk getLoadedChunk(World world, int chunkX, int chunkZ) {
        if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
            return null;
        }

        if (GET_CHUNK_AT_IF_LOADED != null) {
            try {
                Object chunk = GET_CHUNK_AT_IF_LOADED.invoke(world, chunkX, chunkZ);
                if (chunk instanceof Chunk loadedChunk) {
                    return loadedChunk;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }

        return world.getChunkAt(chunkX, chunkZ);
    }

    private static Method findGetChunkAtIfLoaded() {
        try {
            return World.class.getMethod("getChunkAtIfLoaded", int.class, int.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
