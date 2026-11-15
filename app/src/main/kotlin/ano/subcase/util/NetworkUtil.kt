package ano.subcase.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import ano.subcase.GlobalStatus
import ano.subcase.caseApp
import timber.log.Timber
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Enumeration

object NetworkUtil {
    val connectivity = caseApp.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            val capabilities = connectivity.getNetworkCapabilities(network)
            if (capabilities == null) {
                Timber.d("Network available : $network , capabilities is null")
                return
            }

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    Timber.d("Network available : $network , type is WIFI")
                    GlobalStatus.isWifi.value = true
                    GlobalStatus.lanIP.value = getLanIp() ?: ""
                }

                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    Timber.d("Network available : $network , type is CELLULAR")
                }

                else -> {
                    Timber.d("Network available : $network , type is unknown")
                }
            }
        }

        override fun onLost(network: android.net.Network) {
            val capabilities = connectivity.getNetworkCapabilities(network)
            if (capabilities == null) {
                Timber.d("Network lost : $network , capabilities is null")
                return
            }

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    Timber.d("Network lost : $network , type is WIFI")
                    GlobalStatus.isWifi.value = false
                }

                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    Timber.d("Network lost : $network , type is CELLULAR")
                }

                else -> {
                    Timber.d("Network lost : $network , type is unknown")
                }
            }
        }

        override fun onCapabilitiesChanged(
            network: android.net.Network,
            networkCapabilities: NetworkCapabilities
        ) {
            Timber.d("Network capabilities changed")
        }
    }
    private val request = NetworkRequest.Builder().apply {
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
    }.build()

    fun startObserve() {
        connectivity.registerNetworkCallback(request, callback)
    }

    fun stopObserve() {
        connectivity.unregisterNetworkCallback(callback)
    }

    fun hasWifi(): Boolean {
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
    }

    fun getLanIp(): String? {
        try {
            val en: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf: NetworkInterface = en.nextElement()
                val enumIpAddr: Enumeration<InetAddress> = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress: InetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress?.toString()
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

    /** 启动 Node 引擎前预检端口，避免 Node 的 EADDRINUSE 被吞掉后服务假运行。 */
    fun isPortAvailable(host: String, port: Int): Boolean = try {
        ServerSocket().use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(host, port), 1)
        }
        true
    } catch (_: Exception) {
        false
    }

    /** Node 引擎的 HTTP 服务是否已经可以接受连接。 */
    fun isPortOpen(port: Int, host: String = "127.0.0.1"): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 300)
        }
        true
    } catch (_: Exception) {
        false
    }

}
