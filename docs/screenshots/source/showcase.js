const iconPaths = {
  add: '<path d="M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6z"/>',
  bluetooth: '<path d="M17.7 7.7 12 2v7.6L7.4 5 6 6.4l5.6 5.6L6 17.6 7.4 19l4.6-4.6V22l5.7-5.7-4.3-4.3 4.3-4.3ZM14 6.8l.9.9-.9.9V6.8Zm.9 9.5-.9.9v-1.8l.9.9Z"/>',
  check: '<path d="m9.2 16.2-4.3-4.3 1.4-1.4 2.9 2.9 8.5-8.5 1.4 1.4z"/>',
  chevron: '<path d="m9 18 6-6-6-6 1.4-1.4L18.8 12l-8.4 7.4z"/>',
  close: '<path d="M18.3 5.7 12 10.6 5.7 5.7 4.3 7.1l6.3 4.9-6.3 4.9 1.4 1.4 6.3-4.9 6.3 4.9 1.4-1.4-6.3-4.9 6.3-4.9z"/>',
  collapse: '<path d="m7.4 15.4 4.6-4.6 4.6 4.6L18 14l-6-6-6 6z"/>',
  expand: '<path d="m7.4 8.6 4.6 4.6 4.6-4.6L18 10l-6 6-6-6z"/>',
  folder: '<path d="M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8z"/>',
  info: '<path d="M11 10h2v7h-2zm0-3h2v2h-2z"/><path fill-rule="evenodd" d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm0 18a8 8 0 1 1 0-16 8 8 0 0 1 0 16Z"/>',
  library: '<path d="M4 4h3v16H4zm5 3h3v13H9zm5-3h3v16h-3zm5 5h3v11h-3z"/>',
  mixer: '<path d="M4 21h2v-7H4v7Zm0-9h2V3H4v9Zm7 9h2v-9h-2v9Zm0-11h2V3h-2v7Zm7 11h2v-4h-2v4Zm0-6h2V3h-2v12Z"/><path d="M2 11h6v4H2zm7-2h6v4H9zm7 5h6v4h-6z"/>',
  more: '<circle cx="12" cy="5" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="12" cy="19" r="2"/>',
  music: '<path d="M12 3v10.6a4 4 0 1 0 2 3.4V7h6V3h-8Z"/>',
  next: '<path d="M6 18V6l8.5 6L6 18Zm10-12h2v12h-2z"/>',
  pause: '<path d="M6 5h4v14H6zm8 0h4v14h-4z"/>',
  play: '<path d="M8 5v14l11-7z"/>',
  playlist: '<path d="M3 6h12v2H3zm0 5h9v2H3zm0 5h9v2H3zm12-5v7.2a3 3 0 1 0 2-2.8V11h4V9h-6Z"/>',
  previous: '<path d="M18 6v12l-8.5-6L18 6ZM6 6h2v12H6z"/>',
  record: '<circle cx="12" cy="12" r="8"/>',
  refresh: '<path d="M17.7 6.3A8 8 0 1 0 20 12h-2a6 6 0 1 1-1.8-4.3L13 11h8V3l-3.3 3.3Z"/>',
  search: '<path fill-rule="evenodd" d="M10 4a6 6 0 1 0 3.7 10.7L19 20l1-1-5.3-5.3A6 6 0 0 0 10 4Zm0 2a4 4 0 1 1 0 8 4 4 0 0 1 0-8Z"/>',
  shuffle: '<path d="M16 3h5v5l-1.8-1.8-3.9 3.9-1.4-1.4 3.9-3.9L16 3ZM3 7h3.5c1.8 0 3.2.7 4.5 2.2l4.4 5.2c.9 1 1.6 1.6 3 1.6h.6l-2-2 1.4-1.4L22 16l-3.6 3.4L17 18l2-2h-.6c-2.1 0-3.4-1-4.5-2.3L9.5 8.5C8.7 7.6 7.8 7 6.5 7H3Zm0 9h3.5c1.3 0 2.2-.6 3-1.5l1.2-1.4 1.3 1.5-1 1.1C9.9 17 8.6 18 6.5 18H3Z"/>',
  stop: '<path d="M6 6h12v12H6z"/>',
  upload: '<path d="M5 20h14v-2H5v2Zm7-16-5 5h3v6h4V9h3l-5-5Z"/>',
  usb: '<path d="M15 7v4h2v2h-4V5h2l-3-3-3 3h2v8H7v-2.2A3 3 0 1 0 5 16v-3h2v2h4v3.2A3 3 0 1 0 13 18v-3h4a2 2 0 0 0 2-2v-2h2V7h-6Z"/>',
  volume: '<path d="M3 9v6h4l5 4V5L7 9H3Zm12.5 3a3.5 3.5 0 0 0-2-3.2v6.4a3.5 3.5 0 0 0 2-3.2Zm-2-8v2.1a6 6 0 0 1 0 11.8V20a8 8 0 0 0 0-16Z"/>',
  wifi: '<path d="M2 8.8a15.8 15.8 0 0 1 20 0l-1.4 1.5a13.8 13.8 0 0 0-17.2 0L2 8.8Zm3.5 3.5a10.3 10.3 0 0 1 13 0L17 13.8a8.2 8.2 0 0 0-10 0l-1.5-1.5Zm3.6 3.4a4.6 4.6 0 0 1 5.8 0L12 19l-2.9-3.3Z"/>'
};

