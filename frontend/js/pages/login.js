import { api } from '../api/client.js';
import { postLoginDestination, redirectIfAuthenticated, session } from '../auth/session.js';
import {
  clearFieldErrors, hideAlert, initPasswordToggles, showAlert, showServerFieldErrors, validateForm, validateOnBlur, withBusyButton,
} from '../components/forms.js';
import { validateEmail, validateRequired } from '../components/validators.js';

const REASON_MESSAGES = {
  expired: ['Your session has expired. Please log in again.', 'info'],
  'auth-required': ['Please log in to continue.', 'info'],
  'signed-out': ['You have been signed out.', 'success'],
};

const rules = {
  email: (value) => validateEmail(value),
  password: (value) => validateRequired(value, 'Password is required.'),
};

if (!redirectIfAuthenticated()) {
  const form = document.getElementById('login-form');
  const submit = form.querySelector('[type="submit"]');

  initPasswordToggles(form);
  validateOnBlur(form, rules);

  const reason = new URLSearchParams(window.location.search).get('reason');
  if (REASON_MESSAGES[reason]) showAlert(form, ...REASON_MESSAGES[reason]);

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    hideAlert(form);
    clearFieldErrors(form);
    if (!validateForm(form, rules)) return;

    await withBusyButton(submit, 'Logging in…', async () => {
      try {
        const auth = await api.login({
          email: form.elements.email.value.trim(),
          password: form.elements.password.value,
        });
        session.start(auth);
        window.location.assign(postLoginDestination());
      } catch (error) {
        form.elements.password.value = '';
        if (!showServerFieldErrors(form, error.fieldErrors) || error.status === 401) {
          showAlert(form, error.message);
        }
        if (error.status === 401) form.elements.password.focus();
      }
    });
  });

  form.elements.email.focus();
}
