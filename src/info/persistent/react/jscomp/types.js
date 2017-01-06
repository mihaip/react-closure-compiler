/**
 * @fileoverview Type annotations for the React library.
 *
 * Will be automatically included as a source (not externs) file by
 * ReactCompilerPass. The contents of this file should be inert (i.e. interfaces
 * and typedefs only) such that it compiles down to nothing.
 *
 * Based on https://github.com/facebook/flow/blob/master/lib/react.js and
 * https://github.com/borisyankov/DefinitelyTyped/blob/master/react/react.d.ts.
 */

/**
 * @interface
 * @template T
 */
function ReactClass() {}

/**
 * @type {Object.<ReactPropsChainableTypeChecker>|undefined}
 */
ReactClass.prototype.propTypes;

/**
 * @type {Object.<ReactPropsChainableTypeChecker>|undefined}
 */
ReactClass.prototype.contextTypes;

/**
 * @type {Object.<ReactPropsChainableTypeChecker>|undefined}
 */
ReactClass.prototype.childContextTypes;

/**
 * @type {string|undefined}
 */
ReactClass.prototype.displayName;

/**
 * @type {ReactProps|undefined}
 */
ReactClass.prototype.defaultProps;

/**
 * @return {ReactProps}
 */
ReactClass.prototype.getDefaultProps = function() {};

/**
 * @typedef {!Object|{children: (Object|undefined)}}
 */
var ReactProps;

/**
 * @typedef {Object}
 */
var ReactState;

/**
 * @dict
 * @constructor
 */
function ReactRefs() {}

/**
 * @interface
 * @template T
 */
function ReactElement() {}

/**
 * @type {ReactClass.<T>}
 */
ReactElement.prototype.type;

/**
 * @type {ReactProps}
 */
ReactElement.prototype.props;

/**
 * @type {string|number}
 */
ReactElement.prototype.key;

/**
 * @type {string}
 */
ReactElement.prototype.ref;

/**
 * @interface
 * @extends {ReactElement}
 */
function ReactDOMElement() {}

/**
 * @type {string}
 */
ReactDOMElement.prototype.type;

/**
 * @interface
 */
function ReactComponent() {}

/**
 * @type {ReactProps}
 * @const
 */
ReactComponent.prototype.props;

/**
 * @type {ReactState}
 * @const
 */
ReactComponent.prototype.state;

/**
 * @type {Object}
 * @const
 */
ReactComponent.prototype.context;

/**
 * @type {ReactRefs}
 */
ReactComponent.prototype.refs;

/**
 * @return {ReactState}
 */
ReactComponent.prototype.getInitialState = function() {};

/**
 * @return {Object}
 */
ReactComponent.prototype.getChildContext = function() {};

/**
 * @param {ReactProps} props
 * @param {function(): void=} callback
 * @return {void}
 * @deprecated
 */
ReactComponent.prototype.setProps = function(props, callback) {};

/**
 * @param {ReactProps} props
 * @param {function(): void=} callback
 * @return {void}
 * @deprecated
 */
ReactComponent.prototype.replaceProps = function(props, callback) {};

/**
 * @param {Object|function(ReactState, ReactState): ReactState} stateOrFunction
 * @param {function(): void=} callback
 * @return {void}
 */
ReactComponent.prototype.setState = function(stateOrFunction, callback) {};

/**
 * @param {ReactState} state
 * @param {function(): void=} callback
 * @return {void}
 */
ReactComponent.prototype.replaceState = function(state, callback) {};

/**
 * @return {ReactElement|boolean}
 */
ReactComponent.prototype.render = function() {};

/**
 * @param {function(): void=} callback
 * @return {void}
 */
ReactComponent.prototype.forceUpdate = function(callback) {};

/**
 * @return {Element}
 */
ReactComponent.prototype.getDOMNode = function() {};

/**
 * @return {boolean}
 */
ReactComponent.prototype.isMounted = function() {};

// Component lifecycle/delegate methods that may be implemented. Intentionally
// does not include the context parameter that is passed to some methods, since
// it's undocumented. Implementations can still add it (and it will not be
// flagged as an error).

/**
 * @return {void}
 */
