#!/bin/bash

scriptdir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
buildfile="$scriptdir/../build.xml"

ant -f "$buildfile" compile
if [ $? -ne 0 ]
then
  echo "Could not recompile"
  exit 1
fi

classesdir="$scriptdir/../build/classes"
compilerjar=""
for jar in $scriptdir/../lib/closure-compiler-*.jar; do
  compilerjar=$jar
done

exec java -classpath "$classesdir:$compilerjar" info.persistent.jscomp.AstDump "$@"
