package im.flare.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.icons.AllIcons
import javax.swing.Icon

class IIQRuleHelperConfigurationType : ConfigurationType {

    override fun getDisplayName(): String = "IIQ Rule Helper"

    override fun getConfigurationTypeDescription(): String =
        "Run configurations for IdentityIQ rule development"

    override fun getIcon(): Icon = AllIcons.Nodes.Plugin

    override fun getId(): String = "IIQ_RULE_HELPER"

    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(
        IIQRunConfigurationFactory(this),
        IIQSaveRunConfigurationFactory(this),
        IIQExportRunConfigurationFactory(this),
        IIQImportRunConfigurationFactory(this)
    )
}
