# HID descriptor and SDP interoperability audit

Unreleased audit against published v1.2.0 plus the preserved local recovery/diagnostic improvements. Reported Realme RMX3581 / Android 11 evidence: registration succeeds; an accepted connection remains profile state 1 for four attempts; state 0 follows app timeout cleanup. Vivo works with the same Ubuntu host. This establishes failure before the app observes usable HID, not the exact underlying cause.

## Descriptor results

The actual composite descriptor is 119 bytes / 61 short items. Three collections are balanced: Mouse Application -> Pointer Physical, followed by Keyboard Application. Report IDs are nonzero and distinct. No long items or four-byte short values occur. Global ranges are reset before keyboard fields; local usages are consumed/reset at main items.

| Report | Fields in descriptor order | Input payload | Bluetooth report with ID (excluding HIDP header) |
|---|---|---|---|
| Mouse ID 2 | Three button bits, five constant bits, signed relative X/Y/wheel (-127..127) | 4 bytes | 5 bytes |
| Keyboard ID 1 | Eight modifier bits E0..E7, reserved byte, six key array slots 00..73 | 8 bytes | 9 bytes |
| Keyboard output ID 1 | Five LED bits, three constant bits | 1 byte | 2 bytes |

sendReport receives the ID separately: no embedded ID in either payload. Android's native layer inserts that ID. Boot mode uses keyboard ID 1 / eight-byte payload and mouse ID 2 / three-byte payload, matching the fixed Bluetooth boot formats. USB boot interface restrictions must not be misapplied to Bluetooth combo devices. The wheel is only transmitted in report mode.

The descriptor matches USB HID item semantics and Bluetooth SIG keyboard/mouse boot guidance. Automated checks are not a Bluetooth qualification or an on-air SDP validation.

## SDP registration audit

The existing inputs remain exactly: name BluePad, description Mouse and US keyboard, provider BluePad, subclass SUBCLASS1_COMBO = 0xC0, descriptor = the above 119 bytes, null incoming/outgoing QoS. The two subclass boot bits match keyboard/mouse support. All strings are short ASCII with no embedded NUL and comfortably within Android 11 native 50-byte buffers including the terminating NUL. The 119-byte descriptor also fits its one-byte nested SDP sequence length (119 + 4 = 123). Changing service names or using an unrelated subclass has no evidential justification.

Android owns the rest of the SDP record. In inspected AOSP Android 11, HID_DevAddRecord creates HID UUID 0x1124, L2CAP control/interrupt protocol lists, report descriptor type 0x22, parser version 0x0111, US country code 0x21, virtual cable/reconnect/normal-connectable flags and boot capability. App registration does not allow overriding these individual attributes. Vendor output must be read from the host before attributing a malformed field to BluePad.

Important confirmed *platform-source* gap: bta_hd_register_act sets registration success and calls HID_DevAddRecord without checking its return value. Thus a successful registration callback does not prove successful publication of a complete SDP record. This is not proof of the Realme firmware's actual implementation or a demonstrated BluePad defect. There is no supported API to inspect that native error or construct a replacement SDP service record from this app.

The same Android 11 descriptor framing walk treats size code 3 as three bytes rather than HID's four; this is a potential future stack limitation, not a cause here, since every data-bearing item in BluePad uses one byte. Its nested SDP length encoding has a similar future large-descriptor limitation; current length is safe. Neither observation justifies changing the current descriptor.

## Connection/lifecycle and host comparison

Profile acquisition -> registerApp -> registered callback -> connect bonded host is compliant with the Android API. Acceptance is not success. BluePad uses callbacks and HID-specific snapshots, not generic ACL connectivity. Previous fixes reconcile already-connected/missing-callback states, bound disconnection waits, preserve generation fences and maintain clean input sessions. The observed profile state remaining 1 means those reconciliation fixes cannot manufacture success.

Android 11 HidDeviceService forwards registration metadata to native code, checks registered caller ownership for connect and manages foreground registration. Realme's successful registration excludes initial profile absence but not later vendor/native negotiation problems. Android's public connection callback contains only device and state, no SDP/L2CAP failure status. No newly demonstrated registration-to-connect race was found in this audit.

