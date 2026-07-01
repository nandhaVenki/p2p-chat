const express = require('express');
const { WebSocketServer } = require('ws');
const http = require('http');
const fs = require('fs');
const path = require('path');

const app = express();
const port = process.env.PORT || 3000;
const dbPath = path.join(__dirname, 'registered_users.json');

// Initialize Firebase Admin SDK if serviceAccountKey.json is present
let firebaseAdmin = null;
const serviceAccountPath = path.join(__dirname, 'serviceAccountKey.json');
if (fs.existsSync(serviceAccountPath)) {
    try {
        firebaseAdmin = require('firebase-admin');
        const serviceAccount = require(serviceAccountPath);
        firebaseAdmin.initializeApp({
            credential: firebaseAdmin.credential.cert(serviceAccount)
        });
        console.log('Firebase Admin SDK initialized successfully.');
    } catch (e) {
        console.error('Error initializing Firebase Admin SDK:', e);
    }
} else {
    console.warn('Firebase serviceAccountKey.json not found. FCM push notifications will be disabled.');
}

// Helper to relay signaling via FCM push notifications
function sendFcmMessage(fcmToken, payload) {
    if (!firebaseAdmin) {
        console.warn('FCM push skipped: Firebase Admin SDK is not initialized.');
        return;
    }
    
    const message = {
        data: {
            type: payload.type,
            fromPhoneHash: payload.fromPhoneHash || '',
            sdp: payload.sdp || '',
            candidate: payload.candidate || '',
            sdpMLineIndex: payload.sdpMLineIndex != null ? String(payload.sdpMLineIndex) : '',
            sdpMid: payload.sdpMid || ''
        },
        token: fcmToken
    };

    firebaseAdmin.messaging().send(message)
        .then((response) => {
            console.log('Successfully sent FCM push notification:', response);
        })
        .catch((error) => {
            console.error('Error sending FCM push:', error);
        });
}

// Trie Data Structure for hierarchical indexing
class TrieNode {
    constructor() {
        this.children = {};
    }
}

class Trie {
    constructor() {
        this.root = new TrieNode();
    }

    // Insert user details: Root -> countryCode -> area digit-by-digit -> subscriberHash
    insert(country, area, subscriber, details) {
        let node = this.root;
        
        // 1. Country Code node (e.g. "+91")
        if (!node.children[country]) {
            node.children[country] = new TrieNode();
        }
        node = node.children[country];
        
        // 2. Area Code - split digit by digit (e.g. "9" -> "8" -> "7")
        for (let i = 0; i < area.length; i++) {
            const digit = area[i];
            if (!node.children[digit]) {
                node.children[digit] = new TrieNode();
            }
            node = node.children[digit];
        }
        
        // 3. Leaf Node: Hashed Subscriber ID
        node.children[subscriber] = {
            phoneHash: details.phoneHash || null,
            fcmToken: details.fcmToken || null,
            lastKnownIp: details.lastKnownIp,
            status: details.status,
            lastSeen: new Date().toISOString()
        };
    }

    // Find a user's details by traversing the tree paths
    find(country, area, subscriber) {
        let node = this.root;
        
        if (!node.children[country]) return null;
        node = node.children[country];
        
        for (let i = 0; i < area.length; i++) {
            const digit = area[i];
            if (!node.children[digit]) return null;
            node = node.children[digit];
        }
        
        return node.children[subscriber] || null;
    }

    // Find a user's leaf node recursively using their phoneHash
    findLeafByPhoneHash(phoneHash) {
        function scan(node) {
            if (!node) return null;
            if (node.phoneHash === phoneHash) {
                return node;
            }
            for (const key in node.children) {
                const result = scan(node.children[key]);
                if (result) return result;
            }
            // Fallback for raw JSON objects loaded from disk
            for (const key in node) {
                if (key === 'children') continue;
                if (node[key] && typeof node[key] === 'object') {
                    const result = scan(node[key]);
                    if (result) return result;
                }
            }
            return null;
        }
        return scan(this.root);
    }
}

const trie = new Trie();

// Helper to load registered users from disk
function loadTrieDatabase() {
    try {
        if (fs.existsSync(dbPath)) {
            const data = fs.readFileSync(dbPath, 'utf8');
            const parsed = JSON.parse(data);
            if (parsed && parsed.root) {
                trie.root = parsed.root;
            }
        }
    } catch (e) {
        console.error('Error reading trie database:', e);
    }
}

