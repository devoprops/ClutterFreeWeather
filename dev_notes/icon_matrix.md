# Weather icon matrix

The forecast stores an icon URL like `https://api.weather.gov/icons/land/day/sct?size=medium`. The app will take the condition code (`sct`) and the day or night token, then load a local file. It will not download the National Weather Service picture at runtime. That icon service is deprecated, and it only returns PNG.

Those URLs are still useful as a preview while you draw. Open one in a browser, for example `https://api.weather.gov/icons/land/day/tsra?size=medium`. The pictures are US government work. Use them as a reference for meaning, not as the art in the app.

A URL can also look like `day/sct/rain` or `night/tsra,40`. Use the first condition (`sct`, `tsra`). Ignore a trailing number. That number is a rain chance the government paints into its own icon. Temperature and rain chance are drawn by the app, not baked into the artwork.

Morning and evening are not extra files. The app darkens the day or night icon within about an hour of sunrise or sunset.

## What to draw first

One day file and one night file for each of these. Night art should not include a sun. Rain, snow, and thunder can be the same cloud as the day file if the only difference is the moon, which is a separate overlay.

| Code | Meaning | Day file | Night file |
| --- | --- | --- | --- |
| skc | Clear | ic_wx_day_skc | ic_wx_night_skc |
| few | A few clouds | ic_wx_day_few | ic_wx_night_few |
| sct | Partly cloudy | ic_wx_day_sct | ic_wx_night_sct |
| bkn | Mostly cloudy | ic_wx_day_bkn | ic_wx_night_bkn |
| ovc | Overcast | ic_wx_day_ovc | ic_wx_night_ovc |
| rain | Rain | ic_wx_day_rain | ic_wx_night_rain |
| rain_showers | Showers | ic_wx_day_rain_showers | ic_wx_night_rain_showers |
| snow | Snow | ic_wx_day_snow | ic_wx_night_snow |
| sleet | Sleet | ic_wx_day_sleet | ic_wx_night_sleet |
| fzra | Freezing rain | ic_wx_day_fzra | ic_wx_night_fzra |
| tsra | Thunderstorm | ic_wx_day_tsra | ic_wx_night_tsra |
| fog | Fog or mist | ic_wx_day_fog | ic_wx_night_fog |

If a night file is missing, the app will use the day file and add the moon overlay.

## Draw later, or reuse a file above

| Code | Meaning | Suggestion |
| --- | --- | --- |
| rain_showers_hi | Showers, less cloud | Reuse rain_showers until you want a lighter version |
| tsra_sct, tsra_hi | Thunder with less cloud | Reuse tsra |
| rain_snow, rain_sleet, snow_sleet, rain_fzra, snow_fzra | Mixed precip | Own icon when you have time. Until then reuse the first word (rain or snow) |
| dust, smoke, haze | Reduced visibility | Own icon later. Until then reuse fog |
| hot, cold | Temperature extreme | Own icon later. Until then reuse skc |
| tornado, hurricane, tropical_storm, blizzard | Rare and severe | Worth their own icons, not required to start |

Windy conditions arrive as `wind_skc`, `wind_few`, `wind_sct`, `wind_bkn`, and `wind_ovc`. Do not draw ten extra pictures. Draw one wind mark, `ic_wx_wind`, and the app lays it on the matching sky icon. Blizzard can use the snow icon plus that same wind mark until you want a dedicated file.

## Moon overlay

The National Weather Service icon set does not include moon phases. Nothing in the forecast URL tells us the phase. The app computes it from the date.

Draw eight small moons, transparent around the disc, meant to sit in a corner of a night icon:

| Phase | File |
| --- | --- |
| New | ic_moon_new |
| Waxing crescent | ic_moon_waxing_crescent |
| First quarter | ic_moon_first_quarter |
| Waxing gibbous | ic_moon_waxing_gibbous |
| Full | ic_moon_full |
| Waning gibbous | ic_moon_waning_gibbous |
| Last quarter | ic_moon_last_quarter |
| Waning crescent | ic_moon_waning_crescent |

There is no matching set of outline icons to drop in. NASA publishes moon photographs, which are the wrong style for this UI. A weather-font or Material icon only covers one or two moons, not all eight phases. These eight files are new artwork.

The moon is not used on day icons.

## File format and size

Draw SVG. Square, 160 by 160 view box, transparent background. Keep the artwork inside a margin of about 12 pixels so it still looks right in a rounded box. Flat shapes only: no text, no blur, no drop shadow, and no gradient if you can avoid it. Android Studio will turn each SVG into a vector drawable, and those effects often fail the import.

Name the files exactly as in the tables, lowercase, with `.svg`.

Put finished files in `dev_notes/icons/`. They stay out of the app module until they are imported to `app/src/main/res/drawable/` under the same name. A missing file falls back as described above, then to `ic_wx_day_skc` if that code is unknown.

Do not export a separate PNG set, and do not cut morning, evening, or hourly sizes. One SVG is used at the large Today size and the small hour size.
