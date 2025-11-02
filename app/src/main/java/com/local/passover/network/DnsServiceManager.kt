package com.local.passover.network

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class DnsServiceManager @Inject constructor(
    private  val nsdManager: NsdManager
) {
    private val TAG = "DnsServiceManager"
    private val serviceType = "_passover._tcp"
    private val serviceName = "Passover"

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun findService(deviceId: String): NsdServiceInfo? =
        suspendCancellableCoroutine {continuation ->
            var discoveryListener: NsdManager.DiscoveryListener? = null

            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                    Timber.tag(TAG).e("Resolve failed for ${service.serviceName}: $errorCode")
                    if (continuation.isActive) continuation.resume(null)
                }
                override fun onServiceResolved(service: NsdServiceInfo) {
                    Timber.tag(TAG).i("Service resolved: ${service.serviceName}")
                    discoveryListener?.let{ nsdManager.stopServiceDiscovery(it)}
                    if (continuation.isActive) continuation.resume(service)
                }
            }

            discoveryListener = object : NsdManager.DiscoveryListener{
                override fun onDiscoveryStarted(serviceType: String) {
                    Timber.tag(TAG).i( "Discovery started: $serviceType")
                }
                override fun onDiscoveryStopped(serviceType: String) {
                    Timber.tag(TAG).i("Discovery stopped: $serviceType")
                }
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Timber.tag(TAG).d("Start discovery failed: $errorCode")
                    if (continuation.isActive)continuation.resume(null)
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Timber.tag(TAG).d("Stop discovery failed: $errorCode")
                }
                override fun onServiceLost(service: NsdServiceInfo) {
                    Timber.tag(TAG).w("Service lost: ${service.serviceName}")
                }
                override fun onServiceFound(service: NsdServiceInfo) {
                    Timber.tag(TAG).i("Service found: $service")
                    if (service.serviceName == serviceName){
                        val serviceDeviceIdBytes = service.attributes["deviceId"]

                        if(serviceDeviceIdBytes == null || serviceDeviceIdBytes.isEmpty()) {
                            Timber.tag(TAG).w("Service has no deviceId attribute")
                            return
                        }
                        try {
                            val serviceDeviceId = serviceDeviceIdBytes.toString(Charsets.UTF_8)
                            if (serviceDeviceId == deviceId){
                                Timber.tag(TAG).d("Found matching service: ${service.serviceName}")
                                nsdManager.resolveService(service, resolveListener)
                            }
                            else{
                                Timber.tag(TAG).w("Service has different deviceId: ${service.serviceName}")
                            }
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "Failed to parse deviceId attribute")
                            return
                        }
                    }
                }
            }

            continuation.invokeOnCancellation {
                Timber.tag(TAG).w("Coroutine cancelled, stopping discovery")
                discoveryListener.let { nsdManager.stopServiceDiscovery(it) }
            }
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }
}