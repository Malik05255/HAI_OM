package com.haiom.app

import com.haiom.app.network.OmniRouteClient
import org.junit.Assert.assertEquals
import org.junit.Test

class FreeOnlyPolicyTest {
    @Test fun codingAlwaysUsesOmniRouteAutoChannel() {
        assertEquals("auto/coding", OmniRouteClient.CODING_ROUTE)
    }

    @Test fun normalizesV1BaseUrl() {
        assertEquals(
            "http://127.0.0.1:20128",
            OmniRouteClient.normalizeRoot("http://127.0.0.1:20128/v1/")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedSchemes() {
        OmniRouteClient.normalizeRoot("ftp://example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCredentialsEmbeddedInUrl() {
        OmniRouteClient.normalizeRoot("https://user:pass@example.com")
    }
}
