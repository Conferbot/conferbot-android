package com.conferbot.sdk.core.queue

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for NetworkMonitor
 * Tests connection state detection, state change callbacks, and network type detection
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkMonitorTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkMonitor: NetworkMonitor
    private var capturedCallback: ConnectivityManager.NetworkCallback? = null

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        connectivityManager = mockk(relaxed = true)

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager

        // Capture the network callback when registered
        every {
            connectivityManager.registerNetworkCallback(any<NetworkRequest>(), any<ConnectivityManager.NetworkCallback>())
        } answers {
            capturedCallback = secondArg()
        }

        // Default to offline
        every { connectivityManager.activeNetwork } returns null
        every { connectivityManager.getNetworkCapabilities(any()) } returns null

        networkMonitor = NetworkMonitor(context)
    }

    @After
    fun tearDown() {
        capturedCallback = null
    }

    // ==================== INITIALIZATION TESTS ====================

    @Test
    fun `initial state reflects current network status - offline`() {
        // Given - Default mocks return offline

        // Then
        assertThat(networkMonitor.isCurrentlyOnline()).isFalse()
        assertThat(networkMonitor.networkType.value).isEqualTo(NetworkType.NONE)
    }

    @Test
    fun `initial state reflects current network status - online WiFi`() {
        // Given
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false

        // When
        val monitor = NetworkMonitor(context)

        // Then
        assertThat(monitor.isCurrentlyOnline()).isTrue()
        assertThat(monitor.networkType.value).isEqualTo(NetworkType.WIFI)
    }

    @Test
    fun `initial state reflects current network status - online cellular`() {
        // Given
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false

        // When
        val monitor = NetworkMonitor(context)

        // Then
        assertThat(monitor.isCurrentlyOnline()).isTrue()
        assertThat(monitor.networkType.value).isEqualTo(NetworkType.CELLULAR)
    }

    // ==================== START MONITORING TESTS ====================

    @Test
    fun `startMonitoring registers network callback`() {
        // When
        networkMonitor.startMonitoring()

        // Then
        verify { connectivityManager.registerNetworkCallback(any<NetworkRequest>(), any<ConnectivityManager.NetworkCallback>()) }
        assertThat(capturedCallback).isNotNull()
    }

    @Test
    fun `startMonitoring does nothing when already monitoring`() {
        // Given
        networkMonitor.startMonitoring()
        clearMocks(connectivityManager, answers = false)

        // When
        networkMonitor.startMonitoring()

        // Then
        verify(exactly = 0) { connectivityManager.registerNetworkCallback(any<NetworkRequest>(), any<ConnectivityManager.NetworkCallback>()) }
    }

    // ==================== STOP MONITORING TESTS ====================

    @Test
    fun `stopMonitoring unregisters network callback`() {
        // Given
        networkMonitor.startMonitoring()

        // When
        networkMonitor.stopMonitoring()

        // Then
        verify { connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) }
    }

    @Test
    fun `stopMonitoring does nothing when not monitoring`() {
        // When
        networkMonitor.stopMonitoring()

        // Then
        verify(exactly = 0) { connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) }
    }

    // ==================== NETWORK CALLBACK TESTS ====================

    @Test
    fun `onAvailable sets isOnline to true`() = runTest {
        // Given
        networkMonitor.startMonitoring()
        val network = mockk<Network>()

        // When
        capturedCallback?.onAvailable(network)

        // Then
        assertThat(networkMonitor.isOnline.value).isTrue()
    }

    @Test
    fun `onLost sets isOnline to false when no other network`() = runTest {
        // Given
        networkMonitor.startMonitoring()
        val network = mockk<Network>()

        // Simulate network available then lost
        capturedCallback?.onAvailable(network)
        assertThat(networkMonitor.isOnline.value).isTrue()

        // Ensure no other network is available
        every { connectivityManager.activeNetwork } returns null
        every { connectivityManager.getNetworkCapabilities(any()) } returns null

        // When
        capturedCallback?.onLost(network)

        // Then
        assertThat(networkMonitor.isOnline.value).isFalse()
        assertThat(networkMonitor.networkType.value).isEqualTo(NetworkType.NONE)
    }

    @Test
    fun `onLost keeps online if another network is available`() = runTest {
        // Given
        networkMonitor.startMonitoring()
        val network1 = mockk<Network>()
        val network2 = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()

        // Start with network available
        capturedCallback?.onAvailable(network1)

        // Configure another network to be available
        every { connectivityManager.activeNetwork } returns network2
        every { connectivityManager.getNetworkCapabilities(network2) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true

        // When - First network lost but second available
        capturedCallback?.onLost(network1)

        // Then - Should stay online
        assertThat(networkMonitor.isOnline.value).isTrue()
    }

    @Test
    fun `onCapabilitiesChanged updates online status with validation`() = runTest {
        // Given
        networkMonitor.startMonitoring()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false

        // When
        capturedCallback?.onCapabilitiesChanged(network, capabilities)

        // Then
        assertThat(networkMonitor.isOnline.value).isTrue()
    }

    @Test
    fun `onCapabilitiesChanged does not set online without validation`() = runTest {
        // Given
        networkMonitor.startMonitoring()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()

        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false

        // When
        capturedCallback?.onCapabilitiesChanged(network, capabilities)

        // Then - Should remain offline without validated capability
        assertThat(networkMonitor.isOnline.value).isFalse()
    }

    @Test
    fun `onUnavailable sets isOnline to false`() = runTest {
        // Given
        networkMonitor.startMonitoring()
        val network = mockk<Network>()
        capturedCallback?.onAvailable(network)

        // When
        capturedCallback?.onUnavailable()

        // Then
        assertThat(networkMonitor.isOnline.value).isFalse()
        assertThat(networkMonitor.networkType.value).isEqualTo(NetworkType.NONE)
    }

    // ==================== NETWORK TYPE DETECTION TESTS ====================

    @Test
    fun `detects WiFi network type`() = runTest {
        // Given
        networkMonitor.startMonitoring()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false

        // When
        capturedCallback?.onCapabilitiesChanged(network, capabilities)

        // Then
        assertThat(networkMonitor.networkType.value).isEqualTo(NetworkType.WIFI)
    }

    @Test
    fun `detects cellular network type`() = runTest {
        // Given
        networkMonitor.startMonitoring()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false

        // When
        capturedCallback?.onCapabilitiesChanged(network, capabilities)

        // Then
        assertThat(networkMonitor.networkType.value).isEqualTo(NetworkType.CELLULAR)
    }

    @Test
    fun `detects ethernet network type`() = runTest {
        // Given
        networkMonitor.startMonitoring()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns true

        // When
        capturedCallback?.onCapabilitiesChanged(network, capabilities)

        // Then
        assertThat(networkMonitor.networkType.value).isEqualTo(NetworkType.ETHERNET)
    }

    @Test
    fun `detects other network type when no known transport`() = runTest {
        // Given
        networkMonitor.startMonitoring()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false

        // When
        capturedCallback?.onCapabilitiesChanged(network, capabilities)

        // Then
        assertThat(networkMonitor.networkType.value).isEqualTo(NetworkType.OTHER)
    }

    // ==================== STATE FLOW TESTS ====================

    @Test
    fun `isOnline StateFlow emits state changes`() = runTest {
        // Given
        networkMonitor.startMonitoring()
        val network = mockk<Network>()

        // When & Then
        networkMonitor.isOnline.test {
            assertThat(awaitItem()).isFalse() // Initial state

            capturedCallback?.onAvailable(network)
            assertThat(awaitItem()).isTrue()

            every { connectivityManager.activeNetwork } returns null
            every { connectivityManager.getNetworkCapabilities(any()) } returns null
            capturedCallback?.onLost(network)
            assertThat(awaitItem()).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `networkType StateFlow emits type changes`() = runTest {
        // Given
        networkMonitor.startMonitoring()
        val network = mockk<Network>()
        val wifiCapabilities = mockk<NetworkCapabilities>()
        val cellularCapabilities = mockk<NetworkCapabilities>()

        setupCapabilities(wifiCapabilities, wifi = true)
        setupCapabilities(cellularCapabilities, cellular = true)

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns wifiCapabilities

        // When & Then
        networkMonitor.networkType.test {
            assertThat(awaitItem()).isEqualTo(NetworkType.NONE) // Initial

            capturedCallback?.onCapabilitiesChanged(network, wifiCapabilities)
            assertThat(awaitItem()).isEqualTo(NetworkType.WIFI)

            every { connectivityManager.getNetworkCapabilities(network) } returns cellularCapabilities
            capturedCallback?.onCapabilitiesChanged(network, cellularCapabilities)
            assertThat(awaitItem()).isEqualTo(NetworkType.CELLULAR)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== METERED CONNECTION TESTS ====================

    @Test
    fun `isMeteredConnection returns true for metered network`() {
        // Given
        every { connectivityManager.isActiveNetworkMetered } returns true

        // Then
        assertThat(networkMonitor.isMeteredConnection()).isTrue()
    }

    @Test
    fun `isMeteredConnection returns false for unmetered network`() {
        // Given
        every { connectivityManager.isActiveNetworkMetered } returns false

        // Then
        assertThat(networkMonitor.isMeteredConnection()).isFalse()
    }

    // ==================== ISSCURRENTLYONLINE TESTS ====================

    @Test
    fun `isCurrentlyOnline returns current online state`() = runTest {
        // Given
        networkMonitor.startMonitoring()
        val network = mockk<Network>()

        // Initially offline
        assertThat(networkMonitor.isCurrentlyOnline()).isFalse()

        // When online
        capturedCallback?.onAvailable(network)
        assertThat(networkMonitor.isCurrentlyOnline()).isTrue()
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    fun `startMonitoring handles registration exception gracefully`() {
        // Given
        every {
            connectivityManager.registerNetworkCallback(any<NetworkRequest>(), any<ConnectivityManager.NetworkCallback>())
        } throws SecurityException("Permission denied")

        // When - Should not throw
        networkMonitor.startMonitoring()

        // Then - Monitor should still be usable
        assertThat(networkMonitor.isCurrentlyOnline()).isFalse()
    }

    @Test
    fun `stopMonitoring handles unregistration exception gracefully`() {
        // Given
        networkMonitor.startMonitoring()
        every {
            connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>())
        } throws IllegalArgumentException("Callback not registered")

        // When - Should not throw
        networkMonitor.stopMonitoring()

        // Then - No exception
    }

    // ==================== NETWORK TYPE ENUM TESTS ====================

    @Test
    fun `NetworkType enum has all expected values`() {
        // Then
        assertThat(NetworkType.values()).asList().containsExactly(
            NetworkType.NONE,
            NetworkType.WIFI,
            NetworkType.CELLULAR,
            NetworkType.ETHERNET,
            NetworkType.OTHER
        )
    }

    // ==================== HELPER METHODS ====================

    private fun setupCapabilities(
        capabilities: NetworkCapabilities,
        wifi: Boolean = false,
        cellular: Boolean = false,
        ethernet: Boolean = false
    ) {
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns wifi
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns cellular
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns ethernet
    }
}
