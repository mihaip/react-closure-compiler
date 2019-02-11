package info.persistent.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

public class Ast {
  public static void addModuleExport(String name, Node insertionPoint) {
      Node exportSpecNode = new Node(Token.EXPORT_SPEC).srcref(insertionPoint);
      Node nameNode = IR.name(name).srcref(insertionPoint);
      exportSpecNode.addChildToBack(nameNode);
      exportSpecNode.addChildToBack(nameNode.cloneNode());
      Node exportSpecsNode = new Node(Token.EXPORT_SPECS, exportSpecNode).srcref(insertionPoint);
      Node exportNode = IR.export(exportSpecsNode).srcref(insertionPoint);
      insertionPoint.getParent().addChildAfter(exportNode, insertionPoint);
  }
}
