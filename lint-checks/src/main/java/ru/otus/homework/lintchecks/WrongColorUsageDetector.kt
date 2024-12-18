package ru.otus.homework.lintchecks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.util.regex.Pattern

@Suppress("UnstableApiUsage")
internal class WrongColorUsageDetector : ResourceXmlDetector() {

    private data class ColorInfo(
        val location: Location,
        val colorValue: String,
        val xmlContext: XmlContext,
        val element: Element,
    )

    private val hexColorsAndRefs = mutableListOf<ColorInfo>()

    // e.g. #FF018786 -> teal_700
    private val colorValuesToNamesPalette = mutableMapOf<String, String>()

    override fun getApplicableAttributes(): Collection<String>? {
        return XmlScannerConstants.ALL
    }

    override fun getApplicableElements(): Collection<String> {
        return XmlScannerConstants.ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val attrValue = attribute.value.orEmpty()
        if (isHexColor(attrValue) || isAndroidColorReference(attrValue)) {
            hexColorsAndRefs.add(
                ColorInfo(
                    location = context.getValueLocation(attribute),
                    colorValue = attrValue,
                    xmlContext = context,
                    element = attribute.ownerElement,
                )
            )
        }
    }

    private fun isHexColor(value: String): Boolean {
        return COLOR_PATTERN.matcher(value).matches()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val resourceFolderType = context.resourceFolderType ?: return
        if (resourceFolderType == ResourceFolderType.VALUES &&
            context.file.name == PALETTE_RESOURCE_FILE
        ) {
            saveColorToPalette(element)
        }
    }

    private fun saveColorToPalette(element: Element) {
        val attrName = element.getAttribute("name")
        val colorTextValue = element.textContent?.trim() ?: return

        normalizeColor(colorTextValue)?.let { normalized ->
            colorValuesToNamesPalette[normalized.uppercase()] = attrName
        }
    }

    private fun isAndroidColorReference(value: String): Boolean {
        return value.startsWith(ANDROID_COLOR_RES_PREFIX)
    }

    override fun afterCheckRootProject(context: Context) {
        for (info in hexColorsAndRefs) {
            val colorValue = info.colorValue

            if (colorValue.startsWith("#")) {
                val normalizedColorValue = normalizeColor(colorValue)
                if (normalizedColorValue != null) {
                    reportRawHexColorIssue(normalizedColorValue, info, colorValue)
                }
            } else if (colorValue.startsWith(ANDROID_COLOR_RES_PREFIX)) {
                reportSystemColorIssue(info, colorValue)
            }
        }
    }

    private fun reportRawHexColorIssue(
        normalizedColorValue: String,
        info: ColorInfo,
        colorValue: String
    ) {
        val colorName = colorValuesToNamesPalette[normalizedColorValue.uppercase()]
        if (colorName != null) {

            // raw hex color is in palette
            info.xmlContext.report(
                issue = ISSUE,
                scope = info.element,
                location = info.location,
                message = "Color $colorValue is in palette. Don't hardcode colors, use " +
                        "palette references: @color/$colorName",
                quickfixData = replaceWithLinkToPaletteFix(info.location, colorName)
            )

        } else {

            // raw hex color is not in palette
            info.xmlContext.report(
                issue = ISSUE,
                scope = info.element,
                location = info.location,
                message = "Using raw color $colorValue, which is not in the palette. " +
                        "Add it to the palette or use an existing color.",
            )
        }
    }

    private fun reportSystemColorIssue(
        info: ColorInfo,
        colorValue: String
    ) {
        info.xmlContext.report(
            issue = ISSUE,
            scope = info.element,
            location = info.location,
            message = "System color $colorValue is used. Add the corresponding color to " +
                    "the palette or use an existing one.",
        )
    }

    private fun replaceWithLinkToPaletteFix(location: Location, newColor: String): LintFix {
        return fix()
            .replace()
            .range(location)
            .name("Replace with @color/$newColor")
            .all()
            .with(COLOR_RES_PREFIX + newColor)
            .build()
    }

    companion object {

        // matches hexadecimal colors of lengths 3, 4, 6, or 8
        private val COLOR_PATTERN =
            Pattern.compile("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

        private const val ID = "WrongColorUsage"
        private const val BRIEF_DESCRIPTION = "Should use colors only from palette"
        private const val EXPLANATION =
            "All app colors should be taken from the color palette defined in `colors.xml`"
        private const val PRIORITY = 3
        private const val PALETTE_RESOURCE_FILE = "colors.xml"
        private const val COLOR_RES_PREFIX = "@color/"
        private const val ANDROID_COLOR_RES_PREFIX = "@android:color/"

        val ISSUE = Issue.create(
            id = ID,
            briefDescription = BRIEF_DESCRIPTION,
            explanation = EXPLANATION,
            category = Category.CORRECTNESS,
            priority = PRIORITY,
            severity = Severity.WARNING,
            implementation = Implementation(
                WrongColorUsageDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}