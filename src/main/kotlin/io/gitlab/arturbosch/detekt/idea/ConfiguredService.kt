package io.gitlab.arturbosch.detekt.idea

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiFile
import dev.detekt.api.Issue
import dev.detekt.tooling.api.DetektProvider
import dev.detekt.tooling.api.UnexpectedError
import dev.detekt.tooling.api.spec.ProcessingSpec
import dev.detekt.tooling.api.spec.RulesSpec
import io.gitlab.arturbosch.detekt.idea.config.DetektPluginSettings
import io.gitlab.arturbosch.detekt.idea.util.DirectExecutor
import io.gitlab.arturbosch.detekt.idea.util.PluginUtils
import io.gitlab.arturbosch.detekt.idea.util.SimpleAppendable
import io.gitlab.arturbosch.detekt.idea.util.absoluteBaselinePath
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.writeText

class ConfiguredService(private val project: Project) {

    private val logger = logger<ConfiguredService>()
    private val settings = project.service<DetektPluginSettings>()

    private val projectBasePath = project.basePath?.let(::Path)

    fun validate(): List<String> {
        val messages = mutableListOf<String>()

        fun checkPaths(paths: List<Path>, messagePrefix: String) {
            paths.filter { Files.notExists(it) }.forEach { messages += "$messagePrefix <b>$it</b> does not exist." }
            paths.filter { Files.isDirectory(it) }.forEach { messages += "$messagePrefix <b>$it</b> is a directory." }
        }

        checkPaths(pluginPaths(), "Plugin jar")
        checkPaths(configPaths(), "Configuration file")

        val baseline = baseline()
        if (baseline != null && !baseline.exists()) {
            messages += "The provided baseline file <b>$baseline</b> does not exist."
        }

        return messages
    }

    private fun settings(inputPath: Path, autoCorrect: Boolean) = ProcessingSpec {
        project {
            basePath = basePathFor(inputPath, autoCorrect)
            inputPaths = listOf(inputPath)
        }
        rules {
            this.autoCorrect = autoCorrect
            activateAllRules = settings.enableAllRules
            failurePolicy = RulesSpec.FailurePolicy.NeverFail
        }
        config {
            // Do not throw an error during annotation mode as it is a common scenario
            // that the IntelliJ plugin is behind detekt core version-wise (new unknown config properties).
            shouldValidateBeforeAnalysis = false
            useDefaultConfig = settings.buildUponDefaultConfig
            configPaths = configPaths()
        }
        baseline {
            path = baseline()
        }
        extensions {
            fromPaths { pluginPaths() }
            if (!settings.enableFormatting) {
                disableExtension(FORMATTING_RULE_SET_ID)
            }
        }
        execution {
            executorService = DirectExecutor()
        }
        if (settings.redirectChannels) {
            logging {
                debug = settings.debug
                outputChannel = SimpleAppendable { logger.info(it) }
                errorChannel = SimpleAppendable { logger.warn(it) } // print to warn as error will trigger report dialog
            }
        }
    }

    private fun configPaths(): List<Path> =
        settings.configurationFilePaths
            .asSequence()
            .filter { it.isNotBlank() }
            .asAbsolutePaths()
            .toList()

    private fun pluginPaths(): List<Path> =
        settings.pluginJarPaths
            .asSequence()
            .filter { it.isNotBlank() }
            .asAbsolutePaths()
            .toList()

    private fun Sequence<String>.asAbsolutePaths() =
        map { it.replace('/', File.separatorChar) }
            .map { projectBasePath?.resolve(it) ?: Paths.get(it) }

    private fun baseline(): Path? = absoluteBaselinePath(project, settings)

    private fun basePathFor(inputPath: Path, autoCorrect: Boolean): Path =
        if (autoCorrect) {
            project.guessProjectDir()?.canonicalPath?.let { Paths.get(it) }
                ?: inputPath.parent
                ?: inputPath
        } else {
            inputPath.parent ?: inputPath
        }

    fun execute(file: PsiFile, autoCorrect: Boolean): List<Issue> {
        val pathToAnalyze = file.virtualFile
            ?.canonicalPath
            ?: return emptyList()
        val content = runCatching { runReadAction { file.text } }
            .onFailure {
                logErrorIfAllowed(
                    it,
                    "Unexpected error while reading file content: ${file.virtualFile.path}"
                )
            }
            .getOrThrow()
        return runCatching { execute(content, pathToAnalyze, autoCorrect) }
            .onFailure { logErrorIfAllowed(it, "Unexpected error while running detekt analysis") }
            .getOrDefault(emptyList())
    }

    private fun logErrorIfAllowed(error: Throwable, message: String) {
        if (error is ProcessCanceledException) {
            return // process cancellation is not allowed to be logged and will throw
        }
        logger.error(message, error)
    }

    fun execute(fileContent: String, filename: String, autoCorrect: Boolean): List<Issue> {
        if (isSpecialFileToIgnore(filename)) {
            return emptyList()
        }

        val inputPath = if (autoCorrect) Paths.get(filename) else writeTempInput(fileContent, filename)
        try {
            val result = if (autoCorrect) {
                runWriteAction { runDetekt(inputPath, autoCorrect) }
            } else {
                runDetekt(inputPath, autoCorrect)
            }

            when (val error = result.error) {
                is UnexpectedError -> throw error.cause
                null -> Unit
                else -> throw error
            }

            return result.container?.issues ?: emptyList()
        } finally {
            if (!autoCorrect) {
                inputPath.deleteIfExists()
                inputPath.parent?.deleteIfExists()
            }
        }
    }

    private fun runDetekt(inputPath: Path, autoCorrect: Boolean) =
        DetektProvider.load(PluginUtils::class.java.classLoader)
            .get(settings(inputPath, autoCorrect))
            .run()

    private fun writeTempInput(fileContent: String, filename: String): Path {
        val tempDirectory = Files.createTempDirectory("detekt-intellij-plugin")
        val tempInput = tempDirectory.resolve(Paths.get(filename).name)
        tempInput.writeText(fileContent)
        return tempInput
    }

    private fun isSpecialFileToIgnore(filename: String): Boolean =
        filename == SPECIAL_FILENAME_FOR_DEBUGGING ||
            filename.startsWith(SPECIAL_FILENAME_AI_SNIPPED)
}
