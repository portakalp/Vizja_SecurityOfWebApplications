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
const noteDetailSection = document.getElementById('note-detail-section');
const loginForm = document.getElementById('login-form');
const registerForm = document.getElementById('register-form');
const noteForm = document.getElementById('note-form');
const noteFormContainer = document.getElementById('note-form-container');
const notesList = document.getElementById('notes-list');
const userInfo = document.getElementById('user-info');
const usernameDisplay = document.getElementById('username-display');

// Current note being viewed/edited
let currentNote = null;
let notesCache = [];

// Initialize app
document.addEventListener('DOMContentLoaded', () => {
    initializeApp();
    setupEventListeners();
    setupRouter();
});

function initializeApp() {
    const token = getAccessToken();
    if (token) {
        handleRoute();
    } else {
        showAuth();
    }
}

// Router functions
function setupRouter() {
    window.addEventListener('popstate', handleRoute);
}

function handleRoute() {
    const token = getAccessToken();
    
    // If no token, show auth and redirect to home
    if (!token) {
        showAuth();
        return;
    }
    
    const path = window.location.pathname;
    const noteMatch = path.match(/^\/notes\/([a-f0-9-]+)$/i);
    
    if (noteMatch) {
        const noteId = noteMatch[1];
        loadAndShowNote(noteId);
    } else {
        showDashboard();
        loadNotes();
    }
}

async function loadAndShowNote(noteId) {
    try {
        const response = await fetchWithAuth(`${API_BASE}/api/notes/${noteId}`);
        
        if (!response.ok) {
            if (response.status === 404) {
                alert('Note not found');
                navigateTo('/');
                return;
            }
            throw new Error('Failed to load note');
        }
        
        const note = await response.json();
        showNoteDetailView(note);
    } catch (error) {
        console.error('Error loading note:', error);
        navigateTo('/');
    }
}

function navigateTo(path) {
    history.pushState(null, '', path);
    handleRoute();
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
    
    // Note detail section
    document.getElementById('back-to-dashboard').addEventListener('click', showDashboardFromDetail);
    document.getElementById('toggle-edit-btn').addEventListener('click', toggleEditMode);
    document.getElementById('delete-note-detail-btn').addEventListener('click', handleDeleteFromDetail);
    document.getElementById('note-edit-form').addEventListener('submit', handleSaveEditedNote);
    document.getElementById('cancel-edit-btn').addEventListener('click', cancelEditMode);
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
    card.className = 'note-card note-card-preview';
    card.dataset.id = note.id;

    const createdAt = new Date(note.createdAt).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
    });

    // Truncate content for preview
    const previewContent = note.content ? 
        (note.content.length > 150 ? note.content.substring(0, 150) + '...' : note.content) : 
        'No content';

    card.innerHTML = `
        <div class="note-card-header">
            <div class="note-title-section">
                <h3 class="note-preview-title">${escapeHtml(note.title)}</h3>
            </div>
        </div>
        <div class="note-card-content note-preview-content">${escapeHtml(previewContent)}</div>
        <div class="note-card-meta">Created: ${createdAt}</div>
    `;

    // Click on card to open detail view
    card.addEventListener('click', () => {
        navigateTo(`/notes/${note.id}`);
    });

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

