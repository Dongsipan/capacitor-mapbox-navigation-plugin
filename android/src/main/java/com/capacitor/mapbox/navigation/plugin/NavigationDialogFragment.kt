package com.capacitor.mapbox.navigation.plugin

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.capacitor.mapbox.navigation.plugin.databinding.MapboxActivityNavigationViewBinding
import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.TimeFormat
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.formatter.Rounding
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.progress.api.MapboxTripProgressApi
import com.mapbox.navigation.tripdata.progress.model.DistanceRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.EstimatedTimeToArrivalFormatter
import com.mapbox.navigation.tripdata.progress.model.PercentDistanceTraveledFormatter
import com.mapbox.navigation.tripdata.progress.model.TimeRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateFormatter
import com.mapbox.navigation.ui.maps.NavigationStyles
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.camera.transition.NavigationCameraTransitionOptions
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.voice.api.MapboxSpeechApi
import com.mapbox.navigation.voice.api.MapboxVoiceInstructionsPlayer
import com.mapbox.navigation.voice.model.SpeechVolume
import java.util.Locale

class NavigationDialogFragment : DialogFragment() {
    private companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private var currentCall: PluginCall? = null

        fun setCurrentCall(call: PluginCall?) {
            currentCall = call
        }
    }

    private val BUTTON_ANIMATION_DURATION = 1500L
    private lateinit var binding: MapboxActivityNavigationViewBinding
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource

    /*
     * Below are generated camera padding values to ensure that the route fits well on screen while
     * other elements are overlaid on top of the map (including instruction view, buttons, etc.)
     */
    private val pixelDensity = Resources.getSystem().displayMetrics.density
    private val overviewPadding: EdgeInsets by lazy {
        EdgeInsets(
            140.0 * pixelDensity,
            40.0 * pixelDensity,
            120.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }
    private val landscapeOverviewPadding: EdgeInsets by lazy {
        EdgeInsets(
            30.0 * pixelDensity,
            380.0 * pixelDensity,
            110.0 * pixelDensity,
            20.0 * pixelDensity
        )
    }
    private val followingPadding: EdgeInsets by lazy {
        EdgeInsets(
            180.0 * pixelDensity,
            40.0 * pixelDensity,
            150.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }
    private val landscapeFollowingPadding: EdgeInsets by lazy {
        EdgeInsets(
            30.0 * pixelDensity,
            380.0 * pixelDensity,
            110.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }
    private lateinit var maneuverApi: MapboxManeuverApi
    private lateinit var tripProgressApi: MapboxTripProgressApi
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private val routeArrowApi: MapboxRouteArrowApi = MapboxRouteArrowApi()
    private lateinit var routeArrowView: MapboxRouteArrowView
    private var isVoiceInstructionsMuted = false
    private lateinit var speechApi: MapboxSpeechApi
    private lateinit var voiceInstructionsPlayer: MapboxVoiceInstructionsPlayer
    private val navigationLocationProvider = NavigationLocationProvider()

    // 位置更新观察者
    private val locationObserver = object : LocationObserver {
        var firstLocationUpdateReceived = false

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation
            navigationLocationProvider.changePosition(
                location = enhancedLocation,
                keyPoints = locationMatcherResult.keyPoints,
            )
            viewportDataSource.onLocationChanged(enhancedLocation)
            viewportDataSource.evaluate()
            if (!firstLocationUpdateReceived) {
                firstLocationUpdateReceived = true
                currentLocation =
                    Point.fromLngLat(enhancedLocation.longitude, enhancedLocation.latitude)
                currentLocation?.let { origin ->
                    destination?.let { findRoute(origin, it) }
                }
                navigationCamera.requestNavigationCameraToOverview(
                    stateTransitionOptions = NavigationCameraTransitionOptions.Builder()
                        .maxDuration(0)
                        .build()
                )
            }
        }

        override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) {
            // 不处理原始位置
        }
    }

    // 路线进度观察者
    private val routeProgressObserver = object : RouteProgressObserver {
        override fun onRouteProgressChanged(routeProgress: RouteProgress) {
            viewportDataSource.onRouteProgressChanged(routeProgress)
            viewportDataSource.evaluate()

            val style = binding.mapView.mapboxMap.style
            if (style != null) {
                val maneuverArrowResult = routeArrowApi.addUpcomingManeuverArrow(routeProgress)
                routeArrowView.renderManeuverUpdate(style, maneuverArrowResult)
            }

            val maneuvers = maneuverApi.getManeuvers(routeProgress)
            maneuvers.fold(
                { error ->
                    // 处理错误
                },
                {
                    binding.maneuverView.visibility = View.VISIBLE
                    binding.maneuverView.renderManeuvers(maneuvers)
                }
            )

            binding.tripProgressView.render(
                tripProgressApi.getTripProgress(routeProgress)
            )

            var currentProgressData = JSObject()
            currentProgressData.put(
                "bannerInstructions",
                routeProgress.bannerInstructions?.toJson()
            )
            currentProgressData.put("distanceRemaining", routeProgress.distanceRemaining)
            currentProgressData.put(
                "stepDistanceRemaining",
                routeProgress.currentLegProgress?.currentStepProgress?.distanceRemaining
            )
            // 发送路线进度数据到Capacitor
            sendDataToCapacitor(
                status = "success",
                type = "onRouteProgressChange",
                content = currentProgressData
            )
        }
    }

    // 路线变更观察者
    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            routeLineApi.setNavigationRoutes(
                routeUpdateResult.navigationRoutes
            ) { value ->
                binding.mapView.mapboxMap.style?.apply {
                    routeLineView.renderRouteDrawData(this, value)
                }
            }
            viewportDataSource.onRouteChanged(routeUpdateResult.navigationRoutes.first())
            viewportDataSource.evaluate()
        } else {
            val style = binding.mapView.mapboxMap.style
            if (style != null) {
                routeLineApi.clearRouteLine { value ->
                    routeLineView.renderClearRouteLineValue(
                        style,
                        value
                    )
                }
                routeArrowView.render(style, routeArrowApi.clearArrows())
            }
            viewportDataSource.clearRouteData()
            viewportDataSource.evaluate()
        }
    }

    private lateinit var mapboxNavigation: MapboxNavigation

    private var currentLocation: Point? = null
    private var destination: Point? = null

    // 独立的 MapboxNavigationObserver 实例
    private val navigationObserver = object : MapboxNavigationObserver {
        @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
        override fun onAttached(mapboxNavigation: MapboxNavigation) {
            // 注册各种观察者
            mapboxNavigation.registerRoutesObserver(routesObserver)
            mapboxNavigation.registerLocationObserver(locationObserver)
            mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
            mapboxNavigation.startTripSession()
        }

        override fun onDetached(mapboxNavigation: MapboxNavigation) {
            // 注销各种观察者
            mapboxNavigation.unregisterRoutesObserver(routesObserver)
            mapboxNavigation.unregisterLocationObserver(locationObserver)
            mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (!MapboxNavigationApp.isSetup()) {
            MapboxNavigationApp.setup {
                NavigationOptions.Builder(context).build()
            }
        }
        MapboxNavigationApp.attach(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 从插件获取当前调用并存储到静态变量
        val plugin = CapacitorMapboxNavigationPlugin.getInstance()
        setCurrentCall(plugin?.getCurrentCall())
        currentCall?.resolve()
        // 获取当前的 MapboxNavigation 实例
        mapboxNavigation = MapboxNavigationApp.current()!!
        binding = MapboxActivityNavigationViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View, savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)


        // 检查位置权限
        checkLocationPermissionAndRequestRoute()

        // 注册 MapboxNavigation 观察者
        MapboxNavigationApp.registerObserver(navigationObserver)

        // 将 fragment 的生命周期与 MapboxNavigation 绑定
        MapboxNavigationApp.attach(this)

        initNavigation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 解绑 MapboxNavigation 与 fragment 生命周期
        MapboxNavigationApp.detach(this)
        // 注销观察者
        MapboxNavigationApp.unregisterObserver(navigationObserver)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun checkLocationPermissionAndRequestRoute() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            // 权限已授予，如果已有位置则立即请求路线
            currentLocation?.let { origin ->
                destination?.let { findRoute(origin, it) }
            }
        } else {
            // 请求位置权限
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限授予，尝试请求路线
                checkLocationPermissionAndRequestRoute()
            } else {
                val data = JSObject()
                data.put("message", "位置权限被拒绝，无法获取当前位置")
                // 权限被拒绝，发送错误事件并关闭对话框
                sendDataToCapacitor(
                    status = "error",
                    type = "locationPermissionDenied",
                    content = data
                )
                dismiss()
            }
        }
    }

    override fun onDestroy() {
        currentCall = null
        super.onDestroy()
        if (::maneuverApi.isInitialized) maneuverApi.cancel()
        if (::routeLineApi.isInitialized) routeLineApi.cancel()
        if (::routeLineView.isInitialized) routeLineView.cancel()
        if (::speechApi.isInitialized) speechApi.cancel()
        if (::voiceInstructionsPlayer.isInitialized) voiceInstructionsPlayer.shutdown()
    }

    private fun initNavigation() {
        requireActivity().runOnUiThread {
            // 配置地图位置组件
            binding.mapView.location.apply {
                setLocationProvider(navigationLocationProvider)
                enabled = true
            }

            // 获取终点坐标
            destination = Point.fromLngLat(
                requireArguments().getDouble("toLng", 0.0),
                requireArguments().getDouble("toLat", 0.0)
            )

            // 检查位置权限并请求路线
            checkLocationPermissionAndRequestRoute()

            // 初始化导航相机
            viewportDataSource = MapboxNavigationViewportDataSource(binding.mapView.mapboxMap)
            navigationCamera = NavigationCamera(
                binding.mapView.mapboxMap,
                binding.mapView.camera,
                viewportDataSource
            )

            // 添加相机手势处理
            binding.mapView.camera.addCameraAnimationsLifecycleListener(
                NavigationBasicGesturesHandler(navigationCamera)
            )

            // 监听相机状态变化
            navigationCamera.registerNavigationCameraStateChangeObserver { navigationCameraState ->
                when (navigationCameraState) {
                    NavigationCameraState.TRANSITION_TO_FOLLOWING,
                    NavigationCameraState.FOLLOWING -> binding.recenter.visibility = View.INVISIBLE

                    NavigationCameraState.TRANSITION_TO_OVERVIEW,
                    NavigationCameraState.OVERVIEW,
                    NavigationCameraState.IDLE -> binding.recenter.visibility = View.VISIBLE
                }
            }

            // set the padding values depending on screen orientation and visible view layout
            if (this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                viewportDataSource.overviewPadding = landscapeOverviewPadding
            } else {
                viewportDataSource.overviewPadding = overviewPadding
            }
            if (this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                viewportDataSource.followingPadding = landscapeFollowingPadding
            } else {
                viewportDataSource.followingPadding = followingPadding
            }

            // 初始化距离格式化器
            val distanceFormatterOptions =
                DistanceFormatterOptions.Builder(requireContext()).roundingIncrement(Rounding.INCREMENT_FIVE).build()
            // 初始化操作 API
            maneuverApi = MapboxManeuverApi(
                MapboxDistanceFormatter(distanceFormatterOptions)
            )

            // 初始化行程进度 API
            tripProgressApi = MapboxTripProgressApi(
                TripProgressUpdateFormatter.Builder(requireContext())
                    .distanceRemainingFormatter(
                        DistanceRemainingFormatter(distanceFormatterOptions)
                    )
                    .timeRemainingFormatter(
                        TimeRemainingFormatter(requireContext())
                    )
                    .percentRouteTraveledFormatter(
                        PercentDistanceTraveledFormatter()
                    )
                    .estimatedTimeToArrivalFormatter(
                        EstimatedTimeToArrivalFormatter(requireContext(), TimeFormat.NONE_SPECIFIED)
                    )
                    .build()
            )

            // 初始化语音 API
            speechApi = MapboxSpeechApi(
                requireContext(),
                Locale.getDefault().language
            )

            // 初始化语音播放器
            voiceInstructionsPlayer = MapboxVoiceInstructionsPlayer(
                requireContext(),
                Locale.getDefault().language
            )

            // 初始化路线线 API 和视图
            val mapboxRouteLineViewOptions = MapboxRouteLineViewOptions.Builder(requireContext())
                .routeLineBelowLayerId("road-label-navigation")
                .build()
            val mapboxRouteLineApiOptions = MapboxRouteLineApiOptions.Builder()
                .build()
            routeLineApi = MapboxRouteLineApi(mapboxRouteLineApiOptions)
            routeLineView = MapboxRouteLineView(mapboxRouteLineViewOptions)

            // 初始化路线箭头 API 和视图
            val routeArrowOptions = RouteArrowOptions.Builder(requireContext()).build()
            routeArrowView = MapboxRouteArrowView(routeArrowOptions)

            // 加载地图样式
            binding.mapView.mapboxMap.loadStyle(NavigationStyles.NAVIGATION_DAY_STYLE) {
                // 样式加载完成
            }

            // 设置按钮点击事件
            binding.stop.setOnClickListener {
                dismiss()
            }

            binding.recenter.setOnClickListener {
                navigationCamera.requestNavigationCameraToFollowing()
                binding.routeOverview.showTextAndExtend(BUTTON_ANIMATION_DURATION)
            }

            binding.routeOverview.setOnClickListener {
                navigationCamera.requestNavigationCameraToOverview()
                binding.recenter.showTextAndExtend(BUTTON_ANIMATION_DURATION)
            }

            binding.soundButton.setOnClickListener {
                isVoiceInstructionsMuted = !isVoiceInstructionsMuted
                if (isVoiceInstructionsMuted) {
                    binding.soundButton.muteAndExtend(BUTTON_ANIMATION_DURATION)
                    voiceInstructionsPlayer.volume(SpeechVolume(0f))
                } else {
                    binding.soundButton.unmuteAndExtend(BUTTON_ANIMATION_DURATION)
                    voiceInstructionsPlayer.volume(SpeechVolume(1f))
                }
            }

            binding.soundButton.unmute()
        }
    }

    private fun findRoute(origin: Point, destination: Point) {
        mapboxNavigation.requestRoutes(
            RouteOptions.builder()
                .applyDefaultNavigationOptions(DirectionsCriteria.PROFILE_CYCLING)
                .applyLanguageAndVoiceUnitOptions(requireContext())
                .coordinatesList(listOf(origin, destination))
                .layersList(listOf(mapboxNavigation.getZLevel(), null))
                .alternatives(true)
                .build(),
            object : NavigationRouterCallback {
                override fun onCanceled(
                    routeOptions: RouteOptions,
                    @RouterOrigin routerOrigin: String
                ) {
                    Log.e("Mapbox Navigation", "onCanceled")
                    val data = JSObject()
                    data.put("message", "Route Navigation cancelled")
                    finishNavigation(
                        status = "failure",
                        type = "on_cancelled",
                        content = data
                    )
                    dismiss()
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    Log.e("Mapbox Navigation", "onFailure: $reasons")
                    val data = JSObject()
                    data.put("message", "Failed to calculate route")
                    finishNavigation(
                        status = "failure",
                        type = "on_failure",
                        content = data
                    )
                    dismiss()
                }

                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    @RouterOrigin routerOrigin: String
                ) {
                    setRouteAndStartNavigation(routes)
                }
            }
        )
    }

    private fun setRouteAndStartNavigation(routes: List<NavigationRoute>) {
        mapboxNavigation.setNavigationRoutes(routes)
        binding.soundButton.visibility = View.VISIBLE
        binding.routeOverview.visibility = View.VISIBLE
        binding.tripProgressCard.visibility = View.VISIBLE
        isVoiceInstructionsMuted = !isVoiceInstructionsMuted
        // Show screen mirroring confirmation dialog
        AlertDialog.Builder(requireContext())
            .setTitle("投屏确认")
            .setMessage("是否开启投屏？")
            .setPositiveButton("开启") { _, _ ->
                val data = JSObject()
                data.put("enabled", true)
                CapacitorMapboxNavigationPlugin.getInstance()?.triggerScreenMirroringEvent(data)
            }
            .setNegativeButton("取消") { _, _ ->
                val data = JSObject()
                data.put("enabled", false)
                CapacitorMapboxNavigationPlugin.getInstance()?.triggerScreenMirroringEvent(data)
            }
            .show()
        navigationCamera.requestNavigationCameraToFollowing()
    }

    // 导航完成时调用，结束调用并关闭 Activity
    private fun finishNavigation(status: String, type: String, content: JSObject) {
        sendDataToCapacitor(status, type, content)

        // 释放引用并结束 Activity
        currentCall = null
    }

    /**
     * 发送数据到Capacitor插件
     */
    private fun sendDataToCapacitor(status: String, type: String, content: JSObject) {
        val plugin = CapacitorMapboxNavigationPlugin.getInstance()
        val result = JSObject()
        result.put("status", status)
        result.put("type", type)
        result.put("content", content)

        if (type == "onRouteProgressChange") {
            // 导航进度更新使用事件通知
            plugin?.triggerRouteProgressEvent(result)
        } else {
            // 其他类型使用Promise回调
            currentCall?.resolve(result)
        }
    }
}