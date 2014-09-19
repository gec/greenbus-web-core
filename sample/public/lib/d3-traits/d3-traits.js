/*! d3-traits - v0.0.1 - 2014-09-19
* https://github.com/gec/d3-traits
* Copyright (c) 2014 d3-traits; Licensed ,  */
(function(d3) {

//    var a, b, c, d, e
//
//    var t1 = d3.trait( a)       // 1
//            .trait( b, {})      // 1
//            .trait( c, {})      // 1
//            .config( { x1: 1})
//
//    var t2 = d3.trait( t1 )     // .. 1, 3
//            .trait( d, {})      // 2, 3
//            .trait( e, {})      // 2, 3
//            .config( { x1: 2, y1: 3})
//
//    var t3 = d3.trait( c)
//            .config( { x1: 3})
//            .trait( d, {})
//            .trait( e, {})
//            .config( { x1: 4})

  var DEBUG = false

  // Usage: Array.isArray( myArray)
  Array.isArray = Array.isArray || function(vArg) {
    return Object.prototype.toString.call(vArg) === "[object Array]";
  };

  /**
   * Copy all the properties for the super to the new trait.
   * @param superTrait
   * @param newTrait
   */
  function stackTrait(superTrait, newTrait) {
    //newTrait._super = superTrait
    // Copy the properties over onto the new trait
    for( var name in superTrait ) {
      if( !(name in newTrait) )
        newTrait[name] = superTrait[ name]
    }
  }

  function getTraitCache(element, traitInstanceId) {
    var elStore = element[traitInstanceId]
    if( !elStore ) {
      element[traitInstanceId] = {}
      elStore = element[traitInstanceId]
    }
    return elStore
  }

  /**
   * Is this an x scale, axis, etc.
   * @param scaleName  'x1', 'x2', etc.
   * @returns {boolean}
   */
  function isX(scaleName) { return scaleName.charAt(0) === 'x'}

  /**
   * Is this a y scale, axis, etc.
   * @param scaleName  'y1', 'y2', etc.
   * @returns {boolean}
   */
  function isY(scaleName) { return scaleName.charAt(0) === 'y'}

  function extentMin(extent) { return extent[ 0] }

  function extentMax(extent) { return extent[ extent.length - 1] }

  function isData(_data, accessSeries) {
    return d3.max(_data, function(s) { return accessSeries(s).length}) > 0
  }

  function isValidDate(d) {
    if( Object.prototype.toString.call(d) !== "[object Date]" )
      return false;
    return !isNaN(d.getTime());
  }

  function getScaleRange(_super, name) {
    // SVG origin is top-left
    if( d3.trait.utils.isX(name) )
      return [ _super.minRangeMarginLeft(name), _super.chartWidth() - _super.minRangeMarginRight(name)]
    else
      return [ _super.chartHeight() - _super.minRangeMarginBottom(name), _super.minRangeMarginTop(name)]
  }

  function getScaleExtensions(_super, name, scale) {
    var domainExtent = scale.domain()
    // SVG origin is top-left
    if( d3.trait.utils.isX(name) )
      return [
        [0, scale(domainExtent[0])],
        [scale(domainExtent[1]), _super.chartWidth()]
      ]
    else
      return [
        [0, scale(domainExtent[0])],
        [ scale(domainExtent[1]), _super.chartHeight()]
      ]
  }

  function configFloat(valueConfig, valueDefault) {
    var vc = parseFloat(valueConfig)
    return isNaN(vc) ? valueDefault : vc
  }

  function configMargin(marginConfig, marginDefault) {
    if( !marginConfig )
      return marginDefault

    var margin = {}
    margin.top = configFloat(marginConfig.top, marginDefault.top)
    margin.right = configFloat(marginConfig.right, marginDefault.right)
    margin.bottom = configFloat(marginConfig.bottom, marginDefault.bottom)
    margin.left = configFloat(marginConfig.left, marginDefault.left)
    return margin
  }

  function getValueOrArrayItem( valueOrArray, index, defaultValue) {
    if( !valueOrArray)
      return defaultValue
    if( Array.isArray( valueOrArray)) {
      var length = valueOrArray.length
      if( length === 0)
        return defaultValue
      else {
        // If we're off the end of the array, use the last index.
        var i = Math.min( length-1, index)
        var item = valueOrArray[i]
        return item ? item : defaultValue
      }
    } else {
      return valueOrArray
    }

  }

  function getValueOrObjectProperty( valueOrObject, property, defaultValue) {
    if( !valueOrObject)
      return defaultValue
    if( typeof valueOrObject === 'object') {
      var item = valueOrObject[property]
      return item ? item : defaultValue
    } else {
      return valueOrObject
    }

  }


  function clone(obj) {
    if( null == obj || "object" !== typeof obj ) return obj;
    var copy = obj.constructor();
    for( var attr in obj ) {
      if( obj.hasOwnProperty(attr) ) copy[attr] = obj[attr];
    }
    return copy;
  }

  /**
   * Extend the first object with the properties from the second, third, etc. objects.
   * Ignore properties that are null or undefined.
   *
   * @param target
   * @param obj, obj, ...
   * @returns The first object merged with the following objects.
   */
  function extendObject() {
    var i, options, key, value,
        target = arguments[0] || {},
        length = arguments.length;

    for( i = 1; i < length; i++ ) {
      // Ignore non-null/undefined values
      if( (options = arguments[ i ]) != null ) {
        // Extend the target object
        for( key in options ) {
          value = options[key]
          if( value )
            target[key] = value
        }
      }
    }

    return target
  }

  /**
   * Extend the first object with the properties from the second.
   * Do not overwrite any properties of the first object.
   * Ignore properties that are null or undefined.
   *
   * @param target
   * @param extensions
   * @returns {*}
   */
  function extendObjectNoOverwrite(target, extensions) {
    for( var key in extensions ) {
      if( !target.hasOwnProperty(key) )
        if( extensions[key] )
          target[key] = extensions[key];
    }
    return target
  }

  function extendTraitsConfig(config, defaultConfig) {
    var obj = clone(config)
    if( !obj )
      obj = {}
    return extendObjectNoOverwrite(obj, defaultConfig)
  }

  function TraitOld(trait, config, _super) {
    //if( DEBUG) console.log( "trait( " + trait.name + ")")

    var id, imp,
        self = this,
        traitIndex = 0

    self._config = config
    self._super = _super
    self._traitIndex = 0
    if( _super )
      self._traitIndex = _super.__traitIndex + 1

    self.getTraitId = function(name, index) { return "_" + name + "_" + index }


    self.getImp = function() { return self.imp}

    config = extendTraitsConfig(config, self.__getRoot()._config)
    id = self.getTraitId(trait.name, self._traitIndex)
    imp = trait(_super, config, id)
    stackTrait(_super, imp)
    //imp.prototype = Trait.prototype
    imp.call = Trait.prototype.call
    imp.callInstance = Trait.prototype.callInstance
    imp.trait = Trait.prototype.trait
    imp.__traitIndex = traitIndex
    imp.__traitId = id
    imp.__getRoot = Trait.prototype.__getRoot
    imp._super = _super
    imp._config = config
    self.imp = imp
  }

  function Trait(_trait, _config, _super) {
    if( DEBUG ) console.log("trait( " + _trait.name + ")")

    var id, imp,
        self = this

    self.config = _config
    self._super = _super
    self.index = -1
    self.__leafTrait = null


    function makeTraitId(name, index) { return "_" + name + "_" + index }

    self.getImp = function() { return imp}

    self.trait = function(_trait, config) {
      //if( DEBUG) console.log( ".trait( " + _trait.name + ")")
      var t = new Trait(_trait, config, imp)
      return t.getImp()
    }

//    self.config = function( _config) {
//        //if( DEBUG) console.log( ".config( {...})")
//        if( self._super)
//            self._super.config( _config)
//
//        if( _trait instanceof Trait)
//            _trait.config( _config)
//        else
//            self.configs.push( _config)
//        return self
//    }

    self.call = function(_selection, leafTrait) {
      if( !leafTrait )
        leafTrait = this
      if( !this.__leafTrait )
        this.__leafTrait = leafTrait

      if( this._super )
        this._super.call(_selection, leafTrait)
      _selection.call(imp)
      return imp
    }

    self.callTraits = function(_selection) {
      if( this.__leafTrait )
        return this.__leafTrait.call(_selection)
      else
        return this
    }

    function makeVirtual(name, fn, _superFn) {
      if( DEBUG ) console.log("makeVirtual " + name)
      return (function(name, fn) {
        return function() {
          var tmp = this._super;

          // Add a new ._super() method that is the same method
          // but on the super-class
          this._super = _superFn;

          // The method only need to be bound temporarily, so we
          // remove it when we're done executing
          var ret = fn.apply(this, arguments);
          this._super = tmp;

          return ret;
        };
      })(name, fn)
    }

    self.__replaceVirtual = function(name, fn) {
      if( imp.hasOwnProperty(name) )
        imp[name] = fn
      if( _super )
        _super.__replaceVirtual(name, fn)
    }

    self.__virtualize = function(name, fn) {
      if( DEBUG ) console.log("__virtualize begin " + _trait.name + " name=" + name)

      var virtual = null
      if( imp.hasOwnProperty(name) && typeof imp[name] === "function" ) {
        // The first parent has the same function.
        // The parent's version could be normal or virtualized.
        //
        if( DEBUG ) console.log("__virtualize " + _trait.name + " name=" + name + " hasFunction")
//            var original = '__original__' + name

        // imp[name] could be virtualized or normal
        virtual = makeVirtual(name, fn, imp[name])

//            if( ! imp.hasOwnProperty( original)) {
//                if( DEBUG) console.log( "__virtualize " + _trait.name + " name=" + name + " newly virtualized save original")
//                // save original
//                imp[original] = imp[name]
//            }
        imp[name] = virtual
        if( _super )
          _super.__replaceVirtual(name, virtual)
      } else {
        if( DEBUG ) console.log("__virtualize " + _trait.name + " name=" + name + " hasFunction NOT  ")
      }
//        if( _super)
//            virtual = _super.__virtualize( name, fn)
      return virtual
    }
    //       self.a  this._super()
    // t0 a  t3.a    -
    // t1    t3.a    t0.a
    // t2 a  t3.a    t0.a
    // t3 a  t3.a    t2.a
    //
    // t0 a  a
    //
    // t0 a  vt1.a   t0.a  __original__a
    // t1 a  vt1.a   t0.a  __original__a
    //
    // t0 a  vt2.a    -
    // t1 a  vt2.a    vt1.a  t0.a
    // t2 a  vt2.a    vt1.a  t0.a
    //
    // _stack[0] is most derived trait imp.
    //
    self.__makeVTable = function() {
      if( DEBUG ) console.log("__makeVTable begin " + _trait.name)

      var name, virtualized

      for( name in imp ) {

        virtualized = _super.__virtualize(name, imp[name])
        if( virtualized ) {
          if( DEBUG ) console.log(_trait.name + ".__makeVTable name " + name + "   virtualized")
//                imp[ '__original__' + name] = imp[name]
          imp[name] = virtualized
        } else {
          if( DEBUG ) console.log(_trait.name + ".__makeVTable name " + name + " ! virtualized")
        }

      }

      // Replicate super methods in imp; no overrides.
      for( name in _super ) {
        //if( DEBUG) console.log( _trait.name +".__makeVTable replicate " + name + "  in _super")
        if( !(name in imp) )
          imp[name] = _super[ name]
      }

      if( DEBUG ) console.log("__makeVTable end")
    }

    self.__getRoot = function() {
      if( self._super )
        return self._super.__getRoot()
      else
        return self
    },

      // TODO: make config from configs

//    config = extendTraitsConfig( config, self.__getRoot()._config)
      id = makeTraitId(_trait.name, self._traitIndex)
    if( _super )
      self.config = extendTraitsConfig(_config, self.__getRoot().config)

    imp = _trait(_super, self.config, id)

    if( _super )
      self.__makeVTable()


    imp.call = self.call
    imp.callTraits = self.callTraits
    imp.trait = self.trait
    imp.getImp = self.getImp
    imp.config = self.config
    imp.__makeVTable = self.__makeVTable
    imp.__virtualize = self.__virtualize
    imp.__replaceVirtual = self.__replaceVirtual
    imp.__getRoot = self.__getRoot
    imp._super = _super
  }

  Trait.prototype = {

    trait: function(_trait, config) {
      //if( DEBUG) console.log( ".trait( " + _trait.name + ")")
      var t = new Trait(_trait, config, this)
      var imp = t.getImp()
      return imp
    },

    __getRoot: function() {
      if( this._super )
        return this._super.__getRoot()
      else
        return this
    },

    callInstance: function(_selection) {
      var self = this
      _selection.call(function(selection) {
        selection.each(function(_data) {
//                if( ! this[self.__traitId])
//                    this[self.__traitId] = {}
//                self( _data, selection, this[self.__traitId]) // callee example: traitName( selection, data, traitStore)
          self(_data, selection) // callee example: traitName( selection, data, traitStore)
          //self.apply( this[self.__traitId], [this, _data, selection] ) // callee example: traitName( element, data, selection)
        })

      })
    },

    call: function(_selection, leafTrait) {
      if( !leafTrait )
        leafTrait = this
      this.__leafTrait = leafTrait
      if( this._super )
        this._super.call(_selection, leafTrait)

      //this.callInstance( _selection)
      _selection.call(this)
      return this
    }


  }
  Trait.prototype.constructor = Trait

  d3.selection.prototype.trait = function(trait, config) {
    if( Array.isArray(trait) ) {
      for( var index in trait )
        this.trait(trait[index])
    } else {
      //if( DEBUG) console.log( ".trait( " + trait.name + ")")
      this._traitsInitialize()

      var traitCount = this.traits.length

      var _super = {}
      if( traitCount > 0 )
        _super = this.traits[ traitCount - 1]

      var _config = extendTraitsConfig(this._traitsConfig, config)
      var traitInstance = trait(_super, _config, this)
      stackTrait(_super, traitInstance)

      this.call(traitInstance)
      this.traits.push(traitInstance)
    }
    return this
  }

  d3.selection.prototype.callTraits = function() {

    for( var index in this.traits ) {
      var traitInstance = this.traits[ index]
      //if( DEBUG) console.log( ".callTraits  " + index + " " + traitInstance.name)
      this.call(traitInstance)
    }
    return this
  }

  var DEFAULT_TRAITS_CONFIG = {
//    seriesFilter: function( d, i) { return true}
  }

  d3.selection.prototype._traitsInitialize = function() {
    if( !this._traitsConfig )
      this._traitsConfig = clone(DEFAULT_TRAITS_CONFIG)
    if( !this.traits )
      this.traits = []
    return this
  }

  d3.selection.prototype.traitConfig = function(config) {
    this._traitsInitialize()
    for( var key in config ) {
      this._traitsConfig[key] = config[key]
    }
    return this
  }

  function trait(aTrait, config) {
    return new Trait(aTrait, config).getImp()
  }

// Export traits to d3
  d3.trait = trait
  d3.trait.config = {}
  d3.trait.axis = {}
  d3.trait.chart = { utils: {} }
  d3.trait.control = {}
  d3.trait.focus = {}
  d3.trait.layout = {}
  d3.trait.legend = {}
  d3.trait.scale = {}

  d3.trait.utils = {
    clone:              clone,
    extend:             extendObject,
    isX:                isX,
    isY:                isY,
    isData:             isData,
    isValidDate:        isValidDate,
    extentMin:          extentMin,
    extentMax:          extentMax,
    getScaleRange:      getScaleRange,
    getScaleExtensions: getScaleExtensions,
    getTraitCache:      getTraitCache,
    configMargin:       configMargin,
    configFloat:        configFloat,
    getValueOrArrayItem: getValueOrArrayItem,
    getValueOrObjectProperty: getValueOrObjectProperty
  }

}(d3));
(function(d3, trait) {

  function Point(x, y) {
    switch( arguments.length) {
      case 0:
        this.x = 0
        this.y = 0
        break;
      case 1:
        this.x = x.x
        this.y = x.y
        break;
      default:
        this.x = x
        this.y = y
    }
  }
  Point.prototype.set = function( other) {
    if( other && typeof other === 'object') {
      this.x = other.x
      this.y = other.y
    }
  }
  Point.prototype.distanceX = function( other) {
    return other.x > this.x ? other.x - this.x : this.x - other.x
  }
  Point.prototype.distanceY = function( other) {
    return other.y > this.y ? other.y - this.y : this.y - other.y
  }
  Point.prototype.distance = function( other) {
    var dx = this.distanceX( other),
        dy = this.distanceY( other)
    return Math.sqrt(dx * dx + dy * dy)
  }

  function Size(width, height) {
    if( arguments.length <= 0 ) {
      this.width = 0
      this.height = 0
    } else {
      this.width = width
      this.height = height
    }
  }

  /**
   * top, right, bottom, left
   * top, right-left, bottom
   * top-bottom, right-left
   * all
   */
  function Margin(/* variable */) {
    var a, b
    switch( arguments.length ) {
      case 0:
        this.top = 0
        this.right = 0
        this.bottom = 0
        this.left = 0
        break;
      case 1:
        a = arguments[0]
        this.top = a
        this.right = a
        this.bottom = a
        this.left = a
        break;
      case 2:
        a = arguments[0]
        b = arguments[1]
        this.top = a
        this.right = b
        this.bottom = a
        this.left = b
        break;
      case 3:
        this.top = arguments[0]
        this.right = arguments[1]
        this.bottom = arguments[2]
        this.left = arguments[1]
        break;
      case 4:
        this.top = arguments[0]
        this.right = arguments[1]
        this.bottom = arguments[2]
        this.left = arguments[3]
        break;

    }
  }

  /**
   * Rect is a rectangle with origin, and size. The origin is in relation to the anchor.
   * The anchor defaults to top left (0,0). Center left is (0,0.5). Bottom right is (1,1).
   *
   * Top left coordinate is 0.
   *
   * Rects can also have a margin. TBD.
   */
  function Rect(x, y, w, h, ax, ay) {
    switch( arguments.length ) {
      case 0:
        this.origin = new Point(0, 0)
        this.size = new Size(0, 0)
        this.anchor = new Point(0, 0)
        break;
      case 2:
        this.origin = new Point(x.x, x.y)
        this.size = new Size(y.width, y.height)
        this.anchor = new Point(0, 0)
        break;
      case 3:
        this.origin = new Point(x.x, x.y)
        this.size = new Size(y.width, y.height)
        this.anchor = new Point(w.x, w.y)
        break;
      case 4:
        this.origin = new Point(x, y)
        this.size = new Size(w, h)
        this.anchor = new Point(0, 0)
        break;
      case 6:
        this.origin = new Point(x, y)
        this.size = new Size(w, h)
        this.anchor = new Point(ax, ay)
        break;
    }

  }
  Rect.prototype.minX = function() { return this.origin.x - this.size.width * this.anchor.x}
  Rect.prototype.maxX = function() { return this.origin.x + this.size.width * (1 - this.anchor.x)}
  Rect.prototype.midX = function() { return this.origin.x + (this.size.width * (1 - 2 * this.anchor.x)) / 2}
  Rect.prototype.minY = function() { return this.origin.y - this.size.height * this.anchor.y}
  Rect.prototype.maxY = function() { return this.origin.y + this.size.height * (1 - this.anchor.y)}
  Rect.prototype.midY = function() { return this.origin.y + (this.size.height * (1 - 2 *this.anchor.y)) / 2}

  Rect.prototype.spaceOnTop = function(rectAbove) { return this.minY() - rectAbove.maxY() }
  Rect.prototype.spaceOnBottom = function(rectBelow) { return rectBelow.minY() - this.maxY() }

  Rect.prototype.roomOnRight = function(room) { return room.maxX() - this.maxX()}
  Rect.prototype.roomOnBottom = function(room) { return room.maxY() - this.maxY()}
  Rect.prototype.roomOnLeft = function(room) { return this.minX() - room.minX()}
  Rect.prototype.roomOnTop = function(room) { return this.minY() - room.minY()}

  Rect.prototype.translate = function(point) {
    this.origin.x += point.x
    this.origin.y += point.y
  }

//  Rect.prototype.fitInColumn = function(x, colWidth) {
//
//    if( this.anchor.x === 0) {
//      this.origin.x += x - this.minX()
//    } else if( this.anchor.x === 1) {
//      this.origin.x += (x + colWidth) - this.maxX()
//    } else {
//      // TODO:
//    }
//  }


  ///////////////////////////////////
  // Export to d3.trait
  //


  trait.Point = Point
  trait.Size = Size
  trait.Margin = Margin
  trait.Rect = Rect

}(d3, d3.trait));

