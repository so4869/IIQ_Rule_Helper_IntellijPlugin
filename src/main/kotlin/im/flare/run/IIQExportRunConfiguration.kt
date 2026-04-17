package im.flare.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import org.jdom.Element

enum class ExportMode { ALL, SEARCH, SELECT }

class IIQExportRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : LocatableConfigurationBase<RunProfileState>(project, factory, name), IIQServerConnection {

    override var url: String = ""
    override var username: String = ""
    override var password: String = ""
    override var ignoreSslCertificate: Boolean = false
    var exportMode: ExportMode = ExportMode.ALL
    var searchQuery: String = ""

    override fun readExternal(element: Element) {
        super.readExternal(element)
        url = element.getAttributeValue(ATTR_URL) ?: ""
        username = element.getAttributeValue(ATTR_USERNAME) ?: ""
        password = element.getAttributeValue(ATTR_PASSWORD) ?: ""
        ignoreSslCertificate = element.getAttributeValue(ATTR_IGNORE_SSL)?.toBoolean() ?: false
        exportMode = element.getAttributeValue(ATTR_EXPORT_MODE)
            ?.let { runCatching { ExportMode.valueOf(it) }.getOrNull() } ?: ExportMode.ALL
        searchQuery = element.getAttributeValue(ATTR_SEARCH_QUERY) ?: ""
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute(ATTR_URL, url)
        element.setAttribute(ATTR_USERNAME, username)
        element.setAttribute(ATTR_PASSWORD, password)
        element.setAttribute(ATTR_IGNORE_SSL, ignoreSslCertificate.toString())
        element.setAttribute(ATTR_EXPORT_MODE, exportMode.name)
        element.setAttribute(ATTR_SEARCH_QUERY, searchQuery)
    }

    override fun getConfigurationEditor() = IIQExportRunConfigurationEditor()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        IIQExportRunState(environment, this)

    override fun checkConfiguration() {
        if (url.isBlank()) throw RuntimeConfigurationError("URL must not be empty.")
        if (username.isBlank()) throw RuntimeConfigurationError("Username must not be empty.")
        if (password.isBlank()) throw RuntimeConfigurationError("Password must not be empty.")
        if (exportMode == ExportMode.SEARCH && searchQuery.isBlank())
            throw RuntimeConfigurationError("Search query must not be empty when Export Mode is 'Search by Name'.")
    }

    companion object {
        private const val ATTR_URL = "url"
        private const val ATTR_USERNAME = "username"
        private const val ATTR_PASSWORD = "password"
        private const val ATTR_IGNORE_SSL = "ignoreSslCertificate"
        private const val ATTR_EXPORT_MODE = "exportMode"
        private const val ATTR_SEARCH_QUERY = "searchQuery"
    }
}
