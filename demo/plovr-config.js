{
  "id": "demo",
  "paths": ".",
  "inputs": [
    "react-0.13.1-build/react.js",
    "index.js"
  ],
  "mode": "ADVANCED",
  "pretty-print": true,
  "level": "VERBOSE",
  "experimental-exclude-closure-library": false,
  "custom-warnings-guards": [
    "info.persistent.react.jscomp.ReactWarningsGuard"
  ],
  "custom-passes": [
    {
      "class-name": "info.persistent.react.jscomp.ReactCompilerPass",
      "when": "BEFORE_CHECKS"
    }
  ]
}
