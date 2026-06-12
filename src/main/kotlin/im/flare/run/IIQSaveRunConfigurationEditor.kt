package im.flare.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

class IIQSaveRunConfigurationEditor : SettingsEditor<IIQSaveRunConfiguration>() {

    private val basePackagesField = JBTextField()
    private val templateDirField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select Template Directory",
            "Choose the directory containing IIQ rule template files (e.g. ObjectCustomizationRule.template.xml)",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
    }
    private val outputDirField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select Output Directory",
            "Choose the directory where rule XML files will be saved",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
    }
    private val mirrorPackageCheckbox =
        JBCheckBox("Mirror package structure (create subdirectories matching the Java package)")

    override fun createEditor(): JComponent {
        val scanPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Base Packages:", basePackagesField, true)
            .addComponent(
                ComponentPanelBuilder.createCommentComponent(
                    "Comma-separated list of base packages to scan (e.g. com.example.rules, com.example.tasks).",
                    true
                )
            )
            .panel
            .apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("Source Filter"),
                    JBUI.Borders.empty(4, 8)
                )
            }

        val templatePanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Template Directory:", templateDirField, true)
            .addComponent(
                ComponentPanelBuilder.createCommentComponent(
                    "Optional. Directory containing {type}.template.xml files. If not set, a built-in default template is used.",
                    true
                )
            )
            .panel
            .apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("Templates"),
                    JBUI.Borders.empty(4, 8)
                )
            }

        val outputPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Output Directory:", outputDirField, true)
            .addComponent(
                ComponentPanelBuilder.createCommentComponent(
                    "Directory where rule XML files will be written. File names are {ruleName}.xml.",
                    true
                )
            )
            .panel
            .apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("Output"),
                    JBUI.Borders.empty(4, 8)
                )
            }

        val layoutPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("File Layout"),
                JBUI.Borders.empty(4, 8)
            )
            add(mirrorPackageCheckbox, BorderLayout.NORTH)
            add(
                ComponentPanelBuilder.createCommentComponent(
                    "When checked, files are placed under subdirectories matching the Java package " +
                        "(e.g. com/example/rules/MyRuleName.xml). " +
                        "When unchecked, all files are saved directly in the output directory.",
                    true
                ),
                BorderLayout.CENTER
            )
        }

        return FormBuilder.createFormBuilder()
            .addComponent(scanPanel)
            .addVerticalGap(8)
            .addComponent(templatePanel)
            .addVerticalGap(8)
            .addComponent(outputPanel)
            .addVerticalGap(8)
            .addComponent(layoutPanel)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .apply { border = JBUI.Borders.empty(8) }
    }

    override fun resetEditorFrom(config: IIQSaveRunConfiguration) {
        basePackagesField.text = config.basePackages
        templateDirField.text = config.templateDirectory
        outputDirField.text = config.outputDirectory
        mirrorPackageCheckbox.isSelected = config.mirrorPackageStructure
    }

    override fun applyEditorTo(config: IIQSaveRunConfiguration) {
        config.basePackages = basePackagesField.text.trim()
        config.templateDirectory = templateDirField.text.trim()
        config.outputDirectory = outputDirField.text.trim()
        config.mirrorPackageStructure = mirrorPackageCheckbox.isSelected
    }
}
