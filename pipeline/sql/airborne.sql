SELECT time, icao24, callsign, lat, lon, baroaltitude, heading, velocity
FROM state_vectors_data4
WHERE hour = 1792083600              -- the 10 am hour on Oct 15 (required by OpenSky)
  AND lat BETWEEN 46.6 AND 48.3      -- SEA area box
  AND lon BETWEEN -123.6 AND -121.0
  AND onground = false               -- airborne only
  AND baroaltitude IS NOT NULL       -- has an altitude
  AND heading IS NOT NULL            -- has a heading
  AND baroaltitude < 6096            -- below 20,000 ft (6,096 m)
  AND time % 10 = 0                  -- one point every 10 seconds
ORDER BY icao24, time;                -- group each plane's points together, in time order