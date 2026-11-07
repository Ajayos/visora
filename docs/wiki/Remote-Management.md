# Remote management

The client implements a proposed protocol. Match and review the server contract before enabling enrollment. No backend server, hosting project, or administrator upload interface is included in this repository.

Use a valid HTTPS tailnet origin with a trusted certificate. Connections are opened through an observed VPN network, reject cross-origin destinations and redirects, and require a protected enrollment credential. Configure the origin and a one-time registration code locally. Enable remote commands separately after successful enrollment.

| Endpoint | Request and result |
| --- | --- |
| POST /api/visora/v1/register | One-time code as bearer credential; model and appVersion JSON. Returns deviceId, token, and base64 DER RSA commandPublicKey. |
| GET /api/visora/v1/devices/{deviceId}/status | Authenticated response must identify the same deviceId. |
| POST /api/visora/v1/devices/{deviceId}/sync | Sends health, cached campaign revision, and last reserved command sequence. Returns optional campaign and signed commands. |
| POST /api/visora/v1/devices/{deviceId}/ack | Sends command sequence and actual execution result. |

Commands use RSA signatures and a signed JSON payload. See CommandVerifier and its tests for the exact envelope and canonical signed bytes. Device identity, expiry, local authorization, and monotonically increasing sequence numbers are checked. Sequence reservation occurs before execution for at-most-once behavior; retry with a new sequence after failure.

Campaign downloads are size-bounded, hashed, and atomically committed. Keep AOS and Deluper campaign configurations separate. Existing validated content remains available after a failed download or offline restart.

Supported actions are constrained to the local allowlist and Android permissions. Application installation uses the supported system installer and may need user confirmation. Restarting VISORA is distinct from rebooting the operating system. Remote shell execution, silent privileged installs, and firmware control are not provided.
