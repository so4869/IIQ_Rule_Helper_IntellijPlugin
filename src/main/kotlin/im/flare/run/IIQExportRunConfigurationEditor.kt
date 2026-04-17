package im.flare.run

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton

class IIQExportRunConfigurationEditor : SettingsEditor<IIQExportRunConfiguration>() {

    private val urlField = JBTextField()
    private val usernameField = JBTextField()
    private val passwordField = JBPasswordField()
    private val ignoreSslCheckbox = JBCheckBox("Ignore SSL certificate validation (trust all certificates)")

    private val radioAll = JRadioButton("Export All")
    private val radioSearch = JRadioButton("Search by Name")
    private val radioSelect = JRadioButton("Select from List")
    private val searchQueryField = JBTextField()

    init {
        ButtonGroup().apply {
            add(radioAll)
            add(radioSearch)
            add(radioSelect)
        }
        radioAll.isSelected = true
        searchQueryField.isEnabled = false

        val updateSearchField = {
            searchQueryField.isEnabled = radioSearch.isSelected
        }
        radioAll.addActionListener { updateSearchField() }
        radioSearch.addActionListener { updateSearchField() }
        radioSelect.addActionListener { updateSearchField() }
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

        val exportModeNote = ComponentPanelBuilder.createCommentComponent(
            "'Export All' fetches every rule from the server. " +
                "'Search by Name' uses the query below. " +
                "'Select from List' fetches all rules and shows a selection popup at runtime.",
            true
        )

        val exportModePanel = FormBuilder.createFormBuilder()
            .addComponent(radioAll)
            .addComponent(radioSearch)
            .addLabeledComponent("  Search Query:", searchQueryField, true)
            .addComponent(radioSelect)
            .addComponent(exportModeNote)
            .panel
            .apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("Export Mode"),
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
            .addComponent(exportModePanel)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .apply { border = JBUI.Borders.empty(8) }
    }

    override fun resetEditorFrom(config: IIQExportRunConfiguration) {
        urlField.text = config.url
        usernameField.text = config.username
        passwordField.text = config.password
        ignoreSslCheckbox.isSelected = config.ignoreSslCertificate
        searchQueryField.text = config.searchQuery
        when (config.exportMode) {
            ExportMode.ALL -> radioAll.isSelected = true
            ExportMode.SEARCH -> radioSearch.isSelected = true
            ExportMode.SELECT -> radioSelect.isSelected = true
        }
        searchQueryField.isEnabled = config.exportMode == ExportMode.SEARCH
    }

    override fun applyEditorTo(config: IIQExportRunConfiguration) {
        config.url = urlField.text.trim()
        config.username = usernameField.text.trim()
        config.password = String(passwordField.password)
        config.ignoreSslCertificate = ignoreSslCheckbox.isSelected
        config.searchQuery = searchQueryField.text.trim()
        config.exportMode = when {
            radioSearch.isSelected -> ExportMode.SEARCH
            radioSelect.isSelected -> ExportMode.SELECT
            else -> ExportMode.ALL
        }
    }
}
