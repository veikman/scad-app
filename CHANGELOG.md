# Change log
This log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]
Nothing yet.

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
