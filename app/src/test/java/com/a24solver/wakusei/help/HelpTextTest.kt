package com.a24solver.wakusei.help

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The help text really is the documentation: the assets the app ships are the files in `docs/help`,
 * read at runtime, one per language.
 *
 * These tests are what keeps the bundling honest — if the build stopped adding `docs/help` as an
 * asset source, they fail at "asset missing" instead of the page silently showing nothing on a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HelpTextTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun eachLanguageMapsToItsOwnDocument() {
        assertEquals(HelpText.ENGLISH, HelpText.assetName("en"))
        assertEquals(HelpText.ENGLISH, HelpText.assetName("en-GB"))
        assertEquals(HelpText.CHINESE, HelpText.assetName("zh"))
        assertEquals(HelpText.CHINESE, HelpText.assetName("zh-Hans"))
        assertEquals(HelpText.JAPANESE, HelpText.assetName("ja"))
        assertEquals(HelpText.JAPANESE, HelpText.assetName("ja-JP"))
        assertEquals(HelpText.ENGLISH, HelpText.assetName("de"))
        assertEquals(HelpText.ENGLISH, HelpText.assetName(""))
    }

    @Test
    fun allThreeDocumentsAreBundledAndParse() {
        for (file in listOf(HelpText.ENGLISH, HelpText.CHINESE, HelpText.JAPANESE)) {
            val exists = runCatching { context.assets.open(file).close() }.isSuccess
            assertTrue("$file is not bundled (is docs/help an asset source?)", exists)

            val blocks = HelpMarkdown.parse(
                context.assets.open(file).bufferedReader().use { it.readText() }
            )
            assertTrue("$file has no title", blocks.firstOrNull()?.kind == HelpBlock.Kind.TITLE)
            assertTrue(
                "$file has no paragraphs",
                blocks.count { it.kind == HelpBlock.Kind.PARAGRAPH } >= 2
            )
        }
    }

    @Test
    fun theEnglishDocumentMentionsTheKeysItTalksAbout() {
        val text = HelpText.read(context, "en")

        for (key in listOf("NEXT", "DEL", "SOLVE", "TARGET", "CLEAR")) {
            assertTrue("the help text should mention [$key]", text.contains("[$key]"))
        }
    }

    @Test
    fun theChineseDocumentUsesTheButtonsTheScreenActuallyShows() {
        val text = HelpText.read(context, "zh")

        // these labels come from values-zh/strings.xml; the help page must not invent its own
        for (label in listOf("确认", "退格", "清空", "目标", "求 解")) {
            assertTrue("the Chinese help should call the key [$label]", text.contains(label))
        }
        assertTrue("'确定' is the old, wrong label", !text.contains("确定"))
        assertTrue("'回退' is the old, wrong label", !text.contains("回退"))
    }

    @Test
    fun theJapaneseDocumentUsesTheButtonsTheScreenActuallyShows() {
        val text = HelpText.read(context, "ja")

        for (label in listOf("確定", "削除", "クリア", "目標", "計算開始")) {
            assertTrue("the Japanese help should call the key $label", text.contains(label))
        }
    }

    @Test
    fun theCurrentLocaleDecidesWhichDocumentIsRead() {
        assertTrue(HelpText.forCurrentLocale(context).startsWith("# Help"))
    }

    @Test
    @Config(qualifiers = "zh")
    fun aChineseDeviceGetsTheChineseDocument() {
        assertTrue(
            "expected the Chinese title, got ${HelpText.forCurrentLocale(context).take(40)}",
            HelpText.forCurrentLocale(context).startsWith("# 帮助")
        )
    }

    @Test
    @Config(qualifiers = "ja")
    fun aJapaneseDeviceGetsTheJapaneseDocument() {
        assertTrue(
            "expected the Japanese title, got ${HelpText.forCurrentLocale(context).take(40)}",
            HelpText.forCurrentLocale(context).startsWith("# ヘルプ")
        )
    }

    @Test
    @Config(qualifiers = "de")
    fun aLanguageWithoutItsOwnDocumentFallsBackToEnglish() {
        assertTrue(HelpText.forCurrentLocale(context).startsWith("# Help"))
    }

    @Test
    fun anUnreadableAssetReadsAsEmptyInsteadOfCrashing() {
        assertEquals("", HelpText.readAsset(context, "no_such_file.md"))
    }
}
