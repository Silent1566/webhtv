const icDir = `data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23F5A623'><path d='M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z'/></svg>`;
const icFile = `data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23717970'><path d='M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11z'/></svg>`;
const icTextFile = `data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><path fill='%232F7D4F' d='M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6z'/><path fill='%23FFFFFF' d='M13 4v5h5zM8 12h8v1.6H8zM8 15h8v1.6H8zM8 18h5v1.6H8z'/></svg>`;
const icBinaryFile = `data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><path fill='%237B1E3A' d='M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6z'/><path fill='%23FFFFFF' d='M13 4v5h5zM8 12h3v3H8zM13 12h3v3h-3zM8 17h3v3H8zM13 17h3v3h-3z'/></svg>`;

let mode = 'local';
let currentView = 'files';
let target = '';
let targetName = '';
let currentRoot = '';
let currentParent = '';
let currentFile = '';
let fileTreeExpanded = new Set(['']);
let fileTreeCache = {};
let fileTreeLoading = new Set();
let pendingDelFolder = null;
let warnToastTimer = null;
let syncPaths = [];
let syncLoadedKey = '';
let syncTreeExpanded = new Set(['']);
let syncTreeCache = {};
let syncTreeLoading = new Set();
let loginStateLoadedKey = '';
let loginStateData = null;
let loginStatePaths = [];
let currentLoginStatePath = '';
let currentLoginStateEditable = false;
let loginStateExpanded = new Set(['app', 'sdcard']);
let loginStateTreeCache = {};
let loginStateTreeLoading = new Set();
let cspRegistry = null;
let cspLoadedKey = '';
let cspRawDirty = false;
let cspMode = 'form';
let pendingCspIndex = -1;
let proxyLoadedKey = '';
let dialogClosing = false;
let loadingCount = 0;
let heartbeatTimer = null;
let remoteHealthTimer = null;
let remoteHealth = {};
let remoteHealthPending = {};
let fileSelection = new Set();
let currentFiles = [];
let devicePanelOpen = false;
let syncMode = 'push';
let proxyEnabled = false;
let proxyMode = 'form';
let proxyRules = [];
let proxySuggestSites = [];
let configsLoadedKey = '';
let configsData = [];
let configFilter = 0;
let editingConfig = null;

const REQUEST_TIMEOUT = 12000;
const FILE_TIMEOUT = 15000;
const UPLOAD_TIMEOUT = 60000;
const SYNC_TIMEOUT = 600000;
const REMOTE_HEALTH_INTERVAL = 6000;
const REMOTE_HEALTH_BLOCK_MS = 18000;
const CONFIG_UPLOAD_DIR = 'WebHTV/Config';
const LOGIN_TEXTAREA_LIMIT = 32 * 1024;
const LOGIN_TEXTAREA_LINE_LIMIT = 1600;
const LOGIN_PREVIEW_ROW_CHARS = 1200;

