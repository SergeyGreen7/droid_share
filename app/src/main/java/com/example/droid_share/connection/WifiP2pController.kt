package com.example.droid_share.connection

import android.annotation.SuppressLint
import android.net.MacAddress
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.*
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.droid_share.Utils
import java.net.NetworkInterface
import java.util.Collections


class WifiP2pController(
    private val manager: WifiP2pManager,
    private val channel: Channel)
{

    companion object {
        private const val TAG = "WifiP2pController"
        private const val SERVICE_TYPE = "_wifip2p._tcp"
    }

    private val SERVER_PORT = 8888;

    private val buddies = mutableMapOf<String, String>()

    @SuppressLint("MissingPermission")
    fun registerP2rService() {
        val record = HashMap<String, String>();

        val interfaces: List<NetworkInterface> =
            Collections.list(NetworkInterface.getNetworkInterfaces())

        record["buddyname"] = "NS220RE-${(Math.random() * 1000000).toInt()}"
        record["available"] = "visible"

        val instanceName = "NS220RE-${(Math.random() * 1000000).toInt()}"

        val serviceInfo = WifiP2pDnsSdServiceInfo
            .newInstance(instanceName, SERVICE_TYPE, record)

        manager.addLocalService(channel, serviceInfo, object: ActionListener {

            override fun onSuccess() {
                // Command successful! Code isn't necessarily needed here,
                // Unless you want to update the UI or add logging statements.
                Log.d(TAG, "addLocalService(), onSuccess")
            }

            override fun onFailure(code: Int) {
                Log.d(TAG, "addLocalService(), onFailure, ${getErrorCodeDescription(code)}")
            }
        })
    }

    fun unregisterP2pService() {
        manager.clearLocalServices(channel, object: ActionListener {

            override fun onSuccess() {
                // Command successful! Code isn't necessarily needed here,
                // Unless you want to update the UI or add logging statements.
                Log.d(TAG, "clearLocalServices(), onSuccess")
            }

            override fun onFailure(code: Int) {
                Log.d(TAG, "clearLocalServices(), onFailure, ${getErrorCodeDescription(code)}")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun discoverP2pService() {
        /* Callback includes:
         * fullDomain: full domain name: e.g. "printer._ipp._tcp.local."
         * record: TXT record dta as a map of key/value pairs.
         * device: The device running the advertised service.
         */
        val txtListener = DnsSdTxtRecordListener { fullDomain, record, device ->
            Log.d(TAG, "DnsSdTxtRecordListener, DnsSdTxtRecord available:")
            Log.d(TAG, "    fullDomain: $fullDomain")
            Log.d(TAG, "    record: $record")
            Log.d(TAG, "    device: $device")
            record["buddyname"]?.also {
                buddies[device.deviceAddress] = it
            }
        }

        val servListener = DnsSdServiceResponseListener { instanceName, registrationType, resourceType ->
            // Update the device name with the human-friendly version from
            // the DnsTxtRecord, assuming one arrived.
            Log.d(TAG, "DnsSdServiceResponseListener, DnsSdService available:")
            Log.d(TAG, "    instanceName: $instanceName")
            Log.d(TAG, "    registrationType: $registrationType")
            Log.d(TAG, "    resourceType: $resourceType")

            resourceType.deviceName = buddies[resourceType.deviceAddress] ?: resourceType.deviceName

            // Add to the custom adapter defined specifically for showing
            // wifi devices.
//            val fragment = fragmentManager
//                .findFragmentById(R.id.frag_peerlist) as WiFiDirectServicesList
//            (fragment.listAdapter as WiFiDevicesAdapter).apply {
//                add(resourceType)
//                notifyDataSetChanged()
//            }

//            Log.d(TAG, "onBonjourServiceAvailable $instanceName")
        }

        manager.setDnsSdResponseListeners(channel, servListener, txtListener)

        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        manager.addServiceRequest(channel, serviceRequest, object : ActionListener {
                override fun onSuccess() {
                    // Success!
                    Log.d(TAG, "addServiceRequest(), onSuccess")
                }

                override fun onFailure(code: Int) {
                    // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                    Log.d(TAG, "addServiceRequest(), onFailure, ${getErrorCodeDescription(code)}")
                }
            }
        )
        manager.discoverServices(channel,  object : ActionListener {
            override fun onSuccess() {
                // Success!
                Log.d(TAG, "discoverServices(), onSuccess")
            }

            override fun onFailure(code: Int) {
                // Command failed. Check for P2P_UNSUPPORTED, ERROR, or BUSY
                Log.d(TAG, "discoverServices(), onFailure, ${getErrorCodeDescription(code)}")
            }
        })

    }

    fun stopDiscoveryP2pServices() {
        manager.stopPeerDiscovery(channel,  object : ActionListener {
            override fun onSuccess() {
                // Success!
                Log.d(TAG, "stopPeerDiscovery(), onSuccess")
            }

            override fun onFailure(code: Int) {
                // Command failed. Check for P2P_UNSUPPORTED, ERROR, or BUSY
                Log.d(TAG, "stopPeerDiscovery(), onFailure, ${getErrorCodeDescription(code)}")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun discoverP2pPeers() {
        manager.discoverPeers(channel, object : ActionListener {

            override fun onSuccess() {
                Log.d(TAG, "discoverPeers(), onSuccess")
            }

            override fun onFailure(code: Int) {
                Log.d(TAG, "discoverPeers(), onFailure, ${getErrorCodeDescription(code)}")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun requestPeers(listener: PeerListListener) {
        manager.requestPeers(channel, listener)
    }

    @SuppressLint("MissingPermission")
    fun connectP2pDevice(deviceAddress: String, name: String) {
        val config = WifiP2pConfig()
        config.deviceAddress = deviceAddress
        config.wps.setup = WpsInfo.PBC
        config.groupOwnerIntent = 0

        manager.connect(channel, config, object : ActionListener {
            override fun onSuccess() {
                // Success!
                Log.d(TAG, "WifiP2pController, connect(), onSuccess")
                val wlanInetAddress = Utils.getInetAddress("wlan")
                val p2pInetAddress = Utils.getInetAddress("p2p")
                if (wlanInetAddress != null) {
                    Log.d(TAG, "WifiP2pController, WLAN IP = ${wlanInetAddress.hostAddress}")
                }
                if (p2pInetAddress != null) {
                    Log.d(TAG, "WifiP2pController, P2P IP = ${p2pInetAddress.hostAddress}")
                }
            }

            override fun onFailure(code: Int) {
                // Command failed. Check for P2P_UNSUPPORTED, ERROR, or BUSY
                Log.d(TAG, "connect(), onFailure, ${getErrorCodeDescription(code)}")
            }
        })
    }

    fun disconnect(device: WifiP2pDevice?) {
        if (device == null ||
            device.status == WifiP2pDevice.CONNECTED) {
            removeP2pGroup()
        } else if (
            device.status == WifiP2pDevice.INVITED ||
            device.status == WifiP2pDevice.AVAILABLE) {
            cancelP2pConnection()
        }
    }

    fun cancelP2pConnection() {
        manager.cancelConnect(channel, object : ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "cancelConnect(), onSuccess")
            }

            override fun onFailure(code: Int) {
                Log.d(TAG, "cancelConnect(), onFailure, ${getErrorCodeDescription(code)}")
            }
        })
    }

    fun requestConnectionInfo(listener: ConnectionInfoListener) {
        manager.requestConnectionInfo(channel, listener)
    }

    @SuppressLint("MissingPermission")
    fun createP2pGroup(deviceAddress: String, name: String) {

        val builder = WifiP2pConfig.Builder()
            .setNetworkName("DIRECT-AQShare_Group111")
            .setPassphrase("1234567890")

//        val config = WifiP2pConfig()
//        config.deviceAddress =
//        config.wps.setup = WpsInfo.PBC
//        config.groupOwnerIntent = 0

        manager.createGroup(channel, builder.build(), object : ActionListener {
        //manager.createGroup(channel, object : ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "createGroup(), onSuccess")
            }

            override fun onFailure(code: Int) {
                Log.d(TAG, "createGroup(), onFailure, ${getErrorCodeDescription(code)}")
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun removeP2pClient(deviceAddress: String) {
        manager.removeClient(channel, MacAddress.fromString(deviceAddress), object : ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "removeClient(), onSuccess")
            }

            override fun onFailure(code: Int) {
                Log.d(TAG, "removeClient(), onFailure, ${getErrorCodeDescription(code)}")
            }
        })
    }

    fun removeP2pGroup() {
        manager.removeGroup(channel,object : ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "removeGroup, onSuccess")
            }

            override fun onFailure(code: Int) {
                Log.d(TAG, "removeGroup(), onFailure, ${getErrorCodeDescription(code)}")
            }
        })
    }

    private fun getErrorCodeDescription(code: Int): String {
        when (code) {
            P2P_UNSUPPORTED -> return "operation failed because p2p is unsupported on the device"
            ERROR -> return "operation failed due to an internal error"
            BUSY -> return "operation failed because the framework is busy and unable to service the request"
            NO_SERVICE_REQUESTS -> return "the discoverServices failed because no service requests are added"
        }
        return "Error code description is not found"
    }

}