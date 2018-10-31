package info.persistent.react.jscomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import com.google.javascript.jscomp.AbstractCommandLineRunner;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CodePrinter;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
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
        "render: function() {" +
          "return React.createElement(" +
            "\"div\", null, React.DOM.span(null, \"child\"));" +
          "}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      // createClass and other React methods should get renamed.
      "ReactDOM.$render$(React.$createElement$(React.$createClass$({" +
        "$render$:function(){" +
          "return React.$createElement$(" +
            "\"div\",null,React.$DOM$.$span$(null,\"child\"))" +
        "}" +
      "})),document.body);");
  }

  @Test public void testInstanceMethods() {
    test(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}," +
        "method: function() {window.foo = 123;}" +
      "});" +
      "var inst = ReactDOM.render(React.createElement(Comp), document.body);" +
      "inst.method();",
      // Method invocations should not result in warnings if they're known.
      "ReactDOM.$render$(React.$createElement$(React.$createClass$({" +
        "$render$:function(){return React.$createElement$(\"div\")}," +
        "$method$:function(){window.$foo$=123}" +
      "})),document.body).$method$();");
    test(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}," +
        "/** @private */" +
        "privateMethod1_: function(a) {window.foo = 123 + a;}," +
        "/** @private */" +
        "privateMethod2_: function() {this.privateMethod1_(1);}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      // Private methods should be invokable.
      "ReactDOM.$render$(React.$createElement$(React.$createClass$({" +
        "$render$:function(){return React.$createElement$(\"div\")}," +
        "$privateMethod1_$:function($a$jscomp$1$$){window.$foo$=123+$a$jscomp$1$$}," +
        "$privateMethod2_$:function(){this.$privateMethod1_$(1)}" +
      "})),document.body);");
    testError(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}," +
        "/** @param {number} a */" +
        "method: function(a) {window.foo = a;}" +
      "});" +
      "var inst = ReactDOM.render(React.createElement(Comp), document.body);" +
      "inst.method('notanumber');",
      // Their arguments should be validated.
      "JSC_TYPE_MISMATCH");
    testError(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "var inst = ReactDOM.render(React.createElement(Comp), document.body);" +
      "inst.unknownMethod()",
      // And unknown methods should be flagged.
      "JSC_INEXISTENT_PROPERTY");
  }

  @Test public void testMixins() {
    test(
      "var ChainedMixin = React.createMixin({" +
        "chainedMixinMethod: function() {window.foo = 456}" +
      "});\n" +
      "var Mixin = React.createMixin({" +
        "mixins: [ChainedMixin]," +
        "mixinMethod: function() {window.foo = 123}" +
      "});\n" +
      "var Comp = React.createClass({" +
        "mixins: [Mixin]," +
        "render: function() {" +
          "this.mixinMethod();" +
          "this.chainedMixinMethod();" +
          "return React.createElement(\"div\");" +
        "}" +
      "});" +
      "var inst = ReactDOM.render(React.createElement(Comp), document.body);" +
      "inst.mixinMethod();" +
      "inst.chainedMixinMethod();",
      // Mixin method invocations should not result in warnings if they're
      // known, either directly or via chained mixins.
      "var $inst$$=ReactDOM.$render$(React.$createElement$(React.$createClass$({" +
        "$mixins$:[{" +
          "$mixins$:[{" +
            "$chainedMixinMethod$:function(){window.$foo$=456}" +
          "}]," +
          "$mixinMethod$:function(){window.$foo$=123}" +
        "}]," +
        "$render$:function(){" +
          "this.$mixinMethod$();" +
          "this.$chainedMixinMethod$();" +
          "return React.$createElement$(\"div\")" +
        "}" +
      "})),document.body);" +
      "$inst$$.$mixinMethod$();" +
      "$inst$$.$chainedMixinMethod$();");
    test(
      "var Mixin = React.createMixin({" +
        "mixinMethod: function() {window.foo=this.mixinAbstractMethod()}" +
      "});" +
      "/** @return {number} @protected */" +
      "Mixin.mixinAbstractMethod;" +
      "var Comp = React.createClass({" +
        "mixins: [Mixin]," +
        "render: function() {" +
          "this.mixinMethod();" +
          "return React.createElement(\"div\");" +
        "}," +
        "mixinAbstractMethod: function() {return 123;}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      // Mixins can support abstract methods via additional properties.
      "ReactDOM.$render$(React.$createElement$(React.$createClass$({" +
        "$mixins$:[{" +
          "$mixinMethod$:function(){window.$foo$=123}" +
        "}]," +
        "$render$:function(){" +
          "this.$mixinMethod$();" +
          "return React.$createElement$(\"div\")" +
        "}," +
        "$mixinAbstractMethod$:function(){return 123}" +
      "})),document.body);");
    testError(
      "var Mixin = React.createMixin({" +
        "/** @param {number} a */" +
        "mixinMethod: function(a) {window.foo = 123}" +
      "});\n" +
      "var Comp = React.createClass({" +
        "mixins: [Mixin]," +
        "render: function() {" +
          "this.mixinMethod(\"notanumber\");" +
          "return React.createElement(\"div\");" +
        "}" +
      "});" +
      "var inst = ReactDOM.render(React.createElement(Comp), document.body);" +
      "inst.mixinMethod(\"notanumber\");",
      // Mixin methods should have their parameter types check when invoked from
      // the component.
      "JSC_TYPE_MISMATCH");
    testError(
      "var Mixin = React.createMixin({" +
        "/** @private */" +
        "privateMixinMethod_: function() {}" +
      "});\n" +
      "var Comp = React.createClass({" +
        "mixins: [Mixin]," +
        "render: function() {" +
          "this.privateMixinMethod_();" +
          "return null;" +
        "}" +
      "});",
      // Private mixin methods should not be exposed to the component.
      "JSC_INEXISTENT_PROPERTY");
    testError(
      "var Mixin = React.createMixin({" +
        "mixinMethod: function() {" +
          "window.foo = this.mixinAbstractMethod().noSuchMethod()" +
        "}" +
      "});" +
      "/** @return {number} */" +
      "Mixin.mixinAbstractMethod;" +
      "var Comp = React.createClass({" +
        "mixins: [Mixin]," +
        "render: function() {" +
          "this.mixinMethod();" +
          "return React.createElement(\"div\");" +
        "}," +
        "mixinAbstractMethod: function() {return 123;}" +
      "});",
      // Abstract methods have their types checked too, on both the mixin
      // side...
      "JSC_INEXISTENT_PROPERTY");
    testError(
      "var Mixin = React.createMixin({" +
        "mixinMethod: function() {window.foo = this.mixinAbstractMethod()}" +
      "});" +
      "/** @return {number} */" +
      "Mixin.mixinAbstractMethod;" +
      "var Comp = React.createClass({" +
        "mixins: [Mixin]," +
        "render: function() {" +
          "this.mixinMethod();" +
          "return React.createElement(\"div\");" +
        "}," +
        "mixinAbstractMethod: function() {return \"notanumber\";}" +
      "});",
      // ...and the component side
      "JSC_TYPE_MISMATCH");
    test(
      "var Mixin = React.createMixin({});" +
      "/** @param {number} param1 */" +
      "Mixin.mixinAbstractMethod;" +
      "var Comp = React.createClass({" +
        "mixins: [Mixin]," +
        "render: function() {" +
          "return React.createElement(\"div\");" +
        "}," +
        "mixinAbstractMethod: function() {}" +
      "});",
      // But implementations should be OK if they omit parameters...
      "");
    test(
      "var Mixin = React.createMixin({});" +
      "/** @param {number} param1 */" +
      "Mixin.mixinAbstractMethod;" +
      "var Comp = React.createClass({" +
        "mixins: [Mixin]," +
        "render: function() {" +
          "return React.createElement(\"div\");" +
        "}," +
        "mixinAbstractMethod: function(renamedParam1) {}" +
      "});",
      //  ...or rename them.
      "");
    test(
      "var Mixin = React.createMixin({});" +
      "/**\n" +
      " * @param {T} param1\n" +
      " * @return {T}\n" +
      " * @template T\n" +
      " */" +
      "Mixin.mixinAbstractMethod;" +
      "var Comp = React.createClass({" +
        "mixins: [Mixin]," +
        "render: function() {" +
          "return React.createElement(\"div\");" +
        "}," +
        "mixinAbstractMethod: function(param1) {return param1}" +
      "});",
      // Template types are copied over.
      "");
  }

  @Test public void testMixinsRepeatedMethods() {
    test(
      "var Mixin = React.createMixin({" +
        "componentDidMount: function() {}" +
      "});\n" +
      "var Comp = React.createClass({" +
        "mixins: [Mixin]," +
        "componentDidMount: function() {}," +
        "render: function() {" +
          "return React.createElement(\"div\");" +
        "}" +
      "});",
      // It's OK for a base class to redefine a mixin's method component
      // lifecycle method.
      "");
  }

  @Test public void testNamespacedComponent() {
    test(
      "var ns = {};ns.Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "ReactDOM.render(React.createElement(ns.Comp), document.body);",
      // createClass and other React methods should get renamed.
      "ReactDOM.$render$(React.$createElement$(React.$createClass$({" +
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
      "ReactDOM.render(React.createElement(Comp), document.body);",
      // Use of "this" should not cause any warnings.
      "ReactDOM.$render$(React.$createElement$(React.$createClass$({" +
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
        ReactCompilerPass.CREATE_TYPE_SPEC_NOT_VALID);
    testError(
        "var Comp = React.createClass({}, 1)",
        ReactCompilerPass.CREATE_TYPE_UNEXPECTED_PARAMS);
    testError(
        "var a = 1 + React.createClass({})",
        ReactCompilerPass.CREATE_TYPE_TARGET_INVALID);
  }

  /**
   * Tests React.createMixin() validation done by the compiler pass (as opposed
   * to type-based validation).
   */
  @Test public void testCreateMixinValidation() {
    testError(
        "var Mixin = React.createMixin(1)",
        ReactCompilerPass.CREATE_TYPE_SPEC_NOT_VALID);
    testError(
        "var Mixin = React.createMixin({}, 1)",
        ReactCompilerPass.CREATE_TYPE_UNEXPECTED_PARAMS);
    testError(
        "var a = 1 + React.createMixin({})",
        ReactCompilerPass.CREATE_TYPE_TARGET_INVALID);
  }

  /**
   * Tests "mixins" spec property validation done by the compiler pass (as
   * opposed to type-based validation).
   */
  @Test public void testMixinsValidation() {
    testError(
        "var Comp = React.createClass({mixins: 1})",
        ReactCompilerPass.MIXINS_UNEXPECTED_TYPE);
    testError(
        "var Comp = React.createClass({mixins: [1]})",
        ReactCompilerPass.MIXIN_EXPECTED_NAME);
    testError(
        "var Comp = React.createClass({mixins: [NonExistent]})",
        ReactCompilerPass.MIXIN_UNKNOWN);
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
   * Tests that React.createElement calls have their return type cast to either
   * ReactDOMElement or ReactElement.<ClassType>
   */
  @Test public void testCreateElementCasting() {
    // Tests that element.type is a string
    test(
        "window.type = React.createElement(\"div\").type.charAt(0)",
        "window.$type$=React.$createElement$(\"div\").$type$.charAt(0);");
    // Tests that element.type is not a string...
    testError(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "React.createElement(Comp).type.charAt(0)",
      "JSC_INEXISTENT_PROPERTY");
    // ...but is present...
    test(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "window.type = React.createElement(Comp).type;",
      "window.$type$=React.$createElement$(React.$createClass$({" +
        "$render$:function(){return React.$createElement$(\"div\")}" +
      "})).$type$;");
    // ...unlike other properties.
    testError(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "window.foo = React.createElement(Comp).notAnElementProperty;",
      "JSC_INEXISTENT_PROPERTY");
  }

  /**
   * Tests validation done by the types declarted in the types.js file. Not
   * exhaustive, just tests that the type declarations are included.
   */
  @Test public void testTypeValidation() {
    testError(
      "var Comp = React.createClass({render: \"notafunction\"});",
      "JSC_TYPE_MISMATCH");
    testError(
      "var Comp = React.createClass({displayName: function() {}});",
      "JSC_TYPE_MISMATCH");
    test(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "window.foo = Comp.displayName.charAt(0);",
      // displayName is a valid string property of classes
      "window.$foo$=React.$createClass$({" +
        "$render$:function(){return React.$createElement$(\"div\")}" +
      "}).displayName.charAt(0);");
    testError(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "window.foo = Comp.displayName.notAStringMethod();",
      "JSC_INEXISTENT_PROPERTY");
    testError(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "window.foo = Comp.nonExistentProperty;",
      "JSC_INEXISTENT_PROPERTY");
    testError(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body, 123);",
      "JSC_TYPE_MISMATCH");
    testError(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}," +
        "shouldComponentUpdate: function(nextProps, nextState) {return 123;}" +
      "});",
      // Overrides/implemementations of built-in methods should conform to the
      // type annotations added in types.js, even if they're not explicitly
      // present in the spec.
      "JSC_TYPE_MISMATCH");
    test(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}," +
        "shouldComponentUpdate: function() {return false;}" +
      "});",
      // But implementations should be OK if they omit parameters...
      "");
    test(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}," +
        "shouldComponentUpdate: function(param1, param2) {return false;}" +
      "});",
      // ...or rename them.
      "");
    testError(
      "var Comp = React.createClass({" +
        "render: function() {return 123;}" +
      "});",
      "JSC_TYPE_MISMATCH");
    testError(
      "var Mixin = React.createMixin({" +
        "shouldComponentUpdate: function(nextProps, nextState) {return 123;}" +
      "});",
      // Same for mixins
      "JSC_TYPE_MISMATCH");
    testError(
      "var Comp = React.createClass({" +
        "render: function() {" +
          "this.isMounted().indexOf(\"true\");" +
          "return React.createElement(\"div\");" +
        "}" +
      "});",
      // Same for invocations of built-in component methods.
      "JSC_INEXISTENT_PROPERTY");
    test(
      "var Comp = React.createClass({" +
        "refAccess: function() {return this.refs[\"foo\"];}" +
      "});",
      // Refs can be accessed via quoted strings.
      "");
    testError(
      "var Comp = React.createClass({" +
        "refAccess: function() {return this.refs.foo;}" +
      "});",
      // ...but not as property accesses (since they may get renamed)
      "JSC_ILLEGAL_PROPERTY_ACCESS");
  }

  /**
   * Tests that JSDoc type annotations on custom methods are checked.
   */
  @Test public void testMethodJsDoc() {
    testError(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}," +
        "/** @param {number} numberParam */" +
        "someMethod: function(numberParam) {numberParam.notAMethod();}" +
      "});",
      "JSC_INEXISTENT_PROPERTY");
    testError(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(" +
            "\"div\", null, this.someMethod(\"notanumber\"));}," +
        "/** @param {number} numberParam */" +
        "someMethod: function(numberParam) {return numberParam + 1;}" +
      "});",
      "JSC_TYPE_MISMATCH");
  }

  /**
   * Tests that component methods can have default parameters.
   */
  @Test public void testMethodDefaultParameters() {
    test(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}," +
        "/** @param {number=} numberParam @return {number}*/" +
        "someMethod: function(numberParam = 1) {return numberParam * 2;}" +
      "});",
      "");
  }

  /**
   * Tests that components can be marked as implementing interfaces.
   */
  @Test public void testInterfaces() {
    test(
      "/** @interface */ function AnInterface() {}\n" +
      "/** @return {number} */\n" +
      "AnInterface.prototype.interfaceMethod = function() {};\n" +
      "/** @implements {AnInterface} */" +
      "var Comp = React.createClass({" +
        "/** @override */ interfaceMethod: function() {\n" +
            "return 1;\n" +
        "},\n" +
        "render: function() {\n" +
          "return React.createElement(\"div\");\n" +
        "}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "ReactDOM.$render$(React.$createElement$(React.$createClass$({" +
        "$interfaceMethod$:function(){" +
            "return 1" +
        "}," +
        "$render$:function(){" +
          "return React.$createElement$(\"div\")" +
        "}" +
      "})),document.body);");
    // We can't test that missing methods cause compiler warnings since we're
    // declaring CompInterface as extending AnInterface, thus the methods
    // assumed to be there.
  }

  @Test public void testPropTypes() {
    // Basic prop types
    test(
      "var Comp = React.createClass({" +
        "propTypes: {aProp: React.PropTypes.string}," +
        "render: function() {" +
          "return React.createElement(\"div\", null, this.props.aProp);" +
        "}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "ReactDOM.$render$(React.$createElement$(React.$createClass$({" +
        "$propTypes$:{$aProp$:React.$PropTypes$.$string$}," +
        "$render$:function(){" +
          "return React.$createElement$(\"div\",null,this.$props$.$aProp$)" +
        "}" +
      "})),document.body);");
    // isRequired variant
    test(
      "window.foo=React.PropTypes.string.isRequired;",
      "window.$foo$=React.$PropTypes$.$string$.$isRequired$;");
    // Other variants are rejected
    testError(
      "window.foo=React.PropTypes.string.isSortOfRequired;",
      "JSC_INEXISTENT_PROPERTY");
    // arrayOf
    test(
      "window.foo=React.PropTypes.arrayOf(React.PropTypes.string);",
      "window.$foo$=React.$PropTypes$.$arrayOf$(React.$PropTypes$.$string$);");
    test(
      "window.foo=React.PropTypes.arrayOf(React.PropTypes.string).isRequired;",
      "window.$foo$=React.$PropTypes$.$arrayOf$(React.$PropTypes$.$string$).$isRequired$;");
    testError(
      "window.foo=React.PropTypes.arrayOf(123);",
      "JSC_TYPE_MISMATCH");
    testError(
      "window.foo=React.PropTypes.arrayOf(React.PropTypes.string).isSortOfRequired;",
      "JSC_INEXISTENT_PROPERTY");
    // instanceOf
    test(
      "window.foo=React.PropTypes.instanceOf(Element);",
      "window.$foo$=React.$PropTypes$.$instanceOf$(Element);");
    testError(
      "window.foo=React.PropTypes.instanceOf(123);",
      "JSC_TYPE_MISMATCH");
    // oneOf
    test(
      "window.foo=React.PropTypes.oneOf([1,2,3]);",
      "window.$foo$=React.$PropTypes$.$oneOf$([1,2,3]);");
    testError(
      "window.foo=React.PropTypes.oneOf(123);",
      "JSC_TYPE_MISMATCH");
    // oneOfType
    test(
      "window.foo=React.PropTypes.oneOfType([React.PropTypes.string]);",
      "window.$foo$=React.$PropTypes$.$oneOfType$([React.$PropTypes$.$string$]);");
    testError(
      "window.foo=React.PropTypes.oneOfType(123);",
      "JSC_TYPE_MISMATCH");
    // shape
    test(
      "window.foo=React.PropTypes.shape({str: React.PropTypes.string});",
      "window.$foo$=React.$PropTypes$.$shape$({$str$:React.$PropTypes$.$string$});");
    testError(
      "window.foo=React.PropTypes.shape(123);",
      "JSC_TYPE_MISMATCH");
  }

  @Test public void testMinifiedReact() {
    // propTypes should get stripped if we're using the minimized React build
    // (since they're not checked).
    // Additionally, React.createClass and React.createElement calls should be
    // replaced with React$createClass and React$createElement aliases (which
    // can get fully renamed).
    test(
      "var Comp = React.createClass({" +
        "propTypes: {aProp: React.PropTypes.string}," +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "ReactDOM.$render$($React$createElement$$($React$createClass$$({" +
        "$render$:function(){return $React$createElement$$(\"div\")}" +
      "})),document.body);",
      "/src/react.min.js",
      null,
      null);
    // But propTypes tagged with @struct should be preserved.
    test(
      "var Comp = React.createClass({" +
        "/** @struct */" +
        "propTypes: {aProp: React.PropTypes.string}," +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "ReactDOM.$render$($React$createElement$$($React$createClass$$({" +
        "$propTypes$:{$aProp$:React.$PropTypes$.$string$}," +
        "$render$:function(){return $React$createElement$$(\"div\")}" +
      "})),document.body);",
      "/src/react.min.js",
      null,
      null);
  }

  @Test public void testNoRenameReactApi() {
    ReactCompilerPass.Options passOptions = new ReactCompilerPass.Options();
    passOptions.renameReactApi = false;
    test(
      "var Comp = React.createClass({" +
        "propTypes: {aProp: React.PropTypes.string}," +
        "render: function() {\n" +
          "return React.createElement(\"div\", {onClick: null});\n" +
        "}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "ReactDOM.render(React.createElement(React.createClass({" +
        "propTypes:{$aProp$:React.PropTypes.string}," +
        "render:function(){" +
          "return React.createElement(\"div\",{onClick:null})" +
        "}" +
      "})),document.body);",
      null,
      passOptions,
      null);
    // Even with a minified build there is no renaming.
    test(
      "var Comp = React.createClass({" +
        "propTypes: {aProp: React.PropTypes.string}," +
        "render: function() {\n" +
          "return React.createElement(\"div\", {onClick: null});\n" +
        "}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "ReactDOM.render($React$createElement$$($React$createClass$$({" +
        "render:function(){" +
          "return $React$createElement$$(\"div\",{onClick:null})" +
        "}" +
      "})),document.body);",
      "/src/react.min.js",
      passOptions,
      null);
    // Test that other API symbols are not renamed either.
    List<String> reactApiSymbols = ImmutableList.of("React", "React.Component",
      "React.PureComponent", "React.cloneElement", "ReactDOM.findDOMNode",
      "ReactDOM.unmountComponentAtNode");
    for (String reactApiSymbol : reactApiSymbols) {
      test(
        "window['test'] = " + reactApiSymbol + ";",
        "window.test=" + reactApiSymbol + ";",
        null,
        passOptions,
        null);
    }
  }

  @Test public void testExport() {
    // Props where the class is tagged with @export should not get renamed,
    // nor should methods explicitly tagged with @public.
    test(
      "/** @export */" +
      "var Comp = React.createClass({" +
        "propTypes: {aProp: React.PropTypes.string},\n" +
        "/** @public */ publicFunction: function() {\n" +
            "return \"dont_rename_me_bro\";\n" +
        "},\n" +
        "/** @private */ privateFunction_: function() {\n" +
            "return 1;\n" +
        "},\n" +
        "render: function() {\n" +
          "return React.createElement(\"div\", null, this.props.aProp);\n" +
        "}" +
      "});" +
      "ReactDOM.render(React.createElement(" +
          "Comp, {aProp: \"foo\"}), document.body);",
      "ReactDOM.$render$(React.$createElement$(React.$createClass$({" +
        "$propTypes$:{aProp:React.$PropTypes$.$string$}," +
        "publicFunction:function(){" +
            "return\"dont_rename_me_bro\"" +
        "}," +
        "$privateFunction_$:function(){" +
            "return 1" +
        "}," +
        "$render$:function(){" +
          "return React.$createElement$(\"div\",null,this.$props$.aProp)" +
        "}" +
      "}),{aProp:\"foo\"}),document.body);");
    // Even with a minified build there is no renaming.
    test(
      "/** @export */" +
      "var Comp = React.createClass({" +
        "propTypes: {aProp: React.PropTypes.string}," +
        "/** @public */ publicFunction: function() {\n" +
            "return \"dont_rename_me_bro\";\n" +
        "},\n" +
        "/** @private */ privateFunction_: function() {\n" +
            "return 1;\n" +
        "},\n" +
        "render: function() {\n" +
          "return React.createElement(\"div\", null, this.props.aProp);\n" +
        "}" +
      "});" +
      "ReactDOM.render(React.createElement(" +
          "Comp, {aProp: \"foo\"}), document.body);",
      "ReactDOM.$render$($React$createElement$$($React$createClass$$({" +
        "publicFunction:function(){" +
            "return\"dont_rename_me_bro\"" +
        "}," +
        "$privateFunction_$:function(){" +
            "return 1" +
        "}," +
        "$render$:function(){" +
          "return $React$createElement$$(\"div\",null,this.$props$.aProp)" +
        "}" +
      "}),{aProp:\"foo\"}),document.body);",
      "/src/react.min.js",
      null,
      null);
  }

  @Test public void testPropTypesTypeChecking() {
    // Validate use of props within methods.
    testError(
      "var Comp = React.createClass({" +
        "propTypes: {numberProp: React.PropTypes.number}," +
        "render: function() {" +
          "this.props.numberProp();" +
          "return null;" +
        "}" +
      "});",
      "JSC_NOT_FUNCTION_TYPE");
    // Validate props at creation time.
    testPropTypesError(
        "{strProp: React.PropTypes.string}",
        "{strProp: 1}",
        "JSC_TYPE_MISMATCH");
    // Required props cannot be null
    testPropTypesError(
        "{strProp: React.PropTypes.string.isRequired}",
        "{strProp: null}",
        "JSC_TYPE_MISMATCH");
    // Required props cannot be omitted
    testPropTypesError(
        "{strProp: React.PropTypes.string.isRequired}",
        "{}",
        "JSC_TYPE_MISMATCH");
    testPropTypesError(
        "{strProp: React.PropTypes.string.isRequired}",
        "null",
        "JSC_TYPE_MISMATCH");
    // Optional props can be null
    testPropTypesNoError(
        "{strProp: React.PropTypes.string}",
        "null");
    // Optional props can be omitted
    testPropTypesNoError(
        "{strProp: React.PropTypes.string}",
        "{}");
    testPropTypesNoError(
        "{strProp: React.PropTypes.string}",
        "null");
    // Validate object prop
    testPropTypesError(
        "{objProp: React.PropTypes.instanceOf(Message).isRequired}",
        "{objProp: \"foo\"}",
        "JSC_TYPE_MISMATCH");
    // Required object prop cannot be null
    testPropTypesError(
        "{objProp: React.PropTypes.instanceOf(Message).isRequired}",
        "{objProp: null}",
        "JSC_TYPE_MISMATCH");
    // Required object prop cannot be omitted
    testPropTypesError(
        "{objProp: React.PropTypes.instanceOf(Message).isRequired}",
        "{}",
        "JSC_TYPE_MISMATCH");
    // Optional object prop can be null
    testPropTypesNoError(
        "{objProp: React.PropTypes.instanceOf(Message)}",
        "{objProp: null}");
    // Optional object prop can be ommitted
    testPropTypesNoError(
        "{objProp: React.PropTypes.instanceOf(Message)}",
        "{}");
    // Validate array prop
    testPropTypesError(
        "{arrayProp: React.PropTypes.arrayOf(React.PropTypes.string)}",
        "{arrayProp: 1}",
        "JSC_TYPE_MISMATCH");
    // Validate object prop
    testPropTypesError(
        "{objProp: React.PropTypes.objectOf(React.PropTypes.string)}",
        "{objProp: 1}",
        "JSC_TYPE_MISMATCH");
    // Validate oneOfType prop
    testPropTypesError(
        "{unionProp: React.PropTypes.oneOfType([" +
          "React.PropTypes.string," +
          "React.PropTypes.number" +
        "])}",
        "{unionProp: false}",
        "JSC_TYPE_MISMATCH");
    testPropTypesNoError(
        "{unionProp: React.PropTypes.oneOfType([" +
          "React.PropTypes.string," +
          "React.PropTypes.number" +
        "])}",
        "{unionProp: 1}");
    // Validate children prop
    testNoError(
        "var Comp = React.createClass({" +
          "propTypes: {" +
            "children: React.PropTypes.element.isRequired" +
          "}," +
          "render: function() {return null;}" +
        "});\n" +
        "React.createElement(Comp, {}, React.createElement(\"div\"));");
    testNoError(
        "var Comp = React.createClass({" +
          "propTypes: {" +
            "children: React.PropTypes.element.isRequired" +
          "}," +
          "render: function() {return null;}" +
        "});\n" +
        "React.createElement(Comp, {}, React.createElement(Comp));");
    testError(
        "var Comp = React.createClass({" +
          "propTypes: {" +
            "children: React.PropTypes.element.isRequired" +
          "}," +
          "render: function() {return null;}" +
        "});\n" +
        "React.createElement(Comp, {});",
        "REACT_NO_CHILDREN_ARGUMENT");
    testError(
        "var Comp = React.createClass({" +
          "propTypes: {" +
            "children: React.PropTypes.element.isRequired" +
          "}," +
          "render: function() {return null;}" +
        "});\n" +
        "React.createElement(Comp, {}, null);",
        "JSC_TYPE_MISMATCH");

    // Handle spread operator when creating elements
    testPropTypesError(
        "{aProp: React.PropTypes.number.isRequired}",
        "Object.assign({aProp: '1'}, {})",
        "JSC_TYPE_MISMATCH");
    testPropTypesError(
        "{aProp: React.PropTypes.number.isRequired}",
        "{aProp: '1', ...{}}",
        "JSC_TYPE_MISMATCH");

    testPropTypesNoError(
        "{aProp: React.PropTypes.number.isRequired}",
        "Object.assign({aProp: 1}, {})");
    testPropTypesNoError(
          "{aProp: React.PropTypes.number.isRequired}",
          "{aProp: 1, ...{}}");

    testPropTypesNoError(
        "{aProp: React.PropTypes.number.isRequired}",
        "Object.assign({}, {})");
    testPropTypesNoError(
        "{aProp: React.PropTypes.number.isRequired}",
        "{...{}}");
    
    testPropTypesNoError(
        "{aProp: React.PropTypes.number.isRequired," +
        "bProp: React.PropTypes.number.isRequired}",
        "Object.assign({}, {aProp: 1})");
    testPropTypesNoError(
        "{aProp: React.PropTypes.number.isRequired," +
        "bProp: React.PropTypes.number.isRequired}",
        "{...{aProp: 1}}");

    testPropTypesError(
        "{aProp: React.PropTypes.number.isRequired," +
        "bProp: React.PropTypes.number.isRequired}",
        "Object.assign({}, {aProp: '1'})",
        "JSC_TYPE_MISMATCH");
    testPropTypesError(
        "{aProp: React.PropTypes.number.isRequired," +
        "bProp: React.PropTypes.number.isRequired}",
        "{...{aProp: '1'}}",
        "JSC_TYPE_MISMATCH");

    testNoError(
        "var Comp = React.createClass({" +
          "propTypes: {" +
            "aProp: React.PropTypes.string.isRequired" +
          "}," +
          "getDefaultProps: function() {" +
            "return {aProp: \"1\"};" +
          "}," +
          "render: function() {return null;}" +
        "});\n" +
        "React.createElement(Comp, Object.assign({aProp: \"1\"}, {}))");
    testNoError(
        "var Comp = React.createClass({" +
          "propTypes: {" +
            "aProp: React.PropTypes.string.isRequired" +
          "}," +
          "getDefaultProps: function() {" +
            "return {aProp: \"1\"};" +
          "}," +
          "render: function() {return null;}" +
        "});\n" +
        "React.createElement(Comp, {aProp: \"1\",...{}})");
  
    // Custom type expressions
    testPropTypesError(
        "{/** @type {boolean} */ boolProp: function() {}}",
        "{boolProp: 1}",
        "JSC_TYPE_MISMATCH");
    testPropTypesNoError(
        "{/** @type {boolean} */ boolProp: function() {}}",
        "{boolProp: true}");
    testPropTypesNoError(
        "{/** @type {(boolean|undefined|null)} */ boolProp: function() {}}",
        "null");
    // Required props with default values can be ommitted.
    testNoError(
        "var Comp = React.createClass({" +
          "propTypes: {" +
            "strProp: React.PropTypes.string.isRequired" +
          "}," +
          "getDefaultProps: function() {" +
            "return {strProp: \"1\"};" +
          "}," +
          "render: function() {return null;}" +
        "});\n" +
        "React.createElement(Comp, {});");
    testNoError(
        "var Comp = React.createClass({" +
          "propTypes: {" +
            "strProp: React.PropTypes.string.isRequired" +
          "}," +
          "getDefaultProps: function() {" +
            "return {strProp: \"1\"};" +
          "}," +
          "render: function() {return null;}" +
        "});\n" +
        "React.createElement(Comp, null);");
    // Applies to custom type expressions too
    testNoError(
        "var Comp = React.createClass({" +
          "propTypes: {" +
            "/** @type {boolean} */ boolProp: function() {}" +
          "}," +
          "getDefaultProps: function() {" +
            "return {boolProp: true};" +
          "}," +
          "render: function() {return null;}" +
        "});\n" +
        "React.createElement(Comp, {});");
    // But if they are provided their types are still checked.
    testError(
        "var Comp = React.createClass({" +
          "propTypes: {" +
            "strProp: React.PropTypes.string.isRequired" +
          "}," +
          "getDefaultProps: function() {" +
            "return {strProp: \"1\"};" +
          "}," +
          "render: function() {return null;}" +
        "});\n" +
        "React.createElement(Comp, {strProp: 1});",
        "JSC_TYPE_MISMATCH");
    // Even if not required, if they have a default value their value inside
    // the component is not null or undefined.
    testNoError(
        "var Comp = React.createClass({" +
          "propTypes: {" +
            "strProp: React.PropTypes.string" +
          "}," +
          "getDefaultProps: function() {" +
            "return {strProp: \"1\"};" +
          "}," +
          "render: function() {" +
            "this.strMethod_(this.props.strProp);" +
            "return null;" +
          "},\n" +
          "/**" +
          " * @param {string} param" +
          " * @return {string}" +
          " * @private" +
          " */" +
          "strMethod_: function(param) {return param;}" +
        "});\n" +
        "React.createElement(Comp, null);");
  }

  @Test public void testPropTypesMixins() {
    testError(
        "var Mixin = React.createMixin({" +
          "propTypes: {" +
            "mixinNumberProp: React.PropTypes.number.isRequired" +
          "}" +
        "});\n" +
        "var Comp = React.createClass({" +
          "mixins: [Mixin],\n" +
          "propTypes: {" +
            "numberProp: React.PropTypes.number.isRequired" +
          "}," +
          "render: function() {return this.props.mixinNumberProp();}" +
        "});\n",
        "JSC_NOT_FUNCTION_TYPE");
      // Even when the component doesn't have its own propTypes those of the
      // mixin are considered.
      testError(
        "var Mixin = React.createMixin({" +
          "propTypes: {" +
            "mixinNumberProp: React.PropTypes.number.isRequired" +
          "}" +
        "});\n" +
        "var Comp = React.createClass({" +
          "mixins: [Mixin],\n" +
          "render: function() {return this.props.mixinNumberProp();}" +
        "});\n",
        "JSC_NOT_FUNCTION_TYPE");
    testNoError(
        "var Mixin = React.createMixin({" +
          "propTypes: {" +
            "mixinFuncProp: React.PropTypes.func.isRequired" +
          "}" +
        "});\n" +
        "var Comp = React.createClass({" +
          "mixins: [Mixin],\n" +
          "render: function() {return this.props.mixinFuncProp();}" +
        "});\n");
      // The same propTypes can be in both mixins and components (and the
      // component one has precedence).
      testNoError(
        "var Mixin = React.createMixin({" +
          "propTypes: {" +
            "aProp: React.PropTypes.number.isRequired" +
          "}" +
        "});\n" +
        "var Comp = React.createClass({" +
          "mixins: [Mixin],\n" +
          "propTypes: {" +
            "aProp: React.PropTypes.number.isRequired" +
          "},\n" +
          "render: function() {return null;}" +
        "});\n");
      // Custom type expressions are handled
      testNoError(
        "var Mixin = React.createMixin({" +
          "propTypes: {" +
            "/** @type {boolean} */ boolProp: function() {}" +
          "}" +
        "});\n" +
        "var Comp = React.createClass({" +
          "mixins: [Mixin],\n" +
          "render: function() {return null;}" +
        "});\n" +
        "React.createElement(Comp, {boolProp: true});");
  }

  @Test public void testPropTypesComponentMethods() {
    // React component/lifecycle methods automatically get the specific prop
    // type injected.
    testError(
        "var Comp = React.createClass({" +
          "propTypes: {" +
            "numberProp: React.PropTypes.number.isRequired" +
          "}," +
          "componentWillReceiveProps: function(nextProps) {" +
             "nextProps.numberProp();" +
          "},\n" +
          "render: function() {return null;}" +
        "});\n" +
        "React.createElement(Comp, {numberProp: 1});",
        "JSC_NOT_FUNCTION_TYPE");
    // As do abstract mixin methods that use ReactProps as the type.
    testError(
        "var Mixin = React.createMixin({" +
        "});" +
        "/** @param {ReactProps} props @protected */" +
        "Mixin.mixinAbstractMethod;\n" +
        "var Comp = React.createClass({" +
          "mixins: [Mixin],\n" +
          "propTypes: {" +
            "numberProp: React.PropTypes.number.isRequired" +
          "}," +
          "mixinAbstractMethod: function(props) {" +
             "props.numberProp();" +
          "},\n" +
          "render: function() {return null;}" +
        "});\n" +
        "React.createElement(Comp, {numberProp: 1});",
        "JSC_NOT_FUNCTION_TYPE");
  }

  private void testPropTypesError(String propTypes, String props, String error) {
    testError(
      "/** @constructor */ function Message() {};\n" +
      "var Comp = React.createClass({" +
        "propTypes: " + propTypes + "," +
        "render: function() {return null;}" +
      "});\n" +
      "React.createElement(Comp, " + props + ");",
      error);
  }

  private void testPropTypesNoError(String propTypes, String props) {
    testNoError(
      "/** @constructor */ function Message() {};\n" +
      "var Comp = React.createClass({" +
        "propTypes: " + propTypes + "," +
        "render: function() {return null;}" +
      "});\n" +
      "React.createElement(Comp, " + props + ");");
  }

  @Test public void testChildren() {
    // Non-comprehensive test that the React.Children namespace functions exist.
    test(
      "var Comp = React.createClass({" +
        "render: function() {" +
          "return React.createElement(" +
              "\"div\", null, React.Children.only(this.props.children));" +
          "}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "ReactDOM.$render$(React.$createElement$(React.$createClass$({" +
        "$render$:function(){" +
          "return React.$createElement$(" +
              "\"div\",null,React.$Children$.$only$(this.$props$.$children$))" +
        "}" +
      "})),document.body);");
  }

  @Test public void testReactDOM() {
    testNoError("React.DOM.span()");
    testNoError("React.DOM.span(null)");
    testNoError("React.DOM.span(null, \"1\")");
    testNoError("React.DOM.span(null, \"1\", React.DOM.i())");
    test("var Comp = React.createClass({});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "ReactDOM.$render$(React.$createElement$(" +
      "React.$createClass$({})" +
      "),document.body);");
    test("ReactDOM.findDOMNode(document.body);",
      "ReactDOM.$findDOMNode$(document.body);");
    testError("ReactDOM.findDOMNode([document.body]);", "JSC_TYPE_MISMATCH");
    test("ReactDOM.unmountComponentAtNode(document.body);",
      "ReactDOM.$unmountComponentAtNode$(document.body);");
    testError("ReactDOM.unmountComponentAtNode(\"notanode\");",
      "JSC_TYPE_MISMATCH");
    testError("React.DOM.span(1)", "JSC_TYPE_MISMATCH");
  }

  @Test public void testReactDOMServer() {
    test("var Comp = React.createClass({});" +
      "ReactDOMServer.renderToString(React.createElement(Comp));",
      "ReactDOMServer.$renderToString$(" +
      "React.$createElement$(React.$createClass$({})));");
    testError("ReactDOMServer.renderToString(\"notanelement\");",
      "JSC_TYPE_MISMATCH");
    test("var Comp = React.createClass({});" +
      "ReactDOMServer.renderToStaticMarkup(React.createElement(Comp));",
      "ReactDOMServer.$renderToStaticMarkup$(" +
      "React.$createElement$(React.$createClass$({})));");
    testError("ReactDOMServer.renderToStaticMarkup(\"notanelement\");",
      "JSC_TYPE_MISMATCH");
  }

  /**
   * Tests static methods and properties.
   */
  @Test public void testStatics() {
    test(
      "var Comp = React.createClass({" +
        "statics: {" +
          "aNumber: 123," +
          "aString: \"456\"," +
          "aFunction: function() {return 123}" +
        "}," +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "window.aNumber = Comp.aNumber;" +
      "window.aString = Comp.aString;" +
      "window.aFunctionResult = Comp.aFunction();",
      // Statics without JSDoc are OK
      "var $Comp$$=React.$createClass$({" +
        "$statics$:{" +
        "$aNumber$:123," +
        "$aString$:\"456\"," +
        "$aFunction$:function(){return 123}" +
        "}," +
        "$render$:function(){return React.$createElement$(\"div\")}" +
      "});" +
      "window.$aNumber$=$Comp$$.$aNumber$;" +
      "window.$aString$=$Comp$$.$aString$;" +
      "window.$aFunctionResult$=123;");
    testError(
      "var Comp = React.createClass({" +
        "statics: {" +
          "/** @type {number} */" +
          "aNumber: 123" +
        "}," +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "window.foo = Comp.aNumber.charAt(0);",
      // But if JSDoc is provided, then it is used.
      "JSC_INEXISTENT_PROPERTY");
  }

  @Test public void testGetDefaultPropsThis() {
    test(
      "var Comp = React.createClass({" +
        "statics: {" +
          "CONST: 123" +
        "}," +
        "getDefaultProps: function() {" +
          "return {aProp: this.CONST}" +
        "}," +
        "render: function() {" +
          "return React.createElement(\"div\");" +
        "}" +
      "});",
      // "this" inside of getDefaultProps allows access to statics (i.e. we are
      // not in a component instance).
      "");
  }

  @Test public void testPureRenderMixin() {
    test(
      "var Comp = React.createClass({" +
        "mixins: [React.addons.PureRenderMixin]," +
        "render: function() {" +
          "return React.createElement(\"div\");" +
        "}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      // Should be fine to use React.addons.PureRenderMixin.
      "ReactDOM.$render$(React.$createElement$(React.$createClass$({" +
        "$mixins$:[React.$addons$.$PureRenderMixin$]," +
        "$render$:function(){" +
          "return React.$createElement$(\"div\")" +
        "}" +
      "})),document.body);");
    testError(
      "var Comp = React.createClass({" +
        "mixins: [React.addons.PureRenderMixin]," +
        "shouldComponentUpdate: function(nextProps, nextState) {" +
          "return true;" +
        "}," +
        "render: function() {" +
          "return React.createElement(\"div\");" +
        "}" +
      "});",
      // But there should be a warning if using PureRenderMixin yet
      // shouldComponentUpdate is specified.
      ReactCompilerPass.PURE_RENDER_MIXIN_SHOULD_COMPONENT_UPDATE_OVERRIDE);
  }

  @Test public void testElementTypedef() {
    test(
      "var Comp = React.createClass({" +
        "render: function() {" +
          "return React.createElement(\"div\");" +
        "}" +
      "});\n" +
      "/** @return {CompElement} */\n" +
      "function create() {return React.createElement(Comp);}",
      "");
    test(
      "var ns = {};" +
      "ns.Comp = React.createClass({" +
        "render: function() {" +
          "return React.createElement(\"div\");" +
        "}" +
      "});\n" +
      "/** @return {ns.CompElement} */\n" +
      "function create() {return React.createElement(ns.Comp);}",
      "");
  }

  private static void test(String inputJs, String expectedJs) {
    test(inputJs, expectedJs, null, null, null);
  }

  private static void testError(String inputJs, String expectedErrorName) {
    test(inputJs, "", null, null, DiagnosticType.error(expectedErrorName, ""));
  }

  private static void testError(String inputJs, DiagnosticType expectedError) {
    test(inputJs, "", null, null, expectedError);
  }

  private static void testNoError(String inputJs) {
    test(inputJs, null, null, null, null);
  }

  private static void test(
        String inputJs,
        String expectedJs,
        String reactSourceName,
        ReactCompilerPass.Options passOptions,
        DiagnosticType expectedError) {
    if (reactSourceName == null) {
      reactSourceName = "/src/react.js";
    }
    Compiler compiler = new Compiler(
        new PrintStream(ByteStreams.nullOutputStream())); // Silence logging
    compiler.disableThreads(); // Makes errors easier to track down.
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2018);
    options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5);
    options.setWarningLevel(
        DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.ERROR);
    options.setGeneratePseudoNames(true);
    options.addWarningsGuard(new ReactWarningsGuard());
    // Report warnings as errors to make tests simpler
    options.addWarningsGuard(new StrictWarningsGuard());
    options.setPrintInputDelimiter(true);
    if (passOptions == null) {
      passOptions = new ReactCompilerPass.Options();
      passOptions.propTypesTypeChecking = true;
    }
    options.addCustomPass(
        CustomPassExecutionTime.BEFORE_CHECKS,
        new ReactCompilerPass(compiler, passOptions));
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode(reactSourceName, REACT_SOURCE),
        SourceFile.fromCode("/src/test.js", inputJs)
    );
    List<SourceFile> builtInExterns;
    try {
      // We need the built-in externs so that Object and other built-in types
      // are defined when using NTI.
      builtInExterns = AbstractCommandLineRunner.getBuiltinExterns(
          CompilerOptions.Environment.CUSTOM);
    } catch (IOException err) {
      throw new RuntimeException(err);
    }
    List<SourceFile> externs = new ImmutableList.Builder()
        .addAll(builtInExterns)
        .add(SourceFile.fromCode("externs.js",
            "/** @constructor */ function Element() {};" +
            "/** @constructor */ function Event() {};" +
            "var document;" +
            "document.body;" +
            "var window;"))
        .build();

    ReactCompilerPass.saveLastOutputForTests = true;

    Result result = compiler.compile(externs, inputs, options);
    String lastOutput = "\n\nInput:\n" + inputJs + "\nCompiler pass output:\n" +
        ReactCompilerPass.lastOutputForTests + "\n";
    if (compiler.getRoot() != null) {
      lastOutput += "Final compiler output:\n" + new CodePrinter.Builder(compiler.getRoot())
          .setPrettyPrint(true)
          .setOutputTypes(true)
          .setTypeRegistry(compiler.getTypeIRegistry())
          .build() +
          "\n";
    }
    if (expectedError == null) {
      assertEquals(
          "Unexpected errors: " + Joiner.on(",").join(result.errors) +
              lastOutput,
          0, result.errors.length);
      assertEquals(
          "Unexpected warnings: " + Joiner.on(",").join(result.warnings) +
              lastOutput,
          0, result.warnings.length);
      assertTrue(result.success);
      if (expectedJs != null) {
        String actualJs = compiler.toSource();
        int inputIndex = actualJs.indexOf(ACTUAL_JS_INPUT_MARKER);
        assertNotEquals(-1, inputIndex);
        actualJs = actualJs.substring(
            inputIndex + ACTUAL_JS_INPUT_MARKER.length());
        assertEquals(expectedJs, actualJs);
      }
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
              ", instead found " + Joiner.on(",").join(result.errors) +
              lastOutput,
          foundError);
      assertEquals(
          "Unexpected warnings: " + Joiner.on(",").join(result.warnings) +
              lastOutput,
          0, result.warnings.length);
    }
  }
}
