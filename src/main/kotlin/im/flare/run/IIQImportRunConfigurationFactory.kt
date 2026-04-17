package im.flare.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.project.Project

class IIQImportRunConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {

    override fun getId(): String = "IIQ_IMPORT_SNAPSHOT_FACTORY"

    override fun createTemplateConfiguration(project: Project) =
        IIQImportRunConfiguration(project, this, "IIQ Import Snapshot")
}
