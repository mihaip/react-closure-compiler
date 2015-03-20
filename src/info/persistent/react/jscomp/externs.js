/**
 * @type {Object}
 * @const
 */
var React = {};

/**
 * @typedef {!Function}
 */
var ReactClass;

/**
 * @constructor
 */
var ReactElement;

/**
 * @constructor
 */
var ReactComponent;

/**
 * @typedef {
 *   boolean|number|string|ReactElement|
 *   Array.<boolean>|Array.<number>|Array.<string>|Array.<ReactElement>
 * }
 */
var ReactChild;

/**
 * @param {{render: function()}} specification
 * @return {ReactClass}
 */
React.createClass = function(specification) {};

/**
 * @param {(ReactClass|string|number)} type
 * @param {Object=} props
 * @param {...ReactChild} children
 * @return {ReactElement}
 */
React.createElement = function(type, props, children) {};

/**
 * @param {ReactElement} nextElement
 * @param {Element} container
 * @param {function()=} callback
 * @return {ReactComponent}
 */
React.render = function(nextElement, container, callback) {};
