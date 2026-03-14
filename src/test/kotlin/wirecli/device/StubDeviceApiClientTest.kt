package wirecli.device

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StubDeviceApiClientTest {
    private val session =
        AuthSession(
            userId = "alice@example.com",
            accessToken = "token",
            server = null,
        )

    @Test
    fun `returns list of devices by default`() {
        val client = StubDeviceApiClient(emptyMap())

        val result = client.listDevices(session)

        val success = assertIs<DeviceListResult.Success>(result)
        assertEquals(3, success.view.devices.size)
        assertEquals("device-001", success.view.devices[0].id)
        assertEquals(DeviceType.DESKTOP, success.view.devices[0].type)
    }

    @Test
    fun `returns devices with complete properties`() {
        val client = StubDeviceApiClient(emptyMap())

        val result = client.listDevices(session)

        val success = assertIs<DeviceListResult.Success>(result)
        val device = success.view.devices[0]
        assertEquals("device-001", device.id)
        assertEquals("MacBook Pro", device.label)
        assertEquals(DeviceType.DESKTOP, device.type)
        assertEquals("MacBook Pro 16\"", device.model)
        assertEquals(true, device.isVerified)
        assertEquals(3, device.capabilities.size)
        assertEquals(2, device.keyPackages.size)
        assertEquals("Berlin, Germany", device.location)
    }

    @Test
    fun `returns empty list in list_empty mode`() {
        val client = StubDeviceApiClient(mapOf("WIRE_STUB_MODE" to "list_empty"))

        val result = client.listDevices(session)

        val success = assertIs<DeviceListResult.Success>(result)
        assertEquals(0, success.view.devices.size)
    }

    @Test
    fun `returns device list in list_ok mode`() {
        val client = StubDeviceApiClient(mapOf("WIRE_STUB_MODE" to "list_ok"))

        val result = client.listDevices(session)

        val success = assertIs<DeviceListResult.Success>(result)
        assertEquals(3, success.view.devices.size)
    }

    @Test
    fun `returns not found failure in not_found mode`() {
        val client = StubDeviceApiClient(mapOf("WIRE_STUB_MODE" to "not_found"))

        val result = client.listDevices(session)

        val failure = assertIs<DeviceListResult.Failure>(result)
        assertEquals(DeviceMessages.DEVICE_NOT_FOUND, failure.message)
        assertEquals(DeviceExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `returns server error failure in server_error mode`() {
        val client = StubDeviceApiClient(mapOf("WIRE_STUB_MODE" to "server_error"))

        val result = client.listDevices(session)

        val failure = assertIs<DeviceListResult.Failure>(result)
        assertEquals(DeviceMessages.SERVER_FAILURE, failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `returns unauthorized failure in unauthorized mode`() {
        val client = StubDeviceApiClient(mapOf("WIRE_STUB_MODE" to "unauthorized"))

        val result = client.listDevices(session)

        val failure = assertIs<DeviceListResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `returns device detail by default`() {
        val client = StubDeviceApiClient(emptyMap())

        val result = client.getDeviceDetail(session, "device-001")

        val success = assertIs<DeviceDetailResult.Success>(result)
        assertEquals("device-001", success.view.device.id)
        assertEquals(DeviceType.DESKTOP, success.view.device.type)
        assertEquals(KeyPackageStatus.VALID, success.view.keyPackageStatus)
    }

    @Test
    fun `returns not found for missing device`() {
        val client = StubDeviceApiClient(emptyMap())

        val result = client.getDeviceDetail(session, "device-nonexistent")

        val failure = assertIs<DeviceDetailResult.Failure>(result)
        assertEquals(DeviceMessages.DEVICE_NOT_FOUND, failure.message)
        assertEquals(DeviceExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `returns not found failure for detail in not_found mode`() {
        val client = StubDeviceApiClient(mapOf("WIRE_STUB_MODE" to "not_found"))

        val result = client.getDeviceDetail(session, "device-001")

        val failure = assertIs<DeviceDetailResult.Failure>(result)
        assertEquals(DeviceMessages.DEVICE_NOT_FOUND, failure.message)
        assertEquals(DeviceExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `returns server error failure for detail in server_error mode`() {
        val client = StubDeviceApiClient(mapOf("WIRE_STUB_MODE" to "server_error"))

        val result = client.getDeviceDetail(session, "device-001")

        val failure = assertIs<DeviceDetailResult.Failure>(result)
        assertEquals(DeviceMessages.SERVER_FAILURE, failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `returns unauthorized failure for detail in unauthorized mode`() {
        val client = StubDeviceApiClient(mapOf("WIRE_STUB_MODE" to "unauthorized"))

        val result = client.getDeviceDetail(session, "device-001")

        val failure = assertIs<DeviceDetailResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `returns device list for user by default`() {
        val client = StubDeviceApiClient(emptyMap())

        val result = client.listDevicesForUser(session, "user-001")

        val success = assertIs<DeviceListResult.Success>(result)
        assertEquals(3, success.view.devices.size)
        assertEquals("device-001", success.view.devices[0].id)
    }

    @Test
    fun `returns empty list for user in list_empty mode`() {
        val client = StubDeviceApiClient(mapOf("WIRE_STUB_MODE" to "list_empty"))

        val result = client.listDevicesForUser(session, "user-001")

        val success = assertIs<DeviceListResult.Success>(result)
        assertEquals(0, success.view.devices.size)
    }

    @Test
    fun `returns device list for user in list_ok mode`() {
        val client = StubDeviceApiClient(mapOf("WIRE_STUB_MODE" to "list_ok"))

        val result = client.listDevicesForUser(session, "user-001")

        val success = assertIs<DeviceListResult.Success>(result)
        assertEquals(3, success.view.devices.size)
    }

    @Test
    fun `returns not found failure for user list in not_found mode`() {
        val client = StubDeviceApiClient(mapOf("WIRE_STUB_MODE" to "not_found"))

        val result = client.listDevicesForUser(session, "user-001")

        val failure = assertIs<DeviceListResult.Failure>(result)
        assertEquals(DeviceMessages.DEVICE_NOT_FOUND, failure.message)
        assertEquals(DeviceExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `returns server error failure for user list in server_error mode`() {
        val client = StubDeviceApiClient(mapOf("WIRE_STUB_MODE" to "server_error"))

        val result = client.listDevicesForUser(session, "user-001")

        val failure = assertIs<DeviceListResult.Failure>(result)
        assertEquals(DeviceMessages.SERVER_FAILURE, failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `returns unauthorized failure for user list in unauthorized mode`() {
        val client = StubDeviceApiClient(mapOf("WIRE_STUB_MODE" to "unauthorized"))

        val result = client.listDevicesForUser(session, "user-001")

        val failure = assertIs<DeviceListResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `returns success message on device deletion by default`() {
        val client = StubDeviceApiClient(emptyMap())

        val result = client.deleteDevice(session, "device-001")

        val success = assertIs<DeviceDeleteResult.Success>(result)
        assertEquals("Device deleted successfully.", success.message)
    }

    @Test
    fun `returns not found failure for deletion in not_found mode`() {
        val client = StubDeviceApiClient(mapOf("WIRE_STUB_MODE" to "not_found"))

        val result = client.deleteDevice(session, "device-001")

        val failure = assertIs<DeviceDeleteResult.Failure>(result)
        assertEquals(DeviceMessages.DEVICE_NOT_FOUND, failure.message)
        assertEquals(DeviceExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `returns server error failure for deletion in server_error mode`() {
        val client = StubDeviceApiClient(mapOf("WIRE_STUB_MODE" to "server_error"))

        val result = client.deleteDevice(session, "device-001")

        val failure = assertIs<DeviceDeleteResult.Failure>(result)
        assertEquals(DeviceMessages.DELETE_SERVER_FAILURE, failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `returns unauthorized failure for deletion in unauthorized mode`() {
        val client = StubDeviceApiClient(mapOf("WIRE_STUB_MODE" to "unauthorized"))

        val result = client.deleteDevice(session, "device-001")

        val failure = assertIs<DeviceDeleteResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }
}
