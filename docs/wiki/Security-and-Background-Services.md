# Security and background services

VISORA uses supported application APIs and stays within its sandbox. It does not replace Fire OS, write protected partitions, assume root, or assign VPN addresses locally.

Tailscale reconnection is requested through an exported supported receiver when available. Success is observed separately using actual VPN transport and assigned address. Silent reconnection depends on prior consent, installed client capabilities, and operating-system background restrictions. No external activity is automatically opened as a fallback. Optional battery optimization exemption can be requested through an authorized ADB session using the narrowly scoped helper script.

AirScreen monitoring discovers the local advertised AirPlay endpoint and tests its TCP availability. Advertisement and port reachability do not prove end-to-end media playback. External casting mode is an explicit user action; private services are not forcibly started.

SSH uses the vendored Dropbear server with password login, root login, forwarding, agent forwarding, X11, and SFTP disabled. Enable it only after installing an Ed25519 public key through settings. Its listener binds only the observed tailnet address and runs with VISORA's application UID. It has no root or ADB capabilities. The host key is generated and retained in application-private storage. Verify its fingerprint through a trusted channel before connecting. Restrict access with tailnet ACLs. Never publish private keys or expose management ports publicly.

The native parent-death wrapper stops the listener if the application process dies. Existing authenticated command processes can remain until their idle timeout. Turning SSH off stops the listener; it is not an operating-system-wide session revocation mechanism.

Alexa launch behavior depends on supported platform discovery and skills. Custom voice commands and custom mode intents require a supported integration; arbitrary phrases are not presumed available. Firmware startup screens and system-level daemons are outside the application's capabilities.
