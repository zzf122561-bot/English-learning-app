package com.xuesui.englishapp.dictionary.web

import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewSecurityAndroidTest {
    @Test
    fun securitySettingsRemainDisabled() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = WebView(ApplicationProvider.getApplicationContext())
            try {
                configureSecureSettings(webView)
                assertFalse(webView.settings.javaScriptEnabled)
                assertFalse(webView.settings.allowFileAccess)
                assertFalse(webView.settings.allowContentAccess)
                assertFalse(webView.settings.domStorageEnabled)
                assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, webView.settings.mixedContentMode)
            } finally {
                webView.destroy()
            }
        }
    }
}

