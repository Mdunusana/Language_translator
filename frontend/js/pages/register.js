import { api } from '../api/client.js';
import { HOME_PAGE, redirectIfAuthenticated, session } from '../auth/session.js';
import {
  clearFieldErrors, hideAlert, initPasswordToggles, showAlert, showServerFieldErrors, validateForm, validateOnBlur, withBusyButton,
} from '../components/forms.js';
import { validateEmail, validateFullName, validateMatch, validateNewPassword } from '../components/validators.js';

const rules = {
  fullName: (value) => validateFullName(value),
  email: (value) => validateEmail(value),
  password: (value) => validateNewPassword(value),
  confirmPassword: (value, form) => validateMatch(value, form.elements.password.value),
};

if (!redirectIfAuthenticated()) {
  const form = document.getElementById('register-form');
  const submit = form.querySelector('[type="submit"]');

  initPasswordToggles(form);
  validateOnBlur(form, rules);

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    hideAlert(form);
    clearFieldErrors(form);
    if (!validateForm(form, rules)) return;

    await withBusyButton(submit, 'Creating account…', async () => {
      try {
        const auth = await api.register({
          fullName: form.elements.fullName.value.trim(),
          email: form.elements.email.value.trim(),
          password: form.elements.password.value,
          confirmPassword: form.elements.confirmPassword.value,
        });
        session.start(auth);
        window.location.assign(HOME_PAGE);
      } catch (error) {
        if (!showServerFieldErrors(form, error.fieldErrors)) showAlert(form, error.message);
      }
    });
  });

  form.elements.fullName.focus();
}
