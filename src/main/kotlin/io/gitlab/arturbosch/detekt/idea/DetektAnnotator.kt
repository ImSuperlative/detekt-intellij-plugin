package io.gitlab.arturbosch.detekt.idea

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import dev.detekt.api.Issue
import dev.detekt.api.Severity
import dev.detekt.api.TextLocation
import io.gitlab.arturbosch.detekt.idea.config.DetektPluginSettings
import io.gitlab.arturbosch.detekt.idea.intention.AddToBaselineAction
import io.gitlab.arturbosch.detekt.idea.intention.AutoCorrectIntention
import io.gitlab.arturbosch.detekt.idea.util.isDetektEnabled
import io.gitlab.arturbosch.detekt.idea.util.showNotification
import org.jetbrains.kotlin.idea.KotlinLanguage

class DetektAnnotator : ExternalAnnotator<PsiFile, List<Issue>>() {

    override fun collectInformation(file: PsiFile): PsiFile = file

    override fun doAnnotate(collectedInfo: PsiFile): List<Issue> {
        if (
            !collectedInfo.project.isDetektEnabled() ||
            !isKotlinFile(collectedInfo)
        ) {
            return emptyList()
        }

        val service = ConfiguredService(collectedInfo.project)
        val problems = service.validate()

        return if (problems.isNotEmpty()) {
            showNotification(problems, collectedInfo.project)
            emptyList()
        } else {
            service.execute(collectedInfo, autoCorrect = false)
        }
    }

    private fun isKotlinFile(psiFile: PsiFile): Boolean =
        psiFile.language.id == KotlinLanguage.INSTANCE.id ||
            psiFile.getUserData(TEST_KOTLIN_LANGUAGE_ID_KEY) == KotlinLanguage.INSTANCE.id

    override fun apply(
        file: PsiFile,
        annotationResult: List<Issue>,
        holder: AnnotationHolder,
    ) {
        val settings = file.project.service<DetektPluginSettings>()
        val hasCustomConfig = settings.configurationFilePaths.isNotEmpty()
        for (finding in annotationResult) {
            val textRange = finding.location.text.toTextRange()
            val message = buildString {
                append("detekt - ")
                append(finding.id)
                append(": ")
                append(finding.messageOrDescription())
            }
            val severity = getSeverity(finding, hasCustomConfig, settings.treatAsErrors)
            val annotationBuilder = holder.newAnnotation(severity, message)
                .range(textRange)

            if (textRange == file.textRange) {
                annotationBuilder.fileLevel()
            }

            if (finding.isAutoCorrectable) {
                annotationBuilder.withFix(AutoCorrectIntention())
            } else {
                annotationBuilder.withFix(AddToBaselineAction(finding))
            }

            annotationBuilder.create()
        }
    }

    private fun getSeverity(finding: Issue, hasCustomConfig: Boolean, treatAsError: Boolean): HighlightSeverity {
        if (treatAsError) {
            return HighlightSeverity.ERROR
        }

        if (!hasCustomConfig) {
            return HighlightSeverity.WARNING
        }

        return when (finding.severity) {
            Severity.Error -> HighlightSeverity.ERROR
            Severity.Warning -> HighlightSeverity.WARNING
            Severity.Info -> HighlightSeverity.WEAK_WARNING
        }
    }

    private fun TextLocation.toTextRange(): TextRange = TextRange.create(start, end)
}
