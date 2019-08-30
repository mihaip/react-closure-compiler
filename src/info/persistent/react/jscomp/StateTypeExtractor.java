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

import java.util.List;

/**
 * Adds type information for component state. Assumes that getInitialState
 * is annotated with a @return record type. It then turns that into a
 * Comp.State typedef and uses that as the type of this.state, as well as the
 * parameter type for this.setState().
 */
class StateTypeExtractor {
  static final DiagnosticType UNEXPECTED_STATE_TYPE = DiagnosticType.warning(
      "REACT_UNEXPECTED_STATE_TYPE",
      "{0} must use a record type as the return type for 'getInitialState'");
  private static final JSTypeExpression REACT_STATE_TYPE = new JSTypeExpression(
      IR.string("ReactState"), null);
  private static final JSTypeExpression REACT_STATE_TYPE_BANG = new JSTypeExpression(
      new Node(Token.BANG, IR.string("ReactState")), null);
  private static final JSTypeExpression REACT_STATE_TYPE_QMARK = new JSTypeExpression(
      new Node(Token.QMARK, IR.string("ReactState")), null);

  private final String sourceFileName;
  private final String stateTypeName;
  private final String partialStateTypeName;
  private final String interfaceTypeName;
  private final Compiler compiler;
  private final JSTypeExpression stateType;

  public StateTypeExtractor(
      Node stateNode,
      String typeName,
      String interfaceTypeName,
      Compiler compiler) {
    this.sourceFileName = stateNode.getSourceFileName();
    this.stateTypeName = typeName + ".State";
    this.partialStateTypeName = typeName + ".PartialState";
    this.interfaceTypeName = interfaceTypeName;
    this.compiler = compiler;

    JSDocInfo stateJsDoc = stateNode.getJSDocInfo();
    JSTypeExpression stateType = stateJsDoc != null ? stateJsDoc.getReturnType() : null;

    // ?ReactStateType is the default return type that we pick up from types.js,
    // also treat that as a missing return type.
    if (stateType != null && stateType.equals(REACT_STATE_TYPE_QMARK)) {
      stateType = null;
    }

    if (stateType != null) {
        Node root = stateType.getRoot();
        if (root.getToken() == Token.LC && root.getFirstChild().getToken() == Token.LB) {
            // { ... } is OK
        } else if (root.isString() && root.getString().equals("null")) {
            // null is OK
        } else {
            compiler.report(JSError.make(
                stateNode, UNEXPECTED_STATE_TYPE, typeName));
            stateType = null;
        }
    }

    // Use {null} if nothing else.
    if (stateType == null) {
        stateType = new JSTypeExpression(IR.string("null"), stateNode.getSourceFileName());
    }

    this.stateType = stateType;
  }

  public boolean hasStateType() {
      return this.stateType != null;
  }

