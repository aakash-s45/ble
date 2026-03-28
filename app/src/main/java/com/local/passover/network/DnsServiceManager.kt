package com.local.passover.network

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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
    private val DISCOVERY_TIMEOUT_MS = 15_000L

    private fun stopDiscoverySafe(listener: NsdManager.DiscoveryListener?) {
        try {
            listener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (e: IllegalArgumentException) {
            Timber.tag(TAG).e(e, "Listener error: ")
        }
    }

    // ── Discover all services (multi-peer) ────────────────────────────────

    suspend fun discoverServices(): List<NsdServiceInfo> =
        withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val resolvedServices = mutableListOf<NsdServiceInfo>()
                var discoveryListener: NsdManager.DiscoveryListener? = null
                val lock = Any()
                var isResumed = false
                var isResolving = false
                val pendingServices = mutableListOf<NsdServiceInfo>()
                var discoveryComplete = false

                val resolveListener = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                        Timber.tag(TAG).e("Resolve failed for ${service.serviceName}: $errorCode")
                        resolveNext()
                    }
                    override fun onServiceResolved(service: NsdServiceInfo) {
                        Timber.tag(TAG).i("Service resolved: $service")
                        synchronized(lock) {
                            resolvedServices.add(service)
                            resolveNext()
                        }
                    }

                    private fun resolveNext() {
                        synchronized(lock) {
                            val next = pendingServices.removeFirstOrNull()
                            if (next != null) {
                                nsdManager.resolveService(next, this)
                            } else {
                                isResolving = false
                                // If discovery has stopped and nothing left to resolve, we're done
                                if (discoveryComplete && !isResumed) {
                                    isResumed = true
                                    continuation.resume(resolvedServices.toList())
                                }
                            }
                        }
                    }
                }

                // Use a delayed finish — give discovery a few seconds to collect peers
                val finishDiscovery = {
                    synchronized(lock) {
                        discoveryComplete = true
                        stopDiscoverySafe(discoveryListener)
                        if (!isResolving && !isResumed) {
                            isResumed = true
                            continuation.resume(resolvedServices.toList())
                        }
                    }
                }

                discoveryListener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {
                        Timber.tag(TAG).i("Discovery started: $serviceType")
                        // Schedule a finish after a short collection window
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            finishDiscovery()
                        }, 5_000)
                    }
                    override fun onDiscoveryStopped(serviceType: String) {
                        Timber.tag(TAG).i("Discovery stopped: $serviceType")
                    }
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Timber.tag(TAG).d("Start discovery failed: $errorCode")
                        synchronized(lock) {
                            if (!isResumed) {
                                isResumed = true
                                continuation.resume(emptyList())
                            }
                        }
                    }
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Timber.tag(TAG).d("Stop discovery failed: $errorCode")
                    }
                    override fun onServiceLost(service: NsdServiceInfo) {
                        Timber.tag(TAG).w("Service lost: ${service.serviceName}")
                    }
                    override fun onServiceFound(service: NsdServiceInfo) {
                        Timber.tag(TAG).i("Service found: $service")
                        if (service.serviceName.contains(serviceName)) {
                            synchronized(lock) {
                                if (isResumed) return
                                if (!isResolving) {
                                    isResolving = true
                                    nsdManager.resolveService(service, resolveListener)
                                } else {
                                    pendingServices.add(service)
                                }
                            }
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    Timber.tag(TAG).w("Coroutine cancelled, stopping discovery")
                    stopDiscoverySafe(discoveryListener)
                }
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            }
        } ?: emptyList()

    // ── Find specific service by deviceId (existing, kept for backward compat) ──
    // TODO: remove this if not needed

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun findService(deviceId: String): NsdServiceInfo? =
        withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            var discoveryListener: NsdManager.DiscoveryListener? = null
            val resumeLock = Any()
            var isResumed = false
            var isResolving = false
            val pendingServices = mutableListOf<NsdServiceInfo>()

            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                    Timber.tag(TAG).e("Resolve failed for ${service.serviceName}: $errorCode")
                    resolveNext()
                }
                override fun onServiceResolved(service: NsdServiceInfo) {
                    Timber.tag(TAG).i("Service resolved: $service")
                    val deviceIdBytes = service.attributes["deviceId"]
                    val resolvedId = deviceIdBytes?.let { String(it, Charsets.UTF_8) }

                    if (resolvedId != deviceId) {
                        Timber.tag(TAG).d("Ignored service from wrong device: $resolvedId")
                        resolveNext()
                        return
                    }

                    synchronized(resumeLock) {
                        if (continuation.isActive && !isResumed) {
                            isResumed = true
                            stopDiscoverySafe(discoveryListener)
                            continuation.resume(service)
                        }
                    }
                }

                private fun resolveNext() {
                    synchronized(resumeLock) {
                        if (isResumed) return
                        val next = pendingServices.removeFirstOrNull()
                        if (next != null) {
                            nsdManager.resolveService(next, this)
                        } else {
                            isResolving = false
                        }
                    }
                }
            }

            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {
                    Timber.tag(TAG).i("Discovery started: $serviceType")
                }
                override fun onDiscoveryStopped(serviceType: String) {
                    Timber.tag(TAG).i("Discovery stopped: $serviceType")
                }
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Timber.tag(TAG).d("Start discovery failed: $errorCode")
                    synchronized(resumeLock) {
                        if (continuation.isActive && !isResumed) {
                            isResumed = true
                            continuation.resume(null)
                        }
                    }
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Timber.tag(TAG).d("Stop discovery failed: $errorCode")
                }
                override fun onServiceLost(service: NsdServiceInfo) {
                    Timber.tag(TAG).w("Service lost: ${service.serviceName}")
                }
                override fun onServiceFound(service: NsdServiceInfo) {
                    Timber.tag(TAG).i("Service found: $service")
                    if (service.serviceName.contains(serviceName)) {
                        synchronized(resumeLock) {
                            if (isResumed) return
                            if (!isResolving) {
                                isResolving = true
                                nsdManager.resolveService(service, resolveListener)
                            } else {
                                pendingServices.add(service)
                            }
                        }
                    }
                }
            }

            continuation.invokeOnCancellation {
                Timber.tag(TAG).w("Coroutine cancelled, stopping discovery")
                stopDiscoverySafe(discoveryListener)
            }
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }
    }
}