// Helper to save user details to disk
function saveTrieDatabase() {
    try {
        fs.writeFileSync(dbPath, JSON.stringify({ root: trie.root }, null, 2), 'utf8');
    } catch (e) {
        console.error('Error saving trie database:', e);
    }
}

// Load database on startup
loadTrieDatabase();

// Health check endpoint
app.get('/health', (req, res) => {
    res.send('Signaling server is running');
});

// Endpoint to query a specific user's connection details hierarchically (Trie Search)
app.get('/users/:countryCode/:areaCode/:subscriberHash', (req, res) => {
    const { countryCode, areaCode, subscriberHash } = req.params;
    const user = trie.find(countryCode, areaCode, subscriberHash);
    if (user) {
        res.json({
            lastKnownIp: user.lastKnownIp,
            status: user.status
        });
    } else {
        res.status(404).json({ error: 'User not found' });
    }
});

const server = http.createServer(app);
const wss = new WebSocketServer({ server });

// Map to store connected clients: phoneNumberHash -> WebSocket (for O(1) WS routing)
const clients = new Map();

wss.on('connection', (ws, req) => {
    const ip = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
    let currentPhoneHash = null;

    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message);
            console.log('Received:', data.type, 'from:', currentPhoneHash || 'unregistered');

            switch (data.type) {
                case 'register':
                    currentPhoneHash = data.phoneHash;
                    clients.set(currentPhoneHash, ws);
                    
                    const country = data.countryCode || '+0';
                    const area = data.areaCode || '000';
                    const subscriber = data.subscriberHash || currentPhoneHash;

                    // Save routing info on connection state for close event
                    ws.countryCode = country;
                    ws.areaCode = area;
                    ws.subscriberHash = subscriber;

                    console.log(`Phone Hash registered: ${currentPhoneHash} [${country}/${area}/${subscriber}] from IP: ${ip}`);
                    
                    // Persist registration record in Trie tree
                    trie.insert(country, area, subscriber, {
                        phoneHash: currentPhoneHash,
                        fcmToken: data.fcmToken || null,
                        lastKnownIp: ip,
                        status: 'online'
                    });
                    saveTrieDatabase();

                    ws.send(JSON.stringify({ type: 'registered', phoneHash: currentPhoneHash }));
                    break;

                case 'update-token':
                    if (currentPhoneHash) {
                        const leaf = trie.findLeafByPhoneHash(currentPhoneHash);
                        if (leaf) {
                            leaf.fcmToken = data.fcmToken;
                            saveTrieDatabase();
                            console.log(`Updated FCM token for ${currentPhoneHash}`);
                        }
                    }
                    break;

                case 'offer':
                case 'answer':
                case 'ice-candidate':
                    const targetWs = clients.get(data.toPhoneHash);
                    if (targetWs && targetWs.readyState === ws.OPEN) {
                        targetWs.send(JSON.stringify({
                            ...data,
                            fromPhoneHash: currentPhoneHash
                        }));
                    } else {
                        console.log(`Target hash ${data.toPhoneHash} is offline. Attempting FCM relay...`);
                        const leaf = trie.findLeafByPhoneHash(data.toPhoneHash);
                        if (leaf && leaf.fcmToken) {
                            sendFcmMessage(leaf.fcmToken, {
                                ...data,
                                fromPhoneHash: currentPhoneHash
                            });
                        } else {
                            console.log(`No FCM token registered for offline target: ${data.toPhoneHash}`);
                            ws.send(JSON.stringify({ type: 'error', message: `User is offline and has no push registry` }));
                        }
                    }
                    break;

                default:
                    console.log('Unknown message type:', data.type);
            }
        } catch (error) {
            console.error('Error processing message:', error);
        }
    });

    ws.on('close', () => {
        if (currentPhoneHash) {
            clients.delete(currentPhoneHash);
            console.log(`Phone Hash disconnected: ${currentPhoneHash}`);
            
            // Mark user status as offline in Trie
            if (ws.countryCode && ws.areaCode && ws.subscriberHash) {
                const leaf = trie.find(ws.countryCode, ws.areaCode, ws.subscriberHash);
                trie.insert(ws.countryCode, ws.areaCode, ws.subscriberHash, {
                    phoneHash: currentPhoneHash,
                    fcmToken: leaf ? leaf.fcmToken : null,
                    lastKnownIp: ip,
                    status: 'offline'
                });
                saveTrieDatabase();
            }
        }
    });

    ws.on('error', (err) => {
        console.error('WebSocket error:', err);
    });
});

server.listen(port, () => {
    console.log(`Signaling server listening on port ${port}`);
});
