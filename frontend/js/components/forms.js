// Accessible form helpers: inline field errors, a form-level alert, busy buttons and password toggles.
// All text is set with textContent, never innerHTML, so server or user input cannot inject markup.

/** Shows (or clears, with '') the error for one input. Expects <p class="field-error" id="{input.id}-error">. */
export function setFieldError(input, message) {
  const errorEl = document.getElementById(`${input.id}-error`);
  input.setAttribute('aria-invalid', message ? 'true' : 'false');
  if (errorEl) {
    errorEl.textContent = message;
    errorEl.hidden = !message;
  }
}

/**
 * Runs validators against form inputs. `rules` maps an input name to a function (value, form) => message.
 * Returns true when everything is valid and focuses the first invalid field otherwise.
 */
export function validateForm(form, rules) {
  let firstInvalid = null;
  for (const [name, rule] of Object.entries(rules)) {
    const input = form.elements.namedItem(name);
    const message = rule(input.value, form);
    setFieldError(input, message);
    if (message && !firstInvalid) firstInvalid = input;
  }
  firstInvalid?.focus();
  return firstInvalid === null;
}

/** Re-validates a field when the user leaves it, and clears its error as soon as it becomes valid. */
export function validateOnBlur(form, rules) {
  for (const [name, rule] of Object.entries(rules)) {
    const input = form.elements.namedItem(name);
    input.addEventListener('blur', () => {
      if (input.value) setFieldError(input, rule(input.value, form));
    });
    input.addEventListener('input', () => {
      if (input.getAttribute('aria-invalid') === 'true' && !rule(input.value, form)) setFieldError(input, '');
    });
  }
}

/** Maps backend fieldErrors ({ email: '...' }) onto matching inputs. Returns true if any were shown. */
export function showServerFieldErrors(form, fieldErrors) {
  let shown = false;
  let first = null;
  for (const [name, message] of Object.entries(fieldErrors ?? {})) {
    const input = form.elements.namedItem(name);
    if (input) {
      setFieldError(input, message);
      first ??= input;
      shown = true;
    }
  }
  first?.focus();
  return shown;
}

export function clearFieldErrors(form) {
  form.querySelectorAll('[aria-invalid]').forEach((input) => setFieldError(input, ''));
}

/** Form-level message box: <div class="alert" data-form-alert hidden>. type: 'error' | 'success' | 'info' */
export function showAlert(form, message, type = 'error') {
  const alert = form.querySelector('[data-form-alert]');
  if (!alert) return;
  alert.textContent = message;
  alert.dataset.type = type;
  alert.hidden = !message;
  alert.setAttribute('role', type === 'error' ? 'alert' : 'status');
}

export function hideAlert(form) {
  showAlert(form, '');
}

/** Disables a submit button and shows a spinner while `task` runs. */
export async function withBusyButton(button, busyLabel, task) {
  const label = button.querySelector('[data-label]') ?? button;
  const original = label.textContent;
  button.disabled = true;
  button.setAttribute('aria-busy', 'true');
  button.classList.add('is-busy');
  label.textContent = busyLabel;
  try {
    return await task();
  } finally {
    button.disabled = false;
    button.removeAttribute('aria-busy');
    button.classList.remove('is-busy');
    label.textContent = original;
  }
}

/** Wires every [data-password-toggle] button to show/hide the password input it controls. */
export function initPasswordToggles(root = document) {
  root.querySelectorAll('[data-password-toggle]').forEach((button) => {
    const input = document.getElementById(button.getAttribute('aria-controls'));
    button.addEventListener('click', () => {
      const show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      button.setAttribute('aria-pressed', String(show));
      button.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
    });
  });
}
