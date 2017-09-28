package info.persistent.react.jscomp;

import com.google.common.base.Predicate;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.NamedType;
import com.google.javascript.rhino.jstype.PrototypeObjectType;
import com.google.javascript.rhino.jstype.UnionType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts propTypes from React component specs into a record type that the
 * compiler than can then use to type check props accesses within the component
 * and instantiation via React.createElement.
 */
class PropTypesExtractor {
  private static final String REQUIRED_SUFFIX = ".isRequired";

  private static final String INSTANCE_OF_PREFIX = "React.PropTypes.instanceOf(";
  private static final String INSTANCE_OF_SUFFIX = ")";

  private static final String ARRAY_OF_PREFIX = "React.PropTypes.arrayOf(";
  private static final String ARRAY_OF_SUFFIX = ")";

  private static final String OBJECT_OF_PREFIX = "React.PropTypes.objectOf(";
  private static final String OBJECT_OF_SUFFIX = ")";

  private static final String ONE_OF_TYPE_PREFIX = "React.PropTypes.oneOfType([";
  private static final String ONE_OF_TYPE_SUFFIX = "])";

  private static final String SHAPE_PREFIX = "React.PropTypes.shape({";
  private static final String SHAPE_SUFFIX = "})";

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

  static final DiagnosticType NO_CHILDREN_ARGUMENT = DiagnosticType.warning(
      "REACT_NO_CHILDREN_ARGUMENT",
      "{0} has a 'children' propType but is created without any children");

  static final DiagnosticType PROP_TYPES_VALIDATION_MISMATCH = DiagnosticType.warning(
      "REACT_PROP_TYPES_VALIDATION_MISMATCH",
      "Invalid props provided when creating a {0} element:\n{1}");

  private static final String PROPS_VALIDATOR_SUFFIX = "$$PropsValidator";
  private static final String CHILDREN_VALIDATOR_SUFFIX = "$$ChildrenValidator";

  private final Node propTypesNode;
  private final Node getDefaultPropsNode;
  private final String sourceFileName;
  private final String typeName;
  private final String interfaceTypeName;
  private final Compiler compiler;

  private final String validatorFuncName;
  private Node validatorFuncNode;
  private List<Prop> props;
  private boolean canBeCreatedWithNoProps;
  private final String childrenValidatorFuncName;
  private Node childrenPropTypeNode;

  public PropTypesExtractor(
      Node propTypesNode,
      Node getDefaultPropsNode,
      String typeName,
      String interfaceTypeName,
      Compiler compiler) {
    this.propTypesNode = propTypesNode;
    this.sourceFileName = propTypesNode.getSourceFileName();
    this.getDefaultPropsNode = getDefaultPropsNode;
    this.typeName = typeName;
    this.interfaceTypeName = interfaceTypeName;
    this.compiler = compiler;
    // Generate a unique global function name (so that the compiler can more
    // easily see that it's a passthrough and inline and remove it).
    String sanitizedTypeName = typeName.replaceAll("\\.", "\\$\\$");
    this.validatorFuncName = sanitizedTypeName + PROPS_VALIDATOR_SUFFIX;
    this.childrenValidatorFuncName =
        sanitizedTypeName + CHILDREN_VALIDATOR_SUFFIX;
    this.childrenPropTypeNode = null;
  }

