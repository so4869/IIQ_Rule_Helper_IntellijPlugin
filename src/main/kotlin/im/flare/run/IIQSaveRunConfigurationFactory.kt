package im.flare.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.project.Project

class IIQSaveRunConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {

    override fun getId(): String = "IIQ_RULE_SAVE_FACTORY"

    override fun getName(): String = "IIQ Rule Save"

    override fun createTemplateConfiguration(project: Project) =
        IIQSaveRunConfiguration(project, this, "IIQ Rule Save")
}
