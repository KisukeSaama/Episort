# Scan table sort and filter UX

## Summary

- Added JavaFX scan table sorting through `FilteredList -> SortedList -> TableView`.
- Added natural comparators for original/proposed filenames, pattern, extension, and order.
- Added business-order comparators for media type and row status, plus numeric confidence sorting.
- Added scan filter chips above the table: All, Movies, Series, Unknown.
- Unknown filter includes unknown media type and understanding-problem statuses: TYPE, AI, PATTERN, META.
- Added English and French i18n entries for the scan filter chips.
- Added focused unit tests for natural sorting, media type grouping, status order, confidence order, and filter behavior.

## Verification

- `.\gradlew.bat test`
