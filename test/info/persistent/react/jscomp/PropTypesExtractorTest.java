package info.persistent.react.jscomp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;

import org.junit.Test;

/**
 * Test {@link PropTypesExtractor}.
 */
public class PropTypesExtractorTest {
  @Test public void testConvertPropTypeToTypeNode() {
    testPropType("React.PropTypes.array.isRequired", "!Array");
    testPropType("React.PropTypes.array", "(!Array|undefined|null)");

    testPropType("React.PropTypes.bool.isRequired", "boolean");
    testPropType("React.PropTypes.bool", "(boolean|undefined|null)");

    testPropType("React.PropTypes.func.isRequired", "!Function");
    testPropType("React.PropTypes.func", "(!Function|undefined|null)");

    testPropType("React.PropTypes.number.isRequired", "number");
    testPropType("React.PropTypes.number", "(number|undefined|null)");

    testPropType("React.PropTypes.object.isRequired", "!Object");
    testPropType("React.PropTypes.object", "(!Object|undefined|null)");

    testPropType("React.PropTypes.string.isRequired", "string");
    testPropType("React.PropTypes.string", "(string|undefined|null)");

    testPropType("React.PropTypes.symbol.isRequired", "!Symbol");
    testPropType("React.PropTypes.symbol", "(!Symbol|undefined|null)");

    testPropType(
        "React.PropTypes.any",
        "((number|string|boolean|!Object)|undefined|null)");
    testPropType(
        "React.PropTypes.any.isRequired",
        "(number|string|boolean|!Object)");

    testPropType("React.PropTypes.node.isRequired", "!ReactChild");
    testPropType("React.PropTypes.node", "(!ReactChild|undefined|null)");

    testPropType("React.PropTypes.element.isRequired", "!ReactElement");
    testPropType("React.PropTypes.element", "(!ReactElement|undefined|null)");

    testPropType(
        "React.PropTypes.instanceOf(Message).isRequired", "!Message");
    testPropType(
        "React.PropTypes.instanceOf(Message)", "(Message|undefined)");

    testPropType("React.PropTypes.oneOfType([" +
        "React.PropTypes.string," +
        "React.PropTypes.number," +
        "React.PropTypes.instanceOf(Message)" +
    "]).isRequired", "(string|number|!Message)");
    testPropType("React.PropTypes.oneOfType([" +
        "React.PropTypes.string," +
        "React.PropTypes.number," +
        "React.PropTypes.instanceOf(Message)" +
    "])", "(string|number|!Message|undefined|null)");

    testPropType(
        "React.PropTypes.arrayOf(React.PropTypes.number.isRequired).isRequired",
        "!Array<number>");
    testPropType(
        "React.PropTypes.arrayOf(React.PropTypes.number.isRequired)",
        "(Array<number>|undefined)");
    testPropType(
        "React.PropTypes.arrayOf(" +
            "React.PropTypes.instanceOf(Message).isRequired).isRequired",
        "!Array<!Message>");

    testPropType(
        "React.PropTypes.objectOf(React.PropTypes.number.isRequired).isRequired",
        "!Object<number>");
    testPropType(
        "React.PropTypes.objectOf(React.PropTypes.number.isRequired)",
        "(Object<number>|undefined)");

    testPropType(
        "React.PropTypes.shape({" +
            "label: React.PropTypes.string.isRequired," +
            "handler: React.PropTypes.func.isRequired" +
        "}).isRequired",
        "{" +
            "label:string," +
            "handler:!Function" +
        "}");
    testPropType(
        "React.PropTypes.shape({" +
            "label: React.PropTypes.string.isRequired," +
            "handler: React.PropTypes.func.isRequired" +
        "})",
        "({" +
            "label:string," +
            "handler:!Function" +
        "}|undefined|null)");
  }

  private void testPropType(String reactPropType, String typeExpression) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    // So that source dumps still have JSDoc
    options.preserveTypeAnnotations = true;
    compiler.initOptions(options);
    // Avoid extra "use strict" boilerplate in output.
    options.setEmitUseStrict(false);
    compiler.disableThreads(); // Makes errors easier to track down.
    Node reactPropTypeNode = compiler.parse(
        SourceFile.fromCode("/src/test.js", reactPropType))
        .getFirstChild().getFirstChild();
    assertArrayEquals(new JSError[]{}, compiler.getErrors());

    Node typeNode = PropTypesExtractor.convertPropType(reactPropTypeNode)
        .typeNode;

    // Easiest way to stringify the type node is to print it out as JSDoc.
    JSDocInfoBuilder jsDocInfoBuilder = new JSDocInfoBuilder(true);
    jsDocInfoBuilder.recordType(new JSTypeExpression(
        typeNode, "/src/test.js"));
    Node tempNode = IR.var(IR.name("temp"));
    tempNode.setJSDocInfo(jsDocInfoBuilder.build());
    String tempCode = compiler.toSource(tempNode);

    assertEquals("/** @type {" + typeExpression + "} */ var temp", tempCode);
  }
}
