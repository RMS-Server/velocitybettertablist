package net.rms.velocitytablist.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TabListConfig {
    
    private final Path configPath;
    private CommentedConfigurationNode config;
    
    // 跨服务器功能配置
    private boolean showPlayerCount = true;
    private boolean showServerStatus = true;
    private int updateIntervalSeconds = 30;
    private int maxPlayersPerServer = 10;
    
    // 格式化配置
    private String currentServerPrefix = "§a➤ ";
    private String otherServerPrefix = "§7├ ";
    private String serverHeaderFormat = "§e§l%s §7(%d人在线)";
    private String crossServerPlayerFormat = "§7%s §8[%s]";
    private String separatorText = "";
    
    // 性能配置
    private boolean enableIncrementalUpdates = true;
    private int maxTabListSize = 100;
    private boolean preserveModInfo = true;
    
    public TabListConfig(Path dataDirectory) {
        this.configPath = dataDirectory.resolve("config.yml");
    }
    
    public void load() throws IOException {
        // 创建数据目录
        if (!Files.exists(configPath.getParent())) {
            Files.createDirectories(configPath.getParent());
        }
        
        // 创建配置加载器
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(configPath)
                .build();
        
        // 加载或创建配置文件
        if (!Files.exists(configPath)) {
            config = loader.createNode();
            setDefaultValues();
            loader.save(config);
        } else {
            config = loader.load();
            loadValues();
        }
    }
    
    private void setDefaultValues() {
        try {
            // 跨服务器功能
            config.node("cross-server", "show-player-count").set(showPlayerCount);
            config.node("cross-server", "show-server-status").set(showServerStatus);
            config.node("cross-server", "update-interval-seconds").set(updateIntervalSeconds);
            config.node("cross-server", "max-players-per-server").set(maxPlayersPerServer);
            
            // 格式化
            config.node("formatting", "current-server-prefix").set(currentServerPrefix);
            config.node("formatting", "other-server-prefix").set(otherServerPrefix);
            config.node("formatting", "server-header-format").set(serverHeaderFormat);
            config.node("formatting", "cross-server-player-format").set(crossServerPlayerFormat);
            config.node("formatting", "separator-text").set(separatorText);
            
            // 性能
            config.node("performance", "enable-incremental-updates").set(enableIncrementalUpdates);
            config.node("performance", "max-tab-list-size").set(maxTabListSize);
            config.node("performance", "preserve-mod-info").set(preserveModInfo);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set default configuration values", e);
        }
        
        // 添加注释
        config.node("cross-server").comment("跨服务器Tab栏功能配置");
        config.node("formatting").comment("显示格式配置 - 支持 Minecraft 颜色代码");
        config.node("performance").comment("性能优化配置");
    }
    
    private void loadValues() {
        // 跨服务器功能
        showPlayerCount = config.node("cross-server", "show-player-count").getBoolean(true);
        showServerStatus = config.node("cross-server", "show-server-status").getBoolean(true);
        updateIntervalSeconds = config.node("cross-server", "update-interval-seconds").getInt(30);
        maxPlayersPerServer = config.node("cross-server", "max-players-per-server").getInt(10);
        
        // 格式化
        currentServerPrefix = config.node("formatting", "current-server-prefix").getString("§a➤ ");
        otherServerPrefix = config.node("formatting", "other-server-prefix").getString("§7├ ");
        serverHeaderFormat = config.node("formatting", "server-header-format").getString("§e§l%s §7(%d人在线)");
        crossServerPlayerFormat = config.node("formatting", "cross-server-player-format").getString("§7%s §8[%s]");
        separatorText = config.node("formatting", "separator-text").getString("");
        
        // 性能
        enableIncrementalUpdates = config.node("performance", "enable-incremental-updates").getBoolean(true);
        maxTabListSize = config.node("performance", "max-tab-list-size").getInt(100);
        preserveModInfo = config.node("performance", "preserve-mod-info").getBoolean(true);
    }
    
    // Getter 方法
    public boolean isShowPlayerCount() { return showPlayerCount; }
    public boolean isShowServerStatus() { return showServerStatus; }
    public int getUpdateIntervalSeconds() { return updateIntervalSeconds; }
    public int getMaxPlayersPerServer() { return maxPlayersPerServer; }
    
    public String getCurrentServerPrefix() { return currentServerPrefix; }
    public String getOtherServerPrefix() { return otherServerPrefix; }
    public String getServerHeaderFormat() { return serverHeaderFormat; }
    public String getCrossServerPlayerFormat() { return crossServerPlayerFormat; }
    public String getSeparatorText() { return separatorText; }
    
    public boolean isEnableIncrementalUpdates() { return enableIncrementalUpdates; }
    public int getMaxTabListSize() { return maxTabListSize; }
    public boolean isPreserveModInfo() { return preserveModInfo; }
}