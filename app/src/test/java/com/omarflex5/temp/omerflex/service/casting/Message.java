package com.omarflex5.temp.omerflex.service.casting;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a message for communication between devices
 * Supports JSON serialization/deserialization for network transmission
 */
public class Message {
    private String messageId;
    private String senderId;
    private String receiverId;
    private MessageType messageType;
    private String content;
    private long timestamp;
    private int priority;
    
    public enum MessageType {
        HANDSHAKE,
        DATA,
        COMMAND,
        RESPONSE,
        HEARTBEAT,
        DISCOVERY,
        DISCONNECT
    }
    
    public enum Priority {
        LOW(1),
        NORMAL(2),
        HIGH(3),
        URGENT(4);
        
        private final int value;
        
        Priority(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    public Message() {
        this.messageId = generateMessageId();
        this.timestamp = System.currentTimeMillis();
        this.priority = Priority.NORMAL.getValue();
    }
    
    public Message(String senderId, MessageType messageType, String content) {
        this();
        this.senderId = senderId;
        this.messageType = messageType;
        this.content = content;
    }
    
    public Message(String senderId, String receiverId, MessageType messageType, String content) {
        this(senderId, messageType, content);
        this.receiverId = receiverId;
    }
    
    private String generateMessageId() {
        return "msg_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
    }
    
    // Serialization methods
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("messageId", messageId);
        json.put("senderId", senderId);
        json.put("receiverId", receiverId);
        json.put("messageType", messageType != null ? messageType.name() : null);
        json.put("content", content);
        json.put("timestamp", timestamp);
        json.put("priority", priority);
        return json;
    }
    
    public static Message fromJson(JSONObject json) throws JSONException {
        Message message = new Message();
        message.setMessageId(json.getString("messageId"));
        message.setSenderId(json.optString("senderId"));
        message.setReceiverId(json.optString("receiverId"));
        
        String typeStr = json.optString("messageType");
        if (typeStr != null && !typeStr.isEmpty()) {
            message.setMessageType(MessageType.valueOf(typeStr));
        }
        
        message.setContent(json.optString("content"));
        message.setTimestamp(json.getLong("timestamp"));
        message.setPriority(json.getInt("priority"));
        return message;
    }
    
    public String toJsonString() {
        try {
            return toJson().toString();
        } catch (JSONException e) {
            throw new RuntimeException("Failed to serialize message to JSON", e);
        }
    }
    
    public static Message fromJsonString(String jsonString) {
        try {
            return fromJson(new JSONObject(jsonString));
        } catch (JSONException e) {
            throw new RuntimeException("Failed to deserialize message from JSON", e);
        }
    }
    
    // Getters and Setters
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    
    public String getSenderId() {
        return senderId;
    }
    
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }
    
    public String getReceiverId() {
        return receiverId;
    }
    
    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }
    
    public MessageType getMessageType() {
        return messageType;
    }
    
    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    public void setPriority(Priority priority) {
        this.priority = priority.getValue();
    }
    
    // Helper methods
    public boolean isBroadcast() {
        return receiverId == null || receiverId.isEmpty();
    }
    
    public boolean isHighPriority() {
        return priority >= Priority.HIGH.getValue();
    }
    
    @Override
    public String toString() {
        return "Message{" +
                "messageId='" + messageId + '\'' +
                ", senderId='" + senderId + '\'' +
                ", receiverId='" + receiverId + '\'' +
                ", messageType=" + messageType +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                ", priority=" + priority +
                '}';
    }
}