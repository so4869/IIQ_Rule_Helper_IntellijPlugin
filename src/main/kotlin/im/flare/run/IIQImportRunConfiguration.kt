package im.flare.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import org.jdom.Element

class IIQImportRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : LocatableConfigurationBase<RunProfileState>(project, factory, name), IIQServerConnection {

    override var url: String = ""
    override var username: String = ""
    override var password: String = ""
    override var ignoreSslCertificate: Boolean = false
    var snapshotFile: String = ""

    override fun readExternal(element: Element) {
        super.readExternal(element)
        url = element.getAttributeValue(ATTR_URL) ?: ""
        username = element.getAttributeValue(ATTR_USERNAME) ?: ""
        password = element.getAttributeValue(ATTR_PASSWORD) ?: ""
        ignoreSslCertificate = element.getAttributeValue(ATTR_IGNORE_SSL)?.toBoolean() ?: false
        snapshotFile = element.getAttributeValue(ATTR_SNAPSHOT_FILE) ?: ""
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute(ATTR_URL, url)
        element.setAttribute(ATTR_USERNAME, username)
        element.setAttribute(ATTR_PASSWORD, password)
        element.setAttribute(ATTR_IGNORE_SSL, ignoreSslCertificate.toString())
        element.setAttribute(ATTR_SNAPSHOT_FILE, snapshotFile)
    }

    override fun getConfigurationEditor() = IIQImportRunConfigurationEditor()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        IIQImportRunState(environment, this)

    override fun checkConfiguration() {
        if (url.isBlank()) throw RuntimeConfigurationError("URL must not be empty.")
        if (username.isBlank()) throw RuntimeConfigurationError("Username must not be empty.")
        if (password.isBlank()) throw RuntimeConfigurationError("Password must not be empty.")
        if (snapshotFile.isBlank()) throw RuntimeConfigurationError("Snapshot file must not be empty.")
    }

    companion object {
        private const val ATTR_URL = "url"
        private const val ATTR_USERNAME = "username"
        private const val ATTR_PASSWORD = "password"
        private const val ATTR_IGNORE_SSL = "ignoreSslCertificate"
        private const val ATTR_SNAPSHOT_FILE = "snapshotFile"
    }
}