(function(d3, trait) {


  function axes( config) {
    return {
      x: config.xAxis || 'x1',
      y: config.yAxis || 'y1'
    }
  }

  function accessXDefault( d) { return d[0]}
  function accessYDefault( d) { return d[1]}
  function accessNull( d) { return d}
  function accessIndex( d, i) { return i}

  function accessorsXY( config, axes) {
    return {
      x: config[axes.x] || accessXDefault,
      y: config[axes.y] || accessYDefault,
      seriesData: config.seriesData || accessNull,
      seriesName: config.seriesName || accessIndex
    }
  }


  function emptyIfNotObjectOrNull( target) {
    return typeof(target) !== 'object' || target === null || target === 'undefined' ? {} : target
  }


  function updateDeep(target, o) {
    // Inspired by: https://github.com/danvk/dygraphs/blob/master/dygraph-utils.js updateDeep
    if (typeof(o) !== 'undefined' && o !== null) {
      for (var k in o) {
        if (o.hasOwnProperty(k)) {
          var val = o[k]
          target[k] = val === null || val === undefined ? val
            : Array.isArray(val) ? val.slice(0)
            : typeof(val) === 'object' ? updateDeep( emptyIfNotObjectOrNull(target[k]), val)
            : val
        }
      }
    }
    return target;
  }

  // Code depends on certain object hierarchies in the config. We don't want the user
  // to supply null to clip a whole object out of the config.
  //
  function wontOverwriteObjectWithNull( tVal, oVal) {
    // if tVal is not an object, we're OK
    // if tVal is an object then
    //  tVal  oVal
    // ----- -----
    // !null !null true deepUpdate
    //  null !null true assign with deep copy
    // !null  null false
    //  null  null // no op
    //
    if( typeof( tVal) !== 'object') {
      return true
    } else {
      var tNull = tVal === null || tVal === undefined
      var oNull = oVal === null || oVal === undefined
      var bothNotNull = ! tNull && ! oNull
      return bothNotNull || (tNull && ! oNull)
    }
  }

  /**
   * Nice idea to force the user config to match the default config. The problem is when
   * a config parameter can be a number or array or function. We would need extra meta data
   * to support this. Even with that, the Javascript type system isn't great at determining
   * types.
   *
   * @param target
   * @param o
   * @returns {*}
   */
  function updateExistingKeysDeep(target, o) {
    if( typeof(o) !== 'undefined' && o !== null) {
      for( var k in target) {
        if( target.hasOwnProperty(k) && o.hasOwnProperty(k)) {
          var tVal = target[k],
              oVal = o[k],
              oTyp = typeof oVal
          if( typeof(tVal) === oTyp && wontOverwriteObjectWithNull( tVal, oVal) )
          target[k] = oVal === null ? target[k] = null
            : Array.isArray(oVal) ? target[k] = oVal.slice(0)  // TODO: should we make array objects deep update?
            : oTyp === 'object' ? updateExistingKeysDeep( tVal, oVal)
            : oVal
        }
      }
    }
    return target
  }

  function makeConfig( defaultConfig, config) {
    var deepCopy = JSON.parse(JSON.stringify(defaultConfig))
    return updateDeep( deepCopy, config)
  }

  ///////////////////////////////////
  // Export to d3.trait
  //


  trait.config.axes = axes
  trait.config.accessorsXY = accessorsXY

}(d3, d3.trait));

(function(d3, trait) {

  function rangeTranslate(lastDomainMax, domain, scale) {
    if( !lastDomainMax )
      return 0

    //   |<-------- range ------->|
    //      |<-- visible range -->|

    var domainMax = d3.trait.utils.extentMax(domain)
    var domainMin = d3.trait.utils.extentMin(domain)

    var lastRangeMax = scale(lastDomainMax)
    var rangeMin = scale(domainMin)
    if( lastRangeMax < rangeMin )
      return 0
    else
      return lastRangeMax - scale(domainMax)
  }

  function simplePathRedraw(series, attrD) {
    series.selectAll("path")
      .attr("d", attrD)
      .attr("transform", null)
  }

  /**
   * Update the paths with current data and scale. If trending, slide the new
   * data onto the chart from the right.
   *
   * @param type Type of update: "domain", "trend"
   * @param duration If available, the duration for the update in millisecods
   * @param scale
   * @param series The series list to update.
   * @param lastDomainMax Domain max from last trend update. Can be undefined if chart starts with no data.
   * @returns domainMax
   */
  function updatePathWithTrend(type, duration, scale, series, attrD, lastDomainMax) {

    // TODO: The scale.range() needs to be wider, so we draw the new line off the right
    // then translate it to the left with a transition animation.

    var domain = scale.domain()
    var domainMax = d3.trait.utils.extentMax(domain)

    if( type === "trend" ) {

      var translateX = rangeTranslate(lastDomainMax, domain, scale)

      if( translateX !== 0 ) {

        series.attr("transform", null)
        series.selectAll("path")
          .attr("d", attrD)

        // slide the line left
        if( duration === 0 || !duration ) {
          series.attr("transform", "translate(" + translateX + ")")
        } else {
          series.transition()
            .duration(duration)
            .ease("linear")
            .attr("transform", "translate(" + translateX + ")")
          //.each("end", tick);
        }
      } else {
        simplePathRedraw(series, attrD)
      }

    } else {
      simplePathRedraw(series, attrD)
    }

    return domainMax
  }

  /**
   * Return the minimum distance between each data point that is within
   * the indicesExtent. The indicesExtent is typically the data indices
   * that are currently visible on the chart. The distance used is the
   * scale's range, not the domain space.
   *
   * @param data          Array of data.
   * @param indicesExtent Extent of indices used to calculate minimum distance.
   * @param accessor      Data accessor
   * @param scale         Scale for data
   * @returns Minimum distance as a number
   */
  function minDistanceBetween(data, indicesExtent, accessor, scale) {
    var range = scale.range(), //Number.MAX_VALUE,
        min = range[range.length - 1] - range[0],
        length = data.length

    if( length < 2 || indicesExtent.length < 2 )
      return min

    var i = indicesExtent[0],
        lastIndex = Math.min(length - 1, indicesExtent[1])

    if( i < 0 || i >= length )
      return min

    var current,
        last = scale(accessor(data[i], i))

    i++
    for( ; i <= lastIndex; i++ ) {
      current = scale(accessor(data[i], i))
      min = Math.min(min, current - last)
      last = current
    }

    return min
  }

  /**
   * Return the extent of indices withing the domain extent. This is typicaly
   * used to return the indices that are currently visible on the chart.
   *
   * @param data         Array of data
   * @param accessor     Data accessor
   * @param domainExtent Domain extent. Array with 0 being min and 1 being max.
   * @returns Indices extent with array 0 being first and array 1 being last.
   */
  function dataIndicesExtentForDomainExtent(data, accessor, domainExtent) {
    if( data.length <= 0 )
      return null

    var min = d3.trait.utils.extentMin(domainExtent),
        max = d3.trait.utils.extentMax(domainExtent)

    var bisector = d3.bisector(accessor),
        biLeft = bisector.left,
        biRight = bisector.right,
        firstIndex = biLeft(data, min),
        lastIndexPlusOne = biRight(data, max)

    //return {first: firstIndex, lastPlusOne: lastIndexPlusOne}
    return [firstIndex, lastIndexPlusOne - 1]
  }


  trait.chart.utils.updatePathWithTrend = updatePathWithTrend
  trait.chart.utils.minDistanceBetween = minDistanceBetween
  trait.chart.utils.dataIndicesExtentForDomainExtent = dataIndicesExtentForDomainExtent


}(d3, d3.trait));

(function(d3, trait) {

  function makeConfig(config) {
    var focus = {
      distance: 14,
      axis:     null
    }
    if( config.focus ) {
      focus.distance = d3.trait.utils.configFloat(config.focus.distance, focus.distance)
      focus.axis = config.focus.axis
    }
    return focus
  }


  function getRangePointNormal( item, access, x, y) {
    return new d3.trait.Point(x(access.x(item)), y(access.y(item)))
  }

  function getRangePointStacked( item, access, x, y) {
    return new d3.trait.Point(x(access.x(item)), y(item.y0 + access.y(item)))
  }

  function getFocusItem(series, data, index, access, x, y, getRangePoint, focusPoint) {
    var dist, distX,
        item = data[index],
        rangePoint = getRangePoint( item, access, x, y)
    dist = rangePoint.distance( focusPoint)
    distX = rangePoint.distanceX( focusPoint)
    return {
      series: series,
      index: index,
      item: item,
      point: rangePoint,
      distance: dist,
      distanceX: distX
    }
  }

  function withinFocusDistance( found, focusConfig) {
    var distance = focusConfig.axis === 'x' ? found.distanceX : found.distance
    return distance <= focusConfig.distance
  }
  /**
   *
   * @param data Series data to search for focus points.
   * @param focusPoint Find data closest to this point.
   * @param focusConfig From trait.focus.utils.makeConfig
   * @param access x, y, seriesData
   * @param color  function( series) returns color for series.
   * @param isDataStacked  T: This is an area plot with access.y(d) and d.y0. F: Use access.y(d)
   * @returns Array of focus objects
   */
  function getFocusItems( data, focusPoint, focusConfig, access, x, y, color, isDataStacked) {
    var foci = [],
        targetDomain = new d3.trait.Point(x.invert(focusPoint.x), y.invert(focusPoint.y)),
        bisectLeft = d3.bisector(access.x).left,
        getRangePoint = isDataStacked ? getRangePointStacked : getRangePointNormal

    data.forEach(function(series, seriesIndex, array) {
      var found, alterIndex,
          data = access.seriesData(series),
          // search the domain for the closest point in x
          index = bisectLeft(data, targetDomain.x)

      if( index >= data.length )
        index = data.length - 1
      found = getFocusItem(series, data, index, access, x, y, getRangePoint, focusPoint)

      alterIndex = found.index - 1
      if( alterIndex >= 0 ) {
        var alter = getFocusItem(series, data, alterIndex, access, x, y, getRangePoint, focusPoint)
        // console.log( "found x=" + access.x( found.item) + " y=" + access.y( found.item) + " d=" + found.distance + "  " + targetDomain.x + " " + targetDomain.y)
        // console.log( "alter x=" + access.x( alter.item) + " y=" + access.y( alter.item) + " d=" + alter.distance + "  " + targetDomain.x + " " + targetDomain.y)
        if( focusConfig.axis === 'x' ) {
          if( alter.distanceX < found.distanceX )
            found = alter
        } else {
          if( alter.distance < found.distance )
            found = alter
        }
      }

      if( withinFocusDistance( found, focusConfig) ) {
        found.color = color(series)
        foci.push(found)
      }
    })

    return foci
  }


  if( ! trait.focus)
    trait.focus = {}

  trait.focus.utils = {
    makeConfig: makeConfig,
    getFocusItems: getFocusItems
  }


}(d3, d3.trait));

(function(d3, trait) {


  var LEFT = -1,
      RIGHT = 1

  function sortOnY(a, b) { return a.rect.origin.y - b.rect.origin.y}

  // We have enough space to fit everything.
  function listNudgeUpFromBottom(itemsWithRect, maxY) {
    var last = null,
        index = itemsWithRect.length - 1

    for( ; index >= 0; index-- ) {
      var spacingBottom,
          item = itemsWithRect[index],
          r = item.rect

      if( index === itemsWithRect.length - 1 ) {
        spacingBottom = maxY - r.maxY()
      } else {
        spacingBottom = last.rect.spaceOnTop(r)
      }

      if( spacingBottom < 0 )
        r.origin.y += spacingBottom
      last = item
    }
  }

  // top: 10, mo: 0  noop
  // top: -1, mo: 0  ty = 1
  // top: 10, mo: 1  ty = -11  -mo - top
  // top: -1, mo: 2  ty = -1   -mo - top
  function rectRemoveOverlap(r, spacingTop, minOverlap) {
    var ty = 0

    if( minOverlap > 0 ) {
      ty = -minOverlap - spacingTop
    } else if( spacingTop < 0 ) {
      ty = -spacingTop
    }
    r.origin.y += ty
  }

  // Starting from top, remove overlaps by nudging down
  // if minOverlap > 0, then we know they won't fit and need to overlap
  //
  function removeOverlapFromTop(itemsWithRect, inRect, minOverlap) {
    var r, last = null

    itemsWithRect.forEach(function(item, index, array) {
      r = item.rect
      if( index === 0 ) {
        rectRemoveOverlap(r, r.roomOnTop(inRect), minOverlap)
      } else {
        rectRemoveOverlap(r, r.spaceOnTop(last.rect), minOverlap)
      }
      last = item
    })
  }

  /**
   * Translate all items from start to stop-1 by ty.
   * @param itemsWithRect
   * @param start Starting index
   * @param stop Do not include stop index
   * @param ty Amount to translate
   */
  function listTranslateY(itemsWithRect, start, stop, ty) {
    var item,
        index = start

    for( ; index < stop; index++ ) {
      item = itemsWithRect[ index]
      item.rect.origin.y += ty
    }
  }

  // Starting from top, nudge up to restore balance so callouts are
  // more equally offset
  //
  // There is no overlap
  //
  function listBalanceFromTop(itemsWithRect, inRect, originalYs) {
    var r, itemSpaceOnTop, item,
        index = 0,
        spanStart = 0,
        spanCount = 0,
        spanSpaceOnTop = 0,
        last = null,
        yOffsetSum = 0,
        yOffsetAve = 0

    // Find a span of +y offsets with no spacing. Get the average offset.
    // If space above, move all up by average offset. Find the next span.
    for( ; index < itemsWithRect.length; index++ ) {
      item = itemsWithRect[ index]
      r = item.rect
      itemSpaceOnTop = index === 0 ? r.roomOnTop(inRect) : r.spaceOnTop(last.rect)

      if( itemSpaceOnTop > 0 ) {
        // end of last span or start of new span

        if( spanCount > 0 ) {
          // work the span
          yOffsetAve = yOffsetSum / spanCount
          if( yOffsetAve < -1 || yOffsetAve > 1 ) {
            var saveYOffsetAve = yOffsetAve
            // move the span
            if( yOffsetAve > 0 ) {
              // Move up, but not more than spanSpaceOnTOp
              yOffsetAve = Math.min(yOffsetAve, spanSpaceOnTop)
            } else {
              // Move down, but not more than current itemSpaceOnTOp
              yOffsetAve = -Math.min(-yOffsetAve, itemSpaceOnTop)
            }

            listTranslateY(itemsWithRect, spanStart, index, -yOffsetAve)
            spanSpaceOnTop -= yOffsetAve
            itemSpaceOnTop += yOffsetAve

            if( itemSpaceOnTop > 0 ) {
              // Reset counters to start a new span.
              spanCount = 1
              spanStart = index
              spanSpaceOnTop = itemSpaceOnTop
              yOffsetSum = 0
            } else {
              // Span moved down and joined the current item.
              // span was moved down so the yOffset needs adjusting.
              yOffsetSum = (saveYOffsetAve - yOffsetAve) * spanCount
              spanCount++
              yOffsetSum += r.origin.y - originalYs[index]
            }
          }

        } else {
          // Reset counters
          spanCount = 1
          spanStart = index
          spanSpaceOnTop = itemSpaceOnTop
          yOffsetSum += r.origin.y - originalYs[index]
        }
      } else {
        // part of current span
        spanCount++
        yOffsetSum += r.origin.y - originalYs[index]
      }

      last = item
    }

    if( spanCount > 1 ) {
      // work the span
      yOffsetAve = yOffsetSum / spanCount
//            console.log( "yOffsetAve = yOffsetSum / spanCount " + yOffsetAve + " " + yOffsetSum + " " + spanCount)
      if( yOffsetAve < -1 || yOffsetAve > 1 ) {
        // move the span
        if( yOffsetAve > 0 ) {
          // Move up, but not more than spanSpaceOnTOp
          yOffsetAve = Math.min(yOffsetAve, spanSpaceOnTop)
        } else {
          // Move down, but not more than space on bottom
          var bottom = last.rect.roomOnBottom(inRect)
          if( bottom > 0 )
            yOffsetAve = -Math.min(-yOffsetAve, bottom)
          else
            yOffsetAve = 0
        }

        listTranslateY(itemsWithRect, spanStart, index, -yOffsetAve)

      }

    }
  }

  function  layoutVertical(itemsWithRect, inRect) {
    if( itemsWithRect.length <= 0 )
      return;

    itemsWithRect.sort(sortOnY)

    var totalSpacing = 0,
        minOverlap = 0,
        height = inRect.size.height,
        totalHeight = d3.sum(itemsWithRect, function(item) { return item.rect.size.height}),
        originalYs = itemsWithRect.map(function(item) { return item.rect.origin.y})

    if( totalHeight > height )
      minOverlap = ( totalHeight - height ) / itemsWithRect.length
    else
      totalSpacing = height - totalHeight

    removeOverlapFromTop(itemsWithRect, inRect, minOverlap)

    if( totalSpacing > 0 ) {
      listNudgeUpFromBottom(itemsWithRect, inRect.maxY())
      listBalanceFromTop(itemsWithRect, inRect, originalYs)
    }
  }

  /**
   * adjustOrientationToFitWidth
   *
   * Items may already be anchored left or right. If they don't fit on the right,
   * change the anchor to left. If it still doesn't fit, nudge it to the left.
   * @param itemsWithRect
   * @param width
   * @param height
   */
  function adjustOrientationToFitWidth(itemsWithRect, inRect) {
    var r,
        left = [],
        right = []

    itemsWithRect.forEach(function(item, index, array) {
      r = item.rect
      if( r.anchor.x < 0.5 ) {
        // right justified
        if( r.roomOnRight(inRect) >= 0 ) {
          item.orient = RIGHT
          right.push(item)
        } else {
          item.orient = LEFT
          r.anchor.x = 1
          left.push(item)
        }
      } else {
        // left justified
        if( r.roomOnLeft(inRect) >= 0 ) {
          item.orient = LEFT
          left.push(item)
        } else {
          item.orient = RIGHT
          r.anchor.x = 0
          right.push(item)
        }
      }

    })

    return [left, right]
  }

  function layoutVerticalAnchorLeftRight(itemsWithRect, inRect) {
    var leftRight = adjustOrientationToFitWidth(itemsWithRect, inRect)
    layoutVertical(leftRight[0], inRect)
    layoutVertical(leftRight[1], inRect)
  }

  function layoutByOrientation(itemsWithRect, rect, orient, _wrap) {
    var r, i,
        coordinate = 0,
        wrap = _wrap || false

    switch( orient ) {
      case 'left':
        coordinate = rect.minX()
        itemsWithRect.forEach(function(item, index, array) {
          r = item.rect
          r.origin.x += coordinate - r.minX()
          coordinate = r.maxX()
        })
        break;
      case 'right':
        coordinate = rect.maxX()
        for( i = itemsWithRect.length - 1; i >= 0; i-- ) {
          r = itemsWithRect[i].rect
          r.origin.x += coordinate - r.maxX()
          coordinate = r.minX()
        }
        break;
      case 'top':
        coordinate = rect.minY()
        itemsWithRect.forEach(function(item, index, array) {
          r = item.rect
          r.origin.y += coordinate - r.minY()
          coordinate = r.maxY()
        })
        break;
      case 'bottom':
        coordinate = rect.maxY()
        for( i = itemsWithRect.length - 1; i >= 0; i-- ) {
          r = itemsWithRect[i].rect
          r.origin.y += coordinate - r.maxY()
          coordinate = r.minY()
        }
        break;
      default:
    }
  }

  ///////////////////////////////////
  // Export to d3.trait
  //



  trait.layout.adjustOrientationToFitWidth = adjustOrientationToFitWidth
  trait.layout.vertical = layoutVertical
  trait.layout.byOrientation = layoutByOrientation
  trait.layout.verticalAnchorLeftRight = layoutVerticalAnchorLeftRight
  trait.layout.utils = {
    listNudgeUpFromBottom: listNudgeUpFromBottom,
    removeOverlapFromTop: removeOverlapFromTop,
    listBalanceFromTop: listBalanceFromTop
  }

}(d3, d3.trait));

(function(d3, trait) {

  function minFromData(data, access, defaultValue) {
    return minFromDataDo( data, access.series, access.data, defaultValue)
  }
  function minFromAreaData(data, access, defaultValue) {
    return minFromDataDo( data, access.series, function( d) { return d.y0}, defaultValue)
  }
  function minFromDataDo( data, accessSeries, accessData, defaultValue) {
    var min = d3.min(data, function(s) { return d3.min(accessSeries(s), accessData); })
    if( !min )
      min = defaultValue ? defaultValue : 0
    return min
  }

  function maxFromData(data, access, defaultValue) {
    return maxFromDataDo( data, access.series, access.data, defaultValue)
  }

  function maxFromAreaData(data, access, defaultValue) {
    return maxFromDataDo( data, access.series, function( d) { return d.y0 + access.data(d)}, defaultValue)
  }
  function maxFromDataDo(data, accessSeries, accessData, defaultValue) {
    var max = d3.max(data, function(s) { return d3.max(accessSeries(s), accessData); })
    if( !max )
      max = defaultValue ? defaultValue : 0
    return max
  }


  /**
   * Return the extent for all data in all series, example: [min, max] .
   * If the data in each series is empty, return the supplied default or [0,1]
   * if min === max, return [min-1, max+1]
   *
   * @param data     Multiple series of data
   * @param access   Accessors {series: function, data: function}
   * @param defaultValue A default in case there is no data otherwise [0,1] is returned
   * @returns  The extent of all data in an array of the form [min,max]
   */
  function extentFromData(data, access, defaultValue) {
    var extents, min, max

    // Get array of extents for each series.
    extents = data.map(function(s) { return d3.extent( access.series(s), access.data) })
    return extentFromData2( extents, defaultValue)
  }

  function extentFromAreaData(data, access, defaultValue) {
    var extents, min, max

    // Get array of extents for each series.
    extents = data.map(function(s) {
      var series = access.series(s)
      var extent = [
        d3.min( series, function( d) { return d.y0}),
        d3.max( series, function( d) { return d.y0 + access.data(d)})
      ]
      return extent
    })

    return extentFromData2( extents, defaultValue)
  }

  /**
   *
   * @param extents Array of extents for each series.
   * @param defaultValue if no extents, use default if available.
   * @returns Extent array.
   */
  function extentFromData2( extents, defaultValue) {
    var min, max

    min = d3.min(extents, function(e) { return e[0] }) // the minimums of each extent
    max = d3.max(extents, function(e) { return e[1] }) // the maximums of each extent

    if( !min && !max )
      return defaultValue ? defaultValue : [0, 1]

    if( min === max ) {
      min -= 1
      max += 1
    }
    return [min, max]
  }

  /**
   * Is new extend greater than current extent?
   * @param currentExtent
   * @param newExtent
   * @returns {boolean}
   */
  function isExtentExtended( currentExtent, newExtent) {
    if( ! currentExtent || currentExtent.length < 2) {
      return true
    } else {
      return newExtent[0] < currentExtent[0] ||
        trait.utils.extentMax( newExtent) > trait.utils.extentMax( currentExtent)
    }
  }

  function extendExtent( currentExtent, newExtent) {
    if( ! newExtent || newExtent.length < 2)
      return currentExtent

    if( ! currentExtent || currentExtent.length < 2)
      return newExtent

    if( newExtent[0] < currentExtent[0]) {
      currentExtent[0] = newExtent[0]
    }
    if( trait.utils.extentMax( newExtent) > trait.utils.extentMax( currentExtent)) {
      currentExtent[ currentExtent.length-1] = trait.utils.extentMax( newExtent)
    }
    return currentExtent
  }


  if( !trait.utils )
    trait.utils = {}

  trait.utils.minFromData = minFromData
  trait.utils.maxFromData = maxFromData
  trait.utils.minFromAreaData = minFromAreaData
  trait.utils.maxFromAreaData = maxFromAreaData
  trait.utils.extentFromData = extentFromData
  trait.utils.extentFromAreaData = extentFromAreaData
  trait.utils.isExtentExtended = isExtentExtended
  trait.utils.extendExtent = extendExtent

}(d3, d3.trait));

