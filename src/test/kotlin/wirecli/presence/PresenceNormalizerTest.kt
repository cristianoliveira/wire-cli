package wirecli.presence

import kotlin.test.Test
import kotlin.test.assertEquals

class PresenceNormalizerTest {
    @Test
    fun `normalizes online`() {
        assertEquals(PresenceState.ONLINE, PresenceNormalizer.normalize("online"))
    }

    @Test
    fun `normalizes ONLINE uppercase`() {
        assertEquals(PresenceState.ONLINE, PresenceNormalizer.normalize("ONLINE"))
    }

    @Test
    fun `normalizes mixed case`() {
        assertEquals(PresenceState.ONLINE, PresenceNormalizer.normalize("OnLine"))
        assertEquals(PresenceState.BUSY, PresenceNormalizer.normalize("BuSy"))
        assertEquals(PresenceState.AWAY, PresenceNormalizer.normalize("AwAy"))
        assertEquals(PresenceState.OFFLINE, PresenceNormalizer.normalize("OfFlInE"))
    }

    @Test
    fun `normalizes busy`() {
        assertEquals(PresenceState.BUSY, PresenceNormalizer.normalize("busy"))
    }

    @Test
    fun `normalizes away`() {
        assertEquals(PresenceState.AWAY, PresenceNormalizer.normalize("away"))
    }

    @Test
    fun `normalizes not_available to away`() {
        assertEquals(PresenceState.AWAY, PresenceNormalizer.normalize("not_available"))
    }

    @Test
    fun `normalizes offline`() {
        assertEquals(PresenceState.OFFLINE, PresenceNormalizer.normalize("offline"))
    }

    @Test
    fun `normalizes null to unknown`() {
        assertEquals(PresenceState.UNKNOWN, PresenceNormalizer.normalize(null))
    }

    @Test
    fun `normalizes blank to unknown`() {
        assertEquals(PresenceState.UNKNOWN, PresenceNormalizer.normalize("   "))
    }

    @Test
    fun `normalizes empty string to unknown`() {
        assertEquals(PresenceState.UNKNOWN, PresenceNormalizer.normalize(""))
    }

    @Test
    fun `normalizes unsupported value to unknown`() {
        assertEquals(PresenceState.UNKNOWN, PresenceNormalizer.normalize("in_a_call"))
    }

    @Test
    fun `trims leading and trailing spaces`() {
        assertEquals(PresenceState.ONLINE, PresenceNormalizer.normalize("  online  "))
        assertEquals(PresenceState.BUSY, PresenceNormalizer.normalize(" busy "))
        assertEquals(PresenceState.AWAY, PresenceNormalizer.normalize(" away "))
        assertEquals(PresenceState.OFFLINE, PresenceNormalizer.normalize(" offline "))
    }
}
