package info.persistent.react.jscomp;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * React-related utility code.
 */
public class React {
  static final String TYPES_JS_RESOURCE_PATH =
        "info/persistent/react/jscomp/types.js";

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

  public static void replaceComponentMethodParameterTypes(
      List<Node> componentMethodKeys,
      Map<JSTypeExpression, JSTypeExpression> replacements) {
    for (Node key : componentMethodKeys) {
      boolean changedParameterType = false;
      JSDocInfo existing = key.getJSDocInfo();
      // Unfortunately we can't override the type of already-declared
      // parameters, so we need to recreate the entire JSDocInfo with the new
      // type.
      JSDocInfoBuilder jsDocBuilder = new JSDocInfoBuilder(true);
      for (String parameterName : existing.getParameterNames()) {
        JSTypeExpression parameterType = existing.getParameterType(parameterName);
        // JSTypeExpression does not implement hashCode correctly, therefore we
        // can't get() to find in the map.
        for (Map.Entry<JSTypeExpression, JSTypeExpression> entry: replacements.entrySet()) {
          if (entry.getKey().equals(parameterType)) {
            parameterType = entry.getValue();
            changedParameterType = true;
            break;
          }
        }
        jsDocBuilder.recordParameter(parameterName, parameterType);
      }
      if (!changedParameterType) {
        continue;
      }
      if (existing.hasReturnType()) {
        jsDocBuilder.recordReturnType(existing.getReturnType());
      }
      if (existing.hasThisType()) {
        jsDocBuilder.recordThisType(existing.getThisType());
      }
      if (existing.isOverride()) {
        jsDocBuilder.recordOverride();
      }
      for (String templateTypeName : existing.getTemplateTypeNames()) {
        jsDocBuilder.recordTemplateTypeName(templateTypeName);
      }
      for (Map.Entry<String, Node> entry :
          existing.getTypeTransformations().entrySet()) {
        jsDocBuilder.recordTypeTransformation(entry.getKey(), entry.getValue());
      }
      key.setJSDocInfo(jsDocBuilder.build());
    }
  }
}
