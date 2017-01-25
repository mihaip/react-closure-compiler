package info.persistent.react.jscomp;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.javascript.jscomp.SourceFile;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * React-related utility code.
 */
public class React {
  static final String TYPES_JS_RESOURCE_PATH =
        "info/persistent/react/jscomp/types.js";
  static final Map<String, String> REACT_MODULES =
      ImmutableMap.<String, String>builder()
          .put("React", "ReactModule")
          .put("ReactDOM", "ReactDOMModule")
          .put("ReactDOMServer", "ReactDOMServerModule")
          .build();

  private static final String[] REACT_DOM_TAG_NAMES = {
    // HTML
    "a", "abbr", "address", "area", "article", "aside", "audio", "b", "base",
    "bdi", "bdo", "big", "blockquote", "body", "br", "button", "canvas",
    "caption", "cite", "code", "col", "colgroup", "data", "datalist", "dd",
    "del", "details", "dfn", "dialog", "div", "dl", "dt", "em", "embed",
    "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3",
    "h4", "h5", "h6", "head", "header", "hgroup", "hr", "html", "i", "iframe",
    "img", "input", "ins", "kbd", "keygen", "label", "legend", "li", "link",
    "main", "map", "mark", "menu", "menuitem", "meta", "meter", "nav",
    "noscript", "object", "ol", "optgroup", "option", "output", "p", "param",
    "picture", "pre", "progress", "q", "rp", "rt", "ruby", "s", "samp",
    "script", "section", "select", "small", "source", "span", "strong",
    "style", "sub", "summary", "sup", "table", "tbody", "td", "textarea",
    "tfoot", "th", "thead", "time", "title", "tr", "track", "u", "ul", "var",
    "video", "wbr",
    // SVG
    "circle", "clipPath", "defs", "ellipse", "g", "image", "line",
    "linearGradient", "mask", "path", "pattern", "polygon", "polyline",
    "radialGradient", "rect", "stop", "svg", "text", "tspan"
  };

  // From https://github.com/facebook/react/blob/c7129ce1f0bba7d04e9d5fce806a/
  // src/renderers/dom/shared/eventPlugins/..
  private static final String[] REACT_EVENT_NAMES = {
    // SimpleEventPlugin.js
    "abort", "animationEnd", "animationIteration", "animationStart", "blur",
    "canPlay", "canPlayThrough", "click", "contextMenu", "copy", "cut",
    "doubleClick", "drag", "dragEnd", "dragEnter", "dragExit", "dragLeave",
    "dragOver", "dragStart", "drop", "durationChange", "emptied", "encrypted",
    "ended", "error", "focus", "input", "invalid", "keyDown", "keyPress",
    "keyUp", "load", "loadedData", "loadedMetadata", "loadStart", "mouseDown",
    "mouseMove", "mouseOut", "mouseOver", "mouseUp", "paste", "pause", "play",
    "playing", "progress", "rateChange", "reset", "scroll", "seeked", "seeking",
    "stalled", "submit", "suspend", "timeUpdate", "touchCancel", "touchEnd",
    "touchMove", "touchStart", "transitionEnd", "volumeChange", "waiting",
    "wheel",
    // TapEventPlugin.js
    "touchTap",
    // SelectEventPlugin.js
    "select",
    // EnterLeaveEventPlugin.js
    "mouseEnter", "mouseLeave",
    // ChangeEventPlugin.js
    "change",
    // BeforeInputEventPlugin.js
    "beforeInput", "compositionEnd", "compositionStart", "compositionUpdate"
  };

  private static String typesJs = null;

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

  public static synchronized String getTypesJs() {
    if (typesJs != null) {
      return typesJs;
    }

    StringBuilder typesJsBuilder;
    URL typesUrl = Resources.getResource(TYPES_JS_RESOURCE_PATH);
    try {
      typesJsBuilder = new StringBuilder(
        Resources.toString(typesUrl, Charsets.UTF_8));
    } catch (IOException e) {
      throw new RuntimeException(e); // Should never happen
    }

    // Inject ReactDOMFactories methods for each tag
    for (String tagName : REACT_DOM_TAG_NAMES) {
      typesJsBuilder.append(
        "/**\n" +
        " * @param {Object=} props\n" +
        " * @param {...ReactChildrenArgument} children\n" +
        " * @return {ReactDOMElement}\n" +
        " */\n" +
        "ReactDOMFactories.prototype." + tagName +
            " = function(props, children) {};\n");
    }

    // Inject ReactDOMProps properties for each event name, for both the
    // regular form and the capture one.
    for (String eventName : REACT_EVENT_NAMES) {
      String onEventName = "on" + eventName.substring(0, 1).toUpperCase() +
          eventName.substring(1);
      typesJsBuilder.append(
          "/**\n" +
          " * @type {ReactEventHandler}\n" +
          " */\n" +
          "ReactDOMProps.prototype." + onEventName + ";\n");
      typesJsBuilder.append(
          "/**\n" +
          " * @type {ReactEventHandler}\n" +
          " */\n" +
          "ReactDOMProps.prototype." + onEventName + "Capture;\n");
    }

    typesJs = typesJsBuilder.toString();
    return typesJs;
  }

  public static SourceFile createExternsSourceFile() {
      String externsJs = "";
      for (Map.Entry<String, String> entry : REACT_MODULES.entrySet()) {
        String moduleName = entry.getKey();
        String moduleType = entry.getValue();
        externsJs += "/** @type {"  + moduleType + "} */ " +
          "var " + moduleName + ";\n";
      }
      externsJs += getTypesJs();

      return SourceFile.builder()
            .buildFromCode("react.js", externsJs);
  }
}
