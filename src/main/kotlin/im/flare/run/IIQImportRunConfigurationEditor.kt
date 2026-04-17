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

class IIQImportRunConfigurationEditor : SettingsEditor<IIQImportRunConfiguration>() {

    private val urlField = JBTextField()
    private val usernameField = JBTextField()
    private val passwordField = JBPasswordField()
    private val ignoreSslCheckbox = JBCheckBox("Ignore SSL certificate validation (trust all certificates)")
    private val snapshotFileField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select Snapshot File",
            "Choose the snapshot XML file to import",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor("xml")
        )
    }

    override fun createEditor(): JComponent {
        val authLabel = JLabel("Authentication: Basic Auth").apply {
            font = font.deriveFont(Font.BOLD)
            foreground = JBUI.CurrentTheme.Label.foreground()
        }

        val connectionPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("URL:", urlField, true)
            .panel
            .apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("Connection"),
                    JBUI.Borders.empty(4, 8)
                )
            }

        val authPanel = FormBuilder.createFormBuilder()
            .addComponent(authLabel)
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

        val snapshotNote = ComponentPanelBuilder.createCommentComponent(
            "Select a snapshot XML file previously exported by IIQ Export Snapshot. " +
                "All rules inside <Sailpoint> will be uploaded to the server.",
            true
        )

        val snapshotPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Snapshot File:", snapshotFileField, true)
            .addComponent(snapshotNote)
            .panel
            .apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("Snapshot"),
                    JBUI.Borders.empty(4, 8)
                )
            }

        return FormBuilder.createFormBuilder()
            .addComponent(connectionPanel)
            .addVerticalGap(8)
            .addComponent(authPanel)
            .addVerticalGap(8)
            .addComponent(sslPanel)
            .addVerticalGap(8)
            .addComponent(snapshotPanel)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .apply { border = JBUI.Borders.empty(8) }
    }

    override fun resetEditorFrom(config: IIQImportRunConfiguration) {
        urlField.text = config.url
        usernameField.text = config.username
        passwordField.text = config.password
        ignoreSslCheckbox.isSelected = config.ignoreSslCertificate
        snapshotFileField.text = config.snapshotFile
    }

    override fun applyEditorTo(config: IIQImportRunConfiguration) {
        config.url = urlField.text.trim()
        config.username = usernameField.text.trim()
        config.password = String(passwordField.password)
        config.ignoreSslCertificate = ignoreSslCheckbox.isSelected
        config.snapshotFile = snapshotFileField.text.trim()
    }
}
