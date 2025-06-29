import type { PermissionState } from '@capacitor/core';
import type { PluginListenerHandle } from '@capacitor/core/types/definitions';

export interface CapacitorMapboxNavigationPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
  show(options: MapboxNavOptions): Promise<MapboxResult>;
  history(): Promise<any>;
  requestPermissions(): Promise<PermissionStatus>;
  checkPermissions(): Promise<PermissionStatus>;
  addListener(eventName: 'onRouteProgressChange', listenerFunc: (data: any) => any): Promise<PluginListenerHandle>;
  addListener(eventName: 'startScreenMirroring', listenerFunc: (data: StartScreenMirroringEvent) => any): Promise<PluginListenerHandle>;
  removeAllListeners(): Promise<void>;
}

export interface PermissionStatus {
  location: PermissionState;
}

export interface MapboxResult {
  status: 'success' | 'failure';
  type:
    | 'on_failure'
    | 'on_cancelled'
    | 'on_stop'
    | 'on_progress_update'
    | 'on_arrive';
  data: string;
}

export interface MapboxNavOptions {
  routes: LocationOption[];
  simulating?: boolean;
}

export interface LocationOption {
  latitude: number;
  longitude: number;
}

export interface StartScreenMirroringEvent {
  enabled: boolean;
}
