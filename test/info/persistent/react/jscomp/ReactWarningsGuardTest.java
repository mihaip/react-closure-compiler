package info.persistent.react.jscomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;

import org.junit.Test;

import java.util.List;

/**
 * Test {@link React}.
 */
public class ReactWarningsGuardTest {
  @Test public void testReactWarningsGuard() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.addWarningsGuard(new ReactWarningsGuard());
    // Should not warn about non-standard JSDoc.
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode(
          "/src/react.js", "/** @providesModule React */\nvar React"));
    List<SourceFile> externs = ImmutableList.of(
      SourceFile.fromCode("externs", ""));
    Result result = compiler.compile(externs, inputs, options);
    assertTrue(result.success);
    assertEquals(result.errors.length, 0);
    assertEquals(result.warnings.length, 0);
  }
}