const assets = {
  markDark: '../../../app/src/main/res/drawable-xxxhdpi/app_header_logo.png',
  markWhite: '../../../app/src/main/res/drawable-nodpi/logo_mark_white.png'
};

function icon(name, className = '') {
  return `<svg class="icon ${className}" viewBox="0 0 24 24" aria-hidden="true">${iconPaths[name]}</svg>`;
}

function statusBar() {
  return `
    <div class="statusbar">
      <span>9:41</span>
      <span class="status-icons">${icon('wifi')}<span class="cell-bars"><i></i><i></i><i></i><i></i></span><span class="battery"><i></i></span></span>
    </div>`;
}

function topBar(scene) {
  const messages = {
    library: 'Connected to WIDI Core.',
    playback: 'Playing Bach - WTC I Prelude I, BWV 846',
    recording: 'Recording Evening Practice.',
    mixer: 'Playing Bach - WTC I Prelude I, BWV 846',
    connection: 'Connected to WIDI Core.'
  };
  return `
    <header class="topbar">
      <img class="app-mark" src="${assets.markDark}" alt="">
      <div class="app-heading"><strong>APS NoteCast</strong><span>${messages[scene]}</span></div>
      <div class="connection-pill">${icon('bluetooth')}<span><b>Connected</b><small>WIDI Core</small></span></div>
      <span class="icon-button more-button">${icon('more')}</span>
    </header>`;
}

function actionButton(name, label, inviting = false) {
  return `<div class="action-button ${inviting ? 'inviting' : ''}">${icon(name)}<span>${label}</span></div>`;
}

function libraryHero() {
  return `
    <section class="library-hero-card">
      <div class="library-title-row">
        ${icon('library')}
        <div><strong>Library</strong><small>Files: 2 - Playlists: 1</small></div>
        <span class="icon-button">${icon('bluetooth')}</span>
      </div>
      <div class="library-actions">
        ${actionButton('upload', 'Add MIDI')}
        ${actionButton('search', 'External')}
        ${actionButton('record', 'Record MIDI', true)}
        ${actionButton('folder', 'New Playlist')}
      </div>
    </section>`;
}

function playlist(expanded = false, activeTrack = false) {
  return `
    <div class="section-label">Playlists</div>
    <section class="playlist-card ${expanded ? 'expanded' : ''}">
      <div class="playlist-head">
        <span class="icon-button small">${icon(expanded ? 'expand' : 'chevron')}</span>
        ${icon('folder')}
        <div class="playlist-name"><strong>Sample Playlist: Mutopia Public Domain Demos</strong><small>Tracks: 2</small></div>
        <span class="icon-button small">${icon('add')}</span>
        <span class="icon-button small">${icon('playlist')}</span>
        <span class="icon-button small">${icon('shuffle')}</span>
        <span class="icon-button small">${icon('more')}</span>
      </div>
      ${expanded ? `
        <div class="playlist-track"><b>1.</b><span><strong>Beethoven - Für Elise, WoO 59</strong><small>2:10</small></span></div>
        <div class="playlist-track ${activeTrack ? 'selected' : ''}"><b>2.</b><span><strong>Bach - WTC I Prelude I, BWV 846</strong><small>2:20</small></span></div>
      ` : ''}
    </section>`;
}

function midiHeader() {
  return `
    <div class="midi-header">
      <strong>MIDI files</strong>
      <div class="display-control"><span>${icon('collapse')}</span><span>${icon('search')}</span><b>Alphabetical</b><span>All Songs</span></div>
    </div>
    <div class="letter-header"><strong>B</strong><span>2 MIDI files</span>${icon('expand')}</div>`;
}