function escPath(s) { return String(s || '').replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/\\/g, '\\\\').replace(/'/g, "\\'"); }
function escHtml(s) { return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
function itemId(path) { return String(path || '').replace(/[^a-zA-Z0-9_-]/g, '_'); }
function remoteManageActive() { return mode === 'remote' && currentView !== 'sync' && !!target; }
function targetParam() { return remoteManageActive() ? { target } : {}; }
function targetQuery(extra = {}) { return new URLSearchParams({ ...targetParam(), ...extra }).toString(); }
function activeKey() { return mode + ':' + target; }
function healthKey(url = target) { return String(url || '').replace(/\/+$/, ''); }

function fileApi(path, download = false) {
    path = fileRequestPath(path);
    if (mode === 'remote' && target) return '/manage/remote/file?' + new URLSearchParams({ target, path, download: download ? '1' : '' }).toString();
    const encoded = path.split('/').map(encodeURIComponent).join('/');
    return '/file' + encoded + (download ? '?download=1' : '');
}

function fileRequestPath(path) {
    const value = String(path || '').trim();
    if (!value) return '';
    return value.startsWith('/') ? value : '/' + value;
}

function archiveApi() {
    return mode === 'remote' && target ? '/manage/remote/archive' : '/manage/file/archive';
}

function heartbeat(close = false) {
    $.ajax({ url: '/manage/session', type: 'post', data: close ? { close: 'true' } : {}, timeout: 3000, cache: false })
        .done(res => {
            const data = parseJson(res);
            renderServiceStatus(data);
        })
        .fail(() => {
            $('#serviceStatus').text('连接异常').addClass('off').removeClass('warn');
            $('#keepAliveHint').css('display', 'flex');
        });
}

function renderServiceStatus(data) {
    const running = !!(data && data.running && data.serverRunning !== false);
    const optimized = !!(data && (data.backgroundSettingsNeeded != null ? data.backgroundSettingsNeeded : data.batteryOptimized));
    const missingLock = !!(data && running && (!data.wakeLock || !data.wifiLock));
    const text = !running ? '页面已关闭' : optimized ? '后台设置' : missingLock ? '保活异常' : '页面运行中';
    const title = running ? `后台受限: ${optimized ? '是' : '否'}，CPU锁: ${data.wakeLock ? '是' : '否'}，Wi-Fi锁: ${data.wifiLock ? '是' : '否'}` : '管理页服务未运行';
    $('#serviceStatus').text(text).toggleClass('off', !running).toggleClass('warn', running && (optimized || missingLock)).attr('title', title);
    $('#backgroundGuideText').text(data && data.backgroundGuide ? data.backgroundGuide : 'App 进入后台后可能断开连接，请允许后台高耗电或后台运行。');
    $('#keepAliveHint').css('display', running && optimized ? 'flex' : 'none');
}

function startHeartbeat() {
    if (heartbeatTimer) clearInterval(heartbeatTimer);
    heartbeat(false);
    heartbeatTimer = setInterval(() => heartbeat(false), 20000);
}

function startRemoteHealth() {
    if (remoteHealthTimer) clearInterval(remoteHealthTimer);
    remoteHealthTimer = null;
    if (!target || !(mode === 'remote' || currentView === 'sync')) {
        updateTargetHealthUi();
        return;
    }
    pingRemote(true);
    remoteHealthTimer = setInterval(() => pingRemote(false), REMOTE_HEALTH_INTERVAL);
}

function pingRemote(force = false, callback) {
    const key = healthKey();
    if (!key) { if (callback) callback(false); return; }
    if (remoteHealthPending[key]) {
        if (callback) setTimeout(() => {
            const state = targetHealth(key);
            if (state && state.ok != null && !state.checking) callback(!!state.ok);
            else pingRemote(force, callback);
        }, 500);
        return;
    }
    remoteHealthPending[key] = true;
    if (!remoteHealth[key]) remoteHealth[key] = { checking: true, time: 0 };
    else remoteHealth[key].checking = true;
    updateTargetHealthUi();
    $.ajax({ url: '/manage/remote/ping', data: { target: key }, timeout: 1800, cache: false })
        .done(res => {
            let data = {};
            try { data = parseJson(res); } catch (e) {}
            const device = data.device || {};
            remoteHealth[key] = {
                ok: !!data.ok,
                checking: false,
                time: Date.now(),
                message: data.message || '',
                name: device.name || ''
            };
            if (target === key && device.name && !targetName) targetName = device.name;
        })
        .fail((xhr, status) => {
            remoteHealth[key] = { ok: false, checking: false, time: Date.now(), message: status || 'failed' };
        })
        .always(() => {
            remoteHealthPending[key] = false;
            updateTargetText();
            updateTargetHealthUi();
            renderDeviceHealth();
            if (callback) callback(!!(remoteHealth[key] && remoteHealth[key].ok));
        });
}

function targetHealth(url = target) {
    return remoteHealth[healthKey(url)] || null;
}

function isRemoteOffline(url = target) {
    const state = targetHealth(url);
    return !!(state && state.ok === false && Date.now() - state.time < REMOTE_HEALTH_BLOCK_MS);
}

function isRemoteRequest(url, data) {
    if (url === '/manage/remote/ping') return false;
    if (data && data.target) return true;
    if (String(url || '').includes('target=')) return true;
    return String(url || '').startsWith('/manage/remote/');
}

function blockOfflineRemote(url, data) {
    const remote = data && data.target ? data.target : target;
    if (!isRemoteRequest(url, data) || !isRemoteOffline(remote)) return false;
    warnToast('远端设备离线，已停止操作');
    pingRemote(true);
    return true;
}

function updateTargetHealthUi() {
    const state = targetHealth();
    const selected = !!target;
    const offline = selected && state && state.ok === false;
    const online = selected && state && state.ok === true;
    const checking = selected && (!state || state.checking);
    $('#remotePicker').toggleClass('remote-offline', !!offline).toggleClass('remote-online', !!online).toggleClass('remote-checking', !!checking);
    $('#targetStatusDot').toggleClass('ok-dot', !!online).toggleClass('offline-dot', !!offline).toggleClass('pending-dot', !online && !offline);
    $('#targetStatusText').text(!selected ? '远端设备' : offline ? '远端离线' : online ? '远端在线' : '检测中');
}

function renderDeviceHealth() {
    $('#deviceList .device-item').each(function () {
        const key = healthKey($(this).data('ip'));
        const state = remoteHealth[key];
        $(this).toggleClass('offline', !!(state && state.ok === false)).toggleClass('online', !!(state && state.ok === true));
    });
}

function stopManagePage() {
    postAction('/manage/session', { stop: 'true' }, () => {
        $('#serviceStatus').text('页面已关闭').addClass('off');
        warnToast('管理页面已关闭');
    }, '关闭失败');
}

function openBackgroundSettings() {
    const data = mode === 'remote' && target ? { target } : {};
    postAction('/manage/background/settings', data, res => {
        let info = {};
        try { info = parseJson(res); } catch (e) {}
        warnToast(info.opened ? '已尝试打开后台设置' : (info.guide || '未找到可直达的后台设置页'));
        heartbeat(false);
    }, '后台设置打开失败');
}

function showLoading() { loadingCount++; $('#loadingToast').show(); }
function hideLoading() { loadingCount = Math.max(0, loadingCount - 1); if (loadingCount === 0) $('#loadingToast').hide(); }

function requestError(xhr, status, fallback) {
    if (status === 'timeout') return '请求超时，请确认 App 仍在前台或设备未被系统限制后台运行';
    if (status === 'abort') return '请求已取消';
    return xhr && xhr.responseText ? xhr.responseText : fallback;
}

function parseJson(res) {
    return typeof res === 'string' ? JSON.parse(res) : res;
}

function ajaxJson(options, done, failText = '加载失败') {
    showLoading();
    $.ajax({ timeout: REQUEST_TIMEOUT, cache: false, ...options })
        .done(res => {
            try { done(parseJson(res)); }
            catch (e) { warnToast('响应格式错误'); }
        })
        .fail((xhr, status) => warnToast(requestError(xhr, status, failText)))
        .always(hideLoading);
}

function getJson(url, done, failText = '加载失败') {
    if (blockOfflineRemote(url, null)) return;
    ajaxJson({ url }, done, failText);
}

function postJson(url, data, done, failText = '保存失败') {
    const payload = { ...targetParam(), ...data };
    if (blockOfflineRemote(url, payload)) return;
    ajaxJson({ url, type: 'post', data: payload }, done, failText);
}

function postAction(url, data, done, failText = '操作失败') {
    if (blockOfflineRemote(url, data)) return;
    showLoading();
    $.ajax({ url, type: 'post', data, timeout: REQUEST_TIMEOUT, cache: false })
        .done(done)
        .fail((xhr, status) => warnToast(requestError(xhr, status, failText)))
        .always(hideLoading);
}

function setManageMode(next) {
    mode = next;
    $('#modeLocal').toggleClass('active', mode === 'local');
    $('#modeRemote').toggleClass('active', mode === 'remote');
    $('body').toggleClass('remote-mode', mode === 'remote').toggleClass('local-mode', mode === 'local');
    updateActionModeText();
    updateRemotePicker();
    updateTargetText();
    resetViewState();
    if (mode === 'remote' || currentView === 'sync') loadDevices();
    startRemoteHealth();
    if (mode === 'remote' && target) pingRemote(true, ok => { if (ok) loadCurrentView(true); else warnToast('远端设备离线'); });
    else loadCurrentView(true);
}

function updateTargetText() {
    $('#manageTargetText').text(mode === 'remote' ? (target ? '远端管理 · ' + targetName : '远端管理 · 请选择设备') : '本机管理 · 当前 App 设备');
    $('#targetDeviceText').html(target ? `<span>${escHtml(targetName || target)}</span><small>${escHtml(target)}</small>` : '<span>请选择设备</span>');
    $('#syncTargetText').text(targetName || target || '未选择');
}

function resetViewState() {
    currentRoot = '';
    currentParent = '';
    fileTreeExpanded = new Set(['']);
    fileTreeCache = {};
    fileTreeLoading = new Set();
    syncLoadedKey = '';
    loginStateLoadedKey = '';
    loginStateExpanded = new Set(['app', 'sdcard']);
    loginStateTreeCache = {};
    loginStateTreeLoading = new Set();
    cspLoadedKey = '';
    proxyLoadedKey = '';
    configsLoadedKey = '';
    currentFiles = [];
    clearFileSelection();
}

function loadDevices(scan = false) {
    getJson('/manage/devices' + (scan ? '?scan=true' : ''), data => renderDevices(data.devices || []), '设备列表加载失败');
}

function scanDevices() {
    devicePanelOpen = true;
    updateRemotePicker();
    loadDevices(true);
    setTimeout(loadDevices, 1200);
    setTimeout(loadDevices, 2600);
}

function renderDevices(devices) {
    $('#deviceList').html(devices.map(device => {
        const active = target === device.ip ? ' active' : '';
        const state = targetHealth(device.ip);
        const health = state && state.ok === false ? ' offline' : state && state.ok === true ? ' online' : '';
        return `<button class="device-item${active}${health}" data-ip="${escHtml(device.ip || '')}" type="button" onclick="selectDevice('${escPath(device.ip)}','${escPath(device.name || device.ip)}')"><i class="device-dot"></i><span>${escHtml(device.name || device.ip)}</span><small>${escHtml(device.ip || '')}</small></button>`;
    }).join('') || '<div class="empty-state">未发现设备，请确认电视和手机在同一局域网，并已打开 App</div>');
    updateRemotePicker();
    renderDeviceHealth();
}

function selectDevice(ip, name) {
    target = ip;
    targetName = name;
    devicePanelOpen = false;
    updateTargetText();
    updateRemotePicker();
    resetViewState();
    startRemoteHealth();
    pingRemote(true, ok => { if (ok) loadCurrentView(true); else warnToast('远端设备离线，稍后会自动重试'); });
    loadDevices();
}

function toggleDevicePanel() {
    devicePanelOpen = !devicePanelOpen;
    updateRemotePicker();
    if (devicePanelOpen) loadDevices();
}

function updateRemotePicker() {
    const visible = mode === 'remote' || currentView === 'sync';
    $('#remotePicker').css('display', visible ? 'grid' : 'none');
    $('#deviceList').toggle(visible && devicePanelOpen);
    $('#changeDeviceBtn').text(devicePanelOpen ? '收起列表' : '选择设备');
    updateTargetText();
}

function showManageView(view) {
    currentView = view;
    activateManageView(view);
    updateRemotePicker();
    if (currentView === 'sync') loadDevices();
    startRemoteHealth();
    if (mode === 'remote' && currentView !== 'sync' && target) {
        if (isRemoteOffline(target)) {
            warnToast('远端设备离线，已停止加载');
            pingRemote(true);
            return;
        }
        pingRemote(true, ok => { if (ok) loadCurrentView(false); else warnToast('远端设备离线'); });
    } else {
        loadCurrentView(false);
    }
}

function activateManageView(view) {
    $('.manage-view').removeClass('active');
    $('#view' + view.charAt(0).toUpperCase() + view.slice(1)).addClass('active');
    $('.manage-nav .md-nav-item').removeClass('active');
    $('#nav' + view.charAt(0).toUpperCase() + view.slice(1)).addClass('active');
}

function ensureTarget() {
    if (mode !== 'remote' || currentView === 'sync' || target) return true;
    warnToast('请先选择远端设备');
    devicePanelOpen = true;
    updateRemotePicker();
    loadDevices();
    return false;
}

function loadCurrentView(force) {
    if (!ensureTarget()) return;
    if (currentView === 'files') listFile(force ? '' : currentRoot);
    if (currentView === 'sync') loadSyncManage(force);
    if (currentView === 'loginState') loadLoginStateManage(force);
    if (currentView === 'csp') loadCspManage(force);
    if (currentView === 'proxy') loadProxyManage(force);
    if (currentView === 'configs') loadConfigsManage(force);
    if (currentView === 'search' || currentView === 'push') updateActionModeText();
}

function formatFileSize(size, isDir) {
    if (isDir) return '文件夹';
    const value = Number(size);
    if (!Number.isFinite(value) || value < 0) return '-';
    if (value < 1024) return value + ' B';
    const units = ['KB', 'MB', 'GB', 'TB'];
    let n = value / 1024;
    let unit = units[0];
    for (let i = 1; i < units.length && n >= 1024; i++) { n /= 1024; unit = units[i]; }
    return (n >= 100 ? n.toFixed(0) : n >= 10 ? n.toFixed(1) : n.toFixed(2)).replace(/\.0+$/, '') + ' ' + unit;
}

function renderFileBreadcrumb(path) {
    const parts = String(path || '').split('/').filter(Boolean);
    const rows = [`<button class="crumb" type="button" onclick="listFile('')">全部文件</button>`];
    let current = '';
    parts.forEach(part => {
        current += '/' + part;
        rows.push(`<span class="crumb-sep">/</span><button class="crumb" type="button" onclick="listFile('${escPath(current)}')">${escHtml(part)}</button>`);
    });
    $('#fileBreadcrumb').html(rows.join(''));
    $('#fileUpBtn').prop('disabled', currentParent === '.');
}

function loadFileTree(path = '') {
    path = syncNormalize(path);
    if (fileTreeCache[path] || fileTreeLoading.has(path)) return;
    fileTreeLoading.add(path);
    const requestPath = path ? '/' + path : '';
    if (blockOfflineRemote(fileApi(requestPath), null)) {
        fileTreeLoading.delete(path);
        return;
    }
    $.ajax({ url: fileApi(requestPath), timeout: FILE_TIMEOUT, cache: false })
        .done(res => {
            let data;
            try { data = parseJson(res); }
            catch (e) { warnToast('响应格式错误'); return; }
            fileTreeCache[path] = { path, parent: data.parent || '', items: data.files || [] };
            renderFileTree();
        })
        .fail((xhr, status) => warnToast(requestError(xhr, status, '目录树加载失败')))
        .always(() => {
        fileTreeLoading.delete(path);
        });
}

function renderFileTree() {
    const selected = syncNormalize(currentRoot);
    const rootActive = selected ? '' : ' active';
    const rootExpanded = fileTreeExpanded.has('');
    const visible = [];
    const rows = [`<div class="file-tree-row file-tree-root${rootActive}" style="--depth:0" data-path="">
        <button class="tree-toggle file-tree-toggle" type="button" onclick="toggleFileTree('')" aria-label="${rootExpanded ? '收起' : '展开'}">${rootExpanded ? '−' : '+'}</button>
        <span class="tree-check file-tree-check"></span>
        <button class="file-tree-main" type="button" onclick="listFile('')"><img class="file-icon" src="${icDir}" alt=""><span>全部文件</span></button>
        <div class="file-tree-actions"></div>
    </div>`];
    buildFileTreeRows('', 0, rows, visible);
    currentFiles = visible;
    $('#fileTree').html(rows.join('') || '<div class="empty-state compact">没有目录</div>');
    updateFileSelection();
}

function buildFileTreeRows(path, depth, rows, visible) {
    path = syncNormalize(path);
    if (path && !fileTreeExpanded.has(path)) return;
    const tree = fileTreeCache[path];
    if (!tree) {
        rows.push(`<div class="file-tree-row muted" style="--depth:${depth + 1}"><span class="tree-toggle placeholder"></span><span class="tree-check file-tree-check"></span><div class="file-tree-main"><span>加载中...</span></div><div class="file-tree-actions"></div></div>`);
        loadFileTree(path);
        return;
    }
    const items = (tree.items || []).slice().sort((a, b) => {
        if (a.dir !== b.dir) return b.dir - a.dir;
        return String(a.name || '').localeCompare(String(b.name || ''), 'zh-Hans');
    });
    if (!items.length && path === syncNormalize(currentRoot)) rows.push(`<div class="file-tree-row muted" style="--depth:${depth + 1}"><span class="tree-toggle placeholder"></span><span class="tree-check file-tree-check"></span><div class="file-tree-main"><span>当前目录没有文件</span></div><div class="file-tree-actions"></div></div>`);
    items.forEach(item => {
        if (item.path) visible.push(syncNormalize(item.path));
        rows.push(buildFileTreeNode(item, depth + 1));
        if (item.dir === 1 && fileTreeExpanded.has(syncNormalize(item.path || ''))) buildFileTreeRows(item.path, depth + 1, rows, visible);
    });
}

function buildFileTreeNode(item, depth) {
    const path = syncNormalize(item.path || '');
    const ep = escPath(path);
    const isDir = Number(item.dir || 0) === 1;
    const expanded = isDir && fileTreeExpanded.has(path);
    const active = isDir && syncNormalize(currentRoot) === path ? ' active' : '';
    const checked = fileSelection.has(path) ? ' checked' : '';
    const icon = isDir ? icDir : icFile;
    const toggle = isDir ? `<button class="tree-toggle file-tree-toggle" type="button" onclick="toggleFileTree('${ep}')" aria-label="${expanded ? '收起' : '展开'}">${expanded ? '−' : '+'}</button>` : '<span class="tree-toggle placeholder"></span>';
    const click = isDir ? `listFile('${ep}')` : `selectFile('${ep}')`;
    const actions = isDir
        ? `<button class="file-action" type="button" onclick="downloadArchive(['${ep}'])">打包</button><button class="file-action danger" type="button" onclick="showDelFolderDialog('${ep}','${escPath(parentPath(path))}')">删除</button>`
        : `<button class="file-action" type="button" onclick="downloadPath('${ep}')">下载</button><button class="file-action danger" type="button" onclick="showDelFileDialog('${ep}')">删除</button>`;
    return `<div class="file-tree-row${active}" style="--depth:${depth}" data-path="${escHtml(path)}">
        ${toggle}
        <label class="tree-check file-tree-check"><input type="checkbox" aria-label="选择 ${escHtml(item.name || path)}" onchange="toggleFileSelection('${ep}',this.checked)"${checked}></label>
        <button class="file-tree-main" type="button" onclick="${click}"><img class="file-icon" src="${icon}" alt=""><span>${escHtml(item.name || path)}</span></button>
        <div class="file-tree-actions">${actions}</div>
    </div>`;
}

function expandFileTreePath(path) {
    path = syncNormalize(path);
    fileTreeExpanded.add('');
    while (path) {
        const index = path.lastIndexOf('/');
        path = index < 0 ? '' : path.substring(0, index);
        fileTreeExpanded.add(path);
    }
}

function toggleFileTree(path) {
    path = syncNormalize(path);
    if (fileTreeExpanded.has(path)) {
        fileTreeExpanded.delete(path);
        renderFileTree();
    } else {
        fileTreeExpanded.add(path);
        loadFileTree(path);
        renderFileTree();
    }
}

function parentPath(path) {
    path = syncNormalize(path);
    const index = path.lastIndexOf('/');
    return index < 0 ? '' : path.substring(0, index);
}

function toggleFileSelection(path, checked) { checked ? fileSelection.add(path) : fileSelection.delete(path); updateFileSelection(); }
function toggleSelectAll(checked) { fileSelection = checked ? new Set(currentFiles) : new Set(); $('#fileTree input[type=checkbox]').prop('checked', checked); updateFileSelection(); }
function clearFileSelection() { fileSelection.clear(); $('#fileTree input[type=checkbox],#fileSelectAll').prop('checked', false); updateFileSelection(); }
function updateFileSelection() {
    const count = fileSelection.size;
    const total = currentFiles.length;
    const all = total > 0 && count === total;
    const partial = count > 0 && count < total;
    $('#fileSelectionText').text(count ? `已选择 ${count} 项` : `${total} 项`);
    $('#fileZipBtn,#fileClearBtn').prop('disabled', count === 0);
    $('#fileSelectionBar').toggleClass('active', count > 0);
    $('#fileSelectAll').prop('checked', all).prop('indeterminate', partial);
}

function listFile(path = '') {
    if (!ensureTarget()) return;
    path = fileRequestPath(path);
    if (blockOfflineRemote(fileApi(path), null)) return;
    showLoading();
    $.ajax({ url: fileApi(path), timeout: FILE_TIMEOUT, cache: false })
        .done(res => {
        let info;
        try { info = parseJson(res); }
        catch (e) { warnToast('响应格式错误'); return; }
        currentRoot = path;
        currentParent = info.parent || '';
        const files = info.files || [];
        currentFiles = files.map(node => node.path).filter(Boolean);
        fileTreeCache[syncNormalize(path)] = { path: syncNormalize(path), parent: info.parent || '', items: files };
        expandFileTreePath(path);
        loadFileTree('');
        renderFileBreadcrumb(path);
        fileSelection.clear();
        renderFileTree();
    })
        .fail((xhr, status) => warnToast(requestError(xhr, status, '加载失败')))
        .always(hideLoading);
}

function uploadFile() { if (ensureTarget()) $('#file_uploader').click(); }
function onFileSelected() {
    const files = $('#file_uploader')[0].files;
    if (!files.length) return;
    $('#uploadTipContent').text(Array.from(files).map(f => f.name).join(', '));
    openDialog('uploadTip');
}

function confirmUpload(yes) {
    closeDialog('uploadTip');
    if (yes !== 1) return;
    const files = $('#file_uploader')[0].files;
    if (!files.length) return;
    const formData = new FormData();
    formData.append('path', currentRoot);
    const remote = mode === 'remote' && !!target;
    if (blockOfflineRemote(remote ? '/manage/remote/upload' : '/upload', remote ? { target } : {})) return;
    if (remote) formData.append('target', target);
    Array.from(files).forEach((file, index) => formData.append('files-' + index, file));
    showLoading();
    $.ajax({ url: remote ? '/manage/remote/upload' : '/upload', type: 'post', data: formData, processData: false, contentType: false, timeout: UPLOAD_TIMEOUT })
        .done(() => listFile(currentRoot))
        .fail((xhr, status) => warnToast(requestError(xhr, status, '上传失败')))
        .always(() => { $('#file_uploader').val(''); hideLoading(); });
}

function showNewFolderDialog() { if (ensureTarget()) openDialog('newFolder'); }
function confirmNewFolder(yes) {
    closeDialog('newFolder');
    const name = $('#newFolderContent').val().trim();
    $('#newFolderContent').val('');
    if (yes !== 1 || !name) return;
    const remote = mode === 'remote' && !!target;
    postAction(remote ? '/manage/remote/newFolder' : '/newFolder', { ...(remote ? { target } : {}), path: currentRoot, name }, () => listFile(currentRoot), '新增失败');
}

function showDelFolderDialog(path, refreshPath) { pendingDelFolder = { path, refreshPath }; $('#delFolderContent').text('是否删除 ' + path); openDialog('delFolder'); }
function confirmDelFolder(yes) {
    closeDialog('delFolder');
    if (yes !== 1 || !pendingDelFolder) { pendingDelFolder = null; return; }
    const { path, refreshPath } = pendingDelFolder;
    pendingDelFolder = null;
    const remote = mode === 'remote' && !!target;
    postAction(remote ? '/manage/remote/delFolder' : '/delFolder', { ...(remote ? { target } : {}), path }, () => listFile(refreshPath), '删除失败');
}

function showDelFileDialog(path) { currentFile = path; $('#delFileContent').text('是否删除 ' + path); openDialog('delFile'); }
function confirmDelFile(yes) {
    closeDialog('delFile');
    if (yes !== 1) return;
    const remote = mode === 'remote' && !!target;
    postAction(remote ? '/manage/remote/delFile' : '/delFile', { ...(remote ? { target } : {}), path: currentFile }, () => listFile(currentRoot), '删除失败');
}

function selectFile(path) { currentFile = path; $('#fileUrl').text('file:/' + path); openDialog('fileInfoDialog'); }
function downloadFile() { closeDialog('fileInfoDialog'); downloadPath(currentFile); }
function downloadPath(path) {
    if (!path) return;
    if (blockOfflineRemote(fileApi(path, true), null)) return;
    const a = document.createElement('a');
    a.href = fileApi(path, true);
    a.download = path.split('/').filter(Boolean).pop() || 'download';
    document.body.appendChild(a);
    a.click();
    a.remove();
}

function downloadSelectedArchive() { downloadArchive(Array.from(fileSelection)); }
function downloadArchive(paths) {
    if (!paths || !paths.length) return;
    const query = new URLSearchParams({ ...targetParam(), paths: paths.join('\n') }).toString();
    if (blockOfflineRemote(archiveApi(), targetParam())) return;
    const a = document.createElement('a');
    a.href = archiveApi() + '?' + query;
    a.download = paths.length === 1 ? (paths[0].split('/').filter(Boolean).pop() || 'files') + '.zip' : 'webhtv-files.zip';
    document.body.appendChild(a);
    a.click();
    a.remove();
}

function loadSyncManage(force = false) {
    if (syncLoadedKey === activeKey() && !force) return;
    getJson('/manage/sync/paths?' + targetQuery(), data => {
        syncPaths = data.paths || [];
        syncLoadedKey = activeKey();
        syncTreeCache = {};
        expandSyncImportantPaths();
        loadSyncTree('');
        renderSyncPaths();
        renderSyncTree();
    });
}

function loadSyncTree(path = '') {
    path = syncNormalize(path);
    if (syncTreeCache[path] || syncTreeLoading.has(path)) return;
    syncTreeLoading.add(path);
    getJson('/manage/sync/tree?' + targetQuery({ path }), data => {
        syncTreeLoading.delete(path);
        syncTreeCache[path] = data || { path, dirs: [] };
        renderSyncTree();
    });
}

function renderSyncTree() {
    $('#syncTreePath').text('/');
    const rows = [];
    buildSyncTreeRows('', 0, rows);
    $('#syncTree').html(rows.join('') || '<div class="empty-state">没有可选目录</div>');
    renderSyncPaths();
}

function buildSyncTreeRows(path, depth, rows) {
    path = syncNormalize(path);
    const tree = syncTreeCache[path];
    if (!tree) {
        rows.push(`<div class="sync-tree-row muted" style="--depth:${depth}"><span class="sync-tree-toggle placeholder"></span><span class="tree-check sync-tree-check"></span><div class="sync-tree-main"><span>加载中...</span></div></div>`);
        loadSyncTree(path);
        return;
    }
    (tree.dirs || []).forEach(item => {
        const child = syncNormalize(item.path || '');
        rows.push(buildSyncDir(item, depth));
        if (syncTreeExpanded.has(child)) buildSyncTreeRows(child, depth + 1, rows);
    });
    if (tree.truncated) rows.push(`<div class="sync-tree-row muted" style="--depth:${depth}"><span class="sync-tree-toggle placeholder"></span><span class="tree-check sync-tree-check"></span><div class="sync-tree-main"><span>当前目录过多，仅显示前 300 个目录</span></div></div>`);
}

function buildSyncDir(item, depth) {
    const path = item.path || '';
    const ep = escPath(path);
    const checked = syncPaths.includes(path) ? ' checked' : '';
    const expanded = syncTreeExpanded.has(path);
    const hasChildren = item.children !== false;
    const toggle = hasChildren ? `<button class="sync-tree-toggle" type="button" onclick="toggleSyncTree('${ep}')" aria-label="${expanded ? '收起' : '展开'}">${expanded ? '−' : '+'}</button>` : '<span class="sync-tree-toggle placeholder"></span>';
    const click = hasChildren ? `toggleSyncTree('${ep}')` : `toggleSyncPath('${ep}',!syncPaths.includes('${ep}'))`;
    return `<div class="sync-tree-row" style="--depth:${depth}" data-path="${escHtml(path)}">
        ${toggle}
        <label class="tree-check sync-tree-check"><input id="sync_${itemId(path)}" type="checkbox" onchange="toggleSyncPath('${ep}',this.checked)"${checked}></label>
        <button class="sync-tree-main" type="button" onclick="${click}"><img class="file-icon" src="${icDir}" alt=""><span>${escHtml(item.name || path)}</span></button>
    </div>`;
}

function syncNormalize(path) { return String(path || '').replace(/^\/+|\/+$/g, ''); }
function expandSyncPath(path) {
    path = syncNormalize(path);
    while (path) {
        const index = path.lastIndexOf('/');
        path = index < 0 ? '' : path.substring(0, index);
        syncTreeExpanded.add(path);
    }
}
function expandSyncImportantPaths() {
    syncTreeExpanded.add('');
    syncPaths.forEach(expandSyncPath);
}
function toggleSyncTree(path) {
    path = syncNormalize(path);
    if (syncTreeExpanded.has(path)) {
        syncTreeExpanded.delete(path);
        renderSyncTree();
    } else {
        syncTreeExpanded.add(path);
        loadSyncTree(path);
        renderSyncTree();
    }
}
function toggleSyncPath(path, checked) { syncPaths = syncPaths.filter(item => item !== path); if (checked) { syncPaths.push(path); expandSyncPath(path); } renderSyncTree(); }
function removeSyncPath(path) { syncPaths = syncPaths.filter(item => item !== path); renderSyncTree(); }
function renderSyncPaths() {
    syncPaths = Array.from(new Set(syncPaths.filter(Boolean)));
    $('#syncPathChips').html(syncPaths.map(path => `<button class="path-chip" type="button" onclick="removeSyncPath('${escPath(path)}')">${escHtml(path)} ×</button>`).join('') || '<span class="empty-state compact">未选择目录</span>');
    syncPaths.forEach(path => { const el = document.getElementById('sync_' + itemId(path)); if (el) el.checked = true; });
}
function saveSyncPaths() { postJson('/manage/sync/paths', { paths: syncPaths.join('\n') }, data => { syncPaths = data.paths || []; renderSyncPaths(); warnToast('同步目录已保存'); }); }
function detectSyncPaths() { postJson('/manage/sync/detect', {}, data => { syncPaths = data.paths || []; expandSyncImportantPaths(); renderSyncPaths(); renderSyncTree(); warnToast('已自动加入本地包目录'); }, '自动识别失败'); }

function loadLoginStateManage(force = false) {
    if (loginStateLoadedKey === activeKey() && !force) return;
    getJson('/manage/login-state?' + targetQuery(), data => {
        loginStateData = data || {};
        loginStatePaths = data.learned || [];
        loginStateLoadedKey = activeKey();
        expandLoginStateImportantPaths();
        loadLoginStateTree('app');
        loadLoginStateTree('sdcard');
        renderLoginStateManage();
    }, '登录态加载失败');
}

function renderLoginStateManage() {
    const data = loginStateData || {};
    const pendingItems = loginStatePendingItems();
    const findings = data.findings || [];
    const missing = ((data.states || []).filter(item => item && item.exists === false)).length;
    $('#loginStateSummary').text(`${data.learning ? '学习中' : '未学习'} · 已确认 ${loginStatePaths.length} · 待确认 ${pendingItems.length} · 最近 ${findings.length}${missing ? ` · 缺失 ${missing}` : ''}`);
    $('#loginStateLearnBtn').text(data.learning ? '完成学习' : '开始学习');
    $('#loginStateRevealBtn').prop('disabled', pendingItems.length === 0).text(pendingItems.length ? `显示待确认(${pendingItems.length})` : '显示待确认');
    $('#loginStateLearned').html(loginStatePathStates().map(item => buildLoginStateRow(item, 'selected')).join('') || '<div class="empty-state compact">未确认任何登录态路径</div>');
    $('#loginStatePending').html(pendingItems.map(item => buildLoginStateRow(item, 'pending')).join('') || '<div class="empty-state compact">暂无待确认项</div>');
    $('#loginStateFindings').html(findings.map(item => buildLoginStateRow(item, 'finding')).join('') || '<div class="empty-state compact">暂无最近学习结果</div>');
    renderLoginStateTrees();
}

function loginStatePathStates() {
    const byPath = {};
    ((loginStateData && loginStateData.states) || []).forEach(item => { if (item && item.path) byPath[item.path] = item; });
    return loginStatePaths.map(path => byPath[path] || { path, displayPath: path, exists: true, file: true, size: 0, confidence: 'selected', reason: '已确认路径' });
}

function loginStateFindingByPath(path) {
    path = String(path || '');
    return ((loginStateData && loginStateData.findings) || []).find(item => item && item.path === path) || null;
}

function loginStatePendingItems() {
    const byPath = {};
    ((loginStateData && loginStateData.findings) || []).forEach(item => { if (item && item.path) byPath[item.path] = item; });
    return ((loginStateData && loginStateData.pending) || [])
        .filter(path => loginStateState(path) !== 'checked')
        .map(path => byPath[path] || { path, displayPath: path, confidence: 'pending', reason: '学习期间发生变化' });
}

function loginStateMeta(path) {
    path = String(path || '');
    for (const key of Object.keys(loginStateTreeCache || {})) {
        const tree = loginStateTreeCache[key];
        const item = ((tree && tree.items) || []).find(x => x && x.path === path);
        if (item) return item;
    }
    return ((loginStateData && loginStateData.states) || []).find(item => item && item.path === path) || null;
}

function loginStateFileIcon(item) {
    if (item && item.dir) return icDir;
    if (item && item.text === true) return icTextFile;
    if (item && item.fileType === 'binary') return icBinaryFile;
    return icFile;
}

function loginStateTypeTitle(item) {
    if (!item || item.dir) return '';
    if (item.text === true) return '文本';
    if (item.fileType === 'binary' || item.text === false) return '非文本';
    return '文件';
}

function loginStatePreviewText(data) {
    const parts = [];
    if (data.encoding) parts.push(data.encoding);
    if (data.size != null) parts.push(formatFileSize(Number(data.size || 0), false));
    if (data.truncated) parts.push('只读预览，已截断');
    else if (!data.editable) parts.push('只读预览');
    return parts.join(' · ');
}

function loginStateUsePreviewList(data) {
    const text = String((data && data.content) || '');
    return !(data && data.editable) || text.length > LOGIN_TEXTAREA_LIMIT || loginStateMaxLine(text) > LOGIN_TEXTAREA_LINE_LIMIT;
}

function loginStateMaxLine(text) {
    let max = 0;
    let count = 0;
    for (let i = 0; i < text.length; i++) {
        const ch = text.charAt(i);
        if (ch === '\n' || ch === '\r') {
            max = Math.max(max, count);
            count = 0;
        } else {
            count++;
        }
    }
    return Math.max(max, count);
}

function loginStatePreviewRows(text) {
    const rows = [];
    const value = String(text || '');
    let start = 0;
    for (let i = 0; i <= value.length; i++) {
        if (i < value.length && value.charAt(i) !== '\n' && value.charAt(i) !== '\r') continue;
        loginStateAddPreviewChunks(rows, value.substring(start, i));
        if (i + 1 < value.length && value.charAt(i) === '\r' && value.charAt(i + 1) === '\n') i++;
        start = i + 1;
    }
    if (!rows.length) rows.push('');
    return rows;
}

function loginStateAddPreviewChunks(rows, line) {
    if (!line.length) {
        rows.push('');
        return;
    }
    for (let start = 0; start < line.length; start += LOGIN_PREVIEW_ROW_CHARS) rows.push(line.substring(start, start + LOGIN_PREVIEW_ROW_CHARS));
}

function renderLoginStatePreviewContent(data) {
    const content = (data && data.content) || '';
    const listPreview = loginStateUsePreviewList(data);
    currentLoginStateEditable = !!(data && data.editable) && !listPreview;
    $('#loginStateContent')
        .toggle(!listPreview)
        .val(listPreview ? '' : content)
        .prop('readonly', !currentLoginStateEditable)
        .toggleClass('readonly-code', !currentLoginStateEditable);
    $('#loginStatePreviewList')
        .toggle(listPreview)
        .html(listPreview ? loginStatePreviewRows(content).map(row => `<div class="file-preview-row">${row ? escHtml(row) : '&nbsp;'}</div>`).join('') : '');
    $('#loginStateSaveBtn').prop('disabled', !currentLoginStateEditable).toggle(currentLoginStateEditable);
}

function buildLoginStateRow(item, type) {
    const path = item.path || '';
    const ep = escPath(path);
    const confidence = type === 'selected' ? 'selected' : (item.confidence || (type === 'pending' ? 'pending' : 'low'));
    const title = confidenceTitle(confidence, type);
    const size = item.size != null ? formatFileSize(Number(item.size || 0), false) : '';
    const reason = item.reason || (type === 'selected' ? '已确认路径' : '学习期间发生变化');
    const display = item.displayPath || path;
    const missing = item.exists === false ? ' missing' : '';
    const action = type === 'selected'
        ? `<button class="file-action danger" type="button" onclick="removeLoginStatePath('${ep}')">移除</button>`
        : `<button class="file-action" type="button" onclick="addLoginStatePath('${ep}')">确认</button>`;
    return `<div class="login-file-row ${escHtml(type)}${missing}">
        <button class="login-file-main" type="button" onclick="openLoginStateFile('${ep}')">
            <strong>${escHtml(path)}</strong>
            <span>${escHtml(reason)}${size ? ` · ${escHtml(size)}` : ''}</span>
            <small>${escHtml(display)}</small>
        </button>
        <span class="file-tag ${escHtml(confidence)}">${escHtml(title)}</span>
        ${action}
    </div>`;
}

function confidenceTitle(confidence, type = '') {
    if (type === 'selected' || confidence === 'selected') return '已确认';
    if (type === 'pending' || confidence === 'pending') return '待确认';
    if (confidence === 'high') return '高置信';
    if (confidence === 'medium') return '中置信';
    return '低置信';
}

function toggleLoginStateLearning() {
    const learning = !!(loginStateData && loginStateData.learning);
    postJson('/manage/login-state/learn', { action: learning ? 'finish' : 'begin' }, data => {
        loginStateData = data || {};
        loginStatePaths = data.learned || [];
        loginStateLoadedKey = activeKey();
        loginStateTreeCache = {};
        expandLoginStateImportantPaths();
        loadLoginStateTree('app');
        loadLoginStateTree('sdcard');
        renderLoginStateManage();
        warnToast(learning ? '登录态学习完成' : '已开始登录态学习');
    }, '登录态学习失败');
}

function addLoginStatePath(path) {
    path = String(path || '').trim();
    if (!path || loginStatePaths.includes(path)) return;
    loginStatePaths = loginStatePaths.filter(item => !loginStateCovers(path, item) && !loginStateCovers(item, path));
    loginStatePaths.push(path);
    expandLoginStatePath(path);
    renderLoginStateManage();
}

function removeLoginStatePath(path) {
    loginStatePaths = loginStatePaths.filter(item => !loginStateCovers(item, path));
    renderLoginStateManage();
}

function saveLoginStatePaths() {
    postJson('/manage/login-state/paths', { paths: loginStatePaths.join('\n') }, data => {
        loginStateData = data || {};
        loginStatePaths = data.learned || [];
        loginStateLoadedKey = activeKey();
        loginStateTreeCache = {};
        expandLoginStateImportantPaths();
        loadLoginStateTree('app');
        loadLoginStateTree('sdcard');
        renderLoginStateManage();
        warnToast('登录态路径已保存');
    }, '登录态路径保存失败');
}

function openLoginStateFile(path) {
    if (!path) return;
    const meta = loginStateMeta(path);
    if (meta && meta.text === false) {
        warnToast('非文本文件不能查看内容');
        return;
    }
    postJson('/manage/login-state/file', { path }, data => {
        currentLoginStatePath = data.path || path;
        $('#loginStatePath').text(data.displayPath || currentLoginStatePath);
        $('#loginStatePreviewMeta').text(loginStatePreviewText(data)).toggle(!!loginStatePreviewText(data));
        renderLoginStatePreviewContent(data);
        openDialog('loginStateEditorDialog');
        if (currentLoginStateEditable) setTimeout(() => $('#loginStateContent').trigger('focus'), 80);
    }, '登录态文件读取失败');
}

function saveLoginStateFile() {
    if (!currentLoginStatePath) return;
    if (!currentLoginStateEditable) {
        warnToast('只读预览不能保存');
        return;
    }
    const content = $('#loginStateContent').val();
    postJson('/manage/login-state/file', { path: currentLoginStatePath, content }, data => {
        $('#loginStatePreviewMeta').text(loginStatePreviewText(data)).toggle(!!loginStatePreviewText(data));
        renderLoginStatePreviewContent(data);
        addLoginStatePath(data.path || currentLoginStatePath);
        saveLoginStatePaths();
        warnToast('登录态文件已保存');
    }, '登录态文件保存失败');
    closeDialog('loginStateEditorDialog');
}

function loginStateNormalize(path) { return String(path || '').replace(/^\/+|\/+$/g, ''); }
function loginStateCovers(parent, child) {
    parent = loginStateNormalize(parent);
    child = loginStateNormalize(child);
    return !!parent && (parent === child || child.startsWith(parent + '/'));
}
function loginStateState(path) {
    path = loginStateNormalize(path);
    if (loginStatePaths.some(item => loginStateCovers(item, path))) return 'checked';
    if (loginStatePaths.some(item => loginStateCovers(path, item))) return 'partial';
    return 'unchecked';
}
function loginStateHasPendingChild(path) {
    return ((loginStateData && loginStateData.pending) || []).some(item => item !== path && loginStateCovers(path, item) && loginStateState(item) !== 'checked');
}
function expandLoginStatePath(path) {
    path = loginStateNormalize(path);
    while (path) {
        const index = path.lastIndexOf('/');
        if (index < 0) { loginStateExpanded.add(path); return; }
        path = path.substring(0, index);
        if (path) loginStateExpanded.add(path);
    }
}
function expandLoginStateImportantPaths() {
    loginStateExpanded.add('app');
    loginStateExpanded.add('sdcard');
    loginStatePaths.forEach(expandLoginStatePath);
    ((loginStateData && loginStateData.pending) || []).forEach(expandLoginStatePath);
}
function loadLoginStateTree(path, callback) {
    path = loginStateNormalize(path || '');
    if (!path) path = 'app';
    if (loginStateTreeCache[path] || loginStateTreeLoading.has(path)) {
        if (callback) callback(loginStateTreeCache[path]);
        return;
    }
    loginStateTreeLoading.add(path);
    getJson('/manage/login-state/tree?' + targetQuery({ path }), data => {
        loginStateTreeLoading.delete(path);
        loginStateTreeCache[path] = data || { path, items: [] };
        renderLoginStateManage();
        if (callback) callback(loginStateTreeCache[path]);
    }, '登录态目录加载失败');
}
function toggleLoginStateTree(path) {
    path = loginStateNormalize(path);
    if (!path) return;
    if (loginStateExpanded.has(path)) {
        loginStateExpanded.delete(path);
        renderLoginStateManage();
    } else {
        loginStateExpanded.add(path);
        loadLoginStateTree(path);
        renderLoginStateManage();
    }
}
function revealLoginStatePending() {
    const items = loginStatePendingItems();
    if (!items.length) {
        warnToast('没有待确认项');
        return;
    }
    items.forEach(item => expandLoginStatePath(item.path));
    loginStateTreeCache = {};
    loadLoginStateTree('app');
    loadLoginStateTree('sdcard');
    renderLoginStateManage();
    warnToast(`已显示 ${items.length} 个待确认项`);
}
function renderLoginStateTrees() {
    renderLoginStateTree('app', $('#loginStateTreeApp'));
    renderLoginStateTree('sdcard', $('#loginStateTreeSdcard'));
}
function renderLoginStateTree(root, targetEl) {
    if (!targetEl || !targetEl.length) return;
    const rows = [];
    buildLoginStateTreeRows(root, 0, rows);
    appendMissingLoginStateRows(root, rows);
    targetEl.html(rows.join('') || '<div class="empty-state compact">没有可显示的文件</div>');
    targetEl.find('input[data-partial="1"]').each(function () { this.indeterminate = true; });
}
function buildLoginStateTreeRows(path, depth, rows) {
    const tree = loginStateTreeCache[path];
    if (!tree) {
        rows.push(`<div class="login-tree-row muted" style="--depth:${depth}"><span class="login-tree-spacer"></span><span class="tree-check login-tree-check"></span><div class="login-tree-main"><span>加载中...</span></div></div>`);
        if (!loginStateTreeLoading.has(path)) loadLoginStateTree(path);
        return;
    }
    (tree.items || []).forEach(item => {
        rows.push(buildLoginStateTreeRow(item, depth));
        if (item.dir && loginStateExpanded.has(item.path)) buildLoginStateTreeRows(item.path, depth + 1, rows);
    });
}
function appendMissingLoginStateRows(root, rows) {
    const visible = new Set();
    const re = /data-path="([^"]*)"/g;
    rows.forEach(row => {
        let match;
        while ((match = re.exec(row))) visible.add(match[1].replace(/&quot;/g, '"'));
    });
    const paths = [...loginStatePaths, ...((loginStateData && loginStateData.pending) || [])];
    paths.forEach(path => {
        if (!path.startsWith(root + '/') || visible.has(path)) return;
        const state = ((loginStateData && loginStateData.states) || []).find(item => item && item.path === path);
        if (state && state.exists !== false) return;
        rows.push(buildLoginStateTreeRow({ name: path.split('/').pop(), path, dir: false, size: 0, modified: 0, selectable: true, missing: true }, loginStateDepth(path)));
    });
}
function loginStateDepth(path) {
    return Math.max(0, loginStateNormalize(path).split('/').length - 2);
}
function buildLoginStateTreeRow(item, depth) {
    const path = item.path || '';
    const ep = escPath(path);
    const state = loginStateState(path);
    const pending = loginStatePendingItems().some(x => x.path === path);
    const hasPending = item.dir && loginStateHasPendingChild(path);
    const missing = item.missing || (((loginStateData && loginStateData.states) || []).some(x => x && x.path === path && x.exists === false));
    const checked = state === 'checked' ? ' checked' : '';
    const partial = state === 'partial' ? ' data-partial="1"' : '';
    const stateBadge = missing ? '<span class="login-state-badge missing">缺失</span>' : pending ? '<span class="login-state-badge pending">待确认</span>' : hasPending ? '<span class="login-state-badge pending">含待确认</span>' : state === 'checked' ? '<span class="login-state-badge selected">已选</span>' : state === 'partial' ? '<span class="login-state-badge partial">部分</span>' : '';
    const typeTitle = loginStateTypeTitle(item);
    const typeBadge = typeTitle && !missing ? `<span class="login-state-badge file-type ${item.text === true ? 'text' : 'binary'}">${escHtml(typeTitle)}</span>` : '';
    const toggle = item.dir ? `<button class="login-tree-toggle" type="button" onclick="toggleLoginStateTree('${ep}')" aria-label="${loginStateExpanded.has(path) ? '收起' : '展开'}">${loginStateExpanded.has(path) ? '−' : '+'}</button>` : '<span class="login-tree-toggle placeholder"></span>';
    const icon = loginStateFileIcon(item);
    const click = item.dir ? `toggleLoginStateTree('${ep}')` : `openLoginStateFile('${ep}')`;
    return `<div class="login-tree-row ${item.dir ? 'dir' : 'file'} ${pending || hasPending ? 'pending' : ''} ${missing ? 'missing' : ''}" style="--depth:${depth}" data-path="${escHtml(path)}">
        ${toggle}
        <label class="tree-check login-tree-check"><input type="checkbox" onchange="toggleLoginStatePath('${ep}',this.checked)"${checked}${partial} aria-label="选择 ${escHtml(item.name || path)}"></label>
        <button class="login-tree-main" type="button" onclick="${click}">
            <img class="file-icon" src="${icon}" alt="">
            <span class="login-tree-name">${escHtml(item.name || path)}</span>
            <span class="login-tree-badges">${typeBadge}${stateBadge}</span>
        </button>
    </div>`;
}
function toggleLoginStatePath(path, checked) {
    if (checked) addLoginStatePath(path);
    else removeLoginStatePath(path);
}

