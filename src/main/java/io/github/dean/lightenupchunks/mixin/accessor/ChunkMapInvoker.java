package io.github.dean.lightenupchunks.mixin.accessor;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapInvoker {
	@Invoker("save")
	boolean lightenupchunks$saveChunk(ChunkAccess chunkAccess);
}