BlueZ's input source requires control and interrupt channels and an available HID SDP record before completing input setup. Missing/invalid HIDDescriptorList is rejected. In this source, ENOENT from extract_hid_record specifically represents an absent record, whereas malformed descriptor-list structure returns EINVAL. This does not establish which branch Ubuntu takes for Realme; no matching host capture has been provided. Reviewed upstream source is not proof of the installed Ubuntu version's exact code.

## Evidence classification

| Classification | Finding |
|---|---|
| Confirmed BluePad implementation defect | No additional connection/protocol defect established. Previous recovery fixes remain preserved. |
| Standards-compliance risk | Actual vendor-generated SDP and boot/profile attributes are not yet validated on the wire. No current descriptor mismatch found. |
| Plausible compatibility hypothesis | Incomplete/unavailable SDP, host discovery policy, security/channel negotiation, vendor HID stack behavior or profile-role interaction. |
| Unverifiable without host/controller evidence | Which endpoint refuses/stalls SDP or control/interrupt establishment, actual SDP contents, security failures, or whether establishment would succeed after 12 seconds. |

Timeout cleanup explains the subsequent disconnect callbacks, but not why negotiation stays CONNECTING. No timeout increase, descriptor rewrite, new pairing loop or vendor workaround is included.

## Changes and tests

Only registration strings/subclass were centralized in HidRegistration for direct contract tests; values and descriptor passed to the Android constructor are unchanged. Added field-level parsing tests against the real descriptor (signed ranges, usages, flags, reserved fields, collections, encoded lengths and Android 11 item framing). Added SDP input tests for subclass bits, ID compatibility, string safety and native length bounds. Existing connection lifecycle and diagnostic tests remain in the full suite. JVM tests do not validate native SDP publication or real controller behavior.

## Minimum remaining experiment

Keep the diagnostic APK open on Realme. In two Ubuntu terminals, capture one connection attempt (stop each command with Ctrl+C after failure):

```bash
sudo btmon -w /tmp/bluepad-realme.btsnoop
```

```bash
sudo journalctl -fu bluetooth -o short-monotonic | tee /tmp/bluepad-realme-bluez.log
```

Then copy BluePad's local diagnostics. Repeat once with Vivo using different filenames to compare a known-good negotiation. Do not type or operate other Bluetooth devices during these short connection-only captures. Unlike the app's redacted report, controller/journal captures can contain Bluetooth addresses and other device metadata; keep them private.

Inspect SDP ServiceSearchAttributeResponse for HID UUID 0x1124, attributes 0x0004/0x000D (L2CAP PSM 0x0011/0x0013), 0x0202 (subclass), 0x0206 (descriptor type 0x22, all 119 bytes), and 0x020E (boot). Inspect control and interrupt channel connection/configuration requests and result codes, security/encryption failures and host cleanup occurring *before* the app's 12-second cleanup. If no SDP response is exchanged, a read-only SDP browse while registered may be needed; do not delete pairing or alter BlueZ configuration. These captures require developer analysis but do not imply that future users should troubleshoot the OS.

## Sources inspected

