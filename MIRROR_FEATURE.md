# 镜像功能使用说明

## 概述

本插件新增了镜像控制功能，允许用户在地图导航界面通过按钮控制镜像功能的开启和关闭，并向web端发送相应的回调事件。

## 功能特性

- 在地图导航界面添加镜像控制按钮
- 点击按钮显示投屏确认弹框
- 弹框确认后切换镜像状态（开启/关闭）
- 向web端发送镜像状态变化事件
- 支持时间戳记录
- 按钮状态可视化反馈
- 与初始投屏确认弹框保持一致的用户体验

## 界面布局

镜像按钮位于导航界面的右侧，在重新居中按钮下方，具有以下特征：

- 圆形白色背景
- 灰色边框
- 48dp x 48dp 尺寸
- 镜像图标

## 事件数据格式

当镜像按钮被点击并确认后，会触发 `onScreenMirroringChange` 事件，数据格式如下：

```typescript
interface ScreenMirroringChangeEvent {
  enabled: boolean; // 镜像是否启用
  timestamp?: number; // 时间戳（毫秒）
}
```

## Web端使用示例

### 基本监听

```typescript
import { CapacitorMapboxNavigation } from 'capacitor-mapbox-navigation';

// 监听镜像状态变化
CapacitorMapboxNavigation.addListener('onScreenMirroringChange', (data) => {
  console.log('镜像状态:', data.enabled);
  console.log('时间戳:', data.timestamp);

  if (data.enabled) {
    // 镜像已开启
    console.log('开始镜像功能');
  } else {
    // 镜像已关闭
    console.log('停止镜像功能');
  }
});
```

### 完整示例

```typescript
class MirrorController {
  private isMirrorActive = false;

  constructor() {
    this.initMirrorListener();
  }

  private initMirrorListener() {
    CapacitorMapboxNavigation.addListener('onScreenMirroringChange', (data) => {
      this.handleMirrorChange(data);
    });
  }

  private handleMirrorChange(data: ScreenMirroringChangeEvent) {
    this.isMirrorActive = data.enabled;

    // 更新UI状态
    this.updateMirrorUI(data.enabled);

    // 执行相应的镜像操作
    if (data.enabled) {
      this.startMirroring();
    } else {
      this.stopMirroring();
    }
  }

  private updateMirrorUI(enabled: boolean) {
    const statusElement = document.getElementById('mirror-status');
    if (statusElement) {
      statusElement.textContent = enabled ? '镜像已开启' : '镜像已关闭';
      statusElement.className = enabled ? 'mirror-active' : 'mirror-inactive';
    }
  }

  private startMirroring() {
    // 实现镜像开启逻辑
    console.log('开始屏幕镜像');
  }

  private stopMirroring() {
    // 实现镜像关闭逻辑
    console.log('停止屏幕镜像');
  }
}

// 使用示例
const mirrorController = new MirrorController();
```

## 按钮状态

镜像按钮会根据当前状态显示不同的颜色：

- **灰色**: 镜像功能关闭
- **绿色**: 镜像功能开启

## 注意事项

1. 镜像按钮只在导航开始后显示
2. 点击镜像按钮会显示确认弹框，只有确认后才会切换状态
3. 初始导航时会自动显示投屏确认弹框
4. 按钮状态会在应用重启后重置
5. 事件监听器需要在导航开始前设置
6. 时间戳使用系统当前时间（毫秒）

## 错误处理

```typescript
CapacitorMapboxNavigation.addListener('onScreenMirroringChange', (data) => {
  try {
    // 处理镜像状态变化
    this.handleMirrorChange(data);
  } catch (error) {
    console.error('处理镜像状态变化时出错:', error);
  }
});
```

## 测试

可以使用提供的测试文件 `example/src/js/mirror-test.js` 来测试镜像功能：

```typescript
import MirrorTest from './mirror-test.js';

const mirrorTest = new MirrorTest();
```

## 兼容性

- Android: API 23+
- 需要位置权限
- 支持所有现代浏览器
