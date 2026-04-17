package im.flare.run

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier

object IIQSourceExtractor {

    fun extract(psiClass: PsiClass): IIQClassInfo? {
        val psiFile = psiClass.containingFile as? PsiJavaFile ?: return null
        val qualifiedName = psiClass.qualifiedName ?: return null

        val imports = psiFile.importList
            ?.allImportStatements
            ?.joinToString("\n") { it.text }
            ?: ""

        val name = extractStringField(psiClass, "name")
        val type = extractStringField(psiClass, "type")

        val executeMethod = psiClass.findMethodsByName("execute", false).firstOrNull()
            ?: return null

        val executeBody = extractBody(executeMethod)

        val helperMethods = collectHelperMethods(executeMethod, psiClass, mutableSetOf(executeMethod.name))
            .joinToString("\n\n") { it.text }

        return IIQClassInfo(
            qualifiedName = qualifiedName,
            imports = imports,
            helperMethods = helperMethods,
            executeBody = executeBody,
            name = name,
            type = type
        )
    }

    fun assembleSource(info: IIQClassInfo): String = buildString {
        if (info.imports.isNotBlank()) {
            append(info.imports)
            append("\n\n")
        }
        if (info.helperMethods.isNotBlank()) {
            append(dedent(info.helperMethods))
            append("\n\n")
        }
        append(dedent(info.executeBody))
    }.trim()

    private fun dedent(code: String): String {
        val lines = code.lines()
        val minIndent = lines
            .filter { it.isNotBlank() }
            .minOfOrNull { line -> line.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: 0 }
            ?: 0
        if (minIndent == 0) return code
        return lines.joinToString("\n") { line ->
            if (line.isBlank()) "" else line.drop(minIndent)
        }
    }

    private fun extractStringField(psiClass: PsiClass, fieldName: String): String? =
        psiClass.fields
            .find { field ->
                field.name == fieldName &&
                field.hasModifierProperty(PsiModifier.PRIVATE) &&
                field.hasModifierProperty(PsiModifier.STATIC) &&
                field.type.canonicalText == "java.lang.String"
            }
            ?.let { (it.initializer as? PsiLiteralExpression)?.value as? String }

    private fun extractBody(method: PsiMethod): String {
        val body = method.body ?: return ""
        val text = body.text
        // strip surrounding braces and trim
        return text.substring(1, text.length - 1).trim()
    }

    private fun collectHelperMethods(
        method: PsiMethod,
        ownerClass: PsiClass,
        visited: MutableSet<String>
    ): List<PsiMethod> {
        val result = mutableListOf<PsiMethod>()

        method.body?.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                val resolved = expression.resolveMethod() ?: return
                if (resolved.containingClass?.qualifiedName == ownerClass.qualifiedName &&
                    visited.add(resolved.name)
                ) {
                    result.add(resolved)
                    // Recursively collect methods called by this helper
                    result.addAll(collectHelperMethods(resolved, ownerClass, visited))
                }
            }
        })

        return result
    }
}