ReactComponent.prototype.componentWillMount = function() {};

/**
 * @return {void}
 */
ReactComponent.prototype.componentDidMount = function() {};

/**
 * @param {ReactProps} nextProps
 * @return {void}
 */
ReactComponent.prototype.componentWillReceiveProps = function(nextProps) {};

/**
 * @param {ReactProps} nextProps
 * @param {ReactState} nextState
 * @return {boolean}
 */
ReactComponent.prototype.shouldComponentUpdate = function(nextProps, nextState) {};

/**
 * @param {ReactProps} nextProps
 * @param {ReactState} nextState
 * @return {void}
 */
ReactComponent.prototype.componentWillUpdate = function(nextProps, nextState) {};

/**
 * @param {ReactProps} prevProps
 * @param {ReactState} prevState
 * @return {void}
 */
ReactComponent.prototype.componentDidUpdate = function(prevProps, prevState) {};

/**
 * @return {void}
 */
ReactComponent.prototype.componentWillUnmount = function() {};

/**
 * @interface
 */
function ReactFragment() {}

/**
 * @typedef {boolean|number|string|ReactElement|ReactFragment}
 */
var ReactChild;

/**
 * @typedef {
 *   ReactChild|
 *   Array.<boolean>|Array.<number>|Array.<string>|Array.<ReactElement>|
 *   Object.<boolean>|Object.<number>|Object.<string>|Object.<ReactElement>
 * }
 */
var ReactChildrenArgument;

/**
 * @interface
 */
function ReactChildren() {};

/**
 * @param {Object|undefined} children
 * @param {function(*, number)} func
 * @param {*=} context
 * @return {Object|undefined}
 */
ReactChildren.prototype.map = function(children, func, context) {};

/**
 * @param {Object|undefined} children
 * @param {function(*, number)} func
 * @param {*=} context
 */
ReactChildren.prototype.forEach = function(children, func, context) {};

/**
 * @param {Object|undefined} children
 * @return {number}
 */
ReactChildren.prototype.count = function(children) {};

/**
 * @param {Object|undefined} children
 * @return {ReactChild}
 */
ReactChildren.prototype.only = function(children) {};

/**
 * Factory functions for each HTML and SVG tag are generated by
 * ReactCompilerPass.
 *
 * @interface
 */
function ReactDOMModule() {};

/**
 * @param {ReactElement.<T>} element
 * @param {Element} container
 * @param {function()=} callback
 * @return {T}
 * @template T
 */
ReactDOMModule.prototype.render = function(element, container, callback) {};

/**
 * @param {Element} container
 * @return {boolean}
 */
ReactDOMModule.prototype.unmountComponentAtNode = function(container) {};

/**
 * @param {ReactComponent|Element} componentOrElement
 * @return {?Element}
 */
ReactDOMModule.prototype.findDOMNode = function(componentOrElement) {};

/**
 * @param {function()} callback
 * @return {void}
 */
ReactDOMModule.prototype.unstable_batchedUpdates = function(callback) {};

/**
 * @param {ReactComponent} parentComponent
 * @param {ReactElement} nextElement
 * @param {Element} container
 * @param {?function()} callback
 * @return {ReactComponent}
 */
ReactDOMModule.prototype.unstable_renderSubtreeIntoContainer = function(parentComponent, nextElement, container, callback) {};

/**
 * @interface
 */
function ReactDOMServerModule() {};

/**
 * @param {ReactElement} element
 * @return {string}
 */
ReactDOMServerModule.prototype.renderToStaticMarkup = function(element) {};

/**
 * @param {ReactElement} element
 * @return {string}
 */
ReactDOMServerModule.prototype.renderToString = function(element) {};

/**
 * Parameters are: props, propName, componentName, location.
 * @typedef {function(Object, string, string, string): Error}
 */
var ReactPropsChainableTypeChecker = function() {};

/**
 * @interface
 */
function ReactPropTypes() {};
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.array;
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.array.isRequired;
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.bool;
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.bool.isRequired;
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.func;
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.func.isRequired;
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.number;
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.number.isRequired;
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.object;
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.object.isRequired;
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.string;
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.string.isRequired;

