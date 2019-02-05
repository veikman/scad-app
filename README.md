# `scad-app`: SCAD application interface

`scad-app` is a small library for parallel rendering of model assets created
with [`scad-clj`](https://github.com/farrellm/scad-clj).

At its most basic, this library offers a simple, standardized way to get
SCAD files and render them to STL with OpenSCAD, from Clojure.
Parallelization is done with `core.async`, thus automatically using a
reasonable amount of threads.

## Usage

Step by step:

1. Develop a model in your Clojure code, e.g. `(cube 1 2 3)`.
2. Stick the model in a nullary function, called a producer.
3. Stick the function in a map, along with a name that will be used for file
   outputs: a `basename`.
4. Stick the map in an iterable, like this:
   `[{:basename "example-asset", :producer (fn [] (cube 1 2 3))}]`.
5. Pass that iterable to `scad-app.core/build-all`.

By default, this will produce a file called `output/scad/example-asset.scad`,
containing only the OpenSCAD code for the example cuboid.
Along the way, you will get simple progress reports to `*out*`, e.g. your
terminal.

Things like how the file paths are built, whether and how to render to STL etc.
are all configurable by passing options to `build-all`.

## License

Copyright © 2019 Viktor Eikman.

This software is distributed under the [Eclipse Public License](LICENSE-EPL),
(EPL) v2.0 or any later version thereof. This software may also be made
available under the [GNU General Public License](LICENSE-GPL) (GPL), v3.0 or
any later version thereof, as a secondary license hereby granted under the
terms of the EPL.
