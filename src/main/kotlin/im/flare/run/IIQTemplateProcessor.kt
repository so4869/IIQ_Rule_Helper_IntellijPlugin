package im.flare.run

import java.io.File

object IIQTemplateProcessor {

    private val SOURCE_PATTERN = Regex("<Source>[\\s\\S]*?</Source>")
    private val RULE_TAG_PATTERN = Regex("(<Rule)(\\b[^>]*)(>)")

    fun process(
        templateFile: File,
        info: IIQClassInfo,
        existingId: String? = null,
        createdMs: Long? = null,
        modifiedMs: Long? = null
    ): String {
        val source = IIQSourceExtractor.assembleSource(info)
        return templateFile.readText(Charsets.UTF_8)
            .replace("\$TEMPLATE.NAME", info.name ?: "")
            .replace("\$TEMPLATE.TYPE", info.type ?: "")
            .let { addRuleAttributes(it, existingId, createdMs, modifiedMs) }
            .let { SOURCE_PATTERN.replace(it, "<Source><![CDATA[\n$source\n]]></Source>") }
    }

    private fun addRuleAttributes(
        xml: String,
        existingId: String?,
        createdMs: Long?,
        modifiedMs: Long?
    ): String {
        if (existingId == null && createdMs == null && modifiedMs == null) return xml
        return RULE_TAG_PATTERN.replace(xml) { match ->
            val idAttr = if (existingId != null) """ id="$existingId"""" else ""
            val trailingAttrs = buildString {
                if (createdMs != null) append(""" created="$createdMs"""")
                if (modifiedMs != null) append(""" modified="$modifiedMs"""")
            }
            // group 1 = "<Rule", group 2 = existing attrs, group 3 = ">"
            "${match.groupValues[1]}$idAttr${match.groupValues[2]}$trailingAttrs${match.groupValues[3]}"
        }
    }

    fun resolveTemplateFile(templateDirectory: String, type: String): File =
        File(templateDirectory, "$type.template.xml")
}
