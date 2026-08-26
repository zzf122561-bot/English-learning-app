package com.xuesui.englishapp.dictionary.web

import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewSecurityAndroidTest {
    @Test
    fun onlySandboxedDictionaryJavaScriptIsEnabled() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = WebView(ApplicationProvider.getApplicationContext())
            try {
                configureSecureSettings(webView)
                assertTrue(webView.settings.javaScriptEnabled)
                assertFalse(webView.settings.javaScriptCanOpenWindowsAutomatically)
                assertFalse(webView.settings.allowFileAccess)
                assertFalse(webView.settings.allowContentAccess)
                assertFalse(webView.settings.allowFileAccessFromFileURLs)
                assertFalse(webView.settings.allowUniversalAccessFromFileURLs)
                assertFalse(webView.settings.domStorageEnabled)
                assertFalse(webView.settings.supportMultipleWindows())
                assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, webView.settings.mixedContentMode)
            } finally {
                webView.destroy()
            }
        }
    }
}
