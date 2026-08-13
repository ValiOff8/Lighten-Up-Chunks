package io.github.dean.lightenupchunks.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.github.dean.lightenupchunks.CommandFeedback;
import io.github.dean.lightenupchunks.LucConfig;
import io.github.dean.lightenupchunks.LucConfigManager;
import io.github.dean.lightenupchunks.LucDimensions;
import io.github.dean.lightenupchunks.task.LucTaskManager;
import io.github.dean.lightenupchunks.task.RelightMode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.phys.Vec3;

public final class LucCommands {
	private static final String[] CONFIG_KEYS = new String[] {
		"keepRunningWhilePaused",
		"defaultRelightMode",
		"maxInFlightChunks",
		"maxRelightsPerTick",
		"saveFlushIntervalSeconds",
		"enableVoxyCompat",
		"bossBarShowPercentage",
		"bossBarShowCounts",
		"bossBarShowRemaining",
		"bossBarShowCurrentChunk",
		"bossBarShowRate",
		"bossBarShowEta"
	};
	private static final String[] RELIGHT_MODES = new String[] {
		RelightMode.MISSING_ONLY.serializedName(),
		RelightMode.FULL.serializedName(),
		RelightMode.FULL_2_PASS.serializedName()
	};
	private static final String[] SHAPES = new String[] {"square", "circle", "rectangle", "world"};
	private static final String[] BOOLEANS = new String[] {"true", "false"};
	private static final DynamicCommandExceptionType INVALID_DIMENSION = error("Invalid dimension id: ");
	private static final DynamicCommandExceptionType INVALID_CONFIG_KEY = error("Unknown config key: ");
	private static final DynamicCommandExceptionType INVALID_RELIGHT_MODE = error("Unknown relight mode: ");
	private static final DynamicCommandExceptionType INVALID_SHAPE = error("Unknown shape: ");

	private LucCommands() {
	}

