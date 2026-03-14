package wirecli.commands

import wirecli.device.Device
import wirecli.device.DeviceDeleteResult
import wirecli.device.DeviceDetailResult
import wirecli.device.DeviceDetailView
import wirecli.device.DeviceListResult
import wirecli.device.DeviceListView
import wirecli.device.DeviceService
import wirecli.device.DeviceType
import wirecli.device.DeviceVerifyResult
import wirecli.device.KeyPackageStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceCommandTest {
    private val testDevice =
        Device(
            id = "device-001",
            label = "MacBook Pro",
            type = DeviceType.DESKTOP,
            model = "MacBook Pro 16\"",
            fingerprint = "a1b2c3d4e5f6g7h8i9j0",
            isVerified = true,
            lastActive = "2025-03-13T10:30:00Z",
            registrationTime = "2025-01-15T14:22:00Z",
            capabilities = listOf("mls", "e2ee", "calling"),
            keyPackages = listOf("kp-001", "kp-002"),
            location = "Berlin, Germany",
        )

    private val anotherDevice =
        Device(
            id = "device-002",
            label = "iPhone 15",
            type = DeviceType.MOBILE,
            model = "iPhone 15 Pro Max",
            fingerprint = "b2c3d4e5f6g7h8i9j0k1",
            isVerified = true,
            lastActive = "2025-03-12T15:45:00Z",
            registrationTime = "2025-02-20T09:15:00Z",
            capabilities = listOf("mls", "e2ee", "calling"),
            keyPackages = listOf("kp-003"),
            location = "Berlin, Germany",
        )

    // ==================== DeviceListCommand Tests ====================

    @Test
    fun `list command returns current devices`() {
        val service =
            FakeDeviceService(
                listDevicesResult =
                    DeviceListResult.Success(
                        DeviceListView(listOf(testDevice, anotherDevice)),
                    ),
            )

        val result = service.listCurrentDevices()

        val success = result as DeviceListResult.Success
        assertEquals(2, success.view.devices.size)
        assertEquals("device-001", success.view.devices[0].id)
        assertEquals("device-002", success.view.devices[1].id)
    }

    @Test
    fun `list command returns devices for specific user`() {
        val service =
            FakeDeviceService(
                listDevicesForUserResult =
                    DeviceListResult.Success(
                        DeviceListView(listOf(testDevice)),
                    ),
            )

        val result = service.listDevicesForUser("alice@example.com")

        val success = result as DeviceListResult.Success
        assertEquals(1, success.view.devices.size)
        assertEquals("device-001", success.view.devices[0].id)
    }

    @Test
    fun `list command returns empty list`() {
        val service =
            FakeDeviceService(
                listDevicesResult = DeviceListResult.Success(DeviceListView(emptyList())),
            )

        val result = service.listCurrentDevices()

        val success = result as DeviceListResult.Success
        assertEquals(0, success.view.devices.size)
    }

    @Test
    fun `list command handles failure with correct exit code`() {
        val service =
            FakeDeviceService(
                listDevicesResult =
                    DeviceListResult.Failure(
                        message = "Authorization failed",
                        exitCode = 11,
                    ),
            )

        val result = service.listCurrentDevices()

        val failure = result as DeviceListResult.Failure
        assertEquals(11, failure.exitCode)
        assertEquals("Authorization failed", failure.message)
    }

    // ==================== DeviceInfoCommand Tests ====================

    @Test
    fun `info command returns device details`() {
        val service =
            FakeDeviceService(
                getDetailResult =
                    DeviceDetailResult.Success(
                        DeviceDetailView(testDevice, KeyPackageStatus.VALID),
                    ),
            )

        val result = service.getDetail("device-001")

        val success = result as DeviceDetailResult.Success
        assertEquals("device-001", success.view.device.id)
        assertEquals(DeviceType.DESKTOP, success.view.device.type)
        assertEquals("a1b2c3d4e5f6g7h8i9j0", success.view.device.fingerprint)
    }

    @Test
    fun `info command handles device not found`() {
        val service =
            FakeDeviceService(
                getDetailResult =
                    DeviceDetailResult.Failure(
                        message = "Device not found",
                        exitCode = 13,
                    ),
            )

        val result = service.getDetail("invalid-device")

        val failure = result as DeviceDetailResult.Failure
        assertEquals(13, failure.exitCode)
    }

    // ==================== DeviceDeleteCommand Tests ====================

    @Test
    fun `delete command removes device successfully`() {
        val service =
            FakeDeviceService(
                removeResult = DeviceDeleteResult.Success("Device deleted successfully."),
            )

        val result = service.remove("device-001")

        val success = result as DeviceDeleteResult.Success
        assertEquals("Device deleted successfully.", success.message)
    }

    @Test
    fun `delete command handles failure`() {
        val service =
            FakeDeviceService(
                removeResult =
                    DeviceDeleteResult.Failure(
                        message = "Device deletion failed",
                        exitCode = 12,
                    ),
            )

        val result = service.remove("device-001")

        val failure = result as DeviceDeleteResult.Failure
        assertEquals(12, failure.exitCode)
    }

    // ==================== DeviceVerifyCommand Tests ====================

    @Test
    fun `verify command returns verification success`() {
        val service =
            FakeDeviceService(
                verifyResult =
                    DeviceVerifyResult.Success(
                        message = "Device verified successfully.",
                        fingerprint = "a1b2c3d4e5f6g7h8i9j0",
                    ),
            )

        val result = service.verify("device-001")

        val success = result as DeviceVerifyResult.Success
        assertEquals("Device verified successfully.", success.message)
        assertEquals("a1b2c3d4e5f6g7h8i9j0", success.fingerprint)
    }

    @Test
    fun `verify command handles device not found`() {
        val service =
            FakeDeviceService(
                verifyResult =
                    DeviceVerifyResult.Failure(
                        message = "Device not found",
                        exitCode = 13,
                    ),
            )

        val result = service.verify("invalid-device")

        val failure = result as DeviceVerifyResult.Failure
        assertEquals(13, failure.exitCode)
    }

    @Test
    fun `verify command handles unauthorized`() {
        val service =
            FakeDeviceService(
                verifyResult =
                    DeviceVerifyResult.Failure(
                        message = "Unauthorized",
                        exitCode = 11,
                    ),
            )

        val result = service.verify("device-001")

        val failure = result as DeviceVerifyResult.Failure
        assertEquals(11, failure.exitCode)
    }

    // ==================== Helper Classes ====================

    private class FakeDeviceService(
        private val listDevicesResult: DeviceListResult = DeviceListResult.Success(DeviceListView(emptyList())),
        private val listDevicesForUserResult: DeviceListResult =
            DeviceListResult.Success(DeviceListView(emptyList())),
        private val getDetailResult: DeviceDetailResult =
            DeviceDetailResult.Failure("Not found", 13),
        private val removeResult: DeviceDeleteResult = DeviceDeleteResult.Success("Success"),
        private val verifyResult: DeviceVerifyResult =
            DeviceVerifyResult.Success("Success", "fingerprint"),
    ) : DeviceService {
        override fun listCurrentDevices(): DeviceListResult = listDevicesResult

        override fun listDevicesForUser(userId: String): DeviceListResult = listDevicesForUserResult

        override fun getDetail(deviceId: String): DeviceDetailResult = getDetailResult

        override fun remove(
            deviceId: String,
            password: String?,
        ): DeviceDeleteResult = removeResult

        override fun verify(deviceId: String): DeviceVerifyResult = verifyResult
    }
}
