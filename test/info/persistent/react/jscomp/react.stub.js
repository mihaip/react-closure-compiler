// Stub of React that looks enough like the real thing that the compiler will
// not strip out all the code.
window.React = (function(f){
  return {
    createClass: f(),
    createElement: f(),
    cloneElement: f(),
    createFactory: f(),
    createMixin: function(x) {return x},
    setState: f()
  };
})(function() {});

window.ReactDOM = (function(f){
  return {
    render: f(),
    unmountComponentAtNode : f(),
    findDOMNode: f(),
    unstable_batchedUpdates: f(),
    unstable_renderSubtreeIntoContainer: f(),
  };
})(function() {});

window.ReactDOMServer = (function(f){
  return {
    renderToString: f(),
    renderToStaticMarkup: f()
  };
})(function() {});
