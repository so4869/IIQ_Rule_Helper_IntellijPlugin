package im.flare.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.io.File
import java.io.IOException
import java.io.OutputStream

class IIQImportRunState(
    private val environment: ExecutionEnvironment,
    private val config: IIQImportRunConfiguration
) : RunProfileState {

    override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult {
        val project = environment.project
        val processHandler = createProcessHandler()
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        console.attachToProcess(processHandler)
        processHandler.startNotify()

        object : Task.Backgroundable(project, "Reading snapshot file...", false) {
            // name → raw xml block
            private var ruleBlocks: List<Pair<String, String>> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val file = File(config.snapshotFile)
                if (!file.exists()) throw IllegalStateException("Snapshot file not found: ${config.snapshotFile}")

                console.info("Reading snapshot: ${file.name}\n")
                val xml = file.readText(Charsets.UTF_8)

                ruleBlocks = IIQXmlUtils.extractRuleBlocksFromSnapshot(xml)

                if (ruleBlocks.isEmpty()) throw IllegalStateException("No <Rule> elements found in snapshot file.")

                console.info("Found ${ruleBlocks.size} rule(s) in snapshot.\n")
            }

            override fun onSuccess() {
                val allNames = ruleBlocks.map { it.first }
                val dialog = IIQExportSelectDialog(
                    project = project,
                    ruleNames = allNames,
                    preChecked = allNames.toSet(),
                    dialogTitle = "IIQ Import Snapshot",
                    okButtonText = "Import",
                    headerText = "Select rules to import to the IIQ server."
                )

                if (!dialog.showAndGet()) {
                    processHandler.destroyProcess()
                    return
                }

                val selectedNames = dialog.getCheckedRules().toSet()
                if (selectedNames.isEmpty()) {
                    console.error("No rules selected. Import aborted.\n")
                    processHandler.destroyProcess()
                    return
                }

                val selected = ruleBlocks.filter { it.first in selectedNames }
                performImport(project, selected, processHandler, console)
            }

            override fun onThrowable(error: Throwable) {
                console.error("Failed to read snapshot: ${error.message}\n")
                processHandler.destroyProcess()
                notifyError(project, "Import failed: ${error.message}")
            }
        }.queue()

        return DefaultExecutionResult(console, processHandler)
    }

    private fun performImport(
        project: Project,
        ruleBlocks: List<Pair<String, String>>,
        processHandler: ProcessHandler,
        console: ConsoleView
    ) {
        object : Task.Backgroundable(project, "Importing rules to server...", false) {
            private var successCount = 0
            private val errors = mutableListOf<Pair<String, String>>()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                console.info("\nImporting ${ruleBlocks.size} rule(s)...\n\n")

                ruleBlocks.forEachIndexed { index, (ruleName, ruleXml) ->
                    indicator.fraction = (index + 1).toDouble() / ruleBlocks.size
                    indicator.text = "Importing '$ruleName'..."
                    console.info("Importing '$ruleName'...\n")

                    runCatching {
                        val existing = IIQServerUploader.findExisting(config, ruleName)
                        val result = IIQServerUploader.upload(config, ruleXml, existing?.id)
                        console.info("  → POST ${result.endpoint}\n")
                        result.response.use { response ->
                            if (!response.isSuccessful) {
                                throw IOException("HTTP ${response.code}: ${response.body?.string()?.take(200)}")
                            }
                        }
                    }.onSuccess {
                        successCount++
                        console.info("  ✓ Done\n")
                    }.onFailure { e ->
                        val msg = e.message ?: "Unknown error"
                        errors.add(ruleName to msg)
                        console.error("  ✗ Failed: $msg\n")
                    }
                }
            }

            override fun onSuccess() {
                processHandler.destroyProcess()
                if (errors.isEmpty()) {
                    console.info("\nImport complete: $successCount rule(s) imported successfully.\n")
                    notifyInfo(project, "Import complete: $successCount rule(s) imported.")
                } else {
                    console.error("\n$successCount imported, ${errors.size} failed.\n")
                    notifyError(project, "$successCount imported, ${errors.size} failed. See Run console for details.")
                }
            }

            override fun onThrowable(error: Throwable) {
                console.error("Import failed: ${error.message}\n")
                processHandler.destroyProcess()
                notifyError(project, "Import failed: ${error.message}")
            }
        }.queue()
    }


    private fun notifyInfo(project: Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }

    private fun notifyError(project: Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(message, NotificationType.ERROR)
            .notify(project)
    }

    private fun createProcessHandler(): ProcessHandler = object : ProcessHandler() {
        override fun destroyProcessImpl() { notifyProcessTerminated(0) }
        override fun detachProcessImpl() { notifyProcessDetached() }
        override fun detachIsDefault(): Boolean = false
        override fun getProcessInput(): OutputStream? = null
    }

    companion object {
        private const val NOTIFICATION_GROUP = "IIQ Rule Helper"
    }

    private fun ConsoleView.info(text: String) =
        print(text, ConsoleViewContentType.NORMAL_OUTPUT)

    private fun ConsoleView.error(text: String) =
        print(text, ConsoleViewContentType.ERROR_OUTPUT)
}
