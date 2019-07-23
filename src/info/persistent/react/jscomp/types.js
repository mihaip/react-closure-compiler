/**
 * @fileoverview Type annotations for the React library.
 *
 * Will be automatically included as an externs file by ReactCompilerPass.
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
 * Closure Compiler's TypeCheck complains about .Props not being defined on
 * React.Class when extracting prop types without this.
 *
 * @type {!Object}
 */
ReactClass.prototype.Props;

/**
 * @typedef {!Object|{children: (Object|undefined)}}
 */
var ReactProps;

/**
 * Functional component function signature.
 *
 * @typedef {function(ReactProps=): !ReactElement}
 */
var ReactComponentFunction;



/**
 * @typedef {!Object}
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
 * @type {?ReactState}
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
 * @type {(function(): ?ReactState)|undefined}
 */
ReactComponent.prototype.getInitialState;

/**
 * @return {Object}
 */
ReactComponent.prototype.getChildContext = function() {};

/**
 * @param {?ReactState|function(?ReactState, ReactProps): ?ReactState} stateOrFunction
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
 * @typedef {boolean|number|string|null|undefined|ReactElement|ReactFragment}
 */
var ReactChild;

/**
 * @typedef {
 *   ReactChild|
 *   Array.<ReactChild>|
 *   Object.<ReactChild>|
 *   undefined
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
 * @param {Object|undefined} children
 * @return {!Array<ReactChild>}
 */
ReactChildren.prototype.toArray = function(children) {};

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
 * @type {!ReactDOMModule}
 * @const
 */
var ReactDOM;

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
 * @type {!ReactDOMServerModule}
 * @const
 */
var ReactDOMServer;

/**
 * Parameters are: props, propName, componentName, location.
 * @typedef {function(Object, string, string, string): Error}
 */
var ReactPropsChainableTypeChecker;

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
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.symbol;
/** @type {ReactPropsChainableTypeChecker} */ ReactPropTypes.prototype.symbol.isRequired;

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
 * Populated programatically with all of the event handler names.
 *
 * @interface
 */
function ReactDOMProps() {}

/** @type {{__html: string}} */
ReactDOMProps.prototype.dangerouslySetInnerHTML;

/** @type {string} */
ReactDOMProps.prototype.dangerouslySetInnerHTML.__html;

/**
 * @typedef {function(Event): void}
 */
var ReactEventHandler;

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
 * @return {boolean}
 */
ReactAddonsPerf.prototype.isRunning = function() {};

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
ReactAddonsPerf.prototype.printOperations = function(measurements) {};

/**
 * @param {ReactAddonsPerf.Measurement=} measurements
 */
ReactAddonsPerf.prototype.printWasted = function(measurements) {};

/**
 * An opaque type as of React 15.0.
 *
 * @typedef {!Object}
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
 * @param {ReactProps=} props
 * @param {Object=} context
 * @constructor
 * @implements {ReactComponent}
 */
ReactModule.prototype.Component = function (props, context) { }

/** @override */
ReactModule.prototype.Component.prototype.props;

/** @override */
ReactModule.prototype.Component.prototype.state;

/** @override */
ReactModule.prototype.Component.prototype.context;

/** @override */
ReactModule.prototype.Component.prototype.refs;

/** @override */
ReactModule.prototype.Component.prototype.getInitialState;

/** @override */
ReactModule.prototype.Component.prototype.getChildContext = function() {};

/** @override */
ReactModule.prototype.Component.prototype.setState = function(stateOrFunction, callback) {};

/** @override */
ReactModule.prototype.Component.prototype.replaceState = function(state, callback) {};

/** @override */
ReactModule.prototype.Component.prototype.render = function() {};

/** @override */
ReactModule.prototype.Component.prototype.forceUpdate = function(callback) {};

/** @override */
ReactModule.prototype.Component.prototype.isMounted = function() {};
// it's undocumented. Implementations can still add it (and it will not be
// flagged as an error).

/** @override */
ReactModule.prototype.Component.prototype.componentWillMount = function() {};

/** @override */
ReactModule.prototype.Component.prototype.componentDidMount = function() {};

/** @override */
ReactModule.prototype.Component.prototype.componentWillReceiveProps = function(nextProps) {};

/** @override */
ReactModule.prototype.Component.prototype.shouldComponentUpdate = function(nextProps, nextState) {};

/** @override */
ReactModule.prototype.Component.prototype.componentWillUpdate = function(nextProps, nextState) {};

/** @override */
ReactModule.prototype.Component.prototype.componentDidUpdate = function(prevProps, prevState) {};

/** @override */
ReactModule.prototype.Component.prototype.componentWillUnmount = function() {};

/**
 * @type {Object.<ReactPropsChainableTypeChecker>|undefined}
 */
ReactModule.prototype.Component.propTypes;

/**
 * @type {Object|undefined}
 */
ReactModule.prototype.Component.defaultProps;

/**
 * @type {Object|undefined}
 */
ReactModule.prototype.Component.contextTypes;


/**
 * @param {ReactProps=} props
 * @param {Object=} context
 * @constructor
 * @extends {React.Component}
 */
ReactModule.prototype.PureComponent = function (props, context) { }

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
 * @param {ReactClass.<T>|ReactComponentFunction|typeof ReactModule.prototype.Component|string} type
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
 * @param {*} element
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
 * @deprecated
 */
ReactModule.prototype.__spread = function(target, sources) {};

/**
 * @type {!ReactModule}
 * @const
 */
var React;

/**
 * Technically there is a parallel class hierarchy for mouse events, etc.
 * However, rather than repeating all of the fields for each event type,
 * pretend like the React additions are their own subclass.
 *
 * @constructor
 * @extends {Event}
 */
function ReactSyntheticEvent() {};

/** @type {!Event} */
ReactSyntheticEvent.prototype.nativeEvent;

/** @return {boolean} */
ReactSyntheticEvent.prototype.isDefaultPrevented = function() {};

/** @return {boolean} */
ReactSyntheticEvent.prototype.isPropagationStopped = function() {};

/** @return {void} */
ReactSyntheticEvent.prototype.persist = function() {};
