import { api } from '../api/client.js';
import { requireAuth, session } from '../auth/session.js';
import { renderAppShell, updateShellUser } from '../components/app-shell.js';
import {
  clearFieldErrors, hideAlert, initPasswordToggles, showAlert, showServerFieldErrors, validateForm, validateOnBlur, withBusyButton,
} from '../components/forms.js';
import {
  validateEmail, validateFullName, validateMatch, validateNewPassword, validateRequired,
} from '../components/validators.js';

/** Display names for the language codes stored in preferences. The full list arrives with Phase 3. */
const LANGUAGE_NAMES = new Intl.DisplayNames(['en'], { type: 'language' });

const profileRules = {
  fullName: (value) => validateFullName(value),
  email: (value) => validateEmail(value),
};

const passwordRules = {
  currentPassword: (value) => validateRequired(value, 'Current password is required.'),
  newPassword: (value) => validateNewPassword(value),
  confirmNewPassword: (value, form) => validateMatch(value, form.elements.newPassword.value),
};

function languageName(code) {
  try { return LANGUAGE_NAMES.of(code) ?? code; } catch { return code; }
}

function setField(name, text) {
  const el = document.querySelector(`[data-field="${name}"]`);
  el.textContent = text;
  el.classList.remove('skeleton');
}

function renderProfile(profileForm, user) {
  profileForm.elements.fullName.value = user.fullName;
  profileForm.elements.email.value = user.email;

  setField('memberSince', new Date(user.createdAt).toLocaleDateString(undefined, { dateStyle: 'long' }));
  const prefs = user.preferences;
  setField('languages', prefs
    ? `${languageName(prefs.preferredSourceLanguage)} → ${languageName(prefs.preferredTargetLanguage)}`
    : 'Not set');
  setField('speechSpeed', prefs ? `${Number(prefs.speechSpeed).toFixed(2)}×` : 'Not set');
}

function setFormEnabled(form, enabled) {
  form.querySelectorAll('input, button').forEach((el) => { el.disabled = !enabled; });
}

async function loadProfile(profileForm) {
  try {
    const user = await api.getProfile();
    session.setUser(user);
    updateShellUser(user);
    renderProfile(profileForm, user);
    setFormEnabled(profileForm, true);
  } catch (error) {
    if (error.status !== 401) showAlert(profileForm, `Could not load your profile. ${error.message}`);
  }
}

function initProfileForm(form) {
  const submit = form.querySelector('[type="submit"]');
  validateOnBlur(form, profileRules);

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    hideAlert(form);
    clearFieldErrors(form);
    if (!validateForm(form, profileRules)) return;

    await withBusyButton(submit, 'Saving…', async () => {
      try {
        const user = await api.updateProfile({
          fullName: form.elements.fullName.value.trim(),
          email: form.elements.email.value.trim(),
        });
        session.setUser(user);
        updateShellUser(user);
        renderProfile(form, user);
        showAlert(form, 'Your details have been saved.', 'success');
      } catch (error) {
        if (!showServerFieldErrors(form, error.fieldErrors)) showAlert(form, error.message);
      }
    });
  });
}

function initPasswordForm(form) {
  const submit = form.querySelector('[type="submit"]');
  initPasswordToggles(form);
  validateOnBlur(form, passwordRules);

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    hideAlert(form);
    clearFieldErrors(form);
    if (!validateForm(form, passwordRules)) return;

    await withBusyButton(submit, 'Updating…', async () => {
      try {
        await api.changePassword({
          currentPassword: form.elements.currentPassword.value,
          newPassword: form.elements.newPassword.value,
          confirmNewPassword: form.elements.confirmNewPassword.value,
        });
        form.reset();
        showAlert(form, 'Your password has been updated.', 'success');
      } catch (error) {
        if (!showServerFieldErrors(form, error.fieldErrors)) showAlert(form, error.message);
      }
    });
  });
}

if (requireAuth()) {
  renderAppShell();
  const profileForm = document.getElementById('profile-form');
  initProfileForm(profileForm);
  initPasswordForm(document.getElementById('password-form'));
  loadProfile(profileForm);
}
