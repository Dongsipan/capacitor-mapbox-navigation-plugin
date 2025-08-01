### Notice:

This is a fork from the original plugin [capacitor-mapbox-navigation](https://github.com/visio-soft/capacitor-mapbox) to fix some issues and add some features. The original plugin was not maintained and had some issues.
feel free to use this plugin and contribute to it.

# capacitor-mapbox-navigation

Capacitor plugin to implement Turn-by-Turn Mapbox navigation.

## RoadMap

- Bridge event binding.
- ✅ onNavigationStop callback implementation

## Installation Requirements

Before installing the SDK, you will need to gather the appropriate credentials. The SDK requires two pieces of sensitive information from your Mapbox account. If you don't have a Mapbox account: [sign up](https://account.mapbox.com/auth/signup/) and navigate to your [Account page](https://account.mapbox.com/). You'll need:

- **A public access token**: From your account's [tokens page](https://account.mapbox.com/access-tokens/), you can either copy your _default public token_ or click the **Create a token** button to create a new public token.
- **A secret access token with the `Downloads:Read` scope**.

1. From your account's [tokens page](https://account.mapbox.com/access-tokens/), click the **Create a token** button.
1. From the token creation page, give your token a name and make sure the box next to the `Downloads:Read` scope is checked.
1. Click the **Create token** button at the bottom of the page to create your token.
1. The token you've created is a _secret token_, which means you will only have one opportunity to copy it somewhere secure.

## Install

```bash
npm install capacitor-mapbox-navigation
npx cap sync
```

## IOS instructions


**Configure your secret token.**

Your secret token enables you to download the SDK directly from Mapbox. To use your secret token, you will need to store it in a .netrc file in your home directory (not your project folder). This approach helps avoid accidentally exposing your secret token by keeping it out of your application's source code. Depending on your environment, you may have this file already, so check first before creating a new one.

The **.netrc** file is a plain text file that is used in certain development environments to store credentials used to access remote servers. The login should always be mapbox. It should not be your personal username used to create the secret token. To set up the credentials required to download the SDK, add the following entry to your .netrc file:

```bash
machine api.mapbox.com
login mapbox
password YOUR_SECRET_MAPBOX_ACCESS_TOKEN
```

To configure your public access token, open your project's **Info.plist** file and add a **MBXAccessToken** key whose value is your public access token.

#### Permission

In order for the SDK to track the user’s location as they move along the route, set **NSLocationWhenInUseUsageDescription** to:

Shows your location on the map and helps improve the map.

Users expect the SDK to continue to track the user’s location and deliver audible instructions even while a different application is visible or the device is locked. Go to the Signing & Capabilities tab. Under the Background Modes section, enable “**Audio, AirPlay, and Picture in Picture**” and “**Location updates**”. (Alternatively, add the audio and location values to the **UIBackgroundModes** array in the Info tab.)

---

### Android Specific Instructions

Place your secret token in your android app's top level `gradle.properties` file:

```
MAPBOX_DOWNLOADS_TOKEN=SECRET_TOKEN_HERE
```

Open up your _project-level_ `build.gradle` file. Declare the Mapbox Downloads API's `releases/maven` endpoint in the `repositories` block.

```gradle
allprojects {
    repositories {
        maven {
            url 'https://api.mapbox.com/downloads/v2/releases/maven'
            authentication {
                basic(BasicAuthentication)
            }
            credentials {
                // Do not change the username below.
                // This should always be `mapbox` (not your username).
                username = "mapbox"
                // Use the secret token you stored in gradle.properties as the password
                password = project.properties['MAPBOX_DOWNLOADS_TOKEN'] ?: ""
            }
        }
    }
}
```

Place your public token in values/mapbox_access_token.xml `android/app/src/main/res/values/mapbox_access_token.xml`

```xml
<resources xmlns:tools="http://schemas.android.com/tools">
    <string name="mapbox_access_token"><PUT_YOUR_ACCESS_TOKEN_HERE></string>
</resources>
```

For more information you can read the [docs provided by Mapbox](https://docs.mapbox.com/android/navigation/overview/#configure-credentials).

## Usage

### Basic Navigation

```typescript
import { CapacitorMapboxNavigation } from '@dongsp/capacitor-mapbox-navigation';

// Start navigation
const result = await CapacitorMapboxNavigation.show({
  routes: [
    { latitude: 31.297905645089603, longitude: 120.54365934218187 }, // Start point
    { latitude: 31.1262937, longitude: 120.4477933 }, // Destination
  ],
  simulating: true,
});

console.log('Navigation result:', result);
```

### Listening to Navigation Events

```typescript
import { CapacitorMapboxNavigation } from '@dongsp/capacitor-mapbox-navigation';

// Listen for navigation stop events
CapacitorMapboxNavigation.addListener('onNavigationStop', (data) => {
  console.log('Navigation stopped:', data);
  // Handle navigation stop - e.g., show summary, save trip data, etc.
});

// Listen for route progress updates
CapacitorMapboxNavigation.addListener('onRouteProgressChange', (data) => {
  console.log('Route progress:', data);
  // Update UI with current navigation progress
});

// Listen for screen mirroring changes
CapacitorMapboxNavigation.addListener('onScreenMirroringChange', (data) => {
  console.log('Screen mirroring:', data.enabled);
  // Handle screen mirroring state changes
});
```

#### Permission


If you plan to display the user's location on the map or get the user's location information you will need to add the ACCESS_COARSE_LOCATION permission in your application's AndroidManifest.xml. You also need to add ACCESS_FINE_LOCATION permissions if you need access to precise location.


```xml
<manifest ... >
  <!-- Always include this permission -->
  <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

  <!-- Include only if your app benefits from precise location access. -->
  <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
</manifest>

```

## Features

### Screen Mirroring Control

The plugin includes a screen mirroring control button that allows users to enable/disable screen mirroring functionality. When the button is clicked, it shows a confirmation dialog. Only after confirmation will it trigger an event that can be listened to in your web application. The initial navigation also shows a confirmation dialog for screen mirroring.

#### Usage Example

```typescript
import { CapacitorMapboxNavigation } from 'capacitor-mapbox-navigation';

// Listen for screen mirroring changes
CapacitorMapboxNavigation.addListener('onScreenMirroringChange', (data) => {
  console.log('Mirror status changed:', data.enabled);
  console.log('Timestamp:', data.timestamp);

  if (data.enabled) {
    // Handle mirror enabled
    console.log('Screen mirroring is now enabled');
  } else {
    // Handle mirror disabled
    console.log('Screen mirroring is now disabled');
  }
});
```


## API

<docgen-index>

* [`echo(...)`](#echo)
* [`show(...)`](#show)
* [`history()`](#history)
* [`requestPermissions()`](#requestpermissions)
* [`checkPermissions()`](#checkpermissions)
* [`addListener('onRouteProgressChange', ...)`](#addlisteneronrouteprogresschange-)
* [`addListener('onScreenMirroringChange', ...)`](#addlisteneronscreenmirroringchange-)
* [`addListener('onNavigationStop', ...)`](#addlisteneronnavigationstop-)
* [`addListener('onNavigationComplete', ...)`](#addlisteneronnavigationcomplete-)
* [`addListener('plusButtonClicked', ...)`](#addlistenerplusbuttonclicked-)
* [`addListener('minusButtonClicked', ...)`](#addlistenerminusbuttonclicked-)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### echo(...)

```typescript
echo(options: { value: string; }) => Promise<{ value: string; }>
```

| Param         | Type                            |
| ------------- | ------------------------------- |
| **`options`** | <code>{ value: string; }</code> |

**Returns:** <code>Promise&lt;{ value: string; }&gt;</code>

--------------------


### show(...)

```typescript
show(options: MapboxNavOptions) => Promise<MapboxResult>
```

| Param         | Type                                                          |
| ------------- | ------------------------------------------------------------- |
| **`options`** | <code><a href="#mapboxnavoptions">MapboxNavOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#mapboxresult">MapboxResult</a>&gt;</code>

--------------------


### history()

```typescript
history() => Promise<any>
```

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### requestPermissions()

```typescript
requestPermissions() => Promise<PermissionStatus>
```

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

--------------------


### checkPermissions()

```typescript
checkPermissions() => Promise<PermissionStatus>
```

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

--------------------


### addListener('onRouteProgressChange', ...)

```typescript
addListener(eventName: 'onRouteProgressChange', listenerFunc: (data: any) => any) => Promise<PluginListenerHandle>
```

| Param              | Type                                 |
| ------------------ | ------------------------------------ |
| **`eventName`**    | <code>'onRouteProgressChange'</code> |
| **`listenerFunc`** | <code>(data: any) =&gt; any</code>   |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('onScreenMirroringChange', ...)

```typescript
addListener(eventName: 'onScreenMirroringChange', listenerFunc: (data: ScreenMirroringChangeEvent) => any) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                                                |
| ------------------ | --------------------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'onScreenMirroringChange'</code>                                                              |
| **`listenerFunc`** | <code>(data: <a href="#screenmirroringchangeevent">ScreenMirroringChangeEvent</a>) =&gt; any</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('onNavigationStop', ...)

```typescript
addListener(eventName: 'onNavigationStop', listenerFunc: (data: OnNavigationStopEvent) => any) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                                      |
| ------------------ | ----------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'onNavigationStop'</code>                                                           |
| **`listenerFunc`** | <code>(data: <a href="#onnavigationstopevent">OnNavigationStopEvent</a>) =&gt; any</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('onNavigationComplete', ...)

```typescript
addListener(eventName: 'onNavigationComplete', listenerFunc: () => any) => Promise<PluginListenerHandle>
```

| Param              | Type                                |
| ------------------ | ----------------------------------- |
| **`eventName`**    | <code>'onNavigationComplete'</code> |
| **`listenerFunc`** | <code>() =&gt; any</code>           |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('plusButtonClicked', ...)

```typescript
addListener(eventName: 'plusButtonClicked', listenerFunc: (data: Record<string, never>) => any) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                             |
| ------------------ | -------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'plusButtonClicked'</code>                                                 |
| **`listenerFunc`** | <code>(data: <a href="#record">Record</a>&lt;string, never&gt;) =&gt; any</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('minusButtonClicked', ...)

```typescript
addListener(eventName: 'minusButtonClicked', listenerFunc: (data: Record<string, never>) => any) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                             |
| ------------------ | -------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'minusButtonClicked'</code>                                                |
| **`listenerFunc`** | <code>(data: <a href="#record">Record</a>&lt;string, never&gt;) =&gt; any</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

--------------------


### Interfaces


#### MapboxResult

| Prop         | Type                                                                                      |
| ------------ | ----------------------------------------------------------------------------------------- |
| **`status`** | <code>'success' \| 'failure'</code>                                                       |
| **`type`**   | <code>'onNavigationStop' \| 'on_failure' \| 'on_cancelled' \| 'on_progress_update'</code> |
| **`data`**   | <code>string</code>                                                                       |


#### MapboxNavOptions

| Prop             | Type                          |
| ---------------- | ----------------------------- |
| **`routes`**     | <code>LocationOption[]</code> |
| **`simulating`** | <code>boolean</code>          |


#### LocationOption

| Prop            | Type                |
| --------------- | ------------------- |
| **`latitude`**  | <code>number</code> |
| **`longitude`** | <code>number</code> |


#### PermissionStatus

| Prop           | Type                                                        |
| -------------- | ----------------------------------------------------------- |
| **`location`** | <code><a href="#permissionstate">PermissionState</a></code> |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### ScreenMirroringChangeEvent

| Prop          | Type                 |
| ------------- | -------------------- |
| **`enabled`** | <code>boolean</code> |


#### OnNavigationStopEvent

| Prop          | Type                                                 |
| ------------- | ---------------------------------------------------- |
| **`status`**  | <code>'success' \| 'failure'</code>                  |
| **`type`**    | <code>'onNavigationStop'</code>                      |
| **`content`** | <code>{ message: string; timestamp: number; }</code> |


### Type Aliases


#### PermissionState

<code>'prompt' | 'prompt-with-rationale' | 'granted' | 'denied'</code>


#### Record

Construct a type with a set of properties K of type T

<code>{ [P in K]: T; }</code>

</docgen-api>
