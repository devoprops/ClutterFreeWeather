# Play Console data safety

Update this file in the same change that stores user data or sends it off the device. The form itself is filed in Play Console before release. Advertising ID stays No.

Answers below match the app as of the forecast data layer. Nothing here is collected for ads or analytics.

## Security questions

- Data is encrypted in transit. National Weather Service and Iowa Environmental Mesonet calls use HTTPS.
- Data is not encrypted by the app at rest. The place and forecast snapshot sit in app-private files.
- There is no account and no server copy. Uninstalling, or clearing the app's storage, deletes the places and snapshot. Each saved city except the current one can be deleted in the city panel.

## Precise location

Collected and shared. Required for a forecast. Not ephemeral, because saved cities stay on the device until the user deletes them or clears app storage.

Stored in `place.json`, for the current city and for every saved city:

- Place name chosen from the bundled Census place list
- Latitude and longitude
- National Weather Service grid office and the forecast links for that point

Shared with:

- National Weather Service (`api.weather.gov`), latitude and longitude on the place lookup, forecast, observation, and alert requests. Only the city being added or viewed is sent. The saved list itself never leaves the device.
- Iowa Environmental Mesonet (`mesonet.agron.iastate.edu`), radar map tiles. Each tile request names a zoom level and tile column and row, so it reveals the map area on screen. The radar opens centered on the current city, so the first requests show roughly where that city is, to within a tile (a few to tens of miles depending on zoom). No coordinates or place names are sent. The latest-image time request carries no location.

City suggestions are matched on the device from the bundled place list. The typed text is not sent off the device.

Purpose: app functionality.

The typed search text is not written to disk after the place is saved. The forecast snapshot does not contain the coordinates.

Radar tiles are held in memory only while the app runs and are not written to disk. The radar's map position and zoom are kept in saved UI state so they survive rotation; that state is not a file the app writes and it ends with the task.

## Not collected

- Approximate location from this app. The saved point is precise. The app does not read the device IP. Both weather servers see that IP on the connection, and the radar tiles show the area being viewed; confirm while filling the form whether Play wants either listed as approximate location shared with a third party.
- Device or advertising ID. The `User-Agent` sent to both servers is the app name and GitHub URL, not a per-user id.
- Name, email, phone, photos, contacts, messages, files, audio, calendar, health, financial info, web history, or app activity
- Crash logs, diagnostics, or analytics
- Location permission. None is declared. A later coarse or precise permission is a new entry here and a separate Play location declaration.

## Add a section when these are built

- Device location, if a permission is added.
- Alert notifications, if delivery is tied to the saved place.
- Any analytics, crash reporting, or account.
