#!/usr/bin/env python3
"""Builds app/src/main/assets/cities.tsv, the bundled city list, from GeoNames cities15000.

    python3 tools/make-cities.py path/to/cities15000.txt [out.tsv]

Get the input from https://download.geonames.org/export/dump/cities15000.zip (CC BY 4.0, credit
GeoNames; the About screen carries the credit). Dev tool only: nothing here ships in the app.

Output: one tab-separated line per city, biggest first (the order is the search ranking):

    geonameid  name  asciiname  country  admin1  latitude  longitude  timezone  population  aliases

admin1 is kept only for the United States (two-letter state codes, to tell Springfield, MO from
Springfield, IL). Elsewhere GeoNames uses numbers, or codes that mean little to a reader. aliases are other names people search by,
separated by semicolons. The first line is a comment naming the source; the app skips it as it
skips any line that is not a city.

Plain text, not gzip: the Android build decompresses and renames .gz assets, and the APK is compressed
anyway (about 1 MB for this file).
"""
import io
import sys
import unicodedata

SINGAPORE_ID = "1880252"

# Names people search by that GeoNames files under another spelling. Keyed by (GeoNames name, country),
# so a change in the data fails loudly here instead of silently dropping an alias in the app.
ALIASES = {
    ("Makkah", "SA"): ["Mecca", "Makka", "Mekka", "Makkah al Mukarramah"],
    ("Madinah", "SA"): ["Medina", "Madina", "Al Madinah", "Al Madinah al Munawwarah"],
    ("Jeddah", "SA"): ["Jiddah", "Jidda"],
    ("Köln", "DE"): ["Cologne"],
    ("Mumbai", "IN"): ["Bombay"],
    ("Kolkata", "IN"): ["Calcutta"],
    ("Bengaluru", "IN"): ["Bangalore"],
    ("Chattogram", "BD"): ["Chittagong"],
    ("Yangon", "MM"): ["Rangoon"],
    ("Beijing", "CN"): ["Peking"],
    ("Ho Chi Minh City", "VN"): ["Saigon"],
    ("Kyiv", "UA"): ["Kiev"],
    ("George Town", "MY"): ["Penang", "Pulau Pinang"],
    ("Malacca", "MY"): ["Melaka"],
    ("Fes", "MA"): ["Fez"],
}

REQUIRED = [("Singapore", "SG"), ("Makkah", "SA"), ("Madinah", "SA"), ("London", "GB"), ("New York City", "US"), ("Karachi", "PK"),
            ("Jakarta", "ID"), ("Kuala Lumpur", "MY"), ("Istanbul", "TR"), ("Dubai", "AE"), ("Cairo", "EG"), ("Sydney", "AU")]


def keep_region(country, code):
    return code if country == "US" and len(code) == 2 and code.isalpha() else ""


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    source = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/cities.tsv"

    cities = []
    with open(source, encoding="utf-8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            if len(c) < 19 or not c[17]:
                continue
            country = c[8]
            # Singapore has dozens of neighbourhood entries; one Singapore is what a user means.
            if country == "SG" and c[0] != SINGAPORE_ID:
                continue
            cities.append({
                "id": c[0], "name": c[1], "ascii": c[2], "country": country, "region": keep_region(country, c[10]),
                "lat": float(c[4]), "lon": float(c[5]), "zone": c[17], "pop": int(c[14] or 0),
            })

    cities.sort(key=lambda c: (-c["pop"], int(c["id"])))

    by_key = {(c["name"], c["country"]): c for c in cities}
    for key, names in ALIASES.items():
        if key not in by_key:
            sys.exit(f"alias target {key} is not in the data: update ALIASES in {sys.argv[0]}")
        by_key[key]["aliases"] = names
    for key in REQUIRED:
        if key not in by_key:
            sys.exit(f"required city {key} is missing from the data")

    buf = io.StringIO()
    buf.write(f"# GeoNames cities15000 (CC BY 4.0, https://www.geonames.org), built by tools/make-cities.py; {len(cities)} cities\n")
    for c in cities:
        buf.write("\t".join([
            c["id"], c["name"], c["ascii"], c["country"], c["region"],
            f"{c['lat']:.4f}", f"{c['lon']:.4f}", c["zone"], str(c["pop"]), ";".join(c.get("aliases", [])),
        ]) + "\n")
    data = buf.getvalue().encode("utf-8")
    with open(out, "wb") as f:
        f.write(data)
    print(f"{len(cities)} cities, {len(data) / 1e6:.2f} MB, {out}")
    print(f"{len(ALIASES)} aliases applied; countries: {len({c['country'] for c in cities})}; zones: {len({c['zone'] for c in cities})}")


if __name__ == "__main__":
    main()
