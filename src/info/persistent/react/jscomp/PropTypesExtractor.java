package info.persistent.react.jscomp;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Map;

/**
 * Converts propTypes from React component specs into a record type that the
 * compiler than can then use to type check props accesses within the component
 * and instantiation via React.createElement.
 */
class PropTypesExtractor {
  private static final String GENERATED_SOURCE_NAME =
      "<PropTypesExtractor-generated.js>";
  private static final String REQUIRED_SUFFIX = ".isRequired";

  private static final String INSTANCE_OF_PREFIX = "React.PropTypes.instanceOf(";
  private static final String INSTANCE_OF_SUFFIX = ")";

  private static final String ARRAY_OF_PREFIX = "React.PropTypes.arrayOf(";
  private static final String ARRAY_OF_SUFFIX = ")";

  private static final String OBJECT_OF_PREFIX = "React.PropTypes.objectOf(";
  private static final String OBJECT_OF_SUFFIX = ")";

  private static final String ONE_OF_TYPE_PREFIX = "React.PropTypes.oneOfType([";
  private static final String ONE_OF_TYPE_SUFFIX = "])";

  // Maped to the required variant, the "null" and "undefined" union will be
  // added if the prop turns out not to be required.
  private static final Map<String, Node> SIMPLE_PROP_TYPES =
      ImmutableMap.<String, Node>builder()
          .put("array", bang(IR.string("Array")))
          .put("bool", IR.string("boolean"))
          .put("func", bang(IR.string("Function")))
          .put("number", IR.string("number"))
          .put("object", bang(IR.string("Object")))
          .put("string", IR.string("string"))
          .put("symbol", bang(IR.string("Symbol")))
          .put("any", pipe(
              IR.string("number"),
              IR.string("string"),
              IR.string("boolean"),
              bang(IR.string("Object"))))
          .put("node", bang(IR.string("ReactChild")))
          .put("element", bang(IR.string("ReactElement")))
          .build();
  private static final Node DEFAULT_PROP_TYPE = new Node(Token.STAR);

  static final DiagnosticType COULD_NOT_DETERMINE_PROP_TYPE = DiagnosticType.warning(
      "REACT_COULD_NOT_DETERMINE_PROP_TYPE",
      "Could not determine the prop type of prop {0} of the component {1}.");

  private final Node propTypesNode;
  private final String typeName;
  private final String interfaceTypeName;
  private final Compiler compiler;

  private final String propsTypeName;
  private final String validatorFuncName;
  private JSTypeExpression propsTypeExpression;

  public PropTypesExtractor(
      Node propTypesNode,
      String typeName,
      String interfaceTypeName,
      Compiler compiler) {
    this.propTypesNode = propTypesNode;
    this.typeName = typeName;
    this.interfaceTypeName = interfaceTypeName;
    this.compiler = compiler;
    this.propsTypeName = typeName + ".Props";
    // Generate a unique global function name (so that the compiler can more
    // easily see that it's a passthrough and inline and remove it).
    this.validatorFuncName =
        typeName.replaceAll("\\.", "\\$\\$") + "$$PropsValidator";
  }

  public static boolean canExtractPropTypes(Node propTypesNode) {
    return propTypesNode.hasOneChild() &&
        propTypesNode.getFirstChild().isObjectLit();
  }

  public void extract() {
    Node propTypesObjectLitNode = propTypesNode.getFirstChild();
    Node lb = new Node(Token.LB);
    for (Node propTypeKeyNode : propTypesObjectLitNode.children()) {
      Node colon = new Node(Token.COLON);
      Node member = propTypeKeyNode.cloneNode();
      colon.addChildToBack(member);
      Node memberType = convertPropTypeToTypeNode(
          propTypeKeyNode.getFirstChild());
      if (memberType != null) {
        colon.addChildToBack(memberType);
      } else {
        compiler.report(JSError.make(
            propTypeKeyNode,
            COULD_NOT_DETERMINE_PROP_TYPE,
            propTypeKeyNode.getString(),
            typeName));
        colon.addChildToBack(DEFAULT_PROP_TYPE.cloneTree());
      }
      lb.addChildToBack(colon);
    }
    Node propsTypeNode = new Node(Token.LC, lb);
    propsTypeExpression = new JSTypeExpression(
      propsTypeNode, GENERATED_SOURCE_NAME);
  }

  static Node convertPropTypeToTypeNode(Node propTypeNode) {
    String propTypeString = stringifyPropTypeNode(propTypeNode);
    if (propTypeString == null) {
      return null;
    }
    return convertPropTypeToTypeNode(propTypeString);
  }

