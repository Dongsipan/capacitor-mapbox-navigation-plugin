import Foundation
import Capacitor

import MapboxDirections
import MapboxCoreNavigation
import MapboxNavigation

struct Location: Codable {
    var _id: String = ""
    var longitude: Double = 0.0
    var latitude: Double = 0.0
    var when: String = ""
}

var lastLocation: Location?
var locationHistory: NSMutableArray?
var routes = [NSDictionary]()

func getNowString() -> String {
    let date = Date()
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ"
    return formatter.string(from: date)
}

// 智能距离格式化器，使用 Mapbox 官方的 RoundingTable 规则
class SmartDistanceFormatter {
    static func formatDistance(_ distance: CLLocationDistance) -> Double {
        // 使用 Mapbox 官方的 metric RoundingTable 规则
        let roundingTable = RoundingTable.metric
        let threshold = roundingTable.threshold(for: distance)
        let measurement = threshold.measurement(of: distance)
        
        // 返回以米为单位的数字
        return measurement.distance
    }
}

@objc(CapacitorMapboxNavigationPlugin)
public class CapacitorMapboxNavigationPlugin: CAPPlugin, NavigationViewControllerDelegate, CLLocationManagerDelegate {

    var permissionCallID: String?
    var callbackId: String?
    var locationManager = CLLocationManager()
    enum CallType {
        case permissions
    }
    private var callQueue: [String: CallType] = [:]
    var isNavigationActive = false
    var locationRequestCompletion: ((CLLocationCoordinate2D?) -> Void)?

