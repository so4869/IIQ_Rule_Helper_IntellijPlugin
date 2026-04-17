package im.flare.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.icons.AllIcons
import javax.swing.Icon

class IIQImportRunConfigurationType : ConfigurationType {

    override fun getDisplayName(): String = "IIQ Import Snapshot"

    override fun getConfigurationTypeDescription(): String =
        "Import IIQ rules from a snapshot file to the server"

    override fun getIcon(): Icon = AllIcons.Actions.Upload

    override fun getId(): String = "IIQ_IMPORT_SNAPSHOT"

    override fun getConfigurationFactories(): Array<ConfigurationFactory> =
        arrayOf(IIQImportRunConfigurationFactory(this))
}
