# Task 1: Fix RewindCoverageAnalyzer Silent Empty Report

## Issue
When `ObjectClasspathScan.findSourceRoot()` returns `null` or no objects are found, `buildReport()` silently returned an empty report, allowing CI coverage guards to pass vacuously.

## Fix Applied
Modified `RewindCoverageAnalyzer.java`:
1. Replaced silent `new RewindCoverageReport(List.of())` with `IllegalStateException` when source root can't be resolved
2. Added second check after enumeration to throw `IllegalStateException` if coverage list is empty (misconfiguration)
3. Both error messages clearly name the source root path and explain the requirement

## Test Results
```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Test command: `mvn -Dmse=off "-Dtest=com.openggf.game.rewind.coverage.TestRewindCoverageAnalyzer" test`

## Commit Hash
`f734b7fb7`

## Files Changed
- `src/main/java/com/openggf/game/rewind/coverage/RewindCoverageAnalyzer.java`
