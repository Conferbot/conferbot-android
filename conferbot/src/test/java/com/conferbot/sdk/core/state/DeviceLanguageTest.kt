package com.conferbot.sdk.core.state

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import java.util.Locale

/** The device language (web visitorInsights.language.browser), as RN's __tests__/core/deviceLanguage.test.ts. */
class DeviceLanguageTest {

    private val saved = Locale.getDefault()

    @After
    fun tearDown() = Locale.setDefault(saved)

    @Test
    fun `writes locales as the browser does`() {
        assertThat(DeviceLanguage.normalize("de_DE")).isEqualTo("de-DE")
        assertThat(DeviceLanguage.normalize("en_US@calendar=gregorian")).isEqualTo("en-US")
        assertThat(DeviceLanguage.normalize("ja-JP-u-ca-japanese")).isEqualTo("ja-JP")
        assertThat(DeviceLanguage.normalize("zh-Hant-TW")).isEqualTo("zh-Hant-TW")
        assertThat(DeviceLanguage.normalize(" fr ")).isEqualTo("fr")
        assertThat(DeviceLanguage.normalize("")).isEqualTo("")
        assertThat(DeviceLanguage.normalize(null)).isEqualTo("")
        assertThat(DeviceLanguage.normalize("123")).isEqualTo("")
        // The web cuts at 16 characters.
        assertThat(DeviceLanguage.normalize("sr-Latn-RS-x-private-long")).hasLength(16)
    }

    @Test
    fun `the first candidate that is a locale wins`() {
        assertThat(DeviceLanguage.localeOf(null, "", "pt_BR", "en-US")).isEqualTo("pt-BR")
        assertThat(DeviceLanguage.localeOf()).isEqualTo("")
    }

    @Test
    fun `is the web snapshot with only the language`() {
        assertThat(DeviceLanguage.visitorInsightsOf("de-DE")).isEqualTo(mapOf("v" to 1, "language" to mapOf("browser" to "de-DE")))
        assertThat(DeviceLanguage.visitorInsightsOf("")).isNull()
    }

    @Test
    fun `reads the phone's language as BCP 47`() {
        Locale.setDefault(Locale("de", "DE"))
        assertThat(DeviceLanguage.deviceLocale()).isEqualTo("de-DE")
        Locale.setDefault(Locale.forLanguageTag("ja-JP-u-ca-japanese"))
        assertThat(DeviceLanguage.deviceLocale()).isEqualTo("ja-JP")
    }

    @Test
    fun `goes with every response record`() {
        Locale.setDefault(Locale("es", "MX"))
        assertThat(ChatState.buildResponseData()["visitorInsights"]).isEqualTo(mapOf("v" to 1, "language" to mapOf("browser" to "es-MX")))
    }
}
