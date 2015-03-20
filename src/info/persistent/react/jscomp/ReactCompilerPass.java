package info.persistent.react.jscomp;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.io.IOException;
import java.net.URL;

public class ReactCompilerPass extends NodeTraversal.AbstractPostOrderCallback
    implements HotSwapCompilerPass {

  // Errors
  private static final DiagnosticType REACT_SOURCE_NOT_FOUND = DiagnosticType.error(
      "REACT_SOURCE_NOT_FOUND",
      "Could not find the React library source.");
  private static final DiagnosticType CREATE_CLASS_TARGET_INVALID = DiagnosticType.error(
      "REACT_CREATE_CLASS_TARGET_INVALID",
      "Unsupported React.createClass(...) expression.");

  private static final DiagnosticType CREATE_CLASS_SPEC_NOT_VALID = DiagnosticType.error(
      "REACT_CREATE_CLASS_SPEC_NOT_VALID",
      "The React.createClass(...) spec must be an object literal.");

  private static final DiagnosticType CREATE_CLASS_UNEXPECTED_PARAMS = DiagnosticType.error(
      "REACT_CREATE_CLASS_UNEXPECTED_PARAMS",
      "The React.createClass(...) call has too many arguments.");

  private static final String TYPES_JS_RESOURCE_PATH = "info/persistent/react/jscomp/types.js";
  private static final String EXTERNS_SOURCE_NAME = "<ReactCompilerPass-externs.js>";
  private static final String GENERATED_SOURCE_NAME = "<ReactCompilerPass-generated.js>";

  private final Compiler compiler;

  public ReactCompilerPass(AbstractCompiler compiler) {
    this.compiler = (Compiler) compiler;
  }

  @Override
  public void process(Node externs, Node root) {
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
        Node.newString("ReactStaticFunctions"), EXTERNS_SOURCE_NAME));
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
      visitReactCreateClass(t, n);
    }
  }

  private void visitReactCreateClass(NodeTraversal t, Node callNode) {
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

    // Add a @this {ReactComponent} annotation to all methods in the spec, to
    // avoid the compiler complaining dangerous use of "this" in a global
    // context.
    for (Node key : specNode.children()) {
      if (key.getChildCount() != 1 || !key.getFirstChild().isFunction()) {
        continue;
      }
      Node func = key.getFirstChild();
      JSDocInfoBuilder jsDocBuilder =
          JSDocInfoBuilder.maybeCopyFrom(func.getJSDocInfo());
      jsDocBuilder.recordThisType(new JSTypeExpression(
        Node.newString("ReactComponent"), GENERATED_SOURCE_NAME));

      func.setJSDocInfo(jsDocBuilder.build(func));
    }

    System.err.println("React.createClass call: " + callNode.toStringTree());
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
}

