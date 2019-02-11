package com.google.javascript.jscomp;

import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;

/**
 * Helpers to access package-private methods
 */
public class ModulePathAccessor {
  public static ModulePath getVarInputModulePath(AbstractVar var) {
    return var.getInput().getPath();
  }

  public static ModulePath getInputModulePath(CompilerInput input) {
      return input.getPath();
  }
}
