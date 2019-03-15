package com.google.javascript.rhino;

/**
 * Helpers to access package-private methods
 */
public class JSDocInfoAccessor {
  public static void setJSDocInfoThisType(JSDocInfo jsDocInfo, JSTypeExpression type) {
    jsDocInfo.setThisType(type);
  }

  public static void setJSDocExport(JSDocInfo jsDocInfo, boolean export) {
    jsDocInfo.setExport(export);
  }
}
