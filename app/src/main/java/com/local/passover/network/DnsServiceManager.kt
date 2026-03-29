package com.local.passover.network

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class DnsServiceManager @Inject constructor(
    private val nsdManager: NsdManager
) {
    private val TAG = "DnsServiceManager"
    private val serviceType = "_passover._tcp"
    private val serviceName = "Passover"
    private val DISCOVERY_TIMEOUT_MS = 15_000L
    private val DISCOVERY_COLLECTION_WINDOW_MS = 5_000L

    private fun stopDiscoverySafe(listener: NsdManager.DiscoveryListener?) {
        try {
            listener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Listener error: ", e)
        }
    }

    private fun isTargetService(service: NsdServiceInfo): Boolean =
        service.serviceName.contains(serviceName)

    private class DiscoverySession<T>(
        private val continuation: CancellableContinuation<T>,
        private val stopDiscovery: () -> Unit,
        private val onComplete: () -> Unit = {}
    ) {
        private val stateLock = Any()
        private val pendingServices = mutableListOf<NsdServiceInfo>()
        private var isCompleted = false
        private var hasResolveInFlight = false
        private var discoveryFinished = false

        fun complete(result: T, stopDiscoveryBeforeResume: Boolean = false) {
            synchronized(stateLock) {
                if (isCompleted || !continuation.isActive) return

                isCompleted = true
                onComplete()
                if (stopDiscoveryBeforeResume) {
                    stopDiscovery()
                }
                continuation.resume(result)
            }
        }

        fun enqueueForResolution(
            service: NsdServiceInfo,
            resolveService: (NsdServiceInfo) -> Unit,
        ) {
            synchronized(stateLock) {
                if (isCompleted) return

                if (hasResolveInFlight) {
                    pendingServices.add(service)
                    return
                }

                hasResolveInFlight = true
                resolveService(service)
            }
        }

        fun onResolveFinished(
            resolveService: (NsdServiceInfo) -> Unit,
            beforeNext: (() -> Unit)? = null,
            onIdleAfterDiscovery: (() -> Unit)? = null,
        ) {
            synchronized(stateLock) {
                if (isCompleted) return

                beforeNext?.invoke()

                val next = pendingServices.removeFirstOrNull()
                if (next != null) {
                    resolveService(next)
                    return
                }

                hasResolveInFlight = false
                if (discoveryFinished) {
                    onIdleAfterDiscovery?.invoke()
                }
            }
        }

        fun markDiscoveryFinished(onIdleAfterDiscovery: () -> Unit) {
            synchronized(stateLock) {
                if (isCompleted) return

                discoveryFinished = true
                stopDiscovery()
                if (!hasResolveInFlight) {
                    onIdleAfterDiscovery()
                }
            }
        }

        fun cancel() {
            synchronized(stateLock) {
                if (isCompleted) return

                isCompleted = true
                onComplete()
                stopDiscovery()
            }
        }
    }

    // ── Discover all services (multi-peer) ────────────────────────────────

    suspend fun discoverServices(): List<NsdServiceInfo> =
        withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val resolvedServices = mutableListOf<NsdServiceInfo>()
                var discoveryListener: NsdManager.DiscoveryListener? = null
                val finishHandler = Handler(Looper.getMainLooper())
                lateinit var session: DiscoverySession<List<NsdServiceInfo>>
                val finishRunnable = Runnable {
                    session.markDiscoveryFinished {
                        session.complete(resolvedServices.toList())
                    }
                }
                session = DiscoverySession(
                    continuation = continuation,
                    stopDiscovery = { stopDiscoverySafe(discoveryListener) },
                    onComplete = { finishHandler.removeCallbacks(finishRunnable) },
                )

                val resolveListener = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Resolve failed for ${service.serviceName}: $errorCode")
                        session.onResolveFinished(
                            resolveService = { next -> nsdManager.resolveService(next, this) },
                            onIdleAfterDiscovery = {
                                session.complete(resolvedServices.toList())
                            },
                        )
                    }

                    override fun onServiceResolved(service: NsdServiceInfo) {
                        Log.i(TAG, "Service resolved: $service")
                        session.onResolveFinished(
                            resolveService = { next -> nsdManager.resolveService(next, this) },
                            beforeNext = { resolvedServices.add(service) },
                            onIdleAfterDiscovery = {
                                session.complete(resolvedServices.toList())
                            },
                        )
                    }
                }

                discoveryListener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {
                        Log.i(TAG, "Discovery started: $serviceType")
                        finishHandler.postDelayed(finishRunnable, DISCOVERY_COLLECTION_WINDOW_MS)
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        Log.i(TAG, "Discovery stopped: $serviceType")
                    }

                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Log.d(TAG, "Start discovery failed: $errorCode")
                        session.complete(emptyList())
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Log.d(TAG, "Stop discovery failed: $errorCode")
                    }

                    override fun onServiceLost(service: NsdServiceInfo) {
                        Log.w(TAG, "Service lost: ${service.serviceName}")
                    }

                    override fun onServiceFound(service: NsdServiceInfo) {
                        Log.i(TAG, "Service found: $service")
                        if (isTargetService(service)) {
                            session.enqueueForResolution(service) { next ->
                                nsdManager.resolveService(next, resolveListener)
                            }
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    Log.w(TAG, "Coroutine cancelled, stopping discovery")
                    session.cancel()
                }
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            }
        } ?: emptyList()

    // ── Find specific service by deviceId (existing, kept for backward compat) ──
    // TODO: remove this if not needed

    suspend fun findService(deviceId: String): NsdServiceInfo? =
        withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                var discoveryListener: NsdManager.DiscoveryListener? = null
                val session = DiscoverySession(
                    continuation = continuation,
                    stopDiscovery = { stopDiscoverySafe(discoveryListener) },
                )

                val resolveListener = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Resolve failed for ${service.serviceName}: $errorCode")
                        session.onResolveFinished(
                            resolveService = { next -> nsdManager.resolveService(next, this) },
                        )
                    }

                    override fun onServiceResolved(service: NsdServiceInfo) {
                        Log.i(TAG, "Service resolved: $service")
                        val deviceIdBytes = service.attributes["deviceId"]
                        val resolvedId = deviceIdBytes?.let { String(it, Charsets.UTF_8) }

                        if (resolvedId != deviceId) {
                            Log.d(TAG, "Ignored service from wrong device: $resolvedId")
                            session.onResolveFinished(
                                resolveService = { next -> nsdManager.resolveService(next, this) },
                            )
                            return
                        }

                        session.complete(service, stopDiscoveryBeforeResume = true)
                    }
                }

                discoveryListener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {
                        Log.i(TAG, "Discovery started: $serviceType")
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        Log.i(TAG, "Discovery stopped: $serviceType")
                    }

                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Log.d(TAG, "Start discovery failed: $errorCode")
                        session.complete(null)
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Log.d(TAG, "Stop discovery failed: $errorCode")
                    }

                    override fun onServiceLost(service: NsdServiceInfo) {
                        Log.w(TAG, "Service lost: ${service.serviceName}")
                    }

                    override fun onServiceFound(service: NsdServiceInfo) {
                        Log.i(TAG, "Service found: $service")
                        if (isTargetService(service)) {
                            session.enqueueForResolution(service) { next ->
                                nsdManager.resolveService(next, resolveListener)
                            }
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    Log.w(TAG, "Coroutine cancelled, stopping discovery")
                    session.cancel()
                }
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            }
        }
}
