package com.example.p2pchat.webrtc

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRTCManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private val peerConnections = mutableMapOf<String, PeerConnection>()
    private val dataChannels = mutableMapOf<String, DataChannel>()

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(peerId: String = "", observer: PeerConnection.Observer): PeerConnection? {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        // Set bundle policy to maximize chance of connection
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        
        val pc = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
        if (pc != null) {
            peerConnections[peerId] = pc
        }
        return pc
    }

    fun createDataChannel(peerId: String = "", label: String): DataChannel? {
        val config = DataChannel.Init()
        val dc = peerConnections[peerId]?.createDataChannel(label, config)
        if (dc != null) {
            dataChannels[peerId] = dc
        }
        return dc
    }

    fun onRemoteDataChannel(peerId: String = "", channel: DataChannel) {
        dataChannels[peerId] = channel
    }

    fun createOffer(peerId: String = "", observer: SdpObserver) {
        peerConnections[peerId]?.createOffer(observer, MediaConstraints())
    }

    fun createAnswer(peerId: String = "", observer: SdpObserver) {
        peerConnections[peerId]?.createAnswer(observer, MediaConstraints())
    }

    fun setLocalDescription(peerId: String = "", observer: SdpObserver, sdp: SessionDescription) {
        peerConnections[peerId]?.setLocalDescription(observer, sdp)
    }

    fun setRemoteDescription(peerId: String = "", observer: SdpObserver, sdp: SessionDescription) {
        peerConnections[peerId]?.setRemoteDescription(observer, sdp)
    }

    fun addIceCandidate(peerId: String = "", candidate: IceCandidate) {
        peerConnections[peerId]?.addIceCandidate(candidate)
    }

    fun sendMessage(peerId: String = "", message: String): Boolean {
        return sendData(peerId, message.toByteArray())
    }

    fun sendData(peerId: String = "", data: ByteArray): Boolean {
        val channel = dataChannels[peerId]
        if (channel == null || channel.state() != DataChannel.State.OPEN) {
            return false
        }
        val buffer = DataChannel.Buffer(
            java.nio.ByteBuffer.wrap(data),
            false
        )
        return channel.send(buffer)
    }

    fun close(peerId: String = "") {
        dataChannels[peerId]?.close()
        dataChannels.remove(peerId)
        peerConnections[peerId]?.close()
        peerConnections.remove(peerId)
    }

    fun closeAll() {
        dataChannels.values.forEach { it.close() }
        dataChannels.clear()
        peerConnections.values.forEach { it.close() }
        peerConnections.clear()
    }
}
