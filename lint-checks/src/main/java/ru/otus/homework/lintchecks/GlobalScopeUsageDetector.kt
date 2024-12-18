package ru.otus.homework.lintchecks


import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.UElement
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.kotlin.toPsiType

@Suppress("UnstableApiUsage")
class GlobalScopeUsageDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(USimpleNameReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                if (node.identifier != GLOBAL_SCOPE_NAME) return

                val psiElement = node.sourcePsi
                val enclosingClass: PsiType? = psiElement?.getParentOfType<KtClass>(true)?.toPsiType()

                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message = BRIEF_DESCRIPTION,
                    quickfixData = if (enclosingClass == null) null else createFix(context, enclosingClass),
                )
            }

            private fun createFix(context: JavaContext, enclosingClass: PsiType): LintFix? =
                when {
                    isDependencyPresent(context, DEPENDENCY_LIFECYCLE_VIEW_MODEL_KTX) &&
                    isSubtypeOf(context, enclosingClass, VIEW_MODEL_FULL_CLASS_NAME) ->
                        replaceWithViewModelScope()

                    isDependencyPresent(context, DEPENDENCY_LIFECYCLE_RUNTIME_KTX) &&
                    isSubtypeOf(context, enclosingClass, FRAGMENT_FULL_CLASS_NAME) ->
                        replaceWithLifecycleScope()

                    else -> null
                }

            private fun replaceWithViewModelScope(): LintFix {
                return fix().replace()
                    .text(FIX_REPLACE_TARGET)
                    .with(FIX_REPLACE_WITH_VIEW_MODEL_SCOPE)
                    .build()
            }

            private fun replaceWithLifecycleScope(): LintFix {
                return fix().replace()
                    .text(FIX_REPLACE_TARGET)
                    .with(FIX_REPLACE_WITH_LIFECYCLE_SCOPE)
                    .build()
            }
        }
    }

    companion object {
        private const val ID = "GlobalScopeUsage"
        private const val BRIEF_DESCRIPTION = "Don't use GlobalScope for coroutines"
        private const val EXPLANATION = """
    Using GlobalScope can lead to coroutines that outlive the lifecycle of your app components, \
    potentially causing memory leaks and excessive resource usage. Prefer using a more appropriate scope \
    such as viewModelScope or lifecycleScope.
"""

        private const val GLOBAL_SCOPE_NAME = "GlobalScope"

        private const val VIEW_MODEL_FULL_CLASS_NAME = "androidx.lifecycle.ViewModel"
        private const val FRAGMENT_FULL_CLASS_NAME = "androidx.fragment.app.Fragment"

        private const val FIX_REPLACE_TARGET = "GlobalScope"
        private const val FIX_REPLACE_WITH_LIFECYCLE_SCOPE = "lifecycleScope"
        private const val FIX_REPLACE_WITH_VIEW_MODEL_SCOPE = "viewModelScope"

        private const val DEPENDENCY_LIFECYCLE_VIEW_MODEL_KTX = "androidx.lifecycle:lifecycle-viewmodel-ktx"
        private const val DEPENDENCY_LIFECYCLE_RUNTIME_KTX = "androidx.lifecycle:lifecycle-runtime-ktx"

        val ISSUE: Issue = Issue.create(
            id = ID,
            briefDescription = BRIEF_DESCRIPTION,
            explanation = EXPLANATION,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                GlobalScopeUsageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
