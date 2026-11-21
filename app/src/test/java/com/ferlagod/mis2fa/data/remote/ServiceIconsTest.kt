// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/MisApps-labs/mis_2fa

package com.ferlagod.mis2fa.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceIconsTest {

    @Test
    fun guessesDomainFromIssuer() {
        assertEquals("github.com", ServiceIcons.guessDomain("GitHub"))
        assertEquals("epicgames.com", ServiceIcons.guessDomain("Epic Games"))
        assertEquals("proton.me", ServiceIcons.guessDomain("Proton"))
        assertEquals("aws.amazon.com", ServiceIcons.guessDomain("Amazon Web Services"))
        assertEquals("google.com", ServiceIcons.guessDomain("Google (Trabajo)"))
        assertEquals("microsoft.com", ServiceIcons.guessDomain("Microsoft Outlook"))
        assertEquals("slack.com", ServiceIcons.guessDomain("Slack - equipo"))
        assertEquals("login.example.org", ServiceIcons.guessDomain("login.example.org"))
        assertEquals("correos.com", ServiceIcons.guessDomain("Corréos"))
        assertNull(ServiceIcons.guessDomain(""))
        assertNull(ServiceIcons.guessDomain("  ✨ "))
    }

    @Test
    fun normalizesWhatTheUserTypes() {
        assertEquals("www.github.com", ServiceIcons.normalizeDomain(" https://www.GitHub.com/login?x=1 "))
        assertEquals("example.org", ServiceIcons.normalizeDomain("user@example.org:8443"))
        assertNull(ServiceIcons.normalizeDomain("no es un dominio"))
        assertNull(ServiceIcons.normalizeDomain("localhost"))
        assertNull(ServiceIcons.normalizeDomain("192.168.1.1"))
    }

    @Test
    fun prefersLargeRasterIconsAndSkipsSvg() {
        val html = """
            <html><head>
            <link rel="icon" href="/favicon-16.png" sizes="16x16">
            <LINK REL="shortcut icon" HREF='favicon.ico'>
            <link rel="icon" type="image/svg+xml" href="/logo.svg">
            <link rel="mask-icon" href="/mask.png">
            <link rel="apple-touch-icon" sizes="180x180" href="https://cdn.example.com/touch.png?v=1&amp;b=2">
            <link rel="icon" href="http://insecure.example.com/x.png" sizes="192x192">
            <link rel="stylesheet" href="/style.css">
            </head></html>
        """.trimIndent()
        val urls = ServiceIcons.parseIconLinks(html, "https://www.example.com/es/".toHttpUrl()).map { it.toString() }
        assertEquals("https://cdn.example.com/touch.png?v=1&b=2", urls.first())
        assertTrue("https://www.example.com/es/favicon.ico" in urls)
        assertTrue(urls.none { it.endsWith(".svg") || "mask" in it || it.startsWith("http:") || it.endsWith(".css") })
        assertEquals("https://www.example.com/favicon-16.png", urls.last())
    }
}
