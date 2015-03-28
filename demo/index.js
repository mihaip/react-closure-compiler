// Mixins need to be marked with React.createMixin (added in React 0.13) so
// that they can be tracked by the compiler pass.
var ChainedMixin = React.createMixin({
  statics: {
    chainedMixinStatic: function() {
      console.log("Chained mixin static method running");
    }
  },
  chainedMixinMethod: function() {
    console.log("Chained mixin method running");
  }
});

var Mixin = React.createMixin({
  statics: {
    mixinStatic: function() {
      console.log("Mixin static method running");
    }
  },
  mixins: [ChainedMixin],
  mixinMethod: function() {
    console.log("Mixin method running");
  }
});

// Does not use JSX since JSX compilation is assumed to take place before
// inputs are given to Plovr/the Closure Compiler.
var DemoCounter = React.createClass({
  displayName: "DemoCounter",
  statics: {
    classStatic: function() {
      console.log("Class static running");
    }
  },
  mixins: [Mixin],
  getInitialState: function() {
    return {count: 0};
  },
  propTypes: {
    label: React.PropTypes.string
  },
  render: function() {
    this.mixinMethod();
    this.chainedMixinMethod();
    if (Date.now() == 0) { // Don't want this code to run, just to get type checked.
      this.increment("wrong parameter type");
      this.nonExistentMethod();
    }
    return React.DOM.div(null,
      this.props.label, "Count: ", this.state.count,
      React.DOM.button(
        {onClick: this.increment.bind(this, 1)}, "Internal Increment")
    );
  },
  /**
   * @param {number} count
   */
  increment: function(count) {
    this.setState({count: this.state.count + count});
  }
});

// Unused component classes will be removed from the output, since the compiler
// knows that React.createClass does not have side effects.
var UnusedClass = React.createClass({
  displayName: "UnusedClass",
  render: function() {
    return React.createElement("div", null, "I am unused");
  }
});

/**
 * @constructor
 */
function SomeOtherType() {};
SomeOtherType.prototype.increment2 = function() {return false};
SomeOtherType.someOtherStatic = function() {
  console.log("SomeOtherType static method running");
};

DemoCounter.classStatic();
DemoCounter.mixinStatic();
DemoCounter.chainedMixinStatic();
if (Date.now() == 0) { // Don't want this code to run, just to get type checked.
  DemoCounter.someOtherStatic();
}

/** @type {DemoCounter} */ var counterInstance = React.render(
    React.createElement(DemoCounter, {label: "Label"}),
    document.querySelector("#container"));

// Not necessarily what we would do in a production app, but demonstrates that
// methods on instantiated React elements can be called without warnings.
document.querySelector("#button").addEventListener(
  "click",
  function(e) {
    counterInstance.increment(1);
    if (Date.now() == 0) { // Don't want this code to run, just to get type checked.
      counterInstance.increment("wrong parameter type");
      counterInstance.nonExistentMethod();
    }
  });
