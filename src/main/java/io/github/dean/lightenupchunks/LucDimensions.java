package io.github.dean.lightenupchunks;

import net.minecraft.server.level.ServerLevel;

public final class LucDimensions {
	public static final String OVERWORLD = "minecraft:overworld";
	public static final String NETHER = "minecraft:the_nether";
	public static final String END = "minecraft:the_end";

	private LucDimensions() {
	}

	public static String normalize(String value) {
		if (value == null) {
			throw new IllegalArgumentException("Dimension id cannot be null");
		}

		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("Dimension id cannot be empty");
		}
		if (trimmed.equals("overworld")) {
			return OVERWORLD;
		}
		if (trimmed.equals("nether") || trimmed.equals("the_nether")) {
			return NETHER;
		}
		if (trimmed.equals("end") || trimmed.equals("the_end")) {
			return END;
		}

		int separatorIndex = trimmed.indexOf(':');
		if (separatorIndex < 0) {
			return "minecraft:" + trimmed;
		}
		if (separatorIndex == 0 || separatorIndex == trimmed.length() - 1 || trimmed.indexOf(':', separatorIndex + 1) >= 0) {
			throw new IllegalArgumentException("Invalid dimension id: " + value);
		}

		return trimmed;
	}

	public static String asString(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

}
