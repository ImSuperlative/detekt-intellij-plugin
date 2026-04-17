package io.gitlab.arturbosch.detekt.idea.config.ui

import com.intellij.CommonBundle
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.openapi.util.NlsContexts.Label
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import java.awt.GraphicsEnvironment
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

@Suppress("UnstableApiUsage") // For NlsContexts annotations
internal class FilesListPanel(
    private val listModel: ListModel,
    private val project: Project,
    @DialogTitle private val fileChooserTitle: String,
    @Label private val fileChooserDescription: String,
    private val descriptorProvider: () -> FileChooserDescriptor
) {

    private val list = JBList(listModel).apply {
        dragEnabled = !GraphicsEnvironment.isHeadless()
        selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    }

    fun decorated(): JPanel =
        ToolbarDecorator.createDecorator(list)
            .setAddAction {
                onAddFileClick(listModel)
            }
            .setRemoveAction {
                onRemoveFileClick(listModel)
            }
            .setButtonComparator(CommonBundle.message("button.add"), CommonBundle.message("button.remove"))
            .createPanel()

    private fun onRemoveFileClick(listModel: ListModel) {
        listModel.removeAt(list.selectedIndices.toList())
    }

    private fun onAddFileClick(listModel: ListModel) {
        val descriptor = descriptorProvider()
        descriptor.title = fileChooserTitle
        descriptor.description = fileChooserDescription

        val files = FileChooser.chooseFiles(descriptor, list, project, null)
        for (file in files) {
            if (file != null && !listModel.items.contains(file.path)) {
                listModel += file.path
            }
        }
    }

    @Suppress("TooManyFunctions") // Required functionality
    class ListModel(initialItems: List<String> = emptyList()) : DefaultListModel<String>() {

        val items: List<String>
            get() = super.elements().toList()

        init {
            addAll(initialItems)
        }

        operator fun plusAssign(newItem: String) {
            addElement(newItem)
        }

        operator fun plusAssign(newItems: Collection<String>) {
            addAll(newItems)
        }

        fun removeAt(indices: Collection<Int>) {
            if (indices.isEmpty()) return
            indices.reversed()
                .forEach { indexToRemove -> remove(indexToRemove) }
        }
    }
}
