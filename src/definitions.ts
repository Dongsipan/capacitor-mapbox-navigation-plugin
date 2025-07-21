import type { PermissionState } from '@capacitor/core';
import type { PluginListenerHandle } from '@capacitor/core/types/definitions';

export interface CapacitorMapboxNavigationPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
  show(options: MapboxNavOptions): Promise<MapboxResult>;
  history(): Promise<any>;
  requestPermissions(): Promise<PermissionStatus>;
  checkPermissions(): Promise<PermissionStatus>;
  addListener(eventName: 'onRouteProgressChange', listenerFunc: (data: any) => any): Promise<PluginListenerHandle>;
  addListener(
    eventName: 'onScreenMirroringChange',
    listenerFunc: (data: ScreenMirroringChangeEvent) => any,
  ): Promise<PluginListenerHandle>;
  addListener(
    eventName: 'onNavigationStop',
    listenerFunc: (data: OnNavigationStopEvent) => any,
  ): Promise<PluginListenerHandle>;
  addListener(
    eventName: 'onNavigationComplete',
    listenerFunc: () => any,
  ): Promise<PluginListenerHandle>;
  addListener(
    eventName: 'plusButtonClicked',
    listenerFunc: (data: Record<string, never>) => any,
  ): Promise<PluginListenerHandle>;
  addListener(
    eventName: 'minusButtonClicked',
    listenerFunc: (data: Record<string, never>) => any,
  ): Promise<PluginListenerHandle>;
  removeAllListeners(): Promise<void>;
}

export interface PermissionStatus {
  location: PermissionState;
}

export interface MapboxResult {
  status: 'success' | 'failure';
  type: 'on_failure' | 'on_cancelled' | 'onNavigationStop' | 'on_progress_update';
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

export interface ScreenMirroringChangeEvent {
  enabled: boolean;
}

export interface OnNavigationStopEvent {
  status: 'success' | 'failure';
  type: 'onNavigationStop';
  content: {
    message: string;
    timestamp: number;
  };
}
