package info.persistent.react.jscomp;

import com.google.common.collect.Maps;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.ModulePathAccessor;
import com.google.javascript.jscomp.Scope;
import com.google.javascript.jscomp.Var;
import com.google.javascript.rhino.Node;

import java.util.Collection;
import java.util.Map;

class SymbolTable<V> {
  private final Map<String, V> map = Maps.newHashMap();

  public Collection<V> values() {
    return map.values();
  }

  public void put(Node nameNode, V value, CompilerInput exportInput) {
    map.put(writeKey(nameNode, exportInput), value);
    if (exportInput != null) {
        // Also allow the value to be accessed from within the same module
        map.put(writeKey(nameNode, null), value);
    }
  }

  public V get(Scope scope, Node nameNode) {
    return map.get(readKey(scope, nameNode));
  }

  public V getByName(String name) {
    return map.get(name);
  }

  public void clear() {
    map.clear();
  }

  public boolean containsName(Scope scope, Node nameNode) {
    return map.containsKey(readKey(scope, nameNode));
  }

  public boolean containsNamePrefix(String prefixCandidate, CompilerInput exportInput) {
    return map.containsKey(prefixCandidate) || map.containsKey(writeKey(prefixCandidate, exportInput));
  }

  private static String writeKey(Node nameNode, CompilerInput exportInput) {
    return writeKey(nameNode.getQualifiedName(), exportInput);
  }

  private static String writeKey(String name, CompilerInput exportInput) {
    String key = name;
    if (exportInput != null) {
        key = ModulePathAccessor.getInputModulePath(exportInput).toString() + "|" + key;
    }
    return key;
  }

  private static String readKey(Scope scope, Node nameNode) {
    if (!scope.isModuleScope()) {
      return nameNode.getQualifiedName();
    }
    String[] namePieces = nameNode.getQualifiedName().split("\\.");
    if (namePieces.length == 1) {
      return nameNode.getQualifiedName();
    }
    Var nameVar = scope.getVar(namePieces[0]);
    if (nameVar != null && nameVar.getNode().isImportStar()) {
        Node moduleIdentifier = nameVar.getNode().getNext();
        String importPath = ModulePathAccessor.getVarInputModulePath(nameVar).resolveModuleAsPath(moduleIdentifier.getString()).toString();
        if (importPath.endsWith(".jsx.js")) {
            importPath = importPath.substring(0, importPath.length() - 3);
        }
        String key = importPath + "|";
        for (int i = 1; i < namePieces.length; i++) {
            if (i > 1) {
                key += ".";
            }
            key += namePieces[i];
        }
        return key;
    }
    return nameNode.getQualifiedName();
  }

  public void debugDump(String label) {
      System.err.println(label + " contents: ");
      for (Map.Entry<String, V> entry : map.entrySet()) {
          System.err.println("  " + entry.getKey() + " => " + entry.getValue());
      }
  }
}