  public void insert(Node insertionPoint) {
    // /** @typedef {{
    //   fieldA: number,
    //   fieldB: string,
    //   ...
    // }} */
    // Comp.State;
    Node stateTypedefNode = insertTypedefNode(insertionPoint, false);
    insertionPoint = stateTypedefNode;

    // /** @typedef {{
    //   fieldA: (number|undefined),
    //   fieldB: (string|undefined),
    //   ...
    // }} */
    // Comp.PartialState;
    Node partialStateTypedefNode = insertTypedefNode(insertionPoint, true);
    insertionPoint = partialStateTypedefNode;

    // /** @type {Comp.State} */
    // CompInterface.prototype.state;
    JSDocInfoBuilder jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordType(
        new JSTypeExpression(IR.string(stateTypeName), sourceFileName));
    Node stateNode = NodeUtil.newQName(
        compiler, interfaceTypeName + ".prototype.state");
    stateNode.setJSDocInfo(jsDocBuilder.build());
    stateNode = IR.exprResult(stateNode);
    stateNode.useSourceInfoIfMissingFromForTree(insertionPoint);
    insertionPoint.getParent().addChildAfter(stateNode, insertionPoint);
    insertionPoint = stateNode;

    // /**
    // * @param {?Comp.PartialState|function(Comp.State, ReactProps): ?Comp.PartialState} stateOrFunction
    // * @param {function(): void=} callback
    // * @return {void}
    // */
    // CompInterface.prototype.setState;
    jsDocBuilder = new JSDocInfoBuilder(true);
    jsDocBuilder.recordParameter(
        "stateOrFunction",
        new JSTypeExpression(
            new Node(Token.PIPE,
            new Node(Token.QMARK, IR.string(partialStateTypeName)),
                new Node(Token.FUNCTION,
                    new Node(Token.PARAM_LIST,
                        IR.string(stateTypeName),
                        IR.string("ReactProps")),
                        new Node(Token.QMARK, IR.string(partialStateTypeName)))),
            sourceFileName));
    jsDocBuilder.recordParameter(
        "callback",
        JSTypeExpression.makeOptionalArg(
            new JSTypeExpression(
                new Node(Token.FUNCTION,
                    new Node(Token.VOID)),
                sourceFileName)));
    Node setStateNode = NodeUtil.newQName(
        compiler, interfaceTypeName + ".prototype.setState");
    setStateNode.setJSDocInfo(jsDocBuilder.build());
    setStateNode = IR.exprResult(setStateNode);
    setStateNode.useSourceInfoIfMissingFromForTree(insertionPoint);
    insertionPoint.getParent().addChildAfter(setStateNode, insertionPoint);
    insertionPoint = setStateNode;
  }

private Node insertTypedefNode(Node insertionPoint, boolean partial) {
    JSTypeExpression stateType = this.stateType;
    JSDocInfoBuilder jsDocBuilder = new JSDocInfoBuilder(true);
    if (partial) {
        Node partialNode = stateType.getRoot().cloneTree();
        // If type is null the partial type is also null.
        if (!partialNode.isString() || !partialNode.getString().equals("null")) {
            // LC -> LB, which has N COLON children (for each field). Each COLON
            // has two children, the key and the type.
            for (Node colonNode : partialNode.getFirstChild().children()) {
                Node typeNode = colonNode.getSecondChild();
                if (typeNode.getToken() != Token.PIPE) {
                    typeNode.detachFromParent();
                    typeNode = new Node(Token.PIPE, typeNode, IR.string("undefined"));
                    colonNode.addChildToBack(typeNode);
                } else {
                    boolean hadUndefinedInUnion = false;
                    for (Node child : typeNode.children()) {
                    if (child.isString() && child.getString().equals("undefined")) {
                        hadUndefinedInUnion = true;
                        break;
                    }
                    }
                    if (!hadUndefinedInUnion) {
                        typeNode.addChildToBack(IR.string("undefined"));
                    }
                }
            }
        }
        stateType = new JSTypeExpression(partialNode, sourceFileName);
    }
    jsDocBuilder.recordTypedef(stateType);

    Node stateTypedefNode = NodeUtil.newQName(compiler, partial ? partialStateTypeName : stateTypeName);
    stateTypedefNode.setJSDocInfo(jsDocBuilder.build());
    stateTypedefNode = IR.exprResult(stateTypedefNode);

    stateTypedefNode.useSourceInfoIfMissingFromForTree(insertionPoint);
    insertionPoint.getParent().addChildAfter(stateTypedefNode, insertionPoint);
    return stateTypedefNode;
  }

  public void addToComponentMethods(List<Node> componentMethodKeys) {
    // Changes the ReactState parameter type from built-in component methods to
    // the more specific state type.
    JSTypeExpression replacementType = new JSTypeExpression(
        IR.string(stateTypeName), sourceFileName);
    JSTypeExpression replacementTypeQMark = new JSTypeExpression(
        new Node(Token.QMARK, IR.string(stateTypeName)), sourceFileName);
    React.replaceComponentMethodParameterTypes(
        componentMethodKeys,
        ImmutableMap.<JSTypeExpression, JSTypeExpression>builder()
            .put(REACT_STATE_TYPE, replacementType)
            .put(REACT_STATE_TYPE_BANG, replacementType)
            .put(REACT_STATE_TYPE_QMARK, replacementTypeQMark)
            .build());
  }
}
