package com.google.javascript.rhino;

/**
 * Helpers to access package-private methods
 */
public class JSDocInfoAccessor {
  public static void setJSDocInfoThisType(JSDocInfo jsDocInfo, JSTypeExpression type) {
    jsDocInfo.setThisType(type);
  }
}