(function(d3, trait) {
  var debug = false

  function orientFromConfig(axisChar, orient) {
    if( orient )
      return orient
    else
      return axisChar === 'x' ? 'bottom' : 'left'
  }

  /**
   * extentTicks: T: ticks on each extent. Overrides ticks.
   * ticks: Approximate number of ticks
   * @param config
   * @returns {{name: (*|Function|d3.trait.axis|d3.trait.axis|Function|Function), axisChar: string, accessData: *, axisMargin: (*|number), orient: *, ticks: (*|Function|Function|Function|Function|Function|Function|Function|Function|Function|Function|Function|Function|Function|Function|Function|Function|Function|Function|Function|null), extentTicks: (*|boolean)}}
   */
  function axisConfig(config) {
    var name = config.axis,        // x1, y1, x2, etc.
        axisChar = name.charAt(0), // x | y
        c = {
          name:        name,
          axisChar:    axisChar,
          accessData:  config[name],
          orient:      orientFromConfig(axisChar, config.orient),
          ticks:       config.ticks,
          extentTicks: config.extentTicks || false,
          tickSize:    config.tickSize,
          tickPadding: config.tickPadding,
          tickFormat:  config.tickFormat,
          nice:        config.nice,
          label:       config.label,
          lines:       config.lines,
          gridLines:   config.gridLines
        }

    c.labelLineHeight = c.label ? (config.labelLineHeight || 14) : 0
    c.axisMargin = config.axisMargin || (40 + c.labelLineHeight)
    return c
  }

  // TODO: No! Delete this. We're using self.layoutAxis now! We do need to adjust 2 for extent label.
  function adjustChartMarginForAxis(_super, c) {
    switch( c.orient ) {
      case 'left':
        _super.plusMarginTop(2) // Make room for top extent label
        _super.plusMarginLeft(c.axisMargin)
        break;
      case 'bottom':
        _super.plusMarginBottom(c.axisMargin);
        break;
      case 'top':
        _super.plusMarginTop(c.axisMargin);
        break;
      case 'right':
        _super.plusMarginTop(2) // Make room for top extent label
        _super.plusMarginRight(c.axisMargin);
        break;
      default:
    }
  }

  function containerTransform(self, c) {

    switch( c.orient ) {
      case 'left':
        return 'translate(' + self.marginLeft() + ',' + self.marginTop() + ')';
      case 'bottom':
        return 'translate(' + self.marginLeft() + ',' + (self.chartHeight() + self.marginTop()) + ')';
      case 'top':
        return 'translate(' + self.marginLeft() + ',0)';
      case 'right':
        return 'translate(' + (self.marginLeft() + self.chartWidth()) + ',' + self.marginTop() + ')';
      default:
        return null;
    }
  }

  function axisTransform(self, c) {
    if( !c.label )
      return null;

    switch( c.orient ) {
      case 'left':
        return 'translate(' + c.labelLineHeight + ',0)';
      case 'bottom':
        return null;
      case 'top':
        return 'translate(0,' + c.labelLineHeight + ',0)';
      case 'right':
        return null;
      default:
        return null;
    }
  }

  function labelTransform(self, c, label) {
    if( !c.label )
      return null;

    var tx, ty,
        bBox = label.node().getBBox(),
        labelWidth2 = Math.round(bBox.width / 2),
        tXorY = c.axisMargin - c.labelLineHeight
    switch( c.orient ) {
      case 'left':
        tx = -c.axisMargin + c.labelLineHeight
        ty = self.chartHeight() / 2 + labelWidth2
        return 'translate( ' + tx + ',' + ty + ') rotate( -90)';
      case 'bottom':
        tx = self.chartWidth() / 2 - labelWidth2
        ty = c.axisMargin - c.labelLineHeight
        return 'translate( ' + tx + ',' + ty + ')';
      case 'top':
        tx = self.chartWidth() / 2 - labelWidth2
        ty = -c.axisMargin + c.labelLineHeight
        return 'translate( ' + tx + ',' + ty + ')';
      case 'right':
        tx = c.axisMargin - c.labelLineHeight
        ty = self.chartHeight() / 2 - labelWidth2
        return 'translate( ' + tx + ',' + ty + ') rotate( 90)';
      default:
        return null;
    }
  }

  function applyTickConfig( group, axis, scale, c, self) {
    if( c.extentTicks )
      axis.tickValues(scale.domain())
    else if( c.ticks )
      axis.ticks(c.ticks)

    if( c.gridLines)
      applyGridlines(group, axis, c, self)
    else if( c.tickSize )
      axis.tickSize(c.tickSize)

    if( c.tickPadding )
      axis.tickPadding(c.tickPadding)

    if( c.tickFormat )
      axis.tickFormat(c.tickFormat)


  }

  function applyGridlines( group, axis, c, self) {

    group.classed( 'grid', c.gridLines)

    switch( c.axisChar ) {
      case 'x':
        axis.tickSize( - self.chartHeight())
        break
      case 'y':
        axis.tickSize( - self.chartWidth())
        break
      default:
    }
  }

  var AxisLineClass = 'axis-line'

  function makeLineClass( d) {
    return typeof(d) === 'object' && d.hasOwnProperty( 'class') ? AxisLineClass + ' ' + d['class'] : AxisLineClass
  }


  /**
   *
   * config.ticks
   *  ticks(d3.time.years) -- tick every year for time scale
   *  tickValues(x.domain())
   *  tickSubdivide(9) -- 10 subticks per tick
   *  tickSubdivide(9).tickSize(6, 3, 10) -- 10 (smaller) subticks and longer start/end ticks
   * @param _super
   * @param _config
   * @returns {Function}
   * @private
   */
  function _axisLinear(_super, _config) {
    var group, label, axis,
        c = axisConfig(_config),
        scale = _super[c.name]()  // ex: x1()

    function makeLinePath( d) {
      var v = isNaN( d) ? d.value : d,
          sv = scale(v)
      if( c.axisChar === 'x')
        return 'M' + sv + ','+'0L' + sv + ',' + _super.chartHeight()
      else
        return 'M0,' + sv + 'L' + _super.chartWidth() + ',' + sv
    }


    // TODO: No don't call this. We're using self.layoutAxis now!
    //adjustChartMarginForAxis( _super, c)

    function axisLinear(_selection) {
      var self = axisLinear

      _selection.each(function(_data) {
        var element = this

        if( !group ) {
          group = this._container.append('g')
            .classed('axis', true)
            .classed('axis-' + c.name, true)
          if( c.label )
            label = group.append('text').classed('axis-label axis-label-' + c.name, true)
          axis = d3.svg.axis().scale( scale)
        }

        if( debug)
          console.log( 'axisLinear.each ' + c.name)

        axis.orient(c.orient)
        applyTickConfig( group, axis, scale, c, self)

        // c.axisMargin is the width or height of the axis.
        self.layoutAxis( c.name, group, c.orient, c.axisMargin)

        //group.attr( {transform: containerTransform( self, c)})
        if( c.label ) {
          //group.attr( {transform: axisTransform( self, c)})
          label.text(c.label)
          label.attr({ transform: labelTransform(self, c, label) })
        }
        group.call(axis);

        // Do we have to provide a line to extend each end of the axis?
        if( _super.isMinRangeMargin(c.name) ) {

          var extData = d3.trait.utils.getScaleExtensions(_super, c.name, scale)

          var extension = group.selectAll("path.axis-extension")
            .data(extData)

          extension.enter()
            .append("path")
            .attr("class", "axis-extension")
            .attr("d", function(d) {
              return "M" + d[0] + ",0L" + d[1] + ",0";
            })

          extension.transition().duration(0)
            .attr("class", "axis-extension")
            .attr("d", function(d) {
              return "M" + d[0] + ",0L" + d[1] + ",0";
            })
        }

        if(c.lines && Array.isArray(c.lines)) {
          var line = group.selectAll('path.' + AxisLineClass)
            .data(c.lines)
          line.enter()
            .append("path")
            .attr("class", makeLineClass)
            .attr("d", makeLinePath)

          line.attr("class", "axis-line")
            .attr("d", makeLinePath)
        }


      })
    }

    axisLinear.update = function(type, duration) {
      this._super(type, duration)
      if( debug)
        console.log( 'axisLinear.update ' + c.name)

      // Need this for extentTicks, maybe others
      //
      applyTickConfig( group, axis, scale, c, this)

      if( duration === 0 ) {
        group.call(axis);
      } else {
        group.transition()
          .duration(duration || _super.duration())
          .ease("linear")
          .call(axis);
      }

      return this;
    }

    _super.onRangeMarginChanged('axisLinear-' + c.name, axisLinear)

    return axisLinear;
  }

  function tickValuesForMonthDays(x) {
    var domain = x.domain()
    var minDate = domain[0]
    var maxDate = domain[ domain.length - 1]
    var everyDate = d3.time.day.range(minDate, maxDate);
    return everyDate.filter(function(d, i) {
      var date = d.getDate()
      //return date === 1 || date % 5 === 0;
      return date % 5 === 0;
    });
  }

  function _axisMonth(_super, _config) {
    var group, label, lastDomainMax,
        axis = d3.svg.axis(),
        scaleForUpdate = d3.time.scale(),
        c = axisConfig(_config),
        scale = _super[c.name]()

    // TODO: No don't call this. We're using self.layoutAxis now!
//        adjustChartMarginForAxis( _super, c)

    function axisMonth(_selection) {
      var self = axisMonth

      _selection.each(function(_data) {
        var element = this

        if( !group ) {
          group = this._container.append('g')
            .classed('axis', true)
            .classed('axis-' + c.name, true)
          if( c.label )
            label = group.append('text').classed('axis-label axis-label-' + c.name, true)
          axis = d3.svg.axis()
        }

        var domain = scale.domain()

        scaleForUpdate.range(scale.range())
        scaleForUpdate.domain(scale.domain())
        if( c.nice )
          scaleForUpdate.nice(c.nice)

        axis.scale(scaleForUpdate)
          .orient(c.orient)
        applyTickConfig( group, axis, scaleForUpdate, c, self)

        //.tickFormat(d3.time.format('%e')) // %d is 01, 02. %e is \b1, \b2
        //.ticks( 15)
        //.tickValues( tickValuesForMonthDays( scaleForUpdate))
        //.tickSubdivide(4)

        self.layoutAxis( c.name, group, c.orient, c.axisMargin)
        if( c.label ) {
          label.text(c.label)
          label.attr({ transform: labelTransform(self, c, label) })
        }

        group
//                    .attr({transform: containerTransform( self, c)})
          .call(axis);

        var extension = group.selectAll("path.axis-extension")
          .data(d3.trait.utils.isValidDate(domain[0]) ? [domain[0]] : [])

        extension.transition()
          .attr("class", "axis-extension")
          .attr("d", function(d) {
            return "M0,0L" + scaleForUpdate(d) + ",0";
          })

        extension.enter()
          .append("path")
          .attr("class", "axis-extension")
          .attr("d", function(d) {
            return "M0,0L" + scaleForUpdate(d) + ",0";
          })

        if( d3.trait.utils.isData(_data, _config.seriesData) )
          lastDomainMax = d3.trait.utils.extentMax(domain)

      })
    }

    function getScaleForUpdate() {

      // |<------- scale ------>|
      //   |<--scaleForUpdate-->|
      var domain = scale.domain() // original scale
      var domainMin = d3.trait.utils.extentMin(domain)
      var domainMax = d3.trait.utils.extentMax(domain)
      if( lastDomainMax ) {
        var lastDomainMaxTime = lastDomainMax.getTime()
        if( lastDomainMaxTime > domainMin.getTime() ) {
          var delta = domainMax.getTime() - lastDomainMaxTime
          domainMin = new Date(domainMin.getTime() + delta)
        }
      }
      return [domainMin, domainMax]
    }

    axisMonth.update = function(type, duration) {
      this._super(type, duration)

      scaleForUpdate.range(d3.trait.utils.getScaleRange(_super, c.name))

      var domainForUpdate = getScaleForUpdate()
      scaleForUpdate.domain(domainForUpdate)
      lastDomainMax = d3.trait.utils.extentMax(domainForUpdate)

      // slide the x-axis left for trends
      if( duration === 0 ) {
        group.call(axis);
      } else {
        group.transition()
          .duration(duration || _super.duration())
          .ease("linear")
          .call(axis);
      }
      return this;
    }

    _super.onChartResized('axisMonth-' + c.name, axisMonth)
    _super.onRangeMarginChanged('axisMonth-' + c.name, axisMonth)


    return axisMonth;
  }


  trait.axis.linear = _axisLinear


  if( !trait.axis.time )
    trait.axis.time = {}
  trait.axis.time.month = _axisMonth

}(d3, d3.trait));

(function(d3, trait) {

  function makeArea( stacked, access, x, y, interpolate) {
    var area = d3.svg.area()
      .interpolate( interpolate || "linear")

    if( stacked ) {
      area = area.x(function(d) { return x( access.x(d)); })
        .y0(function(d) { return y(d.y0); })
        .y1(function(d) { return y(d.y0 + access.y(d)); })

    } else {

      area = area.x(function(d) { return x(access.x(d)); })
        .y1(function(d) { return y(access.y(d)); })
      // y0 is set in _selection.each.
    }

    return area
  }

  function _chartArea(_super, _config) {
    // Store the group element here so we can have multiple area charts in one chart.
    // A second "area chart" might have a different y-axis, style or orientation.
    var group, series, filteredData, lastDomainMax, stackLayout,
        axes = trait.config.axes( _config),
        access = trait.config.accessorsXY( _config, axes),
        x1 = _super[axes.x](),
        y = _super[axes.y](),
        yMinDomainExtentFromData = _super[axes.y + 'MinDomainExtentFromData'],
        focusConfig = d3.trait.focus.utils.makeConfig(_config),
        interpolate = _config.interpolate || "linear",
        stacked = _config.stacked ? true : false,
        area = makeArea( stacked, access, x1, y, interpolate, _super.chartHeight())

    if( stacked ) {
      stackLayout = d3.layout.stack()
        .values(function(d) { return access.seriesData(d) })
        .y( access.y);
    }

    var dispatch = d3.dispatch('customHover');

    function chartArea(_selection) {
      var self = chartArea

      _selection.each(function(_data) {
        var element = this

        if( !group ) {
          var classes = _config.chartClass ? "chart-area " + _config.chartClass : 'chart-area'
          group = this._chartGroup.append('g').classed(classes, true);
        }

        filteredData = _config.seriesFilter ? _data.filter(_config.seriesFilter) : _data

        if( stacked) {
          if( filteredData.length > 0)
            stackLayout( filteredData)
          access.series = access.seriesData
          access.data = access.y
          var extent = trait.utils.extentFromAreaData( filteredData, access)
          yMinDomainExtentFromData( extent)
        } else {
          area.y0(self.chartHeight())
        }

        // DATA JOIN
        series = group.selectAll(".series")
          .data(filteredData)

        // UPDATE
        series.selectAll("path")
          .transition()
          .duration(500)
          .attr("d", function(d) { return area(access.seriesData(d)); })

        // ENTER
        series.enter()
          .append("g")
          .attr("class", "series")
          .append("path")
          .attr("class", "area")
          .attr("d", function(d) { return area(access.seriesData(d)); })
          .style("fill", self.color);

        lastDomainMax = d3.trait.utils.extentMax(x1.domain())
      })
    }

    chartArea.update = function(type, duration) {
      this._super(type, duration)

      var dur = duration || _super.duration()
      var attrD = function(d) { return area(access.seriesData(d)); }
      if( stacked) {
        stackLayout( filteredData);
        access.series = access.seriesData
        access.data = access.y
        var extent = trait.utils.extentFromAreaData( filteredData, access)
        yMinDomainExtentFromData( extent)
      }

      lastDomainMax = trait.chart.utils.updatePathWithTrend(type, dur, x1, series, attrD, lastDomainMax)

      return this;
    }

    chartArea.getFocusItems = function(focusPoint) {
      var foci = this._super(focusPoint),
          myFoci = trait.focus.utils.getFocusItems( filteredData, focusPoint, focusConfig, access, x1, y, chartArea.color, stacked) // t: isStacked

      foci = foci.concat( myFoci)
      return foci
    }


    d3.rebind(chartArea, dispatch, 'on');

    return chartArea;

  }

  trait.chart.area = _chartArea

}(d3, d3.trait));

