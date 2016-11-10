package info.persistent.react.jscomp;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
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

  static final DiagnosticType NO_CHILDREN_ARGUMENT = DiagnosticType.error(
      "REACT_NO_CHILDREN_ARGUMENT",
      "{0} has a 'children' propType but is created without any children");

  private final Node propTypesNode;
  private final String typeName;
  private final String interfaceTypeName;
  private final Compiler compiler;

  private final String propsTypeName;
  private final String validatorFuncName;
  private JSTypeExpression propsTypeExpression;
  private boolean allPropsAreOptional;
  private final String childrenValidatorFuncName;
  private Node childrenPropTypeNode;

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
    String sanitizedTypeName = typeName.replaceAll("\\.", "\\$\\$");
    this.validatorFuncName = sanitizedTypeName + "$$PropsValidator";
    this.childrenValidatorFuncName = sanitizedTypeName + "$$ChildrenValidator";
    this.childrenPropTypeNode = null;
  }

  public static boolean canExtractPropTypes(Node propTypesNode) {
    return propTypesNode.hasOneChild() &&
        propTypesNode.getFirstChild().isObjectLit();
  }

  /**
   * If we're not doing propType checking we should still clean up the
   * propTypes node in case there were type checking-related types that
   * were added.
   */
  public static void cleanUpPropTypesWhenNotChecking(Node propTypesNode) {
    Node propTypesObjectLitNode = propTypesNode.getFirstChild();
    for (Node propTypeKeyNode : propTypesObjectLitNode.children()) {
      if (propTypeKeyNode.getJSDocInfo() != null) {
        JSDocInfo propTypeJsDoc = propTypeKeyNode.getJSDocInfo();
        if (propTypeJsDoc.hasType()) {
            propTypeKeyNode.setJSDocInfo(null);
        }
      }
    }
  }

  public void extract() {
    allPropsAreOptional = true;
    Node propTypesObjectLitNode = propTypesNode.getFirstChild();
    Node lb = new Node(Token.LB);
    for (Node propTypeKeyNode : propTypesObjectLitNode.children()) {
      Node colon = new Node(Token.COLON);
      Node member = propTypeKeyNode.cloneNode();
      colon.addChildToBack(member);
      ConversionResult memberTypeResult = null;
      // Allow the type for a prop to be explicitly defined via a @type JSDoc
      // annotation on the key.
      if (propTypeKeyNode.getJSDocInfo() != null) {
        JSDocInfo propTypeJsDoc = propTypeKeyNode.getJSDocInfo();
        if (propTypeJsDoc.hasType()) {
            Node propTypeNode = propTypeJsDoc.getType().getRoot();
            // Infer whether the prop is required or not by looking at whether
            // it's a union with undefined or null.
            boolean isRequired = true;
            if (propTypeNode.getToken() == Token.PIPE) {
              for (Node child = propTypeNode.getFirstChild();
                  child != null;
                  child = child.getNext()) {
                if (child.getToken() == Token.QMARK ||
                    (child.isString() &&
                        (child.getString().equals("null") ||
                        child.getString().equals("undefined")))) {
                  isRequired = false;
                  break;
                }
              }
            }
            // Remove the custom type, otherwise the compiler will complain that
            // the key is not typed correctly.
            propTypeKeyNode.setJSDocInfo(null);
            memberTypeResult = new ConversionResult(
                propTypeNode, propTypeNode, isRequired);
        }
      }
      if (memberTypeResult == null) {
        memberTypeResult = convertPropType(propTypeKeyNode.getFirstChild());
      }
      if (memberTypeResult != null) {
        if (propTypeKeyNode.getString().equals("children")) {
          // The "children" propType is a bit special, since it's not passed in
          // directly via the "props" argument to React.createElement. It doesn't
          // usually show up in propTypes, except for the pattern of requiring
          // a single child (https://goo.gl/961UCF).
          childrenPropTypeNode = memberTypeResult.typeNode;
          continue;
        }
        if (memberTypeResult.isRequired) {
          allPropsAreOptional = false;
        }
        colon.addChildToBack(memberTypeResult.typeNode);
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

  static class ConversionResult {
    ConversionResult(
          Node optionalTypeNode,
          Node requiredTypeNode,
          boolean isRequired) {
      this.typeNode = isRequired ? requiredTypeNode : optionalTypeNode;
      this.optionalTypeNode = optionalTypeNode;
      this.requiredTypeNode = requiredTypeNode;
      this.isRequired = isRequired;
    }

    public final Node typeNode;
    public final Node optionalTypeNode;
    public final Node requiredTypeNode;
    public final boolean isRequired;
  }

  static ConversionResult convertPropType(Node propTypeNode) {
    String propTypeString = stringifyPropTypeNode(propTypeNode);
    if (propTypeString == null) {
      return null;
    }
    return convertPropType(propTypeString);
  }

  private static ConversionResult convertPropType(String propTypeString) {
    boolean isRequired = propTypeString.endsWith(REQUIRED_SUFFIX);
    if (isRequired) {
      propTypeString = propTypeString.substring(
          0, propTypeString.length() - REQUIRED_SUFFIX.length());
    }

    // Simple prop types to their equivalernt type.
    for (Map.Entry<String, Node> entry : SIMPLE_PROP_TYPES.entrySet()) {
      String simplePropType = "React.PropTypes." + entry.getKey();
      if (propTypeString.equals(simplePropType)) {
        Node propType = entry.getValue().cloneTree();
        return new ConversionResult(
            pipe(propType, IR.string("undefined"), IR.string("null")),
            propType.cloneTree(),
            isRequired);
      }
    }

    // React.PropTypes.instanceOf(<Class>) to <Class>
    if (propTypeString.startsWith(INSTANCE_OF_PREFIX) &&
        propTypeString.endsWith(INSTANCE_OF_SUFFIX)) {
      String objectType = propTypeString.substring(
          INSTANCE_OF_PREFIX.length(),
          propTypeString.length() - INSTANCE_OF_SUFFIX.length());
      Node propType = IR.string(objectType);
      return new ConversionResult(
          pipe(propType, IR.string("undefined")),
          bang(propType.cloneTree()),
          isRequired);
    }

    // React.PropTypes.arrayOf(<Type>) to Array<Type>
    if (propTypeString.startsWith(ARRAY_OF_PREFIX) &&
        propTypeString.endsWith(ARRAY_OF_SUFFIX)) {
      String arrayTypeString = propTypeString.substring(
          ARRAY_OF_PREFIX.length(),
          propTypeString.length() - ARRAY_OF_SUFFIX.length());
      ConversionResult arrayTypeResult = convertPropType(arrayTypeString);
      if (arrayTypeResult == null) {
        return null;
      }
      Node propType = IR.string("Array");
      propType.addChildToFront(IR.block());
      propType.getFirstChild().addChildToFront(arrayTypeResult.typeNode);
      return new ConversionResult(
          pipe(propType, IR.string("undefined")),
          bang(propType.cloneTree()),
          isRequired);
    }

    // React.PropTypes.objectof(<Type>) to Object<Type>
    if (propTypeString.startsWith(OBJECT_OF_PREFIX) &&
        propTypeString.endsWith(OBJECT_OF_SUFFIX)) {
      String objectTypeString = propTypeString.substring(
          OBJECT_OF_PREFIX.length(),
          propTypeString.length() - OBJECT_OF_SUFFIX.length());
      ConversionResult objectTypeResult = convertPropType(objectTypeString);
      if (objectTypeResult == null) {
        return null;
      }
      Node propType = IR.string("Object");
      propType.addChildToFront(IR.block());
      propType.getFirstChild().addChildToFront(objectTypeResult.typeNode);
      return new ConversionResult(
          pipe(propType, IR.string("undefined")),
          bang(propType.cloneTree()),
          isRequired);
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
        ConversionResult typeResult = convertPropType(typeString);
        if (typeResult == null) {
          return null;
        }
        // Assume that the subtypes are required, we will add the undefined
        // and null if they are not.
        propType.addChildToBack(typeResult.requiredTypeNode);
      }
      Node optionalPropType = propType.cloneTree();
      optionalPropType.addChildToBack(IR.string("undefined"));
      optionalPropType.addChildToBack(IR.string("null"));
      return new ConversionResult(optionalPropType, propType, isRequired);
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
    Node propsTypeNode = IR.string(propsTypeName);
    if (allPropsAreOptional) {
      propsTypeNode = new Node(Token.QMARK, propsTypeNode);
    }
    jsDocBuilder.recordParameter(
        "props",
        new JSTypeExpression(propsTypeNode, GENERATED_SOURCE_NAME));
    jsDocBuilder.recordReturnType(
        new JSTypeExpression(propsTypeNode, GENERATED_SOURCE_NAME));
    validatorFuncNode.setJSDocInfo(jsDocBuilder.build());
    validatorFuncNode.useSourceInfoIfMissingFromForTree(insertionPoint);
    insertionPoint.getParent().addChildAfter(
        validatorFuncNode, insertionPoint);
    insertionPoint = validatorFuncNode;

    // A similar validator function is also necessary to validate the children
    // parameter of React.createElement.
    if (childrenPropTypeNode != null) {
      Node childrenValidatorFuncNode = IR.function(
          IR.name(childrenValidatorFuncName),
          IR.paramList(IR.name("children")),
          IR.block(IR.returnNode(IR.name("children"))));
      jsDocBuilder = new JSDocInfoBuilder(true);
      jsDocBuilder.recordParameter(
          "children",
          new JSTypeExpression(childrenPropTypeNode, GENERATED_SOURCE_NAME));
      jsDocBuilder.recordReturnType(new JSTypeExpression(
          childrenPropTypeNode, GENERATED_SOURCE_NAME));
      childrenValidatorFuncNode.setJSDocInfo(jsDocBuilder.build());
      childrenValidatorFuncNode.useSourceInfoIfMissingFromForTree(insertionPoint);
      insertionPoint.getParent().addChildAfter(
          childrenValidatorFuncNode, insertionPoint);
      insertionPoint = childrenValidatorFuncNode;
    }

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
    insertionPoint = propsNode;
  }

  public void visitReactCreateElement(Node callNode) {
    int callParamCount = callNode.getChildCount() - 1;
    // Replaces
    // React.createElement(Comp, {...});
    // with:
    // React.createElement(Comp, Comp$$PropsValidator({...}));
    if (callParamCount < 2) {
      return;
    }
    Node propsParamNode = callNode.getChildAtIndex(2);
    if (propsParamNode.isObjectLit() || propsParamNode.isNull()) {
      Node typeNode = callNode.getChildAtIndex(1);
      propsParamNode.detach();
      Node validatorCallNode = IR.call(
          IR.name(validatorFuncName), propsParamNode);
      validatorCallNode.useSourceInfoIfMissingFrom(propsParamNode);
      callNode.addChildAfter(validatorCallNode, typeNode);
    } else if (propsParamNode.isCall()) {
      // If it's a React.__spread() call then add the validator to the first
      // object literal parameter instead (it should have all of the explicitly
      // specified props).
      String functionName = propsParamNode.getFirstChild().getQualifiedName();
      if (functionName != null && functionName.equals("React.__spread") &&
          propsParamNode.getChildCount() > 1) {
        for (Node spreadParamNode = propsParamNode.getChildAtIndex(1);
            spreadParamNode != null;
            spreadParamNode = spreadParamNode.getNext()) {
          if (spreadParamNode.isObjectLit()) {
            Node prevNode = spreadParamNode.getPrevious();
            spreadParamNode.detach();
            Node validatorCallNode = IR.call(
                IR.name(validatorFuncName), spreadParamNode);
            validatorCallNode.useSourceInfoIfMissingFrom(spreadParamNode);
            propsParamNode.addChildAfter(validatorCallNode, prevNode);
            break;
          }
        }
      }
    }

    // It's more difficult to validate multiple children, but that use case is
    // uncommon.
    if (childrenPropTypeNode != null) {
      if (callParamCount == 3) {
        Node childParamNode = callNode.getChildAtIndex(3);
        childParamNode.detach();
        Node childValidatorCallNode = IR.call(
            IR.name(childrenValidatorFuncName), childParamNode);
        childValidatorCallNode.useSourceInfoIfMissingFrom(childParamNode);
        callNode.addChildAfter(
          childValidatorCallNode, callNode.getChildAtIndex(2));
      } else {
        compiler.report(JSError.make(callNode, NO_CHILDREN_ARGUMENT, typeName));
      }
    }
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
