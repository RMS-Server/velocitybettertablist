package net.rms.velocitytablist.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class UUIDGenerator {
    
    private static final String NAMESPACE = "velocitytablist";
    private static final UUID NAMESPACE_UUID = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    
    private final ConcurrentMap<String, UUID> uuidCache = new ConcurrentHashMap<>();
    
    public UUID generateVirtualUUID(String identifier) {
        return uuidCache.computeIfAbsent(identifier, this::createVirtualUUID);
    }
    
    private UUID createVirtualUUID(String identifier) {
        try {
            // 使用命名空间和标识符生成确定性UUID
            String input = NAMESPACE + ":" + identifier;
            
            // 使用SHA-1哈希生成UUID (类似UUID v5)
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // 构造UUID
            long msb = 0;
            long lsb = 0;
            
            // 使用哈希的前16字节构造UUID
            for (int i = 0; i < 8; i++) {
                msb = (msb << 8) | (hash[i] & 0xff);
            }
            
            for (int i = 8; i < 16; i++) {
                lsb = (lsb << 8) | (hash[i] & 0xff);
            }
            
            // 设置版本位 (版本5)
            msb &= ~(0xf000L);
            msb |= 0x5000L;
            
            // 设置变体位
            lsb &= ~(0xc000000000000000L);
            lsb |= 0x8000000000000000L;
            
            return new UUID(msb, lsb);
            
        } catch (NoSuchAlgorithmException e) {
            // 如果SHA-1不可用，使用简单的随机UUID作为后备
            return UUID.nameUUIDFromBytes((NAMESPACE + ":" + identifier).getBytes(StandardCharsets.UTF_8));
        }
    }
    
    public UUID generatePlayerVirtualUUID(UUID originalUUID, String serverName) {
        String identifier = "player_" + originalUUID.toString() + "_" + serverName;
        return generateVirtualUUID(identifier);
    }
    
    public UUID generateServerHeaderUUID(String serverName) {
        String identifier = "server_header_" + serverName;
        return generateVirtualUUID(identifier);
    }
    
    public UUID generateSeparatorUUID(String type) {
        String identifier = "separator_" + type;
        return generateVirtualUUID(identifier);
    }
    
    public boolean isVirtualUUID(UUID uuid) {
        return uuidCache.containsValue(uuid);
    }
    
    public void clearCache() {
        uuidCache.clear();
    }
    
    public int getCacheSize() {
        return uuidCache.size();
    }
}