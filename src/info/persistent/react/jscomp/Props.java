package info.persistent.react.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Props-related utility code.
 */
public class Props {

  static boolean hasSpread(Node object) {
    for (Node property = object.getFirstChild();
        property != null;
        property = property.getNext()) {
      if (property.isSpread()) {
        return true;
      }
    }
    return false;
  }

  static Node transformSpreadObjectToObjectAssign(Node object) {
    // Transforms
    //   {a: 1, b: 2, ...c, d: 3, e: 4}
    // into
    //   Object.assign({a: 1, b: 2}, c, {d: 3, e: 4})
    //
    // Transforms
    //   {...a, b: 1, c: 2}
    // into
    //   Object.assign({}, a, {b: 1, c: 1})
    //
    // Transforms
    //   {...a}
    // into
    //   a
    Node objectAssignNode = IR.call(IR.getprop(IR.name("Object"), "assign"));
    boolean isFirst = true;
    for (Node property = object.getFirstChild(); property != null; ) {
      if (property.isSpread()) {
        if (isFirst) {
          // Need to add an empty object to not mutate value.
          objectAssignNode.addChildToBack(IR.objectlit());
        }
        Node spreadExpression = property.getFirstChild();
        Node next = property.getNext();
        property.detach();
        spreadExpression.detach();
        objectAssignNode.addChildToBack(spreadExpression);
        property = next;
      } else {
        Node spreadObject = IR.objectlit();
        while (property != null) {
          if (property.isSpread()) {
            break;
          }
          Node next = property.getNext();
          property.detach();
          spreadObject.addChildToBack(property);
          property = next;
        }
        objectAssignNode.addChildToBack(spreadObject);
      }
      isFirst = false;
    }

    if (objectAssignNode.getChildCount() == 2) {
      // No need for Object.assign({...})
      Node arg = objectAssignNode.getChildAtIndex(1);
      arg.detach();
      objectAssignNode = arg;
    } else if (objectAssignNode.getChildCount() == 3 &&
        objectAssignNode.getSecondChild().getChildCount() == 0) {
      // Or Object.assign({}, {...})
      Node arg = objectAssignNode.getChildAtIndex(2);
      arg.detach();
      objectAssignNode = arg;
    }

    Node prevNode = object.getPrevious();
    Node parentNode = object.getParent();
    object.detach();
    parentNode.addChildAfter(objectAssignNode, prevNode);
    return objectAssignNode;
  }

}
