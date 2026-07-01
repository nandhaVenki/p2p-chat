package com.example.p2pchat.data.repository

import com.example.p2pchat.data.local.dao.MessageDao
import com.example.p2pchat.data.local.dao.UserProfileDao
import com.example.p2pchat.data.local.dao.GroupDao
import com.example.p2pchat.data.local.dao.DirectChatDao
import com.example.p2pchat.data.local.entity.MessageEntity
import com.example.p2pchat.data.local.entity.UserProfileEntity
import com.example.p2pchat.data.local.entity.GroupEntity
import com.example.p2pchat.data.local.entity.GroupMemberEntity
import com.example.p2pchat.data.local.entity.DirectChatEntity
import com.example.p2pchat.signaling.SignalingClient
import com.example.p2pchat.webrtc.WebRTCManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import com.example.p2pchat.data.model.ConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton

import com.example.p2pchat.network.NetworkMonitor
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

import com.example.p2pchat.util.HashUtils

@Singleton
class ChatRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val userProfileDao: UserProfileDao,
    private val groupDao: GroupDao,
    private val directChatDao: DirectChatDao,
    private val signalingClient: SignalingClient,
    private val webRTCManager: WebRTCManager,
    private val networkMonitor: NetworkMonitor,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var myPhoneNumber: String = ""
    private var myPhoneHash: String = ""
    private var peerPhoneHash: String = ""
    private var myFcmToken: String = ""

    val userProfile: Flow<UserProfileEntity?> = userProfileDao.getUserProfile()

    suspend fun registerUser(firstName: String, lastName: String, phoneNumber: String) {
        val profile = UserProfileEntity(phoneNumber, firstName, lastName)
        userProfileDao.insertProfile(profile)
        initialize(phoneNumber)
    }

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val incomingMessages: SharedFlow<String> = _incomingMessages

    private var isInitialized = false

    fun initialize(phoneNumber: String) {
        if (isInitialized) return
        isInitialized = true

        myPhoneNumber = phoneNumber
        myPhoneHash = HashUtils.hashPhoneNumber(phoneNumber)
        
        val sharedPrefs = context.getSharedPreferences("p2p_chat_prefs", Context.MODE_PRIVATE)
        myFcmToken = sharedPrefs.getString("fcm_token", "") ?: ""

        // Connect immediately on initialization
        reconnectSignaling()
        
        // Monitor network changes
        scope.launch {
            networkMonitor.networkStatus.collect { isAvailable ->
                if (isAvailable) {
                    reconnectSignaling()
                }
            }
        }
        
        // Handle incoming signaling
        scope.launch {
            signalingClient.signalingMessages.collect { handleSignalingMessage(it) }
        }
    }

    private fun reconnectSignaling() {
        val routing = HashUtils.parsePhoneRoutingInfo(myPhoneNumber)
        signalingClient.connect("wss://p2p-chat-dg66.onrender.com") {
            val register = JSONObject().apply {
                put("type", "register")
                put("phoneHash", myPhoneHash)
                put("countryCode", routing.countryCode)
                put("areaCode", routing.areaCode)
                put("subscriberHash", routing.subscriberHash)
                if (myFcmToken.isNotEmpty()) {
                    put("fcmToken", myFcmToken)
                }
            }
            signalingClient.sendMessage(register)
        }
    }

    fun startChatWith(peerPhoneNumber: String) {
        peerPhoneHash = HashUtils.hashPhoneNumber(peerPhoneNumber)
        
        scope.launch {
            val existing = directChatDao.getDirectChatByHash(peerPhoneHash)
            directChatDao.insertDirectChat(
                DirectChatEntity(
                    peerPhoneNumber = peerPhoneNumber,
                    peerPhoneHash = peerPhoneHash,
                    lastActiveTimestamp = System.currentTimeMillis(),
                    isMessageRequest = false // Set to false to accept/activate the chat session
                )
            )
        }
        
        initiateP2PHandshake()
    }

    private fun initiateP2PHandshake() {
        if (peerPhoneHash.isEmpty()) return
        setupPeerConnection(peerPhoneHash)
        webRTCManager.createDataChannel(peerPhoneHash, "chat")
        webRTCManager.createOffer(peerPhoneHash, object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    webRTCManager.setLocalDescription(peerPhoneHash, this, it)
                    val offer = JSONObject().apply {
                        put("type", "offer")
                        put("toPhoneHash", peerPhoneHash)
                        put("sdp", it.description)
                    }
                    signalingClient.sendMessage(offer)
                }
            }
        })
    }

    private val fileBuffers = mutableMapOf<String, java.io.ByteArrayOutputStream>()

    fun sendFile(fileBytes: ByteArray, fileName: String) {
        val transferId = java.util.UUID.randomUUID().toString()
        val chunkSize = 16384 // 16KB per packet for WebRTC stability
        val totalChunks = Math.ceil(fileBytes.size.toDouble() / chunkSize).toInt()

        scope.launch {
            saveMessage(peerPhoneHash, "Sending file: $fileName", true, isMedia = true, fileTransferId = transferId)
            
            for (i in 0 until totalChunks) {
                val start = i * chunkSize
                val end = Math.min(fileBytes.size, (i + 1) * chunkSize)
                val chunk = fileBytes.copyOfRange(start, end)
                
                val header = JSONObject().apply {
                    put("type", "file-chunk")
                    put("transferId", transferId)
                    put("chunkIndex", i)
                    put("totalChunks", totalChunks)
                    put("fileName", fileName)
                }
                
                // Packet format: [JSON Header Size (Int)][JSON Header (String)][Raw Bytes]
                val headerBytes = header.toString().toByteArray()
                val packet = java.nio.ByteBuffer.allocate(4 + headerBytes.size + chunk.size)
                packet.putInt(headerBytes.size)
                packet.put(headerBytes)
                packet.put(chunk)
                
                val success = webRTCManager.sendData(peerPhoneHash, packet.array())
                if (!success) break // Handle P2P failure
                
                kotlinx.coroutines.delay(20) // Tiny delay to prevent DataChannel overflow
            }
        }
    }

    private fun handleIncomingData(peerHash: String, data: ByteArray) {
        val buffer = java.nio.ByteBuffer.wrap(data)
        val headerSize = buffer.getInt()
        val headerBytes = ByteArray(headerSize)
        buffer.get(headerBytes)
        val header = JSONObject(String(headerBytes))
        
        when (header.getString("type")) {
            "file-chunk" -> {
                val transferId = header.getString("transferId")
                val chunkIndex = header.getInt("chunkIndex")
                val totalChunks = header.getInt("totalChunks")
                val fileName = header.getString("fileName")
                
                val chunk = ByteArray(buffer.remaining())
                buffer.get(chunk)
                
                val fileBuffer = fileBuffers.getOrPut(transferId) { java.io.ByteArrayOutputStream() }
                fileBuffer.write(chunk)
                
                if (chunkIndex == totalChunks - 1) {
                    // File complete!
                    val finalFile = fileBuffer.toByteArray()
                    fileBuffers.remove(transferId)
                    saveFileToDisk(peerHash, finalFile, fileName, transferId)
                }
            }
        }
    }

    private fun saveFileToDisk(peerHash: String, bytes: ByteArray, name: String, id: String) {
        // In a real app, save to private storage and return URI
        saveMessage(peerHash, "Received file: $name", false, isMedia = true, fileTransferId = id)
    }

    fun sendMessage(text: String) {
        val msg = JSONObject().apply {
            put("type", "text")
            put("content", text)
            put("senderPhoneNumber", myPhoneNumber) // Expose raw number so B can display message request
        }
        val isDirectSuccess = webRTCManager.sendMessage(peerPhoneHash, msg.toString())
        
        if (!isDirectSuccess) {
            initiateP2PHandshake()
        }
        
        saveMessage(peerPhoneHash, text, true)
    }

    private fun saveDirectChatRequest(peerPhoneNumber: String, peerPhoneHash: String) {
        scope.launch {
            val existing = directChatDao.getDirectChatByHash(peerPhoneHash)
            if (existing == null) {
                directChatDao.insertDirectChat(
                    DirectChatEntity(
                        peerPhoneNumber = peerPhoneNumber,
                        peerPhoneHash = peerPhoneHash,
                        lastActiveTimestamp = System.currentTimeMillis(),
                        isMessageRequest = false // Immediately active direct chat session
                    )
                )
            }
        }
    }

    private fun saveMessage(peerHash: String, content: String, isSent: Boolean, isMedia: Boolean = false, fileTransferId: String? = null) {
        scope.launch {
            messageDao.insertMessage(
                MessageEntity(
                    senderId = if (isSent) myPhoneHash else peerHash,
                    receiverId = if (isSent) peerHash else myPhoneHash,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    isSent = isSent,
                    isMedia = isMedia,
                    fileTransferId = fileTransferId
                )
            )
        }
    }

    private fun handleSignalingMessage(data: JSONObject) {
        val type = data.optString("type")
        val fromPhoneHash = data.optString("fromPhoneHash")
        val targetPeerId = if (fromPhoneHash.isNotEmpty()) fromPhoneHash else ""

        when (type) {
            "registered" -> {
                scope.launch {
                    val activePeers = getActivePeerHashes()
                    if (activePeers.isNotEmpty()) {
                        val presenceBroadcast = JSONObject().apply {
                            put("type", "presence")
                            put("targets", org.json.JSONArray(activePeers))
                        }
                        signalingClient.sendMessage(presenceBroadcast)
                    }
                }
            }
            "presence" -> {
                val peerHash = data.optString("fromPhoneHash")
                if (peerHash.isNotEmpty() && myPhoneHash.isNotEmpty()) {
                    scope.launch {
                        val activePeers = getActivePeerHashes()
                        if (activePeers.contains(peerHash)) {
                            setupPeerConnection(peerHash)
                            webRTCManager.createDataChannel(peerHash, "chat")
                            webRTCManager.createOffer(peerHash, object : SimpleSdpObserver() {
                                    override fun onCreateSuccess(sdp: SessionDescription?) {
                                        sdp?.let {
                                            webRTCManager.setLocalDescription(peerHash, this, it)
                                            val offer = JSONObject().apply {
                                                put("type", "offer")
                                                put("toPhoneHash", peerHash)
                                                put("sdp", it.description)
                                            }
                                            signalingClient.sendMessage(offer)
                                        }
                                    }
                                })
                            }
                        }
                    }
                }
            "group-create" -> {
                val groupData = data.getJSONObject("groupData")
                val groupId = groupData.getString("groupId")
                val groupName = groupData.getString("groupName")
                val creatorId = groupData.getString("creatorId")
                
                scope.launch {
                    val group = GroupEntity(groupId, groupName, creatorId, System.currentTimeMillis())
                    groupDao.insertGroup(group)
                    
                    val membersArray = groupData.getJSONArray("members")
                    val membersList = mutableListOf<GroupMemberEntity>()
                    for (i in 0 until membersArray.length()) {
                        membersList.add(GroupMemberEntity(groupId, membersArray.getString(i)))
                    }
                    groupDao.insertGroupMembers(membersList)
                }
            }
            "offer" -> {
                setupPeerConnection(targetPeerId)
                val sdp = SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp"))
                webRTCManager.setRemoteDescription(targetPeerId, object : SimpleSdpObserver() {}, sdp)
                webRTCManager.createAnswer(targetPeerId, object : SimpleSdpObserver() {
                    override fun onCreateSuccess(answer: SessionDescription?) {
                        answer?.let {
                            webRTCManager.setLocalDescription(targetPeerId, this, it)
                            val msg = JSONObject().apply {
                                put("type", "answer")
                                put("toPhoneHash", targetPeerId)
                                put("sdp", it.description)
                            }
                            signalingClient.sendMessage(msg)
                        }
                    }
                })
            }
            "answer" -> {
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, data.getString("sdp"))
                webRTCManager.setRemoteDescription(targetPeerId, object : SimpleSdpObserver() {}, sdp)
            }
            "ice-candidate" -> {
                val candidate = IceCandidate(
                    data.getString("sdpMid"),
                    data.getInt("sdpMLineIndex"),
                    data.getString("candidate")
                )
                webRTCManager.addIceCandidate(targetPeerId, candidate)
            }
        }
    }

    private fun setupPeerConnection(targetPeerId: String = "") {
        _connectionStatus.value = ConnectionStatus.CONNECTING
        webRTCManager.createPeerConnection(targetPeerId, object : SimplePeerConnectionObserver() {
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                when (p0) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        _connectionStatus.value = ConnectionStatus.ERROR
                    }
                    else -> {}
                }
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val ice = JSONObject().apply {
                        put("type", "ice-candidate")
                        put("toPhoneHash", if (targetPeerId.isNotEmpty()) targetPeerId else peerPhoneHash)
                        put("sdpMid", it.sdpMid)
                        put("sdpMLineIndex", it.sdpMLineIndex)
                        put("candidate", it.sdp)
                    }
                    signalingClient.sendMessage(ice)
                }
            }

            override fun onDataChannel(channel: DataChannel?) {
                channel?.let {
                    webRTCManager.onRemoteDataChannel(targetPeerId, it)
                    it.registerObserver(object : DataChannel.Observer {
                        override fun onBufferedAmountChange(p0: Long) {}
                        override fun onStateChange() {
                            if (it.state() == DataChannel.State.OPEN) {
                                // Keep connection open
                            }
                        }
                        override fun onMessage(buffer: DataChannel.Buffer) {
                            val data = buffer.data
                            val bytes = ByteArray(data.remaining())
                            data.get(bytes)
                            
                            if (buffer.binary) {
                                handleIncomingData(targetPeerId, bytes)
                            } else {
                                val text = String(bytes)
                                try {
                                    val json = JSONObject(text)
                                    when (json.optString("type")) {
                                        "text" -> {
                                            val content = json.getString("content")
                                            val senderPhone = json.optString("senderPhoneNumber")
                                            if (senderPhone.isNotEmpty()) {
                                                saveDirectChatRequest(senderPhone, targetPeerId)
                                            }
                                            saveMessage(targetPeerId, content, false)
                                            _incomingMessages.tryEmit(content)
                                        }
                                        "group-message" -> {
                                            handleIncomingGroupMessage(json)
                                        }
                                    }
                                } catch (e: Exception) {
                                    saveMessage(targetPeerId, text, false)
                                    _incomingMessages.tryEmit(text)
                                }
                            }
                        }
                    })
                }
            }
        })
    }

    // --- P2P Mesh Group Chat Functions ---

    fun getGroups(): Flow<List<GroupEntity>> {
        return groupDao.getGroups()
    }

    fun getGroupMessages(groupId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForGroup(groupId)
    }

    suspend fun createGroup(groupName: String, membersPhoneNumbers: List<String>) {
        val groupId = java.util.UUID.randomUUID().toString()
        val group = GroupEntity(
            groupId = groupId,
            groupName = groupName,
            creatorId = myPhoneHash,
            timestamp = System.currentTimeMillis()
        )
        groupDao.insertGroup(group)
        
        val memberEntities = mutableListOf<GroupMemberEntity>()
        memberEntities.add(GroupMemberEntity(groupId, myPhoneHash))
        membersPhoneNumbers.forEach { num ->
            val hash = HashUtils.hashPhoneNumber(num)
            memberEntities.add(GroupMemberEntity(groupId, hash))
        }
        groupDao.insertGroupMembers(memberEntities)
        
        scope.launch {
            val notifyMsg = JSONObject().apply {
                put("type", "group-create")
                put("groupId", groupId)
                put("groupName", groupName)
                put("creatorId", myPhoneHash)
                val membersJsonArray = org.json.JSONArray()
                memberEntities.forEach { membersJsonArray.put(it.memberPhoneHash) }
                put("members", membersJsonArray)
            }
            
            memberEntities.forEach { member ->
                if (member.memberPhoneHash != myPhoneHash) {
                    val directNotify = JSONObject().apply {
                        put("type", "group-create")
                        put("toPhoneHash", member.memberPhoneHash)
                        put("groupData", notifyMsg)
                    }
                    signalingClient.sendMessage(directNotify)
                }
            }
        }
    }

    fun sendGroupMessage(groupId: String, text: String) {
        scope.launch {
            val msgId = java.util.UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val localMsg = MessageEntity(
                messageId = msgId,
                senderId = myPhoneHash,
                receiverId = groupId,
                content = text,
                timestamp = timestamp,
                isSent = true,
                groupId = groupId
            )
            messageDao.insertMessage(localMsg)
            
            val members = groupDao.getGroupMemberHashes(groupId).filter { it != myPhoneHash }.sorted()
            if (members.isEmpty()) return@launch
            
            val routingPath = org.json.JSONArray()
            members.forEach { routingPath.put(it) }
            
            val payload = JSONObject().apply {
                put("type", "group-message")
                put("messageId", msgId)
                put("groupId", groupId)
                put("senderId", myPhoneHash)
                put("content", text)
                put("timestamp", timestamp)
                put("routingPath", routingPath)
                put("currentHopIndex", 0)
            }
            
            val firstTarget = members[0]
            val success = sendOrEstablishP2P(firstTarget, payload)
            if (!success) {
                // Fallback: Skip B (index 0) and try C (index 1)
                relayGroupMessageFallback(payload, 1)
            }
        }
    }

    private suspend fun sendOrEstablishP2P(targetPhoneHash: String, payload: JSONObject): Boolean {
        val success = webRTCManager.sendMessage(targetPhoneHash, payload.toString())
        if (success) return true
        
        setupPeerConnection(targetPhoneHash)
        webRTCManager.createDataChannel(targetPhoneHash, "chat")
        
        val offerDeferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
        webRTCManager.createOffer(targetPhoneHash, object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    webRTCManager.setLocalDescription(targetPhoneHash, this, it)
                    val offer = JSONObject().apply {
                        put("type", "offer")
                        put("toPhoneHash", targetPhoneHash)
                        put("sdp", it.description)
                    }
                    signalingClient.sendMessage(offer)
                    offerDeferred.complete(true)
                } ?: offerDeferred.complete(false)
            }
            override fun onCreateFailure(p0: String?) {
                offerDeferred.complete(false)
            }
        })
        
        val offerSent = offerDeferred.await()
        if (!offerSent) return false
        
        // Wait up to 3 seconds for connection open
        for (i in 0..15) {
            delay(200)
            if (webRTCManager.sendMessage(targetPhoneHash, payload.toString())) {
                return true
            }
        }
        return false
    }

    private fun relayGroupMessageFallback(payload: JSONObject, nextHopIndex: Int) {
        scope.launch {
            val routingPathArray = payload.getJSONArray("routingPath")
            if (nextHopIndex >= routingPathArray.length()) return@launch
            val nextTarget = routingPathArray.getString(nextHopIndex)
            payload.put("currentHopIndex", nextHopIndex)
            val success = sendOrEstablishP2P(nextTarget, payload)
            if (!success) {
                relayGroupMessageFallback(payload, nextHopIndex + 1)
            }
        }
    }

    private fun handleIncomingGroupMessage(json: JSONObject) {
        val msgId = json.getString("messageId")
        val groupId = json.getString("groupId")
        val senderId = json.getString("senderId")
        val content = json.getString("content")
        val timestamp = json.getLong("timestamp")
        val routingPath = json.getJSONArray("routingPath")
        val currentHopIndex = json.getInt("currentHopIndex")

        scope.launch {
            if (messageDao.messageExists(msgId)) return@launch

            messageDao.insertMessage(
                MessageEntity(
                    messageId = msgId,
                    senderId = senderId,
                    receiverId = groupId,
                    content = content,
                    timestamp = timestamp,
                    isSent = false,
                    groupId = groupId
                )
            )
            _incomingMessages.tryEmit("Group message received")

            val nextHopIndex = currentHopIndex + 1
            if (nextHopIndex < routingPath.length()) {
                val nextTarget = routingPath.getString(nextHopIndex)
                json.put("currentHopIndex", nextHopIndex)
                
                scope.launch {
                    val success = sendOrEstablishP2P(nextTarget, json)
                    if (!success) {
                        relayGroupMessageRelayFallback(json, nextHopIndex + 1)
                    }
                }
            }
        }
    }

    private fun relayGroupMessageRelayFallback(payload: JSONObject, nextHopIndex: Int) {
        scope.launch {
            val routingPathArray = payload.getJSONArray("routingPath")
            if (nextHopIndex >= routingPathArray.length()) return@launch
            val nextTarget = routingPathArray.getString(nextHopIndex)
            payload.put("currentHopIndex", nextHopIndex)
            val success = sendOrEstablishP2P(nextTarget, payload)
            if (!success) {
                relayGroupMessageRelayFallback(payload, nextHopIndex + 1)
            }
        }
    }

    // --- End Group Chat Functions ---

    fun getMessages(): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForChat(myPhoneHash, peerPhoneHash)
    }

    fun createManualOffer(onOfferCreated: (String) -> Unit) {
        setupPeerConnection("")
        webRTCManager.createDataChannel("", "chat")
        webRTCManager.createOffer("", object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    webRTCManager.setLocalDescription("", this, it)
                    val compressed = com.example.p2pchat.util.SdpCompressor.compress(it.description)
                    onOfferCreated(compressed)
                }
            }
        })
    }

    fun acceptManualOffer(compressedSdp: String, onAnswerCreated: (String) -> Unit) {
        try {
            setupPeerConnection("")
            val cleanSdp = if (compressedSdp.startsWith("P2P_CONNECT:")) {
                compressedSdp.substringAfter("P2P_CONNECT:")
            } else {
                compressedSdp
            }.trim()
            val sdpString = com.example.p2pchat.util.SdpCompressor.decompress(cleanSdp)
            val sdp = SessionDescription(SessionDescription.Type.OFFER, sdpString)
            webRTCManager.setRemoteDescription("", object : SimpleSdpObserver() {}, sdp)
            webRTCManager.createAnswer("", object : SimpleSdpObserver() {
                override fun onCreateSuccess(answer: SessionDescription?) {
                    answer?.let {
                        webRTCManager.setLocalDescription("", this, it)
                        val compressedAnswer = com.example.p2pchat.util.SdpCompressor.compress(it.description)
                        onAnswerCreated(compressedAnswer)
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            _incomingMessages.tryEmit("Failed to parse manual offer: ${e.localizedMessage}")
        }
    }

    fun acceptManualAnswer(compressedSdp: String) {
        try {
            val cleanSdp = if (compressedSdp.startsWith("P2P_CONNECT:")) {
                compressedSdp.substringAfter("P2P_CONNECT:")
            } else {
                compressedSdp
            }.trim()
            val sdpString = com.example.p2pchat.util.SdpCompressor.decompress(cleanSdp)
            val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpString)
            webRTCManager.setRemoteDescription("", object : SimpleSdpObserver() {}, sdp)
        } catch (e: Exception) {
            e.printStackTrace()
            _incomingMessages.tryEmit("Failed to parse manual answer: ${e.localizedMessage}")
        }
    }

    fun getDirectChats(): Flow<List<DirectChatEntity>> = directChatDao.getDirectChats()

    private suspend fun getActivePeerHashes(): List<String> {
        val hashes = mutableListOf<String>()
        val groupMembers = groupDao.getAllGroupMembers()
        hashes.addAll(groupMembers)
        val directPeers = directChatDao.getAllDirectChatHashes()
        hashes.addAll(directPeers)
        return hashes.filter { it.isNotEmpty() && it != myPhoneHash }.distinct()
    }

    fun updateFcmToken(token: String) {
        myFcmToken = token
        if (isInitialized && myPhoneHash.isNotEmpty()) {
            scope.launch {
                val tokenUpdate = JSONObject().apply {
                    put("type", "update-token")
                    put("fcmToken", token)
                }
                signalingClient.sendMessage(tokenUpdate)
            }
        }
    }

    fun handleIncomingFcmSignaling(data: JSONObject) {
        handleSignalingMessage(data)
    }
}

// Helper base classes to reduce boilerplate
open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

open class SimplePeerConnectionObserver : PeerConnection.Observer {
    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
        // Log state changes for debugging dynamic network shifts
    }
    override fun onIceConnectionReceivingChange(p0: Boolean) {}
    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidate(p0: IceCandidate?) {}
    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
    override fun onAddStream(p0: MediaStream?) {}
    override fun onRemoveStream(p0: MediaStream?) {}
    override fun onDataChannel(p0: DataChannel?) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
    override fun onTrack(transceiver: RtpTransceiver?) {}
}
