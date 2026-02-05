/**
 * SecureNotes Manager - Frontend Application
 * Handles authentication and note CRUD operations via REST API
 */

const API_BASE = '';

// Token storage keys
const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';

// DOM Elements
const authSection = document.getElementById('auth-section');
const dashboardSection = document.getElementById('dashboard-section');
const loginForm = document.getElementById('login-form');
const registerForm = document.getElementById('register-form');
const noteForm = document.getElementById('note-form');
const noteFormContainer = document.getElementById('note-form-container');
const notesList = document.getElementById('notes-list');
const userInfo = document.getElementById('user-info');
const usernameDisplay = document.getElementById('username-display');

// Initialize app
document.addEventListener('DOMContentLoaded', () => {
    initializeApp();
    setupEventListeners();
});

function initializeApp() {
    const token = getAccessToken();
    if (token) {
        showDashboard();
        loadNotes();
    } else {
        showAuth();
    }
}

function setupEventListeners() {
    // Tab switching
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.addEventListener('click', (e) => switchTab(e.target.dataset.tab));
    });

    // Auth forms
    loginForm.addEventListener('submit', handleLogin);
    registerForm.addEventListener('submit', handleRegister);

    // Note form
    noteForm.addEventListener('submit', handleSaveNote);
    document.getElementById('new-note-btn').addEventListener('click', showNewNoteForm);
    document.getElementById('cancel-note-btn').addEventListener('click', hideNoteForm);

    // Logout
    document.getElementById('logout-btn').addEventListener('click', handleLogout);
}

// Tab switching
function switchTab(tab) {
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.tab === tab);
    });
    
    loginForm.classList.toggle('hidden', tab !== 'login');
    registerForm.classList.toggle('hidden', tab !== 'register');
    
    clearMessages();
}

// Auth handlers
async function handleLogin(e) {
    e.preventDefault();
    clearMessages();

    const email = document.getElementById('login-email').value;
    const password = document.getElementById('login-password').value;

    try {
        const response = await fetch(`${API_BASE}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || 'Login failed');
        }

        saveTokens(data.accessToken, data.refreshToken);
        showDashboard();
        loadNotes();
        loginForm.reset();
    } catch (error) {
        showError('login-error', error.message);
    }
}

async function handleRegister(e) {
    e.preventDefault();
    clearMessages();

    const username = document.getElementById('register-username').value;
    const email = document.getElementById('register-email').value;
    const password = document.getElementById('register-password').value;

    try {
        const response = await fetch(`${API_BASE}/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, email, password })
        });

        const data = await response.json();

        if (!response.ok) {
            if (data.fieldErrors) {
                const messages = data.fieldErrors.map(e => `${e.field}: ${e.message}`).join(', ');
                throw new Error(messages);
            }
            throw new Error(data.message || 'Registration failed');
        }

        showSuccess('register-success', 'Registration successful! Please login.');
        registerForm.reset();
        setTimeout(() => switchTab('login'), 1500);
    } catch (error) {
        showError('register-error', error.message);
    }
}

async function handleLogout() {
    const refreshToken = getRefreshToken();
    
    try {
        if (refreshToken) {
            await fetch(`${API_BASE}/auth/logout`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ refreshToken })
            });
        }
    } catch (error) {
        console.error('Logout error:', error);
    } finally {
        clearTokens();
        showAuth();
    }
}

// Note handlers
async function loadNotes() {
    try {
        const response = await fetchWithAuth(`${API_BASE}/api/notes`);
        
        if (!response.ok) {
            if (response.status === 401) {
                await refreshAccessToken();
                return loadNotes();
            }
            throw new Error('Failed to load notes');
        }

        const notes = await response.json();
        renderNotes(notes);
    } catch (error) {
        console.error('Error loading notes:', error);
        if (error.message === 'Token refresh failed') {
            clearTokens();
            showAuth();
        }
    }
}

function renderNotes(notes) {
    const noNotesMessage = document.getElementById('no-notes-message');
    
    // Clear existing notes (except the no-notes message)
    notesList.querySelectorAll('.note-card').forEach(card => card.remove());

    if (notes.length === 0) {
        noNotesMessage.classList.remove('hidden');
        return;
    }

    noNotesMessage.classList.add('hidden');

    notes.forEach(note => {
        const card = createNoteCard(note);
        notesList.appendChild(card);
    });
}

