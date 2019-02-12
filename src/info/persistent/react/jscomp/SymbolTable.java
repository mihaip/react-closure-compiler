package info.persistent.react.jscomp;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.ModulePathAccessor;
import com.google.javascript.jscomp.Scope;
import com.google.javascript.jscomp.Var;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.rhino.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

class SymbolTable<V> {
  private final Map<String, V> map = Maps.newHashMap();

  public Collection<V> values() {
    return map.values();
  }

  public void put(Node nameNode, V value, CompilerInput exportInput) {
    map.put(writeKey(nameNode, exportInput), value);
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

  public <V2> void mapValuesInto(Function<V, V2> mapper, SymbolTable<V2> destTable) {
    for (Map.Entry<String, V> entry : map.entrySet()) {
      destTable.map.put(entry.getKey(), mapper.apply(entry.getValue()));
    }
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
    String name = nameNode.getQualifiedName();
    if (!scope.isModuleScope()) {
      return name;
    }
    String[] namePieces = name.split("\\.");
    Var nameVar = scope.getVar(namePieces[0]);
    if (nameVar == null) {
      return name;
    }
    ModulePath modulePath = ModulePathAccessor.getVarInputModulePath(nameVar);
    if (namePieces.length == 1) {
      return readKey(modulePath, name);
    }

    if (nameVar.getNode().isImportStar()) {
        Node moduleIdentifier = nameVar.getNode().getNext();
        ModulePath importPath = modulePath.resolveModuleAsPath(moduleIdentifier.getString());
        String nameRemainder = Joiner.on(".").join(Arrays.copyOfRange(namePieces, 1, namePieces.length));
        return readKey(importPath, nameRemainder);
    }
    return name;
  }

  private static String readKey(ModulePath modulePath, String name) {
      String key = modulePath.toString();
      // Undo ModuleResolver.resolveModuleAsPath adding .js extensions to .jsx
      // files.
      if (key.endsWith(".jsx.js")) {
          key = key.substring(0, key.length() - 3);
      }
      return modulePath + "|" + name;
  }

  public void debugDump(String label) {
      System.err.println(label + " contents: ");
      for (Map.Entry<String, V> entry : map.entrySet()) {
          System.err.println("  " + entry.getKey() + " => " + entry.getValue());
      }
  }
}
