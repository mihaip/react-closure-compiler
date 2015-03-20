package info.persistent.react.jscomp;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.WarningsGuard;

/**
 * React-related warnings suppressions.
 */
public class ReactWarningsGuard extends WarningsGuard {
  @Override public CheckLevel level(JSError error) {
    if (error.sourceName == null) {
      return null;
    }

    // Ignore all warnings and errors in the React library itself -- it
    // generally compiles as intended, but it uses non-standard JSDoc which
    // throws off the compiler.
    if (React.isReactSourceName(error.sourceName)) {
      return CheckLevel.OFF;
    }

    return null;
  }

  @Override protected int getPriority() {
    return Priority.MAX.getValue();
  }
}