(function(d3, trait) {

  var INSETS_NONE = 'none',          // Don't adjust insets
      INSETS_EXTEND_DOMAIN = 'extend-domain', // Extend the domain extents so bars are completely visible
      INSETS_INSET_RANGE = 'inset-range'    // Inset the scales range so bars are completely visible


  /**
   * extentTicks: T: ticks on each extent. Overrides ticks.
   * ticks: Approximate number of ticks
   * @param config
   * @returns { width:, gap:, justification, insets:}
   */
  function barConfig(config) {
    var gap = d3.trait.utils.configFloat(config.gap, 0.1),
        outerGap = d3.trait.utils.configFloat(config.outerGap, gap),
        c = {
          width:         config.width || 'auto',
          // gap is percentage of bar width (0-1)
          gap:           gap,
          // outerGap can be 0 to greater than 1
          outerGap:      outerGap,
          justification: config.justification || 'center',
          //insets: 'none' 'extend-domain', 'inset-range', {top: 0, right: 0, bottom: 0, left: 0}
          insets:        config.insets || INSETS_INSET_RANGE
        }
    return c
  }


  /**
   *
   *  Domain                 Range
   *   5     +-----------    0
   *   4                     1
   *   3   y  ___            2
   *   2     |***|           3
   *   1     |***|           4
   *   0  y0 +-----------+   5 chartHeight
   *  -1           |***| y0  6
   *  -2           |***|     7
   *  -3            ---  y   8 chartHeight
   *
   *  y = y < 0 ? y0 : y same as Math.max( y, y0)
   *  h = y(0) - y( abs(y-y0))
   *
   * @param access
   * @param barDimensions
   * @param chartHeight
   * @param x1
   * @param y
   * @returns {{x: x, y: y, width: *, height: height}}
   */
  function barAttr(access, barDimensions, chartHeight, x1, y) {
    // NOTE: for transition from enter, use  y(0) for y: and height:
    // x is middle of bar.
    // y is top of bar. Remember, the scale range is flipped for y.
    // height - chartHeight - y OR y0 - y for stacked.

    // For pos/neg bars:
    // x - same
    // y - pos: same. neg:
    //
    return {
      x:      function(d) { return x1(access.x(d)) + barDimensions.offset; },
      y:      function(d) { return y(  Math.max(access.y(d),0) ); },
      width:  barDimensions.width,
      height: function(d) { return y(0) - y( Math.abs( access.y(d))); }
//      height: function(d) { return chartHeight - y( Math.abs( access.y(d))); }
    }
  }

  function barOffsetForJustification(justification, width, gap) {
    var offset = 0
    switch( justification ) {
      case 'left':
        offset = Math.round(gap / 2);
        break;
      case 'right':
        offset = Math.round(0 - width - gap / 2);
        break;
      default:
        // center
        offset = 0 - Math.round(width / 2);
        break;
    }
    return offset
  }

  function rangeExtentOfBarsAndOuterGapForOneSeries(data, indicesExtent, accessor, scale, width, gap, outerGap, justification) {

    var i, minValue, maxValue,
        offsetLeft = barOffsetForJustification(justification, width, gap) - outerGap,
        offsetRight = barOffsetForJustification(justification, width, gap) + width + outerGap

    if( !indicesExtent )
      return scale.range()

    i = indicesExtent[0],
      minValue = scale(accessor(data[i], i)) + offsetLeft

    i = indicesExtent[1]
    maxValue = scale(accessor(data[i], i)) + offsetRight

    return [Math.floor(minValue), Math.ceil(maxValue)]
  }

  function rangeExtentOfBarsAndOuterGap(filteredData, indicesExtents, seriesData, accessor, scale, barWidth, gap, outerGap, justification) {

    var rangeExtents = filteredData.map(function(s, i) { return rangeExtentOfBarsAndOuterGapForOneSeries(seriesData(s), indicesExtents[i], accessor, scale, barWidth, gap, outerGap, justification) })
    var min = d3.min(rangeExtents, function(extent, i) { return extent[0]})
    var max = d3.min(rangeExtents, function(extent, i) { return extent[1]})

    return [min, max]
  }


  /**
   *   Handle pathological case where outer bars are centered on scale extents (so half off chart).
   *
   *    Original        inset-range       extend-domain
   *     |     |      |             |   |               |
   *    _|_   _|_     |  ___   ___  |   |   ___   ___   |
   *   |*|*| |*|*|    | |***| |***| |   |  |***| |***|  |
   *     +-----+       ---+-----+---    +----+-----+----+
   *     1     2          1     2       0    1     2    3
   *
   *   Calculate the first and last bar outer edges plus a nice "inset" and scale that down
   *   to fit in the pixels available (current range).
   */
  function getBarDimensions(filteredData, seriesData, accessor, c, scale, chartWidth) {

    // minimum scale distance between any two adjacent bars visible within the current domain.
    var width,
        minRangeMargin = null,
        domainExtent = null,
        gap = 0,
        outerGap = 0


    if( scale.rangeBand ) {
      width = c.width === 'auto' ? scale.rangeBand() : c.width
      // gap isn't known with range bands
    } else {
      var scaleDomain = scale.domain(),
          // Find the data indices (across all series) for what's visible with current domain.
          indicesExtents = filteredData.map(function(s) { return trait.chart.utils.dataIndicesExtentForDomainExtent(seriesData(s), accessor, scaleDomain) }),
          // Get the minimum distance between bar centers across all data in all series
          minDistanceX = d3.min(filteredData, function(s, i) { return trait.chart.utils.minDistanceBetween(seriesData(s), indicesExtents[i], accessor, scale) })

      width = c.width === 'auto' ? Math.max(1, Math.floor(minDistanceX * (1 - c.gap))) : c.width
      gap = Math.round(width * c.gap)
      outerGap = Math.floor(width * c.outerGap)

      // Get the minimun distance between bar centers across all data in all series
      var rangeExtent = rangeExtentOfBarsAndOuterGap(filteredData, indicesExtents, seriesData, accessor, scale, width, gap, outerGap, c.justification),
          min = rangeExtent[0],
          max = rangeExtent[1]
      //console.log( "minDistanceX: " + minDistanceX + " width: " + width + " rangeExtent: " + rangeExtent)

      if( min < 0 || max > chartWidth ) {

        if( c.insets === INSETS_INSET_RANGE ) {
          // Careful, one bar may be within chart and one bar off chart.
          var totalWidth = Math.max(max, chartWidth) - Math.min(0, min),
              scaleItDown = chartWidth / totalWidth

          if( c.width === 'auto' ) {
            width = Math.max(1, Math.floor(width * scaleItDown))
            gap = Math.round(width * c.gap)
            outerGap = Math.floor(width * c.outerGap)
          }

          if( c.insets === INSETS_INSET_RANGE ) {
            rangeExtent = rangeExtentOfBarsAndOuterGap(filteredData, indicesExtents, seriesData, accessor, scale, width, gap, outerGap, c.justification)
            min = rangeExtent[0]
            max = rangeExtent[1]

            if( min < 0 || max > chartWidth ) {
              minRangeMargin = {}
              if( min < 0 )
                minRangeMargin.left = 1 - min
              if( max > chartWidth )
                minRangeMargin.right = max - chartWidth
            }
          }
        } else if( c.insets === INSETS_EXTEND_DOMAIN ) {
          var domainMin = min < 0 ? scale.invert(min) : scaleDomain[0] ,
              domainMax = max > chartWidth ? scale.invert(max) : scaleDomain[ scaleDomain.length - 1]
          domainExtent = [domainMin, domainMax]
        }

      }

    }

    var offset = barOffsetForJustification(c.justification, width, gap)

    //console.log( "barDimensions: width, gap, offset: " + width + ", " + gap + ", " + offset)

    return {
      width:          width,
      gap:            gap,
      outerGap:       outerGap,
      offset:         offset,
      domainExtent:   domainExtent,
      minRangeMargin: minRangeMargin
    }
  }


  /**
   *
   * Example Bar Chart Configurations
   *
   * ONE            TWO            THREE          FOUR           FIVE
   * Linear Axis    Linear Axis    Ordinal Axis   Ordinal Axis   Ordinal Grouped
   * Tick centered  Tick centered  Tick centered  Tick left      Tick left
   * x(0) == 0      x(0) > 0
   * 0 label        No 0 label                                   Label left
   * scale != data  Scale == data
   *  _________      _________      _________      _________      _______________
   * |      _  |    |      _  |    |      _  |    |      _  |    |     _   _     |
   * |  _  |*| |    |  _  |*| |    |  _  |*| |    |  _  |*| |    |  _ |~| |*| _  |
   * | |*| |*| |    | |*| |*| |    | |*| |*| |    | |*| |*| |    | |*||~| |*||~| |
   * +--+---+--+     --+---+--      --+---+--      -+---+---      -+------+------+
   * 0  1   2  3       1   2          A   B          A   B          A      B
   *
   *    ONE
   *    Linear axis with scala extents outside of data min/max.
   *    Auto bar width based on min data distance
   *    Auto extents based on domain extent and bar width. I.e. Don't want have of the first bar off the chart.
   *
   *    TWO
   *    Linear axis with scale extents equal to data min/max.
   *    Auto inset for axis so x(1) is not left edge of chart
   *    No axis labels outside of data domain. noAxisLabelsOnExtents.
   *
   *    insets: 'none' 'extend-domain', 'inset-range', {top: 0, right: 0, bottom: 0, left: 0}
   *    justification: centered, right or left
   *
   *    THREE & FOUR
   *    Oridnal axis with ticks centered or left.
   *
   * @param _super
   * @param _config
   * @returns {chartBar}
   * @private
   */
  function _chartBar(_super, _config) {
    // Store the group element here so we can have multiple bar charts in one chart.
    // A second "bar chart" might have a different y-axis, style or orientation.
    var group, series, filteredData, bars, barDimensions, lastDomainMax,
        axes = trait.config.axes( _config),
        access = trait.config.accessorsXY( _config, axes),
        x1 = _super[axes.x](),
        y = _super[axes.y](),
        barCount = _config.barCount,
        dispatch = d3.dispatch('customHover'),
        c = barConfig(_config),
        focusConfig = d3.trait.focus.utils.makeConfig(_config),
        x1IsRangeBand = typeof x1.rangeBand === "function"


    function chartBar(_selection) {
      var self = chartBar

      _selection.each(function(_data) {
        var element = this,
            chartWidth = _super.chartWidth()

        filteredData = _config.seriesFilter ? _data.filter(_config.seriesFilter) : _data

        barDimensions = getBarDimensions(filteredData, access.seriesData, access.x, c, x1, chartWidth)

        if( barDimensions.minRangeMargin || barDimensions.domainExtent ) {

          if( barDimensions.minRangeMargin ) {
            self.minRangeMargin('x1', barDimensions.minRangeMargin)
          } else if( barDimensions.domainExtent ) {
            _super.x1Domain(barDimensions.domainExtent)
          }

          barDimensions = getBarDimensions(filteredData, access.seriesData, access.x, c, x1, chartWidth)
        }

        if( !group ) {
          var classes = _config.chartClass ? "chart-bar " + _config.chartClass : 'chart-bar'
          group = this._chartGroup.append('g').classed(classes, true);
        }

        // DATA JOIN
        series = group.selectAll(".series")
          .data(filteredData)
        {
          // UPDATE

          // ENTER
          series.enter()
            .append("g")
            .attr("class", "series")
            .style("fill", self.color);
        }

        // DATA JOIN
        bars = series.selectAll("rect")
          .data(access.seriesData)

        // ENTER
        bars.enter().append('rect')
          .classed('bar', true)
          .attr(barAttr(access, barDimensions, self.chartHeight(), x1, y))
          .on('mouseover', dispatch.customHover);

        // UPDATE
        bars.transition()
          .duration(0).delay(0).ease(self.ease())
          .attr(barAttr(access, barDimensions, self.chartHeight(), x1, y));

        // EXIT
        bars.exit()
          .transition()
          .style({opacity: 0})
          .remove();

        lastDomainMax = d3.trait.utils.extentMax(x1.domain())

      })
    }

    chartBar.update = function(type, duration) {
      this._super(type, duration)

      // TODO: The x1.range() needs to be wider, so we draw the new line off the right
      // then translate it to the left with a transition animation.

      var domainMax = d3.trait.utils.extentMax(x1.domain())
      var translateX = x1(lastDomainMax) - x1(domainMax)

      // redraw the line and no transform
      series.attr("transform", null)
      bars.attr(barAttr(access, barDimensions, _super.chartHeight(), x1, y));

      bars = series.selectAll("rect")
        .data(access.seriesData)

      // ENTER
      bars.enter().append('rect')
        .classed('bar', true)
        .attr(barAttr(access, barDimensions, _super.chartHeight(), x1, y))

      bars.exit()
        .transition()
        .style({opacity: 0})
        .remove();


      // slide the bars left
      if( duration === 0 ) {
        series.attr("transform", "translate(" + translateX + ")")
      } else {

        series.transition()
          .duration(duration || _super.duration())
          .ease("linear")
          .attr("transform", "translate(" + translateX + ")")
        //.each("end", tick);
      }

      lastDomainMax = d3.trait.utils.extentMax(x1.domain())

      // Could pop the data off the front (off the left side of chart)

      return this;
    };

    chartBar.getFocusItems = function(focusPoint) {
      var foci = this._super(focusPoint),
          myFoci = trait.focus.utils.getFocusItems( filteredData, focusPoint, focusConfig, access, x1, y, chartBar.color, false) // t: isStacked

      foci = foci.concat( myFoci)
      return foci
    }


    d3.rebind(chartBar, dispatch, 'on');
//    _super.onRangeMarginChanged( 'chartBar', chartBar)

    return chartBar;

  }

  trait.chart.bar = _chartBar
  trait.chart.barUtils = {
    barConfig:                                barConfig,
    barAttr:                                  barAttr,
    barOffsetForJustification:                barOffsetForJustification,
    rangeExtentOfBarsAndOuterGapForOneSeries: rangeExtentOfBarsAndOuterGapForOneSeries,
    rangeExtentOfBarsAndOuterGap:             rangeExtentOfBarsAndOuterGap,
    getBarDimensions:                         getBarDimensions
  }

}(d3, d3.trait));

