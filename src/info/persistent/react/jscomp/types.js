/**
 * @fileoverview Type annotations for the React library.
 *
 * Will be automatically included as a source (not externs) file by
 * ReactCompilerPass. The contents of this file should be inert (i.e. interfaces
 * and typedefs only) such that it compiles down to nothing.
 *
 * Based on https://github.com/facebook/flow/blob/master/lib/react.js
 */

/**
 * @typedef {!Function}
 * @template T
 */
var ReactClass;

/**
 * @interface
 * @template T
 */
function ReactElement() {}

/**
 * @interface
 */
function ReactComponent() {}

/**
 * @return {Object}
 */
ReactComponent.prototype.getInitialState = function() {};

/**
 * @param {Object} props
 * @param {function(): void=} callback
 * @return {void}
 */
ReactComponent.prototype.setProps = function(props, callback) {};

/**
 * @param {Object} props
 * @param {function(): void=} callback
 * @return {void}
 */
ReactComponent.prototype.replaceProps = function(props, callback) {};

/**
 * @param {Object} state
 * @param {function(): void=} callback
 * @return {void}
 */
ReactComponent.prototype.setState = function(state, callback) {};

/**
 * @param {Object} state
 * @param {function(): void=} callback
 * @return {void}
 */
ReactComponent.prototype.replaceState = function(state, callback) {};

/**
 * @return {ReactElement}
 */
ReactComponent.prototype.render = function(state, callback) {};

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
 * @param {Object} nextProps
 * @return {void}
 */
ReactComponent.prototype.componentWillReceiveProps = function(nextProps) {};

/**
 * @param {Object} nextProps
 * @param {Object} nextState
 * @return {boolean}
 */
ReactComponent.prototype.shouldComponentUpdate = function(nextProps, nextState) {};

/**
 * @param {Object} nextProps
 * @param {Object} nextState
 * @return {void}
 */
ReactComponent.prototype.componentWillUpdate = function(nextProps, nextState) {};

/**
 * @param {Object} prevProps
 * @param {Object} prevState
 * @return {void}
 */
ReactComponent.prototype.componentDidUpdate = function(prevProps, prevState) {};

/**
 * @return {void}
 */
ReactComponent.prototype.componentWillUnmount = function() {};

/**
 * @typedef {
 *   boolean|number|string|ReactElement|
 *   Array.<boolean>|Array.<number>|Array.<string>|Array.<ReactElement>
 * }
 */
var ReactChild;

/**
 * @interface
 */
function ReactModule() {}

/**
 * @param {boolean} shouldUseTouch
 * @return {void}
 */
ReactModule.prototype.initializeTouchEvents = function(shouldUseTouch) {};

/**
 * @param {{render: function(): ReactElement}} specification
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
 * @param {(ReactClass.<T>|string|number)} type
 * @param {Object=} props
 * @param {...ReactChild} children
 * @return {ReactElement.<T>}
 * @template T
 */
ReactModule.prototype.createElement = function(type, props, children) {};

/**
 * @param {ReactElement.<T>} element
 * @param {Object=} props
 * @param {...ReactChild} children
 * @return {ReactElement.<T>}
 * @template T
 */
ReactModule.prototype.cloneElement = function(element, props, children) {};

/**
 * @param {(ReactClass.<T>|string|number)} type
 * @return {function(Object=, ...ReactChild): ReactElement.<T>}
 * @template T
 */
ReactModule.prototype.createFactory = function(type) {};

/**
 * @param {(ReactElement|Element)} componentOrElement
 * @return {Element}
 */
ReactModule.prototype.findDOMNode = function(componentOrElement) {};

/**
 * @param {ReactElement.<T>} element
 * @param {Element} container
 * @param {function()=} callback
 * @return {T}
 * @template T
 */
ReactModule.prototype.render = function(element, container, callback) {};

/**
 * @param {ReactElement} element
 * @return {string}
 */
ReactModule.prototype.renderToString = function(element) {};

/**
 * @param {ReactElement} element
 * @return {string}
 */
ReactModule.prototype.renderToStaticMarkup = function(element) {};

/**
 * @param {Element} container
 * @return {boolean}
 */
ReactModule.prototype.unmountComponentAtNode = function(container) {};

/**
 * @param {Object} element
 * @return {boolean}
 */
ReactModule.prototype.isValidElement = function(element) {};