function midiRow(title, duration, originalName, state = '') {
  return `
    <div class="midi-row ${state}">
      <span class="music-icon">${icon('music')}</span>
      <div class="midi-copy"><strong>${title}</strong><small>${duration} - ${originalName}</small><em>Bundled Mutopia Project demo - Public Domain / no rights reserved</em></div>
      <span class="icon-button">${icon(state === 'playing' ? 'pause' : 'play')}</span>
      <span class="icon-button">${icon('more')}</span>
    </div>`;
}

function libraryContent(scene) {
  const playing = scene === 'playback';
  return `
    <div class="library-scroll ${playing ? 'playback-library' : ''}">
      ${libraryHero()}
      ${playlist(playing, playing)}
      ${midiHeader()}
      ${midiRow('Bach - WTC I Prelude I, BWV 846', '2:20', 'Mutopia - Bach - WTC I Prelude I, BWV 846.mid', playing ? 'playing' : 'selected')}
      ${midiRow('Beethoven - Für Elise, WoO 59', '2:10', 'Mutopia - Beethoven - Fur Elise, WoO 59.mid')}
    </div>`;
}

function slider(value, className = '') {
  return `<div class="slider ${className}" style="--value:${value}%"><span class="slider-track"><i></i></span><b></b></div>`;
}

function transport(active = false) {
  return `
    <footer class="transport ${active ? 'active' : ''}">
      <div class="transport-title"><strong>${active ? 'Bach - WTC I Prelude I, BWV 846' : 'Bach - WTC I Prelude I, BWV 846'}</strong><small>${active ? 'Playing 0:54 / 2:20' : 'Ready'}</small></div>
      ${slider(active ? 39 : 0, 'progress-slider')}
      <div class="time-row"><span>${active ? '0:54' : '0:00'}</span><span>${active ? '2:20' : '2:20'}</span></div>
      <div class="transport-controls">
        <span class="icon-button">${icon('previous')}</span>
        <span class="icon-button transport-main">${icon(active ? 'pause' : 'play')}</span>
        <span class="icon-button">${icon('stop')}</span>
        <span class="icon-button">${icon('next')}</span>
        <span class="volume-icon">${icon('volume')}</span>
        ${slider(82, 'volume-slider')}
        <span class="icon-button mixer-button">${icon('mixer')}</span>
      </div>
    </footer>`;
}

function field(label, value) {
  return `<label class="outlined-field"><span>${label}</span><b>${value}</b></label>`;
}

function recordingDialog() {
  return `
    <div class="app-overlay">
      <section class="alert-dialog recording-dialog">
        <div class="dialog-icon error">${icon('record')}</div>
        <h2>Record MIDI</h2>
        ${field('File name', 'Evening Practice')}
        <div class="record-status">
          <strong>Recording MIDI input...</strong>
          <span>Events: 148 - 0:36</span>
          <div class="indeterminate"><i></i></div>
        </div>
        <div class="dialog-actions"><button class="text-button">Cancel</button><button class="filled-button">Save</button></div>
      </section>
    </div>`;
}

function mixerChannel(name, route, volume, muted = false) {
  return `
    <section class="mixer-channel">
      <div class="mixer-channel-head">
        <div><strong>${name}</strong><small>${route}</small></div>
        <b>${volume}%</b><span class="checkbox ${muted ? 'checked' : ''}">${muted ? icon('check') : ''}</span><small>Mute</small>
      </div>
      <div class="mixer-options"><button>Instrument</button><button>Output channel</button></div>
      ${slider(volume)}
    </section>`;
}

function mixerDialog() {
  return `
    <div class="app-overlay full-overlay">
      <section class="mixer-dialog">
        <header class="mixer-title"><span>${icon('mixer')}</span><div><h2>Volume mixer</h2><small>Legacy volume scaling</small></div><span class="icon-button">${icon('close')}</span></header>
        <div class="mixer-scroll">
          <section class="main-volume"><div><strong>Main volume</strong><span>92%</span></div>${slider(92)}</section>
          ${mixerChannel('Acoustic Grand Piano', 'Source Ch 1 → Output Ch 1', 100)}
          ${mixerChannel('Acoustic Grand Piano', 'Source Ch 2 → Output Ch 1', 72)}
        </div>
        <footer><button class="filled-button">Done</button></footer>
      </section>
    </div>`;
}

function deviceRow(type, name, detail, primaryLabel = 'Switch') {
  return `
    <section class="device-row">
      <div class="device-copy">${icon(type)}<span><strong>${name}</strong><small>${detail}</small></span><span class="icon-button tiny">✎</span></div>
      <div class="device-actions"><button class="outline-button">Save</button><button class="filled-button">${primaryLabel}</button></div>
    </section>`;
}