    @objc override public func load() {
        // Called when the plugin is first constructed in the bridge
        locationHistory = NSMutableArray()

        // Observe application state changes
        NotificationCenter.default.addObserver(self, selector: #selector(applicationWillResignActive), name: UIApplication.willResignActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(applicationDidBecomeActive), name: UIApplication.didBecomeActiveNotification, object: nil)
    }

    // Application will resign active (e.g., goes to background)
    @objc func applicationWillResignActive() {
        if isNavigationActive {
            // Navigation is active, ensure idle timer remains disabled
            UIApplication.shared.isIdleTimerDisabled = true
        }
    }

    // Application did become active (e.g., comes to foreground)
    @objc func applicationDidBecomeActive() {
        if isNavigationActive {
            // Navigation is active, ensure idle timer remains disabled
            UIApplication.shared.isIdleTimerDisabled = true
        }
    }

    @objc func show(_ call: CAPPluginCall) {
        bridge?.saveCall(call)
        callbackId = call.callbackId
        lastLocation = Location(longitude: 0.0, latitude: 0.0)
        locationHistory?.removeAllObjects()
        routes = call.getArray("routes", NSDictionary.self) ?? [NSDictionary]()
        
        // 先获取当前位置
        getCurrentLocation { [weak self] coordinate in
            guard let self = self, let startCoord = coordinate else {
                self?.sendDataToCapacitor(status: "failure", type: "on_failure", content: "无法获取当前位置")
                return
            }
            var waypoints = [Waypoint(coordinate: startCoord)]
            // routes 作为后续 waypoint
            for route in routes {
                if let latitude = route["latitude"] as? NSNumber,
                   let longitude = route["longitude"] as? NSNumber {
                    let lat = latitude.doubleValue
                    let lon = longitude.doubleValue
                    let waypoint = Waypoint(coordinate: CLLocationCoordinate2DMake(lat, lon))
                    waypoints.append(waypoint)
                } else {
                    self.sendDataToCapacitor(status: "failure", type: "on_failure", content: "Failed to convert latitude and longitude to NSNumber")
                    return
                }
            }
            let isSimulate = call.getBool("simulating") ?? true
            let routeOptions = NavigationRouteOptions(waypoints: waypoints, profileIdentifier: .cycling)
            Directions.shared.calculate(routeOptions) { [weak self] (_, result) in
                switch result {
                case .failure(let error):
                    print(error.localizedDescription)
                    self?.sendDataToCapacitor(status: "failure", type: "on_failure", content: "no routes found")
                case .success(let response):
                    guard let route = response.routes?.first, let strongSelf = self else {
                        return
                    }

                    let navigationService = MapboxNavigationService(routeResponse: response, routeIndex: 0, routeOptions: routeOptions, simulating: isSimulate ? .always : .never)
                    
                    let navigationOptions = NavigationOptions(navigationService: navigationService)

                    let viewController = NavigationViewController(for: response, routeIndex: 0, routeOptions: routeOptions, navigationOptions: navigationOptions)
                    viewController.modalPresentationStyle = .overFullScreen
                    viewController.waypointStyle = .extrudedBuilding
                    viewController.delegate = strongSelf
                    
                    // 使用默认的智能距离格式化器
                    // iOS 端会自动使用智能单位显示距离

                    self?.keepAwake()

                    DispatchQueue.main.async {
                        self?.setCenteredPopover(viewController)
                        self?.bridge?.viewController?.present(viewController, animated: true, completion: {
                            // 显示投屏确认弹窗
                            let alert = UIAlertController(title: "开启投屏", message: "是否要开启投屏功能？", preferredStyle: .alert)
                            
                            // 取消按钮
                            alert.addAction(UIAlertAction(title: "取消", style: .cancel, handler: { _ in
                                self?.notifyListeners("onScreenMirroringChange", data: ["enabled": false])
                            }))
                            
                            // 开启按钮 - 发送事件到Capacitor
                            alert.addAction(UIAlertAction(title: "开启", style: .default, handler: { _ in
                                self?.notifyListeners("onScreenMirroringChange", data: ["enabled": true])
                            }))
                            
                            viewController.present(alert, animated: true, completion: nil)
                    
                            // 创建垂直按钮容器
                                let buttonContainer = UIView()
                                buttonContainer.translatesAutoresizingMaskIntoConstraints = false
                                viewController.view.addSubview(buttonContainer)
                                
                                // 创建加号按钮
                                let plusButton = UIButton(type: .system)
                                plusButton.setTitle("+", for: .normal)
                                plusButton.titleLabel?.font = UIFont.systemFont(ofSize: 24, weight: .bold)
                                guard let self = self else { return }
                                plusButton.addTarget(self, action: #selector(self.plusButtonTapped), for: .touchUpInside)
                                plusButton.backgroundColor = .white
                                plusButton.layer.cornerRadius = 25
                                plusButton.translatesAutoresizingMaskIntoConstraints = false
                                buttonContainer.addSubview(plusButton)
                                
                                // 创建减号按钮
                                let minusButton = UIButton(type: .system)
                                minusButton.setTitle("-", for: .normal)
                                minusButton.titleLabel?.font = UIFont.systemFont(ofSize: 24, weight: .bold)
                                minusButton.addTarget(self, action: #selector(self.minusButtonTapped), for: .touchUpInside)
                                minusButton.backgroundColor = .white
                                minusButton.layer.cornerRadius = 25
                                minusButton.translatesAutoresizingMaskIntoConstraints = false
                                buttonContainer.addSubview(minusButton)
                                
                                // 设置按钮容器约束 - 右侧中间
                                NSLayoutConstraint.activate([
                                    buttonContainer.trailingAnchor.constraint(equalTo: viewController.view.trailingAnchor, constant: -20),
                                    buttonContainer.centerYAnchor.constraint(equalTo: viewController.view.centerYAnchor),
                                    buttonContainer.widthAnchor.constraint(equalToConstant: 50)
                                ])
                                
                                // 设置按钮约束 - 垂直排列
                                NSLayoutConstraint.activate([
                                    plusButton.topAnchor.constraint(equalTo: buttonContainer.topAnchor),
                                    plusButton.centerXAnchor.constraint(equalTo: buttonContainer.centerXAnchor),
                                    plusButton.widthAnchor.constraint(equalToConstant: 50),
                                    plusButton.heightAnchor.constraint(equalToConstant: 50),
                                    
                                    minusButton.topAnchor.constraint(equalTo: plusButton.bottomAnchor, constant: 10),
                                    minusButton.centerXAnchor.constraint(equalTo: buttonContainer.centerXAnchor),
                                    minusButton.widthAnchor.constraint(equalToConstant: 50),
                                    minusButton.heightAnchor.constraint(equalToConstant: 50),
                                    minusButton.bottomAnchor.constraint(equalTo: buttonContainer.bottomAnchor)
                                ])
                        })
                    }
                }
            }
        }
    }

    public func keepAwake() {
        isNavigationActive = true
        UIApplication.shared.isIdleTimerDisabled = true
    }

    public func allowSleep() {
        // Re-enable idle timer and reset navigation active state
        UIApplication.shared.isIdleTimerDisabled = false
        isNavigationActive = false
    }

    public func navigationViewControllerDidDismiss(_ navigationViewController: NavigationViewController, byCanceling canceled: Bool) {
        sendDataToCapacitor(status: "success", type: "on_stop", content: "Navigation stopped")

        allowSleep()

        navigationViewController.dismiss(animated: true)
    }

    @objc func history(_ call: CAPPluginCall) {
        let jsonEncoder = JSONEncoder()
        do {
            let lastLocationJsonData = try jsonEncoder.encode(lastLocation)
            let lastLocationJson = String(data: lastLocationJsonData, encoding: String.Encoding.utf8)

            let swiftArray = locationHistory as AnyObject as! [Location]
            let locationHistoryJsonData = try jsonEncoder.encode(swiftArray)
            let locationHistoryJson = String(data: locationHistoryJsonData, encoding: String.Encoding.utf8)

            call.resolve([
                "lastLocation": lastLocationJson ?? "",
                "locationHistory": locationHistoryJson ?? ""
            ])
        } catch {
            call.reject("Error: Json Encoding Error")
        }
    }

    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if let location = locations.first {
            locationRequestCompletion?(location.coordinate)
            locationRequestCompletion = nil
        } else {
            locationRequestCompletion?(nil)
            locationRequestCompletion = nil
        }
    }

    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        locationRequestCompletion?(nil)
        locationRequestCompletion = nil
        let removalQueue = callQueue.filter { $0.value == .permissions }

        for (id, _) in removalQueue {
            if let call = bridge?.savedCall(withID: id) {
                call.reject(error.localizedDescription)
                bridge?.releaseCall(call)
            }
        }

        for (id, _) in callQueue {
            if let call = bridge?.savedCall(withID: id) {
                call.reject(error.localizedDescription)
            }
        }
    }

    public func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        let removalQueue = callQueue.filter { $0.value == .permissions }
        callQueue = callQueue.filter { $0.value != .permissions }

        for (id, _) in removalQueue {
            if let call = bridge?.savedCall(withID: id) {
                checkPermissions(call)
                bridge?.releaseCall(call)
            }
        }
    }

