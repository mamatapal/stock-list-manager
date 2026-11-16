// Pages opened from emailed links. The token is after "#" in the address, so it is never sent to the server
// when the page loads and cannot be used up by email scanners that open links; it is sent only when the
// supplier presses the button. Every text is shown in Hindi and English together.
(function () {
  'use strict';

  const MESSAGES = {
    LINK_INVALID: ['यह लिंक सही नहीं है। ऐप से नया लिंक मँगाएँ।', 'This link is not valid. Ask for a new one in the app.'],
    LINK_EXPIRED: ['इस लिंक का समय खत्म हो गया है। ऐप से नया लिंक मँगाएँ।', 'This link has expired. Ask for a new one in the app.'],
    LINK_USED: ['यह लिंक पहले ही इस्तेमाल हो चुका है या उसकी जगह नया लिंक भेजा गया है।', 'This link has already been used or replaced by a newer one.'],
    WEAK_PASSWORD: ['पासवर्ड में 8 से 128 अक्षर हों और वह आम पासवर्ड न हो।', 'The password needs 8 to 128 characters and must not be a common password.'],
    ACCOUNT_INACTIVE: ['यह खाता चालू नहीं है।', 'This account is not active.'],
    TOO_MANY_ATTEMPTS: ['बहुत ज़्यादा कोशिशें हुईं। एक मिनट बाद फिर कोशिश करें।', 'Too many attempts. Please try again in a minute.'],
    MISMATCH: ['दोनों पासवर्ड एक जैसे नहीं हैं।', 'The two passwords are not the same.'],
    NETWORK: ['सर्वर से जुड़ नहीं पाए। इंटरनेट देखकर फिर कोशिश करें।', 'Could not reach the server. Check the internet and try again.'],
    OTHER: ['कुछ गड़बड़ हुई। फिर कोशिश करें।', 'Something went wrong. Please try again.'],
    VERIFIED: ['आपका ईमेल पक्का हो गया। हमारी टीम अब आपके बिज़नेस की जाँच करेगी। मंज़ूरी मिलने पर आपको ईमेल आएगा। अब आप ऐप खोल सकते हैं।',
      'Your email is verified. Our team will now review your business; you will get an email when it is approved. You can open the app now.'],
    RESET: ['आपका नया पासवर्ड सेव हो गया। अब ऐप में नए पासवर्ड से साइन इन करें।', 'Your new password is saved. Sign in to the app with it now.']
  };

  const token = new URLSearchParams(location.hash.slice(1)).get('t');
  // Keep the token out of the browser history.
  if (location.hash) history.replaceState(null, '', location.pathname);

  const page = document.body.dataset.page;
  const result = document.getElementById('result');
  const form = document.getElementById('form');
  const button = document.getElementById('go');

  function say(key, ok) {
    const [hi, en] = MESSAGES[key] || MESSAGES.OTHER;
    result.replaceChildren();
    const p1 = document.createElement('p'); p1.lang = 'hi'; p1.textContent = hi;
    const p2 = document.createElement('p'); p2.lang = 'en'; p2.textContent = en;
    result.append(p1, p2);
    result.className = 'result' + (ok ? '' : ' error');
    result.hidden = false;
  }

  async function post(path, body) {
    let res;
    try {
      res = await fetch('/api/v1/supplier-portal/auth/' + path, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body: JSON.stringify(body), cache: 'no-store', credentials: 'omit'
      });
    } catch (e) {
      return 'NETWORK';
    }
    if (res.ok) return null;
    try { return (await res.json()).code || 'OTHER'; } catch (e) { return 'OTHER'; }
  }

  if (!token) {
    say('LINK_INVALID', false);
    form.hidden = true;
    return;
  }

  if (page === 'verify') {
    button.addEventListener('click', async () => {
      button.disabled = true;
      const err = await post('verify-email', { token });
      if (err) { say(err, false); button.disabled = false; return; }
      form.hidden = true;
      say('VERIFIED', true);
    });
  } else {
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const pw1 = document.getElementById('pw1').value;
      const pw2 = document.getElementById('pw2').value;
      if (pw1 !== pw2) { say('MISMATCH', false); return; }
      button.disabled = true;
      const err = await post('password/reset', { token, newPassword: pw1 });
      if (err) { say(err, false); button.disabled = false; return; }
      form.hidden = true;
      say('RESET', true);
    });
  }
})();
