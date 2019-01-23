# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).


## [Unreleased]
### Changed
- Bump library versions.
- Improve documentation on custom conformers.

## [0.3.0] — 2018-09-30
### Added
- Full support for arbitrary conforming specs (see README).
- `::cyrus-config.coerce/nonblank-string` conforming spec.
### Changed
- **BREAKING CHANGE** Conversion from EDN now requires using `from-edn` wrapper (see README).
### Fixed
- Values ending with "/" now don't throw errors when parsing.

## [0.2.2] — 2018-09-03
### Added
- _CHANGELOG.md_ created.
### Changed
- Uppercase env var names in README.

## 0.2.1 — 2018-01-12
Released without _CHANGELOG.md_


[0.2.2]: https://github.com/dryewo/cyrus-config/compare/0.2.1...0.2.2
[0.3.0]: https://github.com/dryewo/cyrus-config/compare/0.2.2...0.3.0
[Unreleased]: https://github.com/dryewo/cyrus-config/compare/0.3.0...HEAD
