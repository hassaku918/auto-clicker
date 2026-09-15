# AutoClickerBlocker v5

v5 separates the two touch-blocking trigger profiles.

## App-launch trigger
- Enable/disable independently
- Target package
- Touch-block radius
- Blocking duration

## Time trigger
- Enable/disable independently
- Hour/minute
- Touch-block radius
- Blocking duration
- Daily rescheduling

These values are stored under separate preference keys, so changing one profile does not overwrite the other.

Other v4 features remain:
- Menu start/stop/add marker/delete marker
- Movable markers
- Multiple click points in sequence
- Random click position within a configured radius
- Notification stop
- Settings persistence
- Preset save/load
- AccessibilityService-based gesture injection and touch blocking

Android may restrict exact alarms/background behavior depending on device and OS version.
