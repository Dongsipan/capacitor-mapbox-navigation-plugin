// 镜像功能测试文件
import { CapacitorMapboxNavigation } from 'capacitor-mapbox-navigation'

export class MirrorTest {
  constructor() {
    this.init()
  }

  init() {
    // 监听镜像状态变化（仅在用户确认弹框后触发）
    CapacitorMapboxNavigation.addListener('onScreenMirroringChange', (data) => {
      this.handleMirrorChange(data)
    })

    // 监听路线进度变化
    CapacitorMapboxNavigation.addListener('onRouteProgressChange', (data) => {
      this.handleRouteProgress(data)
    })
  }

  handleMirrorChange(data) {
    console.log('=== 镜像状态变化 ===')
    console.log('启用状态:', data.enabled)
    console.log('时间戳:', data.timestamp)

    // 更新UI显示
    this.updateMirrorStatus(data)

    // 根据状态执行相应操作
    if (data.enabled) {
      this.onMirrorEnabled()
    } else {
      this.onMirrorDisabled()
    }
  }

  handleRouteProgress(data) {
    console.log('=== 路线进度更新 ===')
    console.log('剩余距离:', data.content?.distanceRemaining)
    console.log('当前步骤剩余距离:', data.content?.stepDistanceRemaining)
  }

  updateMirrorStatus(data) {
    // 查找或创建状态显示元素
    let statusElement = document.getElementById('mirror-status')
    if (!statusElement) {
      statusElement = document.createElement('div')
      statusElement.id = 'mirror-status'
      statusElement.style.cssText = `
                position: fixed;
                top: 20px;
                right: 20px;
                padding: 10px;
                border-radius: 5px;
                color: white;
                font-weight: bold;
                z-index: 1000;
                background-color: ${data.enabled ? '#4CAF50' : '#f44336'};
            `
      document.body.appendChild(statusElement)
    }

    statusElement.textContent = data.enabled ? '镜像已开启' : '镜像已关闭'
    statusElement.style.backgroundColor = data.enabled ? '#4CAF50' : '#f44336'
  }

  onMirrorEnabled() {
    console.log('镜像功能已启用')
    // 在这里添加镜像启用时的逻辑
    // 例如：开始录制、显示镜像预览等
  }

  onMirrorDisabled() {
    console.log('镜像功能已禁用')
    // 在这里添加镜像禁用时的逻辑
    // 例如：停止录制、隐藏镜像预览等
  }

  // 手动触发镜像状态变化（用于测试）
  testMirrorToggle() {
    console.log('手动触发镜像状态切换')
    // 这个方法可以在web端调用，用于测试镜像功能
  }
}

// 导出测试类
export default MirrorTest