(function(d3, trait) {

  var chartGroupClipPathNextId = 1,
      debug = {
        layoutAxes: false,
        resize: false
      }


  function _chartBase(_super, _config) {


    if( !_config )
      _config = {}

    if( !_config.seriesData )
      _config.seriesData = function(s) { return s }
    if( !_config.seriesLabel )
      _config.seriesLabel = function(s, i) { return "Series " + i }

    if( !_config.x1 )
      _config.x1 = function(d) { return d[0] }
    if( !_config.y1 )
      _config.y1 = function(d) { return d[1] }

    var margin = d3.trait.utils.configMargin(_config.margin, {top: 5, right: 5, bottom: 5, left: 5})

    _config.clip = _config.clip === undefined || _config.clip === true


    // Margin for adjusting the x1-scale range
    // Example: { x1: {left: 5, right: 5} }
    // Without this margin, the outer bars on a bar chart may be half off the chart.
    var minRangeMargins = {}

    /*
     allAxesWithLayoutInfo
     chartBase manages the layout of axes. There may be more than one axis on each
     orientation (Left, Right, Bottom, Top}. As axes are added (via traits), the
     chart margins are adjusted to accommodate each axis.
     Each array element contains the following:
     group: The SVG g element for the axis and axis label
     orient: left, right, top, bottom
     rect: d3.trait.Rect
     */
    var allAxesWithLayoutInfo = []

    var MIN_RANGE_MARGIN_DEFAULT = {left: 0, right: 0, top: 0, bottom: 0}

    function initMinRangeMargin(axis) {
      if( !minRangeMargins[axis] )
        minRangeMargins[axis] = trait.utils.clone(MIN_RANGE_MARGIN_DEFAULT)
    }

    function isMinRangeMargin(axis) {
      if( !minRangeMargins[axis] )
        return false

      var current = minRangeMargins[axis]

      if( d3.trait.utils.isX(axis) )
        return current.left !== 0 || current.right !== 0
      else
        return current.top !== 0 || current.bottom !== 0
    }

    // Whomever needs the largest margins will get their way.
    // This avoids cyclic events (ex: two traits setting 3 then 4 then 3 ...)
    function minRangeMargin(axis, rangeMargin) {
      if( !arguments.length ) return {}

      initMinRangeMargin(axis)

      if( arguments.length === 1 )
        return minRangeMargins[axis]

      if( !rangeMargin )
        return this

      var current = minRangeMargins[axis],
          changed = false;

      if( rangeMargin.left && current.left < rangeMargin.left ) {
        current.left = rangeMargin.left
        changed = true
      }
      if( rangeMargin.right && current.right < rangeMargin.right ) {
        current.right = rangeMargin.right
        changed = true
      }
      if( rangeMargin.top && current.top < rangeMargin.top ) {
        current.top = rangeMargin.top
        changed = true
      }
      if( rangeMargin.bottom && current.bottom < rangeMargin.bottom ) {
        current.bottom = rangeMargin.bottom
        changed = true
      }

      if( changed )
        dispatch.rangeMarginChanged()

      return this;
    }

    if( _config.minRangeMargin ) {
      for( var axis in _config.minRangeMargin ) {
        minRangeMargin(axis, _config.minRangeMargin[ axis])
      }
    }

    var ease = 'cubic-in-out'
    var sizeFromElement = true
    var size = new d3.trait.Size(),
        chartSize = { attrWidth: null, attrHeight: null}
    var width = 200
    var height = 100
    var chartWidth = width - margin.left - margin.right,
        chartHeight = height - margin.top - margin.bottom,
        colorIndexNext = 0,
        colors = d3.scale.category10(),
        colorsUsed = [],
        externalListeners = {}  // subscribption listeners here or on each element.


    var select, duration = 0
    var selection
    var ChartResized = 'chartResized'
    var RangeMarginChanged = 'rangeMarginChanged'
    var dispatch = d3.dispatch(ChartResized, RangeMarginChanged)

    function appendClipPathDef(selected, svgDefs) {
      var pathId = "chart-group-clip-path-" + chartGroupClipPathNextId

      selected._chartGroupClipPath = svgDefs.append("clipPath")
        .attr("id", pathId)
      selected._chartGroupClipPathRect = selected._chartGroupClipPath.append("rect")
        .attr("width", chartWidth)
        .attr("height", chartHeight)

      chartGroupClipPathNextId++
      return pathId
    }

    function mouseOnChart(focusPoint, chartWidth, chartHeight) {
      return  focusPoint.x >= 0 && focusPoint.x <= chartWidth &&
        focusPoint.y >= 0 && focusPoint.y <= chartHeight

    }

    function onMouseMoveListener( element, focusPoint, onChart, sourceInternal, marginLeft, marginTop) {
      var foci
      if( onChart)
        onChartMouseMoveDispatch(element, [focusPoint], sourceInternal)


      foci = onChart ? chartBase.getFocusItems.call(element, focusPoint) : []
      foci.forEach(function(item) {
        item.point.x += margin.left
        item.point.y += margin.top
      })

      if( fociDifferentFromLast(element, foci) )
        onFocusDispatch(element, [foci, focusPoint], sourceInternal)
      element.__onFocusChangeLastFoci = foci

    }

    function onMouseOutListener( element, sourceInternal) {
      onChartMouseOutDispatch(element, sourceInternal)
    }


    function getDimension(sizeFromElement, dimension, elementOffsetDimension) {
      if( !sizeFromElement )
        return dimension
      else
        return elementOffsetDimension;
    }

    function getDimensionAttr(sizeFromElement, dimension, elementOffsetDimension, elementStyleDimension) {
      if( !sizeFromElement )
        return dimension

      if( elementStyleDimension.indexOf('%') >= 0 )
        return elementStyleDimension;
      else
        return elementOffsetDimension;
    }

    function getChartSizeAttrs(element, sizeFromElement, width, height) {
      var attrs = {}

      attrs.width = getDimensionAttr(sizeFromElement, width, element.offsetWidth, element.style.width)
      attrs.height = getDimensionAttr(sizeFromElement, height, element.offsetHeight, element.style.height)
      return attrs
    }

    function getChartSize(element, sizeFromElement, width, height, margin) {
      var size = new d3.trait.Size()

      size.width = getDimension(sizeFromElement, width, element.offsetWidth) - margin.left - margin.right
      size.height = getDimension(sizeFromElement, height, element.offsetHeight) - margin.top - margin.bottom
      return size
    }

    function getSize(element, sizeFromElement, width, height) {
      return new d3.trait.Size(
        getDimension(sizeFromElement, width, element.offsetWidth),
        getDimension(sizeFromElement, height, element.offsetHeight)
      )
    }

    function chartBase(_selection) {
      var self = chartBase
      selection = _selection
      _selection.each(function(_data) {
        var chartSize,
            element = this, // the div element
            sizeAttrs = getChartSizeAttrs(element, sizeFromElement, width, height)

        select = d3.select(element)

        if( !element._svg ) {
          element._svg = d3.select(element)
            .append("svg")
            .classed('chart', true)
            .attr("width", sizeAttrs.width)
            .attr("height", sizeAttrs.height)
          element._svgDefs = element._svg.append("defs")

          size = getSize(element, sizeFromElement, width, height)
          width = size.width
          height = size.height
          chartWidth = size.width - margin.left - margin.right
          chartHeight = size.height - margin.top - margin.bottom

          var clipId = null
          if( _config.clip )
            clipId = appendClipPathDef(element, element._svgDefs)

          // Outer container group for charts, axes, labels, etc.
          element._container = element._svg.append('g').classed('container-group', true)

          // Inner container group for actual chart data paths, rectangles, circles, etc.
          element._chartGroup = element._container.append('g').classed('chart-group', true);

          // Clip all chart innards to chartWidth and chartHeight
          if( _config.clip )
            element._chartGroup.attr("clip-path", "url(#" + clipId + ")")


          this._svg.on("mousemove", function() {
            var foci,
                mousePoint = d3.mouse(element._chartGroup.node()),
                focusPoint = new d3.trait.Point(mousePoint[0], mousePoint[1]),
                onChart = mouseOnChart(focusPoint, chartWidth, chartHeight)
            onMouseMoveListener( element, focusPoint, onChart, true, margin.left, margin.top)
          })

          this._svg.on("mouseout", function() {
            var mousePoint = d3.mouse(element._chartGroup.node()),
                focusPoint = new d3.trait.Point(mousePoint[0], mousePoint[1]),
                onChart = mouseOnChart(focusPoint, chartWidth, chartHeight)
            if( !onChart )
              onMouseOutListener( element, true) // t: sourceInternal
          })

          colorsUsed = []
          _data.forEach(function(d) {
            var i
            if( d.__color__ ) {
              i = colorsUsed.indexOf(d.__color__)
              if( i >= 0 ) {
                delete d.__color__;
              } else {
                colorsUsed.push(d.__color__)
              }
            }
          })
        }

        //console.log( "chartBase w=" + width + ", h=" + height + " cW=" + chartWidth + ", cH=" + chartHeight)

        element._svg.transition()
          .duration(duration)
          .attr({width: width, height: height})
        element._svg.select('.chart-group')
          .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')');

        if( _config.clip )
          element._chartGroupClipPathRect.attr("width", chartWidth).attr("height", chartHeight)

        duration = 500;
      })
    }

    function fociDifferentFromLast(element, current) {
      var last = element.__onFocusChangeLastFoci
      if( !last || last.length !== current.length )
        return true

      var l, c,
          index = last.length - 1

      for( ; index >= 0; index-- ) {
        l = last[index]
        c = current[index]
        if( l.index !== c.index || l.point.x !== c.point.x || l.point.y !== c.point.y )
          return true
      }
      return false
    }

    function onFocusDispatch(element, args, sourceInternal) {
      elementDispatch(element, '__onFocusChangeListeners', args)
      if( sourceInternal)
        elementDispatch(externalListeners, '__onFocusChangeListeners', args)
    }

    function onChartMouseMoveDispatch(element, args, sourceInternal) {
      elementDispatch(element, '__onChartMouseMoveListeners', args)
      if( sourceInternal)
        elementDispatch(externalListeners, '__onChartMouseMoveListeners', args)
    }
    function onChartMouseOutDispatch(element, sourceInternal) {
      elementDispatch(element, '__onChartMouseOutListeners', [])
      if( sourceInternal)
        elementDispatch(externalListeners, '__onChartMouseOutListeners', [])
    }

    // __onFocusChangeListeners
    function elementDispatch(element, whichListeners, args) {
      if( !element[ whichListeners] )
        return
      var listener,
          i = 0,
          listeners = element[whichListeners],
          length = listeners.length
      for( ; i < length; i++ ) {
        listener = listeners[ i]
        listener.apply(element, args)
      }
    }

    /**
     *
     * Subscribe:
     *    subscribe( function(){}, element)
     *    subscribe( function(){})
     *
     * Unsubscribe:
     *    subscribe( function(){}, element, false)
     *    subscribe( function(){}, false)
     * @param name
     * @param fn
     * @param element
     * @param isSubscribe
     * @returns {boolean}
     */
    function subscribe( name, fn, element, isSubscribe) {
      var unsub = element === false || isSubscribe === false

      if( ! fn)
        return false

      var listenerMap = element === undefined || element === false ? externalListeners : element
      if( !listenerMap[name] ) {
        if( unsub)
          return false
        else
          listenerMap[name] = []
      }
      if( unsub) {
        var listeners = listenerMap[name]
        if( listeners ) {
          var index = listeners.indexOf(fn)
          if( index >= 0) {
            listeners.splice( index, 1)
          }
        }
        return false
      } else {
        listenerMap[name].push(fn)
      }
      return true // successful subscribe or unsubscribe
    }

    /**
     * Subscribe to events from another chart and treat them as our own.
     * This is useful when we want the crosshair from another chart to
     * show up in our chart (along with our tooltips).
     *
     * @param source The source chart that we're subscribing to.
     * @param events List of events to subscribe to.
     * @returns {chartBase}
     */
    chartBase.subscribeToEvents = function( source, events) {
      if( ! source)
        return this

      events.forEach( function( event) {
        var eventHandler
        if( event === 'onChartMouseMove') {
          eventHandler = function() {
            var args = arguments // 0 or more arguments
            if( selection )
              selection.each(function(_data) {
                var element = this, // the div element
                    focusPoint = args[0]
                focusPoint.x = Math.min(focusPoint.x, chartWidth)
                focusPoint.y = Math.min(focusPoint.y, chartHeight)
                onMouseMoveListener(element, focusPoint, true, false, margin.left, margin.top)
              })
          }
        } else if( event === 'onChartMouseOut') {
          eventHandler =  function() {
            var args = arguments // 0 or more arguments
            if( selection)
              selection.each(function(_data) {
                var element = this // the div element
                onMouseOutListener(element, false)
              })
            }
        }

        // subscribe to source
        if( eventHandler)
          source[event]( eventHandler)
      })
      return this
    }

    /**
     * Subscribe or unsubscribe to focus change events.
     *
     * Subscribe:
     *    onFocusChange( function(){}, element)
     *    onFocusChange( function(){})
     *
     * Unsubscribe:
     *    onFocusChange( function(){}, element, false)
     *    onFocusChange( function(){}, false)
     *
     *
     * @param fn Function to call. Ex: function( foci, focusPoint)
     * @param element Current element to hang listeners on or isSubscribe=false
     * @param isSubscribe If isSubscribe === false, unsubscribe
     * @return True on success
     */
    chartBase.onFocusChange = function(fn, element, isSubscribe) {
      return subscribe( '__onFocusChangeListeners', fn, element, isSubscribe)
    }
    chartBase.onChartMouseMove = function(fn, element, isSubscribe) {
      return subscribe( '__onChartMouseMoveListeners', fn, element, isSubscribe)
    }

    chartBase.onChartMouseOut = function(fn, element, isSubscribe) {
      return subscribe( '__onChartMouseOutListeners', fn, element, isSubscribe)
    }

    function updateChartSize() {
      var prev = {
        chartWidth:  chartWidth,
        chartHeight: chartHeight
      }
      chartWidth = Math.max( 0, width - margin.left - margin.right)
      chartHeight = Math.max( 0, height - margin.top - margin.bottom)
      if( debug.resize)
        console.log( 'chartBase.updateChartSize() chartWidth ' + prev.chartWidth + '->' + chartWidth + ', chartHeight ' + prev.chartHeight + '->' + chartHeight + ' selection:' + (!!selection))
      if( prev.chartWidth !== chartWidth || prev.chartHeight !== chartHeight ) {
        if( selection )
          chartBase.callTraits(selection)
        dispatch.chartResized()
      }
    }

    function updateSize() {
      var prev = {
        width:  width,
        height: height
      }
      width = chartWidth + margin.left + margin.right
      height = chartHeight + margin.top + margin.bottom
      if( prev.width !== width || prev.height !== height )
        dispatch.chartResized()
    }

    /**
     * Remove everything that was added to element.
     */
    chartBase.remove = function() {
      selection.each(function(_data) {
        var element = this // the div element

        if( element._svg ) {
          element._svg.remove();
          delete element._svg;
          delete element._svgDefs;
          delete element._container;
          delete element._chartGroup;
          delete element._chartGroupClipPath;
          delete element._chartGroupClipPathRect;
          delete element.__onFocusChangeListeners;
          delete element.__onChartMouseOutListeners;
        }
      })

    };

    function findAxisWithLayoutInfo(group) {
      var i, axisWithLayoutInfo,
          length = allAxesWithLayoutInfo.length

      for( i = 0; i < length; i++ ) {
        axisWithLayoutInfo = allAxesWithLayoutInfo[i]
        if( axisWithLayoutInfo.group === group )
          return axisWithLayoutInfo
      }
      return null
    }

    function updateChartMarginForAxis(axes, orient) {
      var axisMargin,
          updatedMargin = false

      if( axes.length <= 0 )
        return updatedMargin

      switch( orient ) {
        case 'left':
          axisMargin = axes[ axes.length - 1].rect.maxX()
          if( margin.left < axisMargin ) {
            margin.left = axisMargin
            updatedMargin = true;
          }
          break;
        case 'right':
          axisMargin = width - axes[0].rect.minX()
          if( margin.right < axisMargin ) {
            margin.right = axisMargin
            updatedMargin = true;
          }
          break;
        case 'top':
          axisMargin = axes[ axes.length - 1].rect.maxY()
          if( margin.top < axisMargin ) {
            margin.top = axisMargin
            updatedMargin = true;
          }
          break;

        case 'bottom':
          axisMargin = height - axes[0].rect.minY()
          if( margin.bottom < axisMargin ) {
            margin.bottom = axisMargin
            updatedMargin = true;
          }
          break;

        default:
      }
      return updatedMargin
    }

    function updateAxesExtentsForChartMarginTop(axes) {
      var updatedOrigin = false
      axes.forEach(function(axis) {
        if( axis.rect.minY() < margin.top ) {
          axis.rect.origin.y = margin.top
          updatedOrigin = true;
        }
      })
      return updatedOrigin
    }

    function updateAxesExtentsForChartMarginLeft(axes) {
      var updatedOrigin = false
      axes.forEach(function(axis) {
        if( axis.rect.minX() < margin.left ) {
          axis.rect.origin.x = margin.left
          updatedOrigin = true;
        }
      })
      return updatedOrigin
    }

    function updateAxesForChartMargin(axes, orient) {
      var updatedOrigin = false,
          edge = 0,
          delta = 0

      if( axes.length <= 0 )
        return updatedOrigin

      switch( orient ) {
        case 'left':
          updatedOrigin = updateAxesExtentsForChartMarginTop(axes)
          // If the chart's left margin is more than the axes width,
          // shift the axes up against the left edge.
          edge = axes[ axes.length - 1].rect.maxX()
          if( edge < margin.left ) {
            delta = margin.left - edge
            axes.forEach(function(axis) {
              axis.rect.origin.x += delta;
            })
            updatedOrigin = true;
          }
          break;
        case 'right':
          // If the chart's right margin is more than the axes width,
          // shift the axes up against the right edge.
          updatedOrigin = updateAxesExtentsForChartMarginTop(axes)
          edge = axes[0].rect.minX()
          if( edge > width - margin.right ) {
            delta = width - margin.right - edge
            axes.forEach(function(axis) {
              axis.rect.origin.x += delta;
            })
            updatedOrigin = true;
          }
          break;
        case 'top':
          // If the chart's top margin is more than the axes height,
          // shift the axes up against the top edge.
          updatedOrigin = updateAxesExtentsForChartMarginLeft(axes)
          edge = axes[ axes.length - 1].rect.maxY()
          if( edge < margin.top ) {
            delta = margin.top - edge
            axes.forEach(function(axis) {
              axis.rect.origin.y += delta;
            })
            updatedOrigin = true;
          }
          break;
        case 'bottom':
          updatedOrigin = updateAxesExtentsForChartMarginLeft(axes)
          // If the chart's bottom margin is more than the axes height,
          // shift the axes up against the bottom edge.
          edge = axes[0].rect.minY()
          if( edge > height - margin.bottom ) {
            delta = height - margin.bottom - edge
            axes.forEach(function(axis) {
              axis.rect.origin.y += delta;
            })
            updatedOrigin = true;
          }
          break;
        default:
      }
      return updatedOrigin
    }

    function makeAxisRectWithProperAnchor(orient, widthOrHeight) {
      // The left axis (for example) is drawn correctly when translated to the left edge
      // of the chart; therefore, the anchor is on the right side of the rect.
      switch( orient ) {
        case 'left':
          return new d3.trait.Rect(0, 0, widthOrHeight, 0, 1, 0);
        case 'right':
          return new d3.trait.Rect(0, 0, widthOrHeight, 0);
        case 'top':
          return new d3.trait.Rect(0, 0, 0, widthOrHeight, 0, 1);
        case 'bottom':
          return new d3.trait.Rect(0, 0, 0, widthOrHeight);
        default:
          return  new d3.trait.Rect();
      }
    }

    function relayoutAxes() {
      var axesWithLayoutInfo, key,
          updatedMargin = false,
          rect = new d3.trait.Rect(0, 0, width, height),
          orients = [ 'left', 'right', 'top', 'bottom'],
          axesByOrient = {}

      orients.forEach(function(orient) {
        axesWithLayoutInfo = allAxesWithLayoutInfo.filter(function(e) {
          return e.orient === orient
        })
        d3.trait.layout.byOrientation(axesWithLayoutInfo, rect, orient)
        updatedMargin = updatedMargin || updateChartMarginForAxis(axesWithLayoutInfo, orient)
        axesByOrient[orient] = axesWithLayoutInfo
      })
      for( key in axesByOrient ) {
        axesWithLayoutInfo = axesByOrient[key]
        updateAxesForChartMargin(axesWithLayoutInfo, key)
      }

      if( updatedMargin )
        updateChartSize()
    }

    chartBase.layoutAxis = function( name, group, orient, widthOrHeight) {
      var axisWithLayoutInfo = findAxisWithLayoutInfo(group),
          rect = makeAxisRectWithProperAnchor(orient, widthOrHeight)

      if( debug.layoutAxes) {
        console.log( 'layoutAxis( '+name+', ' + orient + ', ' + widthOrHeight + ') BEGIN width:' + width + ' height:' + height + ' margin l:' + margin.left + ' r:' + margin.right + ' t:' + margin.top + ' b:' + margin.bottom)
        allAxesWithLayoutInfo.forEach( function(a){
          console.log( '   ' + a.name + ', ' + a.orient + ' origin:'+ a.rect.origin.x + ',' + a.rect.origin.y +' size:' + a.rect.size.width + ',' + a.rect.size.height + ' anchor:' + a.rect.anchor.x + ',' + a.rect.anchor.y)
        })
      }
      if( !axisWithLayoutInfo ) {
        axisWithLayoutInfo = { name: name, group: group, orient: orient, rect: rect}
        allAxesWithLayoutInfo.push(axisWithLayoutInfo)
        relayoutAxes()
      } else if( axisWithLayoutInfo.orient !== orient || axisWithLayoutInfo.rect.size !== rect.size ) {
        axisWithLayoutInfo.orient = orient
        axisWithLayoutInfo.rect = rect
        relayoutAxes()
      }
      if( debug.layoutAxes) {
        console.log( 'layoutAxis( '+name+', ' + orient + ', ' + widthOrHeight + ') END   width:' + width + ' height:' + height + ' margin l:' + margin.left + ' r:' + margin.right + ' t:' + margin.top + ' b:' + margin.bottom)
        allAxesWithLayoutInfo.forEach( function(a){
          console.log( '   ' + a.name + ', ' + a.orient + ' origin:'+ a.rect.origin.x + ',' + a.rect.origin.y +' size:' + a.rect.size.width + ',' + a.rect.size.height + ' anchor:' + a.rect.anchor.x + ',' + a.rect.anchor.y)
        })
      }
      axisWithLayoutInfo.group.attr('transform', 'translate(' + axisWithLayoutInfo.rect.origin.x + ',' + axisWithLayoutInfo.rect.origin.y + ')');
    }

    // Return a list of points in focus.
    chartBase.getFocusItems = function(point) {
      return []
    };

    /**
     *
     * @param type  trend - New date for trend. Slide the new data from the right.
     *              domain - The domain has been updated and all traits need to udpate based on the
     *                      new domain extent (ex: brush event).
     * @param duration
     */
    chartBase.update = function(type, duration) {
    };

    chartBase.select = function() {
      return select;
    };

    function getColor(series) {
      if( series.__color__ )
        return series.__color__

      var i,
          count = 0;
      while( count < 10 ) {
        series.__color__ = colors(colorIndexNext++)
        i = colorsUsed.indexOf(series.__color__)
        if( i < 0 )
          break;
        count++
      }
      colorsUsed.push(series.__color__)
      return series.__color__
    }

    chartBase.color = function(series, _color) {
      switch( arguments.length ) {
        case 1:
          return getColor(series);
        case 3:
          return getColor(series); // d3 attribute call with (series, seriesIndex, array)
        case 2:
          series.__color__ = _color
          return this;
        default:
          return 'black'; // What else to do?
      }
    };

    chartBase.size = function(_s) {
      if( !arguments.length ) return width;
      sizeFromElement = false
      width = parseInt(_s.width, 10);
      height = parseInt(_s.height, 10);
      if( debug.resize)
        console.log( 'chartBase.size( weight=' + width + ', height=' + height + ')')
      updateChartSize()
      return this;
    };
    chartBase.width = function(_x) {
      if( !arguments.length ) return width;
      sizeFromElement = false
      width = parseInt(_x, 10);
      if( debug.resize)
        console.log( 'chartBase.width( ' + width + ')')
      updateChartSize()
      return this;
    };
    chartBase.height = function(_x) {
      if( !arguments.length ) return height;
      sizeFromElement = false
      height = parseInt(_x, 10);
      if( debug.resize)
        console.log( 'chartBase.height( ' + height + ')')
      updateChartSize()
      duration = 0;
      return this;
    };

    chartBase.marginTop = function(_marginTop) {
      if( !arguments.length ) return margin.top;
      margin.top = _marginTop;
      updateChartSize()
      return this;
    };
    chartBase.marginBottom = function(_marginBottom) {
      if( !arguments.length ) return margin.bottom;
      margin.bottom = _marginBottom;
      updateChartSize()
      return this;
    };
    chartBase.marginLeft = function(_marginLeft) {
      if( !arguments.length ) return margin.left;
      margin.left = _marginLeft;
      updateChartSize()

      return this;
    };
    chartBase.marginRight = function(_marginRight) {
      if( !arguments.length ) return margin.right;
      margin.right = _marginRight;
      updateChartSize()
      return this;
    };

    chartBase.plusMarginTop = function(_marginTop) {
      margin.top += parseInt(_marginTop, 10);
      updateChartSize()
      return this;
    };
    chartBase.plusMarginBottom = function(_marginBottom) {
      margin.bottom += parseInt(_marginBottom, 10);
      updateChartSize()
      return this;
    };
    chartBase.plusMarginLeft = function(_marginLeft) {
      margin.left += parseInt(_marginLeft, 10);
      updateChartSize()
      return this;
    };
    chartBase.plusMarginRight = function(_marginRight) {
      margin.right += parseInt(_marginRight, 10);
      updateChartSize()
      return this;
    };

    chartBase.chartRect = function() {
      return new d3.trait.Rect(margin.left, margin.top, chartWidth, chartHeight)
    }

    chartBase.chartWidth = function(_x) {
      if( !arguments.length ) return chartWidth;
      chartWidth = parseInt(_x, 10);
      updateSize()
      return this;
    };
    chartBase.chartHeight = function(_x) {
      if( !arguments.length ) return chartHeight;
      chartHeight = parseInt(_x, 10);
      updateSize()
      return this;
    };

    chartBase.ease = function(_x) {
      if( !arguments.length ) return ease;
      ease = _x;
      return this;
    };

    chartBase.duration = function(_x) {
      if( !arguments.length ) return duration;
      duration = _x;
      return this;
    };

    //d3.rebind(chartBase, dispatch, 'on');

    chartBase.onChartResized = function(namespace, traitInstance) {
      var event = ChartResized
      if( namespace && namespace.length > 0 )
        event = event + "." + namespace
      dispatch.on(event, function() {
        if( selection )
          selection.call(traitInstance)
      })
    }

    chartBase.minRangeMargin = minRangeMargin
    chartBase.isMinRangeMargin = isMinRangeMargin

    chartBase.minRangeMarginLeft = function(axis, marginLeft) {
      if( !arguments.length ) return 0
      if( arguments.length === 1 ) return minRangeMargins[axis] ? minRangeMargins[axis].left : 0
      initMinRangeMargin(axis)
      if( minRangeMargins[axis].left < marginLeft ) {
        minRangeMargins[axis].left = marginLeft
        dispatch.rangeMarginChanged()
      }
      return this;
    }
    chartBase.minRangeMarginRight = function(axis, marginRight) {
      if( !arguments.length ) return 0
      if( arguments.length === 1 ) return minRangeMargins[axis] ? minRangeMargins[axis].right : 0
      initMinRangeMargin(axis)
      if( minRangeMargins[axis].right < marginRight ) {
        minRangeMargins[axis].right = marginRight
        dispatch.rangeMarginChanged()
      }
      return this;
    }
    chartBase.minRangeMarginTop = function(axis, marginTop) {
      if( !arguments.length ) return 0
      if( arguments.length === 1 ) return minRangeMargins[axis] ? minRangeMargins[axis].top : 0
      initMinRangeMargin(axis)
      if( minRangeMargins[axis].top < marginTop ) {
        minRangeMargins[axis].top = marginTop
        dispatch.rangeMarginChanged()
      }
      return this;
    }
    chartBase.minRangeMarginBottom = function(axis, marginBottom) {
      if( !arguments.length ) return 0
      if( arguments.length === 1 ) return minRangeMargins[axis] ? minRangeMargins[axis].bottom : 0
      initMinRangeMargin(axis)
      if( minRangeMargins[axis].bottom < marginBottom ) {
        minRangeMargins[axis].bottom = marginBottom
        dispatch.rangeMarginChanged()
      }
      return this;
    }

    chartBase.onRangeMarginChanged = function(namespace, traitInstance) {
      var event = RangeMarginChanged
      if( namespace && namespace.length > 0 )
        event = event + "." + namespace
      if( traitInstance )
        dispatch.on(event, function() {
          if( selection )
            selection.call(traitInstance)
        })
      else
        dispatch.on(event) // remove
    }

    return chartBase;
  }

//if( ! traits.chart)
//    traits.chart = {}

  trait.chart.base = _chartBase

}(d3, d3.trait));
(function (d3, trait) {

  function _chartLine(_super, _config) {
    // Store the group element here so we can have multiple line charts in one chart.
    // A second "line chart" might have a different y-axis, style or orientation.
    var group, series, filteredData, lastDomainMax,
        axes = trait.config.axes( _config),
        access = trait.config.accessorsXY( _config, axes),
        x1 = _super[axes.x](),
        y = _super[axes.y](),
        focusConfig = d3.trait.focus.utils.makeConfig(_config),
        line = d3.svg.line()
          .interpolate(_config.interpolate || "linear")
          .x(function(d) { return x1(access.x(d)); })
          .y(function(d) { return y(access.y(d)); });

    function chartLine(_selection) {
      var self = chartLine

      _selection.each(function(_data) {

        if( !group ) {
          var classes = _config.chartClass ? "chart-line " + _config.chartClass : 'chart-line'
          group = this._chartGroup.append('g').classed(classes, true);
        }

        filteredData = _config.seriesFilter ? _data.filter(_config.seriesFilter) : _data

        // DATA JOIN
        series = group.selectAll(".series")
          .data(filteredData)

        // UPDATE
        series.selectAll("path")
          .transition()
          .duration(500)
          .attr("d", function(d) {
            return line(getDataInRange(_config.seriesData(d), x1, _config.x1));
          })

        // ENTER
        series.enter()
          .append("g")
          .attr("class", "series")
          .append("path")
          .attr("class", "line")
          .attr("d", function(d) { return line(_config.seriesData(d)); })
          .style("stroke", self.color);

        // EXIT
        series.exit()
          .transition()
          .style({opacity: 0})
          .remove();

        // Leave lastDomainMax == undefined if chart starts with no data.

        if( d3.trait.utils.isData(filteredData, _config.seriesData) )
          lastDomainMax = d3.trait.utils.extentMax(x1.domain())
      })
    }

    function findClosestIndex(data, access, target, direction, minIndex, maxIndex) {

      var index, d

      if( minIndex === undefined )
        minIndex = 0
      if( maxIndex === undefined )
        maxIndex = data.length - 1

      while( minIndex <= maxIndex ) {
        index = Math.floor((minIndex + maxIndex) / 2);
        d = access(data[index]);

        //   t   t
        // 2   4   6   8
        // ^   d    ^
        if( d < target ) {
          minIndex = index + 1;
        } else if( d > target ) {
          maxIndex = index - 1;
        } else {
          return index;
        }
      }

      if( direction < 0 )
        return minIndex + direction < 0 ? 0 : minIndex + direction
      else
        return maxIndex + direction >= data.length ? data.length - 1 : maxIndex + direction
    }

    function getDataInRange(data, scale, access) {
      var domainMin, domainMax,
        indexMin, indexMax,
        endIndex = data.length - 1,
        range = scale.range(),
        rangeMax = d3.trait.utils.extentMax(range)

      domainMin = scale.invert(range[0])
      domainMax = scale.invert(rangeMax)

      indexMin = findClosestIndex(data, access, domainMin, -1)
      indexMax = findClosestIndex(data, access, domainMax, 1, indexMin, endIndex)
      indexMax++ // because slice doesn't include max

      return data.slice(indexMin, indexMax)
    }

    chartLine.update = function(type, duration) {
      this._super(type, duration)

      var dur = duration || _super.duration()
      var attrD = function(d) {
        return line(getDataInRange(_config.seriesData(d), x1, _config.x1));
      }
      lastDomainMax = trait.chart.utils.updatePathWithTrend(type, dur, x1, series, attrD, lastDomainMax)

      // Could pop the data off the front (off the left side of chart)

      return this;
    }

    chartLine.getFocusItems = function(focusPoint) {
      var foci = this._super(focusPoint),
          myFoci = trait.focus.utils.getFocusItems( filteredData, focusPoint, focusConfig, access, x1, y, chartLine.color)

      foci = foci.concat( myFoci)
      return foci
    }

    return chartLine;

  }

trait.chart.line = _chartLine

}(d3, d3.trait));

