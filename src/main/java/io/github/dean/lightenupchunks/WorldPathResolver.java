package io.github.dean.lightenupchunks;

import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

public final class WorldPathResolver {
	private WorldPathResolver() {
	}

	public static Path resolveWorldRoot(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
	}

	public static Path resolveWorldRoot(ServerLevel level) {
		return resolveWorldRoot(level.getServer());
	}

	public static Path resolveDimensionRoot(ServerLevel level) {
		return DimensionType.getStorageFolder(level.dimension(), resolveWorldRoot(level))
			.toAbsolutePath()
			.normalize();
	}
}
