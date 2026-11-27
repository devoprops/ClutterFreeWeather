# Play Console data safety

Update this file in the same change that stores user data or sends it off the device. The form itself is filed in Play Console before release. Advertising ID stays No.

Answers below match the app as of the forecast data layer. Nothing here is collected for ads or analytics.

## Security questions

- Data is encrypted in transit. National Weather Service, Iowa Environmental Mesonet, and Cloudflare R2 calls use HTTPS.
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

- The device IP as something the app reads. The weather servers and Cloudflare see that IP on the connection, and the radar tiles show the area being viewed; confirm while filling the form whether Play wants either listed as approximate location shared with a third party. The device's own approximate location is a separate section below. The extended-forecast request sends no city or coordinates.
- Device or advertising ID. The `User-Agent` sent to both servers is the app name and a contact email, not a per-user id.
- Name, email, phone, photos, contacts, messages, files, audio, calendar, health, financial info, web history, or app activity
- Crash logs, diagnostics, or analytics
- Precise location permission. `ACCESS_FINE_LOCATION` is not declared. The saved city point is precise because it comes from the bundled place list.

## Approximate device location

Collected only after the user agrees. Read again on a later visit while that permission stays granted, and again when the forecast refreshes. A refresh happens on open when the saved forecast is older than 15 minutes, again when the app returns to the foreground and the forecast is older than 15 minutes, every 30 minutes while the app stays open, every 10 minutes while a severe or extreme alert is active or the latest station report is a special observation for a thunderstorm, tornado, hail, or squall, and when the user refreshes. There is no background poll. Used on the device to pick the nearest bundled city, to show that place in the city list, to mark when the current city is no longer the closest, and to show how far the observation station is from the device. Not written to disk. The device point itself is not sent off the device. Choosing the city then saves and shares that city's coordinates the same way as a typed city. `ACCESS_COARSE_LOCATION` is declared. `ACCESS_FINE_LOCATION` is not. If the user denies the permission, typing a city still works.

Purpose: app functionality. Ephemeral.

This is a Play location declaration for the user to file. Approximate location, collected, not shared as the raw device point.

## Extended forecast

One national file from Cloudflare R2, downloaded when the app opens and the saved copy is outside the file's cache window. The request sends no city, coordinates, or place name. The file stays in app-private storage. A failed download keeps the previous file. There is no background poll.

Purpose: app functionality.

## Home screen widget

While a widget is on the home screen, the saved city's forecast is requested about every 30 minutes, once when the phone finishes booting, and again when the app comes to the foreground and the saved forecast is due. Choosing a city redraws the widget from the forecast just saved. While no city is saved, the widget looks for one about every 30 seconds, up to 10 times, and stops when a city is saved or those tries run out. The job uses the stored place and its forecast links. It does not read the device location. National Weather Service requests on that schedule are the same ones the open app already makes: the forecast and observation links, and the alert request, which includes the saved city's latitude and longitude. A failed refresh keeps the previous forecast on the widget. Removing the last widget stops the schedule. The extended national file is not part of this job.

Purpose: app functionality.

## Add a section when these are built

- Alert notifications, if delivery is tied to the saved place.
- Any analytics, crash reporting, or account.