  private static Node convertPropTypeToTypeNode(String propTypeString) {
    return convertPropTypeToTypeNode(propTypeString, false);
  }

  private static Node convertPropTypeToTypeNode(
      String propTypeString, boolean parentIsRequired) {
    boolean isRequired = propTypeString.endsWith(REQUIRED_SUFFIX);
    if (isRequired) {
      propTypeString = propTypeString.substring(
          0, propTypeString.length() - REQUIRED_SUFFIX.length());
    }
    if (parentIsRequired) {
      isRequired = true;
    }

    // Simple prop types to their equivalernt type.
    for (Map.Entry<String, Node> entry : SIMPLE_PROP_TYPES.entrySet()) {
      String simplePropType = "React.PropTypes." + entry.getKey();
      if (propTypeString.equals(simplePropType)) {
        Node propType = entry.getValue().cloneTree();
        if (isRequired) {
          return propType;
        }
        return pipe(propType, IR.string("undefined"), IR.string("null"));
      }
    }

    // React.PropTypes.instanceOf(<Class>) to <Class>
    if (propTypeString.startsWith(INSTANCE_OF_PREFIX) &&
        propTypeString.endsWith(INSTANCE_OF_SUFFIX)) {
      String objectType = propTypeString.substring(
          INSTANCE_OF_PREFIX.length(),
          propTypeString.length() - INSTANCE_OF_SUFFIX.length());
      Node propType = IR.string(objectType);
      if (isRequired) {
        return bang(propType);
      }
      return pipe(propType, IR.string("undefined"));
    }

    // React.PropTypes.arrayOf(<Type>) to Array<Type>
    if (propTypeString.startsWith(ARRAY_OF_PREFIX) &&
        propTypeString.endsWith(ARRAY_OF_SUFFIX)) {
      String arrayTypeString = propTypeString.substring(
          ARRAY_OF_PREFIX.length(),
          propTypeString.length() - ARRAY_OF_SUFFIX.length());
      Node arrayTypeNode = convertPropTypeToTypeNode(arrayTypeString);
      if (arrayTypeNode == null) {
        return null;
      }
      Node propType = IR.string("Array");
      propType.addChildToFront(IR.block());
      propType.getFirstChild().addChildToFront(arrayTypeNode);
      if (isRequired) {
        return bang(propType);
      }
      return pipe(propType, IR.string("undefined"));
    }

    // React.PropTypes.objectof(<Type>) to Object<Type>
    if (propTypeString.startsWith(OBJECT_OF_PREFIX) &&
        propTypeString.endsWith(OBJECT_OF_SUFFIX)) {
      String objectTypeString = propTypeString.substring(
          OBJECT_OF_PREFIX.length(),
          propTypeString.length() - OBJECT_OF_SUFFIX.length());
      Node objectTypeNode = convertPropTypeToTypeNode(objectTypeString);
      if (objectTypeNode == null) {
        return null;
      }
      Node propType = IR.string("Object");
      propType.addChildToFront(IR.block());
      propType.getFirstChild().addChildToFront(objectTypeNode);
      if (isRequired) {
        return bang(propType);
      }
      return pipe(propType, IR.string("undefined"));
    }

    // React.PropTypes.oneOfType([<Type1>, <Type2>, ...]) to (Type1|Type2|...)
    if (propTypeString.startsWith(ONE_OF_TYPE_PREFIX) &&
        propTypeString.endsWith(ONE_OF_TYPE_SUFFIX)) {
      String oneOfTypeString = propTypeString.substring(
          ONE_OF_TYPE_PREFIX.length(),
          propTypeString.length() - ONE_OF_TYPE_SUFFIX.length());
      String[] oneOfTypeStrings = oneOfTypeString.split(",");
      Node propType = new Node(Token.PIPE);
      for (String typeString : oneOfTypeStrings) {
        // Assume that the subtypes are required, we will add the undefined
        // and null if they are not.
        Node typeNode = convertPropTypeToTypeNode(typeString, true);
        if (typeNode == null) {
          return null;
        }
        propType.addChildToBack(typeNode);
      }
      if (!isRequired) {
        propType.addChildToBack(IR.string("undefined"));
        propType.addChildToBack(IR.string("null"));
      }
      return propType;
    }

    return null;
  }

