// Capacitor native-shell configuration for DocShelf (Android); cleartext http allowed for LAN backends.
import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.docshelf.app',
  appName: 'DocShelf',
  webDir: 'dist/frontend/browser',
  server: {
    // The app talks to a backend on the LAN over plain http (e.g. http://192.168.1.10:8080).
    androidScheme: 'http',
    cleartext: true,
  },
  android: {
    allowMixedContent: true,
  },
  plugins: {
    LocalNotifications: {
      smallIcon: 'ic_stat_icon_config_sample',
      iconColor: '#1e5aa8',
    },
  },
};

export default config;
