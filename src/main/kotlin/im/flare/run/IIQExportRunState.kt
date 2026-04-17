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
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date

class IIQExportRunState(
    private val environment: ExecutionEnvironment,
    private val config: IIQExportRunConfiguration
) : RunProfileState {

    override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult {
        val project = environment.project
        val processHandler = createProcessHandler()
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        console.attachToProcess(processHandler)
        processHandler.startNotify()

        object : Task.Backgroundable(project, "Fetching rule list from server...", false) {
            private var rules: List<IIQServerUploader.ExistingRule> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                console.info("Connecting to ${config.url}...\n")
                rules = when (config.exportMode) {
                    ExportMode.ALL -> {
                        console.info("Fetching all rules from server...\n")
                        IIQServerUploader.listRules(config)
                    }
                    ExportMode.SEARCH -> {
                        console.info("Searching rules matching '${config.searchQuery}'...\n")
                        IIQServerUploader.listRules(config, config.searchQuery)
                    }
                    ExportMode.SELECT -> {
                        console.info("Fetching rule list for selection...\n")
                        IIQServerUploader.listRules(config)
                    }
                }
                console.info("Found ${rules.size} rule(s).\n")
            }

            override fun onSuccess() {
                if (rules.isEmpty()) {
                    console.error("No rules found. Export aborted.\n")
                    processHandler.destroyProcess()
                    return
                }

                val selectedRules = if (config.exportMode == ExportMode.SELECT) {
                    val dialog = IIQExportSelectDialog(project, rules.map { it.name })
                    if (!dialog.showAndGet()) {
                        processHandler.destroyProcess()
                        return
                    }
                    val checkedNames = dialog.getCheckedRules().toSet()
                    if (checkedNames.isEmpty()) {
                        console.error("No rules selected. Export aborted.\n")
                        processHandler.destroyProcess()
                        return
                    }
                    rules.filter { it.name in checkedNames }
                } else {
                    rules
                }

                performExport(project, selectedRules, processHandler, console)
            }

            override fun onThrowable(error: Throwable) {
                console.error("Failed to fetch rule list: ${error.message}\n")
                processHandler.destroyProcess()
                notifyError(project, "Export failed: ${error.message}")
            }
        }.queue()

        return DefaultExecutionResult(console, processHandler)
    }

    private fun performExport(
        project: Project,
        rules: List<IIQServerUploader.ExistingRule>,
        processHandler: ProcessHandler,
        console: ConsoleView
    ) {
        object : Task.Backgroundable(project, "Exporting rules...", false) {

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                console.info("\nFetching full XML for ${rules.size} rule(s)...\n")

                val ruleXmls = mutableListOf<Pair<String, String>>()

                rules.forEachIndexed { index, rule ->
                    indicator.fraction = (index + 1).toDouble() / rules.size
                    indicator.text = "Fetching '${rule.name}'..."
                    console.info("  Fetching '${rule.name}'...\n")

                    val rawXml = IIQServerUploader.fetchRuleXml(config, rule.id)
                        ?: throw IllegalStateException("Failed to fetch XML for rule '${rule.name}'")

                    val ruleBlock = IIQXmlUtils.extractRuleXml(rawXml) ?: rawXml
                    ruleXmls.add(rule.name to ruleBlock)
                }

                val snapshotsDir = File(project.basePath ?: ".", "snapshots")
                snapshotsDir.mkdirs()

                val timestamp = SimpleDateFormat("yyyyMMdd HHmmss").format(Date())
                val outFile = File(snapshotsDir, "$timestamp snapshot.xml")

                val sb = StringBuilder()
                sb.appendLine("<?xml version='1.0' encoding='UTF-8'?>")
                sb.appendLine("<Snapshots>")
                sb.appendLine("  <Targets>")
                ruleXmls.forEach { (name, _) ->
                    sb.appendLine("    <Name>$name</Name>")
                }
                sb.appendLine("  </Targets>")
                sb.appendLine("  <Sailpoint>")
                ruleXmls.forEach { (_, xml) ->
                    xml.lines().forEach { line ->
                        sb.appendLine("    $line")
                    }
                }
                sb.appendLine("  </Sailpoint>")
                sb.append("</Snapshots>")

                outFile.writeText(sb.toString(), Charsets.UTF_8)
                console.info("\nSnapshot saved: ${outFile.absolutePath}\n")
                console.info("Exported ${ruleXmls.size} rule(s).\n")
            }

            override fun onSuccess() {
                processHandler.destroyProcess()
                notifyInfo(project, "Export complete: ${rules.size} rule(s) saved to snapshots/")
            }

            override fun onThrowable(error: Throwable) {
                console.error("Export failed: ${error.message}\n")
                processHandler.destroyProcess()
                notifyError(project, "Export failed: ${error.message}")
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
