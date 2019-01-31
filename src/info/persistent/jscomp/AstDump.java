package info.persistent.jscomp;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;

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
    System.out.println(Debug.toStringTreeVerbose(compiler.getRoot()));
    System.out.append("\n");
  }
}
