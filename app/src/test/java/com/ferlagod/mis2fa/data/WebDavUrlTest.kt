// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.data

import com.ferlagod.mis2fa.data.local.NextcloudCredentials
import com.ferlagod.mis2fa.data.local.SecureSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebDavUrlTest {

    @Test
    fun usesUsernameWhenUserIdIsUnknown() {
        val creds = NextcloudCredentials("https://cloud.example.org/", "ana", "x")
        assertEquals("https://cloud.example.org/remote.php/dav/files/ana/Mis2FA/", SecureSettings.webDavRootUrl(creds))
    }

    @Test
    fun usesNextcloudUserIdForEmailLogins() {
        val creds = NextcloudCredentials("https://cloud.example.org", "ana@example.org", "x", userId = "a1b2c3")
        assertEquals("https://cloud.example.org/remote.php/dav/files/a1b2c3/Mis2FA/", SecureSettings.webDavRootUrl(creds))
    }

    @Test
    fun encodesSpacesInUserId() {
        val creds = NextcloudCredentials("https://cloud.example.org", "Ana García", "x")
        assertEquals("https://cloud.example.org/remote.php/dav/files/Ana%20Garc%C3%ADa/Mis2FA/", SecureSettings.webDavRootUrl(creds))
    }

    @Test
    fun fullWebDavUrlIsUsedAsIs() {
        val creds = NextcloudCredentials("https://cloud.example.org/remote.php/dav/files/ana/Otra", "ana", "x", userId = "zzz")
        assertEquals("https://cloud.example.org/remote.php/dav/files/ana/Otra/", SecureSettings.webDavRootUrl(creds))
    }

    @Test
    fun onlyHttpsIsAccepted() {
        assertNull(SecureSettings.normalizeServerUrl("http://cloud.example.org"))
        assertEquals("https://cloud.example.org", SecureSettings.normalizeServerUrl(" cloud.example.org/ "))
    }
}