function setSyncMode(next) {
    syncMode = next === 'pull' ? 'pull' : 'push';
    $('#syncModePush').toggleClass('active', syncMode === 'push');
    $('#syncModePull').toggleClass('active', syncMode === 'pull');
}
function syncOptionIds() { return ['syncOptConfig', 'syncOptSpider', 'syncOptLoginState', 'syncOptWebHome', 'syncOptSearch', 'syncOptHistory', 'syncOptKeep', 'syncOptSettings']; }
function allSyncSelected() { return syncOptionIds().every(id => $('#' + id).prop('checked')); }
function toggleSyncSelection() {
    const checked = !allSyncSelected();
    syncOptionIds().forEach(id => $('#' + id).prop('checked', checked));
    updateSyncPathsVisible();
}
function updateSyncPathsVisible() {
    const spider = $('#syncOptSpider').prop('checked');
    $('#syncPathsPanel').toggle(spider);
    $('#syncSelectBtn').text(allSyncSelected() ? '取消全选' : '全选');
}
function syncOptionsPayload() {
    return {
        config: $('#syncOptConfig').prop('checked'),
        spider: $('#syncOptSpider').prop('checked'),
        loginState: $('#syncOptLoginState').prop('checked'),
        webHome: $('#syncOptWebHome').prop('checked'),
        search: $('#syncOptSearch').prop('checked'),
        history: $('#syncOptHistory').prop('checked'),
        keep: $('#syncOptKeep').prop('checked'),
        settings: $('#syncOptSettings').prop('checked'),
        paths: syncPaths.join('\n')
    };
}
function startSyncManage() {
    if (!target) {
        warnToast('请先选择远端设备');
        devicePanelOpen = true;
        updateRemotePicker();
        loadDevices();
        return;
    }
    const options = syncOptionsPayload();
    if (!Object.keys(options).some(key => key !== 'paths' && options[key])) {
        warnToast('至少选择一项同步内容');
        return;
    }
    if (isRemoteOffline(target)) {
        warnToast('远端设备离线，已停止同步');
        pingRemote(true);
        return;
    }
    showLoading();
    $.ajax({
        url: '/manage/sync/start',
        type: 'post',
        data: { device: target, mode: syncMode, options: JSON.stringify(options), paths: syncPaths.join('\n') },
        timeout: SYNC_TIMEOUT,
        cache: false
    })
        .done(res => {
            let data = {};
            try { data = parseJson(res); } catch (e) {}
            const fileCount = Number(data.files || 0) + Number(data.loginFiles || 0);
            const zipSize = Number(data.zipSize || 0) + Number(data.loginZipSize || 0);
            const detail = fileCount ? ` · ${fileCount} 个文件 · ${formatFileSize(zipSize, false)}` : '';
            warnToast((syncMode === 'push' ? '推送完成' : '拉取已完成') + detail);
        })
        .fail((xhr, status) => warnToast(requestError(xhr, status, '同步失败')))
        .always(hideLoading);
}