    @objc override public func checkPermissions(_ call: CAPPluginCall) {
        var status: String = ""

        if CLLocationManager.locationServicesEnabled() {
            switch CLLocationManager.authorizationStatus() {
            case .notDetermined:
                status = "prompt"
            case .restricted, .denied:
                status = "denied"
            case .authorizedAlways, .authorizedWhenInUse:
                status = "granted"
            @unknown default:
                status = "prompt"
            }
        } else {
            call.reject("Location services are not enabled")
            return
        }

        let result = [
            "location": status,
            "coarseLocation": status
        ]

        call.resolve(result)
    }

    @objc override public func requestPermissions(_ call: CAPPluginCall) {
        if CLLocationManager.locationServicesEnabled() {
            // If state is not yet determined, request perms.
            // Otherwise, report back the state right away
            if CLLocationManager.authorizationStatus() == .notDetermined {
                bridge?.saveCall(call)
                callQueue[call.callbackId] = .permissions

                DispatchQueue.main.async {
                    self.locationManager.delegate = self
                    self.locationManager.requestWhenInUseAuthorization()
                }
            } else {
                checkPermissions(call)
            }
        } else {
            call.reject("Location services are not enabled")
        }
    }

    public func navigationViewController(_ navigationViewController: NavigationViewController, didArriveAt waypoint: Waypoint) -> Bool {

        let jsonEncoder = JSONEncoder()
        do {
            var minDistance: CLLocationDistance = 0
            var locationId: String = ""
            for (i, route) in routes.enumerated() {
                // 检查 route["location"] 是否存在且类型正确
                guard let locationArray = route["location"] as? NSArray else {
                    continue // 跳过本次循环
                }
                // 检查 route["_id"] 是否存在且类型正确
                guard let currentLocationId = route["_id"] as? String else {
                    continue // 跳过本次循环
                }
                // 确保 locationArray 有足够元素（至少两个）
                guard locationArray.count >= 2 else {
                    continue // 元素不足，跳过
                }
                let coord1 = CLLocation(
                    latitude: locationArray[1] as! CLLocationDegrees,
                    longitude: locationArray[0] as! CLLocationDegrees
                )
                let coord2 = CLLocation(
                    latitude: waypoint.coordinate.latitude,
                    longitude: waypoint.coordinate.longitude
                )
                let distance = coord1.distance(from: coord2)
                if i == 0 || distance < minDistance {
                    minDistance = distance
                    locationId = currentLocationId // 使用检查后的 locationId
                }
            }
            let loc = Location(_id: locationId, longitude: waypoint.coordinate.longitude, latitude: waypoint.coordinate.latitude, when: getNowString())
            let locationJsonData = try jsonEncoder.encode(loc)
            let locationJson = String(data: locationJsonData, encoding: String.Encoding.utf8) ?? ""

            sendDataToCapacitor(status: "success", type: "on_arrive", content: locationJson)
        } catch {
            sendDataToCapacitor(status: "failure", type: "on_failure", content: "Error: Json Encoding Error")
        }
        return true
    }