function connectionDialog() {
  return `
    <div class="app-overlay">
      <section class="alert-dialog connection-dialog">
        <h2>Switch MIDI adapter</h2>
        <div class="connection-note">${icon('info')}<span><strong>Current adapter stays connected.</strong><small>Choose another available MIDI device.</small></span></div>
        <div class="connection-section"><strong>Nearby now</strong><small>Connect uses it now. Save remembers it for later.</small></div>
        ${deviceRow('usb', 'USB MIDI Interface', 'USB MIDI - attached now')}
        ${deviceRow('bluetooth', 'BLE MIDI Controller', 'BLE MIDI - nearby now - -53 dBm')}
        <div class="dialog-actions connection-actions"><button class="text-button">Scan again</button><button class="text-button">Close</button></div>
      </section>
    </div>`;
}

function phone(scene, modifier = '') {
  const baseScene = scene === 'library' ? 'library' : scene;
  const active = ['playback', 'mixer'].includes(scene);
  const overlay = scene === 'recording' ? recordingDialog() : scene === 'mixer' ? mixerDialog() : scene === 'connection' ? connectionDialog() : '';
  return `
    <div class="phone ${modifier}">
      <div class="phone-screen">
        ${statusBar()}
        <div class="app-shell">
          ${topBar(baseScene)}
          <main class="app-content">${libraryContent(active ? 'playback' : 'library')}</main>
          ${transport(active)}
        </div>
        <div class="nav-bar"><i></i></div>
        ${overlay}
      </div>
    </div>`;
}

const portraitCopy = {
  library: {
    eyebrow: 'MIDI LIBRARY',
    title: 'Your repertoire, beautifully organized.',
    text: 'Build playlists, browse your MIDI files, and keep every piece ready to play.'
  },
  playback: {
    eyebrow: 'MIDI PLAYBACK',
    title: 'Stay in the music.',
    text: 'Play, pause, seek, skip, and shape volume without leaving your library.'
  },
  recording: {
    eyebrow: 'MIDI RECORDING',
    title: 'Capture a performance.',
    text: 'Record incoming MIDI, name it, and save it straight into your library.'
  },
  mixer: {
    eyebrow: 'CHANNEL MIXER',
    title: 'Every channel, under control.',
    text: 'Balance parts, mute channels, and route instruments for each song.'
  },
  connection: {
    eyebrow: 'BLUETOOTH + USB MIDI',
    title: 'Connect your way.',
    text: 'Switch between nearby BLE MIDI and attached USB MIDI from one clear chooser.'
  }
};

function brandLockup() {
  return `<div class="brand-lockup"><img src="${assets.markWhite}" alt=""><span><b>APS</b> NoteCast</span></div>`;
}

function portrait(scene) {
  const copy = portraitCopy[scene];
  return `
    <section class="poster poster-${scene}">
      <header class="poster-copy">
        <div class="poster-kicker">${brandLockup()}<span>${copy.eyebrow}</span></div>
        <h1>${copy.title}</h1>
        <p>${copy.text}</p>
      </header>
      <div class="poster-device">${phone(scene)}</div>
    </section>`;
}

function hero() {
  return `
    <section class="hero">
      <div class="hero-copy">
        ${brandLockup()}
        <span class="hero-eyebrow">ANDROID MIDI PLAYER</span>
        <h1>MIDI playback, recording, and control—beautifully together.</h1>
        <p>Organize your library and connect Android to Bluetooth or USB MIDI with APS NoteCast.</p>
        <div class="hero-features"><span>${icon('playlist')} Playlists</span><span>${icon('record')} Recording</span><span>${icon('mixer')} Channel mixer</span></div>
        <div class="hero-connection">${icon('check')}<span><b>WIDI Core</b><small>Connected and ready</small></span></div>
      </div>
      <div class="hero-devices">
        <div class="hero-phone hero-phone-library">${phone('library')}</div>
        <div class="hero-phone hero-phone-recording">${phone('recording')}</div>
        <div class="hero-phone hero-phone-playback">${phone('playback')}</div>
      </div>
    </section>`;
}

const params = new URLSearchParams(window.location.search);
const scene = params.get('scene') || 'library';
const canvas = document.getElementById('canvas');
canvas.className = `canvas ${scene === 'hero' ? 'canvas-hero' : 'canvas-portrait'} scene-${scene}`;
canvas.innerHTML = scene === 'hero' ? hero() : portrait(scene in portraitCopy ? scene : 'library');