  static String getTypeNameForFunctionName(String functionName) {
    String sanitizedTypeName = null;
    if (functionName.endsWith(PROPS_VALIDATOR_SUFFIX)) {
      sanitizedTypeName = functionName.substring(
          0, functionName.length() - PROPS_VALIDATOR_SUFFIX.length());
    }
    if (sanitizedTypeName == null) {
      return null;
    }
    return sanitizedTypeName.replaceAll("\\$\\$", ".");
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

  private static class Prop {
    public Prop(Node propTypeKeyNode, PropType propType, boolean hasDefaultValue) {
      this.propTypeKeyNode = propTypeKeyNode;
      this.propType = propType;
      this.hasDefaultValue = hasDefaultValue;
    }

    private final Node propTypeKeyNode;
    private final PropType propType;
    private final boolean hasDefaultValue;
  }

  public void extract() {
    Set<String> propsWithDefaultValues;
    if (getDefaultPropsNode != null) {
      propsWithDefaultValues = getPropsWithDefaultValues(getDefaultPropsNode);
    } else {
      propsWithDefaultValues = Collections.emptySet();
    }

    Node propTypesObjectLitNode = propTypesNode.getFirstChild();
    props = Lists.newArrayListWithCapacity(
        propTypesObjectLitNode.getChildCount());
    canBeCreatedWithNoProps = true;

    for (Node propTypeKeyNode : propTypesObjectLitNode.children()) {
      String propName = propTypeKeyNode.getString();
      PropType propType = null;
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
          Node optionalPropTypeNode = isRequired ?
            pipe(propTypeNode, IR.string("undefined"), IR.string("null")) :
            propTypeNode;
          propType = new PropType(
            optionalPropTypeNode, propTypeNode, isRequired);
        }
      }
      if (propType == null) {
        propType = convertPropType(propTypeKeyNode.getFirstChild());
      }
      if (propType == null) {
        compiler.report(JSError.make(
            propTypeKeyNode,
            COULD_NOT_DETERMINE_PROP_TYPE,
            propName,
            typeName));
        propType = new PropType(
            DEFAULT_PROP_TYPE.cloneTree(), DEFAULT_PROP_TYPE.cloneTree(),
            false);
      }
      if (propName.equals("children")) {
        // The "children" propType is a bit special, since it's not passed in
        // directly via the "props" argument to React.createElement. It doesn't
        // usually show up in propTypes, except for the pattern of requiring
        // a single child (https://goo.gl/961UCF).
        childrenPropTypeNode = propType.typeNode;
        continue;
      }
      boolean hasDefaultValue = propsWithDefaultValues.contains(propName);
      if (propType.isRequired && !hasDefaultValue) {
        canBeCreatedWithNoProps = false;
      }
      props.add(new Prop(propTypeKeyNode, propType, hasDefaultValue));
    }
  }

  /**
   * Assumes the pattern:
   *   getDefaultProps: function() {
   *     return {propName: ...};
   *   }
   */
  private static Set<String> getPropsWithDefaultValues(Node getDefaultPropsNode) {
    Node getDefaultPropsValueNode = getDefaultPropsNode.getFirstChild();
    if (!getDefaultPropsValueNode.isFunction()) {
      return Collections.emptySet();
    }

    Node getDefaultPropsBlockNode = getDefaultPropsValueNode.getLastChild();
    if (!getDefaultPropsBlockNode.isBlock()) {
      return Collections.emptySet();
    }

    Node getDefaultPropsReturnNode = getDefaultPropsBlockNode.getLastChild();
    if (!getDefaultPropsReturnNode.isReturn()) {
      return Collections.emptySet();
    }

    Node getDefaultPropsReturnValueNode =
        getDefaultPropsReturnNode.getFirstChild();
    if (getDefaultPropsReturnValueNode == null ||
        !getDefaultPropsReturnValueNode.isObjectLit()) {
      return Collections.emptySet();
    }

    Set<String> result = Sets.newHashSetWithExpectedSize(
        getDefaultPropsReturnValueNode.getChildCount());
    for (Node keyNode : getDefaultPropsReturnValueNode.children()) {
      if (keyNode.isString() || keyNode.isStringKey()) {
        result.add(keyNode.getString());
      }
    }
    return result;
  }

  static class PropType {
    PropType(
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

  static PropType convertPropType(Node propTypeNode) {
    String propTypeString = stringifyPropTypeNode(propTypeNode);
    if (propTypeString == null) {
      return null;
    }
    return convertPropType(propTypeString);
  }

  private static PropType convertPropType(String propTypeString) {
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
        return new PropType(
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
      return new PropType(
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
      PropType arrayTypeResult = convertPropType(arrayTypeString);
      if (arrayTypeResult == null) {
        return null;
      }
      Node propType = IR.string("Array");
      propType.addChildToFront(IR.block());
      propType.getFirstChild().addChildToFront(arrayTypeResult.typeNode);
      return new PropType(
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
      PropType objectTypeResult = convertPropType(objectTypeString);
      if (objectTypeResult == null) {
        return null;
      }
      Node propType = IR.string("Object");
      propType.addChildToFront(IR.block());
      propType.getFirstChild().addChildToFront(objectTypeResult.typeNode);
      return new PropType(
          pipe(propType, IR.string("undefined")),
          bang(propType.cloneTree()),
          isRequired);
    }

    // React.PropTypes.oneOfType([<Type1>,<Type2>,...]) to (Type1|Type2|...)
    if (propTypeString.startsWith(ONE_OF_TYPE_PREFIX) &&
        propTypeString.endsWith(ONE_OF_TYPE_SUFFIX)) {
      String oneOfTypeString = propTypeString.substring(
          ONE_OF_TYPE_PREFIX.length(),
          propTypeString.length() - ONE_OF_TYPE_SUFFIX.length());
      String[] oneOfTypeStrings = oneOfTypeString.split(",");
      Node propType = new Node(Token.PIPE);
      for (String typeString : oneOfTypeStrings) {
        PropType typeResult = convertPropType(typeString);
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
      return new PropType(optionalPropType, propType, isRequired);
    }

    // React.PropTypes.shape({prop1:<Type1>,prop2:<Type2>,...]) to
    // {prop1:Type1,prop2:Type2}
    if (propTypeString.startsWith(SHAPE_PREFIX) &&
        propTypeString.endsWith(SHAPE_SUFFIX)) {
      String shapeString = propTypeString.substring(
          SHAPE_PREFIX.length(),
          propTypeString.length() - SHAPE_SUFFIX.length());
      String[] shapeStrings = shapeString.split(",");
      Node lb = new Node(Token.LB);
      for (String typeString : shapeStrings) {
        String[] typeStringPieces = typeString.split(":", 2);
        if (typeStringPieces.length != 2) {
          return null;
        }
        PropType typeResult = convertPropType(typeStringPieces[1]);
        if (typeResult == null) {
          return null;
        }
        Node colon = new Node(Token.COLON);
        colon.addChildToBack(IR.stringKey(typeStringPieces[0]));
        colon.addChildToBack(typeResult.typeNode);
        lb.addChildToBack(colon);
      }
      Node propType = new Node(Token.LC, lb);
      Node optionalPropType =
          pipe(propType.cloneTree(), IR.string("undefined"), IR.string("null"));
      return new PropType(optionalPropType, propType, isRequired);
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
      case OBJECTLIT:
        String objectString = "{";
        for (Node child = first; child != null; child = child.getNext()) {
          String childString = stringifyPropTypeNode(child.getFirstChild());
          if (childString == null) {
            return null;
          }
          if (child != first) {
            objectString += ",";
          }
          objectString += child.getString() + ":" + childString;
        }
        objectString += "}";
        return objectString;
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
    String propsTypeName = typeName + ".Props";
    Node propsTypedefNode = getPropsTypedefNode(propsTypeName, false);
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
    // Props that are required but have default values can be missing at
    // creation time, so for those cases we need to create an alternate type
    // that allows them to be skipped.
    String validatorPropsTypeName = propsTypeName;
    boolean needsCustomValidatorType = Iterables.any(props, new Predicate<Prop>() {
      @Override public boolean apply(Prop prop) {
        return prop.hasDefaultValue;
      }
    });
    if (needsCustomValidatorType) {
      validatorPropsTypeName = typeName + ".CreateProps";
      Node validatorPropsTypedefNode = getPropsTypedefNode(validatorPropsTypeName, true);
      validatorPropsTypedefNode.useSourceInfoIfMissingFromForTree(insertionPoint);
      insertionPoint.getParent().addChildAfter(validatorPropsTypedefNode, insertionPoint);
      insertionPoint = validatorPropsTypedefNode;
    }
    validatorFuncNode = IR.function(
        IR.name(validatorFuncName),
        IR.paramList(IR.name("props")),
        IR.block(IR.returnNode(IR.name("props"))));
    JSDocInfoBuilder jsDocBuilder = new JSDocInfoBuilder(true);
    Node propsTypeNode = IR.string(validatorPropsTypeName);
    if (canBeCreatedWithNoProps) {
      propsTypeNode = new Node(Token.QMARK, propsTypeNode);
    }
    jsDocBuilder.recordParameter(
        "props",
        new JSTypeExpression(propsTypeNode, sourceFileName));
    jsDocBuilder.recordReturnType(
        new JSTypeExpression(propsTypeNode, sourceFileName));
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
          new JSTypeExpression(childrenPropTypeNode, sourceFileName));
      jsDocBuilder.recordReturnType(
          new JSTypeExpression(childrenPropTypeNode, sourceFileName));
      childrenValidatorFuncNode.setJSDocInfo(jsDocBuilder.build());
      childrenValidatorFuncNode.useSourceInfoIfMissingFromForTree(insertionPoint);
      insertionPoint.getParent().addChildAfter(
          childrenValidatorFuncNode, insertionPoint);
      insertionPoint = childrenValidatorFuncNode;
    }

    // /** @type {Comp.Props} */
    // CompInterface.prototype.props;
    jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordType(
        new JSTypeExpression(IR.string(propsTypeName), sourceFileName));
    Node propsNode = NodeUtil.newQName(
        compiler, interfaceTypeName + ".prototype.props");
    propsNode.setJSDocInfo(jsDocBuilder.build());
    propsNode = IR.exprResult(propsNode);
    propsNode.useSourceInfoIfMissingFromForTree(insertionPoint);
    insertionPoint.getParent().addChildAfter(propsNode, insertionPoint);
    insertionPoint = propsNode;
  }

  private Node getPropsTypedefNode(String name, boolean forValidator) {
    Node lb = new Node(Token.LB);
    for (Prop prop : props) {
      PropType propType = prop.propType;
      Node colon = new Node(Token.COLON);
      Node member = prop.propTypeKeyNode.cloneNode();
      colon.addChildToBack(member);
      Node typeNode;
      if (forValidator && propType.isRequired && prop.hasDefaultValue) {
        typeNode = propType.optionalTypeNode;
      } else if (!forValidator && !propType.isRequired &&
          prop.hasDefaultValue) {
        // If a prop is not required but it has a default value then its type
        // inside the component can be treated as required.
        typeNode = propType.requiredTypeNode;
      } else {
        typeNode = propType.typeNode;
      }
      if (typeNode.getParent() != null) {
        // We have already used this node in the regular typedef, so we now
        // need to use a copy.
        typeNode = typeNode.cloneTree();
      }
      colon.addChildToBack(typeNode);
      colon.useSourceInfoFromForTree(prop.propTypeKeyNode);
      lb.addChildToBack(colon);
    }
    Node propsRecordTypeNode = new Node(Token.LC, lb);
    JSDocInfoBuilder jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordTypedef(new JSTypeExpression(
        propsRecordTypeNode, sourceFileName));
    Node propsTypedefNode = NodeUtil.newQName(compiler, name);
    propsTypedefNode.setJSDocInfo(jsDocBuilder.build());
    propsTypedefNode = IR.exprResult(propsTypedefNode);
    return propsTypedefNode;
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
      // If it's a Object.asign() call (created because of a spread operator)
      // then add the validator to object literal parameters instead.
      String functionName = propsParamNode.getFirstChild().getQualifiedName();
      if (functionName != null && functionName.equals("Object.assign") &&
          propsParamNode.getChildCount() > 1) {
        for (Node spreadParamNode = propsParamNode.getChildAtIndex(1);
            spreadParamNode != null;
            spreadParamNode = spreadParamNode.getNext()) {
          if (spreadParamNode.isObjectLit() && spreadParamNode.hasChildren()) {
            Node prevNode = spreadParamNode.getPrevious();
            spreadParamNode.detach();
            Node validatorCallNode = IR.call(
                IR.name(validatorFuncName), spreadParamNode);
            validatorCallNode.useSourceInfoIfMissingFrom(spreadParamNode);
            propsParamNode.addChildAfter(validatorCallNode, prevNode);
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

  JSError generatePropTypesError(Node paramNode) {
    JSType paramType = paramNode.getJSType();
    if (paramType == null) {
      return null;
    }
    JSType validatorFuncType = validatorFuncNode.getJSType();
    if (!(validatorFuncType instanceof FunctionType)) {
      return null;
    }
    JSType expectedType = ((FunctionType) validatorFuncType).getReturnType();
    if (expectedType == null) {
      return null;
    }
    if (paramType.isSubtype(expectedType)) {
      return null;
    }
    // Unpack the union with null if elements can be created with no props.
    if (canBeCreatedWithNoProps && expectedType instanceof UnionType) {
        UnionType unionExpectedType = (UnionType) expectedType;
        for (JSType expectedAlternateType : unionExpectedType.getAlternates()) {
            if (!expectedAlternateType.isNullType()) {
                expectedType = expectedAlternateType;
                break;
            }
        }
    }
    if (expectedType instanceof NamedType) {
        expectedType = ((NamedType) expectedType).getReferencedType();
    }
    if (!(paramType instanceof PrototypeObjectType)) {
      return null;
    }
    if (!(expectedType instanceof PrototypeObjectType)) {
      return null;
    }
    PrototypeObjectType expectedRecordType = (PrototypeObjectType) expectedType;
    PrototypeObjectType paramRecordType = (PrototypeObjectType) paramType;
    if (expectedRecordType == null || paramRecordType == null) {
      return null;
    }
    List<String> errors = Lists.newArrayList();
    for (String property : expectedRecordType.getPropertyNames()) {
      JSType expectedPropertyType =
          expectedRecordType.getPropertyType(property);
      if (!paramRecordType.hasProperty(property)) {
        if (!expectedPropertyType.isExplicitlyVoidable()) {
          errors.add("\"" + property + "\" was missing, expected to be of " +
              "type " + expectedPropertyType);
        }
        continue;
      }
      JSType paramPropertyType = paramRecordType.getPropertyType(property);
      if (!paramPropertyType.isSubtype(expectedPropertyType)) {
          errors.add("\"" + property + "\" was expected to be of type " +
              expectedPropertyType + ", instead was " +paramPropertyType);
      }
    }

    if (errors.isEmpty()) {
      return null;
    }

    return JSError.make(paramNode, PROP_TYPES_VALIDATION_MISMATCH, typeName,
        "  " + Joiner.on("\n  ").join(errors));
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
