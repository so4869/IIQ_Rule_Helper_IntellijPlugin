package im.flare.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class IIQRunConfigurationEditor : SettingsEditor<IIQRunConfiguration>() {

    private val urlField = JBTextField()
    private val usernameField = JBTextField()
    private val passwordField = JBPasswordField()
    private val ignoreSslCheckbox = JBCheckBox("Ignore SSL certificate validation (trust all certificates)")
    private val basePackagesField = JBTextField()
    private val templateDirField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select Template Directory",
            "Choose the directory containing IIQ rule template files (e.g. ObjectCustomizationRule.template.xml)",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
    }

    override fun createEditor(): JComponent {
        val authLabel = JLabel("Authentication: Basic Auth").apply {
            font = font.deriveFont(Font.BOLD)
            foreground = JBUI.CurrentTheme.Label.foreground()
        }

        val authNote = ComponentPanelBuilder.createCommentComponent(
            "Credentials are used for HTTP Basic Authentication when connecting to the IIQ server.",
            true
        )

        val basePackagesNote = ComponentPanelBuilder.createCommentComponent(
            "Comma-separated list of base packages to filter source files (e.g. com.example.rules, com.example.tasks).",
            true
        )

        val connectionPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("URL:", urlField, true)
            .panel
            .apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("Connection"),
                    JBUI.Borders.empty(4, 8)
                )
            }

        val templateDirNote = ComponentPanelBuilder.createCommentComponent(
            "Directory containing template files named {type}.template.xml (e.g. ObjectCustomizationRule.template.xml).",
            true
        )

        val basePackagesPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Base Packages:", basePackagesField, true)
            .addComponent(basePackagesNote)
            .panel
            .apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("Source Filter"),
                    JBUI.Borders.empty(4, 8)
                )
            }

        val templateDirPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Template Directory:", templateDirField, true)
            .addComponent(templateDirNote)
            .panel
            .apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("Templates"),
                    JBUI.Borders.empty(4, 8)
                )
            }

        val authPanel = FormBuilder.createFormBuilder()
            .addComponent(authLabel)
            .addComponent(authNote)
            .addVerticalGap(4)
            .addLabeledComponent("Username:", usernameField, true)
            .addLabeledComponent("Password:", passwordField, true)
            .panel
            .apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("Credentials"),
                    JBUI.Borders.empty(4, 8)
                )
            }

        val sslPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("SSL / TLS"),
                JBUI.Borders.empty(4, 8)
            )
            add(ignoreSslCheckbox, BorderLayout.NORTH)
            add(
                ComponentPanelBuilder.createCommentComponent(
                    "When checked, the SSL certificate of the IIQ server will not be verified. " +
                        "Use only in development or test environments.",
                    true
                ),
                BorderLayout.CENTER
            )
        }

        return FormBuilder.createFormBuilder()
            .addComponent(connectionPanel)
            .addVerticalGap(8)
            .addComponent(authPanel)
            .addVerticalGap(8)
            .addComponent(sslPanel)
            .addVerticalGap(8)
            .addComponent(basePackagesPanel)
            .addVerticalGap(8)
            .addComponent(templateDirPanel)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .apply { border = JBUI.Borders.empty(8) }
    }

    override fun resetEditorFrom(config: IIQRunConfiguration) {
        urlField.text = config.url
        usernameField.text = config.username
        passwordField.text = config.password
        ignoreSslCheckbox.isSelected = config.ignoreSslCertificate
        basePackagesField.text = config.basePackages
        templateDirField.text = config.templateDirectory
    }

    override fun applyEditorTo(config: IIQRunConfiguration) {
        config.url = urlField.text.trim()
        config.username = usernameField.text.trim()
        config.password = String(passwordField.password)
        config.ignoreSslCertificate = ignoreSslCheckbox.isSelected
        config.basePackages = basePackagesField.text.trim()
        config.templateDirectory = templateDirField.text.trim()
    }
}
