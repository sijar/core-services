const message = document.querySelector('#message');
const authPanel = document.querySelector('#auth');
const accountPanel = document.querySelector('#account');

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body) headers.set('Content-Type', 'application/json');
  if (options.method && options.method !== 'GET') {
    const csrf = await fetch('/api/csrf').then(response => response.json());
    headers.set(csrf.headerName, csrf.token);
  }
  const response = await fetch(path, { ...options, headers, credentials: 'same-origin' });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) throw new Error(body?.error || `Request failed (${response.status})`);
  return body;
}

function formData(form) { return Object.fromEntries(new FormData(form).entries()); }
function showMessage(text) { message.textContent = text; }

async function refresh() {
  try {
    const user = await request('/api/me');
    authPanel.hidden = true;
    accountPanel.hidden = false;
    document.querySelector('#profile').textContent = JSON.stringify(user, null, 2);
    const [preferences, payments, notifications, phone] = await Promise.all([
      request('/api/notifications/preferences'), request('/api/payments'), request('/api/notifications'), request('/api/phone/verification')
    ]);
    for (const [channel, enabled] of Object.entries(preferences)) document.querySelector(`[name="${channel}"]`).checked = enabled;
    document.querySelector('#payments').replaceChildren(...payments.map(item => listItem(`${item.productCode}: ${item.status} (${item.amountMinor} ${item.currency})`)));
    document.querySelector('#notifications').replaceChildren(...notifications.map(item => listItem(`${item.type}: ${item.status}`)));
    showMessage(phone.verified ? `Phone verified: ${phone.phoneNumber}` : 'Signed in');
  } catch {
    authPanel.hidden = false;
    accountPanel.hidden = true;
  }
}

function listItem(text) { const li = document.createElement('li'); li.textContent = text; return li; }
async function submit(form, path, success) {
  form.addEventListener('submit', async event => {
    event.preventDefault();
    try {
      await request(path, { method: 'POST', body: JSON.stringify(formData(form)) });
      showMessage(success);
      form.reset();
      await refresh();
    } catch (error) { showMessage(error.message); }
  });
}

submit(document.querySelector('#register'), '/api/auth/register', 'Account created; sign in to continue.');
submit(document.querySelector('#login'), '/api/auth/login', 'Signed in.');
submit(document.querySelector('#reset-request'), '/api/auth/password-reset/request', 'If that account exists, reset instructions will be sent.');
submit(document.querySelector('#reset-confirm'), '/api/auth/password-reset/confirm', 'Password reset. Sign in with your new password.');
submit(document.querySelector('#phone-start'), '/api/phone/verification/start', 'Verification code requested.');
submit(document.querySelector('#phone-confirm'), '/api/phone/verification/confirm', 'Phone verified.');

document.querySelector('#logout').addEventListener('click', async () => {
  try { await request('/api/auth/logout', { method: 'POST' }); showMessage('Signed out.'); await refresh(); }
  catch (error) { showMessage(error.message); }
});

document.querySelector('#preferences').addEventListener('submit', async event => {
  event.preventDefault();
  const preferences = Object.fromEntries([...event.currentTarget.querySelectorAll('input[type="checkbox"]')].map(input => [input.name, input.checked]));
  try { await request('/api/notifications/preferences', { method: 'PUT', body: JSON.stringify({ preferences }) }); showMessage('Preferences saved.'); }
  catch (error) { showMessage(error.message); }
});

document.querySelector('#pay').addEventListener('click', async () => {
  try {
    const payment = await request('/api/payments', { method: 'POST', body: JSON.stringify({ productCode: 'demo-item' }) });
    window.location.assign(payment.checkoutUrl);
  } catch (error) { showMessage(error.message); }
});

const resetToken = new URLSearchParams(window.location.hash.slice(1)).get('resetToken');
if (resetToken) {
  document.querySelector('#reset-confirm [name="token"]').value = resetToken;
  window.history.replaceState(null, '', window.location.pathname + window.location.search);
}
refresh();
