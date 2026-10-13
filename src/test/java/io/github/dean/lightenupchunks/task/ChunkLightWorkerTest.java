package io.github.dean.lightenupchunks.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChunkLightWorkerTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void secondPassWaitsForTheSameChunksFirstPassToFinish() {
		ChunkPos activeChunk = new ChunkPos(8, 8);
		ChunkPos nextChunk = new ChunkPos(9, 8);
		LightTask task = new LightTask(
			"minecraft:overworld", TaskMode.BOX, RelightMode.FULL_2_PASS,
			new ArrayDeque<>(List.of(activeChunk, nextChunk)), 2, 0
		);
		Map<ChunkPos, ChunkLightWorker.InFlightChunk> inFlight = new HashMap<>();
		// Only the existing key matters: submitting another pass must not touch its work or the level.
		inFlight.put(activeChunk, null);
		ChunkLightWorker worker = new ChunkLightWorker();
		try {
			assertEquals(0, worker.submitChunks(null, task, inFlight, 48));
			assertEquals(1, inFlight.size());
			assertEquals(2, task.getRemainingChunks());
			assertEquals(activeChunk, task.pollNextChunk());
			assertEquals(nextChunk, task.pollNextChunk());
			assertNull(task.pollNextChunk());
		} finally {
			worker.close();
		}
	}
}
