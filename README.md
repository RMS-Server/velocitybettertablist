# VelocityTabList

一个强大的Velocity代理插件，用于增强Tab栏显示跨服务器玩家信息。

## 功能特点

- 🌐 **跨服务器Tab栏显示** - 在Tab栏中显示所有服务器的在线玩家
- 🔧 **智能数据包处理** - 在Velocity层面拦截和增强PlayerListItem数据包
- 🛡️ **模组兼容性** - 完美保留Carpet、miniHUD等模组的TPS、MSPT等信息
- ⚡ **高性能优化** - 增量更新机制，最小化网络开销
- 🎨 **自定义格式** - 支持自定义颜色、前缀和显示格式
- 📊 **实时更新** - 自动同步服务器状态和玩家信息

## 安装方法

1. 下载最新版本的插件JAR文件
2. 将JAR文件放入Velocity的`plugins`目录
3. 重启Velocity服务器
4. 配置文件将自动生成在`plugins/velocitytablist/config.yml`

## 配置说明

插件配置文件位于`plugins/velocitytablist/config.yml`：

```yaml
# 跨服务器功能开关
cross-server:
  enabled: true
  update-interval-seconds: 30
  max-players-per-server: 10

# 显示格式自定义
formatting:
  server-header-format: "§e§l%s §7(%d人在线)"
  cross-server-player-format: "§7├ %s §8(%s)"
  separator-text: "§6§l=== Velocity 服务器列表 ==="

# 性能优化
performance:
  preserve-mod-info: true
  max-tab-list-size: 100
```

## 技术原理

### 数据包拦截机制

插件采用先进的数据包拦截技术：

1. **透明拦截** - 在Velocity层面拦截PlayerListItem数据包
2. **智能合并** - 保留原始数据包内容，添加跨服务器信息
3. **模组保护** - 自动识别和保护Carpet等模组的特殊条目

### 虚拟条目生成

- 使用确定性UUID生成算法
- 支持服务器分组显示
- 智能处理玩家数量限制

### 性能优化

- 增量更新机制减少网络开销
- 多线程异步处理避免阻塞
- 智能缓存管理

## 兼容性

- ✅ Velocity 3.2.0+
- ✅ Minecraft 1.16+
- ✅ Carpet Mod
- ✅ miniHUD
- ✅ 其他Tab栏模组

## 开发信息

- **作者**: XRain
- **项目地址**: https://github.com/RMS-Server/velocitytab
- **包名**: net.rms.velocitytablist
- **构建工具**: Gradle
- **Java版本**: 11+

## 构建方法

```bash
git clone https://github.com/RMS-Server/velocitytab.git
cd velocitytab
./gradlew shadowJar
```

构建后的JAR文件位于`build/libs/`目录。

## 支持

如有问题或建议，请在GitHub上提交Issue。