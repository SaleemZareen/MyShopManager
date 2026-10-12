package com.saleemkhan.myshopmanager

import com.saleemkhan.myshopmanager.data.firebase.FirebaseAppConfig
import org.junit.Assert.*
import org.junit.Test

class FirebaseConfigTest {

    @Test
    fun testDefaultConfigIsInvalidWithoutKeys() {
        val defaultConfig = FirebaseAppConfig()
        assertFalse("Default FirebaseAppConfig without API key must be invalid", defaultConfig.isValid)
        assertTrue(defaultConfig.apiKey.isBlank())
        assertTrue(defaultConfig.projectId.isBlank())
    }

    @Test
    fun testConfigValidationWithValidValues() {
        val validConfig = FirebaseAppConfig(
            projectId = "myshopmanager_test",
            appId = "1:123456789:android:test",
            apiKey = "AIzaSyTestKey123456789",
            authDomain = "test.firebaseapp.com"
        )
        assertTrue("Config with non-blank projectId and apiKey must be valid", validConfig.isValid)
        assertEquals("myshopmanager_test", validConfig.projectId)
        assertEquals("AIzaSyTestKey123456789", validConfig.apiKey)
    }

    @Test
    fun testConfigValidationWithMissingApiKey() {
        val missingKeyConfig = FirebaseAppConfig(
            projectId = "myshopmanager_test",
            apiKey = ""
        )
        assertFalse("Config with empty API key must be invalid", missingKeyConfig.isValid)
    }

    @Test
    fun testConfigValidationWithMissingProjectId() {
        val missingProjectConfig = FirebaseAppConfig(
            projectId = "",
            apiKey = "AIzaSyTestKey123456789"
        )
        assertFalse("Config with empty projectId must be invalid", missingProjectConfig.isValid)
    }
}