  /**
   * Limited stringification (in the vein of Node.getQualifiedName()) that
   * handles the node types that we expect to see in prop type statements.
   */
  private static String stringifyPropTypeNode(Node node) {
    Node first = node.getFirstChild();
    Node last = node.getLastChild();
    switch (node.getToken()) {
      case NAME:
        String name = node.getString();
        return name.isEmpty() ? null : name;
      case GETPROP:
        String left = stringifyPropTypeNode(first);
        if (left == null) {
          return null;
        }
        return left + "." + last.getString();
      case CALL:
        if (node.getChildCount() != 2) {
          return null;
        }
        String callString = stringifyPropTypeNode(first);
        if (callString == null) {
          return null;
        }
        callString += "(";
        Node arg = first.getNext();
        String argString = stringifyPropTypeNode(arg);
        if (argString == null) {
          return null;
        }
        callString += argString;
        callString += ")";
        return callString;
      case ARRAYLIT:
        String arrayString = "[";
        for (Node child = first; child != null; child = child.getNext()) {
          String childString = stringifyPropTypeNode(child);
          if (childString == null) {
            return null;
          }
          if (child != first) {
            arrayString += ",";
          }
          arrayString += childString;
        }
        arrayString += "]";
        return arrayString;
      default:
        return null;
    }
  }

  public void insert(Node insertionPoint) {
    // /** @typedef {{
    //   propA: number,
    //   propB: string,
    //   ...
    // }} */
    // Comp.Props;
    JSDocInfoBuilder jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordTypedef(propsTypeExpression);
    Node propsTypedefNode = NodeUtil.newQName(compiler, propsTypeName);
    propsTypedefNode.setJSDocInfo(jsDocBuilder.build());
    propsTypedefNode = IR.exprResult(propsTypedefNode);
    propsTypedefNode.useSourceInfoIfMissingFromForTree(insertionPoint);
    insertionPoint.getParent().addChildAfter(propsTypedefNode, insertionPoint);
    insertionPoint = propsTypedefNode;

    // To type check React.createElement calls we wrap the "props" parameter
    // with a call to this function. This forces the compiler to check the
    // type of the parameter against the element's props (we can't do it via a
    // cast since the compiler allows any casts to/from record types).
    // The function is a no-op, so it will be removed by the inliner.
    // /**
    //  * @param {Comp.Props} props
    //  * @return {Comp.Props}
    //  */
    // CompPropsValidator = function(props) { return props; };
    Node validatorFuncNode = IR.function(
        IR.name(validatorFuncName),
        IR.paramList(IR.name("props")),
        IR.block(IR.returnNode(IR.name("props"))));
    jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordParameter(
        "props",
        new JSTypeExpression(IR.string(propsTypeName), GENERATED_SOURCE_NAME));
    jsDocBuilder.recordReturnType(new JSTypeExpression(
        IR.string(propsTypeName), GENERATED_SOURCE_NAME));
    validatorFuncNode.setJSDocInfo(jsDocBuilder.build());
    validatorFuncNode.useSourceInfoIfMissingFromForTree(insertionPoint);
    insertionPoint.getParent().addChildAfter(
        validatorFuncNode, insertionPoint);
    insertionPoint = validatorFuncNode;

    // /** @type {Comp.Props} */
    // CompInterface.prototype.props;
    jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordType(new JSTypeExpression(
        IR.string(propsTypeName), GENERATED_SOURCE_NAME));
    Node propsNode = NodeUtil.newQName(
        compiler, interfaceTypeName + ".prototype.props");
    propsNode.setJSDocInfo(jsDocBuilder.build());
    propsNode = IR.exprResult(propsNode);
    propsNode.useSourceInfoIfMissingFromForTree(insertionPoint);
    insertionPoint.getParent().addChildAfter(propsNode, insertionPoint);
  }

  public void visitReactCreateElement(Node callNode) {
    // Replaces
    // React.createElement(Comp, {...});
    // with:
    // React.createElement(Comp, Comp$$PropsValidator({...}));
    if (callNode.getChildCount() < 3) {
      return;
    }
    Node propsParamNode = callNode.getChildAtIndex(2);
    Node typeNode = callNode.getChildAtIndex(1);
    propsParamNode.detach();
    Node validatorCallNode = IR.call(
        IR.name(validatorFuncName), propsParamNode);
    validatorCallNode.useSourceInfoIfMissingFromForTree(propsParamNode);
    callNode.addChildAfter(validatorCallNode, typeNode);
  }

  private static Node bang(Node child) {
    return new Node(Token.BANG, child);
  }

  private static Node pipe(Node... children) {
    Node node = new Node(Token.PIPE);
    for (Node child : children) {
      node.addChildToBack(child);
    }
    return node;
  }
}
