package info.persistent.react.jscomp;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

public class ReactCompilerPass extends NodeTraversal.AbstractPostOrderCallback
    implements HotSwapCompilerPass {

  // Errors
  static final DiagnosticType CREATE_CLASS_TARGET_INVALID = DiagnosticType.error(
      "REACT_CREATE_CLASS_TARGET_INVALID",
      "Unsupported React.createClass(...) expression.");

  static final DiagnosticType CREATE_CLASS_SPEC_NOT_VALID = DiagnosticType.error(
      "REACT_CREATE_CLASS_SPEC_NOT_VALID",
      "The React.createClass(...) spec must be an object literal.");

  static final DiagnosticType CREATE_CLASS_UNEXPECTED_PARAMS = DiagnosticType.error(
      "REACT_CREATE_CLASS_UNEXPECTED_PARAMS",
      "The React.createClass(...) call has too many arguments.");

  static final String GENERATED_SOURCE_NAME = "<ReactCompilerPass-generated.js>";

  private final AbstractCompiler compiler;

  public ReactCompilerPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    hotSwapScript(root, null);
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

