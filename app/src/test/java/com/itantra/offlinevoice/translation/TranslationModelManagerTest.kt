package com.itantra.offlinevoice.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationModelManagerTest {

    @Test
    fun testLazyLoading() {
        val manager = TranslationModelManager(maxCachedModels = 2)
        val pair = LanguagePair(SupportedLanguage.ENGLISH, SupportedLanguage.HINDI)

        assertFalse(manager.isModelLoaded(pair))

        val loaded = manager.getOrLoadModel(pair)
        assertNotNull(loaded)
        assertTrue(manager.isModelLoaded(pair))
        assertEquals(1, manager.getLoadedModelsList().size)
    }

    @Test
    fun testLruEviction() {
        val manager = TranslationModelManager(maxCachedModels = 2)

        val pair1 = LanguagePair(SupportedLanguage.ENGLISH, SupportedLanguage.HINDI)
        val pair2 = LanguagePair(SupportedLanguage.ENGLISH, SupportedLanguage.TAMIL)
        val pair3 = LanguagePair(SupportedLanguage.ENGLISH, SupportedLanguage.BENGALI)

        manager.getOrLoadModel(pair1)
        manager.getOrLoadModel(pair2)
        assertEquals(2, manager.getLoadedModelsList().size)

        // Loading 3rd model triggers eviction of LRU model (pair1)
        manager.getOrLoadModel(pair3)
        assertEquals(2, manager.getLoadedModelsList().size)
        assertTrue(manager.isModelLoaded(pair3))
        assertTrue(manager.isModelLoaded(pair2))
        assertFalse(manager.isModelLoaded(pair1))
    }

    @Test
    fun testUnloadAll() {
        val manager = TranslationModelManager(maxCachedModels = 4)
        manager.getOrLoadModel(LanguagePair(SupportedLanguage.ENGLISH, SupportedLanguage.HINDI))
        manager.getOrLoadModel(LanguagePair(SupportedLanguage.ENGLISH, SupportedLanguage.GUJARATI))

        assertEquals(2, manager.getLoadedModelsList().size)
        manager.unloadAll()
        assertEquals(0, manager.getLoadedModelsList().size)
        assertEquals(0L, manager.getTotalEstimatedMemoryBytes())
    }
}
