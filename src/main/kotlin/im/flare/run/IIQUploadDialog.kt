package im.flare.run

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableRowSorter

class IIQUploadDialog(
    project: Project,
    classes: List<Pair<String, String?>>,   // fqn to rule name
    lastChecked: Set<String>,
    lastCreateBackup: Boolean = false
) : DialogWrapper(project) {

    private val tableModel = ClassTableModel(
        classes.map { (fqn, ruleName) -> ClassEntry(fqn in lastChecked, fqn, ruleName) }.toMutableList()
    )
    private val sorter = TableRowSorter(tableModel)

    private val headerCheckbox = JCheckBox().apply { isSelected = false }

    private val table = JBTable(tableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        rowSorter = sorter

        columnModel.getColumn(0).apply {
            maxWidth = 32
            minWidth = 32
            headerRenderer = TableCellRenderer { _, _, _, _, _, _ ->
                val count = tableModel.getCheckedClasses().size
                headerCheckbox.isSelected = count > 0 && count == tableModel.rowCount
                headerCheckbox
            }
        }
        columnModel.getColumn(1).headerValue = "Class"
        columnModel.getColumn(2).headerValue = "Name"

        tableHeader.reorderingAllowed = false
        rowHeight = rowHeight + 2

        tableHeader.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (columnAtPoint(e.point) == 0) {
                    val newState = tableModel.getCheckedClasses().size < tableModel.rowCount
                    tableModel.setAllChecked(newState)
                    tableHeader.repaint()
                    repaint()
                }
            }
        })
    }

    private val searchField = SearchTextField(false).apply {
        toolTipText = "Filter classes"
        addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = applyFilter()
            override fun removeUpdate(e: DocumentEvent) = applyFilter()
            override fun changedUpdate(e: DocumentEvent) = applyFilter()
        })
    }

    private val createBackupCheckbox = JBCheckBox(
        "Create Backup  (saves current server version as {name}-yyyyMMddHHmmss before uploading)"
    ).apply {
        isSelected = lastCreateBackup
    }

    init {
        title = "IIQ Rule Import"
        setOKButtonText("Confirm")
        init()
    }

    private fun applyFilter() {
        val text = searchField.text.trim()
        sorter.rowFilter = if (text.isBlank()) null
        else RowFilter.regexFilter("(?i)${Regex.escape(text)}", 1, 2)
    }

    override fun createCenterPanel(): JComponent {
        val header = JLabel("Select classes to upload to the IIQ server.").apply {
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
            preferredSize = Dimension(700, 400)
        }

        val bottomPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(8)
            add(createBackupCheckbox, BorderLayout.NORTH)
        }

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(topPanel, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
            add(bottomPanel, BorderLayout.SOUTH)
        }
    }

    override fun getPreferredFocusedComponent() = searchField

    fun getCheckedClasses(): List<String> = tableModel.getCheckedClasses()
    fun isCreateBackup(): Boolean = createBackupCheckbox.isSelected
}

private class ClassEntry(var checked: Boolean, val fqn: String, val ruleName: String?)

private class ClassTableModel(
    private val entries: MutableList<ClassEntry>
) : AbstractTableModel() {

    override fun getRowCount(): Int = entries.size
    override fun getColumnCount(): Int = 3

    override fun getColumnName(column: Int): String = when (column) {
        1 -> "Class"
        2 -> "Name"
        else -> ""
    }

    override fun getColumnClass(column: Int): Class<*> = when (column) {
        0 -> java.lang.Boolean::class.java
        else -> String::class.java
    }

    override fun isCellEditable(row: Int, column: Int): Boolean = column == 0

    override fun getValueAt(row: Int, column: Int): Any = when (column) {
        0 -> entries[row].checked
        1 -> entries[row].fqn
        else -> entries[row].ruleName ?: ""
    }

    override fun setValueAt(value: Any?, row: Int, column: Int) {
        if (column == 0 && value is Boolean) {
            entries[row].checked = value
            fireTableCellUpdated(row, column)
        }
    }

    fun setAllChecked(checked: Boolean) {
        entries.forEach { it.checked = checked }
        fireTableDataChanged()
    }

    fun getCheckedClasses(): List<String> =
        entries.filter { it.checked }.map { it.fqn }
}
