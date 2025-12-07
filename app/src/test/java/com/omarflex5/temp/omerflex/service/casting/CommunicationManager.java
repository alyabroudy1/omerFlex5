package com.omarflex5.temp.omerflex.service.casting;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Production-ready Communication Manager for device-to-device communication
 * Handles both server and client socket communication with connection pooling
 */
public class CommunicationManager {
    private static final String TAG = "CommunicationManager";
    
    // Configuration
    public static final int SERVER_PORT = 8888;
    private static final int CONNECTION_TIMEOUT = 10000; // 10 seconds
    private static final int SOCKET_TIMEOUT = 30000; // 30 seconds
    private static final int MAX_CONNECTIONS = 10;
    private static final int THREAD_POOL_SIZE = 5;
    
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private final ConcurrentHashMap<String, Connection> activeConnections;
    private final CopyOnWriteArrayList<MessageListener> messageListeners;
    
    private ServerSocket serverSocket;
    private boolean isServerRunning = false;
    private String deviceId;
    
    public interface MessageListener {
        void onMessageReceived(Message message, Device sender);
        void onMessageSent(Message message, Device recipient);
        void onMessageFailed(Message message, Device recipient, String error);
        void onDeviceConnected(Device device);
        void onDeviceDisconnected(Device device);
    }
    
    private static class Connection {
        Socket socket;
        PrintWriter writer;
        BufferedReader reader;
        Thread listenerThread;
        long lastActivity;
        
        Connection(Socket socket) throws IOException {
            this.socket = socket;
            this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.lastActivity = System.currentTimeMillis();
        }
        
        void close() {
            try {
                if (writer != null) writer.close();
                if (reader != null) reader.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing connection", e);
            }
        }
        
        boolean isValid() {
            return socket != null && !socket.isClosed() && socket.isConnected();
        }
    }
    
    public CommunicationManager(String deviceId) {
        this.deviceId = deviceId;
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.activeConnections = new ConcurrentHashMap<>();
        this.messageListeners = new CopyOnWriteArrayList<>();
    }
    
    /**
     * Start the server to listen for incoming connections
     */
    public void startServer() {
        if (isServerRunning) {
            Log.w(TAG, "Server is already running");
            return;
        }
        
        executorService.execute(() -> {
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
                serverSocket.setSoTimeout(1000); // Non-blocking accept with timeout
                isServerRunning = true;
                
                Log.d(TAG, "Server started on port " + SERVER_PORT);
                
                while (isServerRunning && !Thread.currentThread().isInterrupted()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        clientSocket.setSoTimeout(SOCKET_TIMEOUT);
                        
                        Log.d(TAG, "New client connection from: " + clientSocket.getInetAddress());
                        
                        // Handle new connection
                        handleNewConnection(clientSocket);
                        
                    } catch (SocketTimeoutException e) {
                        // Normal timeout, continue loop
                    } catch (IOException e) {
                        if (isServerRunning) {
                            Log.e(TAG, "Error accepting client connection", e);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error starting server", e);
                isServerRunning = false;
            }
        });
    }
    
    /**
     * Stop the server
     */
    public void stopServer() {
        isServerRunning = false;
        
        executorService.execute(() -> {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
                
                // Close all active connections
                closeAllConnections();
                
                Log.d(TAG, "Server stopped");
            } catch (IOException e) {
                Log.e(TAG, "Error stopping server", e);
            }
        });
    }
    
    /**
     * Connect to a remote device
     */
    public void connectToDevice(Device device) {
        if (device.getDeviceIp() == null) {
            notifyConnectionFailed(device, "Device IP address is null");
            return;
        }
        
        String connectionKey = device.getDeviceAddress();
        if (activeConnections.containsKey(connectionKey)) {
            Log.d(TAG, "Already connected to device: " + device.getDeviceName());
            return;
        }
        
        executorService.execute(() -> {
            try {
                Socket socket = new Socket(device.getDeviceIp(), device.getDevicePort());
                socket.setSoTimeout(SOCKET_TIMEOUT);
                
                Connection connection = new Connection(socket);
                activeConnections.put(connectionKey, connection);
                
                // Start listening for messages from this device
                startConnectionListener(connection, device);
                
                // Send handshake message
                sendHandshake(device);
                
                mainHandler.post(() -> {
                    for (MessageListener listener : messageListeners) {
                        listener.onDeviceConnected(device);
                    }
                });
                
                Log.d(TAG, "Connected to device: " + device.getDeviceName());
                
            } catch (IOException e) {
                Log.e(TAG, "Failed to connect to device: " + device.getDeviceName(), e);
                notifyConnectionFailed(device, e.getMessage());
            }
        });
    }
    
