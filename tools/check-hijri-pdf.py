#!/usr/bin/env python3
"""Compares text extracted from MUIS "Key Islamic dates" PDFs with data/hijri_months.csv.

    swift tools/pdftext.swift "Key Islamic dates 2028.pdf" > /tmp/2028.txt
    python3 tools/check-hijri-pdf.py /tmp/2027.txt /tmp/2028.txt

It reads two kinds of line from the PDF text:
  month starts   "1 Syaaban 1447H January 2026 20 January 2026 (Tuesday)"
  Islamic events "Hari Raya Aidilfitri 1 Syawal 1447H 21 March 2026 (Saturday)"
and checks each against the CSVs. Month starts must be in data/hijri_months.csv with the same date and weekday (errors), and
are flagged if the Hijri year or the month spelling differs (warnings: MUIS's PDFs have typos, and spell
Ramadhan and Ramadan both ways). Events are checked independently: the Hijri day counted from the CSV's month
start must land on the date the PDF prints, which catches a month start typed a day out. Exit status 1 on any error.
Nothing here calculates a date for the app; it only tells you whether what you typed agrees with the PDF.
"""
import csv
import datetime
import re
import sys

NAMES = ["Muharram", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal", "Jamadilakhir",
         "Rejab", "Syaaban", "Ramadhan", "Syawal", "Zulkaedah", "Zulhijjah"]
ALIASES = {"Ramadan": "Ramadhan"}
MONTHS = {m: i for i, m in enumerate(["January", "February", "March", "April", "May", "June", "July", "August",
                                      "September", "October", "November", "December"], 1)}

START = re.compile(r"^1 (\w+) (\d{4})H \w+ \d{4} (\d{1,2}) ?(\w+) (\d{4}) \((\w+)\)$")
EVENT = re.compile(r"(\d{1,2}) (\w+) (\d{4})H (\d{1,2}) ?(\w+) (\d{4}) \((\w+)\)$")


def month_number(name):
    name = ALIASES.get(name, name)
    return NAMES.index(name) + 1 if name in NAMES else None


def load_events(path="data/islamic_events.csv"):
    with open(path, newline="", encoding="utf-8") as f:
        return {(int(r["hijri_month"]), int(r["hijri_day"])): r["event_name"] for r in csv.DictReader(f)}


def clean_name(name):
    """The event name as the CSV spells it: no "(1448H)" tag, one space between words, a typographic apostrophe."""
    return re.sub(r"\s+", " ", re.sub(r"\(\d{4}H\)", "", name)).strip().replace("'", "\u2019")


def load_csv(path="data/hijri_months.csv"):
    with open(path, newline="", encoding="utf-8") as f:
        return [{"year": int(r["hijri_year"]), "month": int(r["month_number"]), "name": r["month_name"],
                 "start": datetime.date.fromisoformat(r["gregorian_start"]), "weekday": r["weekday"]}
                for r in csv.DictReader(f)]


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    rows = load_csv()
    event_names = load_events()
    seen_events = set()
    by_date = {r["start"]: r for r in rows}
    by_month = {(r["year"], r["month"]): r for r in rows}
    errors, warnings, checked_starts, checked_events, outside = [], [], 0, 0, []
    for path in sys.argv[1:]:
        previous = ""
        for raw in open(path, encoding="utf-8"):
            line = raw.strip()
            before, previous = previous, line
            m = START.match(line)
            if m:
                name, year, day, mon, y2, weekday = m.groups()
                date = datetime.date(int(y2), MONTHS[mon], int(day))
                checked_starts += 1
                row = by_date.get(date)
                if row is None:
                    errors.append(f"{path}: month start {date} ({line}) is not in the CSV")
                    continue
                if row["weekday"] != weekday:
                    errors.append(f"{date}: PDF says {weekday}, CSV says {row['weekday']}")
                if month_number(name) != row["month"]:
                    errors.append(f"{date}: PDF month {name}, CSV month {row['name']}")
                if int(year) != row["year"]:
                    warnings.append(f"{date}: PDF prints {year}H for {name}, CSV has {row['year']}H (a typo in the PDF? check the events table)")
                if ALIASES.get(name, name) != row["name"]:
                    warnings.append(f"{date}: PDF spells {name}, CSV {row['name']}")
                continue
            e = EVENT.search(line)
            if e and not START.match(line):
                day, name, year, d2, mon, y2, weekday = e.groups()
                month = month_number(name)
                if month is None:
                    continue
                # The event's name is what comes before its Hijri date, and a long name wraps onto the line before.
                title = line[:e.start()].strip()
                if before and not any(ch.isdigit() for ch in before) and not before.startswith("====="):
                    title = before + " " + title
                title = clean_name(title)
                key = (month, int(day))
                seen_events.add(key)
                if key not in event_names:
                    errors.append(f"event {title} ({day} {name}) is not in data/islamic_events.csv")
                elif event_names[key] != title:
                    errors.append(f"event {day} {name}: PDF calls it {title!r}, data/islamic_events.csv {event_names[key]!r}")
                date = datetime.date(int(y2), MONTHS[mon], int(d2))
                row = by_month.get((int(year), month))
                if row is None:
                    outside.append(f"{day} {name} {year}H = {date}")
                    continue
                checked_events += 1
                expected = row["start"] + datetime.timedelta(days=int(day) - 1)
                if expected != date:
                    errors.append(f"event {day} {name} {year}H: PDF says {date}, CSV month start gives {expected}")
                if date.strftime("%A") != weekday:
                    errors.append(f"event {day} {name} {year}H: {date} is a {date.strftime('%A')}, PDF says {weekday}")
    print(f"month starts checked: {checked_starts}; events checked against the CSV: {checked_events}; events outside the table: {len(outside)}")
    print(f"distinct events seen in the PDFs: {len(seen_events)} of {len(event_names)} in data/islamic_events.csv")
    for key, name in sorted(event_names.items()):
        if key not in seen_events:
            warnings.append(f"event {name} ({key[1]} {NAMES[key[0] - 1]}) is in the CSV but not in these PDFs")
    for o in outside:
        print("  outside the table (not an error):", o)
    for w in warnings:
        print("WARNING:", w)
    for er in errors:
        print("ERROR:", er)
    if errors:
        sys.exit(1)
    print("OK: every month start, event and event name in the PDF text agrees with data/hijri_months.csv and data/islamic_events.csv" + (" (see warnings)" if warnings else ""))


if __name__ == "__main__":
    main()
