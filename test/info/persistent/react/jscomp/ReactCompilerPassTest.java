package info.persistent.react.jscomp;

import info.persistent.jscomp.Debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
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
  // Used to find the test output (and separate it from the injected API
  // aliases source)
  private static final String ACTUAL_JS_INPUT_MARKER = "// Input 1\n";
  // Used to allow test cases to have multiple input files (so that ES6
  // modules can be tested).
  private static String FILE_SEPARATOR = "\n\n/* --file-separator-- */\n\n";

  private static String REACT_SUPPORT_CODE = "var ReactSupport={" +
    "declareMixin(mixin){}," +
    "mixin(comp,...mixins){comp.mixins=mixins}" +
  "};";

  @Test public void testMinimalComponent() {
    test(
      "var Comp = React.createClass({" +
        "render: function() {" +
          "return React.createElement(" +
            "\"div\", null, React.createElement(\"span\", null, \"child\"));" +
          "}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      // createClass and other React methods should not get renamed.
      "ReactDOM.render(React.createElement(React.createClass({" +
        "render:function(){" +
          "return React.createElement(" +
            "\"div\",null,React.createElement(\"span\",null,\"child\"))" +
        "}" +
      "})),document.body);");
  }

  @Test public void testMinimalComponentClass() {
    test(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "return React.createElement(" +
            "\"div\", null, React.createElement(\"span\", null, \"child\"));" +
        "}" +
      "}" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      // React.Component and other React methods should not get renamed.
      "class $Comp$$ extends React.Component{" +
        "render(){" +
          "return React.createElement(\"div\",null,React.createElement(\"span\",null,\"child\"))" +
        "}" +
      "}" +
      "ReactDOM.render(React.createElement($Comp$$),document.body);");

      // ClassExpression using VariableDeclaration
      test(
        "var Comp = class extends React.Component {" +
          "/* @override */" +
          "render() {" +
            "return React.createElement(" +
              "\"div\", null, React.createElement(\"span\", null, \"child\"));" +
          "}" +
        "};" +
        "ReactDOM.render(React.createElement(Comp), document.body);",
        // React.Component and other React methods should not get renamed.
        "ReactDOM.render(" +
          "React.createElement(" +
            "class extends React.Component{" +
              "render(){" +
                "return React.createElement(\"div\",null,React.createElement(\"span\",null,\"child\"))" +
              "}" +
            "})," +
            "document.body);");

      // ClassExpression using AssignmnentExpression
      test(
        "var Comp;" +
        "Comp = class extends React.Component {" +
          "/* @override */" +
          "render() {" +
            "return React.createElement(" +
              "\"div\", null, React.createElement(\"span\", null, \"child\"));" +
          "}" +
        "};" +
        "ReactDOM.render(React.createElement(Comp), document.body);",
        // React.Component and other React methods should not get renamed.
        "ReactDOM.render(" +
          "React.createElement(" +
            "class extends React.Component{" +
              "render(){" +
                "return React.createElement(\"div\",null,React.createElement(\"span\",null,\"child\"))" +
              "}" +
            "})," +
            "document.body);");
  }

  @Test public void testEs6Modules() {
    test(
      "export const Comp = React.createClass({" +
        "render: function() {" +
          "return React.createElement(" +
            "\"div\", null, React.createElement(\"span\", null, \"child\"));" +
          "}" +
      "});" +
      FILE_SEPARATOR +
      // Test that we can use the component and associated types from another
      // module (i.e. that exports are generated for them).
      "import * as file1 from './file1.js';\n" +
      "const /** file1.CompElement */ compElement = React.createElement(file1.Comp);\n" +
      "const /** file1.CompInterface */ compInstance = ReactDOM.render(compElement, document.body);",
      "const $compElement$$module$src$file2$$=React.createElement(React.createClass({" +
        "render:function(){" +
          "return React.createElement(" +
            "\"div\",null,React.createElement(\"span\",null,\"child\"))" +
        "}" +
      "}));" +
      "ReactDOM.render($compElement$$module$src$file2$$,document.body);");
    // Cross-module type checking works for props...
    testError(
      "export const Comp = React.createClass({" +
        "propTypes: {aNumber: React.PropTypes.number.isRequired}," +
        "render: function() {return null;}" +
      "});\n" +
      FILE_SEPARATOR +
      "import * as file1 from './file1.js';\n" +
      "React.createElement(file1.Comp, {aNumber: 'notANumber'});",
      "JSC_TYPE_MISMATCH");
    // Cross-module type checking works for props...
    testError(
      "export const Comp = React.createClass({" +
        "propTypes: {aNumber: React.PropTypes.number.isRequired}," +
        "render: function() {return null;}" +
      "});\n" +
      FILE_SEPARATOR +
      "import {Comp} from './file1.js';\n" +
      "React.createElement(Comp, {aNumber: 'notANumber'});",
      "JSC_TYPE_MISMATCH");
    // ...and methods.
    testError(
      "export const Comp = React.createClass({" +
        "render: function() {return null;},\n" +
        "/** @param {number} a */" +
        "method: function(a) {window.foo = a;}" +
      "});\n" +
      FILE_SEPARATOR +
      "import * as file1 from './file1.js';\n" +
      "const inst = ReactDOM.render(" +
          "React.createElement(file1.Comp), document.body);\n" +
      "inst.method('notanumber');",
      "JSC_TYPE_MISMATCH");
  }

  @Test public void testEs6ModulesClass() {
    test(
      "export class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "return React.createElement(" +
            "\"div\", null, React.createElement(\"span\", null, \"child\"));" +
        "}" +
      "}" +
      FILE_SEPARATOR +
      // Test that we can use the component and associated types from another
      // module (i.e. that exports are generated for them).
      "import * as file1 from './file1.js';\n" +
      "const /** file1.CompElement */ compElement = React.createElement(file1.Comp);\n" +
      "const /** file1.Comp */ compInstance = ReactDOM.render(compElement, document.body);",
      "class $Comp$$module$src$file1$$ extends React.Component{" +
        "render(){" +
          "return React.createElement(\"div\",null,React.createElement(\"span\",null,\"child\"))" +
        "}" +
      "};" +
      "const $compElement$$module$src$file2$$=React.createElement($Comp$$module$src$file1$$);" +
      "ReactDOM.render($compElement$$module$src$file2$$,document.body);");
    // Cross-module type checking works for props...
    testError(
      "export class Comp extends React.Component {\n" +
        "/* @override */" +
        "render() {return null;}\n" +
      "}\n" +
      "Comp.propTypes = {aNumber: React.PropTypes.number.isRequired};" +
      FILE_SEPARATOR +
      "import * as file1 from './file1.js';\n" +
      "React.createElement(file1.Comp, {aNumber: 'notANumber'});",
      "JSC_TYPE_MISMATCH");
    testError(
      "export class Comp extends React.Component {\n" +
        "/* @override */" +
        "render() {return null;}\n" +
      "}\n" +
      "Comp.propTypes = {aNumber: React.PropTypes.number.isRequired};" +
      FILE_SEPARATOR +
      "import {Comp} from './file1.js';\n" +
      "React.createElement(Comp, {aNumber: 'notANumber'});",
      "JSC_TYPE_MISMATCH");
    // ...and methods.
    testError(
      "export class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return null;}\n" +
        "/** @param {number} a */" +
        "method(a) {window.foo = a;}" +
      "}\n" +
      FILE_SEPARATOR +
      "import * as file1 from './file1.js';\n" +
      "const inst = ReactDOM.render(" +
          "React.createElement(file1.Comp), document.body);\n" +
      "inst.method('notanumber');",
      "JSC_TYPE_MISMATCH");
  }

  @Test public void testEs6ModulesScoping() {
    // Comp being defined as a local variable in the second file should not
    // be confused with the Comp from the first file.
    testNoError(
      "export const Comp = React.createClass({" +
        "propTypes: {children: React.PropTypes.element.isRequired}," +
        "render: function() {return null;}" +
      "});\n" +
      FILE_SEPARATOR +
      "import * as file1 from './file1.js';\n" +
      "const AnotherComp1 = React.createClass({render() {return null}});\n" +
      "const AnotherComp2 = React.createClass({render() {return null}});\n" +
      "const Comp = Math.random() < 0.5 ? AnotherComp1 : AnotherComp2;" +
      "React.createElement(Comp, {});");
    // But Comp  can be used as a local variable (and is checked correctly) even
    // when it's exported.
    testError(
      "export const Comp = React.createClass({" +
        "propTypes: {aNumber: React.PropTypes.number.isRequired}," +
        "render: function() {return null;}" +
      "});\n" +
      "React.createElement(Comp, {aNumber: 'notANumber'});",
      "JSC_TYPE_MISMATCH");
  }

  @Test public void testEs6ModulesScopingClass() {
    // Comp being defined as a local variable in the second file should not
    // be confused with the Comp from the first file.
    testNoError(
      "export class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return null;}" +
      "}\n" +
      "Comp.propTypes = {children: React.PropTypes.element.isRequired};" +
      FILE_SEPARATOR +
      "import * as file1 from './file1.js';\n" +
      "const AnotherComp1 = React.createClass({render() {return null}});\n" +
      "const AnotherComp2 = React.createClass({render() {return null}});\n" +
      "const Comp = Math.random() < 0.5 ? AnotherComp1 : AnotherComp2;" +
      "React.createElement(Comp, {});");
    // But Comp  can be used as a local variable (and is checked correctly) even
    // when it's exported.
    testError(
      "export class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return null;}" +
      "}\n" +
      "Comp.propTypes = {aNumber: React.PropTypes.number.isRequired};" +
      "React.createElement(Comp, {aNumber: 'notANumber'});",
      "JSC_TYPE_MISMATCH");
  }

  @Test public void testEs6ModulesMixins() {
    // Mixin references across modules work
    testNoError(
      "export const Mixin = React.createMixin({" +
        "mixinMethod: function() {window.foo = 123}" +
      "});\n" +
      FILE_SEPARATOR +
      "import * as file1 from './file1.js';\n" +
      "export const Comp = React.createClass({" +
        "mixins: [file1.Mixin]," +
        "render: function() {" +
          "this.mixinMethod();" +
          "return React.createElement(\"div\");" +
        "}" +
      "});");
    // Mixins can be imported directly
    testNoError(
      "export const Mixin = React.createMixin({" +
        "mixinMethod: function() {window.foo = 123}" +
      "});\n" +
      FILE_SEPARATOR +
      "import {Mixin} from './file1.js';\n" +
      "export const Comp = React.createClass({" +
        "mixins: [Mixin]," +
        "render: function() {" +
          "this.mixinMethod();" +
          "return React.createElement(\"div\");" +
        "}" +
      "});");
    // Or under a different name
    testNoError(
      "export const Mixin = React.createMixin({" +
        "mixinMethod: function() {window.foo = 123}" +
      "});\n" +
      FILE_SEPARATOR +
      "import {Mixin as m} from './file1.js';\n" +
      "export const Comp = React.createClass({" +
        "mixins: [m]," +
        "render: function() {" +
          "this.mixinMethod();" +
          "return React.createElement(\"div\");" +
        "}" +
      "});");
    // Mixin references can be chained too.
    testNoError(
      "export const Mixin1 = React.createMixin({" +
        "mixinMethod1: function() {window.foo = 123}" +
      "});\n" +
      FILE_SEPARATOR +
      "import * as file1 from './file1.js';\n" +
      "export const Mixin2 = React.createMixin({" +
        "mixins: [file1.Mixin1]," +
        "mixinMethod2: function() {window.foo = 123}" +
      "});\n" +
      FILE_SEPARATOR +
      "import * as file2 from './file2.js';\n" +
      "export const Comp = React.createClass({" +
        "mixins: [file2.Mixin2]," +
        "render: function() {" +
          "this.mixinMethod1();" +
          "this.mixinMethod2();" +
          "return React.createElement(\"div\");" +
        "}" +
      "});");
    // propTypes from imported mixins are handled correctly
    testNoError(
        "export const Mixin = React.createMixin({" +
          "propTypes: {" +
            "mixinFuncProp: React.PropTypes.func.isRequired" +
          "}" +
        "});\n" +
        FILE_SEPARATOR +
        "import * as file1 from './file1.js';\n" +
        "var Comp = React.createClass({" +
          "mixins: [file1.Mixin],\n" +
          "render: function() {return this.props.mixinFuncProp();}" +
        "});\n");
    // Including those that reference types from the mixin's file
    testNoError(
        "export const Obj = class {};\n" +
        "export const Mixin = React.createMixin({" +
          "propTypes: {" +
            "mixinProp: React.PropTypes.instanceOf(Obj).isRequired" +
          "}" +
        "});\n" +
        FILE_SEPARATOR +
        "import * as file1 from './file1.js';\n" +
        "var Comp = React.createClass({" +
          "mixins: [file1.Mixin],\n" +
          "render: function() {return null;}" +
        "});\n");
  }

  @Test public void testEs6ModulesMixinsClass() {
    // Mixin references across modules work
    testNoError(
        REACT_SUPPORT_CODE +
        "export class Mixin extends React.Component {" +
          "mixinMethod() {window.foo = 123}" +
        "}" +
        "ReactSupport.declareMixin(Mixin);" +
        FILE_SEPARATOR +
        REACT_SUPPORT_CODE +
        "import * as file1 from './file1.js';\n" +
        "export class Comp extends React.Component {" +
          "/* @override */" +
          "render() {" +
            "this.mixinMethod();" +
            "return React.createElement(\"div\");" +
          "}" +
        "}" +
        "ReactSupport.mixin(Comp, file1.Mixin);");
    // Mixins can be imported directly
    testNoError(
        REACT_SUPPORT_CODE +
        "export class Mixin extends React.Component {" +
          "mixinMethod() {window.foo = 123}" +
        "}" +
        "ReactSupport.declareMixin(Mixin);" +
        FILE_SEPARATOR +
        REACT_SUPPORT_CODE +
        "import {Mixin} from './file1.js';" +
        "export class Comp extends React.Component {" +
          "/* @override */" +
          "render() {" +
            "this.mixinMethod();" +
            "return React.createElement(\"div\");" +
          "}" +
        "}" +
        "ReactSupport.mixin(Comp, Mixin);");
    // Make sure we can use the mixin twice without adding an import twice
    testNoError(
        REACT_SUPPORT_CODE +
        "export class Mixin extends React.Component {" +
          "mixinMethod() {window.foo = 123}" +
        "}" +
        "ReactSupport.declareMixin(Mixin);" +
        FILE_SEPARATOR +
        REACT_SUPPORT_CODE +
        "import {Mixin} from './file1.js';" +
        "export class Comp extends React.Component {" +
          "/* @override */" +
          "render() {" +
            "this.mixinMethod();" +
            "return React.createElement(\"div\");" +
          "}" +
        "}" +
        "ReactSupport.mixin(Comp, Mixin);" +
        "export class Comp2 extends React.Component {" +
          "/* @override */" +
          "render() {" +
            "this.mixinMethod();" +
            "return null;" +
          "}" +
        "}" +
        "ReactSupport.mixin(Comp2, Mixin);");

    // Or under a different name
    testNoError(
        REACT_SUPPORT_CODE +
        "export class Mixin extends React.Component {" +
          "mixinMethod() {window.foo = 123}" +
        "}" +
        "ReactSupport.declareMixin(Mixin);" +
        FILE_SEPARATOR +
        REACT_SUPPORT_CODE +
        "import {Mixin as m} from './file1.js';\n" +
        "export class Comp extends React.Component {" +
          "/* @override */" +
          "render() {" +
            "this.mixinMethod();" +
            "return React.createElement(\"div\");" +
          "}" +
        "}" +
        "ReactSupport.mixin(Comp, m);");
    // Mixin references can be chained too.
    testNoError(
        REACT_SUPPORT_CODE +
        "export class Mixin1 extends React.Component {" +
          "mixinMethod1() {window.foo = 123}" +
        "}" +
        "ReactSupport.declareMixin(Mixin1);" +
        FILE_SEPARATOR +
        REACT_SUPPORT_CODE +
        "import * as file1 from './file1.js';\n" +
        "export class Mixin2 extends React.Component {" +
          "mixinMethod2() {window.foo = 123}" +
        "}" +
        "ReactSupport.declareMixin(Mixin2);" +
        "ReactSupport.mixin(Mixin2, file1.Mixin1);" +
        FILE_SEPARATOR +
        REACT_SUPPORT_CODE +
        "import * as file2 from './file2.js';\n" +
        "export class Comp extends React.Component {" +
          "/* @override */" +
          "render() {" +
            "this.mixinMethod1();" +
            "this.mixinMethod2();" +
            "return React.createElement(\"div\");" +
          "}" +
        "}" +
        "ReactSupport.mixin(Comp, file2.Mixin2);");
    // propTypes from imported mixins are handled correctly
    testNoError(
        REACT_SUPPORT_CODE +
        "export class Mixin extends React.Component {}" +
        "ReactSupport.declareMixin(Mixin);" +
        "Mixin.propTypes = {" +
          "mixinFuncProp: React.PropTypes.func.isRequired" +
        "};" +
        FILE_SEPARATOR +
        REACT_SUPPORT_CODE +
        "import * as file1 from './file1.js';" +
        "class Comp extends React.Component {" +
          "/* @override */" +  
          "render() {return this.props.mixinFuncProp();}" +
        "}" +
        "ReactSupport.mixin(Comp, file1.Mixin);");
    // Including those that reference types from the mixin's file
    testNoError(
        REACT_SUPPORT_CODE +
        "export const Obj = class {};\n" +
        "export class Mixin extends React.Component {}" +
        "ReactSupport.declareMixin(Mixin);" +
        "Mixin.propTypes = {" +
          "mixinProp: React.PropTypes.instanceOf(Obj).isRequired" +
        "};" +
        FILE_SEPARATOR +
        REACT_SUPPORT_CODE +
        "import * as file1 from './file1.js';\n" +
        "class Comp extends React.Component {" +
          "/* @override */" +
          "render() {return null;}" +
        "}" +
        "ReactSupport.mixin(Comp, file1.Mixin);");
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
      "ReactDOM.render(React.createElement(React.createClass({" +
        "render:function(){return React.createElement(\"div\")}," +
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
      "ReactDOM.render(React.createElement(React.createClass({" +
        "render:function(){return React.createElement(\"div\")}," +
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

  @Test public void testInstanceMethodsClass() {
    test(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return React.createElement(\"div\");}" +
        "method() {window.foo = 123;}" +
      "}" +
      "var inst = ReactDOM.render(React.createElement(Comp), document.body);" +
      "inst.method();",
      // Method invocations should not result in warnings if they're known.
      "class $Comp$$ extends React.Component{" +
        "render(){return React.createElement(\"div\")}" +
      "}" +
      "ReactDOM.render(React.createElement($Comp$$),document.body);" +
      "window.$foo$=123;");
    test(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return React.createElement(\"div\");}" +
        "/** @private */" +
        "privateMethod1_(a) {window.foo = 123 + a;}" +
        "/** @private */" +
        "privateMethod2_() {this.privateMethod1_(1);}" +
      "}" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      // Private methods should be invokable.
      "class $Comp$$ extends React.Component{" +
        "render(){return React.createElement(\"div\")}"+
      "}" +
      "ReactDOM.render(React.createElement($Comp$$),document.body);");
    testError(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return React.createElement(\"div\");}" +
        "/** @param {number} a */" +
        "method(a) {window.foo = a;}" +
      "}" +
      "var inst = ReactDOM.render(React.createElement(Comp), document.body);" +
      "inst.method('notanumber');",
      // Their arguments should be validated.
      "JSC_TYPE_MISMATCH");
    testError(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return React.createElement(\"div\");}" +
      "}" +
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
      "var $inst$$=ReactDOM.render(React.createElement(React.createClass({" +
        "mixins:[{" +
          "mixins:[{" +
            "$chainedMixinMethod$:function(){window.$foo$=456}" +
          "}]," +
          "$mixinMethod$:function(){window.$foo$=123}" +
        "}]," +
        "render:function(){" +
          "this.$mixinMethod$();" +
          "this.$chainedMixinMethod$();" +
          "return React.createElement(\"div\")" +
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
      "ReactDOM.render(React.createElement(React.createClass({" +
        "mixins:[{" +
          "$mixinMethod$:function(){window.$foo$=123}" +
        "}]," +
        "render:function(){" +
          "this.$mixinMethod$();" +
          "return React.createElement(\"div\")" +
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

  @Test public void testMixinsClass() {
    testError(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {}" +
      "ReactSupport.declareMixin(42);",
      "DECLARE_MIXIN_PARAM_NOT_VALID");
    testError(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {}" +
      "ReactSupport.declareMixin(x, y);",
      "DECLARE_MIXIN_UNEXPECTED_NUMBER_OF_PARAMS");
    testError(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {}" +
      "ReactSupport.declareMixin();",
      "DECLARE_MIXIN_UNEXPECTED_NUMBER_OF_PARAMS");
    testError(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {}" +
      "ReactSupport.declareMixin(Unknown);",
      "REACT_MIXIN_UNKNOWN");
    testError(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {}" +
      "ReactSupport.mixin();",
      "MIXIN_UNEXPECTED_NUMBER_OF_PARAMS");
    testError(
      REACT_SUPPORT_CODE +
      "class Comp extends React.Component {}" +
      "ReactSupport.mixin(Comp);",
      "MIXIN_UNEXPECTED_NUMBER_OF_PARAMS");

    test(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {}" +
      "ReactSupport.declareMixin(Mixin);" +
      "class Comp extends React.Component {}" +
      "ReactSupport.mixin(Comp, Mixin);",
      "");
    test(
      REACT_SUPPORT_CODE +
      "class MixinA extends React.Component {}" +
      "ReactSupport.declareMixin(MixinA);" +
      "class MixinB extends React.Component {}" +
      "ReactSupport.declareMixin(MixinB);" +
      "class Comp extends React.Component {}" +
      "ReactSupport.mixin(Comp, MixinA, MixinB);",
      "");

    testError(
      REACT_SUPPORT_CODE +
      "class Comp extends React.Component {}" +
      "class NotAMixin extends React.Component {}" +
      "ReactSupport.mixin(Comp, NotAMixin);",
      "MIXIN_PARAM_IS_NOT_MIXIN");

    // It is a bit surprising that Closure Compiler can inline this. It probably
    // works because there is only one implementation of the method.
    test(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {" +
        "/** @return {*} */" +
        "mixinMethod() {window.foo=this.mixinAbstractMethod()}" +
      "}" +
      "ReactSupport.declareMixin(Mixin);" +
      "/** @return {number} @protected */" +
      "Mixin.mixinAbstractMethod;" +
      "class Comp extends React.Component {" +
        "/* @override */" +
        "mixinAbstractMethod() {" +
          "return 42;" +
        "}" +
      "}" +
      "ReactSupport.mixin(Comp, Mixin);"  +
      "var inst = ReactDOM.render(React.createElement(Comp), document.body);" +
      "inst.mixinMethod();",
      "class $Mixin$$ extends React.Component{}" +
      "class $Comp$$ extends React.Component{}" +
      "$Comp$$.mixins=[$Mixin$$];" +
      "ReactDOM.render(React.createElement($Comp$$),document.body);" +
      "window.$foo$=42;");

    test(
      REACT_SUPPORT_CODE +
      "class ChainedMixin extends React.Component {" +
        "chainedMixinMethod() {window.foo = 456}" +
      "}" +
      "ReactSupport.declareMixin(ChainedMixin);" +
      "class Mixin extends React.Component {" +
        "mixinMethod() {window.foo = 123}" +
      "}" +
      "ReactSupport.declareMixin(Mixin);" +
      "ReactSupport.mixin(Mixin, ChainedMixin);"  +
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "this.mixinMethod();" +
          "this.chainedMixinMethod();" +
          "return React.createElement(\"div\");" +
        "}" +
      "}" +
      "ReactSupport.mixin(Comp, Mixin);"  +
      "var inst = ReactDOM.render(React.createElement(Comp), document.body);" +
      "inst.mixinMethod();" +
      "inst.chainedMixinMethod();",
      // Mixin method invocations should not result in warnings if they're
      // known, either directly or via chained mixins.
      "class $ChainedMixin$$ extends React.Component{}" +
      "class $Mixin$$ extends React.Component{}" +
      "$Mixin$$.mixins=[$ChainedMixin$$];" +
      "class $Comp$$ extends React.Component{" +
        "render(){" +
          "window.$foo$=456;" +
          "return React.createElement(\"div\")" +
        "}" +
      "}" +
      "$Comp$$.mixins=[$Mixin$$];" +
      "ReactDOM.render(React.createElement($Comp$$),document.body);" +
      "window.$foo$=123;" +
      "window.$foo$=456;");

    test(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {" +
        "mixinMethod() {window.foo=this.mixinAbstractMethod()}" +
      "}" +
      "ReactSupport.declareMixin(Mixin);" +
      "/** @return {number} @protected */" +
      "Mixin.mixinAbstractMethod;" +
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "this.mixinMethod();" +
          "return React.createElement(\"div\");" +
        "}" +
        "/* @override */" +
        "mixinAbstractMethod() {return 123;}" +
      "}" +
      "ReactSupport.mixin(Comp, Mixin);" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      // Mixins can support abstract methods via additional properties.
      "class $Mixin$$ extends React.Component{}" +
      "class $Comp$$ extends React.Component{" +
        "render(){" +
          "window.$foo$=123;" +
          "return React.createElement(\"div\")" +
        "}" +
      "}" +
      "$Comp$$.mixins=[$Mixin$$];" +
      "ReactDOM.render(React.createElement($Comp$$),document.body);");

    testError(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {" +
        "/** @param {number} a */" +
        "mixinMethod(a) {window.foo = 123}" +
      "}" +
      "ReactSupport.declareMixin(Mixin);" +
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "this.mixinMethod(\"notanumber\");" +
          "return React.createElement(\"div\");" +
        "}" +
      "}" +
      "ReactSupport.mixin(Comp, Mixin);" +
      "var inst = ReactDOM.render(React.createElement(Comp), document.body);" +
      "inst.mixinMethod(\"notanumber\");",
      // Mixin methods should have their parameter types check when invoked from
      // the component.
      "JSC_TYPE_MISMATCH");
    testError(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {" +
        "/** @private */" +
        "privateMixinMethod_() {}" +
      "}" +
      "ReactSupport.declareMixin(Mixin);" +
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "this.privateMixinMethod_();" +
          "return null;" +
        "}" +
      "}" +
      "ReactSupport.mixin(Comp, Mixin);",
      // Private mixin methods should not be exposed to the component.
      "JSC_INEXISTENT_PROPERTY");
    testError(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {" +
        "mixinMethod() {" +
          "window.foo = this.mixinAbstractMethod().noSuchMethod()" +
        "}" +
      "}" +
      "ReactSupport.declareMixin(Mixin);" +
      "/** @return {number} */" +
      "Mixin.mixinAbstractMethod;" +
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "this.mixinMethod();" +
          "return React.createElement(\"div\");" +
        "}" +
        "/* @override */" +
        "mixinAbstractMethod() {return 123;}" +
      "}" +
      "ReactSupport.mixin(Comp, Mixin);",
      // Abstract methods have their types checked too, on both the mixin
      // side...
      "JSC_INEXISTENT_PROPERTY");
    testError(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {" +
        "mixinMethod() {window.foo = this.mixinAbstractMethod()}" +
      "}" +
      "ReactSupport.declareMixin(Mixin);" +
      "/** @return {number} */" +
      "Mixin.mixinAbstractMethod;" +
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "this.mixinMethod();" +
          "return React.createElement(\"div\");" +
        "}" +
        "/* @override */" +
        "mixinAbstractMethod() {return \"notanumber\";}" +
      "}" +
      "ReactSupport.mixin(Comp, Mixin);",
      // ...and the component side
      "JSC_TYPE_MISMATCH");

    test(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {}" +
      "ReactSupport.declareMixin(Mixin);" +
      "/** @param {number} param1 */" +
      "Mixin.mixinAbstractMethod;" +
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "return React.createElement(\"div\");" +
        "}" +
        "/* @override */" +
        "mixinAbstractMethod() {}" +
      "}" +
      "ReactSupport.mixin(Comp, Mixin);",
      // But implementations should be OK if they omit parameters...
      "");
    test(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {}" +
      "ReactSupport.declareMixin(Mixin);" +
      "/** @param {number} param1 */" +
      "Mixin.mixinAbstractMethod;" +
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "return React.createElement(\"div\");" +
        "}" +
        "/* @override */" +
        "mixinAbstractMethod(renamedParam1) {}" +
      "}" +
      "ReactSupport.mixin(Comp, Mixin);",
      //  ...or rename them.
      "");
    test(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {}" +
      "ReactSupport.declareMixin(Mixin);" +
      "/**\n" +
      " * @param {string} param1\n" +
      " * @return {string}\n" +
      " */" +
      "Mixin.mixinAbstractMethod;" +
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "return React.createElement(\"div\");" +
        "}" +
        "/* @override */" +
        "mixinAbstractMethod(param1) {return param1}" +
      "}" +
      "ReactSupport.mixin(Comp, Mixin);",
      // Template types are copied over.
      "");
  }

  @Test public void testMixinOnExportClass() {
    // The real error here was that SymbolTable uses a HashMap but we need an
    // Map that iterates in the insertion order.
    testNoError(
      REACT_SUPPORT_CODE +
      "class TestMixin extends React.Component {" +
        "/** @return {string} */" +
        "d1() {" +
          "return \"M12\";" +
        "}" +
      "}" +
      "ReactSupport.declareMixin(TestMixin);" +
      "export class AddCommentIcon extends React.Component {" +
        "/** @override */" +
        "render() {" +
          "this.d1();" +
          "return null;" +
        "}" +
      "}" +
      "ReactSupport.mixin(AddCommentIcon, TestMixin);");
  }

  @Test public void testMixinOptionalAbstractMethodClass() {
    testNoError(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {" +
        "/** @return {number} */" +
        "method() {" +
          "if (this.optionalAbstract) {" +
            "return this.optionalAbstract();" +
          "}"+
          "return 42;" +
        "}" +
      "}" +
      "ReactSupport.declareMixin(Mixin);" +
      "/** @return {number} */" +
      "Mixin.optionalAbstract;" +
      "class Comp extends React.Component {" +
        "/** @override */" +
        "render() {" +
          "this.method();" +
          "return null;" +
        "}" +
      "}" +
      "ReactSupport.mixin(Comp, Mixin);");
    testNoError(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {" +
        "/** @return {number} */" +
        "method() {" +
          "if (this.optionalAbstract) {" +
            "return this.optionalAbstract();" +
          "}"+
          "return 42;" +
        "}" +
      "}" +
      "ReactSupport.declareMixin(Mixin);" +
      "/** @return {number} */" +
      "Mixin.optionalAbstract;" +
      "class Comp extends React.Component {" +
        "/** @override */" +
        "render() {" +
          "this.method();" +
          "return null;" +
        "}" +
        "/* @override */" +
        "optionalAbstract() {" +
          "return 42;" +
        "}" +
      "}" +
      "ReactSupport.mixin(Comp, Mixin);");
    testNoError(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {" +
        "/** @return {number} */" +
        "method() {" +
          "if (this.optionalAbstract) {" +
            "this.optionalAbstract(42);" +
          "}"+
          "return 42;" +
        "}" +
      "}" +
      "ReactSupport.declareMixin(Mixin);" +
      "/** @param {number} x */" +
      "Mixin.optionalAbstract;" +
      "class Comp extends React.Component {" +
        "/** @override */" +
        "render() {" +
          "this.method();" +
          "return null;" +
        "}" +
        "/* @override */" +
        "optionalAbstract(x) {" +
          "window.x = x;" +
        "}" +
      "}" +
      "ReactSupport.mixin(Comp, Mixin);");
  }

  @Test public void testMixinImplementsClass() {
    test(
        REACT_SUPPORT_CODE +
        "/** @interface */" +
        "class I {" +
          "/**\n" +
          " *@param {number} x\n" +
          " *@return {string} x\n" +
          " */\n" +
          "m(x) {}" +
        "}" +
        "/** @implements {I} */" +
        "class Mixin extends React.Component {" +
          "/** @override */" +
          "m(x) { return \"x\"; }" +
        "}" +
        "ReactSupport.declareMixin(Mixin);" +
        "class Comp extends React.Component {}" +
        "ReactSupport.mixin(Comp, Mixin);",
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

  @Test public void testMixinsParameters() {
    testNoError(
      "const TestMixin = React.createMixin({" +
        "/**" +
        " * @param {string} p1\n" +
        " * @param {number} p2\n" +
        " * @return {string}" +
        " */" +
        "method(p1, p2) {" +
          "return p1 + p2;" +
        "}" +
      "});" +
      "const AddCommentIcon = React.createClass({" +
        "mixins: [TestMixin]," +
        "/** @override */" +
        "render() {" +
          "this.method(\"a\", 1);" +
          "return null;" +
        "}" +
      "})");
  }

  @Test public void testMixinsParametersClass() {
    testNoError(
      REACT_SUPPORT_CODE +
      "class TestMixin extends React.Component {" +
        "/**" +
        " * @param {string} p1\n" +
        " * @param {number} p2\n" +
        " * @return {string}" +
        " */" +
        "method(p1, p2) {" +
          "return p1 + p2;" +
        "}" +
      "}" +
      "ReactSupport.declareMixin(TestMixin);" +
      "class AddCommentIcon extends React.Component {" +
        "/** @override */" +
        "render() {" +
          "this.method(\"a\", 1);" +
          "return null;" +
        "}" +
      "}" +
      "ReactSupport.mixin(AddCommentIcon, TestMixin);");
  }

  @Test public void testMixinsRepeatedMethodsClass() {
    test(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {" +
        "/* @override */" +
        "componentDidMount() {}" +
      "}" +
      "ReactSupport.declareMixin(Mixin);" +
      "class Comp extends React.Component {" +
        "/* @override */" +
        "componentDidMount() {}" +
        "/* @override */" +
        "render() {" +
          "return React.createElement(\"div\");" +
        "}" +
      "}" +
      "ReactSupport.mixin(Comp, Mixin);",
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
      "ReactDOM.render(React.createElement(React.createClass({" +
        "render:function(){return React.createElement(\"div\")}" +
      "})),document.body);");
  }

  @Test public void testNamespacedComponentClass() {
    test(
      "var ns = {};ns.Comp = class extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "return React.createElement(\"div\");" +
        "}" +
      "};" +
      "ReactDOM.render(React.createElement(ns.Comp), document.body);",
      "ReactDOM.render(React.createElement(" +
        "class extends React.Component{" +
          "render(){" +
            "return React.createElement(\"div\")" +
          "}" +
        "})," +
        "document.body);");
  }

  @Test public void testUnusedComponent() {
    test(
      // Unused components should not appear in the output.
      "var Unused = React.createClass({" +
        "render: function() {return React.createElement(\"div\", null);}" +
      "});",
      "");
  }

  @Test public void testUnusedComponentClass() {
    test(
      // Unused components should not appear in the output.
      "class Unused extends React.Component {" +
        "/* @override */" +
        "render() {return React.createElement(\"div\", null);}" +
      "}",
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
      "ReactDOM.render(React.createElement(React.createClass({" +
        "render:function(){return React.createElement(\"div\")}," +
        "$method$:function(){this.setState({$foo$:123})}" +
      "})),document.body);");
  }

  @Test public void testThisUsageClass() {
    test(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return React.createElement(\"div\");}" +
        "method() {this.setState({foo: 123});}" +
      "}" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      // Use of "this" should not cause any warnings.
      "class $Comp$$ extends React.Component{" +
        "render(){return React.createElement(\"div\")}" +
      "}" +
      "ReactDOM.render(React.createElement($Comp$$),document.body);");
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
        "window.type=React.createElement(\"div\").type.charAt(0);");
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
      "window.type=React.createElement(React.createClass({" +
        "render:function(){return React.createElement(\"div\")}" +
      "})).type;");
    // ...unlike other properties.
    testError(
      "var Comp = React.createClass({" +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "window.foo = React.createElement(Comp).notAnElementProperty;",
      "JSC_INEXISTENT_PROPERTY");
  }

  @Test public void testCreateElementCastingClass() {
    // Tests that element.type is not a string...
    testError(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return React.createElement(\"div\");}" +
      "}" +
      "React.createElement(Comp).type.charAt(0)",
      "JSC_INEXISTENT_PROPERTY");
    // ...but is present...
    test(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return React.createElement(\"div\");}" +
      "}" +
      "window.type = React.createElement(Comp).type;",
      "class $Comp$$ extends React.Component{" +
        "render(){return React.createElement(\"div\")}" +
        "}" +
        "window.type=React.createElement($Comp$$).type;");
    // ...unlike other properties.
    testError(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return React.createElement(\"div\");}" +
      "}" +
      "window.foo = React.createElement(Comp).notAnElementProperty;",
      "JSC_INEXISTENT_PROPERTY");
  }

  /**
   * Tests validation done by the types declared in the types.js file. Not
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
      "window.$foo$=React.createClass({" +
        "render:function(){return React.createElement(\"div\")}" +
      "}).displayName.charAt(0);");

    // Stopped working in v20190513
    // testError(
    //   "var Comp = React.createClass({" +
    //     "render: function() {return React.createElement(\"div\");}" +
    //   "});" +
    //   "window.foo = Comp.displayName.notAStringMethod();",
    //   "JSC_POSSIBLE_INEXISTENT_PROPERTY");
    // testError(
    //   "var Comp = React.createClass({" +
    //     "render: function() {return React.createElement(\"div\");}" +
    //   "});" +
    //   "window.foo = Comp.nonExistentProperty;",
    //   "JSC_POSSIBLE_INEXISTENT_PROPERTY");

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

  @Test public void testTypeValidationClass() {
    testError(
      "class Comp extends React.Component {}" +
      "Comp.prototype.render = \"notafunction\";",
      "JSC_TYPE_MISMATCH");
    testError(
      "class Comp extends React.Component {" +
        "constructor(props) {" +
          "super(props);" +
          "this.render = \"notafunction\";" +
        "}" +
      "}",
      "JSC_TYPE_MISMATCH");

    // displayName is not a property on Comp but not sure why this is not caught.
    // testError(
    //   "class Comp extends React.Component {" +
    //     "render() {return null;}" +
    //   "}" +
    //   "window.foo = Comp.displayName.charAt(0);",
    //   "JSC_POSSIBLE_INEXISTENT_PROPERTY");

    testError(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return React.createElement(\"div\");}" +
      "}" +
      "ReactDOM.render(React.createElement(Comp), document.body, 123);",
      "JSC_TYPE_MISMATCH");
    testError(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return React.createElement(\"div\");}" +
        "/* @override */" +
        "shouldComponentUpdate(nextProps, nextState) {return 123;}" +
      "}",
      // Overrides/implemementations of built-in methods should conform to the
      // type annotations added in types.js, even if they're not explicitly
      // present in the spec.
      "JSC_TYPE_MISMATCH");
    test(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return React.createElement(\"div\");}" +
        "/* @override */" +
        "shouldComponentUpdate() {return false;}" +
      "}",
      // But implementations should be OK if they omit parameters...
      "");
    test(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return React.createElement(\"div\");}" +
        "/* @override */" +
        "shouldComponentUpdate(param1, param2) {return false;}" +
      "}",
      // ...or rename them.
      "");
    testError(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return 123;}" +
      "}",
      "JSC_TYPE_MISMATCH");
    testError(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {" +
        "/* @override */" +
        "shouldComponentUpdate(nextProps, nextState) {return 123;}" +
      "}" +
      "ReactSupport.declareMixin(Mixin);",
      // Same for mixins
      "JSC_TYPE_MISMATCH");
    testError(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "this.isMounted().indexOf(\"true\");" +
          "return React.createElement(\"div\");" +
        "}" +
      "}",
      // Same for invocations of built-in component methods.
      "JSC_INEXISTENT_PROPERTY");
    test(
      "class Comp extends React.Component {" +
        "refAccess() {return this.refs[\"foo\"];}" +
      "}",
      // Refs can be accessed via quoted strings.
      "");
    testError(
      "class Comp extends React.Component {" +
        "refAccess() {return this.refs.foo;}" +
      "}",
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

  @Test public void testMethodJsDocClass() {
    testError(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return React.createElement(\"div\");}" +
        "/** @param {number} numberParam */" +
        "someMethod(numberParam) {numberParam.notAMethod();}" +
      "}",
      "JSC_INEXISTENT_PROPERTY");
    testError(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return React.createElement(" +
            "\"div\", null, this.someMethod(\"notanumber\"));}" +
        "/** @param {number} numberParam */" +
        "someMethod(numberParam) {return numberParam + 1;}" +
      "}",
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

  @Test public void testMethodDefaultParametersClass() {
    test(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return React.createElement(\"div\");}" +
        "/** @param {number=} numberParam @return {number}*/" +
        "someMethod(numberParam = 1) {return numberParam * 2;}" +
      "}",
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
      "ReactDOM.render(React.createElement(React.createClass({" +
        "$interfaceMethod$:function(){" +
            "return 1" +
        "}," +
        "render:function(){" +
          "return React.createElement(\"div\")" +
        "}" +
      "})),document.body);");
    // We can't test that missing methods cause compiler warnings since we're
    // declaring CompInterface as extending AnInterface, thus the methods
    // assumed to be there.
  }

  @Test public void testInterfacesClass() {
    test(
      "/** @interface */ function AnInterface() {}\n" +
      "/** @return {number} */\n" +
      "AnInterface.prototype.interfaceMethod = function() {};\n" +
      "/** @implements {AnInterface} */" +
      "class Comp extends React.Component {" +
        "/** @override */ interfaceMethod() {\n" +
            "return 1;\n" +
        "}\n" +
        "/* @override */" +
        "render() {\n" +
          "return React.createElement(\"div\");\n" +
        "}" +
      "};" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "class $Comp$$ extends React.Component{" +
        "render(){" +
          "return React.createElement(\"div\")" +
        "}" +
      "}" +
      "ReactDOM.render(React.createElement($Comp$$),document.body);");
    // We can't test that missing methods cause compiler warnings since we're
    // declaring CompInterface as extending AnInterface, thus the methods
    // assumed to be there.
  }

  @Test public void testState() {
    // this.state accesses are checked
    testError(
      "var Comp = React.createClass({" +
        "/** @return {{enabled: boolean}} */ getInitialState() {" +
          "return {enabled: false};" +
        "},\n" +
        "render() {" +
          "this.state.enabled.toFixed(2);" +
          "return null" +
        "}" +
      "});",
      "JSC_INEXISTENT_PROPERTY");
    // this.setState() calls are checked
    testError(
      "var Comp = React.createClass({" +
        "/** @return {{enabled: boolean}} */ getInitialState() {" +
          "return {enabled: false};" +
        "},\n" +
        "render() {" +
          "this.setState({enabled: 123});" +
          "return null;" +
        "},\n" +
      "});",
      "JSC_TYPE_MISMATCH");
    // this.setState() calls with an updater function should be checked
    testError(
      "var Comp = React.createClass({" +
        "/** @return {{enabled: boolean}} */ getInitialState() {" +
          "return {enabled: false};" +
        "},\n" +
        "render() {" +
          "this.setState((state, props) => ({enabled: 123}));" +
          "return null;" +
        "},\n" +
      "});",
      "JSC_TYPE_MISMATCH");
    // this.setState() accepts a subset of state fields
    testNoError(
      "var Comp = React.createClass({" +
        "/** @return {{f1: boolean, f2: number, f3: (number|boolean)}} */" +
        "getInitialState() {" +
          "return {f1: false, f2: 1, f3: 2};" +
        "},\n" +
        "render() {" +
          "this.setState({f1: true});" +
          "return null;" +
        "},\n" +
      "});");
    // return type for getInitialState must be a record
    testError(
      "var Comp = React.createClass({" +
        "/** @return {number} */ getInitialState() {" +
          "return {enabled: false};" +
        "},\n" +
        "render() {" +
          "return null;" +
        "}" +
      "});",
      "REACT_UNEXPECTED_STATE_TYPE");
    // component methods that take state parameters are checked
    testError(
      "var Comp = React.createClass({" +
        "/** @return {{enabled: boolean}} */ getInitialState() {" +
          "return {enabled: false};" +
        "},\n" +
        "componentWillUpdate(nextProps, nextState) {" +
          "nextState.enabled.toFixed(2);" +
        "},\n" +
        "render() {" +
          "return null;" +
        "}" +
      "});",
      "JSC_INEXISTENT_PROPERTY");
    // Mixin methods that take state parameters are checked
    testError(
      "var Mixin = React.createMixin({});\n" +
      "/** @param {!ReactState} state */" +
      "Mixin.mixinMethod;\n" +
      "var Comp = React.createClass({" +
        "mixins: [Mixin],\n" +
        "/** @return {{enabled: boolean}} */ getInitialState() {" +
          "return {enabled: false};" +
        "},\n" +
        "mixinMethod(state) {" +
          "state.enabled.toFixed(2);" +
        "},\n" +
        "render: function() {" +
          "return null;" +
        "}" +
      "});",
      "JSC_INEXISTENT_PROPERTY");
  }

  @Test public void testStateClass() {
    // this.state accesses are checked
    testError(
      "class Comp extends React.Component {" +
        "constructor(props) {" +
          "super(props);" +
          "/** @type {Comp.State} */" +
          "this.state = this.initialState();" +
        "}" +
        "/** @return {{enabled: boolean}} */" +
        "initialState() {" +
          "return {enabled: false};" +
        "}" +
        "/* @override */" +
        "render() {" +
          "this.state.enabled.toFixed(2);" +
          "return null" +
        "}" +
      "}",
      "JSC_INEXISTENT_PROPERTY");
    // this.setState() calls are checked
    testError(
      "class Comp extends React.Component {" +
        "constructor(props) {" +
          "super(props);" +
          "/** @type {Comp.State} */" +
          "this.state = this.initialState()" +
        "}" +
        "/** @return {{enabled: boolean}} */" +
        "initialState() {" +
          "return {enabled: false};" +
        "}" +
        "/* @override */" +
        "render() {" +
          "this.setState({enabled: 123});" +
          "return null;" +
        "}" +
      "}",
      "JSC_TYPE_MISMATCH");
    // this.setState() calls with an updater function should be checked, but the
    // compiler does not appear to be doing this.
    testError(
      "class Comp extends React.Component {" +
        "constructor(props) {" +
          "super(props);" +
          "/** @type {Comp.State} */" +
          "this.state = this.initialState();" +
        "}" +
        "/** @return {{enabled: boolean}} */" +
        "initialState() {" +
          "return {enabled: false};" +
        "}" +
        "/* @override */" +
        "render() {" +
          "this.setState((state, props) => ({enabled: 123}));" +
          "return null;" +
        "}" +
      "}",
      "JSC_TYPE_MISMATCH");
    // this.setState() accepts a subset of state fields
    testNoError(
      "class Comp extends React.Component {" +
        "constructor(props) {" +
          "super(props);" +
          "/** @type {Comp.State} */" +
          "this.state = this.initialState();" +
        "}" +
        "/** @return {{f1: boolean, f2: number, f3: (number|boolean)}} */" +
        "initialState() {" +
          "return {f1: false, f2: 1, f3: 2};" +
        "}" +
        "/* @override */" +
        "render() {" +
          "this.setState({f1: true});" +
          "return null;" +
        "}" +
      "}");
    // type for this.state must be a record
    testError(
      "class Comp extends React.Component {" +
        "constructor(props) {" +
          "super(props);" +
          "/** @type {number} */" +
          "this.state = this.initialState();" +
        "}" +
        "/** @return {number} */" +
        "initialState() {" +
          "return {enabled: false};" +
        "}" +
        "/* @override */" +
        "render() {" +
          "return null;" +
        "}" +
      "}",
      "REACT_UNEXPECTED_STATE_TYPE");
    // component methods that take state parameters are checked
    testError(
      "class Comp extends React.Component {" +
        "constructor(props) {" +
          "super(props);" +
          "/** @type {Comp.State} */" +
          "this.state = this.initialState();" +
        "}" +
        "/** @return {{enabled: boolean}} */" +
        "initialState() {" +
          "return {enabled: false};" +
        "}" +
        "/* @override */" +
        "componentWillUpdate(nextProps, nextState) {" +
          "nextState.enabled.toFixed(2);" +
        "}" +
        "render() {" +
          "return null;" +
        "}" +
      "}",
      "JSC_INEXISTENT_PROPERTY");

    // Mixin methods that take state parameters are checked
    testError(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {}" +
      "ReactSupport.declareMixin(Mixin);" +
      "/** @param {!ReactState} state */" +
      "Mixin.mixinMethod;" +
      "class Comp extends React.Component {" +
        "constructor(props) {" +
          "super(props);" +
          "/** @type {Comp.State} */" +
          "this.state = this.initialState();" +
        "}" +
        "/** @return {{enabled: boolean}} */" +
        "initialState() {" +
          "return {enabled: false};" +
        "}" +
        "/* @override */" +
        "mixinMethod(state) {" +
          "state.enabled.toFixed(2);" +
        "}" +
        "/* @override */" +
        "render() {" +
          "return null;" +
        "}" +
      "}" +
      "ReactSupport.mixin(Comp, Mixin);",
      "JSC_INEXISTENT_PROPERTY");
  
    testNoError(
      "class Comp extends React.Component {" +
        "constructor(props) {" +
          "super(props);" +
        "}" +
        "initialState() {" +
          "return null;" +
        "}" +
        "/* @override */" +
        "render() {" +
          "return null;" +
        "}" +
      "}");
   }

  @Test public void testFields() {
    // Fields defined in getInitialState are checked
    testError(
      "var Comp = React.createClass({" +
        "getInitialState() {" +
          "/** @private {boolean} */" +
          "this.field_ = true;\n" +
          "return null;" +
        "},\n" +
        "render() {" +
          "this.field_.toFixed(2);" +
          "return null" +
        "}" +
      "});",
      "JSC_INEXISTENT_PROPERTY");
      // Even if they don't have a value assigned.
      testError(
      "var Comp = React.createClass({" +
        "getInitialState() {" +
          "/** @private {boolean|undefined} */" +
          "this.field_;\n" +
          "return null;" +
        "},\n" +
        "render() {" +
          "this.field_.toFixed(2);" +
          "return null" +
        "}" +
      "});",
      "JSC_INEXISTENT_PROPERTY");
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
      "var $Comp$$=React.createClass({" +
        "propTypes:{$aProp$:React.PropTypes.string}," +
        "render:function(){" +
          "return React.createElement(\"div\",null,this.props.$aProp$)" +
        "}" +
      "});" +
      "$Comp$$.$PropsValidator$=function($props$jscomp$5$$){" +
        "return $props$jscomp$5$$" +
      "};" +
      "ReactDOM.render(React.createElement($Comp$$),document.body);");
    // isRequired variant
    test(
      "window.foo=React.PropTypes.string.isRequired;",
      "window.$foo$=React.PropTypes.string.isRequired;");
    // Other variants are rejected
    testError(
      "window.foo=React.PropTypes.string.isSortOfRequired;",
      "JSC_INEXISTENT_PROPERTY");
    // arrayOf
    test(
      "window.foo=React.PropTypes.arrayOf(React.PropTypes.string);",
      "window.$foo$=React.PropTypes.arrayOf(React.PropTypes.string);");
    test(
      "window.foo=React.PropTypes.arrayOf(React.PropTypes.string).isRequired;",
      "window.$foo$=React.PropTypes.arrayOf(React.PropTypes.string).isRequired;");
    testError(
      "window.foo=React.PropTypes.arrayOf(123);",
      "JSC_TYPE_MISMATCH");
    testError(
      "window.foo=React.PropTypes.arrayOf(React.PropTypes.string).isSortOfRequired;",
      "JSC_INEXISTENT_PROPERTY");
    // instanceOf
    test(
      "window.foo=React.PropTypes.instanceOf(Element);",
      "window.$foo$=React.PropTypes.instanceOf(Element);");
    testError(
      "window.foo=React.PropTypes.instanceOf(123);",
      "JSC_TYPE_MISMATCH");
    // oneOf
    test(
      "window.foo=React.PropTypes.oneOf([1,2,3]);",
      "window.$foo$=React.PropTypes.oneOf([1,2,3]);");
    testError(
      "window.foo=React.PropTypes.oneOf(123);",
      "JSC_TYPE_MISMATCH");
    // oneOfType
    test(
      "window.foo=React.PropTypes.oneOfType([React.PropTypes.string]);",
      "window.$foo$=React.PropTypes.oneOfType([React.PropTypes.string]);");
    testError(
      "window.foo=React.PropTypes.oneOfType(123);",
      "JSC_TYPE_MISMATCH");
    // shape
    test(
      "window.foo=React.PropTypes.shape({str: React.PropTypes.string});",
      "window.$foo$=React.PropTypes.shape({$str$:React.PropTypes.string});");
    testError(
      "window.foo=React.PropTypes.shape(123);",
      "JSC_TYPE_MISMATCH");
  }

  @Test public void testPropTypesClass() {
    // Basic prop types
    test(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "return React.createElement(\"div\", null, this.props.aProp);" +
        "}" +
      "}" +
      "Comp.propTypes = {aProp: React.PropTypes.string};" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "class $Comp$$ extends React.Component{" +
        "render(){" +
          "return React.createElement(\"div\",null,this.props.$aProp$)" +
        "}" +
      "}" +
      "$Comp$$.propTypes={$aProp$:React.PropTypes.string};" +
      "ReactDOM.render(React.createElement($Comp$$),document.body);");
  }

  @Test public void testOptimizeForSize() {
    ReactCompilerPass.Options passOptions =
        new ReactCompilerPass.Options();
    passOptions.optimizeForSize = true;
    passOptions.propTypesTypeChecking = true;
    // - propTypes should get stripped
    // - React.createMixin() calls should be inlined with just the spec
    // - React.createClass and React.createElement calls should be replaced with
    //   React$createClass and React$createElement aliases (which can get fully
    //   renamed).
    test(
      "var Mixin = React.createMixin({" +
          "mixinMethod: function() {return 'foo'}" +
      "});\n" +
      "var Comp = React.createClass({" +
        "mixins: [Mixin]," +
        "propTypes: {aProp: React.PropTypes.string}," +
        "render: function() {return React.createElement(\"div\", null, this.mixinMethod());}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "ReactDOM.render($React$createElement$$($React$createClass$$({" +
        "mixins:[{$mixinMethod$:function(){return\"foo\"}}]," +
        "render:function(){return $React$createElement$$(\"div\",null,\"foo\")}" +
      "})),document.body);",
      passOptions,
      null);
    // This should also work when using ES6 modules
    test(
      "export const anExport = 9;\n" +
      "var Comp = React.createClass({" +
        "propTypes: {aProp: React.PropTypes.string}," +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "ReactDOM.render($React$createElement$$($React$createClass$$({" +
        "render:function(){return $React$createElement$$(\"div\")}" +
      "})),document.body);",
      passOptions,
      null);
    // But propTypes tagged with @struct should be preserved (React.PropTypes
    // is replaced with an alias so that it can also be represented more
    // compactly).
    test(
      "var Comp = React.createClass({" +
        "/** @struct */" +
        "propTypes: {aProp: React.PropTypes.string}," +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "ReactDOM.render($React$createElement$$($React$createClass$$({" +
        "propTypes:{$aProp$:$React$PropTypes$$.string}," +
        "render:function(){return $React$createElement$$(\"div\")}" +
      "})),document.body);",
      passOptions,
      null);
  }

  @Test public void testOptimizeForSizeClass() {
    ReactCompilerPass.Options passOptions =
        new ReactCompilerPass.Options();
    passOptions.optimizeForSize = true;
    passOptions.propTypesTypeChecking = true;
    // - propTypes should get stripped
    // - React.Component and React.createElement calls should be replaced with
    //   React$Component and React$createElement aliases (which can get fully
    //   renamed).

    test(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {" +
          "mixinMethod() {return 'foo'}" +
      "}" +
      "ReactSupport.declareMixin(Mixin);" +
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "return React.createElement(\"div\", null, this.mixinMethod());" +
        "}" +
      "}" +
      "ReactSupport.mixin(Comp, Mixin);" +
      "Comp.propTypes = {aProp: React.PropTypes.string};" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "class $Mixin$$ extends $React$Component$${}" +
      "class $Comp$$ extends $React$Component$${" +
        "render(){" +
          "return $React$createElement$$(\"div\",null,\"foo\")" +
        "}" +
      "}" +
      "$Comp$$.mixins=[$Mixin$$];" +
      "ReactDOM.render($React$createElement$$($Comp$$),document.body);",
      passOptions,
      null);

    // This should also work when using ES6 modules
    test(
      "export const anExport = 9;" +
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "return React.createElement(\"div\");" +
        "}" +
      "}" +
      "Comp.propTypes = {aProp: React.PropTypes.string};" +
      "Comp.defaultProps = {aProp: \"hi\"};" +
      "Comp.contextTypes = {aContext: React.PropTypes.number};" +      
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "class $Comp$$module$src$file1$$ extends $React$Component$${" +
        "render(){" +
          "return $React$createElement$$(\"div\")" +
        "}" +
      "}" +
      "$Comp$$module$src$file1$$.defaultProps={$aProp$:\"hi\"};" +
      "ReactDOM.render($React$createElement$$($Comp$$module$src$file1$$),document.body);",
      passOptions,
      null);
  }

  @Test public void testNoRenameReactApi() {
    // Even when optimizing for size there is no renaming.
    ReactCompilerPass.Options passOptions = new ReactCompilerPass.Options();
    passOptions.optimizeForSize = true;
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
      passOptions,
      null);
    // Other API symbols are not renamed either.
    List<String> reactApiSymbols = ImmutableList.of("React", "React.Component",
      "React.PureComponent", "React.cloneElement", "ReactDOM.findDOMNode",
      "ReactDOM.unmountComponentAtNode");
    for (String reactApiSymbol : reactApiSymbols) {
      test(
        "window['test'] = " + reactApiSymbol + ";",
        "window.test=" + reactApiSymbol + ";",
        passOptions,
        null);
    }
  }

  @Test public void testExport() {
    String CLOSURE_EXPORT_FUNCTIONS =
      "/** @const */ const goog = {};" +
      "goog.exportSymbol = function(publicPath, object) {};\n" +
      "goog.exportProperty = function(object, publicName, symbol) {};\n";
    // Props where the class is tagged with @export should not get renamed,
    // nor should methods explicitly tagged with @public.
    test(
      CLOSURE_EXPORT_FUNCTIONS +
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
      "var $Comp$$=React.createClass({" +
        "propTypes:{aProp:React.PropTypes.string}," +
        "publicFunction:function(){" +
          "return\"dont_rename_me_bro\"" +
        "}," +
        "$privateFunction_$:function(){" +
          "return 1" +
        "}," +
        "render:function(){" +
          "return React.createElement(\"div\",null,this.props.aProp)" +
        "}" +
      "});" +
      "$Comp$$.$PropsValidator$=function(){" +
        "return{aProp:\"foo\"}" +
      "};" +
      "ReactDOM.render(React.createElement($Comp$$,{aProp:\"foo\"}),document.body);");
    // Even with a minified build there is no renaming.
    ReactCompilerPass.Options minifiedReactPassOptions =
        new ReactCompilerPass.Options();
    minifiedReactPassOptions.optimizeForSize = true;
    test(
      CLOSURE_EXPORT_FUNCTIONS +
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
      "ReactDOM.render($React$createElement$$($React$createClass$$({" +
        "publicFunction:function(){" +
            "return\"dont_rename_me_bro\"" +
        "}," +
        "$privateFunction_$:function(){" +
            "return 1" +
        "}," +
        "render:function(){" +
          "return $React$createElement$$(\"div\",null,this.props.aProp)" +
        "}" +
      "}),{aProp:\"foo\"}),document.body);",
      minifiedReactPassOptions,
      null);
  }

  @Test public void testExportClass () {
    String CLOSURE_EXPORT_FUNCTIONS =
      "/** @const */ const goog = {};" +
      "goog.exportSymbol = function(publicPath, object) {};\n" +
      "goog.exportProperty = function(object, publicName, symbol) {};\n";
    // Props where the class is tagged with @export should not get renamed,
    // nor should methods explicitly tagged with @export.
    test(
      CLOSURE_EXPORT_FUNCTIONS +
      "/** @export */" +
      "class Comp extends React.Component {" +
        "/** @export */ publicFunction() {\n" +
            "return \"dont_rename_me_bro\";\n" +
        "}\n" +
        "/** @private */ privateFunction_() {\n" +
            "return 1;\n" +
        "}\n" +
        "render() {\n" +
          "return React.createElement(\"div\", null, this.props.aProp);\n" +
        "}" +
      "}" +
      "Comp.propTypes = {aProp: React.PropTypes.string};\n" +
      "ReactDOM.render(React.createElement(Comp, {aProp: \"foo\"}), document.body);",
      "class $Comp$$ extends React.Component{" +
        "publicFunction(){" +
          "return\"dont_rename_me_bro\"" +
        "}" +
        "render(){" +
          "return React.createElement(\"div\",null,this.props.aProp)" +
        "}" +
      "}" +
      "$Comp$$.propTypes={aProp:React.PropTypes.string};" +
      "ReactDOM.render(React.createElement($Comp$$,{aProp:\"foo\"}),document.body);");
    // Even with a minified build there is no renaming.
    ReactCompilerPass.Options minifiedReactPassOptions =
        new ReactCompilerPass.Options();
    minifiedReactPassOptions.optimizeForSize = true;
    test(
      CLOSURE_EXPORT_FUNCTIONS +
      "/** @export */" +
      "class Comp extends React.Component {" +
        "/** @export */ publicFunction() {\n" +
            "return \"dont_rename_me_bro\";\n" +
        "}\n" +
        "/** @private */ privateFunction_() {\n" +
            "return 1;\n" +
        "}\n" +
        "render() {\n" +
          "return React.createElement(\"div\", null, this.props.aProp);\n" +
        "}" +
      "}" +
      "Comp.propTypes = {aProp: React.PropTypes.string};" +
      "ReactDOM.render(React.createElement(Comp, {aProp: \"foo\"}), document.body);",
      "class $Comp$$ extends $React$Component$${" +
        "publicFunction(){" +
          "return\"dont_rename_me_bro\"" +
        "}" +
        "render(){" +
          "return $React$createElement$$(\"div\",null,this.props.aProp)" +
        "}" +
      "}" +
      "ReactDOM.render($React$createElement$$($Comp$$,{aProp:\"foo\"}),document.body);",
      minifiedReactPassOptions,
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
    // Multiple children
    testNoError(
        "var Comp = React.createClass({" +
          "propTypes: {" +
            "children: React.PropTypes.arrayOf(React.PropTypes.element).isRequired" +
          "}," +
          "render: function() {return null;}" +
        "});\n" +
        "React.createElement(Comp, {}, React.createElement(Comp), React.createElement(Comp));");
    // Children required but not passed in
    testError(
        "var Comp = React.createClass({" +
          "propTypes: {" +
            "children: React.PropTypes.element.isRequired" +
          "}," +
          "render: function() {return null;}" +
        "});\n" +
        "React.createElement(Comp, {});",
        "REACT_NO_CHILDREN_ARGUMENT");
    // Children not required and not passed in
    testNoError(
        "var Comp = React.createClass({" +
          "propTypes: {" +
            "children: React.PropTypes.element" +
          "}," +
          "render: function() {return null;}" +
        "});\n" +
        "React.createElement(Comp, {});");
    // Children required and wrong type passed in
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
    testPropTypesError(
        "{aProp: React.PropTypes.number.isRequired}",
        "{...{}}",
        "JSC_TYPE_MISMATCH");

    testPropTypesNoError(
        "{aProp: React.PropTypes.number.isRequired," +
        "bProp: React.PropTypes.number.isRequired}",
        "Object.assign({}, {aProp: 1})");
    testPropTypesError(
        "{aProp: React.PropTypes.number.isRequired," +
        "bProp: React.PropTypes.number.isRequired}",
        "{...{aProp: 1}}",
        "JSC_TYPE_MISMATCH");

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

@Test public void testPropTypesTypeCheckingClass() {
    // Validate use of props within methods.
    testError(
      "export{};class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "this.props.numberProp();" +
          "return null;" +
        "}" +
      "}" +
      "Comp.propTypes = {numberProp: React.PropTypes.number};",
      "JSC_NOT_FUNCTION_TYPE");

    // Validate children prop
    testNoError(
        "class Comp extends React.Component {" +
          "render() {return null;}" +
        "}" +
        "Comp.propTypes= {" +
          "children: React.PropTypes.element.isRequired" +
        "};" +
        "React.createElement(Comp, {}, React.createElement(\"div\"));");
    testNoError(
        "class Comp extends React.Component {" +
          "render() {return null;}" +
        "}" +
        "Comp.propTypes = {" +
          "children: React.PropTypes.element.isRequired" +
        "};" +
        "React.createElement(Comp, {}, React.createElement(Comp));");
    // Multiple children
    testNoError(
        "class Comp extends React.Component {" +
          "render() {return null;}" +
        "}" +
        "Comp.propTypes = {" +
          "children: React.PropTypes.arrayOf(React.PropTypes.element).isRequired" +
        "};" +
        "React.createElement(Comp, {}, React.createElement(Comp), React.createElement(Comp));");
    // Children required but not passed in
    testError(
        "class Comp extends React.Component {" +
          "render() {return null;}" +
        "}" +
        "Comp.propTypes = {" +
          "children: React.PropTypes.element.isRequired" +
        "};" +
        "React.createElement(Comp, {});",
        "REACT_NO_CHILDREN_ARGUMENT");
    // Children not required and not passed in
    testNoError(
        "class Comp extends React.Component {" +
          "render() {return null;}" +
        "}" +
        "Comp.propTypes = {" +
          "children: React.PropTypes.element" +
        "};" +
        "React.createElement(Comp, {});");
    // Children required and wrong type passed in
    testError(
        "class Comp extends React.Component {" +
          "render() {return null;}" +
        "}" +
        "Comp.propTypes = {" +
          "children: React.PropTypes.element.isRequired" +
        "};" +
        "React.createElement(Comp, {}, null);",
        "JSC_TYPE_MISMATCH");

    test(
        "class Comp extends React.Component {" +
          "render() {return null;}" +
        "}\n" +
        "Comp.propTypes = {" +
          "aProp: React.PropTypes.string.isRequired" +
        "};" +
        "Comp.defaultProps = {aProp: \"1\"};" +
        "React.createElement(Comp, Object.assign({aProp: \"1\"}, {}))",
        "class $Comp$$ extends React.Component{" +
          "render(){" +
            "return null" +
          "}" +
        "}" +
        "$Comp$$.propTypes={$aProp$:React.PropTypes.string.isRequired};" +
        "$Comp$$.defaultProps={$aProp$:\"1\"};" +
        "React.createElement($Comp$$,Object.assign({$aProp$:\"1\"},{}));");
    testNoError(
      "class Comp extends React.Component {" +
        "render() {return null;}" +
      "}\n" +
      "Comp.propTypes = {" +
        "aProp: React.PropTypes.string.isRequired" +
      "};" +
      "Comp.defaultProps = {aProp: \"1\"};" +
      "React.createElement(Comp, {aProp: \"1\",...{}})");

    // Required props with default values can be ommitted.
    testNoError(
        "class Comp extends React.Component {" +
          "render() {return null;}" +
        "}\n" +
        "Comp.propTypes = {" +
          "strProp: React.PropTypes.string.isRequired" +
        "};" +
        "Comp.defaultProps = {strProp: \"1\"};" +
        "React.createElement(Comp, {});");
    testNoError(
        "class Comp extends React.Component {" +
          "render() {return null;}" +
        "}\n" +
        "Comp.propTypes = {" +
            "strProp: React.PropTypes.string.isRequired" +
        "};" +
        "Comp.defaultProps = {strProp: \"1\"};" +
        "React.createElement(Comp, null);");
    // Applies to custom type expressions too
    testNoError(
        "class Comp extends React.Component {" +
          "render() {return null;}" +
        "}\n" +
        "Comp.propTypes = {" +
          "/** @type {boolean} */ boolProp: function() {}" +
        "};" +
        "Comp.defaultProps = {boolProp: true};" +
        "React.createElement(Comp, {});");
    // But if they are provided their types are still checked.
    testError(
        "class Comp extends React.Component {" +
          "render() {return null;}" +
        "}\n" +
        "Comp.propTypes = {" +
          "strProp: React.PropTypes.string.isRequired" +
        "};" +
        "Comp.defaultProps = {strProp: \"1\"};" +
        "React.createElement(Comp, {strProp: 1});",
        "JSC_TYPE_MISMATCH");
    // Even if not required, if they have a default value their value inside
    // the component is not null or undefined.
    testNoError(
        "class Comp extends React.Component {" +
          "render() {" +
            "this.strMethod_(this.props.strProp);" +
            "return null;" +
          "}\n" +
          "/**" +
          " * @param {string} param" +
          " * @return {string}" +
          " * @private" +
          " */" +
          "strMethod_(param) {return param;}" +
        "}\n" +
        "Comp.propTypes = {" +
          "strProp: React.PropTypes.string" +
        "};\n" +
        "Comp.defaultProps = {strProp: \"1\"};\n" +
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

  @Test public void testPropTypesMixinsClass() {
    testError(
        REACT_SUPPORT_CODE +
        "class Mixin extends React.Component {" +
        "}" +
        "ReactSupport.declareMixin(Mixin);" +
        "Mixin.propTypes = {" +
          "mixinNumberProp: React.PropTypes.number.isRequired" +
        "};" +
        "class Comp extends React.Component {" +
          "render() {return this.props.mixinNumberProp();}" +
        "}" +
        "ReactSupport.mixin(Comp, Mixin);" +
        "Comp.propTypes = {" +
          "numberProp: React.PropTypes.number.isRequired" +
        "};",
        "JSC_NOT_FUNCTION_TYPE");
    // Even when the component doesn't have its own propTypes those of the
    // mixin are considered.
    testError(
        REACT_SUPPORT_CODE +
        "class Mixin extends React.Component {}" +
        "ReactSupport.declareMixin(Mixin);" +
        "Mixin.propTypes = {" +
          "mixinNumberProp: React.PropTypes.number.isRequired" +
        "};" +
        "class Comp extends React.Component {" +
          "render() {return this.props.mixinNumberProp();}" +
        "}" +
        "ReactSupport.mixin(Comp, Mixin);",
        "JSC_NOT_FUNCTION_TYPE");
    testNoError(
        REACT_SUPPORT_CODE +
        "class Mixin extends React.Component {}" +
        "ReactSupport.declareMixin(Mixin);" +
        "Mixin.propTypes = {" +
          "mixinFuncProp: React.PropTypes.func.isRequired" +
        "};" +
        "class Comp extends React.Component {" +
          "render() {return this.props.mixinFuncProp();}" +
        "}"+
        "ReactSupport.mixin(Comp, Mixin);");
    // The same propTypes can be in both mixins and components (and the
    // component one has precedence).
    testNoError(
        REACT_SUPPORT_CODE +
        "class Mixin extends React.Component {}" +
        "ReactSupport.declareMixin(Mixin);" +
        "Mixin.propTypes = {" +
          "aProp: React.PropTypes.number.isRequired" +
        "};" +
        "class Comp extends React.Component {" +
          "render() {return null;}" +
        "}" +
        "ReactSupport.mixin(Comp, Mixin);" +
        "Comp.propTypes = {" +
          "aProp: React.PropTypes.number.isRequired" +
        "};");
    // Custom type expressions are handled
    testNoError(
        REACT_SUPPORT_CODE +
        "class Mixin extends React.Component {}" +
        "ReactSupport.declareMixin(Mixin);" +
        "Mixin.propTypes = {" +
          "/** @type {boolean} */ boolProp: function() {}" +
        "};" +
        "class Comp extends React.Component {" +
          "render() {return null;}" +
        "}" +
        "ReactSupport.mixin(Comp, Mixin);" +
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

  @Test public void testPropTypesComponentMethodsClass() {
    // React component/lifecycle methods automatically get the specific prop
    // type injected.
    testError(
        "class Comp extends React.Component {" +
        "/* @override */" +
          "componentWillReceiveProps(nextProps) {" +
             "nextProps.numberProp();" +
          "}\n" +
          "/* @override */" +
          "render() {return null;}" +
        "}\n" +
        "Comp.propTypes = {" +
          "numberProp: React.PropTypes.number.isRequired" +
        "};\n" +
        "React.createElement(Comp, {numberProp: 1});",
        "JSC_NOT_FUNCTION_TYPE");

    // As do abstract mixin methods that use ReactProps as the type.
    testError(
        REACT_SUPPORT_CODE +
        "class Mixin extends React.Component {}" +
        "ReactSupport.declareMixin(Mixin);" +
        "/** @param {ReactProps} props @protected */" +
        "Mixin.mixinAbstractMethod;" +
        "class Comp extends React.Component {" +
          "/* @override */" +
          "mixinAbstractMethod(props) {" +
             "props.numberProp();" +
          "}" +
          "/* @override */" +
          "render() {return null;}" +
        "}" +
        "ReactSupport.mixin(Comp, Mixin);" +
        "Comp.propTypes = {" +
          "numberProp: React.PropTypes.number.isRequired" +
        "};" +
        "React.createElement(Comp, {numberProp: 1});",
        "JSC_NOT_FUNCTION_TYPE");

    test(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "componentWillReceiveProps(nextProps) {" +
            "nextProps.numFunc(42);" +
        "}\n" +
        "/* @override */" +
        "render() {return null;}" +
      "}\n" +
      "Comp.propTypes = {" +
        "/** @type {function(number)} */" +
        "numFunc: React.PropTypes.func.isRequired" +
      "};\n" +
      "React.createElement(Comp, {numFunc: x => x/10});",
      "class $Comp$$ extends React.Component{" +
        "componentWillReceiveProps($nextProps$jscomp$6$$){" +
          "$nextProps$jscomp$6$$.$numFunc$(42)" +
        "}" +
        "render(){" +
          "return null" +
        "}" +
      "}" +
      "$Comp$$.propTypes={" +
        "$numFunc$:React.PropTypes.func.isRequired" +
      "};" +
      "React.createElement($Comp$$,{$numFunc$:$x$jscomp$15$$=>$x$jscomp$15$$/10});");
  
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
    testError(
      "class Message {}\n" +
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return null;}" +
      "}\n" +
      "Comp.propTypes = " + propTypes + ";\n" +
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
    testNoError(
      "class Message {}\n" +
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {return null;}" +
      "}\n" +
      "Comp.propTypes = " + propTypes + ";\n" +
      "React.createElement(Comp, " + props + ");");
  }

  @Test public void testChildren() {
    // Non-comprehensive test that the React.Children namespace functions exist.
    test(
      "var Comp = React.createClass({" +
        "propTypes: {" +
          "children: React.PropTypes.element.isRequired" +
        "}," +
        "render: function() {" +
          "return React.createElement(" +
              "\"div\", null, React.Children.only(this.props.children));" +
          "}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "var $Comp$$=React.createClass({" +
        "propTypes:{" +
          "children:React.PropTypes.element.isRequired" +
        "}," +
        "render:function(){" +
          "return React.createElement(" +
            "\"div\",null,React.Children.only(this.props.children))" +
        "}" +
      "});" +
      "$Comp$$.$PropsValidator$=function($props$jscomp$5$$){" +
        "return $props$jscomp$5$$" +
      "};" +
      "$Comp$$.$ChildrenValidator$=function($children$jscomp$7$$){" +
        "return $children$jscomp$7$$" +
      "};" +
      "ReactDOM.render(React.createElement($Comp$$),document.body);");
  }

  @Test public void testChildrenClass() {
    // Non-comprehensive test that the React.Children namespace functions exist.
    test(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "return React.createElement(" +
              "\"div\", null, React.Children.only(this.props.children));" +
        "}" +
      "}" +
      "Comp.propTypes = {" +
        "children: React.PropTypes.element.isRequired" +
      "};" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "class $Comp$$ extends React.Component{" +
        "render(){" +
          "return React.createElement(\"div\",null,React.Children.only(this.props.children))" +
        "}" +
      "}" +
      "$Comp$$.propTypes={children:React.PropTypes.element.isRequired};" +
      "ReactDOM.render(React.createElement($Comp$$),document.body);");
  }

  @Test public void testContextTypesTypeChecking() {
    // Validate use of context within methods.
    testError(
      "var Comp = React.createClass({" +
        "contextTypes: {numberProp: React.PropTypes.number}," +
        "render: function() {" +
          "this.context.numberProp();" +
          "return null;" +
        "}" +
      "});",
      "JSC_NOT_FUNCTION_TYPE");
    // Both props and context are checked
    testNoError(
      "var Comp = React.createClass({" +
        "contextTypes: {\n" +
          "/** @type {function(number)} */\n" +
          "functionProp: React.PropTypes.func," +
        "}," +
        "propTypes: {numberProp: React.PropTypes.number.isRequired}," +
        "render: function() {" +
          "this.context.functionProp(this.props.numberProp);" +
          "return null;" +
        "}" +
      "});");
    testError(
      "var Comp = React.createClass({" +
        "contextTypes: {\n" +
          "/** @type {function(number)} */\n" +
          "functionProp: React.PropTypes.func," +
        "}," +
        "propTypes: {stringProp: React.PropTypes.string.isRequired}," +
        "render: function() {" +
          "this.context.functionProp(this.props.stringProp);" +
          "return null;" +
        "}" +
      "});",
      "JSC_TYPE_MISMATCH");
  }

  @Test public void testContextTypesTypeCheckingClass() {
    // Validate use of context within methods.
    testError(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "this.context.numberProp();" +
          "return null;" +
        "}" +
      "}" +
      "Comp.contextTypes = {numberProp: React.PropTypes.number};",
      "JSC_NOT_FUNCTION_TYPE");
    // Both props and context are checked
    test(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "this.context.functionProp(this.props.numberProp);" +
          "return null;" +
        "}" +
      "}" +
      "Comp.contextTypes = {\n" +
        "/** @type {function(number)} */\n" +
        "functionProp: React.PropTypes.func," +
      "};" +
      "Comp.propTypes = {numberProp: React.PropTypes.number.isRequired};" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "class $Comp$$ extends React.Component{" +
        "render(){" +
          "this.context.$functionProp$(this.props.$numberProp$);" +
          "return null" +
        "}" +
      "}" +
      "$Comp$$.contextTypes={$functionProp$:React.PropTypes.func};" +
      "$Comp$$.propTypes={$numberProp$:React.PropTypes.number.isRequired};" +
      "ReactDOM.render(React.createElement($Comp$$),document.body);");
    testError(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "this.context.functionProp(this.props.stringProp);" +
          "return null;" +
        "}" +
      "}" +
      "Comp.contextTypes = {\n" +
        "/** @type {function(number)} */\n" +
        "functionProp: React.PropTypes.func," +
      "};\n" +
      "Comp.propTypes = {stringProp: React.PropTypes.string.isRequired};",
      "JSC_TYPE_MISMATCH");
  }

  @Test public void testReactDOM() {
    test("var Comp = React.createClass({});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "ReactDOM.render(React.createElement(" +
      "React.createClass({})" +
      "),document.body);");
    test("ReactDOM.findDOMNode(document.body);",
      "ReactDOM.findDOMNode(document.body);");
    testError("ReactDOM.findDOMNode([document.body]);", "JSC_TYPE_MISMATCH");
    test("ReactDOM.unmountComponentAtNode(document.body);",
      "ReactDOM.unmountComponentAtNode(document.body);");
    testError("ReactDOM.unmountComponentAtNode(\"notanode\");",
      "JSC_TYPE_MISMATCH");
  }

  @Test public void testReactDOMClass() {
    test("class Comp extends React.Component {}" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "class $Comp$$ extends React.Component{}" +
      "ReactDOM.render(React.createElement($Comp$$),document.body);");
  }

  @Test public void testReactDOMServer() {
    test("var Comp = React.createClass({});" +
      "ReactDOMServer.renderToString(React.createElement(Comp));",
      "ReactDOMServer.renderToString(" +
      "React.createElement(React.createClass({})));");
    testError("ReactDOMServer.renderToString(\"notanelement\");",
      "JSC_TYPE_MISMATCH");
    test("var Comp = React.createClass({});" +
      "ReactDOMServer.renderToStaticMarkup(React.createElement(Comp));",
      "ReactDOMServer.renderToStaticMarkup(" +
      "React.createElement(React.createClass({})));");
    testError("ReactDOMServer.renderToStaticMarkup(\"notanelement\");",
      "JSC_TYPE_MISMATCH");
  }

  @Test public void testReactDOMServerClass() {
    test("class Comp extends React.Component {}" +
      "ReactDOMServer.renderToString(React.createElement(Comp));",
      "class $Comp$$ extends React.Component{}" +
      "ReactDOMServer.renderToString(React.createElement($Comp$$));");
    test("class Comp extends React.Component {}" +
      "ReactDOMServer.renderToStaticMarkup(React.createElement(Comp));",
      "class $Comp$$ extends React.Component{}" +
      "ReactDOMServer.renderToStaticMarkup(React.createElement($Comp$$));");
  }

  /**
   * Tests static methods and properties.
   */
  @Test public void testStatics() {
    test(
      "var Comp = React.createClass({" +
        "statics: {" +
          "/** @const {number} */" +
          "aNumber: 123,\n" +
          "/** @const {string} */" +
          "aString: \"456\",\n" +
          "/** @return {number} */" +
          "aFunction: function() {return 123}" +
        "},\n" +
        "render: function() {return React.createElement(\"div\");}" +
      "});\n" +
      "window.aNumber = Comp.aNumber;\n" +
      "window.aString = Comp.aString;\n" +
      "window.aFunctionResult = Comp.aFunction();\n",
      "var $Comp$$=React.createClass({" +
        "statics:{" +
        "$aNumber$:123," +
        "$aString$:\"456\"," +
        "$aFunction$:function(){return 123}" +
        "}," +
        "render:function(){return React.createElement(\"div\")}" +
      "});" +
      "window.$aNumber$=$Comp$$.$aNumber$;" +
      "window.$aString$=$Comp$$.$aString$;" +
      "window.$aFunctionResult$=123;");
    // JSDoc is required
    testError(
      "var Comp = React.createClass({" +
        "statics: {" +
          "aFunction: function(aNumber) {window.foo = aNumber}" +
        "}," +
        "render: function() {return React.createElement(\"div\");}" +
      "});",
      "REACT_JSDOC_REQUIRED_FOR_STATICS");
    // JSDoc is used to validate.
    testError(
      "var Comp = React.createClass({" +
        "statics: {" +
          "/** @param {number} aNumber */" +
          "aFunction: function(aNumber) {window.foo = aNumber}" +
        "}," +
        "render: function() {return React.createElement(\"div\");}" +
      "});" +
      "window.foo = Comp.aFunction('notANumber');",
      "JSC_TYPE_MISMATCH");
  }
  @Test public void testStaticsClass() {
    test(
      "class Comp extends React.Component {" +
        "render() {return React.createElement(\"div\");}" +
        "/** @return {number} */" +
        "static aFunction() {return 123}" +
      "}\n" +
      "/** @const {number} */" +
      "Comp.aNumber = 123;\n" +
      "/** @const {string} */" +
      "Comp.aString = \"456\";\n" +
      "window.aNumber = Comp.aNumber;\n" +
      "window.aString = Comp.aString;\n" +
      "window.aFunctionResult = Comp.aFunction();\n",
      "window.$aNumber$=123;" +
      "window.$aString$=\"456\";" +
      "window.$aFunctionResult$=123;");
    // JSDoc is used to validate.
    testError(
      "class Comp extends React.Component {" +
        "/** @param {number} aNumber */" +
        "static aFunction(aNumber) {window.foo = aNumber}" +
        "render() {return React.createElement(\"div\");}" +
      "}" +
      "window.foo = Comp.aFunction('notANumber');",
      "JSC_TYPE_MISMATCH");
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
      "ReactDOM.render(React.createElement(React.createClass({" +
        "mixins:[React.addons.PureRenderMixin]," +
        "render:function(){" +
          "return React.createElement(\"div\")" +
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

  @Test public void testExtendsPureComponent() {
    test(
      "class Comp extends React.PureComponent {" +
        "/* @override */" +
        "render() {" +
          "return React.createElement(\"div\");" +
        "}" +
      "}" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      // Should be fine to use React.PureComponent.
      "class $Comp$$ extends React.PureComponent{" +
        "render(){" +
          "return React.createElement(\"div\")" +
        "}" +
      "}" +
      "ReactDOM.render(React.createElement($Comp$$),document.body);");
    testError(
      "class Comp extends React.PureComponent {" +
        "shouldComponentUpdate(nextProps, nextState) {" +
          "return true;" +
        "}" +
        "render() {" +
          "return React.createElement(\"div\");" +
        "}" +
      "}",
      // But there should be a warning if using PureComponent and
      // shouldComponentUpdate is specified.
      ReactCompilerPass.PURE_COMPONENT_SHOULD_COMPONENT_UPDATE_OVERRIDE);
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

  @Test public void testElementTypedefClass() {
    test(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "return React.createElement(\"div\");" +
        "}" +
      "}\n" +
      "/** @return {CompElement} */\n" +
      "function create() {return React.createElement(Comp);}",
      "");
    test(
      "const ns = {};" +
      "ns.Comp = class extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "return React.createElement(\"div\");" +
        "}" +
      "}\n" +
      "/** @return {ns.CompElement} */\n" +
      "function create() {return React.createElement(ns.Comp);}",
      "");
  }

  @Test public void testPropsSpreadInlining() {
    test(
      "var Comp = React.createClass({" +
        "render: function() {" +
          "var props = {a: \"1\"};\n" +
          "return React.createElement(\"div\", {...props});" +
        "}" +
      "});" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "ReactDOM.render(React.createElement(React.createClass({" +
        "render:function(){" +
          "return React.createElement(\"div\",{$a$:\"1\"})" +
        "}" +
      "})),document.body);");
  }

  @Test public void testPropsSpreadInliningClass() {
    test(
      "class Comp extends React.Component {" +
        "/* @override */" +
        "render() {" +
          "var props = {a: \"1\"};\n" +
          "return React.createElement(\"div\", {...props});" +
        "}" +
      "}" +
      "ReactDOM.render(React.createElement(Comp), document.body);",
      "class $Comp$$ extends React.Component{" +
        "render(){" +
          "return React.createElement(\"div\",{$a$:\"1\"})" +
        "}" +
      "}" +
      "ReactDOM.render(React.createElement($Comp$$),document.body);");
  }

  @Test public void testMixinStaticMethod() {
    testNoError(
      "const Mixin = React.createMixin({" +
        "statics: {"+
          "/** " +
          " * @param {string} x\n" +
          " * @param {number} y\n" +
          " * @return {string}" +
          " */" +
          "method(x, y) {" +
            "return x + y;" +
          "}," +
        "},"+
      "});" +
      "const Comp = React.createClass({" +
        "mixins: [Mixin]," +
      "});" +
      "Comp.method(\"a\", 1);");
    // Optional param
    testNoError(
      "const Mixin = React.createMixin({" +
        "statics: {"+
          "/** " +
          " * @param {string} x\n" +
          " * @param {number=} y\n" +
          " * @return {string}" +
          " */" +
          "method(x, y) {" +
            "return x + y;" +
          "}," +
        "},"+
      "});" +
      "const Comp = React.createClass({" +
        "mixins: [Mixin]," +
      "});" +
      "Comp.method(\"a\");");
    // Rest params
    testNoError(
      "const Mixin = React.createMixin({" +
        "statics: {"+
          "/** " +
          " * @param {...string} xs\n" +
          " * @return {string}" +
          " */" +
          "method(...xs) {" +
            "return xs.join(\"\");" +
          "}," +
        "},"+
      "});" +
      "const Comp = React.createClass({" +
        "mixins: [Mixin]," +
      "});" +
      "Comp.method(\"a\", \"b\", \"c\");");
  }

  @Test public void testMixinStaticMethodClass() {
    testNoError(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {" +
        "/** " +
        " * @param {string} x\n" +
        " * @param {number} y\n" +
        " * @return {string}" +
        " */" +
        "static method(x, y) {" +
          "return x + y;" +
        "}" +
      "}" +
      "ReactSupport.declareMixin(Mixin);" +
      "class Comp extends React.Component {}" +
      "ReactSupport.mixin(Comp, Mixin);" +
      "Comp.method(\"a\", 1);");
    // Optional param
    testNoError(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {" +
        "/** " +
        " * @param {string} x\n" +
        " * @param {number=} y\n" +
        " * @return {string}" +
        " */" +
        "static method(x, y) {" +
          "return x + y;" +
        "}" +
      "}" +
      "ReactSupport.declareMixin(Mixin);" +
      "class Comp extends React.Component {}" +
      "ReactSupport.mixin(Comp, Mixin);" +
      "Comp.method(\"a\");");
    // Rest params
    testNoError(
      REACT_SUPPORT_CODE +
      "class Mixin extends React.Component {" +
        "/** " +
        " * @param {...string} xs\n" +
        " * @return {string}" +
        " */" +
        "static method(...xs) {" +
          "return xs.join(\"\");" +
        "}" +
      "}" +
      "ReactSupport.declareMixin(Mixin);" +
      "class Comp extends React.Component {}" +
      "ReactSupport.mixin(Comp, Mixin);" +
      "Comp.method(\"a\", \"b\", \"c\");");
  }

  private static void test(String inputJs, String expectedJs) {
    test(inputJs, expectedJs, null, null);
  }

  private static void testError(String inputJs, String expectedErrorName) {
    test(inputJs, "", null, DiagnosticType.error(expectedErrorName, ""));
  }

  private static void testError(String inputJs, DiagnosticType expectedError) {
    test(inputJs, "",  null, expectedError);
  }

  private static void testNoError(String inputJs) {
    test(inputJs, null, null, null);
  }

  private static void test(
        String inputJs,
        String expectedJs,
        ReactCompilerPass.Options passOptions,
        DiagnosticType expectedError) {
    if (passOptions == null) {
      passOptions = new ReactCompilerPass.Options();
      passOptions.propTypesTypeChecking = true;
    }
    Compiler compiler = new Compiler(
        new PrintStream(ByteStreams.nullOutputStream())); // Silence logging
    compiler.disableThreads(); // Makes errors easier to track down.
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setLanguage(CompilerOptions.LanguageMode.ECMASCRIPT_2018);
    options.setEmitUseStrict(false);  // It is just noise
    if (!passOptions.optimizeForSize) {
      // We assume that when optimizing for size we don't care about property
      // checks (they rely on propTypes being extracted, which we don't do).
      options.setWarningLevel(
          DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.ERROR);
      options.setWarningLevel(
          DiagnosticGroups.STRICT_MISSING_PROPERTIES, CheckLevel.ERROR);
    }
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);
    options.setGeneratePseudoNames(true);
    options.addWarningsGuard(new ReactWarningsGuard());
    // Report warnings as errors to make tests simpler
    options.addWarningsGuard(new StrictWarningsGuard());
    options.addCustomPass(
        CustomPassExecutionTime.BEFORE_CHECKS,
        new ReactCompilerPass(compiler, passOptions));
    List<SourceFile> inputs = Lists.newArrayList();
    for (String fileJs : Splitter.on(FILE_SEPARATOR).split(inputJs)) {
      inputs.add(
          SourceFile.fromCode("/src/file" + (inputs.size() + 1) + ".js", fileJs));
    }
    List<SourceFile> builtInExterns;
    try {
      // We need the built-in externs so that Error and other built-in types
      // are defined.
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

    if (passOptions.optimizeForSize) {
      options.setPrintInputDelimiter(true);
    }

    ReactCompilerPass.saveLastOutputForTests = true;

    Result result = compiler.compile(externs, inputs, options);
    String lastOutput = "\n\nInput:\n" + inputJs + "\nCompiler pass output:\n" +
        ReactCompilerPass.lastOutputForTests + "\n";
    if (compiler.getRoot() != null) {
      // Use getSecondChild to skip over the externs root
      lastOutput += "Final compiler output:\n" +
          Debug.toTypeAnnotatedSource(
              compiler, compiler.getRoot().getSecondChild()) +
          "\n";
    }
    if (expectedError == null) {
      assertEquals(
          "Unexpected errors: " + Joiner.on(",").join(result.errors) +
              lastOutput,
          0, result.errors.size());
      assertEquals(
          "Unexpected warnings: " + Joiner.on(",").join(result.warnings) +
              lastOutput,
          0, result.warnings.size());
      assertTrue(result.success);
      if (expectedJs != null) {
        String actualJs = compiler.toSource();
        if (passOptions.optimizeForSize) {
          int inputIndex = actualJs.indexOf(ACTUAL_JS_INPUT_MARKER);
          assertNotEquals(-1, inputIndex);
          actualJs = actualJs.substring(
              inputIndex + ACTUAL_JS_INPUT_MARKER.length());
        }
        assertEquals(expectedJs, actualJs);
      }
    } else {
      assertFalse(
          "Expected failure, instead got output: " + compiler.toSource(),
          result.success);
      assertTrue(result.errors.size() > 0);
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
          0, result.warnings.size());
    }
  }
}
