# 自宅学習

developpertool console 更新確認

fetch('/odata/v4/SerialService/SerialData', { method: 'OPTIONS' })
  .then(r => {
    console.log('status:', r.status);
    console.log('allow:', r.headers.get('allow'));
    console.log([...r.headers.entries()]);
  });


fetch("/odata/v4/SerialService/SerialData(s4Key='S4-001')", {
  method: 'PATCH',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ status: 'OPEN' })
}).then(async r => {
  console.log('status:', r.status);
  console.log(await r.text());
});
