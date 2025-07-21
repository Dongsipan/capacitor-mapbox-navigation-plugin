import { Geolocation } from '@capacitor/geolocation'
import { CapacitorMapboxNavigation } from '@dongsp/capacitor-mapbox-navigation'

const navigateBtn = document.getElementById('navigate-btn')

// 添加 onNavigationStop 事件监听器
CapacitorMapboxNavigation.addListener('onNavigationStop', (data) => {
  console.log('Navigation stopped:', data)
  alert('Navigation stopped: ' + JSON.stringify(data))
})

navigateBtn.addEventListener('click', async () => {
  const long = +document.getElementById('longitude').value
  const lat = +document.getElementById('latitude').value
  await navigateToAddressWithMapbox({ latitude: lat, longitude: long })
})

export const navigateToAddressWithMapbox = async ({
  latitude = 0,
  longitude = 0,
}) => {
  if (!isAddressValid({ latitude, longitude })) {
    return
  }

  try {
    await startNavigation({ latitude, longitude })
  } catch (error) {
    handleDeniedLocation(error)
  }
}
const startNavigation = async ({ latitude, longitude }) => {
  // const location = await Geolocation.getCurrentPosition({
  //   enableHighAccuracy: true,
  // })

  const location = {
    timestamp: 1750063181699,
    coords: {
      accuracy: 40,
      latitude: 31.297905645089603,
      longitude: 120.54365934218187,
      altitude: 0,
      altitudeAccuracy: null,
      heading: null,
      speed: null,
    },
  }

  console.log(location)

  const result = await CapacitorMapboxNavigation.show({
    routes: [
      {
        latitude: location.coords.latitude,
        longitude: location.coords.longitude,
      },
      { latitude: latitude, longitude: longitude },
    ],
    simulating: true,
  })

  if (result?.status === 'failure') {
    switch (result?.type) {
      case 'on_failure':
        alert('No routes found')
        break
      case 'on_cancelled':
        alert('Navigation cancelled')
        break
    }
  }
}

function isAddressValid({ latitude = 0, longitude = 0 }) {
  if (latitude === 0 || longitude === 0) {
    alert('Activity Address is not available')
    return false
  }

  return true
}
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const handleDeniedLocation = (error) => {
  if (error?.type === 'not_supported') {
    return alert('Navigation not supported on web')
  }
  alert('Error in getting location permission, please enable your gps location')
}
