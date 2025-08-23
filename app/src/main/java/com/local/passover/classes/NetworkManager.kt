package com.local.passover.classes

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import timber.log.Timber
import java.io.File
import java.net.Socket

const val NETWORK_TAG = "NetworkManager"

object NetworkManager {
    private var nsdManager: NsdManager? = null
    private const val serviceType = "_passover._tcp."
    private var serviceInfo: NsdServiceInfo? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // Used to avoid resolving the same service or our own
    private val resolvedServiceNames = mutableSetOf<String>()
    private var myServiceName = "Passover"

    fun startServer(context: Context) {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    fun findMacServer() {
        if (discoveryListener == null) {
            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {
                    Timber.tag(NETWORK_TAG).i( "Discovery started: $serviceType")
                }

                override fun onServiceFound(service: NsdServiceInfo) {
                    Timber.tag(NETWORK_TAG).i("Service found: $service")

                    // Avoid resolving our own service or services we've already resolved
                    if (service.serviceType == serviceType && service.serviceName == myServiceName) {
                        resolvedServiceNames.add(service.serviceName)
                        nsdManager?.resolveService(service, resolveListener)
                    } else {
                        Timber.tag(NETWORK_TAG).d("Ignored service: ${service.serviceName}")
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    Timber.tag(NETWORK_TAG).w("Service lost: ${service.serviceName}")
                    resolvedServiceNames.remove(service.serviceName)
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Timber.tag(NETWORK_TAG).d("Stop discovery failed: $errorCode")
                    nsdManager?.stopServiceDiscovery(this)
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Timber.tag(NETWORK_TAG).d("Start discovery failed: $errorCode")
                    nsdManager?.stopServiceDiscovery(this)
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Timber.tag(NETWORK_TAG).i("Discovery stopped: $serviceType")
                }
            }
        }

        nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onServiceResolved(service: NsdServiceInfo) {
            Timber.tag(NETWORK_TAG).i("Service resolved: ${service.serviceName} - ${service.host}:${service.port}")
            serviceInfo = service

            discoveryListener?.let {
                nsdManager?.stopServiceDiscovery(it)
            }
        }

        override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
            Timber.tag(NETWORK_TAG).e("Resolve failed for ${service.serviceName}: $errorCode")
        }
    }

    fun sendFile(file: File) {
        if(serviceInfo == null){
            return
        }
        val ip = serviceInfo?.host
        val port = serviceInfo?.port ?: return

        Thread {
            try {
                val socket = Socket(ip, port)
                val output = socket.getOutputStream()
                val input = file.inputStream()

                input.copyTo(output)

                output.flush()
                output.close()
                input.close()
                socket.close()

                Timber.tag(NETWORK_TAG).d("File sent successfully")
            } catch (e: Exception) {
                Timber.tag(NETWORK_TAG).e(e, "Failed to send file: ${e.message}")
            }
        }.start()
    }

    fun stopServer() {
        discoveryListener?.let {
            try {
                nsdManager?.stopServiceDiscovery(it)
            } catch (e: IllegalArgumentException) {
                Timber.tag(NETWORK_TAG).e(e, "Service discovery already stopped or not started")
            }
        }
        discoveryListener = null
        resolvedServiceNames.clear()
        serviceInfo = null
    }
}