(function(d3, trait) {

  function barAttr(access, barOffsetX, barW, chartHeight, x1, y) {
    // NOTE: for transition from enter, use  y(0) for y: and height:
    return {
      x:      function(d) { return x1(access.x(d)) + barOffsetX; },
      y:      function(d) { return y(access.y(d)); },
      width:  barW,
      height: function(d) { return chartHeight - y(access.y(d)); }
    }
  }

  function circleAttr(access, x1, y) {
    return {
      cx: function(d) { return x1(access.x(d)) },
      cy: function(d) { return y(access.y(d)) },
      r:  8
    }
  }

  function _chartScatter(_super, _config) {
    // Store the group element here so we can have multiple bar charts in one chart.
    // A second "bar chart" might have a different y-axis, style or orientation.
    var group, series, points, barW, barOffsetX, lastDomainMax,
        axes = trait.config.axes( _config),
        access = trait.config.accessorsXY( _config, axes),
        x1 = _super[axes.x](),
        y = _super[axes.y](),
        shape = "circle" // rect

    var dispatch = d3.dispatch('customHover');

    function chartScatter(_selection) {
      var self = chartScatter

      _selection.each(function(_data) {
        var element = this

        var filtered = _config.seriesFilter ? _data.filter(_config.seriesFilter) : _data

        if( !group ) {
          var classes = _config.chartClass ? "chart-scatter " + _config.chartClass : 'chart-scatter'
          group = this._chartGroup.append('g').classed(classes, true);
        }

        // DATA JOIN
        series = group.selectAll(".series")
          .data(filtered)
        {
          // UPDATE

          // ENTER
          series.enter()
            .append("g")
            .attr("class", "series")
            .style("fill", self.color);
        }

        // DATA JOIN
        points = series.selectAll(shape)
          .data(access.seriesData)
        {
          // UPDATE
          points.transition()
            .duration(500).delay(500).ease(self.ease())
            .attr(circleAttr(access, x1, y));

          // ENTER
          points.enter().append(shape)
            .classed('scatter-point', true)
            .attr(circleAttr(access, x1, y))
            //.on('mouseover', dispatch.customHover);
            .on("mouseover", function(d, i) {
              return element._svg.append("text").text("data: " + access.y(d).toFixed(1))
                .attr("id", "tooltip")
                .attr("x", x1(access.x(d)) + 10)
                .attr("y", y(access.y(d)))
                .attr("fill", "black")
                .attr("opacity", 0)
                .style("font-family", "sans-serif")
                .transition().attr("opacity", 1);
            })
            .on("mouseout", function() {
              d3.select(this)
                .transition()
                .duration(500)
                .attr("fill", "slateblue");
              d3.selectAll("#tooltip")
                .transition().duration(500)
                .attr("opacity", 0)
                .remove();
            })

          // EXIT
          points.exit()
            .transition()
            .style({opacity: 0})
            .remove();

          lastDomainMax = d3.trait.utils.extentMax(x1.domain())
        }

      })
    }

    chartScatter.update = function(type, duration) {
      this._super(type, duration)

      // TODO: The x1.range() needs to be wider, so we draw the new line off the right
      // then translate it to the left with a transition animation.

      var domainMax = d3.trait.utils.extentMax(x1.domain())
      var translateX = x1(lastDomainMax) - x1(domainMax)

      // redraw the line and no transform
      series.attr("transform", null)
      points.attr(barAttr(access, barOffsetX, barW, _super.chartHeight(), x1, y));

      points = series.selectAll("rect")
        .data(access.seriesData)

      // ENTER
      points.enter().append('rect')
        .classed('bar', true)
        .attr(barAttr(access, barOffsetX, barW, _super.chartHeight(), x1, y))

      points.exit()
        .transition()
        .style({opacity: 0})
        .remove();


      // slide the bars left
      if( duration === 0 ) {
        series.attr("transform", "translate(" + translateX + ")")
      } else {

        series.transition()
          .duration(duration || _super.duration())
          .ease("linear")
          .attr("transform", "translate(" + translateX + ")")
        //.each("end", tick);
      }

      lastDomainMax = d3.trait.utils.extentMax(x1.domain())

      // Could pop the data off the front (off the left side of chart)

      return this;
    };

    d3.rebind(chartScatter, dispatch, 'on');
    _super.onRangeMarginChanged('chartScatter', chartScatter)

    return chartScatter;

  }

  trait.chart.scatter = _chartScatter

}(d3, d3.trait));

(function(d3, trait) {


  /**
   *
   * d3.trait( d3.trait.control.brush, { axis: 'x1', target: obj, targetAxis: 'x1'})
   * @param _super
   * @param _config
   * @returns {Function}
   * @private
   */
  function _controlBrush(_super, _config) {
    // Store the group element here so we can have multiple line charts in one chart.
    // A second "line chart" might have a different y-axis, style or orientation.
    var group, lastDomainMax,
        //        x1 = _super.x1(),
        //        y1 = _super.y1(),
        name = _config.axis,
        axisChar = name.charAt(0),
        scale = _super[name](),        // ex: x1()
        target = _config.target,
        targetAxis = _config.targetAxis,
        targetScale = target[_config.targetAxis](),
        brush = d3.svg.brush()[axisChar](scale)

    function brushed() {
      var extent = brush.empty() ? scale.domain() : brush.extent()
      target[ targetAxis + "Domain"](extent)
      //targetScale.domain( extent);
      target.update("domain", 0)
//        focus.select("path").attr("d", area);
//        focus.select(".x.axis").call(xAxis);
    }

    brush.on("brush", brushed)

    var dispatch = d3.dispatch('customHover');

    function controlBrush(_selection) {
      var self = controlBrush

      _selection.each(function(_data) {
        var element = this

        if( !group ) {
          var brushClasses = "brush brush-" + name
          var classes = _config.chartClass ? brushClasses + _config.chartClass : brushClasses
          //brushChart = this._chartGroup.lastChild
          group = this._chartGroup.append('g').classed(classes, true)
        }

        group.call( brush)

        group.selectAll("rect")
          .attr("y", -6)
          .attr("height", self.chartHeight() + 7);

        lastDomainMax = d3.trait.utils.extentMax(scale.domain())
      })
    }

    controlBrush.update = function(type, duration) {
      this._super(type, duration)
      group.call( brush)
      lastDomainMax = d3.trait.utils.extentMax(scale.domain())
      return this;
    };

    d3.rebind(controlBrush, dispatch, 'on');

    return controlBrush;

  }

  trait.control.brush = _controlBrush

}(d3, d3.trait));

(function(d3, trait) {

  /**
   * Tooltip will call focus super. Any charts traits can return a list of items that need crosshairs.
   * For example a line chart with two series can return two times.
   *
   * Configure
   *  axis: 'x1', 'y1', ['x1', 'y1']
   */
  function _crosshair(_super, _config, _id) {

    var group, series, lastX,
        line = d3.svg.line(),
        crosshairs = [],
        axis = _config.axis || 'x1'

    function removeCrosshair() {
      if( group ) {
        group.remove()
        group = undefined
      }
    }

    // If focusPoint, then mouse move, else mouse out.
    //
    function chartMouseMove( focusPoint) {
      var chartHeight = _super.chartHeight(),
          marginLeft = _super.marginLeft(),
          marginTop = _super.marginTop()

      if( focusPoint && focusPoint.x === lastX)
        return

      if( focusPoint) {

        if( crosshairs.length === 0)
          crosshairs[0] = []
        var crosshairX = crosshairs[0]

        crosshairX[0] = [focusPoint.x + marginLeft, marginTop]
        crosshairX[1] = [focusPoint.x + marginLeft, chartHeight + marginTop]

        lastX = focusPoint.x

      } else {

        if( crosshairs.length > 0)
          crosshairs.splice(0, 1)
        lastX =  undefined
      }

      // DATA JOIN
      series = group.selectAll(".crosshair")
        .data(crosshairs)

      // ENTER
      series.enter()
        .append("g")
        .attr("class", "crosshair")
        .append("path")
        .attr({
          'class': "line",
          'd': function(d) { return line(d); }
        })

      // UPDATE
      series.selectAll("path")
        .attr('d', function(d) {
          return line(d);
        })

      // EXIT
      series.exit()
        .style({opacity: 0})
        .remove();

    }



    function crosshair(_selection) {
      var self = crosshair

      _selection.each(function(_data) {
        var element = this

        if( ! group) {
          group = element._container.append('g')
            .attr({
              'class':      'crosshair-group'
            });

          self.onChartMouseMove( chartMouseMove, element)
          self.onChartMouseOut( chartMouseMove, element)
        }
      })
    }

    return crosshair;

  }

  trait.focus.crosshair = _crosshair

}(d3, d3.trait));

