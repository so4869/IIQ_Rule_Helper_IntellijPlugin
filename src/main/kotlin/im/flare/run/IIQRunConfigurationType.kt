package im.flare.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.icons.AllIcons
import javax.swing.Icon

class IIQRunConfigurationType : ConfigurationType {

    override fun getDisplayName(): String = "IIQ Rule Import"

    override fun getConfigurationTypeDescription(): String =
        "Run configuration for importing rules into IdentityIQ"

    override fun getIcon(): Icon = AllIcons.Actions.Upload

    override fun getId(): String = "IIQ_RULE_IMPORT"

    override fun getConfigurationFactories(): Array<ConfigurationFactory> =
        arrayOf(IIQRunConfigurationFactory(this))
}
