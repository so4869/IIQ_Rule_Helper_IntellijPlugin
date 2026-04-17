package im.flare.action

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import im.flare.run.IIQRunConfiguration
import im.flare.run.IIQServerUploader
import im.flare.run.IIQSourceExtractor
import im.flare.run.IIQTemplateProcessor

class CompareWithServerAction(
    private val config: IIQRunConfiguration,
    private val psiClass: PsiClass
) : AnAction(config.name) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        object : Task.Backgroundable(project, "Comparing with server (${config.name})...", false) {
            private var serverXml: String? = null
            private var localXml: String? = null
            private var ruleName: String? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                // 1. Extract class info from PSI
                val info = ReadAction.compute<im.flare.run.IIQClassInfo?, Throwable> {
                    IIQSourceExtractor.extract(psiClass)
                } ?: throw IllegalStateException("Failed to extract class info.")

                val name = info.name
                    ?: throw IllegalStateException("'name' field not found in ${info.qualifiedName}")
                val type = info.type
                    ?: throw IllegalStateException("'type' field not found in ${info.qualifiedName}")
                ruleName = name

                // 2. Search for existing rule on server
                indicator.text = "Searching for rule '$name' on server..."
                val existing = IIQServerUploader.findExisting(config, name)
                    ?: throw IllegalStateException("Rule '$name' not found on server '${config.name}'.")

                // 3. Fetch full rule XML from server
                indicator.text = "Fetching rule from server..."
                serverXml = IIQServerUploader.fetchRuleXml(config, existing.id)
                    ?: throw IllegalStateException("Failed to fetch rule XML from server.")

                // 4. Build local XML
                indicator.text = "Building local XML..."
                val templateFile = IIQTemplateProcessor.resolveTemplateFile(config.templateDirectory, type)
                if (!templateFile.exists()) {
                    throw IllegalStateException("Template not found: ${templateFile.name}")
                }
                localXml = IIQTemplateProcessor.process(
                    templateFile = templateFile,
                    info = info,
                    existingId = existing.id,
                    createdMs = existing.createdMs,
                    modifiedMs = existing.modifiedMs
                )
            }

            override fun onSuccess() {
                val sXml = serverXml ?: return
                val lXml = localXml ?: return

                val serverCdata = extractSourceContent(sXml)
                val localCdata = extractSourceContent(lXml)

                val diffFactory = DiffContentFactory.getInstance()
                val localContent = diffFactory.create(localCdata, JavaFileType.INSTANCE)
                val serverContent = diffFactory.create(serverCdata, JavaFileType.INSTANCE)

                val request = SimpleDiffRequest(
                    "Compare with Server: $ruleName [${config.name}]",
                    localContent,
                    serverContent,
                    "Local",
                    "Server (${config.name})"
                )

                DiffManager.getInstance().showDiff(project, request, DiffDialogHints.MODAL)
            }

            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(
                    project,
                    error.message ?: "Unknown error",
                    "Compare with Server Failed"
                )
            }
        }.queue()
    }

    private fun extractSourceContent(xml: String): String {
        val sourceRegex = Regex("<Source>([\\s\\S]*?)</Source>")
        val sourceContent = sourceRegex.find(xml)?.groupValues?.get(1)?.trim() ?: xml
        val cdataRegex = Regex("<!\\[CDATA\\[([\\s\\S]*?)\\]\\]>")
        return cdataRegex.find(sourceContent)?.groupValues?.get(1)?.trim() ?: sourceContent
    }
}
