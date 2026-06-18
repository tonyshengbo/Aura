package com.auracode.assistant.toolwindow.shell

import com.auracode.assistant.i18n.AuraCodeBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.JPanel

class AgentToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowTitle = AuraCodeBundle.message("plugin.name")
        toolWindow.setTitle(toolWindowTitle)
        toolWindow.setStripeTitle(toolWindowTitle)
        val contentDisposable = Disposer.newDisposable("Aura Code ToolWindow Content")
        val content = try {
            val panel = ComposeToolWindowPanel(project, toolWindow, contentDisposable)
            Disposer.register(contentDisposable, panel)
            ContentFactory.getInstance().createContent(panel, "", false).also {
                it.setDisposer(contentDisposable)
            }
        } catch (t: Throwable) {
            Disposer.dispose(contentDisposable)
            LOG.error("Failed to create Aura Code panel", t)
            ContentFactory.getInstance().createContent(buildFallbackPanel(t), "", false)
        }
        ToolWindowPrimaryContentPresentation.configure(toolWindow.component, content)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true

    private fun buildFallbackPanel(t: Throwable): JPanel {
        val area = JBTextArea()
        area.isEditable = false
        area.text = buildString {
            appendLine("Aura Code failed to initialize.")
            appendLine()
            appendLine("${t::class.java.simpleName}: ${t.message}")
            appendLine()
            appendLine("Check IDE logs: Help -> Show Log in Finder/Explorer")
        }
        val panel = JPanel(BorderLayout())
        panel.add(JBScrollPane(area), BorderLayout.CENTER)
        return panel
    }

    companion object {
        private val LOG = Logger.getInstance(AgentToolWindowFactory::class.java)
    }
}
