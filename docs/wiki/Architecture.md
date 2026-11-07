# Architecture

The UI uses lightweight Android views, Canvas layers, a shared frame clock, and an OpenGL galaxy renderer. Rendering pauses when hidden or idle. Platform media APIs play cached promotional content.

| Component | Responsibility |
| --- | --- |
| StartupController | Bounded network wait and supported boot launch |
| DisplayManager | Immersive display and shared UI styling |
| DisplayViewport | Window fitting and overscan dimensions |
| PromotionEngine | Validated, atomic campaign and content cache |
| BrandingManager | Independent brand configuration and logos |
| CastingController | Explicit authorized application launch |
| NetworkMonitor | Network availability |
| TailscaleMonitor | Observed VPN state and supported reconnect request |
| RemoteManagementClient | Authenticated tailnet HTTPS protocol |
| DeviceHealthMonitor | Real process, memory, storage, and connectivity data |
| UpdateManager | Staging and signing identity checks |
| SettingsManager | Local configuration and protected enrollment credential |
| ServiceSupervisor | Central background diagnostics and bounded recovery |

Full diagnostic cycles default to five minutes. Lightweight event monitoring and recovery checks are separate. Failures use bounded retries and timeouts. Cached display content remains available when the server is offline. A reported request never means verified service success.