    // 添加 RouteProgress 更新的代理方法
    public func navigationViewController(_ navigationViewController: NavigationViewController, didUpdate progress: RouteProgress, with location: CLLocation, rawLocation: CLLocation) {
        do {
            let currentStep = progress.currentLegProgress.currentStepProgress.step
            let instructionsDisplayedAlongStep = currentStep.instructionsDisplayedAlongStep
            let distanceRemaining = progress.distanceRemaining
            let stepDistanceRemaining = progress.currentLegProgress.currentStepProgress.distanceRemaining
            
            // 创建包含所有必要信息的字典
            var progressInfo: [String: Any] = [:]
            
            // 添加距离信息
            progressInfo["distanceRemaining"] = distanceRemaining
            // 对 stepDistanceRemaining 应用智能距离格式化
            progressInfo["stepDistanceRemaining"] = SmartDistanceFormatter.formatDistance(stepDistanceRemaining)
            
            // 添加指令信息（如果存在）
            if let instructions = instructionsDisplayedAlongStep {
                let jsonEncoder = JSONEncoder()
                let instructionsData = try jsonEncoder.encode(instructions[0])
                if let instructionsJson = String(data: instructionsData, encoding: .utf8) {
                    progressInfo["bannerInstructions"] = instructionsJson
                }
            } else {
                progressInfo["bannerInstructions"] = ""
            }
            
            // 直接使用 progressInfo 作为 content，不需要转换为字符串
            let data: [String: Any] = ["status": "success", "type": "onRouteProgressChange", "content": progressInfo]
            DispatchQueue.main.async {
                guard self.hasListeners("onRouteProgressChange") else { return }
                self.notifyListeners("onRouteProgressChange", data: data)
            }
        } catch {
            let errorContent: [String: String] = ["error": "Error encoding route progress data", "message": error.localizedDescription]
            let data: [String: Any] = ["status": "failure", "type": "onRouteProgressChange", "content": errorContent]
            DispatchQueue.main.async {
                guard self.hasListeners("onRouteProgressChange") else { return }
                self.notifyListeners("onRouteProgressChange", data: data)
            }
        }
    }

    @objc public func sendDataToCapacitor(status: String, type: String, content: String) {
        if let callID = callbackId, let call = bridge?.savedCall(withID: callID) {

            let data = ["status": status, "type": type, "content": content]
            call.resolve(data)
            bridge?.releaseCall(call)
        }

    }
    
    @objc func plusButtonTapped() {
        notifyListeners("plusButtonClicked", data: [:])
    }
    
    @objc func minusButtonTapped() {
        notifyListeners("minusButtonClicked", data: [:])
    }

    func getCurrentLocation(completion: @escaping (CLLocationCoordinate2D?) -> Void) {
        locationRequestCompletion = completion
        self.locationManager.delegate = self
        self.locationManager.requestWhenInUseAuthorization()
        self.locationManager.requestLocation()
    }
}
