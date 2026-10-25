#!/usr/bin/env python3
"""Builds the Hijri assets from the CSVs in data/:

    python3 tools/make-hijri.py [data/hijri_months.csv] [out.json]

  data/hijri_months.csv  -> app/src/main/assets/hijri_months.json   (the month starts, below)
  data/islamic_events.csv -> app/src/main/assets/islamic_events.json (the yearly Islamic events, see the end)

The CSV is the source of truth: the first day of each Hijri month in Singapore, copied by hand from the Muis
Calendar published by MUIS (https://www.muis.gov.sg/resources/islamic-calendar/). Nothing here, or anywhere in the
app, calculates or estimates a Hijri date. This script only checks the table and reformats it; it refuses to write
the asset if the table is inconsistent, because a wrong date on a lock screen is worse than none.

Columns: hijri_year,month_number,month_name,gregorian_start,weekday
  gregorian_start is YYYY-MM-DD, the civil date of the 1st of that Hijri month in Singapore.
  weekday is the weekday the MUIS calendar prints; it is used only to catch typing mistakes here.
The app reads only the generated JSON. See docs/DEVELOPING.md ("The Hijri month table") for the yearly update steps.
"""
import csv
import datetime
import json
import sys

NAMES = ["Muharram", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal", "Jamadilakhir",
         "Rejab", "Syaaban", "Ramadhan", "Syawal", "Zulkaedah", "Zulhijjah"]
HEADER = ["hijri_year", "month_number", "month_name", "gregorian_start", "weekday"]
SOURCE = "Muis Calendar published by MUIS (Key Islamic dates)"
EVENT_HEADER = ["hijri_month", "hijri_day", "event_name"]


def load(path):
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        header = next(reader, None)
        if header != HEADER:
            sys.exit(f"{path}: header must be {','.join(HEADER)}, found {header}")
        rows = []
        for number, fields in enumerate(reader, start=2):
            if not fields:
                continue
            if len(fields) != 5:
                sys.exit(f"{path}:{number}: expected 5 columns, found {len(fields)}")
            try:
                year, month = int(fields[0]), int(fields[1])
                start = datetime.date.fromisoformat(fields[3])
            except ValueError as e:
                sys.exit(f"{path}:{number}: {e}")
            rows.append({"line": number, "year": year, "month": month, "name": fields[2], "start": start, "weekday": fields[4]})
    return rows


def validate(rows):
    if not rows:
        sys.exit("the table is empty")
    problems = []
    for r in rows:
        where = f"line {r['line']} ({r['name']} {r['year']})"
        if not 1 <= r["month"] <= 12:
            problems.append(f"{where}: month_number {r['month']} is not 1 to 12")
        elif r["name"] != NAMES[r["month"] - 1]:
            problems.append(f"{where}: month {r['month']} is {NAMES[r['month'] - 1]}, not {r['name']}")
        actual = r["start"].strftime("%A")
        if actual != r["weekday"]:
            problems.append(f"{where}: {r['start']} is a {actual}, but the weekday column says {r['weekday']}")
    for a, b in zip(rows, rows[1:]):
        where = f"line {b['line']} ({b['name']} {b['year']})"
        gap = (b["start"] - a["start"]).days
        if gap not in (29, 30):
            problems.append(f"{where}: {gap} days after the previous month start, a Hijri month is 29 or 30")
        expected = (a["year"] + 1, 1) if a["month"] == 12 else (a["year"], a["month"] + 1)
        if (b["year"], b["month"]) != expected:
            problems.append(f"{where}: follows {a['month']}/{a['year']}, so should be {expected[1]}/{expected[0]}")
    if problems:
        sys.exit("hijri_months.csv has problems:\n  " + "\n  ".join(problems))


def load_events(path):
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        header = next(reader, None)
        if header != EVENT_HEADER:
            sys.exit(f"{path}: header must be {','.join(EVENT_HEADER)}, found {header}")
        events = []
        for number, fields in enumerate(reader, start=2):
            if not fields:
                continue
            if len(fields) != 3:
                sys.exit(f"{path}:{number}: expected 3 columns, found {len(fields)}")
            try:
                month, day = int(fields[0]), int(fields[1])
            except ValueError as e:
                sys.exit(f"{path}:{number}: {e}")
            events.append({"line": number, "month": month, "day": day, "name": fields[2].strip()})
    return events


def validate_events(events):
    """An event is a fixed day of a Hijri month that recurs every year, so the table is (month, day, name)."""
    problems, seen = [], {}
    for e in events:
        where = f"line {e['line']} ({e['name']})"
        if not 1 <= e["month"] <= 12:
            problems.append(f"{where}: hijri_month {e['month']} is not 1 to 12")
        if not 1 <= e["day"] <= 30:
            problems.append(f"{where}: hijri_day {e['day']} is not 1 to 30")
        if not e["name"]:
            problems.append(f"{where}: empty name")
        if "  " in e["name"] or e["name"] != e["name"].strip():
            problems.append(f"{where}: stray spaces in the name")
        key = (e["month"], e["day"])
        if key in seen:
            problems.append(f"{where}: {e['day']} {NAMES[e['month'] - 1]} is already {seen[key]}")
        seen[key] = e["name"]
    for a, b in zip(events, events[1:]):
        if (b["month"], b["day"]) <= (a["month"], a["day"]):
            problems.append(f"line {b['line']} ({b['name']}): not in month and day order")
    if problems:
        sys.exit("islamic_events.csv has problems:\n  " + "\n  ".join(problems))


def main():
    csv_path = sys.argv[1] if len(sys.argv) > 1 else "data/hijri_months.csv"
    out = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/hijri_months.json"
    rows = load(csv_path)
    validate(rows)
    asset = {
        "source": SOURCE,
        "months": [{"year": r["year"], "month": r["month"], "name": r["name"], "start": r["start"].isoformat()} for r in rows],
    }
    with open(out, "w", encoding="utf-8") as f:
        json.dump(asset, f, indent=1, ensure_ascii=False)
        f.write("\n")
    last = rows[-1]["start"] + datetime.timedelta(days=28)
    print(f"{len(rows)} month starts, {rows[0]['start']} to {rows[-1]['start']}; certain through {last}; wrote {out}")

    events_csv = csv_path.replace("hijri_months.csv", "islamic_events.csv")
    events_out = out.replace("hijri_months.json", "islamic_events.json")
    events = load_events(events_csv)
    validate_events(events)
    with open(events_out, "w", encoding="utf-8") as f:
        json.dump({"source": SOURCE, "events": [{"month": e["month"], "day": e["day"], "name": e["name"]} for e in events]},
                  f, indent=1, ensure_ascii=False)
        f.write("\n")
    print(f"{len(events)} Islamic events; wrote {events_out}")


if __name__ == "__main__":
    main()


# Islamic events: the MUIS "Key Islamic dates" PDFs list each event with its Hijri date and Gregorian date, for
# example "Hari Raya Aidilfitri 1 Syawal 1447H 21 March 2026 (Saturday)". The Hijri day and month are the same every
# year, so the app keeps (month, day, name) and shows the name on any day whose Hijri date matches. Where the Gregorian
# date comes out is then decided by the month start table, and tools/check-hijri-pdf.py checks the two agree with the PDFs.
