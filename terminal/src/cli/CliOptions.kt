package io.cuttlefish.cli

import io.cuttlefish.components.Clock
import io.cuttlefish.config.EmulatorConfig
import io.cuttlefish.config.GlobalConfig
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException

data class CliFlags(
	val shouldDump: Boolean,
	val isDebug: Boolean,
	val isDebugF: Boolean,
	val isProfile: Boolean,
	val outPath: String?,
	val positionalArgs: List<String>
) {
	val resolvedOutPath: String get() = outPath ?: "out.bin"
	val baseName: String get() = resolvedOutPath.substringBeforeLast(".")

	companion object {
		fun parse(args: List<String>): CliFlags {
			var shouldDump = false
			var isDebug = false
			var isDebugF = false
			var isProfile = false
			var outPath: String? = null
			val positional = mutableListOf<String>()

			var i = 0
			while (i < args.size) {
				when (val arg = args[i]) {
					"--dump" -> shouldDump = true
					"--debug" -> isDebug = true
					"--debugf" -> isDebugF = true
					"--profile" -> isProfile = true
					"-o" -> {
						if (i + 1 >= args.size) throw IllegalArgumentException("Missing output path after -o")
						outPath = args[++i]
					}
					else -> positional.add(arg)
				}
				i++
			}

			return CliFlags(shouldDump, isDebug, isDebugF, isProfile, outPath, positional)
		}
	}
}

fun getFileOrThrow(path: String): File {
	val file = File(path)
	if (!file.exists()) throw FileNotFoundException("File not found: $path")
	return file
}

fun expandPaths(inputPaths: List<String>): List<String> {
	val expanded = mutableListOf<String>()
	for (path in inputPaths) {
		val file = File(path)
		if (file.isDirectory) {
			file.list()?.forEach { child ->
				expanded.add("${file.absolutePath}/$child")
			}
		} else {
			expanded.add(path)
		}
	}
	if (expanded.isEmpty()) throw IllegalArgumentException("No input files resolved")
	return expanded
}

fun loadConfig() {
	val configFile = File("configurations/Emulator config.json")
	val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

	val config = if (configFile.exists()) {
		try {
			json.decodeFromString<EmulatorConfig>(configFile.readText())
		} catch (e: Exception) {
			println("[WARNING] Failed to parse emulator_config.json. Using defaults. (${e.message})")
			EmulatorConfig()
		}
	} else {
		val defaultConfig = EmulatorConfig()
		configFile.parentFile?.mkdirs()
		configFile.writeText(json.encodeToString(defaultConfig))
		defaultConfig
	}

	Clock.applyConfig(config.clock)
	GlobalConfig.debug = config.debug
}