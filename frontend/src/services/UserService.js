import axios from 'axios';

const baseUrl = 'http://localhost:8080';

export function registerUser(user) {
  return axios.post(baseUrl + '/api/auth/register', user);
}

export function loginUser(user) {
  return axios.post(baseUrl + '/api/auth/login', user);
}

export function verifyUser(verifyData) {
  return axios.post(baseUrl + '/api/auth/verify', verifyData);
}

export function resendVerificationCode(email) {
  return axios.post(baseUrl + '/api/auth/resend', email, {
    headers: { 'Content-Type': 'text/plain' }
  });
}
