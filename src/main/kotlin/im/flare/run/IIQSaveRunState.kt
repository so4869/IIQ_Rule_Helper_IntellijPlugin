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
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.io.File
import java.io.OutputStream

class IIQSaveRunState(
    private val environment: ExecutionEnvironment,
    private val config: IIQSaveRunConfiguration
) : RunProfileState {

    override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult {
        val project = environment.project
        val processHandler = createProcessHandler()
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        console.attachToProcess(processHandler)
        processHandler.startNotify()

        object : Task.Backgroundable(project, "Scanning IIQ classes...", false) {
            private var classInfoMap: Map<String, IIQClassInfo> = emptyMap()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                console.info("Scanning classes in packages: ${config.getBasePackageList().joinToString()}\n")
                classInfoMap = ReadAction.compute<Map<String, IIQClassInfo>, Throwable> {
                    IIQClassScanner.findEligibleClasses(project, config.getBasePackageList())
                        .mapNotNull { IIQSourceExtractor.extract(it) }
                        .associateBy { it.qualifiedName }
                }
                console.info("Found ${classInfoMap.size} eligible class(es).\n")
            }

            override fun onSuccess() {
                if (classInfoMap.isEmpty()) {
                    console.error("No eligible classes found.\n")
                    processHandler.destroyProcess()
                    return
                }

                val sortedEntries = classInfoMap.entries
                    .sortedBy { it.key }
                    .map { (fqn, info) -> fqn to info.name }
                val dialog = IIQUploadDialog(
                    project, sortedEntries, config.getLastCheckedClassSet(),
                    showBackup = false
                )

                if (!dialog.showAndGet()) {
                    processHandler.destroyProcess()
                    return
                }

                val checked = dialog.getCheckedClasses()
                if (checked.isEmpty()) {
                    console.error("No classes selected.\n")
                    processHandler.destroyProcess()
                    return
                }
                config.lastCheckedClasses = checked.joinToString(",")

                val toSave = checked.mapNotNull { classInfoMap[it] }
                performSave(project, toSave, processHandler, console)
            }

            override fun onThrowable(error: Throwable) {
                console.error("Scan failed: ${error.message}\n")
                processHandler.destroyProcess()
                notifyError(project, "Scan failed: ${error.message}")
            }
        }.queue()

        return DefaultExecutionResult(console, processHandler)
    }

    private fun performSave(
        project: Project,
        classes: List<IIQClassInfo>,
        processHandler: ProcessHandler,
        console: ConsoleView
    ) {
        object : Task.Backgroundable(project, "Saving rule XML files...", false) {
            private var successCount = 0
            private val errors = mutableListOf<Pair<String, String>>()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                val outputDir = File(config.outputDirectory)

                classes.forEachIndexed { index, info ->
                    indicator.fraction = (index + 1).toDouble() / classes.size
                    indicator.text = "Saving ${info.name ?: info.qualifiedName}..."
                    runCatching { saveClass(info, outputDir, console) }
                        .onSuccess { successCount++ }
                        .onFailure { e ->
                            val msg = e.message ?: "Unknown error"
                            errors.add(info.qualifiedName to msg)
                            console.error("  ✗ ${info.qualifiedName}: $msg\n")
                        }
                }
            }

            override fun onSuccess() {
                processHandler.destroyProcess()
                if (errors.isEmpty()) {
                    console.info("\nSave complete: $successCount file(s) written.\n")
                    notifyInfo(project, "Save complete: $successCount rule XML file(s) written.")
                } else {
                    console.error("\n$successCount saved, ${errors.size} failed.\n")
                    notifyError(project, "$successCount saved, ${errors.size} failed. See Run console for details.")
                }
            }

            override fun onThrowable(error: Throwable) {
                console.error("Save failed: ${error.message}\n")
                processHandler.destroyProcess()
                notifyError(project, "Save failed: ${error.message}")
            }
        }.queue()
    }

    private fun saveClass(info: IIQClassInfo, outputDir: File, console: ConsoleView) {
        val ruleName = info.name
            ?: throw IllegalStateException("${info.qualifiedName} — 'name' field not found")
        val type = info.type
            ?: throw IllegalStateException("${info.qualifiedName} — 'type' field not found")

        val templateText = IIQTemplateProcessor.readTemplateText(config.templateDirectory, type)
        val xml = IIQTemplateProcessor.process(templateText = templateText, info = info)

        val targetDir = if (config.mirrorPackageStructure) {
            val pkg = info.qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")
            if (pkg.isNotEmpty()) File(outputDir, pkg.replace('.', '/')) else outputDir
        } else {
            outputDir
        }
        targetDir.mkdirs()

        val outFile = File(targetDir, "$ruleName.xml")
        outFile.writeText(xml, Charsets.UTF_8)
        console.info("  [Saved] $ruleName → ${outFile.absolutePath}\n")
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
