package wirecli.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeviceContractsTest {
    @Test
    fun `Device model includes all required fields`() {
        val device =
            Device(
                id = "device-001",
                label = "My Desktop",
                type = DeviceType.DESKTOP,
                model = "MacBook Pro",
                fingerprint = "a1b2c3d4e5f6g7h8i9j0",
                isVerified = true,
                lastActive = "2025-03-13T10:30:00Z",
                registrationTime = "2025-01-01T00:00:00Z",
                capabilities = listOf("MLS", "PROTEUS"),
                keyPackages = listOf("key1", "key2"),
                location = "San Francisco, CA",
            )

        assertEquals("device-001", device.id)
        assertEquals("My Desktop", device.label)
        assertEquals(DeviceType.DESKTOP, device.type)
        assertEquals("MacBook Pro", device.model)
        assertEquals("a1b2c3d4e5f6g7h8i9j0", device.fingerprint)
        assertEquals(true, device.isVerified)
        assertEquals("2025-03-13T10:30:00Z", device.lastActive)
        assertEquals("2025-01-01T00:00:00Z", device.registrationTime)
        assertEquals(listOf("MLS", "PROTEUS"), device.capabilities)
        assertEquals(listOf("key1", "key2"), device.keyPackages)
        assertEquals("San Francisco, CA", device.location)
    }

    @Test
    fun `Device model has sensible defaults for optional fields`() {
        val device =
            Device(
                id = "device-001",
                type = DeviceType.DESKTOP,
                fingerprint = "a1b2c3d4e5f6g7h8i9j0",
                lastActive = "2025-03-13T10:30:00Z",
            )

        assertEquals("device-001", device.id)
        assertEquals(null, device.label)
        assertEquals(DeviceType.DESKTOP, device.type)
        assertEquals(null, device.model)
        assertEquals("a1b2c3d4e5f6g7h8i9j0", device.fingerprint)
        assertEquals(false, device.isVerified)
        assertEquals("2025-03-13T10:30:00Z", device.lastActive)
        assertEquals(null, device.registrationTime)
        assertEquals(emptyList(), device.capabilities)
        assertEquals(emptyList(), device.keyPackages)
        assertEquals(null, device.location)
    }

    @Test
    fun `DeviceListView contains devices list`() {
        val devices =
            listOf(
                Device(
                    id = "device-001",
                    type = DeviceType.DESKTOP,
                    fingerprint = "a1b2c3d4e5f6g7h8i9j0",
                    lastActive = "2025-03-13T10:30:00Z",
                ),
                Device(
                    id = "device-002",
                    type = DeviceType.MOBILE,
                    fingerprint = "b2c3d4e5f6g7h8i9j0k1",
                    lastActive = "2025-03-12T15:45:00Z",
                ),
            )
        val view = DeviceListView(devices = devices)

        assertEquals(2, view.devices.size)
        assertEquals("device-001", view.devices[0].id)
        assertEquals("device-002", view.devices[1].id)
    }

    @Test
    fun `DeviceDetailView contains device and key package status`() {
        val device =
            Device(
                id = "device-001",
                type = DeviceType.DESKTOP,
                fingerprint = "a1b2c3d4e5f6g7h8i9j0",
                lastActive = "2025-03-13T10:30:00Z",
            )
        val view =
            DeviceDetailView(
                device = device,
                keyPackageStatus = KeyPackageStatus.VALID,
            )

        assertEquals("device-001", view.device.id)
        assertEquals(KeyPackageStatus.VALID, view.keyPackageStatus)
    }

    @Test
    fun `DeviceListResult Success exposes view`() {
        val view = DeviceListView(devices = emptyList())
        val result: DeviceListResult = DeviceListResult.Success(view)

        val success = assertIs<DeviceListResult.Success>(result)
        assertEquals(0, success.view.devices.size)
    }

    @Test
    fun `DeviceListResult Failure exposes message and exit code`() {
        val result: DeviceListResult =
            DeviceListResult.Failure(
                message = "Not found",
                exitCode = DeviceExitCodes.NOT_FOUND,
            )

        val failure = assertIs<DeviceListResult.Failure>(result)
        assertEquals("Not found", failure.message)
        assertEquals(DeviceExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `DeviceDetailResult Success exposes view`() {
        val device =
            Device(
                id = "device-001",
                type = DeviceType.DESKTOP,
                fingerprint = "a1b2c3d4e5f6g7h8i9j0",
                lastActive = "2025-03-13T10:30:00Z",
            )
        val view =
            DeviceDetailView(
                device = device,
                keyPackageStatus = KeyPackageStatus.VALID,
            )
        val result: DeviceDetailResult = DeviceDetailResult.Success(view)

        val success = assertIs<DeviceDetailResult.Success>(result)
        assertEquals("device-001", success.view.device.id)
    }

    @Test
    fun `DeviceDetailResult Failure exposes message and exit code`() {
        val result: DeviceDetailResult =
            DeviceDetailResult.Failure(
                message = "Device not found",
                exitCode = DeviceExitCodes.NOT_FOUND,
            )

        val failure = assertIs<DeviceDetailResult.Failure>(result)
        assertEquals("Device not found", failure.message)
        assertEquals(DeviceExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `DeviceDeleteResult Success exposes message`() {
        val result: DeviceDeleteResult = DeviceDeleteResult.Success("Device deleted successfully.")

        val success = assertIs<DeviceDeleteResult.Success>(result)
        assertEquals("Device deleted successfully.", success.message)
    }

    @Test
    fun `DeviceDeleteResult Failure exposes message and exit code`() {
        val result: DeviceDeleteResult =
            DeviceDeleteResult.Failure(
                message = "Cannot delete device",
                exitCode = DeviceExitCodes.PERMISSION_DENIED,
            )

        val failure = assertIs<DeviceDeleteResult.Failure>(result)
        assertEquals("Cannot delete device", failure.message)
        assertEquals(DeviceExitCodes.PERMISSION_DENIED, failure.exitCode)
    }

    @Test
    fun `DeviceExitCodes defines standard exit codes`() {
        assertEquals(0, DeviceExitCodes.OK)
        assertEquals(11, DeviceExitCodes.UNAUTHORIZED)
        assertEquals(12, DeviceExitCodes.PERMISSION_DENIED)
        assertEquals(13, DeviceExitCodes.NOT_FOUND)
        assertEquals(14, DeviceExitCodes.INVALID_INPUT)
    }

    @Test
    fun `DeviceType enum has expected values`() {
        assertEquals("desktop", DeviceType.DESKTOP.value)
        assertEquals("mobile", DeviceType.MOBILE.value)
        assertEquals("unknown", DeviceType.UNKNOWN.value)
    }

    @Test
    fun `KeyPackageStatus enum has expected values`() {
        assertEquals("valid", KeyPackageStatus.VALID.value)
        assertEquals("invalid", KeyPackageStatus.INVALID.value)
        assertEquals("exhausted", KeyPackageStatus.EXHAUSTED.value)
        assertEquals("unknown", KeyPackageStatus.UNKNOWN.value)
    }

    @Test
    fun `DeviceException hierarchy includes all specific exception types`() {
        // Test that all exception types can be created
        val notFound = DeviceException.DeviceNotFound()
        val unauthorized = DeviceException.Unauthorized()
        val permissionDenied = DeviceException.PermissionDenied()
        val invalidInput = DeviceException.InvalidInput()
        val networkFailure = DeviceException.NetworkFailure()
        val serverError = DeviceException.ServerError()
        val unknown = DeviceException.UnknownFailure()

        assertEquals(DeviceMessages.DEVICE_NOT_FOUND, notFound.message)
        assertEquals(DeviceMessages.UNAUTHORIZED_FAILURE, unauthorized.message)
        assertEquals(DeviceMessages.PERMISSION_DENIED, permissionDenied.message)
        assertEquals(DeviceMessages.INVALID_INPUT, invalidInput.message)
        assertEquals(DeviceMessages.NETWORK_FAILURE, networkFailure.message)
        assertEquals(DeviceMessages.SERVER_FAILURE, serverError.message)
        assertEquals(DeviceMessages.UNKNOWN_FAILURE, unknown.message)
    }

    @Test
    fun `DeviceException can wrap cause throwable`() {
        val cause = RuntimeException("Original error")
        val exception = DeviceException.NetworkFailure("Custom message", cause)

        assertEquals("Custom message", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `DeviceApiClient interface defines required methods`() {
        // This test ensures the interface contract is upheld
        // The interface should have: listDevices, listDevicesForUser, getDeviceDetail, deleteDevice
        val methodNames = DeviceApiClient::class.java.methods.map { it.name }

        assert(methodNames.contains("listDevices"))
        assert(methodNames.contains("listDevicesForUser"))
        assert(methodNames.contains("getDeviceDetail"))
        assert(methodNames.contains("deleteDevice"))
    }

    @Test
    fun `DeviceService interface defines required methods`() {
        // This test ensures the interface contract is upheld
        // The interface should have: listCurrentDevices, listDevicesForUser, getDetail, remove
        val methodNames = DeviceService::class.java.methods.map { it.name }

        assert(methodNames.contains("listCurrentDevices"))
        assert(methodNames.contains("listDevicesForUser"))
        assert(methodNames.contains("getDetail"))
        assert(methodNames.contains("remove"))
    }
}
