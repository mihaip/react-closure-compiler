// Stub of React that looks enough like the real thing that the compiler will
// not strip out all the code.
window.React = (function(f){
  return {
    createClass: f(),
    createElement: f(),
    cloneElement: f(),
    createFactory: f(),
    createMixin: function(x) {return x},
    render: f(),
    setState: f()
  };
})(function() {});
