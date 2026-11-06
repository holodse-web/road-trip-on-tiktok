// Local fixture for checking the bundled panel without starting Minecraft or TikTok.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const root = path.join(__dirname, '..');
const configSource = fs.readFileSync(path.join(root, 'src/main/java/rttt/client/game/Config.java'), 'utf8');
let config = {};
for (const match of configSource.matchAll(/^    public (?:boolean|String|int|double|long) (\w+) = (.+);$/gm)) {
  try { config[match[1]] = JSON.parse(match[2].replace(/(?<=\d)_(?=\d)/g, '')); } catch {}
}
config.gifts = [{name:'Rose', ru:'Роза', coins:1, meters:150, effect:'SPARKLE', image:'', showInOverlay:true}];
config.mobs = {zombie:{enabled:true, meters:-100, power:1}};
config.hudOffsetX = 0;config.hudOffsetY = 0;config.hudScale = 1;
const requests = [];
let bridge = {state:'stopped', running:false, username:'', message:'Мост не запущен.', logs:[]};
let capture = {width:576,height:1024,portrait:true,fps:60,limited:true,extendsBeyondDisplay:false};
let failNext = false;
const json = (res, value, status=200) => {res.writeHead(status, {'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify(value));};
http.createServer(async (req,res) => {
  let body='';for await(const chunk of req)body+=chunk;
  let data;try{data=body?JSON.parse(body):{};}catch{return json(res,{error:'invalid JSON'},400);}
  if(req.method==='POST')requests.push({path:req.url, data});
  if(req.url==='/__requests')return json(res,requests);
  if(req.url==='/__fail'){failNext=true;return json(res,{ok:true});}
  if(failNext && req.url.startsWith('/api/') && req.method==='POST'){failNext=false;return json(res,{error:'Тестовая ошибка'},503);}
  if(req.url==='/api/config') {if(req.method==='POST')config=data;return json(res,req.method==='POST'?{ok:true}:config);}
  if(req.url==='/api/meta')return json(res,{mobs:[{id:'zombie',ru:'Зомби',names:['зомби','zombie'],helper:false}],positiveEffects:['SPARKLE'],negativeEffects:['KNOCK']});
  if(req.url==='/api/status')return json(res,{distance:12500, goal:999999, wins:3,runs:4,speed:11.5,mobQueue:0,rewardQueue:0,weather:'CLEAR',timeOfDay:config.timeOfDay||'AUTO',testMode:true,active:true,entering:false,liveStatus:'Проверка панели на тестовых данных',log:['18:00:00  💬 Зритель: привет, чат работает'],unknownGifts:[],bridge,capture});
  if(req.url==='/api/action'){
    if(data.action==='timeOfDay')config.timeOfDay=data.value;
    if(data.action==='bridgeStart')bridge={state:'running',running:true,username:data.username,message:'Процесс моста запущен (тестовый стенд).',logs:['[мост] чат: зритель: привет!']};
    if(data.action==='bridgeStop')bridge={...bridge,state:'stopped',running:false,message:'Мост остановлен.'};
    if(data.action==='window')capture={width:data.fitDisplay===false?1080:576,height:data.fitDisplay===false?1920:1024,portrait:true,fps:60,limited:data.fitDisplay!==false,extendsBeyondDisplay:data.fitDisplay===false};
    return json(res,{ok:true});
  }
  if(req.url==='/api/test')return json(res,{ok:true});
  if(req.url.startsWith('/mc/')){
    res.writeHead(200,{'Content-Type':'image/svg+xml'});return res.end('<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24"><path fill="#9a9ca6" d="M5 5h14v14H5z"/></svg>');
  }
  if(req.url.startsWith('/gift/')){res.writeHead(404);return res.end();}
  if(req.url==='/'||req.url==='/index.html'){
    res.writeHead(200,{'Content-Type':'text/html; charset=utf-8'});return res.end(fs.readFileSync(path.join(root,'src/main/resources/assets/rttt/web/index.html')));
  }
  res.writeHead(404);res.end();
}).listen(18765,'127.0.0.1',()=>console.log('Panel fixture: http://127.0.0.1:18765 (no live connections)'));
