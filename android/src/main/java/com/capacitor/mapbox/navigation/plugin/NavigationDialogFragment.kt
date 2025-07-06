package com.capacitor.mapbox.navigation.plugin

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.annotation.RequiresPermission
import androidx.fragment.app.DialogFragment
import com.capacitor.mapbox.navigation.plugin.databinding.MapboxActivityNavigationViewBinding
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.TimeFormat
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.base.route.NavigationRouterCallback
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
import com.mapbox.navigation.voice.model.SpeechAnnouncement
import com.mapbox.navigation.voice.model.SpeechError
import com.mapbox.navigation.voice.model.SpeechValue
import com.mapbox.navigation.voice.model.SpeechVolume
import java.util.Locale

class NavigationDialogFragment : DialogFragment() {

    private val BUTTON_ANIMATION_DURATION = 300L
    private lateinit var binding: MapboxActivityNavigationViewBinding
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
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
        // 获取当前的 MapboxNavigation 实例
        mapboxNavigation = MapboxNavigationApp.current()!!
        binding = MapboxActivityNavigationViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


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

override fun onDestroy() {
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

            // 获取起点和终点坐标
            val origin = Point.fromLngLat(
                requireArguments().getDouble("fromLng", 0.0),
                requireArguments().getDouble("fromLat", 0.0)
            )
            val destination = Point.fromLngLat(
                requireArguments().getDouble("toLng", 0.0),
                requireArguments().getDouble("toLat", 0.0)
            )

            // 请求路线
            findRoute(origin, destination)

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

            // 初始化距离格式化器
            val distanceFormatterOptions =
                DistanceFormatterOptions.Builder(requireContext()).build()

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
                    dismiss()
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
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
        navigationCamera.requestNavigationCameraToFollowing()
    }
}