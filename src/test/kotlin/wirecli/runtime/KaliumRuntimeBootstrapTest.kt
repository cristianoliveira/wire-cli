package wirecli.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class KaliumRuntimeBootstrapTest {
    @Test
    fun `defaults to stub backend when selector is absent`() {
        val selected = KaliumRuntimeBootstrap.resolveBackendForTests(environmentBackend = null)

        assertEquals("STUB", selected)
    }

    @Test
    fun `uses stub backend when env selector is stub`() {
        val selected = KaliumRuntimeBootstrap.resolveBackendForTests(environmentBackend = "stub")

        assertEquals("STUB", selected)
    }

    @Test
    fun `uses real backend when env selector is real`() {
        val selected = KaliumRuntimeBootstrap.resolveBackendForTests(environmentBackend = "real")

        assertEquals("REAL", selected)
    }

    @Test
    fun `falls back to stub backend for invalid selector`() {
        val selected = KaliumRuntimeBootstrap.resolveBackendForTests(environmentBackend = "not-a-backend")

        assertEquals("STUB", selected)
    }
}