/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.any;
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.any.isRequired;
/**
 * @param {ReactPropsChainableTypeChecker} typeChecker
 * @return {ReactPropsChainableTypeChecker}
 */
ReactPropTypes.prototype.arrayOf = function(typeChecker) {};
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.element;
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.element.isRequired;
/**
 * @param {Function} expectedClass
 * @return {ReactPropsChainableTypeChecker}
 */
ReactPropTypes.prototype.instanceOf = function(expectedClass) {};
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.node;
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.node.isRequired;
/**
 * @param {ReactPropsChainableTypeChecker} typeChecker
 * @return {ReactPropsChainableTypeChecker}
 */
ReactPropTypes.prototype.objectOf = function(typeChecker) {};
/**
 * @param {Array} expectedValues
 * @return {ReactPropsChainableTypeChecker}
 */
ReactPropTypes.prototype.oneOf = function(expectedValues) {};
/**
 * @param {Array.<ReactPropsChainableTypeChecker>} arrayOfTypeCheckers
 * @return {ReactPropsChainableTypeChecker}
 */
ReactPropTypes.prototype.oneOfType = function(arrayOfTypeCheckers) {};
/**
 * @param {Object.<ReactPropsChainableTypeChecker>} shapeTypes
 * @return {ReactPropsChainableTypeChecker}
 */
ReactPropTypes.prototype.shape = function(shapeTypes) {};

/**
 * Populated programatically with all of the DOM tag name factory methods.
 *
 * @interface
 */
function ReactDOMFactories() {}

/**
 * @interface
 * @extends {ReactComponent}
 */
function ReactCSSTransitionGroup() {}

/**
 * @interface
 * @extends {ReactComponent}
 */
function ReactTransitionGroup() {}

/**
 * @interface
 */
function ReactAddonsPerf() {}

ReactAddonsPerf.prototype.start = function() {};

ReactAddonsPerf.prototype.stop = function() {};

/**
 * @return {Array.<ReactAddonsPerf.Measurement>}
 */
ReactAddonsPerf.prototype.getLastMeasurements = function() {};

/**
 * @param {ReactAddonsPerf.Measurement=} measurements
 */
ReactAddonsPerf.prototype.printExclusive = function(measurements) {};

/**
 * @param {ReactAddonsPerf.Measurement=} measurements
 */
ReactAddonsPerf.prototype.printInclusive = function(measurements) {};

/**
 * @param {ReactAddonsPerf.Measurement=} measurements
 */
ReactAddonsPerf.prototype.printWasted = function(measurements) {};

/**
 * @typedef {{
 *     exclusive: !Object.<string, number>,
 *     inclusive: !Object.<string, number>,
 *     render: !Object.<string, number>,
 *     counts: !Object.<string, number>,
 *     writes: !Object.<string, {type: string, time: number, args: Array}>,
 *     displayNames: !Object.<string, {current: string, owner: string}>,
 *     totalTime: number
 * }}
 */
ReactAddonsPerf.Measurement;

/**
 * @interface
 */
function ReactAddons() {}

/**
 * @type {ReactClass.<ReactCSSTransitionGroup>}
 */
ReactAddons.prototype.CSSTransitionGroup;

/**
 * @type {ReactClass.<ReactTransitionGroup>}
 */
ReactAddons.prototype.TransitionGroup;

/**
 * @type {ReactAddonsPerf}
 */
ReactAddons.prototype.Perf;

/**
 * @type {Object}
 */
ReactAddons.prototype.PureRenderMixin;

/**
 * @param {Function} callback
 * @param {*=} a
 * @param {*=} b
 * @param {*=} c
 * @param {*=} d
 */
ReactAddons.prototype.batchedUpdates = function(callback, a, b, c, d) {};

/**
 * @param {Object|string} objectOrClassName
 * @param {...string} classNames
 * @return {string}
 * @deprecated
 */
ReactAddons.prototype.classSet = function(objectOrClassName, classNames) {};

/**
 * @param {ReactElement.<T>} element
 * @param {Object=} extraProps
 * @return {ReactElement.<T>}
 * @template T
 * @deprecated
 */
ReactAddons.prototype.cloneWithProps = function(element, extraProps) {};

