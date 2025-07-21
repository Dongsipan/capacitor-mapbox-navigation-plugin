# Bug修复总结

## 问题描述

应用在启动导航时出现闪退，错误信息：

```
FATAL EXCEPTION: main
Process: com.dongsipan.smartbicycle, PID: 5479
kotlin.UninitializedPropertyAccessException: lateinit property viewportDataSource has not been initialized
at com.capacitor.mapbox.navigation.plugin.NavigationDialogFragment$locationObserver$1.onNewLocationMatcherResult(NavigationDialogFragment.kt:147)
```

## 问题原因

在 `NavigationDialogFragment.kt` 中，`viewportDataSource` 属性使用了 `lateinit` 修饰符，但在 `locationObserver` 的 `onNewLocationMatcherResult` 方法中被访问时还没有初始化。

具体问题：

1. 在 `onViewCreated` 方法中，先注册了 `navigationObserver`
2. 注册 `navigationObserver` 会立即触发 `onAttached` 方法
3. `onAttached` 方法中注册了 `locationObserver`
4. `locationObserver` 可能在 `viewportDataSource` 初始化之前就被调用
5. `viewportDataSource` 在 `initNavigation()` 方法中才被初始化

## 修复方案

将 `MapboxNavigationApp.registerObserver(navigationObserver)` 的调用从 `onViewCreated` 方法移动到 `initNavigation()` 方法的最后，确保所有必要的组件都已经初始化完成。

### 修复前

```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // 检查位置权限
    checkLocationPermissionAndRequestRoute()

    // 注册 MapboxNavigation 观察者 - 问题所在
    MapboxNavigationApp.registerObserver(navigationObserver)

    // 将 fragment 的生命周期与 MapboxNavigation 绑定
    MapboxNavigationApp.attach(this)

    initNavigation()
}
```

### 修复后

```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // 将 fragment 的生命周期与 MapboxNavigation 绑定
    MapboxNavigationApp.attach(this)

    // 检查位置权限
    checkLocationPermissionAndRequestRoute()

    initNavigation()
}

private fun initNavigation() {
    requireActivity().runOnUiThread {
        // ... 所有初始化代码 ...

        // 注册 MapboxNavigation 观察者（在所有初始化完成后）
        MapboxNavigationApp.registerObserver(navigationObserver)
    }
}
```

## 验证结果

- ✅ Android项目构建成功
- ✅ 没有编译错误
- ✅ 观察者注册时机正确
- ✅ 所有组件初始化顺序正确

## 相关文件

- `android/src/main/java/com/capacitor/mapbox/navigation/plugin/NavigationDialogFragment.kt`

## 注意事项

1. 确保所有 `lateinit` 属性在使用前都已经初始化
2. 观察者的注册应该在所有相关组件初始化完成后进行
3. 注意组件之间的依赖关系和初始化顺序