function createNoteCard(note) {
    const card = document.createElement('div');
    card.className = 'note-card';
    card.dataset.id = note.id;

    const createdAt = new Date(note.createdAt).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });

    card.innerHTML = `
        <div class="note-card-header">
            <h3>${escapeHtml(note.title)}</h3>
            <div class="note-card-actions">
                <button class="btn btn-secondary btn-small edit-btn">Edit</button>
                <button class="btn btn-danger btn-small delete-btn">Delete</button>
            </div>
        </div>
        <div class="note-card-content">${escapeHtml(note.content || '')}</div>
        <div class="note-card-meta">Created: ${createdAt}</div>
    `;

    card.querySelector('.edit-btn').addEventListener('click', () => showEditNoteForm(note));
    card.querySelector('.delete-btn').addEventListener('click', () => handleDeleteNote(note.id));

    return card;
}

function showNewNoteForm() {
    document.getElementById('note-id').value = '';
    document.getElementById('note-title').value = '';
    document.getElementById('note-content').value = '';
    noteFormContainer.classList.remove('hidden');
    document.getElementById('note-title').focus();
}

function showEditNoteForm(note) {
    document.getElementById('note-id').value = note.id;
    document.getElementById('note-title').value = note.title;
    document.getElementById('note-content').value = note.content || '';
    noteFormContainer.classList.remove('hidden');
    document.getElementById('note-title').focus();
}

function hideNoteForm() {
    noteFormContainer.classList.add('hidden');
    noteForm.reset();
    clearMessages();
}

async function handleSaveNote(e) {
    e.preventDefault();
    clearMessages();

    const noteId = document.getElementById('note-id').value;
    const title = document.getElementById('note-title').value;
    const content = document.getElementById('note-content').value;

    const isEdit = !!noteId;
    const url = isEdit ? `${API_BASE}/api/notes/${noteId}` : `${API_BASE}/api/notes`;
    const method = isEdit ? 'PUT' : 'POST';

    try {
        const response = await fetchWithAuth(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title, content })
        });

        const data = await response.json();

        if (!response.ok) {
            if (data.fieldErrors) {
                const messages = data.fieldErrors.map(e => `${e.field}: ${e.message}`).join(', ');
                throw new Error(messages);
            }
            throw new Error(data.message || 'Failed to save note');
        }

        hideNoteForm();
        loadNotes();
    } catch (error) {
        showError('note-error', error.message);
    }
}

async function handleDeleteNote(noteId) {
    if (!confirm('Are you sure you want to delete this note?')) {
        return;
    }

    try {
        const response = await fetchWithAuth(`${API_BASE}/api/notes/${noteId}`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            throw new Error('Failed to delete note');
        }

        loadNotes();
    } catch (error) {
        alert('Error deleting note: ' + error.message);
    }
}

// Token management
function saveTokens(accessToken, refreshToken) {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

function getAccessToken() {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
}

function getRefreshToken() {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
}

function clearTokens() {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
}

async function refreshAccessToken() {
    const refreshToken = getRefreshToken();
    
    if (!refreshToken) {
        throw new Error('Token refresh failed');
    }

    try {
        const response = await fetch(`${API_BASE}/auth/refresh`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken })
        });

        if (!response.ok) {
            throw new Error('Token refresh failed');
        }

        const data = await response.json();
        saveTokens(data.accessToken, data.refreshToken);
    } catch (error) {
        clearTokens();
        throw error;
    }
}

async function fetchWithAuth(url, options = {}) {
    const token = getAccessToken();
    
    const headers = {
        ...options.headers,
        'Authorization': `Bearer ${token}`
    };

    const response = await fetch(url, { ...options, headers });

    if (response.status === 401) {
        try {
            await refreshAccessToken();
            const newToken = getAccessToken();
            headers['Authorization'] = `Bearer ${newToken}`;
            return fetch(url, { ...options, headers });
        } catch (error) {
            clearTokens();
            showAuth();
            throw error;
        }
    }

    return response;
}

// UI helpers
function showAuth() {
    authSection.classList.remove('hidden');
    dashboardSection.classList.add('hidden');
    userInfo.classList.add('hidden');
}

function showDashboard() {
    authSection.classList.add('hidden');
    dashboardSection.classList.remove('hidden');
    userInfo.classList.remove('hidden');
    
    // Decode JWT to get username
    const token = getAccessToken();
    if (token) {
        try {
            const payload = JSON.parse(atob(token.split('.')[1]));
            usernameDisplay.textContent = payload.sub || 'User';
        } catch (e) {
            usernameDisplay.textContent = 'User';
        }
    }
}

function showError(elementId, message) {
    const element = document.getElementById(elementId);
    element.textContent = message;
    element.classList.remove('hidden');
}

function showSuccess(elementId, message) {
    const element = document.getElementById(elementId);
    element.textContent = message;
    element.classList.remove('hidden');
}

function clearMessages() {
    document.querySelectorAll('.error-message, .success-message').forEach(el => {
        el.classList.add('hidden');
        el.textContent = '';
    });
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
