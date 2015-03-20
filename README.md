# Closure Compiler support for React

Tools for making [React](http://facebook.github.io/react/) work better with the [Closure Compiler](https://developers.google.com/closure/compiler/). Goes beyond [an externs file](https://github.com/steida/react-externs) and adds a custom warnings guard and compiler pass to teach the compiler about components and other React-specific concepts.

The demo shows how to use the warnings guard and compiler pass with [Plovr](http://plovr.com/), but they could be used with other toolchains as well. Plovr is assumed to be checked out in a sibling `plovr` directory. To run the server for the demo, use:

    java -classpath "build/react.jar:../plovr/build/plovr.jar" org.plovr.cli.Main serve demo/plovr-config.js
