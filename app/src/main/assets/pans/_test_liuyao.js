const L = require('./lunar.js');
global.Lunar = L.Lunar;
const E = require('./engine.js');
const r = E.LiuYaoEngine.generateLiuyao(new Date('2024-03-15T10:30:00'), {});
console.log('keys:', Object.keys(r).join(','));
console.log('name:', r.name, '| palace:', r.palace, '| yaos:', r.yaosDetail && r.yaosDetail.length);
console.log('dayGanZhi:', r.ganzhi && r.ganzhi.dayGanZhi, '| monthGanZhi:', r.ganzhi && r.ganzhi.monthGanZhi);
if (r.yaosDetail) {
  r.yaosDetail.forEach(function(y){
    console.log('  y' + y.position, y.najiaDizhi, y.wuxing, y.sixRelative, y.sixGod, (y.isMoving?'动':''), '世应='+y.shiYing);
  });
}
