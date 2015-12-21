package info.persistent.jscomp;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class AstDump {
  public static void main(String[] args) throws Exception {
    Compiler compiler = new Compiler(System.err);
    compiler.disableThreads(); // Makes errors easier to track down.

    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromInputStream("stdin", System.in)
    );

    CompilerOptions options = new CompilerOptions();
    Result result = compiler.compile(Collections.emptyList(), inputs, options);
    dump(compiler.getRoot(), 0, System.out);
    System.out.append("\n");
  }

  /**
   * Unlike {@code Node.toStringTree} this includes JSDocInfo inline.
   */
  private static void dump(Node n, int level, Appendable out)
      throws IOException {
    for (int i = 0; i != level; ++i) {
      out.append("    ");
    }
    out.append(n.toString(true, true, true));
    if (n.getJSDocInfo() != null) {
      out.append(" [jsdoc ");
      out.append(n.getJSDocInfo().toStringVerbose());
      out.append("]");
    }
    out.append('\n');
    for (Node child = n.getFirstChild();
         child != null;
         child = child.getNext()) {
      dump(child, level + 1, out);
    }
  }
}
