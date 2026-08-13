package io.github.dean.lightenupchunks.task;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LucTaskManagerSelectionTest {
	@Test
	void selectionCommandsPreserveChunkyStyleState() {
		LucTaskManager.ConfiguredScan selection = LucTaskManager.ConfiguredScan.square(0, 0, 500, 500);
		assertEquals("square", selection.shapeName());

		selection = selection.withCenter(100, -200).withRadius(300, 600);
		assertEquals("rectangle", selection.shapeName());
		assertEquals("rectangle(center=100, -200, radius=300 x 600)", selection.describe());

		selection = selection.withShape("circle");
		assertEquals("circle(center=100, -200, radius=600)", selection.describe());

		selection = selection.withShape("world");
		assertEquals("world", selection.describe());
	}
}
