import { api } from '../api/client.js';
import { requireAuth, session } from '../auth/session.js';
import { renderAppShell, updateShellUser } from '../components/app-shell.js';
import {
  clearFieldErrors, hideAlert, initPasswordToggles, showAlert, showServerFieldErrors, validateForm, validateOnBlur, withBusyButton,
} from '../components/forms.js';
import { fillLanguageOptions, languageName } from '../components/languages.js';
import {
  validateEmail, validateFullName, validateMatch, validateNewPassword, validateRequired,
} from '../components/validators.js';

/** Device-level translator memory (see translator.js). Cleared when account preferences are saved. */
const DEVICE_OVERRIDE_KEYS = ['linguavoice.lastLanguages', 'linguavoice.speechSpeed'];

const forms = {
  profile: document.getElementById('profile-form'),
  preferences: document.getElementById('preferences-form'),
  password: document.getElementById('password-form'),
};

const profileRules = {
  fullName: (value) => validateFullName(value),
  email: (value) => validateEmail(value),
};

const preferenceRules = {
  preferredTargetLanguage: (value, form) => (value === form.elements.preferredSourceLanguage.value
    ? 'Choose a target language that is different from the source language.'
    : ''),
};

const passwordRules = {
  currentPassword: (value) => validateRequired(value, 'Current password is required.'),
  newPassword: (value) => validateNewPassword(value),
  confirmNewPassword: (value, form) => validateMatch(value, form.elements.newPassword.value),
};

function setField(name, text) {
  const el = document.querySelector(`[data-field="${name}"]`);
  el.textContent = text;
  el.classList.remove('skeleton');
}

function formatSpeed(speed) {
  return `${Number(speed).toFixed(2).replace(/\.?0+$/, '')}×`;
}

/** Shows the user in the overview card and fills both editable forms. */
function render(user) {
  forms.profile.elements.fullName.value = user.fullName;
  forms.profile.elements.email.value = user.email;
  setField('memberSince', new Date(user.createdAt).toLocaleDateString(undefined, { dateStyle: 'long' }));

  const prefs = user.preferences;
  setField('languages', prefs
    ? `${languageName(prefs.preferredSourceLanguage)} → ${languageName(prefs.preferredTargetLanguage)}`
    : 'Not set');
  setField('speechSpeed', prefs ? formatSpeed(prefs.speechSpeed) : 'Not set');
  if (prefs) {
    forms.preferences.elements.preferredSourceLanguage.value = prefs.preferredSourceLanguage;
    forms.preferences.elements.preferredTargetLanguage.value = prefs.preferredTargetLanguage;
    const speed = Number(prefs.speechSpeed).toFixed(2);
    const speedSelect = forms.preferences.elements.speechSpeed;
    if (![...speedSelect.options].some((option) => option.value === speed)) {
      speedSelect.append(Object.assign(document.createElement('option'), { value: speed, textContent: formatSpeed(speed) }));
    }
    speedSelect.value = speed;
  }
}

function setFormEnabled(form, enabled) {
  form.querySelectorAll('input, select, button').forEach((el) => { el.disabled = !enabled; });
}

function applyUser(user) {
  session.setUser(user);
  updateShellUser(user);
  render(user);
}

async function loadProfile() {
  try {
    applyUser(await api.getProfile());
    setFormEnabled(forms.profile, true);
    setFormEnabled(forms.preferences, true);
  } catch (error) {
    if (error.status !== 401) showAlert(forms.profile, `Could not load your profile. ${error.message}`);
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
        applyUser(await api.updateProfile({
          fullName: form.elements.fullName.value.trim(),
          email: form.elements.email.value.trim(),
        }));
        showAlert(form, 'Your details have been saved.', 'success');
      } catch (error) {
        if (!showServerFieldErrors(form, error.fieldErrors)) showAlert(form, error.message);
      }
    });
  });
}

function initPreferencesForm(form) {
  fillLanguageOptions(form.elements.preferredSourceLanguage);
  fillLanguageOptions(form.elements.preferredTargetLanguage);
  const submit = form.querySelector('[type="submit"]');

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    hideAlert(form);
    clearFieldErrors(form);
    if (!validateForm(form, preferenceRules)) return;

    await withBusyButton(submit, 'Saving…', async () => {
      try {
        applyUser(await api.updatePreferences({
          preferredSourceLanguage: form.elements.preferredSourceLanguage.value,
          preferredTargetLanguage: form.elements.preferredTargetLanguage.value,
          speechSpeed: Number(form.elements.speechSpeed.value),
        }));
        // The translator prefers what was last used on this device; saved preferences should win next time.
        DEVICE_OVERRIDE_KEYS.forEach((key) => { try { localStorage.removeItem(key); } catch { /* not important */ } });
        showAlert(form, 'Preferences saved. The translator will use them from now on.', 'success');
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
  initProfileForm(forms.profile);
  initPreferencesForm(forms.preferences);
  initPasswordForm(forms.password);
  loadProfile();
}
