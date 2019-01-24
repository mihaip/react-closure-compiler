package info.persistent.react.jscomp;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.WarningsGuard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * React-related warnings suppressions and rewriting of props validation
 * warnings to be more legible.
 */
public class ReactWarningsGuard extends WarningsGuard {
  private final Compiler compiler;
  private final ReactCompilerPass compilerPass;

  public ReactWarningsGuard() {
    this(null, null);
  }

  public ReactWarningsGuard(Compiler compiler, ReactCompilerPass compilerPass) {
    this.compiler = compiler;
    this.compilerPass = compilerPass;
  }

  private static final Pattern PARAMETER_TYPE_MISMATCH = Pattern.compile(
    "actual parameter 1 of (.+) does not match formal parameter.*");

  @Override public CheckLevel level(JSError error) {
    if (error.sourceName == null) {
      return null;
    }

    // Rewrite propTypes warnings to be more legible.
    if (compiler != null && compilerPass != null &&
        error.getType().key.equals("JSC_TYPE_MISMATCH") &&
        handlePropTypesWarning(error)) {
      return CheckLevel.OFF;
    }

    return null;
  }

  private boolean handlePropTypesWarning(JSError error) {
      Matcher matcher = PARAMETER_TYPE_MISMATCH.matcher(error.description);
      if (!matcher.find()) {
        return false;
      }
      String functionName = matcher.group(1);
      String typeName = PropTypesExtractor.getTypeNameForFunctionName(
          functionName);
      if (typeName == null) {
        return false;
      }
      PropTypesExtractor propTypesExtractor =
          compilerPass.getPropTypesExtractor(typeName);
      if (propTypesExtractor == null) {
        return false;
      }

      JSError propTypesError =
          propTypesExtractor.generatePropTypesError(error.node);
      if (propTypesError == null) {
        return false;
      }

      compiler.report(propTypesError);
      return true;
  }

  @Override protected int getPriority() {
    return Priority.MAX.getValue();
  }
}