	public static void register(
		CommandDispatcher<CommandSourceStack> dispatcher,
		CommandBuildContext buildContext,
		Commands.CommandSelection environment
	) {
		var root = literal("luc")
			.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
			.executes(context -> manager(context).overview(context.getSource()))
			.then(literal("progress")
				.executes(context -> manager(context).status(context.getSource())))
			.then(literal("selection")
				.executes(context -> manager(context).selection(context.getSource())))
			.then(buildWorldCommand())
			.then(buildShapeCommand())
			.then(buildCenterCommand())
			.then(buildRadiusCommand())
			.then(literal("corners").then(cornersArguments()))
			.then(buildRelightModeCommand())
			.then(literal("preview")
				.executes(context -> manager(context).previewConfigured(context.getSource())))
			.then(literal("start")
				.executes(context -> manager(context).startConfigured(context.getSource())))
			.then(literal("pause")
				.executes(context -> manager(context).pause(context.getSource())))
			.then(literal("continue")
				.executes(context -> manager(context).resume(context.getSource())))
			.then(literal("cancel")
				.executes(context -> manager(context).cancel(context.getSource())))
			.then(buildConfigCommand());
		dispatcher.register(root);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> buildWorldCommand() {
		return literal("world")
			.executes(context -> manager(context).configureWorld(
				context.getSource(),
				LucDimensions.asString(context.getSource().getLevel())
			))
			.then(dimensionArgument("dimension")
				.executes(context -> manager(context).configureWorld(
					context.getSource(),
					parseDimension(context, "dimension")
				)));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> buildShapeCommand() {
		return literal("shape")
			.then(argument("shape", StringArgumentType.word())
				.suggests((context, builder) -> SharedSuggestionProvider.suggest(SHAPES, builder))
				.executes(context -> manager(context).configureShape(
					context.getSource(),
					parseShape(context)
				)));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> buildCenterCommand() {
		return literal("center")
			.executes(context -> {
				BlockCoordinate center = currentPosition(context.getSource());
				return manager(context).configureCenter(context.getSource(), center.x(), center.z());
			})
			.then(argument("x", IntegerArgumentType.integer())
				.then(argument("z", IntegerArgumentType.integer())
					.executes(context -> manager(context).configureCenter(
						context.getSource(),
						IntegerArgumentType.getInteger(context, "x"),
						IntegerArgumentType.getInteger(context, "z")
					))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> buildRadiusCommand() {
		return literal("radius")
			.then(argument("radius", IntegerArgumentType.integer(0))
				.executes(context -> {
					int radius = IntegerArgumentType.getInteger(context, "radius");
					return manager(context).configureRadius(context.getSource(), radius, radius);
				})
				.then(argument("radiusZ", IntegerArgumentType.integer(0))
					.executes(context -> manager(context).configureRadius(
						context.getSource(),
						IntegerArgumentType.getInteger(context, "radius"),
						IntegerArgumentType.getInteger(context, "radiusZ")
					))));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, Integer> cornersArguments() {
		return argument("x1", IntegerArgumentType.integer())
			.then(argument("z1", IntegerArgumentType.integer())
				.then(argument("x2", IntegerArgumentType.integer())
					.then(argument("z2", IntegerArgumentType.integer())
						.executes(context -> manager(context).configureCorners(
							context.getSource(),
							IntegerArgumentType.getInteger(context, "x1"),
							IntegerArgumentType.getInteger(context, "z1"),
							IntegerArgumentType.getInteger(context, "x2"),
							IntegerArgumentType.getInteger(context, "z2")
						)))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> buildRelightModeCommand() {
		return literal("mode")
			.executes(context -> manager(context).relightMode(context.getSource()))
			.then(argument("mode", StringArgumentType.word())
				.suggests((context, builder) -> SharedSuggestionProvider.suggest(RELIGHT_MODES, builder))
				.executes(context -> manager(context).configureRelightMode(
					context.getSource(),
					parseRelightMode(context, "mode")
				)));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> buildConfigCommand() {
		return literal("config")
			.executes(context -> showConfig(context.getSource()))
			.then(literal("reload")
				.executes(context -> reloadConfig(context.getSource())))
			.then(literal("reset")
				.executes(context -> resetConfig(context.getSource())))
			.then(literal("get")
				.then(configKeyArgument("key")
					.executes(context -> getConfigValue(
						context.getSource(),
						StringArgumentType.getString(context, "key")
					))))
			.then(literal("set")
				.then(configKeyArgument("key")
					.then(argument("value", StringArgumentType.word())
						.suggests(LucCommands::suggestConfigValues)
						.executes(context -> setConfigValue(
							context.getSource(),
							StringArgumentType.getString(context, "key"),
							StringArgumentType.getString(context, "value")
						)))));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> dimensionArgument(String name) {
		return argument(name, StringArgumentType.word())
			.suggests((context, builder) -> SharedSuggestionProvider.suggest(dimensionSuggestions(context.getSource()), builder));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> configKeyArgument(String name) {
		return argument(name, StringArgumentType.word())
			.suggests((context, builder) -> SharedSuggestionProvider.suggest(CONFIG_KEYS, builder));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
		return LiteralArgumentBuilder.literal(name);
	}

	private static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
		return RequiredArgumentBuilder.argument(name, type);
	}

	private static LucTaskManager manager(CommandContext<CommandSourceStack> context) {
		return LucTaskManager.get(context.getSource().getServer());
	}

	private static String parseDimension(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
		String value = StringArgumentType.getString(context, name);
		try {
			return LucDimensions.normalize(value);
		} catch (IllegalArgumentException exception) {
			throw INVALID_DIMENSION.create(value);
		}
	}

	private static RelightMode parseRelightMode(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
		String value = StringArgumentType.getString(context, name);
		try {
			return RelightMode.fromSerializedName(value);
		} catch (IllegalArgumentException exception) {
			throw INVALID_RELIGHT_MODE.create(value);
		}
	}

	private static String parseShape(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String value = StringArgumentType.getString(context, "shape").toLowerCase(Locale.ROOT);
		for (String shape : SHAPES) {
			if (shape.equals(value)) {
				return value;
			}
		}
		throw INVALID_SHAPE.create(value);
	}

	private static BlockCoordinate currentPosition(CommandSourceStack source) {
		Vec3 position = source.getPosition();
		return new BlockCoordinate((int) Math.floor(position.x), (int) Math.floor(position.z));
	}

	private static int showConfig(CommandSourceStack source) {
		LucConfig config = LucConfigManager.get();
		CommandFeedback.sendSuccess(
			source,
			"LUC config | path=" + LucConfigManager.path() +
				" | keepRunningWhilePaused=" + config.keepRunningWhilePaused +
				" | defaultRelightMode=" + config.defaultRelightMode +
				" | maxInFlightChunks=" + config.maxInFlightChunks +
				" | maxRelightsPerTick=" + config.maxRelightsPerTick +
				" | saveFlushIntervalSeconds=" + config.saveFlushIntervalSeconds +
				" | enableVoxyCompat=" + config.enableVoxyCompat +
				" | bossBarShowPercentage=" + config.bossBarShowPercentage +
				" | bossBarShowCounts=" + config.bossBarShowCounts +
				" | bossBarShowRemaining=" + config.bossBarShowRemaining +
				" | bossBarShowCurrentChunk=" + config.bossBarShowCurrentChunk +
				" | bossBarShowRate=" + config.bossBarShowRate +
				" | bossBarShowEta=" + config.bossBarShowEta,
			false
		);
		return 1;
	}

	private static int reloadConfig(CommandSourceStack source) {
		LucConfigManager.reload();
		CommandFeedback.sendSuccess(source, "Reloaded config from " + LucConfigManager.path() + ".", false);
		return 1;
	}

	private static int resetConfig(CommandSourceStack source) throws CommandSyntaxException {
		try {
			LucConfigManager.reset();
		} catch (IOException exception) {
			throw commandError("Failed to reset config: " + exception.getMessage());
		}
		CommandFeedback.sendSuccess(source, "Reset config to defaults at " + LucConfigManager.path() + ".", true);
		return 1;
	}

	private static int setConfigValue(CommandSourceStack source, String key, String value) throws CommandSyntaxException {
		if (!isKnownConfigKey(key)) {
			throw INVALID_CONFIG_KEY.create(key);
		}
		LucConfig updated;
		try {
			updated = LucConfigManager.update(config -> applyConfigValue(config, key, value));
		} catch (IOException exception) {
			throw commandError("Failed to save config: " + exception.getMessage());
		} catch (IllegalArgumentException exception) {
			throw commandError("Invalid value for " + key + ": " + exception.getMessage());
		}
		CommandFeedback.sendSuccess(source, "Set " + key + "=" + configValue(updated, key) + " in " + LucConfigManager.path() + ".", true);
		return 1;
	}

	private static int getConfigValue(CommandSourceStack source, String key) throws CommandSyntaxException {
		if (!isKnownConfigKey(key)) {
			throw INVALID_CONFIG_KEY.create(key);
		}
		CommandFeedback.sendSuccess(source, "LUC config | " + key + "=" + configValue(LucConfigManager.get(), key), false);
		return 1;
	}

	private static void applyConfigValue(LucConfig config, String key, String value) {
		switch (key) {
			case "keepRunningWhilePaused" -> config.keepRunningWhilePaused = parseBoolean(value);
			case "defaultRelightMode" -> config.defaultRelightMode = RelightMode.fromSerializedName(value).serializedName();
			case "maxInFlightChunks" -> config.maxInFlightChunks = parseNonNegativeInt(value, key);
			case "maxRelightsPerTick" -> config.maxRelightsPerTick = parseNonNegativeInt(value, key);
			case "saveFlushIntervalSeconds" -> config.saveFlushIntervalSeconds = parsePositiveInt(value, key);
			case "enableVoxyCompat" -> config.enableVoxyCompat = parseBoolean(value);
			case "bossBarShowPercentage" -> config.bossBarShowPercentage = parseBoolean(value);
			case "bossBarShowCounts" -> config.bossBarShowCounts = parseBoolean(value);
			case "bossBarShowRemaining" -> config.bossBarShowRemaining = parseBoolean(value);
			case "bossBarShowCurrentChunk" -> config.bossBarShowCurrentChunk = parseBoolean(value);
			case "bossBarShowRate" -> config.bossBarShowRate = parseBoolean(value);
			case "bossBarShowEta" -> config.bossBarShowEta = parseBoolean(value);
			default -> throw new IllegalArgumentException(key);
		}
	}

	private static String configValue(LucConfig config, String key) {
		return switch (key) {
			case "keepRunningWhilePaused" -> Boolean.toString(config.keepRunningWhilePaused);
			case "defaultRelightMode" -> config.defaultRelightMode;
			case "maxInFlightChunks" -> Integer.toString(config.maxInFlightChunks);
			case "maxRelightsPerTick" -> Integer.toString(config.maxRelightsPerTick);
			case "saveFlushIntervalSeconds" -> Integer.toString(config.saveFlushIntervalSeconds);
			case "enableVoxyCompat" -> Boolean.toString(config.enableVoxyCompat);
			case "bossBarShowPercentage" -> Boolean.toString(config.bossBarShowPercentage);
			case "bossBarShowCounts" -> Boolean.toString(config.bossBarShowCounts);
			case "bossBarShowRemaining" -> Boolean.toString(config.bossBarShowRemaining);
			case "bossBarShowCurrentChunk" -> Boolean.toString(config.bossBarShowCurrentChunk);
			case "bossBarShowRate" -> Boolean.toString(config.bossBarShowRate);
			case "bossBarShowEta" -> Boolean.toString(config.bossBarShowEta);
			default -> throw new IllegalArgumentException(key);
		};
	}

	private static List<String> dimensionSuggestions(CommandSourceStack source) {
		Set<String> suggestions = new LinkedHashSet<>(List.of("overworld", "nether", "end"));
		for (ServerLevel level : source.getServer().getAllLevels()) {
			String dimension = LucDimensions.asString(level);
			if (!dimension.equals(LucDimensions.OVERWORLD) && !dimension.equals(LucDimensions.NETHER) && !dimension.equals(LucDimensions.END)) {
				suggestions.add(dimension);
			}
		}
		return new ArrayList<>(suggestions);
	}

	private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestConfigValues(
		CommandContext<CommandSourceStack> context,
		com.mojang.brigadier.suggestion.SuggestionsBuilder builder
	) {
		String key = StringArgumentType.getString(context, "key");
		if (key.equals("defaultRelightMode")) {
			return SharedSuggestionProvider.suggest(RELIGHT_MODES, builder);
		}
		if (key.equals("maxInFlightChunks") || key.equals("maxRelightsPerTick")) {
			return SharedSuggestionProvider.suggest(new String[] {"0", "16", "32", "48", "80", "128", "256"}, builder);
		}
		if (key.equals("saveFlushIntervalSeconds")) {
			return SharedSuggestionProvider.suggest(new String[] {"10", "30", "60", "120"}, builder);
		}
		return SharedSuggestionProvider.suggest(BOOLEANS, builder);
	}

	private static boolean parseBoolean(String value) {
		if (value.equalsIgnoreCase("true")) {
			return true;
		}
		if (value.equalsIgnoreCase("false")) {
			return false;
		}
		throw new IllegalArgumentException("Expected true or false");
	}

	private static int parseNonNegativeInt(String value, String key) {
		int parsed = parseInt(value, key);
		if (parsed < 0) {
			throw new IllegalArgumentException(key + " must be >= 0");
		}
		return parsed;
	}

	private static int parsePositiveInt(String value, String key) {
		int parsed = parseInt(value, key);
		if (parsed <= 0) {
			throw new IllegalArgumentException(key + " must be > 0");
		}
		return parsed;
	}

	private static int parseInt(String value, String key) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Expected an integer for " + key, exception);
		}
	}

	private static boolean isKnownConfigKey(String key) {
		return List.of(CONFIG_KEYS).contains(key);
	}

	private static DynamicCommandExceptionType error(String prefix) {
		return new DynamicCommandExceptionType(value -> Component.literal(prefix + value));
	}

	private static CommandSyntaxException commandError(String message) {
		return new DynamicCommandExceptionType(value -> Component.literal(String.valueOf(value))).create(message);
	}

	private record BlockCoordinate(int x, int z) {
	}
}
