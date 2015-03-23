package info.persistent.react.jscomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CustomPassExecutionTime;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.StrictWarningsGuard;
import com.google.javascript.jscomp.WarningLevel;

import org.junit.Test;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * Test {@link ReactCompilerPass}.
 */
public class ReactCompilerPassTest {
  private static String REACT_SOURCE;
  static {
    try {
      REACT_SOURCE = Resources.toString(
          Resources.getResource("info/persistent/react/jscomp/react.stub.js"),
          Charsets.UTF_8);
    } catch (IOException err) {
      throw new RuntimeException(err);
    }
  }
  // Used to find the test output (and separate it from the React source)¡
  private static final String ACTUAL_JS_INPUT_MARKER = "// Input 1\n";

  @Test public void testMinimalComponent() {
    test(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "React.render(React.createElement(Comp), document.body);",
      // createClass and other React methods should get renamed.
      "React.$render$(React.$createElement$(React.$createClass$({" +
        "$render$:function(){return React.$createElement$(\"div\")}" +
      "})),document.body);");
  }

  @Test public void testInstanceMethods() {
    test(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}," +
        "method: function() {window.foo = 123;}" +
      "});" +
      "var inst = React.render(React.createElement(Comp), document.body);" +
      "inst.method();",
      // Method invocations should not result in warnings if they're known.
      "React.$render$(React.$createElement$(React.$createClass$({" +
        "$render$:function(){return React.$createElement$(\"div\")}," +
        "$method$:function(){window.$foo$=123}" +
      "})),document.body).$method$();");
    testError(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}," +
        "/** @param {number} a */" +
        "method: function(a) {window.foo = a;}" +
      "});" +
      "var inst = React.render(React.createElement(Comp), document.body);" +
      "inst.method('notanumber');",
      // Their arguments should be validated.
      DiagnosticType.error("JSC_TYPE_MISMATCH", ""));
    testError(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "var inst = React.render(React.createElement(Comp), document.body);" +
      "inst.unknownMethod()",
      // And unknown methods should be flagged.
      DiagnosticType.error("JSC_INEXISTENT_PROPERTY", ""));
  }

  @Test public void testNamespacedComponent() {
    test(
      "var ns = {};ns.Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "React.render(React.createElement(ns.Comp), document.body);",
      // createClass and other React methods should get renamed.
      "React.$render$(React.$createElement$(React.$createClass$({" +
        "$render$:function(){return React.$createElement$(\"div\")}" +
      "})),document.body);");
  }

  @Test public void testUnusedComponent() {
    test(
      // Unused components should not appear in the output.
      "var Unused = React.createClass({" +
        "render: function() {return React.createElement(\"div\", null);}" +
      "});",
      "");
  }

  @Test public void testThisUsage() {
    test(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}," +
        "method: function() {this.setState({foo: 123});}" +
      "});" +
      "React.render(React.createElement(Comp), document.body);",
      // Use of "this" should not cause any warnings.
      "React.$render$(React.$createElement$(React.$createClass$({" +
        "$render$:function(){return React.$createElement$(\"div\")}," +
        "$method$:function(){this.$setState$({$foo$:123})}" +
      "})),document.body);");
  }

  /**
   * Tests React.createClass() validation done by the compiler pass (as opposed
   * to type-based validation).
   */
  @Test public void testCreateClassValidation() {
    testError(
        "var Comp = React.createClass(1)",
        ReactCompilerPass.CREATE_CLASS_SPEC_NOT_VALID);
    testError(
        "var Comp = React.createClass({}, 1)",
        ReactCompilerPass.CREATE_CLASS_UNEXPECTED_PARAMS);
    testError(
        "var a = 1 + React.createClass({})",
        ReactCompilerPass.CREATE_CLASS_TARGET_INVALID);
  }

  /**
   * Tests React.createElement() validation done by the compiler pass (as
   * opposed to type-based validation).
   */
  @Test public void testCreateElementValidation() {
    testError(
        "React.createElement()",
        ReactCompilerPass.CREATE_ELEMENT_UNEXPECTED_PARAMS);
  }

  /**
   * Tests validation done by the types declarted in the types.js file. Not
   * exhaustive, just tests that the type declarations are included.
   */
  @Test public void testTypeValidation() {
    testError(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "React.render(React.createElement(Comp), document.body, 123);",
      DiagnosticType.error("JSC_TYPE_MISMATCH", ""));
  }

  private static void test(String inputJs, String expectedJs) {
    test(inputJs, expectedJs, null);
  }

  private static void testError(String inputJs, DiagnosticType expectedError) {
    test(inputJs, "", expectedError);
  }

  private static void test(
        String inputJs,
        String expectedJs,
        DiagnosticType expectedError) {
    Compiler compiler = new Compiler(
        new PrintStream(ByteStreams.nullOutputStream())); // Silence logging
    compiler.disableThreads(); // Makes errors easier to track down.
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setWarningLevel(
        DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.ERROR);
    options.setGeneratePseudoNames(true);
    options.addWarningsGuard(new ReactWarningsGuard());
    // Report warnings as errors to make tests simpler
    options.addWarningsGuard(new StrictWarningsGuard());
    options.setPrintInputDelimiter(true);
    options.setCustomPasses(ImmutableMultimap.of(
        CustomPassExecutionTime.BEFORE_CHECKS,
        (CompilerPass) new ReactCompilerPass(compiler)));
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("/src/react.js", REACT_SOURCE),
        SourceFile.fromCode("/src/test.js", inputJs)
    );
    List<SourceFile> externs = ImmutableList.of(
        SourceFile.fromCode("externs.js",
          "/** @constructor */ function Element() {};" +
          "var document;" +
          "document.body;" +
          "var window;")
    );
    Result result = compiler.compile(externs, inputs, options);
    if (expectedError == null) {
      assertEquals(
          "Unexpected errors: " + Joiner.on(",").join(result.errors),
          0, result.errors.length);
      assertEquals(
          "Unexpected warnings: " + Joiner.on(",").join(result.warnings),
          0, result.warnings.length);
      assertTrue(result.success);
      String actualJs = compiler.toSource();
      int inputIndex = actualJs.indexOf(ACTUAL_JS_INPUT_MARKER);
      assertNotEquals(-1, inputIndex);
      actualJs = actualJs.substring(
          inputIndex + ACTUAL_JS_INPUT_MARKER.length());
      assertEquals(expectedJs, actualJs);
    } else {
      assertFalse(
          "Expected failure, instead got output: " + compiler.toSource(),
          result.success);
      assertTrue(result.errors.length > 0);
      boolean foundError = false;
      for (JSError error : result.errors) {
        if (error.getType().equals(expectedError)) {
          foundError = true;
          break;
        }
      }
      assertTrue(
          "Did not find expected error " + expectedError +
              ", instead found " + Joiner.on(",").join(result.errors),
          foundError);
      assertEquals(
          "Unexpected warnings: " + Joiner.on(",").join(result.warnings),
          0, result.warnings.length);
    }
  }
}
