package info.persistent.react.jscomp;

/**
 * React-related utility code.
 */
public class React {
  public static boolean isReactSourceName(String sourceName) {
    return sourceName.endsWith("/react.js") ||
        sourceName.endsWith("/react.min.js") ||
        sourceName.endsWith("/react-dom.js") ||
        sourceName.endsWith("/react-dom.min.js") ||
        sourceName.endsWith("/react-dom-server.js") ||
        sourceName.endsWith("/react-dom-server.min.js") ||
        sourceName.endsWith("/react-with-addons.js") ||
        sourceName.endsWith("/react-with-addons.min.js");
  }

  public static boolean isReactMinSourceName(String sourceName) {
    return sourceName.endsWith("/react.min.js") ||
        sourceName.endsWith("/react-dom.min.js") ||
        sourceName.endsWith("/react-dom-server.min.js") ||
        sourceName.endsWith("/react-with-addons.min.js");
  }
}
