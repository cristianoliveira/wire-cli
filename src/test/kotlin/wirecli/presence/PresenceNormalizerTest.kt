package wirecli.presence

import kotlin.test.Test
import kotlin.test.assertEquals

class PresenceNormalizerTest {
    @Test
    fun `normalizes online`() {
        assertEquals(PresenceState.ONLINE, PresenceStatusContract.normalize("online"))
    }

    @Test
    fun `normalizes ONLINE uppercase`() {
        assertEquals(PresenceState.ONLINE, PresenceStatusContract.normalize("ONLINE"))
    }

    @Test
    fun `normalizes mixed case`() {
        assertEquals(PresenceState.ONLINE, PresenceStatusContract.normalize("OnLine"))
        assertEquals(PresenceState.BUSY, PresenceStatusContract.normalize("BuSy"))
        assertEquals(PresenceState.AWAY, PresenceStatusContract.normalize("AwAy"))
        assertEquals(PresenceState.OFFLINE, PresenceStatusContract.normalize("OfFlInE"))
    }

    @Test
    fun `normalizes busy`() {
        assertEquals(PresenceState.BUSY, PresenceStatusContract.normalize("busy"))
    }

    @Test
    fun `normalizes away`() {
        assertEquals(PresenceState.AWAY, PresenceStatusContract.normalize("away"))
    }

    @Test
    fun `normalizes not_available to away`() {
        assertEquals(PresenceState.AWAY, PresenceStatusContract.normalize("not_available"))
    }

    @Test
    fun `normalizes offline`() {
        assertEquals(PresenceState.OFFLINE, PresenceStatusContract.normalize("offline"))
    }

    @Test
    fun `normalizes null to unknown`() {
        assertEquals(PresenceState.UNKNOWN, PresenceStatusContract.normalize(null))
    }

    @Test
    fun `normalizes blank to unknown`() {
        assertEquals(PresenceState.UNKNOWN, PresenceStatusContract.normalize("   "))
    }

    @Test
    fun `normalizes empty string to unknown`() {
        assertEquals(PresenceState.UNKNOWN, PresenceStatusContract.normalize(""))
    }

    @Test
    fun `normalizes unsupported value to unknown`() {
        assertEquals(PresenceState.UNKNOWN, PresenceStatusContract.normalize("in_a_call"))
    }

    @Test
    fun `trims leading and trailing spaces`() {
        assertEquals(PresenceState.ONLINE, PresenceStatusContract.normalize("  online  "))
        assertEquals(PresenceState.BUSY, PresenceStatusContract.normalize(" busy "))
        assertEquals(PresenceState.AWAY, PresenceStatusContract.normalize(" away "))
        assertEquals(PresenceState.OFFLINE, PresenceStatusContract.normalize(" offline "))
    }

    @Test
    fun `accepts only writable statuses in parser`() {
        assertEquals(WritablePresenceState.ONLINE, PresenceStatusContract.parseWritable("online"))
        assertEquals(WritablePresenceState.BUSY, PresenceStatusContract.parseWritable("BUSY"))
        assertEquals(WritablePresenceState.AWAY, PresenceStatusContract.parseWritable(" away "))
        assertEquals(WritablePresenceState.OFFLINE, PresenceStatusContract.parseWritable("offline"))
    }

    @Test
    fun `rejects non writable statuses in parser`() {
        assertEquals(null, PresenceStatusContract.parseWritable(null))
        assertEquals(null, PresenceStatusContract.parseWritable(" "))
        assertEquals(null, PresenceStatusContract.parseWritable("unknown"))
        assertEquals(null, PresenceStatusContract.parseWritable("not_available"))
        assertEquals(null, PresenceStatusContract.parseWritable("in_a_call"))
    }
}
