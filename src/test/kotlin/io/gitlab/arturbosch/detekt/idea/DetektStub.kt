package io.gitlab.arturbosch.detekt.idea

import dev.detekt.api.Detektion
import dev.detekt.api.Issue
import dev.detekt.api.RuleInstance
import dev.detekt.api.RuleSetId
import dev.detekt.api.Severity
import dev.detekt.api.SourceLocation
import dev.detekt.api.TextLocation
import dev.detekt.tooling.api.AnalysisResult
import dev.detekt.tooling.api.Detekt
import dev.detekt.tooling.api.DetektProvider
import dev.detekt.tooling.api.spec.ProcessingSpec
import dev.detekt.tooling.internal.DefaultAnalysisResult
import org.jetbrains.kotlin.psi.KtFile

class DetektProviderStub : DetektProvider {

    override val priority: Int = 1

    override fun get(processingSpec: ProcessingSpec): Detekt = DetektStub(processingSpec)
}

class DetektStub(private val processingSpec: ProcessingSpec) : Detekt {

    override fun run(): AnalysisResult {
        val inputPath = processingSpec.projectSpec.inputPaths.single()
        if (!inputPath.fileName.toString().contains("Poko.kt")) {
            throw UnsupportedOperationException("Only Poko.kt runs are supported.")
        }

        val rule = RuleInstance(
            id = "EmptyDefaultConstructor",
            ruleSetId = RuleSetId("empty-blocks"),
            url = null,
            description = "empty",
            severity = Severity.Warning,
            active = true,
        )
        val issue = Issue(
            ruleInstance = rule,
            entity = Issue.Entity(
                signature = "Poko",
                location = Issue.Location(
                    source = SourceLocation(3, 10),
                    endSource = SourceLocation(3, 12),
                    text = TextLocation(28, 30),
                    path = inputPath,
                ),
            ),
            references = emptyList(),
            message = "empty constructor",
            severity = Severity.Warning,
            suppressReasons = emptyList(),
        )

        return DefaultAnalysisResult(Detektion(listOf(issue), listOf(rule)))
    }

    override fun run(files: Collection<KtFile>): AnalysisResult {
        throw UnsupportedOperationException()
    }
}
