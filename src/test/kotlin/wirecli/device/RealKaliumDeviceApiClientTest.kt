package wirecli.device

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RealKaliumDeviceApiClientTest {
    private val session =
        AuthSession(
            userId = "alice@example.com",
            accessToken = "token",
            server = null,
        )

    @Test
    fun `returns device list from runtime success`() {
        val devices = listOf(
            Device(
                id = "device-001",
                type = DeviceType.DESKTOP,
                fingerprint = "a1b2c3d4e5f6g7h8",
                lastActive = "2025-03-13T10:30:00Z",
            ),
            Device(
                id = "device-002",
                type = DeviceType.MOBILE,
                fingerprint = "b2c3d4e5f6g7h8i9",
                lastActive = "2025-03-12T15:45:00Z",
            ),
        )
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Success(KaliumDeviceSessionScope(session.userId, session.server)),
                listDevicesResult = DeviceStepResult.Success(devices),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.listDevices(session)

        val success = assertIs<DeviceListResult.Success>(result)
        assertEquals(2, success.view.devices.size)
        assertEquals("device-001", success.view.devices[0].id)
        assertEquals("device-002", success.view.devices[1].id)
    }

    @Test
    fun `maps unauthorized failure for listDevices to auth semantics`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED),
                listDevicesResult = DeviceStepResult.Success(emptyList()),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.listDevices(session)

        val failure = assertIs<DeviceListResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `maps network failure for listDevices to retry semantics`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Success(KaliumDeviceSessionScope(session.userId, session.server)),
                listDevicesResult = DeviceStepResult.Failure(DeviceFailureCategory.NETWORK),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.listDevices(session)

        val failure = assertIs<DeviceListResult.Failure>(result)
        assertEquals(DeviceMessages.NETWORK_FAILURE, failure.message)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `maps server failure for listDevices to server semantics`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Success(KaliumDeviceSessionScope(session.userId, session.server)),
                listDevicesResult = DeviceStepResult.Failure(DeviceFailureCategory.SERVER),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.listDevices(session)

        val failure = assertIs<DeviceListResult.Failure>(result)
        assertEquals(DeviceMessages.SERVER_FAILURE, failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `returns device list for user from runtime success`() {
        val devices = listOf(
            Device(
                id = "user-device-001",
                type = DeviceType.DESKTOP,
                fingerprint = "c3d4e5f6g7h8i9j0",
                lastActive = "2025-03-13T10:30:00Z",
            ),
        )
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Success(KaliumDeviceSessionScope(session.userId, session.server)),
                listDevicesForUserResult = DeviceStepResult.Success(devices),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.listDevicesForUser(session, "bob@example.com")

        val success = assertIs<DeviceListResult.Success>(result)
        assertEquals(1, success.view.devices.size)
        assertEquals("user-device-001", success.view.devices[0].id)
    }

    @Test
    fun `maps device not found failure for listDevicesForUser`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Success(KaliumDeviceSessionScope(session.userId, session.server)),
                listDevicesForUserResult = DeviceStepResult.Failure(DeviceFailureCategory.DEVICE_NOT_FOUND),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.listDevicesForUser(session, "nonexistent@example.com")

        val failure = assertIs<DeviceListResult.Failure>(result)
        assertEquals(DeviceMessages.DEVICE_NOT_FOUND, failure.message)
        assertEquals(DeviceExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `returns device detail from runtime success`() {
        val device = Device(
            id = "detail-device",
            type = DeviceType.DESKTOP,
            fingerprint = "detail-fingerprint",
            lastActive = "2025-03-13T10:30:00Z",
        )
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Success(KaliumDeviceSessionScope(session.userId, session.server)),
                getDeviceDetailResult = DeviceStepResult.Success(device),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.getDeviceDetail(session, "detail-device")

        val success = assertIs<DeviceDetailResult.Success>(result)
        assertEquals("detail-device", success.view.device.id)
        assertEquals(DeviceType.DESKTOP, success.view.device.type)
    }

    @Test
    fun `returns key package status as VALID in device detail`() {
        val device = Device(
            id = "device-001",
            type = DeviceType.DESKTOP,
            fingerprint = "fingerprint",
            lastActive = "2025-03-13T10:30:00Z",
        )
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Success(KaliumDeviceSessionScope(session.userId, session.server)),
                getDeviceDetailResult = DeviceStepResult.Success(device),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.getDeviceDetail(session, "device-001")

        val success = assertIs<DeviceDetailResult.Success>(result)
        assertEquals(KeyPackageStatus.VALID, success.view.keyPackageStatus)
    }

    @Test
    fun `maps unauthorized failure for getDeviceDetail to auth semantics`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED),
                getDeviceDetailResult = DeviceStepResult.Success(
                    Device(
                        id = "device-001",
                        type = DeviceType.DESKTOP,
                        fingerprint = "fingerprint",
                        lastActive = "2025-03-13T10:30:00Z",
                    ),
                ),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.getDeviceDetail(session, "device-001")

        val failure = assertIs<DeviceDetailResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `maps network failure for getDeviceDetail to retry semantics`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Success(KaliumDeviceSessionScope(session.userId, session.server)),
                getDeviceDetailResult = DeviceStepResult.Failure(DeviceFailureCategory.NETWORK),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.getDeviceDetail(session, "device-001")

        val failure = assertIs<DeviceDetailResult.Failure>(result)
        assertEquals(DeviceMessages.NETWORK_FAILURE, failure.message)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `maps device not found failure for getDeviceDetail`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Success(KaliumDeviceSessionScope(session.userId, session.server)),
                getDeviceDetailResult = DeviceStepResult.Failure(DeviceFailureCategory.DEVICE_NOT_FOUND),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.getDeviceDetail(session, "nonexistent")

        val failure = assertIs<DeviceDetailResult.Failure>(result)
        assertEquals(DeviceMessages.DEVICE_NOT_FOUND, failure.message)
        assertEquals(DeviceExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `deletes device successfully`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Success(KaliumDeviceSessionScope(session.userId, session.server)),
                deleteDeviceResult = DeviceStepResult.Success(Unit),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.deleteDevice(session, "device-to-delete")

        val success = assertIs<DeviceDeleteResult.Success>(result)
        assertEquals("Device deleted successfully.", success.message)
    }

    @Test
    fun `maps unauthorized failure for deleteDevice to auth semantics`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED),
                deleteDeviceResult = DeviceStepResult.Success(Unit),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.deleteDevice(session, "device-001")

        val failure = assertIs<DeviceDeleteResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `maps network failure for deleteDevice to retry semantics`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Success(KaliumDeviceSessionScope(session.userId, session.server)),
                deleteDeviceResult = DeviceStepResult.Failure(DeviceFailureCategory.NETWORK),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.deleteDevice(session, "device-001")

        val failure = assertIs<DeviceDeleteResult.Failure>(result)
        assertEquals(DeviceMessages.DELETE_NETWORK_FAILURE, failure.message)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `maps server failure for deleteDevice to server semantics`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Success(KaliumDeviceSessionScope(session.userId, session.server)),
                deleteDeviceResult = DeviceStepResult.Failure(DeviceFailureCategory.SERVER),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.deleteDevice(session, "device-001")

        val failure = assertIs<DeviceDeleteResult.Failure>(result)
        assertEquals(DeviceMessages.DELETE_SERVER_FAILURE, failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `maps device not found failure for deleteDevice`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Success(KaliumDeviceSessionScope(session.userId, session.server)),
                deleteDeviceResult = DeviceStepResult.Failure(DeviceFailureCategory.DEVICE_NOT_FOUND),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.deleteDevice(session, "nonexistent")

        val failure = assertIs<DeviceDeleteResult.Failure>(result)
        assertEquals(DeviceMessages.DEVICE_NOT_FOUND, failure.message)
        assertEquals(DeviceExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `maps unknown failure for listDevices`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Success(KaliumDeviceSessionScope(session.userId, session.server)),
                listDevicesResult = DeviceStepResult.Failure(DeviceFailureCategory.UNKNOWN),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.listDevices(session)

        val failure = assertIs<DeviceListResult.Failure>(result)
        assertEquals(DeviceMessages.UNKNOWN_FAILURE, failure.message)
        assertEquals(ExitCodes.UNKNOWN_ERROR, failure.exitCode)
    }

    @Test
    fun `maps unknown failure for deleteDevice`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult = DeviceStepResult.Success(KaliumDeviceSessionScope(session.userId, session.server)),
                deleteDeviceResult = DeviceStepResult.Failure(DeviceFailureCategory.UNKNOWN),
            )
        val client = RealKaliumDeviceApiClient(runtime)

        val result = client.deleteDevice(session, "device-001")

        val failure = assertIs<DeviceDeleteResult.Failure>(result)
        assertEquals(DeviceMessages.DELETE_UNKNOWN_FAILURE, failure.message)
        assertEquals(ExitCodes.UNKNOWN_ERROR, failure.exitCode)
    }

    private class FakeRuntime(
        private val sessionScopeResult: DeviceStepResult<KaliumDeviceSessionScope>,
        private val listDevicesResult: DeviceStepResult<List<Device>> = DeviceStepResult.Success(emptyList()),
        private val listDevicesForUserResult: DeviceStepResult<List<Device>> = DeviceStepResult.Success(emptyList()),
        private val getDeviceDetailResult: DeviceStepResult<Device> = DeviceStepResult.Failure(
            DeviceFailureCategory.DEVICE_NOT_FOUND,
        ),
        private val deleteDeviceResult: DeviceStepResult<Unit> = DeviceStepResult.Success(Unit),
    ) : RealKaliumDeviceRuntime {
        override fun resolveSessionScope(session: AuthSession): DeviceStepResult<KaliumDeviceSessionScope> {
            return sessionScopeResult
        }

        override fun listDevices(sessionScope: KaliumDeviceSessionScope): DeviceStepResult<List<Device>> {
            return listDevicesResult
        }

        override fun listDevicesForUser(
            sessionScope: KaliumDeviceSessionScope,
            userId: String,
        ): DeviceStepResult<List<Device>> {
            return listDevicesForUserResult
        }

        override fun getDeviceDetail(
            sessionScope: KaliumDeviceSessionScope,
            deviceId: String,
        ): DeviceStepResult<Device> {
            return getDeviceDetailResult
        }

        override fun deleteDevice(
            sessionScope: KaliumDeviceSessionScope,
            deviceId: String,
        ): DeviceStepResult<Unit> {
            return deleteDeviceResult
        }

        override fun shutdown() {
        }
    }
}
