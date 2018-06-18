#!/bin/bash

scriptdir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

buildfile="$scriptdir"/../build.xml
builddir="$scriptdir"/../build

if [ "$(ant -f "$buildfile" jar)" -ne 0 ]; then
  echo "Could not recompile"
  exit 1
fi

libdir="$scriptdir"/../lib

compilerjar=""
for jar in "$libdir"/closure-compiler-*.jar; do
  compilerjar=$jar
done

guavajar=""
for jar in "$libdir"/guava-*.jar; do
  guavajar=$jar
done

exec java -classpath "$compilerjar:$guavajar:$builddir/react-closure-compiler.jar" info.persistent.react.jscomp.ReactCommandLineRunner "$@"
