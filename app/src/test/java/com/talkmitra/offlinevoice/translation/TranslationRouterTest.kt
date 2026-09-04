package com.talkmitra.offlinevoice.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationRouterTest {

    private val router = TranslationRouter()

    @Test
    fun testSameLanguagePassthrough() {
        val route = router.resolveRoute(SupportedLanguage.HINDI, SupportedLanguage.HINDI)
        assertEquals(TranslationPath.SAME_LANGUAGE_PASSTHROUGH, route.path)
        assertTrue(route.steps.isEmpty())
    }

    @Test
    fun testDirectEnglishToIndicRouting() {
        val route = router.resolveRoute(SupportedLanguage.ENGLISH, SupportedLanguage.HINDI)
        assertEquals(TranslationPath.DIRECT, route.path)
        assertEquals(1, route.steps.size)
        assertEquals("en-hi", route.steps.first().key)
    }

    @Test
    fun testDirectIndicToEnglishRouting() {
        val route = router.resolveRoute(SupportedLanguage.TAMIL, SupportedLanguage.ENGLISH)
        assertEquals(TranslationPath.DIRECT, route.path)
        assertEquals(1, route.steps.size)
        assertEquals("ta-en", route.steps.first().key)
    }

    @Test
    fun testPivotRoutingCrossIndic() {
        // Tamil to Malayalam routes via English (ta -> en -> ml)
        val route = router.resolveRoute(SupportedLanguage.TAMIL, SupportedLanguage.MALAYALAM)
        assertEquals(TranslationPath.PIVOT_ENGLISH, route.path)
        assertEquals(2, route.steps.size)
        assertEquals("ta-en", route.steps[0].key)
        assertEquals("en-ml", route.steps[1].key)
    }

    @Test
    fun testIsPairSupported() {
        assertTrue(router.isPairSupported(SupportedLanguage.ENGLISH, SupportedLanguage.HINDI))
        assertTrue(router.isPairSupported(SupportedLanguage.GUJARATI, SupportedLanguage.ENGLISH))
        assertTrue(router.isPairSupported(SupportedLanguage.BENGALI, SupportedLanguage.TAMIL))
        assertTrue(router.isPairSupported(SupportedLanguage.MARATHI, SupportedLanguage.ODIA))
    }
}
