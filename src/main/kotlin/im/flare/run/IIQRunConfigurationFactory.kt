package im.flare.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.project.Project

class IIQRunConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {

    override fun getId(): String = "IIQ_RULE_IMPORT_FACTORY"

    override fun createTemplateConfiguration(project: Project) =
        IIQRunConfiguration(project, this, "IIQ Rule Import")
}
