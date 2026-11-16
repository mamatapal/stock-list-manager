// Supplier Administration portal. Plain JavaScript, no libraries. All supplier data is written with textContent,
// never as HTML. The session lives in an HttpOnly cookie; every request also sends the X-Requested-With header,
// which the server requires for changes (protection against cross-site requests).
(function () {
  'use strict';

  const API = '/api/v1/admin';
  const STATUS_TEXT = {
    PENDING_EMAIL_VERIFICATION: 'Email not verified',
    PENDING_APPROVAL: 'Waiting for approval',
    APPROVED: 'Approved',
    REJECTED: 'Rejected',
    SUSPENDED: 'Suspended'
  };
  const ACTION_TEXT = {
    REGISTERED: 'Registered',
    EMAIL_VERIFIED: 'Email verified',
    DETAILS_UPDATED: 'Details changed by the supplier',
    RESUBMITTED: 'Sent again for approval',
    APPROVED: 'Approved',
    REJECTED: 'Rejected',
    SUSPENDED: 'Suspended',
    REINSTATED: 'Reinstated',
    REGISTRATION_ATTEMPT: 'Someone tried to register again with this email (the owner was emailed)',
    PASSWORD_RESET_REQUESTED: 'Password reset link requested',
    PASSWORD_RESET: 'Password reset with the emailed link',
    PASSWORD_CHANGED: 'Password changed in the app',
    EMAIL_VERIFIED_BY_RESET: 'Email verified through a password-reset link'
  };

  const $ = (id) => document.getElementById(id);
  let currentFilter = 'PENDING_APPROVAL';
  let current = null; // the supplier shown in the detail view

  function show(view) {
    for (const v of ['login-view', 'list-view', 'detail-view']) $(v).hidden = v !== view;
  }

  function notice(text, isError) {
    const n = $('notice');
    n.textContent = text || '';
    n.className = 'notice' + (isError ? ' error' : '');
    n.hidden = !text;
  }

  function fmt(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    return isNaN(d) ? '' : d.toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
  }

  async function api(method, path, body, version) {
    const headers = { 'Accept': 'application/json', 'X-Requested-With': 'SupplierAdministration' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (version !== undefined) headers['If-Match'] = String(version);
    const res = await fetch(API + path, {
      method, headers, credentials: 'same-origin', cache: 'no-store',
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    let data = null;
    const text = await res.text();
    if (text) { try { data = JSON.parse(text); } catch (e) { data = null; } }
    if (res.status === 401 && path !== '/auth/login') {
      $('who').hidden = true;
      show('login-view');
      throw new Error('Your session has ended. Please sign in again.');
    }
    if (!res.ok) throw new Error((data && data.detail) || ('Request failed (' + res.status + ')'));
    return data;
  }

  // ------------------------------------------------------------------ sign in / out

  $('login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    notice('');
    const btn = e.submitter || $('login-form').querySelector('button');
    btn.disabled = true;
    try {
      const me = await api('POST', '/auth/login', {
        username: $('username').value.trim(),
        password: $('password').value,
        code: $('code').value.replace(/\s/g, '')
      });
      $('password').value = '';
      $('code').value = '';
      signedIn(me);
    } catch (err) {
      notice(err.message, true);
    } finally {
      btn.disabled = false;
    }
  });

  $('logout').addEventListener('click', async () => {
    try { await api('POST', '/auth/logout'); } catch (e) { /* already signed out */ }
    $('who').hidden = true;
    notice('You have signed out.');
    show('login-view');
  });

  function signedIn(me) {
    $('who-name').textContent = me.fullName + ' (' + me.username + ')';
    $('who').hidden = false;
    loadList();
  }

  // ------------------------------------------------------------------ list

  for (const b of document.querySelectorAll('.filter')) {
    b.addEventListener('click', () => { currentFilter = b.dataset.status; notice(''); loadList(); });
  }
  $('refresh').addEventListener('click', () => loadList());
  $('back').addEventListener('click', () => { notice(''); loadList(); });

  async function loadList() {
    for (const b of document.querySelectorAll('.filter')) {
      b.classList.toggle('active', b.dataset.status === currentFilter);
    }
    try {
      const q = currentFilter ? '?status=' + encodeURIComponent(currentFilter) : '';
      const rows = await api('GET', '/suppliers' + q);
      const body = $('rows');
      body.replaceChildren();
      for (const s of rows) {
        const tr = document.createElement('tr');
        const name = s.tradeName ? s.tradeName + ' (' + s.legalName + ')' : s.legalName;
        for (const value of [name, s.city + ', ' + s.state, s.email, s.mobile, s.gstin || '',
          null, fmt(s.submittedAt || s.createdAt)]) {
          const td = document.createElement('td');
          if (value === null) td.appendChild(statusPill(s.status)); else td.textContent = value;
          tr.appendChild(td);
        }
        tr.addEventListener('click', () => openSupplier(s.id));
        body.appendChild(tr);
      }
      $('empty').hidden = rows.length > 0;
      show('list-view');
    } catch (err) {
      notice(err.message, true);
    }
  }

  function statusPill(status) {
    const span = document.createElement('span');
    span.className = 'status ' + status;
    span.textContent = STATUS_TEXT[status] || status;
    return span;
  }

  // ------------------------------------------------------------------ detail and decisions

  async function openSupplier(id) {
    try {
      render(await api('GET', '/suppliers/' + encodeURIComponent(id)));
      show('detail-view');
    } catch (err) {
      notice(err.message, true);
    }
  }

  function render(d) {
    current = d;
    const s = d.summary;
    $('d-title').textContent = s.tradeName ? s.tradeName + ' (' + s.legalName + ')' : s.legalName;
    const st = $('d-status');
    st.className = 'status ' + s.status;
    st.textContent = STATUS_TEXT[s.status] || s.status;

    const warnings = $('d-warnings');
    warnings.replaceChildren();
    if (d.otherSuppliersWithSameGstin > 0) addWarning(warnings, d.otherSuppliersWithSameGstin
      + ' other supplier(s) registered with the same GSTIN. Check for duplicates before approving.');
    if (d.otherSuppliersWithSameMobile > 0) addWarning(warnings, d.otherSuppliersWithSameMobile
      + ' other supplier(s) registered with the same mobile number.');
    if (!d.emailVerifiedAt) addWarning(warnings, 'The email address has not been verified yet.');
    if (d.emailVerifiedByReset) addWarning(warnings, 'The email was verified through a password reset, after the '
      + 'business details had been entered. Check that the details belong to the email owner before approving.');

    const fields = $('d-fields');
    fields.replaceChildren();
    const rows = [
      ['Legal name', s.legalName], ['Trade name', s.tradeName], ['Contact person', d.contactPerson],
      ['Mobile', s.mobile], ['Email', s.email], ['Address', d.address],
      ['City / State / PIN', s.city + ', ' + s.state + ' ' + d.pinCode], ['GSTIN', s.gstin || 'Not given'],
      ['Registered', fmt(s.createdAt)], ['Email verified', fmt(d.emailVerifiedAt) || 'No'],
      ['Submitted for approval', fmt(s.submittedAt)], ['Last decision', fmt(s.reviewedAt)],
      ['Reason given', d.statusReason]
    ];
    for (const [k, v] of rows) {
      if (v === null || v === undefined || v === '') continue;
      const dt = document.createElement('dt'); dt.textContent = k;
      const dd = document.createElement('dd'); dd.textContent = v;
      fields.append(dt, dd);
    }

    const users = $('d-users');
    users.replaceChildren();
    for (const u of d.users) {
      const li = document.createElement('li');
      li.textContent = u.fullName + ' - ' + u.email + ' - ' + u.mobile + (u.emailVerified ? ' (email verified)' : ' (email not verified)')
        + (u.lastLoginAt ? ', last sign-in ' + fmt(u.lastLoginAt) : '');
      users.appendChild(li);
    }

    const history = $('d-history');
    history.replaceChildren();
    for (const e of d.history) {
      const li = document.createElement('li');
      let text = fmt(e.at) + ' - ' + (ACTION_TEXT[e.action] || e.action);
      if (e.actorName) text += ' by ' + e.actorName;
      if (e.reason) text += ': ' + e.reason;
      if (e.ip) text += ' (from ' + e.ip + ')';
      li.textContent = text;
      history.appendChild(li);
    }

    $('reason').value = '';
    $('approve').hidden = s.status !== 'PENDING_APPROVAL';
    $('reject').hidden = s.status !== 'PENDING_APPROVAL';
    $('suspend').hidden = s.status !== 'APPROVED';
    $('reinstate').hidden = s.status !== 'SUSPENDED';
    $('reason-label').hidden = !(s.status === 'PENDING_APPROVAL' || s.status === 'APPROVED');
  }

  function addWarning(parent, text) {
    const p = document.createElement('p');
    p.className = 'warning';
    p.textContent = text;
    parent.appendChild(p);
  }

  async function decide(action, needsReason, question, done) {
    if (!current) return;
    const s = current.summary;
    const reason = $('reason').value.trim();
    if (needsReason && !reason) { notice('Please enter a reason. The supplier will see it.', true); return; }
    if (!window.confirm(question.replace('{name}', s.legalName))) return;
    const buttons = document.querySelectorAll('.actions button');
    buttons.forEach((b) => { b.disabled = true; });
    try {
      const body = needsReason ? { reason } : undefined;
      render(await api('POST', '/suppliers/' + encodeURIComponent(s.id) + '/' + action, body, s.version));
      notice(done);
    } catch (err) {
      notice(err.message, true);
    } finally {
      buttons.forEach((b) => { b.disabled = false; });
    }
  }

  $('approve').addEventListener('click', () => decide('approve', false,
    'Approve "{name}"? The supplier can then use all features.', 'Supplier approved. The supplier has been told by email.'));
  $('reject').addEventListener('click', () => decide('reject', true,
    'Reject "{name}"? The supplier can correct the details and send them again.', 'Supplier rejected. The supplier has been told by email.'));
  $('suspend').addEventListener('click', () => decide('suspend', true,
    'Suspend "{name}"? Their business features stop immediately.', 'Supplier suspended. The supplier has been told by email.'));
  $('reinstate').addEventListener('click', () => decide('reinstate', false,
    'Reinstate "{name}"?', 'Supplier reinstated. The supplier has been told by email.'));

  // ------------------------------------------------------------------ start

  api('GET', '/auth/me').then(signedIn).catch(() => show('login-view'));
})();
