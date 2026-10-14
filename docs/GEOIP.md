# Geo analytics (country and city)

Linky resolves a visitor's country and city from their IP address using any MaxMind-format
`.mmdb` city database. IPs are looked up in memory and **never stored**; only a salted hash is kept.
Without a database everything else works and locations show as "Unknown".

## Option A: MaxMind GeoLite2-City (what this project uses)

1. Create a free MaxMind account, then download **GeoLite2-City** (GeoIP2 Binary `.mmdb`, GZIP)
   from *Account > GeoIP / GeoLite > Download files*.
2. Extract `GeoLite2-City.mmdb` into `linky-backend-springboot/geoip/` (git-ignored).
3. Run with `GEOIP_DB_PATH=geoip/GeoLite2-City.mmdb`.

## Option B: DB-IP City Lite (no account)

`scripts/download-geoip.sh` fetches the free DB-IP City Lite database (CC BY 4.0, same format) into
`geoip/geo-city.mmdb`. It tends to return more cities for anycast and hosting addresses.

## Production notes

- **Do not commit or redistribute the database.** MaxMind's GeoLite EULA restricts redistribution, so do
  not bake it into a public container image. Mount it as a volume or fetch it at deploy time with
  MaxMind's `geoipupdate` and your own license key. The file is refreshed monthly.
- Behind a proxy, set `TRUSTED_PROXY_HOPS` correctly, otherwise every visitor resolves to the proxy's location.
- Attribution is required and shown on the analytics page: "This product includes GeoLite2 data created
  by MaxMind" (and DB-IP.com if you use option B).
- Locations are approximate; do not use them to identify an individual or address.

## Testing locally

With `TRUSTED_PROXY_HOPS=1`, simulate visitors from other countries:

```bash
curl -s -o /dev/null -H "X-Forwarded-For: 8.8.8.8" http://localhost:8080/<code>
```
