package com.xuesui.englishapp.modelprobe.runtime

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechServiceBudgetTest {
    @Test fun backgroundGenerationHasFiveHoursFiftyMinuteActiveBudget() {
        assertEquals(
            TimeUnit.HOURS.toMillis(5) + TimeUnit.MINUTES.toMillis(50),
            SpeechGenerationService.BUDGET_MS
        )
    }

    @Test fun wakeLockExpiresOneMinuteAfterActiveBudget() {
        assertEquals(
            TimeUnit.MINUTES.toMillis(1),
            SpeechGenerationService.WAKE_TIMEOUT_MS - SpeechGenerationService.BUDGET_MS
        )
    }
}
