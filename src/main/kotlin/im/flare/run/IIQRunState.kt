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
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date

class IIQRunState(
    private val environment: ExecutionEnvironment,
    private val config: IIQRunConfiguration
) : RunProfileState {

    override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult {
        val project = environment.project
        val processHandler = createProcessHandler()

        val console = TextConsoleBuilderFactory.getInstance()
            .createBuilder(project)
            .console
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
                val sortedEntries = classInfoMap.entries
                    .sortedBy { it.key }
                    .map { (fqn, info) -> fqn to info.name }
                val dialog = IIQUploadDialog(project, sortedEntries, config.getLastCheckedClassSet(), config.createBackup)

                if (!dialog.showAndGet()) {
                    processHandler.destroyProcess()
                    return
                }

                val checked = dialog.getCheckedClasses()
                config.lastCheckedClasses = checked.joinToString(",")
                config.createBackup = dialog.isCreateBackup()

                val toUpload = checked.mapNotNull { classInfoMap[it] }
                performUpload(project, toUpload, dialog.isCreateBackup(), processHandler, console)
            }

            override fun onThrowable(error: Throwable) {
                console.error("Scan failed: ${error.message}\n")
                processHandler.destroyProcess()
                notifyError(project, "Scan failed: ${error.message}")
            }
        }.queue()

        return DefaultExecutionResult(console, processHandler)
    }

    private fun validateTemplates(classes: List<IIQClassInfo>): List<String> {
        val missing = mutableListOf<String>()
        for (info in classes) {
            if (info.name == null) {
                missing.add("${info.qualifiedName} — 'name' field not found")
            }
            val type = info.type
            if (type == null) {
                missing.add("${info.qualifiedName} — 'type' field not found")
                continue
            }
            val templateFile = IIQTemplateProcessor.resolveTemplateFile(config.templateDirectory, type)
            if (!templateFile.exists()) {
                missing.add("${info.qualifiedName} — template not found: ${templateFile.name}")
            }
        }
        return missing
    }

    private fun performUpload(
        project: Project,
        classes: List<IIQClassInfo>,
        createBackup: Boolean,
        processHandler: ProcessHandler,
        console: ConsoleView
    ) {
        object : Task.Backgroundable(project, "Uploading to IIQ server...", false) {
            private val errors = mutableListOf<Pair<String, String>>()
            private var successCount = 0

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false

                val missingTemplates = validateTemplates(classes)
                if (missingTemplates.isNotEmpty()) {
                    val detail = missingTemplates.joinToString("\n") { "  • $it" }
                    throw IllegalStateException("Upload aborted. Missing templates:\n$detail")
                }

                classes.forEachIndexed { index, info ->
                    indicator.fraction = (index + 1).toDouble() / classes.size
                    indicator.text = "Uploading ${info.qualifiedName}..."
                    runCatching { uploadClass(info, createBackup, console) }
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
                    console.info("\nUpload complete: $successCount class(es) uploaded successfully.\n")
                    console.info("\n없었으면 힘들었을거야. 오늘 반영 많이될거야. 스트롱! 스트롱!!\n")
                    notifyInfo(project, "Upload complete: $successCount class(es) uploaded successfully.")
                } else {
                    console.error("\n$successCount uploaded, ${errors.size} failed.\n")
                    notifyError(project, "$successCount uploaded, ${errors.size} failed. See Run console for details.")
                }
            }

            override fun onThrowable(error: Throwable) {
                console.error("Upload failed: ${error.message}\n")
                processHandler.destroyProcess()
                notifyError(project, "Upload failed: ${error.message}")
            }
        }.queue()
    }

    private fun uploadClass(info: IIQClassInfo, createBackup: Boolean, console: ConsoleView) {
        val type = info.type!! // guaranteed non-null by validateTemplates
        val templateFile = IIQTemplateProcessor.resolveTemplateFile(config.templateDirectory, type)

        val existing = if (info.name != null) IIQServerUploader.findExisting(config, info.name) else null

        if (createBackup && existing != null && info.name != null) {
            val serverXml = IIQServerUploader.fetchRuleXml(config, existing.id)
            if (serverXml != null) {
                val timestamp = SimpleDateFormat("yyyyMMddHHmmss").format(Date())
                val backupXml = IIQXmlUtils.buildBackupXml(serverXml, "${info.name}-$timestamp")
                val backupRuleTag = Regex("<Rule\\b[^>]*>").find(backupXml)?.value ?: "<Rule>"
                val backupResult = IIQServerUploader.upload(config, backupXml, null)
                backupResult.response.use { response ->
                    console.info("  [Backup] $backupRuleTag → POST ${backupResult.endpoint}\n")
                    if (!response.isSuccessful) {
                        throw IOException("Backup failed (HTTP ${response.code}): ${response.body?.string()?.take(200)}")
                    }
                }
            }
        }

        val xml = IIQTemplateProcessor.process(
            templateFile = templateFile,
            info = info,
            existingId = existing?.id,
            createdMs = existing?.createdMs,
            modifiedMs = existing?.modifiedMs
        )

        val ruleTag = Regex("<Rule\\b[^>]*>").find(xml)?.value ?: "<Rule>"
        val result = IIQServerUploader.upload(config, xml, existing?.id)
        result.response.use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.body?.string()?.take(200)}")
            }
        }
        console.info("  [Upload] $ruleTag → POST ${result.endpoint}\n")
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
