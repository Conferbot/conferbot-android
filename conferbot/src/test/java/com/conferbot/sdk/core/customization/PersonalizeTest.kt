package com.conferbot.sdk.core.customization

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * "Greet by name" (core/customization/Personalize.kt): the cases of the React
 * Native SDK's __tests__/core/personalize.test.ts (the web widget's
 * tests/ownerLayout.test.js personalize, personalizeCustomizations).
 */
class PersonalizeTest {

    @Test
    fun `fills tokens and falls back`() {
        val vars = Personalize.visitorVars(mapOf("name" to "Sam Lee", "company" to "Acme"))
        assertThat(vars["first_name"]).isEqualTo("Sam")
        assertThat(Personalize.personalize("Hi {{first_name|there}} 👋", vars)).isEqualTo("Hi Sam 👋")
        assertThat(Personalize.personalize("Welcome, {{company}} team", vars)).isEqualTo("Welcome, Acme team")
        val anon = Personalize.visitorVars(null)
        assertThat(Personalize.personalize("Hi {{first_name|there}} 👋", anon)).isEqualTo("Hi there 👋")
        assertThat(Personalize.personalize("Hi {{name}}!", anon)).isEqualTo("Hi!")
        assertThat(Personalize.personalize("No tokens here", anon)).isEqualTo("No tokens here")
        assertThat(Personalize.personalize(null, anon)).isNull()
    }

    @Test
    fun `keeps a value as typed (drawn as text, not markup)`() {
        assertThat(Personalize.personalize("Hi {{name}}", Personalize.visitorVars(mapOf("name" to "Ann & Bo")))).isEqualTo("Hi Ann & Bo")
    }

    @Test
    fun `takes a name the chat asked for, firstName and lastName, and firstName tokens`() {
        assertThat(Personalize.visitorVars(emptyMap(), "Priya")["first_name"]).isEqualTo("Priya")
        val v = Personalize.visitorVars(mapOf("firstName" to "Ana", "lastName" to "Ruiz"))
        assertThat(v["name"]).isEqualTo("Ana Ruiz")
        assertThat(Personalize.personalize("Hey {{firstName}}", v)).isEqualTo("Hey Ana")
        assertThat(Personalize.visitorVars(mapOf("organization" to "Globex"))["company"]).isEqualTo("Globex")
    }

    @Test
    fun `the app's user is its metadata and its name`() {
        assertThat(Personalize.userContext(null, null)).isNull()
        assertThat(Personalize.userContext(mapOf("company" to "Acme"), "Sam Lee")).isEqualTo(mapOf("company" to "Acme", "name" to "Sam Lee"))
        val vars = Personalize.visitorVars(Personalize.userContext(null, "Sam Lee"))
        assertThat(Personalize.personalize("Hi {{first_name|there}}", vars)).isEqualTo("Hi Sam")
    }

    @Test
    fun `touches only the visitor-facing texts`() {
        val plain = mapOf("text" to "Plain")
        val c = mapOf(
            "chatIconCtaText" to "Hi {{first_name|there}}",
            "initialPageTitle" to "Hello {{name}}",
            "initialPageDescription" to "For {{company|you}}",
            "initialPageCardButtonText" to "Chat, {{first_name}}",
            "logoText" to "{{name}} Bot",
            "greetingRules" to listOf(mapOf("text" to "Pricing for {{company|your team}}"), plain)
        )
        val out = Personalize.customizations(c, Personalize.visitorVars(mapOf("firstName" to "Ana", "company" to "Globex")))
        assertThat(out["chatIconCtaText"]).isEqualTo("Hi Ana")
        assertThat(out["initialPageTitle"]).isEqualTo("Hello Ana")
        assertThat(out["initialPageDescription"]).isEqualTo("For Globex")
        assertThat(out["initialPageCardButtonText"]).isEqualTo("Chat, Ana")
        assertThat(out["logoText"]).isEqualTo("{{name}} Bot")
        val rules = out["greetingRules"] as List<*>
        assertThat((rules[0] as Map<*, *>)["text"]).isEqualTo("Pricing for Globex")
        assertThat(rules[1]).isSameInstanceAs(plain)
        assertThat(c["chatIconCtaText"]).isEqualTo("Hi {{first_name|there}}")
    }

    @Test
    fun `nothing to fill gives the same settings back`() {
        val c = mapOf<String, Any?>("chatIconCtaText" to "Hi there", "greetingRules" to listOf(mapOf("text" to "Plain")))
        assertThat(Personalize.customizations(c, Personalize.visitorVars(null))).isSameInstanceAs(c)
    }
}
