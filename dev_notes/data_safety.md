# Play Console data safety

Update this file in the same change that stores user data or sends it off the device. The form itself is filed in Play Console before release. Advertising ID stays No.

Answers below match the app as of the forecast data layer. Nothing here is collected for ads or analytics.

## Security questions

- Data is encrypted in transit. National Weather Service calls use HTTPS.
- Data is not encrypted by the app at rest. The place and forecast snapshot sit in app-private files.
- There is no account and no server copy. Uninstalling, or clearing the app's storage, deletes the place and snapshot. There is no in-app delete control yet.

## Precise location

Collected and shared. Required for a forecast. Not ephemeral, because the selected place is stored until the next search or until app storage is cleared.

Stored in `place.json`:

- Place name chosen from the bundled Census place list
- Latitude and longitude
- National Weather Service grid office and the forecast links for that point

Shared with:

- National Weather Service (`api.weather.gov`), latitude and longitude on the place lookup, forecast, observation, and alert requests

City suggestions are matched on the device from the bundled place list. The typed text is not sent off the device.

Purpose: app functionality.

The typed search text is not written to disk after the place is saved. The forecast snapshot does not contain the coordinates.

## Not collected

- Approximate location from this app. The saved point is precise. The app does not read the device IP. The weather server sees that IP on the connection; confirm while filling the form whether Play wants that listed as approximate location.
- Device or advertising ID. The National Weather Service `User-Agent` is the app name and GitHub URL, not a per-user id.
- Name, email, phone, photos, contacts, messages, files, audio, calendar, health, financial info, web history, or app activity
- Crash logs, diagnostics, or analytics
- Location permission. None is declared. A later coarse or precise permission is a new entry here and a separate Play location declaration.

## Add a section when these are built

- Radar tiles. A map request to the Iowa Environmental Mesonet reveals the view and the device IP.
- Device location, if a permission is added.
- Alert notifications, if delivery is tied to the saved place.
- Any analytics, crash reporting, or account.
