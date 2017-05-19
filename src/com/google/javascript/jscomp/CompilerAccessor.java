package com.google.javascript.jscomp;

/**
 * Helpers to access package-private methods
 */
public class CompilerAccessor {
  public static CompilerInput getSynthesizedExternsInputAtEnd(Compiler compiler) {
    return compiler.getSynthesizedExternsInputAtEnd();
  }
}
