package im.flare.run

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableRowSorter

class IIQExportSelectDialog(
    project: Project,
    ruleNames: List<String>,
    preChecked: Set<String> = emptySet(),
    dialogTitle: String = "IIQ Export Snapshot",
    okButtonText: String = "Export",
    private val headerText: String = "Select rules to export from the IIQ server."
) : DialogWrapper(project) {

    private val tableModel = RuleTableModel(
        ruleNames.map { (it in preChecked) to it }.toMutableList()
    )
    private val sorter = TableRowSorter(tableModel)
    private val table = JBTable(tableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        rowSorter = sorter
        columnModel.getColumn(0).apply {
            maxWidth = 32
            minWidth = 32
            headerValue = ""
        }
        columnModel.getColumn(1).headerValue = "Rule Name"
        tableHeader.reorderingAllowed = false
        rowHeight = rowHeight + 2
    }

    private val searchField = SearchTextField(false).apply {
        toolTipText = "Filter rules"
        addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = applyFilter()
            override fun removeUpdate(e: DocumentEvent) = applyFilter()
            override fun changedUpdate(e: DocumentEvent) = applyFilter()
        })
    }

    init {
        title = dialogTitle
        setOKButtonText(okButtonText)
        init()
    }

    private fun applyFilter() {
        val text = searchField.text.trim()
        sorter.rowFilter = if (text.isBlank()) null
        else RowFilter.regexFilter("(?i)${Regex.escape(text)}", 1)
    }

    override fun createCenterPanel(): JComponent {
        val header = JLabel(headerText).apply {
            border = JBUI.Borders.emptyBottom(8)
        }

        val searchPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyBottom(6)
            add(searchField, BorderLayout.CENTER)
        }

        val topPanel = JPanel(BorderLayout()).apply {
            add(header, BorderLayout.NORTH)
            add(searchPanel, BorderLayout.CENTER)
        }

        val scrollPane = JBScrollPane(table).apply {
            preferredSize = Dimension(500, 400)
        }

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(topPanel, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }
    }

    override fun getPreferredFocusedComponent() = searchField

    fun getCheckedRules(): List<String> = tableModel.getCheckedRules()
}

private class RuleTableModel(
    private val entries: MutableList<Pair<Boolean, String>>
) : AbstractTableModel() {

    override fun getRowCount(): Int = entries.size
    override fun getColumnCount(): Int = 2

    override fun getColumnName(column: Int): String = when (column) {
        1 -> "Rule Name"
        else -> ""
    }

    override fun getColumnClass(column: Int): Class<*> = when (column) {
        0 -> java.lang.Boolean::class.java
        else -> String::class.java
    }

    override fun isCellEditable(row: Int, column: Int): Boolean = column == 0

    override fun getValueAt(row: Int, column: Int): Any = when (column) {
        0 -> entries[row].first
        else -> entries[row].second
    }

    override fun setValueAt(value: Any?, row: Int, column: Int) {
        if (column == 0 && value is Boolean) {
            entries[row] = entries[row].copy(first = value)
            fireTableCellUpdated(row, column)
        }
    }

    fun getCheckedRules(): List<String> =
        entries.filter { it.first }.map { it.second }
}
