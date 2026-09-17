package com.haiom.app

import com.haiom.app.data.FreeModelCatalog
import com.haiom.app.model.FreeCodingModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeOnlyPolicyTest {
    @Test fun catalogOnlyContainsAllowedModels() {
        assertTrue(FreeModelCatalog.models.isNotEmpty())
        assertTrue(FreeModelCatalog.models.all(FreeModelCatalog::isAllowed))
    }

    @Test fun rejectsUnknownPaidEndpoint() {
        val unknown = FreeCodingModel("premium", "Premium", "Unknown", "https://example.com/v1/chat", 999)
        assertFalse(FreeModelCatalog.isAllowed(unknown))
    }
}
