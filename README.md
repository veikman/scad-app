# `scad-app`: SCAD application interface

`scad-app` is a small library for parallel rendering of model assets created
with Matthew Farrell’s [`scad-clj`](https://github.com/farrellm/scad-clj).

At its most basic, this library offers a simple, standardized way to get SCAD
files and render them to STL with OpenSCAD, from Clojure. Concurrency is done
with `core.async`, automatically using a reasonable amount of threads for your
hardware.

`scad-app` also supports [chirality](https://en.wikipedia.org/wiki/Chirality)
in concert with a reasonable way to define reusable OpenSCAD modules instead of
repeating full specs throughout generated code.

## Usage

First, package your model as a `scad-app` asset. An asset is a Clojure map
with a simple schema. At minimum, an asset must have a `:name` and some
source of specifications for the model. There are three options:

1. `:model-main` is a single `scad-clj` spec like `(circle 1)`.
2. `:model-vector` is a vector of such `scad-clj` specs.
3. `:model-fn` is a nullary function that returns either of the above.

These three are all interchangeable, but you must supply at least one with each
asset. An example of a complete asset looks like this:
`def cube-asset {:name "cute_cube", :model-main (cube 1 2 3)}`.

Having packaged your asset this way, you can write it to a file by calling
`(scad-app.core/build-all [cube-asset])`.

By default, this will produce a file called `output/scad/cute_cube.scad`,
containing only the OpenSCAD code for the example cuboid.
Along the way, you will get simple progress reports to `*out*`, e.g. your
terminal.

### Advanced features

Things like how the file paths are built, whether and how to render to STL etc.
are all configurable by passing options to `build-all`. To duplicate a chiral
asset in a mirrored version, and/or inject modules into its model vector, call
`refine-asset` first.

## Demo

This repository comes with a demo of how concurrency is handled. This demo
doesn’t build any files but it makes a nice sandbox if you wish to test an
improvement to the library.

Run it like this: `lein exec -p src/demo/core.clj`

## License

Copyright © 2019 Viktor Eikman.

This software is distributed under the [Eclipse Public License](LICENSE-EPL),
(EPL) v2.0 or any later version thereof. This software may also be made
available under the [GNU General Public License](LICENSE-GPL) (GPL), v3.0 or
any later version thereof, as a secondary license hereby granted under the
terms of the EPL.