/**
 * @param {Object.<string, ReactElement>} object
 * @return {ReactFragment}
 */
ReactAddons.prototype.createFragment = function(object) {};

/**
 * @typedef {{
 *     $push: (Array|undefined),
 *     $unshift: (Array|undefined),
 *     $splice: (Array.<Array>|undefined),
 *     $set: (Object|undefined),
 *     $merge: (Object|undefined),
 *     $apply: (function(Object): Object|undefined)
 * }}
 */
ReactAddons.UpdateSpec;

/**
 * @param {Object|Array} value
 * @param {ReactAddons.UpdateSpec} updateSpec
 * @return {Object|Array}
 */
ReactAddons.prototype.update = function(value, updateSpec) {};


/**
 * @interface
 */
function ReactModule() {}

/**
 * @type {ReactChildren}
 * @const
 */
ReactModule.prototype.Children;

/**
 * @typedef {ReactComponent}
 */
ReactModule.prototype.Component;

/**
 * @type {ReactDOMFactories}
 * @const
 */
ReactModule.prototype.DOM;

/**
 * @type {ReactPropTypes}
 * @const
 */
ReactModule.prototype.PropTypes;

/**
 * @param {boolean} shouldUseTouch
 * @return {void}
 */
ReactModule.prototype.initializeTouchEvents = function(shouldUseTouch) {};

/**
 * "render()" implementations may be undefined (i.e. missing) if it is instead
 * provided by a mixin.
 *
 * @param {{
 *     render: ((function(): (ReactElement|boolean))|undefined),
 *     displayName: (string|undefined),
 *     propTypes: (Object.<ReactPropsChainableTypeChecker>|undefined),
 *     contextTypes: (Object.<ReactPropsChainableTypeChecker>|undefined),
 *     childContextTypes: (Object.<ReactPropsChainableTypeChecker>|undefined),
 *     mixins: (Array.<Object>|undefined),
 *     statics: (Object|undefined)
 * }} specification
 * @return {ReactClass}
 */
ReactModule.prototype.createClass = function(specification) {};

/**
 * @param {T} mixin
 * @return {T}
 * @template T
 */
ReactModule.prototype.createMixin = function(mixin) {};

/**
 * @param {ReactClass.<T>|string} type
 * @param {Object=} props
 * @param {...ReactChildrenArgument} children
 * @return {!ReactElement.<T>}
 * @template T
 */
ReactModule.prototype.createElement = function(type, props, children) {};

/**
 * @param {ReactElement.<T>} element
 * @param {Object=} props
 * @param {...ReactChildrenArgument} children
 * @return {ReactElement.<T>}
 * @template T
 */
ReactModule.prototype.cloneElement = function(element, props, children) {};

/**
 * @param {ReactClass.<T>|string} type
 * @return {function(Object=, ...ReactChildrenArgument): ReactElement.<T>}
 * @template T
 */
ReactModule.prototype.createFactory = function(type) {};

/**
 * @param {ReactComponent|Element} componentOrElement
 * @return {Element}
 * @deprecated
 */
ReactModule.prototype.findDOMNode = function(componentOrElement) {};

/**
 * @param {ReactElement.<T>} element
 * @param {Element} container
 * @param {function()=} callback
 * @return {T}
 * @template T
 * @deprecated
 */
ReactModule.prototype.render = function(element, container, callback) {};

/**
 * @param {ReactElement} element
 * @return {string}
 * @deprecated
 */
ReactModule.prototype.renderToString = function(element) {};

/**
 * @param {ReactElement} element
 * @return {string}
 * @deprecated
 */
ReactModule.prototype.renderToStaticMarkup = function(element) {};

/**
 * @param {Element} container
 * @return {boolean}
 * @deprecated
 */
ReactModule.prototype.unmountComponentAtNode = function(container) {};

/**
 * @param {Object} element
 * @return {boolean}
 */
ReactModule.prototype.isValidElement = function(element) {};

/**
 * @type {ReactAddons}
 * @const
 */
ReactModule.prototype.addons;

/**
 * @param {Object} target
 * @param {...Object} sources
 * @return {Object}
 */
ReactModule.prototype.__spread = function(target, sources) {};