(function(d3, trait) {

  function l(x, y) { return 'l' + x + ',' + y }

  function q(x1, y1, x, y) { return 'q' + x1 + ',' + y1 + ' ' + x + ',' + y }

  function m(x, y) { return 'm' + x + ',' + y }

  // // call-out half size (half width and half height)
  function getCalloutPointerHalfHeight(h) {
    return Math.max(h * 0.16, 6)
  }

  /**
   * Return a path for a call-out tooltip (has a little pointer pointing to data).
   * @param w Width
   * @param h Height
   * @param r Radius for rounded corners
   * @param cp2  Half of the call-out point's width and height
   * @param anchor  Distinguishes left or right.
   * @param offsetY  Nudge the call-out point up or down to point to original focus (before layout adjustments)
   * @returns {string}
   */
  function getCalloutPath(w, h, r, cp2, anchor, offsetY) {
    // Start at the left on the callout point and go clockwise
    //
    //<path d="m10,0 L90,0 Q100,0 100,10 L100,90" fill="none" stroke="red"/>
    //
    var dx = anchor.x < 0.5 ? 1 : -1,
        ht = Math.floor(h / 2 - cp2 - r),// h top (i.e. callout to radius)
        ih = ht * 2 + cp2 * 2,// inner height (i.e. height - radii)
        iw = Math.floor(w - r - r), // inner width
        p = m(0, 0 + offsetY) +
          l(dx * cp2 * 2, -cp2 - offsetY) +
          l(0, -ht) +
          q(0, -r, dx * r, -r) +  // top-left corner
          l(dx * iw, 0) +
          q(dx * r, 0, dx * r, r) + // top-right corner
          l(0, ih) +
          q(0, r, dx * -r, r) + // bottom-right corner
          l(dx * -iw, 0) +
          q(dx * -r, 0, dx * -r, -r) + // bottom-left corner
          l(0, -ht) +
          'z'  // close path

    return p
  }

  var formatDate = d3.time.format("%Y-%m-%d");

  function markTooltipsForRemoval(tooltips) {
    tooltips.forEach(function(item) {
      if( item )
        item.remove = true
    })
  }

  function removeUnusedTooltips(tooltips) {
    tooltips.forEach(function(item, index) {
      if( item && item.remove ) {
        item.group.remove()
        tooltips[index] = null
      }
    })
  }

  function removeAllTooltips(cache) {

    delete cache.lastFoci;

    if( !cache.tooltips )
      return
    cache.tooltips.forEach(function(item, index) {
      if( item ) {
        item.group.remove()
        cache.tooltips[index] = null
      }
    })
  }

  /**
   * Tooltip will call focus super. Any charts traits can return a list of items that need tooltips.
   * For example a line chart with two series can return two times.
   *
   * Configure
   *  formatY -- d3.format function.
   *  transitionDuration — In milliseconds
   *

   */
  function _tooltipDiscrete(_super, _config, _id) {

    var axis = _config.axis,
        transitionDuration = trait.utils.configFloat( _config.transitionDuration, 100),
        radius = 6,
        margin = 3,
        formatX = _config.formatX || formatDate

    function tooltipDiscrete(_selection) {
      var self = tooltipDiscrete

      _selection.each(function(_data) {
        if( _data.length === 0)
          return

        var element = this
        var cache = trait.utils.getTraitCache(element, _id)

        cache.tooltips = _data.map(function(d) { return null})

        function focusChange(foci, focusPoint) {

          if( foci.length <= 0 ) {
            removeAllTooltips(cache)
            return
          }

          if( _data.length <= 0) {
            console.error( 'tooltipDiscrete.focusChange foci.length=' + foci.length + ' but _data.length=' + _data.length)
            return
          }

          var anchorMidY = new trait.Point(0, 0.5)

          markTooltipsForRemoval(cache.tooltips)

          // TODO: Can this huge function be broken up a bit?
          foci.forEach(function(item, index, array) {
            //console.log( "foci: " + item.point.x + " distance: " + item.distance)

            var formattedText,
                seriesIndex = _data.indexOf(item.series),
                ttip = seriesIndex >= 0 ? cache.tooltips[ seriesIndex] : null,
                xValue = formatX(_config.x1(item.item)),
                yValue = _config.y1(item.item)

            if( seriesIndex < 0) {
              console.error( 'tooltipDiscrete.focusChange forci.forEach seriesIndex=' + seriesIndex + ' but should have found a series.')
              return
            }

            if( _config.formatY )
              yValue = _config.formatY(yValue)
            formattedText = xValue + " " + yValue

            if( !ttip ) {
              ttip = { newby: true}

              ttip.group = element._container.append('g')
                .attr({
                  'id':      'tooltip',
                  'opacity': 0
                });

              ttip.path = ttip.group.append('path')
                .attr('fill', 'darkgray')

              ttip.text = ttip.group.append('text')
                .attr({
                  'font-family':    'monospace',
                  'font-size':      10,
                  'fill':           'black',
                  'text-rendering': 'geometric-precision'
                })

              cache.tooltips[ seriesIndex] = ttip
            }

            ttip.remove = false

            ttip.text.text(formattedText);
            var bbox = ttip.text.node().getBBox()
            bbox.height = bbox.height * 2 + margin * 2
            bbox.width += margin * 2 + getCalloutPointerHalfHeight(bbox.height)
            item.rect = new trait.Rect(item.point, bbox, anchorMidY)
          })

          trait.layout.verticalAnchorLeftRight(foci, self.chartRect())

          foci.forEach(function(item, index, array) {
            var seriesIndex = _data.indexOf(item.series),
                ttip = cache.tooltips[ seriesIndex]

            //var pathFill = d3.rgb( item.color ).brighter().toString()
            var pathStroke = d3.rgb(item.color).darker(1.4).toString()

            //console.log( "bbox: " + bbox.width + ", " + bbox.height)
            var offsetY = item.point.y - item.rect.origin.y
            var calloutPointerHalfHeight = getCalloutPointerHalfHeight(item.rect.size.height)
            var calloutPath = getCalloutPath(item.rect.size.width, item.rect.size.height, radius, calloutPointerHalfHeight, item.rect.anchor, offsetY)

            var textMargin = calloutPointerHalfHeight * 2 + margin + radius,
                tx = item.rect.anchor.x < 0.5 ? textMargin : -item.rect.size.width - margin - radius

            ttip.text.attr('transform', 'translate(' + tx + ',' + 0 + ')')

            if( ttip.newby ) {
              ttip.group.attr('transform', 'translate(' + item.rect.origin.x + ',' + item.rect.origin.y + ')')
              ttip.group.transition().attr('opacity', 1);
            } else {
              ttip.group.transition().attr({
                'opacity':   1,
                'transform': 'translate(' + item.rect.origin.x + ',' + item.rect.origin.y + ')'
              })
            }

            ttip.path.transition().duration(transitionDuration)
              .attr({
                'opacity': 0.72,
                'fill':    item.color,
                'stroke':  pathStroke,
                'd':       calloutPath
              })
            ttip.newby = false
          })

          removeUnusedTooltips(cache.tooltips)
          cache.lastFoci = foci

        }

        self.onChartMouseOut( function() { removeAllTooltips(cache) }, element)

        self.onFocusChange( focusChange, element)

      })

    }

    return tooltipDiscrete;

  }

  // TODO: The seriesFilter can't used series index because we don't have it. It can filter on a series attribute.
  function getFilteredFoci( foci, seriesFilter) {
    if( ! seriesFilter)
      return foci

    var filtered = foci.filter( function( focus, index, array) {
      return seriesFilter( focus.series)
    })
    return filtered
  }


  var ColY = 0,
      ColLabel = 1
  var AnchorBottomLeft = new trait.Point(0, 1),
      AnchorBottomRight = new trait.Point(1, 1),
      AnchorBottomCenter = new trait.Point(0.5, 1),
      AnchorTopLeft = new trait.Point(0,0),
      AnchorMiddleLeft = new trait.Point(0,0.5),
      AnchorMiddle = new trait.Point(0.5,0.5)


  /**
   *
   *
   * row = {
   *    elements: [
   *    ],
   *    group:
   *
   *      y: {
   *        value,
   *        text,
   *        bbox,
   *        element
   *      },
   *      label: {
   *        text,
   *        bbox,
   *        element,
   *      },
   *      group  -- group element for line
   *    },
   * }
   *
   * o 134.00 Grid  seriesMarker, seriesValue, seriesLabel
   * o   1.00 ESS
   *
   * @param element
   * @param focus
   * @param config
   */

  function textAlignLRL( node, depth, row, col) {
    return col === 0 ? 'left'
      : col === 1 ? 'right'
      : 'left'
  }
  function textAlignRLL( node, depth, row, col) {
    return row === 0 ? 'left'
      : col === 0 ? 'right'
      : col === 1 ? 'left'
      : 'left'
  }

  function FocusTable( config, group) {
    this.config = config
    this.group = group
    this.rect = new trait.Rect( 0, 0, 0, 0, 0, 0.5) // y anchor in middle
    this.children = []
    this.dirtyRows = []
    this.radius = 2

    this.element = group.append('rect')   // for background
      .attr({
        x:  0,
        y:  0,
        rx: this.radius,
        ry: this.radius
      })
//      .style({
//        fill: 'white',
//        stroke: 'gray',
//        'stroke-width': 1,
//        opacity: 0.9
//      })
  }

  FocusTable.prototype.addRow   = function( focus) {

    var cols = [
      {
        anchor: AnchorBottomRight,
        accessValue: function( f, config) {return config.y1( f.item)},
        format: this.config.formatY
      },
      {
        anchor: AnchorBottomLeft,
        accessValue: function( f, config) { return config.seriesLabel( f.series)}
      }
    ]

    var row = new FocusTableRow( this, focus, cols)
    this.children.push( row)
    // the new FocusTableRow will call table.rowDirty.

    return row
  }

  FocusTable.prototype.getRow = function( index) { return this.children[ index]}
  FocusTable.prototype.rowCount = function() { return this.children.length }
  FocusTable.prototype.rowDirty = function( row) { this.dirtyRows.push( row) }
  FocusTable.prototype.truncateRows = function( rowCount) {
    // TODO: We have less focus items. Remove excess rows.
  }

  // TODO: How about
  //   var row = table.addRow( cols)
  //   row.setValue( focus) or row.update( focus)
  //
  FocusTable.prototype.setHeaderRow = function( focus) {

    if( ! this.header) {

      var cols = [
        {
          colspan: 2,
          anchor: AnchorBottomLeft,
          accessValue: function( f, config) {return config.x1( f.item)},
          format: this.config.formatHeader
        }
      ]

      this.header = new FocusTableRow( this, focus, cols)
      this.children.unshift( this.header)
      // the new FocusTableRow will call table.rowDirty.
    } else {
      this.header.setFocus( focus)
    }

    return this.header
  }

  FocusTable.prototype.render = function( layout, focusPoint, chartRect) {

    // TODO: use dirtyRows.
    if( this.dirtyRows.length === 0)
      return

    // TODO: what can we do to optimize dirtyRows?
    layout( this)
    this.setOrigin( focusPoint, chartRect)

    this.group.attr('transform', 'translate(' + this.rect.minX() + ',' + this.rect.minY() + ')')

    this.renderTableBox()
    this.children.forEach( function( row, rowIndex) {
      row.render()
    })

    this.dirtyRows = []
  }

  FocusTable.prototype.setOrigin = function( focusPoint, chartRect) {
    var  offsetX = 8,
         x = focusPoint.x + chartRect.origin.x,
         y = focusPoint.y + chartRect.origin.y
    this.rect.origin.x = x
    this.rect.origin.y = y

    // Fit the tooltip to the left or right of the vertical line.
    // Use an offset so the box is sitting a little away from the
    // vertical crosshair.
    //
    this.rect.size.width += offsetX
    trait.layout.verticalAnchorLeftRight( [this], chartRect)
    this.rect.size.width -= offsetX
    if( this.rect.anchor.x < 0.5)
      this.rect.origin.x += offsetX
    else
      this.rect.origin.x -= offsetX

  }

  FocusTable.prototype.renderTableBox = function() {
    this.element.attr({
      width:  this.rect.size.width,
      height: this.rect.size.height + 2
    })
    this.group.transition().style('opacity', 1)
  }


  function FocusTableRow( focusTable, focus, cols) {
    this.focusTable = focusTable
    this.focus = focus
    this.rect = new trait.Rect()
//    this.children = [{}, {}]
    this.children = cols
    this.config = focusTable.config

    this.item = focus.item
//    this.value = this.config.y1( this.item)
//    this.label = this.config.seriesLabel( focus.series)

    this.group = this.focusTable.group.append('g')
      .attr({
        'class':      'd3-trait-tooltip-row'
      })

    this.setFocus( focus)

//    this.children.forEach( function( col) {
//      col.element = this.group.append('text')
//
//      if( col.anchor == AnchorRight)
//        col.element.style('text-anchor', 'end')
//
//      var value = col.accessValue( focus, this.config)
//      this.setCol( col, value, col.anchor, col.format)
//    })


//    var colY = this.children[ColY],
//        colLabel = this.children[ColLabel]
//
//    colY.element = this.group.append('text')
//      .style({
//        'text-anchor': 'end'  // right justified.
//      })
//    colLabel.element = this.group.append('text')
//
//    this.setCol( ColY, this.value, AnchorRight, this.config.formatY)
//    this.setCol( ColLabel, this.label, AnchorLeft)
  }

  FocusTableRow.prototype.setFocus = function( focus) {
    var self = this,
        rectChanged = false

    self.focus = focus
    self.item = focus.item
//    this.value = this.config.y1( this.item)
//    this.label = this.config.seriesLabel( focus.series)

    self.children.forEach( function( col) {
      if( ! col.element) {
        col.element = self.group.append('text')

        if( col.anchor === AnchorBottomRight)
          col.element.style('text-anchor', 'end')
      }

      rectChanged = rectChanged || self.setCol( col, col.accessValue, col.anchor, col.format)
    })

//    rectChanged = rectChanged || this.setCol( ColY, this.value, AnchorRight, this.config.formatY)
//    rectChanged = rectChanged || this.setCol( ColLabel, this.label, AnchorLeft)

    if( rectChanged)
      self.focusTable.rowDirty( self)
    return rectChanged
  }

  FocusTableRow.prototype.setCol = function( col, accessValue, anchor, format) {
    var value = col.accessValue( this.focus, this.config)

    if( col.value && col.value === value && !col.rect)
      console.error( 'how did we get here!')
    if( col.value && col.value === value)
      return false // no change

    col.value = value
    col.text = format ? format(value) : value

    col.element.text(col.text)
    col.bbox = col.element.node().getBBox()
    col.rect = new trait.Rect( 0, 0, col.bbox.width, col.bbox.height, anchor.x, anchor.y)

    return true // rect changed
  }

  FocusTableRow.prototype.render = function() {
    var origin = this.rect.origin
    this.group.attr('transform', 'translate(' + this.rect.minX() + ',' + this.rect.minY() + ')')

    this.children.forEach( function( col, colIndex) {
      origin = col.rect.origin
      col.element.attr('transform', 'translate(' + origin.x + ',' + origin.y + ')')
    })
  }

  function formatNull( d) { return d}

  var defaultConfig = {
    transition: {
      duration: 100
    },
    radius: 3,
    paddingEm: new trait.Margin( 0, 0.6, 0.25),  // top, left/right bottom
    offsetX: 8,
    em: undefined,
    formatHeader: formatNull,
    targets: undefined // object or array of objects
  }
  /**
   * Tooltip will call focus super. Any charts traits can return a list of items that need tooltips.
   * For example a line chart with two series can return two times.
   *
   * o Value Label
   *
   * Configure
   *  formatY -- d3.format function.
   *  transitionDuration — In milliseconds
   *
   */
  function _tooltipUnified(_super, _config, _id) {

    var group, table,
        axis = _config.axis,
        transitionDuration = trait.utils.configFloat( _config.transitionDuration, 100),
        radius = trait.utils.configFloat( _config.radius, 3),
        paddingEm = _config.padding || new trait.Margin( 0, 0.6, 0.25),  // top, left/right bottom
//        paddingEm = _config.padding || new trait.Margin( 0, 0, 0),  // top, left/right bottom
        offsetX = 8,
        emDefault= 10,  // size of em space
        formatHeader = _config.formatHeader || formatNull,
        targets = _config.target

    if( targets && ! Array.isArray( targets))
      targets = [targets]

    var layout = trait.layout.tilestack()
      .paddingEm( paddingEm)
      .translate( function( node, x, y) { node.group.attr('transform', 'translate(' + x + ',' + y + ')' )})

    var box,
        klass = { main: _config['class'] || 'd3-trait-tooltip'}
    klass.box = klass.main + '-box'



    function valueY( f) {
      var v = _config.y1( f.item)
      if( _config.formatY)
        v = _config.formatY( v)
      return v
    }

    // [ Header         ]
    // circle value label
    // circle value label
    // circle value label
    //

    table = {
      group: undefined,
      klass: klass.main + '-table',
      layoutChildren: 'TopToBottom',
      em: emDefault, // TODO: update em at all depths to max em of children.
      children: [
        { // Header
          group: undefined,
          klass: klass.main + '-header',
          limit: 1,  // one header for all foci.
          anchor: AnchorTopLeft,
          layoutChildren: 'LeftToRight',
          em: emDefault,
          children: [  // columns
            {
              type: 'text',
              group: undefined,
              klass: klass.main + '-header-td',
              anchor: AnchorBottomLeft,
              textAnchor: 'start',
              accessValue: function( f) { return formatHeader( _config.x1( f.item)) },
              em: emDefault
            }
          ]
        },
        { // Body
          group: undefined,
          klass: klass.main + '-body',
          anchor: AnchorTopLeft,
          layoutChildren: 'LeftToRight',
          em: emDefault,
          children: [  // columns
            {
              type: 'mark',
              group: undefined,
              klass: klass.main + '-mark',
              anchor: AnchorBottomRight,
              textAnchor: 'end',
              accessValue: valueY,
              em: emDefault
            },
            {
              type: 'text',
              group: undefined,
              klass: klass.main + '-value',
              anchor: AnchorBottomRight,
              textAnchor: 'end',
              accessValue: valueY,
              em: emDefault
            },
            {
              type: 'text',
              group: undefined,
              klass: klass.main + '-label',
              anchor: AnchorBottomLeft,
              textAnchor: 'start',
              accessValue: function( f) { return _config.seriesLabel( f.series)},
              em: emDefault
            }
          ]
        }
      ]
    }

    function setOrigin( obj, focusPoint, chartRect) {
      var  offsetX = 8,
           x = focusPoint.x + chartRect.origin.x,
           y = focusPoint.y + chartRect.origin.y
      obj.rect.origin.x = x
      obj.rect.origin.y = y

      // Fit the tooltip to the left or right of the vertical line.
      // Use an offset so the box is sitting a little away from the
      // vertical crosshair.
      //
      obj.rect.size.width += offsetX
      trait.layout.verticalAnchorLeftRight( [obj], chartRect)
      obj.rect.size.width -= offsetX
      if( obj.rect.anchor.x < 0.5)
        obj.rect.origin.x += offsetX
      else
        obj.rect.origin.x -= offsetX

    }

    function getSelectionRect( selection, anchor) {
      var bbox = selection[0][0].getBBox()
      var x = anchor ? anchor.x * bbox.width : 0,
          y = 0 // anchor ? anchor.y * bbox.height : 0
      return new trait.Rect(
        x, y,
        bbox.width, bbox.height,
        anchor ? anchor.x : 0,
        0 //anchor ? anchor.y : 0
      )
    }


    function yEmWithPadding( d, i) {
      // The first line is y = 1em because character y is the baseline.
      var p = i === 0 ? paddingEm.top + 1 : paddingEm.top + (paddingEm.top + paddingEm.bottom + 1) * i + 1
      return p + 'em'
    }
    function yEmWithPaddingCircle( d, i) {
      // The first line is y = 1em because character y is the baseline.
      var p = i === 0 ? paddingEm.top + 0.64 : paddingEm.top + (paddingEm.top + paddingEm.bottom + 1) * i + 0.64
      return p + 'em'
    }

    function calculateEm( col, fociLength) {
      var heightEm = 0,
          padBottom = 0,
          size = col.rect.size

      if( fociLength > 0) {
        if( fociLength === 1)
          // h = (1+.t)*em
          heightEm = size.height / ( 1+ paddingEm.top)
        else
          // h = (1+.t)*em + (1+.t+.b)*em * (length-1)
          // h = {(1+.t) + (1+.t+.b) * (length-1)} * em
          // em = h / {(1+.t) + (1+.t+.b) * (length-1)}
          heightEm = size.height / (paddingEm.top + 1 + (paddingEm.top + paddingEm.bottom + 1) * (fociLength-1))
        padBottom = heightEm * paddingEm.bottom
        size.height += padBottom
      }
      return heightEm
    }

    function textColumnEnter( col, foci) {
      var li

      li = col.group.selectAll('text')
        .data(foci)

      li.enter()
        .append('text')
        .attr('y', yEmWithPadding)
        .attr('x',0)
        .style('text-anchor', col.textAnchor)
        .text( col.accessValue )

      // UPDATE
      li.text( col.accessValue )

      li.exit()
        .remove()

      col.rect = getSelectionRect( col.group, col.anchor)
      col.em = calculateEm( col, foci.length)

//      return foci.length === 0 ? 0 : size.height / foci.length
      return col.em
    }
    function markColumnEnter( col, foci, color) {
      var li

      li = col.group.selectAll('circle')
        .data(foci)

      li.enter()
        .append('circle')
        .attr('cy', yEmWithPaddingCircle)
        .attr('cx', '0.5em')
        .attr('r','0.4em')
        .style('fill', function( f) { return color(f.series)})

      // UPDATE
      li.style('fill', function( f) { return color(f.series)})

      li.exit()
        .remove()

      col.rect = getSelectionRect( col.group, col.anchor)
      col.em = calculateEm( col, foci.length)

      return col.em
    }

    function sectionEnter( section, foci, color) {
      var markCol,
          em = 10

      if( section.limit)
        foci = foci.slice( 0, section.limit)

      section.children.forEach( function( col) {
        if( col.type === 'text')
          em = textColumnEnter( col, foci)
        else
          markCol = col
      })

      if( markCol)
        markColumnEnter( markCol, foci, color)

    }

//    function layoutColsHorizontal( section) {
//
//      var x = 0
//      section.children.forEach( function( col) {
//        x += padding.left * col.em
//        if( col.rect.anchor.x < 0.5) {
//          col.rect.origin.x = x
//          col.group.attr('transform', 'translate(' + col.rect.minX() + ',' + col.rect.maxY() + ')')
//          x += col.rect.size.width
//        } else {
//          x += col.rect.size.width
//          col.rect.origin.x = x
//          col.group.attr('transform', 'translate(' + col.rect.maxX() + ',' + col.rect.maxY() + ')')
//        }
//        x += padding.right * col.em
//      })
//    }

    function focusChange( foci, focusPoint, color) {

      var filteredFoci,
          headerFoci,
          headerHeight

      // Reverse so stack area charts have the series in the correct visual order.
      foci = foci.reverse()
      filteredFoci = getFilteredFoci( foci, _config.seriesFilter)
      table.children.forEach( function( child) {
        sectionEnter( child, filteredFoci, color)
      })

      layout( table)

      setOrigin( table, focusPoint, _super.chartRect())
      group.attr('transform', 'translate(' + table.rect.minX() + ',' + table.rect.minY() + ')')

      box.attr({
        x: 0.5,
        y: Math.round( table.em * -0.25) + 0.5,
        rx: radius,
        ry: radius,
        width:  Math.round( table.rect.size.width + table.em * 0.1),
        height: Math.round( table.rect.size.height + table.em * 0.25)
      })

      group.style('opacity', 1)


//      if( filteredFoci.length <= 0 ) {
//        // TODO: Don't truncate here. Hide the whole tooltip. When we come back we'll reuse the rows.
//        table.truncateRows(0)
//        return
//      }
//
//      // TODO: change to addRow and take col args. Same for addRow below.
//      table.setHeaderRow( filteredFoci[0])
//
//      // rowCount            1 2 1 2
//      // table.rowCount      1 2 3 3
//      // table.rowCount - 1  0 1 2 2
//      // t.rC - 1 < rowCount T T F F
//      //                     + + s s
//      var rowCount = 1
//      filteredFoci.forEach(function( focus, index, array) {
//        if( table.rowCount() - 1 < rowCount)
//          table.addRow( focus)
//        else
//          table.getRow( rowCount).setFocus( focus)
//        rowCount ++
//      })
//      table.truncateRows( rowCount)
//
//
//      // o 134.00 Grid
//      // o   1.00 ESS
//      if( ! layout) {
//        layout = d3.trait.layout.table()
//          .padding( padding)
//          .textAlign( textAlignRLL)
//      }
//      table.render( layout, focusPoint, _super.chartRect())
    }


    function makeChildGroups( parent) {

      parent.children.forEach( function( child) {

        child.group = group.append('g')
          .classed(child.klass,true)
        child.children.forEach( function( col) {
          col.group = child.group.append('g')
            .classed(col.klass,true)
            .attr('transform', 'translate(0,0)')
        })
      })

    }

    function updateTargets( foci, focusPoint) {
      if( ! targets)
        return

      targets.forEach( function(target) {
        target.update( 'focus', foci, focusPoint)
      })
    }

    function tooltipUnified(_selection) {
      var self = tooltipUnified

      function focusChangeListener( foci, focusPoint){
        focusChange( foci, focusPoint, self.color)
//            updateTargets( foci, focusPoint)
      }
      function chartMouseOutListener() {
        if( group)
          group.transition().duration(100).style('opacity', 0)
//            updateTargets( [])
      }

      _selection.each(function(_data) {
        var element = this

        if( ! group) {
          group = element._container.append('g')
            .attr({
              'class':      'd3-trait-tooltip'
            });
          box = group.append('rect')
            .classed(klass.box,true)

          makeChildGroups( table)
//          table = new FocusTable( _config, group)

          self.onFocusChange( focusChangeListener, element)
          self.onChartMouseOut( chartMouseOutListener, element)
        }

      })
    }

    return tooltipUnified;

  }

  if( ! trait.focus.tooltip)
    trait.focus.tooltip = {}

  trait.focus.tooltip.discrete = _tooltipDiscrete
  trait.focus.tooltip.unified = _tooltipUnified

}(d3, d3.trait));

(function (d3, trait) {

  var PADDING_NULL = new trait.Margin()

  trait.layout.table = function () {

    var children = d3_trait_tableChildren,
//        value = d3_trait_tableValue;
        nodes = null,
        padding = {
          arg: null,  // value get/set via table.padding
          getNull: function() { return PADDING_NULL},
          expandConstantType: 'number',
          expandConstant: function( p) {return new trait.Margin(p)}
        },
        textAlign = {
          arg: 'left',  // value get/set via table.padding
          getNull: function() {return 'left'}
        },
        verticalAlign = {
          arg: null,  // value get/set via table.padding
          getNull:  function() {return 'bottom'}
        }
    padding.get = padding.getNull
    textAlign.get = textAlign.getNull
    verticalAlign.get = verticalAlign.getNull


    function fillArray(array, length, defaultValue) {
      if (!array || length < 0 || length > 100)
        return
      while (array.length < length)
        array.push(defaultValue)
    }

    // Include padding.
    function calculateColWidths( rows) {
      var widths = [],
          colspans = []

      rows.forEach(function (row, rowIndex) {
        var colspan = 0,
            cols = children.call(table, row, row.depth)
        fillArray(widths, cols.length, 0)
        var spanOffset = 0
        cols.forEach(function (node, colIndex) {
          var actual = colIndex + spanOffset
          if( node.colspan) {
            colspans.push( { node: node, row: rowIndex, col: actual})
            spanOffset += node.colspan
          } else {
            var w = colWidthIncludingPadding( node, rowIndex, actual)
            widths[actual] = widths[actual] ? Math.max(widths[actual], w) : w
          }
        })
      })

      // Adjust columns that have colspans
      //
      colspans.sort( function( a, b) { return a.node.colspan - b.node.colspan})
      colspans.forEach( function( spanner) {
        var node = spanner.node,
            maxColWidth = getColspanWidth( spanner.col, node.colspan, widths),
            requestedWidth = colWidthIncludingPadding( node, node.row, node.col)
        if( maxColWidth < requestedWidth)
          stretchColumnWidthsForColspan( requestedWidth, maxColWidth, spanner.col, node.colspan, widths);
      })
      return widths
    }

    function colWidthIncludingPadding( node, rowIndex, colIndex) {
      var pad = padding.get( node, rowIndex, colIndex),
          w = pad.left + node.rect.size.width + pad.right
      return w
    }
    function getColspanWidth( start, span, widths) {
      var total = 0
      for( span--; span >= 0; span--)
        total += widths[start+span]
      return total
    }
    function stretchColumnWidthsForColspan( requestedWidth, currentWidth, start, span, widths) {
      var stretch = requestedWidth - currentWidth,
          each = stretch / span,
          total = 0

      for( span--; span >= 0; span--) {
        var index = start+span
        widths[index] = Math.round(widths[index] + each)
        total += widths[index]
      }
      // if we lost one in the division, add 1 to the first column.
      if( total < requestedWidth)
        widths[start] ++
    }

    // Include padding.
    function setDepthOnNodes( node, depth) {
      node.depth = depth
      var childs = children.call(table, node, depth)
      if( childs && childs.length > 0) {
        depth ++
        childs.forEach(function (child) {
          setDepthOnNodes( child, depth)
        })
      }
    }

    function calculateRowHeight(row, rowIndex) {
      var height = 0,
          cols = children.call(table, row, row.depth)

      cols.forEach(function (node, colIndex) {
        var pad = padding.get( node, rowIndex, colIndex),
            h = pad.top + node.rect.size.height + pad.bottom
        height = Math.max(height, h)
      })
      return height
    }

    /**
     * For now, we assume that the rect anchor matches the alignment, so textAlign='right' goes
     * with Rect anchor 1.0 and we set the x origin on the right side of the box.
     * @param node
     * @param rowIndex
     * @param colIndex
     * @param cellRect The bounding box for the table cell.
     * @returns {d3.trait.Point}
     */
    function nodeOriginInCellRect( node, rowIndex, colIndex, cellRect) {
      var pad = padding.get( node, rowIndex, colIndex),
          tAlign = textAlign.get( node, rowIndex, colIndex),
          vAlign = verticalAlign.get( node, rowIndex, colIndex),
          origin = new trait.Point()

      origin.x = tAlign === 'right' ? cellRect.maxX() - pad.right
        : tAlign === 'center' ? Math.round(cellRect.midX() + (pad.left - pad.right) / 2)
        : cellRect.minX() + pad.left // 'left'

      origin.y = vAlign === 'top' ? cellRect.minY() + pad.top
        : vAlign === 'middle' ? Math.round(cellRect.midY() + (pad.top - pad.bottom) / 2)
        : cellRect.maxY() - pad.bottom  // 'bottom'

      return origin
    }

    function layoutRow(row, rowIndex, colWidths) {
      // row origin and size are already set. Need to set width.

      var cols = children.call(table, row, row.depth),
          lineHeight = row.rect.size.height,
          x = 0,
          y = 0
      cols.forEach(function (node, colIndex) {
        var cellRect = new trait.Rect( x, y, colWidths[colIndex], lineHeight)
        node.rect.origin = nodeOriginInCellRect( node, rowIndex, colIndex, cellRect)
        x += cellRect.size.width
      })
      row.rect.size.width = x
    }



    /**
     * set parameter value or function.
     * @param param Parameters are padding, textAlign, verticalAlign, etc.
     * @param x The value or function to use
     */
    function setParameter( param, x) {

      function paramFunction(node, row, col) {
        var p = x.call(table, node, node.depth, row, col);
        return p == null ? param.getNull(node)
          : param.expandConstantType && typeof p === param.expandConstantType ? param.expandConstant(p)
          : p;
      }

      function paramConstant(node) {
        return x;
      }

      var type;
      param.get = (param.arg = x) == null ? param.getNull
        : (type = typeof x) === "function" ? paramFunction
        : param.expandConstantType && type === param.expandConstantType ? (x = param.expandConstant(x), paramConstant)
        : paramConstant;
    }

    /**
     *
     * table: {
     *    rect:,  // rect for whole table
     *    group:,
     *    children: [
     *      {
     *        rect:,  // rect for tr.
     *        group:,
     *        children: [
     *          {
     *            rect:,  // rect for td
     *            element:,
     *            value:,
     *            text:,  // format( value)
     *            bbox:,  // width, height
     *          },
     *          ...
     *        ]
     *      },
     *      ...
     * }
     *
     * @param rows Rows of columns of items with Rect.
     *
     * @returns {Size}
     */
    function table(d) {
      var nodes = d
      setDepthOnNodes( nodes, 0)

      var r = 0, c = 0,
          rect = nodes.rect,
          rows = children.call(table, nodes, nodes.depth),
          colWidths = calculateColWidths(rows)


      var y = 0
      rows.forEach(function (row, rowIndex) {
        var rowHeight = calculateRowHeight(row, rowIndex)
        row.rect.origin.x = 0  // TODO: row padding?
        row.rect.origin.y = y
        row.rect.size.height = rowHeight
        layoutRow(row, rowIndex, colWidths)
        y += rowHeight
      })

      rect.size.width = d3.sum( colWidths)
      rect.size.height = y - 0

      return nodes
    }

    table.padding = function(x) {
      if (!arguments.length) return padding.arg;
      setParameter( padding, x)
      return table
    };

    table.textAlign = function(x) {
      if (!arguments.length) return textAlign.arg;
      setParameter( textAlign, x)
      return table
    };

    table.verticalAlign = function(x) {
      if (!arguments.length) return verticalAlign.arg;
      setParameter( verticalAlign, x)
      return table
    };

    table.children = function(x) {
      if (!arguments.length) return children;
      children = x;
      return table;
    };

//    table.value = function(x) {
//      if (!arguments.length) return value;
//      value = x;
//      return table;
//    };

    table.utils = {
      setDepthOnNodes: setDepthOnNodes,
      calculateColWidths: calculateColWidths,
      calculateRowHeight: calculateRowHeight,
      nodeOriginInCellRect: nodeOriginInCellRect,
      layoutRow: layoutRow
    }

    return table
  } // end trait.layout.table

  function d3_trait_tableChildren(d) {
    return d.children;
  }

//  function d3_trait_tableValue(d) {
//    return d.value;
//  }



}(d3, d3.trait));

