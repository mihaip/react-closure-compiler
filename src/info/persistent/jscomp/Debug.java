package info.persistent.jscomp;

import com.google.javascript.jscomp.CodePrinter;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.rhino.Node;

import java.io.IOException;

public class Debug {
  public static String toTypeAnnotatedSource(Compiler compiler, Node root) {
    CompilerOptions compilerOptions = new CompilerOptions();
    compilerOptions.setPreserveTypeAnnotations(true);
    compilerOptions.setLanguageIn(CompilerOptions.LanguageMode.STABLE_IN);
    compilerOptions.setLanguageOut(CompilerOptions.LanguageMode.NO_TRANSPILE);
    try {
      return new CodePrinter.Builder(root)
          .setCompilerOptions(compilerOptions)
          .setPrettyPrint(true)
          .setOutputTypes(true)
          .setTypeRegistry(compiler.getTypeRegistry())
          .build();
    } catch (NullPointerException e) {
      // https://github.com/google/closure-compiler/pull/3421
      return new CodePrinter.Builder(root)
          .setCompilerOptions(compilerOptions)
          .setPrettyPrint(true)
          .setOutputTypes(false)
          .setTypeRegistry(compiler.getTypeRegistry())
          .build();
    }
  }

  /**
   * Unlike {@code Node.toStringTree} this includes JSDocInfo inline.
   */
  public static String toStringTreeVerbose(Node root) {
      StringBuilder builder = new StringBuilder();
      try {
          dump(root, 0, builder);
      } catch (IOException err) {
          throw new RuntimeException(err);
      }
      return builder.toString();
  }

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