    /**
     * Send a message to a specific device
     */
    public void sendMessage(Message message, Device recipient) {
        String connectionKey = recipient.getDeviceAddress();
        Connection connection = activeConnections.get(connectionKey);
        
        if (connection == null || !connection.isValid()) {
            notifyMessageFailed(message, recipient, "No active connection to device");
            return;
        }
        
        executorService.execute(() -> {
            try {
                String jsonMessage = message.toJsonString();
                connection.writer.println(jsonMessage);
                connection.lastActivity = System.currentTimeMillis();
                
                Log.d(TAG, "Message sent to " + recipient.getDeviceName() + ": " + message.getMessageType());
                
                mainHandler.post(() -> {
                    for (MessageListener listener : messageListeners) {
                        listener.onMessageSent(message, recipient);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to send message", e);
                notifyMessageFailed(message, recipient, e.getMessage());
            }
        });
    }
    
    /**
     * Send a broadcast message to all connected devices
     */
    public void broadcastMessage(Message message) {
        for (Map.Entry<String, Connection> entry : activeConnections.entrySet()) {
            // Create a copy of the message for each recipient
            Message recipientMessage = Message.fromJsonString(message.toJsonString());
            Device recipient = new Device();
            recipient.setDeviceAddress(entry.getKey());
            sendMessage(recipientMessage, recipient);
        }
    }
    
    /**
     * Disconnect from a specific device
     */
    public void disconnectFromDevice(Device device) {
        String connectionKey = device.getDeviceAddress();
        Connection connection = activeConnections.remove(connectionKey);
        
        if (connection != null) {
            connection.close();
            Log.d(TAG, "Disconnected from device: " + device.getDeviceName());
            
            mainHandler.post(() -> {
                for (MessageListener listener : messageListeners) {
                    listener.onDeviceDisconnected(device);
                }
            });
        }
    }
    
    /**
     * Add a message listener
     */
    public void addMessageListener(MessageListener listener) {
        if (!messageListeners.contains(listener)) {
            messageListeners.add(listener);
        }
    }
    
    /**
     * Remove a message listener
     */
    public void removeMessageListener(MessageListener listener) {
        messageListeners.remove(listener);
    }
    
    /**
     * Get all connected devices
     */
    public List<Device> getConnectedDevices() {
        List<Device> devices = new ArrayList<>();
        for (Map.Entry<String, Connection> entry : activeConnections.entrySet()) {
            if (entry.getValue().isValid()) {
                Device device = new Device();
                device.setDeviceAddress(entry.getKey());
                devices.add(device);
            }
        }
        return devices;
    }
    
    /**
     * Check if connected to a specific device
     */
    public boolean isConnectedToDevice(Device device) {
        String connectionKey = device.getDeviceAddress();
        Connection connection = activeConnections.get(connectionKey);
        return connection != null && connection.isValid();
    }
    
    /**
     * Clean up all resources
     */
    public void cleanup() {
        stopServer();
        closeAllConnections();
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        messageListeners.clear();
        Log.d(TAG, "CommunicationManager cleaned up");
    }
    
    private void handleNewConnection(Socket clientSocket) {
        try {
            Connection connection = new Connection(clientSocket);
            String remoteAddress = clientSocket.getInetAddress().getHostAddress();
            activeConnections.put(remoteAddress, connection);
            
            // Create a temporary device for this connection
            Device device = new Device();
            device.setDeviceAddress(remoteAddress);
            device.setDeviceIp(clientSocket.getInetAddress());
            device.setDevicePort(clientSocket.getPort());
            
            startConnectionListener(connection, device);
            
            Log.d(TAG, "New connection handled from: " + remoteAddress);
            
        } catch (IOException e) {
            Log.e(TAG, "Error handling new connection", e);
            try {
                clientSocket.close();
            } catch (IOException closeException) {
                Log.e(TAG, "Error closing socket", closeException);
            }
        }
    }
    
    private void startConnectionListener(Connection connection, Device device) {
        connection.listenerThread = new Thread(() -> {
            try {
                while (connection.isValid() && !Thread.currentThread().isInterrupted()) {
                    String messageJson = connection.reader.readLine();
                    
                    if (messageJson == null) {
                        // Connection closed
                        break;
                    }
                    
                    connection.lastActivity = System.currentTimeMillis();
                    
                    try {
                        Message message = Message.fromJsonString(messageJson);
                        handleIncomingMessage(message, device);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing message", e);
                    }
                }
            } catch (SocketTimeoutException e) {
                Log.d(TAG, "Socket timeout for device: " + device.getDeviceName());
            } catch (IOException e) {
                if (connection.isValid()) {
                    Log.e(TAG, "Error reading from connection", e);
                }
            } finally {
                // Connection lost
                activeConnections.remove(device.getDeviceAddress());
                connection.close();
                
                mainHandler.post(() -> {
                    for (MessageListener listener : messageListeners) {
                        listener.onDeviceDisconnected(device);
                    }
                });
            }
        });
        
        connection.listenerThread.start();
    }
    
    private void handleIncomingMessage(Message message, Device sender) {
        Log.d(TAG, "Message received from " + sender.getDeviceName() + ": " + message.getMessageType());
        
        mainHandler.post(() -> {
            for (MessageListener listener : messageListeners) {
                listener.onMessageReceived(message, sender);
            }
        });
    }
    
    private void sendHandshake(Device device) {
        Message handshake = new Message(deviceId, Message.MessageType.HANDSHAKE, "Hello from " + deviceId);
        sendMessage(handshake, device);
    }
    
    private void closeAllConnections() {
        for (Connection connection : activeConnections.values()) {
            connection.close();
        }
        activeConnections.clear();
    }
    
    private void notifyConnectionFailed(Device device, String error) {
        mainHandler.post(() -> {
            for (MessageListener listener : messageListeners) {
                listener.onDeviceDisconnected(device);
            }
        });
    }
    
    private void notifyMessageFailed(Message message, Device recipient, String error) {
        mainHandler.post(() -> {
            for (MessageListener listener : messageListeners) {
                listener.onMessageFailed(message, recipient, error);
            }
        });
    }
}