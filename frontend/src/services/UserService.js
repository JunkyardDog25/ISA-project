import axios from 'axios';

const baseUrl = 'http://localhost:8080';

export function registerUser(user) {
  return axios.post(baseUrl + '/api/register', user);
}
