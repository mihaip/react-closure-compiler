# Closure Compiler support for React

Tools for making [React](http://facebook.github.io/react/) work better with the [Closure Compiler](https://developers.google.com/closure/compiler/). Goes beyond [an externs file](https://github.com/steida/react-externs) and adds a custom warnings guard and compiler pass to teach the compiler about components and other React-specific concepts.

## Building

To build the project, use:

    ant jar

That generates `lib/react.jar`, which you can then integrate into your build process (by adding `info.persistent.react.jscomp.ReactWarningsGuard` as a warnings guard and `info.persistent.react.jscomp.ReactCompilerPass` as a custom pass to run before checks).

To run the tests, use:

    ant test

## Demo

The demo shows how to use the warnings guard and compiler pass with [Plovr](http://plovr.com/), but they could be used with other toolchains as well. Plovr is assumed to be checked out in a sibling `plovr` directory. To run the server for the demo, use:

    ant run-demo

And then open the `demo/index.html` file in your browser (`file:///` URLs are fine). You will see some warnings, showing that type checking is working as expected with React components.
