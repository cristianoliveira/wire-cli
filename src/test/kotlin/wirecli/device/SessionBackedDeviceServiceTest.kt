package wirecli.device

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.AuthSessionStore
import wirecli.auth.ExitCodes
import wirecli.auth.SessionInventory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SessionBackedDeviceServiceTest {
    @Test
    fun `returns unauthorized when no session is persisted for listCurrentDevices`() {
        val service =
            SessionBackedDeviceService(
                sessionStore = FakeSessionStore(activeSession = null),
                apiClient = FakeDeviceApiClient(listResult = DeviceListResult.Success(DeviceListView(emptyList()))),
            )

        val result = service.listCurrentDevices()

        val failure = assertIs<DeviceListResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `returns backend device list result for persisted session`() {
        val expected: DeviceListResult =
            DeviceListResult.Success(
                DeviceListView(
                    devices =
                        listOf(
                            Device(
                                id = "device-001",
                                type = DeviceType.DESKTOP,
                                fingerprint = "a1b2c3d4e5f6g7h8i9j0",
                                lastActive = "2025-03-13T10:30:00Z",
                            ),
                        ),
                ),
            )
        val service =
            SessionBackedDeviceService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "alice@example.com",
                                accessToken = "token",
                                server = null,
                            ),
                    ),
                apiClient = FakeDeviceApiClient(listResult = expected),
            )

        val result = service.listCurrentDevices()

        val success = assertIs<DeviceListResult.Success>(result)
        assertEquals(1, success.view.devices.size)
        assertEquals("device-001", success.view.devices[0].id)
    }

    @Test
    fun `returns unauthorized when no session is persisted for getDetail`() {
        val service =
            SessionBackedDeviceService(
                sessionStore = FakeSessionStore(activeSession = null),
                apiClient =
                    FakeDeviceApiClient(
                        detailResult =
                            DeviceDetailResult.Success(
                                DeviceDetailView(
                                    device =
                                        Device(
                                            id = "device-001",
                                            type = DeviceType.DESKTOP,
                                            fingerprint = "a1b2c3d4e5f6g7h8i9j0",
                                            lastActive = "2025-03-13T10:30:00Z",
                                        ),
                                    keyPackageStatus = KeyPackageStatus.VALID,
                                ),
                            ),
                    ),
            )

        val result = service.getDetail("device-001")

        val failure = assertIs<DeviceDetailResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `delegates getDetail to backend for persisted session`() {
        val device =
            Device(
                id = "device-001",
                type = DeviceType.DESKTOP,
                fingerprint = "a1b2c3d4e5f6g7h8i9j0",
                lastActive = "2025-03-13T10:30:00Z",
            )
        val expected: DeviceDetailResult =
            DeviceDetailResult.Success(
                DeviceDetailView(
                    device = device,
                    keyPackageStatus = KeyPackageStatus.VALID,
                ),
            )
        val apiClient = FakeDeviceApiClient(detailResult = expected)
        val service =
            SessionBackedDeviceService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "alice@example.com",
                                accessToken = "token",
                                server = null,
                            ),
                    ),
                apiClient = apiClient,
            )

        val result = service.getDetail("device-001")

        val success = assertIs<DeviceDetailResult.Success>(result)
        assertEquals("device-001", success.view.device.id)
        assertEquals("device-001", apiClient.lastDetailDeviceId)
    }

    @Test
    fun `returns unauthorized when no session is persisted for remove`() {
        val service =
            SessionBackedDeviceService(
                sessionStore = FakeSessionStore(activeSession = null),
                apiClient = FakeDeviceApiClient(deleteResult = DeviceDeleteResult.Success("Deleted")),
            )

        val result = service.remove("device-001")

        val failure = assertIs<DeviceDeleteResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `delegates remove to backend for persisted session`() {
        val expected: DeviceDeleteResult = DeviceDeleteResult.Success("Device deleted successfully.")
        val apiClient = FakeDeviceApiClient(deleteResult = expected)
        val service =
            SessionBackedDeviceService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "alice@example.com",
                                accessToken = "token",
                                server = null,
                            ),
                    ),
                apiClient = apiClient,
            )

        val result = service.remove("device-001")

        val success = assertIs<DeviceDeleteResult.Success>(result)
        assertEquals("Device deleted successfully.", success.message)
        assertEquals("device-001", apiClient.lastDeleteDeviceId)
    }

    private class FakeSessionStore(private val activeSession: AuthSession?) : AuthSessionStore {
        override fun readActiveSession(): AuthSession? = activeSession

        override fun readSessionInventory(): SessionInventory =
            SessionInventory(
                activeSession = activeSession,
                validSessions = if (activeSession == null) 0 else 1,
                invalidSessions = 0,
            )

        override fun writeActiveSession(session: AuthSession) {
        }

        override fun clearActiveSession() {
        }
    }

    private class FakeDeviceApiClient(
        private val listResult: DeviceListResult? = null,
        private val detailResult: DeviceDetailResult? = null,
        private val deleteResult: DeviceDeleteResult? = null,
    ) : DeviceApiClient {
        var lastDetailDeviceId: String? = null
        var lastDeleteDeviceId: String? = null

        override fun listDevices(session: AuthSession): DeviceListResult =
            listResult ?: DeviceListResult.Success(DeviceListView(emptyList()))

        override fun getDeviceDetail(
            session: AuthSession,
            deviceId: String,
        ): DeviceDetailResult {
            lastDetailDeviceId = deviceId
            return detailResult
                ?: DeviceDetailResult.Success(
                    DeviceDetailView(
                        device =
                            Device(
                                id = "device-001",
                                type = DeviceType.DESKTOP,
                                fingerprint = "a1b2c3d4e5f6g7h8i9j0",
                                lastActive = "2025-03-13T10:30:00Z",
                            ),
                        keyPackageStatus = KeyPackageStatus.VALID,
                    ),
                )
        }

        override fun deleteDevice(
            session: AuthSession,
            deviceId: String,
        ): DeviceDeleteResult {
            lastDeleteDeviceId = deviceId
            return deleteResult ?: DeviceDeleteResult.Success("Deleted")
        }
    }
}
