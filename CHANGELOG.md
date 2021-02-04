# Change log
This log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]
### Changed
- Renamed `minimum-face-angle`, `minimum-face-size` and `face-count` to use the
  word “facet” instead of “face”, matching OpenSCAD documentation.
- Moved and thereby re-namespaced keywords to a new `scad-app.schema` module.
- Changed the keywords passed to `report-fn`. This breaks applications
  that pass a custom function relying on the old keywords; no such
  applications are known.
    - Namespaced keywords pertaining to log messages.
    - Replaced `started-stl` and `failed-stl` keywords with generic `-render`
      equivalents, and similarly, replaced `command-stl` with `command-render`.

### Added
- Rendering to two-dimensional images.
    - Added rendering commands to printed reports on failure.
- Termination on failure to render SCAD.
- Exposed (and expanded) the default filepath composition function.

## [Version 0.3.0] - 2019-09-07
### Added
- Support for file-level minimum fragmentation (face) angle and number
  (OpenSCAD’s special `$fa` and `$fn` variables) as a properties of assets,
  keyed as `minimum-face-angle` and `face-count`.

## [Version 0.2.0] - 2019-03-24
### Added
- Support for file-level minimum face size (OpenSCAD’s special `$fs` variable)
  as a property of assets, keyed as `minimum-face-size`.

### Developer
- Added file I/O in unit testing.

## Version 0.1.0 - 2019-02-20
### Added
- Basic functions for writing SCAD and STL with support for chirality and
  OpenSCAD modules.

[Unreleased]: https://github.com/veikman/scad-app/compare/v0.3.0...HEAD
[Version 0.3.0]: https://github.com/veikman/scad-app/compare/v0.2.0...v0.3.0
[Version 0.2.0]: https://github.com/veikman/scad-app/compare/v0.1.0...v0.2.0
