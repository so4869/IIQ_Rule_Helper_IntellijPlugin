package im.flare.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import org.jdom.Element

class IIQRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : LocatableConfigurationBase<RunProfileState>(project, factory, name), IIQServerConnection {

    override var url: String = ""
    override var username: String = ""
    override var password: String = ""
    override var ignoreSslCertificate: Boolean = false
    var basePackages: String = ""
    var templateDirectory: String = ""
    var lastCheckedClasses: String = ""
    var createBackup: Boolean = false

    fun getBasePackageList(): List<String> =
        basePackages.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun getLastCheckedClassSet(): Set<String> =
        lastCheckedClasses.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    override fun readExternal(element: Element) {
        super.readExternal(element)
        url = element.getAttributeValue(ATTR_URL) ?: ""
        username = element.getAttributeValue(ATTR_USERNAME) ?: ""
        password = element.getAttributeValue(ATTR_PASSWORD) ?: ""
        ignoreSslCertificate = element.getAttributeValue(ATTR_IGNORE_SSL)?.toBoolean() ?: false
        basePackages = element.getAttributeValue(ATTR_BASE_PACKAGES) ?: ""
        templateDirectory = element.getAttributeValue(ATTR_TEMPLATE_DIR) ?: ""
        lastCheckedClasses = element.getAttributeValue(ATTR_LAST_CHECKED) ?: ""
        createBackup = element.getAttributeValue(ATTR_CREATE_BACKUP)?.toBoolean() ?: false
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute(ATTR_URL, url)
        element.setAttribute(ATTR_USERNAME, username)
        element.setAttribute(ATTR_PASSWORD, password)
        element.setAttribute(ATTR_IGNORE_SSL, ignoreSslCertificate.toString())
        element.setAttribute(ATTR_BASE_PACKAGES, basePackages)
        element.setAttribute(ATTR_TEMPLATE_DIR, templateDirectory)
        element.setAttribute(ATTR_LAST_CHECKED, lastCheckedClasses)
        element.setAttribute(ATTR_CREATE_BACKUP, createBackup.toString())
    }

    override fun getConfigurationEditor() = IIQRunConfigurationEditor()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        IIQRunState(environment, this)

    override fun checkConfiguration() {
        if (url.isBlank()) throw RuntimeConfigurationError("URL must not be empty.")
        if (username.isBlank()) throw RuntimeConfigurationError("Username must not be empty.")
        if (password.isBlank()) throw RuntimeConfigurationError("Password must not be empty.")
        if (templateDirectory.isBlank()) throw RuntimeConfigurationError("Template directory must not be empty.")
    }

    companion object {
        private const val ATTR_URL = "url"
        private const val ATTR_USERNAME = "username"
        private const val ATTR_PASSWORD = "password"
        private const val ATTR_IGNORE_SSL = "ignoreSslCertificate"
        private const val ATTR_BASE_PACKAGES = "basePackages"
        private const val ATTR_TEMPLATE_DIR = "templateDirectory"
        private const val ATTR_LAST_CHECKED = "lastCheckedClasses"
        private const val ATTR_CREATE_BACKUP = "createBackup"
    }
}
