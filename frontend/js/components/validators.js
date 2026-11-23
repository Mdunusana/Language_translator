// Pure validation functions mirroring the backend's Bean Validation rules (dto/ValidationRules.java).
// Each returns an error message, or '' when the value is valid. The backend re-validates everything.

export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 72;

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateFullName(value) {
  const name = value.trim();
  if (!name) return 'Full name is required.';
  if (name.length < 2 || name.length > 100) return 'Full name must be between 2 and 100 characters.';
  return '';
}

export function validateEmail(value) {
  const email = value.trim();
  if (!email) return 'Email is required.';
  if (email.length > 255 || !EMAIL_PATTERN.test(email)) return 'Enter a valid email address.';
  return '';
}

export function validateNewPassword(value) {
  if (!value) return 'Password is required.';
  if (value.length < PASSWORD_MIN || value.length > PASSWORD_MAX) {
    return `Password must be between ${PASSWORD_MIN} and ${PASSWORD_MAX} characters.`;
  }
  if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) {
    return 'Password must contain at least one letter and one number.';
  }
  return '';
}

export function validateRequired(value, message) {
  return value.trim() ? '' : message;
}

export function validateMatch(value, other, message = 'Passwords do not match.') {
  if (!value) return 'Please confirm your password.';
  return value === other ? '' : message;
}
