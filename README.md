# Closure Compiler support for React

Tools for making [React](http://facebook.github.io/react/) work better with the [Closure Compiler](https://developers.google.com/closure/compiler/). Goes beyond [an externs file](https://github.com/steida/react-externs) and adds a custom warnings guard and compiler pass to teach the compiler about components and other React-specific concepts.

See [this blog post](http://blog.persistent.info/2015/05/teaching-closure-compiler-about-react.html) for details about the motivation for this project.

## Building

To build the project, use:

    ant jar

That generates `lib/react-closure-compiler.jar`, which you can then integrate into your build process (by adding `info.persistent.react.jscomp.ReactWarningsGuard` as a warnings guard and `info.persistent.react.jscomp.ReactCompilerPass` as a custom pass to run before checks). Given a `CompilerOptions` instance, this is usually a matter of:

    options.addWarningsGuard(new ReactWarningsGuard());
    options.addCustomPass(
            CustomPassExecutionTime.BEFORE_CHECKS,
            new ReactCompilerPass(compiler));

To run the tests, use:

    ant test

## Usage

You should be able to write React components as normal, using `React.createClass`, JSX, etc. That is, if you have a component:

```javascript
var Comp = React.createClass({
  render: function() {
    return <div/>;
  },
  /**
   * @return {number}
   */
  someMethod: function() {
    return 123;
  }
});
```

The Closure Compiler will know about three types:

* `ReactClass.<Comp>`, for the class definition
* `ReactElement.<Comp>` for an element created from that definition (via JSX or `React.createElement()`. There is a `CompElement` `@typedef` generated so that you don't have to use the slightly awakward template type notation.
* `Comp` for rendered instances of this component (this is subclass of `ReactComponent`). `Comp` instances are known to have custom component methods like `someMethod` (and their parameter and return types).

See [this page](https://facebook.github.io/react/docs/glossary.html) for more details on React terminology and [`types.js`](https://github.com/mihaip/react-closure-compiler/blob/master/src/info/persistent/react/jscomp/types.js) in this repository for the full type hierarchy that is implemented.

This means that for example you can use `/** @type {Comp} */` to annotate functions that return a _rendered_ instance of `Comp`. Additionally, `React.render` invocations on JSX tags or explicit `React.createElement` calls are automatically annotated with the correct type. That is, given:

```javascript
var compInstance = React.render(<Comp/>, container);
compInstance.someMethod();
compInstance.someOtherMethodThatDoesntExist();
```

The compiler will know that `compInstance` has a `someMethod()` method, but that it doesn't have a `someOtherMethodThatDoesntExist()`.

### Benefits

In addition to type checking of component instances, this compiler pass has the following benefits:

* React API calls also get minified (since React itself is an input to the compiler, there is no need to list it as an extern, therefore)
* React-aware size optimizations (for example `propTypes` in a component will get stripped out when using the minified React build, since they are not checked in that case)
* React-aware checks and warnings (e.g. if you use `PureRenderMixin` but also override `shouldComponentUpdate`, thus obviating the need for the mixin).

### Mixins

Mixins are supported, as long as they are annotated via the `React.createMixin`  wrapper ([introduced](https://github.com/facebook/react/commit/295ef0063b933e13b2ddd541c108b386b35b648b) in React 0.13). That is, the following should work (the compiler will know about the presence of `someMixinMethod` on `Comp` instances, and that it returns a number):

```javascript
var Mixin = React.createMixin({
  /**
   * @return {number}
   */
  someMixinMethod: function() {
    return 123;
  }
});

var Comp = React.createClass({
  mixins: [Mixin],
  render: function() {
    return <div>{this.someMixinMethod()}</div>;
  }
});
```

Note that the `React.createMixin` call will be stripped out by the compiler pass, so they do not result in any extra overhead.

If you (ab)use mixins to simulate classical inheritance (by having mixins call component class methods, in the vein of abstract functions), you'll need to define these functions as separate mixin properties. For example:

```javascript
var Mixin = React.createMixin({
  /**
   * @return {number}
   */
  someMixinMethod: function() {
    return this.abstractMixinMethod() * 2;
  }
});

/**
 * @return {number}
 */
Mixin.abstractMixinMethod;

var Comp = React.createClass({
  mixins: [Mixin],
  render: function() {
    return <div>{this.someMixinMethod()}</div>;
  }
});
```

## Caveats and limitations

* The React source itself must be an input file to the Closure Compiler (with or without add-ons, minified or not). See [`React.isReactSourceName()`](https://github.com/mihaip/react-closure-compiler/blob/master/src/info/persistent/react/jscomp/React.java) for how the React source input is identified.
* Use of ES6 class syntax has not been tested
* Only simple mixins that are referenced by name in the `mixins` array are supported (e.g. dynamic mixins that are generated via function calls are not).
* Automatic type annotation of `React.createElement` calls only works for direct references to component names. That is `var foo = Comp;var elem = React.createElement(foo)` will not result in elem getting the type `ReactElement.<Comp>` as expected. You will need to add a cast in that case.
* If you use the minified version of React as an input, you will need to make some small modifications to it to quote object literal keys, otherwise the compiler will rename them. See [#10](https://github.com/mihaip/react-closure-compiler/issues/10) for more details.

## Demo

The demo shows how to use the warnings guard and compiler pass with [Plovr](http://plovr.com/), but they could be used with other toolchains as well. Plovr is assumed to be checked out in a sibling `plovr` directory. To run the server for the demo, use:

    ant run-demo

And then open the `demo/index.html` file in your browser (`file:///` URLs are fine). You will see some warnings, showing that type checking is working as expected with React components.

## Status

This compiler pass has been integrated into [Quip](https://github.com/quip)'s JavaScript codebase (400+ React components). It is thus not _entirely_ scary code, but you will definitely want to check [the list of issues](https://github.com/mihaip/react-closure-compiler/issues) before using it.
