package info.persistent.react.jscomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CustomPassExecutionTime;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.StrictWarningsGuard;
import com.google.javascript.jscomp.WarningLevel;

import org.junit.Test;

import java.io.PrintStream;
import java.util.List;

/**
 * Test {@link ReactWarningsGuard}.
 */
public class ReactWarningsGuardTest {
  @Test public void testReactWarningsGuard() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.addWarningsGuard(new ReactWarningsGuard());
    // Should not warn about non-standard JSDoc.
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("/src/react.js", "/** @providesModule React */"));
    List<SourceFile> externs = ImmutableList.of(
        SourceFile.fromCode("externs", ""));
    Result result = compiler.compile(externs, inputs, options);
    assertTrue(result.success);
    assertEquals(result.errors.length, 0);
    assertEquals(result.warnings.length, 0);
  }

  @Test public void testPropsValidator() {
    Compiler compiler = new Compiler(
        new PrintStream(ByteStreams.nullOutputStream())); // Silence logging
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setWarningLevel(
        DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.ERROR);
    // Report warnings as errors to make tests simpler
    options.addWarningsGuard(new StrictWarningsGuard());
    ReactCompilerPass compilerPass = new ReactCompilerPass(compiler, true);
    options.addCustomPass(CustomPassExecutionTime.BEFORE_CHECKS, compilerPass);
    options.addWarningsGuard(new ReactWarningsGuard(compiler, compilerPass));
    String inputJs = "var Comp = React.createClass({" +
        "propTypes: {" +
          "strProp: React.PropTypes.string.isRequired" +
        "}," +
        "render: function() {return null;}" +
      "});\n" +
      "React.createElement(Comp, {strProp: 1});";
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("/src/react.js", "/** @providesModule React */"),
        SourceFile.fromCode("/src/test.js", inputJs));
    List<SourceFile> externs = ImmutableList.of(
        SourceFile.fromCode(
          "externs", "/** @constructor */ function Element() {};"));
    Result result = compiler.compile(externs, inputs, options);
    assertFalse(result.success);
    assertEquals(1, result.errors.length);
    JSError error = result.errors[0];
    assertFalse(error.description.contains("Comp$$PropsValidator"));
    assertTrue(error.description.contains("\"strProp\" was expected to be of type"));
    assertEquals(
        PropTypesExtractor.PROP_TYPES_VALIDATION_MISMATCH, error.getType());
  }
}
