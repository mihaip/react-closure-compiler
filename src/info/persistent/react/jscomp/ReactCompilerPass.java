package info.persistent.react.jscomp;

import info.persistent.jscomp.Ast;
import info.persistent.jscomp.Debug;

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerAccessor;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.DiagnosticGroup;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.JsAst;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.Scope;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoAccessor;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Node.SideEffectFlags;
import com.google.javascript.rhino.Token;

import java.util.function.BiConsumer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReactCompilerPass implements NodeTraversal.Callback,
      HotSwapCompilerPass {

  // Errors
  static final DiagnosticType CREATE_TYPE_TARGET_INVALID = DiagnosticType.error(
      "REACT_CREATE_CLASS_TARGET_INVALID",
      "Unsupported {0}(...) expression.");
  static final DiagnosticType CREATE_TYPE_SPEC_NOT_VALID = DiagnosticType.error(
      "REACT_CREATE_TYPE_SPEC_NOT_VALID",
      "The {0}(...) spec must be an object literal.");
  static final DiagnosticType CREATE_TYPE_UNEXPECTED_PARAMS = DiagnosticType.error(
      "REACT_CREATE_TYPE_UNEXPECTED_PARAMS",
      "The {0}(...) call has too many arguments.");
  static final DiagnosticType COULD_NOT_DETERMINE_TYPE_NAME = DiagnosticType.error(
      "REACT_COULD_NOT_DETERMINE_TYPE_NAME",
      "Could not determine the type name from a {0}(...) call.");
  static final DiagnosticType UNSUPPORTED_REACT_CLASS = DiagnosticType.error(
      "UNSUPPORTED_REACT_CLASS",
      "Unsupported React class: {0}.");
  static final DiagnosticType MIXINS_UNEXPECTED_TYPE = DiagnosticType.error(
      "REACT_MIXINS_UNEXPECTED_TYPE",
      "The \"mixins\" value must be an array literal.");
  static final DiagnosticType MIXIN_EXPECTED_NAME = DiagnosticType.error(
      "REACT_MIXIN_EXPECTED_NAME",
      "The \"mixins\" array literal must contain only mixin names.");
  static final DiagnosticType MIXIN_UNKNOWN = DiagnosticType.error(
      "REACT_MIXIN_UNKNOWN",
      "Could not find a mixin with the name {0}");
  static final DiagnosticType CREATE_ELEMENT_UNEXPECTED_PARAMS = DiagnosticType.error(
      "REACT_CREATE_ELEMENT_UNEXPECTED_PARAMS",
      "The React.createElement(...) call has too few arguments.");
  static final DiagnosticType STATICS_UNEXPECTED_TYPE = DiagnosticType.error(
      "REACT_STATICS_UNEXPECTED_TYPE",
      "The \"statics\" value must be an object literal.");
  static final DiagnosticType PURE_RENDER_MIXIN_SHOULD_COMPONENT_UPDATE_OVERRIDE =
      DiagnosticType.error(
          "REACT_PURE_RENDER_MIXIN_SHOULD_COMPONENT_UPDATE_OVERRIDE",
          "{0} uses React.addons.PureRenderMixin, it should not define shouldComponentUpdate.");
  static final DiagnosticType UNEXPECTED_EXPORT_SYNTAX = DiagnosticType.error(
      "REACT_UNEXPECTED_EXPORT_SYNTAX",
      "Unexpected export syntax.");
  static final DiagnosticType JSDOC_REQUIRED_FOR_STATICS = DiagnosticType.error(
      "REACT_JSDOC_REQUIRED_FOR_STATICS",
      "JSDoc is required for static property {0}.");

  public static final DiagnosticGroup MALFORMED_MIXINS = new DiagnosticGroup(
        MIXINS_UNEXPECTED_TYPE, MIXIN_EXPECTED_NAME, MIXIN_UNKNOWN);

  private static final String REACT_PURE_RENDER_MIXIN_NAME =
      "React.addons.PureRenderMixin";
  private static final String CREATE_ELEMENT_ALIAS_NAME = "React$createElement";
  private static final String CREATE_CLASS_ALIAS_NAME = "React$createClass";
  private static final String COMPONENT_ALIAS_NAME = "React$Component";
  private static final String PURE_COMPONENT_ALIAS_NAME = "React$PureComponent";
  static final String PROP_TYPES_ALIAS_NAME = "React$PropTypes";

  private final Compiler compiler;
  private final Options options;
  private Node externsRoot;
  private final SymbolTable<Node> reactClassesByName = new SymbolTable<>();
  private final SymbolTable<Node> reactClassInterfacePrototypePropsByName =
      new SymbolTable<>();
  private final SymbolTable<MixinRef> reactMixinsByName = new SymbolTable<>();
  private final SymbolTable<Node> reactMixinInterfacePrototypePropsByName =
      new SymbolTable<>();
  // Mixin name -> method name -> JSDoc
  private final SymbolTable<Map<String, JSDocInfo>>
      mixinAbstractMethodJsDocsByName = new SymbolTable<>();
  private final SymbolTable<PropTypesExtractor> propTypesExtractorsByName =
      new SymbolTable<>();
  private final SymbolTable<ClassOutOfBoundsData> classOutOfBoundsMap =
      new SymbolTable<>();
  private final List<Node> reactCreateElementNodes = Lists.newArrayList();

  // Make debugging test failures easier by allowing the processed output to
  // be inspected.
  static boolean saveLastOutputForTests = false;
  static String lastOutputForTests;

  public static class Options {
    // TODO: flip default once all known issues are resolved
    public boolean propTypesTypeChecking = false;
    // If running with a minified build of React additional size optimizations
    // are applied to the generated code too.
    public boolean optimizeForSize = false;
  }

  /**
   * When using ES6 classes for react we have lots of manipulations of the class
   * that is done outside of the class body. This data is gathered during that
   * tree traversal and used when we exit the module/script to do the actual
   * transformations.
   */
  private static class ClassOutOfBoundsData {
    boolean addModuleExports;
    boolean isExportedType;
    List<Node> componentMethodKeys;
    Node propTypesNode;
    Node contextTypesNode;
    Node defaultPropsNode;
    Node insertionPoint;
    Node nameNode;
    List<String> exportedNames;
    String typeName;

    ClassOutOfBoundsData(
        Node insertionPoint,
        Node nameNode,
        boolean addModuleExports,
        boolean isExportedType, 
        List<Node> componentMethodKeys,
        List<String> exportedNames) {
      this.insertionPoint = insertionPoint;
      this.nameNode = nameNode;
      this.addModuleExports = addModuleExports;
      this.isExportedType = isExportedType;
      this.componentMethodKeys = componentMethodKeys;
      this.exportedNames = exportedNames;
      typeName = nameNode.getQualifiedName();
    }
  }

  public ReactCompilerPass(AbstractCompiler compiler) {
    this(compiler, new Options());
  }

  public ReactCompilerPass(AbstractCompiler compiler, Options options) {
    this.compiler = (Compiler) compiler;
    this.options = options;
  }

  @Override
  public void process(Node externs, Node root) {
    reactClassesByName.clear();
    reactClassInterfacePrototypePropsByName.clear();
    reactMixinsByName.clear();
    reactMixinInterfacePrototypePropsByName.clear();
    mixinAbstractMethodJsDocsByName.clear();
    propTypesExtractorsByName.clear();
    addExterns();
    if (options.optimizeForSize) {
      addReactApiAliases(root);
    }
    hotSwapScript(root, null);
    if (saveLastOutputForTests) {
      lastOutputForTests = Debug.toTypeAnnotatedSource(compiler, root);
    } else {
      lastOutputForTests = null;
    }
  }

  /**
   * The compiler isn't aware of the React* symbols that are exported from
   * React, inform it via an extern.
   */
  private void addExterns() {
    CompilerInput externsInput =
        CompilerAccessor.getSynthesizedExternsInputAtEnd(compiler);
    externsRoot = externsInput.getAstRoot(compiler);
    Node typesNode = createTypesNode();
    typesNode.useSourceInfoFromForTree(externsRoot);
    Node typesChildren = typesNode.getFirstChild();
    typesNode.removeChildren();
    externsRoot.addChildrenToBack(typesChildren);
    compiler.reportChangeToEnclosingScope(externsRoot);
  }

  private void addReactApiAliases(Node root) {
    // Insert our own script, so that we can make sure we're not using module
    // scoping rules (and the aliases end up in the global scope).
    JsAst aliasesAst = new JsAst(SourceFile.fromCode("react-api-aliases.js", ""));
    // We can't use compiler.addNewScript because that will cause all compiler
    // passes to be run on the new input, but we're not at the point where the
    // ES6 module map (or other metadata) has been built.
    CompilerAccessor.addNewSourceAst(compiler, aliasesAst);
    Node insertionPoint = aliasesAst.getAstRoot(compiler);
    Node insertionParent = insertionPoint.getParent();
    // Move the script to the front (addNewSourceAst adds it to the back) so
    // that the symbols are guaranteed to be defined in all other files.
    insertionPoint.detach();
    insertionParent.addChildToFront(insertionPoint);
    // Add an alias of the form:
    // /** @type {Function} */
    // var React$createElement = React.createElement;
    // Normally React.createElement calls are not renamed at all, due to
    // React being an extern and createElement showing up in the built-in
    // browser DOM externs. By adding an alias and then rewriting calls
    // (see visitReactCreateElement) we allow the compiler to rename the
    // function used at all the calls. This is most beneficial before
    // gzip, but when after gzip there is still some benefit.
    // The Function type is necessary to convince the compiler that we
    // don't need the "this" type to be defined when calling the alias
    // (it thinks that React is an instance of the ReactModule type, but
    // it's actually a static namespace, so we can use unbound functions
    // from it)
    Node createElementAliasNode = IR.var(
        IR.name(CREATE_ELEMENT_ALIAS_NAME),
        IR.getprop(
            IR.name("React"),
            IR.string("createElement")));
    JSDocInfoBuilder jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordType(new JSTypeExpression(
        IR.string("Function"), insertionPoint.getSourceFileName()));
    createElementAliasNode.setJSDocInfo(jsDocBuilder.build());
    insertionPoint.addChildToBack(createElementAliasNode);

    // Same thing for React.createClass, which is not as frequent, but shows up
    // often enough that shortening it is worthwhile.
    Node createClassAliasNode = IR.var(
        IR.name(CREATE_CLASS_ALIAS_NAME),
        IR.getprop(
            IR.name("React"),
            IR.string("createClass")));
    jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordType(new JSTypeExpression(
        IR.string("Function"), insertionPoint.getSourceFileName()));
    createClassAliasNode.setJSDocInfo(jsDocBuilder.build());
    insertionPoint.addChildToBack(createClassAliasNode);
    // Also add an alias for React.PropTypes, which may still be present if we're
    // preserving propTypes via @struct.
    Node propTypesAliasNode = IR.var(
        IR.name(PROP_TYPES_ALIAS_NAME),
        IR.getprop(
            IR.name("React"),
            IR.string("PropTypes")));
    jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordType(new JSTypeExpression(
        IR.string("ReactPropTypes"), insertionPoint.getSourceFileName()));
    jsDocBuilder.recordNoInline();
    propTypesAliasNode.setJSDocInfo(jsDocBuilder.build());
    insertionPoint.addChildToBack(propTypesAliasNode);

    // And for React.Component
    Node componentAliasNode = IR.var(
        IR.name(COMPONENT_ALIAS_NAME),
        IR.getprop(
            IR.name("React"),
            IR.string("Component")));
    jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordType(new JSTypeExpression(
        IR.typeof(IR.string("React.Component")), insertionPoint.getSourceFileName()));
    jsDocBuilder.recordNoInline();
    componentAliasNode.setJSDocInfo(jsDocBuilder.build());
    insertionPoint.addChildToBack(componentAliasNode);

    // And for React.PureComponent
    Node pureComponentAliasNode = IR.var(
        IR.name(PURE_COMPONENT_ALIAS_NAME),
        IR.getprop(
            IR.name("React"),
            IR.string("PureComponent")));
    jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordType(new JSTypeExpression(
        IR.typeof(IR.string("React.PureComponent")), insertionPoint.getSourceFileName()));
    jsDocBuilder.recordNoInline();
    pureComponentAliasNode.setJSDocInfo(jsDocBuilder.build());
    insertionPoint.addChildToBack(pureComponentAliasNode);
    
    compiler.reportChangeToEnclosingScope(insertionPoint);
  }

  /**
   * Cache parsed types AST across invocations.
   */
  private static Node templateTypesNode = null;

  /**
   * Parameter and return types for built-in component methods, so that
   * implementations may be annotated automatically.
   */
  private static Map<String, JSDocInfo> componentMethodJsDocs =
      Maps.newHashMap();

  private Node createTypesNode() {
    if (templateTypesNode == null) {
      String typesJs = React.getTypesJs();
      Result previousResult = compiler.getResult();
      templateTypesNode =
        compiler.parse(SourceFile.fromCode(React.TYPES_JS_RESOURCE_PATH, typesJs));
      Result result = compiler.getResult();
      if ((result.success != previousResult.success && previousResult.success) ||
          result.errors.size() > previousResult.errors.size() ||
          result.warnings.size() > previousResult.warnings.size()) {
        String message = "Could not parse " + React.TYPES_JS_RESOURCE_PATH + ".";
        if (result.errors.size() > 0) {
          message += "\nErrors: " + Joiner.on(",").join(result.errors);
        }
        if (result.warnings.size() > 0) {
          message += "\nWarnings: " + Joiner.on(",").join(result.warnings);
        }
        throw new RuntimeException(message);
      }
      // Gather ReactComponent prototype methods.
      NodeTraversal.traverse(
          compiler,
          templateTypesNode,
          new NodeTraversal.AbstractPostOrderCallback() {
            @Override public void visit(NodeTraversal t, Node n, Node parent) {
              if (!n.isAssign() || !n.getFirstChild().isQualifiedName() ||
                  !n.getFirstChild().getQualifiedName().startsWith(
                      "ReactComponent.prototype.") ||
                  !n.getLastChild().isFunction()) {
                  return;
              }
              componentMethodJsDocs.put(
                  n.getFirstChild().getLastChild().getString(),
                  n.getJSDocInfo());
            }
          });
    }
    return templateTypesNode.cloneTree();
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
    // Inline React.createMixin calls, since they're just decorators.
    for (MixinRef mixinRef : reactMixinsByName.values()) {
      Node mixinSpecNode = mixinRef.node;
      Node mixinSpecParentNode = mixinSpecNode.getParent();
      if (mixinSpecParentNode.isCall() &&
          mixinSpecParentNode.hasMoreThanOneChild() &&
          mixinSpecParentNode.getFirstChild().getQualifiedName().equals(
            "React.createMixin")) {
        mixinSpecNode.detachFromParent();
        mixinSpecParentNode.getParent().replaceChild(
          mixinSpecParentNode,
          mixinSpecNode);
        compiler.reportChangeToEnclosingScope(mixinSpecNode.getParent());
      }
    }
    if (options.optimizeForSize) {
      for (Node classSpecNode : reactClassesByName.values()) {
        Node functionNameNode = classSpecNode.getPrevious();
        if (functionNameNode.getToken() == Token.GETPROP) {
          functionNameNode.replaceWith(IR.name(CREATE_CLASS_ALIAS_NAME));
        }
      }
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
      Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    Scope scope = t.getScope();
    if (isReactCreateClass(n)) {
      visitReactCreateClass(t, n);
    } else if (isReactCreateMixin(n)) {
      visitReactCreateMixin(t, n);
    } else if (visitMixinAbstractMethod(t, n)) {
      // Nothing more needs to be done, mixin abstract method processing is
      // more efficiently done in one function intead of two.
    } else if (isReactCreateElement(n)) {
      // We have to defer dealing with this until we are done with the script
      // because the propTypes and defaultProps might be out of line.
      reactCreateElementNodes.add(n);
    } else if (isReactPropTypes(n)) {
      visitReactPropTypes(n);
    } else if (isClassExtendsReactComponent(n)) {
      visitClassExtendsReactComponent(t, n, reactClassInterfacePrototypePropsByName);
    } else if (isStaticPropTypes(scope, n)) {
      visitStaticPropTypes(scope, n);
    } else if (isStaticDefaultProps(scope, n)) {
      visitStaticDefaultProps(scope, n);
    } else if (isStaticContextTypes(scope, n)) {
      visitStaticContextTypes(scope, n);
    } else if (n.isModuleBody() || n.isScript()) {
      handleOutOfBoundsData(t, n);
    }
    // TODO(arv): Handle alias of React.Component and PureComponent
  }

  private void handleOutOfBoundsData(NodeTraversal t, Node n) {
    // When using class syntax things like propTypes, defaultProps, contextType
    // are added after the class body since ES did not get class properties
    // until ES2021(?) (still in stage 3 at the time of this writing and not yet
    // supported by Closure Compiler.)
    CompilerInput moduleExportInput = t.getScope().isModuleScope() ? t.getInput() : null;
    for (ClassOutOfBoundsData data : classOutOfBoundsMap.values()) {
      transformPropTypesForClass(data, moduleExportInput);
      synthesizeExterns(data.exportedNames, data.typeName);
    }

    for (Node createElementNode : reactCreateElementNodes) {
      visitReactCreateElement(t.getScope(), createElementNode);
    }

    classOutOfBoundsMap.clear();
    reactCreateElementNodes.clear();
  }

  private boolean isStaticDefaultProps(Scope scope, Node n) {
    return isStaticProperty(scope, n, "defaultProps");
  }

  private boolean isStaticPropTypes(Scope scope, Node n) {
    return isStaticProperty(scope, n, "propTypes");
  }

  private boolean isStaticContextTypes(Scope scope, Node n) {
    return isStaticProperty(scope, n, "contextTypes");
  }

  private boolean isStaticProperty(Scope scope, Node n, String propName) {
    if (!n.isExprResult()) {
      return false;
    }
    n = n.getFirstChild();    
    if (n.isAssign() && n.getFirstChild().isGetProp() && n.getLastChild().isObjectLit()) {
      Node lhs = n.getFirstChild();
      if (!lhs.getLastChild().getString().equals(propName)) {
        return false;
      }
      Node classNameNode = lhs.getFirstChild();
      return reactClassesByName.containsName(scope, classNameNode);
    }
    return false;
  }

  private void visitStaticProperty(Scope scope, Node exprResult,
      BiConsumer<ClassOutOfBoundsData, Node> updater) {
    Node assignmentNode = exprResult.getFirstChild();
    Node lhs = assignmentNode.getFirstChild();
    Node classNameNode = lhs.getFirstChild();
    Node classBody = reactClassesByName.get(scope, classNameNode);
    if (classBody == null) {
      return;
    }
    ClassOutOfBoundsData outOfBoundsData = classOutOfBoundsMap.get(scope, classNameNode);
    if (outOfBoundsData == null) {
      return;
    }
    Node rhs = assignmentNode.getLastChild();
    updater.accept(outOfBoundsData, rhs);
  }

  private void visitStaticPropTypes(Scope scope, Node exprResult) {
    visitStaticProperty(scope, exprResult, (ClassOutOfBoundsData data, Node rhs) -> {
      data.propTypesNode = rhs;
    });
  }

  private void visitStaticDefaultProps(Scope scope, Node exprResult) {
    visitStaticProperty(scope, exprResult, (ClassOutOfBoundsData data, Node rhs) -> {
      data.defaultPropsNode = rhs;
    });
  }

  private void visitStaticContextTypes(Scope scope, Node exprResult) {
    visitStaticProperty(scope, exprResult, (ClassOutOfBoundsData data, Node rhs) -> {
      data.contextTypesNode = rhs;
    });
  }

  private void transformPropTypesForClass(ClassOutOfBoundsData data, 
      CompilerInput moduleExportInput) {
    Node classNameNode = data.nameNode;
    Node insertionNode = data.insertionPoint;
    Node propTypesNode = data.propTypesNode;
    Node defaultPropsNode = data.defaultPropsNode;
    Node contextTypesNode = data.contextTypesNode;
    String typeName = data.typeName;

    if (data.isExportedType && propTypesNode != null) {
      for (Node propTypeKeyNode : propTypesNode.children()) {
        data.exportedNames.add(propTypeKeyNode.getString());
      }
    }

    if (options.propTypesTypeChecking) {
      Map<Node, PropTypesExtractor> mixedInPropTypes = Maps.newHashMap();
      if (contextTypesNode != null) {
        PropTypesExtractor extractor = new PropTypesExtractor(
            contextTypesNode, null, typeName, typeName,
            mixedInPropTypes, compiler, true);
        extractor.extract();
        extractor.insert(insertionNode, data.addModuleExports);
      }

      if (propTypesNode != null) {
        PropTypesExtractor extractor = new PropTypesExtractor(
            propTypesNode, defaultPropsNode, typeName, typeName,
            mixedInPropTypes, compiler);
        extractor.extract();
        extractor.insert(insertionNode, data.addModuleExports);
        extractor.addToComponentMethods(data.componentMethodKeys);
        propTypesExtractorsByName.put(classNameNode, extractor, moduleExportInput);
      }
    } else {
      if (propTypesNode != null) {
        PropTypesExtractor.cleanUpPropTypesWhenNotChecking(propTypesNode);
      }
      if (contextTypesNode != null) {
        PropTypesExtractor.cleanUpPropTypesWhenNotChecking(contextTypesNode);
      }
    }
  }

  private static boolean isReactCreateClass(Node value) {
    if (value != null && value.isCall()) {
      return value.getFirstChild().matchesQualifiedName("React.createClass");
    }
    return false;
  }

  private void visitReactCreateClass(NodeTraversal t, Node callNode) {
    visitReactCreateType(
        t,
        callNode,
        "React.createClass",
        reactClassesByName,
        reactClassInterfacePrototypePropsByName);
  }

  private static boolean isReactCreateMixin(Node value) {
    if (value != null && value.isCall()) {
      return value.getFirstChild().matchesQualifiedName("React.createMixin");
    }
    return false;
  }

  private void visitReactCreateMixin(NodeTraversal t, Node callNode) {
    SymbolTable<Node> tempTable = new SymbolTable<>();
    visitReactCreateType(
        t,
        callNode,
        "React.createMixin",
        tempTable,
        reactMixinInterfacePrototypePropsByName);
    tempTable.mapValuesInto(
        mixinNode -> new MixinRef(mixinNode, t.getScope()),
        reactMixinsByName);
  }

  private void visitReactCreateType(
        NodeTraversal t,
        Node callNode,
        String createFuncName,
        SymbolTable<Node> typeSpecNodesByName,
        SymbolTable<Node> interfacePrototypePropsByName) {
    if (!validateCreateTypeUsage(callNode)) {
      compiler.report(JSError.make(
          callNode, CREATE_TYPE_TARGET_INVALID, createFuncName));
      return;
    }
    int paramCount = callNode.getChildCount() - 1;
    if (paramCount > 1) {
      compiler.report(JSError.make(
          callNode, CREATE_TYPE_UNEXPECTED_PARAMS, createFuncName));
      return;
    }
    Node specNode = callNode.getChildAtIndex(1);
    if (specNode == null || !specNode.isObjectLit()) {
      compiler.report(JSError.make(
          specNode, CREATE_TYPE_SPEC_NOT_VALID, createFuncName));
      return;
    }

    // Mark the call as not having side effects, so that unused components and
    // mixins can be removed.
    callNode.setSideEffectFlags(SideEffectFlags.NO_SIDE_EFFECTS);

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
    // var Component = /** @typedef {ComponentInterface} */ (React.createClass({
    //     render: function() {...},
    //     otherMethod: function() {...}
    // }));
    //
    // /**
    //  * @typedef {ReactElement.<Component>}
    //  */
    // var ComponentElement;
    //
    // The <type name>Interface type is necessary in order to teach the compiler
    // about all the methods that are present on the component. Having it as an
    // interface means that no extra code ends up being generated (and the
    // existing code is left untouched). The methods in the interface are just
    // stubs -- they have the same parameters (and JSDoc is copied over, if
    // any), but the body is empty.
    // The @typedef is added to the component variable so that user-authored
    // code can treat that as the type (the interface is an implementation
    // detail).
    // The <type name>Element @typedef is designed to make adding types to
    // elements for that component less verbose.
    Node callParentNode = callNode.getParent();
    Node typeNameNode;
    Node typeAttachNode;
    if (callParentNode.isName()) {
      typeNameNode = callParentNode;
      typeAttachNode = callParentNode.getParent();
    } else if (callParentNode.isAssign() &&
        callParentNode.getFirstChild().isGetProp()) {
      typeNameNode = callParentNode.getFirstChild();
      typeAttachNode = callParentNode;
    } else {
      compiler.report(JSError.make(
          callParentNode, COULD_NOT_DETERMINE_TYPE_NAME, createFuncName));
      return;
    }
    String typeName = typeNameNode.getQualifiedName();
    String interfaceTypeName = generateInterfaceTypeName(t, typeNameNode);

    // Check to see if the type has an ES6 module export. We assume this is
    // of the form `export const Comp = React.createClass(...)`. If it is, then
    // we transform it into `const Comp = React.createClass(...); export {Comp};`.
    // That way it's more similar to non-module uses (as far as where we can
    // add additional nodes to the AST) and the @typedef that we use for the
    // declaration does not prevent it from getting exported (Es6RewriteModules
    // does not export the values of typedefs).
    // Additionally, if the component is exported, then we also need to export
    // types that we generate from it (CompInterface, CompElement).
    boolean addModuleExports = false;
    CompilerInput moduleExportInput =
        t.getScope().isModuleScope() ? t.getInput() : null;
    for (Node ancestor : callNode.getAncestors()) {
      if (!ancestor.isExport()) {
        continue;
      }
      addModuleExports = true;
      Node exportNode = ancestor;
      if (!callParentNode.isName() ||
          !NodeUtil.isNameDeclaration(callParentNode.getParent())) {
        compiler.report(
          JSError.make(callParentNode, UNEXPECTED_EXPORT_SYNTAX));
        return;
      }
      Node nameNode = callParentNode;
      Node declarationNode = nameNode.getParent();
      declarationNode.detachFromParent();
      exportNode.replaceWith(declarationNode);
      Ast.addModuleExport(typeName, declarationNode);
      break;
    }

    // For components tagged with @export don't rename their props or public
    // methods.
    JSDocInfo jsDocInfo = NodeUtil.getBestJSDocInfo(callNode);
    boolean isExportedType = jsDocInfo != null && jsDocInfo.isExport();
    // "Clear" the @export bit, otherwise the compiler will complain about its
    // presence on module-scoped variables.
    if (isExportedType && t.getScope().isModuleScope()) {
      JSDocInfoAccessor.setJSDocExport(jsDocInfo, false);
    }

    List<JSTypeExpression> implementedInterfaces = jsDocInfo != null ?
        jsDocInfo.getImplementedInterfaces() :
        Collections.<JSTypeExpression>emptyList();

    // Add the @typedef
    JSDocInfoBuilder jsDocBuilder = newJsDocInfoBuilderForNode(typeAttachNode);
    jsDocBuilder.recordTypedef(new JSTypeExpression(
        IR.string(interfaceTypeName), callNode.getSourceFileName()));
    typeAttachNode.setJSDocInfo(jsDocBuilder.build());

    // Also add a cast of the form /** @type {typeof CompInterface} */ around
    // the component spec. This is necessary to get the compiler to understand
    // static properties when using strict missing property checks.
    jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordTypedef(new JSTypeExpression(
        IR.string(interfaceTypeName), callNode.getSourceFileName()));
    jsDocBuilder.recordType(new JSTypeExpression(
        IR.typeof(IR.string(interfaceTypeName)), callNode.getSourceFileName()));
    Node callNodePrevious = callNode.getPrevious();
    Node callNodeParent = callNode.getParent();
    callNode.detach();
    Node castNode = IR.cast(callNode, jsDocBuilder.build());
    castNode.useSourceInfoFrom(callNode);
    if (callNodePrevious != null) {
      callNodeParent.addChildAfter(castNode, callNodePrevious);
    } else {
      callNodeParent.addChildToFront(castNode);
    }

    // Record the type so that we can later look it up in React.createElement
    // calls.
    typeSpecNodesByName.put(typeNameNode, specNode, moduleExportInput);

    // Gather methods for the interface definition.
    Node interfacePrototypeProps = IR.objectlit();
    interfacePrototypePropsByName.put(
      typeNameNode, interfacePrototypeProps, moduleExportInput);
    Map<String, JSDocInfo> abstractMethodJsDocsByName = Maps.newHashMap();
    Node propTypesNode = null;
    Node defaultPropsNode = null;
    Node getInitialStateNode = null;
    Node contextTypesNode = null;
    Map<String, JSDocInfo> staticsJsDocs = Maps.newHashMap();
    List<String> exportedNames = Lists.newArrayList();
    Map<Node, PropTypesExtractor> mixedInPropTypes = Maps.newHashMap();
    boolean usesPureRenderMixin = false;
    boolean hasShouldComponentUpdate = false;
    List<Node> componentMethodKeys = Lists.newArrayList();
    for (Node key : specNode.children()) {
      String keyName = key.getString();
      if (keyName.equals("mixins")) {
        List<Node> mixinNameNodes = addMixinsToType(
            t.getScope(),
            typeName,
            key,
            interfacePrototypeProps,
            staticsJsDocs);
        usesPureRenderMixin = mixinNameNodes.stream().anyMatch(
              node -> node.getQualifiedName().equals(REACT_PURE_RENDER_MIXIN_NAME));
        for (Node mixinNameNode : mixinNameNodes) {
          if (mixinAbstractMethodJsDocsByName.containsName(t.getScope(), mixinNameNode)) {
            abstractMethodJsDocsByName.putAll(
              mixinAbstractMethodJsDocsByName.get(t.getScope(), mixinNameNode));
          }
          if (options.propTypesTypeChecking) {
            PropTypesExtractor mixinPropTypesExtractor =
                propTypesExtractorsByName.get(t.getScope(), mixinNameNode);
            if (mixinPropTypesExtractor != null) {
              mixedInPropTypes.put(mixinNameNode, mixinPropTypesExtractor);
            }
          }
        }
        continue;
      }
      if (keyName.equals("propTypes")) {
        propTypesNode = key.getFirstChild();
        continue;
      }
      if (keyName.equals("getDefaultProps")) {
        defaultPropsNode = PropTypesExtractor.extractDefaultPropsObjectLiteralNode(key);
      }
      if (keyName.equals("getInitialState")) {
        getInitialStateNode = key;
      }
      if (keyName.equals("contextTypes")) {
        contextTypesNode = key.getFirstChild();
        continue;
      }
      if (keyName.equals("statics")) {
        if (createFuncName.equals("React.createClass")) {
          gatherStaticsJsDocs(key, staticsJsDocs);
        }
        continue;
      }
      if (!key.hasOneChild() || !key.getFirstChild().isFunction()) {
        continue;
      }
      if (keyName.equals("shouldComponentUpdate")) {
        hasShouldComponentUpdate = true;
      }
      Node func = key.getFirstChild();

      // If the function is an implementation of a standard component method
      // (like shouldComponentUpdate), then copy the parameter and return type
      // from the ReactComponent interface method, so that it gets type checking
      // (without an explicit @override annotation, which doesn't appear to work
      // for interface extending interfaces in any case).
      JSDocInfo componentMethodJsDoc = componentMethodJsDocs.get(keyName);
      if (componentMethodJsDoc != null) {
        componentMethodKeys.add(key);
        mergeInJsDoc(key, func, componentMethodJsDoc);
      }
      // Ditto for abstract methods from mixins.
      JSDocInfo abstractMethodJsDoc = abstractMethodJsDocsByName.get(keyName);
      if (abstractMethodJsDoc != null) {
        // Treat mixin methods as component ones too, as far as making the type
        // used for props more specific.
        componentMethodKeys.add(key);
        mergeInJsDoc(key, func, abstractMethodJsDoc);
      }

      // Require an explicit @public annotation (we can't use @export since
      // it's not allowed on object literal keys and we can't easily remove it
      // while keeping the rest of the JSDoc intact).
      if (isExportedType && componentMethodJsDoc == null &&
          key.getJSDocInfo() != null &&
          key.getJSDocInfo().getVisibility() == JSDocInfo.Visibility.PUBLIC) {
        exportedNames.add(keyName);
      }

      // Gather method signatures so that we can declare them where the compiler
      // can see them.
      addFuncToInterface(
            keyName, func, interfacePrototypeProps, key.getJSDocInfo());

      // Add a @this {<type name>} annotation to all methods in the spec, to
      // avoid the compiler complaining dangerous use of "this" in a global
      // context.
      jsDocBuilder = newJsDocInfoBuilderForNode(key);
      // TODO: Generate type for statics to use as the "this" type for
      // getDefaultProps.
      Node thisTypeNode = keyName.equals("getDefaultProps") ?
          new Node(Token.STAR) : new Node(Token.BANG, IR.string(typeName));
      jsDocBuilder.recordThisType(new JSTypeExpression(
        thisTypeNode, key.getSourceFileName()));
      key.setJSDocInfo(jsDocBuilder.build());
    }

    if (usesPureRenderMixin && hasShouldComponentUpdate) {
      compiler.report(JSError.make(
          specNode, PURE_RENDER_MIXIN_SHOULD_COMPONENT_UPDATE_OVERRIDE, typeName));
      return;
    }

    // Remove propTypes that are not tagged with @struct. It would have been
    // nice to use @preserve, but that is interpreted immediately (as keeping
    // the comment), so we couldn't get to it. @struct is sort of appropriate,
    // since the propTypes are going to be used as structs (for reflection
    // presumably).
    if (propTypesNode != null && options.optimizeForSize) {
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(propTypesNode);
      if (jsdoc == null || !jsdoc.makesStructs()) {
        propTypesNode.getParent().detachFromParent();
      }
    }

    // Add a "<type name>Element" @typedef for the element type of this class.
    jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordTypedef(new JSTypeExpression(
        createReactElementTypeExpressionNode(typeName),
        callNode.getSourceFileName()));
    String elementTypeName = typeName + "Element";
    Node elementTypedefNode = NodeUtil.newQName(compiler, elementTypeName);
    if (elementTypedefNode.isName()) {
      elementTypedefNode = IR.var(elementTypedefNode);
    }
    elementTypedefNode.setJSDocInfo(jsDocBuilder.build());
    if (!NodeUtil.isNameDeclaration(elementTypedefNode)) {
      elementTypedefNode = IR.exprResult(elementTypedefNode);
    }
    elementTypedefNode.useSourceInfoFromForTree(callParentNode);
    Node typesInsertionPoint = callParentNode.getParent();
    typesInsertionPoint.getParent().addChildAfter(
        elementTypedefNode, typesInsertionPoint);
    if (addModuleExports) {
      Ast.addModuleExport(elementTypeName, elementTypedefNode);
    }

    // Generate statics property JSDocs, so that the compiler knows about them.
    if (createFuncName.equals("React.createClass")) {
      Node staticsInsertionPoint = typesInsertionPoint;
      for (Map.Entry<String, JSDocInfo> entry : staticsJsDocs.entrySet()) {
        String staticName = entry.getKey();
        JSDocInfo staticJsDoc = entry.getValue();
        Node staticDeclaration = NodeUtil.newQName(
            compiler, typeName + "." + staticName);
        staticDeclaration.setJSDocInfo(staticJsDoc);
        Node staticExprNode = IR.exprResult(staticDeclaration);
        staticExprNode.useSourceInfoFromForTree(staticsInsertionPoint);
        staticsInsertionPoint.getParent().addChildAfter(
            staticExprNode, staticsInsertionPoint);
        staticsInsertionPoint = staticExprNode;
      }
    }

    // Trim duplicated properties that have accumulated (due to mixins defining
    // a method and then classes overriding it). Keep the last one since it's
    // from the class and thus most specific.
    ListMultimap<String, Node> interfaceKeyNodes = ArrayListMultimap.create();
    for (Node interfaceKeyNode = interfacePrototypeProps.getFirstChild();
          interfaceKeyNode != null;
          interfaceKeyNode = interfaceKeyNode.getNext()) {
      interfaceKeyNodes.put(interfaceKeyNode.getString(), interfaceKeyNode);
    }
    for (String key : interfaceKeyNodes.keySet()) {
      List<Node> keyNodes = interfaceKeyNodes.get(key);
      if (keyNodes.size() > 1) {
        for (Node keyNode : keyNodes.subList(0, keyNodes.size() - 1)) {
          keyNode.detachFromParent();
        }
      }
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
        callNode.getSourceFileName()));
    for (JSTypeExpression implementedInterface : implementedInterfaces) {
        jsDocBuilder.recordExtendedInterface(implementedInterface);
    }
    interfaceTypeFunctionNode.setJSDocInfo(jsDocBuilder.build());
    typesInsertionPoint.getParent().addChildBefore(
        interfaceTypeNode, typesInsertionPoint);
    // Always export inner interface names, assuming that their parents are
    // exported too. We may end up exporting too much, but it's too annoying to
    // figure out if the enclosing type is exported.
    if (addModuleExports || (t.getScope().isModuleScope() &&
        interfaceTypeName.contains("_"))) {
      Ast.addModuleExport(interfaceTypeName, interfaceTypeNode);
    }
    typesInsertionPoint.getParent().addChildAfter(
        NodeUtil.newQNameDeclaration(
            compiler,
            interfaceTypeName + ".prototype",
            interfacePrototypeProps,
            null),
        interfaceTypeNode);

    // Ensure that we trigger propTypes checking even if we don't have our
    // propTypes (if they're provided by mixins).
    if (options.propTypesTypeChecking &&
        !mixedInPropTypes.isEmpty() &&
        propTypesNode == null) {
      propTypesNode = IR.objectlit();
    }

    if (contextTypesNode != null &&
        PropTypesExtractor.canExtractPropTypes(contextTypesNode)) {
      if (options.propTypesTypeChecking) {
        PropTypesExtractor extractor = new PropTypesExtractor(
            contextTypesNode, null, typeName, interfaceTypeName,
            mixedInPropTypes, compiler, true);
        extractor.extract();
        extractor.insert(typesInsertionPoint, addModuleExports);
      } else {
        PropTypesExtractor.cleanUpPropTypesWhenNotChecking(contextTypesNode);
      }
    }

    if (propTypesNode != null &&
        PropTypesExtractor.canExtractPropTypes(propTypesNode)) {
      if (isExportedType) {
        for (Node propTypeKeyNode : propTypesNode.children()) {
          exportedNames.add(propTypeKeyNode.getString());
        }
      }

      if (options.propTypesTypeChecking) {
        PropTypesExtractor extractor = new PropTypesExtractor(
            propTypesNode, defaultPropsNode, typeName, interfaceTypeName,
            mixedInPropTypes, compiler);
        extractor.extract();
        extractor.insert(typesInsertionPoint, addModuleExports);
        if (createFuncName.equals("React.createClass")) {
          extractor.addToComponentMethods(componentMethodKeys);
        }
        propTypesExtractorsByName.put(
          typeNameNode, extractor, moduleExportInput);
      } else {
        PropTypesExtractor.cleanUpPropTypesWhenNotChecking(propTypesNode);
      }
    }

    if (createFuncName.equals("React.createClass") &&
        getInitialStateNode != null) {
      StateTypeExtractor extractor = new StateTypeExtractor(
          getInitialStateNode, typeName, interfaceTypeName, compiler);
      if (extractor.hasStateType()) {
        extractor.insert(typesInsertionPoint);
        extractor.addToComponentMethods(componentMethodKeys);
      }
      // Gather fields defined in getInitialState.
      NodeTraversal.traverse(
          compiler,
          getInitialStateNode,
          new NodeTraversal.AbstractPostOrderCallback() {
            @Override public void visit(NodeTraversal t, Node n, Node parent) {
              if (!n.isThis() || !parent.isGetProp()) {
                return;
              }
              Node prop = n.getNext();
              if (prop == null || !prop.isString()) {
                return;
              }
              JSDocInfo jsDocInfo = NodeUtil.getBestJSDocInfo(parent);
              if (jsDocInfo == null) {
                return;
              }
              String fieldName = prop.getString();
              Node prototypeFieldNode = NodeUtil.newQNameDeclaration(
                  compiler,
                  interfaceTypeName + ".prototype." + fieldName,
                  null, // no value
                  jsDocInfo);
              prototypeFieldNode.useSourceInfoFromForTree(prop);
              typesInsertionPoint.getParent().addChildAfter(
                  prototypeFieldNode,
                  typesInsertionPoint);
            }
          });
    }

    synthesizeExterns(exportedNames, typeName);
  }

  private void synthesizeExterns(List<String> exportedNames, String typeName) {
    if (!exportedNames.isEmpty()) {
      // Synthesize an externs entry of the form
      // ComponentExports = {propA: 0, propB: 0, publicMethod: 0};
      // To disable naming of exported component props and methods.
      Node exportedNamesObjectLitNode = IR.objectlit();
      for (String exportedName : exportedNames) {
        Node keyNode = IR.stringKey(exportedName, IR.number(0));
        exportedNamesObjectLitNode.addChildToBack(keyNode);
      }
      Node exportedNamesNode = NodeUtil.newQNameDeclaration(
          compiler,
          typeName.replaceAll("\\.", "\\$\\$") + "Exports",
          exportedNamesObjectLitNode,
          null);
      externsRoot.addChildToBack(exportedNamesNode);
    }
  }

  /**
   * Mixins can't have real abstract methods, since React forbids definition in
   * a class of a method that was defined in the mixin (therefore we can't
   * include them in the mixin spec with a goog.abstractMethod value). We
   * instead expect to see them as no-op property accesses following the mixin
   * definition i.e.:
   *
   * var Mixin = React.createMixin({
   *   mixinMethod: function() {return this.abstractMixinMethod() * 2;}
   * });
   * /**
   *  * @return {number}
   *  * /
   * Mixin.abstractMixinMethod;
   */
  private boolean visitMixinAbstractMethod(NodeTraversal t, Node value) {
    if (value == null || !value.isExprResult() || !value.hasOneChild() ||
        !value.getFirstChild().isGetProp()) {
      return false;
    }
    Node getPropNode = value.getFirstChild();
    if (!getPropNode.isQualifiedName() || !getPropNode.hasChildren()) {
      return false;
    }
    Node mixinNameNode = getPropNode.getFirstChild();
    MixinRef mixinRef = reactMixinsByName.get(t.getScope(), mixinNameNode);
    if (mixinRef == null) {
      return false;
    }
    Node mixinSpecNode = mixinRef.node;
    String methodName = getPropNode.getLastChild().getString();
    JSDocInfo abstractFuncJsDoc = getPropNode.getJSDocInfo();

    Node abstractFuncParamList = IR.paramList();
    if (abstractFuncJsDoc != null) {
      for (String parameterName : abstractFuncJsDoc.getParameterNames()) {
        abstractFuncParamList.addChildToBack(IR.name(parameterName));
      }
      Map<String, JSDocInfo> jsDocsByName =
          mixinAbstractMethodJsDocsByName.get(t.getScope(), mixinNameNode);
      if (jsDocsByName == null) {
        jsDocsByName = Maps.newHashMap();
        CompilerInput moduleExportInput =
            t.getScope().isModuleScope() ? t.getInput() : null;
        mixinAbstractMethodJsDocsByName.put(
            mixinNameNode, jsDocsByName, moduleExportInput);
      }
      jsDocsByName.put(methodName, abstractFuncJsDoc);
    }
    Node abstractFuncNode = IR.function(
        IR.name(""), abstractFuncParamList, IR.block());
    abstractFuncNode.useSourceInfoFrom(value);
    if (abstractFuncJsDoc != null) {
      abstractFuncNode.setJSDocInfo(abstractFuncJsDoc.clone());
    }
    abstractFuncNode.setStaticSourceFile(value.getStaticSourceFile());
    Node interfacePrototypeProps =
        reactMixinInterfacePrototypePropsByName.get(
          t.getScope(), mixinNameNode);
    addFuncToInterface(
        methodName,
        abstractFuncNode,
        interfacePrototypeProps,
        getPropNode.getJSDocInfo());

    return true;
  }

  private List<Node> addMixinsToType(
      Scope scope,
      String typeName,
      Node mixinsNode,
      Node interfacePrototypeProps,
      Map<String, JSDocInfo> staticsJsDocs) {
    Set<String> mixinNames = Sets.newHashSet();
    List<Node> mixinNameNodes = Lists.newArrayList();
    if (!mixinsNode.hasOneChild() ||
          !mixinsNode.getFirstChild().isArrayLit()) {
      compiler.report(JSError.make(mixinsNode, MIXINS_UNEXPECTED_TYPE));
      return mixinNameNodes;
    }
    Node thisTypeNode = new Node(Token.BANG, IR.string(typeName));
    for (Node mixinNameNode : mixinsNode.getFirstChild().children()) {
      if (!mixinNameNode.isQualifiedName()) {
        compiler.report(JSError.make(mixinNameNode, MIXIN_EXPECTED_NAME));
        continue;
      }
      String mixinName = mixinNameNode.getQualifiedName();
      if (mixinNames.contains(mixinName)) {
        continue;
      }
      mixinNames.add(mixinName);
      mixinNameNodes.add(mixinNameNode);
      if (mixinName.equals(REACT_PURE_RENDER_MIXIN_NAME)) {
        // Built-in mixin, there's nothing more that we need to do.
        continue;
      }
      MixinRef mixinRef = reactMixinsByName.get(scope, mixinNameNode);
      if (mixinRef == null) {
        compiler.report(JSError.make(mixinNameNode, MIXIN_UNKNOWN, mixinName));
        continue;
      }
      Node mixinSpecNode = mixinRef.node;
      for (Node mixinSpecKey : mixinSpecNode.children()) {
        String keyName = mixinSpecKey.getString();
        if (keyName.equals("mixins")) {
          mixinNameNodes.addAll(addMixinsToType(
              mixinRef.scope,
              typeName,
              mixinSpecKey,
              interfacePrototypeProps,
              staticsJsDocs));
          continue;
        }
        if (keyName.equals("statics")) {
          gatherStaticsJsDocs(mixinSpecKey, staticsJsDocs);
          continue;
        }
        JSDocInfo mixinSpecKeyJsDoc = mixinSpecKey.getJSDocInfo();
        // Private methods should not be exposed.
        if (mixinSpecKeyJsDoc != null &&
            mixinSpecKeyJsDoc.getVisibility() == JSDocInfo.Visibility.PRIVATE) {
          continue;
        }
        if (mixinSpecKey.hasOneChild() &&
            mixinSpecKey.getFirstChild().isFunction()) {
          // Ensure that the @this type inside mixin functions refers to the
          // type we're copying into, not the mixin type.
          if (mixinSpecKeyJsDoc != null) {
              // We can't use JSDocInfoBuilder because it will not override the
              // "this" type if it's already set.
              mixinSpecKeyJsDoc = mixinSpecKeyJsDoc.clone();
              JSDocInfoAccessor.setJSDocInfoThisType(
                  mixinSpecKeyJsDoc,
                  new JSTypeExpression(
                      thisTypeNode, mixinSpecKey.getSourceFileName()));
          }
          Node keyNode = addFuncToInterface(
              keyName,
              mixinSpecKey.getFirstChild(),
              interfacePrototypeProps,
              mixinSpecKeyJsDoc);
          // Since mixins are effectively copied into the type, their source
          // file is the type's (allow private methods from mixins to be
          // called).
          keyNode.setStaticSourceFile(mixinsNode.getStaticSourceFile());
        }
      }
    }
    return mixinNameNodes;
  }

  private static Node addFuncToInterface(
      String name, Node funcNode, Node interfacePrototypeProps,
      JSDocInfo jsDocInfo) {
    // Semi-shallow copy (just parameters) so that we don't copy the function
    // implementation.
    Node methodNode = funcNode.cloneNode();
    for (Node funcChild = funcNode.getFirstChild();
         funcChild != null; funcChild = funcChild.getNext()) {
        if (funcChild.isParamList()) {
          Node methodParamList = new Node(Token.PARAM_LIST);
          for (Node paramNode : funcChild.children()) {
            // Don't include parameter default values on the interface. They're
            // not needed and they confuse the compiler (since they'll end up
            // getting transpiled, and thus the interface method body will not
            // be empty).
            if (paramNode.isDefaultValue()) {
              paramNode = paramNode.getFirstChild();
            }
            methodParamList.addChildToBack(paramNode.cloneTree());
          }
          methodNode.addChildToBack(methodParamList);
        } else {
          methodNode.addChildToBack(funcChild.cloneNode());
        }
    }
    Node keyNode = IR.stringKey(name, methodNode);
    keyNode.useSourceInfoFrom(funcNode);
    keyNode.setStaticSourceFile(funcNode.getStaticSourceFile());
    if (jsDocInfo != null) {
        keyNode.setJSDocInfo(jsDocInfo.clone());
    }
    interfacePrototypeProps.addChildToBack(keyNode);
    return keyNode;
  }

  private void gatherStaticsJsDocs(
      Node staticsNode, Map<String, JSDocInfo> staticsJsDocs) {
    if (!staticsNode.hasOneChild() ||
          !staticsNode.getFirstChild().isObjectLit()) {
      compiler.report(JSError.make(staticsNode, STATICS_UNEXPECTED_TYPE));
      return;
    }
    for (Node staticKeyNode : staticsNode.getFirstChild().children()) {
      String staticName = staticKeyNode.getString();
      JSDocInfo staticJsDoc = staticKeyNode.getJSDocInfo();
      if (staticJsDoc == null) {
        // We need to have some kind of JSDoc so that the CheckSideEffects pass
        // doesn't flag this as useless code or a missing property.
        compiler.report(
            JSError.make(staticKeyNode, JSDOC_REQUIRED_FOR_STATICS, staticName));
        continue;
      } else {
        staticJsDoc = staticJsDoc.clone();
      }
      staticsJsDocs.put(staticName, staticJsDoc);
    }
  }

  private static void mergeInJsDoc(Node key, Node func, JSDocInfo jsDoc) {
    JSDocInfo existingJsDoc = key.getJSDocInfo();
    List<String> funcParamNames = Lists.newArrayList();
    for (Node param : NodeUtil.getFunctionParameters(func).children()) {
      if (param.isName()) {
        funcParamNames.add(param.getString());
      }
    }
    JSDocInfoBuilder jsDocBuilder = newJsDocInfoBuilderForNode(key);
    if (!funcParamNames.isEmpty()) {
      for (String parameterName : jsDoc.getParameterNames()) {
        JSTypeExpression parameterType = jsDoc.getParameterType(parameterName);
        // Use the parameter names in the implementation, not the original
        parameterName = funcParamNames.remove(0);
        jsDocBuilder.recordParameter(parameterName, parameterType);
        if (funcParamNames.isEmpty()) {
          break;
        }
      }
    }
    if (jsDoc.hasReturnType() && (existingJsDoc == null ||
        !existingJsDoc.hasReturnType())) {
      jsDocBuilder.recordReturnType(jsDoc.getReturnType());
    }
    for (String templateTypeName : jsDoc.getTemplateTypeNames()) {
      jsDocBuilder.recordTemplateTypeName(templateTypeName);
    }
    for (Map.Entry<String, Node> entry :
        jsDoc.getTypeTransformations().entrySet()) {
      jsDocBuilder.recordTypeTransformation(entry.getKey(), entry.getValue());
    }
    if (jsDoc.isOverride()) {
      jsDocBuilder.recordOverride();
    }
    key.setJSDocInfo(jsDocBuilder.build());
  }

  private boolean validateCreateTypeUsage(Node n) {
    // There are only two valid usage patterns for of React.create{Class|Mixin}:
    //   var ClassName = React.create{Class|Mixin}({...})
    //   namespace.ClassName = React.create{Class|Mixin}({...})
    Node parent = n.getParent();
    switch (parent.getToken()) {
      case NAME:
        return true;
      case ASSIGN:
        return n == parent.getLastChild() && parent.getParent().isExprResult();
      default:
        return false;
    }
  }

  private void visitReactCreateElement(Scope scope, Node callNode) {
    int paramCount = callNode.getChildCount() - 1;
    if (paramCount == 0) {
      compiler.report(JSError.make(callNode, CREATE_ELEMENT_UNEXPECTED_PARAMS));
      return;
    }

    // Replace spreads in props with Object.assign (or even inline them
    // altogether, it's just a single value being spread in an otherwise empty
    // object). This allows the props type checking to work better.
    int callParamCount = callNode.getChildCount() - 1;
    if (callParamCount >= 2) {
      Node propsParamNode = callNode.getChildAtIndex(2);
      if (propsParamNode.isObjectLit() && Props.hasSpread(propsParamNode)) {
        Props.transformSpreadObjectToObjectAssign(propsParamNode);
      }
    }

    if (options.optimizeForSize) {
      // There's no need for extra type checks for optimized builds.
      Node functionNameNode = callNode.getFirstChild();
      if (functionNameNode.getToken() == Token.GETPROP) {
        functionNameNode.replaceWith(IR.name(CREATE_ELEMENT_ALIAS_NAME));
      }
      return;
    }

    if (callNode.getParent().getToken() == Token.CAST) {
      // There's already a cast around the call, there's no need to add another.
      return;
    }

    // Add casts of the form /** @type {!ReactElement.<type name>} */ around
    // React.createElement calls, so that the return value of React.render will
    // have the correct type (for string types assume that it's a
    // ReactDOMElement).
    // It's too expensive to know what the type parameter node actually refers
    // to, so instead we assume that it directly references the type (this is
    // the most common case, especially with JSX). This means that we will not
    // add type annotations for cases such as:
    // var typeAlias = SomeType;
    // React.createElement(typeAlias);
    Node typeNode = callNode.getChildAtIndex(1);
    Node elementTypeExpressionNode;
    if (typeNode.isString()) {
      elementTypeExpressionNode = IR.string("ReactDOMElement");
    } else {
      if (!reactClassesByName.containsName(scope, typeNode)) {
        return;
      }
      elementTypeExpressionNode =
          createReactElementTypeExpressionNode(typeNode.getQualifiedName());
      PropTypesExtractor propTypesExtractor =
          propTypesExtractorsByName.get(scope, typeNode);
      if (propTypesExtractor != null) {
        propTypesExtractor.visitReactCreateElement(callNode);
      }
    }

    JSDocInfoBuilder jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordType(new JSTypeExpression(
        new Node(Token.BANG, elementTypeExpressionNode),
        callNode.getSourceFileName()));
    JSDocInfo jsDoc = jsDocBuilder.build();
    Node callNodePrevious = callNode.getPrevious();
    Node callNodeParent = callNode.getParent();
    callNode.detach();
    Node castNode = IR.cast(callNode, jsDoc);
    castNode.useSourceInfoFrom(callNode);
    if (callNodePrevious != null) {
      callNodeParent.addChildAfter(castNode, callNodePrevious);
    } else {
      callNodeParent.addChildToFront(castNode);
    }
  }

  private static boolean isReactCreateElement(Node value) {
    if (value != null && value.isCall()) {
      return value.getFirstChild().matchesQualifiedName("React.createElement");
    }
    return false;
  }

  private void visitReactPropTypes(Node propTypesNode) {
    if (options.optimizeForSize) {
      propTypesNode.getFirstChild().replaceWith(IR.name(PROP_TYPES_ALIAS_NAME));
    }
  }

  private static boolean isReactPropTypes(Node value) {
    if (value != null && value.isGetProp()) {
      return value.getFirstChild().matchesQualifiedName("React.PropTypes");
    }
    return false;
  }

  private void visitClassExtendsReactComponent(
      NodeTraversal t,
      Node classNode,
      SymbolTable<Node> interfacePrototypePropsByName) {
    Scope scope = t.getScope();
    Node classParentNode = classNode.getParent();
    Node typeNameNode = classNode.getFirstChild();
    Node typesInsertionPointTemp = classNode;
    boolean ok = false;

    if (classParentNode.isAssign() && classNode.getGrandparent().isExprResult()) {
      if (classNode.getPrevious().isQualifiedName()) {
        typeNameNode = classNode.getPrevious();
        typesInsertionPointTemp = classNode.getGrandparent();
        ok = true;
      }
    } else if (classParentNode.isName() && NodeUtil.isNameDeclaration(classNode.getGrandparent())) {
      typeNameNode = classParentNode;
      typesInsertionPointTemp = classNode.getGrandparent();
      ok = true;
    } else if (classParentNode.isScript() ||
        classParentNode.isModuleBody() ||
        classParentNode.isExport() ||
        classParentNode.isBlock() ||
        classParentNode.isFunction()) {
      ok = true;
      typeNameNode = classNode.getFirstChild();
      typesInsertionPointTemp = classNode;
    };

    if (!ok) {
      compiler.report(JSError.make(
          classNode, UNSUPPORTED_REACT_CLASS, classNode.getFirstChild().getQualifiedName()));
      return;
    }
    
    String typeName = typeNameNode.getQualifiedName();

    // Check to see if the type has an ES6 module export. We assume this is
    // of the form `export class Comp extends React.Component`. If it is, then
    // we transform it into `class Comp extends React.Component { ... }; export {Comp};`.
    // That way it's more similar to non-module uses (as far as where we can
    // add additional nodes to the AST) and the @typedef that we use for the
    // declaration does not prevent it from getting exported (Es6RewriteModules
    // does not export the values of typedefs).
    // Additionally, if the component is exported, then we also need to export
    // types that we generate from it (CompInterface, CompElement).
    boolean addModuleExports = false;
    CompilerInput moduleExportInput =
        scope.isModuleScope() ? t.getInput() : null;
    if (typesInsertionPointTemp.getParent().isExport()) {
      addModuleExports = true;
      Node exportNode = typesInsertionPointTemp.getParent();
      typesInsertionPointTemp.detach();
      exportNode.replaceWith(typesInsertionPointTemp);
      Ast.addModuleExport(typeName, typesInsertionPointTemp);
    }

    final Node typesInsertionPoint = typesInsertionPointTemp;

    // For components tagged with @export don't rename their props or public
    // methods.
    JSDocInfo jsDocInfo = NodeUtil.getBestJSDocInfo(classNode);
    boolean isExportedType = jsDocInfo != null && jsDocInfo.isExport();
    // "Clear" the @export bit, otherwise the compiler will complain about its
    // presence on module-scoped variables.
    if (isExportedType && scope.isModuleScope()) {
      JSDocInfoAccessor.setJSDocExport(jsDocInfo, false);
    }

    JSDocInfoBuilder jsDocBuilder;

    // Do not add "<type name>Interface" since we can use the type directly
  
    Node classBody = classNode.getLastChild();

    // Record the type so that we can later look it up in React.createElement
    // calls.
    reactClassesByName.put(typeNameNode, classBody, moduleExportInput);

    // Gather methods for the interface definition.
    Node interfacePrototypeProps = IR.objectlit();
    interfacePrototypePropsByName.put(
        typeNameNode, interfacePrototypeProps, moduleExportInput);
    Map<String, JSDocInfo> abstractMethodJsDocsByName = Maps.newHashMap();
    Node initialStateNode = null;
    List<String> exportedNames = Lists.newArrayList();
    boolean usesPureRenderMixin = false;
    // TODO(arv): Check for PureComponent
    boolean hasShouldComponentUpdate = false;
    List<Node> componentMethodKeys = Lists.newArrayList();
    for (Node key : classBody.children()) {
      if (key.isStaticMember()) {
        // No need to do anything for static members
        continue;
      }

      if (!key.hasOneChild() || !key.getFirstChild().isFunction()) {
        continue;
      }

      String keyName = key.getString();
      
      if (keyName.equals("shouldComponentUpdate")) {
        hasShouldComponentUpdate = true;
      } else if (keyName.equals("initialState")) {
        initialStateNode = key;
      }

      Node func = key.getFirstChild();

      // If the function is an implementation of a standard component method
      // (like shouldComponentUpdate), then copy the parameter and return type
      // from the ReactComponent interface method, so that it gets type checking
      // (without an explicit @override annotation, which doesn't appear to work
      // for interface extending interfaces in any case).
      JSDocInfo componentMethodJsDoc = componentMethodJsDocs.get(keyName);
      if (componentMethodJsDoc != null) {
        componentMethodKeys.add(key);
        mergeInJsDoc(key, func, componentMethodJsDoc);
      }

      // Ditto for abstract methods from mixins.
      JSDocInfo abstractMethodJsDoc = abstractMethodJsDocsByName.get(keyName);
      if (abstractMethodJsDoc != null) {
        // Treat mixin methods as component ones too, as far as making the type
        // used for props more specific.
        componentMethodKeys.add(key);
        mergeInJsDoc(key, func, abstractMethodJsDoc);
      }

      // If @export is present add it to externs to prevent renaming.
      if (isExportedType && componentMethodJsDoc == null &&
          key.getJSDocInfo() != null &&
          key.getJSDocInfo().isExport()) {
        exportedNames.add(keyName);
      }

      // Gather method signatures so that we can declare them where the compiler
      // can see them.
      addFuncToInterface(
            keyName, func, interfacePrototypeProps, key.getJSDocInfo());

      // If the function is an implementation of a standard component method
      // (like shouldComponentUpdate), then add @override.
      if (componentMethodJsDoc != null) {
        jsDocBuilder = new JSDocInfoBuilder(true);
        jsDocBuilder.recordOverride();
        mergeInJsDoc(key, func, jsDocBuilder.build());
      }
    }

    if (usesPureRenderMixin && hasShouldComponentUpdate) {
      compiler.report(JSError.make(
          classNode, PURE_RENDER_MIXIN_SHOULD_COMPONENT_UPDATE_OVERRIDE, typeName));
      return;
    }

    // Add a "<type name>Element" @typedef for the element type of this class.
    jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordTypedef(new JSTypeExpression(
        createReactElementTypeExpressionNode(typeName),
        classNode.getSourceFileName()));
    String elementTypeName = typeName + "Element";
    Node elementTypedefNode = NodeUtil.newQName(compiler, elementTypeName);
    if (elementTypedefNode.isName()) {
      elementTypedefNode = IR.var(elementTypedefNode);
    }
    elementTypedefNode.setJSDocInfo(jsDocBuilder.build());
    if (!NodeUtil.isNameDeclaration(elementTypedefNode)) {
      elementTypedefNode = IR.exprResult(elementTypedefNode);
    }
    elementTypedefNode.useSourceInfoFromForTree(classParentNode);
    typesInsertionPoint.getParent().addChildAfter(
        elementTypedefNode, typesInsertionPoint);
    if (addModuleExports) {
      Ast.addModuleExport(elementTypeName, elementTypedefNode);
    }

    // Trim duplicated properties that have accumulated (due to mixins defining
    // a method and then classes overriding it). Keep the last one since it's
    // from the class and thus most specific.
    ListMultimap<String, Node> interfaceKeyNodes = ArrayListMultimap.create();
    for (Node interfaceKeyNode = interfacePrototypeProps.getFirstChild();
          interfaceKeyNode != null;
          interfaceKeyNode = interfaceKeyNode.getNext()) {
      interfaceKeyNodes.put(interfaceKeyNode.getString(), interfaceKeyNode);
    }
    for (String key : interfaceKeyNodes.keySet()) {
      List<Node> keyNodes = interfaceKeyNodes.get(key);
      if (keyNodes.size() > 1) {
        for (Node keyNode : keyNodes.subList(0, keyNodes.size() - 1)) {
          keyNode.detachFromParent();
        }
      }
    }

    if (initialStateNode != null) {
      StateTypeExtractor extractor = new StateTypeExtractor(
        initialStateNode, typeName, typeName, compiler);
      if (extractor.hasStateType()) {
        extractor.insert(typesInsertionPoint);
        extractor.addToComponentMethods(componentMethodKeys);
      }
    }

    ClassOutOfBoundsData outOfBoundsData =
        new ClassOutOfBoundsData(
            typesInsertionPoint,
            typeNameNode,
            addModuleExports,
            isExportedType,
            componentMethodKeys,
            exportedNames);
    classOutOfBoundsMap.put(typeNameNode, outOfBoundsData, moduleExportInput);

    // Replace React.Component with React$Component
    if (options.optimizeForSize) {      
      Node extendsNode = classNode.getSecondChild();
      String replaceName = extendsNode.getQualifiedName().equals("React.Component") ?
          COMPONENT_ALIAS_NAME : PURE_COMPONENT_ALIAS_NAME;
      extendsNode.replaceWith(IR.name(replaceName));
    }
  }

  private boolean isClassExtendsReactComponent(Node value) {
    if (value == null || !value.isEs6Class()) {
      return false;
    }
    Node extendsExpression = value.getChildAtIndex(1);
    return extendsExpression.isGetProp() &&
      (extendsExpression.matchesQualifiedName("React.Component") ||
      extendsExpression.matchesQualifiedName("React.PureComponent"));
  }

  /**
   * Creates the equivalent to ReactElement.<!typeName>
   */
  private static Node createReactElementTypeExpressionNode(String typeName) {
    Node blockNode = IR.block();
    blockNode.addChildToFront(new Node(Token.BANG, IR.string(typeName)));
    Node typeNode = IR.string("ReactElement");
    typeNode.addChildToFront(blockNode);
    return typeNode;
  }

  private static JSDocInfoBuilder newJsDocInfoBuilderForNode(Node node) {
    JSDocInfo existing = node.getJSDocInfo();
    if (existing == null) {
      return new JSDocInfoBuilder(true);
    }
    return JSDocInfoBuilder.copyFrom(existing);
  }

  PropTypesExtractor getPropTypesExtractor(String typeName) {
    // TODO: this probably needs extra information to look up things by name
    // (and/or reverse the module name mangling).
    return propTypesExtractorsByName.getByName(typeName);
  }

  /**
   * Generates the type name for the interface that we generate for a component.
   * Normally of the form <ComponentName>Interface, but for nested components,
   * we avoid putting the interface as a nested property, since the compiler
   * appears to have trouble removing it in that case. That is, given:
   *
   * const ns = {};
   * ns.Comp = React.createClass(...);
   * ns.Comp.Inner = React.createClass(...);
   *
   * We generate interface names ns.CompInterface and ns.Comp_InnerInterface;
   */
  private String generateInterfaceTypeName(NodeTraversal t, Node typeNameNode) {
    String typeName = typeNameNode.getQualifiedName();
    String[] typeNameParts = typeName.split("\\.");
    String typeNamePrefix = "";
    CompilerInput moduleExportInput =
        t.getScope().isModuleScope() ? t.getInput() : null;
    for (int i = 0; i < typeNameParts.length; i++) {
      String prefixCandidate = typeNamePrefix.isEmpty() ?
          typeNameParts[i] :
          typeNamePrefix + "." + typeNameParts[i];
      if (reactClassesByName.containsNamePrefix(
          prefixCandidate, moduleExportInput)) {
        for (; i < typeNameParts.length; i++) {
          typeNamePrefix += typeNamePrefix.isEmpty() ?
              typeNameParts[i] :
              "_" + typeNameParts[i];
        }
        break;
      } else {
        typeNamePrefix = prefixCandidate;
      }
    }

    return typeNamePrefix + "Interface";
  }

  private static class MixinRef {
    public final Node node;
    public final Scope scope;

    public MixinRef(Node node, Scope scope) {
      this.node = node;
      this.scope = scope;
    }
  }
}
