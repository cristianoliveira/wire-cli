package wirecli.device

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService
import wirecli.auth.ExitCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthGuardedDeviceServiceTest {
    @Test
    fun `returns auth failure when listCurrentDevices called without session`() {
        val service =
            AuthGuardedDeviceService(
                authSessionService = FakeAuthSessionService(isAuthorized = false),
                delegate = FakeDeviceService(),
            )

        val result = service.listCurrentDevices()

        val failure = assertIs<DeviceListResult.Failure>(result)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `delegates listCurrentDevices when session is valid`() {
        val expectedDevice =
            Device(
                id = "device-001",
                type = DeviceType.DESKTOP,
                fingerprint = "a1b2c3d4e5f6g7h8i9j0",
                lastActive = "2025-03-13T10:30:00Z",
            )
        val expectedResult: DeviceListResult =
            DeviceListResult.Success(
                DeviceListView(devices = listOf(expectedDevice)),
            )
        val delegate = FakeDeviceService(listResult = expectedResult)
        val service =
            AuthGuardedDeviceService(
                authSessionService = FakeAuthSessionService(isAuthorized = true),
                delegate = delegate,
            )

        val result = service.listCurrentDevices()

        val success = assertIs<DeviceListResult.Success>(result)
        assertEquals(1, success.view.devices.size)
    }

    @Test
    fun `returns auth failure when getDetail called without session`() {
        val service =
            AuthGuardedDeviceService(
                authSessionService = FakeAuthSessionService(isAuthorized = false),
                delegate = FakeDeviceService(),
            )

        val result = service.getDetail("device-001")

        val failure = assertIs<DeviceDetailResult.Failure>(result)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `delegates getDetail when session is valid`() {
        val device =
            Device(
                id = "device-001",
                type = DeviceType.DESKTOP,
                fingerprint = "a1b2c3d4e5f6g7h8i9j0",
                lastActive = "2025-03-13T10:30:00Z",
            )
        val expectedResult: DeviceDetailResult =
            DeviceDetailResult.Success(
                DeviceDetailView(
                    device = device,
                    keyPackageStatus = KeyPackageStatus.VALID,
                ),
            )
        val delegate = FakeDeviceService(detailResult = expectedResult)
        val service =
            AuthGuardedDeviceService(
                authSessionService = FakeAuthSessionService(isAuthorized = true),
                delegate = delegate,
            )

        val result = service.getDetail("device-001")

        val success = assertIs<DeviceDetailResult.Success>(result)
        assertEquals("device-001", success.view.device.id)
    }

    @Test
    fun `returns auth failure when remove called without session`() {
        val service =
            AuthGuardedDeviceService(
                authSessionService = FakeAuthSessionService(isAuthorized = false),
                delegate = FakeDeviceService(),
            )

        val result = service.remove("device-001")

        val failure = assertIs<DeviceDeleteResult.Failure>(result)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `delegates remove when session is valid`() {
        val expectedResult: DeviceDeleteResult = DeviceDeleteResult.Success("Device deleted successfully.")
        val delegate = FakeDeviceService(deleteResult = expectedResult)
        val service =
            AuthGuardedDeviceService(
                authSessionService = FakeAuthSessionService(isAuthorized = true),
                delegate = delegate,
            )

        val result = service.remove("device-001")

        val success = assertIs<DeviceDeleteResult.Success>(result)
        assertEquals("Device deleted successfully.", success.message)
    }

    private class FakeAuthSessionService(private val isAuthorized: Boolean) : AuthSessionService {
        override fun login(input: wirecli.auth.LoginInput): AuthResult {
            throw NotImplementedError()
        }

        override fun logout(): AuthResult {
            throw NotImplementedError()
        }

        override fun requireActiveSession(): AuthResult {
            return if (isAuthorized) {
                AuthResult.Success("Session is valid")
            } else {
                AuthResult.Failure("No active session", ExitCodes.UNAUTHORIZED)
            }
        }
    }

    private class FakeDeviceService(
        private val listResult: DeviceListResult =
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
            ),
        private val detailResult: DeviceDetailResult =
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
        private val deleteResult: DeviceDeleteResult = DeviceDeleteResult.Success("Deleted"),
        private val verifyResult: DeviceVerifyResult =
            DeviceVerifyResult.Success("Verified", "a1b2c3d4e5f6g7h8i9j0"),
    ) : DeviceService {
        override fun listCurrentDevices(): DeviceListResult = listResult

        override fun listDevicesForUser(userId: String): DeviceListResult = listResult

        override fun getDetail(deviceId: String): DeviceDetailResult = detailResult

        override fun remove(
            deviceId: String,
            password: String?,
        ): DeviceDeleteResult = deleteResult

        override fun verify(deviceId: String): DeviceVerifyResult = verifyResult
    }
}
