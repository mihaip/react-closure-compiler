package info.persistent.react.jscomp;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CodePrinter;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerAccessor;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.JSModule;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReactCompilerPass implements NodeTraversal.Callback,
      HotSwapCompilerPass {

  // Errors
  static final DiagnosticType REACT_SOURCE_NOT_FOUND = DiagnosticType.error(
      "REACT_SOURCE_NOT_FOUND",
      "Could not find the React library source.");
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

  private static final String TYPES_JS_RESOURCE_PATH = "info/persistent/react/jscomp/types.js";
  private static final String[] REACT_DOM_TAG_NAMES = {
    // HTML
    "a", "abbr", "address", "area", "article", "aside", "audio", "b", "base",
    "bdi", "bdo", "big", "blockquote", "body", "br", "button", "canvas",
    "caption", "cite", "code", "col", "colgroup", "data", "datalist", "dd",
    "del", "details", "dfn", "dialog", "div", "dl", "dt", "em", "embed",
    "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3",
    "h4", "h5", "h6", "head", "header", "hgroup", "hr", "html", "i", "iframe",
    "img", "input", "ins", "kbd", "keygen", "label", "legend", "li", "link",
    "main", "map", "mark", "menu", "menuitem", "meta", "meter", "nav",
    "noscript", "object", "ol", "optgroup", "option", "output", "p", "param",
    "picture", "pre", "progress", "q", "rp", "rt", "ruby", "s", "samp",
    "script", "section", "select", "small", "source", "span", "strong",
    "style", "sub", "summary", "sup", "table", "tbody", "td", "textarea",
    "tfoot", "th", "thead", "time", "title", "tr", "track", "u", "ul", "var",
    "video", "wbr",
    // SVG
    "circle", "clipPath", "defs", "ellipse", "g", "image", "line",
    "linearGradient", "mask", "path", "pattern", "polygon", "polyline",
    "radialGradient", "rect", "stop", "svg", "text", "tspan"
  };
  private static final String REACT_PURE_RENDER_MIXIN_NAME =
      "React.addons.PureRenderMixin";
  private static final String EXTERNS_SOURCE_NAME = "<ReactCompilerPass-externs.js>";
  private static final String CREATE_ELEMENT_ALIAS_NAME = "React$createElement";

  private final Compiler compiler;
  private final boolean propTypesTypeChecking;
  private boolean stripPropTypes = false;
  private boolean addCreateElementAlias = false;
  private final Map<String, Node> reactClassesByName = Maps.newHashMap();
  private final Map<String, Node> reactClassInterfacePrototypePropsByName =
      Maps.newHashMap();
  private final Map<String, Node> reactMixinsByName = Maps.newHashMap();
  private final Map<String, Node> reactMixinInterfacePrototypePropsByName =
      Maps.newHashMap();
  // Mixin name -> method name -> JSDoc
  private final Map<String, Map<String, JSDocInfo>>
      mixinAbstractMethodJsDocsByName = Maps.newHashMap();
  private final Map<String, PropTypesExtractor> propTypesExtractorsByName =
      Maps.newHashMap();

  // Make debugging test failures easier by allowing the processed output to
  // be inspected.
  static boolean saveLastOutputForTests = false;
  static String lastOutputForTests;

  public ReactCompilerPass(AbstractCompiler compiler) {
    // TODO: flip default once all known issues are resolved
    this(compiler, false);
  }

  public ReactCompilerPass(
      AbstractCompiler compiler, boolean propTypesTypeChecking) {
    this.compiler = (Compiler) compiler;
    this.propTypesTypeChecking = propTypesTypeChecking;
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
    addTypes(root);
    hotSwapScript(root, null);
    if (saveLastOutputForTests) {
      lastOutputForTests = new CodePrinter.Builder(root)
          .setPrettyPrint(true)
          .setOutputTypes(true)
          .setTypeRegistry(compiler.getTypeIRegistry())
          .build();
    } else {
      lastOutputForTests = null;
    }
  }

  /**
   * Adds the definition equivalent to a file with:
   *
   * /**
   *  * @type {<moduleType>}
   *  * @const
   *  * /
   * var <moduleName>;
   */
  private void addExternModule(String moduleName, String moduleType,
      CompilerInput externsInput) {
    Node reactVarNode = IR.var(IR.name(moduleName));
    JSDocInfoBuilder jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordType(new JSTypeExpression(
        IR.string(moduleType), EXTERNS_SOURCE_NAME));
    jsDocBuilder.recordConstancy();
    reactVarNode.setJSDocInfo(jsDocBuilder.build());
    externsInput.getAstRoot(compiler).addChildToBack(reactVarNode);
  }

  /**
   * The compiler isn't aware of the React* symbols that are exported from
   * React, inform it via an extern.
   *
   * TODO(mihai): figure out a way to do this without externs, so that the
   * symbols can get renamed.
   */
  private void addExterns() {
    CompilerInput externsInput = CompilerAccessor.getSynthesizedExternsInputAtEnd(compiler);
    addExternModule("React", "ReactModule", externsInput);
    addExternModule("ReactDOM", "ReactDOMModule", externsInput);
    addExternModule("ReactDOMServer", "ReactDOMServerModule", externsInput);
    compiler.reportCodeChange();
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

  /**
   * Inject React type definitions (we want these to get renamed, so they're
   * not part of the externs). {@link Compiler#getNodeForCodeInsertion(JSModule)} is
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
      Result previousResult = compiler.getResult();
      templateTypesNode =
        compiler.parse(SourceFile.fromCode(TYPES_JS_RESOURCE_PATH, typesJs));
      Result result = compiler.getResult();
      if ((result.success != previousResult.success && previousResult.success) ||
          result.errors.length > previousResult.errors.length ||
          result.warnings.length > previousResult.warnings.length) {
        String message = "Could not parse " + TYPES_JS_RESOURCE_PATH + ".";
        if (result.errors.length > 0) {
          message += "\nErrors: " + Joiner.on(",").join(result.errors);
        }
        if (result.warnings.length > 0) {
          message += "\nWarnings: " + Joiner.on(",").join(result.warnings);
        }
        throw new RuntimeException(message);
      }
      // Gather ReactComponent prototype methods.
      NodeTraversal.traverseEs6(
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
      // Inject ReactDOMFactories methods for each tag, of the form:
      // /**
      // * @param {Object=} props
      // * @param {...ReactChildrenArgument} children
      // * @return {ReactDOMElement}
      // */
      // ReactDOMFactories.prototype.<tagName> = function(props, children) {};
      Node tagFuncNode = IR.function(
          IR.name(""),
          IR.paramList(IR.name("props"), IR.name("children")),
          IR.block());
      JSDocInfoBuilder jsDocBuilder = new JSDocInfoBuilder(true);
      jsDocBuilder.recordParameter(
          "props",
          new JSTypeExpression(
            new Node(Token.EQUALS, IR.string("Object")),
            TYPES_JS_RESOURCE_PATH));
      jsDocBuilder.recordParameter(
          "children",
          new JSTypeExpression(
            new Node(Token.ELLIPSIS, IR.string("ReactChildrenArgument")),
            TYPES_JS_RESOURCE_PATH));
      jsDocBuilder.recordReturnType(new JSTypeExpression(
          IR.string("ReactDOMElement"), TYPES_JS_RESOURCE_PATH));
      tagFuncNode.setJSDocInfo(jsDocBuilder.build());
      for (String tagName : REACT_DOM_TAG_NAMES) {
        templateTypesNode.addChildToBack(NodeUtil.newQNameDeclaration(
          compiler,
          "ReactDOMFactories.prototype." + tagName,
          tagFuncNode.cloneTree(),
          null));
      }
    }

    Node typesNode = templateTypesNode.cloneTree();
    boolean foundReactSource = false;
    for (Node inputNode : root.children()) {
      if (inputNode.getToken() == Token.SCRIPT &&
          inputNode.getSourceFileName() != null &&
          React.isReactSourceName(inputNode.getSourceFileName())) {
        Node typesChildren = typesNode.getFirstChild();
        typesNode.removeChildren();
        inputNode.addChildrenToFront(typesChildren);
        foundReactSource = true;
        stripPropTypes = addCreateElementAlias = React.isReactMinSourceName(
            inputNode.getSourceFileName());
        if (addCreateElementAlias) {
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
              IR.string("Function"), inputNode.getSourceFileName()));
          createElementAliasNode.setJSDocInfo(jsDocBuilder.build());
          inputNode.addChildToBack(createElementAliasNode);
        }
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
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
    // Inline React.createMixin calls, since they're just decorators.
    boolean inlinedMixin = false;
    for (Node mixinSpecNode : reactMixinsByName.values()) {
      Node mixinSpecParentNode = mixinSpecNode.getParent();
      if (mixinSpecParentNode.isCall() &&
          mixinSpecParentNode.hasMoreThanOneChild() &&
          mixinSpecParentNode.getFirstChild().getQualifiedName().equals(
            "React.createMixin")) {
        mixinSpecNode.detachFromParent();
        mixinSpecParentNode.getParent().replaceChild(
          mixinSpecParentNode,
          mixinSpecNode);
        inlinedMixin = true;
      }
    }
    if (inlinedMixin) {
      compiler.reportCodeChange();
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
      Node parent) {
    // Don't want React itself to get annotated (the version with addons creates
    // defines some classes).
    if (n.getToken() == Token.SCRIPT &&
        n.getSourceFileName() != null &&
        React.isReactSourceName(n.getSourceFileName())) {
      return false;
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (isReactCreateClass(n)) {
      visitReactCreateClass(n);
    } else if (isReactCreateMixin(n)) {
      visitReactCreateMixin(n);
    } else if (visitMixinAbstractMethod(n)) {
      // Nothing more needs to be done, mixin abstract method processing is
      // more efficiently done in one function intead of two.
    } else if (isReactCreateElement(n)) {
      visitReactCreateElement(t, n);
    }
  }

  private static boolean isReactCreateClass(Node value) {
    if (value != null && value.isCall()) {
      return value.getFirstChild().matchesQualifiedName("React.createClass");
    }
    return false;
  }

  private void visitReactCreateClass(Node callNode) {
    visitReactCreateType(
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

  private void visitReactCreateMixin(Node callNode) {
    visitReactCreateType(
        callNode,
        "React.createMixin",
        reactMixinsByName,
        reactMixinInterfacePrototypePropsByName);
  }

  private void visitReactCreateType(
        Node callNode, String createFuncName,
        Map<String, Node> typeSpecNodesByName,
        Map<String, Node> interfacePrototypePropsByName) {
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
      compiler.report(JSError.make(
          callParentNode, COULD_NOT_DETERMINE_TYPE_NAME, createFuncName));
      return;
    }
    String interfaceTypeName = typeName + "Interface";

    // Add the @typedef
    JSDocInfoBuilder jsDocBuilder = newJsDocInfoBuilderForNode(typeAttachNode);
    jsDocBuilder.recordTypedef(new JSTypeExpression(
        IR.string(interfaceTypeName), callNode.getSourceFileName()));
    typeAttachNode.setJSDocInfo(jsDocBuilder.build());

    // Record the type so that we can later look it up in React.createElement
    // calls.
    typeSpecNodesByName.put(typeName, specNode);

    // Gather methods for the interface definition.
    Node interfacePrototypeProps = IR.objectlit();
    interfacePrototypePropsByName.put(typeName, interfacePrototypeProps);
    Map<String, JSDocInfo> abstractMethodJsDocsByName = Maps.newHashMap();
    Node propTypesNode = null;
    Node getDefaultPropsNode = null;
    Map<String, JSDocInfo> staticsJsDocs = Maps.newHashMap();
    boolean usesPureRenderMixin = false;
    boolean hasShouldComponentUpdate = false;
    for (Node key : specNode.children()) {
      String keyName = key.getString();
      if (keyName.equals("mixins")) {
        Set<String> mixinNames =
            addMixinsToType(key, interfacePrototypeProps, staticsJsDocs);
        usesPureRenderMixin = mixinNames.contains(REACT_PURE_RENDER_MIXIN_NAME);
        for (String mixinName : mixinNames) {
          if (mixinAbstractMethodJsDocsByName.containsKey(mixinName)) {
            abstractMethodJsDocsByName.putAll(
              mixinAbstractMethodJsDocsByName.get(mixinName));
          }
        }
        continue;
      }
      if (keyName.equals("propTypes")) {
        propTypesNode = key;
        continue;
      }
      if (keyName.equals("getDefaultProps")) {
        getDefaultPropsNode = key;
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
        mergeInJsDoc(key, func, componentMethodJsDoc);
      }
      // Ditto for abstract methods from mixins.
      JSDocInfo abstractMethodJsDoc = abstractMethodJsDocsByName.get(keyName);
      if (abstractMethodJsDoc != null) {
        mergeInJsDoc(key, func, abstractMethodJsDoc);
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
          new Node(Token.STAR) : IR.string(typeName);
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
    if (propTypesNode != null && stripPropTypes &&
        (propTypesNode.getJSDocInfo() == null ||
            !propTypesNode.getJSDocInfo().makesStructs())) {
      propTypesNode.detachFromParent();
    }

    // Add a "<type name>Element" @typedef for the element type of this class.
    jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordTypedef(new JSTypeExpression(
        createReactElementTypeExpressionNode(typeName),
        callNode.getSourceFileName()));
    Node elementTypedefNode = NodeUtil.newQName(
        compiler, typeName + "Element");
    if (elementTypedefNode.isName()) {
      elementTypedefNode = IR.var(elementTypedefNode);
    }
    elementTypedefNode.setJSDocInfo(jsDocBuilder.build());
    if (!elementTypedefNode.isVar()) {
      elementTypedefNode = IR.exprResult(elementTypedefNode);
    }
    elementTypedefNode.useSourceInfoFromForTree(callParentNode);
    Node elementTypedefInsertionPoint = callParentNode.getParent();
    elementTypedefInsertionPoint.getParent().addChildAfter(
        elementTypedefNode, elementTypedefInsertionPoint);

    // Generate statics property JSDocs, so that the compiler knows about them.
    if (createFuncName.equals("React.createClass")) {
      Node staticsInsertionPoint = callParentNode.getParent();
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
    interfaceTypeFunctionNode.setJSDocInfo(jsDocBuilder.build());
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

    if (propTypesNode != null &&
        PropTypesExtractor.canExtractPropTypes(propTypesNode)) {
      if (propTypesTypeChecking) {
        PropTypesExtractor extractor = new PropTypesExtractor(
            propTypesNode, getDefaultPropsNode, typeName, interfaceTypeName,
            compiler);
        extractor.extract();
        extractor.insert(elementTypedefInsertionPoint);
        propTypesExtractorsByName.put(typeName, extractor);
      } else {
        PropTypesExtractor.cleanUpPropTypesWhenNotChecking(propTypesNode);
      }
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
  private boolean visitMixinAbstractMethod(Node value) {
    if (value == null || !value.isExprResult() || !value.hasOneChild() ||
        !value.getFirstChild().isGetProp()) {
      return false;
    }
    Node getPropNode = value.getFirstChild();
    if (!getPropNode.isQualifiedName() || !getPropNode.hasChildren()) {
      return false;
    }
    String mixinName = getPropNode.getFirstChild().getQualifiedName();
    Node mixinSpecNode = reactMixinsByName.get(mixinName);
    if (mixinSpecNode == null) {
      return false;
    }
    String methodName = getPropNode.getLastChild().getString();
    JSDocInfo abstractFuncJsDoc = getPropNode.getJSDocInfo();

    Node abstractFuncParamList = IR.paramList();
    if (abstractFuncJsDoc != null) {
      for (String parameterName : abstractFuncJsDoc.getParameterNames()) {
        abstractFuncParamList.addChildToBack(IR.name(parameterName));
      }
      Map<String, JSDocInfo> jsDocsByName =
          mixinAbstractMethodJsDocsByName.get(mixinName);
      if (jsDocsByName == null) {
        jsDocsByName = Maps.newHashMap();
        mixinAbstractMethodJsDocsByName.put(mixinName, jsDocsByName);
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
        reactMixinInterfacePrototypePropsByName.get(mixinName);
    addFuncToInterface(
        methodName,
        abstractFuncNode,
        interfacePrototypeProps,
        getPropNode.getJSDocInfo());

    return true;
  }

  private Set<String> addMixinsToType(
      Node mixinsNode,
      Node interfacePrototypeProps,
      Map<String, JSDocInfo> staticsJsDocs) {
    Set<String> mixinNames = Sets.newHashSet();
    if (!mixinsNode.hasOneChild() ||
          !mixinsNode.getFirstChild().isArrayLit()) {
      compiler.report(JSError.make(mixinsNode, MIXINS_UNEXPECTED_TYPE));
      return mixinNames;
    }
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
      if (mixinName.equals(REACT_PURE_RENDER_MIXIN_NAME)) {
        // Built-in mixin, there's nothing more that we need to do.
        continue;
      }
      Node mixinSpecNode = reactMixinsByName.get(mixinName);
      if (mixinSpecNode == null) {
        compiler.report(JSError.make(mixinNameNode, MIXIN_UNKNOWN, mixinName));
        continue;
      }
      for (Node mixinSpecKey : mixinSpecNode.children()) {
        String keyName = mixinSpecKey.getString();
        if (keyName.equals("mixins")) {
          mixinNames.addAll(addMixinsToType(
              mixinSpecKey, interfacePrototypeProps, staticsJsDocs));
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
    return mixinNames;
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
          methodNode.addChildToBack(funcChild.cloneTree());
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
        // doesn't flag this as useless code.
        // TODO: synthesize type based on value if it's a simple constant
        // like a function or number.
        staticJsDoc = new JSDocInfoBuilder(true).build(true);
      } else {
        staticJsDoc = staticJsDoc.clone();
      }
      staticsJsDocs.put(staticName, staticJsDoc);
    }
  }

  private static void mergeInJsDoc(Node key, Node func, JSDocInfo jsDoc) {
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
    if (jsDoc.hasReturnType()) {
      jsDocBuilder.recordReturnType(jsDoc.getReturnType());
    }
    for (String templateTypeName : jsDoc.getTemplateTypeNames()) {
      jsDocBuilder.recordTemplateTypeName(templateTypeName);
    }
    for (Map.Entry<String, Node> entry :
        jsDoc.getTypeTransformations().entrySet()) {
      jsDocBuilder.recordTypeTransformation(entry.getKey(), entry.getValue());
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
    }
    return false;
  }

  private void visitReactCreateElement(NodeTraversal t, Node callNode) {
    int paramCount = callNode.getChildCount() - 1;
    if (paramCount == 0) {
      compiler.report(JSError.make(callNode, CREATE_ELEMENT_UNEXPECTED_PARAMS));
      return;
    }

    if (addCreateElementAlias) {
      // If we're adding aliases that means we're doing an optimized build, so
      // there's no need for extra type checks.
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
      String typeName = typeNode.getQualifiedName();
      if (!reactClassesByName.containsKey(typeName)) {
        return;
      }
      elementTypeExpressionNode =
          createReactElementTypeExpressionNode(typeName);
      PropTypesExtractor propTypesExtractor =
          propTypesExtractorsByName.get(typeName);
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
    return propTypesExtractorsByName.get(typeName);
  }
}
