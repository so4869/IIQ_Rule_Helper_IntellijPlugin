package im.flare.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.project.Project

class IIQExportRunConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {

    override fun getId(): String = "IIQ_EXPORT_SNAPSHOT_FACTORY"

    override fun createTemplateConfiguration(project: Project) =
        IIQExportRunConfiguration(project, this, "IIQ Export Snapshot")
}
