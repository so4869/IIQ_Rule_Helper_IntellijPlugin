package im.flare.action

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.execution.RunManager
import im.flare.run.IIQRunConfiguration

class CompareWithServerGroup : ActionGroup() {

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isVisible = false
            return
        }
        val psiClass = ReadAction.compute<PsiClass?, Throwable> { getCurrentPsiClass(e) }
        e.presentation.isVisible = psiClass != null && ReadAction.compute<Boolean, Throwable> { isEligible(psiClass) }
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val e = e ?: return AnAction.EMPTY_ARRAY
        val project = e.project ?: return AnAction.EMPTY_ARRAY
        val psiClass = ReadAction.compute<PsiClass?, Throwable> { getCurrentPsiClass(e) }
            ?: return AnAction.EMPTY_ARRAY

        val configs = RunManager.getInstance(project).allSettings
            .mapNotNull { it.configuration as? IIQRunConfiguration }

        if (configs.isEmpty()) return AnAction.EMPTY_ARRAY

        return configs.map { config ->
            CompareWithServerAction(config, psiClass)
        }.toTypedArray()
    }

    companion object {
        fun getCurrentPsiClass(e: AnActionEvent): PsiClass? {
            // From editor caret position
            val editor = e.getData(CommonDataKeys.EDITOR)
            val psiFile = e.getData(CommonDataKeys.PSI_FILE)
            if (editor != null && psiFile != null) {
                val offset = editor.caretModel.offset
                val element = psiFile.findElementAt(offset)
                PsiTreeUtil.getParentOfType(element, PsiClass::class.java)?.let { return it }
            }

            // From project view selection
            val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
            if (psiElement is PsiClass) return psiElement
            if (psiElement is PsiJavaFile) return psiElement.classes.firstOrNull()
            PsiTreeUtil.getParentOfType(psiElement, PsiClass::class.java)?.let { return it }

            return null
        }

        fun isEligible(psiClass: PsiClass): Boolean {
            val hasPerformFlag = psiClass.fields.any { field ->
                field.name == "PERFORM_IIQ_SERVER" &&
                field.hasModifierProperty(PsiModifier.PRIVATE) &&
                field.hasModifierProperty(PsiModifier.STATIC) &&
                field.type == PsiType.BOOLEAN &&
                field.initializer?.text?.trim() == "true"
            }
            if (!hasPerformFlag) return false

            val hasName = psiClass.fields.any { field ->
                field.name == "name" &&
                field.hasModifierProperty(PsiModifier.PRIVATE) &&
                field.hasModifierProperty(PsiModifier.STATIC) &&
                field.type.canonicalText == "java.lang.String"
            }
            val hasType = psiClass.fields.any { field ->
                field.name == "type" &&
                field.hasModifierProperty(PsiModifier.PRIVATE) &&
                field.hasModifierProperty(PsiModifier.STATIC) &&
                field.type.canonicalText == "java.lang.String"
            }
            return hasName && hasType
        }
    }
}
