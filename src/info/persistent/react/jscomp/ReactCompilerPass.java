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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
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
import java.util.Map;
import java.util.Set;

public class ReactCompilerPass extends AbstractPostOrderCallback
    implements HotSwapCompilerPass {

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
    "circle", "defs", "ellipse", "g", "line", "linearGradient", "mask", "path",
    "pattern", "polygon", "polyline", "radialGradient", "rect", "stop", "svg",
    "text", "tspan"
  };
  private static final String REACT_PURE_RENDER_MIXIN_NAME =
      "React.addons.PureRenderMixin";
  private static final String EXTERNS_SOURCE_NAME = "<ReactCompilerPass-externs.js>";
  private static final String GENERATED_SOURCE_NAME = "<ReactCompilerPass-generated.js>";

  private final Compiler compiler;
  private boolean stripPropTypes = false;
  private final Map<String, Node> reactClassesByName = Maps.newHashMap();
  private final Map<String, Node> reactClassInterfacePrototypePropsByName =
      Maps.newHashMap();
  private final Map<String, Node> reactMixinsByName = Maps.newHashMap();
  private final Map<String, Node> reactMixinInterfacePrototypePropsByName =
      Maps.newHashMap();
  // Mixin name -> method name -> JSDoc
  private final Map<String, Map<String, JSDocInfo>>
      mixinAbstractMethodJsDocsByName = Maps.newHashMap();

  public ReactCompilerPass(AbstractCompiler compiler) {
    this.compiler = (Compiler) compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    reactClassesByName.clear();
    reactClassInterfacePrototypePropsByName.clear();
    reactMixinsByName.clear();
    reactMixinInterfacePrototypePropsByName.clear();
    mixinAbstractMethodJsDocsByName.clear();
    addExterns();
    addTypes(root);
    hotSwapScript(root, null);
  }

  /**
   * The compiler isn't aware of the React symbol that is exported from React,
   *  inform it via an extern. Equivalent to a file with:
   *
   * /**
   *  * @type {ReactModule}
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
        IR.string("ReactModule"), EXTERNS_SOURCE_NAME));
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
   * Parameter and return types for built-in component methods, so that
   * implementations may be annotated automatically.
   */
  private static Map<String, JSDocInfo> componentMethodJsDocs =
      Maps.newHashMap();

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
      if (!result.success || result.errors.length > 0 ||
          result.warnings.length > 0) {
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
      NodeTraversal.traverse(
          compiler,
          templateTypesNode,
          new AbstractPostOrderCallback() {
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
      // Inject ReactDOM methods for each tag, of the form:
      // /**
      // * @param {Object=} props
      // * @param {...ReactChildrenArgument} children
      // * @return {ReactDOMElement}
      // */
      // ReactDOM.prototype.<tagName> = function(props, children) {};
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
      tagFuncNode.setJSDocInfo(jsDocBuilder.build(tagFuncNode));
      for (String tagName : REACT_DOM_TAG_NAMES) {
        templateTypesNode.addChildToBack(NodeUtil.newQNameDeclaration(
          compiler,
          "ReactDOM.prototype." + tagName,
          tagFuncNode.cloneTree(),
          null));
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
        stripPropTypes = React.isReactMinSourceName(
            inputNode.getSourceFileName());
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
      compiler.report(JSError.make(
          callParentNode, COULD_NOT_DETERMINE_TYPE_NAME, createFuncName));
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
    typeSpecNodesByName.put(typeName, specNode);

    // Gather methods for the interface definition.
    Node interfacePrototypeProps = IR.objectlit();
    interfacePrototypePropsByName.put(typeName, interfacePrototypeProps);
    Map<String, JSDocInfo> abstractMethodJsDocsByName = Maps.newHashMap();
    Node propTypesNode = null;
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
      if (keyName.equals("statics")) {
        if (createFuncName == "React.createClass") {
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
        mergeInJsDoc(func, componentMethodJsDoc);
      }
      // Ditto for abstract methods from mixins.
      JSDocInfo abstractMethodJsDoc = abstractMethodJsDocsByName.get(keyName);
      if (abstractMethodJsDoc != null) {
        mergeInJsDoc(func, abstractMethodJsDoc);
      }

      // Gather method signatures so that we can declare them where the compiler
      // can see them.
      addFuncToInterface(keyName, func, interfacePrototypeProps);

      // Add a @this {<type name>} annotation to all methods in the spec, to
      // avoid the compiler complaining dangerous use of "this" in a global
      // context.
      jsDocBuilder = JSDocInfoBuilder.maybeCopyFrom(func.getJSDocInfo());
      jsDocBuilder.recordThisType(new JSTypeExpression(
        IR.string(typeName), GENERATED_SOURCE_NAME));
      func.setJSDocInfo(jsDocBuilder.build(func));
    }

    if (usesPureRenderMixin && hasShouldComponentUpdate) {
      compiler.report(JSError.make(
          specNode, PURE_RENDER_MIXIN_SHOULD_COMPONENT_UPDATE_OVERRIDE, typeName));
      return;
    }

    if (propTypesNode != null && stripPropTypes) {
      propTypesNode.detachFromParent();
    }

    // Generate statics property JSDocs, so that the compiler knows about them.
    if (createFuncName == "React.createClass") {
      Node staticsInsertionPoint = callParentNode.getParent();
      for (Map.Entry<String, JSDocInfo> entry : staticsJsDocs.entrySet()) {
        String staticName = entry.getKey();
        JSDocInfo staticJsDoc = entry.getValue();
        Node staticDeclaration = NodeUtil.newQName(
            compiler, typeName + "." + staticName);
        staticDeclaration.setJSDocInfo(staticJsDoc);
        Node staticExprNode = IR.exprResult(staticDeclaration);
        staticsInsertionPoint.getParent().addChildAfter(
            staticExprNode, staticsInsertionPoint);
        staticsInsertionPoint = staticExprNode;
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
    if (abstractFuncJsDoc != null) {
      abstractFuncNode.setJSDocInfo(abstractFuncJsDoc.clone());
    }
    Node interfacePrototypeProps =
        reactMixinInterfacePrototypePropsByName.get(mixinName);
    addFuncToInterface(methodName, abstractFuncNode, interfacePrototypeProps);

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
        if (mixinSpecKey.hasOneChild() &&
            mixinSpecKey.getFirstChild().isFunction()) {
          addFuncToInterface(
              keyName, mixinSpecKey.getFirstChild(), interfacePrototypeProps);
        }
      }
    }
    return mixinNames;
  }

  private static void addFuncToInterface(
      String name, Node funcNode, Node interfacePrototypeProps) {
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
    interfacePrototypeProps.addChildrenToBack(
      IR.stringKey(name, methodNode));
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
        staticJsDoc = new JSDocInfo(true);
      } else {
        staticJsDoc = staticJsDoc.clone();
      }
      staticsJsDocs.put(staticName, staticJsDoc);
    }
  }

  private static void mergeInJsDoc(Node func, JSDocInfo jsDoc) {
    JSDocInfoBuilder funcJsDocBuilder =
        JSDocInfoBuilder.maybeCopyFrom(func.getJSDocInfo());
    for (String parameterName : jsDoc.getParameterNames()) {
      JSTypeExpression parameterType = jsDoc.getParameterType(parameterName);
      funcJsDocBuilder.recordParameter(parameterName, parameterType);
    }
    if (jsDoc.hasReturnType()) {
      funcJsDocBuilder.recordReturnType(jsDoc.getReturnType());
    }
    func.setJSDocInfo(funcJsDocBuilder.build(func));
  }

  private boolean validateCreateTypeUsage(Node n) {
    // There are only two valid usage patterns for of React.create{Class|Mixin}:
    //   var ClassName = React.create{Class|Mixin}({...})
    //   namespace.ClassName = React.create{Class|Mixin}({...})
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
    if (callNode.getParent().getType() == Token.CAST) {
      // There's already a cast around the call, there's no need to add another.
      return;
    }

    // Add casts of the form /** @type {ReactElement.<type name>} */ around
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
      elementTypeExpressionNode = IR.string("ReactElement");
      elementTypeExpressionNode.addChildToFront(IR.block());
      elementTypeExpressionNode.getFirstChild().addChildToFront(IR.string(typeName));
    }

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