- [USB HID 1.11](https://www.usb.org/sites/default/files/hid1_11.pdf), descriptor item/report semantics and example mouse/keyboard fields.
- [Bluetooth SIG HID keyboard/mouse guidance](https://www.bluetooth.com/wp-content/uploads/2023/08/HID-Lite-WP-V10.pdf), boot report IDs and payloads, SDP discovery and channel establishment.
- [Android BluetoothHidDevice](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice), registration, connect and sendReport contracts.
- [AOSP Android 11 HidDeviceService](https://android.googlesource.com/platform/packages/apps/Bluetooth/+/refs/heads/android11-release/src/com/android/bluetooth/hid/HidDeviceService.java).
- [AOSP Android 11 bta_hd_act](https://android.googlesource.com/platform/system/bt/+/refs/heads/android11-release/bta/hd/bta_hd_act.cc), framing, registration and report-ID handling.
- [AOSP Android 11 registration buffers](https://android.googlesource.com/platform/system/bt/+/refs/heads/android11-release/bta/hd/bta_hd_int.h), 50-byte text fields.
- [AOSP Android 11 hidd_api](https://android.googlesource.com/platform/system/bt/+/refs/heads/android11-release/stack/hid/hidd_api.cc), SDP construction.
- [BlueZ input implementation](https://github.com/bluez/bluez/blob/master/profiles/input/device.c), SDP extraction and input setup.

## Complete byte/item audit

Every byte is accounted for below. Offsets are decimal; item bytes are hexadecimal. Flags are decoded in the Input/Output rows; usage values are interpreted in the report summary above.

| Offset (decimal) | Bytes (hex) | Item |
|---|---|---|
| 000 | 05 01 | Usage Page: 1 |
| 002 | 09 02 | Usage: 2 |
| 004 | A1 01 | Collection: Application |
| 006 | 85 02 | Report ID: 2 |
| 008 | 09 01 | Usage: 1 |
| 010 | A1 00 | Collection: Physical |
| 012 | 05 09 | Usage Page: 9 |
| 014 | 19 01 | Usage Minimum: 1 |
| 016 | 29 03 | Usage Maximum: 3 |
| 018 | 15 00 | Logical Minimum: 0 |
| 020 | 25 01 | Logical Maximum: 1 |
| 022 | 95 03 | Report Count: 3 |
| 024 | 75 01 | Report Size: 1 |
| 026 | 81 02 | Input: Data, Variable, Absolute |
| 028 | 95 01 | Report Count: 1 |
| 030 | 75 05 | Report Size: 5 |
| 032 | 81 03 | Input: Constant, Variable, Absolute |
| 034 | 05 01 | Usage Page: 1 |
| 036 | 09 30 | Usage: 48 |
| 038 | 09 31 | Usage: 49 |
| 040 | 09 38 | Usage: 56 |
| 042 | 15 81 | Logical Minimum: -127 |
| 044 | 25 7F | Logical Maximum: 127 |
| 046 | 75 08 | Report Size: 8 |
| 048 | 95 03 | Report Count: 3 |
| 050 | 81 06 | Input: Data, Variable, Relative |
| 052 | C0 | End Collection |
| 053 | C0 | End Collection |
| 054 | 05 01 | Usage Page: 1 |
| 056 | 09 06 | Usage: 6 |
| 058 | A1 01 | Collection: Application |
| 060 | 85 01 | Report ID: 1 |
| 062 | 05 07 | Usage Page: 7 |
| 064 | 19 E0 | Usage Minimum: 224 |
| 066 | 29 E7 | Usage Maximum: 231 |
| 068 | 15 00 | Logical Minimum: 0 |
| 070 | 25 01 | Logical Maximum: 1 |
| 072 | 75 01 | Report Size: 1 |
| 074 | 95 08 | Report Count: 8 |
| 076 | 81 02 | Input: Data, Variable, Absolute |
| 078 | 75 08 | Report Size: 8 |
| 080 | 95 01 | Report Count: 1 |
| 082 | 81 03 | Input: Constant, Variable, Absolute |
| 084 | 05 08 | Usage Page: 8 |
| 086 | 19 01 | Usage Minimum: 1 |
| 088 | 29 05 | Usage Maximum: 5 |
| 090 | 75 01 | Report Size: 1 |
| 092 | 95 05 | Report Count: 5 |
| 094 | 91 02 | Output: Data, Variable, Absolute |
| 096 | 75 03 | Report Size: 3 |
| 098 | 95 01 | Report Count: 1 |
| 100 | 91 03 | Output: Constant, Variable, Absolute |
| 102 | 05 07 | Usage Page: 7 |
| 104 | 19 00 | Usage Minimum: 0 |
| 106 | 29 73 | Usage Maximum: 115 |
| 108 | 15 00 | Logical Minimum: 0 |
| 110 | 25 73 | Logical Maximum: 115 |
| 112 | 75 08 | Report Size: 8 |
| 114 | 95 06 | Report Count: 6 |
| 116 | 81 00 | Input: Data, Array, Absolute |
| 118 | C0 | End Collection |
