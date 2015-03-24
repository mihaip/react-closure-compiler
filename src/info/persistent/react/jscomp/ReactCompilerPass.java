package info.persistent.react.jscomp;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.io.IOException;
import java.net.URL;
import java.util.Set;
import java.util.Map;

public class ReactCompilerPass extends NodeTraversal.AbstractPostOrderCallback
    implements HotSwapCompilerPass {

  // Errors
  static final DiagnosticType REACT_SOURCE_NOT_FOUND = DiagnosticType.error(
      "REACT_SOURCE_NOT_FOUND",
      "Could not find the React library source.");
  static final DiagnosticType CREATE_CLASS_TARGET_INVALID = DiagnosticType.error(
      "REACT_CREATE_CLASS_TARGET_INVALID",
      "Unsupported React.createClass(...) expression.");
  static final DiagnosticType CREATE_CLASS_SPEC_NOT_VALID = DiagnosticType.error(
      "REACT_CREATE_CLASS_SPEC_NOT_VALID",
      "The React.createClass(...) spec must be an object literal.");
  static final DiagnosticType CREATE_CLASS_UNEXPECTED_PARAMS = DiagnosticType.error(
      "REACT_CREATE_CLASS_UNEXPECTED_PARAMS",
      "The React.createClass(...) call has too many arguments.");
  static final DiagnosticType COULD_NOT_DETERMINE_TYPE_NAME = DiagnosticType.error(
      "REACT_COULD_NOT_DETERMINE_TYPE_NAME",
      "Could not determine the type name from a React.createClass(...) call.");
  static final DiagnosticType CREATE_ELEMENT_UNEXPECTED_PARAMS = DiagnosticType.error(
      "REACT_CREATE_ELEMENT_UNEXPECTED_PARAMS",
      "The React.createElement(...) call has too few arguments.");


  private static final String TYPES_JS_RESOURCE_PATH = "info/persistent/react/jscomp/types.js";
  private static final String EXTERNS_SOURCE_NAME = "<ReactCompilerPass-externs.js>";
  private static final String GENERATED_SOURCE_NAME = "<ReactCompilerPass-generated.js>";

  private final Compiler compiler;
  private final Set<String> reactClassTypeNames;

  public ReactCompilerPass(AbstractCompiler compiler) {
    this.compiler = (Compiler) compiler;
    this.reactClassTypeNames = Sets.newHashSet();
  }

  @Override
  public void process(Node externs, Node root) {
    reactClassTypeNames.clear();
    addExterns();
    addTypes(root);
    hotSwapScript(root, null);
  }

  /**
   * The compiler isn't aware of the React symbol that is exported from React,
   *  inform it via an extern. Equivalent to a file with:
   *
   * /**
   *  * @type {ReactStaticFunctions}
   *  * @const
   *  * /
   * var React;
   *
   * TODO(mihai): figure out a way to do this without externs, so that the
   * symbol can get renamed.
   */
  private void addExterns() {
    Node reactVarNode = IR.var(IR.name("React"));
    JSDocInfoBuilder jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordType(new JSTypeExpression(
        IR.string("ReactStaticFunctions"), EXTERNS_SOURCE_NAME));
    jsDocBuilder.recordConstancy();
    reactVarNode.setJSDocInfo(jsDocBuilder.build(reactVarNode));
    CompilerInput externsInput = compiler.newExternInput(EXTERNS_SOURCE_NAME);
    externsInput.getAstRoot(compiler).addChildrenToBack(reactVarNode);

    compiler.reportCodeChange();
  }

  /**
   * Cache parsed types AST across invocations.
   */
  private static Node templateTypesNode = null;

  /**
   * Parameter and return types for lifecycle methods, so that implementations
   * may be annotated automatically.
   */
  private static Map<String, JSDocInfo> lifecyleMethodJsDocs = Maps.newHashMap();

  /**
   * Inject React type definitions (we want these to get renamed, so they're
   * not part of the externs). {@link Compiler#getNodeForCodeInsertion()} is
   * package-private, so we instead add the types to the React source file.
   */
  private void addTypes(Node root) {
    if (templateTypesNode == null) {
      URL typesUrl = Resources.getResource(TYPES_JS_RESOURCE_PATH);
      String typesJs;
      try {
        typesJs = Resources.toString(typesUrl, Charsets.UTF_8);
      } catch (IOException e) {
        throw new RuntimeException(e); // Should never happen
      }
      templateTypesNode =
        compiler.parse(SourceFile.fromCode(TYPES_JS_RESOURCE_PATH, typesJs));
      Result result = compiler.getResult();
      if (!result.success) {
        throw new RuntimeException(
            "Could not parse " + TYPES_JS_RESOURCE_PATH + ": " +
            Joiner.on(",").join(result.errors));
      }
      for (Node child : templateTypesNode.children()) {
        if (child.isFunction() && NodeUtil.getNearestFunctionName(child) ==
            "ReactComponentLifecycle") {
          for (;child != null; child = child.getNext()) {
            if (!child.isExprResult()) {
              continue;
            }
            Node lifecycleMethodsObjectLit =
                child.getFirstChild().getChildAtIndex(1);
            if (!lifecycleMethodsObjectLit.isObjectLit()) {
              throw new RuntimeException(
                  "Did not find ReactComponentLifecycle.prototype object " +
                  "literal, instead found: " +
                  lifecycleMethodsObjectLit.toStringTree());
            }
            for (Node key : lifecycleMethodsObjectLit.children()) {
              lifecyleMethodJsDocs.put(
                  key.getString(), key.getFirstChild().getJSDocInfo());
            }
            break;
          }
        }
      }
    }

    Node typesNode = templateTypesNode.cloneTree();
    boolean foundReactSource = false;
    for (Node inputNode : root.children()) {
      if (inputNode.getType() == Token.SCRIPT &&
          inputNode.getSourceFileName() != null &&
          React.isReactSourceName(inputNode.getSourceFileName())) {
        Node typesChildren = typesNode.getFirstChild();
        typesNode.removeChildren();
        inputNode.addChildrenToFront(typesChildren);
        foundReactSource = true;
        break;
      }
    }
    if (!foundReactSource) {
      compiler.report(JSError.make(root, REACT_SOURCE_NOT_FOUND));
      return;
    }

    compiler.reportCodeChange();
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (isReactCreateClass(n)) {
      visitReactCreateClass(n);
    } else if (isReactCreateElement(n)) {
      visitReactCreateElement(t, n);
    }
  }

  private void visitReactCreateClass(Node callNode) {
    if (!validateCreateClassUsage(callNode)) {
      compiler.report(JSError.make(callNode, CREATE_CLASS_TARGET_INVALID));
      return;
    }
    int paramCount = callNode.getChildCount() - 1;
    if (paramCount > 1) {
      compiler.report(JSError.make(callNode, CREATE_CLASS_UNEXPECTED_PARAMS));
      return;
    }
    Node specNode = callNode.getChildAtIndex(1);
    if (specNode == null || !specNode.isObjectLit()) {
      compiler.report(JSError.make(specNode, CREATE_CLASS_SPEC_NOT_VALID));
      return;
    }

    // Mark the call as not having side effects, so that unused components can
    // be removed.
    callNode.setSideEffectFlags(Node.NO_SIDE_EFFECTS);

    // Turn the React.createClass call into a type definition for the Closure
    // compiler. Equivalent to generating the following code around the call:
    // /**
    //  * @interface
    //  * @extends {ReactComponent}
    //  */
    // function ComponentInterface() {}
    // ComponentInterface.prototype = {
    //     render: function() {},
    //     otherMethod: function() {}
    // };
    // /**
    //  * @typedef {ComponentInterface}
    //  */
    // var Component = React.createClass({
    //     render: function() {...},
    //     otherMethod: function() {...}
    // });
    //
    // The <type name>Interface type is necessary in order to teach the compiler
    // about all the methods that are present on the component. Having it as an
    // interface means that no extra code ends up being generated (and the
    // existing code is left untouched). The methods in the interface are just
    // stubs -- they have the same parameters (and JSDoc is ccopied over, if
    // any), but the body is empty.
    // The @typedef is added to the component variable so that user-authored
    // code can treat that as the type (the interface is an implementation
    // detail).
    Node callParentNode = callNode.getParent();
    String typeName;
    Node typeAttachNode;
    if (callParentNode.isName()) {
      typeName = callParentNode.getQualifiedName();
      typeAttachNode = callParentNode.getParent();
    } else if (callParentNode.isAssign() &&
        callParentNode.getFirstChild().isGetProp()) {
      typeName = callParentNode.getFirstChild().getQualifiedName();
      typeAttachNode = callParentNode;
    } else {
      compiler.report(JSError.make(callParentNode, COULD_NOT_DETERMINE_TYPE_NAME));
      return;
    }
    String interfaceTypeName = typeName + "Interface";

    // Add the @typedef
    JSDocInfoBuilder jsDocBuilder =
        JSDocInfoBuilder.maybeCopyFrom(typeAttachNode.getJSDocInfo());
    jsDocBuilder.recordTypedef(new JSTypeExpression(
        IR.string(interfaceTypeName),
        GENERATED_SOURCE_NAME));
    typeAttachNode.setJSDocInfo(jsDocBuilder.build(typeAttachNode));

    // Record the type so that we can later look it up in React.createElement
    // calls.
    reactClassTypeNames.add(typeName);

    // Gather methods for the interface definition.
    Node interfacePrototypeProps = IR.objectlit();
    for (Node key : specNode.children()) {
      if (key.getChildCount() != 1 || !key.getFirstChild().isFunction()) {
        continue;
      }
      Node func = key.getFirstChild();

      JSDocInfo lifecycleJsDoc = lifecyleMethodJsDocs.get(key.getString());
      if (lifecycleJsDoc != null) {
        JSDocInfoBuilder funcJsDocBuilder =
            JSDocInfoBuilder.maybeCopyFrom(func.getJSDocInfo());
        for (String parameterName : lifecycleJsDoc.getParameterNames()) {
          JSTypeExpression parameterType =
              lifecycleJsDoc.getParameterType(parameterName);
          funcJsDocBuilder.recordParameter(parameterName, parameterType);
        }
        if (lifecycleJsDoc.hasReturnType()) {
          funcJsDocBuilder.recordReturnType(lifecycleJsDoc.getReturnType());
        }
        func.setJSDocInfo(funcJsDocBuilder.build(func));
      }

      // Gather method signatures so that we can declare them were the compiler
      // can see them.
      Node methodNode = func.cloneNode();
      for (Node funcChild = func.getFirstChild();
           funcChild != null; funcChild = funcChild.getNext()) {
          if (funcChild.isParamList()) {
            methodNode.addChildToBack(funcChild.cloneTree());
          } else {
            methodNode.addChildToBack(funcChild.cloneNode());
          }
      }
      interfacePrototypeProps.addChildrenToBack(
        IR.stringKey(key.getString(), methodNode));

      // Add a @this {<type name>} annotation to all methods in the spec, to
      // avoid the compiler complaining dangerous use of "this" in a global
      // context.
      jsDocBuilder = JSDocInfoBuilder.maybeCopyFrom(func.getJSDocInfo());
      jsDocBuilder.recordThisType(new JSTypeExpression(
        IR.string(typeName), GENERATED_SOURCE_NAME));
      func.setJSDocInfo(jsDocBuilder.build(func));
    }

    // Generate the interface definition.
    Node interfaceTypeFunctionNode =
      IR.function(IR.name(""), IR.paramList(), IR.block());
    Node interfaceTypeNode = NodeUtil.newQNameDeclaration(
        compiler,
        interfaceTypeName,
        interfaceTypeFunctionNode,
        null);
    jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordInterface();
    jsDocBuilder.recordExtendedInterface(new JSTypeExpression(
        new Node(Token.BANG, IR.string("ReactComponent")),
        GENERATED_SOURCE_NAME));
    interfaceTypeFunctionNode.setJSDocInfo(jsDocBuilder.build(interfaceTypeFunctionNode));
    Node interfaceTypeInsertionPoint = callParentNode.getParent();
    interfaceTypeInsertionPoint.getParent().addChildBefore(
        interfaceTypeNode, interfaceTypeInsertionPoint);
    interfaceTypeInsertionPoint.getParent().addChildAfter(
        NodeUtil.newQNameDeclaration(
            compiler,
            interfaceTypeName + ".prototype",
            interfacePrototypeProps,
            null),
        interfaceTypeNode);
  }

  private static boolean isReactCreateClass(Node value) {
    if (value != null && value.isCall()) {
      return value.getFirstChild().matchesQualifiedName("React.createClass");
    }
    return false;
  }

  private boolean validateCreateClassUsage(Node n) {
    // There are only two valid usage patterns for of React.createClass
    //   var ClassName = React.createClass({...})
    //   namespace.ClassName = React.createClass({...})
    Node parent = n.getParent();
    switch (parent.getType()) {
      case Token.NAME:
        return true;
      case Token.ASSIGN:
        return n == parent.getLastChild() && parent.getParent().isExprResult();
    }
    return false;
  }

  private void visitReactCreateElement(NodeTraversal t, Node callNode) {
    int paramCount = callNode.getChildCount() - 1;
    if (paramCount == 0) {
      compiler.report(JSError.make(callNode, CREATE_ELEMENT_UNEXPECTED_PARAMS));
      return;
    }
    Node typeNode = callNode.getChildAtIndex(1);
    if (typeNode.isString()) {
      // TODO(mihai): add type annotations for DOM element creation
      return;
    }

    if (callNode.getParent().getType() == Token.CAST) {
      // There's already a cast around the call, there's no need to add another.
      return;
    }

    // Add casts of the form /** @type {ReactElement.<type name>} */ around
    // React.createElement calls, so that the return value of React.render will
    // have the correct type.
    // It's too expensive to know what the type parameter node actually refers
    // to, so instead we assume that it directly references the type (this is
    // the most common case, especially with JSX). This means that we will not
    // add type annotations for cases such as:
    // var typeAlias = SomeType;
    // React.createElement(typeAlias);
    String typeName = typeNode.getQualifiedName();
    if (!reactClassTypeNames.contains(typeName)) {
      return;
    }
    Node elementTypeExpressionNode = IR.string("ReactElement");
    elementTypeExpressionNode.addChildToFront(IR.block());
    elementTypeExpressionNode.getFirstChild().addChildToFront(IR.string(typeName));
    Node castNode = IR.cast(callNode.cloneTree());
    JSDocInfoBuilder jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordType(new JSTypeExpression(
        elementTypeExpressionNode, GENERATED_SOURCE_NAME));
    castNode.setJSDocInfo(jsDocBuilder.build(castNode));
    callNode.getParent().replaceChild(callNode, castNode);
  }

  private static boolean isReactCreateElement(Node value) {
    if (value != null && value.isCall()) {
      return value.getFirstChild().matchesQualifiedName("React.createElement");
    }
    return false;
  }
}