(function (d3, trait) {

  var paddingEm = new trait.Margin(),
      translate = function( node, x, y){}

  trait.layout.tilestack = function () {

    var layouts = {

      /**
       * translate X, but don't touch Y
       */
      LeftToRight: function( node, offset) {
        var r = node.rect

        offset.x += paddingEm.left * node.em

        if( r.anchor.x < 0.5) {
          r.origin.x = offset.x
          translate( node, r.minX(), r.origin.y)
          offset.x += r.size.width
        } else {
          offset.x += r.size.width
          r.origin.x = offset.x
          translate( node, r.maxX(), r.origin.y)
        }

        offset.x += paddingEm.right * node.em
        offset.y = Math.max( offset.y, r.maxY())
        return offset
      },

      /**
       * translate Y, but don't touch X
       */
      TopToBottom: function( node, offset) {
        var r = node.rect

        offset.y += paddingEm.top * node.em

        if( r.anchor.y < 0.5) {
          r.origin.y = offset.y
          translate( node, r.origin.x, r.minY())
          offset.y += r.size.height
        } else {
          offset.y += r.size.height
          r.origin.y = offset.y
          translate( node, r.origin.x, r.maxY())
        }

        offset.x = Math.max( offset.x, r.maxX())
        offset.y += paddingEm.bottom * node.em
        return offset
      }

    }


    function pack( node) {
      var emSum = 0,
          offset = new trait.Point()

      node.children.forEach( function( child) {
        if( child.children)
          pack( child)
        offset = layouts[ node.layoutChildren]( child, offset, paddingEm)
        emSum += child.em
      })

      //var nodeYMax = (paddingEm.top + paddingEm.bottom) * node.em  + r.maxY()

      node.rect = new trait.Rect( 0, 0, offset.x, offset.y)

      var length = node.children.length
      node.em = length === 0 ? 0 : emSum / length

      // TODO: resize children so vertical layout has the same widths, etc.
    }

    function tilestack( node) {
      pack( node)
      return node
    }

    tilestack.paddingEm = function(x) {
      if (!arguments.length) return paddingEm;
      paddingEm = x
      return tilestack
    };
    tilestack.translate = function(x) {
      if (!arguments.length) return translate;
      translate = x
      return tilestack
    };


    tilestack.utils = {
      pack: pack

    }
    tilestack.layouts = layouts


    return tilestack

  } // end trait.layout.tilestack


}(d3, d3.trait));

(function(d3, trait) {

  function _legendSeries(_super, _config) {
    // Store the group element here so we can have multiple line charts in one chart.
    // A second "line chart" might have a different y-axis, style or orientation.

    var orient = _config.orient || "top"

    function topOrBottom() { return orient === "top" || orient === "bottom"}

    function marginStyle() {
      var style, m = {left: 2, right: 2}
      if( _config.legendMargin ) {
        m.left = _config.legendMargin.left || m.left
        m.right = _config.legendMargin.right || m.right
        m.top = _config.legendMargin.top
        m.bottom = _config.legendMargin.bottom
      }
      m.left += _super.marginLeft()
      m.right += _super.marginRight()
      style = "margin-left:" + m.left + "px;"
      style += "margin-right:" + m.right + "px;"
      if( m.top )
        style += "margin-top:" + m.top + "px;"
      if( m.bottom )
        style += "margin-bottom:" + m.bottom + "px;"
      return style
    }

    var dispatch = d3.dispatch('customHover');

    function legendSeries(_selection) {
      var self = legendSeries

      _selection.each(function(_data) {
        var element = this

        if( !this._legend ) {
          var classes = _config.legendClass ? "legend " + _config.legendClass : 'legend'

          if( topOrBottom() ) {
            // insert before svg element. Could use ":first-child"
            var select = d3.select(this)
            this._legend = orient === "top" ? select.insert("ul", "svg") : select.append("ul")
            this._legend.attr("style", marginStyle())
              .attr("class", classes)
          } else {
            this._legend = this._container.append('g').classed(classes, true);
          }
        }

        var filtered = _config.legendFilter ? _data.filter(_config.legendFilter) : _data

        if( topOrBottom() ) {
          // DATA JOIN
          var legendTop = this._legend.selectAll("li")
            .data(filtered)

          // UPDATE

          // ENTER
          legendTop.enter()
            .append("li")
            .attr("class", "legend-item")
            .style("border-bottom-color", self.color)
            .text(_config.seriesLabel)

          // also try: <li><span>• </span>Lorem ipsum</li> with css span { font-size: 20pt; }
        } else {
          // DATA JOIN
          var legend = this._legend.selectAll(".legend")
            .data(filtered)

          // UPDATE


          // ENTER
          legend.enter()
            .append("g")
            .attr("class", "legend")
            .attr("transform", function(d, i) { return "translate(0," + i * 20 + ")"; })

          legend.append("rect")
            .attr("x", self.chartWidth() - 18)
            .attr("width", 18)
            .attr("height", 18)
            .style("fill", self.color)

          legend.append("text")
            .attr("x", self.chartWidth() - 24)
            .attr("y", 9)
            .attr("dy", ".35em")
            .style("text-anchor", "end")
            .text(_config.seriesLabel)
        }

      })
    }

    d3.rebind(legendSeries, dispatch, 'on');

    return legendSeries;

  }

  trait.legend.series = _legendSeries

}(d3, d3.trait));

(function(d3, trait) {


  /**
   * Implementation inspired by: http://bl.ocks.org/ZJONSSON/3918369
   *
   * @param _super
   * @param _config
   * @param _id
   * @returns {legendStandard}
   * @private
   */
  function _legendStandard(_super, _config, _id) {

    var group, ul, box,
        legendPadding = 5

    var klass = { main: _config['class'] || 'd3-trait-legend'}
    klass.box = klass.main + '-box'
    klass.items = klass.main + '-items'
    klass.item = klass.main + '-item'


    function legendStandard(_selection) {
      var self = legendStandard

      _selection.each(function(_data) {
        var element = this

        var li, liEnter

        var marginLeft = _super.marginLeft() + 20
        var marginTop = _super.marginTop() + 20
        if( ! group) {

          group = element._container.append('g')
            .attr({
              'class':      klass.main
            });
          box = group.append('rect')
            .classed(klass.box,true)
          ul = group.append('g')
            .classed(klass.items,true)
            .attr('transform', 'translate('+marginLeft+','+marginTop+')')
        }


        var filtered = _config.legendFilter ? _data.filter(_config.legendFilter) : _data

//        li = ul.selectAll('.' + klass.item)
        li = ul.selectAll('.' + klass.item)
          .data(filtered)

        liEnter = li.enter()
//          .append('g')
//            .classed(klass.items,true)
//            .attr('transform', function(d,i) { return 'translate(0,'+i+'em)'})


        liEnter.append('text')
          .classed(klass.item,true)
//          .attr('y',0)
          .attr('y',function(d,i) { return i+'em'})
          .attr('x','1em')
          .text(_config.seriesLabel)

        liEnter.append('circle')
//          .attr('cy', '' + (-0.25) +'em')
          .attr('cy',function(d,i) { return i-0.3+'em'})
          .attr('cx',0)
          .attr('r','0.4em')
          .style('fill', self.color)


        li.exit()
          .remove()

        // Reposition and resize the box
        // BBox is bbox for ul group
        var lbbox = ul[0][0].getBBox()
        box.attr('x', Math.floor(marginLeft + lbbox.x-legendPadding) + 0.5)// add 0.5 so line is crisp!
          .attr('y', Math.floor(marginTop + lbbox.y-legendPadding) + 0.5)
          .attr('height', Math.ceil(lbbox.height+2*legendPadding))
          .attr('width', Math.ceil(lbbox.width+2*legendPadding))
      })
    }

    return legendStandard;

  }

  trait.legend.standard = _legendStandard

}(d3, d3.trait));

(function(d3, trait) {

  var debug = false

  var TRACKING_NONE = "none"

// Force domain to follow current wall-time (i.e. domain max = current time).
  var TRACKING_CURRENT_TIME = "current-time"

// Force domain to follow latest date in data (i.e. domain max = _data.max().
  var TRACKING_DOMAIN_MAX = "domain-max"


  var timeIntervals = [
    d3.time.second,
    d3.time.minute,
    d3.time.hour,
    d3.time.day,
    d3.time.week,
    d3.time.month,
    d3.time.year
  ]

  function isTimeInterval(d) { return timeIntervals.indexOf(d) >= 0 }

  function getMillisFromDomain(domain) { return domain[ domain.length - 1].getTime() - domain[0].getTime() }

  function makeAccessorsFromConfig(config, scaleName) {
    return {
      series: config.seriesData,
      data:   config[scaleName],
      scaleName: scaleName
    }
  }

  /**
   * domainMin or domainMax overrides domain.
   *
   * @param config
   * @returns domain config { trend, domain, domainMin, domainMax }
   */
  function makeDomainConfig(config) {
    var dMin = d3.trait.utils.configFloat(config.domainMin, null),
        dMax = d3.trait.utils.configFloat(config.domainMax, null),
        dc = {
          trend: config.trend
        }

    if( dMin !== null && dMax !== null ) {
      dc.domain = [dMin, dMax]
    } else if( dMin !== null || dMax != null ) {
      dc.domainMin = dMin
      dc.domainMax = dMax
    } else {
      dc.domain = config.domain
    }
    return dc
  }


  /**
   * Return an object with interval and count or null
   *
   * { interval: d3.time.minute,
 *   intervalCount: 15
 * }
   *
   * @param config  {interval: d3.time.interval, intervalCount: 1}
   * @returns null or {interval: d3.time.interval, count: number}
   */
  function makeIntervalFromConfig(config) {

    return !config.interval ? null
      : {
      interval: config.interval,
      count:    config.intervalCount || 1
    }
  }

// trendDomain: { interval: d3.time.month, count: 1 }
// trendDomain: { interval: milliseconds, count: 1 }
  function getTrendMin(max, trendDomain) {
    var min,
        count = trendDomain.count || 1

    if( isTimeInterval(trendDomain.interval) )
      min = trendDomain.interval.offset(max, 0 - count)
    else if( max instanceof Date )
      min = max.getTime() - (trendDomain.interval * count)
    else
      min = max - (trendDomain.interval * count)

    return min
  }

  /**
   * { track: "domain-max", domain: { interval: d3.time.month, count: 1 } }
   * @param trend
   * @param data
   * @param access
   * @returns {*}
   */
  function getDomainTrend(trend, data, access) {
    var min, max, domain

    if( trend.track === TRACKING_CURRENT_TIME ) {

      max = new Date()
      min = getTrendMin(max, trend.domain)
      domain = [min, max]

    } else {

      // assume TRACKING_DOMAIN_MAX

      if( trend.domain && trend.domain.interval ) {

        // tracking is domain-max or none. In either case, since a time interval
        // is specified, we'll do domain-max
        //
        max = trait.utils.maxFromData(data, access)
        min = getTrendMin(max, trend.domain)
        domain = [min, max]

      } else {

        domain = trait.utils.extentFromData(data, access)
      }
    }
    return domain
  }

  /**
   *
   *
   *
   * @param domainConfig Config object with domain, interval, tracking
   * @param data The chart data
   * @param access object containing series, data
   * @returns {*}
   */
  function getDomain( domain, domainConfig, data, access) {
    var min, max, dataDomain

    // if domainConfig.domain is specified, it trumps other configs
    if( domainConfig.domain )
      return domainConfig.domain

    // TODO: This overrides trend. The two should work together.
    if( domainConfig.minDomainFromData) {
      if( trait.utils.isExtentExtended( domain, domainConfig.minDomainFromData))
        domain = trait.utils.extendExtent( domain, domainConfig.minDomainFromData)
      return domain
    }


    if( domainConfig.trend )
      domain = getDomainTrend(domainConfig, data, access)
    else if( domainConfig.domainMin != null )
      domain = [domainConfig.domainMin, trait.utils.maxFromData(data, access)]
    else if( domainConfig.domainMax != null )
      domain = [trait.utils.minFromData(data, access), domainConfig.domainMax]
    else
      domain = trait.utils.extentFromData(data, access)

    return domain
  }

//function pd( d) {
//    var m, day
//    m = d.getMonth() + 1
//    day = d.getDate()
//    return "" + m + "-" + day
//}


// 1. reset the range to chart dimensions
// 2. get new max from data or date now
// 3. domainTranslateNew = max - oldMax
// 4. newDomain = shift domain by domainTranslateLast
// 5. newRangeMax = scale( max)
// 6. domain = [newDomain[0], max]
// 7. extend range to newRangeMax
// 8. save oldMax and domainTranslateLast
//
  function updateScale(scale, range, domainConfig, data, access) {
    var min, max, dataDomain, oldDomain, oldMax, newRangeMax

    scale.range(range)

    // if domainConfig.domain is specified, it trumps other configs
    if( domainConfig.domain ) {
      scale.domain(domainConfig.domain)
      return
    }

    oldDomain = scale.domain()
    oldMax = oldDomain[ oldDomain.length - 1]

    if( domainConfig.trend ) {
      var trend = domainConfig.trend

      if( trend.track === TRACKING_CURRENT_TIME ) {

        max = new Date()
        min = getTrendMin(max, trend.domain)
        scale.domain([min, max])

      } else {
        if( trend.domain && trend.domain.interval ) {

          // track is domain-max or none. In either case, since a time interval
          // is specified, we'll do domain-max
          //

          max = trait.utils.maxFromData(data, access)

          // The scale is translated off to the left.
          // Reset domain with oldMax to get rid of the part not visible.
          min = getTrendMin(oldMax, trend.domain)
          scale.domain([min, oldMax])
          //console.log( "updateScale domain [min, oldMax]: " + pd( min) + " " + pd( oldMax))

          newRangeMax = scale(max)

          // Expand the domain to the right with the new max.
          min = getTrendMin(max, trend.domain)
          scale.domain([min, max])
          //console.log( "updateScale domain [min,    max]: " + pd(min) + " " + pd( max) + " end")
          // Expand the range to the right, so we can scroll it slowly to the left.
          scale.range([range[0], newRangeMax])

        } else {
          dataDomain = trait.utils.extentFromData(data, access)
          scale.domain(dataDomain)
        }
      }

    } else {
      dataDomain = trait.utils.extentFromData(data, access)
      scale.domain(dataDomain)
    }

  }


  function _scaleOrdinalBars(_super, _config) {
    var filteredData,
        scaleName = _config.axis,
        axisChar = scaleName.charAt(0), // x | y
        accessData = _config[scaleName],
        scale = d3.scale.ordinal()

    function scaleOrdinalBars(_selection) {
      var self = scaleOrdinalBars

      _selection.each(function(_data) {
        var ordinals,
            element = this

        filteredData = _config.seriesFilter ? _data.filter(_config.seriesFilter) : _data

        var rangeMax = axisChar === 'x' ? self.chartWidth() : self.chartHeight()
        scale.rangeRoundBands([0, rangeMax], 0.1)

        // Use the first series for the ordinals. TODO: should we merge the series ordinals?
        ordinals = filteredData[0].map(accessData)
        scale.domain(ordinals);
      })
    }

    scaleOrdinalBars[scaleName] = function() {
      return scale;
    };
    return scaleOrdinalBars;
  }

  function _scaleTime(_super, _config) {

    var filteredData,
        scaleName = _config.axis,
        axisChar = scaleName.charAt(0),
        access = makeAccessorsFromConfig(_config, scaleName),
        domainConfig = makeDomainConfig(_config),
        scale = d3.time.scale()
      ;

    _super.minRangeMargin(scaleName, _config.minRangeMargin)


    function scaleTime(_selection) {
      var self = scaleTime

      _selection.each(function(_data, i, j) {
        var currentDomain,
            element = this

        // TODO: store this in each selection?
        filteredData = _config.seriesFilter ? _data.filter(_config.seriesFilter) : _data

        scale.domain(getDomain( scale.domain(), domainConfig, filteredData, access))

        // TODO: nice overlaps wth interval. Maybe it's one or the other?
        if( _config.nice )
          scale.nice(_config.nice) // start and end on month. Ex Jan 1 00:00 to Feb 1 00:00
        scale.range(d3.trait.utils.getScaleRange(self, scaleName))
      })
    }

    scaleTime[scaleName] = function() {
      return scale;
    }
    scaleTime[scaleName + 'Domain'] = function(newDomain) {
      domainConfig.domain = newDomain
      scale.domain(newDomain)
      // TODO: domain updated event?
    }
    scaleTime.update = function(type, duration) {

      this._super(type, duration)

      // Reset the range to the physical chart coordinates. We'll use this range to
      // calculate newRangeMax below, then we'll extend the range to that.
      var range = d3.trait.utils.getScaleRange(_super, scaleName)

      updateScale(scale, range, domainConfig, filteredData, access)

      return this;
    };


    _super.onRangeMarginChanged('scaleTime-' + scaleName, scaleTime)

    return scaleTime;
  }


  /**
   * Each time this trait is stacked it produces an addition yScale (ex: y1, y2, ... y10)
   * @param _super
   * @returns {Function}
   */
  function _scaleLinear(_super, _config) {

    var filteredData,
        scaleName = _config.axis,
        axisChar = scaleName.charAt(0),
        access = makeAccessorsFromConfig(_config, scaleName),
        domainConfig = makeDomainConfig(_config),
        scale = d3.scale.linear()

    _super.minRangeMargin(_config.axis, _config.minRangeMargin)


    function scaleLinear(_selection) {
      var self = scaleLinear

      _selection.each(function(_data) {
        var extents, min, max,
            element = this

        filteredData = _config.seriesFilter ? _data.filter(_config.seriesFilter) : _data
        var domain = getDomain( scale.domain(), domainConfig, filteredData, access)
        scale.domain( domain)
        var range = d3.trait.utils.getScaleRange(self, scaleName)
        scale.range(range)
        if( debug)
          console.log( 'scaleLinear.each ' + scaleName + ' range:' + range + ' domain:' + domain)
      })
    }

    scaleLinear[scaleName] = function() {
      return scale;
    };
    scaleLinear[scaleName + 'Domain'] = function(newDomain) {
      domainConfig.domain = newDomain
      scale.domain(newDomain)
      // TODO: domain updated event?
    }
    scaleLinear[scaleName + 'MinDomainExtent'] = function(minDomain) {
      if( !arguments.length ) return domainConfig.minDomain

      if( trait.utils.isExtentExtended( domainConfig.minDomain, minDomain)) {
        domainConfig.minDomain = trait.utils.extendExtent( domainConfig.minDomain, minDomain)

        var domain = scale.domain()
        if( trait.utils.isExtentExtended( domain, domainConfig.minDomain)) {
          domain = trait.utils.extendExtent( domain, domainConfig.minDomain)
          scale.domain( domain)
          // TODO: domain updated event?
        }
      }
    }

    /**
     * Each chart can specify the minimum required for domain extent (ex: min height or width).
     * If a chart is stacked it needs more height from the scale's domain.
     *
     * @param minDomain
     * @returns {*|minDomainFromData}
     */
    scaleLinear[scaleName + 'MinDomainExtentFromData'] = function(minDomain) {
      if( !arguments.length ) return domainConfig.minDomainFromData

      // Is new extend greater than current extent?
      if( trait.utils.isExtentExtended( domainConfig.minDomainFromData, minDomain)) {
        domainConfig.minDomainFromData = trait.utils.extendExtent( domainConfig.minDomainFromData, minDomain)

        var domain = scale.domain()
        if( trait.utils.isExtentExtended( domain, domainConfig.minDomainFromData)) {
          domain = trait.utils.extendExtent( domain, domainConfig.minDomainFromData)
          scale.domain( domain)
          // TODO: domain updated event?
        }
      }
    }
    scaleLinear.update = function(type, duration) {
      this._super(type, duration)
      var range = d3.trait.utils.getScaleRange(_super, scaleName)
      if( debug)
        console.log( 'scaleLinear.update1 ' + scaleName + ' range:' + range + ' domain:' + scale.domain());

      // reset the minimum domain from visible data, so later traits can grow the min domain as needed.
      delete domainConfig.minDomainFromData;
      updateScale(scale, range, domainConfig, filteredData, access)
      if( debug)
        console.log( 'scaleLinear.update2 ' + scaleName + ' range:' + range + ' domain:' + scale.domain())

      return this;
    };


    _super.onRangeMarginChanged('scaleLinear-' + scaleName, scaleLinear)

    return scaleLinear;
  }

  if( !trait.scale.ordinal )
    trait.scale.ordinal = {}

  trait.scale.linear = _scaleLinear
  trait.scale.ordinal.bars = _scaleOrdinalBars
  trait.scale.time = _scaleTime

}(d3, d3.trait));
