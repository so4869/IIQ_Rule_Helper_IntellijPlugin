package im.flare.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import org.jdom.Element

class IIQSaveRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : LocatableConfigurationBase<RunProfileState>(project, factory, name) {

    var basePackages: String = ""
    var templateDirectory: String = ""
    var outputDirectory: String = ""
    var mirrorPackageStructure: Boolean = false
    var lastCheckedClasses: String = ""

    fun getBasePackageList(): List<String> =
        basePackages.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun getLastCheckedClassSet(): Set<String> =
        lastCheckedClasses.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    override fun readExternal(element: Element) {
        super.readExternal(element)
        basePackages = element.getAttributeValue(ATTR_BASE_PACKAGES) ?: ""
        templateDirectory = element.getAttributeValue(ATTR_TEMPLATE_DIR) ?: ""
        outputDirectory = element.getAttributeValue(ATTR_OUTPUT_DIR) ?: ""
        mirrorPackageStructure = element.getAttributeValue(ATTR_MIRROR_PKG)?.toBoolean() ?: false
        lastCheckedClasses = element.getAttributeValue(ATTR_LAST_CHECKED) ?: ""
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute(ATTR_BASE_PACKAGES, basePackages)
        element.setAttribute(ATTR_TEMPLATE_DIR, templateDirectory)
        element.setAttribute(ATTR_OUTPUT_DIR, outputDirectory)
        element.setAttribute(ATTR_MIRROR_PKG, mirrorPackageStructure.toString())
        element.setAttribute(ATTR_LAST_CHECKED, lastCheckedClasses)
    }

    override fun getConfigurationEditor() = IIQSaveRunConfigurationEditor()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        IIQSaveRunState(environment, this)

    override fun checkConfiguration() {
        if (basePackages.isBlank()) throw RuntimeConfigurationError("Base packages must not be empty.")
        if (outputDirectory.isBlank()) throw RuntimeConfigurationError("Output directory must not be empty.")
    }

    companion object {
        private const val ATTR_BASE_PACKAGES = "basePackages"
        private const val ATTR_TEMPLATE_DIR = "templateDirectory"
        private const val ATTR_OUTPUT_DIR = "outputDirectory"
        private const val ATTR_MIRROR_PKG = "mirrorPackageStructure"
        private const val ATTR_LAST_CHECKED = "lastCheckedClasses"
    }
}
