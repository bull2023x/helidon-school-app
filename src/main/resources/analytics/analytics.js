(() => {
  'use strict';
  const el = id => document.getElementById(id);
  let token = '';
  let busy = false;
  function clear() { el('login').hidden=false; token=''; el('token').value=''; el('statistics').hidden=true; for (const id of ['today','month','total','date','monthLabel','started']) el(id).textContent=''; }
  async function load() {
    if (busy) return;
    busy=true; el('show').disabled=el('refresh').disabled=el('logout').disabled=true;
    el('message').textContent='集計を取得しています…';
    try {
      const response=await fetch('/admin/api/analytics',{headers:{Authorization:'Bearer '+token},cache:'no-store',credentials:'omit'});
      const data=await response.json();
      if (!response.ok) { if (response.status===401) clear(); throw new Error(data.error || '集計を取得できませんでした。'); }
      for (const id of ['today','month','total']) el(id).textContent=Number(data[id]).toLocaleString('ja-JP')+' 回';
      el('date').textContent=data.date+'（日本時間）';el('monthLabel').textContent=data.monthLabel+'（日本時間）';
      el('started').textContent='記録開始：'+new Date(data.startedAt).toLocaleString('ja-JP',{timeZone:'Asia/Tokyo'})+'（日本時間）';
      el('statistics').hidden=false;
      // Removing the successful login form lets password managers detect sign-in.
      el('login').hidden=true;
      el('message').textContent=data.lastRecordingFailure?'記録できなかった閲覧がある可能性があります。最終記録エラー：'+new Date(data.lastRecordingFailure).toLocaleString('ja-JP',{timeZone:'Asia/Tokyo'}):'集計を更新しました。';
    } catch(error) { el('login').hidden=false;el('statistics').hidden=true;el('message').textContent=error.message; }
    finally { busy=false;el('show').disabled=el('refresh').disabled=el('logout').disabled=false; }
  }
  el('login').addEventListener('submit',event=>{event.preventDefault();token=el('token').value;load();});
  el('refresh').addEventListener('click',load);
  el('logout').addEventListener('click',()=>{clear();el('message').textContent='表示を閉じ、メモリ内のキーを消去しました。';});
})();
