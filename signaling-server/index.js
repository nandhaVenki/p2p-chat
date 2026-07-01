const express = require('express');
const { WebSocketServer } = require('ws');
const http = require('http');
const fs = require('fs');
const path = require('path');

const app = express();
const port = process.env.PORT || 3000;
const dbPath = path.join(__dirname, 'registered_users.json');

// Helper to load registered users from disk
function loadUsers() {
    try {
        if (fs.existsSync(dbPath)) {
            const data = fs.readFileSync(dbPath, 'utf8');
            return JSON.parse(data);
        }
    } catch (e) {
        console.error('Error reading users database:', e);
    }
    return {};
}

// Helper to update/save user details to disk
function saveUser(phoneHash, details) {
    try {
        const users = loadUsers();
        users[phoneHash] = {
            ...users[phoneHash],
            ...details,
            lastSeen: new Date().toISOString()
        };
        fs.writeFileSync(dbPath, JSON.stringify(users, null, 2), 'utf8');
        console.log(`Updated user persistent record [${phoneHash}]:`, users[phoneHash]);
    } catch (e) {
        console.error('Error saving user to database:', e);
    }
}

// Health check endpoint
app.get('/health', (req, res) => {
    res.send('Signaling server is running');
});

// Endpoint to query a specific user's connection details (for P2P mapping)
app.get('/users/:phoneHash', (req, res) => {
    const users = loadUsers();
    const user = users[req.params.phoneHash];
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

// Map to store connected clients: phoneNumberHash -> WebSocket
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
                    console.log(`Phone Hash registered: ${currentPhoneHash} from IP: ${ip}`);
                    
                    // Persist registration record and update IP & status
                    saveUser(currentPhoneHash, {
                        lastKnownIp: ip,
                        status: 'online'
                    });

                    ws.send(JSON.stringify({ type: 'registered', phoneHash: currentPhoneHash }));
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
                        console.log(`Target hash ${data.toPhoneHash} not found or not connected for ${data.type}`);
                        ws.send(JSON.stringify({ type: 'error', message: `User is offline` }));
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
            
            // Mark user status as offline persistently
            saveUser(currentPhoneHash, {
                status: 'offline'
            });
        }
    });

    ws.on('error', (err) => {
        console.error('WebSocket error:', err);
    });
});

server.listen(port, () => {
    console.log(`Signaling server listening on port ${port}`);
});
