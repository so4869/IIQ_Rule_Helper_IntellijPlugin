package im.flare.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.icons.AllIcons
import javax.swing.Icon

class IIQExportRunConfigurationType : ConfigurationType {

    override fun getDisplayName(): String = "IIQ Export Snapshot"

    override fun getConfigurationTypeDescription(): String =
        "Export IIQ rules from server as a snapshot file"

    override fun getIcon(): Icon = AllIcons.Actions.Download

    override fun getId(): String = "IIQ_EXPORT_SNAPSHOT"

    override fun getConfigurationFactories(): Array<ConfigurationFactory> =
        arrayOf(IIQExportRunConfigurationFactory(this))
}
