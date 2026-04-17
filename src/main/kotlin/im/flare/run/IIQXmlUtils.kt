package im.flare.run

import org.jdom.input.SAXBuilder
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import java.io.StringReader

object IIQXmlUtils {

    private const val XML_PREAMBLE =
        "<?xml version='1.0' encoding='UTF-8'?>\n" +
        "<!DOCTYPE Rule PUBLIC \"sailpoint.dtd\" \"sailpoint.dtd\">"

    /**
     * Returns a SAXBuilder that:
     * - does not validate
     * - does not fetch external DTDs (returns empty content for all entity references)
     * JDOM preserves CDATA sections via its built-in LexicalHandler support.
     */
    private fun saxBuilder() = SAXBuilder().apply {
        setEntityResolver(EntityResolver { _, _ -> InputSource(StringReader("")) })
    }

    private fun outputter() = XMLOutputter(Format.getPrettyFormat())

    /**
     * Takes a raw Rule XML from the server, removes id/created/modified attributes,
     * sets name to [backupName], and returns the modified XML string with preamble.
     * CDATA sections inside <Source> are preserved.
     */
    fun buildBackupXml(xml: String, backupName: String): String {
        val doc = saxBuilder().build(StringReader(xml))
        val root = doc.rootElement
        val rule = if (root.name == "Rule") root else root.getChild("Rule")
            ?: throw IllegalStateException("No <Rule> element found in server XML")
        rule.removeAttribute("id")
        rule.removeAttribute("created")
        rule.removeAttribute("modified")
        rule.setAttribute("name", backupName)
        return "$XML_PREAMBLE\n${outputter().outputString(rule)}"
    }

    /**
     * Extracts and returns the <Rule> element XML string from a server response XML.
     * Returns null if no <Rule> element is found or parsing fails.
     */
    fun extractRuleXml(xml: String): String? = runCatching {
        val doc = saxBuilder().build(StringReader(xml))
        val root = doc.rootElement
        val rule = if (root.name == "Rule") root else root.getChild("Rule")
        rule?.let { outputter().outputString(it) }
    }.getOrNull()

    /**
     * Parses a snapshot XML file and returns a list of (ruleName, ruleXmlString) pairs
     * from the <Sailpoint> section. CDATA sections are preserved.
     */
    fun extractRuleBlocksFromSnapshot(snapshotXml: String): List<Pair<String, String>> {
        val doc = saxBuilder().build(StringReader(snapshotXml))
        val sailpoint = doc.rootElement.getChild("Sailpoint") ?: return emptyList()
        val out = outputter()
        @Suppress("UNCHECKED_CAST")
        return (sailpoint.getChildren("Rule") as List<org.jdom.Element>).mapNotNull { rule ->
            val name = rule.getAttributeValue("name") ?: return@mapNotNull null
            name to out.outputString(rule)
        }
    }
}