// Note Detail View Functions
function showNoteDetailView(note) {
    currentNote = note;
    
    const formatDate = (dateStr) => new Date(dateStr).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
    
    // Populate view mode
    document.getElementById('detail-note-title').textContent = note.title;
    // ID is visible in URL, no need to display it
    document.getElementById('detail-note-content').textContent = note.content || 'No content';
    document.getElementById('detail-note-created').textContent = formatDate(note.createdAt);
    document.getElementById('detail-note-updated').textContent = formatDate(note.updatedAt);
    
    // Populate edit mode
    document.getElementById('edit-note-title').value = note.title;
    document.getElementById('edit-note-content').value = note.content || '';
    
    // Show view mode by default
    document.getElementById('note-view-mode').classList.remove('hidden');
    document.getElementById('note-edit-mode').classList.add('hidden');
    document.getElementById('toggle-edit-btn').textContent = '✏️ Edit';
    
    // Switch to detail section
    authSection.classList.add('hidden');
    dashboardSection.classList.add('hidden');
    noteDetailSection.classList.remove('hidden');
    userInfo.classList.remove('hidden');
    
    // Decode JWT to get username and roles
    const token = getAccessToken();
    if (token) {
        try {
            const payload = JSON.parse(atob(token.split('.')[1]));
            usernameDisplay.textContent = payload.sub || 'User';
            updateAdminBadge(payload.roles || []);
        } catch (e) {
            usernameDisplay.textContent = 'User';
            updateAdminBadge([]);
        }
    }
}

function showDashboardFromDetail() {
    navigateTo('/');
}

function toggleEditMode() {
    const viewMode = document.getElementById('note-view-mode');
    const editMode = document.getElementById('note-edit-mode');
    const toggleBtn = document.getElementById('toggle-edit-btn');
    
    if (viewMode.classList.contains('hidden')) {
        // Switch to view mode
        viewMode.classList.remove('hidden');
        editMode.classList.add('hidden');
        toggleBtn.textContent = '✏️ Edit';
    } else {
        // Switch to edit mode
        viewMode.classList.add('hidden');
        editMode.classList.remove('hidden');
        toggleBtn.textContent = '👁️ View';
    }
}

function cancelEditMode() {
    // Reset edit form to current note values
    document.getElementById('edit-note-title').value = currentNote.title;
    document.getElementById('edit-note-content').value = currentNote.content || '';
    
    // Switch back to view mode
    document.getElementById('note-view-mode').classList.remove('hidden');
    document.getElementById('note-edit-mode').classList.add('hidden');
    document.getElementById('toggle-edit-btn').textContent = '✏️ Edit';
    clearMessages();
}

async function handleSaveEditedNote(e) {
    e.preventDefault();
    clearMessages();
    
    const title = document.getElementById('edit-note-title').value;
    const content = document.getElementById('edit-note-content').value;
    
    try {
        const response = await fetchWithAuth(`${API_BASE}/api/notes/${currentNote.id}`, {
            method: 'PUT',
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
        
        // Update current note and view
        currentNote = data;
        showNoteDetailView(data);
    } catch (error) {
        showError('edit-note-error', error.message);
    }
}

async function handleDeleteFromDetail() {
    if (!confirm('Are you sure you want to delete this note?')) {
        return;
    }
    
    try {
        const response = await fetchWithAuth(`${API_BASE}/api/notes/${currentNote.id}`, {
            method: 'DELETE'
        });
        
        if (!response.ok) {
            throw new Error('Failed to delete note');
        }
        
        showDashboardFromDetail();
    } catch (error) {
        alert('Error deleting note: ' + error.message);
    }
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
    noteDetailSection.classList.add('hidden');
    userInfo.classList.add('hidden');
    currentNote = null;
}

function showDashboard() {
    authSection.classList.add('hidden');
    dashboardSection.classList.remove('hidden');
    noteDetailSection.classList.add('hidden');
    userInfo.classList.remove('hidden');
    
    // Decode JWT to get username and roles
    const token = getAccessToken();
    if (token) {
        try {
            const payload = JSON.parse(atob(token.split('.')[1]));
            usernameDisplay.textContent = payload.sub || 'User';
            updateAdminBadge(payload.roles || []);
        } catch (e) {
            usernameDisplay.textContent = 'User';
            updateAdminBadge([]);
        }
    }
}

function updateAdminBadge(roles) {
    const adminBadge = document.getElementById('admin-badge');
    if (roles.includes('ROLE_ADMIN')) {
        adminBadge.classList.remove('hidden');
    } else {
        adminBadge.classList.add('hidden');
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
