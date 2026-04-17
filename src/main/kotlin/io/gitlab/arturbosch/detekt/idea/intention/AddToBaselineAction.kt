package io.gitlab.arturbosch.detekt.idea.intention

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import dev.detekt.api.Issue
import dev.detekt.tooling.api.BaselineProvider
import io.gitlab.arturbosch.detekt.idea.DETEKT
import io.gitlab.arturbosch.detekt.idea.DetektBundle
import io.gitlab.arturbosch.detekt.idea.config.DetektPluginSettings
import io.gitlab.arturbosch.detekt.idea.baselineId
import io.gitlab.arturbosch.detekt.idea.id
import io.gitlab.arturbosch.detekt.idea.util.PluginUtils
import io.gitlab.arturbosch.detekt.idea.util.absoluteBaselinePath
import kotlin.io.path.exists

class AddToBaselineAction(private val finding: Issue) : IntentionAction, LowPriorityAction {

    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = DetektBundle.message("detekt.actions.addToBaseline", finding.id)

    override fun getFamilyName(): String = DETEKT

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        val id = finding.baselineId
        return id.trim().isNotEmpty() && project.isBaselineDefinedAndValid()
    }

    private fun Project.isBaselineDefinedAndValid(): Boolean {
        val settings = this.service<DetektPluginSettings>()
        val baseline = absoluteBaselinePath(this, settings)
        return baseline != null && baseline.exists()
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val settings = project.service<DetektPluginSettings>()
        val baselinePath = requireNotNull(absoluteBaselinePath(project, settings))
        val provider = BaselineProvider.load(PluginUtils::class.java.classLoader)
        val baseline = provider.read(baselinePath)
        val newBaseline = provider.of(
            baseline.manuallySuppressedIssues + finding.baselineId,
            baseline.currentIssues,
        )
        provider.write(baselinePath, newBaseline)
        DaemonCodeAnalyzer.getInstance(project).restart()
    }
}
