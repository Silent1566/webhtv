(function (root, factory) {
  var api = factory();
  if (typeof module === 'object' && module && module.exports) module.exports = api;
  else root.EclipseFocusNavigation = api;
}(this, function () {
  'use strict';

  function text(value) {
    return value === null || typeof value === 'undefined' ? '' : String(value);
  }

  function asArray(value) {
    return Object.prototype.toString.call(value) === '[object Array]' ? value : [];
  }

  function findHorizontalTarget(items, currentIndex, direction) {
    var list = asArray(items);
    var index = Number(currentIndex);
    var current;
    var row;
    var currentX;
    var best = -1;
    var bestDistance = Number.MAX_VALUE;
    var i;
    if ((direction !== 'left' && direction !== 'right') || index < 0 || index >= list.length) return -1;
    current = list[index] && typeof list[index] === 'object' ? list[index] : {};
    row = text(current.row);
    currentX = Number(current.x);
    if (!row || !isFinite(currentX)) return -1;
    for (i = 0; i < list.length; i += 1) {
      var candidate = list[i] && typeof list[i] === 'object' ? list[i] : {};
      var candidateX;
      var delta;
      var distance;
      if (i === index || text(candidate.row) !== row) continue;
      candidateX = Number(candidate.x);
      if (!isFinite(candidateX)) continue;
      delta = candidateX - currentX;
      if (direction === 'left' && delta >= -3) continue;
      if (direction === 'right' && delta <= 3) continue;
      distance = Math.abs(delta);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = i;
      }
    }
    return best;
  }

  return {
    findHorizontalTarget: findHorizontalTarget
  };
}));
