package com.capacitor.mapbox.navigation.plugin

import android.content.Intent
import androidx.activity.result.ActivityResult
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
    )]
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
    fun show(call: PluginCall){
        if(getPermissionState("location") != PermissionState.GRANTED){
            requestPermissionForAlias( "location",call, "permissionCallback")
        } else {
            call.setKeepAlive(true)
            currentCall = call
            startNavigation(call)
        }
    }

    fun startNavigation(call: PluginCall) {
        val routesArray = call.getArray("routes")

        if (routesArray != null && routesArray.length() == 2) {
            val fromLocation = routesArray.getJSONObject(0)
            val toLocation = routesArray.getJSONObject(1)

            val fromLat = fromLocation.getDouble("latitude")
            val fromLng = fromLocation.getDouble("longitude")
            val toLat = toLocation.getDouble("latitude")
            val toLng = toLocation.getDouble("longitude")

            val intent = Intent(this.getBridge().context, NavigationActivity::class.java)
            intent.putExtra("fromLat", fromLat)
            intent.putExtra("fromLng", fromLng)
            intent.putExtra("toLat", toLat)
            intent.putExtra("toLng", toLng)

            val simulate = call.getBoolean("simulating", true)
            intent.putExtra("simulate",simulate)

            startActivityForResult(call, intent, "navigationCallback")
        } else {
            call.reject("Invalid routes data")
        }
    }

    @ActivityCallback
    private fun navigationCallback(call: PluginCall?, result: ActivityResult) {
        if (call == null) {
            return
        }

        // Do something with the result data
        var resultIntent = result.data

        var resultStatus = resultIntent?.getStringExtra("status")
        var resultType = resultIntent?.getStringExtra("type")
        var resultData = resultIntent?.getStringExtra("data")


        val data = JSObject()

        data.put("status",resultStatus)
        data.put("type",resultType)
        data.put("data",resultData)

        call.resolve(data)
        currentCall = null
    }
    @PermissionCallback
    private fun permissionCallback(call: PluginCall) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            startNavigation(call);
        } else {
            call.reject("Permission is required to take a picture");
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
}
