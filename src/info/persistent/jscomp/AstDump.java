package info.persistent.jscomp;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public class AstDump {
  public static void main(String[] args) throws Exception {
    Compiler compiler = new Compiler(System.err);
    compiler.disableThreads(); // Makes errors easier to track down.

    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromInputStream("stdin", System.in, StandardCharsets.UTF_8)
    );

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2018);
    options.setLanguageOut(CompilerOptions.LanguageMode.NO_TRANSPILE);
    options.skipAllCompilerPasses();
    options.setEs6ModuleTranspilation(CompilerOptions.Es6ModuleTranspilation.NONE);
    Result result = compiler.compile(
        Collections.<SourceFile>emptyList(), inputs, options);
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
