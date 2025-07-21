package com.capacitor.mapbox.navigation.plugin

import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.getcapacitor.*
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback

@CapacitorPlugin(
    name = "CapacitorMapboxNavigation",
    permissions = [
        Permission(
            alias = "location",
            strings = arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    ]
)
class CapacitorMapboxNavigationPlugin : Plugin() {
    private val implementation = CapacitorMapboxNavigation()
    private var currentCall: PluginCall? = null

    companion object {
        private var instance: CapacitorMapboxNavigationPlugin? = null

        fun getInstance(): CapacitorMapboxNavigationPlugin? {
            return instance
        }
    }

    override fun load() {
        super.load()
        instance = this
    }

    @PluginMethod
    fun echo(call: PluginCall) {
        val value = call.getString("value")

        val ret = JSObject()
        ret.put("value", value?.let { implementation.echo(it) })
        call.resolve(ret)
    }

    @PluginMethod
    fun show(call: PluginCall) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "permissionCallback")
        } else {
            call.setKeepAlive(true)
            currentCall = call
            startNavigation(call)
        }
    }

    fun startNavigation(call: PluginCall) {
        val routesArray = call.getArray("routes")

        if (routesArray != null && routesArray.length() > 0) {
            val toLocation = routesArray.getJSONObject(0)
            val toLat = toLocation.getDouble("latitude")
            val toLng = toLocation.getDouble("longitude")

            val dialogFragment = NavigationDialogFragment()
            val args = Bundle()
            args.putDouble("toLat", toLat)
            args.putDouble("toLng", toLng)
            dialogFragment.arguments = args
            val activity = getActivity()
            if (activity != null) {
                activity.runOnUiThread {
                    val dialogFragment = NavigationDialogFragment()
                    val args = Bundle()
                    args.putDouble("toLat", toLat)
                    args.putDouble("toLng", toLng)
                    dialogFragment.arguments = args
                    dialogFragment.show(activity.supportFragmentManager, "NavigationDialogFragment")
                }
            } else {
                call.reject("Activity not available")
            }
        } else {
            call.reject("Invalid routes data")
        }
    }

    @PermissionCallback
    private fun permissionCallback(call: PluginCall) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            startNavigation(call)
        } else {
            call.reject("Permission is required to take a picture")
        }
    }

    fun getCurrentCall(): PluginCall? {
        return currentCall
    }

    fun releaseCurrentCall() {
        currentCall = null
    }

    fun triggerRouteProgressEvent(data: JSObject) {
        if (hasListeners("onRouteProgressChange")) {
            notifyListeners("onRouteProgressChange", data)
        }
    }

    fun triggerScreenMirroringEvent(data: JSObject) {
        if (hasListeners("onScreenMirroringChange")) {
            notifyListeners("onScreenMirroringChange", data)
        }
    }

    fun triggerPlusButtonClicked(data: JSObject) {
        if (hasListeners("plusButtonClicked")) {
            notifyListeners("plusButtonClicked", data)
        }
    }

    fun triggerMinusButtonClicked(data: JSObject) {
        if (hasListeners("minusButtonClicked")) {
            notifyListeners("minusButtonClicked", data)
        }
    }

    fun triggerOnNavigationStopEvent(data: JSObject) {
        if (hasListeners("onNavigationStop")) {
            notifyListeners("onNavigationStop", data)
        }
    }
}