function loadCspManage(force = false) {
    if (cspLoadedKey === activeKey() && !force) return;
    getJson('/manage/csp?' + targetQuery(), data => { cspRegistry = normalizeCspRegistry(data); cspLoadedKey = activeKey(); renderCspManage(); });
}
const CSP_KINDS = ['webHome', 'csp', 'live'];
function cspKind(item = {}) {
    if (CSP_KINDS.includes(item.kind)) return item.kind;
    if (item.live && typeof item.live === 'object') return 'live';
    if (!item.site && !item.key && (item.url || item.groups || item.epg)) return 'live';
    if (item.webHome === true) return 'webHome';
    if (item.webHome === false) return 'csp';
    const api = String(siteValue(item, 'api', ''));
    const home = String(siteValue(item, 'homePage', ''));
    return !api && !!home ? 'webHome' : 'csp';
}
function cspKindName(kind) { return kind === 'live' ? '直播' : kind === 'webHome' ? 'WebHome' : '通用 CSP'; }
function liveDefaultObject(name = '') { return { name, type: 0, playerType: 2, ua: 'okhttp' }; }
function siteValue(item, key, fallback = '') {
    if (item[key] !== undefined && item[key] !== null && item[key] !== '') return item[key];
    return item.site && item.site[key] !== undefined && item.site[key] !== null ? item.site[key] : fallback;
}
function liveValue(item, key, fallback = '') {
    if (item[key] !== undefined && item[key] !== null && item[key] !== '') return item[key];
    return item.live && item.live[key] !== undefined && item.live[key] !== null ? item.live[key] : fallback;
}
function rawObjectFromItem(item, drop = []) {
    const object = { ...(item || {}) };
    ['id', 'enabled', 'kind', 'site', 'live', 'items', 'sites', 'lives', 'headerText', 'styleText', 'siteText', 'liveText', 'catchupText', 'coreText', 'groupsText', ...drop].forEach(key => delete object[key]);
    return object;
}
function jsonText(value, fallback) {
    const data = value === undefined || value === null ? fallback : value;
    return JSON.stringify(data, null, 2);
}
function extensionText(value) {
    if (!hasJsonValue(value)) return '';
    if (typeof value === 'string') return value.trim();
    return JSON.stringify(value, null, 2);
}
function hasJsonValue(value) {
    if (value === undefined || value === null || value === '') return false;
    if (Array.isArray(value)) return value.length > 0;
    if (typeof value === 'object') return Object.keys(value).length > 0;
    return true;
}
function assignOptionalJson(object, key, value) {
    if (!hasJsonValue(value)) delete object[key];
    else object[key] = value;
}
function buildFieldLabel(text, required = false, note = '') {
    const tag = required ? '必填' : '可选';
    const cls = required ? 'required-label' : 'optional-label';
    return `<label class="form-label live-field-label ${cls}">${escHtml(text)}<span>${escHtml(note || tag)}</span></label>`;
}
function buildLiveTextField(key, label, value, placeholder = '', required = false, note = '', type = 'text', extraClass = '') {
    return `<div class="md-field ${extraClass}">${buildFieldLabel(label, required, note)}<input class="md-input csp-field" data-key="${key}" type="${type}" value="${escHtml(value)}" placeholder="${escHtml(placeholder)}"></div>`;
}
function buildJsonTextarea(key, value, placeholder = '') {
    return `<textarea class="code-area csp-field compact-code" data-key="${key}" spellcheck="false" placeholder="${escHtml(placeholder)}">${escHtml(value)}</textarea>`;
}
function normalizeCspRegistry(data) {
    const source = { ...(data || {}) };
    const r = data || {};
    r.enabled = r.enabled !== false;
    r.insertIndex = Math.max(0, Math.min(9, Number(r.insertIndex || 0)));
    r.homeKey = r.homeKey || '';
    if (!Array.isArray(r.items)) {
        r.items = [];
        if (Array.isArray(r.sites)) r.sites.forEach(site => r.items.push({ kind: 'csp', site, ...site }));
        if (Array.isArray(r.lives)) r.lives.forEach(live => r.items.push({ kind: 'live', live, ...live }));
        if (!r.items.length && (source.kind || source.site || source.live || source.key || source.url || source.groups || source.epg)) r.items.push(source);
    }
    delete r.sites;
    delete r.lives;
    r.items.forEach((item, i) => normalizeCspItem(item, i));
    return r;
}
function normalizeCspItem(item, index = 0) {
    item.kind = cspKind(item);
    item.enabled = item.enabled !== false;
    item.id = item.id || (item.kind + '_' + Date.now() + '_' + index);
    if (item.kind === 'live') return normalizeLiveItem(item, index);
    item.live = null;
    item.site = item.site && typeof item.site === 'object' ? item.site : rawObjectFromItem(item, ['webHome']);
    item.webHome = item.kind === 'webHome';
    item.key = item.key || siteValue(item, 'key', '__custom_csp_' + item.id);
    const inferredApi = String(siteValue(item, 'api', ''));
    item.name = item.name || siteValue(item, 'name', cspKindName(item.kind) + ' ' + (index + 1));
    item.type = Number(siteValue(item, 'type', 3));
    item.api = inferredApi;
    item.ext = siteValue(item, 'ext', '');
    item.jar = String(siteValue(item, 'jar', ''));
    item.homePage = String(siteValue(item, 'homePage', ''));
    if (item.homePage === 'true' || item.homePage === 'false') item.homePage = '';
    item.click = String(siteValue(item, 'click', ''));
    item.playUrl = String(siteValue(item, 'playUrl', ''));
    item.hide = Number(siteValue(item, 'hide', 0));
    item.indexs = Number(siteValue(item, 'indexs', 0));
    item.timeout = siteValue(item, 'timeout', '');
    item.searchable = Number(siteValue(item, 'searchable', item.webHome ? 0 : 1));
    item.changeable = Number(siteValue(item, 'changeable', 1));
    item.quickSearch = Number(siteValue(item, 'quickSearch', item.webHome ? 0 : 1));
    item.categories = Array.isArray(item.categories) ? item.categories : (Array.isArray(item.site.categories) ? item.site.categories : []);
    item.header = item.header || item.site.header || {};
    item.style = item.style || item.site.style || {};
    item.extensions = item.extensions !== undefined && item.extensions !== null ? item.extensions : (item.site ? item.site.extensions : null);
    item.extensionsText = item.extensionsText !== undefined && item.extensionsText !== null ? String(item.extensionsText) : extensionText(item.extensions);
    item.extensionsExpanded = item.extensionsExpanded === true || !!item.extensionsText.trim();
    item.extensionsInvalid = !!item.extensionsInvalid;
    item.headerText = JSON.stringify(item.header || {}, null, 2);
    item.styleText = JSON.stringify(item.style || {}, null, 2);
    syncCspSite(item);
    return item;
}
function normalizeLiveItem(item, index = 0) {
    item.kind = 'live';
    item.webHome = false;
    item.site = {};
    item.live = item.live && typeof item.live === 'object' ? item.live : rawObjectFromItem(item, ['webHome', 'key', 'homePage', 'playUrl', 'hide', 'searchable', 'changeable', 'quickSearch', 'indexs', 'categories', 'style']);
    item.name = item.name || liveValue(item, 'name', '直播 ' + (index + 1));
    item.type = liveValue(item, 'type', 0);
    item.playerType = liveValue(item, 'playerType', 2);
    item.url = String(liveValue(item, 'url', ''));
    item.api = String(liveValue(item, 'api', ''));
    item.ext = liveValue(item, 'ext', '');
    item.jar = String(liveValue(item, 'jar', ''));
    item.click = String(liveValue(item, 'click', ''));
    item.logo = String(liveValue(item, 'logo', ''));
    item.epg = String(liveValue(item, 'epg', ''));
    item.ua = String(liveValue(item, 'ua', 'okhttp'));
    item.origin = String(liveValue(item, 'origin', ''));
    item.referer = String(liveValue(item, 'referer', ''));
    item.timeZone = String(liveValue(item, 'timeZone', ''));
    item.keep = String(liveValue(item, 'keep', ''));
    item.timeout = liveValue(item, 'timeout', '');
    item.header = liveValue(item, 'header', {});
    item.catchup = liveValue(item, 'catchup', {});
    item.core = liveValue(item, 'core', {});
    item.groups = liveValue(item, 'groups', []);
    item.boot = liveValue(item, 'boot', '');
    item.pass = liveValue(item, 'pass', '');
    item.headerText = jsonText(item.header, {});
    item.catchupText = jsonText(item.catchup, {});
    item.coreText = jsonText(item.core, {});
    item.groupsText = jsonText(Array.isArray(item.groups) ? item.groups : [], []);
    syncCspLive(item);
    return item;
}
function syncCspItem(item) { return item.kind === 'live' ? syncCspLive(item) : syncCspSite(item); }
function assignOptional(object, key, value) {
    if (value === undefined || value === null || value === '') delete object[key];
    else object[key] = value;
}
function syncCspSite(item) {
    const site = { ...(item.site || {}) };
    item.kind = item.kind === 'webHome' ? 'webHome' : 'csp';
    item.webHome = item.kind === 'webHome';
    site.key = item.key;
    site.name = item.name;
    site.type = Number(item.type || 0);
    site.homePage = item.homePage || '';
    site.hide = Number(item.hide || 0);
    site.searchable = Number(item.searchable || 0);
    site.changeable = Number(item.changeable || 0);
    site.quickSearch = Number(item.quickSearch || 0);
    if (item.webHome) {
        site.api = '';
        site.ext = '';
        site.jar = '';
        delete site.click;
        delete site.playUrl;
        if (item.extensionsExpanded && hasJsonValue(item.extensions)) site.extensions = item.extensions;
        else delete site.extensions;
    } else {
        site.api = item.api || '';
        site.ext = item.ext || '';
        site.jar = item.jar || '';
        site.click = item.click || '';
        site.playUrl = item.playUrl || '';
        site.indexs = Number(item.indexs || 0);
        if (item.timeout !== '' && item.timeout !== null) site.timeout = Number(item.timeout || 0); else delete site.timeout;
        site.categories = Array.isArray(item.categories) ? item.categories : [];
        site.header = item.header || {};
        site.style = item.style || {};
    }
    item.live = null;
    ['url', 'logo', 'epg', 'ua', 'origin', 'referer', 'timeZone', 'keep', 'playerType', 'boot', 'pass', 'catchup', 'core', 'groups', 'liveText', 'catchupText', 'coreText', 'groupsText'].forEach(key => delete item[key]);
    item.site = site;
    item.siteText = JSON.stringify(site || {}, null, 2);
}
function syncCspLive(item) {
    const live = { ...(item.live || {}) };
    item.kind = 'live';
    item.webHome = false;
    live.name = item.name || '';
    assignOptional(live, 'type', item.type === '' || item.type === null || item.type === undefined ? '' : Number(item.type || 0));
    assignOptional(live, 'playerType', item.playerType === '' || item.playerType === null || item.playerType === undefined ? '' : Number(item.playerType || 0));
    assignOptional(live, 'url', item.url || '');
    assignOptional(live, 'api', item.api || '');
    assignOptional(live, 'ext', item.ext || '');
    assignOptional(live, 'jar', item.jar || '');
    assignOptional(live, 'click', item.click || '');
    assignOptional(live, 'logo', item.logo || '');
    assignOptional(live, 'epg', item.epg || '');
    assignOptional(live, 'ua', item.ua || '');
    assignOptional(live, 'origin', item.origin || '');
    assignOptional(live, 'referer', item.referer || '');
    assignOptional(live, 'timeZone', item.timeZone || '');
    assignOptional(live, 'keep', item.keep || '');
    if (item.timeout !== '' && item.timeout !== null) live.timeout = Number(item.timeout || 0); else delete live.timeout;
    if (item.boot === true || item.boot === false) live.boot = !!item.boot; else delete live.boot;
    if (item.pass === true || item.pass === false) live.pass = !!item.pass; else delete live.pass;
    assignOptionalJson(live, 'header', item.header);
    assignOptionalJson(live, 'catchup', item.catchup);
    assignOptionalJson(live, 'core', item.core);
    assignOptionalJson(live, 'groups', Array.isArray(item.groups) ? item.groups : []);
    item.site = {};
    item.live = live;
    item.headerText = jsonText(live.header, {});
    item.catchupText = jsonText(live.catchup, {});
    item.coreText = jsonText(live.core, {});
    item.groupsText = jsonText(live.groups, []);
    item.liveText = JSON.stringify(live || {}, null, 2);
}
function stripCspMeta(registry) {
    const copy = JSON.parse(JSON.stringify(registry || {}));
    delete copy.active;
    delete copy.enabledCount;
    delete copy.itemsCount;
    (copy.items || []).forEach(item => { delete item.headerText; delete item.styleText; delete item.siteText; delete item.liveText; delete item.catchupText; delete item.coreText; delete item.groupsText; delete item.extensionsText; delete item.extensionsExpanded; delete item.extensionsInvalid; });
    return copy;
}
function renderCspManage() {
    $('#cspEnabled').prop('checked', cspRegistry.enabled !== false);
    $('#cspInsertText').text((cspRegistry.insertIndex || 0) + 1);
    $('#cspSummary').text(`${cspRegistry.active || 0}/${cspRegistry.enabledCount || 0} 可用 · ${cspRegistry.items.length} 条`);
    $('#cspList').html(cspRegistry.items.map(buildCspCard).join('') || '<div class="empty-state">还没有站点注入条目</div>');
    $('#cspRaw').val(JSON.stringify(stripCspMeta(cspRegistry), null, 2));
    cspRawDirty = false;
    updateCspModeUi();
}
function cspItemValid(item) {
    if (item.kind === 'live') return !!item.name && (!!item.url || !!(Array.isArray(item.groups) && item.groups.length) || !!(item.live && Array.isArray(item.live.groups) && item.live.groups.length));
    return item.webHome ? !!item.homePage : !!item.api;
}
function buildCspCard(item, index) {
    const invalid = item.enabled && (!cspItemValid(item) || item.extensionsInvalid) ? ' invalid' : '';
    const title = item.name || cspKindName(item.kind);
    const source = item.webHome ? `<div class="source-actions"><button class="md-btn md-btn-tonal md-btn-compact" type="button" onclick="chooseCspFile(${index})">文件</button><button class="md-btn md-btn-tonal md-btn-compact" type="button" onclick="openCspCode(${index})">代码</button><button class="md-btn md-btn-tonal md-btn-compact" type="button" onclick="openCspLink(${index})">链接</button></div>` : '';
    const typeButtons = `<div class="segmented csp-type-toggle"><button class="segment ${item.kind === 'webHome' ? 'active' : ''}" onclick="setCspKind(${index},'webHome')" type="button">WebHome</button><button class="segment ${item.kind === 'csp' ? 'active' : ''}" onclick="setCspKind(${index},'csp')" type="button">通用 CSP</button><button class="segment ${item.kind === 'live' ? 'active' : ''}" onclick="setCspKind(${index},'live')" type="button">直播</button></div>`;
    const nameRow = item.kind === 'live'
        ? `<div class="field-row compact">${buildLiveTextField('name', '名称', item.name, '直播名称', true)}</div>`
        : `<div class="field-row compact"><input class="md-input csp-field" data-key="name" value="${escHtml(item.name)}" placeholder="名称"><input class="md-input csp-field" data-key="key" value="${escHtml(item.key)}" placeholder="Key"></div>`;
    const extensions = item.webHome ? buildCspExtensionsFields(item, index) : '';
    const homeLine = item.kind === 'live' ? '' : `<div class="csp-home-line">${buildHomeCheck(item, index)}${source}</div>`;
    const homePage = item.kind === 'live' ? '' : `<div class="md-field"><input class="md-input csp-field" data-key="homePage" value="${escHtml(item.homePage)}" placeholder="${item.webHome ? 'WebHome 地址' : 'WebHome 首页地址，可选'}"></div>`;
    const fields = item.kind === 'live' ? buildLiveFields(item) : item.webHome ? buildAdvancedSiteFields(item) : buildCommonCspFields(item);
    return `<div class="manage-card csp-card${invalid}" data-index="${index}"><div class="csp-head"><div class="csp-title-block"><label class="check-row"><input class="csp-field" data-key="enabled" type="checkbox" ${item.enabled ? 'checked' : ''}><span>${escHtml(title)}</span></label>${typeButtons}</div><div class="card-actions"><button class="file-action" type="button" onclick="moveCspItem(${index},-1)">上移</button><button class="file-action" type="button" onclick="moveCspItem(${index},1)">下移</button><button class="file-action danger" type="button" onclick="removeCspItem(${index})">删除</button></div></div>${nameRow}${extensions}${homeLine}${homePage}${fields}</div>`;
}
function buildHomeCheck(item, index) { return `<label class="check-row"><input class="csp-home" type="checkbox" ${cspRegistry.homeKey === item.key ? 'checked' : ''} onchange="setCspHome(${index},this.checked)"><span>设为首页</span></label>`; }
function buildCspExtensionsFields(item, index) {
    const text = item.extensionsText || '';
    const detail = item.extensionsExpanded ? `<textarea class="code-area csp-field compact-code csp-extensions-text" data-key="extensionsText" spellcheck="false" placeholder='["https://example.com/webhome/site.js"]'>${escHtml(text)}</textarea>${item.extensionsInvalid ? '<div class="field-error">extensions JSON 格式无效</div>' : ''}` : '';
    return `<div class="csp-extensions-panel"><div class="csp-extensions-head"><button class="toggle-pill ${item.extensionsExpanded ? 'on' : 'off'}" type="button" onclick="toggleCspExtensions(${index})">扩展</button><span>填写 extensions 数组，支持 JS 链接简写或对象配置</span></div>${detail}</div>`;
}
function buildCommonCspFields(item) {
    return `<div class="field-row compact"><input class="md-input mini-input csp-field" data-key="type" type="number" value="${escHtml(item.type)}" placeholder="类型"><input class="md-input csp-field" data-key="api" value="${escHtml(item.api)}" placeholder="API / CSP 类名"></div><div class="field-row compact"><input class="md-input csp-field" data-key="jar" value="${escHtml(item.jar)}" placeholder="Jar"><input class="md-input csp-field" data-key="ext" value="${escHtml(typeof item.ext === 'string' ? item.ext : JSON.stringify(item.ext))}" placeholder="Ext"></div><div class="field-row compact"><input class="md-input csp-field" data-key="click" value="${escHtml(item.click)}" placeholder="点击脚本"><input class="md-input csp-field" data-key="playUrl" value="${escHtml(item.playUrl)}" placeholder="播放前缀"></div><div class="field-row compact"><input class="md-input mini-input csp-field" data-key="indexs" type="number" value="${escHtml(item.indexs)}" placeholder="索引"><input class="md-input mini-input csp-field" data-key="timeout" type="number" value="${escHtml(item.timeout)}" placeholder="超时秒"><input class="md-input csp-field" data-key="categories" value="${escHtml((item.categories || []).join(','))}" placeholder="分类，逗号分隔"></div><div class="flag-grid"><label class="check-row"><input class="csp-field" data-key="hide" type="checkbox" ${item.hide ? 'checked' : ''}><span>隐藏</span></label><label class="check-row"><input class="csp-field" data-key="searchable" type="checkbox" ${item.searchable ? 'checked' : ''}><span>搜索</span></label><label class="check-row"><input class="csp-field" data-key="changeable" type="checkbox" ${item.changeable ? 'checked' : ''}><span>换源</span></label><label class="check-row"><input class="csp-field" data-key="quickSearch" type="checkbox" ${item.quickSearch ? 'checked' : ''}><span>快搜</span></label></div><details class="advanced-panel"><summary>高级参数</summary><label class="form-label">Header JSON</label><textarea class="code-area csp-field compact-code" data-key="headerText" spellcheck="false" placeholder="Header JSON">${escHtml(item.headerText)}</textarea><label class="form-label">Style JSON</label><textarea class="code-area csp-field compact-code" data-key="styleText" spellcheck="false" placeholder="Style JSON">${escHtml(item.styleText)}</textarea>${buildAdvancedSiteFields(item, false)}</details>`;
}
function buildLiveFields(item) {
    return `<div class="field-row compact live-common-row">${buildLiveTextField('url', '直播地址', item.url, 'http(s):// 或本地路径', true, 'URL/分组至少一项')}</div><div class="field-row compact">${buildLiveTextField('ua', 'UA', item.ua, 'okhttp')}${buildLiveTextField('epg', 'EPG', item.epg, 'http://...{name}...')}${buildLiveTextField('logo', 'Logo', item.logo, 'https://.../{name}.png')}</div>${buildAdvancedLiveFields(item)}`;
}
function buildAdvancedSiteFields(item, wrap = true) {
    const field = `<label class="form-label">完整 Site JSON</label><textarea class="code-area csp-field compact-code" data-key="siteText" spellcheck="false" placeholder="完整站点 JSON，可参考 webhome-devkit/docs/应用完整开发文档.md 里的其它字段">${escHtml(item.siteText)}</textarea>`;
    return wrap ? `<details class="advanced-panel"><summary>高级 Site JSON</summary>${field}</details>` : field;
}
function buildAdvancedLiveFields(item) {
    return `<details class="advanced-panel"><summary>高级直播参数（可选）</summary><div class="field-row compact advanced-field-row">${buildLiveTextField('api', 'API', item.api, 'raw 兼容字段')}${buildLiveTextField('ext', 'Ext', typeof item.ext === 'string' ? item.ext : JSON.stringify(item.ext), 'raw 兼容字段')}${buildLiveTextField('timeout', '超时秒', item.timeout, '', false, '可选', 'number', 'short')}</div><div class="field-row compact advanced-field-row">${buildLiveTextField('jar', 'Jar', item.jar, 'raw 兼容字段')}${buildLiveTextField('click', '点击脚本', item.click, '可选')}${buildLiveTextField('keep', 'Keep', item.keep, '分组@@频道@@线路，可选')}</div><div class="field-row compact advanced-field-row">${buildLiveTextField('origin', 'Origin', item.origin, '可选')}${buildLiveTextField('referer', 'Referer', item.referer, '可选')}${buildLiveTextField('timeZone', '时区', item.timeZone, 'Asia/Shanghai')}</div><div class="flag-grid live-flag-grid"><label class="check-row"><input class="csp-field" data-key="boot" type="checkbox" ${item.boot ? 'checked' : ''}><span>启动进入</span></label><label class="check-row"><input class="csp-field" data-key="pass" type="checkbox" ${item.pass ? 'checked' : ''}><span>跳过分组</span></label></div>${buildFieldLabel('Header JSON')}${buildJsonTextarea('headerText', item.headerText, 'Header JSON，例如 {"User-Agent":"okhttp"}')}${buildFieldLabel('Catchup JSON')}${buildJsonTextarea('catchupText', item.catchupText, 'Catchup JSON')}${buildFieldLabel('Core JSON')}${buildJsonTextarea('coreText', item.coreText, 'Core JSON')}${buildFieldLabel('Groups JSON')}${buildJsonTextarea('groupsText', item.groupsText, 'Groups 数组')}${buildFieldLabel('完整 Live JSON')}${buildJsonTextarea('liveText', item.liveText, '完整直播 JSON，可包含 raw JSON 的所有字段')}</details>`;
}
function updateCspGlobal() {
    if (!cspRegistry) return;
    cspRegistry.enabled = $('#cspEnabled').prop('checked');
    cspRegistry.insertIndex = Math.max(0, Math.min(9, Number(cspRegistry.insertIndex || 0)));
    $('#cspInsertText').text(cspRegistry.insertIndex + 1);
    $('#cspRaw').val(JSON.stringify(stripCspMeta(cspRegistry), null, 2));
}
function stepCspInsert(delta) { if (!cspRegistry) return; cspRegistry.insertIndex = Math.max(0, Math.min(9, Number(cspRegistry.insertIndex || 0) + delta)); updateCspGlobal(); }
function setCspMode(next) {
    next = next === 'json' ? 'json' : 'form';
    if (next === cspMode) return;
    if (cspMode === 'form') {
        if (!syncCspFromCards(true, true)) return;
    } else {
        if (!parseCspRawIntoRegistry()) return;
    }
    cspMode = next;
    renderCspManage();
}
function updateCspModeUi() {
    $('#cspModeForm').toggleClass('active', cspMode === 'form');
    $('#cspModeJson').toggleClass('active', cspMode === 'json');
    $('#cspFormPanel').toggle(cspMode === 'form');
    $('#cspJsonPanel').toggle(cspMode === 'json');
}
function parseCspRawIntoRegistry() {
    try {
        cspRegistry = normalizeCspRegistry(JSON.parse($('#cspRaw').val().trim() || '{}'));
        cspRawDirty = false;
        return true;
    } catch (e) {
        warnToast('站点注入 JSON 格式无效');
        return false;
    }
}
function formatCspJson() {
    if (!parseCspRawIntoRegistry()) return;
    $('#cspRaw').val(JSON.stringify(stripCspMeta(cspRegistry), null, 2));
    warnToast('JSON 已格式化');
}
function syncCspFromCards(updateRaw = true, validate = false) {
    if (!cspRegistry) return;
    let valid = true;
    cspRegistry.enabled = $('#cspEnabled').prop('checked');
    cspRegistry.insertIndex = Math.max(0, Math.min(9, Number(cspRegistry.insertIndex || 0)));
    $('#cspList .csp-card').each(function () {
        const item = cspRegistry.items[Number($(this).data('index'))];
        item.extensionsInvalid = false;
        $(this).find('.csp-field').each(function () {
            const key = $(this).data('key');
            if (this.type === 'checkbox') item[key] = ['enabled', 'boot', 'pass'].includes(key) ? this.checked : (this.checked ? 1 : 0);
            else if (['type', 'playerType', 'hide', 'searchable', 'changeable', 'quickSearch', 'indexs'].includes(key)) item[key] = this.value === '' ? '' : Number(this.value || 0);
            else if (key === 'timeout') item[key] = this.value === '' ? '' : Number(this.value || 0);
            else if (key === 'categories') item[key] = this.value.split(',').map(x => x.trim()).filter(Boolean);
            else if (key === 'headerText') item.header = parseJsonField(this.value, {});
            else if (key === 'styleText') item.style = parseJsonField(this.value, {});
            else if (key === 'siteText') item.site = parseJsonField(this.value, item.site || {});
            else if (key === 'extensionsText') {
                item.extensionsText = this.value.trim();
                try { item.extensions = item.extensionsExpanded && item.extensionsText ? parseCspExtensions(item.extensionsText) : null; }
                catch (e) { item.extensionsInvalid = true; valid = false; }
            }
            else if (key === 'catchupText') item.catchup = parseOptionalJsonField(this.value, item.catchup || {}, {});
            else if (key === 'coreText') item.core = parseOptionalJsonField(this.value, item.core || {}, {});
            else if (key === 'groupsText') item.groups = parseOptionalJsonField(this.value, item.groups || [], []);
            else if (key === 'liveText') item.live = parseJsonField(this.value, item.live || {});
            else item[key] = this.value.trim();
        });
        if (!item.extensionsExpanded) {
            item.extensionsText = '';
            item.extensions = null;
        }
        if (!item.extensionsInvalid) syncCspItem(item);
    });
    if (validate && !valid) {
        warnToast('extensions JSON 格式无效');
        renderCspManage();
        return false;
    }
    if (updateRaw) {
        $('#cspRaw').val(JSON.stringify(stripCspMeta(cspRegistry), null, 2));
        cspRawDirty = false;
    }
    return true;
}
function parseJsonField(text, fallback) { try { return text && text.trim() ? JSON.parse(text) : fallback; } catch (e) { warnToast('JSON 格式无效，已保留为空对象'); return fallback; } }
function parseOptionalJsonField(text, fallback, emptyValue) {
    try { return text && text.trim() ? JSON.parse(text) : emptyValue; }
    catch (e) { warnToast('JSON 格式无效，已保留原值'); return fallback; }
}
function parseCspExtensions(text) {
    const value = String(text || '').trim();
    if (!value) return null;
    const array = [];
    if (!value.startsWith('[') && !value.startsWith('{') && !value.startsWith('"')) {
        array.push(value);
        return array;
    }
    const element = JSON.parse(value);
    if (Array.isArray(element)) return element;
    if (element && typeof element === 'object' && Array.isArray(element.extensions)) return element.extensions;
    array.push(element);
    return array;
}
function addCspItem(kind) {
    if (!cspRegistry) cspRegistry = normalizeCspRegistry({});
    if (cspMode === 'json') {
        if (!parseCspRawIntoRegistry()) return;
        cspMode = 'form';
    }
    syncCspFromCards(false);
    if (kind === true) kind = 'webHome';
    if (kind === false) kind = 'csp';
    if (!CSP_KINDS.includes(kind)) kind = 'webHome';
    const n = cspRegistry.items.filter(x => x.kind === kind).length + 1;
    const name = cspKindName(kind) + ' ' + n;
    const seed = { kind, webHome: kind === 'webHome', name, homePage: '' };
    if (kind === 'live') seed.live = liveDefaultObject(name);
    cspRegistry.items.push(normalizeCspItem(seed, cspRegistry.items.length));
    renderCspManage();
}
function removeCspItem(index) { syncCspFromCards(false); const item = cspRegistry.items[index]; if (item && item.kind !== 'live' && cspRegistry.homeKey === item.key) cspRegistry.homeKey = ''; cspRegistry.items.splice(index, 1); renderCspManage(); }
function moveCspItem(index, delta) { syncCspFromCards(false); const targetIndex = index + delta; if (targetIndex < 0 || targetIndex >= cspRegistry.items.length) return; const item = cspRegistry.items.splice(index, 1)[0]; cspRegistry.items.splice(targetIndex, 0, item); renderCspManage(); }
function setCspHome(index, checked) { syncCspFromCards(false); const item = cspRegistry.items[index]; if (!item || item.kind === 'live') return; cspRegistry.homeKey = checked ? item.key : ''; renderCspManage(); }
function toggleCspExtensions(index) {
    syncCspFromCards(false);
    const item = cspRegistry.items[index];
    if (!item || !item.webHome) return;
    item.extensionsExpanded = !item.extensionsExpanded;
    if (!item.extensionsExpanded) {
        item.extensionsText = '';
        item.extensions = null;
    }
    renderCspManage();
}
function setCspKind(index, kind) {
    syncCspFromCards(false);
    const item = cspRegistry.items[index];
    if (kind === true) kind = 'webHome';
    if (kind === false) kind = 'csp';
    if (!item || item.kind === kind || !CSP_KINDS.includes(kind)) return;
    const oldKind = item.kind;
    const oldAuto = /^(WebHome|通用 CSP|直播) \d+$/.test(item.name || '');
    if (item.kind !== 'live' && kind === 'live' && cspRegistry.homeKey === item.key) cspRegistry.homeKey = '';
    item.kind = kind;
    item.webHome = kind === 'webHome';
    if (kind === 'webHome') {
        if (oldKind === 'live') item.type = 3;
        item.api = '';
        item.ext = '';
        item.jar = '';
        item.searchable = 0;
        item.quickSearch = 0;
    } else if (kind === 'csp') {
        if (oldKind === 'live') item.type = 3;
        item.searchable = 1;
        item.quickSearch = 1;
    } else if (kind === 'live') {
        item.live = { ...liveDefaultObject(item.name), ...(item.live || {}) };
        item.type = liveValue(item, 'type', 0);
        item.playerType = liveValue(item, 'playerType', 2);
        item.ua = String(liveValue(item, 'ua', 'okhttp'));
    }
    if (oldAuto) {
        const n = cspRegistry.items.filter((x, i) => i !== index && x.kind === kind).length + 1;
        item.name = cspKindName(kind) + ' ' + n;
    }
    normalizeCspItem(item, index);
    renderCspManage();
}
function chooseCspFile(index) {
    syncCspFromCards(false);
    pendingCspIndex = index;
    $('#csp_file_uploader').val('').click();
}
function onCspFileSelected() {
    const file = $('#csp_file_uploader')[0].files[0];
    if (!file || pendingCspIndex < 0) return;
    const reader = new FileReader();
    reader.onload = e => saveCspPage(pendingCspIndex, { code: e.target.result || '' }, '文件已载入');
    reader.onerror = () => warnToast('文件读取失败');
    reader.readAsText(file);
}
function openCspCode(index) {
    syncCspFromCards(false);
    pendingCspIndex = index;
    $('#cspCodeContent').val('');
    openDialog('cspCodeDialog');
}
function confirmCspCode(yes) {
    closeDialog('cspCodeDialog');
    if (yes !== 1 || pendingCspIndex < 0) return;
    saveCspPage(pendingCspIndex, { code: $('#cspCodeContent').val() }, '代码已保存');
}
function openCspLink(index) {
    syncCspFromCards(false);
    pendingCspIndex = index;
    const item = cspRegistry.items[index] || {};
    $('#cspLinkContent').val(/^file:\/\//i.test(item.homePage || '') ? '' : (item.homePage || ''));
    openDialog('cspLinkDialog');
}
function confirmCspLink(yes) {
    closeDialog('cspLinkDialog');
    const link = $('#cspLinkContent').val().trim();
    if (yes !== 1 || pendingCspIndex < 0 || !link) return;
    saveCspPage(pendingCspIndex, { link }, '链接已设置');
}
function saveCspPage(index, data, message) {
    const item = cspRegistry && cspRegistry.items[index];
    if (!item) return;
    postJson('/manage/csp/page', { id: item.id, ...data }, res => {
        item.id = res.id || item.id;
        item.homePage = res.homePage || item.homePage;
        syncCspSite(item);
        renderCspManage();
        warnToast(message || 'WebHome 已更新，请保存生效');
        pendingCspIndex = -1;
    }, 'WebHome 保存失败');
}
function saveCspManage() {
    if (cspMode === 'form') {
        if (!syncCspFromCards(true, true)) return;
    } else if (!parseCspRawIntoRegistry()) return;
    cspRegistry.items.forEach(syncCspItem);
    postJson('/manage/csp', { registry: JSON.stringify(stripCspMeta(cspRegistry)) }, data => { cspRegistry = normalizeCspRegistry(data); renderCspManage(); warnToast('站点注入已保存'); });
}

function loadProxyManage(force = false) {
    if (proxyLoadedKey === activeKey() && !force) return;
    getJson('/manage/proxy?' + targetQuery(), data => {
        proxyLoadedKey = activeKey();
        proxyEnabled = !!data.enabled;
        $('#proxyUrl').val(data.url || '');
        $('#proxyRules').val(formatProxyRules(parseProxyRules(data.rules || '')));
        proxyRules = parseProxyRules(data.rules || '');
        renderProxyManage(data);
    });
}
function updateProxySummary(data = {}) {
    const count = data.count != null ? data.count : proxyRules.filter(rule => rule.hosts || rule.url).length;
    const configured = count > 0 || !!cleanProxyUrl($('#proxyUrl').val());
    $('#proxySummary').text(`${proxyEnabled ? '启用' : '禁用'} · ${count || 0} 条 · ${configured ? '已配置' : '未配置'}`);
}
function renderProxyManage(data = {}) {
    $('#proxyEnabled').text(proxyEnabled ? '启用' : '禁用').toggleClass('on', proxyEnabled).toggleClass('off', !proxyEnabled);
    $('#proxyModeForm').toggleClass('active', proxyMode === 'form');
    $('#proxyModeText').toggleClass('active', proxyMode === 'text');
    $('#proxyFormPanel').toggle(proxyMode === 'form');
    $('#proxyTextPanel').toggle(proxyMode === 'text');
    if (!proxyRules.length) proxyRules = [proxyRule('', '')];
    $('#proxyRuleList').html(proxyRules.map(buildProxyRule).join(''));
    updateProxySummary(data);
}
function toggleProxyEnabled() {
    proxyEnabled = !proxyEnabled;
    renderProxyManage();
}
function setProxyMode(next) {
    if (next === proxyMode) return;
    if (proxyMode === 'form') syncProxyTextFromForm();
    else proxyRules = parseProxyRules($('#proxyRules').val());
    proxyMode = next === 'text' ? 'text' : 'form';
    if (proxyMode === 'text') $('#proxyRules').val(formatProxyRules(proxyRules));
    renderProxyManage();
}
function proxyRule(hosts, url) { return { hosts: hosts || '', url: url || '' }; }
function buildProxyRule(rule, index) {
    return `<div class="proxy-rule-card" data-index="${index}"><div class="proxy-rule-head"><span>规则 ${index + 1}</span><div class="card-actions"><button class="file-action" onclick="moveProxyRule(${index},-1)" type="button">上移</button><button class="file-action" onclick="moveProxyRule(${index},1)" type="button">下移</button><button class="file-action danger" onclick="removeProxyRule(${index})" type="button">删除</button></div></div><div class="proxy-rule-fields"><div><label class="form-label">域名 / Host</label><input class="md-input proxy-rule-hosts" value="${escHtml(rule.hosts)}" placeholder="例如 * 或 api.example.com,*.example.org"></div><div><label class="form-label">代理地址</label><input class="md-input proxy-rule-url" value="${escHtml(rule.url)}" placeholder="留空时使用默认代理地址"></div></div></div>`;
}
function syncProxyRulesFromForm() {
    const items = [];
    $('#proxyRuleList .proxy-rule-card').each(function () {
        items.push(proxyRule($(this).find('.proxy-rule-hosts').val().trim(), $(this).find('.proxy-rule-url').val().trim()));
    });
    proxyRules = items.length ? items : [proxyRule('', '')];
}
function syncProxyTextFromForm() {
    syncProxyRulesFromForm();
    $('#proxyRules').val(formatProxyRules(proxyRules));
}
function addProxyRule() {
    syncProxyRulesFromForm();
    proxyRules.push(proxyRule('', cleanProxyUrl($('#proxyUrl').val())));
    renderProxyManage();
}
function showProxySuggestDialog() {
    if (!ensureProxyDefaultUrl()) return;
    getJson('/manage/proxy/suggest/sites?' + targetQuery(), data => {
        proxySuggestSites = Array.isArray(data.sites) ? data.sites : [];
        renderProxySuggestList();
        openDialog('proxySuggestDialog');
    }, '自动建议加载失败');
}
function renderProxySuggestList() {
    const rows = [];
    if (proxySuggestSites.length) rows.push(buildProxySuggestRow({ key: 'all', name: '全部站点', home: false, all: true }));
    proxySuggestSites.forEach(site => rows.push(buildProxySuggestRow(site)));
    $('#proxySuggestList').html(rows.length ? rows.join('') : '<div class="empty-state compact">暂无可选站点</div>');
}
function buildProxySuggestRow(site) {
    const key = escPath(site.key || '');
    const name = escHtml(site.name || site.key || '未命名站点');
    const meta = site.all ? '扫描当前接口内所有站点' : (site.home ? '当前首页站点' : escHtml(site.key || ''));
    return `<button class="proxy-suggest-row" onclick="applyProxySuggest('${key}',${site.all ? 'true' : 'false'})" type="button"><span>${name}</span><small>${meta}</small></button>`;
}
function applyProxySuggest(key, all) {
    if (!ensureProxyDefaultUrl()) return;
    const query = targetQuery(all ? { all: 'true' } : { key });
    getJson('/manage/proxy/suggest?' + query, data => {
        const hosts = Array.isArray(data.hosts) ? data.hosts : [];
        if (!hosts.length) { warnToast('未发现可代理域名'); return; }
        const added = appendProxyHosts(hosts, cleanProxyUrl($('#proxyUrl').val()));
        proxyEnabled = true;
        proxyMode = 'form';
        renderProxyManage({ count: proxyRules.filter(rule => rule.hosts || rule.url).length });
        closeDialog('proxySuggestDialog');
        warnToast(`已新增 ${added} 个域名，共 ${countProxyHosts()} 个`);
    }, '自动建议失败');
}
function showProxyRecognizeDialog() {
    $('#proxyRecognizeInput').val('');
    openDialog('proxyRecognizeDialog');
}
function applyProxyRecognize() {
    const text = $('#proxyRecognizeInput').val();
    const items = parseProxyDetectedRules(text, true);
    if (!items.length) { warnToast('未识别到 Proxy 规则'); return; }
    if (proxyMode === 'text') proxyRules = parseProxyRules($('#proxyRules').val());
    else syncProxyRulesFromForm();
    const before = proxyRules.filter(rule => rule.hosts || rule.url).length;
    proxyRules = mergeProxyRules(proxyRules, items);
    const added = proxyRules.filter(rule => rule.hosts || rule.url).length - before;
    const firstUrl = firstProxyRuleUrl(items);
    if (!cleanProxyUrl($('#proxyUrl').val()) && firstUrl) $('#proxyUrl').val(firstUrl);
    proxyEnabled = true;
    proxyMode = 'form';
    $('#proxyRules').val(formatProxyRules(proxyRules));
    renderProxyManage({ count: proxyRules.filter(rule => rule.hosts || rule.url).length });
    closeDialog('proxyRecognizeDialog');
    warnToast(`已识别 ${items.length} 条，新增 ${Math.max(added, 0)} 条`);
}
function ensureProxyDefaultUrl() {
    if (isValidProxyUrl(cleanProxyUrl($('#proxyUrl').val()))) return true;
    warnToast('请先填写默认代理地址');
    return false;
}
function isValidProxyUrl(url) {
    try {
        const parsed = new URL(String(url || '').trim());
        return /^(https?|socks)\w*$/i.test(parsed.protocol.replace(':', '')) && !!parsed.hostname && !!parsed.port;
    } catch (e) {
        return false;
    }
}
function appendProxyHosts(hosts, url) {
    if (proxyMode === 'text') proxyRules = parseProxyRules($('#proxyRules').val());
    else syncProxyRulesFromForm();
    proxyRules = proxyRules.filter(rule => rule.hosts || rule.url);
    const exists = new Set();
    proxyRules.forEach(rule => splitProxyValue(rule.hosts).forEach(host => exists.add(normalizeProxyHost(host))));
    let added = 0;
    hosts.forEach(host => {
        const value = String(host || '').trim();
        const key = normalizeProxyHost(value);
        if (!value || !key || exists.has(key)) return;
        proxyRules.push(proxyRule(value, url));
        exists.add(key);
        added++;
    });
    if (!proxyRules.length) proxyRules.push(proxyRule('', ''));
    return added;
}
function normalizeProxyHost(host) {
    return String(host || '').trim().toLowerCase();
}
function countProxyHosts() {
    const hosts = new Set();
    proxyRules.forEach(rule => splitProxyValue(rule.hosts).forEach(host => {
        const value = normalizeProxyHost(host);
        if (value) hosts.add(value);
    }));
    return hosts.size;
}
function removeProxyRule(index) {
    syncProxyRulesFromForm();
    proxyRules.splice(index, 1);
    if (!proxyRules.length) proxyRules.push(proxyRule('', ''));
    renderProxyManage();
}
function moveProxyRule(index, delta) {
    syncProxyRulesFromForm();
    const next = index + delta;
    if (next < 0 || next >= proxyRules.length) return;
    const item = proxyRules.splice(index, 1)[0];
    proxyRules.splice(next, 0, item);
    renderProxyManage();
}
function parseProxyRules(text) {
    const raw = String(text || '').trim();
    if (!raw) return [];
    const detected = parseProxyDetectedRules(raw, raw[0] === '{' || raw[0] === '[');
    if (detected.length) return detected;
    if (raw[0] === '{' || raw[0] === '[' || raw.includes('"proxy"')) return [];
    const rows = [];
    raw.split(/\r?\n/).forEach(line => {
        const value = line.trim();
        if (!value || value.startsWith('#')) return;
        const parts = value.split(/\s+/, 2);
        if (parts.length === 1 && looksLikeProxyUrl(parts[0])) rows.push(proxyRule('*', parts[0]));
        else rows.push(proxyRule(parts[0], parts.length > 1 ? parts[1] : ''));
    });
    return rows;
}
function parseProxyDetectedRules(text, notifyInvalid = false) {
    const raw = String(text || '').trim();
    if (!raw) return [];
    let items = parseProxyJson(raw, false);
    if (items.length) return items;
    const proxyArray = extractNamedJsonArray(raw, 'proxy');
    if (proxyArray) {
        items = parseProxyJson(`{"proxy":${proxyArray}}`, false);
        if (items.length) return items;
    }
    const array = extractFirstJsonArray(raw);
    if (array && /"hosts"|"urls"/.test(array)) {
        items = parseProxyJson(array, false);
        if (items.length) return items;
    }
    const objects = extractProxyJsonObjects(raw);
    if (objects.length) {
        items = parseProxyJson(`[${objects.join(',')}]`, false);
        if (items.length) return items;
    }
    if (notifyInvalid && (raw[0] === '{' || raw[0] === '[' || raw.includes('"proxy"'))) warnToast('Proxy JSON 格式无效');
    return [];
}
function parseProxyJson(text, notifyInvalid = true) {
    try {
        const root = JSON.parse(trimJsonTrailingComma(text));
        const array = Array.isArray(root) ? root : (Array.isArray(root.proxy) ? root.proxy : [root]);
        return array.map(item => proxyRule(joinProxyValue(item.hosts), joinProxyValue(item.urls))).filter(item => item.hosts || item.url);
    } catch (e) {
        if (notifyInvalid) warnToast('Proxy JSON 格式无效');
        return [];
    }
}
function trimJsonTrailingComma(text) {
    let value = String(text || '').trim();
    while (value.endsWith(',')) value = value.slice(0, -1).trim();
    return value;
}
function extractNamedJsonArray(text, key) {
    const marker = `"${key}"`;
    let search = 0;
    while (search >= 0 && search < text.length) {
        const index = text.indexOf(marker, search);
        if (index < 0) return '';
        const colon = text.indexOf(':', index + marker.length);
        if (colon < 0) return '';
        const start = nextNonSpaceIndex(text, colon + 1);
        if (start >= 0 && text[start] === '[') {
            const end = findJsonClosing(text, start, '[', ']');
            return end > start ? text.slice(start, end + 1) : '';
        }
        search = colon + 1;
    }
    return '';
}
function extractFirstJsonArray(text) {
    let start = text.indexOf('[');
    while (start >= 0) {
        const end = findJsonClosing(text, start, '[', ']');
        if (end > start) return text.slice(start, end + 1);
        start = text.indexOf('[', start + 1);
    }
    return '';
}
function extractProxyJsonObjects(text) {
    const objects = [];
    let start = text.indexOf('{');
    while (start >= 0) {
        const end = findJsonClosing(text, start, '{', '}');
        if (end <= start) break;
        const object = text.slice(start, end + 1);
        if (/"hosts"|"urls"/.test(object)) objects.push(object);
        start = text.indexOf('{', end + 1);
    }
    return objects;
}
function nextNonSpaceIndex(text, start) {
    for (let i = start; i < text.length; i++) if (!/\s/.test(text[i])) return i;
    return -1;
}
function findJsonClosing(text, start, open, close) {
    let inString = false, escaped = false, depth = 0;
    for (let i = start; i < text.length; i++) {
        const c = text[i];
        if (inString) {
            if (escaped) escaped = false;
            else if (c === '\\') escaped = true;
            else if (c === '"') inString = false;
            continue;
        }
        if (c === '"') { inString = true; continue; }
        if (c === open) depth++;
        else if (c === close && --depth === 0) return i;
    }
    return -1;
}
function mergeProxyRules(current, incoming) {
    const result = (current || []).filter(rule => rule.hosts || rule.url);
    const exists = new Set(result.map(proxyRuleKey));
    incoming.forEach(rule => {
        const item = proxyRule(rule.hosts, rule.url);
        const key = proxyRuleKey(item);
        if (!item.hosts && !item.url || exists.has(key)) return;
        result.push(item);
        exists.add(key);
    });
    return result.length ? result : [proxyRule('', '')];
}
function proxyRuleKey(rule) {
    return `${splitProxyValue(rule.hosts).map(normalizeProxyHost).join('|')}=>${splitProxyValue(rule.url).join('|')}`;
}
function firstProxyRuleUrl(items) {
    for (const item of items || []) {
        const urls = splitProxyValue(item.url);
        if (urls.length) return urls[0];
    }
    return '';
}
function joinProxyValue(value) {
    if (Array.isArray(value)) return value.map(item => String(item).trim()).filter(Boolean).join(',');
    return value == null ? '' : String(value).trim();
}
function splitProxyValue(value) {
    return String(value || '').split(',').map(item => item.trim()).filter(Boolean);
}
function formatProxyRules(items) {
    const proxy = (items || []).map(item => {
        const hosts = splitProxyValue(item.hosts || '*');
        const urls = splitProxyValue(item.url);
        if (!hosts.length && !urls.length) return null;
        const rule = { hosts: hosts.length ? hosts : ['*'] };
        if (urls.length) rule.urls = urls;
        return rule;
    }).filter(Boolean);
    return proxy.length ? JSON.stringify({ proxy }, null, 2) : '';
}
function cleanProxyUrl(url) {
    const value = String(url || '').trim();
    return value.toLowerCase() === 'socks5://' ? '' : value;
}
function looksLikeProxyUrl(text) {
    return /^(https?|socks)\w*:\/\//i.test(String(text || '').trim());
}
function saveProxyManage() {
    if (proxyMode === 'form') syncProxyTextFromForm();
    else proxyRules = parseProxyRules($('#proxyRules').val());
    const rules = proxyMode === 'form' ? formatProxyRules(proxyRules) : $('#proxyRules').val().trim();
    postJson('/manage/proxy', { enabled: proxyEnabled ? 'true' : 'false', url: cleanProxyUrl($('#proxyUrl').val()), rules }, data => {
        proxyEnabled = !!data.enabled;
        proxyRules = parseProxyRules(data.rules || rules);
        $('#proxyRules').val(formatProxyRules(proxyRules));
        renderProxyManage(data);
        warnToast('Proxy 已保存');
    }, 'Proxy 保存失败');
}

function loadConfigsManage(force = false) {
    if (configsLoadedKey === activeKey() && !force) return;
    getJson('/manage/configs?' + targetQuery(), data => {
        configsLoadedKey = activeKey();
        configsData = Array.isArray(data.items) ? data.items : [];
        renderConfigsManage();
    }, '接口配置加载失败');
}
function renderConfigsManage() {
    const filtered = configsData.filter(item => Number(item.type || 0) === Number(configFilter));
    const active = configsData.filter(item => item.active).length;
    $('#configsSummary').text(`${configsData.length} 个接口 · 当前启用 ${active}`);
    $('#configFilterVod').toggleClass('active', configFilter === 0);
    $('#configFilterLive').toggleClass('active', configFilter === 1);
    $('#configFilterWall').toggleClass('active', configFilter === 2);
    $('#configList').html(filtered.map(buildConfigCard).join('') || '<div class="empty-state">暂无接口配置</div>');
}
function setConfigFilter(type) {
    configFilter = Number(type);
    renderConfigsManage();
}
function buildConfigCard(item) {
    const url = item.url || '';
    const name = item.name || '';
    const type = Number(item.type || 0);
    const title = name || url || '未命名接口';
    const active = item.active ? ' active' : '';
    return `<div class="config-card${active}">
        <div class="config-main">
            <div class="config-title-line"><span class="config-type">${escHtml(item.typeName || configTypeName(type))}</span><strong>${escHtml(title)}</strong>${item.active ? '<em>当前</em>' : ''}</div>
            <div class="config-url">${escHtml(url)}</div>
        </div>
        <div class="config-actions">
            <button class="file-action" type="button" onclick="useConfig(${type},'${escPath(url)}')"${item.active ? ' disabled' : ''}>启用</button>
            <button class="file-action" type="button" onclick="showConfigDialog(${type},'${escPath(url)}','${escPath(name)}')">编辑</button>
            <button class="file-action danger" type="button" onclick="deleteConfig(${type},'${escPath(url)}')">删除</button>
        </div>
    </div>`;
}
function configTypeName(type) {
    if (Number(type) === 1) return '直播';
    if (Number(type) === 2) return '壁纸';
    return '影视';
}
function showConfigDialog(type = null, url = '', name = '') {
    const editing = !!url;
    const selectedType = editing ? Number(type || 0) : Number(configFilter || 0);
    editingConfig = { type: selectedType, oldUrl: url || '', editing };
    $('#configType').val(String(editingConfig.type));
    $('#configTypeRow').toggle(editing);
    $('#configName').val(name || '');
    $('#configUrl').val(url || '');
    $('#configDialogTitle').text(url ? '编辑接口' : '新增接口');
    openDialog('configDialog');
}
function chooseConfigLocalFile() {
    if (mode === 'remote' && !target) { ensureActionTarget(); return; }
    $('#config_file_uploader').val('').click();
}
function onConfigLocalFileSelected() {
    const input = $('#config_file_uploader')[0];
    const file = input && input.files ? input.files[0] : null;
    if (!file) return;
    const remote = mode === 'remote' && !!target;
    const data = new FormData();
    data.append('path', CONFIG_UPLOAD_DIR);
    if (remote) data.append('target', target);
    data.append('files-0', file);
    const url = remote ? '/manage/remote/upload' : '/upload';
    if (blockOfflineRemote(url, remote ? { target } : {})) return;
    showLoading();
    $.ajax({ url, type: 'post', data, processData: false, contentType: false, timeout: UPLOAD_TIMEOUT })
        .done(() => {
            const path = CONFIG_UPLOAD_DIR + '/' + file.name;
            const prefix = Number($('#configType').val() || 0) === 2 ? 'file:/' : 'file://';
            $('#configUrl').val(prefix + path);
            warnToast('文件已上传并填入接口地址');
        })
        .fail((xhr, status) => warnToast(requestError(xhr, status, '本地文件上传失败')))
        .always(() => { $('#config_file_uploader').val(''); hideLoading(); });
}
function saveConfigDialog() {
    const type = Number($('#configType').val() || 0);
    const name = $('#configName').val().trim();
    const url = $('#configUrl').val().trim();
    if (!url) { warnToast('请填写接口地址'); return; }
    const oldUrl = editingConfig && editingConfig.oldUrl && editingConfig.oldUrl !== url ? editingConfig.oldUrl : '';
    const save = () => postJson('/manage/configs', { type, name, url }, data => {
        configsData = data.items || [];
        configsLoadedKey = activeKey();
        configFilter = type;
        renderConfigsManage();
        closeDialog('configDialog');
        warnToast('接口已保存');
    }, '接口保存失败');
    if (oldUrl) postJson('/manage/config/delete', { type: editingConfig.type, url: oldUrl }, save, '旧接口移除失败');
    else save();
}
function useConfig(type, url) {
    postJson('/manage/config/use', { type, url }, data => {
        configsData = data.items || [];
        configsLoadedKey = activeKey();
        renderConfigsManage();
        warnToast('已切换接口，正在加载');
    }, '接口启用失败');
}
function deleteConfig(type, url) {
    postJson('/manage/config/delete', { type, url }, data => {
        configsData = data.items || [];
        configsLoadedKey = activeKey();
        renderConfigsManage();
        warnToast('接口已删除');
    }, '接口删除失败');
}
function updateActionModeText() {
    const remote = mode === 'remote';
    $('#searchTitle').text(remote ? '远端搜索' : '本机搜索');
    $('#searchSubtitle').text(remote ? '让选中设备打开搜索结果页' : '让当前 App 打开搜索结果页');
    $('#pushTitle').text(remote ? '远端推送' : '本机推送');
    $('#pushSubtitle').text(remote ? '把播放地址推送到选中设备' : '把播放地址推送到当前 App');
}
function remoteSearch() {
    const word = $('#remoteKeyword').val().trim();
    if (!word) { warnToast('请输入搜索关键词'); return; }
    const payload = mode === 'remote' ? { target, do: 'search', word } : { do: 'search', word };
    if (mode === 'remote' && !target) { ensureActionTarget(); return; }
    postAction('/manage/action', payload, () => warnToast('已发送搜索'), '搜索发送失败');
}
function remotePush() {
    const url = $('#remotePushUrl').val().trim();
    if (!url) { warnToast('请输入播放地址'); return; }
    const payload = mode === 'remote' ? { target, do: 'push', url } : { do: 'push', url };
    if (mode === 'remote' && !target) { ensureActionTarget(); return; }
    postAction('/manage/action', payload, () => warnToast('已发送推送'), '推送发送失败');
}
function ensureActionTarget() {
    warnToast('请先选择远端设备');
    devicePanelOpen = true;
    updateRemotePicker();
    loadDevices();
}

function openDialog(id) { $('#' + id).show(); history.pushState({ dialog: id }, ''); }
function closeDialog(id) { dialogClosing = true; $('#' + id).hide(); history.back(); }
function warnToast(msg) { $('#warnToastContent').text(msg); $('#warnToast').show(); if (warnToastTimer) clearTimeout(warnToastTimer); warnToastTimer = setTimeout(() => { $('#warnToast').hide(); warnToastTimer = null; }, 1500); }

window.addEventListener('popstate', function () {
    if (dialogClosing) { dialogClosing = false; return; }
    const visible = $('.md-dialog-overlay:visible');
    if (visible.length) { visible.first().hide(); return; }
    if (currentView === 'files') listFile(currentParent);
});

$(function () {
    startHeartbeat();
    setManageMode('local');
    updateSyncPathsVisible();
    $('#newFolderContent').on('keydown', function (e) { if (e.key === 'Enter') { this.blur(); confirmNewFolder(1); } });
    $(document).on('input', '#cspList input.csp-field', function () { syncCspFromCards(); });
    $(document).on('change', '#cspList .csp-field', function () { syncCspFromCards(); });
    $('#cspRaw').on('input', function () { cspRawDirty = true; });
    $(document).on('change', '#viewSync input[type=checkbox]', updateSyncPathsVisible);
});
