package io.gitlab.arturbosch.detekt.idea.problems

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.openapi.vfs.VirtualFile
import dev.detekt.api.Issue
import dev.detekt.api.Severity
import io.gitlab.arturbosch.detekt.idea.description
import io.gitlab.arturbosch.detekt.idea.id
import io.gitlab.arturbosch.detekt.idea.messageOrDescription
import javax.swing.Icon

class DetektProblem(
    override val provider: DetektProblemsProvider,
    override val file: VirtualFile,
    private val finding: Issue,
) : FileProblem {

    override val text: String
        get() = finding.messageOrDescription()

    override val description: String
        get() = finding.description

    override val group: String
        get() = finding.id

    override val icon: Icon
        get() = when (finding.severity) {
            Severity.Error -> HighlightDisplayLevel.ERROR.icon
            Severity.Warning -> HighlightDisplayLevel.WARNING.icon
            Severity.Info -> HighlightDisplayLevel.WEAK_WARNING.icon
        }

    override val line: Int
        get() = finding.location.source.line - 1

    override val column: Int
        get() = finding.location.source.column - 1
}
