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
        "render: function() {" +
          "return React.createElement(" +
            "\"div\", null, React.DOM.span(null, \"child\"));" +
          "}" +
      "});" +
      "React.render(React.createElement(Comp), document.body);",
      // createClass and other React methods should get renamed.
      "React.$render$(React.$createElement$(React.$createClass$({" +
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
      "JSC_TYPE_MISMATCH");
    testError(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "var inst = React.render(React.createElement(Comp), document.body);" +
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
      "var inst = React.render(React.createElement(Comp), document.body);" +
      "inst.mixinMethod();" +
      "inst.chainedMixinMethod();",
      // Mixin method invocations should not result in warnings if they're
      // known, either directly or via chained mixins.
      "var $inst$$=React.$render$(React.$createElement$(React.$createClass$({" +
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
      "/** @return {number} */" +
      "Mixin.mixinAbstractMethod;" +
      "var Comp = React.createClass({" +
        "mixins: [Mixin]," +
        "render: function() {" +
          "this.mixinMethod();" +
          "return React.createElement(\"div\");" +
        "}," +
        "mixinAbstractMethod: function() {return 123;}" +
      "});" +
      "React.render(React.createElement(Comp), document.body);",
      // Mixins can support abstract methods via additional properties.
      "React.$render$(React.$createElement$(React.$createClass$({" +
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
      "var inst = React.render(React.createElement(Comp), document.body);" +
      "inst.mixinMethod(\"notanumber\");",
      // Mixin methods should have their parameter types check when invoked from
      // the component.
      "JSC_TYPE_MISMATCH");
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
      "}).$displayName$.charAt(0);");
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
      "React.render(React.createElement(Comp), document.body, 123);",
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
      // Refs can be accessed via quoted strings..
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

  @Test public void testPropTypes() {
    // Basic prop types
    test(
      "var Comp = React.createClass({" +
        "propTypes: {aProp: React.PropTypes.string}," +
        "render: function() {" +
          "return React.createElement(\"div\", null, this.props.aProp);" +
        "}" +
      "});" +
      "React.render(React.createElement(Comp), document.body);",
      "React.$render$(React.$createElement$(React.$createClass$({" +
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

  @Test public void testPropTypesStripping() {
    // propTypes should get stripped if we're using the minimized React build
    // (since they're not checked).
    test(
      "var Comp = React.createClass({" +
        "propTypes: {aProp: React.PropTypes.string}," +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "React.render(React.createElement(Comp), document.body);",
      "React.$render$(React.$createElement$(React.$createClass$({" +
        "$render$:function(){return React.$createElement$(\"div\")}" +
      "})),document.body);",
      "/src/react.min.js",
      null);
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
      "React.render(React.createElement(Comp), document.body);",
      "React.$render$(React.$createElement$(React.$createClass$({" +
        "$render$:function(){" +
          "return React.$createElement$(" +
              "\"div\",null,React.$Children$.$only$(this.$props$.$children$))" +
        "}" +
      "})),document.body);");
  }

  @Test public void testReactDOM() {
    test("React.DOM.span()", "");
    test("React.DOM.span(null)", "");
    test("React.DOM.span(null, \"1\")", "");
    test("React.DOM.span(null, \"1\", React.DOM.i())", "");
    testError("React.DOM.span(1)", "JSC_TYPE_MISMATCH");
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
      "React.render(React.createElement(Comp), document.body);",
      // Should be fine to use React.addons.PureRenderMixin.
      "React.$render$(React.$createElement$(React.$createClass$({" +
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
    test(inputJs, expectedJs, null, null);
  }

  private static void testError(String inputJs, String expectedErrorName) {
    test(inputJs, "", null, DiagnosticType.error(expectedErrorName, ""));
  }

  private static void testError(String inputJs, DiagnosticType expectedError) {
    test(inputJs, "", null, expectedError);
  }

  private static void test(
        String inputJs,
        String expectedJs,
        String reactSourceName,
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
        SourceFile.fromCode(reactSourceName, REACT_SOURCE),
        SourceFile.fromCode("/src/test.js", inputJs)
    );
    List<SourceFile> externs = ImmutableList.of(
        SourceFile.fromCode("externs.js",
          "/** @constructor */ function Element() {};" +
          "var document;" +
          "document.body;" +
          "var window;" +
          "var String;" +
          "/**\n" +
          " * @param {number} index\n" +
          " * @return {string}\n" +
          " * @nosideeffects\n" +
          " */" +
          "String.prototype.charAt = function(index) {};")
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
